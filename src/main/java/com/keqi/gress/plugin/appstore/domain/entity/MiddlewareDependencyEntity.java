package com.keqi.gress.plugin.appstore.domain.entity;

import  com.keqi.gress.plugin.api.database.annotation.TableField;
import  com.keqi.gress.plugin.api.database.annotation.TableName;
import com.keqi.gress.plugin.api.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 中间件依赖关系实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appstore_middleware_dependency")
public class MiddlewareDependencyEntity extends BaseEntity {
    
    @TableField("middleware_id")
    private String middlewareId;
    
    @TableField("service_id")
    private String serviceId;
    
    @TableField("created_at")
    private java.sql.Timestamp createdAt;
}
