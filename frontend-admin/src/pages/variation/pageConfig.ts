export const VAR_TYPE_OPTIONS = [
  { label: '设计变更', value: '设计变更' },
  { label: '现场签证', value: '现场签证' },
  { label: '索赔', value: '索赔' },
  { label: '洽商', value: '洽商' },
] as const

export const DIRECTION_OPTIONS = [
  { label: '成本', value: 'COST' },
  { label: '收入/索赔', value: 'INCOME' },
] as const

export const VAR_TYPE_LABEL: Record<string, string> = {
  设计变更: '设计变更',
  现场签证: '现场签证',
  索赔: '索赔',
  洽商: '洽商',
}

export const VAR_TYPE_COLOR: Record<string, string> = {
  设计变更: 'blue',
  现场签证: 'orange',
  索赔: 'purple',
  洽商: 'cyan',
}

export const APPROVAL_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

export const APPROVAL_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'processing',
  APPROVING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
}

export function buildVariationGridColumns(codeColumnWidth: number) {
  return [
    {
      field: 'varCode',
      title: '变更编号',
      width: codeColumnWidth,
      minWidth: 128,
      showOverflow: false,
      slots: { default: 'varCode' },
    },
    { field: 'varName', title: '变更名称', minWidth: 150, ellipsis: true },
    { field: 'varType', title: '变更类型', width: 108, slots: { default: 'varType' } },
    { field: 'direction', title: '方向', width: 70, slots: { default: 'direction' } },
    { field: 'projectName', title: '项目名称', minWidth: 150, ellipsis: true },
    { field: 'contractName', title: '合同名称', minWidth: 150, ellipsis: true },
    { field: 'partnerName', title: '合作方', minWidth: 140, ellipsis: true },
    {
      field: 'reportedAmount',
      title: '上报金额',
      width: 118,
      align: 'right' as const,
      slots: { default: 'reportedAmount' },
    },
    {
      field: 'approvedAmount',
      title: '审定金额',
      width: 118,
      align: 'right' as const,
      slots: { default: 'approvedAmount' },
    },
    {
      field: 'confirmedAmount',
      title: '确认金额',
      width: 118,
      align: 'right' as const,
      slots: { default: 'confirmedAmount' },
    },
    {
      field: 'approvalStatus',
      title: '审批状态',
      width: 108,
      slots: { default: 'approvalStatus' },
    },
    {
      key: 'ops',
      title: '操作',
      width: 76,
      align: 'center' as const,
      headerAlign: 'center' as const,
      slots: { default: 'ops' },
    },
  ] as const
}
