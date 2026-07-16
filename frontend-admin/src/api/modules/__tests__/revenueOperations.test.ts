import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())
vi.mock('@/api/request', () => ({ request: requestMock }))

import {
  createCollection,
  createOwnerSettlement,
  createSalesInvoice,
  getReceivables,
  getRevenueDashboard,
  getRevenueTrace,
  reverseCollection,
  submitOwnerSettlement,
} from '../revenueOperations'

describe('revenue collection API contracts', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue({}))

  it('uses the settlement, receivable and dashboard endpoints', async () => {
    await createOwnerSettlement({ projectId: '1' })
    await submitOwnerSettlement('10')
    await getReceivables('1', 'OPEN')
    await getRevenueDashboard('1')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/revenue-operations/settlements',
      method: 'post',
      data: { projectId: '1' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/revenue-operations/settlements/10/submit',
      method: 'post',
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/revenue-operations/receivables',
      method: 'get',
      params: { projectId: '1', status: 'OPEN' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/revenue-operations/dashboard/1',
      method: 'get',
    })
  })

  it('keeps invoice, collection, reversal and trace endpoints distinct', async () => {
    await createSalesInvoice({ invoiceNo: 'INV-1' })
    await createCollection({ externalTxnNo: 'BANK-1' })
    await reverseCollection('20', { reason: '银行退回', idempotencyKey: 'R-1' })
    await getRevenueTrace('30')

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: '/revenue-operations/sales-invoices',
      method: 'post',
      data: { invoiceNo: 'INV-1' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: '/revenue-operations/collections',
      method: 'post',
      data: { externalTxnNo: 'BANK-1' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: '/revenue-operations/collections/20/reverse',
      method: 'post',
      data: { reason: '银行退回', idempotencyKey: 'R-1' },
    })
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      url: '/revenue-operations/trace/cash-journals/30',
      method: 'get',
    })
  })
})
