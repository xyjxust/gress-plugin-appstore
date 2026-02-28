package com.keqi.gress.plugin.appstore.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用商店插件管理实体
 */
@Data
public class AppStoreManager {
    
    private Long id;
    private String pluginId;
    private String pluginName;
    private String pluginType;
    private String icon;
    private String summary;
    private String description;
    private String currentVersion;
    private String latestVersion;
    private String status;
    private Integer feedbackCount;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

