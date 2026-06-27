<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  FileSearchOutlined,
  LinkOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { getCostSummary, refreshCostSummary } from '@/api/modules/cost'
import { getProjectList } from '@/api/modules/project'
import type { SelectOption } from '@/types/ui'
import type { CostSummaryVO } from '@/types/cost'
import type { ProjectVO } from '@/types/project'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

type CostSubjectSummary = CostSummaryVO['subjects'][number]
type CheckStatus = 'overrun' | 'saving' | 'balanced'

const router = useRouter()

const projectList = ref<ProjectVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const loading = ref(false)
const summary = ref<CostSummaryVO | null>(null)

function normalizeArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (value && typeof value === 'object') {
    const records = (value as { records?: unknown }).records
    if (Array.isArray(records)) return records as T[]
  }
  return []
}

function parseAmount(val: string | undefined): number {
  if (!val) return 0
  const n = Number.parseFloat(val)
  return Number.isFinite(n) ? n : 0
}

async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 50 })
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

function handleProjectChange(val: string | undefined) {
  selectedProjectId.value = val
  if (val) fetchSummary()
  else summary.value = null
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

const selectedProject = computed(() =>
  projectList.value.find((project) => project.id === selectedProjectId.value),
)

const summarySubjects = computed(() =>
  summary.value ? normalizeArray<CostSubjectSummary>(summary.value.subjects) : [],
)

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
      label: '目标成本',
      value: summary.value.targetCost,
      desc: '来自目标成本版本，用于判断成本控制基准。',
      path: '/cost-target/index',
    },
    {
      key: 'contract',
      label: '合同锁定成本',
      value: summary.value.contractLockedCost,
      desc: '来自合同台账，核对已签约与锁定金额。',
      path: '/contract/ledger',
    },
    {
      key: 'actual',
      label: '实际成本',
      value: summary.value.actualCost,
      desc: '来自成本台账，核对已发生成本记录。',
      path: '/cost/ledger',
    },
    {
      key: 'paid',
      label: '已付款',
      value: summary.value.paidAmount,
      desc: '来自付款申请，核对资金支付进度。',
      path: '/payment/application',
    },
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
    title: '目标成本',
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
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head cost-summary-page-head">
      <div class="cost-summary-meta-row">
        <a-breadcrumb class="cost-summary-breadcrumb">
          <a-breadcrumb-item>成本管理</a-breadcrumb-item>
          <a-breadcrumb-item>项目成本明细核对</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="cost-summary-subtitle">
          按项目核对目标、合同锁定、实际、付款与动态成本来源
        </span>
      </div>
    </div>

    <div class="lg-search-bar cost-summary-search">
      <a-select
        v-model:value="selectedProjectId"
        placeholder="选择项目进行成本核对"
        allow-clear
        class="cost-summary-project-select"
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
      <a-button type="primary" :disabled="!selectedProjectId" @click="fetchSummary">查询</a-button>
      <a-button :disabled="!selectedProjectId" @click="handleRefresh" aria-label="重新计算动态成本">
        <template #icon><ReloadOutlined /></template>
        重算动态成本
      </a-button>
    </div>

    <div class="lg-grid">
      <div class="lg-left">
        <template v-if="summary">
          <section class="cost-reconcile-overview">
            <div class="cost-reconcile-project">
              <span class="cost-reconcile-project-label">当前项目</span>
              <strong>{{ summary.projectName || selectedProject?.projectName || '-' }}</strong>
              <span>科目维度核对 · 金额单位：万元</span>
            </div>
            <div class="cost-reconcile-badges">
              <a-tag color="blue">目标成本</a-tag>
              <a-tag color="cyan">合同锁定</a-tag>
              <a-tag color="green">实际成本</a-tag>
              <a-tag color="orange">付款进度</a-tag>
            </div>
          </section>

          <div class="lg-kpi-strip cost-reconcile-kpis">
            <div class="lg-kpi-card">
              <span class="lg-kpi-card-label">目标成本</span>
              <span class="lg-kpi-card-value"
                >{{ fmtAmount(summary.targetCost) }} <small>万元</small></span
              >
            </div>
            <div class="lg-kpi-card">
              <span class="lg-kpi-card-label">合同锁定成本</span>
              <span class="lg-kpi-card-value"
                >{{ fmtAmount(summary.contractLockedCost) }} <small>万元</small></span
              >
            </div>
            <div class="lg-kpi-card">
              <span class="lg-kpi-card-label">实际成本</span>
              <span class="lg-kpi-card-value"
                >{{ fmtAmount(summary.actualCost) }} <small>万元</small></span
              >
            </div>
            <div class="lg-kpi-card">
              <span class="lg-kpi-card-label">动态成本</span>
              <span class="lg-kpi-card-value"
                >{{ fmtAmount(summary.dynamicCost) }} <small>万元</small></span
              >
            </div>
            <div class="lg-kpi-card is-warn">
              <span class="lg-kpi-card-label">成本偏差</span>
              <span
                class="lg-kpi-card-value"
                :class="`is-${getDeviationTone(summary.costDeviation)}`"
              >
                {{ fmtDeviation(summary.costDeviation) }} <small>万元</small>
              </span>
            </div>
          </div>
        </template>

        <section class="lg-list-table-panel cost-summary-panel">
          <div class="lg-toolbar cost-toolbar">
            <div class="lg-toolbar-left">
              <strong>科目核对明细</strong>
              <span class="cost-toolbar-meta">
                {{ summary ? `共 ${summarySubjects.length} 个科目` : '选择项目后开始核对' }}
              </span>
            </div>
            <div class="lg-toolbar-right">
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
            </div>
          </div>

          <template v-if="summary">
            <div class="cost-source-grid">
              <button
                v-for="card in sourceCards"
                :key="card.key"
                type="button"
                class="cost-source-card"
                @click="go(card.path)"
              >
                <span class="cost-source-card-head">
                  <span>{{ card.label }}</span>
                  <LinkOutlined />
                </span>
                <strong>{{ fmtAmount(card.value) }} <small>万元</small></strong>
                <span>{{ card.desc }}</span>
              </button>
            </div>

            <div class="cost-reconcile-note">
              <FileSearchOutlined />
              <span>
                本页用于核对项目成本明细：先确认各来源金额是否归集完整，再按科目检查动态成本和目标成本偏差。
              </span>
            </div>

            <div class="lg-table-wrap cost-summary-table">
              <vxe-grid
                :data="summarySubjects"
                :columns="visibleGridColumns"
                :loading="loading"
                :column-config="{ resizable: true }"
                stripe
                border="inner"
                size="small"
                max-height="520"
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
      </div>

      <aside v-if="summary" class="lg-analysis-rail cost-reconcile-rail">
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
          <div class="lg-panel-title">核对口径</div>
          <ul class="cost-caliber-list">
            <li>目标成本：以当前目标成本版本为控制基准。</li>
            <li>合同锁定：以合同台账中的已签约金额归集。</li>
            <li>实际成本：以成本台账中的已发生记录归集。</li>
            <li>已付款：以付款申请和支付进度归集。</li>
          </ul>
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
      </aside>
    </div>
  </div>
</template>

<style scoped>
.cost-summary-breadcrumb {
  color: var(--muted);
  font-size: var(--font-size-sm);
  line-height: 20px;
}

.cost-summary-page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.cost-summary-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.cost-summary-subtitle {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 20px;
  white-space: nowrap;
}

.cost-summary-search {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-bottom: var(--spacing-md);
}

.cost-summary-project-select {
  width: 360px;
}

.cost-reconcile-overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: 14px 16px;
  margin-bottom: var(--spacing-md);
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.cost-reconcile-project {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.cost-reconcile-project strong {
  color: var(--text);
  font-size: var(--font-size-lg);
}

.cost-reconcile-project-label {
  color: var(--muted);
}

.cost-reconcile-badges {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.cost-reconcile-kpis {
  margin-bottom: var(--spacing-md);
}

.cost-summary-panel {
  overflow: hidden;
}

.cost-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}

.cost-toolbar-meta {
  margin-left: var(--spacing-xs);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 400;
}

.cost-source-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
}

.cost-source-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 128px;
  padding: 14px;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.cost-source-card:hover,
.cost-source-card:focus-visible {
  border-color: var(--primary);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
  outline: none;
}

.cost-source-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.cost-source-card strong {
  color: var(--text);
  font-size: 20px;
  line-height: 1.2;
}

.cost-source-card small {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.cost-reconcile-note {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin: 0 var(--spacing-sm) var(--spacing-sm);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  background: var(--surface-tint);
  border: 1px solid var(--primary-border-soft);
  border-radius: var(--radius-md);
}

.cost-summary-table {
  margin: var(--spacing-sm);
}

.cost-summary-deviation {
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

.cost-reconcile-rail {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
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

.cost-caliber-list {
  display: grid;
  gap: 8px;
  padding: var(--spacing-sm) 14px 14px 28px;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.cost-summary-muted-state {
  justify-content: center;
  color: var(--muted);
  text-align: center;
}

.cost-summary-muted-state :deep(.anticon) {
  color: var(--success);
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

@media (max-width: 1280px) {
  .cost-source-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cost-reconcile-overview {
    align-items: flex-start;
    flex-direction: column;
  }

  .cost-reconcile-badges {
    justify-content: flex-start;
  }
}

@media (max-width: 960px) {
  .cost-summary-search {
    flex-wrap: wrap;
  }

  .cost-summary-project-select {
    width: min(100%, 360px);
  }

  .cost-source-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
