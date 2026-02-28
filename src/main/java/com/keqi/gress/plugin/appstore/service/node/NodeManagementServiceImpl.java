package com.keqi.gress.plugin.appstore.service.node;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;

import com.keqi.gress.plugin.appstore.domain.entity.NodeInfoEntity;
import com.keqi.gress.plugin.appstore.service.NodeManagementService;
import com.keqi.gress.plugin.appstore.service.middleware.execution.DockerApiExecutionEnvironment;
import com.keqi.gress.plugin.appstore.service.middleware.execution.SshExecutionEnvironment;
import com.keqi.gress.plugin.appstore.service.node.repository.NodeInfoRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 节点管理服务（持久化实现）
 *
 * - 使用 sys_node_info 表保存节点信息
 * - config 字段以 JSON 存储，前端可自由扩展
 */
@Service
@Slf4j
public class NodeManagementServiceImpl implements NodeManagementService {

   // private static final Log log = LogFactory.get(NodeManagementServiceImpl.class);

    @Inject
    private NodeInfoRepository nodeInfoRepository;

    @Override
    public Optional<NodeInfo> getNode(String nodeId) {
        return nodeInfoRepository.findByNodeId(nodeId).map(this::toNodeInfo);
    }

    @Override
    public List<NodeInfo> listNodes() {
        List<NodeInfoEntity> entities = nodeInfoRepository.findAll();
        List<NodeInfo> result = new ArrayList<>();
        for (NodeInfoEntity e : entities) {
            result.add(toNodeInfo(e));
        }
        return result;
    }

    @Override
    public Result<NodeInfo> saveNode(NodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.getNodeId() == null || nodeInfo.getNodeId().isBlank()) {
            return Result.error("nodeId 不能为空");
        }
        if (nodeInfo.getType() == null || nodeInfo.getType().isBlank()) {
            return Result.error("type 不能为空（local | ssh | docker-api）");
        }
        if (nodeInfo.getConfig() == null) {
            return Result.error("config 不能为空");
        }

        long now = Instant.now().toEpochMilli();

        Optional<NodeInfoEntity> existing = nodeInfoRepository.findByNodeId(nodeInfo.getNodeId());
        if (existing.isPresent()) {
            NodeInfoEntity e = existing.get();
            e.setName(nodeInfo.getName());
            e.setType(nodeInfo.getType());
            e.setDescription(nodeInfo.getDescription());
            e.setEnabled(nodeInfo.isEnabled());
            e.setConfig(serializeConfig(nodeInfo));
            e.setUpdatedAt(now);
            nodeInfoRepository.update(e);
            return Result.success(toNodeInfo(e));
        }

        NodeInfoEntity e = new NodeInfoEntity();
        e.setNodeId(nodeInfo.getNodeId());
        e.setName(nodeInfo.getName());
        e.setType(nodeInfo.getType());
        e.setDescription(nodeInfo.getDescription());
        e.setEnabled(nodeInfo.isEnabled());
        e.setConfig(serializeConfig(nodeInfo));
        e.setCreatedAt(nodeInfo.getCreatedAt() != null ? nodeInfo.getCreatedAt() : now);
        e.setUpdatedAt(now);
        nodeInfoRepository.save(e);
        return Result.success(toNodeInfo(e));
    }

    @Override
    public Result<Void> deleteNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return Result.error("nodeId 不能为空");
        }
        nodeInfoRepository.deleteByNodeId(nodeId);
        return Result.success();
    }

    @Override
    public Result<Boolean> testConnection(String nodeId) {
        Optional<NodeInfo> opt = getNode(nodeId);
        if (opt.isEmpty()) {
            return Result.error("节点不存在: " + nodeId);
        }
        NodeInfo node = opt.get();
        if (!node.isEnabled()) {
            return Result.error("节点未启用: " + nodeId);
        }

        try {
            boolean ok;
            switch (node.getType()) {
                case "local" -> ok = true;
                case "ssh" -> {
                    // 兼容：config 以 Map 形式存储/返回
                    SshNodeConfig cfg = parseSshConfig(node);
                    SshExecutionEnvironment env = new SshExecutionEnvironment(cfg);
                    ok = env.isAvailable();
                    env.close();
                }
                case "docker-api" -> {
                    DockerApiNodeConfig cfg = parseDockerApiConfig(node);
                    DockerApiExecutionEnvironment env = new DockerApiExecutionEnvironment(cfg);
                    ok = env.isAvailable();
                }
                default -> {
                    return Result.error("不支持的节点类型: " + node.getType());
                }
            }
            return Result.success(ok);
        } catch (Exception e) {
            log.warn("测试节点连接失败: nodeId={}, err={}", nodeId, e.getMessage());
            return Result.success(false);
        }
    }

    private String serializeConfig(NodeInfo nodeInfo) {
        // NodeInfo.config 是抽象类型，实际接收时可能为 Map；统一存为 JSON
        return JSON.toJSONString(nodeInfo.getConfig());
    }

    private NodeInfo toNodeInfo(NodeInfoEntity e) {
        NodeInfo info = NodeInfo.builder()
            .nodeId(e.getNodeId())
            .name(e.getName())
            .type(e.getType())
            .description(e.getDescription())
            .enabled(Boolean.TRUE.equals(e.getEnabled()))
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .build();

        // 将 config JSON 解析为正确的 NodeConfig 子类
        try {
            if ("ssh".equals(e.getType())) {
                info.setConfig(parseSshConfigFromJson(e.getConfig()));
            } else if ("docker-api".equals(e.getType())) {
                info.setConfig(parseDockerApiConfigFromJson(e.getConfig()));
            } else {
                // local
                info.setConfig(new LocalNodeConfig());
            }
        } catch (Exception ex) {
            // 兜底：不影响列表/展示
            info.setConfig(new LocalNodeConfig());
        }

        return info;
    }

    private SshNodeConfig parseSshConfig(NodeInfo node) {
        return (SshNodeConfig) node.getConfig();
    }

    private DockerApiNodeConfig parseDockerApiConfig(NodeInfo node) {
        return (DockerApiNodeConfig) node.getConfig();
    }

    private SshNodeConfig parseSshConfigFromJson(String json) {
        // 默认值兜底（避免历史数据缺字段）
        SshNodeConfig defaults = new SshNodeConfig();
        defaults.setAuthType("PASSWORD");
        defaults.setPort(22);
        defaults.setTimeoutSeconds(30);

        if (json == null || json.isBlank()) {
            return defaults;
        }

        try {
            SshNodeConfig cfg = JSON.parseObject(json, SshNodeConfig.class);
            if (cfg == null) {
                return defaults;
            }
            // 兜底默认值
            if (cfg.getAuthType() == null || cfg.getAuthType().isBlank()) cfg.setAuthType(defaults.getAuthType());
            if (cfg.getPort() == null) cfg.setPort(defaults.getPort());
            if (cfg.getTimeoutSeconds() == null) cfg.setTimeoutSeconds(defaults.getTimeoutSeconds());
            return cfg;
        } catch (Exception e) {
            return defaults;
        }
    }

    private DockerApiNodeConfig parseDockerApiConfigFromJson(String json) {
        DockerApiNodeConfig defaults = new DockerApiNodeConfig();
        if (json == null || json.isBlank()) {
            return defaults;
        }

        try {
            DockerApiNodeConfig cfg = JSON.parseObject(json, DockerApiNodeConfig.class);
            return cfg != null ? cfg : defaults;
        } catch (Exception e) {
            return defaults;
        }
    }

    private Integer toInt(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
        }
        return def;
    }
}

