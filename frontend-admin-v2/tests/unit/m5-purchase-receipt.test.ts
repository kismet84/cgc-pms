import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { PURCHASE_EXECUTION_PERMISSIONS } from '@cgc-pms/frontend-contracts'
import {
  createPurchaseOrder,
  createPurchaseRequest,
  createReceipt,
  deletePurchaseOrder,
  deletePurchaseRequest,
  deleteReceipt,
  loadPurchaseOrder,
  loadPurchaseOrderItems,
  loadPurchaseOrders,
  loadPurchaseRequest,
  loadPurchaseRequestItems,
  loadPurchaseRequests,
  loadMaterials,
  loadOrderItemsForReceipt,
  loadReceipt,
  loadReceiptItems,
  loadReceipts,
  savePurchaseOrderItems,
  savePurchaseRequestItems,
  saveReceiptItems,
  submitPurchaseOrder,
  submitPurchaseRequest,
  submitReceipt,
} from '@/services/supply-chain'

const fetchMock = vi.fn<typeof fetch>()
const response = (data: unknown = {}) =>
  new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  fetchMock
    .mockReset()
    .mockImplementation(async () => response({ records: [], total: 0, pageNo: 1, pageSize: 20 }))
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('M5 purchase request, order and receipt contract', () => {
  it('keeps query and write permissions separate', () => {
    expect(Object.values(PURCHASE_EXECUTION_PERMISSIONS)).toEqual([
      'purchase:request:list',
      'purchase:request:add',
      'purchase:request:edit',
      'purchase:request:delete',
      'purchase:request:submit',
      'purchase:order:query',
      'purchase:order:add',
      'purchase:order:edit',
      'purchase:order:delete',
      'purchase:order:submit',
      'receipt:query',
      'receipt:add',
      'receipt:edit',
      'receipt:delete',
      'receipt:submit',
    ])
  })

  it('uses one clean-room page and re-reads every successful write', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/supply-chain/PurchaseExecutionPage.vue'),
      'utf8',
    )
    expect(source).toContain("route.path === '/purchase/order'")
    expect(source).toContain("route.path === '/purchase/receipt'")
    expect(source).toContain('await loadPage()')
    expect(source).toContain('await selectRecord(refreshed)')
    expect(source).toContain('loadOrderItemsForReceipt')
    expect(source).toContain('loadMaterials')
    expect(source).toContain('loadPartners')
    expect(source).toContain('loadWarehouses')
    expect(source).toContain('remainingQuantity')
    expect(source).toContain("selected.value?.approvalStatus === 'DRAFT'")
    expect(source).not.toMatch(
      /frontend-admin\/src|Legacy|totalAmount\s*[+]=|receivedQuantity\s*[+]=|label="[^"]*ID/,
    )
  })

  it('preserves decimal strings and encoded ids across all endpoints', async () => {
    const signal = new AbortController().signal
    fetchMock.mockImplementation(async (url, init) => {
      if (
        init?.method === 'POST' &&
        !String(url).includes('/items/') &&
        !String(url).endsWith('/submit')
      )
        return response('9007199254740993')
      return response(
        String(url).includes('/items') ? [] : { records: [], total: 0, pageNo: 1, pageSize: 20 },
      )
    })
    await loadPurchaseRequests({ projectId: ' P/1 ' }, signal)
    await loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' }, signal)
    await loadPurchaseRequest('R/1', signal)
    await loadPurchaseRequestItems('R/1', signal)
    const requestId = await createPurchaseRequest({ projectId: 'P1', purpose: '钢材' })
    await savePurchaseRequestItems(requestId, [
      {
        requestId,
        materialId: 'M1',
        quantity: '9007199254740993.1234',
        estimatedUnitPrice: '0.01',
      },
    ])
    await submitPurchaseRequest('R/1')
    await deletePurchaseRequest('R/1')

    await loadPurchaseOrders({ projectId: 'P/1' }, signal)
    await loadPurchaseOrder('O/1', signal)
    await loadPurchaseOrderItems('O/1', signal)
    const orderId = await createPurchaseOrder({ projectId: 'P1', requestId: 'R1', partnerId: 'S1' })
    await savePurchaseOrderItems(orderId, [
      { orderId, requestItemId: 'RI1', quantity: '2.0000', unitPrice: '10.25', taxRate: '13' },
    ])
    await submitPurchaseOrder('O/1')
    await deletePurchaseOrder('O/1')

    await loadReceipts({ projectId: 'P/1' }, signal)
    await loadReceipt('RC/1', signal)
    await loadReceiptItems('RC/1', signal)
    await loadOrderItemsForReceipt('O/1', signal)
    const receiptId = await createReceipt({
      projectId: 'P1',
      orderId: 'O1',
      receiptMode: 'INVENTORY',
    })
    await saveReceiptItems(receiptId, [
      {
        receiptId,
        orderItemId: 'OI1',
        actualQuantity: '1.0000',
        qualifiedQuantity: '0.7500',
        unqualifiedQuantity: '0.2500',
      },
    ])
    await submitReceipt('RC/1')
    await deleteReceipt('RC/1')

    expect(requestId).toBe('9007199254740993')
    expect(orderId).toBe('9007199254740993')
    expect(receiptId).toBe('9007199254740993')
    const calls = fetchMock.mock.calls
    expect(calls.map(([url]) => String(url))).toContain('/api/purchase-requests/R%2F1/items')
    expect(calls.map(([url]) => String(url))).toContain('/api/purchase-orders/O%2F1/submit')
    expect(calls.map(([url]) => String(url))).toContain('/api/receipts/RC%2F1/items')
    expect(
      calls.find(
        ([url, init]) =>
          String(url).endsWith('/purchase-requests/R%2F1') && init?.method === 'DELETE',
      ),
    ).toBeDefined()
    expect(calls.filter(([, init]) => init?.signal === signal)).toHaveLength(11)
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/materials?pageNo=1&pageSize=200&status=ENABLE',
    )
    const requestItems = calls.find(([url]) =>
      String(url).endsWith('/purchase-requests/9007199254740993/items/batch'),
    )
    expect(JSON.parse(String(requestItems?.[1]?.body))[0].quantity).toBe('9007199254740993.1234')
  })

  it('propagates over-receipt 409 without client retry', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ code: 'RECEIPT_QUANTITY_EXCEEDED', message: '验收数量超过订单剩余数量' }),
        {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )
    await expect(
      saveReceiptItems('R1', [
        {
          receiptId: 'R1',
          orderItemId: 'O1',
          actualQuantity: '2',
          qualifiedQuantity: '2',
          unqualifiedQuantity: '0',
        },
      ]),
    ).rejects.toMatchObject({
      status: 409,
      code: 'RECEIPT_QUANTITY_EXCEEDED',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
