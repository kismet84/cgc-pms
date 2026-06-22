<script setup lang="ts">
import { computed } from 'vue'
import type { InvoiceVO } from '@/types/invoice'

const props = defineProps<{
  data: InvoiceVO[]
}>()

const kpiInvoiceTotal = computed(() =>
  props.data.reduce((s, r) => s + (parseFloat(r.invoiceAmount) || 0), 0),
)
const kpiInvoiced = computed(() =>
  props.data
    .filter((r) => r.verifyStatus === 'VERIFIED')
    .reduce((s, r) => s + (parseFloat(r.invoiceAmount) || 0), 0),
)
const kpiUninvoiced = computed(() =>
  props.data
    .filter((r) => r.verifyStatus !== 'VERIFIED')
    .reduce((s, r) => s + (parseFloat(r.invoiceAmount) || 0), 0),
)
const kpiAbnormal = computed(
  () => props.data.filter((r) => r.verifyStatus === 'FAILED').length,
)

const kpiMax = computed(() => ({
  total: Math.max(kpiInvoiceTotal.value, 1),
}))
function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}
</script>

<template>
  <div class="lg-kpi-strip">
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">发票总额</span>
      <span class="lg-kpi-card-value"
        >{{ kpiInvoiceTotal.toLocaleString() }} <small>元</small></span
      >
      <span class="lg-kpi-card-bar"
        ><span style="width: 100%; background: var(--kpi-amount)"></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">已核验</span>
      <span class="lg-kpi-card-value"
        >{{ kpiInvoiced.toLocaleString() }} <small>元</small></span
      >
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: kpiPct(kpiInvoiced, kpiMax.total) + '%',
            background: 'var(--kpi-paid)',
          }"
        ></span
      ></span>
    </div>
    <div class="lg-kpi-card">
      <span class="lg-kpi-card-label">待核验</span>
      <span class="lg-kpi-card-value"
        >{{ kpiUninvoiced.toLocaleString() }} <small>元</small></span
      >
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: kpiPct(kpiUninvoiced, kpiMax.total) + '%',
            background: 'var(--kpi-unpaid)',
          }"
        ></span
      ></span>
    </div>
    <div class="lg-kpi-card is-warn">
      <span class="lg-kpi-card-label">异常发票</span>
      <span class="lg-kpi-card-value" style="color: #ef4444"
        >{{ kpiAbnormal }} <small>张</small></span
      >
      <span class="lg-kpi-card-bar"
        ><span
          :style="{
            width: kpiPct(kpiAbnormal, kpiMax.total) + '%',
            background: 'var(--kpi-overdue)',
          }"
        ></span
      ></span>
    </div>
  </div>
</template>

<style scoped></style>
