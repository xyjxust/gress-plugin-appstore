package com.keqi.gress.plugin.appstore.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.PostConstruct;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.common.storage.FileStorageService;
import com.keqi.gress.plugin.appstore.config.AppStoreConfig;
import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import com.keqi.gress.plugin.appstore.dto.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用商店 API 服务
 * 
 * 负责与远程应用商店 API 交互，获取应用信息、下载应用等
 */
@Slf4j
@Service(order = 10)
public class AppStoreApiService {
   // private Log log   = LogFactory.get(AppStoreApiService.class);
    
    @Inject
    private AppStoreConfig config;
    
    @Inject(source = Inject.BeanSource.SPRING)
    private FileStorageService fileStorageService;
    
    private RestTemplate restTemplate;
    
    @PostConstruct
    public void init() {
        log.info("初始化应用商店 API 服务");
        
        // 检查配置是否注入
        if (config == null) {
            log.error("AppStoreConfig 未注入，请检查 @ConfigurationProperties 配置");
            return;
        }
        
        log.info("配置加载成功:");
        log.info("  - 商店名称: {}", config.getStoreName());
        log.info("  - 商店 URL: {}", config.getStoreUrl());
        
        // 检查 API 配置
        if (config.getApi() == null) {
            log.error("API 配置未加载，请检查 plugin.yml 中的 appstore.api 配置");
            return;
        }
        
        log.info("  - API 基础地址: {}", config.getApi().getBaseUrl());
        log.info("  - API 启用状态: {}", config.getApi().getEnabled());
        log.info("  - API 超时时间: {}ms", config.getApi().getTimeout());
        log.info("  - API 重试次数: {}", config.getApi().getMaxRetries());
        
        // 创建 RestTemplate
        this.restTemplate = new RestTemplate();
        
        // 验证配置
        validateConfig();
    }
    
    /**
     * 验证配置
     */
    private void validateConfig() {
        if (config == null || config.getApi() == null) {
            log.error("配置验证失败：配置对象为空");
            return;
        }
        
        AppStoreConfig.ApiConfig apiConfig = config.getApi();
        
        if (Boolean.TRUE.equals(apiConfig.getEnabled())) {
            if (apiConfig.getBaseUrl() == null || apiConfig.getBaseUrl().isEmpty()) {
                log.error("API 基础地址未配置");
                throw new IllegalStateException("API 基础地址未配置");
            }
            
            if (apiConfig.getSecretKey() == null || apiConfig.getSecretKey().isEmpty()) {
                log.warn("API 已启用但未配置密钥（开发环境可忽略）");
            }
            
            log.info("配置验证通过");
        } else {
            log.warn("API 未启用，应用商店功能将不可用");
        }
    }
    
    /**
     * 获取应用列表（分页）
     * 
     * @param page 页码
     * @param size 每页大小
     * @param keyword 关键词
     * @return 分页结果
     */
    public PageResult<ApplicationDTO> getApplicationsPage(Integer page, Integer size, String keyword) {
        return getApplicationsPage(page, size, keyword, null);
    }
    
    /**
     * 获取应用列表（分页，支持按插件类型过滤）
     * 
     * @param page 页码
     * @param size 每页大小
     * @param keyword 关键词
     * @param pluginType 插件类型（如 MIDDLEWARE, TASK, TRIGGER, APPLICATION）
     * @return 分页结果
     */
    public PageResult<ApplicationDTO> getApplicationsPage(Integer page, Integer size, String keyword, String pluginType) {
        // 检查配置是否加载
        if (config == null || config.getApi() == null) {
            log.error("应用商店配置未加载，请检查 plugin.yml 配置");
            return createEmptyPageResult(page, size);
        }
        
        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            log.debug("API 未启用，返回空列表");
            return createEmptyPageResult(page, size);
        }
        
        try {
            // 修改为 /packages 端点
            String url = String.format("%s/packages?page=%d&size=%d", 
                config.getApi().getBaseUrl(), page, size);
            
            if (keyword != null && !keyword.isEmpty()) {
                url += "&keyword=" + keyword;
            }
            
            if (pluginType != null && !pluginType.isEmpty()) {
                url += "&pluginType=" + pluginType;
            }
            
            log.debug("请求应用列表: {}", url);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<ApplicationListResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                ApplicationListResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ApplicationListResponse body = response.getBody();
                if (body != null && body.data != null && body.data.items != null) {
                    log.info("获取应用列表成功，共 {} 个应用，总数 {}", body.data.items.size(), body.data.total);
                    
                    // 转换 PluginPackageInfo 到 ApplicationDTO
                    List<ApplicationDTO> applications = body.data.items.stream()
                        .map(this::convertToApplicationDTO)
                        .collect(Collectors.toList());
                    
                    // 创建分页结果
                    PageResult<ApplicationDTO> pageResult = new PageResult<>();
                    pageResult.setItems(applications);
                    pageResult.setTotal(body.data.total != null ? body.data.total : (long) applications.size());
                    pageResult.setPage(body.data.page != null ? body.data.page : page);
                    pageResult.setSize(body.data.size != null ? body.data.size : size);
                    pageResult.setTotalPages(body.data.totalPages);
                    
                    return pageResult;
                }
            }
            
            log.warn("获取应用列表失败: {}", response.getStatusCode());
            return createEmptyPageResult(page, size);
            
        } catch (Exception e) {
            log.error("获取应用列表失败", e);
            return createEmptyPageResult(page, size);
        }
    }
    
    /**
     * 获取应用列表（兼容旧接口）
     * 
     * @param page 页码
     * @param size 每页大小
     * @param keyword 关键词
     * @return 应用列表
     */
    public List<ApplicationDTO> getApplications(Integer page, Integer size, String keyword) {
        PageResult<ApplicationDTO> pageResult = getApplicationsPage(page, size, keyword);
        return pageResult != null && pageResult.getItems() != null ? pageResult.getItems() : Collections.emptyList();
    }
    
    /**
     * 创建空的分页结果
     */
    private PageResult<ApplicationDTO> createEmptyPageResult(Integer page, Integer size) {
        PageResult<ApplicationDTO> pageResult = new PageResult<>();
        pageResult.setItems(Collections.emptyList());
        pageResult.setTotal(0L);
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setTotalPages(0);
        return pageResult;
    }
    
    /**
     * 获取应用详情
     * 
     * @param pluginId 插件ID
     * @return 应用详情
     */
    public ApplicationDTO getApplicationDetail(String pluginId) {
        // 检查配置是否加载
        if (config == null || config.getApi() == null) {
            log.error("应用商店配置未加载，请检查 plugin.yml 配置");
            return null;
        }
        
        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            log.debug("API 未启用");
            return null;
        }
        
        try {
            // 修改为 /packages/{pluginId} 端点
            String url = String.format("%s/packages/%s", 
                config.getApi().getBaseUrl(), pluginId);
            
            log.debug("请求应用详情: {}", url);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<ApplicationDetailResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                ApplicationDetailResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ApplicationDetailResponse body = response.getBody();
                if (body != null && body.data != null) {
                    log.info("获取应用详情成功: {}", pluginId);
                    return convertToApplicationDTO(body.data);
                }
            }
            
            log.warn("获取应用详情失败: {}", response.getStatusCode());
            return null;
            
        } catch (Exception e) {
            log.error("获取应用详情失败: pluginId={}", pluginId, e);
            return null;
        }
    }

    /**
     * 根据插件ID和版本获取远程版本信息
     *
     * @param pluginId 插件ID
     * @param version  版本号
     * @return 版本对应的应用信息
     */
    public ApplicationDTO getApplicationVersionDetail(String pluginId, String version) {
        // 检查配置是否加载
        if (config == null || config.getApi() == null) {
            log.error("应用商店配置未加载，请检查 plugin.yml 配置");
            return null;
        }

        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            log.debug("API 未启用");
            return null;
        }

        try {
            // /packages/{pluginId}/versions/{version}
            String url = String.format("%s/packages/%s/versions/%s",
                config.getApi().getBaseUrl(), pluginId, version);

             if(StringUtils.isBlank(version)){
                 url = String.format("%s/packages/%s",
                         config.getApi().getBaseUrl(), pluginId);
             }

            log.debug("请求应用版本详情: {}", url);

            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<ApplicationDetailResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                ApplicationDetailResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ApplicationDetailResponse body = response.getBody();
                if (body != null && body.data != null) {
                    log.info("获取应用版本详情成功: pluginId={}, version={}", pluginId, version);
                    return convertToApplicationDTO(body.data);
                }
            }

            log.warn("获取应用版本详情失败: HTTP {}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.error("获取应用版本详情失败: pluginId={}, version={}", pluginId, version, e);
            return null;
        }
    }

    /**
     * 下载应用
     * 
     * @param pluginId 插件ID
     * @return 下载的文件URL
     * @throws RuntimeException 下载失败时抛出异常
     */
    public String downloadApplication(String pluginId) {
        // 检查配置是否加载
        if (config == null || config.getApi() == null) {
            String errorMsg = "应用商店配置未加载，请检查 plugin.yml 配置";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        
        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            String errorMsg = "应用商店 API 未启用";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        
        try {
            // 修改为 /packages/{pluginId}/download 端点
            String url = String.format("%s/packages/%s/download", 
                config.getApi().getBaseUrl(), pluginId);
            
            log.info("下载应用: pluginId={}, url={}", pluginId, url);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                byte[].class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // 使用 FileStorageService 保存文件
                String fileName = extractFileName(response.getHeaders());
                if (fileName == null) {
                    fileName = pluginId + ".jar";
                }
                
                byte[] fileData = response.getBody();
                
                log.info("开始保存应用文件: {}, 大小: {} bytes", fileName, fileData.length);
                
                String fileUrl = fileStorageService
                    .upload(new ByteArrayInputStream(fileData), fileName)
                    .withMetadata("pluginId", pluginId)
                    .withMetadata("category", "plugin")
                    .onSuccess(savedUrl -> log.info("应用文件保存成功: {}", savedUrl))
                    .onError(e -> {
                        log.error("应用文件保存失败", e);
                        throw new RuntimeException("应用文件保存失败: " + e.getMessage(), e);
                    })
                    .get();
                
                if (fileUrl == null || fileUrl.isEmpty()) {
                    throw new RuntimeException("文件保存失败，返回的 URL 为空");
                }
                
                log.info("应用下载成功: {}", fileUrl);
                return fileUrl;
            }
            
            String errorMsg = String.format("应用下载失败: HTTP %d", response.getStatusCode().value());
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("应用下载失败: pluginId=%s, error=%s", pluginId, e.getMessage());
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * 根据插件ID和版本下载应用
     *
     * @param pluginId 插件ID
     * @param version  版本号
     * @return 下载的文件URL
     * @throws RuntimeException 下载失败时抛出异常
     */
    public String downloadApplication(String pluginId, String version) {
        // 检查配置是否加载
        if (config == null || config.getApi() == null) {
            String errorMsg = "应用商店配置未加载，请检查 plugin.yml 配置";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            String errorMsg = "应用商店 API 未启用";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        try {
            // /packages/{pluginId}/versions/{version}/download
            String url = String.format("%s/packages/%s/versions/%s/download",
                config.getApi().getBaseUrl(), pluginId, version);

            log.info("按版本下载应用: pluginId={}, version={}, url={}", pluginId, version, url);

            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                byte[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String fileName = extractFileName(response.getHeaders());
                if (fileName == null) {
                    fileName = pluginId + "-" + version + ".jar";
                }

                byte[] fileData = response.getBody();

                log.info("开始保存按版本下载的应用文件: {}, 大小: {} bytes", fileName, fileData.length);

                String fileUrl = fileStorageService
                    .upload(new ByteArrayInputStream(fileData), fileName)
                    .withMetadata("pluginId", pluginId)
                    .withMetadata("version", version)
                    .withMetadata("category", "plugin")
                    .onSuccess(savedUrl -> log.info("按版本下载的应用文件保存成功: {}", savedUrl))
                    .onError(e -> {
                        log.error("按版本下载的应用文件保存失败", e);
                        throw new RuntimeException("应用文件保存失败: " + e.getMessage(), e);
                    })
                    .get();

                if (fileUrl == null || fileUrl.isEmpty()) {
                    throw new RuntimeException("文件保存失败，返回的 URL 为空");
                }

                log.info("按版本应用下载成功: {}", fileUrl);
                return fileUrl;
            }

            String errorMsg = String.format("按版本应用下载失败: HTTP %d", response.getStatusCode().value());
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("按版本应用下载失败: pluginId=%s, version=%s, error=%s",
                    pluginId, version, e.getMessage());
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * 获取插件表权限信息
     * 
     * @param pluginId 插件ID
     * @return 表权限列表
     */
    public List<PluginTablePermissionInfo> getTablePermissions(String pluginId) {
        // 检查配置是否加载
        if (config == null || config.getApi() == null) {
            log.error("应用商店配置未加载，请检查 plugin.yml 配置");
            return Collections.emptyList();
        }
        
        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            log.debug("API 未启用");
            return Collections.emptyList();
        }
        
        try {
            // 调用 /api/appstore/packages/{pluginId}/table-permissions 端点
            String url = String.format("%s/packages/%s/table-permissions", 
                config.getApi().getBaseUrl(), pluginId);
            
            log.debug("请求插件表权限信息: {}", url);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<TablePermissionsResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                TablePermissionsResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TablePermissionsResponse body = response.getBody();
                if (body != null && body.success && body.data != null) {
                    log.info("获取插件表权限信息成功: pluginId={}, count={}", pluginId, body.data.size());
                    return body.data;
                }
            }
            
            log.warn("获取插件表权限信息失败: HTTP {}", response.getStatusCode());
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.warn("获取插件表权限信息失败: pluginId={}", pluginId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 转换 PluginPackageInfo 到 ApplicationDTO
     */
    private ApplicationDTO convertToApplicationDTO(PluginPackageInfo info) {
        ApplicationDTO dto = new ApplicationDTO();
        dto.setId(info.getId());
        dto.setPluginId(info.getPluginId());
        dto.setApplicationName(info.getPluginName());
        dto.setPluginVersion(info.getVersion());
        dto.setDescription(info.getDescription());
        dto.setAuthor(info.getDeveloperName());
        dto.setApplicationType("plugin");
        dto.setApplicationTypeText("插件应用"); // 设置应用类型文本
        dto.setPluginType(info.getPluginType()); // 设置插件类型
        dto.setStatus(1); // ONLINE 状态映射为 1
        dto.setStatusText("启用"); // 设置状态文本
        dto.setInstallTime(info.getUploadTime());
        dto.setUpdateTime(info.getUploadTime());
        
        // 转换依赖信息（从 JSON 字符串解析）
        if (info.getDependencies() != null && !info.getDependencies().trim().isEmpty()) {
            try {
                List<DependencyInfo> depList = JSON.parseArray(info.getDependencies(),DependencyInfo.class);
                
                List<ApplicationDTO.DependencyInfo> dependencies = new java.util.ArrayList<>();
                for (DependencyInfo dep : depList) {
                    ApplicationDTO.DependencyInfo depInfo = new ApplicationDTO.DependencyInfo();
                    depInfo.setPluginId(dep.getPluginId());
                    depInfo.setVersion(dep.getVersion());
                    depInfo.setOptional(dep.getOptional() != null ? dep.getOptional() : false);
                    depInfo.setVersionRange(dep.getVersionRange());
                    dependencies.add(depInfo);
                }
                dto.setDependencies(dependencies);
            } catch (Exception e) {
                log.warn("解析依赖信息失败: {}", info.getDependencies(), e);
            }
        }
        
        return dto;
    }
    
    /**
     * 从响应头提取文件名
     */
    private String extractFileName(HttpHeaders headers) {
        List<String> contentDisposition = headers.get(HttpHeaders.CONTENT_DISPOSITION);
        if (contentDisposition != null && !contentDisposition.isEmpty()) {
            String disposition = contentDisposition.get(0);
            int filenameIndex = disposition.indexOf("filename=\"");
            if (filenameIndex >= 0) {
                int start = filenameIndex + 10;
                int end = disposition.indexOf("\"", start);
                if (end > start) {
                    return disposition.substring(start, end);
                }
            }
        }
        return null;
    }
    
    /**
     * 创建请求头
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        
        // 添加认证密钥
        String secretKey = config.getApi().getSecretKey();
        if (secretKey != null && !secretKey.isEmpty()) {
            headers.set("X-API-Key", secretKey);
        }
        
        return headers;
    }
    
    /**
     * 应用列表响应（匹配 Result<PageResult<PluginPackageDTO>> 结构）
     */
    @Data
    private static class ApplicationListResponse {
        private boolean success;
        private String errorMessage;
        private PageData data;
    }
    
    /**
     * 分页数据
     */
    @Data
    private static class PageData {
        private List<PluginPackageInfo> items;
        private Long total;
        private Integer page;
        private Integer size;
        private Integer totalPages;
    }
    
    /**
     * 应用详情响应（匹配 Result<PluginPackageDTO> 结构）
     */
    @Data
    private static class ApplicationDetailResponse {
        private boolean success;
        private String errorMessage;
        private PluginPackageInfo data;
    }
    
    /**
     * 插件包信息（匹配 PluginPackageDTO 结构）
     */
    @Data
    private static class PluginPackageInfo {
        private Long id;
        private String pluginId;
        private String pluginName;
        private String pluginType;
        private String version;
        private String fileUrl;
        private String fileName;
        private Long fileSize;
        private String description;
        private String releaseNotes;
        private String icon;
        private String category;
        private String developerId;
        private String developerName;
        private String uploadBy;
        private LocalDateTime uploadTime;
        private String status;
        private Integer downloadCount;
        private Double ratingAverage;
        private String md5;
        private String sha256;
        private String dependencies; // JSON格式的依赖信息字符串
    }
    
    /**
     * 依赖信息
     */
    @Data
    private static class DependencyInfo {
        private String pluginId;
        private String version;
        private Boolean optional;
        private String versionRange;
    }
    
    /**
     * 表权限列表响应（匹配 Result<List<PluginTablePermissionDTO>> 结构）
     */
    @Data
    private static class TablePermissionsResponse {
        private boolean success;
        private String errorMessage;
        private List<PluginTablePermissionInfo> data;
    }
    
    /**
     * 插件表权限信息（匹配 PluginTablePermissionDTO 结构）
     */
    @Data
    public static class PluginTablePermissionInfo {
        private Long id;
        private String pluginId;
        private String tableName;
        private String allowedOperations;
        private Boolean isReadonly;
        private String description;
        private Boolean enabled;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private String createBy;
        private String updateBy;
    }
    
    /**
     * 从远程应用商店的 jar 包中解析配置元数据
     * 
     * @param pluginId 插件ID
     * @return 配置元数据列表
     */
    public java.util.List< com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata> getPluginConfigMetadataFromJar(String pluginId) {
        // 检查配置是否加载
        if (config == null || config.getApi() == null) {
            log.error("应用商店配置未加载，请检查 plugin.yml 配置");
            return java.util.Collections.emptyList();
        }
        
        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            log.debug("API 未启用");
            return java.util.Collections.emptyList();
        }
        
        java.nio.file.Path tmpJarPath = null;
        try {
            // 1. 下载 jar 包到临时文件
            String url = String.format("%s/packages/%s/download", 
                config.getApi().getBaseUrl(), pluginId);
            
            log.info("下载插件包以解析配置元数据: pluginId={}, url={}", pluginId, url);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                byte[].class
            );
            
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("下载插件包失败: HTTP {}", response.getStatusCode());
                return java.util.Collections.emptyList();
            }
            
            // 2. 保存到临时文件
            byte[] jarBytes = response.getBody();
            tmpJarPath = java.nio.file.Files.createTempFile("plugin-config-", ".jar");
            java.nio.file.Files.write(tmpJarPath, jarBytes);
            
            log.debug("插件包已保存到临时文件: {}", tmpJarPath);
            
            // 3. 从 jar 文件中解析配置元数据
            return parseConfigMetadataFromJar(tmpJarPath);
            
        } catch (Exception e) {
            log.error("从 jar 包解析配置元数据失败: pluginId={}", pluginId, e);
            return java.util.Collections.emptyList();
        } finally {
            // 4. 清理临时文件
            if (tmpJarPath != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tmpJarPath);
                } catch (Exception e) {
                    log.warn("清理临时文件失败: {}", tmpJarPath, e);
                }
            }
        }
    }
    
    /**
     * 从 jar 文件中解析配置元数据
     * 仅从 install-workflow.yml 中读取 configClass，不再进行全局扫描
     * 
     * @param jarPath jar 文件路径
     * @return 配置元数据列表
     */
    private java.util.List< com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata> parseConfigMetadataFromJar(java.nio.file.Path jarPath) {
        try {
            // 从 install-workflow.yml 中读取 configClass
            String configClassName = parseConfigClassFromWorkflow(jarPath);
            
            if (configClassName == null || configClassName.trim().isEmpty()) {
                log.debug("install-workflow.yml 中未指定 configClass，返回空配置元数据");
                return java.util.Collections.emptyList();
            }
            
            log.info("从 install-workflow.yml 找到配置类: {}", configClassName);
            
            // 直接加载指定的配置类
            try {
                java.net.URL jarUrl = jarPath.toUri().toURL();
                java.net.URLClassLoader classLoader = new java.net.URLClassLoader(
                    new java.net.URL[]{jarUrl},
                    Thread.currentThread().getContextClassLoader()
                );
                
                try {
                    Class<?> configClass = classLoader.loadClass(configClassName);
                    
                    // 检查是否是 Input 类型
                    if ( com.keqi.gress.common.plugin.dto.Input.class.isAssignableFrom(configClass)) {
                        @SuppressWarnings("unchecked")
                        Class<? extends  com.keqi.gress.common.plugin.dto.Input> inputClass = 
                            (Class<? extends  com.keqi.gress.common.plugin.dto.Input>) configClass;
                        
                         com.keqi.gress.common.plugin.FormMetadataParser.FormMetadata formMetadata = 
                             com.keqi.gress.common.plugin.FormMetadataParser.parse(inputClass);
                        
                        if (formMetadata != null && formMetadata.getFields() != null && !formMetadata.getFields().isEmpty()) {
                            log.info("成功从指定配置类解析元数据: className={}, fields={}", configClassName, formMetadata.getFields().size());
                            return formMetadata.getFields();
                        } else {
                            log.warn("配置类 {} 解析后没有字段", configClassName);
                            return java.util.Collections.emptyList();
                        }
                    } else {
                        log.warn("配置类 {} 不是 Input 类型", configClassName);
                        return java.util.Collections.emptyList();
                    }
                } finally {
                    classLoader.close();
                }
            } catch (ClassNotFoundException e) {
                log.error("无法加载配置类: {}", configClassName, e);
                return java.util.Collections.emptyList();
            } catch (Exception e) {
                log.error("解析配置类失败: {}", configClassName, e);
                return java.util.Collections.emptyList();
            }
            
        } catch (Exception e) {
            log.error("解析 jar 文件配置元数据失败: jarPath={}", jarPath, e);
            return java.util.Collections.emptyList();
        }
    }
    
    /**
     * 从 install-workflow.yml 中解析 configClass
     * 
     * @param jarPath jar 文件路径
     * @return 配置类全限定名，如果不存在则返回 null
     */
    private String parseConfigClassFromWorkflow(java.nio.file.Path jarPath) {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath.toFile())) {
            // 查找工作流定义文件（按优先级）
            String[] candidates = {
                "install-workflow.yml",
                "install-workflow.yaml",
                "workflow.yml",
                "workflow.yaml"
            };
            
            for (String candidate : candidates) {
                java.util.jar.JarEntry entry = jarFile.getJarEntry(candidate);
                if (entry != null) {
                    try (java.io.InputStream in = jarFile.getInputStream(entry)) {
                        com.fasterxml.jackson.databind.ObjectMapper yamlMapper = 
                            new com.fasterxml.jackson.databind.ObjectMapper(
                                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()
                            );
                        
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> workflow = yamlMapper.readValue(in, java.util.Map.class);
                        
                        if (workflow != null && workflow.containsKey("configClass")) {
                            Object configClassObj = workflow.get("configClass");
                            if (configClassObj != null) {
                                return configClassObj.toString();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析 install-workflow.yml 失败: {}", e.getMessage());
        }
        
        return null;
    }
    
}
