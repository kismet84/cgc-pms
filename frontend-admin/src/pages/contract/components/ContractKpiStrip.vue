<script setup lang="ts">
import { computed } from 'vue'
import {
  FileTextOutlined,
  DollarOutlined,
  PayCircleOutlined,
  WalletOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons-vue'
import type { ContractKpiVO } from '@/types/contract'

const props = defineProps<{
  kpi: ContractKpiVO
  isMobile: boolean
  fmtAmount: (val: string) => string
  kpiMax: { totalCount: number; totalAmount: number; overdueCount: number }
  kpiPct: (value: number, max: number) => number
}>()

const mobileItems = computed(() => [
  {
    icon: FileTextOutlined,
    bg: 'var(--kpi-total)',
    label: '合同总数',
    value: props.kpi.totalCount,
    unit: '份',
  },
  {
    icon: DollarOutlined,
    bg: 'var(--kpi-amount)',
    label: '合同总金额(含税)',
    value: props.fmtAmount(props.kpi.totalAmount),
    unit: '万元',
  },
  {
    icon: PayCircleOutlined,
    bg: 'var(--kpi-paid)',
    label: '已付款金额',
    value: props.fmtAmount(props.kpi.paidAmount),
    unit: '万元',
  },
  {
    icon: WalletOutlined,
    bg: 'var(--kpi-unpaid)',
    label: '未付款金额',
    value: props.fmtAmount(props.kpi.unpaidAmount),
    unit: '万元',
  },
  {
    icon: ClockCircleOutlined,
    bg: 'var(--kpi-overdue)',
    label: '逾期合同数',
    value: props.kpi.overdueCount,
    unit: '份',
  },
])
</script>

<template>
  <!-- KPI 桌面/平板 -->
  <!-- class="cl-kpi-summary" -->
  <div v-if="!isMobile" class="lg-kpi-strip cl-kpi-summary" aria-label="合同关键指标">
    <div class="cl-kpi-item">
      <span class="cl-kpi-icon is-total"><FileTextOutlined /></span>
      <span class="cl-kpi-label">合同总数</span>
      <span class="cl-kpi-value">{{ kpi.totalCount }} <small>份</small></span>
    </div>
    <div class="cl-kpi-item is-wide">
      <span class="cl-kpi-icon is-amount"><DollarOutlined /></span>
      <span class="cl-kpi-label">合同总金额(含税)</span>
      <span class="cl-kpi-value">{{ fmtAmount(kpi.totalAmount) }} <small>万元</small></span>
    </div>
    <div class="cl-kpi-item is-progress">
      <span class="cl-kpi-icon is-paid"><PayCircleOutlined /></span>
      <span class="cl-kpi-label">已付款</span>
      <span class="cl-kpi-value">{{ fmtAmount(kpi.paidAmount) }} <small>万元</small></span>
      <span class="cl-kpi-progress">
        <span
          :style="{ width: kpiPct(parseFloat(kpi.paidAmount), kpiMax.totalAmount) + '%' }"
        ></span>
      </span>
    </div>
    <div class="cl-kpi-item is-progress is-unpaid">
      <span class="cl-kpi-icon is-unpaid"><WalletOutlined /></span>
      <span class="cl-kpi-label">未付款</span>
      <span class="cl-kpi-value">{{ fmtAmount(kpi.unpaidAmount) }} <small>万元</small></span>
      <span class="cl-kpi-progress">
        <span
          :style="{ width: kpiPct(parseFloat(kpi.unpaidAmount), kpiMax.totalAmount) + '%' }"
        ></span>
      </span>
    </div>
    <div class="cl-kpi-item is-overdue">
      <span class="cl-kpi-icon is-overdue"><ClockCircleOutlined /></span>
      <span class="cl-kpi-label">逾期合同</span>
      <span class="cl-kpi-value">{{ kpi.overdueCount }} <small>份</small></span>
    </div>
  </div>

  <!-- KPI 移动端：单条卡片 -->
  <div v-else class="lg-kpi-single">
    <div class="lg-kpi-single-row" v-for="item in mobileItems" :key="item.label">
      <div class="lg-kpi-single-icon" :style="{ background: item.bg }">
        <component :is="item.icon" />
      </div>
      <span class="lg-kpi-single-label">{{ item.label }}</span>
      <span class="lg-kpi-single-value"
        >{{ item.value }} <small>{{ item.unit }}</small></span
      >
    </div>
  </div>
</template>

<style scoped>
.cl-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cl-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px 8px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.cl-kpi-item:last-child {
  border-right: 0;
}

.cl-kpi-icon {
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

.cl-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.cl-kpi-icon.is-paid {
  color: var(--success);
  background: var(--success-soft);
}

.cl-kpi-icon.is-unpaid {
  color: var(--primary);
  background: var(--surface-tint);
}

.cl-kpi-icon.is-overdue {
  color: var(--error);
  background: var(--error-soft);
}

.cl-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cl-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cl-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.cl-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.cl-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.cl-kpi-item.is-unpaid .cl-kpi-progress > span {
  background: var(--kpi-unpaid);
}

@media (max-width: 1200px) {
  .cl-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cl-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }
}

.contract-kpi-bar-fill {
  width: 100%;
}

.contract-kpi-bar-fill.is-total {
  background: var(--kpi-total);
}

.contract-kpi-bar-fill.is-amount {
  background: var(--kpi-amount);
}
</style>
