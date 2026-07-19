import type {
  CostManagerDashboardVO,
  FinanceDashboardVO,
  ProjectManagerDashboardVO,
} from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from '@/pages/dashboard/DashboardPage.vue'
import { loadCostBreakdown, loadDashboard } from '@/services/dashboard'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/services/dashboard', () => ({
  loadDashboard: vi.fn(),
  loadCostBreakdown: vi.fn(),
}))
vi.mock('@/pages/dashboard/DashboardGauge.vue', () => ({
  default: { template: '<div class="test-gauge" />' },
}))
vi.mock('@/pages/dashboard/DashboardTrendChart.vue', () => ({
  default: {
    props: ['points', 'series', 'ariaLabel', 'caption'],
    template: '<div class="test-trend-chart">{{ ariaLabel }} · {{ points.length }}</div>',
  },
}))

const pmData: ProjectManagerDashboardVO = {
  projectId: '1',
  projectName: '项目一',
  pendingTaskCount: 2,
  laggingProjectCount: 1,
  pendingApprovalCount: 3,
  expiringContractCount: 0,
  pendingTasks: [],
  laggingProjects: [],
  pendingApprovals: [],
  expiringContracts: [],
}

const costData: CostManagerDashboardVO = {
  projectId: '1',
  projectName: '项目一',
  targetCost: '1000000.00',
  dynamicCost: '980000.00',
  costDeviation: '-20000.00',
  contractLockedCost: '800000.00',
  actualCost: '500000.00',
  estimatedRemainingCost: '480000.00',
  expectedProfit: '-120000.00',
  contractIncome: '1100000.00',
  trendPoints: [],
  subjectRankings: [],
  overBudgetAlerts: [],
  overdueItems: [],
  pendingPayments: [],
  ledgerRows: [],
  ledgerTotal: 0,
}

const financeData: FinanceDashboardVO = {
  projectId: '1',
  projectName: '项目一',
  pendingPaymentAmount: '90000.00',
  pendingPaymentCount: 1,
  approvedUnpaidAmount: '120000.00',
  overRatioAmount: '0.00',
  warrantyExpiringAmount: '0.00',
  totalContractAmount: '800000.00',
  totalPaidAmount: '340000.00',
  budgetAmount: '3900000.00',
  budgetConsumedAmount: '800000.00',
  budgetExecutionRate: '20.51',
  cashOutflowAmount: '340000.00',
  cashBalance: '5560000.00',
  projectProfit: '4200000.00',
  metricFormulaVersion: 'PAYMENT_CLOSED_LOOP_V1',
  trendPoints: [
    {
      month: '2026-01',
      cashOutflowAmount: '120000.00',
      cumulativePaidAmount: '120000.00',
      pendingPaymentAmount: '0.00',
    },
    {
      month: '2026-07',
      cashOutflowAmount: '40000.00',
      cumulativePaidAmount: '340000.00',
      pendingPaymentAmount: '120000.00',
    },
  ],
  pendingPayments: [
    {
      payRecordId: '99',
      contractId: '88',
      contractName: '项目管理服务合同',
      partnerName: '演示合作方',
      payAmount: '120000.00',
      payDate: '2026-07-18',
      payStatus: 'PROCESSING',
      projectId: '1',
      projectName: '项目一',
    },
  ],
  overRatioPayments: [],
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

async function mountDashboard(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const session = useSessionStore()
  session.userInfo = {
    userId: '1',
    username: 'tester',
    roles: ['USER'],
    permissions: ['dashboard:view', ...permissions],
  }
  session.status = 'authenticated'
  const workspace = useWorkspaceStore()
  workspace.setProjects([
    { value: '1', label: '项目一' },
    { value: '2', label: '项目二' },
  ])
  workspace.setReportPeriods([{ value: '2026-07', label: '2026年7月' }])
  workspace.selectProject('1')
  workspace.selectReportPeriod('2026-07')
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/dashboard', component: DashboardPage }],
  })
  await router.push('/dashboard')
  await router.isReady()
  const wrapper = mount(DashboardPage, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, workspace }
}

beforeEach(() => {
  vi.mocked(loadDashboard).mockReset()
  vi.mocked(loadCostBreakdown).mockReset()
})

describe('M2 dashboard page', () => {
  it('requests and renders only the permitted role with real service data', async () => {
    vi.mocked(loadDashboard).mockResolvedValue(pmData)
    const { wrapper } = await mountDashboard(['dashboard:project-manager:view'])

    expect(loadDashboard).toHaveBeenCalledTimes(1)
    expect(loadDashboard).toHaveBeenCalledWith(
      'pm',
      { projectId: '1', period: '2026-07' },
      expect.any(Object),
      expect.any(AbortSignal),
    )
    expect(wrapper.find('[aria-label="驾驶舱角色视图"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('待处理任务')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('项目经营健康度')
    expect(wrapper.text()).toContain('经营趋势')
    expect(wrapper.text()).toContain('经营预警与待办')
    expect(wrapper.get('.command-panel__title .dashboard-page__outline-link').text()).toBe(
      '查看并处理最高风险',
    )
    expect(wrapper.find('.highest-risk .dashboard-page__outline-link').exists()).toBe(false)
  })

  it('requests an aggregate role view when all projects and periods are selected', async () => {
    vi.mocked(loadDashboard).mockResolvedValue(pmData)
    const { wrapper, workspace } = await mountDashboard(['dashboard:project-manager:view'])
    vi.mocked(loadDashboard).mockClear()

    workspace.selectProject('')
    workspace.selectReportPeriod('')
    await flushPromises()

    expect(loadDashboard).toHaveBeenCalledWith(
      'pm',
      { projectId: null, period: null },
      expect.any(Object),
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).toContain('全部项目')
  })

  it('does not request the project-manager dashboard for a non-active project', async () => {
    vi.mocked(loadDashboard).mockResolvedValue(pmData)
    const { wrapper, workspace } = await mountDashboard(['dashboard:project-manager:view'])
    vi.mocked(loadDashboard).mockClear()

    workspace.setProjects([{ value: '2', label: '草稿项目', status: 'DRAFT' }])
    workspace.selectProject('2')
    await flushPromises()

    expect(loadDashboard).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前项目暂不支持此视图')
    expect(wrapper.text()).toContain('仅支持进行中项目')
  })

  it('ignores a stale response after project switching', async () => {
    const first = deferred<ProjectManagerDashboardVO>()
    const second = deferred<ProjectManagerDashboardVO>()
    vi.mocked(loadDashboard).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const mounting = mountDashboard(['dashboard:project-manager:view'])
    await Promise.resolve()
    const { wrapper, workspace } = await mounting

    workspace.selectProject('2')
    await Promise.resolve()
    second.resolve({ ...pmData, projectId: '2', projectName: '项目二', pendingTaskCount: 8 })
    await flushPromises()
    first.resolve({ ...pmData, pendingTaskCount: 99 })
    await flushPromises()

    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).not.toContain('99')
  })

  it('keeps the main cost dashboard visible when breakdown fails independently', async () => {
    const trendPoints = Array.from({ length: 7 }, (_, index) => ({
      month: `2026-${String(index + 1).padStart(2, '0')}`,
      targetCost: '3900000.00',
      dynamicCost: `${(index + 1) * 100000}.00`,
      costDeviation: `${(index + 1) * 100000 - 3900000}.00`,
    }))
    vi.mocked(loadDashboard)
      .mockResolvedValueOnce({ ...costData, trendPoints })
      .mockResolvedValueOnce({ ...costData, projectId: '2', trendPoints: trendPoints.slice(-2) })
    vi.mocked(loadCostBreakdown).mockRejectedValue(new Error('breakdown unavailable'))
    const { wrapper, workspace } = await mountDashboard([
      'dashboard:cost-manager:view',
      'dashboard:cost-breakdown:view',
    ])

    expect(wrapper.text()).toContain('目标成本')
    expect(wrapper.text()).toContain('100.00 万元')
    expect(wrapper.findAll('.quick-actions a')).toHaveLength(7)
    expect(wrapper.findAll('.health-metric')[3]?.classes()).toContain('is-danger')
    expect(wrapper.text()).toContain('成本分解加载失败')
    expect(wrapper.text()).toContain('主驾驶舱数据未受影响')
    expect(wrapper.get('.test-trend-chart').text()).toContain('· 7')

    await wrapper.get('button[aria-pressed="false"]').trigger('click')
    expect(wrapper.get('.test-trend-chart').text()).toContain('· 6')

    workspace.selectProject('2')
    await flushPromises()
    expect(wrapper.get('.test-trend-chart').text()).toContain('· 2')
  })

  it('renders finance payment trend, closed-loop indicators and pending work', async () => {
    vi.mocked(loadDashboard).mockResolvedValue(financeData)
    const { wrapper } = await mountDashboard(['dashboard:finance:view'])

    expect(wrapper.text()).toContain('审批中付款')
    expect(wrapper.text()).toContain('预算执行率')
    expect(wrapper.text()).toContain('公司资金余额')
    expect(wrapper.text()).toContain('资金支付趋势')
    expect(wrapper.get('.test-trend-chart').text()).toContain('本月支付、累计支付和处理中付款')
    expect(wrapper.get('.test-trend-chart').text()).toContain('2')
    expect(wrapper.text()).toContain('资金闭环指标')
    expect(wrapper.text()).toContain('合同金额')
    expect(wrapper.text()).toContain('累计支付')
    expect(wrapper.text()).toContain('项目管理服务合同')
    expect(wrapper.text()).toContain('PROCESSING')
  })
})
