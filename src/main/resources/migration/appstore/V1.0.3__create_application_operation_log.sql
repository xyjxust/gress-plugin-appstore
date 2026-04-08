-- 创建应用操作日志表
CREATE TABLE IF NOT EXISTS sys_application_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    application_id BIGINT NOT NULL COMMENT '关联的应用ID',
    application_code VARCHAR(100) COMMENT '应用代码',
    application_name VARCHAR(200) COMMENT '应用名称',
    plugin_id VARCHAR(100) COMMENT '插件ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型：START, STOP, RESTART, INSTALL, UNINSTALL, UPGRADE, ROLLBACK, CONFIG_UPDATE',
    operation_desc VARCHAR(500) COMMENT '操作描述',
    operator_id VARCHAR(100) COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人名称',
    status VARCHAR(20) NOT NULL COMMENT '操作结果：SUCCESS / FAIL',
    message TEXT COMMENT '失败原因或补充信息',
    before_data TEXT COMMENT '操作前数据（JSON格式）',
    after_data TEXT COMMENT '操作后数据（JSON格式）',
    duration BIGINT COMMENT '操作耗时（毫秒）',
    client_ip VARCHAR(50) COMMENT '客户端IP',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_application_id (application_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用操作日志表';
