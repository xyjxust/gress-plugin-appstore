package com.keqi.gress.plugin.appstore.service.monitor;

import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Scheduled;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.dao.MonitorHistoryDao;
import com.keqi.gress.plugin.appstore.domain.entity.PluginMonitorSnapshot;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控快照定时任务
 * 负责定期保存插件监控快照和清理过期数据
 */
@Slf4j
@Service
public class MonitorSnapshotScheduler {
    
    @Inject
    private PluginStatusCollector statusCollector;
    
    @Inject
    private PluginMemoryCollector memoryCollector;
    
    @Inject
    private MonitorHistoryDao historyDao;
    
    @Inject
    private PluginStateChangeListener stateChangeListener;
    
    /**
     * 定时保存监控快照
     * 每分钟执行一次
     */
    @Scheduled(cron = "0 * * * * ?")
    public void saveMonitorSnapshots() {
        try {
            log.debug("开始保存监控快照");
            
            // 收集所有插件状态
            List<PluginMonitorStatus> statusList = statusCollector.collectAllStatus();
            
            if (statusList == null || statusList.isEmpty()) {
                log.debug("没有插件需要保存快照");
                return;
            }
            
            // 创建快照列表
            List<PluginMonitorSnapshot> snapshots = new ArrayList<>();
            Map<String, String> currentStates = new HashMap<>();
            long timestamp = System.currentTimeMillis();
            
            for (PluginMonitorStatus status : statusList) {
                try {
                    // 收集内存信息
                    Long memoryUsage = null;
                    try {
                        var memInfo = memoryCollector.collectMemoryInfo(status.getPluginId());
                        if (memInfo != null) {
                            memoryUsage = memInfo.getUsedMemory();
                        }
                    } catch (Exception e) {
                        log.warn("收集插件内存信息失败: pluginId={}", status.getPluginId(), e);
                    }
                    
                    // 创建快照
                    PluginMonitorSnapshot snapshot = PluginMonitorSnapshot.builder()
                            .pluginId(status.getPluginId())
                            .state(status.getState())
                            .memoryUsage(memoryUsage)
                            .timestamp(timestamp)
                            .metadata(buildMetadata(status))
                            .build();
                    
                    snapshots.add(snapshot);
                    
                    // 记录当前状态用于状态变更检测
                    currentStates.put(status.getPluginId(), status.getState());
                    
                } catch (Exception e) {
                    log.error("创建插件快照失败: pluginId={}", status.getPluginId(), e);
                }
            }
            
            // 批量保存快照
            if (!snapshots.isEmpty()) {
                int savedCount = historyDao.batchSaveSnapshots(snapshots);
                log.debug("保存监控快照完成: 总数={}, 成功={}", snapshots.size(), savedCount);
            }
            
            // 检查并记录状态变更
            if (!currentStates.isEmpty()) {
                stateChangeListener.checkAndRecordStateChanges(currentStates);
            }
            
        } catch (Exception e) {
            log.error("保存监控快照失败", e);
        }
    }
    
    /**
     * 构建元数据 JSON
     * 
     * @param status 插件状态
     * @return JSON 格式的元数据
     */
    private String buildMetadata(PluginMonitorStatus status) {
        try {
            // 简单的 JSON 构建（避免引入 JSON 库依赖）
            StringBuilder json = new StringBuilder("{");
            
            if (status.getPluginName() != null) {
                json.append("\"pluginName\":\"").append(escapeJson(status.getPluginName())).append("\",");
            }
            
            if (status.getPluginVersion() != null) {
                json.append("\"pluginVersion\":\"").append(escapeJson(status.getPluginVersion())).append("\",");
            }
            
            if (status.getLoaded() != null) {
                json.append("\"loaded\":").append(status.getLoaded()).append(",");
            }
            
            if (status.getStartTime() != null) {
                json.append("\"startTime\":").append(status.getStartTime()).append(",");
            }
            
            if (status.getUptime() != null) {
                json.append("\"uptime\":").append(status.getUptime()).append(",");
            }
            
            // 移除最后的逗号
            if (json.charAt(json.length() - 1) == ',') {
                json.setLength(json.length() - 1);
            }
            
            json.append("}");
            return json.toString();
            
        } catch (Exception e) {
            log.warn("构建元数据失败: pluginId={}", status.getPluginId(), e);
            return "{}";
        }
    }
    
    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
