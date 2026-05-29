import json
import threading
import unittest
import unittest.mock as mock
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from tempfile import TemporaryDirectory

import core


class KnowledgeHandler(BaseHTTPRequestHandler):
    routes = {}
    captured = []

    def do_POST(self):
        length = int(self.headers.get("content-length", "0"))
        body = self.rfile.read(length).decode("utf-8")
        payload = json.loads(body) if body else None
        self.__class__.captured.append((self.command, self.path, payload))
        self._send(self.__class__.routes.get((self.command, self.path), {"query": "", "hits": [], "total": 0}))

    def do_GET(self):
        self.__class__.captured.append((self.command, self.path, None))
        result = self.__class__.routes.get((self.command, self.path))
        if result is None:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"not found")
            return
        self._send(result)

    def _send(self, payload):
        data = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format, *args):
        return


class KnowledgeCoreTest(unittest.TestCase):
    def setUp(self):
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), KnowledgeHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        KnowledgeHandler.routes = {}
        KnowledgeHandler.captured = []
        self.url = f"http://127.0.0.1:{self.server.server_port}"
        self.patches = [
            mock.patch.object(core, "KNOWLEDGE_SERVICE_URL", self.url),
            mock.patch.object(core, "KNOWLEDGE_REQUEST_TIMEOUT_SECONDS", 5),
        ]
        for patcher in self.patches:
            patcher.start()

    def tearDown(self):
        for patcher in reversed(self.patches):
            patcher.stop()
        self.server.shutdown()
        self.server.server_close()

    def test_reads_configured_knowledge_scope(self):
        with TemporaryDirectory() as temp_dir:
            config_path = Path(temp_dir) / "config.yaml"
            config_path.write_text(
                "\n".join(
                    [
                        "extensions:",
                        "  other:",
                        "    sourceId: wrong",
                        "  knowledge-service:",
                        "    x-opsfactory:",
                        "      knowledgeScope:",
                        "        sourceId: src_1",
                    ]
                ),
                encoding="utf-8",
            )
            with mock.patch.object(core, "CONFIG_FILE_PATH", config_path):
                self.assertEqual(core.read_configured_source_id(), "src_1")

    def test_search_uses_configured_scope_when_source_ids_are_omitted(self):
        KnowledgeHandler.routes[("POST", "/knowledge/search")] = {"query": "故障定位", "hits": [], "total": 0}
        with mock.patch.object(core, "read_configured_source_id", return_value="src_configured"):
            result = json.loads(core.handle_search("故障定位"))

        self.assertEqual(result["total"], 0)
        self.assertEqual(KnowledgeHandler.captured[0][2]["sourceIds"], ["src_configured"])

    def test_search_passes_explicit_source_ids(self):
        KnowledgeHandler.routes[("POST", "/knowledge/search")] = {"query": "容量规划", "hits": [], "total": 1}
        result = json.loads(core.handle_search("容量规划", sourceIds=["src_custom"], topK=3))

        self.assertEqual(result["total"], 1)
        self.assertEqual(KnowledgeHandler.captured[0][2]["sourceIds"], ["src_custom"])
        self.assertEqual(KnowledgeHandler.captured[0][2]["topK"], 3)

    def test_fetch_includes_neighbor_options(self):
        path = "/knowledge/fetch/chk_123?includeNeighbors=true&neighborWindow=2&includeMarkdown=true&includeRawText=true"
        KnowledgeHandler.routes[("GET", path)] = {"chunkId": "chk_123", "neighbors": [{"chunkId": "chk_122"}]}
        result = json.loads(core.handle_fetch("chk_123", includeNeighbors=True, neighborWindow=2))

        self.assertEqual(result["chunkId"], "chk_123")
        self.assertEqual(len(result["neighbors"]), 1)

    def test_fetch_rejects_neighbor_window_above_backend_limit(self):
        with self.assertRaisesRegex(ValueError, r"fetch\.neighborWindow must be an integer between 1 and 2"):
            core.handle_fetch("chk_123", includeNeighbors=True, neighborWindow=3)


if __name__ == "__main__":
    unittest.main()
