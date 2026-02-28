package com.keqi.gress.plugin.appstore.service.middleware.repository;

import com.keqi.gress.plugin.appstore.domain.entity.MiddlewareInfoEntity;

import java.util.List;
import java.util.Optional;

/**
 * 中间件信息 Repository 接口
 */
public interface MiddlewareInfoRepository {
    
    /**
     * 保存中间件信息
     */
    void save(MiddlewareInfoEntity entity);
    
    /**
     * 根据中间件ID查找
     */
    Optional<MiddlewareInfoEntity> findByMiddlewareId(String middlewareId);
    
    /**
     * 查找所有中间件
     */
    List<MiddlewareInfoEntity> findAll();
    
    /**
     * 根据状态查找
     */
    List<MiddlewareInfoEntity> findByStatus(String status);
    
    /**
     * 删除中间件
     */
    void deleteByMiddlewareId(String middlewareId);
    
    /**
     * 更新中间件信息
     */
    void update(MiddlewareInfoEntity entity);
}
