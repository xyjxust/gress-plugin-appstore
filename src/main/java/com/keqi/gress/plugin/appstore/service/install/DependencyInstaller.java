package com.keqi.gress.plugin.appstore.service.install;

import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginPackageInstallResult;

/**
 * 抽象的依赖安装器，用于按需安装缺失的依赖插件。
 */
@FunctionalInterface
public interface DependencyInstaller {

    /**
     * 安装指定插件版本。
     *
     * @param pluginId 插件ID
     * @param version  版本号（可为空，表示最新）
     * @param operator 操作者
     * @return 安装结果
     */
    Result<PluginPackageInstallResult> install(String pluginId, String version, String operator);
}











