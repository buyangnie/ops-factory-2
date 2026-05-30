import { useCallback, useEffect, useMemo, useState } from 'react'
import { RefreshCw, Coins, CornerDownRight, CornerUpRight, MessageSquare, Users, Bot } from '../../../platform/ui/icons/AppIcons'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../platform/ui/primitives/PageHeader'
import Button from '../../../platform/ui/primitives/Button'
import StatCard from '../../../platform/ui/primitives/StatCard'
import ListToolbar from '../../../platform/ui/list/ListToolbar'
import ListWorkbench from '../../../platform/ui/list/ListWorkbench'
import ListResultsMeta from '../../../platform/ui/list/ListResultsMeta'
import { useFinOps } from '../hooks/useFinOps'
import { useScrollClass } from '../hooks/useScrollClass'
import {
    fetchFinOpsAgents,
    fetchFinOpsModels,
    fetchFinOpsSessionMessages,
    fetchFinOpsSessions,
    type SessionUsage,
    type AgentUsage,
    type UserUsage,
    type SessionMessagesResponse,
    fetchFinOpsUsers,
} from '../../../../services/finopsAPI'

import {
    TabId,
    DetailTabId,
    DetailPages,
    DetailData,
    DetailPageResponse,
    PAGE_SIZE,
    formatNumber,
    formatPercent,
    formatGrowth,
    PeriodSummary,
} from '../components/SharedComponents'
import { OverviewTab } from '../components/OverviewTab'
import { AgentsTab, UsersTab, SessionsTab, ModelsTab } from '../components/DimensionTabs'
import { AgentDetailDrawer, UserDetailDrawer } from '../components/DetailDrawers'
import { SessionMessagesDrawer } from '../components/SessionMessagesDrawer'
import '../styles/finops.css'
import '../styles/finops-drawers.css'
import '../styles/finops-overview.css'

const tabs: TabId[] = ['overview', 'agents', 'users', 'sessions', 'models']

export default function FinOpsPage() {
    const { t, i18n } = useTranslation()
    const { data, loading, refreshing, error, refresh } = useFinOps()
    const [activeTab, setActiveTab] = useState<TabId>('overview')
    const [detailPages, setDetailPages] = useState<DetailPages>({ agents: 1, users: 1, sessions: 1, models: 1 })
    const [detailData, setDetailData] = useState<DetailData>({ agents: null, users: null, sessions: null, models: null })
    const [detailLoading, setDetailLoading] = useState(false)
    const [detailError, setDetailError] = useState<string | null>(null)
    const [detailReloadKey, setDetailReloadKey] = useState(0)
    const [selectedSession, setSelectedSession] = useState<SessionUsage | null>(null)
    const [selectedAgent, setSelectedAgent] = useState<AgentUsage | null>(null)
    const [selectedUser, setSelectedUser] = useState<UserUsage | null>(null)
    const [sessionMessages, setSessionMessages] = useState<SessionMessagesResponse | null>(null)
    const [sessionMessagesLoading, setSessionMessagesLoading] = useState(false)
    const [sessionMessagesError, setSessionMessagesError] = useState<string | null>(null)
    const locale = i18n.language?.startsWith('zh') ? 'zh-CN' : 'en-US'
    const bodyTarget = useCallback(() => document.body, [])
    const windowTarget = useCallback(() => window, [])

    const stats = data?.summary.current
    const topTrend = useMemo(() => data?.tokenTrend.slice(-14) ?? [], [data])

    function handleTabChange(tab: TabId) {
        setActiveTab(tab)
    }

    function setDetailPage(tab: DetailTabId, page: number) {
        setDetailPages(prev => ({ ...prev, [tab]: page }))
    }

    async function handleRefresh() {
        await refresh()
        setDetailReloadKey(value => value + 1)
        setSelectedSession(null)
        setSelectedAgent(null)
        setSelectedUser(null)
    }

    useEffect(() => {
        if (activeTab === 'overview') return
        let cancelled = false
        const page = detailPages[activeTab]

        async function loadDetailPage() {
            setDetailLoading(true)
            setDetailError(null)
            try {
                const response = await fetchDetailPage(activeTab as DetailTabId, page)
                if (!cancelled) {
                    setDetailData(prev => ({ ...prev, [activeTab]: response }))
                }
            } catch (err) {
                if (!cancelled) {
                    setDetailError(err instanceof Error ? err.message : String(err))
                }
            } finally {
                if (!cancelled) {
                    setDetailLoading(false)
                }
            }
        }

        void loadDetailPage()
        return () => {
            cancelled = true
        }
    }, [activeTab, detailPages, detailReloadKey])

    useEffect(() => {
        if (!selectedSession) {
            setSessionMessages(null)
            return
        }
        const currentSession = selectedSession
        let cancelled = false
        async function loadSessionMessages() {
            setSessionMessagesLoading(true)
            setSessionMessagesError(null)
            try {
                const response = await fetchFinOpsSessionMessages(currentSession)
                if (!cancelled) {
                    setSessionMessages(response)
                }
            } catch (err) {
                if (!cancelled) {
                    setSessionMessagesError(err instanceof Error ? err.message : String(err))
                }
            } finally {
                if (!cancelled) {
                    setSessionMessagesLoading(false)
                }
            }
        }
        void loadSessionMessages()
        return () => {
            cancelled = true
        }
    }, [selectedSession])

    useEffect(() => {
        if (selectedSession || selectedAgent || selectedUser) {
            document.body.style.overflow = 'hidden'
        } else {
            document.body.style.overflow = ''
        }
        return () => {
            document.body.style.overflow = ''
        }
    }, [selectedSession, selectedAgent, selectedUser])

    useEffect(() => {
        document.body.classList.add('finops-page-active')
        return () => {
            document.body.classList.remove('finops-page-active')
        }
    }, [])
    useScrollClass(bodyTarget, 'is-scrolling', [], windowTarget)

    useEffect(() => {
        if (activeTab !== 'sessions' && activeTab !== 'agents' && activeTab !== 'users') {
            setSelectedSession(null)
        }
        if (activeTab !== 'agents') {
            setSelectedAgent(null)
        }
        if (activeTab !== 'users') {
            setSelectedUser(null)
        }
    }, [activeTab])

    function getResultCount(tab: TabId, detailData: DetailData): number {
        if (tab === 'overview') return 0
        return detailData[tab as Exclude<TabId, 'overview'>]?.totalItems ?? 0
    }

    function fetchDetailPage(tab: DetailTabId, page: number): Promise<DetailPageResponse> {
        if (tab === 'agents') return fetchFinOpsAgents(page, PAGE_SIZE)
        if (tab === 'users') return fetchFinOpsUsers(page, PAGE_SIZE)
        if (tab === 'sessions') return fetchFinOpsSessions(page, PAGE_SIZE)
        return fetchFinOpsModels(page, PAGE_SIZE)
    }

    return (
        <div className="page-container sidebar-top-page page-shell-wide finops-page">
            <PageHeader
                title={t('finops.title')}
                subtitle={t('finops.subtitle')}
                action={(
                    <div className="finops-header-actions">
                        <PeriodSummary data={data} locale={locale} />
                        <Button
                            variant="secondary"
                            size="sm"
                            leadingIcon={<RefreshCw size={15} />}
                            onClick={() => void handleRefresh()}
                            disabled={refreshing}
                            className={refreshing ? 'finops-refresh-spinning' : undefined}
                        >
                            {refreshing ? t('finops.refreshing') : t('finops.refresh')}
                        </Button>
                    </div>
                )}
            />

            <ListWorkbench
                controls={(
                    <ListToolbar
                        primary={(
                            <div className="config-tabs finops-tabs" role="tablist" aria-label={t('finops.tabsLabel')}>
                                {tabs.map(tab => (
                                    <button
                                        key={tab}
                                        type="button"
                                        role="tab"
                                        aria-selected={activeTab === tab}
                                        className={`config-tab ${activeTab === tab ? 'config-tab-active' : ''}`}
                                        onClick={() => handleTabChange(tab)}
                                    >
                                        {t(`finops.tabs.${tab}`)}
                                    </button>
                                ))}
                            </div>
                        )}
                    />
                )}
            >
                {error ? <div className="conn-banner conn-banner-error">{t('finops.loadFailed', { error })}</div> : null}
                {loading ? <div className="empty-state"><div className="empty-state-title">{t('finops.loading')}</div></div> : null}

                {!loading && data ? (
                    <div className="finops-workbench">
                        <div className="finops-tab-content" key={activeTab}>
                            {activeTab === 'overview' ? (
                                <section className="ui-metric-grid finops-metrics">
                                    <StatCard
                                        label={t('finops.metrics.totalTokens')}
                                        value={formatNumber(stats?.totalTokens)}
                                        meta={formatGrowth(data.summary.tokenGrowthRate)}
                                        icon={<Coins size={16} />}
                                        className="finops-stat-total-tokens"
                                    />
                                    <StatCard
                                        label={t('finops.metrics.inputTokens')}
                                        value={formatNumber(stats?.inputTokens)}
                                        meta={stats?.totalTokens && stats.totalTokens > 0 ? `${t('finops.columns.input')} ${formatPercent(stats.inputTokens / stats.totalTokens)}` : undefined}
                                        icon={<CornerDownRight size={16} />}
                                        className="finops-stat-input-tokens"
                                    />
                                    <StatCard
                                        label={t('finops.metrics.outputTokens')}
                                        value={formatNumber(stats?.outputTokens)}
                                        meta={stats?.totalTokens && stats.totalTokens > 0 ? `${t('finops.columns.output')} ${formatPercent(stats.outputTokens / stats.totalTokens)}` : undefined}
                                        icon={<CornerUpRight size={16} />}
                                        className="finops-stat-output-tokens"
                                    />
                                    <StatCard
                                        label={t('finops.metrics.sessions')}
                                        value={formatNumber(stats?.sessionCount)}
                                        meta={stats?.sessionCount && stats?.totalTokens && stats.sessionCount > 0 ? `${t('finops.dimension.avgPerSession')}: ${formatNumber(stats.totalTokens / stats.sessionCount)}` : undefined}
                                        icon={<MessageSquare size={16} />}
                                        className="finops-stat-sessions"
                                    />
                                    <StatCard
                                        label={t('finops.metrics.activeUsers')}
                                        value={formatNumber(stats?.activeUsers)}
                                        meta={stats?.activeUsers && stats?.totalTokens && stats.activeUsers > 0 ? `${t('finops.dimension.avgPerUser')}: ${formatNumber(stats.totalTokens / stats.activeUsers)}` : undefined}
                                        icon={<Users size={16} />}
                                        className="finops-stat-active-users"
                                    />
                                    <StatCard
                                        label={t('finops.metrics.activeAgents')}
                                        value={formatNumber(stats?.activeAgents)}
                                        meta={stats?.activeAgents && stats?.totalTokens && stats.activeAgents > 0 ? `${t('finops.dimension.avgPerAgent')}: ${formatNumber(stats.totalTokens / stats.activeAgents)}` : undefined}
                                        icon={<Bot size={16} />}
                                        className="finops-stat-active-agents"
                                    />
                                </section>
                            ) : null}

                            <div className="finops-content">
                                <div className="finops-main">
                                    {activeTab === 'overview' ? (
                                        <OverviewTab
                                            trend={topTrend}
                                            sessionTypes={data.sessionTypeDistribution}
                                            models={data.models}
                                            taskExecutionLoad={data.taskExecutionLoad}
                                            totalTokens={stats?.totalTokens ?? 0}
                                            topUsers={data.topUsers}
                                        />
                                    ) : null}
                                    {detailError ? <div className="conn-banner conn-banner-error">{t('finops.loadFailed', { error: detailError })}</div> : null}
                                    {activeTab === 'agents' ? (
                                        <AgentsTab
                                            topRows={data.topAgents}
                                            page={detailData.agents}
                                            loading={detailLoading}
                                            onPageChange={page => setDetailPage('agents', page)}
                                            onAgentSelect={setSelectedAgent}
                                            selectedAgent={selectedAgent}
                                        />
                                    ) : null}
                                    {activeTab === 'users' ? (
                                        <UsersTab
                                            topRows={data.topUsers}
                                            page={detailData.users}
                                            loading={detailLoading}
                                            totalTokens={stats?.totalTokens ?? 0}
                                            locale={locale}
                                            onPageChange={page => setDetailPage('users', page)}
                                            onUserSelect={setSelectedUser}
                                            selectedUser={selectedUser}
                                        />
                                    ) : null}
                                    {activeTab === 'sessions' ? (
                                        <SessionsTab
                                            topRows={data.topSessions}
                                            page={detailData.sessions}
                                            loading={detailLoading}
                                            sessionTypes={data.sessionTypeDistribution}
                                            inputTokens={stats?.inputTokens ?? 0}
                                            outputTokens={stats?.outputTokens ?? 0}
                                            locale={locale}
                                            onPageChange={page => setDetailPage('sessions', page)}
                                            onSessionSelect={setSelectedSession}
                                            selectedSession={selectedSession}
                                        />
                                    ) : null}
                                    {activeTab === 'models' ? (
                                        <ModelsTab
                                            topRows={data.models}
                                            page={detailData.models}
                                            loading={detailLoading}
                                            providers={data.providerDistribution}
                                            onPageChange={page => setDetailPage('models', page)}
                                        />
                                    ) : null}
                                </div>
                            </div>

                            {activeTab !== 'overview' ? (
                                <ListResultsMeta>
                                    {t('common.resultsFound', { count: getResultCount(activeTab, detailData) })}
                                </ListResultsMeta>
                            ) : null}
                        </div>
                    </div>
                ) : null}
            </ListWorkbench>
            {selectedSession ? (
                <SessionMessagesDrawer
                    session={selectedSession}
                    data={sessionMessages}
                    loading={sessionMessagesLoading}
                    error={sessionMessagesError}
                    locale={locale}
                    onClose={() => setSelectedSession(null)}
                />
            ) : null}
            {selectedAgent ? (
                <AgentDetailDrawer
                    agent={selectedAgent}
                    locale={locale}
                    onClose={() => setSelectedAgent(null)}
                    onSessionSelect={setSelectedSession}
                />
            ) : null}
            {selectedUser ? (
                <UserDetailDrawer
                    user={selectedUser}
                    locale={locale}
                    onClose={() => setSelectedUser(null)}
                    onSessionSelect={setSelectedSession}
                />
            ) : null}
        </div>
    )
}
