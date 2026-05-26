package com.keqi.gress.plugin.appstore.support;

import java.util.List;
import java.util.Map;

/**
 * 从 plugin 清单中的 UI 配置（如 plugin.yml 解析后的 plugin 节点）生成写入 sys_application.extension_config 的标记字段（扁平键）。
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

        boolean hasMenusOrRoutes = menusOrRoutesNonEmpty(pluginYmlRoot);

        Map<String, Object> pluginMap = null;
        Object pluginNode = pluginYmlRoot != null ? pluginYmlRoot.get("plugin") : null;
        if (pluginNode instanceof Map) {
            pluginMap = (Map<String, Object>) pluginNode;
        }

        boolean surfaceAdmin;
        boolean surfaceConsumer;
        if (pluginMap != null && pluginMap.get("surfaces") instanceof Map) {
            Map<String, Object> surfaces = (Map<String, Object>) pluginMap.get("surfaces");
            surfaceAdmin = boolVal(surfaces.get("admin"));
            surfaceConsumer = boolVal(surfaces.get("consumer"));
        } else {
            surfaceAdmin = hasMenusOrRoutes;
            surfaceConsumer = false;
        }

        boolean autoLoadAdmin;
        boolean autoLoadConsumer;
        if (pluginMap != null && pluginMap.get("autoLoad") instanceof Map) {
            Map<String, Object> al = (Map<String, Object>) pluginMap.get("autoLoad");
            autoLoadAdmin = boolVal(al.get("admin"));
            autoLoadConsumer = boolVal(al.get("consumer"));
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

    private static boolean menusOrRoutesNonEmpty(Map<String, Object> root) {
        if (root == null) {
            return false;
        }
        Object menusObj = root.get("menus");
        if (menusObj instanceof List && !((List<?>) menusObj).isEmpty()) {
            return true;
        }
        Object routesObj = root.get("routes");
        return routesObj instanceof List && !((List<?>) routesObj).isEmpty();
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
