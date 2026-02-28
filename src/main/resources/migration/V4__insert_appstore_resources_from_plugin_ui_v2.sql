-- App Store 插件资源 INSERT 语句
-- 根据 plugin-ui.yml 配置生成（符合新规范）
-- 包含：菜单资源、按钮资源、操作、权限、角色

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
    'menu:appstore:applications',
    '应用管理',
    'MENU',
    'appstore',
    '/plugins/appstore/applications',
    'apps-outline',
    50,
    'ACTIVE',
    0,
    JSON_OBJECT(
        'component', 'ApplicationManagement',
        'hideInMenu', false,
        'apiPrefix', '/api/v2/plugins/appstore/applications',
        'type', 'layout',
        'path', '/plugins/appstore/applications',
        'icon', 'apps-outline',
        'sortOrder', 50
    ),
    '应用管理菜单'
);

-- ============================================================================
-- 2. 按钮资源 (BUTTON)
-- ============================================================================

-- 使用临时变量存储菜单ID，避免子查询错误
SET @menu_applications_id = (SELECT id FROM sys_resource WHERE resource_code = 'menu:appstore:applications');

-- 按钮1: 上传安装
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
    'button:appstore:applications.upload',
    '上传安装',
    'BUTTON',
    @menu_applications_id,
    'appstore',
    '/api/v2/plugins/appstore/applications/upload',
    'POST',
    1,
    'ACTIVE',
    0,
    NULL,
    '上传安装按钮'
);

-- 按钮2: 启用
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
    'button:appstore:applications.enable',
    '启用',
    'BUTTON',
    @menu_applications_id,
    'appstore',
    '/api/v2/plugins/appstore/applications/{id}/enable',
    'POST',
    2,
    'ACTIVE',
    0,
    NULL,
    '启用按钮'
);

-- 按钮3: 禁用
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
    'button:appstore:applications.disable',
    '禁用',
    'BUTTON',
    @menu_applications_id,
    'appstore',
    '/api/v2/plugins/appstore/applications/{id}/disable',
    'POST',
    3,
    'ACTIVE',
    0,
    NULL,
    '禁用按钮'
);

-- 按钮4: 升级
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
    'button:appstore:applications.upgrade',
    '升级',
    'BUTTON',
    @menu_applications_id,
    'appstore',
    '/api/v2/plugins/appstore/applications/{id}/upgrade',
    'POST',
    4,
    'ACTIVE',
    0,
    NULL,
    '升级按钮'
);

-- 按钮5: 卸载
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
    'button:appstore:applications.uninstall',
    '卸载',
    'BUTTON',
    @menu_applications_id,
    'appstore',
    '/api/v2/plugins/appstore/applications/{id}',
    'DELETE',
    5,
    'ACTIVE',
    0,
    NULL,
    '卸载按钮'
);

-- 按钮6: 从应用商店安装
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
    'button:appstore:applications.remote-install',
    '从应用商店安装',
    'BUTTON',
    @menu_applications_id,
    'appstore',
    '/api/v2/plugins/appstore/applications/remote/install',
    'POST',
    6,
    'ACTIVE',
    0,
    NULL,
    '从应用商店安装按钮'
);

-- 按钮7: 刷新远程列表
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
    'button:appstore:applications.refresh-remote',
    '刷新远程列表',
    'BUTTON',
    @menu_applications_id,
    'appstore',
    '/api/v2/plugins/appstore/applications/remote',
    'GET',
    7,
    'ACTIVE',
    0,
    NULL,
    '刷新远程列表按钮'
);

-- ============================================================================
-- 3. 资源操作 (sys_resource_operation)
-- ============================================================================

-- 菜单操作: VIEW
INSERT INTO `sys_resource_operation` (
    `resource_id`,
    `operation_code`,
    `operation_name`,
    `sort_order`,
    `status`,
    `remark`
) VALUES (
    @menu_applications_id,
    'VIEW',
    '查看',
    1,
    'ACTIVE',
    '查看菜单和访问页面'
);

-- 菜单操作: MANAGE
INSERT INTO `sys_resource_operation` (
    `resource_id`,
    `operation_code`,
    `operation_name`,
    `sort_order`,
    `status`,
    `remark`
) VALUES (
    @menu_applications_id,
    'MANAGE',
    '管理',
    2,
    'ACTIVE',
    '管理应用（增删改）'
);

-- 按钮操作: EXECUTE（为所有按钮创建 EXECUTE 操作）
INSERT INTO `sys_resource_operation` (
    `resource_id`,
    `operation_code`,
    `operation_name`,
    `sort_order`,
    `status`,
    `remark`
)
SELECT 
    id,
    'EXECUTE',
    '执行',
    1,
    'ACTIVE',
    '执行按钮操作'
FROM sys_resource
WHERE resource_type = 'BUTTON' 
  AND plugin_id = 'appstore'
  AND resource_code LIKE 'button:appstore:applications.%';

-- ============================================================================
-- 4. 权限 (sys_permission)
-- ============================================================================

-- 为所有资源和操作自动生成权限
INSERT INTO `sys_permission` (
    `permission_code`,
    `permission_name`,
    `resource_id`,
    `operation_id`,
    `status`,
    `remark`
)
SELECT 
    CONCAT(r.resource_code, ':', o.operation_code) AS permission_code,
    CONCAT(r.resource_name, '-', o.operation_name) AS permission_name,
    r.id AS resource_id,
    o.id AS operation_id,
    'ACTIVE' AS status,
    CONCAT('权限：', r.resource_name, ' ', o.operation_name) AS remark
FROM sys_resource r
JOIN sys_resource_operation o ON r.id = o.resource_id
WHERE r.plugin_id = 'appstore'
  AND r.resource_code IN (
    'menu:appstore:applications',
    'button:appstore:applications.upload',
    'button:appstore:applications.enable',
    'button:appstore:applications.disable',
    'button:appstore:applications.upgrade',
    'button:appstore:applications.uninstall',
    'button:appstore:applications.remote-install',
    'button:appstore:applications.refresh-remote'
  );

-- ============================================================================
-- 5. 角色 (sys_role)
-- ============================================================================

-- 角色1: 应用查看者
INSERT INTO `sys_role` (
    `role_code`,
    `role_name`,
    `description`,
    `role_type`,
    `status`,
    `remark`
) VALUES (
    'appstore-viewer',
    '应用查看者',
    '只能查看应用列表和详情，不能进行任何操作',
    'CUSTOM',
    'ACTIVE',
    '应用商店查看者角色'
);

-- 角色2: 应用普通用户
INSERT INTO `sys_role` (
    `role_code`,
    `role_name`,
    `description`,
    `role_type`,
    `status`,
    `remark`
) VALUES (
    'appstore-user',
    '应用普通用户',
    '可以查看应用、启用/禁用应用',
    'CUSTOM',
    'ACTIVE',
    '应用商店普通用户角色'
);

-- 角色3: 应用管理员
INSERT INTO `sys_role` (
    `role_code`,
    `role_name`,
    `description`,
    `role_type`,
    `status`,
    `remark`
) VALUES (
    'appstore-admin',
    '应用管理员',
    '拥有应用管理的完整权限',
    'CUSTOM',
    'ACTIVE',
    '应用商店管理员角色'
);

-- ============================================================================
-- 6. 角色权限关联 (sys_role_permission)
-- ============================================================================

-- 设置角色ID变量
SET @role_viewer_id = (SELECT id FROM sys_role WHERE role_code = 'appstore-viewer');
SET @role_user_id = (SELECT id FROM sys_role WHERE role_code = 'appstore-user');
SET @role_admin_id = (SELECT id FROM sys_role WHERE role_code = 'appstore-admin');

-- 角色1: 应用查看者 - 只有 VIEW 权限
INSERT INTO `sys_role_permission` (
    `role_id`,
    `permission_id`,
    `status`,
    `remark`
)
SELECT 
    @role_viewer_id AS role_id,
    p.id AS permission_id,
    'ACTIVE' AS status,
    '查看者权限' AS remark
FROM sys_permission p
JOIN sys_resource r ON p.resource_id = r.id
JOIN sys_resource_operation o ON p.operation_id = o.id
WHERE r.plugin_id = 'appstore'
  AND r.resource_code = 'menu:appstore:applications'
  AND o.operation_code = 'VIEW';

-- 角色2: 应用普通用户 - VIEW + enable/disable 按钮权限
INSERT INTO `sys_role_permission` (
    `role_id`,
    `permission_id`,
    `status`,
    `remark`
)
SELECT 
    @role_user_id AS role_id,
    p.id AS permission_id,
    'ACTIVE' AS status,
    '普通用户权限' AS remark
FROM sys_permission p
JOIN sys_resource r ON p.resource_id = r.id
JOIN sys_resource_operation o ON p.operation_id = o.id
WHERE r.plugin_id = 'appstore'
  AND (
    (r.resource_code = 'menu:appstore:applications' AND o.operation_code = 'VIEW')
    OR (r.resource_code = 'button:appstore:applications.enable' AND o.operation_code = 'EXECUTE')
    OR (r.resource_code = 'button:appstore:applications.disable' AND o.operation_code = 'EXECUTE')
  );

-- 角色3: 应用管理员 - 所有权限（VIEW, MANAGE + 所有按钮）
INSERT INTO `sys_role_permission` (
    `role_id`,
    `permission_id`,
    `status`,
    `remark`
)
SELECT 
    @role_admin_id AS role_id,
    p.id AS permission_id,
    'ACTIVE' AS status,
    '管理员权限' AS remark
FROM sys_permission p
JOIN sys_resource r ON p.resource_id = r.id
WHERE r.plugin_id = 'appstore'
  AND r.resource_code IN (
    'menu:appstore:applications',
    'button:appstore:applications.upload',
    'button:appstore:applications.enable',
    'button:appstore:applications.disable',
    'button:appstore:applications.upgrade',
    'button:appstore:applications.uninstall',
    'button:appstore:applications.remote-install',
    'button:appstore:applications.refresh-remote'
  );

