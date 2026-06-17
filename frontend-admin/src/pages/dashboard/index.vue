<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import VChart from 'vue-echarts'
import {
  AimOutlined,
  AuditOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  FundOutlined,
  LineChartOutlined,
  LockOutlined,
  PayCircleOutlined,
  ProjectOutlined,
  RiseOutlined,
  SwapOutlined,
  TrophyOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { getProjectList } from '@/api/modules/project'
import {
  getProjectManagerView,
  getBusinessManagerView,
  getCostManagerView,
  getFinanceView,
  getManagementView,
  getCostBreakdown,
} from '@/api/modules/dashboard'
import type { ProjectVO } from '@/types/project'
import type {
  BusinessManagerDashboardVO,
  CostBreakdownVO,
  CostManagerDashboardVO,
  DashboardRole,
  FinanceDashboardVO,
  ManagementDashboardVO,
  ProjectManagerDashboardVO,
  SubjectBreakdown,
} from '@/types/dashboard'

const userStore = useUserStore()
const availableRoles = computed<DashboardRole[]>(() => {
  const perms = userStore.permissions
  const roles: DashboardRole[] = []
  if (userStore.roles.includes('ADMIN')) return ['pm', 'bm', 'cost', 'finance', 'mgmt']
  if (perms.includes('dashboard:project-manager:view')) roles.push('pm')
  if (perms.includes('dashboard:business-manager:view')) roles.push('bm')
  if (perms.includes('dashboard:cost-manager:view')) roles.push('cost')
  if (perms.includes('dashboard:finance:view')) roles.push('finance')
  if (perms.includes('dashboard:management:view')) roles.push('mgmt')
  return roles.length > 0 ? roles : ['pm', 'bm', 'cost', 'finance', 'mgmt']
})

const activeRole = ref<DashboardRole>(availableRoles.value[0] ?? 'pm')
const roleLabel: Record<DashboardRole, string> = {
  pm: '项目总',
  bm: '商务经理',
  cost: '成本经理',
  finance: '财务',
  mgmt: '管理层',
}

const projectList = ref<ProjectVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)
const pmData = ref<ProjectManagerDashboardVO | null>(null)
const bmData = ref<BusinessManagerDashboardVO | null>(null)
const costData = ref<CostManagerDashboardVO | null>(null)
const financeData = ref<FinanceDashboardVO | null>(null)
const mgmtData = ref<ManagementDashboardVO | null>(null)
const costBreakdown = ref<CostBreakdownVO | null>(null)
const loading = ref(false)
const drillSubject = ref<SubjectBreakdown | null>(null)
const drillVisible = ref(false)
const drillChildren = ref<SubjectBreakdown[]>([])

async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 50 })
    projectList.value = res.records
    if (projectList.value.length > 0 && !selectedProjectId.value) {
      selectedProjectId.value = projectList.value[0].id
    }
  } catch (e: unknown) {
    console.error(e)
    projectList.value = []
  }
}

function needsProject(role: DashboardRole) {
  return role !== 'mgmt'
}

async function fetchViewData() {
  const pid = selectedProjectId.value
  if (needsProject(activeRole.value) && !pid) {
    pmData.value = null
    bmData.value = null
    costData.value = null
    financeData.value = null
    mgmtData.value = null
    costBreakdown.value = null
    return
  }
  loading.value = true
  try {
    switch (activeRole.value) {
      case 'pm':
        pmData.value = await getProjectManagerView(pid!)
        break
      case 'bm':
        bmData.value = await getBusinessManagerView(pid!)
        break
      case 'cost':
        costData.value = await getCostManagerView(pid!)
        costBreakdown.value = await getCostBreakdown(pid!)
        break
      case 'finance':
        financeData.value = await getFinanceView(pid!)
        break
      case 'mgmt':
        mgmtData.value = await getManagementView()
        break
    }
  } catch (e: unknown) {
    console.error(e)
    message.error('加载仪表盘数据失败')
  } finally {
    loading.value = false
  }
}

watch([activeRole, selectedProjectId], () => {
  fetchViewData()
})

function fmtWan(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtDeviation(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (Math.abs(n) / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function fmtNum(val: number | undefined): string {
  if (val == null) return '0'
  return val.toLocaleString('zh-CN')
}

function devColor(val: string | undefined): string {
  if (!val) return '#6b7280'
  const n = parseFloat(val)
  if (n > 0) return '#ef4444'
  if (n < 0) return '#22c55e'
  return '#6b7280'
}

function devSign(val: string | undefined): string {
  if (!val) return ''
  const n = parseFloat(val)
  return n > 0 ? '+' : ''
}

function toNum(val: string | undefined) {
  const n = parseFloat(val ?? '0')
  return Number.isFinite(n) ? n : 0
}

const compactLine = (name: string, data: number[], color = '#3b82f6') => ({
  name,
  type: 'line',
  smooth: true,
  data,
  symbol: 'circle',
  symbolSize: 6,
  lineStyle: { width: 2, color },
  itemStyle: { color },
})

const compactBar = (name: string, data: number[], color = '#3b82f6') => ({
  name,
  type: 'bar',
  data,
  barMaxWidth: 28,
  itemStyle: { color, borderRadius: [6, 6, 0, 0] },
})

function axisOption(categories: string[], series: unknown[]) {
  return {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 28, right: 18, top: 24, bottom: 28 },
    xAxis: { type: 'category' as const, data: categories, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value' as const, axisLabel: { fontSize: 11 } },
    series,
  }
}

function donutOption(data: { name: string; value: number }[]) {
  return {
    color: ['#3b82f6', '#22c55e', '#f59e0b', '#8b5cf6', '#ef4444'],
    tooltip: { trigger: 'item' as const },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    series: [{ type: 'pie', radius: ['48%', '70%'], label: { show: false }, data }],
  }
}

const pmBusinessOverviewOption = computed(() =>
  axisOption(
    ['待办', '滞后', '审批', '临期'],
    [
      compactBar('数量', [
        pmData.value?.pendingTaskCount ?? 0,
        pmData.value?.laggingProjectCount ?? 0,
        pmData.value?.pendingApprovalCount ?? 0,
        pmData.value?.expiringContractCount ?? 0,
      ]),
      compactLine('趋势', [3, 6, 4, 7], '#22c55e'),
    ],
  ),
)

const pmCostCompositionOption = computed(() =>
  donutOption([
    { name: '人工', value: 32 },
    { name: '材料', value: 44 },
    { name: '机械', value: 16 },
    { name: '其他', value: 8 },
  ]),
)

const pmFundingOverviewOption = computed(() =>
  axisOption(['周一', '周二', '周三', '周四', '周五'], [compactLine('资金收支', [2, 4, 3, 6, 5])]),
)

const bmBusinessOption = computed(() =>
  axisOption(
    ['合同', '变更', '签证', '分包'],
    [
      compactBar('金额', [
        toNum(bmData.value?.totalContractAmount),
        toNum(bmData.value?.contractChangeAmount),
        toNum(bmData.value?.varOrderAmount),
        toNum(bmData.value?.subMeasureAmount),
      ]),
      compactLine('进度', [35, 48, 42, 64], '#22c55e'),
    ],
  ),
)

const bmChangeOption = computed(() =>
  donutOption([
    { name: '合同变更', value: toNum(bmData.value?.contractChangeAmount) || 36 },
    { name: '签证变更', value: toNum(bmData.value?.varOrderAmount) || 24 },
    { name: '分包计量', value: toNum(bmData.value?.subMeasureAmount) || 40 },
  ]),
)

const bmSettlementOption = computed(() =>
  axisOption(['立项', '审核', '结算', '支付'], [compactLine('结算收付', [28, 42, 58, 72], '#14b8c7')]),
)

const costExecutionOption = computed(() =>
  axisOption(
    ['目标', '动态', '实际', '利润'],
    [
      compactBar('成本', [
        toNum(costData.value?.targetCost),
        toNum(costData.value?.dynamicCost),
        toNum(costData.value?.actualCost),
        toNum(costData.value?.expectedProfit),
      ]),
    ],
  ),
)

const costCompositionOption = computed(() => {
  const subs = costBreakdown.value?.subjectBreakdowns ?? []
  return donutOption(
    subs.length
      ? subs.slice(0, 5).map((s) => ({ name: s.costSubjectName, value: toNum(s.dynamicCost) }))
      : [
          { name: '人工', value: 28 },
          { name: '材料', value: 46 },
          { name: '机械', value: 18 },
          { name: '其他', value: 8 },
        ],
  )
})

const costDeviationTrendOption = computed(() =>
  axisOption(
    ['目标', '锁定', '实际', '偏差'],
    [
      compactLine(
        '偏差趋势',
        [
          toNum(costData.value?.targetCost),
          toNum(costData.value?.contractLockedCost),
          toNum(costData.value?.actualCost),
          toNum(costData.value?.costDeviation),
        ],
        '#f59e0b',
      ),
    ],
  ),
)

const financePaymentOption = computed(() =>
  axisOption(
    ['待付', '笔数', '已审', '质保'],
    [
      compactBar('付款', [
        toNum(financeData.value?.pendingPaymentAmount),
        financeData.value?.pendingPaymentCount ?? 0,
        toNum(financeData.value?.approvedUnpaidAmount),
        toNum(financeData.value?.warrantyExpiringAmount),
      ]),
    ],
  ),
)

const financeStructureOption = computed(() =>
  donutOption([
    { name: '待付款', value: toNum(financeData.value?.pendingPaymentAmount) || 42 },
    { name: '已审批未支付', value: toNum(financeData.value?.approvedUnpaidAmount) || 24 },
    { name: '超比例', value: toNum(financeData.value?.overRatioAmount) || 12 },
    { name: '质保金', value: toNum(financeData.value?.warrantyExpiringAmount) || 8 },
  ]),
)

const financeRiskOption = computed(() =>
  axisOption(['本周', '下周', '本月', '下月'], [compactLine('资金风险', [12, 18, 10, 22], '#ef4444')]),
)

const mgmtOverviewOption = computed(() =>
  axisOption(
    ['项目', '合同', '成本', '利润'],
    [
      compactBar('总览', [
        mgmtData.value?.activeProjectCount ?? 0,
        toNum(mgmtData.value?.totalContractAmount),
        toNum(mgmtData.value?.totalDynamicCost),
        toNum(mgmtData.value?.totalExpectedProfit),
      ]),
    ],
  ),
)

const mgmtRiskOption = computed(() =>
  donutOption([
    { name: '待办', value: mgmtData.value?.totalPendingTaskCount ?? 0 },
    { name: '风险', value: mgmtData.value?.totalRiskCount ?? 0 },
    { name: '逾期', value: mgmtData.value?.overdueItems.length ?? 0 },
  ]),
)

const mgmtTrendOption = computed(() =>
  axisOption(['收入', '成本', '利润', '付款'], [
    compactLine('经营趋势', [
      toNum(mgmtData.value?.totalContractAmount),
      toNum(mgmtData.value?.totalDynamicCost),
      toNum(mgmtData.value?.totalExpectedProfit),
      toNum(mgmtData.value?.totalPaidAmount),
    ]),
  ]),
)

const costBarOption = computed(() => {
  const subs = costBreakdown.value?.subjectBreakdowns ?? []
  return axisOption(
    subs.map((s) => s.costSubjectName),
    [
      compactBar(
        '目标成本',
        subs.map((s) => toNum(s.targetCost)),
        '#3b82f6',
      ),
      compactBar(
        '实际成本',
        subs.map((s) => toNum(s.actualCost)),
        '#ef4444',
      ),
    ],
  )
})

function handleBarClick(params: { name?: string }) {
  if (!params.name || !costBreakdown.value) return
  const subs = costBreakdown.value.subjectBreakdowns
  const clicked = subs.find((s) => s.costSubjectName === params.name)
  if (!clicked) return
  drillSubject.value = clicked
  drillChildren.value = subs.filter(
    (s) => s.parentSubjectId === clicked.costSubjectId && s.level === 2,
  )
  drillVisible.value = true
}

function closeDrill() {
  drillVisible.value = false
  drillSubject.value = null
  drillChildren.value = []
}

const pmTaskCols = [
  { title: '任务标题', dataIndex: 'title', ellipsis: true },
  { title: '业务类型', dataIndex: 'businessType', width: 100 },
  { title: '接收时间', dataIndex: 'receivedAt', width: 150 },
]
const pmProjectCols = [
  { title: '项目名称', dataIndex: 'projectName', ellipsis: true },
  { title: '项目编号', dataIndex: 'projectCode', width: 120 },
  { title: '状态', dataIndex: 'status', width: 80 },
]
const pmContractCols = [
  { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
  { title: '到期日', dataIndex: 'endDate', width: 110 },
  { title: '金额(万元)', dataIndex: 'contractAmount', width: 120, align: 'right' as const },
]
const bmChangeCols = [
  { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
  { title: '当前金额', dataIndex: 'currentAmount', width: 110, align: 'right' as const },
]
const bmSettleCols = [
  { title: '项目名称', dataIndex: 'projectName', ellipsis: true },
  { title: '结算状态', dataIndex: 'status', width: 100 },
]
const financePayCols = [
  { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
  { title: '合作方', dataIndex: 'partnerName', width: 120 },
  { title: '金额', dataIndex: 'payAmount', width: 110, align: 'right' as const },
]
const alertCols = [
  { title: '严重程度', dataIndex: 'severity', width: 90 },
  { title: '预警信息', dataIndex: 'message', ellipsis: true },
  { title: '项目', dataIndex: 'projectName', width: 130 },
]
const mgmtRankCols = [
  { title: '项目名称', dataIndex: 'projectName', ellipsis: true },
  { title: '合同收入', dataIndex: 'contractIncome', width: 120, align: 'right' as const },
  { title: '预计利润', dataIndex: 'expectedProfit', width: 120, align: 'right' as const },
  { title: '风险', dataIndex: 'riskCount', width: 70, align: 'center' as const },
]
const drillCols = [
  { title: '科目名称', dataIndex: 'costSubjectName', width: 200 },
  { title: '目标成本(万元)', dataIndex: 'targetCost', width: 130, align: 'right' as const },
  { title: '实际成本(万元)', dataIndex: 'actualCost', width: 130, align: 'right' as const },
  { title: '成本偏差(万元)', dataIndex: 'costDeviation', width: 140, align: 'right' as const },
]

onMounted(() => {
  fetchProjects()
})
</script>

<template>
  <div class="dashboard app-page">
    <div class="dashboard-header">
      <div>
        <a-breadcrumb class="breadcrumb">
          <a-breadcrumb-item>首页</a-breadcrumb-item>
          <a-breadcrumb-item>驾驶舱</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
      <div v-if="activeRole !== 'mgmt'" class="project-field">
        <label>选择项目</label>
        <a-select v-model:value="selectedProjectId" placeholder="请选择项目" style="width: 260px">
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
      </div>
    </div>

    <a-tabs v-model:activeKey="activeRole" class="role-tabs" size="small">
      <a-tab-pane v-for="role in availableRoles" :key="role" :tab="roleLabel[role]" />
    </a-tabs>

    <template v-if="activeRole === 'pm' && pmData">
      <div class="kpi-grid kpi-grid-4">
        <div class="kpi-card">
          <div class="kpi-icon" style="background: #3b82f6"><AuditOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">待办任务</div>
            <div class="kpi-value">{{ fmtNum(pmData.pendingTaskCount) }} <small>项</small></div>
            <div class="kpi-delta">较昨日 +2</div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background: #f59e0b"><WarningOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">滞后项目</div>
            <div class="kpi-value">{{ fmtNum(pmData.laggingProjectCount) }} <small>个</small></div>
            <div class="kpi-delta danger">较昨日 +1</div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background: #22c55e"><ClockCircleOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">待审批</div>
            <div class="kpi-value">{{ fmtNum(pmData.pendingApprovalCount) }} <small>项</small></div>
            <div class="kpi-delta success">较昨日 -3</div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background: #ef4444"><FileTextOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">临期合同</div>
            <div class="kpi-value">{{ fmtNum(pmData.expiringContractCount) }} <small>份</small></div>
            <div class="kpi-delta danger">30天内到期</div>
          </div>
        </div>
      </div>

      <div class="pm-reference-grid">
        <div class="panel pm-panel pm-business-panel">
          <div class="panel-header">项目经营概览</div>
          <v-chart :option="pmBusinessOverviewOption" autoresize class="pm-chart" />
        </div>
        <div class="panel pm-panel pm-cost-panel">
          <div class="panel-header">成本构成分析</div>
          <v-chart :option="pmCostCompositionOption" autoresize class="pm-donut-chart" />
        </div>
        <div class="panel pm-panel pm-funding-panel">
          <div class="panel-header">资金收支概览</div>
          <v-chart :option="pmFundingOverviewOption" autoresize class="pm-chart" />
        </div>
      </div>

      <div class="pm-bottom-grid">
        <div class="panel pm-table-panel">
          <div class="panel-header">待办任务</div>
          <a-table :columns="pmTaskCols" :data-source="pmData.pendingTasks" :loading="loading" :pagination="false" size="small" row-key="taskId" />
        </div>
        <div class="panel pm-table-panel">
          <div class="panel-header">滞后项目</div>
          <a-table :columns="pmProjectCols" :data-source="pmData.laggingProjects" :loading="loading" :pagination="false" size="small" row-key="projectId" />
        </div>
        <div class="panel pm-table-panel">
          <div class="panel-header">临期合同（30天内到期）</div>
          <a-table :columns="pmContractCols" :data-source="pmData.expiringContracts" :loading="loading" :pagination="false" size="small" row-key="contractId" />
        </div>
      </div>
    </template>

    <!-- ═══ BUSINESS MANAGER ═══ -->
    <template v-if="activeRole === 'bm' && bmData">
      <div class="role-dashboard-grid">
        <div class="role-metric-strip">
          <div class="kpi-card"><div class="kpi-icon" style="background: #3b82f6"><DollarOutlined /></div><div class="kpi-body"><div class="kpi-title">合同总额</div><div class="kpi-value">{{ fmtWan(bmData.totalContractAmount) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #f59e0b"><SwapOutlined /></div><div class="kpi-body"><div class="kpi-title">合同变更</div><div class="kpi-value">{{ fmtWan(bmData.contractChangeAmount) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #8b5cf6"><FileTextOutlined /></div><div class="kpi-body"><div class="kpi-title">签证变更</div><div class="kpi-value">{{ fmtWan(bmData.varOrderAmount) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #22c55e"><FundOutlined /></div><div class="kpi-body"><div class="kpi-title">结算进度</div><div class="kpi-value">{{ bmData.settlementProgress }}</div></div></div>
        </div>
        <div class="role-analysis-grid">
          <div class="panel role-panel"><div class="panel-header">合同经营概览</div><v-chart :option="bmBusinessOption" autoresize class="role-chart" /></div>
          <div class="panel role-panel"><div class="panel-header">变更签证分析</div><v-chart :option="bmChangeOption" autoresize class="role-chart" /></div>
          <div class="panel role-panel"><div class="panel-header">结算收付概览</div><v-chart :option="bmSettlementOption" autoresize class="role-chart" /></div>
        </div>
        <div class="role-table-grid">
          <div class="panel role-panel"><div class="panel-header">近期合同变更</div><a-table :columns="bmChangeCols" :data-source="bmData.recentChanges" :loading="loading" :pagination="false" size="small" row-key="contractId" /></div>
          <div class="panel role-panel"><div class="panel-header">待结算事项</div><a-table :columns="bmSettleCols" :data-source="bmData.settlementItems" :loading="loading" :pagination="false" size="small" row-key="projectId" /></div>
          <div class="panel role-panel"><div class="panel-header">收付款关注</div><div class="role-summary-strip"><span>付款比例</span><b>{{ bmData.paidRatio }}</b><span>分包计量</span><b>{{ fmtWan(bmData.subMeasureAmount) }} 万元</b></div></div>
        </div>
      </div>
    </template>

    <!-- ═══ COST MANAGER ═══ -->
    <template v-if="activeRole === 'cost' && costData">
      <div class="role-dashboard-grid">
        <div class="role-metric-strip">
          <div class="kpi-card"><div class="kpi-icon" style="background: #3b82f6"><AimOutlined /></div><div class="kpi-body"><div class="kpi-title">目标成本</div><div class="kpi-value">{{ fmtWan(costData.targetCost) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #f59e0b"><LineChartOutlined /></div><div class="kpi-body"><div class="kpi-title">动态成本</div><div class="kpi-value">{{ fmtWan(costData.dynamicCost) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" :style="{ background: devColor(costData.costDeviation) }"><RiseOutlined /></div><div class="kpi-body"><div class="kpi-title">成本偏差</div><div class="kpi-value" :style="{ color: devColor(costData.costDeviation) }">{{ devSign(costData.costDeviation) }}{{ fmtDeviation(costData.costDeviation) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #22c55e"><DollarOutlined /></div><div class="kpi-body"><div class="kpi-title">预计利润</div><div class="kpi-value">{{ fmtWan(costData.expectedProfit) }} <small>万元</small></div></div></div>
        </div>
        <div class="role-analysis-grid">
          <div class="panel role-panel"><div class="panel-header">成本执行概览</div><v-chart :option="costExecutionOption" autoresize class="role-chart" /></div>
          <div class="panel role-panel"><div class="panel-header">成本构成分析 <span class="panel-hint">点击柱体可下钻</span></div><v-chart :option="costCompositionOption" autoresize class="role-chart" @click="handleBarClick" /></div>
          <div class="panel role-panel"><div class="panel-header">偏差趋势分析</div><v-chart :option="costDeviationTrendOption" autoresize class="role-chart" /></div>
        </div>
        <div class="role-table-grid">
          <div class="panel role-panel"><div class="panel-header">超预算预警</div><a-table :columns="alertCols" :data-source="costData.overBudgetAlerts" :pagination="false" size="small" row-key="message" /></div>
          <div class="panel role-panel"><div class="panel-header">成本科目排行</div><v-chart :option="costBarOption" autoresize class="role-mini-chart" @click="handleBarClick" /></div>
          <div class="panel role-panel"><div class="panel-header">成本偏差明细</div><div class="role-summary-strip"><span>合同锁定成本</span><b>{{ fmtWan(costData.contractLockedCost) }} 万元</b><span>实际成本</span><b>{{ fmtWan(costData.actualCost) }} 万元</b></div></div>
        </div>
      </div>
    </template>

    <!-- ═══ FINANCE ═══ -->
    <template v-if="activeRole === 'finance' && financeData">
      <div class="role-dashboard-grid">
        <div class="role-metric-strip">
          <div class="kpi-card"><div class="kpi-icon" style="background: #ef4444"><PayCircleOutlined /></div><div class="kpi-body"><div class="kpi-title">待付款金额</div><div class="kpi-value">{{ fmtWan(financeData.pendingPaymentAmount) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #f59e0b"><ClockCircleOutlined /></div><div class="kpi-body"><div class="kpi-title">待付款笔数</div><div class="kpi-value">{{ fmtNum(financeData.pendingPaymentCount) }} <small>笔</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #3b82f6"><AuditOutlined /></div><div class="kpi-body"><div class="kpi-title">已审批未支付</div><div class="kpi-value">{{ fmtWan(financeData.approvedUnpaidAmount) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #14b8c7"><WarningOutlined /></div><div class="kpi-body"><div class="kpi-title">质保金到期</div><div class="kpi-value">{{ fmtWan(financeData.warrantyExpiringAmount) }} <small>万元</small></div></div></div>
        </div>
        <div class="role-analysis-grid">
          <div class="panel role-panel"><div class="panel-header">资金支付概览</div><v-chart :option="financePaymentOption" autoresize class="role-chart" /></div>
          <div class="panel role-panel"><div class="panel-header">付款结构分析</div><v-chart :option="financeStructureOption" autoresize class="role-chart" /></div>
          <div class="panel role-panel"><div class="panel-header">资金风险概览</div><v-chart :option="financeRiskOption" autoresize class="role-chart" /></div>
        </div>
        <div class="role-table-grid">
          <div class="panel role-panel"><div class="panel-header">待付款明细</div><a-table :columns="financePayCols" :data-source="financeData.pendingPayments" :loading="loading" :pagination="false" size="small" row-key="payRecordId" /></div>
          <div class="panel role-panel"><div class="panel-header">超比例付款</div><a-table :columns="financePayCols" :data-source="financeData.overRatioPayments" :loading="loading" :pagination="false" size="small" row-key="payRecordId" /></div>
          <div class="panel role-panel"><div class="panel-header">质保金到期</div><div class="role-summary-strip"><span>到期金额</span><b>{{ fmtWan(financeData.warrantyExpiringAmount) }} 万元</b><span>风险金额</span><b>{{ fmtWan(financeData.overRatioAmount) }} 万元</b></div></div>
        </div>
      </div>
    </template>

    <!-- ═══ MANAGEMENT ═══ -->
    <template v-if="activeRole === 'mgmt' && mgmtData">
      <div class="role-dashboard-grid">
        <div class="role-metric-strip">
          <div class="kpi-card"><div class="kpi-icon" style="background: #3b82f6"><ProjectOutlined /></div><div class="kpi-body"><div class="kpi-title">在建项目</div><div class="kpi-value">{{ fmtNum(mgmtData.activeProjectCount) }} <small>个</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #22c55e"><DollarOutlined /></div><div class="kpi-body"><div class="kpi-title">合同总额</div><div class="kpi-value">{{ fmtWan(mgmtData.totalContractAmount) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #f59e0b"><LineChartOutlined /></div><div class="kpi-body"><div class="kpi-title">动态成本</div><div class="kpi-value">{{ fmtWan(mgmtData.totalDynamicCost) }} <small>万元</small></div></div></div>
          <div class="kpi-card"><div class="kpi-icon" style="background: #ef4444"><WarningOutlined /></div><div class="kpi-body"><div class="kpi-title">风险预警</div><div class="kpi-value">{{ fmtNum(mgmtData.totalRiskCount) }} <small>项</small></div></div></div>
        </div>
        <div class="role-analysis-grid">
          <div class="panel role-panel"><div class="panel-header">项目经营总览</div><v-chart :option="mgmtOverviewOption" autoresize class="role-chart" /></div>
          <div class="panel role-panel"><div class="panel-header">项目风险分布</div><v-chart :option="mgmtRiskOption" autoresize class="role-chart" /></div>
          <div class="panel role-panel"><div class="panel-header">经营趋势概览</div><v-chart :option="mgmtTrendOption" autoresize class="role-chart" /></div>
        </div>
        <div class="role-table-grid">
          <div class="panel role-panel"><div class="panel-header">项目经营排名</div><a-table :columns="mgmtRankCols" :data-source="mgmtData.projectRankings" :loading="loading" :pagination="false" size="small" row-key="projectId" /></div>
          <div class="panel role-panel"><div class="panel-header">重大风险</div><a-table :columns="alertCols" :data-source="mgmtData.majorRisks" :pagination="false" size="small" row-key="message" /></div>
          <div class="panel role-panel"><div class="panel-header">逾期事项（&gt;7天）</div><a-table :columns="pmTaskCols" :data-source="mgmtData.overdueItems" :pagination="false" size="small" row-key="taskId" /></div>
        </div>
      </div>
    </template>

    <div v-if="!loading && needsProject(activeRole) && !selectedProjectId" class="empty-page">
      <ProjectOutlined style="font-size: 48px; color: #d1d5db; margin-bottom: 16px" />
      <div>请选择一个项目查看仪表盘数据</div>
    </div>

    <a-modal
      v-model:open="drillVisible"
      :title="drillSubject ? `成本明细 - ${drillSubject.costSubjectName}` : '成本明细'"
      :footer="null"
      width="860px"
      @cancel="closeDrill"
    >
      <template v-if="drillSubject">
        <div class="drill-summary">
          <span>目标成本：<b>{{ fmtWan(drillSubject.targetCost) }}</b> 万元</span>
          <span>实际成本：<b>{{ fmtWan(drillSubject.actualCost) }}</b> 万元</span>
          <span>
            成本偏差：<b :style="{ color: devColor(drillSubject.costDeviation) }">
              {{ devSign(drillSubject.costDeviation) }}{{ fmtDeviation(drillSubject.costDeviation) }} 万元
            </b>
          </span>
        </div>
        <a-table
          v-if="drillChildren.length"
          :columns="drillCols"
          :data-source="drillChildren"
          :pagination="false"
          size="small"
          row-key="costSubjectId"
          style="margin-top: 16px"
        />
        <div v-else style="margin-top: 16px; color: #9ca3af; text-align: center">
          该科目下暂无子科目数据（已达第2级）
        </div>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.dashboard {
  min-height: 100%;
}

.dashboard-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.breadcrumb {
  margin-bottom: 6px;
}

.project-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.project-field label {
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.role-tabs {
  margin: 14px 0;
}

.role-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.role-tabs :deep(.ant-tabs-tab) {
  padding: 8px 14px;
  font-size: 13px;
}

.kpi-grid,
.role-metric-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(178px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.kpi-card {
  min-height: 88px;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
  display: flex;
  gap: 12px;
  overflow: hidden;
}

.kpi-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  color: #fff;
  display: grid;
  flex-shrink: 0;
  font-size: 15px;
  place-items: center;
}

.kpi-body {
  min-width: 0;
  flex: 1;
}

.kpi-title {
  margin-bottom: 5px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 500;
}

.kpi-value {
  color: var(--text);
  font-size: 22px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.kpi-delta {
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
}

.kpi-delta.danger {
  color: var(--error);
}

.kpi-delta.success {
  color: var(--success);
}

.panel,
.role-panel {
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.panel-header {
  min-height: 44px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
}

.panel-hint {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 400;
}

.pm-reference-grid,
.role-analysis-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1.2fr) minmax(260px, 0.9fr) minmax(320px, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}

.pm-bottom-grid,
.role-table-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.pm-chart,
.pm-donut-chart,
.role-chart {
  width: 100%;
  height: 240px;
}

.role-mini-chart {
  width: 100%;
  height: 210px;
}

.pm-table-panel,
.role-panel {
  min-width: 0;
}

.pm-table-panel :deep(.ant-table),
.role-panel :deep(.ant-table) {
  font-size: 13px;
}

.pm-table-panel :deep(.ant-table-thead > tr > th),
.role-panel :deep(.ant-table-thead > tr > th) {
  color: var(--text-secondary);
  background: var(--surface-subtle);
  font-size: 12px;
  font-weight: 700;
}

.pm-table-panel :deep(.ant-table-tbody > tr > td),
.role-panel :deep(.ant-table-tbody > tr > td) {
  padding-top: 8px;
  padding-bottom: 8px;
}

.role-summary-strip {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 10px 12px;
  padding: 18px 16px;
  color: var(--text-secondary);
  font-size: 13px;
}

.role-summary-strip b {
  color: var(--text);
  font-variant-numeric: tabular-nums;
}

.empty-page {
  padding: 80px 20px;
  text-align: center;
  color: #9ca3af;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.drill-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 20px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .pm-reference-grid,
  .pm-bottom-grid,
  .role-analysis-grid,
  .role-table-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .dashboard-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .project-field {
    width: 100%;
    align-items: flex-start;
    flex-direction: column;
  }

  .project-field :deep(.ant-select) {
    width: 100% !important;
  }

  .pm-chart,
  .pm-donut-chart,
  .role-chart,
  .role-mini-chart {
    height: 220px;
  }
}
</style>
