package com.keqi.gress.plugin.appstore.config;

import  com.keqi.gress.common.plugin.annotion.ConfigurationProperties;
import  com.keqi.gress.common.plugin.annotion.FormField;
import  com.keqi.gress.common.plugin.dto.Input;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 应用商店配置
 * 
 * 注意：配置类只使用 @ConfigurationProperties 注解，不使用 @Service 注解
 * 配置类会被 BeanDefinitionScanner 自动扫描、创建实例并绑定配置值
 */
@Data
@ConfigurationProperties(prefix = "appstore", order = -100)
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
    private ApiConfig api;
    
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
    private DownloadConfig download;
    
    /**
     * 同步配置
     */
    private SyncConfig sync;
    
    /**
     * 缓存配置
     */
    private CacheConfig cache;
    
    /**
     * 安全配置
     */
    private SecurityConfig security;
    
    /**
     * 应用类型配置
     */
    private ApplicationTypesConfig applicationTypes;
    
    /**
     * 通知配置
     */
    private NotificationConfig notification;
    
    /**
     * 自定义属性
     */
    private Map<String, String> customProperties;
    
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
         * API 认证密钥
         */
        @FormField(
            label = "API 密钥",
            description = "用于 API 认证的密钥",
            type = FormField.FieldType.STRING,
            required = true,
            placeholder = "请输入 API 密钥",
            order = 2
        )
        private String secretKey;
        
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
        private Boolean enabled;
        
        /**
         * 同步间隔（秒）
         */
        private Integer interval;
        
        /**
         * 同步时间（Cron 表达式）
         */
        private String cron;
        
        /**
         * 同步失败后重试次数
         */
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
        private Boolean enabled;
        
        /**
         * 缓存过期时间（秒）
         */
        private Integer expireSeconds;
        
        /**
         * 最大缓存条目数
         */
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
        private Boolean verifySignature;
        
        /**
         * 是否检查应用来源
         */
        private Boolean checkSource;
        
        /**
         * 允许的应用来源列表
         */
        private List<String> allowedSources;
        
        /**
         * 是否启用应用沙箱
         */
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
        private List<String> allowed;
        
        /**
         * 默认应用类型
         */
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
        private Boolean enabled;
        
        /**
         * 通知方式
         */
        private List<String> methods;
        
        /**
         * 通知事件
         */
        private List<String> events;
    }
}
