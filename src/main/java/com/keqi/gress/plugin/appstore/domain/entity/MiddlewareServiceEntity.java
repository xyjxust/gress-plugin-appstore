package com.keqi.gress.plugin.appstore.domain.entity;

import  com.keqi.gress.plugin.api.database.annotation.TableField;
import  com.keqi.gress.plugin.api.database.annotation.TableId;
import  com.keqi.gress.plugin.api.database.annotation.TableName;
import lombok.Data;

/**
 * 中间件服务实体类
 */
@Data
@TableName("sys_middleware_service")
public class MiddlewareServiceEntity {
    
    @TableId
    @TableField("id")
    private Long id;
    
    @TableField("service_id")
    private String serviceId;
    
    @TableField("service_type")
    private String serviceType;
    
    @TableField("service_name")
    private String serviceName;
    
    @TableField("container_name")
    private String containerName;
    
    @TableField("service_host")
    private String serviceHost;
    
    @TableField("service_port")
    private Integer servicePort;
    
    @TableField("health_check_url")
    private String healthCheckUrl;
    
    @TableField("config")
    private String config;  // JSON 字符串
    
    @TableField("installed_by")
    private String installedBy;
    
    @TableField("reference_count")
    private Integer referenceCount;
    
    @TableField("status")
    private String status;
    
    @TableField("work_dir")
    private String workDir;
    
    @TableField("created_at")
    private java.sql.Timestamp createdAt;
    
    @TableField("updated_at")
    private java.sql.Timestamp updatedAt;
}
