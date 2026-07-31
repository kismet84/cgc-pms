import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  confirmAcceptanceArchive,
  confirmDrawingReview,
  createConstructionReference,
  createDrawingReview,
  createTechnicalDisclosure,
  createTechnicalRfi,
  getDrawingTrace,
  receiveDrawingVersion,
  respondTechnicalRfi,
  reviewTechnicalRfiResponse,
  submitTechnicalRfi,
} from '../technicalManagement'

describe('drawing RFI technical closed-loop API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('keeps review, RFI response and drawing revision on the same chain', async () => {
    await createDrawingReview('11', { reviewCode: 'RV-001' })
    await confirmDrawingReview('21')
    await createTechnicalRfi('21', { rfiCode: 'RFI-001' })
    await submitTechnicalRfi('31')
    await respondTechnicalRfi('31', { responseContent: '按变更版执行', changeRequired: true })
    await reviewTechnicalRfiResponse('41', { decision: 'ACCEPTED', reviewComment: '同意改版' })
    await receiveDrawingVersion('51', {
      versionNo: 'B',
      previousVersionId: '11',
      sourceRfiId: '31',
    })

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/technical-management/drawing-versions/11/reviews',
      method: 'post',
      data: { reviewCode: 'RV-001' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(6, {
      url: '/technical-management/rfi-responses/41/review',
      method: 'post',
      data: { decision: 'ACCEPTED', reviewComment: '同意改版' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(7, {
      url: '/technical-management/drawings/51/versions',
      method: 'post',
      data: { versionNo: 'B', previousVersionId: '11', sourceRfiId: '31' },
    })
  })

  it('keeps disclosure, construction fact, archive and reverse trace connected', async () => {
    await createTechnicalDisclosure('61', { drawingVersionId: '71', disclosureCode: 'TD-001' })
    await createConstructionReference('61', {
      disclosureId: '81',
      dailyLogId: '91',
      wbsTaskId: '92',
    })
    await confirmAcceptanceArchive('101')
    await getDrawingTrace('51')

    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/technical-management/projects/61/construction-references',
      method: 'post',
      data: { disclosureId: '81', dailyLogId: '91', wbsTaskId: '92' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/technical-management/drawings/51/trace',
      method: 'get',
    })
  })
})
