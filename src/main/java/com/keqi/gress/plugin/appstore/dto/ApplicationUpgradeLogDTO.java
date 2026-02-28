package com.keqi.gress.plugin.appstore.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用升级日志 DTO
 */
@Data
public class ApplicationUpgradeLogDTO {

    private Long id;

    private Long applicationId;

    private String pluginId;

    private String oldVersion;

    private String newVersion;

    private String targetVersion;

    private String pluginType;

    private String operatorName;

    private String status;

    private String message;

    private LocalDateTime createTime;
}


