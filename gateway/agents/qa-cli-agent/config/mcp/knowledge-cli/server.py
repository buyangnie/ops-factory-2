#!/usr/bin/env python3
from __future__ import annotations

from typing import Any, Optional

from mcp.server.fastmcp import FastMCP

from core import LOGGER, handle_find_files, handle_read_file, handle_search_content


mcp = FastMCP(
    "Knowledge-Cli",
    instructions="File-system QA tools for a configured root directory.",
)


@mcp.tool(name="find_files", description="Find files under the configured root directory.")
def find_files(pathPrefix: Optional[str] = None, glob: Optional[str] = None, limit: Any = None) -> str:
    return handle_find_files(pathPrefix=pathPrefix, glob=glob, limit=limit)


@mcp.tool(name="search_content", description="Search text content under the configured root directory.")
def search_content(
    query: str,
    pathPrefix: Optional[str] = None,
    glob: Optional[str] = None,
    regex: bool = False,
    caseSensitive: bool = False,
    limit: Any = None,
) -> str:
    return handle_search_content(
        query=query,
        pathPrefix=pathPrefix,
        glob=glob,
        regex=regex,
        caseSensitive=caseSensitive,
        limit=limit,
    )


@mcp.tool(
    name="read_file",
    description=(
        "Read a file or a specific line range under the configured root directory. "
        "Results are capped to keep context small."
    ),
)
def read_file(path: str, startLine: Any = None, endLine: Any = None) -> str:
    return handle_read_file(path=path, startLine=startLine, endLine=endLine)


if __name__ == "__main__":
    LOGGER.info("server_started", transport="stdio")
    mcp.run(transport="stdio")
