import type { DashboardDataByRole } from '@cgc-pms/frontend-contracts'
import { describe, expect, it } from 'vitest'
import {
  alertRiskLevel,
  compactDashboardValue,
  dashboardActivityItems,
  deriveDashboardHealth,
  formatAmount,
  formatRatio,
  normalizeGaugeValue,
  primaryRiskItems,
} from '@/pages/dashboard/model'

describe('dashboard display model', () => {
  it('maps INFO alerts to the styled other risk level', () => {
    expect(alertRiskLevel('INFO')).toBe('other')
  })

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

  it('reuses role business records as activity instead of fabricating trend data', () => {
    const purchase = {
      purchaseOrders: [
        {
          sourceType: 'PURCHASE_ORDER',
          sourceId: '1',
          title: '钢筋采购订单',
          projectName: '在建项目',
          amount: '320000.00',
          status: 'IN_PROGRESS',
        },
      ],
      recentRequests: [],
      pendingReceipts: [],
    } as DashboardDataByRole['purchase']

    expect(dashboardActivityItems('purchase', purchase)).toEqual([
      {
        id: 'PURCHASE_ORDER-1',
        title: '钢筋采购订单',
        meta: '在建项目',
        value: '¥320,000.00',
        status: 'IN_PROGRESS',
      },
    ])
  })

  it('classifies risk filters from business severity instead of list position', () => {
    const cost = {
      overBudgetAlerts: [
        {
          alertType: 'COST_OVER_BUDGET',
          severity: 'MEDIUM',
          message: '一般关注',
          projectId: '1',
          projectName: '项目一',
          triggeredAt: '2026-07-20 10:00:00',
        },
        {
          alertType: 'COST_OVER_BUDGET',
          severity: 'HIGH',
          message: '高风险',
          projectId: '2',
          projectName: '项目二',
          triggeredAt: '2026-07-20 11:00:00',
        },
        {
          alertType: 'COST_OVER_BUDGET',
          severity: 'LOW',
          message: '低风险',
          projectId: '3',
          projectName: '项目三',
          triggeredAt: '2026-07-20 12:00:00',
        },
        {
          alertType: 'COST_OVER_BUDGET',
          severity: 'INFO',
          message: '其他提醒',
          projectId: '4',
          projectName: '项目四',
          triggeredAt: '2026-07-20 13:00:00',
        },
      ],
    } as DashboardDataByRole['cost']

    expect(primaryRiskItems('cost', cost).map((item) => item.riskLevel)).toEqual([
      'medium',
      'high',
      'low',
      'other',
    ])
  })

  it('classifies business contracts into the unified four levels', () => {
    const dateAfter = (days: number) => {
      const date = new Date()
      date.setDate(date.getDate() + days)
      return date.toISOString().slice(0, 10)
    }
    const business = {
      recentChanges: [
        {
          contractId: '1',
          contractCode: 'C-001',
          contractName: '长期合同',
          currentAmount: '800000',
          contractStatus: 'PERFORMING',
          endDate: dateAfter(365),
        },
        {
          contractId: '2',
          contractCode: 'C-002',
          contractName: '高风险合同',
          currentAmount: '720000',
          contractStatus: 'PERFORMING',
          endDate: dateAfter(20),
        },
        {
          contractId: '3',
          contractCode: 'C-003',
          contractName: '中风险合同',
          currentAmount: '710000',
          contractStatus: 'PERFORMING',
          endDate: dateAfter(60),
        },
        {
          contractId: '4',
          contractCode: 'C-004',
          contractName: '低风险合同',
          currentAmount: '700000',
          contractStatus: 'PERFORMING',
          endDate: dateAfter(120),
        },
      ],
    } as DashboardDataByRole['bm']

    expect(primaryRiskItems('bm', business).map((item) => item.riskLevel)).toEqual([
      'other',
      'high',
      'medium',
      'low',
    ])
    expect(primaryRiskItems('bm', business)[0]?.meta).toBe('C-001')
    expect(dashboardActivityItems('bm', business)[0]?.meta).toBe('C-001')
  })
})
