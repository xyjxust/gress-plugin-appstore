/**
 * 应用管理类型定义
 */

/**
 * 应用类型
 */
export type ApplicationType = 'integrated' | 'plugin'

/**
 * 安装状态
 */
export type InstallStatus = 'NOT_INSTALLED' | 'INSTALLED' | 'UPGRADABLE'

/**
 * 应用信息
 */
export interface Application {
  id: number
  applicationCode: string
  applicationName: string
  pluginId: string
  pluginVersion?: string
  description?: string
  author?: string
  homepage?: string
  status: number
  statusText: string
  installTime: string
  updateTime: string
  createBy?: string
  updateBy?: string
  namespaceCode?: string
  applicationType: ApplicationType
  applicationTypeText: string
  isDefault: number
  pluginType?: string
  // 远程应用字段
  installStatus?: InstallStatus
  localVersion?: string
  // 本地应用字段
  remoteVersion?: string
  hasNewVersion?: boolean
}

/**
 * 分页结果
 */
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

/**
 * API响应
 */
export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  message?: string
}

/**
 * 应用查询请求
 */
export interface ApplicationQueryRequest {
  page?: number
  size?: number
  keyword?: string
  status?: number
  applicationType?: ApplicationType
  pluginId?: string
}

/**
 * 应用升级请求
 */
export interface ApplicationUpgradeRequest {
  targetVersion: string
  operatorId: string
  operatorName: string
}

/**
 * 应用卸载请求
 */
export interface ApplicationUninstallRequest {
  operatorId: string
  operatorName: string
  reason: string
}

/**
 * 应用升级日志
 */
export interface ApplicationUpgradeLog {
  id: number
  applicationId: number
  pluginId: string
  oldVersion?: string
  newVersion?: string
  targetVersion?: string
  pluginType?: string
  operatorName?: string
  status: string
  message?: string
  createTime: string
}
