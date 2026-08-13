import {
  SUPPLY_CHAIN_API,
  type MaterialReturnCommand,
  type MaterialReturnItemRecord,
  type MaterialReturnRecord,
  type RequisitionCommand,
  type RequisitionItemRecord,
  type RequisitionPage,
  type RequisitionQuery,
  type RequisitionRecord,
  type RequisitionTraceRecord,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import {
  createId,
  deleteResource,
  encodedId,
  post,
  PUT_METHOD,
  resourcePath,
  saveItems,
  withQuery,
} from './support'
import type { RequisitionFormOptions } from './types'

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
