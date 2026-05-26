package com.keqi.gress.plugin.appstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用商店分类（用于应用过滤标签）
 * 数据来源：gress-plugin-appstore-admin 的 Category 配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppStoreCategoryDTO {
    private String categoryKey;
    private String categoryName;
    private String description;
    private String icon;
    private Integer displayOrder;
    private Boolean enabled;
}

