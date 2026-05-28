#!/usr/bin/env python3
from __future__ import annotations

from mcp.server.fastmcp import FastMCP

from core import (
    LOGGER,
    handle_get_agents_status,
    handle_get_observability_data,
    handle_get_platform_status,
    handle_get_realtime_metrics,
    handle_get_service_status,
    handle_list_events,
    handle_list_services,
    handle_read_service_config,
    handle_read_service_logs,
    handle_restart_service,
    handle_start_service,
    handle_stop_service,
)


mcp = FastMCP(
    "control_center",
    instructions=(
        "OpsFactory control-center MCP for platform runtime, observability, "
        "service inspection, and service actions."
    ),
)


@mcp.tool(
    name="get_platform_status",
    description=(
        "Get platform runtime status: gateway uptime, host/port, running "
        "instances, Langfuse status, and idle timeout configuration."
    ),
)
def get_platform_status() -> str:
    return handle_get_platform_status()


@mcp.tool(
    name="get_agents_status",
    description="Get configured agents (provider, model, skills) and their running instance counts grouped by agent.",
)
def get_agents_status() -> str:
    return handle_get_agents_status()


@mcp.tool(
    name="get_observability_data",
    description=(
        "Get observability metrics from Control Center: KPIs, traces, "
        "latency, errors, and observation breakdown. Accepts optional hours."
    ),
)
def get_observability_data(hours: float = 24) -> str:
    return handle_get_observability_data(hours)


@mcp.tool(
    name="get_realtime_metrics",
    description="Get real-time gateway performance metrics from Control Center runtime metrics.",
)
def get_realtime_metrics() -> str:
    return handle_get_realtime_metrics()


@mcp.tool(
    name="list_services",
    description="List all managed services from Control Center with their health and reachability status.",
)
def list_services() -> str:
    return handle_list_services()


@mcp.tool(
    name="get_service_status",
    description="Get detailed status for one managed service by serviceId.",
)
def get_service_status(serviceId: str) -> str:
    return handle_get_service_status(serviceId)


@mcp.tool(
    name="read_service_logs",
    description="Read the latest lines from a managed service log file.",
)
def read_service_logs(serviceId: str, lines: int = 200) -> str:
    return handle_read_service_logs(serviceId, lines)


@mcp.tool(
    name="read_service_config",
    description="Read the current config file content for a managed service.",
)
def read_service_config(serviceId: str) -> str:
    return handle_read_service_config(serviceId)


@mcp.tool(
    name="list_events",
    description="List recent Control Center service events such as actions and health transitions.",
)
def list_events() -> str:
    return handle_list_events()


@mcp.tool(
    name="start_service",
    description="Start a managed service through Control Center.",
)
def start_service(serviceId: str) -> str:
    return handle_start_service(serviceId)


@mcp.tool(
    name="stop_service",
    description="Stop a managed service through Control Center.",
)
def stop_service(serviceId: str) -> str:
    return handle_stop_service(serviceId)


@mcp.tool(
    name="restart_service",
    description="Restart a managed service through Control Center.",
)
def restart_service(serviceId: str) -> str:
    return handle_restart_service(serviceId)


if __name__ == "__main__":
    LOGGER.info("server_started", transport="stdio")
    mcp.run(transport="stdio")
