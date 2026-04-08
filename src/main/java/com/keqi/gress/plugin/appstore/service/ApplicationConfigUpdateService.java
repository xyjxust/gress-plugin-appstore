package com.keqi.gress.plugin.appstore.service;

import com.alibaba.fastjson2.JSON;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.common.utils.ConfigUtils;
import com.keqi.gress.plugin.appstore.dao.ApplicationDao;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import lombok.extern.slf4j.Slf4j;

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
    
    @Inject
    private ApplicationDao applicationDao;
    
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
            
            // 将拍平的配置转换为嵌套结构
            Map<String, Object> nestedConfig = ConfigUtils.flatToNested(pluginYmlConfig);
            // 安装时也强制写入扩展点标记/页面标记，避免被覆盖导致缺失
            applyExtensionFlags(nestedConfig, nestedConfig);
            
            // 安装时直接覆盖配置
            String newConfigJson = JSON.toJSONString(nestedConfig);
            int updated = applicationDao.updateApplicationExtensionConfig(
                    application.getId(), 
                    newConfigJson, 
                    updateBy != null ? updateBy : "system"
            );
            
            if (updated > 0) {
                log.info("安装时更新应用配置成功: pluginId={}, configKeys={}", 
                        pluginId, nestedConfig.keySet());
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
            
            // 获取现有配置（嵌套结构）
            String existingConfigJson = application.getExtensionConfig();
            Map<String, Object> existingConfig = parseConfigToMap(existingConfigJson);
            
            // 将拍平的新配置转换为嵌套结构
            Map<String, Object> newNestedConfig = ConfigUtils.flatToNested(pluginYmlConfig);
            
            // 增量合并配置（只添加新 key，不覆盖已存在的 key）
            Map<String, Object> mergedConfig = ConfigUtils.incrementalMerge(existingConfig, newNestedConfig);

            // 前端扩展点标记以新版本为准（强制覆盖），升级后可能新增/移除了 widget/panel
            applyExtensionFlags(mergedConfig, newNestedConfig);

            // 更新配置
            String newConfigJson = JSON.toJSONString(mergedConfig);
            int updated = applicationDao.updateApplicationExtensionConfig(
                    application.getId(), 
                    newConfigJson, 
                    updateBy != null ? updateBy : "system"
            );
            
            if (updated > 0) {
                log.info("升级时更新应用配置成功: pluginId={}, 新增keys={}", 
                        pluginId, getNewKeys(existingConfig, newNestedConfig));
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
     * 从新配置中提取 hasWidget / hasPanel 标记，强制覆盖到合并后的配置中。
     * 升级时这些标记必须以新版本 plugin-ui.yml 为准，不能保留旧值。
     * 同时根据标记自动计算 autoLoad。
     *
     * @param mergedConfig 增量合并后的配置（会被就地修改）
     * @param newConfig    新版本的嵌套配置
     */
    @SuppressWarnings("unchecked")
    private void applyExtensionFlags(Map<String, Object> mergedConfig, Map<String, Object> newConfig) {
        // 从新配置的 plugin 节点提取标记
        boolean hasWidget = false;
        Boolean yamlHasPanel = null;
        Boolean pluginAutoLoad = null;
        boolean hasPanel;
        boolean hasFrontend = false;

        Object pluginNode = newConfig.get("plugin");
        if (pluginNode instanceof Map) {
            Map<String, Object> pluginMap = (Map<String, Object>) pluginNode;
            hasWidget = Boolean.TRUE.equals(pluginMap.get("hasWidget"));
            if (pluginMap.containsKey("hasPanel")) {
                yamlHasPanel = pluginMap.get("hasPanel") instanceof Boolean
                        ? (Boolean) pluginMap.get("hasPanel")
                        : Boolean.parseBoolean(String.valueOf(pluginMap.get("hasPanel")));
            }
            if (pluginMap.containsKey("autoLoad")) {
                pluginAutoLoad = pluginMap.get("autoLoad") instanceof Boolean
                        ? (Boolean) pluginMap.get("autoLoad")
                        : Boolean.parseBoolean(String.valueOf(pluginMap.get("autoLoad")));
            }
        }

        // menus/routes 任一非空，则认为存在前端页面
        Object menusObj = newConfig.get("menus");
        if (menusObj instanceof List && !((List<?>) menusObj).isEmpty()) {
            hasFrontend = true;
        } else {
            Object routesObj = newConfig.get("routes");
            if (routesObj instanceof List && !((List<?>) routesObj).isEmpty()) {
                hasFrontend = true;
            }
        }

        if (pluginAutoLoad != null) {
            // 有显式 autoLoad：优先使用它
            boolean autoLoad = pluginAutoLoad;
            if (yamlHasPanel != null) {
                hasPanel = yamlHasPanel;
            } else {
                // 没有 hasPanel 时推断：panel-only 更合理
                hasPanel = autoLoad && !hasWidget;
            }
            mergedConfig.put("autoLoad", autoLoad);
        } else {
            // 兼容旧版：autoLoad = hasWidget || hasPanel
            hasPanel = yamlHasPanel != null ? yamlHasPanel : false;
            mergedConfig.put("autoLoad", hasWidget || hasPanel);
        }

        // 强制覆盖
        mergedConfig.put("hasWidget", hasWidget);
        mergedConfig.put("hasPanel", hasPanel);
        mergedConfig.put("hasFrontend", hasFrontend);

        log.info("升级时强制更新扩展点标记: hasWidget={}, hasPanel={}, autoLoad={}",
                hasWidget, hasPanel, mergedConfig.get("autoLoad"));
    }
}
