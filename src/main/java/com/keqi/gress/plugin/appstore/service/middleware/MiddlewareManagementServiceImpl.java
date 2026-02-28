package com.keqi.gress.plugin.appstore.service.middleware;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginMetadataParser;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.common.utils.WorkspaceDirectoryUtils;

import com.keqi.gress.plugin.appstore.domain.entity.MiddlewareDependencyEntity;
import com.keqi.gress.plugin.appstore.domain.entity.MiddlewareInfoEntity;
import com.keqi.gress.plugin.appstore.domain.entity.MiddlewareServiceEntity;
import com.keqi.gress.plugin.appstore.service.MiddlewareManagementService;
import com.keqi.gress.plugin.appstore.service.NodeManagementService;
import com.keqi.gress.plugin.appstore.service.install.JarResourceExtractor;
import com.keqi.gress.plugin.appstore.service.middleware.dependency.MiddlewareDependencyResolutionService;
import com.keqi.gress.plugin.appstore.service.middleware.util.EnvironmentVariableParser;
import com.keqi.gress.plugin.appstore.service.middleware.util.AESEncryptionUtil;
import com.keqi.gress.plugin.appstore.config.AppStoreConfig;
import com.keqi.gress.plugin.appstore.service.middleware.repository.MiddlewareDependencyRepository;
import com.keqi.gress.plugin.appstore.service.middleware.repository.MiddlewareInfoRepository;
import com.keqi.gress.plugin.appstore.service.middleware.repository.MiddlewareServiceRepository;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.*;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 平台级中间件管理服务实现（Spring Bean）。
 *
 * 说明：
 * - 按"方案 A"，由主应用提供（其他插件通过 BeanSource.SPRING 注入）
 * - 安装流程由中间件插件包内的 install-workflow.yml 编排
 * - 使用数据库持久化中间件信息和服务信息
 */
@Service
@Slf4j
public class MiddlewareManagementServiceImpl implements MiddlewareManagementService {

   // private static final Log log = LogFactory.get(MiddlewareManagementServiceImpl.class);

    @Inject
    private  MiddlewareWorkflowEngine workflowEngine;
    @Inject
    private  WorkflowDefinitionParser workflowParser;
    @Inject
    private  MiddlewareDependencyResolutionService dependencyResolutionService;
    @Inject
    private  MiddlewareInfoRepository middlewareInfoRepository;
    @Inject
    private  MiddlewareServiceRepository middlewareServiceRepository;
    @Inject
    private  MiddlewareDependencyRepository middlewareDependencyRepository;
    
    @Inject
    private AppStoreConfig appStoreConfig;

    /**
     * 节点管理服务（可选，如果未注入则使用本地执行环境）
     */
    @Inject(required = false)
    private NodeManagementService nodeManagementService;


    @Override
    public Result<MiddlewareInstallResult> installMiddleware(Path middlewarePackage, String operator) {
        return installMiddleware(middlewarePackage, operator, null, null);
    }
    
    @Override
    public Result<MiddlewareInstallResult> installMiddleware(
            Path middlewarePackage,
            String operator,
            String targetNodeId,
            String executionType) {
        return installMiddleware(middlewarePackage, operator, targetNodeId, executionType, null);
    }

    @Override
    public Result<MiddlewareInstallResult> installMiddleware(
            Path middlewarePackage,
            String operator,
            String targetNodeId,
            String executionType,
            String clientId) {
        return installMiddleware(middlewarePackage, operator, targetNodeId, executionType, clientId, null);
    }

    @Override
    public Result<MiddlewareInstallResult> installMiddleware(
            Path middlewarePackage,
            String operator,
            String targetNodeId,
            String executionType,
            String clientId,
            Map<String, Object> config) {
        try {
            PluginMetadataParser.PluginMetadata metadata = PluginMetadataParser.parseFromJar(middlewarePackage);
            String middlewareId = metadata.getPluginId();
            String version = metadata.getVersion();

            // 检查是否已安装
            if (middlewareInfoRepository.findByMiddlewareId(middlewareId).isPresent()) {
                return Result.error("中间件已安装: " + middlewareId);
            }

            // 1. 解析并安装依赖（从应用商店下载安装）
            Result<Map<String, MiddlewareServiceInfo>> depResult = 
                dependencyResolutionService.ensureDependenciesInstalled(middlewarePackage, operator);
            
            if (!depResult.isSuccess()) {
                return Result.error("依赖安装失败: " + depResult.getErrorMessage());
            }
            
            Map<String, MiddlewareServiceInfo> resolvedServices = depResult.getData();
            
            // 记录中间件依赖关系到数据库
            if (!resolvedServices.isEmpty()) {
                for (String serviceId : resolvedServices.keySet()) {
                    MiddlewareDependencyEntity depEntity = new MiddlewareDependencyEntity();
                    depEntity.setMiddlewareId(middlewareId);
                    depEntity.setServiceId(serviceId);
                    depEntity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
                    middlewareDependencyRepository.save(depEntity);
                }
            }

            // 2. 执行安装工作流
            WorkflowDefinition workflow = workflowParser.parse(middlewarePackage);

            Path workDir = resolveWorkDir(middlewareId, version);
            Files.createDirectories(workDir);

            // 处理安装配置（如果提供）
            Map<String, Object> installConfig = config != null ? new HashMap<>(config) : new HashMap<>();

            MiddlewareInstallContext ctx = MiddlewareInstallContext.builder()
                    .middlewareId(middlewareId)
                    .version(version)
                    .middlewarePackage(middlewarePackage)
                    .workDir(workDir)
                    .operator(operator)
                    .resolvedServices(resolvedServices)  // 注入已解析的服务信息
                    .targetNodeId(targetNodeId)  // 目标节点ID
                    .executionType(executionType)  // 执行环境类型
                    .clientId(clientId)  // SSE客户端ID
                    .installConfig(installConfig)  // 安装配置
                    .metadata(Map.of(
                            "pluginId", middlewareId,
                            "version", version
                    ))
                    .build();

            Result<WorkflowExecutionResult> execResult = workflowEngine.executeInstallWorkflow(workflow, ctx);
            if (!execResult.isSuccess()) {
                // 回滚依赖
                rollbackDependencies(middlewareId, resolvedServices.keySet(), operator);
                return Result.error("工作流执行失败: " + execResult.getErrorMessage());
            }

            // 3. 从 docker-compose.yml 注册服务到服务表
            try {
            registerServicesFromDockerCompose(middlewarePackage, workDir, middlewareId, resolvedServices);
                log.info("服务注册完成: middlewareId={}", middlewareId);
            } catch (Exception e) {
                log.warn("服务注册失败，但继续保存中间件信息: middlewareId={}", middlewareId, e);
            }

            // 4. 保存中间件信息到数据库
            log.info("开始保存中间件信息到数据库: middlewareId={}, version={}", middlewareId, version);
            MiddlewareInfoEntity entity = new MiddlewareInfoEntity();
            entity.setMiddlewareId(middlewareId);
            entity.setName(middlewareId);
            entity.setVersion(version);
            entity.setShared(true);
            entity.setStatus(MiddlewareStatus.RUNNING.name());
            entity.setWorkDir(workDir.toString());
            entity.setPackagePath(middlewarePackage.toString());
            entity.setDependencies(metadata.getDependencies() != null ? metadata.getDependencies() : "");
            entity.setInstalledBy(operator);
            entity.setInstalledAt(new Timestamp(System.currentTimeMillis()));
            entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("packagePath", middlewarePackage.toString());
            configMap.put("dependencies", metadata.getDependencies() != null ? metadata.getDependencies() : "");
            // 中间件信息中的配置通常不包含敏感信息，但为了安全也进行加密
            String encryptionKey = getEncryptionKey();
            if (encryptionKey != null) {
                configMap = AESEncryptionUtil.encryptSensitiveFields(configMap, encryptionKey);
            }
            entity.setConfig(JSON.toJSONString(configMap));
            
            try {
            middlewareInfoRepository.save(entity);
                log.info("中间件信息保存成功: middlewareId={}", middlewareId);
            } catch (Exception e) {
                log.error("保存中间件信息失败: middlewareId={}", middlewareId, e);
                throw new RuntimeException("保存中间件信息失败: " + e.getMessage(), e);
            }

            log.info("中间件安装成功: middlewareId={}, version={}, dependencies={}, operator={}", 
                    middlewareId, version, resolvedServices.size(), operator);
            return Result.success(MiddlewareInstallResult.builder()
                    .middlewareId(middlewareId)
                    .version(version)
                    .workDir(workDir.toString())
                    .message("安装成功")
                    .build());
        } catch (Exception e) {
            log.error("安装中间件失败", e);
            return Result.error("安装失败: " + e.getMessage());
        }
    }
    
    /**
     * 回滚依赖
     */
    private void rollbackDependencies(String middlewareId, Set<String> serviceIds, String operator) {
        for (String serviceId : serviceIds) {
            try {
                decrementServiceReference(serviceId, middlewareId);
            } catch (Exception e) {
                log.warn("回滚依赖失败: serviceId={}, middlewareId={}", serviceId, middlewareId, e);
            }
        }
    }

    @Override
    public Result<Void> uninstallMiddleware(String middlewareId, String operator) {
        try {
            Optional<MiddlewareInfoEntity> entityOpt = middlewareInfoRepository.findByMiddlewareId(middlewareId);
            if (entityOpt.isEmpty()) {
                return Result.error("中间件未安装: " + middlewareId);
            }
            
            MiddlewareInfoEntity entity = entityOpt.get();
            Path workDir = entity.getWorkDir() != null ? Paths.get(entity.getWorkDir()) : null;
            String pkgPath = entity.getPackagePath();

            if (workDir != null && pkgPath != null && Files.exists(Paths.get(pkgPath))) {
                WorkflowDefinition workflow = workflowParser.parse(Paths.get(pkgPath));
                MiddlewareUninstallContext ctx = MiddlewareUninstallContext.builder()
                        .middlewareId(middlewareId)
                        .workDir(workDir)
                        .operator(operator)
                        .build();
                Result<WorkflowExecutionResult> execResult = workflowEngine.executeUninstallWorkflow(workflow, ctx);
                if (!execResult.isSuccess()) {
                    log.warn("卸载工作流执行失败，但继续清理注册信息: {}", execResult.getErrorMessage());
                }
            } else {
                log.warn("缺少卸载所需信息（workDir/packagePath），跳过卸载工作流，仅移除注册信息: middlewareId={}", middlewareId);
            }

            // 减少共享服务引用计数
            List<MiddlewareDependencyEntity> dependencies = middlewareDependencyRepository.findByMiddlewareId(middlewareId);
            for (MiddlewareDependencyEntity dep : dependencies) {
                decrementServiceReference(dep.getServiceId(), middlewareId);
            }
            
            // 删除依赖关系
            middlewareDependencyRepository.deleteByMiddlewareId(middlewareId);
            
            // 删除中间件信息
            middlewareInfoRepository.deleteByMiddlewareId(middlewareId);
            
            log.info("中间件卸载完成: middlewareId={}, operator={}", middlewareId, operator);
            return Result.success();
        } catch (Exception e) {
            log.error("卸载中间件失败", e);
            return Result.error("卸载失败: " + e.getMessage());
        }
    }

    @Override
    public Optional<MiddlewareInfo> getMiddleware(String middlewareId) {
        return middlewareInfoRepository.findByMiddlewareId(middlewareId)
                .map(this::toMiddlewareInfo);
    }

    @Override
    public List<MiddlewareInfo> listMiddlewares() {
        return middlewareInfoRepository.findAll().stream()
                .map(this::toMiddlewareInfo)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isMiddlewareAvailable(String middlewareId) {
        Optional<MiddlewareInfoEntity> entityOpt = middlewareInfoRepository.findByMiddlewareId(middlewareId);
        if (entityOpt.isEmpty()) {
            return false;
        }
        return MiddlewareStatus.RUNNING.name().equals(entityOpt.get().getStatus());
    }

    @Override
    public HealthCheckResult checkHealth(String middlewareId) {
        Optional<MiddlewareInfoEntity> entityOpt = middlewareInfoRepository.findByMiddlewareId(middlewareId);
        if (entityOpt.isEmpty()) {
            return HealthCheckResult.notInstalled();
        }
        // TODO: 真实健康检查（HTTP/Socket）
        return HealthCheckResult.healthy();
    }
    
    /**
     * Entity 转 DTO
     */
    private MiddlewareInfo toMiddlewareInfo(MiddlewareInfoEntity entity) {
        Map<String, Object> config = new HashMap<>();
        if (entity.getConfig() != null && !entity.getConfig().isEmpty()) {
            try {
                config = JSON.parseObject(entity.getConfig(), Map.class);
                // 中间件信息中的配置通常不包含敏感信息，但为了安全也进行解密
                String encryptionKey = getEncryptionKey();
                if (encryptionKey != null) {
                    config = AESEncryptionUtil.decryptSensitiveFields(config, encryptionKey);
                }
            } catch (Exception e) {
                log.warn("解析配置失败: middlewareId={}", entity.getMiddlewareId(), e);
            }
        }
        
        return MiddlewareInfo.builder()
                .id(entity.getMiddlewareId())
                .name(entity.getName())
                .version(entity.getVersion())
                .category(entity.getCategory())
                .shared(entity.getShared() != null ? entity.getShared() : true)
                .serviceHost(entity.getServiceHost())
                .servicePort(entity.getServicePort())
                .healthCheckUrl(entity.getHealthCheckUrl())
                .status(MiddlewareStatus.valueOf(entity.getStatus()))
                .workDir(entity.getWorkDir())
                .config(config)
                .build();
    }

    private Path resolveWorkDir(String middlewareId, String version) {
        return WorkspaceDirectoryUtils.getMiddlewareWorkDirectory(middlewareId, version);
    }
    
    /**
     * 从 docker-compose.yml 解析服务并注册到服务表
     */
    private void registerServicesFromDockerCompose(
            Path middlewarePackage,
            Path workDir,
            String middlewareId,
            Map<String, MiddlewareServiceInfo> resolvedServices) {
        
        try {
            // 1. 从插件包中提取 docker-compose.yml
            JarResourceExtractor extractor = new JarResourceExtractor();
            Optional<Path> composeFileOpt = extractor.extractDockerCompose(middlewarePackage, workDir);
            
            if (composeFileOpt.isEmpty() || !Files.exists(composeFileOpt.get())) {
                log.warn("未找到 docker-compose.yml，跳过服务注册: middlewareId={}", middlewareId);
                return;
            }
            
            Path composeFile = composeFileOpt.get();
            
            // 2. 解析 YAML
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> composeData;
            try (InputStream is = Files.newInputStream(composeFile)) {
                composeData = (Map<String, Object>) yamlMapper.readValue(is, Map.class);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) composeData.get("services");
            if (services == null || services.isEmpty()) {
                log.warn("docker-compose.yml 中没有 services，跳过服务注册: middlewareId={}", middlewareId);
                return;
            }
            
            // 3. 为每个服务创建 MiddlewareServiceInfo 并注册
            // 注意：使用 middlewareId 作为 serviceId，而不是 docker-compose 中的服务名
            // 这样依赖解析时才能正确找到服务
            for (Map.Entry<String, Object> entry : services.entrySet()) {
                String serviceName = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> serviceDef = (Map<String, Object>) entry.getValue();
                
                // 检查是否已注册（可能是依赖的服务）
                // 使用 middlewareId 作为 serviceId 进行匹配
                if (resolvedServices.containsKey(middlewareId)) {
                    log.debug("服务已通过依赖解析注册，跳过: serviceId={}", middlewareId);
                    continue;
                }
                
                // 创建服务信息
                MiddlewareServiceInfo serviceInfo = buildServiceInfoFromDockerCompose(
                    serviceName, serviceDef, middlewareId, workDir);
                
                // 注册服务：使用 middlewareId 作为 serviceId，而不是 docker-compose 中的服务名
                registerMiddlewareService(middlewareId, serviceInfo);
                log.info("注册服务成功: serviceId={}, middlewareId={}, dockerServiceName={}", 
                    middlewareId, middlewareId, serviceName);
            }
            
        } catch (Exception e) {
            log.warn("从 docker-compose.yml 注册服务失败，但不影响安装: middlewareId={}", middlewareId, e);
        }
    }
    
    /**
     * 从 docker-compose 服务定义构建 MiddlewareServiceInfo
     */
    @SuppressWarnings("unchecked")
    private MiddlewareServiceInfo buildServiceInfoFromDockerCompose(
            String serviceName,
            Map<String, Object> serviceDef,
            String middlewareId,
            Path workDir) {
        
        // 注意：serviceId 应该使用 middlewareId，而不是 docker-compose 中的服务名
        // serviceName 是 docker-compose.yml 中的服务名（如 "minio"），用于显示
        MiddlewareServiceInfo.MiddlewareServiceInfoBuilder builder = MiddlewareServiceInfo.builder()
                .serviceId(middlewareId)  // 使用 middlewareId 作为 serviceId
                .serviceType(middlewareId)  // 使用 middlewareId 作为 serviceType
                .serviceName(serviceName)  // docker-compose 中的服务名用于显示
                .containerName((String) serviceDef.get("container_name"))
                .serviceHost("localhost")
                .installedBy(middlewareId)
                .status(MiddlewareStatus.RUNNING)
                .workDir(workDir.toString())
                .consumers(new ArrayList<>());
        
        // 提取端口
        Object portsObj = serviceDef.get("ports");
        if (portsObj instanceof List) {
            List<String> ports = (List<String>) portsObj;
            if (!ports.isEmpty()) {
                // 解析第一个端口映射，例如 "9000:9000" -> 9000
                String firstPort = ports.get(0);
                if (firstPort.contains(":")) {
                    String[] parts = firstPort.split(":");
                    try {
                        builder.servicePort(Integer.parseInt(parts[0]));
                    } catch (NumberFormatException e) {
                        log.warn("解析端口失败: {}", firstPort, e);
                    }
                }
            }
        }
        
        // 提取健康检查 URL（TODO: 从健康检查命令中提取 URL）
        // Object healthcheckObj = serviceDef.get("healthcheck");
        // if (healthcheckObj instanceof Map) {
        //     Map<String, Object> healthcheck = (Map<String, Object>) healthcheckObj;
        //     // 从健康检查命令中提取 URL（如果有）
        // }
        
        // 提取环境变量作为配置（支持 ${VAR:-default} 格式）
        Map<String, Object> config = new HashMap<>();
        Object envObj = serviceDef.get("environment");
        if (envObj instanceof Map) {
            Map<String, Object> env = (Map<String, Object>) envObj;
            for (Map.Entry<String, Object> entry : env.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof String) {
                    String strValue = (String) value;
                    // 解析 ${VAR:-default} 格式，提取默认值
                    String parsedValue = EnvironmentVariableParser.extractDefaultValue(strValue);
                    config.put(key, parsedValue);
                } else {
                    config.put(key, value);
                }
            }
        } else if (envObj instanceof List) {
            // 处理列表格式的环境变量，例如 ["KEY=value"]
            List<String> envList = (List<String>) envObj;
            for (String envItem : envList) {
                if (envItem.contains("=")) {
                    String[] parts = envItem.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0];
                        String value = parts[1];
                        // 解析 ${VAR:-default} 格式
                        String parsedValue = EnvironmentVariableParser.extractDefaultValue(value);
                        config.put(key, parsedValue);
                    }
                }
            }
        }
        
        // 从 command 中提取环境变量（如 Redis 的 --requirepass）
        Object commandObj = serviceDef.get("command");
        if (commandObj instanceof String) {
            String command = (String) commandObj;
            // 提取 Redis 密码：--requirepass ${REDIS_PASSWORD:-redis123}
            String redisPassword = EnvironmentVariableParser.extractFromCommand(
                command, 
                "--requirepass\\s+\\$\\{([^:}]+)(?::-([^}]*))?\\}",
                "REDIS_PASSWORD"
            );
            if (redisPassword != null) {
                config.put("REDIS_PASSWORD", redisPassword);
            }
        }
        
        builder.config(config);
        
        return builder.build();
    }
    
    // ===================== 服务注册相关方法 =====================
    
    @Override
    public Optional<MiddlewareServiceInfo> getMiddlewareService(String serviceId) {
        return middlewareServiceRepository.findByServiceId(serviceId)
                .map(this::toMiddlewareServiceInfo);
    }
    
    @Override
    public List<MiddlewareServiceInfo> listMiddlewareServices() {
        return middlewareServiceRepository.findAll().stream()
                .map(this::toMiddlewareServiceInfo)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean isMiddlewareServiceAvailable(String serviceId) {
        Optional<MiddlewareServiceEntity> entityOpt = middlewareServiceRepository.findByServiceId(serviceId);
        if (entityOpt.isEmpty()) {
            return false;
        }
        return MiddlewareStatus.RUNNING.name().equals(entityOpt.get().getStatus());
    }
    
    @Override
    public Result<MiddlewareServiceInfo> registerMiddlewareService(
            String serviceId, MiddlewareServiceInfo serviceInfo) {
        try {
            // 检查是否已存在
            Optional<MiddlewareServiceEntity> existingOpt = middlewareServiceRepository.findByServiceId(serviceId);
            
            if (existingOpt.isPresent()) {
                // 更新引用计数
                MiddlewareServiceEntity existing = existingOpt.get();
                existing.setReferenceCount((existing.getReferenceCount() != null ? existing.getReferenceCount() : 0) + 1);
                middlewareServiceRepository.update(existing);
                log.info("增加服务引用计数: serviceId={}, count={}", serviceId, existing.getReferenceCount());
                return Result.success(toMiddlewareServiceInfo(existing));
            }
            
            // 插入新记录
            MiddlewareServiceEntity entity = toMiddlewareServiceEntity(serviceId, serviceInfo);
            entity.setReferenceCount(1);
            middlewareServiceRepository.save(entity);
            
            log.info("注册新服务: serviceId={}, installedBy={}", serviceId, serviceInfo.getInstalledBy());
            return Result.success(serviceInfo);
        } catch (Exception e) {
            log.error("注册服务失败: serviceId={}", serviceId, e);
            return Result.error("注册失败: " + e.getMessage());
        }
    }
    
    @Override
    public Result<Void> incrementServiceReference(String serviceId, String middlewareId) {
        Optional<MiddlewareServiceEntity> entityOpt = middlewareServiceRepository.findByServiceId(serviceId);
        if (entityOpt.isEmpty()) {
            return Result.error("共享服务不存在: " + serviceId);
        }
        
        middlewareServiceRepository.incrementReferenceCount(serviceId);
        
        log.info("增加服务引用: serviceId={}, middlewareId={}", serviceId, middlewareId);
        return Result.success();
    }
    
    @Override
    public Result<Void> decrementServiceReference(String serviceId, String middlewareId) {
        Optional<MiddlewareServiceEntity> entityOpt = middlewareServiceRepository.findByServiceId(serviceId);
        if (entityOpt.isEmpty()) {
            return Result.error("共享服务不存在: " + serviceId);
        }
        
        middlewareServiceRepository.decrementReferenceCount(serviceId);
        
        // 检查引用计数
        Optional<MiddlewareServiceEntity> updatedOpt = middlewareServiceRepository.findByServiceId(serviceId);
        if (updatedOpt.isPresent()) {
            MiddlewareServiceEntity entity = updatedOpt.get();
            if (entity.getReferenceCount() != null && entity.getReferenceCount() <= 0) {
                log.info("服务引用计数为0，标记为可卸载: serviceId={}", serviceId);
                entity.setStatus(MiddlewareStatus.STOPPED.name());
                middlewareServiceRepository.update(entity);
            }
        }
        
        log.info("减少服务引用: serviceId={}, middlewareId={}", serviceId, middlewareId);
        return Result.success();
    }
    
    @Override
    public Result<Void> deleteMiddlewareService(String serviceId) {
        try {
            // 检查服务是否存在
            Optional<MiddlewareServiceEntity> entityOpt = middlewareServiceRepository.findByServiceId(serviceId);
            if (entityOpt.isEmpty()) {
                return Result.error("服务不存在: " + serviceId);
            }
            
            MiddlewareServiceEntity entity = entityOpt.get();
            
            // 检查引用计数，如果被其他中间件引用，不允许删除
            if (entity.getReferenceCount() != null && entity.getReferenceCount() > 0) {
                return Result.error("服务正在被其他中间件使用，无法删除。引用计数: " + entity.getReferenceCount());
            }
            
            // 删除服务
            middlewareServiceRepository.deleteByServiceId(serviceId);
            log.info("删除服务成功: serviceId={}", serviceId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除服务失败: serviceId={}", serviceId, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * Entity 转 DTO
     */
    private MiddlewareServiceInfo toMiddlewareServiceInfo(MiddlewareServiceEntity entity) {
        Map<String, Object> config = new HashMap<>();
        if (entity.getConfig() != null && !entity.getConfig().isEmpty()) {
            try {
                config = JSON.parseObject(entity.getConfig(), Map.class);
                // 解密敏感字段
                String encryptionKey = getEncryptionKey();
                if (encryptionKey != null) {
                    config = AESEncryptionUtil.decryptSensitiveFields(config, encryptionKey);
                }
            } catch (Exception e) {
                log.warn("解析服务配置失败: serviceId={}", entity.getServiceId(), e);
            }
        }
        
        // 获取使用该服务的中间件列表
        List<String> consumers = middlewareDependencyRepository.findByServiceId(entity.getServiceId())
                .stream()
                .map(MiddlewareDependencyEntity::getMiddlewareId)
                .collect(Collectors.toList());
        
        return MiddlewareServiceInfo.builder()
                .serviceId(entity.getServiceId())
                .serviceType(entity.getServiceType())
                .serviceName(entity.getServiceName())
                .containerName(entity.getContainerName())
                .serviceHost(entity.getServiceHost())
                .servicePort(entity.getServicePort())
                .healthCheckUrl(entity.getHealthCheckUrl())
                .config(config)
                .installedBy(entity.getInstalledBy())
                .referenceCount(entity.getReferenceCount() != null ? entity.getReferenceCount() : 0)
                .consumers(consumers)
                .status(MiddlewareStatus.valueOf(entity.getStatus()))
                .workDir(entity.getWorkDir())
                .build();
    }
    
    /**
     * DTO 转 Entity
     */
    private MiddlewareServiceEntity toMiddlewareServiceEntity(String serviceId, MiddlewareServiceInfo serviceInfo) {
        MiddlewareServiceEntity entity = new MiddlewareServiceEntity();
        entity.setServiceId(serviceId);
        entity.setServiceType(serviceInfo.getServiceType());
        entity.setServiceName(serviceInfo.getServiceName());
        entity.setContainerName(serviceInfo.getContainerName());
        entity.setServiceHost(serviceInfo.getServiceHost() != null ? serviceInfo.getServiceHost() : "localhost");
        entity.setServicePort(serviceInfo.getServicePort());
        entity.setHealthCheckUrl(serviceInfo.getHealthCheckUrl());
        entity.setInstalledBy(serviceInfo.getInstalledBy());
        entity.setStatus(serviceInfo.getStatus() != null ? serviceInfo.getStatus().name() : MiddlewareStatus.RUNNING.name());
        entity.setWorkDir(serviceInfo.getWorkDir());
        entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        
        if (serviceInfo.getConfig() != null && !serviceInfo.getConfig().isEmpty()) {
            // 加密敏感字段
            Map<String, Object> configToSave = new HashMap<>(serviceInfo.getConfig());
            String encryptionKey = getEncryptionKey();
            if (encryptionKey != null) {
                configToSave = AESEncryptionUtil.encryptSensitiveFields(configToSave, encryptionKey);
            }
            entity.setConfig(JSON.toJSONString(configToSave));
        }
        
        return entity;
    }
    
    /**
     * 获取加密密钥
     */
    private String getEncryptionKey() {
        if (appStoreConfig != null && 
            appStoreConfig.getSecurity() != null && 
            appStoreConfig.getSecurity().getMiddlewareEncryptionKey() != null) {
            return appStoreConfig.getSecurity().getMiddlewareEncryptionKey();
        }
        return null; // 如果未配置，返回 null（不加密）
    }
}

