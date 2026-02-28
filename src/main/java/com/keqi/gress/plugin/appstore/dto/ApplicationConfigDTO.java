package com.keqi.gress.plugin.appstore.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 应用配置 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationConfigDTO {
    
    /** 是否自动加载（系统启动时自动加载插件） */
    private Boolean autoLoad;
    
    /** 是否启动时加载（插件加载后立即启动） */
    private Boolean loadOnStartup;
    
    /** 启动优先级（0-100，数值越大优先级越高） */
    private Integer startPriority;
    
    /** 启动延迟（毫秒） */
    private Integer startDelay;
    
    /** 配置描述 */
    private String description;
    
    /** 扩展配置（插件自定义配置，根据 PluginConfigMetadataProvider 提供的元数据动态生成） */
    private Map<String, Object> extensionConfig;
}
