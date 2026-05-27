package com.keqi.gress.plugin.appstore.support;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 从 plugin 清单中的 UI 配置（plugin.yml 解析后的 plugin / plugin.ui 节点）生成
 * 写入 sys_application.extension_config 的标记字段（扁平键）。
 */
public final class PluginUiExtensionConfigFactory {

    private PluginUiExtensionConfigFactory() {
    }

    @SuppressWarnings("unchecked")
    public static void putExtensionFlags(
            Map<String, Object> extConfig,
            Map<String, Object> pluginYmlRoot,
            boolean hasWidget,
            boolean hasPanel) {

        Map<String, Object> pluginMap = pluginMap(pluginYmlRoot);
        Map<String, Object> uiMap = uiMap(pluginMap);

        boolean hasMenusOrRoutes = menusOrRoutesNonEmpty(pluginYmlRoot, pluginMap, uiMap);

        Map<String, Object> surfaces = mapValue(uiMap, "surfaces");
        if (surfaces == null) {
            surfaces = mapValue(pluginMap, "surfaces");
        }

        boolean surfaceAdmin;
        boolean surfaceConsumer;
        if (surfaces != null) {
            surfaceAdmin = boolVal(surfaces.get("admin"));
            surfaceConsumer = boolVal(surfaces.get("consumer"));
        } else {
            surfaceAdmin = hasMenusOrRoutes;
            surfaceConsumer = false;
        }

        Map<String, Object> autoLoad = mapValue(uiMap, "autoLoad");
        if (autoLoad == null) {
            autoLoad = mapValue(pluginMap, "autoLoad");
        }

        boolean autoLoadAdmin;
        boolean autoLoadConsumer;
        if (autoLoad != null) {
            autoLoadAdmin = boolVal(autoLoad.get("admin"));
            autoLoadConsumer = boolVal(autoLoad.get("consumer"));
        } else {
            autoLoadAdmin = false;
            autoLoadConsumer = false;
        }

        extConfig.put("surfaceAdmin", surfaceAdmin);
        extConfig.put("surfaceConsumer", surfaceConsumer);
        extConfig.put("autoLoadAdmin", autoLoadAdmin);
        extConfig.put("autoLoadConsumer", autoLoadConsumer);
        extConfig.put("hasWidget", hasWidget);
        extConfig.put("hasPanel", hasPanel);
    }

    /**
     * 从 plugin.yml 根视图解析 hasWidget / hasPanel（优先 plugin.ui，兼容旧版 plugin 直挂字段）。
     */
    @SuppressWarnings("unchecked")
    public static boolean[] resolveWidgetPanelFlags(Map<String, Object> pluginYmlRoot, boolean defaultWidget, boolean defaultPanel) {
        Map<String, Object> pluginMap = pluginMap(pluginYmlRoot);
        Map<String, Object> uiMap = uiMap(pluginMap);

        boolean hasWidget = defaultWidget;
        boolean hasPanel = defaultPanel;

        if (uiMap.containsKey("hasWidget")) {
            hasWidget = boolVal(uiMap.get("hasWidget"));
        } else if (pluginMap != null && pluginMap.containsKey("hasWidget")) {
            hasWidget = boolVal(pluginMap.get("hasWidget"));
        }

        if (uiMap.containsKey("hasPanel")) {
            hasPanel = boolVal(uiMap.get("hasPanel"));
        } else if (pluginMap != null && pluginMap.containsKey("hasPanel")) {
            hasPanel = boolVal(pluginMap.get("hasPanel"));
        }

        return new boolean[] {hasWidget, hasPanel};
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pluginMap(Map<String, Object> pluginYmlRoot) {
        if (pluginYmlRoot == null) {
            return Collections.emptyMap();
        }
        Object pluginNode = pluginYmlRoot.get("plugin");
        if (pluginNode instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> uiMap(Map<String, Object> pluginMap) {
        if (pluginMap == null || pluginMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Object uiNode = pluginMap.get("ui");
        if (uiNode instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static boolean menusOrRoutesNonEmpty(
            Map<String, Object> pluginYmlRoot,
            Map<String, Object> pluginMap,
            Map<String, Object> uiMap) {
        if (menusOrRoutesNonEmptyAt(pluginYmlRoot)) {
            return true;
        }
        if (menusOrRoutesNonEmptyAt(uiMap)) {
            return true;
        }
        if (menusOrRoutesNonEmptyAt(pluginMap)) {
            return true;
        }
        return false;
    }

    private static boolean menusOrRoutesNonEmptyAt(Map<String, Object> node) {
        if (node == null || node.isEmpty()) {
            return false;
        }
        Object menusObj = node.get("menus");
        if (menusObj instanceof List<?> menus && !menus.isEmpty()) {
            return true;
        }
        Object routesObj = node.get("routes");
        return routesObj instanceof List<?> routes && !routes.isEmpty();
    }

    private static boolean boolVal(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(v.toString().trim());
        }
        return false;
    }
}
