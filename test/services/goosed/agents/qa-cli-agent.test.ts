import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { existsSync, mkdtempSync, realpathSync, rmSync, writeFileSync, mkdirSync, readFileSync } from 'node:fs'
import { execFileSync, spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import os from 'node:os'
import { join } from 'node:path'
import yaml from 'js-yaml'
import { sendSessionReplyAndWait, sleep, startJavaGateway, type GatewayHandle } from '../../../platform/shared/helpers.js'

const AGENT_ID = 'qa-cli-agent'
const USER_ID = 'admin'
const PROJECT_ROOT = join(import.meta.dirname, '..', '..', '..', '..')
const AGENT_DIR = join(PROJECT_ROOT, 'gateway', 'agents', 'qa-cli-agent')
const AGENT_CONFIG_PATH = join(AGENT_DIR, 'config', 'config.yaml')
const SECRETS_PATH = join(AGENT_DIR, 'config', 'secrets.yaml')
const MCP_DIR = join(AGENT_DIR, 'config', 'mcp', 'knowledge-cli')

let gw: GatewayHandle

function readQaCliAgentConfig(): Record<string, unknown> {
  return yaml.load(readFileSync(AGENT_CONFIG_PATH, 'utf8')) as Record<string, unknown>
}

function readConfiguredKnowledgeCliExtension(): Record<string, unknown> {
  const parsed = readQaCliAgentConfig()
  const extension = parsed?.extensions?.['knowledge-cli']
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
  const result = await sendSessionReplyAndWait(handle, userId, agentId, sessionId, message, 120_000)
  expect(result.body.length).toBeGreaterThan(0)
  return { sessionId, replyBody: result.body }
}

function hasQaCliSecrets(): boolean {
  if (!existsSync(SECRETS_PATH)) return false
  const content = readFileSync(SECRETS_PATH, 'utf8')
  return /CUSTOM_OPSAGENTLLM_API_KEY:\s*\S+/.test(content)
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
    clientInfo: { name: 'qa-cli-agent-test', version: '1.0.0' },
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
  gw = await startJavaGateway()
  await sleep(2_000)
}, 60_000)

afterAll(async () => {
  if (gw) await gw.stop()
}, 20_000)

describe('qa-cli-agent registration and tool wiring', () => {
  it('lists qa-cli-agent in the gateway registry', async () => {
    const res = await gw.fetchAs(USER_ID, '/agents')
    expect(res.ok).toBe(true)
    const data = await res.json()
    const qaCli = (data.agents as Array<Record<string, unknown>>).find(agent => agent.id === AGENT_ID)

    expect(qaCli).toBeDefined()
    expect(qaCli!.name).toBe('QA CLI Agent')
  })

  it('resumes a real session with Knowledge-Cli MCP enabled', async () => {
    const startRes = await gw.fetchAs(USER_ID, `/agents/${AGENT_ID}/agent/start`, {
      method: 'POST',
      body: JSON.stringify({}),
    })
    expect(startRes.ok).toBe(true)
    const session = await startRes.json()
    const sessionId = session.id as string

    const resumeRes = await gw.fetchAs(USER_ID, `/agents/${AGENT_ID}/agent/resume`, {
      method: 'POST',
      body: JSON.stringify({ session_id: sessionId, load_model_and_extensions: true }),
    })
    expect(resumeRes.ok).toBe(true)

    const resumeBody = await resumeRes.json() as Record<string, unknown>
    expect(resumeBody).toBeDefined()
  }, 60_000)

  it('serves find, search, and read tools through real MCP stdio', async () => {
    const rootDir = realpathSync(mkdtempSync(join(os.tmpdir(), 'qa-cli-mcp-')))
    try {
      const docsDir = join(rootDir, 'artifacts', 'doc_1')
      mkdirSync(docsDir, { recursive: true })
      const contentPath = join(docsDir, 'content.md')
      writeFileSync(
        contentPath,
        [
          '# 告警管理',
          '',
          '告警管理流程要求值班人员先搜索知识产物，再读取原文证据。',
          '恢复确认完成后，需要记录处理结论。',
        ].join('\n'),
        'utf8',
      )

      const realContentPath = realpathSync(contentPath)

      const configuredExtension = readConfiguredKnowledgeCliExtension()
      const client = await startMcpClient({
        cwd: AGENT_DIR,
        command: configuredExtension.cmd as string,
        args: configuredExtension.args as string[],
        env: {
          ...(configuredExtension.envs as Record<string, string> | undefined),
          QA_CLI_ROOT_DIR: rootDir,
        },
      })
      try {
        const tools = await client.request('tools/list', {})
        const findResult = await client.request('tools/call', { name: 'find_files', arguments: { glob: '*.md' } })
        const findPayload = JSON.parse(findResult.content[0].text)
        const searchResult = await client.request('tools/call', {
          name: 'search_content',
          arguments: { query: '告警管理', glob: '*.md' },
        })
        const searchPayload = JSON.parse(searchResult.content[0].text)
        const readResult = await client.request('tools/call', {
          name: 'read_file',
          arguments: { path: searchPayload.hits[0].path, startLine: 1, endLine: 3 },
        })
        const readPayload = JSON.parse(readResult.content[0].text)

        const payload = {
          toolNames: tools.tools.map((tool: Record<string, unknown>) => tool.name),
          findTotal: findPayload.total,
          searchTotal: searchPayload.total,
          firstHitPath: searchPayload.hits[0]?.path || null,
          readContent: readPayload.content,
        }

        expect(payload.toolNames).toContain('find_files')
        expect(payload.toolNames).toContain('search_content')
        expect(payload.toolNames).toContain('read_file')
        expect(payload.findTotal).toBe(1)
        expect(payload.searchTotal).toBe(2)
        expect(realpathSync(payload.firstHitPath)).toBe(realContentPath)
        expect(payload.readContent).toContain('告警管理')
      } finally {
        await client.close()
      }
    } finally {
      rmSync(rootDir, { recursive: true, force: true })
    }
  }, 60_000)
})

describe('qa-cli-agent end-to-end conversation', () => {
  it.skipIf(!hasQaCliSecrets())('answers from configured files and emits filecite markers', async () => {
    const { replyBody } = await createSessionAndChat(
      gw,
      USER_ID,
      AGENT_ID,
      '请使用 knowledge-cli__search_content 搜索“告警管理”，再用 knowledge-cli__read_file 读取命中的文件，只回答文件和证据。',
    )

    expect(replyBody.length).toBeGreaterThan(0)

    const events = parseSseEvents(replyBody)
    const assistantText = collectAssistantTextFromSse(events)
    const toolNames = extractToolNames(events)

    expect(toolNames.some(name => name.includes('search_content'))).toBe(true)
    expect(assistantText).toContain('告警管理')
    expect(assistantText).toContain('content.md')
    expect(assistantText).toContain('[[filecite:')
  }, 120_000)
})
