package com.keqi.gress.plugin.appstore.dao;

import  org.springframework.beans.factory.annotation.Autowired;
import  org.springframework.stereotype.Service;
import  com.keqi.gress.plugin.api.database.page.IPage;
import  com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用数据访问层
 * 
 * <p>使用 PluginLambdaDataSource 进行数据访问</p>
 */
@Slf4j
@Service
public class ApplicationDao {
    
    @Autowired
    private PluginLambdaDataSource dataSource;
    
    /**
     * 分页查询应用列表（带过滤）
     * 
     * <p>使用 Lambda 链式 API 进行单表分页查询</p>
     */
    public IPage<SysApplication> queryApplicationsPage(Integer page, Integer size, String keyword, 
                                                     Integer status, String applicationType, String pluginId,
                                                     String clientType, Integer preloadEnabled, String tag) {
        // 构建查询条件
        var query = dataSource.lambdaQuery(SysApplication.class);
        
        // 关键词搜索（使用嵌套条件）
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            query.and() .like(SysApplication::getApplicationName, kw)
                    .or()
                    .like(SysApplication::getApplicationCode, kw)
                    .or()
                    .like(SysApplication::getPluginId, kw);
        }
        
        // 状态过滤
        if (status != null) {
            query.eq(SysApplication::getStatus, status);
        }
        
        // 应用类型过滤
        if (applicationType != null && !applicationType.trim().isEmpty()) {
            query.eq(SysApplication::getApplicationType, applicationType);
        } else {
            // 默认列表接口不返回聚合应用，聚合应用由 /applications/aggregates 独立管理
            query.ne(SysApplication::getApplicationType, "aggregated");
        }
        
        // 插件ID过滤
        if (pluginId != null && !pluginId.trim().isEmpty()) {
            query.eq(SysApplication::getPluginId, pluginId);
        }

        // 客户端类型过滤（B/C）
        if (clientType != null && !clientType.trim().isEmpty()) {
            query.eq(SysApplication::getClientType, clientType.trim());
        }

        // 预加载开关过滤（0/1）
        if (preloadEnabled != null) {
            query.eq(SysApplication::getPreloadEnabled, preloadEnabled);
        }

        // tag 过滤：tags 存为 JSON array string，按 `"tag"` 进行模糊匹配避免子串误伤
        if (tag != null && !tag.trim().isEmpty()) {
            String t = tag.trim().replace("\"", "");
            String pattern = "%\"" + t + "\"%";
            query.like(SysApplication::getTags, t);
        }
        
        // 按更新时间倒序，执行分页查询
        return query.orderByDesc(SysApplication::getUpdateTime)
                    .page(page, size);
    }
    
    /**
     * 根据ID查询应用详情
     */
    public SysApplication getApplicationById(Long id) {
        return dataSource.lambdaQuery(SysApplication.class)
                         .eq(SysApplication::getId, id)
                         .one();
    }
    
    /**
     * 根据应用代码查询应用
     */
    public SysApplication getApplicationByCode(String applicationCode) {
        return dataSource.lambdaQuery(SysApplication.class)
                         .eq(SysApplication::getApplicationCode, applicationCode)
                         .one();
    }
    
    /**
     * 根据插件ID查询应用
     */
    public SysApplication getApplicationByPluginId(String pluginId) {
        return dataSource.lambdaQuery(SysApplication.class)
                         .eq(SysApplication::getPluginId, pluginId)
                         .one();
    }
    
    /**
     * 更新应用版本
     */
    public int updateApplicationVersion(Long id, String version, String updateBy) {
        return dataSource.lambdaUpdate(SysApplication.class)
                         .set(SysApplication::getPluginVersion, version)
                         .set(SysApplication::getUpdatedBy, updateBy)
                         .eq(SysApplication::getId, id)
                         .update();
    }
    
    /**
     * 更新应用版本和插件类型
     */
    public int updateApplicationVersionAndType(Long id, String version, String pluginType, String updateBy) {
        return dataSource.lambdaUpdate(SysApplication.class)
                         .set(SysApplication::getPluginVersion, version)
                         .set(SysApplication::getPluginType, pluginType)
                         .set(SysApplication::getUpdatedBy, updateBy)
                         .eq(SysApplication::getId, id)
                         .update();
    }
    
    /**
     * 删除应用
     */
    public int deleteApplication(Long id) {
        return dataSource.lambdaUpdate(SysApplication.class)
                         .eq(SysApplication::getId, id)
                         .delete();
    }
    
    /**
     * 更新应用状态
     */
    public int updateApplicationStatus(Long id, Integer status, String updateBy) {
        return dataSource.lambdaUpdate(SysApplication.class)
                         .set(SysApplication::getStatus, status)
                         .set(SysApplication::getUpdatedBy, updateBy)
                         .eq(SysApplication::getId, id)
                         .update();
    }
    
    /**
     * 插入应用
     */
    public int insertApplication(SysApplication application) {
        return dataSource.insert(application);
    }
    
    /**
     * 更新应用扩展配置
     */
    public int updateApplicationExtensionConfig(Long id, String extensionConfig, String updateBy) {
        return dataSource.lambdaUpdate(SysApplication.class)
                         .set(SysApplication::getExtensionConfig, extensionConfig)
                         .set(SysApplication::getUpdatedBy, updateBy)
                         .eq(SysApplication::getId, id)
                         .update();
    }
    
    /**
     * 查询所有应用
     */
    public java.util.List<SysApplication> findAll() {
        return dataSource.lambdaQuery(SysApplication.class)
                         .list();
    }

    /**
     * 按应用类型查询
     */
    public java.util.List<SysApplication> findByApplicationType(String applicationType) {
        return dataSource.lambdaQuery(SysApplication.class)
                .eq(SysApplication::getApplicationType, applicationType)
                .list();
    }

    /**
     * 更新聚合应用基础信息
     */
    public int updateAggregateApplication(Long id,
                                          String applicationCode,
                                          String applicationName,
                                          String description,
                                          String icon,
                                          String extensionConfig,
                                          String updateBy) {
        return dataSource.lambdaUpdate(SysApplication.class)
                .set(SysApplication::getApplicationCode, applicationCode)
                .set(SysApplication::getApplicationName, applicationName)
                .set(SysApplication::getDescription, description)
                .set(SysApplication::getIcon, icon)
                .set(SysApplication::getExtensionConfig, extensionConfig)
                .set(SysApplication::getUpdatedBy, updateBy)
                .eq(SysApplication::getId, id)
                .update();
    }
}
