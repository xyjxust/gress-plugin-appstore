package com.keqi.gress.plugin.appstore.contoller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.config.AppStoreConfig;
import com.keqi.gress.plugin.appstore.dto.*;
import com.keqi.gress.plugin.appstore.service.ApplicationManagementService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 应用管理控制器
 */
@Slf4j
@Service
@RestController
@RequestMapping("/applications")
@Valid
public class ApplicationManagementController {

   // private final Log log = LogFactory.get(ApplicationManagementService.class);
    
    @Inject(source = Inject.BeanSource.PLUGIN)
    private ApplicationManagementService applicationManagementService;
    
    @Inject(source = Inject.BeanSource.PLUGIN)
    private com.keqi.gress.plugin.appstore.service.AppStoreApiService appStoreApiService;
    
    @Inject(source = Inject.BeanSource.SPRING)
    private  com.keqi.gress.common.plugin.PluginConfigMetadataProvider pluginConfigMetadataProvider;
    @Inject(source = Inject.BeanSource.PLUGIN)
    private AppStoreConfig appStoreConfig;
    
    /**
     * 查询应用列表
     */
    @GetMapping
    public Result<PageResult<ApplicationDTO>> queryApplications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
             @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String applicationType,
            @RequestParam(required = false) String pluginId) {
        
        log.info("查询应用列表1: page={}, size={}, keyword={}, status={}, applicationType={}, pluginId={}",
                page, size, keyword, status, applicationType, pluginId);

        log.info("查询应用列表: name={}, size={}, keyword={}, status={}, applicationType={}, pluginId={}",
                appStoreConfig.getStoreName(), size, keyword, status, applicationType, pluginId);
        ApplicationQueryRequest request = new ApplicationQueryRequest();
        request.setPage(page);
        request.setSize(size);
        request.setKeyword(keyword);
        request.setStatus(status);
        request.setApplicationType(applicationType);
        request.setPluginId(pluginId);
        
        Result<PageResult<ApplicationDTO>> result = applicationManagementService.queryApplications(request);
        
        // 检查远程是否有新版本
        if (result.isSuccess() && result.getData() != null) {
            try {
                checkRemoteVersions(result.getData().getItems());
            } catch (Exception e) {
                log.warn("检查远程版本失败", e);
                // 不影响主流程，继续返回结果
            }
        }
        
        return result;
    }
    
    /**
     * 检查远程版本
     */
    private void checkRemoteVersions(java.util.List<ApplicationDTO> localApplications) {
        if (localApplications == null || localApplications.isEmpty()) {
            return;
        }
        
        try {
            // 获取远程应用列表
            java.util.List<ApplicationDTO> remoteApplications = appStoreApiService.getApplications(1, 1000, null);
            
            // 创建远程应用映射表（pluginId -> ApplicationDTO）
            java.util.Map<String, ApplicationDTO> remoteAppMap = new java.util.HashMap<>();
            for (ApplicationDTO remoteApp : remoteApplications) {
                remoteAppMap.put(remoteApp.getPluginId(), remoteApp);
            }
            
            // 检查每个本地应用是否有远程新版本
            for (ApplicationDTO localApp : localApplications) {
                ApplicationDTO remoteApp = remoteAppMap.get(localApp.getPluginId());
                
                if (remoteApp != null && remoteApp.getPluginVersion() != null) {
                    // 设置远程版本
                    localApp.setRemoteVersion(remoteApp.getPluginVersion());
                    
                    // 检查是否有新版本
                    if (isVersionNewer(remoteApp.getPluginVersion(), localApp.getPluginVersion())) {
                        localApp.setHasNewVersion(true);
                    } else {
                        localApp.setHasNewVersion(false);
                    }
                } else {
                    localApp.setHasNewVersion(false);
                }
            }
        } catch (Exception e) {
            log.warn("检查远程版本失败", e);
        }
    }
    
    /**
     * 获取应用详情
     */
    @GetMapping("/{id}")
    public Result<ApplicationDTO> getApplicationDetail(@PathVariable Long id) {
        log.info("获取应用详情: id={}", id);
        return applicationManagementService.getApplicationDetail(id);
    }

    /**
     * 查询应用升级日志
     */
    @GetMapping("/{id}/upgrade-logs")
    public Result<java.util.List<ApplicationUpgradeLogDTO>> getApplicationUpgradeLogs(@PathVariable Long id) {
        log.info("查询应用升级日志: id={}", id);
        return applicationManagementService.getUpgradeLogs(id);
    }
    
    /**
     * 升级应用
     */
    @PostMapping("/{id}/upgrade")
    public Result<Void> upgradeApplication(@PathVariable Long id, @RequestBody ApplicationUpgradeRequest request) {
        log.info("升级应用: id={}, targetVersion={}, operator={}", 
                id, request.getTargetVersion(), request.getOperatorName());
        return applicationManagementService.upgradeApplication(id, request);
    }

    /**
     * 降级应用（按指定版本回滚）
     */
    @PostMapping("/{id}/rollback")
    public Result<Void> rollbackApplication(@PathVariable Long id, @RequestBody ApplicationUpgradeRequest request) {
        log.info("降级应用: id={}, targetVersion={}, operator={}",
                id, request.getTargetVersion(), request.getOperatorName());
        return applicationManagementService.rollbackApplication(id, request);
    }
    
    /**
     * 卸载应用
     */
    @DeleteMapping("/{id}")
    public Result<Void> uninstallApplication(@PathVariable Long id, @RequestBody ApplicationUninstallRequest request) {
        log.info("卸载应用: id={}, operator={}, reason={}", 
                id, request.getOperatorName(), request.getReason());
        return applicationManagementService.uninstallApplication(id, request);
    }
    
    /**
     * 启用应用（启动插件包）
     */
    @PostMapping("/{id}/enable")
    public Result<Void> enableApplication(@PathVariable Long id) {
        log.info("启动应用: id={}", id);
        return applicationManagementService.startApplication(id, "admin");
    }
    
    /**
     * 禁用应用（停止插件包）
     */
    @PostMapping("/{id}/disable")
    public Result<Void> disableApplication(@PathVariable Long id) {
        log.info("停止应用: id={}", id);
        return applicationManagementService.stopApplication(id, "admin");
    }
    
    /**
     * 重启应用
     */
    @PostMapping("/{id}/restart")
    public Result<Void> restartApplication(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        String operatorName = body != null ? body.get("operatorName") : "admin";
        log.info("重启应用: id={}, operator={}", id, operatorName);
        return applicationManagementService.restartApplication(id, operatorName);
    }
    
    /**
     * 上传并安装应用包
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Result<Void> uploadAndInstall(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "admin") String operatorId,
            @RequestParam(required = false, defaultValue = "admin") String operatorName) {
        
        log.info("上传并安装应用包: fileName={}, size={}, operator={}", 
                file.getOriginalFilename(), file.getSize(), operatorName);
        
        return applicationManagementService.uploadAndInstall(file, operatorId, operatorName);
    }
    
    /**
     * 从远程应用商店安装应用
     */
    @PostMapping("/remote/install")
    public Result<Void> installRemoteApplication(
            @RequestParam String pluginId,
            @RequestParam(required = false, defaultValue = "admin") String operatorId,
            @RequestParam(required = false, defaultValue = "admin") String operatorName) {
        
        log.info("从远程应用商店安装应用: pluginId={}, operator={}", pluginId, operatorName);
        
        try {
            // 使用带依赖处理的安装流程：先根据远程详情解析依赖并安装依赖，再安装主应用
            Result< com.keqi.gress.common.plugin.PluginPackageInstallResult> installResult =
                    applicationManagementService.installApplicationFromAppStore(pluginId, null, operatorName);

            if (!installResult.isSuccess()) {
                String errorMsg = "安装应用失败: " + installResult.getErrorMessage();
                log.error(errorMsg);
                return Result.error(errorMsg);
            }

            log.info("应用安装成功: pluginId={}, operator={}", pluginId, operatorName);
            return Result.success();
            
        } catch (Exception e) {
            String errorMsg = "安装应用失败: " + e.getMessage();
            log.error("从远程应用商店安装应用失败: pluginId={}, error={}", pluginId, e.getMessage(), e);
            return Result.error(errorMsg);
        }
    }
    
    /**
     * 获取应用配置元数据（用于动态表单渲染）
     */
    @GetMapping("/{id}/config/metadata")
    public Result<java.util.List< com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata>> getApplicationConfigMetadata(@PathVariable Long id) {
        log.info("获取应用配置元数据: id={}", id);
        return applicationManagementService.getApplicationConfigMetadata(id);
    }
    
    /**
     * 获取应用配置
     */
    @GetMapping("/{id}/config")
    public Result<ApplicationConfigDTO> getApplicationConfig(@PathVariable Long id) {
        log.info("获取应用配置: id={}", id);
        
        Result<ApplicationConfigDTO> result = applicationManagementService.getApplicationConfig(id);
        
        // 如果 extensionConfig 为空，从插件中获取当前配置
        if (result.isSuccess() && result.getData() != null) {
            ApplicationConfigDTO config = result.getData();
            
            if (config.getExtensionConfig() == null || config.getExtensionConfig().isEmpty()) {
                log.info("extensionConfig 为空，从插件中获取配置");
                
                try {
                    // 获取应用详情以获取 pluginId
                    Result<ApplicationDTO> appResult = applicationManagementService.getApplicationDetail(id);
                    if (appResult.isSuccess() && appResult.getData() != null) {
                        String pluginId = appResult.getData().getPluginId();
                        
                        if (pluginId != null && !pluginId.isEmpty()) {
                            // 从插件中获取配置（嵌套格式）
                            java.util.Map<String, Object> pluginConfig = 
                                pluginConfigMetadataProvider.getPluginPackageConfig(pluginId);
                            
                            if (pluginConfig != null && !pluginConfig.isEmpty()) {
                                log.info("从插件中获取到配置: {}", pluginConfig);
                                config.setExtensionConfig(pluginConfig);
                            } else {
                                log.info("插件配置为空");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("从插件中获取配置失败", e);
                    // 不影响主流程，继续返回原结果
                }
            }
            
            // 将嵌套格式的 extensionConfig 转换为拍平格式（DynamicFormRenderer 需要拍平格式）
            if (config.getExtensionConfig() != null && !config.getExtensionConfig().isEmpty()) {
                try {
                    java.util.Map<String, Object> flatConfig = 
                         com.keqi.gress.common.utils.ConfigUtils.nestedToFlat(config.getExtensionConfig());
                    log.info("转换为拍平格式: {}", flatConfig);
                    config.setExtensionConfig(flatConfig);
                } catch (Exception e) {
                    log.warn("转换配置为拍平格式失败", e);
                    // 不影响主流程，继续返回原结果
                }
            }
        }
        
        return result;
    }
    
    /**
     * 更新应用配置
     */
    @PutMapping("/{id}/config")
    public Result<Void> updateApplicationConfig(
            @PathVariable Long id, 
            @RequestBody ApplicationConfigDTO config) {
        log.info("更新应用配置: id={}, extensionConfig={}", id, config.getExtensionConfig());
        return applicationManagementService.updateApplicationConfig(id, config);
    }
    
    /**
     * 查询应用操作日志
     */
    @GetMapping("/{id}/operation-logs")
    public Result<PageResult<ApplicationOperationLogDTO>> getApplicationOperationLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String operationType) {
        
        log.info("查询应用操作日志: id={}, page={}, size={}, operationType={}", id, page, size, operationType);
        return applicationManagementService.getApplicationOperationLogs(id, page, size, operationType);
    }
    
    /**
     * 查询所有应用操作日志（全局查询）
     */
    @GetMapping("/operation-logs")
    public Result<PageResult<ApplicationOperationLogDTO>> getAllOperationLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String applicationName,
            @RequestParam(required = false) String status) {
        
        log.info("查询所有应用操作日志: page={}, size={}, operationType={}, operatorName={}, applicationName={}, status={}", 
                page, size, operationType, operatorName, applicationName, status);
        return applicationManagementService.getAllOperationLogs(page, size, operationType, operatorName, applicationName, status);
    }
    
    /**
     * 查询远程应用商店应用列表
     */
    @GetMapping("/remote")
    public Result<PageResult<ApplicationDTO>> queryRemoteApplications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        
        log.info("查询远程应用列表: page={}, size={}, keyword={}", page, size, keyword);
        
        try {
            // 获取远程应用列表（使用新的分页方法）
            PageResult<ApplicationDTO> remotePageResult = appStoreApiService.getApplicationsPage(page, size, keyword);
            
            if (remotePageResult == null || remotePageResult.getItems() == null) {
                return Result.success(createEmptyPageResult(page, size));
            }
            
            // 获取所有本地已安装应用
            ApplicationQueryRequest localRequest = new ApplicationQueryRequest();
            localRequest.setPage(1);
            localRequest.setSize(1000); // 获取所有本地应用
            Result<PageResult<ApplicationDTO>> localResult = applicationManagementService.queryApplications(localRequest);
            
            // 创建本地应用映射表（pluginId -> ApplicationDTO）
            java.util.Map<String, ApplicationDTO> localAppMap = new java.util.HashMap<>();
            if (localResult.isSuccess() && localResult.getData() != null) {
                for (ApplicationDTO localApp : localResult.getData().getItems()) {
                    localAppMap.put(localApp.getPluginId(), localApp);
                }
            }
            
            // 检查每个远程应用的安装状态
            for (ApplicationDTO remoteApp : remotePageResult.getItems()) {
                ApplicationDTO localApp = localAppMap.get(remoteApp.getPluginId());
                
                if (localApp == null) {
                    // 未安装
                    remoteApp.setInstallStatus("NOT_INSTALLED");
                    remoteApp.setLocalVersion(null);
                } else {
                    // 已安装，检查版本
                    remoteApp.setLocalVersion(localApp.getPluginVersion());
                    
                    if (isVersionNewer(remoteApp.getPluginVersion(), localApp.getPluginVersion())) {
                        // 远程版本更新，可升级
                        remoteApp.setInstallStatus("UPGRADABLE");
                    } else {
                        // 版本相同或本地版本更新
                        remoteApp.setInstallStatus("INSTALLED");
                    }
                }
            }
            
            return Result.success(remotePageResult);
        } catch (Exception e) {
            log.error("查询远程应用列表失败", e);
            return Result.error("查询远程应用列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建空的分页结果
     */
    private PageResult<ApplicationDTO> createEmptyPageResult(Integer page, Integer size) {
        PageResult<ApplicationDTO> pageResult = new PageResult<>();
        pageResult.setItems(java.util.Collections.emptyList());
        pageResult.setTotal(0L);
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setTotalPages(0);
        return pageResult;
    }
    
    /**
     * 比较版本号
     * 
     * @param remoteVersion 远程版本
     * @param localVersion 本地版本
     * @return true 如果远程版本更新
     */
    private boolean isVersionNewer(String remoteVersion, String localVersion) {
        if (remoteVersion == null || localVersion == null) {
            return false;
        }
        
        try {
            // 移除版本号中的 'v' 前缀
            String remote = remoteVersion.toLowerCase().replaceAll("^v", "");
            String local = localVersion.toLowerCase().replaceAll("^v", "");
            
            // 分割版本号
            String[] remoteParts = remote.split("\\.");
            String[] localParts = local.split("\\.");
            
            int maxLength = Math.max(remoteParts.length, localParts.length);
            
            for (int i = 0; i < maxLength; i++) {
                int remotePart = i < remoteParts.length ? parseVersionPart(remoteParts[i]) : 0;
                int localPart = i < localParts.length ? parseVersionPart(localParts[i]) : 0;
                
                if (remotePart > localPart) {
                    return true;
                } else if (remotePart < localPart) {
                    return false;
                }
            }
            
            return false; // 版本相同
        } catch (Exception e) {
            log.warn("版本号比较失败: remote={}, local={}", remoteVersion, localVersion, e);
            return false;
        }
    }
    
    /**
     * 解析版本号部分
     */
    private int parseVersionPart(String part) {
        try {
            // 提取数字部分（处理 1.0.0-beta 这样的版本）
            String numericPart = part.replaceAll("[^0-9].*$", "");
            return numericPart.isEmpty() ? 0 : Integer.parseInt(numericPart);
        } catch (Exception e) {
            return 0;
        }
    }
}
