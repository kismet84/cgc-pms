export const LIST_COLUMN_WIDTH = {
  amount: 128,
  date: 112,
  dateTime: 160,
  status: 108,
  action: 76,
} as const

function toNumber(value: string | number | undefined): number | null {
  if (value == null || value === '') return null
  const parsed = typeof value === 'number' ? value : parseFloat(value)
  return Number.isFinite(parsed) ? parsed : null
}

export function formatWanAmount(
  value: string | number | undefined,
  fallback = '0.00',
): string {
  const parsed = toNumber(value)
  if (parsed == null) return fallback
  return (parsed / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

export function formatWanAmountWithUnit(
  value: string | number | undefined,
  fallback = '-',
): string {
  const amount = formatWanAmount(value, '')
  return amount ? `${amount} 万元` : fallback
}

export function formatCurrencyAmount(
  value: string | number | undefined,
  fallback = '-',
): string {
  const parsed = toNumber(value)
  if (parsed == null) return fallback
  return parsed.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

type ColumnExtra = Record<string, unknown>

export function buildAmountColumn(
  field: string,
  title: string,
  slot = field,
  extra: ColumnExtra = {},
) {
  return {
    field,
    title,
    width: LIST_COLUMN_WIDTH.amount,
    minWidth: LIST_COLUMN_WIDTH.amount,
    align: 'right' as const,
    slots: { default: slot },
    ...extra,
  }
}

export function buildDateColumn(field: string, title: string, extra: ColumnExtra = {}) {
  return {
    field,
    title,
    width: LIST_COLUMN_WIDTH.date,
    ...extra,
  }
}

export function buildDateTimeColumn(field: string, title: string, extra: ColumnExtra = {}) {
  return {
    field,
    title,
    width: LIST_COLUMN_WIDTH.dateTime,
    ...extra,
  }
}

export function buildStatusColumn(
  field: string,
  title: string,
  slot = field,
  extra: ColumnExtra = {},
) {
  return {
    field,
    title,
    width: LIST_COLUMN_WIDTH.status,
    slots: { default: slot },
    ...extra,
  }
}

export function buildActionColumn(slot = 'action', extra: ColumnExtra = {}) {
  return {
    title: '操作',
    width: LIST_COLUMN_WIDTH.action,
    fixed: 'right' as const,
    slots: { default: slot },
    ...extra,
  }
}
