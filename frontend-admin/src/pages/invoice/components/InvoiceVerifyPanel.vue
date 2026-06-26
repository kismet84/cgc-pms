<script setup lang="ts">
import { computed } from 'vue'
import type { InvoiceVO } from '@/types/invoice'
import { INVOICE_TYPE_LABEL, VERIFY_STATUS_LABEL } from '@/types/invoice'

const props = defineProps<{
  data: InvoiceVO[]
}>()

const verifyBreakdown = computed(() => {
  const m: Record<string, number> = {}
  props.data.forEach((r) => {
    const label = VERIFY_STATUS_LABEL[r.verifyStatus] ?? r.verifyStatus
    m[label] = (m[label] || 0) + 1
  })
  const total = Object.values(m).reduce((s, v) => s + v, 0) || 1
  return Object.entries(m).map(([label, count]) => ({
    label,
    count,
    pct: Math.round((count / total) * 100),
    color: dotColor(label),
  }))
})

const typeBreakdown = computed(() => {
  const m: Record<string, number> = {}
  props.data.forEach((r) => {
    const label = INVOICE_TYPE_LABEL[r.invoiceType] ?? r.invoiceType
    m[label] = (m[label] || 0) + 1
  })
  const total = Object.values(m).reduce((s, v) => s + v, 0) || 1
  return Object.entries(m).map(([label, count], index) => ({
    label,
    count,
    pct: Math.round((count / total) * 100),
    color: ['#2563eb', '#31c48d', '#f59e0b', '#8b5cf6'][index % 4],
  }))
})

const abnormalRows = computed(() =>
  props.data
    .filter((r) => r.verifyStatus === 'ABNORMAL')
    .map((row) => ({
      id: row.id,
      project: row.sellerName || '异常发票',
      title: row.invoiceNo || '-',
    }))
    .slice(0, 4),
)

function dotColor(label: string): string {
  if (label === '已认证') return '#31c48d'
  if (label === '异常') return '#ef4444'
  if (label === '待核验') return '#f59e0b'
  return '#8b5cf6'
}
</script>

<template>
  <aside class="lg-analysis-rail invoice-analysis-rail" aria-label="发票辅助分析">
    <div class="invoice-analysis-panel">
      <header class="invoice-analysis-head">
        <div>
          <div class="invoice-analysis-title">发票分析</div>
          <div class="invoice-analysis-subtitle">核验状态、类型分布与异常提醒</div>
        </div>
      </header>

      <section class="invoice-analysis-section">
        <div class="invoice-section-title">核验状态分布</div>
        <div v-for="it in verifyBreakdown" :key="it.label" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: it.color }"></span>
          <span class="lg-type-label">{{ it.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{
                width: it.pct + '%',
                background: it.color,
              }"
            ></span>
          </span>
          <span class="lg-type-num">{{ it.count }}</span>
          <span class="lg-type-pct">{{ it.pct }}%</span>
        </div>
        <div v-if="!verifyBreakdown.length" class="invoice-analysis-empty">暂无核验状态数据</div>
      </section>

      <section class="invoice-analysis-section">
        <div class="invoice-section-title">发票类型分布</div>
        <div v-for="it in typeBreakdown" :key="it.label" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: it.color }"></span>
          <span class="lg-type-label">{{ it.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{
                width: it.pct + '%',
                background: it.color,
              }"
            ></span>
          </span>
          <span class="lg-type-num">{{ it.count }}</span>
          <span class="lg-type-pct">{{ it.pct }}%</span>
        </div>
        <div v-if="!typeBreakdown.length" class="invoice-analysis-empty">暂无发票类型数据</div>
      </section>

      <section class="invoice-analysis-section">
        <div class="invoice-warning-head">
          <div class="invoice-section-title">异常提醒</div>
          <span class="invoice-warning-count">{{ abnormalRows.length }} 项</span>
        </div>
        <div v-for="item in abnormalRows" :key="item.id" class="lg-warning-item">
          <span class="lg-warning-project">{{ item.project }}</span>
          <span class="lg-warning-title">{{ item.title }}</span>
          <span class="invoice-warning-tag">需处理</span>
        </div>
        <div v-if="!abnormalRows.length" class="lg-warning-empty">无异常发票</div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.invoice-analysis-rail {
  width: 336px;
}

.invoice-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.invoice-analysis-head,
.invoice-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.invoice-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.invoice-analysis-subtitle,
.invoice-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.invoice-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.invoice-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.invoice-analysis-empty {
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: center;
}

.invoice-analysis-section :deep(.lg-type-row),
.lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

.invoice-warning-tag {
  color: var(--error);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .invoice-analysis-rail {
    width: 100%;
  }
}
</style>
