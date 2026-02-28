<template>
  <div class="node-management-page">
    <PageHeader title="节点管理" subtitle="管理用于远程部署的节点（local/ssh/docker-api）">
      <template #actions>
        <n-button type="primary" @click="openCreate">
          新增节点
        </n-button>
        <n-button :loading="loading" @click="loadNodes">刷新</n-button>
      </template>
    </PageHeader>

    <div class="page-content">
      <n-card>
        <n-data-table
          :columns="columns"
          :data="nodes"
          :loading="loading"
          :pagination="false"
          :row-key="(row: NodeInfo) => row.nodeId"
          striped
        />
      </n-card>
    </div>

    <n-modal v-model:show="showModal" preset="card" :title="modalTitle" style="width: 720px">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="120">
        <n-form-item label="节点ID" path="nodeId">
          <n-input v-model:value="form.nodeId" placeholder="如 node-001" :disabled="isEdit" />
        </n-form-item>
        <n-form-item label="节点名称" path="name">
          <n-input v-model:value="form.name" placeholder="可选" />
        </n-form-item>
        <n-form-item label="类型" path="type">
          <n-select v-model:value="form.type" :options="typeOptions" />
        </n-form-item>
        <n-form-item label="启用" path="enabled">
          <n-select v-model:value="form.enabled" :options="enabledOptions" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input v-model:value="form.description" type="textarea" placeholder="可选" />
        </n-form-item>

        <!-- SSH 配置 -->
        <template v-if="form.type === 'ssh'">
          <n-form-item label="Host" path="config.host">
            <n-input v-model:value="form.config.host" placeholder="192.168.1.100" />
          </n-form-item>
          <n-form-item label="Port" path="config.port">
            <n-input-number v-model:value="form.config.port" :min="1" :max="65535" />
          </n-form-item>
          <n-form-item label="用户名" path="config.username">
            <n-input v-model:value="form.config.username" placeholder="root" />
          </n-form-item>
          <n-form-item label="认证方式" path="config.authType">
            <n-select v-model:value="form.config.authType" :options="sshAuthOptions" />
          </n-form-item>
          <template v-if="form.config.authType === 'PASSWORD'">
            <n-form-item label="密码" path="config.password">
              <n-input v-model:value="form.config.password" type="password" />
            </n-form-item>
          </template>
          <template v-else>
            <n-form-item label="私钥" path="config.privateKey">
              <n-input v-model:value="form.config.privateKey" type="textarea" placeholder="-----BEGIN ...-----" />
            </n-form-item>
            <n-form-item label="私钥密码" path="config.passphrase">
              <n-input v-model:value="form.config.passphrase" type="password" placeholder="可选" />
            </n-form-item>
          </template>
          <n-form-item label="超时(秒)" path="config.timeoutSeconds">
            <n-input-number v-model:value="form.config.timeoutSeconds" :min="10" :max="3600" />
          </n-form-item>
        </template>

        <!-- Docker API 配置 -->
        <template v-else-if="form.type === 'docker-api'">
          <n-form-item label="DOCKER_HOST" path="config.dockerHost">
            <n-input v-model:value="form.config.dockerHost" placeholder="tcp://192.168.1.100:2376" />
          </n-form-item>
          <n-form-item label="TLS Verify" path="config.dockerTlsVerify">
            <n-select v-model:value="form.config.dockerTlsVerify" :options="dockerTlsOptions" />
          </n-form-item>
          <n-form-item label="CERT_PATH" path="config.dockerCertPath">
            <n-input v-model:value="form.config.dockerCertPath" placeholder="可选，如 /etc/docker/certs" />
          </n-form-item>
        </template>
      </n-form>

      <template #footer>
        <div style="display:flex; justify-content:flex-end; gap:8px;">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { NButton, useDialog, useMessage, type DataTableColumns } from 'naive-ui'
import { nodesApi, type NodeInfo, type NodeType } from '../api/nodes'

const message = useMessage()
const dialog = useDialog()

const nodes = ref<NodeInfo[]>([])
const loading = ref(false)
const saving = ref(false)

const showModal = ref(false)
const isEdit = ref(false)
const modalTitle = ref('新增节点')
const formRef = ref<any>(null)

const emptyForm = (): NodeInfo => ({
  nodeId: '',
  name: '',
  type: 'local',
  description: '',
  enabled: true,
  config: { type: 'local' }
})

const form = ref<NodeInfo>(emptyForm())

const typeOptions = [
  { label: '本地 (local)', value: 'local' },
  { label: 'SSH (ssh)', value: 'ssh' },
  { label: 'Docker API (docker-api)', value: 'docker-api' }
]

const enabledOptions = [
  { label: '启用', value: true },
  { label: '禁用', value: false }
]

const sshAuthOptions = [
  { label: '密码 (PASSWORD)', value: 'PASSWORD' },
  { label: '密钥 (KEY)', value: 'KEY' }
]

const dockerTlsOptions = [
  { label: '否', value: false },
  { label: '是', value: true }
]

const rules: any = {
  nodeId: { required: true, message: 'nodeId 不能为空', trigger: ['blur', 'input'] },
  type: { required: true, message: '请选择类型', trigger: ['change'] }
}

const columns: DataTableColumns<NodeInfo> = [
  { title: '节点ID', key: 'nodeId', width: 180 },
  { title: '名称', key: 'name', width: 160, render: (row) => row.name || '-' },
  { title: '类型', key: 'type', width: 140 },
  { title: '启用', key: 'enabled', width: 100, render: (row) => (row.enabled ? '是' : '否') },
  {
    title: '操作',
    key: 'actions',
    width: 260,
    render(row) {
      return h('div', { style: 'display:flex; gap:8px;' }, [
        h(
          NButton,
          { size: 'small', onClick: () => handleTest(row) },
          { default: () => '测试' }
        ),
        h(
          NButton,
          { size: 'small', onClick: () => openEdit(row) },
          { default: () => '编辑' }
        ),
        h(
          NButton,
          { size: 'small', type: 'error', onClick: () => handleDelete(row) },
          { default: () => '删除' }
        )
      ])
    }
  }
]

async function loadNodes() {
  loading.value = true
  try {
    nodes.value = await nodesApi.list()

  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  modalTitle.value = '新增节点'
  form.value = emptyForm()
  showModal.value = true
}

function openEdit(row: NodeInfo) {
  isEdit.value = true
  modalTitle.value = '编辑节点'
  // 深拷贝，避免编辑时影响表格数据
  form.value = JSON.parse(JSON.stringify(row))
  showModal.value = true
}

async function handleSave() {
  saving.value = true
  try {
    // 按类型补齐 config.type + 默认值
    const t = form.value.type as NodeType
    if (!form.value.config) form.value.config = {}
    form.value.config.type = t

    if (t === 'local') {
      form.value.config = { type: 'local' }
    } else if (t === 'ssh') {
      form.value.config.port ??= 22
      form.value.config.authType ??= 'PASSWORD'
      form.value.config.timeoutSeconds ??= 30
    } else if (t === 'docker-api') {
      form.value.config.dockerTlsVerify ??= false
    }

    await nodesApi.save(form.value)
    message.success('保存成功')
    showModal.value = false
    await loadNodes()

  } finally {
    saving.value = false
  }
}

async function handleDelete(row: NodeInfo) {
  dialog.warning({
    title: '确认删除',
    content: `确定删除节点 ${row.nodeId} 吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {

        await nodesApi.delete(row.nodeId)
        message.success('删除成功')
        await loadNodes()

    }
  })
}

async function handleTest(row: NodeInfo) {

    const ok = await nodesApi.test(row.nodeId)
    if (ok) message.success('连接成功')
    else message.warning('连接失败')

}

onMounted(() => {
  loadNodes()
})
</script>

<style scoped>
.node-management-page {
  width: 100%;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.page-content {
  flex: 1;
  padding: 16px;
  overflow: auto;
}
</style>

