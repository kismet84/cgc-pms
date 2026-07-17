import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  getAccountingEntries,
  getAccountingEntryDetail,
  postAccountingEntry,
  resubmitAccountingEntry,
  reviewAccountingEntry,
  reverseAccountingEntry,
} from '../accounting'

describe('accounting entry API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('uses the list and detail endpoints', async () => {
    const params = { pageNo: 2, pageSize: 20, entryStatus: 'DRAFT' as const }
    await getAccountingEntries(params)
    await getAccountingEntryDetail('101')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/accounting-entry',
      method: 'get',
      params,
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/accounting-entry/101',
      method: 'get',
    })
  })

  it('uses PUT for review, resubmit, post and reverse transitions', async () => {
    await reviewAccountingEntry('100', true, '复核通过')
    await resubmitAccountingEntry('100')
    await postAccountingEntry('101')
    await reverseAccountingEntry('102', '会计冲销')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/accounting-entry/100/review',
      method: 'put',
      data: { approved: true, comment: '复核通过' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/accounting-entry/100/resubmit',
      method: 'put',
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/accounting-entry/101/post',
      method: 'put',
    })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/accounting-entry/102/reverse',
      method: 'put',
      data: { reason: '会计冲销' },
    })
  })
})
