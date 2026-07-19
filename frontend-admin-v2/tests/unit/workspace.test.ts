import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

beforeEach(() => {
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
})
