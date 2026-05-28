import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const ROOT = resolve(process.cwd())

function read(path: string): string {
    return readFileSync(resolve(ROOT, path), 'utf-8')
}

function nestedKeys(value: unknown, prefix = ''): string[] {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return [prefix]
    }
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) => {
        const next = prefix ? `${prefix}.${key}` : key
        return nestedKeys(child, next)
    })
}

describe('finops frontend structure', () => {
    it('registers an authenticated route and sidebar item', () => {
        const moduleSource = read('src/app/modules/finops/module.ts')

        expect(moduleSource).toContain("id: 'finops'")
        expect(moduleSource).toContain("path: '/finops'")
        expect(moduleSource).toContain("titleKey: 'sidebar.finops'")
        expect(moduleSource).toContain("icon: 'finops'")
        expect(moduleSource).toContain("access: 'authenticated'")
    })

    it('declares runtime URL, secret configuration, and dev proxy', () => {
        const runtimeSource = read('src/config/runtime.ts')
        const viteSource = read('vite.config.ts')
        const standalone = JSON.parse(read('config.standalone.json.example')) as Record<string, unknown>
        const embed = JSON.parse(read('config.embed.json.example')) as Record<string, unknown>

        expect(runtimeSource).toContain('finopsServiceUrl')
        expect(runtimeSource).toContain('finopsSecretKey')
        expect(runtimeSource).toContain("pathPrefix: '/finops'")
        expect(runtimeSource).toContain('FINOPS_URL')
        expect(runtimeSource).toContain('FINOPS_SECRET_KEY')
        expect(viteSource).toContain("'/finops': 'http://127.0.0.1:8097'")
        expect(standalone.finopsServiceUrl).toBe('http://127.0.0.1:8097')
        expect(standalone.finopsSecretKey).toBe('')
        expect(embed.finopsServiceUrl).toBe('')
        expect(embed.finopsSecretKey).toBe('')
    })

    it('routes API calls only to supported finops endpoints', () => {
        const apiSource = read('src/services/finopsAPI.ts')

        expect(apiSource).toContain('FINOPS_URL')
        expect(apiSource).toContain('FINOPS_SECRET_KEY')
        expect(apiSource).toContain("'x-secret-key': runtime.FINOPS_SECRET_KEY")
        expect(apiSource).toContain('/overview?compare=true')
        expect(apiSource).toContain('/agents?')
        expect(apiSource).toContain('/users?')
        expect(apiSource).toContain('/sessions?')
        expect(apiSource).toContain('/messages?')
        expect(apiSource).toContain('/models?')
        expect(apiSource).toContain('/refresh')
        expect(apiSource).not.toContain('/recommendations')
        expect(apiSource).not.toContain('/reports/summary')
        expect(apiSource).not.toContain('interface Recommendation')
    })

    it('keeps visible finops copy aligned in both locales', () => {
        const en = JSON.parse(read('src/i18n/en.json'))
        const zh = JSON.parse(read('src/i18n/zh.json'))

        expect(en.sidebar.finops).toBe('Token Ops')
        expect(zh.sidebar.finops).toBe('Token 运营')
        expect(nestedKeys(en.finops).sort()).toEqual(nestedKeys(zh.finops).sort())
        expect(Object.keys(en.finops.tabs).sort()).toEqual(['agents', 'models', 'overview', 'sessions', 'users'])
        expect(Object.keys(zh.finops.tabs).sort()).toEqual(['agents', 'models', 'overview', 'sessions', 'users'])
    })

    it('uses the agreed tab set without recommendations, reports, or search', () => {
        const pageSource = read('src/app/modules/finops/pages/FinOpsPage.tsx')
        const overviewSource = read('src/app/modules/finops/components/OverviewTab.tsx')
        const tabsSource = read('src/app/modules/finops/components/DimensionTabs.tsx')
        const sharedSource = read('src/app/modules/finops/components/SharedComponents.tsx')

        expect(pageSource).toContain("const tabs: TabId[] = ['overview', 'agents', 'users', 'sessions', 'models']")
        expect(pageSource).toContain('PageHeader')
        expect(pageSource).toContain('ListWorkbench')
        expect(pageSource).toContain('ListToolbar')
        expect(pageSource).toContain('StatCard')
        expect(overviewSource).toContain('SectionCard')
        expect(tabsSource).toContain('AnalyticsTableCard')
        expect(sharedSource).toContain('PaginationControls')
        expect(sharedSource).toContain("../../../platform/ui/primitives/Pagination")
        expect(pageSource).not.toContain('finops-pagination')
        expect(pageSource).not.toContain("'recommendations'")
        expect(pageSource).not.toContain("'reports'")
        expect(pageSource).not.toContain('Search')
        expect(pageSource).not.toContain('selectedRow')
    })

    it('opens message explainability only from session rows', () => {
        const pageSource = read('src/app/modules/finops/pages/FinOpsPage.tsx')
        const sessionDrawerSource = read('src/app/modules/finops/components/SessionMessagesDrawer.tsx')
        const tabsSource = read('src/app/modules/finops/components/DimensionTabs.tsx')

        expect(pageSource).toContain('SessionMessagesDrawer')
        expect(pageSource).toContain('fetchFinOpsSessionMessages(currentSession)')
        expect(pageSource).toContain('onSessionSelect={setSelectedSession}')
        expect(tabsSource).toContain('finops-clickable-row')
        expect(sessionDrawerSource).toContain('finops.drawer.messageTokens')
    })

    it('uses message creation time for latency badges and formats sub-second gaps', () => {
        const drawersSource = read('src/app/modules/finops/components/SessionMessagesDrawer.tsx')

        expect(drawersSource).toContain('const createdAtMs = new Date(message.createdAt).getTime()')
        expect(drawersSource).toContain('return new Date(message.insertedAt).getTime()')
        expect(drawersSource).toContain('diff >= 0 && diff < 3600')
        expect(drawersSource).toContain('durationSeconds >= 0')
        expect(drawersSource).toContain("return '<1s'")
    })
})
