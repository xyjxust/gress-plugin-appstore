package com.keqi.gress.plugin.appstore.service.middleware.repository.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.MiddlewareServiceEntity;
import com.keqi.gress.plugin.appstore.service.middleware.repository.MiddlewareServiceRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 中间件服务 Repository 实现
 */
@Service
@Slf4j
public class MiddlewareServiceRepositoryImpl implements MiddlewareServiceRepository {
    
    //private static final Log log = LogFactory.get(MiddlewareServiceRepositoryImpl.class);
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginLambdaDataSource dataSource;
    
    @Override
    public void save(MiddlewareServiceEntity entity) {
        if (dataSource == null) {
            log.warn("数据源未配置，无法保存服务信息");
            return;
        }
        
        try {
            dataSource.insert(entity);
            log.debug("保存服务信息成功: serviceId={}", entity.getServiceId());
        } catch (Exception e) {
            log.error("保存服务信息失败: serviceId={}", entity.getServiceId(), e);
            throw new RuntimeException("保存服务信息失败", e);
        }
    }
    
    @Override
    public Optional<MiddlewareServiceEntity> findByServiceId(String serviceId) {
        if (dataSource == null) {
            return Optional.empty();
        }
        
        try {
            MiddlewareServiceEntity entity = dataSource.lambdaQuery(MiddlewareServiceEntity.class)
                .eq(MiddlewareServiceEntity::getServiceId, serviceId)
                .one();
            return Optional.ofNullable(entity);
        } catch (Exception e) {
            log.error("查询服务信息失败: serviceId={}", serviceId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<MiddlewareServiceEntity> findAll() {
        if (dataSource == null) {
            return new ArrayList<>();
        }
        
        try {
            return dataSource.lambdaQuery(MiddlewareServiceEntity.class)
                .orderByDesc(MiddlewareServiceEntity::getCreatedAt)
                .list();
        } catch (Exception e) {
            log.error("查询所有服务信息失败", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<MiddlewareServiceEntity> findByStatus(String status) {
        if (dataSource == null) {
            return new ArrayList<>();
        }
        
        try {
            return dataSource.lambdaQuery(MiddlewareServiceEntity.class)
                .eq(MiddlewareServiceEntity::getStatus, status)
                .orderByDesc(MiddlewareServiceEntity::getCreatedAt)
                .list();
        } catch (Exception e) {
            log.error("根据状态查询服务信息失败: status={}", status, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void update(MiddlewareServiceEntity entity) {
        if (dataSource == null) {
            return;
        }
        
        try {
            dataSource.updateById(entity);
            log.debug("更新服务信息成功: serviceId={}", entity.getServiceId());
        } catch (Exception e) {
            log.error("更新服务信息失败: serviceId={}", entity.getServiceId(), e);
            throw new RuntimeException("更新服务信息失败", e);
        }
    }
    
    @Override
    public void deleteByServiceId(String serviceId) {
        if (dataSource == null) {
            return;
        }
        
        try {
            dataSource.lambdaUpdate(MiddlewareServiceEntity.class)
                .eq(MiddlewareServiceEntity::getServiceId, serviceId)
                .delete();
            log.debug("删除服务信息成功: serviceId={}", serviceId);
        } catch (Exception e) {
            log.error("删除服务信息失败: serviceId={}", serviceId, e);
            throw new RuntimeException("删除服务信息失败", e);
        }
    }
    
    @Override
    public void incrementReferenceCount(String serviceId) {
        if (dataSource == null) {
            return;
        }
        
        try {
            Optional<MiddlewareServiceEntity> entityOpt = findByServiceId(serviceId);
            if (entityOpt.isPresent()) {
                MiddlewareServiceEntity entity = entityOpt.get();
                entity.setReferenceCount((entity.getReferenceCount() != null ? entity.getReferenceCount() : 0) + 1);
                update(entity);
            }
        } catch (Exception e) {
            log.error("增加服务引用计数失败: serviceId={}", serviceId, e);
            throw new RuntimeException("增加服务引用计数失败", e);
        }
    }
    
    @Override
    public void decrementReferenceCount(String serviceId) {
        if (dataSource == null) {
            return;
        }
        
        try {
            Optional<MiddlewareServiceEntity> entityOpt = findByServiceId(serviceId);
            if (entityOpt.isPresent()) {
                MiddlewareServiceEntity entity = entityOpt.get();
                int newCount = (entity.getReferenceCount() != null ? entity.getReferenceCount() : 0) - 1;
                entity.setReferenceCount(Math.max(0, newCount));
                update(entity);
            }
        } catch (Exception e) {
            log.error("减少服务引用计数失败: serviceId={}", serviceId, e);
            throw new RuntimeException("减少服务引用计数失败", e);
        }
    }
}
