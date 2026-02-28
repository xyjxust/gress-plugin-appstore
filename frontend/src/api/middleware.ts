/**
 * 中间件管理 API
 */

import { http } from './http'
import type { MiddlewareInfo, MiddlewareInstallResult, HealthCheckResult } from '../types/middleware'
import type { PageResult } from '../types/application'

const API_BASE = '/plugins/appstore'

/**
 * 远程中间件信息（继承自 Application，用于远程列表）
 */
export interface RemoteMiddlewareInfo {
  id?: number
  pluginId: string
  applicationName?: string
  pluginVersion?: string
  description?: string
  author?: string
  installStatus?: 'NOT_INSTALLED' | 'INSTALLED' | 'UPGRADABLE'
  localVersion?: string
  updateTime?: string
}

export const middlewareApi = {
  list(): Promise<MiddlewareInfo[]> {
    return http.get<MiddlewareInfo[]>(`${API_BASE}/middlewares`)
  },

  health(middlewareId: string): Promise<HealthCheckResult> {
    return http.get<HealthCheckResult>(`${API_BASE}/middlewares/${middlewareId}/health`)
  },

  uninstall(middlewareId: string, operatorName: string = 'admin'): Promise<void> {
    return http.post(`${API_BASE}/middlewares/${middlewareId}/uninstall`, { operatorName })
  },

  /**
   * 获取远程中间件列表（分页）
   */
  getRemoteList(params?: { page?: number; size?: number; keyword?: string }): Promise<PageResult<RemoteMiddlewareInfo>> {
    return http.get<PageResult<RemoteMiddlewareInfo>>(`${API_BASE}/middlewares/remote`, params)
  },

  /**
   * 从远程应用商店安装中间件
   */
  installRemote(
    pluginId: string,
    operatorName: string = 'admin',
    opts?: { targetNodeId?: string; executionType?: 'local' | 'ssh' | 'docker-api' }
  ): Promise<MiddlewareInstallResult> {
    const params = new URLSearchParams({ pluginId, operatorName })
    if (opts?.targetNodeId) params.set('targetNodeId', opts.targetNodeId)
    if (opts?.executionType) params.set('executionType', opts.executionType)
    return http.post<MiddlewareInstallResult>(`${API_BASE}/middlewares/remote/install?${params.toString()}`)
  },

  /**
   * 从远程应用商店安装中间件（配合 SSE 显示日志）
   * 注意：主应用会自动在请求中注入 X-Client-Id / X-Namespace，无需前端手动传递
   */
  async installRemoteStream(
    pluginId: string,
    opts?: { targetNodeId?: string; executionType?: 'local' | 'ssh' | 'docker-api'; config?: Record<string, any> }
  ): Promise<void> {
    const params = new URLSearchParams({ pluginId, operatorName: 'admin' })
    if (opts?.targetNodeId) params.set('targetNodeId', opts.targetNodeId)
    if (opts?.executionType) params.set('executionType', opts.executionType)

    // 这里直接复用原有安装接口，安装过程中服务端会通过 SSE 推送日志
    // 如果有配置数据，通过请求体传递
    await http.post<MiddlewareInstallResult>(
      `${API_BASE}/middlewares/remote/install?${params.toString()}`,
      opts?.config || {}
    )
  },

  /**
   * 上传并安装中间件插件包
   * 注意：使用原生 fetch API，因为 GressBridge 不支持 FormData
   */
  async uploadAndInstall(formData: FormData): Promise<MiddlewareInstallResult> {
    const response = await fetch(`/api/${API_BASE}/middlewares/upload`, {
      method: 'POST',
      body: formData,
      credentials: 'include'
    })

    const result = await response.json().catch(() => ({
      success: false,
      errorMessage: '解析响应失败'
    }))

    if (result.success === false) {
      throw new Error(result.errorMessage || '上传失败')
    }

    if (!response.ok) {
      throw new Error(result.errorMessage || `HTTP ${response.status}`)
    }

    return result.data as MiddlewareInstallResult
  },

  /**
   * 获取中间件连接信息
   */
  getConnectionInfo(middlewareId: string): Promise<any> {
    return http.get(`${API_BASE}/middlewares/${middlewareId}/connection-info`)
  },

  /**
   * 获取中间件连接信息（文本格式）
   */
  getConnectionInfoText(middlewareId: string): Promise<{ text: string; middlewareId: string; middlewareName: string }> {
    return http.get(`${API_BASE}/middlewares/${middlewareId}/connection-info/text`)
  },

  /**
   * 获取中间件插件配置元数据（用于动态表单渲染）
   */
  getConfigMetadata(pluginId: string): Promise<any[]> {
    return http.get(`${API_BASE}/middlewares/remote/${pluginId}/config/metadata`)
  },

  /**
   * 列出所有中间件服务
   */
  listServices(): Promise<MiddlewareServiceInfo[]> {
    return http.get<MiddlewareServiceInfo[]>(`${API_BASE}/middlewares/services`)
  },

  /**
   * 获取单个服务信息
   */
  getService(serviceId: string): Promise<MiddlewareServiceInfo> {
    return http.get<MiddlewareServiceInfo>(`${API_BASE}/middlewares/services/${serviceId}`)
  },

  /**
   * 手动注册中间件服务
   */
  registerService(serviceInfo: MiddlewareServiceInfo): Promise<MiddlewareServiceInfo> {
    return http.post<MiddlewareServiceInfo>(`${API_BASE}/middlewares/services`, serviceInfo)
  },

  /**
   * 更新中间件服务信息
   */
  updateService(serviceId: string, serviceInfo: MiddlewareServiceInfo): Promise<MiddlewareServiceInfo> {
    return http.put<MiddlewareServiceInfo>(`${API_BASE}/middlewares/services/${serviceId}`, serviceInfo)
  },

  /**
   * 删除中间件服务
   */
  deleteService(serviceId: string): Promise<void> {
    return http.delete(`${API_BASE}/middlewares/services/${serviceId}`)
  }
}

/**
 * 中间件服务信息
 */
export interface MiddlewareServiceInfo {
  serviceId: string
  serviceType?: string
  serviceName?: string
  containerName?: string
  serviceHost?: string
  servicePort?: number
  healthCheckUrl?: string
  config?: Record<string, any>
  installedBy?: string
  referenceCount?: number
  consumers?: string[]
  status?: 'RUNNING' | 'STOPPED' | 'ERROR'
  workDir?: string
}

