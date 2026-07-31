import { request } from '@/api/request'
import type { ContractVO } from '@/types/contract'
import type { PurchaseRequestVO } from '@/types/inventory'
import type { MatPurchaseOrderVO } from '@/types/purchase'

export interface SourcingEvent {
  id: string
  projectId: string
  purchaseRequestId: string
  sourcingCode: string
  sourcingTitle: string
  sourcingType: 'INQUIRY' | 'TENDER'
  deadline: string
  currencyCode: string
  status: 'DRAFT' | 'PUBLISHED' | 'EVALUATING' | 'AWARDED' | 'CONTRACTED' | 'CANCELLED'
  awardedQuoteId?: string
  awardedPartnerId?: string
  contractId?: string
  awardReason?: string
}

export interface SourcingSupplier {
  id: string
  sourcingEventId: string
  partnerId: string
  invitationStatus: 'PENDING' | 'INVITED' | 'DECLINED' | 'QUOTED' | 'DISQUALIFIED'
  disqualificationReason?: string
}

export interface SupplierQuote {
  id: string
  sourcingEventId: string
  sourcingSupplierId: string
  partnerId: string
  quoteCode: string
  totalAmount: number
  taxRate: number
  deliveryDays: number
  validityDate: string
  commercialTerms: string
  status: 'DRAFT' | 'SUBMITTED' | 'WINNER' | 'LOST' | 'INVALID'
}

export interface BidEvaluation {
  id: string
  sourcingEventId: string
  quoteId: string
  partnerId: string
  commercialScore: number
  technicalScore: number
  deliveryScore: number
  qualityScore: number
  totalScore: number
  evaluationComment: string
}

export interface SupplierPerformanceEvaluation {
  id: string
  projectId: string
  partnerId: string
  contractId: string
  purchaseOrderId: string
  evaluationCode: string
  periodStart: string
  periodEnd: string
  deliveryScore: number
  qualityScore: number
  serviceScore: number
  commercialScore: number
  totalScore: number
  grade: 'A' | 'B' | 'C' | 'D' | 'E'
  onTimeFlag: number
  approvedReceiptCount: number
  unqualifiedReceiptCount: number
  returnCount: number
  finalizedSettlementCount: number
  qualitySafetyFactCount: number
  qualitySafetyAverage?: number
  evaluationComment: string
  recommendBlacklist: number
  status: 'DRAFT' | 'CONFIRMED'
}

export interface SupplierReturn {
  id: string
  projectId: string
  partnerId: string
  contractId: string
  purchaseOrderId: string
  receiptId: string
  returnCode: string
  returnDate: string
  returnQuantity: number
  returnAmount: number
  reason: string
  status: 'DRAFT' | 'CONFIRMED'
}

export interface SupplierBlacklistRecord {
  id: string
  performanceEvaluationId: string
  partnerId: string
  projectId: string
  actionType: 'ADD'
  reason: string
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'
  submittedBy?: string
  reviewedBy?: string
  reviewComment?: string
}

export interface SourcingTrace {
  event: SourcingEvent
  purchaseRequest: PurchaseRequestVO
  invitedSuppliers: SourcingSupplier[]
  quotes: SupplierQuote[]
  bidEvaluations: BidEvaluation[]
  contract?: ContractVO
  purchaseOrders: MatPurchaseOrderVO[]
  receipts: Array<{
    id: string
    receiptCode: string
    receiptDate?: string
    orderId?: string
    contractId?: string
    partnerId?: string
    qualityStatus?: string
    approvalStatus?: string
  }>
  supplierReturns: SupplierReturn[]
  settlements: Array<{ id: string; settlementCode: string; settlementStatus?: string }>
  performanceEvaluations: SupplierPerformanceEvaluation[]
  blacklistRecords: SupplierBlacklistRecord[]
  qualitySafetyFacts: Array<{ id: string; evaluationType: string; score: number }>
}

export interface EventCommand {
  projectId: string
  purchaseRequestId: string
  sourcingCode: string
  sourcingTitle: string
  sourcingType: 'INQUIRY' | 'TENDER'
  deadline: string
  currencyCode: string
  remark?: string
}

export interface QuoteCommand {
  sourcingEventId: string
  partnerId: string
  quoteCode: string
  totalAmount: number
  taxRate: number
  deliveryDays: number
  validityDate: string
  commercialTerms: string
  remark?: string
}

export interface EvaluationCommand {
  quoteId: string
  commercialScore: number
  technicalScore: number
  deliveryScore: number
  qualityScore: number
  evaluationComment: string
}

export const getSourcingEvents = (projectId: string) =>
  request<SourcingEvent[]>({
    url: '/supplier-sourcing/events',
    method: 'get',
    params: { projectId },
  })
export const createSourcingEvent = (data: EventCommand) =>
  request<SourcingEvent>({ url: '/supplier-sourcing/events', method: 'post', data })
export const addSourcingSuppliers = (id: string, partnerIds: string[]) =>
  request<SourcingSupplier[]>({
    url: `/supplier-sourcing/events/${id}/suppliers`,
    method: 'post',
    data: { partnerIds },
  })
export const getSourcingSuppliers = (id: string) =>
  request<SourcingSupplier[]>({ url: `/supplier-sourcing/events/${id}/suppliers`, method: 'get' })
export const publishSourcingEvent = (id: string) =>
  request<SourcingEvent>({ url: `/supplier-sourcing/events/${id}/publish`, method: 'post' })
export const declineSourcingSupplier = (eventId: string, partnerId: string, reason: string) =>
  request<SourcingSupplier>({
    url: `/supplier-sourcing/events/${eventId}/suppliers/${partnerId}/decline`,
    method: 'post',
    data: { reason },
  })
export const getSupplierQuotes = (id: string) =>
  request<SupplierQuote[]>({ url: `/supplier-sourcing/events/${id}/quotes`, method: 'get' })
export const createSupplierQuote = (data: QuoteCommand) =>
  request<SupplierQuote>({ url: '/supplier-sourcing/quotes', method: 'post', data })
export const submitSupplierQuote = (id: string) =>
  request<SupplierQuote>({ url: `/supplier-sourcing/quotes/${id}/submit`, method: 'post' })
export const startBidEvaluation = (id: string) =>
  request<SourcingEvent>({
    url: `/supplier-sourcing/events/${id}/start-evaluation`,
    method: 'post',
  })
export const createBidEvaluation = (data: EvaluationCommand) =>
  request<BidEvaluation>({ url: '/supplier-sourcing/evaluations', method: 'post', data })
export const getBidEvaluations = (id: string) =>
  request<BidEvaluation[]>({ url: `/supplier-sourcing/events/${id}/evaluations`, method: 'get' })
export const awardSourcingEvent = (id: string, quoteId: string, awardReason: string) =>
  request<SourcingEvent>({
    url: `/supplier-sourcing/events/${id}/award`,
    method: 'post',
    data: { quoteId, awardReason },
  })
export const linkSourcingContract = (id: string, contractId: string) =>
  request<SourcingEvent>({
    url: `/supplier-sourcing/events/${id}/link-contract`,
    method: 'post',
    data: { contractId },
  })
export const getSupplierPerformance = (projectId: string) =>
  request<SupplierPerformanceEvaluation[]>({
    url: '/supplier-sourcing/performance',
    method: 'get',
    params: { projectId },
  })
export const createSupplierPerformance = (
  purchaseOrderId: string,
  serviceScore: number,
  evaluationComment: string,
) =>
  request<SupplierPerformanceEvaluation>({
    url: '/supplier-sourcing/performance',
    method: 'post',
    data: { purchaseOrderId, serviceScore, evaluationComment },
  })
export const confirmSupplierPerformance = (id: string) =>
  request<SupplierPerformanceEvaluation>({
    url: `/supplier-sourcing/performance/${id}/confirm`,
    method: 'post',
  })
export const getSupplierReturns = (projectId: string) =>
  request<SupplierReturn[]>({
    url: '/supplier-sourcing/returns',
    method: 'get',
    params: { projectId },
  })
export const createSupplierReturn = (data: {
  receiptId: string
  returnCode: string
  returnDate: string
  returnQuantity: number
  returnAmount: number
  reason: string
}) => request<SupplierReturn>({ url: '/supplier-sourcing/returns', method: 'post', data })
export const confirmSupplierReturn = (id: string) =>
  request<SupplierReturn>({ url: `/supplier-sourcing/returns/${id}/confirm`, method: 'post' })
export const createSupplierBlacklist = (performanceEvaluationId: string, reason: string) =>
  request<SupplierBlacklistRecord>({
    url: '/supplier-sourcing/blacklists',
    method: 'post',
    data: { performanceEvaluationId, reason },
  })
export const submitSupplierBlacklist = (id: string) =>
  request<SupplierBlacklistRecord>({
    url: `/supplier-sourcing/blacklists/${id}/submit`,
    method: 'post',
  })
export const reviewSupplierBlacklist = (
  id: string,
  decision: 'APPROVE' | 'REJECT',
  comment: string,
) =>
  request<SupplierBlacklistRecord>({
    url: `/supplier-sourcing/blacklists/${id}/review`,
    method: 'post',
    data: { decision, comment },
  })
export const getSourcingTrace = (id: string) =>
  request<SourcingTrace>({ url: `/supplier-sourcing/events/${id}/trace`, method: 'get' })
