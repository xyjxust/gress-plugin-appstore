package com.keqi.gress.plugin.appstore.service.middleware.repository;

import com.keqi.gress.plugin.appstore.domain.entity.MiddlewareServiceEntity;

import java.util.List;
import java.util.Optional;

/**
 * 中间件服务 Repository 接口
 */
public interface MiddlewareServiceRepository {
    
    /**
     * 保存服务信息
     */
    void save(MiddlewareServiceEntity entity);
    
    /**
     * 根据服务ID查找
     */
    Optional<MiddlewareServiceEntity> findByServiceId(String serviceId);
    
    /**
     * 查找所有服务
     */
    List<MiddlewareServiceEntity> findAll();
    
    /**
     * 根据状态查找
     */
    List<MiddlewareServiceEntity> findByStatus(String status);
    
    /**
     * 更新服务信息
     */
    void update(MiddlewareServiceEntity entity);
    
    /**
     * 删除服务
     */
    void deleteByServiceId(String serviceId);
    
    /**
     * 增加引用计数
     */
    void incrementReferenceCount(String serviceId);
    
    /**
     * 减少引用计数
     */
    void decrementReferenceCount(String serviceId);
}
