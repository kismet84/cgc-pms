export function withQuery(path: string, query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'number' && Number.isInteger(value) && value > 0)
      params.set(key, String(value))
    else if (typeof value === 'boolean') params.set(key, String(value))
    else if (typeof value === 'string' && value.trim()) params.set(key, value.trim())
  }
  return params.size ? `${path}?${params}` : path
}
export function requiredId(id: string): string {
  if (!id.trim()) throw new Error('ID_REQUIRED')
  return encodeURIComponent(id.trim())
}
