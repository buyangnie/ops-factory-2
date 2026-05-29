#!/usr/bin/env python3
import json
import os
import re
import selectors
import shutil
import subprocess
import sys
import time
import traceback
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional


CONFIG_FILE_PATH = Path(__file__).resolve().parents[2] / "config.yaml"
CONFIG_DIR = CONFIG_FILE_PATH.parent
DEFAULT_ROOT_DIR = "../data"
DEFAULT_FIND_LIMIT = 100
DEFAULT_SEARCH_LIMIT = 50
DEFAULT_READ_WINDOW = 120
MAX_FIND_LIMIT = 500
MAX_SEARCH_LIMIT = 200
MAX_READ_WINDOW = 200
MAX_READ_OUTPUT_CHARS = 24_000
COMMAND_TIMEOUT_SECONDS = 20
MAX_STDERR_CHARS = 16_000
LOG_FILE_NAME = "knowledge_cli.log"


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
            "service": "knowledge_cli",
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


@dataclass(frozen=True)
class RootDirContext:
    root_dir: Path
    exists: bool


@dataclass(frozen=True)
class ScopeContext:
    root_dir: Path
    scope_path: Path
    exists: bool


@dataclass(frozen=True)
class CommandResult:
    lines: List[str]
    stderr: str
    code: int
    truncated: bool


def clamp(value: Any, minimum: int, maximum: int, fallback: int) -> int:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        number = fallback
    else:
        number = int(value)
    return max(minimum, min(maximum, number))


def normalize_glob(value: Any) -> Optional[str]:
    if not isinstance(value, str) or not value.strip():
        return None
    glob = value.strip()
    segments = re.split(r"[\\/]+", glob)
    if "\0" in glob or glob.startswith("!") or Path(glob).is_absolute() or ".." in segments:
        raise ValueError(f"Invalid glob pattern: {glob}")
    return glob


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


def extract_configured_root_dir(content: str) -> Optional[str]:
    value = _extract_nested_scalar(
        content,
        ["extensions", "knowledge-cli", "x-opsfactory", "scope", "rootDir"],
    )
    return value.strip() if value and value.strip() else None


def read_configured_root_dir() -> str:
    env_root = os.environ.get("QA_CLI_ROOT_DIR", "").strip()
    if env_root:
        return env_root
    try:
        configured = extract_configured_root_dir(CONFIG_FILE_PATH.read_text(encoding="utf-8"))
        if configured:
            return configured
    except OSError:
        # The agent can run before config.yaml is materialized; fall back to the default data directory.
        return DEFAULT_ROOT_DIR
    return DEFAULT_ROOT_DIR


def _is_within_root(root_dir: Path, candidate_path: Path) -> bool:
    try:
        candidate_path.relative_to(root_dir)
        return True
    except ValueError:
        return False


def get_root_dir_context() -> RootDirContext:
    configured = read_configured_root_dir()
    resolved = Path(configured)
    if not resolved.is_absolute():
        resolved = CONFIG_DIR / resolved
    try:
        return RootDirContext(resolved.resolve(strict=True), True)
    except OSError:
        return RootDirContext(resolved.resolve(strict=False), False)


def resolve_scope_path(path_prefix: Any = None) -> ScopeContext:
    root = get_root_dir_context()
    prefix = path_prefix.strip() if isinstance(path_prefix, str) else ""
    candidate = root.root_dir / prefix if prefix else root.root_dir
    candidate = candidate.resolve(strict=False)
    if not _is_within_root(root.root_dir, candidate):
        raise ValueError(f"Path escapes configured rootDir: {candidate}")
    if not candidate.exists():
        return ScopeContext(root.root_dir, candidate, False)
    real_candidate = candidate.resolve(strict=True)
    if not _is_within_root(root.root_dir, real_candidate):
        raise ValueError(f"Resolved path escapes configured rootDir: {real_candidate}")
    return ScopeContext(root.root_dir, real_candidate, True)


def resolve_readable_file(file_path: Any) -> Path:
    root = get_root_dir_context()
    if not root.exists:
        raise ValueError(f"Configured rootDir does not exist: {root.root_dir}")
    if not isinstance(file_path, str) or not file_path.strip():
        raise ValueError("read_file.path is required")
    candidate = Path(file_path)
    if not candidate.is_absolute():
        candidate = root.root_dir / candidate
    real_file = candidate.resolve(strict=True)
    if not _is_within_root(root.root_dir, real_file):
        raise ValueError(f"File escapes configured rootDir: {real_file}")
    if not real_file.is_file():
        raise ValueError(f"Path is not a file: {real_file}")
    return real_file


def run_command_lines(command: str, args: List[str], limit: int) -> CommandResult:
    LOGGER.info("command_started", command=command, mode="stream", limit=limit, argCount=len(args))
    started_at = time.monotonic()
    process = subprocess.Popen(
        [command, *args],
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    lines: List[str] = []
    stdout_buffer = ""
    stderr_parts: List[str] = []
    stderr_chars = 0
    truncated = False
    timed_out = False

    def append_stdout(text: str) -> None:
        nonlocal stdout_buffer, truncated
        stdout_buffer += text
        while "\n" in stdout_buffer:
            line, stdout_buffer = stdout_buffer.split("\n", 1)
            if len(lines) < limit:
                lines.append(line.rstrip("\r"))
            else:
                truncated = True
                process.kill()
                return

    def append_stderr(text: str) -> None:
        nonlocal stderr_chars
        remaining = MAX_STDERR_CHARS - stderr_chars
        if remaining <= 0:
            return
        chunk = text[:remaining]
        stderr_parts.append(chunk)
        stderr_chars += len(chunk)

    def communicate_after_kill() -> tuple[bytes, bytes]:
        try:
            return process.communicate(timeout=1)
        except subprocess.TimeoutExpired:
            process.kill()
            return process.communicate(timeout=1)

    try:
        if process.stdout is None or process.stderr is None:
            process.kill()
            raise RuntimeError(f"{command} did not expose stdout/stderr pipes")
        selector = selectors.DefaultSelector()
        selector.register(process.stdout, selectors.EVENT_READ, "stdout")
        selector.register(process.stderr, selectors.EVENT_READ, "stderr")

        try:
            while selector.get_map():
                elapsed = time.monotonic() - started_at
                remaining = COMMAND_TIMEOUT_SECONDS - elapsed
                if remaining <= 0:
                    timed_out = True
                    process.kill()
                    break

                events = selector.select(timeout=min(0.1, remaining))
                if not events:
                    if process.poll() is not None:
                        break
                    continue

                for key, _mask in events:
                    data = os.read(key.fileobj.fileno(), 4096)
                    if not data:
                        selector.unregister(key.fileobj)
                        continue
                    text = data.decode("utf-8", errors="replace")
                    if key.data == "stdout":
                        append_stdout(text)
                    else:
                        append_stderr(text)
                    if truncated:
                        break
                if truncated:
                    break
        finally:
            selector.close()

        if not truncated and not timed_out and stdout_buffer:
            if len(lines) < limit:
                lines.append(stdout_buffer.rstrip("\r\n"))
            else:
                truncated = True
                process.kill()

        if truncated:
            try:
                _stdout_tail, stderr_tail = process.communicate(timeout=1)
                append_stderr(stderr_tail.decode("utf-8", errors="replace"))
            except subprocess.TimeoutExpired:
                process.kill()
                _stdout_tail, stderr_tail = process.communicate(timeout=1)
                append_stderr(stderr_tail.decode("utf-8", errors="replace"))
            code = 0
        else:
            code = process.wait(timeout=1)
        stderr = "".join(stderr_parts)
    except subprocess.TimeoutExpired:
        timed_out = True
        process.kill()
        _stdout_tail, stderr_tail = communicate_after_kill()
        append_stderr(stderr_tail.decode("utf-8", errors="replace"))
        stderr = "".join(stderr_parts)
        code = process.wait()
    finally:
        if process.stdout:
            process.stdout.close()
        if process.stderr:
            process.stderr.close()

    if timed_out:
        raise TimeoutError(
            f"{command} timed out after {COMMAND_TIMEOUT_SECONDS}s. Try a narrower pathPrefix, glob, or query."
        )

    LOGGER.info("command_succeeded", command=command, mode="stream", lines=len(lines), truncated=truncated)
    return CommandResult(lines=lines, stderr=stderr, code=0 if truncated else code, truncated=truncated)


def get_search_engine() -> str:
    return "rg" if shutil.which("rg") else "grep"


def parse_rg_line(line: str) -> Optional[Dict[str, Any]]:
    match = re.match(r"^(.*?):(\d+):(\d+):(.*)$", line)
    if not match:
        return None
    return {
        "path": match.group(1),
        "line": int(match.group(2)),
        "column": int(match.group(3)),
        "preview": match.group(4).strip(),
    }


def parse_grep_line(line: str) -> Optional[Dict[str, Any]]:
    match = re.match(r"^(.*?):(\d+):(.*)$", line)
    if not match:
        return None
    return {
        "path": match.group(1),
        "line": int(match.group(2)),
        "column": None,
        "preview": match.group(3).strip(),
    }


def summarize_tool_args(args: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "keys": sorted(args.keys()),
        "hasQuery": isinstance(args.get("query"), str) and len(args.get("query", "")) > 0,
        "queryLength": len(args["query"]) if isinstance(args.get("query"), str) else None,
        "hasPath": isinstance(args.get("path"), str) and len(args.get("path", "")) > 0,
        "hasPathPrefix": isinstance(args.get("pathPrefix"), str) and len(args.get("pathPrefix", "")) > 0,
        "glob": args.get("glob") if isinstance(args.get("glob"), str) else None,
        "limit": args.get("limit") if isinstance(args.get("limit"), (int, float)) else None,
    }


def format_read_content(lines: List[str], start_line: int) -> str:
    return "\n".join(f"{start_line + index:>4}  {line}" for index, line in enumerate(lines))


def fit_read_content_to_char_limit(lines: List[str], start_line: int) -> tuple[str, List[str], bool]:
    selected = list(lines)
    content = format_read_content(selected, start_line)
    truncated = False
    while len(selected) > 1 and len(content) > MAX_READ_OUTPUT_CHARS:
        selected = selected[:-1]
        content = format_read_content(selected, start_line)
        truncated = True
    if len(content) > MAX_READ_OUTPUT_CHARS:
        content = content[:MAX_READ_OUTPUT_CHARS]
        truncated = True
    return content, selected, truncated


def handle_find_files(pathPrefix: Optional[str] = None, glob: Optional[str] = None, limit: Any = None) -> str:
    scope = resolve_scope_path(pathPrefix)
    result_limit = clamp(limit, 1, MAX_FIND_LIMIT, DEFAULT_FIND_LIMIT)
    normalized_glob = normalize_glob(glob)
    if not scope.exists:
        return json.dumps(
            {"rootDir": str(scope.root_dir), "files": [], "total": 0, "truncated": False},
            ensure_ascii=False,
            indent=2,
        )

    engine = get_search_engine()
    if engine == "rg":
        args = ["--files", "--hidden", "--no-ignore"]
        if normalized_glob:
            args.extend(["--glob", normalized_glob])
        args.append(str(scope.scope_path))
        result = run_command_lines("rg", args, result_limit)
    else:
        args = [str(scope.scope_path), "-type", "f"]
        if normalized_glob:
            args.extend(["-name", normalized_glob])
        result = run_command_lines("find", args, result_limit)

    if result.code > 1 or (engine != "rg" and result.code > 0):
        raise RuntimeError(result.stderr.strip() or f"find_files failed with code {result.code}")

    files = []
    for line in [item.strip() for item in result.lines if item.strip()]:
        file_path = Path(line)
        if not file_path.is_absolute():
            file_path = scope.scope_path / file_path
        stats = file_path.stat()
        files.append(
            {
                "path": str(file_path),
                "type": "file" if file_path.is_file() else "other",
                "size": stats.st_size,
                "mtime": datetime.fromtimestamp(stats.st_mtime, timezone.utc).isoformat(),
            }
        )

    return json.dumps(
        {"rootDir": str(scope.root_dir), "files": files, "total": len(files), "truncated": result.truncated},
        ensure_ascii=False,
        indent=2,
    )


def handle_search_content(
    query: str,
    pathPrefix: Optional[str] = None,
    glob: Optional[str] = None,
    regex: bool = False,
    caseSensitive: bool = False,
    limit: Any = None,
) -> str:
    query_text = query.strip() if isinstance(query, str) else ""
    if not query_text:
        raise ValueError("search_content.query is required")
    scope = resolve_scope_path(pathPrefix)
    result_limit = clamp(limit, 1, MAX_SEARCH_LIMIT, DEFAULT_SEARCH_LIMIT)
    normalized_glob = normalize_glob(glob)
    if not scope.exists:
        return json.dumps(
            {"rootDir": str(scope.root_dir), "hits": [], "total": 0, "engine": "none", "truncated": False},
            ensure_ascii=False,
            indent=2,
        )

    engine = get_search_engine()
    if engine == "rg":
        args = [
            "-n",
            "--no-heading",
            "--with-filename",
            "--column",
            "--max-columns",
            "500",
            "--hidden",
            "--no-ignore",
        ]
        if normalized_glob:
            args.extend(["--glob", normalized_glob])
        if not caseSensitive:
            args.append("-i")
        if not regex:
            args.append("-F")
        args.extend(["-e", query_text, str(scope.scope_path)])
        result = run_command_lines("rg", args, result_limit)
    else:
        args = ["-R", "-n", "-I"]
        if normalized_glob:
            args.append(f"--include={normalized_glob}")
        if not caseSensitive:
            args.append("-i")
        if not regex:
            args.append("-F")
        args.extend(["--", query_text, str(scope.scope_path)])
        result = run_command_lines("grep", args, result_limit)

    if result.code and result.code > 1:
        raise RuntimeError(result.stderr.strip() or f"search_content failed with code {result.code}")

    parser = parse_rg_line if engine == "rg" else parse_grep_line
    hits = [hit for hit in (parser(line.strip()) for line in result.lines) if hit]
    return json.dumps(
        {"rootDir": str(scope.root_dir), "hits": hits, "total": len(hits), "engine": engine, "truncated": result.truncated},
        ensure_ascii=False,
        indent=2,
    )


def handle_read_file(path: str, startLine: Any = None, endLine: Any = None) -> str:
    file_path = resolve_readable_file(path)
    data = file_path.read_bytes()
    if b"\0" in data:
        raise ValueError(f"Binary files are not supported: {file_path}")
    content = data.decode("utf-8", errors="replace")
    lines = re.split(r"\r?\n", content)
    total_lines = len(lines)

    requested_start = clamp(startLine, 1, total_lines or 1, 1)
    requested_end = (
        clamp(endLine, requested_start, total_lines or requested_start, requested_start)
        if isinstance(endLine, (int, float)) and not isinstance(endLine, bool)
        else min(total_lines, requested_start + DEFAULT_READ_WINDOW - 1)
    )
    capped_end = min(requested_end, requested_start + MAX_READ_WINDOW - 1)
    selected = lines[requested_start - 1 : capped_end]
    fitted_content, fitted_lines, truncated_by_chars = fit_read_content_to_char_limit(selected, requested_start)
    returned_end_line = requested_start + len(fitted_lines) - 1
    truncated_by_lines = capped_end < requested_end
    truncated = truncated_by_lines or truncated_by_chars
    truncated_reason = "line_limit" if truncated_by_lines else "char_limit" if truncated_by_chars else None
    next_start_line = returned_end_line + 1 if truncated and returned_end_line < total_lines else None
    message = None
    if truncated:
        parts = [f"内容已被截断：请求到第 {requested_end} 行，实际返回到第 {returned_end_line} 行。"]
        if next_start_line:
            parts.append(f"如需继续读取，请用 startLine={next_start_line}。")
        message = " ".join(parts)

    return json.dumps(
        {
            "path": str(file_path),
            "startLine": requested_start,
            "endLine": returned_end_line,
            "requestedEndLine": requested_end,
            "totalLines": total_lines,
            "truncated": truncated,
            "truncatedReason": truncated_reason,
            "nextStartLine": next_start_line,
            "message": message,
            "content": fitted_content,
        },
        ensure_ascii=False,
        indent=2,
    )


def dispatch(name: str, arguments: Optional[Dict[str, Any]] = None) -> str:
    args = arguments or {}
    started_at = time.monotonic()
    LOGGER.info("tool_dispatch_started", tool=name, args=summarize_tool_args(args))
    try:
        if name == "find_files":
            result = handle_find_files(args.get("pathPrefix"), args.get("glob"), args.get("limit"))
        elif name == "search_content":
            result = handle_search_content(
                str(args.get("query", "")),
                args.get("pathPrefix") if isinstance(args.get("pathPrefix"), str) else None,
                args.get("glob") if isinstance(args.get("glob"), str) else None,
                bool(args.get("regex", False)),
                bool(args.get("caseSensitive", False)),
                args.get("limit"),
            )
        elif name == "read_file":
            result = handle_read_file(str(args.get("path", "")), args.get("startLine"), args.get("endLine"))
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
