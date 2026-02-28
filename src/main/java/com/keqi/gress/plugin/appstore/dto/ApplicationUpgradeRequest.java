package com.keqi.gress.plugin.appstore.dto;

import lombok.Data;

/**
 * 应用升级/降级请求
 */
@Data
public class ApplicationUpgradeRequest {
    /**
     * 目标版本号
     */
    private String targetVersion;
    
    /**
     * 操作人ID
     */
    private String operatorId;
    
    /**
     * 操作人名称
     */
    private String operatorName;
    
    /**
     * 操作类型：UPGRADE-升级，ROLLBACK-降级
     * 默认为 UPGRADE
     */
    private OperationType operationType = OperationType.UPGRADE;
    
    /**
     * 操作类型枚举
     */
    public enum OperationType {
        /**
         * 升级
         */
        UPGRADE,
        
        /**
         * 降级/回滚
         */
        ROLLBACK
    }
}
