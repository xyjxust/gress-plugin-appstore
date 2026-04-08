-- App Store 插件路由资源 INSERT 语句
-- 根据 plugin-ui.yml 中的 routes 配置生成

-- 路由1: AppStore (应用商店浏览页面)
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
    1,
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

-- 路由2: AppStoreAdmin (应用商店管理页面)
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
    2,
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

