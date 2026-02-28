import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * App Store 插件构建配置
 *
 * 使用统一的 __GRESS_PLUGIN__ 全局变量加载模式
 *
 * 构建流程：
 * 1. 打包为 UMD/IIFE 格式
 * 2. 导出工厂函数（不是实例）
 * 3. UMD 构建会自动将 default 导出赋值给 window.__GRESS_PLUGIN__
 * 4. 宿主通过 window.__GRESS_PLUGIN__ 获取插件模块
 * 5. 宿主调用工厂函数实例化：pluginModule.default(bridge, properties)
 *
 * 注意：
 * - 插件不会自动实例化
 * - 插件不会自动执行
 * - 只有宿主调用工厂函数时才会创建实例
 * - 使用统一的 __GRESS_PLUGIN__ 全局变量，避免命名冲突
 */
export default defineConfig({
  plugins: [vue()],

  define: {
    // Define process.env for browser compatibility
    'process.env': {}
  },

  build: {
    minify: false,
    cssCodeSplit: true, // 将组件的 style 打包到 js 文件中
    lib: {
      target: 'esnext',
      entry: fileURLToPath(new URL('./src/index.ts', import.meta.url)),
      name: '__GRESS_PLUGIN__',
      formats: ['iife'],
      fileName: () => 'appstore-frontend.umd.js'
    },
    rollupOptions: {
      // 由主应用提供这些依赖，避免重复打包
      // 注意：只将 echarts 设为 external，vue-echarts 需要打包进来
      external: ['vue', 'naive-ui', 'echarts'],
      output: {
        globals: {
          vue: 'Vue',
          'naive-ui': 'NaiveUI',
          echarts: 'echarts'
        },
        exports: 'named'
      }
    }
  }
})