import {
  buildActionColumn,
  buildAmountColumn,
  buildDateTimeColumn,
  buildStatusColumn,
} from '@/composables/listTablePresets'

export const SETTLEMENT_GRID_COLUMNS = [
  {
    field: 'settlementCode',
    title: '结算编号',
    minWidth: 160,
    slots: { default: 'settlementCode' },
  },
  { field: 'projectName', title: '项目', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同', minWidth: 150, ellipsis: true },
  buildAmountColumn('settlementAmount', '结算金额(万)', 'settlementAmount'),
  buildStatusColumn('settlementStatus', '状态', 'settlementStatus'),
  buildDateTimeColumn('createdAt', '创建时间'),
  buildActionColumn('ops'),
] as const

export const SETTLEMENT_STATUS_COLOR_MAP: Record<string, string> = {
  DRAFT: '#f59e0b',
  FINALIZED: '#31c48d',
  CANCELLED: '#ef4444',
}
