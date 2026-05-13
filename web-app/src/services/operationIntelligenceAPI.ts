/**
 * Operation Intelligence API Service
 * Aligned with operation-intelligence QoS REST endpoints (/operation-intelligence/qos/*)
 */

import { runtime } from '../config/runtime'

import type { HealthIndicatorResponse, IndicatorDetailResponse } from '../types/operationIntelligence';

export interface EnvironmentInfo {
  envCode: string
  envName?: string
  agentSolutionType: string
  productTypeName?: string
}

function operationIntelligenceHeaders(userId?: string | null): Record<string, string> {
  const h: Record<string, string> = {
    'Content-Type': 'application/json',
    'x-secret-key': runtime.OPERATION_INTELLIGENCE_SECRET_KEY,
  }
  if (userId) h['x-user-id'] = userId
  return h
}

async function request<T>(endpoint: string, body?: unknown, method = 'POST', userId?: string | null): Promise<T> {
  const url = `${runtime.OPERATION_INTELLIGENCE_SERVICE_URL}${endpoint}`;
  const response = await fetch(url, {
    method,
    headers: operationIntelligenceHeaders(userId),
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getHealthIndicator(envCode: string, startTime: number, endTime: number, userId?: string | null): Promise<HealthIndicatorResponse> {
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

export async function getProductConfigRule(agentSolutionType: string, userId?: string | null): Promise<{ result: unknown }> {
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
