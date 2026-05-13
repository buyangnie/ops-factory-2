import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { existsSync, mkdtempSync, realpathSync, rmSync, writeFileSync, mkdirSync, readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import os from 'node:os'
import { join } from 'node:path'
import { sendSessionReplyAndWait, sleep, startJavaGateway, type GatewayHandle } from '../../../platform/shared/helpers.js'

const AGENT_ID = 'qa-cli-agent'
const USER_ID = 'admin'
const PROJECT_ROOT = join(import.meta.dirname, '..', '..', '..', '..')
const SECRETS_PATH = join(PROJECT_ROOT, 'gateway', 'agents', 'qa-cli-agent', 'config', 'secrets.yaml')
const MCP_DIR = join(PROJECT_ROOT, 'gateway', 'agents', 'qa-cli-agent', 'config', 'mcp', 'knowledge-cli')

let gw: GatewayHandle

function parseSseEvents(body: string): Array<Record<string, any>> {
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

function collectAssistantTextFromSse(events: Array<Record<string, any>>): string {
  return events
    .filter(event => event.type === 'Message' && event.message)
    .flatMap(event => (event.message.content || []) as Array<{ type: string; text?: string }>)
    .filter(content => content.type === 'text' && typeof content.text === 'string')
    .map(content => content.text || '')
    .join('')
}

function extractToolNames(events: Array<Record<string, any>>): string[] {
  return events
    .filter(event => event.type === 'Message' && event.message)
    .flatMap(event => (event.message.content || []) as Array<Record<string, any>>)
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
  const result = await sendSessionReplyAndWait(handle, userId, agentId, sessionId, message, 120_000)
  expect(result.body.length).toBeGreaterThan(0)
  return { sessionId, replyBody: result.body }
}

function hasQaCliSecrets(): boolean {
  if (!existsSync(SECRETS_PATH)) return false
  const content = readFileSync(SECRETS_PATH, 'utf8')
  return /CUSTOM_OPSAGENTLLM_API_KEY:\s*\S+/.test(content)
}

beforeAll(async () => {
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
    const qaCli = (data.agents as Array<Record<string, any>>).find(agent => agent.id === AGENT_ID)

    expect(qaCli).toBeDefined()
    expect(qaCli!.name).toBe('QA CLI Agent')
  })

  it('loads Knowledge-Cli tools on a real session', async () => {
    const startRes = await gw.fetchAs(USER_ID, `/agents/${AGENT_ID}/agent/start`, {
      method: 'POST',
      body: JSON.stringify({}),
    })
    expect(startRes.ok).toBe(true)
    const session = await startRes.json()
    const sessionId = session.id as string

    const res = await gw.fetchAs(USER_ID, `/agents/${AGENT_ID}/agent/tools?session_id=${sessionId}`)
    expect(res.ok).toBe(true)
    const tools = await res.json() as Array<Record<string, any>>
    const names = tools.map(tool => tool.name as string)

    expect(names.some(name => name.includes('search_content'))).toBe(true)
    expect(names.some(name => name.includes('read_file'))).toBe(true)
    expect(names.some(name => name.includes('find_files'))).toBe(true)
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

      execFileSync('npm', ['run', 'build'], { cwd: MCP_DIR, stdio: 'inherit' })
      const script = `
        import { Client } from '@modelcontextprotocol/sdk/client/index.js'
        import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js'

        const transport = new StdioClientTransport({
          command: 'node',
          args: ['dist/index.js'],
          cwd: process.cwd(),
          env: {
            ...process.env,
            QA_CLI_ROOT_DIR: ${JSON.stringify(rootDir)},
          },
        })

        const client = new Client({ name: 'qa-cli-agent-test', version: '1.0.0' }, { capabilities: {} })
        await client.connect(transport)

        const tools = await client.listTools()
        const findResult = await client.callTool({ name: 'find_files', arguments: { glob: '*.md' } })
        const findPayload = JSON.parse(findResult.content[0].text)
        const searchResult = await client.callTool({ name: 'search_content', arguments: { query: '告警管理', glob: '*.md' } })
        const searchPayload = JSON.parse(searchResult.content[0].text)
        const readResult = await client.callTool({ name: 'read_file', arguments: { path: searchPayload.hits[0].path, startLine: 1, endLine: 3 } })
        const readPayload = JSON.parse(readResult.content[0].text)

        console.log(JSON.stringify({
          toolNames: tools.tools.map(tool => tool.name),
          findTotal: findPayload.total,
          searchTotal: searchPayload.total,
          firstHitPath: searchPayload.hits[0]?.path || null,
          readContent: readPayload.content,
        }))

        await client.close()
      `

      const output = execFileSync('node', ['--input-type=module', '-e', script], {
        cwd: MCP_DIR,
        encoding: 'utf-8',
      })
      const lines = output.trim().split('\n').filter(Boolean)
      const payload = JSON.parse(lines[lines.length - 1]) as Record<string, any>

      expect(payload.toolNames).toContain('find_files')
      expect(payload.toolNames).toContain('search_content')
      expect(payload.toolNames).toContain('read_file')
      expect(payload.findTotal).toBe(1)
      expect(payload.searchTotal).toBe(2)
      expect(realpathSync(payload.firstHitPath)).toBe(realContentPath)
      expect(payload.readContent).toContain('告警管理')
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
