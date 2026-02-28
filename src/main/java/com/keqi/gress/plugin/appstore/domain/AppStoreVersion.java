package com.keqi.gress.plugin.appstore.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用商店插件版本实体
 */
@Data
public class AppStoreVersion {
    
    private Long id;
    private Long managerId;
    private String pluginId;
    private String version;
    private String changeLog;
    private String downloadUrl;
    private String fileHash;
    private String createdBy;
    private LocalDateTime createTime;
}

