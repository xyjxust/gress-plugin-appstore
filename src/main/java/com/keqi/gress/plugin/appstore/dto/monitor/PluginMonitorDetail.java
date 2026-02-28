package com.keqi.gress.plugin.appstore.dto.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 插件监控详情 DTO
 * 用于展示插件的完整监控信息，包括状态、内存、元数据等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMonitorDetail {
    
    /** 基本状态信息 */
    private PluginMonitorStatus status;
    
    /** 内存信息 */
    private PluginMemoryInfo memoryInfo;
    
    /** 插件元数据（作者、描述、主页等） */
    private Map<String, Object> metadata;
    
    /** 类加载器信息 */
    private ClassLoaderInfo classLoaderInfo;
    
    /** 插件配置信息 */
    private Map<String, Object> configuration;
}
