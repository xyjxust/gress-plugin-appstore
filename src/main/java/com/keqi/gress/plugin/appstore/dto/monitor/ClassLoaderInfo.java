package com.keqi.gress.plugin.appstore.dto.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 类加载器信息 DTO
 * 用于展示插件的类加载器详细信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassLoaderInfo {
    
    /** 类加载器类名 */
    private String className;
    
    /** 父类加载器类名 */
    private String parentClassName;
}
