import { getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

/** Source type display mapping */
export type SourceType =
  | 'CT_CONTRACT'
  | 'CT_REVENUE'
  | 'CT_DIRECT'
  | 'CT_INDIRECT'
  | 'CT_MATERIAL'
  | 'CT_MACHINE'
  | 'CT_MACHINERY'
  | 'CT_SUBCONTRACT'
  | 'CT_LABOR'
  | 'CT_OTHER'
  | 'MATERIAL_RECEIPT'
  | 'MAT_RECEIPT'
  | 'SUB_MEASURE'
  | 'VAR_ORDER'
  | 'VARIATION'
  | 'CT_CHANGE'
  | 'BID_COST'
  | 'BID_COST_TRANSFERRED'
  | 'OVERHEAD_ALLOCATION'

export const COST_TYPE_DICT = 'cost_type'
export const COST_SOURCE_TYPE_DICT = 'cost_source_type'
export const COST_STATUS_DICT = 'cost_status'
export const COST_TYPE_LABEL: Record<string, string> = {
  CONTRACT_LOCKED: '合同锁定成本',
  ACTUAL_COST: '实际成本',
  TARGET_COST: '成本目标',
  PAID_AMOUNT: '已付款',
  DYNAMIC_COST: '动态成本',
  CT_CONTRACT: '合同锁定成本',
  CT_DIRECT: '直接成本',
  CT_INDIRECT: '间接成本',
  CT_MATERIAL: '材料成本',
  CT_MACHINE: '机械使用成本',
  CT_MACHINERY: '机械使用成本',
  CT_SUBCONTRACT: '分包成本',
  CT_LABOR: '人工成本',
  CT_OTHER: '其他成本',
  MATERIAL: '材料费',
  SUBCONTRACT: '分包费',
  MACHINERY: '机械费',
  LABOR: '人工费',
  VISA: '签证费',
  MANAGEMENT: '管理费',
  MATERIAL_RECEIPT: '材料验收成本',
  MAT_RECEIPT: '材料验收成本',
  SUB_MEASURE: '分包计量成本',
  VAR_ORDER: '签证变更成本',
  VARIATION: '签证变更成本',
  CHANGE: '合同变更成本',
  CT_CHANGE: '合同变更成本',
}

export const SOURCE_TYPE_LABEL: Record<SourceType, string> = {
  CT_CONTRACT: '合同锁定成本',
  CT_REVENUE: '业主收入确认',
  CT_DIRECT: '直接成本',
  CT_INDIRECT: '间接成本',
  CT_MATERIAL: '材料成本',
  CT_MACHINE: '机械使用成本',
  CT_MACHINERY: '机械使用成本',
  CT_SUBCONTRACT: '分包成本',
  CT_LABOR: '人工成本',
  CT_OTHER: '其他成本',
  MATERIAL_RECEIPT: '材料验收成本',
  MAT_RECEIPT: '材料验收成本',
  SUB_MEASURE: '分包计量成本',
  VAR_ORDER: '签证变更成本',
  VARIATION: '签证变更成本',
  CT_CHANGE: '合同变更成本',
  BID_COST: '投标前期费用',
  BID_COST_TRANSFERRED: '投标前期费用(已结转)',
  OVERHEAD_ALLOCATION: '间接费用分摊',
}

export const SOURCE_TYPE_COLOR: Record<SourceType, string> = {
  CT_CONTRACT: 'blue',
  CT_REVENUE: 'cyan',
  CT_DIRECT: 'geekblue',
  CT_INDIRECT: 'magenta',
  CT_MATERIAL: 'green',
  CT_MACHINE: 'gold',
  CT_MACHINERY: 'gold',
  CT_SUBCONTRACT: 'orange',
  CT_LABOR: 'lime',
  CT_OTHER: 'default',
  MATERIAL_RECEIPT: 'green',
  MAT_RECEIPT: 'green',
  SUB_MEASURE: 'orange',
  VAR_ORDER: 'purple',
  VARIATION: 'purple',
  CT_CHANGE: 'purple',
  BID_COST: 'geekblue',
  BID_COST_TRANSFERRED: 'lime',
  OVERHEAD_ALLOCATION: 'magenta',
}

export function getCostTypeLabel(value: string | undefined) {
  const key = value?.trim()
  if (!key) return '-'
  return getDictLabelSync(COST_TYPE_DICT, key, COST_TYPE_LABEL) || key
}

export function getSourceTypeLabel(value: string | undefined) {
  const key = value?.trim()
  if (!key) return '-'
  return getDictLabelSync(COST_SOURCE_TYPE_DICT, key, SOURCE_TYPE_LABEL) || key
}

export function getSourceTypeColor(value: string | undefined) {
  const key = value?.trim()
  if (!key) return 'default'
  return getDictTagColorSync(COST_SOURCE_TYPE_DICT, key, SOURCE_TYPE_COLOR)
}

export function getCostStatusLabel(value: string | undefined) {
  const key = value?.trim()
  if (!key) return '-'
  return getDictLabelSync(COST_STATUS_DICT, key) || key
}

export function getCostStatusColor(value: string | undefined) {
  const key = value?.trim()
  if (!key) return 'default'
  return getDictTagColorSync(COST_STATUS_DICT, key)
}

/** Cost ledger item view object */
export interface CostLedgerVO {
  id: string
  projectId: string
  projectName: string
  contractId: string
  contractName: string
  partnerId: string
  partnerName: string
  costSubjectId: string
  costSubjectName: string
  costType: string
  amount: string
  taxAmount: string
  amountWithoutTax: string
  sourceType: string
  sourceId: string
  sourceItemId: string
  costDate: string
  costStatus: string
  generatedFlag: string
  createdBy: string
  createdAt: string
  remark: string
}

/** Cost ledger query parameters */
export interface CostLedgerQueryParams {
  pageNo: number
  pageSize: number
  projectId?: string
  contractId?: string
  partnerId?: string
  costSubjectId?: string
  costType?: string
  sourceType?: string
  costStatus?: string
  startDate?: string
  endDate?: string
  keyword?: string
}

/** Cost ledger summary view object */
export interface CostLedgerSummaryVO {
  totalAmount: string
  totalTaxAmount: string
  bySourceType: Record<string, string>
  byProject: Record<string, string>
  byCostType: Record<string, string>
}

// --- 动态成本汇总 ---

/** Cost summary per subject view object */
export interface CostSubjectSummaryVO {
  costTargetId?: string
  costForecastId?: string
  costSubjectId: string
  costSubjectName: string
  targetCost: string
  contractLockedCost: string
  actualCost: string
  paidAmount: string
  dynamicCost: string
  costDeviation: string
  responsibilityCost?: string
  forecastAtCompletionCost?: string
  forecastProfit?: string
  profitMargin?: string
}

/** Cost summary project-level view object */
export interface CostSummaryVO {
  projectId: string
  projectName: string
  targetCost: string
  contractLockedCost: string
  actualCost: string
  paidAmount: string
  dynamicCost: string
  costDeviation: string
  contractIncome: string
  confirmedRevenue: string
  expectedProfit: string
  costTargetId?: string
  costForecastId?: string
  responsibilityCost?: string
  forecastAtCompletionCost?: string
  forecastProfit?: string
  profitMargin?: string
  subjects: CostSubjectSummaryVO[]
}

/** Historical cost summary snapshot for one cost subject. */
export interface CostSummaryHistoryVO {
  id: string
  projectId: string
  projectName: string
  summaryDate: string
  costSubjectId: string
  costSubjectName: string
  costTargetId?: string
  costForecastId?: string
  targetCost: string
  contractLockedCost: string
  actualCost: string
  paidAmount: string
  estimatedRemainingCost: string
  dynamicCost: string
  contractIncome: string
  confirmedRevenue: string
  expectedProfit: string
  costDeviation: string
  responsibilityCost?: string
  forecastAtCompletionCost?: string
  forecastProfit?: string
  profitMargin?: string
  createdAt: string
}
