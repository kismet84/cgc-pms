<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  FileSearchOutlined,
  FilterOutlined,
  LinkOutlined,
  ReloadOutlined,
  SearchOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { getCostSummary, getCostSummaryHistory, refreshCostSummary } from '@/api/modules/cost'
import { getProjectList } from '@/api/modules/project'
import type { SelectOption } from '@/types/ui'
import type { CostSummaryHistoryVO, CostSummaryVO } from '@/types/cost'
import type { ProjectVO } from '@/types/project'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { normalizeArray } from '@/utils/normalizeArray'
import CostSummaryTablePanel from './components/CostSummaryTablePanel.vue'
import CostSummaryAnalysisRail from './components/CostSummaryAnalysisRail.vue'
import { useMobileViewport } from '@/composables/useMobileViewport'

type CostSubjectSummary = CostSummaryVO['subjects'][number]
type CheckStatus = 'overrun' | 'saving' | 'balanced'

const { isMobile } = useMobileViewport()
const mobileFiltersOpen = ref(false)

const router = useRouter()

const projectList = ref<ProjectVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const keyword = ref('')
const loading = ref(false)
const summary = ref<CostSummaryVO | null>(null)
const historyOpen = ref(false)
const historyLoading = ref(false)
const historyRows = ref<CostSummaryHistoryVO[]>([])
const historyError = ref('')

function parseAmount(val: string | undefined): number {
  if (!val) return 0
  const n = Number.parseFloat(val)
  return Number.isFinite(n) ? n : 0
}

async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNo: 1, pageSize: 50 })
    projectList.value = normalizeArray<ProjectVO>(res.records)
  } catch (e: unknown) {
    console.error(e)
    projectList.value = []
  }
}

async function fetchSummary() {
  if (!selectedProjectId.value) {
    summary.value = null
    return
  }
  loading.value = true
  try {
    summary.value = await getCostSummary(selectedProjectId.value)
  } catch (e: unknown) {
    console.error(e)
    summary.value = null
    message.error('加载项目成本明细失败')
  } finally {
    loading.value = false
  }
}

async function handleRefresh() {
  if (!selectedProjectId.value) {
    message.warning('请先选择项目')
    return
  }
  loading.value = true
  try {
    summary.value = await refreshCostSummary(selectedProjectId.value)
    message.success('动态成本已重新计算')
  } catch (e: unknown) {
    console.error(e)
    message.error('重新计算失败')
  } finally {
    loading.value = false
  }
}

async function openHistory() {
  if (!selectedProjectId.value) {
    message.warning('请先选择项目')
    return
  }
  historyOpen.value = true
  historyLoading.value = true
  historyRows.value = []
  historyError.value = ''
  try {
    historyRows.value = normalizeArray<CostSummaryHistoryVO>(
      await getCostSummaryHistory(selectedProjectId.value),
    )
  } catch (e: unknown) {
    console.error(e)
    historyError.value = '历史快照加载失败，请稍后重试'
    message.error('加载成本历史快照失败')
  } finally {
    historyLoading.value = false
  }
}

function handleProjectChange(val: string | undefined) {
  selectedProjectId.value = val
  summary.value = null
  historyOpen.value = false
  historyRows.value = []
  historyError.value = ''
  if (val) fetchSummary()
}

function fmtAmount(val: string | undefined): string {
  const n = parseAmount(val)
  return (n / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function fmtDeviation(val: string | undefined): string {
  const n = parseAmount(val)
  const abs = Math.abs(n / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  if (n > 0) return `+${abs}`
  if (n < 0) return `-${abs}`
  return abs
}

function getDeviationTone(val: string | undefined): string {
  const n = parseAmount(val)
  if (n > 0) return 'danger'
  if (n < 0) return 'success'
  return 'neutral'
}

function fmtPercent(val: string | undefined, base: string | undefined): string {
  const v = parseAmount(val)
  const b = parseAmount(base)
  if (!b) return '0.0%'
  return `${((v / b) * 100).toFixed(1)}%`
}

function getCheckStatus(row: Pick<CostSubjectSummary, 'costDeviation'>): CheckStatus {
  const deviation = parseAmount(row.costDeviation)
  if (deviation > 0) return 'overrun'
  if (deviation < 0) return 'saving'
  return 'balanced'
}

function getCheckStatusText(row: Pick<CostSubjectSummary, 'costDeviation'>): string {
  const status = getCheckStatus(row)
  if (status === 'overrun') return '超目标'
  if (status === 'saving') return '低于目标'
  return '持平'
}

function go(path: string) {
  router.push(path)
}

function handleSearch() {
  if (selectedProjectId.value && !summary.value) {
    fetchSummary()
  }
}

function handleReset() {
  selectedProjectId.value = undefined
  keyword.value = ''
  summary.value = null
  historyOpen.value = false
  historyRows.value = []
  historyError.value = ''
}

function fmtDate(val: string | undefined): string {
  return val ? val.slice(0, 10) : '-'
}

const selectedProject = computed(() =>
  projectList.value.find((project) => project.id === selectedProjectId.value),
)

const summarySubjects = computed(() =>
  summary.value ? normalizeArray<CostSubjectSummary>(summary.value.subjects) : [],
)
const filteredSummarySubjects = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return summarySubjects.value
  return summarySubjects.value.filter((item) =>
    `${item.costSubjectName ?? ''}`.toLowerCase().includes(value),
  )
})

const overBudgetItems = computed(() =>
  summarySubjects.value
    .filter((item) => parseAmount(item.costDeviation) > 0)
    .sort((a, b) => parseAmount(b.costDeviation) - parseAmount(a.costDeviation)),
)

const normalSubjectCount = computed(
  () => summarySubjects.value.length - overBudgetItems.value.length,
)

const highRiskItems = computed(() =>
  overBudgetItems.value
    .filter((item) => {
      const target = parseAmount(item.targetCost)
      const deviation = parseAmount(item.costDeviation)
      return target > 0 && deviation / target >= 0.1
    })
    .slice(0, 5),
)

const sourceCards = computed(() => {
  if (!summary.value) return []
  return [
    {
      key: 'target',
      label: '成本目标',
      value: summary.value.targetCost,
      path: '/cost-target/index',
    },
    {
      key: 'contract',
      label: '合同锁定成本',
      value: summary.value.contractLockedCost,
      path: '/contract/ledger',
    },
    {
      key: 'actual',
      label: '实际成本',
      value: summary.value.actualCost,
      path: '/cost/ledger',
    },
    {
      key: 'paid',
      label: '已付款',
      value: summary.value.paidAmount,
      path: '/payment/application',
    },
  ]
})

const sourceRows = computed(() => {
  if (!summary.value) return []
  return [
    { key: 'target', label: '成本目标', value: fmtAmount(summary.value.targetCost), unit: '万元' },
    {
      key: 'contract',
      label: '合同锁定',
      value: fmtAmount(summary.value.contractLockedCost),
      unit: '万元',
    },
    { key: 'actual', label: '实际成本', value: fmtAmount(summary.value.actualCost), unit: '万元' },
    { key: 'paid', label: '已付款', value: fmtAmount(summary.value.paidAmount), unit: '万元' },
  ]
})

const conclusionItems = computed(() => {
  if (!summary.value) return []
  const deviation = parseAmount(summary.value.costDeviation)
  return [
    {
      label: '核对科目',
      value: `${summarySubjects.value.length} 项`,
      tone: 'neutral',
    },
    {
      label: '超目标科目',
      value: `${overBudgetItems.value.length} 项`,
      tone: overBudgetItems.value.length ? 'danger' : 'success',
    },
    {
      label: '正常科目',
      value: `${normalSubjectCount.value} 项`,
      tone: 'success',
    },
    {
      label: '总偏差率',
      value: fmtPercent(summary.value.costDeviation, summary.value.targetCost),
      tone: deviation > 0 ? 'danger' : deviation < 0 ? 'success' : 'neutral',
    },
  ]
})

const gridColumns = computed(() => [
  { field: 'costSubjectName', title: '成本科目', minWidth: 160, ellipsis: true },
  {
    field: 'checkStatus',
    title: '核对状态',
    width: 110,
    slots: { default: 'checkStatus' },
  },
  {
    field: 'targetCost',
    title: '成本目标',
    width: 130,
    align: 'right' as const,
    slots: { default: 'targetCost' },
  },
  {
    field: 'contractLockedCost',
    title: '合同锁定',
    width: 150,
    align: 'right' as const,
    slots: { default: 'contractLockedCost' },
  },
  {
    field: 'actualCost',
    title: '实际成本',
    width: 130,
    align: 'right' as const,
    slots: { default: 'actualCost' },
  },
  {
    field: 'paidAmount',
    title: '已付款',
    width: 120,
    align: 'right' as const,
    slots: { default: 'paidAmount' },
  },
  {
    field: 'dynamicCost',
    title: '动态成本',
    width: 130,
    align: 'right' as const,
    slots: { default: 'dynamicCost' },
  },
  {
    field: 'costDeviation',
    title: '成本偏差',
    width: 130,
    align: 'right' as const,
    slots: { default: 'costDeviation' },
  },
])

const historyColumns = [
  { dataIndex: 'summaryDate', key: 'summaryDate', title: '汇总日期', width: 120 },
  { dataIndex: 'costSubjectName', key: 'costSubjectName', title: '成本科目', width: 180 },
  { dataIndex: 'targetCost', key: 'targetCost', title: '成本目标', width: 130, align: 'right' },
  { dataIndex: 'actualCost', key: 'actualCost', title: '实际成本', width: 130, align: 'right' },
  { dataIndex: 'dynamicCost', key: 'dynamicCost', title: '动态成本', width: 130, align: 'right' },
  {
    dataIndex: 'costDeviation',
    key: 'costDeviation',
    title: '成本偏差',
    width: 130,
    align: 'right',
  },
]

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('cost_reconcile_cols_v1', gridColumns)

onMounted(() => {
  fetchProjects()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page cost-summary-page project-operation-list-page">
    <div class="lg-page-head cost-summary-page-head">
      <div class="cost-summary-head-main">
        <a-breadcrumb class="cost-summary-breadcrumb">
          <a-breadcrumb-item>成本管理</a-breadcrumb-item>
          <a-breadcrumb-item>项目成本明细核对</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid cost-summary-grid project-operation-workspace">
      <div class="lg-left cost-summary-main project-operation-main-column">
        <div class="lg-kpi-strip cost-reconcile-kpis project-operation-kpi">
          <div class="lg-kpi-card">
            <span class="cost-summary-kpi-icon is-target"><FileSearchOutlined /></span>
            <span class="lg-kpi-card-label">成本目标</span>
            <span class="lg-kpi-card-value">
              {{ summary ? fmtAmount(summary.targetCost) : '--' }}
              <small v-if="summary">万元</small>
            </span>
          </div>
          <div class="lg-kpi-card">
            <span class="cost-summary-kpi-icon is-locked"><LinkOutlined /></span>
            <span class="lg-kpi-card-label">合同锁定成本</span>
            <span class="lg-kpi-card-value">
              {{ summary ? fmtAmount(summary.contractLockedCost) : '--' }}
              <small v-if="summary">万元</small>
            </span>
          </div>
          <div class="lg-kpi-card">
            <span class="cost-summary-kpi-icon is-actual"><CheckCircleOutlined /></span>
            <span class="lg-kpi-card-label">实际成本</span>
            <span class="lg-kpi-card-value">
              {{ summary ? fmtAmount(summary.actualCost) : '--' }}
              <small v-if="summary">万元</small>
            </span>
          </div>
          <div class="lg-kpi-card">
            <span class="cost-summary-kpi-icon is-dynamic"><ReloadOutlined /></span>
            <span class="lg-kpi-card-label">动态成本</span>
            <span class="lg-kpi-card-value">
              {{ summary ? fmtAmount(summary.dynamicCost) : '--' }}
              <small v-if="summary">万元</small>
            </span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="cost-summary-kpi-icon is-risk"><WarningOutlined /></span>
            <span class="lg-kpi-card-label">成本偏差</span>
            <span
              class="lg-kpi-card-value"
              :class="summary ? `is-${getDeviationTone(summary.costDeviation)}` : ''"
            >
              {{ summary ? fmtDeviation(summary.costDeviation) : '--' }}
              <small v-if="summary">万元</small>
            </span>
          </div>
        </div>

        <div class="lg-search-bar cost-summary-search project-operation-query-panel">
          <div
            id="cost-summary-filter-panel"
            class="cost-summary-search-row cost-summary-search-row--filters project-operation-filter-panel"
            :class="{ 'is-open': mobileFiltersOpen }"
          >
            <div class="cost-summary-search-main">
              <a-select
                v-model:value="selectedProjectId"
                placeholder="选择项目进行成本核对"
                allow-clear
                class="cost-summary-project-select"
                size="large"
                show-search
                :filter-option="
                  (input: string, option: SelectOption) =>
                    option.label?.toLowerCase().includes(input.toLowerCase())
                "
                @change="handleProjectChange"
              >
                <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
                  {{ p.projectName }}
                </a-select-option>
              </a-select>
            </div>
          </div>
          <div class="cost-summary-search-row cost-summary-search-row--actions">
            <a-input
              v-model:value="keyword"
              class="cost-summary-keyword"
              placeholder="按成本科目名称筛选当前表格"
              allow-clear
              size="large"
              @pressEnter="handleSearch"
            >
              <template #prefix>
                <SearchOutlined />
              </template>
            </a-input>
            <div class="cost-summary-search-actions">
              <a-button
                class="project-operation-desktop-query-action"
                type="primary"
                size="large"
                :disabled="!selectedProjectId"
                @click="handleSearch"
              >
                搜索
              </a-button>
              <a-button
                class="project-operation-desktop-query-action"
                data-testid="cost-summary-history-button"
                size="large"
                :disabled="!selectedProjectId"
                @click="openHistory"
              >
                历史快照
              </a-button>
              <a-button
                class="project-operation-desktop-query-action"
                size="large"
                @click="handleReset"
                >重置</a-button
              >
              <a-button
                class="project-operation-filter-toggle"
                size="large"
                :aria-expanded="mobileFiltersOpen"
                aria-controls="cost-summary-filter-panel"
                @click="mobileFiltersOpen = !mobileFiltersOpen"
              >
                <template #icon><FilterOutlined /></template>筛选
              </a-button>
            </div>
          </div>
        </div>

        <CostSummaryTablePanel
          :summary="summary"
          :selected-project="selectedProject"
          :selected-project-id="selectedProjectId"
          :filtered-summary-subjects="filteredSummarySubjects"
          :summary-subjects="summarySubjects"
          :is-mobile="isMobile"
          :loading="loading"
          :visible-grid-columns="visibleGridColumns"
          :column-settings="columnSettings"
          :col-visible="colVisible"
          :fmt-amount="fmtAmount"
          :fmt-deviation="fmtDeviation"
          :get-deviation-tone="getDeviationTone"
          :get-check-status="getCheckStatus"
          :get-check-status-text="getCheckStatusText"
          :on-refresh="handleRefresh"
          :on-toggle-col="toggleCol"
        />
      </div>

      <CostSummaryAnalysisRail
        :conclusion-items="conclusionItems"
        :over-budget-items="overBudgetItems"
        :source-rows="sourceRows"
        :source-cards="sourceCards"
        :high-risk-items="highRiskItems"
        :fmt-amount="fmtAmount"
        :fmt-percent="fmtPercent"
        :go="go"
      />
    </div>

    <a-modal
      v-model:open="historyOpen"
      :title="`${selectedProject?.projectName || '当前项目'}成本历史快照`"
      :footer="null"
      width="1120px"
    >
      <div class="cost-summary-history-dialog" data-testid="cost-summary-history-dialog">
        <a-spin :spinning="historyLoading">
          <a-alert v-if="historyError" type="error" show-icon :message="historyError" />
          <a-empty
            v-else-if="!historyLoading && historyRows.length === 0"
            description="暂无历史快照"
          />
          <a-table
            v-else
            class="cost-summary-history-table"
            :columns="historyColumns"
            :data-source="historyRows"
            :loading="historyLoading"
            :pagination="false"
            :row-key="(row: CostSummaryHistoryVO) => row.id"
            :scroll="{ x: 820 }"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'summaryDate'">
                {{ fmtDate(record.summaryDate) }}
              </template>
              <template
                v-else-if="
                  ['targetCost', 'actualCost', 'dynamicCost', 'costDeviation'].includes(column.key)
                "
              >
                {{
                  column.key === 'costDeviation'
                    ? fmtDeviation(record[column.key])
                    : fmtAmount(record[column.key])
                }}
                万元
              </template>
              <template v-else>
                {{ record[column.key] || '-' }}
              </template>
            </template>
          </a-table>
        </a-spin>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.cost-summary-breadcrumb {
  color: var(--muted);
  font-size: var(--font-size-sm);
  line-height: 20px;
}

.cost-summary-page {
  background: var(--surface-subtle);
}

.cost-summary-page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
  gap: 16px;
}

.cost-summary-head-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.cost-summary-head-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.cost-summary-search {
  align-items: stretch;
  flex-wrap: wrap;
  width: 100%;
  margin: 0;
}

.cost-summary-search-row {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  min-width: 0;
  width: 100%;
}

.cost-summary-grid {
}

.cost-summary-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.cost-summary-search-main {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
}

.cost-summary-keyword {
  flex: 1 1 320px;
  min-width: 0;
}

.cost-summary-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.cost-summary-project-select {
  width: 100%;
  min-width: 0;
}

.cost-summary-history-dialog {
  min-height: 220px;
}

.cost-summary-history-table {
  margin-top: 4px;
}

.cost-reconcile-badges {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.cost-reconcile-kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  margin-bottom: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cost-reconcile-kpis .lg-kpi-card {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: auto auto;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  row-gap: 4px;
  min-height: 0;
  padding: 14px 18px;
  border-right: 1px solid var(--border-subtle);
}

.cost-reconcile-kpis .lg-kpi-card:last-child {
  border-right: 0;
}

.cost-summary-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 10px;
  grid-row: 1 / span 2;
}

.cost-summary-kpi-icon.is-locked,
.cost-summary-kpi-icon.is-dynamic {
  color: var(--warning);
  background: var(--warning-soft);
}

.cost-summary-kpi-icon.is-actual {
  color: var(--success);
  background: var(--success-soft);
}

.cost-summary-kpi-icon.is-risk {
  color: var(--error);
  background: var(--error-soft);
}

.cost-reconcile-kpis .lg-kpi-card-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-reconcile-kpis .lg-kpi-card-value {
  overflow: hidden;
  color: var(--text);
  font-size: 22px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-reconcile-kpis .lg-kpi-card-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.cost-summary-deviation.is-danger,
.lg-kpi-card-value.is-danger {
  color: var(--error);
}

.cost-summary-deviation.is-success,
.lg-kpi-card-value.is-success {
  color: var(--success);
}

.cost-summary-deviation.is-neutral,
.lg-kpi-card-value.is-neutral {
  color: var(--text-secondary);
}

@media (max-width: 1280px) {
  .cost-reconcile-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cost-reconcile-kpis .lg-kpi-card {
    border-bottom: 1px solid var(--border-subtle);
  }
}

@media (max-width: 960px) {
  .cost-summary-page-head,
  .cost-summary-search,
  .cost-summary-search-row {
    flex-direction: column;
    align-items: stretch;
  }

  .cost-summary-project-select {
    width: 100%;
    min-width: 0;
  }

  .cost-reconcile-kpis {
    grid-template-columns: minmax(0, 1fr);
  }

  .cost-summary-head-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .cost-reconcile-kpis {
    display: none;
  }
}
</style>
