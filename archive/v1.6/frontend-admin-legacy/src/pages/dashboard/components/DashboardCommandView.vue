<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import {
  AuditOutlined,
  CalendarOutlined,
  DollarOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  FundProjectionScreenOutlined,
  PayCircleOutlined,
  RightOutlined,
  ScheduleOutlined,
  SwapOutlined,
} from '@ant-design/icons-vue'
import type { Component } from 'vue'
import type { CostManagerDashboardVO } from '@/types/dashboard'
import { toNum } from '../utils/formatUtils'

const props = defineProps<{
  data: CostManagerDashboardVO
  loading: boolean
}>()

const router = useRouter()
const trendRange = ref<'year' | 'half' | 'quarter'>('year')
const riskFilter = ref<'all' | 'high' | 'medium' | 'low'>('all')

type RiskLevel = 'high' | 'medium' | 'low'

type RiskRow = {
  id: string
  level: RiskLevel
  title: string
  amount: string
  owner: string
  dueAt: string
  source: string
  route: string
}

type QuickAction = {
  label: string
  route: string
  icon: Component
}

const quickActions: QuickAction[] = [
  { label: '合同管理', route: '/contract/ledger', icon: FileDoneOutlined },
  { label: '变更签证', route: '/variation/order', icon: SwapOutlined },
  { label: '成本台账', route: '/cost/ledger', icon: AuditOutlined },
  { label: '结算管理', route: '/settlement/list', icon: FileSearchOutlined },
  { label: '收付款管理', route: '/finance-operations', icon: PayCircleOutlined },
  { label: '动态利润', route: '/cost/control', icon: DollarOutlined },
  { label: '进度计划', route: '/project-schedule', icon: ScheduleOutlined },
]

const projectName = computed(() => props.data.projectName || '当前项目')
const overBudgetAlerts = computed(() => props.data.overBudgetAlerts ?? [])
const overdueItems = computed(() => props.data.overdueItems ?? [])
const pendingPayments = computed(() => props.data.pendingPayments ?? [])
const ledgerRows = computed(() => props.data.ledgerRows ?? [])

function formatAmount(value: string | number | undefined, sign = false) {
  const numeric = toNum(value)
  const prefix = sign && numeric > 0 ? '+' : ''
  return `${prefix}${(numeric / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function formatDate(value?: string) {
  return value ? value.slice(0, 10) : '待确认'
}

function severityLevel(severity?: string): RiskLevel {
  if (severity === 'HIGH') return 'high'
  if (severity === 'LOW') return 'low'
  return 'medium'
}

const pendingPaymentAmount = computed(() =>
  pendingPayments.value.reduce((sum, item) => sum + toNum(item.payAmount), 0),
)

const healthScore = computed(() => {
  const target = Math.max(Math.abs(toNum(props.data.targetCost)), 1)
  const deviationRatio = Math.abs(toNum(props.data.costDeviation)) / target
  const penalty =
    overBudgetAlerts.value.filter((item) => item.severity === 'HIGH').length * 9 +
    overBudgetAlerts.value.filter((item) => item.severity !== 'HIGH').length * 5 +
    overdueItems.value.length * 4 +
    Math.min(24, Math.round(deviationRatio * 100))
  return Math.max(0, Math.min(100, 100 - penalty))
})

const healthTone = computed(() => {
  if (healthScore.value < 70) return { label: '风险偏高', status: 'exception' as const }
  if (healthScore.value < 85) return { label: '需要关注', status: 'normal' as const }
  return { label: '经营稳健', status: 'success' as const }
})

const highestRisk = computed(() => {
  const highAlert = overBudgetAlerts.value.find((item) => item.severity === 'HIGH')
  if (highAlert) {
    return {
      title: highAlert.message || '成本预算出现高风险偏差',
      detail: `${highAlert.projectName || projectName.value} · ${formatDate(highAlert.triggeredAt)}`,
    }
  }
  const overdue = overdueItems.value[0]
  if (overdue) {
    return {
      title: overdue.title || '关键事项已逾期',
      detail: `${overdue.ownerName || '责任人待确认'} · 已逾期 ${overdue.overdueDays || 0} 天`,
    }
  }
  return { title: '当前暂无高风险事项', detail: '经营指标处于可控范围，请持续关注趋势变化。' }
})

const metrics = computed(() => [
  {
    label: '动态利润（含税）',
    value: formatAmount(props.data.expectedProfit, true),
    unit: '万元',
    tone: toNum(props.data.expectedProfit) < 0 ? 'danger' : 'success',
    note: `利润率 ${
      toNum(props.data.contractIncome)
        ? ((toNum(props.data.expectedProfit) / toNum(props.data.contractIncome)) * 100).toFixed(2)
        : '0.00'
    }%`,
  },
  {
    label: '成本偏差（含税）',
    value: formatAmount(props.data.costDeviation, true),
    unit: '万元',
    tone: toNum(props.data.costDeviation) > 0 ? 'danger' : 'success',
    note: `动态成本 ${formatAmount(props.data.dynamicCost)} 万元`,
  },
  {
    label: '资金缺口（预测）',
    value: formatAmount(pendingPaymentAmount.value),
    unit: '万元',
    tone: pendingPaymentAmount.value > 0 ? 'warning' : 'default',
    note: `${pendingPayments.value.length} 笔待付款需关注`,
  },
  {
    label: '进度风险（里程碑）',
    value: String(overdueItems.value.length),
    unit: '项',
    tone: overdueItems.value.length ? 'danger' : 'success',
    note: '关键里程碑预警',
  },
])

const riskRows = computed<RiskRow[]>(() => {
  const alerts = overBudgetAlerts.value.map((item, index) => ({
    id: `alert-${index}`,
    level: severityLevel(item.severity),
    title: item.message || '成本预算预警',
    amount: '—',
    owner: '商务经理',
    dueAt: formatDate(item.triggeredAt),
    source: '成本偏差分析',
    route: '/cost/control',
  }))
  const overdue = overdueItems.value.map((item) => ({
    id: `overdue-${item.taskId}`,
    level: item.overdueDays >= 7 ? ('high' as const) : ('medium' as const),
    title: item.title || '逾期经营事项',
    amount: '—',
    owner: item.ownerName || '待确认',
    dueAt: formatDate(item.plannedAt),
    source: '进度计划',
    route: '/approval/todo',
  }))
  const payments = pendingPayments.value.map((item) => ({
    id: `payment-${item.payRecordId}`,
    level: toNum(item.payAmount) >= 1000000 ? ('medium' as const) : ('low' as const),
    title: `${item.partnerName || item.contractName || '合同'}待付款`,
    amount: `${formatAmount(item.payAmount)}（待支付）`,
    owner: '资金专员',
    dueAt: formatDate(item.payDate),
    source: '付款申请',
    route: '/finance-operations',
  }))
  return [...alerts, ...overdue, ...payments].slice(0, 7)
})

const filteredRiskRows = computed(() =>
  riskFilter.value === 'all'
    ? riskRows.value
    : riskRows.value.filter((item) => item.level === riskFilter.value),
)

const visibleTrendPoints = computed(() => {
  const points = props.data.trendPoints ?? []
  const length = trendRange.value === 'quarter' ? 3 : trendRange.value === 'half' ? 6 : 12
  return points.slice(-length)
})

const trendOption = computed(() => ({
  animationDuration: 450,
  color: ['#2563eb', '#0f9fa8', '#f97316'],
  tooltip: { trigger: 'axis', valueFormatter: (value: number) => `${value.toFixed(2)} 万元` },
  legend: { top: 0, left: 0, itemWidth: 18, itemHeight: 3, textStyle: { color: '#53627a' } },
  grid: { left: 44, right: 20, top: 42, bottom: 30 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: visibleTrendPoints.value.map((item) => item.month.slice(5) + '月'),
    axisLine: { lineStyle: { color: '#dbe3ee' } },
    axisTick: { show: false },
    axisLabel: { color: '#64748b' },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#edf1f6', type: 'dashed' } },
    axisLabel: { color: '#64748b' },
  },
  series: [
    {
      name: '目标成本（含税）',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      data: visibleTrendPoints.value.map((item) => toNum(item.targetCost) / 10000),
    },
    {
      name: '动态成本（含税）',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      data: visibleTrendPoints.value.map((item) => toNum(item.dynamicCost) / 10000),
    },
    {
      name: '成本偏差',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      data: visibleTrendPoints.value.map((item) => toNum(item.costDeviation) / 10000),
    },
  ],
}))

const trendSummary = computed(() => [
  { label: '合同收入（含税）', value: formatAmount(props.data.contractIncome), tone: 'default' },
  { label: '成本目标（含税）', value: formatAmount(props.data.targetCost), tone: 'blue' },
  { label: '动态成本（含税）', value: formatAmount(props.data.dynamicCost), tone: 'teal' },
  {
    label: '预计利润（含税）',
    value: formatAmount(props.data.expectedProfit, true),
    tone: toNum(props.data.expectedProfit) < 0 ? 'danger' : 'success',
  },
])

const recentRecords = computed(() =>
  ledgerRows.value.slice(0, 3).map((item) => ({
    title: item.contractName || item.costSubjectName || '经营记录',
    code: item.contractCode || item.sourceId || item.costSubjectId,
    meta: item.ownerName || '最近更新',
    route: item.rowType === 'contract' ? '/contract/ledger' : '/cost/ledger',
  })),
)

function openRisk(row?: RiskRow) {
  router.push(row?.route || '/alert')
}

function openRoute(route: string) {
  router.push(route)
}
</script>

<template>
  <div class="command-view" :aria-busy="loading">
    <section class="command-panel health-panel">
      <header class="command-panel__title">项目经营健康度</header>
      <div class="health-content">
        <div class="health-score">
          <a-progress
            type="circle"
            :percent="healthScore"
            :size="142"
            :stroke-width="7"
            :status="healthTone.status"
            stroke-color="#e8463a"
          >
            <template #format>
              <strong>{{ healthScore }}</strong
              ><small>/100</small>
              <span>{{ healthTone.label }}</span>
            </template>
          </a-progress>
          <p>综合成本、资金、履约与待办风险</p>
        </div>

        <div class="highest-risk">
          <span class="highest-risk__tag">最高风险</span>
          <h2>{{ highestRisk.title }}</h2>
          <p>{{ highestRisk.detail }}</p>
          <a-button type="primary" ghost @click="openRisk(riskRows[0])">
            查看并处理最高风险 <RightOutlined />
          </a-button>
        </div>

        <div class="health-metrics">
          <article v-for="metric in metrics" :key="metric.label" class="health-metric">
            <span>{{ metric.label }}</span>
            <strong :class="`is-${metric.tone}`"
              >{{ metric.value }} <small>{{ metric.unit }}</small></strong
            >
            <p>{{ metric.note }}</p>
          </article>
        </div>
      </div>
    </section>

    <div class="command-grid">
      <section class="command-panel trend-panel">
        <header class="panel-toolbar">
          <div><strong>经营趋势</strong><span>（万元）</span></div>
          <a-segmented
            v-model:value="trendRange"
            :options="[
              { label: '当年累计', value: 'year' },
              { label: '近6个月', value: 'half' },
              { label: '近3个月', value: 'quarter' },
            ]"
            size="small"
          />
        </header>
        <v-chart :option="trendOption" autoresize class="trend-chart" />
        <div class="trend-summary">
          <article v-for="item in trendSummary" :key="item.label">
            <span>{{ item.label }}</span>
            <strong :class="`is-${item.tone}`">{{ item.value }}</strong>
            <small>万元</small>
          </article>
        </div>
      </section>

      <section class="command-panel risk-panel">
        <header class="panel-toolbar">
          <strong>经营预警与待办（{{ riskRows.length }}）</strong>
          <div class="risk-toolbar">
            <a-select v-model:value="riskFilter" size="small" style="width: 92px">
              <a-select-option value="all">全部预警</a-select-option>
              <a-select-option value="high">高风险</a-select-option>
              <a-select-option value="medium">中风险</a-select-option>
              <a-select-option value="low">低风险</a-select-option>
            </a-select>
            <button type="button" @click="openRoute('/alert')">更多 <RightOutlined /></button>
          </div>
        </header>
        <div class="risk-table-wrap">
          <table class="risk-table">
            <thead>
              <tr>
                <th>优先级</th>
                <th>预警事项</th>
                <th>金额（万元）</th>
                <th>责任人</th>
                <th>要求完成时间</th>
                <th>来源</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in filteredRiskRows" :key="row.id" @click="openRisk(row)">
                <td>
                  <span class="risk-level" :class="`is-${row.level}`">{{
                    row.level === 'high' ? '高' : row.level === 'medium' ? '中' : '低'
                  }}</span>
                </td>
                <td>
                  <strong>{{ row.title }}</strong>
                </td>
                <td>{{ row.amount }}</td>
                <td>{{ row.owner }}</td>
                <td>{{ row.dueAt }}</td>
                <td>{{ row.source }}</td>
              </tr>
              <tr v-if="filteredRiskRows.length === 0" class="empty-row">
                <td colspan="6">当前筛选条件下暂无预警与待办</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <section class="command-panel utility-panel">
      <div class="quick-entry">
        <strong>快捷入口</strong>
        <div class="quick-actions">
          <button
            v-for="action in quickActions"
            :key="action.label"
            type="button"
            @click="openRoute(action.route)"
          >
            <component :is="action.icon" />
            <span>{{ action.label }}</span>
          </button>
        </div>
      </div>
      <div class="recent-entry">
        <strong>最近打开</strong>
        <div class="recent-list">
          <button
            v-for="record in recentRecords"
            :key="record.code"
            type="button"
            @click="openRoute(record.route)"
          >
            <CalendarOutlined />
            <span
              ><b>{{ record.title }}</b
              ><small>{{ record.code }} · {{ record.meta }}</small></span
            >
          </button>
          <button
            v-if="recentRecords.length === 0"
            type="button"
            @click="openRoute('/dashboard/reports')"
          >
            <FundProjectionScreenOutlined />
            <span><b>报表目录</b><small>查看经营分析报表</small></span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.command-view {
  display: grid;
  gap: 12px;
  color: #172033;
}

.command-panel {
  min-width: 0;
  background: #fff;
  border: 1px solid #e1e8f2;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgb(31 52 80 / 3%);
}

.command-panel__title,
.panel-toolbar {
  min-height: 48px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #edf1f6;
  font-size: 15px;
  font-weight: 700;
}

.health-content {
  min-height: 222px;
  display: grid;
  grid-template-columns: 205px minmax(230px, 1fr) minmax(520px, 1.8fr);
  align-items: center;
}

.health-score {
  display: grid;
  place-items: center;
  padding: 20px 16px;
}

.health-score :deep(.ant-progress-text) {
  display: grid;
  grid-template-columns: auto auto;
  justify-content: center;
  align-items: baseline;
}

.health-score :deep(.ant-progress-text strong) {
  color: #d71920;
  font-size: 36px;
  line-height: 40px;
}

.health-score :deep(.ant-progress-text small) {
  color: #64748b;
  font-size: 12px;
}

.health-score :deep(.ant-progress-text span) {
  grid-column: 1 / -1;
  width: max-content;
  margin: 5px auto 0;
  padding: 2px 7px;
  color: #d71920;
  background: #fff1f0;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
}

.health-score p {
  margin: 10px 0 0;
  color: #7a879a;
  font-size: 11px;
  text-align: center;
}

.highest-risk {
  min-width: 0;
  padding: 20px 28px;
  border-left: 1px solid #e8edf4;
  border-right: 1px solid #e8edf4;
}

.highest-risk__tag {
  display: inline-flex;
  padding: 3px 8px;
  color: #d71920;
  background: #fff1f0;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
}

.highest-risk h2 {
  margin: 12px 0 6px;
  overflow: hidden;
  color: #121b2e;
  font-size: 17px;
  font-weight: 750;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.highest-risk p {
  min-height: 38px;
  margin: 0 0 15px;
  color: #6b778b;
  font-size: 12px;
  line-height: 19px;
}

.health-metrics {
  align-self: stretch;
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
}

.health-metric {
  min-width: 0;
  padding: 45px 20px 24px;
  border-right: 1px solid #e8edf4;
}

.health-metric:last-child {
  border-right: 0;
}

.health-metric > span {
  color: #53627a;
  font-size: 12px;
}

.health-metric strong {
  display: block;
  margin: 19px 0 16px;
  color: #172033;
  font-size: 21px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.health-metric strong small {
  margin-left: 3px;
  color: #53627a;
  font-size: 11px;
  font-weight: 600;
}

.health-metric p {
  margin: 0;
  color: #6b778b;
  font-size: 11px;
  line-height: 18px;
}

.is-danger {
  color: #d71920 !important;
}
.is-warning {
  color: #f97316 !important;
}
.is-success {
  color: #0f9f6e !important;
}
.is-blue {
  color: #2563eb !important;
}
.is-teal {
  color: #0f9fa8 !important;
}

.command-grid {
  display: grid;
  grid-template-columns: minmax(430px, 0.92fr) minmax(550px, 1.18fr);
  gap: 12px;
}

.panel-toolbar span {
  color: #64748b;
  font-size: 11px;
  font-weight: 500;
}

.trend-chart {
  width: 100%;
  height: 292px;
  padding: 4px 10px 0;
}

.trend-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0 16px 16px;
  border-top: 1px solid #edf1f6;
}

.trend-summary article {
  min-width: 0;
  padding: 18px 14px 4px;
  border-right: 1px solid #edf1f6;
}

.trend-summary article:last-child {
  border-right: 0;
}
.trend-summary span {
  display: block;
  color: #53627a;
  font-size: 11px;
}
.trend-summary strong {
  display: inline-block;
  margin-top: 9px;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}
.trend-summary small {
  margin-left: 3px;
  color: #64748b;
  font-size: 10px;
}

.risk-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.risk-toolbar button {
  padding: 0;
  color: #2563eb;
  background: none;
  border: 0;
  font-size: 12px;
  cursor: pointer;
}

.risk-table-wrap {
  overflow: auto;
}
.risk-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}
.risk-table th {
  height: 36px;
  padding: 0 10px;
  color: #53627a;
  background: #fafbfd;
  text-align: left;
  white-space: nowrap;
}
.risk-table td {
  height: 43px;
  padding: 0 10px;
  color: #4b5b73;
  border-top: 1px solid #edf1f6;
  white-space: nowrap;
}
.risk-table tbody tr:not(.empty-row) {
  cursor: pointer;
}
.risk-table tbody tr:not(.empty-row):hover {
  background: #f7faff;
}
.risk-table td:nth-child(2) {
  max-width: 230px;
}
.risk-table td:nth-child(2) strong {
  display: block;
  overflow: hidden;
  color: #263246;
  font-weight: 600;
  text-overflow: ellipsis;
}
.risk-level {
  display: inline-flex;
  width: 25px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: 1px solid;
  border-radius: 3px;
  font-weight: 700;
}
.risk-level.is-high {
  color: #d71920;
  background: #fff5f5;
  border-color: #ffb8b5;
}
.risk-level.is-medium {
  color: #d97706;
  background: #fff9ed;
  border-color: #ffd698;
}
.risk-level.is-low {
  color: #64748b;
  background: #f8fafc;
  border-color: #d9e2ec;
}
.empty-row td {
  height: 210px;
  color: #94a3b8;
  text-align: center;
}

.utility-panel {
  min-height: 104px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 16px 14px;
}

.quick-entry {
  padding-right: 24px;
  border-right: 1px solid #e8edf4;
}
.recent-entry {
  padding-left: 28px;
}
.quick-entry > strong,
.recent-entry > strong {
  display: block;
  margin-bottom: 12px;
  font-size: 13px;
}
.quick-actions {
  display: grid;
  grid-template-columns: repeat(7, minmax(58px, 1fr));
  gap: 8px;
}
.quick-actions button,
.recent-list button {
  color: #3d4b61;
  background: transparent;
  border: 0;
  cursor: pointer;
}
.quick-actions button {
  display: grid;
  justify-items: center;
  gap: 7px;
  padding: 5px;
  font-size: 11px;
}
.quick-actions button > :first-child {
  color: #53627a;
  font-size: 22px;
}
.quick-actions button:hover {
  color: #2563eb;
}
.quick-actions button:hover > :first-child {
  color: #2563eb;
}
.recent-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.recent-list button {
  min-width: 0;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 8px;
  padding: 4px 0;
  text-align: left;
}
.recent-list button > :first-child {
  margin-top: 2px;
  color: #64748b;
}
.recent-list span {
  min-width: 0;
}
.recent-list b,
.recent-list small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-list b {
  font-size: 11px;
  font-weight: 600;
}
.recent-list small {
  margin-top: 5px;
  color: #7a879a;
  font-size: 10px;
}

@media (max-width: 1260px) {
  .health-content {
    grid-template-columns: 190px minmax(230px, 1fr);
  }
  .health-metrics {
    grid-column: 1 / -1;
    border-top: 1px solid #e8edf4;
  }
  .health-metric {
    padding: 24px 20px;
  }
  .command-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .health-content {
    grid-template-columns: 1fr;
  }
  .highest-risk {
    border: 0;
    border-top: 1px solid #e8edf4;
  }
  .health-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .trend-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .utility-panel {
    grid-template-columns: 1fr;
  }
  .quick-entry {
    padding-right: 0;
    padding-bottom: 18px;
    border-right: 0;
    border-bottom: 1px solid #e8edf4;
  }
  .recent-entry {
    padding: 18px 0 0;
  }
  .quick-actions {
    grid-template-columns: repeat(4, minmax(58px, 1fr));
  }
  .recent-list {
    grid-template-columns: 1fr;
  }
}
</style>
