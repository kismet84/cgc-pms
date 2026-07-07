import { describe, expect, it, vi } from 'vitest'
import { getDictDataByCode } from '@/api/modules/dict'
import { clearDictCache, fetchDictData, getDictLabelSync, getDictTagColorSync } from '../dict'

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

  it('prefers local fallback when cached dict label is still raw code', async () => {
    clearDictCache()
    vi.mocked(getDictDataByCode).mockResolvedValue([
      { dictValue: 'BUILDING', dictLabel: 'BUILDING' } as never,
      { dictValue: 'CT_MACHINERY', dictLabel: 'CT_MACHINERY' } as never,
    ])

    await fetchDictData('project_type')
    await fetchDictData('cost_type')

    expect(getDictLabelSync('project_type', 'BUILDING', { BUILDING: '施工总承包' })).toBe(
      '施工总承包',
    )
    expect(getDictLabelSync('project_type', 'BUILDING ', { BUILDING: '施工总承包' })).toBe(
      '施工总承包',
    )
    expect(getDictLabelSync('cost_type', 'CT_MACHINERY', { CT_MACHINERY: '机械使用成本' })).toBe(
      '机械使用成本',
    )
  })
})
