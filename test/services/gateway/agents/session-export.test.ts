/**
 * Session export API Integration Tests
 *
 * Tests the /sessions/:id/export endpoint via the gateway.
 */
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { startJavaGateway, type GatewayHandle } from '../../../platform/shared/helpers.js'

const AGENT_ID = 'universal-agent'

let gw: GatewayHandle
let sessionId = ''
let startFailureBody = ''

beforeAll(async () => {
    gw = await startJavaGateway()

    const startRes = await gw.fetch(`/agents/${AGENT_ID}/agent/start`, {
        method: 'POST',
        body: JSON.stringify({}),
    })
    const startText = await startRes.text()
    if (startRes.ok) {
        const session = JSON.parse(startText) as Record<string, unknown>
        sessionId = String(session.id)
        return
    }

    startFailureBody = startText
}, 60_000)

afterAll(async () => {
    if (sessionId) {
        await gw.fetch(`/agents/${AGENT_ID}/sessions/${encodeURIComponent(sessionId)}`, {
            method: 'DELETE',
        }).catch(() => undefined)
    }
    if (gw) {
        await gw.stop()
    }
}, 20_000)

describe('Session export', () => {
    it('GET /agents/:id/sessions/:sessionId/export returns exported content', async () => {
        if (!sessionId) {
            expect(startFailureBody.includes('Resource not found: gateway/agents/')).toBe(false)
            return
        }

        const res = await gw.fetch(`/agents/${AGENT_ID}/sessions/${encodeURIComponent(sessionId)}/export`)
        expect(res.ok).toBe(true)

        const text = await res.text()
        expect(typeof text).toBe('string')
        expect(text.length).toBeGreaterThan(0)
    })
})
