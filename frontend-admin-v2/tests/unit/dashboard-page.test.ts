import type {
  CostBreakdownVO,
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

const costBreakdownData: CostBreakdownVO = {
  projectId: '1',
  projectName: '项目一',
  targetCost: '3900000.00',
  dynamicCost: '3970000.00',
  expectedProfit: '1030000.00',
  subjectBreakdowns: [
    {
      costSubjectId: '900001',
      costSubjectName: '合同履约成本',
      level: 1,
      parentSubjectId: '0',
      targetCost: '3900000.00',
      contractLockedCost: '3100000.00',
      actualCost: '2400000.00',
      dynamicCost: '3970000.00',
      costDeviation: '70000.00',
    },
    {
      costSubjectId: '900010',
      costSubjectName: '招投标及前期费用',
      level: 2,
      parentSubjectId: '900001',
      targetCost: '200000.00',
      contractLockedCost: '160000.00',
      actualCost: '150000.00',
      dynamicCost: '180000.00',
      costDeviation: '-20000.00',
    },
  ],
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
  contractFundBreakdowns: [
    {
      contractId: '88',
      projectId: '1',
      projectName: '项目一',
      contractCode: 'M52-SERVICE-C',
      contractName: '项目管理服务合同',
      contractAmount: '800000.00',
      paidAmount: '340000.00',
      approvingAmount: '90000.00',
      approvedUnpaidAmount: '120000.00',
      remainingAmount: '460000.00',
      paymentRatio: '42.50',
      paymentRecords: [
        {
          payRecordId: '99',
          recordCode: 'PMT-20260718-001',
          contractId: '88',
          contractName: '项目管理服务合同',
          partnerName: '演示合作方',
          payAmount: '120000.00',
          payDate: '2026-07-18',
          payStatus: 'SUCCESS',
          projectId: '1',
          projectName: '项目一',
        },
      ],
    },
  ],
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
    expect(wrapper.text()).toContain('经营动态')
    expect(wrapper.text()).not.toContain('当前角色暂无趋势数据')
    expect(wrapper.text()).toContain('经营预警与待办')
    expect(wrapper.get('.command-panel__title .dashboard-page__outline-link').text()).toBe(
      '查看最高风险',
    )
    expect(wrapper.find('.highest-risk .dashboard-page__outline-link').exists()).toBe(false)
  })

  it('filters by semantic risk level and the header action selects highest risks', async () => {
    vi.mocked(loadDashboard).mockResolvedValue({
      ...costData,
      overBudgetAlerts: [
        {
          alertType: 'COST_OVER_BUDGET',
          severity: 'MEDIUM',
          message: '一般关注事项',
          projectId: '1',
          projectName: '项目一',
          triggeredAt: '2026-07-20 10:00:00',
        },
        {
          alertType: 'COST_OVER_BUDGET',
          severity: 'HIGH',
          message: '最高风险事项',
          projectId: '1',
          projectName: '项目一',
          triggeredAt: '2026-07-20 11:00:00',
        },
        {
          alertType: 'COST_OVER_BUDGET',
          severity: 'LOW',
          message: '低风险事项',
          projectId: '1',
          projectName: '项目一',
          triggeredAt: '2026-07-20 12:00:00',
        },
        {
          alertType: 'COST_OVER_BUDGET',
          severity: 'INFO',
          message: '其他事项',
          projectId: '1',
          projectName: '项目一',
          triggeredAt: '2026-07-20 13:00:00',
        },
      ],
    })
    const { wrapper } = await mountDashboard(['dashboard:cost-manager:view'])
    const filter = wrapper.get('.risk-filter')
    const riskList = wrapper.get('#risk-list')

    await filter
      .findAll('button')
      .find((button) => button.text() === '中')!
      .trigger('click')
    expect(riskList.text()).toContain('一般关注事项')
    expect(riskList.text()).not.toContain('最高风险事项')
    expect(wrapper.get('.risk-level').text()).toBe('中')

    await wrapper.get('.command-panel__title .dashboard-page__outline-link').trigger('click')
    expect(filter.get('summary').text()).toBe('高')
    expect(riskList.text()).toContain('最高风险事项')
    expect(riskList.text()).not.toContain('一般关注事项')
    expect(wrapper.get('.risk-level').text()).toBe('高')
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

  it('renders canonical cost subjects and toggles the second level', async () => {
    vi.mocked(loadDashboard).mockResolvedValue(costData)
    vi.mocked(loadCostBreakdown).mockResolvedValue(costBreakdownData)
    const { wrapper } = await mountDashboard([
      'dashboard:cost-manager:view',
      'dashboard:cost-breakdown:view',
    ])
    const breakdownPanel = wrapper.get('#cost-breakdown')

    expect(loadCostBreakdown).toHaveBeenCalledWith('1', expect.any(Object), expect.any(AbortSignal))
    expect(breakdownPanel.findAll('tbody tr')).toHaveLength(1)
    expect(breakdownPanel.text()).toContain('合同履约成本')
    expect(breakdownPanel.text()).not.toContain('招投标及前期费用')

    await breakdownPanel.get('button[aria-expanded="false"]').trigger('click')
    expect(breakdownPanel.findAll('tbody tr')).toHaveLength(2)
    expect(breakdownPanel.text()).toContain('招投标及前期费用')
    expect(breakdownPanel.text()).toContain('¥−20,000.00')

    await breakdownPanel.get('button[aria-expanded="true"]').trigger('click')
    expect(breakdownPanel.findAll('tbody tr')).toHaveLength(1)
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
    const breakdown = wrapper.get('#finance-contract-breakdown')
    expect(breakdown.text()).toContain('合同资金分解')
    expect(breakdown.findAll('tbody tr')).toHaveLength(1)
    await breakdown.get('button[aria-expanded="false"]').trigger('click')
    expect(breakdown.findAll('tbody tr')).toHaveLength(2)
    expect(breakdown.text()).toContain('PMT-20260718-001 · 2026-07-18')
    expect(breakdown.text()).toContain('SUCCESS')
  })
})
