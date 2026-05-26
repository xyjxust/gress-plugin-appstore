<template>
  <div class="aggregate-application-page">
    <div class="page-header-wrapper">
      <PageHeader title="聚合应用管理" subtitle="将多个含前端的插件合并为一个应用，统一菜单与路由">
        <template #actions>
          <n-button type="primary" @click="handleNew">
            <template #icon>
              <n-icon><component :is="AddOutline" /></n-icon>
            </template>
            创建
          </n-button>
          <n-button :loading="aggregateLoading" @click="loadAggregateData">
            <template #icon>
              <n-icon><component :is="RefreshOutline" /></n-icon>
            </template>
            刷新
          </n-button>
        </template>
      </PageHeader>
    </div>

    <div class="page-content">
      <n-spin :show="aggregateLoading">
        <n-card title="已创建聚合应用" size="small" class="list-card">
          <n-alert type="info" :show-icon="false" class="hint-alert" style="margin-bottom: 12px">
            可选项仅包含已标记「B 端表面」的插件（surfaceAdmin=true）。已被聚合的插件将不再出现在主应用列表中；聚合应用的菜单与路由会自动汇总子插件结果。
          </n-alert>

          <div v-if="sortedAggregateList.length === 0" class="empty-hint">暂无聚合应用</div>
          <div v-else class="aggregate-grid">
            <n-card v-for="item in sortedAggregateList" :key="item.id" size="small" class="aggregate-item-card">
              <div class="aggregate-row">
                <div class="aggregate-row__main">
                  <div v-if="aggregateCardIconUrl(item)" class="aggregate-card-icon-wrap">
                    <img :src="aggregateCardIconUrl(item)!" alt="" class="aggregate-card-icon-img" />
                  </div>
                  <div v-else class="aggregate-card-icon-wrap aggregate-card-icon-wrap--default">
                    <n-icon size="20"><component :is="LayersOutline" /></n-icon>
                  </div>
                  <div class="aggregate-row__text">
                    <div class="aggregate-row__title">
                      {{ item.applicationName }}（{{ item.applicationCode }}）
                      <span
                        v-if="item.aggregateListOrder != null"
                        class="aggregate-row__order"
                      >
                        排序 {{ item.aggregateListOrder }}
                      </span>
                    </div>
                    <div class="aggregate-row__plugins">
                      {{ item.aggregatedPluginIds?.join(', ') || '-' }}
                    </div>
                  </div>
                </div>
              </div>
              <div class="aggregate-actions">
                <n-space>
                  <n-button size="tiny" @click="editAggregate(item)">编辑</n-button>
                  <n-button size="tiny" type="error" @click="removeAggregate(item)">删除</n-button>
                </n-space>
              </div>
            </n-card>
          </div>
        </n-card>
      </n-spin>
    </div>

    <!-- Create/Edit Modal -->
    <n-modal
      v-model:show="aggregateModalVisible"
      preset="card"
      :mask-closable="false"
      :title="aggregateForm.id ? '编辑聚合应用' : '创建聚合应用'"
      style="width: 760px"
    >
      <n-form :model="aggregateForm" label-placement="left" label-width="110" size="small">
        <n-form-item label="应用编码">
          <n-input
            v-model:value="aggregateForm.applicationCode"
            placeholder="例如：ops-center"
            :disabled="Boolean(aggregateForm.id)"
          />
        </n-form-item>
        <n-form-item label="应用名称">
          <n-input v-model:value="aggregateForm.applicationName" placeholder="例如：运营中台" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="aggregateForm.description" type="textarea" :rows="2" />
        </n-form-item>
        <n-form-item label="展示图标">
          <n-input
            v-model:value="aggregateForm.icon"
            placeholder="可选：图标图片 URL；留空则使用默认聚合图标"
          />
        </n-form-item>
        <n-form-item label="列表排序">
          <n-input-number
            v-model:value="aggregateForm.aggregateListOrder"
            :min="0"
            :max="999999"
            placeholder="越小越靠前，默认 1000"
            style="width: 100%"
          />
        </n-form-item>
        <n-form-item label="自动加载">
          <n-switch v-model:value="aggregateForm.autoLoad" size="small" />
        </n-form-item>
        <n-form-item label="聚合插件">
          <n-select
            v-model:value="aggregateForm.pluginIds"
            :options="aggregatablePluginOptions"
            multiple
            filterable
            placeholder="请选择要聚合的插件（仅 surfaceAdmin=true）"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="handleCancelAggregateModal">取消</n-button>
          <n-button type="primary" :loading="aggregateSaving" @click="saveAggregateApp">
            保存
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, getCurrentInstance } from 'vue'
import { useMessage } from '@keqi.gress/plugin-bridge'
import { useDialog } from 'naive-ui'
import { useIcon } from '@keqi.gress/plugin-bridge'
import {
  NSpace,
  NButton,
  NIcon,
  NCard,
  NAlert,
  NForm,
  NFormItem,
  NInput,
  NSpin,
  NSelect,
  NSwitch,
  NInputNumber,
  NModal
} from 'naive-ui'
import { applicationApi } from '../api/application'
import type { Application, AggregateApplicationRequest } from '../types/application'

const message = useMessage()
const dialog = useDialog()

const RefreshOutline = useIcon('RefreshOutline')
const CubeOutline = useIcon('CubeOutline')
const AddOutline = useIcon('AddOutline')
const LayersOutline = useIcon('LayersOutline')

const APPLICATIONS_PATH = '/plugins/appstore/applications'

function navigate(path: string) {
  const router = getCurrentInstance()?.appContext.config.globalProperties.$router as
    | { push?: (p: string) => void }
    | undefined
  if (router?.push) {
    router.push(path)
  } else {
    window.location.assign(path)
  }
}

function goToApplications() {
  navigate(APPLICATIONS_PATH)
}

const aggregateLoading = ref(false)
const aggregateSaving = ref(false)
const aggregateList = ref<Application[]>([])
const aggregatablePlugins = ref<Application[]>([])
const aggregateModalVisible = ref(false)
const aggregateForm = reactive<AggregateApplicationRequest & { id?: number }>({
  id: undefined,
  applicationCode: '',
  applicationName: '',
  description: '',
  icon: '',
  aggregateListOrder: 1000,
  pluginIds: [],
  autoLoad: false
})

const aggregatablePluginOptions = computed(() =>
  aggregatablePlugins.value.map(p => ({
    label: `${p.applicationName} (${p.pluginId})`,
    value: p.pluginId
  }))
)

const sortedAggregateList = computed(() =>
  [...aggregateList.value].sort(
    (a, b) => (a.aggregateListOrder ?? 1000) - (b.aggregateListOrder ?? 1000)
  )
)

function aggregateCardIconUrl(item: Application): string | null {
  const u = item.icon?.trim()
  return u || null
}

function resetAggregateForm() {
  aggregateForm.id = undefined
  aggregateForm.applicationCode = ''
  aggregateForm.applicationName = ''
  aggregateForm.description = ''
  aggregateForm.icon = ''
  aggregateForm.aggregateListOrder = 1000
  aggregateForm.pluginIds = []
  aggregateForm.autoLoad = false
}

function handleCancelAggregateModal() {
  aggregateModalVisible.value = false
  resetAggregateForm()
}

async function loadAggregateData() {
  aggregateLoading.value = true
  try {
    const [aggregates, plugins] = await Promise.all([
      applicationApi.listAggregates(),
      applicationApi.listAggregatablePlugins()
    ])
    aggregateList.value = aggregates || []
    aggregatablePlugins.value = (plugins || []).filter(p => !p.aggregateApp)
  } finally {
    aggregateLoading.value = false
  }
}

function handleNew() {
  resetAggregateForm()
  aggregateModalVisible.value = true
  message.info('已清空表单，可填写后保存创建新聚合应用')
}

function editAggregate(app: Application) {
  aggregateForm.id = app.id
  aggregateForm.applicationCode = app.applicationCode || ''
  aggregateForm.applicationName = app.applicationName || ''
  aggregateForm.description = app.description || ''
  aggregateForm.icon = app.icon || ''
  aggregateForm.aggregateListOrder = app.aggregateListOrder ?? 1000
  aggregateForm.pluginIds = Array.isArray(app.aggregatedPluginIds) ? [...app.aggregatedPluginIds] : []
      aggregateForm.autoLoad = Boolean(app.autoLoadAdmin)
  aggregateModalVisible.value = true
}

async function saveAggregateApp() {
  if (!aggregateForm.applicationCode?.trim()) {
    message.warning('请输入应用编码')
    return
  }
  if (!aggregateForm.applicationName?.trim()) {
    message.warning('请输入应用名称')
    return
  }
  if (!aggregateForm.pluginIds || aggregateForm.pluginIds.length === 0) {
    message.warning('请选择至少一个插件')
    return
  }

  aggregateSaving.value = true
  try {
    const payload: AggregateApplicationRequest = {
      applicationCode: aggregateForm.applicationCode.trim(),
      applicationName: aggregateForm.applicationName.trim(),
      description: aggregateForm.description || '',
      icon: aggregateForm.icon?.trim() || undefined,
      aggregateListOrder: aggregateForm.aggregateListOrder ?? 1000,
      pluginIds: [...aggregateForm.pluginIds],
      autoLoad: Boolean(aggregateForm.autoLoad)
    }
    if (aggregateForm.id) {
      await applicationApi.updateAggregate(aggregateForm.id, payload)
      message.success('聚合应用已更新')
    } else {
      await applicationApi.createAggregate(payload)
      message.success('聚合应用已创建')
    }
    await loadAggregateData()
    aggregateModalVisible.value = false
    resetAggregateForm()
  } catch (e) {
    console.error('保存聚合应用失败', e)
  } finally {
    aggregateSaving.value = false
  }
}

function removeAggregate(app: Application) {
  dialog.warning({
    title: '删除聚合应用',
    content: `确定删除聚合应用 "${app.applicationName}" 吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await applicationApi.deleteAggregate(app.id)
      message.success('聚合应用已删除')
      if (aggregateForm.id === app.id) {
        resetAggregateForm()
        aggregateModalVisible.value = false
      }
      await loadAggregateData()
    }
  })
}

onMounted(() => {
  loadAggregateData()
})
</script>

<style scoped>
.aggregate-application-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}

.page-header-wrapper {
  background: white;
  border-bottom: 1px solid #e8e8e8;
}

.page-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  overflow: auto;
}

.form-card {
  max-width: 920px;
}

.hint-alert {
  max-width: 920px;
}

.list-card {
  max-width: 1180px;
}

.aggregate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.aggregate-item-card {
  min-height: 120px;
  position: relative;
  padding-bottom: 44px;
}

.empty-hint {
  color: #999;
  font-size: 12px;
}

.aggregate-row {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  gap: 12px;
}

.aggregate-row__main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.aggregate-row__text {
  min-width: 0;
}

.aggregate-row__title {
  font-weight: 600;
}

.aggregate-row__order {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
  margin-left: 6px;
}

.aggregate-row__plugins {
  font-size: 12px;
  color: #999;
}

.aggregate-actions {
  position: absolute;
  right: 12px;
  bottom: 12px;
}

.aggregate-card-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
  overflow: hidden;
}

.aggregate-card-icon-wrap--default {
  border: 1px solid rgba(245, 158, 11, 0.35);
}

.aggregate-card-icon-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
