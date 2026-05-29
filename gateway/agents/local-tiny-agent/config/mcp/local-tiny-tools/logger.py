import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

_LOG_ROOT = os.environ.get("GOOSE_PATH_ROOT") or os.getcwd()
_LOG_DIR = Path(_LOG_ROOT) / "logs" / "mcp"
_LOG_DIR.mkdir(parents=True, exist_ok=True)

LOG_FILE_PATH = str(_LOG_DIR / "local_tiny_tools.log")


def _sanitize(value: object) -> object:
    if isinstance(value, Exception):
        return {"name": type(value).__name__, "message": str(value)}
    try:
        json.dumps(value)
        return value
    except Exception:
        return str(value)


def log(level: str, event: str, details: dict | None = None) -> None:
    payload: dict = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "level": level,
        "service": "local_tiny_tools",
        "event": event,
    }
    for key, value in (details or {}).items():
        sanitized = _sanitize(value)
        if sanitized is not None:
            payload[key] = sanitized

    line = json.dumps(payload)
    sys.stderr.write(f"{line}\n")
    sys.stderr.flush()
    try:
        with open(LOG_FILE_PATH, "a", encoding="utf-8") as f:
            f.write(f"{line}\n")
    except OSError as exc:
        # File-side logging is best-effort: stderr is the MCP-safe primary
        # channel (already written above). Surface the failure once so a
        # read-only / out-of-space log mount isn't completely invisible, but
        # never raise from the logger — that would crash whoever called us.
        diag = json.dumps({
            "level": "ERROR",
            "service": "local_tiny_tools",
            "event": "log_file_write_failed",
            "path": LOG_FILE_PATH,
            "error": f"{type(exc).__name__}: {exc}",
        })
        sys.stderr.write(f"{diag}\n")
        sys.stderr.flush()


def log_info(event: str, details: dict | None = None) -> None:
    log("INFO", event, details)


def log_error(event: str, details: dict | None = None) -> None:
    log("ERROR", event, details)
