package com.keqi.gress.plugin.appstore.service.middleware.execution;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.keqi.gress.plugin.appstore.service.NodeManagementService;
import lombok.extern.slf4j.Slf4j;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Docker API 执行环境
 * 通过 Docker API（DOCKER_HOST）连接远程 Docker daemon
 * 
 * 注意：此实现通过设置 DOCKER_HOST 环境变量来使用远程 Docker，
 * 实际命令仍在本地执行，但会连接到远程 Docker daemon。
 */
@Slf4j
public class DockerApiExecutionEnvironment implements ExecutionEnvironment {
    
   //@Slf4j private static final Log log = LogFactory.get(DockerApiExecutionEnvironment.class);
    
    private final NodeManagementService.DockerApiNodeConfig config;
    
    public DockerApiExecutionEnvironment(NodeManagementService.DockerApiNodeConfig config) {
        this.config = config;
    }
    
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
            
            // 设置 Docker API 相关环境变量
            if (config.getDockerHost() != null) {
                env.put("DOCKER_HOST", config.getDockerHost());
            }
            if (config.getDockerCertPath() != null) {
                env.put("DOCKER_CERT_PATH", config.getDockerCertPath());
            }
            if (config.getDockerTlsVerify() != null) {
                env.put("DOCKER_TLS_VERIFY", config.getDockerTlsVerify() ? "1" : "0");
            }
            
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
                    log.warn("Docker API 命令执行失败: cmd={}, exitCode={}, error={}", 
                        command, exitCode, errorOutput);
                } else {
                    log.debug("Docker API 命令执行成功: cmd={}, output={}", command, output);
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
        // Docker API 环境不支持文件上传
        // 文件需要通过其他方式（如 SSH）上传，或使用 Docker volume
        throw new UnsupportedOperationException(
            "Docker API 环境不支持文件上传，请使用 SSH 环境或 Docker volume");
    }
    
    @Override
    public void downloadFile(String remotePath, Path localFile) throws java.io.IOException {
        // Docker API 环境不支持文件下载
        throw new UnsupportedOperationException(
            "Docker API 环境不支持文件下载，请使用 SSH 环境或 Docker volume");
    }
    
    @Override
    public boolean isAvailable() {
        // 测试 Docker 连接
        try {
            ExecutionResult result = executeCommand(
                List.of("docker", "info"), 
                null, 
                Duration.ofSeconds(10)
            );
            return result.isSuccess();
        } catch (Exception e) {
            log.warn("检查 Docker API 连接失败", e);
            return false;
        }
    }
    
    @Override
    public String getType() {
        return "docker-api";
    }
    
    @Override
    public String getIdentifier() {
        return config.getDockerHost() != null ? 
            config.getDockerHost() : "docker-api://unknown";
    }
}
