-- App Store 插件资源 INSERT 语句
-- 根据 plugin-ui.yml 配置生成
-- 包含：菜单资源、API资源、数据资源、功能资源、路由资源

-- ============================================================================
-- 菜单资源 (MENU)
-- ============================================================================

-- 菜单资源1: 应用商店主菜单
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `path`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'menu:appstore:main',
    '应用商店主菜单',
    'MENU',
    'appstore',
    '/plugins/appstore/plugin-manager/plugins',
    1,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('VIEW')
    ),
    '应用商店浏览页面'
);

-- 菜单资源2: 应用商店管理菜单
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `path`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'menu:appstore:admin',
    '应用商店管理菜单',
    'MENU',
    'appstore',
    '/plugins/appstore/plugin-manager/plugin-store',
    2,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('VIEW', 'MANAGE')
    ),
    '应用商店管理页面'
);

-- ============================================================================
-- API资源 (API)
-- ============================================================================

-- API资源1: 应用商店插件接口
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `path`,
    `method`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'api:appstore:plugins',
    '应用商店插件接口',
    'API',
    'appstore',
    '/api/v2/plugins/store/**',
    '*',
    10,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('READ', 'WRITE')
    ),
    '应用商店插件管理相关API接口'
);

-- API资源2: 应用商店类型接口
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `path`,
    `method`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'api:appstore:types',
    '应用商店类型接口',
    'API',
    'appstore',
    '/api/v2/plugins/store/types',
    'GET',
    11,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('READ')
    ),
    '获取插件类型列表'
);

-- ============================================================================
-- 数据资源 (DATA)
-- ============================================================================

-- 数据资源1: 应用商店插件信息
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'data:appstore:plugin-info',
    '应用商店插件信息',
    'DATA',
    'appstore',
    20,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('READ', 'WRITE')
    ),
    '应用商店插件信息数据'
);

-- ============================================================================
-- 功能资源 (FEATURE)
-- ============================================================================

-- 功能资源1: 插件安装功能
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'feature:appstore:install',
    '插件安装功能',
    'FEATURE',
    'appstore',
    30,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('EXECUTE')
    ),
    '安装插件功能'
);

-- 功能资源2: 插件卸载功能
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'feature:appstore:uninstall',
    '插件卸载功能',
    'FEATURE',
    'appstore',
    31,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('EXECUTE')
    ),
    '卸载插件功能'
);

-- ============================================================================
-- 路由资源 (ROUTE)
-- ============================================================================

-- 路由资源1: AppStore (应用商店浏览页面)
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `path`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'route:appstore:AppStore',
    '应用商店路由',
    'ROUTE',
    'appstore',
    '/plugins/appstore/plugin-manager/plugins',
    100,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'component', 'AppStorePage',
        'name', 'AppStore',
        'type', 'LAYOUT',
        'meta', JSON_OBJECT(
            'title', '应用商店',
            'icon', 'storefront-outline',
            'requiresAuth', true
        ),
        'requiredPermissions', JSON_ARRAY('menu:appstore:main:VIEW')
    ),
    '应用商店浏览页面路由'
);

-- 路由资源2: AppStoreAdmin (应用商店管理页面)
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `path`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'route:appstore:AppStoreAdmin',
    '应用商店管理路由',
    'ROUTE',
    'appstore',
    '/plugins/appstore/plugin-manager/plugin-store',
    101,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'component', 'AppStoreAdminPage',
        'name', 'AppStoreAdmin',
        'type', 'LAYOUT',
        'meta', JSON_OBJECT(
            'title', '应用商店管理',
            'icon', 'settings-outline',
            'requiresAuth', true,
            'requiresAdmin', true
        ),
        'requiredPermissions', JSON_ARRAY('menu:appstore:admin:VIEW')
    ),
    '应用商店管理页面路由'
);

