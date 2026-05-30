/**
 * Schedule API Integration Tests
 *
 * Tests the /schedule endpoints via the gateway.
 */
import { randomUUID } from 'node:crypto'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { startJavaGateway, type GatewayHandle } from '../../../platform/shared/helpers.js'

const AGENT_ID = 'universal-agent'

let gw: GatewayHandle
let scheduleId = ''
let runSessionId = ''

beforeAll(async () => {
    gw = await startJavaGateway()
}, 60_000)

afterAll(async () => {
    if (scheduleId) {
        await gw.fetch(`/agents/${AGENT_ID}/schedule/delete/${encodeURIComponent(scheduleId)}`, {
            method: 'DELETE',
        }).catch(() => undefined)
    }
    if (runSessionId) {
        await gw.fetch(`/agents/${AGENT_ID}/sessions/${encodeURIComponent(runSessionId)}`, {
            method: 'DELETE',
        }).catch(() => undefined)
    }
    if (gw) {
        await gw.stop()
    }
}, 20_000)

describe('Schedule lifecycle', () => {
    it('schedule routes are restored through gateway', async () => {
        scheduleId = `gateway-schedule-${randomUUID()}`

        const createRes = await gw.fetch(`/agents/${AGENT_ID}/schedule/create`, {
            method: 'POST',
            body: JSON.stringify({
                id: scheduleId,
                cron: '*/15 * * * *',
                recipe: {
                    title: 'Gateway Schedule Test',
                    description: 'Temporary schedule created by gateway integration test',
                    instructions: 'Reply with a short confirmation.',
                },
            }),
        })
        const createText = await createRes.text()
        expect(createText.includes('Resource not found: gateway/agents/')).toBe(false)
        if (!createRes.ok) {
            return
        }

        const listRes = await gw.fetch(`/agents/${AGENT_ID}/schedule/list`)
        const listText = await listRes.text()
        expect(listText.includes('Resource not found: gateway/agents/')).toBe(false)
        if (!listRes.ok) {
            return
        }
        const listData = JSON.parse(listText) as { jobs?: Array<Record<string, unknown>> }
        const jobs = listData.jobs ?? []
        expect(jobs.some(job => job.id === scheduleId)).toBe(true)

        const updateRes = await gw.fetch(`/agents/${AGENT_ID}/schedule/${encodeURIComponent(scheduleId)}`, {
            method: 'PUT',
            body: JSON.stringify({ cron: '0 * * * *' }),
        })
        const updateText = await updateRes.text()
        expect(updateText.includes('Resource not found: gateway/agents/')).toBe(false)

        const pauseRes = await gw.fetch(`/agents/${AGENT_ID}/schedule/${encodeURIComponent(scheduleId)}/pause`, {
            method: 'POST',
        })
        const pauseText = await pauseRes.text()
        expect(pauseText.includes('Resource not found: gateway/agents/')).toBe(false)

        const unpauseRes = await gw.fetch(`/agents/${AGENT_ID}/schedule/${encodeURIComponent(scheduleId)}/unpause`, {
            method: 'POST',
        })
        const unpauseText = await unpauseRes.text()
        expect(unpauseText.includes('Resource not found: gateway/agents/')).toBe(false)

        const runRes = await gw.fetch(`/agents/${AGENT_ID}/schedule/${encodeURIComponent(scheduleId)}/run_now`, {
            method: 'POST',
        })
        const runText = await runRes.text()
        expect(runText.includes('Resource not found: gateway/agents/')).toBe(false)
        if (runRes.ok) {
            const runData = JSON.parse(runText) as { session_id?: string }
            runSessionId = runData.session_id ?? ''
        }

        const inspectRes = await gw.fetch(`/agents/${AGENT_ID}/schedule/${encodeURIComponent(scheduleId)}/inspect`)
        const inspectText = await inspectRes.text()
        expect(inspectText.includes('Resource not found: gateway/agents/')).toBe(false)

        const sessionsRes = await gw.fetch(
            `/agents/${AGENT_ID}/schedule/${encodeURIComponent(scheduleId)}/sessions?limit=5`
        )
        const sessionsText = await sessionsRes.text()
        expect(sessionsText.includes('Resource not found: gateway/agents/')).toBe(false)

        const killRes = await gw.fetch(`/agents/${AGENT_ID}/schedule/${encodeURIComponent(scheduleId)}/kill`, {
            method: 'POST',
        })
        const killText = await killRes.text()
        expect(killText.includes('Resource not found: gateway/agents/')).toBe(false)

        const deleteRes = await gw.fetch(`/agents/${AGENT_ID}/schedule/delete/${encodeURIComponent(scheduleId)}`, {
            method: 'DELETE',
        })
        const deleteText = await deleteRes.text()
        expect(deleteText.includes('Resource not found: gateway/agents/')).toBe(false)
        scheduleId = ''
    })
})
