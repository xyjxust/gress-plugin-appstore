<template>
  <FilterPanel
    :filters="remoteFilters"
    :show-advanced="showRemoteAdvanced"
    :basic-fields="remoteBasicFields"
    @update:filters="$emit('update:remote-filters', $event)"
    @update:show-advanced="$emit('update:show-remote-advanced', $event)"
    @search="$emit('search')"
    @reset="$emit('reset')"
  />

  <div class="local-layout">
    <aside class="local-sidebar">
      <n-card size="small" class="filter-sidebar">
        <div class="filter-group">
          <div class="filter-group__title">类型</div>
          <n-radio-group :value="remoteFilters.category || ''" @update:value="$emit('select-category', $event || '')">
            <n-space vertical size="small">
              <n-radio value="">全部</n-radio>
              <n-radio v-for="item in typeOptions" :key="item.id" :value="item.id">{{ item.label }}</n-radio>
            </n-space>
          </n-radio-group>
        </div>

        <div class="filter-group">
          <div class="filter-group__title">价格</div>
          <n-radio-group :value="remoteFilters.priceType || ''" @update:value="$emit('select-price', $event || '')">
            <n-space vertical size="small">
              <n-radio value="">全部</n-radio>
              <n-radio value="free">免费</n-radio>
              <n-radio value="paid">付费</n-radio>
            </n-space>
          </n-radio-group>
        </div>

        <div class="filter-group">
          <div class="filter-group__title">标签</div>
          <div class="tag-chip-list">
            <n-tag
              size="small"
              round
              checkable
              :checked="!remoteFilters.tag"
              :type="remoteFilters.tag ? 'default' : 'primary'"
              @update:checked="(checked: boolean) => checked && $emit('select-tag', '')"
            >
              全部
            </n-tag>
            <n-tag
              v-for="item in tagOptions"
              :key="item.id"
              size="small"
              round
              checkable
              :checked="remoteFilters.tag === item.id"
              :type="remoteFilters.tag === item.id ? 'primary' : 'default'"
              @update:checked="(checked: boolean) => checked && $emit('select-tag', item.id)"
            >
              {{ item.label }}
            </n-tag>
          </div>
        </div>
      </n-card>
    </aside>

    <div class="local-main">
      <div v-if="remoteLoading" class="loading-state">
        <n-spin size="large" />
      </div>

      <div v-else-if="remoteTableData.length === 0" class="empty-state">
        <div class="empty-state__icon">
          <n-icon size="48">
            <component :is="appsOutline" />
          </n-icon>
        </div>
        <div class="empty-state__text">
          {{ remoteFilters.keyword ? '未找到匹配的应用' : '暂无远程应用' }}
        </div>
      </div>

      <div v-else class="app-list">
        <n-card
          v-for="app in remoteTableData"
          :key="app.id"
          class="app-card"
          hoverable
          @click="$emit('view-remote-detail', app)"
        >
          <div class="app-header">
            <div class="app-icon app-icon--plugin">
              <n-icon size="24">
                <component :is="extensionPuzzleOutline" />
              </n-icon>
            </div>
            <div class="app-info">
              <div class="app-title-row">
                <div class="app-name">{{ app.applicationName }}</div>
                <div class="app-badges">
                  <n-tag v-if="app.installStatus === 'INSTALLED'" type="success" size="small" round>已安装</n-tag>
                  <n-tag v-else-if="app.installStatus === 'UPGRADABLE'" type="warning" size="small" round>可升级</n-tag>
                  <n-tag v-else type="info" size="small" round>未安装</n-tag>
                </div>
              </div>
              <div class="app-code">{{ app.applicationCode }}</div>
            </div>
          </div>

          <div class="app-body">
            <div class="app-meta">
              <div class="meta-item">
                <span class="meta-label">插件ID：</span>
                <span class="meta-value">{{ app.pluginId }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">远程版本：</span>
                <span class="meta-value">{{ app.pluginVersion || '-' }}</span>
              </div>
              <div v-if="app.localVersion" class="meta-item">
                <span class="meta-label">本地版本：</span>
                <span class="meta-value">{{ app.localVersion }}</span>
              </div>
              <div v-if="app.author" class="meta-item">
                <span class="meta-label">作者：</span>
                <span class="meta-value">{{ app.author }}</span>
              </div>
            </div>

            <div
              v-if="app.description"
              class="app-description richtext"
              v-html="sanitize(app.description)"
            />
          </div>

          <div class="app-footer">
            <div class="app-actions">
              <n-button text type="info" size="small" @click.stop="$emit('view-remote-detail', app)">详情</n-button>
              <n-button
                v-if="app.installStatus === 'NOT_INSTALLED'"
                text
                type="success"
                size="small"
                :loading="installRemoteLoading[app.pluginId]"
                @click.stop="$emit('install-remote', app)"
              >
                安装
              </n-button>
              <n-button
                v-else-if="app.installStatus === 'UPGRADABLE'"
                text
                type="warning"
                size="small"
                :loading="upgradeRemoteLoading[app.pluginId]"
                @click.stop="$emit('upgrade-remote', app)"
              >
                升级
              </n-button>
              <n-button v-else text type="default" size="small" disabled>已安装</n-button>
            </div>
          </div>
        </n-card>
      </div>
    </div>
  </div>

  <div v-if="remotePagination.itemCount > 0" class="pagination">
    <n-pagination
      v-model:page="remotePagination.page"
      v-model:page-size="remotePagination.pageSize"
      :page-count="Math.ceil(remotePagination.itemCount / remotePagination.pageSize)"
      :page-sizes="remotePagination.pageSizes"
      show-size-picker
      @update:page="$emit('remote-page-change', $event)"
      @update:page-size="$emit('remote-page-size-change', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { sanitizeHtml } from '@keqi.gress/plugin-ui'
import type { Application } from '../../../types/application'
import type { FilterFieldConfig } from '../types'

defineProps<{
  remoteFilters: { keyword: string; category?: string; tag?: string; priceType?: string }
  showRemoteAdvanced: boolean
  remoteBasicFields: FilterFieldConfig[]
  remoteLoading: boolean
  remoteTableData: Application[]
  remotePagination: { page: number; pageSize: number; itemCount: number; pageSizes: number[] }
  typeOptions: Array<{ id: string; label: string; desc?: string }>
  tagOptions: Array<{ id: string; label: string; desc?: string }>
  appsOutline: unknown
  extensionPuzzleOutline: unknown
  timeOutline: unknown
  formatDateTime: (v: any) => string
  installRemoteLoading: Record<string, boolean>
  upgradeRemoteLoading: Record<string, boolean>
}>()

defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
  (e: 'update:remote-filters', v: { keyword: string }): void
  (e: 'update:show-remote-advanced', v: boolean): void
  (e: 'select-category', categoryKey: string): void
  (e: 'select-tag', tagKey: string): void
  (e: 'select-price', priceType: string): void
  (e: 'view-remote-detail', app: Application): void
  (e: 'install-remote', app: Application): void
  (e: 'upgrade-remote', app: Application): void
  (e: 'remote-page-change', page: number): void
  (e: 'remote-page-size-change', pageSize: number): void
}>()

const sanitize = sanitizeHtml
</script>

<style scoped>
.local-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  margin-top: 12px;
}

.local-sidebar {
  position: sticky;
  top: 12px;
}

.filter-sidebar {
  border-radius: 20px;
}

.filter-sidebar :deep(.n-card__content) {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 18px 16px;
}

.filter-group {
  border-bottom: 1px solid #edf1f5;
  padding-bottom: 16px;
}

.filter-group:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.filter-group__title {
  font-size: 14px;
  font-weight: 700;
  color: #374151;
  letter-spacing: 0.02em;
  margin-bottom: 12px;
}

.tag-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-sidebar :deep(.n-radio-group) {
  width: 100%;
}

.filter-sidebar :deep(.n-radio-button),
.filter-sidebar :deep(.n-radio) {
  font-size: 14px;
}

.filter-sidebar :deep(.n-space) {
  gap: 10px !important;
}

.filter-sidebar :deep(.n-radio) {
  display: inline-flex;
  align-items: center;
}

.filter-sidebar :deep(.n-radio__radio) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.filter-sidebar :deep(.n-radio .n-radio__label) {
  font-size: 14px;
  font-weight: 500;
  color: #4b5563;
  line-height: 1.2;
  display: inline-flex;
  align-items: center;
  margin-left: 10px;
  margin-top: 0;
}

.filter-sidebar :deep(.n-radio__dot) {
  display: block;
}

.tag-chip-list :deep(.n-tag) {
  border-radius: 999px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 600;
  line-height: 26px;
  height: 26px;
  border-color: #e5e7eb;
}

.tag-chip-list :deep(.n-tag.n-tag--primary-type) {
  box-shadow: inset 0 0 0 1px rgba(79, 70, 229, 0.08);
}

.app-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.app-card {
  border-radius: 20px;
}

.app-card :deep(.n-card__content) {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px;
}

.app-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.app-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(180deg, #eefaf6 0%, #e2f5ee 100%);
  color: #10b981;
}

.app-icon--plugin {
  background: linear-gradient(180deg, #eef6ff 0%, #e5f0ff 100%);
  color: #3b82f6;
}

.app-info {
  min-width: 0;
  flex: 1;
}

.app-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.app-name {
  min-width: 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
  color: #1f2937;
  word-break: break-word;
}

.app-badges {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.app-code {
  margin-top: 6px;
  font-size: 13px;
  color: #6b7280;
  word-break: break-all;
}

.app-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.app-meta {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.meta-label {
  flex-shrink: 0;
  font-size: 13px;
  color: #9ca3af;
}

.meta-value {
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  word-break: break-all;
}

.app-description {
  font-size: 14px;
  line-height: 1.6;
  color: #6b7280;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.richtext :deep(p) {
  margin: 0;
}

.richtext :deep(p + p) {
  margin-top: 6px;
}

.richtext :deep(ul),
.richtext :deep(ol) {
  margin: 0;
  padding-left: 18px;
}

.richtext :deep(a) {
  color: #2563eb;
  text-decoration: underline;
}

.app-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

.app-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
  width: 100%;
}

@media (max-width: 1024px) {
  .local-layout {
    grid-template-columns: 1fr;
  }

  .local-sidebar {
    position: static;
  }
}

@media (max-width: 640px) {
  .app-list {
    grid-template-columns: 1fr;
  }

  .app-title-row {
    flex-direction: column;
  }

  .app-badges {
    justify-content: flex-start;
  }

  .app-card :deep(.n-card__content) {
    padding: 16px;
  }

  .filter-sidebar :deep(.n-card__content) {
    padding: 16px 14px;
  }
}
</style>
