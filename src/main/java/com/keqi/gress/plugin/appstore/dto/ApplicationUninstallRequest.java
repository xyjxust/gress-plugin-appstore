package com.keqi.gress.plugin.appstore.dto;

import lombok.Data;

/**
 * 应用卸载请求
 */
@Data
public class ApplicationUninstallRequest {
    private String operatorId;
    private String operatorName;
    private String reason;
}
