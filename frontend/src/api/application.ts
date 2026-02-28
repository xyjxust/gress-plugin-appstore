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
  ApplicationUpgradeLog
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
    return http.get<PageResult<Application>>(`${API_BASE}/applications`, params)
  },

  /**
   * 获取应用详情
   */
  getDetail(id: number): Promise<Application> {
    return http.get<Application>(`${API_BASE}/applications/${id}`)
  },

  /**
   * 升级应用
   */
  upgrade(id: number, data: ApplicationUpgradeRequest): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/upgrade`, data)
  },

  /**
   * 卸载应用
   */
  uninstall(id: number, data: ApplicationUninstallRequest): Promise<void> {
    return http.delete(`${API_BASE}/applications/${id}`, data)
  },

  /**
   * 启用应用
   */
  enable(id: number, operatorName: string): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/enable`, { operatorName })
  },

  /**
   * 禁用应用
   */
  disable(id: number, operatorName: string): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/disable`, { operatorName })
  },

  /**
   * 查询远程应用商店应用列表
   */
  getRemoteList(params?: ApplicationQueryRequest): Promise<PageResult<Application>> {
    return http.get<PageResult<Application>>(`${API_BASE}/applications/remote`, params)
  },

  /**
   * 上传并安装应用包
   * 注意：使用原生 fetch API，因为 GressBridge 不支持 FormData
   */
  async uploadAndInstall(formData: FormData): Promise<void> {
    // 使用原生 fetch API 上传文件
    const response = await fetch(`/api/${API_BASE}/applications/upload`, {
      method: 'POST',
      body: formData,
      credentials: 'include' // 包含 cookies
    })
    
    // 先解析 JSON 响应
    const result = await response.json().catch(() => ({
      success: false,
      errorMessage: '解析响应失败'
    }))
    
    // 检查业务逻辑是否成功（不依赖 HTTP 状态码）
    if (result.success === false) {
      throw new Error(result.errorMessage || '上传失败')
    }
    
    // 检查 HTTP 状态码
    if (!response.ok) {
      throw new Error(result.errorMessage || `HTTP ${response.status}`)
    }
    
    return result.data
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
    return http.post(`${API_BASE}/applications/remote/install?${queryString}`)
  },

  /**
   * 查询应用升级日志
   */
  getUpgradeLogs(id: number): Promise<ApplicationUpgradeLog[]> {
    return http.get<ApplicationUpgradeLog[]>(`${API_BASE}/applications/${id}/upgrade-logs`)
  },

  /**
   * 降级应用（按指定版本回滚）
   */
  rollback(id: number, data: ApplicationUpgradeRequest): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/rollback`, data)
  },

  /**
   * 重启应用
   */
  restart(id: number, operatorName: string = 'admin'): Promise<void> {
    return http.post(`${API_BASE}/applications/${id}/restart`, { operatorName })
  },

  /**
   * 获取应用配置元数据（用于动态表单渲染）
   */
  getConfigMetadata(id: number): Promise<any[]> {
    return http.get(`${API_BASE}/applications/${id}/config/metadata`)
  },

  /**
   * 获取应用配置
   */
  getConfig(id: number): Promise<{
    extensionConfig?: Record<string, any>
  }> {
    return http.get(`${API_BASE}/applications/${id}/config`)
  },

  /**
   * 更新应用配置
   */
  updateConfig(id: number, data: {
    extensionConfig?: Record<string, any>
  }): Promise<void> {
    return http.put(`${API_BASE}/applications/${id}/config`, data)
  },

  /**
   * 查询应用操作日志
   */
  getOperationLogs(id: number, page: number = 1, size: number = 20, operationType?: string): Promise<PageResult<any>> {
    const params: any = { page, size }
    if (operationType) {
      params.operationType = operationType
    }
    return http.get<PageResult<any>>(`${API_BASE}/applications/${id}/operation-logs`, params)
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
    return http.get<PageResult<any>>(`${API_BASE}/applications/operation-logs`, params)
  }
}