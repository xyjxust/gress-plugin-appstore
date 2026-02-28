/**
 * Gress Plugin AppStore 开发服务器配置
 */

export default {
  // 插件名称
  pluginName: 'appstore',
  
  // 后端服务地址
  backendUrl: 'http://localhost:8080',
  
  // 开发服务器端口
  port: 3001,
  
  // API 前缀
  apiPrefix: '/api/plugin/appstore',
  
  // 启用热更新
  hmr: true,
  
  // 不自动打开浏览器
  open: false,
    // 自动扫描开启（默认）
    autoScanRoutes: true,

    // 只配置特殊路由
    devRoutes: [

    ],
  
  // 自定义 Vite 配置（可选）
  vite: {
    resolve: {
      alias: {
        '@': '/src'
      }
    }
  }
}
