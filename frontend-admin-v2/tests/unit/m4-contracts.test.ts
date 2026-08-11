import type { ContractCompositeRecord, ContractPage } from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ContractPageView from '@/pages/commercial/ContractPage.vue'
import {
  createContractComposite,
  deleteContract,
  loadContractComposite,
  loadContractPage,
  loadContractProjectOptions,
  loadPartners,
  submitContract,
  updateContractComposite,
} from '@/services/commercial'
import { useSessionStore } from '@/stores/session'
import { loadMaterials } from '@/services/supply-chain'

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
  loadContractPage: vi.fn(),
  loadContractProjectOptions: vi.fn(),
  loadPartners: vi.fn(),
  submitContract: vi.fn(),
  updateContractComposite: vi.fn(),
}))

vi.mock('@/services/supply-chain', () => ({
  loadMaterials: vi.fn(),
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
  vi.mocked(loadContractProjectOptions)
    .mockReset()
    .mockResolvedValue([
      {
        id: 'P1',
        projectCode: 'P1',
        projectName: '主合同项目',
        status: 'ACTIVE',
        mainEligible: true,
        nonMainEligible: false,
      },
      {
        id: 'P2',
        projectCode: 'P2',
        projectName: '非主合同项目',
        status: 'ACTIVE',
        mainEligible: false,
        nonMainEligible: true,
      },
      {
        id: 'P3',
        projectCode: 'P3',
        projectName: '不符合项目',
        status: 'CLOSED',
        mainEligible: false,
        nonMainEligible: false,
      },
    ])
  vi.mocked(loadPartners)
    .mockReset()
    .mockResolvedValue({
      records: [
        {
          id: 'A1',
          partnerCode: 'A1',
          partnerName: '甲方一',
          partnerType: 'CUSTOMER',
          status: 'ENABLE',
        },
        {
          id: 'B1',
          partnerCode: 'B1',
          partnerName: '乙方一',
          partnerType: 'SUPPLIER',
          status: 'ENABLE',
        },
      ],
    })
  vi.mocked(loadMaterials).mockReset().mockResolvedValue({
    records: [],
    total: 0,
    pageNo: 1,
    pageSize: 200,
  })
  vi.mocked(loadContractPage).mockReset().mockResolvedValue(contractPage)
  vi.mocked(loadContractComposite).mockReset().mockResolvedValue(contractDetail)
  vi.mocked(createContractComposite).mockReset()
  vi.mocked(updateContractComposite).mockReset()
  vi.mocked(submitContract).mockReset()
  vi.mocked(deleteContract).mockReset()
})

describe('M4 contracts page', () => {
  it('filters project candidates by contract type and keeps the current historical project disabled', async () => {
    const create = await mountPage('/contract/create', ['contract:add'])
    const createProject = create.wrapper.get('select[aria-label="项目"]')
    expect(createProject.find('option[value="P1"]').exists()).toBe(true)
    expect(createProject.find('option[value="P2"]').exists()).toBe(false)
    expect(createProject.find('option[value="P3"]').exists()).toBe(false)

    await create.wrapper.get('select[aria-label="合同类型"]').setValue('PURCHASE')
    expect(createProject.find('option[value="P1"]').exists()).toBe(false)
    expect(createProject.find('option[value="P2"]').exists()).toBe(true)
    expect(createProject.find('option[value="P3"]').exists()).toBe(false)
    create.wrapper.unmount()

    vi.mocked(loadContractProjectOptions).mockResolvedValueOnce([
      {
        id: 'P1',
        projectCode: 'P1',
        projectName: '历史项目',
        status: 'CLOSED',
        mainEligible: false,
        nonMainEligible: false,
      },
    ])
    const edit = await mountPage('/contract/9/edit', ['contract:query', 'contract:edit'])
    const historical = edit.wrapper.get('select[aria-label="项目"]').get('option[value="P1"]')
    expect(historical.text()).toContain('历史项目（历史值）')
    expect(historical.attributes('disabled')).toBeDefined()
  })

  it('prevents selecting the same party twice and limits purchase vendors to suppliers', async () => {
    const { wrapper } = await mountPage('/contract/create', ['contract:add'])
    await wrapper.get('select[aria-label="合同类型"]').setValue('PURCHASE')

    const partyA = wrapper.get('select[aria-label="甲方"]')
    await partyA.setValue('A1')
    const partyB = wrapper.get('select[aria-label="乙方"]')
    expect(partyB.text()).toContain('乙方一')
    expect(partyB.text()).not.toContain('甲方一')
  })

  it('searches material dictionary and adds the selected material to a purchase contract', async () => {
    const material = {
      id: 'M1',
      materialCode: 'GC-STEEL-001',
      materialName: 'HRB400E螺纹钢',
      specification: 'Φ20',
      unit: '吨',
      status: 'ENABLE',
    }
    const { wrapper } = await mountPage('/contract/create', ['contract:add'])
    await wrapper.get('select[aria-label="合同类型"]').setValue('PURCHASE')
    vi.mocked(loadMaterials).mockResolvedValueOnce({
      records: [material],
      total: 1,
      pageNo: 1,
      pageSize: 50,
    })

    vi.useFakeTimers()
    try {
      await wrapper.get('input[aria-label="搜索材料名称"]').setValue('螺纹钢')
      await vi.advanceTimersByTimeAsync(250)
      await flushPromises()

      expect(loadMaterials).toHaveBeenLastCalledWith(
        { pageNo: 1, pageSize: 50, status: 'ENABLE', materialName: '螺纹钢' },
        expect.any(AbortSignal),
      )
      expect(wrapper.get('select[aria-label="选择材料"]').text()).toContain(
        'HRB400E螺纹钢 · Φ20 · 吨',
      )
      await wrapper.get('select[aria-label="选择材料"]').setValue('M1')
      await flushPromises()

      const item = wrapper.get('.contract-page__editor-list article')
      expect((item.get('input[aria-label="名称"]').element as HTMLInputElement).value).toBe(
        'HRB400E螺纹钢',
      )
      expect((item.get('input[aria-label="编号"]').element as HTMLInputElement).value).toBe(
        'GC-STEEL-001',
      )
      expect((item.get('input[aria-label="规格"]').element as HTMLInputElement).value).toBe('Φ20')
      expect((item.get('input[aria-label="单位"]').element as HTMLInputElement).value).toBe('吨')
    } finally {
      wrapper.unmount()
      vi.useRealTimers()
    }
  })

  it('derives header tax preview and exposes only quantity and unit price for item finance inputs', async () => {
    const { wrapper } = await mountPage('/contract/create', ['contract:add'])
    const amountInput = wrapper.get('input[aria-label="合同金额"]')
    const taxRateInput = wrapper.get('input[aria-label="税率"]')

    await amountInput.setValue('113')
    await taxRateInput.setValue('13')

    const taxAmountInput = wrapper.get('input[aria-label="税额（自动计算）"]')
    const amountWithoutTaxInput = wrapper.get('input[aria-label="不含税金额（自动计算）"]')
    expect((taxAmountInput.element as HTMLInputElement).disabled).toBe(true)
    expect((taxAmountInput.element as HTMLInputElement).value).toBe('13.00')
    expect((amountWithoutTaxInput.element as HTMLInputElement).disabled).toBe(true)
    expect((amountWithoutTaxInput.element as HTMLInputElement).value).toBe('100.00')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('新增清单'))!
      .trigger('click')
    const itemEditor = wrapper.get('.contract-page__editor-list article')
    expect(itemEditor.find('input[aria-label="数量"]').exists()).toBe(true)
    expect(itemEditor.find('input[aria-label="单价"]').exists()).toBe(true)
    expect(itemEditor.find('input[aria-label="金额"]').exists()).toBe(false)
    expect(itemEditor.find('input[aria-label="税率"]').exists()).toBe(false)
    expect(itemEditor.find('input[aria-label="税额"]').exists()).toBe(false)
    expect(itemEditor.find('input[aria-label="不含税金额"]').exists()).toBe(false)
  })

  it('locks contract amount when editing a contract under an active project', async () => {
    const { wrapper } = await mountPage('/contract/9/edit', ['contract:query', 'contract:edit'])

    expect((wrapper.get('input[aria-label="合同金额"]').element as HTMLInputElement).disabled).toBe(
      true,
    )
    expect(wrapper.text()).toContain('项目已在建，合同总价调整请发起合同变更。')
  })

  it('renders ledger with server page', async () => {
    const { wrapper } = await mountPage('/contract/ledger', ['contract:query', 'contract:add'])

    expect(loadContractPage).toHaveBeenCalledTimes(1)
    expect(loadContractPage).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
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
    expect(wrapper.find('.v2-ledger-kpis').exists()).toBe(false)
  })

  it('shows server-calculated net payable for purchase contracts', async () => {
    vi.mocked(loadContractComposite).mockResolvedValueOnce({
      ...contractDetail,
      contract: {
        ...contractDetail.contract,
        contractType: 'PURCHASE',
        payableAmount: '600.00',
      },
    })

    const { wrapper } = await mountPage('/contract/9', ['contract:query'])

    expect(wrapper.text()).toContain('采购净应付')
    expect(wrapper.text()).toContain('¥600.00')
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
    const pageSignals: AbortSignal[] = []

    vi.mocked(loadContractPage)
      .mockImplementationOnce(async (_query, signal) => {
        pageSignals.push(signal!)
        return firstPage.promise
      })
      .mockImplementationOnce(async (_query, signal) => {
        pageSignals.push(signal!)
        return secondPage.promise
      })
    const { wrapper, router } = await mountPage('/contract/ledger', ['contract:query'])
    await router.push('/contract/ledger?keyword=new')
    await flushPromises()

    secondPage.resolve({
      ...contractPage,
      records: [{ ...contractPage.records[0]!, contractName: '最新合同', contractCode: 'HT-NEW' }],
    })
    await flushPromises()
    firstPage.resolve({
      ...contractPage,
      records: [{ ...contractPage.records[0]!, contractName: '旧合同', contractCode: 'HT-OLD' }],
    })
    await flushPromises()

    expect(pageSignals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('最新合同')
    expect(wrapper.text()).not.toContain('旧合同')
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
    const { wrapper, router } = await mountPage('/contract/ledger', ['contract:query'])
    await router.push('/contract/ledger?keyword=new')
    await flushPromises()

    expect(wrapper.text()).toContain('演示合同')
    expect(wrapper.text()).not.toContain('aborted')
    expect(wrapper.text()).not.toContain('合同台账加载失败')
  })

  it('does not render a false empty state when the ledger request fails', async () => {
    vi.mocked(loadContractPage).mockRejectedValueOnce(
      apiError('服务响应格式无效', 502, 'API_MALFORMED_RESPONSE'),
    )

    const { wrapper } = await mountPage('/contract/ledger', ['contract:query'])

    expect(wrapper.text()).not.toContain('暂无可见合同')
    expect(wrapper.find('section.v2-alert--danger').exists()).toBe(false)
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

  it('does not render an inline alert or fake success on save 422', async () => {
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

    expect(wrapper.find('section.v2-alert--danger').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('操作完成')
  })

  it('does not render an inline alert or fake success on save 500', async () => {
    vi.mocked(updateContractComposite).mockRejectedValueOnce(
      apiError('合同服务暂不可用', 500, 'INTERNAL_ERROR'),
    )

    const { wrapper } = await mountPage('/contract/9/edit', ['contract:query', 'contract:edit'])
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('保存变更'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.find('section.v2-alert--danger').exists()).toBe(false)
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
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认提交'))!
      .trigger('click')
    await flushPromises()

    expect(wrapper.find('section.v2-alert--danger').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('合同已提交审批。')
    expect(wrapper.text()).not.toContain('操作完成')
  })

  it('submits the authoritative current contract id and version', async () => {
    const { wrapper } = await mountPage('/contract/9', ['contract:query', 'contract:submit'])
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交审批'))!
      .trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认提交'))!
      .trigger('click')
    await flushPromises()

    expect(submitContract).toHaveBeenCalledWith('9', '1')
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
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('确认提交'))!
      .trigger('click')
    await flushPromises()

    expect(loadContractComposite).toHaveBeenCalledTimes(2)
    expect(wrapper.find('section.v2-alert--danger').exists()).toBe(false)
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

    expect(wrapper.find('section.v2-alert--danger').exists()).toBe(false)
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

    const labels = wrapper.findAll('.v2-dialog__footer button').map((button) => button.text())
    expect(labels.some((text) => text.includes('编辑'))).toBe(false)
    expect(labels.some((text) => text.includes('提交审批'))).toBe(false)
    expect(labels.some((text) => text.includes('删除'))).toBe(false)
  })
})
