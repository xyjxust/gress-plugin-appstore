<template>
  <n-button v-if="activeTab === 'local'" @click="$emit('aggregate')">
    <template #icon>
      <n-icon><component :is="appsOutline" /></n-icon>
    </template>
    聚合应用管理
  </n-button>
  <n-button v-if="activeTab === 'local'" type="primary" @click="$emit('upload')">
    <template #icon>
      <n-icon><component :is="cloudUploadOutline" /></n-icon>
    </template>
    上传应用包
  </n-button>
  <n-button :loading="refreshLoading" @click="$emit('refresh')">
    <template #icon>
      <n-icon><component :is="refresh" /></n-icon>
    </template>
    刷新
  </n-button>
</template>

<script setup lang="ts">
defineProps<{
  activeTab: string
  refreshLoading: boolean
  appsOutline: unknown
  cloudUploadOutline: unknown
  refresh: unknown
}>()

defineEmits<{
  (e: 'aggregate'): void
  (e: 'upload'): void
  (e: 'refresh'): void
}>()
</script>

