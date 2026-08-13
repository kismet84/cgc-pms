import type {
  AssignmentRuleRecord,
  BidTransferRequestRecord,
  CostSubjectAuditRow,
  CostSubjectRecord,
  FinanceAllocationRequestRecord,
  SubjectImpactRecord,
} from '@/services/cost-subject'

export const statusOptions = [
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
]

export const enabledOptions = [
  { value: 'true', label: '启用' },
  { value: 'false', label: '停用' },
]

export const sourceTypeOptions = [
  { value: 'ACCOUNTING_ENTRY_LINE', label: '已过账借方凭证明细' },
  { value: 'EXPENSE_APPLICATION', label: '已审批费用申请' },
]

export const allocationBasisOptions = [
  { value: 'DIRECT_PROJECT', label: '直接归属' },
  { value: 'BENEFIT_AMOUNT', label: '受益金额' },
  { value: 'OCCUPIED_DAYS', label: '占用天数' },
  { value: 'CONTRACT_AMOUNT_EXCEPTION', label: '合同额例外' },
]

export const impactLabels: Array<[keyof SubjectImpactRecord, string]> = [
  ['costItems', '成本明细'],
  ['targetItems', '目标成本明细'],
  ['forecastItems', '完工预测'],
  ['budgetLines', '预算明细'],
  ['payments', '付款申请'],
  ['expenses', '费用申请'],
  ['settlementItems', '结算明细'],
  ['accountingLines', '会计凭证明细'],
  ['assignmentRules', '归集规则'],
  ['projectScopes', '项目范围'],
]

const subjectTypeLabels: Record<string, string> = {
  ROOT: '根科目',
  BID: '投标成本',
  PURCHASE: '采购成本',
  MATERIAL: '材料费',
  TESTING: '试验检测费',
  CONSTRUCTION: '施工成本',
  LABOR: '人工费',
  MACHINERY: '机械费',
  UTILITY: '水电费',
  SUBCONTRACT: '分包费',
  MEASURES: '措施费',
  OTHER: '其他成本',
  OVERHEAD: '间接费用',
  TARGET_COST: '项目目标成本',
  SITE_MANAGEMENT: '现场管理费',
  SPECIAL: '其他专项成本',
  FINANCE_TAX: '财务及税费',
  RISK_RESERVE: '风险准备',
}

const statusLabels: Record<string, string> = {
  ACTIVE: '已启用',
  DISABLE: '停用',
  DRAFT: '草稿',
  ENABLE: '启用',
  POSTED: '已入账',
  REJECTED: '已驳回',
  REVERSED: '已冲销',
  SUBMITTED: '审批中',
  WITHDRAWN: '已撤回',
}

export function pageSlice<T>(items: T[], pageNo: number, pageSize = 10): T[] {
  return items.slice((pageNo - 1) * pageSize, pageNo * pageSize)
}

export function subjectTypeLabel(value?: string): string {
  return subjectTypeLabels[value ?? ''] ?? '其他成本'
}

export function isGovernedSubject(subject?: CostSubjectRecord | null): boolean {
  return Boolean(
    subject && (subject.subjectCode === '5401.03' || subject.subjectCode.startsWith('5401.03.')),
  )
}

export function statusLabel(status: string): string {
  return statusLabels[status] ?? status
}

export function codeNameLabel(
  code: string | null | undefined,
  name: string | null | undefined,
  fallback: string,
): string {
  return [code?.trim(), name?.trim()].filter(Boolean).join(' · ') || fallback
}

export function ruleProjectLabel(record: AssignmentRuleRecord): string {
  if (!record.projectId) return '全局'
  return codeNameLabel(record.projectCode, record.projectName, '项目已归档')
}

export function requestProjectLabel(
  record: Pick<BidTransferRequestRecord, 'projectCode' | 'projectName'>,
): string {
  return codeNameLabel(record.projectCode, record.projectName, '项目已归档')
}

export function bidCostLabel(record: BidTransferRequestRecord): string {
  return record.bidCode?.trim() || '投标成本已归档'
}

export function targetVersionLabel(record: BidTransferRequestRecord): string {
  return codeNameLabel(record.targetVersionNo, record.targetVersionName, '目标成本版本已归档')
}

export function allocationSourceLabel(record: FinanceAllocationRequestRecord): string {
  const type =
    sourceTypeOptions.find((item) => item.value === record.sourceType)?.label ?? '财务业务来源'
  return record.sourceCode?.trim() ? `${type} · ${record.sourceCode.trim()}` : type
}

export function allocationBasisLabel(value: string): string {
  return allocationBasisOptions.find((item) => item.value === value)?.label ?? '其他依据'
}

export function allocationSubjectLabel(record: FinanceAllocationRequestRecord): string {
  return codeNameLabel(record.costSubjectCode, record.costSubjectName, '成本科目已归档')
}

export function rowText(row: CostSubjectAuditRow, key: string): string {
  const value = row[key]
  if (value == null || value === '') return '—'
  return key === 'status' ? statusLabel(String(value)) : String(value)
}
