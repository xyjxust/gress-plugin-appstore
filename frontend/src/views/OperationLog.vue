<template>
  <div class="operation-log-page">
    <!-- 页面头部 - 撑满宽度 -->
    <div class="page-header-wrapper">
      <PageHeader title="操作日志" subtitle="查看所有应用操作记录，追踪应用变更">
        <template #actions>
          <n-button @click="loadData" :loading="refreshLoading">
            <template #icon>
              <n-icon><component :is="Refresh" /></n-icon>
            </template>
            刷新
          </n-button>
        </template>
      </PageHeader>
    </div>

    <!-- 内容区域 - 有 padding -->
    <div class="page-content">
      <!-- 过滤面板 -->
      <FilterPanel
        v-model:filters="filters"
        v-model:show-advanced="showAdvanced"
        :basic-fields="basicFields"
        :advanced-fields="advancedFields"
        @search="handleSearch"
        @reset="handleReset"
      />

      <!-- 数据表格 -->
      <div class="table-container">
        <n-data-table
          :columns="columns"
          :data="tableData"
          :loading="loading"
          :pagination="false"
          :row-key="(row: OperationLog) => row.id"
          striped
        />
        
        <!-- 独立分页器 -->
        <div class="pagination-container">
          <n-pagination
            v-model:page="pagination.page"
            v-model:page-size="pagination.pageSize"
            :item-count="pagination.itemCount"
            :page-sizes="pagination.pageSizes"
            show-size-picker
            show-quick-jumper
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
          >
            <template #prefix="{ itemCount }">
              共 {{ itemCount }} 条
            </template>
          </n-pagination>
        </div>
      </div>
    </div>

    <!-- 详情抽屉 -->
    <n-drawer
      v-model:show="showDetailDrawer"
      :width="720"
      placement="right"
    >
      <n-drawer-content title="操作日志详情" closable>
        <div v-if="currentLog" class="log-detail">
          <!-- 基本信息 -->
          <n-card title="基本信息" :bordered="false" class="detail-section">
            <n-descriptions :column="2" label-placement="left">
              <n-descriptions-item label="日志ID">
                {{ currentLog.id }}
              </n-descriptions-item>
              <n-descriptions-item label="操作时间">
                {{ formatDateTime(currentLog.createTime) }}
              </n-descriptions-item>
              <n-descriptions-item label="应用名称">
                {{ currentLog.applicationName }}
              </n-descriptions-item>
              <n-descriptions-item label="操作类型">
                <n-tag :type="getOperationTypeColor(currentLog.operationType)" size="small">
                  {{ currentLog.operationTypeText }}
                </n-tag>
              </n-descriptions-item>
              <n-descriptions-item label="操作结果" :span="2">
                <n-tag :type="currentLog.status === 'SUCCESS' ? 'success' : 'error'" size="small">
                  {{ currentLog.statusText }}
                </n-tag>
              </n-descriptions-item>
              <n-descriptions-item v-if="currentLog.duration" label="耗时" :span="2">
                {{ currentLog.duration }} ms
              </n-descriptions-item>
            </n-descriptions>

            <template v-if="currentLog.operationDesc">
              <n-divider />
              <n-descriptions :column="1" label-placement="left">
                <n-descriptions-item label="操作描述">
                  {{ currentLog.operationDesc }}
                </n-descriptions-item>
              </n-descriptions>
            </template>
          </n-card>

          <!-- 操作人信息 -->
          <n-card title="操作人信息" :bordered="false" class="detail-section">
            <n-descriptions :column="2" label-placement="left">
              <n-descriptions-item label="操作人ID">
                {{ currentLog.operatorId }}
              </n-descriptions-item>
              <n-descriptions-item label="操作人名称">
                {{ currentLog.operatorName || '-' }}
              </n-descriptions-item>
              <n-descriptions-item label="操作IP" :span="2">
                {{ currentLog.clientIp || '-' }}
              </n-descriptions-item>
            </n-descriptions>
          </n-card>

          <!-- 错误信息 -->
          <n-card v-if="currentLog.status === 'FAIL' && currentLog.message" 
                  title="错误信息" 
                  :bordered="false" 
                  class="detail-section">
            <n-alert type="error" :show-icon="false">
              {{ currentLog.message }}
            </n-alert>
          </n-card>

          <!-- 配置变更内容 -->
          <n-card v-if="currentLog.operationType === 'CONFIG_UPDATE' && (currentLog.beforeData || currentLog.afterData)" 
                  title="配置变更" 
                  :bordered="false" 
                  class="detail-section">
            <div class="config-diff">
              <div class="config-column">
                <div class="config-header">变更前</div>
                <n-code 
                  v-if="currentLog.beforeData" 
                  :code="formatJson(currentLog.beforeData)" 
                  language="json" 
                  :word-wrap="true"
                />
                <div v-else class="empty-config">无数据</div>
              </div>
              <div class="config-column">
                <div class="config-header">变更后</div>
                <n-code 
                  v-if="currentLog.afterData" 
                  :code="formatJson(currentLog.afterData)" 
                  language="json"
                  :word-wrap="true"
                />
                <div v-else class="empty-config">无数据</div>
              </div>
            </div>
          </n-card>
        </div>

        <template #footer>
          <n-space justify="end">
            <n-button @click="showDetailDrawer = false">关闭</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onMounted } from 'vue'
import { useMessage } from '@keqi.gress/plugin-bridge'
import {
  NSpace,
  NButton,
  NDataTable,
  NPagination,
  NTag,
  NIcon,
  NDrawer,
  NDrawerContent,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NCard,
  NAlert,
  NCode,
  type DataTableColumns
} from 'naive-ui'
import { useIcon } from '@keqi.gress/plugin-bridge'
import { applicationApi } from '../api/application'

// 图标
const Refresh = useIcon('RefreshOutline')
const Eye = useIcon('EyeOutline')
const CheckmarkCircle = useIcon('CheckmarkCircleOutline')
const CloseCircle = useIcon('CloseCircleOutline')

// 操作日志类型定义
interface OperationLog {
  id: number
  applicationId: number
  applicationCode: string
  applicationName: string
  pluginId: string
  operationType: string
  operationTypeText: string
  operationDesc: string
  operatorId: string
  operatorName: string
  status: string
  statusText: string
  message: string
  beforeData: string
  afterData: string
  duration: number
  clientIp: string
  createTime: string
}

// FilterFieldConfig 类型定义
export type FilterFieldType = 'input' | 'select' | 'date' | 'date-range'

export type FilterFieldConfig = {
  key: string
  label?: string
  type?: FilterFieldType
  placeholder?: string
  options?: Array<{ label: string; value: unknown }>
  clearable?: boolean
  span?: number
  slotName?: string
  componentProps?: Record<string, unknown>
}

// Message
const message = useMessage()

// State
const loading = ref(false)
const refreshLoading = ref(false)
const tableData = ref<OperationLog[]>([])
const showDetailDrawer = ref(false)
const currentLog = ref<OperationLog | null>(null)

// 过滤器状态
const showAdvanced = ref(false)
const filters = ref({
  operationType: null as string | null,
  status: null as string | null,
  applicationName: '',
  operatorName: ''
})

// Pagination
const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  pageSizes: [10, 20, 50, 100]
})

const handlePageChange = (page: number) => {
  pagination.page = page
  loadData()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize
  pagination.page = 1
  loadData()
}

// 过滤字段配置
const basicFields: FilterFieldConfig[] = [
  {
    key: 'applicationName',
    label: '应用名称',
    type: 'input',
    placeholder: '搜索应用名称'
  },
  {
    key: 'operationType',
    label: '操作类型',
    type: 'select',
    placeholder: '请选择操作类型',
    options: [
      { label: '全部', value: null },
      { label: '启动', value: 'START' },
      { label: '停止', value: 'STOP' },
      { label: '重启', value: 'RESTART' },
      { label: '安装', value: 'INSTALL' },
      { label: '卸载', value: 'UNINSTALL' },
      { label: '升级', value: 'UPGRADE' },
      { label: '降级', value: 'ROLLBACK' },
      { label: '配置更新', value: 'CONFIG_UPDATE' }
    ]
  },
  {
    key: 'status',
    label: '操作结果',
    type: 'select',
    placeholder: '请选择操作结果',
    options: [
      { label: '全部', value: null },
      { label: '成功', value: 'SUCCESS' },
      { label: '失败', value: 'FAIL' }
    ]
  },
  {
    key: 'operatorName',
    label: '操作人',
    type: 'input',
    placeholder: '搜索操作人'
  }
]

const advancedFields: FilterFieldConfig[] = []

// Table Columns
const columns: DataTableColumns<OperationLog> = [
  {
    title: 'ID',
    key: 'id',
    width: 80
  },
  {
    title: '应用名称',
    key: 'applicationName',
    width: 150,
    ellipsis: {
      tooltip: true
    }
  },
  {
    title: '操作类型',
    key: 'operationType',
    width: 120,
    render: (row: OperationLog) => h(
      NTag,
      { 
        type: getOperationTypeColor(row.operationType), 
        size: 'small'
      },
      { default: () => row.operationTypeText }
    )
  },
  {
    title: '操作描述',
    key: 'operationDesc',
    width: 200,
    ellipsis: {
      tooltip: true
    }
  },
  {
    title: '操作人',
    key: 'operatorName',
    width: 120,
    ellipsis: {
      tooltip: true
    },
    render: (row: OperationLog) => row.operatorName || row.operatorId
  },
  {
    title: '结果',
    key: 'status',
    width: 80,
    render: (row: OperationLog) => h(
      NTag,
      { 
        type: row.status === 'SUCCESS' ? 'success' : 'error',
        size: 'small'
      },
      { 
        default: () => row.statusText,
        icon: () => h(NIcon, { 
          component: row.status === 'SUCCESS' ? CheckmarkCircle : CloseCircle 
        })
      }
    )
  },
  {
    title: '耗时',
    key: 'duration',
    width: 100,
    render: (row: OperationLog) => row.duration ? `${row.duration} ms` : '-'
  },
  {
    title: '操作时间',
    key: 'createTime',
    width: 180,
    render: (row: OperationLog) => formatDateTime(row.createTime)
  },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    fixed: 'right',
    render: (row: OperationLog) => h(
      NButton,
      {
        size: 'small',
        onClick: () => handleViewDetail(row)
      },
      {
        default: () => '查看详情',
        icon: () => h(NIcon, { component: Eye })
      }
    )
  }
]

// Methods
const loadData = async () => {
  loading.value = true
  refreshLoading.value = true
  try {
    const params: any = {
      page: pagination.page,
      size: pagination.pageSize
    }
    
    // 只添加有效的过滤参数
    if (filters.value.operationType) {
      params.operationType = filters.value.operationType
    }
    if (filters.value.status) {
      params.status = filters.value.status
    }
    if (filters.value.applicationName && filters.value.applicationName.trim()) {
      params.applicationName = filters.value.applicationName.trim()
    }
    if (filters.value.operatorName && filters.value.operatorName.trim()) {
      params.operatorName = filters.value.operatorName.trim()
    }

    const response = await applicationApi.getAllOperationLogs(params)
    
    if (response) {
      tableData.value = response.items
      pagination.itemCount = response.total
    } else {
      message.error('加载操作日志失败')
    }

  } finally {
    loading.value = false
    refreshLoading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  filters.value.operationType = null
  filters.value.status = null
  filters.value.applicationName = ''
  filters.value.operatorName = ''
  pagination.page = 1
  loadData()
}

const handleViewDetail = (log: OperationLog) => {
  currentLog.value = log
  showDetailDrawer.value = true
}

// Helper Functions
const getOperationTypeColor = (type: string): 'default' | 'success' | 'warning' | 'error' | 'info' => {
  if (type === 'START' || type === 'INSTALL' || type === 'UPGRADE') {
    return 'success'
  }
  if (type === 'STOP' || type === 'UNINSTALL' || type === 'ROLLBACK') {
    return 'error'
  }
  if (type === 'RESTART' || type === 'CONFIG_UPDATE') {
    return 'info'
  }
  return 'default'
}

const formatDateTime = (dateTime: string): string => {
  if (!dateTime) return '-'
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const formatJson = (jsonStr: string): string => {
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return jsonStr
  }
}

// Lifecycle
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.operation-log-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}

.page-header-wrapper {
  background: white;
  border-bottom: 1px solid #e8e8e8;
}

.page-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  overflow: auto;
}

.table-container {
  flex: 1;
  background: white;
  border-radius: 8px;
  padding: 16px;
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.log-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section {
  margin-bottom: 16px;
}

.config-diff {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.config-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-header {
  font-weight: 600;
  font-size: 14px;
  color: #333;
  padding: 8px 0;
  border-bottom: 2px solid #e8e8e8;
}

.empty-config {
  padding: 16px;
  text-align: center;
  color: #999;
  background: #f5f5f5;
  border-radius: 4px;
}
</style>
