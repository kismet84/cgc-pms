import type {
  CostControlOverview,
  CostLedgerPage,
  CostProjectSummary,
} from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CostControlPage from '@/pages/commercial/CostControlPage.vue'
import CostLedgerPage from '@/pages/commercial/CostLedgerPage.vue'
import CostSummaryPage from '@/pages/commercial/CostSummaryPage.vue'
import * as commercial from '@/services/commercial'
import * as projects from '@/services/projects'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/commercial', () => ({
  closeCostCorrective: vi.fn(),
  confirmCostForecast: vi.fn(),
  createCostCorrective: vi.fn(),
  createCostForecast: vi.fn(),
  loadCostSubjectOptions: vi.fn(),
  loadCostControl: vi.fn(),
  loadCostForecastTrace: vi.fn(),
  loadCostLedger: vi.fn(),
  loadCostLedgerPage: vi.fn(),
  loadCostLedgerSummary: vi.fn(),
  loadCostSummary: vi.fn(),
  loadCostSummaryHistory: vi.fn(),
  loadProjectContextOptions: vi.fn(),
  refreshCostSummary: vi.fn(),
  submitCostCorrective: vi.fn(),
  updateCostCorrective: vi.fn(),
  updateCostForecast: vi.fn(),
}))
vi.mock('@/services/projects', () => ({ loadProjectUsers: vi.fn() }))

const ledger = {
  id: '9007199254740993',
  projectId: 'P1',
  projectName: '项目一',
  amount: '9007199254740993.12',
  taxAmount: '0',
  amountWithoutTax: '9007199254740993.12',
  costType: 'DIRECT',
  sourceType: 'MAT_RECEIPT',
  costStatus: 'CONFIRMED',
}
const ledgerPage: CostLedgerPage = { records: [ledger], total: 1, pageNo: 1, pageSize: 20 }
const summary: CostProjectSummary = {
  projectId: 'P1',
  projectName: '项目一',
  targetCost: '1',
  contractLockedCost: '0',
  actualCost: '0',
  paidAmount: '0',
  estimatedRemainingCost: '0',
  dynamicCost: '1',
  contractIncome: '2',
  confirmedRevenue: '0',
  expectedProfit: '1',
  costDeviation: '0',
  responsibilityCost: '1',
  forecastAtCompletionCost: '1',
  forecastProfit: '1',
  profitMargin: '0.5',
  subjects: [],
}
const overview: CostControlOverview = {
  project: { id: 'P1' },
  activeTarget: {},
  targetItems: [],
  forecastInputItems: [],
  latestForecast: {
    id: 'F1',
    forecast_code: 'FC-1',
    forecast_at_completion_amount: '1',
    forecast_profit_amount: '1',
    cost_variance_amount: '0.1',
    status: 'DRAFT',
    version: '7',
  },
  forecastItems: [],
  correctiveActions: [
    {
      id: 'A1',
      forecast_id: 'F1',
      action_title: '纠偏一',
      expected_saving_amount: '0.1',
      status: 'DRAFT',
      version: '11',
    },
  ],
  forecastHistory: [],
  costSources: [],
  summary: {},
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((r, j) => {
    resolve = r
    reject = j
  })
  return { promise, resolve, reject }
}
function apiError(message: string, status: number) {
  return Object.assign(new Error(message), { name: 'ApiClientError', code: 'TEST_ERROR', status })
}
async function mountPage(component: typeof CostLedgerPage, path: string, permissions: string[]) {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = { userId: '1', username: 'tester', roles: ['USER'], permissions }
  session.status = 'authenticated'
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/cost/ledger', component: CostLedgerPage },
      { path: '/cost/summary', component: CostSummaryPage },
      { path: '/cost/control', component: CostControlPage },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return { wrapper, router }
}
function button(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper'], label: string) {
  return wrapper.findAll('button').find((item) => item.text().includes(label))
}

beforeEach(() => {
  vi.mocked(commercial.loadCostSubjectOptions)
    .mockReset()
    .mockResolvedValue([{ id: 'S1', subjectCode: '6001', subjectName: '材料费', status: 'ACTIVE' }])
  vi.mocked(projects.loadProjectUsers)
    .mockReset()
    .mockResolvedValue({
      records: [{ id: 'U1', username: 'owner', realName: '负责人', status: 'ACTIVE' }],
      total: 1,
      pageNo: 1,
      pageSize: 200,
    })
  vi.mocked(commercial.loadProjectContextOptions)
    .mockReset()
    .mockResolvedValue([
      { id: 'P1', projectName: '项目一', status: 'ACTIVE' },
      { id: 'P2', projectName: '项目二', status: 'ACTIVE' },
    ])
  vi.mocked(commercial.loadCostLedgerPage).mockReset().mockResolvedValue(ledgerPage)
  vi.mocked(commercial.loadCostLedgerSummary).mockReset().mockResolvedValue({
    totalAmount: '9007199254740993.12',
    totalTaxAmount: '0',
    bySourceType: {},
    byProject: {},
    byCostType: {},
  })
  vi.mocked(commercial.loadCostLedger).mockReset().mockResolvedValue(ledger)
  vi.mocked(commercial.loadCostSummary).mockReset().mockResolvedValue(summary)
  vi.mocked(commercial.loadCostSummaryHistory).mockReset().mockResolvedValue([])
  vi.mocked(commercial.refreshCostSummary).mockReset().mockResolvedValue(summary)
  vi.mocked(commercial.loadCostControl).mockReset().mockResolvedValue(overview)
  vi.mocked(commercial.loadCostForecastTrace).mockReset().mockResolvedValue(overview)
  vi.mocked(commercial.confirmCostForecast).mockReset().mockResolvedValue({})
  vi.mocked(commercial.submitCostCorrective).mockReset().mockResolvedValue({})
})

describe('M4 costs pages', () => {
  it('keeps cost ledger on the standard table and 10-row pagination contract', async () => {
    const { wrapper } = await mountPage(
      CostLedgerPage,
      '/cost/ledger?projectId=P1&period=2026-07',
      ['cost:ledger:query'],
    )

    expect(commercial.loadCostLedgerPage).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.get('table').element.closest('.v2-card')).not.toBeNull()
    const detailHeader = wrapper
      .get('table')
      .element.closest('.v2-card')
      ?.querySelector('.v2-card__header')
    expect(detailHeader?.textContent).toContain('成本总额9007199254740993.12')
    expect(detailHeader?.textContent).toContain('税额0')
    expect(wrapper.text()).not.toContain('筛选汇总')
    const pagination = wrapper.get('nav[aria-label="成本台账分页"]')
    expect(pagination.text()).toContain('共 1 条')
    expect(pagination.text()).toContain('第 1 页')
    expect(pagination.text()).not.toContain('/ 1')
  })

  it('keeps ledger and cost-control dialogs business-labelled', async () => {
    const ledgerView = await mountPage(CostLedgerPage, '/cost/ledger?projectId=P1', [
      'cost:ledger:query',
    ])
    await button(ledgerView.wrapper, '详情')!.trigger('click')
    await flushPromises()
    expect(ledgerView.wrapper.text()).toContain('已确认')
    expect(ledgerView.wrapper.text()).not.toContain('CONFIRMED')
    expect(
      ledgerView.wrapper.findAll('.v2-dialog__body dt').map((item) => item.text()),
    ).not.toContain('ID')

    const controlView = await mountPage(CostControlPage, '/cost/control?projectId=P1', [
      'cost:control:query',
      'cost:forecast:maintain',
      'cost:corrective:maintain',
    ])
    expect(controlView.wrapper.text()).not.toContain('负责人ID')
    expect(controlView.wrapper.text()).not.toContain('成本科目 S1')
  })

  it('fails closed on all three pages without cost permissions and loads no business data', async () => {
    const cases = [
      { component: CostLedgerPage, path: '/cost/ledger', title: '无权访问成本台账' },
      { component: CostSummaryPage, path: '/cost/summary', title: '无权访问成本核对' },
      { component: CostControlPage, path: '/cost/control', title: '无权访问动态利润控制' },
    ] as const
    for (const item of cases) {
      vi.clearAllMocks()
      const { wrapper } = await mountPage(item.component, item.path, [])
      expect(wrapper.text()).toContain(item.title)
      expect(commercial.loadProjectContextOptions).not.toHaveBeenCalled()
      expect(commercial.loadCostLedgerPage).not.toHaveBeenCalled()
      expect(commercial.loadCostSummary).not.toHaveBeenCalled()
      expect(commercial.loadCostControl).not.toHaveBeenCalled()
      wrapper.unmount()
    }
  })

  it('shows ledger 500 and detail 404 without creating writes', async () => {
    vi.mocked(commercial.loadCostLedgerPage).mockRejectedValueOnce(apiError('台账服务异常', 500))
    const first = await mountPage(CostLedgerPage, '/cost/ledger?projectId=P1', [
      'cost:ledger:query',
    ])
    expect(first.wrapper.text()).toContain('台账服务异常')
    expect(first.wrapper.text()).toContain('暂无成本台账')
    vi.mocked(commercial.loadCostLedgerPage).mockResolvedValueOnce(ledgerPage)
    vi.mocked(commercial.loadCostLedger).mockRejectedValueOnce(apiError('台账记录不存在', 404))
    const second = await mountPage(CostLedgerPage, '/cost/ledger?projectId=P1', [
      'cost:ledger:query',
    ])
    await button(second.wrapper, '详情')!.trigger('click')
    await flushPromises()
    expect(second.wrapper.text()).toContain('台账记录不存在')
    expect(commercial.refreshCostSummary).not.toHaveBeenCalled()
  })

  it('shows summary and control loading 500 without inventing business data', async () => {
    vi.mocked(commercial.loadCostSummary).mockRejectedValueOnce(apiError('汇总服务异常', 500))
    const summaryPage = await mountPage(CostSummaryPage, '/cost/summary?projectId=P1', [
      'cost:summary:view',
    ])
    expect(summaryPage.wrapper.text()).toContain('汇总服务异常')
    expect(summaryPage.wrapper.text()).toContain('暂无成本汇总')

    vi.mocked(commercial.loadCostControl).mockRejectedValueOnce(apiError('控制服务异常', 500))
    const controlPage = await mountPage(CostControlPage, '/cost/control?projectId=P1', [
      'cost:control:query',
    ])
    expect(controlPage.wrapper.text()).toContain('控制服务异常')
    expect(controlPage.wrapper.text()).toContain('暂无动态利润数据')
  })

  it('aborts ledger requests and ignores stale response after project and period switch', async () => {
    const old = deferred<CostLedgerPage>()
    const fresh = deferred<CostLedgerPage>()
    const signals: AbortSignal[] = []
    vi.mocked(commercial.loadCostLedgerPage)
      .mockImplementationOnce(async (_q, s) => {
        signals.push(s!)
        return old.promise
      })
      .mockImplementationOnce(async (_q, s) => {
        signals.push(s!)
        return fresh.promise
      })
    const { wrapper, router } = await mountPage(
      CostLedgerPage,
      '/cost/ledger?projectId=P1&period=2026-06',
      ['cost:ledger:query'],
    )
    await router.push('/cost/ledger?projectId=P2&period=2026-07')
    await flushPromises()
    fresh.resolve({
      ...ledgerPage,
      records: [{ ...ledger, projectId: 'P2', projectName: '最新项目' }],
    })
    await flushPromises()
    old.resolve({ ...ledgerPage, records: [{ ...ledger, projectName: '陈旧项目' }] })
    await flushPromises()
    expect(signals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('最新项目')
    expect(wrapper.text()).not.toContain('陈旧项目')
  })

  it('reloads summary after refresh 409 and keeps the conflict visible', async () => {
    vi.mocked(commercial.refreshCostSummary).mockRejectedValueOnce(apiError('汇总版本冲突', 409))
    const { wrapper } = await mountPage(
      CostSummaryPage,
      '/cost/summary?projectId=P1&period=2026-07',
      ['cost:summary:view', 'cost:summary:refresh'],
    )
    await button(wrapper, '刷新汇总')!.trigger('click')
    await flushPromises()
    expect(commercial.loadCostSummary).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('汇总版本冲突')
  })

  it('aborts summary loading and ignores a stale period response', async () => {
    const old = deferred<CostProjectSummary>()
    const fresh = deferred<CostProjectSummary>()
    const signals: AbortSignal[] = []
    vi.mocked(commercial.loadCostSummary)
      .mockImplementationOnce(async (_id, s) => {
        signals.push(s!)
        return old.promise
      })
      .mockImplementationOnce(async (_id, s) => {
        signals.push(s!)
        return fresh.promise
      })
    const { wrapper, router } = await mountPage(
      CostSummaryPage,
      '/cost/summary?projectId=P1&period=2026-06',
      ['cost:summary:view'],
    )
    await router.push('/cost/summary?projectId=P2&period=2026-07')
    await flushPromises()
    fresh.resolve({ ...summary, projectId: 'P2', projectName: '最新汇总' })
    await flushPromises()
    old.resolve({ ...summary, projectName: '陈旧汇总' })
    await flushPromises()
    expect(signals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('最新汇总')
    expect(wrapper.text()).not.toContain('陈旧汇总')
  })

  it('reloads control after forecast 409 and corrective 422, preserving CAS versions', async () => {
    vi.mocked(commercial.confirmCostForecast).mockRejectedValueOnce(apiError('预测版本冲突', 409))
    vi.mocked(commercial.submitCostCorrective).mockRejectedValueOnce(
      apiError('纠偏状态已变化', 422),
    )
    const { wrapper } = await mountPage(
      CostControlPage,
      '/cost/control?projectId=P1&period=2026-07',
      ['cost:control:query', 'cost:forecast:confirm', 'cost:corrective:submit'],
    )
    await button(wrapper, '确认预测')!.trigger('click')
    await flushPromises()
    expect(commercial.confirmCostForecast).toHaveBeenCalledWith('F1', '7')
    expect(wrapper.text()).toContain('预测版本冲突')
    await button(wrapper, '提交')!.trigger('click')
    await flushPromises()
    expect(commercial.submitCostCorrective).toHaveBeenCalledWith('A1', '11')
    expect(commercial.loadCostControl).toHaveBeenCalledTimes(3)
    expect(wrapper.text()).toContain('纠偏状态已变化')
  })

  it('aborts control loading and ignores stale project response', async () => {
    const old = deferred<CostControlOverview>()
    const fresh = deferred<CostControlOverview>()
    const signals: AbortSignal[] = []
    vi.mocked(commercial.loadCostControl)
      .mockImplementationOnce(async (_id, s) => {
        signals.push(s!)
        return old.promise
      })
      .mockImplementationOnce(async (_id, s) => {
        signals.push(s!)
        return fresh.promise
      })
    const { wrapper, router } = await mountPage(
      CostControlPage,
      '/cost/control?projectId=P1&period=2026-06',
      ['cost:control:query'],
    )
    await router.push('/cost/control?projectId=P2&period=2026-07')
    await flushPromises()
    fresh.resolve({
      ...overview,
      latestForecast: { ...overview.latestForecast, forecast_code: 'LATEST' },
    })
    await flushPromises()
    old.resolve({
      ...overview,
      latestForecast: { ...overview.latestForecast, forecast_code: 'STALE' },
    })
    await flushPromises()
    expect(signals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('LATEST')
    expect(wrapper.text()).not.toContain('STALE')
  })
})
