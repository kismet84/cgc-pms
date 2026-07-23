import type {
  VariationOwnerSubmissionRecord,
  VariationPage,
  VariationRecord,
} from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VariationPageView from '@/pages/commercial/VariationPage.vue'
import {
  createVariation,
  deleteVariation,
  loadVariation,
  loadVariationPage,
  loadVariationTrace,
  reviewVariationOwner,
  saveVariationItems,
  submitVariation,
  submitVariationToOwner,
  updateVariation,
} from '@/services/commercial'
import { uploadSiteFile } from '@/services/delivery'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/commercial', () => ({
  createVariation: vi.fn(),
  deleteVariation: vi.fn(),
  loadVariation: vi.fn(),
  loadVariationPage: vi.fn(),
  loadVariationTrace: vi.fn(),
  reviewVariationOwner: vi.fn(),
  saveVariationItems: vi.fn(),
  submitVariation: vi.fn(),
  submitVariationToOwner: vi.fn(),
  updateVariation: vi.fn(),
}))

vi.mock('@/services/delivery', () => ({ uploadSiteFile: vi.fn() }))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve
  })
  return { promise, resolve }
}

function apiError(message: string, status: number, code = 'TEST_ERROR') {
  return Object.assign(new Error(message), { name: 'ApiClientError', status, code })
}

const baseRecord: VariationRecord = {
  id: '9',
  tenantId: '1',
  projectId: 'P1',
  contractId: 'C1',
  partnerId: 'A1',
  varCode: 'VO-009',
  varName: '设计变更一',
  reportedAmount: '1234567.89',
  approvedAmount: '1200000.00',
  confirmedAmount: '0.00',
  estimatedCostAmount: '800000.00',
  eventDate: '2026-07-01',
  claimDeadline: '2026-07-31',
  varType: 'DESIGN',
  direction: 'COST',
  approvalStatus: 'DRAFT',
  ownerStatus: 'INTERNAL_APPROVED',
  projectName: '项目一',
  contractName: '合同一',
  version: '3',
  items: [
    {
      id: 'I1',
      varOrderId: '9',
      itemName: '钢筋调整',
      unit: '吨',
      quantity: '10.500',
      unitPrice: '5000.00',
      amount: '52500.00',
      claimUnitPrice: '5200.00',
      claimAmount: '54600.00',
      costSubjectId: 'CS1',
    },
  ],
  ownerSubmissions: [],
}

const page: VariationPage = { records: [baseRecord], total: 1, pageNo: 1, pageSize: 10 }

async function mountPage(path: string, permissions: string[]) {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = { userId: '1', username: 'tester', roles: ['USER'], permissions }
  session.status = 'authenticated'
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/variation/order', component: VariationPageView }],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(VariationPageView, {
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return { wrapper, router }
}

function button(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper'], label: string) {
  return wrapper.findAll('button').find((item) => item.text().includes(label))!
}

async function chooseFile(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper']) {
  const input = wrapper.get('input[type="file"]')
  const file = new File(['evidence'], 'owner.pdf', { type: 'application/pdf' })
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
  return file
}

beforeEach(() => {
  vi.mocked(loadVariationPage).mockReset().mockResolvedValue(page)
  vi.mocked(loadVariation).mockReset().mockResolvedValue(baseRecord)
  vi.mocked(loadVariationTrace).mockReset().mockResolvedValue({ variation: baseRecord })
  vi.mocked(createVariation).mockReset().mockResolvedValue('10')
  vi.mocked(updateVariation).mockReset().mockResolvedValue()
  vi.mocked(deleteVariation).mockReset().mockResolvedValue()
  vi.mocked(saveVariationItems).mockReset().mockResolvedValue()
  vi.mocked(submitVariation).mockReset().mockResolvedValue()
  vi.mocked(submitVariationToOwner).mockReset().mockResolvedValue({ id: 'S1' })
  vi.mocked(reviewVariationOwner).mockReset().mockResolvedValue({ id: 'S1' })
  vi.mocked(uploadSiteFile).mockReset().mockResolvedValue({
    id: 'F1',
    businessType: 'VARIATION',
    businessId: '9',
    fileName: 'owner.pdf',
    fileSize: 8,
    uploadedAt: '2026-07-22T00:00:00Z',
  })
})

describe('M4 variation page', () => {
  it('renders the server ledger and exact permission actions', async () => {
    const { wrapper } = await mountPage('/variation/order?period=2026-07', [
      'variation:order:query',
    ])

    expect(loadVariationPage).toHaveBeenCalledTimes(1)
    expect(loadVariationPage).toHaveBeenCalledWith(
      expect.objectContaining({
        pageSize: 10,
        startDate: '2026-07-01',
        endDate: '2026-07-31',
      }),
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).toContain('VO-009')
    expect(wrapper.text()).toContain('设计变更一')
    expect(wrapper.text()).toContain('¥1,234,567.89')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('内部已通过')
    expect(wrapper.findAll('thead th')).toHaveLength(8)
    expect(wrapper.findAll('tbody tr').at(0)?.findAll('td')).toHaveLength(8)
    expect(wrapper.text()).not.toContain('DRAFT')
    expect(wrapper.text()).not.toContain('INTERNAL_APPROVED')
    expect(wrapper.text()).toContain('第 1 页')
    expect(wrapper.text()).not.toContain('变更、申报、核定与追溯台账')
    expect(wrapper.text()).not.toContain('新建变更')
    expect(wrapper.text()).not.toContain('编辑')
  })

  it('aborts a stale list request and keeps only the newest response', async () => {
    const first = deferred<VariationPage>()
    const second = deferred<VariationPage>()
    const signals: AbortSignal[] = []
    vi.mocked(loadVariationPage)
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return first.promise
      })
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return second.promise
      })

    const { wrapper, router } = await mountPage('/variation/order', ['variation:order:query'])
    await router.push('/variation/order?varCode=NEW')
    await flushPromises()
    second.resolve({
      ...page,
      records: [{ ...baseRecord, varCode: 'VO-NEW', varName: '最新变更' }],
    })
    await flushPromises()
    first.resolve({ ...page, records: [{ ...baseRecord, varCode: 'VO-OLD', varName: '旧变更' }] })
    await flushPromises()

    expect(signals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('最新变更')
    expect(wrapper.text()).not.toContain('旧变更')
  })

  it('saves decimal strings with the authoritative version and reloads detail', async () => {
    vi.mocked(loadVariation)
      .mockResolvedValueOnce(baseRecord)
      .mockResolvedValueOnce({
        ...baseRecord,
        version: '4',
        items: [{ ...baseRecord.items![0]!, quantity: '10.500' }],
      })
    const { wrapper } = await mountPage('/variation/order?mode=detail&id=9', [
      'variation:order:query',
      'variation:order:item:edit',
    ])

    await button(wrapper, '保存明细').trigger('click')
    await flushPromises()

    expect(saveVariationItems).toHaveBeenCalledWith(
      '9',
      [expect.objectContaining({ quantity: '10.500', claimUnitPrice: '5200.00' })],
      '3',
    )
    expect(loadVariation).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('变更明细已保存并刷新')
    expect(wrapper.text()).toContain('4')
  })

  it('keeps item editing behind its dedicated permission', async () => {
    const { wrapper } = await mountPage('/variation/order?mode=detail&id=9', [
      'variation:order:query',
      'variation:order:edit',
    ])

    expect(button(wrapper, '保存明细')).toBeUndefined()
    expect(button(wrapper, '添加明细')).toBeUndefined()
    expect(wrapper.get('input[aria-label="数量"]').attributes('disabled')).toBeDefined()
  })

  it('aborts a stale detail request and keeps only the selected variation', async () => {
    const first = deferred<VariationRecord>()
    const second = deferred<VariationRecord>()
    const signals: AbortSignal[] = []
    vi.mocked(loadVariation)
      .mockImplementationOnce(async (_id, signal) => {
        signals.push(signal!)
        return first.promise
      })
      .mockImplementationOnce(async (_id, signal) => {
        signals.push(signal!)
        return second.promise
      })

    const { wrapper, router } = await mountPage('/variation/order?mode=detail&id=9', [
      'variation:order:query',
    ])
    await router.push('/variation/order?mode=detail&id=10')
    await flushPromises()
    second.resolve({ ...baseRecord, id: '10', varCode: 'VO-010', varName: '最新详情' })
    await flushPromises()
    first.resolve({ ...baseRecord, id: '9', varCode: 'VO-009', varName: '旧详情' })
    await flushPromises()

    expect(signals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('最新详情')
    expect(wrapper.text()).not.toContain('旧详情')
  })

  it('uploads owner evidence before one guarded submission and rereads authority', async () => {
    const pending = deferred<VariationOwnerSubmissionRecord>()
    vi.mocked(submitVariationToOwner).mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountPage('/variation/order?mode=detail&id=9', [
      'variation:order:query',
      'variation:owner:submit',
    ])
    await wrapper.get('input[aria-label="对外发文号"]').setValue('OUT-001')
    const file = await chooseFile(wrapper)

    await button(wrapper, '提交业主申报').trigger('click')
    await button(wrapper, '提交业主申报').trigger('click')
    await flushPromises()

    expect(uploadSiteFile).toHaveBeenCalledWith(file, 'VARIATION', '9', 'OWNER_SUBMISSION')
    expect(submitVariationToOwner).toHaveBeenCalledTimes(1)
    expect(submitVariationToOwner).toHaveBeenCalledWith(
      '9',
      expect.objectContaining({ externalDocumentNo: 'OUT-001' }),
      '3',
    )
    pending.resolve({ id: 'S1' })
    await flushPromises()
    expect(loadVariation).toHaveBeenCalledTimes(2)
  })

  it('uploads selected site evidence before internal approval submission', async () => {
    const { wrapper } = await mountPage('/variation/order?mode=detail&id=9', [
      'variation:order:query',
      'variation:order:edit',
      'variation:order:submit',
    ])
    const input = wrapper.get('#variation-site-evidence')
    const file = new File(['site'], 'site.pdf', { type: 'application/pdf' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')

    await button(wrapper, '提交审批').trigger('click')
    await flushPromises()

    expect(uploadSiteFile).toHaveBeenCalledWith(file, 'VARIATION', '9', 'SITE_EVIDENCE')
    expect(submitVariation).toHaveBeenCalledWith('9', '3')
    expect(vi.mocked(uploadSiteFile).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(submitVariation).mock.invocationCallOrder[0]!,
    )
  })

  it('passes owner confirmed amounts as strings and does not mutate contract authority', async () => {
    vi.mocked(loadVariation).mockResolvedValue({
      ...baseRecord,
      ownerStatus: 'OWNER_SUBMITTED',
      ownerSubmissions: [
        {
          id: 'S1',
          status: 'SUBMITTED',
          items: [{ id: 'SI1', item_name: '钢筋调整', claimed_amount: '54600.00' }],
        },
      ],
    })
    const { wrapper } = await mountPage('/variation/order?mode=detail&id=9', [
      'variation:order:query',
      'variation:owner:review',
    ])
    await wrapper.get('input[aria-label="业主回复文号"]').setValue('REPLY-001')
    await wrapper.get('input[aria-label="核定金额"]').setValue('50000.25')
    await chooseFile(wrapper)
    await button(wrapper, '登记业主回复').trigger('click')
    await flushPromises()

    expect(reviewVariationOwner).toHaveBeenCalledWith(
      '9',
      'S1',
      expect.objectContaining({
        conclusion: 'CONFIRMED',
        items: [{ submissionItemId: 'SI1', confirmedAmount: '50000.25', reductionReason: null }],
      }),
      '3',
    )
    expect(wrapper.text()).toContain('合同金额以系统结果为准')
  })

  it('fails closed on submit conflict and keeps authoritative state', async () => {
    vi.mocked(submitVariation).mockRejectedValueOnce(
      apiError('版本冲突', 409, 'VAR_ORDER_CONFLICT'),
    )
    const { wrapper } = await mountPage('/variation/order?mode=detail&id=9', [
      'variation:order:query',
      'variation:order:submit',
    ])

    await button(wrapper, '提交审批').trigger('click')
    await flushPromises()

    expect(submitVariation).toHaveBeenCalledWith('9', '3')
    expect(loadVariation).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('版本冲突')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).not.toContain('DRAFT')
    expect(wrapper.text()).not.toContain('变更签证已提交审批')
  })
})
