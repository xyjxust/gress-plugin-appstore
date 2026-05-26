<template>
  <div class="remote-detail-page">
    <div class="remote-detail__top">
      <n-button text @click="goBack">
        <template #icon>
          <n-icon><component :is="ChevronBackOutline" /></n-icon>
        </template>
        返回
      </n-button>
    </div>

    <div v-if="loading" class="loading-state">
      <n-spin size="large" />
    </div>

    <div v-else-if="!app" class="empty-state">
      <div class="empty-state__text">未找到应用信息</div>
    </div>

    <div v-else class="remote-detail__layout">
      <aside class="remote-detail__aside">
        <div class="aside-card">
          <div class="aside-head">
            <div class="aside-icon">
              <img v-if="app.icon?.trim()" :src="app.icon.trim()" alt="" class="aside-icon__img" />
              <n-icon v-else size="28"><component :is="ExtensionPuzzleOutline" /></n-icon>
            </div>
            <div class="aside-title">
              <div class="aside-name">{{ app.applicationName }}</div>
              <div class="aside-sub">{{ app.applicationCode }}</div>
            </div>
          </div>

          <div class="aside-meta">
            <div class="meta-row">
              <div class="meta-label">插件ID</div>
              <div class="meta-value mono">{{ app.pluginId }}</div>
            </div>
            <div class="meta-row">
              <div class="meta-label">版本</div>
              <div class="meta-value mono">{{ app.pluginVersion || '-' }}</div>
            </div>
            <div class="meta-row" v-if="app.localVersion">
              <div class="meta-label">本地版本</div>
              <div class="meta-value mono">{{ app.localVersion }}</div>
            </div>
          </div>

          <div class="aside-actions">
            <n-button
              v-if="app.installStatus === 'NOT_INSTALLED'"
              type="primary"
              :loading="installing"
              @click="installRemote"
            >
              安装
            </n-button>
            <n-button
              v-else-if="app.installStatus === 'UPGRADABLE'"
              type="warning"
              :loading="upgrading"
              @click="upgradeRemote"
            >
              升级
            </n-button>
            <n-button v-else disabled>已安装</n-button>
          </div>
        </div>
      </aside>

      <main class="remote-detail__main">
        <n-card size="small" class="main-card">
          <n-tabs v-model:value="activeTab" type="line">
            <n-tab-pane name="intro" tab="简介">
              <div class="intro">
                <div v-if="app.description" class="intro__desc">{{ app.description }}</div>
                <div v-else class="intro__desc intro__desc--muted">暂无简介</div>
              </div>
            </n-tab-pane>
            <n-tab-pane name="version" tab="版本">
              <div class="version">
                <div class="version__row">
                  <div class="version__label">当前版本</div>
                  <div class="version__value mono">{{ app.pluginVersion || '-' }}</div>
                </div>
                <div class="version__row" v-if="app.localVersion">
                  <div class="version__label">本地版本</div>
                  <div class="version__value mono">{{ app.localVersion }}</div>
                </div>
              </div>
            </n-tab-pane>
          </n-tabs>
        </n-card>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, ref } from 'vue'
import { useIcon, useMessage } from '@keqi.gress/plugin-bridge'
import { applicationApi } from '../api/application'
import type { Application } from '../types/application'

const message = useMessage()

const ChevronBackOutline = useIcon('ChevronBackOutline')
const ExtensionPuzzleOutline = useIcon('ExtensionPuzzleOutline')

const loading = ref(false)
const app = ref<Application | null>(null)
const activeTab = ref<'intro' | 'version'>('intro')
const installing = ref(false)
const upgrading = ref(false)

function getRouteParamPluginId(): string {
  const route = getCurrentInstance()?.appContext.config.globalProperties.$route as any
  const pid = route?.params?.pluginId
  if (typeof pid === 'string' && pid) return pid

  // fallback: /plugins/appstore/applications/remote/:pluginId
  const m = window.location.pathname.match(/\/plugins\/appstore\/applications\/remote\/([^/]+)$/)
  if (m?.[1]) return decodeURIComponent(m[1])
  return ''
}

const pluginId = computed(() => getRouteParamPluginId())

async function load() {
  const pid = pluginId.value
  if (!pid) return
  loading.value = true
  try {
    app.value = await applicationApi.getRemoteDetail(pid)
  } finally {
    loading.value = false
  }
}

function goBack() {
  const router = getCurrentInstance()?.appContext.config.globalProperties.$router as
    | { back?: () => void; push?: (p: string) => void }
    | undefined
  if (router?.back) router.back()
  else if (router?.push) router.push('/plugins/appstore/applications')
  else window.location.assign('/plugins/appstore/applications')
}

async function installRemote() {
  if (!app.value?.pluginId) return
  installing.value = true
  try {
    await applicationApi.installRemote(app.value.pluginId)
    message.success('已提交安装，请稍后刷新查看状态')
    await load()
  } finally {
    installing.value = false
  }
}

async function upgradeRemote() {
  if (!app.value?.pluginId) return
  upgrading.value = true
  try {
    await applicationApi.installRemote(app.value.pluginId)
    message.success('已提交升级，请稍后刷新查看状态')
    await load()
  } finally {
    upgrading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.remote-detail-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f5f5f5;
}

.remote-detail__top {
  padding: 12px 16px 0;
}

.remote-detail__layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  padding: 12px 16px 16px;
  align-items: start;
}

.remote-detail__aside {
  position: sticky;
  top: 12px;
}

.aside-card {
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
}

.aside-head {
  display: flex;
  gap: 12px;
  align-items: center;
}

.aside-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: rgba(99, 102, 241, 0.08);
  color: #6366f1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.aside-icon__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.aside-name {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.aside-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.aside-meta {
  margin-top: 14px;
  display: grid;
  gap: 10px;
}

.meta-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.meta-label {
  font-size: 12px;
  color: #9ca3af;
}

.meta-value {
  font-size: 13px;
  color: #111827;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.aside-actions {
  margin-top: 14px;
  display: flex;
  gap: 10px;
}

.aside-actions :deep(.n-button) {
  flex: 1;
}

.main-card :deep(.n-card__content) {
  padding: 14px;
}

.intro__desc {
  font-size: 13px;
  line-height: 1.7;
  color: #374151;
  white-space: pre-wrap;
}

.intro__desc--muted {
  color: #9ca3af;
}

.version {
  display: grid;
  gap: 10px;
  padding: 6px 2px;
}

.version__row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.version__label {
  font-size: 12px;
  color: #9ca3af;
}

.version__value {
  font-size: 13px;
  color: #111827;
}

@media (max-width: 900px) {
  .remote-detail__layout {
    grid-template-columns: 1fr;
  }

  .remote-detail__aside {
    position: static;
  }
}
</style>
