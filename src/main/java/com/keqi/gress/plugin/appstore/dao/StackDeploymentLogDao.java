package com.keqi.gress.plugin.appstore.dao;

import com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.StackDeploymentLogEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StackDeploymentLogDao {

    @Autowired
    private PluginLambdaDataSource dataSource;

    public StackDeploymentLogEntity save(StackDeploymentLogEntity entity) {
        dataSource.insert(entity);
        return entity;
    }

    public List<StackDeploymentLogEntity> listByDeploymentId(String deploymentId, int limit) {
        int size = Math.max(1, limit);
        return dataSource.lambdaQuery(StackDeploymentLogEntity.class)
            .eq(StackDeploymentLogEntity::getDeploymentId, deploymentId)
            .orderByDesc(StackDeploymentLogEntity::getTimestamp)
            .page(1, size)
            .getRecords();
    }
}

