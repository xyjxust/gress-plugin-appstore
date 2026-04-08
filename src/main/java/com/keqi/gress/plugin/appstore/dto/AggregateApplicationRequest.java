package com.keqi.gress.plugin.appstore.dto;

import lombok.Data;

import java.util.List;

/**
 * 聚合应用创建/更新请求
 */
@Data
public class AggregateApplicationRequest {
    private String applicationCode;
    private String applicationName;
    private String description;
    /** 列表展示用图标（URL 或静态资源路径） */
    private String icon;
    /** 列表排序权重，数值越小越靠前（仅聚合应用） */
    private Integer aggregateListOrder;
    private List<String> pluginIds;
    private Boolean autoLoad;
}
