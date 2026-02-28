package com.keqi.gress.plugin.appstore.service.middleware.workflow.executor;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;

import com.keqi.gress.plugin.appstore.service.MiddlewareManagementService;
import com.keqi.gress.plugin.appstore.service.NodeManagementService;
import com.keqi.gress.plugin.appstore.service.install.JarResourceExtractor;
import com.keqi.gress.plugin.appstore.service.middleware.MiddlewareInstallSsePublisher;
import com.keqi.gress.plugin.appstore.service.middleware.execution.ExecutionEnvironment;
import com.keqi.gress.plugin.appstore.service.middleware.execution.ExecutionEnvironmentFactory;
import com.keqi.gress.plugin.appstore.service.middleware.util.DockerComposeRunner;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.MiddlewareInstallContext;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStep;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStepExecutor;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStepResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Docker Compose 步骤执行器
 * 
 * 支持多种执行环境：
 * - 本地执行（默认）
 * - SSH 远程执行
 * - Docker API 远程执行
 * 
 * 执行环境信息从节点管理服务获取，通过 MiddlewareInstallContext 中的 targetNodeId 和 executionType 指定。
 */
@Service
@Slf4j
public class DockerComposeStepExecutor implements WorkflowStepExecutor {
    
  //  private static final Log log = LogFactory.get(DockerComposeStepExecutor.class);
    
    private final JarResourceExtractor extractor = new JarResourceExtractor();
    private final ExecutionEnvironmentFactory envFactory = new ExecutionEnvironmentFactory();
    
    /**
     * 节点管理服务（可选，如果未注入则使用本地执行环境）
     */
    @Inject
    private NodeManagementService nodeManagementService;

    /**
     * SSE 日志发布器（可选，如果未注入则不推送日志）
     */
    @Inject
    private MiddlewareInstallSsePublisher installSsePublisher;
    
    @Override
    public String getStepType() {
        return "docker-compose";
    }
    
    @Override
    public WorkflowStepResult execute(WorkflowStep step, MiddlewareInstallContext ctx) {
        try {
            DockerComposeStepConfig config = parseConfig(step.getConfig());
            // 读取 file 字段以消除未使用告警（当前 extractor 固定提取为 docker-compose.yml）
            // 后续如需支持不同 compose 文件名，可扩展 JarResourceExtractor。
            String ignoredFileName = config.getFile();
            if (ignoredFileName != null && !ignoredFileName.isBlank()) {
                log.debug("docker-compose step config file={}", ignoredFileName);
            }
            
            // 发送SSE日志：开始执行Docker Compose步骤
            if (installSsePublisher != null && ctx.getClientId() != null) {
                installSsePublisher.sendLog(ctx.getClientId(), ctx.getMiddlewareId(),
                    "开始执行Docker Compose步骤: " + (config.getAction() != null ? config.getAction() : "up"));
            }

            // 1. 创建执行环境（传入SSE日志回调）
            ExecutionEnvironment executionEnv = createExecutionEnvironment(ctx);

            // 创建DockerComposeRunner，传入SSE日志回调
            DockerComposeRunner.LogCallback logCallback = ctx.getClientId() != null && installSsePublisher != null
                ? (message) -> installSsePublisher.sendLog(ctx.getClientId(), ctx.getMiddlewareId(), message)
                : null;
            DockerComposeRunner runner = new DockerComposeRunner(executionEnv, logCallback);

            log.info("使用执行环境: type={}, identifier={}",
                executionEnv.getType(), executionEnv.getIdentifier());
            
            // 2. 提取 docker-compose.yml
            Path workDir = ctx.getWorkDir();
            Path composeFile = extractor.extractDockerCompose(
                ctx.getMiddlewarePackage(), workDir).orElse(null);
            
            if (composeFile == null || !Files.exists(composeFile)) {
                return WorkflowStepResult.failure(
                    step.getId(), "未找到 docker-compose.yml");
            }
            
            // 3. 准备环境变量（注入依赖服务信息）
            Map<String, String> envVars = prepareEnvironmentVariables(ctx);
            
            // 4. 执行 docker compose
            String projectName = config.getProjectName() != null ? 
                config.getProjectName() : buildProjectName(ctx.getMiddlewareId());
            
            String action = config.getAction() != null ? 
                config.getAction() : "up";
            
            // 发送SSE日志：开始执行Docker Compose命令
            if (installSsePublisher != null && ctx.getClientId() != null) {
                installSsePublisher.sendLog(ctx.getClientId(), ctx.getMiddlewareId(),
                    String.format("执行Docker Compose命令: %s -p %s", action, projectName));
            }

            DockerComposeRunner.ResultExec result;
            if ("up".equals(action)) {
                result = runner.execComposeUp(composeFile, projectName, envVars);
            } else if ("down".equals(action)) {
                result = runner.execComposeDown(composeFile, projectName, config.isRemoveVolumes(), envVars);
            } else {
                return WorkflowStepResult.failure(
                    step.getId(), "不支持的操作: " + action);
            }
            
            if (result.exitCode != 0) {
                // 发送SSE日志：Docker Compose执行失败
                if (installSsePublisher != null && ctx.getClientId() != null) {
                    installSsePublisher.sendError(ctx.getClientId(), ctx.getMiddlewareId(),
                        "Docker Compose 执行失败: " + result.output);
                }
                return WorkflowStepResult.failure(
                    step.getId(), "Docker Compose 执行失败: " + result.output);
            }
            
            // 发送SSE日志：Docker Compose执行成功
            if (installSsePublisher != null && ctx.getClientId() != null) {
                installSsePublisher.sendLog(ctx.getClientId(), ctx.getMiddlewareId(),
                    String.format("Docker Compose %s 命令执行成功", action));
            }
            
            // 注意：健康检查已从 DockerComposeStepExecutor 中移除
            // 架构原则：单一职责 - DockerComposeStepExecutor 只负责 Docker Compose 操作
            // 如果需要健康检查，应该在工作流中配置独立的 health-check 步骤
            // 如果配置了 wait-for-health，给出警告提示
            if (config.isWaitForHealth() && config.getHealthCheckUrl() != null) {
                log.warn("检测到 docker-compose 步骤配置了 wait-for-health，该配置已废弃。" +
                    "请在工作流中配置独立的 health-check 步骤。配置的 URL: {}", config.getHealthCheckUrl());
            }
            // 为了向后兼容，MiddlewareWorkflowEngine 会自动为配置了 wait-for-health 的步骤插入 health-check 步骤
            
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("projectName", projectName);
            resultData.put("composeFile", composeFile.toString());
            resultData.put("executionEnv", executionEnv.getType());
            resultData.put("executionEnvIdentifier", executionEnv.getIdentifier());
            
            return WorkflowStepResult.success(step.getId(), resultData);
            
        } catch (Exception e) {
            log.error("执行 Docker Compose 步骤失败", e);
            return WorkflowStepResult.failure(step.getId(), 
                "执行异常: " + e.getMessage());
        }
    }
    
    /**
     * 创建执行环境
     * 根据 MiddlewareInstallContext 中的 targetNodeId 和 executionType 创建对应的执行环境
     */
    private ExecutionEnvironment createExecutionEnvironment(MiddlewareInstallContext ctx) {
        // 创建SSE日志回调
        ExecutionEnvironmentFactory.LogCallback logCallback = ctx.getClientId() != null && installSsePublisher != null
            ? (message) -> installSsePublisher.sendLog(ctx.getClientId(), ctx.getMiddlewareId(), message)
            : null;

        // 如果没有指定节点ID，使用本地执行环境
        if (ctx.getTargetNodeId() == null || ctx.getTargetNodeId().isBlank()) {
            log.debug("未指定目标节点，使用本地执行环境");
            return envFactory.create((NodeManagementService.NodeInfo) null, logCallback);
        }

        // 如果节点管理服务不可用，使用本地执行环境
        if (nodeManagementService == null) {
            log.warn("节点管理服务不可用，使用本地执行环境");
            return envFactory.create((NodeManagementService.NodeInfo) null, logCallback);
        }

        // 从节点管理服务获取节点信息
        Optional<NodeManagementService.NodeInfo> nodeInfoOpt =
            nodeManagementService.getNode(ctx.getTargetNodeId());

        if (nodeInfoOpt.isEmpty()) {
            log.warn("节点不存在: {}, 使用本地执行环境", ctx.getTargetNodeId());
            return envFactory.create((NodeManagementService.NodeInfo) null, logCallback);
        }

        NodeManagementService.NodeInfo nodeInfo = nodeInfoOpt.get();

        // 如果指定了执行环境类型，验证是否匹配
        String executionType = ctx.getExecutionType();
        if (executionType != null && !executionType.isBlank()) {
            if (!executionType.equals(nodeInfo.getType())) {
                log.warn("执行环境类型 {} 与节点类型 {} 不匹配，使用节点类型",
                    executionType, nodeInfo.getType());
            }
        }

        // 创建执行环境（传入日志回调）
        ExecutionEnvironment env = envFactory.create(nodeInfo, logCallback);
        log.info("创建执行环境: nodeId={}, type={}, identifier={}",
            ctx.getTargetNodeId(), env.getType(), env.getIdentifier());

        return env;
    }
    
    @Override
    public boolean supportsRollback() {
        return true;
    }
    
    @Override
    public void rollback(WorkflowStep step, MiddlewareInstallContext ctx) {
        try {
            // 创建执行环境（与执行时使用相同的环境）
            ExecutionEnvironment executionEnv = createExecutionEnvironment(ctx);
            DockerComposeRunner runner = new DockerComposeRunner(executionEnv);
            
            DockerComposeStepConfig config = parseConfig(step.getConfig());
            String projectName = config.getProjectName() != null ? 
                config.getProjectName() : buildProjectName(ctx.getMiddlewareId());
            
            Path workDir = ctx.getWorkDir();
            Path composeFile = workDir.resolve("docker-compose.yml");
            
            if (Files.exists(composeFile)) {
                Map<String, String> envVars = prepareEnvironmentVariables(ctx);
                runner.execComposeDown(composeFile, projectName, false, envVars);
                log.info("已回滚 Docker Compose: projectName={}, env={}", 
                    projectName, executionEnv.getIdentifier());
            }
        } catch (Exception e) {
            log.error("回滚 Docker Compose 失败", e);
        }
    }
    
    /**
     * 准备环境变量（注入依赖服务信息和安装配置）
     */
    private Map<String, String> prepareEnvironmentVariables(MiddlewareInstallContext ctx) {
        Map<String, String> envVars = new HashMap<>();
        
        // 1. 首先注入安装配置（用户在前端填写的配置）
        if (ctx.getInstallConfig() != null && !ctx.getInstallConfig().isEmpty()) {
            for (Map.Entry<String, Object> entry : ctx.getInstallConfig().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value != null) {
                    envVars.put(key, String.valueOf(value));
                }
            }
            log.debug("注入安装配置到环境变量: {}", ctx.getInstallConfig().keySet());
        }
        
        // 2. 注入依赖服务连接信息
        if (ctx.getResolvedServices() != null && !ctx.getResolvedServices().isEmpty()) {
            for (Map.Entry<String,  MiddlewareManagementService.MiddlewareServiceInfo> entry
                    : ctx.getResolvedServices().entrySet()) {
                String serviceId = entry.getKey();
                MiddlewareManagementService.MiddlewareServiceInfo service = entry.getValue();
                
                // 设置服务连接信息为环境变量
                // 例如：MINIO_HOST, MINIO_PORT, MINIO_ACCESS_KEY 等
                String prefix = serviceId.toUpperCase().replaceAll("-", "_");
                envVars.put(prefix + "_HOST", service.getServiceHost() != null ? service.getServiceHost() : "localhost");
                if (service.getServicePort() != null) {
                    envVars.put(prefix + "_PORT", String.valueOf(service.getServicePort()));
                }
                if (service.getHealthCheckUrl() != null) {
                    envVars.put(prefix + "_HEALTH_URL", service.getHealthCheckUrl());
                }
                
                // 注入服务配置（如密码、连接信息等）
                if (service.getConfig() != null) {
                    for (Map.Entry<String, Object> configEntry : service.getConfig().entrySet()) {
                        String key = prefix + "_" + configEntry.getKey().toUpperCase().replaceAll("-", "_");
                        envVars.put(key, String.valueOf(configEntry.getValue()));
                    }
                }
            }
        }
        
        // 3. 自动构建依赖服务的连接地址（如果用户未配置）
        // 通过命名约定自动推断：如果配置类中有 {serviceId}Endpoints 或 {serviceId}Address 字段，
        // 且该字段未配置，则从依赖服务信息自动构建
        buildDependencyServiceAddresses(envVars, ctx.getResolvedServices());
        
        log.debug("准备环境变量: {}", envVars.keySet());
        return envVars;
    }
    
    /**
     * 自动构建依赖服务的连接地址（通用机制）
     * 
     * 通过命名约定自动推断依赖服务的连接地址：
     * 1. 遍历所有已解析的依赖服务
     * 2. 对于每个依赖服务，检查是否存在对应的环境变量：
     *    - {serviceId}Endpoints（如 etcd-installer -> etcdEndpoints）
     *    - {serviceId}Address（如 minio-installer -> minioAddress）
     *    - {serviceId}Url（如 redis-installer -> redisUrl）
     * 3. 如果不存在，则从依赖服务信息自动构建
     * 
     * 优先级：用户配置 > 依赖服务信息 > 默认值（在 docker-compose.yml 中）
     * 
     * @param envVars 环境变量 Map
     * @param resolvedServices 已解析的依赖服务信息
     */
    private void buildDependencyServiceAddresses(
            Map<String, String> envVars,
            Map<String,  MiddlewareManagementService.MiddlewareServiceInfo> resolvedServices) {
        
        if (resolvedServices == null || resolvedServices.isEmpty()) {
            return;
    }
    
        // 遍历所有已解析的依赖服务
        for (Map.Entry<String,  MiddlewareManagementService.MiddlewareServiceInfo> entry
                : resolvedServices.entrySet()) {
            String serviceId = entry.getKey();  // 如 "etcd-installer", "minio-installer"
            MiddlewareManagementService.MiddlewareServiceInfo service = entry.getValue();
            
            // 将 serviceId 转换为可能的配置字段名（移除 "-installer" 后缀，转为驼峰命名）
            String baseName = normalizeServiceIdToFieldName(serviceId);
            
            // 检查常见的连接地址字段名模式（需要完整地址：host:port）
            String[] addressFieldPatterns = {
                baseName + "Endpoints",    // 如 "etcdEndpoints"
                baseName + "Address",      // 如 "minioAddress"
                baseName + "Url"           // 如 "redisUrl"
            };
            
            for (String fieldName : addressFieldPatterns) {
                // 如果该字段已配置，跳过
                if (envVars.containsKey(fieldName)) {
                    continue;
                }
                
                // 从依赖服务信息构建连接地址
                String address = buildServiceAddress(service, envVars, serviceId);
                if (address != null) {
                    envVars.put(fieldName, address);
                    log.debug("自动构建依赖服务连接地址: {} = {} (从依赖服务: {})", 
                        fieldName, address, serviceId);
                    break;  // 找到一个匹配的字段名后，不再尝试其他模式
                }
            }
        }
    }
    
    /**
     * 将服务ID转换为配置字段名
     * 例如：
     * - "etcd-installer" -> "etcd"
     * - "minio-installer" -> "minio"
     * - "redis-installer" -> "redis"
     * - "mysql-installer" -> "mysql"
     * 
     * @param serviceId 服务ID
     * @return 配置字段名基础部分
     */
    private String normalizeServiceIdToFieldName(String serviceId) {
        if (serviceId == null || serviceId.isEmpty()) {
            return "";
        }
        
        // 移除常见的后缀
        String base = serviceId;
        if (base.endsWith("-installer")) {
            base = base.substring(0, base.length() - "-installer".length());
        }
        
        // 将 kebab-case 转换为 camelCase
        // 例如: "etcd-installer" -> "etcd", "minio-installer" -> "minio"
        // 如果包含多个连字符，只处理第一个单词（通常服务名都是单个单词）
        if (base.contains("-")) {
            String[] parts = base.split("-");
            if (parts.length > 0) {
                base = parts[0];
            }
        }
        
        return base;
    }
    
    /**
     * 从依赖服务信息构建连接地址
     * 
     * @param service 依赖服务信息
     * @param envVars 环境变量 Map（可能已包含 {SERVICE_ID}_HOST 和 {SERVICE_ID}_PORT）
     * @param serviceId 服务ID（用于查找环境变量）
     * @return 连接地址（格式：host:port），如果无法构建则返回 null
     */
    private String buildServiceAddress(
             MiddlewareManagementService.MiddlewareServiceInfo service,
            Map<String, String> envVars,
            String serviceId) {
        
        if (service == null) {
            return null;
                }
        
        // 优先使用已注入的环境变量（{SERVICE_ID}_HOST 和 {SERVICE_ID}_PORT）
        // 这些环境变量在步骤2中已经注入
        String prefix = serviceId.toUpperCase().replaceAll("-", "_");
        String host = envVars.getOrDefault(prefix + "_HOST", 
            service.getServiceHost() != null ? service.getServiceHost() : "host.docker.internal");
        
        String port = envVars.getOrDefault(prefix + "_PORT", 
            service.getServicePort() != null ? String.valueOf(service.getServicePort()) : null);
        
        // 如果端口为空，无法构建完整地址
        if (port == null || port.isEmpty()) {
            return null;
            }
        
        return host + ":" + port;
    }
    
    
    
    private DockerComposeStepConfig parseConfig(Map<String, Object> config) {
        DockerComposeStepConfig result = new DockerComposeStepConfig();
        if (config == null) {
            return result;
        }
        
        result.setFile((String) config.get("file"));
        result.setProjectName((String) config.get("project-name"));
        result.setAction((String) config.get("action"));
        result.setWaitForHealth(Boolean.TRUE.equals(config.get("wait-for-health")));
        result.setHealthCheckUrl((String) config.get("health-check-url"));
        result.setHealthCheckTimeout(getInt(config, "health-check-timeout", 300));
        result.setRemoveVolumes(Boolean.TRUE.equals(config.get("remove-volumes")));
        
        return result;
    }
    
    private String buildProjectName(String middlewareId) {
        return ("gress_" + middlewareId).replaceAll("[^a-zA-Z0-9_]", "_");
    }
    
    private int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * Docker Compose 步骤配置
     */
    private static class DockerComposeStepConfig {
        // file 字段目前保留为未来扩展（允许指定非默认 compose 文件名）
        private String file;
        private String projectName;
        private String action;
        private boolean waitForHealth;
        private String healthCheckUrl;
        private int healthCheckTimeout = 300;
        private boolean removeVolumes;
        
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        // 以下字段已废弃：健康检查逻辑已从 DockerComposeStepExecutor 中移除，遵循单一职责原则
        // 如果需要健康检查，请在工作流中配置独立的 health-check 步骤
        // 保留这些字段仅用于配置解析，避免解析错误
        @Deprecated
        @SuppressWarnings("unused")
        public boolean isWaitForHealth() { return waitForHealth; }
        public void setWaitForHealth(boolean waitForHealth) { this.waitForHealth = waitForHealth; }
        
        @Deprecated
        @SuppressWarnings("unused")
        public String getHealthCheckUrl() { return healthCheckUrl; }
        public void setHealthCheckUrl(String healthCheckUrl) { this.healthCheckUrl = healthCheckUrl; }
        
        @Deprecated
        @SuppressWarnings("unused")
        public int getHealthCheckTimeout() { return healthCheckTimeout; }
        public void setHealthCheckTimeout(int healthCheckTimeout) { this.healthCheckTimeout = healthCheckTimeout; }
        
        public boolean isRemoveVolumes() { return removeVolumes; }
        public void setRemoveVolumes(boolean removeVolumes) { this.removeVolumes = removeVolumes; }
    }
}
