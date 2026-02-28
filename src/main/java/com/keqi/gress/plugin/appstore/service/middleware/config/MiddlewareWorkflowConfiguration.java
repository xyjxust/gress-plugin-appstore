package com.keqi.gress.plugin.appstore.service.middleware.config;


import  com.keqi.gress.common.plugin.annotion.Bean;
import  com.keqi.gress.common.plugin.annotion.Configuration;
import com.keqi.gress.plugin.appstore.service.middleware.MiddlewareInstallSsePublisher;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.MiddlewareWorkflowEngine;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowDefinitionParser;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.executor.DockerComposeStepExecutor;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.executor.HealthCheckStepExecutor;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.executor.ShellScriptStepExecutor;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.executor.WaitStepExecutor;


/**
 * 中间件工作流引擎 Spring 配置（提供给 AppStore 插件注入使用）。
 */
@Configuration
public class MiddlewareWorkflowConfiguration {

    @Bean
    public WorkflowDefinitionParser workflowDefinitionParser() {
        return new WorkflowDefinitionParser();
    }

//    @Bean
//    public DockerComposeStepExecutor dockerComposeStepExecutor(NodeManagementService nodeManagementService) {
//        return new DockerComposeStepExecutor();
//    }

    @Bean
    public ShellScriptStepExecutor shellScriptStepExecutor() {
        return new ShellScriptStepExecutor();
    }

    @Bean
    public WaitStepExecutor waitStepExecutor() {
        return new WaitStepExecutor();
    }

    @Bean
    public HealthCheckStepExecutor healthCheckStepExecutor() {
        return new HealthCheckStepExecutor();
    }

    @Bean
    public MiddlewareWorkflowEngine middlewareWorkflowEngine(
            DockerComposeStepExecutor dockerComposeStepExecutor,
            ShellScriptStepExecutor shellScriptStepExecutor,
            WaitStepExecutor waitStepExecutor,
            HealthCheckStepExecutor healthCheckStepExecutor,
            MiddlewareInstallSsePublisher installSsePublisher
    ) {
        // 创建引擎实例并手动注入依赖
        MiddlewareWorkflowEngine engine = new MiddlewareWorkflowEngine();
        engine.setInstallSsePublisher(installSsePublisher);
        engine.registerExecutor(dockerComposeStepExecutor);
        engine.registerExecutor(shellScriptStepExecutor);
        engine.registerExecutor(waitStepExecutor);
        engine.registerExecutor(healthCheckStepExecutor);
        return engine;
    }
}

