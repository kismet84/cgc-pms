<script setup lang="ts">
import { InboxOutlined, FallOutlined, RiseOutlined, AlertOutlined } from '@ant-design/icons-vue'
import type { StockKpiVO } from '@/types/inventory'

defineProps<{
  kpi: StockKpiVO
  kpiMax: { txnInCount: number; txnOutCount: number }
  kpiPct: (value: number, max: number) => number
  isMobile: boolean
}>()
</script>

<template>
  <!-- KPI 横条：桌面 -->
  <div v-if="!isMobile" class="stock-kpi-summary" aria-label="库存关键指标">
    <div class="stock-kpi-item">
      <span class="stock-kpi-icon is-blue"><InboxOutlined /></span>
      <span class="stock-kpi-label">仓库数量</span>
      <strong>{{ kpi.warehouseCount }} <small>个</small></strong>
    </div>
    <div class="stock-kpi-item">
      <span class="stock-kpi-icon is-cyan"><InboxOutlined /></span>
      <span class="stock-kpi-label">物料种类</span>
      <strong>{{ kpi.materialTypeCount }} <small>种</small></strong>
    </div>
    <div class="stock-kpi-item">
      <span class="stock-kpi-icon is-red"><AlertOutlined /></span>
      <span class="stock-kpi-label">低库存物料</span>
      <strong>{{ kpi.lowStockCount }} <small>种</small></strong>
    </div>
    <div class="stock-kpi-item is-progress">
      <span class="stock-kpi-icon is-green"><RiseOutlined /></span>
      <span class="stock-kpi-label">入库记录</span>
      <strong>{{ kpi.txnInCount }} <small>条</small></strong>
      <span class="stock-kpi-progress">
        <span :style="{ width: kpiPct(kpi.txnInCount, kpiMax.txnInCount) + '%' }"></span>
      </span>
    </div>
    <div class="stock-kpi-item is-progress is-out">
      <span class="stock-kpi-icon is-purple"><FallOutlined /></span>
      <span class="stock-kpi-label">出库记录</span>
      <strong>{{ kpi.txnOutCount }} <small>条</small></strong>
      <span class="stock-kpi-progress">
        <span :style="{ width: kpiPct(kpi.txnOutCount, kpiMax.txnOutCount) + '%' }"></span>
      </span>
    </div>
  </div>

  <!-- KPI 移动端：单卡片 -->
  <div v-else class="lg-kpi-single">
    <div
      class="lg-kpi-single-row"
      v-for="item in [
        {
          icon: InboxOutlined,
          bg: 'var(--kpi-total)',
          label: '仓库数量',
          value: kpi.warehouseCount,
          unit: '个',
        },
        {
          icon: InboxOutlined,
          bg: 'var(--kpi-amount)',
          label: '物料种类',
          value: kpi.materialTypeCount,
          unit: '种',
        },
        {
          icon: AlertOutlined,
          bg: 'var(--kpi-overdue)',
          label: '低库存物料',
          value: kpi.lowStockCount,
          unit: '种',
        },
        {
          icon: RiseOutlined,
          bg: 'var(--kpi-paid)',
          label: '入库记录',
          value: kpi.txnInCount,
          unit: '条',
        },
        {
          icon: FallOutlined,
          bg: 'var(--kpi-unpaid)',
          label: '出库记录',
          value: kpi.txnOutCount,
          unit: '条',
        },
      ]"
      :key="item.label"
    >
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
.stock-kpi-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  overflow: hidden;
  height: 88px;
  min-height: 88px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.stock-kpi-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 20px 30px 8px;
  column-gap: 10px;
  align-content: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.stock-kpi-item:last-child {
  border-right: 0;
}

.stock-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.stock-kpi-icon.is-blue {
  color: var(--primary);
  background: var(--primary-soft);
}
.stock-kpi-icon.is-cyan {
  color: #0891b2;
  background: #ecfeff;
}
.stock-kpi-icon.is-red {
  color: var(--error);
  background: var(--error-soft);
}
.stock-kpi-icon.is-green {
  color: var(--success);
  background: var(--success-soft);
}
.stock-kpi-icon.is-purple {
  color: #7c3aed;
  background: #f3e8ff;
}

.stock-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-kpi-item strong {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-kpi-item small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.stock-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.stock-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.stock-kpi-item.is-out .stock-kpi-progress > span {
  background: var(--kpi-unpaid);
}
</style>
