package com.keqi.gress.plugin.appstore.service.middleware.workflow;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 工作流定义解析器
 */
public class WorkflowDefinitionParser {
    
    private static final Log log = LogFactory.get(WorkflowDefinitionParser.class);
    
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    /**
     * 从插件包中解析工作流定义
     */
    public WorkflowDefinition parse(Path middlewarePackage) {
        try (JarFile jarFile = new JarFile(middlewarePackage.toFile())) {
            // 查找工作流定义文件（按优先级）
            JarEntry entry = findWorkflowFile(jarFile);
            
            if (entry == null) {
                throw new IllegalArgumentException("未找到工作流定义文件（install-workflow.yml, install-workflow.yaml, workflow.yml）");
            }
            
            try (InputStream in = jarFile.getInputStream(entry)) {
                WorkflowDefinitionDTO dto = yamlMapper.readValue(
                    in, WorkflowDefinitionDTO.class);
                return convertToWorkflowDefinition(dto);
            }
            
        } catch (Exception e) {
            log.error("解析工作流定义失败", e);
            throw new RuntimeException("解析工作流定义失败: " + e.getMessage(), e);
        }
    }
    
    private JarEntry findWorkflowFile(JarFile jarFile) {
        // 按优先级查找
        String[] candidates = {
            "install-workflow.yml",
            "install-workflow.yaml",
            "workflow.yml",
            "workflow.yaml"
        };
        
        for (String candidate : candidates) {
            JarEntry entry = jarFile.getJarEntry(candidate);
            if (entry != null) {
                log.info("找到工作流定义文件: {}", candidate);
                return entry;
            }
        }
        
        return null;
    }
    
    private WorkflowDefinition convertToWorkflowDefinition(WorkflowDefinitionDTO dto) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName(dto.getName());
        definition.setVersion(dto.getVersion());
        definition.setDescription(dto.getDescription());
        definition.setConfigClass(dto.getConfigClass());
        
        // 转换安装步骤
        if (dto.getSteps() != null) {
            definition.setSteps(convertSteps(dto.getSteps()));
        }
        
        // 转换卸载步骤
        if (dto.getUninstall() != null && dto.getUninstall().getSteps() != null) {
            definition.setUninstallSteps(convertSteps(dto.getUninstall().getSteps()));
        }
        
        return definition;
    }
    
    private List<WorkflowStep> convertSteps(List<Map<String, Object>> stepsData) {
        List<WorkflowStep> steps = new ArrayList<>();
        
        for (Map<String, Object> stepData : stepsData) {
            WorkflowStep step = new WorkflowStep();
            step.setId((String) stepData.get("id"));
            step.setType((String) stepData.get("type"));
            step.setName((String) stepData.get("name"));
            
            // 解析配置
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) stepData.get("config");
            step.setConfig(config);
            
            // 解析错误处理策略
            String onErrorStr = (String) stepData.get("on-error");
            if (onErrorStr != null) {
                try {
                    step.setOnError(ErrorHandlingStrategy.valueOf(onErrorStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("未知的错误处理策略: {}, 使用默认值 STOP", onErrorStr);
                    step.setOnError(ErrorHandlingStrategy.STOP);
                }
            }
            
            steps.add(step);
        }
        
        return steps;
    }
    
    /**
     * 工作流定义 DTO（用于 YAML 解析）
     */
    @SuppressWarnings("unused")
    private static class WorkflowDefinitionDTO {
        private String name;
        private String version;
        private String description;
        private String configClass;
        private List<Map<String, Object>> steps;
        private UninstallDTO uninstall;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getConfigClass() { return configClass; }
        public void setConfigClass(String configClass) { this.configClass = configClass; }
        
        public List<Map<String, Object>> getSteps() { return steps; }
        public void setSteps(List<Map<String, Object>> steps) { this.steps = steps; }
        
        public UninstallDTO getUninstall() { return uninstall; }
        public void setUninstall(UninstallDTO uninstall) { this.uninstall = uninstall; }
        
        private static class UninstallDTO {
            private List<Map<String, Object>> steps;
            
            public List<Map<String, Object>> getSteps() { return steps; }
            public void setSteps(List<Map<String, Object>> steps) { this.steps = steps; }
        }
    }
}
