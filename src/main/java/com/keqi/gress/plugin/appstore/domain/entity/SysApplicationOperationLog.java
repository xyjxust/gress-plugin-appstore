package com.keqi.gress.plugin.appstore.domain.entity;

import  com.keqi.gress.plugin.api.database.annotation.IdType;
import  com.keqi.gress.plugin.api.database.annotation.TableField;
import  com.keqi.gress.plugin.api.database.annotation.TableId;
import  com.keqi.gress.plugin.api.database.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用操作日志
 * 用于记录应用的各种操作，包括启动、停止、重启、配置更新等
 */
@Data
@TableName("sys_application_operation_log")
public class SysApplicationOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的应用ID */
    @TableField("application_id")
    private Long applicationId;

    /** 应用代码 */
    @TableField("application_code")
    private String applicationCode;

    /** 应用名称 */
    @TableField("application_name")
    private String applicationName;

    /** 插件ID（packageId） */
    @TableField("plugin_id")
    private String pluginId;

    /** 操作类型：START, STOP, RESTART, INSTALL, UNINSTALL, UPGRADE, ROLLBACK, CONFIG_UPDATE */
    @TableField("operation_type")
    private String operationType;

    /** 操作描述 */
    @TableField("operation_desc")
    private String operationDesc;

    /** 操作人ID */
    @TableField("operator_id")
    private String operatorId;

    /** 操作人名称 */
    @TableField("operator_name")
    private String operatorName;

    /** 操作结果：SUCCESS / FAIL */
    @TableField("status")
    private String status;

    /** 失败原因或补充信息 */
    @TableField("message")
    private String message;

    /** 操作前数据（JSON格式，用于配置更新等场景） */
    @TableField("before_data")
    private String beforeData;

    /** 操作后数据（JSON格式，用于配置更新等场景） */
    @TableField("after_data")
    private String afterData;

    /** 操作耗时（毫秒） */
    @TableField("duration")
    private Long duration;

    /** 客户端IP */
    @TableField("client_ip")
    private String clientIp;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime = LocalDateTime.now();
}
