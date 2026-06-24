<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  totalCount: number
  totalAmount: string
  fmtAmount: (val: string) => string
}>()

const stockedPct = computed(() => {
  return 100
})
</script>

<template>
  <div class="lg-kpi-strip">
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">领料总数</span>
      <span class="lg-kpi-card-value">{{ totalCount }} <small>单</small></span>
      <span class="lg-kpi-card-bar"
        ><span style="width: 100%; background: var(--kpi-total)"></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">领料总金额(含税)</span>
      <span class="lg-kpi-card-value">{{ fmtAmount(totalAmount) }} <small>万元</small></span>
      <span class="lg-kpi-card-bar"
        ><span style="width: 100%; background: var(--kpi-amount)"></span
      ></span>
    </div>
  </div>
</template>

<style scoped></style>
