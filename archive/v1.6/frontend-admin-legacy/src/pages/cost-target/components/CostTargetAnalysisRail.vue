<script setup lang="ts">
import type { CostTargetVO } from '@/types/costTarget'

interface SummaryItem {
  label: string
  count: number
  color: string
}

const props = defineProps<{
  total: number
  targetStatusSummary: SummaryItem[]
  targetVersionSummary: SummaryItem[]
  recentTargets: CostTargetVO[]
}>()

function targetPercent(value: number): number {
  if (!props.total) return 0
  return Math.round((value / props.total) * 100)
}
</script>

<template>
  <aside
    class="lg-analysis-rail ct-analysis-rail project-operation-analysis-rail"
    aria-label="成本目标辅助分析"
  >
    <div class="lg-analysis-panel lg-fill-card ct-analysis-panel">
      <header class="ct-analysis-head lg-analysis-header">
        <div>
          <div class="ct-analysis-title lg-analysis-heading">辅助分析</div>
          <div class="lg-analysis-description">审批状态、金额与近期目标</div>
        </div>
      </header>

      <section class="ct-analysis-section">
        <div class="ct-section-title">审批状态分布</div>
        <div v-for="item in targetStatusSummary" :key="item.label" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: item.color }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: targetPercent(item.count) + '%', background: item.color }"
            ></span>
          </span>
          <span class="lg-type-num">{{ item.count }}</span>
          <span class="lg-type-pct">{{ targetPercent(item.count) }}%</span>
        </div>
      </section>

      <section class="ct-analysis-section">
        <div class="ct-section-title">版本状态分布</div>
        <div v-for="item in targetVersionSummary" :key="item.label" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: item.color }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: targetPercent(item.count) + '%', background: item.color }"
            ></span>
          </span>
          <span class="lg-type-num">{{ item.count }}</span>
          <span class="lg-type-pct">{{ targetPercent(item.count) }}%</span>
        </div>
      </section>

      <section class="ct-analysis-section">
        <div class="ct-section-title">近期版本</div>
        <div v-for="item in recentTargets" :key="item.id" class="ct-status-row">
          <span class="lg-type-dot" style="background: #1890ff"></span>
          <span class="ct-status-label">{{ item.versionName }}</span>
          <strong>{{ item.versionNo }}</strong>
        </div>
        <div v-if="!recentTargets.length" class="ct-empty-state">暂无成本目标版本</div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.ct-analysis-rail {
  padding-top: 0;
}

.ct-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0 0 12px;
}

.ct-analysis-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.ct-analysis-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 20px;
}

.ct-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 10px 16px 0;
}

.ct-analysis-section + .ct-analysis-section {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.ct-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.ct-analysis-section :deep(.lg-type-row),
.ct-analysis-section .lg-type-row {
  display: grid;
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
  align-items: center;
  gap: 8px;
  color: var(--text);
  line-height: 1.5;
}

.ct-analysis-section .lg-type-dot {
  margin-top: 0;
}

.ct-analysis-section .lg-type-label {
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ct-status-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.ct-status-label {
  min-width: 0;
  overflow: hidden;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ct-status-row strong {
  color: var(--text);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.ct-empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 72px;
  color: var(--muted);
  font-size: var(--font-size-sm);
  text-align: center;
}

@media (max-width: 768px) {
  .ct-analysis-rail {
    width: 100%;
  }
}
</style>
