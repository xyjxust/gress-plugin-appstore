package com.keqi.gress.plugin.appstore.domain.entity;

import com.keqi.gress.plugin.api.database.annotation.TableField;
import com.keqi.gress.plugin.api.database.annotation.TableName;
import com.keqi.gress.plugin.api.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 插件监控缓存实体
 * 用于存储插件监控数据的缓存
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("appstore_plugin_monitor_cache")
public class PluginMonitorCache extends BaseEntity {
    
    /** 插件ID */
    @TableField("plugin_id")
    private String pluginId;
    
    /** 缓存数据（JSON格式） */
    @TableField("cache_data")
    private String cacheData;
    
    /** 缓存类型（single: 单个插件, all: 所有插件） */
    @TableField("cache_type")
    private String cacheType;
    
    /** 缓存创建时间（时间戳，毫秒） */
    @TableField("cache_create_time")
    private Long cacheCreateTime;
    
    /** 过期时间（时间戳，毫秒） */
    @TableField("expire_time")
    private Long expireTime;
}
