package com.keqi.gress.plugin.appstore.service.middleware.util;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.keqi.gress.plugin.appstore.service.middleware.execution.ExecutionEnvironment;
import com.keqi.gress.plugin.appstore.service.middleware.execution.ExecutionResult;
import com.keqi.gress.plugin.appstore.service.middleware.execution.LocalExecutionEnvironment;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 运行 docker compose（支持 DinD：透传 DOCKER_HOST/DOCKER_TLS_VERIFY/DOCKER_CERT_PATH 等环境变量）。
 *
 * 支持多种执行环境：本地、SSH、Docker API
 */
@Slf4j
public class DockerComposeRunner {
  //  private static final Log log = LogFactory.get(DockerComposeRunner.class);

    private final ExecutionEnvironment executionEnv;

    /**
     * SSE 日志回调接口
     */
    @FunctionalInterface
    public interface LogCallback {
        void log(String message);
    }

    private final LogCallback logCallback;
    
    /**
     * 使用指定的执行环境创建 DockerComposeRunner
     */
    public DockerComposeRunner(ExecutionEnvironment executionEnv) {
        this(executionEnv, null);
    }

    /**
     * 使用指定的执行环境和日志回调创建 DockerComposeRunner
     */
    public DockerComposeRunner(ExecutionEnvironment executionEnv, LogCallback logCallback) {
        this.executionEnv = executionEnv != null ? executionEnv : new LocalExecutionEnvironment();
        this.logCallback = logCallback;
    }

    /**
     * 使用本地执行环境创建 DockerComposeRunner（向后兼容）
     */
    public DockerComposeRunner() {
        this(new LocalExecutionEnvironment(), null);
    }

    /**
     * 发送日志（如果有回调）
     */
    private void sendLog(String message) {
        if (logCallback != null) {
            logCallback.log(message);
        }
    }

    public ResultExec execComposeUp(Path composeFile, String projectName) {
        return execComposeUp(composeFile, projectName, null);
    }
    
    public ResultExec execComposeUp(Path composeFile, String projectName, Map<String, String> envVars) {
        try {
            // 发送日志：开始执行docker compose up
            sendLog(String.format("开始执行docker compose up，项目名称: %s", projectName));

            // 1. 如果是远程环境，先上传 compose 文件
            String remoteComposePath = composeFile.toAbsolutePath().toString();
            if (!(executionEnv instanceof LocalExecutionEnvironment)) {
                remoteComposePath = "/tmp/docker-compose-" + System.currentTimeMillis() + ".yml";
                sendLog(String.format("上传docker-compose.yml到远程环境: %s", remoteComposePath));
                executionEnv.uploadFile(composeFile, remoteComposePath);
                log.debug("已上传 docker-compose.yml 到远程: {}", remoteComposePath);
            }
            
            // 2. 构建命令
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("compose");
        cmd.add("-f");
            cmd.add(remoteComposePath);
        if (projectName != null && !projectName.isBlank()) {
            cmd.add("-p");
            cmd.add(projectName);
        }
        cmd.add("up");
        cmd.add("-d");
            
            // 3. 执行命令
            sendLog(String.format("执行命令: %s", String.join(" ", cmd)));
            ExecutionResult result = executionEnv.executeCommand(cmd, envVars, Duration.ofMinutes(5));

            if (result.getExitCode() == 0) {
                sendLog("docker compose up 执行成功");
            } else {
                sendLog(String.format("docker compose up 执行失败，退出码: %d", result.getExitCode()));
            }

            return new ResultExec(result.getExitCode(), result.getOutput());

        } catch (Exception e) {
            log.error("执行 docker compose up 失败", e);
            sendLog("docker compose up 执行异常: " + e.getMessage());
            return new ResultExec(1, "执行异常: " + e.getMessage());
        }
    }
    
    public ResultExec execComposeDown(Path composeFile, String projectName, boolean removeVolumes) {
        return execComposeDown(composeFile, projectName, removeVolumes, null);
    }
    
    public ResultExec execComposeDown(Path composeFile, String projectName, boolean removeVolumes, Map<String, String> envVars) {
        try {
            // 发送日志：开始执行docker compose down
            sendLog(String.format("开始执行docker compose down，项目名称: %s%s", projectName,
                removeVolumes ? " (包含数据卷)" : ""));

            // 1. 如果是远程环境，需要找到远程 compose 文件路径
            // 注意：这里假设 compose 文件已经在远程（之前上传过）
            // 如果不存在，可能需要重新上传或使用固定路径
            String remoteComposePath = composeFile.toAbsolutePath().toString();
            if (!(executionEnv instanceof LocalExecutionEnvironment)) {
                // 尝试使用标准路径，如果不存在则上传
                remoteComposePath = "/tmp/docker-compose-" + System.currentTimeMillis() + ".yml";
                try {
                    sendLog(String.format("上传docker-compose.yml到远程环境: %s", remoteComposePath));
                    executionEnv.uploadFile(composeFile, remoteComposePath);
                } catch (Exception e) {
                    log.warn("上传 compose 文件失败，尝试使用默认路径", e);
                    sendLog("上传失败，使用默认路径: /tmp/docker-compose.yml");
                    remoteComposePath = "/tmp/docker-compose.yml";
                }
            }
            
            // 2. 构建命令
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("compose");
        cmd.add("-f");
            cmd.add(remoteComposePath);
        if (projectName != null && !projectName.isBlank()) {
            cmd.add("-p");
            cmd.add(projectName);
        }
        cmd.add("down");
        if (removeVolumes) {
            cmd.add("-v");
        }
            
            // 3. 执行命令
            sendLog(String.format("执行命令: %s", String.join(" ", cmd)));
            ExecutionResult result = executionEnv.executeCommand(cmd, envVars, Duration.ofMinutes(5));

            if (result.getExitCode() == 0) {
                sendLog("docker compose down 执行成功");
            } else {
                sendLog(String.format("docker compose down 执行失败，退出码: %d", result.getExitCode()));
            }

            return new ResultExec(result.getExitCode(), result.getOutput());

        } catch (Exception e) {
            log.error("执行 docker compose down 失败", e);
            sendLog("docker compose down 执行异常: " + e.getMessage());
            return new ResultExec(1, "执行异常: " + e.getMessage());
        }
    }

    public boolean dockerAvailable() {
        ExecutionResult result = executionEnv.executeCommand(
            Arrays.asList("docker", "info"), 
            null, 
            Duration.ofSeconds(10)
        );
        return result.isSuccess();
    }

    public boolean dockerComposeAvailable() {
        ExecutionResult result = executionEnv.executeCommand(
            Arrays.asList("docker", "compose", "version"), 
            null, 
            Duration.ofSeconds(10)
        );
        return result.isSuccess();
    }

    /**
     * 执行命令（向后兼容方法，使用执行环境）
     */
    public ResultExec exec(List<String> cmd, Duration timeout) {
        return exec(cmd, timeout, null);
    }
    
    /**
     * 执行命令（向后兼容方法，使用执行环境）
     */
    public ResultExec exec(List<String> cmd, Duration timeout, Map<String, String> additionalEnvVars) {
        ExecutionResult result = executionEnv.executeCommand(cmd, additionalEnvVars, timeout);
        return new ResultExec(result.getExitCode(), result.getOutput());
    }

    public static class ResultExec {
        public final int exitCode;
        public final String output;

        public ResultExec(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
