package com.keqi.gress.plugin.appstore.service.middleware.workflow;

import lombok.Data;

import java.util.Map;

/**
 * 工作流步骤定义
 */
@Data
public class WorkflowStep {
    /**
     * 步骤ID
     */
    private String id;
    
    /**
     * 步骤类型：docker-compose, shell-script, wait, health-check 等
     */
    private String type;
    
    /**
     * 步骤名称
     */
    private String name;
    
    /**
     * 步骤配置（类型相关）
     */
    private Map<String, Object> config;
    
    /**
     * 错误处理策略
     */
    private ErrorHandlingStrategy onError = ErrorHandlingStrategy.STOP;
}
