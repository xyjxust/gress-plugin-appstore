package com.keqi.gress.plugin.appstore.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RemoteApplicationInstallRequest {
    private String pluginId;
    private String operatorId;
    private String operatorName;
    private Map<String, Object> installConfig;
}
