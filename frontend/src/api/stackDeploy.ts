/**
 * 多套/多节点部署 API（MVP）
 */

import { http } from './http'

const API_BASE = '/plugins/appstore/stack-deploy'

export interface StackConfig {
  id?: number
  stackId: string
  name?: string
  enabled?: boolean
  mysqlDatabase: string
  redisDb: number
  runtimeBaseDir?: string
  webImage?: string
  frontedImage?: string
  versionTag?: string
  deployFronted?: boolean
  joinNginx?: boolean
  entryNodeId?: string
  domain?: string
  webHostPort?: number
  frontedHostPort?: number
  extraConfig?: string
}

export interface StackTarget {
  id?: number
  stackId?: string
  nodeId: string
  enabled?: boolean
  roles?: string
  webPort?: number
  frontedPort?: number
  lastDeployedVersion?: string
  healthStatus?: string
  lastHealthCheckTime?: number
}

export interface StackDeployment {
  id?: number
  deploymentId: string
  stackId: string
  requestedVersion?: string
  mode?: string
  strategy?: string
  deployFronted?: boolean
  joinNginx?: boolean
  status?: string
  message?: string
  startedAt?: number
  endedAt?: number
}

export interface StackDeploymentLog {
  id?: number
  deploymentId: string
  nodeId?: string
  step: string
  status: string
  output?: string
  timestamp: number
}

export const stackDeployApi = {
  // stacks
  listStacks(): Promise<StackConfig[]> {
    return http.get<StackConfig[]>(`${API_BASE}/stacks`)
  },
  getStack(stackId: string): Promise<StackConfig> {
    return http.get<StackConfig>(`${API_BASE}/stacks/${stackId}`)
  },
  createStack(data: StackConfig): Promise<StackConfig> {
    return http.post<StackConfig>(`${API_BASE}/stacks`, data)
  },
  updateStack(stackId: string, data: StackConfig): Promise<StackConfig> {
    return http.put<StackConfig>(`${API_BASE}/stacks/${stackId}`, data)
  },
  deleteStack(stackId: string): Promise<void> {
    return http.delete<void>(`${API_BASE}/stacks/${stackId}`)
  },

  // targets
  listTargets(stackId: string): Promise<StackTarget[]> {
    return http.get<StackTarget[]>(`${API_BASE}/stacks/${stackId}/targets`)
  },
  upsertTarget(stackId: string, data: StackTarget): Promise<StackTarget> {
    return http.post<StackTarget>(`${API_BASE}/stacks/${stackId}/targets`, data)
  },
  deleteTarget(stackId: string, nodeId: string): Promise<void> {
    return http.delete<void>(`${API_BASE}/stacks/${stackId}/targets/${nodeId}`)
  },

  // deployments
  createDeployment(stackId: string, data?: Partial<{
    requestedVersion: string
    mode: string
    strategy: string
    deployFronted: boolean
    joinNginx: boolean
  }>): Promise<StackDeployment> {
    return http.post<StackDeployment>(`${API_BASE}/stacks/${stackId}/deployments`, data || {})
  },
  getDeployment(deploymentId: string): Promise<StackDeployment> {
    return http.get<StackDeployment>(`${API_BASE}/deployments/${deploymentId}`)
  },
  listDeploymentLogs(deploymentId: string, limit = 200): Promise<StackDeploymentLog[]> {
    return http.get<StackDeploymentLog[]>(`${API_BASE}/deployments/${deploymentId}/logs`, { limit })
  }
}

