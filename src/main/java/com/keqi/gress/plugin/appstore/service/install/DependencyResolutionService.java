package com.keqi.gress.plugin.appstore.service.install;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.model.Result;
import  com.keqi.gress.common.plugin.PluginPackageLifecycle;
import  com.keqi.gress.common.plugin.PluginPackageInstallResult;
import com.keqi.gress.common.utils.VersionRangeMatcher;
import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import com.keqi.gress.plugin.appstore.service.AppStoreApiService;
import  com.keqi.gress.common.plugin.PluginPackageMetadataResult;
import com.keqi.gress.plugin.appstore.service.ApplicationInstallService;
import com.keqi.gress.plugin.appstore.service.persistence.ApplicationPersistenceService;

import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;

import org.apache.commons.lang3.StringUtils;

/**
 * 依赖解析与安装服务
 * 
 * 职责：
 * - 检查版本是否已安装
 * - 确保依赖已安装（递归安装缺失的依赖）
 * - 提供完整的从应用商店安装（含依赖链）的流程
 * - 遵循单一职责原则（SRP）：只负责依赖解析和安装
 * 
 * @author Gress Team
 */
@Service
public class DependencyResolutionService {
    private static final Log log = LogFactory.get(DependencyResolutionService.class);

    @Inject(source = Inject.BeanSource.SPRING)
    private PluginPackageLifecycle pluginPackageLifecycle;

    @Inject
    private AppStoreApiService appStoreApiService;
    
    @Inject
    private ApplicationInstallService applicationInstallService;
    
    @Inject
    private ApplicationPersistenceService persistenceService;

    /**
     * 升级链路依赖变更集：用于在主应用升级失败时回滚依赖变更。
     *
     * 规则：
     * - newlyInstalled：本次升级过程中“新安装”的依赖（之前未安装）——回滚时卸载
     * - upgradedBeforeVersion：本次升级过程中“被升级过”的依赖及其升级前版本——回滚时降级回旧版本
     */
    public static class DependencyChangeSet {
        private final java.util.List<String> newlyInstalled = new java.util.ArrayList<>();
        private final java.util.Map<String, String> upgradedBeforeVersion = new java.util.LinkedHashMap<>();

        public java.util.List<String> getNewlyInstalled() {
            return newlyInstalled;
        }

        public java.util.Map<String, String> getUpgradedBeforeVersion() {
            return upgradedBeforeVersion;
        }
    }

    /**
     * 检查指定版本是否已安装（基于 PluginPackageLifecycle.getMetadata）
     * 
     * @deprecated 使用 {@link #isVersionSatisfied(String, String, String)} 代替，支持版本范围匹配
     */
    @Deprecated
    public boolean isVersionAlreadyInstalled(String pluginId, String version) {
        if (pluginId == null || pluginId.isEmpty() || version == null || version.isEmpty()) {
            return false;
        }
        try {
            Result<PluginPackageMetadataResult> metaResult =
                    pluginPackageLifecycle.getMetadata(pluginId);
            if (!metaResult.isSuccess() || metaResult.getData() == null) {
                return false;
            }
            String installedVersion = metaResult.getData().getVersion();
            return version.equals(installedVersion);
        } catch (Exception e) {
            log.warn("检查插件包版本是否已安装失败: pluginId={}, version={}", pluginId, version, e);
            return false;
        }
    }
    
    /**
     * 检查已安装版本是否满足版本要求（支持版本范围）
     * 
     * @param pluginId 插件ID
     * @param targetVersion 目标版本号（可选）
     * @param versionRange 版本范围要求（如 ">=1.0.0", "1.0.0", "*"）
     * @return true 如果已安装版本满足要求
     */
    public boolean isVersionSatisfied(String pluginId, String targetVersion, String versionRange) {
        if (pluginId == null || pluginId.isEmpty()) {
            return false;
        }
        
        String installedVersion = getInstalledVersion(pluginId);
        if (installedVersion == null) {
            log.debug("插件未安装: pluginId={}", pluginId);
            return false;
        }
        
        // 如果指定了版本范围，检查是否满足范围要求
        if (versionRange != null && !versionRange.trim().isEmpty()) {
            boolean satisfied = VersionRangeMatcher.matches(installedVersion, versionRange);
            log.debug("版本范围检查: pluginId={}, installed={}, range={}, satisfied={}", 
                    pluginId, installedVersion, versionRange, satisfied);
            return satisfied;
        }
        
        // 如果没有版本范围但指定了目标版本，检查精确匹配
        if (targetVersion != null && !targetVersion.trim().isEmpty()) {
            boolean matched = installedVersion.equals(targetVersion);
            log.debug("精确版本检查: pluginId={}, installed={}, target={}, matched={}", 
                    pluginId, installedVersion, targetVersion, matched);
            return matched;
        }
        
        // 如果既没有版本范围也没有目标版本，认为已安装即满足
        log.debug("无版本要求，已安装即满足: pluginId={}, installed={}", pluginId, installedVersion);
        return true;
    }

    /**
     * 获取当前已安装版本（若未安装返回 null）。
     */
    private String getInstalledVersion(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return null;
        }
        try {
            Result<PluginPackageMetadataResult> metaResult = pluginPackageLifecycle.getMetadata(pluginId);
            if (!metaResult.isSuccess() || metaResult.getData() == null) {
                return null;
            }
            return metaResult.getData().getVersion();
        } catch (Exception e) {
            log.warn("获取已安装版本失败: pluginId={}", pluginId, e);
            return null;
        }
    }

    /**
     * 确保依赖已安装：对缺失的依赖递归安装，任一失败则智能回滚
     * 
     * 智能回滚机制：
     * - 记录本次新安装的依赖
     * - 如果安装失败，只回滚本次新安装的依赖（不影响已存在的）
     * - 回滚按照安装的反向顺序进行（先装的后卸）
     * 
     * @param pluginId   主插件ID
     * @param version    主插件版本
     * @param operator   操作人
     */
    public Result<Void> ensureDependenciesInstalled(String pluginId, String version, String operator) {
        java.util.List<String> newlyInstalledDeps = new java.util.ArrayList<>();  // 记录本次新安装的依赖
        
        try {
            // 获取应用详情（含依赖信息）
            ApplicationDTO appDetail = appStoreApiService.getApplicationVersionDetail(pluginId, version);
            if (appDetail == null || appDetail.getDependencies() == null || appDetail.getDependencies().isEmpty()) {
                log.info("应用无依赖或获取依赖信息失败，跳过依赖检查: pluginId={}, version={}", pluginId, version);
                return Result.success();
            }

            // 遍历依赖列表，逐个检查并安装
            for (ApplicationDTO.DependencyInfo dep : appDetail.getDependencies()) {
                String depPluginId = dep.getPluginId();
                String depVersion = dep.getVersion();
                String depVersionRange = dep.getVersionRange();
                
                if (depPluginId == null || depPluginId.isEmpty()) {
                    log.warn("依赖插件ID为空，跳过: pluginId={}, version={}", pluginId, version);
                    continue;
                }
                
                // 使用版本范围检查依赖是否已满足
                if (isVersionSatisfied(depPluginId, depVersion, depVersionRange)) {
                    String installedVersion = getInstalledVersion(depPluginId);
                    log.info("依赖已安装且满足版本要求，跳过: pluginId={}, installed={}, required={}, range={}", 
                            depPluginId, installedVersion, depVersion, depVersionRange);
                    continue;
                }
                
                // 检查是否需要升级
                String installedVersion = getInstalledVersion(depPluginId);
                if (installedVersion != null) {
                    // 已安装但不满足版本要求，需要升级
                    log.info("依赖已安装但版本不满足要求，需要升级: pluginId={}, installed={}, required={}, range={}", 
                            depPluginId, installedVersion, depVersion, depVersionRange);
                    
                    // 使用目标版本（如果指定）
                    String targetVersion = (depVersion != null && !depVersion.isEmpty()) ? depVersion : null;
                    if (targetVersion == null) {
                        log.warn("依赖需要升级但未指定目标版本，跳过: pluginId={}", depPluginId);
                        continue;
                    }
                    
                    // 下载并升级依赖
                    String depFileUrl = appStoreApiService.downloadApplication(depPluginId, targetVersion);
                    if (depFileUrl == null || depFileUrl.isEmpty()) {
                        String msg = "依赖下载失败: " + depPluginId + "@" + targetVersion;
                        log.error(msg);
                        
                        // 安装失败，执行智能回滚
                        log.warn("检测到依赖下载失败，开始回滚本次新安装的 {} 个依赖...", newlyInstalledDeps.size());
                        rollbackNewlyInstalled(newlyInstalledDeps, operator);
                        
                        return Result.error(msg + "（已回滚本次安装的依赖）");
                    }
                    
                    Result<com.keqi.gress.common.plugin.PluginPackageUpgradeResult> upgradeResult =
                            applicationInstallService.upgradeApplication(depPluginId, depFileUrl, null, null);
                    
                    if (!upgradeResult.isSuccess()) {
                        String msg = "依赖升级失败: " + depPluginId + " - " + upgradeResult.getErrorMessage();
                        log.error(msg);
                        
                        // 升级失败，执行智能回滚
                        log.warn("检测到依赖升级失败，开始回滚本次新安装的 {} 个依赖...", newlyInstalledDeps.size());
                        rollbackNewlyInstalled(newlyInstalledDeps, operator);
                        
                        return Result.error(msg + "（已回滚本次安装的依赖）");
                    }
                    
                    log.info("依赖升级成功: pluginId={}, from={}, to={}", depPluginId, installedVersion, targetVersion);
                    continue;
                }
                
                // 未安装，递归安装依赖（含其依赖链）
                log.info("开始安装依赖插件（含其依赖链）: pluginId={}, version={}", depPluginId, depVersion);
                Result<PluginPackageInstallResult> depResult = 
                        installWithDependencies(depPluginId, depVersion, operator);
                
                if (!depResult.isSuccess()) {
                    String msg = "依赖插件安装失败: " + depPluginId + " - " + depResult.getErrorMessage();
                    log.error(msg);
                    
                    // 安装失败，执行智能回滚
                    log.warn("检测到依赖安装失败，开始回滚本次新安装的 {} 个依赖...", newlyInstalledDeps.size());
                    rollbackNewlyInstalled(newlyInstalledDeps, operator);
                    
                    return Result.error(msg + "（已回滚本次安装的依赖）");
                }
                
                // 安装成功，记录到新安装列表
                newlyInstalledDeps.add(depPluginId);
                log.info("依赖安装成功并已记录: pluginId={}, 当前新安装数量={}", depPluginId, newlyInstalledDeps.size());
            }

            log.info("所有依赖检查完成，本次新安装 {} 个依赖", newlyInstalledDeps.size());
            return Result.success();
            
        } catch (Exception e) {
            String msg = "检查并安装依赖失败: pluginId=" + pluginId +
                    ", version=" + version + ", error=" + e.getMessage();
            log.error(msg, e);
            
            // 异常时也要回滚
            log.warn("检测到异常，开始回滚本次新安装的 {} 个依赖...", newlyInstalledDeps.size());
            rollbackNewlyInstalled(newlyInstalledDeps, operator);
            
            return Result.error(msg + "（已回滚本次安装的依赖）");
        }
    }

    /**
     * 升级场景：确保依赖满足目标版本（若依赖已安装但版本不同，则执行升级到目标版本）。
     *
     * 关键点：
     * - 会生成 DependencyChangeSet，供上层在“主应用升级失败”等场景统一回滚
     * - 若在依赖安装/升级阶段失败，会立即回滚本次已做的依赖变更，并返回错误
     *
     * @param pluginId   主插件ID
     * @param version    主插件目标版本
     * @param operator   操作人
     * @return 成功则返回变更集；失败则返回错误（并已尽力回滚）
     */
    public Result<DependencyChangeSet> ensureDependenciesUpgradedWithRollback(
            String pluginId, String version, String operator) {
        DependencyChangeSet changeSet = new DependencyChangeSet();

        try {
            ApplicationDTO appDetail = appStoreApiService.getApplicationVersionDetail(pluginId, version);
            if (appDetail == null || appDetail.getDependencies() == null || appDetail.getDependencies().isEmpty()) {
                log.info("升级场景：无依赖或获取依赖信息失败，跳过: pluginId={}, version={}", pluginId, version);
                return Result.success(changeSet);
            }

            for (ApplicationDTO.DependencyInfo dep : appDetail.getDependencies()) {
                String depPluginId = dep.getPluginId();
                String depTargetVersion = dep.getVersion();
                String depVersionRange = dep.getVersionRange();
                
                if (depPluginId == null || depPluginId.isEmpty()) {
                    log.warn("升级场景：依赖插件ID为空，跳过: pluginId={}, version={}", pluginId, version);
                    continue;
                }

                String installedVersion = getInstalledVersion(depPluginId);
                
                // 检查是否满足版本要求
                if (installedVersion != null && isVersionSatisfied(depPluginId, depTargetVersion, depVersionRange)) {
                    log.info("升级场景：依赖版本已满足要求，跳过: pluginId={}, installed={}, required={}, range={}", 
                            depPluginId, installedVersion, depTargetVersion, depVersionRange);
                    continue;
                }
                
                if (installedVersion == null) {
                    // 未安装：安装依赖（会递归处理其依赖链；内部失败会回滚它自己的 newlyInstalled）
                    log.info("升级场景：依赖未安装，开始安装: pluginId={}, version={}", depPluginId, depTargetVersion);
                    Result<PluginPackageInstallResult> installResult =
                            installWithDependencies(depPluginId, depTargetVersion, operator);
                    if (!installResult.isSuccess()) {
                        String msg = "升级场景依赖安装失败: " + depPluginId + " - " + installResult.getErrorMessage();
                        log.error(msg);
                        // 回滚本次已完成的依赖变更
                        rollbackDependencyChangeSet(changeSet, operator);
                        return Result.error(msg + "（已回滚本次依赖变更）");
                    }
                    changeSet.getNewlyInstalled().add(depPluginId);
                    continue;
                }

                // 已安装但不满足版本要求：升级到目标版本
                if (depTargetVersion != null && !depTargetVersion.isEmpty()) {
                    log.info("升级场景：依赖需要升级: pluginId={}, from={}, to={}, range={}", 
                            depPluginId, installedVersion, depTargetVersion, depVersionRange);
                    // 记录升级前版本（只记录一次）
                    changeSet.getUpgradedBeforeVersion().putIfAbsent(depPluginId, installedVersion);

                    String depFileUrl = appStoreApiService.downloadApplication(depPluginId, depTargetVersion);
                    if (depFileUrl == null || depFileUrl.isEmpty()) {
                        String msg = "升级场景依赖下载失败: " + depPluginId + "@" + depTargetVersion;
                        log.error(msg);
                        rollbackDependencyChangeSet(changeSet, operator);
                        return Result.error(msg + "（已回滚本次依赖变更）");
                    }

                    Result< com.keqi.gress.common.plugin.PluginPackageUpgradeResult> upgradeResult =
                            applicationInstallService.upgradeApplication(depPluginId, depFileUrl, null, null);
                    if (!upgradeResult.isSuccess()) {
                        String msg = "升级场景依赖升级失败: " + depPluginId + " - " + upgradeResult.getErrorMessage();
                        log.error(msg);
                        rollbackDependencyChangeSet(changeSet, operator);
                        return Result.error(msg + "（已回滚本次依赖变更）");
                    }
                } else {
                    log.warn("升级场景：依赖不满足版本要求但未指定目标版本，跳过: pluginId={}, installed={}, range={}", 
                            depPluginId, installedVersion, depVersionRange);
                }
            }

            return Result.success(changeSet);
        } catch (Exception e) {
            log.error("升级场景依赖处理异常: pluginId={}, version={}", pluginId, version, e);
            rollbackDependencyChangeSet(changeSet, operator);
            return Result.error("升级场景依赖处理失败: " + e.getMessage() + "（已回滚本次依赖变更）");
        }
    }

    /**
     * 升级场景：根据变更集回滚依赖变更。
     *
     * - upgradedBeforeVersion：按记录反向回滚到旧版本
     * - newlyInstalled：按反向卸载
     */
    public void rollbackDependencyChangeSet(DependencyChangeSet changeSet, String operator) {
        if (changeSet == null) {
            return;
        }

        // 1) 回滚已升级的依赖到旧版本（反向）
        if (changeSet.getUpgradedBeforeVersion() != null && !changeSet.getUpgradedBeforeVersion().isEmpty()) {
            java.util.List<java.util.Map.Entry<String, String>> entries =
                    new java.util.ArrayList<>(changeSet.getUpgradedBeforeVersion().entrySet());
            java.util.Collections.reverse(entries);

            for (java.util.Map.Entry<String, String> entry : entries) {
                String depPluginId = entry.getKey();
                String oldVersion = entry.getValue();
                try {
                    if (depPluginId == null || depPluginId.isEmpty() || oldVersion == null || oldVersion.isEmpty()) {
                        continue;
                    }
                    log.warn("回滚依赖版本: pluginId={}, toOldVersion={}", depPluginId, oldVersion);
                    String oldFileUrl = appStoreApiService.downloadApplication(depPluginId, oldVersion);
                    if (oldFileUrl == null || oldFileUrl.isEmpty()) {
                        log.error("回滚依赖版本下载失败: pluginId={}, oldVersion={}", depPluginId, oldVersion);
                        continue;
                    }
                    Result< com.keqi.gress.common.plugin.PluginPackageUpgradeResult> rollbackResult =
                            applicationInstallService.upgradeApplication(
                                    depPluginId,
                                    oldFileUrl,
                                    com.keqi.gress.plugin.appstore.dto.ApplicationUpgradeRequest.OperationType.ROLLBACK,
                                    null
                            );
                    if (!rollbackResult.isSuccess()) {
                        log.error("回滚依赖版本失败: pluginId={}, oldVersion={}, error={}",
                                depPluginId, oldVersion, rollbackResult.getErrorMessage());
                    }
                } catch (Exception e) {
                    log.error("回滚依赖版本异常: pluginId={}, oldVersion={}", depPluginId, oldVersion, e);
                }
            }
        }

        // 2) 卸载本次新安装的依赖（反向）
        if (changeSet.getNewlyInstalled() != null && !changeSet.getNewlyInstalled().isEmpty()) {
            java.util.List<String> deps = new java.util.ArrayList<>(changeSet.getNewlyInstalled());
            java.util.Collections.reverse(deps);
            for (String depPluginId : deps) {
                try {
                    if (depPluginId == null || depPluginId.isEmpty()) {
                        continue;
                    }
                    log.warn("回滚卸载新安装依赖: pluginId={}", depPluginId);
                    Result<?> uninstallResult = pluginPackageLifecycle.uninstall(depPluginId);
                    if (!uninstallResult.isSuccess()) {
                        log.error("回滚卸载依赖失败: pluginId={}, error={}", depPluginId, uninstallResult.getErrorMessage());
                        continue;
                    }
                    // 删除DB记录（尽力）
                    try {
                        com.keqi.gress.plugin.appstore.domain.entity.SysApplication app = persistenceService.findByPluginId(depPluginId);
                        if (app != null) {
                            persistenceService.deleteById(app.getId());
                        }
                    } catch (Exception e) {
                        log.warn("回滚卸载依赖后删除DB记录失败: pluginId={}", depPluginId, e);
                    }
                } catch (Exception e) {
                    log.error("回滚卸载新安装依赖异常: pluginId={}", depPluginId, e);
                }
            }
        }
    }
    
    /**
     * 回滚本次新安装的依赖
     * 
     * 回滚策略：
     * - 按照安装的反向顺序进行卸载（后装的先卸）
     * - 只卸载本次新安装的依赖，不影响已存在的依赖
     * - 卸载失败不会阻断整个回滚流程，会继续卸载其他依赖
     * - 详细记录回滚日志
     * 
     * @param newlyInstalledDeps 本次新安装的依赖列表
     * @param operator 操作人
     */
    private void rollbackNewlyInstalled(java.util.List<String> newlyInstalledDeps, String operator) {
        if (newlyInstalledDeps == null || newlyInstalledDeps.isEmpty()) {
            log.info("无需回滚：没有本次新安装的依赖");
            return;
        }
        
        log.info("开始智能回滚，共 {} 个依赖需要卸载", newlyInstalledDeps.size());
        
        // 反向遍历（后装的先卸）
        java.util.Collections.reverse(newlyInstalledDeps);
        
        int successCount = 0;
        int failCount = 0;
        
        for (String pluginId : newlyInstalledDeps) {
            try {
                log.info("回滚依赖: pluginId={}", pluginId);
                
                // 1. 卸载插件包
                Result<?> uninstallResult = pluginPackageLifecycle.uninstall(pluginId);
                if (!uninstallResult.isSuccess()) {
                    log.warn("回滚时卸载插件包失败: pluginId={}, error={}", pluginId, uninstallResult.getErrorMessage());
                    failCount++;
                    continue;  // 继续卸载其他依赖
                }
                
                // 2. 从数据库删除应用记录
                try {
                    com.keqi.gress.plugin.appstore.domain.entity.SysApplication app = 
                            persistenceService.findByPluginId(pluginId);
                    if (app != null) {
                        persistenceService.deleteById(app.getId());
                        log.info("回滚成功：已删除数据库记录: pluginId={}", pluginId);
                    }
                } catch (Exception e) {
                    log.warn("回滚时删除数据库记录失败: pluginId={}", pluginId, e);
                    // 插件已卸载，数据库删除失败不是致命问题
                }
                
                successCount++;
                log.info("依赖回滚成功: pluginId={}", pluginId);
                
            } catch (Exception e) {
                log.error("回滚依赖时发生异常: pluginId={}", pluginId, e);
                failCount++;
                // 继续回滚其他依赖
            }
        }
        
        log.info("智能回滚完成：成功 {} 个，失败 {} 个", successCount, failCount);
    }
    
    /**
     * 从应用商店安装应用（含依赖链，支持智能回滚）
     * 
     * 流程：
     * 1. 检查版本是否已安装
     * 2. 递归安装所有依赖（内部已支持智能回滚）
     * 3. 下载并安装主应用
     * 4. 保存到数据库
     * 5. 保存表权限
     * 
     * 注意：依赖安装过程已经内置智能回滚机制，本次新安装的依赖会在失败时自动回滚
     * 
     * @param pluginId 插件ID
     * @param version 版本号
     * @param operatorName 操作人
     * @return 安装结果
     */
    public Result<PluginPackageInstallResult> installWithDependencies(
            String pluginId, String version, String operatorName) {
        try {
            log.info("开始从应用商店安装应用（含依赖链）: pluginId={}, version={}, operator={}", 
                    pluginId, version, operatorName);
            
            // 1. 检查版本是否已安装（支持版本范围）
            ApplicationDTO appDetail = appStoreApiService.getApplicationVersionDetail(pluginId, version);
            if (appDetail == null) {
                String msg = "获取应用详情失败: " + pluginId;
                log.error(msg);
                return Result.error(msg);
            }
            if(StringUtils.isBlank(version)){
                version = appDetail.getPluginVersion();
            }
            
            // 使用版本范围检查（如果应用详情中有版本范围信息）
            String versionRange = null; // 主应用通常没有版本范围，只有精确版本
            if (isVersionSatisfied(pluginId, version, versionRange)) {
                String installedVersion = getInstalledVersion(pluginId);
                log.warn("版本已安装且满足要求，跳过: pluginId={}, installed={}, required={}", 
                        pluginId, installedVersion, version);
                return Result.error("版本已安装: " + installedVersion);
            }
            
            // 2. 获取应用详情（含依赖）已在上面获取
            
            // 3. 递归安装所有依赖（内部已支持智能回滚）
            Result<Void> depResult = ensureDependenciesInstalled(pluginId, version, operatorName);
            if (!depResult.isSuccess()) {
                // 依赖安装失败时，已在 ensureDependenciesInstalled 内部完成回滚
                return Result.error("依赖安装失败: " + depResult.getErrorMessage());
            }
            
            // 4. 从应用商店下载主应用包
            log.info("从应用商店下载应用包: pluginId={}, version={}", pluginId, version);
            String fileUrl = appStoreApiService.downloadApplication(pluginId, version);
            
            if (fileUrl == null || fileUrl.isEmpty()) {
                String errorMsg = "从应用商店下载应用包失败";
                log.error(errorMsg);
                return Result.error(errorMsg);
            }
            
            log.info("应用包下载成功: fileUrl={}", fileUrl);
            
            // 5. 安装应用
            Result<PluginPackageInstallResult> installResult = 
                    applicationInstallService.installApplication(fileUrl);
            
            if (!installResult.isSuccess()) {
                log.error("安装应用失败: {}", installResult.getErrorMessage());
                return installResult;
            }
            
            PluginPackageInstallResult installInfo = installResult.getData();
            log.info("应用安装成功: packageId={}, version={}", 
                    installInfo.getPackageId(), installInfo.getVersion());
            
            // 6. 保存应用信息到数据库
            try {
                persistenceService.saveApplication(installInfo, operatorName);
            } catch (Exception e) {
                log.warn("保存应用信息到数据库失败，但插件已安装成功: packageId={}", 
                        installInfo.getPackageId(), e);
            }
            
            // 7. 从应用商店获取表权限并保存
            try {
                persistenceService.saveTablePermissionsFromAppStore(
                        installInfo.getPackageId(), operatorName);
            } catch (Exception e) {
                log.warn("获取并保存表权限失败，但插件已安装成功: packageId={}", 
                        installInfo.getPackageId(), e);
            }
            
            return installResult;
            
        } catch (Exception e) {
            log.error("从应用商店安装应用失败: pluginId={}, version={}", pluginId, version, e);
            return Result.error("安装应用失败: " + e.getMessage());
        }
    }
}











