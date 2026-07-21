import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadVisibleProjects } from '@/services/projects'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/services/projects', () => ({ loadVisibleProjects: vi.fn() }))

const loadVisibleProjectsMock = vi.mocked(loadVisibleProjects)

beforeEach(() => {
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

describe('V2 workspace context store', () => {
  it('does not retain unknown project or report-period values from a deep link', () => {
    const workspace = useWorkspaceStore()
    workspace.setProjects([{ value: 'P-1', label: '项目一' }])
    workspace.setReportPeriods([{ value: '2026-07', label: '2026年7月' }])

    workspace.syncRoute('/dashboard', { projectId: 'P-unknown', period: '2025-12' }, {})

    expect(workspace.selectedProjectId).toBeNull()
    expect(workspace.selectedReportPeriod).toBeNull()
  })

  it('restores valid route context and derives object context from route params', () => {
    const workspace = useWorkspaceStore()
    workspace.setProjects([{ value: 'P-1', label: '项目一' }])
    workspace.setReportPeriods([{ value: '2026-07', label: '2026年7月' }])

    workspace.syncRoute(
      '/project/P-1/overview',
      { projectId: 'P-1', period: '2026-07' },
      { projectId: 'P-1' },
    )

    expect(workspace.selectedProjectId).toBe('P-1')
    expect(workspace.selectedReportPeriod).toBe('2026-07')
    expect(workspace.objectContext).toEqual({ kind: 'project', id: 'P-1' })
  })

  it('keeps global context when a navigation target omits context query keys', () => {
    const workspace = useWorkspaceStore()
    workspace.setProjects([{ value: 'P-1', label: '项目一' }])
    workspace.setReportPeriods([{ value: '2026-07', label: '2026年7月' }])
    workspace.syncRoute('/project/list', { projectId: 'P-1', period: '2026-07' }, {})

    workspace.syncRoute('/dashboard', { role: 'pm' }, {})

    expect(workspace.selectedProjectId).toBe('P-1')
    expect(workspace.selectedReportPeriod).toBe('2026-07')
  })

  it('clears every context when the session is cleared', async () => {
    const workspace = useWorkspaceStore()
    const session = useSessionStore()
    workspace.setProjects([{ value: 'P-1', label: '项目一' }])
    workspace.setReportPeriods([{ value: '2026-07', label: '2026年7月' }])
    workspace.syncRoute('/contract/C-1', {}, { id: 'C-1' })

    await session.clearSession()

    expect(workspace.projects).toEqual([])
    expect(workspace.reportPeriods).toEqual([])
    expect(workspace.objectContext).toBeNull()
  })

  it('loads only permitted projects and ignores a stale context response', async () => {
    let resolveFirst: ((value: Awaited<ReturnType<typeof loadVisibleProjects>>) => void) | undefined
    loadVisibleProjectsMock
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirst = resolve
          }),
      )
      .mockResolvedValueOnce([
        { id: 'P-1', projectCode: 'P1', projectName: '项目一', status: 'DRAFT' },
        { id: 'P-2', projectCode: 'P2', projectName: '项目二', status: 'ACTIVE' },
      ])
    const workspace = useWorkspaceStore()

    const stale = workspace.initialize(['ADMIN'], [])
    await workspace.initialize(['ADMIN'], [])
    resolveFirst?.([{ id: 'P-1', projectCode: 'P1', projectName: '项目一', status: 'ACTIVE' }])
    await stale

    expect(workspace.projects).toEqual([
      { value: 'P-1', label: '项目一', status: 'DRAFT' },
      { value: 'P-2', label: '项目二', status: 'ACTIVE' },
    ])
    expect(workspace.selectedProjectId).toBeNull()
    expect(workspace.selectedReportPeriod).toBeNull()
    expect(workspace.reportPeriods).toHaveLength(12)
  })

  it('does not request projects without project query access', async () => {
    const workspace = useWorkspaceStore()

    await workspace.initialize(['USER'], ['dashboard:finance:view'])

    expect(loadVisibleProjectsMock).not.toHaveBeenCalled()
    expect(workspace.projects).toEqual([])
    expect(workspace.reportPeriods).toHaveLength(12)
  })
})
