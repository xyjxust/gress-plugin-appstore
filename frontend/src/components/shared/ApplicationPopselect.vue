<template>
  <div class="app-picker">
    <n-button :disabled="disabled" secondary class="app-picker__trigger" @click="openModal">
      <span class="app-picker__trigger-icon">
        <img v-if="selectedIconUrl" :src="selectedIconUrl" alt="" class="app-picker__trigger-img" />
        <span v-else class="app-picker__trigger-fallback" />
      </span>
      <span class="app-picker__trigger-text">{{ selectedLabel || placeholder }}</span>
      <span class="app-picker__trigger-chev" aria-hidden="true">⌄</span>
    </n-button>

    <n-modal
      v-model:show="open"
      preset="card"
      title="选择插件"
      style="width:min(860px,96vw)"
      :segmented="{ content: true, footer: 'soft' }"
    >
      <div class="app-picker__header">
        <n-input
          v-model:value="keyword"
          clearable
          :placeholder="searchPlaceholder"
          @keyup.enter="searchNow"
          @update:value="onKeywordInput"
        />
      </div>

      <div v-if="items.length" class="app-picker__grid">
        <button
          v-for="app in items"
          :key="app.pluginId"
          type="button"
          class="app-picker__card"
          :class="{ 'app-picker__card--active': app.pluginId === (modelValue || '') }"
          @click="select(app)"
        >
          <div class="app-picker__card-icon" :class="{ 'app-picker__card-icon--img': !!getAppIconUrl(app) }">
            <img v-if="getAppIconUrl(app)" :src="getAppIconUrl(app) || undefined" class="app-picker__card-img" alt="" />
            <span v-else class="app-picker__card-fallback" />
          </div>
          <div class="app-picker__card-name">{{ app.applicationName }}</div>
          <div v-if="app.pluginId === (modelValue || '')" class="app-picker__card-check">✓</div>
        </button>
      </div>
      <div v-else class="app-picker__empty">{{ loading ? '加载中…' : emptyText }}</div>

      <template #footer>
        <div class="app-picker__footer">
          <n-button size="small" :disabled="loading || page <= 1" @click="prevPage">上一页</n-button>
          <span class="app-picker__page">{{ total ? `${page}/${totalPages}` : page }}</span>
          <n-button size="small" :disabled="loading || page >= totalPages" @click="nextPage">下一页</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { NButton, NInput, NModal } from 'naive-ui'
import { applicationApi } from '../../api/application'
import type { Application } from '../../types/application'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    placeholder?: string
    searchPlaceholder?: string
    emptyText?: string
    disabled?: boolean
    pageSize?: number
    localOnly?: boolean
  }>(),
  {
    modelValue: null,
    placeholder: '选择一个插件（可选）',
    searchPlaceholder: '搜索应用名 / code / pluginId',
    emptyText: '没有匹配的应用',
    disabled: false,
    pageSize: 12,
    localOnly: true
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null): void
  (e: 'change', value: string | null, app: Application | null): void
}>()

const open = ref(false)
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const items = ref<Application[]>([])
const selectedApp = ref<Application | null>(null)

const totalPages = computed(() => {
  const size = props.pageSize || 1
  return Math.max(1, Math.ceil((total.value || 0) / size))
})

const selectedLabel = computed(() => {
  const pid = (props.modelValue || '').trim()
  if (!pid) return ''
  const app = items.value.find((x) => x.pluginId === pid) || selectedApp.value
  return app?.applicationName || pid
})

const selectedIconUrl = computed(() => getAppIconUrl(selectedApp.value))

function getAppIconUrl(app?: Application | null): string {
  const icon = (app?.icon || '').trim()
  if (!icon) return ''
  if (/^https?:\/\//i.test(icon)) return icon
  if (/^data:/i.test(icon)) return icon
  if (icon.startsWith('/')) return icon
  return ''
}

async function ensureSelectedApp() {
  const pid = (props.modelValue || '').trim()
  if (!pid) {
    selectedApp.value = null
    return
  }
  const inPage = items.value.find((x) => x.pluginId === pid)
  if (inPage) {
    selectedApp.value = inPage
    return
  }
  try {
    const res = await applicationApi.getList({ page: 1, size: 50, keyword: pid })
    selectedApp.value = res.items.find((x) => x.pluginId === pid) || null
  } catch {
    selectedApp.value = null
  }
}

async function load() {
  loading.value = true
  try {
    const res = await applicationApi.getList({
      page: page.value,
      size: props.pageSize,
      keyword: keyword.value.trim() || undefined
    })
    items.value = props.localOnly ? res.items : res.items
    total.value = res.total || 0
    const pid = (props.modelValue || '').trim()
    if (pid) {
      const hit = items.value.find((x) => x.pluginId === pid)
      if (hit) selectedApp.value = hit
    }
  } catch {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function select(app: Application) {
  emit('update:modelValue', app.pluginId)
  emit('change', app.pluginId, app)
  selectedApp.value = app
  open.value = false
}

function openModal() {
  if (props.disabled) return
  open.value = true
  page.value = 1
  void load()
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  void load()
}

function nextPage() {
  if (page.value >= totalPages.value) return
  page.value += 1
  void load()
}

function searchNow() {
  page.value = 1
  void load()
}

let keywordTimer: ReturnType<typeof setTimeout> | null = null
function onKeywordInput() {
  page.value = 1
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    void load()
  }, 180)
}

onMounted(() => {
  void load()
  void ensureSelectedApp()
})

watch(
  () => props.modelValue,
  () => {
    void ensureSelectedApp()
  }
)
</script>

<style scoped>
.app-picker__trigger {
  width: 100%;
  justify-content: space-between;
  height: 42px;
  border-radius: 12px;
}
.app-picker__trigger-icon {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 8px;
  flex-shrink: 0;
}
.app-picker__trigger-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.app-picker__trigger-fallback {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background-image: radial-gradient(#6b7280 1.2px, transparent 1.3px);
  background-size: 4px 4px;
  display: block;
}
.app-picker__trigger-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  flex: 1;
}
.app-picker__trigger-chev {
  color: #94a3b8;
  margin-left: 10px;
}

.app-picker__header {
  margin-bottom: 12px;
}

.app-picker__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  min-height: 220px;
}

.app-picker__card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 98px;
  padding: 10px 8px;
  border-radius: 12px;
  border: 1.5px solid transparent;
  background: #f3f4f6;
  cursor: pointer;
  transition: all 0.2s ease;
}
.app-picker__card:hover {
  background: #eef0f3;
  border-color: rgba(99, 102, 241, 0.25);
  transform: translateY(-1px);
}
.app-picker__card--active {
  background: rgba(99, 102, 241, 0.14);
  border-color: #6366f1;
}

.app-picker__card-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: rgba(99, 102, 241, 0.2);
  color: #5b61ea;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.app-picker__card--active .app-picker__card-icon {
  background: #6366f1;
  color: #fff;
}
.app-picker__card-icon--img {
  background: transparent;
}
.app-picker__card-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.app-picker__card-fallback {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  background-image: radial-gradient(currentColor 1.3px, transparent 1.4px);
  background-size: 5px 5px;
}
.app-picker__card-name {
  width: 100%;
  font-size: 13px;
  color: #0f172a;
  text-align: center;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  word-break: break-word;
}
.app-picker__card--active .app-picker__card-name {
  color: #5b61ea;
  font-weight: 600;
}
.app-picker__card-check {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: #18a058;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
}

.app-picker__empty {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
}

.app-picker__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.app-picker__page {
  font-size: 13px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 960px) {
  .app-picker__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .app-picker__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

