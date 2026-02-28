package com.keqi.gress.plugin.appstore.service.install;

import java.util.Collections;
import java.util.Map;

/**
 * Hook 上下文（尽量保持稳定，便于扩展）。
 */
public class PluginInstallContext {
    private final String operatorName;
    private final String packageIdHint;
    private final String versionHint;
    private final Map<String, Object> attributes;

    public PluginInstallContext(String operatorName, String packageIdHint, String versionHint, Map<String, Object> attributes) {
        this.operatorName = operatorName;
        this.packageIdHint = packageIdHint;
        this.versionHint = versionHint;
        this.attributes = attributes != null ? attributes : Collections.emptyMap();
    }

    public String getOperatorName() {
        return operatorName;
    }

    /**
     * 可能为空：有些场景安装前无法确定 packageId（需解析 jar 或由上层传入）
     */
    public String getPackageIdHint() {
        return packageIdHint;
    }

    /**
     * 可能为空：有些场景安装前无法确定 version（需解析 jar 或由上层传入）
     */
    public String getVersionHint() {
        return versionHint;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}











