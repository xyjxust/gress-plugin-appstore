<template>
  <div class="panel">
    <div class="panel__intro">
      <h2 class="panel__title">工作台</h2>
      <p class="panel__desc">本区域为 <strong>交互 Demo</strong>：后续可对接真实提交、审核流与下载统计。</p>
    </div>
    <div class="stat-row">
      <n-card v-for="s in stats" :key="s.label" size="small" class="stat-card">
        <n-statistic :label="s.label" :value="s.value" />
      </n-card>
    </div>
    <n-card title="下一步" size="small" class="next-card" style="margin-top: 20px">
      <n-space vertical>
        <n-alert type="info" title="集成提示">
          上传包、元数据与 API 密钥将走独立服务；当前页面仅模拟交互与文案层级。
        </n-alert>
        <n-button type="primary" dashed @click="emitGo">前往上传向导</n-button>
      </n-space>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { NAlert, NButton, NCard, NSpace, NStatistic, useMessage } from 'naive-ui'

const stats = [
  { label: '在架插件', value: 2 },
  { label: '审核中', value: 1 },
  { label: '本月下载', value: 18420 },
  { label: '待处理事项', value: 1 }
]

const message = useMessage()

function emitGo() {
  message.info('请从左侧菜单进入「上传新版本」')
}
</script>

<style scoped lang="scss">
@import '../../../styles/developer-portal-tokens.scss';

.panel__intro {
  margin-bottom: 20px;
}

.panel__title {
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--asdp-ink);
  margin-bottom: 8px;
}

.panel__desc {
  font-size: 14px;
  color: var(--asdp-ink-muted);
  line-height: 1.6;
}

.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.stat-row .stat-card {
  flex: 1 1 200px;
  min-width: 160px;
  border-radius: var(--asdp-radius) !important;
  box-shadow: var(--asdp-shadow) !important;
}

.next-card {
  border-radius: var(--asdp-radius) !important;
}
</style>
