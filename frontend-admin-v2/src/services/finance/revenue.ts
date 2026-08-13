import {
  FINANCE_API,
  type CollectionCommand,
  type CollectionRecord,
  type ContractRevenuePage,
  type OwnerSettlementCommand,
  type OwnerSettlementRecord,
  type ReceivableRecord,
  type RevenueQuery,
  type RevenueRecord,
  type SalesInvoiceCommand,
  type SalesInvoiceRecord,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId, withQuery } from './support'

export const loadRevenueSettlements = (query: RevenueQuery = {}, signal?: AbortSignal) =>
  apiRequest<OwnerSettlementRecord[]>(withQuery(FINANCE_API.revenueSettlements, query), { signal })
export const loadApprovedContractRevenues = (
  projectId: string,
  contractId: string,
  signal?: AbortSignal,
) =>
  apiRequest<ContractRevenuePage>(
    withQuery(FINANCE_API.contractRevenues, {
      projectId,
      contractId,
    }),
    { signal },
  )
export const loadReceivables = (query: RevenueQuery = {}, signal?: AbortSignal) =>
  apiRequest<ReceivableRecord[]>(withQuery(FINANCE_API.revenueReceivables, query), { signal })
export const loadSalesInvoices = (query: RevenueQuery = {}, signal?: AbortSignal) =>
  apiRequest<SalesInvoiceRecord[]>(withQuery(FINANCE_API.revenueSalesInvoices, query), { signal })
export const loadCollections = (query: RevenueQuery = {}, signal?: AbortSignal) =>
  apiRequest<CollectionRecord[]>(withQuery(FINANCE_API.revenueCollections, query), { signal })
export const createOwnerSettlement = (body: OwnerSettlementCommand) =>
  apiRequest<OwnerSettlementRecord>(FINANCE_API.revenueSettlements, { method: 'POST', body })
export const submitOwnerSettlement = (id: string) =>
  apiRequest<OwnerSettlementRecord>(`${FINANCE_API.revenueSettlements}/${requiredId(id)}/submit`, {
    method: 'POST',
  })
export const createSalesInvoice = (body: SalesInvoiceCommand) =>
  apiRequest<SalesInvoiceRecord>(FINANCE_API.revenueSalesInvoices, { method: 'POST', body })
export const confirmSalesInvoice = (id: string, allocations: SalesInvoiceCommand['allocations']) =>
  apiRequest<SalesInvoiceRecord>(`${FINANCE_API.revenueSalesInvoices}/${requiredId(id)}/confirm`, {
    method: 'POST',
    body: { allocations },
  })
export const createCollection = (body: CollectionCommand) =>
  apiRequest<CollectionRecord>(FINANCE_API.revenueCollections, { method: 'POST', body })
export const confirmCollection = (
  id: string,
  allocations: NonNullable<CollectionCommand['allocations']>,
) =>
  apiRequest<CollectionRecord>(`${FINANCE_API.revenueCollections}/${requiredId(id)}/confirm`, {
    method: 'POST',
    body: { allocations },
  })
export const creditReceivable = (
  id: string,
  amount: string,
  reason: string,
  idempotencyKey: string,
) =>
  apiRequest<RevenueRecord>(`${FINANCE_API.revenueReceivables}/${requiredId(id)}/credit`, {
    method: 'POST',
    body: { amount, reason, idempotencyKey },
  })
export const reverseCollection = (id: string, reason: string, idempotencyKey: string) =>
  apiRequest<RevenueRecord>(`${FINANCE_API.revenueCollections}/${requiredId(id)}/reverse`, {
    method: 'POST',
    body: { reason, idempotencyKey },
  })
