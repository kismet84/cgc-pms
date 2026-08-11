import type {
  QualityInspectionRecord,
  QualityIssueRecord,
  QualityPlanRecord,
  QualityWorkspace,
  QualityWorkspaceView,
} from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QualitySafetyPage from '@/pages/delivery/QualitySafetyPage.vue'
import { loadQualityWorkspace } from '@/services/quality'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/services/quality', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/quality')>()),
  loadQualityWorkspace: vi.fn(),
}))

const plan: QualityPlanRecord = {
  id: '101',
  projectId: 'P1',
  planCode: 'PLAN-101',
  planName: '服务端计划',
  inspectionType: 'QUALITY',
  frequencyType: 'SINGLE',
  startDate: '2026-08-01',
  endDate: '2026-08-31',
  ownerUserId: '1',
  status: 'ACTIVE',
}

const inspection: QualityInspectionRecord = {
  id: '201',
  planId: plan.id,
  projectId: plan.projectId,
  inspectionCode: 'INS-201',
  inspectionDate: '2026-08-12',
  location: '现场',
  inspectorUserId: '1',
  conclusion: 'PENDING',
  summary: '服务端检查',
  status: 'DRAFT',
}

const issue: QualityIssueRecord = {
  id: '301',
  planId: plan.id,
  inspectionId: inspection.id,
  projectId: plan.projectId,
  issueCode: 'ISS-301',
  issueType: 'QUALITY',
  category: '现场',
  severity: 'MEDIUM',
  title: '服务端问题',
  description: '描述',
  responsibleKind: 'INTERNAL',
  responsibleUserId: '1',
  dueDate: '2026-08-20',
  status: 'RECTIFYING',
}

function response(view: QualityWorkspaceView, pageNo = 1, projectId = 'P1'): QualityWorkspace {
  const records =
    view === 'plan'
      ? [{ ...plan, id: `${pageNo}01`, projectId, planName: `服务端计划-${projectId}-${pageNo}` }]
      : view === 'inspection'
        ? [{ ...inspection, projectId }]
        : [{ ...issue, projectId }]
  return {
    view,
    page: { records, total: view === 'plan' ? 21 : 1, pageNo, pageSize: 10 },
    counts: { plan: 21, inspection: 1, rectification: 1, reinspection: 0, consequence: 0 },
    selectedPlanRef: { ...plan, projectId },
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve
  })
  return { promise, resolve }
}

async function mountPage() {
  setActivePinia(createPinia())
  const session = useSessionStore()
  session.userInfo = {
    tenantId: '7',
    userId: '1',
    username: 'tester',
    roles: ['USER'],
    permissions: ['quality:safety:query'],
  }
  session.status = 'authenticated'
  const workspace = useWorkspaceStore()
  workspace.setProjects([
    { value: 'P1', label: '项目一' },
    { value: 'P2', label: '项目二' },
  ])
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/quality-safety', component: QualitySafetyPage }],
  })
  await router.push('/quality-safety')
  await router.isReady()
  const wrapper = mount(QualitySafetyPage, {
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return { wrapper, workspace }
}

beforeEach(() => {
  vi.mocked(loadQualityWorkspace)
    .mockReset()
    .mockImplementation(async (query) =>
      response(query.view, query.pageNo, query.projectId ?? 'ALL'),
    )
})

describe('M91 F03 quality safety server pagination', () => {
  it('uses one workspace request for initial load, page, tab and project changes', async () => {
    const { wrapper, workspace } = await mountPage()
    expect(loadQualityWorkspace).toHaveBeenCalledTimes(1)
    expect(vi.mocked(loadQualityWorkspace).mock.calls[0]?.[0]).toMatchObject({
      view: 'plan',
      pageNo: 1,
      pageSize: 10,
    })

    await wrapper
      .findAll('button')
      .find((item) => item.text().includes('下一页'))!
      .trigger('click')
    await flushPromises()
    expect(loadQualityWorkspace).toHaveBeenCalledTimes(2)
    expect(vi.mocked(loadQualityWorkspace).mock.calls[1]?.[0]).toMatchObject({
      view: 'plan',
      pageNo: 2,
    })

    await wrapper.get('#quality-tab-inspection').trigger('click')
    await flushPromises()
    expect(
      vi.mocked(loadQualityWorkspace).mock.calls.map(([query]) => ({
        view: query.view,
        pageNo: query.pageNo,
        projectId: query.projectId,
        planId: query.planId,
      })),
    ).toEqual([
      { view: 'plan', pageNo: 1, projectId: undefined, planId: undefined },
      { view: 'plan', pageNo: 2, projectId: undefined, planId: '101' },
      { view: 'inspection', pageNo: 1, projectId: undefined, planId: '101' },
    ])
    expect(vi.mocked(loadQualityWorkspace).mock.calls[2]?.[0]).toMatchObject({
      view: 'inspection',
      pageNo: 1,
    })

    workspace.selectProject('P2')
    await flushPromises()
    expect(loadQualityWorkspace).toHaveBeenCalledTimes(4)
    expect(vi.mocked(loadQualityWorkspace).mock.calls[3]?.[0]).toMatchObject({
      view: 'inspection',
      pageNo: 1,
      projectId: 'P2',
    })
  })

  it('aborts and ignores an older all-project response after project selection', async () => {
    const oldRequest = deferred<QualityWorkspace>()
    const newRequest = deferred<QualityWorkspace>()
    const signals: AbortSignal[] = []
    vi.mocked(loadQualityWorkspace)
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return oldRequest.promise
      })
      .mockImplementationOnce(async (_query, signal) => {
        signals.push(signal!)
        return newRequest.promise
      })

    const { wrapper, workspace } = await mountPage()
    workspace.selectProject('P1')
    await flushPromises()
    newRequest.resolve({
      ...response('plan', 1, 'P1'),
      page: { ...response('plan').page, records: [{ ...plan, planName: '最新项目响应' }] },
    })
    await flushPromises()
    oldRequest.resolve({
      ...response('plan', 1, 'ALL'),
      page: { ...response('plan').page, records: [{ ...plan, planName: '迟到旧响应' }] },
    })
    await flushPromises()

    expect(signals[0]?.aborted).toBe(true)
    expect(wrapper.text()).toContain('最新项目响应')
    expect(wrapper.text()).not.toContain('迟到旧响应')
  })
})
