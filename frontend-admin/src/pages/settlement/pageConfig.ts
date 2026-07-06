export const SETTLEMENT_GRID_COLUMNS = [
  {
    field: 'settlementCode',
    title: '结算编号',
    minWidth: 160,
    slots: { default: 'settlementCode' },
  },
  { field: 'projectName', title: '项目', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同', minWidth: 150, ellipsis: true },
  {
    field: 'settlementAmount',
    title: '结算金额(万)',
    width: 140,
    align: 'right' as const,
    slots: { default: 'settlementAmount' },
  },
  {
    field: 'settlementStatus',
    title: '状态',
    width: 100,
    slots: { default: 'settlementStatus' },
  },
  { field: 'createdAt', title: '创建时间', width: 160 },
  { title: '操作', width: 76, slots: { default: 'ops' } },
] as const

export const SETTLEMENT_STATUS_COLOR_MAP: Record<string, string> = {
  DRAFT: '#f59e0b',
  FINALIZED: '#31c48d',
  CANCELLED: '#ef4444',
}
