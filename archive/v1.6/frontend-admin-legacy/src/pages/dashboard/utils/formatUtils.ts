/** Format value to 万元 with 2 decimal places */
export function fmtWan(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** Format absolute deviation value to 万元 */
export function fmtDeviation(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (Math.abs(n) / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** Format number with locale separators */
export function fmtNum(val: number | undefined): string {
  if (val == null) return '0'
  return val.toLocaleString('zh-CN')
}

/** Color based on deviation sign: positive -> red, negative -> green, zero -> gray */
export function devColor(val: string | undefined): string {
  if (!val) return '#6b7280'
  const n = parseFloat(val)
  if (n > 0) return '#ef4444'
  if (n < 0) return '#22c55e'
  return '#6b7280'
}

/** Sign prefix for deviation: '+' for positive, '' otherwise */
export function devSign(val: string | undefined): string {
  if (!val) return ''
  const n = parseFloat(val)
  return n > 0 ? '+' : ''
}

/** Safe number parse returning 0 for invalid inputs */
export function toNum(val: string | undefined): number {
  const n = parseFloat(val ?? '0')
  return Number.isFinite(n) ? n : 0
}
