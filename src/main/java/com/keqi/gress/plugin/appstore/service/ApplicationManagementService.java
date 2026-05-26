package com.keqi.gress.plugin.appstore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keqi.gress.common.model.Result;
import com.keqi.gress.common.plugin.PluginPackageInstallResult;
import com.keqi.gress.common.plugin.PluginPackageLifecycle;
import com.keqi.gress.common.storage.FileStorageService;
import com.keqi.gress.plugin.api.database.page.IPage;
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
import com.keqi.gress.plugin.appstore.support.OperatorContextHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final String APP_TYPE_AGGREGATED = "aggregated";

  //  private static final Log log = LogFactory.get(ApplicationManagementService.class);
    
    // === 核心组件（编排器） ===
    
    @Autowired
    private InstallOrchestrator installOrchestrator;
    
    @Autowired
    private UpgradeOrchestrator upgradeOrchestrator;
    
    @Autowired
    private UninstallOrchestrator uninstallOrchestrator;
    
    // === 基础服务 ===
    
    @Autowired
    private ApplicationPersistenceService persistenceService;
    
    @Autowired
    private ApplicationOperationLogger operationLogger;
    
    @Autowired
    private ApplicationDao applicationDao;
    
    @Autowired
    private ApplicationUpgradeLogDao applicationUpgradeLogDao;
    
    @Autowired
    private com.keqi.gress.plugin.appstore.dao.ApplicationOperationLogDao applicationOperationLogDao;
    
    @Autowired
    private PluginPackageLifecycle pluginPackageLifecycle;
    
    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private AppStoreApiService appStoreApiService;
    
    @Autowired
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
                request.getPluginId(),
                request.getClientType(),
                request.getPreloadEnabled(),
                request.getTag()
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

    /**
     * 查询聚合应用列表
     */
    public Result<List<ApplicationDTO>> listAggregateApplications() {
        try {
            List<ApplicationDTO> list = applicationDao.findByApplicationType(APP_TYPE_AGGREGATED).stream()
                    .map(this::mapToApplicationDTO)
                    .collect(Collectors.toList());
            return Result.success(list);
        } catch (Exception e) {
            log.error("查询聚合应用列表失败", e);
            return Result.error("查询聚合应用列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询可被聚合的插件应用（过滤掉聚合应用本身）
     */
    public Result<List<ApplicationDTO>> listAggregatablePlugins() {
        try {
            List<ApplicationDTO> list = applicationDao.findAll().stream()
                    .filter(app -> !"aggregated".equalsIgnoreCase(app.getApplicationType()))
                    .filter(this::surfaceAdminFlagFromEntity)
                    .map(this::mapToApplicationDTO)
                    .collect(Collectors.toList());
            return Result.success(list);
        } catch (Exception e) {
            log.error("查询可聚合插件失败", e);
            return Result.error("查询可聚合插件失败: " + e.getMessage());
        }
    }

    /** 仅允许将声明了 B 端表面的插件纳入聚合 */
    private boolean surfaceAdminFlagFromEntity(SysApplication app) {
        Map<String, Object> ext = parseExtConfig(app.getExtensionConfig());
        return Boolean.TRUE.equals(ext.get("surfaceAdmin"));
    }

    /**
     * 创建聚合应用
     */
    public Result<Void> createAggregateApplication(AggregateApplicationRequest request, String operatorName) {
        return saveAggregateApplication(null, request, operatorName);
    }

    /**
     * 更新聚合应用
     */
    public Result<Void> updateAggregateApplication(Long id, AggregateApplicationRequest request, String operatorName) {
        return saveAggregateApplication(id, request, operatorName);
    }

    /**
     * 删除聚合应用
     */
    public Result<Void> deleteAggregateApplication(Long id, String operatorName) {
        try {
            SysApplication existing = persistenceService.findById(id);
            if (existing == null) {
                return Result.error("聚合应用不存在");
            }
            if (!APP_TYPE_AGGREGATED.equalsIgnoreCase(existing.getApplicationType())) {
                return Result.error("仅支持删除聚合应用");
            }
            int rows = applicationDao.deleteApplication(id);
            if (rows <= 0) {
                return Result.error("删除聚合应用失败");
            }
            return Result.success();
        } catch (Exception e) {
            log.error("删除聚合应用失败: id={}", id, e);
            return Result.error("删除聚合应用失败: " + e.getMessage());
        }
    }
    
    // ==================== 安装相关（委托给 InstallOrchestrator） ====================
    
    /**
     * 上传并安装应用
     */
    public Result<Void> uploadAndInstall(MultipartFile file, String operatorId, String operatorName) {
        return uploadAndInstall(file, operatorId, operatorName, null);
    }

    public Result<Void> uploadAndInstall(
            MultipartFile file,
            String operatorId,
            String operatorName,
            Map<String, Object> installConfig) {
        long startTime = System.currentTimeMillis();
        SysApplication tempApp = createTempApp("上传的应用", "unknown");
        String uploadedFileUrl = null;

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
            uploadedFileUrl = fileStorageService
                .upload(new ByteArrayInputStream(file.getBytes()), originalFilename)
                .withMetadata("category", "plugin")
                .withMetadata("uploadBy", operatorName)
                .onSuccess(savedUrl -> log.info("应用包文件上传成功: {}", savedUrl))
                .onError(e -> log.error("应用包文件上传失败", e))
                .get();
            
            if (uploadedFileUrl == null || uploadedFileUrl.isEmpty()) {
                return Result.error("文件上传失败");
            }
            
            log.info("应用包文件上传成功: fileUrl={}", uploadedFileUrl);
            
            // 3. 委托给 InstallOrchestrator 执行安装
            Result<PluginPackageInstallResult> installResult = 
                    // 本地上传安装：不从应用商店下载表权限数据
                    installOrchestrator.installFromUrl(uploadedFileUrl, operatorName, false, installConfig);
            
            if (!installResult.isSuccess()) {
                operationLogger.logFailure(tempApp, "INSTALL", "上传并安装应用", 
                        operatorId, operatorName, installResult.getErrorMessage(), startTime);
                deleteUploadedPluginArtifactSilently(uploadedFileUrl);
                return Result.error(installResult.getErrorMessage());
            }
            
            // 操作日志在 Orchestrator 中已记录
            return Result.success();
            
        } catch (Exception e) {
            log.error("上传并安装应用失败", e);
            deleteUploadedPluginArtifactSilently(uploadedFileUrl);
            operationLogger.logFailure(tempApp, "INSTALL", "上传并安装应用", 
                    operatorId, operatorName, "异常: " + e.getMessage(), startTime);
            return Result.error("上传并安装应用失败: " + e.getMessage());
        }
    }

    /**
     * 上传安装失败时删除对象存储中的插件包，避免残留占用空间。
     */
    private void deleteUploadedPluginArtifactSilently(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        try {
            Result<Boolean> del = fileStorageService.delete(fileUrl).execute();
            if (del.isSuccess() && Boolean.TRUE.equals(del.getData())) {
                log.info("安装失败，已删除上传的插件包存储文件: {}", fileUrl);
            } else if (del.isSuccess()) {
                log.warn("安装失败，删除上传文件返回 false（可能已不存在）: {}", fileUrl);
            } else {
                log.warn("安装失败，删除上传文件未成功: url={}, msg={}", fileUrl, del.getErrorMessage());
            }
        } catch (Exception ex) {
            log.warn("安装失败，删除上传文件异常（忽略）: {}", fileUrl, ex);
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
        return installApplicationFromAppStore(pluginId, version, operatorName, null);
    }

    public Result<PluginPackageInstallResult> installApplicationFromAppStore(
            String pluginId, String version, String operatorName, Map<String, Object> installConfig) {
        return installOrchestrator.installFromAppStore(pluginId, version, operatorName, installConfig);
    }

    public Result<List<com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata>> getRemoteInstallConfigMetadata(String pluginId) {
        try {
            return Result.success(appStoreApiService.getPluginInstallConfigMetadataFromJar(pluginId));
        } catch (Exception e) {
            log.error("获取远程安装前配置元数据失败: pluginId={}", pluginId, e);
            return Result.error("获取安装前配置元数据失败: " + e.getMessage());
        }
    }

    public Result<List<com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata>> getUploadInstallConfigMetadata(MultipartFile file) {
        java.nio.file.Path tmpJarPath = null;
        try {
            if (file == null || file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            tmpJarPath = java.nio.file.Files.createTempFile("plugin-upload-install-config-", ".jar");
            file.transferTo(tmpJarPath);
            return Result.success(appStoreApiService.parseInstallConfigMetadataFromJar(tmpJarPath));
        } catch (Exception e) {
            log.error("获取上传安装前配置元数据失败", e);
            return Result.error("获取安装前配置元数据失败: " + e.getMessage());
        } finally {
            if (tmpJarPath != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tmpJarPath);
                } catch (Exception e) {
                    log.warn("清理上传安装前配置临时文件失败: {}", tmpJarPath, e);
                }
            }
        }
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
        String resolvedOperatorName = OperatorContextHelper.resolveOperatorName(operatorName);
        String resolvedOperatorId = OperatorContextHelper.getOperatorId();
        
        try {
            application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            String packageId = application.getPluginId();
            log.info("启动应用: id={}, packageId={}, operator={}", id, packageId, resolvedOperatorName);
            
            Result<?> result = pluginPackageLifecycle.start(packageId);
            
            if (result.isSuccess()) {
                persistenceService.updateStatus(id, 1, resolvedOperatorName);
                operationLogger.logSuccess(application, "START", "启动应用", 
                        resolvedOperatorId, resolvedOperatorName, "启动成功", startTime);
                return Result.success();
            } else {
                operationLogger.logFailure(application, "START", "启动应用", 
                        resolvedOperatorId, resolvedOperatorName, "启动失败: " + result.getErrorMessage(), startTime);
                return Result.error("启动应用失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("启动应用失败: id={}", id, e);
            if (application != null) {
                operationLogger.logFailure(application, "START", "启动应用", 
                        resolvedOperatorId, resolvedOperatorName, "异常: " + e.getMessage(), startTime);
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
        String resolvedOperatorName = OperatorContextHelper.resolveOperatorName(operatorName);
        String resolvedOperatorId = OperatorContextHelper.getOperatorId();
        
        try {
            application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            String packageId = application.getPluginId();
            log.info("停止应用: id={}, packageId={}, operator={}", id, packageId, resolvedOperatorName);
            
            Result<?> result = pluginPackageLifecycle.stop(packageId);
            
            if (result.isSuccess()) {
                persistenceService.updateStatus(id, 0, resolvedOperatorName);
                operationLogger.logSuccess(application, "STOP", "停止应用", 
                        resolvedOperatorId, resolvedOperatorName, "停止成功", startTime);
                return Result.success();
            } else {
                operationLogger.logFailure(application, "STOP", "停止应用", 
                        resolvedOperatorId, resolvedOperatorName, "停止失败: " + result.getErrorMessage(), startTime);
                return Result.error("停止应用失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("停止应用失败: id={}", id, e);
            if (application != null) {
                operationLogger.logFailure(application, "STOP", "停止应用", 
                        resolvedOperatorId, resolvedOperatorName, "异常: " + e.getMessage(), startTime);
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
        String resolvedOperatorName = OperatorContextHelper.resolveOperatorName(operatorName);
        String resolvedOperatorId = OperatorContextHelper.getOperatorId();
        
        try {
            application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            String packageId = application.getPluginId();
            log.info("重启应用: id={}, packageId={}, operator={}", id, packageId, resolvedOperatorName);
            
            Result<?> result = pluginPackageLifecycle.restart(packageId);
            
            if (result.isSuccess()) {
                operationLogger.logSuccess(application, "RESTART", "重启应用", 
                        resolvedOperatorId, resolvedOperatorName, "重启成功", startTime);
                return Result.success();
            } else {
                operationLogger.logFailure(application, "RESTART", "重启应用", 
                        resolvedOperatorId, resolvedOperatorName, "重启失败: " + result.getErrorMessage(), startTime);
                return Result.error("重启应用失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("重启应用失败: id={}", id, e);
            if (application != null) {
                operationLogger.logFailure(application, "RESTART", "重启应用", 
                        resolvedOperatorId, resolvedOperatorName, "异常: " + e.getMessage(), startTime);
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
            
            // 从 extension_config JSON 解析（推荐拍平键；历史数据可能为嵌套，GET 出口仍会 nestedToFlat）
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
            
            Object autoLoadAdminVal = allConfig.get("autoLoadAdmin");
            if (autoLoadAdminVal != null) {
                config.setAutoLoadAdmin(parseBoolean(autoLoadAdminVal));
            }
            Object autoLoadConsumerVal = allConfig.get("autoLoadConsumer");
            if (autoLoadConsumerVal != null) {
                config.setAutoLoadConsumer(parseBoolean(autoLoadConsumerVal));
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
            
            // extension_config JSON 与 GET /applications/{id}/config、PluginConfigLoader 约定一致：
            // 使用「拍平」结构持久化（键如 appstore.storeUrl），不再 flatToNested。
            // 否则会把带点号的键收成嵌套对象，与前端提交的 extensionConfig 不一致，且 PluginConfigLoader
            // 从 DB 读出的嵌套 Map 与 plugin.yml 展平结果合并时行为混乱。
            Map<String, Object> flatExtensionConfig = config.getExtensionConfig();
            if (flatExtensionConfig == null) {
                flatExtensionConfig = new HashMap<>();
            }

            Map<String, Object> flatToPersist = new LinkedHashMap<>(flatExtensionConfig);

            // DTO 顶层字段覆盖 extensionConfig 中同名键（请求体常同时带两份）
            if (config.getAutoLoadAdmin() != null) {
                flatToPersist.put("autoLoadAdmin", config.getAutoLoadAdmin());
            }
            if (config.getAutoLoadConsumer() != null) {
                flatToPersist.put("autoLoadConsumer", config.getAutoLoadConsumer());
            }
            if (config.getLoadOnStartup() != null) {
                flatToPersist.put("loadOnStartup", config.getLoadOnStartup());
            }
            if (config.getStartPriority() != null) {
                flatToPersist.put("startPriority", config.getStartPriority());
            }
            if (config.getStartDelay() != null) {
                flatToPersist.put("startDelay", config.getStartDelay());
            }
            if (config.getDescription() != null) {
                flatToPersist.put("description", config.getDescription());
            }

            ObjectMapper mapper = new ObjectMapper();
            String configJson = mapper.writeValueAsString(flatToPersist);

            String operatorId = OperatorContextHelper.getOperatorId();
            String operatorName = OperatorContextHelper.getOperatorName();

            // 更新配置
            boolean updated = persistenceService.updateExtensionConfig(id, configJson, operatorName);

            if (updated) {
                log.info("应用配置更新成功: id={}, configKeys={}", id, flatToPersist.keySet());
                operationLogger.logConfigUpdate(application, operatorId, operatorName, "SUCCESS",
                        "配置更新成功", oldConfig, flatToPersist, startTime);
                return Result.success();
            } else {
                operationLogger.logConfigUpdate(application, operatorId, operatorName, "FAIL",
                        "更新数据库失败", oldConfig, flatToPersist, startTime);
                return Result.error("更新应用配置失败");
            }
        } catch (Exception e) {
            log.error("更新应用配置失败: id={}", id, e);
            if (application != null) {
                operationLogger.logConfigUpdate(application,
                        OperatorContextHelper.getOperatorId(),
                        OperatorContextHelper.getOperatorName(),
                        "FAIL",
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
        dto.setIcon(application.getIcon());
        dto.setHomepage(application.getHomepage());
        dto.setApplicationType(application.getApplicationType());
        dto.setStatus(application.getStatus());
        dto.setIsDefault(application.getIsDefault());
        dto.setInstallTime(application.getInstallTime());
        dto.setUpdateTime(application.getUpdateTime());
        dto.setCreateBy(application.getCreatedBy());
        dto.setUpdateBy(application.getUpdatedBy());
        dto.setNamespaceCode(application.getNamespaceCode());

        // 聚合应用扩展信息
        Map<String, Object> extConfig = parseExtConfig(application.getExtensionConfig());
        boolean aggregateApp = "aggregated".equalsIgnoreCase(application.getApplicationType())
                || Boolean.TRUE.equals(extConfig.get("aggregateApp"));
        dto.setAggregateApp(aggregateApp);
        dto.setAggregatedPluginIds(parseStringList(extConfig.get("aggregatedPluginIds")));
        dto.setSurfaceAdmin(Boolean.TRUE.equals(extConfig.get("surfaceAdmin")));
        dto.setSurfaceConsumer(Boolean.TRUE.equals(extConfig.get("surfaceConsumer")));
        dto.setAutoLoadAdmin(Boolean.TRUE.equals(extConfig.get("autoLoadAdmin")));
        dto.setAutoLoadConsumer(Boolean.TRUE.equals(extConfig.get("autoLoadConsumer")));
        dto.setAggregateListOrder(parseIntObject(extConfig.get("aggregateListOrder")));
        
        // 设置计算字段：应用类型文本
        if ("integrated".equals(application.getApplicationType())) {
            dto.setApplicationTypeText("集成应用");
        } else if ("plugin".equals(application.getApplicationType())) {
            dto.setApplicationTypeText("插件应用");
        } else if ("aggregated".equalsIgnoreCase(application.getApplicationType())) {
            dto.setApplicationTypeText("聚合应用");
        } else {
            dto.setApplicationTypeText(application.getApplicationType());
        }
        
        // 设置计算字段：状态文本
        if (application.getStatus() != null) {
            dto.setStatusText(application.getStatus() == 1 ? "启用" : "禁用");
        }
        
        return dto;
    }

    private Result<Void> saveAggregateApplication(Long id,
                                                  AggregateApplicationRequest request,
                                                  String operatorName) {
        try {
            if (request == null) {
                return Result.error("请求参数不能为空");
            }
            if (request.getApplicationCode() == null || request.getApplicationCode().trim().isEmpty()) {
                return Result.error("应用编码不能为空");
            }
            if (request.getApplicationName() == null || request.getApplicationName().trim().isEmpty()) {
                return Result.error("应用名称不能为空");
            }
            if (request.getPluginIds() == null || request.getPluginIds().isEmpty()) {
                return Result.error("请选择至少一个插件");
            }

            List<String> pluginIds = request.getPluginIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            if (pluginIds.isEmpty()) {
                return Result.error("请选择至少一个有效插件");
            }

            // 禁止将聚合应用再次聚合
            Set<String> aggregatePluginIds = applicationDao.findByApplicationType(APP_TYPE_AGGREGATED).stream()
                    .map(SysApplication::getPluginId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (String pid : pluginIds) {
                if (aggregatePluginIds.contains(pid)) {
                    return Result.error("聚合应用不能被再次聚合: " + pid);
                }
            }

            Map<String, Object> extConfig = new HashMap<>();
            extConfig.put("aggregateApp", true);
            extConfig.put("surfaceAdmin", true);
            extConfig.put("surfaceConsumer", false);
            extConfig.put("autoLoadAdmin", Boolean.TRUE.equals(request.getAutoLoad()));
            extConfig.put("autoLoadConsumer", false);
            extConfig.put("aggregatedPluginIds", pluginIds);
            int listOrder = request.getAggregateListOrder() != null ? request.getAggregateListOrder() : 1000;
            extConfig.put("aggregateListOrder", listOrder);
            String extJson = new ObjectMapper().writeValueAsString(extConfig);

            String iconVal = request.getIcon() != null ? request.getIcon().trim() : "";
            String iconToStore = iconVal.isEmpty() ? null : iconVal;

            if (id == null) {
                SysApplication existingByCode = applicationDao.getApplicationByCode(request.getApplicationCode().trim());
                if (existingByCode != null) {
                    return Result.error("应用编码已存在: " + request.getApplicationCode());
                }
                SysApplication app = new SysApplication();
                app.setApplicationCode(request.getApplicationCode().trim());
                app.setApplicationName(request.getApplicationName().trim());
                app.setPluginId(request.getApplicationCode().trim());
                app.setPluginVersion("1.0.0");
                app.setDescription(request.getDescription());
                app.setAuthor("system");
                app.setApplicationType(APP_TYPE_AGGREGATED);
                app.setPluginType("APPLICATION");
                app.setStatus(1);
                app.setIsDefault(0);
                app.setInstallTime(LocalDateTime.now());
                app.setUpdateTime(LocalDateTime.now());
                app.setExtensionConfig(extJson);
                app.setIcon(iconToStore);
                int rows = applicationDao.insertApplication(app);
                if (rows <= 0) {
                    return Result.error("创建聚合应用失败");
                }
                return Result.success();
            }

            SysApplication existing = persistenceService.findById(id);
            if (existing == null) {
                return Result.error("聚合应用不存在");
            }
            if (!APP_TYPE_AGGREGATED.equalsIgnoreCase(existing.getApplicationType())) {
                return Result.error("仅支持更新聚合应用");
            }
            int rows = applicationDao.updateAggregateApplication(
                    id,
                    request.getApplicationCode().trim(),
                    request.getApplicationName().trim(),
                    request.getDescription(),
                    iconToStore,
                    extJson,
                    OperatorContextHelper.resolveOperatorName(operatorName)
            );
            if (rows <= 0) {
                return Result.error("更新聚合应用失败");
            }
            return Result.success();
        } catch (Exception e) {
            log.error("保存聚合应用失败: id={}", id, e);
            return Result.error("保存聚合应用失败: " + e.getMessage());
        }
    }

    private Map<String, Object> parseExtConfig(String extJson) {
        if (extJson == null || extJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(extJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<String> parseStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    private Integer parseIntObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
