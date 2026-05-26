<template>
  <div class="remote-detail-inline">
    <div class="remote-detail-inline__top">
      <n-button text @click="$emit('back')">
        <template #icon>
          <n-icon><component :is="chevronBackOutline" /></n-icon>
        </template>
        返回列表
      </n-button>
    </div>

    <div v-if="loading" class="loading-state">
      <n-spin size="large" />
    </div>

    <div v-else-if="!detail" class="empty-state">
      <div class="empty-state__text">未找到应用信息</div>
    </div>

    <div v-else class="remote-detail__layout">
      <aside class="remote-detail__aside">
        <div class="aside-card">
          <div class="aside-head">
            <div class="aside-icon">
              <img v-if="detail.icon?.trim()" :src="detail.icon.trim()" alt="" class="aside-icon__img" />
              <n-icon v-else size="28"><component :is="extensionPuzzleOutline" /></n-icon>
            </div>
            <div class="aside-title">
              <div class="aside-name">{{ detail.applicationName }}</div>
              <div class="aside-sub">{{ detail.applicationCode }}</div>
            </div>
          </div>

          <div class="aside-meta">
            <div class="meta-row">
              <div class="meta-label">插件ID</div>
              <div class="meta-value mono">{{ detail.pluginId }}</div>
            </div>
            <div class="meta-row">
              <div class="meta-label">版本</div>
              <div class="meta-value mono">{{ detail.pluginVersion || '-' }}</div>
            </div>
            <div class="meta-row" v-if="detail.localVersion">
              <div class="meta-label">本地版本</div>
              <div class="meta-value mono">{{ detail.localVersion }}</div>
            </div>
          </div>

          <div class="aside-actions">
            <n-button
              v-if="detail.installStatus === 'NOT_INSTALLED'"
              type="primary"
              :loading="installing"
              @click="$emit('install', detail)"
            >
              安装
            </n-button>
            <n-button
              v-else-if="detail.installStatus === 'UPGRADABLE'"
              type="warning"
              :loading="upgrading"
              @click="$emit('upgrade', detail)"
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
                <div v-if="detail.description" class="intro__desc richtext" v-html="sanitize(detail.description)" />
                <div v-else class="intro__desc intro__desc--muted">暂无简介</div>
              </div>
            </n-tab-pane>
            <n-tab-pane name="version" tab="版本">
              <div class="version-timeline">
                <div class="version-timeline__left">
                  <div v-if="versionsLoading" class="version-timeline__loading">加载中...</div>
                  <button
                    v-for="v in versions"
                    :key="v.version"
                    class="version-item"
                    :class="{ 'version-item--active': selectedVersion === v.version }"
                    @click="$emit('select-version', v.version)"
                  >
                    <div class="version-item__title">
                      <span class="mono">v{{ v.version }}</span>
                      <span v-if="v.current" class="version-item__badge">最新</span>
                    </div>
                    <div class="version-item__sub">
                      <span v-if="v.uploadTime">{{ v.uploadTime }}</span>
                    </div>
                  </button>
                </div>

                <div class="version-timeline__right">
                  <div class="version-panel">
                    <div class="version-panel__head">
                      <div class="version-panel__ver mono">v{{ selectedVersion }}</div>
                      <div class="version-panel__actions">
                        <n-button
                          size="small"
                          type="primary"
                          :disabled="!detail?.pluginId || !selectedVersion"
                          @click="$emit('download', detail!.pluginId, selectedVersion)"
                        >
                          资源下载
                        </n-button>
                      </div>
                    </div>
                    <div class="version-panel__body">
                      <div
                        v-if="selectedReleaseNotes"
                        class="version-panel__notes richtext"
                        v-html="sanitize(selectedReleaseNotes)"
                      />
                      <div v-else class="version-panel__notes version-panel__notes--muted">
                        暂无发布说明
                      </div>
                    </div>
                  </div>
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
import { computed } from 'vue'
import { sanitizeHtml } from '@keqi.gress/plugin-ui'
import type { Application } from '../../../types/application'

const props = defineProps<{
  detail: Application | null
  loading: boolean
  installing: boolean
  upgrading: boolean
  versions: Array<{
    pluginId: string
    version: string
    releaseNotes?: string
    fileSize?: number
    uploadTime?: string
    current?: boolean
  }>
  versionsLoading: boolean
  selectedVersion: string
  chevronBackOutline: unknown
  extensionPuzzleOutline: unknown
}>()

const activeTab = defineModel<'intro' | 'version'>('activeTab', { default: 'intro' })

const selectedReleaseNotes = computed(() => {
  const v = (props.versions || []).find((x) => x.version === props.selectedVersion)
  return v?.releaseNotes || ''
})

const sanitize = sanitizeHtml

defineEmits<{
  (e: 'back'): void
  (e: 'install', app: Application): void
  (e: 'upgrade', app: Application): void
  (e: 'select-version', version: string): void
  (e: 'download', pluginId: string, version: string): void
}>()
</script>

<style scoped>
.remote-detail-inline__top {
  padding: 0 0 12px;
}

.remote-detail__layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
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

.richtext :deep(p) {
  margin: 0 0 10px;
}
.richtext :deep(ul),
.richtext :deep(ol) {
  padding-left: 22px;
  margin: 0 0 10px;
}
.richtext :deep(a) {
  color: #1677ff;
  text-decoration: underline;
}

.intro__desc--muted {
  color: #9ca3af;
}

.version-timeline {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 14px;
  padding: 6px 0;
}

.version-timeline__left {
  border-right: 1px solid #eef0f4;
  padding-right: 12px;
}

.version-timeline__loading {
  font-size: 12px;
  color: #9ca3af;
  padding: 8px 8px 12px;
}

.version-item {
  width: 100%;
  border: 1px solid transparent;
  background: transparent;
  text-align: left;
  padding: 10px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
  display: block;
}

.version-item:hover {
  background: rgba(99, 102, 241, 0.06);
  border-color: rgba(99, 102, 241, 0.25);
}

.version-item--active {
  background: rgba(99, 102, 241, 0.09);
  border-color: rgba(99, 102, 241, 0.35);
}

.version-item__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-weight: 600;
  color: #111827;
}

.version-item__badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(99, 102, 241, 0.12);
  color: #4f46e5;
  font-weight: 600;
}

.version-item__sub {
  margin-top: 6px;
  font-size: 12px;
  color: #9ca3af;
}

.version-panel {
  border: 1px solid #eef0f4;
  border-radius: 12px;
  background: #fff;
  padding: 12px 12px;
}

.version-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f3f6;
}

.version-panel__ver {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.version-panel__body {
  padding-top: 10px;
}

.version-panel__notes {
  font-size: 13px;
  line-height: 1.7;
  color: #374151;
  white-space: pre-wrap;
}

.version-panel__notes--muted {
  color: #9ca3af;
}

@media (max-width: 900px) {
  .remote-detail__layout {
    grid-template-columns: 1fr;
  }

  .remote-detail__aside {
    position: static;
  }

  .version-timeline {
    grid-template-columns: 1fr;
  }

  .version-timeline__left {
    border-right: none;
    padding-right: 0;
    border-bottom: 1px solid #eef0f4;
    padding-bottom: 10px;
    margin-bottom: 10px;
  }
}
</style>

