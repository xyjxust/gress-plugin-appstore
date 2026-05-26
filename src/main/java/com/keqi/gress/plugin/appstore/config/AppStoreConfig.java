package com.keqi.gress.plugin.appstore.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import  com.keqi.gress.common.plugin.annotion.FormField;
import  com.keqi.gress.common.plugin.dto.Input;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用商店配置
 * 
 * 注意：配置类只使用 @ConfigurationProperties 注解，不使用 @Service 注解
 * 配置类会被 BeanDefinitionScanner 自动扫描、创建实例并绑定配置值
 */
@Data
@ConfigurationProperties(prefix = "appstore")
public class AppStoreConfig implements Input {
    
    /**
     * 商店名称
     */
    @FormField(
        label = "商店名称",
        description = "应用商店的显示名称",
        type = FormField.FieldType.STRING,
        required = true,
        order = 1
    )
    private String storeName;
    
    /**
     * 商店 URL
     */
    @FormField(
        label = "商店 URL",
        description = "应用商店的访问地址",
        type = FormField.FieldType.STRING,
        required = true,
        order = 2
    )
    private String storeUrl;
    
    /**
     * API 配置
     */
    @FormField(
        label = "API 配置",
        description = "应用商店 API 相关配置",
        type = FormField.FieldType.OBJECT,
        order = 10,
        group = "api"
    )
    private ApiConfig api = new ApiConfig();
    
    /**
     * 下载配置
     */
    @FormField(
        label = "下载配置",
        description = "应用下载相关配置",
        type = FormField.FieldType.OBJECT,
        order = 20,
        group = "download"
    )
    private DownloadConfig download = new DownloadConfig();
    
    /**
     * 同步配置
     */
    @FormField(
        label = "同步配置",
        description = "应用商店数据同步相关配置",
        type = FormField.FieldType.OBJECT,
        order = 30,
        group = "sync"
    )
    private SyncConfig sync = new SyncConfig();
    
    /**
     * 缓存配置
     */
    @FormField(
        label = "缓存配置",
        description = "应用商店缓存相关配置",
        type = FormField.FieldType.OBJECT,
        order = 40,
        group = "cache"
    )
    private CacheConfig cache  = new CacheConfig();
    
    /**
     * 安全配置
     */
    @FormField(
        label = "安全配置",
        description = "来源白名单、签名校验等安全策略",
        type = FormField.FieldType.OBJECT,
        order = 50,
        group = "security"
    )
    private SecurityConfig security  = new SecurityConfig();
    
    /**
     * 应用类型配置
     */
    @FormField(
        label = "应用类型配置",
        description = "允许的应用类型与默认类型配置",
        type = FormField.FieldType.OBJECT,
        order = 60,
        group = "applicationTypes"
    )
    private ApplicationTypesConfig applicationTypes = new ApplicationTypesConfig();
    
    /**
     * 通知配置
     */
    @FormField(
        label = "通知配置",
        description = "通知相关配置",
        type = FormField.FieldType.OBJECT,
        order = 70,
        group = "notification"
    )
    private NotificationConfig notification = new NotificationConfig();
    
    /**
     * 自定义属性
     */
    @FormField(
        label = "自定义属性",
        description = "自定义键值对（高级配置）",
        type = FormField.FieldType.OBJECT,
        order = 80,
        group = "customProperties"
    )
    private Map<String, String> customProperties = new HashMap<>();
    
    /**
     * API 配置
     */
    @Data
    public static class ApiConfig {
        /**
         * API 基础地址
         */
        @FormField(
            label = "API 基础地址",
            description = "应用商店 API 的基础 URL",
            type = FormField.FieldType.STRING,
            required = true,
            placeholder = "https://api.example.com",
            order = 1
        )
        private String baseUrl;
        
        /**
         * API Secret（签名密钥）
         */
        @FormField(
            label = "API Secret",
            description = "用于 API 请求签名的 Secret（与 KeyId 配对）",
            type = FormField.FieldType.STRING,
            required = true,
            placeholder = "请输入 API Secret",
            order = 3
        )
        private String secretKey;

        /**
         * API KeyId（配合签名使用）
         */
        @FormField(
            label = "API KeyId",
            description = "用于 API 认证的 KeyId（与 Secret 配对）",
            type = FormField.FieldType.STRING,
            required = false,
            placeholder = "请输入 KeyId",
            order = 2
        )
        private String keyId;
        
        /**
         * API 超时时间（毫秒）
         */
        @FormField(
            label = "超时时间",
            description = "API 请求超时时间（毫秒）",
            type = FormField.FieldType.INTEGER,
            defaultValue = "30000",
            order = 3
        )
        private Integer timeout;
        
        /**
         * 重试次数
         */
        @FormField(
            label = "重试次数",
            description = "API 请求失败后的重试次数",
            type = FormField.FieldType.INTEGER,
            defaultValue = "3",
            order = 4
        )
        private Integer maxRetries;
        
        /**
         * 是否启用 API 调用
         */
        @FormField(
            label = "启用 API",
            description = "是否启用 API 调用功能",
            type = FormField.FieldType.BOOLEAN,
            defaultValue = "true",
            component = FormField.ComponentType.SWITCH,
            order = 5
        )
        private Boolean enabled;
    }
    
    /**
     * 下载配置
     */
    @Data
    public static class DownloadConfig {
        /**
         * 下载目录
         */
        @FormField(
            label = "下载目录",
            description = "应用下载保存的目录路径",
            type = FormField.FieldType.STRING,
            required = true,
            placeholder = "/var/appstore/downloads",
            order = 1
        )
        private String directory;
        
        /**
         * 下载超时时间（毫秒）
         */
        @FormField(
            label = "下载超时",
            description = "下载超时时间（毫秒）",
            type = FormField.FieldType.INTEGER,
            defaultValue = "300000",
            order = 2
        )
        private Integer timeout;
        
        /**
         * 最大并发下载数
         */
        @FormField(
            label = "最大并发数",
            description = "同时进行的最大下载任务数",
            type = FormField.FieldType.INTEGER,
            defaultValue = "3",
            order = 3
        )
        private Integer maxConcurrent;
        
        /**
         * 下载完成后是否自动安装
         */
        @FormField(
            label = "自动安装",
            description = "下载完成后是否自动安装应用",
            type = FormField.FieldType.BOOLEAN,
            defaultValue = "false",
            component = FormField.ComponentType.SWITCH,
            order = 4
        )
        private Boolean autoInstall;
        
        /**
         * 下载失败后是否自动重试
         */
        @FormField(
            label = "自动重试",
            description = "下载失败后是否自动重试",
            type = FormField.FieldType.BOOLEAN,
            defaultValue = "true",
            component = FormField.ComponentType.SWITCH,
            order = 5
        )
        private Boolean autoRetry;
    }
    
    /**
     * 同步配置
     */
    @Data
    public static class SyncConfig {
        /**
         * 是否启用自动同步
         */
        @FormField(
            label = "启用同步",
            description = "是否启用自动同步",
            type = FormField.FieldType.BOOLEAN,
            defaultValue = "false",
            component = FormField.ComponentType.SWITCH,
            order = 1,
            group = "sync"
        )
        private Boolean enabled;
        
        /**
         * 同步间隔（秒）
         */
        @FormField(
            label = "同步间隔（秒）",
            description = "定期同步的间隔（秒），与 cron 二选一",
            type = FormField.FieldType.INTEGER,
            defaultValue = "3600",
            order = 2,
            group = "sync"
        )
        private Integer interval;
        
        /**
         * 同步时间（Cron 表达式）
         */
        @FormField(
            label = "同步 Cron",
            description = "定时同步 Cron 表达式（优先于 interval）",
            type = FormField.FieldType.STRING,
            required = false,
            order = 3,
            group = "sync"
        )
        private String cron;
        
        /**
         * 同步失败后重试次数
         */
        @FormField(
            label = "同步重试次数",
            description = "同步失败后最大重试次数",
            type = FormField.FieldType.INTEGER,
            defaultValue = "3",
            order = 4,
            group = "sync"
        )
        private Integer maxRetries;
    }
    
    /**
     * 缓存配置
     */
    @Data
    public static class CacheConfig {
        /**
         * 是否启用缓存
         */
        @FormField(
            label = "启用缓存",
            description = "是否启用应用商店缓存",
            type = FormField.FieldType.BOOLEAN,
            defaultValue = "true",
            component = FormField.ComponentType.SWITCH,
            order = 1,
            group = "cache"
        )
        private Boolean enabled;
        
        /**
         * 缓存过期时间（秒）
         */
        @FormField(
            label = "缓存过期（秒）",
            description = "缓存默认过期时间（秒）",
            type = FormField.FieldType.INTEGER,
            defaultValue = "300",
            order = 2,
            group = "cache"
        )
        private Integer expireSeconds;
        
        /**
         * 最大缓存条目数
         */
        @FormField(
            label = "最大缓存条目数",
            description = "缓存最大条目数（0 或空表示不限制）",
            type = FormField.FieldType.INTEGER,
            required = false,
            order = 3,
            group = "cache"
        )
        private Integer maxSize;
    }
    
    /**
     * 安全配置
     */
    @Data
    public static class SecurityConfig {
        /**
         * 是否验证应用签名
         */
        @FormField(
            label = "是否验证应用签名",
            description = "安装应用前校验 JAR 内置签名（META-INF/*.SF/*.RSA）。验签失败禁止安装。",
            type = FormField.FieldType.BOOLEAN,
            defaultValue = "false",
            component = FormField.ComponentType.SWITCH,
            order = 1
        )
        private Boolean verifySignature;
        
        /**
         * 是否检查应用来源
         */
        @FormField(
            label = "校验来源白名单",
            description = "仅允许从可信来源下载/安装（白名单域名/仓库）。建议开启。",
            type = FormField.FieldType.BOOLEAN,
            defaultValue = "true",
            component = FormField.ComponentType.SWITCH,
            order = 2
        )
        private Boolean checkSource;
        
        /**
         * 允许的应用来源列表（逗号分隔）
         */
        @FormField(
            label = "允许的来源列表",
            description = "白名单来源（例如域名或仓库前缀），使用逗号分隔。仅在开启“校验来源白名单”时生效。",
            type = FormField.FieldType.STRING,
            required = false,
            placeholder = "例如：appstore.company.com,github.com/org/repo",
            component = FormField.ComponentType.TEXTAREA,
            order = 3
        )
        private String allowedSources;

        /**
         * 解析允许来源列表（以逗号分隔）
         */
        public java.util.List<String> allowedSourcesList() {
            if (allowedSources == null || allowedSources.trim().isEmpty()) {
                return java.util.Collections.emptyList();
            }
            return java.util.Arrays.stream(allowedSources.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        }
        
        /**
         * 是否启用应用沙箱
         */
        @FormField(
            label = "启用沙箱",
            description = "是否启用应用沙箱隔离（预留开关）",
            type = FormField.FieldType.BOOLEAN,
            defaultValue = "false",
            component = FormField.ComponentType.SWITCH,
            order = 4
        )
        private Boolean enableSandbox;
        
        /**
         * 中间件连接信息加密密钥（AES，32字节，256位）
         * 如果未配置，将使用默认密钥（不推荐生产环境使用）
         */
        @FormField(
            label = "连接信息加密密钥",
            description = "用于加密中间件连接信息中的敏感数据（如密码），AES-256，32字节密钥",
            type = FormField.FieldType.STRING,
            required = false,
            order = 10
        )
        private String middlewareEncryptionKey;
    }
    
    /**
     * 应用类型配置
     */
    @Data
    public static class ApplicationTypesConfig {
        /**
         * 允许的应用类型
         */
        @FormField(
            label = "允许的应用类型",
            description = "允许的应用类型列表（例如 plugin、integrated 等）",
            type = FormField.FieldType.ARRAY,
            required = false,
            order = 1,
            group = "applicationTypes"
        )
        private List<String> allowed;
        
        /**
         * 默认应用类型
         */
        @FormField(
            label = "默认应用类型",
            description = "当未指定时使用的默认应用类型",
            type = FormField.FieldType.STRING,
            required = false,
            order = 2,
            group = "applicationTypes"
        )
        private String defaultType;
    }
    
    /**
     * 通知配置
     */
    @Data
    public static class NotificationConfig {
        /**
         * 是否启用通知
         */
        @FormField(
            label = "启用通知",
            description = "是否启用通知功能",
            type = FormField.FieldType.BOOLEAN,
            defaultValue = "false",
            component = FormField.ComponentType.SWITCH,
            order = 1,
            group = "notification"
        )
        private Boolean enabled;
        
        /**
         * 通知方式
         */
        @FormField(
            label = "通知方式",
            description = "通知方式列表（预留字段）",
            type = FormField.FieldType.ARRAY,
            required = false,
            order = 2,
            group = "notification"
        )
        private List<String> methods;
        
        /**
         * 通知事件
         */
        @FormField(
            label = "通知事件",
            description = "需要通知的事件列表（预留字段）",
            type = FormField.FieldType.ARRAY,
            required = false,
            order = 3,
            group = "notification"
        )
        private List<String> events;
    }
}
