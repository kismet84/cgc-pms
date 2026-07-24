import type {
  ContractCompositeRecord,
  ContractKpi,
  ContractPage,
} from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ContractPageView from '@/pages/commercial/ContractPage.vue'
import {
  createContractComposite,
  deleteContract,
  loadContractComposite,
  loadContractKpi,
  loadContractPage,
  loadPartners,
  loadProjectContextOptions,
  submitContract,
  updateContractComposite,
} from '@/services/commercial'
import { useSessionStore } from '@/stores/session'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve
    reject = nextReject
  })
  return { promise, resolve, reject }
}

function apiError(message: string, status: number, code = 'TEST_ERROR') {
  return Object.assign(new Error(message), {
    name: 'ApiClientError',
    code,
    status,
  })
}

vi.mock('@/services/commercial', () => ({
  createContractComposite: vi.fn(),
  deleteContract: vi.fn(),
  loadContractComposite: vi.fn(),
  loadContractKpi: vi.fn(),
  loadContractPage: vi.fn(),
  loadPartners: vi.fn(),
  loadProjectContextOptions: vi.fn(),
  submitContract: vi.fn(),
  updateContractComposite: vi.fn(),
}))

const contractPage: ContractPage = {
  records: [
    {
      id: '9',
      tenantId: '1',
      orgId: '1',
      projectId: 'P1',
      contractCode: 'HT-009',
      contractName: '演示合同',
      contractType: 'MAIN',
      partyAId: 'A1',
      partyAName: '甲方一',
      partyBId: 'B1',
      partyBName: '乙方一',
      contractAmount: '1200000.00',
      currentAmount: '1200000.00',
      taxRate: '9',
      taxAmount: '99082.57',
      amountWithoutTax: '1100917.43',
      signedDate: '2026-07-01',
      startDate: '2026-07-01',
      endDate: '2027-07-01',
      paymentMethod: '转账',
      settlementMethod: '月结',
      paidAmount: '100000.00',
      settlementAmount: '0.00',
      contractStatus: 'PERFORMING',
      approvalStatus: 'DRAFT',
      projectName: '项目一',
      createdBy: 'tester',
      createdAt: '2026-07-20 10:00:00',
      updatedAt: '2026-07-20 10:00:00',
      version: '1',
      remark: '备注',
    },
  ],
  total: 1,
  pageNo: 1,
  pageSize: 10,
}

const contractKpi: ContractKpi = {
  totalCount: '1',
  totalAmount: '1200000.00',
  paidAmount: '100000.00',
  unpaidAmount: '1100000.00',
  overdueCount: '0',
}

const contractDetail: ContractCompositeRecord = {
  contract: contractPage.records[0]!,
  items: [
    {
      id: 'I1',
      contractId: '9',
      itemCode: 'ITEM-1',
      itemName: '土建',
      unit: '项',
      quantity: '1',
      unitPrice: '1200000.00',
      amount: '1200000.00',
      taxRate: '9',
      taxAmount: '99082.57',
      amountWithoutTax: '1100917.43',
      sortOrder: '1',
    },
  ],
  paymentTerms: [
    {
      id: 'T1',
      contractId: '9',
      termName: '首付款',
      paymentRatio: '30',
      paymentAmount: '360000.00',
      plannedDate: '2026-08-01',
      termStatus: 'PLANNED',
      sortOrder: '1',
    },
  ],
  approvalRecords: [
    {
      id: 'AR1',
      nodeName: '发起',
      operatorName: 'tester',
      actionType: 'SUBMIT',
      actionName: '提交',
      comment: '已发起',
      createdAt: '2026-07-20 10:00:00',
    },
  ],
}

async function mountPage(path: string, permissions: string[]) {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = {
    userId: '1',
    username: 'tester',
    roles: ['USER'],
    permissions,
  }
  session.status = 'authenticated'

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/contract/ledger', component: ContractPageView },
      { path: '/contract/create', component: ContractPageView },
      { path: '/contract/:id/edit', component: ContractPageView },
      { path: '/contract/:id', component: ContractPageView },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ContractPageView, {
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.mocked(loadProjectContextOptions)
    .mockReset()
    .mockResolvedValue([{ id: 'P1', projectName: '项目一', status: 'ACTIVE' }])
  vi.mocked(loadPartners)
    .mockReset()
    .mockResolvedValue({
      records: [{ id: 'A1', partnerCode: 'A1', partnerName: '甲方一', status: 'ENABLE' }],
    })
  vi.mocked(loadContractPage).mockReset().mockResolvedValue(contractPage)
  vi.mocked(loadContractKpi).mockReset().mockResolvedValue(contractKpi)
  vi.mocked(loadContractComposite).mockReset().mockResolvedValue(contractDetail)
  vi.mocked(createContractComposite).mockReset()
  vi.mocked(updateContractComposite).mockReset()
  vi.mocked(submitContract).mockReset()
  vi.mocked(deleteContract).mockReset()
})

describe('M4 contracts page', () => {
  it('renders ledger with server page and KPI', async () => {
    const { wrapper } = await mountPage('/contract/ledger', ['contract:query', 'contract:add'])

    expect(loadContractPage).toHaveBeenCalledTimes(1)
    expect(loadContractPage).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(loadContractKpi).toHaveBeenCalledTimes(1)
    expect(loadPartners).not.toHaveBeenCalled()
    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toContain('合同台账')
    expect(wrapper.text()).toContain('演示合同')
    expect(wrapper.text()).toContain('¥1,200,000.00')
    expect(wrapper.text()).toContain('主合同')
    expect(wrapper.text()).toContain('履约中')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).not.toContain('MAIN')
    expect(wrapper.text()).not.toContain('PERFORMING')
    expect(wrapper.text()).toContain('第 1 页')
    expect(wrapper.text()).not.toContain('分页')
    expect(wrapper.get('table').text()).toContain('合同编号合同名称')
    expect(wrapper.get('nav[aria-label="合同预设视图"]').text()).toContain('全部合同')
    expect(wrapper.find('input[aria-label="关键词"]').exists()).toBe(false)
    expect(wrapper.findAll('button').some((button) => button.text().includes('查询'))).toBe(false)
    expect(wrapper.get('.contract-page__list-card > .v2-card__header').text()).toContain('新建合同')
    expect(wrapper.find('.contract-page__list-card .v2-card__subtitle').exists()).toBe(false)
    expect(wrapper.get('.contract-page__kpi-grid').findAll(':scope > div')).toHaveLength(5)
  })

  it('applies a preset view through visible server filters and clears previous search', async () => {
    const { wrapper, router } = await mountPage(
      '/contract/ledger?keyword=旧条件&projectId=P1&period=2026-07',
      ['contract:query'],
    )

    const preset = wrapper
      .get('nav[aria-label="合同预设视图"]')
      .findAll('button')
      .find((button) => button.text().includes('履约中'))
    expect(preset).toBeDefined()
    await preset!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({
      contractStatus: 'PERFORMING',
      period: '2026-07',
      projectId: 'P1',
    })
    expect(loadContractPage).toHaveBeenLastCalledWith(
      expect.objectContaining({
        keyword: '',
        projectId: 'P1',
        contractStatus: 'PERFORMING',
        approvalStatus: undefined,
        startDate: '2026-07-01',
        endDate: '2026-07-31',
      }),
      expect.any(AbortSignal),
    )
    expect(loadContractKpi).toHaveBeenLastCalledWith(
      expect.objectContaining({ startDate: '2026-07-01', endDate: '2026-07-31' }),
      expect.any(AbortSignal),
    )
    expect(wrapper.get('button[aria-pressed="true"]').text()).toContain('履约中')
  })

  it('hides edit action for non-draft contracts', async () => {
    vi.mocked(loadContractPage).mockResolvedValueOnce({
      ...contractPage,
      records: [{ ...contractPage.records[0]!, approvalStatus: 'APPROVED' }],
    })

    const { wrapper } = await mountPage('/contract/ledger', ['contract:query', 'contract:edit'])

    expect(wrapper.text()).toContain('已通过')
    expect(wrapper.text()).not.toContain('APPROVED')
    expect(wrapper.findAll('button').some((button) => button.text().includes('编辑'))).toBe(false)
  })

  it('hides create entry without contract:add permission', async () => {
    const { wrapper } = await mountPage('/contract/ledger', ['contract:query'])

    expect(wrapper.findAll('button').some((button) => button.text().includes('新建合同'))).toBe(
      false,
    )
  })

  it('aborts stale ledger request and keeps newest response only', async () => {
    const firstPage = deferred<ContractPage>()
    const secondPage = deferred<ContractPage>()
    const firstKpi = deferred<ContractKpi>()
    const secondKpi = deferred<ContractKpi>()
    const pageSignals: AbortSignal[] = []
    const kpiSignals: AbortSignal[] = []

    vi.mocked(loadContractPage)
      .mockImplementationOnce(async (_query, signal) => {
        pageSignals.push(signal!)
        return firstPage.promise
      })
      .mockImplementationOnce(async (_query, signal) => {
        pageSignals.push(signal!)
        return secondPage.promise
      })
    vi.mocked(loadContractKpi)
      .mockImplementationOnce(async (_query, signal) => {
        kpiSignals.push(signal!)
        return firstKpi.promise
      })
      .mockImplementationOnce(async (_query, signal) => {
        kpiSignals.push(signal!)
        return secondKpi.promise
      })

    const { wrapper, router } = await mountPage('/contract/ledger', ['contract:query'])
    await router.push('/contract/ledger?keyword=new')
    await flushPromises()

    secondPage.resolve({
      ...contractPage,
      records: [{ ...contractPage.records[0]!, contractName: '最新合同', contractCode: 'HT-NEW' }],
    })
    secondKpi.resolve({ ...contractKpi, totalCount: '2' })
    await flushPromises()
    firstPage.resolve({
      ...contractPage,
      records: [{ ...contractPage.records[0]!, contractName: '旧合同', contractCode: 'HT-OLD' }],
    })
    firstKpi.resolve({ ...contractKpi, totalCount: '99' })
    await flushPromises()

    expect(pageSignals[0]?.aborted).toBe(true)
    expect(kpiSignals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('最新合同')
    expect(wrapper.text()).not.toContain('旧合同')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).not.toContain('99')
  })

  it('silently ignores an AbortError from the superseded ledger request', async () => {
    vi.mocked(loadContractPage)
      .mockImplementationOnce(
        (_query, signal) =>
          new Promise<ContractPage>((_resolve, reject) => {
            signal?.addEventListener('abort', () =>
              reject(Object.assign(new Error('aborted'), { name: 'AbortError' })),
            )
          }),
      )
      .mockResolvedValueOnce(contractPage)
    vi.mocked(loadContractKpi)
      .mockImplementationOnce(
        (_query, signal) =>
          new Promise<ContractKpi>((_resolve, reject) => {
            signal?.addEventListener('abort', () =>
              reject(Object.assign(new Error('aborted'), { name: 'AbortError' })),
            )
          }),
      )
      .mockResolvedValueOnce(contractKpi)

    const { wrapper, router } = await mountPage('/contract/ledger', ['contract:query'])
    await router.push('/contract/ledger?keyword=new')
    await flushPromises()

    expect(wrapper.text()).toContain('演示合同')
    expect(wrapper.text()).not.toContain('aborted')
    expect(wrapper.text()).not.toContain('合同台账加载失败')
  })

  it('aborts stale detail request and keeps newest detail only', async () => {
    const first = deferred<ContractCompositeRecord>()
    const second = deferred<ContractCompositeRecord>()
    const detailSignals: AbortSignal[] = []

    vi.mocked(loadContractComposite)
      .mockImplementationOnce(async (_id, signal) => {
        detailSignals.push(signal!)
        return first.promise
      })
      .mockImplementationOnce(async (_id, signal) => {
        detailSignals.push(signal!)
        return second.promise
      })

    const { wrapper, router } = await mountPage('/contract/9', ['contract:query'])
    await router.push('/contract/10')
    await flushPromises()

    second.resolve({
      ...contractDetail,
      contract: {
        ...contractDetail.contract,
        id: '10',
        contractCode: 'HT-010',
        contractName: '新详情合同',
      },
    })
    await flushPromises()
    first.resolve({
      ...contractDetail,
      contract: {
        ...contractDetail.contract,
        id: '9',
        contractCode: 'HT-009',
        contractName: '旧详情合同',
      },
    })
    await flushPromises()

    expect(detailSignals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('新详情合同')
    expect(wrapper.text()).not.toContain('旧详情合同')
  })

  it('shows explicit error on detail 404', async () => {
    vi.mocked(loadContractComposite).mockRejectedValueOnce(
      apiError('合同不存在', 404, 'CONTRACT_NOT_FOUND'),
    )

    const { wrapper } = await mountPage('/contract/404', ['contract:query'])

    expect(wrapper.text()).toContain('合同不存在')
    expect(wrapper.text()).toContain('合同不可访问')
  })

  it('shows explicit error on save 422 and does not fake success', async () => {
    vi.mocked(updateContractComposite).mockRejectedValueOnce(
      apiError('付款条款合计不匹配', 422, 'CONTRACT_VALIDATION_FAILED'),
    )
    vi.mocked(loadContractComposite).mockResolvedValue(contractDetail)

    const { wrapper } = await mountPage('/contract/9/edit', ['contract:query', 'contract:edit'])
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('保存变更'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('付款条款合计不匹配')
    expect(wrapper.text()).not.toContain('操作完成')
  })

  it('shows explicit error on save 500 and does not fake success', async () => {
    vi.mocked(updateContractComposite).mockRejectedValueOnce(
      apiError('合同服务暂不可用', 500, 'INTERNAL_ERROR'),
    )

    const { wrapper } = await mountPage('/contract/9/edit', ['contract:query', 'contract:edit'])
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('保存变更'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('合同服务暂不可用')
    expect(wrapper.text()).not.toContain('操作完成')
  })

  it('re-reads authoritative detail after edit conflict', async () => {
    vi.mocked(loadContractComposite)
      .mockResolvedValueOnce(contractDetail)
      .mockResolvedValueOnce({
        ...contractDetail,
        contract: { ...contractDetail.contract, contractName: '权威合同-2', version: '2' },
      })
    vi.mocked(updateContractComposite).mockRejectedValueOnce(
      Object.assign(new Error('版本冲突'), {
        name: 'ApiClientError',
        code: 'CONTRACT_CONFLICT',
        status: 409,
      }),
    )

    const { wrapper } = await mountPage('/contract/9/edit', ['contract:query', 'contract:edit'])
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('保存变更'))!
      .trigger('click')
    await flushPromises()

    expect(updateContractComposite).toHaveBeenCalledTimes(1)
    expect(loadContractComposite).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('已刷新最新数据')
    expect((wrapper.get('input[aria-label="合同名称"]').element as HTMLInputElement).value).toBe(
      '权威合同-2',
    )
  })

  it('does not fake success when submit fails', async () => {
    vi.mocked(submitContract).mockRejectedValueOnce(apiError('提交失败', 409, 'CONTRACT_CONFLICT'))

    const { wrapper } = await mountPage('/contract/9', ['contract:query', 'contract:submit'])
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交审批'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('提交失败')
    expect(wrapper.text()).not.toContain('合同已提交审批。')
    expect(wrapper.text()).not.toContain('操作完成')
  })

  it('fails closed on submit 403 and keeps authoritative draft state', async () => {
    vi.mocked(submitContract).mockRejectedValueOnce(
      apiError('无合同提交权限', 403, 'WORKFLOW_PERMISSION_DENIED'),
    )

    const { wrapper } = await mountPage('/contract/9', ['contract:query', 'contract:submit'])
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交审批'))!
      .trigger('click')
    await flushPromises()

    expect(loadContractComposite).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('无合同提交权限')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).not.toContain('DRAFT')
    expect(wrapper.text()).not.toContain('合同已提交审批。')
    expect(wrapper.text()).not.toContain('操作完成')
  })

  it('does not fake success when delete fails', async () => {
    vi.mocked(deleteContract).mockRejectedValueOnce(apiError('删除失败', 409, 'CONTRACT_CONFLICT'))

    const { wrapper } = await mountPage('/contract/9', ['contract:query', 'contract:delete'])
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('删除'))!
      .trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认删除'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('删除失败')
    expect(wrapper.text()).not.toContain('操作完成')
  })

  it('hides edit, submit and delete on non-draft detail', async () => {
    vi.mocked(loadContractComposite).mockResolvedValueOnce({
      ...contractDetail,
      contract: { ...contractDetail.contract, approvalStatus: 'APPROVED' },
    })

    const { wrapper } = await mountPage('/contract/9', [
      'contract:query',
      'contract:edit',
      'contract:submit',
      'contract:delete',
    ])

    const labels = wrapper.findAll('button').map((button) => button.text())
    expect(labels.some((text) => text.includes('编辑'))).toBe(false)
    expect(labels.some((text) => text.includes('提交审批'))).toBe(false)
    expect(labels.some((text) => text.includes('删除'))).toBe(false)
  })
})
