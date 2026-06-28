import { describe, expect, it } from 'vitest'
import { formatDashboardMonth } from '../composables/useDashboardData'

describe('Dashboard month options', () => {
  it('formats local month without UTC rollover', () => {
    expect(formatDashboardMonth(new Date(2026, 5, 1))).toBe('2026-06')
  })
})
