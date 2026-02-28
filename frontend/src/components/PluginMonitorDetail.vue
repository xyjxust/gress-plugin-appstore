<template>
  <div class="plugin-monitor-detail">
    <n-spin :show="loading">
      <div v-if="detail">
        <!-- 错误信息提示 -->
        <n-alert
          v-if="detail.status?.hasError && detail.status?.errorMessage"
          type="error"
          :show-icon="true"
          class="error-alert"
        >
          <template #header>错误信息</template>
          {{ detail.status.errorMessage }}
        </n-alert>

        <!-- 标签页 -->
        <n-tabs type="line" animated>
          <!-- 详情标签页 -->
          <n-tab-pane name="detail" tab="详细信息">
            <!-- 基本信息 -->
            <n-card title="基本信息" class="info-card" :bordered="false">
          <n-descriptions :column="1" label-placement="left" bordered>
            <n-descriptions-item label="插件ID">
              {{ detail.status?.pluginId }}
            </n-descriptions-item>
            <n-descriptions-item label="插件名称">
              {{ detail.status?.pluginName }}
            </n-descriptions-item>
            <n-descriptions-item label="版本">
              {{ detail.status?.pluginVersion || '未知' }}
            </n-descriptions-item>
            <n-descriptions-item label="运行状态">
              <n-tag :type="getStateType(detail.status?.state)" size="small">
                {{ detail.status?.state }}
              </n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="加载状态">
              <n-tag :type="detail.status?.loaded ? 'success' : 'error'" size="small">
                {{ detail.status?.loaded ? '已加载' : '未加载' }}
              </n-tag>
            </n-descriptions-item>
          </n-descriptions>
        </n-card>

        <!-- 运行时信息 -->
        <n-card title="运行时信息" class="info-card" :bordered="false">
          <n-descriptions :column="1" label-placement="left" bordered>
            <n-descriptions-item label="启动时间">
              {{ formatStartTime(detail.status?.startTime) }}
            </n-descriptions-item>
            <n-descriptions-item label="运行时长">
              {{ formatUptime(detail.status?.uptime) }}
            </n-descriptions-item>
          </n-descriptions>
        </n-card>

        <!-- 内存信息 -->
        <n-card title="内存信息" class="info-card" :bordered="false">
          <n-descriptions :column="1" label-placement="left" bordered>
            <n-descriptions-item label="插件内存使用">
              <span :class="{ 'memory-warning': detail.status?.isMemoryWarning }">
                {{ detail.memoryInfo?.formattedMemory || '不可用' }}
                <n-icon v-if="detail.status?.isMemoryWarning" size="16" color="#f0a020">
                  <component :is="WarningOutline" />
                </n-icon>
              </span>
            </n-descriptions-item>
            <n-descriptions-item label="JVM 总内存">
              {{ formatMemory(detail.memoryInfo?.totalJvmMemory) }}
            </n-descriptions-item>
            <n-descriptions-item label="JVM 空闲内存">
              {{ formatMemory(detail.memoryInfo?.freeJvmMemory) }}
            </n-descriptions-item>
            <n-descriptions-item label="JVM 最大内存">
              {{ formatMemory(detail.memoryInfo?.maxJvmMemory) }}
            </n-descriptions-item>
          </n-descriptions>
        </n-card>

        <!-- 元数据信息 -->
        <n-card title="元数据" class="info-card" :bordered="false">
          <n-descriptions :column="1" label-placement="left" bordered>
            <n-descriptions-item label="作者">
              {{ detail.metadata?.author || '未知' }}
            </n-descriptions-item>
            <n-descriptions-item label="描述">
              {{ detail.metadata?.description || '无描述' }}
            </n-descriptions-item>
            <n-descriptions-item label="主页">
              <a
                v-if="detail.metadata?.homepage"
                :href="detail.metadata.homepage"
                target="_blank"
                rel="noopener noreferrer"
              >
                {{ detail.metadata.homepage }}
              </a>
              <span v-else>无</span>
            </n-descriptions-item>
          </n-descriptions>
        </n-card>

        <!-- 依赖信息 -->
        <n-card title="依赖信息" class="info-card" :bordered="false">
          <div v-if="hasDependencies">
            <n-tag
              v-for="(dep, index) in getDependencies()"
              :key="index"
              type="info"
              size="small"
              class="dependency-tag"
            >
              {{ dep }}
            </n-tag>
          </div>
          <n-empty v-else description="无依赖" size="small" />
        </n-card>

        <!-- 配置信息 -->
        <n-card title="配置信息" class="info-card" :bordered="false">
          <div v-if="hasConfiguration">
            <n-code
              :code="formatConfiguration()"
              language="json"
              :word-wrap="true"
            />
          </div>
          <n-empty v-else description="无配置" size="small" />
        </n-card>

        <!-- 类加载器信息 -->
        <n-card title="类加载器信息" class="info-card" :bordered="false">
          <n-descriptions :column="1" label-placement="left" bordered>
            <n-descriptions-item label="类加载器">
              {{ detail.classLoaderInfo?.className || '不可用' }}
            </n-descriptions-item>
            <n-descriptions-item label="父类加载器">
              {{ detail.classLoaderInfo?.parentClassName || '不可用' }}
            </n-descriptions-item>
          </n-descriptions>
        </n-card>
          </n-tab-pane>

          <!-- 历史数据标签页 -->
          <n-tab-pane name="history" tab="历史数据">
            <plugin-monitor-history :plugin-id="pluginId" />
          </n-tab-pane>
        </n-tabs>
      </div>

      <n-empty v-else-if="!loading" description="无法加载插件详情" />
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { NCard, NDescriptions, NDescriptionsItem, NTag, NAlert, NSpin, NEmpty, NCode, NIcon, NTabs, NTabPane } from 'naive-ui'
import { useIcon } from '@keqi.gress/plugin-bridge'
import { pluginMonitorApi } from '../api/pluginMonitor'
import type { PluginMonitorDetail } from '../types/pluginMonitor'
import PluginMonitorHistory from './PluginMonitorHistory.vue'

// 图标
const WarningOutline = useIcon('WarningOutline')

// Props
const props = defineProps<{
  pluginId: string
}>()

// 数据状态
const detail = ref<PluginMonitorDetail | null>(null)
const loading = ref(false)

// 计算属性
const hasDependencies = computed(() => {
  return detail.value?.metadata?.dependencies && 
         Array.isArray(detail.value.metadata.dependencies) &&
         detail.value.metadata.dependencies.length > 0
})

const hasConfiguration = computed(() => {
  return detail.value?.configuration && 
         Object.keys(detail.value.configuration).length > 0
})

// 获取依赖列表
function getDependencies(): string[] {
  if (!detail.value?.metadata?.dependencies) {
    return []
  }
  const deps = detail.value.metadata.dependencies
  if (Array.isArray(deps)) {
    return deps
  }
  return []
}

// 格式化配置信息
function formatConfiguration(): string {
  if (!detail.value?.configuration) {
    return '{}'
  }
  try {
    return JSON.stringify(detail.value.configuration, null, 2)
  } catch (e) {
    return '{}'
  }
}

// 格式化内存大小
function formatMemory(bytes?: number): string {
  if (!bytes || bytes === 0) {
    return '不可用'
  }
  
  if (bytes < 1024) {
    return `${bytes} B`
  } else if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(2)} KB`
  } else if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  } else {
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
  }
}

// 格式化启动时间
function formatStartTime(timestamp?: number): string {
  if (!timestamp) {
    return '未知'
  }
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 格式化运行时长
function formatUptime(uptime?: number): string {
  if (!uptime || uptime === 0) {
    return '未运行'
  }
  
  const seconds = Math.floor(uptime / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  
  if (days > 0) {
    return `${days} 天 ${hours % 24} 小时`
  } else if (hours > 0) {
    return `${hours} 小时 ${minutes % 60} 分钟`
  } else if (minutes > 0) {
    return `${minutes} 分钟 ${seconds % 60} 秒`
  } else {
    return `${seconds} 秒`
  }
}

// 获取状态类型
function getStateType(state?: string): 'success' | 'error' | 'warning' | 'info' {
  if (!state) return 'info'
  
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

// 加载详情数据
async function loadDetail() {
  if (!props.pluginId) {
    return
  }
  
  loading.value = true
  try {
    detail.value = await pluginMonitorApi.getDetail(props.pluginId)
  } catch (error) {
    console.error('加载插件详情失败:', error)
    detail.value = null
  } finally {
    loading.value = false
  }
}

// 监听 pluginId 变化
watch(() => props.pluginId, () => {
  loadDetail()
}, { immediate: true })
</script>

<style scoped lang="scss">
.plugin-monitor-detail {
  .error-alert {
    margin-bottom: 16px;
  }

  .info-card {
    margin-bottom: 16px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .memory-warning {
    color: #f0a020;
    font-weight: bold;
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  .dependency-tag {
    margin-right: 8px;
    margin-bottom: 8px;
  }
}

/* 平板端样式 (768px - 1024px) */
@media screen and (max-width: 1024px) {
  .plugin-monitor-detail {
    .info-card {
      margin-bottom: 12px;
    }

    :deep(.n-descriptions) {
      .n-descriptions-table-wrapper {
        font-size: 14px;
      }
    }
  }
}

/* 移动端样式 (< 768px) */
@media screen and (max-width: 768px) {
  .plugin-monitor-detail {
    .error-alert {
      margin-bottom: 12px;
      :deep(.n-alert__content) {
        font-size: 13px;
      }
    }

    .info-card {
      margin-bottom: 12px;

      :deep(.n-card__header) {
        padding: 12px;
        font-size: 15px;
      }

      :deep(.n-card__content) {
        padding: 12px;
      }
    }

    :deep(.n-descriptions) {
      .n-descriptions-table-wrapper {
        font-size: 13px;
      }

      .n-descriptions-table-content__label {
        padding: 8px;
      }

      .n-descriptions-table-content__content {
        padding: 8px;
      }
    }

    :deep(.n-tabs) {
      .n-tabs-nav {
        font-size: 14px;
      }
    }

    .dependency-tag {
      margin-right: 6px;
      margin-bottom: 6px;
      font-size: 12px;
    }

    :deep(.n-code) {
      font-size: 11px;
    }
  }
}

/* 小屏移动端样式 (< 480px) */
@media screen and (max-width: 480px) {
  .plugin-monitor-detail {
    .info-card {
      margin-bottom: 10px;

      :deep(.n-card__header) {
        padding: 10px;
        font-size: 14px;
      }

      :deep(.n-card__content) {
        padding: 10px;
      }
    }

    :deep(.n-descriptions) {
      .n-descriptions-table-wrapper {
        font-size: 12px;
      }

      .n-descriptions-table-content__label {
        padding: 6px;
        min-width: 80px;
      }

      .n-descriptions-table-content__content {
        padding: 6px;
      }
    }

    .dependency-tag {
      font-size: 11px;
    }

    :deep(.n-code) {
      font-size: 10px;
    }
  }
}
</style>
