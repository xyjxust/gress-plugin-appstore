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
import com.keqi.gress.plugin.appstore.service.security.JarSignatureVerifier;
import com.keqi.gress.plugin.appstore.service.install.DockerComposeInstallHook;
import com.keqi.gress.plugin.appstore.service.install.PluginInstallContext;
import com.keqi.gress.plugin.appstore.service.install.PluginInstallHookChain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
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

    @Inject
    private com.keqi.gress.plugin.appstore.config.AppStoreConfig appStoreConfig;

    @Inject(source = Inject.BeanSource.PLUGIN)
    private AppStoreApiService appStoreApiService;

    private final JarSignatureVerifier jarSignatureVerifier = new JarSignatureVerifier();

    private static final long TRUSTED_ROOTS_REFRESH_INTERVAL_MS = 5L * 60L * 1000L;
    private volatile long trustedRootsLastFetchTs = 0L;

    private void refreshTrustedRootsIfNeeded() {
        try {
            if (appStoreConfig == null || appStoreConfig.getSecurity() == null) {
                return;
            }
            if (!Boolean.TRUE.equals(appStoreConfig.getSecurity().getVerifySignature())) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - trustedRootsLastFetchTs < TRUSTED_ROOTS_REFRESH_INTERVAL_MS) {
                return;
            }
            if (appStoreApiService == null) {
                return;
            }

            var roots = appStoreApiService.getTrustedRoots();
            if (roots == null || roots.isEmpty()) {
                return;
            }

            java.util.List<String> pemList = roots.stream()
                    .map(AppStoreApiService.TrustedRootDTO::getPublicKeyPem)
                    .filter(pem -> pem != null && !pem.trim().isEmpty())
                    .toList();

            jarSignatureVerifier.refreshTrustedRootsFromPems(pemList);
            trustedRootsLastFetchTs = now;
        } catch (Exception ignore) {
            // 保持安装流程健壮：即便拉取 trusted roots 失败，也可以依赖 JarSignatureVerifier 内置 pinned key。
        }
    }

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
        return installApplication(fileUrl, null);
    }

    /**
     * 安装应用（带期望 SHA-256 校验）
     *
     * @param fileUrl 文件存储URL
     * @param expectedSha256 期望 SHA-256（来自 Marketplace），为空则跳过
     */
    public Result<PluginPackageInstallResult> installApplication(String fileUrl, String expectedSha256) {
        boolean verifySignatureEnabled = appStoreConfig != null
                && appStoreConfig.getSecurity() != null
                && Boolean.TRUE.equals(appStoreConfig.getSecurity().getVerifySignature());
        boolean checkSourceEnabled = appStoreConfig != null
                && appStoreConfig.getSecurity() != null
                && Boolean.TRUE.equals(appStoreConfig.getSecurity().getCheckSource());
        String allowedSourcesRaw = (appStoreConfig != null
                && appStoreConfig.getSecurity() != null)
                ? appStoreConfig.getSecurity().getAllowedSources()
                : null;

        log.info("开始安装应用: fileUrl={}, verifySignature={}, checkSource={}, allowedSources={}, expectedSha256Present={}",
                fileUrl,
                verifySignatureEnabled,
                checkSourceEnabled,
                allowedSourcesRaw,
                expectedSha256 != null && !expectedSha256.trim().isEmpty());
        log.info("ids: this={}, cfg={}, security={}",
                System.identityHashCode(this),
                System.identityHashCode(appStoreConfig),
                appStoreConfig == null ? null : System.identityHashCode(appStoreConfig.getSecurity()));
        try {
            // 1. 从文件存储下载到临时文件
            Path tempFile = downloadToTempFile(fileUrl);
            
            try {
                // 0.1 （可选）安装前 SHA-256 校验（下载后、安装前）
                String sha256Before = sha256Hex(tempFile);
                if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
                    String expected = expectedSha256.trim();
                    if (!expected.equalsIgnoreCase(sha256Before)) {
                        return Result.error("SHA-256 校验失败（安装前）: expected=" + expected + ", actual=" + sha256Before);
                    }
                }

                // 0. （可选）安装前 JAR 签名验签：失败则禁止安装
                if (appStoreConfig != null
                        && appStoreConfig.getSecurity() != null
                        && Boolean.TRUE.equals(appStoreConfig.getSecurity().getVerifySignature())) {
                    refreshTrustedRootsIfNeeded();
                    Result<Void> verifyResult = jarSignatureVerifier.verify(tempFile);
                    if (!verifyResult.isSuccess()) {
                        return Result.error(verifyResult.getErrorMessage());
                    }
                }

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

                    // 记录 SHA-256 校验信息（供 persistence 写入 extension_config）
                    if (installResult != null) {
                        try {
                            installResult.addMetadata("sha256Expected", expectedSha256 == null ? "" : expectedSha256.trim());
                            installResult.addMetadata("sha256Before", sha256Before);
                        } catch (Exception ignore) {
                        }
                    }

                    // 3.2 （可选）安装后再次验签：确保落地文件未被篡改
                    if (appStoreConfig != null
                            && appStoreConfig.getSecurity() != null
                            && Boolean.TRUE.equals(appStoreConfig.getSecurity().getVerifySignature())
                            && installResult != null
                            && installResult.getPluginPath() != null) {
                        Path installedJar = Path.of(installResult.getPluginPath());
                        refreshTrustedRootsIfNeeded();
                        Result<Void> postVerify = jarSignatureVerifier.verify(installedJar);
                        if (!postVerify.isSuccess()) {
                            // 尽量回滚：卸载刚安装的插件包
                            if (installResult.getPackageId() != null) {
                                try {
                                    pluginPackageLifecycle.uninstall(installResult.getPackageId());
                                } catch (Exception ignore) {
                                    // ignore rollback failure; original verification error is more relevant
                                }
                            }
                            return Result.error(postVerify.getErrorMessage());
                        }
                    }

                    // 3.3 （可选）安装后 SHA-256 二次校验：确保落地文件未被篡改
                    if (expectedSha256 != null && !expectedSha256.trim().isEmpty()
                            && installResult != null
                            && installResult.getPluginPath() != null) {
                        Path installedJar = Path.of(installResult.getPluginPath());
                        String sha256After = sha256Hex(installedJar);
                        try {
                            installResult.addMetadata("sha256After", sha256After);
                        } catch (Exception ignore) {
                        }
                        if (!expectedSha256.trim().equalsIgnoreCase(sha256After)) {
                            if (installResult.getPackageId() != null) {
                                try {
                                    pluginPackageLifecycle.uninstall(installResult.getPackageId());
                                } catch (Exception ignore) {
                                }
                            }
                            return Result.error("SHA-256 校验失败（安装后）: expected=" + expectedSha256.trim() + ", actual=" + sha256After);
                        }
                    }

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

    private static String sha256Hex(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return java.util.HexFormat.of().formatHex(md.digest());
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
                // 0. （可选）升级前 JAR 签名验签：失败则禁止升级
                if (appStoreConfig != null
                        && appStoreConfig.getSecurity() != null
                        && Boolean.TRUE.equals(appStoreConfig.getSecurity().getVerifySignature())) {
                    refreshTrustedRootsIfNeeded();
                    Result<Void> verifyResult = jarSignatureVerifier.verify(tempFile);
                    if (!verifyResult.isSuccess()) {
                        return Result.error(verifyResult.getErrorMessage());
                    }
                }

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
