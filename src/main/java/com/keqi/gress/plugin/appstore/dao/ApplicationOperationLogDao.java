package com.keqi.gress.plugin.appstore.dao;

import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.plugin.api.database.page.IPage;
import  com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplicationOperationLog;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用操作日志 DAO
 */
@Slf4j
@Service
public class ApplicationOperationLogDao {

    @Inject(source = Inject.BeanSource.SPRING)
    private PluginLambdaDataSource dataSource;

    /**
     * 保存操作日志
     */
    public int insert(SysApplicationOperationLog logEntry) {
        try {
            return dataSource.insert(logEntry);
        } catch (Exception e) {
            log.error("保存应用操作日志失败", e);
            return 0;
        }
    }

    /**
     * 根据应用ID查询操作日志，按时间倒序
     */
    public java.util.List<SysApplicationOperationLog> findByApplicationId(Long applicationId) {
        return dataSource.lambdaQuery(SysApplicationOperationLog.class)
                .eq(SysApplicationOperationLog::getApplicationId, applicationId)
                .orderByDesc(SysApplicationOperationLog::getCreateTime)
                .list();
    }

    /**
     * 分页查询操作日志
     */
    public IPage<SysApplicationOperationLog> queryPage(
            Integer page,
            Integer size,
            Long applicationId,
            String operationType,
            String operatorName) {
        
        var query = dataSource.lambdaQuery(SysApplicationOperationLog.class);
        
        if (applicationId != null) {
            query.eq(SysApplicationOperationLog::getApplicationId, applicationId);
        }
        
        if (operationType != null && !operationType.isEmpty()) {
            query.eq(SysApplicationOperationLog::getOperationType, operationType);
        }
        
        if (operatorName != null && !operatorName.isEmpty()) {
            query.like(SysApplicationOperationLog::getOperatorName, operatorName);
        }
        
        return query.orderByDesc(SysApplicationOperationLog::getCreateTime)
                .page(page, size);
    }
    
    /**
     * 分页查询操作日志（支持更多过滤条件）
     */
    public IPage<SysApplicationOperationLog> queryPageWithFilters(
            Integer page,
            Integer size,
            Long applicationId,
            String operationType,
            String operatorName,
            String applicationName,
            String status) {
        
        var query = dataSource.lambdaQuery(SysApplicationOperationLog.class);
        
        if (applicationId != null) {
            query.eq(SysApplicationOperationLog::getApplicationId, applicationId);
        }
        
        if (operationType != null && !operationType.isEmpty()) {
            query.eq(SysApplicationOperationLog::getOperationType, operationType);
        }
        
        if (operatorName != null && !operatorName.isEmpty()) {
            query.like(SysApplicationOperationLog::getOperatorName, operatorName);
        }
        
        if (applicationName != null && !applicationName.isEmpty()) {
            query.like(SysApplicationOperationLog::getApplicationName, applicationName);
        }
        
        if (status != null && !status.isEmpty()) {
            query.eq(SysApplicationOperationLog::getStatus, status);
        }
        
        return query.orderByDesc(SysApplicationOperationLog::getCreateTime)
                .page(page, size);
    }
}
