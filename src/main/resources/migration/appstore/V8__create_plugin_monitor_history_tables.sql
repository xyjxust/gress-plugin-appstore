-- App Store 插件：插件监控缓存表

CREATE TABLE IF NOT EXISTS ${pluginId}_plugin_monitor_cache (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  plugin_id    VARCHAR(128)             COMMENT '插件ID（单个插件缓存时使用）',
  cache_data   LONGTEXT        NOT NULL COMMENT '缓存数据（JSON格式）',
  cache_type   VARCHAR(32)     NOT NULL COMMENT '缓存类型：all=所有插件, single=单个插件',
  create_time  BIGINT          NOT NULL COMMENT '创建时间（时间戳，毫秒）',
  expire_time  BIGINT          NOT NULL COMMENT '过期时间（时间戳，毫秒）',
  PRIMARY KEY (id),
  KEY idx_cache_type (cache_type),
  KEY idx_plugin_id (plugin_id),
  KEY idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='插件监控数据缓存表';

-- App Store 插件：插件监控历史数据表

-- 插件监控快照表
CREATE TABLE IF NOT EXISTS ${pluginId}_plugin_monitor_snapshot (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  plugin_id    VARCHAR(128)    NOT NULL COMMENT '插件ID',
  state        VARCHAR(32)     NOT NULL COMMENT '插件状态：STARTED, STOPPED, CREATED, DISABLED',
  memory_usage BIGINT                   COMMENT '内存使用量（字节）',
  timestamp    BIGINT          NOT NULL COMMENT '快照时间戳（毫秒）',
  metadata     TEXT                     COMMENT '额外元数据（JSON格式）',
  PRIMARY KEY (id),
  KEY idx_plugin_timestamp (plugin_id, timestamp),
  KEY idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='插件监控快照表';

-- 插件状态变更日志表
CREATE TABLE IF NOT EXISTS ${pluginId}_plugin_state_change_log (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  plugin_id    VARCHAR(128)    NOT NULL COMMENT '插件ID',
  old_state    VARCHAR(32)              COMMENT '旧状态',
  new_state    VARCHAR(32)     NOT NULL COMMENT '新状态',
  change_time  BIGINT          NOT NULL COMMENT '变更时间戳（毫秒）',
  operator     VARCHAR(64)              COMMENT '操作人',
  reason       TEXT                     COMMENT '变更原因',
  PRIMARY KEY (id),
  KEY idx_plugin_time (plugin_id, change_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='插件状态变更日志表';
