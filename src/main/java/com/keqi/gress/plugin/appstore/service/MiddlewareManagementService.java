package com.keqi.gress.plugin.appstore.service;


import com.keqi.gress.common.model.Result;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 中间件管理服务（由应用商店插件实现并对外暴露）。
 *
 * 设计目标：
 * - 管理员手动安装/卸载中间件插件（例如 Milvus、Redis 等）
 * - 其他业务插件只依赖 gress-plugin-api，通过该接口查询中间件是否可用
 * - 具体安装流程由中间件插件内的 install-workflow.yml 编排（docker-compose + shell 等）
 */
public interface MiddlewareManagementService {

    /**
     * 安装中间件插件（管理员操作）
     *
     * @param middlewarePackage 中间件插件包（jar）路径
     * @param operator          操作人
     */
    Result<MiddlewareInstallResult> installMiddleware(Path middlewarePackage, String operator);

    /**
     * 安装中间件插件到指定节点（管理员操作）
     *
     * @param middlewarePackage 中间件插件包（jar）路径
     * @param operator          操作人
     * @param targetNodeId      目标节点ID（如果为 null，则在本地安装）
     * @param executionType     执行环境类型：local | ssh | docker-api（如果为 null，则根据节点类型自动判断）
     */
    default Result<MiddlewareInstallResult> installMiddleware(
            Path middlewarePackage,
            String operator,
            String targetNodeId,
            String executionType) {
        // 默认实现：调用原方法（向后兼容）
        return installMiddleware(middlewarePackage, operator);
    }

    /**
     * 安装中间件插件到指定节点并推送SSE日志（管理员操作）
     *
     * @param middlewarePackage 中间件插件包（jar）路径
     * @param operator          操作人
     * @param targetNodeId      目标节点ID（如果为 null，则在本地安装）
     * @param executionType     执行环境类型：local | ssh | docker-api（如果为 null，则根据节点类型自动判断）
     * @param clientId          SSE客户端ID（用于推送实时日志，如果为 null 则不推送）
     */
    default Result<MiddlewareInstallResult> installMiddleware(
            Path middlewarePackage,
            String operator,
            String targetNodeId,
            String executionType,
            String clientId) {
        // 默认实现：调用原方法（向后兼容）
        return installMiddleware(middlewarePackage, operator, targetNodeId, executionType);
    }

    /**
     * 安装中间件插件到指定节点并推送SSE日志（带配置数据）
     *
     * @param middlewarePackage 中间件插件包（jar）路径
     * @param operator          操作人
     * @param targetNodeId      目标节点ID（如果为 null，则在本地安装）
     * @param executionType     执行环境类型：local | ssh | docker-api（如果为 null，则根据节点类型自动判断）
     * @param clientId          SSE客户端ID（用于推送实时日志，如果为 null 则不推送）
     * @param config            安装配置（用于替换 docker-compose.yml 中的环境变量）
     */
    default Result<MiddlewareInstallResult> installMiddleware(
            Path middlewarePackage,
            String operator,
            String targetNodeId,
            String executionType,
            String clientId,
            Map<String, Object> config) {
        // 默认实现：调用原方法（向后兼容）
        return installMiddleware(middlewarePackage, operator, targetNodeId, executionType, clientId);
    }

    /**
     * 卸载中间件插件（管理员操作）
     *
     * @param middlewareId 中间件ID（通常为 pluginId）
     * @param operator     操作人
     */
    Result<Void> uninstallMiddleware(String middlewareId, String operator);

    /**
     * 查询中间件信息
     */
    Optional<MiddlewareInfo> getMiddleware(String middlewareId);

    /**
     * 列出所有已注册的中间件
     */
    List<MiddlewareInfo> listMiddlewares();

    /**
     * 判断中间件是否可用（已安装 + 运行中 + 健康）
     */
    boolean isMiddlewareAvailable(String middlewareId);

    /**
     * 健康检查
     */
    HealthCheckResult checkHealth(String middlewareId);
    
    /**
     * 获取中间件服务信息（用于查询共享服务）
     * 
     * @param serviceId 服务ID（如 "minio", "etcd"）
     * @return 服务信息
     */
    Optional<MiddlewareServiceInfo> getMiddlewareService(String serviceId);
    
    /**
     * 列出所有中间件服务
     */
    List<MiddlewareServiceInfo> listMiddlewareServices();
    
    /**
     * 检查中间件服务是否可用
     */
    boolean isMiddlewareServiceAvailable(String serviceId);
    
    /**
     * 注册中间件服务（安装成功后调用）
     */
    Result<MiddlewareServiceInfo> registerMiddlewareService(
        String serviceId,
        MiddlewareServiceInfo serviceInfo
    );
    
    /**
     * 增加服务引用计数
     */
    Result<Void> incrementServiceReference(String serviceId, String middlewareId);
    
    /**
     * 减少服务引用计数
     */
    Result<Void> decrementServiceReference(String serviceId, String middlewareId);
    
    /**
     * 删除中间件服务（手动注册的服务可以删除）
     */
    default Result<Void> deleteMiddlewareService(String serviceId) {
        return Result.error("删除服务功能未实现");
    }

    // ===================== DTO 定义 =====================

    @Data
    @Builder
    class MiddlewareInstallResult {
        private String middlewareId;
        private String version;
        private String workDir;
        private String message;
    }

    @Data
    @Builder
    class MiddlewareInfo {
        private String id;
        private String name;
        private String version;
        private String category;
        private boolean shared;
        private String serviceHost;
        private Integer servicePort;
        private String healthCheckUrl;
        private MiddlewareStatus status;
        private String workDir;
        private Map<String, Object> config;
    }

    enum MiddlewareStatus {
        NOT_INSTALLED,
        INSTALLING,
        RUNNING,
        STOPPED,
        ERROR,
        UNKNOWN
    }

    @Data
    @Builder
    class HealthCheckResult {
        private boolean healthy;
        private String message;
        private Long timestamp;

        public static HealthCheckResult healthy() {
            return HealthCheckResult.builder()
                    .healthy(true)
                    .message("健康")
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static HealthCheckResult unhealthy(String message) {
            return HealthCheckResult.builder()
                    .healthy(false)
                    .message(message)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static HealthCheckResult notInstalled() {
            return HealthCheckResult.builder()
                    .healthy(false)
                    .message("中间件未安装")
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }
    
    /**
     * 中间件服务信息（用于共享服务管理）
     */
    @Data
    @Builder
    class MiddlewareServiceInfo {
        private String serviceId;           // 服务ID（如 "minio"）
        private String serviceType;         // 服务类型
        private String serviceName;         // 服务名称
        private String containerName;       // Docker容器名称
        private String serviceHost;         // 服务主机
        private Integer servicePort;         // 服务端口
        private String healthCheckUrl;      // 健康检查URL
        private Map<String, Object> config; // 服务配置（密码、连接信息等）
        private String installedBy;         // 安装者（中间件插件ID）
        private int referenceCount;         // 引用计数
        private List<String> consumers;     // 使用该服务的中间件列表
        private MiddlewareStatus status;     // 服务状态
        private String workDir;             // 工作目录
    }
}

