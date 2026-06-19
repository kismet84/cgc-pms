/** Source type display mapping */
export type SourceType = 'CT_CONTRACT' | 'MAT_RECEIPT' | 'SUB_MEASURE' | 'VAR_ORDER'

export const SOURCE_TYPE_LABEL: Record<SourceType, string> = {
  CT_CONTRACT: '合同锁定成本',
  MAT_RECEIPT: '材料验收成本',
  SUB_MEASURE: '分包计量成本',
  VAR_ORDER: '签证变更成本',
  CT_REVENUE: '业主收入确认',
  BID_COST: '投标前期费用',
  BID_COST_TRANSFERRED: '投标前期费用(已结转)',
  OVERHEAD_ALLOCATION: '间接费用分摊',
}

export const SOURCE_TYPE_COLOR: Record<SourceType, string> = {
  CT_CONTRACT: 'blue',
  MAT_RECEIPT: 'green',
  SUB_MEASURE: 'orange',
  VAR_ORDER: 'purple',
  CT_REVENUE: 'cyan',
  BID_COST: 'geekblue',
  BID_COST_TRANSFERRED: 'lime',
  OVERHEAD_ALLOCATION: 'magenta',
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
  costSubjectId: string
  costSubjectName: string
  targetCost: string
  contractLockedCost: string
  actualCost: string
  paidAmount: string
  dynamicCost: string
  costDeviation: string
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
  subjects: CostSubjectSummaryVO[]
}
