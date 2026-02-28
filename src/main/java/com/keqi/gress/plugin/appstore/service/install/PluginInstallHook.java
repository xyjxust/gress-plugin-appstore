package com.keqi.gress.plugin.appstore.service.install;

import  com.keqi.gress.common.model.Result;

import java.nio.file.Path;

/**
 * 插件安装/升级前置 Hook（可扩展）。
 *
 * 设计目标：
 * - 开闭原则：新增能力通过新增 Hook 实现，不修改安装主流程
 * - 单一职责：每个 Hook 只负责一个维度（例如：中间件部署、策略校验等）
 */
public interface PluginInstallHook {

    /**
     * 安装前执行。
     *
     * @param jarFile 插件包 jar 临时文件路径
     * @param ctx 上下文
     */
    Result<Void> beforeInstall(Path jarFile, PluginInstallContext ctx);

    /**
     * 升级/降级前执行。
     *
     * @param jarFile 新版本插件包 jar 临时文件路径
     * @param ctx 上下文
     */
    Result<Void> beforeUpgrade(Path jarFile, PluginInstallContext ctx);
}











