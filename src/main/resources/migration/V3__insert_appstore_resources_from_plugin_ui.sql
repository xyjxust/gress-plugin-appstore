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
    `icon`,
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
    'storefront-outline',
    50,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('VIEW'),
        'menuId', 'appstore-menu',
        'parent', 'plugins',
        'visible', true,
        'type', 'LAYOUT'
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
    `icon`,
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
    'settings-outline',
    51,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('VIEW', 'MANAGE'),
        'menuId', 'appstore-admin-menu',
        'parent', 'settings',
        'visible', true,
        'type', 'LAYOUT'
    ),
    '应用商店管理页面'
);

-- 菜单资源3: 应用管理菜单
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `path`,
    `icon`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'menu:appstore:applications',
    '应用管理菜单',
    'MENU',
    'appstore',
    '/plugins/appstore/applications',
    'apps-outline',
    52,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('VIEW', 'MANAGE'),
        'menuId', 'application-management-menu',
        'parent', 'plugins',
        'visible', true,
        'type', 'LAYOUT'
    ),
    '应用管理页面'
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
    100,
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
    101,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('READ')
    ),
    '获取插件类型列表'
);

-- API资源3: 应用管理接口
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
    'api:appstore:applications',
    '应用管理接口',
    'API',
    'appstore',
    '/api/v2/plugins/appstore/applications/**',
    '*',
    102,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('READ', 'WRITE')
    ),
    '应用管理相关API接口'
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
    200,
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
    300,
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
    301,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'operations', JSON_ARRAY('EXECUTE')
    ),
    '卸载插件功能'
);

-- ============================================================================
-- 路由资源 (作为 MENU 类型，因为表结构不支持 ROUTE 类型)
-- ============================================================================

-- 路由资源1: AppStore (应用商店浏览页面)
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `path`,
    `icon`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'route:appstore:AppStore',
    '应用商店路由',
    'MENU',
    'appstore',
    '/plugins/appstore/plugin-manager/plugins',
    'storefront-outline',
    400,
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
    `icon`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'route:appstore:AppStoreAdmin',
    '应用商店管理路由',
    'MENU',
    'appstore',
    '/plugins/appstore/plugin-manager/plugin-store',
    'settings-outline',
    401,
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

-- 路由资源3: ApplicationManagement (应用管理页面)
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `plugin_id`,
    `path`,
    `icon`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'route:appstore:ApplicationManagement',
    '应用管理路由',
    'ROUTE',
    'appstore',
    '/plugins/appstore/applications',
    'apps-outline',
    402,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'component', 'ApplicationManagement',
        'name', 'ApplicationManagement',
        'type', 'LAYOUT',
        'meta', JSON_OBJECT(
            'title', '应用管理',
            'icon', 'apps-outline',
            'requiresAuth', true
        ),
        'requiredPermissions', JSON_ARRAY('menu:appstore:applications:VIEW')
    ),
    '应用管理页面路由'
);

