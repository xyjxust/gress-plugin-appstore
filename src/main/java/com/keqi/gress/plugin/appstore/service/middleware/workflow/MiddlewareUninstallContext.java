package com.keqi.gress.plugin.appstore.service.middleware.workflow;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;

/**
 * 中间件卸载上下文
 */
@Data
@Builder
public class MiddlewareUninstallContext {
    /**
     * 中间件ID
     */
    private String middlewareId;
    
    /**
     * 工作目录
     */
    private Path workDir;
    
    /**
     * 操作人
     */
    private String operator;
}
