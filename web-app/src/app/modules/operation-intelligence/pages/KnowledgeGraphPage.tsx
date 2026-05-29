import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent } from 'react'
import { FileDown, Network, Radar, RefreshCw, Save, Search, Trash2, Upload } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../platform/ui/primitives/PageHeader'
import SectionCard from '../../../platform/ui/primitives/SectionCard'
import Button from '../../../platform/ui/primitives/Button'
import DetailDialog from '../../../platform/ui/primitives/DetailDialog'
import ResourceCard, {
    ResourceCardAction,
    ResourceCardActionGroup,
    ResourceCardDeleteAction,
    ResourceCardEditAction,
    type ResourceCardMetric,
    type ResourceStatusTone,
} from '../../../platform/ui/primitives/ResourceCard'
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
    generateCallChainSubgraph,
    getCallChainSubgraph,
    deleteGraphEntity,
    exportGraph,
    findHostByIp,
    getEnvironments,
    listCallChainSubgraphs,
    getResourceTree,
    deleteEntities,
    deleteOntology,
    importGraph,
    importOntology,
    listGraphEnvironments,
    listOntologies,
    queryObservations,
    querySubgraph,
    testHostConnection,
    type CallChainSubgraphHistoryItem,
    type CallChainSubgraphResult,
    type EnvironmentInfo,
    type GraphEnvironmentInfo,
    type GraphEntity,
    type GraphExportPackage,
    type GraphOntology,
    type GraphObservation,
    type GraphSnapshot,
    type ResourceTreeGroup,
    updateGraphEntity,
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
const DEFAULT_CALL_CHAIN_RANGE_MINUTES = 15
const DEFAULT_GRAPH_ZOOM = 1
const MIN_GRAPH_ZOOM = 0.5
const MAX_GRAPH_ZOOM = 1.6
const GRAPH_ZOOM_STEP = 0.1
const ENTITY_MANAGEMENT_PAGE_SIZE = 6
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

type KnowledgeGraphTab = 'ontology' | 'entities' | 'entity-management'
type GraphCanvasDensity = 'compact' | 'spacious'
type EntityEditableFieldKind = 'text' | 'number' | 'boolean' | 'json'
type EntityEditableValue = string | boolean
type SubgraphMode = 'entity' | 'call-chain'

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

interface EditableEntityTypeDefinition {
    requiredProperties: string[]
    optionalProperties: string[]
}

interface EntityEditableField {
    key: string
    label: string
    section: 'core' | 'property'
    required: boolean
    kind: EntityEditableFieldKind
    value: unknown
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

function getEntityStatusTone(status?: string): ResourceStatusTone {
    const normalizedStatus = status?.trim().toLowerCase()
    if (!normalizedStatus) {
        return 'neutral'
    }
    if (['normal', 'healthy', 'running', 'success', 'online', 'active'].includes(normalizedStatus)) {
        return 'success'
    }
    if (['warning', 'degraded', 'unknown', 'pending'].includes(normalizedStatus)) {
        return 'warning'
    }
    if (['error', 'failed', 'critical', 'offline', 'down'].includes(normalizedStatus)) {
        return 'danger'
    }
    return 'configured'
}

function getEntityCardSummary(entity: GraphEntity, t: (key: string) => string): string {
    if (entity.displayName && entity.name && entity.displayName !== entity.name) {
        return `${t('operationIntelligence.knowledgeGraph.name')}: ${entity.name}`
    }
    return `${t('operationIntelligence.knowledgeGraph.id')}: ${entity.id}`
}

function getEntityCardMetrics(entity: GraphEntity, t: (key: string) => string): ResourceCardMetric[] {
    const metrics: ResourceCardMetric[] = []
    const addMetric = (label: string, value: unknown) => {
        if (value === undefined || value === null || value === '') {
            return
        }
        metrics.push({
            label,
            value: formatPropertyValue(value),
        })
    }
    addMetric(t('operationIntelligence.knowledgeGraph.id'), entity.id)
    if (entity.name && entity.name !== entity.displayName) {
        addMetric(t('operationIntelligence.knowledgeGraph.name'), entity.name)
    }
    getResourceCardFields(entity).forEach(([key, value]) => {
        if ((key === 'id') || (key === 'status')) {
            return
        }
        addMetric(formatPropertyName(key), value)
    })
    return metrics.slice(0, 3)
}

function inferEditableFieldKind(key: string, value: unknown): EntityEditableFieldKind {
    if (typeof value === 'boolean') {
        return 'boolean'
    }
    if (typeof value === 'number') {
        return 'number'
    }
    if (Array.isArray(value) || (value != null && typeof value === 'object')) {
        return 'json'
    }
    if (/port$/i.test(key)) {
        return 'number'
    }
    return 'text'
}

function editableFieldId(section: 'core' | 'property', key: string): string {
    return `${section}:${key}`
}

function serializeEditableFieldValue(kind: EntityEditableFieldKind, value: unknown): EntityEditableValue {
    if (kind === 'boolean') {
        return value === true
    }
    if (kind === 'json') {
        return value == null ? '' : JSON.stringify(value, null, 2)
    }
    if (value == null) {
        return ''
    }
    return String(value)
}

function parseEditableFieldValue(
    field: EntityEditableField,
    value: EntityEditableValue | undefined,
): { value?: unknown; error?: string } {
    if (field.kind === 'boolean') {
        return { value: value === true }
    }
    const textValue = String(value ?? '').trim()
    if (!textValue) {
        if (field.required) {
            return { error: `${field.label} is required` }
        }
        return { value: undefined }
    }
    if (field.kind === 'number') {
        const numberValue = Number(textValue)
        if (Number.isNaN(numberValue)) {
            return { error: `${field.label} must be a valid number` }
        }
        return { value: numberValue }
    }
    if (field.kind === 'json') {
        try {
            return { value: JSON.parse(textValue) }
        } catch {
            return { error: `${field.label} must be valid JSON` }
        }
    }
    return { value: textValue }
}

function buildEntityEditableFields(
    entity: GraphEntity,
    entityTypeDefinition: EditableEntityTypeDefinition | null,
    t: (key: string, options?: Record<string, unknown>) => string,
): EntityEditableField[] {
    const propertyNames = Array.from(new Set([
        ...entityTypeDefinition?.requiredProperties ?? [],
        ...entityTypeDefinition?.optionalProperties ?? [],
        ...Object.keys(entity.properties ?? {}),
    ]))
    const requiredPropertySet = new Set(entityTypeDefinition?.requiredProperties ?? [])
    return [
        {
            key: 'name',
            label: t('operationIntelligence.knowledgeGraph.name'),
            section: 'core',
            required: false,
            kind: inferEditableFieldKind('name', entity.name),
            value: entity.name,
        },
        {
            key: 'displayName',
            label: t('operationIntelligence.knowledgeGraph.displayName'),
            section: 'core',
            required: false,
            kind: inferEditableFieldKind('displayName', entity.displayName),
            value: entity.displayName,
        },
        {
            key: 'status',
            label: t('operationIntelligence.knowledgeGraph.status'),
            section: 'core',
            required: false,
            kind: inferEditableFieldKind('status', entity.status),
            value: entity.status,
        },
        ...propertyNames.map(propertyName => ({
            key: propertyName,
            label: formatPropertyName(propertyName),
            section: 'property' as const,
            required: requiredPropertySet.has(propertyName),
            kind: inferEditableFieldKind(propertyName, entity.properties?.[propertyName]),
            value: entity.properties?.[propertyName],
        })),
    ]
}

function createEntityDraft(fields: EntityEditableField[]): Record<string, EntityEditableValue> {
    return fields.reduce<Record<string, EntityEditableValue>>((result, field) => {
        result[editableFieldId(field.section, field.key)] = serializeEditableFieldValue(field.kind, field.value)
        return result
    }, {})
}

function buildUpdatedGraphEntity(
    entity: GraphEntity,
    fields: EntityEditableField[],
    draft: Record<string, EntityEditableValue>,
): { entity?: GraphEntity; error?: string } {
    const properties: Record<string, unknown> = {}
    let name = entity.name
    let displayName = entity.displayName
    let status = entity.status
    for (const field of fields) {
        const parsed = parseEditableFieldValue(field, draft[editableFieldId(field.section, field.key)])
        if (parsed.error) {
            return { error: parsed.error }
        }
        if (field.section === 'core') {
            if (field.key === 'name') {
                name = parsed.value as string | undefined
            } else if (field.key === 'displayName') {
                displayName = parsed.value as string | undefined
            } else if (field.key === 'status') {
                status = parsed.value as string | undefined
            }
            continue
        }
        if (parsed.value !== undefined) {
            properties[field.key] = parsed.value
        }
    }
    return {
        entity: {
            id: entity.id,
            type: entity.type,
            name,
            displayName,
            status,
            properties,
        },
    }
}

function updateSnapshotEntity(snapshot: GraphSnapshot | undefined, updatedEntity: GraphEntity): GraphSnapshot | undefined {
    if (!snapshot) {
        return snapshot
    }
    return {
        ...snapshot,
        entities: snapshot.entities.map(entity => entity.id === updatedEntity.id ? updatedEntity : entity),
    }
}

function removeSnapshotEntity(snapshot: GraphSnapshot | undefined, entityId: string): GraphSnapshot | undefined {
    if (!snapshot) {
        return snapshot
    }
    return {
        ...snapshot,
        entities: snapshot.entities.filter(entity => entity.id !== entityId),
        relations: snapshot.relations.filter(relation => relation.from !== entityId && relation.to !== entityId),
        observations: snapshot.observations.filter(observation => observation.entityId !== entityId),
    }
}

function updateResourceGroupsEntity(groups: ResourceTreeGroup[], updatedEntity: GraphEntity): ResourceTreeGroup[] {
    return groups.map(group => ({
        ...group,
        children: group.children.map(entity => entity.id === updatedEntity.id
            ? {
                ...entity,
                name: updatedEntity.name,
                displayName: updatedEntity.displayName,
                status: updatedEntity.status,
            }
            : entity),
    }))
}

function removeResourceGroupsEntity(groups: ResourceTreeGroup[], entityId: string): ResourceTreeGroup[] {
    return groups
        .map(group => {
            const nextChildren = group.children.filter(entity => entity.id !== entityId)
            return {
                ...group,
                count: nextChildren.length,
                children: nextChildren,
            }
        })
        .filter(group => group.children.length > 0)
}

function resolveHostConnectionIp(entity: GraphEntity): string | null {
    const properties = entity.properties ?? {}
    const candidates = [
        properties.hostIp,
        properties.sshIp,
        properties.ip,
        properties.businessIp,
        properties.businessAddress,
        entity.name,
    ]
    return candidates.find(candidate => typeof candidate === 'string' && candidate.trim()) as string | undefined ?? null
}

function canTestEntityConnection(entityType: string, allowedEntityTypes: ReadonlySet<string>): boolean {
    return allowedEntityTypes.has(entityType)
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

interface CallChainTimeRangeInput {
    startTimeLocal: string
    endTimeLocal: string
}

interface CallChainDateTimeDraft {
    startDateValue: string
    startTimeValue: string
    endDateValue: string
    endTimeValue: string
}

function roundDateToMinute(input: Date): Date {
    const rounded = new Date(input)
    rounded.setSeconds(0, 0)
    return rounded
}

function toDateTimeLocalValue(input: Date): string {
    const year = input.getFullYear()
    const month = String(input.getMonth() + 1).padStart(2, '0')
    const day = String(input.getDate()).padStart(2, '0')
    const hours = String(input.getHours()).padStart(2, '0')
    const minutes = String(input.getMinutes()).padStart(2, '0')
    return `${year}-${month}-${day}T${hours}:${minutes}`
}

function buildDefaultCallChainTimeRange(): CallChainTimeRangeInput {
    const endTime = roundDateToMinute(new Date())
    const startTime = new Date(endTime.getTime() - (DEFAULT_CALL_CHAIN_RANGE_MINUTES * 60 * 1000))
    return {
        startTimeLocal: toDateTimeLocalValue(startTime),
        endTimeLocal: toDateTimeLocalValue(endTime),
    }
}

function parseDateTimeLocalValue(value: string): number | null {
    if (!value) {
        return null
    }
    const timestamp = new Date(value).getTime()
    return Number.isNaN(timestamp) ? null : timestamp
}

function formatLocalDateTimeDisplay(value: string, locale?: string): string {
    const timestamp = parseDateTimeLocalValue(value)
    if (timestamp === null) {
        return value
    }
    return new Intl.DateTimeFormat(locale, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    }).format(new Date(timestamp))
}

function formatHistoryTimestamp(value: string, locale?: string): string {
    const timestamp = Date.parse(value)
    if (Number.isNaN(timestamp)) {
        return value
    }
    return new Intl.DateTimeFormat(locale, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    }).format(new Date(timestamp))
}

function splitDateTimeLocalValue(value: string): { dateValue: string; timeValue: string } {
    const [dateValue = '', timeValue = ''] = value.split('T')
    return {
        dateValue,
        timeValue: timeValue.slice(0, 5),
    }
}

function mergeDateAndTime(dateValue: string, timeValue: string): string | null {
    if (!dateValue || !timeValue) {
        return null
    }
    const mergedValue = `${dateValue}T${timeValue}`
    return parseDateTimeLocalValue(mergedValue) === null ? null : mergedValue
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
    const [callChainMenuId, setCallChainMenuId] = useState('')
    const [callChainSolutionType, setCallChainSolutionType] = useState('')
    const [callChainTimeRange, setCallChainTimeRange] = useState<CallChainTimeRangeInput>(buildDefaultCallChainTimeRange)
    const [callChainDateTimeDraft, setCallChainDateTimeDraft] = useState<CallChainDateTimeDraft | null>(null)
    const [callChainHistoryItems, setCallChainHistoryItems] = useState<CallChainSubgraphHistoryItem[]>([])
    const [selectedCallChainHistoryId, setSelectedCallChainHistoryId] = useState('')
    const [resourceSearch, setResourceSearch] = useState('')
    const [entityManagementSearch, setEntityManagementSearch] = useState('')
    const [entityManagementPage, setEntityManagementPage] = useState(1)
    const [selectedResourceTreeItem, setSelectedResourceTreeItem] = useState<ResourceTreeSelection | null>(null)
    const [subgraphUpstreamHops, setSubgraphUpstreamHops] = useState(DEFAULT_SUBGRAPH_UPSTREAM_HOPS)
    const [subgraphDownstreamHops, setSubgraphDownstreamHops] = useState(DEFAULT_SUBGRAPH_DOWNSTREAM_HOPS)
    const [environments, setEnvironments] = useState<GraphEnvironmentInfo[]>([])
    const [resourceGroups, setResourceGroups] = useState<ResourceTreeGroup[]>([])
    const [observations, setObservations] = useState<GraphObservation[]>([])
    const [subgraph, setSubgraph] = useState<GraphSnapshot | null>(null)
    const [subgraphMode, setSubgraphMode] = useState<SubgraphMode | null>(null)
    const [callChainSubgraphResult, setCallChainSubgraphResult] = useState<CallChainSubgraphResult | null>(null)
    const [exportPackage, setExportPackage] = useState<GraphExportPackage | null>(null)
    const [activeGraphTab, setActiveGraphTab] = useState<KnowledgeGraphTab>('ontology')
    const [environmentInfos, setEnvironmentInfos] = useState<EnvironmentInfo[]>([])
    const [selectedOntologyNodeId, setSelectedOntologyNodeId] = useState<string | null>(null)
    const [selectedEntityNodeId, setSelectedEntityNodeId] = useState<string | null>(null)
    const [expandedEntityGraphNodeIds, setExpandedEntityGraphNodeIds] = useState<Set<string>>(new Set())
    const [expandedResourceGroupIds, setExpandedResourceGroupIds] = useState<Set<string>>(new Set())
    const [expandedResourceEntityIds, setExpandedResourceEntityIds] = useState<Set<string>>(new Set())
    const [selectedResourceEntity, setSelectedResourceEntity] = useState<GraphEntity | null>(null)
    const [editingEntityId, setEditingEntityId] = useState<string | null>(null)
    const [entityDraftValues, setEntityDraftValues] = useState<Record<string, EntityEditableValue>>({})
    const [entityHostTestResults, setEntityHostTestResults] = useState<Record<string, { ok: boolean; msg: string }>>({})
    const [testingEntityId, setTestingEntityId] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)
    const [savingEntity, setSavingEntity] = useState(false)
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
    const selectedEnvironmentInfo = useMemo(() => {
        return environmentInfos.find(environment => environment.envCode === envCode) ?? null
    }, [envCode, environmentInfos])
    const graphTabs: Array<{ key: KnowledgeGraphTab; label: string }> = [
        { key: 'ontology', label: t('operationIntelligence.knowledgeGraph.ontology') },
        { key: 'entities', label: t('operationIntelligence.knowledgeGraph.entities') },
        { key: 'entity-management', label: t('operationIntelligence.knowledgeGraph.entityManagement') },
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
    const entityTypeDefinitions = useMemo(() => {
        return (selectedOntology?.entityTypes ?? []).reduce<Record<string, EditableEntityTypeDefinition>>((result, entityType) => {
            result[entityType.type] = {
                requiredProperties: entityType.requiredProperties ?? [],
                optionalProperties: entityType.optionalProperties ?? [],
            }
            return result
        }, {})
    }, [selectedOntology])
    const resourceTreeHierarchyRules = runtime.OPERATION_INTELLIGENCE_KNOWLEDGE_GRAPH_RESOURCE_TREE_HIERARCHY_RULES
    const testConnectionEntityTypeSet = useMemo(() => {
        return new Set(runtime.OPERATION_INTELLIGENCE_KNOWLEDGE_GRAPH_TEST_CONNECTION_ENTITY_TYPES)
    }, [])
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
    const normalizedEntityManagementSearch = entityManagementSearch.trim().toLowerCase()
    const filteredEntityManagementCards = useMemo(() => {
        if (!normalizedEntityManagementSearch) {
            return resourceDetailEntities
        }
        return resourceDetailEntities.filter(entity => getEntitySearchText(entity).includes(normalizedEntityManagementSearch))
    }, [normalizedEntityManagementSearch, resourceDetailEntities])
    const entityManagementTotalPages = Math.max(1, Math.ceil(filteredEntityManagementCards.length / ENTITY_MANAGEMENT_PAGE_SIZE))
    const safeEntityManagementPage = Math.min(entityManagementPage, entityManagementTotalPages)
    const paginatedEntityManagementCards = useMemo(() => {
        const startIndex = (safeEntityManagementPage - 1) * ENTITY_MANAGEMENT_PAGE_SIZE
        return filteredEntityManagementCards.slice(startIndex, startIndex + ENTITY_MANAGEMENT_PAGE_SIZE)
    }, [filteredEntityManagementCards, safeEntityManagementPage])
    const editingEntity = useMemo(() => {
        if (!editingEntityId) {
            return null
        }
        return snapshot?.entities.find(entity => entity.id === editingEntityId)
            ?? resourceDetailEntities.find(entity => entity.id === editingEntityId)
            ?? null
    }, [editingEntityId, resourceDetailEntities, snapshot])
    const editingEntityFields = useMemo(() => {
        if (!editingEntity) {
            return []
        }
        return buildEntityEditableFields(editingEntity, entityTypeDefinitions[editingEntity.type] ?? null, t)
    }, [editingEntity, entityTypeDefinitions, t])
    const graphTitle = useMemo(() => {
        if (subgraphMode === 'call-chain') {
            return t('operationIntelligence.knowledgeGraph.callChainSubgraph')
        }
        return subgraph ? t('operationIntelligence.knowledgeGraph.subgraph') : t('operationIntelligence.knowledgeGraph.entityGraph')
    }, [subgraph, subgraphMode, t])
    const graphSubtitle = useMemo(() => {
        if (subgraphMode === 'call-chain') {
            return t('operationIntelligence.knowledgeGraph.callChainSubgraphSubtitle', {
                menuId: callChainSubgraphResult?.menuId ?? (callChainMenuId.trim() || '-'),
                flows: callChainSubgraphResult?.summary.flowCount ?? 0,
                entities: totalEntities,
                relations: totalRelations,
                observations: totalObservations,
                resourceEntities: callChainSubgraphResult?.summary.resourceEntityCount ?? 0,
                resourceRelations: callChainSubgraphResult?.summary.resourceRelationCount ?? 0,
                matchedServices: callChainSubgraphResult?.summary.matchedServiceCount ?? 0,
                unmatchedServices: callChainSubgraphResult?.summary.unmatchedServiceCount ?? 0,
            })
        }
        if (subgraph) {
            return t('operationIntelligence.knowledgeGraph.subgraphSubtitle', {
                entity: selectedEntity ? toDisplayName(selectedEntity) : entityId,
                upstreamHops: subgraphUpstreamHops,
                downstreamHops: subgraphDownstreamHops,
                entities: totalEntities,
                relations: totalRelations,
                observations: totalObservations,
            })
        }
        return t('operationIntelligence.knowledgeGraph.entityGraphSubtitle', {
            entities: totalEntities,
            relations: totalRelations,
            observations: totalObservations,
        })
    }, [
        callChainMenuId,
        callChainSubgraphResult,
        entityId,
        selectedEntity,
        subgraph,
        subgraphDownstreamHops,
        subgraphMode,
        subgraphUpstreamHops,
        t,
        totalEntities,
        totalObservations,
        totalRelations,
    ])

    const alertApiError = useCallback((err: unknown) => {
        showToast('error', err instanceof Error ? err.message : t('operationIntelligence.loadFailed'))
    }, [showToast, t])

    const reloadCallChainSubgraphHistory = useCallback(async (
        targetOntologyId = ontologyId,
        targetEnvCode = envCode,
        options?: { silent?: boolean },
    ) => {
        if (!targetOntologyId || !targetEnvCode) {
            setCallChainHistoryItems([])
            setSelectedCallChainHistoryId('')
            return []
        }
        try {
            const response = await listCallChainSubgraphs(targetOntologyId, targetEnvCode, userId)
            setCallChainHistoryItems(response.result)
            setSelectedCallChainHistoryId(previous => response.result.some(item => item.subgraphId === previous) ? previous : '')
            return response.result
        } catch (err) {
            setCallChainHistoryItems([])
            setSelectedCallChainHistoryId('')
            if (!options?.silent) {
                alertApiError(err)
            }
            return []
        }
    }, [alertApiError, envCode, ontologyId, userId])

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
            setSubgraphMode(null)
            setCallChainSubgraphResult(null)
            setSelectedEntityNodeId(null)
            setSelectedResourceEntity(null)
            setSelectedResourceTreeItem(null)
            setEditingEntityId(null)
            setEntityDraftValues({})
            setExpandedEntityGraphNodeIds(new Set())
            setExpandedResourceGroupIds(new Set())
            setExpandedResourceEntityIds(new Set())
            void reloadCallChainSubgraphHistory(targetOntologyId, targetEnvCode, { silent: true })
            return true
        } catch (err) {
            if (!options?.silent) {
                alertApiError(err)
            }
            return false
        } finally {
            setLoading(false)
        }
    }, [alertApiError, envCode, ontologyId, reloadCallChainSubgraphHistory, userId])

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
        setSubgraphMode(null)
        setCallChainSubgraphResult(null)
        setExportPackage(null)
        setSelectedOntologyNodeId(null)
        setSelectedEntityNodeId(null)
        setSelectedResourceEntity(null)
        setSelectedResourceTreeItem(null)
        setEditingEntityId(null)
        setEntityDraftValues({})
        setExpandedEntityGraphNodeIds(new Set())
        setExpandedResourceGroupIds(new Set())
        setExpandedResourceEntityIds(new Set())
        setCallChainHistoryItems([])
        setSelectedCallChainHistoryId('')
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
        setEntityManagementPage(1)
    }, [entityManagementSearch, resourceDetailTitle])

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

    useEffect(() => {
        let isMounted = true
        getEnvironments(userId)
            .then(response => {
                if (isMounted) {
                    setEnvironmentInfos(response.results)
                }
            })
            .catch(() => {
                if (isMounted) {
                    setEnvironmentInfos([])
                }
            })
        return () => {
            isMounted = false
        }
    }, [userId])

    useEffect(() => {
        void reloadCallChainSubgraphHistory(ontologyId, envCode, { silent: true })
    }, [envCode, ontologyId, reloadCallChainSubgraphHistory])

    useEffect(() => {
        setCallChainSolutionType(selectedEnvironmentInfo?.agentSolutionType ?? '')
    }, [selectedEnvironmentInfo])

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
            setSubgraphMode('entity')
            setCallChainSubgraphResult(null)
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

    const handleGenerateCallChainSubgraph = async () => {
        const normalizedMenuId = callChainMenuId.trim()
        const normalizedSolutionType = callChainSolutionType.trim()
        if (!normalizedMenuId) {
            showToast('warning', t('operationIntelligence.knowledgeGraph.callChainMenuIdRequired'))
            return
        }
        if (!normalizedSolutionType) {
            showToast('warning', t('operationIntelligence.knowledgeGraph.callChainSolutionTypeRequired'))
            return
        }
        const startTime = parseDateTimeLocalValue(callChainTimeRange.startTimeLocal)
        const endTime = parseDateTimeLocalValue(callChainTimeRange.endTimeLocal)
        if (startTime === null || endTime === null || endTime <= startTime) {
            showToast('warning', t('operationIntelligence.knowledgeGraph.callChainTimeRangeInvalid'))
            return
        }
        setLoading(true)
        try {
            const response = await generateCallChainSubgraph({
                menuId: normalizedMenuId,
                envCode,
                solutionType: normalizedSolutionType,
                ontologyId,
                startTime,
                endTime,
            }, userId)
            setSubgraph(response.result.graph)
            setSubgraphMode('call-chain')
            setCallChainSubgraphResult(response.result)
            setObservations(response.result.graph.observations ?? [])
            setSelectedEntityNodeId(null)
            setExpandedEntityGraphNodeIds(new Set())
            setSelectedCallChainHistoryId(response.result.subgraphId)
            await reloadCallChainSubgraphHistory(ontologyId, envCode, { silent: true })
            showToast('success', t('operationIntelligence.knowledgeGraph.callChainSubgraphLoaded', {
                menuId: normalizedMenuId,
            }))
        } catch (err) {
            alertApiError(err)
        } finally {
            setLoading(false)
        }
    }

    const openCallChainDateTimeDialog = () => {
        const { dateValue: startDateValue, timeValue: startTimeValue } = splitDateTimeLocalValue(callChainTimeRange.startTimeLocal)
        const { dateValue: endDateValue, timeValue: endTimeValue } = splitDateTimeLocalValue(callChainTimeRange.endTimeLocal)
        setCallChainDateTimeDraft({
            startDateValue,
            startTimeValue,
            endDateValue,
            endTimeValue,
        })
    }

    const handleCancelCallChainDateTimeDialog = () => {
        setCallChainDateTimeDraft(null)
    }

    const handleConfirmCallChainDateTimeDialog = () => {
        if (!callChainDateTimeDraft) {
            return
        }
        const nextStartTimeLocal = mergeDateAndTime(callChainDateTimeDraft.startDateValue, callChainDateTimeDraft.startTimeValue)
        const nextEndTimeLocal = mergeDateAndTime(callChainDateTimeDraft.endDateValue, callChainDateTimeDraft.endTimeValue)
        if (!nextStartTimeLocal || !nextEndTimeLocal) {
            showToast('warning', t('operationIntelligence.knowledgeGraph.callChainDateTimeRequired'))
            return
        }
        const nextStartTime = parseDateTimeLocalValue(nextStartTimeLocal)
        const nextEndTime = parseDateTimeLocalValue(nextEndTimeLocal)
        if (nextStartTime === null || nextEndTime === null || nextEndTime <= nextStartTime) {
            showToast('warning', t('operationIntelligence.knowledgeGraph.callChainTimeRangeInvalid'))
            return
        }
        setCallChainTimeRange({
            startTimeLocal: nextStartTimeLocal,
            endTimeLocal: nextEndTimeLocal,
        })
        setCallChainDateTimeDraft(null)
    }

    const handleResetSubgraphView = () => {
        setSubgraph(null)
        setSubgraphMode(null)
        setCallChainSubgraphResult(null)
        setObservations([])
        setSelectedEntityNodeId(null)
        setExpandedEntityGraphNodeIds(new Set())
        setEntityQuery('')
        setSelectedCallChainHistoryId('')
    }

    const handleSelectCallChainHistory = async (subgraphId: string) => {
        setSelectedCallChainHistoryId(subgraphId)
        if (!subgraphId) {
            return
        }
        setLoading(true)
        try {
            const response = await getCallChainSubgraph(subgraphId, userId)
            setSubgraph(response.result.graph)
            setSubgraphMode('call-chain')
            setCallChainSubgraphResult(response.result)
            setCallChainMenuId(response.result.menuId)
            setCallChainSolutionType(response.result.solutionType)
            setObservations(response.result.graph.observations ?? [])
            setSelectedEntityNodeId(null)
            setExpandedEntityGraphNodeIds(new Set())
            showToast('success', t('operationIntelligence.knowledgeGraph.callChainHistoryLoaded', {
                menuId: response.result.menuId,
            }))
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

    const handleEntityDraftValueChange = (fieldId: string, value: EntityEditableValue) => {
        setEntityDraftValues(previous => ({
            ...previous,
            [fieldId]: value,
        }))
    }

    const handleStartEntityEdit = (entity: GraphEntity) => {
        const fullEntity = snapshot?.entities.find(item => item.id === entity.id) ?? entity
        const fields = buildEntityEditableFields(fullEntity, entityTypeDefinitions[fullEntity.type] ?? null, t)
        setSelectedResourceEntity(fullEntity)
        setEditingEntityId(fullEntity.id)
        setEntityDraftValues(createEntityDraft(fields))
    }

    const handleCancelEntityEdit = () => {
        setEditingEntityId(null)
        setEntityDraftValues({})
    }

    const handleSaveEntity = async (entity: GraphEntity) => {
        const fullEntity = snapshot?.entities.find(item => item.id === entity.id) ?? entity
        const fields = buildEntityEditableFields(fullEntity, entityTypeDefinitions[fullEntity.type] ?? null, t)
        const nextEntity = buildUpdatedGraphEntity(fullEntity, fields, entityDraftValues)
        if (!nextEntity.entity) {
            showToast('error', nextEntity.error ?? t('operationIntelligence.knowledgeGraph.entityUpdateFailed'))
            return
        }
        setSavingEntity(true)
        try {
            const response = await updateGraphEntity(envCode, fullEntity.id, nextEntity.entity, userId, ontologyId)
            setExportPackage(previous => {
                if (!previous) {
                    return previous
                }
                return {
                    ...previous,
                    snapshot: updateSnapshotEntity(previous.snapshot, response.result) ?? previous.snapshot,
                }
            })
            setResourceGroups(previous => updateResourceGroupsEntity(previous, response.result))
            setSelectedResourceEntity(response.result)
            setEditingEntityId(null)
            setEntityDraftValues({})
            showToast('success', t('operationIntelligence.knowledgeGraph.entityUpdated'))
        } catch (err) {
            alertApiError(err)
        } finally {
            setSavingEntity(false)
        }
    }

    const handleDeleteEntity = async (entity: GraphEntity) => {
        const fullEntity = snapshot?.entities.find(item => item.id === entity.id) ?? entity
        const confirmed = await requestConfirm({
            message: t('operationIntelligence.knowledgeGraph.confirmDeleteEntity', {
                entity: toDisplayName(fullEntity),
            }),
            variant: 'danger',
            confirmLabel: t('common.delete'),
        })
        if (!confirmed) {
            return
        }
        setSavingEntity(true)
        try {
            await deleteGraphEntity(envCode, fullEntity.id, userId, ontologyId)
            setExportPackage(previous => {
                if (!previous) {
                    return previous
                }
                return {
                    ...previous,
                    snapshot: removeSnapshotEntity(previous.snapshot, fullEntity.id) ?? previous.snapshot,
                }
            })
            setResourceGroups(previous => removeResourceGroupsEntity(previous, fullEntity.id))
            setSelectedResourceEntity(previous => previous?.id === fullEntity.id ? null : previous)
            setSelectedResourceTreeItem(previous => previous?.kind === 'entity' && previous.entityId === fullEntity.id ? null : previous)
            setEditingEntityId(previous => previous === fullEntity.id ? null : previous)
            setEntityDraftValues({})
            setEntityHostTestResults(previous => {
                const next = { ...previous }
                delete next[fullEntity.id]
                return next
            })
            showToast('success', t('operationIntelligence.knowledgeGraph.entityDeleted'))
        } catch (err) {
            alertApiError(err)
        } finally {
            setSavingEntity(false)
        }
    }

    const handleTestHostEntity = async (entity: GraphEntity) => {
        const hostIp = resolveHostConnectionIp(entity)
        if (!hostIp) {
            showToast('warning', t('operationIntelligence.knowledgeGraph.hostConnectionAddressMissing'))
            return
        }
        setTestingEntityId(entity.id)
        setEntityHostTestResults(previous => {
            const next = { ...previous }
            delete next[entity.id]
            return next
        })
        try {
            const host = await findHostByIp(hostIp, userId)
            const result = await testHostConnection(host.id, userId)
            const nextResult = result.success
                ? {
                    ok: true,
                    msg: t('remoteDiagnosis.hosts.testSuccess', { latency: `${result.latency ?? ''}ms` }),
                }
                : {
                    ok: false,
                    msg: t('remoteDiagnosis.hosts.testFailed', { error: result.message || 'Unknown' }),
                }
            setEntityHostTestResults(previous => ({
                ...previous,
                [entity.id]: nextResult,
            }))
        } catch (err) {
            setEntityHostTestResults(previous => ({
                ...previous,
                [entity.id]: {
                    ok: false,
                    msg: t('remoteDiagnosis.hosts.testFailed', { error: err instanceof Error ? err.message : 'Unknown' }),
                },
            }))
        } finally {
            setTestingEntityId(null)
        }
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
                ) : activeGraphTab === 'entities' ? (
                    <div className="kg-tab-page">
                        <div className="kg-entity-control-panel">
                            <div className="kg-entity-query-row kg-entity-query-row-primary">
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
                                <label className="kg-field kg-entity-query-field">
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
                                            onClick={handleResetSubgraphView}
                                            disabled={loading}
                                        >
                                            {t('operationIntelligence.knowledgeGraph.showFullGraph')}
                                        </Button>
                                    ) : null}
                                </div>
                            </div>
                            <div className="kg-entity-query-row kg-entity-query-row-call-chain">
                                <label className="kg-field kg-call-chain-menu-field">
                                    <span>{t('operationIntelligence.knowledgeGraph.callChainMenuId')}</span>
                                    <input
                                        value={callChainMenuId}
                                        onChange={event => setCallChainMenuId(event.target.value)}
                                        onKeyDown={event => {
                                            if (event.key === 'Enter') {
                                                void handleGenerateCallChainSubgraph()
                                            }
                                        }}
                                        placeholder={t('operationIntelligence.knowledgeGraph.callChainMenuIdPlaceholder')}
                                    />
                                </label>
                                <label className="kg-field kg-call-chain-solution-field">
                                    <span>{t('operationIntelligence.knowledgeGraph.callChainSolutionType')}</span>
                                    <input
                                        value={callChainSolutionType}
                                        onChange={event => setCallChainSolutionType(event.target.value)}
                                        onKeyDown={event => {
                                            if (event.key === 'Enter') {
                                                void handleGenerateCallChainSubgraph()
                                            }
                                        }}
                                        placeholder={t('operationIntelligence.knowledgeGraph.callChainSolutionTypePlaceholder')}
                                    />
                                </label>
                                <label className="kg-field kg-call-chain-time-field">
                                    <span>{t('operationIntelligence.knowledgeGraph.callChainTimeRange')}</span>
                                    <button
                                        type="button"
                                        className="kg-datetime-trigger kg-datetime-range-trigger"
                                        onClick={openCallChainDateTimeDialog}
                                    >
                                        {`${formatLocalDateTimeDisplay(callChainTimeRange.startTimeLocal)} ~ ${formatLocalDateTimeDisplay(callChainTimeRange.endTimeLocal)}`}
                                    </button>
                                </label>
                                <div className="kg-query-actions">
                                    <Button leadingIcon={<Radar size={16} />} onClick={handleGenerateCallChainSubgraph} disabled={loading}>
                                        {t('operationIntelligence.knowledgeGraph.generateCallChainSubgraph')}
                                    </Button>
                                </div>
                            </div>
                            <div className="kg-tab-actions kg-entity-actions">
                                <label className="kg-field kg-call-chain-history-inline-field">
                                    <span>{t('operationIntelligence.knowledgeGraph.callChainHistorySelect')}</span>
                                    <select
                                        value={selectedCallChainHistoryId}
                                        onChange={event => void handleSelectCallChainHistory(event.target.value)}
                                        disabled={callChainHistoryItems.length === 0 || loading}
                                    >
                                        <option value="">
                                            {callChainHistoryItems.length === 0
                                                ? t('operationIntelligence.knowledgeGraph.callChainHistoryEmpty')
                                                : t('operationIntelligence.knowledgeGraph.callChainHistorySelectPlaceholder')}
                                        </option>
                                        {callChainHistoryItems.map(item => (
                                            <option key={item.subgraphId} value={item.subgraphId}>
                                                {`${item.menuId} | ${formatHistoryTimestamp(item.generatedAt)} | ${item.solutionType}`}
                                            </option>
                                        ))}
                                    </select>
                                </label>
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

                        {callChainDateTimeDraft ? (
                            <DetailDialog
                                title={t('operationIntelligence.knowledgeGraph.callChainTimeRange')}
                                onClose={handleCancelCallChainDateTimeDialog}
                                footer={(
                                    <>
                                        <Button variant="secondary" onClick={handleCancelCallChainDateTimeDialog}>
                                            {t('common.cancel')}
                                        </Button>
                                        <Button variant="primary" onClick={handleConfirmCallChainDateTimeDialog}>
                                            {t('common.confirm')}
                                        </Button>
                                    </>
                                )}
                            >
                                <div className="kg-datetime-dialog-fields">
                                    <label className="kg-field">
                                        <span>{t('operationIntelligence.knowledgeGraph.callChainDate')}</span>
                                        <input
                                            type="date"
                                            value={callChainDateTimeDraft.startDateValue}
                                            onChange={event => setCallChainDateTimeDraft(previous => previous ? {
                                                ...previous,
                                                startDateValue: event.target.value,
                                            } : previous)}
                                        />
                                    </label>
                                    <label className="kg-field">
                                        <span>{t('operationIntelligence.knowledgeGraph.callChainTime')}</span>
                                        <input
                                            type="time"
                                            value={callChainDateTimeDraft.startTimeValue}
                                            onChange={event => setCallChainDateTimeDraft(previous => previous ? {
                                                ...previous,
                                                startTimeValue: event.target.value,
                                            } : previous)}
                                        />
                                    </label>
                                    <label className="kg-field">
                                        <span>{t('operationIntelligence.knowledgeGraph.callChainDate')}</span>
                                        <input
                                            type="date"
                                            value={callChainDateTimeDraft.endDateValue}
                                            onChange={event => setCallChainDateTimeDraft(previous => previous ? {
                                                ...previous,
                                                endDateValue: event.target.value,
                                            } : previous)}
                                        />
                                    </label>
                                    <label className="kg-field">
                                        <span>{t('operationIntelligence.knowledgeGraph.callChainTime')}</span>
                                        <input
                                            type="time"
                                            value={callChainDateTimeDraft.endTimeValue}
                                            onChange={event => setCallChainDateTimeDraft(previous => previous ? {
                                                ...previous,
                                                endTimeValue: event.target.value,
                                            } : previous)}
                                        />
                                    </label>
                                </div>
                            </DetailDialog>
                        ) : null}

                        <GraphCanvas
                            nodes={activeEntityGraph.nodes}
                            edges={activeEntityGraph.edges}
                            selectedNodeId={selectedEntityNodeId}
                            onSelectNode={subgraph ? handleSelectEntityGraphNode : setSelectedEntityNodeId}
                            nodeTitle={t('operationIntelligence.knowledgeGraph.nodeProperties')}
                            title={graphTitle}
                            subtitle={graphSubtitle}
                            density="spacious"
                        />

                        <SectionCard title={subgraphMode === 'call-chain'
                            ? t('operationIntelligence.knowledgeGraph.callChainObservationTrace')
                            : t('operationIntelligence.knowledgeGraph.observationTrace')}>
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
                ) : (
                    <div className="kg-tab-page">
                        <div className="kg-entity-control-panel kg-entity-management-control-panel">
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
                            </div>
                        </div>

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
                                                title={group.name}
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
                                                                                title={childGroup.name}
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
                                <div className="kg-resource-panel-header kg-resource-panel-header-management">
                                    <div>
                                        <h3>{resourceDetailTitle}</h3>
                                        <p>{t('operationIntelligence.knowledgeGraph.resourceInstanceCount', {
                                            count: filteredEntityManagementCards.length,
                                        })}</p>
                                    </div>
                                    <div className="kg-resource-panel-tools">
                                        <ListSearchInput
                                            value={entityManagementSearch}
                                            placeholder={t('operationIntelligence.knowledgeGraph.searchEntityCards')}
                                            onChange={setEntityManagementSearch}
                                        />
                                        {entityManagementSearch ? (
                                            <ListResultsMeta>
                                                {t('common.resultsFound', { count: filteredEntityManagementCards.length })}
                                            </ListResultsMeta>
                                        ) : null}
                                    </div>
                                </div>
                                {paginatedEntityManagementCards.length > 0 ? (
                                    <>
                                        <div className="kg-resource-card-grid">
                                            {paginatedEntityManagementCards.map(entity => {
                                                const fullEntity = snapshot?.entities.find(item => item.id === entity.id) ?? entity
                                                const isSelected = selectedResourceEntity?.id === entity.id
                                                const hostTestResult = entityHostTestResults[entity.id]
                                                return (
                                                    <div
                                                        key={entity.id}
                                                        className={`kg-resource-card-shell ${isSelected ? 'kg-resource-card-selected' : ''}`}
                                                        onClick={() => handleSelectResourceEntity(fullEntity)}
                                                    >
                                                        <ResourceCard
                                                            className="kg-resource-card"
                                                            title={toDisplayName(fullEntity)}
                                                            statusLabel={fullEntity.status}
                                                            statusTone={getEntityStatusTone(fullEntity.status)}
                                                            tags={(
                                                                <div className="resource-card-tags kg-resource-card-tags">
                                                                    <span className="resource-card-tag kg-resource-card-tag">
                                                                        {fullEntity.type}
                                                                    </span>
                                                                </div>
                                                            )}
                                                            summary={(
                                                                <div className="resource-card-summary-stack">
                                                                    <p className="resource-card-summary-text">
                                                                        {getEntityCardSummary(fullEntity, t)}
                                                                    </p>
                                                                    {hostTestResult ? (
                                                                        <div className={`kg-resource-test-result ${hostTestResult.ok ? 'success' : 'error'}`}>
                                                                            {hostTestResult.msg}
                                                                        </div>
                                                                    ) : null}
                                                                </div>
                                                            )}
                                                            metrics={getEntityCardMetrics(fullEntity, t)}
                                                            footer={(
                                                                <ResourceCardActionGroup
                                                                    className="kg-resource-card-actions"
                                                                    onClick={event => event.stopPropagation()}
                                                                >
                                                                    {canTestEntityConnection(fullEntity.type, testConnectionEntityTypeSet) ? (
                                                                        <ResourceCardAction
                                                                            icon={Radar}
                                                                            tone="success"
                                                                            label={testingEntityId === fullEntity.id
                                                                                ? t('remoteDiagnosis.hosts.testing')
                                                                                : t('remoteDiagnosis.hosts.testConnection')}
                                                                            disabled={testingEntityId === fullEntity.id || savingEntity}
                                                                            onClick={() => void handleTestHostEntity(fullEntity)}
                                                                        />
                                                                    ) : null}
                                                                    <ResourceCardEditAction
                                                                        label={t('common.edit')}
                                                                        disabled={savingEntity}
                                                                        onClick={() => handleStartEntityEdit(fullEntity)}
                                                                    />
                                                                    <ResourceCardDeleteAction
                                                                        label={t('common.delete')}
                                                                        disabled={savingEntity}
                                                                        onClick={() => void handleDeleteEntity(fullEntity)}
                                                                    />
                                                                </ResourceCardActionGroup>
                                                            )}
                                                        />
                                                    </div>
                                                )
                                            })}
                                        </div>
                                        {entityManagementTotalPages > 1 ? (
                                            <div className="kg-resource-pagination">
                                                <span className="kg-resource-pagination-info">
                                                    {t('common.showing', {
                                                        start: (safeEntityManagementPage - 1) * ENTITY_MANAGEMENT_PAGE_SIZE + 1,
                                                        end: Math.min(
                                                            safeEntityManagementPage * ENTITY_MANAGEMENT_PAGE_SIZE,
                                                            filteredEntityManagementCards.length,
                                                        ),
                                                        total: filteredEntityManagementCards.length,
                                                    })}
                                                </span>
                                                <div className="kg-resource-pagination-controls">
                                                    <button
                                                        type="button"
                                                        className="kg-resource-pagination-btn"
                                                        disabled={safeEntityManagementPage <= 1}
                                                        onClick={() => setEntityManagementPage(safeEntityManagementPage - 1)}
                                                    >
                                                        {t('common.previousPage')}
                                                    </button>
                                                    <span className="kg-resource-pagination-page">
                                                        {safeEntityManagementPage} / {entityManagementTotalPages}
                                                    </span>
                                                    <button
                                                        type="button"
                                                        className="kg-resource-pagination-btn"
                                                        disabled={safeEntityManagementPage >= entityManagementTotalPages}
                                                        onClick={() => setEntityManagementPage(safeEntityManagementPage + 1)}
                                                    >
                                                        {t('common.nextPage')}
                                                    </button>
                                                </div>
                                            </div>
                                        ) : null}
                                    </>
                                ) : (
                                    <div className="kg-empty-detail">
                                        {resourceDetailEntities.length > 0
                                            ? t('common.noResults')
                                            : t('operationIntelligence.knowledgeGraph.selectResourceHint')}
                                    </div>
                                )}
                            </section>
                        </div>
                        {editingEntity ? (
                            <DetailDialog
                                title={`${t('common.edit')} ${toDisplayName(editingEntity)}`}
                                onClose={handleCancelEntityEdit}
                                variant="wide"
                                className="kg-entity-edit-dialog"
                                bodyClassName="kg-entity-edit-dialog-body"
                                footer={(
                                    <>
                                        <Button tone="quiet" onClick={handleCancelEntityEdit} disabled={savingEntity}>
                                            {t('common.cancel')}
                                        </Button>
                                        <Button
                                            variant="primary"
                                            leadingIcon={<Save size={14} />}
                                            disabled={savingEntity}
                                            onClick={() => void handleSaveEntity(editingEntity)}
                                        >
                                            {t('common.save')}
                                        </Button>
                                    </>
                                )}
                            >
                                <div className="kg-entity-edit-intro">
                                    <div className="kg-entity-edit-summary-card">
                                        <div className="resource-card-tags kg-resource-card-tags">
                                            <span className="resource-card-tag kg-resource-card-tag">{editingEntity.type}</span>
                                            {editingEntity.status ? (
                                                <span className="resource-card-tag kg-resource-card-tag-status">
                                                    {editingEntity.status}
                                                </span>
                                            ) : null}
                                        </div>
                                        <div className="kg-entity-edit-summary-meta">
                                            <div className="kg-resource-card-meta-field">
                                                <span className="kg-resource-card-meta-label">
                                                    {t('operationIntelligence.knowledgeGraph.id')}
                                                </span>
                                                <span className="kg-resource-card-meta-value">{editingEntity.id}</span>
                                            </div>
                                            <div className="kg-resource-card-meta-field">
                                                <span className="kg-resource-card-meta-label">
                                                    {t('operationIntelligence.knowledgeGraph.type')}
                                                </span>
                                                <span className="kg-resource-card-meta-value">{editingEntity.type}</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div className="kg-resource-form-grid">
                                    {editingEntityFields.map(field => {
                                        const draftValue = entityDraftValues[editableFieldId(field.section, field.key)]
                                        return (
                                            <label key={editableFieldId(field.section, field.key)} className="kg-field">
                                                <span>{field.label}{field.required ? ' *' : ''}</span>
                                                {field.kind === 'boolean' ? (
                                                    <select
                                                        value={draftValue === true ? 'true' : 'false'}
                                                        onChange={event => {
                                                            handleEntityDraftValueChange(
                                                                editableFieldId(field.section, field.key),
                                                                event.target.value === 'true',
                                                            )
                                                        }}
                                                    >
                                                        <option value="true">true</option>
                                                        <option value="false">false</option>
                                                    </select>
                                                ) : field.kind === 'json' ? (
                                                    <textarea
                                                        value={String(draftValue ?? '')}
                                                        rows={6}
                                                        onChange={event => {
                                                            handleEntityDraftValueChange(
                                                                editableFieldId(field.section, field.key),
                                                                event.target.value,
                                                            )
                                                        }}
                                                    />
                                                ) : (
                                                    <input
                                                        type={field.kind === 'number' ? 'number' : 'text'}
                                                        value={String(draftValue ?? '')}
                                                        onChange={event => {
                                                            handleEntityDraftValueChange(
                                                                editableFieldId(field.section, field.key),
                                                                event.target.value,
                                                            )
                                                        }}
                                                    />
                                                )}
                                            </label>
                                        )
                                    })}
                                </div>
                            </DetailDialog>
                        ) : null}
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
