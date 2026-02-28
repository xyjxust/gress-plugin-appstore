# Task 15 实现验证 - 历史数据图表组件

## 任务概述

实现插件监控历史数据的可视化展示，包括内存使用趋势图和状态变更时间线。

## 实现内容

### 15.1 创建 PluginMonitorHistory.vue 组件 ✅

**文件**: `frontend/src/components/PluginMonitorHistory.vue`

**实现功能**:

1. **图表库集成**
   - 安装并集成 ECharts 和 vue-echarts
   - 注册必要的 ECharts 组件（LineChart, ScatterChart, 各种组件）
   - 使用 Canvas 渲染器提供最佳性能

2. **内存使用趋势图**
   - 折线图展示内存使用随时间的变化
   - 内存值自动转换为 MB 单位
   - 平滑曲线和渐变填充效果
   - 悬停提示显示时间和内存值
   - 支持数据缩放（DataZoom）

3. **状态变更时间线**
   - 阶梯线图展示插件状态变化
   - 散点标记状态变更点
   - 状态映射：
     - STARTED (运行中) = 3
     - STARTING/STOPPING (变更中) = 2
     - STOPPED (已停止) = 1
     - CREATED/DISABLED (未启动) = 0
   - 不同状态使用不同颜色标识
   - 支持数据缩放

4. **时间范围选择器**
   - 三个预设时间范围：1小时、24小时、7天
   - 单选按钮组切换时间范围
   - 切换时自动重新加载数据

5. **数据加载和刷新**
   - 自动加载历史数据
   - 手动刷新按钮
   - 加载状态指示器
   - 空状态提示

**关键代码片段**:

```typescript
// 内存趋势图配置
const memoryChartOption = computed(() => {
  const timestamps = historyData.value.map(item => new Date(item.timestamp))
  const memoryValues = historyData.value.map(item => {
    return item.memoryUsage ? (item.memoryUsage / (1024 * 1024)).toFixed(2) : 0
  })
  
  return {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: timestamps },
    yAxis: { type: 'value', name: '内存 (MB)' },
    dataZoom: [{ type: 'slider', start: 0, end: 100 }],
    series: [{
      name: '内存使用',
      type: 'line',
      data: memoryValues,
      smooth: true,
      areaStyle: { /* 渐变填充 */ }
    }]
  }
})

// 状态时间线配置
const stateTimelineOption = computed(() => {
  // 提取状态变更点
  const stateChanges = /* 检测状态变化 */
  
  return {
    series: [
      { type: 'line', step: 'end' },  // 阶梯线
      { type: 'scatter', data: changePoints }  // 变更点
    ]
  }
})
```

### 15.2 在 PluginMonitorDetail 中集成历史图表 ✅

**文件**: `frontend/src/components/PluginMonitorDetail.vue`

**实现功能**:

1. **标签页布局**
   - 使用 NTabs 组件创建标签页
   - "详细信息" 标签页：原有的所有详情内容
   - "历史数据" 标签页：新增的历史图表组件

2. **历史组件集成**
   - 导入 PluginMonitorHistory 组件
   - 传递 pluginId 属性
   - 自动加载和展示历史数据

3. **用户体验优化**
   - 标签页动画效果
   - 保持原有详情功能完整性
   - 无缝切换标签页

**关键代码片段**:

```vue
<template>
  <div class="plugin-monitor-detail">
    <n-tabs type="line" animated>
      <!-- 详情标签页 -->
      <n-tab-pane name="detail" tab="详细信息">
        <!-- 原有的所有详情卡片 -->
      </n-tab-pane>

      <!-- 历史数据标签页 -->
      <n-tab-pane name="history" tab="历史数据">
        <plugin-monitor-history :plugin-id="pluginId" />
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<script setup lang="ts">
import PluginMonitorHistory from './PluginMonitorHistory.vue'
// ... 其他导入和逻辑
</script>
```

## 依赖安装

```bash
npm install echarts vue-echarts --save
```

**安装的包**:
- `echarts`: 强大的数据可视化库
- `vue-echarts`: ECharts 的 Vue 3 封装

## 验证结果

### 构建验证 ✅

```bash
npm run build
```

**结果**: 构建成功
- 输出文件: `dist/appstore-frontend.umd.js` (1,873.75 kB, gzip: 370.37 kB)
- 包含 657 个模块
- 构建时间: 2.32s

### TypeScript 类型检查 ✅

```bash
getDiagnostics
```

**结果**: 无类型错误
- PluginMonitorHistory.vue: 无诊断问题
- PluginMonitorDetail.vue: 无诊断问题

## 需求验证

### 需求 7.5: 历史监控数据查询 ✅

**验收标准**: THE System SHALL 提供查询历史监控数据的接口

**验证**:
- ✅ 使用 `pluginMonitorApi.getHistory()` 调用后端接口
- ✅ 支持时间范围参数（1h, 24h, 7d）
- ✅ 数据按时间戳排序
- ✅ 错误处理和空状态显示

## 功能特性

### 1. 内存使用趋势图

**特性**:
- 📊 折线图展示内存使用趋势
- 📈 平滑曲线和渐变填充
- 🔍 悬停提示显示详细信息
- 📏 数据缩放支持
- 💾 自动单位转换（字节 → MB）

### 2. 状态变更时间线

**特性**:
- 📊 阶梯线图展示状态变化
- 🔴 散点标记状态变更点
- 🎨 不同状态不同颜色
- 📅 时间轴展示
- 🔍 悬停提示显示状态信息

### 3. 时间范围选择

**特性**:
- ⏰ 三个预设时间范围
- 🔄 快速切换
- 🔃 自动重新加载数据

### 4. 用户体验

**特性**:
- 🔄 手动刷新按钮
- ⏳ 加载状态指示
- 📭 空状态友好提示
- 🎬 标签页动画效果
- 📱 响应式图表（自动调整大小）

## 技术实现亮点

### 1. 性能优化

- **按需加载**: 只注册使用的 ECharts 组件
- **Canvas 渲染**: 使用 Canvas 渲染器提供最佳性能
- **计算属性**: 使用 computed 缓存图表配置
- **自动调整**: 图表自动响应容器大小变化

### 2. 数据处理

- **状态映射**: 将字符串状态映射为数值便于绘图
- **变更检测**: 智能检测状态变更点
- **数据排序**: 确保时间序列正确
- **单位转换**: 自动转换内存单位

### 3. 用户体验

- **交互式图表**: 支持缩放、悬停提示
- **视觉反馈**: 加载状态、空状态
- **颜色编码**: 不同状态使用语义化颜色
- **时间格式化**: 友好的时间显示格式

## 文件清单

### 新增文件

1. `frontend/src/components/PluginMonitorHistory.vue` - 历史数据图表组件

### 修改文件

1. `frontend/src/components/PluginMonitorDetail.vue` - 集成历史图表标签页
2. `frontend/package.json` - 添加 echarts 和 vue-echarts 依赖

## 使用示例

### 在详情页面查看历史数据

1. 打开插件监控页面
2. 点击任意插件查看详情
3. 切换到"历史数据"标签页
4. 选择时间范围（1小时/24小时/7天）
5. 查看内存使用趋势图和状态变更时间线
6. 使用数据缩放功能查看特定时间段
7. 悬停在图表上查看详细数据

### API 调用示例

```typescript
// 获取 1 小时的历史数据
const history = await pluginMonitorApi.getHistory('plugin-id', '1h')

// 获取 24 小时的历史数据
const history = await pluginMonitorApi.getHistory('plugin-id', '24h')

// 获取 7 天的历史数据
const history = await pluginMonitorApi.getHistory('plugin-id', '7d')
```

## 后续优化建议

### 功能增强

1. **导出功能**: 支持导出图表为图片或 PDF
2. **对比功能**: 支持多个插件的历史数据对比
3. **告警标记**: 在图表上标记告警事件
4. **自定义时间范围**: 支持用户自定义时间范围

### 性能优化

1. **数据采样**: 对于大量数据点进行采样
2. **虚拟滚动**: 对于超长时间范围使用虚拟滚动
3. **增量加载**: 支持分页或增量加载历史数据

### 用户体验

1. **实时更新**: 支持历史图表的实时更新
2. **快捷操作**: 添加快捷键支持
3. **主题适配**: 支持深色/浅色主题切换

## 总结

✅ **任务 15 已完成**

成功实现了插件监控历史数据的可视化展示功能，包括：

1. ✅ 创建了功能完整的 PluginMonitorHistory 组件
2. ✅ 集成了 ECharts 图表库
3. ✅ 实现了内存使用趋势图
4. ✅ 实现了状态变更时间线
5. ✅ 实现了时间范围选择器
6. ✅ 在详情页面添加了历史数据标签页
7. ✅ 通过了构建和类型检查验证

该功能为用户提供了直观的历史数据可视化，帮助分析插件的长期运行趋势和状态变化，满足需求 7.5 的所有验收标准。
