package com.keqi.gress.plugin.appstore.contoller;

import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;

import com.keqi.gress.plugin.appstore.domain.entity.NodeInfoEntity;
import com.keqi.gress.plugin.appstore.service.NodeManagementService;
import com.keqi.gress.plugin.appstore.service.node.repository.NodeInfoRepository;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Map;

/**
 * 节点管理控制器（供前端节点管理页面使用）
 *
 * 路径：
 * - 前端通过 /plugins/appstore/nodes 访问（宿主会加上插件前缀）
 */
@Service
@RestController
@RequestMapping("/nodes")
@Valid
@Slf4j
public class NodeManagementController {
    @Inject(required = false)
    private NodeManagementService nodeManagementService;

    @Inject(required = false)
    private NodeInfoRepository nodeInfoRepository;

    @GetMapping
    public Result<List<NodeDTO>> list() {
        if (nodeInfoRepository == null) {
            return Result.success(List.of());
        }
        List<NodeInfoEntity> entities = nodeInfoRepository.findAll();
        List<NodeDTO> result = new java.util.ArrayList<>();
        for (NodeInfoEntity e : entities) {
            result.add(toDto(e, false));
        }
        return Result.success(result);
    }

    @GetMapping("/{nodeId}")
    public Result<NodeDTO> get(@PathVariable String nodeId) {
        if (nodeInfoRepository == null) {
            return Result.error("节点管理服务不可用");
        }
        Optional<NodeInfoEntity> opt = nodeInfoRepository.findByNodeId(nodeId);
        return opt.map(e -> Result.success(toDto(e, false)))
            .orElseGet(() -> Result.error("节点不存在: " + nodeId));
    }

    @PostMapping
    public Result<NodeManagementService.NodeInfo> save(@RequestBody NodeDTO dto) {
        if (nodeManagementService == null) {
            return Result.error("节点管理服务不可用");
        }
        return nodeManagementService.saveNode(toNodeInfo(dto));
    }

    @DeleteMapping("/{nodeId}")
    public Result<Void> delete(@PathVariable String nodeId) {
        if (nodeManagementService == null) {
            return Result.error("节点管理服务不可用");
        }
        return nodeManagementService.deleteNode(nodeId);
    }

    @PostMapping("/{nodeId}/test")
    public Result<Boolean> test(@PathVariable String nodeId) {
        if (nodeManagementService == null) {
            return Result.error("节点管理服务不可用");
        }
        return nodeManagementService.testConnection(nodeId);
    }

    /**
     * 前端 DTO：config 用 JSON 对象（Map）承载，避免抽象类反序列化问题
     */
    public static class NodeDTO {
        public String nodeId;
        public String name;
        public String type; // local | ssh | docker-api
        public String description;
        public Boolean enabled;
        public Map<String, Object> config;
    }

    private NodeDTO toDto(NodeInfoEntity e, boolean includeSecret) {
        NodeDTO dto = new NodeDTO();
        dto.nodeId = e.getNodeId();
        dto.name = e.getName();
        dto.type = e.getType();
        dto.description = e.getDescription();
        dto.enabled = Boolean.TRUE.equals(e.getEnabled());

        if (e.getConfig() != null && !e.getConfig().isBlank()) {
            Object cfg = null;
            try {
                cfg = com.alibaba.fastjson2.JSON.parse(e.getConfig());
            } catch (Exception ignored) {}
            if (cfg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) cfg;
                // 脱敏：编辑时不回显密码/私钥
                if (!includeSecret && m != null) {
                    m.put("password", "");
                    m.put("privateKey", "");
                    m.put("passphrase", "");
                }
                dto.config = m;
            }
        }
        return dto;
    }

    private NodeManagementService.NodeInfo toNodeInfo(NodeDTO dto) {
        NodeManagementService.NodeInfo info = NodeManagementService.NodeInfo.builder()
            .nodeId(dto.nodeId)
            .name(dto.name)
            .type(dto.type)
            .description(dto.description)
            .enabled(dto.enabled != null ? dto.enabled : true)
            .build();

        // 根据 type 构造具体 config
        if ("ssh".equals(dto.type)) {
            NodeManagementService.SshNodeConfig cfg = new NodeManagementService.SshNodeConfig();
            if (dto.config != null) {
                cfg.setHost((String) dto.config.get("host"));
                cfg.setPort(toInt(dto.config.get("port"), 22));
                cfg.setUsername((String) dto.config.get("username"));
                cfg.setAuthType((String) dto.config.getOrDefault("authType", "PASSWORD"));
                cfg.setPassword((String) dto.config.get("password"));
                cfg.setPrivateKey((String) dto.config.get("privateKey"));
                cfg.setPassphrase((String) dto.config.get("passphrase"));
                cfg.setTimeoutSeconds(toInt(dto.config.get("timeoutSeconds"), 30));
            }
            info.setConfig(cfg);
        } else if ("docker-api".equals(dto.type)) {
            NodeManagementService.DockerApiNodeConfig cfg = new NodeManagementService.DockerApiNodeConfig();
            if (dto.config != null) {
                cfg.setDockerHost((String) dto.config.get("dockerHost"));
                cfg.setDockerCertPath((String) dto.config.get("dockerCertPath"));
                Object tls = dto.config.get("dockerTlsVerify");
                if (tls instanceof Boolean) cfg.setDockerTlsVerify((Boolean) tls);
                if (tls instanceof String) cfg.setDockerTlsVerify(Boolean.parseBoolean((String) tls));
            }
            info.setConfig(cfg);
        } else {
            info.setConfig(new NodeManagementService.LocalNodeConfig());
        }

        return info;
    }

    private Integer toInt(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
        }
        return def;
    }
}

