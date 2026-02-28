package com.keqi.gress.plugin.appstore.service.monitor;

import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Scheduled;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.dao.MonitorHistoryDao;
import lombok.extern.slf4j.Slf4j;

/**
 * 监控数据清理定时任务
 * 负责定期清理过期的监控历史数据
 */
@Slf4j
@Service
public class MonitorDataCleanupScheduler {
    
    @Inject
    private MonitorHistoryDao historyDao;
    
    /**
     * 数据保留天数（默认 7 天）
     */
    private static final int RETENTION_DAYS = 7;
    
    /**
     * 定时清理过期数据
     * 每天凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredData() {
        try {
            log.info("开始清理过期监控数据");
            
            // 计算过期时间戳（7 天前）
            long expireTime = System.currentTimeMillis() - (RETENTION_DAYS * 24L * 60 * 60 * 1000);
            
            // 清理过期的监控快照
            int deletedSnapshots = historyDao.deleteExpiredData(expireTime);
            log.info("清理过期监控快照: 删除数量={}", deletedSnapshots);
            
            // 清理过期的状态变更日志
            int deletedLogs = historyDao.deleteExpiredStateLogs(expireTime);
            log.info("清理过期状态变更日志: 删除数量={}", deletedLogs);
            
            log.info("清理过期监控数据完成: 快照={}, 日志={}", deletedSnapshots, deletedLogs);
            
        } catch (Exception e) {
            log.error("清理过期监控数据失败", e);
        }
    }
    
    /**
     * 手动触发清理（用于测试或手动维护）
     * 
     * @param retentionDays 保留天数
     * @return 清理的总记录数
     */
    public int manualCleanup(int retentionDays) {
        try {
            log.info("手动清理过期监控数据: 保留天数={}", retentionDays);
            
            long expireTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
            
            int deletedSnapshots = historyDao.deleteExpiredData(expireTime);
            int deletedLogs = historyDao.deleteExpiredStateLogs(expireTime);
            
            int total = deletedSnapshots + deletedLogs;
            log.info("手动清理完成: 快照={}, 日志={}, 总计={}", deletedSnapshots, deletedLogs, total);
            
            return total;
            
        } catch (Exception e) {
            log.error("手动清理过期监控数据失败", e);
            return 0;
        }
    }
}
