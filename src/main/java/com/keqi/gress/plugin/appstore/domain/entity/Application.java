package com.keqi.gress.plugin.appstore.domain.entity;

import  com.keqi.gress.plugin.api.database.annotation.TableField;
import  com.keqi.gress.plugin.api.database.annotation.TableName;
import  com.keqi.gress.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 工作流应用插件表
 * 用于管理需要持续运行的应用插件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("gress_application")
@Schema(description = "工作流应用插件")
public class Application extends BaseEntity {
    
    @Schema(description = "应用代码（唯一标识）")
    private String applicationCode;
    
    @Schema(description = "应用名称")
    private String applicationName;
    
    @Schema(description = "插件ID")
    private String pluginId;
    
    @Schema(description = "插件版本")
    private String pluginVersion;
    
    @Schema(description = "应用配置（JSON格式）")
    @TableField(value = "application_config")
    private String applicationConfig;
    
    @Schema(description = "描述")
    private String description;
    
    @Schema(description = "状态（0:禁用 1:启用）")
    private Integer status;
    
    @Schema(description = "最后启动时间")
    private LocalDateTime lastStartTime;
    
    @Schema(description = "最后停止时间")
    private LocalDateTime lastStopTime;
    
    @Schema(description = "执行器ID")
    private String executorId;
    
    @Schema(description = "应用类型（integrated:集成应用 plugin:插件应用）")
    private String applicationType;
    
    @Schema(description = "是否默认应用（0:否 1:是）")
    private Integer isDefault;
    
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
}

