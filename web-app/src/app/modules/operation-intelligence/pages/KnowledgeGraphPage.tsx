import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent } from 'react'
import { FileDown, Network, RefreshCw, Search, Trash2, Upload } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../platform/ui/primitives/PageHeader'
import SectionCard from '../../../platform/ui/primitives/SectionCard'
import Button from '../../../platform/ui/primitives/Button'
import { useUser } from '../../../platform/providers/UserContext'
import { useToast } from '../../../platform/providers/ToastContext'
import { useConfirmDialog } from '../../../platform/providers/ConfirmDialogContext'
import ListSearchInput from '../../../platform/ui/list/ListSearchInput'
import ListResultsMeta from '../../../platform/ui/list/ListResultsMeta'
import {
    runtime,
    type KnowledgeGraphCollapsedRelationRule,
    type KnowledgeGraphResourceTreeHierarchyRule,
} from '../../../../config/runtime'
import {
    exportGraph,
    getResourceTree,
    deleteEntities,
    deleteOntology,
    importGraph,
    importOntology,
    listGraphEnvironments,
    listOntologies,
    queryObservations,
    querySubgraph,
    type GraphEnvironmentInfo,
    type GraphEntity,
    type GraphExportPackage,
    type GraphOntology,
    type GraphObservation,
    type GraphSnapshot,
    type ResourceTreeGroup,
} from '../../../../services/operationIntelligenceAPI'
import '../styles/operation-intelligence.css'

const DEFAULT_ENV_CODE = 'prod'
const DEFAULT_ENTITY_ID = 'biz-prod-604015020'
const DEFAULT_ONTOLOGY_ID = 'b2b-callchain-v1'
const JSON_MIME_TYPE = 'application/json;charset=utf-8'
const KG_SELECTION_STORAGE_KEY = 'operation-intelligence:knowledge-graph-selection'
const GRAPH_NODE_WIDTH = 92
const GRAPH_NODE_HEIGHT = 38
const GRAPH_CANVAS_MIN_WIDTH = 1360
const ENTITY_OVERVIEW_NODE_WIDTH = 128
const ENTITY_OVERVIEW_NODE_HEIGHT = 46
const DEFAULT_SUBGRAPH_UPSTREAM_HOPS = 0
const DEFAULT_SUBGRAPH_DOWNSTREAM_HOPS = 3
const MIN_SUBGRAPH_HOPS = 0
const MAX_SUBGRAPH_HOPS = 6
const DEFAULT_GRAPH_ZOOM = 1
const MIN_GRAPH_ZOOM = 0.5
const MAX_GRAPH_ZOOM = 1.6
const GRAPH_ZOOM_STEP = 0.1
const NODE_STYLE_COLORS = [
    '#3b82f6',
    '#14b8a6',
    '#8b5cf6',
    '#f97316',
    '#0ea5e9',
    '#ec4899',
    '#22c55e',
    '#f59e0b',
    '#6366f1',
    '#06b6d4',
    '#84cc16',
    '#ef4444',
    '#64748b',
    '#a855f7',
    '#10b981',
    '#d946ef',
]
const EDGE_STYLE_COLORS = [
    '#2563eb',
    '#0f766e',
    '#9333ea',
    '#c2410c',
    '#be123c',
    '#0891b2',
    '#65a30d',
    '#7c3aed',
    '#475569',
    '#db2777',
    '#ca8a04',
    '#16a34a',
]
const EDGE_STYLE_DASHES = ['none', '5 4', '2 4', '8 4 2 4', '10 5', '3 3']
const EDGE_STYLE_WIDTHS = ['1.2', '1.35', '1.5', '1.65']
const PARALLEL_EDGE_SPACING = 18
const TYPE_LAYER_ORDER = [
    ['BusinessCapability', 'BusinessSystem', 'ManagementSystem'],
    ['PlatformService', 'Service', 'ServiceCluster'],
    ['ServiceInstance', 'ApplicationComponent', 'AppUnit'],
    ['Cluster', 'KubernetesCluster', 'ComputeGroup'],
    ['Pod'],
    ['Container'],
    ['ComputeNode'],
    ['Host'],
    ['PhysicalOrNetworkResource', 'EnvironmentOrPlatform'],
]
const TYPE_LAYER_INDEX = TYPE_LAYER_ORDER.reduce<Record<string, number>>((result, layer, index) => {
    layer.forEach(type => {
        result[type] = index
    })
    return result
}, {})

interface OntologyEntityType {
    type: string
    requiredProperties: string[]
}

interface OntologyRelationType {
    type: string
    from: string
    to: string
}

interface GraphViewNode {
    id: string
    type: string
    label: string
    x: number
    y: number
    width?: number
    height?: number
    collapsedChildrenCount?: number
    properties: Record<string, unknown>
}

interface GraphViewEdge {
    id: string
    type: string
    from: string
    to: string
}

interface GraphNodePosition {
    x: number
    y: number
}

type GraphCssProperties = CSSProperties & Record<'--kg-node-color' | '--kg-edge-color' | '--kg-edge-dash' | '--kg-edge-width', string>

type KnowledgeGraphTab = 'ontology' | 'entities'
type GraphCanvasDensity = 'compact' | 'spacious'

type ResourceTreeSelection =
    | { kind: 'group'; groupId: string }
    | { kind: 'nested-group'; parentId: string; childType: string }
    | { kind: 'entity'; entityId: string }

interface ResourceTreeChildGroup {
    id: string
    parentId: string
    type: string
    name: string
    children: GraphEntity[]
}

interface ResourceTreeEntityNode {
    entity: GraphEntity
    childGroups: ResourceTreeChildGroup[]
}

interface ResourceTreeViewGroup {
    id: string
    type: string
    name: string
    count: number
    children: ResourceTreeEntityNode[]
}

interface KnowledgeGraphSelection {
    ontologyId?: string
    envCode?: string
    entityId?: string
}

interface GraphSnapshotImportPackage {
    snapshot?: GraphSnapshot
}

function parseOntology(schemaDsl?: string): {
    entityTypes: OntologyEntityType[]
    relationTypes: OntologyRelationType[]
} {
    const entityTypes: OntologyEntityType[] = []
    const relationTypes: OntologyRelationType[] = []
    if (!schemaDsl) {
        return { entityTypes, relationTypes }
    }

    let section = ''
    let currentEntity: OntologyEntityType | null = null
    let currentRelation: OntologyRelationType | null = null
    let currentRelationEndpoint: 'from' | 'to' | null = null

    schemaDsl.split('\n').forEach(line => {
        const trimmedLine = line.trim()
        if (trimmedLine === 'entityTypes:') {
            section = 'entityTypes'
            return
        }
        if (trimmedLine === 'relationTypes:') {
            section = 'relationTypes'
            return
        }

        const typeMatch = trimmedLine.match(/^- type: "([^"]+)"/)
        if (typeMatch && section === 'entityTypes') {
            currentEntity = { type: typeMatch[1], requiredProperties: [] }
            entityTypes.push(currentEntity)
            currentRelation = null
            currentRelationEndpoint = null
            return
        }
        if (typeMatch && section === 'relationTypes') {
            currentRelation = { type: typeMatch[1], from: '', to: '' }
            relationTypes.push(currentRelation)
            currentEntity = null
            currentRelationEndpoint = null
            return
        }

        const propertyMatch = trimmedLine.match(/^- "([^"]+)"/)
        if (propertyMatch && currentEntity) {
            currentEntity.requiredProperties.push(propertyMatch[1])
            return
        }
        if (propertyMatch && currentRelation && currentRelationEndpoint) {
            const previous = currentRelation[currentRelationEndpoint]
            currentRelation[currentRelationEndpoint] = previous
                ? `${previous}, ${propertyMatch[1]}`
                : propertyMatch[1]
            return
        }

        const endpointMatch = trimmedLine.match(/^(from|to): "([^"]+)"/)
        if (endpointMatch && currentRelation) {
            currentRelation[endpointMatch[1] as 'from' | 'to'] = endpointMatch[2]
            currentRelationEndpoint = null
            return
        }
        const endpointListMatch = trimmedLine.match(/^(from|to):$/)
        if (endpointListMatch && currentRelation) {
            currentRelationEndpoint = endpointListMatch[1] as 'from' | 'to'
        }
    })

    return { entityTypes, relationTypes }
}

function ontologyFromDefinition(ontology?: GraphOntology): {
    entityTypes: OntologyEntityType[]
    relationTypes: OntologyRelationType[]
} {
    if (!ontology) {
        return { entityTypes: [], relationTypes: [] }
    }
    return {
        entityTypes: ontology.entityTypes.map(entityType => ({
            type: entityType.type,
            requiredProperties: entityType.requiredProperties,
        })),
        relationTypes: ontology.relationTypes.map(relationType => ({
            type: relationType.type,
            from: relationType.from.join(', '),
            to: relationType.to.join(', '),
        })),
    }
}

function groupCount<T>(items: T[], getKey: (item: T) => string): Record<string, number> {
    return items.reduce<Record<string, number>>((counts, item) => {
        const key = getKey(item)
        counts[key] = (counts[key] ?? 0) + 1
        return counts
    }, {})
}

function toDisplayName(entity: GraphEntity): string {
    return entity.displayName || entity.name || entity.id
}

function getEntitySearchText(entity: GraphEntity): string {
    const propertyValues = Object.values(entity.properties ?? {})
        .filter(value => typeof value === 'string' || typeof value === 'number')
        .map(String)
    return [
        entity.id,
        entity.name,
        entity.displayName,
        entity.type,
        ...propertyValues,
    ].filter(Boolean)
        .join(' ')
        .toLowerCase()
}

function downloadJson(fileName: string, payload: unknown): void {
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: JSON_MIME_TYPE })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
}

function fileStamp(): string {
    return new Date().toISOString().replace(/:/g, '').replace(/\.\d{3}Z$/, 'Z')
}

function readStoredSelection(): KnowledgeGraphSelection {
    if (typeof window === 'undefined') {
        return {}
    }
    try {
        return JSON.parse(window.localStorage.getItem(KG_SELECTION_STORAGE_KEY) || '{}') as KnowledgeGraphSelection
    } catch {
        return {}
    }
}

function normalizeEntityImportPayload(payload: unknown): GraphSnapshot {
    const candidate = payload as GraphSnapshotImportPackage
    return candidate.snapshot ?? payload as GraphSnapshot
}

function nodeTone(type: string): string {
    if (type === 'BusinessCapability') {
        return 'business'
    }
    if (type === 'Service' || type === 'ServiceInstance' || type === 'ServiceCluster' || type === 'PlatformService') {
        return 'service'
    }
    if (type === 'Cluster' || type === 'KubernetesCluster' || type === 'ComputeGroup') {
        return 'cluster'
    }
    if (type === 'Host' || type === 'ComputeNode') {
        return 'host'
    }
    if (type === 'Container' || type === 'Pod' || type === 'AppUnit') {
        return 'runtime'
    }
    return 'default'
}

function hashString(value: string): number {
    return value.split('').reduce((hash, char) => {
        return ((hash << 5) - hash + char.charCodeAt(0)) >>> 0
    }, 0)
}

function cssToken(value: string): string {
    return value.replace(/[^a-zA-Z0-9_-]/g, '-').toLowerCase() || 'default'
}

function nodeShapeIndex(type: string): number {
    return hashString(type) % 8
}

function nodeVisualStyle(type: string): CSSProperties {
    return {
        '--kg-node-color': NODE_STYLE_COLORS[hashString(type) % NODE_STYLE_COLORS.length],
    } as Partial<GraphCssProperties> as CSSProperties
}

function edgeVisualStyle(type: string): CSSProperties {
    const baseType = relationBaseType(type)
    const hash = hashString(baseType)
    return {
        '--kg-edge-color': EDGE_STYLE_COLORS[hash % EDGE_STYLE_COLORS.length],
        '--kg-edge-dash': EDGE_STYLE_DASHES[hash % EDGE_STYLE_DASHES.length],
        '--kg-edge-width': EDGE_STYLE_WIDTHS[hash % EDGE_STYLE_WIDTHS.length],
    } as Partial<GraphCssProperties> as CSSProperties
}

function relationTone(type: string): string {
    return cssToken(relationBaseType(type))
}

function relationBaseType(type: string): string {
    return type.replace(/\s+\(\d+\)$/, '')
}

function relationStyleIndex(type: string): number {
    const hash = relationBaseType(type).split('').reduce((result, char) => {
        return result + char.charCodeAt(0)
    }, 0)
    return hash % 6
}

function formatPropertyName(name: string): string {
    return name
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
        .replace(/[_-]+/g, ' ')
        .replace(/\s+/g, ' ')
        .trim()
}

function formatPropertyValue(value: unknown): string {
    if (value == null || value === '') {
        return '-'
    }
    if (Array.isArray(value)) {
        return value.map(formatPropertyValue).join(', ')
    }
    if (typeof value === 'object') {
        return Object.entries(value as Record<string, unknown>)
            .map(([key, item]) => `${formatPropertyName(key)}: ${formatPropertyValue(item)}`)
            .join('; ')
    }
    if (typeof value === 'boolean') {
        return value ? 'true' : 'false'
    }
    return String(value)
}

function getResourceCardFields(entity: GraphEntity): Array<[string, unknown]> {
    return Object.entries({
        id: entity.id,
        status: entity.status,
        ...entity.properties,
    }).filter(([, value]) => value !== undefined && value !== null && value !== '').slice(0, 4)
}

function findCollapsedRelationRule(
    relation: { type: string; to: string },
    entityById: Record<string, GraphEntity>,
    rules: KnowledgeGraphCollapsedRelationRule[],
): KnowledgeGraphCollapsedRelationRule | null {
    const targetType = entityById[relation.to]?.type
    if (!targetType) {
        return null
    }
    return rules.find(rule => {
        return rule.relationType === relation.type && rule.targetEntityTypes.includes(targetType)
    }) ?? null
}

function collapsedRelationGroupKey(rule: KnowledgeGraphCollapsedRelationRule, relation: { from: string; type: string }): string {
    return `${relation.from}::${rule.relationType}::${rule.targetEntityTypes.join('|')}`
}

function buildCollapsedRelationIds(
    relations: GraphSnapshot['relations'],
    entityById: Record<string, GraphEntity>,
    rules: KnowledgeGraphCollapsedRelationRule[],
    expandedNodeIds: Set<string>,
): Set<string> {
    const groups = new Map<string, { rule: KnowledgeGraphCollapsedRelationRule; relationIds: string[] }>()
    relations.forEach(relation => {
        if (expandedNodeIds.has(relation.from)) {
            return
        }
        const rule = findCollapsedRelationRule(relation, entityById, rules)
        if (!rule) {
            return
        }
        const key = collapsedRelationGroupKey(rule, relation)
        const group = groups.get(key) ?? { rule, relationIds: [] }
        group.relationIds.push(relation.id)
        groups.set(key, group)
    })
    return Array.from(groups.values()).reduce((result, group) => {
        if (group.relationIds.length > group.rule.threshold) {
            group.relationIds.forEach(relationId => result.add(relationId))
        }
        return result
    }, new Set<string>())
}

function matchesHierarchyRule(
    relation: { type: string; from: string; to: string },
    entityById: Record<string, GraphEntity>,
    ontologyId: string,
    rule: KnowledgeGraphResourceTreeHierarchyRule,
): boolean {
    const parentType = entityById[relation.from]?.type
    const childType = entityById[relation.to]?.type
    if (!parentType || !childType) {
        return false
    }
    return (!rule.ontologyId || rule.ontologyId === ontologyId)
        && rule.relationType === relation.type
        && (!rule.parentEntityTypes?.length || rule.parentEntityTypes.includes(parentType))
        && (!rule.childEntityTypes?.length || rule.childEntityTypes.includes(childType))
}

function buildResourceTreeViewGroups(
    groups: ResourceTreeGroup[],
    snapshot: GraphSnapshot | undefined,
    hierarchyRules: KnowledgeGraphResourceTreeHierarchyRule[],
    ontologyId: string,
): ResourceTreeViewGroup[] {
    const entityById = (snapshot?.entities ?? []).reduce<Record<string, GraphEntity>>((result, entity) => {
        result[entity.id] = entity
        return result
    }, {})
    const childGroupsByParent = (snapshot?.relations ?? [])
        .filter(relation => hierarchyRules.some(rule => matchesHierarchyRule(relation, entityById, ontologyId, rule)))
        .reduce<Record<string, Record<string, GraphEntity[]>>>((result, relation) => {
            const childEntity = entityById[relation.to]
            if (!childEntity) {
                return result
            }
            const parentGroups = result[relation.from] ?? {}
            const children = parentGroups[childEntity.type] ?? []
            children.push(childEntity)
            parentGroups[childEntity.type] = children
            result[relation.from] = parentGroups
            return result
        }, {})

    return groups.map(group => ({
        ...group,
        children: group.children.map(entity => ({
            entity,
            childGroups: Object.entries(childGroupsByParent[entity.id] ?? {})
                .map(([childType, children]) => ({
                    id: `${entity.id}::${childType}`,
                    parentId: entity.id,
                    type: childType,
                    name: childType,
                    children,
                }))
                .filter(childGroup => {
                    const ruleThreshold = hierarchyRules
                        .filter(rule => !rule.childEntityTypes?.length || rule.childEntityTypes.includes(childGroup.type))
                        .reduce((threshold, rule) => Math.min(threshold, rule.threshold ?? 1), Number.MAX_SAFE_INTEGER)
                    return childGroup.children.length >= (Number.isFinite(ruleThreshold) ? ruleThreshold : 1)
                }),
        })),
    }))
}

function edgePoint(source: GraphViewNode, target: GraphViewNode, direction: 'source' | 'target'): { x: number; y: number } {
    const sourceWidth = source.width ?? GRAPH_NODE_WIDTH
    const sourceHeight = source.height ?? GRAPH_NODE_HEIGHT
    const targetWidth = target.width ?? GRAPH_NODE_WIDTH
    const targetHeight = target.height ?? GRAPH_NODE_HEIGHT
    const sourceCenterX = source.x + sourceWidth / 2
    const sourceCenterY = source.y + sourceHeight / 2
    const targetCenterX = target.x + targetWidth / 2
    const targetCenterY = target.y + targetHeight / 2
    const deltaX = targetCenterX - sourceCenterX
    const deltaY = targetCenterY - sourceCenterY
    const absDeltaX = Math.abs(deltaX)
    const absDeltaY = Math.abs(deltaY)

    if (absDeltaX < 0.1 && absDeltaY < 0.1) {
        return { x: sourceCenterX, y: sourceCenterY }
    }

    const halfWidth = (direction === 'source' ? sourceWidth : targetWidth) / 2
    const halfHeight = (direction === 'source' ? sourceHeight : targetHeight) / 2
    const scale = Math.min(
        absDeltaX > 0.1 ? halfWidth / absDeltaX : Number.POSITIVE_INFINITY,
        absDeltaY > 0.1 ? halfHeight / absDeltaY : Number.POSITIVE_INFINITY,
    )
    const safeScale = Number.isFinite(scale) ? scale : 0
    const padding = direction === 'source' ? 1 : 3
    const edgeScale = safeScale + padding / Math.max(absDeltaX, absDeltaY, 1)

    if (direction === 'source') {
        return {
            x: sourceCenterX + deltaX * edgeScale,
            y: sourceCenterY + deltaY * edgeScale,
        }
    }

    return {
        x: targetCenterX - deltaX * edgeScale,
        y: targetCenterY - deltaY * edgeScale,
    }
}

function edgePairKey(edge: GraphViewEdge): string {
    return [edge.from, edge.to].sort().join('::')
}

function edgePairOffset(edge: GraphViewEdge, laneOffset: number, nodeById: Record<string, GraphViewNode>): { x: number; y: number } {
    if (laneOffset === 0) {
        return { x: 0, y: 0 }
    }
    const [firstNodeId, secondNodeId] = [edge.from, edge.to].sort()
    const firstNode = nodeById[firstNodeId]
    const secondNode = nodeById[secondNodeId]
    if (!firstNode || !secondNode) {
        return { x: 0, y: 0 }
    }
    const firstWidth = firstNode.width ?? GRAPH_NODE_WIDTH
    const firstHeight = firstNode.height ?? GRAPH_NODE_HEIGHT
    const secondWidth = secondNode.width ?? GRAPH_NODE_WIDTH
    const secondHeight = secondNode.height ?? GRAPH_NODE_HEIGHT
    const deltaX = secondNode.x + secondWidth / 2 - (firstNode.x + firstWidth / 2)
    const deltaY = secondNode.y + secondHeight / 2 - (firstNode.y + firstHeight / 2)
    const length = Math.hypot(deltaX, deltaY)
    if (length < 0.1) {
        return { x: 0, y: 0 }
    }
    return {
        x: -deltaY / length * laneOffset,
        y: deltaX / length * laneOffset,
    }
}

function getTypeLayer(type: string): number {
    return TYPE_LAYER_INDEX[type] ?? TYPE_LAYER_ORDER.length + (hashString(type) % 3)
}

function buildTypeLayerPositions(
    typeIds: string[],
    options: { startX: number; startY: number; columnGap: number; rowGap: number; maxColumns: number },
    edges: Array<{ from: string; to: string }> = [],
): Record<string, GraphNodePosition> {
    const graphLayers = buildDirectedLayers(typeIds, edges, getTypeLayer)
    const groupedTypes = typeIds.reduce<Record<number, string[]>>((groups, type) => {
        const layer = graphLayers[type] ?? getTypeLayer(type)
        const items = groups[layer] ?? []
        items.push(type)
        groups[layer] = items
        return groups
    }, {})
    const layerOrder = Object.keys(groupedTypes)
        .map(Number)
        .sort((left, right) => left - right)
    const compactLayerIndex = new Map(layerOrder.map((layer, index) => [layer, index]))
    return Object.entries(groupedTypes).reduce<Record<string, GraphNodePosition>>((positions, [layerText, types]) => {
        const layer = Number(layerText)
        const compactLayer = compactLayerIndex.get(layer) ?? layer
        const orderedTypes = [...types].sort((left, right) => {
            return (TYPE_LAYER_ORDER[layer]?.indexOf(left) ?? 99) - (TYPE_LAYER_ORDER[layer]?.indexOf(right) ?? 99)
                || left.localeCompare(right)
        })
        const columns = Math.min(options.maxColumns, Math.max(orderedTypes.length, 1))
        const offsetX = Math.max(0, (options.maxColumns - columns) * options.columnGap / 2)
        orderedTypes.forEach((type, index) => {
            const column = index % columns
            const row = Math.floor(index / columns)
            positions[type] = {
                x: options.startX + offsetX + column * options.columnGap,
                y: options.startY + compactLayer * options.rowGap + row * Math.round(options.rowGap * 0.5),
            }
        })
        return positions
    }, {})
}

function buildEntityLayerPositions(
    entities: GraphEntity[],
    options: { startX: number; startY: number; columnGap: number; rowGap: number; maxColumns: number },
    relations: Array<{ from: string; to: string }> = [],
): Record<string, GraphNodePosition> {
    const entityById = new Map(entities.map(entity => [entity.id, entity]))
    const graphLayers = buildDirectedLayers(
        entities.map(entity => entity.id),
        relations,
        id => getTypeLayer(entityById.get(id)?.type ?? ''),
    )
    const groupedEntities = entities.reduce<Record<number, GraphEntity[]>>((groups, entity) => {
        const layer = graphLayers[entity.id] ?? getTypeLayer(entity.type)
        const items = groups[layer] ?? []
        items.push(entity)
        groups[layer] = items
        return groups
    }, {})
    const layerOrder = Object.keys(groupedEntities)
        .map(Number)
        .sort((left, right) => left - right)
    const compactLayerIndex = new Map(layerOrder.map((layer, index) => [layer, index]))
    return Object.entries(groupedEntities).reduce<Record<string, GraphNodePosition>>((positions, [layerText, layerEntities]) => {
        const layer = Number(layerText)
        const compactLayer = compactLayerIndex.get(layer) ?? layer
        const orderedEntities = [...layerEntities].sort((left, right) => {
            return toDisplayName(left).localeCompare(toDisplayName(right))
        })
        const columns = Math.min(options.maxColumns, Math.max(orderedEntities.length, 1))
        const offsetX = Math.max(0, (options.maxColumns - columns) * options.columnGap / 2)
        orderedEntities.forEach((entity, index) => {
            const column = index % columns
            const row = Math.floor(index / columns)
            positions[entity.id] = {
                x: options.startX + offsetX + column * options.columnGap,
                y: options.startY + compactLayer * options.rowGap + row * Math.round(options.rowGap * 0.5),
            }
        })
        return positions
    }, {})
}

function buildDirectedLayers(
    nodeIds: string[],
    edges: Array<{ from: string; to: string }>,
    getFallbackLayer: (nodeId: string) => number,
): Record<string, number> {
    const nodeIdSet = new Set(nodeIds)
    const layers = nodeIds.reduce<Record<string, number>>((result, nodeId) => {
        result[nodeId] = getFallbackLayer(nodeId)
        return result
    }, {})
    const directionalEdges = edges.filter(edge => {
        return edge.from !== edge.to
            && nodeIdSet.has(edge.from)
            && nodeIdSet.has(edge.to)
            && getFallbackLayer(edge.from) <= getFallbackLayer(edge.to)
    })
    for (let index = 0; index < nodeIds.length; index += 1) {
        let changed = false
        directionalEdges.forEach(edge => {
            const nextLayer = Math.min(nodeIds.length - 1, (layers[edge.from] ?? 0) + 1)
            if (nextLayer > (layers[edge.to] ?? 0)) {
                layers[edge.to] = nextLayer
                changed = true
            }
        })
        if (!changed) {
            break
        }
    }
    const orderedLayers = Array.from(new Set(Object.values(layers))).sort((left, right) => left - right)
    const compactLayerIndex = new Map(orderedLayers.map((layer, index) => [layer, index]))
    return Object.fromEntries(Object.entries(layers).map(([nodeId, layer]) => [nodeId, compactLayerIndex.get(layer) ?? layer]))
}

function buildOntologyGraph(
    ontology: { entityTypes: OntologyEntityType[]; relationTypes: OntologyRelationType[] },
    entityTypeCounts: Record<string, number>,
): { nodes: GraphViewNode[]; edges: GraphViewEdge[] } {
    const entityTypeSet = new Set(ontology.entityTypes.map(entityType => entityType.type))
    const edges = ontology.relationTypes.flatMap(relationType => [
        ...relationType.from.split(', ').flatMap(from => relationType.to.split(', ').map(to => ({
            id: `ontology-${from}-${relationType.type}-${to}`,
            type: relationType.type,
            from,
            to,
        }))),
    ]).filter(edge => entityTypeSet.has(edge.from) && entityTypeSet.has(edge.to))
    const ontologyPositions = buildTypeLayerPositions(
        ontology.entityTypes.map(entityType => entityType.type),
        {
            startX: 95,
            startY: 46,
            columnGap: 265,
            rowGap: 112,
            maxColumns: 4,
        },
        edges,
    )
    const entityNodes = ontology.entityTypes.map((entityType, index) => ({
        id: entityType.type,
        type: entityType.type,
        label: entityType.type,
        ...(ontologyPositions[entityType.type] ?? {
            x: GRAPH_CANVAS_MIN_WIDTH - 205,
            y: 46 + index * 78,
        }),
        properties: {
            type: entityType.type,
            requiredProperties: entityType.requiredProperties,
            entityCount: entityTypeCounts[entityType.type] ?? 0,
        },
    }))
    return { nodes: entityNodes, edges }
}

function buildEntityOverviewGraph(snapshot?: GraphSnapshot): { nodes: GraphViewNode[]; edges: GraphViewEdge[] } {
    const entities = snapshot?.entities ?? []
    const relations = snapshot?.relations ?? []
    const groupedEntities = entities.reduce<Record<string, GraphEntity[]>>((groups, entity) => {
        const group = groups[entity.type] ?? []
        group.push(entity)
        groups[entity.type] = group
        return groups
    }, {})
    const typeOrder = [
        'ManagementSystem',
        'EnvironmentOrPlatform',
        'BusinessSystem',
        'PlatformService',
        'ServiceCluster',
        'ServiceInstance',
        'ApplicationComponent',
        'AppUnit',
        'KubernetesCluster',
        'Pod',
        'Container',
        'ComputeNode',
        'ComputeGroup',
        'Host',
        'PhysicalOrNetworkResource',
        'BusinessCapability',
        'Service',
        'Cluster',
    ]
    const orderedTypes = [
        ...typeOrder.filter(type => groupedEntities[type]?.length),
        ...Object.keys(groupedEntities).filter(type => !typeOrder.includes(type)).sort(),
    ]
    const entityById = entities.reduce<Record<string, GraphEntity>>((result, entity) => {
        result[entity.id] = entity
        return result
    }, {})
    const edgeMap = new Map<string, { type: string; from: string; to: string; count: number }>()
    relations.forEach(relation => {
        const fromType = entityById[relation.from]?.type
        const toType = entityById[relation.to]?.type
        if (!fromType || !toType) {
            return
        }
        const key = `${fromType}::${relation.type}::${toType}`
        const existing = edgeMap.get(key)
        if (existing) {
            existing.count += 1
            return
        }
        edgeMap.set(key, {
            type: relation.type,
            from: `type:${fromType}`,
            to: `type:${toType}`,
            count: 1,
        })
    })
    const connectedTypeIds = new Set<string>()
    edgeMap.forEach(edge => {
        connectedTypeIds.add(edge.from.replace(/^type:/, ''))
        connectedTypeIds.add(edge.to.replace(/^type:/, ''))
    })
    const connectedTypes = orderedTypes.filter(type => connectedTypeIds.has(type))
    const isolatedTypes = orderedTypes.filter(type => !connectedTypeIds.has(type))
    const overviewEdges = Array.from(edgeMap.entries()).map(([key, edge]) => ({
        id: key,
        type: `${edge.type} (${edge.count})`,
        from: edge.from,
        to: edge.to,
    }))
    const overviewPositions = buildTypeLayerPositions(
        connectedTypes,
        {
            startX: 95,
            startY: 82,
            columnGap: 265,
            rowGap: 135,
            maxColumns: 4,
        },
        overviewEdges.map(edge => ({
            from: edge.from.replace(/^type:/, ''),
            to: edge.to.replace(/^type:/, ''),
        })),
    )
    const nodes = [
        ...connectedTypes.map((type, index) => ({
            id: `type:${type}`,
            type,
            label: `${type} (${groupedEntities[type]?.length ?? 0})`,
            ...(overviewPositions[type] ?? {
                x: 95 + (index % 4) * 265,
                y: 82 + Math.floor(index / 4) * 135,
            }),
            width: ENTITY_OVERVIEW_NODE_WIDTH,
            height: ENTITY_OVERVIEW_NODE_HEIGHT,
            properties: {
                type,
                entityCount: groupedEntities[type]?.length ?? 0,
            },
        })),
        ...isolatedTypes.map((type, index) => ({
            id: `type:${type}`,
            type,
            label: `${type} (${groupedEntities[type]?.length ?? 0})`,
            x: GRAPH_CANVAS_MIN_WIDTH - 205,
            y: 82 + index * 78,
            width: ENTITY_OVERVIEW_NODE_WIDTH,
            height: ENTITY_OVERVIEW_NODE_HEIGHT,
            properties: {
                type,
                entityCount: groupedEntities[type]?.length ?? 0,
            },
        })),
    ]
    return { nodes, edges: overviewEdges }
}

function buildEntitySubgraph(
    subgraph: GraphSnapshot | null | undefined,
    collapsedRelationRules: KnowledgeGraphCollapsedRelationRule[],
    expandedNodeIds: Set<string>,
): { nodes: GraphViewNode[]; edges: GraphViewEdge[] } {
    const entities = subgraph?.entities ?? []
    const relations = subgraph?.relations ?? []
    const centerId = entities[0]?.id
    const entityById = entities.reduce<Record<string, GraphEntity>>((result, entity) => {
        result[entity.id] = entity
        return result
    }, {})
    const collapsedRelationIds = buildCollapsedRelationIds(relations, entityById, collapsedRelationRules, expandedNodeIds)
    const visibleRelations = relations.filter(relation => !collapsedRelationIds.has(relation.id))
    const visibleRelationEntityIds = visibleRelations.reduce<Set<string>>((result, relation) => {
        result.add(relation.from)
        result.add(relation.to)
        return result
    }, new Set<string>())
    const collapsedTargetEntityIds = relations
        .filter(relation => collapsedRelationIds.has(relation.id))
        .reduce<Set<string>>((result, relation) => {
            result.add(relation.to)
            return result
        }, new Set<string>())
    const visibleEntities = entities.filter(entity => {
        return entity.id === centerId || !collapsedTargetEntityIds.has(entity.id) || visibleRelationEntityIds.has(entity.id)
    })
    const collapsedChildrenByParent = relations
        .filter(relation => collapsedRelationIds.has(relation.id))
        .reduce<Record<string, number>>((result, relation) => {
            const targetEntity = entityById[relation.to]
            if (!targetEntity || visibleRelationEntityIds.has(targetEntity.id)) {
                return result
            }
            result[relation.from] = (result[relation.from] ?? 0) + 1
            return result
        }, {})
    const relationCounts = visibleRelations.reduce<Record<string, number>>((counts, relation) => {
        counts[relation.from] = (counts[relation.from] ?? 0) + 1
        counts[relation.to] = (counts[relation.to] ?? 0) + 1
        return counts
    }, {})
    const orderedEntities = [...visibleEntities].sort((left, right) => {
        if (left.id === centerId) {
            return -1
        }
        if (right.id === centerId) {
            return 1
        }
        return (relationCounts[right.id] ?? 0) - (relationCounts[left.id] ?? 0)
    })
    const connectedEntities = orderedEntities.filter(entity => entity.id === centerId
        || (relationCounts[entity.id] ?? 0) > 0
        || (collapsedChildrenByParent[entity.id] ?? 0) > 0)
    const isolatedEntities = orderedEntities.filter(entity => entity.id !== centerId
        && !(relationCounts[entity.id] ?? 0)
        && !(collapsedChildrenByParent[entity.id] ?? 0))
    const subgraphPositions = buildEntityLayerPositions(
        connectedEntities,
        {
            startX: 95,
            startY: 82,
            columnGap: 265,
            rowGap: 135,
            maxColumns: 4,
        },
        visibleRelations,
    )
    const connectedNodes = connectedEntities.map((entity, index) => {
        return {
            id: entity.id,
            type: entity.type,
            label: toDisplayName(entity),
            collapsedChildrenCount: collapsedChildrenByParent[entity.id] ?? 0,
            ...(subgraphPositions[entity.id] ?? {
                x: 95 + (index % 4) * 265,
                y: 82 + Math.floor(index / 4) * 135,
            }),
            properties: {
                id: entity.id,
                type: entity.type,
                name: entity.name,
                displayName: entity.displayName,
                status: entity.status,
                ...(entity.properties ?? {}),
            },
        }
    })
    const isolatedNodes = isolatedEntities.map((entity, index) => ({
        id: entity.id,
        type: entity.type,
        label: toDisplayName(entity),
        collapsedChildrenCount: collapsedChildrenByParent[entity.id] ?? 0,
        x: GRAPH_CANVAS_MIN_WIDTH - 205,
        y: 96 + index * 78,
        properties: {
            id: entity.id,
            type: entity.type,
            name: entity.name,
            displayName: entity.displayName,
            status: entity.status,
            ...(entity.properties ?? {}),
        },
    }))
    return {
        nodes: [...connectedNodes, ...isolatedNodes],
        edges: visibleRelations.map(relation => ({
            id: relation.id,
            type: relation.type,
            from: relation.from,
            to: relation.to,
        })),
    }
}

interface KnowledgeGraphPageProps {
    embedded?: boolean
}

export default function KnowledgeGraphPage({ embedded = false }: KnowledgeGraphPageProps) {
    const { t } = useTranslation()
    const { userId } = useUser()
    const { showToast } = useToast()
    const { requestConfirm } = useConfirmDialog()
    const storedSelection = useMemo(() => readStoredSelection(), [])
    const [ontologyId, setOntologyId] = useState(storedSelection.ontologyId || DEFAULT_ONTOLOGY_ID)
    const [ontologies, setOntologies] = useState<GraphOntology[]>([])
    const [envCode, setEnvCode] = useState(storedSelection.envCode || DEFAULT_ENV_CODE)
    const [entityId, setEntityId] = useState(storedSelection.entityId || DEFAULT_ENTITY_ID)
    const [entityQuery, setEntityQuery] = useState('')
    const [resourceSearch, setResourceSearch] = useState('')
    const [selectedResourceTreeItem, setSelectedResourceTreeItem] = useState<ResourceTreeSelection | null>(null)
    const [subgraphUpstreamHops, setSubgraphUpstreamHops] = useState(DEFAULT_SUBGRAPH_UPSTREAM_HOPS)
    const [subgraphDownstreamHops, setSubgraphDownstreamHops] = useState(DEFAULT_SUBGRAPH_DOWNSTREAM_HOPS)
    const [environments, setEnvironments] = useState<GraphEnvironmentInfo[]>([])
    const [resourceGroups, setResourceGroups] = useState<ResourceTreeGroup[]>([])
    const [observations, setObservations] = useState<GraphObservation[]>([])
    const [subgraph, setSubgraph] = useState<GraphSnapshot | null>(null)
    const [exportPackage, setExportPackage] = useState<GraphExportPackage | null>(null)
    const [activeGraphTab, setActiveGraphTab] = useState<KnowledgeGraphTab>('ontology')
    const [selectedOntologyNodeId, setSelectedOntologyNodeId] = useState<string | null>(null)
    const [selectedEntityNodeId, setSelectedEntityNodeId] = useState<string | null>(null)
    const [expandedEntityGraphNodeIds, setExpandedEntityGraphNodeIds] = useState<Set<string>>(new Set())
    const [expandedResourceGroupIds, setExpandedResourceGroupIds] = useState<Set<string>>(new Set())
    const [expandedResourceEntityIds, setExpandedResourceEntityIds] = useState<Set<string>>(new Set())
    const [selectedResourceEntity, setSelectedResourceEntity] = useState<GraphEntity | null>(null)
    const [loading, setLoading] = useState(false)
    const ontologyFileInputRef = useRef<HTMLInputElement | null>(null)
    const entitiesFileInputRef = useRef<HTMLInputElement | null>(null)

    const snapshot = exportPackage?.snapshot
    const selectedOntology = useMemo(() => {
        return ontologies.find(ontology => ontology.ontologyId === ontologyId)
    }, [ontologies, ontologyId])
    const ontology = useMemo(() => {
        return exportPackage?.schemaDsl ? parseOntology(exportPackage.schemaDsl) : ontologyFromDefinition(selectedOntology)
    }, [exportPackage, selectedOntology])
    const entityTypeCounts = useMemo(() => groupCount(snapshot?.entities ?? [], entity => entity.type), [snapshot])
    const ontologyGraph = useMemo(
        () => buildOntologyGraph(ontology, entityTypeCounts),
        [ontology, entityTypeCounts],
    )
    const entityOverviewGraph = useMemo(() => buildEntityOverviewGraph(snapshot), [snapshot])
    const collapsedRelationRules = runtime.OPERATION_INTELLIGENCE_KNOWLEDGE_GRAPH_COLLAPSED_RELATION_RULES
    const entitySubgraph = useMemo(
        () => buildEntitySubgraph(subgraph, collapsedRelationRules, expandedEntityGraphNodeIds),
        [collapsedRelationRules, expandedEntityGraphNodeIds, subgraph],
    )
    const activeEntityGraph = subgraph ? entitySubgraph : entityOverviewGraph

    const totalEntities = subgraph?.entities.length
        ?? snapshot?.entities.length
        ?? resourceGroups.reduce((sum, group) => sum + group.count, 0)
    const totalRelations = subgraph?.relations.length ?? snapshot?.relations.length ?? 0
    const totalObservations = subgraph ? observations.length : snapshot?.observations.length ?? observations.length
    const ontologyEntityTypeCount = ontology.entityTypes.length
    const ontologyRelationTypeCount = ontology.relationTypes.length
    const ontologyPropertyCount = ontology.entityTypes.reduce((sum, type) => {
        return sum + type.requiredProperties.length
    }, 0)
    const environmentOptions = useMemo(() => {
        return environments.map(environment => ({
            value: environment.envCode,
            label: environment.envName
                ? `${environment.envName}(${environment.envCode})`
                : environment.envCode,
        }))
    }, [environments])
    const graphTabs: Array<{ key: KnowledgeGraphTab; label: string }> = [
        { key: 'ontology', label: t('operationIntelligence.knowledgeGraph.ontology') },
        { key: 'entities', label: t('operationIntelligence.knowledgeGraph.entities') },
    ]
    const ontologyOptions = useMemo(() => {
        return ontologies.map(ontology => ({
            value: ontology.ontologyId,
            label: ontology.name || ontology.ontologyId,
        }))
    }, [ontologies])
    const selectedEntity = useMemo(() => {
        return snapshot?.entities.find(entity => entity.id === entityId)
    }, [entityId, snapshot])
    const resourceTreeHierarchyRules = runtime.OPERATION_INTELLIGENCE_KNOWLEDGE_GRAPH_RESOURCE_TREE_HIERARCHY_RULES
    const resourceTreeViewGroups = useMemo(
        () => buildResourceTreeViewGroups(resourceGroups, snapshot, resourceTreeHierarchyRules, ontologyId),
        [ontologyId, resourceGroups, resourceTreeHierarchyRules, snapshot],
    )
    const normalizedResourceSearch = resourceSearch.trim().toLowerCase()
    const filteredResourceGroups = useMemo(() => {
        if (!normalizedResourceSearch) {
            return resourceTreeViewGroups
        }
        return resourceTreeViewGroups
            .map(group => ({
                ...group,
                children: group.children.filter(node => getEntitySearchText(node.entity).includes(normalizedResourceSearch)
                    || group.name.toLowerCase().includes(normalizedResourceSearch)
                    || node.childGroups.some(childGroup => childGroup.name.toLowerCase().includes(normalizedResourceSearch)
                        || childGroup.children.some(entity => getEntitySearchText(entity).includes(normalizedResourceSearch)))),
            }))
            .filter(group => group.children.length > 0 || group.name.toLowerCase().includes(normalizedResourceSearch))
    }, [normalizedResourceSearch, resourceTreeViewGroups])
    const selectedResourceGroup = useMemo(() => {
        const selectedGroupId = selectedResourceTreeItem?.kind === 'group' ? selectedResourceTreeItem.groupId : undefined
        return filteredResourceGroups.find(group => group.id === selectedGroupId)
            ?? filteredResourceGroups[0]
            ?? null
    }, [filteredResourceGroups, selectedResourceTreeItem])
    const resourceDetailEntities = useMemo(() => {
        if (selectedResourceTreeItem?.kind === 'nested-group') {
            return filteredResourceGroups
                .flatMap(group => group.children)
                .find(node => node.entity.id === selectedResourceTreeItem.parentId)
                ?.childGroups.find(childGroup => childGroup.type === selectedResourceTreeItem.childType)
                ?.children ?? []
        }
        return selectedResourceGroup?.children.map(node => node.entity) ?? []
    }, [filteredResourceGroups, selectedResourceGroup, selectedResourceTreeItem])
    const resourceDetailTitle = useMemo(() => {
        if (selectedResourceTreeItem?.kind === 'nested-group') {
            return selectedResourceTreeItem.childType
        }
        return selectedResourceGroup?.name ?? t('operationIntelligence.knowledgeGraph.resourceInstances')
    }, [selectedResourceGroup, selectedResourceTreeItem, t])
    const selectedResourceProperties = selectedResourceEntity
        ? Object.entries({
            id: selectedResourceEntity.id,
            type: selectedResourceEntity.type,
            name: selectedResourceEntity.name,
            displayName: selectedResourceEntity.displayName,
            status: selectedResourceEntity.status,
            ...(selectedResourceEntity.properties ?? {}),
        }).filter(([, value]) => value !== undefined && value !== null && value !== '')
        : []

    const alertApiError = useCallback((err: unknown) => {
        showToast('error', err instanceof Error ? err.message : t('operationIntelligence.loadFailed'))
    }, [showToast, t])

    const loadGraph = useCallback(async (
        query?: { ontologyId?: string; envCode?: string },
        options?: { silent?: boolean },
    ): Promise<boolean> => {
        const targetOntologyId = query?.ontologyId ?? ontologyId
        const targetEnvCode = query?.envCode ?? envCode
        if (!targetEnvCode) {
            return false
        }
        setLoading(true)
        try {
            const [treeResponse, exportResponse] = await Promise.all([
                getResourceTree(targetEnvCode, userId, targetOntologyId),
                exportGraph(targetEnvCode, targetOntologyId, userId),
            ])
            setResourceGroups(treeResponse.result.roots)
            setExportPackage(exportResponse.result)
            setObservations([])
            setSubgraph(null)
            setSelectedEntityNodeId(null)
            setSelectedResourceEntity(null)
            setSelectedResourceTreeItem(null)
            setExpandedEntityGraphNodeIds(new Set())
            setExpandedResourceGroupIds(new Set())
            setExpandedResourceEntityIds(new Set())
            return true
        } catch (err) {
            if (!options?.silent) {
                alertApiError(err)
            }
            return false
        } finally {
            setLoading(false)
        }
    }, [alertApiError, envCode, ontologyId, userId])

    const reloadOntologies = useCallback(async () => {
        const ontologyResponse = await listOntologies(userId)
        setOntologies(ontologyResponse.result)
        return ontologyResponse.result
    }, [userId])

    const reloadGraphEnvironments = useCallback(async (targetOntologyId = ontologyId) => {
        const environmentResponse = await listGraphEnvironments(targetOntologyId, userId)
        setEnvironments(environmentResponse.result)
        return environmentResponse.result
    }, [ontologyId, userId])

    const clearGraphState = () => {
        setResourceGroups([])
        setObservations([])
        setSubgraph(null)
        setExportPackage(null)
        setSelectedOntologyNodeId(null)
        setSelectedEntityNodeId(null)
        setSelectedResourceEntity(null)
        setSelectedResourceTreeItem(null)
        setExpandedEntityGraphNodeIds(new Set())
        setExpandedResourceGroupIds(new Set())
        setExpandedResourceEntityIds(new Set())
    }

    const handleOntologySelectionChange = (nextOntologyId: string) => {
        if (nextOntologyId === ontologyId) {
            return
        }
        clearGraphState()
        setOntologyId(nextOntologyId)
        setEnvCode('')
    }

    useEffect(() => {
        if (!envCode) {
            clearGraphState()
            return
        }
        void loadGraph({ ontologyId, envCode }, { silent: true })
    }, [envCode, loadGraph, ontologyId])

    useEffect(() => {
        window.localStorage.setItem(KG_SELECTION_STORAGE_KEY, JSON.stringify({ ontologyId, envCode, entityId }))
    }, [entityId, envCode, ontologyId])

    useEffect(() => {
        let isMounted = true
        listOntologies(userId)
            .then(ontologyResponse => {
                if (!isMounted) {
                    return
                }
                setOntologies(ontologyResponse.result)
                if (ontologyResponse.result.length === 0) {
                    setOntologyId('')
                    setEnvCode('')
                    clearGraphState()
                    return
                }
                if (!ontologyResponse.result.some(ontology => ontology.ontologyId === ontologyId)) {
                    setOntologyId(ontologyResponse.result[0].ontologyId)
                }
            })
            .catch(() => {
                if (isMounted) {
                    setOntologies([])
                }
            })
        return () => {
            isMounted = false
        }
    }, [ontologyId, userId])

    useEffect(() => {
        if (!ontologyId) {
            setEnvironments([])
            clearGraphState()
            return
        }
        let isMounted = true
        listGraphEnvironments(ontologyId, userId)
            .then(environmentResponse => {
                if (!isMounted) {
                    return
                }
                const nextEnvironments = environmentResponse.result
                setEnvironments(nextEnvironments)
                if (nextEnvironments.length === 0) {
                    setEnvCode('')
                    clearGraphState()
                    return
                }
                if (!nextEnvironments.some(environment => environment.envCode === envCode)) {
                    setEnvCode(nextEnvironments[0].envCode)
                }
            })
            .catch(() => {
                if (isMounted) {
                    setEnvironments([])
                }
            })
        return () => {
            isMounted = false
        }
    }, [envCode, ontologyId, userId])

    const readJsonFile = async (file: File): Promise<unknown> => {
        try {
            return JSON.parse(await file.text())
        } catch (err) {
            const reason = err instanceof Error ? err.message : t('operationIntelligence.loadFailed')
            throw new Error(t('operationIntelligence.knowledgeGraph.fileReadFailed', { error: reason }))
        }
    }

    const handleImportOntologyFile = async (file: File) => {
        setLoading(true)
        try {
            const payload = await readJsonFile(file) as GraphOntology
            const response = await importOntology(payload, userId)
            const nextOntologies = await reloadOntologies()
            const nextOntologyId = response.result.ontologyId || nextOntologies[0]?.ontologyId || ontologyId
            setOntologyId(nextOntologyId)
            setExportPackage(null)
            showToast('success', t('operationIntelligence.knowledgeGraph.ontologyImported'))
        } catch (err) {
            alertApiError(err)
        } finally {
            setLoading(false)
            if (ontologyFileInputRef.current) {
                ontologyFileInputRef.current.value = ''
            }
        }
    }

    const handleImportEntitiesFile = async (file: File) => {
        setLoading(true)
        try {
            const payload = normalizeEntityImportPayload(await readJsonFile(file))
            await importGraph(payload, userId)
            if (payload.ontologyId) {
                setOntologyId(payload.ontologyId)
            }
            if (payload.envCode) {
                setEnvCode(payload.envCode)
            }
            if (payload.entities?.[0]?.id) {
                setEntityId(payload.entities[0].id)
                setEntityQuery('')
            }
            await reloadGraphEnvironments(payload.ontologyId ?? ontologyId)
            const loaded = await loadGraph({
                ontologyId: payload.ontologyId ?? ontologyId,
                envCode: payload.envCode ?? envCode,
            })
            if (loaded) {
                showToast('success', t('operationIntelligence.knowledgeGraph.entitiesImported'))
            }
        } catch (err) {
            alertApiError(err)
        } finally {
            setLoading(false)
            if (entitiesFileInputRef.current) {
                entitiesFileInputRef.current.value = ''
            }
        }
    }

    const resolveEntityQuery = (): GraphEntity | null => {
        const queryText = entityQuery.trim().toLowerCase()
        if (!queryText) {
            return null
        }
        const entities = snapshot?.entities ?? []
        return entities.find(entity => entity.id.toLowerCase() === queryText)
            ?? entities.find(entity => toDisplayName(entity).toLowerCase() === queryText)
            ?? entities.find(entity => getEntitySearchText(entity).includes(queryText))
            ?? null
    }

    const handleQuerySubgraph = async () => {
        const matchedEntity = resolveEntityQuery()
        if (!matchedEntity) {
            showToast('warning', t('operationIntelligence.knowledgeGraph.entityNotFound'))
            return
        }
        setLoading(true)
        try {
            const [subgraphResponse, observationResponse] = await Promise.all([
                querySubgraph(envCode, matchedEntity.id, subgraphUpstreamHops, subgraphDownstreamHops, userId, ontologyId),
                queryObservations(envCode, matchedEntity.id, userId, ontologyId),
            ])
            setEntityId(matchedEntity.id)
            setEntityQuery('')
            setSubgraph(subgraphResponse.result)
            setObservations(observationResponse.result.results)
            setSelectedEntityNodeId(null)
            setExpandedEntityGraphNodeIds(new Set())
            showToast('success', t('operationIntelligence.knowledgeGraph.subgraphLoaded', { entity: toDisplayName(matchedEntity) }))
        } catch (err) {
            alertApiError(err)
        } finally {
            setLoading(false)
        }
    }

    const handleSelectResourceEntity = (entity: GraphEntity) => {
        const fullEntity = snapshot?.entities.find(item => item.id === entity.id) ?? entity
        setEntityId(fullEntity.id)
        setEntityQuery('')
        setSelectedResourceEntity(fullEntity)
    }

    const handleSelectEntityGraphNode = (nodeId: string | null) => {
        setSelectedEntityNodeId(nodeId)
        if (!nodeId) {
            return
        }
        const graphNode = entitySubgraph.nodes.find(node => node.id === nodeId)
        if ((graphNode?.collapsedChildrenCount ?? 0) > 0 || expandedEntityGraphNodeIds.has(nodeId)) {
            setExpandedEntityGraphNodeIds(previous => {
                const next = new Set(previous)
                if (next.has(nodeId)) {
                    next.delete(nodeId)
                } else {
                    next.add(nodeId)
                }
                return next
            })
        }
        const graphEntity = subgraph?.entities.find(entity => entity.id === nodeId)
        if (graphEntity) {
            handleSelectResourceEntity(graphEntity)
        }
    }

    const handleSelectResourceGroup = (groupId: string) => {
        setSelectedResourceTreeItem({ kind: 'group', groupId })
        setSelectedResourceEntity(null)
        setExpandedResourceGroupIds(previous => {
            const next = new Set(previous)
            if (next.has(groupId)) {
                next.delete(groupId)
            } else {
                next.add(groupId)
            }
            return next
        })
    }

    const handleToggleResourceEntity = (entity: GraphEntity) => {
        handleSelectResourceEntity(entity)
        setSelectedResourceTreeItem({ kind: 'entity', entityId: entity.id })
        setExpandedResourceEntityIds(previous => {
            const next = new Set(previous)
            if (next.has(entity.id)) {
                next.delete(entity.id)
            } else {
                next.add(entity.id)
            }
            return next
        })
    }

    const handleSelectNestedResourceGroup = (parentId: string, childType: string) => {
        setSelectedResourceTreeItem({ kind: 'nested-group', parentId, childType })
        setSelectedResourceEntity(null)
    }

    const handleDeleteOntology = async () => {
        const confirmed = await requestConfirm({
            message: t('operationIntelligence.knowledgeGraph.confirmDeleteOntology'),
            variant: 'danger',
        })
        if (!confirmed) {
            return
        }
        setLoading(true)
        try {
            await deleteOntology(ontologyId, userId)
            clearGraphState()
            const nextOntologies = await reloadOntologies()
            setOntologyId(nextOntologies[0]?.ontologyId ?? '')
            showToast('success', t('operationIntelligence.knowledgeGraph.ontologyDeleted'))
        } catch (err) {
            alertApiError(err)
        } finally {
            setLoading(false)
        }
    }

    const handleDeleteEntities = async () => {
        const confirmed = await requestConfirm({
            message: t('operationIntelligence.knowledgeGraph.confirmDeleteEntities'),
            variant: 'danger',
        })
        if (!confirmed) {
            return
        }
        setLoading(true)
        try {
            await deleteEntities(ontologyId, envCode, userId)
            clearGraphState()
            const nextEnvironments = await reloadGraphEnvironments()
            if (nextEnvironments.length > 0) {
                setEnvCode(nextEnvironments[0].envCode)
            } else {
                setEnvCode('')
            }
            showToast('success', t('operationIntelligence.knowledgeGraph.entitiesDeleted'))
        } catch (err) {
            alertApiError(err)
        } finally {
            setLoading(false)
        }
    }

    const ensureExportPackage = async (): Promise<GraphExportPackage> => {
        if (exportPackage) {
            return exportPackage
        }
        const response = await exportGraph(envCode, ontologyId, userId)
        setExportPackage(response.result)
        return response.result
    }

    const handleExportOntology = async () => {
        setLoading(true)
        try {
            const graphPackage = exportPackage
            downloadJson(`kg-ontology-${ontologyId}-${fileStamp()}.json`, {
                manifest: graphPackage?.manifest ?? {
                    packageVersion: '1.0',
                    format: 'KG_ONTOLOGY_JSON',
                    ontologyId,
                    exportedAt: new Date().toISOString(),
                },
                ontology: graphPackage?.ontology ?? selectedOntology,
                schemaDsl: graphPackage?.schemaDsl,
            })
            showToast('success', t('operationIntelligence.knowledgeGraph.ontologyDownloaded'))
        } catch (err) {
            alertApiError(err)
        } finally {
            setLoading(false)
        }
    }

    const handleExportEntities = async () => {
        setLoading(true)
        try {
            const graphPackage = await ensureExportPackage()
            downloadJson(`kg-entities-${ontologyId}-${envCode}-${fileStamp()}.json`, {
                manifest: graphPackage.manifest,
                snapshot: graphPackage.snapshot,
            })
            showToast('success', t('operationIntelligence.knowledgeGraph.entitiesDownloaded'))
        } catch (err) {
            alertApiError(err)
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className={embedded
            ? 'operation-intelligence-page operation-intelligence-page-embedded'
            : 'page-container sidebar-top-page page-shell-wide operation-intelligence-page'}>
            <input
                ref={ontologyFileInputRef}
                type="file"
                accept="application/json,.json"
                className="kg-file-input"
                onChange={event => {
                    const file = event.target.files?.[0]
                    if (file) {
                        void handleImportOntologyFile(file)
                    }
                }}
            />
            <input
                ref={entitiesFileInputRef}
                type="file"
                accept="application/json,.json"
                className="kg-file-input"
                onChange={event => {
                    const file = event.target.files?.[0]
                    if (file) {
                        void handleImportEntitiesFile(file)
                    }
                }}
            />
            <div className="operation-intelligence-section kg-workspace">
                {!embedded ? (
                    <div className="kg-workspace-header">
                        <PageHeader
                            title={t('operationIntelligence.knowledgeGraph.title')}
                            subtitle={t('operationIntelligence.knowledgeGraph.subtitle')}
                        />
                    </div>
                ) : null}
                <div className="kg-control-panel">
                    <div
                        className="seg-filter oi-tabs kg-page-tabs"
                        role="tablist"
                        aria-label={t('operationIntelligence.knowledgeGraph.pageTabs')}
                    >
                        {graphTabs.map(tab => (
                            <button
                                key={tab.key}
                                type="button"
                                role="tab"
                                aria-selected={activeGraphTab === tab.key}
                                className={`seg-filter-btn ${activeGraphTab === tab.key ? 'active' : ''}`}
                                onClick={() => setActiveGraphTab(tab.key)}
                            >
                                {tab.label}
                            </button>
                        ))}
                    </div>
                    {!embedded ? (
                        <div className="kg-control-spacer" />
                    ) : null}
                    <label className="kg-field kg-ontology-picker" aria-label={t('operationIntelligence.knowledgeGraph.ontology')}>
                        <select value={ontologyId} onChange={event => handleOntologySelectionChange(event.target.value)}>
                            {ontologyOptions.map(option => (
                                <option key={option.value} value={option.value}>
                                    {option.label}
                                </option>
                            ))}
                        </select>
                    </label>
                </div>

                {activeGraphTab === 'ontology' ? (
                    <div className="kg-tab-page">
                        <div className="kg-tab-actions kg-ontology-actions">
                            <Button
                                leadingIcon={<Upload size={16} />}
                                onClick={() => ontologyFileInputRef.current?.click()}
                                disabled={loading}
                            >
                                {t('operationIntelligence.knowledgeGraph.importOntology')}
                            </Button>
                            <Button
                                leadingIcon={<FileDown size={16} />}
                                onClick={handleExportOntology}
                                disabled={loading}
                            >
                                {t('operationIntelligence.knowledgeGraph.exportOntology')}
                            </Button>
                            <Button leadingIcon={<Trash2 size={16} />} onClick={handleDeleteOntology} disabled={loading}>
                                {t('operationIntelligence.knowledgeGraph.deleteOntology')}
                            </Button>
                        </div>

                        <GraphCanvas
                            nodes={ontologyGraph.nodes}
                            edges={ontologyGraph.edges}
                            selectedNodeId={selectedOntologyNodeId}
                            onSelectNode={setSelectedOntologyNodeId}
                            nodeTitle={t('operationIntelligence.knowledgeGraph.nodeProperties')}
                            title={t('operationIntelligence.knowledgeGraph.ontologyGraph')}
                            subtitle={t('operationIntelligence.knowledgeGraph.ontologyGraphSubtitle', {
                                entityTypes: ontologyEntityTypeCount,
                                relationTypes: ontologyRelationTypeCount,
                                properties: ontologyPropertyCount,
                            })}
                            density="compact"
                        />

                        <SectionCard title={t('operationIntelligence.knowledgeGraph.schemaDsl')}>
                            <pre className="kg-export-preview">{exportPackage?.schemaDsl || t('common.noResults')}</pre>
                        </SectionCard>
                    </div>
                ) : (
                    <div className="kg-tab-page">
                        <div className="kg-entity-control-panel">
                            <div className="kg-entity-query-row">
                                <label className="kg-field kg-env-field">
                                    <span>{t('operationIntelligence.environment')}</span>
                                    <select value={envCode} onChange={event => setEnvCode(event.target.value)}>
                                        {environmentOptions.map(option => (
                                            <option key={option.value} value={option.value}>
                                                {option.label}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <label className="kg-field kg-field-wide">
                                    <span>{t('operationIntelligence.knowledgeGraph.entityQuery')}</span>
                                    <input
                                        value={entityQuery}
                                        onChange={event => setEntityQuery(event.target.value)}
                                        onKeyDown={event => {
                                            if (event.key === 'Enter') {
                                                void handleQuerySubgraph()
                                            }
                                        }}
                                        placeholder={t('operationIntelligence.knowledgeGraph.entityQueryPlaceholder')}
                                    />
                                </label>
                                <label className="kg-field kg-hop-field">
                                    <span>{t('operationIntelligence.knowledgeGraph.upstreamHops')}</span>
                                    <input
                                        type="number"
                                        min={MIN_SUBGRAPH_HOPS}
                                        max={MAX_SUBGRAPH_HOPS}
                                        value={subgraphUpstreamHops}
                                        onChange={event => {
                                            const nextHops = Number(event.target.value)
                                            if (!Number.isNaN(nextHops)) {
                                                setSubgraphUpstreamHops(Math.min(MAX_SUBGRAPH_HOPS, Math.max(MIN_SUBGRAPH_HOPS, nextHops)))
                                            }
                                        }}
                                    />
                                </label>
                                <label className="kg-field kg-hop-field">
                                    <span>{t('operationIntelligence.knowledgeGraph.downstreamHops')}</span>
                                    <input
                                        type="number"
                                        min={MIN_SUBGRAPH_HOPS}
                                        max={MAX_SUBGRAPH_HOPS}
                                        value={subgraphDownstreamHops}
                                        onChange={event => {
                                            const nextHops = Number(event.target.value)
                                            if (!Number.isNaN(nextHops)) {
                                                setSubgraphDownstreamHops(Math.min(MAX_SUBGRAPH_HOPS, Math.max(MIN_SUBGRAPH_HOPS, nextHops)))
                                            }
                                        }}
                                    />
                                </label>
                                <div className="kg-query-actions">
                                    <Button leadingIcon={<Search size={16} />} onClick={handleQuerySubgraph} disabled={loading}>
                                        {t('operationIntelligence.knowledgeGraph.querySubgraph')}
                                    </Button>
                                    {subgraph ? (
                                        <Button
                                            leadingIcon={<RefreshCw size={16} />}
                                            onClick={() => {
                                                setSubgraph(null)
                                                setObservations([])
                                                setSelectedEntityNodeId(null)
                                                setExpandedEntityGraphNodeIds(new Set())
                                                setEntityQuery('')
                                            }}
                                            disabled={loading}
                                        >
                                            {t('operationIntelligence.knowledgeGraph.showFullGraph')}
                                        </Button>
                                    ) : null}
                                </div>
                            </div>
                            <div className="kg-tab-actions kg-entity-actions">
                                <Button
                                    leadingIcon={<Upload size={16} />}
                                    onClick={() => entitiesFileInputRef.current?.click()}
                                    disabled={loading}
                                >
                                    {t('operationIntelligence.knowledgeGraph.importEntities')}
                                </Button>
                                <Button
                                    leadingIcon={<FileDown size={16} />}
                                    onClick={handleExportEntities}
                                    disabled={loading}
                                >
                                    {t('operationIntelligence.knowledgeGraph.exportEntities')}
                                </Button>
                                <Button leadingIcon={<Trash2 size={16} />} onClick={handleDeleteEntities} disabled={loading}>
                                    {t('operationIntelligence.knowledgeGraph.deleteEntities')}
                                </Button>
                            </div>
                        </div>

                        <GraphCanvas
                            nodes={activeEntityGraph.nodes}
                            edges={activeEntityGraph.edges}
                            selectedNodeId={selectedEntityNodeId}
                            onSelectNode={subgraph ? handleSelectEntityGraphNode : setSelectedEntityNodeId}
                            nodeTitle={t('operationIntelligence.knowledgeGraph.nodeProperties')}
                            title={subgraph
                                ? t('operationIntelligence.knowledgeGraph.subgraph')
                                : t('operationIntelligence.knowledgeGraph.entityGraph')}
                            subtitle={subgraph
                                ? t('operationIntelligence.knowledgeGraph.subgraphSubtitle', {
                                    entity: selectedEntity ? toDisplayName(selectedEntity) : entityId,
                                    upstreamHops: subgraphUpstreamHops,
                                    downstreamHops: subgraphDownstreamHops,
                                    entities: totalEntities,
                                    relations: totalRelations,
                                    observations: totalObservations,
                                })
                                : t('operationIntelligence.knowledgeGraph.entityGraphSubtitle', {
                                    entities: totalEntities,
                                    relations: totalRelations,
                                    observations: totalObservations,
                                })}
                            density="spacious"
                            showNodeProperties={false}
                        />

                        <div className="kg-resource-layout">
                            <aside className="kg-resource-tree-panel">
                                <div className="kg-resource-search">
                                    <ListSearchInput
                                        value={resourceSearch}
                                        placeholder={t('operationIntelligence.knowledgeGraph.searchResources')}
                                        onChange={setResourceSearch}
                                    />
                                </div>
                                <div className="kg-tree">
                                    {filteredResourceGroups.map(group => (
                                        <div key={group.id} className="kg-tree-group">
                                            <button
                                                type="button"
                                                className="kg-tree-group-title"
                                                data-selected={selectedResourceTreeItem?.kind === 'group'
                                                    && selectedResourceTreeItem.groupId === group.id
                                                    && !selectedResourceEntity ? 'true' : undefined}
                                                aria-expanded={expandedResourceGroupIds.has(group.id)}
                                                onClick={() => handleSelectResourceGroup(group.id)}
                                            >
                                                <Network size={15} aria-hidden="true" />
                                                <span>{group.name}</span>
                                                <strong>{group.children.length}</strong>
                                            </button>
                                            {expandedResourceGroupIds.has(group.id) ? (
                                                <div className="kg-tree-children">
                                                    {group.children.map(node => (
                                                        <div key={node.entity.id} className="kg-tree-entity-branch">
                                                            <button
                                                                type="button"
                                                                className="kg-tree-node"
                                                                data-selected={selectedResourceEntity?.id === node.entity.id ? 'true' : undefined}
                                                                aria-expanded={node.childGroups.length > 0
                                                                    ? expandedResourceEntityIds.has(node.entity.id)
                                                                    : undefined}
                                                                onClick={() => {
                                                                    if (node.childGroups.length > 0) {
                                                                        handleToggleResourceEntity(node.entity)
                                                                        return
                                                                    }
                                                                    handleSelectResourceEntity(node.entity)
                                                                }}
                                                            >
                                                                <span>{toDisplayName(node.entity)}</span>
                                                                <em>{node.entity.status}</em>
                                                            </button>
                                                            {expandedResourceEntityIds.has(node.entity.id) ? (
                                                                <div className="kg-tree-children kg-tree-nested-children">
                                                                    {node.childGroups.map(childGroup => (
                                                                        <div key={childGroup.id} className="kg-tree-group kg-tree-nested-group">
                                                                            <button
                                                                                type="button"
                                                                                className="kg-tree-group-title kg-tree-nested-title"
                                                                                data-selected={selectedResourceTreeItem?.kind === 'nested-group'
                                                                                    && selectedResourceTreeItem.parentId === node.entity.id
                                                                                    && selectedResourceTreeItem.childType === childGroup.type ? 'true' : undefined}
                                                                                onClick={() => handleSelectNestedResourceGroup(node.entity.id, childGroup.type)}
                                                                            >
                                                                                <Network size={13} aria-hidden="true" />
                                                                                <span>{childGroup.name}</span>
                                                                                <strong>{childGroup.children.length}</strong>
                                                                            </button>
                                                                        </div>
                                                                    ))}
                                                                </div>
                                                            ) : null}
                                                        </div>
                                                    ))}
                                                </div>
                                            ) : null}
                                        </div>
                                    ))}
                                </div>
                            </aside>

                            <section className="kg-resource-detail-panel">
                                <div className="kg-resource-panel-header">
                                    <div>
                                        <h3>{selectedResourceEntity
                                            ? t('operationIntelligence.knowledgeGraph.resourceDetail')
                                            : resourceDetailTitle}</h3>
                                        <p>{selectedResourceEntity
                                            ? selectedResourceEntity.type
                                            : t('operationIntelligence.knowledgeGraph.resourceInstanceCount', {
                                                count: resourceDetailEntities.length,
                                            })}</p>
                                    </div>
                                    {resourceSearch ? (
                                        <ListResultsMeta>
                                            {t('common.resultsFound', { count: filteredResourceGroups.reduce((sum, group) => sum + group.children.length, 0) })}
                                        </ListResultsMeta>
                                    ) : null}
                                </div>
                                {selectedResourceEntity ? (
                                    <div className="kg-resource-detail-card">
                                        <div className="kg-resource-card-header">
                                            <div className="kg-resource-card-title-block">
                                                <div className="kg-resource-card-tags">
                                                    <span className="kg-resource-card-tag">{selectedResourceEntity.type}</span>
                                                    {selectedResourceEntity.status ? (
                                                        <span className="kg-resource-card-tag kg-resource-card-tag-status">
                                                            {selectedResourceEntity.status}
                                                        </span>
                                                    ) : null}
                                                </div>
                                                <div className="kg-resource-card-title-line">
                                                    <h3 className="kg-resource-card-name">{toDisplayName(selectedResourceEntity)}</h3>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="kg-resource-card-meta kg-resource-card-meta-detail">
                                            {selectedResourceProperties.map(([key, value]) => (
                                                <div key={key} className="kg-resource-card-meta-field">
                                                    <span className="kg-resource-card-meta-label">{formatPropertyName(key)}</span>
                                                    <span className="kg-resource-card-meta-value">{formatPropertyValue(value)}</span>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                ) : resourceDetailEntities.length > 0 ? (
                                    <div className="kg-resource-card-grid">
                                        {resourceDetailEntities.map(entity => (
                                            <button
                                                key={entity.id}
                                                type="button"
                                                className="kg-resource-card"
                                                onClick={() => handleSelectResourceEntity(entity)}
                                            >
                                                <div className="kg-resource-card-header">
                                                    <div className="kg-resource-card-title-block">
                                                        <div className="kg-resource-card-tags">
                                                            <span className="kg-resource-card-tag">{entity.type}</span>
                                                            {entity.status ? (
                                                                <span className="kg-resource-card-tag kg-resource-card-tag-status">
                                                                    {entity.status}
                                                                </span>
                                                            ) : null}
                                                        </div>
                                                        <div className="kg-resource-card-title-line">
                                                            <h3 className="kg-resource-card-name">{toDisplayName(entity)}</h3>
                                                        </div>
                                                    </div>
                                                </div>
                                                <div className="kg-resource-card-meta">
                                                    {getResourceCardFields(entity).map(([key, value]) => (
                                                        <div key={key} className="kg-resource-card-meta-field">
                                                            <span className="kg-resource-card-meta-label">{formatPropertyName(key)}</span>
                                                            <span className="kg-resource-card-meta-value">{formatPropertyValue(value)}</span>
                                                        </div>
                                                    ))}
                                                </div>
                                            </button>
                                        ))}
                                    </div>
                                ) : (
                                    <div className="kg-empty-detail">
                                        {t('operationIntelligence.knowledgeGraph.selectResourceHint')}
                                    </div>
                                )}
                            </section>
                        </div>

                        <SectionCard title={t('operationIntelligence.knowledgeGraph.observationTrace')}>
                            <div className="kg-table">
                                {observations.map(observation => (
                                    <div key={observation.id} className="kg-table-row">
                                        <span>{observation.name}</span>
                                        <span>{observation.severity}</span>
                                        <span>
                                            {String(observation.value ?? '')}
                                            {observation.unit ? ` ${observation.unit}` : ''}
                                        </span>
                                        <span>{observation.observedAt}</span>
                                    </div>
                                ))}
                            </div>
                        </SectionCard>
                    </div>
                )}
            </div>
        </div>
    )
}

function GraphCanvas({
    nodes,
    edges,
    selectedNodeId,
    onSelectNode,
    nodeTitle,
    title,
    subtitle,
    density,
    showNodeProperties = true,
}: {
    nodes: GraphViewNode[]
    edges: GraphViewEdge[]
    selectedNodeId: string | null
    onSelectNode: (nodeId: string | null) => void
    nodeTitle: string
    title: string
    subtitle: string
    density: GraphCanvasDensity
    showNodeProperties?: boolean
}) {
    const { t } = useTranslation()
    const [zoom, setZoom] = useState(DEFAULT_GRAPH_ZOOM)
    const [nodePositions, setNodePositions] = useState<Record<string, GraphNodePosition>>({})
    const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null)
    const dragStateRef = useRef<{
        nodeId: string
        startClientX: number
        startClientY: number
        startX: number
        startY: number
        moved: boolean
    } | null>(null)
    const wasDraggedRef = useRef(false)

    useEffect(() => {
        setNodePositions(nodes.reduce<Record<string, GraphNodePosition>>((positions, node) => {
            positions[node.id] = { x: node.x, y: node.y }
            return positions
        }, {}))
        onSelectNode(null)
        setSelectedEdgeId(null)
    }, [nodes, onSelectNode])

    const positionedNodes = useMemo(() => {
        return nodes.map(node => ({
            ...node,
            x: nodePositions[node.id]?.x ?? node.x,
            y: nodePositions[node.id]?.y ?? node.y,
        }))
    }, [nodePositions, nodes])

    const nodeById = useMemo(() => {
        return positionedNodes.reduce<Record<string, GraphViewNode>>((result, node) => {
            result[node.id] = node
            return result
        }, {})
    }, [positionedNodes])
    const edgeLaneOffsets = useMemo(() => {
        const groups = edges.reduce<Record<string, GraphViewEdge[]>>((result, edge) => {
            if (!nodeById[edge.from] || !nodeById[edge.to]) {
                return result
            }
            const key = edgePairKey(edge)
            result[key] = [...(result[key] ?? []), edge]
            return result
        }, {})
        return Object.values(groups).reduce<Record<string, number>>((result, group) => {
            if (group.length === 1) {
                result[group[0].id] = 0
                return result
            }
            const orderedGroup = [...group].sort((first, second) => {
                return `${first.from}:${first.to}:${first.type}:${first.id}`
                    .localeCompare(`${second.from}:${second.to}:${second.type}:${second.id}`)
            })
            orderedGroup.forEach((edge, index) => {
                result[edge.id] = (index - (orderedGroup.length - 1) / 2) * PARALLEL_EDGE_SPACING
            })
            return result
        }, {})
    }, [edges, nodeById])
    const selectedNode = selectedNodeId ? nodeById[selectedNodeId] : null
    const selectedNodeRelatedEdgeIds = useMemo(() => {
        if (!selectedNodeId) {
            return new Set<string>()
        }
        return new Set(edges
            .filter(edge => edge.from === selectedNodeId || edge.to === selectedNodeId)
            .map(edge => edge.id))
    }, [edges, selectedNodeId])
    const selectedNodeProperties = selectedNode
        ? Object.entries(selectedNode.properties).filter(([, value]) => value !== undefined && value !== null && value !== '')
        : []
    const width = Math.max(
        GRAPH_CANVAS_MIN_WIDTH,
        ...positionedNodes.map(node => node.x + (node.width ?? GRAPH_NODE_WIDTH) + 80),
    )
    const minHeight = density === 'spacious' ? 560 : 420
    const height = Math.max(
        minHeight,
        ...positionedNodes.map(node => node.y + (node.height ?? GRAPH_NODE_HEIGHT) + 80),
    )
    const zoomedWidth = Math.ceil(width * zoom)
    const zoomedHeight = Math.ceil(height * zoom)

    const updateZoom = (nextZoom: number) => {
        setZoom(Math.min(MAX_GRAPH_ZOOM, Math.max(MIN_GRAPH_ZOOM, Number(nextZoom.toFixed(2)))))
    }

    const resetGraphView = () => {
        setZoom(DEFAULT_GRAPH_ZOOM)
        setSelectedEdgeId(null)
        onSelectNode(null)
        setNodePositions(nodes.reduce<Record<string, GraphNodePosition>>((positions, node) => {
            positions[node.id] = { x: node.x, y: node.y }
            return positions
        }, {}))
    }

    const clearGraphSelection = () => {
        setSelectedEdgeId(null)
        onSelectNode(null)
    }

    const handleNodePointerDown = (event: PointerEvent<HTMLButtonElement>, node: GraphViewNode) => {
        if (event.button !== 0) {
            return
        }
        event.currentTarget.setPointerCapture(event.pointerId)
        dragStateRef.current = {
            nodeId: node.id,
            startClientX: event.clientX,
            startClientY: event.clientY,
            startX: node.x,
            startY: node.y,
            moved: false,
        }
    }

    const handleNodePointerMove = (event: PointerEvent<HTMLButtonElement>) => {
        const dragState = dragStateRef.current
        if (!dragState) {
            return
        }
        const deltaX = (event.clientX - dragState.startClientX) / zoom
        const deltaY = (event.clientY - dragState.startClientY) / zoom
        if (Math.abs(deltaX) > 2 || Math.abs(deltaY) > 2) {
            dragState.moved = true
            wasDraggedRef.current = true
        }
        setNodePositions(previous => ({
            ...previous,
            [dragState.nodeId]: {
                x: Math.max(0, dragState.startX + deltaX),
                y: Math.max(0, dragState.startY + deltaY),
            },
        }))
    }

    const handleNodePointerUp = (event: PointerEvent<HTMLButtonElement>) => {
        const dragState = dragStateRef.current
        if (dragState) {
            wasDraggedRef.current = dragState.moved
        }
        if (event.currentTarget.hasPointerCapture(event.pointerId)) {
            event.currentTarget.releasePointerCapture(event.pointerId)
        }
        dragStateRef.current = null
    }

    return (
        <div className={`kg-graph-shell kg-graph-shell-${density}`}>
            <div className="kg-graph-heading">
                <div className="kg-graph-heading-copy">
                    <h3>{title}</h3>
                    <span>{subtitle}</span>
                </div>
                <div className="kg-graph-heading-actions">
                    <div className="kg-graph-zoom">
                        <button
                            type="button"
                            aria-label={t('operationIntelligence.knowledgeGraph.zoomOut')}
                            onClick={() => updateZoom(zoom - GRAPH_ZOOM_STEP)}
                        >
                            -
                        </button>
                        <input
                            type="range"
                            min={MIN_GRAPH_ZOOM}
                            max={MAX_GRAPH_ZOOM}
                            step={GRAPH_ZOOM_STEP}
                            value={zoom}
                            aria-label={t('operationIntelligence.knowledgeGraph.zoomLevel')}
                            onChange={event => updateZoom(Number(event.target.value))}
                        />
                        <button
                            type="button"
                            aria-label={t('operationIntelligence.knowledgeGraph.zoomIn')}
                            onClick={() => updateZoom(zoom + GRAPH_ZOOM_STEP)}
                        >
                            +
                        </button>
                        <span>{Math.round(zoom * 100)}%</span>
                    </div>
                    <button
                        type="button"
                        className="kg-graph-reset"
                        onClick={resetGraphView}
                    >
                        {t('operationIntelligence.knowledgeGraph.resetGraph')}
                    </button>
                    <em>{nodes.length}</em>
                </div>
            </div>
            <div className="kg-graph-scroll">
                <div className="kg-graph-zoom-surface" style={{ width: zoomedWidth, height: zoomedHeight }}>
                    <div
                        className="kg-graph-canvas"
                        style={{
                            width,
                            height,
                            transform: `scale(${zoom})`,
                        }}
                        onClick={clearGraphSelection}
                    >
                    <svg className="kg-graph-edges" viewBox={`0 0 ${width} ${height}`} aria-hidden="true">
                        <defs>
                            {EDGE_STYLE_COLORS.map((color, index) => (
                                <marker
                                    key={color}
                                    id={`kg-arrow-${index}`}
                                    viewBox="0 0 8 8"
                                    refX="7"
                                    refY="4"
                                    markerWidth="6"
                                    markerHeight="6"
                                    orient="auto-start-reverse"
                                >
                                    <path
                                        d="M 1 1 L 7 4 L 1 7"
                                        fill="none"
                                        stroke={color}
                                        strokeWidth="1.45"
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                    />
                                </marker>
                            ))}
                        </defs>
                        {edges.map(edge => {
                            const from = nodeById[edge.from]
                            const to = nodeById[edge.to]
                            if (!from || !to) {
                                return null
                            }
                            const start = edgePoint(from, to, 'source')
                            const end = edgePoint(from, to, 'target')
                            const pairOffset = edgePairOffset(edge, edgeLaneOffsets[edge.id] ?? 0, nodeById)
                            const startX = start.x + pairOffset.x
                            const startY = start.y + pairOffset.y
                            const endX = end.x + pairOffset.x
                            const endY = end.y + pairOffset.y
                            const midX = (startX + endX) / 2
                            const midY = (startY + endY) / 2
                            const markerId = `kg-arrow-${hashString(relationBaseType(edge.type)) % EDGE_STYLE_COLORS.length}`
                            return (
                                <g
                                    key={edge.id}
                                    className={[
                                        'kg-graph-edge',
                                        `kg-edge-style-${relationStyleIndex(edge.type)}`,
                                        `kg-edge-${relationTone(edge.type)}`,
                                        `kg-edge-type-${cssToken(relationBaseType(edge.type))}`,
                                        selectedEdgeId === edge.id ? 'kg-graph-edge-selected' : '',
                                        selectedNodeRelatedEdgeIds.has(edge.id) ? 'kg-graph-edge-related' : '',
                                    ].filter(Boolean).join(' ')}
                                    style={edgeVisualStyle(edge.type)}
                                    onClick={event => {
                                        event.stopPropagation()
                                        setSelectedEdgeId(selectedEdgeId === edge.id ? null : edge.id)
                                        onSelectNode(null)
                                    }}
                                >
                                    <line
                                        className="kg-graph-edge-hitbox"
                                        x1={startX}
                                        y1={startY}
                                        x2={endX}
                                        y2={endY}
                                    />
                                    <line x1={startX} y1={startY} x2={endX} y2={endY} markerEnd={`url(#${markerId})`} />
                                    <text x={midX} y={midY - 8}>{edge.type}</text>
                                </g>
                            )
                        })}
                    </svg>
                    {positionedNodes.map(node => (
                        <button
                            key={node.id}
                            type="button"
                            title={node.label.includes(node.type) ? node.label : `${node.label} · ${node.type}`}
                            aria-label={`${node.label} ${node.type}`}
                            className={[
                                'kg-graph-node',
                                `kg-node-${nodeTone(node.type)}`,
                                `kg-node-type-${cssToken(node.type)}`,
                                `kg-node-shape-${nodeShapeIndex(node.type)}`,
                            ].join(' ')}
                            data-selected={selectedNodeId === node.id ? 'true' : undefined}
                            style={{
                                ...nodeVisualStyle(node.type),
                                left: node.x,
                                top: node.y,
                                width: node.width,
                                minHeight: node.height,
                            }}
                            onPointerDown={event => handleNodePointerDown(event, node)}
                            onPointerMove={handleNodePointerMove}
                            onPointerUp={handleNodePointerUp}
                            onPointerCancel={handleNodePointerUp}
                            onClick={event => {
                                event.stopPropagation()
                                if (wasDraggedRef.current) {
                                    event.preventDefault()
                                    wasDraggedRef.current = false
                                    return
                                }
                                setSelectedEdgeId(null)
                                onSelectNode(showNodeProperties && selectedNodeId === node.id ? null : node.id)
                            }}
                        >
                            <strong>{node.label}</strong>
                            {(node.collapsedChildrenCount ?? 0) > 0 ? (
                                <span className="kg-graph-node-collapsed">+{node.collapsedChildrenCount}</span>
                            ) : null}
                        </button>
                    ))}
                    {showNodeProperties && selectedNode ? (
                        <div
                            className="kg-node-popover"
                            onClick={event => event.stopPropagation()}
                            style={{
                                left: selectedNode.x,
                                top: selectedNode.y + (selectedNode.height ?? GRAPH_NODE_HEIGHT) + 12,
                        }}
                    >
                        <strong>{nodeTitle}</strong>
                        <div className="kg-property-list">
                            {selectedNodeProperties.map(([key, value]) => (
                                <div key={key} className="kg-property-row">
                                    <span>{formatPropertyName(key)}</span>
                                    <em>{formatPropertyValue(value)}</em>
                                </div>
                            ))}
                        </div>
                    </div>
                    ) : null}
                    </div>
                </div>
            </div>
        </div>
    )
}
