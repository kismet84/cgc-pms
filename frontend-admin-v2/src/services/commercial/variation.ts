import {
  COMMERCIAL_API,
  type VariationItemRecord,
  type VariationOwnerReviewCommand,
  type VariationOwnerSubmissionCommand,
  type VariationOwnerSubmissionRecord,
  type VariationPage,
  type VariationQuery,
  type VariationRecord,
  type VariationSaveCommand,
  type VariationTrace,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId, withSearchParams, withVersion, WRITE_METHOD } from './support'

export function loadVariationPage(
  query: VariationQuery = {},
  signal?: AbortSignal,
): Promise<VariationPage> {
  return apiRequest<VariationPage>(withSearchParams(COMMERCIAL_API.variations, query), { signal })
}

export function loadVariation(id: string, signal?: AbortSignal): Promise<VariationRecord> {
  return apiRequest<VariationRecord>(COMMERCIAL_API.variation(requiredId(id, '变更ID')), { signal })
}

export function loadVariationTrace(id: string, signal?: AbortSignal): Promise<VariationTrace> {
  return apiRequest<VariationTrace>(COMMERCIAL_API.variationTrace(requiredId(id, '变更ID')), {
    signal,
  })
}

export function createVariation(command: VariationSaveCommand): Promise<string> {
  return apiRequest<string, VariationSaveCommand>(COMMERCIAL_API.variations, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function updateVariation(id: string, command: VariationSaveCommand): Promise<void> {
  return apiRequest<void, VariationSaveCommand>(
    withVersion(COMMERCIAL_API.variation(requiredId(id, '变更ID')), command.version),
    {
      method: WRITE_METHOD.update,
      body: command,
    },
  )
}

export function deleteVariation(id: string, version: string | number): Promise<void> {
  return apiRequest<void>(
    withVersion(COMMERCIAL_API.variation(requiredId(id, '变更ID')), version),
    {
      method: WRITE_METHOD.remove,
    },
  )
}

export function saveVariationItems(
  id: string,
  items: VariationItemRecord[],
  version: string | number,
): Promise<void> {
  return apiRequest<void, VariationItemRecord[]>(
    withVersion(COMMERCIAL_API.variationItems(requiredId(id, '变更ID')), version),
    { method: WRITE_METHOD.create, body: items },
  )
}

export function submitVariation(id: string, version: string | number): Promise<void> {
  return apiRequest<void>(
    withVersion(COMMERCIAL_API.variationSubmit(requiredId(id, '变更ID')), version),
    { method: WRITE_METHOD.submit },
  )
}

export function submitVariationToOwner(
  id: string,
  command: VariationOwnerSubmissionCommand,
  version: string | number,
): Promise<VariationOwnerSubmissionRecord> {
  return apiRequest<VariationOwnerSubmissionRecord, VariationOwnerSubmissionCommand>(
    withVersion(COMMERCIAL_API.variationOwnerSubmissions(requiredId(id, '变更ID')), version),
    { method: WRITE_METHOD.create, body: command },
  )
}

export function reviewVariationOwner(
  id: string,
  submissionId: string,
  command: VariationOwnerReviewCommand,
  version: string | number,
): Promise<VariationOwnerSubmissionRecord> {
  return apiRequest<VariationOwnerSubmissionRecord, VariationOwnerReviewCommand>(
    withVersion(
      COMMERCIAL_API.variationOwnerReview(
        requiredId(id, '变更ID'),
        requiredId(submissionId, '申报ID'),
      ),
      version,
    ),
    { method: WRITE_METHOD.create, body: command },
  )
}
