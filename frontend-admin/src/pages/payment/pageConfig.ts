export const APPROVAL_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

export const APPROVAL_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
}

export const PAYMENT_GRID_COLUMNS = [
  { field: 'applyCode', title: '申请编号', minWidth: 150, ellipsis: true },
  { field: 'projectName', title: '项目', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同', minWidth: 150, ellipsis: true },
  { field: 'partnerName', title: '合作方', minWidth: 140, ellipsis: true },
  {
    field: 'applyAmount',
    title: '申请金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'applyAmount' },
  },
  {
    field: 'approvedAmount',
    title: '审批金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'approvedAmount' },
  },
  {
    field: 'actualPayAmount',
    title: '实付金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'actualPayAmount' },
  },
  { field: 'payType', title: '付款类型', width: 108, slots: { default: 'payType' } },
  { field: 'payStatus', title: '支付状态', width: 108, slots: { default: 'payStatus' } },
  { field: 'approvalStatus', title: '审批状态', width: 108, slots: { default: 'approvalStatus' } },
  { title: '操作', width: 76, slots: { default: 'action' } },
] as const
