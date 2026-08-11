import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createBidTransferRequest,
  createFinanceAllocationRequest,
  loadBidTransferRequests,
  loadCostSubjectTree,
  loadFinanceAllocationRequests,
  submitBidTransferRequest,
  submitFinanceAllocationRequest,
} from '@/services/cost-subject'
import { apiRequest } from '@/services/request'

vi.mock('@/services/request', () => ({ apiRequest: vi.fn() }))

describe('cost subject service', () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset())

  it('normalizes numeric target ratios to decimal strings', async () => {
    vi.mocked(apiRequest).mockResolvedValue([
      {
        id: 901001,
        parentId: 900060,
        subjectCode: '5401.03.01',
        subjectName: '人工成本',
        subjectType: 'LABOR',
        accountCategory: 'COST',
        level: 3,
        sortOrder: 1,
        status: 'ENABLE',
        defaultTargetRatio: 25,
        children: [],
      },
    ])

    const [subject] = await loadCostSubjectTree()

    expect(subject?.id).toBe('901001')
    expect(subject?.defaultTargetRatio).toBe('25')
  })

  it('uses separate draft and submit endpoints for transfer and allocation workflows', async () => {
    vi.mocked(apiRequest)
      .mockResolvedValueOnce([
        {
          id: 41,
          request_code: 'BTR-001',
          total_amount: 12.5,
          status: 'DRAFT',
        },
      ])
      .mockResolvedValueOnce({ id: 42, requestCode: 'BTR-002', totalAmount: 20, status: 'DRAFT' })
      .mockResolvedValueOnce({
        id: 42,
        requestCode: 'BTR-002',
        totalAmount: 20,
        status: 'SUBMITTED',
        approvalInstanceId: 9001,
      })
      .mockResolvedValueOnce([
        {
          id: 51,
          request_code: 'FAR-001',
          source_amount: 8.25,
          status: 'DRAFT',
        },
      ])
      .mockResolvedValueOnce({ id: 52, requestCode: 'FAR-002', sourceAmount: 9, status: 'DRAFT' })
      .mockResolvedValueOnce({
        id: 52,
        requestCode: 'FAR-002',
        sourceAmount: 9,
        status: 'SUBMITTED',
        approvalInstanceId: 9002,
      })

    const transferCommand = {
      bidCostId: 'BID-1',
      projectId: 'P-1',
      targetId: 'TARGET-1',
      mappingVersionId: 'MAP-1',
      idempotencyKey: 'BTR-K1',
      remark: '',
    }
    const allocationCommand = {
      sourceType: 'VOUCHER_LINE',
      sourceId: 'V-1',
      allocationBasis: 'BENEFIT_AMOUNT',
      accountingPeriod: '2026-08',
      costSubjectId: '111',
      idempotencyKey: 'FAR-K1',
      remark: '',
      lines: [{ projectId: 'P-1', basisValue: '1' }],
    }

    const transfers = await loadBidTransferRequests()
    await createBidTransferRequest(transferCommand)
    const submittedTransfer = await submitBidTransferRequest('42')
    const allocations = await loadFinanceAllocationRequests()
    await createFinanceAllocationRequest(allocationCommand)
    const submittedAllocation = await submitFinanceAllocationRequest('52')

    expect(transfers[0]).toMatchObject({ id: '41', requestCode: 'BTR-001', totalAmount: '12.5' })
    expect(submittedTransfer).toMatchObject({
      id: '42',
      status: 'SUBMITTED',
      approvalInstanceId: '9001',
    })
    expect(allocations[0]).toMatchObject({
      id: '51',
      requestCode: 'FAR-001',
      sourceAmount: '8.25',
    })
    expect(submittedAllocation).toMatchObject({
      id: '52',
      status: 'SUBMITTED',
      approvalInstanceId: '9002',
    })
    expect(apiRequest).toHaveBeenNthCalledWith(1, '/cost-subject-v2/bid-transfer-requests', {
      signal: undefined,
    })
    expect(apiRequest).toHaveBeenNthCalledWith(2, '/cost-subject-v2/bid-transfer-requests', {
      method: 'POST',
      body: transferCommand,
    })
    expect(apiRequest).toHaveBeenNthCalledWith(
      3,
      '/cost-subject-v2/bid-transfer-requests/42/submit',
      { method: 'POST' },
    )
    expect(apiRequest).toHaveBeenNthCalledWith(4, '/cost-subject-v2/finance-allocation-requests', {
      signal: undefined,
    })
    expect(apiRequest).toHaveBeenNthCalledWith(5, '/cost-subject-v2/finance-allocation-requests', {
      method: 'POST',
      body: allocationCommand,
    })
    expect(apiRequest).toHaveBeenNthCalledWith(
      6,
      '/cost-subject-v2/finance-allocation-requests/52/submit',
      { method: 'POST' },
    )
    expect(transferCommand).not.toHaveProperty('approvalInstanceId')
    expect(allocationCommand).not.toHaveProperty('approvalInstanceId')
  })
})
