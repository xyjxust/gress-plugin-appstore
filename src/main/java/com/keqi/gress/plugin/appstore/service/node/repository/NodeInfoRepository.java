package com.keqi.gress.plugin.appstore.service.node.repository;

import com.keqi.gress.plugin.appstore.domain.entity.NodeInfoEntity;

import java.util.List;
import java.util.Optional;

public interface NodeInfoRepository {
    void save(NodeInfoEntity entity);
    void update(NodeInfoEntity entity);
    Optional<NodeInfoEntity> findByNodeId(String nodeId);
    List<NodeInfoEntity> findAll();
    void deleteByNodeId(String nodeId);
}

