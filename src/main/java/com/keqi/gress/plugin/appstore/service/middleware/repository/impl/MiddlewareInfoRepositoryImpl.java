package com.keqi.gress.plugin.appstore.service.middleware.repository.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.MiddlewareInfoEntity;
import com.keqi.gress.plugin.appstore.service.middleware.repository.MiddlewareInfoRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 中间件信息 Repository 实现
 */
@Service
@Slf4j
public class MiddlewareInfoRepositoryImpl implements MiddlewareInfoRepository {
    
   // private static final Log log = LogFactory.get(MiddlewareInfoRepositoryImpl.class);
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginLambdaDataSource dataSource;
    
    @Override
    public void save(MiddlewareInfoEntity entity) {
        if (dataSource == null) {
            log.warn("数据源未配置，无法保存中间件信息");
            return;
        }
        
        try {
            dataSource.insert(entity);
            log.debug("保存中间件信息成功: middlewareId={}", entity.getMiddlewareId());
        } catch (Exception e) {
            log.error("保存中间件信息失败: middlewareId={}", entity.getMiddlewareId(), e);
            throw new RuntimeException("保存中间件信息失败", e);
        }
    }
    
    @Override
    public Optional<MiddlewareInfoEntity> findByMiddlewareId(String middlewareId) {
        if (dataSource == null) {
            return Optional.empty();
        }
        
        try {
            MiddlewareInfoEntity entity = dataSource.lambdaQuery(MiddlewareInfoEntity.class)
                .eq(MiddlewareInfoEntity::getMiddlewareId, middlewareId)
                .one();
            return Optional.ofNullable(entity);
        } catch (Exception e) {
            log.error("查询中间件信息失败: middlewareId={}", middlewareId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<MiddlewareInfoEntity> findAll() {
        if (dataSource == null) {
            return new ArrayList<>();
        }
        
        try {
            return dataSource.lambdaQuery(MiddlewareInfoEntity.class)
                .orderByDesc(MiddlewareInfoEntity::getInstalledAt)
                .list();
        } catch (Exception e) {
            log.error("查询所有中间件信息失败", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<MiddlewareInfoEntity> findByStatus(String status) {
        if (dataSource == null) {
            return new ArrayList<>();
        }
        
        try {
            return dataSource.lambdaQuery(MiddlewareInfoEntity.class)
                .eq(MiddlewareInfoEntity::getStatus, status)
                .orderByDesc(MiddlewareInfoEntity::getInstalledAt)
                .list();
        } catch (Exception e) {
            log.error("根据状态查询中间件信息失败: status={}", status, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void deleteByMiddlewareId(String middlewareId) {
        if (dataSource == null) {
            return;
        }
        
        try {
            dataSource.lambdaUpdate(MiddlewareInfoEntity.class)
                .eq(MiddlewareInfoEntity::getMiddlewareId, middlewareId)
                .delete();
            log.debug("删除中间件信息成功: middlewareId={}", middlewareId);
        } catch (Exception e) {
            log.error("删除中间件信息失败: middlewareId={}", middlewareId, e);
            throw new RuntimeException("删除中间件信息失败", e);
        }
    }
    
    @Override
    public void update(MiddlewareInfoEntity entity) {
        if (dataSource == null) {
            return;
        }
        
        try {
            dataSource.updateById(entity);
            log.debug("更新中间件信息成功: middlewareId={}", entity.getMiddlewareId());
        } catch (Exception e) {
            log.error("更新中间件信息失败: middlewareId={}", entity.getMiddlewareId(), e);
            throw new RuntimeException("更新中间件信息失败", e);
        }
    }
}
