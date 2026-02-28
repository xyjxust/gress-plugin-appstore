package com.keqi.gress.plugin.appstore.domain.entity;

import  com.keqi.gress.plugin.api.database.annotation.TableField;
import  com.keqi.gress.plugin.api.database.annotation.TableId;
import  com.keqi.gress.plugin.api.database.annotation.TableName;
import lombok.Data;

/**
 * 中间件信息实体类
 */
@Data
@TableName("sys_middleware_info")
public class MiddlewareInfoEntity {
    
    @TableId
    @TableField("id")
    private Long id;
    
    @TableField("middleware_id")
    private String middlewareId;
    
    @TableField("name")
    private String name;
    
    @TableField("version")
    private String version;
    
    @TableField("category")
    private String category;
    
    @TableField("shared")
    private Boolean shared;
    
    @TableField("service_host")
    private String serviceHost;
    
    @TableField("service_port")
    private Integer servicePort;
    
    @TableField("health_check_url")
    private String healthCheckUrl;
    
    @TableField("status")
    private String status;
    
    @TableField("work_dir")
    private String workDir;
    
    @TableField("config")
    private String config;  // JSON 字符串
    
    @TableField("package_path")
    private String packagePath;
    
    @TableField("dependencies")
    private String dependencies;
    
    @TableField("installed_by")
    private String installedBy;
    
    @TableField("installed_at")
    private java.sql.Timestamp installedAt;
    
    @TableField("updated_at")
    private java.sql.Timestamp updatedAt;
}
