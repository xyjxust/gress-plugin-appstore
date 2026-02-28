package com.keqi.gress.plugin.appstore.dto.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 监控概览 DTO
 * 用于展示系统级别的插件监控概览信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorOverview {
    
    /** 总插件数 */
    private Integer totalPlugins;
    
    /** 运行中插件数 */
    private Long runningPlugins;
    
    /** 已停止插件数 */
    private Long stoppedPlugins;
    
    /** 异常插件数 */
    private Long errorPlugins;
    
    /** 总内存使用（字节） */
    private Long totalMemoryUsage;
}
