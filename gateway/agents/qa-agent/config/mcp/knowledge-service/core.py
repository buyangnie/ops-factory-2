#!/usr/bin/env python3
import json
import os
import sys
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional


KNOWLEDGE_SERVICE_URL = os.environ.get("KNOWLEDGE_SERVICE_URL", "http://127.0.0.1:8092").rstrip("/")
def _parse_timeout_seconds() -> float:
    try:
        value = int(os.environ.get("KNOWLEDGE_REQUEST_TIMEOUT_MS", "15000"))
    except ValueError:
        value = 15000
    return value / 1000


KNOWLEDGE_REQUEST_TIMEOUT_SECONDS = _parse_timeout_seconds()
KNOWLEDGE_FETCH_MAX_NEIGHBOR_WINDOW = 2
API_PREFIX = "/knowledge"
CONFIG_FILE_PATH = Path(__file__).resolve().parents[2] / "config.yaml"
LOG_FILE_NAME = "knowledge_service.log"


class McpLogger:
    def __init__(self) -> None:
        root = Path(os.environ.get("GOOSE_PATH_ROOT") or os.getcwd())
        self.log_dir = root / "logs" / "mcp"
        self.log_dir.mkdir(parents=True, exist_ok=True)
        self.log_file = self.log_dir / LOG_FILE_NAME

    def _write(self, level: str, event: str, **fields: Any) -> None:
        payload: Dict[str, Any] = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "level": level,
            "service": "knowledge_service",
            "event": event,
        }
        for key, value in fields.items():
            sanitized = _sanitize_log_value(value)
            if sanitized is not None:
                payload[key] = sanitized
        line = json.dumps(payload, ensure_ascii=True)
        print(line, file=sys.stderr, flush=True)
        with self.log_file.open("a", encoding="utf-8") as fp:
            fp.write(line + "\n")

    def info(self, event: str, **fields: Any) -> None:
        self._write("INFO", event, **fields)

    def error(self, event: str, **fields: Any) -> None:
        self._write("ERROR", event, **fields)

    def exception(self, event: str, **fields: Any) -> None:
        fields = dict(fields)
        fields["traceback"] = traceback.format_exc()
        self._write("ERROR", event, **fields)


def _sanitize_log_value(value: Any) -> Any:
    if isinstance(value, BaseException):
        return {
            "name": type(value).__name__,
            "message": str(value),
        }
    try:
        json.dumps(value)
        return value
    except TypeError:
        return str(value)


LOGGER = McpLogger()


def summarize_tool_args(args: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "keys": sorted(args.keys()),
        "hasQuery": isinstance(args.get("query"), str) and len(args.get("query", "")) > 0,
        "queryLength": len(args["query"]) if isinstance(args.get("query"), str) else None,
        "sourceIdCount": len(args["sourceIds"]) if isinstance(args.get("sourceIds"), list) else None,
        "documentIdCount": len(args["documentIds"]) if isinstance(args.get("documentIds"), list) else None,
        "hasChunkId": isinstance(args.get("chunkId"), str) and len(args.get("chunkId", "")) > 0,
        "includeNeighbors": bool(args.get("includeNeighbors")) if "includeNeighbors" in args else None,
        "neighborWindow": args.get("neighborWindow") if isinstance(args.get("neighborWindow"), int) else None,
        "topK": args.get("topK") if isinstance(args.get("topK"), (int, float)) else None,
    }


def _parse_yaml_scalar(value: str) -> Optional[str]:
    value = value.strip()
    if not value:
        return None
    if value[0] in {"'", '"'} and value[-1:] == value[0]:
        return value[1:-1]
    return value


def _extract_nested_scalar(content: str, path: List[str]) -> Optional[str]:
    stack: List[tuple[int, str]] = []
    for raw_line in content.splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        stripped = raw_line.strip()
        if ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        key = key.strip().strip("'\"")
        while stack and indent <= stack[-1][0]:
            stack.pop()
        current_path = [item[1] for item in stack] + [key]
        if current_path == path:
            return _parse_yaml_scalar(value)
        stack.append((indent, key))
    return None


def read_configured_source_id() -> Optional[str]:
    try:
        content = CONFIG_FILE_PATH.read_text(encoding="utf-8")
    except OSError:
        return None
    value = _extract_nested_scalar(
        content,
        ["extensions", "knowledge-service", "x-opsfactory", "knowledgeScope", "sourceId"],
    )
    return value.strip() if value and value.strip() else None


def normalize_source_ids(source_ids: Any) -> List[str]:
    if isinstance(source_ids, list) and source_ids:
        return [item.strip() for item in source_ids if isinstance(item, str) and item.strip()]
    configured = read_configured_source_id()
    return [configured] if configured else []


def _knowledge_request(method: str, path: str, body: Optional[Any] = None) -> Any:
    started_at = time.monotonic()
    url = f"{KNOWLEDGE_SERVICE_URL}{path}"
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    LOGGER.info("knowledge_request_started", method=method, path=path)
    try:
        with urllib.request.urlopen(request, timeout=KNOWLEDGE_REQUEST_TIMEOUT_SECONDS) as response:
            response_body = response.read().decode("utf-8", errors="replace")
            LOGGER.info(
                "knowledge_request_succeeded",
                method=method,
                path=path,
                status=response.status,
                durationMs=int((time.monotonic() - started_at) * 1000),
            )
            return json.loads(response_body)
    except urllib.error.HTTPError as exc:
        response_body = exc.read().decode("utf-8", errors="replace")
        LOGGER.error(
            "knowledge_request_failed",
            method=method,
            path=path,
            status=exc.code,
            durationMs=int((time.monotonic() - started_at) * 1000),
            responseLength=len(response_body),
        )
        raise RuntimeError(f"Knowledge service {path} returned {exc.code}: {response_body}") from exc
    except urllib.error.URLError as exc:
        LOGGER.error(
            "knowledge_request_failed",
            method=method,
            path=path,
            durationMs=int((time.monotonic() - started_at) * 1000),
            error=exc,
        )
        raise RuntimeError(f"Knowledge service {path} request failed: {exc.reason}") from exc


def handle_search(
    query: str,
    sourceIds: Optional[List[str]] = None,
    documentIds: Optional[List[str]] = None,
    topK: Any = 8,
) -> str:
    query_text = query.strip() if isinstance(query, str) else ""
    if not query_text:
        raise ValueError("search.query is required")

    source_ids = normalize_source_ids(sourceIds)
    if not source_ids:
        LOGGER.info("knowledge_scope_empty_search_skipped", queryLength=len(query_text))
        return json.dumps({"query": query_text, "hits": [], "total": 0}, ensure_ascii=False, indent=2)

    body = {
        "query": query_text,
        "sourceIds": source_ids,
        "documentIds": documentIds if isinstance(documentIds, list) else [],
        "topK": topK if topK is not None else 8,
    }
    result = _knowledge_request("POST", f"{API_PREFIX}/search", body)
    return json.dumps(result, ensure_ascii=False, indent=2)


def handle_fetch(chunkId: str, includeNeighbors: bool = False, neighborWindow: Any = 1) -> str:
    chunk_id = chunkId.strip() if isinstance(chunkId, str) else ""
    if not chunk_id:
        raise ValueError("fetch.chunkId is required")
    if not isinstance(neighborWindow, int) or isinstance(neighborWindow, bool):
        raise ValueError(
            f"fetch.neighborWindow must be an integer between 1 and {KNOWLEDGE_FETCH_MAX_NEIGHBOR_WINDOW}"
        )
    if neighborWindow < 1 or neighborWindow > KNOWLEDGE_FETCH_MAX_NEIGHBOR_WINDOW:
        raise ValueError(
            f"fetch.neighborWindow must be an integer between 1 and {KNOWLEDGE_FETCH_MAX_NEIGHBOR_WINDOW}"
        )

    params = urllib.parse.urlencode(
        {
            "includeNeighbors": str(bool(includeNeighbors)).lower(),
            "neighborWindow": str(neighborWindow),
            "includeMarkdown": "true",
            "includeRawText": "true",
        }
    )
    result = _knowledge_request("GET", f"{API_PREFIX}/fetch/{urllib.parse.quote(chunk_id, safe='')}?{params}")
    return json.dumps(result, ensure_ascii=False, indent=2)


def dispatch(name: str, arguments: Optional[Dict[str, Any]] = None) -> str:
    args = arguments or {}
    started_at = time.monotonic()
    LOGGER.info("tool_dispatch_started", tool=name, args=summarize_tool_args(args))
    try:
        if name == "search":
            result = handle_search(
                str(args.get("query", "")),
                args.get("sourceIds") if isinstance(args.get("sourceIds"), list) else None,
                args.get("documentIds") if isinstance(args.get("documentIds"), list) else None,
                args.get("topK", 8),
            )
        elif name == "fetch":
            result = handle_fetch(
                str(args.get("chunkId", "")),
                bool(args.get("includeNeighbors", False)),
                args.get("neighborWindow", 1),
            )
        else:
            raise ValueError(f"Unknown tool: {name}")
        LOGGER.info("tool_dispatch_succeeded", tool=name, durationMs=int((time.monotonic() - started_at) * 1000))
        return result
    except Exception as exc:
        LOGGER.error(
            "tool_dispatch_failed",
            tool=name,
            args=summarize_tool_args(args),
            durationMs=int((time.monotonic() - started_at) * 1000),
            error=exc,
        )
        raise
