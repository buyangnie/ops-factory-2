import ReactECharts from 'echarts-for-react'
import type { EChartsOption } from 'echarts'
import ChartHeaderLegend from '../../../platform/ui/primitives/ChartHeaderLegend'

interface MonitoringChartCardProps {
    title: string
    subtitle?: string
    summary?: string
    legendItems?: Array<{ label: string; color: string; dashed?: boolean }>
    option?: EChartsOption
    height?: number
    isLoading?: boolean
    isEmpty?: boolean
    emptyText?: string
    loadingText?: string
}

export default function MonitoringChartCard({
    title,
    subtitle,
    summary,
    legendItems,
    option,
    height = 220,
    isLoading = false,
    isEmpty = false,
    emptyText,
    loadingText,
}: MonitoringChartCardProps) {
    return (
        <div className="mon-chart-block mon-chart-card">
            <div className="mon-chart-card-head">
                <div className="mon-chart-card-meta">
                    <span className="mon-chart-title">{title}</span>
                    {subtitle && <span className="mon-chart-subtitle">{subtitle}</span>}
                </div>
                <div className="mon-chart-card-side">
                    {legendItems && legendItems.length > 0 && (
                        <ChartHeaderLegend items={legendItems} />
                    )}
                    {summary && <span className="mon-chart-summary">{summary}</span>}
                </div>
            </div>
            {(() => {
                if (isLoading) {
                    return (
                        <div className="mon-chart-empty" style={{ height }}>
                            {loadingText}
                        </div>
                    )
                }
                if (isEmpty || !option) {
                    return (
                        <div className="mon-chart-empty" style={{ height }}>
                            {emptyText}
                        </div>
                    )
                }
                return <ReactECharts option={option} style={{ height, width: '100%' }} opts={{ renderer: 'svg' }} notMerge lazyUpdate />
            })()}
        </div>
    )
}
