package com.keqi.gress.plugin.appstore.service.middleware.workflow;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流定义
 */
@Data
public class WorkflowDefinition {
    /**
     * 工作流名称
     */
    private String name;
    
    /**
     * 工作流版本
     */
    private String version;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 配置类全限定名（用于动态表单渲染）
     */
    private String configClass;
    
    /**
     * 安装步骤列表
     */
    private List<WorkflowStep> steps = new ArrayList<>();
    
    /**
     * 卸载步骤列表
     */
    private List<WorkflowStep> uninstallSteps = new ArrayList<>();
    
    /**
     * 获取安装步骤（兼容性方法）
     */
    public List<WorkflowStep> getInstallSteps() {
        return steps;
    }
}
