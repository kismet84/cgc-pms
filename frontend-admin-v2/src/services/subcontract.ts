import {
  SUBCONTRACT_API,
  type SettlementApprovalRecord,
  type SettlementAttachmentRecord,
  type SettlementCommand,
  type SettlementCompute,
  type SettlementCostRecord,
  type SettlementItemCommand,
  type SettlementItemRecord,
  type SettlementKpi,
  type SettlementPage,
  type SettlementPaymentRecord,
  type SettlementQuery,
  type SettlementRecord,
  type SettlementSources,
  type SettlementVariationRecord,
  type SubcontractMeasureCommand,
  type SubcontractMeasureItemCommand,
  type SubcontractMeasureItemRecord,
  type SubcontractMeasurePage,
  type SubcontractMeasureQuery,
  type SubcontractMeasureRecord,
  type SubcontractTaskCommand,
  type SubcontractTaskFormOptions,
  type SubcontractTaskPage,
  type SubcontractTaskQuery,
  type SubcontractTaskRecord,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export const loadSubcontractTasks = (query: SubcontractTaskQuery = {}, signal?: AbortSignal) =>
  apiRequest<SubcontractTaskPage>(withQuery(SUBCONTRACT_API.tasks, query), { signal })

export const loadSubcontractTask = (id: string, signal?: AbortSignal) =>
  apiRequest<SubcontractTaskRecord>(SUBCONTRACT_API.task(requiredId(id)), { signal })

export const loadSubcontractTaskFormOptions = (projectId: string, signal?: AbortSignal) =>
  apiRequest<SubcontractTaskFormOptions>(
    withQuery(SUBCONTRACT_API.taskFormOptions, { projectId }),
    { signal },
  )

export const createSubcontractTask = (command: SubcontractTaskCommand) =>
  apiRequest<string, SubcontractTaskCommand>(SUBCONTRACT_API.tasks, {
    method: 'POST',
    body: command,
  })

export const updateSubcontractTask = (id: string, command: SubcontractTaskCommand) =>
  apiRequest<void, SubcontractTaskCommand>(SUBCONTRACT_API.task(requiredId(id)), {
    method: 'PUT',
    body: command,
  })

export const deleteSubcontractTask = (id: string) =>
  apiRequest<void>(SUBCONTRACT_API.task(requiredId(id)), { method: 'DELETE' })

export const loadSubcontractMeasures = (
  query: SubcontractMeasureQuery = {},
  signal?: AbortSignal,
) => apiRequest<SubcontractMeasurePage>(withQuery(SUBCONTRACT_API.measures, query), { signal })

export const loadSubcontractMeasure = (id: string, signal?: AbortSignal) =>
  apiRequest<SubcontractMeasureRecord>(SUBCONTRACT_API.measure(requiredId(id)), { signal })

export const createSubcontractMeasure = (command: SubcontractMeasureCommand) =>
  apiRequest<string, SubcontractMeasureCommand>(SUBCONTRACT_API.measures, {
    method: 'POST',
    body: command,
  })

export const updateSubcontractMeasure = (id: string, command: SubcontractMeasureCommand) =>
  apiRequest<void, SubcontractMeasureCommand>(SUBCONTRACT_API.measure(requiredId(id)), {
    method: 'PUT',
    body: command,
  })

export const deleteSubcontractMeasure = (id: string) =>
  apiRequest<void>(SUBCONTRACT_API.measure(requiredId(id)), { method: 'DELETE' })

export const loadSubcontractMeasureItems = (id: string, signal?: AbortSignal) =>
  apiRequest<SubcontractMeasureItemRecord[]>(SUBCONTRACT_API.measureItems(requiredId(id)), {
    signal,
  })

export const saveSubcontractMeasureItems = (id: string, items: SubcontractMeasureItemCommand[]) =>
  apiRequest<void, SubcontractMeasureItemCommand[]>(
    SUBCONTRACT_API.measureItemsBatch(requiredId(id)),
    { method: 'POST', body: items },
  )

export const submitSubcontractMeasure = (id: string) =>
  apiRequest<void>(SUBCONTRACT_API.measureSubmit(requiredId(id)), { method: 'POST' })

export const loadSettlements = (query: SettlementQuery = {}, signal?: AbortSignal) =>
  apiRequest<SettlementPage>(withQuery(SUBCONTRACT_API.settlements, query), { signal })

export const loadSettlementKpi = (query: SettlementQuery = {}, signal?: AbortSignal) =>
  apiRequest<SettlementKpi>(withQuery(SUBCONTRACT_API.settlementKpi, query), { signal })

export const loadSettlement = (id: string, signal?: AbortSignal) =>
  apiRequest<SettlementRecord>(SUBCONTRACT_API.settlement(requiredId(id)), { signal })

export const createSettlement = (command: SettlementCommand) =>
  apiRequest<string, SettlementCommand>(SUBCONTRACT_API.settlements, {
    method: 'POST',
    body: command,
  })

export const updateSettlement = (id: string, command: SettlementCommand) =>
  apiRequest<void, SettlementCommand>(SUBCONTRACT_API.settlement(requiredId(id)), {
    method: 'PUT',
    body: command,
  })

export const deleteSettlement = (id: string) =>
  apiRequest<void>(SUBCONTRACT_API.settlement(requiredId(id)), { method: 'DELETE' })

export const loadSettlementItems = (id: string, signal?: AbortSignal) =>
  apiRequest<SettlementItemRecord[]>(SUBCONTRACT_API.settlementItems(requiredId(id)), { signal })

export const saveSettlementItems = (id: string, items: SettlementItemCommand[]) =>
  apiRequest<void, SettlementItemCommand[]>(SUBCONTRACT_API.settlementItemsBatch(requiredId(id)), {
    method: 'POST',
    body: items,
  })

export const computeSettlement = (contractId: string, signal?: AbortSignal) =>
  apiRequest<SettlementCompute>(SUBCONTRACT_API.settlementCompute(requiredId(contractId)), {
    signal,
  })

export const loadSettlementSources = (id: string, signal?: AbortSignal) =>
  apiRequest<SettlementSources>(SUBCONTRACT_API.settlementSources(requiredId(id)), { signal })

export const loadSettlementVariations = (id: string, signal?: AbortSignal) =>
  apiRequest<SettlementVariationRecord[]>(SUBCONTRACT_API.settlementVariations(requiredId(id)), {
    signal,
  })

export const loadSettlementPayments = (id: string, signal?: AbortSignal) =>
  apiRequest<SettlementPaymentRecord[]>(SUBCONTRACT_API.settlementPayments(requiredId(id)), {
    signal,
  })

export const loadSettlementCosts = (id: string, signal?: AbortSignal) =>
  apiRequest<SettlementCostRecord[]>(SUBCONTRACT_API.settlementCosts(requiredId(id)), { signal })

export const loadSettlementAttachments = (id: string, signal?: AbortSignal) =>
  apiRequest<SettlementAttachmentRecord[]>(SUBCONTRACT_API.settlementAttachments(requiredId(id)), {
    signal,
  })

export const loadSettlementApprovalRecords = (id: string, signal?: AbortSignal) =>
  apiRequest<SettlementApprovalRecord[]>(
    SUBCONTRACT_API.settlementApprovalRecords(requiredId(id)),
    { signal },
  )

export const submitSettlement = (id: string) =>
  apiRequest<void>(SUBCONTRACT_API.settlementSubmit(requiredId(id)), { method: 'POST' })

function requiredId(id: string): string {
  const normalized = id.trim()
  if (!normalized) throw new TypeError('业务记录不能为空')
  return normalized
}

function withQuery(path: string, query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number' && Number.isInteger(value) && value > 0)
      params.set(key, String(value))
    else if (typeof value === 'string' && value.trim()) params.set(key, value.trim())
  }
  return params.size ? `${path}?${params}` : path
}
