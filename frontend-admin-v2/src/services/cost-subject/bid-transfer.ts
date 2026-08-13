import { apiRequest } from '../request'
import { normalizeAuditRow, query, requiredId } from './normalize'
import type {
  BidTransferRequestCommand,
  BidTransferRequestRecord,
  CostSubjectAuditRow,
} from './types'

export function loadBidTransfers(signal?: AbortSignal): Promise<CostSubjectAuditRow[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/bid-transfers', { signal }).then(
    (rows) => rows.map(normalizeAuditRow),
  )
}

export function loadBidTransferRequests(signal?: AbortSignal): Promise<BidTransferRequestRecord[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/bid-transfer-requests', {
    signal,
  }).then((rows) => rows.map((row) => normalizeAuditRow(row) as BidTransferRequestRecord))
}

export function createBidTransferRequest(
  command: BidTransferRequestCommand,
): Promise<BidTransferRequestRecord> {
  return apiRequest<Record<string, unknown>, BidTransferRequestCommand>(
    '/cost-subject-v2/bid-transfer-requests',
    { method: 'POST', body: command },
  ).then((row) => normalizeAuditRow(row) as BidTransferRequestRecord)
}

export function submitBidTransferRequest(id: string): Promise<BidTransferRequestRecord> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/bid-transfer-requests/${requiredId(id)}/submit`,
    { method: 'POST' },
  ).then((row) => normalizeAuditRow(row) as BidTransferRequestRecord)
}

export function reverseBidTransfer(
  id: string,
  approvalInstanceId: string,
  idempotencyKey: string,
  remark: string,
): Promise<string | number> {
  return apiRequest<string | number>(
    `/cost-subject-v2/bid-transfers/${requiredId(id)}/reverse?${query({
      approvalInstanceId,
      idempotencyKey,
      remark,
    })}`,
    { method: 'POST' },
  )
}
