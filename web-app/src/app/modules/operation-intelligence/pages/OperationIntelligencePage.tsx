import { useState, useEffect, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import OperationIntelligenceChart from '../components/OperationIntelligenceChart'
import DimensionScoreCards from '../components/DimensionScoreCards'
import IndicatorDetailTable from '../components/IndicatorDetailTable'
import AlarmDetailTable from '../components/AlarmDetailTable'
import OperationIntelligenceFilters from '../components/OperationIntelligenceFilters'
import ContributionAnalysis from '../components/ContributionAnalysis'
import TopologyView from '../components/TopologyView'
import { useUser } from '../../../platform/providers/UserContext'
import AnalyticsTableCard from '../../../platform/ui/primitives/AnalyticsTableCard'
import ChartHeaderLegend from '../../../platform/ui/primitives/ChartHeaderLegend'
import PageHeader from '../../../platform/ui/primitives/PageHeader'
import SectionCard from '../../../platform/ui/primitives/SectionCard'
import { getHealthIndicator } from '../../../../services/operationIntelligenceAPI'
import type { HealthIndicatorPoint } from '../../../../types/operationIntelligence'
import { CHART_COLORS } from '../styles/chart-colors'
import '../styles/operation-intelligence.css'

type DetailTab = 'availability' | 'performance' | 'alarm'

interface OperationIntelligencePageProps {
    embedded?: boolean
}

export default function OperationIntelligencePage({ embedded = false }: OperationIntelligencePageProps) {
    const { t } = useTranslation()
    const { userId } = useUser()
    const [envCode, setEnvCode] = useState<string>('')
    const [startTime, setStartTime] = useState<number>(Date.now() - 3600000)
    const [endTime, setEndTime] = useState<number>(Date.now())
    const [points, setPoints] = useState<HealthIndicatorPoint[]>([])
    const [activeTab, setActiveTab] = useState<DetailTab>('availability')
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const fetchData = useCallback(async () => {
        if (!envCode) {
            return
        }
        setLoading(true)
        setError(null)
        try {
            const res = await getHealthIndicator(envCode, startTime, endTime, userId)
            setPoints(res.results || [])
        } catch (err) {
            setPoints([])
            setError(err instanceof Error ? err.message : t('operationIntelligence.loadFailed'))
        } finally {
            setLoading(false)
        }
    }, [envCode, startTime, endTime, userId, t])

    useEffect(() => { fetchData() }, [fetchData])

    useEffect(() => {
        const timer = setInterval(() => fetchData(), 60000)
        return () => clearInterval(timer)
    }, [fetchData])

    const tabs: { key: DetailTab; label: string }[] = [
        { key: 'availability', label: t('operationIntelligence.availabilityDetail') },
        { key: 'performance', label: t('operationIntelligence.performanceDetail') },
        { key: 'alarm', label: t('operationIntelligence.alarmDetail') },
    ]
    const activeTabLabel = tabs.find(tab => tab.key === activeTab)?.label

    const healthLegend = [
        { label: t('operationIntelligence.good'), color: CHART_COLORS.good },
        { label: t('operationIntelligence.warning'), color: CHART_COLORS.warning },
        { label: t('operationIntelligence.orange'), color: CHART_COLORS.orange },
        { label: t('operationIntelligence.critical'), color: CHART_COLORS.critical },
    ]

    return (
        <div className={embedded
            ? 'operation-intelligence-page operation-intelligence-page-embedded'
            : 'page-container sidebar-top-page page-shell-wide operation-intelligence-page'}>
            {!embedded ? (
                <PageHeader
                    title={t('operationIntelligence.title')}
                    subtitle={t('operationIntelligence.subtitle')}
                />
            ) : null}

            <div className="operation-intelligence-filter-toolbar">
                <OperationIntelligenceFilters
                    envCode={envCode}
                    onEnvCodeChange={setEnvCode}
                    onTimeRangeChange={(start, end) => {
                        setStartTime(start)
                        setEndTime(end)
                    }}
                    onRefresh={fetchData}
                />
            </div>

            {error && (
                <div className="conn-banner conn-banner-error">
                    {t('operationIntelligence.loadFailedWithReason', { error })}
                </div>
            )}

            <div className="mon-section oi-cards-row">
                <div className="oi-score-panel">
                    <DimensionScoreCards envCode={envCode} startTime={startTime} endTime={endTime} />
                </div>
                <ContributionAnalysis envCode={envCode} startTime={startTime} endTime={endTime} />
            </div>

            <SectionCard
                title={t('operationIntelligence.chart')}
                subtitle={t('operationIntelligence.chartSubtitle')}
                action={<ChartHeaderLegend items={healthLegend} />}
                className="operation-intelligence-section operation-intelligence-chart-section"
                bodyClassName="operation-intelligence-chart-card-body"
            >
                <div className="operation-intelligence-chart-surface operation-intelligence-chart-surface-line">
                    <OperationIntelligenceChart points={points} loading={loading} />
                </div>
            </SectionCard>

            <AnalyticsTableCard
                title={activeTabLabel}
                subtitle={t('operationIntelligence.detailSubtitle')}
                className="operation-intelligence-section"
            >
                <div className="seg-filter oi-tabs" role="tablist" aria-label={t('operationIntelligence.detailTabs')}>
                    {tabs.map(tab => (
                        <button
                            key={tab.key}
                            type="button"
                            role="tab"
                            aria-selected={activeTab === tab.key}
                            className={`seg-filter-btn ${activeTab === tab.key ? 'active' : ''}`}
                            onClick={() => setActiveTab(tab.key)}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>
                <div className="oi-detail-content">
                    {activeTab === 'alarm' ? (
                        <AlarmDetailTable envCode={envCode} startTime={startTime} endTime={endTime} />
                    ) : (
                        <IndicatorDetailTable
                            envCode={envCode}
                            startTime={startTime}
                            endTime={endTime}
                            type={activeTab === 'availability' ? 'A' : 'P'}
                        />
                    )}
                </div>
            </AnalyticsTableCard>

            <SectionCard
                title={t('operationIntelligence.topology')}
                subtitle={t('operationIntelligence.topologySubtitle')}
                className="operation-intelligence-section"
            >
                <TopologyView points={points} envCode={envCode} />
            </SectionCard>
        </div>
    )
}
