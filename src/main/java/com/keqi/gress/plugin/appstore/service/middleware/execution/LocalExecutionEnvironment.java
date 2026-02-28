package com.keqi.gress.plugin.appstore.service.middleware.execution;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 本地执行环境
 * 使用 ProcessBuilder 在本地执行命令
 */
@Slf4j
public class LocalExecutionEnvironment implements ExecutionEnvironment {
    
  //  private static final Log log = LogFactory.get(LocalExecutionEnvironment.class);
    
    @Override
    public ExecutionResult executeCommand(List<String> command, Map<String, String> envVars, Duration timeout) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            
            // 设置环境变量
            Map<String, String> env = pb.environment();
            if (envVars != null) {
                env.putAll(envVars);
            }
            
            // 透传 Docker 相关环境变量
            passThroughDockerEnv(env);
            
            Process process = pb.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                
                // 异步读取输出
                Thread outputThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    } catch (Exception e) {
                        log.error("读取标准输出失败", e);
                    }
                });
                
                Thread errorThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            errorOutput.append(line).append("\n");
                        }
                    } catch (Exception e) {
                        log.error("读取错误输出失败", e);
                    }
                });
                
                outputThread.start();
                errorThread.start();
                
                // 等待进程结束或超时
                boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                
                if (!finished) {
                    process.destroyForcibly();
                    return ExecutionResult.builder()
                        .exitCode(124)
                        .output(output.toString())
                        .errorOutput(errorOutput + "\n<timeout>")
                        .build();
                }
                
                outputThread.join(1000);
                errorThread.join(1000);
                
                int exitCode = process.exitValue();
                
                if (exitCode != 0) {
                    log.warn("命令执行失败: cmd={}, exitCode={}, error={}", 
                        command, exitCode, errorOutput);
                } else {
                    log.debug("命令执行成功: cmd={}, output={}", command, output);
                }
                
                return ExecutionResult.builder()
                    .exitCode(exitCode)
                    .output(output.toString())
                    .errorOutput(errorOutput.toString())
                    .build();
            }
            
        } catch (Exception e) {
            log.error("执行命令失败: cmd={}", command, e);
            return ExecutionResult.builder()
                .exitCode(1)
                .output("")
                .errorOutput("执行异常: " + e.getMessage())
                .build();
        }
    }
    
    @Override
    public void uploadFile(Path localFile, String remotePath) throws java.io.IOException {
        // 本地环境，直接复制文件
        Path targetPath = Path.of(remotePath);
        Files.createDirectories(targetPath.getParent());
        Files.copy(localFile, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.debug("本地文件复制: {} -> {}", localFile, targetPath);
    }
    
    @Override
    public void downloadFile(String remotePath, Path localFile) throws java.io.IOException {
        // 本地环境，直接复制文件
        Path sourcePath = Path.of(remotePath);
        Files.createDirectories(localFile.getParent());
        Files.copy(sourcePath, localFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.debug("本地文件复制: {} -> {}", sourcePath, localFile);
    }
    
    @Override
    public boolean isAvailable() {
        // 本地环境始终可用
        return true;
    }
    
    @Override
    public String getType() {
        return "local";
    }
    
    @Override
    public String getIdentifier() {
        return "localhost";
    }
    
    private void passThroughDockerEnv(Map<String, String> env) {
        String[] dockerEnvVars = {
            "DOCKER_HOST", "DOCKER_TLS_VERIFY", 
            "DOCKER_CERT_PATH", "DOCKER_CONTEXT"
        };
        for (String var : dockerEnvVars) {
            String value = System.getenv(var);
            if (value != null && !value.isBlank()) {
                env.put(var, value);
            }
        }
    }
}
