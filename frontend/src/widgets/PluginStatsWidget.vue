<template>
  <div class="plugin-stats-widget">
    <n-spin :show="loading">
      <div class="stats-grid">
        <div class="stat-item">
          <div class="stat-value">{{ overview.totalPlugins }}</div>
          <div class="stat-label">已安装</div>
        </div>
        <div class="stat-item running">
          <div class="stat-value">{{ overview.runningPlugins }}</div>
          <div class="stat-label">运行中</div>
        </div>
        <div class="stat-item error" :class="{ 'has-error': overview.errorPlugins > 0 }">
          <div class="stat-value">{{ overview.errorPlugins }}</div>
          <div class="stat-label">异常</div>
        </div>
      </div>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { pluginMonitorApi } from '../api/pluginMonitor'
import type { MonitorOverview } from '../types/pluginMonitor'

const loading = ref(false)
const overview = ref<MonitorOverview>({
  totalPlugins: 0,
  runningPlugins: 0,
  stoppedPlugins: 0,
  errorPlugins: 0,
  totalMemoryUsage: 0
})

let timer: number | null = null

async function fetchOverview() {
  try {
    loading.value = true
    overview.value = await pluginMonitorApi.getOverview()
  } catch (e) {
    console.error('[PluginStatsWidget] Failed to fetch overview', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchOverview()
  timer = window.setInterval(fetchOverview, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.plugin-stats-widget {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
}

.stats-grid {
  display: flex;
  gap: 16px;
  width: 100%;
  justify-content: space-around;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  color: var(--n-text-color);
}

.stat-label {
  font-size: 12px;
  color: var(--n-text-color-3);
}

.running .stat-value {
  color: #18a058;
}

.error .stat-value {
  color: var(--n-text-color-3);
}

.error.has-error .stat-value {
  color: #d03050;
}
</style>

