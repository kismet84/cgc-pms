<script setup lang="ts">
import type {
  AlertProcessStatus,
  AlertRecord,
  CostBreakdownVO,
  CostManagerDashboardVO,
  DashboardDataByRole,
  DashboardRole,
  FinanceDashboardVO,
  SubjectBreakdown,
} from '@cgc-pms/frontend-contracts'
import { hasPermission, resolveDashboardRoles } from '@cgc-pms/frontend-contracts'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2Dialog,
  V2GlassButton,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
  useToastMessage,
} from '@/components'
import DomainNavigationIcon from '@/components/DomainNavigationIcon.vue'
import { loadCostBreakdown, loadDashboard } from '@/services/dashboard'
import {
  acknowledgeAlert,
  evaluateAlerts,
  loadAlerts,
  markAlertRead,
  updateAlertStatus,
} from '@/services/alerts'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import {
  DASHBOARD_ROLE_LABELS,
  DASHBOARD_RISK_LABELS,
  type DashboardRiskLevel,
  alertRiskLevel,
  compactDashboardValue,
  dashboardActivityItems,
  dashboardHealth,
  dashboardMetrics,
  dashboardStatusLabel,
  formatAmount,
  formatDashboardMessage,
  primaryRiskItems,
} from './model'
import { alertRuleLabel, alertStatusLabel, severityTone } from '../workbench/alert-report-model'
import DashboardGauge from './DashboardGauge.vue'
import DashboardTrendChart from './DashboardTrendChart.vue'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspace = useWorkspaceStore()

const allowedRoles = computed(() => resolveDashboardRoles(session.roles, session.permissions))
const requestedRole = typeof route.query.role === 'string' ? route.query.role : ''
const selectedRole = ref<DashboardRole>(
  allowedRoles.value.includes(requestedRole as DashboardRole)
    ? (requestedRole as DashboardRole)
    : (allowedRoles.value[0] ?? 'pm'),
)
const data = ref<DashboardDataByRole[DashboardRole] | null>(null)
const alertRows = ref<AlertRecord[]>([])
const alertLoading = ref(false)
const alertError = ref('')
const alertActionLoading = ref(false)
const alertActionMessage = ref('')
const alertEvaluationMessage = useToastMessage('info', '操作结果')
const selectedAlert = ref<AlertRecord | null>(null)
const targetAlertStatus = ref<AlertProcessStatus>('PROCESSED')
const alertRemark = ref('')
const loading = ref(false)
const error = ref(false)
const breakdown = ref<CostBreakdownVO | null>(null)
const breakdownLoading = ref(false)
const breakdownError = ref(false)
const expandedSubjects = ref(new Set<string>())
const expandedFinanceContracts = ref(new Set<string>())
const trendRange = ref<'year' | 'half' | 'quarter'>('year')
const riskFilter = ref<'all' | DashboardRiskLevel>('all')
const riskPage = ref(1)
const riskPageSize = 10
const riskFilterOptions = [
  { value: 'all', label: '全部预警' },
  { value: 'high', label: '高' },
  { value: 'medium', label: '中' },
  { value: 'low', label: '低' },
  { value: 'other', label: '其他' },
]
const refreshToken = ref(0)
let generation = 0
let controller: AbortController | null = null
let alertController: AbortController | null = null

const access = computed(() => ({ roles: session.roles, permissions: session.permissions }))
const canViewAlerts = computed(() => hasPermission(session.permissions, 'alert:view'))
const canEditAlerts = computed(() => hasPermission(session.permissions, 'alert:edit'))
const canEvaluateAlerts = computed(() => hasPermission(session.permissions, 'alert:evaluate'))
const currentProject = computed(() =>
  workspace.projects.find((item) => item.value === workspace.selectedProjectId),
)
const projectUnsupported = computed(
  () =>
    selectedRole.value === 'pm' &&
    currentProject.value?.status !== undefined &&
    currentProject.value.status !== 'ACTIVE',
)
const currentProjectLabel = computed(
  () => currentProject.value?.label ?? (selectedRole.value === 'mgmt' ? '租户汇总' : '全部项目'),
)
const selectedAlertProjectLabel = computed(
  () =>
    workspace.projects.find((item) => item.value === selectedAlert.value?.projectId)?.label ?? '—',
)
const metrics = computed(() => (data.value ? dashboardMetrics(selectedRole.value, data.value) : []))
const displayMetrics = computed(() =>
  metrics.value.map((metric) => ({ ...metric, ...compactDashboardValue(metric.value) })),
)
const health = computed(() => (data.value ? dashboardHealth(selectedRole.value, data.value) : null))
const derivedRisks = computed(() =>
  data.value ? primaryRiskItems(selectedRole.value, data.value) : [],
)
const alertRisks = computed(() =>
  alertRows.value.map((alert) => ({
    id: alert.id,
    title: alert.message,
    meta: `${alertRuleLabel(alert.ruleType)} · ${alert.triggeredAt}`,
    status: alertStatusLabel(alert.processStatus),
    riskLevel: alertRiskLevel(alert.severity),
    alert,
  })),
)
const risks = computed(() =>
  (canViewAlerts.value ? alertRisks.value : derivedRisks.value).map((item) => ({
    ...item,
    title: formatDashboardMessage(item.title),
    status: dashboardStatusLabel(item.status),
  })),
)
const showRiskValueColumn = computed(() =>
  risks.value.some((item) => 'value' in item && Boolean(item.value)),
)
const riskTableColumnCount = computed(() => (showRiskValueColumn.value ? 6 : 5))
const activityItems = computed(() =>
  data.value
    ? dashboardActivityItems(selectedRole.value, data.value)
        .slice(0, 6)
        .map((item) => ({
          ...item,
          title: formatDashboardMessage(item.title),
          status: dashboardStatusLabel(item.status),
        }))
    : [],
)
const costData = computed(() =>
  selectedRole.value === 'cost' && data.value ? (data.value as CostManagerDashboardVO) : null,
)
const financeData = computed(() =>
  selectedRole.value === 'finance' && data.value ? (data.value as FinanceDashboardVO) : null,
)
const trendDefinition = computed(() => {
  if (costData.value) {
    return {
      title: '经营趋势',
      ariaLabel: '目标成本、动态成本和成本偏差月度趋势图',
      caption: '经营趋势精确数据',
      series: [
        { key: 'targetCost', label: '目标成本', color: '--v2-chart-1' },
        { key: 'dynamicCost', label: '动态成本', color: '--v2-chart-2' },
        { key: 'costDeviation', label: '成本偏差', color: '--v2-chart-3' },
      ],
      points: costData.value.trendPoints.map((point) => ({
        month: point.month,
        values: {
          targetCost: point.targetCost,
          dynamicCost: point.dynamicCost,
          costDeviation: point.costDeviation,
        },
      })),
    }
  }
  if (financeData.value) {
    return {
      title: '资金支付趋势',
      ariaLabel: '本月支付、累计支付和处理中付款月度趋势图',
      caption: '资金支付趋势精确数据',
      series: [
        { key: 'cashOutflowAmount', label: '本月支付', color: '--v2-chart-1' },
        { key: 'cumulativePaidAmount', label: '累计支付', color: '--v2-chart-2' },
        { key: 'pendingPaymentAmount', label: '处理中付款', color: '--v2-chart-3' },
      ],
      points: (financeData.value.trendPoints ?? []).map((point) => ({
        month: point.month,
        values: {
          cashOutflowAmount: point.cashOutflowAmount,
          cumulativePaidAmount: point.cumulativePaidAmount,
          pendingPaymentAmount: point.pendingPaymentAmount,
        },
      })),
    }
  }
  return {
    title: '经营趋势',
    ariaLabel: '经营趋势图',
    caption: '经营趋势精确数据',
    series: [],
    points: [],
  }
})
const visibleTrendPoints = computed(() => {
  const count = trendRange.value === 'quarter' ? 3 : trendRange.value === 'half' ? 6 : 12
  return trendDefinition.value.points.slice(-count)
})
const financeSummary = computed(() => {
  const item = financeData.value
  if (!item) return []
  return [
    { label: '合同金额', value: formatAmount(item.totalContractAmount) },
    { label: '累计支付', value: formatAmount(item.totalPaidAmount) },
    { label: '预算总额', value: formatAmount(item.budgetAmount) },
    { label: '预算已消耗', value: formatAmount(item.budgetConsumedAmount) },
    { label: '预算执行率', value: `${item.budgetExecutionRate}%` },
    { label: '现金流出', value: formatAmount(item.cashOutflowAmount) },
    { label: '公司资金余额', value: formatAmount(item.cashBalance) },
    { label: '项目利润', value: formatAmount(item.projectProfit) },
  ]
})
const highestRisk = computed(
  () => risks.value.find((item) => item.riskLevel === 'high') ?? risks.value[0],
)
const filteredRisks = computed(() => {
  const items =
    riskFilter.value === 'all'
      ? risks.value
      : risks.value.filter((item) => item.riskLevel === riskFilter.value)
  return items
})
const riskPageCount = computed(() =>
  Math.max(1, Math.ceil(filteredRisks.value.length / riskPageSize)),
)
const visibleRisks = computed(() => {
  const start = (riskPage.value - 1) * riskPageSize
  return filteredRisks.value.slice(start, start + riskPageSize)
})
const targetAlertStatusOptions = [
  { value: 'PROCESSED', label: '已处理' },
  { value: 'ARCHIVED', label: '已归档' },
  { value: 'INVALID', label: '已失效' },
]
const quickEntries = [
  { label: '风险待办', href: '#risk-list', domain: 'workbench' },
  { label: '经营趋势', href: '#cost-trend', domain: 'delivery' },
  { label: '成本分解', href: '#cost-breakdown', domain: 'commercial' },
  { label: '健康总览', href: '#health-overview', domain: 'finance' },
  { label: '经营指标', href: '#health-overview', domain: 'master-data' },
  { label: '预警筛选', href: '#risk-list', domain: 'supply' },
  { label: '最近打开', href: '#recent-entry', domain: 'system-management' },
] as const
const visibleSubjects = computed(() => {
  if (!breakdown.value) return []
  const roots = breakdown.value.subjectBreakdowns.filter((item) => item.level <= 1)
  return roots.flatMap((root) => [
    root,
    ...(expandedSubjects.value.has(root.costSubjectId)
      ? breakdown.value!.subjectBreakdowns.filter(
          (item) => item.level === 2 && item.parentSubjectId === root.costSubjectId,
        )
      : []),
  ])
})
const canLoadBreakdown = computed(
  () =>
    session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
    session.permissions.includes('*') ||
    session.permissions.includes('dashboard:cost-breakdown:view'),
)

watch(
  allowedRoles,
  (roles) => {
    if (!roles.includes(selectedRole.value) && roles[0]) selectedRole.value = roles[0]
  },
  { immediate: true },
)

watch(selectedRole, (role) => {
  const query = { ...route.query, role }
  void router.replace({ path: route.path, query, hash: route.hash })
})

watch(riskFilter, () => {
  riskPage.value = 1
})

watch(riskPageCount, (pageCount) => {
  if (riskPage.value > pageCount) riskPage.value = pageCount
})

watch(
  [
    selectedRole,
    () => workspace.selectedProjectId,
    () => workspace.selectedReportPeriod,
    refreshToken,
  ],
  () => void refresh(),
  { immediate: true },
)

watch(
  [
    selectedRole,
    canViewAlerts,
    () => workspace.selectedProjectId,
    () => workspace.selectedReportPeriod,
    refreshToken,
  ],
  () => void refreshAlerts(),
  { immediate: true },
)

onBeforeUnmount(() => {
  controller?.abort()
  alertController?.abort()
})

async function showHighestRisks(): Promise<void> {
  riskFilter.value = riskFilter.value === 'high' ? 'all' : 'high'
  await nextTick()
  document.getElementById('risk-list')?.scrollIntoView?.({ block: 'start' })
}

async function refreshAlerts(): Promise<void> {
  alertController?.abort()
  alertRows.value = []
  alertError.value = ''
  if (!canViewAlerts.value) return

  alertController = new AbortController()
  const currentController = alertController
  alertLoading.value = true
  try {
    const periodBounds = dashboardPeriodBounds(workspace.selectedReportPeriod)
    const result = await loadAlerts(
      {
        pageNum: 1,
        pageSize: 50,
        projectId: workspace.selectedProjectId || undefined,
        processStatus: 'OPEN',
        ...periodBounds,
      },
      currentController.signal,
    )
    if (currentController.signal.aborted) return
    const seen = new Set<string>()
    alertRows.value = result.records.flatMap((row) => {
      const normalized = {
        ...row,
        id: String(row.id),
        projectId: String(row.projectId),
      }
      const signature =
        normalized.dedupKey ??
        [
          normalized.projectId,
          normalized.ruleType,
          normalized.severity,
          normalized.message,
          normalized.triggeredAt,
          normalized.processStatus,
        ].join('\u0000')
      if (seen.has(signature)) return []
      seen.add(signature)
      return [normalized]
    })
  } catch (caught) {
    if (!currentController.signal.aborted) {
      alertError.value = isApiClientError(caught) ? caught.message : '预警列表加载失败'
    }
  } finally {
    if (!currentController.signal.aborted) alertLoading.value = false
  }
}

function dashboardPeriodBounds(period: string | null): {
  triggeredStart?: string
  triggeredEnd?: string
} {
  const match = /^(\d{4})-(\d{2})$/.exec(period ?? '')
  if (!match) return {}
  const year = Number(match[1])
  const month = Number(match[2])
  if (month < 1 || month > 12) return {}
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate()
  return {
    triggeredStart: `${period}-01T00:00:00`,
    triggeredEnd: `${period}-${String(lastDay).padStart(2, '0')}T23:59:59`,
  }
}

function openAlertDisposition(item: (typeof risks.value)[number]): void {
  if (!('alert' in item) || !item.alert) return
  selectedAlert.value = item.alert
  targetAlertStatus.value = 'PROCESSED'
  alertRemark.value = ''
  alertActionMessage.value = ''
  alertError.value = ''
}

async function runAlertAction(action: () => Promise<unknown>, message: string): Promise<void> {
  const alertId = selectedAlert.value?.id
  if (!alertId) return
  alertActionLoading.value = true
  alertError.value = ''
  try {
    await action()
    alertActionMessage.value = message
    await refreshAlerts()
    selectedAlert.value = alertRows.value.find((row) => row.id === alertId) ?? null
  } catch (caught) {
    alertError.value = isApiClientError(caught) ? caught.message : '预警处置失败'
  } finally {
    alertActionLoading.value = false
  }
}

function markSelectedAlertRead(): void {
  if (!selectedAlert.value) return
  void runAlertAction(() => markAlertRead(selectedAlert.value!.id), '已标记为已读')
}

function acknowledgeSelectedAlert(): void {
  if (!selectedAlert.value) return
  void runAlertAction(() => acknowledgeAlert(selectedAlert.value!.id, '在驾驶舱接单'), '预警已接单')
}

function disposeSelectedAlert(): void {
  if (!selectedAlert.value) return
  if (!alertRemark.value.trim()) {
    alertError.value = '处置预警必须填写处理说明'
    return
  }
  void runAlertAction(
    () =>
      updateAlertStatus(selectedAlert.value!.id, targetAlertStatus.value, alertRemark.value.trim()),
    '预警状态已更新',
  )
}

function evaluateCurrentAlerts(): void {
  alertActionLoading.value = true
  alertError.value = ''
  alertEvaluationMessage.value = ''
  void evaluateAlerts()
    .then(async (result) => {
      alertEvaluationMessage.value = `评估完成，生成 ${result.alertsGenerated} 项预警`
      await refreshAlerts()
    })
    .catch((caught: unknown) => {
      alertError.value = isApiClientError(caught) ? caught.message : '预警评估失败'
    })
    .finally(() => {
      alertActionLoading.value = false
    })
}

async function refresh(): Promise<void> {
  const currentGeneration = ++generation
  controller?.abort()
  controller = new AbortController()
  data.value = null
  breakdown.value = null
  error.value = false
  breakdownError.value = false
  expandedSubjects.value = new Set()
  expandedFinanceContracts.value = new Set()

  if (!allowedRoles.value.includes(selectedRole.value)) return
  if (projectUnsupported.value) return

  loading.value = true
  try {
    const result = await loadDashboard(
      selectedRole.value,
      {
        projectId: workspace.selectedProjectId,
        period: workspace.selectedReportPeriod,
      },
      access.value,
      controller.signal,
    )
    if (currentGeneration !== generation) return
    data.value = result
  } catch (caught) {
    if (!isAbort(caught) && currentGeneration === generation) error.value = true
    return
  } finally {
    if (currentGeneration === generation) loading.value = false
  }

  if (selectedRole.value !== 'cost' || !workspace.selectedProjectId || !canLoadBreakdown.value) {
    return
  }
  breakdownLoading.value = true
  try {
    const result = await loadCostBreakdown(
      workspace.selectedProjectId,
      access.value,
      controller.signal,
    )
    if (currentGeneration === generation) breakdown.value = result
  } catch (caught) {
    if (!isAbort(caught) && currentGeneration === generation) breakdownError.value = true
  } finally {
    if (currentGeneration === generation) breakdownLoading.value = false
  }
}

async function refreshDashboard(): Promise<void> {
  await Promise.all([refresh(), refreshAlerts()])
  if (error.value || alertError.value) {
    showToast('error', '刷新失败', '驾驶舱数据未能完整更新')
  } else if (breakdownError.value) {
    showToast('warn', '刷新完成', '驾驶舱已更新，成本分解加载失败')
  } else {
    showToast('success', '刷新成功', '驾驶舱数据已更新')
  }
}

function selectRole(role: DashboardRole): void {
  selectedRole.value = role
}

function toggleSubject(subject: SubjectBreakdown): void {
  const next = new Set(expandedSubjects.value)
  if (next.has(subject.costSubjectId)) next.delete(subject.costSubjectId)
  else next.add(subject.costSubjectId)
  expandedSubjects.value = next
}

function hasChildren(subject: SubjectBreakdown): boolean {
  return Boolean(
    breakdown.value?.subjectBreakdowns.some(
      (item) => item.level === 2 && item.parentSubjectId === subject.costSubjectId,
    ),
  )
}

function toggleFinanceContract(contractId: string): void {
  const next = new Set(expandedFinanceContracts.value)
  if (next.has(contractId)) next.delete(contractId)
  else next.add(contractId)
  expandedFinanceContracts.value = next
}

function isAbort(errorValue: unknown): boolean {
  return errorValue instanceof DOMException && errorValue.name === 'AbortError'
}
</script>

<template>
  <section class="dashboard-page" aria-labelledby="dashboard-title">
    <h1 id="dashboard-title" class="v2-visually-hidden">经营驾驶舱</h1>

    <nav v-if="allowedRoles.length > 1" class="dashboard-page__roles" aria-label="驾驶舱角色视图">
      <V2Button
        v-for="role in allowedRoles"
        :key="role"
        class="dashboard-page__role-button"
        size="small"
        :variant="selectedRole === role ? 'primary' : 'secondary'"
        :aria-pressed="selectedRole === role"
        @click="selectRole(role)"
      >
        {{ DASHBOARD_ROLE_LABELS[role] }}
      </V2Button>
    </nav>

    <V2PageState
      v-if="!allowedRoles.length"
      code="403"
      kind="error"
      title="暂无驾驶舱权限"
      description="当前账号没有任何角色驾驶舱查看权限。"
      :heading-level="2"
    />
    <V2PageState
      v-else-if="projectUnsupported"
      kind="empty"
      title="当前项目暂不支持此视图"
      description="项目经理驾驶舱仅支持进行中项目，请在顶部切换项目。"
      :heading-level="2"
    />
    <V2PageState
      v-else-if="loading"
      kind="loading"
      title="正在加载经营数据"
      description="仅请求当前选中的角色视图。"
      :heading-level="2"
    />
    <V2PageState
      v-else-if="error"
      kind="error"
      title="经营数据加载失败"
      description="已保留当前项目与报告期，可重试本角色视图。"
      :heading-level="2"
    >
      <template #actions>
        <V2Button @click="refreshToken += 1">重新加载</V2Button>
      </template>
    </V2PageState>

    <template v-else-if="data">
      <section id="health-overview" class="command-panel health-panel">
        <header class="command-panel__title">
          <strong>项目经营健康评分</strong>
          <span>{{ currentProjectLabel }} · {{ DASHBOARD_ROLE_LABELS[selectedRole] }}</span>
          <V2Button size="small" variant="ghost" :loading="loading" @click="refreshDashboard">
            刷新
          </V2Button>
          <V2Button size="small" variant="secondary" @click="showHighestRisks">
            查看最高风险
          </V2Button>
        </header>
        <div class="health-content">
          <div
            v-if="health"
            class="health-score"
            role="img"
            :aria-label="`经营健康评分 ${health.score} 分，${health.label}；分数越高越健康`"
          >
            <div class="health-score__chart">
              <DashboardGauge
                :value="health.score"
                :color-token="health.tone === 'danger' ? '--v2-color-danger' : '--v2-color-primary'"
              />
              <div class="health-score__value">
                <strong>{{ health.score }}</strong>
                <span :class="`is-${health.tone}`">{{ health.label }}</span>
              </div>
            </div>
            <p>分数越高表示经营状况越健康；仅作辅助判断，非财务/结算口径</p>
          </div>

          <div class="highest-risk">
            <span class="highest-risk__tag">最高风险</span>
            <h2>{{ highestRisk?.title || '当前暂无高风险事项' }}</h2>
            <p>{{ highestRisk?.meta || '经营指标处于可控范围，请持续关注趋势变化。' }}</p>
          </div>

          <div class="health-metrics">
            <article
              v-for="metric in displayMetrics"
              :key="metric.label"
              class="health-metric"
              :class="`is-${metric.tone || 'default'}`"
            >
              <span>{{ metric.label }}</span>
              <strong
                >{{ metric.value }} <small v-if="metric.unit">{{ metric.unit }}</small></strong
              >
              <p>当前报告期经营口径</p>
            </article>
          </div>
        </div>
      </section>

      <div class="command-grid">
        <section id="cost-trend" class="command-panel trend-panel">
          <header class="panel-toolbar">
            <div>
              <strong>{{
                trendDefinition.series.length ? trendDefinition.title : '经营动态'
              }}</strong>
              <span v-if="trendDefinition.series.length">（万元）</span>
              <span v-else>（{{ activityItems.length }}）</span>
            </div>
            <div v-if="trendDefinition.series.length" class="trend-range" aria-label="趋势时间范围">
              <V2Button
                v-for="range in [
                  { value: 'year', label: '当年累计' },
                  { value: 'half', label: '近6个月' },
                  { value: 'quarter', label: '近3个月' },
                ] as const"
                :key="range.value"
                size="small"
                :variant="trendRange === range.value ? 'secondary' : 'ghost'"
                :aria-pressed="trendRange === range.value"
                @click="trendRange = range.value"
              >
                {{ range.label }}
              </V2Button>
            </div>
          </header>
          <DashboardTrendChart
            v-if="trendDefinition.series.length && visibleTrendPoints.length"
            :points="visibleTrendPoints"
            :series="trendDefinition.series"
            :aria-label="trendDefinition.ariaLabel"
            :caption="trendDefinition.caption"
          />
          <p v-else-if="trendDefinition.series.length" class="trend-empty">
            当前筛选条件下暂无趋势数据
          </p>
          <ul v-else-if="activityItems.length" class="dashboard-activity-list">
            <li v-for="item in activityItems" :key="item.id">
              <div>
                <strong>{{ item.title }}</strong>
                <span>{{ item.meta }}</span>
              </div>
              <div>
                <strong>{{ item.value || '—' }}</strong>
                <span>{{ item.status }}</span>
              </div>
            </li>
          </ul>
          <p v-else class="trend-empty">当前筛选条件下暂无经营动态</p>
        </section>

        <section id="risk-list" class="command-panel risk-panel">
          <header class="panel-toolbar">
            <strong>预警列表（{{ risks.length }}）</strong>
            <div class="risk-panel__actions">
              <V2Select
                v-model="riskFilter"
                class="risk-filter"
                label="预警级别"
                hide-label
                :options="riskFilterOptions"
                placeholder="全部预警"
              />
              <div v-if="canEvaluateAlerts" class="risk-evaluate-action">
                <V2Button
                  size="small"
                  variant="ghost"
                  :loading="alertActionLoading"
                  @click="evaluateCurrentAlerts"
                >
                  评估预警
                </V2Button>
              </div>
            </div>
          </header>
          <V2Alert v-if="alertError && !selectedAlert" tone="danger" title="预警请求未完成">
            {{ alertError }}
          </V2Alert>
          <div class="risk-table-wrap" tabindex="0">
            <table class="risk-table v2-table--compact">
              <caption class="v2-visually-hidden">
                预警列表
              </caption>
              <thead>
                <tr>
                  <th>优先级</th>
                  <th>预警事项</th>
                  <th v-if="showRiskValueColumn">金额 / 指标</th>
                  <th>状态</th>
                  <th>责任范围</th>
                  <th>规则 / 触发时间</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in visibleRisks"
                  :key="item.id"
                  :class="{ 'is-actionable': 'alert' in item && item.alert }"
                  :tabindex="'alert' in item && item.alert ? 0 : undefined"
                  @click="openAlertDisposition(item)"
                  @keydown.enter="openAlertDisposition(item)"
                >
                  <td>
                    <span class="risk-level" :class="`is-${item.riskLevel}`">{{
                      item.riskLevel ? DASHBOARD_RISK_LABELS[item.riskLevel] : '其他'
                    }}</span>
                  </td>
                  <td>
                    <strong>{{ item.title }}</strong>
                  </td>
                  <td v-if="showRiskValueColumn">{{ item.value }}</td>
                  <td>{{ item.status }}</td>
                  <td>{{ currentProjectLabel }}</td>
                  <td>{{ item.meta }}</td>
                </tr>
                <tr v-if="alertLoading" class="empty-row">
                  <td :colspan="riskTableColumnCount">正在加载预警</td>
                </tr>
                <tr v-else-if="!filteredRisks.length" class="empty-row">
                  <td :colspan="riskTableColumnCount">当前筛选条件下暂无预警</td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer v-if="filteredRisks.length" class="risk-pagination" aria-label="预警分页">
            <span>共 {{ filteredRisks.length }} 条</span>
            <V2Button size="small" variant="ghost" :disabled="riskPage <= 1" @click="riskPage -= 1">
              上一页
            </V2Button>
            <span>第 {{ riskPage }} / {{ riskPageCount }} 页</span>
            <V2Button
              size="small"
              variant="ghost"
              :disabled="riskPage >= riskPageCount"
              @click="riskPage += 1"
            >
              下一页
            </V2Button>
          </footer>
        </section>
      </div>

      <section v-if="financeSummary.length" class="command-panel finance-summary-panel">
        <header class="panel-toolbar"><strong>资金闭环指标</strong></header>
        <div class="finance-summary-grid">
          <article v-for="item in financeSummary" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>
      </section>

      <section class="command-panel utility-panel">
        <div class="quick-entry">
          <strong>快捷入口</strong>
          <div class="quick-actions">
            <a
              v-for="entry in quickEntries"
              v-show="entry.label !== '成本分解' || selectedRole === 'cost'"
              :key="entry.label"
              :href="entry.href"
            >
              <DomainNavigationIcon :domain-id="entry.domain" />
              <span>{{ entry.label }}</span>
            </a>
          </div>
        </div>
        <div id="recent-entry" class="recent-entry">
          <strong>最近打开</strong>
          <a href="#dashboard-title">
            <DomainNavigationIcon domain-id="workbench" />
            <span>
              <b>{{ currentProjectLabel }}</b>
              <small>{{ DASHBOARD_ROLE_LABELS[selectedRole] }} · 最近更新</small>
            </span>
          </a>
        </div>
      </section>

      <V2Card v-if="selectedRole === 'cost'" id="cost-breakdown" class="dashboard-page__panel">
        <header>
          <div>
            <h2>成本科目分解</h2>
          </div>
        </header>
        <V2PageState
          v-if="breakdownLoading"
          kind="loading"
          title="加载成本分解"
          description="主驾驶舱数据保持可见。"
          :heading-level="3"
        />
        <V2Alert v-else-if="breakdownError" title="成本分解加载失败" tone="warning">
          主驾驶舱数据未受影响；刷新可重试独立下钻请求。
        </V2Alert>
        <p v-else-if="!canLoadBreakdown" class="dashboard-page__empty">当前账号无成本分解权限。</p>
        <div v-else-if="visibleSubjects.length" class="dashboard-page__table-wrap">
          <table>
            <caption class="v2-visually-hidden">
              最多两级成本科目分解
            </caption>
            <thead>
              <tr>
                <th>成本科目</th>
                <th>目标成本</th>
                <th>实际成本</th>
                <th>动态成本</th>
                <th>偏差</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="subject in visibleSubjects" :key="subject.costSubjectId">
                <td :class="{ 'is-child': subject.level === 2 }">
                  <V2Button
                    v-if="subject.level <= 1 && hasChildren(subject)"
                    size="small"
                    variant="ghost"
                    :aria-expanded="expandedSubjects.has(subject.costSubjectId)"
                    @click="toggleSubject(subject)"
                  >
                    {{ expandedSubjects.has(subject.costSubjectId) ? '收起' : '展开' }}
                  </V2Button>
                  {{ subject.costSubjectName }}
                </td>
                <td>{{ formatAmount(subject.targetCost) }}</td>
                <td>{{ formatAmount(subject.actualCost) }}</td>
                <td>{{ formatAmount(subject.dynamicCost) }}</td>
                <td>{{ formatAmount(subject.costDeviation) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="dashboard-page__empty">当前项目暂无成本科目分解。</p>
      </V2Card>

      <V2Card
        v-if="selectedRole === 'finance'"
        id="finance-contract-breakdown"
        class="dashboard-page__panel"
      >
        <header><h2>合同资金分解</h2></header>
        <div v-if="financeData?.contractFundBreakdowns.length" class="dashboard-page__table-wrap">
          <table>
            <caption class="v2-visually-hidden">
              合同与付款记录资金分解
            </caption>
            <thead>
              <tr>
                <th>名称</th>
                <th>编号</th>
                <th>业务日期</th>
                <th>合同金额</th>
                <th>累计支付 / 付款金额</th>
                <th>审批中</th>
                <th>已批未付</th>
                <th>剩余额度</th>
                <th>支付比例 / 状态</th>
              </tr>
            </thead>
            <tbody>
              <template
                v-for="contract in financeData.contractFundBreakdowns"
                :key="contract.contractId"
              >
                <tr>
                  <td>
                    <V2Button
                      v-if="contract.paymentRecords.length"
                      size="small"
                      variant="ghost"
                      :aria-expanded="expandedFinanceContracts.has(contract.contractId)"
                      @click="toggleFinanceContract(contract.contractId)"
                    >
                      {{ expandedFinanceContracts.has(contract.contractId) ? '收起' : '展开' }}
                    </V2Button>
                    {{ contract.contractName }}
                  </td>
                  <th scope="row">{{ contract.contractCode }}</th>
                  <td>—</td>
                  <td>{{ formatAmount(contract.contractAmount) }}</td>
                  <td>{{ formatAmount(contract.paidAmount) }}</td>
                  <td>{{ formatAmount(contract.approvingAmount) }}</td>
                  <td>{{ formatAmount(contract.approvedUnpaidAmount) }}</td>
                  <td>{{ formatAmount(contract.remainingAmount) }}</td>
                  <td>{{ contract.paymentRatio }}%</td>
                </tr>
                <tr
                  v-for="payment in expandedFinanceContracts.has(contract.contractId)
                    ? contract.paymentRecords
                    : []"
                  :key="payment.payRecordId"
                >
                  <td class="is-child">付款记录</td>
                  <th scope="row">{{ payment.recordCode || '—' }}</th>
                  <td>{{ payment.payDate || '日期未定' }}</td>
                  <td>—</td>
                  <td>{{ formatAmount(payment.payAmount) }}</td>
                  <td>—</td>
                  <td>—</td>
                  <td>—</td>
                  <td>{{ dashboardStatusLabel(payment.payStatus) }}</td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <p v-else class="dashboard-page__empty">当前范围暂无合同资金明细。</p>
      </V2Card>
    </template>

    <V2Dialog
      :open="Boolean(selectedAlert)"
      title="预警详情"
      description="查看预警记录并执行当前账号允许的操作。"
      close-label="关闭预警详情"
      panel-class="v2-dialog-standard v2-detail-dialog"
      :close-on-backdrop="false"
      @close="selectedAlert = null"
    >
      <template v-if="selectedAlert">
        <V2Alert v-if="alertError" tone="danger" title="处置未完成">{{ alertError }}</V2Alert>
        <V2Alert v-if="alertActionMessage" tone="info" title="操作结果">
          {{ alertActionMessage }}
        </V2Alert>
        <div class="v2-detail-dialog__section">
          <V2Badge :tone="severityTone(selectedAlert.severity)">{{
            DASHBOARD_RISK_LABELS[alertRiskLevel(selectedAlert.severity)]
          }}</V2Badge>
          <p class="v2-detail-dialog__message">{{ selectedAlert.message }}</p>
          <dl class="v2-detail-dialog__facts">
            <div>
              <dt>规则</dt>
              <dd>{{ alertRuleLabel(selectedAlert.ruleType) }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ alertStatusLabel(selectedAlert.processStatus) }}</dd>
            </div>
            <div>
              <dt>项目</dt>
              <dd>{{ selectedAlertProjectLabel }}</dd>
            </div>
            <div>
              <dt>触发时间</dt>
              <dd>{{ selectedAlert.triggeredAt }}</dd>
            </div>
          </dl>
        </div>
        <div v-if="canEditAlerts" class="v2-detail-dialog__actions">
          <div class="v2-detail-dialog__quick-actions">
            <V2GlassButton
              v-if="selectedAlert.isRead !== 1"
              text="标记已读"
              :loading="alertActionLoading"
              @click="markSelectedAlertRead"
            />
            <V2GlassButton
              v-if="
                (selectedAlert.processStatus || 'OPEN') === 'OPEN' && !selectedAlert.acknowledgedBy
              "
              text="接单"
              :loading="alertActionLoading"
              @click="acknowledgeSelectedAlert"
            />
          </div>
          <div class="v2-detail-dialog__form-row">
            <V2Select
              v-model="targetAlertStatus"
              label="目标状态"
              :options="targetAlertStatusOptions"
            />
            <V2Input v-model="alertRemark" label="处理说明" placeholder="必填，最多500字" />
            <V2GlassButton
              text="确认处置"
              :loading="alertActionLoading"
              @click="disposeSelectedAlert"
            />
          </div>
        </div>
        <p v-else class="v2-detail-dialog__readonly">当前账号仅可查看预警。</p>
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped>
.dashboard-page {
  display: grid;
  gap: var(--v2-space-4);
  min-width: 0;
}
.dashboard-page__panel header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--v2-space-4);
}
.dashboard-page__roles {
  display: flex;
  gap: var(--v2-space-2);
  overflow-x: auto;
  padding-block-end: var(--v2-space-1);
  scrollbar-width: thin;
}
.dashboard-page__panel {
  min-width: 0;
}
.dashboard-page__panel :deep(.v2-card__body) {
  display: grid;
  gap: var(--v2-space-4);
}
.dashboard-page__panel h2 {
  margin: 0;
  font-size: var(--v2-font-size-17);
}
.dashboard-page__empty {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-12);
}
.dashboard-page__table-wrap {
  max-width: 100%;
  overflow-x: auto;
}
.dashboard-page td.is-child {
  padding-inline-start: var(--v2-space-8);
}
@media (max-width: 48rem) {
  .dashboard-page__panel header {
    align-items: stretch;
    flex-direction: column;
  }
}

.dashboard-page {
  gap: var(--v2-space-3);
}
.dashboard-page__roles {
  padding: 0;
}
.command-panel {
  min-width: 0;
  overflow: hidden;
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  box-shadow: var(--v2-shadow-panel);
}
.command-panel__title,
.panel-toolbar {
  min-height: var(--v2-space-12);
  padding: 0 var(--v2-space-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-3);
  border-bottom: 1px solid var(--v2-color-border-subtle);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-15);
  font-weight: var(--v2-font-weight-bold);
}
.command-panel__title > span,
.panel-toolbar span {
  margin-inline-start: auto;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-regular);
}
.health-panel > .command-panel__title {
  min-height: calc(var(--v2-space-7) + var(--v2-space-7));
}
.health-content {
  min-height: calc(
    var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-6)
  );
  display: grid;
  grid-template-columns: 205px minmax(230px, 1fr) minmax(520px, 1.8fr);
  align-items: center;
}
.health-score {
  display: grid;
  place-items: center;
  padding: var(--v2-space-3);
}
.health-score__chart {
  position: relative;
  width: calc(var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-4));
  height: calc(var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-4));
}
.health-score__chart canvas {
  width: calc(var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-4));
  height: calc(var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-4));
}
.health-score__value {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  align-items: baseline;
}
.health-score__value strong {
  color: var(--v2-color-danger);
  font-size: var(--v2-font-size-36);
  line-height: var(--v2-line-height-tight);
}
.health-score__value span {
  grid-column: 1 / -1;
  width: max-content;
  margin: var(--v2-space-1) auto 0;
  padding: var(--v2-space-1) var(--v2-space-2);
  border-radius: var(--v2-radius-xs);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-bold);
}
.health-score p {
  margin: var(--v2-space-2) 0 0;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
  text-align: center;
}
.highest-risk {
  min-width: 0;
  padding: var(--v2-space-3) var(--v2-space-5);
  border-inline: 1px solid var(--v2-color-border-subtle);
}
.highest-risk__tag {
  display: inline-flex;
  padding: var(--v2-space-1) var(--v2-space-2);
  color: var(--v2-color-danger-text);
  background: var(--v2-color-danger-soft);
  border-radius: var(--v2-radius-xs);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-bold);
}
.highest-risk h2 {
  margin: var(--v2-space-2) 0 var(--v2-space-1);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-17);
  line-height: var(--v2-line-height-tight);
  overflow-wrap: anywhere;
}
.highest-risk p {
  min-height: var(--v2-control-height-sm);
  margin: 0;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-body);
}
.health-metrics {
  align-self: stretch;
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
}
.health-metric {
  min-width: 0;
  padding: var(--v2-space-7) var(--v2-space-4) var(--v2-space-4);
  border-inline-end: 1px solid var(--v2-color-border-subtle);
}
.health-metric:last-child {
  border-inline-end: 0;
}
.health-metric > span {
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}
.health-metric strong {
  display: block;
  margin: var(--v2-space-3) 0 var(--v2-space-2);
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-21);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.health-metric strong small {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-medium);
}
.health-metric p {
  margin: 0;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.is-danger {
  color: var(--v2-color-danger-text) !important;
}
.is-warning {
  color: var(--v2-color-warning-text) !important;
}
.is-success,
.is-positive {
  color: var(--v2-color-success-text) !important;
}
.is-neutral,
.is-default {
  color: var(--v2-color-text) !important;
}
.is-blue {
  color: var(--v2-color-primary) !important;
}
.is-teal {
  color: var(--v2-color-info) !important;
}
.command-grid {
  display: grid;
  grid-template-columns: minmax(430px, 0.92fr) minmax(550px, 1.18fr);
  align-items: start;
  gap: var(--v2-space-3);
}
.trend-range {
  display: flex;
  gap: var(--v2-space-1);
}
.trend-chart,
.trend-empty {
  width: 100%;
  height: calc(
    var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-8) +
      var(--v2-space-1)
  );
}
.dashboard-activity-list {
  padding: 0;
  margin: 0;
  list-style: none;
}
.dashboard-activity-list li {
  display: flex;
  gap: var(--v2-space-4);
  align-items: center;
  justify-content: space-between;
  min-height: var(--v2-control-height-touch);
  padding: var(--v2-space-2) var(--v2-space-4);
  border-bottom: 1px solid var(--v2-color-border-subtle);
}
.dashboard-activity-list li > div {
  display: grid;
  min-width: 0;
  gap: var(--v2-space-1);
}
.dashboard-activity-list li > div:last-child {
  flex: 0 0 auto;
  text-align: right;
}
.dashboard-activity-list strong,
.dashboard-activity-list span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dashboard-activity-list strong {
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-12);
}
.dashboard-activity-list span {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.trend-empty {
  display: grid;
  place-items: center;
  margin: 0;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-12);
}
.risk-filter {
  width: 112px;
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-regular);
}
.risk-panel__actions {
  display: flex;
  align-items: end;
  gap: var(--v2-space-2);
}
.risk-panel {
  overflow: visible;
}
.risk-evaluate-action {
  position: relative;
  flex: 0 0 auto;
}
.finance-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.finance-summary-grid article {
  min-width: 0;
  padding: var(--v2-space-4);
  border-inline-end: 1px solid var(--v2-color-border-subtle);
  border-block-end: 1px solid var(--v2-color-border-subtle);
}
.finance-summary-grid article:nth-child(4n) {
  border-inline-end: 0;
}
.finance-summary-grid article:nth-last-child(-n + 4) {
  border-block-end: 0;
}
.finance-summary-grid span,
.finance-summary-grid strong {
  display: block;
}
.finance-summary-grid span {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.finance-summary-grid strong {
  margin-top: var(--v2-space-2);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-15);
  font-variant-numeric: tabular-nums;
}
.risk-table-wrap {
  overflow: auto;
}
.risk-pagination {
  min-height: var(--v2-space-12);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--v2-space-2);
  padding: var(--v2-space-2) var(--v2-space-3);
  border-top: 1px solid var(--v2-color-border-subtle);
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.dashboard-page .risk-table td:nth-child(2) {
  max-width: 220px;
}
.dashboard-page .risk-table td:nth-child(2) strong {
  display: block;
  overflow: hidden;
  color: var(--v2-color-text-strong);
  text-overflow: ellipsis;
}
.dashboard-page .risk-table tr.is-actionable {
  cursor: pointer;
}
.dashboard-page .risk-table tr.is-actionable:hover,
.dashboard-page .risk-table tr.is-actionable:focus-visible {
  background: var(--v2-color-primary-soft);
  outline: 0;
}
.risk-level {
  display: inline-flex;
  width: var(--v2-space-6);
  height: var(--v2-space-6);
  align-items: center;
  justify-content: center;
  border: 1px solid;
  border-radius: var(--v2-radius-xs);
  font-weight: var(--v2-font-weight-bold);
}
.risk-level.is-high {
  color: var(--v2-color-danger-text);
  background: var(--v2-color-danger-soft);
  border-color: var(--v2-color-danger);
}
.risk-level.is-medium {
  color: var(--v2-color-warning-text);
  background: var(--v2-color-warning-soft);
  border-color: var(--v2-color-warning);
}
.risk-level.is-low {
  color: var(--v2-color-success-text);
  background: var(--v2-color-success-soft);
  border-color: var(--v2-color-success);
}
.risk-level.is-other {
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface-subtle);
  border-color: var(--v2-color-border);
}
.dashboard-page .empty-row td {
  height: calc(
    var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-8) +
      var(--v2-space-1)
  );
  color: var(--v2-color-text-muted);
  text-align: center;
}
.utility-panel {
  min-height: calc(var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-2));
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: var(--v2-space-4);
}
.quick-entry {
  padding-inline-end: var(--v2-space-6);
  border-inline-end: 1px solid var(--v2-color-border-subtle);
}
.recent-entry {
  padding-inline-start: var(--v2-space-7);
}
.quick-entry > strong,
.recent-entry > strong {
  display: block;
  margin-bottom: var(--v2-space-3);
  font-size: var(--v2-font-size-13);
}
.quick-actions {
  display: grid;
  grid-template-columns: repeat(7, minmax(58px, 1fr));
  gap: var(--v2-space-2);
}
.quick-actions a,
.recent-entry a {
  color: var(--v2-color-text-secondary);
  text-decoration: none;
}
.quick-actions a {
  display: grid;
  justify-items: center;
  gap: var(--v2-space-2);
  padding: var(--v2-space-1);
  font-size: var(--v2-font-size-11);
}
.quick-actions svg {
  width: var(--v2-space-5);
  height: var(--v2-space-5);
}
.quick-actions a:hover {
  color: var(--v2-color-primary);
}
.recent-entry a {
  min-width: 0;
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: var(--v2-space-2);
  align-items: start;
}
.recent-entry svg {
  width: var(--v2-space-4);
  height: var(--v2-space-4);
}
.recent-entry b,
.recent-entry small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-entry b {
  font-size: var(--v2-font-size-11);
}
.recent-entry small {
  margin-top: var(--v2-space-1);
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

@media (max-width: 78.75rem) {
  .health-content {
    grid-template-columns: 190px minmax(230px, 1fr);
  }
  .health-metrics {
    grid-column: 1 / -1;
    border-top: 1px solid var(--v2-color-border-subtle);
  }
  .health-metric {
    padding: var(--v2-space-6) var(--v2-space-5);
  }
  .command-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 48rem) {
  .dashboard-page__roles {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    overflow: visible;
  }
  .dashboard-page__role-button {
    width: 100%;
  }
  .command-panel__title {
    align-items: flex-start;
    flex-direction: column;
    padding-block: var(--v2-space-2);
  }
  .command-panel__title > span {
    margin-inline-start: 0;
  }
  .health-content {
    grid-template-columns: 1fr;
  }
  .highest-risk {
    border-inline: 0;
    border-block: 1px solid var(--v2-color-border-subtle);
  }
  .health-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .finance-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .finance-summary-grid article:nth-child(2n) {
    border-inline-end: 0;
  }
  .finance-summary-grid article:nth-last-child(-n + 4) {
    border-block-end: 1px solid var(--v2-color-border-subtle);
  }
  .finance-summary-grid article:nth-last-child(-n + 2) {
    border-block-end: 0;
  }
  .health-metric:nth-child(2) {
    border-inline-end: 0;
  }
  .panel-toolbar {
    align-items: flex-start;
    flex-direction: column;
    padding-block: var(--v2-space-2);
  }
  .risk-panel__actions {
    width: 100%;
    align-items: flex-end;
    justify-content: space-between;
  }
  .risk-pagination {
    flex-wrap: wrap;
    justify-content: center;
  }
  .utility-panel {
    grid-template-columns: 1fr;
  }
  .quick-entry {
    padding: 0 0 var(--v2-space-5);
    border-inline-end: 0;
    border-block-end: 1px solid var(--v2-color-border-subtle);
  }
  .recent-entry {
    padding: var(--v2-space-5) 0 0;
  }
}
@media (max-width: 25rem) {
  .health-metrics {
    grid-template-columns: 1fr;
  }
  .quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
