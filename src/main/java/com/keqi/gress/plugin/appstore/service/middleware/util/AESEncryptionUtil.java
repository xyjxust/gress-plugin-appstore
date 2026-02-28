package com.keqi.gress.plugin.appstore.service.middleware.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AES 加密工具类
 * 
 * 用于加密/解密中间件连接信息中的敏感数据
 */
@Slf4j
public class AESEncryptionUtil {
    
   //@Slf4j private static final Log log = LogFactory.get(AESEncryptionUtil.class);
    
    /**
     * 默认密钥（仅用于开发环境，生产环境必须配置）
     */
    private static final String DEFAULT_KEY = "GressMiddlewareEncryptionKey32Bytes!!";
    
    /**
     * AES 密钥长度（256位 = 32字节）
     */
    private static final int KEY_LENGTH = 32;
    
    /**
     * 加密前缀标记
     */
    private static final String ENCRYPTION_PREFIX = "ENC:";
    
    /**
     * 加密敏感配置值
     * 
     * @param value 原始值
     * @param encryptionKey 加密密钥
     * @return 加密后的值（ENC:前缀 + Base64编码）
     */
    public static String encrypt(String value, String encryptionKey) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        try {
            String key = normalizeKey(encryptionKey);
            AES aes = new AES(key.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = aes.encrypt(value);
            return ENCRYPTION_PREFIX + Base64.encode(encrypted);
        } catch (Exception e) {
            log.error("加密失败", e);
            throw new RuntimeException("加密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解密敏感配置值
     * 
     * @param encryptedValue 加密后的值（ENC:前缀 + Base64编码）
     * @param encryptionKey 加密密钥
     * @return 解密后的原始值
     */
    public static String decrypt(String encryptedValue, String encryptionKey) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }
        
        // 检查是否是加密的值
        if (!isEncrypted(encryptedValue)) {
            // 如果不是加密格式，直接返回（兼容未加密的数据）
            return encryptedValue;
        }
        
        try {
            // 移除前缀
            String base64Value = encryptedValue.substring(ENCRYPTION_PREFIX.length());
            String key = normalizeKey(encryptionKey);
            AES aes = new AES(key.getBytes(StandardCharsets.UTF_8));
            byte[] decrypted = aes.decrypt(Base64.decode(base64Value));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密失败", e);
            throw new RuntimeException("解密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加密配置 Map 中的敏感字段
     * 
     * @param config 配置 Map
     * @param encryptionKey 加密密钥
     * @return 加密后的配置 Map
     */
    public static Map<String, Object> encryptSensitiveFields(Map<String, Object> config, String encryptionKey) {
        if (config == null || config.isEmpty() || encryptionKey == null) {
            return config;
        }
        
        Map<String, Object> encryptedConfig = new java.util.HashMap<>(config);
        
        // 需要加密的字段关键词
        String[] sensitiveKeywords = {
            "PASSWORD", "SECRET", "KEY", "TOKEN", "AUTH", "CREDENTIAL"
        };
        
        for (Map.Entry<String, Object> entry : encryptedConfig.entrySet()) {
            String key = entry.getKey().toUpperCase();
            Object value = entry.getValue();
            
            // 检查是否是敏感字段
            boolean isSensitive = false;
            for (String keyword : sensitiveKeywords) {
                if (key.contains(keyword)) {
                    isSensitive = true;
                    break;
                }
            }
            
            if (isSensitive && value instanceof String) {
                String originalValue = (String) value;
                if (!originalValue.isEmpty() && !isEncrypted(originalValue)) {
                    // 加密敏感值
                    String encrypted = encrypt(originalValue, encryptionKey);
                    encryptedConfig.put(entry.getKey(), encrypted);
                    log.debug("加密敏感字段: key={}", entry.getKey());
                }
            }
        }
        
        return encryptedConfig;
    }
    
    /**
     * 解密配置 Map 中的敏感字段
     * 
     * @param config 配置 Map
     * @param encryptionKey 加密密钥
     * @return 解密后的配置 Map
     */
    public static Map<String, Object> decryptSensitiveFields(Map<String, Object> config, String encryptionKey) {
        if (config == null || config.isEmpty() || encryptionKey == null) {
            return config;
        }
        
        Map<String, Object> decryptedConfig = new java.util.HashMap<>(config);
        
        // 需要解密的字段关键词
        String[] sensitiveKeywords = {
            "PASSWORD", "SECRET", "KEY", "TOKEN", "AUTH", "CREDENTIAL"
        };
        
        for (Map.Entry<String, Object> entry : decryptedConfig.entrySet()) {
            String key = entry.getKey().toUpperCase();
            Object value = entry.getValue();
            
            // 检查是否是敏感字段
            boolean isSensitive = false;
            for (String keyword : sensitiveKeywords) {
                if (key.contains(keyword)) {
                    isSensitive = true;
                    break;
                }
            }
            
            if (isSensitive && value instanceof String) {
                String encryptedValue = (String) value;
                if (isEncrypted(encryptedValue)) {
                    // 解密敏感值
                    try {
                        String decrypted = decrypt(encryptedValue, encryptionKey);
                        decryptedConfig.put(entry.getKey(), decrypted);
                        log.debug("解密敏感字段: key={}", entry.getKey());
                    } catch (Exception e) {
                        log.warn("解密字段失败，保留原值: key={}", entry.getKey(), e);
                        // 解密失败时保留原值
                    }
                }
            }
        }
        
        return decryptedConfig;
    }
    
    /**
     * 规范化密钥（确保长度为32字节）
     * 
     * @param key 原始密钥
     * @return 规范化后的密钥
     */
    private static String normalizeKey(String key) {
        if (key == null || key.isEmpty()) {
            log.warn("未配置加密密钥，使用默认密钥（不推荐生产环境使用）");
            return DEFAULT_KEY;
        }
        
        // 如果密钥长度不足，使用 SHA-256 哈希扩展
        if (key.length() < KEY_LENGTH) {
            // SecureUtil.sha256(String) 返回十六进制字符串，我们需要转换为字节数组
            String hashHex = SecureUtil.sha256(key);
            // 将十六进制字符串转换为字节数组，然后取前32个字节
            byte[] hashBytes = HexUtil.decodeHex(hashHex);
            // 将字节数组转换为字符串（使用 Base64 或直接使用字符）
            // 为了确保是32个字符，我们使用 Base64 编码
            String hashStr = Base64.encode(hashBytes);
            if (hashStr.length() >= KEY_LENGTH) {
                return hashStr.substring(0, KEY_LENGTH);
            } else {
                // 如果还不够，重复填充
                StringBuilder sb = new StringBuilder(hashStr);
                while (sb.length() < KEY_LENGTH) {
                    sb.append(hashStr);
                }
                return sb.substring(0, KEY_LENGTH);
            }
        } else if (key.length() > KEY_LENGTH) {
            // 如果密钥长度超过，截取前32个字符
            return key.substring(0, KEY_LENGTH);
        }
        
        return key;
    }
    
    /**
     * 检查值是否是加密格式
     * 
     * @param value 要检查的值
     * @return true 如果是加密格式
     */
    private static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTION_PREFIX);
    }
}
