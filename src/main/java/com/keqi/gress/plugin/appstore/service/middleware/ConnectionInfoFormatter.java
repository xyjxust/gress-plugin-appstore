package com.keqi.gress.plugin.appstore.service.middleware;


import com.keqi.gress.plugin.appstore.service.MiddlewareManagementService;
import lombok.Data;

import java.util.*;

/**
 * 连接信息格式化器
 * 
 * 直接返回存储的连接信息，不做定制格式化
 */
public class ConnectionInfoFormatter {
    
    /**
     * 格式化中间件连接信息
     * 
     * @param middleware 中间件信息
     * @param services 相关服务列表
     * @return 连接信息
     */
    public static ConnectionInfo format(MiddlewareManagementService.MiddlewareInfo middleware,
                                        List<MiddlewareManagementService.MiddlewareServiceInfo> services) {
        ConnectionInfo info = new ConnectionInfo();
        info.setMiddlewareId(middleware.getId());
        info.setMiddlewareName(middleware.getName());
        info.setVersion(middleware.getVersion());
        
        // 过滤出该中间件安装的服务，直接返回原始服务信息
        List<MiddlewareManagementService.MiddlewareServiceInfo> middlewareServices = new ArrayList<>();
        for (MiddlewareManagementService.MiddlewareServiceInfo service : services) {
            if (middleware.getId().equals(service.getInstalledBy())) {
                middlewareServices.add(service);
            }
        }
        
        info.setServices(middlewareServices);
        
        return info;
    }
    
    /**
     * 生成文本格式的连接信息（用于复制）
     * 直接输出服务信息的键值对格式
     */
    public static String formatAsText(ConnectionInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(info.getMiddlewareName() != null ? info.getMiddlewareName() : info.getMiddlewareId())
          .append(" 连接信息\n");
        if (info.getVersion() != null) {
            sb.append("# 版本: ").append(info.getVersion()).append("\n");
        }
        sb.append("\n");
        
        if (info.getServices() != null && !info.getServices().isEmpty()) {
            for (MiddlewareManagementService.MiddlewareServiceInfo service : info.getServices()) {
                sb.append("## ").append(service.getServiceName() != null ? service.getServiceName() : service.getServiceId())
                  .append("\n");
                sb.append("serviceId: ").append(service.getServiceId()).append("\n");
                sb.append("serviceType: ").append(service.getServiceType() != null ? service.getServiceType() : "").append("\n");
                sb.append("serviceName: ").append(service.getServiceName() != null ? service.getServiceName() : "").append("\n");
                sb.append("host: ").append(service.getServiceHost() != null ? service.getServiceHost() : "localhost").append("\n");
                if (service.getServicePort() != null) {
                    sb.append("port: ").append(service.getServicePort()).append("\n");
                }
                if (service.getHealthCheckUrl() != null) {
                    sb.append("healthCheckUrl: ").append(service.getHealthCheckUrl()).append("\n");
                }
                
                // 输出配置信息（包含账号密码等）
                if (service.getConfig() != null && !service.getConfig().isEmpty()) {
                    sb.append("config:\n");
                    for (Map.Entry<String, Object> entry : service.getConfig().entrySet()) {
                        sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    }
                }
                
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }
    
    @Data
    public static class ConnectionInfo {
        private String middlewareId;
        private String middlewareName;
        private String version;
        private List<MiddlewareManagementService.MiddlewareServiceInfo> services;
    }
}
