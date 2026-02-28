package com.keqi.gress.plugin.appstore.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用操作日志 DTO
 */
@Data
public class ApplicationOperationLogDTO {
    
    private Long id;
    
    /** 关联的应用ID */
    private Long applicationId;
    
    /** 应用代码 */
    private String applicationCode;
    
    /** 应用名称 */
    private String applicationName;
    
    /** 插件ID */
    private String pluginId;
    
    /** 操作类型 */
    private String operationType;
    
    /** 操作类型文本 */
    private String operationTypeText;
    
    /** 操作描述 */
    private String operationDesc;
    
    /** 操作人ID */
    private String operatorId;
    
    /** 操作人名称 */
    private String operatorName;
    
    /** 操作结果 */
    private String status;
    
    /** 操作结果文本 */
    private String statusText;
    
    /** 失败原因或补充信息 */
    private String message;
    
    /** 操作前数据 */
    private String beforeData;
    
    /** 操作后数据 */
    private String afterData;
    
    /** 操作耗时（毫秒） */
    private Long duration;
    
    /** 客户端IP */
    private String clientIp;
    
    /** 创建时间 */
    private LocalDateTime createTime;
}
