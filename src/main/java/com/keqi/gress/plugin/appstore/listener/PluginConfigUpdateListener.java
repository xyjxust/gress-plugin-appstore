package com.keqi.gress.plugin.appstore.listener;

import  com.keqi.gress.common.event.Event;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.service.ApplicationConfigUpdateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.util.Map;

/**
 * 插件配置更新监听器
 * 
 * 监听插件安装和升级事件，自动更新 SysApplication 的 extensionConfig 字段
 */
@Slf4j
@Service
public class PluginConfigUpdateListener {
    
    @Inject
    private ApplicationConfigUpdateService applicationConfigUpdateService;
    
    /**
     * 处理插件安装事件
     */
    @EventListener(condition = "#event.type == 'plugin.package.installed'")
    public void handlePluginInstalled(Event<?> event) {
        try {
            Object data = event.getData();
            if (!(data instanceof Map)) {
                log.warn("插件安装事件数据格式不正确");
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) data;
            
            String packageId = (String) eventData.get("packageId");
            @SuppressWarnings("unchecked")
            Map<String, Object> pluginYmlConfig = (Map<String, Object>) eventData.get("config");
            
            log.info("收到插件安装事件: packageId={}", packageId);
            
            if (pluginYmlConfig == null || pluginYmlConfig.isEmpty()) {
                log.debug("插件配置为空，跳过更新: packageId={}", packageId);
                return;
            }
            
            boolean success = applicationConfigUpdateService.updateConfigOnInstall(
                    packageId, pluginYmlConfig, "system");
            
            if (success) {
                log.info("插件安装后配置更新成功: packageId={}", packageId);
            } else {
                log.warn("插件安装后配置更新失败: packageId={}", packageId);
            }
            
        } catch (Exception e) {
            log.error("处理插件安装事件异常", e);
        }
    }
    
    /**
     * 处理插件升级事件
     */
    @EventListener(condition = "#event.type == 'plugin.package.upgraded'")
    public void handlePluginUpgraded(Event<?> event) {
        try {
            Object data = event.getData();
            if (!(data instanceof Map)) {
                log.warn("插件升级事件数据格式不正确");
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) data;
            
            String packageId = (String) eventData.get("packageId");
            String oldVersion = (String) eventData.get("oldVersion");
            String newVersion = (String) eventData.get("newVersion");
            @SuppressWarnings("unchecked")
            Map<String, Object> pluginYmlConfig = (Map<String, Object>) eventData.get("config");
            
            log.info("收到插件升级事件: packageId={}, oldVersion={}, newVersion={}", 
                    packageId, oldVersion, newVersion);
            
            if (pluginYmlConfig == null || pluginYmlConfig.isEmpty()) {
                log.debug("插件配置为空，跳过更新: packageId={}", packageId);
                return;
            }
            
            boolean success = applicationConfigUpdateService.updateConfigOnUpgrade(
                    packageId, pluginYmlConfig, "system");
            
            if (success) {
                log.info("插件升级后配置更新成功: packageId={}", packageId);
            } else {
                log.warn("插件升级后配置更新失败: packageId={}", packageId);
            }
            
        } catch (Exception e) {
            log.error("处理插件升级事件异常", e);
        }
    }
}
