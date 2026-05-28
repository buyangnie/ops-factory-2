import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { runtime } from '../config/runtime'
import {
    fetchFinOpsAgents,
    fetchFinOpsModels,
    fetchFinOpsOverview,
    fetchFinOpsSessionMessages,
    fetchFinOpsSessions,
    fetchFinOpsUsers,
    refreshFinOpsSnapshot,
    type SessionUsage,
} from '../services/finopsAPI'

describe('finops API client', () => {
    beforeEach(() => {
        runtime.FINOPS_URL = 'http://127.0.0.1:8097/finops'
        runtime.FINOPS_SECRET_KEY = 'test-secret'
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it('requests overview with comparison and secret headers', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ snapshotStatus: { status: 'ready' }, summary: {} }),
        })
        vi.stubGlobal('fetch', fetchMock)

        const result = await fetchFinOpsOverview()

        expect(result.snapshotStatus.status).toBe('ready')
        expect(fetchMock).toHaveBeenCalledWith(
            'http://127.0.0.1:8097/finops/overview?compare=true',
            expect.objectContaining({
                headers: expect.objectContaining({
                    'Content-Type': 'application/json',
                    'x-secret-key': 'test-secret',
                }),
            })
        )
    })

    it('posts refresh requests to the finops service', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ status: 'ready', sessionCount: 2 }),
        })
        vi.stubGlobal('fetch', fetchMock)

        const result = await refreshFinOpsSnapshot()

        expect(result.sessionCount).toBe(2)
        expect(fetchMock).toHaveBeenCalledWith(
            'http://127.0.0.1:8097/finops/refresh',
            expect.objectContaining({
                method: 'POST',
                headers: expect.objectContaining({
                    'x-secret-key': 'test-secret',
                }),
            })
        )
    })

    it('requests paginated detail lists for dimension tabs', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ items: [], page: 2, size: 25, totalItems: 0, totalPages: 1 }),
        })
        vi.stubGlobal('fetch', fetchMock)

        await fetchFinOpsAgents(2, 25)
        await fetchFinOpsUsers(2, 25)
        await fetchFinOpsSessions(2, 25)
        await fetchFinOpsModels(2, 25)

        expect(fetchMock).toHaveBeenNthCalledWith(1, 'http://127.0.0.1:8097/finops/agents?page=2&size=25', expect.any(Object))
        expect(fetchMock).toHaveBeenNthCalledWith(2, 'http://127.0.0.1:8097/finops/users?page=2&size=25', expect.any(Object))
        expect(fetchMock).toHaveBeenNthCalledWith(3, 'http://127.0.0.1:8097/finops/sessions?page=2&size=25', expect.any(Object))
        expect(fetchMock).toHaveBeenNthCalledWith(4, 'http://127.0.0.1:8097/finops/models?page=2&size=25', expect.any(Object))
    })

    it('requests session message details with user and agent scope', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ messages: [], stats: { messageCount: 0 } }),
        })
        vi.stubGlobal('fetch', fetchMock)
        const session = {
            id: '20260527_1',
            userId: 'admin',
            agentId: 'qa-agent',
        } as SessionUsage

        await fetchFinOpsSessionMessages(session)

        expect(fetchMock).toHaveBeenCalledWith(
            'http://127.0.0.1:8097/finops/sessions/20260527_1/messages?userId=admin&agentId=qa-agent',
            expect.objectContaining({
                headers: expect.objectContaining({
                    'x-secret-key': 'test-secret',
                }),
            })
        )
    })

    it('surfaces backend error text when a finops request fails', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: false,
            status: 401,
            text: async () => 'Unauthorized',
        }))

        await expect(fetchFinOpsOverview()).rejects.toThrow('Unauthorized')
    })
})
