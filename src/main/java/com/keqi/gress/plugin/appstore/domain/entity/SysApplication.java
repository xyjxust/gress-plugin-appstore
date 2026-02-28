package com.keqi.gress.plugin.appstore.domain.entity;

import  com.keqi.gress.plugin.api.database.annotation.IdType;
import  com.keqi.gress.plugin.api.database.annotation.TableField;
import  com.keqi.gress.plugin.api.database.annotation.TableId;
import  com.keqi.gress.plugin.api.database.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统应用实体
 */
@Data
@TableName("sys_application")
public class SysApplication {
    
    /** 应用ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 应用代码（唯一标识） */
    @TableField("application_code")
    private String applicationCode;
    
    /** 应用名称 */
    @TableField("application_name")
    private String applicationName;
    
    /** 插件ID */
    @TableField("plugin_id")
    private String pluginId;
    
    /** 插件版本 */
    @TableField("plugin_version")
    private String pluginVersion;
    
    /** 应用描述 */
    @TableField("description")
    private String description;
    
    /** 作者 */
    @TableField("author")
    private String author;
    
    /** 主页URL */
    @TableField("homepage")
    private String homepage;
    
    /** 状态（0:禁用 1:启用） */
    @TableField("status")
    private Integer status;
    
    /** 安装时间 */
    @TableField("install_time")
    private LocalDateTime installTime;
    
    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
    
    /** 创建人 */
    @TableField("create_by")
    private String createBy;
    
    /** 更新人 */
    @TableField("update_by")
    private String updateBy;
    
    /** 命名空间代码 */
    @TableField("namespace_code")
    private String namespaceCode;
    
    @Schema(description = "应用类型（integrated:集成应用 plugin:插件应用）")
    @TableField("application_type")
    private String applicationType;

    @Schema(description = "是否默认应用（0:否 1:是）")
    @TableField("is_default")
    private Integer isDefault;
    
    /** 插件类型（TRIGGER, TASK, APPLICATION） */
    @Schema(description = "插件类型（多个类型用逗号分隔，如：TRIGGER,TASK,APPLICATION）")
    @TableField("plugin_type")
    private String pluginType;
    
    /** 扩展配置（JSON格式） */
    @Schema(description = "扩展配置（JSON格式），用于存储应用的额外配置信息")
    @TableField("extension_config")
    private String extensionConfig;

    /**
     * 检查应用是否启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }

    /**
     * 检查是否为集成应用
     */
    public boolean isIntegrated() {
        return "integrated".equalsIgnoreCase(applicationType);
    }

    /**
     * 检查是否为插件应用
     */
    public boolean isPlugin() {
        return "plugin".equalsIgnoreCase(applicationType);
    }

    /**
     * 检查是否为默认应用
     */
    public boolean isDefaultApplication() {
        return isDefault != null && isDefault == 1;
    }

    /**
     * 检查应用是否禁用
     */
    public boolean isDisabled() {
        return !isEnabled();
    }
    
    /**
     * 检查是否包含 APPLICATION 类型插件
     */
    public boolean containsApplicationType() {
        if (pluginType == null || pluginType.isEmpty()) {
            return false;
        }
        return pluginType.toUpperCase().contains("APPLICATION");
    }
}

