package com.keqi.gress.plugin.appstore.service.middleware.workflow;

/**
 * 错误处理策略
 */
public enum ErrorHandlingStrategy {
    /**
     * 停止执行，不回滚
     */
    STOP,
    
    /**
     * 继续执行下一步
     */
    CONTINUE,
    
    /**
     * 回滚已执行的步骤
     */
    ROLLBACK
}
