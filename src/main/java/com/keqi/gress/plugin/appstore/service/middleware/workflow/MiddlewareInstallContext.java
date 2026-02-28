package com.keqi.gress.plugin.appstore.service.middleware.workflow;


import com.keqi.gress.plugin.appstore.service.MiddlewareManagementService;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 中间件安装上下文
 */
@Data
@Builder
public class MiddlewareInstallContext {
    /**
     * 中间件ID
     */
    private String middlewareId;
    
    /**
     * 版本
     */
    private String version;
    
    /**
     * 中间件插件包路径
     */
    private Path middlewarePackage;
    
    /**
     * 工作目录
     */
    private Path workDir;
    
    /**
     * 操作人
     */
    private String operator;
    
    /**
     * 元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * 已解析的依赖服务信息（用于注入到 docker-compose 环境变量中）
     */
    @Builder.Default
    private Map<String, MiddlewareManagementService.MiddlewareServiceInfo> resolvedServices = new HashMap<>();
    
    /**
     * 目标节点ID（用于远程部署）
     * 如果为 null，则使用本地执行环境
     */
    private String targetNodeId;
    
    /**
     * 执行环境类型：local | ssh | docker-api
     * 如果为 null，则根据 targetNodeId 自动判断
     */
    private String executionType;

    /**
     * SSE 客户端ID（用于推送实时日志）
     * 如果为 null，则不推送 SSE 日志
     */
    private String clientId;

    /**
     * 安装配置（用于替换 docker-compose.yml 中的环境变量）
     * 例如：{"MINIO_ROOT_USER": "admin", "MINIO_ROOT_PASSWORD": "password123"}
     */
    @Builder.Default
    private Map<String, Object> installConfig = new HashMap<>();
}
