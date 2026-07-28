export function normalizeRedirect(value: unknown, fallback = '/session'): string {
  if (typeof value !== 'string') return fallback
  const basePath = import.meta.env.BASE_URL.replace(/\/$/, '')
  const target =
    basePath && value === basePath
      ? fallback
      : basePath && value.startsWith(`${basePath}/`)
        ? value.slice(basePath.length)
        : value
  if (!target.startsWith('/') || target.startsWith('//') || target.startsWith('/login')) {
    return fallback
  }
  return target
}
