import asyncio
import json
import os
import sys

# Ensure handlers.py/logger.py are importable when invoked via a path
# such as  python3 config/mcp/local-tiny-tools/server.py
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp import types

from handlers import TOOLS, dispatch
from logger import LOG_FILE_PATH, log_error, log_info

app = Server("local-tiny-tools")


@app.list_tools()
async def handle_list_tools() -> list[types.Tool]:
    log_info("list_tools_requested", {"toolCount": len(TOOLS)})
    return [
        types.Tool(
            name=t["name"],
            description=t["description"],
            inputSchema=t["inputSchema"],
        )
        for t in TOOLS
    ]


@app.call_tool()
async def handle_call_tool(
    name: str, arguments: dict | None
) -> list[types.TextContent]:
    args = arguments or {}
    try:
        result = await dispatch(name, args)
        return [types.TextContent(type="text", text=result)]
    except Exception as exc:
        log_error("call_tool_failed", {"tool": name, "args": args, "error": str(exc)})
        return [
            types.TextContent(
                type="text",
                text=json.dumps(
                    {
                        "ok": False,
                        "tool": name,
                        "summary": "Tool execution failed.",
                        "data": None,
                        "truncated": False,
                        "warnings": [],
                        "error": {
                            "code": "TOOL_EXECUTION_FAILED",
                            "message": str(exc),
                        },
                    },
                    indent=2,
                ),
            )
        ]


async def main() -> None:
    async with stdio_server() as (read_stream, write_stream):
        log_info("server_started", {
            "transport": "stdio",
            "pid": os.getpid(),
            "logFile": LOG_FILE_PATH,
        })
        await app.run(
            read_stream,
            write_stream,
            app.create_initialization_options(),
        )


if __name__ == "__main__":
    asyncio.run(main())
