import { describe, expect, it } from 'vitest'
import { getCostTypeLabel, getSourceTypeLabel } from '../cost'

describe('cost label helpers', () => {
  it('maps known cost type aliases to Chinese labels', () => {
    expect(getCostTypeLabel('MACHINERY')).toBe('机械费')
    expect(getCostTypeLabel('CT_MACHINE ')).toBe('机械使用成本')
    expect(getCostTypeLabel('CT_MACHINERY')).toBe('机械使用成本')
    expect(getCostTypeLabel(' CT_MACHINERY')).toBe('机械使用成本')
    expect(getCostTypeLabel('VARIATION')).toBe('签证变更成本')
  })

  it('maps known source type aliases to Chinese labels', () => {
    expect(getSourceTypeLabel('CT_MACHINE ')).toBe('机械使用成本')
    expect(getSourceTypeLabel('CT_MACHINERY')).toBe('机械使用成本')
    expect(getSourceTypeLabel('MAT_RECEIPT')).toBe('材料验收成本')
    expect(getSourceTypeLabel('UNKNOWN')).toBe('UNKNOWN')
  })
})
