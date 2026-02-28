package com.keqi.gress.plugin.appstore.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.PostConstruct;
import  com.keqi.gress.common.plugin.annotion.PreDestroy;
import  com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.config.AppStoreConfig;
import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 应用商店同步服务
 * 
 * 定期从远程 API 同步应用信息到本地数据库
 */
// @Slf4j
@Service(order = 20)
@Slf4j
public class AppStoreSyncService {

   // private final Log log = LogFactory.get(AppStoreSyncService.class);
    
    @Inject
    private AppStoreConfig config;
    
    @Inject(source = Inject.BeanSource.PLUGIN)
    private AppStoreApiService apiService;
    
    @Inject(source = Inject.BeanSource.PLUGIN)
    private ApplicationManagementService managementService;
    
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    
    @PostConstruct
    public void init() {
        log.info("初始化应用商店同步服务");
        
        AppStoreConfig.SyncConfig syncConfig = config.getSync();
        
        if (!syncConfig.getEnabled()) {
            log.info("应用同步未启用");
            return;
        }
        
        log.info("同步间隔: {} 秒", syncConfig.getInterval());
        log.info("同步 Cron: {}", syncConfig.getCron());
        
        // 启动定时同步任务
        startScheduledSync();
    }
    
    /**
     * 启动定时同步任务
     */
    private void startScheduledSync() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.running = true;
        
        Integer interval = config.getSync().getInterval();
        
        // 延迟 10 秒后开始第一次同步，然后按间隔定期同步
        scheduler.scheduleAtFixedRate(
            this::syncApplications,
            10,
            interval,
            TimeUnit.SECONDS
        );
        
        log.info("定时同步任务已启动");
    }
    
    /**
     * 同步应用信息
     */
    public void syncApplications() {
        if (!running) {
            log.debug("同步服务未运行");
            return;
        }
        
        log.info("开始同步应用信息");
        
        try {
            // 1. 从远程 API 获取应用列表
            List<ApplicationDTO> remoteApplications = fetchRemoteApplications();
            
            if (remoteApplications == null || remoteApplications.isEmpty()) {
                log.warn("未获取到远程应用信息");
                return;
            }
            
            log.info("获取到 {} 个远程应用", remoteApplications.size());
            
            // 2. 更新本地数据库
            int updated = updateLocalApplications(remoteApplications);
            
            log.info("应用信息同步完成，更新 {} 个应用", updated);
            
        } catch (Exception e) {
            log.error("同步应用信息失败", e);
            
            // 如果启用了重试，则进行重试
            if (config.getSync().getMaxRetries() > 0) {
                retrySync();
            }
        }
    }
    
    /**
     * 从远程 API 获取应用列表
     */
    private List<ApplicationDTO> fetchRemoteApplications() {
        try {
            // 分页获取所有应用
            int page = 1;
            int size = 100;
            
            List<ApplicationDTO> allApplications = new java.util.ArrayList<>();
            
            while (true) {
                List<ApplicationDTO> applications = apiService.getApplications(page, size, null);
                
                if (applications == null || applications.isEmpty()) {
                    break;
                }
                
                allApplications.addAll(applications);
                
                // 如果返回的数量小于 size，说明已经是最后一页
                if (applications.size() < size) {
                    break;
                }
                
                page++;
            }
            
            return allApplications;
            
        } catch (Exception e) {
            log.error("获取远程应用列表失败", e);
            return null;
        }
    }
    
    /**
     * 更新本地应用信息
     */
    private int updateLocalApplications(List<ApplicationDTO> remoteApplications) {
        int updated = 0;
        
        for (ApplicationDTO remoteApp : remoteApplications) {
            try {
                // 检查本地是否已存在该应用
                // 如果存在，更新信息；如果不存在，创建新记录
                // 这里简化处理，实际应该调用 managementService 的方法
                
                log.debug("更新应用: id={}, name={}, version={}", 
                    remoteApp.getId(), remoteApp.getApplicationName(), remoteApp.getRemoteVersion());
                
                updated++;
                
            } catch (Exception e) {
                log.error("更新应用失败: id={}", remoteApp.getId(), e);
            }
        }
        
        return updated;
    }
    
    /**
     * 重试同步
     */
    private void retrySync() {
        Integer maxRetries = config.getSync().getMaxRetries();
        
        for (int i = 1; i <= maxRetries; i++) {
            log.info("重试同步，第 {} 次", i);
            
            try {
                Thread.sleep(5000); // 等待 5 秒后重试
                syncApplications();
                return; // 成功后退出
                
            } catch (Exception e) {
                log.error("重试同步失败，第 {} 次", i, e);
            }
        }
        
        log.error("同步失败，已达到最大重试次数: {}", maxRetries);
    }
    
    /**
     * 手动触发同步
     */
    public void triggerSync() {
        log.info("手动触发应用同步");
        
        // 在新线程中执行同步，避免阻塞
        new Thread(() -> {
            try {
                syncApplications();
            } catch (Exception e) {
                log.error("手动同步失败", e);
            }
        }).start();
    }
    
    @PreDestroy
    public void destroy() {
        log.info("停止应用商店同步服务");
        
        this.running = false;
        
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("应用商店同步服务已停止");
    }
}
