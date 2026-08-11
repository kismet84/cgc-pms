import {
  SUPPLY_CHAIN_API,
  type BidEvaluationCommand,
  type BidEvaluationRecord,
  type PurchaseOrderCommand,
  type PurchaseOrderFromRequestCommand,
  type PurchaseOrderItemRecord,
  type PurchaseOrderPage,
  type PurchaseOrderPricingSuggestionRecord,
  type PurchaseOrderQuery,
  type PurchaseRequestCommand,
  type PurchaseRequestItemRecord,
  type PurchaseRequestPage,
  type PurchaseRequestQuery,
  type PurchaseRequestRecord,
  type PurchaseOrderRecord,
  type ReceiptCommand,
  type ReceiptItemRecord,
  type ReceiptPage,
  type ReceiptQuery,
  type ReceiptRecord,
  type ReceiptSupplierReturnCommand,
  type RequisitionCommand,
  type RequisitionItemRecord,
  type RequisitionPage,
  type RequisitionQuery,
  type RequisitionRecord,
  type RequisitionTraceRecord,
  type MaterialReturnCommand,
  type MaterialReturnItemRecord,
  type MaterialReturnRecord,
  type MaterialPage,
  type MaterialQuery,
  type StockLedger,
  type StockLedgerQuery,
  type StockPage,
  type StockQuery,
  type StockRecord,
  type StockKpiRecord,
  type StockTransferCandidateRecord,
  type StockIncomingSupplyRecord,
  type StockConsumptionBaselineRecord,
  type StockTransferCommand,
  type StockTransferRecord,
  type StockReplenishmentCommand,
  type SourcingEventCommand,
  type SourcingEventRecord,
  type SourcingSupplierRecord,
  type SourcingTraceRecord,
  type SupplierBlacklistRecord,
  type SupplierPerformanceRecord,
  type SupplierQuoteCommand,
  type SupplierQuoteRecord,
  type SupplierReturnRecord,
  type WarehousePage,
  type WarehouseCommand,
  type WarehouseRecord,
  type WarehouseQuery,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

const POST_METHOD = 'POST'
const PUT_METHOD = 'PUT'
const DELETE_METHOD = 'DELETE'

export interface SupplyFormMaterialOption {
  id: string
  materialCode: string
  materialName: string
  unit?: string | null
}

export interface PurchaseRequestFormOptions {
  materials: SupplyFormMaterialOption[]
}

export interface RequisitionFormOptions {
  warehouses: Array<{
    id: string
    warehouseCode: string
    warehouseName: string
    projectId: string
  }>
  materials: SupplyFormMaterialOption[]
  partners: Array<{ id: string; partnerCode: string; partnerName: string }>
  contracts: Array<{ id: string; contractCode: string; contractName: string; projectId: string }>
}

export function loadWarehouses(
  query: WarehouseQuery = {},
  signal?: AbortSignal,
): Promise<WarehousePage> {
  return apiRequest<WarehousePage>(withQuery(SUPPLY_CHAIN_API.warehouses, query), { signal })
}

export function loadMaterials(
  query: MaterialQuery = {},
  signal?: AbortSignal,
): Promise<MaterialPage> {
  return apiRequest<MaterialPage>(withQuery(SUPPLY_CHAIN_API.materials, query), { signal })
}

export function loadWarehouse(id: string, signal?: AbortSignal) {
  return apiRequest<WarehouseRecord>(resourcePath(SUPPLY_CHAIN_API.warehouses, id), { signal })
}

export function createWarehouse(body: WarehouseCommand): Promise<string> {
  return createId(SUPPLY_CHAIN_API.warehouses, body)
}

export function updateWarehouse(id: string, body: WarehouseCommand): Promise<void> {
  return apiRequest<void, WarehouseCommand>(resourcePath(SUPPLY_CHAIN_API.warehouses, id), {
    method: PUT_METHOD,
    body,
  })
}

export function updateWarehouseStatus(id: string, status: 'ENABLE' | 'DISABLE'): Promise<void> {
  return apiRequest<void>(
    withQuery(`${resourcePath(SUPPLY_CHAIN_API.warehouses, id)}/status`, { status }),
    { method: PUT_METHOD },
  )
}

export function deleteWarehouse(id: string): Promise<void> {
  return deleteResource(SUPPLY_CHAIN_API.warehouses, id)
}

export function loadStockLedger(
  query: StockLedgerQuery,
  signal?: AbortSignal,
): Promise<StockLedger> {
  const normalized = {
    ...query,
    materialId: requiredId(query.materialId, '物料ID'),
  }
  return apiRequest<StockLedger>(withQuery(SUPPLY_CHAIN_API.stockLedger, normalized), { signal })
}

export function loadStocks(query: StockQuery = {}, signal?: AbortSignal): Promise<StockPage> {
  return apiRequest<StockPage>(withQuery(SUPPLY_CHAIN_API.stocks, query), { signal })
}

export function loadStockKpi(
  query: { warehouseId?: string; projectId?: string } = {},
  signal?: AbortSignal,
): Promise<StockKpiRecord> {
  return apiRequest<StockKpiRecord>(withQuery(SUPPLY_CHAIN_API.stockKpi, query), { signal })
}

export function loadStockTransferCandidates(id: string, signal?: AbortSignal) {
  return apiRequest<StockTransferCandidateRecord[]>(
    `${resourcePath('/inventory/stock', id)}/transfer-candidates`,
    { signal },
  )
}

export function loadStockIncomingSupplies(id: string, signal?: AbortSignal) {
  return apiRequest<StockIncomingSupplyRecord[]>(
    `${resourcePath('/inventory/stock', id)}/incoming-supplies`,
    { signal },
  )
}

export function loadStockConsumptionBaseline(id: string, signal?: AbortSignal) {
  return apiRequest<StockConsumptionBaselineRecord>(
    `${resourcePath('/inventory/stock', id)}/consumption-baseline`,
    { signal },
  )
}

export function createStockTransfer(body: StockTransferCommand) {
  return apiRequest<StockTransferRecord, StockTransferCommand>('/inventory/stock/transfers', {
    method: POST_METHOD,
    body,
  })
}

export function updateStockReplenishment(id: string, body: StockReplenishmentCommand) {
  return apiRequest<StockRecord, StockReplenishmentCommand>(
    `${resourcePath('/inventory/stock', id)}/replenishment-settings`,
    { method: PUT_METHOD, body },
  )
}

export function loadPurchaseOrders(
  query: PurchaseOrderQuery = {},
  signal?: AbortSignal,
): Promise<PurchaseOrderPage> {
  return apiRequest<PurchaseOrderPage>(withQuery(SUPPLY_CHAIN_API.purchaseOrders, query), {
    signal,
  })
}

export function loadRequisitions(
  query: RequisitionQuery = {},
  signal?: AbortSignal,
): Promise<RequisitionPage> {
  return apiRequest<RequisitionPage>(withQuery(SUPPLY_CHAIN_API.requisitions, query), { signal })
}

export function loadRequisitionFormOptions(
  projectId: string,
  signal?: AbortSignal,
): Promise<RequisitionFormOptions> {
  return apiRequest<RequisitionFormOptions>(
    withQuery(`${SUPPLY_CHAIN_API.requisitions}/form-options`, { projectId }),
    { signal, notifyError: false },
  )
}

export function loadRequisition(id: string, signal?: AbortSignal) {
  return apiRequest<RequisitionRecord>(resourcePath(SUPPLY_CHAIN_API.requisitions, id), {
    signal,
    notifyError: false,
  })
}

export function loadRequisitionItems(id: string, signal?: AbortSignal) {
  return apiRequest<RequisitionItemRecord[]>(
    `${resourcePath(SUPPLY_CHAIN_API.requisitions, id)}/items`,
    { signal, notifyError: false },
  )
}

export function createRequisition(body: RequisitionCommand): Promise<string> {
  return createId(SUPPLY_CHAIN_API.requisitions, body)
}

export function updateRequisition(id: string, body: RequisitionCommand): Promise<void> {
  return apiRequest<void, RequisitionCommand>(resourcePath(SUPPLY_CHAIN_API.requisitions, id), {
    method: PUT_METHOD,
    body,
  })
}

export function deleteRequisition(id: string): Promise<void> {
  return deleteResource(SUPPLY_CHAIN_API.requisitions, id)
}

export function saveRequisitionItems(id: string, items: RequisitionItemRecord[]): Promise<void> {
  return saveItems(SUPPLY_CHAIN_API.requisitions, id, items)
}

export function submitRequisition(id: string): Promise<void> {
  return post<void>(`${resourcePath(SUPPLY_CHAIN_API.requisitions, id)}/submit`)
}

export function stockOutRequisition(id: string): Promise<void> {
  return post<void>(`${resourcePath(SUPPLY_CHAIN_API.requisitions, id)}/stock-out`)
}

export function loadRequisitionTrace(id: string, signal?: AbortSignal) {
  return apiRequest<RequisitionTraceRecord>(
    `/procurement-traces/requisitions/${encodedId(id, '领料单ID')}`,
    { signal, notifyError: false },
  )
}

export function confirmMaterialReturn(body: MaterialReturnCommand): Promise<string> {
  return createId('/material-returns/confirm', body)
}

export function loadMaterialReturn(id: string, signal?: AbortSignal) {
  return apiRequest<MaterialReturnRecord>(resourcePath('/material-returns', id), {
    signal,
    notifyError: false,
  })
}

export function loadMaterialReturnItems(id: string, signal?: AbortSignal) {
  return apiRequest<MaterialReturnItemRecord[]>(`${resourcePath('/material-returns', id)}/items`, {
    signal,
    notifyError: false,
  })
}

export function reverseMaterialReturn(id: string, reason: string): Promise<string> {
  return createId(`${resourcePath('/material-returns', id)}/reverse`, { reason })
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

export interface PurchaseRequestApprovalCommand {
  comment?: string
  idempotencyKey: string
  items: Array<{
    itemId: string
    approvedQuantity: string
    approvalVersion: number
    changeReason?: string
  }>
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

export const loadSourcingEvents = (projectId: string, signal?: AbortSignal) =>
  apiRequest<SourcingEventRecord[]>(
    withQuery(SUPPLY_CHAIN_API.supplierSourcingEvents, {
      projectId: requiredId(projectId, '项目ID'),
    }),
    { signal },
  )

export const createSourcingEvent = (body: SourcingEventCommand) =>
  apiRequest<SourcingEventRecord, SourcingEventCommand>(SUPPLY_CHAIN_API.supplierSourcingEvents, {
    method: POST_METHOD,
    body,
  })

export const loadSourcingSuppliers = (eventId: string, signal?: AbortSignal) =>
  apiRequest<SourcingSupplierRecord[]>(eventPath(eventId, 'suppliers'), { signal })

export const inviteSourcingSuppliers = (eventId: string, partnerIds: string[]) =>
  apiRequest<SourcingSupplierRecord[], { partnerIds: string[] }>(eventPath(eventId, 'suppliers'), {
    method: POST_METHOD,
    body: { partnerIds },
  })

export const publishSourcingEvent = (eventId: string) =>
  post<SourcingEventRecord>(eventPath(eventId, 'publish'))

export const declineSourcingSupplier = (eventId: string, partnerId: string, reason: string) =>
  apiRequest<SourcingSupplierRecord, { reason: string }>(
    `${eventPath(eventId, 'suppliers')}/${encodedId(partnerId, '供应商ID')}/decline`,
    { method: POST_METHOD, body: { reason } },
  )

export const loadSupplierQuotes = (eventId: string, signal?: AbortSignal) =>
  apiRequest<SupplierQuoteRecord[]>(eventPath(eventId, 'quotes'), { signal })

export const createSupplierQuote = (body: SupplierQuoteCommand) =>
  apiRequest<SupplierQuoteRecord, SupplierQuoteCommand>(SUPPLY_CHAIN_API.supplierSourcingQuotes, {
    method: POST_METHOD,
    body,
  })

export const submitSupplierQuote = (quoteId: string) =>
  post<SupplierQuoteRecord>(
    `${SUPPLY_CHAIN_API.supplierSourcingQuotes}/${encodedId(quoteId, '报价ID')}/submit`,
  )

export const startSourcingEvaluation = (eventId: string) =>
  post<SourcingEventRecord>(eventPath(eventId, 'start-evaluation'))

export const createBidEvaluation = (body: BidEvaluationCommand) =>
  apiRequest<BidEvaluationRecord, BidEvaluationCommand>(
    SUPPLY_CHAIN_API.supplierSourcingEvaluations,
    { method: POST_METHOD, body },
  )

export const loadBidEvaluations = (eventId: string, signal?: AbortSignal) =>
  apiRequest<BidEvaluationRecord[]>(eventPath(eventId, 'evaluations'), { signal })

export const awardSourcingEvent = (eventId: string, quoteId: string, awardReason: string) =>
  apiRequest<SourcingEventRecord, { quoteId: string; awardReason: string }>(
    eventPath(eventId, 'award'),
    { method: POST_METHOD, body: { quoteId, awardReason } },
  )

export const linkSourcingContract = (eventId: string, contractId: string) =>
  apiRequest<SourcingEventRecord, { contractId: string }>(eventPath(eventId, 'link-contract'), {
    method: POST_METHOD,
    body: { contractId },
  })

export const loadSupplierPerformance = (projectId: string, signal?: AbortSignal) =>
  apiRequest<SupplierPerformanceRecord[]>(
    withQuery(SUPPLY_CHAIN_API.supplierPerformance, {
      projectId: requiredId(projectId, '项目ID'),
    }),
    { signal },
  )

export const createSupplierPerformance = (
  purchaseOrderId: string,
  serviceScore: string,
  evaluationComment: string,
) =>
  apiRequest<
    SupplierPerformanceRecord,
    { purchaseOrderId: string; serviceScore: string; evaluationComment: string }
  >(SUPPLY_CHAIN_API.supplierPerformance, {
    method: POST_METHOD,
    body: { purchaseOrderId, serviceScore, evaluationComment },
  })

export const confirmSupplierPerformance = (id: string) =>
  post<SupplierPerformanceRecord>(
    `${SUPPLY_CHAIN_API.supplierPerformance}/${encodedId(id, '履约评价ID')}/confirm`,
  )

export const loadSupplierReturns = (projectId: string, signal?: AbortSignal) =>
  apiRequest<SupplierReturnRecord[]>(
    withQuery(SUPPLY_CHAIN_API.supplierReturns, {
      projectId: requiredId(projectId, '项目ID'),
    }),
    { signal },
  )

export const createSupplierBlacklist = (performanceEvaluationId: string, reason: string) =>
  apiRequest<SupplierBlacklistRecord, { performanceEvaluationId: string; reason: string }>(
    SUPPLY_CHAIN_API.supplierBlacklists,
    { method: POST_METHOD, body: { performanceEvaluationId, reason } },
  )

export const submitSupplierBlacklist = (id: string) =>
  post<SupplierBlacklistRecord>(
    `${SUPPLY_CHAIN_API.supplierBlacklists}/${encodedId(id, '黑名单ID')}/submit`,
  )

export const reviewSupplierBlacklist = (
  id: string,
  decision: 'APPROVE' | 'REJECT',
  comment: string,
) =>
  apiRequest<SupplierBlacklistRecord, { decision: 'APPROVE' | 'REJECT'; comment: string }>(
    `${SUPPLY_CHAIN_API.supplierBlacklists}/${encodedId(id, '黑名单ID')}/review`,
    { method: POST_METHOD, body: { decision, comment } },
  )

export const loadSourcingTrace = (eventId: string, signal?: AbortSignal) =>
  apiRequest<SourcingTraceRecord>(eventPath(eventId, 'trace'), { signal })

function post<T>(path: string): Promise<T> {
  return apiRequest<T>(path, { method: POST_METHOD })
}

function createId<B>(path: string, body: B): Promise<string> {
  return apiRequest<string | number, B>(path, { method: POST_METHOD, body }).then(String)
}

function saveItems<B>(path: string, id: string, body: B): Promise<void> {
  return apiRequest<void, B>(`${resourcePath(path, id)}/items/batch`, {
    method: POST_METHOD,
    body,
  })
}

function deleteResource(path: string, id: string): Promise<void> {
  return apiRequest<void>(resourcePath(path, id), { method: DELETE_METHOD })
}

function resourcePath(path: string, id: string): string {
  return `${path}/${encodedId(id, '业务ID')}`
}

function eventPath(id: string, action: string): string {
  return `${SUPPLY_CHAIN_API.supplierSourcingEvents}/${encodedId(id, '招采事件ID')}/${action}`
}

function encodedId(value: string, label: string): string {
  return encodeURIComponent(requiredId(value, label))
}

function withQuery(path: string, query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number') {
      if (Number.isInteger(value) && value > 0) params.set(key, String(value))
    } else if (typeof value === 'string' && value.trim()) {
      params.set(key, value.trim())
    }
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}

function requiredId(value: string, label: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError(`${label}不能为空`)
  return normalized
}
