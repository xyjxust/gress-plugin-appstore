package com.keqi.gress.plugin.appstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用信息 DTO
 * 对应 SysApplication 实体，字段顺序和类型保持一致
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDTO {
    /** 应用ID */
    private Long id;
    
    /** 应用代码（唯一标识） */
    private String applicationCode;
    
    /** 应用名称 */
    private String applicationName;
    
    /** 插件ID */
    private String pluginId;
    
    /** 插件版本 */
    private String pluginVersion;
    
    /** 应用描述 */
    private String description;
    
    /** 作者 */
    private String author;
    
    /** 主页URL */
    private String homepage;
    
    /** 状态（0:禁用 1:启用） */
    private Integer status;
    
    /** 状态文本（计算字段：启用/禁用） */
    private String statusText;
    
    /** 安装时间 */
    private LocalDateTime installTime;
    
    /** 更新时间 */
    private LocalDateTime updateTime;
    
    /** 创建人 */
    private String createBy;
    
    /** 更新人 */
    private String updateBy;
    
    /** 命名空间代码 */
    private String namespaceCode;
    
    /** 应用类型（integrated:集成应用 plugin:插件应用） */
    private String applicationType;
    
    /** 应用类型文本（计算字段：集成应用/插件应用） */
    private String applicationTypeText;
    
    /** 是否默认应用（0:否 1:是） */
    private Integer isDefault;
    
    /** 插件类型（TRIGGER, TASK, APPLICATION） */
    private String pluginType;
    
    /** 本地安装状态（用于远程应用列表）：NOT_INSTALLED-未安装, INSTALLED-已安装, UPGRADABLE-可升级 */
    private String installStatus;
    
    /** 本地已安装版本（用于远程应用列表） */
    private String localVersion;
    
    /** 远程最新版本（用于本地应用列表） */
    private String remoteVersion;
    
    /** 是否有新版本可升级（用于本地应用列表） */
    private Boolean hasNewVersion;
    
    /** 依赖信息列表 */
    private List<DependencyInfo> dependencies;
    
    /**
     * 依赖信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DependencyInfo {
        /** 插件ID */
        private String pluginId;
        
        /** 版本号 */
        private String version;
        
        /** 是否为可选依赖 */
        private Boolean optional;
        
        /** 版本范围（如 ">=1.0.0"） */
        private String versionRange;
    }
}
