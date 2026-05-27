<template>
  <div class="stack-deploy-page">
    <PageHeader title="多套部署" subtitle="多套隔离（独立 DB + Redis DB）& 多节点发布（入口 Nginx 负载均衡）">
      <template #actions>
        <n-button :loading="loadingStacks" @click="refreshAll">刷新</n-button>
        <n-button type="primary" @click="openCreateStack">新增 Stack</n-button>
      </template>
    </PageHeader>

    <div class="page-content">
      <div class="layout">
        <n-card class="panel panel--left" title="Stacks">
          <n-data-table
            :columns="stackColumns"
            :data="stacks"
            :loading="loadingStacks"
            :pagination="false"
            :row-key="(row: StackConfig) => row.stackId"
            striped
          />
        </n-card>

        <div class="panel panel--right">
          <n-card class="panel__top" :title="selectedStack ? `Stack：${selectedStack.stackId}` : '选择一个 Stack'">
            <template #header-extra>
              <n-space v-if="selectedStack" :size="8">
                <n-button size="small" @click="openEditStack">编辑</n-button>
                <n-button size="small" type="error" @click="deleteSelectedStack">删除</n-button>
              </n-space>
            </template>

            <div v-if="selectedStack" class="stack-meta">
              <div class="meta">
                <div class="meta__k">DB</div>
                <div class="meta__v">{{ selectedStack.mysqlDatabase }}</div>
              </div>
              <div class="meta">
                <div class="meta__k">Redis DB</div>
                <div class="meta__v">{{ selectedStack.redisDb }}</div>
              </div>
              <div class="meta">
                <div class="meta__k">入口节点</div>
                <div class="meta__v">{{ selectedStack.entryNodeId || '-' }}</div>
              </div>
              <div class="meta">
                <div class="meta__k">域名</div>
                <div class="meta__v">{{ selectedStack.domain || '-' }}</div>
              </div>
              <div class="meta meta--wide">
                <div class="meta__k">runtime</div>
                <div class="meta__v">{{ selectedStack.runtimeBaseDir || '-' }}</div>
              </div>
            </div>

            <n-alert v-else type="info" :show-icon="false">
              先在左侧选择一个 Stack，然后在右侧配置 Target 节点与发起部署。
            </n-alert>
          </n-card>

          <n-card class="panel__mid" title="Targets（stack × node）">
            <template #header-extra>
              <n-space v-if="selectedStack" :size="8">
                <n-button size="small" type="primary" @click="openAddTarget">绑定节点</n-button>
                <n-button size="small" :loading="loadingTargets" @click="loadTargets">刷新</n-button>
              </n-space>
            </template>

            <n-data-table
              :columns="targetColumns"
              :data="targets"
              :loading="loadingTargets"
              :pagination="false"
              :row-key="(row: StackTarget) => row.nodeId"
              striped
            />
          </n-card>

          <n-card class="panel__bottom" title="Deploy">
            <div v-if="selectedStack" class="deploy-bar">
              <n-space :size="12" align="center">
                <n-input
                  v-model:value="deployForm.requestedVersion"
                  placeholder="版本/镜像 tag（可选）"
                  style="width: 240px"
                />
                <n-select v-model:value="deployForm.mode" :options="modeOptions" style="width: 170px" />
                <n-select v-model:value="deployForm.strategy" :options="strategyOptions" style="width: 180px" />
                <n-select v-model:value="deployForm.deployFronted" :options="boolOptions" style="width: 160px" />
                <n-select v-model:value="deployForm.joinNginx" :options="boolOptions2" style="width: 160px" />
              </n-space>

              <div class="deploy-actions">
                <n-button type="primary" :loading="deploying" @click="startDeploy">发起部署</n-button>
                <n-button
                  style="margin-left: 8px"
                  :disabled="!selectedStack?.entryNodeId"
                  :loading="deployingNginx"
                  @click="joinNginxOnly"
                >
                  加入 Nginx
                </n-button>
              </div>
            </div>

            <n-alert v-else type="warning" :show-icon="false">
              需要先选择 Stack 才能发起部署。
            </n-alert>

            <div v-if="activeDeployment" class="deploy-status">
              <div class="deploy-status__left">
                <div class="pill">
                  <span class="pill__k">deploymentId</span>
                  <span class="pill__v">{{ activeDeployment.deploymentId }}</span>
                </div>
                <div class="pill">
                  <span class="pill__k">状态</span>
                  <span class="pill__v">{{ activeDeployment.status || '-' }}</span>
                </div>
                <div class="pill">
                  <span class="pill__k">started</span>
                  <span class="pill__v">{{ formatTime(activeDeployment.startedAt) }}</span>
                </div>
              </div>
              <div class="deploy-status__right">
                <n-button size="small" @click="openLogsDrawer(activeDeployment.deploymentId)">查看日志</n-button>
                <n-button size="small" @click="refreshDeployment">刷新状态</n-button>
              </div>
            </div>
          </n-card>
        </div>
      </div>
    </div>

    <!-- Stack modal -->
    <n-modal v-model:show="showStackModal" preset="card" :title="stackModalTitle" style="width: 860px">
      <n-form ref="stackFormRef" :model="stackForm" label-placement="left" label-width="140">
        <div class="form-grid">
          <n-form-item label="stackId" path="stackId">
            <n-input v-model:value="stackForm.stackId" placeholder="如 a / b" :disabled="stackModalMode === 'edit'" />
          </n-form-item>
          <n-form-item label="名称" path="name">
            <n-input v-model:value="stackForm.name" placeholder="可选" />
          </n-form-item>
          <n-form-item label="启用" path="enabled">
            <n-select v-model:value="stackForm.enabled" :options="enabledOptions" />
          </n-form-item>
          <n-form-item label="MySQL DB" path="mysqlDatabase">
            <n-input v-model:value="stackForm.mysqlDatabase" placeholder="如 gress_a" />
          </n-form-item>
          <n-form-item label="Redis DB" path="redisDb">
            <n-input-number v-model:value="stackForm.redisDb" :min="0" :max="15" />
          </n-form-item>
          <n-form-item label="runtimeBaseDir" path="runtimeBaseDir">
            <n-input v-model:value="stackForm.runtimeBaseDir" placeholder="/home/gress/a/runtime" />
          </n-form-item>
          <n-form-item label="webImage" path="webImage">
            <n-input v-model:value="stackForm.webImage" placeholder="docker.io/keqi123/gress-web:latest" />
          </n-form-item>
          <n-form-item label="frontedImage" path="frontedImage">
            <n-input v-model:value="stackForm.frontedImage" placeholder="docker.io/keqi123/gress-frontend:latest" />
          </n-form-item>
          <n-form-item label="deployFronted" path="deployFronted">
            <n-select v-model:value="stackForm.deployFronted" :options="boolOptions" />
          </n-form-item>
          <n-form-item label="joinNginx" path="joinNginx">
            <n-select v-model:value="stackForm.joinNginx" :options="boolOptions2" />
          </n-form-item>
          <n-form-item label="entryNodeId" path="entryNodeId">
            <n-select
              v-model:value="stackForm.entryNodeId"
              filterable
              clearable
              tag
              placeholder="选择入口节点（可选，空=不更新 Nginx）"
              :options="nodeOptions"
            />
          </n-form-item>
          <n-form-item label="domain" path="domain">
            <n-input v-model:value="stackForm.domain" placeholder="如 gress-a.example.com（可选）" />
          </n-form-item>
          <n-form-item label="webHostPort" path="webHostPort">
            <n-input-number v-model:value="stackForm.webHostPort" :min="1" :max="65535" />
          </n-form-item>
          <n-form-item label="frontedHostPort" path="frontedHostPort">
            <n-input-number v-model:value="stackForm.frontedHostPort" :min="1" :max="65535" />
          </n-form-item>
          <n-form-item class="span-2" label="extraConfig(JSON)" path="extraConfig">
            <n-input
              v-model:value="stackForm.extraConfig"
              type="textarea"
              placeholder='{"MYSQL_HOST":"127.0.0.1","MYSQL_USERNAME":"gress","MYSQL_PASSWORD":"***","REDIS_HOST":"127.0.0.1","REDIS_PORT":"6379","NGINX_CONF_PATH":"/etc/nginx/conf.d/gress-stacks-a.conf"}'
            />
          </n-form-item>
        </div>
      </n-form>

      <template #footer>
        <div class="footer-actions">
          <n-button @click="showStackModal = false">取消</n-button>
          <n-button type="primary" :loading="savingStack" @click="saveStack">保存</n-button>
        </div>
      </template>
    </n-modal>

    <!-- Target modal -->
    <n-modal v-model:show="showTargetModal" preset="card" :title="targetModalTitle" style="width: 720px">
      <n-form :model="targetForm" label-placement="left" label-width="120">
        <n-form-item label="nodeId">
          <n-select
            v-model:value="targetForm.nodeId"
            filterable
            clearable
            tag
            placeholder="搜索并选择 nodeId（也可手动输入）"
            :options="nodeOptions"
          />
        </n-form-item>
        <n-form-item label="enabled">
          <n-select v-model:value="targetForm.enabled" :options="enabledOptions" />
        </n-form-item>
        <n-form-item label="roles">
          <n-input v-model:value="targetForm.roles" placeholder="web,front,lb（逗号分隔）" />
        </n-form-item>
        <n-form-item label="webPort">
          <n-input-number v-model:value="targetForm.webPort" :min="1" :max="65535" />
        </n-form-item>
        <n-form-item label="frontedPort">
          <n-input-number v-model:value="targetForm.frontedPort" :min="1" :max="65535" />
        </n-form-item>
      </n-form>

      <template #footer>
        <div class="footer-actions">
          <n-button @click="showTargetModal = false">取消</n-button>
          <n-button type="primary" :loading="savingTarget" @click="saveTarget">保存</n-button>
        </div>
      </template>
    </n-modal>

    <!-- Logs drawer -->
    <n-drawer v-model:show="showLogs" placement="right" width="640">
      <n-drawer-content title="部署日志">
        <template #footer>
          <div class="drawer-actions">
            <n-button size="small" @click="loadLogs">刷新</n-button>
            <n-button size="small" type="error" @click="stopLogPolling">停止轮询</n-button>
          </div>
        </template>

        <div class="logs-head">
          <div class="logs-head__k">deploymentId</div>
          <div class="logs-head__v">{{ logsDeploymentId }}</div>
        </div>

        <n-card size="small" class="logs-card">
          <div v-if="logs.length === 0" class="logs-empty">暂无日志</div>
          <div v-else class="logs">
            <div v-for="(l, idx) in logs" :key="idx" class="log-line">
              <div class="log-line__meta">
                <span class="tag">{{ l.status }}</span>
                <span class="step">{{ l.step }}</span>
                <span class="node">{{ l.nodeId || '-' }}</span>
                <span class="time">{{ formatTime(l.timestamp) }}</span>
              </div>
              <pre v-if="l.output" class="log-line__out">{{ l.output }}</pre>
            </div>
          </div>
        </n-card>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, onUnmounted, ref } from 'vue'
import { NButton, useDialog, useMessage, type DataTableColumns } from 'naive-ui'
import { stackDeployApi, type StackConfig, type StackTarget, type StackDeployment, type StackDeploymentLog } from '../api/stackDeploy'
import { nodesApi, type NodeInfo } from '../api/nodes'

const message = useMessage()
const dialog = useDialog()

const nodes = ref<NodeInfo[]>([])
const nodeOptions = ref<Array<{ label: string; value: string }>>([])
const nodeById = ref<Record<string, NodeInfo>>({})

const loadingStacks = ref(false)
const stacks = ref<StackConfig[]>([])
const selectedStackId = ref<string | null>(null)
const selectedStack = ref<StackConfig | null>(null)

const loadingTargets = ref(false)
const targets = ref<StackTarget[]>([])

const deploying = ref(false)
const deployingNginx = ref(false)
const activeDeployment = ref<StackDeployment | null>(null)

const showStackModal = ref(false)
const stackModalMode = ref<'create' | 'edit'>('create')
const stackModalTitle = ref('新增 Stack')
const stackFormRef = ref<any>(null)
const savingStack = ref(false)

const showTargetModal = ref(false)
const targetModalTitle = ref('绑定节点')
const savingTarget = ref(false)

const showLogs = ref(false)
const logsDeploymentId = ref('')
const logs = ref<StackDeploymentLog[]>([])
let logsTimer: any = null

const enabledOptions = [
  { label: '启用', value: true },
  { label: '禁用', value: false }
]

const boolOptions = [
  { label: '部署前端：是', value: true },
  { label: '部署前端：否', value: false }
]

const boolOptions2 = [
  { label: '加入 Nginx：是', value: true },
  { label: '加入 Nginx：否', value: false }
]

const modeOptions = [
  { label: 'SINGLE_NODE', value: 'SINGLE_NODE' },
  { label: 'CLUSTER', value: 'CLUSTER' }
]

const strategyOptions = [
  { label: 'ALL_AT_ONCE', value: 'ALL_AT_ONCE' },
  { label: 'ROLLING（预留）', value: 'ROLLING' }
]

const emptyStack = (): StackConfig => ({
  stackId: '',
  name: '',
  enabled: true,
  mysqlDatabase: '',
  redisDb: 0,
  runtimeBaseDir: '',
  webImage: '',
  frontedImage: '',
  versionTag: '',
  deployFronted: true,
  joinNginx: true,
  entryNodeId: '',
  domain: '',
  webHostPort: 8082,
  frontedHostPort: 8080,
  extraConfig: ''
})

const stackForm = ref<StackConfig>(emptyStack())

const targetForm = ref<StackTarget>({
  nodeId: '',
  enabled: true,
  roles: 'web',
  webPort: 8082,
  frontedPort: 8080
})

const deployForm = ref({
  requestedVersion: '',
  mode: 'SINGLE_NODE',
  strategy: 'ALL_AT_ONCE',
  deployFronted: true,
  joinNginx: true
})

const stackColumns: DataTableColumns<StackConfig> = [
  { title: 'stackId', key: 'stackId', width: 90 },
  { title: '名称', key: 'name', width: 150, render: (r) => r.name || '-' },
  { title: 'DB', key: 'mysqlDatabase', width: 160 },
  { title: 'Redis', key: 'redisDb', width: 80 },
  {
    title: '状态',
    key: 'enabled',
    width: 80,
    render: (r) => (r.enabled ? '启用' : '禁用')
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row) =>
      h(
        NButton,
        {
          size: 'small',
          type: row.stackId === selectedStackId.value ? 'primary' : 'default',
          onClick: () => selectStack(row.stackId)
        },
        { default: () => '选择' }
      )
  }
]

const targetColumns: DataTableColumns<StackTarget> = [
  {
    title: '节点',
    key: 'nodeId',
    width: 260,
    render: (r) => {
      const n = nodeById.value[r.nodeId]
      if (!n) return r.nodeId
      const host = n.type === 'ssh' ? n.config?.host : ''
      const hint = [n.name || '', n.type || '', host || ''].filter(Boolean).join(' · ')
      return h('div', { style: 'display:flex; flex-direction:column; gap:2px;' }, [
        h('div', { style: 'font-weight:600; color: rgba(0,0,0,.88);' }, r.nodeId),
        h('div', { style: 'font-size:12px; color: rgba(0,0,0,.55);' }, hint || '-')
      ])
    }
  },
  { title: 'roles', key: 'roles', width: 140, render: (r) => r.roles || 'web' },
  { title: 'webPort', key: 'webPort', width: 100, render: (r) => r.webPort ?? '-' },
  { title: 'front', key: 'frontedPort', width: 100, render: (r) => r.frontedPort ?? '-' },
  { title: 'health', key: 'healthStatus', width: 110, render: (r) => r.healthStatus || 'UNKNOWN' },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render(row) {
      return h('div', { style: 'display:flex; gap:8px;' }, [
        h(
          NButton,
          { size: 'small', onClick: () => openEditTarget(row) },
          { default: () => '编辑' }
        ),
        h(
          NButton,
          { size: 'small', type: 'error', onClick: () => deleteTarget(row) },
          { default: () => '移除' }
        )
      ])
    }
  }
]

async function refreshAll() {
  await loadStacks()
  await loadNodes()
  if (selectedStackId.value) {
    await selectStack(selectedStackId.value)
  }
}

async function loadNodes() {
  try {
    nodes.value = await nodesApi.list()
    nodeById.value = Object.fromEntries(nodes.value.map((n) => [n.nodeId, n]))
    nodeOptions.value = nodes.value.map((n) => {
      const host = n.type === 'ssh' ? n.config?.host : ''
      const label = [n.nodeId, n.name ? `(${n.name})` : '', n.type ? `[${n.type}]` : '', host ? `@${host}` : '']
        .filter(Boolean)
        .join(' ')
      return { label, value: n.nodeId }
    })
  } catch (e: any) {
    // 不阻断页面：节点管理可用时再提升体验
    nodeById.value = {}
    nodeOptions.value = []
  }
}

async function loadStacks() {
  loadingStacks.value = true
  try {
    stacks.value = await stackDeployApi.listStacks()
  } finally {
    loadingStacks.value = false
  }
}

async function selectStack(stackId: string) {
  selectedStackId.value = stackId
  selectedStack.value = await stackDeployApi.getStack(stackId)
  // 部署默认值：优先用 stack 配置，减少手动配置错误
  deployForm.value.deployFronted = selectedStack.value.deployFronted ?? true
  deployForm.value.joinNginx = selectedStack.value.joinNginx ?? true
  await loadTargets()
}

async function loadTargets() {
  if (!selectedStackId.value) return
  loadingTargets.value = true
  try {
    targets.value = await stackDeployApi.listTargets(selectedStackId.value)
  } finally {
    loadingTargets.value = false
  }
}

function openCreateStack() {
  stackModalMode.value = 'create'
  stackModalTitle.value = '新增 Stack'
  stackForm.value = emptyStack()
  showStackModal.value = true
}

function openEditStack() {
  if (!selectedStack.value) return
  stackModalMode.value = 'edit'
  stackModalTitle.value = '编辑 Stack'
  stackForm.value = { ...selectedStack.value }
  showStackModal.value = true
}

async function saveStack() {
  savingStack.value = true
  try {
    if (stackModalMode.value === 'create') {
      await stackDeployApi.createStack(stackForm.value)
      message.success('创建成功')
    } else {
      await stackDeployApi.updateStack(stackForm.value.stackId, stackForm.value)
      message.success('更新成功')
    }
    showStackModal.value = false
    await refreshAll()
  } finally {
    savingStack.value = false
  }
}

function deleteSelectedStack() {
  if (!selectedStackId.value) return
  const sid = selectedStackId.value
  dialog.warning({
    title: '确认删除',
    content: `确定删除 stack: ${sid} ?（不会自动清理远程资源）`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await stackDeployApi.deleteStack(sid)
      message.success('已删除')
      selectedStackId.value = null
      selectedStack.value = null
      targets.value = []
      await loadStacks()
    }
  })
}

function openAddTarget() {
  if (!selectedStackId.value) return
  targetModalTitle.value = '绑定节点'
  targetForm.value = { nodeId: '', enabled: true, roles: 'web', webPort: 8082, frontedPort: 8080 }
  showTargetModal.value = true
}

function openEditTarget(row: StackTarget) {
  targetModalTitle.value = '编辑目标'
  targetForm.value = { ...row }
  showTargetModal.value = true
}

async function saveTarget() {
  if (!selectedStackId.value) return
  savingTarget.value = true
  try {
    await stackDeployApi.upsertTarget(selectedStackId.value, targetForm.value)
    message.success('保存成功')
    showTargetModal.value = false
    await loadTargets()
  } finally {
    savingTarget.value = false
  }
}

function deleteTarget(row: StackTarget) {
  if (!selectedStackId.value) return
  dialog.warning({
    title: '移除节点',
    content: `确定从 stack 移除节点 ${row.nodeId} ?`,
    positiveText: '移除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await stackDeployApi.deleteTarget(selectedStackId.value!, row.nodeId)
      message.success('已移除')
      await loadTargets()
    }
  })
}

async function startDeploy() {
  if (!selectedStackId.value) return
  deploying.value = true
  try {
    const dep = await stackDeployApi.createDeployment(selectedStackId.value, { ...deployForm.value })
    activeDeployment.value = dep
    message.success('部署已创建并开始执行')
    openLogsDrawer(dep.deploymentId)
  } finally {
    deploying.value = false
  }
}

async function joinNginxOnly() {
  if (!selectedStackId.value) return
  if (!selectedStack.value?.entryNodeId) {
    message.warning('请先在 Stack 里配置 entryNodeId（入口节点）')
    return
  }
  deployingNginx.value = true
  try {
    const dep = await stackDeployApi.createDeployment(selectedStackId.value, {
      mode: 'NGINX_ONLY',
      strategy: 'ALL_AT_ONCE',
      deployFronted: false,
      joinNginx: true
    })
    activeDeployment.value = dep
    message.success('已触发 Nginx 更新任务')
    openLogsDrawer(dep.deploymentId)
  } finally {
    deployingNginx.value = false
  }
}

async function refreshDeployment() {
  if (!activeDeployment.value) return
  activeDeployment.value = await stackDeployApi.getDeployment(activeDeployment.value.deploymentId)
}

function openLogsDrawer(deploymentId: string) {
  logsDeploymentId.value = deploymentId
  showLogs.value = true
  loadLogs()
  startLogPolling()
}

async function loadLogs() {
  if (!logsDeploymentId.value) return
  logs.value = await stackDeployApi.listDeploymentLogs(logsDeploymentId.value, 300)
}

function startLogPolling() {
  stopLogPolling()
  logsTimer = setInterval(async () => {
    if (!showLogs.value) return
    await loadLogs()
    // 同步刷新部署状态
    if (activeDeployment.value?.deploymentId === logsDeploymentId.value) {
      await refreshDeployment()
    }
  }, 2500)
}

function stopLogPolling() {
  if (logsTimer) {
    clearInterval(logsTimer)
    logsTimer = null
  }
}

function formatTime(ts?: number) {
  if (!ts) return '-'
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(async () => {
  await loadNodes()
  await loadStacks()
})

onUnmounted(() => {
  stopLogPolling()
})
</script>

<style scoped lang="scss">
.stack-deploy-page {
  .page-content {
    padding: 12px;
  }
}

.layout {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 12px;
  align-items: start;
}

.panel--left {
  position: sticky;
  top: 12px;
}

.panel--right {
  display: grid;
  grid-template-rows: auto auto auto;
  gap: 12px;
}

.stack-meta {
  margin-top: 6px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
}

.meta {
  padding: 10px 10px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 10px;
  background: rgba(250, 250, 252, 0.9);
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 8px;
  align-items: center;
}

.meta--wide {
  grid-column: 1 / -1;
}

.meta__k {
  font-size: 12px;
  letter-spacing: 0.06em;
  color: rgba(0, 0, 0, 0.55);
  text-transform: uppercase;
}

.meta__v {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.88);
  word-break: break-all;
}

.deploy-bar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.deploy-actions {
  display: flex;
  justify-content: flex-end;
}

.deploy-status {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(0, 0, 0, 0.12);
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.deploy-status__left {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.8);
}

.pill__k {
  font-size: 11px;
  color: rgba(0, 0, 0, 0.55);
}

.pill__v {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.9);
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.logs-head {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(248, 248, 252, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.logs-head__k {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.logs-head__v {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.88);
  word-break: break-all;
}

.logs-card {
  background: radial-gradient(1200px 600px at 30% -10%, rgba(0, 136, 255, 0.10), transparent 60%),
    radial-gradient(900px 520px at 90% 10%, rgba(255, 193, 7, 0.10), transparent 55%),
    rgba(255, 255, 255, 0.9);
}

.logs-empty {
  padding: 18px 0;
  color: rgba(0, 0, 0, 0.55);
  text-align: center;
}

.logs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.log-line {
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.85);
}

.log-line__meta {
  display: grid;
  grid-template-columns: 84px 120px 1fr 160px;
  gap: 8px;
  padding: 10px 10px;
  align-items: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(250, 250, 252, 0.95);
}

.tag {
  display: inline-flex;
  justify-content: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  border: 1px solid rgba(0, 0, 0, 0.10);
  background: rgba(0, 0, 0, 0.03);
}

.step {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.8);
  font-weight: 600;
}

.node,
.time {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.log-line__out {
  margin: 0;
  padding: 10px 10px;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
  color: rgba(0, 0, 0, 0.85);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
}

.span-2 {
  grid-column: 1 / -1;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
  .panel--left {
    position: static;
  }
}
</style>

