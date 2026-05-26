package com.keqi.gress.plugin.appstore.service.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import  com.keqi.gress.common.plugin.PluginPackageInstallResult;
import com.keqi.gress.common.plugin.PluginConfigMetadataProvider;
import  org.springframework.beans.factory.annotation.Autowired;
import  org.springframework.stereotype.Service;
import  com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.dao.ApplicationDao;
import com.keqi.gress.plugin.appstore.dao.ApplicationUpgradeLogDao;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplicationUpgradeLog;
import com.keqi.gress.plugin.appstore.service.AppStoreApiService;
import com.keqi.gress.plugin.appstore.support.PluginUiExtensionConfigFactory;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用持久化服务
 * 
 * 职责：
 * - 负责应用数据的 CRUD 操作
 * - 负责升级日志的持久化
 * - 负责表权限的保存
 * - 遵循单一职责原则（SRP）：只负责数据持久化
 * 
 * @author Gress Team
 */
@Service
@Slf4j
public class ApplicationPersistenceService {
    
   // private static final Log log = LogFactory.get(ApplicationPersistenceService.class);
    
    @Autowired
    private ApplicationDao applicationDao;
    
    @Autowired
    private ApplicationUpgradeLogDao applicationUpgradeLogDao;
    
    @Autowired
    private PluginLambdaDataSource dataSource;
    
    @Autowired
    private AppStoreApiService appStoreApiService;

    @Autowired(required = false)
    private PluginConfigMetadataProvider pluginConfigMetadataProvider;
    
    /**
     * 根据ID查找应用
     */
    public SysApplication findById(Long id) {
        return applicationDao.getApplicationById(id);
    }
    
    /**
     * 根据插件ID查找应用
     */
    public SysApplication findByPluginId(String pluginId) {
        return applicationDao.getApplicationByPluginId(pluginId);
    }
    
    /**
     * 根据ID删除应用
     */
    public boolean deleteById(Long id) {
        int rows = applicationDao.deleteApplication(id);
        return rows > 0;
    }
    
    /**
     * 更新应用状态
     */
    public boolean updateStatus(Long id, Integer status, String operatorName) {
        int rows = applicationDao.updateApplicationStatus(id, status, operatorName);
        return rows > 0;
    }
    
    /**
     * 更新应用版本和插件类型
     */
    public boolean updateVersionAndType(Long id, String version, String pluginType, String operatorName) {
        int rows;
        if (pluginType != null) {
            rows = applicationDao.updateApplicationVersionAndType(id, version, pluginType, operatorName);
        } else {
            rows = applicationDao.updateApplicationVersion(id, version, operatorName);
        }
        return rows > 0;
    }
    
    /**
     * 保存应用信息到数据库
     */
    public SysApplication saveApplication(PluginPackageInstallResult installInfo, String operatorName) {
        try {
            String packageId = installInfo.getPackageId();
            
            // 检查是否已存在
            SysApplication existing = applicationDao.getApplicationByPluginId(packageId);
            if (existing != null) {
                log.info("应用已存在，跳过保存: pluginId={}", packageId);
                return existing;
            }
            
            // 创建应用记录
            SysApplication application = new SysApplication();
            application.setApplicationCode(packageId);
            application.setApplicationName(packageId);
            application.setPluginId(packageId);
            application.setPluginVersion(installInfo.getVersion());
            application.setDescription(installInfo.getDescription());
            application.setAuthor(installInfo.getProvider());
            application.setIcon(installInfo.getIcon());
            application.setApplicationType("plugin");
            application.setStatus(1);
            application.setIsDefault(0);
            application.setInstallTime(LocalDateTime.now());
            application.setUpdateTime(LocalDateTime.now());
  
            
            // 设置插件类型
            if (installInfo.getPluginTypes() != null && !installInfo.getPluginTypes().isEmpty()) {
                String pluginType = String.join(",", installInfo.getPluginTypes());
                application.setPluginType(pluginType);
                log.info("设置插件类型: pluginId={}, pluginType={}", packageId, pluginType);
            } else {
                application.setPluginType("APPLICATION");
                log.warn("未检测到插件类型，使用默认值 APPLICATION: pluginId={}", packageId);
            }

            // 构建 extension_config：surfaces / autoLoad.{admin,consumer} / hasWidget / hasPanel
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> extConfig = new java.util.HashMap<>();
                boolean hasWidget = installInfo.isHasWidget();
                boolean hasPanel = installInfo.isHasPanel();

                Map<String, Object> pluginYmlConfig = installInfo.getPluginYmlConfig();
                Object pluginNode = pluginYmlConfig != null ? pluginYmlConfig.get("plugin") : null;
                if (pluginNode instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pluginMap = (Map<String, Object>) pluginNode;
                    if (pluginMap.containsKey("hasPanel")) {
                        hasPanel = parseBoolean(pluginMap.get("hasPanel"));
                    }
                    if (pluginMap.containsKey("hasWidget")) {
                        hasWidget = parseBoolean(pluginMap.get("hasWidget"));
                    }
                }

                PluginUiExtensionConfigFactory.putExtensionFlags(extConfig, pluginYmlConfig, hasWidget, hasPanel);

                // 完整性校验信息（Marketplace SHA-256 + 安装前后校验）
                try {
                    Map<String, String> meta = installInfo.getMetadata();
                    if (meta != null) {
                        String sha256Expected = meta.get("sha256Expected");
                        String sha256Before = meta.get("sha256Before");
                        String sha256After = meta.get("sha256After");
                        if (sha256Expected != null && !sha256Expected.isBlank()) {
                            extConfig.put("sha256Expected", sha256Expected);
                        }
                        if (sha256Before != null && !sha256Before.isBlank()) {
                            extConfig.put("sha256Before", sha256Before);
                        }
                        if (sha256After != null && !sha256After.isBlank()) {
                            extConfig.put("sha256After", sha256After);
                        }
                        if (sha256Expected != null && !sha256Expected.isBlank()
                                && sha256After != null && !sha256After.isBlank()) {
                            extConfig.put("sha256Verified", sha256Expected.equalsIgnoreCase(sha256After));
                        }
                    }
                } catch (Exception ignore) {
                }

                application.setExtensionConfig(objectMapper.writeValueAsString(extConfig));
                log.info("设置扩展配置: pluginId={}, surfaceAdmin={}, surfaceConsumer={}, autoLoadAdmin={}, autoLoadConsumer={}, hasPanel={}, hasWidget={}",
                        packageId, extConfig.get("surfaceAdmin"), extConfig.get("surfaceConsumer"),
                        extConfig.get("autoLoadAdmin"), extConfig.get("autoLoadConsumer"), hasPanel, hasWidget);
            } catch (Exception e) {
                log.warn("构建 extension_config 失败: pluginId={}, error={}", packageId, e.getMessage());
            }

            // 保存到数据库
            int rows = dataSource.insert(application);
            
            if (rows > 0) {
                log.info("应用信息保存成功: pluginId={}, version={}", packageId, installInfo.getVersion());
                return applicationDao.getApplicationByPluginId(packageId);
            } else {
                log.warn("应用信息保存失败: pluginId={}", packageId);
                return null;
            }
            
        } catch (Exception e) {
            log.error("保存应用信息到数据库失败", e);
            throw e;
        }
    }
    
    /**
     * 从应用商店获取表权限并保存到数据库
     */
    public void saveTablePermissionsFromAppStore(String pluginId, String operatorName) {
        try {
            log.info("开始从应用商店获取表权限: pluginId={}", pluginId);
            
            List<AppStoreApiService.PluginTablePermissionInfo> permissions = 
                    appStoreApiService.getTablePermissions(pluginId);
            
            if (permissions == null || permissions.isEmpty()) {
                log.info("应用商店中未找到表权限配置: pluginId={}", pluginId);
                return;
            }
            
            log.info("从应用商店获取到 {} 条表权限配置: pluginId={}", permissions.size(), pluginId);
            
            int savedCount = 0;
            LocalDateTime now = LocalDateTime.now();
            
            for (AppStoreApiService.PluginTablePermissionInfo permissionInfo : permissions) {
                try {
                    if (permissionInfo.getTableName() == null || permissionInfo.getTableName().trim().isEmpty()) {
                        log.warn("表名为空，跳过: pluginId={}", pluginId);
                        continue;
                    }
                    
                    String tableName = permissionInfo.getTableName().toLowerCase();
                    
                    // 检查是否已存在
                    String checkSql = "SELECT COUNT(*) as cnt FROM appstore_plugin_table_permission WHERE plugin_id = #{pluginId} AND table_name = #{tableName}";
                    List<Map<String, Object>> checkResult = dataSource.dynamicSql(checkSql)
                            .param("pluginId", pluginId)
                            .param("tableName", tableName)
                            .query();
                    
                    if (checkResult != null && !checkResult.isEmpty()) {
                        Object count = checkResult.get(0).get("cnt");
                        if (count != null && ((Number) count).intValue() > 0) {
                            log.debug("表权限已存在，跳过: pluginId={}, tableName={}", pluginId, tableName);
                            continue;
                        }
                    }
                    
                    // 插入新的表权限记录
                    String insertSql = """
                        INSERT INTO appstore_plugin_table_permission 
                        (plugin_id, table_name, allowed_operations, is_readonly, description, enabled, 
                         create_time, update_time, create_by, update_by)
                        VALUES (#{pluginId}, #{tableName}, #{allowedOperations}, #{isReadonly}, #{description}, #{enabled}, 
                                #{createTime}, #{updateTime}, #{createBy}, #{updateBy})
                        """;
                    
                    dataSource.dynamicSql(insertSql)
                            .param("pluginId", pluginId)
                            .param("tableName", tableName)
                            .param("allowedOperations", permissionInfo.getAllowedOperations())
                            .param("isReadonly", permissionInfo.getIsReadonly() != null ? permissionInfo.getIsReadonly() : true)
                            .param("description", permissionInfo.getDescription())
                            .param("enabled", permissionInfo.getEnabled() != null ? permissionInfo.getEnabled() : true)
                            .param("createTime", now)
                            .param("updateTime", now)
                            .param("createBy", operatorName)
                            .param("updateBy", operatorName)
                            .execute();
                    
                    savedCount++;
                    log.debug("保存表权限成功: pluginId={}, tableName={}", pluginId, tableName);
                    
                } catch (Exception e) {
                    log.warn("保存单个表权限失败: pluginId={}, tableName={}", 
                            pluginId, permissionInfo.getTableName(), e);
                }
            }
            
            log.info("表权限保存完成: pluginId={}, 总数={}, 保存={}", 
                    pluginId, permissions.size(), savedCount);
            
        } catch (Exception e) {
            log.error("从应用商店获取并保存表权限失败: pluginId={}", pluginId, e);
            throw e;
        }
    }
    
    /**
     * 保存升级日志
     */
    public void saveUpgradeLog(SysApplication application,
                              String oldVersion,
                              String newVersion,
                              String targetVersion,
                              String pluginType,
                              String operatorName,
                              String status,
                              String message) {
        try {
            SysApplicationUpgradeLog logEntry = new SysApplicationUpgradeLog();
            logEntry.setApplicationId(application.getId());
            logEntry.setPluginId(application.getPluginId());
            logEntry.setOldVersion(oldVersion);
            logEntry.setNewVersion(newVersion);
            logEntry.setTargetVersion(targetVersion);
            logEntry.setPluginType(pluginType);
            logEntry.setOperatorName(operatorName);
            logEntry.setStatus(status);
            logEntry.setMessage(message);
            applicationUpgradeLogDao.insert(logEntry);
        } catch (Exception e) {
            log.warn("保存升级日志失败", e);
        }
    }
    
    /**
     * 解析应用的扩展配置
     */
    public Map<String, Object> parseExtensionConfig(SysApplication application) {
        if (application.getExtensionConfig() == null || application.getExtensionConfig().trim().isEmpty()) {
            return null;
        }
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(
                    application.getExtensionConfig(), 
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析应用扩展配置失败: id={}, error={}", application.getId(), e.getMessage());
            return null;
        }
    }
    
    /**
     * 更新应用扩展配置
     */
    public boolean updateExtensionConfig(Long id, String configJson, String operatorName) {
        int updated = applicationDao.updateApplicationExtensionConfig(id, configJson, operatorName);
        return updated > 0;
    }

    /**
     * 追加并持久化安装前配置到 extension_config。
     */
    public boolean mergeExtensionConfigByPluginId(String pluginId, Map<String, Object> extraConfig, String operatorName) {
        if (pluginId == null || pluginId.isBlank() || extraConfig == null || extraConfig.isEmpty()) {
            return false;
        }

        SysApplication application = applicationDao.getApplicationByPluginId(pluginId);
        if (application == null) {
            log.warn("未找到应用记录，无法合并安装配置: pluginId={}", pluginId);
            return false;
        }

        try {
            Map<String, Object> merged = new LinkedHashMap<>();
            Map<String, Object> existing = parseExtensionConfig(application);
            if (existing != null && !existing.isEmpty()) {
                merged.putAll(existing);
            }
            merged.putAll(resolveEffectiveConfig(pluginId, extraConfig));

            String configJson = new ObjectMapper().writeValueAsString(merged);
            return updateExtensionConfig(application.getId(), configJson, operatorName);
        } catch (Exception e) {
            log.error("合并安装配置失败: pluginId={}", pluginId, e);
            return false;
        }
    }

    private Map<String, Object> resolveEffectiveConfig(String pluginId, Map<String, Object> fallbackConfig) {
        if (pluginConfigMetadataProvider == null) {
            return fallbackConfig;
        }
        try {
            Map<String, Object> flatConfig = pluginConfigMetadataProvider.getPluginPackageFlatConfig(pluginId);
            if (flatConfig != null && !flatConfig.isEmpty()) {
                return flatConfig;
            }
        } catch (Exception e) {
            log.warn("获取插件当前配置快照失败，回退到安装表单原始配置: pluginId={}, error={}",
                    pluginId, e.getMessage());
        }
        return fallbackConfig;
    }

    /**
     * 兼容插件清单 YAML 中布尔字段解析（SnakeYAML/JSON 可能返回 Boolean 或字符串）。
     */
    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}








