package com.keqi.gress.plugin.appstore.service.middleware.execution;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.keqi.gress.plugin.appstore.service.NodeManagementService;
import lombok.extern.slf4j.Slf4j;


/**
 * 执行环境工厂
 * 根据节点信息创建对应的执行环境
 */
@Slf4j
public class ExecutionEnvironmentFactory {

    //private static final Log log = LogFactory.get(ExecutionEnvironmentFactory.class);

    /**
     * 日志回调接口
     */
    @FunctionalInterface
    public interface LogCallback {
        void log(String message);
    }
    
    /**
     * 根据节点信息创建执行环境
     * 
     * @param nodeInfo 节点信息
     * @return 执行环境
     */
    public ExecutionEnvironment create(NodeManagementService.NodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.getConfig() == null) {
            log.warn("节点信息为空，使用本地执行环境");
            return new LocalExecutionEnvironment();
        }
        
        NodeManagementService.NodeConfig config = nodeInfo.getConfig();
        String type = config.getType();
        
        switch (type) {
            case "local":
                return new LocalExecutionEnvironment();
                
            case "ssh":
                if (config instanceof NodeManagementService.SshNodeConfig) {
                    return new SshExecutionEnvironment((NodeManagementService.SshNodeConfig) config);
                }
                log.warn("SSH 节点配置类型不匹配，使用本地执行环境");
                return new LocalExecutionEnvironment();
                
            case "docker-api":
                if (config instanceof NodeManagementService.DockerApiNodeConfig) {
                    return new DockerApiExecutionEnvironment((NodeManagementService.DockerApiNodeConfig) config);
                }
                log.warn("Docker API 节点配置类型不匹配，使用本地执行环境");
                return new LocalExecutionEnvironment();
                
            default:
                log.warn("未知的节点类型: {}，使用本地执行环境", type);
                return new LocalExecutionEnvironment();
        }
    }
    
    /**
     * 根据执行环境类型和节点信息创建执行环境
     * 
     * @param executionType 执行环境类型：local | ssh | docker-api
     * @param nodeInfo 节点信息（如果 executionType 不是 local，则必须提供）
     * @return 执行环境
     */
    public ExecutionEnvironment create(String executionType, NodeManagementService.NodeInfo nodeInfo) {
        if ("local".equals(executionType) || executionType == null) {
            return new LocalExecutionEnvironment();
        }
        
        if (nodeInfo == null) {
            log.warn("执行环境类型为 {} 但节点信息为空，使用本地执行环境", executionType);
            return new LocalExecutionEnvironment();
        }
        
        // 验证节点类型是否匹配
        if (!executionType.equals(nodeInfo.getType())) {
            log.warn("执行环境类型 {} 与节点类型 {} 不匹配，使用节点类型", 
                executionType, nodeInfo.getType());
        }
        
        return create(nodeInfo);
    }

    /**
     * 根据节点信息创建执行环境（支持日志回调）
     *
     * @param nodeInfo 节点信息
     * @param logCallback 日志回调（可选）
     * @return 执行环境
     */
    public ExecutionEnvironment create(NodeManagementService.NodeInfo nodeInfo, LogCallback logCallback) {
        if (nodeInfo == null || nodeInfo.getConfig() == null) {
            log.warn("节点信息为空，使用本地执行环境");
            return new LocalExecutionEnvironment();
        }

        NodeManagementService.NodeConfig config = nodeInfo.getConfig();
        String type = config.getType();

        switch (type) {
            case "local":
                return new LocalExecutionEnvironment();

            case "ssh":
                if (config instanceof NodeManagementService.SshNodeConfig) {
                    return new SshExecutionEnvironment((NodeManagementService.SshNodeConfig) config,
                        logCallback != null ? logCallback::log : null);
                }
                log.warn("SSH 节点配置类型不匹配，使用本地执行环境");
                return new LocalExecutionEnvironment();

            case "docker-api":
                if (config instanceof NodeManagementService.DockerApiNodeConfig) {
                    return new DockerApiExecutionEnvironment((NodeManagementService.DockerApiNodeConfig) config);
                }
                log.warn("Docker API 节点配置类型不匹配，使用本地执行环境");
                return new LocalExecutionEnvironment();

            default:
                log.warn("未知的节点类型: {}，使用本地执行环境", type);
                return new LocalExecutionEnvironment();
        }
    }

    /**
     * 根据执行环境类型和节点信息创建执行环境（支持日志回调）
     *
     * @param executionType 执行环境类型：local | ssh | docker-api
     * @param nodeInfo 节点信息（如果 executionType 不是 local，则必须提供）
     * @param logCallback 日志回调（可选）
     * @return 执行环境
     */
    public ExecutionEnvironment create(String executionType, NodeManagementService.NodeInfo nodeInfo, LogCallback logCallback) {
        if ("local".equals(executionType) || executionType == null) {
            return new LocalExecutionEnvironment();
        }

        if (nodeInfo == null) {
            log.warn("执行环境类型为 {} 但节点信息为空，使用本地执行环境", executionType);
            return new LocalExecutionEnvironment();
        }

        // 验证节点类型是否匹配
        if (!executionType.equals(nodeInfo.getType())) {
            log.warn("执行环境类型 {} 与节点类型 {} 不匹配，使用节点类型",
                executionType, nodeInfo.getType());
        }

        return create(nodeInfo, logCallback);
    }
}
