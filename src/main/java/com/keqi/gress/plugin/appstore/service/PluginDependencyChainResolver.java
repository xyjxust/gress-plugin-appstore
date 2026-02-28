package com.keqi.gress.plugin.appstore.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 插件依赖链解析器
 * 
 * 负责递归解析插件的所有依赖（包括直接依赖和间接依赖），
 * 构建依赖图，并进行拓扑排序以确定正确的安装顺序。
 * 
 * @author Gress Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class PluginDependencyChainResolver {
    
    //private static final Log log = LogFactory.get(PluginDependencyChainResolver.class);
    
    @Inject
    private AppStoreApiService appStoreApiService;
    
    /**
     * 解析插件依赖链
     * 
     * @param pluginId 插件ID
     * @param version 版本号（可选，如果为null则使用最新版本）
     * @return 依赖链信息
     */
    public DependencyChain resolveDependencyChain(String pluginId, String version) {
        log.info("开始解析插件依赖链: pluginId={}, version={}", pluginId, version);
        
        DependencyChain chain = new DependencyChain();
        chain.setRootPluginId(pluginId);
        chain.setRootVersion(version);
        
        // 用于跟踪已处理的插件，避免重复解析
        Set<String> processedPlugins = new HashSet<>();
        // 用于存储所有依赖节点
        Map<String, DependencyNode> dependencyMap = new HashMap<>();
        
        try {
            // 递归解析依赖
            DependencyNode rootNode = resolveDependencyRecursive(
                pluginId, version, processedPlugins, dependencyMap);
            
            if (rootNode == null) {
                log.error("无法解析根插件: pluginId={}, version={}", pluginId, version);
                throw new RuntimeException("无法解析根插件: " + pluginId);
            }
            
            chain.setRootNode(rootNode);
            chain.setAllDependencies(dependencyMap);
            
            // 计算安装顺序（拓扑排序）
            List<String> installOrder = calculateInstallOrder(dependencyMap);
            chain.setInstallOrder(installOrder);
            
            log.info("依赖链解析完成: pluginId={}, 总依赖数={}, 安装顺序={}", 
                pluginId, dependencyMap.size(), installOrder);
            
            return chain;
            
        } catch (Exception e) {
            log.error("解析依赖链失败: pluginId={}, version={}", pluginId, version, e);
            throw new RuntimeException("解析依赖链失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 递归解析依赖
     * 
     * @param pluginId 插件ID
     * @param version 版本号
     * @param processedPlugins 已处理的插件集合（用于避免循环依赖）
     * @param dependencyMap 依赖映射表
     * @return 依赖节点
     */
    private DependencyNode resolveDependencyRecursive(
            String pluginId, 
            String version,
            Set<String> processedPlugins,
            Map<String, DependencyNode> dependencyMap) {
        
        // 生成唯一标识
        String key = pluginId + "@" + (version != null ? version : "latest");
        
        // 检查是否已处理过
        if (processedPlugins.contains(key)) {
            log.debug("插件已处理，跳过: {}", key);
            return dependencyMap.get(key);
        }
        
        // 标记为正在处理（用于检测循环依赖）
        processedPlugins.add(key);
        
        log.debug("解析插件依赖: pluginId={}, version={}", pluginId, version);
        
        // 创建依赖节点
        DependencyNode node = new DependencyNode();
        node.setPluginId(pluginId);
        node.setVersion(version);
        node.setOptional(false); // 默认为必需依赖
        
        // 从应用商店获取插件元数据（包括依赖信息）
        List<DependencyInfo> dependencies = getPluginDependencies(pluginId, version);
        node.setDependencies(dependencies);
        
        // 添加到依赖映射表
        dependencyMap.put(key, node);
        
        // 递归解析所有依赖
        if (dependencies != null && !dependencies.isEmpty()) {
            for (DependencyInfo dep : dependencies) {
                try {
                    DependencyNode depNode = resolveDependencyRecursive(
                        dep.getPluginId(),
                        dep.getVersion(),
                        processedPlugins,
                        dependencyMap
                    );
                    
                    if (depNode != null) {
                        node.getDirectDependencies().add(depNode);
                    }
                } catch (Exception e) {
                    log.warn("解析依赖失败: pluginId={}, version={}, error={}", 
                        dep.getPluginId(), dep.getVersion(), e.getMessage());
                    
                    // 如果是必需依赖，抛出异常
                    if (!dep.isOptional()) {
                        throw new RuntimeException(
                            "必需依赖解析失败: " + dep.getPluginId() + "@" + dep.getVersion(), e);
                    }
                    // 可选依赖失败时，记录警告但继续处理
                }
            }
        }
        
        return node;
    }
    
    /**
     * 从应用商店获取插件依赖信息
     * 
     * @param pluginId 插件ID
     * @param version 版本号
     * @return 依赖列表
     */
    private List<DependencyInfo> getPluginDependencies(String pluginId, String version) {
        try {
            // 从应用商店API获取插件详情
            com.keqi.gress.plugin.appstore.dto.ApplicationDTO appDetail;
            if (version != null && !version.isEmpty()) {
                appDetail = appStoreApiService.getApplicationVersionDetail(pluginId, version);
            } else {
                appDetail = appStoreApiService.getApplicationDetail(pluginId);
            }
            
            if (appDetail == null) {
                log.warn("无法获取插件详情: pluginId={}, version={}", pluginId, version);
                return Collections.emptyList();
            }
            
            // 从 ApplicationDTO 中提取依赖信息
            // 注意：这里假设 ApplicationDTO 有 dependencies 字段，如果没有需要扩展
            // 或者从 plugin.yml 中解析依赖信息
            List<DependencyInfo> dependencies = parseDependencies(appDetail);
            
            log.debug("获取到插件依赖: pluginId={}, version={}, count={}", 
                pluginId, version, dependencies.size());
            
            return dependencies;
            
        } catch (Exception e) {
            log.error("获取插件依赖信息失败: pluginId={}, version={}", pluginId, version, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 解析依赖信息
     * 
     * 从 ApplicationDTO 中解析依赖信息
     * 
     * @param appDetail 应用详情
     * @return 依赖列表
     */
    private List<DependencyInfo> parseDependencies(ApplicationDTO appDetail) {
        if (appDetail == null || appDetail.getDependencies() == null) {
            return Collections.emptyList();
        }
        
        java.util.List<DependencyInfo> dependencies = new ArrayList<>();
        java.util.List<ApplicationDTO.DependencyInfo> appDependencies = appDetail.getDependencies();
        for (ApplicationDTO.DependencyInfo dep : appDependencies) {
            DependencyInfo info = new DependencyInfo();
            info.setPluginId(dep.getPluginId());
            info.setVersion(dep.getVersion());
            info.setOptional(dep.getOptional() != null ? dep.getOptional() : false);
            info.setVersionRange(dep.getVersionRange());
            dependencies.add(info);
        }
        
        return dependencies;
    }
    
    /**
     * 计算安装顺序（拓扑排序）
     * 
     * @param dependencyMap 依赖映射表
     * @return 安装顺序列表（从依赖到被依赖）
     */
    private List<String> calculateInstallOrder(Map<String, DependencyNode> dependencyMap) {
        // 构建依赖图
        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        
        // 初始化
        for (String key : dependencyMap.keySet()) {
            dependencyGraph.put(key, new HashSet<>());
            inDegree.put(key, 0);
        }
        
        // 构建图
        for (Map.Entry<String, DependencyNode> entry : dependencyMap.entrySet()) {
            String key = entry.getKey();
            DependencyNode node = entry.getValue();
            
            for (DependencyNode dep : node.getDirectDependencies()) {
                String depKey = dep.getPluginId() + "@" + (dep.getVersion() != null ? dep.getVersion() : "latest");
                if (dependencyGraph.containsKey(depKey)) {
                    dependencyGraph.get(key).add(depKey);
                    inDegree.put(depKey, inDegree.get(depKey) + 1);
                }
            }
        }
        
        // 拓扑排序
        List<String> installOrder = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        
        // 找到所有入度为0的节点（没有依赖的节点）
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        // 执行拓扑排序
        while (!queue.isEmpty()) {
            String current = queue.poll();
            installOrder.add(current);
            
            // 减少依赖当前节点的其他节点的入度
            for (String dependent : dependencyGraph.get(current)) {
                int newInDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newInDegree);
                if (newInDegree == 0) {
                    queue.offer(dependent);
                }
            }
        }
        
        // 检查是否有循环依赖
        if (installOrder.size() != dependencyMap.size()) {
            log.warn("检测到可能的循环依赖: 已处理={}, 总数={}", 
                installOrder.size(), dependencyMap.size());
        }
        
        return installOrder;
    }
    
    /**
     * 检测循环依赖
     * 
     * @param chain 依赖链
     * @return 是否存在循环依赖
     */
    public boolean hasCircularDependency(DependencyChain chain) {
        Map<String, DependencyNode> dependencyMap = chain.getAllDependencies();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String key : dependencyMap.keySet()) {
            if (hasCircularDependencyDFS(key, dependencyMap, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 深度优先搜索检测循环依赖
     */
    private boolean hasCircularDependencyDFS(
            String key,
            Map<String, DependencyNode> dependencyMap,
            Set<String> visited,
            Set<String> recursionStack) {
        
        if (recursionStack.contains(key)) {
            return true; // 发现循环
        }
        
        if (visited.contains(key)) {
            return false; // 已访问过，无需再次检查
        }
        
        visited.add(key);
        recursionStack.add(key);
        
        DependencyNode node = dependencyMap.get(key);
        if (node != null) {
            for (DependencyNode dep : node.getDirectDependencies()) {
                String depKey = dep.getPluginId() + "@" + (dep.getVersion() != null ? dep.getVersion() : "latest");
                if (hasCircularDependencyDFS(depKey, dependencyMap, visited, recursionStack)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(key);
        return false;
    }
    
    /**
     * 依赖链信息
     */
    @Data
    public static class DependencyChain {
        /** 根插件ID */
        private String rootPluginId;
        
        /** 根插件版本 */
        private String rootVersion;
        
        /** 根节点 */
        private DependencyNode rootNode;
        
        /** 所有依赖节点（key: pluginId@version） */
        private Map<String, DependencyNode> allDependencies = new HashMap<>();
        
        /** 安装顺序（拓扑排序结果） */
        private List<String> installOrder = new ArrayList<>();
    }
    
    /**
     * 依赖节点
     */
    @Data
    public static class DependencyNode {
        /** 插件ID */
        private String pluginId;
        
        /** 版本号 */
        private String version;
        
        /** 是否为可选依赖 */
        private boolean optional;
        
        /** 直接依赖列表 */
        private List<DependencyInfo> dependencies = new ArrayList<>();
        
        /** 直接依赖节点（已解析） */
        private List<DependencyNode> directDependencies = new ArrayList<>();
        
        /** 下载状态 */
        private DependencyDownloadStatus downloadStatus = DependencyDownloadStatus.PENDING;
        
        /** 下载错误信息 */
        private String downloadError;
    }
    
    /**
     * 依赖信息
     */
    @Data
    public static class DependencyInfo {
        /** 插件ID */
        private String pluginId;
        
        /** 版本号 */
        private String version;
        
        /** 是否为可选依赖 */
        private boolean optional;
        
        /** 版本范围（如 ">=1.0.0"） */
        private String versionRange;
    }
    
    /**
     * 下载状态
     */
    public enum DependencyDownloadStatus {
        PENDING,    // 待下载
        DOWNLOADING, // 下载中
        SUCCESS,    // 下载成功
        FAILED      // 下载失败
    }
}










