<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, LineChartOutlined } from '@ant-design/icons-vue'
import type { CallbackDataParams } from 'echarts'
import VChart from 'vue-echarts'
import { getCostSummary, refreshCostSummary } from '@/api/modules/cost'
import { getProjectList } from '@/api/modules/project'
import type { SelectOption } from '@/types/ui'
import type { CostSummaryVO } from '@/types/cost'
import type { ProjectVO } from '@/types/project'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'
import { chartPalette } from '@/theme/tokens'

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
    message.error('加载动态成本汇总失败')
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
    message.success('刷新成功')
  } catch (e: unknown) {
    console.error(e)
    message.error('刷新失败')
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
  if (!val) return '0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtDeviation(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function getDeviationColor(val: string | undefined): string {
  if (!val) return chartPalette.semantic.neutral
  const n = parseFloat(val)
  if (n > 0) return chartPalette.semantic.negative
  if (n < 0) return chartPalette.semantic.positive
  return chartPalette.semantic.neutral
}

function fmtPercent(val: string | undefined, base: string | undefined): string {
  if (!val || !base) return '0.0%'
  const v = parseFloat(val)
  const b = parseFloat(base)
  if (isNaN(v) || isNaN(b) || b === 0) return '0.0%'
  return ((v / b) * 100).toFixed(1) + '%'
}

// ---- VxeGrid columns ----
const gridColumns = computed(() => [
  { field: 'costSubjectName', title: '成本科目', minWidth: 140, ellipsis: true },
  {
    field: 'targetCost',
    title: '目标成本(万元)',
    width: 130,
    align: 'right' as const,
    slots: { default: 'targetCost' },
  },
  {
    field: 'contractLockedCost',
    title: '合同锁定成本(万元)',
    width: 150,
    align: 'right' as const,
    slots: { default: 'contractLockedCost' },
  },
  {
    field: 'actualCost',
    title: '实际成本(万元)',
    width: 130,
    align: 'right' as const,
    slots: { default: 'actualCost' },
  },
  {
    field: 'paidAmount',
    title: '已付款(万元)',
    width: 120,
    align: 'right' as const,
    slots: { default: 'paidAmount' },
  },
  {
    field: 'dynamicCost',
    title: '动态成本(万元)',
    width: 130,
    align: 'right' as const,
    slots: { default: 'dynamicCost' },
  },
  {
    field: 'costDeviation',
    title: '成本偏差(万元)',
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
} = useColumnSettings('cost_summary_cols', gridColumns)

const summarySubjects = computed(() =>
  summary.value ? normalizeArray<CostSummaryVO['subjects'][number]>(summary.value.subjects) : [],
)

// ---- Chart options ----
const executionOption = computed(() => {
  if (!summary.value) return {}
  const s = summary.value
  return {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 10, right: 20, top: 20, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category' as const,
      data: ['目标成本', '锁定成本', '实际成本', '已付款', '动态成本'],
      axisLabel: { fontSize: 12 },
    },
    yAxis: {
      type: 'value' as const,
      axisLabel: { fontSize: 11, formatter: '{value} 万' },
    },
    series: [
      {
        type: 'bar',
        data: [
          parseFloat(s.targetCost) / 10000,
          parseFloat(s.contractLockedCost) / 10000,
          parseFloat(s.actualCost) / 10000,
          parseFloat(s.paidAmount) / 10000,
          parseFloat(s.dynamicCost) / 10000,
        ],
        itemStyle: {
          borderRadius: [4, 4, 0, 0],

          color: (params: CallbackDataParams) => {
            const colors = chartPalette.categorical
            return colors[params.dataIndex ?? 0] ?? chartPalette.categorical[0]
          },
        },
        barMaxWidth: 32,
      },
    ],
  }
})

const compositionOption = computed(() => {
  if (!summary.value || !summarySubjects.value.length) return {}
  const data = summarySubjects.value
    .filter((s) => parseFloat(s.dynamicCost) > 0)
    .map((s) => ({
      name: s.costSubjectName,
      value: parseFloat(s.dynamicCost) / 10000,
    }))
  return {
    tooltip: { trigger: 'item' as const, formatter: '{b}: {c} 万元 ({d}%)' },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '55%'],
        data,
        label: { fontSize: 11 },
        emphasis: { itemStyle: { shadowBlur: 8, shadowColor: chartPalette.shadow.emphasis } },
      },
    ],
  }
})

const deviationOption = computed(() => {
  if (!summary.value || !summarySubjects.value.length) return {}
  const subjects = summarySubjects.value
  return {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 10, right: 20, top: 10, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category' as const,
      data: subjects.map((s) => s.costSubjectName),
      axisLabel: { fontSize: 10, rotate: 25 },
    },
    yAxis: {
      type: 'value' as const,
      axisLabel: { fontSize: 11, formatter: '{value} 万' },
    },
    series: [
      {
        type: 'bar',
        data: subjects.map((s) => parseFloat(s.costDeviation) / 10000),
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: (params: CallbackDataParams) =>
            ((params.value as number) ?? 0) > 0
              ? chartPalette.semantic.negative
              : chartPalette.semantic.positive,
        },
        barMaxWidth: 24,
      },
    ],
  }
})

const rankingOption = computed(() => {
  if (!summary.value || !summarySubjects.value.length) return {}
  const subjects = [...summarySubjects.value]
    .sort((a, b) => parseFloat(b.dynamicCost) - parseFloat(a.dynamicCost))
    .slice(0, 8)
  return {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 10, right: 30, top: 10, bottom: 10, containLabel: true },
    xAxis: {
      type: 'value' as const,
      axisLabel: { fontSize: 11, formatter: '{value} 万' },
    },
    yAxis: {
      type: 'category' as const,
      data: subjects.map((s) => s.costSubjectName).reverse(),
      axisLabel: { fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        data: subjects.map((s) => parseFloat(s.dynamicCost) / 10000).reverse(),
        itemStyle: {
          borderRadius: [0, 3, 3, 0],
          color: (params: CallbackDataParams) => {
            const colors = chartPalette.extended
            return colors[(params.dataIndex ?? 0) % colors.length]
          },
        },
        barMaxWidth: 18,
      },
    ],
  }
})

const overBudgetItems = computed(() => {
  if (!summary.value) return []
  return summarySubjects.value.filter((s) => parseFloat(s.costDeviation) > 0)
})

const anomalyItems = computed(() => {
  if (!summary.value) return []
  return summarySubjects.value
    .filter((s) => {
      const dev = parseFloat(s.costDeviation)
      const target = parseFloat(s.targetCost)
      return dev > 0 && target > 0 && dev / target > 0.1
    })
    .sort((a, b) => parseFloat(b.costDeviation) - parseFloat(a.costDeviation))
})

// ---- Right rail: subject composition distribution ----
const subjectDistribution = computed(() => {
  if (!summary.value || !summarySubjects.value.length) return []
  const subjects = summarySubjects.value.filter((s) => parseFloat(s.dynamicCost) > 0)
  const total = subjects.reduce((acc, s) => acc + parseFloat(s.dynamicCost), 0) || 1
  const colors = chartPalette.extended
  return subjects.slice(0, 8).map((s, i) => ({
    key: s.costSubjectId,
    label: s.costSubjectName,
    value: parseFloat(s.dynamicCost),
    color: colors[i % colors.length],
    percent: Math.round((parseFloat(s.dynamicCost) / total) * 100),
  }))
})

const maxDistValue = computed(() => Math.max(...subjectDistribution.value.map((d) => d.value), 1))
function distBarPct(value: number): number {
  return Math.min(Math.round((value / maxDistValue.value) * 100), 100)
}

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
          <a-breadcrumb-item>动态成本汇总</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="cost-summary-subtitle">
          按项目查看目标成本、锁定成本、实际成本与偏差风险
        </span>
      </div>
    </div>

    <div class="lg-search-bar cost-summary-search">
      <a-select
        v-model:value="selectedProjectId"
        placeholder="请选择项目"
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
      <a-button :disabled="!selectedProjectId" @click="handleRefresh" aria-label="刷新动态成本汇总">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <div class="lg-grid">
      <div class="lg-left">
        <div v-if="summary" class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">目标成本</span>
            <span class="lg-kpi-card-value"
              >{{ fmtAmount(summary.targetCost) }} <small>万元</small></span
            >
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">锁定成本</span>
            <span class="lg-kpi-card-value"
              >{{ fmtAmount(summary.contractLockedCost) }} <small>万元</small></span
            >
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">动态成本</span>
            <span class="lg-kpi-card-value"
              >{{ fmtAmount(summary.dynamicCost) }} <small>万元</small></span
            >
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">偏差金额</span>
            <span
              class="lg-kpi-card-value"
              :style="{ color: getDeviationColor(summary.costDeviation) }"
            >
              {{ fmtDeviation(summary.costDeviation) }} <small>万元</small>
            </span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">偏差率</span>
            <span
              class="lg-kpi-card-value"
              :style="{ color: getDeviationColor(summary.costDeviation) }"
            >
              {{ fmtPercent(summary.costDeviation, summary.targetCost) }}
            </span>
          </div>
        </div>

        <section class="lg-list-table-panel cost-summary-panel">
          <div class="lg-toolbar cost-toolbar">
            <div class="lg-toolbar-left">
              <strong>成本明细</strong>
              <span class="cost-toolbar-meta">
                {{ summary ? `共 ${summarySubjects.length} 个科目` : '选择项目后查看科目明细' }}
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
            <div class="cost-chart-grid">
              <section class="lg-panel">
                <div class="lg-panel-title">成本执行概览</div>
                <div class="cost-chart-box">
                  <v-chart :option="executionOption" autoresize class="cost-chart" />
                </div>
              </section>
              <section class="lg-panel">
                <div class="lg-panel-title">成本构成分析</div>
                <div class="cost-chart-box">
                  <v-chart :option="compositionOption" autoresize class="cost-chart" />
                </div>
              </section>
            </div>

            <div class="cost-chart-grid">
              <section class="lg-panel">
                <div class="lg-panel-title">偏差趋势分析</div>
                <div class="cost-chart-box">
                  <v-chart :option="deviationOption" autoresize class="cost-chart" />
                </div>
              </section>
              <section class="lg-panel">
                <div class="lg-panel-title">超预算预警</div>
                <div class="cost-summary-list-body">
                  <div v-if="overBudgetItems.length" class="lg-type-list">
                    <div
                      v-for="item in overBudgetItems.slice(0, 6)"
                      :key="item.costSubjectId"
                      class="lg-type-row cost-summary-risk-row"
                    >
                      <span class="cost-summary-row-label">{{ item.costSubjectName }}</span>
                      <span class="cost-summary-risk-value"
                        >+{{ fmtDeviation(item.costDeviation) }} 万</span
                      >
                    </div>
                  </div>
                  <div v-else class="cost-summary-muted-state">无超预算科目</div>
                </div>
              </section>
            </div>

            <div class="cost-chart-grid">
              <section class="lg-panel">
                <div class="lg-panel-title">成本科目排行</div>
                <div class="cost-chart-box">
                  <v-chart :option="rankingOption" autoresize class="cost-chart" />
                </div>
              </section>
              <section class="lg-panel">
                <div class="lg-panel-title">异常明细</div>
                <div class="cost-summary-list-body">
                  <div v-if="anomalyItems.length" class="lg-type-list">
                    <div
                      v-for="item in anomalyItems.slice(0, 5)"
                      :key="'anomaly-' + item.costSubjectId"
                      class="lg-type-row cost-summary-risk-row"
                    >
                      <span class="cost-summary-row-label">{{ item.costSubjectName }}</span>
                      <span class="cost-summary-risk-value"
                        >偏差 +{{ fmtDeviation(item.costDeviation) }} 万</span
                      >
                    </div>
                  </div>
                  <div v-else class="cost-summary-muted-state">无异常科目</div>
                </div>
              </section>
            </div>

            <div class="lg-table-wrap cost-summary-table">
              <div class="cost-summary-table-title">科目明细</div>
              <vxe-grid
                :data="summarySubjects"
                :columns="visibleGridColumns"
                :loading="loading"
                :column-config="{ resizable: true }"
                stripe
                border="inner"
                size="small"
                max-height="420"
              >
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
                  <span class="cost-summary-deviation" :style="{ color: getDeviationColor(row.costDeviation) }">
                    {{ fmtDeviation(row.costDeviation) }}
                  </span>
                </template>
              </vxe-grid>
            </div>
          </template>

          <template v-else>
            <section class="cost-summary-empty">
              <LineChartOutlined class="cost-summary-empty-icon" />
              <div class="cost-summary-empty-title">请选择项目</div>
              <div class="cost-summary-empty-text">
                选择项目后查看动态成本、成本偏差和科目排行。
              </div>
            </section>
          </template>
        </section>
      </div>

      <aside v-if="summary" class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">科目动态成本分布</div>
          <div class="lg-type-list">
            <div v-for="item in subjectDistribution" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: distBarPct(item.value) + '%', background: item.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ fmtAmount(String(item.value)) }}</span>
              <span class="lg-type-pct">{{ item.percent }}%</span>
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
  width: 320px;
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

.cost-chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--spacing-sm);
  margin: var(--spacing-sm);
}

.cost-chart-box {
  padding: 0 4px 4px;
}

.cost-chart {
  width: 100%;
  height: 260px;
}

.cost-summary-list-body {
  padding: var(--spacing-sm) 14px;
}

.cost-summary-risk-row {
  grid-template-columns: 1fr 96px;
}

.cost-summary-row-label {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.cost-summary-risk-value {
  color: var(--error);
  font-size: var(--font-size-sm);
  font-weight: 700;
  text-align: right;
}

.cost-summary-muted-state {
  padding: var(--spacing-sm) 0;
  color: var(--muted);
  font-size: var(--font-size-sm);
  text-align: center;
}

.cost-summary-table {
  margin: var(--spacing-sm);
}

.cost-summary-table-title {
  padding: var(--spacing-sm) 14px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.cost-summary-deviation {
  font-weight: 600;
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
  .cost-chart-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .cost-summary-search {
    flex-wrap: wrap;
  }

  .cost-summary-project-select {
    width: min(100%, 360px);
  }
}
</style>
