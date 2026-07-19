export function normalizeRedirect(value: unknown, fallback = '/session'): string {
  if (typeof value !== 'string') return fallback
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/login')) {
    return fallback
  }
  return value
}
