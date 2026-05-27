#!/usr/bin/env python3
import json
import sys
from datetime import datetime
from typing import Any, Dict, Optional


JSONRPC_VERSION = "2.0"
NEGOTIATED_PROTOCOL_VERSION = "2025-03-26"
SERVER_NAME = "current-time"
SERVER_VERSION = "1.0.0"
TIME_FORMAT = "%Y-%m-%d %H:%M:%S"

TOOLS = [
    {
        "name": "get_current_time",
        "description": "获取当前本地时间，返回格式为 YYYY-MM-DD HH:MM:SS，例如 2026-01-12 13:00:28",
        "inputSchema": {
            "type": "object",
            "properties": {},
        },
    },
]


def make_success_response(request_id: Any, result: Any) -> Dict[str, Any]:
    return {"jsonrpc": JSONRPC_VERSION, "id": request_id, "result": result}


def make_error_response(request_id: Any, code: int, message: str) -> Dict[str, Any]:
    response: Dict[str, Any] = {
        "jsonrpc": JSONRPC_VERSION,
        "error": {"code": code, "message": message},
    }
    if request_id is not None:
        response["id"] = request_id
    return response


def format_tool_result(data: str, *, is_error: bool = False) -> Dict[str, Any]:
    return {
        "content": [{"type": "text", "text": data}],
        "isError": is_error,
    }


def dispatch_tool(name: str) -> str:
    if name == "get_current_time":
        return datetime.now().strftime(TIME_FORMAT)
    raise KeyError(name)


def handle_request(message: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    request_id = message.get("id")
    method = message.get("method")
    params = message.get("params") or {}

    if method == "initialize":
        return make_success_response(
            request_id,
            {
                "protocolVersion": NEGOTIATED_PROTOCOL_VERSION,
                "capabilities": {"tools": {}},
                "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
                "instructions": "Use get_current_time to fetch the current local time.",
            },
        )

    if method == "notifications/initialized":
        return None

    if method == "ping":
        return make_success_response(request_id, {})

    if method == "tools/list":
        return make_success_response(request_id, {"tools": TOOLS})

    if method == "tools/call":
        name = params.get("name")
        if not isinstance(name, str) or not name:
            return make_error_response(request_id, -32602, "Invalid params: tools/call requires a tool name")
        arguments = params.get("arguments") or {}
        if not isinstance(arguments, dict):
            return make_error_response(request_id, -32602, "Invalid params: tool arguments must be an object")

        try:
            return make_success_response(request_id, format_tool_result(dispatch_tool(name)))
        except KeyError:
            return make_error_response(request_id, -32601, f"Unknown tool: {name}")
        except Exception:  # pragma: no cover - defensive path
            return make_success_response(
                request_id,
                format_tool_result("Internal server error", is_error=True),
            )

    return make_error_response(request_id, -32601, f"Method not found: {method}")


def send_message(message: Dict[str, Any]) -> None:
    sys.stdout.write(json.dumps(message, ensure_ascii=True) + "\n")
    sys.stdout.flush()


def main() -> int:
    for raw_line in sys.stdin:
        line = raw_line.strip()
        if not line:
            continue

        try:
            message = json.loads(line)
        except json.JSONDecodeError:
            send_message(make_error_response(None, -32700, "Parse error"))
            continue

        if not isinstance(message, dict):
            send_message(make_error_response(None, -32600, "Invalid Request"))
            continue

        response = handle_request(message)
        if response is not None:
            send_message(response)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
