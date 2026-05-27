package com.keqi.gress.plugin.appstore.service.dependency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keqi.gress.common.model.Result;
import com.keqi.gress.plugin.appstore.dao.ApplicationDao;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 {@link SysApplication#dependencyPluginIds} 校验插件是否仍被其他应用依赖。
 */
@Service
@Slf4j
public class ApplicationDependentsGuardService {

    @Autowired
    private ApplicationDao applicationDao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param targetPluginId        待停用/卸载的插件 ID
     * @param enabledDependentsOnly true：仅统计仍启用的依赖方（用于停用）；false：统计全部已安装依赖方（用于卸载）
     */
    public Result<Void> validateNoDependents(String targetPluginId, boolean enabledDependentsOnly) {
        if (targetPluginId == null || targetPluginId.isBlank()) {
            return Result.success();
        }
        List<DependentApplication> dependents = findDependents(targetPluginId, enabledDependentsOnly);
        if (dependents.isEmpty()) {
            return Result.success();
        }
        String detail = dependents.stream()
                .map(d -> d.getApplicationName() + "（" + d.getPluginId() + "）")
                .collect(Collectors.joining("、"));
        String action = enabledDependentsOnly ? "停用" : "卸载";
        return Result.error("无法" + action + "：以下应用仍依赖该插件，请先" + action + "或解除依赖：" + detail);
    }

    public List<DependentApplication> findDependents(String targetPluginId, boolean enabledDependentsOnly) {
        List<DependentApplication> result = new ArrayList<>();
        if (targetPluginId == null || targetPluginId.isBlank()) {
            return result;
        }
        String normalizedTarget = targetPluginId.trim();

        List<SysApplication> candidates = enabledDependentsOnly
                ? applicationDao.findEnabledByDependencyPluginId(normalizedTarget)
                : applicationDao.findByDependencyPluginId(normalizedTarget);

        for (SysApplication candidate : candidates) {
            if (candidate == null || candidate.getPluginId() == null) {
                continue;
            }
            if (normalizedTarget.equals(candidate.getPluginId())) {
                continue;
            }
            if (!declaresDependency(candidate, normalizedTarget)) {
                continue;
            }
            DependentApplication info = new DependentApplication();
            info.setPluginId(candidate.getPluginId());
            info.setApplicationName(candidate.getApplicationName());
            info.setPluginVersion(candidate.getPluginVersion());
            info.setEnabled(candidate.isEnabled());
            result.add(info);
        }
        return result;
    }

    private boolean declaresDependency(SysApplication application, String targetPluginId) {
        return parseDependencyPluginIds(application.getDependencyPluginIds()).contains(targetPluginId);
    }

    public List<String> parseDependencyPluginIds(String dependencyPluginIdsJson) {
        if (dependencyPluginIdsJson == null || dependencyPluginIdsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(dependencyPluginIdsJson, new TypeReference<List<String>>() {});
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.debug("解析 dependencyPluginIds 失败: {}", dependencyPluginIdsJson, e);
            return List.of();
        }
    }

    @Data
    public static class DependentApplication {
        private String pluginId;
        private String applicationName;
        private String pluginVersion;
        private boolean enabled;
    }
}
