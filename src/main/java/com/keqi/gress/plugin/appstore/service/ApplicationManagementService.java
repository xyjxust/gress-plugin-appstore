package com.keqi.gress.plugin.appstore.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginPackageInstallResult;
import  com.keqi.gress.common.plugin.PluginPackageLifecycle;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.common.storage.FileStorageService;
import  com.keqi.gress.plugin.api.database.page.IPage;
import com.keqi.gress.plugin.appstore.dao.ApplicationDao;
import com.keqi.gress.plugin.appstore.dao.ApplicationUpgradeLogDao;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplicationOperationLog;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplicationUpgradeLog;
import com.keqi.gress.plugin.appstore.dto.*;
import com.keqi.gress.plugin.appstore.service.orchestrator.InstallOrchestrator;
import com.keqi.gress.plugin.appstore.service.orchestrator.UpgradeOrchestrator;
import com.keqi.gress.plugin.appstore.service.orchestrator.UninstallOrchestrator;
import com.keqi.gress.plugin.appstore.service.persistence.ApplicationPersistenceService;
import com.keqi.gress.plugin.appstore.service.logging.ApplicationOperationLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 应用管理服务（重构后的薄协调层）
 * 
 * 职责：
 * - 作为应用管理的门面（Facade），协调各个编排器和服务
 * - 遵循单一职责原则（SRP）：只负责协调，不处理具体业务逻辑
 * - 遵循开闭原则（OCP）：通过依赖注入扩展功能
 * - 遵循依赖倒置原则（DIP）：依赖抽象接口
 * 
 * 架构：
 * - 安装/升级/卸载 -> 委托给对应的 Orchestrator
 * - 数据库操作 -> 委托给 ApplicationPersistenceService
 * - 日志记录 -> 委托给 ApplicationOperationLogger
 * - 启停控制 -> 直接调用 PluginPackageLifecycle（已足够简单）
 * 
 * @author Gress Team
 */
@Service
@Slf4j
public class ApplicationManagementService {

  //  private static final Log log = LogFactory.get(ApplicationManagementService.class);
    
    // === 核心组件（编排器） ===
    
    @Inject
    private InstallOrchestrator installOrchestrator;
    
    @Inject
    private UpgradeOrchestrator upgradeOrchestrator;
    
    @Inject
    private UninstallOrchestrator uninstallOrchestrator;
    
    // === 基础服务 ===
    
    @Inject
    private ApplicationPersistenceService persistenceService;
    
    @Inject
    private ApplicationOperationLogger operationLogger;
    
    @Inject
    private ApplicationDao applicationDao;
    
    @Inject
    private ApplicationUpgradeLogDao applicationUpgradeLogDao;
    
    @Inject
    private com.keqi.gress.plugin.appstore.dao.ApplicationOperationLogDao applicationOperationLogDao;
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginPackageLifecycle pluginPackageLifecycle;
    
    @Inject(source = Inject.BeanSource.SPRING)
    private FileStorageService fileStorageService;
    
    @Inject(source = Inject.BeanSource.SPRING)
    private  com.keqi.gress.common.plugin.PluginConfigMetadataProvider pluginConfigMetadataProvider;
    
    // ==================== 查询相关 ====================
    
    /**
     * 查询应用列表
     */
    public Result<PageResult<ApplicationDTO>> queryApplications(ApplicationQueryRequest request) {
        try {
            IPage<SysApplication> page = applicationDao.queryApplicationsPage(
                request.getPage(),
                request.getSize(),
                request.getKeyword(),
                request.getStatus(),
                request.getApplicationType(),
                request.getPluginId()
            );
            
            List<ApplicationDTO> applications = page.getRecords().stream()
                    .map(this::mapToApplicationDTO)
                    .collect(Collectors.toList());
            
            PageResult<ApplicationDTO> pageResult = PageResult.of(
                applications, 
                page.getTotal(), 
                (int) page.getCurrent(), 
                (int) page.getSize()
            );
            
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("查询应用列表失败", e);
            return Result.error("查询应用列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取应用详情
     */
    public Result<ApplicationDTO> getApplicationDetail(Long id) {
        try {
            SysApplication application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            return Result.success(mapToApplicationDTO(application));
        } catch (Exception e) {
            log.error("获取应用详情失败: id={}", id, e);
            return Result.error("获取应用详情失败: " + e.getMessage());
        }
    }
    
    // ==================== 安装相关（委托给 InstallOrchestrator） ====================
    
    /**
     * 上传并安装应用
     */
    public Result<Void> uploadAndInstall(MultipartFile file, String operatorId, String operatorName) {
        long startTime = System.currentTimeMillis();
        SysApplication tempApp = createTempApp("上传的应用", "unknown");
        
        try {
            // 1. 验证文件
            if (file == null || file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".jar")) {
                return Result.error("只支持 .jar 格式的应用包文件");
            }
            
            log.info("开始上传并安装应用: filename={}, size={}, operator={}", 
                    originalFilename, file.getSize(), operatorName);
            
            // 2. 上传文件到存储服务
            String fileUrl = fileStorageService
                .upload(new ByteArrayInputStream(file.getBytes()), originalFilename)
                .withMetadata("category", "plugin")
                .withMetadata("uploadBy", operatorName)
                .onSuccess(savedUrl -> log.info("应用包文件上传成功: {}", savedUrl))
                .onError(e -> log.error("应用包文件上传失败", e))
                .get();
            
            if (fileUrl == null || fileUrl.isEmpty()) {
                return Result.error("文件上传失败");
            }
            
            log.info("应用包文件上传成功: fileUrl={}", fileUrl);
            
            // 2. 委托给 InstallOrchestrator 执行安装
            Result<PluginPackageInstallResult> installResult = 
                    installOrchestrator.installFromUrl(fileUrl, operatorName);
            
            if (!installResult.isSuccess()) {
                operationLogger.logFailure(tempApp, "INSTALL", "上传并安装应用", 
                        operatorId, operatorName, installResult.getErrorMessage(), startTime);
                return Result.error(installResult.getErrorMessage());
            }
            
            // 操作日志在 Orchestrator 中已记录
            return Result.success();
            
        } catch (Exception e) {
            log.error("上传并安装应用失败", e);
            operationLogger.logFailure(tempApp, "INSTALL", "上传并安装应用", 
                    operatorId, operatorName, "异常: " + e.getMessage(), startTime);
            return Result.error("上传并安装应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 从URL安装应用
     */
    public Result<PluginPackageInstallResult> installApplicationFromUrl(String fileUrl, String operatorName) {
        return installOrchestrator.installFromUrl(fileUrl, operatorName);
    }
    
    /**
     * 从应用商店安装应用（支持依赖链）
     */
    public Result<PluginPackageInstallResult> installApplicationFromAppStore(
            String pluginId, String version, String operatorName) {
        return installOrchestrator.installFromAppStore(pluginId, version, operatorName);
    }
    
    // ==================== 升级相关（委托给 UpgradeOrchestrator） ====================
    
    /**
     * 升级应用
     */
    public Result<Void> upgradeApplication(Long id, ApplicationUpgradeRequest request) {
        return upgradeOrchestrator.upgrade(id, request);
    }
    
    /**
     * 回滚应用（暂未完全实现，保留接口）
     */
    public Result<Void> rollbackApplication(Long id, ApplicationUpgradeRequest request) {
        return Result.error("回滚功能暂未实现");
    }
    
    // ==================== 卸载相关（委托给 UninstallOrchestrator） ====================
    
    /**
     * 卸载应用
     */
    public Result<Void> uninstallApplication(Long id, ApplicationUninstallRequest request) {
        return uninstallOrchestrator.uninstall(id, request);
    }
    
    // ==================== 启停控制（直接调用 PluginPackageLifecycle） ====================
    
    /**
     * 启动应用
     */
    public Result<Void> startApplication(Long id, String operatorName) {
        long startTime = System.currentTimeMillis();
        SysApplication application = null;
        
        try {
            application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            String packageId = application.getPluginId();
            log.info("启动应用: id={}, packageId={}, operator={}", id, packageId, operatorName);
            
            Result<?> result = pluginPackageLifecycle.start(packageId);
            
            if (result.isSuccess()) {
                persistenceService.updateStatus(id, 1, operatorName);
                operationLogger.logSuccess(application, "START", "启动应用", 
                        "admin", operatorName, "启动成功", startTime);
                return Result.success();
            } else {
                operationLogger.logFailure(application, "START", "启动应用", 
                        "admin", operatorName, "启动失败: " + result.getErrorMessage(), startTime);
                return Result.error("启动应用失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("启动应用失败: id={}", id, e);
            if (application != null) {
                operationLogger.logFailure(application, "START", "启动应用", 
                        "admin", operatorName, "异常: " + e.getMessage(), startTime);
            }
            return Result.error("启动应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止应用
     */
    public Result<Void> stopApplication(Long id, String operatorName) {
        long startTime = System.currentTimeMillis();
        SysApplication application = null;
        
        try {
            application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            String packageId = application.getPluginId();
            log.info("停止应用: id={}, packageId={}, operator={}", id, packageId, operatorName);
            
            Result<?> result = pluginPackageLifecycle.stop(packageId);
            
            if (result.isSuccess()) {
                persistenceService.updateStatus(id, 0, operatorName);
                operationLogger.logSuccess(application, "STOP", "停止应用", 
                        "admin", operatorName, "停止成功", startTime);
                return Result.success();
            } else {
                operationLogger.logFailure(application, "STOP", "停止应用", 
                        "admin", operatorName, "停止失败: " + result.getErrorMessage(), startTime);
                return Result.error("停止应用失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("停止应用失败: id={}", id, e);
            if (application != null) {
                operationLogger.logFailure(application, "STOP", "停止应用", 
                        "admin", operatorName, "异常: " + e.getMessage(), startTime);
            }
            return Result.error("停止应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 重启应用
     */
    public Result<Void> restartApplication(Long id, String operatorName) {
        long startTime = System.currentTimeMillis();
        SysApplication application = null;
        
        try {
            application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            String packageId = application.getPluginId();
            log.info("重启应用: id={}, packageId={}, operator={}", id, packageId, operatorName);
            
            Result<?> result = pluginPackageLifecycle.restart(packageId);
            
            if (result.isSuccess()) {
                operationLogger.logSuccess(application, "RESTART", "重启应用", 
                        "admin", operatorName, "重启成功", startTime);
                return Result.success();
            } else {
                operationLogger.logFailure(application, "RESTART", "重启应用", 
                        "admin", operatorName, "重启失败: " + result.getErrorMessage(), startTime);
                return Result.error("重启应用失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("重启应用失败: id={}", id, e);
            if (application != null) {
                operationLogger.logFailure(application, "RESTART", "重启应用", 
                        "admin", operatorName, "异常: " + e.getMessage(), startTime);
            }
            return Result.error("重启应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 切换应用状态（启用/停用）
     */
    public Result<Void> toggleApplicationStatus(Long id, Integer status, String operatorName) {
        try {
            SysApplication application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            boolean updated = persistenceService.updateStatus(id, status, operatorName);
            if (updated) {
                return Result.success();
            } else {
                return Result.error("更新状态失败");
            }
        } catch (Exception e) {
            log.error("切换应用状态失败: id={}, status={}", id, status, e);
            return Result.error("切换应用状态失败: " + e.getMessage());
        }
    }
    
    // ==================== 日志查询 ====================
    
    /**
     * 获取升级日志
     */
    public Result<List<ApplicationUpgradeLogDTO>> getUpgradeLogs(Long applicationId) {
        try {
            List<SysApplicationUpgradeLog> logs = 
                    applicationUpgradeLogDao.findByApplicationId(applicationId);
            
            List<ApplicationUpgradeLogDTO> dtoList = logs.stream()
                    .map(this::mapToUpgradeLogDTO)
                    .collect(Collectors.toList());
            
            return Result.success(dtoList);
        } catch (Exception e) {
            log.error("获取升级日志失败: applicationId={}", applicationId, e);
            return Result.error("获取升级日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取应用操作日志
     */
    public Result<PageResult<ApplicationOperationLogDTO>> getApplicationOperationLogs(
            Long applicationId, Integer page, Integer size, String operationType) {
        try {
            IPage<SysApplicationOperationLog> logPage =
                    applicationOperationLogDao.queryPage(page, size, applicationId, operationType, null);
            
            List<ApplicationOperationLogDTO> logs = logPage.getRecords().stream()
                    .map(this::mapToOperationLogDTO)
                    .collect(Collectors.toList());
            
            PageResult<ApplicationOperationLogDTO> pageResult = PageResult.of(
                    logs,
                    logPage.getTotal(),
                    (int) logPage.getCurrent(),
                    (int) logPage.getSize()
            );
            
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取应用操作日志失败", e);
            return Result.error("获取应用操作日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有操作日志
     */
    public Result<PageResult<ApplicationOperationLogDTO>> getAllOperationLogs(
            Integer page, Integer size, String operationType, String operatorName, 
            String applicationName, String status) {
        try {
            IPage<SysApplicationOperationLog> logPage =
                    applicationOperationLogDao.queryPageWithFilters(
                            page, size, null, operationType, operatorName, applicationName, status);
            
            List<ApplicationOperationLogDTO> logs = logPage.getRecords().stream()
                    .map(this::mapToOperationLogDTO)
                    .collect(Collectors.toList());
            
            PageResult<ApplicationOperationLogDTO> pageResult = PageResult.of(
                    logs,
                    logPage.getTotal(),
                    (int) logPage.getCurrent(),
                    (int) logPage.getSize()
            );
            
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取所有操作日志失败", e);
            return Result.error("获取所有操作日志失败: " + e.getMessage());
        }
    }
    
    // ==================== 配置管理 ====================
    
    /**
     * 获取应用配置元数据
     */
    public Result<List< com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata>> 
            getApplicationConfigMetadata(Long id) {
        try {
            SysApplication application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            String pluginId = application.getPluginId();
            
            // 通过 PluginConfigMetadataProvider 获取配置元数据
            List< com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata> metadata = 
                    pluginConfigMetadataProvider.getPluginPackageConfigMetadata(pluginId);
            
            if (metadata == null || metadata.isEmpty()) {
                return Result.success(java.util.Collections.emptyList());
            }
            
            return Result.success(metadata);
        } catch (Exception e) {
            log.error("获取应用配置元数据失败: id={}", id, e);
            return Result.error("获取应用配置元数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取应用配置
     */
    public Result<ApplicationConfigDTO> getApplicationConfig(Long id) {
        try {
            SysApplication application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            ApplicationConfigDTO config = new ApplicationConfigDTO();
            
            // 从 extension_config JSON 字段中解析配置（嵌套格式）
            Map<String, Object> allConfig = new HashMap<>();
            if (application.getExtensionConfig() != null && !application.getExtensionConfig().isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    allConfig = mapper.readValue(
                            application.getExtensionConfig(),
                            new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("解析扩展配置失败，使用空配置: id={}", id, e);
                    allConfig = new HashMap<>();
                }
            }
            
            if (allConfig == null) {
                allConfig = new HashMap<>();
            }
            
            // 从总配置中还原通用应用配置字段
            Object autoLoadVal = allConfig.get("autoLoad");
            if (autoLoadVal != null) {
                config.setAutoLoad(parseBoolean(autoLoadVal));
            }
            
            Object loadOnStartupVal = allConfig.get("loadOnStartup");
            if (loadOnStartupVal != null) {
                config.setLoadOnStartup(parseBoolean(loadOnStartupVal));
            }
            
            Object startPriorityVal = allConfig.get("startPriority");
            if (startPriorityVal != null) {
                config.setStartPriority(parseInteger(startPriorityVal));
            }
            
            Object startDelayVal = allConfig.get("startDelay");
            if (startDelayVal != null) {
                config.setStartDelay(parseInteger(startDelayVal));
            }
            
            Object descVal = allConfig.get("description");
            if (descVal != null) {
                config.setDescription(String.valueOf(descVal));
            }
            
            // 将剩余配置作为 extensionConfig 返回给前端
            config.setExtensionConfig(allConfig);
            
            return Result.success(config);
        } catch (Exception e) {
            log.error("获取应用配置失败: id={}", id, e);
            return Result.error("获取应用配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新应用配置
     */
    public Result<Void> updateApplicationConfig(Long id, ApplicationConfigDTO config) {
        long startTime = System.currentTimeMillis();
        SysApplication application = null;
        Map<String, Object> oldConfig = null;
        
        try {
            application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            // 获取原配置
            String oldConfigJson = application.getExtensionConfig();
            if (oldConfigJson != null && !oldConfigJson.isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    oldConfig = mapper.readValue(oldConfigJson, 
                            new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("解析原配置失败", e);
                }
            }
            
            // extension_config JSON 中统一保存：
            // - 通用应用配置：autoLoad / loadOnStartup / startPriority / startDelay / description
            // - 插件扩展配置：extensionConfig
            Map<String, Object> nestedConfig = config.getExtensionConfig();
            if (nestedConfig == null) {
                nestedConfig = new HashMap<>();
            }
            
            // 合并通用配置字段到同一个 Map 中，确保它们也被持久化到 extension_config
            if (config.getAutoLoad() != null) {
                nestedConfig.put("autoLoad", config.getAutoLoad());
            }
            if (config.getLoadOnStartup() != null) {
                nestedConfig.put("loadOnStartup", config.getLoadOnStartup());
            }
            if (config.getStartPriority() != null) {
                nestedConfig.put("startPriority", config.getStartPriority());
            }
            if (config.getStartDelay() != null) {
                nestedConfig.put("startDelay", config.getStartDelay());
            }
            if (config.getDescription() != null) {
                nestedConfig.put("description", config.getDescription());
            }
            
            // 直接转换为 JSON 字符串（嵌套格式）
            ObjectMapper mapper = new ObjectMapper();
            String configJson = mapper.writeValueAsString(nestedConfig);
            
            // 更新配置
            boolean updated = persistenceService.updateExtensionConfig(id, configJson, "admin");
            
            if (updated) {
                log.info("应用配置更新成功: id={}, configKeys={}", id, nestedConfig.keySet());
                operationLogger.logConfigUpdate(application, "admin", "admin", "SUCCESS", 
                        "配置更新成功", oldConfig, nestedConfig, startTime);
                return Result.success();
            } else {
                operationLogger.logConfigUpdate(application, "admin", "admin", "FAIL", 
                        "更新数据库失败", oldConfig, nestedConfig, startTime);
                return Result.error("更新应用配置失败");
            }
        } catch (Exception e) {
            log.error("更新应用配置失败: id={}", id, e);
            if (application != null) {
                operationLogger.logConfigUpdate(application, "admin", "admin", "FAIL", 
                        "异常: " + e.getMessage(), oldConfig, null, startTime);
            }
            return Result.error("更新应用配置失败: " + e.getMessage());
        }
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 映射实体到DTO
     */
    private ApplicationDTO mapToApplicationDTO(SysApplication application) {
        ApplicationDTO dto = new ApplicationDTO();
        dto.setId(application.getId());
        dto.setApplicationCode(application.getApplicationCode());
        dto.setApplicationName(application.getApplicationName());
        dto.setPluginId(application.getPluginId());
        dto.setPluginVersion(application.getPluginVersion());
        dto.setPluginType(application.getPluginType());
        dto.setDescription(application.getDescription());
        dto.setAuthor(application.getAuthor());
        dto.setHomepage(application.getHomepage());
        dto.setApplicationType(application.getApplicationType());
        dto.setStatus(application.getStatus());
        dto.setIsDefault(application.getIsDefault());
        dto.setInstallTime(application.getInstallTime());
        dto.setUpdateTime(application.getUpdateTime());
        dto.setCreateBy(application.getCreateBy());
        dto.setUpdateBy(application.getUpdateBy());
        dto.setNamespaceCode(application.getNamespaceCode());
        
        // 设置计算字段：应用类型文本
        if ("integrated".equals(application.getApplicationType())) {
            dto.setApplicationTypeText("集成应用");
        } else if ("plugin".equals(application.getApplicationType())) {
            dto.setApplicationTypeText("插件应用");
        } else {
            dto.setApplicationTypeText(application.getApplicationType());
        }
        
        // 设置计算字段：状态文本
        if (application.getStatus() != null) {
            dto.setStatusText(application.getStatus() == 1 ? "启用" : "禁用");
        }
        
        return dto;
    }
    
    /**
     * 映射升级日志到DTO
     */
    private ApplicationUpgradeLogDTO mapToUpgradeLogDTO(SysApplicationUpgradeLog log) {
        ApplicationUpgradeLogDTO dto = new ApplicationUpgradeLogDTO();
        dto.setId(log.getId());
        dto.setApplicationId(log.getApplicationId());
        dto.setPluginId(log.getPluginId());
        dto.setOldVersion(log.getOldVersion());
        dto.setNewVersion(log.getNewVersion());
        dto.setTargetVersion(log.getTargetVersion());
        dto.setPluginType(log.getPluginType());
        dto.setOperatorName(log.getOperatorName());
        dto.setStatus(log.getStatus());
        dto.setMessage(log.getMessage());
        dto.setCreateTime(log.getCreateTime());
        return dto;
    }
    
    /**
     * 映射操作日志到DTO
     */
    private ApplicationOperationLogDTO mapToOperationLogDTO(
            com.keqi.gress.plugin.appstore.domain.entity.SysApplicationOperationLog log) {
        ApplicationOperationLogDTO dto = new ApplicationOperationLogDTO();
        dto.setId(log.getId());
        dto.setApplicationId(log.getApplicationId());
        dto.setApplicationName(log.getApplicationName());
        dto.setPluginId(log.getPluginId());
        dto.setOperationType(log.getOperationType());
        dto.setOperationDesc(log.getOperationDesc());
        dto.setStatus(log.getStatus());
        dto.setOperatorId(log.getOperatorId());
        dto.setOperatorName(log.getOperatorName());
        dto.setMessage(log.getMessage());
        dto.setBeforeData(log.getBeforeData());
        dto.setAfterData(log.getAfterData());
        dto.setDuration(log.getDuration());
        dto.setCreateTime(log.getCreateTime());
        
        // 设置计算字段：操作类型文本
        if (log.getOperationType() != null) {
            switch (log.getOperationType()) {
                case "START":
                    dto.setOperationTypeText("启动");
                    break;
                case "STOP":
                    dto.setOperationTypeText("停止");
                    break;
                case "RESTART":
                    dto.setOperationTypeText("重启");
                    break;
                case "INSTALL":
                    dto.setOperationTypeText("安装");
                    break;
                case "UNINSTALL":
                    dto.setOperationTypeText("卸载");
                    break;
                case "UPGRADE":
                    dto.setOperationTypeText("升级");
                    break;
                case "ROLLBACK":
                    dto.setOperationTypeText("降级");
                    break;
                case "CONFIG_UPDATE":
                    dto.setOperationTypeText("配置更新");
                    break;
                default:
                    dto.setOperationTypeText(log.getOperationType());
            }
        }
        
        // 设置计算字段：状态文本
        if (log.getStatus() != null) {
            dto.setStatusText("SUCCESS".equals(log.getStatus()) ? "成功" : "失败");
        }
        
        return dto;
    }
    
    /**
     * 创建临时应用对象（用于日志记录）
     */
    private SysApplication createTempApp(String applicationName, String pluginId) {
        SysApplication app = new SysApplication();
        app.setApplicationName(applicationName);
        app.setPluginId(pluginId);
        return app;
    }
    
    /**
     * 解析布尔值
     */
    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
    
    /**
     * 解析整数值
     */
    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
