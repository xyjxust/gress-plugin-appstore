package com.keqi.gress.plugin.appstore.service.monitor;

import com.keqi.gress.common.model.Result;
import com.keqi.gress.common.plugin.PluginPackageLifecycle;
import com.keqi.gress.common.plugin.PluginPackageRuntimeInfo;
import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.dao.ApplicationDao;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplication;
import com.keqi.gress.plugin.appstore.dto.monitor.ClassLoaderInfo;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 插件状态收集器
 * 负责收集插件的运行状态信息
 * 
 * 使用 PluginPackageLifecycle 接口获取插件包的运行时信息，
 * 而不是直接访问 PluginManager。
 */
@Slf4j
@Service
public class PluginStatusCollector {
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginPackageLifecycle pluginLifecycle;
    
    @Inject
    private ApplicationDao applicationDao;
    
    /**
     * 收集所有插件的状态
     * 
     * @return 所有插件的状态列表
     */
    public List<PluginMonitorStatus> collectAllStatus() {
        log.debug("开始收集所有插件状态");
        
        List<SysApplication> applications = applicationDao.findAll();
        log.debug("从数据库查询到 {} 个应用", applications.size());
        
        return applications.stream()
                .map(this::collectStatusFromApplication)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 收集单个插件的状态
     * 
     * @param pluginId 插件ID
     * @return 插件状态，如果插件不存在则返回 null
     */
    public PluginMonitorStatus collectStatus(String pluginId) {
        log.debug("收集插件状态: pluginId={}", pluginId);
        
        SysApplication app = applicationDao.getApplicationByPluginId(pluginId);
        if (app == null) {
            log.warn("插件不存在: pluginId={}", pluginId);
            return null;
        }
        
        return collectStatusFromApplication(app);
    }
    
    /**
     * 从应用实体收集状态信息
     * 
     * @param app 应用实体
     * @return 插件监控状态
     */
    private PluginMonitorStatus collectStatusFromApplication(SysApplication app) {
        try {
            PluginMonitorStatus status = new PluginMonitorStatus();
            status.setPluginId(app.getPluginId());
            status.setPluginName(app.getApplicationName());
            status.setPluginVersion(app.getPluginVersion());
            
            // 获取插件包运行时信息
            Result<PluginPackageRuntimeInfo> result = pluginLifecycle.getPluginPackageRuntimeInfo(app.getPluginId());
            
            if (result.isSuccess() && result.getData() != null) {
                // 插件已加载
                PluginPackageRuntimeInfo runtimeInfo = result.getData();
                status.setLoaded(true);
                status.setState(runtimeInfo.getState());
                
                // 获取启动时间（如果可用）
                Long startTime = runtimeInfo.getStartTime();
                status.setStartTime(startTime);
                
                // 计算运行时长
                if (startTime != null) {
                    status.setUptime(System.currentTimeMillis() - startTime);
                }
                
                status.setHasError(false);
            } else {
                // 插件未加载
                status.setLoaded(false);
                status.setState("NOT_LOADED");
                status.setHasError(false);
            }
            
            return status;
        } catch (Exception e) {
            log.error("收集插件状态失败: pluginId={}", app.getPluginId(), e);
            
            // 返回错误状态
            PluginMonitorStatus errorStatus = new PluginMonitorStatus();
            errorStatus.setPluginId(app.getPluginId());
            errorStatus.setPluginName(app.getApplicationName());
            errorStatus.setPluginVersion(app.getPluginVersion());
            errorStatus.setLoaded(false);
            errorStatus.setState("ERROR");
            errorStatus.setHasError(true);
            errorStatus.setErrorMessage("收集状态失败: " + e.getMessage());
            
            return errorStatus;
        }
    }
    
    /**
     * 获取类加载器信息
     * 
     * @param pluginId 插件ID
     * @return 类加载器信息，如果插件未加载则返回 null
     */
    public ClassLoaderInfo getClassLoaderInfo(String pluginId) {
        log.debug("获取类加载器信息: pluginId={}", pluginId);
        
        Result<PluginPackageRuntimeInfo> result = pluginLifecycle.getPluginPackageRuntimeInfo(pluginId);
        if (!result.isSuccess() || result.getData() == null) {
            log.warn("插件未加载，无法获取类加载器信息: pluginId={}", pluginId);
            return null;
        }
        
        try {
            PluginPackageRuntimeInfo runtimeInfo = result.getData();
            PluginPackageRuntimeInfo.ClassLoaderInfo classLoaderInfo = runtimeInfo.getClassLoader();
            
            if (classLoaderInfo == null) {
                log.warn("插件运行时信息中没有类加载器信息: pluginId={}", pluginId);
                return null;
            }
            
            ClassLoaderInfo info = new ClassLoaderInfo();
            info.setClassName(classLoaderInfo.getClassName());
            info.setParentClassName(classLoaderInfo.getParentClassName());
            
            return info;
        } catch (Exception e) {
            log.error("获取类加载器信息失败: pluginId={}", pluginId, e);
            return null;
        }
    }
}
