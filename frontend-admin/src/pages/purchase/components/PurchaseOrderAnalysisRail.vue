<script setup lang="ts">
defineProps<{
  focusAmount: string
  orderStatusBreakdown: Array<{
    key: string
    label: string
    count: number
    pct: number
    color: string
  }>
  orderTypeBreakdown: Array<{
    key: string
    label: string
    count: number
    pct: number
    color: string
  }>
  pendingOrders: Array<{ id: string; project: string; title: string; amount: string }>
  onRefresh: () => void
}>()
</script>

<template>
  <aside class="lg-analysis-rail" aria-label="采购订单辅助分析">
    <div class="lg-analysis-panel lg-fill-card purchase-order-analysis-panel">
      <header class="purchase-order-analysis-head">
        <div>
          <div class="purchase-order-analysis-title">辅助分析</div>
          <div class="purchase-order-analysis-subtitle">状态、类型与待履约金额</div>
        </div>
        <a-button type="link" size="small" @click="onRefresh">刷新</a-button>
      </header>

      <section class="purchase-order-analysis-focus">
        <span>本页重点</span>
        <strong>{{ focusAmount }} 万</strong>
        <em>尚未完成到货或验收的采购金额，优先跟踪供应商履约。</em>
      </section>

      <section class="purchase-order-analysis-section">
        <div class="purchase-order-section-title">订单状态分布</div>
        <div v-for="it in orderStatusBreakdown" :key="it.key" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: it.color }"></span>
          <span class="lg-type-label">{{ it.label }}</span>
          <span class="lg-type-bar-wrap">
            <span class="lg-type-bar" :style="{ width: it.pct + '%', background: it.color }"></span>
          </span>
          <span class="lg-type-num">{{ it.count }}</span>
          <span class="lg-type-pct">{{ it.pct }}%</span>
        </div>
        <div v-if="!orderStatusBreakdown.length" class="purchase-order-analysis-empty">
          暂无订单状态数据
        </div>
      </section>

      <section class="purchase-order-analysis-section">
        <div class="purchase-order-section-title">订单类型分布</div>
        <div v-for="it in orderTypeBreakdown" :key="it.key" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: it.color }"></span>
          <span class="lg-type-label">{{ it.label }}</span>
          <span class="lg-type-bar-wrap">
            <span class="lg-type-bar" :style="{ width: it.pct + '%', background: it.color }"></span>
          </span>
          <span class="lg-type-num">{{ it.count }}</span>
          <span class="lg-type-pct">{{ it.pct }}%</span>
        </div>
      </section>

      <section class="purchase-order-analysis-section">
        <div class="purchase-order-warning-head">
          <div class="purchase-order-section-title">待履约订单</div>
          <span class="purchase-order-warning-count">{{ pendingOrders.length }} 项</span>
        </div>
        <div v-for="item in pendingOrders" :key="item.id" class="lg-warning-item">
          <span class="lg-warning-project">{{ item.project }}</span>
          <span class="lg-warning-title">{{ item.title }}</span>
          <span class="purchase-order-warning-amount">{{ item.amount }}万</span>
        </div>
        <div v-if="!pendingOrders.length" class="lg-warning-empty">暂无待履约订单</div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.purchase-order-analysis-rail {
  display: flex;
  min-height: 0;
}

.purchase-order-analysis-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  overflow: auto;
  padding: 16px;
}

.purchase-order-analysis-focus {
  display: grid;
  gap: 4px;
  padding: 14px;
  background: var(--error-soft);
  border: 1px solid rgba(239, 68, 68, 0.18);
  border-radius: var(--radius-md);
}

.purchase-order-analysis-focus span,
.purchase-order-analysis-focus em {
  color: var(--text-secondary);
  font-size: 12px;
  font-style: normal;
}

.purchase-order-analysis-focus strong {
  color: var(--error);
  font-size: 24px;
  font-weight: 800;
  line-height: 30px;
}

.purchase-order-analysis-head,
.purchase-order-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.purchase-order-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.purchase-order-analysis-subtitle,
.purchase-order-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.purchase-order-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.purchase-order-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.purchase-order-analysis-empty {
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: center;
}

.purchase-order-analysis-section :deep(.lg-type-row),
.lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

.purchase-order-warning-amount {
  color: var(--error);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .lg-analysis-rail {
    width: 100%;
  }
}
</style>
