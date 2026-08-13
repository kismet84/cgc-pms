export const WRITE_METHOD = {
  create: 'POST' as const,
  update: 'PUT' as const,
  remove: 'DELETE' as const,
  submit: 'POST' as const,
}

export function withSearchParams(path: string, query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number') {
      if (Number.isInteger(value) && value > 0) params.set(key, String(value))
    } else if (typeof value === 'string' && value.trim()) {
      params.set(key, value.trim())
    }
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}

export function withVersion(path: string, version: string | number | null | undefined): string {
  const normalized = String(version ?? '').trim()
  if (!normalized) throw new TypeError('版本不能为空')
  return `${path}?version=${encodeURIComponent(normalized)}`
}

export function requiredId(value: string, label: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError(`${label}不能为空`)
  return normalized
}
