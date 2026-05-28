import json
import threading
import unittest
import unittest.mock as mock
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from tempfile import TemporaryDirectory

import core


class ControlCenterHandler(BaseHTTPRequestHandler):
    routes = {}
    captured = []

    def do_GET(self):
        self._handle()

    def do_POST(self):
        self._handle()

    def _handle(self):
        self.__class__.captured.append(
            (self.command, self.path, self.headers.get("x-secret-key"))
        )
        payload = self.__class__.routes.get((self.command, self.path))
        if payload is None:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"not found")
            return
        data = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, _format, *args):
        return


class ControlCenterCoreTest(unittest.TestCase):
    def setUp(self):
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), ControlCenterHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        ControlCenterHandler.routes = {}
        ControlCenterHandler.captured = []
        self.temp_dir = TemporaryDirectory()
        self.patches = [
            mock.patch.object(
                core,
                "CONTROL_CENTER_URL",
                f"http://127.0.0.1:{self.server.server_port}",
            ),
            mock.patch.object(core, "REQUEST_TIMEOUT_SECONDS", 5),
            mock.patch.dict(
                "os.environ",
                {
                    "CONTROL_CENTER_SECRET_KEY": "unit-test-secret",
                    "GOOSE_PATH_ROOT": self.temp_dir.name,
                },
            ),
        ]
        for patcher in self.patches:
            patcher.start()
        self.logger_patcher = mock.patch.object(core, "LOGGER", core.McpLogger())
        self.logger_patcher.start()

    def tearDown(self):
        self.logger_patcher.stop()
        for patcher in reversed(self.patches):
            patcher.stop()
        self.temp_dir.cleanup()
        self.server.shutdown()
        self.server.server_close()

    def test_cc_fetches_json_with_secret_header(self):
        ControlCenterHandler.routes[("GET", "/control-center/services")] = {"services": []}
        result = core.cc("/control-center/services")

        self.assertEqual(result, {"services": []})
        self.assertEqual(ControlCenterHandler.captured[0][2], "unit-test-secret")

    def test_cc_appends_query_params_and_skips_empty_values(self):
        ControlCenterHandler.routes[
            ("GET", "/control-center/services/gateway/logs?lines=50")
        ] = {"content": "line"}

        result = core.cc(
            "/control-center/services/gateway/logs",
            {"lines": 50, "empty": ""},
        )

        self.assertEqual(result["content"], "line")

    def test_cc_raises_on_non_ok_response(self):
        with self.assertRaisesRegex(RuntimeError, "returned 404"):
            core.cc("/missing")

    def test_requires_secret_key(self):
        with mock.patch.dict("os.environ", {}, clear=True):
            with self.assertRaisesRegex(
                RuntimeError,
                "CONTROL_CENTER_SECRET_KEY is required",
            ):
                core.require_control_center_secret_key()

    def test_handle_get_platform_status_combines_system_and_instances(self):
        ControlCenterHandler.routes[("GET", "/control-center/runtime/system")] = {
            "gateway": {"uptimeMs": 1000}
        }
        ControlCenterHandler.routes[("GET", "/control-center/runtime/instances")] = {
            "totalInstances": 3
        }

        result = json.loads(core.handle_get_platform_status())

        self.assertEqual(result["system"], {"gateway": {"uptimeMs": 1000}})
        self.assertEqual(result["instances"], {"totalInstances": 3})

    def test_handle_get_agents_status_combines_agents_and_instances(self):
        ControlCenterHandler.routes[("GET", "/control-center/runtime/agents")] = [
            {"id": "agent-1"}
        ]
        ControlCenterHandler.routes[("GET", "/control-center/runtime/instances")] = {
            "totalInstances": 1
        }

        result = json.loads(core.handle_get_agents_status())

        self.assertEqual(result["agents"], [{"id": "agent-1"}])
        self.assertEqual(result["instances"], {"totalInstances": 1})

    def test_observability_returns_unconfigured_error(self):
        ControlCenterHandler.routes[
            ("GET", "/control-center/observability/status")
        ] = {"enabled": False}

        result = json.loads(core.handle_get_observability_data(24))

        self.assertIn("not configured", result["error"])

    def test_observability_fetches_payloads_when_reachable(self):
        ControlCenterHandler.routes[("GET", "/control-center/observability/status")] = {
            "enabled": True,
            "reachable": True,
        }
        ControlCenterHandler.routes[
            (
                "GET",
                "/control-center/observability/overview"
                "?from=2026-01-01T00%3A00%3A00Z"
                "&to=2026-01-02T00%3A00%3A00Z",
            )
        ] = {"totalTraces": 100}
        ControlCenterHandler.routes[
            (
                "GET",
                "/control-center/observability/traces"
                "?from=2026-01-01T00%3A00%3A00Z"
                "&to=2026-01-02T00%3A00%3A00Z&limit=30",
            )
        ] = [{"id": "trace-1"}]
        ControlCenterHandler.routes[
            (
                "GET",
                "/control-center/observability/observations"
                "?from=2026-01-01T00%3A00%3A00Z"
                "&to=2026-01-02T00%3A00%3A00Z",
            )
        ] = {"observations": []}

        fixed_to = core.datetime(2026, 1, 2, tzinfo=core.timezone.utc)

        class FixedDatetime(core.datetime):
            @classmethod
            def now(cls, tz=None):
                return fixed_to

        with mock.patch.object(core, "datetime", FixedDatetime):
            result = json.loads(core.handle_get_observability_data(24))

        self.assertEqual(result["timeRange"]["hours"], 24)
        self.assertEqual(result["overview"], {"totalTraces": 100})
        self.assertEqual(result["traces"], [{"id": "trace-1"}])
        self.assertEqual(result["observations"], {"observations": []})

    def test_service_scoped_handlers_encode_service_id(self):
        ControlCenterHandler.routes[
            ("GET", "/control-center/services/business-intelligence")
        ] = {"id": "business-intelligence"}
        ControlCenterHandler.routes[
            ("GET", "/control-center/services/business-intelligence/logs?lines=50")
        ] = {"content": "line"}
        ControlCenterHandler.routes[
            ("GET", "/control-center/services/business-intelligence/config")
        ] = {"content": "server:"}

        self.assertEqual(
            json.loads(core.handle_get_service_status("business-intelligence"))["id"],
            "business-intelligence",
        )
        self.assertEqual(
            json.loads(core.handle_read_service_logs("business-intelligence", 50))[
                "content"
            ],
            "line",
        )
        self.assertIn(
            "server",
            json.loads(core.handle_read_service_config("business-intelligence"))[
                "content"
            ],
        )

    def test_action_handlers_use_post(self):
        ControlCenterHandler.routes[
            ("POST", "/control-center/services/gateway/actions/start")
        ] = {"action": "start"}
        ControlCenterHandler.routes[
            ("POST", "/control-center/services/gateway/actions/stop")
        ] = {"action": "stop"}
        ControlCenterHandler.routes[
            ("POST", "/control-center/services/gateway/actions/restart")
        ] = {"action": "restart"}

        self.assertEqual(json.loads(core.handle_start_service("gateway"))["action"], "start")
        self.assertEqual(json.loads(core.handle_stop_service("gateway"))["action"], "stop")
        self.assertEqual(json.loads(core.handle_restart_service("gateway"))["action"], "restart")

    def test_list_handlers(self):
        ControlCenterHandler.routes[("GET", "/control-center/runtime/metrics")] = {"series": []}
        ControlCenterHandler.routes[("GET", "/control-center/services")] = {"services": []}
        ControlCenterHandler.routes[("GET", "/control-center/events")] = {"events": []}

        self.assertEqual(json.loads(core.handle_get_realtime_metrics())["series"], [])
        self.assertEqual(json.loads(core.handle_list_services())["services"], [])
        self.assertEqual(json.loads(core.handle_list_events())["events"], [])

    def test_normalizers_match_existing_contract(self):
        self.assertEqual(core.normalize_hours("bad"), 24)
        self.assertEqual(core.normalize_hours(0), 1)
        self.assertEqual(core.normalize_hours(900), 720)
        self.assertEqual(core.normalize_lines("bad"), 200)
        self.assertEqual(core.normalize_lines(0), 1)
        self.assertEqual(core.normalize_lines(50.5), 51)
        self.assertEqual(core.normalize_lines(1500), 1000)

    def test_rejects_missing_service_id(self):
        with self.assertRaisesRegex(ValueError, "serviceId is required"):
            core.handle_get_service_status(" ")


if __name__ == "__main__":
    unittest.main()
