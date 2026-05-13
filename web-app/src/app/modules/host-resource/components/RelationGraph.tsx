import { useMemo, useState, useCallback, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import ReactECharts from 'echarts-for-react'
import type { EChartsOption } from 'echarts'

import type { ClusterGraphData, ClusterGraphNode, GraphData, GraphNode } from '../../../../types/host'
import TopologyNodeIcon, { TOPOLOGY_ICON_DEFAULTS, getTopologyNodeSymbol } from './TopologyNodeIcon'

const BS_NODE_COLOR = '#6366f1'    // indigo
const BS_EDGE_COLOR = '#a5b4fc'    // light indigo

function getCssVar(styles: CSSStyleDeclaration, name: string, fallback: string): string {
    return styles.getPropertyValue(name).trim() || fallback
}

function escapeHtml(value: string | number | null | undefined): string {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
}

type Props = {
    data: GraphData | ClusterGraphData
    focusedHostId?: string | null
    hopFocusId?: string | null
    onNodeClick?: (nodeId: string) => void
    onNodeDoubleClick?: (nodeId: string) => void
    onBackgroundClick?: () => void
}

type RelationGraphNode = GraphNode | ClusterGraphNode

const PAD = 40 // padding around the graph area

/**
 * Compute topological layer positions: ingoing=0 nodes at the top,
 * then expand layer by layer via outgoing edges.
 * Spacing adapts to fill the available container width and height.
 */
function computeLayerPositions(
    nodeIds: string[],
    edges: { source: string; target: string }[],
    width: number,
    height: number,
): Map<string, { x: number; y: number }> {
    const nodeSet = new Set(nodeIds)

    // Build adjacency: count incoming edges within the visible set
    const inCount = new Map<string, number>()
    const outTargets = new Map<string, string[]>()
    for (const id of nodeIds) {
        inCount.set(id, 0)
        outTargets.set(id, [])
    }
    for (const e of edges) {
        if (nodeSet.has(e.source) && nodeSet.has(e.target)) {
            inCount.set(e.target, (inCount.get(e.target) ?? 0) + 1)
            outTargets.get(e.source)!.push(e.target)
        }
    }

    // BFS layering: start from nodes with no incoming edges
    const layers: string[][] = []
    const assigned = new Set<string>()

    // Seed: ingoing=0 nodes
    const seed = nodeIds.filter(id => (inCount.get(id) ?? 0) === 0)
    if (seed.length > 0) {
        layers.push(seed)
        seed.forEach(id => assigned.add(id))
    } else {
        // No root nodes (cycle) — put first node as layer 0
        layers.push([nodeIds[0]])
        assigned.add(nodeIds[0])
    }

    // BFS expand
    let frontier = layers[0]
    while (assigned.size < nodeIds.length) {
        const next: string[] = []
        for (const src of frontier) {
            for (const tgt of (outTargets.get(src) ?? [])) {
                if (!assigned.has(tgt)) {
                    next.push(tgt)
                    assigned.add(tgt)
                }
            }
        }
        if (next.length === 0) {
            // Remaining unassigned nodes (disconnected) — add as one layer
            const remaining = nodeIds.filter(id => !assigned.has(id))
            if (remaining.length > 0) layers.push(remaining)
            break
        }
        layers.push(next)
        frontier = next
    }

    // Adaptive positioning: fill the container with padding
    const result = new Map<string, { x: number; y: number }>()
    const usableW = Math.max(width - PAD * 2, 80)
    const usableH = Math.max(height - PAD * 2, 80)
    const layerCount = layers.length
    const maxLayerWidth = Math.max(...layers.map(l => l.length), 1)

    // Horizontal gap spreads nodes evenly across the usable width per layer
    const gapX = maxLayerWidth > 1 ? usableW / (maxLayerWidth - 1) : 0
    // Vertical gap spreads layers evenly across the usable height
    const gapY = layerCount > 1 ? usableH / (layerCount - 1) : 0

    for (let li = 0; li < layerCount; li++) {
        const layer = layers[li]
        const y = PAD + gapY * li
        const layerWidth = layer.length > 1 ? gapX * (layer.length - 1) : 0
        const startX = PAD + (usableW - layerWidth) / 2
        for (let ni = 0; ni < layer.length; ni++) {
            result.set(layer[ni], {
                x: startX + gapX * ni,
                y,
            })
        }
    }
    return result
}

export default function RelationGraph({ data, focusedHostId, hopFocusId, onNodeClick, onNodeDoubleClick, onBackgroundClick }: Props) {
    const { t } = useTranslation()

    const containerRef = useRef<HTMLDivElement | null>(null)
    const [dims, setDims] = useState({ w: 800, h: 300 })
    const [colorScheme, setColorScheme] = useState<'light' | 'dark'>(() => {
        if (typeof window === 'undefined') return 'light'
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    })

    // +1 hop filtering: show the hopFocusId node and all its direct neighbors (outgoing + incoming)
    const displayData = useMemo(() => {
        if (!hopFocusId) return data

        const neighborIds = new Set<string>([hopFocusId])
        const connectedEdges = data.edges.filter(e => {
            if (e.source === hopFocusId || e.target === hopFocusId) {
                neighborIds.add(e.source)
                neighborIds.add(e.target)
                return true
            }
            return false
        })

        return { nodes: data.nodes.filter(n => neighborIds.has(n.id)), edges: connectedEdges }
    }, [data, hopFocusId])

    useEffect(() => {
        if (typeof window === 'undefined') return undefined

        const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
        const updateColorScheme = (event: MediaQueryList | MediaQueryListEvent) => {
            setColorScheme(event.matches ? 'dark' : 'light')
        }

        updateColorScheme(mediaQuery)

        if (typeof mediaQuery.addEventListener === 'function') {
            mediaQuery.addEventListener('change', updateColorScheme)
            return () => mediaQuery.removeEventListener('change', updateColorScheme)
        }

        const legacyMediaQuery = mediaQuery as MediaQueryList & {
            addListener?: (listener: (event: MediaQueryListEvent) => void) => void
            removeListener?: (listener: (event: MediaQueryListEvent) => void) => void
        }
        legacyMediaQuery.addListener?.(updateColorScheme)
        return () => legacyMediaQuery.removeListener?.(updateColorScheme)
    }, [])

    const iconPalette = useMemo(() => {
        const styles = getComputedStyle(containerRef.current ?? document.documentElement)
        return {
            surface: getCssVar(styles, '--hr-topology-icon-surface', TOPOLOGY_ICON_DEFAULTS.surface),
            outline: getCssVar(styles, '--hr-topology-icon-outline', TOPOLOGY_ICON_DEFAULTS.outline),
            ink: getCssVar(styles, '--hr-topology-icon-ink', TOPOLOGY_ICON_DEFAULTS.ink),
            clusterAccent: getCssVar(styles, '--hr-topology-cluster-accent', TOPOLOGY_ICON_DEFAULTS.accents.cluster),
            businessAccent: getCssVar(styles, '--hr-topology-business-accent', TOPOLOGY_ICON_DEFAULTS.accents.business),
        }
    }, [dims.w, dims.h, colorScheme])

    const positionedOption = useMemo<EChartsOption>(() => {
        const nodeCount = displayData.nodes.length
        if (nodeCount === 0) return { series: [] }

        const nodeIdList = displayData.nodes.map(n => n.id)
        const positions = computeLayerPositions(nodeIdList, displayData.edges, dims.w, dims.h)

        // BFS to find all downstream nodes & edges reachable via outgoing traversal from focusedHostId
        const downstreamNodes = new Set<string>()
        const downstreamEdges = new Set<number>()
        if (focusedHostId) {
            const outMap = new Map<string, number[]>()
            displayData.edges.forEach((e, i) => {
                if (!outMap.has(e.source)) outMap.set(e.source, [])
                outMap.get(e.source)!.push(i)
            })
            const queue = [focusedHostId]
            downstreamNodes.add(focusedHostId)
            while (queue.length > 0) {
                const cur = queue.shift()!
                for (const idx of (outMap.get(cur) ?? [])) {
                    downstreamEdges.add(idx)
                    const tgt = displayData.edges[idx].target
                    if (!downstreamNodes.has(tgt)) {
                        downstreamNodes.add(tgt)
                        queue.push(tgt)
                    }
                }
            }
        }

        const highlightId = focusedHostId ?? hopFocusId
        const nodes = displayData.nodes.map((n) => {
            const pos = positions.get(n.id) ?? { x: dims.w / 2, y: dims.h / 2 }
            const isDownstream = focusedHostId ? downstreamNodes.has(n.id) : n.id === highlightId
            const isSource = n.id === focusedHostId
            const nodeKind = (() => {
                if (n.nodeType === 'business-service') return 'business'
                if (n.nodeType === 'cluster') return 'cluster'
                return 'host'
            })()
            const isBs = nodeKind === 'business'
            const isCluster = nodeKind === 'cluster'
            const accentColor = isBs
                ? iconPalette.businessAccent
                : iconPalette.clusterAccent
            const symbolSize = (() => {
                if (isBs) return isSource ? 52 : 46
                if (isCluster) {
                    if (isSource) return 50
                    return isDownstream ? 44 : 40
                }
                if (isSource) return 48
                return isDownstream ? 42 : 38
            })()
            return {
                id: n.id,
                name: n.name,
                x: pos.x,
                y: pos.y,
                symbol: getTopologyNodeSymbol(nodeKind, {
                    size: symbolSize,
                    accentColor,
                    surfaceColor: iconPalette.surface,
                    outlineColor: isDownstream ? '#94a3b8' : iconPalette.outline,
                    inkColor: iconPalette.ink,
                }),
                symbolSize,
                symbolKeepAspect: true,
                fixed: false,
                itemStyle: {
                    opacity: focusedHostId && !isDownstream ? 0.42 : 1,
                },
                label: {
                    show: true,
                    fontSize: (() => {
                        if (isSource) return 12
                        if (isDownstream) return 11
                        return 10
                    })(),
                    fontWeight: isBs ? 'bold' as const : 'normal' as const,
                    position: 'bottom' as const,
                },
                tooltip: {
                    formatter: () => {
                        if (isBs) {
                            return `<b>${escapeHtml(n.name)}</b><br/>${escapeHtml(t('hostResource.createBusinessService'))}`
                        }
                        const parts = [`<b>${escapeHtml(n.name)}</b>`]
                        if (isCluster) {
                            parts.push(escapeHtml(t('hostResource.createCluster')))
                            if ('type' in n && n.type) parts.push(`${escapeHtml(t('hostResource.clusterType'))}: ${escapeHtml(n.type)}`)
                            if ('hostCount' in n) parts.push(`${escapeHtml(t('hostResource.hostCount'))}: ${escapeHtml(n.hostCount)}`)
                            if ('mode' in n && n.mode) parts.push(`Mode: ${escapeHtml(n.mode)}`)
                            return parts.join('<br/>')
                        }
                        if (n.ip) parts.push(`${escapeHtml(t('hostResource.ip'))}: ${escapeHtml(n.ip)}`)
                        if (n.clusterType) parts.push(`${escapeHtml(t('hostResource.clusterType'))}: ${escapeHtml(n.clusterType)}`)
                        if ('clusterName' in n && n.clusterName) parts.push(`${escapeHtml(t('hostResource.cluster'))}: ${escapeHtml(n.clusterName)}`)
                        if ('purpose' in n && n.purpose) parts.push(`${escapeHtml(t('hostResource.purpose'))}: ${escapeHtml(n.purpose)}`)
                        return parts.join('<br/>')
                    },
                },
            }
        })

        const edges = displayData.edges.map((e, i) => {
            const isDownstream = downstreamEdges.has(i)
            const isBsEdge = e.type === 'business-entry'
            return {
                source: e.source,
                target: e.target,
                lineStyle: {
                    curveness: 0.15,
                    ...(focusedHostId ? {
                        width: isDownstream ? 3 : 1,
                        color: (() => {
                            if (!isDownstream) return '#d0d5dd'
                            return isBsEdge ? BS_NODE_COLOR : '#5470c6'
                        })(),
                        opacity: isDownstream ? 1 : 0.4,
                        type: isDownstream ? 'dashed' as const : 'solid' as const,
                    } : (() => {
                        if (isBsEdge) return { type: 'dashed' as const, color: BS_EDGE_COLOR }
                        return {}
                    })()),
                },
                label: { show: !focusedHostId || isDownstream, formatter: e.description || '', fontSize: 10 },
                symbol: ['none', 'arrow'] as [string, string],
                symbolSize: [4, 8],
            }
        })

        return {
            tooltip: {},
            animation: false,
            series: [{
                type: 'graph',
                layout: 'none',
                roam: true,
                draggable: true,
                label: {
                    position: 'bottom',
                    fontSize: 11,
                },
                data: nodes,
                links: edges,
                ...(!focusedHostId ? {
                    emphasis: {
                        focus: 'adjacency',
                        lineStyle: { width: 3 },
                    },
                } : {}),
            }],
        } as EChartsOption
    }, [displayData, focusedHostId, hopFocusId, dims, iconPalette, t])

    const handleEvents = useMemo(() => ({
        click: (params: { dataType?: string; data?: RelationGraphNode; componentType?: string }) => {
            if (params.componentType === 'series' && params.dataType === 'node' && params.data?.id && onNodeClick) {
                onNodeClick(params.data.id)
            } else if (params.componentType !== 'series' && onBackgroundClick) {
                onBackgroundClick()
            }
        },
        dblclick: (params: { dataType?: string; data?: RelationGraphNode; componentType?: string }) => {
            if (params.componentType === 'series' && params.dataType === 'node' && params.data?.id && onNodeDoubleClick) {
                onNodeDoubleClick(params.data.id)
            }
        },
    }), [onNodeClick, onNodeDoubleClick, onBackgroundClick])

    // Track container size via ResizeObserver to avoid infinite re-renders
    const setContainerRef = useCallback((el: HTMLDivElement | null) => {
        containerRef.current = el
    }, [])

    useEffect(() => {
        const el = containerRef.current
        if (!el) return
        const observer = new ResizeObserver((entries) => {
            for (const entry of entries) {
                const { width, height } = entry.contentRect
                const w = width || 800
                const h = height || 300
                setDims(prev => {
                    if (prev.w === w && prev.h === h) return prev
                    return { w, h }
                })
            }
        })
        observer.observe(el)
        return () => observer.disconnect()
    }, [data.nodes.length])

    if (data.nodes.length === 0) {
        return <div className="hr-graph-empty">No topology data available</div>
    }

    return (
        <div
            ref={setContainerRef}
            style={{ position: 'relative', width: '100%', height: '100%' }}
        >
            <div className="hr-topology-shell">
                <div className="hr-topology-legend" aria-hidden="true">
                    <div className="hr-topology-legend-item">
                        <TopologyNodeIcon kind="cluster" size={18} />
                        <span>{t('hostResource.createCluster')}</span>
                    </div>
                    <span className="hr-topology-legend-connector" />
                    <div className="hr-topology-legend-item">
                        <TopologyNodeIcon kind="business" size={18} />
                        <span>{t('hostResource.createBusinessService')}</span>
                    </div>
                </div>
                <ReactECharts
                    option={positionedOption}
                    style={{ height: '100%', width: '100%' }}
                    opts={{ renderer: 'svg' }}
                    notMerge
                    lazyUpdate
                    onEvents={handleEvents}
                    className={focusedHostId ? 'hr-graph-focused' : ''}
                />
            </div>
        </div>
    )
}
