package com.keqi.gress.plugin.appstore.service.middleware.workflow;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流步骤执行结果
 */
@Data
public class WorkflowStepResult {
    private String stepId;
    private boolean success;
    private String errorMessage;
    private Map<String, Object> data = new HashMap<>();
    
    public static WorkflowStepResult success(String stepId) {
        WorkflowStepResult result = new WorkflowStepResult();
        result.stepId = stepId;
        result.success = true;
        return result;
    }
    
    public static WorkflowStepResult success(String stepId, Map<String, Object> data) {
        WorkflowStepResult result = success(stepId);
        if (data != null) {
            result.data.putAll(data);
        }
        return result;
    }
    
    public static WorkflowStepResult failure(String stepId, String errorMessage) {
        WorkflowStepResult result = new WorkflowStepResult();
        result.stepId = stepId;
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }
}
