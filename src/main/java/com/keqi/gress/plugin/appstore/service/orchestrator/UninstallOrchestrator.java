package com.keqi.gress.plugin.appstore.service.orchestrator;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginPackageUninstallResult;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import com.keqi.gress.plugin.appstore.dto.ApplicationUninstallRequest;
import com.keqi.gress.plugin.appstore.service.ApplicationInstallService;
import com.keqi.gress.plugin.appstore.service.persistence.ApplicationPersistenceService;
import com.keqi.gress.plugin.appstore.service.logging.ApplicationOperationLogger;
import lombok.extern.slf4j.Slf4j;

/**
 * 卸载编排器
 * 
 * 职责：
 * - 协调完整的卸载流程
 * - 遵循单一职责原则（SRP）：只负责流程编排
 * - 遵循开闭原则（OCP）：通过依赖注入扩展功能
 * - 遵循依赖倒置原则（DIP）：依赖抽象接口
 * 
 * 卸载流程：
 * 1. 获取应用信息
 * 2. 检查是否可卸载（默认应用、集成应用不可卸载）
 * 3. 执行卸载
 * 4. 删除数据库记录
 * 5. 记录卸载日志
 * 
 * @author Gress Team
 */
@Service
@Slf4j
public class UninstallOrchestrator {
    
   // private static final Log log = LogFactory.get(UninstallOrchestrator.class);
    
    @Inject
    private ApplicationPersistenceService persistenceService;
    
    @Inject
    private ApplicationInstallService applicationInstallService;
    
    @Inject
    private ApplicationOperationLogger operationLogger;
    
    /**
     * 卸载应用
     * 
     * @param id 应用ID
     * @param request 卸载请求
     * @return 卸载结果
     */
    public Result<Void> uninstall(Long id, ApplicationUninstallRequest request) {
        long startTime = System.currentTimeMillis();
        SysApplication application = null;
        
        try {
            // 1. 获取应用信息
            application = persistenceService.findById(id);
            if (application == null) {
                return Result.error("应用不存在");
            }
            
            // 2. 检查是否为默认应用
            if (application.isDefaultApplication()) {
                operationLogger.logFailure(application, "UNINSTALL", "卸载应用", 
                        request.getOperatorId(), request.getOperatorName(), 
                        "默认应用不能卸载", startTime);
                return Result.error("默认应用不能卸载");
            }
            
            // 3. 检查是否为集成应用
            if (application.isIntegrated()) {
                operationLogger.logFailure(application, "UNINSTALL", "卸载应用", 
                        request.getOperatorId(), request.getOperatorName(), 
                        "集成应用不能卸载", startTime);
                return Result.error("集成应用不能卸载");
            }
            
            // 4. 调用插件生命周期管理器卸载
            String packageId = application.getPluginId();
            Result<PluginPackageUninstallResult> uninstallResult = 
                    applicationInstallService.uninstallApplication(packageId);
            
            if (!uninstallResult.isSuccess()) {
                operationLogger.logFailure(application, "UNINSTALL", "卸载应用", 
                        request.getOperatorId(), request.getOperatorName(), 
                        "卸载失败: " + uninstallResult.getErrorMessage(), startTime);
                return Result.error("卸载插件包失败: " + uninstallResult.getErrorMessage());
            }
            
            // 5. 从数据库删除应用记录
            boolean deleteSuccess = persistenceService.deleteById(id);
            
            if (deleteSuccess) {
                log.info("应用卸载成功: id={}, packageId={}, operator={}, reason={}", 
                        id, packageId, request.getOperatorName(), request.getReason());
                String message = request.getReason() != null && !request.getReason().isEmpty() 
                        ? "卸载成功，原因: " + request.getReason() 
                        : "卸载成功";
                operationLogger.logSuccess(application, "UNINSTALL", "卸载应用", 
                        request.getOperatorId(), request.getOperatorName(), message, startTime);
                return Result.success();
            } else {
                operationLogger.logFailure(application, "UNINSTALL", "卸载应用", 
                        request.getOperatorId(), request.getOperatorName(), 
                        "删除应用记录失败", startTime);
                return Result.error("删除应用记录失败");
            }
            
        } catch (Exception e) {
            log.error("应用卸载失败: id={}", id, e);
            if (application != null) {
                operationLogger.logFailure(application, "UNINSTALL", "卸载应用", 
                        request.getOperatorId(), request.getOperatorName(), 
                        "异常: " + e.getMessage(), startTime);
            }
            return Result.error("应用卸载失败: " + e.getMessage());
        }
    }
}










