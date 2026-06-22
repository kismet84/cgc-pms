<script setup lang="ts">
import type { StockKpiVO } from '@/types/inventory'

defineProps<{
  lowStockWarn: { name: string; qty: number }[]
  kpi: StockKpiVO
  inOutStats: { inPct: number; outPct: number }
}>()
</script>

<template>
  <aside class="lg-analysis-rail">
    <section class="lg-panel">
      <div class="lg-panel-title">低库存预警</div>
      <div class="lg-type-list">
        <div v-for="w in lowStockWarn" :key="w.name" class="lg-type-row">
          <span
            class="lg-type-dot"
            :style="{ background: w.qty < 5 ? '#ef4444' : 'var(--kpi-overdue)' }"
          ></span>
          <span class="lg-type-label">{{ w.name }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{
                width: Math.min(100, (w.qty / 10) * 100) + '%',
                background: w.qty < 5 ? '#ef4444' : 'var(--kpi-overdue)',
              }"
            ></span>
          </span>
          <span class="lg-type-num" style="color: #ef4444">{{ w.qty }}</span>
          <span class="lg-type-pct"></span>
        </div>
        <div v-if="lowStockWarn.length === 0" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: 'var(--kpi-paid)' }"></span>
          <span class="lg-type-label" style="grid-column: 2 / span 4">库存正常</span>
        </div>
      </div>
    </section>
    <section class="lg-panel">
      <div class="lg-panel-title">出入库统计</div>
      <div class="lg-type-list">
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
  </aside>
</template>
