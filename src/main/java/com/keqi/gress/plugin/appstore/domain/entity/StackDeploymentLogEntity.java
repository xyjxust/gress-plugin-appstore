package com.keqi.gress.plugin.appstore.domain.entity;

import com.keqi.gress.plugin.api.database.annotation.TableField;
import com.keqi.gress.plugin.api.database.annotation.TableName;
import com.keqi.gress.plugin.api.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appstore_stack_deployment_log")
public class StackDeploymentLogEntity extends BaseEntity {

    @TableField("deployment_id")
    private String deploymentId;

    @TableField("node_id")
    private String nodeId;

    @TableField("step")
    private String step;

    @TableField("status")
    private String status;

    @TableField("output")
    private String output;

    @TableField("timestamp")
    private Long timestamp;
}

