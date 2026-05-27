/**
 * Frontend E2E Tests (Playwright)
 *
 * Prerequisites: gateway + webapp must be running:
 *   cd ops-factory && scripts/ctl.sh startup all
 *
 * Run:
 *   cd test && npx playwright test --config playwright.config.ts
 *
 * Covered:
 *   - Sidebar navigation (all authenticated users)
 *   - Page access: authenticated users can access all pages
 *   - Agents page: lists agents, configure button visibility
 *   - Agent Configure page: edit prompt, save
 *   - History page: renders, search input present
 *   - Files page: renders with category filters
 *   - Chat page: send message, receive streaming response
 *   - Settings page: shows user, logout
 *   - Embed mode: sidebar hidden, URL-based auth, page rendering
 */
import { test, expect, type Page } from '@playwright/test'

const REGULAR_USER = 'e2e-test-user'
const ADMIN_USER = 'admin'
const USER_STORAGE_KEY = 'opsfactory:userId'

// Helper: seed the current user directly through runtime storage.
async function loginAs(page: Page, username: string) {
  await page.goto('/#/')
  await page.evaluate(([storageKey, userId]) => {
    localStorage.setItem(storageKey, userId)
  }, [USER_STORAGE_KEY, username])
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForURL(/\/#\/?$/)
  // Wait for role to be fetched from /me
  await page.waitForTimeout(500)
}

async function waitForHomeReady(page: Page) {
  await expect(page.locator('.chat-input')).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.new-chat-nav')).toBeVisible({ timeout: 15_000 })
}

// Helper: login as regular user (backward compat)
async function login(page: Page) {
  await loginAs(page, REGULAR_USER)
}

// =====================================================
// 1. Sidebar Navigation — All Authenticated Users
// =====================================================
test.describe('Sidebar navigation — authenticated user', () => {
  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  const userNavItems = [
    { text: '首页', path: '/' },
    { text: '历史记录', path: '/history' },
    { text: '文件', path: '/files' },
    { text: '收件箱', path: '/inbox' },
  ]

  for (const item of userNavItems) {
    test(`navigates to ${item.text} (${item.path})`, async ({ page }) => {
      await page.getByRole('link', { name: item.text }).click()
      await expect(page).toHaveURL(new RegExp(`/#${item.path === '/' ? '/?' : item.path}$`))
    })
  }

  test('shows Agents link', async ({ page }) => {
    await expect(page.getByRole('link', { name: '智能体' })).toBeVisible()
  })

  test('shows Scheduler link', async ({ page }) => {
    await expect(page.getByRole('link', { name: '定时任务' })).toBeVisible()
  })

})

// =====================================================
// 2. Sidebar Navigation — Authenticated User (All Links)
// =====================================================
test.describe('Sidebar navigation — authenticated user sees all links', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, REGULAR_USER)
  })

  const allNavItems = [
    { text: '首页', path: '/' },
    { text: '历史记录', path: '/history' },
    { text: '文件', path: '/files' },
    { text: '收件箱', path: '/inbox' },
    { text: '智能体', path: '/agents' },
    { text: '定时任务', path: '/scheduler' },
  ]

  for (const item of allNavItems) {
    test(`shows and navigates to ${item.text} (${item.path})`, async ({ page }) => {
      const link = page.getByRole('link', { name: item.text })
      await expect(link).toBeVisible()
      await link.click()
      await expect(page).toHaveURL(new RegExp(`/#${item.path === '/' ? '/?' : item.path}$`))
    })
  }
})

// =====================================================
// 3. Page Access — All Authenticated Users
// =====================================================
test.describe('Page access — authenticated users', () => {
  test('any user can access /agents/:id/configure', async ({ page }) => {
    await login(page)
    await page.goto('/#/agents/universal-agent/configure')
    await expect(page).toHaveURL(/\/#\/agents\/universal-agent\/configure$/)
    // Verify the configure page content loaded
    await page.waitForSelector('text=universal-agent', { timeout: 10_000 })
  })

  test('any user can access /scheduler', async ({ page }) => {
    await login(page)
    await page.goto('/#/scheduler')
    await expect(page).toHaveURL(/\/#\/scheduler$/)
  })
})

// =====================================================
// 4. Agents Page — All Authenticated Users
// =====================================================
test.describe('Agents page', () => {
  test('any user can access /agents', async ({ page }) => {
    await login(page)
    await page.goto('/#/agents')
    await expect(page).toHaveURL(/\/#\/agents$/)
  })

  test('any user sees Configure button on agent cards', async ({ page }) => {
    await login(page)
    await page.goto('/#/agents')
    await page.waitForSelector('article', { timeout: 10_000 })
    const configBtn = page.getByRole('button', { name: '配置' }).first()
    await expect(configBtn).toBeVisible()
  })

  test('configure button navigates to agent settings', async ({ page }) => {
    await login(page)
    await page.goto('/#/agents')
    await page.waitForSelector('article', { timeout: 10_000 })
    const configBtn = page.getByRole('button', { name: '配置' }).first()
    await configBtn.click()
    await expect(page).toHaveURL(/\/#\/agents\/[^/]+\/configure/)
  })

  test('agent cards show model info', async ({ page }) => {
    await login(page)
    await page.goto('/#/agents')
    await page.waitForSelector('article', { timeout: 10_000 })
    await expect(page.locator('article').first()).toContainText(/qwen|GLM|custom/i)
  })
})

// =====================================================
// 5. Agent Configure Page
// =====================================================
test.describe('Agent configure page', () => {
  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  test('loads agent configure page', async ({ page }) => {
    await page.goto('/#/agents/universal-agent/configure')
    await page.waitForSelector('text=universal-agent', { timeout: 10_000 })
    await expect(page.locator('text=Universal Agent').first()).toBeVisible()
  })

  test('shows agent basic info fields', async ({ page }) => {
    await page.goto('/#/agents/universal-agent/configure')
    await page.waitForSelector('text=universal-agent', { timeout: 10_000 })
    // Verify basic info section is visible
    await expect(page.locator('text=角色名称')).toBeVisible()
    await expect(page.locator('text=角色 ID')).toBeVisible()
  })
})

// =====================================================
// 6. History Page
// =====================================================
test.describe('History page', () => {
  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  test('renders with search and filter controls', async ({ page }) => {
    await page.goto('/#/history')
    const search = page.locator('input[placeholder*="Search"]').or(page.locator('input[placeholder*="搜索"]')).or(page.locator('input[type="search"]'))
    await expect(search.first()).toBeVisible({ timeout: 5000 })
  })
})

// =====================================================
// 7. Files Page
// =====================================================
test.describe('Files page', () => {
  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  test('renders with category filters', async ({ page }) => {
    await page.goto('/#/files')
    await page.waitForSelector('text=全部', { timeout: 5000 })
    await expect(page.locator('text=全部').first()).toBeVisible()
  })
})

// =====================================================
// 8. Chat Page
// =====================================================
test.describe('Chat page', () => {
  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  test('can send a message and receive a streamed response', async ({ page }) => {
    await page.goto('/#/chat')
    const chatInput = page.locator('textarea').or(page.locator('[contenteditable]')).or(page.locator('input[type="text"]'))
    await expect(chatInput.first()).toBeVisible({ timeout: 15_000 })
    await chatInput.first().fill('Reply with the single word "pong"')
    await chatInput.first().press('Enter')
    await page.waitForTimeout(3000)
    const messageArea = page.locator('.main-content')
    const textContent = await messageArea.textContent()
    expect(textContent?.length).toBeGreaterThan(10)
  }, 120_000)
})

// =====================================================
// 9. Chat — session working_dir & multi-user
// =====================================================
test.describe('Chat — session working_dir isolation', () => {
  const USER_A = 'e2e-alice'
  const USER_B = 'e2e-bob'

  test('regular user creates session and working_dir points to correct user directory', async ({ page }) => {
    await loginAs(page, USER_A)
    await waitForHomeReady(page)

    // Intercept the /agent/start response to verify working_dir
    const startResponsePromise = page.waitForResponse(
      resp => resp.url().includes('/agent/start') && resp.status() === 200
    )
    // Click New Chat to create a session
    await page.locator('.new-chat-nav').click()
    await expect(page).toHaveURL(/\/chat/, { timeout: 15_000 })

    const startResponse = await startResponsePromise
    const sessionData = await startResponse.json()
    // working_dir must contain the user ID and agent ID
    expect(sessionData.working_dir).toContain(USER_A)
    expect(sessionData.working_dir).toContain('universal-agent')
    // Must NOT point to another user's directory
    expect(sessionData.working_dir).not.toContain(USER_B)
  }, 120_000)

  test('different user gets different working_dir', async ({ page }) => {
    await loginAs(page, USER_B)
    await waitForHomeReady(page)

    const startResponsePromise = page.waitForResponse(
      resp => resp.url().includes('/agent/start') && resp.status() === 200
    )
    // Click New Chat to create a session
    await page.locator('.new-chat-nav').click()
    await expect(page).toHaveURL(/\/chat/, { timeout: 15_000 })

    const startResponse = await startResponsePromise
    const sessionData = await startResponse.json()
    expect(sessionData.working_dir).toContain(USER_B)
    expect(sessionData.working_dir).toContain('universal-agent')
    expect(sessionData.working_dir).not.toContain(USER_A)
  }, 120_000)

  test('no system_info 403 errors in console for regular user', async ({ page }) => {
    const errors: string[] = []
    page.on('response', resp => {
      if (resp.status() === 403) {
        errors.push(`403 on ${resp.url()}`)
      }
    })

    await loginAs(page, USER_A)
    await page.goto('/#/chat')
    await page.waitForTimeout(3000) // Wait for system_info and other requests

    // No 403 errors should occur
    expect(errors).toEqual([])
  }, 30_000)
})

// =====================================================
// 10. Settings Page
// =====================================================
test.describe('Settings modal', () => {
  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  test('shows user info and logout button', async ({ page }) => {
    // Settings is a modal opened via the gear icon in the sidebar
    const settingsBtn = page.locator('.sidebar-user-btn').first()
    await settingsBtn.click()
    await expect(page.locator('.settings-modal')).toBeVisible({ timeout: 5000 })
    // Switch to user tab
    const userTab = page.locator('.settings-nav-item').filter({ hasText: '用户' })
    await userTab.click()
    await expect(page.locator(`.settings-username:has-text("${REGULAR_USER}")`)).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.settings-logout-btn')).toBeVisible()
  })

})

// =====================================================
// 11. Embed Mode
// =====================================================
test.describe('Embed mode', () => {
  const EMBED_USER = 'e2e-embed-user'

  test('hides sidebar when embed=true', async ({ page }) => {
    await page.goto(`/#/history?embed=true&userId=${EMBED_USER}`)
    // Sidebar should not be rendered
    await expect(page.locator('.sidebar')).not.toBeVisible()
    // Main content should still render
    await expect(page.locator('.main-content')).toBeVisible()
  })

  test('main-wrapper has embed-mode class', async ({ page }) => {
    await page.goto(`/#/history?embed=true&userId=${EMBED_USER}`)
    await expect(page.locator('.main-wrapper.embed-mode')).toBeVisible()
  })

  test('auto-authenticates via userId URL param', async ({ page }) => {
    // Clear any existing auth
    await page.goto('/#/')
    await page.evaluate(() => localStorage.clear())
    // Navigate with embed params — should remain inside the app shell.
    await page.goto(`/#/files?embed=true&userId=${EMBED_USER}`)
    await expect(page.locator('.main-content')).toBeVisible()
  })

  test('userId from URL is persisted to localStorage', async ({ page }) => {
    await page.goto(`/#/history?embed=true&userId=${EMBED_USER}`)
    await page.waitForFunction(
      (expectedUserId) => localStorage.getItem('opsfactory:userId') === expectedUserId,
      EMBED_USER,
      { timeout: 5000 }
    )
    const storedUserId = await page.evaluate(() => localStorage.getItem('opsfactory:userId'))
    expect(storedUserId).toBe(EMBED_USER)
  })

  test('FilePreview is not rendered in embed mode', async ({ page }) => {
    await page.goto(`/#/files?embed=true&userId=${EMBED_USER}`)
    await expect(page.locator('.file-preview')).not.toBeVisible()
  })

  test('each page renders correctly in embed mode', async ({ page }) => {
    const pages = [
      { path: '/#/history', marker: 'input' },
      { path: '/#/files', marker: 'text=全部' },
      { path: '/#/inbox', marker: '.main-content' },
    ]
    for (const p of pages) {
      await page.goto(`${p.path}?embed=true&userId=${EMBED_USER}`)
      await expect(page.locator('.sidebar')).not.toBeVisible()
      await expect(page.locator(p.marker).first()).toBeVisible({ timeout: 5000 })
    }
  })

  test('non-embed mode still shows sidebar', async ({ page }) => {
    await login(page)
    await page.goto('/#/history')
    await expect(page.locator('.sidebar')).toBeVisible()
    await expect(page.locator('.main-wrapper.embed-mode')).not.toBeVisible()
  })
})
