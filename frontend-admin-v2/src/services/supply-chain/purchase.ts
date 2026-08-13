import {
  SUPPLY_CHAIN_API,
  type PurchaseOrderCommand,
  type PurchaseOrderFromRequestCommand,
  type PurchaseOrderItemRecord,
  type PurchaseOrderPage,
  type PurchaseOrderPricingSuggestionRecord,
  type PurchaseOrderQuery,
  type PurchaseOrderRecord,
  type PurchaseRequestCommand,
  type PurchaseRequestItemRecord,
  type PurchaseRequestPage,
  type PurchaseRequestQuery,
  type PurchaseRequestRecord,
  type ReceiptCommand,
  type ReceiptItemRecord,
  type ReceiptPage,
  type ReceiptQuery,
  type ReceiptRecord,
  type ReceiptSupplierReturnCommand,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import {
  createId,
  deleteResource,
  encodedId,
  post,
  POST_METHOD,
  PUT_METHOD,
  resourcePath,
  saveItems,
  withQuery,
} from './support'
import type { PurchaseRequestApprovalCommand, PurchaseRequestFormOptions } from './types'

export function loadPurchaseOrders(
  query: PurchaseOrderQuery = {},
  signal?: AbortSignal,
): Promise<PurchaseOrderPage> {
  return apiRequest<PurchaseOrderPage>(withQuery(SUPPLY_CHAIN_API.purchaseOrders, query), {
    signal,
  })
}

export function loadPurchaseRequests(
  query: PurchaseRequestQuery = {},
  signal?: AbortSignal,
): Promise<PurchaseRequestPage> {
  return apiRequest<PurchaseRequestPage>(withQuery(SUPPLY_CHAIN_API.purchaseRequests, query), {
    signal,
  })
}

export function loadPurchaseRequestFormOptions(
  projectId: string,
  signal?: AbortSignal,
): Promise<PurchaseRequestFormOptions> {
  return apiRequest<PurchaseRequestFormOptions>(
    withQuery(`${SUPPLY_CHAIN_API.purchaseRequests}/form-options`, { projectId }),
    { signal, notifyError: false },
  )
}

export function loadPurchaseRequest(id: string, signal?: AbortSignal) {
  return apiRequest<PurchaseRequestRecord>(resourcePath(SUPPLY_CHAIN_API.purchaseRequests, id), {
    signal,
    notifyError: false,
  })
}

export function loadPurchaseRequestItems(id: string, signal?: AbortSignal) {
  return apiRequest<PurchaseRequestItemRecord[]>(
    `${resourcePath(SUPPLY_CHAIN_API.purchaseRequests, id)}/items`,
    { signal, notifyError: false },
  )
}

export function createPurchaseRequest(body: PurchaseRequestCommand): Promise<string> {
  return createId(`${SUPPLY_CHAIN_API.purchaseRequests}/with-items`, body)
}

export function deletePurchaseRequest(id: string) {
  return deleteResource(SUPPLY_CHAIN_API.purchaseRequests, id)
}

export function savePurchaseRequestItems(id: string, items: PurchaseRequestItemRecord[]) {
  return saveItems(SUPPLY_CHAIN_API.purchaseRequests, id, items)
}

export function submitPurchaseRequest(id: string) {
  return post<void>(`${resourcePath(SUPPLY_CHAIN_API.purchaseRequests, id)}/submit`)
}

export function approvePurchaseRequest(
  requestId: string,
  taskId: string,
  body: PurchaseRequestApprovalCommand,
) {
  return apiRequest<void, PurchaseRequestApprovalCommand>(
    `${resourcePath(SUPPLY_CHAIN_API.purchaseRequests, requestId)}/approval-tasks/${encodeURIComponent(taskId)}/approve`,
    { method: POST_METHOD, body },
  )
}

export function loadPurchaseRequestApprovalItems(
  requestId: string,
  taskId: string,
): Promise<PurchaseRequestItemRecord[]> {
  return apiRequest<PurchaseRequestItemRecord[]>(
    `${resourcePath(SUPPLY_CHAIN_API.purchaseRequests, requestId)}/approval-tasks/${encodeURIComponent(taskId)}/items`,
    { notifyError: false },
  )
}

export function loadPurchaseOrder(id: string, signal?: AbortSignal) {
  return apiRequest<PurchaseOrderRecord>(resourcePath(SUPPLY_CHAIN_API.purchaseOrders, id), {
    signal,
    notifyError: false,
  })
}

export function loadPurchaseOrderItems(id: string, signal?: AbortSignal) {
  return apiRequest<PurchaseOrderItemRecord[]>(
    `${resourcePath(SUPPLY_CHAIN_API.purchaseOrders, id)}/items`,
    { signal, notifyError: false },
  )
}

export function loadPurchaseOrderPricingSuggestion(
  contractId: string,
  materialId: string,
): Promise<PurchaseOrderPricingSuggestionRecord> {
  return apiRequest<PurchaseOrderPricingSuggestionRecord>(
    withQuery(`${SUPPLY_CHAIN_API.purchaseOrders}/pricing-suggestion`, { contractId, materialId }),
    { notifyError: false },
  )
}

export function createPurchaseOrder(body: PurchaseOrderCommand): Promise<string> {
  return createId(SUPPLY_CHAIN_API.purchaseOrders, body)
}

export function createPurchaseOrderFromRequest(
  body: PurchaseOrderFromRequestCommand,
): Promise<string> {
  return createId(`${SUPPLY_CHAIN_API.purchaseOrders}/from-request`, body)
}

export function updatePurchaseOrder(
  id: string,
  body: PurchaseOrderCommand & { orderCode: string },
): Promise<void> {
  return apiRequest<void, PurchaseOrderCommand & { orderCode: string }>(
    resourcePath(SUPPLY_CHAIN_API.purchaseOrders, id),
    { method: PUT_METHOD, body },
  )
}

export function deletePurchaseOrder(id: string) {
  return deleteResource(SUPPLY_CHAIN_API.purchaseOrders, id)
}

export function savePurchaseOrderItems(id: string, items: PurchaseOrderItemRecord[]) {
  return saveItems(SUPPLY_CHAIN_API.purchaseOrders, id, items)
}

export function submitPurchaseOrder(id: string) {
  return post<void>(`${resourcePath(SUPPLY_CHAIN_API.purchaseOrders, id)}/submit`)
}

export function loadReceipts(query: ReceiptQuery = {}, signal?: AbortSignal): Promise<ReceiptPage> {
  return apiRequest<ReceiptPage>(withQuery(SUPPLY_CHAIN_API.receipts, query), { signal })
}

export function loadReceipt(id: string, signal?: AbortSignal) {
  return apiRequest<ReceiptRecord>(resourcePath(SUPPLY_CHAIN_API.receipts, id), {
    signal,
    notifyError: false,
  })
}

export function loadReceiptItems(id: string, signal?: AbortSignal) {
  return apiRequest<ReceiptItemRecord[]>(`${resourcePath(SUPPLY_CHAIN_API.receipts, id)}/items`, {
    signal,
    notifyError: false,
  })
}

export function loadOrderItemsForReceipt(id: string, signal?: AbortSignal) {
  return apiRequest<ReceiptItemRecord[]>(
    `${SUPPLY_CHAIN_API.receipts}/orders/${encodedId(id, '订单ID')}/items`,
    { signal },
  )
}

export function createReceipt(body: ReceiptCommand): Promise<string> {
  return createId(SUPPLY_CHAIN_API.receipts, body)
}

export function confirmReceiptSupplierReturn(body: ReceiptSupplierReturnCommand): Promise<string> {
  return createId(SUPPLY_CHAIN_API.receiptSupplierReturns, body)
}

export function updateReceipt(id: string, body: ReceiptCommand) {
  return apiRequest<void, ReceiptCommand>(resourcePath(SUPPLY_CHAIN_API.receipts, id), {
    method: PUT_METHOD,
    body,
  })
}

export function deleteReceipt(id: string) {
  return deleteResource(SUPPLY_CHAIN_API.receipts, id)
}

export function saveReceiptItems(id: string, items: ReceiptItemRecord[]) {
  return saveItems(
    SUPPLY_CHAIN_API.receipts,
    id,
    items.map((item) => ({
      orderItemId: item.orderItemId,
      acceptedQuantity: item.acceptedQuantity,
      useLocation: item.useLocation,
    })),
  )
}

export function submitReceipt(id: string) {
  return post<void>(`${resourcePath(SUPPLY_CHAIN_API.receipts, id)}/submit`)
}
