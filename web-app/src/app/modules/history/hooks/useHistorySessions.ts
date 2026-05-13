import { useState, useEffect, useCallback } from 'react'
import { runtime, gatewayHeaders } from '../../../../config/runtime'
import { useUser } from '../../../platform/providers/UserContext'
import { trackedFetch } from '../../../platform/logging/requestClient'
import type { Session } from '@goosed/sdk'

export interface AgentSession extends Session {
    agentId: string
}

export interface PaginatedSessions {
    sessions: AgentSession[]
    total: number
    pageIndex: number
    pageSize: number
    totalPages: number
}

export function useHistorySessions(params: {
    pageIndex: number
    pageSize: number
    search?: string
    agentId?: string
    type?: string
    enabled?: boolean
}) {
    const { userId } = useUser()
    const [data, setData] = useState<PaginatedSessions>({ sessions: [], total: 0, pageIndex: 1, pageSize: 20, totalPages: 0 })
    const [isLoading, setIsLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    const fetchSessions = useCallback(async () => {
        if (!userId) { setIsLoading(false); return }
        setIsLoading(true)
        setError(null)
        try {
            const qs = new URLSearchParams()
            qs.set('pageIndex', String(params.pageIndex))
            qs.set('pageSize', String(params.pageSize))
            if (params.search?.trim()) qs.set('search', params.search.trim())
            if (params.agentId) qs.set('agentId', params.agentId)
            if (params.type) qs.set('type', params.type)
            const res = await trackedFetch(`${runtime.GATEWAY_URL}/sessions?${qs}`, {
                category: 'request',
                name: 'request.send',
                headers: gatewayHeaders(userId),
                cache: 'no-store',
                signal: AbortSignal.timeout(30000),
            })
            if (!res.ok) throw new Error(`HTTP ${res.status}`)
            const json = await res.json()
            const sessions: AgentSession[] = json.sessions || []
            const total: number = json.total ?? 0
            const pi: number = json.pageIndex ?? params.pageIndex
            const ps: number = json.pageSize ?? params.pageSize
            setData({ sessions, total, pageIndex: pi, pageSize: ps, totalPages: Math.max(1, Math.ceil(total / ps)) })
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load sessions')
        } finally {
            setIsLoading(false)
        }
    }, [userId, params.pageIndex, params.pageSize, params.search, params.agentId, params.type])

    useEffect(() => {
        if (params.enabled !== false) void fetchSessions()
    }, [fetchSessions, params.enabled])

    return { ...data, isLoading, error, refresh: fetchSessions }
}
