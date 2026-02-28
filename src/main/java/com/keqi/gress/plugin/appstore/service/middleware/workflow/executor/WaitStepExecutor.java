package com.keqi.gress.plugin.appstore.service.middleware.workflow.executor;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.MiddlewareInstallContext;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStep;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStepExecutor;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStepResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 等待步骤执行器
 */
@Slf4j
public class WaitStepExecutor implements WorkflowStepExecutor {
    
   // private static final Log log = LogFactory.get(WaitStepExecutor.class);
    
    @Override
    public String getStepType() {
        return "wait";
    }
    
    @Override
    public WorkflowStepResult execute(WorkflowStep step, MiddlewareInstallContext ctx) {
        try {
            WaitStepConfig config = parseConfig(step.getConfig());
            int duration = config.getDuration();
            
            log.info("等待 {} 秒...", duration);
            Thread.sleep(duration * 1000L);
            
            return WorkflowStepResult.success(step.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WorkflowStepResult.failure(step.getId(), "等待被中断");
        } catch (Exception e) {
            log.error("执行等待步骤失败", e);
            return WorkflowStepResult.failure(step.getId(), 
                "执行异常: " + e.getMessage());
        }
    }
    
    private WaitStepConfig parseConfig(Map<String, Object> config) {
        WaitStepConfig result = new WaitStepConfig();
        if (config == null) {
            return result;
        }
        
        Object durationObj = config.get("duration");
        if (durationObj instanceof Number) {
            result.setDuration(((Number) durationObj).intValue());
        } else {
            result.setDuration(30); // 默认30秒
        }
        
        return result;
    }
    
    /**
     * 等待步骤配置
     */
    private static class WaitStepConfig {
        private int duration = 30;
        
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
    }
}
