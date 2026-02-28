package com.keqi.gress.plugin.appstore.dto.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 插件监控状态 DTO
 * 用于展示插件的基本运行状态信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMonitorStatus {
    
    /** 插件ID */
    private String pluginId;
    
    /** 插件名称 */
    private String pluginName;
    
    /** 插件版本 */
    private String pluginVersion;
    
    /** 运行状态: STARTED, STOPPED, CREATED, DISABLED */
    private String state;
    
    /** 是否已加载到 PluginManager */
    private Boolean loaded;
    
    /** 启动时间戳（毫秒） */
    private Long startTime;
    
    /** 运行时长（毫秒） */
    private Long uptime;
    
    /** 内存信息 */
    private PluginMemoryInfo memoryInfo;
    
    /** 是否有错误 */
    private Boolean hasError;
    
    /** 错误信息 */
    private String errorMessage;
    
    /** 是否有内存告警 */
    private Boolean isMemoryWarning;
}
