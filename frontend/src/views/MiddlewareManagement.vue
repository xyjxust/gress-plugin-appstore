<template>
  <div class="middleware-management-page">
    <PageHeader title="中间件管理" subtitle="安装与管理基础设施中间件插件（如 Milvus/Redis 等）">
      <template #actions>
        <n-button v-if="activeTab === 'local'" type="primary" @click="handleUploadClick" :loading="uploadLoading">
          <template #icon>
            <n-icon><component :is="CloudUploadOutline" /></n-icon>
          </template>
          上传中间件插件包
        </n-button>
        <n-button :loading="refreshLoading" @click="loadData">
          <template #icon>
            <n-icon><component :is="Refresh" /></n-icon>
          </template>
          刷新
        </n-button>
      </template>
    </PageHeader>

    <div class="page-content">
      <!-- 标签页 -->
      <n-tabs v-model:value="activeTab" type="line" @update:value="handleTabChange">
        <!-- 本地中间件 Tab -->
        <n-tab-pane name="local" tab="我的中间件">
          <n-card>
            <n-data-table
              :columns="localColumns"
              :data="localTableData"
              :loading="localLoading"
              :pagination="false"
              :row-key="(row: MiddlewareInfo) => row.id"
              striped
            />
          </n-card>
        </n-tab-pane>

        <!-- 远程中间件 Tab -->
        <n-tab-pane name="remote" tab="中间件商店">
          <!-- 搜索框 -->
          <n-card style="margin-bottom: 16px">
            <n-space>
              <n-input
                v-model:value="remoteFilters.keyword"
                placeholder="搜索中间件名称、插件ID"
                clearable
                style="width: 300px"
                @keyup.enter="handleRemoteSearch"
              >
                <template #prefix>
                  <n-icon><component :is="SearchOutline" /></n-icon>
                </template>
              </n-input>
              <n-button type="primary" @click="handleRemoteSearch">搜索</n-button>
              <n-button @click="handleRemoteReset">重置</n-button>
            </n-space>
          </n-card>

          <!-- 远程中间件列表 -->
          <div v-if="remoteLoading" class="loading-state">
            <n-spin size="large" />
          </div>

          <div v-else-if="remoteTableData.length === 0" class="empty-state">
            <div class="empty-state__icon">
              <n-icon size="48">
                <component :is="ServerOutline" />
              </n-icon>
            </div>
            <div class="empty-state__text">
              {{ remoteFilters.keyword ? '未找到匹配的中间件' : '暂无远程中间件' }}
            </div>
          </div>

          <div v-else class="middleware-list">
            <n-card
              v-for="middleware in remoteTableData"
              :key="middleware.pluginId"
              class="middleware-card"
              hoverable
            >
              <div class="middleware-header">
                <div class="middleware-icon">
                  <n-icon size="24">
                    <component :is="ServerOutline" />
                  </n-icon>
                </div>
                <div class="middleware-info">
                  <div class="middleware-name">
                    {{ middleware.applicationName || middleware.pluginId }}
                  </div>
                  <div class="middleware-id">{{ middleware.pluginId }}</div>
                </div>
                <div class="middleware-status">
                  <n-tag v-if="middleware.installStatus === 'INSTALLED'" type="success" size="small">
                    已安装
                  </n-tag>
                  <n-tag v-else-if="middleware.installStatus === 'UPGRADABLE'" type="warning" size="small">
                    可升级
                  </n-tag>
                  <n-tag v-else type="info" size="small">
                    未安装
                  </n-tag>
                </div>
              </div>

              <div class="middleware-body">
                <div class="middleware-meta">
                  <div class="meta-item">
                    <span class="meta-label">远程版本：</span>
                    <span class="meta-value">{{ middleware.pluginVersion || '-' }}</span>
                  </div>
                  <div v-if="middleware.localVersion" class="meta-item">
                    <span class="meta-label">本地版本：</span>
                    <span class="meta-value">{{ middleware.localVersion }}</span>
                  </div>
                  <div v-if="middleware.author" class="meta-item">
                    <span class="meta-label">作者：</span>
                    <span class="meta-value">{{ middleware.author }}</span>
                  </div>
                </div>

                <div v-if="middleware.description" class="middleware-description">
                  {{ middleware.description }}
                </div>
              </div>

              <div class="middleware-footer">
                <div class="middleware-actions">
                  <n-button
                    v-if="middleware.installStatus === 'NOT_INSTALLED'"
                    text
                    type="success"
                    size="small"
                    :loading="installRemoteLoading[middleware.pluginId]"
                    @click="handleInstallRemote(middleware)"
                  >
                    安装
                  </n-button>
                  <n-button
                    v-else-if="middleware.installStatus === 'UPGRADABLE'"
                    text
                    type="warning"
                    size="small"
                    :loading="upgradeRemoteLoading[middleware.pluginId]"
                    @click="handleUpgradeRemote(middleware)"
                  >
                    升级
                  </n-button>
                  <n-button
                    v-else
                    text
                    type="default"
                    size="small"
                    disabled
                  >
                    已安装
                  </n-button>
                </div>
              </div>
            </n-card>
          </div>

          <!-- 远程中间件分页 -->
          <div v-if="remotePagination.itemCount > 0" class="pagination">
            <n-pagination
              v-model:page="remotePagination.page"
              v-model:page-size="remotePagination.pageSize"
              :page-count="Math.ceil(remotePagination.itemCount / remotePagination.pageSize)"
              :page-sizes="remotePagination.pageSizes"
              show-size-picker
              @update:page="handleRemotePageChange"
              @update:page-size="handleRemotePageSizeChange"
            />
          </div>
        </n-tab-pane>

        <!-- 服务管理 Tab -->
        <n-tab-pane name="services" tab="服务管理">
          <n-card>
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>中间件服务管理</span>
                <n-button type="primary" @click="handleAddServiceClick">
                  <template #icon>
                    <n-icon><component :is="AddOutline" /></n-icon>
                  </template>
                  添加服务
                </n-button>
              </div>
            </template>

            <n-data-table
              :columns="serviceColumns"
              :data="serviceTableData"
              :loading="serviceLoading"
              :pagination="false"
              :row-key="(row: MiddlewareServiceInfo) => row.serviceId"
              striped
            />
          </n-card>
        </n-tab-pane>
      </n-tabs>

      <input
        ref="fileInputRef"
        type="file"
        accept=".jar"
        style="display: none"
        @change="handleFileSelected"
      />

      <!-- 安装 / 升级详情抽屉（实时日志） -->
      <n-drawer v-model:show="showInstallDrawer" placement="right" width="520">
        <n-drawer-content :title="currentInstallPluginId ? `安装详情 - ${currentInstallPluginId}` : '安装详情'">
          <div class="install-drawer__body">
            <!-- 步骤进度显示 -->
            <n-card v-if="installStepProgress.currentStep > 0" size="small" style="margin-bottom: 16px;">
              <n-space vertical :size="8">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span style="font-weight: 500;">执行进度</span>
                  <n-text type="info">
                    {{ installStepProgress.currentStep }} / {{ installStepProgress.totalSteps }}
                  </n-text>
    </div>
                <n-progress
                  :percentage="installStepProgress.percentage"
                  :status="installStepProgress.status"
                  :show-indicator="true"
                />
                <n-text v-if="installStepProgress.stepName" depth="3" style="font-size: 12px;">
                  当前步骤: {{ installStepProgress.stepName }}
                </n-text>
              </n-space>
            </n-card>
            
            <div class="install-drawer__logs">
              <template v-if="installLogs.length > 0">
                <div v-for="(line, idx) in installLogs" :key="idx">
                  {{ line }}
                </div>
              </template>
              <template v-else>
                <div>等待安装日志输出...</div>
              </template>
            </div>
          </div>
          <template #footer>
            <n-space justify="end">
              <n-button @click="closeInstallDrawer">关闭</n-button>
            </n-space>
          </template>
        </n-drawer-content>
      </n-drawer>

      <!-- 配置表单弹窗（用于需要配置的中间件） -->
      <n-modal
        v-model:show="showConfigDialog"
        preset="dialog"
        title="配置安装参数"
        positive-text="确定安装"
        negative-text="取消"
        :positive-button-props="{ loading: configLoading }"
        :loading="configLoading"
        :style="{ width: '900px' }"
        @positive-click="handleConfirmConfig"
      >
        <n-scrollbar style="max-height: 70vh;">
          <n-spin :show="configMetadataLoading">
            <div class="config-modal-content">
              <!-- 中间件信息卡片 -->
              <n-card size="small" :bordered="false" class="app-info-card" v-if="pendingInstall">
                <div class="app-info-row">
                  <div class="app-info-item">
                    <span class="info-label">中间件名称：</span>
                    <span class="info-value">{{ pendingInstall.middleware.applicationName || pendingInstall.middleware.pluginId }}</span>
                  </div>
                  <div class="app-info-item">
                    <span class="info-label">插件ID：</span>
                    <n-text type="info" class="info-value">{{ pendingInstall.middleware.pluginId }}</n-text>
                  </div>
                </div>
              </n-card>

              <!-- 动态扩展配置 -->
              <template v-if="configMetadata.length > 0">
                <n-card size="small" title="安装配置" :bordered="false" class="config-section">
                  <DynamicFormRenderer
                    v-model="configFormData"
                    :metadata="{ fields: configMetadata }"
                  />
                </n-card>
              </template>
            </div>
          </n-spin>
        </n-scrollbar>
      </n-modal>

      <!-- 服务管理对话框 -->
      <n-modal
        v-model:show="showServiceDialog"
        preset="dialog"
        :title="editingServiceId ? '编辑服务' : '添加服务'"
        positive-text="确定"
        negative-text="取消"
        :style="{ width: '800px' }"
        @positive-click="handleSaveService"
      >
        <n-form
          ref="serviceFormRef"
          :model="serviceFormData"
          :rules="serviceFormRules"
          label-placement="left"
          label-width="120px"
          require-mark-placement="right-hanging"
        >
          <n-form-item label="服务ID" path="serviceId">
            <n-input
              v-model:value="serviceFormData.serviceId"
              placeholder="例如: etcd-installer, minio-installer"
              :disabled="!!editingServiceId"
            />
          </n-form-item>
          <n-form-item label="服务名称" path="serviceName">
            <n-input v-model:value="serviceFormData.serviceName" placeholder="服务显示名称" />
          </n-form-item>
          <n-form-item label="服务类型" path="serviceType">
            <n-input v-model:value="serviceFormData.serviceType" placeholder="例如: etcd, minio, redis" />
          </n-form-item>
          <n-form-item label="容器名称" path="containerName">
            <n-input v-model:value="serviceFormData.containerName" placeholder="Docker 容器名称（可选）" />
          </n-form-item>
          <n-form-item label="服务主机" path="serviceHost">
            <n-input v-model:value="serviceFormData.serviceHost" placeholder="localhost 或 IP 地址" />
          </n-form-item>
          <n-form-item label="服务端口" path="servicePort">
            <n-input-number
              v-model:value="serviceFormData.servicePort"
              placeholder="端口号"
              :min="1"
              :max="65535"
              style="width: 100%"
            />
          </n-form-item>
          <n-form-item label="健康检查URL" path="healthCheckUrl">
            <n-input v-model:value="serviceFormData.healthCheckUrl" placeholder="例如: http://localhost:9000/health" />
          </n-form-item>
          <n-form-item label="服务状态" path="status">
            <n-select
              v-model:value="serviceFormData.status"
              :options="[
                { label: '运行中', value: 'RUNNING' },
                { label: '已停止', value: 'STOPPED' },
                { label: '错误', value: 'ERROR' }
              ]"
            />
          </n-form-item>
          <n-form-item label="安装者" path="installedBy">
            <n-input v-model:value="serviceFormData.installedBy" placeholder="manual" />
          </n-form-item>
          <n-form-item label="自定义配置" path="config">
            <n-card size="small" :bordered="true" style="width: 100%">
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>配置项（如账号、密码等）</span>
                  <n-button size="small" type="primary" @click="handleAddConfigItem">
                    <template #icon>
                      <n-icon><component :is="AddOutline" /></n-icon>
                    </template>
                    添加
                  </n-button>
                </div>
              </template>
              <div v-if="configItems.length === 0" style="text-align: center; color: #999; padding: 20px;">
                暂无配置项，点击"添加"按钮添加配置
              </div>
              <n-space v-else vertical :size="12">
                <div
                  v-for="(item, index) in configItems"
                  :key="index"
                  style="display: flex; gap: 8px; align-items: center; padding: 8px; background: #f5f5f5; border-radius: 4px;"
                >
                  <n-input
                    v-model:value="item.key"
                    placeholder="配置键（如: username）"
                    style="flex: 1"
                    @update:value="updateConfigFromItems"
                  />
                  <n-input
                    v-model:value="item.value"
                    placeholder="配置值（如: admin）"
                    style="flex: 1"
                    :type="item.key && (item.key.toLowerCase().includes('password') || item.key.toLowerCase().includes('pwd') || item.key.toLowerCase().includes('secret')) ? 'password' : 'text'"
                    @update:value="updateConfigFromItems"
                  />
                  <n-button
                    size="small"
                    type="error"
                    @click="handleRemoveConfigItem(index)"
                  >
                    <template #icon>
                      <n-icon><component :is="TrashOutline" /></n-icon>
                    </template>
                  </n-button>
                </div>
              </n-space>
            </n-card>
          </n-form-item>
        </n-form>
      </n-modal>
    </div>
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, onUnmounted, ref, reactive } from 'vue'
import { NButton, NIcon, NTabs, NTabPane, NTag, NSpin, NCard, NPagination, NInput, NSpace, NSelect, NForm, NFormItem, NInputNumber, NText, NScrollbar, NModal, NProgress, useDialog, useMessage, type DataTableColumns } from 'naive-ui'
import { useIcon } from '@keqi.gress/plugin-bridge'
import { middlewareApi, type RemoteMiddlewareInfo, type MiddlewareServiceInfo } from '../api/middleware'
import { nodesApi, type NodeInfo } from '../api/nodes'
import type { MiddlewareInfo } from '../types/middleware'

const message = useMessage()
const dialog = useDialog()

// 图标
const Refresh = useIcon('RefreshOutline')
const CloudUploadOutline = useIcon('CloudUploadOutline')
const PulseOutline = useIcon('PulseOutline')
const TrashOutline = useIcon('TrashOutline')
const ServerOutline = useIcon('ServerOutline')
const SearchOutline = useIcon('SearchOutline')
const CopyOutline = useIcon('CopyOutline')
const AddOutline = useIcon('AddOutline')
const EditOutline = useIcon('CreateOutline')

// Tab 状态
const activeTab = ref<'local' | 'remote' | 'services'>('local')

// 本地中间件状态
const localLoading = ref(false)
const refreshLoading = ref(false)
const uploadLoading = ref(false)
const localTableData = ref<MiddlewareInfo[]>([])

// 远程中间件状态
const remoteLoading = ref(false)
const remoteTableData = ref<RemoteMiddlewareInfo[]>([])
const remoteFilters = ref({
  keyword: ''
})

// 远程安装/升级加载状态
const installRemoteLoading = ref<Record<string, boolean>>({})
const upgradeRemoteLoading = ref<Record<string, boolean>>({})

// 安装详情抽屉 & 日志（SSE）
const showInstallDrawer = ref(false)
const installLogs = ref<string[]>([])
const currentInstallPluginId = ref<string | null>(null)
let installSseUnsubscribe: (() => void) | null = null

// 步骤进度信息
const installStepProgress = ref<{
  currentStep: number
  totalSteps: number
  stepName: string
  percentage: number
  status: 'success' | 'error' | 'warning' | 'info'
}>({
  currentStep: 0,
  totalSteps: 0,
  stepName: '',
  percentage: 0,
  status: 'info'
})

// 节点列表（用于远程安装时选择）
const nodesLoading = ref(false)
const nodes = ref<NodeInfo[]>([])

const selectedNodeId = ref<string>('__local__')

// 配置表单（用于需要配置的中间件）
const showConfigDialog = ref(false)
const configFormRef = ref<any>(null)
const configFormData = ref<Record<string, any>>({})
const configMetadata = ref<any[]>([])
const configMetadataLoading = ref(false)
const configLoading = ref(false)
const pendingInstall = ref<{ middleware: RemoteMiddlewareInfo; targetNodeId?: string; executionType?: 'local' | 'ssh' | 'docker-api' } | null>(null)

// 服务管理状态
const serviceLoading = ref(false)
const serviceTableData = ref<MiddlewareServiceInfo[]>([])
const showServiceDialog = ref(false)
const serviceFormRef = ref<any>(null)
const serviceFormData = ref<MiddlewareServiceInfo>({
  serviceId: '',
  serviceType: '',
  serviceName: '',
  containerName: '',
  serviceHost: 'localhost',
  servicePort: undefined,
  healthCheckUrl: '',
  config: {},
  installedBy: 'manual',
  status: 'RUNNING'
})
const editingServiceId = ref<string | null>(null)
const configItems = ref<Array<{ key: string; value: string }>>([])

const nodeOptions = () => {
  const opts = [
    { label: '本地 (local)', value: '__local__' }
  ]
  for (const n of nodes.value) {
    const label = `${n.nodeId}${n.name ? `（${n.name}）` : ''} - ${n.type}${n.enabled ? '' : ' [禁用]'}`
    opts.push({ label, value: n.nodeId })
  }
  return opts
}

async function loadNodes() {
  nodesLoading.value = true
  try {
    nodes.value = await nodesApi.list()
  } catch (e: any) {
    // 节点管理可能还未启用，不阻断安装
    console.warn('加载节点失败:', e)
    nodes.value = []
  } finally {
    nodesLoading.value = false
  }
}

// 远程分页
const remotePagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  pageSizes: [10, 20, 50, 100]
})

const fileInputRef = ref<HTMLInputElement | null>(null)

function appendInstallLog(line: string) {
  if (!line) return
  installLogs.value.push(line)
}

function subscribeInstallLogs(pluginId: string) {
  const sseClient = (window as any).__GRESS_SSE_CLIENT__
  if (!sseClient) {
    console.warn('[Middleware] 全局 SSE 客户端未初始化')
    return
  }

  // 先取消之前的订阅
  if (installSseUnsubscribe) {
    installSseUnsubscribe()
    installSseUnsubscribe = null
  }

  currentInstallPluginId.value = pluginId
  installLogs.value = []

  const businessType = 'MIDDLEWARE_INSTALL'

  installSseUnsubscribe = sseClient.on(businessType, (message: any) => {
    try {
      const { businessId, type, status, data, message: msg, error, progress } = message || {}
      if (businessId && businessId !== pluginId) return

      switch (type) {
        case 'DATA':
          appendInstallLog((data && (data.line || data.message)) || msg || '')
          break
        case 'PROGRESS':
          // 处理步骤进度信息
          if (progress) {
            installStepProgress.value = {
              currentStep: progress.current || 0,
              totalSteps: progress.total || 0,
              stepName: progress.message || '',
              percentage: progress.percentage || 0,
              status: 'info'
            }
            // 同时显示进度日志
            if (progress.message) {
              appendInstallLog(`[步骤 ${progress.current}/${progress.total}] ${progress.message}`)
            }
          }
          break
        case 'STATUS':
          if (status === 'START') {
            appendInstallLog('开始安装中间件...')
            // 重置进度
            installStepProgress.value = {
              currentStep: 0,
              totalSteps: 0,
              stepName: '',
              percentage: 0,
              status: 'info'
            }
          } else if (status === 'SUCCESS') {
            appendInstallLog('中间件安装成功')
            // 更新进度为完成
            if (installStepProgress.value.totalSteps > 0) {
              installStepProgress.value.currentStep = installStepProgress.value.totalSteps
              installStepProgress.value.percentage = 100
              installStepProgress.value.status = 'success'
            }
            // 安装成功后刷新本地列表
            loadLocalData()
          } else if (msg) {
            appendInstallLog(String(msg))
          }
          break
        case 'ERROR':
          appendInstallLog(`安装出错: ${error || msg || ''}`)
          installStepProgress.value.status = 'error'
          break
        case 'COMPLETE':
          appendInstallLog('安装完成')
          if (installStepProgress.value.totalSteps > 0) {
            installStepProgress.value.currentStep = installStepProgress.value.totalSteps
            installStepProgress.value.percentage = 100
            installStepProgress.value.status = 'success'
          }
          break
        default:
          // 其它事件先简单打印
          if (msg) {
            appendInstallLog(String(msg))
          }
      }
    } catch (e) {
      console.error('[Middleware] 处理安装日志消息失败:', e, message)
    }
  })
}

function closeInstallDrawer() {
  if (installSseUnsubscribe) {
    installSseUnsubscribe()
    installSseUnsubscribe = null
  }
  showInstallDrawer.value = false
  installLogs.value = []
  currentInstallPluginId.value = null
  // 重置进度信息
  installStepProgress.value = {
    currentStep: 0,
    totalSteps: 0,
    stepName: '',
    percentage: 0,
    status: 'info'
  }
}

// 本地中间件表格列
const localColumns: DataTableColumns<MiddlewareInfo> = [
  {
    title: 'ID',
    key: 'id',
    width: 220
  },
  {
    title: '版本',
    key: 'version',
    width: 120
  },
  {
    title: '状态',
    key: 'status',
    width: 120,
    render(row) {
      return row.status || 'UNKNOWN'
    }
  },
  {
    title: '工作目录',
    key: 'workDir',
    render(row) {
      return row.workDir || '-'
    }
  },
  {
    title: '操作',
    key: 'actions',
    width: 320,
    render(row) {
      return h('div', { style: 'display:flex; gap:8px;' }, [
        h(
          NButton,
          {
            size: 'small',
            onClick: () => handleCopyConnectionInfo(row)
          },
          {
            icon: () => h(NIcon, null, { default: () => h(CopyOutline as any) }),
            default: () => '复制连接信息'
          }
        ),
        h(
          NButton,
          {
            size: 'small',
            onClick: () => handleHealth(row)
          },
          {
            icon: () => h(NIcon, null, { default: () => h(PulseOutline as any) }),
            default: () => '健康'
          }
        ),
        h(
          NButton,
          {
            size: 'small',
            type: 'error',
            onClick: () => handleUninstall(row)
          },
          {
            icon: () => h(NIcon, null, { default: () => h(TrashOutline as any) }),
            default: () => '卸载'
          }
        )
      ])
    }
  }
]


// 服务管理表格列
const serviceColumns: DataTableColumns<MiddlewareServiceInfo> = [
  {
    title: '服务ID',
    key: 'serviceId',
    width: 180
  },
  {
    title: '服务名称',
    key: 'serviceName',
    width: 150
  },
  {
    title: '服务类型',
    key: 'serviceType',
    width: 120
  },
  {
    title: '主机',
    key: 'serviceHost',
    width: 150
  },
  {
    title: '端口',
    key: 'servicePort',
    width: 100
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render(row) {
      const status = row.status || 'RUNNING'
      const type = status === 'RUNNING' ? 'success' : status === 'STOPPED' ? 'default' : 'error'
      return h(NTag, { type, size: 'small' }, { default: () => status })
    }
  },
  {
    title: '引用计数',
    key: 'referenceCount',
    width: 100,
    render(row) {
      return row.referenceCount || 0
    }
  },
  {
    title: '安装者',
    key: 'installedBy',
    width: 120,
    render(row) {
      return row.installedBy || '-'
    }
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render(row) {
      return h('div', { style: 'display:flex; gap:8px;' }, [
        h(
          NButton,
          {
            size: 'small',
            onClick: () => handleEditService(row)
          },
          {
            icon: () => h(NIcon, null, { default: () => h(EditOutline as any) }),
            default: () => '编辑'
          }
        ),
        h(
          NButton,
          {
            size: 'small',
            type: 'error',
            onClick: () => handleDeleteService(row)
          },
          {
            icon: () => h(NIcon, null, { default: () => h(TrashOutline as any) }),
            default: () => '删除'
          }
        )
      ])
    }
  }
]

// 方法
const loadData = async () => {
  refreshLoading.value = true
  try {
    if (activeTab.value === 'local') {
      await loadLocalData()
    } else if (activeTab.value === 'remote') {
      await loadRemoteData()
    } else if (activeTab.value === 'services') {
      await loadServices()
    }
  } finally {
    refreshLoading.value = false
  }
}

const loadLocalData = async () => {
  localLoading.value = true
  try {
    localTableData.value = await middlewareApi.list()
  } catch (e: any) {

  } finally {
    localLoading.value = false
  }
}

const loadRemoteData = async () => {
  remoteLoading.value = true
  try {
    const params: any = {
      page: remotePagination.page,
      size: remotePagination.pageSize
    }
    
    if (remoteFilters.value.keyword && remoteFilters.value.keyword.trim()) {
      params.keyword = remoteFilters.value.keyword.trim()
    }

    const data = await middlewareApi.getRemoteList(params)
    remoteTableData.value = data.items
    remotePagination.itemCount = data.total
  } catch (e: any) {

  } finally {
    remoteLoading.value = false
  }
}

const handleTabChange = (value: string) => {
  activeTab.value = value as 'local' | 'remote' | 'services'
  loadData()
}

const handleRemoteSearch = () => {
  remotePagination.page = 1
  loadRemoteData()
}

const handleRemoteReset = () => {
  remoteFilters.value.keyword = ''
  remotePagination.page = 1
  loadRemoteData()
}

const handleRemotePageChange = (page: number) => {
  remotePagination.page = page
  loadRemoteData()
}

const handleRemotePageSizeChange = (pageSize: number) => {
  remotePagination.pageSize = pageSize
  remotePagination.page = 1
  loadRemoteData()
}

// 服务管理方法
const loadServices = async () => {
  serviceLoading.value = true
  try {
    serviceTableData.value = await middlewareApi.listServices()
  } catch (e: any) {

  } finally {
    serviceLoading.value = false
  }
}

const handleAddServiceClick = () => {
  editingServiceId.value = null
  serviceFormData.value = {
    serviceId: '',
    serviceType: '',
    serviceName: '',
    containerName: '',
    serviceHost: 'localhost',
    servicePort: undefined,
    healthCheckUrl: '',
    config: {},
    installedBy: 'manual',
    status: 'RUNNING'
  }
  configItems.value = []
  showServiceDialog.value = true
}

const handleEditService = (service: MiddlewareServiceInfo) => {
  editingServiceId.value = service.serviceId
  serviceFormData.value = {
    ...service,
    config: service.config || {}
  }
  // 将 config 对象转换为键值对数组
  let configObj: Record<string, any> = {}
  if (service.config) {
    if (typeof service.config === 'string') {
      try {
        configObj = JSON.parse(service.config)
      } catch (e) {
        console.warn('解析 config JSON 失败:', e)
        configObj = {}
      }
    } else if (typeof service.config === 'object') {
      configObj = service.config
    }
  }
  configItems.value = Object.entries(configObj).map(([key, value]) => ({
    key,
    value: String(value != null ? value : '')
  }))
  showServiceDialog.value = true
}

const handleDeleteService = (service: MiddlewareServiceInfo) => {
  dialog.warning({
    title: '删除服务',
    content: `确定要删除服务 "${service.serviceId}" 吗？${service.referenceCount && service.referenceCount > 0 ? `该服务正在被 ${service.referenceCount} 个中间件使用。` : ''}`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await middlewareApi.deleteService(service.serviceId)
        message.success('删除成功')
        await loadServices()
      } catch (e: any) {

      }
    }
  })
}

const handleAddConfigItem = () => {
  configItems.value.push({ key: '', value: '' })
}

const handleRemoveConfigItem = (index: number) => {
  configItems.value.splice(index, 1)
  updateConfigFromItems()
}

const updateConfigFromItems = () => {
  const config: Record<string, any> = {}
  configItems.value.forEach(item => {
    if (item.key && item.key.trim()) {
      config[item.key.trim()] = item.value || ''
    }
  })
  serviceFormData.value.config = config
}

const handleSaveService = async () => {
  try {
    // 先更新 config 对象
    updateConfigFromItems()
    
    await serviceFormRef.value?.validate()
    
    if (editingServiceId.value) {
      // 更新
      await middlewareApi.updateService(editingServiceId.value, serviceFormData.value)
      message.success('更新成功')
    } else {
      // 创建
      await middlewareApi.registerService(serviceFormData.value)
      message.success('添加成功')
    }
    
    showServiceDialog.value = false
    await loadServices()
  } catch (e: any) {
    if (e?.message && !e.message.includes('validation')) {
      message.error(e.message)
    }
  }
}

// 服务表单验证规则
const serviceFormRules: any = {
  serviceId: {
    required: true,
    message: '请输入服务ID',
    trigger: 'blur'
  },
  serviceHost: {
    required: true,
    message: '请输入服务主机',
    trigger: 'blur'
  },
  servicePort: {
    required: true,
    type: 'number',
    message: '请输入服务端口',
    trigger: 'blur'
  }
}

function handleUploadClick() {
  fileInputRef.value?.click()
}

async function handleFileSelected() {
  const input = fileInputRef.value
  if (!input || !input.files || input.files.length === 0) return
  const file = input.files[0]
  input.value = ''

  uploadLoading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('operatorName', 'admin')
    const r = await middlewareApi.uploadAndInstall(fd)
    message.success(`安装成功: ${r.middlewareId}`)
    await loadLocalData()
  } catch (e: any) {

  } finally {
    uploadLoading.value = false
  }
}

async function handleHealth(row: MiddlewareInfo) {

    const r = await middlewareApi.health(row.id)
    if (r.healthy) {
      message.success(`健康: ${row.id}`)
    } else {
      message.warning(`不健康: ${r.message || row.id}`)
    }

}

function handleUninstall(row: MiddlewareInfo) {
  dialog.warning({
    title: '确认卸载',
    content: `确定要卸载中间件 ${row.id} 吗？`,
    positiveText: '卸载',
    negativeText: '取消',
    onPositiveClick: async () => {
        await middlewareApi.uninstall(row.id, 'admin')
        message.success('卸载成功')
        await loadLocalData()

    }
  })
}

async function handleCopyConnectionInfo(row: MiddlewareInfo) {
    // 获取文本格式的连接信息
    const result = await middlewareApi.getConnectionInfoText(row.id)
    
    // 复制到剪贴板
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(result.text)
      message.success('连接信息已复制到剪贴板')
    } else {
      // 降级方案：使用传统方法
      const textArea = document.createElement('textarea')
      textArea.value = result.text
      textArea.style.position = 'fixed'
      textArea.style.left = '-999999px'
      document.body.appendChild(textArea)
      textArea.select()
      try {
        document.execCommand('copy')
        message.success('连接信息已复制到剪贴板')
      } finally {
        document.body.removeChild(textArea)
      }
    }

}

const handleInstallRemote = (middleware: RemoteMiddlewareInfo) => {
  if (!middleware.pluginId) {
    message.error('插件ID不能为空')
    return
  }
  
  // 每次打开都刷新一次节点（避免新增节点后未刷新）
  loadNodes()
  selectedNodeId.value = '__local__'
  
  dialog.warning({
    title: '安装中间件',
    content: () =>
      h('div', { style: 'display:flex; flex-direction:column; gap:12px;' }, [
        h('div', null, `确定要安装中间件 "${middleware.applicationName || middleware.pluginId}" 吗？`),
        h('div', { style: 'display:flex; align-items:center; gap:10px;' }, [
          h('div', { style: 'width:96px; color:#666;' }, '目标节点'),
          h(NSelect as any, {
            value: selectedNodeId.value,
            options: nodeOptions(),
            loading: nodesLoading.value,
            style: 'flex:1;',
            onUpdateValue: (v: string) => (selectedNodeId.value = v)
          })
        ])
      ]),
    positiveText: '下一步',
    negativeText: '取消',
    onPositiveClick: async () => {
      const targetNodeId = selectedNodeId.value === '__local__' ? undefined : selectedNodeId.value
      const node = targetNodeId ? nodes.value.find(n => n.nodeId === targetNodeId) : undefined

      // 先检查是否有配置元数据
      configMetadataLoading.value = true
      try {
        const metadata = await middlewareApi.getConfigMetadata(middleware.pluginId)
        configMetadata.value = metadata || []
        
        if (configMetadata.value.length > 0) {
          // 有配置元数据，显示配置表单
          // 初始化配置表单数据（使用默认值）
          configFormData.value = {}
          // 从元数据中提取默认值
          configMetadata.value.forEach((field: any) => {
            if (field.defaultValue) {
              configFormData.value[field.code] = field.defaultValue
            }
          })
          
          pendingInstall.value = {
            middleware,
            targetNodeId,
            executionType: node?.type as 'local' | 'ssh' | 'docker-api' | undefined
          }
          showConfigDialog.value = true
        } else {
          // 没有配置元数据，直接安装
          await doInstall(middleware, targetNodeId, node?.type, {})
        }
      } catch (error: any) {
        console.error('获取配置元数据失败:', error)
        // 获取元数据失败，直接安装（不阻断安装流程）
        message.warning('获取配置元数据失败，将使用默认配置安装')
        await doInstall(middleware, targetNodeId, node?.type, {})
      } finally {
        configMetadataLoading.value = false
      }
    }
  })
}

// 执行安装
async function doInstall(
  middleware: RemoteMiddlewareInfo,
  targetNodeId?: string,
  executionType?: 'local' | 'ssh' | 'docker-api',
  config?: Record<string, any>
) {
      installRemoteLoading.value[middleware.pluginId] = true
  showInstallDrawer.value = true
  subscribeInstallLogs(middleware.pluginId)
  message.info(`正在安装中间件（可在右侧抽屉查看实时日志）: ${middleware.applicationName || middleware.pluginId}...`)
      try {
    // 优先使用流式安装接口（SSE）
    await middlewareApi.installRemoteStream(middleware.pluginId, {
      targetNodeId,
      executionType: executionType as 'local' | 'ssh' | 'docker-api' | undefined,
      config
    })

      } finally {
        installRemoteLoading.value[middleware.pluginId] = false
      }
    }

// 确认配置并安装
function handleConfirmConfig() {
  if (!pendingInstall.value) return false

  configLoading.value = true
  try {
    // DynamicFormRenderer 会自动验证表单，这里直接使用配置数据
    showConfigDialog.value = false
    const { middleware, targetNodeId, executionType } = pendingInstall.value!
    doInstall(middleware, targetNodeId, executionType, configFormData.value)
    pendingInstall.value = null
    return true
  } catch (error: any) {

    return false
  } finally {
    configLoading.value = false
  }
}

const handleUpgradeRemote = (middleware: RemoteMiddlewareInfo) => {
  if (!middleware.pluginId) {
    message.error('插件ID不能为空')
    return
  }
  
  const targetVersion = middleware.pluginVersion || '-'
  
  loadNodes()
  selectedNodeId.value = '__local__'
  
  dialog.warning({
    title: '升级中间件',
    content: () =>
      h('div', { style: 'display:flex; flex-direction:column; gap:12px;' }, [
        h('div', null, `确定要将中间件 "${middleware.applicationName || middleware.pluginId}" 升级到版本 ${targetVersion} 吗？`),
        h('div', { style: 'display:flex; align-items:center; gap:10px;' }, [
          h('div', { style: 'width:96px; color:#666;' }, '目标节点'),
          h(NSelect as any, {
            value: selectedNodeId.value,
            options: nodeOptions(),
            loading: nodesLoading.value,
            style: 'flex:1;',
            onUpdateValue: (v: string) => (selectedNodeId.value = v)
          })
        ])
      ]),
    positiveText: '升级',
    negativeText: '取消',
    onPositiveClick: async () => {
      upgradeRemoteLoading.value[middleware.pluginId] = true
      showInstallDrawer.value = true
      subscribeInstallLogs(middleware.pluginId)
      message.info(`正在升级中间件（可在右侧抽屉查看实时日志）: ${middleware.applicationName || middleware.pluginId}...`)
      try {
        const targetNodeId = selectedNodeId.value === '__local__' ? undefined : selectedNodeId.value
        const node = targetNodeId ? nodes.value.find(n => n.nodeId === targetNodeId) : undefined

        // 升级逻辑：直接使用流式安装接口
        await middlewareApi.installRemoteStream(middleware.pluginId, {
          targetNodeId,
          executionType: node?.type
        })
        
        // 成功与否由 SSE 日志提示

      } finally {
        upgradeRemoteLoading.value[middleware.pluginId] = false
      }
    }
  })
}

onMounted(() => {
  loadData()
})

onUnmounted(() => {
  if (installSseUnsubscribe) {
    installSseUnsubscribe()
    installSseUnsubscribe = null
  }
})
</script>

<style scoped>
.middleware-management-page {
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

.loading-state {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 60px 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: #999;
}

.empty-state__icon {
  margin-bottom: 16px;
}

.empty-state__text {
  font-size: 14px;
}

.middleware-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.middleware-card {
  cursor: pointer;
  transition: all 0.2s;
}

.middleware-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.middleware-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.middleware-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 8px;
  flex-shrink: 0;
}

.middleware-info {
  flex: 1;
  min-width: 0;
}

.middleware-name {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.middleware-id {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.middleware-body {
  margin-bottom: 12px;
}

.middleware-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.meta-label {
  color: #666;
  margin-right: 8px;
  flex-shrink: 0;
}

.meta-value {
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.middleware-description {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.middleware-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.middleware-actions {
  display: flex;
  gap: 8px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.install-drawer__body {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.install-drawer__logs {
  flex: 1;
  background: #000;
  color: #0f0;
  padding: 12px;
  border-radius: 4px;
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
  font-size: 12px;
  overflow: auto;
  white-space: pre-wrap;
}

/* 配置对话框样式 */
.config-modal-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 8px;
}

.app-info-card {
  background: #f8f9fa;
}

.app-info-row {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
}

.app-info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.info-label {
  color: #6b7280;
  font-weight: 500;
}

.info-value {
  color: #1f2937;
  font-weight: 600;
}

.config-section {
  margin-bottom: 0;
}
</style>
