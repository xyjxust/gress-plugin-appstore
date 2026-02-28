package com.keqi.gress.plugin.appstore.service.middleware.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 执行环境接口
 * 
 * 抽象不同的执行环境（本地、SSH、Docker API等），
 * 用于在工作流步骤中执行命令、上传/下载文件等操作。
 */
public interface ExecutionEnvironment {
    
    /**
     * 执行命令
     * 
     * @param command 命令列表（如 ["docker", "compose", "up", "-d"]）
     * @param envVars 环境变量
     * @param timeout 超时时间
     * @return 执行结果
     */
    ExecutionResult executeCommand(List<String> command, Map<String, String> envVars, Duration timeout);
    
    /**
     * 上传文件到远程环境
     * 
     * @param localFile 本地文件路径
     * @param remotePath 远程文件路径
     * @throws IOException 上传失败时抛出
     */
    void uploadFile(Path localFile, String remotePath) throws IOException;
    
    /**
     * 从远程环境下载文件
     * 
     * @param remotePath 远程文件路径
     * @param localFile 本地文件路径
     * @throws IOException 下载失败时抛出
     */
    void downloadFile(String remotePath, Path localFile) throws IOException;
    
    /**
     * 检查环境是否可用
     * 
     * @return true 如果环境可用
     */
    boolean isAvailable();
    
    /**
     * 获取环境类型
     * 
     * @return 环境类型：local | ssh | docker-api
     */
    String getType();
    
    /**
     * 获取环境标识（用于日志和调试）
     * 
     * @return 环境标识，如 "localhost" 或 "ssh://user@host:port"
     */
    String getIdentifier();
}
