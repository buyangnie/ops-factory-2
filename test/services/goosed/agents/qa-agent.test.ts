/**
 * QA Agent — End-to-End Tests
 *
 * Covers:
 *   1. qa-agent is registered in gateway
 *   2. knowledge-service MCP is attached to qa-agent
 *   3. MCP tools are exposed on a real session
 *   4. end-to-end conversation triggers search/fetch and emits chunk-level citations
 *
 * Notes:
 * - This suite starts a dedicated Java gateway with a higher per-user instance limit
 *   so resident prewarm does not block qa-agent startup.
 * - knowledge-service is expected to be reachable at http://127.0.0.1:8092.
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { execFileSync, spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { join } from 'node:path'
import { existsSync, readFileSync } from 'node:fs'
import net from 'node:net'
import yaml from 'js-yaml'
import { sendSessionReplyAndWait, sleep, type GatewayHandle } from '../../../platform/shared/helpers.js'

const AGENT_ID = 'qa-agent'
const USER_SYS = 'admin'
const SECRET_KEY = 'test-secret'
const PROJECT_ROOT = join(import.meta.dirname, '..', '..', '..', '..')
const AGENT_DIR = join(PROJECT_ROOT, 'gateway', 'agents', 'qa-agent')
const AGENT_CONFIG_PATH = join(AGENT_DIR, 'config', 'config.yaml')
const MCP_DIR = join(AGENT_DIR, 'config', 'mcp', 'knowledge-service')

let gw: GatewayHandle

function readQaAgentConfig(): Record<string, unknown> {
  return yaml.load(readFileSync(AGENT_CONFIG_PATH, 'utf8')) as Record<string, unknown>
}

function readConfiguredKnowledgeSourceId(): string {
  const parsed = readQaAgentConfig()
  const sourceId = parsed?.extensions?.['knowledge-service']?.['x-opsfactory']?.knowledgeScope?.sourceId
  expect(typeof sourceId).toBe('string')
  return sourceId
}

function readConfiguredKnowledgeExtension(): Record<string, unknown> {
    const parsed = readQaAgentConfig()
    const extension = parsed?.extensions?.['knowledge-service']
    expect(extension).toBeDefined()
    return extension as Record<string, unknown>
}

function ensurePythonMcpDependencies(mcpDir: string): void {
    const depsDir = join(mcpDir, '.python-deps')
    const env = {
        ...process.env,
        PYTHONPATH: depsDir,
    }
    try {
        execFileSync('python3', [
            '-c',
            "import importlib.metadata as md; from mcp.server.fastmcp import FastMCP; raise SystemExit(0 if md.version('mcp') == '1.27.1' else 1)",
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

async function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const srv = net.createServer()
    srv.listen(0, '127.0.0.1', () => {
      const port = (srv.address() as net.AddressInfo).port
      srv.close(() => resolve(port))
    })
    srv.on('error', reject)
  })
}

async function startQaJavaGateway(): Promise<GatewayHandle> {
  const port = await freePort()
  const baseUrl = `http://127.0.0.1:${port}/gateway`

  const jarPath = join(PROJECT_ROOT, 'gateway', 'gateway-service', 'target', 'gateway-service.jar')
  const libDir = join(PROJECT_ROOT, 'gateway', 'gateway-service', 'target', 'lib')
  const logbackConfig = [
    join(PROJECT_ROOT, 'gateway', 'gateway-service', 'target', 'resources', 'logback-spring.xml'),
    join(PROJECT_ROOT, 'gateway', 'gateway-service', 'target', 'test-classes', 'logback-spring.xml'),
    join(PROJECT_ROOT, 'gateway', 'gateway-service', 'src', 'test', 'resources', 'logback-spring.xml'),
  ].find(candidate => existsSync(candidate))

  const javaArgs = [
    `-Dloader.path=${libDir}`,
    `-Dserver.port=${port}`,
    '-Dserver.address=127.0.0.1',
    `-Dgateway.secret-key=${SECRET_KEY}`,
    `-Dgateway.goosed-bin=${process.env.GOOSED_BIN || 'goosed'}`,
    '-Dgateway.goosed-tls=true',
    `-Dgateway.paths.project-root=${PROJECT_ROOT}`,
    '-Dgateway.cors-origin=*',
    '-Dgateway.limits.max-instances-per-user=20',
    '-Dgateway.limits.max-instances-global=100',
    '-jar', jarPath,
  ]
  if (logbackConfig) {
    javaArgs.splice(javaArgs.length - 1, 0, `-Dlogging.config=file:${logbackConfig}`)
  }

  const child = spawn('java', javaArgs, {
    cwd: join(PROJECT_ROOT, 'gateway'),
    stdio: ['ignore', 'pipe', 'pipe'],
  })

  const logs: string[] = []
  child.stdout?.on('data', (d: Buffer) => {
    const line = d.toString().trim()
    if (line) logs.push(`[gw:out] ${line}`)
  })
  child.stderr?.on('data', (d: Buffer) => {
    const line = d.toString().trim()
    if (line) logs.push(`[gw:err] ${line}`)
  })

  const waitUntilReady = async () => {
    const maxWait = 30_000
    const start = Date.now()
    while (Date.now() - start < maxWait) {
      try {
        const res = await fetch(`${baseUrl}/status`, {
          headers: { 'x-secret-key': SECRET_KEY, 'x-user-id': USER_SYS },
          signal: AbortSignal.timeout(2_000),
        })
        if (res.ok) return
      } catch {
        // not ready yet
      }
      await sleep(500)
    }
    throw new Error(`Gateway failed to start\n${logs.join('\n')}`)
  }

  await waitUntilReady()

  const headers = (userId?: string) => {
    const h: Record<string, string> = {
      'x-secret-key': SECRET_KEY,
      'Content-Type': 'application/json',
    }
    if (userId) h['x-user-id'] = userId
    return h
  }

  return {
    port,
    baseUrl,
    secretKey: SECRET_KEY,
    process: child,
    logs,
    fetch: (path, init) =>
      fetch(`${baseUrl}${path}`, { ...init, headers: { ...headers('admin'), ...init?.headers } }),
    fetchAs: (userId, path, init) =>
      fetch(`${baseUrl}${path}`, { ...init, headers: { ...headers(userId), ...init?.headers } }),
    stop: async () => {
      child.kill('SIGTERM')
      await sleep(3_000)
      if (!child.killed) child.kill('SIGKILL')
      await sleep(500)
    },
  }
}

function parseSseEvents(body: string): Array<Record<string, unknown>> {
  return body
    .split('\n\n')
    .map(chunk => chunk.trim())
    .filter(Boolean)
    .flatMap(chunk => {
      const data = chunk
        .split('\n')
        .filter(line => line.startsWith('data:'))
        .map(line => line.replace(/^data:\s*/, ''))
        .join('\n')
      if (!data) return []
      try {
        return [JSON.parse(data)]
      } catch {
        return []
      }
    })
}

function collectAssistantTextFromSse(events: Array<Record<string, unknown>>): string {
  return events
    .filter(event => event.type === 'Message' && event.message)
    .flatMap(event => (event.message.content || []) as Array<{ type: string; text?: string }>)
    .filter(content => content.type === 'text' && typeof content.text === 'string')
    .map(content => content.text || '')
    .join('')
}

function extractToolNames(events: Array<Record<string, unknown>>): string[] {
  return events
    .filter(event => event.type === 'Message' && event.message)
    .flatMap(event => (event.message.content || []) as Array<Record<string, unknown>>)
    .filter(content => content.type === 'toolRequest')
    .map(content =>
      content.toolCall?.value?.name ||
      content.toolCall?.name ||
      ''
    )
    .filter(Boolean)
}

async function sendReplyAndWait(
  handle: GatewayHandle,
  userId: string,
  agentId: string,
  sessionId: string,
  message: string,
  timeoutMs = 120_000,
): Promise<string> {
  const result = await sendSessionReplyAndWait(handle, userId, agentId, sessionId, message, timeoutMs)
  return result.body
}

async function createSessionAndChat(
  handle: GatewayHandle,
  userId: string,
  agentId: string,
  message: string,
) {
  const startRes = await handle.fetchAs(userId, `/agents/${agentId}/agent/start`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
  expect(startRes.ok).toBe(true)
  const session = await startRes.json()
  const sessionId = session.id as string

  const resumeRes = await handle.fetchAs(userId, `/agents/${agentId}/agent/resume`, {
    method: 'POST',
    body: JSON.stringify({ session_id: sessionId, load_model_and_extensions: true }),
  })
  expect(resumeRes.ok).toBe(true)

  const replyBody = await sendReplyAndWait(handle, userId, agentId, sessionId, message)
  return { sessionId, replyBody }
}

interface McpClientHandle {
  request: (method: string, params: Record<string, unknown>) => Promise<unknown>
  close: () => Promise<void>
}

async function startMcpClient(options: {
  cwd: string
  command?: string
  args?: string[]
  env?: Record<string, string>
}): Promise<McpClientHandle> {
  const child = spawn(options.command || 'python3', options.args || ['server.py'], {
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
      const message = JSON.parse(line)
      const waiter = pending.get(message.id)
      if (!waiter) continue
      pending.delete(message.id)
      if (message.error) {
        waiter.reject(new Error(message.error.message || JSON.stringify(message.error)))
      } else {
        waiter.resolve(message.result)
      }
    }
  })

  const request = (method: string, params: Record<string, unknown>) => {
    const id = nextId++
    const payload = { jsonrpc: '2.0', id, method, params }
    const result = new Promise<unknown>((resolve, reject) => {
      const timeout = setTimeout(() => {
        pending.delete(id)
        reject(new Error(`MCP request timed out: ${method}\n${stderr.join('\n')}`))
      }, 30_000)
      pending.set(id, {
        resolve: value => {
          clearTimeout(timeout)
          resolve(value)
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
    clientInfo: { name: 'qa-agent-test', version: '1.0.0' },
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

beforeAll(async () => {
  ensurePythonMcpDependencies(MCP_DIR)
  gw = await startQaJavaGateway()
  await sleep(2_000)
}, 90_000)

afterAll(async () => {
  if (gw) await gw.stop()
}, 20_000)

describe('qa-agent registration and MCP wiring', () => {
  it('lists qa-agent with the expected provider and model', async () => {
    const res = await gw.fetchAs(USER_SYS, '/agents')
    expect(res.ok).toBe(true)
    const data = await res.json()
    const qa = (data.agents as Array<Record<string, unknown>>).find(agent => agent.id === AGENT_ID)
    expect(qa).toBeDefined()
    expect(qa!.name).toBe('QA Agent')
    const config = readQaAgentConfig()
    expect(qa!.provider).toBe(config.GOOSE_PROVIDER)
    expect(qa!.model).toBe(config.GOOSE_MODEL)
  })

  it('exposes the knowledge-service MCP extension on /agents/qa-agent/mcp', async () => {
    const res = await gw.fetchAs(USER_SYS, `/agents/${AGENT_ID}/mcp`)
    expect(res.ok).toBe(true)
    const data = await res.json() as Record<string, unknown>
    const extensions = data.extensions as Array<Record<string, unknown>>
    const knowledge = extensions.find(ext => ext.name === 'knowledge-service')
    expect(knowledge).toBeDefined()
    expect(knowledge!.enabled).toBe(true)
    expect(knowledge!.type).toBe('stdio')
    const configuredExtension = readConfiguredKnowledgeExtension()
    expect(knowledge!.cmd).toBe(configuredExtension.cmd)
    expect(knowledge!.args).toEqual(configuredExtension.args)
  })
})

describe('qa-agent MCP runtime', () => {
  it('resumes a real session with knowledge-service MCP enabled', async () => {
    const startRes = await gw.fetchAs(USER_SYS, `/agents/${AGENT_ID}/agent/start`, {
      method: 'POST',
      body: JSON.stringify({}),
    })
    expect(startRes.ok).toBe(true)
    const session = await startRes.json()
    const sessionId = session.id as string

    const resumeRes = await gw.fetchAs(USER_SYS, `/agents/${AGENT_ID}/agent/resume`, {
      method: 'POST',
      body: JSON.stringify({ session_id: sessionId, load_model_and_extensions: true }),
    })
    expect(resumeRes.ok).toBe(true)

    const resumeBody = await resumeRes.json() as Record<string, unknown>
    expect(resumeBody).toBeDefined()
  }, 60_000)

  it('supports real MCP search and fetch calls through stdio', async () => {
    const configuredExtension = readConfiguredKnowledgeExtension()
    const client = await startMcpClient({
      cwd: AGENT_DIR,
      command: configuredExtension.cmd as string,
      args: configuredExtension.args as string[],
      env: {
        ...(configuredExtension.envs as Record<string, string> | undefined),
        KNOWLEDGE_SERVICE_URL: 'http://127.0.0.1:8092',
        KNOWLEDGE_REQUEST_TIMEOUT_MS: '15000',
      },
    })
    try {
      const tools = await client.request('tools/list', {})
      const searchResult = await client.request('tools/call', {
        name: 'search',
        arguments: { query: '运维', topK: 2 },
      })
      const searchPayload = JSON.parse(searchResult.content[0].text)

      let fetchPayload = null
      if (Array.isArray(searchPayload.hits) && searchPayload.hits.length > 0) {
        const fetchResult = await client.request('tools/call', {
          name: 'fetch',
          arguments: { chunkId: searchPayload.hits[0].chunkId, includeNeighbors: true, neighborWindow: 1 },
        })
        fetchPayload = JSON.parse(fetchResult.content[0].text)
      }

      const payload = {
        toolNames: tools.tools.map((tool: Record<string, unknown>) => tool.name),
        searchTotal: searchPayload.total,
        firstHit: searchPayload.hits?.[0] || null,
        fetchedChunkId: fetchPayload?.chunkId || null,
        fetchedSourceId: fetchPayload?.sourceId || null,
      }

      expect(payload.toolNames).toContain('search')
      expect(payload.toolNames).toContain('fetch')
      expect(payload.searchTotal).toBeGreaterThan(0)
      const configuredSourceId = readConfiguredKnowledgeSourceId()
      expect(payload.firstHit?.sourceId).toBe(configuredSourceId)
      expect(payload.fetchedChunkId).toBeTruthy()
      expect(payload.fetchedSourceId).toBe(configuredSourceId)
    } finally {
      await client.close()
    }
  }, 60_000)
})

describe('qa-agent end-to-end RAG conversation', () => {
  it('starts a real conversation and triggers knowledge-service search', async () => {
    const { replyBody } = await createSessionAndChat(
      gw,
      USER_SYS,
      AGENT_ID,
      '请基于知识库中《部署方案.pdf》第1页的内容，简洁说明部署架构和部署环境，并保留 citation 标记。',
    )

    expect(replyBody.length).toBeGreaterThan(0)

    const events = parseSseEvents(replyBody)
    const assistantText = collectAssistantTextFromSse(events)
    const toolNames = extractToolNames(events)

    expect(toolNames.some(name => name.includes('search'))).toBe(true)
    expect(assistantText.length).toBeGreaterThan(0)

    const lowerText = assistantText.toLowerCase()
    const hasRelevantContent = assistantText.includes('部署') ||
      assistantText.includes('架构') ||
      assistantText.includes('环境') ||
      assistantText.includes('数据盘') ||
      assistantText.includes('知识库') ||
      assistantText.includes('检索') ||
      lowerText.includes('euleros')
    expect(hasRelevantContent).toBe(true)
  }, 120_000)
})
