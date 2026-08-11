import type { DashboardDataByRole } from '@cgc-pms/frontend-contracts'
import { describe, expect, it } from 'vitest'
import { dashboardStatusLabel, formatAmount, formatDecimal } from '@/shared/display'
import {
  alertRiskLevel,
  compactDashboardValue,
  dashboardActivityItems,
  dashboardHealth,
  dashboardMetrics,
  deriveDashboardHealth,
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
    ['99999999999999999999.995', '¥100,000,000,000,000,000,000.00'],
    ['12.3449', '¥12.34'],
    ['12.345', '¥12.35'],
    ['-12.3449', '¥−12.34'],
    ['-12.345', '¥−12.35'],
    ['0.0049', '¥0.00'],
    ['-0.005', '¥−0.01'],
    ['32000.0000', '¥32,000.00'],
    ['32.5000', '¥32.50'],
    ['0', '¥0.00'],
    [0, '¥0.00'],
    ['-0.00', '¥0.00'],
    ['', '—'],
    [undefined, '—'],
  ])('formats amount strings without floating point conversion', (input, expected) => {
    expect(formatAmount(input)).toBe(expected)
  })

  it.each([
    ['9007199254740993.125', '9007199254740993.13'],
    ['-0.005', '-0.01'],
    ['3', '3.00'],
  ])('formats non-money decimals without floating point conversion', (input, expected) => {
    expect(formatDecimal(input)).toBe(expected)
  })

  it('preserves percentage strings', () => {
    expect(formatRatio('12.50')).toBe('12.50%')
    expect(formatRatio('7%')).toBe('7.00%')
    expect(formatRatio(null)).toBe('—')
  })

  it.each([
    ['VERIFIED', '已核验'],
    ['APPROVING', '审批中'],
    ['PREPARING', '筹备'],
    ['PAID', '已付款'],
    ['PARTIALLY_PAID', '部分付款'],
    ['RECEIVABLE_CREATED', '已生成应收'],
    ['PARTIALLY_COLLECTED', '部分回款'],
    ['FULLY_ALLOCATED', '已全额分配'],
  ])('localizes finance status %s', (input, expected) => {
    expect(dashboardStatusLabel(input)).toBe(expected)
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

  it('falls back to business code when runtime data has no title', () => {
    const production = {
      recentReceipts: [],
      recentRequisitions: [
        {
          sourceType: 'MATERIAL_REQUISITION',
          sourceId: '1',
          code: 'REQ-20260720-001',
          title: null,
          itemSummary: null,
          projectName: '在建项目',
          amount: '32000.00',
          status: 'APPROVED',
        },
      ],
      recentSubMeasures: [],
    } as unknown as DashboardDataByRole['production']

    expect(dashboardActivityItems('production', production)[0]?.title).toBe('REQ-20260720-001')
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

  it('keeps business settlement progress as finalized/total and exposes authoritative metrics', () => {
    const business = {
      totalContractAmount: '1000000.00',
      contractChangeAmount: '120000.00',
      varOrderAmount: '80000.00',
      subMeasureAmount: '650000.00',
      paidRatio: '65.00',
      settlementProgress: '1/3',
      recentChanges: [],
      settlementItems: [],
    } as DashboardDataByRole['bm']

    expect(dashboardMetrics('bm', business)).toEqual([
      { label: '合同总额', value: '¥1,000,000.00' },
      { label: '合同变更', value: '¥120,000.00' },
      { label: '签证金额', value: '¥80,000.00' },
      { label: '分包计量', value: '¥650,000.00' },
      { label: '支付比例', value: '65.00%' },
      { label: '结算进度', value: '1/3' },
    ])
  })

  it('adds settlement project activity without fabricating a monetary value', () => {
    const business = {
      recentChanges: [],
      settlementItems: [
        {
          projectId: '2',
          projectName: '结算项目',
          projectCode: 'PJ-002',
          status: 'SETTLING',
        },
        {
          projectId: '2',
          projectName: '结算项目（第二项）',
          projectCode: 'PJ-002-2',
          status: 'PENDING',
        },
      ],
    } as DashboardDataByRole['bm']

    expect(dashboardActivityItems('bm', business)).toEqual([
      {
        id: 'settlement-2-0',
        title: '结算项目',
        meta: 'PJ-002',
        status: 'SETTLING',
      },
      {
        id: 'settlement-2-1',
        title: '结算项目（第二项）',
        meta: 'PJ-002-2',
        status: 'PENDING',
      },
    ])
  })

  it('counts only imminent business contracts and unfinished settlements in health', () => {
    const dateAfter = (days: number) => {
      const date = new Date()
      date.setDate(date.getDate() + days)
      return date.toISOString().slice(0, 10)
    }
    const business = {
      recentChanges: [
        { endDate: dateAfter(20) },
        { endDate: dateAfter(60) },
        { endDate: dateAfter(365) },
      ],
      settlementItems: [
        { status: 'FINALIZED' },
        { status: 'COMPLETED' },
        { status: 'CLOSED' },
        { status: 'PENDING' },
      ],
    } as DashboardDataByRole['bm']

    expect(dashboardHealth('bm', business)).toEqual({ score: 82, label: '关注', tone: 'warning' })
  })
})
