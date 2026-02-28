package com.keqi.gress.plugin.appstore.service.middleware.workflow;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.keqi.gress.plugin.appstore.service.middleware.MiddlewareInstallSsePublisher;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.executor.DockerComposeStepExecutor;

import java.util.*;

/**
 * 中间件工作流引擎
 */
public class MiddlewareWorkflowEngine {
    
    private static final Log log = LogFactory.get(MiddlewareWorkflowEngine.class);
    
    private final Map<String,WorkflowStepExecutor> stepExecutors = new HashMap<>();
    
    /**
     * SSE 消息发布器（可选，如果未注入则不推送步骤进度）
     */
    private MiddlewareInstallSsePublisher installSsePublisher;
    
    /**
     * 设置 SSE 消息发布器（由配置类注入）
     */
    public void setInstallSsePublisher(MiddlewareInstallSsePublisher installSsePublisher) {
        this.installSsePublisher = installSsePublisher;
    }
    
//    @PostConstruct
//    public void init() {
//        // 注册各种步骤执行器
//        registerExecutor(new DockerComposeStepExecutor());
//        registerExecutor(new ShellScriptStepExecutor());
//        registerExecutor(new WaitStepExecutor());
//        registerExecutor(new HealthCheckStepExecutor());
//
//        log.info("工作流引擎初始化完成，已注册 {} 个步骤执行器", stepExecutors.size());
//    }
    
    /**
     * 注册步骤执行器
     */
    public void registerExecutor(WorkflowStepExecutor executor) {
        stepExecutors.put(executor.getStepType(), executor);
        log.debug("注册步骤执行器: type={}", executor.getStepType());
    }
    
    /**
     * 执行安装工作流
     */
    public  com.keqi.gress.common.model.Result<WorkflowExecutionResult> executeInstallWorkflow(
           WorkflowDefinition workflow,
           MiddlewareInstallContext ctx) {
        
        List<WorkflowStep> steps = workflow.getInstallSteps();
        if (steps == null || steps.isEmpty()) {
            return  com.keqi.gress.common.model.Result.error("工作流没有定义安装步骤");
        }
        
        List<WorkflowStepResult> stepResults = new ArrayList<>();
        List<WorkflowStep> executedSteps = new ArrayList<>();
        
        try {
            // 严格按照配置的步骤执行，不进行任何自动插入或修改
            // 执行环境的选择由各个执行器根据上下文自动处理
            int totalSteps = steps.size();
            int currentStep = 0;
            
            for (WorkflowStep step : steps) {
                currentStep++;
                log.info("执行步骤: {}/{} - id={}, name={}, type={}", 
                    currentStep, totalSteps, step.getId(), step.getName(), step.getType());
                
                // 发送步骤进度信息
                if (installSsePublisher != null && ctx.getClientId() != null) {
                    installSsePublisher.sendStepProgress(
                        ctx.getClientId(), 
                        ctx.getMiddlewareId(), 
                        currentStep, 
                        totalSteps, 
                        step.getName() != null ? step.getName() : step.getId()
                    );
                }
                
                // 执行步骤
               WorkflowStepExecutor executor = stepExecutors.get(step.getType());
                if (executor == null) {
                    String errorMsg = "未知的步骤类型: " + step.getType();
                    log.error(errorMsg);
                    rollback(executedSteps, ctx);
                    return  com.keqi.gress.common.model.Result.error(errorMsg);
                }
                
               WorkflowStepResult stepResult = executor.execute(step, ctx);
                stepResults.add(stepResult);
                executedSteps.add(step);
                
                // 检查步骤执行结果
                if (!stepResult.isSuccess()) {
                    log.error("步骤执行失败: stepId={}, error={}", 
                        step.getId(), stepResult.getErrorMessage());
                    
                    // 根据错误处理策略处理
                   ErrorHandlingStrategy strategy = step.getOnError();
                    if (strategy ==ErrorHandlingStrategy.STOP) {
                        // 执行回滚
                        rollback(executedSteps, ctx);
                        return  com.keqi.gress.common.model.Result.error(
                            "步骤执行失败: " + stepResult.getErrorMessage());
                    } else if (strategy ==ErrorHandlingStrategy.ROLLBACK) {
                        rollback(executedSteps, ctx);
                        return  com.keqi.gress.common.model.Result.error(
                            "步骤执行失败，已回滚: " + stepResult.getErrorMessage());
                    }
                    // CONTINUE: 继续执行下一步
                }
            }
            
            return  com.keqi.gress.common.model.Result.success(
                new WorkflowExecutionResult(stepResults));
            
        } catch (Exception e) {
            log.error("工作流执行异常", e);
            // 执行回滚
            rollback(executedSteps, ctx);
            return  com.keqi.gress.common.model.Result.error(
                "工作流执行异常: " + e.getMessage());
        }
    }
    
    /**
     * 执行卸载工作流
     */
    public  com.keqi.gress.common.model.Result<WorkflowExecutionResult> executeUninstallWorkflow(
           WorkflowDefinition workflow,
           MiddlewareUninstallContext ctx) {
        
        List<WorkflowStep> steps = workflow.getUninstallSteps();
        if (steps == null || steps.isEmpty()) {
            log.warn("工作流没有定义卸载步骤");
            return  com.keqi.gress.common.model.Result.success(
                new WorkflowExecutionResult(new ArrayList<>()));
        }
        
        List<WorkflowStepResult> stepResults = new ArrayList<>();
        
        try {
            // 卸载步骤通常不需要回滚，按顺序执行即可
            for (WorkflowStep step : steps) {
                log.info("执行卸载步骤: id={}, name={}, type={}", 
                    step.getId(), step.getName(), step.getType());
                
               WorkflowStepExecutor executor = stepExecutors.get(step.getType());
                if (executor == null) {
                    log.warn("未知的步骤类型: {}, 跳过", step.getType());
                    continue;
                }
                
                // 卸载步骤需要适配不同的上下文类型
                // 这里简化处理，实际可能需要根据步骤类型创建不同的上下文
               WorkflowStepResult stepResult = executeUninstallStep(executor, step, ctx);
                stepResults.add(stepResult);
                
                if (!stepResult.isSuccess()) {
                    log.warn("卸载步骤执行失败: stepId={}, error={}", 
                        step.getId(), stepResult.getErrorMessage());
                    // 卸载步骤失败通常继续执行，不中断
                }
            }
            
            return  com.keqi.gress.common.model.Result.success(
                new WorkflowExecutionResult(stepResults));
            
        } catch (Exception e) {
            log.error("卸载工作流执行异常", e);
            return  com.keqi.gress.common.model.Result.error(
                "卸载工作流执行异常: " + e.getMessage());
        }
    }
    
    /**
     * 执行卸载步骤（适配不同的上下文）
     */
    private WorkflowStepResult executeUninstallStep(
           WorkflowStepExecutor executor,
           WorkflowStep step,
           MiddlewareUninstallContext ctx) {
        
        // 对于需要 MiddlewareInstallContext 的执行器，创建适配的上下文
        if (executor instanceof DockerComposeStepExecutor) {
            // Docker Compose 执行器需要适配
           MiddlewareInstallContext installCtx =MiddlewareInstallContext.builder()
                .middlewareId(ctx.getMiddlewareId())
                .workDir(ctx.getWorkDir())
                .operator(ctx.getOperator())
                .build();
            
            // 修改步骤配置为 down 操作
            if (step.getConfig() != null) {
                step.getConfig().put("action", "down");
            }
            
            return executor.execute(step, installCtx);
        }
        
        // 其他执行器可能需要类似的处理
        // 这里简化处理，实际可能需要更复杂的适配逻辑
       MiddlewareInstallContext installCtx =MiddlewareInstallContext.builder()
            .middlewareId(ctx.getMiddlewareId())
            .workDir(ctx.getWorkDir())
            .operator(ctx.getOperator())
            .build();
        
        return executor.execute(step, installCtx);
    }
    
    /**
     * 回滚已执行的步骤
     */
    private void rollback(List<WorkflowStep> executedSteps,MiddlewareInstallContext ctx) {
        if (executedSteps.isEmpty()) {
            return;
        }
        
        log.info("开始回滚，已执行 {} 个步骤", executedSteps.size());
        
        // 逆序回滚
        Collections.reverse(executedSteps);
        for (WorkflowStep step : executedSteps) {
            try {
               WorkflowStepExecutor executor = stepExecutors.get(step.getType());
                if (executor != null && executor.supportsRollback()) {
                    executor.rollback(step, ctx);
                    log.info("已回滚步骤: stepId={}", step.getId());
                } else {
                    log.debug("步骤不支持回滚: stepId={}, type={}", step.getId(), step.getType());
                }
            } catch (Exception e) {
                log.error("回滚步骤失败: stepId={}", step.getId(), e);
            }
        }
    }
}
