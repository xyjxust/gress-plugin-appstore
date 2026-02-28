package com.keqi.gress.plugin.appstore.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginPackageInstallResult;
import  com.keqi.gress.common.plugin.PluginPackageLifecycle;
import  com.keqi.gress.common.plugin.PluginPackageUninstallResult;
import  com.keqi.gress.common.plugin.PluginPackageUpgradeResult;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.common.storage.FileStorageService;
import com.keqi.gress.plugin.appstore.dto.ApplicationUpgradeRequest;
import com.keqi.gress.plugin.appstore.service.install.DockerComposeInstallHook;
import com.keqi.gress.plugin.appstore.service.install.PluginInstallContext;
import com.keqi.gress.plugin.appstore.service.install.PluginInstallHookChain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 应用安装服务
 * 
 * 负责应用的安装、卸载和升级操作
 */
@Service
public class ApplicationInstallService {
    
    private static final Log log = LogFactory.get(ApplicationInstallService.class);
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginPackageLifecycle pluginPackageLifecycle;
    
    @Inject(source = Inject.BeanSource.SPRING)
    private FileStorageService fileStorageService;

    /**
     * 可扩展的安装/升级前置 Hook 链（默认包含 docker-compose 部署 Hook）。
     * 说明：不影响原有安装逻辑；若插件包内无 docker-compose.yml 则跳过。
     */
    private final PluginInstallHookChain hookChain =
            new PluginInstallHookChain(List.of(new DockerComposeInstallHook()));
    
    /**
     * 安装应用
     * 
     * @param fileUrl 文件存储URL
     * @return 安装结果（包含插件包信息）
     */
    public Result<PluginPackageInstallResult> installApplication(String fileUrl) {
        log.info("开始安装应用: fileUrl={}", fileUrl);
        
        try {
            // 1. 从文件存储下载到临时文件
            Path tempFile = downloadToTempFile(fileUrl);
            
            try {
                // 1.1 安装前置 Hook（例如：docker-compose 部署）
                PluginInstallContext ctx = new PluginInstallContext("system", null, null, null);
                Result<Void> hookResult = hookChain.beforeInstall(tempFile, ctx);
                if (!hookResult.isSuccess()) {
                    return Result.error(hookResult.getErrorMessage());
                }

                // 2. 调用插件生命周期管理器安装
                Result<PluginPackageInstallResult> result = pluginPackageLifecycle.install(tempFile);
                
                if (result.isSuccess()) {
                    PluginPackageInstallResult installResult = result.getData();
                    log.info("应用安装成功: packageId={}, version={}", 
                            installResult.getPackageId(), installResult.getVersion());
                } else {
                    log.error("应用安装失败: {}", result.getErrorMessage());
                }
                
                return result;
                
            } finally {
                // 3. 清理临时文件
                cleanupTempFile(tempFile);
            }
            
        } catch (Exception e) {
            log.error("安装应用失败: fileUrl={}", fileUrl, e);
            return Result.error("安装应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 卸载应用
     * 
     * @param packageId 插件包ID
     * @return 卸载结果
     */
    public Result<PluginPackageUninstallResult> uninstallApplication(String packageId) {
        log.info("开始卸载应用: packageId={}", packageId);
        
        try {
            Result<PluginPackageUninstallResult> result = pluginPackageLifecycle.uninstall(packageId);
            
            if (result.isSuccess()) {
                PluginPackageUninstallResult uninstallResult = result.getData();
                log.info("应用卸载成功: packageId={}, affectedPlugins={}, jarDeleted={}", 
                    packageId, 
                    uninstallResult.getAffectedPluginIds().size(),
                    uninstallResult.isJarFileDeleted());
            } else {
                log.error("应用卸载失败: packageId={}, error={}", packageId, result.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("卸载应用失败: packageId={}", packageId, e);
            return Result.error("卸载应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 升级应用
     * 
     * @param packageId 原插件包ID
     * @param newFileUrl 新文件存储URL
     * @return 升级结果
     */
    public Result<PluginPackageUpgradeResult> upgradeApplication(String packageId, String newFileUrl) {
        return upgradeApplication(packageId, newFileUrl, null, null);
    }
    
    /**
     * 升级或降级应用
     * 
     * @param packageId 原插件包ID
     * @param newFileUrl 新文件存储URL
     * @param operationType 操作类型（升级或降级），null表示升级
     * @return 升级结果
     */
    public Result<PluginPackageUpgradeResult> upgradeApplication(
            String packageId, String newFileUrl, ApplicationUpgradeRequest.OperationType operationType) {
        return upgradeApplication(packageId, newFileUrl, operationType, null);
    }
    
    /**
     * 升级或降级应用（带配置参数）
     * 
     * @param packageId 原插件包ID
     * @param newFileUrl 新文件存储URL
     * @param operationType 操作类型（升级或降级），null表示升级
     * @param extensionConfig 扩展配置（拍平格式的 JSON Map）
     * @return 升级结果
     */
    public Result<PluginPackageUpgradeResult> upgradeApplication(
            String packageId, 
            String newFileUrl, 
            ApplicationUpgradeRequest.OperationType operationType,
            java.util.Map<String, Object> extensionConfig) {
        String operationName = operationType == ApplicationUpgradeRequest.OperationType.ROLLBACK ? "降级" : "升级";
        log.info("开始{}应用: packageId={}, newFileUrl={}, hasConfig={}", 
                operationName, packageId, newFileUrl, extensionConfig != null && !extensionConfig.isEmpty());
        
        try {
            // 1. 从文件存储下载到临时文件
            Path tempFile = downloadToTempFile(newFileUrl);
            
            try {
                // 1.1 升级前置 Hook（例如：docker-compose 部署/更新）
                PluginInstallContext ctx = new PluginInstallContext("system", packageId, null, null);
                Result<Void> hookResult = hookChain.beforeUpgrade(tempFile, ctx);
                if (!hookResult.isSuccess()) {
                    return Result.error(hookResult.getErrorMessage());
                }

                // 2. 调用插件生命周期管理器升级（传入配置参数）
                Result<PluginPackageUpgradeResult> result = pluginPackageLifecycle.upgrade(
                        packageId, tempFile, extensionConfig);
                
                if (result.isSuccess()) {
                    PluginPackageUpgradeResult upgradeResult = result.getData();
                    log.info("应用{}成功: oldPackageId={}, newPackageId={}, oldVersion={}, newVersion={}", 
                        operationName,
                        upgradeResult.getPackageId(),
                        upgradeResult.getNewPackageId(),
                        upgradeResult.getOldVersion(),
                        upgradeResult.getNewVersion());
                } else {
                    log.error("应用{}失败: packageId={}, error={}", operationName, packageId, result.getErrorMessage());
                }
                
                return result;
                
            } finally {
                // 3. 清理临时文件
                cleanupTempFile(tempFile);
            }
            
        } catch (Exception e) {
            log.error("{}应用失败: packageId={}, newFileUrl={}", operationName, packageId, newFileUrl, e);
            return Result.error(operationName + "应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 启动应用
     * 
     * @param packageId 插件包ID
     * @return 启动结果
     */
    public Result< com.keqi.gress.common.plugin.PluginPackageStartResult> startApplication(String packageId) {
        log.info("开始启动应用: packageId={}", packageId);
        
        try {
            Result< com.keqi.gress.common.plugin.PluginPackageStartResult> result = 
                    pluginPackageLifecycle.start(packageId);
            
            if (result.isSuccess()) {
                log.info("应用启动成功: packageId={}",
                        packageId);
            } else {
                log.error("应用启动失败: packageId={}, error={}", packageId, result.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("启动应用失败: packageId={}", packageId, e);
            return Result.error("启动应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止应用
     * 
     * @param packageId 插件包ID
     * @return 停止结果
     */
    public Result< com.keqi.gress.common.plugin.PluginPackageStopResult> stopApplication(String packageId) {
        log.info("开始停止应用: packageId={}", packageId);
        
        try {
            Result< com.keqi.gress.common.plugin.PluginPackageStopResult> result = 
                    pluginPackageLifecycle.stop(packageId);
            
            if (result.isSuccess()) {
                log.info("应用停止成功: packageId={}",
                        packageId);
            } else {
                log.error("应用停止失败: packageId={}, error={}", packageId, result.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("停止应用失败: packageId={}", packageId, e);
            return Result.error("停止应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 重启应用
     * 
     * @param packageId 插件包ID
     * @return 重启结果
     */
    public Result< com.keqi.gress.common.plugin.PluginPackageRestartResult> restartApplication(String packageId) {
        log.info("开始重启应用: packageId={}", packageId);
        
        try {
            Result< com.keqi.gress.common.plugin.PluginPackageRestartResult> result = 
                    pluginPackageLifecycle.restart(packageId);
            
            if (result.isSuccess()) {
                log.info("应用重启成功: packageId={}",
                        packageId);
            } else {
                log.error("应用重启失败: packageId={}, error={}", packageId, result.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("重启应用失败: packageId={}", packageId, e);
            return Result.error("重启应用失败: " + e.getMessage());
        }
    }
    
    /**
     * 从文件存储下载到临时文件
     * 
     * @param fileUrl 文件存储URL
     * @return 临时文件路径
     * @throws Exception 下载失败
     */
    private Path downloadToTempFile(String fileUrl) throws Exception {
        log.debug("从文件存储下载: {}", fileUrl);
        
        // 创建临时文件
        Path tempFile =   Files.createTempFile("plugin-", ".jar");
        
        // 从文件存储下载
        fileStorageService.download(fileUrl)
            .toStream(inputStream -> {
                try {
                    Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    log.debug("文件下载到临时路径: {}", tempFile);
                } catch (Exception e) {
                    throw new RuntimeException("复制文件失败", e);
                }
            })
            .onError(e -> {
                log.error("从文件存储下载失败: {}", fileUrl, e);
                throw new RuntimeException("从文件存储下载失败", e);
            })
            .executeVoid();
        
        return tempFile;
    }
    
    /**
     * 清理临时文件
     * 
     * @param tempFile 临时文件路径
     */
    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null && Files.exists(tempFile)) {
            try {
                Files.delete(tempFile);
                log.debug("临时文件已删除: {}", tempFile);
            } catch (Exception e) {
                log.warn("删除临时文件失败: {}", tempFile, e);
            }
        }
    }
}
