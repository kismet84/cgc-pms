export function normalizeArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (value && typeof value === 'object') {
    const records = (value as { records?: unknown }).records
    if (Array.isArray(records)) return records as T[]
  }
  return []
}
