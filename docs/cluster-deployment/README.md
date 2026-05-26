## 集群/多套部署（AppStore 插件内置部署服务）

本目录沉淀“多套（stack）× 多节点（node）”部署能力的设计与实现约束。

### 目标

- **同一套部署服务**支持：
  - **单节点**：一台服务器部署多套（A/B/...），端口/域名隔离。
  - **多节点**：多台服务器部署多套（每套可多副本），通过 Nginx 负载均衡。
- 多套隔离策略：
  - **独立 MySQL 数据库**：`gress_a` / `gress_b` / ...
  - **Redis DB 隔离**：`0` / `1` / ...
- 多节点入口策略（第一版）：
  - 选择“入口节点”（默认第一个节点），在其上维护 Nginx upstream，把所有目标节点加入 upstream。
- 部署行为可配置：
  - 是否部署前端
  - 是否加入 Nginx upstream（灰度/验证后再加入）

### 文档索引

- [01-architecture.md](./01-architecture.md)：整体架构与职责边界（不造 k8s）
- [02-data-model.md](./02-data-model.md)：数据模型（Stack/Target/Deployment 及状态机）
- [03-workflows.md](./03-workflows.md)：部署流程（单节点/多节点）与幂等策略
- [04-nginx.md](./04-nginx.md)：Nginx upstream 生成规则、粘滞与 SSE 注意事项
- [05-mvp-scope.md](./05-mvp-scope.md)：MVP vs Pro 的分期范围

