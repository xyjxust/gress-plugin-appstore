## 05. MVP 范围（本次先落地）

### MVP（先做这些，能用）

- **数据模型/表**：
  - StackConfig
  - StackTarget
  - Deployment（含基础日志）
- **接口**：
  - StackConfig CRUD
  - StackTarget 绑定节点、设置 role/webPort、启停
  - 发起部署（选择 stackId、版本、deployFronted/joinNginx 开关）
- **执行环境**：
  - 先支持 `ssh`（复用现有 `SshExecutionEnvironment`）
- **Nginx**：
  - 入口节点全量渲染 upstream + reload（可选开关）

### Pro（后续演进）

- Rolling 更新、并发控制、失败回滚
- docker-api 直连执行
- 入口节点 HA / 多入口
- 更完善的审计、日志流（SSE/WS）

