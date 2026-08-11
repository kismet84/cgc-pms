export interface ReportPeriodBounds {
  startDate: string
  endDate: string
}

function localIsoValue(date: Date): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString()
}

export function localDateInputValue(date = new Date()): string {
  return localIsoValue(date).slice(0, 10)
}

export function localMonthInputValue(date = new Date()): string {
  return localIsoValue(date).slice(0, 7)
}

export function localDateTimeInputValue(date = new Date()): string {
  return localIsoValue(date).slice(0, 16)
}

export function reportPeriodBounds(period: string | null | undefined): ReportPeriodBounds | null {
  const match = /^(\d{4})-(\d{2})$/.exec(period ?? '')
  if (!match) return null
  const year = Number(match[1])
  const month = Number(match[2])
  if (month < 1 || month > 12) return null
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate()
  return {
    startDate: `${period}-01`,
    endDate: `${period}-${String(lastDay).padStart(2, '0')}`,
  }
}
