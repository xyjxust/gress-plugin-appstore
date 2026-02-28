package com.keqi.gress.plugin.appstore.util;

import com.keqi.gress.plugin.appstore.dto.monitor.PluginMemoryInfo;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * 监控错误处理器
 * 负责处理监控数据收集过程中的各种错误情况
 * 
 * 设计原则：
 * 1. 错误隔离：单个插件的错误不影响其他插件的监控
 * 2. 降级处理：无法获取的数据使用默认值或标记为不可用
 * 3. 详细日志：记录错误详情便于问题排查
 */
@Slf4j
public class MonitorErrorHandler {
    
    /**
     * 处理数据收集错误
     * 
     * 当收集插件监控数据失败时，创建一个包含错误信息的状态对象，
     * 确保部分失败不影响整体监控功能。
     * 
     * @param pluginId 插件ID
     * @param e 异常对象
     * @return 包含错误信息的插件监控状态
     */
    public static PluginMonitorStatus handleCollectionError(String pluginId, Exception e) {
        log.error("收集插件监控数据失败: pluginId={}", pluginId, e);
        
        return PluginMonitorStatus.builder()
                .pluginId(pluginId)
                .pluginName("未知")
                .pluginVersion("未知")
                .state("ERROR")
                .loaded(false)
                .hasError(true)
                .errorMessage("数据收集失败: " + e.getMessage())
                .build();
    }
    
    /**
     * 处理超时错误
     * 
     * 当数据收集操作超时时，记录警告日志。
     * 超时通常不会中断整体流程，但需要记录以便分析性能问题。
     * 
     * @param pluginId 插件ID
     */
    public static void handleTimeout(String pluginId) {
        log.warn("收集插件监控数据超时: pluginId={}", pluginId);
        // 超时事件已记录，调用方可以决定是否继续处理
    }
    
    /**
     * 处理内存信息不可用
     * 
     * 当无法获取插件的内存使用信息时，返回一个标记为"不可用"的内存信息对象。
     * 这样可以保证监控页面正常显示，只是内存信息显示为不可用状态。
     * 
     * @param pluginId 插件ID
     * @return 标记为不可用的内存信息对象
     */
    public static PluginMemoryInfo handleMemoryUnavailable(String pluginId) {
        log.debug("插件内存信息不可用: pluginId={}", pluginId);
        
        return PluginMemoryInfo.builder()
                .usedMemory(0L)
                .formattedMemory("不可用")
                .totalJvmMemory(Runtime.getRuntime().totalMemory())
                .freeJvmMemory(Runtime.getRuntime().freeMemory())
                .maxJvmMemory(Runtime.getRuntime().maxMemory())
                .build();
    }
}
