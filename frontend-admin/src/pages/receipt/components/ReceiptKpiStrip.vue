<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileDoneOutlined,
  WarningOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'

const props = defineProps<{
  totalCount: number
  totalAmount: string
  qualifiedCount: number
  unqualifiedCount: number
  fmtAmount: (val: string) => string
}>()

const qualifiedPct = computed(() => {
  const total = Number(props.totalCount) || 0
  if (!total) return 0
  return Math.round((Number(props.qualifiedCount) / total) * 100)
})

const unqualifiedPct = computed(() => {
  const total = Number(props.totalCount) || 0
  if (!total) return 0
  return Math.round((Number(props.unqualifiedCount) / total) * 100)
})

const pendingCount = computed(() =>
  Math.max(
    (Number(props.totalCount) || 0) -
      (Number(props.qualifiedCount) || 0) -
      (Number(props.unqualifiedCount) || 0),
    0,
  ),
)

const pendingPct = computed(() => {
  const total = Number(props.totalCount) || 0
  if (!total) return 0
  return Math.round((pendingCount.value / total) * 100)
})
</script>

<template>
  <div class="receipt-kpi-summary" aria-label="材料验收关键指标">
    <div class="receipt-kpi-item">
      <span class="receipt-kpi-icon is-blue"><FileDoneOutlined /></span>
      <div>
        <span class="receipt-kpi-label">验收总数</span>
        <strong>{{ totalCount }}</strong>
        <span class="receipt-kpi-hint">全部验收单</span>
      </div>
    </div>
    <div class="receipt-kpi-item">
      <span class="receipt-kpi-icon is-cyan"><WalletOutlined /></span>
      <div>
        <span class="receipt-kpi-label">验收金额</span>
        <strong>{{ fmtAmount(totalAmount) }}</strong>
        <span class="receipt-kpi-hint">万元</span>
      </div>
    </div>
    <div class="receipt-kpi-item">
      <span class="receipt-kpi-icon is-green"><CheckCircleOutlined /></span>
      <div>
        <span class="receipt-kpi-label">合格批次</span>
        <strong>{{ qualifiedCount }}</strong>
        <span class="receipt-kpi-hint">{{ qualifiedPct }}%</span>
      </div>
    </div>
    <div class="receipt-kpi-item">
      <span class="receipt-kpi-icon is-red"><WarningOutlined /></span>
      <div>
        <span class="receipt-kpi-label">不合格批次</span>
        <strong>{{ unqualifiedCount }}</strong>
        <span class="receipt-kpi-hint">{{ unqualifiedPct }}%</span>
      </div>
    </div>
    <div class="receipt-kpi-item">
      <span class="receipt-kpi-icon is-amber"><ClockCircleOutlined /></span>
      <div>
        <span class="receipt-kpi-label">待处理</span>
        <strong>{{ pendingCount }}</strong>
        <span class="receipt-kpi-hint">{{ pendingPct }}%</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.receipt-kpi-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  height: 88px;
  min-height: 88px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.receipt-kpi-item {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 12px 18px;
  border-right: 1px solid #edf1f5;
}

.receipt-kpi-item:last-child {
  border-right: 0;
}

.receipt-kpi-icon {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  font-size: 18px;
}

.receipt-kpi-icon.is-blue {
  color: #2563eb;
  background: #eff6ff;
}

.receipt-kpi-icon.is-cyan {
  color: #0891b2;
  background: #ecfeff;
}

.receipt-kpi-icon.is-green {
  color: #16a34a;
  background: #f0fdf4;
}

.receipt-kpi-icon.is-red {
  color: #dc2626;
  background: #fef2f2;
}

.receipt-kpi-icon.is-amber {
  color: #d97706;
  background: #fffbeb;
}

.receipt-kpi-label,
.receipt-kpi-hint {
  display: block;
  font-size: 12px;
  color: #64748b;
  line-height: 18px;
}

.receipt-kpi-item strong {
  display: block;
  margin: 1px 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 24px;
  font-weight: 700;
}

@media (max-width: 1280px) {
  .receipt-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
