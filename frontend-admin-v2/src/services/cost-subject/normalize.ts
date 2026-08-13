import type { CostSubjectAuditRow, CostSubjectRecord } from './types'

export function normalizeSubject(row: CostSubjectRecord): CostSubjectRecord {
  return {
    ...row,
    id: String(row.id),
    parentId: row.parentId == null ? '0' : String(row.parentId),
    defaultTargetRatio: row.defaultTargetRatio == null ? null : String(row.defaultTargetRatio),
    children: row.children?.map(normalizeSubject) ?? [],
  }
}

export function normalizeRow<T>(row: Record<string, unknown>): T {
  return Object.fromEntries(
    Object.entries(row).map(([key, value]) => {
      const normalizedKey = key.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase())
      return [normalizedKey, isIdKey(normalizedKey) && value != null ? String(value) : value]
    }),
  ) as T
}

export function normalizeAuditRow(row: Record<string, unknown>): CostSubjectAuditRow {
  return Object.fromEntries(
    Object.entries(normalizeRow<Record<string, unknown>>(row)).map(([key, value]) => [
      key,
      isAmountKey(key) && value != null ? String(value) : (value as string | number | null),
    ]),
  )
}

function isIdKey(key: string): boolean {
  return key === 'id' || key.endsWith('Id')
}

function isAmountKey(key: string): boolean {
  return /(?:amount|cost|allocated|transferred)$/i.test(key)
}

export function query(values: Record<string, string>): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value.trim()) params.set(key, value.trim())
  }
  return params.toString()
}

export function requiredId(value: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError('ID不能为空')
  return encodeURIComponent(normalized)
}
