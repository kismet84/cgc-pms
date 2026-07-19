import { describe, expect, it } from 'vitest'
import {
  compactDashboardValue,
  deriveDashboardHealth,
  formatAmount,
  formatRatio,
  normalizeGaugeValue,
} from '@/pages/dashboard/model'

describe('dashboard display model', () => {
  it.each([
    ['12345678901234567890.12', '¥12,345,678,901,234,567,890.12'],
    ['-12.3456', '¥−12.3456'],
    ['0', '¥0.00'],
    ['-0.00', '¥0.00'],
    ['', '—'],
    [undefined, '—'],
  ])('formats amount strings without floating point conversion', (input, expected) => {
    expect(formatAmount(input)).toBe(expected)
  })

  it('preserves percentage strings', () => {
    expect(formatRatio('12.50')).toBe('12.50%')
    expect(formatRatio('7%')).toBe('7%')
    expect(formatRatio(null)).toBe('—')
  })

  it('compacts monetary dashboard metrics to ten-thousands', () => {
    expect(compactDashboardValue('¥3,900,000.00')).toEqual({ value: '390.00', unit: '万元' })
    expect(compactDashboardValue('¥−800,000.00')).toEqual({ value: '-80.00', unit: '万元' })
    expect(compactDashboardValue('12.50%')).toEqual({ value: '12.50%', unit: '' })
  })

  it('clamps gauge values to the display range', () => {
    expect(normalizeGaugeValue(-1)).toBe(0)
    expect(normalizeGaugeValue(76)).toBe(76)
    expect(normalizeGaugeValue(101)).toBe(100)
    expect(normalizeGaugeValue(Number.NaN)).toBe(0)
  })

  it('freezes the auxiliary health score bands', () => {
    expect(deriveDashboardHealth(0, 0, 0)).toEqual({ score: 100, label: '稳健', tone: 'success' })
    expect(deriveDashboardHealth(1, 1, 2)).toEqual({ score: 83, label: '关注', tone: 'warning' })
    expect(deriveDashboardHealth(4, 3, 4)).toEqual({ score: 45, label: '风险', tone: 'danger' })
    expect(deriveDashboardHealth(99, 99, 99).score).toBe(0)
  })
})
