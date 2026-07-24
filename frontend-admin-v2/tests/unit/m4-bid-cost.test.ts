import type { BidCostPage, BidCostRecord } from '@cgc-pms/frontend-contracts'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BidCostPageView from '@/pages/commercial/BidCostPage.vue'
import {
  createBidCost,
  deleteBidCost,
  loadBidCost,
  loadBidCostPage,
  loadProjectContextOptions,
  markBidCostLost,
  markBidCostWon,
  updateBidCost,
} from '@/services/commercial'
import { useSessionStore } from '@/stores/session'

const currentDir = dirname(fileURLToPath(import.meta.url))
const bidCostPagePath = resolve(currentDir, '../../src/pages/commercial/BidCostPage.vue')

vi.mock('@/services/commercial', () => ({
  createBidCost: vi.fn(),
  deleteBidCost: vi.fn(),
  loadBidCost: vi.fn(),
  loadBidCostPage: vi.fn(),
  loadProjectContextOptions: vi.fn(),
  markBidCostLost: vi.fn(),
  markBidCostWon: vi.fn(),
  updateBidCost: vi.fn(),
}))

const bidding: BidCostRecord = {
  id: '11',
  projectId: null,
  bidProjectName: '市民中心投标',
  bidStatus: 'BIDDING',
  createdAt: '2026-07-20 09:00:00',
  updatedAt: '2026-07-21 10:00:00',
  remark: '资格预审完成',
}

const page: BidCostPage = {
  records: [bidding],
  total: 1,
  pageNo: 1,
  pageSize: 10,
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve
  })
  return { promise, resolve }
}

function apiError(message: string, status: number) {
  return Object.assign(new Error(message), { name: 'ApiClientError', code: 'TEST_ERROR', status })
}

async function mountPage(permissions: string[], path = '/bid-cost') {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = { userId: '1', username: 'tester', roles: ['USER'], permissions }
  session.status = 'authenticated'
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/bid-cost', component: BidCostPageView }],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(BidCostPageView, {
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return { wrapper, router }
}

function button(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper'], label: string) {
  return wrapper.findAll('button').find((item) => item.text().includes(label))
}

beforeEach(() => {
  vi.mocked(loadBidCostPage).mockReset().mockResolvedValue(page)
  vi.mocked(loadBidCost).mockReset().mockResolvedValue(bidding)
  vi.mocked(loadProjectContextOptions)
    .mockReset()
    .mockResolvedValue([{ id: 'P1', projectName: '项目一', status: 'ACTIVE' }])
  vi.mocked(createBidCost).mockReset().mockResolvedValue('11')
  vi.mocked(updateBidCost).mockReset()
  vi.mocked(deleteBidCost).mockReset()
  vi.mocked(markBidCostWon).mockReset()
  vi.mocked(markBidCostLost).mockReset()
})

describe('M4 bid cost page', () => {
  it('keeps the audited page inside the V2 design-system gate', () => {
    const source = readFileSync(bidCostPagePath, 'utf-8')
    const style = source.match(/<style scoped>([\s\S]*?)<\/style>/)?.[1] ?? ''

    expect(source).toContain('<V2Button variant="secondary" @click="openDetail(record.id)">预览')
    expect(source).toContain('class="v2-detail-dialog__quick-actions"')
    expect(source).toContain('id="bid-cost-form"')
    expect(source.indexOf('text="取消"')).toBeLessThan(source.indexOf('form="bid-cost-form"'))
    expect(source).not.toMatch(/<V2Button\b[^>]*\bsize="small"/)
    expect(style).not.toMatch(/\.bid-cost-page__table-wrap\s*\{/)
    expect(style).not.toMatch(/\.bid-cost-page__table\s+(?:th|td)/)
    expect(style).not.toMatch(/textarea\s*\{[^}]*\b(?:background|border|color|padding)\s*:/)
  })

  it('fails closed without bid:query and does not load business data', async () => {
    const { wrapper } = await mountPage([])

    expect(loadBidCostPage).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('无权访问投标成本')
    expect(wrapper.text()).toContain('当前账号没有访问权限')
    expect(wrapper.text()).not.toContain('bid:query')
  })

  it('renders server-side list and hides unauthorized actions', async () => {
    const { wrapper } = await mountPage(['bid:query'])

    expect(loadBidCostPage).toHaveBeenCalledWith(
      expect.objectContaining({ pageNo: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).toContain('市民中心投标')
    expect(wrapper.text()).toContain('投标中')
    expect(wrapper.text()).toContain('第 1 页')
    expect(loadProjectContextOptions).not.toHaveBeenCalled()
    expect(button(wrapper, '预览')?.classes()).not.toContain('v2-glass-button')
    expect(wrapper.text()).not.toContain('投标前期成本记录与中标、未中标状态闭环')
    expect(button(wrapper, '新建投标成本')).toBeUndefined()
    expect(button(wrapper, '编辑')).toBeUndefined()
    expect(button(wrapper, '标记中标')).toBeUndefined()
    expect(button(wrapper, '删除')).toBeUndefined()
  })

  it('applies public project and report period context', async () => {
    await mountPage(['bid:query'], '/bid-cost?projectId=P1&period=2026-07')

    expect(loadBidCostPage).toHaveBeenCalledWith(
      expect.objectContaining({
        projectId: 'P1',
        startDate: '2026-07-01',
        endDate: '2026-07-31',
      }),
      expect.any(AbortSignal),
    )
  })

  it('opens bid detail in a correctly titled dialog', async () => {
    vi.mocked(loadBidCost).mockResolvedValueOnce({
      ...bidding,
      projectId: 'P1',
      bidStatus: 'WON',
    })
    const { wrapper } = await mountPage(['bid:query'])

    await button(wrapper, '预览')!.trigger('click')
    await flushPromises()

    expect(loadBidCost).toHaveBeenCalledWith('11', expect.any(AbortSignal))
    expect(wrapper.get('[role="dialog"] h2').text()).toBe('投标成本预览')
    expect(wrapper.get('[role="dialog"]').text()).toContain('市民中心投标')
    expect(wrapper.get('[role="dialog"]').text()).not.toContain('关联项目')
    expect(wrapper.get('[role="dialog"]').text()).not.toContain('P1')
    await wrapper.get('button[aria-label="关闭对话框"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('aborts stale list request and keeps newest page', async () => {
    const first = deferred<BidCostPage>()
    const second = deferred<BidCostPage>()
    const signals: AbortSignal[] = []
    vi.mocked(loadBidCostPage)
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return first.promise
      })
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return second.promise
      })

    const mountTask = mountPage(['bid:query'], '/bid-cost?keyword=old')
    await flushPromises()
    const { wrapper, router } = await mountTask
    await router.push('/bid-cost?keyword=new')
    await flushPromises()
    second.resolve({ ...page, records: [{ ...bidding, bidProjectName: '最新投标' }] })
    await flushPromises()
    first.resolve({ ...page, records: [{ ...bidding, bidProjectName: '旧投标' }] })
    await flushPromises()

    expect(signals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('最新投标')
    expect(wrapper.text()).not.toContain('旧投标')
  })

  it('creates once during repeated clicks and re-reads authoritative detail', async () => {
    const pending = deferred<string>()
    vi.mocked(createBidCost).mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountPage(['bid:query', 'bid:add'])
    await button(wrapper, '新建投标成本')!.trigger('click')
    await wrapper.get('input[aria-label="投标项目名称"]').setValue(' 新投标 ')
    const create = wrapper.get('form')
    await create.trigger('submit')
    await create.trigger('submit')

    expect(createBidCost).toHaveBeenCalledTimes(1)
    expect(createBidCost).toHaveBeenCalledWith({ bidProjectName: '新投标', remark: null })
    pending.resolve('11')
    await flushPromises()
    expect(loadBidCost).toHaveBeenCalledWith('11', expect.any(AbortSignal))
    expect(wrapper.text()).toContain('投标成本已创建')
  })

  it('keeps local input and edit mode after save failure', async () => {
    vi.mocked(updateBidCost).mockRejectedValueOnce(apiError('状态已变化', 409))
    const { wrapper } = await mountPage(['bid:query', 'bid:edit'])
    await button(wrapper, '编辑')!.trigger('click')
    await flushPromises()
    await wrapper.get('input[aria-label="投标项目名称"]').setValue('本地改名')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(updateBidCost).toHaveBeenCalledTimes(1)
    expect(loadBidCost).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[role="dialog"] h2').text()).toBe('编辑投标成本')
    expect(wrapper.get('[role="dialog"]').text()).toContain('状态已变化')
    expect(wrapper.get('input[aria-label="投标项目名称"]').element.value).toBe('本地改名')
    expect(wrapper.text()).not.toContain('投标成本已保存')
  })

  it('keeps form validation inside the dialog and orders cancel before submit', async () => {
    const { wrapper } = await mountPage(['bid:query', 'bid:add'])
    await button(wrapper, '新建投标成本')!.trigger('click')

    const dialog = wrapper.get('[role="dialog"]')
    const labels = dialog.findAll('button').map((item) => item.text())
    expect(labels.indexOf('取消')).toBeLessThan(labels.indexOf('创建'))
    expect(button(wrapper, '创建')?.classes()).toContain('v2-glass-button')
    expect(dialog.get('form').attributes()).toHaveProperty('novalidate')
    await dialog.get('form').trigger('submit')

    expect(dialog.text()).toContain('投标项目名称不能为空')
    expect(dialog.get('input[aria-label="投标项目名称"]').attributes('aria-invalid')).toBe('true')
    expect(createBidCost).not.toHaveBeenCalled()
  })

  it('queries immediately when status changes', async () => {
    const { wrapper } = await mountPage(['bid:query'])

    await wrapper.get('button[data-value="WON"]').trigger('click')
    await flushPromises()

    expect(loadBidCostPage).toHaveBeenCalledTimes(2)
    expect(loadBidCostPage).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNo: 1, bidStatus: 'WON' }),
      expect.any(AbortSignal),
    )
  })

  it('marks won only with a visible project and refreshes authoritative list', async () => {
    vi.mocked(loadBidCostPage)
      .mockResolvedValueOnce(page)
      .mockResolvedValueOnce({
        ...page,
        records: [{ ...bidding, projectId: 'P1', bidStatus: 'WON' }],
      })
    const { wrapper } = await mountPage(['bid:query', 'bid:status'])
    await button(wrapper, '标记中标')!.trigger('click')
    await flushPromises()
    expect(loadProjectContextOptions).toHaveBeenCalledTimes(1)
    await button(wrapper, '确认更新')!.trigger('click')
    expect(wrapper.get('[role="dialog"]').text()).toContain('中标项目不能为空')
    expect(wrapper.get('[role="dialog"] [role="button"]').attributes('aria-invalid')).toBe('true')
    await wrapper.get('button[data-value="P1"]').trigger('click')
    await button(wrapper, '确认更新')!.trigger('click')
    await flushPromises()

    expect(markBidCostWon).toHaveBeenCalledWith('11', 'P1')
    expect(loadBidCostPage).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('已中标')
    expect(wrapper.text()).toContain('投标状态已更新')
  })

  it('fails closed on status 403 and does not fake success', async () => {
    vi.mocked(markBidCostLost).mockRejectedValueOnce(apiError('无状态变更权限', 403))
    const { wrapper } = await mountPage(['bid:query', 'bid:status'])
    await button(wrapper, '标记未中标')!.trigger('click')
    await button(wrapper, '确认更新')!.trigger('click')
    await flushPromises()

    expect(markBidCostLost).toHaveBeenCalledWith('11')
    expect(wrapper.text()).toContain('无状态变更权限')
    expect(wrapper.text()).not.toContain('投标状态已更新')
  })

  it('confirms delete and preserves record when delete fails', async () => {
    vi.mocked(deleteBidCost).mockRejectedValueOnce(apiError('仅投标中记录可删除', 409))
    const { wrapper } = await mountPage(['bid:query', 'bid:delete'])
    await button(wrapper, '删除')!.trigger('click')
    await button(wrapper, '确认删除')!.trigger('click')
    await flushPromises()

    expect(deleteBidCost).toHaveBeenCalledWith('11')
    expect(wrapper.text()).toContain('仅投标中记录可删除')
    expect(wrapper.text()).toContain('市民中心投标')
    expect(wrapper.text()).not.toContain('投标成本已删除')
  })
})
