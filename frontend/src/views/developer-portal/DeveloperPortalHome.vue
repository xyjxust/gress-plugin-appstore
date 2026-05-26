<template>
  <div class="asdp-portal portal-root">
    <div class="portal-bg" aria-hidden="true" />
    <header class="portal-header">
      <div class="portal-header__inner">
        <a class="brand" href="#" @click.prevent>
          <span class="brand__mark" />
          <span class="brand__text">Gress <em>Store</em></span>
        </a>
        <nav class="nav" aria-label="主导航">
          <button type="button" class="nav__link" :class="{ 'nav__link--active': browseMode === 'all' }" @click="browseMode = 'all'">浏览</button>
          <button type="button" class="nav__link" @click="scrollPlugins">插件</button>
          <button type="button" class="nav__link" @click="openDocs">文档</button>
        </nav>
        <div class="portal-header__actions">
          <n-button quaternary class="btn-ghost" @click="openLogin">开发者登录</n-button>
          <n-button type="primary" class="btn-accent" @click="openLogin">进入中心</n-button>
        </div>
      </div>
    </header>

    <main>
      <section class="hero">
        <div class="hero__grid" aria-hidden="true" />
        <div class="hero__content">
          <p class="hero__eyebrow" style="--i: 0">插件生态 · 可审计交付</p>
          <h1 class="hero__title" style="--i: 1">
            发现、评估、集成<br >
            <span class="hero__title-accent">下一套业务能力</span>
          </h1>
          <p class="hero__lead" style="--i: 2">
            面向团队的插件目录与开发者工作台 Demo：搜索、分类筛选、登录后上传与 API 密钥管理（本地模拟数据）。
          </p>
          <div class="hero__cta" style="--i: 3">
            <n-input
              v-model:value="query"
              size="large"
              round
              placeholder="按名称或场景搜索…"
              class="hero__search"
              clearable
              @keyup.enter="focusResults"
            >
              <template #prefix>
                <span class="hero__search-icon">⌕</span>
              </template>
            </n-input>
            <n-button size="large" class="btn-accent btn-accent--lg" @click="focusResults">搜索</n-button>
          </div>
        </div>
      </section>

      <section id="plugin-results" class="filters">
        <div class="filters__row">
          <span class="filters__label">分类</span>
          <div class="filters__pills">
            <button
              v-for="c in categories"
              :key="c"
              type="button"
              class="pill"
              :class="{ 'pill--active': activeCategory === c }"
              @click="activeCategory = c"
            >
              {{ c }}
            </button>
          </div>
        </div>
      </section>

      <section class="catalog">
        <div class="catalog__head">
          <h2 class="catalog__title">精选与匹配结果</h2>
          <p class="catalog__meta">{{ filteredPlugins.length }} 款插件</p>
        </div>
        <div class="catalog__grid">
          <article
            v-for="(p, idx) in filteredPlugins"
            :key="p.id"
            class="card"
            :style="{ '--i': idx }"
          >
            <div class="card__top">
              <span class="card__badge" :data-v="p.verified ? '认证' : '社区'">{{ p.verified ? '认证' : '社区' }}</span>
              <span class="card__ver">{{ p.version }}</span>
            </div>
            <h3 class="card__name">{{ p.name }}</h3>
            <p class="card__tagline">{{ p.tagline }}</p>
            <div class="card__foot">
              <span class="card__cat">{{ p.category }}</span>
              <span class="card__dl">{{ formatDl(p.downloads) }} 次安装</span>
            </div>
            <n-button text type="primary" class="card__action" @click="toastDetail(p)">详情</n-button>
          </article>
        </div>
      </section>

      <footer class="portal-footer">
        <p>© {{ year }} Gress Store · 本页为 <strong>交互设计 Demo</strong>，数据为模拟。</p>
      </footer>
    </main>

    <n-modal
      v-model:show="showLogin"
      preset="card"
      title="开发者登录"
      class="login-modal"
      style="width: min(420px, 92vw)"
      :bordered="false"
      size="huge"
    >
      <p class="login-modal__hint">任意账号密码即可进入开发者中心（本地会话）。</p>
      <n-form ref="formRef" :model="loginForm" :rules="loginRules" label-placement="top">
        <n-form-item path="username" label="邮箱或用户名">
          <n-input v-model:value="loginForm.username" placeholder="you@company.com" autocomplete="username" />
        </n-form-item>
        <n-form-item path="password" label="密码">
          <n-input
            v-model:value="loginForm.password"
            type="password"
            placeholder="••••••••"
            show-password-on="click"
            autocomplete="current-password"
            @keyup.enter="submitLogin"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showLogin = false">取消</n-button>
          <n-button type="primary" class="btn-accent" :loading="loginLoading" @click="submitLogin">登录</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NForm, NFormItem, NInput, NModal, NSpace, useMessage } from 'naive-ui'
import { DEMO_PLUGINS, SESSION_KEY, type DemoPlugin } from './mockData'

const message = useMessage()
const year = new Date().getFullYear()
const query = ref('')
const browseMode = ref<'all' | 'search'>('all')
const activeCategory = ref<string>('全部')
const showLogin = ref(false)
const loginLoading = ref(false)
const formRef = ref<InstanceType<typeof NForm> | null>(null)
const loginForm = ref({ username: '', password: '' })
const loginRules = {
  username: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const categories = ['全部', '自动化', '效率', '安全', '数据', '协作', 'AI']

const filteredPlugins = computed(() => {
  let list = [...DEMO_PLUGINS]
  if (activeCategory.value !== '全部') {
    list = list.filter((p) => p.category === activeCategory.value)
  }
  const q = query.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (p) =>
        p.name.toLowerCase().includes(q) ||
        p.tagline.toLowerCase().includes(q) ||
        p.category.toLowerCase().includes(q)
    )
  }
  return list
})

function formatDl(n: number) {
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

function scrollPlugins() {
  document.getElementById('plugin-results')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function focusResults() {
  browseMode.value = 'search'
  scrollPlugins()
}

function openDocs() {
  message.info('文档中心为占位：可后续接入飞书 / 本站文档。')
}

function openLogin() {
  showLogin.value = true
}

function toastDetail(p: DemoPlugin) {
  message.success(`「${p.name}」详情页为占位（Demo）`)
}

async function submitLogin() {
  await formRef.value?.validate?.()
  loginLoading.value = true
  try {
    await new Promise((r) => setTimeout(r, 420))
    localStorage.setItem(SESSION_KEY, JSON.stringify({ user: loginForm.value.username, at: Date.now() }))
    message.success('已登录，正在进入开发者中心…')
    showLogin.value = false
    const w = window as any
    const path = '/plugins/appstore/developer-center'
    const router = w.__GRESS_PLUGIN_BRIDGE__?.router
    if (router?.replace) {
      await router.replace(path)
    } else if (router?.push) {
      await router.push(path)
    } else {
      window.location.href = path
    }
  } finally {
    loginLoading.value = false
  }
}
</script>

<style scoped lang="scss">
@import '../../styles/developer-portal-tokens.scss';

.portal-root {
  min-height: 100vh;
  color: var(--asdp-ink);
  font-family: var(--asdp-font-display);
  position: relative;
  overflow-x: hidden;
}

.portal-bg {
  position: fixed;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 50% at 50% -20%, var(--asdp-glow), transparent 55%),
    linear-gradient(180deg, #0e141c 0%, var(--asdp-bg-deep) 40%, #060809 100%);
  z-index: 0;
}

.portal-header {
  position: sticky;
  top: 0;
  z-index: 10;
  backdrop-filter: blur(14px);
  background: rgba(11, 15, 20, 0.72);
  border-bottom: 1px solid var(--asdp-line);
}

.portal-header__inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: var(--asdp-space-md) var(--asdp-space-xl);
  display: flex;
  align-items: center;
  gap: var(--asdp-space-xl);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--asdp-space-sm);
  color: inherit;
  text-decoration: none;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.brand__mark {
  width: 10px;
  height: 28px;
  border-radius: 3px;
  background: linear-gradient(180deg, var(--asdp-accent), #0d9488);
  box-shadow: 0 0 20px var(--asdp-glow);
}

.brand__text em {
  font-style: normal;
  color: var(--asdp-accent);
}

.nav {
  display: flex;
  gap: var(--asdp-space-xs);
  flex: 1;
}

.nav__link {
  background: transparent;
  border: none;
  color: var(--asdp-ink-muted);
  font: inherit;
  padding: var(--asdp-space-sm) var(--asdp-space-md);
  border-radius: 999px;
  cursor: pointer;
  transition: color 0.2s var(--asdp-ease), background 0.2s var(--asdp-ease);
}

.nav__link:hover {
  color: var(--asdp-ink);
  background: rgba(255, 255, 255, 0.04);
}

.nav__link--active {
  color: var(--asdp-accent);
  background: var(--asdp-accent-dim);
}

.portal-header__actions {
  display: flex;
  gap: var(--asdp-space-sm);
  align-items: center;
}

.btn-ghost {
  color: var(--asdp-ink-muted) !important;
}

.btn-accent {
  --n-color: var(--asdp-accent) !important;
  --n-color-hover: #5eead4 !important;
  --n-color-pressed: #14b8a6 !important;
  --n-text-color: #042f2e !important;
  font-weight: 600 !important;
}

.btn-accent--lg {
  border-radius: 999px !important;
}

.hero {
  position: relative;
  z-index: 1;
  padding: clamp(48px, 12vw, 120px) var(--asdp-space-xl) var(--asdp-space-2xl);
  max-width: 1120px;
  margin: 0 auto;
}

.hero__grid {
  position: absolute;
  inset: 0;
  margin: -40px -80px 0;
  background-image:
    linear-gradient(rgba(45, 212, 191, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(45, 212, 191, 0.05) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 0%, black, transparent);
  pointer-events: none;
}

.hero__content {
  position: relative;
  max-width: 720px;
}

.hero__eyebrow,
.hero__title,
.hero__lead,
.hero__cta {
  animation: rise 0.8s var(--asdp-ease) backwards;
  animation-delay: calc(0.06s * var(--i, 0));
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hero__eyebrow {
  font-size: 13px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--asdp-accent);
  margin-bottom: var(--asdp-space-md);
}

.hero__title {
  font-size: clamp(2rem, 4.5vw, 3.25rem);
  line-height: 1.08;
  font-weight: 700;
  letter-spacing: -0.03em;
  margin-bottom: var(--asdp-space-lg);
}

.hero__title-accent {
  color: transparent;
  background: linear-gradient(90deg, #5eead4, #a5f3fc, #5eead4);
  -webkit-background-clip: text;
  background-clip: text;
}

.hero__lead {
  font-size: 1.05rem;
  line-height: 1.65;
  color: var(--asdp-ink-muted);
  margin-bottom: var(--asdp-space-xl);
}

.hero__cta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--asdp-space-md);
  align-items: center;
}

.hero__search {
  flex: 1 1 240px;
  max-width: 420px;
  --n-border: 1px solid var(--asdp-line) !important;
  --n-color: rgba(255, 255, 255, 0.04) !important;
}

.hero__search-icon {
  opacity: 0.55;
  font-size: 1.1rem;
}

.filters {
  position: relative;
  z-index: 1;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 var(--asdp-space-xl) var(--asdp-space-xl);
}

.filters__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--asdp-space-md);
}

.filters__label {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--asdp-ink-muted);
}

.filters__pills {
  display: flex;
  flex-wrap: wrap;
  gap: var(--asdp-space-sm);
}

.pill {
  border: 1px solid var(--asdp-line);
  background: rgba(255, 255, 255, 0.03);
  color: var(--asdp-ink-muted);
  padding: 6px 14px;
  border-radius: 999px;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s var(--asdp-ease);
}

.pill:hover {
  color: var(--asdp-ink);
  border-color: rgba(45, 212, 191, 0.35);
}

.pill--active {
  color: #042f2e;
  background: var(--asdp-accent);
  border-color: transparent;
}

.catalog {
  position: relative;
  z-index: 1;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 var(--asdp-space-xl) clamp(48px, 8vw, 96px);
}

.catalog__head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: var(--asdp-space-xl);
  gap: var(--asdp-space-md);
}

.catalog__title {
  font-size: 1.35rem;
  font-weight: 600;
}

.catalog__meta {
  font-size: 13px;
  color: var(--asdp-ink-muted);
  font-family: var(--asdp-font-mono);
}

.catalog__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--asdp-space-lg);
}

.card {
  position: relative;
  padding: var(--asdp-space-lg);
  border-radius: var(--asdp-radius-lg);
  background: var(--asdp-bg-card);
  border: 1px solid var(--asdp-line);
  box-shadow: var(--asdp-shadow);
  backdrop-filter: blur(12px);
  transition: transform 0.35s var(--asdp-ease), border-color 0.25s ease;
  animation: rise 0.65s var(--asdp-ease) backwards;
  animation-delay: calc(0.05s * var(--i, 0) + 0.2s);
}

.card:hover {
  transform: translateY(-4px);
  border-color: rgba(45, 212, 191, 0.35);
}

.card__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--asdp-space-md);
}

.card__badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--asdp-ink-muted);
}

.card__badge[data-v='认证'] {
  background: var(--asdp-accent-dim);
  color: var(--asdp-accent);
}

.card__ver {
  font-family: var(--asdp-font-mono);
  font-size: 12px;
  color: var(--asdp-ink-muted);
}

.card__name {
  font-size: 1.2rem;
  font-weight: 600;
  margin-bottom: var(--asdp-space-xs);
}

.card__tagline {
  font-size: 13px;
  color: var(--asdp-ink-muted);
  line-height: 1.5;
  min-height: 3em;
  margin-bottom: var(--asdp-space-lg);
}

.card__foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--asdp-ink-muted);
  font-family: var(--asdp-font-mono);
  margin-bottom: var(--asdp-space-sm);
}

.card__action {
  color: var(--asdp-accent) !important;
}

.portal-footer {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: var(--asdp-space-2xl) var(--asdp-space-lg);
  font-size: 12px;
  color: var(--asdp-ink-muted);
  border-top: 1px solid var(--asdp-line);
}

.login-modal__hint {
  font-size: 13px;
  color: #64748b;
  margin-bottom: var(--asdp-space-lg);
  line-height: 1.5;
}
</style>
