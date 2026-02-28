/**
 * 节点管理 API
 */

import { http } from './http'

const API_BASE = '/plugins/appstore'

export type NodeType = 'local' | 'ssh' | 'docker-api'

export interface NodeInfo {
  nodeId: string
  name?: string
  type: NodeType
  description?: string
  enabled: boolean
  createdAt?: number
  updatedAt?: number
  config: any
}

export const nodesApi = {
  list(): Promise<NodeInfo[]> {
    return http.get<NodeInfo[]>(`${API_BASE}/nodes`)
  },

  get(nodeId: string): Promise<NodeInfo> {
    return http.get<NodeInfo>(`${API_BASE}/nodes/${nodeId}`)
  },

  save(node: NodeInfo): Promise<NodeInfo> {
    return http.post<NodeInfo>(`${API_BASE}/nodes`, node)
  },

  delete(nodeId: string): Promise<void> {
    return http.delete<void>(`${API_BASE}/nodes/${nodeId}`)
  },

  test(nodeId: string): Promise<boolean> {
    return http.post<boolean>(`${API_BASE}/nodes/${nodeId}/test`)
  }
}

