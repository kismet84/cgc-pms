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

const projectList = ref<ProjectVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const loading = ref(false)
const summary = ref<CostSummaryVO | null>(null)

async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 50 })
    projectList.value = res.records
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
  if (!val) return '#6b7280'
  const n = parseFloat(val)
  if (n > 0) return '#ef4444'
  if (n < 0) return '#22c55e'
  return '#6b7280'
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
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          color: (params: CallbackDataParams) => {
            const colors = ['#3b82f6', '#8b5cf6', '#f59e0b', '#22c55e', '#ef4444']
            return colors[params.dataIndex ?? 0] ?? '#3b82f6'
          },
        },
        barMaxWidth: 32,
      },
    ],
  }
})

const compositionOption = computed(() => {
  if (!summary.value || !summary.value.subjects.length) return {}
  const data = summary.value.subjects
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
        emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.15)' } },
      },
    ],
  }
})

const deviationOption = computed(() => {
  if (!summary.value || !summary.value.subjects.length) return {}
  const subjects = summary.value.subjects
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
          color: (params: CallbackDataParams) => ((params.value as number ?? 0) > 0 ? '#ef4444' : '#22c55e'),
        },
        barMaxWidth: 24,
      },
    ],
  }
})

const rankingOption = computed(() => {
  if (!summary.value || !summary.value.subjects.length) return {}
  const subjects = [...summary.value.subjects]
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
            const colors = ['#3b82f6', '#6366f1', '#8b5cf6', '#a855f7', '#d946ef']
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
  return summary.value.subjects.filter((s) => parseFloat(s.costDeviation) > 0)
})

const anomalyItems = computed(() => {
  if (!summary.value) return []
  return summary.value.subjects
    .filter((s) => {
      const dev = parseFloat(s.costDeviation)
      const target = parseFloat(s.targetCost)
      return dev > 0 && target > 0 && dev / target > 0.1
    })
    .sort((a, b) => parseFloat(b.costDeviation) - parseFloat(a.costDeviation))
})

// ---- Right rail: subject composition distribution ----
const subjectDistribution = computed(() => {
  if (!summary.value || !summary.value.subjects.length) return []
  const subjects = summary.value.subjects.filter((s) => parseFloat(s.dynamicCost) > 0)
  const total = subjects.reduce((acc, s) => acc + parseFloat(s.dynamicCost), 0) || 1
  const colors = [
    '#3b82f6',
    '#8b5cf6',
    '#f59e0b',
    '#22c55e',
    '#ef4444',
    '#6366f1',
    '#a855f7',
    '#d946ef',
  ]
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
  <div class="lg-page app-page">
    <!-- Page head -->
    <div class="lg-page-head">
      <a-breadcrumb style="font-size: 13px; color: var(--muted); margin-bottom: 5px">
        <a-breadcrumb-item>成本管理</a-breadcrumb-item>
        <a-breadcrumb-item>动态成本汇总</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-grid">
      <!-- 左列 -->
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div v-if="summary" class="lg-kpi-strip" style="grid-template-columns: repeat(5, 1fr)">
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

        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button type="primary" @click="handleRefresh" :disabled="!selectedProjectId">
              <template #icon><ReloadOutlined /></template>
              刷新
            </a-button>
          </div>
          <div class="lg-toolbar-right">
            <a-select
              v-model:value="selectedProjectId"
              placeholder="请选择项目"
              allow-clear
              style="width: 200px"
              size="small"
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

        <template v-if="summary">
          <!-- Row 1: 成本执行概览 + 成本构成分析 -->
          <div
            style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px"
          >
            <section class="lg-panel">
              <div class="lg-panel-title">成本执行概览</div>
              <div style="padding: 0 4px 4px">
                <v-chart :option="executionOption" autoresize style="width: 100%; height: 260px" />
              </div>
            </section>
            <section class="lg-panel">
              <div class="lg-panel-title">成本构成分析</div>
              <div style="padding: 0 4px 4px">
                <v-chart
                  :option="compositionOption"
                  autoresize
                  style="width: 100%; height: 260px"
                />
              </div>
            </section>
          </div>

          <!-- Row 2: 偏差趋势分析 + 超预算预警 -->
          <div
            style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px"
          >
            <section class="lg-panel">
              <div class="lg-panel-title">偏差趋势分析</div>
              <div style="padding: 0 4px 4px">
                <v-chart :option="deviationOption" autoresize style="width: 100%; height: 260px" />
              </div>
            </section>
            <section class="lg-panel">
              <div class="lg-panel-title">超预算预警</div>
              <div style="padding: 12px 14px">
                <div v-if="overBudgetItems.length" class="lg-type-list">
                  <div
                    v-for="item in overBudgetItems.slice(0, 6)"
                    :key="item.costSubjectId"
                    class="lg-type-row"
                    style="grid-template-columns: 1fr 80px"
                  >
                    <span style="font-size: 13px; color: var(--text-secondary)">{{
                      item.costSubjectName
                    }}</span>
                    <span
                      style="text-align: right; font-weight: 700; color: #ef4444; font-size: 13px"
                      >+{{ fmtDeviation(item.costDeviation) }} 万</span
                    >
                  </div>
                </div>
                <div
                  v-else
                  style="font-size: 13px; color: var(--muted); padding: 12px 0; text-align: center"
                >
                  无超预算科目
                </div>
              </div>
            </section>
          </div>

          <!-- Row 3: 成本科目排行 + 异常明细 -->
          <div
            style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px"
          >
            <section class="lg-panel">
              <div class="lg-panel-title">成本科目排行</div>
              <div style="padding: 0 4px 4px">
                <v-chart :option="rankingOption" autoresize style="width: 100%; height: 260px" />
              </div>
            </section>
            <section class="lg-panel">
              <div class="lg-panel-title">异常明细</div>
              <div style="padding: 12px 14px">
                <div v-if="anomalyItems.length" class="lg-type-list">
                  <div
                    v-for="item in anomalyItems.slice(0, 5)"
                    :key="'anomaly-' + item.costSubjectId"
                    class="lg-type-row"
                    style="grid-template-columns: 1fr 80px"
                  >
                    <span style="font-size: 13px; color: var(--text-secondary)">{{
                      item.costSubjectName
                    }}</span>
                    <span
                      style="text-align: right; font-weight: 700; color: #ef4444; font-size: 13px"
                      >偏差 +{{ fmtDeviation(item.costDeviation) }} 万</span
                    >
                  </div>
                </div>
                <div
                  v-else
                  style="font-size: 13px; color: var(--muted); padding: 12px 0; text-align: center"
                >
                  无异常科目
                </div>
              </div>
            </section>
          </div>

          <!-- Subject detail table -->
          <div class="lg-table-wrap" style="margin-bottom: 0">
            <div
              style="
                padding: 12px 14px;
                border-bottom: 1px solid var(--border-subtle);
                color: var(--text);
                font-size: 15px;
                font-weight: 700;
              "
            >
              科目明细
            </div>
            <vxe-grid
              :data="summary.subjects"
              :columns="gridColumns"
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
                <span :style="{ color: getDeviationColor(row.costDeviation), fontWeight: 600 }">
                  {{ fmtDeviation(row.costDeviation) }}
                </span>
              </template>
            </vxe-grid>
          </div>
        </template>

        <template v-else>
          <div
            style="
              text-align: center;
              padding: 80px 0;
              color: #9ca3af;
              font-size: 14px;
              background: var(--surface);
              border: 1px solid var(--border);
              border-radius: var(--radius-md);
            "
          >
            <LineChartOutlined
              style="font-size: 48px; margin-bottom: 16px; display: block; color: #d1d5db"
            />
            请选择一个项目，查看动态成本汇总分析
          </div>
        </template>
      </div>

      <!-- 右侧分析面板 -->
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
