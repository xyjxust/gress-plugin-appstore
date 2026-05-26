## 02. 数据模型（建议）

### 1) StackConfig（多套配置）

字段建议：
- `stackId`：如 `a` / `b`
- `name`：展示名
- `enabled`
- `mysqlDatabase`：如 `gress_a`
- `redisDb`：如 `0/1`
- `runtimeBaseDir`：如 `/home/gress/{stackId}/runtime`
- `deployFronted`（默认 true）
- `joinNginx`（默认 true，可在部署时覆盖）
- `entryNodeId`：入口节点（默认第一个）
- `domain` 或 `hostPorts`：
  - 多节点推荐 domain
  - 单节点多套可用 hostPorts
- `images`：
  - `webImage` / `frontedImage`
  - `versionTag`

### 2) StackTarget（stack × node）

- `stackId`
- `nodeId`
- `roles`：`web/front/lb`
- `enabled`
- `lastDeployedVersion`
- `healthStatus`：`UNKNOWN/UP/DOWN`

### 3) Deployment（部署任务）

- `deploymentId`
- `stackId`
- `requestedVersion`
- `mode`：`SINGLE_NODE` / `CLUSTER`
- `strategy`：`ALL_AT_ONCE` / `ROLLING`
- `deployFronted` / `joinNginx`（本次任务级别开关）
- `status`：`PENDING/RUNNING/SUCCESS/FAILED/CANCELLED`
- `startedAt/endedAt`

### 4) DeploymentStep / DeploymentLog（可选但推荐）

用于审计与排障：
- `deploymentId`
- `nodeId`
- `step`：`UPLOAD/COMPOSE_UP/HEALTHCHECK/JOIN_NGINX/...`
- `status`
- `output`（截断）

### 状态机简述

单节点：
- render → upload → compose up → healthcheck → (front?) → (join nginx?) → done

多节点：
- per node rolling：
  - upload → compose up → healthcheck → join nginx（可选）→ next node

