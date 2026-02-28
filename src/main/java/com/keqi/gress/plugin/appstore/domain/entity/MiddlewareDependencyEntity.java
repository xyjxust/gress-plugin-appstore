package com.keqi.gress.plugin.appstore.domain.entity;

import  com.keqi.gress.plugin.api.database.annotation.TableField;
import  com.keqi.gress.plugin.api.database.annotation.TableId;
import  com.keqi.gress.plugin.api.database.annotation.TableName;
import lombok.Data;

/**
 * 中间件依赖关系实体类
 */
@Data
@TableName("sys_middleware_dependency")
public class MiddlewareDependencyEntity {
    
    @TableId
    @TableField("id")
    private Long id;
    
    @TableField("middleware_id")
    private String middlewareId;
    
    @TableField("service_id")
    private String serviceId;
    
    @TableField("created_at")
    private java.sql.Timestamp createdAt;
}
