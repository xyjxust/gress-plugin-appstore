package com.keqi.gress.plugin.appstore.service.dependency;

import com.keqi.gress.common.plugin.PluginMetadataParser;
import com.keqi.gress.common.plugin.PluginPackageInstallResult;
import com.keqi.gress.common.utils.WorkspaceDirectoryUtils;
import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import com.keqi.gress.plugin.appstore.service.AppStoreApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析应用所依赖的插件 ID 列表（不含 optional 依赖）。
 */
@Service
@Slf4j
public class ApplicationDependencyPluginIdsResolver {

    @Autowired
    private AppStoreApiService appStoreApiService;

    public List<String> resolve(String pluginId, String version) {
        return resolve(pluginId, version, null, null);
    }

    public List<String> resolve(String pluginId,
                                String version,
                                ApplicationDTO appDetail,
                                PluginPackageInstallResult installInfo) {
        Set<String> ids = new LinkedHashSet<>();

        ApplicationDTO detail = appDetail;
        if (detail == null && pluginId != null && !pluginId.isBlank()) {
            try {
                if (version != null && !version.isBlank()) {
                    detail = appStoreApiService.getApplicationVersionDetail(pluginId, version);
                }
                if (detail == null) {
                    detail = appStoreApiService.getApplicationDetail(pluginId);
                }
            } catch (Exception e) {
                log.debug("从应用商店解析依赖失败: pluginId={}, version={}", pluginId, version, e);
            }
        }
        collectFromApplicationDto(detail, ids);

        if (ids.isEmpty() && installInfo != null) {
            collectFromPluginYml(installInfo.getPluginYmlConfig(), ids);
        }

        if (ids.isEmpty()) {
            collectFromInstalledJar(pluginId, version, ids);
        }

        return new ArrayList<>(ids);
    }

    private void collectFromApplicationDto(ApplicationDTO detail, Set<String> ids) {
        if (detail == null || detail.getDependencies() == null) {
            return;
        }
        for (ApplicationDTO.DependencyInfo dep : detail.getDependencies()) {
            if (dep == null || dep.getPluginId() == null || dep.getPluginId().isBlank()) {
                continue;
            }
            if (Boolean.TRUE.equals(dep.getOptional())) {
                continue;
            }
            ids.add(dep.getPluginId().trim());
        }
    }

    @SuppressWarnings("unchecked")
    private void collectFromPluginYml(Map<String, Object> pluginYmlConfig, Set<String> ids) {
        if (pluginYmlConfig == null || pluginYmlConfig.isEmpty()) {
            return;
        }
        Object pluginNode = pluginYmlConfig.get("plugin");
        if (!(pluginNode instanceof Map<?, ?> pluginMap)) {
            return;
        }
        Object dependencies = pluginMap.get("dependencies");
        if (!(dependencies instanceof List<?> depList)) {
            return;
        }
        for (Object item : depList) {
            if (item == null) {
                continue;
            }
            String pluginId = parseDependencyItem(String.valueOf(item));
            if (pluginId != null && !pluginId.isBlank()) {
                ids.add(pluginId);
            }
        }
    }

    private void collectFromInstalledJar(String pluginId, String version, Set<String> ids) {
        if (pluginId == null || pluginId.isBlank() || version == null || version.isBlank()) {
            return;
        }
        try {
            Path jar = WorkspaceDirectoryUtils.getPluginsDirectory()
                    .resolve(pluginId + "-" + version + ".jar");
            if (!Files.isRegularFile(jar)) {
                return;
            }
            PluginMetadataParser.PluginMetadata metadata = PluginMetadataParser.parseFromJar(jar);
            if (metadata.getDependencyList() == null) {
                return;
            }
            for (PluginMetadataParser.DependencyInfo dep : metadata.getDependencyList()) {
                if (dep != null && dep.getPluginId() != null && !dep.getPluginId().isBlank()) {
                    ids.add(dep.getPluginId().trim());
                }
            }
        } catch (Exception e) {
            log.debug("从已安装 JAR 解析依赖失败: pluginId={}, version={}", pluginId, version, e);
        }
    }

    private String parseDependencyItem(String raw) {
        String part = raw.trim();
        if (part.isEmpty()) {
            return null;
        }
        int at = part.indexOf('@');
        return at > 0 ? part.substring(0, at).trim() : part;
    }
}
