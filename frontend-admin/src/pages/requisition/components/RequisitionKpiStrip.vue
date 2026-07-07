<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileDoneOutlined,
  InboxOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'

const props = defineProps<{
  totalCount: number
  totalAmount: string
  stockedCount: number
  unstockedCount: number
  pendingCount: number
  fmtAmount: (val: string) => string
}>()

const stockedPct = computed(() => percent(props.stockedCount))
const unstockedPct = computed(() => percent(props.unstockedCount))
const pendingPct = computed(() => percent(props.pendingCount))

function percent(value: number) {
  const total = Number(props.totalCount) || 0
  if (!total) return 0
  return Math.round(((Number(value) || 0) / total) * 100)
}
</script>

<template>
  <div class="lg-kpi-strip requisition-kpi-summary" aria-label="领料申请关键指标">
    <div class="requisition-kpi-item">
      <span class="requisition-kpi-icon is-blue"><FileDoneOutlined /></span>
      <div>
        <span class="requisition-kpi-label">领料总数</span>
        <strong>{{ totalCount }}</strong>
        <span class="requisition-kpi-hint">全部申请</span>
      </div>
    </div>
    <div class="requisition-kpi-item">
      <span class="requisition-kpi-icon is-cyan"><WalletOutlined /></span>
      <div>
        <span class="requisition-kpi-label">领料金额</span>
        <strong>{{ fmtAmount(totalAmount) }}</strong>
        <span class="requisition-kpi-hint">万元</span>
      </div>
    </div>
    <div class="requisition-kpi-item">
      <span class="requisition-kpi-icon is-green"><CheckCircleOutlined /></span>
      <div>
        <span class="requisition-kpi-label">已出库</span>
        <strong>{{ stockedCount }}</strong>
        <span class="requisition-kpi-hint">{{ stockedPct }}%</span>
      </div>
    </div>
    <div class="requisition-kpi-item">
      <span class="requisition-kpi-icon is-amber"><InboxOutlined /></span>
      <div>
        <span class="requisition-kpi-label">未出库</span>
        <strong>{{ unstockedCount }}</strong>
        <span class="requisition-kpi-hint">{{ unstockedPct }}%</span>
      </div>
    </div>
    <div class="requisition-kpi-item">
      <span class="requisition-kpi-icon is-purple"><ClockCircleOutlined /></span>
      <div>
        <span class="requisition-kpi-label">待审批</span>
        <strong>{{ pendingCount }}</strong>
        <span class="requisition-kpi-hint">{{ pendingPct }}%</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.requisition-kpi-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.requisition-kpi-item {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 12px 18px;
  border-right: 1px solid #edf1f5;
}

.requisition-kpi-item:last-child {
  border-right: 0;
}

.requisition-kpi-icon {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  font-size: 18px;
}

.requisition-kpi-icon.is-blue {
  color: #2563eb;
  background: #eff6ff;
}

.requisition-kpi-icon.is-cyan {
  color: #0891b2;
  background: #ecfeff;
}

.requisition-kpi-icon.is-green {
  color: #16a34a;
  background: #f0fdf4;
}

.requisition-kpi-icon.is-amber {
  color: #d97706;
  background: #fffbeb;
}

.requisition-kpi-icon.is-purple {
  color: #7c3aed;
  background: #f5f3ff;
}

.requisition-kpi-label,
.requisition-kpi-hint {
  display: block;
  font-size: 12px;
  color: #64748b;
  line-height: 18px;
}

.requisition-kpi-item strong {
  display: block;
  margin: 1px 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 24px;
  font-weight: 700;
}

@media (max-width: 1280px) {
  .requisition-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
