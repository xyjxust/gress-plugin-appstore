import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { createPluginRollupOptions } from '../../gress/gress-plugin-packages/plugin-vite-externals.ts'

/**
 * App Store 插件构建配置
 *
 * 使用统一的 __GRESS_PLUGIN__ 全局变量加载模式
 */
export default defineConfig({
  plugins: [vue()],

  define: {
    'process.env': {}
  },

  build: {
    minify: true,
    cssCodeSplit: true,
    lib: {
      target: 'esnext',
      entry: fileURLToPath(new URL('./src/index.ts', import.meta.url)),
      name: '__GRESS_PLUGIN__',
      formats: ['iife'],
      fileName: () => 'appstore-frontend.umd.js'
    },
    rollupOptions: createPluginRollupOptions()
  }
})
