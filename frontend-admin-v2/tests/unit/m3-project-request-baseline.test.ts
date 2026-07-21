import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  loadProject,
  loadProjectMembers,
  loadProjectOverview,
  loadProjectPage,
} from '@/services/projects'

const fetchMock = vi.fn<typeof fetch>()

function apiResponse<T>(data: T): Response {
  return new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockImplementation(async () => apiResponse({ records: [], total: 0 }))
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

describe('M3 project request baseline', () => {
  it('encodes non-empty project filters and passes the abort signal', async () => {
    const controller = new AbortController()
    await loadProjectPage(
      { pageNo: 2, pageSize: 20, keyword: ' 项目 A&B ', projectType: '', status: 'ACTIVE' },
      controller.signal,
    )

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/projects?pageNo=2&pageSize=20&keyword=%E9%A1%B9%E7%9B%AE+A%26B&status=ACTIVE',
    )
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBe(controller.signal)
  })

  it('uses only current project detail, overview and member endpoints', async () => {
    fetchMock.mockImplementation(async () => apiResponse({}))

    await loadProject('P/1')
    await loadProjectOverview('P/1')
    await loadProjectMembers('P/1', { pageNo: 1, roleCode: 'PM' })

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/projects/P%2F1',
      '/api/projects/P%2F1/overview',
      '/api/projects/P%2F1/members?pageNo=1&roleCode=PM',
    ])
  })

  it('rejects an empty project id before sending a request', () => {
    expect(() => loadProject('  ')).toThrow('项目ID不能为空')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('keeps authoritative overview amounts as strings', async () => {
    fetchMock.mockImplementationOnce(async () =>
      apiResponse({
        projectId: '1',
        contractCount: '2',
        totalContractAmount: '9007199254740993.01',
        dynamicCost: '10.00',
        paidAmount: '3.20',
        warningCount: '0',
        memberCount: '0',
        members: [],
      }),
    )

    await expect(loadProjectOverview('1')).resolves.toMatchObject({
      totalContractAmount: '9007199254740993.01',
      dynamicCost: '10.00',
    })
  })
})
