package com.keqi.gress.plugin.appstore.service.middleware.workflow;

/**
 * 工作流步骤执行器接口
 */
public interface WorkflowStepExecutor {
    
    /**
     * 执行步骤
     */
    WorkflowStepResult execute(WorkflowStep step, MiddlewareInstallContext ctx);
    
    /**
     * 是否支持回滚
     */
    default boolean supportsRollback() {
        return false;
    }
    
    /**
     * 回滚步骤
     */
    default void rollback(WorkflowStep step, MiddlewareInstallContext ctx) {
        // 默认不支持回滚
    }
    
    /**
     * 获取步骤类型
     */
    String getStepType();
}
