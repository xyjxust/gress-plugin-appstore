<template>
  <n-card size="small" class="tag-card">
    <div class="tag-card__header">
      <div class="tag-card__title">应用标签</div>
      <n-button text size="tiny" @click="$emit('select', '')">全部</n-button>
    </div>
    <div class="tag-list">
      <button
        v-for="t in options"
        :key="t.id"
        class="tag-list__item"
        :class="{ 'tag-list__item--active': activeTag === t.id }"
        @click="$emit('select', t.id)"
      >
        <span class="tag-list__label">{{ t.label }}</span>
        <span class="tag-list__desc">{{ t.desc }}</span>
      </button>
    </div>
  </n-card>
</template>

<script setup lang="ts">
defineProps<{
  options: Array<{ id: string; label: string; desc?: string }>
  activeTag: string
}>()

defineEmits<{
  (e: 'select', tagId: string): void
}>()
</script>

<style scoped>
.tag-card :deep(.n-card__content) {
  padding: 12px;
}

.tag-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.tag-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.tag-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 280px);
  overflow: auto;
  padding-right: 4px;
}

.tag-list__item {
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 10px;
  padding: 10px 10px;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}

.tag-list__item:hover {
  border-color: rgba(99, 102, 241, 0.35);
  background: rgba(99, 102, 241, 0.05);
}

.tag-list__item--active {
  border-color: rgba(99, 102, 241, 0.5);
  background: rgba(99, 102, 241, 0.08);
}

.tag-list__label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.tag-list__desc {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}
</style>

