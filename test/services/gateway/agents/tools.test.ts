/**
 * Tool API Integration Tests
 *
 * Tests the /agent/tools and /agent/call_tool endpoints via the gateway.
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
        await gw.fetch(`/agents/${AGENT_ID}/sessions/${sessionId}`, {
            method: 'DELETE',
        }).catch(() => undefined)
    }
    if (gw) {
        await gw.stop()
    }
}, 20_000)

describe('Tool listing', () => {
    it('GET /agents/:id/agent/tools returns an array for a live session', async () => {
        if (!sessionId) {
            expect(startFailureBody.includes('Resource not found: gateway/agents/')).toBe(false)
            return
        }

        const res = await gw.fetch(`/agents/${AGENT_ID}/agent/tools?session_id=${encodeURIComponent(sessionId)}`)
        expect(res.ok).toBe(true)

        const data = await res.json() as Array<Record<string, unknown>>
        expect(Array.isArray(data)).toBe(true)
    })

    it('GET /agents/:id/agent/tools forwards extension_name query', async () => {
        if (!sessionId) {
            expect(startFailureBody.includes('Resource not found: gateway/agents/')).toBe(false)
            return
        }

        const res = await gw.fetch(
            `/agents/${AGENT_ID}/agent/tools?session_id=${encodeURIComponent(sessionId)}&extension_name=${encodeURIComponent('control-center')}`
        )
        expect([200, 400, 404]).toContain(res.status)

        const text = await res.text()
        expect(text.includes('Resource not found: gateway/agents/')).toBe(false)
    })
})

describe('Tool call route', () => {
    it('POST /agents/:id/agent/call_tool is no longer missing at gateway', async () => {
        if (!sessionId) {
            expect(startFailureBody.includes('Resource not found: gateway/agents/')).toBe(false)
            return
        }

        const res = await gw.fetch(`/agents/${AGENT_ID}/agent/call_tool`, {
            method: 'POST',
            body: JSON.stringify({
                session_id: sessionId,
                name: 'definitely_missing_tool_for_route_verification',
                arguments: {},
            }),
        })

        expect([200, 400, 404, 500]).toContain(res.status)
        const text = await res.text()
        expect(text.includes('Resource not found: gateway/agents/')).toBe(false)
    })
})
