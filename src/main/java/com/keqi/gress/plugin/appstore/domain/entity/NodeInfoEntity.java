package com.keqi.gress.plugin.appstore.domain.entity;

import  com.keqi.gress.plugin.api.database.annotation.TableField;
import  com.keqi.gress.plugin.api.database.annotation.TableName;
import com.keqi.gress.plugin.api.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 节点信息实体类（持久化）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appstore_node_info")
public class NodeInfoEntity extends BaseEntity {

    @TableField("node_id")
    private String nodeId;

    @TableField("name")
    private String name;

    @TableField("type")
    private String type; // local | ssh | docker-api

    @TableField("description")
    private String description;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("config")
    private String config; // JSON

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}

