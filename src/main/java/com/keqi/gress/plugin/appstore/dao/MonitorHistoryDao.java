package com.keqi.gress.plugin.appstore.dao;

import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.PluginMonitorSnapshot;
import com.keqi.gress.plugin.appstore.domain.entity.PluginStateChangeLog;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 监控历史数据访问层
 * 负责插件监控历史数据的持久化操作
 */
@Slf4j
@Service
public class MonitorHistoryDao {
    
    @Inject(source = Inject.BeanSource.SPRING)
    private PluginLambdaDataSource dataSource;
    
    /**
     * 保存监控快照
     * 
     * @param snapshot 监控快照对象
     * @return 影响行数
     */
    public int saveSnapshot(PluginMonitorSnapshot snapshot) {
        try {
            return dataSource.insert(snapshot);
        } catch (Exception e) {
            log.error("保存监控快照失败: pluginId={}", snapshot.getPluginId(), e);
            return 0;
        }
    }
    
    /**
     * 批量保存监控快照
     * 
     * @param snapshots 监控快照列表
     * @return 成功保存的数量
     */
    public int batchSaveSnapshots(List<PluginMonitorSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0;
        }
        
        int successCount = 0;
        for (PluginMonitorSnapshot snapshot : snapshots) {
            if (saveSnapshot(snapshot) > 0) {
                successCount++;
            }
        }
        return successCount;
    }
    
    /**
     * 查询插件的历史监控数据
     * 
     * @param pluginId 插件ID
     * @param startTime 起始时间戳（毫秒）
     * @return 历史监控数据列表
     */
    public List<PluginMonitorSnapshot> queryHistory(String pluginId, long startTime) {
        return dataSource.lambdaQuery(PluginMonitorSnapshot.class)
                .eq(PluginMonitorSnapshot::getPluginId, pluginId)
                .ge(PluginMonitorSnapshot::getTimestamp, startTime)
                .orderByAsc(PluginMonitorSnapshot::getTimestamp)
                .list();
    }
    
    /**
     * 查询插件在指定时间范围内的历史数据
     * 
     * @param pluginId 插件ID
     * @param startTime 起始时间戳（毫秒）
     * @param endTime 结束时间戳（毫秒）
     * @return 历史监控数据列表
     */
    public List<PluginMonitorSnapshot> queryHistoryByTimeRange(String pluginId, long startTime, long endTime) {
        return dataSource.lambdaQuery(PluginMonitorSnapshot.class)
                .eq(PluginMonitorSnapshot::getPluginId, pluginId)
                .ge(PluginMonitorSnapshot::getTimestamp, startTime)
                .le(PluginMonitorSnapshot::getTimestamp, endTime)
                .orderByAsc(PluginMonitorSnapshot::getTimestamp)
                .list();
    }
    
    /**
     * 删除过期的监控数据
     * 
     * @param expireTime 过期时间戳（毫秒），早于此时间的数据将被删除
     * @return 删除的记录数
     */
    public int deleteExpiredData(long expireTime) {
        try {
            return dataSource.lambdaUpdate(PluginMonitorSnapshot.class)
                    .lt(PluginMonitorSnapshot::getTimestamp, expireTime)
                    .delete();
        } catch (Exception e) {
            log.error("删除过期监控数据失败: expireTime={}", expireTime, e);
            return 0;
        }
    }
    
    /**
     * 保存状态变更日志
     * 
     * @param changeLog 状态变更日志对象
     * @return 影响行数
     */
    public int saveStateChangeLog(PluginStateChangeLog changeLog) {
        try {
            return dataSource.insert(changeLog);
        } catch (Exception e) {
            log.error("保存状态变更日志失败: pluginId={}", changeLog.getPluginId(), e);
            return 0;
        }
    }
    
    /**
     * 查询插件的状态变更历史
     * 
     * @param pluginId 插件ID
     * @param startTime 起始时间戳（毫秒）
     * @return 状态变更日志列表
     */
    public List<PluginStateChangeLog> queryStateChangeLogs(String pluginId, long startTime) {
        return dataSource.lambdaQuery(PluginStateChangeLog.class)
                .eq(PluginStateChangeLog::getPluginId, pluginId)
                .ge(PluginStateChangeLog::getChangeTime, startTime)
                .orderByDesc(PluginStateChangeLog::getChangeTime)
                .list();
    }
    
    /**
     * 删除过期的状态变更日志
     * 
     * @param expireTime 过期时间戳（毫秒）
     * @return 删除的记录数
     */
    public int deleteExpiredStateLogs(long expireTime) {
        try {
            return dataSource.lambdaUpdate(PluginStateChangeLog.class)
                    .lt(PluginStateChangeLog::getChangeTime, expireTime)
                    .delete();
        } catch (Exception e) {
            log.error("删除过期状态变更日志失败: expireTime={}", expireTime, e);
            return 0;
        }
    }
}
