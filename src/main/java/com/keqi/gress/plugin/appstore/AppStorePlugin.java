package com.keqi.gress.plugin.appstore;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.ApplicationPlugin;
import  com.keqi.gress.common.plugin.annotion.PluginSpec;
import com.keqi.gress.plugin.appstore.config.AppStoreConfig;
import org.pf4j.Extension;
import org.pf4j.Plugin;

/**
 * 应用商店插件入口
 * 
 * 注意：@Service 类的扫描和初始化由 ApplicationServiceScanner 自动完成
 * - AppStoreSchemaService 会在插件启动时自动扫描、实例化并调用 @PostConstruct 方法
 * - AppStoreAdminApi 会在插件启动时自动扫描、实例化并调用 @PostConstruct 方法
 * 
 * @Import 注解用于明确声明需要导入的配置类
 */
@Extension
@PluginSpec(
        id = "${plugin.id}",
        name = "应用商店",
        description = "${plugin.description}",
        version = "${plugin.version}",
        author = "${plugin.provider}",
        tags = {"appstore"},
        icon = "icons/appstore.svg",
        jsPath = "js/appstore-frontend.umd.js",
        inputClass = AppStoreConfig.class
)
public class AppStorePlugin extends Plugin implements ApplicationPlugin {
    private final Log log = LogFactory.get(AppStorePlugin.class);
    
    @Override
    public void start() {
        log.info("App Store 插件启动");
    }
    
    @Override
    public void stop() {
        log.info("App Store 插件停止");
    }

}



