import { describe, expect, it } from 'vitest'
import { reportPeriodBounds } from '@/services/workspace-context'

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
})
