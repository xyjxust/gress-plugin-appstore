package com.keqi.gress.plugin.appstore.dto.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 插件监控历史数据 DTO
 * 用于返回插件的历史监控数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMonitorHistory {
    
    /** 插件ID */
    private String pluginId;
    
    /** 插件状态 */
    private String state;
    
    /** 内存使用量（字节） */
    private Long memoryUsage;
    
    /** 格式化的内存大小 */
    private String formattedMemory;
    
    /** 快照时间戳（毫秒） */
    private Long timestamp;
    
    /** 额外元数据 */
    private String metadata;
}
