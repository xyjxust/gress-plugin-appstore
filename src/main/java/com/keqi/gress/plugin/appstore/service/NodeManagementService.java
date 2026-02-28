package com.keqi.gress.plugin.appstore.service;


import com.keqi.gress.common.model.Result;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Optional;

/**
 * 节点管理服务接口
 * 
 * 用于管理远程服务器节点信息，支持：
 * - 本地节点（localhost）
 * - SSH 节点（通过 SSH 连接）
 * - Docker API 节点（通过 Docker API 连接）
 * 
 * 该服务可以由节点管理插件实现，或由应用商店插件内置实现。
 */
public interface NodeManagementService {
    
    /**
     * 获取节点信息
     * 
     * @param nodeId 节点ID
     * @return 节点信息，如果不存在返回空
     */
    Optional<NodeInfo> getNode(String nodeId);
    
    /**
     * 列出所有节点
     */
    List<NodeInfo> listNodes();
    
    /**
     * 创建或更新节点
     */
    Result<NodeInfo> saveNode(NodeInfo nodeInfo);
    
    /**
     * 删除节点
     */
    Result<Void> deleteNode(String nodeId);
    
    /**
     * 测试节点连接
     */
    Result<Boolean> testConnection(String nodeId);
    
    /**
     * 节点信息
     */
    @Data
    @Builder
    class NodeInfo {
        /**
         * 节点ID
         */
        private String nodeId;
        
        /**
         * 节点名称
         */
        private String name;
        
        /**
         * 节点类型：local | ssh | docker-api
         */
        private String type;
        
        /**
         * 节点描述
         */
        private String description;
        
        /**
         * 节点配置（根据类型不同，配置内容不同）
         */
        private NodeConfig config;
        
        /**
         * 是否启用
         */
        @Builder.Default
        private boolean enabled = true;
        
        /**
         * 创建时间
         */
        private Long createdAt;
        
        /**
         * 更新时间
         */
        private Long updatedAt;
    }
    
    /**
     * 节点配置基类
     */
    @Data
    abstract class NodeConfig {
        /**
         * 节点类型
         */
        private String type;
    }
    
    /**
     * 本地节点配置
     */
    @Data
    class LocalNodeConfig extends NodeConfig {
        public LocalNodeConfig() {
            setType("local");
        }
    }
    
    /**
     * SSH 节点配置
     */
    @Data
    class SshNodeConfig extends NodeConfig {
        private String host;
        private Integer port;
        private String username;
        private String authType; // PASSWORD | KEY
        private String password; // 密码认证时使用
        private String privateKey; // 密钥认证时使用
        private String passphrase; // 密钥密码（如果密钥有密码保护）
        private Integer timeoutSeconds; // 连接超时时间（秒）
        
        public SshNodeConfig() {
            setType("ssh");
        }
    }
    
    /**
     * Docker API 节点配置
     */
    @Data
    class DockerApiNodeConfig extends NodeConfig {
        private String dockerHost; // Docker daemon 地址，如 tcp://192.168.1.100:2376
        private String dockerCertPath; // Docker 证书路径
        private Boolean dockerTlsVerify; // 是否启用 TLS 验证
        
        public DockerApiNodeConfig() {
            setType("docker-api");
        }
    }
}
