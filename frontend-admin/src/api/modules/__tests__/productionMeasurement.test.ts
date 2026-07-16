import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  createMeasurementPeriod,
  createOwnerMeasurementSubmission,
  createProductionMeasurement,
  getMeasurementSettlementTrace,
  getMeasurementSources,
  reviewOwnerMeasurementSubmission,
  submitProductionMeasurement,
} from '../productionMeasurement'

describe('production measurement API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('keeps period, source, measurement and workflow endpoints distinct', async () => {
    await createMeasurementPeriod({ projectId: '1' })
    await getMeasurementSources('1', '2')
    await createProductionMeasurement({ periodId: '3' })
    await submitProductionMeasurement('4')

    expect(requestMock).toHaveBeenNthCalledWith(1, { url: '/production-measurements/periods', method: 'post', data: { projectId: '1' } })
    expect(requestMock).toHaveBeenNthCalledWith(2, { url: '/production-measurements/sources', method: 'get', params: { projectId: '1', contractId: '2' } })
    expect(requestMock).toHaveBeenNthCalledWith(3, { url: '/production-measurements', method: 'post', data: { periodId: '3' } })
    expect(requestMock).toHaveBeenNthCalledWith(4, { url: '/production-measurements/4/submit', method: 'post' })
  })

  it('uses versioned owner submission, review and reverse trace endpoints', async () => {
    await createOwnerMeasurementSubmission('4', { attachmentCount: 1 })
    await reviewOwnerMeasurementSubmission('5', { decision: 'CONFIRMED' })
    await getMeasurementSettlementTrace('6')

    expect(requestMock).toHaveBeenNthCalledWith(1, { url: '/production-measurements/4/owner-submissions', method: 'post', data: { attachmentCount: 1 } })
    expect(requestMock).toHaveBeenNthCalledWith(2, { url: '/production-measurements/owner-submissions/5/review', method: 'post', data: { decision: 'CONFIRMED' } })
    expect(requestMock).toHaveBeenNthCalledWith(3, { url: '/production-measurements/trace/settlements/6', method: 'get' })
  })
})
