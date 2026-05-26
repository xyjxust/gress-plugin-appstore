<template>
  <!-- 过滤面板 -->
  <FilterPanel
    :filters="filters"
    :show-advanced="showAdvanced"
    :basic-fields="basicFields"
    @update:filters="$emit('update:filters', $event)"
    @update:show-advanced="$emit('update:show-advanced', $event)"
    @search="$emit('search')"
    @reset="$emit('reset')"
  />

  <!-- 应用列表 -->
  <div class="local-layout">
    <aside class="local-sidebar">
      <n-card size="small" class="filter-sidebar">
        <div class="filter-group">
          <div class="filter-group__title">类型</div>
          <n-radio-group :value="activeType" @update:value="$emit('select-type', $event || '')">
            <n-space vertical size="small">
              <n-radio value="">全部</n-radio>
              <n-radio v-for="item in typeOptions" :key="item.id" :value="item.id">{{ item.label }}</n-radio>
            </n-space>
          </n-radio-group>
        </div>

        <div class="filter-group">
          <div class="filter-group__title">标签</div>
          <div class="tag-chip-list">
            <n-tag
              size="small"
              round
              :type="filters.tag ? 'default' : 'primary'"
              checkable
              :checked="!filters.tag"
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
              :checked="filters.tag === item.id"
              :type="filters.tag === item.id ? 'primary' : 'default'"
              @update:checked="(checked: boolean) => checked && $emit('select-tag', item.id)"
            >
              {{ item.label }}
            </n-tag>
          </div>
        </div>
      </n-card>
    </aside>

    <div class="local-main">
      <div v-if="loading" class="loading-state">
        <n-spin size="large" />
      </div>

      <div v-else-if="tableData.length === 0" class="empty-state">
        <div class="empty-state__icon">
          <n-icon size="48">
            <component :is="appsOutline" />
          </n-icon>
        </div>
        <div class="empty-state__text">
          {{ filters.keyword ? '未找到匹配的应用' : '暂无应用信息' }}
        </div>
      </div>

      <div v-else class="app-list">
        <n-card v-for="app in tableData" :key="app.id" class="app-card" hoverable @click="$emit('view-detail', app)">
          <div class="app-header">
            <div class="app-icon" :class="localAppIconModifierClass(app)">
              <img v-if="useAggregateIconImg(app)" :src="app.icon!.trim()" alt="" class="app-icon__img" />
              <n-icon v-else size="24">
                <component :is="localAppIconComponent(app)" />
              </n-icon>
            </div>
            <div class="app-info">
              <div class="app-title-row">
                <div class="app-name">{{ app.applicationName }}</div>
                <div class="app-badges">
                  <n-tag v-if="app.aggregateApp" type="info" size="small" round>聚合</n-tag>
                  <n-tag v-if="app.isDefault === 1" type="warning" size="small" round>默认</n-tag>
                  <n-tag :type="app.status === 1 ? 'success' : 'error'" size="small" round>
                    {{ app.statusText }}
                  </n-tag>
                </div>
              </div>
              <div class="app-code">{{ app.applicationCode }}</div>
            </div>
          </div>

          <div class="app-body">
            <div class="app-meta">
              <div class="meta-item" v-copy="app.pluginId">
                <span class="meta-label">插件ID：</span>
                <span class="meta-value">{{ app.pluginId }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">版本：</span>
                <span class="meta-value">{{ app.pluginVersion || '-' }}</span>
                <n-tooltip v-if="app.hasNewVersion" placement="top">
                  <template #trigger>
                    <n-icon
                      size="16"
                      color="#f59e0b"
                      style="margin-left: 6px; cursor: pointer; vertical-align: middle"
                      @click.stop="$emit('show-upgrade-info', app)"
                    >
                      <component :is="rocketOutline" />
                    </n-icon>
                  </template>
                  <span>有新版本 {{ app.remoteVersion }} 可升级</span>
                </n-tooltip>
              </div>
            </div>

            <div v-if="app.description" class="app-description">
              {{ app.description }}
            </div>
          </div>

          <div class="app-footer">
            <div class="app-actions">
              <n-button text type="primary" size="small" @click.stop="$emit('show-config', app)">配置</n-button>

              <n-button
                v-if="app.hasNewVersion"
                text
                type="warning"
                size="small"
                :disabled="app.applicationType === 'integrated' || app.aggregateApp"
                @click.stop="$emit('upgrade', app)"
              >
                升级
              </n-button>

              <n-button text type="info" size="small" :disabled="app.status !== 1" @click.stop="$emit('restart', app)">重启</n-button>

              <n-dropdown :options="getMoreActions(app)" @select="(key) => $emit('more-action', key as any, app)" trigger="click">
                <n-button text size="small" @click.stop>
                  <template #icon>
                    <n-icon><component :is="ellipsisHorizontalOutline" /></n-icon>
                  </template>
                </n-button>
              </n-dropdown>
            </div>
          </div>
        </n-card>
      </div>
    </div>
  </div>

  <!-- 分页 -->
  <div v-if="pagination.itemCount > 0" class="pagination">
    <n-pagination
      v-model:page="pagination.page"
      v-model:page-size="pagination.pageSize"
      :page-count="Math.ceil(pagination.itemCount / pagination.pageSize)"
      :page-sizes="pagination.pageSizes"
      show-size-picker
      @update:page="$emit('page-change', $event)"
      @update:page-size="$emit('page-size-change', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import type { Application } from '../../../types/application'
import type { FilterFieldConfig } from '../types'

defineProps<{
  filters: {
    keyword: string
    status: number | null
    applicationType: any
    clientType: 'B' | 'C' | null
    preloadEnabled: 0 | 1 | null
    typeKey: string
    tag: string
  }
  showAdvanced: boolean
  basicFields: FilterFieldConfig[]
  activeType: string
  typeOptions: Array<{ id: string; label: string; desc?: string }>
  tagOptions: Array<{ id: string; label: string; desc?: string }>
  loading: boolean
  tableData: Application[]
  pagination: { page: number; pageSize: number; itemCount: number; pageSizes: number[] }
  appsOutline: unknown
  rocketOutline: unknown
  timeOutline: unknown
  ellipsisHorizontalOutline: unknown

  formatDateTime: (v: any) => string
  getApplicationTypeColor: (t: any) => any
  getPluginTypes: (t: any) => string[]
  getPluginTypeColor: (t: any) => any
  getPluginTypeText: (t: any) => string
  getMoreActions: (app: Application) => any[]
  localAppIconModifierClass: (app: Application) => string
  useAggregateIconImg: (app: Application) => boolean
  localAppIconComponent: (app: Application) => any
}>()

defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
  (e: 'update:filters', v: any): void
  (e: 'update:show-advanced', v: boolean): void
  (e: 'select-type', typeKey: string): void
  (e: 'select-tag', tagId: string): void
  (e: 'view-detail', app: Application): void
  (e: 'show-upgrade-info', app: Application): void
  (e: 'show-config', app: Application): void
  (e: 'upgrade', app: Application): void
  (e: 'restart', app: Application): void
  (e: 'more-action', key: string, app: Application): void
  (e: 'page-change', page: number): void
  (e: 'page-size-change', pageSize: number): void
}>()
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

.app-icon__img {
  width: 38px;
  height: 38px;
  object-fit: contain;
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
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
