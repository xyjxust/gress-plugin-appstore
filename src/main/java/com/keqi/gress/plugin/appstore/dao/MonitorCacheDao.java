package com.keqi.gress.plugin.appstore.dao;

import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.PluginMonitorCache;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 监控缓存数据访问层
 */
@Slf4j
@Service
public class MonitorCacheDao {
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginLambdaDataSource dataSource;
    
    /**
     * 根据缓存类型查询缓存
     * 
     * @param cacheType 缓存类型
     * @return 缓存记录
     */
    public PluginMonitorCache getCacheByType(String cacheType) {
        return dataSource.lambdaQuery(PluginMonitorCache.class)
                .eq(PluginMonitorCache::getCacheType, cacheType)
                .one();
    }
    
    /**
     * 根据插件ID查询缓存
     * 
     * @param pluginId 插件ID
     * @return 缓存记录
     */
    public PluginMonitorCache getCacheByPluginId(String pluginId) {
        return dataSource.lambdaQuery(PluginMonitorCache.class)
                .eq(PluginMonitorCache::getPluginId, pluginId)
                .eq(PluginMonitorCache::getCacheType, "single")
                .one();
    }
    
    /**
     * 插入或更新缓存
     * 
     * @param cache 缓存对象
     * @return 影响行数
     */
    public int saveOrUpdate(PluginMonitorCache cache) {
        PluginMonitorCache existing;
        
        if ("all".equals(cache.getCacheType())) {
            existing = getCacheByType("all");
        } else {
            existing = getCacheByPluginId(cache.getPluginId());
        }
        
        if (existing != null) {
            // 更新现有记录
            return dataSource.lambdaUpdate(PluginMonitorCache.class)
                    .set(PluginMonitorCache::getCacheData, cache.getCacheData())
                    .set(PluginMonitorCache::getCreateTime, cache.getCreateTime())
                    .set(PluginMonitorCache::getExpireTime, cache.getExpireTime())
                    .eq(PluginMonitorCache::getId, existing.getId())
                    .update();
        } else {
            // 插入新记录
            return dataSource.insert(cache);
        }
    }
    
    /**
     * 删除过期的缓存
     * 
     * @param currentTime 当前时间戳
     * @return 删除的记录数
     */
    public int deleteExpiredCache(long currentTime) {
        return dataSource.lambdaUpdate(PluginMonitorCache.class)
                .lt(PluginMonitorCache::getExpireTime, currentTime)
                .delete();
    }
    
    /**
     * 清空所有缓存
     * 
     * @return 删除的记录数
     */
    public int clearAllCache() {
        return dataSource.lambdaUpdate(PluginMonitorCache.class)
                .delete();
    }
}
