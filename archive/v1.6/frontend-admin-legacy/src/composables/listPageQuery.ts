import type { LocationQuery, LocationQueryRaw, LocationQueryValue } from 'vue-router'

function firstQueryValue(value: LocationQueryValue | LocationQueryValue[] | undefined) {
  return Array.isArray(value) ? value[0] : value
}

export function readStringQuery(value: LocationQueryValue | LocationQueryValue[] | undefined) {
  const raw = firstQueryValue(value)
  if (raw == null) return undefined
  const text = String(raw).trim()
  return text ? text : undefined
}

export function readPositiveIntQuery(
  value: LocationQueryValue | LocationQueryValue[] | undefined,
  fallback: number,
) {
  const raw = readStringQuery(value)
  if (!raw) return fallback
  const parsed = Number.parseInt(raw, 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

export function replaceListQuery(
  currentQuery: LocationQuery,
  patch: Record<string, string | number | undefined>,
  managedKeys: string[],
): LocationQueryRaw {
  const nextQuery: LocationQueryRaw = { ...currentQuery }
  for (const key of managedKeys) {
    delete nextQuery[key]
  }
  for (const [key, value] of Object.entries(patch)) {
    if (value == null) continue
    if (typeof value === 'string' && !value.trim()) continue
    nextQuery[key] = String(value)
  }
  return nextQuery
}
