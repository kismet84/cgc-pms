<script setup lang="ts">
import { computed } from 'vue'

/**
 * 通用状态标签组件
 *
 * Props:
 * - status: 状态值/文本（必填）
 * - type:   显式指定颜色类型 'success' | 'warning' | 'error' | 'info'（可选）
 * - label:  自定义显示文本，不传则取 status 本身（可选）
 *
 * 颜色自动映射规则（当 type 未指定时按 status 推断）：
 *   active / success → green
 *   pending          → orange
 *   rejected / error → red
 *   其他              → default
 */

interface Props {
  status: string
  type?: 'success' | 'warning' | 'error' | 'info'
  label?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: undefined,
  label: undefined,
})

/** type props → Ant Design Tag 颜色 */
const typeColor: Record<string, string> = {
  success: 'green',
  warning: 'orange',
  error: 'red',
  info: 'blue',
}

/** status 推断 → Ant Design Tag 颜色 */
const statusColor: Record<string, string> = {
  active: 'green',
  success: 'green',
  pending: 'orange',
  rejected: 'red',
  error: 'red',
}

const color = computed<string>(() => {
  if (props.type) return typeColor[props.type] || 'default'
  const lower = props.status.toLowerCase()
  return statusColor[lower] || 'default'
})

const displayLabel = computed<string>(() => {
  if (props.label !== undefined) return props.label
  return props.status
})
</script>

<template>
  <a-tag :color="color">{{ displayLabel }}</a-tag>
</template>
