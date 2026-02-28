package com.keqi.gress.plugin.appstore.service.logging;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import com.keqi.gress.plugin.appstore.service.ApplicationOperationLogService;

import java.util.Map;

/**
 * 应用操作日志记录器
 * 
 * 职责：
 * - 负责记录应用操作日志
 * - 遵循单一职责原则（SRP）：只负责日志记录
 * - 提供统一的日志记录接口
 * 
 * @author Gress Team
 */
@Service
public class ApplicationOperationLogger {
    
    private static final Log log = LogFactory.get(ApplicationOperationLogger.class);
    
    @Inject
    private ApplicationOperationLogService operationLogService;
    
    /**
     * 记录成功操作
     */
    public void logSuccess(SysApplication application, String operationType, 
                          String operationDesc, String operatorId, String operatorName, 
                          String message, long startTime) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            operationLogService.logOperation(application, operationType, operationDesc, 
                    operatorId, operatorName, "SUCCESS", message, duration);
        } catch (Exception e) {
            log.warn("记录操作日志失败", e);
        }
    }
    
    /**
     * 记录失败操作
     */
    public void logFailure(SysApplication application, String operationType, 
                          String operationDesc, String operatorId, String operatorName, 
                          String message, long startTime) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            operationLogService.logOperation(application, operationType, operationDesc, 
                    operatorId, operatorName, "FAIL", message, duration);
        } catch (Exception e) {
            log.warn("记录操作日志失败", e);
        }
    }
    
    /**
     * 记录配置更新操作（包含前后数据）
     */
    public void logConfigUpdate(SysApplication application, String operatorId, 
                               String operatorName, String status, String message, 
                               Map<String, Object> beforeData, Map<String, Object> afterData, 
                               long startTime) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            operationLogService.logOperation(application, "CONFIG_UPDATE", "更新应用配置", 
                    operatorId, operatorName, status, message, beforeData, afterData, duration);
        } catch (Exception e) {
            log.warn("记录配置更新日志失败", e);
        }
    }
}










