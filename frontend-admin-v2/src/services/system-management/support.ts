import type { PageResult } from '@cgc-pms/frontend-contracts'

export type { PageResult } from '@cgc-pms/frontend-contracts'

export function normalizePage<T, R>(page: PageResult<T>, mapper: (row: T) => R): PageResult<R> {
  return {
    pageNo: Number(page.pageNo),
    pageSize: Number(page.pageSize),
    total: Number(page.total),
    records: (page.records ?? []).map(mapper),
  }
}

export function params(
  values: Record<string, string | number | undefined | null>,
): URLSearchParams {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.set(key, String(value))
    }
  }
  return query
}

export function requiredId(id: string): string {
  const value = id.trim()
  if (!value) throw new Error('业务标识不能为空')
  return encodeURIComponent(value)
}
