<template>
  <div class="asdp-dev dev-root">
    <n-layout has-sider class="dev-layout">
      <n-layout-sider
        v-model:collapsed="collapsed"
        bordered
        collapse-mode="width"
        :collapsed-width="64"
        :width="220"
        show-trigger
        content-style="display:flex;flex-direction:column"
      >
        <div class="sider-brand">
          <span class="sider-brand__dot" />
          <span v-if="!collapsed" class="sider-brand__txt">Dev Hub</span>
        </div>
        <n-menu
          v-model:value="activeKey"
          :collapsed="collapsed"
          :collapsed-width="64"
          :options="menuOptions"
          @update:value="handleMenuSelect"
        />
        <div class="sider-foot">
          <n-button v-if="!collapsed" quaternary size="small" block @click="logout">退出 Demo</n-button>
        </div>
      </n-layout-sider>
      <n-layout>
        <n-layout-header bordered class="dev-header">
          <div class="dev-header__left">
            <n-breadcrumb>
              <n-breadcrumb-item>应用商店</n-breadcrumb-item>
              <n-breadcrumb-item>{{ breadcrumbTitle }}</n-breadcrumb-item>
            </n-breadcrumb>
          </div>
          <div class="dev-header__right">
            <span class="env-pill">Demo</span>
            <span class="user-chip">{{ sessionUser }}</span>
          </div>
        </n-layout-header>
        <n-layout-content content-style="padding: 24px; min-height: 360px" native-scrollbar>
          <OverviewPanel v-if="activeKey === 'overview'" />
          <MyPluginsPanel v-else-if="activeKey === 'plugins'" />
          <UploadPanel v-else-if="activeKey === 'upload'" />
          <ApiKeysPanel v-else-if="activeKey === 'keys'" />
        </n-layout-content>
      </n-layout>
    </n-layout>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { MenuOption } from 'naive-ui'
import {
  NLayout,
  NLayoutSider,
  NLayoutHeader,
  NLayoutContent,
  NMenu,
  NBreadcrumb,
  NBreadcrumbItem,
  NButton,
  useMessage
} from 'naive-ui'
import { SESSION_KEY } from './mockData'
import OverviewPanel from './panels/OverviewPanel.vue'
import MyPluginsPanel from './panels/MyPluginsPanel.vue'
import UploadPanel from './panels/UploadPanel.vue'
import ApiKeysPanel from './panels/ApiKeysPanel.vue'

const message = useMessage()
const collapsed = ref(false)
const activeKey = ref('overview')
const sessionUser = ref('开发者')

const menuOptions: MenuOption[] = [
  { label: '概览', key: 'overview' },
  { label: '我的插件', key: 'plugins' },
  { label: '上传新版本', key: 'upload' },
  { label: 'API 密钥', key: 'keys' }
]

const breadcrumbTitle = computed(() => {
  const m: Record<string, string> = {
    overview: '工作台',
    plugins: '我的插件',
    upload: '上传',
    keys: 'API 密钥'
  }
  return m[activeKey.value] || ''
})

function handleMenuSelect(key: string) {
  activeKey.value = key
}

function logout() {
  localStorage.removeItem(SESSION_KEY)
  message.info('已退出 Demo 会话')
  const w = window as any
  const path = '/plugins/appstore/developer-portal'
  if (w.__GRESS_PLUGIN_BRIDGE__?.router?.replace) {
    w.__GRESS_PLUGIN_BRIDGE__.router.replace(path)
  } else {
    window.location.assign(path)
  }
}

onMounted(() => {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) {
      message.warning('请先于门户页登录')
      const w = window as any
      const path = '/plugins/appstore/developer-portal'
      if (w.__GRESS_PLUGIN_BRIDGE__?.router?.replace) {
        w.__GRESS_PLUGIN_BRIDGE__.router.replace(path)
      }
      return
    }
    const j = JSON.parse(raw) as { user?: string }
    if (j?.user) sessionUser.value = j.user
  } catch {
    message.error('会话无效')
  }
})
</script>

<style scoped lang="scss">
@import '../../styles/developer-portal-tokens.scss';

.dev-root {
  min-height: 100vh;
  background: var(--asdp-bg);
  color: var(--asdp-ink);
  font-family: var(--asdp-font-display);
}

.dev-layout {
  min-height: 100vh;
}

.sider-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
  border-bottom: 1px solid var(--asdp-line);
}

.sider-brand__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--asdp-accent);
  box-shadow: 0 0 12px rgba(99, 102, 241, 0.6);
}

.sider-foot {
  margin-top: auto;
  padding: 12px;
}

.dev-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 56px;
  background: var(--asdp-surface) !important;
}

.dev-header__right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.env-pill {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--asdp-accent-soft);
  color: var(--asdp-accent);
  font-family: var(--asdp-font-mono);
}

.user-chip {
  font-size: 13px;
  color: var(--asdp-ink-muted);
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
