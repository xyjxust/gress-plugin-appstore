## 04. Nginx upstream 生成规则

### upstream 命名

- `upstream gress_stack_{stackId}_web { ... }`

### server 列表来源

- 来自 `StackTarget` 中 role 包含 `web` 且 `enabled=true` 的节点
- 每个节点提供 `host:port`（或容器/内网地址）

### 粘滞会话（建议默认打开）

对于包含 SSE / WebSocket 的场景：
- 推荐使用 cookie 粘滞（例如 `hash $cookie_ROUTEID consistent;` 或第三方 sticky 模块）
- 或者让上层 LB（云）做 session affinity

### 变更方式（第一版）

- 入口节点上采用“全量渲染 + 原子替换”：
  - 渲染 `conf.d/gress-stacks.conf`
  - `nginx -t`
  - `nginx -s reload`

