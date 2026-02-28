package com.keqi.gress.plugin.appstore.service.install;

import  com.keqi.gress.common.model.Result;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hook 链：按顺序执行，任一失败则中断并返回失败。
 */
public class PluginInstallHookChain {
    private final List<PluginInstallHook> hooks;

    public PluginInstallHookChain(List<PluginInstallHook> hooks) {
        this.hooks = hooks != null ? new ArrayList<>(hooks) : Collections.emptyList();
    }

    public Result<Void> beforeInstall(Path jarFile, PluginInstallContext ctx) {
        for (PluginInstallHook h : hooks) {
            Result<Void> r = h.beforeInstall(jarFile, ctx);
            if (r != null && !r.isSuccess()) {
                return r;
            }
        }
        return Result.success();
    }

    public Result<Void> beforeUpgrade(Path jarFile, PluginInstallContext ctx) {
        for (PluginInstallHook h : hooks) {
            Result<Void> r = h.beforeUpgrade(jarFile, ctx);
            if (r != null && !r.isSuccess()) {
                return r;
            }
        }
        return Result.success();
    }
}











