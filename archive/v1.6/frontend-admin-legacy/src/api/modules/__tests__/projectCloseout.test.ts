import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  acceptArchiveTransfer,
  bindFinalSettlement,
  closeProjectFromCloseout,
  createCloseoutDefect,
  getCloseoutTrace,
  rectifyCloseoutDefect,
  releaseCloseoutWarranty,
  verifyCloseoutDefect,
  verifyTailCollection,
} from '../projectCloseout'

describe('project completion closeout API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('keeps settlement, collection, warranty and defect actions on the closeout chain', async () => {
    await bindFinalSettlement('10', '20')
    await verifyTailCollection('10')
    await createCloseoutDefect('30', { defectCode: 'DF-001' })
    await rectifyCloseoutDefect('40', { rectificationContent: '完成整改' })
    await verifyCloseoutDefect('40', { decision: 'ACCEPTED', verificationComment: '通过' })
    await releaseCloseoutWarranty('30')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/project-closeouts/10/final-settlement',
      method: 'post',
      data: { ownerSettlementId: '20' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(5, {
      url: '/project-closeouts/defects/40/verify',
      method: 'post',
      data: { decision: 'ACCEPTED', verificationComment: '通过' },
    })
  })

  it('keeps archive acceptance, project close and reverse trace connected', async () => {
    await acceptArchiveTransfer('50')
    await closeProjectFromCloseout('10', { actualCompletionDate: '2026-07-17', reason: '收尾完成' })
    await getCloseoutTrace('10')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/project-closeouts/archive-transfers/50/accept',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/project-closeouts/10/trace',
      method: 'get',
    })
  })
})
