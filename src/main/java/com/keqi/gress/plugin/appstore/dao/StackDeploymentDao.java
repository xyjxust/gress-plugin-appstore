package com.keqi.gress.plugin.appstore.dao;

import com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.StackDeploymentEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StackDeploymentDao {

    @Autowired
    private PluginLambdaDataSource dataSource;

    public Optional<StackDeploymentEntity> findByDeploymentId(String deploymentId) {
        StackDeploymentEntity one = dataSource.lambdaQuery(StackDeploymentEntity.class)
            .eq(StackDeploymentEntity::getDeploymentId, deploymentId)
            .one();
        return Optional.ofNullable(one);
    }

    public List<StackDeploymentEntity> listByStackId(String stackId, int limit) {
        int size = Math.max(1, limit);
        return dataSource.lambdaQuery(StackDeploymentEntity.class)
            .eq(StackDeploymentEntity::getStackId, stackId)
            .orderByDesc(StackDeploymentEntity::getCreateTime)
            .page(1, size)
            .getRecords();
    }

    public StackDeploymentEntity save(StackDeploymentEntity entity) {
        dataSource.insert(entity);
        return entity;
    }

    public boolean updateById(StackDeploymentEntity entity) {
        int updated = dataSource.lambdaUpdate(StackDeploymentEntity.class)
            .set(StackDeploymentEntity::getRequestedVersion, entity.getRequestedVersion())
            .set(StackDeploymentEntity::getMode, entity.getMode())
            .set(StackDeploymentEntity::getStrategy, entity.getStrategy())
            .set(StackDeploymentEntity::getDeployFronted, entity.getDeployFronted())
            .set(StackDeploymentEntity::getJoinNginx, entity.getJoinNginx())
            .set(StackDeploymentEntity::getStatus, entity.getStatus())
            .set(StackDeploymentEntity::getMessage, entity.getMessage())
            .set(StackDeploymentEntity::getStartedAt, entity.getStartedAt())
            .set(StackDeploymentEntity::getEndedAt, entity.getEndedAt())
            .eq(StackDeploymentEntity::getId, entity.getId())
            .update();
        return updated > 0;
    }
}

