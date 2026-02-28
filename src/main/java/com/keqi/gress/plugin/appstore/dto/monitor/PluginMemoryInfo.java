package com.keqi.gress.plugin.appstore.dto.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 插件内存信息 DTO
 * 用于展示插件的内存使用情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMemoryInfo {
    
    /** 插件使用的内存（字节） */
    private Long usedMemory;
    
    /** 格式化的内存大小（如 "10.5 MB"） */
    private String formattedMemory;
    
    /** JVM 总内存（字节） */
    private Long totalJvmMemory;
    
    /** JVM 空闲内存（字节） */
    private Long freeJvmMemory;
    
    /** JVM 最大内存（字节） */
    private Long maxJvmMemory;
}
