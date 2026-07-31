import type { SubjectBreakdown } from '@/types/dashboard'
import { toNum } from './formatUtils'

/* ── Shared compact series helpers ── */

interface CompactSeries {
  name: string
  type: string
  smooth?: boolean
  data: number[]
  symbol?: string
  symbolSize?: number
  lineStyle?: { width: number; color: string }
  itemStyle: { color: string; borderRadius?: [number, number, number, number] }
  barMaxWidth?: number
}

const compactLine = (name: string, data: number[], color = '#3b82f6'): CompactSeries => ({
  name,
  type: 'line',
  smooth: true,
  data,
  symbol: 'circle',
  symbolSize: 6,
  lineStyle: { width: 2, color },
  itemStyle: { color },
})

const compactBar = (name: string, data: number[], color = '#3b82f6'): CompactSeries => ({
  name,
  type: 'bar',
  data,
  barMaxWidth: 28,
  itemStyle: { color, borderRadius: [6, 6, 0, 0] },
})

/* ── Chart option builders ── */

interface ChartOption {
  tooltip: { trigger: 'axis' | 'item' }
  grid?: { left: number; right: number; top: number; bottom: number; containLabel?: boolean }
  legend?: {
    bottom: number
    itemWidth: number
    itemHeight: number
    textStyle: { fontSize: number }
  }
  color?: string[]
  xAxis?: { type: string; data: string[]; axisLabel: { fontSize: number } }
  yAxis?: { type: string; axisLabel: { fontSize: number } }
  series:
    | CompactSeries[]
    | {
        type: string
        radius: [string, string]
        label: { show: boolean }
        data: { name: string; value: number }[]
      }[]
}

function axisOption(categories: string[], series: CompactSeries[]): ChartOption {
  return {
    tooltip: { trigger: 'axis' as const },
    grid: { left: 48, right: 18, top: 24, bottom: 28, containLabel: true },
    xAxis: { type: 'category' as const, data: categories, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value' as const, axisLabel: { fontSize: 11 } },
    series,
  }
}

function donutOption(data: { name: string; value: number }[]): ChartOption {
  return {
    color: ['#3b82f6', '#22c55e', '#f59e0b', '#8b5cf6', '#ef4444'],
    tooltip: { trigger: 'item' as const },
    legend: { bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    series: [{ type: 'pie', radius: ['48%', '70%'], label: { show: false }, data }],
  }
}

/* ── PM (Project Manager) ── */

export function pmBusinessOverviewOption(data: {
  pendingTaskCount?: number
  laggingProjectCount?: number
  pendingApprovalCount?: number
  expiringContractCount?: number
}) {
  return axisOption(
    ['待办', '滞后', '审批', '临期'],
    [
      compactBar('数量', [
        data.pendingTaskCount ?? 0,
        data.laggingProjectCount ?? 0,
        data.pendingApprovalCount ?? 0,
        data.expiringContractCount ?? 0,
      ]),
      compactLine('趋势', [3, 6, 4, 7], '#22c55e'),
    ],
  )
}

export function pmCostCompositionOption() {
  return donutOption([
    { name: '人工', value: 32 },
    { name: '材料', value: 44 },
    { name: '机械', value: 16 },
    { name: '其他', value: 8 },
  ])
}

export function pmFundingOverviewOption() {
  return axisOption(
    ['周一', '周二', '周三', '周四', '周五'],
    [compactLine('资金收支', [2, 4, 3, 6, 5])],
  )
}

/* ── BM (Business Manager) ── */

export function bmBusinessOption(data: {
  totalContractAmount?: string
  contractChangeAmount?: string
  varOrderAmount?: string
  subMeasureAmount?: string
}) {
  return axisOption(
    ['合同', '变更', '签证', '分包'],
    [
      compactBar('金额', [
        toNum(data.totalContractAmount),
        toNum(data.contractChangeAmount),
        toNum(data.varOrderAmount),
        toNum(data.subMeasureAmount),
      ]),
      compactLine('进度', [35, 48, 42, 64], '#22c55e'),
    ],
  )
}

export function bmChangeOption(data: {
  contractChangeAmount?: string
  varOrderAmount?: string
  subMeasureAmount?: string
}) {
  return donutOption([
    { name: '合同变更', value: toNum(data.contractChangeAmount) || 36 },
    { name: '签证变更', value: toNum(data.varOrderAmount) || 24 },
    { name: '分包计量', value: toNum(data.subMeasureAmount) || 40 },
  ])
}

export function bmSettlementOption() {
  return axisOption(
    ['立项', '审核', '结算', '支付'],
    [compactLine('结算收付', [28, 42, 58, 72], '#14b8c7')],
  )
}

/* ── COST ── */

export function costExecutionOption(data: {
  targetCost?: string
  dynamicCost?: string
  actualCost?: string
  expectedProfit?: string
}) {
  return axisOption(
    ['目标', '动态', '实际', '利润'],
    [
      compactBar('成本', [
        toNum(data.targetCost),
        toNum(data.dynamicCost),
        toNum(data.actualCost),
        toNum(data.expectedProfit),
      ]),
    ],
  )
}

export function costCompositionOption(subs: SubjectBreakdown[]) {
  return donutOption(
    subs.length
      ? subs.slice(0, 5).map((s) => ({ name: s.costSubjectName, value: toNum(s.dynamicCost) }))
      : [
          { name: '人工', value: 28 },
          { name: '材料', value: 46 },
          { name: '机械', value: 18 },
          { name: '其他', value: 8 },
        ],
  )
}

export function costDeviationTrendOption(data: {
  targetCost?: string
  contractLockedCost?: string
  actualCost?: string
  costDeviation?: string
}) {
  return axisOption(
    ['目标', '锁定', '实际', '偏差'],
    [
      compactLine(
        '偏差趋势',
        [
          toNum(data.targetCost),
          toNum(data.contractLockedCost),
          toNum(data.actualCost),
          toNum(data.costDeviation),
        ],
        '#f59e0b',
      ),
    ],
  )
}

export function costBarOption(subs: SubjectBreakdown[]) {
  return axisOption(
    subs.map((s) => s.costSubjectName),
    [
      compactBar(
        '成本目标',
        subs.map((s) => toNum(s.targetCost)),
        '#3b82f6',
      ),
      compactBar(
        '实际成本',
        subs.map((s) => toNum(s.actualCost)),
        '#ef4444',
      ),
    ],
  )
}

/* ── FINANCE ── */

export function financePaymentOption(data: {
  pendingPaymentAmount?: string
  pendingPaymentCount?: number
  approvedUnpaidAmount?: string
  warrantyExpiringAmount?: string
}) {
  return axisOption(
    ['待付', '笔数', '已审', '质保'],
    [
      compactBar('付款', [
        toNum(data.pendingPaymentAmount),
        data.pendingPaymentCount ?? 0,
        toNum(data.approvedUnpaidAmount),
        toNum(data.warrantyExpiringAmount),
      ]),
    ],
  )
}

export function financeStructureOption(data: {
  pendingPaymentAmount?: string
  approvedUnpaidAmount?: string
  overRatioAmount?: string
  warrantyExpiringAmount?: string
}) {
  return donutOption([
    { name: '待付款', value: toNum(data.pendingPaymentAmount) || 42 },
    { name: '已审批未支付', value: toNum(data.approvedUnpaidAmount) || 24 },
    { name: '超比例', value: toNum(data.overRatioAmount) || 12 },
    { name: '质保金', value: toNum(data.warrantyExpiringAmount) || 8 },
  ])
}

export function financeRiskOption() {
  return axisOption(
    ['本周', '下周', '本月', '下月'],
    [compactLine('资金风险', [12, 18, 10, 22], '#ef4444')],
  )
}

/* ── MGMT ── */

export function mgmtOverviewOption(data: {
  activeProjectCount?: number
  totalContractAmount?: string
  totalDynamicCost?: string
  totalExpectedProfit?: string
}) {
  return axisOption(
    ['项目', '合同', '成本', '利润'],
    [
      compactBar('总览', [
        data.activeProjectCount ?? 0,
        toNum(data.totalContractAmount),
        toNum(data.totalDynamicCost),
        toNum(data.totalExpectedProfit),
      ]),
    ],
  )
}

export function mgmtRiskOption(data: {
  totalPendingTaskCount?: number
  totalRiskCount?: number
  overdueItemCount?: number
}) {
  return donutOption([
    { name: '待办', value: data.totalPendingTaskCount ?? 0 },
    { name: '风险', value: data.totalRiskCount ?? 0 },
    { name: '逾期', value: data.overdueItemCount ?? 0 },
  ])
}

export function mgmtTrendOption(data: {
  totalContractAmount?: string
  totalDynamicCost?: string
  totalExpectedProfit?: string
  totalPaidAmount?: string
}) {
  return axisOption(
    ['收入', '成本', '利润', '付款'],
    [
      compactLine('经营趋势', [
        toNum(data.totalContractAmount),
        toNum(data.totalDynamicCost),
        toNum(data.totalExpectedProfit),
        toNum(data.totalPaidAmount),
      ]),
    ],
  )
}
