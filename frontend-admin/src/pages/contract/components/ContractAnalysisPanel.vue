<script setup lang="ts">
interface TypeDistItem {
  key: string
  label: string
  value: number
  color: string
}

interface StatusBarItem {
  key: string
  label: string
  value: number
  color: string
  percent: number
}

interface WarningRow {
  project: string
  title: string
  days: number
}

defineProps<{
  typeDistribution: TypeDistItem[]
  typePercent: (value: number) => number
  statusBars: StatusBarItem[]
  warningRows: WarningRow[]
}>()

const emit = defineEmits<{
  (e: 'allAlerts'): void
}>()
</script>

<template>
  <aside class="lg-analysis-rail cl-analysis-rail" aria-label="合同辅助分析">
    <div class="cl-analysis-panel">
      <header class="cl-analysis-head">
        <div>
          <div class="cl-analysis-title">合同分析</div>
          <div class="cl-analysis-subtitle">分布、状态与逾期风险</div>
        </div>
        <a-button type="link" size="small" @click="emit('allAlerts')">查看预警</a-button>
      </header>

      <section class="cl-analysis-section">
        <div class="cl-section-title">合同类型分布</div>
        <div v-for="item in typeDistribution" :key="item.key" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: item.color }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: typePercent(item.value) + '%', background: item.color }"
            ></span>
          </span>
          <span class="lg-type-num">{{ item.value }}</span>
          <span class="lg-type-pct">{{ typePercent(item.value) }}%</span>
        </div>
        <div v-if="!typeDistribution.length" class="cl-analysis-empty">暂无合同类型数据</div>
      </section>

      <section class="cl-analysis-section">
        <div class="cl-section-title">合同状态</div>
        <div v-for="item in statusBars" :key="item.key" class="lg-type-row">
          <span class="lg-type-dot" :style="{ background: item.color }"></span>
          <span class="lg-type-label">{{ item.label }}</span>
          <span class="lg-type-bar-wrap">
            <span
              class="lg-type-bar"
              :style="{ width: item.percent + '%', background: item.color }"
            ></span>
          </span>
          <span class="lg-type-num">{{ item.value }}</span>
          <span class="lg-type-pct">{{ item.percent }}%</span>
        </div>
        <div v-if="!statusBars.length" class="cl-analysis-empty">暂无合同状态数据</div>
      </section>

      <section class="cl-analysis-section">
        <div class="cl-warning-head">
          <div class="cl-section-title">逾期预警</div>
          <span class="cl-warning-count">{{ warningRows.length }} 项</span>
        </div>
        <div
          v-for="row in warningRows"
          :key="`${row.project}-${row.title}`"
          class="lg-warning-item"
        >
          <span class="lg-warning-project">{{ row.project }}</span>
          <span class="lg-warning-title">{{ row.title }}</span>
          <span class="lg-warning-days">{{ row.days }}天</span>
        </div>
        <div v-if="!warningRows.length" class="lg-warning-empty">暂无逾期合同</div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.cl-analysis-rail {
  width: 336px;
}

.cl-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cl-analysis-head,
.cl-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cl-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.cl-analysis-subtitle,
.cl-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.cl-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.cl-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.cl-analysis-empty {
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: center;
}

.cl-analysis-section :deep(.lg-type-row),
.lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

@media (max-width: 1200px) {
  .cl-analysis-rail {
    width: 100%;
  }
}
</style>
