package com.keqi.gress.plugin.appstore.dao;

import com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.StackConfigEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StackConfigDao {

    @Autowired
    private PluginLambdaDataSource dataSource;

    public List<StackConfigEntity> listAll() {
        return dataSource.lambdaQuery(StackConfigEntity.class)
            .orderByAsc(StackConfigEntity::getStackId)
            .list();
    }

    public Optional<StackConfigEntity> findByStackId(String stackId) {
        StackConfigEntity one = dataSource.lambdaQuery(StackConfigEntity.class)
            .eq(StackConfigEntity::getStackId, stackId)
            .one();
        return Optional.ofNullable(one);
    }

    public StackConfigEntity save(StackConfigEntity entity) {
        dataSource.insert(entity);
        return entity;
    }

    public boolean updateById(StackConfigEntity entity) {
        int updated = dataSource.lambdaUpdate(StackConfigEntity.class)
            .set(StackConfigEntity::getName, entity.getName())
            .set(StackConfigEntity::getEnabled, entity.getEnabled())
            .set(StackConfigEntity::getMysqlDatabase, entity.getMysqlDatabase())
            .set(StackConfigEntity::getRedisDb, entity.getRedisDb())
            .set(StackConfigEntity::getRuntimeBaseDir, entity.getRuntimeBaseDir())
            .set(StackConfigEntity::getWebImage, entity.getWebImage())
            .set(StackConfigEntity::getFrontedImage, entity.getFrontedImage())
            .set(StackConfigEntity::getVersionTag, entity.getVersionTag())
            .set(StackConfigEntity::getDeployFronted, entity.getDeployFronted())
            .set(StackConfigEntity::getJoinNginx, entity.getJoinNginx())
            .set(StackConfigEntity::getEntryNodeId, entity.getEntryNodeId())
            .set(StackConfigEntity::getDomain, entity.getDomain())
            .set(StackConfigEntity::getWebHostPort, entity.getWebHostPort())
            .set(StackConfigEntity::getFrontedHostPort, entity.getFrontedHostPort())
            .set(StackConfigEntity::getExtraConfig, entity.getExtraConfig())
            .eq(StackConfigEntity::getId, entity.getId())
            .update();
        return updated > 0;
    }

    public boolean deleteByStackId(String stackId) {
        int deleted = dataSource.lambdaUpdate(StackConfigEntity.class)
            .eq(StackConfigEntity::getStackId, stackId)
            .delete();
        return deleted > 0;
    }
}

