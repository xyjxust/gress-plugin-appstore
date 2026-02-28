/**
 * 插件监控类型定义
 */

/**
 * 插件内存信息
 */
export interface PluginMemoryInfo {
  /** 插件使用的内存（字节） */
  usedMemory?: number
  /** 格式化的内存大小（如 "10.5 MB"） */
  formattedMemory?: string
  /** JVM 总内存（字节） */
  totalJvmMemory?: number
  /** JVM 空闲内存（字节） */
  freeJvmMemory?: number
  /** JVM 最大内存（字节） */
  maxJvmMemory?: number
}

/**
 * 插件监控状态
 */
export interface PluginMonitorStatus {
  /** 插件ID */
  pluginId: string
  /** 插件名称 */
  pluginName: string
  /** 插件版本 */
  pluginVersion?: string
  /** 运行状态: STARTED, STOPPED, CREATED, DISABLED */
  state: string
  /** 是否已加载到 PluginManager */
  loaded?: boolean
  /** 启动时间戳（毫秒） */
  startTime?: number
  /** 运行时长（毫秒） */
  uptime?: number
  /** 内存信息 */
  memoryInfo?: PluginMemoryInfo
  /** 是否有错误 */
  hasError?: boolean
  /** 错误信息 */
  errorMessage?: string
  /** 是否有内存告警 */
  isMemoryWarning?: boolean
}

/**
 * 类加载器信息
 */
export interface ClassLoaderInfo {
  /** 类加载器类名 */
  className?: string
  /** 父类加载器类名 */
  parentClassName?: string
}

/**
 * 插件监控详情
 */
export interface PluginMonitorDetail {
  /** 基本状态信息 */
  status?: PluginMonitorStatus
  /** 内存信息 */
  memoryInfo?: PluginMemoryInfo
  /** 插件元数据（作者、描述、主页等） */
  metadata?: Record<string, any>
  /** 类加载器信息 */
  classLoaderInfo?: ClassLoaderInfo
  /** 插件配置信息 */
  configuration?: Record<string, any>
}

/**
 * 监控概览
 */
export interface MonitorOverview {
  /** 总插件数 */
  totalPlugins: number
  /** 运行中插件数 */
  runningPlugins: number
  /** 已停止插件数 */
  stoppedPlugins: number
  /** 异常插件数 */
  errorPlugins: number
  /** 总内存使用（字节） */
  totalMemoryUsage: number
}

/**
 * 插件监控历史数据
 */
export interface PluginMonitorHistory {
  /** 插件ID */
  pluginId: string
  /** 插件状态 */
  state: string
  /** 内存使用量（字节） */
  memoryUsage?: number
  /** 格式化的内存大小 */
  formattedMemory?: string
  /** 快照时间戳（毫秒） */
  timestamp: number
  /** 额外元数据 */
  metadata?: string
}
