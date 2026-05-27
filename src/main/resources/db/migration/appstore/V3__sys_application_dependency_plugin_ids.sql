ALTER TABLE `sys_application`
    ADD COLUMN `dependency_plugin_ids` varchar(1024) DEFAULT NULL COMMENT '依赖的插件ID列表（JSON array，如 ["iam","verification-channel"]）' AFTER `tags`;
