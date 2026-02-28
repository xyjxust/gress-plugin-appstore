package com.keqi.gress.plugin.appstore.service.middleware.repository.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.MiddlewareDependencyEntity;
import com.keqi.gress.plugin.appstore.service.middleware.repository.MiddlewareDependencyRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 中间件依赖关系 Repository 实现
 */
@Service
@Slf4j
public class MiddlewareDependencyRepositoryImpl implements MiddlewareDependencyRepository {
    
   // private static final Log log = LogFactory.get(MiddlewareDependencyRepositoryImpl.class);
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginLambdaDataSource dataSource;
    
    @Override
    public void save(MiddlewareDependencyEntity entity) {
        if (dataSource == null) {
            log.warn("数据源未配置，无法保存依赖关系");
            return;
        }
        
        try {
            dataSource.insert(entity);
            log.debug("保存依赖关系成功: middlewareId={}, serviceId={}", 
                entity.getMiddlewareId(), entity.getServiceId());
        } catch (Exception e) {
            log.error("保存依赖关系失败: middlewareId={}, serviceId={}", 
                entity.getMiddlewareId(), entity.getServiceId(), e);
            throw new RuntimeException("保存依赖关系失败", e);
        }
    }
    
    @Override
    public List<MiddlewareDependencyEntity> findByMiddlewareId(String middlewareId) {
        if (dataSource == null) {
            return new ArrayList<>();
        }
        
        try {
            return dataSource.lambdaQuery(MiddlewareDependencyEntity.class)
                .eq(MiddlewareDependencyEntity::getMiddlewareId, middlewareId)
                .list();
        } catch (Exception e) {
            log.error("查询依赖关系失败: middlewareId={}", middlewareId, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<MiddlewareDependencyEntity> findByServiceId(String serviceId) {
        if (dataSource == null) {
            return new ArrayList<>();
        }
        
        try {
            return dataSource.lambdaQuery(MiddlewareDependencyEntity.class)
                .eq(MiddlewareDependencyEntity::getServiceId, serviceId)
                .list();
        } catch (Exception e) {
            log.error("查询依赖关系失败: serviceId={}", serviceId, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void deleteByMiddlewareId(String middlewareId) {
        if (dataSource == null) {
            return;
        }
        
        try {
            dataSource.lambdaUpdate(MiddlewareDependencyEntity.class)
                .eq(MiddlewareDependencyEntity::getMiddlewareId, middlewareId)
                .delete();
            log.debug("删除依赖关系成功: middlewareId={}", middlewareId);
        } catch (Exception e) {
            log.error("删除依赖关系失败: middlewareId={}", middlewareId, e);
            throw new RuntimeException("删除依赖关系失败", e);
        }
    }
    
    @Override
    public void delete(String middlewareId, String serviceId) {
        if (dataSource == null) {
            return;
        }
        
        try {
            dataSource.lambdaUpdate(MiddlewareDependencyEntity.class)
                .eq(MiddlewareDependencyEntity::getMiddlewareId, middlewareId)
                .eq(MiddlewareDependencyEntity::getServiceId, serviceId)
                .delete();
            log.debug("删除依赖关系成功: middlewareId={}, serviceId={}", middlewareId, serviceId);
        } catch (Exception e) {
            log.error("删除依赖关系失败: middlewareId={}, serviceId={}", middlewareId, serviceId, e);
            throw new RuntimeException("删除依赖关系失败", e);
        }
    }
}
