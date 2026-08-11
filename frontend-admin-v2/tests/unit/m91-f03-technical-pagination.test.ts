import type {
  TechnicalOverview,
  TechnicalWorkspace,
  TechnicalWorkspaceView,
} from '@cgc-pms/frontend-contracts'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TechnicalManagementPage from '@/pages/delivery/TechnicalManagementPage.vue'
import { loadTechnicalOverview, loadTechnicalWorkspace } from '@/services/technical'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/services/technical', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/technical')>()),
  loadTechnicalOverview: vi.fn(),
  loadTechnicalWorkspace: vi.fn(),
}))

const pageSource = readFileSync(
  resolve(process.cwd(), 'src/pages/delivery/TechnicalManagementPage.vue'),
  'utf8',
)

function workspaceResponse(
  view: TechnicalWorkspaceView,
  pageNo = 1,
  secondaryPageNo = 1,
): TechnicalWorkspace {
  const scheme = {
    id: `scheme-${pageNo}`,
    projectId: 'P1',
    schemeCode: `S-${pageNo}`,
    schemeName: `服务端方案-${pageNo}`,
    schemeType: 'SPECIAL' as const,
    responsibleUserId: '1',
    plannedEffectiveDate: '2026-08-12',
    status: 'APPROVED' as const,
  }
  const drawing = {
    id: `drawing-${pageNo}`,
    projectId: 'P1',
    drawingCode: `D-${pageNo}`,
    drawingName: `服务端图纸-${pageNo}`,
    specialty: '建筑',
    sourceOrganization: '设计院',
    currentVersionId: `version-${pageNo}`,
    currentVersionNo: 'V1',
    currentVersionStatus: 'APPROVED' as const,
    status: 'ACTIVE' as const,
  }
  const version = {
    id: `version-${secondaryPageNo}`,
    drawingId: drawing.id,
    drawingCode: drawing.drawingCode,
    versionNo: `V${secondaryPageNo}`,
    receivedAt: '2026-08-12T08:00:00',
    status: 'APPROVED' as const,
  }
  const primary = view === 'drawing' ? [drawing] : [scheme]
  return {
    view,
    counts: { scheme: 21, drawing: 21, review: 4, rfi: 3, disclosure: 2, archive: 1 },
    primary: { records: primary, total: 21, pageNo, pageSize: 10 },
    secondary:
      view === 'drawing'
        ? { records: [version], total: 21, pageNo: secondaryPageNo, pageSize: 10 }
        : null,
  }
}

function selectedOverview(name: string): TechnicalOverview {
  return {
    schemes: [
      {
        id: 'selected-scheme',
        projectId: 'P1',
        schemeCode: 'SELECTED',
        schemeName: name,
        schemeType: 'SPECIAL',
        responsibleUserId: '1',
        plannedEffectiveDate: '2026-08-12',
        status: 'APPROVED',
      },
    ],
    drawings: [],
    versions: [],
    reviews: [],
    rfis: [],
    responses: [],
    disclosures: [],
    constructionReferences: [],
    archives: [],
    constructionFacts: [],
    qualityInspections: [],
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
    permissions: ['technical:query'],
  }
  session.status = 'authenticated'
  const workspace = useWorkspaceStore()
  workspace.setProjects([
    { value: 'P1', label: '项目一' },
    { value: 'P2', label: '项目二' },
  ])
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/technical-management', component: TechnicalManagementPage }],
  })
  await router.push('/technical-management')
  await router.isReady()
  const wrapper = mount(TechnicalManagementPage, {
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return { wrapper, workspace }
}

beforeEach(() => {
  vi.mocked(loadTechnicalWorkspace)
    .mockReset()
    .mockImplementation(async (query) =>
      workspaceResponse(query.view, query.pageNo, query.secondaryPageNo),
    )
  vi.mocked(loadTechnicalOverview).mockReset().mockResolvedValue(selectedOverview('选中项目方案'))
})

describe('M91 F03 technical server pagination', () => {
  it('issues exactly one workspace request for tab and each composite page change', async () => {
    const { wrapper } = await mountPage()
    expect(loadTechnicalWorkspace).toHaveBeenCalledTimes(1)
    expect(vi.mocked(loadTechnicalWorkspace).mock.calls[0]?.[0]).toEqual({
      view: 'scheme',
      pageNo: 1,
      pageSize: 10,
      secondaryPageNo: 1,
    })

    await wrapper.get('#technical-tab-drawing').trigger('click')
    await flushPromises()
    expect(loadTechnicalWorkspace).toHaveBeenCalledTimes(2)
    expect(vi.mocked(loadTechnicalWorkspace).mock.calls[1]?.[0]).toMatchObject({
      view: 'drawing',
      pageNo: 1,
      secondaryPageNo: 1,
    })

    await wrapper
      .get('nav[aria-label="图纸分页"]')
      .findAll('button')
      .find((button) => button.text().includes('下一页'))!
      .trigger('click')
    await flushPromises()
    expect(loadTechnicalWorkspace).toHaveBeenCalledTimes(3)
    expect(vi.mocked(loadTechnicalWorkspace).mock.calls[2]?.[0]).toMatchObject({
      view: 'drawing',
      pageNo: 2,
      secondaryPageNo: 1,
    })

    await wrapper
      .get('nav[aria-label="图纸版本分页"]')
      .findAll('button')
      .find((button) => button.text().includes('下一页'))!
      .trigger('click')
    await flushPromises()
    expect(loadTechnicalWorkspace).toHaveBeenCalledTimes(4)
    expect(vi.mocked(loadTechnicalWorkspace).mock.calls[3]?.[0]).toMatchObject({
      view: 'drawing',
      pageNo: 2,
      secondaryPageNo: 2,
    })
  })

  it('aborts and ignores a late all-project page after selecting one project', async () => {
    const { wrapper, workspace } = await mountPage()
    const stale = deferred<TechnicalWorkspace>()
    let staleSignal: AbortSignal | undefined
    vi.mocked(loadTechnicalWorkspace).mockImplementationOnce(async (query, signal) => {
      staleSignal = signal
      return stale.promise
    })

    await wrapper
      .get('nav[aria-label="技术方案分页"]')
      .findAll('button')
      .find((button) => button.text().includes('下一页'))!
      .trigger('click')
    workspace.selectProject('P1')
    await flushPromises()
    expect(loadTechnicalOverview).toHaveBeenCalledTimes(1)
    expect(staleSignal?.aborted).toBe(true)

    stale.resolve(workspaceResponse('scheme', 2))
    await flushPromises()
    expect(wrapper.text()).toContain('选中项目方案')
    expect(wrapper.text()).not.toContain('服务端方案-2')
  })

  it('contains no project-list fan-out in the all-project loader', () => {
    expect(pageSource).toContain('loadTechnicalWorkspace(')
    expect(pageSource).toContain('const requestGeneration = ++generation')
    expect(pageSource).toContain('requestGeneration === generation')
    expect(pageSource).not.toContain('scopeProjectIds')
    expect(pageSource).not.toContain('workspace.projects.map')
    expect(pageSource).not.toContain('Promise.all')
  })
})
