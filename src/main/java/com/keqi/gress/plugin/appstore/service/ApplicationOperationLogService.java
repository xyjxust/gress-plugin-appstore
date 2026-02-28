package com.keqi.gress.plugin.appstore.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.dao.ApplicationOperationLogDao;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplicationOperationLog;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 应用操作日志服务
 */
@Service
@Slf4j
public class ApplicationOperationLogService {

   // private static final Log log = LogFactory.get(ApplicationOperationLogService.class);
    
    @Inject
    private ApplicationOperationLogDao operationLogDao;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 记录操作日志
     * 
     * @param application 应用信息
     * @param operationType 操作类型
     * @param operationDesc 操作描述
     * @param operatorId 操作人ID
     * @param operatorName 操作人名称
     * @param status 操作结果
     * @param message 补充信息
     * @param beforeData 操作前数据
     * @param afterData 操作后数据
     * @param duration 操作耗时
     */
    public void logOperation(
            SysApplication application,
            String operationType,
            String operationDesc,
            String operatorId,
            String operatorName,
            String status,
            String message,
            Object beforeData,
            Object afterData,
            Long duration) {
        
        try {
            SysApplicationOperationLog logEntry = new SysApplicationOperationLog();
            logEntry.setApplicationId(application.getId());
            logEntry.setApplicationCode(application.getApplicationCode());
            logEntry.setApplicationName(application.getApplicationName());
            logEntry.setPluginId(application.getPluginId());
            logEntry.setOperationType(operationType);
            logEntry.setOperationDesc(operationDesc);
            logEntry.setOperatorId(operatorId);
            logEntry.setOperatorName(operatorName);
            logEntry.setStatus(status);
            logEntry.setMessage(message);
            logEntry.setDuration(duration);
            logEntry.setCreateTime(LocalDateTime.now());
            
            // 序列化操作前后数据
            if (beforeData != null) {
                try {
                    logEntry.setBeforeData(objectMapper.writeValueAsString(beforeData));
                } catch (Exception e) {
                    log.warn("序列化操作前数据失败", e);
                }
            }
            
            if (afterData != null) {
                try {
                    logEntry.setAfterData(objectMapper.writeValueAsString(afterData));
                } catch (Exception e) {
                    log.warn("序列化操作后数据失败", e);
                }
            }
            
            operationLogDao.insert(logEntry);
            
            log.info("操作日志记录成功: appId={}, operationType={}, status={}", 
                    application.getId(), operationType, status);
            
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
            // 不抛出异常，避免影响主流程
        }
    }
    
    /**
     * 记录操作日志（简化版，无操作前后数据）
     */
    public void logOperation(
            SysApplication application,
            String operationType,
            String operationDesc,
            String operatorId,
            String operatorName,
            String status,
            String message) {
        
        logOperation(application, operationType, operationDesc, operatorId, operatorName, 
                status, message, null, null, null);
    }
    
    /**
     * 记录操作日志（带耗时）
     */
    public void logOperation(
            SysApplication application,
            String operationType,
            String operationDesc,
            String operatorId,
            String operatorName,
            String status,
            String message,
            Long duration) {
        
        logOperation(application, operationType, operationDesc, operatorId, operatorName, 
                status, message, null, null, duration);
    }
}
