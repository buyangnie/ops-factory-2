import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import AnalyticsTableCard from '../../../platform/ui/primitives/AnalyticsTableCard'
import type {
    AgentUsage,
    DistributionItem,
    ModelUsage,
    PageResponse,
    SessionUsage,
    UserUsage,
} from '../../../../services/finopsAPI'
import {
    formatNumber,
    formatPercent,
    formatDate,
    sumBy,
    topShare,
    normalizeSessionTypes,
    chartColorSets,
    DimensionHeader,
    DataTable,
    PaginationControls,
} from './SharedComponents'

interface AgentsTabProps {
    topRows: AgentUsage[]
    page: PageResponse<AgentUsage> | null
    loading: boolean
    onPageChange: (page: number) => void
    onAgentSelect: (agent: AgentUsage) => void
    selectedAgent: AgentUsage | null
}

export function AgentsTab({
    topRows,
    page,
    loading,
    onPageChange,
    onAgentSelect,
    selectedAgent,
}: AgentsTabProps) {
    const { t } = useTranslation()
    const tableRows = page?.items ?? []
    const agentTokens = sumBy(topRows, row => row.totalTokens)
    const topThreeTokens = topRows.slice(0, 3).reduce((sum, row) => sum + row.totalTokens, 0)
    const metrics = [
        { label: t('finops.dimension.agentCount'), value: formatNumber(page?.totalItems ?? topRows.length), meta: t('finops.dimension.activeDimension') },
        { label: t('finops.dimension.top1Share'), value: formatPercent(topShare(topRows, row => row.totalTokens)), meta: topRows[0]?.agentId ?? '-' },
        { label: t('finops.dimension.avgPerAgent'), value: formatNumber(topRows.length ? agentTokens / topRows.length : 0), meta: t('finops.metrics.avgTokens') },
        { label: t('finops.dimension.userCoverage'), value: formatNumber(sumBy(topRows, row => row.activeUsers)), meta: t('finops.columns.users') },
    ]
    return (
        <DimensionTabLayout
            header={(
                <DimensionHeader
                    title={t('finops.analysis.agents')}
                    subtitle={t('finops.sections.agentTableSubtitle')}
                    metrics={metrics}
                    segments={[
                        { id: 'top3', label: t('finops.insights.top3'), value: topThreeTokens, color: chartColorSets.primary },
                        { id: 'other', label: t('finops.insights.other'), value: Math.max(0, agentTokens - topThreeTokens), color: chartColorSets.muted },
                    ]}
                />
            )}
            table={<AgentsTable rows={tableRows} page={page} loading={loading} onPageChange={onPageChange} onAgentSelect={onAgentSelect} selectedAgent={selectedAgent} />}
        />
    )
}

interface UsersTabProps {
    topRows: UserUsage[]
    page: PageResponse<UserUsage> | null
    loading: boolean
    totalTokens: number
    locale: string
    onPageChange: (page: number) => void
    onUserSelect: (user: UserUsage) => void
    selectedUser: UserUsage | null
}

export function UsersTab({
    topRows,
    page,
    loading,
    totalTokens,
    locale,
    onPageChange,
    onUserSelect,
    selectedUser,
}: UsersTabProps) {
    const { t } = useTranslation()
    const tableRows = page?.items ?? []
    const userTokens = sumBy(topRows, row => row.totalTokens)
    const multiAgentUsers = topRows.filter(row => row.activeAgents > 1).length
    const metrics = [
        { label: t('finops.metrics.activeUsers'), value: formatNumber(page?.totalItems ?? topRows.length), meta: t('finops.dimension.activeDimension') },
        { label: t('finops.dimension.top1Share'), value: formatPercent(totalTokens > 0 ? (topRows[0]?.totalTokens ?? 0) / totalTokens : 0), meta: topRows[0]?.userId ?? '-' },
        { label: t('finops.dimension.avgPerUser'), value: formatNumber(topRows.length ? userTokens / topRows.length : 0), meta: t('finops.metrics.avgTokens') },
        { label: t('finops.dimension.avgAgentsPerUser'), value: topRows.length ? (sumBy(topRows, row => row.activeAgents) / topRows.length).toFixed(1) : '0', meta: t('finops.columns.agents') },
    ]
    return (
        <DimensionTabLayout
            header={(
                <DimensionHeader
                    title={t('finops.analysis.users')}
                    subtitle={t('finops.sections.userTableSubtitle')}
                    metrics={metrics}
                    segments={[
                        { id: 'multi', label: t('finops.insights.multiAgentUsers'), value: multiAgentUsers, color: chartColorSets.primary },
                        { id: 'single', label: t('finops.insights.singleAgentUsers'), value: Math.max(0, topRows.length - multiAgentUsers), color: chartColorSets.muted },
                    ]}
                />
            )}
            table={<UsersTable rows={tableRows} page={page} loading={loading} locale={locale} onPageChange={onPageChange} onUserSelect={onUserSelect} selectedUser={selectedUser} />}
        />
    )
}

interface SessionsTabProps {
    topRows: SessionUsage[]
    page: PageResponse<SessionUsage> | null
    loading: boolean
    sessionTypes: DistributionItem[]
    inputTokens: number
    outputTokens: number
    locale: string
    onPageChange: (page: number) => void
    onSessionSelect: (session: SessionUsage) => void
    selectedSession: SessionUsage | null
}

export function SessionsTab({
    topRows,
    page,
    loading,
    sessionTypes,
    inputTokens,
    outputTokens,
    locale,
    onPageChange,
    onSessionSelect,
    selectedSession,
}: SessionsTabProps) {
    const { t } = useTranslation()
    const tableRows = page?.items ?? []
    const sessionTokens = sumBy(topRows, row => row.totalTokens)
    const normalizedSessionTypes = normalizeSessionTypes(sessionTypes, t)
    const metrics = [
        { label: t('finops.metrics.sessions'), value: formatNumber(page?.totalItems ?? topRows.length), meta: t('finops.dimension.topSessions') },
        { label: t('finops.dimension.avgPerSession'), value: formatNumber(topRows.length ? sessionTokens / topRows.length : 0), meta: t('finops.metrics.avgTokens') },
        { label: t('finops.dimension.maxSession'), value: formatNumber(topRows[0]?.totalTokens ?? 0), meta: topRows[0]?.label || '-' },
        { label: t('finops.dimension.inputShare'), value: formatPercent(inputTokens + outputTokens > 0 ? inputTokens / (inputTokens + outputTokens) : 0), meta: t('finops.metrics.inputTokens') },
    ]
    return (
        <DimensionTabLayout
            header={(
                <DimensionHeader
                    title={t('finops.analysis.sessions')}
                    subtitle={t('finops.sections.sessionTableSubtitle')}
                    metrics={metrics}
                    segments={normalizedSessionTypes.map((item, index) => ({
                        id: item.id,
                        label: item.label,
                        value: item.totalTokens,
                        color: chartColorSets.session[index % chartColorSets.session.length],
                    }))}
                />
            )}
            table={<SessionsTable rows={tableRows} page={page} loading={loading} locale={locale} onPageChange={onPageChange} onSessionSelect={onSessionSelect} selectedSession={selectedSession} />}
        />
    )
}

interface ModelsTabProps {
    topRows: ModelUsage[]
    page: PageResponse<ModelUsage> | null
    loading: boolean
    providers: DistributionItem[]
    onPageChange: (page: number) => void
}

export function ModelsTab({
    topRows,
    page,
    loading,
    providers,
    onPageChange,
}: ModelsTabProps) {
    const { t } = useTranslation()
    const tableRows = page?.items ?? []
    const modelTokens = sumBy(topRows, row => row.totalTokens)
    const providerCount = new Set(topRows.map(row => row.providerName)).size
    const metrics = [
        { label: t('finops.dimension.providerCount'), value: formatNumber(providerCount), meta: t('finops.columns.provider') },
        { label: t('finops.dimension.modelCount'), value: formatNumber(page?.totalItems ?? topRows.length), meta: t('finops.columns.model') },
        { label: t('finops.dimension.topProviderShare'), value: formatPercent(topShare(providers, item => item.totalTokens)), meta: providers[0]?.label ?? '-' },
        { label: t('finops.dimension.avgPerModel'), value: formatNumber(topRows.length ? modelTokens / topRows.length : 0), meta: t('finops.metrics.avgTokens') },
    ]
    return (
        <DimensionTabLayout
            header={(
                <DimensionHeader
                    title={t('finops.analysis.models')}
                    subtitle={t('finops.sections.modelTableSubtitle')}
                    metrics={metrics}
                    segments={providers.slice(0, 4).map((item, index) => ({
                        id: item.id,
                        label: item.label,
                        value: item.totalTokens,
                        color: chartColorSets.provider[index % chartColorSets.provider.length],
                    }))}
                />
            )}
            table={<ModelsTable rows={tableRows} page={page} loading={loading} onPageChange={onPageChange} />}
        />
    )
}

function DimensionTabLayout({
    header,
    table,
}: {
    header: ReactNode
    table: ReactNode
}) {
    return (
        <div className="finops-dimension-view">
            {header}
            {table}
        </div>
    )
}

function AgentsTable({
    rows,
    page,
    loading,
    onPageChange,
    onAgentSelect,
    selectedAgent,
}: {
    rows: AgentUsage[]
    page: PageResponse<AgentUsage> | null
    loading: boolean
    onPageChange: (page: number) => void
    onAgentSelect: (agent: AgentUsage) => void
    selectedAgent: AgentUsage | null
}) {
    const { t } = useTranslation()
    return (
        <AnalyticsTableCard title={t('finops.sections.agentTable')} subtitle={t('finops.sections.agentTableSubtitle')}>
            {loading ? <div className="finops-table-loading">{t('finops.loadingDetail')}</div> : null}
            <DataTable>
                <thead>
                    <tr>
                        <th>{t('finops.columns.agent')}</th>
                        <th>{t('finops.columns.users')}</th>
                        <th>{t('finops.columns.sessions')}</th>
                        <th>{t('finops.columns.tokens')}</th>
                        <th>{t('finops.columns.input')}</th>
                        <th>{t('finops.columns.output')}</th>
                        <th>{t('finops.columns.avgTokens')}</th>
                        <th>{t('finops.columns.scheduled')}</th>
                    </tr>
                </thead>
                <tbody>
                    {rows.map(row => (
                        <tr
                            key={row.agentId}
                            className={`finops-clickable-row ${selectedAgent?.agentId === row.agentId ? 'finops-row-selected' : ''}`}
                            onClick={() => onAgentSelect(row)}
                            tabIndex={0}
                            onKeyDown={(event) => {
                                if (event.key === 'Enter' || event.key === ' ') {
                                    event.preventDefault()
                                    onAgentSelect(row)
                                }
                            }}
                        >
                            <td className="finops-strong-cell">{row.agentId}</td>
                            <td>{row.activeUsers}</td>
                            <td>{row.sessionCount}</td>
                            <td>{formatNumber(row.totalTokens)}</td>
                            <td>{formatNumber(row.inputTokens)}</td>
                            <td>{formatNumber(row.outputTokens)}</td>
                            <td>{formatNumber(row.avgTokensPerSession)}</td>
                            <td>{row.scheduledSessionCount}</td>
                        </tr>
                    ))}
                </tbody>
            </DataTable>
            <PaginationControls page={page} onPageChange={onPageChange} />
        </AnalyticsTableCard>
    )
}

function UsersTable({
    rows,
    page,
    loading,
    locale,
    onPageChange,
    onUserSelect,
    selectedUser,
}: {
    rows: UserUsage[]
    page: PageResponse<UserUsage> | null
    loading: boolean
    locale: string
    onPageChange: (page: number) => void
    onUserSelect: (user: UserUsage) => void
    selectedUser: UserUsage | null
}) {
    const { t } = useTranslation()
    return (
        <AnalyticsTableCard title={t('finops.sections.userTable')} subtitle={t('finops.sections.userTableSubtitle')}>
            {loading ? <div className="finops-table-loading">{t('finops.loadingDetail')}</div> : null}
            <DataTable>
                <thead>
                    <tr>
                        <th>{t('finops.columns.user')}</th>
                        <th>{t('finops.columns.agents')}</th>
                        <th>{t('finops.columns.sessions')}</th>
                        <th>{t('finops.columns.tokens')}</th>
                        <th>{t('finops.columns.input')}</th>
                        <th>{t('finops.columns.output')}</th>
                        <th>{t('finops.columns.avgTokens')}</th>
                        <th>{t('finops.columns.lastActive')}</th>
                        <th>{t('finops.columns.topAgent')}</th>
                    </tr>
                </thead>
                <tbody>
                    {rows.map(row => (
                        <tr
                            key={row.userId}
                            className={`finops-clickable-row ${selectedUser?.userId === row.userId ? 'finops-row-selected' : ''}`}
                            onClick={() => onUserSelect(row)}
                            tabIndex={0}
                            onKeyDown={(event) => {
                                if (event.key === 'Enter' || event.key === ' ') {
                                    event.preventDefault()
                                    onUserSelect(row)
                                }
                            }}
                        >
                            <td className="finops-strong-cell">{row.userId}</td>
                            <td>{row.activeAgents}</td>
                            <td>{row.sessionCount}</td>
                            <td>{formatNumber(row.totalTokens)}</td>
                            <td>{formatNumber(row.inputTokens)}</td>
                            <td>{formatNumber(row.outputTokens)}</td>
                            <td>{formatNumber(row.avgTokensPerSession)}</td>
                            <td>{formatDate(row.lastActiveAt, locale)}</td>
                            <td>{row.topAgent || '-'}</td>
                        </tr>
                    ))}
                </tbody>
            </DataTable>
            <PaginationControls page={page} onPageChange={onPageChange} />
        </AnalyticsTableCard>
    )
}

function SessionsTable({
    rows,
    page,
    loading,
    locale,
    onPageChange,
    onSessionSelect,
    selectedSession,
}: {
    rows: SessionUsage[]
    page: PageResponse<SessionUsage> | null
    loading: boolean
    locale: string
    onPageChange: (page: number) => void
    onSessionSelect: (session: SessionUsage) => void
    selectedSession: SessionUsage | null
}) {
    const { t } = useTranslation()
    return (
        <AnalyticsTableCard title={t('finops.sections.sessionTable')} subtitle={t('finops.sections.sessionTableSubtitle')}>
            {loading ? <div className="finops-table-loading">{t('finops.loadingDetail')}</div> : null}
            <DataTable>
                <thead>
                    <tr>
                        <th>{t('finops.columns.session')}</th>
                        <th>{t('finops.columns.user')}</th>
                        <th>{t('finops.columns.agent')}</th>
                        <th>{t('finops.columns.type')}</th>
                        <th>{t('finops.columns.provider')}</th>
                        <th>{t('finops.columns.model')}</th>
                        <th>{t('finops.columns.tokens')}</th>
                        <th>{t('finops.columns.input')}</th>
                        <th>{t('finops.columns.output')}</th>
                        <th>{t('finops.columns.messages')}</th>
                        <th>{t('finops.columns.updated')}</th>
                    </tr>
                </thead>
                <tbody>
                    {rows.map(row => (
                        <tr
                            key={`${row.userId}:${row.agentId}:${row.id}`}
                            className={`finops-clickable-row ${selectedSession?.id === row.id && selectedSession.userId === row.userId && selectedSession.agentId === row.agentId ? 'finops-row-selected' : ''}`}
                            onClick={() => onSessionSelect(row)}
                            tabIndex={0}
                            onKeyDown={(event) => {
                                if (event.key === 'Enter' || event.key === ' ') {
                                    event.preventDefault()
                                    onSessionSelect(row)
                                }
                            }}
                        >
                            <td className="finops-session-cell" title={row.label || row.id}>{row.label || row.id}</td>
                            <td>{row.userId}</td>
                            <td>{row.agentId}</td>
                            <td>{row.sessionType || '-'}</td>
                            <td>{row.providerName || '-'}</td>
                            <td>{row.modelName || '-'}</td>
                            <td>{formatNumber(row.totalTokens)}</td>
                            <td>{formatNumber(row.inputTokens)}</td>
                            <td>{formatNumber(row.outputTokens)}</td>
                            <td>{row.messageCount}</td>
                            <td>{formatDate(row.updatedAt, locale)}</td>
                        </tr>
                    ))}
                </tbody>
            </DataTable>
            <PaginationControls page={page} onPageChange={onPageChange} />
        </AnalyticsTableCard>
    )
}

function ModelsTable({
    rows,
    page,
    loading,
    onPageChange,
}: {
    rows: ModelUsage[]
    page: PageResponse<ModelUsage> | null
    loading: boolean
    onPageChange: (page: number) => void
}) {
    const { t } = useTranslation()
    return (
        <AnalyticsTableCard title={t('finops.sections.modelTable')} subtitle={t('finops.sections.modelTableSubtitle')}>
            {loading ? <div className="finops-table-loading">{t('finops.loadingDetail')}</div> : null}
            <DataTable>
                <thead>
                    <tr>
                        <th>{t('finops.columns.provider')}</th>
                        <th>{t('finops.columns.model')}</th>
                        <th>{t('finops.columns.users')}</th>
                        <th>{t('finops.columns.agents')}</th>
                        <th>{t('finops.columns.sessions')}</th>
                        <th>{t('finops.columns.tokens')}</th>
                        <th>{t('finops.columns.input')}</th>
                        <th>{t('finops.columns.output')}</th>
                        <th>{t('finops.columns.avgTokens')}</th>
                    </tr>
                </thead>
                <tbody>
                    {rows.map(row => (
                        <tr key={`${row.providerName}:${row.modelName}`}>
                            <td>{row.providerName}</td>
                            <td className="finops-strong-cell">{row.modelName}</td>
                            <td>{row.activeUsers}</td>
                            <td>{row.activeAgents}</td>
                            <td>{row.sessionCount}</td>
                            <td>{formatNumber(row.totalTokens)}</td>
                            <td>{formatNumber(row.inputTokens)}</td>
                            <td>{formatNumber(row.outputTokens)}</td>
                            <td>{formatNumber(row.avgTokensPerSession)}</td>
                        </tr>
                    ))}
                </tbody>
            </DataTable>
            <PaginationControls page={page} onPageChange={onPageChange} />
        </AnalyticsTableCard>
    )
}
