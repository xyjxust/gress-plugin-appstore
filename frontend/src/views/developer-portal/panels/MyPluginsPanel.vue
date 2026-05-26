<template>
  <div class="panel">
    <div class="panel__head">
      <h2 class="panel__title">我的插件</h2>
      <n-button size="small" type="primary" @click="refresh">刷新列表</n-button>
    </div>
    <n-data-table :columns="columns" :data="rows" :pagination="false" striped size="small" />
  </div>
</template>

<script setup lang="ts">
import { h, ref } from 'vue'
import { NButton, NDataTable, NTag, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { DEMO_MY_SUBMISSIONS } from '../mockData'

const message = useMessage()
const rows = ref([...DEMO_MY_SUBMISSIONS])

const statusType = (s: string): 'default' | 'info' | 'success' | 'warning' | 'error' => {
  if (s === '已通过') return 'success'
  if (s === '审核中') return 'info'
  if (s === '需修改') return 'warning'
  return 'default'
}

const columns: DataTableColumns<(typeof DEMO_MY_SUBMISSIONS)[0]> = [
  { title: '插件', key: 'name' },
  { title: '版本', key: 'version', width: 100 },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render(row) {
      return h(NTag, { type: statusType(row.status), size: 'small' }, { default: () => row.status })
    }
  },
  { title: '更新', key: 'updated', width: 120 },
  {
    title: '操作',
    key: 'actions',
    width: 140,
    render() {
      return h(
        NButton,
        { size: 'tiny', quaternary: true, onClick: () => message.info('详情抽屉为占位') },
        { default: () => '详情' }
      )
    }
  }
]

function refresh() {
  rows.value = [...DEMO_MY_SUBMISSIONS]
  message.success('已同步模拟数据')
}
</script>

<style scoped lang="scss">
@import '../../../styles/developer-portal-tokens.scss';

.panel__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.panel__title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--asdp-ink);
}
</style>
