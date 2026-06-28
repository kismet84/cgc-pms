<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { InfoCircleOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type {
  CostManagerDashboardVO,
  CostBreakdownVO,
  CostManagerSubjectRanking,
  CostManagerLedgerRow,
} from '@/types/dashboard'
import { CONTRACT_TYPE_MAP } from '@/types/dashboard'
import { devSign, toNum } from '../utils/formatUtils'

const props = defineProps<{
  data: CostManagerDashboardVO
  breakdown: CostBreakdownVO | null
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'barClick', params: { name?: string }): void
}>()

const router = useRouter()

type SubjectRankRow = {
  rank: number
  name: string
  amount: number
  ratio: number
  sourceName?: string
}

type LedgerTab = 'cost' | 'contract' | 'fund'
type TrendMode = '累计' | '当月'

const ALERT_TYPE_LABEL: Record<string, string> = {
  MATERIAL_EXCEEDS_BUDGET: '材料费超预算',
  DYNAMIC_COST_EXCEEDS_TARGET: '动态成本超目标',
}
const SEVERITY_LABEL: Record<string, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const BUSINESS_TYPE_LABEL: Record<string, string> = {
  PAY_APPLICATION: '付款申请',
  CONTRACT: '合同',
  CONTRACT_CHANGE: '合同变更',
}
const PAYMENT_STATUS_LABEL: Record<string, string> = {
  PENDING: '待审批',
  PROCESSING: '审批中',
  APPROVED: '已审批',
  REJECTED: '已驳回',
  UNPAID: '未支付',
  PARTIAL: '部分支付',
  PAID: '已支付',
}
const CONTRACT_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PERFORMING: '履约中',
  SETTLED: '已结算',
  TERMINATED: '已终止',
}

function displayAmount(value: number) {
  return (value / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function amountText(value: string | undefined) {
  return displayAmount(toNum(value))
}

function percentText(numerator: string | undefined, denominator: string | undefined) {
  const base = toNum(denominator)
  if (!base) return '0.00%'
  return `${((toNum(numerator) / base) * 100).toFixed(2)}%`
}

function alertDeviationText(message: string | undefined) {
  if (!message) return '-'
  const match = message.match(/(?:建议|请).+$/)
  return match?.[0] ?? message
}

function labelOf(map: Record<string, string>, value: string | undefined) {
  return value ? (map[value] ?? value) : '-'
}

function ledgerSubjectLabel(row: { rowType?: string; costSubjectName?: string }) {
  if (row.rowType === 'contract') return labelOf(CONTRACT_TYPE_MAP, row.costSubjectName)
  return row.costSubjectName || '-'
}

function ledgerStatusLabel(status: string | undefined) {
  return labelOf({ ...PAYMENT_STATUS_LABEL, ...CONTRACT_STATUS_LABEL }, status)
}

function statusTagColor(label: string) {
  if (['履约中', '审批中'].includes(label)) return 'processing'
  if (['已结算', '已审批', '已支付', '正常'].includes(label)) return 'success'
  if (['待审批', '部分支付', '偏高'].includes(label)) return 'warning'
  if (['已驳回', '已终止', '超预算'].includes(label)) return 'error'
  return 'default'
}

const trendPoints = computed(() => props.data.trendPoints ?? [])
const subjectRankings = computed(() => props.data.subjectRankings ?? [])
const overBudgetAlerts = computed(() => props.data.overBudgetAlerts ?? [])
const overdueRecords = computed(() => props.data.overdueItems ?? [])
const pendingPaymentRecords = computed(() => props.data.pendingPayments ?? [])
const ledgerRecords = computed(() => props.data.ledgerRows ?? [])
const activeLedgerTab = ref<LedgerTab>('cost')
const trendMode = ref<TrendMode>('累计')
const subjectFilter = ref('all')
const statusFilter = ref('all')
const ledgerKeyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)

const kpiCards = computed(() => [
  {
    title: '目标成本（含税）',
    value: amountText(props.data.targetCost),
    unit: '万元',
    accent: '#1677ff',
    tone: 'default',
    metas: ['项目级成本汇总'],
  },
  {
    title: '动态成本（含税）',
    value: amountText(props.data.dynamicCost),
    unit: '万元',
    accent: '#13b7c7',
    tone: 'cyan',
    metas: [`完成率 ${percentText(props.data.dynamicCost, props.data.targetCost)}`],
  },
  {
    title: '成本偏差',
    value: `${devSign(props.data.costDeviation)}${displayAmount(Math.abs(toNum(props.data.costDeviation)))}`,
    unit: '万元',
    accent: '#ef4444',
    tone: 'danger',
    metas: [`偏差率 ${percentText(props.data.costDeviation, props.data.targetCost)}`],
    info: true,
  },
  {
    title: '预计利润（含税）',
    value: amountText(props.data.expectedProfit),
    unit: '万元',
    accent: '#f97316',
    tone: 'warning',
    metas: [`利润率 ${percentText(props.data.expectedProfit, props.data.contractIncome)}`],
  },
  {
    title: '合同收入',
    value: amountText(props.data.contractIncome),
    unit: '万元',
    accent: '#d9e2ef',
    tone: 'default',
    metas: ['合同收入汇总'],
  },
  {
    title: '实际成本',
    value: amountText(props.data.actualCost),
    unit: '万元',
    accent: '#d9e2ef',
    tone: 'default',
    metas: [`锁定成本 ${amountText(props.data.contractLockedCost)}`],
  },
])

const subjectRows = computed<SubjectRankRow[]>(() => {
  return [...subjectRankings.value]
    .sort((a, b) => toNum(b.actualCost) - toNum(a.actualCost))
    .slice(0, 6)
    .map((item, index) => toSubjectRankRow(item, index))
})

const maxSubjectAmount = computed(() => Math.max(...subjectRows.value.map((item) => item.amount), 1))

const budgetAlertRows = computed(() =>
  overBudgetAlerts.value.map((item, index) => [
    String(index + 1),
    item.projectName || item.projectId || '-',
    labelOf(ALERT_TYPE_LABEL, item.alertType),
    alertDeviationText(item.message),
    labelOf(SEVERITY_LABEL, item.severity),
    item.triggeredAt || '-',
    item.message || '-',
  ]),
)

const overdueRows = computed(() =>
  overdueRecords.value.map((item, index) => [
    String(index + 1),
    labelOf(BUSINESS_TYPE_LABEL, item.businessType),
    item.title || '-',
    String(item.overdueDays ?? 0),
    item.ownerName || '-',
    item.plannedAt || '-',
  ]),
)

const pendingPaymentRows = computed(() =>
  pendingPaymentRecords.value.map((item, index) => [
    String(index + 1),
    item.payRecordId || '-',
    item.partnerName || item.contractName || '-',
    amountText(item.payAmount),
    item.payDate || '-',
    ledgerStatusLabel(item.payStatus),
  ]),
)

const subjectOptions = computed(() =>
  Array.from(new Set(ledgerRecords.value.map((row) => row.costSubjectName).filter(Boolean))),
)

const statusOptions = computed(() =>
  Array.from(new Set(ledgerRecords.value.map((row) => row.status).filter(Boolean))),
)

const filteredLedgerRecords = computed(() => {
  const keyword = ledgerKeyword.value.trim().toLowerCase()
  return ledgerRecords.value.filter((row) => {
    if (activeLedgerTab.value === 'fund' && row.rowType !== 'fund') return false
    if (activeLedgerTab.value === 'contract' && row.rowType !== 'contract') return false
    if (activeLedgerTab.value === 'cost' && row.rowType && row.rowType !== 'cost') return false
    if (subjectFilter.value !== 'all' && row.costSubjectName !== subjectFilter.value) return false
    if (statusFilter.value !== 'all' && row.status !== statusFilter.value) return false
    if (!keyword) return true
    return [row.contractCode, row.contractName, row.costSubjectName, row.ownerName]
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  })
})

const ledgerRows = computed(() =>
  filteredLedgerRecords.value.map((row, index) => [
    String((currentPage.value - 1) * pageSize.value + index + 1),
    ledgerSubjectLabel(row),
    row.contractCode || '-',
    row.contractName || '-',
    amountText(row.budgetAmount),
    amountText(row.actualAmount),
    row.completionRatio || '0.00%',
    amountText(row.deviationAmount),
    row.deviationRatio || '0.00%',
    ledgerStatusLabel(row.status) || '正常',
    row.ownerName || '-',
  ]),
)

const ledgerTotal = computed(() => filteredLedgerRecords.value.length)
const pagedLedgerRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredLedgerRecords.value.slice(start, start + pageSize.value)
})
const pagedLedgerRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return ledgerRows.value.slice(start, start + pageSize.value)
})
const displayedTrendPoints = computed(() => {
  if (trendMode.value === '累计') return trendPoints.value
  return trendPoints.value.map((item, index, source) => {
    const prev = source[index - 1]
    return {
      month: item.month,
      targetCost: String(toNum(item.targetCost) - toNum(prev?.targetCost)),
      dynamicCost: String(toNum(item.dynamicCost) - toNum(prev?.dynamicCost)),
      costDeviation: String(toNum(item.costDeviation) - toNum(prev?.costDeviation)),
    }
  })
})
const trendLabel = computed(() => trendMode.value)

const costTrendOption = computed(() => ({
  color: ['#2f7cf6', '#13b7c7', '#f28b82'],
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'cross', label: { backgroundColor: '#6b7280' } },
    backgroundColor: 'rgba(255,255,255,0.96)',
    borderColor: '#e5eaf3',
    borderWidth: 1,
    textStyle: { color: '#1f2937' },
  },
  legend: {
    top: 4,
    left: 'center',
    itemWidth: 16,
    itemHeight: 6,
    textStyle: { color: '#536176', fontSize: 11 },
    data: [`目标成本（${trendLabel.value}）`, `动态成本（${trendLabel.value}）`, `成本偏差（${trendLabel.value}）`],
  },
  grid: { left: 50, right: 54, top: 42, bottom: 34, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: displayedTrendPoints.value.map((item) => item.month),
    axisLine: { lineStyle: { color: '#d8e0ec' } },
    axisLabel: { color: '#6b7280', fontSize: 10 },
  },
  yAxis: [
    {
      type: 'value',
      name: '单位：万元',
      nameTextStyle: { color: '#6b7280', fontSize: 10, padding: [0, 0, 0, 18] },
      splitLine: { lineStyle: { color: '#edf1f7' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    {
      type: 'value',
      splitLine: { show: false },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
  ],
  series: [
    {
      name: `目标成本（${trendLabel.value}）`,
      type: 'line',
      data: displayedTrendPoints.value.map((item) => toNum(item.targetCost) / 10000),
      smooth: true,
      symbolSize: 0,
      lineStyle: { width: 2, type: 'dashed' },
    },
    {
      name: `动态成本（${trendLabel.value}）`,
      type: 'line',
      data: displayedTrendPoints.value.map((item) => toNum(item.dynamicCost) / 10000),
      smooth: true,
      symbolSize: 0,
      lineStyle: { width: 2 },
    },
    {
      name: `成本偏差（${trendLabel.value}）`,
      type: 'bar',
      yAxisIndex: 1,
      data: displayedTrendPoints.value.map((item) => toNum(item.costDeviation) / 10000),
      barWidth: 10,
      itemStyle: { borderRadius: [3, 3, 0, 0], opacity: 0.86 },
    },
  ],
}))

function toSubjectRankRow(item: CostManagerSubjectRanking, index: number): SubjectRankRow {
  return {
    rank: index + 1,
    name: item.costSubjectName || '-',
    amount: Math.max(toNum(item.actualCost), 0),
    ratio: toNum(item.ratio),
    sourceName: item.costSubjectName,
  }
}

function onSubjectClick(row: SubjectRankRow) {
  emit('barClick', { name: row.sourceName ?? row.name })
}

function subjectBarWidth(amount: number) {
  return `${Math.max(8, Math.round((amount / maxSubjectAmount.value) * 100))}%`
}

function resetLedgerFilters() {
  activeLedgerTab.value = 'cost'
  subjectFilter.value = 'all'
  statusFilter.value = 'all'
  ledgerKeyword.value = ''
  currentPage.value = 1
}

function exportLedgerCsv() {
  const headers = [
    '序号',
    '成本科目',
    '合同编号',
    '合同名称',
    '预算金额（万元）',
    '累计发生（万元）',
    '完成量',
    '偏差（万元）',
    '偏差率',
    '状态',
    '责任人',
  ]
  const csv = [headers, ...ledgerRows.value]
    .map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(','))
    .join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = '成本台账.csv'
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(link.href))
  message.success('导出成功')
}

function ledgerRoute(row: CostManagerLedgerRow) {
  if (row.rowType === 'contract') return '/contract/ledger'
  if (row.rowType === 'fund') return '/payment/application'
  return '/cost/ledger'
}

function viewLedgerRow(row: CostManagerLedgerRow | undefined) {
  if (!row) return
  const query: Record<string, string> = {}
  if (props.data.projectId) query.projectId = props.data.projectId
  if (row.rowType === 'cost' && row.costSubjectId) query.costSubjectId = row.costSubjectId
  router.push({ path: ledgerRoute(row), query })
}

function drillLedgerRow(row: CostManagerLedgerRow | undefined) {
  if (!row) return
  if (row.rowType === 'cost') {
    subjectFilter.value = row.costSubjectName || 'all'
    currentPage.value = 1
    emit('barClick', { name: row.costSubjectName })
    return
  }
  viewLedgerRow(row)
}

watch([activeLedgerTab, subjectFilter, statusFilter, ledgerKeyword, pageSize], () => {
  currentPage.value = 1
})
</script>

<template>
  <div class="cost-reference-shell" :aria-busy="loading">
    <section class="cost-reference-kpis">
      <article
        v-for="card in kpiCards"
        :key="card.title"
        class="cost-reference-kpi"
        :style="{ '--kpi-accent': card.accent }"
      >
        <div class="cost-reference-kpi-title">
          {{ card.title }}
          <InfoCircleOutlined v-if="card.info" />
        </div>
        <div class="cost-reference-kpi-value" :class="`is-${card.tone}`">
          {{ card.value }} <small>{{ card.unit }}</small>
        </div>
        <div class="cost-reference-kpi-meta">
          <span v-for="meta in card.metas" :key="meta">{{ meta }}</span>
        </div>
      </article>
    </section>

    <section class="cost-reference-main-grid">
      <div class="cost-reference-panel cost-reference-analysis">
        <div class="cost-reference-panel-head">
          <div>
            <strong>成本执行情况</strong>
            <span>（含税）</span>
          </div>
          <a-segmented v-model:value="trendMode" :options="['累计', '当月']" size="small" />
        </div>
        <v-chart :option="costTrendOption" autoresize class="cost-reference-chart" />

        <div class="cost-rank-head">
          <div>
            <strong>成本科目排名</strong>
            <span>（点击柱体下钻）</span>
          </div>
          <div class="cost-rank-unit">单位：万元</div>
        </div>
        <div class="cost-rank-list">
          <button
            v-for="row in subjectRows"
            :key="row.name"
            class="cost-rank-row"
            type="button"
            @click="onSubjectClick(row)"
          >
            <span class="cost-rank-index">{{ row.rank }}</span>
            <span class="cost-rank-name">{{ row.name }}</span>
            <span class="cost-rank-bar"><i :style="{ width: subjectBarWidth(row.amount) }" /></span>
            <span class="cost-rank-amount">{{ displayAmount(row.amount) }}</span>
            <span class="cost-rank-ratio">{{ row.ratio.toFixed(2) }}%</span>
          </button>
        </div>
      </div>

      <aside class="cost-reference-side-stack">
        <div class="cost-reference-panel cost-mini-panel is-red">
          <div class="cost-reference-panel-head mini">
            <strong>超预算预警 <b>（{{ overBudgetAlerts.length }}）</b></strong>
            <a>全部</a>
          </div>
          <table class="cost-mini-table">
            <thead>
              <tr>
                <th>序号</th>
                <th>项目名称</th>
                <th>预警类型</th>
                <th>预警说明</th>
                <th>风险等级</th>
                <th>预警时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in budgetAlertRows" :key="row[0]">
                <td v-for="(cell, index) in row.slice(0, 6)" :key="index" :class="{ danger: index === 3 }">
                  <a-tooltip v-if="index === 3" :title="row[6]">
                    <span>{{ cell }}</span>
                  </a-tooltip>
                  <template v-else>{{ cell }}</template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="cost-reference-panel cost-mini-panel is-orange">
          <div class="cost-reference-panel-head mini">
            <strong>逾期事项 <b>（{{ overdueRecords.length }}）</b></strong>
            <a>全部</a>
          </div>
          <table class="cost-mini-table">
            <thead>
              <tr>
                <th>序号</th>
                <th>事项类型</th>
                <th>事项名称</th>
                <th>逾期天数</th>
                <th>责任人</th>
                <th>计划完成时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in overdueRows" :key="row[0]">
                <td v-for="(cell, index) in row" :key="index" :class="{ danger: index === 3 }">{{ cell }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="cost-reference-panel cost-mini-panel is-blue">
          <div class="cost-reference-panel-head mini">
            <strong>待审批付款 <b>（{{ pendingPaymentRecords.length }}）</b></strong>
            <a>全部</a>
          </div>
          <table class="cost-mini-table">
            <thead>
              <tr>
                <th>序号</th>
                <th>申请单号</th>
                <th>合同名称</th>
                <th>金额（万元）</th>
                <th>申请付款时间</th>
                <th>审批节点</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in pendingPaymentRows" :key="row[0]">
                <td v-for="(cell, index) in row" :key="index">{{ cell }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </aside>
    </section>

    <section class="cost-reference-panel cost-ledger-reference">
      <div class="cost-ledger-tabs">
        <a :class="{ active: activeLedgerTab === 'cost' }" @click="activeLedgerTab = 'cost'">成本台账</a>
        <a :class="{ active: activeLedgerTab === 'contract' }" @click="activeLedgerTab = 'contract'">合同执行</a>
        <a :class="{ active: activeLedgerTab === 'fund' }" @click="activeLedgerTab = 'fund'">资金流水</a>
      </div>
      <div class="cost-ledger-tools">
        <a-select v-model:value="subjectFilter" size="small" style="width: 96px">
          <a-select-option value="all">全部科目</a-select-option>
          <a-select-option v-for="subject in subjectOptions" :key="subject" :value="subject">
            {{ subject }}
          </a-select-option>
        </a-select>
        <a-select v-model:value="statusFilter" size="small" style="width: 96px">
          <a-select-option value="all">全部状态</a-select-option>
          <a-select-option v-for="status in statusOptions" :key="status" :value="status">
            {{ ledgerStatusLabel(status) }}
          </a-select-option>
        </a-select>
        <a-range-picker size="small" value-format="YYYY-MM-DD" disabled />
        <a-input
          v-model:value="ledgerKeyword"
          size="small"
          placeholder="请输入合同名称/编号"
          style="width: 210px"
        />
        <a-button size="small" @click="resetLedgerFilters">重置</a-button>
        <a-button size="small" type="primary" ghost @click="exportLedgerCsv">导出</a-button>
      </div>
      <table class="cost-ledger-table">
        <thead>
          <tr>
            <th>序号</th>
            <th>成本科目</th>
            <th>合同编号</th>
            <th>合同名称</th>
            <th>预算金额（万元）</th>
            <th>累计发生（万元）</th>
            <th>完成量</th>
            <th>偏差（万元）</th>
            <th>偏差率</th>
            <th>状态</th>
            <th>责任人</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in pagedLedgerRows" :key="`${row[0]}-${row[2]}-${row[3]}`">
            <td v-for="(cell, cellIndex) in row" :key="cellIndex">
              <a-tag v-if="cellIndex === 9" :color="statusTagColor(cell)">{{ cell }}</a-tag>
              <template v-else>{{ cell }}</template>
            </td>
            <td class="cost-ledger-actions">
              <a @click="viewLedgerRow(pagedLedgerRecords[rowIndex])">查看</a>
              <a @click="drillLedgerRow(pagedLedgerRecords[rowIndex])">下钻</a>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="cost-ledger-pagination">
        <span>共 {{ ledgerTotal }} 条</span>
        <a-select v-model:value="pageSize" size="small" style="width: 88px">
          <a-select-option :value="10">10条/页</a-select-option>
          <a-select-option :value="20">20条/页</a-select-option>
          <a-select-option :value="50">50条/页</a-select-option>
        </a-select>
        <a-pagination
          v-model:current="currentPage"
          :total="ledgerTotal"
          :page-size="pageSize"
          size="small"
        />
        <span>前往</span>
        <a-input-number v-model:value="currentPage" :min="1" size="small" style="width: 56px" />
        <span>页</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.cost-reference-shell {
  display: grid;
  gap: 10px;
  color: #1f2937;
}

.cost-reference-kpis {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  background: #fff;
  border: 1px solid #e4eaf3;
  border-radius: 6px;
  overflow: hidden;
}

.cost-reference-kpi {
  position: relative;
  min-height: 104px;
  padding: 17px 18px 14px;
  border-right: 1px solid #edf1f7;
  background: linear-gradient(180deg, #fff, #fbfdff);
}

.cost-reference-kpi:last-child {
  border-right: 0;
}

.cost-reference-kpi::before {
  position: absolute;
  top: 14px;
  bottom: 14px;
  left: 0;
  width: 4px;
  background: var(--kpi-accent);
  border-radius: 0 999px 999px 0;
  content: '';
}

.cost-reference-kpi-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #263246;
  font-size: 14px;
  font-weight: 700;
  line-height: 18px;
}

.cost-reference-kpi-value {
  margin-top: 12px;
  color: #111827;
  font-size: 22px;
  font-weight: 800;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.cost-reference-kpi-value.is-cyan {
  color: #13a8ba;
}

.cost-reference-kpi-value.is-danger {
  color: #b42318;
}

.cost-reference-kpi-value.is-warning {
  color: #ea7500;
}

.cost-reference-kpi-value small {
  margin-left: 4px;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
}

.cost-reference-kpi-meta {
  display: flex;
  gap: 18px;
  margin-top: 10px;
  color: #5d6b82;
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
}

.cost-reference-main-grid {
  display: grid;
  grid-template-columns: minmax(420px, 0.72fr) minmax(720px, 1.28fr);
  gap: 10px;
}

.cost-reference-panel {
  min-width: 0;
  background: #fff;
  border: 1px solid #e4eaf3;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.03);
  overflow: hidden;
}

.cost-reference-panel-head {
  min-height: 42px;
  padding: 0 16px;
  border-bottom: 1px solid #edf1f7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #172033;
  font-size: 14px;
}

.cost-reference-panel-head strong {
  font-weight: 800;
}

.cost-reference-panel-head span {
  color: #64748b;
  font-weight: 500;
}

.cost-reference-panel-head.mini {
  min-height: 36px;
  font-size: 13px;
}

.cost-reference-panel-head.mini b {
  color: #ef4444;
}

.cost-reference-panel-head.mini a {
  color: #2f7cf6;
  font-size: 12px;
}

.cost-mini-panel.is-red {
  border-top: 2px solid #ef4444;
}

.cost-mini-panel.is-orange {
  border-top: 2px solid #f97316;
}

.cost-mini-panel.is-blue {
  border-top: 2px solid #2f7cf6;
}

.cost-reference-chart {
  width: 100%;
  height: 286px;
}

.cost-rank-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0 18px 8px;
  color: #172033;
  font-size: 13px;
}

.cost-rank-head strong {
  font-weight: 800;
}

.cost-rank-head span,
.cost-rank-unit {
  color: #64748b;
  font-size: 12px;
}

.cost-rank-list {
  padding: 0 18px 2px;
}

.cost-rank-row {
  width: 100%;
  min-height: 30px;
  padding: 0;
  border: 0;
  background: transparent;
  display: grid;
  grid-template-columns: max-content minmax(max-content, 1fr) minmax(80px, 1.5fr) max-content max-content;
  align-items: center;
  gap: 10px;
  color: #273449;
  font: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.cost-rank-row:hover {
  background: #f7fbff;
}

.cost-rank-index,
.cost-rank-amount,
.cost-rank-ratio {
  font-variant-numeric: tabular-nums;
}

.cost-rank-name {
  white-space: nowrap;
}

.cost-rank-bar {
  height: 8px;
  background: #edf2f8;
  border-radius: 999px;
  overflow: hidden;
}

.cost-rank-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #2f7cf6, #3285ff);
  border-radius: inherit;
}

.cost-rank-amount,
.cost-rank-ratio {
  text-align: right;
}

.cost-drill-hint {
  padding: 5px 18px 13px;
  color: #64748b;
  font-size: 12px;
  text-align: right;
}

.cost-drill-hint span {
  display: inline-grid;
  width: 16px;
  height: 16px;
  margin-left: 6px;
  border: 1px solid #cbd5e1;
  border-radius: 50%;
  place-items: center;
}

.cost-reference-side-stack {
  display: grid;
  grid-template-rows: repeat(3, minmax(132px, 1fr));
  gap: 10px;
}

.cost-mini-table,
.cost-ledger-table {
  width: 100%;
  border-collapse: collapse;
  color: #243044;
  font-size: 12px;
}

.cost-mini-table {
  table-layout: auto;
}

.cost-ledger-table {
  table-layout: fixed;
}

.cost-mini-table th,
.cost-mini-table td,
.cost-ledger-table th,
.cost-ledger-table td {
  height: 30px;
  padding: 0 8px;
  border-bottom: 1px solid #edf1f7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-mini-table td {
  height: auto;
  min-height: 30px;
  padding-top: 5px;
  padding-bottom: 5px;
  line-height: 16px;
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
}

.cost-mini-table th,
.cost-ledger-table th {
  color: #536176;
  background: #fbfcff;
  font-weight: 700;
}

.cost-mini-table td:first-child,
.cost-mini-table th:first-child,
.cost-ledger-table td:first-child,
.cost-ledger-table th:first-child {
  text-align: center;
}

.cost-mini-table .danger {
  color: #ef4444;
  font-weight: 700;
}

.cost-ledger-reference {
  position: relative;
  padding-top: 43px;
}

.cost-ledger-tabs {
  position: absolute;
  top: 0;
  left: 16px;
  height: 43px;
  display: flex;
  align-items: flex-end;
  gap: 28px;
}

.cost-ledger-tabs a {
  height: 35px;
  color: #334155;
  display: grid;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
}

.cost-ledger-tabs a.active {
  color: #1677ff;
  border-bottom: 2px solid #1677ff;
}

.cost-ledger-tools {
  position: absolute;
  top: 8px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.cost-ledger-tools :deep(.ant-select-selector),
.cost-ledger-tools :deep(.ant-picker),
.cost-ledger-tools :deep(.ant-input),
.cost-ledger-tools :deep(.ant-btn) {
  min-height: 26px;
  height: 26px;
  line-height: 24px;
}

.cost-ledger-tools :deep(.ant-select-selection-item),
.cost-ledger-tools :deep(.ant-select-selection-placeholder),
.cost-ledger-tools :deep(.ant-picker-input > input),
.cost-ledger-tools :deep(.ant-input),
.cost-ledger-tools :deep(.ant-btn) {
  font-size: 12px;
}

.cost-ledger-tools :deep(.ant-select-single.ant-select-sm .ant-select-selector) {
  padding-top: 0;
  padding-bottom: 0;
}

.cost-ledger-table th:nth-child(1) {
  width: 48px;
}

.cost-ledger-table th:nth-child(2) {
  width: 150px;
}

.cost-ledger-table th:nth-child(3) {
  width: 120px;
}

.cost-ledger-table th:nth-child(4) {
  width: 230px;
}

.cost-ledger-table th:nth-child(5),
.cost-ledger-table th:nth-child(6),
.cost-ledger-table th:nth-child(8) {
  width: 116px;
}

.cost-ledger-table th:nth-child(7),
.cost-ledger-table th:nth-child(9),
.cost-ledger-table th:nth-child(10),
.cost-ledger-table th:nth-child(11),
.cost-ledger-table th:nth-child(12) {
  width: 78px;
}

.cost-ledger-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cost-ledger-actions a {
  color: #1677ff;
}

.cost-ledger-pagination {
  min-height: 54px;
  padding: 10px 18px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  color: #536176;
  font-size: 12px;
}

.cost-ledger-pagination :deep(.ant-select-single.ant-select-sm),
.cost-ledger-pagination :deep(.ant-pagination-item),
.cost-ledger-pagination :deep(.ant-pagination-prev),
.cost-ledger-pagination :deep(.ant-pagination-next),
.cost-ledger-pagination :deep(.ant-input-number-sm) {
  height: 22px;
  line-height: 20px;
}

.cost-ledger-pagination :deep(.ant-select-single.ant-select-sm .ant-select-selector),
.cost-ledger-pagination :deep(.ant-input-number-sm input) {
  height: 22px;
  line-height: 20px;
}

.cost-ledger-pagination :deep(.ant-select-single.ant-select-sm .ant-select-selection-item) {
  line-height: 20px;
}

@media (max-width: 1280px) {
  .cost-reference-kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .cost-reference-main-grid {
    grid-template-columns: 1fr;
  }

  .cost-reference-side-stack {
    grid-template-rows: none;
  }

  .cost-ledger-tools {
    position: static;
    padding: 0 16px 10px;
    flex-wrap: wrap;
  }

  .cost-ledger-reference {
    padding-top: 43px;
  }
}
</style>
