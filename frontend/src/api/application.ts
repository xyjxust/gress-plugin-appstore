/**
 * 应用管理 API
 */

import { http } from './http'
import type {
  Application,
  PageResult,
  ApplicationQueryRequest,
  ApplicationUpgradeRequest,
  ApplicationUninstallRequest,
  ApplicationUpgradeLog,
  AggregateApplicationRequest
} from '../types/application'

const API_BASE = '/plugins/appstore'

/**
 * 应用管理 API
 * 
 * 注意：所有方法直接返回业务数据，不包装 ApiResponse
 * 错误通过 try-catch 捕获
 */
export const applicationApi = {
  /**
   * 查询应用列表
   */
  getList(params?: ApplicationQueryRequest): Promise<PageResult<Application>> {
    return http.get<PageResult<Application>>(`${API_BASE}/applications`, params) as any
  },

  /**
   * 获取应用详情
   */
  getDetail(id: number): Promise<Application> {
    return http.get<Application>(`${API_BASE}/applications/${id}`) as any
  },

  /**
   * 升级应用
   */
  upgrade(id: number, data: ApplicationUpgradeRequest): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/upgrade`, data) as any
  },

  /**
   * 卸载应用
   */
  uninstall(id: number, data: ApplicationUninstallRequest): Promise<void> {
    return http.delete(`${API_BASE}/applications/${id}`, { data }) as any
  },

  /**
   * 启用应用
   */
  enable(id: number, operatorName: string): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/enable`, { operatorName }) as any
  },

  /**
   * 禁用应用
   */
  disable(id: number, operatorName: string): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/disable`, { operatorName }) as any
  },

  /**
   * 查询远程应用商店应用列表
   */
  getRemoteList(params?: ApplicationQueryRequest): Promise<PageResult<Application>> {
    return http.get<PageResult<Application>>(`${API_BASE}/applications/remote`, params) as any
  },

  /**
   * 上传并安装应用包
   */
  async uploadAndInstall(formData: FormData): Promise<void> {
    await http.post(`${API_BASE}/applications/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    } as any)
  },

  /**
   * 从远程应用商店安装应用
   */
  installRemote(pluginId: string, operatorName: string = 'admin'): Promise<void> {
    // 使用查询参数传递 pluginId 和 operatorName
    const params = {
      pluginId,
      operatorName
    }
    // 构建查询字符串
    const queryString = new URLSearchParams(params).toString()
    return http.post(`${API_BASE}/applications/remote/install?${queryString}`) as any
  },

  /**
   * 查询应用升级日志
   */
  getUpgradeLogs(id: number): Promise<ApplicationUpgradeLog[]> {
    return http.get<ApplicationUpgradeLog[]>(`${API_BASE}/applications/${id}/upgrade-logs`) as any
  },

  /**
   * 降级应用（按指定版本回滚）
   */
  rollback(id: number, data: ApplicationUpgradeRequest): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/rollback`, data) as any
  },

  /**
   * 重启应用
   */
  restart(id: number, operatorName: string = 'admin'): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/restart`, { operatorName }) as any
  },

  /**
   * 获取应用配置元数据（用于动态表单渲染）
   */
  getConfigMetadata(id: number): Promise<any[]> {
    return http.get(`${API_BASE}/applications/${id}/config/metadata`) as any
  },

  /**
   * 获取应用配置
   */
  getConfig(id: number): Promise<{
    autoLoad?: boolean
    loadOnStartup?: boolean
    startPriority?: number
    startDelay?: number
    description?: string
    extensionConfig?: Record<string, any>
  }> {
    return http.get(`${API_BASE}/applications/${id}/config`) as any
  },

  /**
   * 更新应用配置
   */
  updateConfig(id: number, data: {
    autoLoad?: boolean
    loadOnStartup?: boolean
    startPriority?: number
    startDelay?: number
    description?: string
    extensionConfig?: Record<string, any>
  }): Promise<void> {
    return http.put(`${API_BASE}/applications/${id}/config`, data) as any
  },

  /**
   * 聚合应用管理
   */
  listAggregates(): Promise<Application[]> {
    return http.get<Application[]>(`${API_BASE}/applications/aggregates`) as any
  },
  listAggregatablePlugins(): Promise<Application[]> {
    return http.get<Application[]>(`${API_BASE}/applications/aggregates/available-plugins`) as any
  },
  createAggregate(data: AggregateApplicationRequest): Promise<void> {
    return http.post(`${API_BASE}/applications/aggregates`, data) as any
  },
  updateAggregate(id: number, data: AggregateApplicationRequest): Promise<void> {
    return http.put(`${API_BASE}/applications/aggregates/${id}`, data) as any
  },
  deleteAggregate(id: number): Promise<void> {
    return http.delete(`${API_BASE}/applications/aggregates/${id}`) as any
  },

  /**
   * 查询应用操作日志
   */
  getOperationLogs(id: number, page: number = 1, size: number = 20, operationType?: string): Promise<PageResult<any>> {
    const params: any = { page, size }
    if (operationType) {
      params.operationType = operationType
    }
    return http.get<PageResult<any>>(`${API_BASE}/applications/${id}/operation-logs`, params) as any
  },

  /**
   * 查询所有应用操作日志（全局查询）
   */
  getAllOperationLogs(params?: {
    page?: number
    size?: number
    operationType?: string
    operatorName?: string
    applicationName?: string
    status?: string
  }): Promise<PageResult<any>> {
    return http.get<PageResult<any>>(`${API_BASE}/applications/operation-logs`, params) as any
  }
}