package com.keqi.gress.plugin.appstore.service.middleware.workflow;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流执行结果
 */
@Data
public class WorkflowExecutionResult {
    private boolean success;
    private List<WorkflowStepResult> stepResults = new ArrayList<>();
    private String errorMessage;
    
    public WorkflowExecutionResult(List<WorkflowStepResult> stepResults) {
        this.stepResults = stepResults;
        this.success = stepResults.stream().allMatch(WorkflowStepResult::isSuccess);
    }
    
    public WorkflowExecutionResult(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
    }
}
