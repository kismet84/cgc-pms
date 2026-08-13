import {
  SUPPLY_CHAIN_API,
  type BidEvaluationCommand,
  type BidEvaluationRecord,
  type SourcingEventCommand,
  type SourcingEventRecord,
  type SourcingSupplierRecord,
  type SourcingTraceRecord,
  type SupplierBlacklistRecord,
  type SupplierPerformanceCandidatePage,
  type SupplierPerformanceRecord,
  type SupplierQuoteCommand,
  type SupplierQuoteRecord,
  type SupplierReturnRecord,
  type SupplierSourcingWorkspacePage,
  type SupplierSourcingWorkspaceQuery,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { encodedId, eventPath, post, POST_METHOD, requiredId, withQuery } from './support'

export const loadSourcingEvents = (projectId: string, signal?: AbortSignal) =>
  apiRequest<SourcingEventRecord[]>(
    withQuery(SUPPLY_CHAIN_API.supplierSourcingEvents, {
      projectId: requiredId(projectId, '项目ID'),
    }),
    { signal },
  )

export const loadSupplierSourcingWorkspace = (
  query: SupplierSourcingWorkspaceQuery = {},
  signal?: AbortSignal,
) =>
  apiRequest<SupplierSourcingWorkspacePage>(
    withQuery(SUPPLY_CHAIN_API.supplierSourcingWorkspace, query),
    { signal },
  )

export const loadSupplierPerformanceCandidates = (
  query: { pageNo?: number; pageSize?: number; projectId?: string } = {},
  signal?: AbortSignal,
) =>
  apiRequest<SupplierPerformanceCandidatePage>(
    withQuery(SUPPLY_CHAIN_API.supplierPerformanceCandidates, query),
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
