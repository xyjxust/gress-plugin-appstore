package com.keqi.gress.plugin.appstore.dao;

import com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.StackTargetEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StackTargetDao {

    @Autowired
    private PluginLambdaDataSource dataSource;

    public List<StackTargetEntity> listByStackId(String stackId) {
        return dataSource.lambdaQuery(StackTargetEntity.class)
            .eq(StackTargetEntity::getStackId, stackId)
            .orderByAsc(StackTargetEntity::getNodeId)
            .list();
    }

    public Optional<StackTargetEntity> findByStackIdAndNodeId(String stackId, String nodeId) {
        StackTargetEntity one = dataSource.lambdaQuery(StackTargetEntity.class)
            .eq(StackTargetEntity::getStackId, stackId)
            .eq(StackTargetEntity::getNodeId, nodeId)
            .one();
        return Optional.ofNullable(one);
    }

    public StackTargetEntity save(StackTargetEntity entity) {
        dataSource.insert(entity);
        return entity;
    }

    public boolean updateById(StackTargetEntity entity) {
        int updated = dataSource.lambdaUpdate(StackTargetEntity.class)
            .set(StackTargetEntity::getEnabled, entity.getEnabled())
            .set(StackTargetEntity::getRoles, entity.getRoles())
            .set(StackTargetEntity::getWebPort, entity.getWebPort())
            .set(StackTargetEntity::getFrontedPort, entity.getFrontedPort())
            .set(StackTargetEntity::getLastDeployedVersion, entity.getLastDeployedVersion())
            .set(StackTargetEntity::getHealthStatus, entity.getHealthStatus())
            .set(StackTargetEntity::getLastHealthCheckTime, entity.getLastHealthCheckTime())
            .eq(StackTargetEntity::getId, entity.getId())
            .update();
        return updated > 0;
    }

    public boolean deleteByStackIdAndNodeId(String stackId, String nodeId) {
        int deleted = dataSource.lambdaUpdate(StackTargetEntity.class)
            .eq(StackTargetEntity::getStackId, stackId)
            .eq(StackTargetEntity::getNodeId, nodeId)
            .delete();
        return deleted > 0;
    }
}

