import { describe, expect, it, vi } from 'vitest'
import {
  localDateInputValue,
  localDateTimeInputValue,
  localMonthInputValue,
  reportPeriodBounds,
} from '@/services/workspace-context'

describe('workspace report-period context', () => {
  it('maps valid periods to inclusive calendar-month bounds', () => {
    expect(reportPeriodBounds('2026-02')).toEqual({
      startDate: '2026-02-01',
      endDate: '2026-02-28',
    })
    expect(reportPeriodBounds('2024-02')?.endDate).toBe('2024-02-29')
  })

  it('rejects malformed and out-of-range periods', () => {
    expect(reportPeriodBounds('2026-13')).toBeNull()
    expect(reportPeriodBounds('2026-7')).toBeNull()
    expect(reportPeriodBounds(null)).toBeNull()
  })

  it('formats date inputs in local time instead of UTC', () => {
    const date = new Date('2026-08-11T16:05:00.000Z')
    vi.spyOn(date, 'getTimezoneOffset').mockReturnValue(-480)

    expect(localDateInputValue(date)).toBe('2026-08-12')
    expect(localMonthInputValue(date)).toBe('2026-08')
    expect(localDateTimeInputValue(date)).toBe('2026-08-12T00:05')
  })
})
