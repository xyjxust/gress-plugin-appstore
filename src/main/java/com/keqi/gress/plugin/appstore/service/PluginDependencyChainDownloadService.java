package com.keqi.gress.plugin.appstore.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 插件依赖链下载服务
 * 
 * 负责按照正确的顺序下载插件及其所有依赖。
 * 
 * @author Gress Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class PluginDependencyChainDownloadService {
    
   // private static final Log log = LogFactory.get(PluginDependencyChainDownloadService.class);
    
    @Inject
    private PluginDependencyChainResolver chainResolver;
    
    @Inject
    private AppStoreApiService appStoreApiService;
    
    /**
     * 下载插件及其所有依赖
     * 
     * @param pluginId 插件ID
     * @param version 版本号（可选）
     * @return 下载结果
     */
    public DependencyChainDownloadResult downloadWithDependencies(String pluginId, String version) {
        log.info("========== 开始下载插件依赖链 ==========");
        log.info("目标插件: {}, 版本: {}", pluginId, version);
        
        DependencyChainDownloadResult result = new DependencyChainDownloadResult();
        result.setRootPluginId(pluginId);
        result.setRootVersion(version);
        
        try {
            // ========== 阶段1: 解析依赖链 ==========
            log.info("阶段1: 解析依赖链...");
            PluginDependencyChainResolver.DependencyChain chain = 
                chainResolver.resolveDependencyChain(pluginId, version);
            result.setDependencyChain(chain);
            
            log.info("依赖链解析完成:");
            log.info("  - 根插件: {}", chain.getRootPluginId());
            log.info("  - 总依赖数: {}", chain.getAllDependencies().size());
            log.info("  - 安装顺序: {}", chain.getInstallOrder());
            
            // ========== 阶段2: 检测循环依赖 ==========
            log.info("阶段2: 检测循环依赖...");
            if (chainResolver.hasCircularDependency(chain)) {
                String errorMsg = "检测到循环依赖，无法下载";
                log.error(errorMsg);
                result.setSuccess(false);
                result.setErrorMessage(errorMsg);
                return result;
            }
            log.info("循环依赖检测通过");
            
            // ========== 阶段3: 按顺序下载 ==========
            log.info("阶段3: 按顺序下载插件...");
            List<String> installOrder = chain.getInstallOrder();
            Map<String, String> downloadUrls = new LinkedHashMap<>();
            
            int total = installOrder.size();
            int current = 0;
            
            for (String key : installOrder) {
                current++;
                
                // 解析 pluginId 和 version
                String[] parts = key.split("@");
                String depPluginId = parts[0];
                String depVersion = parts.length > 1 && !"latest".equals(parts[1]) ? parts[1] : null;
                
                PluginDependencyChainResolver.DependencyNode node = chain.getAllDependencies().get(key);
                if (node == null) {
                    log.warn("依赖节点不存在: {}", key);
                    continue;
                }
                
                log.info("下载进度: [{}/{}] 插件: {}@{}", 
                    current, total, depPluginId, depVersion != null ? depVersion : "latest");
                
                try {
                    // 更新下载状态
                    node.setDownloadStatus(
                        PluginDependencyChainResolver.DependencyDownloadStatus.DOWNLOADING);
                    
                    // 下载插件
                    long startTime = System.currentTimeMillis();
                    String fileUrl = downloadPlugin(depPluginId, depVersion);
                    long duration = System.currentTimeMillis() - startTime;
                    
                    downloadUrls.put(depPluginId, fileUrl);
                    node.setDownloadStatus(
                        PluginDependencyChainResolver.DependencyDownloadStatus.SUCCESS);
                    
                    log.info("  ✓ 下载成功: {} (耗时: {}ms)", fileUrl, duration);
                    
                } catch (Exception e) {
                    log.error("  ✗ 下载失败: {}", depPluginId, e);
                    node.setDownloadStatus(
                        PluginDependencyChainResolver.DependencyDownloadStatus.FAILED);
                    node.setDownloadError(e.getMessage());
                    
                    // 如果是必需依赖，抛出异常
                    if (!node.isOptional()) {
                        String errorMsg = String.format("必需依赖下载失败: %s, 原因: %s", 
                            depPluginId, e.getMessage());
                        throw new RuntimeException(errorMsg, e);
                    } else {
                        log.warn("  可选依赖下载失败，继续处理其他插件");
                    }
                }
            }
            
            result.setDownloadUrls(downloadUrls);
            result.setSuccess(true);
            
            log.info("========== 下载完成 ==========");
            log.info("成功下载: {} 个插件", downloadUrls.size());
            log.info("下载的插件: {}", downloadUrls.keySet());
            
        } catch (Exception e) {
            log.error("========== 下载失败 ==========", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            
            // 清理已下载的文件
            cleanupDownloadedFiles(result);
        }
        
        return result;
    }
    
    /**
     * 下载单个插件
     * 
     * @param pluginId 插件ID
     * @param version 版本号（可选）
     * @return 文件URL
     */
    private String downloadPlugin(String pluginId, String version) {
        log.debug("开始下载插件: pluginId={}, version={}", pluginId, version);
        
        String fileUrl;
        if (version != null && !version.isEmpty()) {
            fileUrl = appStoreApiService.downloadApplication(pluginId, version);
        } else {
            fileUrl = appStoreApiService.downloadApplication(pluginId);
        }
        
        log.debug("插件下载完成: pluginId={}, fileUrl={}", pluginId, fileUrl);
        return fileUrl;
    }
    
    /**
     * 清理已下载的文件
     * 
     * @param result 下载结果
     */
    private void cleanupDownloadedFiles(DependencyChainDownloadResult result) {
        // TODO: 实现文件清理逻辑
        // 如果下载失败，可能需要清理已下载的文件
        log.warn("清理已下载文件功能待实现");
    }
    
    /**
     * 依赖链下载结果
     */
    @Data
    public static class DependencyChainDownloadResult {
        /** 根插件ID */
        private String rootPluginId;
        
        /** 根插件版本 */
        private String rootVersion;
        
        /** 是否成功 */
        private boolean success;
        
        /** 错误信息 */
        private String errorMessage;
        
        /** 下载的文件URL映射（pluginId -> fileUrl） */
        private Map<String, String> downloadUrls = new LinkedHashMap<>();
        
        /** 依赖链信息 */
        private PluginDependencyChainResolver.DependencyChain dependencyChain;
    }
}










