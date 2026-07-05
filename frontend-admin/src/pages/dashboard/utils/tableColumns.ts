export const pmTaskCols = [
  { title: '任务标题', dataIndex: 'title', ellipsis: true },
  { title: '业务类型', dataIndex: 'businessType', width: 100 },
  { title: '接收时间', dataIndex: 'receivedAt', width: 150 },
]

export const pmProjectCols = [
  { title: '项目名称', dataIndex: 'projectName', ellipsis: true },
  { title: '项目编号', dataIndex: 'projectCode', width: 120 },
  { title: '状态', dataIndex: 'status', width: 88 },
]

export const pmContractCols = [
  { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
  { title: '到期日', dataIndex: 'endDate', width: 110 },
  { title: '金额(万元)', dataIndex: 'contractAmount', width: 120, align: 'right' as const },
]

export const bmChangeCols = [
  { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
  { title: '当前金额', dataIndex: 'currentAmount', width: 128, align: 'right' as const },
]

export const bmSettleCols = [
  { title: '项目名称', dataIndex: 'projectName', ellipsis: true },
  { title: '结算状态', dataIndex: 'status', width: 100 },
]

export const financePayCols = [
  { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
  { title: '合作方', dataIndex: 'partnerName', width: 120 },
  { title: '金额', dataIndex: 'payAmount', width: 128, align: 'right' as const },
]

export const businessItemCols = [
  { title: '事项', dataIndex: 'title', ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '日期', dataIndex: 'date', width: 112 },
]

export const businessItemAmountCols = [
  { title: '事项', dataIndex: 'title', ellipsis: true },
  { title: '金额', dataIndex: 'amount', width: 120, align: 'right' as const },
  { title: '状态', dataIndex: 'status', width: 100 },
]

export const alertCols = [
  { title: '严重程度', dataIndex: 'severity', width: 90 },
  { title: '预警信息', dataIndex: 'message', ellipsis: true },
  { title: '项目', dataIndex: 'projectName', width: 130 },
]

export const mgmtRankCols = [
  { title: '项目名称', dataIndex: 'projectName', ellipsis: true },
  { title: '合同收入', dataIndex: 'contractIncome', width: 120, align: 'right' as const },
  { title: '预计利润', dataIndex: 'expectedProfit', width: 120, align: 'right' as const },
  { title: '风险', dataIndex: 'riskCount', width: 82, align: 'center' as const },
]

export const drillCols = [
  { title: '科目名称', dataIndex: 'costSubjectName', width: 200 },
  { title: '成本目标(万元)', dataIndex: 'targetCost', width: 130, align: 'right' as const },
  { title: '实际成本(万元)', dataIndex: 'actualCost', width: 130, align: 'right' as const },
  { title: '成本偏差(万元)', dataIndex: 'costDeviation', width: 140, align: 'right' as const },
]
