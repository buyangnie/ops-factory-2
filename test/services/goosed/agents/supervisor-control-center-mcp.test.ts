/**
 * Supervisor Agent — Control Center MCP stdio tests
 *
 * Verifies the supervisor-agent configured stdio MCP can start through its
 * config.yaml command and successfully call every exposed control-center tool.
 */
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { execFileSync, spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { mkdtempSync, rmSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { delimiter, join } from 'node:path'
import http, { type IncomingMessage, type ServerResponse } from 'node:http'
import yaml from 'js-yaml'
import { sleep } from '../../../platform/shared/helpers.js'

const PROJECT_ROOT = join(import.meta.dirname, '..', '..', '..', '..')
const AGENT_DIR = join(PROJECT_ROOT, 'gateway', 'agents', 'supervisor-agent')
const AGENT_CONFIG_PATH = join(AGENT_DIR, 'config', 'config.yaml')
const MCP_DIR = join(AGENT_DIR, 'config', 'mcp', 'control-center')
const EXPECTED_TOOL_NAMES = [
  'get_platform_status',
  'get_agents_status',
  'get_observability_data',
  'get_realtime_metrics',
  'list_services',
  'get_service_status',
  'read_service_logs',
  'read_service_config',
  'list_events',
  'start_service',
  'stop_service',
  'restart_service',
]

type JsonObject = Record<string, unknown>

interface SupervisorExtensionConfig {
  cmd: string
  args: string[]
  envs?: Record<string, string>
}

interface McpClientHandle {
  request: <T = unknown>(method: string, params: JsonObject) => Promise<T>
  close: () => Promise<void>
}

interface MockControlCenter {
  baseUrl: string
  calls: Array<{ method: string; pathname: string; searchParams: URLSearchParams; secretKey: string | null }>
  close: () => Promise<void>
}

function isJsonObject(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function toStringRecord(value: unknown): Record<string, string> | undefined {
  if (!isJsonObject(value)) return undefined
  const entries = Object.entries(value)
  if (!entries.every(([, entryValue]) => typeof entryValue === 'string')) {
    return undefined
  }
  return Object.fromEntries(entries) as Record<string, string>
}

function readSupervisorControlCenterExtension(): SupervisorExtensionConfig {
  const parsed = yaml.load(readFileSync(AGENT_CONFIG_PATH, 'utf8'))
  if (!isJsonObject(parsed) || !isJsonObject(parsed.extensions)) {
    throw new Error('supervisor config is missing extensions')
  }

  const extension = parsed.extensions.control_center
  if (!isJsonObject(extension)) {
    throw new Error('supervisor config is missing control_center extension')
  }

  expect(extension.enabled).toBe(true)
  expect(extension.type).toBe('stdio')
  if (typeof extension.cmd !== 'string' || !Array.isArray(extension.args)) {
    throw new Error('control_center extension must define cmd and args')
  }

  return {
    cmd: extension.cmd,
    args: extension.args.map(String),
    envs: toStringRecord(extension.envs),
  }
}

function ensurePythonMcpDependencies(mcpDir: string, depsDir: string): void {
  const env = {
    ...process.env,
    PYTHONPATH: depsDir,
  }
  try {
    execFileSync('python3', [
      '-c',
      [
        'import importlib.metadata as md',
        'from mcp.server.fastmcp import FastMCP',
        "raise SystemExit(0 if md.version('mcp') == '1.27.1' else 1)",
      ].join('; '),
    ], { env, stdio: 'ignore' })
    return
  } catch {
    execFileSync('python3', [
      '-m',
      'pip',
      'install',
      '--disable-pip-version-check',
      '--quiet',
      '--upgrade',
      '--target',
      depsDir,
      '-r',
      join(mcpDir, 'requirements.txt'),
    ], { stdio: 'inherit' })
  }
}

function readHeaderValue(value: string | string[] | undefined): string | null {
  if (Array.isArray(value)) return value[0] ?? null
  return value ?? null
}

function jsonResponse(res: ServerResponse, payload: unknown): void {
  const data = JSON.stringify(payload)
  res.writeHead(200, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(data),
  })
  res.end(data)
}

async function startMockControlCenter(): Promise<MockControlCenter> {
  const calls: MockControlCenter['calls'] = []
  const server = http.createServer((req: IncomingMessage, res: ServerResponse) => {
    const url = new URL(req.url || '/', 'http://127.0.0.1')
    calls.push({
      method: req.method || 'GET',
      pathname: url.pathname,
      searchParams: url.searchParams,
      secretKey: readHeaderValue(req.headers['x-secret-key']),
    })

    const method = req.method || 'GET'
    const path = url.pathname
    if (method === 'GET' && path === '/control-center/runtime/system') {
      jsonResponse(res, {
        gateway: { uptimeMs: 1000, host: '127.0.0.1', port: 3000 },
        langfuse: { configured: true },
      })
      return
    }
    if (method === 'GET' && path === '/control-center/runtime/instances') {
      jsonResponse(res, { totalInstances: 2, runningInstances: 2 })
      return
    }
    if (method === 'GET' && path === '/control-center/runtime/agents') {
      jsonResponse(res, [{ id: 'supervisor-agent', provider: 'custom_qwen3.5-35b-a3b' }])
      return
    }
    if (method === 'GET' && path === '/control-center/observability/status') {
      jsonResponse(res, { enabled: true, reachable: true })
      return
    }
    if (method === 'GET' && path === '/control-center/observability/overview') {
      jsonResponse(res, { totalTraces: 10, errorCount: 0, avgLatencyMs: 120 })
      return
    }
    if (method === 'GET' && path === '/control-center/observability/traces') {
      jsonResponse(res, [{ id: 'trace-1' }])
      return
    }
    if (method === 'GET' && path === '/control-center/observability/observations') {
      jsonResponse(res, { observations: [] })
      return
    }
    if (method === 'GET' && path === '/control-center/runtime/metrics') {
      jsonResponse(res, { aggregate: { totalRequests: 12 }, series: [] })
      return
    }
    if (method === 'GET' && path === '/control-center/services') {
      jsonResponse(res, { services: [{ id: 'gateway', health: 'UP' }] })
      return
    }
    if (method === 'GET' && path === '/control-center/services/gateway') {
      jsonResponse(res, { id: 'gateway', health: 'UP', reachable: true })
      return
    }
    if (method === 'GET' && path === '/control-center/services/gateway/logs') {
      jsonResponse(res, {
        serviceId: 'gateway',
        lines: Number(url.searchParams.get('lines')),
        content: 'INFO gateway ok',
      })
      return
    }
    if (method === 'GET' && path === '/control-center/services/gateway/config') {
      jsonResponse(res, { serviceId: 'gateway', content: 'server:\\n  port: 3000' })
      return
    }
    if (method === 'GET' && path === '/control-center/events') {
      jsonResponse(res, { events: [{ type: 'health', serviceId: 'gateway' }] })
      return
    }
    if (method === 'POST' && path === '/control-center/services/gateway/actions/start') {
      jsonResponse(res, { success: true, action: 'start' })
      return
    }
    if (method === 'POST' && path === '/control-center/services/gateway/actions/stop') {
      jsonResponse(res, { success: true, action: 'stop' })
      return
    }
    if (method === 'POST' && path === '/control-center/services/gateway/actions/restart') {
      jsonResponse(res, { success: true, action: 'restart' })
      return
    }

    res.writeHead(404, { 'content-type': 'text/plain' })
    res.end(`not found: ${method} ${path}`)
  })

  await new Promise<void>((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => resolve())
  })
  const address = server.address()
  if (!address || typeof address === 'string') {
    throw new Error('Mock Control Center did not bind to a TCP port')
  }

  return {
    baseUrl: `http://127.0.0.1:${address.port}`,
    calls,
    close: () => new Promise(resolve => server.close(() => resolve())),
  }
}

async function startMcpClient(options: {
  cwd: string
  command: string
  args: string[]
  env: Record<string, string>
}): Promise<McpClientHandle> {
  const child = spawn(options.command, options.args, {
    cwd: options.cwd,
    env: { ...process.env, ...options.env },
    stdio: ['pipe', 'pipe', 'pipe'],
  }) as ChildProcessWithoutNullStreams

  const stderr: string[] = []
  let nextId = 1
  let stdoutBuffer = ''
  const pending = new Map<number, {
    resolve: (value: unknown) => void
    reject: (error: Error) => void
  }>()

  child.stderr.on('data', chunk => {
    const text = chunk.toString().trim()
    if (text) stderr.push(text)
  })
  child.stdout.on('data', chunk => {
    stdoutBuffer += chunk.toString('utf8')
    const lines = stdoutBuffer.split('\n')
    stdoutBuffer = lines.pop() ?? ''
    for (const line of lines) {
      if (!line.trim()) continue
      const message: unknown = JSON.parse(line)
      if (!isJsonObject(message) || typeof message.id !== 'number') continue
      const waiter = pending.get(message.id)
      if (!waiter) continue
      pending.delete(message.id)
      if (message.error) {
        const errorMessage = isJsonObject(message.error) &&
          typeof message.error.message === 'string'
          ? message.error.message
          : JSON.stringify(message.error)
        waiter.reject(new Error(errorMessage))
      } else {
        waiter.resolve(message.result)
      }
    }
  })

  const request = <T = unknown>(method: string, params: JsonObject): Promise<T> => {
    const id = nextId++
    const payload = { jsonrpc: '2.0', id, method, params }
    const result = new Promise<T>((resolve, reject) => {
      const timeout = setTimeout(() => {
        pending.delete(id)
        reject(new Error(`MCP request timed out: ${method}\n${stderr.join('\n')}`))
      }, 30_000)
      pending.set(id, {
        resolve: value => {
          clearTimeout(timeout)
          resolve(value as T)
        },
        reject: error => {
          clearTimeout(timeout)
          reject(error)
        },
      })
    })
    child.stdin.write(`${JSON.stringify(payload)}\n`)
    return result
  }

  await request('initialize', {
    protocolVersion: '2025-03-26',
    capabilities: {},
    clientInfo: { name: 'supervisor-agent-mcp-test', version: '1.0.0' },
  })
  child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized', params: {} })}\n`)

  return {
    request,
    close: async () => {
      child.kill('SIGTERM')
      await sleep(300)
      if (!child.killed) child.kill('SIGKILL')
    },
  }
}

function readToolText(callResult: unknown): string {
  if (!isJsonObject(callResult) || !Array.isArray(callResult.content)) {
    throw new Error('MCP tool result is missing content')
  }
  const firstContent = callResult.content[0]
  if (!isJsonObject(firstContent)) {
    throw new Error('MCP tool result content is invalid')
  }
  const text = firstContent.text
  expect(typeof text).toBe('string')
  return text
}

function parseToolJson(callResult: unknown): JsonObject {
  const parsed: unknown = JSON.parse(readToolText(callResult))
  if (!isJsonObject(parsed)) {
    throw new Error('MCP tool result text must be a JSON object')
  }
  return parsed
}

function getObjectField(record: JsonObject, field: string): JsonObject {
  const value = record[field]
  if (!isJsonObject(value)) {
    throw new Error(`Expected object field: ${field}`)
  }
  return value
}

function getArrayField(record: JsonObject, field: string): unknown[] {
  const value = record[field]
  if (!Array.isArray(value)) {
    throw new Error(`Expected array field: ${field}`)
  }
  return value
}

let mockControlCenter: MockControlCenter
let runtimeRoot: string
let pythonDepsDir: string

beforeAll(async () => {
  runtimeRoot = mkdtempSync(join(tmpdir(), 'supervisor-control-center-mcp-'))
  pythonDepsDir = join(runtimeRoot, 'python-deps')
  ensurePythonMcpDependencies(MCP_DIR, pythonDepsDir)
  mockControlCenter = await startMockControlCenter()
}, 60_000)

afterAll(async () => {
  if (mockControlCenter) await mockControlCenter.close()
  if (runtimeRoot) rmSync(runtimeRoot, { recursive: true, force: true })
}, 15_000)

describe('supervisor-agent control-center MCP stdio runtime', () => {
  it('starts from supervisor-agent config and lists all control-center tools', async () => {
    const extension = readSupervisorControlCenterExtension()
    const client = await startMcpClient({
      cwd: AGENT_DIR,
      command: extension.cmd,
      args: extension.args,
      env: {
        ...(extension.envs || {}),
        PYTHONPATH: [pythonDepsDir, MCP_DIR].join(delimiter),
        CONTROL_CENTER_URL: mockControlCenter.baseUrl,
        CONTROL_CENTER_SECRET_KEY: 'unit-test-secret',
        GOOSE_PATH_ROOT: runtimeRoot,
      },
    })
    try {
      const toolsResult = await client.request<JsonObject>('tools/list', {})
      const tools = getArrayField(toolsResult, 'tools')
      const toolNames = tools.map(tool => {
        if (!isJsonObject(tool) || typeof tool.name !== 'string') {
          throw new Error('MCP tools/list returned a malformed tool')
        }
        return tool.name
      })

      expect(toolNames).toEqual(EXPECTED_TOOL_NAMES)
    } finally {
      await client.close()
    }
  })

  it('successfully calls every control-center MCP tool through stdio', async () => {
    const extension = readSupervisorControlCenterExtension()
    const client = await startMcpClient({
      cwd: AGENT_DIR,
      command: extension.cmd,
      args: extension.args,
      env: {
        ...(extension.envs || {}),
        PYTHONPATH: [pythonDepsDir, MCP_DIR].join(delimiter),
        CONTROL_CENTER_URL: mockControlCenter.baseUrl,
        CONTROL_CENTER_SECRET_KEY: 'unit-test-secret',
        GOOSE_PATH_ROOT: runtimeRoot,
      },
    })

    try {
      const calls: Array<{ name: string; arguments: JsonObject }> = [
        { name: 'get_platform_status', arguments: {} },
        { name: 'get_agents_status', arguments: {} },
        { name: 'get_observability_data', arguments: { hours: 24 } },
        { name: 'get_realtime_metrics', arguments: {} },
        { name: 'list_services', arguments: {} },
        { name: 'get_service_status', arguments: { serviceId: 'gateway' } },
        { name: 'read_service_logs', arguments: { serviceId: 'gateway', lines: 50 } },
        { name: 'read_service_config', arguments: { serviceId: 'gateway' } },
        { name: 'list_events', arguments: {} },
        { name: 'start_service', arguments: { serviceId: 'gateway' } },
        { name: 'stop_service', arguments: { serviceId: 'gateway' } },
        { name: 'restart_service', arguments: { serviceId: 'gateway' } },
      ]

      const results: Record<string, JsonObject> = {}
      for (const call of calls) {
        const result = await client.request('tools/call', call)
        results[call.name] = parseToolJson(result)
      }

      const platformSystem = getObjectField(results.get_platform_status, 'system')
      expect(getObjectField(platformSystem, 'gateway').port).toBe(3000)

      const agents = getArrayField(results.get_agents_status, 'agents')
      expect(isJsonObject(agents[0]) ? agents[0].id : undefined)
        .toBe('supervisor-agent')

      expect(getObjectField(results.get_observability_data, 'overview').totalTraces)
        .toBe(10)
      expect(getObjectField(results.get_realtime_metrics, 'aggregate').totalRequests)
        .toBe(12)

      const services = getArrayField(results.list_services, 'services')
      expect(isJsonObject(services[0]) ? services[0].id : undefined)
        .toBe('gateway')

      expect(results.get_service_status.health).toBe('UP')
      expect(results.read_service_logs.lines).toBe(50)
      expect(results.read_service_config.content).toContain('server:')

      const events = getArrayField(results.list_events, 'events')
      expect(isJsonObject(events[0]) ? events[0].serviceId : undefined)
        .toBe('gateway')

      expect(results.start_service.action).toBe('start')
      expect(results.stop_service.action).toBe('stop')
      expect(results.restart_service.action).toBe('restart')

      const secretKeys = mockControlCenter.calls.map(call => call.secretKey)
      expect(secretKeys.every(key => key === 'unit-test-secret')).toBe(true)
      expect(mockControlCenter.calls.some(call =>
        call.pathname === '/control-center/services/gateway/logs' &&
        call.searchParams.get('lines') === '50'
      )).toBe(true)
    } finally {
      await client.close()
    }
  }, 60_000)
})
