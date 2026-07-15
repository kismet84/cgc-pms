<script setup lang="ts">
import type { SubMeasureVO } from '@/types/subcontract'

interface MeasureStatusSummaryItem {
  label: string
  count: number
  color: string
  pct: number
}

defineProps<{
  statusCount: number
  kpiMeasureTotal: number
  kpiApproved: number
  approvedRate: number
  measureStatusSummary: MeasureStatusSummaryItem[]
  recentMeasures: SubMeasureVO[]
  fmtWan: (val: number) => string
  measureStatusLabel: (status: string | undefined) => string
}>()
</script>

<template>
  <aside class="lg-analysis-rail subcontract-measure-analysis-rail" aria-label="分包计量辅助分析">
    <div class="lg-analysis-panel subcontract-measure-analysis-panel">
      <section class="subcontract-measure-analysis-section">
        <div class="subcontract-measure-section-head">
          <strong>计量状态分布</strong>
          <span>{{ statusCount }} 条</span>
        </div>
        <div class="subcontract-measure-bar-list">
          <div
            v-for="item in measureStatusSummary"
            :key="item.label"
            class="subcontract-measure-bar-row"
          >
            <div class="subcontract-measure-bar-meta">
              <span><i :style="{ background: item.color }"></i>{{ item.label }}</span>
              <strong>{{ item.count }} 条</strong>
            </div>
            <div class="subcontract-measure-bar-track">
              <span :style="{ width: item.pct + '%', background: item.color }"></span>
            </div>
          </div>
        </div>
      </section>
      <section class="subcontract-measure-analysis-section">
        <div class="subcontract-measure-section-head">
          <strong>金额审核</strong>
          <span>{{ approvedRate }}%</span>
        </div>
        <div class="subcontract-measure-amount-box">
          <div>
            <span>申报</span><strong>{{ fmtWan(kpiMeasureTotal) }} 万元</strong>
          </div>
          <div>
            <span>审核</span><strong>{{ fmtWan(kpiApproved) }} 万元</strong>
          </div>
        </div>
      </section>
      <section class="subcontract-measure-analysis-section">
        <div class="subcontract-measure-section-head">
          <strong>近期计量</strong>
          <span>最新 4 条</span>
        </div>
        <div class="subcontract-measure-recent-list">
          <div
            v-for="item in recentMeasures"
            :key="item.id"
            class="subcontract-measure-recent-item"
          >
            <span>{{ item.measureCode || item.measurePeriod }}</span>
            <strong>{{ measureStatusLabel(item.status) || '-' }}</strong>
          </div>
          <div v-if="!recentMeasures.length" class="subcontract-measure-empty">暂无计量</div>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.subcontract-measure-analysis-rail {
  width: var(--lg-rail-width, 240px);
}

.subcontract-measure-analysis-panel {
  height: 100%;
  min-height: 100%;
  box-sizing: border-box;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.subcontract-measure-analysis-section {
  padding: 14px 16px;
  border-bottom: 1px solid #edf1f5;
}

.subcontract-measure-analysis-section:last-child {
  border-bottom: 0;
}

.subcontract-measure-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.subcontract-measure-section-head strong {
  font-size: 15px;
  color: #0f172a;
}

.subcontract-measure-section-head span {
  font-size: 12px;
  color: #64748b;
}

.subcontract-measure-bar-list,
.subcontract-measure-recent-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.subcontract-measure-bar-meta,
.subcontract-measure-recent-item,
.subcontract-measure-amount-box div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.subcontract-measure-bar-meta span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  color: #334155;
}

.subcontract-measure-bar-meta i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.subcontract-measure-bar-meta strong,
.subcontract-measure-recent-item strong,
.subcontract-measure-amount-box strong {
  color: #0f172a;
  font-weight: 600;
  white-space: nowrap;
}

.subcontract-measure-bar-track {
  margin-top: 7px;
  height: 6px;
  border-radius: 999px;
  background: #f1f5f9;
  overflow: hidden;
}

.subcontract-measure-bar-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.subcontract-measure-amount-box {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.subcontract-measure-amount-box span {
  font-size: 12px;
  color: #64748b;
}

.subcontract-measure-recent-item {
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.subcontract-measure-recent-item:last-child {
  border-bottom: 0;
}

.subcontract-measure-recent-item span {
  min-width: 0;
  overflow: hidden;
  color: #334155;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subcontract-measure-empty {
  padding: 18px 0;
  color: #94a3b8;
  text-align: center;
  font-size: 13px;
}

@media (max-width: 1280px) {
  .subcontract-measure-analysis-rail {
    width: 100%;
  }
}
</style>
