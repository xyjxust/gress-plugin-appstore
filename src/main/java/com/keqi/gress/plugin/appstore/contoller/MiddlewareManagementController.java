package com.keqi.gress.plugin.appstore.contoller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;

import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import com.keqi.gress.plugin.appstore.dto.PageResult;
import com.keqi.gress.plugin.appstore.service.AppStoreApiService;
import com.keqi.gress.plugin.appstore.service.MiddlewareManagementService;
import com.keqi.gress.plugin.appstore.service.middleware.ConnectionInfoFormatter;
import com.keqi.gress.plugin.appstore.service.middleware.MiddlewareInstallSsePublisher;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 中间件管理控制器（AppStore 管理页面使用）
 *
 * 说明：
 * - 按“方案 A”，底层能力由主应用 Spring Bean 提供
 * - AppStore 只提供管理入口与页面（REST API）
 */
@Service
@RestController
@RequestMapping("/middlewares")
@Valid
@Slf4j
public class MiddlewareManagementController {

  //  private static final Log log = LogFactory.get(MiddlewareManagementController.class);

    @Inject
    private MiddlewareManagementService middlewareManagementService;
    
    @Inject(source = Inject.BeanSource.PLUGIN)
    private AppStoreApiService appStoreApiService;
    
    @Inject(source = Inject.BeanSource.SPRING)
    private  com.keqi.gress.common.storage.FileStorageService fileStorageService;

    @Inject
    private MiddlewareInstallSsePublisher installSsePublisher;

    /**
     * 列出已注册中间件
     */
    @GetMapping
    public Result<List<MiddlewareManagementService.MiddlewareInfo>> list() {
        try {
            return Result.success(middlewareManagementService.listMiddlewares());
        } catch (Exception e) {
            log.error("查询中间件列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/{middlewareId}/health")
    public Result<MiddlewareManagementService.HealthCheckResult> health(@PathVariable String middlewareId) {
        try {
            return Result.success(middlewareManagementService.checkHealth(middlewareId));
        } catch (Exception e) {
            log.warn("健康检查失败: middlewareId={}, err={}", middlewareId, e.getMessage());
            return Result.success(MiddlewareManagementService.HealthCheckResult.unhealthy(e.getMessage()));
        }
    }

    /**
     * 上传并安装中间件插件包
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Result<MiddlewareManagementService.MiddlewareInstallResult> uploadAndInstall(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "admin") String operatorName) {

        log.info("上传并安装中间件插件包: fileName={}, size={}, operator={}",
                file.getOriginalFilename(), file.getSize(), operatorName);

        Path tmp = null;
        try {
            tmp = Files.createTempFile("middleware-", ".jar");
            Files.copy(file.getInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
            return middlewareManagementService.installMiddleware(tmp, operatorName);
        } catch (Exception e) {
            log.error("上传安装中间件失败", e);
            return Result.error("安装失败: " + e.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 卸载中间件（管理员操作）
     *
     * 为了避免 DELETE 带 body 的兼容问题，这里用 POST。
     */
    @PostMapping("/{middlewareId}/uninstall")
    public Result<Void> uninstall(
            @PathVariable String middlewareId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String operatorName = body != null ? body.getOrDefault("operatorName", "admin") : "admin";
        log.info("卸载中间件: middlewareId={}, operator={}", middlewareId, operatorName);
        return middlewareManagementService.uninstallMiddleware(middlewareId, operatorName);
    }
    
    /**
     * 获取远程中间件列表（分页）
     * 
     * 从远程应用商店获取 MIDDLEWARE 类型的插件列表，并检查本地安装状态
     * 
     * @param page 页码，默认 1
     * @param size 每页大小，默认 20
     * @param keyword 关键词搜索（可选）
     * @return 分页的远程中间件列表，包含安装状态
     */
    @GetMapping("/remote")
    public Result<PageResult<ApplicationDTO>> queryRemoteMiddlewares(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        
        log.info("查询远程中间件列表: page={}, size={}, keyword={}", page, size, keyword);
        
        try {
            // 从远程应用商店获取 MIDDLEWARE 类型的插件列表
            PageResult<ApplicationDTO> remotePageResult = appStoreApiService.getApplicationsPage(
                page, size, keyword, "MIDDLEWARE");
            
            if (remotePageResult == null || remotePageResult.getItems() == null) {
                return Result.success(createEmptyPageResult(page, size));
            }
            
            // 获取所有本地已安装的中间件
            List<MiddlewareManagementService.MiddlewareInfo> localMiddlewares = 
                middlewareManagementService.listMiddlewares();
            
            // 创建本地中间件映射表（pluginId -> MiddlewareInfo）
            Map<String, MiddlewareManagementService.MiddlewareInfo> localMiddlewareMap = new HashMap<>();
            for (MiddlewareManagementService.MiddlewareInfo localMiddleware : localMiddlewares) {
                localMiddlewareMap.put(localMiddleware.getId(), localMiddleware);
            }
            
            // 检查每个远程中间件的安装状态
            for (ApplicationDTO remoteMiddleware : remotePageResult.getItems()) {
                MiddlewareManagementService.MiddlewareInfo localMiddleware = 
                    localMiddlewareMap.get(remoteMiddleware.getPluginId());
                
                if (localMiddleware == null) {
                    // 未安装
                    remoteMiddleware.setInstallStatus("NOT_INSTALLED");
                    remoteMiddleware.setLocalVersion(null);
                } else {
                    // 已安装，检查版本
                    remoteMiddleware.setLocalVersion(localMiddleware.getVersion());
                    
                    if (isVersionNewer(remoteMiddleware.getPluginVersion(), localMiddleware.getVersion())) {
                        // 远程版本更新，可升级
                        remoteMiddleware.setInstallStatus("UPGRADABLE");
                    } else {
                        // 版本相同或本地版本更新
                        remoteMiddleware.setInstallStatus("INSTALLED");
                    }
                }
            }
            
            return Result.success(remotePageResult);
            
        } catch (Exception e) {
            log.error("查询远程中间件列表失败", e);
            return Result.error("查询远程中间件列表失败: " + e.getMessage());
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
     * 比较版本号，判断 version1 是否比 version2 更新
     * 
     * @param version1 版本1
     * @param version2 版本2
     * @return true 如果 version1 > version2
     */
    private boolean isVersionNewer(String version1, String version2) {
        if (version1 == null || version2 == null) {
            return false;
        }
        
        try {
            // 简单的版本比较（支持语义化版本号）
            String[] v1Parts = version1.split("\\.");
            String[] v2Parts = version2.split("\\.");
            
            int maxLength = Math.max(v1Parts.length, v2Parts.length);
            for (int i = 0; i < maxLength; i++) {
                int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
                int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;
                
                if (v1Part > v2Part) {
                    return true;
                } else if (v1Part < v2Part) {
                    return false;
                }
            }
            
            return false; // 版本相同
        } catch (Exception e) {
            log.warn("版本比较失败: version1={}, version2={}", version1, version2, e);
            return false;
        }
    }
    
    /**
     * 从远程应用商店安装中间件
     * 
     * @param pluginId 插件ID
     * @param operatorName 操作人
     * @return 安装结果
     */
    @PostMapping("/remote/install")
    public Result<MiddlewareManagementService.MiddlewareInstallResult> installRemoteMiddleware(
            @RequestParam String pluginId,
            @RequestParam(required = false, defaultValue = "admin") String operatorName,
            @RequestParam(required = false) String targetNodeId,
            @RequestParam(required = false) String executionType,
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestBody(required = false) Map<String, Object> config) {
        
        log.info("从远程应用商店安装中间件: pluginId={}, operator={}, targetNodeId={}, executionType={}, clientId={}, config={}",
                pluginId, operatorName, targetNodeId, executionType, clientId, config);
        
        try {
            if (clientId != null && !clientId.isEmpty()) {
                installSsePublisher.sendStart(clientId, pluginId, "开始安装中间件...");
            }

            // 1. 从远程应用商店下载中间件插件包
            String fileUrl = appStoreApiService.downloadApplication(pluginId);
            if (fileUrl == null || fileUrl.isEmpty()) {
                String err = "下载中间件插件包失败：文件URL为空";
                if (clientId != null && !clientId.isEmpty()) {
                    installSsePublisher.sendError(clientId, pluginId, err);
                    installSsePublisher.sendComplete(clientId, pluginId);
                }
                return Result.error(err);
            }
            
            log.info("中间件插件包下载成功: pluginId={}, fileUrl={}", pluginId, fileUrl);
            if (clientId != null && !clientId.isEmpty()) {
                installSsePublisher.sendLog(clientId, pluginId, "中间件插件包下载成功，开始保存临时文件...");
            }
            
            // 2. 从文件存储服务读取文件并保存到临时文件
            Path tmpFile = Files.createTempFile("middleware-remote-", ".jar");
            try {
                // 使用 toStream 方法下载文件
                fileStorageService.download(fileUrl)
                    .toStream(inputStream -> {
                        try {
                            Files.copy(inputStream, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException("保存临时文件失败: " + e.getMessage(), e);
                        }
                    })
                    .onError(e -> {
                        log.error("从文件存储服务读取文件失败: fileUrl={}", fileUrl, e);
                        throw new RuntimeException("读取中间件插件包失败: " + e.getMessage(), e);
                    })
                    .executeVoid();
                
                if (clientId != null && !clientId.isEmpty()) {
                    installSsePublisher.sendLog(clientId, pluginId, "临时文件已保存，开始执行安装流程...");
                }
                
                // 3. 调用中间件管理服务安装（传递配置数据）
                Result<MiddlewareManagementService.MiddlewareInstallResult> installResult = 
                    middlewareManagementService.installMiddleware(tmpFile, operatorName, targetNodeId, executionType, clientId, config);
                
                if (!installResult.isSuccess()) {
                    String err = "安装中间件失败: " + installResult.getErrorMessage();
                    if (clientId != null && !clientId.isEmpty()) {
                        installSsePublisher.sendError(clientId, pluginId, err);
                        installSsePublisher.sendComplete(clientId, pluginId);
                    }
                    return Result.error(err);
                }
                
                log.info("中间件安装成功: pluginId={}, operator={}", pluginId, operatorName);
                if (clientId != null && !clientId.isEmpty()) {
                    installSsePublisher.sendSuccess(clientId, pluginId, "中间件安装成功");
                    installSsePublisher.sendComplete(clientId, pluginId);
                }
                return installResult;
                
            } finally {
                // 清理临时文件
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}", tmpFile, e);
                }
            }
            
        } catch (Exception e) {
            log.error("从远程应用商店安装中间件失败: pluginId={}", pluginId, e);
            if (clientId != null && !clientId.isEmpty()) {
                installSsePublisher.sendError(clientId, pluginId, "安装失败: " + e.getMessage());
                installSsePublisher.sendComplete(clientId, pluginId);
            }
            return Result.error("安装失败: " + e.getMessage());
        }
    }

    // 原流式接口逻辑已合并到普通安装接口中，依赖全局 X-Client-Id / X-Namespace 头进行 SSE 推送
    
    /**
     * 获取中间件连接信息（用于复制）
     * 
     * 返回格式化的连接信息，包含 URL、账号、密码等，方便其他插件配置使用
     * 
     * @param middlewareId 中间件ID
     * @return 格式化的连接信息
     */
    @GetMapping("/{middlewareId}/connection-info")
    public Result<ConnectionInfoFormatter.ConnectionInfo> getConnectionInfo(@PathVariable String middlewareId) {
        try {
            Optional<MiddlewareManagementService.MiddlewareInfo> middlewareOpt =
                middlewareManagementService.getMiddleware(middlewareId);
            
            if (middlewareOpt.isEmpty()) {
                return Result.error("中间件不存在: " + middlewareId);
            }
            
            MiddlewareManagementService.MiddlewareInfo middleware = middlewareOpt.get();
            
            // 获取所有相关服务信息
            List<MiddlewareManagementService.MiddlewareServiceInfo> services = 
                middlewareManagementService.listMiddlewareServices();
            
            // 格式化连接信息
            ConnectionInfoFormatter.ConnectionInfo connectionInfo = 
                ConnectionInfoFormatter.format(middleware, services);
            
            return Result.success(connectionInfo);
        } catch (Exception e) {
            log.error("获取连接信息失败: middlewareId={}", middlewareId, e);
            return Result.error("获取连接信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取中间件连接信息（文本格式，用于复制）
     * 
     * @param middlewareId 中间件ID
     * @return 文本格式的连接信息
     */
    @GetMapping("/{middlewareId}/connection-info/text")
    public Result<Map<String, String>> getConnectionInfoText(@PathVariable String middlewareId) {
        try {
            Optional<MiddlewareManagementService.MiddlewareInfo> middlewareOpt = 
                middlewareManagementService.getMiddleware(middlewareId);
            
            if (middlewareOpt.isEmpty()) {
                return Result.error("中间件不存在: " + middlewareId);
            }
            
            MiddlewareManagementService.MiddlewareInfo middleware = middlewareOpt.get();
            
            // 获取所有相关服务信息
            List<MiddlewareManagementService.MiddlewareServiceInfo> services = 
                middlewareManagementService.listMiddlewareServices();
            
            // 格式化连接信息
            ConnectionInfoFormatter.ConnectionInfo connectionInfo = 
                ConnectionInfoFormatter.format(middleware, services);
            
            // 生成文本格式
            String text = ConnectionInfoFormatter.formatAsText(connectionInfo);
            
            Map<String, String> result = new HashMap<>();
            result.put("text", text);
            result.put("middlewareId", middlewareId);
            result.put("middlewareName", middleware.getName() != null ? middleware.getName() : middlewareId);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取连接信息文本失败: middlewareId={}", middlewareId, e);
            return Result.error("获取连接信息文本失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取中间件插件配置元数据（用于动态表单渲染）
     * 
     * 注意：插件还未安装，需要从远程应用商店的 jar 包中解析配置元数据
     * 
     * @param pluginId 插件ID
     * @return 配置元数据列表
     */
    @GetMapping("/remote/{pluginId}/config/metadata")
    public Result<java.util.List< com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata>> getPluginConfigMetadata(@PathVariable String pluginId) {
        log.info("获取中间件插件配置元数据: pluginId={}", pluginId);
        try {
            // 通过 AppStoreApiService 从远程 jar 包解析配置元数据
            List< com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata> metadata = 
                    appStoreApiService.getPluginConfigMetadataFromJar(pluginId);
            
            if (metadata == null || metadata.isEmpty()) {
                return Result.success(java.util.Collections.emptyList());
            }
            
            return Result.success(metadata);
        } catch (Exception e) {
            log.error("获取中间件插件配置元数据失败: pluginId={}", pluginId, e);
            return Result.error("获取配置元数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 列出所有中间件服务
     */
    @GetMapping("/services")
    public Result<List<MiddlewareManagementService.MiddlewareServiceInfo>> listServices() {
        try {
            List<MiddlewareManagementService.MiddlewareServiceInfo> services = 
                middlewareManagementService.listMiddlewareServices();
            return Result.success(services);
        } catch (Exception e) {
            log.error("查询服务列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取单个服务信息
     */
    @GetMapping("/services/{serviceId}")
    public Result<MiddlewareManagementService.MiddlewareServiceInfo> getService(@PathVariable String serviceId) {
        try {
            Optional<MiddlewareManagementService.MiddlewareServiceInfo> serviceOpt = 
                middlewareManagementService.getMiddlewareService(serviceId);
            if (serviceOpt.isEmpty()) {
                return Result.error("服务不存在: " + serviceId);
            }
            return Result.success(serviceOpt.get());
        } catch (Exception e) {
            log.error("查询服务失败: serviceId={}", serviceId, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 手动注册中间件服务
     */
    @PostMapping("/services")
    public Result<MiddlewareManagementService.MiddlewareServiceInfo> registerService(
            @RequestBody MiddlewareManagementService.MiddlewareServiceInfo serviceInfo) {
        try {
            if (serviceInfo.getServiceId() == null || serviceInfo.getServiceId().trim().isEmpty()) {
                return Result.error("服务ID不能为空");
            }
            
            // 设置默认值
            if (serviceInfo.getInstalledBy() == null || serviceInfo.getInstalledBy().trim().isEmpty()) {
                serviceInfo.setInstalledBy("manual");
            }
            if (serviceInfo.getServiceHost() == null || serviceInfo.getServiceHost().trim().isEmpty()) {
                serviceInfo.setServiceHost("localhost");
            }
            if (serviceInfo.getStatus() == null) {
                serviceInfo.setStatus(MiddlewareManagementService.MiddlewareStatus.RUNNING);
            }
            
            Result<MiddlewareManagementService.MiddlewareServiceInfo> result = 
                middlewareManagementService.registerMiddlewareService(serviceInfo.getServiceId(), serviceInfo);
            
            log.info("手动注册服务: serviceId={}, host={}, port={}", 
                serviceInfo.getServiceId(), serviceInfo.getServiceHost(), serviceInfo.getServicePort());
            
            return result;
        } catch (Exception e) {
            log.error("注册服务失败: serviceId={}", serviceInfo.getServiceId(), e);
            return Result.error("注册失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新中间件服务信息
     */
    @PutMapping("/services/{serviceId}")
    public Result<MiddlewareManagementService.MiddlewareServiceInfo> updateService(
            @PathVariable String serviceId,
            @RequestBody MiddlewareManagementService.MiddlewareServiceInfo serviceInfo) {
        try {
            // 检查服务是否存在
            Optional<MiddlewareManagementService.MiddlewareServiceInfo> existingOpt = 
                middlewareManagementService.getMiddlewareService(serviceId);
            if (existingOpt.isEmpty()) {
                return Result.error("服务不存在: " + serviceId);
            }
            
            // 确保 serviceId 一致
            serviceInfo.setServiceId(serviceId);
            
            // 更新服务信息（通过删除后重新注册实现）
            // 注意：这里简化处理，实际应该提供专门的更新方法
            Result<Void> deleteResult = middlewareManagementService.deleteMiddlewareService(serviceId);
            if (!deleteResult.isSuccess()) {
                return Result.error("更新失败: " + deleteResult.getErrorMessage());
            }
            
            Result<MiddlewareManagementService.MiddlewareServiceInfo> registerResult = 
                middlewareManagementService.registerMiddlewareService(serviceId, serviceInfo);
            
            log.info("更新服务: serviceId={}", serviceId);
            return registerResult;
        } catch (Exception e) {
            log.error("更新服务失败: serviceId={}", serviceId, e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除中间件服务
     */
    @DeleteMapping("/services/{serviceId}")
    public Result<Void> deleteService(@PathVariable String serviceId) {
        try {
            Result<Void> result = middlewareManagementService.deleteMiddlewareService(serviceId);
            if (result.isSuccess()) {
                log.info("删除服务: serviceId={}", serviceId);
            }
            return result;
        } catch (Exception e) {
            log.error("删除服务失败: serviceId={}", serviceId, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
}

