package com.keqi.gress.plugin.appstore.service.monitor;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.dao.MonitorCacheDao;
import com.keqi.gress.plugin.appstore.domain.entity.PluginMonitorCache;
import com.keqi.gress.plugin.appstore.dto.monitor.PluginMonitorStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 监控数据缓存服务
 * 使用数据库存储缓存数据，减少重复计算
 */
@Slf4j
@Service
public class MonitorDataCache {
    
    @Inject
    private MonitorCacheDao cacheDao;
    
    /** 缓存过期时间（毫秒）- 5秒 */
    private static final long CACHE_TTL = 5000;
    
    /** 所有插件状态的缓存类型 */
    private static final String CACHE_TYPE_ALL = "all";
    
    /** 单个插件状态的缓存类型 */
    private static final String CACHE_TYPE_SINGLE = "single";
    
    /**
     * 获取所有插件状态（从缓存）
     * 
     * @return 所有插件状态列表，如果缓存不存在或已过期则返回 null
     */
    public List<PluginMonitorStatus> getAllStatus() {
        log.debug("从缓存获取所有插件状态");
        
        try {
            PluginMonitorCache cache = cacheDao.getCacheByType(CACHE_TYPE_ALL);
            
            if (cache == null) {
                log.debug("缓存不存在");
                return null;
            }
            
            // 检查缓存是否过期
            long currentTime = System.currentTimeMillis();
            if (cache.getExpireTime() < currentTime) {
                log.debug("缓存已过期: expireTime={}, currentTime={}", 
                        cache.getExpireTime(), currentTime);
                return null;
            }
            
            // 反序列化缓存数据
            List<PluginMonitorStatus> statusList = JSON.parseObject(
                    cache.getCacheData(), 
                    new TypeReference<List<PluginMonitorStatus>>() {}
            );
            
            log.debug("从缓存获取到 {} 个插件状态", statusList != null ? statusList.size() : 0);
            return statusList;
        } catch (Exception e) {
            log.error("从缓存获取所有插件状态失败", e);
            return null;
        }
    }
    
    /**
     * 更新所有插件状态缓存
     * 
     * @param statusList 插件状态列表
     */
    public void updateAllStatus(List<PluginMonitorStatus> statusList) {
        log.debug("更新所有插件状态缓存: {} 个插件", statusList != null ? statusList.size() : 0);
        
        try {
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime + CACHE_TTL;
            
            // 序列化为 JSON
            String cacheData = JSON.toJSONString(statusList);
            
            // 构建缓存对象
            PluginMonitorCache cache = PluginMonitorCache.builder()
                    .cacheType(CACHE_TYPE_ALL)
                    .cacheData(cacheData)
                    .createTime(currentTime)
                    .expireTime(expireTime)
                    .build();
            
            // 保存或更新缓存
            int rows = cacheDao.saveOrUpdate(cache);
            log.debug("缓存更新完成: rows={}", rows);
            
            // 异步清理过期缓存
            cleanExpiredCacheAsync();
        } catch (Exception e) {
            log.error("更新所有插件状态缓存失败", e);
        }
    }
    
    /**
     * 获取单个插件状态（从缓存）
     * 
     * @param pluginId 插件ID
     * @return 插件状态，如果缓存不存在或已过期则返回 null
     */
    public PluginMonitorStatus getStatus(String pluginId) {
        log.debug("从缓存获取插件状态: pluginId={}", pluginId);
        
        try {
            PluginMonitorCache cache = cacheDao.getCacheByPluginId(pluginId);
            
            if (cache == null) {
                log.debug("缓存不存在: pluginId={}", pluginId);
                return null;
            }
            
            // 检查缓存是否过期
            long currentTime = System.currentTimeMillis();
            if (cache.getExpireTime() < currentTime) {
                log.debug("缓存已过期: pluginId={}, expireTime={}, currentTime={}", 
                        pluginId, cache.getExpireTime(), currentTime);
                return null;
            }
            
            // 反序列化缓存数据
            PluginMonitorStatus status = JSON.parseObject(
                    cache.getCacheData(), 
                    PluginMonitorStatus.class
            );
            
            log.debug("从缓存获取到插件状态: pluginId={}", pluginId);
            return status;
        } catch (Exception e) {
            log.error("从缓存获取插件状态失败: pluginId={}", pluginId, e);
            return null;
        }
    }
    
    /**
     * 更新单个插件状态缓存
     * 
     * @param pluginId 插件ID
     * @param status 插件状态
     */
    public void updateStatus(String pluginId, PluginMonitorStatus status) {
        log.debug("更新插件状态缓存: pluginId={}", pluginId);
        
        try {
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime + CACHE_TTL;
            
            // 序列化为 JSON
            String cacheData = JSON.toJSONString(status);
            
            // 构建缓存对象
            PluginMonitorCache cache = PluginMonitorCache.builder()
                    .pluginId(pluginId)
                    .cacheType(CACHE_TYPE_SINGLE)
                    .cacheData(cacheData)
                    .createTime(currentTime)
                    .expireTime(expireTime)
                    .build();
            
            // 保存或更新缓存
            int rows = cacheDao.saveOrUpdate(cache);
            log.debug("缓存更新完成: pluginId={}, rows={}", pluginId, rows);
        } catch (Exception e) {
            log.error("更新插件状态缓存失败: pluginId={}", pluginId, e);
        }
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCache() {
        log.debug("清除所有缓存");
        
        try {
            int rows = cacheDao.clearAllCache();
            log.debug("缓存清除完成: rows={}", rows);
        } catch (Exception e) {
            log.error("清除缓存失败", e);
        }
    }
    
    /**
     * 异步清理过期缓存
     */
    private void cleanExpiredCacheAsync() {
        // 使用新线程异步清理，避免阻塞主流程
        new Thread(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                int rows = cacheDao.deleteExpiredCache(currentTime);
                if (rows > 0) {
                    log.debug("清理过期缓存完成: rows={}", rows);
                }
            } catch (Exception e) {
                log.warn("清理过期缓存失败", e);
            }
        }, "monitor-cache-cleaner").start();
    }
}
