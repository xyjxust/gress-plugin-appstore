package com.keqi.gress.plugin.appstore.domain.entity;

import com.keqi.gress.plugin.api.database.annotation.IdType;
import com.keqi.gress.plugin.api.database.annotation.TableField;
import com.keqi.gress.plugin.api.database.annotation.TableId;
import com.keqi.gress.plugin.api.database.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 插件监控快照实体
 * 用于存储插件监控数据的历史快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("appstore_plugin_monitor_snapshot")
public class PluginMonitorSnapshot {
    
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 插件ID */
    @TableField("plugin_id")
    private String pluginId;
    
    /** 插件状态 */
    @TableField("state")
    private String state;
    
    /** 内存使用量（字节） */
    @TableField("memory_usage")
    private Long memoryUsage;
    
    /** 快照时间戳（毫秒） */
    @TableField("timestamp")
    private Long timestamp;
    
    /** 额外元数据（JSON格式） */
    @TableField("metadata")
    private String metadata;
}
