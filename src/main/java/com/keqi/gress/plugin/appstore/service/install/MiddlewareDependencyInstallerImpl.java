package com.keqi.gress.plugin.appstore.service.install;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.common.storage.FileStorageService;

import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import com.keqi.gress.plugin.appstore.service.AppStoreApiService;
import com.keqi.gress.plugin.appstore.service.MiddlewareManagementService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 中间件依赖安装器实现
 * 
 * 负责从应用商店下载并安装中间件依赖
 */
@Service
public class MiddlewareDependencyInstallerImpl implements MiddlewareDependencyInstaller {
    
    private static final Log log = LogFactory.get(MiddlewareDependencyInstallerImpl.class);
    
    @Inject
    private AppStoreApiService appStoreApiService;
    
    @Inject
    private MiddlewareManagementService middlewareManagementService;
    
    @Inject(source = Inject.BeanSource.SPRING)
    private FileStorageService fileStorageService;
    
    @Override
    public Result<MiddlewareManagementService.MiddlewareInstallResult> install(
            String pluginId, String versionRange, String operator) {
        
        Path tempFile = null;
        try {
            log.info("开始从应用商店安装中间件依赖: pluginId={}, versionRange={}, operator={}", 
                pluginId, versionRange, operator);
            
            // 1. 检查中间件是否已安装（包括手动添加的服务）
            if (middlewareManagementService.getMiddleware(pluginId).isPresent()) {
                log.warn("中间件已安装，跳过: pluginId={}", pluginId);
                return Result.error("中间件已安装: " + pluginId);
            }
            
            // 1.1 检查服务表中是否存在该服务（手动添加的服务）
            if (middlewareManagementService.getMiddlewareService(pluginId).isPresent()) {
                log.warn("服务已手动注册，跳过安装: pluginId={}", pluginId);
                return Result.error("服务已手动注册，无需安装: " + pluginId);
            }
            
            // 2. 从应用商店获取中间件详情
            String version = resolveVersion(pluginId, versionRange);
            ApplicationDTO middlewareDetail = appStoreApiService.getApplicationVersionDetail(pluginId, version);
            
            if (middlewareDetail == null) {
                String msg = "获取中间件详情失败: " + pluginId;
                log.error(msg);
                return Result.error(msg);
            }
            
            // 3. 从应用商店下载中间件包
            String fileUrl = appStoreApiService.downloadApplication(pluginId, version);
            if (fileUrl == null || fileUrl.isEmpty()) {
                return Result.error("从应用商店下载中间件包失败");
            }
            
            log.info("中间件包下载成功: pluginId={}, fileUrl={}", pluginId, fileUrl);
            
            // 4. 下载到临时文件
            tempFile = downloadToTempFile(fileUrl);
            
            // 5. 调用中间件管理服务安装
            Result<MiddlewareManagementService.MiddlewareInstallResult> installResult = 
                middlewareManagementService.installMiddleware(tempFile, operator);
            
            if (!installResult.isSuccess()) {
                return installResult;
            }
            
            log.info("中间件依赖安装成功: pluginId={}, version={}", pluginId, version);
            return installResult;
            
        } catch (Exception e) {
            log.error("从应用商店安装中间件依赖失败: pluginId={}", pluginId, e);
            return Result.error("安装中间件依赖失败: " + e.getMessage());
        } finally {
            // 清理临时文件
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}", tempFile, e);
                }
            }
        }
    }
    
    /**
     * 解析版本号（从版本范围或应用商店获取最新版本）
     */
    private String resolveVersion(String pluginId, String versionRange) {
        if (versionRange != null && !versionRange.isEmpty() && !versionRange.startsWith(">=")) {
            // 精确版本
            return versionRange;
        }
        // 从应用商店获取最新版本
        ApplicationDTO detail = appStoreApiService.getApplicationVersionDetail(pluginId, null);
        return detail != null ? detail.getPluginVersion() : null;
    }
    
    /**
     * 下载文件到临时位置
     */
    private Path downloadToTempFile(String fileUrl) throws Exception {
        Path tempFile = Files.createTempFile("middleware-download-", ".jar");
        
        fileStorageService.download(fileUrl)
            .toStream(inputStream -> {
                try {
                    Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("保存临时文件失败: " + e.getMessage(), e);
                }
            })
            .onError(e -> {
                throw new RuntimeException("下载文件失败: " + e.getMessage(), e);
            })
            .executeVoid();
        
        return tempFile;
    }
}
