package com.keqi.gress.plugin.appstore.domain.entity;

import com.keqi.gress.plugin.api.database.annotation.TableField;
import com.keqi.gress.plugin.api.database.annotation.TableName;
import com.keqi.gress.plugin.api.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appstore_stack_deployment")
public class StackDeploymentEntity extends BaseEntity {

    @TableField("deployment_id")
    private String deploymentId;

    @TableField("stack_id")
    private String stackId;

    @TableField("requested_version")
    private String requestedVersion;

    @TableField("mode")
    private String mode;

    @TableField("strategy")
    private String strategy;

    @TableField("deploy_fronted")
    private Boolean deployFronted;

    @TableField("join_nginx")
    private Boolean joinNginx;

    @TableField("status")
    private String status;

    @TableField("message")
    private String message;

    @TableField("started_at")
    private Long startedAt;

    @TableField("ended_at")
    private Long endedAt;
}

