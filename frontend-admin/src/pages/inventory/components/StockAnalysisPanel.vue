<script setup lang="ts">
import type { StockKpiVO } from '@/types/inventory'

defineProps<{
  lowStockWarn: { name: string; qty: number; threshold: number }[]
  kpi: StockKpiVO
  inOutStats: { inPct: number; outPct: number }
}>()

defineEmits<{ replenish: [] }>()
</script>

<template>
  <aside class="lg-analysis-rail stock-analysis-rail" aria-label="库存辅助分析">
    <div class="lg-analysis-panel lg-fill-card stock-analysis-panel">
      <header class="stock-analysis-head lg-analysis-header">
        <div>
          <div class="stock-analysis-title lg-analysis-heading">辅助分析</div>
          <div class="stock-analysis-subtitle lg-analysis-description">预警、出入库与当前结构</div>
        </div>
      </header>
      <section class="stock-analysis-section">
        <div class="stock-section-title">低库存预警</div>
        <div>
          <div v-for="w in lowStockWarn" :key="w.name" class="lg-type-row">
            <span
              class="lg-type-dot"
              :style="{ background: w.qty < w.threshold / 2 ? '#ef4444' : 'var(--kpi-overdue)' }"
            ></span>
            <span class="lg-type-label">{{ w.name }}</span>
            <span class="lg-type-bar-wrap">
              <span
                class="lg-type-bar"
                :style="{
                  width: Math.min(100, (w.qty / w.threshold) * 100) + '%',
                  background: w.qty < w.threshold / 2 ? '#ef4444' : 'var(--kpi-overdue)',
                }"
              ></span>
            </span>
            <span class="lg-type-num" style="color: #ef4444">{{ w.qty }}</span>
            <span class="lg-type-pct">低库存</span>
          </div>
          <div v-if="lowStockWarn.length === 0" class="lg-type-row">
            <span class="lg-type-dot" :style="{ background: 'var(--kpi-paid)' }"></span>
            <span class="lg-type-label" style="grid-column: 2 / span 4">库存正常</span>
          </div>
        </div>
      </section>
      <section class="stock-analysis-section">
        <div class="stock-section-title">出入库统计</div>
        <div>
          <div class="lg-type-row">
            <span class="lg-type-dot" style="background: #22c55e"></span>
            <span class="lg-type-label">入库次数</span>
            <span class="lg-type-bar-wrap">
              <span
                class="lg-type-bar"
                :style="{
                  width: inOutStats.inPct + '%',
                  background: '#22c55e',
                }"
              ></span>
            </span>
            <span class="lg-type-num" style="color: #22c55e">{{ kpi.txnInCount }}</span>
            <span class="lg-type-pct">{{ inOutStats.inPct }}%</span>
          </div>
          <div class="lg-type-row">
            <span class="lg-type-dot" style="background: #ef4444"></span>
            <span class="lg-type-label">出库次数</span>
            <span class="lg-type-bar-wrap">
              <span
                class="lg-type-bar"
                :style="{
                  width: inOutStats.outPct + '%',
                  background: '#ef4444',
                }"
              ></span>
            </span>
            <span class="lg-type-num" style="color: #ef4444">{{ kpi.txnOutCount }}</span>
            <span class="lg-type-pct">{{ inOutStats.outPct }}%</span>
          </div>
        </div>
      </section>
      <section class="stock-analysis-section">
        <div class="stock-section-title">库存结构</div>
        <div class="lg-type-row">
          <span class="lg-type-dot" style="background: #2563eb"></span>
          <span class="lg-type-label">仓库数量</span>
          <span class="lg-type-bar-wrap">
            <span class="lg-type-bar" style="width: 100%; background: #2563eb"></span>
          </span>
          <span class="lg-type-num">{{ kpi.warehouseCount }}</span>
          <span class="lg-type-pct">个</span>
        </div>
        <div class="lg-type-row">
          <span class="lg-type-dot" style="background: #0891b2"></span>
          <span class="lg-type-label">物料种类</span>
          <span class="lg-type-bar-wrap">
            <span class="lg-type-bar" style="width: 100%; background: #0891b2"></span>
          </span>
          <span class="lg-type-num">{{ kpi.materialTypeCount }}</span>
          <span class="lg-type-pct">种</span>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.stock-analysis-rail {
}

.stock-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  box-sizing: border-box;
  padding: 18px;
}

.stock-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.stock-analysis-subtitle {
  color: var(--text-secondary);
  font-size: 12px;
}

.stock-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.stock-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.stock-analysis-section :deep(.lg-type-row),
.stock-analysis-section .lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}
</style>
