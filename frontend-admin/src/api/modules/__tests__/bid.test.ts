import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/request', () => ({ request: requestMock }))

import { getBidCosts } from '../bid'

describe('bid cost API contract', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('uses only the typed GET list parameters', async () => {
    const params = {
      pageNo: 2,
      pageSize: 20,
      bidStatus: 'BIDDING' as const,
      keyword: '学校',
    }

    await getBidCosts(params)

    expect(requestMock).toHaveBeenCalledWith({
      url: '/bid-cost',
      method: 'get',
      params,
    })
    expect(requestMock.mock.calls[0]?.[0]).not.toHaveProperty('data')
    expect(params).not.toHaveProperty('tenantId')
  })
})
