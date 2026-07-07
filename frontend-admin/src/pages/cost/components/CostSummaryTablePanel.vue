<script setup lang="ts">
import { FileSearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { ColumnSettingsButton } from '@/components/list-page'
import type { ProjectVO } from '@/types/project'
import type { CostSummaryVO } from '@/types/cost'

type CostSubjectSummary = CostSummaryVO['subjects'][number]
type CheckStatus = 'overrun' | 'saving' | 'balanced'

defineProps<{
  summary: CostSummaryVO | null
  selectedProject: ProjectVO | undefined
  selectedProjectId: string | undefined
  filteredSummarySubjects: CostSubjectSummary[]
  summarySubjects: CostSubjectSummary[]
  isMobile: boolean
  loading: boolean
  visibleGridColumns: Array<Record<string, unknown>>
  columnSettings: Array<Record<string, unknown>>
  colVisible: Record<string, boolean>
  fmtAmount: (val: string | undefined) => string
  fmtDeviation: (val: string | undefined) => string
  getDeviationTone: (val: string | undefined) => string
  getCheckStatus: (row: Pick<CostSubjectSummary, 'costDeviation'>) => CheckStatus
  getCheckStatusText: (row: Pick<CostSubjectSummary, 'costDeviation'>) => string
  onRefresh: () => void
  onToggleCol: (key: string) => void
}>()
</script>

<template>
  <section class="lg-list-table-panel cost-summary-panel">
    <div class="lg-toolbar cost-toolbar">
      <div class="lg-toolbar-left">
        <div class="cost-toolbar-heading">
          <strong>科目核对明细</strong>
          <span class="cost-toolbar-meta">
            {{
              summary
                ? `当前 ${filteredSummarySubjects.length} / ${summarySubjects.length} 个科目`
                : '选择项目后开始核对'
            }}
          </span>
        </div>
        <div class="cost-toolbar-context">
          <span class="cost-toolbar-project-label">当前项目</span>
          <strong>{{ summary?.projectName || selectedProject?.projectName || '-' }}</strong>
          <span>科目维度核对 · 金额单位：万元</span>
        </div>
      </div>
      <div class="lg-toolbar-right">
        <div class="cost-reconcile-badges">
          <a-tag color="blue">成本目标</a-tag>
          <a-tag color="cyan">合同锁定</a-tag>
          <a-tag color="green">实际成本</a-tag>
          <a-tag color="orange">付款进度</a-tag>
        </div>
        <a-button
          size="large"
          :disabled="!selectedProjectId"
          @click="onRefresh"
          aria-label="重新计算动态成本"
        >
          <template #icon><ReloadOutlined /></template>
          重算动态成本
        </a-button>
        <ColumnSettingsButton
          v-if="!isMobile"
          :columns="columnSettings"
          :visible="colVisible"
          @toggle="onToggleCol"
        />
      </div>
    </div>

    <template v-if="summary">
      <div v-if="isMobile" class="cost-summary-mobile-list">
        <div v-if="loading" class="cost-summary-mobile-state">
          <a-spin />
        </div>
        <div v-else-if="!filteredSummarySubjects.length" class="cost-summary-mobile-state">
          <a-empty description="暂无科目明细" />
        </div>
        <template v-else>
          <article
            v-for="row in filteredSummarySubjects"
            :key="row.costSubjectId"
            class="cost-summary-mobile-card"
          >
            <div class="cost-summary-mobile-card-head">
              <strong>{{ row.costSubjectName || '-' }}</strong>
              <a-tag :class="['cost-check-tag', `is-${getCheckStatus(row)}`]">
                {{ getCheckStatusText(row) }}
              </a-tag>
            </div>
            <div class="cost-summary-mobile-card-meta">
              成本目标：{{ fmtAmount(row.targetCost) }} 万元
            </div>
            <div class="cost-summary-mobile-card-meta">
              动态成本：{{ fmtAmount(row.dynamicCost) }} 万元
            </div>
            <div class="cost-summary-mobile-card-meta">
              成本偏差：
              <span :class="`is-${getDeviationTone(row.costDeviation)}`">
                {{ fmtDeviation(row.costDeviation) }} 万元
              </span>
            </div>
          </article>
        </template>
      </div>
      <div v-else class="lg-table-wrap cost-summary-table">
        <vxe-grid
          :data="filteredSummarySubjects"
          :columns="visibleGridColumns"
          :loading="loading"
          :column-config="{ resizable: true }"
          stripe
          border="inner"
          size="small"
          height="100%"
        >
          <template #checkStatus="{ row }">
            <a-tag :class="['cost-check-tag', `is-${getCheckStatus(row)}`]">
              {{ getCheckStatusText(row) }}
            </a-tag>
          </template>
          <template #targetCost="{ row }">
            <span>{{ fmtAmount(row.targetCost) }}</span>
          </template>
          <template #contractLockedCost="{ row }">
            <span>{{ fmtAmount(row.contractLockedCost) }}</span>
          </template>
          <template #actualCost="{ row }">
            <span>{{ fmtAmount(row.actualCost) }}</span>
          </template>
          <template #paidAmount="{ row }">
            <span>{{ fmtAmount(row.paidAmount) }}</span>
          </template>
          <template #dynamicCost="{ row }">
            <span>{{ fmtAmount(row.dynamicCost) }}</span>
          </template>
          <template #costDeviation="{ row }">
            <span
              class="cost-summary-deviation"
              :class="`is-${getDeviationTone(row.costDeviation)}`"
            >
              {{ fmtDeviation(row.costDeviation) }}
            </span>
          </template>
        </vxe-grid>
      </div>
    </template>

    <template v-else>
      <section class="cost-summary-empty">
        <FileSearchOutlined class="cost-summary-empty-icon" />
        <div class="cost-summary-empty-title">请选择项目开始核对</div>
        <div class="cost-summary-empty-text">
          选择项目后查看成本来源、科目明细、成本偏差和核对结论。
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.cost-summary-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  min-height: 0;
}

.cost-toolbar {
  flex: 0 0 auto;
  border-bottom: 1px solid var(--border-subtle);
}

.cost-toolbar-meta {
  margin-left: var(--spacing-xs);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 400;
}

.cost-summary-table {
  flex: 1 1 auto;
  margin: 0;
  min-height: 0;
}

.cost-summary-mobile-list {
  display: grid;
  flex: 1 1 auto;
  gap: 12px;
  padding: 12px;
}

.cost-summary-mobile-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.cost-summary-mobile-card {
  display: grid;
  gap: 8px;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.cost-summary-mobile-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.cost-summary-mobile-card-meta {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.cost-summary-deviation {
  font-weight: 600;
}

.cost-summary-deviation.is-danger {
  color: var(--error);
}

.cost-summary-deviation.is-success {
  color: var(--success);
}

.cost-summary-deviation.is-neutral {
  color: var(--text-secondary);
}

.cost-check-tag {
  margin-right: 0;
  border-radius: var(--radius-sm);
}

.cost-check-tag.is-overrun {
  color: var(--error);
  background: var(--error-soft);
  border-color: var(--border-subtle);
}

.cost-check-tag.is-saving {
  color: var(--success);
  background: var(--success-soft);
  border-color: var(--border-subtle);
}

.cost-check-tag.is-balanced {
  color: var(--text-secondary);
  background: var(--surface-subtle);
  border-color: var(--border-subtle);
}

.cost-toolbar-heading {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.cost-toolbar-context {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.cost-toolbar-context strong {
  color: var(--text);
  font-size: 14px;
}

.cost-toolbar-project-label {
  color: var(--muted);
}

.cost-reconcile-badges {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.cost-summary-empty {
  min-height: 430px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  color: var(--muted);
  background: var(--surface);
}

.cost-summary-empty-icon {
  font-size: 46px;
  color: var(--primary);
}

.cost-summary-empty-title {
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--text);
}

.cost-summary-empty-text {
  font-size: var(--font-size-sm);
}

@media (max-width: 960px) {
  .cost-summary-search-actions {
    justify-content: flex-start;
  }

  .cost-reconcile-badges {
    justify-content: flex-start;
  }
}
</style>
