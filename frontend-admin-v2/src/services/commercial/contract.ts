import {
  COMMERCIAL_API,
  type ContractApprovalRecord,
  type ContractBudgetAllocationRecord,
  type ContractCompositeRecord,
  type ContractItemRecord,
  type ContractKpi,
  type ContractPage,
  type ContractPaymentTermRecord,
  type ContractProjectOption,
  type ContractQuery,
  type ContractRecord,
  type ContractSaveCommand,
  type PartnerQuery,
  type PartnerRecord,
  type ProjectContextOption,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId, WRITE_METHOD } from './support'
import type { CostSubjectOption } from './types'

export function loadContractPage(
  query: ContractQuery = {},
  signal?: AbortSignal,
): Promise<ContractPage> {
  return apiRequest<ContractPage>(withQuery(COMMERCIAL_API.contracts, query), { signal })
}

export async function loadAllContracts(
  query: Omit<ContractQuery, 'pageNo' | 'pageSize'> = {},
  signal?: AbortSignal,
): Promise<ContractRecord[]> {
  const pageSize = 200
  const first = await loadContractPage({ ...query, pageNo: 1, pageSize }, signal)
  const records = [...first.records]
  const pageCount = Math.ceil(first.total / pageSize)
  for (let pageNo = 2; pageNo <= pageCount; pageNo += 1) {
    const page = await loadContractPage({ ...query, pageNo, pageSize }, signal)
    records.push(...page.records)
  }
  return records
}

export function loadContractKpi(
  query: Omit<ContractQuery, 'pageNo' | 'pageSize' | 'keyword'> = {},
  signal?: AbortSignal,
): Promise<ContractKpi> {
  return apiRequest<ContractKpi>(withQuery(COMMERCIAL_API.contractKpi, query), { signal })
}

export function loadContractProjectOptions(signal?: AbortSignal): Promise<ContractProjectOption[]> {
  return apiRequest<ContractProjectOption[]>(COMMERCIAL_API.contractProjectOptions, { signal })
}

export function loadContract(id: string, signal?: AbortSignal): Promise<ContractRecord> {
  return apiRequest<ContractRecord>(COMMERCIAL_API.contract(requiredId(id, '合同ID')), { signal })
}

export function loadContractItems(id: string, signal?: AbortSignal): Promise<ContractItemRecord[]> {
  return apiRequest<ContractItemRecord[]>(COMMERCIAL_API.contractItems(requiredId(id, '合同ID')), {
    signal,
  })
}

export function loadContractPaymentTerms(
  id: string,
  signal?: AbortSignal,
): Promise<ContractPaymentTermRecord[]> {
  return apiRequest<ContractPaymentTermRecord[]>(
    COMMERCIAL_API.contractPaymentTerms(requiredId(id, '合同ID')),
    { signal },
  )
}

export function loadContractApprovalRecords(
  id: string,
  signal?: AbortSignal,
): Promise<ContractApprovalRecord[]> {
  return apiRequest<ContractApprovalRecord[]>(
    COMMERCIAL_API.contractApprovalRecords(requiredId(id, '合同ID')),
    { signal },
  )
}

export function loadContractBudgetAllocations(
  id: string,
  signal?: AbortSignal,
): Promise<ContractBudgetAllocationRecord[]> {
  return apiRequest<ContractBudgetAllocationRecord[]>(
    COMMERCIAL_API.contractBudgetAllocations(requiredId(id, '合同ID')),
    { signal },
  )
}

export function saveContractBudgetAllocations(
  id: string,
  rows: ContractBudgetAllocationRecord[],
): Promise<void> {
  const contractId = requiredId(id, '合同ID')
  return apiRequest<void, ContractBudgetAllocationRecord[]>(
    COMMERCIAL_API.contractBudgetAllocations(contractId),
    {
      method: WRITE_METHOD.update,
      body: rows.map(({ budgetLineId, allocatedAmount }) => ({
        contractId,
        budgetLineId,
        allocatedAmount,
      })),
    },
  )
}

export async function loadContractComposite(
  id: string,
  signal?: AbortSignal,
): Promise<ContractCompositeRecord> {
  const contractId = requiredId(id, '合同ID')
  const [contract, items, paymentTerms, approvalRecords] = await Promise.all([
    loadContract(contractId, signal),
    loadContractItems(contractId, signal),
    loadContractPaymentTerms(contractId, signal),
    loadContractApprovalRecords(contractId, signal),
  ])
  return { contract, items, paymentTerms, approvalRecords }
}

export function createContractComposite(command: ContractSaveCommand): Promise<string> {
  return apiRequest<string, ContractSaveCommand>(COMMERCIAL_API.contractCompositeCreate, {
    method: WRITE_METHOD.create,
    body: command,
  })
}

export function updateContractComposite(id: string, command: ContractSaveCommand): Promise<void> {
  return apiRequest<void, ContractSaveCommand>(
    COMMERCIAL_API.contractCompositeUpdate(requiredId(id, '合同ID')),
    {
      method: WRITE_METHOD.update,
      body: command,
    },
  )
}

export function submitContract(id: string, version?: string | number | null): Promise<void> {
  const params = new URLSearchParams()
  const normalizedVersion = String(version ?? '').trim()
  if (normalizedVersion) params.set('version', normalizedVersion)
  const path = `${COMMERCIAL_API.contractSubmit(requiredId(id, '合同ID'))}${
    params.size ? `?${params.toString()}` : ''
  }`
  return apiRequest<void>(path, {
    method: WRITE_METHOD.submit,
  })
}

export function deleteContract(id: string): Promise<void> {
  return apiRequest<void>(COMMERCIAL_API.contract(requiredId(id, '合同ID')), {
    method: WRITE_METHOD.remove,
  })
}

export function loadPartners(
  query: PartnerQuery = { pageNo: 1, pageSize: 200, status: 'ENABLE' },
  signal?: AbortSignal,
): Promise<{ records: PartnerRecord[] }> {
  return apiRequest<{ records: PartnerRecord[] }>(withQuery(COMMERCIAL_API.partners, query), {
    signal,
  })
}

export function loadProjectContextOptions(signal?: AbortSignal): Promise<ProjectContextOption[]> {
  return apiRequest<ProjectContextOption[]>(COMMERCIAL_API.projectContextOptions, { signal })
}

export function loadCostSubjectOptions(signal?: AbortSignal): Promise<CostSubjectOption[]> {
  return apiRequest<CostSubjectOption[]>('/cost-subjects?category=COST', { signal })
}

function withQuery(path: string, query: ContractQuery | PartnerQuery): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number') {
      if (Number.isInteger(value) && value > 0) params.set(key, String(value))
    } else if (value?.trim()) {
      params.set(key, value.trim())
    }
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}
