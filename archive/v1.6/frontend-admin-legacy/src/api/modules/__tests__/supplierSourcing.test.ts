import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  addSourcingSuppliers,
  awardSourcingEvent,
  confirmSupplierReturn,
  createBidEvaluation,
  createSupplierReturn,
  getSourcingTrace,
  linkSourcingContract,
  publishSourcingEvent,
  reviewSupplierBlacklist,
  submitSupplierQuote,
} from '../supplierSourcing'

describe('supplier sourcing performance closed-loop API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('keeps invitation, quote, evaluation, award, contract and trace connected', async () => {
    await addSourcingSuppliers('10', ['21', '22', '23'])
    await publishSourcingEvent('10')
    await submitSupplierQuote('31')
    await createBidEvaluation({
      quoteId: '31',
      commercialScore: 90,
      technicalScore: 85,
      deliveryScore: 80,
      qualityScore: 88,
      evaluationComment: '综合评审通过',
    })
    await awardSourcingEvent('10', '31', '综合评分最高')
    await linkSourcingContract('10', '41')
    await getSourcingTrace('10')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/supplier-sourcing/events/10/suppliers',
      method: 'post',
      data: { partnerIds: ['21', '22', '23'] },
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/supplier-sourcing/events/10/publish',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/supplier-sourcing/quotes/31/submit',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(5, {
      url: '/supplier-sourcing/events/10/award',
      method: 'post',
      data: { quoteId: '31', awardReason: '综合评分最高' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(7, {
      url: '/supplier-sourcing/events/10/trace',
      method: 'get',
    })
  })

  it('keeps supplier return and blacklist decisions as auditable state transitions', async () => {
    await createSupplierReturn({
      receiptId: '51',
      returnCode: 'SRT-001',
      returnDate: '2026-07-17',
      returnQuantity: 2,
      returnAmount: 2000,
      reason: '质量不合格',
    })
    await confirmSupplierReturn('61')
    await reviewSupplierBlacklist('71', 'APPROVE', '事实完整，同意纳入')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/supplier-sourcing/returns',
      method: 'post',
      data: expect.objectContaining({ receiptId: '51', returnQuantity: 2 }),
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/supplier-sourcing/returns/61/confirm',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/supplier-sourcing/blacklists/71/review',
      method: 'post',
      data: { decision: 'APPROVE', comment: '事实完整，同意纳入' },
    })
  })
})
