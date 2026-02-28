package com.keqi.gress.plugin.appstore.dao;

import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.SysApplicationUpgradeLog;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用升级日志 DAO
 */
@Slf4j
@Service
public class ApplicationUpgradeLogDao {

    @Inject(source = Inject.BeanSource.SPRING)
    private PluginLambdaDataSource dataSource;

    /**
     * 保存升级日志
     */
    public int insert(SysApplicationUpgradeLog logEntry) {
        try {
            return dataSource.insert(logEntry);
        } catch (Exception e) {
            log.error("保存应用升级日志失败", e);
            return 0;
        }
    }

    /**
     * 根据应用ID查询升级日志，按时间倒序
     */
    public java.util.List<SysApplicationUpgradeLog> findByApplicationId(Long applicationId) {
        return dataSource.lambdaQuery(SysApplicationUpgradeLog.class)
                .eq(SysApplicationUpgradeLog::getApplicationId, applicationId)
                .orderByDesc(SysApplicationUpgradeLog::getCreateTime)
                .list();
    }
}

