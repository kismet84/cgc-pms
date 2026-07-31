import {
  buildActionColumn,
  buildAmountColumn,
  buildStatusColumn,
} from '@/composables/listTablePresets'

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
  buildAmountColumn('applyAmount', '申请金额'),
  buildAmountColumn('approvedAmount', '审批金额'),
  buildAmountColumn('actualPayAmount', '实付金额'),
  buildStatusColumn('payType', '付款类型'),
  buildStatusColumn('payStatus', '支付状态'),
  buildStatusColumn('approvalStatus', '审批状态'),
  buildActionColumn(),
] as const
