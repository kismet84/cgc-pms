<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  totalCount: number
  totalAmount: string
  qualifiedCount: number
  unqualifiedCount: number
  fmtAmount: (val: string) => string
}>()

const qualifiedPct = computed(() => {
  if (!props.totalCount) return 0
  return Math.round((props.qualifiedCount / props.totalCount) * 100)
})

const unqualifiedPct = computed(() => {
  if (!props.totalCount) return 0
  return Math.round((props.unqualifiedCount / props.totalCount) * 100)
})
</script>

<template>
  <div class="lg-kpi-strip">
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">验收总数</span>
      <span class="lg-kpi-card-value">{{ totalCount }} <small>单</small></span>
      <span class="lg-kpi-card-bar"
        ><span style="width: 100%; background: var(--kpi-total)"></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">验收总金额(含税)</span>
      <span class="lg-kpi-card-value">{{ fmtAmount(totalAmount) }} <small>万元</small></span>
      <span class="lg-kpi-card-bar"
        ><span style="width: 100%; background: var(--kpi-amount)"></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">合格批次</span>
      <span class="lg-kpi-card-value">{{ qualifiedCount }} <small>单</small></span>
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: qualifiedPct + '%',
            background: 'var(--kpi-paid)',
          }"
        ></span
      ></span>
      <span class="lg-kpi-card-hint" v-if="totalCount">{{ qualifiedPct }}%</span>
    </div>
    <div class="lg-kpi-card is-warn" v-if="unqualifiedCount > 0">
      <span class="lg-kpi-card-label">不合格批次</span>
      <span class="lg-kpi-card-value">{{ unqualifiedCount }} <small>单</small></span>
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: unqualifiedPct + '%',
            background: 'var(--kpi-overdue)',
          }"
        ></span
      ></span>
      <span class="lg-kpi-card-hint" v-if="totalCount">{{ unqualifiedPct }}%</span>
    </div>
  </div>
</template>

<style scoped></style>
