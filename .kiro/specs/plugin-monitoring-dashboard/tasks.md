# 实现计划: 插件监控页面

## 概述

本实现计划将插件监控页面功能分解为一系列增量式的开发任务。每个任务都建立在前面任务的基础上，确保功能逐步完善并可随时验证。实现将按照后端 → 前端 → 集成的顺序进行，优先实现核心监控功能，然后添加历史数据和高级特性。

## 任务列表

- [x] 1. 创建数据模型和 DTO 类
  - 创建 PluginMonitorStatus, PluginMemoryInfo, PluginMonitorDetail, MonitorOverview 等 DTO 类
  - 创建 ClassLoaderInfo 辅助类
  - 创建 PluginMonitorSnapshot, PluginStateChangeLog 实体类
  - _需求: 所有需求的数据基础_

- [x] 2. 实现插件状态收集器
  - [x] 2.1 实现 PluginStatusCollector 服务
    - 实现 collectAllStatus() 方法收集所有插件状态
    - 实现 collectStatus(pluginId) 方法收集单个插件状态
    - 实现 getClassLoaderInfo(pluginId) 方法获取类加载器信息
    - 集成 PluginManager 和 ApplicationDao
    - _需求: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.5_
  
  - [ ]* 2.2 编写 PluginStatusCollector 的属性测试
    - **Property 2: 插件状态更新一致性**
    - **验证: 需求 1.4**
  
  - [ ]* 2.3 编写 PluginStatusCollector 的属性测试
    - **Property 3: 插件加载状态检测正确性**
    - **验证: 需求 2.1, 2.2, 2.3**
  
  - [ ]* 2.4 编写 PluginStatusCollector 的属性测试
    - **Property 4: 类加载器信息完整性**
    - **验证: 需求 2.5**

- [x] 3. 实现插件内存收集器
  - [x] 3.1 实现 PluginMemoryCollector 服务
    - 实现 collectMemoryInfo(pluginId) 方法收集内存信息
    - 实现 getTotalMemoryUsage() 方法计算总内存使用
    - 实现 estimatePluginMemory() 方法估算插件内存
    - 实现 formatMemorySize() 方法格式化内存大小
    - _需求: 3.1, 3.2, 3.5_
  
  - [ ]* 3.2 编写 PluginMemoryCollector 的属性测试
    - **Property 5: 内存使用计算有效性**
    - **验证: 需求 3.1**
  
  - [ ]* 3.3 编写内存格式化的属性测试
    - **Property 6: 内存大小格式化正确性**
    - **验证: 需求 3.2**
  
  - [ ]* 3.4 编写 JVM 内存信息的属性测试
    - **Property 8: JVM 内存信息完整性**
    - **验证: 需求 3.5**

- [x] 4. 实现监控数据缓存
  - [x] 4.1 实现 MonitorDataCache 服务
    - 实现 getAllStatus() 方法从缓存获取数据
    - 实现 updateAllStatus() 方法更新缓存
    - 实现 getStatus(pluginId) 方法获取单个插件缓存
    - 实现缓存过期机制（TTL 5秒）
    - 使用 ConcurrentHashMap 保证线程安全
    - _需求: 10.3_
  
  - [ ]* 4.2 编写 MonitorDataCache 的单元测试
    - 测试缓存存储和读取
    - 测试缓存过期机制
    - 测试并发访问安全性

- [x] 5. 实现监控服务核心逻辑
  - [x] 5.1 实现 PluginMonitorService 服务
    - 实现 getAllPluginStatus() 方法获取所有插件状态
    - 实现 getPluginDetail(pluginId) 方法获取插件详情
    - 实现 getMonitorOverview() 方法获取监控概览
    - 集成 PluginStatusCollector, PluginMemoryCollector, MonitorDataCache
    - 使用 CompletableFuture 异步收集内存信息
    - _需求: 1.1, 1.2, 1.3, 1.4, 4.1, 4.2, 4.3, 4.4, 4.5_
  
  - [ ]* 5.2 编写插件列表渲染的属性测试
    - **Property 1: 插件列表渲染完整性**
    - **验证: 需求 1.2, 1.3**
  
  - [ ]* 5.3 编写插件详情的属性测试
    - **Property 9: 插件详情渲染完整性**
    - **验证: 需求 4.2, 4.3, 4.4, 4.5**

- [x] 6. 检查点 - 核心监控功能验证
  - 确保所有核心服务测试通过
  - 手动测试数据收集功能
  - 询问用户是否有问题

- [x] 7. 实现监控 REST API 控制器
  - [x] 7.1 实现 PluginMonitorController
    - 实现 GET /api/plugins/appstore/monitor/status 接口
    - 实现 GET /api/plugins/appstore/monitor/status/{pluginId} 接口
    - 实现 GET /api/plugins/appstore/monitor/overview 接口
    - 添加 @RestController 和 @RequestMapping 注解
    - 集成 PluginMonitorService
    - _需求: 6.1, 6.2, 6.3_
  
  - [ ]* 7.2 编写 API 响应格式的属性测试
    - **Property 10: API 响应格式一致性**
    - **验证: 需求 6.3**
  
  - [ ]* 7.3 编写 API 错误处理的属性测试
    - **Property 12: API 错误响应完整性**
    - **验证: 需求 6.5**
  
  - [ ]* 7.4 编写 API 性能测试
    - **Property 11: API 响应时间性能**
    - **验证: 需求 6.4**

- [x] 8. 实现错误处理和告警逻辑
  - [x] 8.1 实现 MonitorErrorHandler 工具类
    - 实现 handleCollectionError() 方法处理数据收集错误
    - 实现 handleTimeout() 方法处理超时错误
    - 实现 handleMemoryUnavailable() 方法处理内存信息不可用
    - _需求: 2.4, 3.4, 8.1_
  
  - [x] 8.2 在 PluginMonitorService 中集成错误处理
    - 添加 try-catch 块捕获异常
    - 使用 MonitorErrorHandler 处理错误
    - 确保部分失败不影响整体结果
    - _需求: 10.5_
  
  - [x] 8.3 实现内存告警逻辑
    - 在 PluginMonitorStatus 中添加 isMemoryWarning 字段
    - 在 PluginMemoryCollector 中检查内存阈值（默认 500MB）
    - 在 MonitorOverview 中统计异常插件数量
    - _需求: 3.3, 8.2, 8.3_
  
  - [ ]* 8.4 编写内存告警的属性测试
    - **Property 7: 内存告警阈值检测**
    - **验证: 需求 3.3, 8.2**
  
  - [ ]* 8.5 编写异常统计的属性测试
    - **Property 16: 异常插件统计准确性**
    - **验证: 需求 8.3**
  
  - [ ]* 8.6 编写错误状态显示的属性测试
    - **Property 15: 错误状态显示一致性**
    - **验证: 需求 2.4, 8.1, 8.4**
  
  - [ ]* 8.7 编写故障隔离的属性测试
    - **Property 20: 监控服务故障隔离**
    - **验证: 需求 10.5**

- [x] 9. 实现监控历史数据持久化
  - [x] 9.1 创建数据库表
    - 创建 plugin_monitor_snapshot 表
    - 创建 plugin_state_change_log 表
    - 添加索引优化查询性能
    - _需求: 7.1, 7.2_
  
  - [x] 9.2 实现 MonitorHistoryDao
    - 实现 saveSnapshot() 方法保存监控快照
    - 实现 queryHistory() 方法查询历史数据
    - 实现 deleteExpiredData() 方法清理过期数据
    - _需求: 7.4, 7.5_
  
  - [x] 9.3 实现状态变更监听器
    - 创建 PluginStateChangeListener 监听插件状态变化
    - 在状态变化时记录到 plugin_state_change_log 表
    - _需求: 7.1, 7.2_
  
  - [x] 9.4 实现定时任务保存监控快照
    - 使用 @Scheduled 注解创建定时任务
    - 每分钟保存一次所有插件的监控快照
    - _需求: 7.3_
  
  - [x] 9.5 实现定时任务清理过期数据
    - 使用 @Scheduled 注解创建定时任务
    - 每天清理 7 天前的历史数据
    - _需求: 7.4_
  
  - [x] 9.6 在 PluginMonitorService 中添加历史查询方法
    - 实现 getPluginHistory(pluginId, timeRange) 方法
    - 实现 parseTimeRange() 辅助方法解析时间范围
    - _需求: 7.5_
  
  - [x] 9.7 在 PluginMonitorController 中添加历史查询接口
    - 实现 GET /api/plugins/appstore/monitor/history/{pluginId} 接口
    - _需求: 7.5_
  
  - [ ]* 9.8 编写状态变更记录的属性测试
    - **Property 13: 状态变更记录完整性**
    - **验证: 需求 7.1, 7.2**
  
  - [ ]* 9.9 编写历史数据保留的属性测试
    - **Property 14: 历史数据保留策略**
    - **验证: 需求 7.4**

- [ ] 10. 检查点 - 后端功能完整性验证
  - 确保所有后端测试通过
  - 使用 Postman 或 curl 测试所有 API 接口
  - 验证数据持久化和历史查询功能
  - 询问用户是否有问题

- [x] 11. 实现前端类型定义
  - [x] 11.1 创建 pluginMonitor.ts 类型文件
    - 定义 PluginMonitorStatus 接口
    - 定义 PluginMemoryInfo 接口
    - 定义 PluginMonitorDetail 接口
    - 定义 MonitorOverview 接口
    - 定义 PluginMonitorHistory 接口
    - 定义 ClassLoaderInfo 接口
    - _需求: 所有前端需求的类型基础_

- [x] 12. 实现前端 API 客户端
  - [x] 12.1 创建 pluginMonitor.ts API 客户端
    - 实现 getAllStatus() 方法
    - 实现 getDetail(pluginId) 方法
    - 实现 getHistory(pluginId, timeRange) 方法
    - 实现 getOverview() 方法
    - 使用现有的 http 工具类
    - _需求: 6.1, 6.2, 7.5_
  
  - [ ]* 12.2 编写 API 客户端的单元测试
    - 测试 API 调用和响应处理
    - 测试错误处理

- [x] 13. 实现监控页面主组件
  - [x] 13.1 创建 PluginMonitorDashboard.vue 组件
    - 实现概览卡片显示（总插件数、运行中、已停止、异常）
    - 实现刷新控制（手动刷新按钮、自动刷新开关）
    - 实现插件列表表格（使用 NDataTable）
    - 实现数据加载和刷新逻辑
    - 实现自动刷新定时器（默认 5 秒）
    - _需求: 1.1, 1.2, 1.3, 5.1, 5.2, 5.4, 5.5_
  
  - [x] 13.2 实现插件状态渲染函数
    - 实现 renderState() 函数渲染运行状态（使用 NTag 组件）
    - 实现 renderLoaded() 函数渲染加载状态
    - 实现 renderMemory() 函数渲染内存使用（带告警高亮）
    - 实现 renderActions() 函数渲染操作按钮
    - _需求: 1.3, 2.2, 2.3, 3.2, 3.3, 8.1, 8.2_
  
  - [x] 13.3 实现错误状态显示
    - 在插件列表顶部显示异常插件数量
    - 为异常插件添加错误标识图标
    - 提供查看详细错误信息的入口
    - _需求: 8.1, 8.3, 8.4_

- [x] 14. 实现插件详情组件
  - [x] 14.1 创建 PluginMonitorDetail.vue 组件
    - 实现基本信息展示（插件ID、名称、版本、状态）
    - 实现内存信息展示（插件内存、JVM 内存）
    - 实现元数据展示（作者、描述、主页）
    - 实现依赖信息展示
    - 实现配置信息展示
    - 实现运行时信息展示（启动时间、运行时长）
    - 实现类加载器信息展示
    - _需求: 4.1, 4.2, 4.3, 4.4, 4.5, 2.5_
  
  - [x] 14.2 在 PluginMonitorDashboard 中集成详情组件
    - 添加详情抽屉（使用 NDrawer 组件）
    - 实现点击插件条目打开详情
    - 实现详情数据加载
    - _需求: 4.1_

- [x] 15. 实现历史数据图表组件
  - [x] 15.1 创建 PluginMonitorHistory.vue 组件
    - 集成图表库（如 ECharts 或 Chart.js）
    - 实现内存使用趋势图
    - 实现状态变更时间线
    - 实现时间范围选择器（1小时、24小时、7天）
    - _需求: 7.5_
  
  - [x] 15.2 在 PluginMonitorDetail 中集成历史图表
    - 添加历史数据标签页
    - 实现历史数据加载和展示
    - _需求: 7.5_

- [x] 16. 实现响应式设计
  - [x] 16.1 添加响应式样式
    - 使用 CSS 媒体查询实现响应式布局
    - 桌面端：完整表格和详情
    - 平板端：自适应列宽
    - 移动端：简化视图（卡片列表）
    - _需求: 9.1, 9.2, 9.3, 9.4_
  
  - [ ]* 16.2 编写跨设备数据一致性测试
    - **Property 17: 跨设备数据一致性**
    - **验证: 需求 9.5**

- [x] 17. 在插件主入口注册监控页面
  - [x] 17.1 在 index.ts 中注册 PluginMonitorDashboard 组件
    - 添加组件到 components 注册表
    - _需求: 所有前端需求_
  
  - [x] 17.2 创建或更新 plugin-ui.yml 配置
    - 添加监控页面路由配置
    - 添加菜单项配置
    - _需求: 所有前端需求_

- [ ] 18. 检查点 - 前端功能完整性验证
  - 确保前端页面正常显示
  - 测试所有交互功能（刷新、详情、历史）
  - 测试响应式布局
  - 询问用户是否有问题

- [ ] 19. 性能优化和测试
  - [ ] 19.1 实现性能优化
    - 优化缓存策略减少重复计算
    - 优化数据库查询添加索引
    - 优化前端渲染使用虚拟滚动（如果插件数量很多）
    - _需求: 10.1, 10.2, 10.3, 10.4_
  
  - [ ]* 19.2 编写单插件性能测试
    - **Property 18: 单插件数据采集性能**
    - **验证: 需求 10.1**
  
  - [ ]* 19.3 编写批量数据性能测试
    - **Property 19: 批量数据采集性能**
    - **验证: 需求 10.2**
  
  - [ ]* 19.4 执行压力测试
    - 测试高频刷新场景（每秒 10 次）
    - 测试大量插件场景（100+ 插件）
    - 测试长时间运行稳定性

- [ ] 20. 集成测试和文档
  - [ ]* 20.1 编写端到端集成测试
    - 测试完整的监控数据流（后端 → API → 前端）
    - 测试状态变更和历史记录
    - 测试错误处理和告警
  
  - [ ] 20.2 编写用户文档
    - 编写监控页面使用说明
    - 编写 API 接口文档
    - 编写配置说明（刷新间隔、内存阈值等）
  
  - [ ] 20.3 编写开发者文档
    - 编写架构说明
    - 编写扩展指南（如何添加新的监控指标）
    - 编写故障排查指南

- [ ] 21. 最终验收
  - 确保所有测试通过
  - 验证所有需求都已实现
  - 进行完整的功能演示
  - 询问用户是否满意

## 注意事项

1. **增量开发**: 每个任务都应该是可独立测试和验证的
2. **测试优先**: 核心功能应该先编写测试，确保正确性
3. **性能关注**: 在实现过程中持续关注性能指标
4. **错误处理**: 确保所有错误情况都有适当的处理
5. **文档同步**: 在实现过程中同步更新文档

## 可选任务说明

标记为 `*` 的任务是可选的测试任务。这些任务对于确保代码质量很重要，但如果时间紧迫，可以先实现核心功能，后续再补充测试。
