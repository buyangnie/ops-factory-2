/**
 * Operation Intelligence API Service
 * Aligned with gateway proxied operation-intelligence REST endpoints.
 */

import { gatewayHeaders, runtime } from '../config/runtime'

import type { HealthIndicatorResponse, IndicatorDetailResponse } from '../types/operationIntelligence';

export interface EnvironmentInfo {
  envCode: string
  envName?: string
  agentSolutionType: string
  productTypeName?: string
}

export interface GraphEnvironmentInfo {
  envCode: string
  envName?: string
}

export interface GraphEntity {
  id: string
  type: string
  name?: string
  displayName?: string
  status?: string
  properties?: Record<string, unknown>
}

export interface GraphRelation {
  id: string
  type: string
  from: string
  to: string
  properties?: Record<string, unknown>
}

export interface GraphObservation {
  id: string
  entityId: string
  observedAt: string
  category?: string
  name?: string
  severity?: string
  value?: unknown
  unit?: string
  properties?: Record<string, unknown>
}

export interface EntityTypeDefinition {
  type: string
  requiredProperties: string[]
  optionalProperties?: string[]
}

export interface RelationTypeDefinition {
  type: string
  from: string[]
  to: string[]
  layer?: string
}

export interface GraphOntology {
  ontologyId: string
  name?: string
  version?: string
  sourceSystem?: string
  metadata?: Record<string, unknown>
  entityTypes: EntityTypeDefinition[]
  relationTypes: RelationTypeDefinition[]
}

export interface GraphSnapshot {
  formatVersion?: string
  ontologyId?: string
  envCode: string
  snapshotId?: string
  schemaVersion?: string
  sourceSystem?: string | null
  importMode?: string
  generatedAt?: string
  metadata?: Record<string, unknown>
  entities: GraphEntity[]
  relations: GraphRelation[]
  observations: GraphObservation[]
}

export interface GraphExportPackage {
  manifest: Record<string, unknown>
  ontology?: GraphOntology
  schemaDsl: string
  snapshot: GraphSnapshot
}

export interface ResourceTreeGroup {
  id: string
  type: string
  name: string
  count: number
  children: GraphEntity[]
}

export interface RootCauseCandidate {
  entityId: string
  entityType: string
  displayName?: string
  severity: string
  score: number
  evidence: GraphObservation
}

async function request<T>(endpoint: string, body?: unknown, method = 'POST', userId?: string | null): Promise<T> {
  const url = `${runtime.OPERATION_INTELLIGENCE_SERVICE_URL}${endpoint}`;
  const response = await fetch(url, {
    method,
    headers: gatewayHeaders(userId),
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || errorData.error || `HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getHealthIndicator(
  envCode: string,
  startTime: number,
  endTime: number,
  userId?: string | null,
): Promise<HealthIndicatorResponse> {
  return request<HealthIndicatorResponse>('/qos/getHealthIndicator', {
    envCode, startTime, endTime,
  }, 'POST', userId);
}

export async function getResourceIndicatorDetail(envCode: string, startTime: number, endTime: number,
    userId?: string | null): Promise<{ results: Record<string, unknown>[] }> {
  return request<{ results: Record<string, unknown>[] }>('/qos/getResourceIndicatorDetail', {
    envCode, startTime, endTime,
  }, 'POST', userId);
}

export async function getContributionData(envCode: string, startTime: number, endTime: number,
    userId?: string | null): Promise<{ results: { type: string; contribution: number }[] }> {
  return request<{ results: { type: string; contribution: number }[] }>('/qos/getContributionData', {
    envCode, startTime, endTime,
  }, 'POST', userId);
}

export async function getProductConfigRule(
  agentSolutionType: string,
  userId?: string | null,
): Promise<{ result: unknown }> {
  return request<{ result: unknown }>('/qos/getProductConfigRule', {
    agentSolutionType,
  }, 'POST', userId);
}

export async function getEnvironments(userId?: string | null): Promise<{ results: EnvironmentInfo[] }> {
  return request<{ results: EnvironmentInfo[] }>('/qos/getEnvironments', undefined, 'GET', userId);
}

export async function getIndicatorDetail(
  endpoint: string, envCode: string, startTime: number, endTime: number, pageIndex: number, pageSize: number,
  userId?: string | null,
): Promise<IndicatorDetailResponse> {
  return request<IndicatorDetailResponse>(endpoint, {
    envCode, startTime, endTime, pageIndex, pageSize,
  }, 'POST', userId);
}

export async function importGraph(
  snapshot: unknown,
  userId?: string | null,
): Promise<{ result: Record<string, unknown> }> {
  return request<{ result: Record<string, unknown> }>('/graph/admin/import', snapshot, 'POST', userId)
}

export async function importOntology(
  ontology: GraphOntology,
  userId?: string | null,
): Promise<{ result: GraphOntology }> {
  return request<{ result: GraphOntology }>('/graph/ontologies', ontology, 'POST', userId)
}

export async function listOntologies(userId?: string | null): Promise<{ result: GraphOntology[] }> {
  return request<{ result: GraphOntology[] }>('/graph/ontologies', undefined, 'GET', userId)
}

export async function listGraphEnvironments(
  ontologyId: string,
  userId?: string | null,
): Promise<{ result: GraphEnvironmentInfo[] }> {
  return request<{ result: GraphEnvironmentInfo[] }>(
    `/graph/environments?ontologyId=${encodeURIComponent(ontologyId)}`,
    undefined,
    'GET',
    userId,
  )
}

export async function deleteOntology(
  ontologyId: string,
  userId?: string | null,
): Promise<{ result: Record<string, unknown> }> {
  return request<{ result: Record<string, unknown> }>(
    '/graph/admin/delete-ontology',
    { ontologyId },
    'POST',
    userId,
  )
}

export async function deleteEntities(
  ontologyId: string,
  envCode: string,
  userId?: string | null,
): Promise<{ result: Record<string, unknown> }> {
  return request<{ result: Record<string, unknown> }>(
    '/graph/admin/delete-entities',
    { ontologyId, envCode },
    'POST',
    userId,
  )
}

export async function exportGraph(
  envCode: string,
  ontologyId?: string,
  userId?: string | null,
): Promise<{ result: GraphExportPackage }> {
  return request<{ result: GraphExportPackage }>('/graph/admin/export', { ontologyId, envCode }, 'POST', userId)
}

export async function getResourceTree(envCode: string, userId?: string | null, ontologyId?: string): Promise<{
  result: { envCode: string; total: number; roots: ResourceTreeGroup[] }
}> {
  const query = encodeURIComponent(envCode)
  const ontologyQuery = ontologyId ? `&ontologyId=${encodeURIComponent(ontologyId)}` : ''
  return request<{ result: { envCode: string; total: number; roots: ResourceTreeGroup[] } }>(
    `/graph/resources/tree?envCode=${query}${ontologyQuery}`,
    undefined,
    'GET',
    userId,
  )
}

export async function queryObservations(
  envCode: string,
  entityId: string,
  userId?: string | null,
  ontologyId?: string,
): Promise<{
  result: { total: number; results: GraphObservation[] }
}> {
  return request<{ result: { total: number; results: GraphObservation[] } }>(
    '/graph/observations/query',
    { ontologyId, envCode, entityId, severity: 'warning', limit: 20 },
    'POST',
    userId,
  )
}

export async function querySubgraph(
  envCode: string,
  entityId: string,
  upstreamHops: number,
  downstreamHops: number,
  userId?: string | null,
  ontologyId?: string,
): Promise<{ result: GraphSnapshot }> {
  const maxHops = Math.max(upstreamHops, downstreamHops)
  return request<{ result: GraphSnapshot }>(
    '/graph/subgraph',
    { ontologyId, envCode, entityId, upstreamHops, downstreamHops, maxHops },
    'POST',
    userId,
  )
}

export async function getDiagnosisContext(
  envCode: string,
  entityId: string,
  userId?: string | null,
  ontologyId?: string,
): Promise<{
  result: {
    entity: GraphEntity
    subgraph: GraphSnapshot
    observations: GraphObservation[]
    rootCauseCandidates: RootCauseCandidate[]
  }
}> {
  return request<{
    result: {
      entity: GraphEntity
      subgraph: GraphSnapshot
      observations: GraphObservation[]
      rootCauseCandidates: RootCauseCandidate[]
    }
  }>('/graph/diagnosis/context', { ontologyId, envCode, entityId, maxHops: 2 }, 'POST', userId)
}
