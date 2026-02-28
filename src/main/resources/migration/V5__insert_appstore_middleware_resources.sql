-- App Store 插件：中间件管理资源 INSERT 语句
-- 根据 plugin-ui.yml 新增 middlewares 菜单生成

-- ============================================================================
-- 1. 菜单资源 (MENU)
-- ============================================================================

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
    'menu:appstore:middlewares',
    '中间件管理',
    'MENU',
    'appstore',
    '/plugins/appstore/middlewares',
    'construct',
    52,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'component', 'MiddlewareManagement',
        'hideInMenu', false,
        'apiPrefix', '/api/v2/plugins/appstore/middlewares',
        'type', 'layout',
        'path', '/plugins/appstore/middlewares',
        'icon', 'construct',
        'sortOrder', 52
    ),
    '中间件管理菜单'
);

-- ============================================================================
-- 2. 按钮资源 (BUTTON)
-- ============================================================================

SET @menu_middlewares_id = (SELECT id FROM sys_resource WHERE resource_code = 'menu:appstore:middlewares');

-- 上传安装
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `parent_id`,
    `plugin_id`,
    `path`,
    `method`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'button:appstore:middlewares.upload',
    '上传安装',
    'BUTTON',
    @menu_middlewares_id,
    'appstore',
    '/api/v2/plugins/appstore/middlewares/upload',
    'POST',
    1,
    'ACTIVE',
    0,
    NULL,
    '中间件上传安装按钮'
);

-- 健康检查
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `parent_id`,
    `plugin_id`,
    `path`,
    `method`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'button:appstore:middlewares.health',
    '健康检查',
    'BUTTON',
    @menu_middlewares_id,
    'appstore',
    '/api/v2/plugins/appstore/middlewares/{middlewareId}/health',
    'GET',
    2,
    'ACTIVE',
    0,
    NULL,
    '中间件健康检查按钮'
);

-- 卸载
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `parent_id`,
    `plugin_id`,
    `path`,
    `method`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'button:appstore:middlewares.uninstall',
    '卸载',
    'BUTTON',
    @menu_middlewares_id,
    'appstore',
    '/api/v2/plugins/appstore/middlewares/{middlewareId}/uninstall',
    'POST',
    3,
    'ACTIVE',
    0,
    NULL,
    '中间件卸载按钮'
);

-- ============================================================================
-- 3. 节点管理菜单资源 (MENU) + 按钮资源 (BUTTON)
--    根据 plugin-ui.yml 新增 nodes 菜单生成
-- ============================================================================

-- 菜单资源：节点管理
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
    'menu:appstore:nodes',
    '节点管理',
    'MENU',
    'appstore',
    '/plugins/appstore/nodes',
    'server-outline',
    53,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'component', 'NodeManagement',
        'hideInMenu', false,
        'apiPrefix', '/api/v2/plugins/appstore/nodes',
        'type', 'layout',
        'path', '/plugins/appstore/nodes',
        'icon', 'server-outline',
        'sortOrder', 53
    ),
    '节点管理菜单'
);

SET @menu_nodes_id = (SELECT id FROM sys_resource WHERE resource_code = 'menu:appstore:nodes');

-- 新增节点（创建/更新）
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `parent_id`,
    `plugin_id`,
    `path`,
    `method`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'button:appstore:nodes.create',
    '新增节点',
    'BUTTON',
    @menu_nodes_id,
    'appstore',
    '/api/v2/plugins/appstore/nodes',
    'POST',
    1,
    'ACTIVE',
    0,
    NULL,
    '节点新增/保存按钮'
);

-- 编辑节点（与新增同一保存接口）
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `parent_id`,
    `plugin_id`,
    `path`,
    `method`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'button:appstore:nodes.edit',
    '编辑节点',
    'BUTTON',
    @menu_nodes_id,
    'appstore',
    '/api/v2/plugins/appstore/nodes',
    'POST',
    2,
    'ACTIVE',
    0,
    NULL,
    '节点编辑/保存按钮'
);

-- 删除节点
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `parent_id`,
    `plugin_id`,
    `path`,
    `method`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'button:appstore:nodes.delete',
    '删除节点',
    'BUTTON',
    @menu_nodes_id,
    'appstore',
    '/api/v2/plugins/appstore/nodes/{nodeId}',
    'DELETE',
    3,
    'ACTIVE',
    0,
    NULL,
    '节点删除按钮'
);

-- 测试连接
INSERT INTO `sys_resource` (
    `resource_code`,
    `resource_name`,
    `resource_type`,
    `parent_id`,
    `plugin_id`,
    `path`,
    `method`,
    `sort_order`,
    `status`,
    `is_public`,
    `metadata`,
    `remark`
) VALUES (
    'button:appstore:nodes.test',
    '测试连接',
    'BUTTON',
    @menu_nodes_id,
    'appstore',
    '/api/v2/plugins/appstore/nodes/{nodeId}/test',
    'POST',
    4,
    'ACTIVE',
    0,
    NULL,
    '节点测试连接按钮'
);
