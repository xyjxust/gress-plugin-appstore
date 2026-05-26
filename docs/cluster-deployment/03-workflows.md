## 03. 部署流程与幂等

### 单节点多套（同机部署 A/B）

- **前置**：节点已录入（ssh/local），并具备 docker/compose
- **渲染 stack 配置**：
  - `application.yml` 注入：端口、数据库名、redis db、plugin/runtime 目录
  - `docker-compose.yml` 注入：容器名、端口映射、挂载目录
- **执行（幂等）**：
  - 上传（覆盖）到 `{runtimeBaseDir}`
  - `docker compose up -d`
  - 健康检查 `GET /actuator/health`（或自定义 health）
  - 可选：前端部署 / Nginx 配置

### 多节点同套（多副本）

- **入口节点策略（第一版）**
  - 所有 web 节点都加入入口节点的 upstream
  - `joinNginx=false` 时只部署，不加 upstream（用于灰度）
- **更新策略（第一版：ALL_AT_ONCE；第二版：ROLLING）**
  - ALL_AT_ONCE：并发部署所有节点 → 全部健康 → 一次性写 upstream
  - ROLLING：逐台部署 → 每台健康后加入 upstream

### 幂等原则

- 同一 stackId 的 runtime 目录固定，反复部署等价于“覆盖配置 + compose up”
- upstream 生成采用“全量渲染覆盖”，避免增量 patch 漏配

