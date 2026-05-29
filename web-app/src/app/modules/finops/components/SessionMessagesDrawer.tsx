import { useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { X, Clock, Zap, AlertTriangle, BarChart2 } from 'lucide-react'
import {
    type SessionUsage,
    type SessionMessagesResponse,
    type SessionMessageDetail,
} from '../../../../services/finopsAPI'
import { useScrollClass } from '../hooks/useScrollClass'
import {
    formatNumber,
    formatPercent,
    formatDate,
    formatMessageContent,
    roleLabel,
    MiniMetric,
    InsightLine,
} from './SharedComponents'

type MessageRoleFilter = 'all' | 'user' | 'assistant' | 'tool'

interface SessionMessagesDrawerProps {
    session: SessionUsage
    data: SessionMessagesResponse | null
    loading: boolean
    error: string | null
    locale: string
    onClose: () => void
}

export function SessionMessagesDrawer({
    session,
    data,
    loading,
    error,
    locale,
    onClose,
}: SessionMessagesDrawerProps) {
    const { t } = useTranslation()
    const [roleFilter, setRoleFilter] = useState<MessageRoleFilter>('all')
    const [expandedRows, setExpandedRows] = useState<Set<string>>(() => new Set())
    const bodyRef = useRef<HTMLDivElement>(null)
    useScrollClass(bodyRef)

    const messagesWithLatency = useMemo(() => {
        const rawMessages = data?.messages ?? []
        return rawMessages.map((msg, index) => {
            let durationSeconds: number | null = null
            const shouldShowLatency = index > 0 && !isUserMessage(msg)
            if (shouldShowLatency) {
                const currentMs = messageTimestampMs(msg)
                const prevMs = messageTimestampMs(rawMessages[index - 1])
                const diff = (currentMs - prevMs) / 1000
                if (Number.isFinite(diff) && diff >= 0 && diff < 3600) {
                    durationSeconds = diff
                }
            }
            return {
                ...msg,
                durationSeconds,
            }
        })
    }, [data])

    const messages = data?.messages ?? []
    const roleCounts: Record<MessageRoleFilter, number> = {
        all: messages.length,
        user: messages.filter(message => !message.toolRequest && !message.toolResponse && message.role === 'user').length,
        assistant: messages.filter(message => !message.toolRequest && !message.toolResponse && message.role === 'assistant').length,
        tool: messages.filter(message => message.toolRequest || message.toolResponse).length,
    }
    const filteredMessages = messagesWithLatency.filter(message => {
        if (roleFilter === 'all') return true
        if (roleFilter === 'tool') return message.toolRequest || message.toolResponse
        return !message.toolRequest && !message.toolResponse && message.role === roleFilter
    })
    const largestLabel = data?.stats.largestContentPreview || '-'
    const inputOutputTotal = session.inputTokens + session.outputTokens
    const inputShare = inputOutputTotal > 0 ? session.inputTokens / inputOutputTotal : 0

    function toggleMessage(rowKey: string) {
        setExpandedRows(prev => {
            const next = new Set(prev)
            if (next.has(rowKey)) {
                next.delete(rowKey)
            } else {
                next.add(rowKey)
            }
            return next
        })
    }

    return (
        <div className="finops-drawer-backdrop finops-drawer-backdrop-priority" onClick={onClose}>
            <aside className="finops-session-drawer" role="dialog" aria-modal="true" aria-label={t('finops.drawer.title')} onClick={event => event.stopPropagation()}>
                <header className="finops-drawer-header">
                    <div>
                        <h2 title={session.label || session.id}>{session.label || session.id}</h2>
                        <span>{t('finops.drawer.eyebrow')}</span>
                    </div>
                    <button type="button" className="finops-drawer-close" onClick={onClose} aria-label={t('common.close')}>
                        <X size={18} />
                    </button>
                </header>

                <div ref={bodyRef} className="finops-drawer-body">
                    <section className="finops-drawer-meta" aria-label={t('finops.drawer.sessionMeta')}>
                        <span><b>{t('finops.columns.user')}</b>{session.userId}</span>
                        <span><b>{t('finops.columns.agent')}</b>{session.agentId}</span>
                        <span><b>{t('finops.columns.model')}</b>{session.modelName || '-'}</span>
                        <span><b>{t('finops.columns.updated')}</b>{formatDate(session.updatedAt, locale)}</span>
                    </section>

                    <section className="finops-drawer-metrics">
                        <MiniMetric label={t('finops.metrics.totalTokens')} value={formatNumber(session.totalTokens)} />
                        <MiniMetric label={t('finops.metrics.inputTokens')} value={formatNumber(session.inputTokens)} />
                        <MiniMetric label={t('finops.metrics.outputTokens')} value={formatNumber(session.outputTokens)} />
                        <MiniMetric label={t('finops.columns.messages')} value={formatNumber(data?.stats.messageCount ?? session.messageCount)} />
                    </section>

                    {error ? <div className="conn-banner conn-banner-error">{t('finops.loadFailed', { error })}</div> : null}
                    {loading ? <div className="finops-table-loading">{t('finops.drawer.loadingMessages')}</div> : null}

                    {data ? (
                        <>
                            <section className="finops-drawer-insights" aria-label={t('finops.drawer.explainability')}>
                                <InsightLine
                                    label={t('finops.drawer.inputOutput')}
                                    value={`${formatNumber(session.inputTokens)} / ${formatNumber(session.outputTokens)}`}
                                    detail={t('finops.drawer.inputShare', { percent: formatPercent(inputShare) })}
                                />
                                <InsightLine
                                    label={t('finops.drawer.messageMix')}
                                    value={t('finops.drawer.messageMixValue', {
                                        users: roleCounts.user,
                                        assistants: roleCounts.assistant,
                                        tools: roleCounts.tool,
                                    })}
                                    detail={t('finops.drawer.messageMixDetail', { count: data.stats.messageCount })}
                                />
                                <InsightLine
                                    label={t('finops.drawer.largestContent')}
                                    value={t('finops.drawer.largestContentValue', {
                                        length: formatNumber(data.stats.largestContentLength),
                                        role: roleLabel(data.stats.largestContentRole, t),
                                    })}
                                    detail={largestLabel}
                                />
                            </section>

                            <section className="finops-role-filter" role="tablist" aria-label={t('finops.drawer.roleFilter')}>
                                {(['all', 'user', 'assistant', 'tool'] as const).map(role => (
                                    <button
                                        key={role}
                                        type="button"
                                        className={`finops-role-filter-${role} ${roleFilter === role ? 'active' : ''}`}
                                        disabled={role !== 'all' && roleCounts[role] === 0}
                                        onClick={() => setRoleFilter(role)}
                                    >
                                        <span>{t(`finops.drawer.roles.${role}`)}</span>
                                        <b>{roleCounts[role]}</b>
                                    </button>
                                ))}
                            </section>

                            <section className="finops-message-timeline">
                                {filteredMessages.length === 0 ? (
                                    <div className="empty-state">
                                        <div className="empty-state-title">{t('common.noResults')}</div>
                                    </div>
                                ) : null}
                                {filteredMessages.map(message => {
                                    const rowKey = `${message.rowId}:${message.messageId || ''}`
                                    const expanded = expandedRows.has(rowKey)
                                    return (
                                        <MessageTimelineItem
                                            key={rowKey}
                                            message={message}
                                            durationSeconds={message.durationSeconds}
                                            expanded={expanded}
                                            locale={locale}
                                            onToggle={() => toggleMessage(rowKey)}
                                        />
                                    )
                                })}
                            </section>
                        </>
                    ) : null}
                </div>
            </aside>
        </div>
    )
}

interface MessageTimelineItemProps {
    message: SessionMessageDetail & { durationSeconds?: number | null }
    durationSeconds?: number | null
    expanded: boolean
    locale: string
    onToggle: () => void
}

function MessageTimelineItem({
    message,
    durationSeconds,
    expanded,
    locale,
    onToggle,
}: MessageTimelineItemProps) {
    const { t } = useTranslation()
    const isTool = message.toolRequest || message.toolResponse
    const role = isTool ? 'tool' : message.role
    const preRef = useRef<HTMLPreElement>(null)
    useScrollClass(preRef, 'is-scrolling', [expanded])

    return (
        <article className={`finops-message-item finops-message-${role}`}>
            <div className="finops-message-head">
                <div className="finops-message-title">
                    <span className={`finops-message-role finops-role-${role}`}>{roleLabel(role, t)}</span>
                    {message.toolName ? <span className="finops-message-tool-name">{message.toolName}</span> : null}

                    {durationSeconds !== undefined && durationSeconds !== null && durationSeconds >= 0 ? (
                        <span className="finops-message-duration">
                            <Clock size={12} />
                            {formatDuration(durationSeconds)}
                        </span>
                    ) : null}

                    {message.tokens != null ? (
                        <span className="finops-message-tokens-badge">
                            <Zap size={12} />
                            {t('finops.drawer.messageTokens', { tokens: formatNumber(message.tokens) })}
                        </span>
                    ) : null}

                    {message.error ? (
                        <span className="finops-message-error-badge">
                            <AlertTriangle size={12} />
                            {t('finops.drawer.errorFlag')}
                        </span>
                    ) : null}
                </div>
                <time>{formatDate(message.createdAt, locale)}</time>
            </div>
            <button type="button" className="finops-message-preview" onClick={onToggle}>
                {message.contentPreview || t('finops.drawer.emptyContent')}
            </button>
            <div className="finops-message-meta">
                <span className={`finops-meta-size ${message.contentLength > 5000 ? 'finops-size-large' : ''}`}>
                    <BarChart2 size={12} />
                    {t('finops.drawer.contentLength', { length: formatNumber(message.contentLength) })}
                </span>

                <span className="finops-meta-divider">·</span>

                <span className="finops-meta-visibility">
                    {message.userVisible ? t('finops.drawer.userVisible') : t('finops.drawer.userHidden')}
                </span>

                <span className="finops-meta-divider">·</span>

                <span className="finops-meta-visibility">
                    {message.agentVisible ? t('finops.drawer.agentVisible') : t('finops.drawer.agentHidden')}
                </span>
            </div>
            {expanded ? (
                <pre ref={preRef} className="finops-message-content">
                    <code>
                        {formatMessageContent(message.contentText)
                            .split('\n')
                            .map((line, idx) => (
                                <span key={idx} className="finops-code-line">
                                    <span className="finops-line-number">{idx + 1}</span>
                                    <span className="finops-line-text">{line}</span>
                                </span>
                            ))}
                    </code>
                    {message.contentTruncated ? (
                        <div className="finops-content-truncated-warning">
                            {t('finops.drawer.contentTruncated')}
                        </div>
                    ) : null}
                </pre>
            ) : null}
        </article>
    )
}

function isUserMessage(message: SessionMessageDetail) {
    return !message.toolRequest && !message.toolResponse && message.role === 'user'
}

function messageTimestampMs(message: SessionMessageDetail) {
    const createdAtMs = new Date(message.createdAt).getTime()
    if (Number.isFinite(createdAtMs)) {
        return createdAtMs
    }
    return new Date(message.insertedAt).getTime()
}

function formatDuration(seconds: number) {
    if (seconds < 1) {
        return '<1s'
    }
    return `${Math.max(0, Math.round(seconds))}s`
}
