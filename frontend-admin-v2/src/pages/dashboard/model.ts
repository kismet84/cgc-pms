import type { DashboardDataByRole, DashboardRole } from '@cgc-pms/frontend-contracts'

export interface DashboardMetric {
  label: string
  value: string
  tone?: 'default' | 'positive' | 'warning' | 'danger' | 'blue' | 'teal'
}

export interface DashboardListItem {
  id: string
  title: string
  meta: string
  value?: string
  status?: string
  riskLevel?: DashboardRiskLevel
}

export type DashboardRiskLevel = 'high' | 'medium' | 'low' | 'other'

export const DASHBOARD_RISK_LABELS: Record<DashboardRiskLevel, string> = {
  high: '高',
  medium: '中',
  low: '低',
  other: '其他',
}

export interface DashboardHealth {
  score: number
  label: '稳健' | '关注' | '风险'
  tone: 'success' | 'warning' | 'danger'
}

export const DASHBOARD_ROLE_LABELS: Record<DashboardRole, string> = {
  pm: '项目经理',
  bm: '商务经理',
  cost: '成本经理',
  purchase: '采购经理',
  production: '生产经理',
  chiefEngineer: '总工程师',
  finance: '财务',
  mgmt: '管理层',
}

export function formatAmount(value: string | null | undefined): string {
  const normalized = value?.trim()
  if (!normalized) return '—'
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(normalized)
  if (!match) return normalized
  const [, rawSign, rawInteger, rawFraction] = match
  const integer = rawInteger!.replace(/^0+(?=\d)/, '')
  const fraction = rawFraction ? rawFraction.padEnd(2, '0') : '00'
  const isZero = /^0+$/.test(integer) && /^0+$/.test(fraction)
  const sign = rawSign && !isZero ? '−' : ''
  return `¥${sign}${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction}`
}

const DASHBOARD_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '进行中',
  APPROVED: '已通过',
  ARCHIVED: '已归档',
  BLOCKED: '已阻塞',
  CLOSED: '已关闭',
  COMPLETED: '已完成',
  CONFIRMED: '已确认',
  DRAFT: '草稿',
  FAILED: '失败',
  INVALID: '已失效',
  OPEN: '待处理',
  OVERDUE: '已逾期',
  PENDING: '待处理',
  PROCESSED: '已处理',
  PROCESSING: '处理中',
  REJECTED: '已驳回',
  RUNNING: '进行中',
  SUCCESS: '已完成',
}

export function dashboardStatusLabel(value: string | null | undefined): string {
  const normalized = value?.trim()
  if (!normalized) return '—'
  return DASHBOARD_STATUS_LABELS[normalized.toUpperCase()] ?? normalized
}

export function formatDashboardMessage(message: string): string {
  return message.replace(/-?\d{1,18}\.\d{2}(?!\d)/g, (value) => formatAmount(value))
}

export function formatRatio(value: string | null | undefined): string {
  const normalized = value?.trim()
  if (!normalized) return '—'
  return normalized.endsWith('%') ? normalized : `${normalized}%`
}

export function compactDashboardValue(value: string): { value: string; unit: string } {
  if (!value.startsWith('¥')) return { value, unit: '' }
  const numeric = Number(value.slice(1).replaceAll(',', '').replace('−', '-'))
  if (!Number.isFinite(numeric)) return { value, unit: '' }
  return {
    value: (numeric / 10000).toLocaleString('zh-CN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }),
    unit: '万元',
  }
}

export function normalizeGaugeValue(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0
}

export function deriveDashboardHealth(
  riskCount: number,
  overdueCount: number,
  pendingCount: number,
): DashboardHealth {
  const safeRisk = Math.max(0, riskCount)
  const safeOverdue = Math.max(0, overdueCount)
  const safePending = Math.max(0, pendingCount)
  const score = Math.max(0, Math.min(100, 100 - safeRisk * 8 - safeOverdue * 5 - safePending * 2))
  if (score >= 85) return { score, label: '稳健', tone: 'success' }
  if (score >= 70) return { score, label: '关注', tone: 'warning' }
  return { score, label: '风险', tone: 'danger' }
}

export function dashboardMetrics(
  role: DashboardRole,
  data: DashboardDataByRole[DashboardRole],
): DashboardMetric[] {
  switch (role) {
    case 'pm': {
      const item = data as DashboardDataByRole['pm']
      return [
        { label: '待处理任务', value: String(item.pendingTaskCount) },
        { label: '滞后项目', value: String(item.laggingProjectCount), tone: 'warning' },
        { label: '待审批', value: String(item.pendingApprovalCount) },
        { label: '合同临期', value: String(item.expiringContractCount), tone: 'danger' },
      ]
    }
    case 'bm': {
      const item = data as DashboardDataByRole['bm']
      return [
        { label: '合同总额', value: formatAmount(item.totalContractAmount) },
        { label: '合同变更', value: formatAmount(item.contractChangeAmount) },
        { label: '签证金额', value: formatAmount(item.varOrderAmount) },
        { label: '结算进度', value: formatRatio(item.settlementProgress) },
      ]
    }
    case 'cost': {
      const item = data as DashboardDataByRole['cost']
      return [
        { label: '目标成本', value: formatAmount(item.targetCost), tone: 'blue' },
        { label: '动态成本', value: formatAmount(item.dynamicCost), tone: 'teal' },
        {
          label: '成本偏差',
          value: formatAmount(item.costDeviation),
          tone: Number(item.costDeviation) > 0 ? 'danger' : 'positive',
        },
        {
          label: '预计利润',
          value: formatAmount(item.expectedProfit),
          tone: Number(item.expectedProfit) < 0 ? 'danger' : 'positive',
        },
      ]
    }
    case 'purchase': {
      const item = data as DashboardDataByRole['purchase']
      return [
        { label: '采购申请', value: String(item.pendingRequestCount) },
        { label: '执行订单', value: String(item.activeOrderCount) },
        { label: '逾期交付', value: String(item.overdueDeliveryCount), tone: 'danger' },
        { label: '订单总额', value: formatAmount(item.totalOrderAmount) },
      ]
    }
    case 'production': {
      const item = data as DashboardDataByRole['production']
      return [
        { label: '到货验收', value: String(item.receiptCount) },
        { label: '领料记录', value: String(item.requisitionCount) },
        { label: '待出库', value: String(item.pendingStockOutCount), tone: 'warning' },
        { label: '确认计量', value: formatAmount(item.confirmedMeasureAmount) },
      ]
    }
    case 'chiefEngineer': {
      const item = data as DashboardDataByRole['chiefEngineer']
      return [
        { label: '待审查', value: String(item.pendingReviewCount) },
        { label: '待协调', value: String(item.pendingCoordinationCount) },
        { label: '未闭环问题', value: String(item.openIssueCount), tone: 'warning' },
        { label: '逾期事项', value: String(item.overdueCount), tone: 'danger' },
      ]
    }
    case 'finance': {
      const item = data as DashboardDataByRole['finance']
      return [
        { label: '审批中付款', value: formatAmount(item.pendingPaymentAmount) },
        { label: '已批未付', value: formatAmount(item.approvedUnpaidAmount), tone: 'warning' },
        { label: '预算执行率', value: formatRatio(item.budgetExecutionRate), tone: 'blue' },
        { label: '公司资金余额', value: formatAmount(item.cashBalance) },
      ]
    }
    case 'mgmt': {
      const item = data as DashboardDataByRole['mgmt']
      return [
        { label: '在建项目', value: String(item.activeProjectCount) },
        { label: '合同总额', value: formatAmount(item.totalContractAmount) },
        { label: '动态成本', value: formatAmount(item.totalDynamicCost) },
        { label: '预计利润', value: formatAmount(item.totalExpectedProfit), tone: 'positive' },
      ]
    }
  }
}

export function dashboardHealth(
  role: DashboardRole,
  data: DashboardDataByRole[DashboardRole],
): DashboardHealth {
  switch (role) {
    case 'pm': {
      const item = data as DashboardDataByRole['pm']
      return deriveDashboardHealth(
        item.laggingProjectCount + item.expiringContractCount,
        0,
        item.pendingTaskCount + item.pendingApprovalCount,
      )
    }
    case 'purchase': {
      const item = data as DashboardDataByRole['purchase']
      return deriveDashboardHealth(
        item.lowStockItemCount,
        item.overdueDeliveryCount,
        item.pendingRequestCount + item.pendingReceiptCount,
      )
    }
    case 'production': {
      const item = data as DashboardDataByRole['production']
      return deriveDashboardHealth(item.lowStockItemCount, 0, item.pendingStockOutCount)
    }
    case 'chiefEngineer': {
      const item = data as DashboardDataByRole['chiefEngineer']
      return deriveDashboardHealth(
        item.openIssueCount,
        item.overdueCount,
        item.pendingReviewCount + item.pendingCoordinationCount,
      )
    }
    case 'finance': {
      const item = data as DashboardDataByRole['finance']
      return deriveDashboardHealth(
        item.overRatioPayments.length,
        0,
        item.pendingPaymentCount + item.pendingPayments.length,
      )
    }
    case 'mgmt': {
      const item = data as DashboardDataByRole['mgmt']
      return deriveDashboardHealth(
        item.totalRiskCount,
        item.overdueItems.length,
        item.totalPendingTaskCount,
      )
    }
    case 'cost': {
      const item = data as DashboardDataByRole['cost']
      return deriveDashboardHealth(
        item.overBudgetAlerts.length,
        item.overdueItems.length,
        item.pendingPayments.length,
      )
    }
    case 'bm': {
      const item = data as DashboardDataByRole['bm']
      return deriveDashboardHealth(item.recentChanges.length, 0, item.settlementItems.length)
    }
  }
}

export function primaryRiskItems(
  role: DashboardRole,
  data: DashboardDataByRole[DashboardRole],
): DashboardListItem[] {
  switch (role) {
    case 'pm': {
      const item = data as DashboardDataByRole['pm']
      const personalTaskIds = new Set(item.pendingTasks.map((task) => task.taskId))
      return [
        ...item.laggingProjects.map((project) => ({
          id: `lagging-${project.projectId}`,
          title: project.projectName,
          meta: `项目 ${project.projectCode}`,
          value: formatAmount(project.costDeviation),
          status: `${project.riskCount} 项风险`,
          riskLevel: 'high' as const,
        })),
        ...item.expiringContracts.map((contract) => ({
          id: `contract-${contract.contractId}`,
          title: contract.contractName,
          meta: contract.contractCode,
          value: formatAmount(contract.currentAmount),
          status: '合同临期',
          riskLevel: 'medium' as const,
        })),
        ...item.pendingApprovals
          .filter((task) => !personalTaskIds.has(task.taskId))
          .map((task) => ({ ...taskItem(task), riskLevel: 'low' as const })),
        ...item.pendingTasks.map((task) => ({
          ...taskItem(task),
          riskLevel: 'other' as const,
        })),
      ]
    }
    case 'bm':
      return (data as DashboardDataByRole['bm']).recentChanges.map((item) => ({
        id: item.contractId,
        title: item.contractName,
        meta: [item.projectName, item.contractCode].filter(Boolean).join(' · '),
        value: formatAmount(item.currentAmount),
        status: item.contractStatus,
        riskLevel: contractRiskLevel(item.endDate),
      }))
    case 'cost':
      return (data as DashboardDataByRole['cost']).overBudgetAlerts.map((item, index) => ({
        id: `${item.projectId}-${index}`,
        title: item.message,
        meta: item.projectName,
        status: `${item.severity} · ${item.alertType}`,
        riskLevel: alertRiskLevel(item.severity),
      }))
    case 'purchase': {
      const item = data as DashboardDataByRole['purchase']
      return [
        ...item.overdueOrders.map((order) => ({
          ...businessItem(order),
          riskLevel: overdueRiskLevel(order.overdueDays),
        })),
        ...item.pendingReceipts.map((receipt) => ({
          ...businessItem(receipt),
          riskLevel: 'low' as const,
        })),
        ...item.recentRequests.map((request) => ({
          ...businessItem(request),
          riskLevel: 'other' as const,
        })),
      ]
    }
    case 'production':
      return (data as DashboardDataByRole['production']).recentSubMeasures.map((item) => ({
        ...businessItem(item),
        riskLevel: statusRiskLevel(item.status),
      }))
    case 'chiefEngineer': {
      const item = data as DashboardDataByRole['chiefEngineer']
      const overdueIds = new Set(item.overdueItems.map((entry) => entry.sourceId))
      return [
        ...item.overdueItems.map((entry) => ({
          ...businessItem(entry),
          riskLevel: overdueRiskLevel(entry.overdueDays),
        })),
        ...item.openIssues
          .filter((entry) => !overdueIds.has(entry.sourceId))
          .map((entry) => ({ ...businessItem(entry), riskLevel: 'low' as const })),
        ...[...item.pendingReviews, ...item.pendingCoordinations]
          .filter((entry) => !overdueIds.has(entry.sourceId))
          .map((entry) => ({ ...businessItem(entry), riskLevel: 'other' as const })),
      ]
    }
    case 'finance': {
      const finance = data as DashboardDataByRole['finance']
      const overRatioPayments = finance.overRatioPayments.filter(
        (item, index, all) =>
          all.findIndex((candidate) => candidate.contractId === item.contractId) === index,
      )
      const overRatioIds = new Set(overRatioPayments.map((item) => item.payRecordId))
      const payments = [...overRatioPayments, ...finance.pendingPayments].filter(
        (item, index, all) =>
          all.findIndex((candidate) => candidate.payRecordId === item.payRecordId) === index,
      )
      return payments.map((item) => ({
        id: item.payRecordId,
        title: item.contractName || '待处理付款',
        meta: [item.projectName, item.partnerName].filter(Boolean).join(' · '),
        value: formatAmount(item.payAmount),
        status: item.payStatus,
        riskLevel: overRatioIds.has(item.payRecordId)
          ? 'high'
          : financePaymentRiskLevel(item.payStatus),
      }))
    }
    case 'mgmt':
      return (data as DashboardDataByRole['mgmt']).majorRisks.map((item, index) => ({
        id: `${item.projectId}-${index}`,
        title: item.message,
        meta: item.projectName,
        status: `${item.severity} · ${item.alertType}`,
        riskLevel: alertRiskLevel(item.severity),
      }))
  }
}

export function alertRiskLevel(severity: string): DashboardRiskLevel {
  const normalized = severity.toUpperCase()
  if (['CRITICAL', 'HIGH'].includes(normalized)) return 'high'
  if (normalized === 'MEDIUM') return 'medium'
  if (normalized === 'LOW') return 'low'
  return 'other'
}

function overdueRiskLevel(overdueDays?: number): DashboardRiskLevel {
  if ((overdueDays ?? 0) >= 7) return 'high'
  if ((overdueDays ?? 0) > 0) return 'medium'
  return 'low'
}

function contractRiskLevel(endDate?: string): DashboardRiskLevel {
  const timestamp = Date.parse(endDate ?? '')
  if (Number.isNaN(timestamp)) return 'other'
  const remainingDays = (timestamp - Date.now()) / (24 * 60 * 60 * 1000)
  if (remainingDays <= 30) return 'high'
  if (remainingDays <= 90) return 'medium'
  if (remainingDays <= 180) return 'low'
  return 'other'
}

function statusRiskLevel(status?: string): DashboardRiskLevel {
  const normalized = status?.toUpperCase() ?? ''
  if (['REJECTED', 'FAILED', 'OVERDUE', 'BLOCKED'].includes(normalized)) return 'high'
  if (['PENDING', 'PROCESSING', 'RUNNING'].includes(normalized)) return 'medium'
  if (['APPROVED', 'CONFIRMED', 'SUCCESS'].includes(normalized)) return 'low'
  return 'other'
}

function financePaymentRiskLevel(status?: string): DashboardRiskLevel {
  const normalized = status?.toUpperCase() ?? ''
  if (['FAILED', 'REJECTED', 'OVERDUE', 'BLOCKED'].includes(normalized)) return 'high'
  if (normalized === 'PROCESSING') return 'medium'
  if (normalized === 'PENDING') return 'low'
  return 'other'
}

export function dashboardActivityItems(
  role: DashboardRole,
  data: DashboardDataByRole[DashboardRole],
): DashboardListItem[] {
  switch (role) {
    case 'pm': {
      const item = data as DashboardDataByRole['pm']
      return [
        ...item.pendingTasks.map((task) => ({
          id: task.taskId,
          title: task.title,
          meta: task.projectName,
          value: task.amount ? formatAmount(task.amount) : undefined,
          status: task.taskStatus,
        })),
        ...item.expiringContracts.map((contract) => ({
          id: contract.contractId,
          title: contract.contractName,
          meta: `${contract.contractCode} · ${contract.endDate}`,
          value: formatAmount(contract.currentAmount),
          status: '即将到期',
        })),
      ]
    }
    case 'bm': {
      const item = data as DashboardDataByRole['bm']
      return item.recentChanges.map((contract) => ({
        id: contract.contractId,
        title: contract.contractName,
        meta: [contract.projectName, contract.contractCode].filter(Boolean).join(' · '),
        value: formatAmount(contract.currentAmount),
        status: contract.contractStatus,
      }))
    }
    case 'purchase': {
      const item = data as DashboardDataByRole['purchase']
      return [...item.purchaseOrders, ...item.recentRequests, ...item.pendingReceipts].map(
        businessItem,
      )
    }
    case 'production': {
      const item = data as DashboardDataByRole['production']
      return [...item.recentReceipts, ...item.recentRequisitions, ...item.recentSubMeasures].map(
        businessItem,
      )
    }
    case 'chiefEngineer': {
      const item = data as DashboardDataByRole['chiefEngineer']
      return [...item.pendingReviews, ...item.pendingCoordinations, ...item.openIssues].map(
        businessItem,
      )
    }
    case 'mgmt':
      return (data as DashboardDataByRole['mgmt']).projectRankings.map((project) => ({
        id: project.projectId,
        title: project.projectName,
        meta: project.projectCode,
        value: formatAmount(project.expectedProfit),
        status: project.status,
      }))
    case 'cost':
    case 'finance':
      return []
  }
}

function businessItem(item: {
  sourceType: string
  sourceId: string
  code?: string | null
  title?: string | null
  itemSummary?: string | null
  projectName?: string | null
  amount?: string | null
  status?: string | null
}): DashboardListItem {
  return {
    id: `${item.sourceType}-${item.sourceId}`,
    title: item.title?.trim() || item.itemSummary?.trim() || item.code?.trim() || '未命名事项',
    meta: item.projectName?.trim() || '全部项目',
    value: formatAmount(item.amount),
    status: item.status ?? undefined,
  }
}

function taskItem(item: DashboardDataByRole['pm']['pendingTasks'][number]): DashboardListItem {
  return {
    id: `task-${item.taskId}`,
    title: item.title,
    meta: item.projectName,
    value: item.amount ? formatAmount(item.amount) : undefined,
    status: item.taskStatus,
  }
}
