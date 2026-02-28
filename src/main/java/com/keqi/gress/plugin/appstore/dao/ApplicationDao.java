package com.keqi.gress.plugin.appstore.dao;

import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
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
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginLambdaDataSource dataSource;
    
    /**
     * 分页查询应用列表（带过滤）
     * 
     * <p>使用 Lambda 链式 API 进行单表分页查询</p>
     */
    public IPage<SysApplication> queryApplicationsPage(Integer page, Integer size, String keyword, 
                                                     Integer status, String applicationType, String pluginId) {
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
        }
        
        // 插件ID过滤
        if (pluginId != null && !pluginId.trim().isEmpty()) {
            query.eq(SysApplication::getPluginId, pluginId);
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
                         .set(SysApplication::getUpdateBy, updateBy)
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
                         .set(SysApplication::getUpdateBy, updateBy)
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
                         .set(SysApplication::getUpdateBy, updateBy)
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
                         .set(SysApplication::getUpdateBy, updateBy)
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
}
