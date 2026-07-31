import { describe, expect, it } from 'vitest'
import {
  ALL_DASHBOARD_MONTH,
  buildDashboardMonthOptions,
  formatDashboardMonth,
} from '../composables/useDashboardData'

describe('Dashboard month options', () => {
  it('formats local month without UTC rollover', () => {
    expect(formatDashboardMonth(new Date(2026, 5, 1))).toBe('2026-06')
  })

  it('defaults to all and prepends the all option', () => {
    const options = buildDashboardMonthOptions(new Date(2026, 6, 2))

    expect(ALL_DASHBOARD_MONTH).toBe('')
    expect(options[0]).toEqual({ value: '', label: '全部' })
    expect(options[1]).toEqual({ value: '2026-07', label: '2026-07' })
  })

  it('keeps concrete month options available for switching', () => {
    const options = buildDashboardMonthOptions(new Date(2026, 6, 2))

    expect(options.map((option) => option.value)).toContain('2026-06')
    expect(options.map((option) => option.value)).toContain('2026-07')
  })
})
