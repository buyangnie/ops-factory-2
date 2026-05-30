import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { X, AlertTriangle } from '../../../platform/ui/icons/AppIcons'
import {
    fetchFinOpsAgentSessions,
    fetchFinOpsUserSessions,
    type SessionUsage,
    type AgentUsage,
    type UserUsage,
    type PageResponse,
} from '../../../../services/finopsAPI'
import { useScrollClass } from '../hooks/useScrollClass'
import {
    formatNumber,
    formatDate,
    DataTable,
    PaginationControls,
    MiniMetric,
    SplitBar,
} from './SharedComponents'

interface AgentDetailDrawerProps {
    agent: AgentUsage
    locale: string
    onClose: () => void
    onSessionSelect: (session: SessionUsage) => void
}

export function AgentDetailDrawer({
    agent,
    locale,
    onClose,
    onSessionSelect,
}: AgentDetailDrawerProps) {
    const { t } = useTranslation()
    const [page, setPage] = useState(1)
    const [data, setData] = useState<PageResponse<SessionUsage> | null>(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [sessionFilter, setSessionFilter] = useState<'all' | 'user' | 'scheduled'>('all')

    const bodyRef = useRef<HTMLDivElement>(null)
    useScrollClass(bodyRef)

    useEffect(() => {
        let cancelled = false
        async function loadAgentSessions() {
            setLoading(true)
            setError(null)
            try {
                const res = await fetchFinOpsAgentSessions(agent.agentId, page, 25)
                if (!cancelled) {
                    setData(res)
                }
            } catch (err) {
                if (!cancelled) {
                    setError(err instanceof Error ? err.message : String(err))
                }
            } finally {
                if (!cancelled) {
                    setLoading(false)
                }
            }
        }
        void loadAgentSessions()
        return () => {
            cancelled = true
        }
    }, [agent.agentId, page])

    const sessions = data?.items ?? []

    const filteredSessions = useMemo(() => {
        return sessions.filter(session => {
            if (sessionFilter === 'all') return true
            if (sessionFilter === 'scheduled') return session.sessionType === 'scheduled'
            return session.sessionType !== 'scheduled'
        })
    }, [sessions, sessionFilter])

    const manualCount = Math.max(0, agent.sessionCount - agent.scheduledSessionCount)
    const runSegments = [
        { id: 'manual', label: t('finops.sessionTypes.user'), value: manualCount, color: 'var(--chart-1)' },
        { id: 'scheduled', label: t('finops.sessionTypes.scheduled'), value: agent.scheduledSessionCount, color: 'var(--chart-3)' }
    ]

    const tokenSegments = [
        { id: 'input', label: t('finops.metrics.inputTokens'), value: agent.inputTokens, color: 'var(--color-accent)' },
        { id: 'output', label: t('finops.metrics.outputTokens'), value: agent.outputTokens, color: 'var(--chart-2)' }
    ]

    return (
        <div className="finops-drawer-backdrop" onClick={onClose}>
            <aside className="finops-session-drawer" role="dialog" aria-modal="true" aria-label={t('finops.drawer.agentTitle')} onClick={event => event.stopPropagation()}>
                <header className="finops-drawer-header">
                    <div>
                        <h2>{agent.agentId}</h2>
                        <span>{t('finops.drawer.agentEyebrow')}</span>
                    </div>
                    <button type="button" className="finops-drawer-close" onClick={onClose} aria-label={t('common.close')}>
                        <X size={18} />
                    </button>
                </header>

                <div ref={bodyRef} className="finops-drawer-body">
                    <section className="finops-drawer-meta" aria-label={t('finops.drawer.agentMeta')}>
                        <span><b>{t('finops.columns.users')}</b>{agent.activeUsers}</span>
                        <span><b>{t('finops.columns.sessions')}</b>{agent.sessionCount}</span>
                        <span><b>{t('finops.columns.scheduled')}</b>{agent.scheduledSessionCount}</span>
                        <span><b>{t('finops.metrics.avgTokens')}</b>{formatNumber(agent.avgTokensPerSession)}</span>
                    </section>

                    <section className="finops-drawer-metrics">
                        <MiniMetric label={t('finops.metrics.totalTokens')} value={formatNumber(agent.totalTokens)} />
                        <MiniMetric label={t('finops.metrics.inputTokens')} value={formatNumber(agent.inputTokens)} />
                        <MiniMetric label={t('finops.metrics.outputTokens')} value={formatNumber(agent.outputTokens)} />
                        <MiniMetric label={t('finops.columns.sessions')} value={String(agent.highTokenSessionCount)} />
                    </section>

                    <section className="finops-drawer-insights" aria-label={t('finops.drawer.agentMeta')}>
                        <div className="finops-insight-card finops-insight-card-half">
                            <span className="finops-insight-card-title">{t('finops.drawer.sessionSplit')}</span>
                            <SplitBar segments={runSegments} />
                        </div>
                        <div className="finops-insight-card finops-insight-card-half">
                            <span className="finops-insight-card-title">{t('finops.sections.tokenComposition')}</span>
                            <SplitBar segments={tokenSegments} />
                        </div>
                    </section>

                    <section className="finops-role-filter" role="tablist" aria-label={t('finops.drawer.associatedSessions')}>
                        <span className="finops-role-filter-title">
                            {t('finops.drawer.associatedSessions')}
                        </span>
                        {(['all', 'user', 'scheduled'] as const).map(filter => (
                            <button
                                key={filter}
                                type="button"
                                className={`finops-role-filter-all finops-role-filter-compact ${sessionFilter === filter ? 'active' : ''}`}
                                onClick={() => setSessionFilter(filter)}
                            >
                                <span>{filter === 'all' ? t('finops.drawer.roles.all') : (filter === 'scheduled' ? t('finops.sessionTypes.scheduled') : t('finops.sessionTypes.user'))}</span>
                            </button>
                        ))}
                    </section>

                    {error ? <div className="conn-banner conn-banner-error">{t('finops.loadFailed', { error })}</div> : null}
                    {loading ? <div className="finops-table-loading">{t('finops.drawer.loadingAgentSessions')}</div> : null}

                    {!loading && data ? (
                        <div className="finops-session-table-shell">
                            <DataTable>
                                <thead>
                                    <tr>
                                        <th>{t('finops.columns.session')}</th>
                                        <th>{t('finops.columns.user')}</th>
                                        <th>{t('finops.columns.tokens')}</th>
                                        <th>{t('finops.columns.messages')}</th>
                                        <th>{t('finops.columns.updated')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredSessions.length === 0 ? (
                                        <tr>
                                            <td colSpan={5} className="finops-table-empty-cell">
                                                {t('common.noResults')}
                                            </td>
                                        </tr>
                                    ) : null}
                                    {filteredSessions.map(row => (
                                        <tr
                                            key={row.id}
                                            className="finops-clickable-row"
                                            onClick={() => onSessionSelect(row)}
                                        >
                                            <td className="finops-session-cell finops-session-cell-compact" title={row.label || row.id}>
                                                {row.label || row.id}
                                            </td>
                                            <td>{row.userId}</td>
                                            <td className={row.totalTokens > 50000 ? 'finops-high-token-cell' : undefined}>
                                                {formatNumber(row.totalTokens)}
                                                {row.totalTokens > 50000 ? (
                                                    <span className="finops-high-token-badge">
                                                        <AlertTriangle size={10} />
                                                    </span>
                                                ) : null}
                                            </td>
                                            <td>{row.messageCount}</td>
                                            <td>{formatDate(row.updatedAt, locale)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </DataTable>
                            <PaginationControls page={data} onPageChange={setPage} />
                        </div>
                    ) : null}
                </div>
            </aside>
        </div>
    )
}

interface UserDetailDrawerProps {
    user: UserUsage
    locale: string
    onClose: () => void
    onSessionSelect: (session: SessionUsage) => void
}

export function UserDetailDrawer({
    user,
    locale,
    onClose,
    onSessionSelect,
}: UserDetailDrawerProps) {
    const { t } = useTranslation()
    const [page, setPage] = useState(1)
    const [data, setData] = useState<PageResponse<SessionUsage> | null>(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [sessionFilter, setSessionFilter] = useState<'all' | 'user' | 'scheduled'>('all')

    const bodyRef = useRef<HTMLDivElement>(null)
    useScrollClass(bodyRef)

    useEffect(() => {
        let cancelled = false
        async function loadUserSessions() {
            setLoading(true)
            setError(null)
            try {
                const res = await fetchFinOpsUserSessions(user.userId, page, 25)
                if (!cancelled) {
                    setData(res)
                }
            } catch (err) {
                if (!cancelled) {
                    setError(err instanceof Error ? err.message : String(err))
                }
            } finally {
                if (!cancelled) {
                    setLoading(false)
                }
            }
        }
        void loadUserSessions()
        return () => {
            cancelled = true
        }
    }, [user.userId, page])

    const sessions = data?.items ?? []

    const filteredSessions = useMemo(() => {
        return sessions.filter(session => {
            if (sessionFilter === 'all') return true
            if (sessionFilter === 'scheduled') return session.sessionType === 'scheduled'
            return session.sessionType !== 'scheduled'
        })
    }, [sessions, sessionFilter])

    const tokenSegments = [
        { id: 'input', label: t('finops.metrics.inputTokens'), value: user.inputTokens, color: 'var(--color-accent)' },
        { id: 'output', label: t('finops.metrics.outputTokens'), value: user.outputTokens, color: 'var(--chart-2)' }
    ]

    return (
        <div className="finops-drawer-backdrop" onClick={onClose}>
            <aside className="finops-session-drawer" role="dialog" aria-modal="true" aria-label={t('finops.drawer.userTitle')} onClick={event => event.stopPropagation()}>
                <header className="finops-drawer-header">
                    <div>
                        <h2>{user.userId}</h2>
                        <span>{t('finops.drawer.userEyebrow')}</span>
                    </div>
                    <button type="button" className="finops-drawer-close" onClick={onClose} aria-label={t('common.close')}>
                        <X size={18} />
                    </button>
                </header>

                <div ref={bodyRef} className="finops-drawer-body">
                    <section className="finops-drawer-meta" aria-label={t('finops.drawer.userMeta')}>
                        <span><b>{t('finops.columns.agents')}</b>{user.activeAgents}</span>
                        <span><b>{t('finops.columns.sessions')}</b>{user.sessionCount}</span>
                        <span><b>{t('finops.columns.topAgent')}</b>{user.topAgent || '-'}</span>
                        <span><b>{t('finops.columns.lastActive')}</b>{formatDate(user.lastActiveAt, locale)}</span>
                    </section>

                    <section className="finops-drawer-metrics">
                        <MiniMetric label={t('finops.metrics.totalTokens')} value={formatNumber(user.totalTokens)} />
                        <MiniMetric label={t('finops.metrics.inputTokens')} value={formatNumber(user.inputTokens)} />
                        <MiniMetric label={t('finops.metrics.outputTokens')} value={formatNumber(user.outputTokens)} />
                        <MiniMetric label={t('finops.metrics.avgTokens')} value={formatNumber(user.avgTokensPerSession)} />
                    </section>

                    <section className="finops-drawer-insights" aria-label={t('finops.drawer.userMeta')}>
                        <div className="finops-insight-card finops-insight-card-full">
                            <span className="finops-insight-card-title">{t('finops.sections.tokenComposition')}</span>
                            <SplitBar segments={tokenSegments} />
                        </div>
                    </section>

                    <section className="finops-role-filter" role="tablist" aria-label={t('finops.drawer.associatedSessions')}>
                        <span className="finops-role-filter-title">
                            {t('finops.drawer.associatedSessions')}
                        </span>
                        {(['all', 'user', 'scheduled'] as const).map(filter => (
                            <button
                                key={filter}
                                type="button"
                                className={`finops-role-filter-all finops-role-filter-compact ${sessionFilter === filter ? 'active' : ''}`}
                                onClick={() => setSessionFilter(filter)}
                            >
                                <span>{filter === 'all' ? t('finops.drawer.roles.all') : (filter === 'scheduled' ? t('finops.sessionTypes.scheduled') : t('finops.sessionTypes.user'))}</span>
                            </button>
                        ))}
                    </section>

                    {error ? <div className="conn-banner conn-banner-error">{t('finops.loadFailed', { error })}</div> : null}
                    {loading ? <div className="finops-table-loading">{t('finops.drawer.loadingUserSessions')}</div> : null}

                    {!loading && data ? (
                        <div className="finops-session-table-shell">
                            <DataTable>
                                <thead>
                                    <tr>
                                        <th>{t('finops.columns.session')}</th>
                                        <th>{t('finops.columns.agent')}</th>
                                        <th>{t('finops.columns.tokens')}</th>
                                        <th>{t('finops.columns.messages')}</th>
                                        <th>{t('finops.columns.updated')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredSessions.length === 0 ? (
                                        <tr>
                                            <td colSpan={5} className="finops-table-empty-cell">
                                                {t('common.noResults')}
                                            </td>
                                        </tr>
                                    ) : null}
                                    {filteredSessions.map(row => (
                                        <tr
                                            key={row.id}
                                            className="finops-clickable-row"
                                            onClick={() => onSessionSelect(row)}
                                        >
                                            <td className="finops-session-cell finops-session-cell-compact" title={row.label || row.id}>
                                                {row.label || row.id}
                                            </td>
                                            <td>{row.agentId}</td>
                                            <td className={row.totalTokens > 50000 ? 'finops-high-token-cell' : undefined}>
                                                {formatNumber(row.totalTokens)}
                                                {row.totalTokens > 50000 ? (
                                                    <span className="finops-high-token-badge">
                                                        <AlertTriangle size={10} />
                                                    </span>
                                                ) : null}
                                            </td>
                                            <td>{row.messageCount}</td>
                                            <td>{formatDate(row.updatedAt, locale)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </DataTable>
                            <PaginationControls page={data} onPageChange={setPage} />
                        </div>
                    ) : null}
                </div>
            </aside>
        </div>
    )
}
