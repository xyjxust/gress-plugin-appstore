package com.keqi.gress.plugin.appstore.service.middleware.execution;

import lombok.Builder;
import lombok.Data;

/**
 * 执行结果
 */
@Data
@Builder
public class ExecutionResult {
    /**
     * 退出码
     */
    private int exitCode;
    
    /**
     * 标准输出
     */
    private String output;
    
    /**
     * 错误输出
     */
    private String errorOutput;
    
    /**
     * 是否成功（exitCode == 0）
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
