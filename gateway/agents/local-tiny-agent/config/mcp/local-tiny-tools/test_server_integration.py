"""End-to-end test that drives server.py via the MCP stdio protocol.

Equivalent to the deleted TS server.integration.test.ts. Requires the `mcp`
Python package (see requirements.txt); run from this directory.
"""

import http.server
import json
import os
import shutil
import sys
import tempfile
import threading
import unittest
from contextlib import contextmanager

from mcp import ClientSession
from mcp.client.stdio import StdioServerParameters, stdio_client

_HERE = os.path.dirname(os.path.abspath(__file__))


@contextmanager
def _http_server(handler_fn):
    def _make_handler(fn):
        class _Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                fn(self)

            def log_message(self, *args):
                pass

        return _Handler

    srv = http.server.HTTPServer(("127.0.0.1", 0), _make_handler(handler_fn))
    port = srv.server_address[1]
    thread = threading.Thread(target=srv.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{port}"
    finally:
        srv.shutdown()
        thread.join(timeout=2)


def _parse_text(call_result) -> dict:
    assert call_result.content, "tool call returned no content"
    first = call_result.content[0]
    assert getattr(first, "type", None) == "text", f"unexpected content type: {first}"
    return json.loads(first.text)


def _params(goose_root: str) -> StdioServerParameters:
    return StdioServerParameters(
        command=sys.executable,
        args=[os.path.join(_HERE, "server.py")],
        env={
            **os.environ,
            "LOCAL_TINY_ALLOWED_HOSTS": "localhost,127.0.0.1",
            "LOCAL_TINY_COMMAND_ROOTS": _HERE,
            "LOCAL_TINY_COMMAND_TIMEOUT_MS": "5000",
            "LOCAL_TINY_MAX_OUTPUT_BYTES": "65536",
            "LOCAL_TINY_FETCH_TIMEOUT_MS": "8000",
            "LOCAL_TINY_FETCH_MAX_BYTES": "65536",
            "GOOSE_PATH_ROOT": goose_root,
        },
        cwd=_HERE,
    )


class TestServerStdio(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self):
        self._goose_root = tempfile.mkdtemp(prefix="local-tiny-tools-test-")

    async def asyncTearDown(self):
        shutil.rmtree(self._goose_root, ignore_errors=True)

    async def test_lists_and_runs_both_tools_end_to_end(self):
        def handler(req):
            body = b"mcp integration ok"
            req.send_response(200)
            req.send_header("Content-Type", "text/plain")
            req.send_header("Content-Length", str(len(body)))
            req.end_headers()
            req.wfile.write(body)

        async with stdio_client(_params(self._goose_root)) as (read, write):
            async with ClientSession(read, write) as session:
                await session.initialize()

                listed = await session.list_tools()
                self.assertEqual(
                    [t.name for t in listed.tools],
                    ["fetch_url_content", "run_command"],
                )

                cmd = _parse_text(await session.call_tool(
                    "run_command", {"command": "pwd", "cwd": _HERE}
                ))
                self.assertTrue(cmd["ok"])
                self.assertEqual(cmd["data"]["command"], "pwd")

                with _http_server(handler) as url:
                    fetch = _parse_text(await session.call_tool(
                        "fetch_url_content", {"url": url}
                    ))
                self.assertTrue(fetch["ok"])
                self.assertEqual(fetch["data"]["body"], "mcp integration ok")

    async def test_returns_structured_error_without_crashing(self):
        async with stdio_client(_params(self._goose_root)) as (read, write):
            async with ClientSession(read, write) as session:
                await session.initialize()
                result = _parse_text(await session.call_tool(
                    "run_command", {"command": "rm -rf tmp", "cwd": _HERE}
                ))
                self.assertFalse(result["ok"])
                self.assertEqual(result["error"]["code"], "COMMAND_NOT_ALLOWED")

    async def test_unknown_tool_returns_structured_error(self):
        async with stdio_client(_params(self._goose_root)) as (read, write):
            async with ClientSession(read, write) as session:
                await session.initialize()
                # Some SDK versions raise on protocol-level unknown tool;
                # ours dispatches all calls through call_tool and returns a
                # structured payload, so we accept either path.
                try:
                    result = _parse_text(await session.call_tool(
                        "definitely_not_a_tool", {}
                    ))
                    self.assertFalse(result["ok"])
                    self.assertEqual(result["error"]["code"], "UNKNOWN_TOOL")
                except Exception as e:
                    # Acceptable: protocol-level error from the SDK
                    self.assertIn("definitely_not_a_tool", str(e).lower() + " ")


if __name__ == "__main__":
    unittest.main()
