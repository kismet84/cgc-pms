import { beforeEach, describe, expect, it, vi } from 'vitest'

const { mockRequest } = vi.hoisted(() => ({
  mockRequest: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  request: mockRequest,
}))

import { getCostSummaryHistory } from '../cost'

describe('cost summary history api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('只发送选定项目的历史 GET 请求且不携带租户或写参数', async () => {
    mockRequest.mockResolvedValue([])

    await getCostSummaryHistory('project-1')

    expect(mockRequest).toHaveBeenCalledOnce()
    expect(mockRequest).toHaveBeenCalledWith({
      url: '/cost-summary/project-1/history',
      method: 'get',
    })
    const requestConfig = mockRequest.mock.calls[0][0]
    expect(requestConfig).not.toHaveProperty('data')
    expect(requestConfig).not.toHaveProperty('params')
    expect(requestConfig).not.toHaveProperty('tenantId')
  })
})
