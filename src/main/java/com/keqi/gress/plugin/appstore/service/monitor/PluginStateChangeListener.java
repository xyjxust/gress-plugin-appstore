package com.keqi.gress.plugin.appstore.service.monitor;

import com.keqi.gress.common.plugin.annotion.Inject;
import com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.dao.MonitorHistoryDao;
import com.keqi.gress.plugin.appstore.domain.entity.PluginStateChangeLog;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件状态变更监听器
 * 负责监听和记录插件状态的变化
 */
@Slf4j
@Service
public class PluginStateChangeListener {
    
    @Inject
    private MonitorHistoryDao historyDao;
    
    /**
     * 缓存插件的当前状态，用于检测状态变化
     * Key: pluginId, Value: state
     */
    private final Map<String, String> pluginStateCache = new ConcurrentHashMap<>();
    
    /**
     * 记录插件状态变更
     * 
     * @param pluginId 插件ID
     * @param newState 新状态
     */
    public void recordStateChange(String pluginId, String newState) {
        recordStateChange(pluginId, newState, null, null);
    }
    
    /**
     * 记录插件状态变更（带操作人和原因）
     * 
     * @param pluginId 插件ID
     * @param newState 新状态
     * @param operator 操作人
     * @param reason 变更原因
     */
    public void recordStateChange(String pluginId, String newState, String operator, String reason) {
        try {
            // 获取旧状态
            String oldState = pluginStateCache.get(pluginId);
            
            // 如果状态没有变化，不记录
            if (oldState != null && oldState.equals(newState)) {
                return;
            }
            
            // 创建状态变更日志
            PluginStateChangeLog changeLog = PluginStateChangeLog.builder()
                    .pluginId(pluginId)
                    .oldState(oldState)
                    .newState(newState)
                    .changeTime(System.currentTimeMillis())
                    .operator(operator)
                    .reason(reason)
                    .build();
            
            // 保存到数据库
            int result = historyDao.saveStateChangeLog(changeLog);
            
            if (result > 0) {
                // 更新缓存
                pluginStateCache.put(pluginId, newState);
                log.debug("记录插件状态变更: pluginId={}, oldState={}, newState={}", 
                        pluginId, oldState, newState);
            } else {
                log.warn("保存插件状态变更日志失败: pluginId={}", pluginId);
            }
            
        } catch (Exception e) {
            log.error("记录插件状态变更失败: pluginId={}, newState={}", pluginId, newState, e);
        }
    }
    
    /**
     * 批量检查并记录状态变更
     * 
     * @param currentStates 当前所有插件的状态 (pluginId -> state)
     */
    public void checkAndRecordStateChanges(Map<String, String> currentStates) {
        if (currentStates == null || currentStates.isEmpty()) {
            return;
        }
        
        for (Map.Entry<String, String> entry : currentStates.entrySet()) {
            String pluginId = entry.getKey();
            String currentState = entry.getValue();
            
            recordStateChange(pluginId, currentState);
        }
    }
    
    /**
     * 初始化插件状态缓存
     * 
     * @param pluginId 插件ID
     * @param state 初始状态
     */
    public void initializeState(String pluginId, String state) {
        pluginStateCache.put(pluginId, state);
    }
    
    /**
     * 清除插件状态缓存
     * 
     * @param pluginId 插件ID
     */
    public void clearState(String pluginId) {
        pluginStateCache.remove(pluginId);
    }
    
    /**
     * 获取插件的缓存状态
     * 
     * @param pluginId 插件ID
     * @return 缓存的状态，如果不存在返回 null
     */
    public String getCachedState(String pluginId) {
        return pluginStateCache.get(pluginId);
    }
}
