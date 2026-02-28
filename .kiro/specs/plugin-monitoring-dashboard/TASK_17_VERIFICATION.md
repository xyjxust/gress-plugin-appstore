# Task 17 Verification: 在插件主入口注册监控页面

## 完成时间
2026-02-06

## 实现内容

### 17.1 在 index.ts 中注册 PluginMonitorDashboard 组件 ✅

**状态**: 已完成（组件已在之前的任务中注册）

**实现位置**: `gress-plugin-appstore/frontend/src/index.ts`

**实现内容**:
1. ✅ 导入组件: `import PluginMonitorDashboard from './views/PluginMonitorDashboard.vue'`
2. ✅ 注册到 components 对象:
```typescript
components: {
  ApplicationManagement,
  OperationLog,
  MiddlewareManagement,
  NodeManagement,
  PluginMonitorDashboard  // 已注册
}
```

### 17.2 创建或更新 plugin-ui.yml 配置 ✅

**状态**: 已完成

**实现位置**: `gress-plugin-appstore/src/main/resources/plugin-ui.yml`

**实现内容**:

#### 1. 添加监控页面菜单配置
```yaml
# 插件监控菜单
- id: plugin-monitor
  title: 插件监控
  icon: Activity
  path: /plugins/appstore/monitor
  component: PluginMonitorDashboard
  order: 54

  # API 前缀（自动关联所有匹配的 API）
  apiPrefix: /api/plugins/appstore/monitor

  operations:
    - VIEW    # 查看监控数据

  buttons:
    - id: refresh
      label: 刷新
      # 约定：GET /api/plugins/appstore/monitor/status
    - id: view-detail
      label: 查看详情
      # 约定：GET /api/plugins/appstore/monitor/status/{pluginId}
    - id: view-history
      label: 查看历史
      # 约定：GET /api/plugins/appstore/monitor/history/{pluginId}
```

#### 2. 更新角色权限配置
为所有预定义角色添加了插件监控权限：

- **viewer** (应用查看者):
  - `plugin-monitor:VIEW` - 查看监控数据

- **user** (应用普通用户):
  - `plugin-monitor:VIEW` - 查看监控数据
  - `plugin-monitor.*` - 所有监控按钮权限

- **admin** (应用管理员):
  - `plugin-monitor:VIEW` - 查看监控数据
  - `plugin-monitor.*` - 所有监控按钮权限

## 配置说明

### 菜单配置
- **菜单ID**: `plugin-monitor`
- **菜单标题**: 插件监控
- **图标**: Activity (活动图标，适合监控功能)
- **路由路径**: `/plugins/appstore/monitor`
- **组件名称**: `PluginMonitorDashboard`
- **菜单顺序**: 54 (在节点管理之后)

### API 路由映射
- **API 前缀**: `/api/plugins/appstore/monitor`
- **获取所有插件状态**: `GET /api/plugins/appstore/monitor/status`
- **获取单个插件详情**: `GET /api/plugins/appstore/monitor/status/{pluginId}`
- **获取插件历史数据**: `GET /api/plugins/appstore/monitor/history/{pluginId}`
- **获取监控概览**: `GET /api/plugins/appstore/monitor/overview`

### 权限配置
- **VIEW 操作**: 查看监控页面和数据
- **refresh 按钮**: 刷新监控数据
- **view-detail 按钮**: 查看插件详细信息
- **view-history 按钮**: 查看插件历史数据

## 前端构建

已成功构建前端代码:
```bash
npm run build
✓ 657 modules transformed.
dist/appstore-frontend.umd.js  1,880.84 kB │ gzip: 371.31 kB
✓ built in 2.34s
```

## 验证步骤

### 1. 组件注册验证
- [x] PluginMonitorDashboard 已导入到 index.ts
- [x] 组件已添加到 components 注册表
- [x] 前端代码已成功构建

### 2. 配置文件验证
- [x] plugin-ui.yml 中添加了 plugin-monitor 菜单项
- [x] 配置了正确的路由路径和组件名称
- [x] 配置了 API 前缀和按钮权限
- [x] 更新了所有角色的权限配置

### 3. 集成验证（需要运行时测试）
- [ ] 启动应用后，菜单中显示"插件监控"选项
- [ ] 点击菜单能正确导航到监控页面
- [ ] 监控页面能正常加载和显示
- [ ] API 调用能正确路由到后端控制器

## 需求覆盖

本任务完成了以下需求的前端入口配置：
- ✅ 需求 1: 插件运行状态监控 - 提供了访问入口
- ✅ 需求 2: 插件加载状态检测 - 提供了访问入口
- ✅ 需求 3: 插件内存使用监控 - 提供了访问入口
- ✅ 需求 4: 插件详细信息查看 - 配置了详情按钮
- ✅ 需求 5: 监控数据刷新控制 - 配置了刷新按钮
- ✅ 需求 7: 监控数据持久化 - 配置了历史查看按钮

## 注意事项

1. **图标选择**: 使用了 `Activity` 图标，这是一个常用的监控/活动图标。如果需要更改，可以在 plugin-ui.yml 中修改 `icon` 字段。

2. **菜单顺序**: 设置为 54，在节点管理（53）之后。如果需要调整顺序，可以修改 `order` 字段。

3. **权限控制**: 所有用户角色（viewer、user、admin）都有查看监控的权限，这是合理的，因为监控是只读操作。

4. **API 路径**: 使用了 `/api/plugins/appstore/monitor` 作为前缀，与后端控制器的 `@RequestMapping` 路径一致。

## 下一步

任务 17 已完成。建议进行以下操作：

1. **运行时测试**: 启动应用，验证菜单和页面是否正常工作
2. **权限测试**: 测试不同角色的用户是否能正确访问监控页面
3. **继续下一个任务**: Task 18 - 检查点 - 前端功能完整性验证
