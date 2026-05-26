export interface DemoPlugin {
  id: string
  name: string
  tagline: string
  category: string
  downloads: number
  version: string
  verified: boolean
}

export const DEMO_PLUGINS: DemoPlugin[] = [
  { id: 'p-flow', name: 'FlowKit', tagline: '编排可视化与审计导出', category: '自动化', downloads: 12800, version: '2.4.1', verified: true },
  { id: 'p-doc', name: 'DocLens', tagline: '文档索引与智能摘要', category: '效率', downloads: 6400, version: '1.9.0', verified: true },
  { id: 'p-sec', name: 'VaultLine', tagline: '密钥轮换与健康检查', category: '安全', downloads: 2100, version: '0.8.3', verified: false },
  { id: 'p-data', name: 'GridSmith', tagline: '表格权限与字段血缘', category: '数据', downloads: 9200, version: '3.1.0', verified: true },
  { id: 'p-msg', name: 'PulseBus', tagline: '站内消息与待办聚合', category: '协作', downloads: 4500, version: '1.2.2', verified: true },
  { id: 'p-ai', name: 'PromptDock', tagline: '提示词模板与评测集', category: 'AI', downloads: 17800, version: '4.0.0', verified: true }
]

export const DEMO_MY_SUBMISSIONS = [
  { id: 's1', name: 'FlowKit', status: '审核中', version: '2.5.0', updated: '2026-05-10' },
  { id: 's2', name: 'Sidecar Utils', status: '已通过', version: '1.0.0', updated: '2026-04-28' },
  { id: 's3', name: 'Legacy Bridge', status: '需修改', version: '0.2.1', updated: '2026-05-02' }
]

export const SESSION_KEY = 'appstore-dev-portal-demo-session'
