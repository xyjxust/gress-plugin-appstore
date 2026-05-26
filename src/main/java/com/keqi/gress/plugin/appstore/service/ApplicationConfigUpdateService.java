package com.keqi.gress.plugin.appstore.service;

import com.alibaba.fastjson2.JSON;
import  org.springframework.beans.factory.annotation.Autowired;
import  org.springframework.stereotype.Service;
import com.keqi.gress.common.plugin.PluginConfigMetadataProvider;
import  com.keqi.gress.common.utils.ConfigUtils;
import com.keqi.gress.plugin.appstore.dao.ApplicationDao;
import com.keqi.gress.plugin.appstore.support.PluginUiExtensionConfigFactory;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用配置更新服务
 * 
 * 负责在插件安装和升级时更新 SysApplication 的 extensionConfig 字段
 */
@Slf4j
@Service
public class ApplicationConfigUpdateService {
    
    @Autowired
    private ApplicationDao applicationDao;

    @Autowired(required = false)
    private PluginConfigMetadataProvider pluginConfigMetadataProvider;
    
    /**
     * 安装时更新配置（完全覆盖）
     * 
     * @param pluginId 插件ID
     * @param pluginYmlConfig plugin.yml 配置数据
     * @param updateBy 更新人
     * @return 是否更新成功
     */
    public boolean updateConfigOnInstall(String pluginId, Map<String, Object> pluginYmlConfig, String updateBy) {
        if (pluginId == null || pluginId.isEmpty()) {
            log.warn("插件ID为空，跳过配置更新");
            return false;
        }
        
        if (pluginYmlConfig == null || pluginYmlConfig.isEmpty()) {
            log.debug("plugin.yml 配置为空，跳过配置更新: pluginId={}", pluginId);
            return true;
        }
        
        try {
            // 查找应用
            SysApplication application = applicationDao.getApplicationByPluginId(pluginId);
            if (application == null) {
                log.warn("未找到应用记录，跳过配置更新: pluginId={}", pluginId);
                return false;
            }
            
            Map<String, Object> flatConfig = buildInstallMergedConfig(pluginId, application, pluginYmlConfig);
            applyExtensionFlags(flatConfig, pluginYmlConfig);

            String newConfigJson = JSON.toJSONString(flatConfig);
            int updated = applicationDao.updateApplicationExtensionConfig(
                    application.getId(), 
                    newConfigJson, 
                    updateBy != null ? updateBy : "system"
            );
            
            if (updated > 0) {
                log.info("安装时更新应用配置成功: pluginId={}, configKeys={}", 
                        pluginId, flatConfig.keySet());
                return true;
            } else {
                log.warn("安装时更新应用配置失败: pluginId={}", pluginId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("安装时更新应用配置异常: pluginId={}", pluginId, e);
            return false;
        }
    }
    
    /**
     * 升级时更新配置（增量更新，只添加新 key，不覆盖已存在的 key）
     * 
     * @param pluginId 插件ID
     * @param pluginYmlConfig plugin.yml 配置数据
     * @param updateBy 更新人
     * @return 是否更新成功
     */
    public boolean updateConfigOnUpgrade(String pluginId, Map<String, Object> pluginYmlConfig, String updateBy) {
        if (pluginId == null || pluginId.isEmpty()) {
            log.warn("插件ID为空，跳过配置更新");
            return false;
        }
        
        if (pluginYmlConfig == null || pluginYmlConfig.isEmpty()) {
            log.debug("plugin.yml 配置为空，跳过配置更新: pluginId={}", pluginId);
            return true;
        }
        
        try {
            // 查找应用
            SysApplication application = applicationDao.getApplicationByPluginId(pluginId);
            if (application == null) {
                log.warn("未找到应用记录，跳过配置更新: pluginId={}", pluginId);
                return false;
            }
            
            String existingConfigJson = application.getExtensionConfig();
            Map<String, Object> existingConfig = parseConfigToMap(existingConfigJson);
            Map<String, Object> existingFlat = ConfigUtils.nestedToFlat(existingConfig);

            Map<String, Object> newFlat = ConfigUtils.nestedToFlat(pluginYmlConfig);

            Map<String, Object> mergedFlat = ConfigUtils.incrementalMerge(existingFlat, newFlat);

            applyExtensionFlags(mergedFlat, pluginYmlConfig);

            String newConfigJson = JSON.toJSONString(mergedFlat);
            int updated = applicationDao.updateApplicationExtensionConfig(
                    application.getId(), 
                    newConfigJson, 
                    updateBy != null ? updateBy : "system"
            );
            
            if (updated > 0) {
                log.info("升级时更新应用配置成功: pluginId={}, 新增keys={}", 
                        pluginId, getNewKeys(existingFlat, newFlat));
                return true;
            } else {
                log.warn("升级时更新应用配置失败: pluginId={}", pluginId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("升级时更新应用配置异常: pluginId={}", pluginId, e);
            return false;
        }
    }
    
    /**
     * 解析配置 JSON 为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigToMap(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            return new java.util.LinkedHashMap<>();
        }
        
        try {
            Object parsed = JSON.parse(configJson);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            } else {
                log.warn("配置 JSON 不是 Map 类型，返回空对象");
                return new java.util.LinkedHashMap<>();
            }
        } catch (Exception e) {
            log.warn("解析配置 JSON 失败，返回空对象: {}", e.getMessage());
            return new java.util.LinkedHashMap<>();
        }
    }
    
    /**
     * 获取新增的 key 列表（用于日志）
     * 递归比较嵌套结构
     */
    private String getNewKeys(Map<String, Object> existingConfig, Map<String, Object> newConfig) {
        StringBuilder newKeys = new StringBuilder();
        collectNewKeys("", existingConfig, newConfig, newKeys);
        return newKeys.length() > 0 ? newKeys.toString() : "无";
    }
    
    /**
     * 递归收集新增的 key
     */
    @SuppressWarnings("unchecked")
    private void collectNewKeys(String prefix, Map<String, Object> existing, Map<String, Object> newConfig, StringBuilder result) {
        for (Map.Entry<String, Object> entry : newConfig.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

            Object existingValue = existing != null ? existing.get(key) : null;

            if (existingValue == null) {
                // 新key
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(fullKey);
            } else if (existingValue instanceof Map && newValue instanceof Map) {
                // 递归处理嵌套Map
                collectNewKeys(fullKey, (Map<String, Object>) existingValue,
                              (Map<String, Object>) newValue, result);
            }
        }
    }

    /**
     * 从新版本 plugin-ui 配置提取 surfaces / autoLoad / widget / panel，强制写入合并后的 extension_config 顶层。
     */
    @SuppressWarnings("unchecked")
    private void applyExtensionFlags(Map<String, Object> mergedConfig, Map<String, Object> newConfig) {
        boolean hasWidget = false;
        boolean hasPanel = false;

        Object pluginNode = newConfig.get("plugin");
        if (pluginNode instanceof Map) {
            Map<String, Object> pluginMap = (Map<String, Object>) pluginNode;
            if (pluginMap.get("hasWidget") != null) {
                hasWidget = Boolean.parseBoolean(String.valueOf(pluginMap.get("hasWidget")));
            }
            if (pluginMap.get("hasPanel") != null) {
                hasPanel = Boolean.parseBoolean(String.valueOf(pluginMap.get("hasPanel")));
            }
        }

        PluginUiExtensionConfigFactory.putExtensionFlags(mergedConfig, newConfig, hasWidget, hasPanel);

        log.info("升级时强制更新扩展点标记: surfaceAdmin={}, surfaceConsumer={}, autoLoadAdmin={}, autoLoadConsumer={}, hasWidget={}, hasPanel={}",
                mergedConfig.get("surfaceAdmin"), mergedConfig.get("surfaceConsumer"),
                mergedConfig.get("autoLoadAdmin"), mergedConfig.get("autoLoadConsumer"),
                hasWidget, hasPanel);
    }

    private Map<String, Object> buildInstallMergedConfig(
            String pluginId,
            SysApplication application,
            Map<String, Object> pluginYmlConfig) {
        Map<String, Object> merged = new LinkedHashMap<>();

        Map<String, Object> existingConfig = parseConfigToMap(application.getExtensionConfig());
        if (existingConfig != null && !existingConfig.isEmpty()) {
            merged.putAll(ConfigUtils.nestedToFlat(existingConfig));
        }

        Map<String, Object> currentFlatConfig = getCurrentPluginFlatConfig(pluginId);
        if (currentFlatConfig != null && !currentFlatConfig.isEmpty()) {
            merged.putAll(currentFlatConfig);
        }

        Map<String, Object> pluginYmlFlatConfig = ConfigUtils.nestedToFlat(pluginYmlConfig);
        if (pluginYmlFlatConfig != null && !pluginYmlFlatConfig.isEmpty()) {
            merged.putAll(pluginYmlFlatConfig);
        }

        return merged;
    }

    private Map<String, Object> getCurrentPluginFlatConfig(String pluginId) {
        if (pluginConfigMetadataProvider == null || pluginId == null || pluginId.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> flatConfig = pluginConfigMetadataProvider.getPluginPackageFlatConfig(pluginId);
            return flatConfig != null ? new LinkedHashMap<>(flatConfig) : new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("获取插件当前拍平配置失败，继续保留已有 extension_config: pluginId={}, error={}",
                    pluginId, e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}
