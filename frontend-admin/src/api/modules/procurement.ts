import { request } from '@/api/request'

export interface SupplierReturnRequest {
  receiptItemId: string
  returnKind: 'UNQUALIFIED' | 'ACCEPTED'
  quantity: string
  returnDate: string
  reason: string
  idempotencyKey: string
}

export interface MaterialReturnRequest {
  requisitionItemId: string
  originalStockTxnId: string
  quantity: string
  returnDate: string
  reason: string
  idempotencyKey: string
}

export interface ProcurementTrace {
  project?: Record<string, unknown>
  contract?: Record<string, unknown>
  purchaseRequest?: Record<string, unknown>
  purchaseOrder?: Record<string, unknown>
  receipt?: Record<string, unknown>
  requisition?: Record<string, unknown>
  materialReturn?: Record<string, unknown>
  supplierReturn?: Record<string, unknown>
  stockTransactions?: Array<Record<string, unknown>>
  costs?: Array<Record<string, unknown>>
  approvalInstances?: Array<Record<string, unknown>>
  approvalRecords?: Array<Record<string, unknown>>
}

export function createSupplierReturn(data: SupplierReturnRequest) {
  return request<string>({ url: '/supplier-returns', method: 'post', data })
}

export function confirmSupplierReturn(id: string) {
  return request<void>({ url: `/supplier-returns/${id}/confirm`, method: 'post' })
}

export function confirmMaterialReturn(data: MaterialReturnRequest) {
  return request<string>({ url: '/material-returns/confirm', method: 'post', data })
}

export function getProcurementTrace(
  kind: 'receipts' | 'requisitions' | 'supplier-returns' | 'material-returns',
  id: string,
) {
  return request<ProcurementTrace>({ url: `/procurement-traces/${kind}/${id}`, method: 'get' })
}
