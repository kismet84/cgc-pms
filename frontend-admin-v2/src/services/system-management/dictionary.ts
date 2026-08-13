import { apiRequest } from '@/services/request'
import { normalizePage, params, requiredId, type PageResult } from './support'

export interface DictTypeRecord {
  id: string
  groupId: string
  dictCode: string
  dictName: string
  dictClass: string
  status: string
  createdAt?: string
}

export interface DictGroupRecord {
  id: string
  groupCode: string
  groupName: string
  orderNum: number
  status: string
}

export interface DictDataRecord {
  id: string
  dictTypeId: string
  dictLabel: string
  dictValue: string
  cssClass?: string
  listClass?: string
  orderNum: number
  status: string
}

export interface DictTreeType extends DictTypeRecord {
  data: DictDataRecord[]
}

export interface DictGroupTreeRecord extends DictGroupRecord {
  types: DictTreeType[]
}

export async function loadDictTypes(
  query: {
    pageNo: number
    pageSize: number
    groupId?: string
    dictCode?: string
    dictName?: string
    status?: string
    dictClass?: string
  },
  signal?: AbortSignal,
): Promise<PageResult<DictTypeRecord>> {
  const page = await apiRequest<PageResult<DictTypeRecord>>(`/system/dict/types?${params(query)}`, {
    signal,
  })
  return normalizePage(page, normalizeDictType)
}

export async function loadDictGroups(
  query: {
    pageNo: number
    pageSize: number
    keyword?: string
    status?: string
  },
  signal?: AbortSignal,
): Promise<PageResult<DictGroupRecord>> {
  const page = await apiRequest<PageResult<DictGroupRecord>>(
    `/system/dict/groups?${params(query)}`,
    { signal },
  )
  return normalizePage(page, normalizeDictGroup)
}

export function loadDictTree(keyword = '', signal?: AbortSignal): Promise<DictGroupTreeRecord[]> {
  return apiRequest<DictGroupTreeRecord[]>(`/system/dict/tree?${params({ keyword })}`, {
    signal,
  }).then((groups) => (groups ?? []).map(normalizeDictTreeGroup))
}

export function createDictGroup(command: Omit<DictGroupRecord, 'id'>): Promise<string> {
  return apiRequest<string, Omit<DictGroupRecord, 'id'>>('/system/dict/groups', {
    method: 'POST',
    body: command,
  }).then(String)
}

export function updateDictGroup(id: string, command: Omit<DictGroupRecord, 'id'>): Promise<void> {
  return apiRequest<void, Omit<DictGroupRecord, 'id'>>(`/system/dict/groups/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function deleteDictGroup(id: string): Promise<void> {
  return apiRequest<void>(`/system/dict/groups/${requiredId(id)}`, { method: 'DELETE' })
}

export function createDictType(command: Omit<DictTypeRecord, 'id' | 'createdAt'>): Promise<string> {
  return apiRequest<string, Omit<DictTypeRecord, 'id' | 'createdAt'>>('/system/dict/types', {
    method: 'POST',
    body: command,
  }).then(String)
}

export function updateDictType(
  id: string,
  command: Omit<DictTypeRecord, 'id' | 'createdAt'>,
): Promise<void> {
  return apiRequest<void, Omit<DictTypeRecord, 'id' | 'createdAt'>>(
    `/system/dict/types/${requiredId(id)}`,
    { method: 'PUT', body: command },
  )
}

export function deleteDictType(id: string): Promise<void> {
  return apiRequest<void>(`/system/dict/types/${requiredId(id)}`, { method: 'DELETE' })
}

export async function loadDictData(
  query: { pageNo: number; pageSize: number; typeId?: string; dictLabel?: string; status?: string },
  signal?: AbortSignal,
): Promise<PageResult<DictDataRecord>> {
  const page = await apiRequest<PageResult<DictDataRecord>>(`/system/dict/data?${params(query)}`, {
    signal,
  })
  return normalizePage(page, normalizeDictData)
}

export function loadEnabledDictDataByCode(
  code: string,
  signal?: AbortSignal,
): Promise<DictDataRecord[]> {
  return apiRequest<DictDataRecord[]>(`/system/dict/data/by-code/${requiredId(code)}`, {
    signal,
  }).then((rows) => (rows ?? []).map(normalizeDictData))
}

export function createDictData(command: Omit<DictDataRecord, 'id'>): Promise<string> {
  return apiRequest<string, Omit<DictDataRecord, 'id'>>('/system/dict/data', {
    method: 'POST',
    body: command,
  }).then(String)
}

export function updateDictData(id: string, command: Omit<DictDataRecord, 'id'>): Promise<void> {
  return apiRequest<void, Omit<DictDataRecord, 'id'>>(`/system/dict/data/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function deleteDictData(id: string): Promise<void> {
  return apiRequest<void>(`/system/dict/data/${requiredId(id)}`, { method: 'DELETE' })
}

function normalizeDictData(row: DictDataRecord): DictDataRecord {
  return {
    ...row,
    id: String(row.id),
    dictTypeId: String(row.dictTypeId),
    orderNum: Number(row.orderNum ?? 0),
  }
}

function normalizeDictGroup(row: DictGroupRecord): DictGroupRecord {
  return {
    ...row,
    id: String(row.id),
    orderNum: Number(row.orderNum ?? 0),
  }
}

function normalizeDictType(row: DictTypeRecord): DictTypeRecord {
  return { ...row, id: String(row.id), groupId: String(row.groupId) }
}

function normalizeDictTreeGroup(row: DictGroupTreeRecord): DictGroupTreeRecord {
  return {
    ...normalizeDictGroup(row),
    types: (row.types ?? []).map((type) => ({
      ...normalizeDictType(type),
      data: (type.data ?? []).map(normalizeDictData),
    })),
  }
}
