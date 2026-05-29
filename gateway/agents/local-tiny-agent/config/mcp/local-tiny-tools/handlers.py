import asyncio
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

from logger import log_error, log_info

# ---------------------------------------------------------------------------
# Configuration (evaluated once at import time, matching TS behaviour)
# ---------------------------------------------------------------------------

def _parse_pos_int(raw: Any, fallback: int, lo: int, hi: int) -> int:
    try:
        return max(lo, min(hi, int(str(raw).strip())))
    except Exception:
        return fallback


FETCH_TIMEOUT_MS = _parse_pos_int(os.environ.get("LOCAL_TINY_FETCH_TIMEOUT_MS"), 8000, 1000, 30000)
FETCH_MAX_BYTES = _parse_pos_int(os.environ.get("LOCAL_TINY_FETCH_MAX_BYTES"), 65536, 1024, 262144)
COMMAND_TIMEOUT_MS = _parse_pos_int(os.environ.get("LOCAL_TINY_COMMAND_TIMEOUT_MS"), 5000, 1000, 30000)
COMMAND_MAX_OUTPUT_BYTES = _parse_pos_int(os.environ.get("LOCAL_TINY_MAX_OUTPUT_BYTES"), 65536, 1024, 262144)

_DEFAULT_ALLOWED_HOSTS = ["localhost", "127.0.0.1"]
_DEFAULT_COMMANDS = ["pwd", "ls", "rg", "cat", "head", "tail", "wc", "ps", "top", "lsof", "df", "du"]
# Resolve symlinks so this is stable regardless of how server.py is invoked
_REPO_ROOT = str(Path(__file__).resolve().parents[6])
_DANGEROUS_ARG_RE = re.compile(r"[|;&<>`]")
_COMMANDS_WITH_LIMITED_PATHS = frozenset({"rg", "cat", "head", "tail", "wc", "du"})

TOOLS = [
    {
        "name": "fetch_url_content",
        "description": "Fetch text or JSON content from an allowed local URL. Only GET is supported.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "url": {
                    "type": "string",
                    "description": "URL to fetch. localhost:3000/path is accepted and normalized to http://localhost:3000/path.",
                },
                "max_bytes": {
                    "type": "number",
                    "description": "Optional response byte limit. Defaults to the server limit.",
                    "minimum": 1024,
                    "maximum": FETCH_MAX_BYTES,
                },
            },
            "required": ["url"],
        },
    },
    {
        "name": "run_command",
        "description": "Run a whitelisted local command with bounded arguments, cwd, timeout, and output.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "command": {
                    "type": "string",
                    "description": 'Command name, or a simple command string such as "ls -la".',
                },
                "args": {
                    "type": ["array", "string"],
                    "description": "Arguments as an array or a simple string. Shell operators are rejected.",
                    "items": {"type": "string"},
                },
                "cwd": {
                    "type": "string",
                    "description": "Working directory. Defaults to the MCP process working directory.",
                },
                "timeout_ms": {
                    "type": "number",
                    "description": "Optional timeout in milliseconds.",
                    "minimum": 1000,
                    "maximum": COMMAND_TIMEOUT_MS,
                },
                "max_output_bytes": {
                    "type": "number",
                    "description": "Optional stdout/stderr byte limit.",
                    "minimum": 1024,
                    "maximum": COMMAND_MAX_OUTPUT_BYTES,
                },
            },
            "required": ["command"],
        },
    },
]


# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------

def _normalize_str(value: Any) -> str:
    return str(value).strip() if isinstance(value, str) else ""


def _split_simple_command(s: str) -> list[str]:
    """Split a quoted command string without invoking a shell."""
    parts: list[str] = []
    current = ""
    quote: str | None = None
    for ch in s:
        if ch in ('"', "'") and quote is None:
            quote = ch
        elif ch == quote:
            quote = None
        elif ch.isspace() and quote is None:
            if current:
                parts.append(current)
                current = ""
        else:
            current += ch
    if current:
        parts.append(current)
    return parts


def _normalize_args(args: Any) -> list[str]:
    if isinstance(args, list):
        return [str(a) for a in args if str(a)]
    if isinstance(args, str):
        return _split_simple_command(args)
    return []


def _make_result(
    tool: str,
    ok: bool,
    summary: str,
    data: Any = None,
    truncated: bool = False,
    warnings: list[str] | None = None,
    error: dict | None = None,
) -> str:
    return json.dumps(
        {
            "ok": ok,
            "tool": tool,
            "summary": summary,
            "data": data,
            "truncated": truncated,
            "warnings": warnings or [],
            "error": error,
        },
        indent=2,
    )


def _error_result(
    tool: str, code: str, message: str, warnings: list[str] | None = None
) -> str:
    return _make_result(
        tool,
        ok=False,
        summary=message,
        warnings=warnings,
        error={"code": code, "message": message},
    )


def _allowed_hosts() -> list[str]:
    raw = os.environ.get("LOCAL_TINY_ALLOWED_HOSTS", ",".join(_DEFAULT_ALLOWED_HOSTS))
    return [h.strip().lower() for h in raw.split(",") if h.strip()]


def _allowed_commands() -> list[str]:
    raw = os.environ.get("LOCAL_TINY_ALLOWED_COMMANDS", ",".join(_DEFAULT_COMMANDS))
    return [c.strip() for c in raw.split(",") if c.strip()]


def _normalize_url(raw: Any) -> urllib.parse.ParseResult | None:
    s = _normalize_str(raw)
    if not s:
        return None
    if not re.match(r"^[a-zA-Z][a-zA-Z0-9+.\-]*://", s):
        s = f"http://{s}"
    try:
        p = urllib.parse.urlparse(s)
        return p if p.scheme and p.netloc else None
    except Exception:
        return None


def _is_readable_content_type(ct: str) -> bool:
    ct = ct.lower()
    return (
        ct.startswith("text/")
        or "json" in ct
        or "xml" in ct
        or "yaml" in ct
        or "javascript" in ct
    )


# ---------------------------------------------------------------------------
# fetch_url_content
# ---------------------------------------------------------------------------

def _do_fetch(url_str: str, max_bytes: int, timeout_s: float, allowed: list[str]) -> dict:
    """Synchronous GET with bounded body read. Blocks foreign redirects before
    following them (improvement over the TS post-hoc check)."""

    class _BlockForeignRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            parsed = urllib.parse.urlparse(newurl)
            hostname = (parsed.hostname or "").lower()
            if hostname and hostname not in allowed:
                err = urllib.error.URLError(f"Redirect to disallowed host: {hostname}")
                err._redirect_host = hostname  # type: ignore[attr-defined]
                raise err
            return super().redirect_request(req, fp, code, msg, headers, newurl)

    opener = urllib.request.build_opener(_BlockForeignRedirect())
    req = urllib.request.Request(url_str, method="GET")

    try:
        with opener.open(req, timeout=timeout_s) as resp:
            final_url: str = resp.geturl()
            status: int = resp.status
            content_type: str = resp.getheader("content-type") or ""
            chunks: list[bytes] = []
            total = 0
            while total < max_bytes:
                chunk = resp.read(min(8192, max_bytes - total))
                if not chunk:
                    break
                chunks.append(chunk)
                total += len(chunk)
            truncated = bool(resp.read(1))
            body = b"".join(chunks).decode("utf-8", errors="replace")
            return {
                "ok": True,
                "status": status,
                "content_type": content_type,
                "body": body,
                "bytes": total,
                "truncated": truncated,
                "final_url": final_url,
            }
    except urllib.error.HTTPError as e:
        try:
            body = e.read(min(4096, max_bytes)).decode("utf-8", errors="replace")
        except Exception:
            body = ""
        return {
            "is_http_error": True,
            "status": e.code,
            "content_type": e.headers.get("content-type") or "",
            "body": body,
            "bytes": len(body.encode("utf-8")),
            "truncated": False,
            "final_url": e.url or url_str,
        }
    except urllib.error.URLError as e:
        if hasattr(e, "_redirect_host"):
            return {"redirect_host_blocked": e._redirect_host}  # type: ignore[attr-defined]
        raise


async def handle_fetch_url_content(args: dict) -> str:
    tool = "fetch_url_content"

    parsed = _normalize_url(args.get("url"))
    if not parsed:
        return _error_result(tool, "URL_REQUIRED", "A valid url string is required.")

    if parsed.scheme not in ("http", "https"):
        return _error_result(tool, "URL_SCHEME_NOT_ALLOWED", "Only http and https URLs are allowed.")

    allowed = _allowed_hosts()
    hostname = (parsed.hostname or "").lower()
    if hostname not in allowed:
        return _error_result(
            tool,
            "HOST_NOT_ALLOWED",
            f"Host {parsed.hostname} is not allowed. Allowed hosts: {', '.join(allowed)}.",
        )

    max_bytes = _parse_pos_int(args.get("max_bytes"), FETCH_MAX_BYTES, 1024, FETCH_MAX_BYTES)
    url_str = parsed.geturl()
    timeout_s = FETCH_TIMEOUT_MS / 1000
    loop = asyncio.get_running_loop()
    started_at = loop.time()
    log_info("fetch_started", {"url": url_str, "maxBytes": max_bytes})

    try:
        result = await asyncio.to_thread(_do_fetch, url_str, max_bytes, timeout_s, allowed)
    except Exception as exc:
        duration_ms = int((loop.time() - started_at) * 1000)
        log_error("fetch_failed", {"url": url_str, "durationMs": duration_ms, "error": str(exc)})
        return _error_result(tool, "FETCH_FAILED", str(exc))

    if "redirect_host_blocked" in result:
        return _error_result(
            tool,
            "REDIRECT_HOST_NOT_ALLOWED",
            f"Redirect target host {result['redirect_host_blocked']} is not allowed.",
        )

    if result.get("is_http_error"):
        return _make_result(
            tool,
            ok=False,
            summary=f"URL returned HTTP {result['status']}.",
            data={
                "url": result["final_url"],
                "requested_url": url_str,
                "status": result["status"],
                "content_type": result["content_type"],
                "body": result["body"],
            },
            truncated=result["truncated"],
            error={"code": "HTTP_ERROR", "message": f"URL returned HTTP {result['status']}."},
        )

    ct = result["content_type"]
    if not _is_readable_content_type(ct):
        return _make_result(
            tool,
            ok=False,
            summary=f"Content type {ct or 'unknown'} is not readable as text.",
            data={
                "url": result["final_url"],
                "requested_url": url_str,
                "status": result["status"],
                "content_type": ct or None,
            },
            error={
                "code": "CONTENT_TYPE_NOT_READABLE",
                "message": "The response is not text, JSON, XML, YAML, or JavaScript.",
            },
        )

    return _make_result(
        tool,
        ok=True,
        summary=f"Fetched {url_str} with HTTP {result['status']}.",
        data={
            "url": result["final_url"],
            "requested_url": url_str,
            "status": result["status"],
            "content_type": ct or None,
            "bytes": result["bytes"],
            "body": result["body"],
        },
        truncated=result["truncated"],
        warnings=(
            [f"Response exceeded {max_bytes} bytes and was truncated."]
            if result["truncated"]
            else []
        ),
    )


# ---------------------------------------------------------------------------
# run_command
# ---------------------------------------------------------------------------

async def _allowed_roots() -> list[str]:
    default = f"{os.getcwd()},{_REPO_ROOT}"
    raw = os.environ.get("LOCAL_TINY_COMMAND_ROOTS", default)
    seen: dict[str, None] = {}
    for root in [r.strip() for r in raw.split(",") if r.strip()]:
        p = Path(root)
        if p.exists():
            try:
                seen[str(p.resolve())] = None
            except OSError as exc:
                # Symlink loop, permission denied, or transient FS error during
                # resolve(): skip this root but record why so misconfigured
                # LOCAL_TINY_COMMAND_ROOTS entries are debuggable.
                log_info("allowed_root_skipped", {"root": root, "error": str(exc)})
    return list(seen.keys())


async def _normalize_cwd(raw_cwd: Any) -> dict:
    requested = _normalize_str(raw_cwd) or os.getcwd()
    roots = await _allowed_roots()
    try:
        absolute = Path(requested).resolve()
        if not absolute.exists():
            return {"ok": False, "error": f"cwd does not exist: {requested}", "roots": roots}
        if not absolute.is_dir():
            return {"ok": False, "error": f"cwd is not a directory: {requested}", "roots": roots}
    except Exception:
        return {"ok": False, "error": f"cwd does not exist: {requested}", "roots": roots}

    real_cwd = str(absolute)
    if not any(real_cwd == r or real_cwd.startswith(r + "/") for r in roots):
        return {"ok": False, "error": f"cwd is outside allowed roots: {requested}", "roots": roots}
    return {"ok": True, "cwd": real_cwd, "roots": roots}


def _normalize_command_input(args: dict) -> tuple[str, list[str]]:
    command_input = _normalize_str(args.get("command"))
    if not command_input:
        return "", []
    parts = _split_simple_command(command_input)
    command = parts[0] if parts else ""
    return command, parts[1:] + _normalize_args(args.get("args"))


def _reject_unsafe_args(command_args: list[str]) -> str | None:
    for arg in command_args:
        if _DANGEROUS_ARG_RE.search(arg) or "$(" in arg:
            return f"Argument contains a shell operator or unsafe expansion: {arg}"
        if "\x00" in arg or "\n" in arg or "\r" in arg:
            return "Arguments may not contain control characters or newlines."
    return None


def _normalize_command_args(command: str, command_args: list[str]) -> list[str]:
    if command == "top":
        return ["-l", "1", "-n", "20"] if sys.platform == "darwin" else ["-b", "-n", "1"]
    if command == "rg":
        has_limit = any(
            a in ("-m", "--max-count") or a.startswith("--max-count=") for a in command_args
        )
        return command_args if has_limit else ["--max-count", "50", *command_args]
    if command == "du":
        has_depth = any(a == "-d" or a.startswith("-d") or a.startswith("--max-depth") for a in command_args)
        if has_depth:
            return command_args
        return ["-d", "2", *command_args] if sys.platform == "darwin" else ["--max-depth=2", *command_args]
    return command_args


def _reject_pathless_read(command: str, command_args: list[str]) -> str | None:
    if command in _COMMANDS_WITH_LIMITED_PATHS and not command_args:
        return f"{command} requires at least one argument so it cannot wait for stdin."
    return None


async def _run_child(
    command: str,
    command_args: list[str],
    cwd: str,
    timeout_ms: int,
    max_bytes: int,
) -> dict:
    env = {
        "PATH": os.environ.get("PATH", "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"),
        "LANG": os.environ.get("LANG", "C.UTF-8"),
    }
    try:
        proc = await asyncio.create_subprocess_exec(
            command,
            *command_args,
            cwd=cwd,
            env=env,
            stdin=asyncio.subprocess.DEVNULL,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
    except Exception as exc:
        return {"exit_code": None, "timed_out": False, "error": exc,
                "stdout": "", "stderr": "", "truncated": False}

    truncated = False

    async def _drain(stream: asyncio.StreamReader) -> bytes:
        nonlocal truncated
        chunks: list[bytes] = []
        stored = 0
        while True:
            chunk = await stream.read(8192)
            if not chunk:
                break
            remaining = max_bytes - stored
            if remaining <= 0:
                truncated = True
            elif len(chunk) <= remaining:
                chunks.append(chunk)
                stored += len(chunk)
            else:
                chunks.append(chunk[:remaining])
                stored = max_bytes
                truncated = True
        return b"".join(chunks)

    stdout_task = asyncio.create_task(_drain(proc.stdout))
    stderr_task = asyncio.create_task(_drain(proc.stderr))

    timed_out = False
    try:
        await asyncio.wait_for(proc.wait(), timeout=timeout_ms / 1000)
    except asyncio.TimeoutError:
        timed_out = True
        proc.terminate()
        try:
            await asyncio.wait_for(proc.wait(), timeout=0.5)
        except asyncio.TimeoutError:
            proc.kill()
            await proc.wait()

    stdout_bytes = await stdout_task
    stderr_bytes = await stderr_task

    return {
        "exit_code": proc.returncode,
        "timed_out": timed_out,
        "error": None,
        "stdout": stdout_bytes.decode("utf-8", errors="replace"),
        "stderr": stderr_bytes.decode("utf-8", errors="replace"),
        "truncated": truncated,
    }


async def handle_run_command(args: dict) -> str:
    tool = "run_command"
    command, command_args_raw = _normalize_command_input(args)
    if not command:
        return _error_result(tool, "COMMAND_REQUIRED", "A command string is required.")

    allowed = _allowed_commands()
    if command not in allowed:
        return _error_result(
            tool,
            "COMMAND_NOT_ALLOWED",
            f'Command "{command}" is not allowed. Allowed commands: {", ".join(allowed)}.',
        )

    unsafe = _reject_unsafe_args(command_args_raw)
    if unsafe:
        return _error_result(tool, "UNSAFE_ARGUMENT", unsafe)

    stdin_risk = _reject_pathless_read(command, command_args_raw)
    if stdin_risk:
        return _error_result(tool, "ARGUMENT_REQUIRED", stdin_risk)

    cwd_result = await _normalize_cwd(args.get("cwd"))
    if not cwd_result["ok"]:
        return _make_result(
            tool,
            ok=False,
            summary=cwd_result["error"],
            data={"allowed_roots": cwd_result["roots"]},
            error={"code": "CWD_NOT_ALLOWED", "message": cwd_result["error"]},
        )

    command_args = _normalize_command_args(command, command_args_raw)
    timeout_ms = _parse_pos_int(args.get("timeout_ms"), COMMAND_TIMEOUT_MS, 1000, COMMAND_TIMEOUT_MS)
    max_bytes = _parse_pos_int(args.get("max_output_bytes"), COMMAND_MAX_OUTPUT_BYTES, 1024, COMMAND_MAX_OUTPUT_BYTES)

    loop = asyncio.get_running_loop()
    started_at = loop.time()
    log_info("command_started", {
        "command": command,
        "args": command_args,
        "cwd": cwd_result["cwd"],
        "timeoutMs": timeout_ms,
        "maxBytes": max_bytes,
    })

    result = await _run_child(command, command_args, cwd_result["cwd"], timeout_ms, max_bytes)
    duration_ms = int((loop.time() - started_at) * 1000)

    if result["error"]:
        log_error("command_failed_to_start", {
            "command": command, "args": command_args,
            "durationMs": duration_ms, "error": str(result["error"]),
        })
        return _error_result(tool, "COMMAND_START_FAILED", str(result["error"]))

    ok = result["exit_code"] == 0 and not result["timed_out"]
    return _make_result(
        tool,
        ok=ok,
        summary=(
            f"Command timed out after {timeout_ms} ms."
            if result["timed_out"]
            else f"Command exited with code {result['exit_code']}."
        ),
        data={
            "command": command,
            "args": command_args,
            "cwd": cwd_result["cwd"],
            "exit_code": result["exit_code"],
            "signal": None,
            "duration_ms": duration_ms,
            "stdout": result["stdout"],
            "stderr": result["stderr"],
        },
        truncated=result["truncated"],
        warnings=[
            *([f"Output exceeded {max_bytes} bytes and was truncated."] if result["truncated"] else []),
            *(["top arguments were normalized to one-shot non-interactive mode."] if command == "top" else []),
        ],
        error=None if ok else {
            "code": "COMMAND_TIMEOUT" if result["timed_out"] else "COMMAND_EXIT_NONZERO",
            "message": (
                f"Command timed out after {timeout_ms} ms."
                if result["timed_out"]
                else f"Command exited with code {result['exit_code']}."
            ),
        },
    )


async def dispatch(name: str, args: dict | None = None) -> str:
    args = args or {}
    loop = asyncio.get_running_loop()
    started_at = loop.time()
    log_info("tool_dispatch_started", {"tool": name, "args": args})

    if name == "fetch_url_content":
        result = await handle_fetch_url_content(args)
    elif name == "run_command":
        result = await handle_run_command(args)
    else:
        result = _error_result(name, "UNKNOWN_TOOL", f"Unknown tool: {name}")

    log_info("tool_dispatch_finished", {
        "tool": name,
        "durationMs": int((loop.time() - started_at) * 1000),
    })
    return result
