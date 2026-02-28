package com.keqi.gress.plugin.appstore.service.middleware.repository;

import com.keqi.gress.plugin.appstore.domain.entity.MiddlewareDependencyEntity;

import java.util.List;

/**
 * 中间件依赖关系 Repository 接口
 */
public interface MiddlewareDependencyRepository {
    
    /**
     * 保存依赖关系
     */
    void save(MiddlewareDependencyEntity entity);
    
    /**
     * 根据中间件ID查找所有依赖
     */
    List<MiddlewareDependencyEntity> findByMiddlewareId(String middlewareId);
    
    /**
     * 根据服务ID查找所有依赖该服务的中间件
     */
    List<MiddlewareDependencyEntity> findByServiceId(String serviceId);
    
    /**
     * 删除中间件的所有依赖关系
     */
    void deleteByMiddlewareId(String middlewareId);
    
    /**
     * 删除特定的依赖关系
     */
    void delete(String middlewareId, String serviceId);
}
