<script setup lang="ts">
import { CheckCircleOutlined, LinkOutlined, WarningOutlined } from '@ant-design/icons-vue'
import type { CostSummaryVO } from '@/types/cost'

type CostSubjectSummary = CostSummaryVO['subjects'][number]

defineProps<{
  conclusionItems: Array<{ label: string; value: string; tone: string }>
  overBudgetItems: CostSubjectSummary[]
  sourceRows: Array<{ key: string; label: string; value: string; unit: string }>
  sourceCards: Array<{ key: string; label: string; value: string | undefined; path: string }>
  highRiskItems: CostSubjectSummary[]
  fmtAmount: (val: string | undefined) => string
  fmtPercent: (val: string | undefined, base: string | undefined) => string
  go: (path: string) => void
}>()
</script>

<template>
  <aside class="lg-analysis-rail cost-reconcile-rail">
    <div class="lg-analysis-panel lg-fill-card cost-reconcile-rail-body">
      <header class="cost-reconcile-rail-head">
        <div>
          <div class="cost-reconcile-rail-title">辅助分析</div>
        </div>
      </header>

      <section class="lg-panel">
        <div class="lg-panel-title">核对结论</div>
        <div class="cost-conclusion-list">
          <div
            v-for="item in conclusionItems"
            :key="item.label"
            :class="['cost-conclusion-row', `is-${item.tone}`]"
          >
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>

      <section class="lg-panel">
        <div class="lg-panel-title">重点差异科目</div>
        <div class="cost-risk-list">
          <template v-if="overBudgetItems.length">
            <div v-for="item in overBudgetItems.slice(0, 5)" :key="item.costSubjectId">
              <span>
                <WarningOutlined />
                {{ item.costSubjectName }}
              </span>
              <strong>+{{ fmtAmount(item.costDeviation) }} 万</strong>
            </div>
          </template>
          <div v-else class="cost-summary-muted-state">
            <CheckCircleOutlined />
            暂无超目标科目
          </div>
        </div>
      </section>

      <section class="lg-panel">
        <div class="lg-panel-title">成本来源对比</div>
        <div class="cost-source-rail-list">
          <button
            v-for="item in sourceRows"
            :key="item.key"
            type="button"
            class="cost-source-rail-row"
          >
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <span>{{ item.unit }}</span>
          </button>
        </div>
      </section>

      <section class="lg-panel">
        <div class="lg-panel-title">数据来源</div>
        <div class="cost-source-rail-list">
          <button
            v-for="card in sourceCards"
            :key="card.key"
            type="button"
            class="cost-source-rail-row"
            @click="go(card.path)"
          >
            <span>{{ card.label }}</span>
            <strong>{{ fmtAmount(card.value) }} 万</strong>
            <LinkOutlined />
          </button>
        </div>
      </section>

      <section v-if="highRiskItems.length" class="lg-panel">
        <div class="lg-panel-title">需优先复核</div>
        <div class="cost-risk-list">
          <div v-for="item in highRiskItems" :key="`high-${item.costSubjectId}`">
            <span>{{ item.costSubjectName }}</span>
            <strong>{{ fmtPercent(item.costDeviation, item.targetCost) }}</strong>
          </div>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.cost-reconcile-rail {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
}

.cost-reconcile-rail-body {
  gap: 0;
  overflow: auto;
}

.cost-reconcile-rail-head {
  padding: 12px 16px 10px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-bottom: 0;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}

.cost-reconcile-rail-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 20px;
}

.cost-reconcile-rail .lg-panel {
  flex: 0 0 auto;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  border-radius: 0;
}

.cost-reconcile-rail .lg-panel:first-of-type {
  flex: 1 1 auto;
  min-height: 0;
}

.cost-source-rail-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--spacing-sm) 14px;
}

.cost-source-rail-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.cost-source-rail-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-source-rail-row strong {
  color: var(--text);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.cost-conclusion-list,
.cost-risk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: var(--spacing-sm) 14px;
}

.cost-conclusion-row,
.cost-risk-list > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  min-height: 34px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.cost-conclusion-row strong,
.cost-risk-list strong {
  color: var(--text);
  font-weight: 700;
  white-space: nowrap;
}

.cost-conclusion-row.is-danger strong,
.cost-risk-list strong {
  color: var(--error);
}

.cost-conclusion-row.is-success strong {
  color: var(--success);
}

.cost-risk-list span {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.cost-risk-list span :deep(.anticon) {
  color: var(--error);
}

.cost-summary-muted-state {
  justify-content: center;
  color: var(--muted);
  text-align: center;
}

.cost-summary-muted-state :deep(.anticon) {
  color: var(--success);
}

@media (max-width: 960px) {
  .cost-reconcile-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .cost-reconcile-rail {
    display: none;
  }
}
</style>
