package com.keqi.gress.plugin.appstore.service.orchestrator;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginPackageInstallResult;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import com.keqi.gress.plugin.appstore.service.ApplicationInstallService;
import com.keqi.gress.plugin.appstore.service.install.DependencyResolutionService;
import com.keqi.gress.plugin.appstore.service.persistence.ApplicationPersistenceService;
import com.keqi.gress.plugin.appstore.service.logging.ApplicationOperationLogger;
import lombok.extern.slf4j.Slf4j;

/**
 * 安装编排器
 * 
 * 职责：
 * - 协调完整的安装流程
 * - 遵循单一职责原则（SRP）：只负责流程编排，不处理具体业务逻辑
 * - 遵循开闭原则（OCP）：通过依赖注入的组件扩展功能
 * - 遵循依赖倒置原则（DIP）：依赖抽象接口而非具体实现
 * 
 * 安装流程：
 * 1. 版本检查（是否已安装）
 * 2. 依赖安装（递归安装所有依赖）
 * 3. 下载文件
 * 4. 执行安装
 * 5. 数据持久化
 * 6. 日志记录
 * 
 * @author Gress Team
 */
@Service
@Slf4j
public class InstallOrchestrator {
    
    //private static final Log log = LogFactory.get(InstallOrchestrator.class);
    
    @Inject
    private DependencyResolutionService dependencyResolutionService;
    
    @Inject
    private ApplicationInstallService applicationInstallService;
    
    @Inject
    private ApplicationPersistenceService persistenceService;
    
    @Inject
    private ApplicationOperationLogger operationLogger;
    
    /**
     * 从URL安装应用（含依赖检查与安装）
     * 
     * @param fileUrl 文件URL
     * @param operatorName 操作员名称
     * @return 安装结果
     */
    public Result<PluginPackageInstallResult> installFromUrl(String fileUrl, String operatorName) {
        long startTime = System.currentTimeMillis();
        SysApplication tempApp = null;
        
        try {
            log.info("开始从URL安装应用: fileUrl={}, operator={}", fileUrl, operatorName);
            
            // 1. 执行安装（通过 ApplicationInstallService）
            Result<PluginPackageInstallResult> installResult = 
                    applicationInstallService.installApplication(fileUrl);
            
            if (!installResult.isSuccess()) {
                log.error("安装应用失败: {}", installResult.getErrorMessage());
                tempApp = createTempApp("unknown", "unknown");
                operationLogger.logFailure(tempApp, "INSTALL", "安装应用", 
                        "admin", operatorName, "安装失败: " + installResult.getErrorMessage(), 
                        startTime);
                return installResult;
            }
            
            PluginPackageInstallResult installInfo = installResult.getData();
            log.info("应用安装成功: packageId={}, version={}", 
                    installInfo.getPackageId(), installInfo.getVersion());
            
            // 2. 保存应用信息到数据库
            SysApplication application = null;
            try {
                application = persistenceService.saveApplication(installInfo, operatorName);
            } catch (Exception e) {
                log.warn("保存应用信息到数据库失败，但插件已安装成功: packageId={}", 
                        installInfo.getPackageId(), e);
            }
            
            // 3. 从应用商店获取表权限并保存
            try {
                persistenceService.saveTablePermissionsFromAppStore(
                        installInfo.getPackageId(), operatorName);
            } catch (Exception e) {
                log.warn("获取并保存表权限失败，但插件已安装成功: packageId={}", 
                        installInfo.getPackageId(), e);
            }
            
            // 4. 记录成功日志
            if (application == null) {
                application = createTempApp(installInfo.getPackageId(), installInfo.getPackageId());
            }
            operationLogger.logSuccess(application, "INSTALL", "安装应用", 
                    "admin", operatorName, 
                    String.format("安装成功，版本: %s", installInfo.getVersion()), 
                    startTime);
            
            return installResult;
            
        } catch (Exception e) {
            log.error("从URL安装应用失败: fileUrl={}", fileUrl, e);
            if (tempApp != null) {
                operationLogger.logFailure(tempApp, "INSTALL", "安装应用", 
                        "admin", operatorName, "异常: " + e.getMessage(), startTime);
            }
            return Result.error("安装应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 从应用商店安装应用（支持依赖链下载）
     * 
     * @param pluginId 插件ID
     * @param version 版本号（可选）
     * @param operatorName 操作员名称
     * @return 安装结果
     */
    public Result<PluginPackageInstallResult> installFromAppStore(
            String pluginId, String version, String operatorName) {
        long startTime = System.currentTimeMillis();
        SysApplication tempApp = createTempApp(pluginId, pluginId);
        
        try {
            log.info("开始从应用商店安装应用（含依赖）: pluginId={}, version={}, operator={}", 
                    pluginId, version, operatorName);
            
            // 通过依赖安装器执行完整的依赖链安装（含下载、安装、持久化）
            Result<PluginPackageInstallResult> result = 
                    dependencyResolutionService.installWithDependencies(pluginId, version, operatorName);
            
            if (!result.isSuccess()) {
                operationLogger.logFailure(tempApp, "INSTALL", "安装应用", 
                        "admin", operatorName, result.getErrorMessage(), startTime);
                return result;
            }
            
            // 记录成功日志
            PluginPackageInstallResult installInfo = result.getData();
            SysApplication application = persistenceService.findByPluginId(pluginId);
            if (application == null) {
                application = tempApp;
            }
            
            operationLogger.logSuccess(application, "INSTALL", "安装应用", 
                    "admin", operatorName, 
                    String.format("安装成功（含依赖），版本: %s", installInfo.getVersion()), 
                    startTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("从应用商店安装应用失败: pluginId={}, version={}", pluginId, version, e);
            operationLogger.logFailure(tempApp, "INSTALL", "安装应用", 
                    "admin", operatorName, "异常: " + e.getMessage(), startTime);
            return Result.error("安装应用失败: " + e.getMessage());
        }
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
}










