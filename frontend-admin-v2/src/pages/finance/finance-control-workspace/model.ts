import { formatAmount } from '@/shared/display'

export const pageSize = 10

export function pageSlice<T>(items: T[], pageNo: number): T[] {
  return items.slice((pageNo - 1) * pageSize, pageNo * pageSize)
}

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待处理',
  PENDING_ARCHIVE: '待归档',
  ARCHIVED: '已归档',
  REVERSED: '已冲销',
  SUBMITTED: '已提交',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  POSTED: '已过账',
  OPEN: '开放',
  CHECKING: '检查中',
  CLOSED: '已关账',
  REOPENED: '已反结账',
  PLANNED: '计划中',
  PARTIALLY_PAID: '部分付款',
  RESOLVED: '已解决',
  IGNORED: '已忽略',
  MATCHED: '已匹配',
  EXCEPTION: '异常',
  COMPLETED: '已完成',
  PROPOSED: '拟定',
  SUPERSEDED: '已滚动',
  BASE: '基准',
  OPTIMISTIC: '乐观',
  CONSERVATIVE: '保守',
  IN: '收入',
  OUT: '支出',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
  DEBIT: '借',
  CREDIT: '贷',
  PAYMENT_DUE: '付款到期',
  JOURNAL_ARCHIVE_OVERDUE: '日记账归档超时',
  INVOICE_MISSING: '发票缺失',
  ACCELERATE_COLLECTION: '加速回款',
  DEFER_PAYMENT: '延后付款',
  FUND_TRANSFER: '资金调拨',
  FINANCING: '融资补充',
}

export function label(value?: string | null): string {
  return value ? statusLabels[value] || '状态待确认' : '—'
}

export function amount(value?: string | null): string {
  return value == null ? '—' : formatAmount(value)
}

export function askReason(message: string): string {
  return window.prompt(message, 'V2工作台人工操作')?.trim() || ''
}
