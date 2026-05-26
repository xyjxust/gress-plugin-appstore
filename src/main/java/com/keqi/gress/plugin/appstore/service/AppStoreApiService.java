package com.keqi.gress.plugin.appstore.service;

import com.alibaba.fastjson2.JSON;
import  org.springframework.beans.factory.annotation.Autowired;
import  jakarta.annotation.PostConstruct;
import  org.springframework.stereotype.Service;
import  com.keqi.gress.common.storage.FileStorageService;
import com.keqi.gress.common.utils.ServletPathUtils;
import com.keqi.gress.plugin.appstore.config.AppStoreConfig;
import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import com.keqi.gress.plugin.appstore.dto.PageResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 应用商店 API 服务
 * 
 * 负责与远程应用商店 API 交互，获取应用信息、下载应用等
 */
@Slf4j
@Service
public class AppStoreApiService {
   // private Log log   = LogFactory.get(AppStoreApiService.class);
    
    @Autowired
    private AppStoreConfig config;
    
    @Autowired
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
     * 获取分类列表
     *
     * 优先走匿名用户端接口：appstore-admin `/plugins/as-admin/anon/categories`
     * 兜底兼容旧接口：appstore-admin `/categories`
     */
    public List<com.keqi.gress.plugin.appstore.dto.AppStoreCategoryDTO> getCategories() {
        if (config == null || config.getApi() == null) {
            log.warn("AppStoreConfig 未加载，无法获取分类");
            return Collections.emptyList();
        }

        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            log.debug("API 未启用，返回空分类列表");
            return Collections.emptyList();
        }

        try {
            String base = getConfiguredApiBaseUrl();
            if (StringUtils.isBlank(base)) return Collections.emptyList();
            String apiAppstoreRoot = getAnonymousApiBaseUrl(base);

            ResponseEntity<String> response = null;

            // 1) 优先：/plugins/as-admin/anon/categories
            try {
                String url = apiAppstoreRoot + "/categories";
                log.debug("请求分类列表(用户端): {}", url);
                response = restTemplate.exchange(url, HttpMethod.GET, createGetEntity(url), String.class);
            } catch (Exception e) {
                log.debug("请求分类列表(用户端)失败，尝试兜底 /categories", e);
            }

            // 2) 兜底：/categories（旧 CategoryController）
            if (response == null || response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                String rootOnly = base;
                int idx2 = rootOnly.indexOf("/api/appstore");
                if (idx2 > 0) rootOnly = rootOnly.substring(0, idx2);
                if (rootOnly.endsWith("/")) rootOnly = rootOnly.substring(0, rootOnly.length() - 1);
                String url = rootOnly + "/categories";
                log.debug("请求分类列表(兜底): {}", url);
                response = restTemplate.exchange(url, HttpMethod.GET, createGetEntity(url), String.class);
            }

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("获取分类列表失败: {}", response.getStatusCode());
                return Collections.emptyList();
            }

            CategoryListResponse parsed = JSON.parseObject(response.getBody(), CategoryListResponse.class);
            if (parsed == null || !parsed.success || parsed.data == null) {
                return Collections.emptyList();
            }

            return parsed.data.stream()
                .filter(Objects::nonNull)
                .filter(it -> it.enabled == null || Boolean.TRUE.equals(it.enabled))
                .sorted(Comparator.comparingInt(it -> it.displayOrder != null ? it.displayOrder : 9999))
                .map(it -> com.keqi.gress.plugin.appstore.dto.AppStoreCategoryDTO.builder()
                    .categoryKey(it.categoryKey)
                    .categoryName(it.categoryName)
                    .description(it.description)
                    .icon(it.icon)
                    .displayOrder(it.displayOrder)
                    .enabled(it.enabled)
                    .build()
                )
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取分类列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取标签列表（按 tagTypeKey 过滤）
     *
     * 来源：appstore-admin `/plugins/as-admin/anon/tags?typeKey=xxx`
     */
    public List<com.keqi.gress.plugin.appstore.dto.AppStoreCategoryDTO> getTags(String typeKey) {
        if (config == null || config.getApi() == null) {
            return Collections.emptyList();
        }
        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            return Collections.emptyList();
        }
        try {
            String base = getConfiguredApiBaseUrl();
            if (StringUtils.isBlank(base)) return Collections.emptyList();
            String apiAppstoreRoot = getAnonymousApiBaseUrl(base);

            String url = apiAppstoreRoot + "/tags";
            if (StringUtils.isNotBlank(typeKey)) {
                url += "?typeKey=" + typeKey;
            }
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, createGetEntity(url), String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return Collections.emptyList();
            }
            TagListResponse parsed = JSON.parseObject(response.getBody(), TagListResponse.class);
            if (parsed == null || !parsed.success || parsed.data == null) {
                return Collections.emptyList();
            }
            return parsed.data.stream()
                .filter(Objects::nonNull)
                .filter(it -> it.enabled == null || Boolean.TRUE.equals(it.enabled))
                .map(it -> com.keqi.gress.plugin.appstore.dto.AppStoreCategoryDTO.builder()
                    .categoryKey(it.tagKey)
                    .categoryName(it.tagName)
                    .description(it.description)
                    .enabled(it.enabled)
                    .build()
                )
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取标签列表失败, typeKey={}", typeKey, e);
            return Collections.emptyList();
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
        return getApplicationsPage(page, size, keyword, null, null);
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
        return getApplicationsPage(page, size, keyword, pluginType, null);
    }

    /**
     * 获取应用列表（分页，支持按插件类型、分类过滤）
     *
     * @param category 分类 key（appstore-admin 的 Category.categoryKey）
     */
    public PageResult<ApplicationDTO> getApplicationsPage(Integer page, Integer size, String keyword, String pluginType, String category) {
        return getApplicationsPage(page, size, keyword, pluginType, category, null, null);
    }

    /**
     * 获取应用列表（分页，支持按插件类型、分类、标签、价格过滤）
     */
    public PageResult<ApplicationDTO> getApplicationsPage(
        Integer page,
        Integer size,
        String keyword,
        String pluginType,
        String category,
        String tag,
        String priceType
    ) {
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
                getAnonymousApiBaseUrl(), page, size);
            
            if (keyword != null && !keyword.isEmpty()) {
                url += "&keyword=" + keyword;
            }
            
            if (pluginType != null && !pluginType.isEmpty()) {
                url += "&pluginType=" + pluginType;
            }

            if (category != null && !category.isEmpty()) {
                url += "&category=" + category;
            }

            if (tag != null && !tag.isEmpty()) {
                url += "&tag=" + tag;
            }

            if (priceType != null && !priceType.isEmpty()) {
                url += "&priceType=" + priceType;
            }
            
            log.debug("请求应用列表: {}", url);
            
            HttpEntity<Void> entity = createGetEntity(url);
            
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
     * 获取应用发布版本列表（用户端）
     *
     * 来源：appstore-admin `/plugins/as-admin/anon/packages/{pluginId}/versions`
     */
    public List<com.keqi.gress.plugin.appstore.dto.AppStorePackageVersionDTO> getApplicationVersions(String pluginId) {
        if (config == null || config.getApi() == null) {
            return Collections.emptyList();
        }
        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            return Collections.emptyList();
        }

        try {
            String base = getAnonymousApiBaseUrl();
            if (StringUtils.isBlank(base)) return Collections.emptyList();
            String url = String.format("%s/packages/%s/versions", base, pluginId);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, createGetEntity(url), String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return Collections.emptyList();
            }

            VersionsResponse parsed = JSON.parseObject(response.getBody(), VersionsResponse.class);
            if (parsed == null || !parsed.success || parsed.data == null) return Collections.emptyList();

            return parsed.data.stream()
                .filter(Objects::nonNull)
                .map(v -> com.keqi.gress.plugin.appstore.dto.AppStorePackageVersionDTO.builder()
                    .pluginId(v.pluginId)
                    .version(v.version)
                    .releaseNotes(v.releaseNotes)
                    .fileSize(v.fileSize)
                    .uploadTime(v.uploadTime)
                    .current(v.current)
                    .build()
                )
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取应用版本列表失败: pluginId={}", pluginId, e);
            return Collections.emptyList();
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
                getAnonymousApiBaseUrl(), pluginId);
            
            log.debug("请求应用详情: {}", url);
            
            HttpEntity<Void> entity = createGetEntity(url);
            
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
                getAnonymousApiBaseUrl(), pluginId, version);

             if(StringUtils.isBlank(version)){
                 url = String.format("%s/packages/%s",
                         getAnonymousApiBaseUrl(), pluginId);
             }

            log.debug("请求应用版本详情: {}", url);

            HttpEntity<Void> entity = createGetEntity(url);

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
            String token = requestDownloadToken(pluginId, null);
            if (StringUtils.isBlank(token)) {
                throw new RuntimeException("获取下载令牌失败");
            }
            // 先换 token，再下载
            String url = String.format("%s/packages/%s/download?token=%s",
                getAnonymousApiBaseUrl(), pluginId, token);
            
            log.info("下载应用: pluginId={}, url={}", pluginId, url);
            
            HttpEntity<Void> entity = createGetEntity(url);
            
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
     * Fetch trusted signing roots from appstore-admin.
     *
     * <p>Stage B: enables smooth rotation by trusting multiple roots (old+new window).</p>
     */
    public java.util.List<TrustedRootDTO> getTrustedRoots() {
        // 复用 API 配置校验逻辑
        if (config == null || config.getApi() == null) {
            log.error("应用商店配置未加载，请检查 plugin.yml 中的 appstore.api 配置");
            return java.util.Collections.emptyList();
        }
        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            return java.util.Collections.emptyList();
        }

        try {
            // GET {baseUrl}/signing/trusted-roots
            String url = String.format("%s/signing/trusted-roots", getAnonymousApiBaseUrl());

            HttpEntity<Void> entity = createGetEntity(url);

            ResponseEntity<TrustedRootsResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                TrustedRootsResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TrustedRootsResponse body = response.getBody();
                if (body != null && body.data != null) {
                    return body.data;
                }
            }

            log.warn("获取可信签名根失败: HTTP {}", response.getStatusCode());
            return java.util.Collections.emptyList();
        } catch (Exception e) {
            log.error("获取可信签名根失败", e);
            return java.util.Collections.emptyList();
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
            String token = requestDownloadToken(pluginId, version);
            if (StringUtils.isBlank(token)) {
                throw new RuntimeException("获取下载令牌失败");
            }
            // /packages/{pluginId}/versions/{version}/download?token=...
            String url = String.format("%s/packages/%s/versions/%s/download?token=%s",
                getAnonymousApiBaseUrl(), pluginId, version, token);

            log.info("按版本下载应用: pluginId={}, version={}, url={}", pluginId, version, url);

            HttpEntity<Void> entity = createGetEntity(url);

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
     * 按版本下载应用字节（供控制器转发给浏览器下载）
     */
    public DownloadedFile downloadApplicationBytes(String pluginId, String version) {
        if (config == null || config.getApi() == null) {
            throw new RuntimeException("应用商店配置未加载，请检查 plugin.yml 配置");
        }
        if (!Boolean.TRUE.equals(config.getApi().getEnabled())) {
            throw new RuntimeException("应用商店 API 未启用");
        }
        try {
            String token = requestDownloadToken(pluginId, version);
            if (StringUtils.isBlank(token)) {
                throw new RuntimeException("获取下载令牌失败");
            }
            String url = String.format("%s/packages/%s/versions/%s/download?token=%s",
                getAnonymousApiBaseUrl(), pluginId, version, token);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                createGetEntity(url),
                byte[].class
            );
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new RuntimeException("按版本应用下载失败: HTTP " + response.getStatusCode().value());
            }
            String fileName = extractFileName(response.getHeaders());
            if (StringUtils.isBlank(fileName)) {
                fileName = pluginId + "-" + version + ".jar";
            }
            return new DownloadedFile(fileName, response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("按版本应用下载失败: " + e.getMessage(), e);
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
            // 调用 /api/appstore/anon/packages/{pluginId}/table-permissions 端点
            String url = String.format("%s/packages/%s/table-permissions",
                getAnonymousApiBaseUrl(), pluginId);
            
            log.debug("请求插件表权限信息: {}", url);
            
            HttpEntity<Void> entity = createGetEntity(url);
            
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
        dto.setSha256(info.getSha256());
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
    
    private HttpEntity<Void> createGetEntity(String url) {
        return new HttpEntity<>(createHeaders("GET", url));
    }

    /**
     * 方案A：KeyId + HMAC-SHA256 + timestamp + nonce
     *
     * canonical = METHOD \\n PATH \\n RAW_QUERY \\n TIMESTAMP \\n NONCE
     */
    private HttpHeaders createHeaders(String method, String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

//        if (isAnonymousStoreUrl(url)) {
//            return headers;
//        }

        String keyId = config.getApi().getKeyId();
        String secret = config.getApi().getSecretKey();
        if (StringUtils.isNotBlank(keyId) && StringUtils.isNotBlank(secret)) {
            long ts = System.currentTimeMillis();
            String nonce = java.util.UUID.randomUUID().toString().replace("-", "");
            java.net.URI uri = java.net.URI.create(url);
            String path = ServletPathUtils.stripContextPath(uri.getPath());
            String query = uri.getRawQuery();
            String canonical = method.toUpperCase() + "\n" + path + "\n" + (query == null ? "" : query) + "\n" + ts + "\n" + nonce;

            headers.set("X-AppStore-KeyId", keyId);
            headers.set("X-AppStore-Timestamp", String.valueOf(ts));
            headers.set("X-AppStore-Nonce", nonce);
            headers.set("X-AppStore-Signature", signHmacBase64(secret, canonical));
            return headers;
        }

        // fallback legacy header
        String legacy = config.getApi().getSecretKey();
        if (StringUtils.isNotBlank(legacy)) {
            headers.set("X-API-Key", legacy);
        }
        return headers;
    }

    private String getConfiguredApiBaseUrl() {
        if (config == null || config.getApi() == null) {
            return null;
        }
        String baseUrl = config.getApi().getBaseUrl();
        if (StringUtils.isBlank(baseUrl)) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String getAnonymousApiBaseUrl() {
        return getAnonymousApiBaseUrl(getConfiguredApiBaseUrl());
    }

    private String getAnonymousApiBaseUrl(String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            return baseUrl;
        }
        if (baseUrl.contains("/plugins/as-admin/anon")) {
            return baseUrl.substring(0, baseUrl.indexOf("/plugins/as-admin/anon")) + "/plugins/as-admin/anon";
        }
        if (baseUrl.contains("/plugins/as-admin")) {
            return baseUrl.substring(0, baseUrl.indexOf("/plugins/as-admin")) + "/plugins/as-admin/anon";
        }
        if (baseUrl.contains("/plugins/appstore-admin")) {
            return baseUrl.substring(0, baseUrl.indexOf("/plugins/appstore-admin")) + "/plugins/as-admin/anon";
        }
        if (baseUrl.contains("/api/appstore/anon")) {
            return baseUrl.substring(0, baseUrl.indexOf("/api/appstore/anon")) + "/plugins/as-admin/anon";
        }
        if (baseUrl.contains("/api/appstore")) {
            return baseUrl.substring(0, baseUrl.indexOf("/api/appstore")) + "/plugins/as-admin/anon";
        }
        return baseUrl + "/anon";
    }

    private boolean isAnonymousStoreUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        return url.contains("/plugins/as-admin/anon/");
    }

    private static String signHmacBase64(String secret, String msg) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(msg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("sign failed: " + e.getMessage(), e);
        }
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

    @Data
    private static class CategoryListResponse {
        private boolean success;
        private String errorMessage;
        private List<CategoryItem> data;
    }

    @Data
    private static class CategoryItem {
        private Long id;
        private String categoryName;
        private String categoryKey;
        private String description;
        private String icon;
        private Integer displayOrder;
        private Boolean enabled;
        private Integer pluginCount;
    }

    @Data
    private static class TagListResponse {
        private boolean success;
        private String errorMessage;
        private List<TagItem> data;
    }

    @Data
    private static class TagItem {
        private Long id;
        private String tagName;
        private String tagKey;
        private String description;
        private String tagTypeKey;
        private Boolean enabled;
    }

    @Data
    private static class VersionsResponse {
        private boolean success;
        private String errorMessage;
        private List<VersionItem> data;
    }

    @Data
    private static class VersionItem {
        private String pluginId;
        private String version;
        private String releaseNotes;
        private Long fileSize;
        private LocalDateTime uploadTime;
        private Boolean current;
    }

    @Data
    private static class TrustedRootsResponse {
        private boolean success;
        private String errorMessage;
        private java.util.List<TrustedRootDTO> data;
    }

    @Data
    private static class DownloadTokenApiResponse {
        private boolean success;
        private String errorMessage;
        private DownloadTokenData data;
    }

    @Data
    private static class DownloadTokenData {
        private String token;
        private Long expireAtEpochMs;
    }

    /**
     * Trusted root material returned by appstore-admin.
     */
    @Data
    public static class TrustedRootDTO {
        private String keyId;
        private String alias;
        /**
         * PEM string: CERTIFICATE or PUBLIC KEY.
         */
        private String publicKeyPem;
        private String fingerprintSha256;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class DownloadedFile {
        private String fileName;
        private byte[] bytes;
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
        return getPluginInstallConfigMetadataFromJar(pluginId);
    }

    /**
     * 从远程应用商店的 jar 包中解析安装前配置元数据。
     */
    public java.util.List<com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata> getPluginInstallConfigMetadataFromJar(String pluginId) {
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
            // 1. 先换 token，再下载 jar 包到临时文件
            String token = requestDownloadToken(pluginId, null);
            if (StringUtils.isBlank(token)) {
                log.warn("获取下载令牌失败: pluginId={}", pluginId);
                return java.util.Collections.emptyList();
            }
            String url = String.format("%s/packages/%s/download?token=%s",
                getAnonymousApiBaseUrl(), pluginId, token);
            
            log.info("下载插件包以解析配置元数据: pluginId={}, url={}", pluginId, url);
            
            HttpEntity<Void> entity = createGetEntity(url);
            
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
            return parseInstallConfigMetadataFromJar(tmpJarPath);
            
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
     * 请求短期一次性下载 token
     */
    public String requestDownloadToken(String pluginId, String version) {
        try {
            String url;
            if (StringUtils.isNotBlank(version)) {
                url = String.format("%s/packages/%s/versions/%s/download-token",
                    getAnonymousApiBaseUrl(), pluginId, version);
            } else {
                url = String.format("%s/packages/%s/download-token",
                    getAnonymousApiBaseUrl(), pluginId);
            }
            ResponseEntity<DownloadTokenApiResponse> resp = restTemplate.exchange(
                url,
                HttpMethod.GET,
                createGetEntity(url),
                DownloadTokenApiResponse.class
            );
            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) return null;
            DownloadTokenApiResponse body = resp.getBody();
            if (body == null || !body.success || body.data == null) return null;
            return body.data.token;
        } catch (Exception e) {
            log.warn("请求下载token失败: pluginId={}, version={}", pluginId, version, e);
            return null;
        }
    }
    
    /**
     * 从 jar 文件中解析配置元数据
     * 仅从 install-workflow.yml 中读取 configClass，不再进行全局扫描
     * 
     * @param jarPath jar 文件路径
     * @return 配置元数据列表
     */
    public java.util.List<com.keqi.gress.common.plugin.FormMetadataParser.FieldMetadata> parseInstallConfigMetadataFromJar(java.nio.file.Path jarPath) {
        try {
            String pluginClassName = parsePluginClassName(jarPath);
            if (StringUtils.isBlank(pluginClassName)) {
                log.debug("plugin.properties 中未找到 plugin.class，返回空安装前配置元数据");
                return java.util.Collections.emptyList();
            }

            try (java.net.URLClassLoader classLoader = new java.net.URLClassLoader(
                    new java.net.URL[]{jarPath.toUri().toURL()},
                    Thread.currentThread().getContextClassLoader())) {
                Class<?> pluginClass = classLoader.loadClass(pluginClassName);
                com.keqi.gress.common.plugin.annotion.PluginSpec pluginSpec =
                        pluginClass.getAnnotation(com.keqi.gress.common.plugin.annotion.PluginSpec.class);
                if (pluginSpec == null || pluginSpec.installInputClass() == com.keqi.gress.common.plugin.annotion.PluginSpec.DefaultInput.class) {
                    return java.util.Collections.emptyList();
                }

                Class<? extends com.keqi.gress.common.plugin.dto.Input> installInputClass = pluginSpec.installInputClass();
                com.keqi.gress.common.plugin.FormMetadataParser.FormMetadata formMetadata =
                        com.keqi.gress.common.plugin.FormMetadataParser.parse(installInputClass);
                if (formMetadata == null || formMetadata.getFields() == null || formMetadata.getFields().isEmpty()) {
                    return java.util.Collections.emptyList();
                }

                log.info("成功解析安装前配置元数据: pluginClass={}, installInputClass={}, fields={}",
                        pluginClassName, installInputClass.getName(), formMetadata.getFields().size());
                return formMetadata.getFields();
            }
        } catch (Exception e) {
            log.error("解析 jar 文件安装前配置元数据失败: jarPath={}", jarPath, e);
            return java.util.Collections.emptyList();
        }
    }

    private String parsePluginClassName(java.nio.file.Path jarPath) {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath.toFile())) {
            java.util.jar.JarEntry entry = jarFile.getJarEntry("plugin.properties");
            if (entry == null) {
                return null;
            }
            java.util.Properties properties = new java.util.Properties();
            try (java.io.InputStream in = jarFile.getInputStream(entry)) {
                properties.load(in);
            }
            return properties.getProperty("plugin.class");
        } catch (Exception e) {
            log.debug("解析 plugin.properties 失败: {}", e.getMessage());
            return null;
        }
    }
    
}
