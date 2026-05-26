package com.keqi.gress.plugin.appstore.domain.entity;

import com.keqi.gress.plugin.api.database.annotation.TableField;
import com.keqi.gress.plugin.api.database.annotation.TableName;
import com.keqi.gress.plugin.api.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appstore_stack_config")
public class StackConfigEntity extends BaseEntity {

    @TableField("stack_id")
    private String stackId;

    @TableField("name")
    private String name;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("mysql_database")
    private String mysqlDatabase;

    @TableField("redis_db")
    private Integer redisDb;

    @TableField("runtime_base_dir")
    private String runtimeBaseDir;

    @TableField("web_image")
    private String webImage;

    @TableField("fronted_image")
    private String frontedImage;

    @TableField("version_tag")
    private String versionTag;

    @TableField("deploy_fronted")
    private Boolean deployFronted;

    @TableField("join_nginx")
    private Boolean joinNginx;

    @TableField("entry_node_id")
    private String entryNodeId;

    @TableField("domain")
    private String domain;

    @TableField("web_host_port")
    private Integer webHostPort;

    @TableField("fronted_host_port")
    private Integer frontedHostPort;

    @TableField("extra_config")
    private String extraConfig;
}

