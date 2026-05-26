## 01. 架构与边界

### 解决的问题

- 在少量节点（几台～几十台）的场景下，提供“可视化、一键、多套、多节点”的发布能力。
- 目标是 **PaaS-lite（轻量发布平台）**，不是 Kubernetes 替代品。

### 关键对象

- **Node（节点）**：由现有节点管理维护（local/ssh/docker-api）。
- **Stack（套/环境）**：一套完整 gress-web + gress-fronted 实例（独立 DB + Redis DB）。
- **Deployment（部署任务）**：把某个 stack 发布到一组节点，并可选加入入口 Nginx upstream。

### 组件职责

- **AppStore 节点管理（已存在）**
  - 节点资产、连接信息、连通性测试。
- **AppStore 部署服务（新增）**
  - 渲染 stack 级别的配置与 compose
  - 分发/执行（优先 SSH，docker-api 可后续支持）
  - 健康检查 + 是否加入 upstream 的控制
  - 部署记录/日志（用于回溯）
- **入口 Nginx（第一版单点）**
  - 作为 stack 的统一入口（域名/端口），upstream 指向各 web 节点
  - 后续可演进为双入口/云 LB

### 生产约束（必须认清）

- 入口节点（第一版）存在 **SPOF**，需要提供“入口节点可切换/迁移”的能力作为兜底。
- 多节点下的 SSE/长连接必须使用 **粘滞会话**（Cookie 或 LB affinity）。

