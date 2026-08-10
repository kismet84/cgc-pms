import type { MeasurementAmountRow } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MeasurementPageView from '@/pages/commercial/ProductionMeasurementPage.vue'
import { dismissToast, toastItems } from '@/components/toast'
import * as commercial from '@/services/commercial'
import { uploadSiteFile } from '@/services/delivery'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/services/commercial', () => ({
  closeMeasurementPeriod: vi.fn(),
  createMeasurement: vi.fn(),
  createMeasurementPeriod: vi.fn(),
  loadContractPage: vi.fn(),
  loadMeasurement: vi.fn(),
  loadMeasurementPeriods: vi.fn(),
  loadMeasurementSettlementTrace: vi.fn(),
  loadMeasurementSources: vi.fn(),
  loadMeasurements: vi.fn(),
  loadOwnerMeasurementSubmission: vi.fn(),
  loadOwnerMeasurementSubmissions: vi.fn(),
  loadProjectContextOptions: vi.fn(),
  reviewOwnerMeasurement: vi.fn(),
  submitMeasurement: vi.fn(),
  submitOwnerMeasurement: vi.fn(),
}))
vi.mock('@/services/delivery', () => ({ uploadSiteFile: vi.fn() }))

const measurement: MeasurementAmountRow = {
  id: 'M1',
  measure_code: 'ME-1',
  project_id: 'P1',
  contract_id: 'C1',
  period_id: 'P01',
  period_name: '2026-07',
  measure_date: '2026-07-25',
  current_reported_amount: '9007199254740993.12',
  cumulative_reported_amount: '9007199254740993.12',
  approval_status: 'DRAFT',
  status: 'DRAFT',
  version: '9',
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve
  })
  return { promise, resolve }
}

function apiError(message: string, status: number) {
  return Object.assign(new Error(message), { name: 'ApiClientError', code: 'TEST', status })
}

async function mountPage(path: string, permissions: string[]) {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = { userId: '1', username: 'tester', roles: ['USER'], permissions }
  session.status = 'authenticated'
  useWorkspaceStore().setProjects([
    { value: 'P1', label: '项目一', status: 'ACTIVE' },
    { value: 'P2', label: '项目二', status: 'ACTIVE' },
  ])
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/production-measurement', component: MeasurementPageView }],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(MeasurementPageView, {
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return { wrapper, router }
}

function button(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper'], label: string) {
  return wrapper.findAll('button').find((item) => item.text().includes(label))
}

beforeEach(() => {
  toastItems.slice().forEach((toast) => dismissToast(toast.id))
  vi.mocked(commercial.loadProjectContextOptions)
    .mockReset()
    .mockResolvedValue([
      { id: 'P1', projectName: '项目一', status: 'ACTIVE' },
      { id: 'P2', projectName: '项目二', status: 'ACTIVE' },
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
    .mockResolvedValue([
      {
        id: 'P01',
        project_id: 'P1',
        contract_id: 'C1',
        period_name: '2026-07',
        status: 'OPEN',
        version: '2',
      },
    ])
  vi.mocked(commercial.loadMeasurements).mockReset().mockResolvedValue([measurement])
  vi.mocked(commercial.loadMeasurement)
    .mockReset()
    .mockResolvedValue({ ...measurement, lines: [{ id: 'ML1', item_name: '主体结构' }] })
  vi.mocked(commercial.loadOwnerMeasurementSubmissions).mockReset().mockResolvedValue([])
  vi.mocked(commercial.loadMeasurementSources).mockReset().mockResolvedValue([])
  vi.mocked(commercial.submitMeasurement).mockReset().mockResolvedValue({})
  vi.mocked(uploadSiteFile).mockReset().mockResolvedValue({ id: 'F1' })
})

describe('M4 production measurement page', () => {
  it('uses one measurement table and expands owner submission versions', async () => {
    vi.mocked(commercial.loadMeasurements).mockResolvedValueOnce([
      { ...measurement, status: 'OWNER_SUBMITTED', approval_status: 'APPROVED' },
    ])
    vi.mocked(commercial.loadOwnerMeasurementSubmissions).mockResolvedValueOnce([
      {
        id: 'S1',
        measurement_id: 'M1',
        measure_code: 'ME-1',
        submission_code: 'OMS-202607-001-R2',
        external_document_no: 'OWNER-DOC-2026-07',
        revision_no: '2',
        submitted_at: '2026-07-25T10:00:00',
        submitted_amount: '9007199254740993.12',
        confirmed_amount: '0',
        status: 'SUBMITTED',
      },
    ])
    const { wrapper } = await mountPage('/production-measurement?projectId=P1&period=2026-07', [
      'measurement:query',
    ])

    const table = wrapper.get('[aria-label="产值计量列表"]')
    expect(table.findAll('th').map((item) => item.text())).toEqual([
      '计量编号',
      '所属项目',
      '计量期间',
      '计量日期',
      '本期申报',
      '累计申报',
      '内部状态',
      '业主状态',
      '时间窗口',
      '操作',
    ])
    expect(table.text()).toContain('项目一')
    expect(table.text()).toContain('ME-1')
    expect(table.text()).toContain('内部已通过')
    expect(table.text()).toContain('已报送')
    expect(wrapper.findAll('button').some((item) => item.text() === '报送记录')).toBe(false)
    expect(vi.mocked(commercial.loadMeasurements).mock.calls[0]?.[0]).toMatchObject({
      startDate: '2026-07-01',
      endDate: '2026-07-31',
    })
    await button(wrapper, 'ME-1')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[aria-label="ME-1 业主报送记录"]').text()).toContain('OMS-202607-001-R2')
    expect(wrapper.get('[aria-label="ME-1 业主报送记录"]').text()).toContain('V2')
  })

  it('shows one empty state when the current filter has no records', async () => {
    vi.mocked(commercial.loadMeasurements).mockResolvedValueOnce([])
    const { wrapper } = await mountPage('/production-measurement?projectId=P1&period=2026-07', [
      'measurement:query',
    ])
    expect(wrapper.text()).toContain('暂无产值计量')
    expect(wrapper.find('[aria-label="产值计量列表"]').exists()).toBe(false)
  })

  it('selects project and owner contract inside both create dialogs', async () => {
    const { wrapper } = await mountPage('/production-measurement?projectId=P1', [
      'measurement:query',
      'measurement:maintain',
      'file:upload',
    ])
    await button(wrapper, '新建期间')!.trigger('click')
    await flushPromises()
    expect(commercial.loadContractPage).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 100,
      projectId: 'P1',
      contractType: 'MAIN',
      approvalStatus: 'APPROVED',
      contractStatus: 'PERFORMING',
    })
    expect(wrapper.get('[role="dialog"]').text()).toContain('业主合同')
    expect(wrapper.get('.measurement-page__period-form').attributes('id')).toBe(
      'measurement-period-form',
    )
    expect(wrapper.get('.measurement-page__period-dates').text()).toContain('日期范围')
    expect(wrapper.get('button[form="measurement-period-form"]').text()).toContain('保存期间')
    await wrapper.get('[role="dialog"]').get('button[aria-label="关闭对话框"]').trigger('click')
    await button(wrapper, '新建计量')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('业主合同')
  })

  it('recovers draft evidence using server document types', async () => {
    const { wrapper } = await mountPage('/production-measurement?projectId=P1', [
      'measurement:query',
      'measurement:maintain',
      'file:upload',
    ])
    await button(wrapper, '补传/更新计量依据')!.trigger('click')
    const file = new File(['evidence'], 'measurement.pdf', { type: 'application/pdf' })
    const lineFile = new File(['line evidence'], 'measurement-line.pdf', {
      type: 'application/pdf',
    })
    const input = wrapper.get('input[aria-label="计量依据"]')
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    const lineInput = wrapper.get('input[aria-label="主体结构现场完成依据"]')
    Object.defineProperty(lineInput.element, 'files', { configurable: true, value: [lineFile] })
    await lineInput.trigger('change')
    await wrapper.get('#measurement-evidence-form').trigger('submit')
    await flushPromises()
    expect(uploadSiteFile).toHaveBeenNthCalledWith(
      1,
      file,
      'PRODUCTION_MEASUREMENT',
      'M1',
      'MEASUREMENT_GENERAL',
    )
    expect(uploadSiteFile).toHaveBeenNthCalledWith(
      2,
      lineFile,
      'PRODUCTION_MEASUREMENT',
      'M1',
      'ML_ML1',
    )
  })

  it('puts status before create actions and applies it immediately', async () => {
    const { wrapper, router } = await mountPage('/production-measurement?projectId=P1', [
      'measurement:query',
      'measurement:maintain',
    ])
    const actions = wrapper.get('.v2-card__actions > .actions')
    expect(actions.text().indexOf('全部状态')).toBeLessThan(actions.text().indexOf('新建期间'))
    expect(button(wrapper, '查询')).toBeUndefined()
    await actions.get('select').setValue('DRAFT')
    await flushPromises()
    expect(router.currentRoute.value.query.status).toBe('DRAFT')
  })

  it('fails closed without route permission', async () => {
    const { wrapper } = await mountPage('/production-measurement', [])
    expect(wrapper.text()).toContain('无权访问产值计量')
    expect(commercial.loadMeasurements).not.toHaveBeenCalled()
    expect(commercial.loadProjectContextOptions).not.toHaveBeenCalled()
  })

  it('aborts stale project responses and reports list failures', async () => {
    vi.mocked(commercial.loadMeasurements).mockRejectedValueOnce(apiError('计量服务异常', 500))
    await mountPage('/production-measurement?projectId=P1', ['measurement:query'])
    expect(toastItems.some((toast) => toast.message.includes('计量服务异常'))).toBe(true)

    const oldRequest = deferred<MeasurementAmountRow[]>()
    const freshRequest = deferred<MeasurementAmountRow[]>()
    const signals: AbortSignal[] = []
    vi.mocked(commercial.loadMeasurements)
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return oldRequest.promise
      })
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return freshRequest.promise
      })
    const page = await mountPage('/production-measurement?projectId=P1&period=2026-06', [
      'measurement:query',
    ])
    await page.router.push('/production-measurement?projectId=P2&period=2026-07')
    await flushPromises()
    freshRequest.resolve([{ ...measurement, measure_code: 'LATEST' }])
    await flushPromises()
    oldRequest.resolve([{ ...measurement, measure_code: 'STALE' }])
    await flushPromises()
    expect(signals[0]?.aborted).toBe(true)
    expect(page.wrapper.text()).toContain('LATEST')
    expect(page.wrapper.text()).not.toContain('STALE')
  })

  it('reloads authoritative data after submit conflict', async () => {
    vi.mocked(commercial.submitMeasurement).mockRejectedValueOnce(apiError('计量版本冲突', 409))
    const { wrapper } = await mountPage('/production-measurement?projectId=P1', [
      'measurement:query',
      'measurement:submit',
    ])
    await button(wrapper, '提交内部审批')!.trigger('click')
    await flushPromises()
    expect(commercial.submitMeasurement).toHaveBeenCalledWith('M1', '9')
    expect(commercial.loadMeasurements).toHaveBeenCalledTimes(2)
    expect(toastItems.some((toast) => toast.message.includes('计量版本冲突'))).toBe(true)
  })
})
