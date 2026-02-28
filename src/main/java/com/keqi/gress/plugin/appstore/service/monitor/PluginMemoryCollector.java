package com.keqi.gress.plugin.appstore.service.monitor;

import com.keqi.gress.common.plugin.PluginPackageLifecycle;
import com.keqi.gress.common.plugin.PluginPackageRuntimeInfo;
import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.common.model.Result;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMemoryInfo;
import com.keqi.gress.plugin.appstore.util.MonitorErrorHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 插件内存收集器
 * 负责收集插件的内存使用信息
 * 
 * 使用 PluginPackageLifecycle 接口获取插件包的运行时信息，
 * 包括类加载器信息，用于估算内存使用。
 */
@Slf4j
@Service
public class PluginMemoryCollector {
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginPackageLifecycle pluginLifecycle;
    
    private final Runtime runtime = Runtime.getRuntime();
    
    /** 内存告警阈值（字节），默认 500MB */
    private static final long MEMORY_WARNING_THRESHOLD = 500 * 1024 * 1024L;
    
    /**
     * 收集插件内存信息
     * 
     * @param pluginId 插件ID
     * @return 插件内存信息，如果插件未加载则返回 null
     */
    public PluginMemoryInfo collectMemoryInfo(String pluginId) {
        log.debug("收集插件内存信息: pluginId={}", pluginId);
        
        try {
            // 获取插件包运行时信息
            Result<PluginPackageRuntimeInfo> result = pluginLifecycle.getPluginPackageRuntimeInfo(pluginId);
            if (!result.isSuccess() || result.getData() == null) {
                log.warn("插件未加载，无法收集内存信息: pluginId={}", pluginId);
                return MonitorErrorHandler.handleMemoryUnavailable(pluginId);
            }
            
            PluginPackageRuntimeInfo runtimeInfo = result.getData();
            
            // 估算插件内存使用
            long estimatedMemory = estimatePluginMemory(runtimeInfo);
            
            // 构建内存信息对象
            PluginMemoryInfo memInfo = PluginMemoryInfo.builder()
                    .usedMemory(estimatedMemory)
                    .formattedMemory(formatMemorySize(estimatedMemory))
                    .totalJvmMemory(runtime.totalMemory())
                    .freeJvmMemory(runtime.freeMemory())
                    .maxJvmMemory(runtime.maxMemory())
                    .build();
            
            log.debug("插件内存信息收集完成: pluginId={}, usedMemory={}", 
                    pluginId, memInfo.getFormattedMemory());
            
            return memInfo;
        } catch (Exception e) {
            log.error("收集插件内存信息失败: pluginId={}", pluginId, e);
            return MonitorErrorHandler.handleMemoryUnavailable(pluginId);
        }
    }
    
    /**
     * 获取所有插件的总内存使用
     * 
     * @return 总内存使用量（字节）
     */
    public long getTotalMemoryUsage() {
        log.debug("计算所有插件的总内存使用");
        
        try {
            // 获取所有插件包的运行时信息
            Result<List<PluginPackageRuntimeInfo>> result = pluginLifecycle.getAllPluginPackages();
            if (!result.isSuccess() || result.getData() == null) {
                log.warn("无法获取插件包列表");
                return 0;
            }
            
            long totalMemory = result.getData().stream()
                    .mapToLong(this::estimatePluginMemory)
                    .sum();
            
            log.debug("所有插件总内存使用: {}", formatMemorySize(totalMemory));
            return totalMemory;
        } catch (Exception e) {
            log.error("计算总内存使用失败", e);
            return 0;
        }
    }
    
    /**
     * 估算插件内存使用
     * 
     * 注意：这是一个估算值，实际内存使用可能不同
     * 计算方法：统计插件类加载器加载的类数量 * 平均类大小
     * 
     * 由于 Java 不提供直接获取类加载器内存使用的 API，
     * 这里使用简化的估算方法：
     * 1. 从运行时信息中获取已加载的类数量
     * 2. 使用经验值估算每个类的平均内存占用
     * 3. 返回估算的总内存使用
     * 
     * @param runtimeInfo 插件包运行时信息
     * @return 估算的内存使用量（字节）
     */
    private long estimatePluginMemory(PluginPackageRuntimeInfo runtimeInfo) {
        try {
            PluginPackageRuntimeInfo.ClassLoaderInfo classLoaderInfo = runtimeInfo.getClassLoader();
            if (classLoaderInfo == null) {
                log.debug("插件 {} 没有类加载器信息，使用默认估算值", runtimeInfo.getPackageId());
                return 10 * 1024 * 1024; // 默认 10MB
            }
            
            Integer loadedClassCount = classLoaderInfo.getLoadedClassCount();
            
            if (loadedClassCount != null && loadedClassCount > 0) {
                // 假设每个类平均占用 50KB（包括类元数据、常量池等）
                long estimatedMemory = loadedClassCount * 50L * 1024;
                log.debug("插件 {} 估算内存: {} 个类, 约 {}", 
                        runtimeInfo.getPackageId(), loadedClassCount, formatMemorySize(estimatedMemory));
                return estimatedMemory;
            } else {
                // 如果无法获取类数量，返回默认估算值 10MB
                log.debug("无法获取插件 {} 的类数量，使用默认估算值 10MB", runtimeInfo.getPackageId());
                return 10 * 1024 * 1024;
            }
        } catch (Exception e) {
            log.warn("估算插件内存失败: pluginId={}, 使用默认值", runtimeInfo.getPackageId(), e);
            // 出错时返回默认估算值 10MB
            return 10 * 1024 * 1024;
        }
    }
    
    /**
     * 格式化内存大小
     * 
     * 将字节数转换为人类可读的格式（B/KB/MB/GB）
     * 
     * @param bytes 字节数
     * @return 格式化的内存大小字符串
     */
    public String formatMemorySize(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * 检查内存使用是否超过告警阈值
     * 
     * @param memoryBytes 内存使用量（字节）
     * @return 如果超过阈值返回 true，否则返回 false
     */
    public boolean isMemoryWarning(long memoryBytes) {
        return memoryBytes > MEMORY_WARNING_THRESHOLD;
    }
    
    /**
     * 获取内存告警阈值
     * 
     * @return 内存告警阈值（字节）
     */
    public long getMemoryWarningThreshold() {
        return MEMORY_WARNING_THRESHOLD;
    }
}
