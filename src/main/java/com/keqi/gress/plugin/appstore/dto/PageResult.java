package com.keqi.gress.plugin.appstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private List<T> items;
    private Long total;
    private Integer page;
    private Integer size;
    private Integer totalPages;
    
    public static <T> PageResult<T> of(List<T> items, Long total, Integer page, Integer size) {
        int totalPages = (int) Math.ceil((double) total / size);
        return PageResult.<T>builder()
                .items(items)
                .total(total)
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .build();
    }
}
