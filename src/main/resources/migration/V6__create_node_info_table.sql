-- App Store 插件：节点管理表（用于远程部署/中间件安装目标节点）

CREATE TABLE IF NOT EXISTS sys_node_info (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  node_id      VARCHAR(128)    NOT NULL COMMENT '节点ID（业务唯一）',
  name         VARCHAR(255)             COMMENT '节点名称',
  type         VARCHAR(32)     NOT NULL COMMENT '节点类型：local | ssh | docker-api',
  description  VARCHAR(512)             COMMENT '描述',
  enabled      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
  config       LONGTEXT                 COMMENT '节点配置（JSON）',
  created_at   BIGINT                   COMMENT '创建时间（epoch ms）',
  updated_at   BIGINT                   COMMENT '更新时间（epoch ms）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_node_id (node_id),
  KEY idx_type (type),
  KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中间件/应用远程部署节点信息';

