/**
 * 开发环境入口文件
 * 
 * 用于在开发服务器中加载和渲染插件
 */

import { createApp, h } from 'vue'
import * as naive from 'naive-ui'
import pluginFactory from './index'
import type { PluginManifest } from '@keqi.gress/plugin-bridge'

// 类型声明
type PluginFactory = (bridge: any, properties?: any) => PluginManifest<any>
const factory = pluginFactory as PluginFactory

// 创建 Vue 应用实例
const app = createApp({})

// 注册所有 NaiveUI 组件
const naiveComponents: Record<string, any> = {}
const naiveComponentNames = [
  'NButton', 'NCard', 'NInput', 'NPagination', 'NSpin', 'NTag',
  'NModal', 'NSelect', 'NTable', 'NSpace', 'NAlert', 'NForm',
  'NFormItem', 'NInputNumber', 'NDataTable', 'NDrawer', 'NDrawerContent',
  'NEmpty', 'NTabPane', 'NTabs', 'NStatistic', 'NIcon', 'NSwitch',
  'NDescriptions', 'NDescriptionsItem', 'NMessageProvider', 'NDialogProvider',
  'NConfigProvider', 'NLoadingBarProvider', 'NNotificationProvider'
]

naiveComponentNames.forEach(name => {
  const component = (naive as any)[name]
  if (component) {
    naiveComponents[name] = component
    app.component(name, component)
  }
})

console.log('[Dev] NaiveUI components registered:', Object.keys(naiveComponents))

// 模拟 bridge 对象（开发环境）
const mockBridge = {
  http: {
    get: async (url: string) => {
      const response = await fetch(url)
      return response.json()
    },
    post: async (url: string, data: any) => {
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      })
      return response.json()
    },
    put: async (url: string, data: any) => {
      const response = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
      })
      return response.json()
    },
    delete: async (url: string) => {
      const response = await fetch(url, { method: 'DELETE' })
      return response.json()
    }
  },
  i18n: {
    t: (key: string) => key,
    locale: 'zh-CN'
  },
  store: {
    state: {},
    getters: {},
    commit: () => {},
    dispatch: () => {}
  },
  router: {
    push: (path: string) => {
      console.log('[Router] Push:', path)
      window.history.pushState({}, '', path)
    },
    replace: (path: string) => {
      console.log('[Router] Replace:', path)
      window.history.replaceState({}, '', path)
    },
    currentRoute: {
      path: window.location.pathname,
      query: {},
      params: {}
    }
  },
  ui: {
    components: naiveComponents
  },
  events: {
    on: () => {},
    off: () => {},
    emit: () => {}
  },
  utils: {},
  auth: {
    hasPermission: () => true,
    getUser: () => ({ id: 'dev-user', name: 'Developer' })
  },
  notification: {
    success: (msg: string) => console.log('[Success]', msg),
    error: (msg: string) => console.error('[Error]', msg),
    warning: (msg: string) => console.warn('[Warning]', msg),
    info: (msg: string) => console.info('[Info]', msg)
  },
  app: app
}

// 加载插件
let manifest: PluginManifest<any>
try {
  manifest = factory(mockBridge, { enabled: true })
  console.log('[Dev] Plugin loaded:', manifest)
} catch (error) {
  console.error('[Dev] Failed to load plugin:', error)
  throw error
}

// 渲染插件组件
// 支持通过 URL 参数 ?component=ComponentName 来选择组件
const componentNames = Object.keys(manifest.components || {})
const urlParams = new URLSearchParams(window.location.search)
const requestedComponent = urlParams.get('component') || componentNames[0]

if (componentNames.length > 0) {
  const componentName = componentNames.includes(requestedComponent) 
    ? requestedComponent 
    : componentNames[0]
  const Component = manifest.components![componentName]
  
  console.log(`[Dev] Rendering component: ${componentName}`)
  console.log(`[Dev] Available components: ${componentNames.join(', ')}`)
  
  // 创建根组件，显示插件信息和组件选择器
  // 使用 render 函数而不是 template，确保组件正确渲染
  app.component('DevRoot', {
    components: {
      PluginComponent: Component,
      NConfigProvider: naiveComponents.NConfigProvider || naive.NConfigProvider,
      NMessageProvider: naiveComponents.NMessageProvider || naive.NMessageProvider,
      NDialogProvider: naiveComponents.NDialogProvider || naive.NDialogProvider,
      NNotificationProvider: naiveComponents.NNotificationProvider || naive.NNotificationProvider
    },
    data() {
      return {
        currentComponent: componentName,
        availableComponents: componentNames,
        manifest: manifest
      }
    },
    methods: {
      switchComponent(name: string) {
        window.location.search = `?component=${name}`
      }
    },
    render() {
      return h('div', { style: 'padding: 20px;' }, [
        h('div', {
          style: 'margin-bottom: 20px; padding: 16px; background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);'
        }, [
          h('h1', { style: 'margin-bottom: 8px; color: #333;' }, this.manifest.name),
          h('p', { style: 'color: #666; margin-bottom: 4px;' }, `ID: ${this.manifest.id}`),
          h('p', { style: 'color: #666; margin-bottom: 4px;' }, `Version: ${this.manifest.version}`),
          h('p', { style: 'color: #666; margin-bottom: 12px;' }, `Description: ${this.manifest.description}`),
          h('div', {
            style: 'margin-top: 12px; padding-top: 12px; border-top: 1px solid #eee;'
          }, [
            h('p', { style: 'color: #666; margin-bottom: 8px; font-weight: 500;' }, '组件选择:'),
            h('div', {
              style: 'display: flex; gap: 8px; flex-wrap: wrap;'
            }, this.availableComponents.map((name: string) => 
              h('button', {
                key: name,
                onClick: () => this.switchComponent(name),
                style: {
                  padding: '6px 12px',
                  border: this.currentComponent === name ? '2px solid #18a058' : '1px solid #ddd',
                  borderRadius: '4px',
                  background: this.currentComponent === name ? '#e8f5e9' : 'white',
                  cursor: 'pointer',
                  fontSize: '14px'
                }
              }, name)
            ))
          ])
        ]),
        h('div', {
          style: 'background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); padding: 20px;'
        }, [
          h(Component)
        ])
      ])
    }
  })
  
  // 创建根组件并挂载到 DOM
  const NConfigProvider = naiveComponents.NConfigProvider
  const RootApp = NConfigProvider 
    ? {
        render() {
          return h(NConfigProvider, {}, {
            default: () => h('DevRoot')
          })
        }
      }
    : {
        render() {
          return h('DevRoot')
        }
      }
  
  // 将 RootApp 注册为根组件并挂载
  app.component('RootApp', RootApp)
  
  // 创建新的应用实例，使用 RootApp 作为根组件
  const rootAppInstance = createApp(RootApp)
  // 注册所有 NaiveUI 组件
  naiveComponentNames.forEach(name => {
    const component = (naive as any)[name]
    if (component) {
      rootAppInstance.component(name, component)
    }
  })
  rootAppInstance.mount('#app')
} else {
  // 如果没有组件，显示提示信息
  app.component('DevRoot', {
    template: `
      <div style="padding: 20px;">
        <div style="padding: 20px; background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
          <h1 style="margin-bottom: 8px;">${manifest.name}</h1>
          <p style="color: #666;">No components found in plugin manifest.</p>
          <p style="color: #999; margin-top: 8px;">Available components: ${componentNames.join(', ') || 'None'}</p>
        </div>
      </div>
    `
  })
  
  app.mount('#app')
}

// 导出以便调试
if (typeof window !== 'undefined') {
  const win = window as any
  win.__PLUGIN_MANIFEST__ = manifest
  win.__BRIDGE__ = mockBridge
  console.log('[Dev] Plugin manifest and bridge available on window.__PLUGIN_MANIFEST__ and window.__BRIDGE__')
}
