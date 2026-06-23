<script setup lang="ts">
import { computed } from 'vue'

/**
 * LgKpiCard — KPI 指标卡片
 *
 * Props:
 * - label: 指标名称
 * - value: 指标数值
 * - unit: 单位（可选，显示在数值右侧 small 标签中）
 * - warn: 是否预警态（可选，启用后卡片应用 is-warn 样式）
 * - color: 进度条颜色（可选，默认 #1677FF）
 * - barWidth: 进度条宽度 0-100（可选，不传则不显示进度条）
 * - hint: 底部提示文字（可选，如百分比说明）
 */
const props = withDefaults(
  defineProps<{
    label: string
    value: string | number
    unit?: string
    warn?: boolean
    color?: string
    barWidth?: number
    hint?: string
  }>(),
  {
    unit: undefined,
    warn: false,
    color: 'var(--primary)',
    barWidth: undefined,
    hint: undefined,
  },
)

const hasBar = computed(() => props.barWidth !== undefined && props.barWidth >= 0)
</script>

<template>
  <div class="lg-kpi-card" :class="{ 'is-warn': warn }">
    <span class="lg-kpi-card-label">{{ label }}</span>
    <span class="lg-kpi-card-value"
      >{{ value }} <small v-if="unit">{{ unit }}</small></span
    >
    <span v-if="hasBar" class="lg-kpi-card-bar">
      <span :style="{ width: barWidth + '%', background: color }"></span>
    </span>
    <span v-if="hint" class="lg-kpi-card-hint">{{ hint }}</span>
  </div>
</template>
