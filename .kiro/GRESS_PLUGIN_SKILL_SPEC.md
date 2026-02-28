# 通用 Skill 文档：根据 Skill 生成 Gress 插件

> 基于 `gress-plugin-appstore/frontend` 及同仓库后端与配置整理，适用于任意 Gress 插件的「需求补充 → 功能确认 → 代码生成 → 语法检查」一次性生成。

---

## 1. 元信息与技能概述

生成时用占位符替换：`<plugin_id>`、`<plugin_name>`、`<one_line_goal>` 等。

```yaml
skill_id: "<plugin_id>"                    # 如 todo-list、approval-flow，与插件 id 一致
title: "<plugin_name>"                    # 中文显示名，如「待办列表」「审批流」
one_line_goal: "<one_line_goal>"          # 一句话：为 Gress 提供什么能力
tech_stack:
  backend: "Java 21, Gress plugin API, @Service/@Inject, Result<T>, 包名 com.keqi.gress.plugin.<plugin_id>"
  frontend: "Vue 3, TypeScript, naive-ui, @keqi.gress/plugin-bridge, @keqi.gress/shared-utils, UMD(IIFE)"
scope: full                               # full | backend_only | frontend_only
```

**前端固定约束（与 appstore frontend 一致）**：

- 构建：Vite 5，入口 `src/index.ts`，库模式、`name: '__GRESS_PLUGIN__'`、`formats: ['iife']`，产物 `dist/<plugin_id>-frontend.umd.js`。
- 依赖：`vue`、`naive-ui`、`echarts` 为 external；必须包含 `@keqi.gress/plugin-bridge`、`@keqi.gress/plugin-ui`、`@keqi.gress/shared-utils`。
- 脚本：`dev` 使用 `gress-dev`，`build` 使用 `vite build`。
- 开发配置：`gress-dev.config.js` 中 `pluginName`、`apiPrefix` 与插件 id 一致，`backendUrl` 指向宿主后端。

---

## 2. 术语表

每个 Skill 文档需维护本插件的术语表，格式如下；生成需求与功能描述时统一使用这些术语。

```markdown
## 术语表
- **<概念A>**: 定义（与业务域一致）
- **<概念B>**: 定义
- **System**: 指当前插件所服务的 Gress 系统/宿主
- **API_BASE**: 前端请求基础路径，即 /plugins/<plugin_id>
```

---

## 3. 需求补充规则与产出格式

**输入**：用户自然语言描述，或元信息中的 `one_line_goal` + 用户补充。

**产出**：结构化需求列表，每条包含：

- **需求 ID**：`REQ-1`, `REQ-2`, ...
- **用户故事**：作为 [角色]，我想要 [能力]，以便 [价值]。
- **验收标准**：使用 WHEN/THEN/IF，可引用术语表；每条编号（1. 2. 3. …）便于与功能确认、检查项追溯。

示例：

```markdown
### 需求 REQ-1: xxx
**用户故事:** 作为 [角色]，我想要 [能力]，以便 [价值]。

#### 验收标准
1. WHEN [条件] THEN THE System SHALL [行为]
2. ...
```

若用户描述过简，可根据术语表和 Gress 常见能力（列表、详情、增删改、配置、权限）补全，并注明「已补全」。

---

## 4. 功能确认清单格式

功能确认阶段必须产出以下四类清单，且与需求 ID 可追溯。

### 4.1 API 清单

| 方法 | 路径 | 说明 | 需求 |
|------|------|------|------|
| GET/POST/PUT/DELETE | `/plugins/<plugin_id>/<resource>[/:id][/子路径]` | 简要说明 | REQ-x |

- 路径为**前端视角**：不含宿主 baseURL，与前端 `API_BASE` + 各 api 模块内路径一致。
- 后端实际挂载由宿主决定（如 `/api/v2/plugins/<plugin_id>/...` 或 `/api/plugins/<plugin_id>/...`），需与 `plugin-ui.yml` 中 `apiPrefix`、`buttons[].path` 一致。

### 4.2 前端页面/菜单清单

| 菜单 id | title | path | component | order | 需求 |
|---------|--------|------|-----------|-------|------|
| xxx | 中文标题 | /plugins/<plugin_id>/xxx | XxxView | 50 | REQ-x |

- `path` 与 `plugin-ui.yml` 的 `menus[].path` 一致。
- `component` 与 `plugin-ui.yml` 的 `menus[].component` 及 `src/index.ts` 中 `components` 的 key 一致（Vue 组件名）。

### 4.3 数据模型清单

| 名称 | 类型 | 主要字段/用途 | 需求 |
|------|------|----------------|------|
| XxxDTO / XxxRequest | 后端 DTO | 字段简要说明 | REQ-x |
| Xxx, PageResult\<Xxx\> | 前端 types | 与后端对应 | REQ-x |

### 4.4 配置与权限

- `plugin.yml`：如需扩展配置，列出 key 与含义。
- `plugin-ui.yml`：每个菜单的 `operations`（如 VIEW、MANAGE）、`buttons`（id、label、约定 path 或自定义 `path: METHOD:/full/path`）。

---

## 5. 代码生成规范与产物清单

以下结构与 `gress-plugin-appstore/frontend` 及同仓库后端对齐，生成时按「产物清单」逐项生成。

### 5.1 前端目录与文件（通用）

```
frontend/
├── package.json          # name: @gress/plugin-<plugin_id>-frontend，main/module/types 指向 dist 产物
├── vite.config.ts        # lib.entry=src/index.ts, name='__GRESS_PLUGIN__', fileName=()=>'<plugin_id>-frontend.umd.js'
├── gress-dev.config.js   # pluginName, backendUrl, apiPrefix: '/api/plugin/<plugin_id>' 或与宿主约定一致
├── env.d.ts              # /// <reference types="vite/client" /> + __PLUGIN_NAME__/__API_PREFIX__/__BACKEND_URL__ + *.vue
├── src/
│   ├── index.ts          # 工厂函数 default(bridge, properties?) => PluginManifest；id/name/version/description/author/icon；permissions；components={组件名: 组件}; lifecycle.install 注册 naive 组件
│   ├── api/
│   │   ├── http.ts       # export { http } from '@keqi.gress/shared-utils'
│   │   └── <resource>.ts # API_BASE = '/plugins/<plugin_id>'；方法对应 API 清单，使用 http.get/post/put/delete
│   ├── types/
│   │   └── <resource>.ts # 与后端 DTO 对应的 interface/type，含 PageResult<T>、请求体类型
│   ├── views/
│   │   └── <ViewName>.vue # 每个菜单对应一个，组件名与 plugin-ui.yml 的 component 一致
│   └── components/      # 可选：仅当有复用时生成
└── dist/
    └── <plugin_id>-frontend.umd.js   # 构建产物，与 vite 的 fileName 及后端 @PluginSpec(jsPath) 一致
```

### 5.2 前端约定（与 appstore 一致）

- **入口**：`src/index.ts` 唯一默认导出为工厂函数 `(bridge, properties?) => PluginManifest<Config>`；不导出实例。
- **Config**：定义 `XxxConfig` 接口与 `defaultConfig`，合并 `properties` 后写入 `manifest.config.default`。
- **manifest**：`id`/`name`/`version`/`description`/`author`/`icon` 与 `plugin.yml` / `plugin-ui.yml` 一致；`components` 为「组件名 → Vue 组件」；`permissions` 至少包含 `PluginPermission.NETWORK_ACCESS`、`ROUTER_*`、`COMPONENT_REGISTER`、`UI_MENU` 等所需项；`loadStrategy: 'lazy'`；`extensions.routes` 可为空，由后端 yml 管理路由。
- **lifecycle.install**：从 `bridge.ui.components` 注册 naive-ui 组件到 `bridge.app`，组件列表参考 appstore（NButton、NCard、NDataTable 等）。
- **API 模块**：`API_BASE = '/plugins/<plugin_id>'`；方法返回 `Promise<T>`，不包一层 ApiResponse；错误由调用方 try/catch；FormData 上传若 bridge 不支持，可用原生 `fetch` + `/api/${API_BASE}/...`。
- **类型**：`types/<resource>.ts` 与后端 DTO 对齐（字段名、分页结构 `PageResult<T>`）。

### 5.3 后端与配置（简要，保证与前端对齐）

- 后端：`com.keqi.gress.plugin.<plugin_id>` 下 Controller（`@RequestMapping` 与 API 清单路径一致）、Service、DTO；`plugin.yml` 中 `plugin.id`；`plugin-ui.yml` 中 `plugin.id/name/version`、`menus`（path、component、apiPrefix、buttons、operations）。
- **一致点**：`plugin-ui.yml` 的 `menus[].component` = `index.ts` 的 `components` key；`menus[].path` 与路由一致；`apiPrefix` / `buttons[].path` 与前端请求路径一致；后端 `@PluginSpec(jsPath = "js/<plugin_id>-frontend.umd.js")` 与前端构建产物名一致。

### 5.4 产物清单（生成管线检查用）

| 类别 | 路径 | 说明 |
|------|------|------|
| 前端入口 | frontend/src/index.ts | 工厂函数、manifest、components、lifecycle |
| 前端 API | frontend/src/api/http.ts, \<resource\>.ts | API_BASE、方法与 API 清单一致 |
| 前端类型 | frontend/src/types/\<resource\>.ts | 与后端 DTO 对应 |
| 前端视图 | frontend/src/views/\<ViewName\>.vue | 与菜单 component 一一对应 |
| 前端配置 | frontend/package.json, vite.config.ts, gress-dev.config.js, env.d.ts | name、fileName、pluginName、apiPrefix |
| 后端 | src/main/java/.../contoller, service, dto, plugin.yml, plugin-ui.yml | 与功能确认清单一致 |
| 构建产物 | frontend/dist/\<plugin_id>-frontend.umd.js | 与 jsPath 一致 |

---

## 6. 语法与规范检查项

生成完成后，以下项均需通过（可脚本化或人工核对）。

### 6.1 需求与功能一致性

- [ ] 每个 REQ 的验收标准至少被 API 清单或菜单清单中的某一项覆盖。
- [ ] API 清单中每条接口在后端 Controller 中存在且方法、路径一致。
- [ ] 每个菜单在 `plugin-ui.yml` 和 `frontend/src/index.ts` 的 `components` 中存在，且 path/component 一致。

### 6.2 前端

- [ ] `package.json` 的 `name` 为 `@gress/plugin-<plugin_id>-frontend`，`scripts.dev` 为 `gress-dev`，`scripts.build` 为 `vite build`。
- [ ] `vite.config.ts` 的 `build.lib.entry` 为 `./src/index.ts`，`name` 为 `__GRESS_PLUGIN__`，`formats` 为 `['iife']`，`fileName()` 返回 `'<plugin_id>-frontend.umd.js'`；external 包含 `vue`、`naive-ui`、`echarts`。
- [ ] `gress-dev.config.js` 的 `pluginName`、`apiPrefix` 与插件 id 及宿主路由约定一致。
- [ ] `src/index.ts` 默认导出工厂函数；返回对象包含 `id`、`name`、`version`、`components`、`lifecycle.install`；`components` 的 key 与 `plugin-ui.yml` 的 `menus[].component` 一致。
- [ ] `src/api/http.ts` 仅 re-export `@keqi.gress/shared-utils` 的 `http`。
- [ ] `src/api/<resource>.ts` 的 `API_BASE` 为 `'/plugins/<plugin_id>'`，各方法路径与 API 清单一致，使用 `http.get/post/put/delete`。
- [ ] `src/types/*.ts` 与后端 DTO 对齐（含 `PageResult<T>` 等）。
- [ ] 无 TypeScript/Vue 语法错误；构建可成功生成 `dist/<plugin_id>-frontend.umd.js`。

### 6.3 后端与配置

- [ ] Java 无语法错误；包名为 `com.keqi.gress.plugin.<plugin_id>`；Controller 使用 `@Service`、`@RestController`、`Result<T>`；Service 使用 `@Inject`。
- [ ] `plugin.yml` 的 `plugin.id` 与前端及 `plugin-ui.yml` 一致。
- [ ] `plugin-ui.yml` 的 `menus[].path`、`apiPrefix`、`buttons[].path` 与 API 清单及前端请求路径一致。
- [ ] 后端 `@PluginSpec(jsPath = "js/<plugin_id>-frontend.umd.js")` 与前端构建产物文件名一致。

---

## 7. 占位符速查

生成时替换以下占位符：

| 占位符 | 含义 | 示例 |
|--------|------|------|
| `<plugin_id>` | 插件唯一 id，小写短横线 | todo-list, appstore |
| `<plugin_name>` | 插件中文名 | 待办列表、应用商店 |
| `<PluginId>` | 首字母大写的 id（类名/变量用） | TodoList, Appstore |
| `<resource>` | 资源名，与 API 路径一致 | application, nodes |
| `<ViewName>` | Vue 页面组件名，与 plugin-ui component 一致 | ApplicationManagement, NodeManagement |
| `<one_line_goal>` | 一句话能力描述 | 为 Gress 提供待办增删改查与完成状态管理 |

---

## 8. 参考：appstore frontend 结构

本规范基于以下实际结构整理：

- **frontend/package.json**：`@gress/plugin-appstore-frontend`，`main`/`module`/`types` 指向 dist，`dev`/`build` 脚本，peer 与依赖（vue、naive-ui、plugin-bridge、plugin-ui、shared-utils、echarts、vue-echarts）。
- **frontend/vite.config.ts**：lib.entry=`src/index.ts`，name=`__GRESS_PLUGIN__`，formats=`['iife']`，fileName=`appstore-frontend.umd.js`，external vue/naive-ui/echarts。
- **frontend/gress-dev.config.js**：pluginName、backendUrl、apiPrefix、port。
- **frontend/src/index.ts**：工厂函数返回 PluginManifest，components 注册各 view，lifecycle.install 注册 naive 组件。
- **frontend/src/api/**：http.ts 复用 shared-utils；各 resource.ts 使用 `API_BASE = '/plugins/appstore'`，方法对应后端接口。
- **frontend/src/types/**：与后端 DTO 对应的 interface，含 PageResult\<T\>。
- **frontend/src/views/**：每个菜单一个 Vue 组件，名称与 plugin-ui.yml 的 component 一致。
