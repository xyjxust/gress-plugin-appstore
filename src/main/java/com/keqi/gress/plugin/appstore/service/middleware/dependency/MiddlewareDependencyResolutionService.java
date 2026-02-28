package com.keqi.gress.plugin.appstore.service.middleware.dependency;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginMetadataParser;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;

import com.keqi.gress.plugin.appstore.service.MiddlewareManagementService;
import com.keqi.gress.plugin.appstore.service.install.MiddlewareDependencyInstaller;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.*;

/**
 * 中间件依赖解析与安装服务
 * 
 * 职责：
 * - 解析 plugin.dependencies 中的中间件依赖
 * - 检查依赖是否已安装（中间件注册表 + 服务表）
 * - 从应用商店下载并安装缺失的依赖
 * - 递归处理依赖链
 * - 安装成功后注册服务到服务表
 * - 支持智能回滚
 */
@Service
@Slf4j
public class MiddlewareDependencyResolutionService {
    
   // private static final Log log = LogFactory.get(MiddlewareDependencyResolutionService.class);
    @Inject
    private MiddlewareManagementService middlewareManagementService;
    
    // 可选依赖：中间件依赖安装器（来自 appstore 插件）
    @Inject
    private MiddlewareDependencyInstaller middlewareDependencyInstaller;  // 使用 Object 避免编译时依赖
    

    /**
     * 获取 MiddlewareDependencyInstaller（通过反射调用）
     */
    private Object getMiddlewareDependencyInstaller() {
        if (middlewareDependencyInstaller == null) {
            throw new IllegalStateException("MiddlewareDependencyInstaller 未注入，无法从应用商店下载中间件");
        }
        return middlewareDependencyInstaller;
    }
    
    /**
     * 确保中间件依赖已安装（递归安装缺失的依赖）
     * 
     * @param middlewarePackage 中间件插件包路径
     * @param operator 操作人
     * @return 已解析的依赖服务信息
     */
    public Result<Map<String, MiddlewareManagementService.MiddlewareServiceInfo>> 
            ensureDependenciesInstalled(Path middlewarePackage, String operator) {
        
        List<String> newlyInstalledDeps = new ArrayList<>();  // 记录本次新安装的依赖
        
        try {
            // 1. 解析插件元数据，提取依赖
            PluginMetadataParser.PluginMetadata metadata = 
                PluginMetadataParser.parseFromJar(middlewarePackage);
            String dependenciesStr = metadata.getDependencies();
            
            if (dependenciesStr == null || dependenciesStr.trim().isEmpty()) {
                log.info("中间件无依赖，跳过依赖检查: pluginId={}", metadata.getPluginId());
                return Result.success(Map.of());
            }
            
            // 2. 解析依赖列表
            List<PluginMetadataParser.DependencyInfo> dependencies = 
                PluginMetadataParser.parseDependencies(dependenciesStr);
            
            if (dependencies.isEmpty()) {
                return Result.success(Map.of());
            }
            
            // 3. 遍历依赖列表，逐个检查并安装
            Map<String, MiddlewareManagementService.MiddlewareServiceInfo> resolvedServices = 
                new HashMap<>();
            
            for (PluginMetadataParser.DependencyInfo dep : dependencies) {
                String depPluginId = dep.getPluginId();
                if (depPluginId == null || depPluginId.isEmpty()) {
                    log.warn("依赖插件ID为空，跳过");
                    continue;
                }
                
                // 3.1 检查依赖的中间件插件是否已安装
                if (isMiddlewareInstalled(depPluginId)) {
                    log.info("依赖的中间件插件已安装，检查服务注册: pluginId={}", depPluginId);
                    
                    // 检查服务是否已注册到服务表
                    Optional<MiddlewareManagementService.MiddlewareServiceInfo> serviceInfo = 
                        middlewareManagementService.getMiddlewareService(depPluginId);
                    
                    if (serviceInfo.isPresent()) {
                        // 服务已注册，复用
                        resolvedServices.put(depPluginId, serviceInfo.get());
                        middlewareManagementService.incrementServiceReference(depPluginId, metadata.getPluginId());
                        log.info("复用已安装的服务: serviceId={}", depPluginId);
                        continue;
                    } else {
                        log.warn("中间件插件已安装但服务未注册，尝试重新注册: pluginId={}", depPluginId);
                        // TODO: 可以尝试从已安装的中间件中提取服务信息并注册
                    }
                }
                
                // 3.2 依赖未安装，从应用商店下载并安装
                log.info("开始安装依赖中间件（含其依赖链）: pluginId={}", depPluginId);
                
                // 调用 appstore 插件中的安装器
                Result<MiddlewareManagementService.MiddlewareInstallResult> depResult = 
                    invokeInstallerInstall(depPluginId, dep.getVersionRange(), operator);
                
                if (!depResult.isSuccess()) {
                    String msg = "依赖中间件安装失败: " + depPluginId + " - " + depResult.getErrorMessage();
                    log.error(msg);
                    
                    // 安装失败，执行智能回滚
                    log.warn("检测到依赖安装失败，开始回滚本次新安装的 {} 个依赖...", newlyInstalledDeps.size());
                    rollbackNewlyInstalled(newlyInstalledDeps, operator);
                    
                    return Result.error(msg + "（已回滚本次安装的依赖）");
                }
                
                // 3.3 安装成功，从服务表获取服务信息
                Optional<MiddlewareManagementService.MiddlewareServiceInfo> serviceInfo = 
                    middlewareManagementService.getMiddlewareService(depPluginId);
                
                if (serviceInfo.isPresent()) {
                    resolvedServices.put(depPluginId, serviceInfo.get());
                    newlyInstalledDeps.add(depPluginId);
                    log.info("依赖安装成功并已记录: pluginId={}, 当前新安装数量={}", 
                        depPluginId, newlyInstalledDeps.size());
                } else {
                    log.warn("依赖安装成功但服务未注册: pluginId={}", depPluginId);
                }
            }
            
            log.info("所有依赖检查完成，本次新安装 {} 个依赖", newlyInstalledDeps.size());
            return Result.success(resolvedServices);
            
        } catch (Exception e) {
            String msg = "检查并安装依赖失败: error=" + e.getMessage();
            log.error(msg, e);
            
            // 异常时也要回滚
            log.warn("检测到异常，开始回滚本次新安装的 {} 个依赖...", newlyInstalledDeps.size());
            rollbackNewlyInstalled(newlyInstalledDeps, operator);
            
            return Result.error(msg + "（已回滚本次安装的依赖）");
        }
    }
    
    /**
     * 调用中间件依赖安装器安装中间件
     */
    @SuppressWarnings("unchecked")
    private Result<MiddlewareManagementService.MiddlewareInstallResult> 
            invokeInstallerInstall(String pluginId, String versionRange, String operator) {
        try {
            Object installer = getMiddlewareDependencyInstaller();
            java.lang.reflect.Method method = installer.getClass().getMethod(
                "install", String.class, String.class, String.class);
            return (Result<MiddlewareManagementService.MiddlewareInstallResult>) 
                method.invoke(installer, pluginId, versionRange, operator);
        } catch (Exception e) {
            log.error("调用 MiddlewareDependencyInstaller.install 失败", e);
            throw new RuntimeException("调用中间件依赖安装器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查中间件是否已安装（包括手动添加的服务）
     */
    private boolean isMiddlewareInstalled(String pluginId) {
        // 检查中间件信息表
        if (middlewareManagementService.getMiddleware(pluginId).isPresent()) {
            return true;
        }
        // 检查服务表（手动添加的服务）
        if (middlewareManagementService.getMiddlewareService(pluginId).isPresent()) {
            return true;
        }
        return false;
    }
    
    
    /**
     * 回滚本次新安装的依赖
     */
    private void rollbackNewlyInstalled(List<String> newlyInstalledDeps, String operator) {
        if (newlyInstalledDeps == null || newlyInstalledDeps.isEmpty()) {
            return;
        }
        
        log.info("开始智能回滚，共 {} 个依赖需要卸载", newlyInstalledDeps.size());
        
        // 反向遍历（后装的先卸）
        Collections.reverse(newlyInstalledDeps);
        
        for (String pluginId : newlyInstalledDeps) {
            try {
                log.info("回滚依赖中间件: pluginId={}", pluginId);
                Result<Void> uninstallResult = 
                    middlewareManagementService.uninstallMiddleware(pluginId, operator);
                
                if (!uninstallResult.isSuccess()) {
                    log.warn("回滚时卸载中间件失败: pluginId={}, error={}", 
                        pluginId, uninstallResult.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("回滚依赖中间件时发生异常: pluginId={}", pluginId, e);
            }
        }
    }
}
