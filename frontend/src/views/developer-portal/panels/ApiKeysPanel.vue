<template>
  <div class="panel">
    <div class="panel__head">
      <div>
        <h2 class="panel__title">API 密钥</h2>
        <p class="panel__desc">用于 CLI / CI 拉取构建产物。以下为模拟数据，点击可复制。</p>
      </div>
      <n-button type="primary" @click="rotate">模拟轮换</n-button>
    </div>

    <n-alert type="warning" style="margin-bottom: 16px" title="安全提示">
      勿将密钥提交到仓库。生产环境请接入与 IAM 联动的短期凭证策略。
    </n-alert>

    <n-card size="small" class="key-card">
      <div class="key-row">
        <span class="key-label">Key ID</span>
        <code class="key-val">{{ keyId }}</code>
        <n-button size="tiny" quaternary @click="copy(keyId)">复制</n-button>
      </div>
      <n-divider style="margin: 12px 0" />
      <div class="key-row">
        <span class="key-label">Secret</span>
        <code class="key-val key-val--secret">{{ masked }}</code>
        <n-button size="tiny" type="primary" quaternary @click="copy(secret)">复制明文</n-button>
      </div>
      <p class="key-hint">明文仅用于 Demo；真实环境由服务端加密存储，按需 reveal。</p>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { NAlert, NButton, NCard, NDivider, useMessage } from 'naive-ui'

const message = useMessage()
const keyId = ref('k_demo_' + 'a1b2c3d4')
const secret = ref('s_demo_' + '7f91e2c4-secure-token-placeholder')

const masked = computed(() => {
  const s = secret.value
  if (s.length <= 12) return '••••••••'
  return s.slice(0, 8) + '…' + s.slice(-4)
})

async function copy(text: string) {
  try {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text)
    else throw new Error('no clipboard')
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败（浏览器限制）')
  }
}

function rotate() {
  const hex = Math.random().toString(16).slice(2, 10)
  secret.value = 's_demo_rotated_' + hex
  message.success('已生成新的模拟 Secret（仅前端）')
}
</script>

<style scoped lang="scss">
@import '../../../styles/developer-portal-tokens.scss';

.panel__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 8px;
}

.panel__title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--asdp-ink);
}

.panel__desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--asdp-ink-muted);
  line-height: 1.5;
}

.key-card {
  border-radius: var(--asdp-radius) !important;
  max-width: 720px;
}

.key-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.key-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--asdp-ink-muted);
  width: 64px;
  font-family: var(--asdp-font-mono);
}

.key-val {
  flex: 1;
  min-width: 0;
  font-family: var(--asdp-font-mono);
  font-size: 13px;
  padding: 6px 10px;
  background: #f4f6f9;
  border-radius: 6px;
  border: 1px solid var(--asdp-line);
  word-break: break-all;
}

.key-val--secret {
  letter-spacing: 0.04em;
}

.key-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--asdp-ink-muted);
}
</style>
