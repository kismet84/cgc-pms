import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  createQualityConsequence,
  createQualityIssue,
  createQualityRectification,
  getQualityTrace,
  postQualityConsequence,
  reinspectQualityRectification,
  submitQualityInspection,
  submitQualityRectification,
} from '../qualitySafety'

describe('quality safety rectification closed-loop API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('keeps issue, rectification, reinspection and trace endpoints connected', async () => {
    await createQualityIssue('11', {
      inspectionId: '11',
      category: '临边防护',
      severity: 'HIGH',
      title: '防护缺失',
      description: '检查发现防护栏缺失',
      responsibleKind: 'PARTNER',
      responsiblePartnerId: '22',
      responsibleUserId: '33',
      dueDate: '2099-01-07',
    })
    await submitQualityInspection('11')
    await createQualityRectification({
      issueId: '44',
      actionDescription: '恢复防护栏',
      responsibleUserId: '33',
      plannedCompleteDate: '2099-01-05',
    })
    await submitQualityRectification('55')
    await reinspectQualityRectification('55', 'REJECT', '固定不牢，退回整改')
    await getQualityTrace('44')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/quality-safety/inspections/11/issues',
      method: 'post',
      data: expect.objectContaining({ responsiblePartnerId: '22' }),
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/quality-safety/inspections/11/submit',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/quality-safety/rectifications',
      method: 'post',
      data: expect.objectContaining({ issueId: '44' }),
    })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/quality-safety/rectifications/55/submit',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(5, {
      url: '/quality-safety/rectifications/55/reinspect',
      method: 'post',
      data: { result: 'REJECT', comment: '固定不牢，退回整改' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(6, {
      url: '/quality-safety/issues/44/trace',
      method: 'get',
    })
  })

  it('posts penalty, rework cost and partner evaluation as one consequence fact', async () => {
    await createQualityConsequence({
      issueId: '44',
      partnerId: '22',
      contractId: '66',
      consequenceCode: 'QS-C-001',
      decisionType: 'BOTH',
      fineAmount: 100,
      reworkCostAmount: 500,
      evaluationScore: 60,
      evaluationComment: '高等级问题扣减评分',
    })
    await postQualityConsequence('77')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/quality-safety/consequences',
      method: 'post',
      data: expect.objectContaining({ decisionType: 'BOTH', evaluationScore: 60 }),
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/quality-safety/consequences/77/post',
      method: 'post',
    })
  })
})
