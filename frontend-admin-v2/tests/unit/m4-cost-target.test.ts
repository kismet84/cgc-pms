import type {
  CostTargetItemRecord,
  CostTargetPage,
  CostTargetRecord,
} from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CostTargetPageView from '@/pages/commercial/CostTargetPage.vue'
import { dismissToast, toastItems } from '@/components/toast'
import {
  deleteCostTarget,
  loadCostSubjectOptions,
  loadCostTarget,
  loadCostTargetDefaultAllocation,
  loadCostTargetItems,
  loadCostTargetProjectManagerOptions,
  loadCostTargetPage,
  loadProjectContextOptions,
  saveCostBudgetDraft,
  submitCostTarget,
} from '@/services/commercial'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/commercial', () => ({
  deleteCostTarget: vi.fn(),
  loadCostSubjectOptions: vi.fn(),
  loadCostTarget: vi.fn(),
  loadCostTargetDefaultAllocation: vi.fn(),
  loadCostTargetItems: vi.fn(),
  loadCostTargetProjectManagerOptions: vi.fn(),
  loadCostTargetPage: vi.fn(),
  loadProjectContextOptions: vi.fn(),
  saveCostBudgetDraft: vi.fn(),
  submitCostTarget: vi.fn(),
}))

const target: CostTargetRecord = {
  id: '81',
  projectId: 'P1',
  versionNo: 'V1',
  versionName: '首版目标成本',
  totalTargetAmount: '9007199254740993.12',
  totalBidCostAmount: '8800000000000000.10',
  totalResponsibilityAmount: '9007199254740993.12',
  isActive: 0,
  approvalStatus: 'DRAFT',
  status: 'DRAFT',
  version: '7',
  remark: '待分解',
}
const item: CostTargetItemRecord = {
  id: '91',
  targetId: '81',
  projectId: 'P1',
  costSubjectId: 'S1',
  targetAmount: '9007199254740993.12',
  bidCostAmount: '8800000000000000.10',
  responsibilityAmount: '9007199254740993.12',
}
const page: CostTargetPage = { records: [target], total: 1, pageNo: 1, pageSize: 20 }

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve
    reject = nextReject
  })
  return { promise, reject, resolve }
}

function apiError(message: string, status: number) {
  return Object.assign(new Error(message), { name: 'ApiClientError', code: 'TEST_ERROR', status })
}

async function mountPage(permissions: string[], path = '/cost-target/index', embedded?: boolean) {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = { userId: '1', username: 'tester', roles: ['USER'], permissions }
  session.status = 'authenticated'
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/cost-target/index', component: CostTargetPageView },
      { path: '/cost-budget', component: CostTargetPageView },
      { path: '/cost-target/create', component: CostTargetPageView },
      { path: '/cost-target/:id/edit', component: CostTargetPageView },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(CostTargetPageView, {
    props: embedded === undefined ? {} : { embedded },
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return { wrapper, router }
}

function button(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper'], label: string) {
  return wrapper.findAll('button').find((candidate) => candidate.text().includes(label))
}

beforeEach(() => {
  toastItems.slice().forEach((toast) => dismissToast(toast.id))
  vi.mocked(loadCostSubjectOptions)
    .mockReset()
    .mockResolvedValue([{ id: 'S1', subjectCode: '6001', subjectName: '材料费', status: 'ENABLE' }])
  vi.mocked(loadCostTargetPage).mockReset().mockResolvedValue(page)
  vi.mocked(loadCostTarget).mockReset().mockResolvedValue(target)
  vi.mocked(loadCostTargetDefaultAllocation)
    .mockReset()
    .mockResolvedValue({
      projectId: 'P1',
      projectManagerId: 'U1',
      sourceMainContractId: 'C1',
      sourceMainContractCode: 'MAIN-001',
      sourceContractAmount: '10596697064401168.38',
      targetCostRate: '0.850000',
      totalTargetAmount: '9007199254740993.12',
      items: Array.from({ length: 10 }, (_, index) => ({
        id: String(index + 1),
        targetId: '',
        projectId: 'P1',
        costSubjectId: `S${index + 1}`,
        subjectCode: `5401.03.${String(index + 1).padStart(2, '0')}`,
        subjectName: `目标成本${index + 1}`,
        subjectType: 'TARGET',
        defaultTargetRatio: index === 0 ? '100.0000' : '0.0000',
        targetAmount: index === 0 ? '9007199254740993.12' : '0.00',
        bidCostAmount: '0.00',
        responsibilityAmount: index === 0 ? '9007199254740993.12' : '0.00',
        responsibleUserId: 'U1',
      })),
    })
  vi.mocked(loadCostTargetItems).mockReset().mockResolvedValue([item])
  vi.mocked(loadProjectContextOptions)
    .mockReset()
    .mockResolvedValue([
      {
        id: 'P1',
        projectCode: 'P1',
        projectName: '项目一',
        status: 'ACTIVE',
        projectManagerId: 'U1',
      },
    ])
  vi.mocked(loadCostTargetProjectManagerOptions)
    .mockReset()
    .mockResolvedValue([
      {
        id: 'U1',
        username: 'owner',
        realName: '负责人',
        status: 'ENABLE',
        eligible: true,
      },
    ])
  vi.mocked(saveCostBudgetDraft).mockReset().mockResolvedValue('81')
  vi.mocked(submitCostTarget).mockReset()
  vi.mocked(deleteCostTarget).mockReset()
})

describe('M4 cost target page', () => {
  it('fails closed without query permission and loads no business data', async () => {
    const { wrapper } = await mountPage([])

    expect(loadCostTargetPage).not.toHaveBeenCalled()
    expect(loadProjectContextOptions).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('无权访问项目成本预算')
    expect(wrapper.text()).not.toContain('cost:target:query')
  })

  it('renders server decimals as fixed two-place amounts and hides unauthorized writes', async () => {
    const { wrapper } = await mountPage(['cost:target:query'], '/cost-target/index?projectId=P1')

    expect(loadCostTargetPage).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: 'P1', pageNo: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).toContain('¥9,007,199,254,740,993.12')
    expect(wrapper.text()).toContain('项目一')
    expect(wrapper.text()).not.toContain('P1')
    expect(button(wrapper, '新建版本')).toBeUndefined()
    expect(button(wrapper, '编辑')).toBeUndefined()
    expect(button(wrapper, '提交')).toBeUndefined()
    expect(button(wrapper, '删除')).toBeUndefined()
  })

  it('derives the embedded budget heading from the canonical route', async () => {
    const { wrapper } = await mountPage(
      ['cost:target:query', 'cost:target:add'],
      '/cost-budget?projectId=P1',
    )

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toBe('项目成本预算')
    expect(wrapper.find('h2').exists()).toBe(false)
    const heading = wrapper.get('.v2-card--page-heading')
    expect(heading.find('.cost-target-page__filters').exists()).toBe(true)
    expect(heading.text()).toContain('新建版本')
  })

  it('opens new project cost budget in one-card dialog', async () => {
    const { wrapper, router } = await mountPage(['cost:target:query', 'cost:target:add'])

    await button(wrapper, '新建版本')!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/cost-target/create')
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('新建项目成本预算')
    expect(dialog.text()).toContain('版本信息')
    await dialog.get('select[aria-label="项目"]').setValue('P1')
    await flushPromises()
    const managerSelect = dialog.get('select[aria-label="项目经理"]')
    expect(managerSelect.findAll('option').map((option) => option.text())).toContain('负责人')
    expect(managerSelect.text()).not.toContain('普通用户')
    expect(loadCostTargetProjectManagerOptions).toHaveBeenCalledWith('P1', expect.any(AbortSignal))
    expect(dialog.text()).toContain('成本预算明细')
    expect(dialog.text()).not.toContain('备注')
    expect(dialog.findAll('.v2-card')).toHaveLength(1)
    expect(dialog.get('[aria-label="成本预算明细编辑表格"]')).toBeTruthy()
    expect(
      dialog.findAll('.cost-target-page__editor-table thead th').map((header) => header.text()),
    ).toEqual(['成本科目编码/名称*', '目标金额*', '投标金额*', '责任金额*'])
    expect(dialog.find('input[aria-label="责任单位"]').exists()).toBe(false)
    expect(dialog.find('select[aria-label="责任人"]').exists()).toBe(false)
  })

  it('loads header and items for detail with abort signals', async () => {
    const { wrapper } = await mountPage(['cost:target:query'])

    await button(wrapper, 'V1')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('6001 · 材料费')
    expect(wrapper.text()).not.toContain('S1')

    expect(loadCostTarget).toHaveBeenCalledWith('81', expect.any(AbortSignal))
    expect(loadCostTargetItems).toHaveBeenCalledWith('81', expect.any(AbortSignal))
    expect(wrapper.get('[role="dialog"]').text()).toContain('首版目标成本')
    expect(wrapper.get('[role="dialog"]').text()).toContain('¥9,007,199,254,740,993.12')
  })

  it('shows a list 500 without inventing records', async () => {
    vi.mocked(loadCostTargetPage).mockRejectedValueOnce(apiError('目标成本服务暂不可用', 500))
    const { wrapper } = await mountPage(['cost:target:query'])

    expect(toastItems.some((toast) => toast.message.includes('目标成本服务暂不可用'))).toBe(true)
    expect(wrapper.text()).not.toContain('暂无目标成本')
    expect(wrapper.text()).not.toContain('首版目标成本')
  })

  it('shows a detail 404 and keeps the missing record closed', async () => {
    vi.mocked(loadCostTarget).mockRejectedValueOnce(apiError('目标成本不存在', 404))
    const { wrapper } = await mountPage(['cost:target:query'])

    await button(wrapper, 'V1')!.trigger('click')
    await flushPromises()

    expect(toastItems.some((toast) => toast.message.includes('目标成本不存在'))).toBe(true)
    expect(wrapper.get('[role="dialog"]').text()).not.toContain('首版目标成本')
  })

  it('aborts an old list request and ignores its late error', async () => {
    const oldRequest = deferred<CostTargetPage>()
    const newRequest = deferred<CostTargetPage>()
    const signals: AbortSignal[] = []
    vi.mocked(loadCostTargetPage)
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return oldRequest.promise
      })
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return newRequest.promise
      })

    const { wrapper, router } = await mountPage(
      ['cost:target:query'],
      '/cost-target/index?versionNo=old',
    )
    await router.push('/cost-target/index?versionNo=new')
    await flushPromises()
    newRequest.resolve({
      ...page,
      records: [{ ...target, versionName: '最新服务端版本' }],
    })
    await flushPromises()
    oldRequest.reject(apiError('旧请求失败不应显示', 500))
    await flushPromises()

    expect(signals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('最新服务端版本')
    expect(wrapper.text()).not.toContain('旧请求失败不应显示')
  })

  it('creates once on repeated submits and preserves DecimalString payloads', async () => {
    const pending = deferred<string>()
    vi.mocked(saveCostBudgetDraft).mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountPage(['cost:target:add'], '/cost-target/create?projectId=P1')
    await wrapper.get('select[aria-label="项目"]').setValue('P1')
    await wrapper.get('input[aria-label="版本号"]').setValue(' V2 ')
    await wrapper.get('input[aria-label="版本名称"]').setValue(' 控制版 ')
    await wrapper.findAll('input[aria-label="投标金额"]')[0]!.setValue('8800.10')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(saveCostBudgetDraft).toHaveBeenCalledTimes(1)
    expect(saveCostBudgetDraft).toHaveBeenCalledWith(
      null,
      expect.objectContaining({
        projectId: 'P1',
        projectManagerId: 'U1',
        versionNo: 'V2',
        versionName: '控制版',
        items: expect.arrayContaining([
          expect.objectContaining({
            costSubjectId: 'S1',
            targetAmount: '9007199254740993.12',
            bidCostAmount: '8800.10',
            responsibleUserId: 'U1',
            responsibilityUnit: '项目成本责任人',
          }),
        ]),
      }),
    )
    expect(vi.mocked(saveCostBudgetDraft).mock.calls[0]?.[1].items).toHaveLength(10)
    pending.resolve('81')
    await flushPromises()
  })

  it('saves header and items in one transaction and re-reads authoritative totals', async () => {
    const { wrapper } = await mountPage(['cost:target:edit'], '/cost-target/81/edit?projectId=P1')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(saveCostBudgetDraft).toHaveBeenCalledWith(
      '81',
      expect.objectContaining({
        version: '7',
        items: [expect.objectContaining({ targetAmount: '9007199254740993.12' })],
      }),
    )
    expect(loadCostTarget).toHaveBeenCalledTimes(2)
    expect(toastItems.at(-1)?.message).toContain('已刷新服务端合计')
  })

  it('shows item validation 422 and refreshes the latest detail', async () => {
    vi.mocked(saveCostBudgetDraft).mockRejectedValueOnce(
      apiError('责任金额合计必须等于目标金额合计', 422),
    )
    const { wrapper } = await mountPage(['cost:target:edit'], '/cost-target/81/edit')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(
      toastItems.some((toast) => toast.message.includes('责任金额合计必须等于目标金额合计')),
    ).toBe(true)
    expect(loadCostTarget).toHaveBeenCalledTimes(2)
    expect(loadCostTargetItems).toHaveBeenCalledTimes(2)
  })

  it('submits once with server version and refreshes authoritative status', async () => {
    const pending = deferred<void>()
    vi.mocked(submitCostTarget).mockReturnValueOnce(pending.promise)
    vi.mocked(loadCostTarget)
      .mockResolvedValueOnce(target)
      .mockResolvedValueOnce({ ...target, approvalStatus: 'APPROVING', version: '8' })
    const { wrapper } = await mountPage(
      ['cost:target:edit', 'cost:target:submit'],
      '/cost-target/81/edit',
    )
    await button(wrapper, '提交审批')!.trigger('click')
    await button(wrapper, '确认提交')!.trigger('click')
    await button(wrapper, '确认提交')!.trigger('click')

    expect(submitCostTarget).toHaveBeenCalledTimes(1)
    expect(submitCostTarget).toHaveBeenCalledWith('81', '7')
    pending.resolve()
    await flushPromises()
    expect(toastItems.at(-1)?.message).toContain('项目成本预算已提交审批')
  })

  it('shows submit conflict 409 and refreshes the latest status', async () => {
    vi.mocked(submitCostTarget).mockRejectedValueOnce(apiError('目标成本版本已变更', 409))
    const { wrapper } = await mountPage(
      ['cost:target:edit', 'cost:target:submit'],
      '/cost-target/81/edit',
    )

    await button(wrapper, '提交审批')!.trigger('click')
    await button(wrapper, '确认提交')!.trigger('click')
    await flushPromises()

    expect(toastItems.some((toast) => toast.message.includes('目标成本版本已变更'))).toBe(true)
    expect(loadCostTarget).toHaveBeenCalledTimes(2)
    expect(loadCostTargetItems).toHaveBeenCalledTimes(2)
  })

  it('does not expose manual activation after approval', async () => {
    const approved = { ...target, approvalStatus: 'APPROVED' as const }
    vi.mocked(loadCostTarget).mockResolvedValue(approved)
    const { wrapper } = await mountPage(
      ['cost:target:edit', 'cost:target:activate'],
      '/cost-target/81/edit',
    )

    expect(button(wrapper, '激活版本')).toBeUndefined()
    expect(button(wrapper, '确认激活')).toBeUndefined()
  })

  it('shows delete conflict 409, refreshes the list, and preserves the record', async () => {
    vi.mocked(deleteCostTarget).mockRejectedValueOnce(apiError('目标成本已被引用', 409))
    const { wrapper } = await mountPage(
      ['cost:target:query', 'cost:target:delete'],
      '/cost-target/index',
    )

    await button(wrapper, '删除')!.trigger('click')
    await button(wrapper, '确认删除')!.trigger('click')
    await flushPromises()

    expect(deleteCostTarget).toHaveBeenCalledWith('81', '7')
    expect(loadCostTargetPage).toHaveBeenCalledTimes(2)
    expect(toastItems.some((toast) => toast.message.includes('目标成本已被引用'))).toBe(true)
    expect(wrapper.text()).toContain('首版目标成本')
  })

  it('keeps server fact after a 409 edit conflict', async () => {
    vi.mocked(saveCostBudgetDraft).mockRejectedValueOnce(
      Object.assign(new Error('目标成本已被修改'), {
        name: 'ApiClientError',
        code: 'COST_TARGET_CONCURRENT_UPDATE',
        status: 409,
      }),
    )
    vi.mocked(loadCostTarget)
      .mockResolvedValueOnce(target)
      .mockResolvedValueOnce({ ...target, versionName: '服务端权威版', version: '8' })
    const { wrapper } = await mountPage(['cost:target:edit'], '/cost-target/81/edit')
    await wrapper.get('input[aria-label="版本名称"]').setValue('本地修改')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(toastItems.some((toast) => toast.message.includes('目标成本已被修改'))).toBe(true)
    expect(wrapper.get('input[aria-label="版本名称"]').element).toHaveProperty(
      'value',
      '服务端权威版',
    )
  })
})
