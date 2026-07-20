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
  V2Input,
  V2PageState,
  V2Select,
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
  formatAmount,
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
const riskFilterMenu = ref<HTMLDetailsElement>()
const riskFilterLabel = computed(
  () => ({ all: '全部预警', ...DASHBOARD_RISK_LABELS })[riskFilter.value],
)
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
const currentProjectLabel = computed(() => currentProject.value?.label ?? '全部项目')
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
const risks = computed(() => (canViewAlerts.value ? alertRisks.value : derivedRisks.value))
const activityItems = computed(() =>
  data.value ? dashboardActivityItems(selectedRole.value, data.value).slice(0, 6) : [],
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
        { key: 'targetCost', label: '目标成本', color: '#2563eb' },
        { key: 'dynamicCost', label: '动态成本', color: '#0891b2' },
        { key: 'costDeviation', label: '成本偏差', color: '#f97316' },
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
        { key: 'cashOutflowAmount', label: '本月支付', color: '#2563eb' },
        { key: 'cumulativePaidAmount', label: '累计支付', color: '#0891b2' },
        { key: 'pendingPaymentAmount', label: '处理中付款', color: '#f97316' },
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

function selectRiskFilter(value: 'all' | DashboardRiskLevel): void {
  riskFilter.value = value
  riskFilterMenu.value?.removeAttribute('open')
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
        ...periodBounds,
      },
      currentController.signal,
    )
    if (currentController.signal.aborted) return
    alertRows.value = result.records.map((row) => ({
      ...row,
      id: String(row.id),
      projectId: String(row.projectId),
    }))
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
  void evaluateAlerts()
    .then(async (result) => {
      alertActionMessage.value = `评估完成，生成 ${result.alertsGenerated} 项预警`
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
      <button
        v-for="role in allowedRoles"
        :key="role"
        type="button"
        :aria-pressed="selectedRole === role"
        :class="{ 'is-active': selectedRole === role }"
        @click="selectRole(role)"
      >
        {{ DASHBOARD_ROLE_LABELS[role] }}
      </button>
    </nav>

    <V2PageState
      v-if="!allowedRoles.length"
      code="403"
      kind="error"
      title="暂无驾驶舱权限"
      description="当前账号没有任何角色驾驶舱查看权限。"
    />
    <V2PageState
      v-else-if="projectUnsupported"
      kind="empty"
      title="当前项目暂不支持此视图"
      description="项目经理驾驶舱仅支持进行中项目，请在顶部切换项目。"
    />
    <V2PageState
      v-else-if="loading"
      kind="loading"
      title="正在加载经营数据"
      description="仅请求当前选中的角色视图。"
    />
    <V2PageState
      v-else-if="error"
      kind="error"
      title="经营数据加载失败"
      description="已保留当前项目与报告期，可重试本角色视图。"
    >
      <template #actions>
        <V2Button @click="refreshToken += 1">重新加载</V2Button>
      </template>
    </V2PageState>

    <template v-else-if="data">
      <section id="health-overview" class="command-panel health-panel">
        <header class="command-panel__title">
          <strong>项目经营健康度</strong>
          <span>{{ currentProjectLabel }} · {{ DASHBOARD_ROLE_LABELS[selectedRole] }}</span>
          <V2Button size="small" variant="ghost" :loading="loading" @click="refreshToken += 1">
            刷新
          </V2Button>
          <button type="button" class="dashboard-page__outline-link" @click="showHighestRisks">
            查看最高风险
          </button>
        </header>
        <div class="health-content">
          <div v-if="health" class="health-score">
            <div class="health-score__chart">
              <DashboardGauge
                :value="health.score"
                :color="health.tone === 'danger' ? '#d71920' : '#2563eb'"
              />
              <div class="health-score__value">
                <strong>{{ health.score }}</strong>
                <span :class="`is-${health.tone}`">{{ health.label }}</span>
              </div>
            </div>
            <p>综合成本、资金、履约与待办风险</p>
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
              <button
                v-for="range in [
                  { value: 'year', label: '当年累计' },
                  { value: 'half', label: '近6个月' },
                  { value: 'quarter', label: '近3个月' },
                ] as const"
                :key="range.value"
                type="button"
                :aria-pressed="trendRange === range.value"
                :class="{ 'is-active': trendRange === range.value }"
                @click="trendRange = range.value"
              >
                {{ range.label }}
              </button>
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
              <details ref="riskFilterMenu" class="risk-filter">
                <span class="v2-visually-hidden">筛选预警</span>
                <summary aria-label="筛选预警">{{ riskFilterLabel }}</summary>
                <div class="risk-filter__menu" role="listbox" aria-label="筛选预警选项">
                  <button
                    type="button"
                    role="option"
                    :aria-selected="riskFilter === 'all'"
                    @click="selectRiskFilter('all')"
                  >
                    全部预警
                  </button>
                  <button
                    type="button"
                    role="option"
                    :aria-selected="riskFilter === 'high'"
                    @click="selectRiskFilter('high')"
                  >
                    高
                  </button>
                  <button
                    type="button"
                    role="option"
                    :aria-selected="riskFilter === 'medium'"
                    @click="selectRiskFilter('medium')"
                  >
                    中
                  </button>
                  <button
                    type="button"
                    role="option"
                    :aria-selected="riskFilter === 'low'"
                    @click="selectRiskFilter('low')"
                  >
                    低
                  </button>
                  <button
                    type="button"
                    role="option"
                    :aria-selected="riskFilter === 'other'"
                    @click="selectRiskFilter('other')"
                  >
                    其他
                  </button>
                </div>
              </details>
              <V2Button
                v-if="canEvaluateAlerts"
                size="small"
                variant="ghost"
                :loading="alertActionLoading"
                @click="evaluateCurrentAlerts"
              >
                评估预警
              </V2Button>
            </div>
          </header>
          <V2Alert v-if="alertError && !selectedAlert" tone="danger" title="预警请求未完成">
            {{ alertError }}
          </V2Alert>
          <V2Alert v-if="alertActionMessage && !selectedAlert" tone="info" title="操作结果">
            {{ alertActionMessage }}
          </V2Alert>
          <div class="risk-table-wrap" tabindex="0">
            <table class="risk-table">
              <caption class="v2-visually-hidden">
                预警列表
              </caption>
              <thead>
                <tr>
                  <th>优先级</th>
                  <th>预警事项</th>
                  <th>金额 / 指标</th>
                  <th>状态</th>
                  <th>责任范围</th>
                  <th>来源</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in filteredRisks"
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
                  <td>{{ item.value || '—' }}</td>
                  <td>{{ item.status }}</td>
                  <td>{{ currentProjectLabel }}</td>
                  <td>{{ item.meta }}</td>
                </tr>
                <tr v-if="alertLoading" class="empty-row">
                  <td colspan="6">正在加载预警</td>
                </tr>
                <tr v-else-if="!filteredRisks.length" class="empty-row">
                  <td colspan="6">当前筛选条件下暂无预警</td>
                </tr>
              </tbody>
            </table>
          </div>
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
                  <button
                    v-if="subject.level <= 1 && hasChildren(subject)"
                    type="button"
                    :aria-expanded="expandedSubjects.has(subject.costSubjectId)"
                    @click="toggleSubject(subject)"
                  >
                    {{ expandedSubjects.has(subject.costSubjectId) ? '收起' : '展开' }}
                  </button>
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
                <th>合同 / 付款记录</th>
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
                    <button
                      v-if="contract.paymentRecords.length"
                      type="button"
                      :aria-expanded="expandedFinanceContracts.has(contract.contractId)"
                      @click="toggleFinanceContract(contract.contractId)"
                    >
                      {{ expandedFinanceContracts.has(contract.contractId) ? '收起' : '展开' }}
                    </button>
                    {{ contract.contractName }}（{{ contract.contractCode }}）
                  </td>
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
                  <td class="is-child">
                    {{ payment.recordCode || '付款记录' }} · {{ payment.payDate || '日期未定' }}
                  </td>
                  <td>—</td>
                  <td>{{ formatAmount(payment.payAmount) }}</td>
                  <td>—</td>
                  <td>—</td>
                  <td>—</td>
                  <td>{{ payment.payStatus }}</td>
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
      :title="selectedAlert?.message ?? '预警处置'"
      description="查看权威预警记录并执行当前账号允许的操作。"
      close-label="关闭预警处置"
      panel-class="workflow-detail-dialog dashboard-alert-dialog"
      :close-on-backdrop="!alertActionLoading"
      @close="selectedAlert = null"
    >
      <template v-if="selectedAlert">
        <V2Alert v-if="alertError" tone="danger" title="处置未完成">{{ alertError }}</V2Alert>
        <V2Alert v-if="alertActionMessage" tone="info" title="操作结果">
          {{ alertActionMessage }}
        </V2Alert>
        <div class="dashboard-alert-detail">
          <V2Badge :tone="severityTone(selectedAlert.severity)">{{
            DASHBOARD_RISK_LABELS[alertRiskLevel(selectedAlert.severity)]
          }}</V2Badge>
          <dl>
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
              <dd>{{ selectedAlert.projectId }}</dd>
            </div>
            <div>
              <dt>触发时间</dt>
              <dd>{{ selectedAlert.triggeredAt }}</dd>
            </div>
          </dl>
        </div>
        <div v-if="canEditAlerts" class="dashboard-alert-actions">
          <V2Button
            v-if="selectedAlert.isRead !== 1"
            size="small"
            variant="secondary"
            :loading="alertActionLoading"
            @click="markSelectedAlertRead"
          >
            标记已读
          </V2Button>
          <V2Button
            v-if="
              (selectedAlert.processStatus || 'OPEN') === 'OPEN' && !selectedAlert.acknowledgedBy
            "
            size="small"
            variant="secondary"
            :loading="alertActionLoading"
            @click="acknowledgeSelectedAlert"
          >
            接单
          </V2Button>
          <V2Select
            v-model="targetAlertStatus"
            label="目标状态"
            :options="targetAlertStatusOptions"
          />
          <V2Input v-model="alertRemark" label="处理说明" placeholder="必填，最多500字" />
          <V2Button size="small" :loading="alertActionLoading" @click="disposeSelectedAlert">
            确认处置
          </V2Button>
        </div>
        <p v-else class="dashboard-alert-readonly">当前账号仅可查看预警。</p>
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
.dashboard-page__hero,
.dashboard-page__panel header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--v2-space-4);
}
.dashboard-page__hero {
  padding: var(--v2-space-5) var(--v2-space-6);
  background: linear-gradient(130deg, #10284d, #1d4f91 70%, #2563eb);
  border-radius: var(--v2-radius-lg);
  color: #fff;
  box-shadow: var(--v2-shadow-panel);
}
.dashboard-page__hero h1 {
  margin: 0;
  font-size: clamp(var(--v2-font-size-21), 3vw, var(--v2-font-size-28));
}
.dashboard-page__hero p {
  margin: var(--v2-space-2) 0 0;
  color: rgb(255 255 255 / 76%);
  font-size: var(--v2-font-size-12);
}
.dashboard-page__eyebrow {
  margin: 0 0 var(--v2-space-1);
  color: var(--v2-color-primary);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-bold);
  letter-spacing: 0.1em;
}
.dashboard-page__hero .dashboard-page__eyebrow {
  color: #a8d1ff;
}
.dashboard-page__hero :deep(.v2-button--secondary) {
  color: var(--v2-color-primary-active);
}
.dashboard-page__roles {
  display: flex;
  gap: var(--v2-space-2);
  overflow-x: auto;
  padding-block-end: var(--v2-space-1);
  scrollbar-width: thin;
}
.dashboard-page__roles button {
  min-height: 2.75rem;
  padding: 0 var(--v2-space-4);
  white-space: nowrap;
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-round);
  font: inherit;
  cursor: pointer;
}
.dashboard-page__roles button.is-active {
  color: #fff;
  background: var(--v2-color-primary);
  border-color: var(--v2-color-primary);
  box-shadow: 0 6px 18px rgb(37 99 235 / 18%);
}
.dashboard-page__summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.dashboard-page__summary :deep(.v2-card) {
  min-width: 0;
  padding: var(--v2-space-4);
}
.dashboard-page__health {
  background: var(--v2-color-primary-soft);
}
.dashboard-page__health p,
.dashboard-page__metric p {
  margin: 0;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-12);
}
.dashboard-page__health p {
  color: var(--v2-color-text-secondary);
}
.dashboard-page__health strong,
.dashboard-page__metric strong {
  display: block;
  margin: var(--v2-space-2) 0;
  color: var(--v2-color-text-strong);
  font-size: clamp(var(--v2-font-size-17), 2vw, var(--v2-font-size-21));
  overflow-wrap: anywhere;
}
.dashboard-page__metric--positive strong {
  color: var(--v2-color-success-text);
}
.dashboard-page__metric--warning strong {
  color: var(--v2-color-warning-text);
}
.dashboard-page__metric--danger strong {
  color: var(--v2-color-danger-text);
}
.dashboard-page__grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(16rem, 1fr);
  gap: var(--v2-space-3);
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
.dashboard-page__list {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}
.dashboard-page__list li {
  display: flex;
  justify-content: space-between;
  gap: var(--v2-space-3);
  padding: var(--v2-space-3) 0;
  border-block-start: 1px solid var(--v2-color-border-subtle);
}
.dashboard-page__list li > div {
  display: grid;
  gap: var(--v2-space-1);
  min-width: 0;
}
.dashboard-page__list strong {
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-13);
}
.dashboard-page__list span,
.dashboard-page__empty,
.dashboard-page__quick p {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-12);
}
.dashboard-page__list-value {
  text-align: end;
}
.dashboard-page__quick a {
  display: block;
  min-height: 2.75rem;
  padding: var(--v2-space-3);
  color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
  border-radius: var(--v2-radius-sm);
  text-decoration: none;
}
.dashboard-page__trend {
  display: grid;
  grid-template-columns: minmax(18rem, 1fr) minmax(24rem, 2fr);
  gap: var(--v2-space-4);
  align-items: center;
}
.dashboard-page__trend svg {
  width: 100%;
  min-height: 12rem;
  background: linear-gradient(to bottom, var(--v2-color-surface-subtle), transparent);
  border-radius: var(--v2-radius-md);
}
.dashboard-page__trend line {
  stroke: var(--v2-color-border);
  stroke-width: 1;
}
.dashboard-page__trend polyline {
  fill: none;
  stroke: var(--v2-chart-1);
  stroke-width: 3;
  vector-effect: non-scaling-stroke;
}
.dashboard-page__table-wrap {
  max-width: 100%;
  overflow-x: auto;
}
.dashboard-page table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--v2-font-size-12);
}
.dashboard-page th,
.dashboard-page td {
  padding: var(--v2-space-3);
  border-block-end: 1px solid var(--v2-color-border-subtle);
  text-align: start;
  white-space: nowrap;
}
.dashboard-page th {
  color: var(--v2-color-text-muted);
  background: var(--v2-color-surface-subtle);
}
.dashboard-page td {
  color: var(--v2-color-text-secondary);
}
.dashboard-page td.is-child {
  padding-inline-start: var(--v2-space-8);
}
.dashboard-page td button {
  min-height: 2rem;
  margin-inline-end: var(--v2-space-2);
  color: var(--v2-color-primary);
  background: transparent;
  border: 0;
  cursor: pointer;
}
@media (max-width: 64rem) {
  .dashboard-page__summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .dashboard-page__trend {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 48rem) {
  .dashboard-page__hero {
    padding: var(--v2-space-4);
  }
  .dashboard-page__hero,
  .dashboard-page__panel header {
    align-items: stretch;
    flex-direction: column;
  }
  .dashboard-page__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .dashboard-page__grid {
    grid-template-columns: 1fr;
  }
  .dashboard-page__list li {
    align-items: flex-start;
    flex-direction: column;
  }
  .dashboard-page__list-value {
    text-align: start;
  }
}
@media (max-width: 25rem) {
  .dashboard-page__summary {
    grid-template-columns: 1fr;
  }
}

.dashboard-page {
  gap: 12px;
}
.v2-visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}
.dashboard-page__roles {
  min-height: 34px;
  padding: 0;
}
.dashboard-page__roles button {
  min-height: 32px;
  padding: 0 14px;
  border-radius: var(--v2-radius-sm);
  font-size: var(--v2-font-size-12);
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
  min-height: 48px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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
  min-height: 55px;
}
.health-content {
  min-height: 168px;
  display: grid;
  grid-template-columns: 205px minmax(230px, 1fr) minmax(520px, 1.8fr);
  align-items: center;
}
.health-score {
  display: grid;
  place-items: center;
  padding: 12px;
}
.health-score__chart {
  position: relative;
  width: 112px;
  height: 112px;
}
.health-score__chart canvas {
  width: 112px;
  height: 112px;
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
  font-size: 36px;
  line-height: 40px;
}
.health-score__value span {
  grid-column: 1 / -1;
  width: max-content;
  margin: 5px auto 0;
  padding: 2px 7px;
  border-radius: var(--v2-radius-xs);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-bold);
}
.health-score p {
  margin: 10px 0 0;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
  text-align: center;
}
.highest-risk {
  min-width: 0;
  padding: 12px 20px;
  border-inline: 1px solid var(--v2-color-border-subtle);
}
.highest-risk__tag {
  display: inline-flex;
  padding: 3px 8px;
  color: var(--v2-color-danger-text);
  background: var(--v2-color-danger-soft);
  border-radius: var(--v2-radius-xs);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-bold);
}
.highest-risk h2 {
  margin: 8px 0 4px;
  overflow: hidden;
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-17);
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.highest-risk p {
  min-height: 30px;
  margin: 0;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
  line-height: 19px;
}
.dashboard-page__outline-link {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  padding: 0 16px;
  background: transparent;
  color: var(--v2-color-primary);
  border: 1px solid var(--v2-color-primary);
  border-radius: var(--v2-radius-sm);
  font-size: var(--v2-font-size-12);
  font-family: inherit;
  text-decoration: none;
  cursor: pointer;
}
.command-panel__title .dashboard-page__outline-link {
  min-height: 32px;
  padding-inline: 12px;
  white-space: nowrap;
}
.health-metrics {
  align-self: stretch;
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
}
.health-metric {
  min-width: 0;
  padding: 28px 16px 16px;
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
  margin: 12px 0 10px;
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-21);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.health-metric strong small {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-10, 0.625rem);
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
  gap: 12px;
}
.trend-range {
  display: flex;
  padding: 2px;
  background: var(--v2-color-surface-subtle);
  border-radius: var(--v2-radius-sm);
}
.trend-range button {
  min-height: 28px;
  padding: 0 8px;
  color: var(--v2-color-text-muted);
  background: transparent;
  border: 0;
  border-radius: var(--v2-radius-xs);
  font: inherit;
  font-size: var(--v2-font-size-11);
  cursor: pointer;
}
.trend-range button.is-active {
  color: var(--v2-color-text-strong);
  background: var(--v2-color-surface);
  box-shadow: 0 1px 3px rgb(31 52 80 / 8%);
}
.trend-chart,
.trend-empty {
  width: 100%;
  height: 180px;
}
.dashboard-activity-list {
  padding: 0;
  margin: 0;
  list-style: none;
}
.dashboard-activity-list li {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  min-height: 45px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--v2-color-border-subtle);
}
.dashboard-activity-list li > div {
  display: grid;
  min-width: 0;
  gap: 3px;
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
  position: relative;
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-regular);
}
.risk-panel__actions {
  display: flex;
  align-items: end;
  gap: 8px;
}
.risk-filter summary {
  width: 94px;
  min-height: 32px;
  display: flex;
  align-items: center;
  padding: 0 8px;
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  list-style: none;
  cursor: pointer;
}
.risk-filter summary::after {
  margin-inline-start: auto;
  content: '⌄';
}
.risk-filter summary::-webkit-details-marker {
  display: none;
}
.risk-filter__menu {
  position: absolute;
  z-index: var(--v2-z-dropdown);
  inset-block-start: calc(100% + 4px);
  inset-inline-end: 0;
  width: 112px;
  padding: 4px;
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  box-shadow: var(--v2-shadow-panel);
}
.risk-filter__menu button {
  width: 100%;
  min-height: 32px;
  padding: 0 8px;
  color: var(--v2-color-text-secondary);
  background: transparent;
  border: 0;
  border-radius: var(--v2-radius-sm);
  text-align: start;
  cursor: pointer;
}
.risk-filter__menu button:hover,
.risk-filter__menu button[aria-selected='true'] {
  color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}
.finance-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.finance-summary-grid article {
  min-width: 0;
  padding: 14px;
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
  margin-top: 7px;
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-15);
  font-variant-numeric: tabular-nums;
}
.risk-table-wrap {
  overflow: auto;
}
.dashboard-page .risk-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--v2-font-size-11);
}
.dashboard-page .risk-table th,
.dashboard-page .risk-table td {
  height: 40px;
  padding: 0 10px;
  border: 0;
  border-top: 1px solid var(--v2-color-border-subtle);
  white-space: nowrap;
}
.dashboard-page .risk-table th {
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface-subtle);
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
  width: 25px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: 1px solid;
  border-radius: var(--v2-radius-xs);
  font-weight: var(--v2-font-weight-bold);
}
.risk-level.is-high {
  color: var(--v2-color-danger-text);
  background: var(--v2-color-danger-soft);
  border-color: #ffb8b5;
}
.risk-level.is-medium {
  color: var(--v2-color-warning-text);
  background: var(--v2-color-warning-soft);
  border-color: #ffd698;
}
.risk-level.is-low {
  color: var(--v2-color-success-text);
  background: var(--v2-color-success-soft);
  border-color: #9ce3c5;
}
.risk-level.is-other {
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface-subtle);
  border-color: var(--v2-color-border);
}
.dashboard-page .empty-row td {
  height: 180px;
  color: var(--v2-color-text-muted);
  text-align: center;
}
.dashboard-alert-detail {
  display: grid;
  gap: 12px;
  padding: var(--v2-space-4);
  border: 1px solid color-mix(in srgb, var(--v2-color-border) 70%, transparent);
  border-radius: var(--v2-radius-md);
  background: color-mix(in srgb, var(--v2-color-surface) 68%, transparent);
}
.dashboard-alert-detail dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}
.dashboard-alert-detail dl div {
  padding-inline-start: 10px;
  border-inline-start: 2px solid var(--v2-color-border-subtle);
}
.dashboard-alert-detail dt {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.dashboard-alert-detail dd {
  margin: 3px 0 0;
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-12);
  font-weight: var(--v2-font-weight-semibold);
}
.dashboard-alert-actions {
  display: grid;
  grid-template-columns: auto auto minmax(120px, 0.7fr) minmax(180px, 1fr) auto;
  gap: 10px;
  align-items: end;
  padding: var(--v2-space-3) var(--v2-space-4);
  border: 1px solid color-mix(in srgb, var(--v2-color-border) 64%, transparent);
  border-radius: var(--v2-radius-md);
  background: color-mix(in srgb, var(--v2-color-surface) 58%, transparent);
}
.dashboard-alert-readonly {
  margin: 14px 0 0;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-12);
}
.utility-panel {
  min-height: 104px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 16px 14px;
}
.quick-entry {
  padding-inline-end: 24px;
  border-inline-end: 1px solid var(--v2-color-border-subtle);
}
.recent-entry {
  padding-inline-start: 28px;
}
.quick-entry > strong,
.recent-entry > strong {
  display: block;
  margin-bottom: 12px;
  font-size: var(--v2-font-size-13);
}
.quick-actions {
  display: grid;
  grid-template-columns: repeat(7, minmax(58px, 1fr));
  gap: 8px;
}
.quick-actions a,
.recent-entry a {
  color: var(--v2-color-text-secondary);
  text-decoration: none;
}
.quick-actions a {
  display: grid;
  justify-items: center;
  gap: 7px;
  padding: 5px;
  font-size: var(--v2-font-size-11);
}
.quick-actions svg {
  width: 22px;
  height: 22px;
}
.quick-actions a:hover {
  color: var(--v2-color-primary);
}
.recent-entry a {
  min-width: 0;
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.recent-entry svg {
  width: 18px;
  height: 18px;
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
  margin-top: 5px;
  color: var(--v2-color-text-muted);
  font-size: 10px;
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
    padding: 24px 20px;
  }
  .command-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 48rem) {
  .command-panel__title {
    align-items: flex-start;
    flex-direction: column;
    padding-block: 10px;
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
    padding-block: 10px;
  }
  .dashboard-alert-detail dl,
  .dashboard-alert-actions {
    grid-template-columns: 1fr;
  }
  .utility-panel {
    grid-template-columns: 1fr;
  }
  .quick-entry {
    padding: 0 0 18px;
    border-inline-end: 0;
    border-block-end: 1px solid var(--v2-color-border-subtle);
  }
  .recent-entry {
    padding: 18px 0 0;
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
