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
 * 插件状态变更日志实体
 * 用于记录插件状态的变化历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("appstore_plugin_state_change_log")
public class PluginStateChangeLog {
    
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 插件ID */
    @TableField("plugin_id")
    private String pluginId;
    
    /** 旧状态 */
    @TableField("old_state")
    private String oldState;
    
    /** 新状态 */
    @TableField("new_state")
    private String newState;
    
    /** 变更时间戳（毫秒） */
    @TableField("change_time")
    private Long changeTime;
    
    /** 操作人 */
    @TableField("operator")
    private String operator;
    
    /** 变更原因 */
    @TableField("reason")
    private String reason;
}
