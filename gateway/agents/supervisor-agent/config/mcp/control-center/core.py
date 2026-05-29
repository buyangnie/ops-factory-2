#!/usr/bin/env python3
from __future__ import annotations

import json
import math
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, Optional


CONTROL_CENTER_URL = os.environ.get(
    "CONTROL_CENTER_URL",
    "http://127.0.0.1:8094",
).rstrip("/")
REQUEST_TIMEOUT_SECONDS = 15
LOG_FILE_NAME = "control_center.log"


class McpLogger:
    def __init__(self) -> None:
        root = Path(os.environ.get("GOOSE_PATH_ROOT") or os.getcwd())
        self.log_dir = root / "logs" / "mcp"
        self.log_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.log_file = self.log_dir / LOG_FILE_NAME

    def _write(self, level: str, event: str, **fields: Any) -> None:
        payload: Dict[str, Any] = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "level": level,
            "service": "control_center",
            "event": event,
        }
        for key, value in fields.items():
            sanitized = _sanitize_log_value(value)
            if sanitized is not None:
                payload[key] = sanitized
        line = json.dumps(payload, ensure_ascii=True)
        print(line, file=sys.stderr, flush=True)
        _append_log_line(self.log_file, line)

    def info(self, event: str, **fields: Any) -> None:
        self._write("INFO", event, **fields)

    def error(self, event: str, **fields: Any) -> None:
        self._write("ERROR", event, **fields)


def _sanitize_log_value(value: Any) -> Any:
    if isinstance(value, BaseException):
        return {
            "name": type(value).__name__,
            "message": str(value),
        }
    if value is None:
        return None
    try:
        json.dumps(value)
        return value
    except TypeError:
        return str(value)


def _append_log_line(path: Path, line: str) -> None:
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
    with os.fdopen(fd, "a", encoding="utf-8") as fp:
        fp.write(line + "\n")


LOGGER = McpLogger()
LOG_FILE_PATH = str(LOGGER.log_file)


def require_control_center_secret_key() -> str:
    secret = os.environ.get("CONTROL_CENTER_SECRET_KEY", "").strip()
    if not secret:
        raise RuntimeError("CONTROL_CENTER_SECRET_KEY is required")
    return secret


def _build_url(path: str, params: Optional[Dict[str, Any]] = None) -> str:
    url = f"{CONTROL_CENTER_URL}{path}"
    filtered_params: Dict[str, str] = {}
    if params:
        for key, value in params.items():
            if value is not None and value != "":
                filtered_params[key] = str(value)
    if filtered_params:
        url = f"{url}?{urllib.parse.urlencode(filtered_params)}"
    return url


def cc(path: str, params: Optional[Dict[str, Any]] = None, method: str = "GET") -> Any:
    started_at = time.monotonic()
    url = _build_url(path, params)
    normalized_method = method.upper()

    LOGGER.info(
        "control_center_request_started",
        method=normalized_method,
        path=path,
        url=url,
        params=params,
    )

    request = urllib.request.Request(
        url,
        method=normalized_method,
        headers={"x-secret-key": require_control_center_secret_key()},
    )

    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            response_body = response.read().decode("utf-8", errors="replace")
            LOGGER.info(
                "control_center_request_succeeded",
                method=normalized_method,
                path=path,
                status=response.status,
                durationMs=int((time.monotonic() - started_at) * 1000),
            )
            return json.loads(response_body)
    except urllib.error.HTTPError as exc:
        response_body = exc.read().decode("utf-8", errors="replace")
        LOGGER.error(
            "control_center_request_failed",
            method=normalized_method,
            path=path,
            status=exc.code,
            durationMs=int((time.monotonic() - started_at) * 1000),
            responseLength=len(response_body),
            error=exc,
        )
        raise RuntimeError(f"Control Center {path} returned {exc.code}") from exc
    except urllib.error.URLError as exc:
        LOGGER.error(
            "control_center_request_exception",
            method=normalized_method,
            path=path,
            durationMs=int((time.monotonic() - started_at) * 1000),
            error=exc,
        )
        raise RuntimeError(f"Control Center {path} request failed: {exc.reason}") from exc


def normalize_hours(raw_hours: Any) -> float:
    if isinstance(raw_hours, bool) or not isinstance(raw_hours, (int, float)):
        return 24
    if raw_hours != raw_hours or raw_hours in (float("inf"), float("-inf")):
        return 24
    return min(720, max(1, raw_hours))


def normalize_lines(raw_lines: Any) -> int:
    if isinstance(raw_lines, bool) or not isinstance(raw_lines, (int, float)):
        return 200
    if raw_lines != raw_lines or raw_lines in (float("inf"), float("-inf")):
        return 200
    return min(1000, max(1, math.floor(raw_lines + 0.5)))


def require_service_id(service_id: Any) -> str:
    value = service_id.strip() if isinstance(service_id, str) else ""
    if not value:
        raise ValueError("serviceId is required")
    return value


def _dump(payload: Any) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2)


def handle_get_platform_status() -> str:
    system = cc("/control-center/runtime/system")
    instances = cc("/control-center/runtime/instances")
    return _dump({"system": system, "instances": instances})


def handle_get_agents_status() -> str:
    agents = cc("/control-center/runtime/agents")
    instances = cc("/control-center/runtime/instances")
    return _dump({"agents": agents, "instances": instances})


def handle_get_observability_data(hours: Any = None) -> str:
    normalized_hours = normalize_hours(hours)
    to_time = datetime.now(timezone.utc)
    from_time = to_time - timedelta(hours=normalized_hours)
    params = {
        "from": from_time.isoformat().replace("+00:00", "Z"),
        "to": to_time.isoformat().replace("+00:00", "Z"),
    }

    status = cc("/control-center/observability/status")
    if not status.get("enabled"):
        return _dump(
            {
                "error": "Langfuse is not configured. Observability data is unavailable.",
                "status": status,
            }
        )

    if not status.get("reachable"):
        return _dump(
            {
                "error": "Langfuse is configured but not reachable.",
                "status": status,
            }
        )

    overview = cc("/control-center/observability/overview", params)
    traces = cc("/control-center/observability/traces", {**params, "limit": "30"})
    observations = cc("/control-center/observability/observations", params)
    return _dump(
        {
            "timeRange": {
                "from": params["from"],
                "to": params["to"],
                "hours": normalized_hours,
            },
            "overview": overview,
            "traces": traces,
            "observations": observations,
        }
    )


def handle_get_realtime_metrics() -> str:
    return _dump(cc("/control-center/runtime/metrics"))


def handle_list_services() -> str:
    return _dump(cc("/control-center/services"))


def handle_get_service_status(service_id: Any) -> str:
    normalized = urllib.parse.quote(require_service_id(service_id), safe="")
    return _dump(cc(f"/control-center/services/{normalized}"))


def handle_read_service_logs(service_id: Any, lines: Any = None) -> str:
    normalized = urllib.parse.quote(require_service_id(service_id), safe="")
    return _dump(
        cc(
            f"/control-center/services/{normalized}/logs",
            {"lines": normalize_lines(lines)},
        )
    )


def handle_read_service_config(service_id: Any) -> str:
    normalized = urllib.parse.quote(require_service_id(service_id), safe="")
    return _dump(cc(f"/control-center/services/{normalized}/config"))


def handle_list_events() -> str:
    return _dump(cc("/control-center/events"))


def handle_start_service(service_id: Any) -> str:
    normalized = urllib.parse.quote(require_service_id(service_id), safe="")
    return _dump(
        cc(f"/control-center/services/{normalized}/actions/start", method="POST")
    )


def handle_stop_service(service_id: Any) -> str:
    normalized = urllib.parse.quote(require_service_id(service_id), safe="")
    return _dump(
        cc(f"/control-center/services/{normalized}/actions/stop", method="POST")
    )


def handle_restart_service(service_id: Any) -> str:
    normalized = urllib.parse.quote(require_service_id(service_id), safe="")
    return _dump(
        cc(f"/control-center/services/{normalized}/actions/restart", method="POST")
    )
