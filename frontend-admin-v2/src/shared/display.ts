export function formatAmount(value: string | number | null | undefined): string {
  const normalized = value == null ? '' : String(value).trim()
  if (!normalized) return '—'
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(normalized)
  if (!match) return normalized
  const [, rawSign, rawInteger, rawFraction = ''] = match
  let digits = `${rawInteger}${rawFraction.padEnd(2, '0').slice(0, 2)}`
  if ((rawFraction[2] ?? '0') >= '5') {
    const rounded = digits.split('')
    for (let index = rounded.length - 1; index >= 0; index -= 1) {
      if (rounded[index] !== '9') {
        rounded[index] = String.fromCharCode(rounded[index]!.charCodeAt(0) + 1)
        digits = rounded.join('')
        break
      }
      rounded[index] = '0'
      if (index === 0) digits = `1${rounded.join('')}`
    }
  }
  const integer = digits.slice(0, -2).replace(/^0+(?=\d)/, '') || '0'
  const fraction = digits.slice(-2).padStart(2, '0')
  const isZero = /^0+$/.test(integer) && /^0+$/.test(fraction)
  const sign = rawSign && !isZero ? '−' : ''
  return `¥${sign}${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction}`
}

export function formatDecimal(value: string | null | undefined): string {
  const formatted = formatAmount(value)
  return formatted.startsWith('¥')
    ? formatted.slice(1).replaceAll(',', '').replace('−', '-')
    : formatted
}

const DASHBOARD_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '进行中',
  ABNORMAL: '异常',
  APPROVED: '已通过',
  APPROVING: '审批中',
  ARCHIVED: '已归档',
  BLOCKED: '已阻塞',
  CLOSED: '已关闭',
  COMPLETED: '已完成',
  COLLECTED: '已回款',
  CONFIRMED: '已确认',
  CREDITED: '已冲减',
  DRAFT: '草稿',
  FAILED: '失败',
  FULLY_ALLOCATED: '已全额分配',
  INVALID: '已失效',
  OPEN: '待处理',
  OVERDUE: '已逾期',
  PAID: '已付款',
  PARTIALLY_PAID: '部分付款',
  PARTIALLY_COLLECTED: '部分回款',
  PREPARING: '筹备',
  PENDING: '待处理',
  PROCESSED: '已处理',
  PROCESSING: '处理中',
  RECEIVABLE_CREATED: '已生成应收',
  REJECTED: '已驳回',
  REVERSED: '已冲销',
  RUNNING: '进行中',
  SUCCESS: '已完成',
  UNVERIFIED: '待核验',
  VERIFIED: '已核验',
  VOIDED: '已作废',
}

export function dashboardStatusLabel(value: string | null | undefined): string {
  const normalized = value?.trim()
  if (!normalized) return '—'
  return DASHBOARD_STATUS_LABELS[normalized.toUpperCase()] ?? normalized
}
