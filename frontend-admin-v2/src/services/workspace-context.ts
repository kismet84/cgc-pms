export interface ReportPeriodBounds {
  startDate: string
  endDate: string
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
