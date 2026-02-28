package com.keqi.gress.plugin.appstore.domain.entity;

import  com.keqi.gress.plugin.api.database.annotation.IdType;
import  com.keqi.gress.plugin.api.database.annotation.TableField;
import  com.keqi.gress.plugin.api.database.annotation.TableId;
import  com.keqi.gress.plugin.api.database.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用升级日志
 * 用于记录每次升级的结果，便于后续回滚或审计
 */
@Data
@TableName("sys_application_upgrade_log")
public class SysApplicationUpgradeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的应用ID */
    @TableField("application_id")
    private Long applicationId;

    /** 插件ID（packageId） */
    @TableField("plugin_id")
    private String pluginId;

    /** 升级前版本 */
    @TableField("old_version")
    private String oldVersion;

    /** 升级后版本 */
    @TableField("new_version")
    private String newVersion;

    /** 目标版本（请求参数） */
    @TableField("target_version")
    private String targetVersion;

    /** 升级后的插件类型（逗号分隔） */
    @TableField("plugin_type")
    private String pluginType;

    /** 操作人 */
    @TableField("operator_name")
    private String operatorName;

    /** 升级结果：SUCCESS / FAIL */
    @TableField("status")
    private String status;

    /** 失败原因或补充信息 */
    @TableField("message")
    private String message;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime = LocalDateTime.now();
}

