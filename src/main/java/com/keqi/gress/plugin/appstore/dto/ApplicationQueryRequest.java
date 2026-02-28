package com.keqi.gress.plugin.appstore.dto;

import lombok.Data;

/**
 * 应用查询请求
 */
@Data
public class ApplicationQueryRequest {
    private Integer page = 1;
    private Integer size = 20;
    private String keyword;
    private Integer status;
    private String applicationType;
    private String pluginId;
}
