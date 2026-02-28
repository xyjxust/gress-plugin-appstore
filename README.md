# Gress Plugin AppStore

Gress 应用商店插件 - 提供插件管理、安装、监控等完整的插件生命周期管理功能。

## 📋 目录

- [功能特性](#功能特性)
- [技术架构](#技术架构)
- [快速开始](#快速开始)
- [开发指南](#开发指南)
- [API 文档](#api-文档)
- [部署说明](#部署说明)

## 🎯 功能特性

### 1. 插件管理

#### 应用列表
- 浏览所有可用插件
- 查看插件详细信息（名称、版本、描述、作者等）
- 插件分类和标签筛选
- 搜索和排序功能

#### 插件安装
- 一键安装插件
- 依赖链自动解析和下载
- 安装进度实时显示
- 安装失败自动回滚
- Docker Compose 自动部署支持

#### 插件卸载
- 安全卸载插件
- 依赖检查（防止卸载被依赖的插件）
- 数据清理选项
- 卸载日志记录

#### 插件升级
- 版本更新检测
- 升级通知提醒
- 一键升级到最新版本
- 升级历史记录

### 2. 插件监控

#### 实时监控
- 插件运行状态监控
- 内存使用情况统计
- 类加载器信息查看
- 性能指标采集

#### 历史数据
- 监控数据历史记录
- 趋势分析图表（基于 ECharts）
- 数据导出功能
- 自定义时间范围查询

#### 状态变更
- 插件状态变更日志
- 启动/停止/安装/卸载记录
- 操作人员追踪
- 异常事件告警

### 3. 远程应用商店

#### 应用市场
- 连接远程应用商店
- 浏览在线插件库
- 插件评分和评论
- 下载统计

#### 版本管理
- 多版本支持
- 版本兼容性检查
- 版本回退功能
- 版本发布历史

### 4. 配置管理

#### 插件配置
- 可视化配置界面
- 配置项动态表单生成
- 配置验证和校验
- 配置导入/导出

#### 权限管理
- 插件权限控制
- 表权限管理
- API 权限配置
- 操作审计日志

### 5. 中间件管理

#### 中间件安装
- 上传中间件插件包安装
- 从应用商店安装中间件
- 多节点部署支持（本地/SSH/Docker API）
- Docker Compose 自动编排
- 安装配置动态注入
- 实时安装日志（SSE 推送）

#### 中间件监控
- 中间件运行状态监控
- 健康检查（HTTP/TCP）
- 服务连接信息查看
- 容器状态监控
- 资源使用统计

#### 共享服务管理
- 共享服务注册（MinIO、ETCD、Redis 等）
- 服务引用计数管理
- 服务消费者追踪
- 服务配置管理
- 连接信息格式化展示

#### 中间件卸载
- 安全卸载检查
- 引用计数验证
- 容器自动清理
- 数据保留选项
- 卸载日志记录

### 6. 节点管理

#### 节点注册
- 本地节点自动注册
- SSH 节点手动添加
- Docker API 节点配置
- 节点连接测试
- 节点信息编辑

#### 执行环境
- 本地执行环境
- SSH 远程执行
- Docker API 执行
- 执行日志收集
- 错误处理和重试

### 7. 依赖管理

#### 依赖解析
- 自动解析插件依赖链
- 中间件依赖自动安装
- 依赖冲突检测
- 依赖版本兼容性检查
- 依赖关系可视化

#### 依赖下载
- 批量下载依赖插件
- 断点续传支持
- 下载进度显示
- 下载失败重试

## 🏗️ 技术架构

### 后端技术栈

- **框架**: PF4J (插件框架)
- **语言**: Java 21
- **构建工具**: Maven
- **依赖注入**: 自定义 @Inject 注解
- **数据库**: MyBatis Plus
- **日志**: SLF4J + Hutool Log
- **模板引擎**: Apache Velocity
- **SSH 支持**: JSch
- **YAML 解析**: Jackson YAML

### 前端技术栈

- **框架**: Vue 3 + TypeScript
- **构建工具**: Vite
- **UI 组件**: Naive UI
- **图表库**: ECharts + Vue-ECharts
- **样式**: SCSS
- **状态管理**: Pinia (通过 plugin-bridge)
- **路由**: Vue Router (通过 plugin-bridge)
- **HTTP 客户端**: Axios (通过 shared-utils)

### 核心依赖包

- `@keqi.gress/plugin-bridge`: 插件桥接通信
- `@keqi.gress/plugin-ui`: UI 组件和样式
- `@keqi.gress/shared-utils`: 共享工具函数
- `@keqi.gress/plugin-dev-server`: 开发服务器

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- Node.js 18+
- npm 9+

### 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd gress-plugin-appstore

# 构建（包含前端）
mvn clean package

# 构建产物
target/gress-plugin-appstore-1.0-SNAPSHOT.jar
```

### 安装插件

1. 将构建好的 JAR 包复制到 Gress 插件目录
2. 重启 Gress 应用
3. 在 Gress 管理界面启用插件

## 💻 开发指南

### 后端开发

#### 项目结构

```
src/main/java/com/keqi/gress/plugin/appstore/
├── AppStorePlugin.java          # 插件入口
├── config/                      # 配置类
├── contoller/                   # REST API 控制器
├── dao/                         # 数据访问层
├── domain/                      # 领域模型
│   ├── entity/                  # 实体类
│   └── dto/                     # 数据传输对象
├── dto/                         # DTO 定义
├── listener/                    # 事件监听器
├── service/                     # 业务逻辑层
│   ├── install/                 # 安装相关服务
│   └── monitor/                 # 监控相关服务
└── util/                        # 工具类
```

#### 核心服务

**应用管理**
- `AppStoreApiService`: 应用商店 API 服务
- `PluginInstallService`: 插件安装服务
- `PluginMonitorService`: 插件监控服务

**中间件管理**
- `MiddlewareManagementService`: 中间件管理服务
- `MiddlewareDependencyInstaller`: 中间件依赖安装器
- `MiddlewareInstallSsePublisher`: 安装日志 SSE 推送
- `ConnectionInfoFormatter`: 连接信息格式化

**节点管理**
- `NodeManagementService`: 节点管理服务
- `SshExecutionEnvironment`: SSH 执行环境
- `DockerApiExecutionEnvironment`: Docker API 执行环境

**依赖管理**
- `PluginDependencyChainResolver`: 依赖链解析
- `PluginDependencyChainDownloadService`: 依赖下载

#### 开发流程

1. 修改 Java 代码
2. 运行 `mvn clean package`
3. 重启 Gress 应用测试

### 前端开发

#### 项目结构

```
frontend/
├── src/
│   ├── api/                     # API 接口
│   ├── components/              # 组件
│   ├── views/                   # 页面
│   ├── stores/                  # 状态管理
│   ├── router/                  # 路由配置
│   ├── types/                   # 类型定义
│   └── App.vue                  # 根组件
├── index.html                   # HTML 模板
├── vite.config.ts              # Vite 配置
├── gress-dev.config.js         # 开发服务器配置
└── package.json                # 依赖配置
```

#### 开发流程

##### 1. 安装依赖

```bash
cd frontend
npm install
```

##### 2. 启动开发服务器

```bash
npm run dev
```

开发服务器会启动在 `http://localhost:3001`，提供：
- ⚡️ 热更新 (HMR)
- 🔄 后端 API 自动代理
- 🛣️ 完整路由支持

详见 [DEV_SERVER_GUIDE.md](frontend/DEV_SERVER_GUIDE.md)

##### 3. 构建生产版本

```bash
npm run build
```

构建产物会输出到 `frontend/dist/`，Maven 会自动将其打包到 JAR 中。

#### 开发服务器配置

```javascript
// frontend/gress-dev.config.js
export default {
  pluginName: 'appstore',
  backendUrl: 'http://localhost:8080',
  port: 3001,
  apiPrefix: '/api/plugin/appstore'
}
```

#### 路由访问

所有前端路由都可以直接访问：

```
http://localhost:3001/              # 首页
http://localhost:3001/applications  # 应用列表
http://localhost:3001/installed     # 已安装应用
http://localhost:3001/middlewares   # 中间件管理
http://localhost:3001/nodes         # 节点管理
http://localhost:3001/monitor       # 监控页面
```

## 📡 API 文档

### 应用管理 API

#### 获取应用列表
```http
GET /api/plugin/appstore/applications
```

#### 安装应用
```http
POST /api/plugin/appstore/install
Content-Type: application/json

{
  "packageId": "plugin-id",
  "version": "1.0.0"
}
```

#### 卸载应用
```http
POST /api/plugin/appstore/uninstall
Content-Type: application/json

{
  "pluginId": "plugin-id"
}
```

### 监控 API

#### 获取插件监控状态
```http
GET /api/plugin/appstore/monitor/status/{pluginId}
```

#### 获取监控历史数据
```http
GET /api/plugin/appstore/monitor/history/{pluginId}?startTime=xxx&endTime=xxx
```

### 中间件管理 API

#### 获取中间件列表
```http
GET /api/plugin/appstore/middlewares
```

#### 上传安装中间件
```http
POST /api/plugin/appstore/middlewares/upload
Content-Type: multipart/form-data

file: <middleware-package.jar>
operatorName: admin
```

#### 从应用商店安装中间件
```http
POST /api/plugin/appstore/middlewares/install-from-store
Content-Type: application/json

{
  "pluginId": "milvus-installer",
  "version": "1.0.0",
  "targetNodeId": "node-001",
  "executionType": "ssh",
  "config": {
    "port": 19530,
    "password": "xxx"
  }
}
```

#### 卸载中间件
```http
POST /api/plugin/appstore/middlewares/{middlewareId}/uninstall
Content-Type: application/json

{
  "operatorName": "admin"
}
```

#### 健康检查
```http
GET /api/plugin/appstore/middlewares/{middlewareId}/health
```

#### 获取连接信息
```http
GET /api/plugin/appstore/middlewares/{middlewareId}/connection-info
```

### 节点管理 API

#### 获取节点列表
```http
GET /api/plugin/appstore/nodes
```

#### 添加节点
```http
POST /api/plugin/appstore/nodes
Content-Type: application/json

{
  "nodeId": "node-001",
  "nodeName": "生产服务器",
  "nodeType": "ssh",
  "host": "192.168.1.100",
  "port": 22,
  "username": "admin",
  "password": "xxx"
}
```

#### 测试节点连接
```http
POST /api/plugin/appstore/nodes/{nodeId}/test
```

### 依赖管理 API

#### 解析依赖链
```http
POST /api/plugin/appstore/dependencies/resolve
Content-Type: application/json

{
  "pluginId": "plugin-id",
  "version": "1.0.0"
}
```

## 📦 部署说明

### 构建部署包

```bash
# 完整构建（包含前端）
mvn clean package

# 跳过前端构建（开发时）
mvn clean package -Dskip.npm

# 跳过测试
mvn clean package -DskipTests
```

### 部署到 Gress

1. 将 `target/gress-plugin-appstore-1.0-SNAPSHOT.jar` 复制到 Gress 插件目录
2. 重启 Gress 应用
3. 在管理界面启用插件

### Docker Compose 支持

插件和中间件支持自动部署 Docker Compose 服务：

1. 在插件/中间件包中包含 `docker-compose.yml`
2. 安装时自动检测并部署
3. 支持环境变量动态注入
4. 支持多节点部署（本地/SSH/Docker API）
5. 卸载时自动清理容器

### 中间件安装流程

1. **上传或选择中间件包**
   - 从本地上传 JAR 包
   - 从应用商店选择中间件

2. **选择目标节点**
   - 本地节点（默认）
   - SSH 远程节点
   - Docker API 节点

3. **配置中间件参数**
   - 端口配置
   - 密码设置
   - 资源限制
   - 其他自定义配置

4. **执行安装**
   - 解压中间件包
   - 提取 docker-compose.yml
   - 注入配置变量
   - 执行 docker-compose up
   - 实时推送安装日志（SSE）

5. **注册服务**
   - 注册到中间件服务表
   - 记录连接信息
   - 初始化引用计数

6. **健康检查**
   - 验证服务可用性
   - 更新服务状态

## 🔧 配置说明

### 插件配置

```yaml
# plugin-ui.yml
appstore:
  remoteUrl: https://appstore.example.com
  autoUpdate: true
  checkInterval: 3600
```

### 数据库表

插件会自动创建以下数据表：

**监控相关**
- `appstore_plugin_monitor_snapshot`: 监控快照
- `appstore_plugin_monitor_cache`: 监控缓存
- `appstore_plugin_state_change_log`: 状态变更日志

**中间件相关**
- `appstore_middleware_info`: 中间件信息表
- `appstore_middleware_service`: 共享服务表
- `appstore_middleware_install_log`: 安装日志表

**节点相关**
- `appstore_node_info`: 节点信息表
- `appstore_node_execution_log`: 执行日志表

## 🐛 故障排查

### 常见问题

#### 1. 前端构建失败

**问题**: `npm install` 或 `npm run build` 失败

**解决**:
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
```

#### 2. 插件启动失败

**问题**: 插件无法启动或加载

**解决**:
- 检查 Gress 日志
- 确认 JDK 版本 >= 21
- 检查依赖插件是否已安装

#### 3. 中间件安装失败

**问题**: 中间件安装失败或容器启动失败

**解决**:
- 检查 Docker 是否已安装并运行
- 检查端口是否被占用
- 查看安装日志（SSE 实时日志）
- 检查 docker-compose.yml 配置
- 验证节点连接（SSH/Docker API）

#### 4. API 代理失败

**问题**: 开发时 API 请求失败

**解决**:
- 确认后端服务已启动
- 检查 `gress-dev.config.js` 中的 `backendUrl`
- 查看浏览器控制台和开发服务器日志

## 📝 开发规范

### 代码风格

- Java: 遵循 Google Java Style Guide
- TypeScript: 遵循 Airbnb TypeScript Style Guide
- 使用 Lombok 简化 Java 代码
- 使用 TypeScript 严格模式

### 提交规范

```
feat: 新功能
fix: 修复 bug
docs: 文档更新
style: 代码格式调整
refactor: 重构
test: 测试相关
chore: 构建/工具相关
```

## 📄 许可证

MIT License

## 👥 贡献者

- Gress Team

## 📞 联系方式

- 项目地址: [GitHub Repository]
- 问题反馈: [Issue Tracker]
- 文档: [Documentation]

## 🔗 相关链接

- [Gress 主项目](../gress)
- [插件开发文档](../gress/docs)
- [插件开发包](../gress/gress-plugin-packages)
- [开发服务器文档](../gress/gress-plugin-packages/plugin-dev-server)
