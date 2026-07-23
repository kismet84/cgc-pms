import type {
  BudgetPage,
  MeasurementAmountRow,
  ProjectBudgetRecord,
} from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BudgetPageView from '@/pages/commercial/BudgetPage.vue'
import MeasurementPageView from '@/pages/commercial/ProductionMeasurementPage.vue'
import * as commercial from '@/services/commercial'
import { useSessionStore } from '@/stores/session'
vi.mock('@/services/commercial', () => ({
  createBudget: vi.fn(),
  deleteBudget: vi.fn(),
  loadBudget: vi.fn(),
  loadBudgetAvailability: vi.fn(),
  loadBudgetPage: vi.fn(),
  loadContractPage: vi.fn(),
  loadMeasurement: vi.fn(),
  loadMeasurementPeriods: vi.fn(),
  loadMeasurementSettlementTrace: vi.fn(),
  loadMeasurementSources: vi.fn(),
  loadMeasurements: vi.fn(),
  loadOwnerMeasurementSubmission: vi.fn(),
  loadOwnerMeasurementSubmissions: vi.fn(),
  loadProjectContextOptions: vi.fn(),
  saveBudgetLines: vi.fn(),
  submitBudget: vi.fn(),
  updateBudget: vi.fn(),
  closeMeasurementPeriod: vi.fn(),
  createMeasurement: vi.fn(),
  createMeasurementPeriod: vi.fn(),
  reviewOwnerMeasurement: vi.fn(),
  submitMeasurement: vi.fn(),
  submitOwnerMeasurement: vi.fn(),
}))
vi.mock('@/services/delivery', () => ({ uploadSiteFile: vi.fn() }))
const budget: ProjectBudgetRecord = {
  id: '9007199254740993',
  projectId: 'P1',
  versionNo: 'V1',
  budgetName: '项目预算',
  totalAmount: '9007199254740993.12',
  approvalStatus: 'DRAFT',
  status: 'ACTIVE',
  active: false,
  version: '7',
  lines: [
    {
      id: 'L1',
      costSubjectId: 'S1',
      costSubjectName: '材料费',
      budgetAmount: '9007199254740993.12',
      reservedAmount: '0',
      consumedAmount: '-0.01',
      availableAmount: '9007199254740993.13',
    },
  ],
}
const budgetPage: BudgetPage = { records: [budget], total: 1, pageNo: 1, pageSize: 20 }
const measurement: MeasurementAmountRow = {
  id: 'M1',
  measure_code: 'ME-1',
  project_id: 'P1',
  period_name: '2026-07',
  current_reported_amount: '9007199254740993.12',
  cumulative_reported_amount: '9007199254740993.12',
  status: 'DRAFT',
  version: '9',
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
  return Object.assign(new Error(message), { name: 'ApiClientError', code: 'TEST', status })
}
async function mountPage(component: typeof BudgetPageView, path: string, permissions: string[]) {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = { userId: '1', username: 'tester', roles: ['USER'], permissions }
  session.status = 'authenticated'
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/budget', component: BudgetPageView },
      { path: '/production-measurement', component: MeasurementPageView },
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
  vi.mocked(commercial.loadProjectContextOptions)
    .mockReset()
    .mockResolvedValue([
      { id: 'P1', projectName: '项目一', status: 'ACTIVE' },
      { id: 'P2', projectName: '项目二', status: 'ACTIVE' },
    ])
  vi.mocked(commercial.loadBudgetPage).mockReset().mockResolvedValue(budgetPage)
  vi.mocked(commercial.loadBudget).mockReset().mockResolvedValue(budget)
  vi.mocked(commercial.loadBudgetAvailability)
    .mockReset()
    .mockResolvedValue([
      {
        budgetId: budget.id,
        budgetLineId: 'L1',
        projectId: 'P1',
        costSubjectId: 'S1',
        budgetAmount: '9007199254740993.12',
        reservedAmount: '0',
        consumedAmount: '-0.01',
        availableAmount: '9007199254740993.13',
      },
    ])
  vi.mocked(commercial.loadContractPage)
    .mockReset()
    .mockResolvedValue({
      records: [
        {
          id: 'C1',
          tenantId: 'T1',
          orgId: 'O1',
          contractCode: 'C1',
          contractName: '业主合同',
          contractType: 'MAIN',
          projectId: 'P1',
          partyAId: 'A1',
          partyBId: 'B1',
          contractAmount: '1',
          taxRate: '0',
          taxAmount: '0',
          amountWithoutTax: '1',
          signDate: '2026-01-01',
          startDate: '2026-01-01',
          endDate: '2026-12-31',
          approvalStatus: 'APPROVED',
          contractStatus: 'PERFORMING',
          createdAt: '',
          updatedAt: '',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 100,
    })
  vi.mocked(commercial.loadMeasurementPeriods)
    .mockReset()
    .mockResolvedValue([{ id: 'P01', period_name: '2026-07', status: 'OPEN', version: '2' }])
  vi.mocked(commercial.loadMeasurements).mockReset().mockResolvedValue([measurement])
  vi.mocked(commercial.loadOwnerMeasurementSubmissions).mockReset().mockResolvedValue([])
  vi.mocked(commercial.loadMeasurementSources).mockReset().mockResolvedValue([])
  vi.mocked(commercial.loadMeasurement).mockReset().mockResolvedValue(measurement)
  vi.mocked(commercial.createBudget).mockReset().mockResolvedValue('NEW-1')
  vi.mocked(commercial.updateBudget).mockReset().mockResolvedValue()
  vi.mocked(commercial.saveBudgetLines).mockReset().mockResolvedValue()
  vi.mocked(commercial.deleteBudget).mockReset().mockResolvedValue()
  vi.mocked(commercial.submitBudget).mockReset()
  vi.mocked(commercial.submitMeasurement).mockReset().mockResolvedValue({})
})
describe('M4 budget and measurement pages', () => {
  it('keeps project budget on the standard table and 10-row pagination contract', async () => {
    const { wrapper } = await mountPage(BudgetPageView, '/budget?projectId=P1&period=2026-07', [
      'budget:query',
    ])

    expect(commercial.loadBudgetPage).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.get('table').element.closest('.v2-card')).not.toBeNull()
    const pagination = wrapper.get('nav[aria-label="项目预算分页"]')
    expect(pagination.text()).toContain('共 1 条')
    expect(pagination.text()).toContain('第 1 页')
    expect(pagination.text()).not.toContain('/ 1')
    expect(wrapper.get('tbody').text()).toContain('草稿')
    expect(wrapper.get('tbody').text()).toContain('已启用')
    expect(wrapper.get('tbody').text()).not.toMatch(/\b(?:DRAFT|ACTIVE)\b/)
  })

  it('fails closed without route permissions and sends no business requests', async () => {
    const b = await mountPage(BudgetPageView, '/budget', [])
    expect(b.wrapper.text()).toContain('无权访问项目预算')
    expect(commercial.loadBudgetPage).not.toHaveBeenCalled()
    const m = await mountPage(MeasurementPageView, '/production-measurement', [])
    expect(m.wrapper.text()).toContain('无权访问产值计量')
    expect(commercial.loadMeasurements).not.toHaveBeenCalled()
    expect(commercial.loadProjectContextOptions).not.toHaveBeenCalled()
  })
  it('shows list 500 and detail 404 for both pages', async () => {
    vi.mocked(commercial.loadBudgetPage).mockRejectedValueOnce(apiError('预算服务异常', 500))
    const b500 = await mountPage(BudgetPageView, '/budget?projectId=P1', ['budget:query'])
    expect(b500.wrapper.text()).toContain('预算服务异常')
    vi.mocked(commercial.loadBudgetPage).mockResolvedValueOnce(budgetPage)
    vi.mocked(commercial.loadBudget).mockRejectedValueOnce(apiError('预算不存在', 404))
    const b404 = await mountPage(BudgetPageView, '/budget?projectId=P1', ['budget:query'])
    await button(b404.wrapper, '详情')!.trigger('click')
    await flushPromises()
    expect(b404.wrapper.text()).toContain('预算不存在')
    vi.mocked(commercial.loadMeasurements).mockRejectedValueOnce(apiError('计量服务异常', 500))
    const m500 = await mountPage(MeasurementPageView, '/production-measurement?projectId=P1', [
      'measurement:query',
    ])
    expect(m500.wrapper.text()).toContain('计量服务异常')
    vi.mocked(commercial.loadMeasurements).mockResolvedValueOnce([measurement])
    vi.mocked(commercial.loadMeasurement).mockRejectedValueOnce(apiError('计量不存在', 404))
    const m404 = await mountPage(MeasurementPageView, '/production-measurement?projectId=P1', [
      'measurement:query',
    ])
    await button(m404.wrapper, '详情')!.trigger('click')
    await flushPromises()
    expect(m404.wrapper.text()).toContain('计量不存在')
  })
  it('aborts budget and measurement requests and ignores stale project/period responses', async () => {
    const oldBudget = deferred<BudgetPage>()
    const freshBudget = deferred<BudgetPage>()
    const budgetSignals: AbortSignal[] = []
    vi.mocked(commercial.loadBudgetPage)
      .mockImplementationOnce(async (_q, s) => {
        budgetSignals.push(s!)
        return oldBudget.promise
      })
      .mockImplementationOnce(async (_q, s) => {
        budgetSignals.push(s!)
        return freshBudget.promise
      })
    const b = await mountPage(BudgetPageView, '/budget?projectId=P1&period=2026-06', [
      'budget:query',
    ])
    await b.router.push('/budget?projectId=P2&period=2026-07')
    await flushPromises()
    freshBudget.resolve({
      ...budgetPage,
      records: [{ ...budget, projectId: 'P2', budgetName: '最新预算' }],
    })
    await flushPromises()
    oldBudget.resolve({ ...budgetPage, records: [{ ...budget, budgetName: '陈旧预算' }] })
    await flushPromises()
    expect(budgetSignals[0]?.aborted).toBe(true)
    expect(b.wrapper.text()).toContain('最新预算')
    expect(b.wrapper.text()).not.toContain('陈旧预算')
    const oldMeasurement = deferred<MeasurementAmountRow[]>()
    const freshMeasurement = deferred<MeasurementAmountRow[]>()
    const measurementSignals: AbortSignal[] = []
    vi.mocked(commercial.loadMeasurements)
      .mockImplementationOnce(async (_q, s) => {
        measurementSignals.push(s!)
        return oldMeasurement.promise
      })
      .mockImplementationOnce(async (_q, s) => {
        measurementSignals.push(s!)
        return freshMeasurement.promise
      })
    const m = await mountPage(
      MeasurementPageView,
      '/production-measurement?projectId=P1&period=2026-06',
      ['measurement:query'],
    )
    await m.router.push('/production-measurement?projectId=P2&period=2026-07')
    await flushPromises()
    freshMeasurement.resolve([{ ...measurement, measure_code: 'LATEST' }])
    await flushPromises()
    oldMeasurement.resolve([{ ...measurement, measure_code: 'STALE' }])
    await flushPromises()
    expect(measurementSignals[0]?.aborted).toBe(true)
    expect(m.wrapper.text()).toContain('LATEST')
    expect(m.wrapper.text()).not.toContain('STALE')
  })
  it('deduplicates CAS submits and reloads after 409', async () => {
    const pending = deferred<void>()
    vi.mocked(commercial.submitBudget).mockReturnValueOnce(pending.promise)
    const b = await mountPage(BudgetPageView, '/budget?projectId=P1', [
      'budget:query',
      'budget:submit',
    ])
    await button(b.wrapper, '提交')!.trigger('click')
    await button(b.wrapper, '提交')!.trigger('click')
    expect(commercial.submitBudget).toHaveBeenCalledTimes(1)
    expect(commercial.submitBudget).toHaveBeenCalledWith(budget.id, '7')
    pending.resolve()
    await flushPromises()
    vi.mocked(commercial.submitMeasurement).mockRejectedValueOnce(apiError('计量版本冲突', 409))
    const m = await mountPage(MeasurementPageView, '/production-measurement?projectId=P1', [
      'measurement:query',
      'measurement:submit',
    ])
    await button(m.wrapper, '提交内部审批')!.trigger('click')
    await flushPromises()
    expect(commercial.submitMeasurement).toHaveBeenCalledWith('M1', '9')
    expect(commercial.loadMeasurements).toHaveBeenCalledTimes(2)
    expect(m.wrapper.text()).toContain('计量版本冲突')
  })
  it('retains create and edit form values on 422 without false success or reload', async () => {
    vi.mocked(commercial.createBudget).mockRejectedValueOnce(apiError('版本号重复', 422))
    const created = await mountPage(BudgetPageView, '/budget?projectId=P1', [
      'budget:query',
      'budget:add',
    ])
    await button(created.wrapper, '新建预算')!.trigger('click')
    await created.wrapper.get('input[aria-label="预算版本号"]').setValue('V-NEW')
    await created.wrapper.get('input[aria-label="预算名称"]').setValue('新预算保留值')
    await created.wrapper.get('input[aria-label="预算总额"]').setValue('88.88')
    await created.wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(commercial.createBudget).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: 'P1', version: null, versionNo: 'V-NEW' }),
    )
    expect(created.wrapper.get('input[aria-label="预算名称"]').element.value).toBe('新预算保留值')
    expect(created.wrapper.text()).toContain('版本号重复')
    expect(created.wrapper.text()).not.toContain('预算已创建')
    expect(commercial.loadBudgetPage).toHaveBeenCalledTimes(1)

    vi.mocked(commercial.updateBudget).mockRejectedValueOnce(apiError('预算校验失败', 422))
    const edited = await mountPage(BudgetPageView, '/budget?projectId=P1', [
      'budget:query',
      'budget:edit',
    ])
    await button(edited.wrapper, '编辑')!.trigger('click')
    await flushPromises()
    await edited.wrapper.get('input[aria-label="预算名称"]').setValue('编辑后保留值')
    await edited.wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(commercial.updateBudget).toHaveBeenCalledWith(
      budget.id,
      expect.objectContaining({ budgetName: '编辑后保留值', version: '7' }),
    )
    expect(edited.wrapper.get('input[aria-label="预算名称"]').element.value).toBe('编辑后保留值')
    expect(edited.wrapper.text()).toContain('预算校验失败')
    expect(edited.wrapper.text()).not.toContain('预算已更新')
    expect(commercial.loadBudgetPage).toHaveBeenCalledTimes(2)
  })
  it('saves lines with CAS then rereads authoritative availability', async () => {
    vi.mocked(commercial.loadBudgetAvailability)
      .mockResolvedValueOnce([
        {
          budgetId: budget.id,
          budgetLineId: 'L1',
          projectId: 'P1',
          costSubjectId: 'S1',
          budgetAmount: '10',
          reservedAmount: '1',
          consumedAmount: '0.11',
          availableAmount: '8.89',
        },
      ])
      .mockResolvedValueOnce([
        {
          budgetId: budget.id,
          budgetLineId: 'L1',
          projectId: 'P1',
          costSubjectId: 'S1',
          budgetAmount: '10',
          reservedAmount: '2',
          consumedAmount: '0.22',
          availableAmount: '7.78',
        },
      ])
    const page = await mountPage(BudgetPageView, '/budget?projectId=P1', [
      'budget:query',
      'budget:edit',
    ])
    await button(page.wrapper, '详情')!.trigger('click')
    await flushPromises()
    expect(page.wrapper.text()).toContain('8.89')
    await button(page.wrapper, '保存明细')!.trigger('click')
    await flushPromises()
    expect(commercial.saveBudgetLines).toHaveBeenCalledWith(budget.id, budget.lines, '7')
    expect(commercial.loadBudgetAvailability).toHaveBeenCalledTimes(2)
    expect(page.wrapper.text()).toContain('7.78')
    expect(page.wrapper.text()).not.toContain('8.89')
    expect(page.wrapper.text()).toContain('预算明细已保存')
  })
  it('retains edited lines on 422 without a success notice or authority overwrite', async () => {
    vi.mocked(commercial.saveBudgetLines).mockRejectedValueOnce(apiError('明细校验失败', 422))
    const page = await mountPage(BudgetPageView, '/budget?projectId=P1', [
      'budget:query',
      'budget:edit',
    ])
    await button(page.wrapper, '详情')!.trigger('click')
    await flushPromises()
    await page.wrapper.get('input[aria-label="预算金额"]').setValue('77.77')
    await button(page.wrapper, '保存明细')!.trigger('click')
    await flushPromises()
    expect(commercial.saveBudgetLines).toHaveBeenCalledWith(
      budget.id,
      [expect.objectContaining({ budgetAmount: '77.77' })],
      '7',
    )
    expect(page.wrapper.get('input[aria-label="预算金额"]').element.value).toBe('77.77')
    expect(page.wrapper.text()).toContain('明细校验失败')
    expect(page.wrapper.text()).not.toContain('预算明细已保存')
    expect(commercial.loadBudgetPage).toHaveBeenCalledTimes(1)
    expect(commercial.loadBudgetAvailability).toHaveBeenCalledTimes(1)
  })
  it('keeps the budget visible and reports delete 422 without false success', async () => {
    vi.mocked(commercial.deleteBudget).mockRejectedValueOnce(apiError('预算已被引用', 422))
    const page = await mountPage(BudgetPageView, '/budget?projectId=P1', [
      'budget:query',
      'budget:delete',
    ])
    await button(page.wrapper, '删除')!.trigger('click')
    await flushPromises()
    expect(commercial.deleteBudget).toHaveBeenCalledWith(budget.id, '7')
    expect(page.wrapper.text()).toContain('预算已被引用')
    expect(page.wrapper.text()).toContain('项目预算')
    expect(page.wrapper.text()).not.toContain('预算已删除')
    expect(commercial.loadBudgetPage).toHaveBeenCalledTimes(1)
  })
})
