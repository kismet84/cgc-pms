<script setup lang="ts">
import { computed } from 'vue'

interface RailItem {
  label: string
  amount: string
}

const props = defineProps<{
  costTypeBreakdown: RailItem[]
  sourceBreakdown: RailItem[]
  fmtWan: (val: string | undefined) => string
  barPercent: (amount: string) => string
  handleSearch: () => void
}>()

const warningItems = computed(() =>
  props.sourceBreakdown.filter((item) => parseFloat(item.amount) > 0).slice(0, 5),
)
const warningCount = computed(
  () => props.sourceBreakdown.filter((item) => parseFloat(item.amount) > 0).length,
)
const hasWarningItems = computed(() =>
  props.sourceBreakdown.some((item) => parseFloat(item.amount) > 0),
)
</script>

<template>
  <aside
    class="lg-analysis-rail cost-ledger-analysis-rail project-operation-analysis-rail"
    aria-label="成本辅助分析"
  >
    <div class="lg-analysis-panel lg-fill-card cost-ledger-analysis-panel">
      <header class="cost-ledger-analysis-head lg-analysis-header">
        <div>
          <div class="cost-ledger-analysis-title lg-analysis-heading">辅助分析</div>
          <div class="lg-analysis-description">成本类型、来源与预算预警</div>
        </div>
      </header>

      <section class="cost-ledger-analysis-section">
        <div class="cost-ledger-section-title">成本类型占比</div>
        <div v-for="item in costTypeBreakdown" :key="item.label" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: 'var(--kpi-paid)' }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: barPercent(item.amount), background: 'var(--kpi-paid)' }"
            ></span>
          </span>
          <span class="lg-type-num">{{ fmtWan(item.amount) }}</span>
          <span class="lg-type-pct">{{ barPercent(item.amount) }}</span>
        </div>
      </section>

      <section class="cost-ledger-analysis-section">
        <div class="cost-ledger-section-title">来源类型分布</div>
        <div v-for="item in sourceBreakdown" :key="item.label" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: 'var(--kpi-amount)' }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: barPercent(item.amount), background: 'var(--kpi-amount)' }"
            ></span>
          </span>
          <span class="lg-type-num">{{ fmtWan(item.amount) }}</span>
          <span class="lg-type-pct">{{ barPercent(item.amount) }}</span>
        </div>
      </section>

      <section class="cost-ledger-analysis-section">
        <div class="lg-warning-head">
          <div class="cost-ledger-section-title">超预算预警</div>
          <span class="cost-ledger-warning-count">{{ warningCount }} 项</span>
        </div>
        <div v-for="item in warningItems" :key="'warn-' + item.label" class="lg-warning-item">
          <span class="lg-warning-project">{{ item.label }}</span>
          <span class="lg-warning-days">{{ fmtWan(item.amount) }} 万</span>
        </div>
        <div v-if="!hasWarningItems" class="lg-warning-empty">暂无超预算项</div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.cost-ledger-analysis-rail {
}

.cost-ledger-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
  padding: 0 0 12px;
}

.cost-ledger-analysis-head,
.cost-ledger-analysis-section .lg-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cost-ledger-analysis-head {
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.cost-ledger-analysis-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 20px;
}

.cost-ledger-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.cost-ledger-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 10px 16px 0;
}

.cost-ledger-analysis-section + .cost-ledger-analysis-section {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.cost-ledger-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.cost-ledger-analysis-section :deep(.lg-type-row),
.cost-ledger-analysis-section .lg-type-row {
  display: grid;
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 42px 46px;
  align-items: center;
  gap: 8px;
  color: var(--text);
  line-height: 1.5;
}

.cost-ledger-analysis-section .lg-type-dot {
  margin-top: 0;
}

.cost-ledger-analysis-section .lg-type-label {
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-ledger-analysis-section .lg-warning-empty {
  color: var(--muted);
  font-size: 13px;
  line-height: 20px;
}

@media (max-width: 1200px) {
  .cost-ledger-analysis-rail {
    width: 100%;
  }
}
</style>
