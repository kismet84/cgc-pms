<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckCircleOutlined,
  FileTextOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
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
const kpiAbnormal = computed(() => props.data.filter((r) => r.verifyStatus === 'ABNORMAL').length)

const kpiMax = computed(() => ({
  total: Math.max(kpiInvoiceTotal.value, 1),
}))
function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}
</script>

<template>
  <div class="lg-kpi-strip invoice-kpi-summary" aria-label="发票关键指标">
    <div class="invoice-kpi-item">
      <span class="invoice-kpi-icon is-total"><FileTextOutlined /></span>
      <span class="invoice-kpi-label">发票总数</span>
      <span class="invoice-kpi-value">{{ data.length }} <small>张</small></span>
    </div>
    <div class="invoice-kpi-item is-wide">
      <span class="invoice-kpi-icon is-amount"><WalletOutlined /></span>
      <span class="invoice-kpi-label">发票总额</span>
      <span class="invoice-kpi-value"
        >{{ (kpiInvoiceTotal / 10000).toFixed(2) }} <small>万元</small></span
      >
    </div>
    <div class="invoice-kpi-item is-progress">
      <span class="invoice-kpi-icon is-paid"><CheckCircleOutlined /></span>
      <span class="invoice-kpi-label">已认证金额</span>
      <span class="invoice-kpi-value"
        >{{ (kpiInvoiced / 10000).toFixed(2) }} <small>万元</small></span
      >
      <span class="invoice-kpi-progress">
        <span :style="{ width: kpiPct(kpiInvoiced, kpiMax.total) + '%' }"></span>
      </span>
    </div>
    <div class="invoice-kpi-item is-progress is-unpaid">
      <span class="invoice-kpi-icon is-unpaid"><SafetyCertificateOutlined /></span>
      <span class="invoice-kpi-label">待核验金额</span>
      <span class="invoice-kpi-value"
        >{{ (kpiUninvoiced / 10000).toFixed(2) }} <small>万元</small></span
      >
      <span class="invoice-kpi-progress">
        <span :style="{ width: kpiPct(kpiUninvoiced, kpiMax.total) + '%' }"></span>
      </span>
    </div>
    <div class="invoice-kpi-item">
      <span class="invoice-kpi-icon is-abnormal"><WarningOutlined /></span>
      <span class="invoice-kpi-label">异常发票</span>
      <span class="invoice-kpi-value">{{ kpiAbnormal }} <small>张</small></span>
    </div>
  </div>
</template>

<style scoped>
.invoice-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  overflow: hidden;
  min-height: 108px;
  margin-bottom: 0;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.invoice-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px 8px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  min-height: 108px;
  padding: 16px 18px;
  border: 0;
  border-right: 1px solid var(--border-subtle);
  border-radius: 0;
  box-shadow: none;
}

.invoice-kpi-item:last-child {
  border-right: 0;
}

.invoice-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.invoice-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.invoice-kpi-icon.is-paid {
  color: var(--success);
  background: var(--success-soft);
}

.invoice-kpi-icon.is-unpaid {
  color: var(--primary);
  background: var(--surface-tint);
}

.invoice-kpi-icon.is-abnormal {
  color: var(--error);
  background: var(--error-soft);
}

.invoice-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.invoice-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.invoice-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.invoice-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.invoice-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.invoice-kpi-item.is-unpaid .invoice-kpi-progress > span {
  background: var(--kpi-unpaid);
}

@media (max-width: 1200px) {
  .invoice-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .invoice-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }
}
</style>
