import { describe, expect, it, vi } from 'vitest'
import { getDictLabelSync, getDictTagColorSync, clearDictCache } from '../dict'

vi.mock('@/api/modules/dict', () => ({
  getDictDataByCode: vi.fn(),
}))

describe('dict utils', () => {
  it('uses local fallback when dict cache has no matching label', () => {
    clearDictCache()

    expect(getDictLabelSync('common_status', 'ENABLE', { ENABLE: '启用' })).toBe('启用')
    expect(getDictLabelSync('common_status', 'UNKNOWN', { ENABLE: '启用' })).toBe('UNKNOWN')
  })

  it('uses local fallback color when dict cache has no matching listClass', () => {
    clearDictCache()

    expect(getDictTagColorSync('common_status', 'ENABLE', { ENABLE: 'success' })).toBe('success')
    expect(getDictTagColorSync('common_status', 'UNKNOWN', { ENABLE: 'success' })).toBe('default')
    expect(getDictTagColorSync('common_status', 'DISABLE', { DISABLE: 'danger' })).toBe('error')
  })
})
