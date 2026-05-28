import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import Pagination from '../../../platform/ui/primitives/Pagination'
import type {
    AgentUsage,
    DistributionItem,
    ModelUsage,
    OverviewResponse,
    PageResponse,
    SessionUsage,
    UserUsage,
} from '../../../../services/finopsAPI'

export type TabId = 'overview' | 'agents' | 'users' | 'sessions' | 'models'
export type DetailTabId = Exclude<TabId, 'overview'>

export interface CompactMetric {
    label: string
    value: string
    meta?: string
}

export interface SplitSegment {
    id: string
    label: string
    value: number
    color: string
}

export interface DetailPages {
    agents: number
    users: number
    sessions: number
    models: number
}

export interface DetailData {
    agents: PageResponse<AgentUsage> | null
    users: PageResponse<UserUsage> | null
    sessions: PageResponse<SessionUsage> | null
    models: PageResponse<ModelUsage> | null
}

export type DetailPageResponse =
    | PageResponse<AgentUsage>
    | PageResponse<UserUsage>
    | PageResponse<SessionUsage>
    | PageResponse<ModelUsage>

export const PAGE_SIZE = 25

export const chartColorSets = {
    token: ['var(--chart-1)', 'var(--chart-2)'],
    session: ['var(--chart-1)', 'var(--chart-3)'],
    provider: ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-5)', 'var(--chart-7)', 'var(--chart-8)'],
    user: ['var(--chart-5)', 'var(--chart-7)', 'var(--chart-8)', 'var(--chart-1)', 'var(--chart-2)'],
    driver: 'var(--chart-1)',
    primary: 'var(--chart-1)',
    muted: 'color-mix(in srgb, var(--color-border) 72%, transparent)',
}

export function formatNumber(value: number | null | undefined): string {
    const n = value ?? 0
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
    if (n >= 1_000) return `${(n / 1_000).toFixed(1)}k`
    return String(Math.round(n))
}

export function formatPercent(value: number | null | undefined): string {
    if (value == null) return '-'
    return `${(value * 100).toFixed(1)}%`
}

export function formatGrowth(value: number | null | undefined): string {
    if (value == null) return '-'
    const prefix = value > 0 ? '+' : ''
    return `${prefix}${formatPercent(value)}`
}

export function formatDate(value: string | null | undefined, locale: string): string {
    if (!value) return '-'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '-'
    return date.toLocaleString(locale, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function formatMessageContent(content: string): string {
    if (!content) return ''
    const trimmed = content.trim()
    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
        try {
            const parsed = JSON.parse(trimmed)
            return JSON.stringify(parsed, null, 2)
        } catch {
            return content
        }
    }
    return content
}

export function roleLabel(role: string | null | undefined, t: TFunction): string {
    if (role === 'user') return t('finops.drawer.roles.user')
    if (role === 'assistant') return t('finops.drawer.roles.assistant')
    if (role === 'tool') return t('finops.drawer.roles.tool')
    return role || '-'
}

export function sumBy<T>(items: T[], selector: (item: T) => number): number {
    return items.reduce((sum, item) => sum + selector(item), 0)
}

export function topShare<T>(items: T[], selector: (item: T) => number): number {
    const total = sumBy(items, selector)
    if (total <= 0 || items.length === 0) return 0
    return Math.max(...items.map(selector)) / total
}

export function normalizeSessionTypes(items: DistributionItem[], t: TFunction): DistributionItem[] {
    const byId = new Map(items.map(item => [item.id, item]))
    const manual = byId.get('user') ?? byId.get('manual')
    const scheduled = byId.get('scheduled')
    const total = items.reduce((sum, item) => sum + item.totalTokens, 0)

    return [
        {
            id: 'user',
            label: t('finops.sessionTypes.user'),
            sessionCount: manual?.sessionCount ?? 0,
            totalTokens: manual?.totalTokens ?? 0,
            percentage: total > 0 ? (manual?.totalTokens ?? 0) / total : 0,
        },
        {
            id: 'scheduled',
            label: t('finops.sessionTypes.scheduled'),
            sessionCount: scheduled?.sessionCount ?? 0,
            totalTokens: scheduled?.totalTokens ?? 0,
            percentage: total > 0 ? (scheduled?.totalTokens ?? 0) / total : 0,
        },
    ]
}

export function PeriodSummary({ data, locale }: { data: OverviewResponse | null | undefined; locale: string }) {
    const { t } = useTranslation()
    return (
        <div className="finops-period-strip">
            <span>{t('finops.period.label')}</span>
            <strong>{t('finops.period.latest30Days')}</strong>
            {data ? (
                <small>{t('finops.period.snapshot', {
                    time: formatDate(data.snapshotStatus.lastRefreshedAt, locale),
                    dbs: data.snapshotStatus.sourceDbCount,
                    skipped: data.snapshotStatus.skippedDbCount,
                })}</small>
            ) : null}
        </div>
    )
}

export function DimensionHeader({
    title,
    subtitle,
    metrics,
    segments,
}: {
    title: string
    subtitle: string
    metrics: CompactMetric[]
    segments?: SplitSegment[]
}) {
    return (
        <section className="finops-analysis-header">
            <div className="finops-analysis-top">
                <div className="finops-analysis-copy">
                    <h2>{title}</h2>
                    <p>{subtitle}</p>
                </div>
                <div className="finops-analysis-metrics">
                    {metrics.map(metric => (
                        <div className="finops-analysis-metric" key={metric.label}>
                            <span>{metric.label}</span>
                            <strong>{metric.value}</strong>
                            {metric.meta ? <small title={metric.meta}>{metric.meta}</small> : null}
                        </div>
                    ))}
                </div>
            </div>
            {segments?.length ? <SplitBar segments={segments} /> : null}
        </section>
    )
}

export function SplitBar({ segments }: { segments: SplitSegment[] }) {
    const total = segments.reduce((sum, item) => sum + item.value, 0)
    return (
        <div className="finops-split">
            <div className="finops-split-bar" aria-hidden="true">
                {segments.map(segment => {
                    const width = total > 0 ? segment.value / total * 100 : 0
                    return (
                        <svg
                            key={segment.id}
                            className="finops-split-segment"
                            width={`${width}%`}
                            viewBox="0 0 100 1"
                            preserveAspectRatio="none"
                        >
                            <rect x="0" y="0" width={width} height="1" fill={segment.color} />
                        </svg>
                    )
                })}
            </div>
            <div className="finops-split-legend">
                {segments.map(segment => (
                    <span key={segment.id}>
                        <svg className="finops-color-dot" viewBox="0 0 8 8" aria-hidden="true">
                            <circle cx="4" cy="4" r="4" fill={segment.color} />
                        </svg>
                        <b>{segment.label}</b>
                        <strong>{formatNumber(segment.value)}</strong>
                        <small>{formatPercent(total > 0 ? segment.value / total : 0)}</small>
                    </span>
                ))}
            </div>
        </div>
    )
}

export function DataTable({ children }: { children: ReactNode }) {
    return <table className="finops-table">{children}</table>
}

export function PaginationControls<T>({
    page,
    onPageChange,
}: {
    page: PageResponse<T> | null
    onPageChange: (page: number) => void
}) {
    if (!page || page.totalItems === 0) return null
    return (
        <Pagination
            currentPage={page.page}
            totalPages={page.totalPages}
            pageSize={page.size}
            totalItems={page.totalItems}
            onPageChange={onPageChange}
        />
    )
}

export function MiniMetric({ label, value }: { label: string; value: string }) {
    return (
        <div className="finops-mini-metric">
            <span>{label}</span>
            <strong>{value}</strong>
        </div>
    )
}

export function InsightLine({ label, value, detail }: { label: string; value: string; detail?: string }) {
    return (
        <div className="finops-insight-line">
            <span>{label}</span>
            <strong>{value}</strong>
            {detail ? <small title={detail}>{detail}</small> : null}
        </div>
    )
}
