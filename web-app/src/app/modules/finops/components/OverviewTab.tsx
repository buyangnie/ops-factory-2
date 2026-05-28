import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import ReactECharts from 'echarts-for-react'
import type { EChartsOption } from 'echarts'
import ChartHeaderLegend from '../../../platform/ui/primitives/ChartHeaderLegend'
import SectionCard from '../../../platform/ui/primitives/SectionCard'
import type { DistributionItem, ModelUsage, TaskExecutionLoad, UserUsage } from '../../../../services/finopsAPI'
import {
    formatNumber,
    formatPercent,
    normalizeSessionTypes,
    chartColorSets,
} from './SharedComponents'

interface TooltipParam {
    name?: string
}

interface OverviewTabProps {
    trend: { bucket: string; totalTokens: number; inputTokens: number; outputTokens: number; sessionCount: number }[]
    sessionTypes: DistributionItem[]
    models: ModelUsage[]
    taskExecutionLoad: TaskExecutionLoad
    totalTokens: number
    topUsers: UserUsage[]
}

export function OverviewTab({
    trend,
    sessionTypes,
    models,
    taskExecutionLoad,
    totalTokens,
    topUsers,
}: OverviewTabProps) {
    const { t } = useTranslation()
    const normalizedSessionTypes = normalizeSessionTypes(sessionTypes, t)
    const normalizedTrend = useMemo(() => normalizeDailyTrend(trend), [trend])
    const trendLegend = useMemo(() => [
        { label: t('finops.columns.input'), color: 'var(--chart-1)' },
        { label: t('finops.columns.output'), color: 'var(--chart-2)' },
        { label: t('finops.columns.total'), color: 'var(--chart-5)', dashed: true },
    ], [t])

    const echartsOption = useMemo<EChartsOption>(() => {
        const xAxisData = normalizedTrend.map(p => p.bucket)
        const inputData = normalizedTrend.map(p => p.inputTokens)
        const outputData = normalizedTrend.map(p => p.outputTokens)
        const totalData = normalizedTrend.map(p => p.totalTokens)
        return {
            grid: {
                top: 28,
                right: 16,
                bottom: 42,
                left: 48,
                containLabel: false,
            },
            xAxis: {
                type: 'category',
                data: xAxisData,
                axisLine: { lineStyle: { color: 'var(--color-border)' } },
                axisTick: { show: false },
                axisLabel: {
                    color: 'var(--color-text-secondary)',
                    fontSize: 11,
                    hideOverlap: true,
                    margin: 10,
                },
                splitLine: { show: false },
            },
            yAxis: {
                type: 'value',
                axisLine: { show: false },
                axisTick: { show: false },
                axisLabel: {
                    color: 'var(--color-text-secondary)',
                    fontSize: 11,
                    formatter: (value: number) => formatNumber(value),
                },
                splitLine: { lineStyle: { color: 'var(--color-border)', type: 'dashed' } },
            },
            tooltip: {
                trigger: 'axis',
                backgroundColor: 'var(--color-bg-primary)',
                borderColor: 'var(--color-border)',
                textStyle: { color: 'var(--color-text-primary)', fontSize: 12 },
                formatter: (params: TooltipParam | TooltipParam[]) => {
                    const rows = Array.isArray(params) ? params : [params]
                    const first = rows[0]
                    const point = normalizedTrend.find(item => item.bucket === first?.name)
                    if (!first || !point) {
                        return ''
                    }
                    const metricRows = [
                        [t('finops.columns.total'), point.totalTokens],
                        [t('finops.columns.input'), point.inputTokens],
                        [t('finops.columns.output'), point.outputTokens],
                        [t('finops.columns.sessions'), point.sessionCount],
                        [t('finops.metrics.avgTokens'), point.sessionCount > 0 ? point.totalTokens / point.sessionCount : 0],
                    ]
                    return [
                        `<strong>${escapeHtml(first.name ?? '')}</strong>`,
                        ...metricRows.map(([label, value]) => `${escapeHtml(String(label))}: <b>${formatNumber(Number(value))}</b>`),
                    ].join('<br/>')
                },
            },
            series: [
                {
                    name: t('finops.columns.input'),
                    type: 'bar',
                    stack: 'tokens',
                    barMaxWidth: 34,
                    itemStyle: { color: 'var(--chart-1)', borderRadius: [3, 3, 0, 0] },
                    emphasis: { disabled: true },
                    data: inputData,
                },
                {
                    name: t('finops.columns.output'),
                    type: 'bar',
                    stack: 'tokens',
                    barMaxWidth: 34,
                    itemStyle: { color: 'var(--chart-2)', borderRadius: [3, 3, 0, 0] },
                    emphasis: { disabled: true },
                    data: outputData,
                },
                {
                    name: t('finops.columns.total'),
                    type: 'line',
                    smooth: false,
                    symbol: 'circle',
                    symbolSize: 6,
                    itemStyle: { color: 'var(--chart-5)' },
                    lineStyle: { width: 2, color: 'var(--chart-5)', type: 'dashed' },
                    emphasis: { disabled: true },
                    data: totalData,
                    z: 3,
                },
            ],
        }
    }, [normalizedTrend, t])

    return (
        <div className="finops-overview-grid">
            <SectionCard
                title={t('finops.sections.tokenTrend')}
                subtitle={t('finops.sections.tokenTrendSubtitle')}
                action={<ChartHeaderLegend items={trendLegend} />}
                className="finops-card-span"
            >
                <div className="finops-trend-chart">
                    <ReactECharts
                        className="finops-trend-echart"
                        option={echartsOption}
                        opts={{ renderer: 'svg' }}
                        style={{ width: '100%', height: '100%' }}
                    />
                </div>
            </SectionCard>
            <TaskExecutionLoadCard load={taskExecutionLoad} />
            <SessionTypeCard items={normalizedSessionTypes} />
            <ModelDistributionCard models={models} totalTokens={totalTokens} />
            <UserDistributionCard topUsers={topUsers} totalTokens={totalTokens} />
        </div>
    )
}

function TaskExecutionLoadCard({ load }: { load: TaskExecutionLoad }) {
    const { t } = useTranslation()
    const rows = [
        {
            id: 'tokens',
            label: t('finops.taskLoad.avgTokens'),
            value: formatNumber(load.avgTokensPerTask),
            detail: t('finops.taskLoad.avgTokensDetail'),
            ratio: scaleMetric(load.avgTokensPerTask, 20000),
            color: 'var(--chart-1)',
        },
        {
            id: 'messages',
            label: t('finops.taskLoad.avgMessages'),
            value: formatDecimal(load.avgMessagesPerTask),
            detail: t('finops.taskLoad.avgMessagesDetail'),
            ratio: scaleMetric(load.avgMessagesPerTask, 20),
            color: 'var(--chart-5)',
        },
        {
            id: 'tools',
            label: t('finops.taskLoad.avgToolResponses'),
            value: formatDecimal(load.avgToolResponsesPerTask),
            detail: t('finops.taskLoad.avgToolResponsesDetail'),
            ratio: scaleMetric(load.avgToolResponsesPerTask, 8),
            color: 'var(--chart-2)',
        },
    ]

    return (
        <SectionCard
            title={t('finops.sections.taskExecutionLoad')}
            subtitle={t('finops.sections.taskExecutionLoadSubtitle')}
            className="finops-overview-card"
            bodyClassName="finops-overview-card-body"
        >
            <div className="finops-task-load-card">
                {rows.map(row => (
                    <div className="finops-task-load-row" key={row.id}>
                        <div className="finops-task-load-head">
                            <span>{row.label}</span>
                            <strong>{row.value}</strong>
                        </div>
                        <small>{row.detail}</small>
                        <div className="finops-task-load-bar" aria-hidden="true">
                            <svg className="finops-inline-bar" viewBox="0 0 100 1" preserveAspectRatio="none">
                                <rect x="0" y="0" width={row.ratio * 100} height="1" fill={row.color} />
                            </svg>
                        </div>
                    </div>
                ))}
            </div>
        </SectionCard>
    )
}

function SessionTypeCard({ items }: { items: DistributionItem[] }) {
    const { t } = useTranslation()
    return (
        <SectionCard
            title={t('finops.sections.sessionTypes')}
            subtitle={t('finops.sections.sessionTypesSubtitle')}
            className="finops-overview-card"
            bodyClassName="finops-overview-card-body"
        >
            <DonutDistribution items={items} colors={chartColorSets.session} />
        </SectionCard>
    )
}

function UserDistributionCard({ topUsers, totalTokens }: { topUsers: UserUsage[]; totalTokens: number }) {
    const { t } = useTranslation()

    const list = useMemo(() => {
        if (!topUsers || topUsers.length === 0) return []
        return [...topUsers].sort((a, b) => b.totalTokens - a.totalTokens).slice(0, 5)
    }, [topUsers])

    return (
        <SectionCard
            title={t('finops.sections.userDistribution')}
            subtitle={t('finops.sections.userDistributionSubtitle')}
            className="finops-overview-card"
            bodyClassName="finops-overview-card-body"
        >
            <div className="finops-leaderboard">
                {list.map((u, index) => {
                    const percentage = totalTokens > 0 ? u.totalTokens / totalTokens : 0
                    const initials = u.userId ? u.userId.slice(0, 2).toUpperCase() : '??'
                    return (
                        <div className="finops-leaderboard-item" key={u.userId}>
                            <div className="finops-leaderboard-rank">
                                <span className={`rank-badge rank-${index + 1}`}>{index + 1}</span>
                            </div>
                            <div className={`finops-leaderboard-avatar finops-leaderboard-accent-${index % chartColorSets.user.length + 1}`}>
                                {initials}
                            </div>
                            <div className="finops-leaderboard-info">
                                <div className="info-top">
                                    <strong className="user-id" title={u.userId}>{u.userId}</strong>
                                    <span className="user-value">{formatNumber(u.totalTokens)} ({formatPercent(percentage)})</span>
                                </div>
                                <div className="info-bar">
                                    <svg className="finops-inline-bar" viewBox="0 0 100 1" preserveAspectRatio="none" aria-hidden="true">
                                        <rect x="0" y="0" width={percentage * 100} height="1" fill={chartColorSets.user[index % chartColorSets.user.length]} />
                                    </svg>
                                </div>
                            </div>
                        </div>
                    )
                })}
            </div>
        </SectionCard>
    )
}

function ModelDistributionCard({ models, totalTokens }: { models: ModelUsage[]; totalTokens: number }) {
    const { t } = useTranslation()
    const list = useMemo(() => {
        const byModel = new Map<string, ModelUsage & { providers: Set<string> }>()
        models.forEach(model => {
            const modelName = model.modelName || t('finops.labels.unknownModel')
            const providerName = model.providerName || t('finops.labels.unknownProvider')
            const existing = byModel.get(modelName)
            if (existing) {
                existing.providers.add(providerName)
                existing.sessionCount += model.sessionCount
                existing.activeUsers += model.activeUsers
                existing.activeAgents += model.activeAgents
                existing.totalTokens += model.totalTokens
                existing.inputTokens += model.inputTokens
                existing.outputTokens += model.outputTokens
                existing.avgTokensPerSession = existing.sessionCount > 0 ? existing.totalTokens / existing.sessionCount : 0
            } else {
                byModel.set(modelName, {
                    ...model,
                    modelName,
                    providerName,
                    providers: new Set([providerName]),
                })
            }
        })

        return Array.from(byModel.values())
            .sort((a, b) => b.totalTokens - a.totalTokens)
            .slice(0, 5)
            .map(item => ({
                ...item,
                providerLabel: Array.from(item.providers).join(' / '),
            }))
    }, [models, t])

    return (
        <SectionCard
            title={t('finops.sections.modelDistribution')}
            subtitle={t('finops.sections.modelDistributionSubtitle')}
            className="finops-overview-card"
            bodyClassName="finops-overview-card-body"
        >
            <div className="finops-model-distribution">
                {list.map((model, index) => {
                    const percentage = totalTokens > 0 ? model.totalTokens / totalTokens : 0
                    const color = chartColorSets.provider[index % chartColorSets.provider.length]
                    return (
                        <div className="finops-model-row" key={`${model.modelName}-${model.providerLabel}`}>
	                            <div className="finops-model-row-head">
	                                <span className="finops-model-name" title={model.modelName}>
                                        <svg className="finops-color-dot" viewBox="0 0 8 8" aria-hidden="true">
                                            <circle cx="4" cy="4" r="4" fill={color} />
                                        </svg>
	                                    {model.modelName}
	                                </span>
	                                <strong>{formatNumber(model.totalTokens)}</strong>
	                            </div>
                            <div className="finops-model-provider" title={model.providerLabel}>
                                {t('finops.modelDistribution.meta', { sessions: model.sessionCount })}
	                            </div>
	                            <div className="finops-model-bar" aria-hidden="true">
                                    <svg className="finops-inline-bar" viewBox="0 0 100 1" preserveAspectRatio="none">
                                        <rect x="0" y="0" width={percentage * 100} height="1" fill={color} />
                                    </svg>
	                            </div>
                            <div className="finops-model-meta">
                                <span>{formatPercent(percentage)}</span>
                                <span>{t('finops.modelDistribution.avgTokens', { tokens: formatNumber(model.avgTokensPerSession) })}</span>
                            </div>
                        </div>
                    )
                })}
            </div>
        </SectionCard>
    )
}

function DonutDistribution({ items, colors, caption }: { items: Array<{ id: string; label: string; totalTokens: number }>; colors: string[]; caption?: string }) {
    const { t } = useTranslation()
    const total = items.reduce((sum, item) => sum + item.totalTokens, 0)
    const radius = 42
    const circumference = 2 * Math.PI * radius
    let offset = 0

    return (
        <div className="finops-donut-card">
            <svg className="finops-donut" viewBox="0 0 120 120" role="img" aria-hidden="true">
                <circle cx="60" cy="60" r={radius} className="finops-donut-track" />
                {items.map((item, index) => {
                    const ratio = total > 0 ? item.totalTokens / total : 0
                    const dash = ratio * circumference
                    const segmentOffset = offset
                    offset += dash
                    return ratio > 0 ? (
                        <circle
                            key={item.id}
                            cx="60"
                            cy="60"
                            r={radius}
                            className="finops-donut-segment"
                            stroke={colors[index % colors.length]}
                            strokeDasharray={`${dash} ${circumference - dash}`}
                            strokeDashoffset={-segmentOffset}
                        />
                    ) : null
                })}
                <text x="60" y="56" textAnchor="middle" className="finops-donut-total">{formatNumber(total)}</text>
                <text x="60" y="72" textAnchor="middle" className="finops-donut-caption">{caption || t('finops.metrics.tokenUnit')}</text>
            </svg>
            <div className="finops-donut-legend">
                {items.map((item, index) => {
                    const percent = total > 0 ? item.totalTokens / total : 0
	                    return (
	                        <div className="finops-donut-row" key={item.id}>
	                            <span className="finops-donut-label" title={item.label}>
                                    <svg className="finops-color-dot" viewBox="0 0 8 8" aria-hidden="true">
                                        <circle cx="4" cy="4" r="4" fill={colors[index % colors.length]} />
                                    </svg>
	                                {item.label}
	                            </span>
                            <strong>{formatNumber(item.totalTokens)}</strong>
                            <small>{formatPercent(percent)}</small>
                        </div>
                    )
                })}
            </div>
        </div>
    )
}

function normalizeDailyTrend(trend: OverviewTabProps['trend']): OverviewTabProps['trend'] {
    if (trend.length === 0) return []

    const byBucket = new Map(trend.map(point => [point.bucket, point]))
    const latest = parseDay(trend[trend.length - 1].bucket)
    if (!latest) return trend

    const days: OverviewTabProps['trend'] = []
    for (let index = 13; index >= 0; index -= 1) {
        const date = new Date(latest)
        date.setUTCDate(latest.getUTCDate() - index)
        const bucket = formatDay(date)
        days.push(byBucket.get(bucket) ?? {
            bucket,
            totalTokens: 0,
            inputTokens: 0,
            outputTokens: 0,
            sessionCount: 0,
        })
    }
    return days
}

function parseDay(value: string): Date | null {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
    if (!match) return null
    return new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])))
}

function formatDay(date: Date): string {
    const year = date.getUTCFullYear()
    const month = String(date.getUTCMonth() + 1).padStart(2, '0')
    const day = String(date.getUTCDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

function escapeHtml(value: string): string {
    return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;')
}

function scaleMetric(value: number, reference: number): number {
    if (!Number.isFinite(value) || value <= 0 || reference <= 0) {
        return 0
    }
    return Math.max(0.04, Math.min(1, value / reference))
}

function formatDecimal(value: number): string {
    if (!Number.isFinite(value) || value === 0) {
        return '0'
    }
    return value.toFixed(1)
}
