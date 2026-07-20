import type {
  AlertBatchResult,
  AlertProcessStatus,
  AlertSeverity,
} from '@cgc-pms/frontend-contracts'

export const ALERT_SEVERITY_LABELS: Record<AlertSeverity, string> = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

export const ALERT_STATUS_LABELS: Record<AlertProcessStatus, string> = {
  OPEN: '待处理',
  PROCESSED: '已处理',
  ARCHIVED: '已归档',
  INVALID: '已失效',
}

export function severityTone(severity: AlertSeverity) {
  if (severity === 'HIGH') return 'danger' as const
  if (severity === 'MEDIUM') return 'warning' as const
  return 'info' as const
}

export function alertStatusLabel(status?: string): string {
  return ALERT_STATUS_LABELS[status as AlertProcessStatus] ?? '其他状态'
}

export function batchResultMessage(result: AlertBatchResult): string {
  if (!result.failed) return `已成功处理 ${result.success} 项`
  return `成功 ${result.success} 项，失败 ${result.failed} 项`
}

export function alertRuleLabel(value: string): string {
  const labels: Record<string, string> = {
    DYNAMIC_COST_EXCEEDS_TARGET: '动态成本超目标',
    MATERIAL_EXCEEDS_BUDGET: '材料超预算',
    SUBCONTRACT_EXCEEDS_CONTRACT: '分包超合同',
    CONTRACT_OVERDUE: '合同超期',
    CONTRACT_EXPIRING: '合同到期',
    PAYMENT_EXCEEDS_RATIO: '付款超比例',
    PURCHASE_DELIVERY_OVERDUE: '采购交期逾期',
    CASH_JOURNAL_ARCHIVE_OVERDUE: '资金流水归档逾期',
  }
  return labels[value] ?? '其他预警'
}
