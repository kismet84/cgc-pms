<script setup lang="ts">
import type {
  CostBreakdownVO,
  CostManagerDashboardVO,
  DashboardDataByRole,
  DashboardRole,
  FinanceDashboardVO,
  SubjectBreakdown,
} from '@cgc-pms/frontend-contracts'
import { resolveDashboardRoles } from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Alert, V2Badge, V2Button, V2Card, V2PageState } from '@/components'
import DomainNavigationIcon from '@/components/DomainNavigationIcon.vue'
import { loadCostBreakdown, loadDashboard } from '@/services/dashboard'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import {
  DASHBOARD_ROLE_LABELS,
  compactDashboardValue,
  dashboardHealth,
  dashboardMetrics,
  formatAmount,
  primaryRiskItems,
} from './model'
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
const loading = ref(false)
const error = ref(false)
const breakdown = ref<CostBreakdownVO | null>(null)
const breakdownLoading = ref(false)
const breakdownError = ref(false)
const expandedSubjects = ref(new Set<string>())
const trendRange = ref<'year' | 'half' | 'quarter'>('year')
const riskFilter = ref<'all' | 'attention' | 'risk'>('all')
const refreshToken = ref(0)
let generation = 0
let controller: AbortController | null = null

const access = computed(() => ({ roles: session.roles, permissions: session.permissions }))
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
const risks = computed(() =>
  data.value ? primaryRiskItems(selectedRole.value, data.value).slice(0, 6) : [],
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
const highestRisk = computed(() => risks.value[0])
const filteredRisks = computed(() => {
  if (riskFilter.value === 'all') return risks.value
  return risks.value.filter((_, index) => (riskFilter.value === 'risk' ? index === 0 : index > 0))
})
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

onBeforeUnmount(() => controller?.abort())

async function refresh(): Promise<void> {
  const currentGeneration = ++generation
  controller?.abort()
  controller = new AbortController()
  data.value = null
  breakdown.value = null
  error.value = false
  breakdownError.value = false
  expandedSubjects.value = new Set()

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
          <a class="dashboard-page__outline-link" href="#risk-list">查看并处理最高风险</a>
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
              <strong>{{ trendDefinition.title }}</strong
              ><span>（万元）</span>
            </div>
            <div class="trend-range" aria-label="趋势时间范围">
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
            v-if="visibleTrendPoints.length"
            :points="visibleTrendPoints"
            :series="trendDefinition.series"
            :aria-label="trendDefinition.ariaLabel"
            :caption="trendDefinition.caption"
          />
          <p v-else class="trend-empty">当前角色暂无趋势数据</p>
        </section>

        <section id="risk-list" class="command-panel risk-panel">
          <header class="panel-toolbar">
            <strong>经营预警与待办（{{ risks.length }}）</strong>
            <label class="risk-filter">
              <span class="v2-visually-hidden">筛选预警</span>
              <select v-model="riskFilter">
                <option value="all">全部预警</option>
                <option value="risk">最高风险</option>
                <option value="attention">其他关注</option>
              </select>
            </label>
          </header>
          <div class="risk-table-wrap">
            <table class="risk-table">
              <caption class="v2-visually-hidden">
                经营预警与待办
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
                <tr v-for="(item, index) in filteredRisks" :key="item.id">
                  <td>
                    <span class="risk-level" :class="index === 0 ? 'is-high' : 'is-medium'">{{
                      index === 0 ? '高' : '中'
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
                <tr v-if="!filteredRisks.length" class="empty-row">
                  <td colspan="6">当前筛选条件下暂无预警与待办</td>
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
            <p class="dashboard-page__eyebrow">两级只读下钻</p>
            <h2>成本科目分解</h2>
          </div>
          <V2Badge tone="info">独立请求</V2Badge>
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
    </template>
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
  color: var(--v2-color-primary);
  border: 1px solid var(--v2-color-primary);
  border-radius: var(--v2-radius-sm);
  font-size: var(--v2-font-size-12);
  text-decoration: none;
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
.trend-empty {
  display: grid;
  place-items: center;
  margin: 0;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-12);
}
.risk-filter select {
  width: 94px;
  min-height: 32px;
  padding: 0 8px;
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  font: inherit;
  font-size: var(--v2-font-size-11);
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
.dashboard-page .empty-row td {
  height: 180px;
  color: var(--v2-color-text-muted);
  text-align: center;
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
