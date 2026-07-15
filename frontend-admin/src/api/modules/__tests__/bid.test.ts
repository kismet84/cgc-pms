import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/request', () => ({ request: requestMock }))

import { createBidCost, getBidCost, getBidCosts, updateBidCost } from '../bid'

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

  it('posts only the controlled create fields', async () => {
    const data = { bidProjectName: '学校投标', remark: '资格预审阶段' }

    await createBidCost(data)

    expect(requestMock).toHaveBeenCalledWith({
      url: '/bid-cost',
      method: 'post',
      data,
    })
    expect(data).not.toHaveProperty('tenantId')
    expect(data).not.toHaveProperty('projectId')
    expect(data).not.toHaveProperty('bidStatus')
    expect(data).not.toHaveProperty('amount')
  })

  it('gets one bid without tenant or write parameters', async () => {
    await getBidCost('10001')

    expect(requestMock).toHaveBeenCalledWith({
      url: '/bid-cost/10001',
      method: 'get',
    })
    expect(requestMock.mock.calls[0]?.[0]).not.toHaveProperty('params')
    expect(requestMock.mock.calls[0]?.[0]).not.toHaveProperty('data')
    expect(JSON.stringify(requestMock.mock.calls[0]?.[0])).not.toContain('tenantId')
  })

  it('updates only the controlled editable fields', async () => {
    const data = { bidProjectName: '更新项目', remark: '更新备注' }
    await updateBidCost('10001', data)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/bid-cost/10001',
      method: 'put',
      data,
    })
    expect(data).not.toHaveProperty('tenantId')
    expect(data).not.toHaveProperty('projectId')
    expect(data).not.toHaveProperty('bidStatus')
    expect(data).not.toHaveProperty('amount')
    expect(data).not.toHaveProperty('id')
  })
})
