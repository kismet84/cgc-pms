import type { PageResult } from '@cgc-pms/frontend-contracts'
import { apiRequest } from './request'

export interface DictOption {
  dictLabel: string
  dictValue: string
  status: string
}

export interface PartnerRecord {
  id: string
  partnerCode: string
  partnerName: string
  partnerType: string
  creditCode?: string
  legalPerson?: string
  contactName?: string
  contactPhone?: string
  bankName?: string
  bankAccount?: string
  qualificationLevel?: string
  blacklistFlag?: number
  riskLevel?: string
  defaultLeadDays?: number
  status: string
}

export type PartnerCommand = Omit<PartnerRecord, 'id'> & {
  defaultLeadDays: number | null
}

export interface OrgCompanyRecord {
  id: string
  companyCode: string
  companyName: string
  status: string
  remark?: string
}

export interface OrgDepartmentRecord {
  id: string
  companyId: string
  parentId?: string
  deptCode: string
  deptName: string
  orderNum: number
  status: string
  remark?: string
  children?: OrgDepartmentRecord[]
}

export interface OrgPositionRecord {
  id: string
  companyId: string
  departmentId: string
  positionCode: string
  positionName: string
  status: string
  remark?: string
}

export interface MaterialRecord {
  id: string
  materialCode: string
  materialName: string
  categoryId?: string
  specification?: string
  unit?: string
  brand?: string
  defaultTaxRate?: string
  taxInclusiveInfoPrice?: string
  infoPricePeriod?: string
  infoPriceSource?: string
  infoPriceVerificationStatus?: string
  infoPriceExternalRowKey?: string
  infoPriceReviewRequired?: number
  purchasePrice?: string
  purchasePriceReceiptItemId?: string
  purchasePriceDate?: string
  status: string
  createdAt?: string
  remark?: string
}

export interface MaterialImportResult {
  total: number
  created: number
  priceUpdated: number
  conflictsCreated: number
  skipped: number
  failed: number
  errors: Array<{ row: number; code: string; message: string }>
}

export interface MaterialCategory {
  id: string
  categoryCode: string
  categoryName: string
  status: string
}

export function loadPartnerTypes(signal?: AbortSignal): Promise<DictOption[]> {
  return apiRequest<DictOption[]>('/system/dict/data/by-code/partner_type', { signal })
}

export function loadPartners(
  query: Record<string, string | number | undefined>,
  signal?: AbortSignal,
): Promise<PageResult<PartnerRecord>> {
  return apiRequest<PageResult<PartnerRecord>>(withQuery('/partners', query), { signal })
}

export function loadPartner(id: string): Promise<PartnerRecord> {
  return apiRequest<PartnerRecord>(`/partners/${requiredId(id)}`)
}

export function createPartner(command: PartnerCommand): Promise<string | number> {
  return apiRequest<string | number, PartnerCommand>('/partners', { method: 'POST', body: command })
}

export function updatePartner(id: string, command: PartnerCommand): Promise<void> {
  return apiRequest<void, PartnerCommand>(`/partners/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function deletePartner(id: string): Promise<void> {
  return apiRequest<void>(`/partners/${requiredId(id)}`, { method: 'DELETE' })
}

export function loadCompanies(
  query: { pageNo: number; pageSize: number },
  signal?: AbortSignal,
): Promise<PageResult<OrgCompanyRecord>> {
  return apiRequest<PageResult<OrgCompanyRecord>>(withQuery('/org/companies', query), { signal })
}

export function loadDepartmentTree(
  companyId?: string,
  signal?: AbortSignal,
): Promise<OrgDepartmentRecord[]> {
  return apiRequest<OrgDepartmentRecord[]>(
    withQuery('/org/departments/tree', { companyId: companyId || undefined }),
    { signal },
  )
}

export function loadPositions(
  query: { pageNo: number; pageSize: number; companyId?: string; departmentId?: string },
  signal?: AbortSignal,
): Promise<PageResult<OrgPositionRecord>> {
  return apiRequest<PageResult<OrgPositionRecord>>(withQuery('/org/positions', query), { signal })
}

export function saveCompany(
  id: string | null,
  command: Omit<OrgCompanyRecord, 'id'>,
): Promise<string | void> {
  return id
    ? apiRequest<void, Omit<OrgCompanyRecord, 'id'>>(`/org/companies/${requiredId(id)}`, {
        method: 'PUT',
        body: command,
      })
    : apiRequest<string, Omit<OrgCompanyRecord, 'id'>>('/org/companies', {
        method: 'POST',
        body: command,
      })
}

export function deleteCompany(id: string): Promise<void> {
  return apiRequest<void>(`/org/companies/${requiredId(id)}`, { method: 'DELETE' })
}

export function saveDepartment(
  id: string | null,
  command: Omit<OrgDepartmentRecord, 'id' | 'children'>,
): Promise<string | void> {
  return id
    ? apiRequest<void, Omit<OrgDepartmentRecord, 'id' | 'children'>>(
        `/org/departments/${requiredId(id)}`,
        { method: 'PUT', body: command },
      )
    : apiRequest<string, Omit<OrgDepartmentRecord, 'id' | 'children'>>('/org/departments', {
        method: 'POST',
        body: command,
      })
}

export function deleteDepartment(id: string): Promise<void> {
  return apiRequest<void>(`/org/departments/${requiredId(id)}`, { method: 'DELETE' })
}

export function savePosition(
  id: string | null,
  command: Omit<OrgPositionRecord, 'id'>,
): Promise<string | void> {
  return id
    ? apiRequest<void, Omit<OrgPositionRecord, 'id'>>(`/org/positions/${requiredId(id)}`, {
        method: 'PUT',
        body: command,
      })
    : apiRequest<string, Omit<OrgPositionRecord, 'id'>>('/org/positions', {
        method: 'POST',
        body: command,
      })
}

export function deletePosition(id: string): Promise<void> {
  return apiRequest<void>(`/org/positions/${requiredId(id)}`, { method: 'DELETE' })
}

export function loadMaterials(
  query: Record<string, string | number | undefined>,
  signal?: AbortSignal,
): Promise<PageResult<MaterialRecord>> {
  return apiRequest<PageResult<MaterialRecord>>(withQuery('/materials', query), { signal })
}

export function loadMaterial(id: string): Promise<MaterialRecord> {
  return apiRequest<MaterialRecord>(`/materials/${requiredId(id)}`)
}

export function loadMaterialCategories(signal?: AbortSignal): Promise<MaterialCategory[]> {
  return apiRequest<MaterialCategory[]>('/material-categories', { signal })
}

export function createMaterial(command: Omit<MaterialRecord, 'id' | 'createdAt'>): Promise<string> {
  return apiRequest<string, Omit<MaterialRecord, 'id' | 'createdAt'>>('/materials', {
    method: 'POST',
    body: command,
  })
}

export function updateMaterial(
  id: string,
  command: Omit<MaterialRecord, 'id' | 'createdAt'>,
): Promise<void> {
  return apiRequest<void, Omit<MaterialRecord, 'id' | 'createdAt'>>(
    `/materials/${requiredId(id)}`,
    { method: 'PUT', body: command },
  )
}

export function updateMaterialStatus(id: string, status: 'ENABLE' | 'DISABLE'): Promise<void> {
  return apiRequest<void>(`/materials/${requiredId(id)}/status?status=${status}`, {
    method: 'PUT',
  })
}

export function downloadMaterialImportTemplate(): Promise<Blob> {
  return apiRequest<Blob>('/materials/import-template', {
    headers: { Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
  })
}

export function importMaterials(file: File): Promise<MaterialImportResult> {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<MaterialImportResult, FormData>('/materials/import', {
    method: 'POST',
    body,
  })
}

function withQuery(path: string, query: Record<string, string | number | undefined>): string {
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

function requiredId(value: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError('ID不能为空')
  return normalized
}
