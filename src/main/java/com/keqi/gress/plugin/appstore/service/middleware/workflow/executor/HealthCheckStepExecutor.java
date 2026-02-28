package com.keqi.gress.plugin.appstore.service.middleware.workflow.executor;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;

import com.keqi.gress.plugin.appstore.service.NodeManagementService;
import com.keqi.gress.plugin.appstore.service.middleware.execution.ExecutionEnvironment;
import com.keqi.gress.plugin.appstore.service.middleware.execution.ExecutionEnvironmentFactory;
import com.keqi.gress.plugin.appstore.service.middleware.execution.ExecutionResult;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.MiddlewareInstallContext;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStep;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStepExecutor;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStepResult;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 健康检查步骤执行器
 * 
 * 支持多种执行环境：
 * - local: 直接使用 HTTP 客户端检查本地服务
 * - ssh: 通过 SSH 执行 curl 命令检查远程服务
 * - docker-api: 类似于 local，但需要考虑 DOCKER_HOST
 * 
 * 执行环境信息从节点管理服务获取，通过 MiddlewareInstallContext 中的 targetNodeId 和 executionType 指定。
 */
@Service
@Slf4j
public class HealthCheckStepExecutor implements WorkflowStepExecutor {
    
    //private static final Log log = LogFactory.get(HealthCheckStepExecutor.class);
    
    private final ExecutionEnvironmentFactory envFactory = new ExecutionEnvironmentFactory();
    
    /**
     * 节点管理服务（可选，如果未注入则使用本地执行环境）
     */
    @Inject
    private NodeManagementService nodeManagementService;
    
    @Override
    public String getStepType() {
        return "health-check";
    }
    
    @Override
    public WorkflowStepResult execute(WorkflowStep step, MiddlewareInstallContext ctx) {
        try {
            HealthCheckStepConfig config = parseConfig(step.getConfig());
            
            String url = config.getUrl();
            String method = config.getMethod() != null ? config.getMethod() : "GET";
            int timeout = config.getTimeout() != null ? config.getTimeout() : 10;
            int retries = config.getRetries() != null ? config.getRetries() : 3;
            int retryInterval = config.getRetryInterval() != null ? 
                config.getRetryInterval() : 5;
            
            // 根据上下文自动获取执行环境（本地、SSH、Docker API）
            ExecutionEnvironment executionEnv = createExecutionEnvironment(ctx);
            log.debug("健康检查执行环境: type={}, identifier={}", 
                executionEnv.getType(), executionEnv.getIdentifier());
            
            // 执行健康检查
            HealthCheckResult result = performHealthCheck(
                url, method, timeout, retries, retryInterval, executionEnv);
            
            if (result.isSuccess()) {
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("statusCode", result.getStatusCode());
                resultData.put("body", result.getBody());
                resultData.put("retries", result.getRetries());
                resultData.put("executionEnv", executionEnv != null ? executionEnv.getType() : "local");
                
                log.info("健康检查成功: url={}, statusCode={}, retries={}, env={}", 
                    url, result.getStatusCode(), result.getRetries(),
                    executionEnv != null ? executionEnv.getType() : "local");
                return WorkflowStepResult.success(step.getId(), resultData);
            } else {
                return WorkflowStepResult.failure(step.getId(), 
                    "健康检查失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("执行健康检查步骤失败", e);
            return WorkflowStepResult.failure(step.getId(), 
                "执行异常: " + e.getMessage());
        }
    }
    
    /**
     * 执行健康检查（静态方法，可被其他执行器调用）
     * 
     * @param url 健康检查 URL
     * @param timeoutSeconds 超时时间（秒）
     * @param executionEnv 执行环境（如果为 null，则使用本地 HTTP 客户端）
     * @return true 如果健康检查成功
     */
    public static boolean performHealthCheck(
            String url, int timeoutSeconds, ExecutionEnvironment executionEnv) {
        return performHealthCheck(url, "GET", timeoutSeconds, 1, 0, executionEnv).isSuccess();
    }
    
    /**
     * 执行健康检查（完整版本，支持重试）
     * 
     * @param url 健康检查 URL
     * @param method HTTP 方法（GET/POST）
     * @param timeout 单次请求超时时间（秒）
     * @param retries 重试次数
     * @param retryInterval 重试间隔（秒）
     * @param executionEnv 执行环境（如果为 null，则使用本地 HTTP 客户端）
     * @return 健康检查结果
     */
    public static HealthCheckResult performHealthCheck(
            String url, String method, int timeout, int retries, 
            int retryInterval, ExecutionEnvironment executionEnv) {
        
        String envType = executionEnv != null ? executionEnv.getType() : "local";
        log.debug("执行健康检查: url={}, method={}, timeout={}, retries={}, envType={}", 
            url, method, timeout, retries, envType);
            
            for (int i = 0; i < retries; i++) {
                try {
                boolean success = false;
                int statusCode = 0;
                String body = "";
                
                if (executionEnv != null && "ssh".equals(executionEnv.getType())) {
                    // SSH 远程环境：通过 SSH 执行 curl 命令
                    success = performSshHealthCheck(executionEnv, url, method, timeout);
                    if (success) {
                        statusCode = 200;
                    }
                } else {
                    // 本地或 Docker API 环境：直接使用 HTTP 客户端
                    // 注意：对于 Docker API，服务可能运行在远程 Docker 主机上，
                    // 但健康检查 URL 应该指向可访问的地址（可能是远程主机的 IP）
                    java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(timeout))
                        .build();
                    
                    java.net.http.HttpRequest.Builder requestBuilder = 
                        java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create(url));
                    
                    if ("GET".equalsIgnoreCase(method)) {
                        requestBuilder.GET();
                    } else if ("POST".equalsIgnoreCase(method)) {
                        requestBuilder.POST(java.net.http.HttpRequest.BodyPublishers.noBody());
                    }
                    
                    java.net.http.HttpRequest request = requestBuilder
                        .timeout(Duration.ofSeconds(timeout))
                        .build();
                    
                    java.net.http.HttpResponse<String> response = client.send(
                        request, java.net.http.HttpResponse.BodyHandlers.ofString());
                    
                    statusCode = response.statusCode();
                    body = response.body();
                    success = (statusCode == 200);
                }
                
                if (success) {
                    return HealthCheckResult.success(statusCode, body, i + 1);
                } else {
                    log.warn("健康检查失败 (尝试 {}/{}): url={}, statusCode={}", 
                        i + 1, retries, url, statusCode);
                    }
                    
                } catch (Exception e) {
                log.warn("健康检查失败 (尝试 {}/{}): url={}, error={}", 
                    i + 1, retries, url, e.getMessage());
                }
                
                if (i < retries - 1) {
                try {
                    Thread.sleep(retryInterval * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return HealthCheckResult.failure("健康检查被中断");
                }
            }
        }
        
        return HealthCheckResult.failure("健康检查失败，已重试 " + retries + " 次");
    }
    
    /**
     * 创建执行环境
     * 根据 MiddlewareInstallContext 中的 targetNodeId 和 executionType 创建对应的执行环境
     */
    private ExecutionEnvironment createExecutionEnvironment(MiddlewareInstallContext ctx) {
        // 如果没有指定节点ID，使用本地执行环境
        if (ctx.getTargetNodeId() == null || ctx.getTargetNodeId().isBlank()) {
            log.debug("未指定目标节点，使用本地执行环境进行健康检查");
            return envFactory.create((NodeManagementService.NodeInfo) null);
        }

        // 如果节点管理服务不可用，使用本地执行环境
        if (nodeManagementService == null) {
            log.warn("节点管理服务不可用，使用本地执行环境进行健康检查");
            return envFactory.create((NodeManagementService.NodeInfo) null);
        }

        // 从节点管理服务获取节点信息
        Optional<NodeManagementService.NodeInfo> nodeInfoOpt =
            nodeManagementService.getNode(ctx.getTargetNodeId());

        if (nodeInfoOpt.isEmpty()) {
            log.warn("未找到节点: {}，使用本地执行环境进行健康检查", ctx.getTargetNodeId());
            return envFactory.create((NodeManagementService.NodeInfo) null);
        }

        NodeManagementService.NodeInfo nodeInfo = nodeInfoOpt.get();

        // 根据 executionType 和节点类型创建执行环境
        String executionType = ctx.getExecutionType();
        if (executionType != null && !executionType.isBlank()) {
            if (!executionType.equals(nodeInfo.getType())) {
                log.warn("执行环境类型 {} 与节点类型 {} 不匹配，使用节点类型",
                    executionType, nodeInfo.getType());
            }
        }

        ExecutionEnvironment env = envFactory.create(nodeInfo);
        log.info("创建健康检查执行环境: nodeId={}, type={}, identifier={}",
            ctx.getTargetNodeId(), env.getType(), env.getIdentifier());

        return env;
    }
    
    /**
     * 通过 SSH 执行健康检查
     */
    private static boolean performSshHealthCheck(
            ExecutionEnvironment executionEnv, String url, String method, int timeout) {
        try {
            // 构建 curl 命令
            List<String> command = new ArrayList<>();
            command.add("curl");
            command.add("-s");  // 静默模式
            command.add("-o");  // 输出到文件
            command.add("/dev/null");
            command.add("-w");  // 写入格式
            command.add("%{http_code}");  // 只输出 HTTP 状态码
            command.add("--max-time");  // 超时时间
            command.add(String.valueOf(timeout));
            
            if ("POST".equalsIgnoreCase(method)) {
                command.add("-X");
                command.add("POST");
            }
            
            command.add(url);
            
            // 执行命令
            ExecutionResult result = executionEnv.executeCommand(
                command, null, Duration.ofSeconds(timeout + 5));
            
            if (result.getExitCode() == 0) {
                String output = result.getOutput().trim();
                try {
                    int statusCode = Integer.parseInt(output);
                    return statusCode == 200;
                } catch (NumberFormatException e) {
                    log.warn("解析 curl 输出失败: {}", output);
                    return false;
                }
            } else {
                log.warn("SSH 健康检查命令执行失败: exitCode={}, error={}", 
                    result.getExitCode(), result.getErrorOutput());
                return false;
            }
            
        } catch (Exception e) {
            log.error("SSH 健康检查异常: url={}", url, e);
            return false;
        }
    }
    
    private HealthCheckStepConfig parseConfig(Map<String, Object> config) {
        HealthCheckStepConfig result = new HealthCheckStepConfig();
        if (config == null) {
            return result;
        }
        
        result.setUrl((String) config.get("url"));
        result.setMethod((String) config.get("method"));
        
        Object timeoutObj = config.get("timeout");
        if (timeoutObj instanceof Number) {
            result.setTimeout(((Number) timeoutObj).intValue());
        }
        
        Object retriesObj = config.get("retries");
        if (retriesObj instanceof Number) {
            result.setRetries(((Number) retriesObj).intValue());
        }
        
        Object retryIntervalObj = config.get("retry-interval");
        if (retryIntervalObj instanceof Number) {
            result.setRetryInterval(((Number) retryIntervalObj).intValue());
        }
        
        return result;
    }
    
    /**
     * 健康检查步骤配置
     */
    private static class HealthCheckStepConfig {
        private String url;
        private String method;
        private Integer timeout;
        private Integer retries;
        private Integer retryInterval;
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        
        public Integer getTimeout() { return timeout; }
        public void setTimeout(Integer timeout) { this.timeout = timeout; }
        
        public Integer getRetries() { return retries; }
        public void setRetries(Integer retries) { this.retries = retries; }
        
        public Integer getRetryInterval() { return retryInterval; }
        public void setRetryInterval(Integer retryInterval) { this.retryInterval = retryInterval; }
    }
    
    /**
     * 健康检查结果
     */
    public static class HealthCheckResult {
        private boolean success;
        private int statusCode;
        private String body;
        private int retries;
        private String errorMessage;
        
        public static HealthCheckResult success(int statusCode, String body, int retries) {
            HealthCheckResult result = new HealthCheckResult();
            result.success = true;
            result.statusCode = statusCode;
            result.body = body;
            result.retries = retries;
            return result;
        }
        
        public static HealthCheckResult failure(String errorMessage) {
            HealthCheckResult result = new HealthCheckResult();
            result.success = false;
            result.errorMessage = errorMessage;
            return result;
        }
        
        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
        public int getRetries() { return retries; }
        public String getErrorMessage() { return errorMessage; }
    }
}
