<template>
  <div class="plugin-monitor-dashboard">
    <PageHeader title="插件监控" subtitle="实时监控插件运行状态、内存使用和性能指标">
      <template #actions>
        <n-space align="center">
          <n-switch v-model:value="autoRefresh" @update:value="toggleAutoRefresh">
            <template #checked>自动刷新</template>
            <template #unchecked>自动刷新</template>
          </n-switch>
          <span v-if="autoRefresh" class="refresh-interval-text">
            每 {{ refreshInterval / 1000 }}s
          </span>
          <n-button :loading="loading" @click="refreshData">刷新</n-button>
        </n-space>
      </template>
    </PageHeader>

    <div class="page-content">
      <!-- 概览卡片 -->
      <div class="overview-cards">
        <n-card class="overview-card">
          <n-statistic label="总插件数" :value="overview.totalPlugins" />
        </n-card>
        <n-card class="overview-card">
          <n-statistic label="运行中" :value="overview.runningPlugins">
            <template #suffix>
              <n-icon size="20" color="#18a058">
                <component :is="CheckmarkCircleOutline" />
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
        <n-card class="overview-card">
          <n-statistic label="已停止" :value="overview.stoppedPlugins">
            <template #suffix>
              <n-icon size="20" color="#d03050">
                <component :is="StopCircleOutline" />
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
        <n-card class="overview-card">
          <n-statistic label="异常" :value="overview.errorPlugins">
            <template #suffix>
              <n-icon size="20" color="#f0a020">
                <component :is="WarningOutline" />
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
      </div>

      <!-- 异常插件提示 -->
      <n-alert
        v-if="overview.errorPlugins > 0"
        type="warning"
        closable
        class="error-alert"
      >
        <template #icon>
          <n-icon><component :is="WarningOutline" /></n-icon>
        </template>
        <template #header>
          发现 {{ overview.errorPlugins }} 个异常插件
        </template>
        请检查插件列表中标记为异常的插件，点击详情查看错误信息
      </n-alert>

      <!-- 插件列表表格 -->
      <n-card>
        <n-data-table
          :columns="columns"
          :data="pluginList"
          :loading="loading"
          :pagination="pagination"
          :row-key="(row: PluginMonitorStatus) => row.pluginId"
          striped
        />
      </n-card>
    </div>

    <!-- 详情抽屉 -->
    <n-drawer
      v-model:show="showDetail"
      :width="drawerWidth"
      placement="right"
    >
      <n-drawer-content title="插件详情" closable>
        <PluginMonitorDetail v-if="selectedPluginId" :plugin-id="selectedPluginId" />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, h } from 'vue'
import { NButton, NTag, NIcon, NAlert } from 'naive-ui'
import { useIcon } from '@keqi.gress/plugin-bridge'
import { pluginMonitorApi } from '../api/pluginMonitor'
import PluginMonitorDetail from '../components/PluginMonitorDetail.vue'
import type { 
  PluginMonitorStatus, 
  MonitorOverview 
} from '../types/pluginMonitor'

// 图标
const CheckmarkCircleOutline = useIcon('CheckmarkCircleOutline')
const StopCircleOutline = useIcon('StopCircleOutline')
const WarningOutline = useIcon('WarningOutline')

// 数据状态
const pluginList = ref<PluginMonitorStatus[]>([])
const overview = ref<MonitorOverview>({
  totalPlugins: 0,
  runningPlugins: 0,
  stoppedPlugins: 0,
  errorPlugins: 0,
  totalMemoryUsage: 0
})
const loading = ref(false)
const autoRefresh = ref(false)
const refreshInterval = ref(5000) // 默认 5 秒
let refreshTimer: number | null = null

// 详情抽屉
const showDetail = ref(false)
const selectedPluginId = ref<string>('')

// 响应式抽屉宽度
const drawerWidth = ref(600)

// 根据窗口大小调整抽屉宽度
function updateDrawerWidth() {
  const width = window.innerWidth
  if (width < 768) {
    drawerWidth.value = width // 移动端全屏
  } else if (width < 1024) {
    drawerWidth.value = Math.min(500, width * 0.8) // 平板端 80%
  } else {
    drawerWidth.value = 600 // 桌面端固定宽度
  }
}

// 分页
const pagination = ref({
  page: 1,
  pageSize: 10,
  showSizePicker: true,
  pageSizes: [10, 20, 50, 100],
  onChange: (page: number) => {
    pagination.value.page = page
  },
  onUpdatePageSize: (pageSize: number) => {
    pagination.value.pageSize = pageSize
    pagination.value.page = 1
  }
})

// 渲染函数：插件ID（带错误标识）
function renderPluginId(row: PluginMonitorStatus) {
  if (row.hasError) {
    return h(
      'span',
      {
        style: {
          display: 'flex',
          alignItems: 'center',
          gap: '6px'
        }
      },
      [
        h(NIcon, { size: 16, color: '#d03050' }, { default: () => h(WarningOutline) }),
        row.pluginId
      ]
    )
  }
  return h('span', row.pluginId)
}

// 渲染函数：运行状态
function renderState(row: PluginMonitorStatus) {
  return h(
    NTag,
    {
      type: getStateType(row.state),
      size: 'small'
    },
    { default: () => row.state }
  )
}

// 渲染函数：加载状态
function renderLoaded(row: PluginMonitorStatus) {
  return h(
    NTag,
    {
      type: row.loaded ? 'success' : 'error',
      size: 'small'
    },
    { default: () => (row.loaded ? '已加载' : '未加载') }
  )
}

// 渲染函数：内存使用（带告警高亮）
function renderMemory(row: PluginMonitorStatus) {
  const memory = row.memoryInfo?.formattedMemory || '不可用'
  const isWarning = row.isMemoryWarning || false
  
  if (isWarning) {
    return h(
      'span',
      {
        style: {
          color: '#f0a020',
          fontWeight: 'bold',
          display: 'flex',
          alignItems: 'center',
          gap: '4px'
        }
      },
      [
        h(NIcon, { size: 16 }, { default: () => h(WarningOutline) }),
        memory
      ]
    )
  }
  
  return h('span', memory)
}

// 渲染函数：操作按钮
function renderActions(row: PluginMonitorStatus) {
  return h(
    NButton,
    {
      text: true,
      type: 'primary',
      size: 'small',
      onClick: () => handleViewDetail(row)
    },
    { default: () => '详情' }
  )
}

// 表格列定义
const columns = [
  {
    title: '插件ID',
    key: 'pluginId',
    width: 200,
    render: renderPluginId
  },
  {
    title: '插件名称',
    key: 'pluginName',
    width: 150
  },
  {
    title: '版本',
    key: 'pluginVersion',
    width: 100
  },
  {
    title: '运行状态',
    key: 'state',
    width: 120,
    render: renderState
  },
  {
    title: '加载状态',
    key: 'loaded',
    width: 120,
    render: renderLoaded
  },
  {
    title: '内存使用',
    key: 'memoryInfo',
    width: 120,
    render: renderMemory
  },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: renderActions
  }
]

// 获取状态类型
function getStateType(state: string): 'success' | 'error' | 'warning' | 'info' {
  switch (state) {
    case 'STARTED':
      return 'success'
    case 'STOPPED':
      return 'error'
    case 'STARTING':
    case 'STOPPING':
      return 'warning'
    default:
      return 'info'
  }
}

// 刷新数据
async function refreshData() {
  loading.value = true
  try {
    const [statusData, overviewData] = await Promise.all([
      pluginMonitorApi.getAllStatus(),
      pluginMonitorApi.getOverview()
    ])
    pluginList.value = statusData
    overview.value = overviewData
  } catch (error) {
    console.error('刷新监控数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 切换自动刷新
function toggleAutoRefresh(enabled: boolean) {
  if (enabled) {
    refreshTimer = window.setInterval(refreshData, refreshInterval.value)
  } else {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }
}

// 查看详情
function handleViewDetail(plugin: PluginMonitorStatus) {
  selectedPluginId.value = plugin.pluginId
  showDetail.value = true
}

// 生命周期
onMounted(() => {
  refreshData()
  updateDrawerWidth()
  window.addEventListener('resize', updateDrawerWidth)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  window.removeEventListener('resize', updateDrawerWidth)
})
</script>

<style scoped lang="scss">
.plugin-monitor-dashboard {
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

.refresh-interval-text {
  color: #666;
  font-size: 14px;
  white-space: nowrap;
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.overview-card {
  :deep(.n-statistic) {
    .n-statistic-value {
      font-size: 32px;
      font-weight: 600;
    }
  }
}

.error-alert {
  margin-bottom: 20px;
}

/* 平板端样式 (768px - 1024px) */
@media screen and (max-width: 1024px) {
  .page-content {
    padding: 12px;
  }

  .overview-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    margin-bottom: 16px;
  }

  .overview-card {
    :deep(.n-statistic) {
      .n-statistic-value {
        font-size: 28px;
      }
    }
  }

  .error-alert {
    margin-bottom: 16px;
  }
}

/* 移动端样式 (< 768px) */
@media screen and (max-width: 768px) {
  .page-content {
    padding: 8px;
  }

  .overview-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    margin-bottom: 12px;
  }

  .overview-card {
    :deep(.n-statistic) {
      .n-statistic-value {
        font-size: 24px;
      }
      .n-statistic-label {
        font-size: 12px;
      }
    }
  }

  .refresh-interval-text {
    font-size: 12px;
  }

  .error-alert {
    margin-bottom: 12px;
    :deep(.n-alert__content) {
      font-size: 13px;
    }
  }

  /* 抽屉在移动端全屏显示 */
  :deep(.n-drawer) {
    .n-drawer-body-content-wrapper {
      width: 100% !important;
      max-width: 100% !important;
    }
  }
}

/* 小屏移动端样式 (< 480px) */
@media screen and (max-width: 480px) {
  .page-content {
    padding: 8px;
  }

  .overview-cards {
    grid-template-columns: 1fr;
    gap: 8px;
    margin-bottom: 12px;
  }

  .overview-card {
    :deep(.n-statistic) {
      .n-statistic-value {
        font-size: 28px;
      }
      .n-statistic-label {
        font-size: 13px;
      }
    }
  }
}
</style>
