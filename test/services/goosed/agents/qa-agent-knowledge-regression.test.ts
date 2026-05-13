/**
 * QA Agent knowledge regression
 *
 * Runs independent QA Agent conversations against the real gateway and validates:
 * - every round triggers knowledge-service search
 * - tool calls complete successfully
 * - search returns evidence candidates for each round
 * - answers are generated
 * - the agent does not time out while following the knowledge-service flow
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { spawn } from 'node:child_process'
import { join } from 'node:path'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import net from 'node:net'

import { sendSessionReplyAndWait, type GatewayHandle, sleep } from '../../../platform/shared/helpers.js'
import { qaKnowledgeRegressionCases } from './qa-agent-knowledge-regression.cases.js'

const AGENT_ID = 'qa-agent'
const USER_SYS = 'admin'
const SECRET_KEY = 'test-secret'
const PROJECT_ROOT = join(import.meta.dirname, '..', '..', '..', '..')
const MCP_DIR = join(PROJECT_ROOT, 'gateway', 'agents', 'qa-agent', 'config', 'mcp', 'knowledge-service')
const REPORT_DIR = join(PROJECT_ROOT, 'test', 'report')
const MIN_TOOL_SUCCESS_RATE = Number(process.env.QA_KNOWLEDGE_MIN_TOOL_SUCCESS_RATE || '1')
const ROUND_TIMEOUT_MS = Number(process.env.QA_KNOWLEDGE_ROUND_TIMEOUT_MS || '240000')
const REQUESTED_ROUND_LIMIT = Number(process.env.QA_KNOWLEDGE_ROUND_LIMIT || String(qaKnowledgeRegressionCases.length))
const ROUND_LIMIT = Math.min(REQUESTED_ROUND_LIMIT, qaKnowledgeRegressionCases.length)

let gw: GatewayHandle

interface ToolRequestRecord {
  id: string
  name: string
  args: Record<string, unknown>
}

interface ToolResponseRecord {
  id: string
  name: string
  status: string
  data: Record<string, any> | null
}

interface RoundReport {
  id: string
  prompt: string
  generated: boolean
  timedOut: boolean
  searchCalls: number
  searchSuccesses: number
  fetchCalls: number
  fetchSuccesses: number
  citationCount: number
  topHits: string[]
  answerPreview: string
}

interface ReportPaths {
  markdownPath: string
  jsonPath: string
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
  const log4jConfig = [
    join(PROJECT_ROOT, 'gateway', 'gateway-service', 'target', 'resources', 'log4j2.xml'),
    join(PROJECT_ROOT, 'gateway', 'gateway-service', 'target', 'test-classes', 'log4j2.xml'),
    join(PROJECT_ROOT, 'gateway', 'gateway-service', 'src', 'test', 'resources', 'log4j2.xml'),
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
  if (log4jConfig) {
    javaArgs.splice(javaArgs.length - 1, 0, `-Dlogging.config=file:${log4jConfig}`)
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
        // ignore
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

function unwrapToolResult(value: unknown): Record<string, any> | null {
  if (!value) return null
  if (typeof value === 'string') {
    try {
      return JSON.parse(value)
    } catch {
      return null
    }
  }

  const obj = value as Record<string, unknown>
  if (Array.isArray(obj.content)) {
    for (const item of obj.content) {
      const record = item as Record<string, unknown>
      if (record.type === 'text' && typeof record.text === 'string') {
        try {
          return JSON.parse(record.text)
        } catch {
          return null
        }
      }
    }
  }

  return obj as Record<string, any>
}

function collectAssistantText(events: Array<Record<string, any>>): string {
  return events
    .filter(event => event.type === 'Message' && event.message)
    .flatMap(event => (event.message.content || []) as Array<{ type: string; text?: string }>)
    .filter(content => content.type === 'text' && typeof content.text === 'string')
    .map(content => content.text || '')
    .join('')
}

function extractToolRecords(events: Array<Record<string, any>>) {
  const requests = new Map<string, ToolRequestRecord>()
  const responses: ToolResponseRecord[] = []

  for (const event of events) {
    if (event.type !== 'Message' || !event.message?.content) continue
    for (const content of event.message.content as Array<Record<string, any>>) {
      if (content.type === 'toolRequest' && content.id) {
        const name = content.toolCall?.value?.name || content.toolCall?.name || ''
        requests.set(content.id, {
          id: content.id,
          name,
          args: (content.toolCall?.value?.arguments || content.toolCall?.arguments || {}) as Record<string, unknown>,
        })
      }
      if (content.type === 'toolResponse' && content.id) {
        const req = requests.get(content.id)
        responses.push({
          id: content.id,
          name: req?.name || '',
          status: content.toolResult?.status || 'unknown',
          data: unwrapToolResult(content.toolResult?.value),
        })
      }
    }
  }

  return {
    requests: Array.from(requests.values()),
    responses,
  }
}

function stripCitations(text: string): string {
  return text.replace(/\{\{cite:[^}]+\}\}/g, '')
}

function countCitations(text: string): number {
  return (text.match(/\{\{cite:[^}]+\}\}/g) || []).length
}

function assertCondition(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message)
}

function buildRoundPrompt(prompt: string, mustUseFetch: boolean): string {
  const instructions = [
    '请先调用 search 检索知识库，不要只依赖记忆。',
    mustUseFetch ? '命中后请 fetch 1 个最相关 chunk 的完整内容。' : '',
    '完成必要的工具调用后直接用一句话回答，不要反复搜索，并保留 citation 标记。',
  ].filter(Boolean)

  return `${prompt}\n\n${instructions.join(' ')}`
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

function formatTimestamp(date = new Date()): string {
  return `${date.getFullYear()}${pad2(date.getMonth() + 1)}${pad2(date.getDate())}_${pad2(date.getHours())}${pad2(date.getMinutes())}${pad2(date.getSeconds())}`
}

function formatHumanTime(date = new Date()): string {
  const offsetMinutes = -date.getTimezoneOffset()
  const sign = offsetMinutes >= 0 ? '+' : '-'
  const abs = Math.abs(offsetMinutes)
  const hh = pad2(Math.floor(abs / 60))
  const mm = pad2(abs % 60)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())} ${sign}${hh}:${mm}`
}

function createReportPaths(stamp = formatTimestamp()): ReportPaths {
  mkdirSync(REPORT_DIR, { recursive: true })
  return {
    markdownPath: join(REPORT_DIR, `qa-agent-knowledge-regression-report_${stamp}.md`),
    jsonPath: join(REPORT_DIR, `qa-agent-knowledge-regression-report_${stamp}.json`),
  }
}

function writeRegressionReport(paths: ReportPaths, payload: {
  startedAt: string
  finishedAt: string
  status: 'running' | 'passed' | 'failed'
  roundsPlanned: number
  roundsCompleted: number
  roundLimit: number
  toolSuccessRate: number
  searchSuccessRate: number
  fetchSuccessRate: number
  citationRate: number
  reports: RoundReport[]
  failureMessage?: string
}) {
  const passRate = payload.status === 'passed' && payload.roundsCompleted === payload.roundsPlanned
    ? 1
    : payload.roundsPlanned > 0
      ? payload.roundsCompleted / payload.roundsPlanned
      : 0
  const markdown = [
    '# QA Agent Knowledge Regression Report',
    '',
    `- 生成时间：${payload.finishedAt}`,
    `- 状态：${payload.status}`,
    `- 回归通过率：${(passRate * 100).toFixed(2)}%`,
    `- 计划轮次：${payload.roundsPlanned}`,
    `- 已完成轮次：${payload.roundsCompleted}`,
    `- 执行轮次上限：${payload.roundLimit}`,
    `- Tool 成功率：${(payload.toolSuccessRate * 100).toFixed(2)}%`,
    `- Search 成功率：${(payload.searchSuccessRate * 100).toFixed(2)}%`,
    `- Fetch 成功率：${(payload.fetchSuccessRate * 100).toFixed(2)}%`,
    `- Citation 覆盖率（观察项）：${(payload.citationRate * 100).toFixed(2)}%`,
    `- Markdown 报告路径：\`${paths.markdownPath}\``,
    `- JSON 报告路径：\`${paths.jsonPath}\``,
    payload.failureMessage ? `- 失败原因：${payload.failureMessage}` : '',
    '',
    '## 逐轮结果',
    '',
    '| Round | Generated | Timed Out | Search | Fetch | Citations | Top Hits | Answer Preview |',
    '| --- | --- | --- | --- | --- | --- | --- | --- |',
    ...payload.reports.map(report => {
      const topHits = report.topHits.join(' / ').replace(/\s+/g, ' ').replace(/\|/g, '\\|')
      const answer = report.answerPreview.replace(/\n/g, ' ').replace(/\|/g, '\\|')
      return `| ${report.id} | ${report.generated ? 'yes' : 'no'} | ${report.timedOut ? 'yes' : 'no'} | ${report.searchSuccesses}/${report.searchCalls} | ${report.fetchSuccesses}/${report.fetchCalls} | ${report.citationCount} | ${topHits} | ${answer} |`
    }),
    '',
  ].filter(Boolean).join('\n')

  writeFileSync(paths.markdownPath, markdown, 'utf-8')
  writeFileSync(paths.jsonPath, JSON.stringify({ ...payload, passRate }, null, 2), 'utf-8')
}

async function createSession(handle: GatewayHandle, userId: string, agentId: string): Promise<string> {
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
  return sessionId
}

async function sendReplyAndWait(
  handle: GatewayHandle,
  userId: string,
  agentId: string,
  sessionId: string,
  message: string,
  timeoutMs = ROUND_TIMEOUT_MS,
): Promise<{ body: string; timedOut: boolean }> {
  return sendSessionReplyAndWait(handle, userId, agentId, sessionId, message, timeoutMs)
}

beforeAll(async () => {
  if (!existsSync(join(MCP_DIR, 'node_modules'))) {
    execFileSync('npm', ['install'], { cwd: MCP_DIR, stdio: 'inherit' })
  }
  execFileSync('npm', ['run', 'build'], { cwd: MCP_DIR, stdio: 'inherit' })
  gw = await startQaJavaGateway()
  await sleep(2_000)
}, 90_000)

afterAll(async () => {
  if (gw) await gw.stop()
}, 20_000)

describe('qa-agent knowledge regression', () => {
  it('runs the knowledge regression and records tool success metrics', async () => {
    const cases = qaKnowledgeRegressionCases.slice(0, ROUND_LIMIT)
    const reports: RoundReport[] = []
    const startedAt = formatHumanTime()
    const reportPaths = createReportPaths()

    let totalToolResponses = 0
    let successfulToolResponses = 0
    let totalSearchResponses = 0
    let successfulSearchResponses = 0
    let totalFetchResponses = 0
    let successfulFetchResponses = 0
    let roundsWithCitations = 0

    writeRegressionReport(reportPaths, {
      startedAt,
      finishedAt: startedAt,
      status: 'running',
      roundsPlanned: cases.length,
      roundsCompleted: reports.length,
      roundLimit: ROUND_LIMIT,
      toolSuccessRate: 0,
      searchSuccessRate: 0,
      fetchSuccessRate: 0,
      citationRate: 0,
      reports,
    })
    try {
      for (const [index, testCase] of cases.entries()) {
        const sessionId = await createSession(gw, USER_SYS, AGENT_ID)
        const roundPrompt = buildRoundPrompt(testCase.prompt, testCase.mustUseFetch)
        console.info(`[qa-regression] round ${index + 1}/${cases.length} start ${testCase.id}`)
        const { body: replyBody, timedOut } = await sendReplyAndWait(gw, USER_SYS, AGENT_ID, sessionId, roundPrompt)
        if (timedOut) {
          console.warn(`[qa-regression] round ${index + 1}/${cases.length} timeout ${testCase.id} after ${ROUND_TIMEOUT_MS}ms`)
        }
        assertCondition(!timedOut, `[${testCase.id}] agent response timed out`)
        assertCondition(replyBody.length > 0, `[${testCase.id}] empty SSE response`)

        const events = parseSseEvents(replyBody)
        const assistantText = collectAssistantText(events)
        const answerText = stripCitations(assistantText)
        const citationCount = countCitations(assistantText)
        const { requests, responses } = extractToolRecords(events)

        const searchRequests = requests.filter(req => req.name.includes('search'))
        const searchResponses = responses.filter(res => res.name.includes('search'))
        const fetchResponses = responses.filter(res => res.name.includes('fetch') && !res.name.includes('search'))
        const successfulSearch = searchResponses.filter(res => res.status === 'success')
        const successfulFetch = fetchResponses.filter(res => res.status === 'success')

        totalToolResponses += responses.length
        successfulToolResponses += responses.filter(res => res.status === 'success').length
        totalSearchResponses += searchResponses.length
        successfulSearchResponses += successfulSearch.length
        totalFetchResponses += fetchResponses.length
        successfulFetchResponses += successfulFetch.length

        assertCondition(searchRequests.length > 0, `[${testCase.id}] expected at least one search tool request`)
        assertCondition(searchResponses.length > 0, `[${testCase.id}] expected at least one search tool response`)
        assertCondition(searchResponses.every(res => res.status === 'success'), `[${testCase.id}] search tool returned non-success status`)

        assertCondition(fetchResponses.every(res => res.status === 'success'), `[${testCase.id}] fetch tool returned non-success status`)

        const firstSearchHits = Array.isArray(successfulSearch[0]?.data?.hits) ? successfulSearch[0]!.data!.hits : []
        const topHits = firstSearchHits
          .slice(0, 3)
          .map((hit: Record<string, any>) =>
            [
              hit.title,
              hit.documentName,
              hit.snippet,
              hit.chunkId,
            ]
              .filter(Boolean)
              .map(String)
              .join(' ')
          )

        assertCondition(assistantText.length > 0, `[${testCase.id}] assistant returned empty text`)
        assertCondition(topHits.length > 0, `[${testCase.id}] expected at least one search hit`)
        if (citationCount > 0) roundsWithCitations++

        reports.push({
          id: testCase.id,
          prompt: roundPrompt,
          generated: assistantText.length > 0,
          timedOut,
          searchCalls: searchResponses.length,
          searchSuccesses: successfulSearch.length,
          fetchCalls: fetchResponses.length,
          fetchSuccesses: successfulFetch.length,
          citationCount,
          topHits,
          answerPreview: answerText.slice(0, 220),
        })

        const toolSuccessRate = totalToolResponses > 0 ? successfulToolResponses / totalToolResponses : 0
        const searchSuccessRate = totalSearchResponses > 0 ? successfulSearchResponses / totalSearchResponses : 0
        const fetchSuccessRate = totalFetchResponses > 0 ? successfulFetchResponses / totalFetchResponses : 1
        const citationRate = reports.length > 0 ? roundsWithCitations / reports.length : 0

        writeRegressionReport(reportPaths, {
          startedAt,
          finishedAt: formatHumanTime(),
          status: 'running',
          roundsPlanned: cases.length,
          roundsCompleted: reports.length,
          roundLimit: ROUND_LIMIT,
          toolSuccessRate,
          searchSuccessRate,
          fetchSuccessRate,
          citationRate,
          reports,
        })
        console.info(`[qa-regression] round ${index + 1}/${cases.length} done ${testCase.id} generated=${assistantText.length > 0} search=${successfulSearch.length}/${searchResponses.length} fetch=${successfulFetch.length}/${fetchResponses.length}`)
      }

      const toolSuccessRate = totalToolResponses > 0 ? successfulToolResponses / totalToolResponses : 0
      const searchSuccessRate = totalSearchResponses > 0 ? successfulSearchResponses / totalSearchResponses : 0
      const fetchSuccessRate = totalFetchResponses > 0 ? successfulFetchResponses / totalFetchResponses : 1
      const citationRate = reports.length > 0 ? roundsWithCitations / reports.length : 0
      const finishedAt = formatHumanTime()
      writeRegressionReport(reportPaths, {
        startedAt,
        finishedAt,
        status: 'passed',
        roundsPlanned: cases.length,
        roundsCompleted: reports.length,
        roundLimit: ROUND_LIMIT,
        toolSuccessRate,
        searchSuccessRate,
        fetchSuccessRate,
        citationRate,
        reports,
      })

      console.info(JSON.stringify({
        suite: 'qa-agent knowledge regression',
        rounds: reports.length,
        toolSuccessRate,
        searchSuccessRate,
        fetchSuccessRate,
        citationRate,
        reportPaths,
        reports,
      }, null, 2))

      assertCondition(reports.length === cases.length, 'expected every maintained regression case to complete')
      assertCondition(totalFetchResponses > 0, 'expected at least one fetch tool response across the regression')
      expect(toolSuccessRate).toBeGreaterThanOrEqual(MIN_TOOL_SUCCESS_RATE)
      expect(searchSuccessRate).toBeGreaterThanOrEqual(MIN_TOOL_SUCCESS_RATE)
      expect(fetchSuccessRate).toBeGreaterThanOrEqual(MIN_TOOL_SUCCESS_RATE)
    } catch (error) {
      const toolSuccessRate = totalToolResponses > 0 ? successfulToolResponses / totalToolResponses : 0
      const searchSuccessRate = totalSearchResponses > 0 ? successfulSearchResponses / totalSearchResponses : 0
      const fetchSuccessRate = totalFetchResponses > 0 ? successfulFetchResponses / totalFetchResponses : 1
      const citationRate = reports.length > 0 ? roundsWithCitations / reports.length : 0
      const finishedAt = formatHumanTime()
      const failureMessage = error instanceof Error ? error.message : String(error)
      writeRegressionReport(reportPaths, {
        startedAt,
        finishedAt,
        status: 'failed',
        roundsPlanned: cases.length,
        roundsCompleted: reports.length,
        roundLimit: ROUND_LIMIT,
        toolSuccessRate,
        searchSuccessRate,
        fetchSuccessRate,
        citationRate,
        reports,
        failureMessage,
      })

      console.info(JSON.stringify({
        suite: 'qa-agent knowledge regression',
        status: 'failed',
        reportPaths,
        failureMessage,
        roundsCompleted: reports.length,
      }, null, 2))
      throw error
    }
  }, 30 * 60 * 1000)
})
