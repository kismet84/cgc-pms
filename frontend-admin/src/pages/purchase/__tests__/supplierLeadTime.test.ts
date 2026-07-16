import { describe, expect, it } from 'vitest'
import { suggestSupplierDeliveryDate } from '../supplierLeadTime'

describe('suggestSupplierDeliveryDate', () => {
  it('uses calendar days for zero, month boundaries and leap days', () => {
    expect(suggestSupplierDeliveryDate('2026-07-16', 0)).toBe('2026-07-16')
    expect(suggestSupplierDeliveryDate('2026-01-31', 1)).toBe('2026-02-01')
    expect(suggestSupplierDeliveryDate('2028-02-28', 1)).toBe('2028-02-29')
  })

  it('keeps null and invalid inputs empty', () => {
    expect(suggestSupplierDeliveryDate('2026-07-16', null)).toBeUndefined()
    expect(suggestSupplierDeliveryDate(undefined, 7)).toBeUndefined()
    expect(suggestSupplierDeliveryDate('2026-02-30', 7)).toBeUndefined()
    expect(suggestSupplierDeliveryDate('2026-07-16', 1.5)).toBeUndefined()
    expect(suggestSupplierDeliveryDate('2026-07-16', -1)).toBeUndefined()
    expect(suggestSupplierDeliveryDate('2026-07-16', 3651)).toBeUndefined()
  })
})
