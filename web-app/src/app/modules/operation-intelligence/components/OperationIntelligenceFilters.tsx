import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { RefreshCw } from '../../../platform/ui/icons/AppIcons'
import { useUser } from '../../../platform/providers/UserContext'
import Button from '../../../platform/ui/primitives/Button'
import FilterSelect from '../../../platform/ui/filters/FilterSelect'
import { getEnvironments, getProductConfigRule } from '../../../../services/operationIntelligenceAPI'
import type { EnvironmentInfo } from '../../../../services/operationIntelligenceAPI'

interface OperationIntelligenceFiltersProps {
    envCode: string
    onEnvCodeChange: (v: string) => void
    onTimeRangeChange: (start: number, end: number) => void
    onRefresh: () => void
}

interface ProductOption {
    agentSolutionType: string
    productTypeName: string
}

export default function OperationIntelligenceFilters({
    envCode, onEnvCodeChange, onTimeRangeChange, onRefresh,
}: OperationIntelligenceFiltersProps) {
    const { t } = useTranslation()
    const { userId } = useUser()
    const [products, setProducts] = useState<ProductOption[]>([])
    const [selectedProduct, setSelectedProduct] = useState('')
    const [envOptions, setEnvOptions] = useState<EnvironmentInfo[]>([])
    const [alarmScoreMax, setAlarmScoreMax] = useState<number | null>(null)
    const [weights, setWeights] = useState<{ a: string; p: string; r: string } | null>(null)

    useEffect(() => {
        getEnvironments(userId)
            .then(res => {
                const envs = res.results || []
                setEnvOptions(envs)
                const seen = new Map<string, ProductOption>()
                for (const e of envs) {
                    if (!seen.has(e.agentSolutionType)) {
                        seen.set(e.agentSolutionType, {
                            agentSolutionType: e.agentSolutionType,
                            productTypeName: e.productTypeName || e.agentSolutionType,
                        })
                    }
                }
                const productList = [...seen.values()]
                setProducts(productList)
                if (productList.length > 0 && !selectedProduct) {
                    setSelectedProduct(productList[0].agentSolutionType)
                }
            })
            .catch(() => {
                setEnvOptions([])
                setProducts([])
            })
    }, [userId])

    useEffect(() => {
        const product = selectedProduct || (products.length > 0 ? products[0].agentSolutionType : '')
        if (!product) {
            return
        }
        getProductConfigRule(product, userId)
            .then(res => {
                const rule = res.result as Record<string, unknown> | null
                if (rule) {
                    if (rule.alarmScoreMax != null) {
                        setAlarmScoreMax(rule.alarmScoreMax as number)
                    }
                    if (rule.healthWeight && typeof rule.healthWeight === 'string') {
                        const parts = rule.healthWeight.split(',')
                        if (parts.length === 3) {
                            setWeights({ a: parts[0].trim(), p: parts[1].trim(), r: parts[2].trim() })
                        }
                    }
                }
            })
            .catch(() => {
                setAlarmScoreMax(null)
                setWeights(null)
            })
    }, [selectedProduct, products, userId])

    const filteredEnvs = selectedProduct
        ? envOptions.filter(e => e.agentSolutionType === selectedProduct)
        : []

    useEffect(() => {
        if (filteredEnvs.length > 0 && !filteredEnvs.some(e => e.envCode === envCode)) {
            onEnvCodeChange(filteredEnvs[0].envCode)
        }
    }, [selectedProduct, filteredEnvs])

    const [timeWindow, setTimeWindow] = useState(1)

    const timeOptions = [
        { value: '0.25', label: t('operationIntelligence.last15Min') },
        { value: '1', label: t('operationIntelligence.lastHour') },
        { value: '2', label: t('operationIntelligence.last2Hours') },
        { value: '12', label: t('operationIntelligence.last12Hours') },
        { value: '24', label: t('operationIntelligence.last24Hours') },
        { value: '48', label: t('operationIntelligence.last48Hours') },
    ]

    const handleTimeWindow = (hours: number) => {
        setTimeWindow(hours)
        const now = Date.now()
        onTimeRangeChange(now - hours * 3600000, now)
    }

    const scoreItems = [
        alarmScoreMax != null ? { label: t('operationIntelligence.alarmScoreMax'), value: String(alarmScoreMax) } : null,
        weights ? { label: t('operationIntelligence.availability'), value: weights.a } : null,
        weights ? { label: t('operationIntelligence.performance'), value: weights.p } : null,
        weights ? { label: t('operationIntelligence.resource'), value: weights.r } : null,
    ].filter((item): item is { label: string; value: string } => Boolean(item))

    return (
        <div className="operation-intelligence-control-panel">
            <div className="operation-intelligence-filters">
                <FilterSelect
                    label={t('operationIntelligence.product')}
                    value={selectedProduct}
                    options={products.map(p => ({ value: p.agentSolutionType, label: p.productTypeName }))}
                    onChange={value => {
                        setSelectedProduct(value)
                        const envsOfProduct = envOptions.filter(env => env.agentSolutionType === value)
                        if (envsOfProduct.length > 0) {
                            onEnvCodeChange(envsOfProduct[0].envCode)
                        }
                    }}
                />
                <FilterSelect
                    label={t('operationIntelligence.environment')}
                    value={envCode}
                    options={filteredEnvs.map(e => ({ value: e.envCode, label: e.envName || e.envCode }))}
                    onChange={onEnvCodeChange}
                />
                <FilterSelect
                    label={t('operationIntelligence.timeRange')}
                    value={String(timeWindow)}
                    options={timeOptions}
                    onChange={value => handleTimeWindow(Number(value))}
                />
            </div>
            <div className="operation-intelligence-score-chips">
                {scoreItems.map(item => (
                    <div key={item.label} className="operation-intelligence-score-chip">
                        <span className="operation-intelligence-score-chip-label">{item.label}</span>
                        <span className="operation-intelligence-score-chip-value">{item.value}</span>
                    </div>
                ))}
                {scoreItems.length === 0 && (
                    <span className="operation-intelligence-score-empty">{t('operationIntelligence.noDataShort')}</span>
                )}
            </div>
            <Button
                size="sm"
                variant="secondary"
                iconOnly
                className="operation-intelligence-refresh-button"
                leadingIcon={<RefreshCw size={14} />}
                onClick={onRefresh}
                aria-label={t('operationIntelligence.refresh')}
                title={t('operationIntelligence.refresh')}
            />
        </div>
    )
}
