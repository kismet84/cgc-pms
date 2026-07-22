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
import {
  activateCostTarget,
  createCostTarget,
  deleteCostTarget,
  loadCostTarget,
  loadCostTargetItems,
  loadCostTargetPage,
  loadProjectContextOptions,
  saveCostTargetItems,
  submitCostTarget,
  updateCostTarget,
} from '@/services/commercial'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/commercial', () => ({
  activateCostTarget: vi.fn(),
  createCostTarget: vi.fn(),
  deleteCostTarget: vi.fn(),
  loadCostTarget: vi.fn(),
  loadCostTargetItems: vi.fn(),
  loadCostTargetPage: vi.fn(),
  loadProjectContextOptions: vi.fn(),
  saveCostTargetItems: vi.fn(),
  submitCostTarget: vi.fn(),
  updateCostTarget: vi.fn(),
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

async function mountPage(permissions: string[], path = '/cost-target/index') {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = { userId: '1', username: 'tester', roles: ['USER'], permissions }
  session.status = 'authenticated'
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/cost-target/index', component: CostTargetPageView },
      { path: '/cost-target/create', component: CostTargetPageView },
      { path: '/cost-target/:id/edit', component: CostTargetPageView },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(CostTargetPageView, {
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return { wrapper, router }
}

function button(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper'], label: string) {
  return wrapper.findAll('button').find((candidate) => candidate.text().includes(label))
}

beforeEach(() => {
  vi.mocked(loadCostTargetPage).mockReset().mockResolvedValue(page)
  vi.mocked(loadCostTarget).mockReset().mockResolvedValue(target)
  vi.mocked(loadCostTargetItems).mockReset().mockResolvedValue([item])
  vi.mocked(loadProjectContextOptions)
    .mockReset()
    .mockResolvedValue([{ id: 'P1', projectName: '项目一', status: 'ACTIVE' }])
  vi.mocked(createCostTarget).mockReset().mockResolvedValue('81')
  vi.mocked(updateCostTarget).mockReset()
  vi.mocked(saveCostTargetItems).mockReset()
  vi.mocked(submitCostTarget).mockReset()
  vi.mocked(activateCostTarget).mockReset()
  vi.mocked(deleteCostTarget).mockReset()
})

describe('M4 cost target page', () => {
  it('fails closed without query permission and loads no business data', async () => {
    const { wrapper } = await mountPage([])

    expect(loadCostTargetPage).not.toHaveBeenCalled()
    expect(loadProjectContextOptions).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('无权访问目标成本')
    expect(wrapper.text()).not.toContain('cost:target:query')
  })

  it('renders server decimals unchanged and hides unauthorized writes', async () => {
    const { wrapper } = await mountPage(['cost:target:query'], '/cost-target/index?projectId=P1')

    expect(loadCostTargetPage).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: 'P1', pageNo: 1, pageSize: 20 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).toContain('9007199254740993.12')
    expect(button(wrapper, '新建版本')).toBeUndefined()
    expect(button(wrapper, '编辑')).toBeUndefined()
    expect(button(wrapper, '提交')).toBeUndefined()
    expect(button(wrapper, '删除')).toBeUndefined()
  })

  it('loads header and items for detail with abort signals', async () => {
    const { wrapper } = await mountPage(['cost:target:query'])

    await button(wrapper, '详情')!.trigger('click')
    await flushPromises()

    expect(loadCostTarget).toHaveBeenCalledWith('81', expect.any(AbortSignal))
    expect(loadCostTargetItems).toHaveBeenCalledWith('81', expect.any(AbortSignal))
    expect(wrapper.get('[role="dialog"]').text()).toContain('首版目标成本')
    expect(wrapper.get('[role="dialog"]').text()).toContain('9007199254740993.12')
  })

  it('shows a list 500 without inventing records', async () => {
    vi.mocked(loadCostTargetPage).mockRejectedValueOnce(apiError('目标成本服务暂不可用', 500))
    const { wrapper } = await mountPage(['cost:target:query'])

    expect(wrapper.text()).toContain('目标成本服务暂不可用')
    expect(wrapper.text()).toContain('暂无目标成本')
    expect(wrapper.text()).not.toContain('首版目标成本')
  })

  it('shows a detail 404 and keeps the missing record closed', async () => {
    vi.mocked(loadCostTarget).mockRejectedValueOnce(apiError('目标成本不存在', 404))
    const { wrapper } = await mountPage(['cost:target:query'])

    await button(wrapper, '详情')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('目标成本不存在')
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
    vi.mocked(createCostTarget).mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountPage(['cost:target:add'], '/cost-target/create?projectId=P1')
    await wrapper.get('button[data-value="P1"]').trigger('click')
    await wrapper.get('input[aria-label="版本号"]').setValue(' V2 ')
    await wrapper.get('input[aria-label="版本名称"]').setValue(' 控制版 ')
    await wrapper.get('input[aria-label="目标成本总额"]').setValue('9007199254740993.12')
    await wrapper.get('input[aria-label="投标成本总额"]').setValue('8800.10')
    await wrapper.get('input[aria-label="责任成本总额"]').setValue('9007199254740993.12')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(createCostTarget).toHaveBeenCalledTimes(1)
    expect(createCostTarget).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 'P1',
        versionNo: 'V2',
        versionName: '控制版',
        totalTargetAmount: '9007199254740993.12',
        totalBidCostAmount: '8800.10',
      }),
    )
    pending.resolve('81')
    await flushPromises()
  })

  it('saves items with CAS version and re-reads authoritative detail', async () => {
    const { wrapper } = await mountPage(['cost:target:edit'], '/cost-target/81/edit?projectId=P1')
    await button(wrapper, '保存明细')!.trigger('click')
    await flushPromises()

    expect(saveCostTargetItems).toHaveBeenCalledWith(
      '81',
      [expect.objectContaining({ targetAmount: '9007199254740993.12' })],
      '7',
    )
    expect(loadCostTarget).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('目标成本明细已保存')
  })

  it('shows item validation 422 and refreshes the latest detail', async () => {
    vi.mocked(saveCostTargetItems).mockRejectedValueOnce(apiError('明细合计与目标总额不一致', 422))
    const { wrapper } = await mountPage(['cost:target:edit'], '/cost-target/81/edit')

    await button(wrapper, '保存明细')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('明细合计与目标总额不一致')
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
    expect(wrapper.text()).toContain('目标成本已提交审批')
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

    expect(wrapper.text()).toContain('目标成本版本已变更')
    expect(loadCostTarget).toHaveBeenCalledTimes(2)
    expect(loadCostTargetItems).toHaveBeenCalledTimes(2)
  })

  it('shows activate conflict 409 and refreshes the approved inactive record', async () => {
    const approved = { ...target, approvalStatus: 'APPROVED' as const }
    vi.mocked(loadCostTarget).mockResolvedValue(approved)
    vi.mocked(activateCostTarget).mockRejectedValueOnce(apiError('其他版本已被激活', 409))
    const { wrapper } = await mountPage(
      ['cost:target:edit', 'cost:target:activate'],
      '/cost-target/81/edit',
    )

    await button(wrapper, '激活版本')!.trigger('click')
    await button(wrapper, '确认激活')!.trigger('click')
    await flushPromises()

    expect(activateCostTarget).toHaveBeenCalledWith('81', '7')
    expect(wrapper.text()).toContain('其他版本已被激活')
    expect(loadCostTarget).toHaveBeenCalledTimes(2)
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
    expect(wrapper.text()).toContain('目标成本已被引用')
    expect(wrapper.text()).toContain('首版目标成本')
  })

  it('keeps server fact after a 409 edit conflict', async () => {
    vi.mocked(updateCostTarget).mockRejectedValueOnce(
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

    expect(wrapper.text()).toContain('目标成本已被修改')
    expect(wrapper.get('input[aria-label="版本名称"]').element).toHaveProperty(
      'value',
      '服务端权威版',
    )
  })
})
