import { beforeEach, describe, expect, it, vi } from 'vitest'

const { mockRequest } = vi.hoisted(() => ({
  mockRequest: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  request: mockRequest,
}))

import {
  createOverheadAllocationRule,
  deleteOverheadAllocationRule,
  getCostSummaryHistory,
  updateOverheadAllocationRule,
} from '../cost'

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

  it('只发送间接费规则白名单字段', async () => {
    mockRequest.mockResolvedValue('rule-1')
    const data = {
      costSubjectId: 'subject-1',
      allocationBasis: 'DIRECT_LABOR' as const,
      allocationCycle: 'MONTHLY' as const,
    }

    await createOverheadAllocationRule(data)

    expect(mockRequest).toHaveBeenCalledWith({
      url: '/overhead-allocation/rules',
      method: 'post',
      data,
    })
    expect(data).not.toHaveProperty('id')
    expect(data).not.toHaveProperty('tenantId')
    expect(data).not.toHaveProperty('status')
  })

  it('修改间接费规则只使用路径 ID 和白名单字段', async () => {
    mockRequest.mockResolvedValue(undefined)
    const data = {
      costSubjectId: 'subject-2',
      allocationBasis: 'USAGE' as const,
      allocationCycle: 'PER_OCCURRENCE' as const,
    }

    await updateOverheadAllocationRule('rule-1', data)

    expect(mockRequest).toHaveBeenCalledWith({
      url: '/overhead-allocation/rules/rule-1',
      method: 'put',
      data,
    })
    expect(data).not.toHaveProperty('id')
    expect(data).not.toHaveProperty('tenantId')
    expect(data).not.toHaveProperty('status')
  })

  it('删除间接费规则只发送路径 ID 且没有请求体', async () => {
    mockRequest.mockResolvedValue(undefined)

    await deleteOverheadAllocationRule('rule-1')

    expect(mockRequest).toHaveBeenCalledWith({
      url: '/overhead-allocation/rules/rule-1',
      method: 'delete',
    })
    const requestConfig = mockRequest.mock.calls[0][0]
    expect(requestConfig).not.toHaveProperty('data')
    expect(requestConfig).not.toHaveProperty('params')
    expect(requestConfig).not.toHaveProperty('tenantId')
  })
})
