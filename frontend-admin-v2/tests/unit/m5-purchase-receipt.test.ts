import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { PURCHASE_EXECUTION_PERMISSIONS } from '@cgc-pms/frontend-contracts'
import {
  createPurchaseOrder,
  createPurchaseOrderFromRequest,
  createPurchaseRequest,
  createReceipt,
  confirmReceiptSupplierReturn,
  deletePurchaseOrder,
  deletePurchaseRequest,
  deleteReceipt,
  loadPurchaseOrder,
  loadPurchaseOrderItems,
  loadPurchaseOrderPricingSuggestion,
  loadPurchaseOrders,
  loadPurchaseRequest,
  loadPurchaseRequestFormOptions,
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
  updatePurchaseOrder,
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
      'receipt:return',
    ])
  })

  it('uses route-layered procurement execution and re-reads every successful write', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/supply-chain/PurchaseExecutionPage.vue'),
      'utf8',
    )
    expect(source).toContain("route.path === '/purchase/order'")
    expect(source).toContain("route.path === '/purchase/receipt'")
    expect(source).toContain('attachmentDocumentType')
    expect(source).toContain('uploadReceiptAttachment')
    expect(source).toContain('DELIVERY_NOTE')
    expect(source).toContain('MATERIAL_ACCEPTANCE_FORM')
    expect(source).toContain('attachmentScanStatus')
    expect(source).toContain('systemBatchNo')
    expect(source).toContain('deliveryNoteNo')
    expect(source).toContain('loadDocumentGenerationHistory')
    expect(source).toContain('downloadDocumentGeneration')
    expect(source).not.toContain('retryDocumentGeneration')
    expect(source).toContain("previewDocument('PURCHASE_ORDER'")
    expect(source).toContain('previewPurchaseOrderDocument')
    expect(source).toContain('printPurchaseOrderDocument')
    expect(source).toContain('预览草稿水印订单')
    expect(source).toContain('打印正式 PDF')
    expect(source).toContain('acceptedQuantity')
    expect(source).not.toContain('unqualifiedQuantity')
    expect(source).not.toContain('qualifiedQuantity')
    expect(source).toContain('await loadPage()')
    expect(source).toContain('await selectRecord(refreshed)')
    expect(source).toContain('loadOrderItemsForReceipt')
    expect(source).toContain('loadMaterials')
    expect(source).toContain('loadPurchaseRequestFormOptions')
    expect(source).toContain("hasPermission('purchase:request:self')")
    expect(source).toContain('v-if="!purchaseRequestSelfOnly"')
    expect(source).toContain('loadPartners')
    expect(source).toContain('loadContractPage')
    expect(source).toContain('loadContractItems')
    expect(source).toContain("contractStatus: 'PERFORMING'")
    expect(source).toContain(
      'form.partnerId = contracts.value.find((item) => item.id === value)?.partyBId',
    )
    expect(source).toContain('contractItems.value.map((item) => item.materialId)')
    expect(source).toContain("['PERFORMING', 'PARTIAL_RECEIVED'].includes(item.orderStatus || '')")
    expect(source).toContain('item.id === form.orderId')
    expect(source).not.toContain('changeOrderEditContract')
    expect(source).toContain('loadBudgetPage')
    expect(source).toContain('budgetLineId: requiredDraft')
    expect(source).not.toContain('estimatedUnitPrice')
    expect(source).toContain('previewDocument')
    expect(source).not.toContain('retryPurchaseRequestDocument')
    expect(source).toContain('loadWarehouses')
    expect(source).toContain('uploadSiteFile')
    expect(source).toContain('listSiteFiles')
    expect(source).toContain('getSiteFileUrl')
    expect(source).toContain('businessAttachments.length')
    expect(source).toContain('附件列表已更新')
    expect(source).not.toContain('生成并上传单据说明')
    expect(source).toContain('requestItemDrafts')
    expect(source).toContain('MaterialSearchPicker')
    expect(source).toContain('@select="addRequestMaterial"')
    expect(source).toContain('materialId: material.id')
    expect(source).toContain("unit: material.unit || ''")
    expect(source).toContain('orderItemDrafts')
    expect(source).toContain('useLocation: requiredDraft')
    expect(source).toContain('currentDocument')
    expect(source).toContain('businessAttachments')
    expect(source).toContain('新建例外采购订单')
    expect(source).toContain('新建采购订单')
    expect(source).toContain('orderCreateMode')
    expect(source).toContain('requestCandidates')
    expect(source).toContain('changeOrderRequest')
    expect(source).toContain('createPurchaseOrderFromRequest')
    expect(source).toContain("status: 'APPROVED'")
    expect(source).toContain('明细来自已审批采购申请，只读展示')
    expect(source).toContain('由服务端合同事实决定')
    expect(source).toContain('updatePurchaseOrder')
    expect(source).toContain('orderItemEdits.value.map')
    expect(source).toContain('来源行不可修改')
    expect(source).toContain("contractId: requiredOrderEdit('contractId', '采购合同')")
    expect(source).toContain('v-model="orderEditForm.contractId"')
    expect(source).toContain('@update:model-value="changeEditorProject"')
    expect(source).not.toContain('convertPurchaseRequest')
    expect(source).toContain('v-model="form.requestId"')
    expect(source).toContain('编辑商业条件')
    expect(source).toContain('登记供应商退货')
    expect(source).toContain('库存、订单与合同净应付已重新读取')
    expect(source).not.toContain('不合格供应商退货')
    expect(source).toContain('confirmReceiptSupplierReturn')
    expect(source).toContain('remainingQuantity')
    expect(source).toContain("receiptItem.systemBatchNo || selected.value?.systemBatchNo || '-'")
    expect(source).toContain("form.receiptMode === 'DIRECT_CONSUMPTION'")
    expect(source).toContain('v-model="form.useLocation"')
    expect(source).toContain('label="使用部位"')
    expect(source).toContain("'orderCode' in record")
    expect(source).toContain("CONVERTED: '已转订单'")
    expect(source).toContain("PARTIAL: '部分合格'")
    expect(source).toContain(
      "if ('receiptCode' in record) return statusLabel(record.approvalStatus)",
    )
    expect(source).toContain("selected.value?.approvalStatus === 'DRAFT'")
    expect(source).not.toMatch(
      /frontend-admin\/src|Legacy|totalAmount\s*[+]=|receivedQuantity\s*[+]=|label="[^"]*ID/,
    )
  })

  it('loads project-scoped purchase form options without amount fields', async () => {
    const signal = new AbortController().signal
    fetchMock.mockResolvedValueOnce(
      response({
        materials: [{ id: 'M1', materialCode: 'MAT-1', materialName: '钢筋', unit: '吨' }],
      }),
    )

    await expect(loadPurchaseRequestFormOptions('P/1', signal)).resolves.toEqual({
      materials: [{ id: 'M1', materialCode: 'MAT-1', materialName: '钢筋', unit: '吨' }],
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/purchase-requests/form-options?projectId=P%2F1',
      expect.objectContaining({ method: 'GET', headers: expect.any(Headers), signal }),
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
    const requestId = await createPurchaseRequest({
      header: { projectId: 'P1' },
      items: [{ materialId: 'M1', quantity: '1', useLocation: 'A区' }],
    })
    await savePurchaseRequestItems(requestId, [
      {
        requestId,
        materialId: 'M1',
        quantity: '9007199254740993.1234',
      },
    ])
    await submitPurchaseRequest('R/1')
    await deletePurchaseRequest('R/1')

    await loadPurchaseOrders({ projectId: 'P/1' }, signal)
    await loadPurchaseOrder('O/1', signal)
    await loadPurchaseOrderItems('O/1', signal)
    await loadPurchaseOrderPricingSuggestion('C/1', 'M/1')
    const fromRequestOrderId = await createPurchaseOrderFromRequest({
      projectId: 'P1',
      requestId: 'R1',
      contractId: 'C1',
      deliveryTerms: '送达项目仓库并完成联合验收',
    })
    const orderId = await createPurchaseOrder({
      projectId: 'P1',
      contractId: 'C1',
      partnerId: 'S1',
      exceptionPurchaseFlag: 1,
      exceptionReason: '紧急补采',
    })
    await updatePurchaseOrder('O/1', {
      projectId: 'P1',
      orderCode: 'PO-001',
      partnerId: 'S1',
      orderDate: '2026-07-01',
      deliveryDate: '2026-07-10',
      deliveryTerms: '送达项目仓库并完成联合验收',
    })
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
        acceptedQuantity: '1.0000',
      },
    ])
    await submitReceipt('RC/1')
    await confirmReceiptSupplierReturn({
      receiptItemId: 'RI/1',
      returnKind: 'ACCEPTED',
      quantity: '0.2500',
      returnDate: '2026-07-01',
      reason: '不合格品退回供应商',
      idempotencyKey: 'SRT-RI-1',
    })
    await deleteReceipt('RC/1')

    expect(requestId).toBe('9007199254740993')
    expect(orderId).toBe('9007199254740993')
    expect(fromRequestOrderId).toBe('9007199254740993')
    expect(receiptId).toBe('9007199254740993')
    const calls = fetchMock.mock.calls
    expect(calls.map(([url]) => String(url))).toContain('/api/purchase-requests/R%2F1/items')
    expect(calls.map(([url]) => String(url))).toContain('/api/purchase-requests/with-items')
    expect(calls.map(([url]) => String(url))).toContain('/api/purchase-orders/O%2F1/submit')
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/purchase-orders/pricing-suggestion?contractId=C%2F1&materialId=M%2F1',
    )
    const fromRequestCall = calls.find(([url]) =>
      String(url).endsWith('/purchase-orders/from-request'),
    )
    expect(fromRequestCall).toBeDefined()
    expect(JSON.parse(String(fromRequestCall?.[1]?.body))).toEqual({
      projectId: 'P1',
      requestId: 'R1',
      contractId: 'C1',
      deliveryTerms: '送达项目仓库并完成联合验收',
    })
    expect(
      calls.find(
        ([url, init]) => String(url).endsWith('/purchase-orders/O%2F1') && init?.method === 'PUT',
      ),
    ).toBeDefined()
    expect(calls.map(([url]) => String(url))).toContain('/api/receipts/RC%2F1/items')
    expect(calls.map(([url]) => String(url))).toContain('/api/supplier-returns')
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
    const receiptItems = calls.find(([url]) =>
      String(url).endsWith('/receipts/9007199254740993/items/batch'),
    )
    expect(JSON.parse(String(receiptItems?.[1]?.body))).toEqual([
      { orderItemId: 'OI1', acceptedQuantity: '1.0000' },
    ])
    const supplierReturn = calls.find(([url]) => String(url).endsWith('/supplier-returns'))
    expect(JSON.parse(String(supplierReturn?.[1]?.body))).toMatchObject({
      receiptItemId: 'RI/1',
      returnKind: 'ACCEPTED',
      quantity: '0.2500',
      idempotencyKey: 'SRT-RI-1',
    })
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
          acceptedQuantity: '2',
        },
      ]),
    ).rejects.toMatchObject({
      status: 409,
      code: 'RECEIPT_QUANTITY_EXCEEDED',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
