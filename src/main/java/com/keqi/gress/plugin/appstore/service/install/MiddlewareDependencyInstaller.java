package com.keqi.gress.plugin.appstore.service.install;

import  com.keqi.gress.common.model.Result;
import com.keqi.gress.plugin.appstore.service.MiddlewareManagementService;


/**
 * 中间件依赖安装器接口
 * 
 * 用于从应用商店下载并安装中间件依赖
 */
@FunctionalInterface
public interface MiddlewareDependencyInstaller {

    /**
     * 从应用商店安装指定中间件版本
     *
     * @param pluginId 插件ID
     * @param versionRange 版本要求（如 ">=1.0.0"，可为空表示最新）
     * @param operator 操作者
     * @return 安装结果
     */
    Result<MiddlewareManagementService.MiddlewareInstallResult> install(
        String pluginId, 
        String versionRange, 
        String operator
    );
}
