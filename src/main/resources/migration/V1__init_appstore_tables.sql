-- App Store plugin schema (Flyway)
-- Mirrors AppStoreSchemaService creation to keep DAO queries consistent

CREATE TABLE IF NOT EXISTS appstore_manager (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  plugin_id       VARCHAR(128)    NOT NULL COMMENT 'PF4J 插件 ID（plugin.id）',
  plugin_name     VARCHAR(255)    NOT NULL COMMENT '展示名称',
  plugin_type     VARCHAR(32)     NOT NULL COMMENT '插件类型（TRIGGER/TASK/APPLICATION）',
  icon            VARCHAR(255)             COMMENT '图标路径',
  summary         VARCHAR(512)             COMMENT '摘要简介',
  description     LONGTEXT                 COMMENT '富文本描述信息（HTML/Markdown）',
  current_version VARCHAR(64)              COMMENT '当前已上线版本',
  latest_version  VARCHAR(64)              COMMENT '最新上传版本',
  status          VARCHAR(32)    NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/ONLINE/OFFLINE）',
  feedback_count  INT UNSIGNED   NOT NULL DEFAULT 0 COMMENT '反馈总数',
  created_by      VARCHAR(64)              COMMENT '创建人',
  updated_by      VARCHAR(64)              COMMENT '更新人',
  create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_id (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用商店-插件管理表';

CREATE TABLE IF NOT EXISTS appstore_version (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  manager_id   BIGINT UNSIGNED NOT NULL COMMENT '关联 appstore_manager.id',
  plugin_id    VARCHAR(128)    NOT NULL COMMENT '冗余插件 ID',
  version      VARCHAR(64)     NOT NULL COMMENT '版本号',
  change_log   LONGTEXT                 COMMENT '版本更新说明（富文本）',
  download_url VARCHAR(1024)            COMMENT '插件包下载地址/存储路径',
  file_hash    VARCHAR(128)             COMMENT '文件校验值（可选）',
  created_by   VARCHAR(64)              COMMENT '上传人',
  create_time  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (id),
  KEY idx_manager_id (manager_id),
  KEY idx_plugin_version (plugin_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用商店-插件版本历史表';

