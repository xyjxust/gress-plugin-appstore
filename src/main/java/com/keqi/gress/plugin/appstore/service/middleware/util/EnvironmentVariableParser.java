package com.keqi.gress.plugin.appstore.service.middleware.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境变量解析工具
 * 
 * 支持解析 Docker Compose 中的环境变量格式：
 * - ${VAR:-default} - 带默认值的环境变量
 * - ${VAR} - 无默认值的环境变量
 * - 普通值 - 直接返回
 */
@Slf4j
public class EnvironmentVariableParser {
    
    /**
     * 匹配 ${VAR:-default} 或 ${VAR} 格式的正则表达式
     */
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::-([^}]*))?\\}");
    
    /**
     * 解析环境变量值，支持 ${VAR:-default} 格式
     * 
     * @param value 环境变量值，可能是 "${VAR:-default}" 或直接值
     * @param actualEnvVars 实际环境变量映射（可选，用于运行时替换）
     * @return 解析后的值
     */
    public static String parseValue(String value, Map<String, String> actualEnvVars) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        
        if (!matcher.find()) {
            // 不是 ${} 格式，直接返回
            return value;
        }
        
        String varName = matcher.group(1);
        String defaultValue = matcher.group(2);  // 可能为 null
        
        // 优先使用实际环境变量值
        if (actualEnvVars != null && actualEnvVars.containsKey(varName)) {
            return actualEnvVars.get(varName);
        }
        
        // 使用默认值
        return defaultValue != null ? defaultValue : "";
    }
    
    /**
     * 解析环境变量值（仅提取默认值，不依赖运行时环境变量）
     * 
     * @param value 环境变量值
     * @return 解析后的值（提取默认值）
     */
    public static String extractDefaultValue(String value) {
        return parseValue(value, null);
    }
    
    /**
     * 从 command 字符串中提取环境变量值
     * 
     * 例如：从 "redis-server --requirepass ${REDIS_PASSWORD:-redis123}" 中提取 REDIS_PASSWORD
     * 
     * @param command command 字符串
     * @param pattern 匹配模式，例如 "--requirepass\\s+\\$\\{([^:}]+)(?::-([^}]*))?\\}"
     * @param configKey 配置键名
     * @return 提取的值，如果未找到返回 null
     */
    public static String extractFromCommand(String command, String pattern, String configKey) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        
        Pattern p = Pattern.compile(pattern);
        Matcher matcher = p.matcher(command);
        
        if (matcher.find()) {
            String defaultValue = matcher.group(2);  // 默认值
            return defaultValue != null ? defaultValue : "";
        }
        
        return null;
    }
    
    /**
     * 检查值是否包含环境变量占位符
     * 
     * @param value 要检查的值
     * @return true 如果包含 ${} 格式
     */
    public static boolean containsEnvVar(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return ENV_VAR_PATTERN.matcher(value).find();
    }
}
