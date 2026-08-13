import {
  COMMERCIAL_API,
  type BidCostPage,
  type BidCostQuery,
  type BidCostRecord,
  type BidCostSaveCommand,
  type BidDocumentCreateCommand,
  type BidDocumentVersionRecord,
  type BidStatus,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId, withSearchParams, WRITE_METHOD } from './support'
import type { BidCostOption, BidOwnerOption, CostSubjectOption } from './types'

export function loadBidCostPage(
  query: BidCostQuery = {},
  signal?: AbortSignal,
): Promise<BidCostPage> {
  return apiRequest<BidCostPage>(withSearchParams(COMMERCIAL_API.bidCosts, query), { signal })
}

export function loadBidOwnerOptions(signal?: AbortSignal): Promise<BidOwnerOption[]> {
  return apiRequest<BidOwnerOption[]>('/bid-cost/owners', { signal })
}

export function loadBidCostOptions(signal?: AbortSignal): Promise<BidCostOption[]> {
  return apiRequest<BidCostOption[]>('/bid-cost/cost-options', { signal })
}

export function loadBidCostSubjectOptions(signal?: AbortSignal): Promise<CostSubjectOption[]> {
  return apiRequest<CostSubjectOption[]>('/cost-subjects/bid-options', { signal })
}

export function loadBidCost(id: string, signal?: AbortSignal): Promise<BidCostRecord> {
  return apiRequest<BidCostRecord>(COMMERCIAL_API.bidCost(requiredId(id, '投标成本ID')), { signal })
}

export function createBidCost(command: BidCostSaveCommand): Promise<string> {
  return apiRequest<string, BidCostSaveCommand>(COMMERCIAL_API.bidCosts, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function updateBidCost(id: string, command: BidCostSaveCommand): Promise<void> {
  return apiRequest<void, BidCostSaveCommand>(
    COMMERCIAL_API.bidCost(requiredId(id, '投标成本ID')),
    {
      method: WRITE_METHOD.update,
      body: command,
    },
  )
}

export function deleteBidCost(id: string): Promise<void> {
  return apiRequest<void>(COMMERCIAL_API.bidCost(requiredId(id, '投标成本ID')), {
    method: WRITE_METHOD.remove,
  })
}

export function markBidCostWon(id: string): Promise<void> {
  return apiRequest<void>(COMMERCIAL_API.bidWon(requiredId(id, '投标记录ID')), {
    method: WRITE_METHOD.update,
  })
}

export function changeBidStatus(
  id: string,
  expectedStatus: BidStatus,
  targetStatus: BidStatus,
  reason?: string | null,
): Promise<string | null> {
  return apiRequest<string | null>(COMMERCIAL_API.bidStatus(requiredId(id, '投标记录ID')), {
    method: WRITE_METHOD.update,
    body: { expectedStatus, targetStatus, reason },
  })
}

export function loadBidDocuments(
  id: string,
  signal?: AbortSignal,
): Promise<BidDocumentVersionRecord[]> {
  return apiRequest<BidDocumentVersionRecord[]>(
    COMMERCIAL_API.bidDocuments(requiredId(id, '投标记录ID')),
    { signal },
  )
}

export function appendBidDocument(
  id: string,
  command: BidDocumentCreateCommand,
): Promise<BidDocumentVersionRecord> {
  return apiRequest<BidDocumentVersionRecord>(
    COMMERCIAL_API.bidDocuments(requiredId(id, '投标记录ID')),
    { method: WRITE_METHOD.create, body: command },
  )
}

export function finalizeBidDocument(id: string, versionId: string): Promise<void> {
  return apiRequest<void>(
    COMMERCIAL_API.bidDocumentFinalize(requiredId(id), requiredId(versionId)),
    { method: WRITE_METHOD.create },
  )
}

export function voidBidDocument(id: string, versionId: string, reason: string): Promise<void> {
  return apiRequest<void>(COMMERCIAL_API.bidDocumentVoid(requiredId(id), requiredId(versionId)), {
    method: WRITE_METHOD.create,
    body: { reason },
  })
}

export function markBidCostLost(id: string): Promise<void> {
  return apiRequest<void>(COMMERCIAL_API.bidLost(requiredId(id, '投标成本ID')), {
    method: WRITE_METHOD.update,
  })
}
