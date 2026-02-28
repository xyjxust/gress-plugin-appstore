/// <reference types="vite/client" />

// Gress Plugin Dev Server 注入的全局变量
declare const __PLUGIN_NAME__: string
declare const __API_PREFIX__: string
declare const __BACKEND_URL__: string

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
