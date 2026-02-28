/**
 * App Store 插件
 *
 * 参考 webhook-trigger，使用工厂函数返回 PluginManifest
 */

import type { PluginManifest } from '@keqi.gress/plugin-bridge'
import { PluginPermission } from '@keqi.gress/plugin-bridge'
import ApplicationManagement from './views/ApplicationManagement.vue'
import OperationLog from './views/OperationLog.vue'
import MiddlewareManagement from './views/MiddlewareManagement.vue'
import NodeManagement from './views/NodeManagement.vue'
import PluginMonitorDashboard from './views/PluginMonitorDashboard.vue'

// 不再需要全局类型声明，使用统一的 __GRESS_PLUGIN__ 全局变量

export interface AppStoreConfig {
  enabled?: boolean
}

const defaultConfig: Required<AppStoreConfig> = {
  enabled: true
}

/**
 * 插件工厂函数
 * 
 * 这是插件的唯一导出，宿主通过调用此函数来实例化插件
 * 
 * @param bridge - 宿主机提供的桥接对象，包含所有能力
 *   - bridge.http: HTTP 客户端
 *   - bridge.i18n: 国际化
 *   - bridge.store: 状态管理
 *   - bridge.router: 路由
 *   - bridge.ui: UI 组件
 *   - bridge.events: 事件总线
 *   - bridge.utils: 工具函数
 *   - bridge.auth: 权限
 *   - bridge.notification: 通知
 *   - bridge.app: Vue 应用实例
 * @param properties - 插件初始化配置（可选）
 * @returns 插件清单（manifest）
 */
export default (bridge: any, properties?: AppStoreConfig): PluginManifest<AppStoreConfig> => {
  console.log('[AppStore] Factory function called with bridge:', bridge)
  console.log('[AppStore] Configuration properties:', properties)

  const config: Required<AppStoreConfig> = {
    ...defaultConfig,
    ...(properties || {})
  }

  console.log('[AppStore] Merged config:', config)

  return {
    id: 'appstore',
    name: '应用商店',
    version: '1.0.0',
    description: '应用商店管理与浏览插件',
    author: {
      name: 'Gress Team'
    },
    icon: 'appstore',

    permissions: [
      PluginPermission.NETWORK_ACCESS,
      PluginPermission.ROUTER_REGISTER,
      PluginPermission.ROUTER_NAVIGATE,
      PluginPermission.COMPONENT_REGISTER,
      PluginPermission.DATA_READ,
      PluginPermission.DATA_WRITE,
      PluginPermission.STORAGE_READ,
      PluginPermission.STORAGE_WRITE,
      PluginPermission.UI_MENU
    ],

    loadStrategy: 'lazy',

    /**
     * 组件注册表（名称 -> 组件实例）
     *
     * - 后端 plugin-ui.yml 中只写组件名称（如 AppStorePage / AppStoreAdminPage）
     * - 宿主通过 PluginRuntime 获取 manifest.components 后按名称查找组件：
     *   const runtime = getPluginRuntime()
     *   const plugin = runtime.get('appstore')
     *   const comp = plugin?.manifest.components?.['AppStorePage']
     */
    components: {
      ApplicationManagement,
      OperationLog,
      MiddlewareManagement,
      NodeManagement,
      PluginMonitorDashboard
    },

    extensions: {
      // 这里不再重复定义 routes，交由后端 yml 管理，避免前后端路由信息重复维护
      routes: [],
      // 组件扩展（可选，保留以兼容旧逻辑）
      components: [],
      menus: [
        
      ]
    },

    lifecycle: {
      async install(context: any) {
        const { logger } = context
        const { ui, app } = bridge // 从 bridge 获取 UI 组件和应用实例

        logger.info('Installing AppStore plugin')

        // 注册 NaiveUI 组件
        const naiveComponents = [
          'NButton',
          'NCard',
          'NInput',
          'NPagination',
          'NSpin',
          'NTag',
          'NModal',
          'NSelect',
          'NTable',
          'NSpace',
          'NAlert',
          'NForm',
          'NFormItem',
          'NInputNumber',
          'NDataTable',
          'NDrawer',
          'NDrawerContent',
          'NEmpty',
          'NTabPane',
          'NTabs',
          'NStatistic',
          'NIcon',
          'NSwitch',
          'NDescriptions',
          'NDescriptionsItem'
        ]

        if (ui && ui.components) {
          naiveComponents.forEach(name => {
            const component = ui.components[name]
            if (component && app) {
              app.component(name, component)
            }
          })
          logger.debug('NaiveUI components registered via bridge')
        } else {
          logger.warn('UI bridge not available, skipping component registration')
        }
      },

      async activate(context: any) {
        const { logger } = context
        logger.info('Activating AppStore plugin')
      },

      async deactivate(context: any) {
        const { logger } = context
        logger.info('Deactivating AppStore plugin')
      }
    },

    config: {
      default: config
    },

    extra: {
      category: 'application',
      tags: ['appstore', 'market', 'plugin']
    }
  }
}

// 不再需要自动注册代码
// 插件通过统一的 __GRESS_PLUGIN__ 全局变量加载
// UMD 构建会自动将 default 导出赋值给 window.__GRESS_PLUGIN__
