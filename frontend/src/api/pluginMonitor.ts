/**
 * 插件监控 API
 */

import { http } from './http'
import type {
  PluginMonitorStatus,
  PluginMonitorDetail,
  PluginMonitorHistory,
  MonitorOverview
} from '../types/pluginMonitor'

const API_BASE = '/plugins/appstore/monitor'

/**
 * 插件监控 API
 * 
 * 注意：所有方法直接返回业务数据，不包装 ApiResponse
 * 错误通过 try-catch 捕获
 */
export const pluginMonitorApi = {
  /**
   * 获取所有插件的监控状态
   */
  getAllStatus(): Promise<PluginMonitorStatus[]> {
    return http.get<PluginMonitorStatus[]>(`${API_BASE}/status`)
  },

  /**
   * 获取单个插件的详细监控信息
   */
  getDetail(pluginId: string): Promise<PluginMonitorDetail> {
    return http.get<PluginMonitorDetail>(`${API_BASE}/status/${pluginId}`)
  },

  /**
   * 获取插件历史监控数据
   * @param pluginId 插件ID
   * @param timeRange 时间范围，如 "1h", "24h", "7d"，默认 "1h"
   */
  getHistory(pluginId: string, timeRange: string = '1h'): Promise<PluginMonitorHistory[]> {
    return http.get<PluginMonitorHistory[]>(`${API_BASE}/history/${pluginId}`, { timeRange })
  },

  /**
   * 获取系统监控概览
   */
  getOverview(): Promise<MonitorOverview> {
    return http.get<MonitorOverview>(`${API_BASE}/overview`)
  }
}
