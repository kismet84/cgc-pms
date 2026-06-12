<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import VChart from 'vue-echarts'
import {
  FileTextOutlined,
  DollarOutlined,
  PayCircleOutlined,
  WalletOutlined,
  ClockCircleOutlined,
  AimOutlined,
  LockOutlined,
  LineChartOutlined,
  WarningOutlined,
  ProjectOutlined,
  AuditOutlined,
  SwapOutlined,
  RiseOutlined,
  TrophyOutlined,
  ExclamationCircleOutlined,
  FundOutlined,
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
  DashboardRole,
  ProjectManagerDashboardVO,
  BusinessManagerDashboardVO,
  CostManagerDashboardVO,
  FinanceDashboardVO,
  ManagementDashboardVO,
  CostBreakdownVO,
  SubjectBreakdown,
} from '@/types/dashboard'

/* ── User & Role ── */
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

/* ── Project Selector ── */
const projectList = ref<ProjectVO[]>([])
const selectedProjectId = ref<string | undefined>(undefined)

async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 500 })
    projectList.value = res.records
    if (projectList.value.length > 0 && !selectedProjectId.value) {
      selectedProjectId.value = projectList.value[0].id
    }
  } catch {
    projectList.value = []
  }
}

/* ── Data per view ── */
const pmData = ref<ProjectManagerDashboardVO | null>(null)
const bmData = ref<BusinessManagerDashboardVO | null>(null)
const costData = ref<CostManagerDashboardVO | null>(null)
const financeData = ref<FinanceDashboardVO | null>(null)
const mgmtData = ref<ManagementDashboardVO | null>(null)
const costBreakdown = ref<CostBreakdownVO | null>(null)
const loading = ref(false)

/* ── Drill-down ── */
const drillSubject = ref<SubjectBreakdown | null>(null)
const drillVisible = ref(false)
const drillChildren = ref<SubjectBreakdown[]>([])

/* ── Fetch logic ── */
function needsProject(role: DashboardRole) {
  return role !== 'mgmt'
}

async function fetchViewData() {
  const pid = selectedProjectId.value
  if (needsProject(activeRole.value) && !pid) {
    pmData.value = null; bmData.value = null; costData.value = null
    financeData.value = null; mgmtData.value = null; costBreakdown.value = null
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
  } catch {
    message.error('加载仪表盘数据失败')
  } finally {
    loading.value = false
  }
}

watch([activeRole, selectedProjectId], () => {
  fetchViewData()
})

/* ── Formatters ── */
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
  return (Math.abs(n) / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
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

/* ── Cost Manager Charts ── */
const costBarOption = computed(() => {
  const subs = costBreakdown.value?.subjectBreakdowns ?? []
  return {
    tooltip: {
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
      valueFormatter: (v: number) => (v / 10000).toFixed(2) + ' 万元',
    },
    legend: {
      data: ['目标成本', '合同锁定成本', '实际成本'],
      bottom: 0,
    },
    grid: { left: 12, right: 24, top: 20, bottom: 40 },
    xAxis: {
      type: 'category' as const,
      data: subs.map(s => s.costSubjectName),
      axisLabel: { rotate: 20, fontSize: 11 },
    },
    yAxis: {
      type: 'value' as const,
      name: '万元',
      axisLabel: { formatter: (v: number) => (v / 10000).toFixed(0) },
    },
    series: [
      {
        name: '目标成本', type: 'bar', stack: 'cost',
        data: subs.map(s => parseFloat(s.targetCost) || 0),
        itemStyle: { color: '#3b82f6' }, barMaxWidth: 36,
      },
      {
        name: '合同锁定成本', type: 'bar', stack: 'cost',
        data: subs.map(s => parseFloat(s.contractLockedCost) || 0),
        itemStyle: { color: '#f59e0b' }, barMaxWidth: 36,
      },
      {
        name: '实际成本', type: 'bar', stack: 'cost',
        data: subs.map(s => parseFloat(s.actualCost) || 0),
        itemStyle: { color: '#ef4444', borderRadius: [6, 6, 0, 0] }, barMaxWidth: 36,
      },
    ],
  }
})

const costLineOption = computed(() => {
  const subs = costBreakdown.value?.subjectBreakdowns ?? []
  return {
    tooltip: {
      trigger: 'axis' as const,
      valueFormatter: (v: number) => (v / 10000).toFixed(2) + ' 万元',
    },
    legend: {
      data: ['合同收入(产值)', '动态成本', '目标成本'],
      bottom: 0,
    },
    grid: { left: 12, right: 24, top: 20, bottom: 40 },
    xAxis: {
      type: 'category' as const,
      data: subs.map(s => s.costSubjectName),
      axisLabel: { rotate: 20, fontSize: 11 },
    },
    yAxis: {
      type: 'value' as const,
      name: '万元',
      axisLabel: { formatter: (v: number) => (v / 10000).toFixed(0) },
    },
    series: [
      {
        name: '合同收入(产值)', type: 'line',
        data: subs.map(() => {
          const income = parseFloat(costData.value?.contractIncome ?? '0')
          const totalSubjects = subs.length || 1
          return income / totalSubjects
        }),
        smooth: true,
        lineStyle: { color: '#3b82f6', width: 2 },
        itemStyle: { color: '#3b82f6' },
        symbol: 'circle', symbolSize: 6,
      },
      {
        name: '动态成本', type: 'line',
        data: subs.map(s => parseFloat(s.dynamicCost) || 0),
        smooth: true,
        lineStyle: { color: '#f59e0b', width: 2 },
        itemStyle: { color: '#f59e0b' },
        symbol: 'circle', symbolSize: 6,
      },
      {
        name: '目标成本', type: 'line',
        data: subs.map(s => parseFloat(s.targetCost) || 0),
        smooth: true,
        lineStyle: { color: '#22c55e', width: 2, type: 'dashed' as const },
        itemStyle: { color: '#22c55e' },
        symbol: 'diamond', symbolSize: 6,
      },
    ],
  }
})

/* ── Drill-down ── */
function handleBarClick(params: { name?: string }) {
  if (!params.name || !costBreakdown.value) return
  const subs = costBreakdown.value.subjectBreakdowns
  const clicked = subs.find(s => s.costSubjectName === params.name)
  if (!clicked) return
  drillSubject.value = clicked
  drillChildren.value = subs.filter(
    s => s.parentSubjectId === clicked.costSubjectId && s.level === 2,
  )
  drillVisible.value = true
}

function closeDrill() {
  drillVisible.value = false
  drillSubject.value = null
  drillChildren.value = []
}

/* ── Table Columns ── */
const pmTaskCols = [
  { title: '任务标题', dataIndex: 'title', ellipsis: true },
  { title: '业务类型', dataIndex: 'businessType', width: 100 },
  { title: '接收时间', dataIndex: 'receivedAt', width: 160 },
]
const pmProjectCols = [
  { title: '项目名称', dataIndex: 'projectName', ellipsis: true },
  { title: '项目编号', dataIndex: 'projectCode', width: 120 },
  { title: '状态', dataIndex: 'status', width: 80 },
]
const pmContractCols = [
  { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
  { title: '合同编号', dataIndex: 'contractCode', width: 150 },
  { title: '到期日', dataIndex: 'endDate', width: 110 },
  { title: '金额(万元)', dataIndex: 'contractAmount', width: 120, align: 'right' as const },
]
const bmChangeCols = [
  { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
  { title: '合同编号', dataIndex: 'contractCode', width: 150 },
  { title: '合同金额(万元)', dataIndex: 'contractAmount', width: 130, align: 'right' as const },
  { title: '当前金额(万元)', dataIndex: 'currentAmount', width: 130, align: 'right' as const },
]
const bmSettleCols = [
  { title: '项目名称', dataIndex: 'projectName', ellipsis: true },
  { title: '结算状态', dataIndex: 'status', width: 100 },
]
const financePayCols = [
  { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
  { title: '合作方', dataIndex: 'partnerName', width: 120 },
  { title: '付款金额(万元)', dataIndex: 'payAmount', width: 130, align: 'right' as const },
  { title: '付款日期', dataIndex: 'payDate', width: 110 },
  { title: '状态', dataIndex: 'payStatus', width: 90 },
]
const alertCols = [
  { title: '预警类型', dataIndex: 'alertType', width: 100 },
  { title: '严重程度', dataIndex: 'severity', width: 90 },
  { title: '预警信息', dataIndex: 'message', ellipsis: true },
  { title: '项目', dataIndex: 'projectName', width: 140 },
  { title: '触发时间', dataIndex: 'triggeredAt', width: 160 },
]
const mgmtRankCols = [
  { title: '项目名称', dataIndex: 'projectName', width: 180, ellipsis: true },
  { title: '项目编号', dataIndex: 'projectCode', width: 120 },
  { title: '合同收入(万元)', dataIndex: 'contractIncome', width: 140, align: 'right' as const },
  { title: '动态成本(万元)', dataIndex: 'dynamicCost', width: 140, align: 'right' as const },
  { title: '预计利润(万元)', dataIndex: 'expectedProfit', width: 140, align: 'right' as const },
  { title: '成本偏差(万元)', dataIndex: 'costDeviation', width: 140, align: 'right' as const },
  { title: '已付款(万元)', dataIndex: 'paidAmount', width: 130, align: 'right' as const },
  { title: '待办', dataIndex: 'pendingTaskCount', width: 70, align: 'center' as const },
  { title: '风险', dataIndex: 'riskCount', width: 70, align: 'center' as const },
]
const drillCols = [
  { title: '科目名称', dataIndex: 'costSubjectName', width: 200 },
  { title: '目标成本(万元)', dataIndex: 'targetCost', width: 130, align: 'right' as const },
  { title: '锁定成本(万元)', dataIndex: 'contractLockedCost', width: 130, align: 'right' as const },
  { title: '实际成本(万元)', dataIndex: 'actualCost', width: 130, align: 'right' as const },
  { title: '动态成本(万元)', dataIndex: 'dynamicCost', width: 130, align: 'right' as const },
  { title: '成本偏差(万元)', dataIndex: 'costDeviation', width: 140, align: 'right' as const },
]

onMounted(() => {
  fetchProjects()
})
</script>

<template>
  <div class="dashboard">
    <a-breadcrumb class="breadcrumb">
      <a-breadcrumb-item>首页</a-breadcrumb-item>
      <a-breadcrumb-item>驾驶舱</a-breadcrumb-item>
    </a-breadcrumb>

    <!-- Project selector (hidden for mgmt) -->
    <div v-if="activeRole !== 'mgmt'" class="project-bar">
      <div class="project-field">
        <label>选择项目：</label>
        <a-select
          v-model:value="selectedProjectId"
          placeholder="请选择项目"
          style="width: 260px"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
      </div>
    </div>

    <!-- Role tabs -->
    <a-tabs v-model:activeKey="activeRole" class="role-tabs" size="small">
      <a-tab-pane v-for="role in availableRoles" :key="role" :tab="roleLabel[role]" />
    </a-tabs>

    <!-- ═══ PROJECT MANAGER ═══ -->
    <template v-if="activeRole === 'pm' && pmData">
      <div class="kpi-grid kpi-grid-4">
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#3b82f6"><AuditOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">待办任务</div>
            <div class="kpi-value">{{ fmtNum(pmData.pendingTaskCount) }} <small>项</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#ef4444"><WarningOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">滞后项目</div>
            <div class="kpi-value">{{ fmtNum(pmData.laggingProjectCount) }} <small>个</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#f59e0b"><ClockCircleOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">待审批</div>
            <div class="kpi-value">{{ fmtNum(pmData.pendingApprovalCount) }} <small>项</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#8b5cf6"><FileTextOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">临期合同</div>
            <div class="kpi-value">{{ fmtNum(pmData.expiringContractCount) }} <small>份</small></div>
          </div>
        </div>
      </div>

      <div class="chart-row">
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">待办任务</div>
            <a-table :columns="pmTaskCols" :data-source="pmData.pendingTasks" :loading="loading" :pagination="false" size="small" row-key="taskId" />
            <div v-if="!pmData.pendingTasks.length" class="empty-hint">暂无待办任务</div>
          </div>
          <div class="panel" style="margin-top:14px">
            <div class="panel-header">待审批</div>
            <a-table :columns="pmTaskCols" :data-source="pmData.pendingApprovals" :loading="loading" :pagination="false" size="small" row-key="taskId" />
            <div v-if="!pmData.pendingApprovals.length" class="empty-hint">暂无待审批事项</div>
          </div>
        </div>
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">滞后项目</div>
            <a-table :columns="pmProjectCols" :data-source="pmData.laggingProjects" :loading="loading" :pagination="false" size="small" row-key="projectId" />
            <div v-if="!pmData.laggingProjects.length" class="empty-hint">暂无滞后项目</div>
          </div>
          <div class="panel" style="margin-top:14px">
            <div class="panel-header">临期合同（30天内到期）</div>
            <a-table :columns="pmContractCols" :data-source="pmData.expiringContracts" :loading="loading" :pagination="false" size="small" row-key="contractId" />
            <div v-if="!pmData.expiringContracts.length" class="empty-hint">暂无临期合同</div>
          </div>
        </div>
      </div>
    </template>

    <!-- ═══ BUSINESS MANAGER ═══ -->
    <template v-if="activeRole === 'bm' && bmData">
      <div class="kpi-grid kpi-grid-6">
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#3b82f6"><DollarOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">合同总额</div>
            <div class="kpi-value">{{ fmtWan(bmData.totalContractAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#f59e0b"><SwapOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">合同变更</div>
            <div class="kpi-value">{{ fmtWan(bmData.contractChangeAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#8b5cf6"><FileTextOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">签证变更</div>
            <div class="kpi-value">{{ fmtWan(bmData.varOrderAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#14b8c7"><ProjectOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">分包计量</div>
            <div class="kpi-value">{{ fmtWan(bmData.subMeasureAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#22c55e"><PayCircleOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">付款比例</div>
            <div class="kpi-value">{{ bmData.paidRatio }}</div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#ef4444"><FundOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">结算进度</div>
            <div class="kpi-value">{{ bmData.settlementProgress }}</div>
          </div>
        </div>
      </div>

      <div class="chart-row">
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">近期合同变更</div>
            <a-table :columns="bmChangeCols" :data-source="bmData.recentChanges" :loading="loading" :pagination="false" size="small" row-key="contractId" />
            <div v-if="!bmData.recentChanges.length" class="empty-hint">暂无变更记录</div>
          </div>
        </div>
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">结算事项</div>
            <a-table :columns="bmSettleCols" :data-source="bmData.settlementItems" :loading="loading" :pagination="false" size="small" row-key="projectId" />
            <div v-if="!bmData.settlementItems.length" class="empty-hint">暂无结算事项</div>
          </div>
        </div>
      </div>
    </template>

    <!-- ═══ COST MANAGER ═══ -->
    <template v-if="activeRole === 'cost' && costData">
      <div class="kpi-grid kpi-grid-6">
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#3b82f6"><AimOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">目标成本</div>
            <div class="kpi-value">{{ fmtWan(costData.targetCost) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#f59e0b"><LineChartOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">动态成本</div>
            <div class="kpi-value">{{ fmtWan(costData.dynamicCost) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" :style="{ background: devColor(costData.costDeviation) }"><RiseOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">成本偏差</div>
            <div class="kpi-value" :style="{ color: devColor(costData.costDeviation) }">
              {{ devSign(costData.costDeviation) }}{{ fmtDeviation(costData.costDeviation) }} <small>万元</small>
            </div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#22c55e"><DollarOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">预计利润</div>
            <div class="kpi-value">{{ fmtWan(costData.expectedProfit) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#8b5cf6"><LockOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">合同锁定成本</div>
            <div class="kpi-value">{{ fmtWan(costData.contractLockedCost) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#ef4444"><WalletOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">实际成本</div>
            <div class="kpi-value">{{ fmtWan(costData.actualCost) }} <small>万元</small></div>
          </div>
        </div>
      </div>

      <!-- ECharts -->
      <div class="chart-row">
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">成本构成（按科目） <span class="panel-hint">点击柱体可下钻</span></div>
            <v-chart :option="costBarOption" autoresize style="height:340px" @click="handleBarClick" />
          </div>
        </div>
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">产值趋势</div>
            <v-chart :option="costLineOption" autoresize style="height:340px" />
          </div>
        </div>
      </div>

      <div v-if="costData.overBudgetAlerts.length" class="panel" style="margin-top:14px">
        <div class="panel-header">超预算预警</div>
        <a-table :columns="alertCols" :data-source="costData.overBudgetAlerts" :pagination="false" size="small" row-key="message" />
      </div>
    </template>

    <!-- ═══ FINANCE ═══ -->
    <template v-if="activeRole === 'finance' && financeData">
      <div class="kpi-grid kpi-grid-5">
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#ef4444"><PayCircleOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">待付款金额</div>
            <div class="kpi-value">{{ fmtWan(financeData.pendingPaymentAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#f59e0b"><ClockCircleOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">待付款笔数</div>
            <div class="kpi-value">{{ fmtNum(financeData.pendingPaymentCount) }} <small>笔</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#3b82f6"><AuditOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">已审批未支付</div>
            <div class="kpi-value">{{ fmtWan(financeData.approvedUnpaidAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#8b5cf6"><ExclamationCircleOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">超比例付款</div>
            <div class="kpi-value">{{ fmtWan(financeData.overRatioAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#14b8c7"><WarningOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">质保金到期</div>
            <div class="kpi-value">{{ fmtWan(financeData.warrantyExpiringAmount) }} <small>万元</small></div>
          </div>
        </div>
      </div>

      <div class="chart-row">
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">待付款明细</div>
            <a-table :columns="financePayCols" :data-source="financeData.pendingPayments" :loading="loading" :pagination="false" size="small" row-key="payRecordId" />
            <div v-if="!financeData.pendingPayments.length" class="empty-hint">暂无待付款</div>
          </div>
        </div>
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">超比例付款</div>
            <a-table :columns="financePayCols" :data-source="financeData.overRatioPayments" :loading="loading" :pagination="false" size="small" row-key="payRecordId" />
            <div v-if="!financeData.overRatioPayments.length" class="empty-hint">暂无超比例付款</div>
          </div>
        </div>
      </div>
    </template>

    <!-- ═══ MANAGEMENT ═══ -->
    <template v-if="activeRole === 'mgmt' && mgmtData">
      <div class="kpi-grid kpi-grid-7">
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#3b82f6"><ProjectOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">在建项目</div>
            <div class="kpi-value">{{ fmtNum(mgmtData.activeProjectCount) }} <small>个</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#22c55e"><DollarOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">合同总额</div>
            <div class="kpi-value">{{ fmtWan(mgmtData.totalContractAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#f59e0b"><LineChartOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">动态成本</div>
            <div class="kpi-value">{{ fmtWan(mgmtData.totalDynamicCost) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#8b5cf6"><TrophyOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">预计利润</div>
            <div class="kpi-value">{{ fmtWan(mgmtData.totalExpectedProfit) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#14b8c7"><PayCircleOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">已付款</div>
            <div class="kpi-value">{{ fmtWan(mgmtData.totalPaidAmount) }} <small>万元</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#ef4444"><ClockCircleOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">待办任务</div>
            <div class="kpi-value">{{ fmtNum(mgmtData.totalPendingTaskCount) }} <small>项</small></div>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon" style="background:#ef4444"><WarningOutlined /></div>
          <div class="kpi-body">
            <div class="kpi-title">风险预警</div>
            <div class="kpi-value">{{ fmtNum(mgmtData.totalRiskCount) }} <small>项</small></div>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-header">项目经营排名</div>
        <a-table :columns="mgmtRankCols" :data-source="mgmtData.projectRankings" :loading="loading" :pagination="false" size="small" row-key="projectId" :scroll="{ y: 400 }" />
      </div>

      <div class="chart-row" style="margin-top:14px">
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">重大风险</div>
            <div v-if="!mgmtData.majorRisks.length" class="empty-hint">暂无风险预警</div>
            <a-table v-else :columns="alertCols" :data-source="mgmtData.majorRisks" :pagination="false" size="small" row-key="message" />
          </div>
        </div>
        <div class="chart-col">
          <div class="panel">
            <div class="panel-header">逾期事项（>7天）</div>
            <div v-if="!mgmtData.overdueItems.length" class="empty-hint">暂无逾期事项</div>
            <a-table v-else :columns="pmTaskCols" :data-source="mgmtData.overdueItems" :pagination="false" size="small" row-key="taskId" />
          </div>
        </div>
      </div>
    </template>

    <!-- Empty state -->
    <div v-if="!loading && needsProject(activeRole) && !selectedProjectId" class="empty-page">
      <ProjectOutlined style="font-size:48px;color:#d1d5db;margin-bottom:16px" />
      <div>请选择一个项目查看仪表盘数据</div>
    </div>

    <!-- ═══ DRILL-DOWN MODAL ═══ -->
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
          <span>合同锁定成本：<b>{{ fmtWan(drillSubject.contractLockedCost) }}</b> 万元</span>
          <span>实际成本：<b>{{ fmtWan(drillSubject.actualCost) }}</b> 万元</span>
          <span>动态成本：<b>{{ fmtWan(drillSubject.dynamicCost) }}</b> 万元</span>
          <span>
            成本偏差：<b :style="{ color: devColor(drillSubject.costDeviation) }">
              {{ devSign(drillSubject.costDeviation) }}{{ fmtDeviation(drillSubject.costDeviation) }} 万元
            </b>
          </span>
        </div>
        <div v-if="drillChildren.length" style="margin-top:16px">
          <div style="font-size:14px;font-weight:600;color:#111827;margin-bottom:8px">子科目明细</div>
          <a-table :columns="drillCols" :data-source="drillChildren" :pagination="false" size="small" row-key="costSubjectId">
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'targetCost'">{{ fmtWan(record.targetCost) }}</template>
              <template v-else-if="column.dataIndex === 'contractLockedCost'">{{ fmtWan(record.contractLockedCost) }}</template>
              <template v-else-if="column.dataIndex === 'actualCost'">{{ fmtWan(record.actualCost) }}</template>
              <template v-else-if="column.dataIndex === 'dynamicCost'">{{ fmtWan(record.dynamicCost) }}</template>
              <template v-else-if="column.dataIndex === 'costDeviation'">
                <span :style="{ color: devColor(record.costDeviation), fontWeight: 600 }">
                  {{ devSign(record.costDeviation) }}{{ fmtDeviation(record.costDeviation) }}
                </span>
              </template>
            </template>
          </a-table>
        </div>
        <div v-else style="margin-top:16px;color:#9ca3af;text-align:center">
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

.breadcrumb {
  margin-bottom: 14px;
}

/* ── Project bar ── */
.project-bar {
  margin-bottom: 10px;
}
.project-field {
  display: flex;
  align-items: center;
  gap: 10px;
}
.project-field label {
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
}

/* ── Role tabs ── */
.role-tabs {
  margin-bottom: 14px;
}
.role-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

/* ── KPI Grid ── */
.kpi-grid {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
}
.kpi-grid-4 { grid-template-columns: repeat(4, 1fr); }
.kpi-grid-5 { grid-template-columns: repeat(5, 1fr); }
.kpi-grid-6 { grid-template-columns: repeat(6, 1fr); }
.kpi-grid-7 { grid-template-columns: repeat(7, 1fr); }

.kpi-card {
  height: 96px;
  padding: 16px 18px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
  overflow: hidden;
}

.kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}

.kpi-body {
  flex: 1;
  min-width: 0;
}

.kpi-title {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 21px;
  font-weight: 800;
  color: #111827;
  letter-spacing: 0.2px;
}

.kpi-value small {
  font-size: 13px;
  font-weight: 500;
  margin-left: 4px;
  color: #6b7280;
}

/* ── Panel ── */
.panel {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
  overflow: hidden;
}

.panel-header {
  padding: 14px 20px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-hint {
  font-size: 12px;
  font-weight: 400;
  color: #9ca3af;
}

/* ── Chart row ── */
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.chart-col {
  display: flex;
  flex-direction: column;
}

/* ── Empty hints ── */
.empty-hint {
  padding: 40px 20px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.empty-page {
  padding: 80px 20px;
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
}

/* ── Drill-down modal ── */
.drill-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 20px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  font-size: 13px;
}
.drill-summary span {
  white-space: nowrap;
}
.drill-summary b {
  color: #111827;
}
</style>
