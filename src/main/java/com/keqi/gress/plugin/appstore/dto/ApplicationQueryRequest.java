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
    /** 客户端类型（B/C） */
    private String clientType;
    /** 是否开启预加载（0/1） */
    private Integer preloadEnabled;
    /** 应用过滤标签（单个 tag，用于查询时模糊匹配 JSON 字符串） */
    private String tag;
}
