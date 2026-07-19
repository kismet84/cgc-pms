import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { loadDashboard } from '@/services/dashboard'
import { loadVisibleProjects } from '@/services/projects'

const fetchMock = vi.fn<typeof fetch>()

function apiResponse<T>(data: T): Response {
  return new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('M2 request baseline', () => {
  it('rejects unauthorized roles and requests permitted aggregate data without a project', async () => {
    await expect(
      loadDashboard('finance', { projectId: '1' }, { roles: [], permissions: [] }),
    ).rejects.toMatchObject({ code: 'DASHBOARD_ROLE_FORBIDDEN' })
    fetchMock.mockImplementation(async () => apiResponse({ projectId: null }))
    await loadDashboard('finance', {}, { roles: [], permissions: ['dashboard:finance:view'] })
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/dashboard/finance')
  })

  it('requests only the selected role and emits month only for supported valid periods', async () => {
    fetchMock.mockImplementation(async () => apiResponse({ projectId: '7' }))
    const access = { roles: ['ADMIN'], permissions: [] }

    await loadDashboard('cost', { projectId: '7', period: '2026-07' }, access)
    await loadDashboard('finance', { projectId: '7', period: '2026-07' }, access)
    await loadDashboard('cost', { projectId: '7', period: 'invalid' }, access)
    await loadDashboard('cost', { period: '2026-07' }, access)
    await loadDashboard('mgmt', { projectId: '7', period: '2026-07' }, access)

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/dashboard/cost-manager?projectId=7&month=2026-07',
      '/api/dashboard/finance?projectId=7',
      '/api/dashboard/cost-manager?projectId=7',
      '/api/dashboard/cost-manager?month=2026-07',
      '/api/dashboard/management',
    ])
  })

  it('loads every visible project page using the supplied abort signal', async () => {
    fetchMock
      .mockResolvedValueOnce(
        apiResponse({
          records: [{ id: '1', projectCode: 'P1', projectName: '项目一', status: 'ACTIVE' }],
          total: 2,
          pageNo: 1,
          pageSize: 200,
        }),
      )
      .mockResolvedValueOnce(
        apiResponse({
          records: [{ id: '2', projectCode: 'P2', projectName: '项目二', status: 'ACTIVE' }],
          total: 2,
          pageNo: 2,
          pageSize: 200,
        }),
      )
    const controller = new AbortController()

    await expect(loadVisibleProjects(controller.signal)).resolves.toHaveLength(2)
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/projects?pageNo=1&pageSize=200',
      '/api/projects?pageNo=2&pageSize=200',
    ])
    expect(fetchMock.mock.calls.every(([, init]) => init?.signal === controller.signal)).toBe(true)
  })
})
