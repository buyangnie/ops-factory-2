#!/usr/bin/env python3
from __future__ import annotations

from typing import Any, List, Optional

from mcp.server.fastmcp import FastMCP

from core import LOGGER, handle_fetch, handle_search


mcp = FastMCP(
    "knowledge-service",
    instructions="Knowledge-service MCP for agentic RAG over OpsFactory knowledge chunks.",
)


@mcp.tool(
    name="search",
    description="Search knowledge chunks. Uses the config.yaml knowledge scope when sourceIds is omitted.",
)
def search(
    query: str,
    sourceIds: Optional[List[str]] = None,
    documentIds: Optional[List[str]] = None,
    topK: Any = 8,
) -> str:
    return handle_search(query=query, sourceIds=sourceIds, documentIds=documentIds, topK=topK)


@mcp.tool(
    name="fetch",
    description="Fetch a knowledge chunk by chunkId, with optional neighbor chunks.",
)
def fetch(chunkId: str, includeNeighbors: bool = False, neighborWindow: int = 1) -> str:
    return handle_fetch(chunkId=chunkId, includeNeighbors=includeNeighbors, neighborWindow=neighborWindow)


if __name__ == "__main__":
    LOGGER.info("server_started", transport="stdio")
    mcp.run(transport="stdio")
