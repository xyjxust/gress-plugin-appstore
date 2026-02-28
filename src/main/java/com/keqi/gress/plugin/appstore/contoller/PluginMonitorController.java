package com.keqi.gress.plugin.appstore.contoller;

import com.keqi.gress.common.model.Result;
import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.dto.monitor.MonitorOverview;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorDetail;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorHistory;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorStatus;
import com.keqi.gress.plugin.appstore.service.monitor.PluginMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 插件监控控制器
 * 
 * 提供插件监控相关的 REST API 接口，包括：
 * - 获取所有插件的监控状态
 * - 获取单个插件的详细监控信息
 * - 获取监控概览信息
 * 
 * 所有接口返回统一的 Result 包装对象
 */
@Slf4j
@Service
@RestController
@RequestMapping("/monitor")
public class PluginMonitorController {
    
    @Inject
    private PluginMonitorService monitorService;
    
    /**
     * 获取所有插件的监控状态
     * 
     * 返回所有已安装插件的基本监控信息，包括：
     * - 插件ID、名称、版本
     * - 运行状态（STARTED/STOPPED/NOT_LOADED等）
     * - 加载状态
     * - 内存使用信息
     * - 错误信息（如果有）
     * 
     * 该接口支持缓存机制，默认缓存5秒，以提高性能
     * 
     * @return 所有插件的监控状态列表
     */
    @GetMapping("/status")
    public Result<List<PluginMonitorStatus>> getAllPluginStatus() {
        log.info("获取所有插件监控状态");
        
        try {
            Result<List<PluginMonitorStatus>> result = monitorService.getAllPluginStatus();
            
            if (result.isSuccess()) {
                log.info("成功获取 {} 个插件的监控状态", 
                        result.getData() != null ? result.getData().size() : 0);
            } else {
                log.warn("获取插件监控状态失败: {}", result.getErrorMessage());
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取所有插件监控状态时发生异常", e);
            return Result.error("获取插件监控状态失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取单个插件的详细监控信息
     * 
     * 返回指定插件的详细监控信息，包括：
     * - 基本状态信息
     * - 内存使用详情（插件内存、JVM内存）
     * - 插件元数据（作者、描述、主页等）
     * - 类加载器信息
     * - 配置信息
     * - 运行时信息（启动时间、运行时长）
     * 
     * @param pluginId 插件ID
     * @return 插件详细监控信息
     */
    @GetMapping("/status/{pluginId}")
    public Result<PluginMonitorDetail> getPluginDetail(@PathVariable String pluginId) {
        log.info("获取插件详细监控信息: pluginId={}", pluginId);
        
        try {
            Result<PluginMonitorDetail> result = monitorService.getPluginDetail(pluginId);
            
            if (result.isSuccess()) {
                log.info("成功获取插件详细信息: pluginId={}", pluginId);
            } else {
                log.warn("获取插件详细信息失败: pluginId={}, error={}", 
                        pluginId, result.getErrorMessage());
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取插件详细监控信息时发生异常: pluginId={}", pluginId, e);
            return Result.error("获取插件详细信息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取监控概览信息
     * 
     * 返回系统级别的监控概览，包括：
     * - 总插件数
     * - 运行中插件数
     * - 已停止插件数
     * - 异常插件数
     * - 总内存使用量
     * 
     * 该接口适用于监控仪表板的概览卡片展示
     * 
     * @return 监控概览信息
     */
    @GetMapping("/overview")
    public Result<MonitorOverview> getMonitorOverview() {
        log.info("获取监控概览信息");
        
        try {
            Result<MonitorOverview> result = monitorService.getMonitorOverview();
            
            if (result.isSuccess()) {
                MonitorOverview overview = result.getData();
                log.info("成功获取监控概览: total={}, running={}, stopped={}, error={}", 
                        overview != null ? overview.getTotalPlugins() : 0,
                        overview != null ? overview.getRunningPlugins() : 0,
                        overview != null ? overview.getStoppedPlugins() : 0,
                        overview != null ? overview.getErrorPlugins() : 0);
            } else {
                log.warn("获取监控概览失败: {}", result.getErrorMessage());
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取监控概览信息时发生异常", e);
            return Result.error("获取监控概览失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取插件的历史监控数据
     * 
     * 返回指定插件在指定时间范围内的历史监控数据，包括：
     * - 插件状态变化历史
     * - 内存使用趋势
     * - 快照时间戳
     * 
     * 支持的时间范围格式：
     * - "1h" = 最近1小时
     * - "24h" = 最近24小时
     * - "7d" = 最近7天
     * 
     * @param pluginId 插件ID
     * @param timeRange 时间范围（默认 "1h"）
     * @return 插件历史监控数据列表
     */
    @GetMapping("/history/{pluginId}")
    public Result<List<PluginMonitorHistory>> getPluginHistory(
            @PathVariable String pluginId,
            @RequestParam(defaultValue = "1h") String timeRange) {
        log.info("获取插件历史监控数据: pluginId={}, timeRange={}", pluginId, timeRange);
        
        try {
            Result<List<PluginMonitorHistory>> result = monitorService.getPluginHistory(pluginId, timeRange);
            
            if (result.isSuccess()) {
                log.info("成功获取插件历史数据: pluginId={}, 记录数={}", 
                        pluginId, result.getData() != null ? result.getData().size() : 0);
            } else {
                log.warn("获取插件历史数据失败: pluginId={}, error={}", 
                        pluginId, result.getErrorMessage());
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取插件历史监控数据时发生异常: pluginId={}", pluginId, e);
            return Result.error("获取历史数据失败: " + e.getMessage(), e);
        }
    }
}
