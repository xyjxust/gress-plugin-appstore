CREATE TABLE `appstore_stack_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stack_id` varchar(64) NOT NULL COMMENT 'stack 标识（如 a/b）',
  `name` varchar(255) DEFAULT NULL COMMENT '展示名称',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',

  `mysql_database` varchar(128) NOT NULL COMMENT 'MySQL 数据库名，如 gress_a',
  `redis_db` int NOT NULL COMMENT 'Redis DB，如 0/1',

  `runtime_base_dir` varchar(512) DEFAULT NULL COMMENT '远程运行目录，如 /home/gress/stack-a/runtime',
  `web_image` varchar(255) DEFAULT NULL COMMENT 'gress-web 镜像',
  `fronted_image` varchar(255) DEFAULT NULL COMMENT 'gress-fronted 镜像',
  `version_tag` varchar(128) DEFAULT NULL COMMENT '版本 tag（可选）',

  `deploy_fronted` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否部署前端（默认）',
  `join_nginx` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否加入入口 Nginx upstream（默认）',
  `entry_node_id` varchar(128) DEFAULT NULL COMMENT '入口节点 nodeId（空=使用第一个/默认）',

  `domain` varchar(255) DEFAULT NULL COMMENT '域名（可选，优先用于多节点）',
  `web_host_port` int DEFAULT NULL COMMENT 'web 对外端口（单节点模式可用）',
  `fronted_host_port` int DEFAULT NULL COMMENT 'fronted 对外端口（单节点模式可用）',

  `extra_config` json DEFAULT NULL COMMENT '扩展配置（JSON）',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(128) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(128) DEFAULT NULL COMMENT '更新人',
  `namespace_code` varchar(128) DEFAULT NULL COMMENT '命名空间代码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stack_id` (`stack_id`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多套部署配置';

CREATE TABLE `appstore_stack_target` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stack_id` varchar(64) NOT NULL COMMENT 'stack 标识',
  `node_id` varchar(128) NOT NULL COMMENT '节点 nodeId',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',

  `roles` varchar(128) NOT NULL DEFAULT 'web' COMMENT '角色：web/front/lb（逗号分隔）',
  `web_port` int DEFAULT NULL COMMENT 'web 服务端口（容器/主机端口视实现约定）',
  `fronted_port` int DEFAULT NULL COMMENT 'fronted 服务端口（可选）',

  `last_deployed_version` varchar(128) DEFAULT NULL COMMENT '上次部署版本（可选）',
  `health_status` varchar(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '健康：UNKNOWN/UP/DOWN',
  `last_health_check_time` bigint DEFAULT NULL COMMENT '上次健康检查时间（epoch ms）',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(128) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(128) DEFAULT NULL COMMENT '更新人',
  `namespace_code` varchar(128) DEFAULT NULL COMMENT '命名空间代码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stack_node` (`stack_id`, `node_id`),
  KEY `idx_stack` (`stack_id`),
  KEY `idx_node` (`node_id`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='stack 到节点的部署目标';

CREATE TABLE `appstore_stack_deployment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `deployment_id` varchar(64) NOT NULL COMMENT '部署任务ID（业务唯一）',
  `stack_id` varchar(64) NOT NULL COMMENT 'stack 标识',
  `requested_version` varchar(128) DEFAULT NULL COMMENT '期望部署版本（镜像 tag 或 jar 版本）',

  `mode` varchar(32) NOT NULL DEFAULT 'SINGLE_NODE' COMMENT 'SINGLE_NODE/CLUSTER',
  `strategy` varchar(32) NOT NULL DEFAULT 'ALL_AT_ONCE' COMMENT 'ALL_AT_ONCE/ROLLING',

  `deploy_fronted` tinyint(1) NOT NULL DEFAULT '1' COMMENT '本次是否部署前端',
  `join_nginx` tinyint(1) NOT NULL DEFAULT '1' COMMENT '本次是否加入 Nginx upstream',

  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
  `message` text COMMENT '失败原因/补充信息',

  `started_at` bigint DEFAULT NULL COMMENT '开始时间（epoch ms）',
  `ended_at` bigint DEFAULT NULL COMMENT '结束时间（epoch ms）',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(128) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(128) DEFAULT NULL COMMENT '更新人',
  `namespace_code` varchar(128) DEFAULT NULL COMMENT '命名空间代码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_deployment_id` (`deployment_id`),
  KEY `idx_stack` (`stack_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='stack 部署任务';

CREATE TABLE `appstore_stack_deployment_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `deployment_id` varchar(64) NOT NULL COMMENT '部署任务ID',
  `node_id` varchar(128) DEFAULT NULL COMMENT '节点 nodeId（可为空）',
  `step` varchar(64) NOT NULL COMMENT '步骤标识',
  `status` varchar(16) NOT NULL COMMENT 'SUCCESS/FAIL/RUNNING',
  `output` mediumtext COMMENT '输出（截断存储）',
  `timestamp` bigint NOT NULL COMMENT '时间（epoch ms）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(128) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(128) DEFAULT NULL COMMENT '更新人',
  `namespace_code` varchar(128) DEFAULT NULL COMMENT '命名空间代码',
  PRIMARY KEY (`id`),
  KEY `idx_deployment` (`deployment_id`),
  KEY `idx_node` (`node_id`),
  KEY `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='stack 部署日志';

