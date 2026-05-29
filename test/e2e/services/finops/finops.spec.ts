import { test, expect, type Page, type Route } from '@playwright/test'

const USER = 'e2e-finops-user'

const snapshotStatus = {
  status: 'ready',
  lastRefreshedAt: '2026-05-28T10:00:00Z',
  sourceDbCount: 1,
  skippedDbCount: 0,
  sessionCount: 2,
  lastRefreshError: null,
}

const sessions = [
  {
    id: 'finops-session-1',
    userId: 'admin',
    agentId: 'qa-agent',
    name: 'Investigate high latency',
    sessionType: 'user',
    providerName: 'custom-qwen',
    modelName: 'qwen/qwen3.5-27b',
    createdAt: '2026-05-28T09:00:00Z',
    updatedAt: '2026-05-28T09:20:00Z',
    totalTokens: 1200,
    inputTokens: 1000,
    outputTokens: 200,
    scheduleId: null,
    messageCount: 3,
    userMessageCount: 1,
    assistantMessageCount: 1,
    toolResponseCount: 1,
    label: 'Investigate high latency',
  },
  {
    id: 'finops-session-2',
    userId: 'stress-b4',
    agentId: 'ops-agent',
    name: 'Scheduled report',
    sessionType: 'scheduled',
    providerName: 'ollama',
    modelName: 'qwen3.5:9b',
    createdAt: '2026-05-28T08:00:00Z',
    updatedAt: '2026-05-28T08:05:00Z',
    totalTokens: 300,
    inputTokens: 260,
    outputTokens: 40,
    scheduleId: 'schedule-1',
    messageCount: 2,
    userMessageCount: 1,
    assistantMessageCount: 1,
    toolResponseCount: 0,
    label: 'Scheduled report',
  },
]

const models = [
  {
    providerName: 'custom-qwen',
    modelName: 'qwen/qwen3.5-27b',
    sessionCount: 1,
    activeUsers: 1,
    activeAgents: 1,
    totalTokens: 1200,
    inputTokens: 1000,
    outputTokens: 200,
    avgTokensPerSession: 1200,
  },
  {
    providerName: 'ollama',
    modelName: 'qwen3.5:9b',
    sessionCount: 1,
    activeUsers: 1,
    activeAgents: 1,
    totalTokens: 300,
    inputTokens: 260,
    outputTokens: 40,
    avgTokensPerSession: 300,
  },
]

const overview = {
  snapshotStatus,
  summary: {
    current: {
      sessionCount: 2,
      totalTokens: 1500,
      inputTokens: 1260,
      outputTokens: 240,
      activeUsers: 2,
      activeAgents: 2,
      activeModels: 2,
      scheduledSessionCount: 1,
      manualSessionCount: 1,
      avgTokensPerSession: 750,
    },
    previous: {
      sessionCount: 0,
      totalTokens: 0,
      inputTokens: 0,
      outputTokens: 0,
      activeUsers: 0,
      activeAgents: 0,
      activeModels: 0,
      scheduledSessionCount: 0,
      manualSessionCount: 0,
      avgTokensPerSession: 0,
    },
    tokenDelta: 1500,
    tokenGrowthRate: null,
    sessionDelta: 2,
    sessionGrowthRate: null,
  },
  tokenTrend: [
    { bucket: '2026-05-27', sessionCount: 1, totalTokens: 600, inputTokens: 500, outputTokens: 100 },
    { bucket: '2026-05-28', sessionCount: 2, totalTokens: 1500, inputTokens: 1260, outputTokens: 240 },
  ],
  topAgents: [
    {
      agentId: 'qa-agent',
      activeUsers: 1,
      sessionCount: 1,
      totalTokens: 1200,
      inputTokens: 1000,
      outputTokens: 200,
      avgTokensPerSession: 1200,
      scheduledSessionCount: 0,
      highTokenSessionCount: 1,
    },
  ],
  topUsers: [
    {
      userId: 'admin',
      activeAgents: 1,
      sessionCount: 1,
      totalTokens: 1200,
      inputTokens: 1000,
      outputTokens: 200,
      avgTokensPerSession: 1200,
      lastActiveAt: '2026-05-28T09:20:00Z',
      topAgent: 'qa-agent',
    },
  ],
  topSessions: sessions,
  models,
  taskExecutionLoad: {
    avgTokensPerTask: 750,
    avgMessagesPerTask: 2.5,
    avgToolResponsesPerTask: 0.5,
  },
  sessionTypeDistribution: [
    { id: 'user', label: 'user', sessionCount: 1, totalTokens: 1200, percentage: 0.8 },
    { id: 'scheduled', label: 'scheduled', sessionCount: 1, totalTokens: 300, percentage: 0.2 },
  ],
  providerDistribution: [
    { id: 'custom-qwen', label: 'custom-qwen', sessionCount: 1, totalTokens: 1200, percentage: 0.8 },
    { id: 'ollama', label: 'ollama', sessionCount: 1, totalTokens: 300, percentage: 0.2 },
  ],
}

async function loginAs(page: Page, username: string) {
  await page.goto('/#/')
  await page.evaluate((userId) => {
    localStorage.setItem('opsfactory:userId', userId)
  }, username)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForURL(/\/#\/?$/)
}

async function mockFinOpsApi(page: Page) {
  await page.route(/\/config\.json(?:\?|$)/, async (route) => {
    await json(route, {
      gatewayUrl: '',
      gatewaySecretKey: 'test-secret',
      finopsServiceUrl: '',
      finopsSecretKey: 'finops-secret',
      logging: { level: 'error', consoleEnabled: false },
    })
  })
  await page.route(/\/gateway\/status(?:\?|$)/, async (route) => {
    await json(route, { status: 'ok' })
  })
  await page.route(/^https?:\/\/[^/]+\/finops\/.*/, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path.endsWith('/overview')) {
      await json(route, overview)
      return
    }
    if (path.endsWith('/refresh')) {
      await json(route, snapshotStatus)
      return
    }
    if (path.endsWith('/sessions/finops-session-1/messages')) {
      await json(route, {
        snapshotStatus,
        session: sessions[0],
        stats: {
          messageCount: 3,
          userMessageCount: 1,
          assistantMessageCount: 1,
          toolRequestCount: 1,
          toolResponseCount: 1,
          messagesWithTokenCount: 1,
          largestContentLength: 32,
          largestContentRole: 'tool',
          largestContentPreview: 'Search logs for latency spikes',
        },
        capabilities: {
          messageTokenAvailable: true,
          contentPreviewAvailable: true,
          toolSignalAvailable: true,
        },
        messages: [
          {
            messageId: 'msg-1',
            rowId: 1,
            role: 'user',
            createdAt: '2026-05-28T09:00:00Z',
            insertedAt: '2026-05-28T09:00:00Z',
            tokens: 24,
            contentLength: 25,
            contentPreview: 'Investigate high latency',
            contentText: 'Investigate high latency',
            contentTruncated: false,
            toolRequest: false,
            toolResponse: false,
            toolName: null,
            error: false,
            userVisible: true,
            agentVisible: true,
          },
          {
            messageId: 'msg-2',
            rowId: 2,
            role: 'tool',
            createdAt: '2026-05-28T09:01:00Z',
            insertedAt: '2026-05-28T09:01:00Z',
            tokens: null,
            contentLength: 32,
            contentPreview: 'Search logs for latency spikes',
            contentText: 'Search logs for latency spikes',
            contentTruncated: false,
            toolRequest: false,
            toolResponse: true,
            toolName: 'search',
            error: false,
            userVisible: false,
            agentVisible: true,
          },
        ],
      })
      return
    }
    if (path.endsWith('/sessions')) {
      await json(route, pageResponse(sessions))
      return
    }
    if (path.endsWith('/agents')) {
      await json(route, pageResponse(overview.topAgents))
      return
    }
    if (path.endsWith('/users')) {
      await json(route, pageResponse(overview.topUsers))
      return
    }
    if (path.endsWith('/models')) {
      await json(route, pageResponse(models))
      return
    }
    await route.fulfill({ status: 404, body: '{}' })
  })
}

function pageResponse<T>(items: T[]) {
  return {
    snapshotStatus,
    items,
    page: 1,
    size: 25,
    totalItems: items.length,
    totalPages: 1,
  }
}

async function json(route: Route, body: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

test.describe('FinOps — token operations page', () => {
  test('loads overview, switches dimensions, refreshes, and opens session messages', async ({ page }) => {
    await mockFinOpsApi(page)
    await loginAs(page, USER)

    await page.goto('/#/finops')

    await expect(page.locator('.finops-page')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.finops-metrics .stat-card')).toHaveCount(6)
    await expect(page.locator('.finops-trend-chart')).toBeVisible()
    await expect(page.locator('.finops-model-row', { hasText: 'qwen/qwen3.5-27b' })).toBeVisible()

    await page.getByRole('tab', { name: /sessions|会话/i }).click()
    await expect(page.locator('.finops-clickable-row', { hasText: 'Investigate high latency' })).toBeVisible()

    await page.getByRole('tab', { name: /models|模型/i }).click()
    await expect(page.locator('.finops-strong-cell', { hasText: 'qwen3.5:9b' })).toBeVisible()

    await page.getByRole('tab', { name: /sessions|会话/i }).click()
    await page.locator('.finops-clickable-row', { hasText: 'Investigate high latency' }).click()
    await expect(page.locator('.finops-session-drawer')).toBeVisible()
    await expect(page.locator('.finops-message-item', { hasText: 'Search logs for latency spikes' })).toBeVisible()

    await page.locator('.finops-drawer-close').click()
    await page.getByRole('button', { name: /refresh|刷新/i }).click()
    await expect(page.locator('.conn-banner-error')).toHaveCount(0)
  })
})
