package com.keqi.gress.plugin.appstore.domain.entity;

import com.keqi.gress.plugin.api.database.annotation.TableField;
import com.keqi.gress.plugin.api.database.annotation.TableName;
import com.keqi.gress.plugin.api.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appstore_stack_target")
public class StackTargetEntity extends BaseEntity {

    @TableField("stack_id")
    private String stackId;

    @TableField("node_id")
    private String nodeId;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("roles")
    private String roles;

    @TableField("web_port")
    private Integer webPort;

    @TableField("fronted_port")
    private Integer frontedPort;

    @TableField("last_deployed_version")
    private String lastDeployedVersion;

    @TableField("health_status")
    private String healthStatus;

    @TableField("last_health_check_time")
    private Long lastHealthCheckTime;
}

