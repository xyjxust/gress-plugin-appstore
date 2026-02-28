package com.keqi.gress.plugin.appstore.service.orchestrator;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginPackageUpgradeResult;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import com.keqi.gress.plugin.appstore.dto.ApplicationUpgradeRequest;
import com.keqi.gress.plugin.appstore.service.ApplicationInstallService;
import com.keqi.gress.plugin.appstore.service.AppStoreApiService;
import com.keqi.gress.plugin.appstore.service.install.DependencyResolutionService;
import com.keqi.gress.plugin.appstore.service.persistence.ApplicationPersistenceService;
import com.keqi.gress.plugin.appstore.service.logging.ApplicationOperationLogger;
import lombok.extern.slf4j.Slf4j;

/**
 * 升级编排器
 * 
 * 职责：
 * - 协调完整的升级流程
 * - 遵循单一职责原则（SRP）：只负责流程编排
 * - 遵循开闭原则（OCP）：通过依赖注入扩展功能
 * - 遵循依赖倒置原则（DIP）：依赖抽象接口
 * 
 * 升级流程：
 * 1. 获取应用信息
 * 2. 版本检查（目标版本是否已安装）
 * 3. 依赖检查与安装
 * 4. 下载新版本文件
 * 5. 执行升级
 * 6. 更新数据库
 * 7. 记录升级日志
 * 
 * @author Gress Team
 */
@Service
@Slf4j
public class UpgradeOrchestrator {
    
  //  private static final Log log = LogFactory.get(UpgradeOrchestrator.class);
    
    @Inject
    private ApplicationPersistenceService persistenceService;
    
    @Inject
    private DependencyResolutionService dependencyResolutionService;
    
    @Inject
    private AppStoreApiService appStoreApiService;
    
    @Inject
    private ApplicationInstallService applicationInstallService;
    
    @Inject
    private ApplicationOperationLogger operationLogger;
    
    /**
     * 升级应用
     * 
     * @param id 应用ID
     * @param request 升级请求
     * @return 升级结果
     */
    public Result<Void> upgrade(Long id, ApplicationUpgradeRequest request) {
        long startTime = System.currentTimeMillis();
        SysApplication application = null;
        
        try {
            // 1. 验证参数
            if (request.getTargetVersion() == null || request.getTargetVersion().trim().isEmpty()) {
                return Result.error("目标版本不能为空");
            }
            
            // 2. 获取应用信息
            application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            String pluginId = application.getPluginId();
            String currentVersion = application.getPluginVersion();
            
            log.info("开始升级应用: id={}, pluginId={}, currentVersion={}, targetVersion={}", 
                    id, pluginId, currentVersion, request.getTargetVersion());
            
            // 3. 版本检查（目标版本是否已安装）
            if (dependencyResolutionService.isVersionAlreadyInstalled(pluginId, request.getTargetVersion())) {
                String msg = "目标版本已安装，无需升级: " + request.getTargetVersion();
                log.warn(msg);
                operationLogger.logFailure(application, "UPGRADE", 
                        String.format("升级应用从 %s 到 %s", currentVersion, request.getTargetVersion()), 
                        request.getOperatorId(), request.getOperatorName(), msg, startTime);
                return Result.error(msg);
            }
            
            // 4. 获取远程版本描述信息（用于记录升级日志）
            String remoteDescription = getRemoteDescription(pluginId, request.getTargetVersion());
            
            // 5. 依赖检查/安装/升级（带回滚变更集）
            Result<DependencyResolutionService.DependencyChangeSet> depEnsureResult =
                    dependencyResolutionService.ensureDependenciesUpgradedWithRollback(
                            pluginId, request.getTargetVersion(), request.getOperatorName());
            if (!depEnsureResult.isSuccess()) {
                operationLogger.logFailure(application, "UPGRADE", 
                        String.format("升级应用从 %s 到 %s", currentVersion, request.getTargetVersion()), 
                        request.getOperatorId(), request.getOperatorName(), 
                        depEnsureResult.getErrorMessage(), startTime);
                persistenceService.saveUpgradeLog(application, currentVersion, null, 
                        request.getTargetVersion(), null, request.getOperatorName(), 
                        "FAIL", depEnsureResult.getErrorMessage());
                return Result.error(depEnsureResult.getErrorMessage());
            }
            com.keqi.gress.plugin.appstore.service.install.DependencyResolutionService.DependencyChangeSet depChangeSet =
                    depEnsureResult.getData();
            
            // 6. 从应用商店下载新版本应用包
            log.info("从应用商店下载应用包: pluginId={}, version={}", pluginId, request.getTargetVersion());
            String fileUrl = appStoreApiService.downloadApplication(pluginId, request.getTargetVersion());
            
            if (fileUrl == null || fileUrl.isEmpty()) {
                String errorMsg = "从应用商店下载应用包失败";
                operationLogger.logFailure(application, "UPGRADE", 
                        String.format("升级应用从 %s 到 %s", currentVersion, request.getTargetVersion()), 
                        request.getOperatorId(), request.getOperatorName(), errorMsg, startTime);
                return Result.error(errorMsg);
            }
            
            log.info("应用包下载成功: fileUrl={}", fileUrl);
            
            // 7. 解析应用的扩展配置
            java.util.Map<String, Object> extensionConfig = 
                    persistenceService.parseExtensionConfig(application);
            
            // 8. 使用 PluginPackageLifecycle 进行升级（传入配置参数）
            log.info("开始执行插件包升级: packageId={}, fileUrl={}, hasConfig={}", 
                    pluginId, fileUrl, extensionConfig != null && !extensionConfig.isEmpty());
            
            Result<PluginPackageUpgradeResult> upgradeResult = 
                    applicationInstallService.upgradeApplication(
                            pluginId, fileUrl, request.getOperationType(), extensionConfig);
            
            if (!upgradeResult.isSuccess()) {
                log.error("插件包升级失败: {}", upgradeResult.getErrorMessage());
                // 主应用升级失败：回滚本次依赖变更（新安装卸载/已升级降级）
                try {
                    dependencyResolutionService.rollbackDependencyChangeSet(depChangeSet, request.getOperatorName());
                } catch (Exception e) {
                    log.warn("主应用升级失败后回滚依赖变更异常: pluginId={}", pluginId, e);
                }
                operationLogger.logFailure(application, "UPGRADE", 
                        String.format("升级应用从 %s 到 %s", currentVersion, request.getTargetVersion()), 
                        request.getOperatorId(), request.getOperatorName(), 
                        "升级失败: " + upgradeResult.getErrorMessage(), startTime);
                persistenceService.saveUpgradeLog(application, currentVersion, null, 
                        request.getTargetVersion(), null, request.getOperatorName(), 
                        "FAIL", upgradeResult.getErrorMessage());
                return Result.error("插件包升级失败: " + upgradeResult.getErrorMessage());
            }
            
            PluginPackageUpgradeResult result = upgradeResult.getData();
            log.info("插件包升级成功: oldVersion={}, newVersion={}, affectedPlugins={}", 
                    result.getOldVersion(), result.getNewVersion(), result.getAffectedPluginCount());
            
            // 9. 更新数据库中的版本信息和插件类型
            String newVersion = result.getNewVersion() != null && !result.getNewVersion().isEmpty() 
                    ? result.getNewVersion() 
                    : request.getTargetVersion();
            
            String pluginType = result.getPluginTypes() != null && !result.getPluginTypes().isEmpty() 
                    ? String.join(",", result.getPluginTypes()) 
                    : null;
            
            boolean updateSuccess = persistenceService.updateVersionAndType(
                    id, newVersion, pluginType, request.getOperatorName());
            
            if (updateSuccess) {
                log.info("应用升级成功: id={}, pluginId={}, oldVersion={}, newVersion={}, pluginType={}", 
                        id, pluginId, currentVersion, newVersion, pluginType);
                operationLogger.logSuccess(application, "UPGRADE", 
                        String.format("升级应用从 %s 到 %s", currentVersion, newVersion), 
                        request.getOperatorId(), request.getOperatorName(), "升级成功", startTime);
                persistenceService.saveUpgradeLog(application, currentVersion, newVersion, 
                        request.getTargetVersion(), pluginType, request.getOperatorName(), 
                        "SUCCESS", remoteDescription);
                return Result.success();
            } else {
                log.warn("插件包升级成功但更新数据库信息失败: id={}", id);
                String message = remoteDescription != null && !remoteDescription.isEmpty()
                        ? remoteDescription + "；升级成功但更新数据库失败"
                        : "升级成功但更新数据库失败";
                operationLogger.logFailure(application, "UPGRADE", 
                        String.format("升级应用从 %s 到 %s", currentVersion, newVersion), 
                        request.getOperatorId(), request.getOperatorName(), 
                        "更新数据库失败", startTime);
                persistenceService.saveUpgradeLog(application, currentVersion, newVersion, 
                        request.getTargetVersion(), pluginType, request.getOperatorName(), 
                        "FAIL", message);
                return Result.error("更新应用信息失败");
            }
            
        } catch (Exception e) {
            log.error("应用升级失败: id={}", id, e);
            if (application != null) {
                String currentVersion = application.getPluginVersion();
                operationLogger.logFailure(application, "UPGRADE", 
                        String.format("升级应用从 %s 到 %s", currentVersion, request.getTargetVersion()), 
                        request.getOperatorId(), request.getOperatorName(), 
                        "异常: " + e.getMessage(), startTime);
                persistenceService.saveUpgradeLog(application, currentVersion, null,
                        request.getTargetVersion(), null, request.getOperatorName(), 
                        "FAIL", e.getMessage());
            }
            return Result.error("应用升级失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取远程版本描述信息
     */
    private String getRemoteDescription(String pluginId, String targetVersion) {
        try {
            ApplicationDTO remoteVersionInfo = 
                    appStoreApiService.getApplicationVersionDetail(pluginId, targetVersion);
            if (remoteVersionInfo != null) {
                log.info("获取远程版本描述成功: pluginId={}, targetVersion={}, description={}",
                        pluginId, targetVersion, remoteVersionInfo.getDescription());
                return remoteVersionInfo.getDescription();
            }
        } catch (Exception e) {
            log.warn("获取远程版本描述失败: pluginId={}, targetVersion={}", pluginId, targetVersion, e);
        }
        return null;
    }
    
}










