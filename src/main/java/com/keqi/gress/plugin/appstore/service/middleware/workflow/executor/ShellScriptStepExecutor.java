package com.keqi.gress.plugin.appstore.service.middleware.workflow.executor;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.MiddlewareInstallContext;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStep;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStepExecutor;
import com.keqi.gress.plugin.appstore.service.middleware.workflow.WorkflowStepResult;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shell 脚本步骤执行器
 */
@Slf4j
public class ShellScriptStepExecutor implements WorkflowStepExecutor {
    
   // private static final Log log = LogFactory.get(ShellScriptStepExecutor.class);
    
    @Override
    public String getStepType() {
        return "shell-script";
    }
    
    @Override
    public WorkflowStepResult execute(WorkflowStep step, MiddlewareInstallContext ctx) {
        try {
            ShellScriptStepConfig config = parseConfig(step.getConfig());
            
            // 1. 提取脚本文件
            Path workDir = ctx.getWorkDir();
            Path scriptPath = extractScript(
                ctx.getMiddlewarePackage(), 
                workDir, 
                config.getScript());
            
            if (scriptPath == null || !Files.exists(scriptPath)) {
                return WorkflowStepResult.failure(
                    step.getId(), "未找到脚本文件: " + config.getScript());
            }
            
            // 2. 设置执行权限
            makeExecutable(scriptPath);
            
            // 3. 准备环境变量
            Map<String, String> env = new HashMap<>();
            
            // 系统环境变量
            env.putAll(System.getenv());
            
            // 工作流上下文变量
            env.put("MIDDLEWARE_ID", ctx.getMiddlewareId());
            env.put("MIDDLEWARE_VERSION", ctx.getVersion() != null ? ctx.getVersion() : "");
            env.put("WORK_DIR", workDir.toString());
            
            // 步骤配置的环境变量
            if (config.getEnv() != null) {
                env.putAll(config.getEnv());
            }
            
            // Docker 环境变量（支持 DinD）
            passThroughDockerEnv(env);
            
            // 4. 执行脚本
            ProcessBuilder pb = new ProcessBuilder(scriptPath.toString());
            pb.directory(config.getWorkingDir() != null ? 
                Paths.get(config.getWorkingDir()).toFile() : workDir.toFile());
            pb.environment().putAll(env);
            
            Process process = pb.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                
                // 读取标准输出
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("脚本输出: {}", line);
                }
                
                // 读取错误输出
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    log.warn("脚本错误输出: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                String errorMsg = String.format("脚本执行失败，退出码: %d, 输出: %s, 错误: %s", 
                    exitCode, output.toString(), errorOutput.toString());
                return WorkflowStepResult.failure(step.getId(), errorMsg);
            }
            
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("output", output.toString());
            resultData.put("exitCode", exitCode);
            
            return WorkflowStepResult.success(step.getId(), resultData);
            
        } catch (Exception e) {
            log.error("执行 Shell 脚本步骤失败", e);
            return WorkflowStepResult.failure(step.getId(), 
                "执行异常: " + e.getMessage());
        }
    }
    
    private Path extractScript(Path middlewarePackage, Path workDir, String scriptPath) {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(middlewarePackage.toFile())) {
            java.util.jar.JarEntry entry = jarFile.getJarEntry(scriptPath);
            if (entry == null) {
                log.warn("脚本文件不存在: {}", scriptPath);
                return null;
            }
            
            Files.createDirectories(workDir);
            Path targetPath = workDir.resolve(Paths.get(scriptPath).getFileName());
            
            try (InputStream in = jarFile.getInputStream(entry)) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            log.info("已提取脚本文件: {} -> {}", scriptPath, targetPath);
            return targetPath;
            
        } catch (Exception e) {
            log.error("提取脚本失败", e);
            return null;
        }
    }
    
    private void makeExecutable(Path scriptPath) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(scriptPath);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(scriptPath, perms);
            log.debug("已设置脚本执行权限: {}", scriptPath);
        } catch (Exception e) {
            // Windows 系统可能不支持，忽略
            log.debug("设置执行权限失败（可能是不支持的系统）: {}", e.getMessage());
        }
    }
    
    private void passThroughDockerEnv(Map<String, String> env) {
        String[] dockerEnvVars = {
            "DOCKER_HOST", "DOCKER_TLS_VERIFY", 
            "DOCKER_CERT_PATH", "DOCKER_CONTEXT"
        };
        for (String var : dockerEnvVars) {
            String value = System.getenv(var);
            if (value != null) {
                env.put(var, value);
            }
        }
    }
    
    private ShellScriptStepConfig parseConfig(Map<String, Object> config) {
        ShellScriptStepConfig result = new ShellScriptStepConfig();
        if (config == null) {
            return result;
        }
        
        result.setScript((String) config.get("script"));
        result.setWorkingDir((String) config.get("working-dir"));
        
        // 解析环境变量
        Object envObj = config.get("env");
        if (envObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envMap = (Map<String, Object>) envObj;
            Map<String, String> env = new HashMap<>();
            for (Map.Entry<String, Object> entry : envMap.entrySet()) {
                env.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            result.setEnv(env);
        }
        
        return result;
    }
    
    /**
     * Shell 脚本步骤配置
     */
    private static class ShellScriptStepConfig {
        private String script;
        private String workingDir;
        private Map<String, String> env;
        
        public String getScript() { return script; }
        public void setScript(String script) { this.script = script; }
        
        public String getWorkingDir() { return workingDir; }
        public void setWorkingDir(String workingDir) { this.workingDir = workingDir; }
        
        public Map<String, String> getEnv() { return env; }
        public void setEnv(Map<String, String> env) { this.env = env; }
    }
}
