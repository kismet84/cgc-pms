<script setup lang="ts">
import { computed } from 'vue'
import V2Button from './V2Button.vue'

const props = withDefaults(
  defineProps<{
    total: number | string
    pageNo: number
    pageSize?: number
    label: string
    unit?: string
    disabled?: boolean
  }>(),
  { pageSize: 10, unit: '条', disabled: false },
)

const emit = defineEmits<{ 'update:pageNo': [value: number] }>()
const normalizedTotal = computed(() => Math.max(0, Number(props.total) || 0))
const pageCount = computed(() => Math.max(1, Math.ceil(normalizedTotal.value / props.pageSize)))
</script>

<template>
  <nav class="v2-pagination" :aria-label="label">
    <span>共 {{ normalizedTotal }} {{ unit }}</span>
    <V2Button
      size="small"
      variant="secondary"
      :disabled="disabled || pageNo <= 1"
      @click="emit('update:pageNo', pageNo - 1)"
    >
      上一页
    </V2Button>
    <span>第 {{ pageNo }} 页</span>
    <V2Button
      size="small"
      variant="secondary"
      :disabled="disabled || pageNo >= pageCount"
      @click="emit('update:pageNo', pageNo + 1)"
    >
      下一页
    </V2Button>
  </nav>
</template>
