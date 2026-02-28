<template>
  <div class="plugin-monitor-history">
    <!-- 时间范围选择器 -->
    <div class="time-range-selector">
      <n-radio-group v-model:value="selectedTimeRange" @update:value="handleTimeRangeChange">
        <n-radio-button value="1h">1小时</n-radio-button>
        <n-radio-button value="24h">24小时</n-radio-button>
        <n-radio-button value="7d">7天</n-radio-button>
      </n-radio-group>
      <n-button @click="refreshHistory" :loading="loading" size="small" class="refresh-btn">
        <template #icon>
          <n-icon>
            <component :is="RefreshOutline" />
          </n-icon>
        </template>
        刷新
      </n-button>
    </div>

    <n-spin :show="loading">
      <div v-if="historyData.length > 0" class="charts-container">
        <!-- 内存使用趋势图 -->
        <n-card title="内存使用趋势" class="chart-card" :bordered="false">
          <v-chart
            class="chart"
            :option="memoryChartOption"
            autoresize
          />
        </n-card>

        <!-- 状态变更时间线 -->
        <n-card title="状态变更时间线" class="chart-card" :bordered="false">
          <v-chart
            class="chart timeline-chart"
            :option="stateTimelineOption"
            autoresize
          />
        </n-card>
      </div>

      <n-empty
        v-else-if="!loading"
        description="暂无历史数据"
        class="empty-state"
      />
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { NCard, NRadioGroup, NRadioButton, NButton, NIcon, NSpin, NEmpty } from 'naive-ui'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, ScatterChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { useIcon } from '@keqi.gress/plugin-bridge'
import { pluginMonitorApi } from '../api/pluginMonitor'
import type { PluginMonitorHistory } from '../types/pluginMonitor'

// 注册 ECharts 组件
use([
  CanvasRenderer,
  LineChart,
  ScatterChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent
])

// 图标
const RefreshOutline = useIcon('RefreshOutline')

// Props
const props = defineProps<{
  pluginId: string
}>()

// 数据状态
const historyData = ref<PluginMonitorHistory[]>([])
const selectedTimeRange = ref<string>('1h')
const loading = ref(false)

// 内存使用趋势图配置
const memoryChartOption = computed(() => {
  const timestamps = historyData.value.map(item => new Date(item.timestamp))
  const memoryValues = historyData.value.map(item => {
    // 转换为 MB
    return item.memoryUsage ? (item.memoryUsage / (1024 * 1024)).toFixed(2) : 0
  })

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const param = params[0]
        const date = new Date(param.axisValue)
        const timeStr = date.toLocaleString('zh-CN', {
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit'
        })
        return `${timeStr}<br/>内存使用: ${param.value} MB`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: timestamps,
      axisLabel: {
        formatter: (value: any) => {
          // value 可能是字符串、数字或 Date 对象
          const date = value instanceof Date ? value : new Date(value)
          return date.toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit'
          })
        }
      }
    },
    yAxis: {
      type: 'value',
      name: '内存 (MB)',
      axisLabel: {
        formatter: '{value} MB'
      }
    },
    dataZoom: [
      {
        type: 'slider',
        start: 0,
        end: 100
      }
    ],
    series: [
      {
        name: '内存使用',
        type: 'line',
        data: memoryValues,
        smooth: true,
        lineStyle: {
          color: '#18a058',
          width: 2
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(24, 160, 88, 0.3)' },
              { offset: 1, color: 'rgba(24, 160, 88, 0.05)' }
            ]
          }
        }
      }
    ]
  }
})

// 状态变更时间线配置
const stateTimelineOption = computed(() => {
  // 提取状态变更点（状态发生变化的数据点）
  const stateChanges: Array<{ time: Date; state: string; index: number }> = []
  
  for (let i = 0; i < historyData.value.length; i++) {
    const current = historyData.value[i]
    const prev = i > 0 ? historyData.value[i - 1] : null
    
    // 如果是第一个点或状态发生变化，记录
    if (!prev || current.state !== prev.state) {
      stateChanges.push({
        time: new Date(current.timestamp),
        state: current.state,
        index: i
      })
    }
  }

  const timestamps = historyData.value.map(item => new Date(item.timestamp))
  const stateValues = historyData.value.map(item => getStateValue(item.state))
  
  // 状态变更点数据
  const changePoints = stateChanges.map(change => ({
    value: [change.time, getStateValue(change.state)],
    itemStyle: {
      color: getStateColor(change.state)
    }
  }))

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const param = params[0]
        const date = new Date(param.axisValue)
        const timeStr = date.toLocaleString('zh-CN', {
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit'
        })
        const state = getStateLabel(param.value)
        return `${timeStr}<br/>状态: ${state}`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: timestamps,
      axisLabel: {
        formatter: (value: any) => {
          // value 可能是字符串、数字或 Date 对象
          const date = value instanceof Date ? value : new Date(value)
          return date.toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit'
          })
        }
      }
    },
    yAxis: {
      type: 'value',
      name: '状态',
      min: 0,
      max: 4,
      interval: 1,
      axisLabel: {
        formatter: (value: number) => getStateLabel(value)
      }
    },
    dataZoom: [
      {
        type: 'slider',
        start: 0,
        end: 100
      }
    ],
    series: [
      {
        name: '状态',
        type: 'line',
        data: stateValues,
        step: 'end',
        lineStyle: {
          color: '#2080f0',
          width: 2
        }
      },
      {
        name: '状态变更',
        type: 'scatter',
        data: changePoints,
        symbolSize: 10,
        z: 10
      }
    ]
  }
})

// 状态映射函数
function getStateValue(state: string): number {
  const stateMap: Record<string, number> = {
    'STARTED': 3,
    'STARTING': 2,
    'STOPPED': 1,
    'STOPPING': 2,
    'CREATED': 0,
    'DISABLED': 0
  }
  return stateMap[state] ?? 0
}

function getStateLabel(value: number): string {
  const labelMap: Record<number, string> = {
    0: '未启动',
    1: '已停止',
    2: '变更中',
    3: '运行中'
  }
  return labelMap[value] ?? '未知'
}

function getStateColor(state: string): string {
  const colorMap: Record<string, string> = {
    'STARTED': '#18a058',
    'STARTING': '#f0a020',
    'STOPPED': '#d03050',
    'STOPPING': '#f0a020',
    'CREATED': '#909399',
    'DISABLED': '#909399'
  }
  return colorMap[state] ?? '#909399'
}

// 加载历史数据
async function loadHistory() {
  if (!props.pluginId) {
    return
  }

  loading.value = true
  try {
    historyData.value = await pluginMonitorApi.getHistory(
      props.pluginId,
      selectedTimeRange.value
    )
    
    // 按时间戳排序
    historyData.value.sort((a, b) => a.timestamp - b.timestamp)
  } catch (error) {
    console.error('加载历史数据失败:', error)
    historyData.value = []
  } finally {
    loading.value = false
  }
}

// 刷新历史数据
function refreshHistory() {
  loadHistory()
}

// 时间范围变更处理
function handleTimeRangeChange() {
  loadHistory()
}

// 监听 pluginId 变化
watch(() => props.pluginId, () => {
  loadHistory()
}, { immediate: true })
</script>

<style scoped lang="scss">
.plugin-monitor-history {
  .time-range-selector {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .refresh-btn {
      margin-left: 12px;
    }
  }

  .charts-container {
    .chart-card {
      margin-bottom: 16px;

      &:last-child {
        margin-bottom: 0;
      }

      .chart {
        height: 300px;
        width: 100%;
      }

      .timeline-chart {
        height: 250px;
      }
    }
  }

  .empty-state {
    padding: 60px 0;
  }
}

/* 平板端样式 (768px - 1024px) */
@media screen and (max-width: 1024px) {
  .plugin-monitor-history {
    .time-range-selector {
      margin-bottom: 12px;

      :deep(.n-radio-group) {
        .n-radio-button {
          font-size: 13px;
        }
      }
    }

    .charts-container {
      .chart-card {
        margin-bottom: 12px;

        .chart {
          height: 280px;
        }

        .timeline-chart {
          height: 230px;
        }
      }
    }
  }
}

/* 移动端样式 (< 768px) */
@media screen and (max-width: 768px) {
  .plugin-monitor-history {
    .time-range-selector {
      flex-direction: column;
      align-items: stretch;
      gap: 10px;
      margin-bottom: 12px;

      .refresh-btn {
        margin-left: 0;
        width: 100%;
      }

      :deep(.n-radio-group) {
        width: 100%;
        display: flex;

        .n-radio-button {
          flex: 1;
          font-size: 12px;
        }
      }
    }

    .charts-container {
      .chart-card {
        margin-bottom: 12px;

        :deep(.n-card__header) {
          padding: 12px;
          font-size: 15px;
        }

        :deep(.n-card__content) {
          padding: 12px;
        }

        .chart {
          height: 250px;
        }

        .timeline-chart {
          height: 200px;
        }
      }
    }

    .empty-state {
      padding: 40px 0;
    }
  }
}

/* 小屏移动端样式 (< 480px) */
@media screen and (max-width: 480px) {
  .plugin-monitor-history {
    .time-range-selector {
      :deep(.n-radio-group) {
        .n-radio-button {
          font-size: 11px;
          padding: 0 8px;
        }
      }
    }

    .charts-container {
      .chart-card {
        margin-bottom: 10px;

        :deep(.n-card__header) {
          padding: 10px;
          font-size: 14px;
        }

        :deep(.n-card__content) {
          padding: 10px;
        }

        .chart {
          height: 220px;
        }

        .timeline-chart {
          height: 180px;
        }
      }
    }

    .empty-state {
      padding: 30px 0;
      :deep(.n-empty__description) {
        font-size: 13px;
      }
    }
  }
}
</style>
