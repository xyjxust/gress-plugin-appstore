package com.keqi.gress.plugin.appstore.service.middleware.execution;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.utils.WorkspaceDirectoryUtils;

import com.jcraft.jsch.*;
import com.keqi.gress.plugin.appstore.service.NodeManagementService;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * SSH 执行环境
 * 通过 SSH 连接远程服务器执行命令
 *
 * 注意：SSH 会话会在多次调用之间复用，以提高性能。
 * 如果需要关闭连接，可以调用 close() 方法。
 */
@Slf4j
public class SshExecutionEnvironment implements ExecutionEnvironment {

    /**
     * 日志回调接口
     */
    @FunctionalInterface
    public interface LogCallback {
        void log(String message);
    }

   // private static final Log log = LogFactory.get(SshExecutionEnvironment.class);

    private final NodeManagementService.SshNodeConfig config;
    private final LogCallback logCallback;
    private Session session;
    
    public SshExecutionEnvironment(NodeManagementService.SshNodeConfig config) {
        this(config, null);
    }

    public SshExecutionEnvironment(NodeManagementService.SshNodeConfig config, LogCallback logCallback) {
        this.config = config;
        this.logCallback = logCallback;
    }

    /**
     * 发送日志（如果有回调）
     */
    private void sendLog(String message) {
        if (logCallback != null) {
            logCallback.log(message);
        }
    }

    /**
     * 从缓冲区中提取完整的行并发送日志
     * 
     * @param buffer 累积的字符串缓冲区
     * @param isStderr 是否为stderr输出
     */
    private void sendLinesFromBuffer(StringBuilder buffer, boolean isStderr) {
        if (buffer.length() == 0) {
            return;
        }
        
        String content = buffer.toString();
        int lastNewline = content.lastIndexOf('\n');
        
        if (lastNewline >= 0) {
            // 提取所有完整的行（包括最后一个换行符之前的内容）
            String lines = content.substring(0, lastNewline + 1);
            
            // 发送每一行
            String[] lineArray = lines.split("\n", -1);
            for (int i = 0; i < lineArray.length - 1; i++) { // 最后一个是空字符串（因为split的-1参数）
                String line = lineArray[i];
                if (!line.isEmpty() || i < lineArray.length - 2) { // 允许空行，但不发送最后一个空字符串
                    if (isStderr) {
                        sendLog("[stderr] " + line);
                    } else {
                        sendLog(line);
                    }
                }
            }
            
            // 保留最后一个不完整的行（如果有）
            if (lastNewline < content.length() - 1) {
                buffer.setLength(0);
                buffer.append(content.substring(lastNewline + 1));
            } else {
                buffer.setLength(0);
            }
        }
        // 如果没有换行符，保留在缓冲区中等待更多数据
    }
    
    @Override
    public ExecutionResult executeCommand(List<String> command, Map<String, String> envVars, Duration timeout) {
        // 发送日志：开始执行SSH命令
        sendLog(String.format("SSH执行命令: %s", String.join(" ", command)));

        ensureConnected();
        Session session = getSession();
        if (session == null || !session.isConnected()) {
            sendLog("SSH连接失败");
            return ExecutionResult.builder()
                .exitCode(1)
                .output("")
                .errorOutput("SSH 连接失败")
                .build();
        }
        
        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            
            // 构建完整命令
            String fullCommand = String.join(" ", command);
            
            // 设置环境变量
            if (envVars != null && !envVars.isEmpty()) {
                StringBuilder envPrefix = new StringBuilder();
                for (Map.Entry<String, String> entry : envVars.entrySet()) {
                    envPrefix.append(entry.getKey())
                        .append("='")
                        .append(entry.getValue().replace("'", "'\\''"))
                        .append("' ");
                }
                fullCommand = envPrefix.toString() + fullCommand;
            }
            
            channel.setCommand(fullCommand);
            channel.setInputStream(null);
            
            // 将输出写入工作空间日志目录，避免全部缓存在内存中
            Path logsBaseDir = WorkspaceDirectoryUtils.getLogsDirectory();
            Path logsDir = logsBaseDir.resolve("ssh");
            
            // 确保父目录和子目录都存在（createDirectories 会创建所有不存在的父目录）
            try {
                Files.createDirectories(logsDir);
            } catch (IOException e) {
                log.error("创建SSH日志目录失败: {}", logsDir, e);
                sendLog("错误: 无法创建SSH日志目录: " + e.getMessage());
                throw new IOException("无法创建SSH日志目录: " + logsDir, e);
            }
            
            String baseName = String.format("ssh-%s-%d",
                config.getHost() != null ? config.getHost().replaceAll("[^a-zA-Z0-9_.-]", "_") : "unknown",
                System.currentTimeMillis());
            Path stdoutFile = logsDir.resolve(baseName + "-stdout.log");
            Path stderrFile = logsDir.resolve(baseName + "-stderr.log");
            log.debug("SSH 命令输出重定向到文件: stdout={}, stderr={}", stdoutFile, stderrFile);
            
            try (InputStream in = channel.getInputStream();
                 InputStream err = channel.getErrStream();
                 OutputStream outFile = Files.newOutputStream(stdoutFile, 
                     StandardOpenOption.CREATE, 
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE);
                 OutputStream errFile = Files.newOutputStream(stderrFile,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE)) {
                
                channel.connect((int) timeout.toMillis());
                
                // 用于按行累积输出并实时发送到SSE
                StringBuilder stdoutBuffer = new StringBuilder();
                StringBuilder stderrBuffer = new StringBuilder();
                
                byte[] buffer = new byte[1024];
                while (true) {
                    // 读取stdout
                    while (in.available() > 0) {
                        int bytesRead = in.read(buffer, 0, buffer.length);
                        if (bytesRead < 0) break;
                        
                        // 写入文件（如果失败，继续处理SSE日志）
                        try {
                            outFile.write(buffer, 0, bytesRead);
                        } catch (IOException e) {
                            log.warn("写入stdout日志文件失败，继续通过SSE输出: {}", e.getMessage());
                        }
                        
                        // 将字节转换为字符串并累积（使用UTF-8编码）
                        String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        stdoutBuffer.append(chunk);
                        
                        // 按行发送日志
                        sendLinesFromBuffer(stdoutBuffer, false);
                    }
                    
                    // 读取stderr
                    while (err.available() > 0) {
                        int bytesRead = err.read(buffer, 0, buffer.length);
                        if (bytesRead < 0) break;
                        
                        // 写入文件（如果失败，继续处理SSE日志）
                        try {
                            errFile.write(buffer, 0, bytesRead);
                        } catch (IOException e) {
                            log.warn("写入stderr日志文件失败，继续通过SSE输出: {}", e.getMessage());
                        }
                        
                        // 将字节转换为字符串并累积（使用UTF-8编码）
                        String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                        stderrBuffer.append(chunk);
                        
                        // 按行发送日志（stderr作为错误输出）
                        sendLinesFromBuffer(stderrBuffer, true);
                    }
                    
                    if (channel.isClosed()) {
                        // 发送剩余的缓冲区内容
                        if (stdoutBuffer.length() > 0) {
                            sendLog(stdoutBuffer.toString());
                            stdoutBuffer.setLength(0);
                        }
                        if (stderrBuffer.length() > 0) {
                            sendLog("[stderr] " + stderrBuffer.toString());
                            stderrBuffer.setLength(0);
                        }
                        if (in.available() > 0) continue;
                        break;
                    }
                    
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            int exitCode = channel.getExitStatus();
            channel.disconnect();
            
            if (exitCode != 0) {
                log.warn("SSH 命令执行失败: cmd={}, exitCode={}, stderrFile={}",
                    command, exitCode, stderrFile);
                sendLog(String.format("SSH命令执行失败，退出码: %d", exitCode));
            } else {
                log.debug("SSH 命令执行成功: cmd={}, stdoutFile={}", command, stdoutFile);
                sendLog("SSH命令执行成功");
            }

            return ExecutionResult.builder()
                .exitCode(exitCode)
                .output(stdoutFile.toString())
                .errorOutput(stderrFile.toString())
                .build();

        } catch (Exception e) {
            log.error("SSH 执行命令失败: cmd={}", command, e);
            sendLog("SSH命令执行异常: " + e.getMessage());
            return ExecutionResult.builder()
                .exitCode(1)
                .output("")
                .errorOutput("执行异常: " + e.getMessage())
                .build();
        }
    }
    
    @Override
    public void uploadFile(Path localFile, String remotePath) throws IOException {
        ensureConnected();
        Session session = getSession();
        if (session == null || !session.isConnected()) {
            throw new IOException("SSH 连接失败");
        }
        
        ChannelSftp channel = null;
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            // 确保远程目录存在
            String remoteDir = new File(remotePath).getParent();
            if (remoteDir != null) {
                createRemoteDirectories(channel, remoteDir);
            }
            
            // 上传文件
            channel.put(localFile.toString(), remotePath);
            log.debug("SSH 上传文件: {} -> {}", localFile, remotePath);
            
        } catch (JSchException | SftpException e) {
            throw new IOException("上传文件失败: " + e.getMessage(), e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }
    
    @Override
    public void downloadFile(String remotePath, Path localFile) throws IOException {
        ensureConnected();
        Session session = getSession();
        if (session == null || !session.isConnected()) {
            throw new IOException("SSH 连接失败");
        }
        
        ChannelSftp channel = null;
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            // 确保本地目录存在
            Files.createDirectories(localFile.getParent());
            
            // 下载文件
            channel.get(remotePath, localFile.toString());
            log.debug("SSH 下载文件: {} -> {}", remotePath, localFile);
            
        } catch (JSchException | SftpException e) {
            throw new IOException("下载文件失败: " + e.getMessage(), e);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            Session session = getSession();
            return session != null && session.isConnected();
        } catch (Exception e) {
            log.warn("检查 SSH 连接失败", e);
            return false;
        }
    }
    
    @Override
    public String getType() {
        return "ssh";
    }
    
    @Override
    public String getIdentifier() {
        return String.format("ssh://%s@%s:%d", 
            config.getUsername(), 
            config.getHost(), 
            config.getPort() != null ? config.getPort() : 22);
    }
    
    /**
     * 获取或创建 SSH 会话
     */
    private synchronized Session getSession() {
        if (session != null && session.isConnected()) {
            return session;
        }
        
        try {
            JSch jsch = new JSch();
            
            // 设置认证方式
            if ("KEY".equals(config.getAuthType()) && config.getPrivateKey() != null) {
                // 使用 SSH 密钥
                byte[] privateKeyBytes = config.getPrivateKey().getBytes();
                byte[] passphraseBytes = config.getPassphrase() != null ? 
                    config.getPassphrase().getBytes() : null;
                jsch.addIdentity("ssh-key", privateKeyBytes, null, passphraseBytes);
            }
            
            session = jsch.getSession(
                config.getUsername(), 
                config.getHost(), 
                config.getPort() != null ? config.getPort() : 22
            );
            
            // 使用密码认证
            if ("PASSWORD".equals(config.getAuthType()) && config.getPassword() != null) {
                session.setPassword(config.getPassword());
            }
            
            // 配置会话
            Properties sessionConfig = new Properties();
            sessionConfig.put("StrictHostKeyChecking", "no");
            sessionConfig.put("PreferredAuthentications", "publickey,password");
            session.setConfig(sessionConfig);
            
            int timeout = config.getTimeoutSeconds() != null ? 
                config.getTimeoutSeconds() * 1000 : 30000;
            session.setTimeout(timeout);
            
            // 连接
            session.connect();
            log.info("SSH 连接成功: {}", getIdentifier());
            
            return session;
            
        } catch (JSchException e) {
            log.error("SSH 连接失败: {}", getIdentifier(), e);
            session = null;
            return null;
        }
    }
    
    /**
     * 创建远程目录
     */
    private void createRemoteDirectories(ChannelSftp channel, String remoteDir) throws SftpException {
        String[] dirs = remoteDir.split("/");
        String currentPath = "";
        
        for (String dir : dirs) {
            if (dir.isEmpty()) continue;
            currentPath += "/" + dir;
            try {
                channel.mkdir(currentPath);
            } catch (SftpException e) {
                // 目录可能已存在，忽略错误
                if (e.id != ChannelSftp.SSH_FX_FAILURE) {
                    throw e;
                }
            }
        }
    }
    
    /**
     * 关闭 SSH 连接
     * 
     * 注意：通常不需要手动调用，SSH 会话会在多次操作之间复用。
     * 只有在确定不再需要连接时才调用此方法。
     */
    public void close() {
        synchronized (this) {
            if (session != null && session.isConnected()) {
                session.disconnect();
                session = null;
                log.debug("SSH 连接已关闭: {}", getIdentifier());
            }
        }
    }
    
    /**
     * 确保连接可用（如果连接断开则重新连接）
     */
    private void ensureConnected() {
        if (session == null || !session.isConnected()) {
            getSession();
        }
    }
}
