import type {
  WorkflowCc,
  WorkflowMine,
  WorkflowRecord,
  WorkflowTab,
  WorkflowTask,
} from '@cgc-pms/frontend-contracts'

export interface WorkflowRow {
  key: string
  instanceId: string
  title: string
  businessType: string
  businessCode: string
  status: string
  actor: string
  time: string
  note: string
}

export const WORKFLOW_TABS: { value: WorkflowTab; label: string }[] = [
  { value: 'todo', label: '待我处理' },
  { value: 'done', label: '我已处理' },
  { value: 'cc', label: '抄送我的' },
  { value: 'mine', label: '我发起' },
]

export const WORKFLOW_BUSINESS_TYPES = [
  ['CONTRACT_APPROVAL', '合同审批'],
  ['PROJECT_APPROVAL', '项目审批'],
  ['PURCHASE_ORDER', '采购订单'],
  ['MATERIAL_RECEIPT', '材料验收'],
  ['SUB_MEASURE', '分包计量'],
  ['PAY_REQUEST', '付款申请'],
  ['VAR_ORDER', '签证变更'],
  ['PURCHASE_REQUEST', '采购申请'],
  ['CT_CHANGE', '合同变更'],
  ['SETTLEMENT', '结算审批'],
  ['COST_TARGET', '成本目标'],
  ['CONTRACT_REVENUE', '合同收入'],
  ['MATERIAL_REQUISITION', '材料领用'],
  ['TECH_ITEM', '技术事项'],
  ['PROJECT_BUDGET', '项目预算'],
  ['EXPENSE', '费用申请'],
  ['OWNER_SETTLEMENT', '业主结算'],
  ['PRODUCTION_MEASUREMENT', '产值计量'],
  ['PROJECT_SCHEDULE', '项目基线/修订计划'],
  ['PROJECT_PERIOD_PLAN', '项目月周计划'],
  ['PROJECT_CORRECTIVE_ACTION', '项目进度纠偏'],
  ['COST_CORRECTIVE_ACTION', '成本偏差纠偏'],
  ['TECHNICAL_SCHEME', '技术方案'],
  ['PROJECT_FINAL_ACCEPTANCE', '竣工验收'],
  ['COST_SUBJECT_MAPPING', '成本科目映射'],
  ['BID_COST_TARGET_TRANSFER', '投标成本转入目标成本'],
  ['BID_COST_TARGET_TRANSFER_REVERSAL', '投标成本转入冲销'],
  ['DEMO_APPROVAL_SCENARIO', '演示审批场景'],
] as const

const WORKFLOW_BUSINESS_TYPE_LABELS = Object.fromEntries(WORKFLOW_BUSINESS_TYPES)

export const WORKFLOW_STATUS_LABELS: Record<string, string> = {
  RUNNING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  VOIDED: '已作废',
  PENDING: '待处理',
  COMPLETED: '已完成',
  TRANSFERRED: '已转办',
  CANCELLED: '已取消',
  SUBMIT: '已提交',
  APPROVE: '已同意',
  REJECT: '已驳回',
  WITHDRAW: '已撤回',
  RESUBMIT: '已重新提交',
  TRANSFER: '已转办',
  ADD_SIGN: '已加签',
  ACTIVE: '处理中',
}

export const WORKFLOW_ACTION_LABELS = {
  approve: '同意',
  reject: '驳回',
  withdraw: '撤回',
  resubmit: '重新提交',
  transfer: '转办',
  addSign: '加签',
} as const

export function workflowRows(
  tab: WorkflowTab,
  records: WorkflowTask[] | WorkflowRecord[] | WorkflowCc[] | WorkflowMine[],
): WorkflowRow[] {
  if (tab === 'todo') {
    return (records as WorkflowTask[]).map((item) => ({
      key: item.id,
      instanceId: item.instanceId,
      title: item.title,
      businessType: item.businessType,
      businessCode: item.businessCode ?? '-',
      status: item.taskStatus,
      actor: item.approverName,
      time: item.receivedAt,
      note: `第 ${item.roundNo} 轮`,
    }))
  }
  if (tab === 'done') {
    return (records as WorkflowRecord[]).map((item) => ({
      key: item.id,
      instanceId: item.instanceId,
      title: item.title ?? '-',
      businessType: item.businessType ?? '-',
      businessCode: item.businessCode ?? '-',
      status: item.actionType,
      actor: item.operatorName,
      time: item.createdAt,
      note: item.comment ?? item.actionName,
    }))
  }
  if (tab === 'cc') {
    return (records as WorkflowCc[]).map((item) => ({
      key: item.id,
      instanceId: item.instanceId,
      title: item.title,
      businessType: item.businessType,
      businessCode: item.businessCode ?? '-',
      status: item.instanceStatus ?? '-',
      actor: item.ccUserName,
      time: item.createdTime,
      note: item.isRead ? '已读' : '未读',
    }))
  }
  return (records as WorkflowMine[]).map((item) => ({
    key: item.instanceId,
    instanceId: item.instanceId,
    title: item.title,
    businessType: item.businessType,
    businessCode: item.businessCode ?? '-',
    status: item.instanceStatus,
    actor: item.currentNodeName ?? '-',
    time: item.updatedAt ?? item.createdAt,
    note: item.currentNodeName ? `当前节点：${item.currentNodeName}` : '流程已结束',
  }))
}

export function workflowStatusLabel(status: string): string {
  return status === '-' ? '-' : (WORKFLOW_STATUS_LABELS[status] ?? '其他状态')
}

export function workflowBusinessTypeLabel(businessType?: string): string {
  return businessType ? (WORKFLOW_BUSINESS_TYPE_LABELS[businessType] ?? '其他业务审批') : '-'
}

export function workflowDate(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

export function workflowApproveModeLabel(mode: string): string {
  if (mode === 'SEQUENTIAL') return '顺序审批'
  if (mode === 'OR') return '任一审批'
  if (mode === 'AND') return '会签审批'
  return '审批'
}
