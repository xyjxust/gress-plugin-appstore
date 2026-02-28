package com.keqi.gress.plugin.appstore.service.monitor;

import com.keqi.gress.common.model.Result;
import com.keqi.gress.common.plugin.PluginPackageLifecycle;
import com.keqi.gress.common.plugin.PluginPackageMetadataResult;
import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.dao.MonitorHistoryDao;
import com.keqi.gress.plugin.appstore.dto.monitor.MonitorOverview;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMemoryInfo;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorDetail;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorStatus;
import com.keqi.gress.plugin.appstore.util.MonitorErrorHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 插件监控服务
 * 协调数据收集和缓存，提供统一的监控数据访问接口
 */
@Slf4j
@Service
public class PluginMonitorService {
    
    @Inject
    private PluginStatusCollector statusCollector;
    
    @Inject
    private PluginMemoryCollector memoryCollector;
    
    @Inject
    private MonitorDataCache monitorCache;
    
    @Inject
    private MonitorHistoryDao historyDao;
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginPackageLifecycle pluginLifecycle;
    
    /** 异步收集内存信息的超时时间（毫秒） */
    private static final long MEMORY_COLLECTION_TIMEOUT = 3000;
    
    /**
     * 获取所有插件的监控状态
     * 
     * 实现策略：
     * 1. 尝试从缓存获取数据
     * 2. 如果缓存不存在或已过期，收集新数据
     * 3. 异步收集内存信息以提高响应速度
     * 4. 更新缓存供下次使用
     * 
     * @return 所有插件的状态列表
     */
    public Result<List<PluginMonitorStatus>> getAllPluginStatus() {
        log.debug("获取所有插件监控状态");
        
        try {
            // 尝试从缓存获取
            List<PluginMonitorStatus> cached = monitorCache.getAllStatus();
            if (cached != null && !cached.isEmpty()) {
                log.debug("从缓存返回 {} 个插件状态", cached.size());
                return Result.success(cached);
            }
            
            // 收集所有插件状态
            List<PluginMonitorStatus> statusList = statusCollector.collectAllStatus();
            log.debug("收集到 {} 个插件状态", statusList.size());
            
            // 异步收集内存信息
            CompletableFuture.runAsync(() -> {
                try {
                    for (PluginMonitorStatus status : statusList) {
                        try {
                            if (status.getLoaded() != null && status.getLoaded()) {
                                PluginMemoryInfo memInfo = memoryCollector.collectMemoryInfo(status.getPluginId());
                                if (memInfo == null) {
                                    // 内存信息不可用，使用错误处理器提供默认值
                                    memInfo = MonitorErrorHandler.handleMemoryUnavailable(status.getPluginId());
                                }
                                status.setMemoryInfo(memInfo);
                                
                                // 检查内存告警
                                if (memInfo.getUsedMemory() != null) {
                                    boolean isWarning = memoryCollector.isMemoryWarning(memInfo.getUsedMemory());
                                    status.setIsMemoryWarning(isWarning);
                                    if (isWarning) {
                                        log.warn("插件内存使用超过阈值: pluginId={}, usedMemory={}, threshold={}", 
                                                status.getPluginId(), 
                                                memInfo.getFormattedMemory(),
                                                memoryCollector.formatMemorySize(memoryCollector.getMemoryWarningThreshold()));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 单个插件的内存收集失败不影响其他插件
                            log.warn("收集插件内存信息失败: pluginId={}", status.getPluginId(), e);
                            status.setMemoryInfo(MonitorErrorHandler.handleMemoryUnavailable(status.getPluginId()));
                            status.setIsMemoryWarning(false);
                        }
                    }
                    // 更新缓存
                    monitorCache.updateAllStatus(statusList);
                    log.debug("异步内存信息收集完成");
                } catch (Exception e) {
                    log.error("异步收集内存信息失败", e);
                }
            }).orTimeout(MEMORY_COLLECTION_TIMEOUT, TimeUnit.MILLISECONDS)
              .exceptionally(ex -> {
                  log.warn("内存信息收集超时或失败", ex);
                  // 超时时记录所有插件
                  for (PluginMonitorStatus status : statusList) {
                      if (status.getPluginId() != null) {
                          MonitorErrorHandler.handleTimeout(status.getPluginId());
                      }
                  }
                  return null;
              });
            
            return Result.success(statusList);
        } catch (Exception e) {
            log.error("获取所有插件状态失败", e);
            return Result.error("获取插件状态失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取单个插件的详细监控信息
     * 
     * 详细信息包括：
     * - 基本状态（运行状态、加载状态等）
     * - 内存使用信息
     * - 插件元数据（作者、描述、主页等）
     * - 类加载器信息
     * - 配置信息
     * 
     * @param pluginId 插件ID
     * @return 插件详细监控信息
     */
    public Result<PluginMonitorDetail> getPluginDetail(String pluginId) {
        log.debug("获取插件详细信息: pluginId={}", pluginId);
        
        try {
            // 收集基本状态
            PluginMonitorStatus status = null;
            try {
                status = statusCollector.collectStatus(pluginId);
            } catch (Exception e) {
                log.error("收集插件状态失败: pluginId={}", pluginId, e);
                status = MonitorErrorHandler.handleCollectionError(pluginId, e);
            }
            
            if (status == null) {
                log.warn("插件不存在: pluginId={}", pluginId);
                return Result.error("插件不存在: " + pluginId);
            }
            
            // 构建详细信息
            PluginMonitorDetail detail = PluginMonitorDetail.builder()
                    .status(status)
                    .build();
            
            // 收集内存信息（如果插件已加载）
            if (status.getLoaded() != null && status.getLoaded()) {
                try {
                    PluginMemoryInfo memInfo = memoryCollector.collectMemoryInfo(pluginId);
                    if (memInfo == null) {
                        memInfo = MonitorErrorHandler.handleMemoryUnavailable(pluginId);
                    }
                    detail.setMemoryInfo(memInfo);
                    
                    // 检查内存告警
                    if (memInfo.getUsedMemory() != null) {
                        boolean isWarning = memoryCollector.isMemoryWarning(memInfo.getUsedMemory());
                        status.setIsMemoryWarning(isWarning);
                    }
                    
                    // 获取类加载器信息
                    detail.setClassLoaderInfo(statusCollector.getClassLoaderInfo(pluginId));
                } catch (Exception e) {
                    log.warn("收集插件内存或类加载器信息失败: pluginId={}", pluginId, e);
                    detail.setMemoryInfo(MonitorErrorHandler.handleMemoryUnavailable(pluginId));
                    status.setIsMemoryWarning(false);
                }
            }
            
            // 获取插件元数据
            try {
                Result<PluginPackageMetadataResult> metadataResult = pluginLifecycle.getMetadata(pluginId);
                if (metadataResult.isSuccess() && metadataResult.getData() != null) {
                    Map<String, Object> metadata = convertMetadataToMap(metadataResult.getData());
                    detail.setMetadata(metadata);
                } else {
                    log.warn("获取插件元数据失败: pluginId={}, error={}", 
                            pluginId, metadataResult.getErrorMessage());
                    detail.setMetadata(new HashMap<>());
                }
            } catch (Exception e) {
                log.warn("获取插件元数据异常: pluginId={}", pluginId, e);
                detail.setMetadata(new HashMap<>());
            }
            
            // 配置信息（暂时返回空Map，后续任务中实现）
            detail.setConfiguration(new HashMap<>());
            
            log.debug("插件详细信息收集完成: pluginId={}", pluginId);
            return Result.success(detail);
        } catch (Exception e) {
            log.error("获取插件详细信息失败: pluginId={}", pluginId, e);
            return Result.error("获取插件详细信息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取监控概览
     * 
     * 概览信息包括：
     * - 总插件数
     * - 运行中插件数
     * - 已停止插件数
     * - 异常插件数
     * - 总内存使用
     * 
     * @return 监控概览信息
     */
    public Result<MonitorOverview> getMonitorOverview() {
        log.debug("获取监控概览");
        
        try {
            // 收集所有插件状态
            List<PluginMonitorStatus> allStatus = statusCollector.collectAllStatus();
            
            // 统计各种状态的插件数量
            long runningCount = allStatus.stream()
                    .filter(s -> "STARTED".equals(s.getState()))
                    .count();
            
            long stoppedCount = allStatus.stream()
                    .filter(s -> "STOPPED".equals(s.getState()) || "NOT_LOADED".equals(s.getState()))
                    .count();
            
            // 统计异常插件数量（包括有错误的和有内存告警的）
            long errorCount = allStatus.stream()
                    .filter(s -> (s.getHasError() != null && s.getHasError()) || 
                                 (s.getIsMemoryWarning() != null && s.getIsMemoryWarning()))
                    .count();
            
            // 计算总内存使用（捕获异常确保不影响概览显示）
            long totalMemory = 0;
            try {
                totalMemory = memoryCollector.getTotalMemoryUsage();
            } catch (Exception e) {
                log.warn("计算总内存使用失败，使用默认值 0", e);
            }
            
            // 构建概览对象
            MonitorOverview overview = MonitorOverview.builder()
                    .totalPlugins(allStatus.size())
                    .runningPlugins(runningCount)
                    .stoppedPlugins(stoppedCount)
                    .errorPlugins(errorCount)
                    .totalMemoryUsage(totalMemory)
                    .build();
            
            log.debug("监控概览: total={}, running={}, stopped={}, error={}, memory={}", 
                    overview.getTotalPlugins(), overview.getRunningPlugins(), 
                    overview.getStoppedPlugins(), overview.getErrorPlugins(),
                    memoryCollector.formatMemorySize(overview.getTotalMemoryUsage()));
            
            return Result.success(overview);
        } catch (Exception e) {
            log.error("获取监控概览失败", e);
            return Result.error("获取监控概览失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将插件元数据结果转换为Map
     * 
     * @param metadata 插件元数据结果
     * @return 元数据Map
     */
    private Map<String, Object> convertMetadataToMap(PluginPackageMetadataResult metadata) {
        Map<String, Object> map = new HashMap<>();
        
        if (metadata.getName() != null) {
            map.put("name", metadata.getName());
        }
        if (metadata.getVersion() != null) {
            map.put("version", metadata.getVersion());
        }
        if (metadata.getDescription() != null) {
            map.put("description", metadata.getDescription());
        }
        if (metadata.getProvider() != null) {
            map.put("provider", metadata.getProvider());
        }
        if (metadata.getLicense() != null) {
            map.put("license", metadata.getLicense());
        }
        if (metadata.getRequires() != null) {
            map.put("requires", metadata.getRequires());
        }
        
        return map;
    }
    
    /**
     * 获取插件的历史监控数据
     * 
     * @param pluginId 插件ID
     * @param timeRange 时间范围（如 "1h", "24h", "7d"）
     * @return 历史监控数据列表
     */
    public Result<List<com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorHistory>> getPluginHistory(String pluginId, String timeRange) {
        log.debug("获取插件历史数据: pluginId={}, timeRange={}", pluginId, timeRange);
        
        try {
            // 解析时间范围
            long startTime = parseTimeRange(timeRange);
            
            // 查询历史快照
            List<com.keqi.gress.plugin.appstore.domain.entity.PluginMonitorSnapshot> snapshots = 
                    historyDao.queryHistory(pluginId, startTime);
            
            if (snapshots == null || snapshots.isEmpty()) {
                log.debug("没有找到历史数据: pluginId={}, timeRange={}", pluginId, timeRange);
                return Result.success(java.util.Collections.emptyList());
            }
            
            // 转换为 DTO
            List<com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorHistory> historyList = 
                    new java.util.ArrayList<>();
            
            for (com.keqi.gress.plugin.appstore.domain.entity.PluginMonitorSnapshot snapshot : snapshots) {
                com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorHistory history = 
                        com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorHistory.builder()
                        .pluginId(snapshot.getPluginId())
                        .state(snapshot.getState())
                        .memoryUsage(snapshot.getMemoryUsage())
                        .formattedMemory(snapshot.getMemoryUsage() != null ? 
                                memoryCollector.formatMemorySize(snapshot.getMemoryUsage()) : "N/A")
                        .timestamp(snapshot.getTimestamp())
                        .metadata(snapshot.getMetadata())
                        .build();
                
                historyList.add(history);
            }
            
            log.debug("查询到 {} 条历史记录", historyList.size());
            return Result.success(historyList);
            
        } catch (Exception e) {
            log.error("获取插件历史数据失败: pluginId={}, timeRange={}", pluginId, timeRange, e);
            return Result.error("获取历史数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析时间范围字符串
     * 
     * 支持的格式：
     * - "1h" = 1小时
     * - "24h" = 24小时
     * - "7d" = 7天
     * - "30d" = 30天
     * 
     * @param timeRange 时间范围字符串
     * @return 起始时间戳（毫秒）
     */
    private long parseTimeRange(String timeRange) {
        if (timeRange == null || timeRange.trim().isEmpty()) {
            // 默认返回 1 小时前
            return System.currentTimeMillis() - (60 * 60 * 1000);
        }
        
        timeRange = timeRange.trim().toLowerCase();
        
        try {
            // 提取数字和单位
            int length = timeRange.length();
            if (length < 2) {
                throw new IllegalArgumentException("Invalid time range format: " + timeRange);
            }
            
            char unit = timeRange.charAt(length - 1);
            String numberStr = timeRange.substring(0, length - 1);
            int number = Integer.parseInt(numberStr);
            
            long duration;
            switch (unit) {
                case 'h': // 小时
                    duration = number * 60L * 60 * 1000;
                    break;
                case 'd': // 天
                    duration = number * 24L * 60 * 60 * 1000;
                    break;
                case 'm': // 分钟
                    duration = number * 60L * 1000;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported time unit: " + unit);
            }
            
            return System.currentTimeMillis() - duration;
            
        } catch (NumberFormatException e) {
            log.warn("解析时间范围失败，使用默认值 1h: timeRange={}", timeRange, e);
            return System.currentTimeMillis() - (60 * 60 * 1000);
        }
    }
}
