import http.server
import json
import os
import sys
import threading
import unittest
from contextlib import contextmanager

# Allow running from any directory
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from handlers import (
    _normalize_args,
    _normalize_command_args,
    _normalize_url,
    _reject_pathless_read,
    _reject_unsafe_args,
    _split_simple_command,
    handle_fetch_url_content,
    handle_run_command,
)


def _parse(raw: str) -> dict:
    return json.loads(raw)


@contextmanager
def _http_server(handler_fn):
    """Spin up a local HTTP server on an ephemeral port, yield its base URL."""

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


class TestRunCommand(unittest.IsolatedAsyncioTestCase):

    async def test_accepts_simple_whitelisted_command(self):
        result = _parse(await handle_run_command({"command": "pwd", "cwd": os.getcwd()}))
        self.assertTrue(result["ok"])
        self.assertEqual(result["tool"], "run_command")
        self.assertEqual(result["data"]["command"], "pwd")
        self.assertEqual(result["data"]["exit_code"], 0)

    async def test_splits_inline_args_from_command_string(self):
        result = _parse(await handle_run_command({"command": "ls -d .", "cwd": os.getcwd()}))
        self.assertTrue(result["ok"])
        self.assertEqual(result["data"]["command"], "ls")
        self.assertEqual(result["data"]["args"], ["-d", "."])
        self.assertEqual(result["data"]["exit_code"], 0)

    async def test_rejects_non_whitelisted_commands(self):
        result = _parse(await handle_run_command({"command": "rm -rf tmp", "cwd": os.getcwd()}))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "COMMAND_NOT_ALLOWED")

    async def test_rejects_mutating_commands_by_default(self):
        for cmd in ["sed -i s/a/b/g file.txt", "find . -delete"]:
            with self.subTest(cmd=cmd):
                result = _parse(await handle_run_command({"command": cmd, "cwd": os.getcwd()}))
                self.assertFalse(result["ok"])
                self.assertEqual(result["error"]["code"], "COMMAND_NOT_ALLOWED")

    async def test_returns_structured_error_for_missing_command(self):
        result = _parse(await handle_run_command({}))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "COMMAND_REQUIRED")

    async def test_rejects_shell_operators_in_arguments(self):
        result = _parse(await handle_run_command({
            "command": "ls",
            "args": [".", "&&", "pwd"],
            "cwd": os.getcwd(),
        }))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "UNSAFE_ARGUMENT")

    async def test_rejects_cwd_outside_allowed_roots(self):
        prev = os.environ.get("LOCAL_TINY_COMMAND_ROOTS")
        os.environ["LOCAL_TINY_COMMAND_ROOTS"] = os.getcwd()
        try:
            result = _parse(await handle_run_command({"command": "pwd", "cwd": "/"}))
            self.assertFalse(result["ok"])
            self.assertEqual(result["error"]["code"], "CWD_NOT_ALLOWED")
        finally:
            if prev is None:
                os.environ.pop("LOCAL_TINY_COMMAND_ROOTS", None)
            else:
                os.environ["LOCAL_TINY_COMMAND_ROOTS"] = prev


class TestFetchUrlContent(unittest.IsolatedAsyncioTestCase):

    async def test_fetches_localhost_text(self):
        def handler(req):
            body = b"hello local tiny agent"
            req.send_response(200)
            req.send_header("Content-Type", "text/plain")
            req.send_header("Content-Length", str(len(body)))
            req.end_headers()
            req.wfile.write(body)

        with _http_server(handler) as url:
            result = _parse(await handle_fetch_url_content({"url": url}))

        self.assertTrue(result["ok"])
        self.assertEqual(result["data"]["status"], 200)
        self.assertEqual(result["data"]["body"], "hello local tiny agent")

    async def test_accepts_url_without_scheme(self):
        def handler(req):
            body = json.dumps({"ok": True}).encode()
            req.send_response(200)
            req.send_header("Content-Type", "application/json")
            req.send_header("Content-Length", str(len(body)))
            req.end_headers()
            req.wfile.write(body)

        with _http_server(handler) as url:
            no_scheme = url.replace("http://", "", 1)
            result = _parse(await handle_fetch_url_content({"url": no_scheme}))

        self.assertTrue(result["ok"])
        self.assertEqual(result["data"]["status"], 200)
        self.assertTrue(json.loads(result["data"]["body"])["ok"])

    async def test_truncates_long_content(self):
        def handler(req):
            body = b"x" * 4096
            req.send_response(200)
            req.send_header("Content-Type", "text/plain")
            req.send_header("Content-Length", str(len(body)))
            req.end_headers()
            req.wfile.write(body)

        with _http_server(handler) as url:
            result = _parse(await handle_fetch_url_content({"url": url, "max_bytes": 1024}))

        self.assertTrue(result["ok"])
        self.assertTrue(result["truncated"])
        self.assertEqual(len(result["data"]["body"]), 1024)

    async def test_returns_structured_error_for_missing_url(self):
        result = _parse(await handle_fetch_url_content({}))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "URL_REQUIRED")

    async def test_rejects_non_local_hosts_by_default(self):
        result = _parse(await handle_fetch_url_content({"url": "https://example.com"}))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "HOST_NOT_ALLOWED")

    async def test_rejects_redirect_to_disallowed_host(self):
        def handler(req):
            req.send_response(302)
            req.send_header("Location", "https://example.com/")
            req.end_headers()

        with _http_server(handler) as url:
            result = _parse(await handle_fetch_url_content({"url": url}))

        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "REDIRECT_HOST_NOT_ALLOWED")


class TestPureHelpers(unittest.TestCase):

    def test_split_simple_command_respects_quotes(self):
        self.assertEqual(_split_simple_command('ls "foo bar" baz'), ["ls", "foo bar", "baz"])
        self.assertEqual(_split_simple_command("rg 'a b' c"), ["rg", "a b", "c"])
        self.assertEqual(_split_simple_command(""), [])
        self.assertEqual(_split_simple_command("   pwd   "), ["pwd"])

    def test_normalize_args_accepts_list_or_string(self):
        self.assertEqual(_normalize_args(["a", "b"]), ["a", "b"])
        self.assertEqual(_normalize_args("-d ."), ["-d", "."])
        self.assertEqual(_normalize_args(None), [])
        self.assertEqual(_normalize_args(42), [])

    def test_normalize_url_scheme_handling(self):
        self.assertEqual(_normalize_url("localhost:3000/x").geturl(), "http://localhost:3000/x")
        self.assertEqual(_normalize_url("http://localhost/y").scheme, "http")
        self.assertEqual(_normalize_url("https://x.example/y").scheme, "https")
        self.assertEqual(_normalize_url("ftp://localhost/").scheme, "ftp")
        self.assertIsNone(_normalize_url(""))
        self.assertIsNone(_normalize_url(None))

    def test_reject_unsafe_args(self):
        self.assertIsNone(_reject_unsafe_args(["foo", "-bar"]))
        self.assertIsNotNone(_reject_unsafe_args(["foo", "|"]))
        self.assertIsNotNone(_reject_unsafe_args(["a", "$(b)"]))
        self.assertIsNotNone(_reject_unsafe_args(["a\nb"]))
        self.assertIsNotNone(_reject_unsafe_args(["a;b"]))
        self.assertIsNotNone(_reject_unsafe_args(["a`b"]))

    def test_reject_pathless_read(self):
        self.assertIsNotNone(_reject_pathless_read("cat", []))
        self.assertIsNotNone(_reject_pathless_read("rg", []))
        self.assertIsNone(_reject_pathless_read("cat", ["file.txt"]))
        self.assertIsNone(_reject_pathless_read("ls", []))
        self.assertIsNone(_reject_pathless_read("pwd", []))

    def test_normalize_command_args_rg_injects_max_count(self):
        self.assertEqual(
            _normalize_command_args("rg", ["foo", "."]),
            ["--max-count", "50", "foo", "."],
        )
        self.assertEqual(
            _normalize_command_args("rg", ["-m", "10", "foo"]),
            ["-m", "10", "foo"],
        )
        self.assertEqual(
            _normalize_command_args("rg", ["--max-count", "5", "foo"]),
            ["--max-count", "5", "foo"],
        )
        self.assertEqual(
            _normalize_command_args("rg", ["--max-count=5", "foo"]),
            ["--max-count=5", "foo"],
        )

    def test_normalize_command_args_du_injects_depth(self):
        out = _normalize_command_args("du", ["."])
        if sys.platform == "darwin":
            self.assertEqual(out, ["-d", "2", "."])
        else:
            self.assertEqual(out, ["--max-depth=2", "."])
        # already has -d: pass-through
        self.assertEqual(_normalize_command_args("du", ["-d", "3", "."]), ["-d", "3", "."])
        self.assertEqual(_normalize_command_args("du", ["--max-depth=3"]), ["--max-depth=3"])

    def test_normalize_command_args_top_is_forced_to_one_shot(self):
        out = _normalize_command_args("top", ["whatever"])
        if sys.platform == "darwin":
            self.assertEqual(out, ["-l", "1", "-n", "20"])
        else:
            self.assertEqual(out, ["-b", "-n", "1"])

    def test_normalize_command_args_unrelated_command_pass_through(self):
        self.assertEqual(_normalize_command_args("ls", ["-la"]), ["-la"])
        self.assertEqual(_normalize_command_args("pwd", []), [])


class TestRunCommandEdgeCases(unittest.IsolatedAsyncioTestCase):

    async def test_args_as_string_is_split(self):
        result = _parse(await handle_run_command({
            "command": "ls",
            "args": "-d .",
            "cwd": os.getcwd(),
        }))
        self.assertTrue(result["ok"])
        self.assertEqual(result["data"]["args"], ["-d", "."])

    async def test_cat_without_args_is_rejected(self):
        result = _parse(await handle_run_command({"command": "cat", "cwd": os.getcwd()}))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "ARGUMENT_REQUIRED")

    async def test_nonzero_exit_reports_exit_nonzero(self):
        result = _parse(await handle_run_command({
            "command": "ls",
            "args": ["/this/path/should/not/exist/xyz"],
            "cwd": os.getcwd(),
        }))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "COMMAND_EXIT_NONZERO")
        self.assertNotEqual(result["data"]["exit_code"], 0)

    async def test_output_truncation(self):
        result = _parse(await handle_run_command({
            "command": "ls",
            "args": ["-la", "/usr/bin"],
            "cwd": os.getcwd(),
            "max_output_bytes": 1024,
        }))
        self.assertTrue(result["ok"])
        self.assertTrue(result["truncated"])
        self.assertGreater(len(result["warnings"]), 0)

    async def test_rg_injects_max_count_through_handler(self):
        # Create a file with many matches inside a tmp dir under cwd
        tmp_dir = os.path.join(os.getcwd(), ".test-rg-tmp")
        os.makedirs(tmp_dir, exist_ok=True)
        f = os.path.join(tmp_dir, "matches.txt")
        try:
            with open(f, "w") as fh:
                fh.write("\n".join(["hit"] * 200))
            result = _parse(await handle_run_command({
                "command": "rg",
                "args": ["hit", tmp_dir],
                "cwd": os.getcwd(),
            }))
            self.assertEqual(result["data"]["args"][:2], ["--max-count", "50"])
        finally:
            os.remove(f)
            os.rmdir(tmp_dir)


class TestFetchEdgeCases(unittest.IsolatedAsyncioTestCase):

    async def test_rejects_ftp_scheme(self):
        result = _parse(await handle_fetch_url_content({"url": "ftp://localhost/foo"}))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "URL_SCHEME_NOT_ALLOWED")

    async def test_http_error_status(self):
        def handler(req):
            body = b"not here"
            req.send_response(404)
            req.send_header("Content-Type", "text/plain")
            req.send_header("Content-Length", str(len(body)))
            req.end_headers()
            req.wfile.write(body)

        with _http_server(handler) as url:
            result = _parse(await handle_fetch_url_content({"url": url}))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "HTTP_ERROR")
        self.assertEqual(result["data"]["status"], 404)

    async def test_non_readable_content_type(self):
        def handler(req):
            body = b"\x89PNG\r\n\x1a\n"  # PNG header
            req.send_response(200)
            req.send_header("Content-Type", "image/png")
            req.send_header("Content-Length", str(len(body)))
            req.end_headers()
            req.wfile.write(body)

        with _http_server(handler) as url:
            result = _parse(await handle_fetch_url_content({"url": url}))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "CONTENT_TYPE_NOT_READABLE")

    async def test_fetch_failed_on_unreachable(self):
        # Bind to an ephemeral port, then close so nobody's listening
        import socket
        s = socket.socket()
        s.bind(("127.0.0.1", 0))
        port = s.getsockname()[1]
        s.close()
        result = _parse(await handle_fetch_url_content({"url": f"http://127.0.0.1:{port}/"}))
        self.assertFalse(result["ok"])
        self.assertEqual(result["error"]["code"], "FETCH_FAILED")


if __name__ == "__main__":
    unittest.main()
