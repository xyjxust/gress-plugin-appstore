<template>
  <div class="appstore-test-shared-field">
    <n-select
      v-if="options && options.length > 0"
      :disabled="disabled"
      :value="modelValue"
      :options="options"
      filterable
      clearable
      :placeholder="placeholder"
      @update:value="onUpdate"
    />

    <n-input
      v-else
      :disabled="disabled"
      :value="displayValue"
      :placeholder="placeholder"
      @update:value="onUpdate"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NInput, NSelect } from 'naive-ui'

type OptionItem = {
  label: string
  value: any
}

const props = defineProps<{
  /**
   * UI-layer value. Must work with `v-model:modelValue`.
   */
  modelValue: any
  disabled?: boolean
  placeholder?: string
  options?: OptionItem[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
}>()

const placeholder = computed(() => props.placeholder ?? '请选择/输入')
const disabled = computed(() => props.disabled ?? false)

const displayValue = computed(() => {
  if (props.modelValue === null || props.modelValue === undefined) return ''
  return String(props.modelValue)
})

function onUpdate(val: unknown) {
  emit('update:modelValue', val)
}
</script>

<style scoped>
.appstore-test-shared-field {
  width: 100%;
}
</style>

