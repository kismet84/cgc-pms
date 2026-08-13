import type { CostSubjectOption } from '@/services/commercial'
import type { V2SelectOption } from '@/components/types'
import type { DictDataRecord } from '@/services/system-management'
import type {
  BudgetLineRecord,
  CollectionCommand,
  ContractRecord,
  ExpenseApplicationCommand,
  InvoiceCommand,
  OwnerSettlementCommand,
  PartnerRecord,
  PaymentApplicationCommand,
  SalesInvoiceCommand,
} from '@cgc-pms/frontend-contracts'

export type SelectOption = V2SelectOption

export interface PaymentEditor {
  id: string
  projectId: string
  contractId: string
  partnerId: string
  payType: string
  expenseCategory: string
  costSubjectId: string
  budgetLineId: string
  sourceType: string
  sourceRefId: string
  applyAmount: string
  applyReason: string
}

export interface ExpenseEditor {
  id: string
  projectId: string
  contractId: string
  costSubjectId: string
  budgetLineId: string
  payeePartnerId: string
  expenseCategory: string
  expenseDate: string
  amount: string
  description: string
}

export interface InvoiceEditor {
  id: string
  payRecordId: string
  invoiceNo: string
  invoiceType: string
  invoiceAmount: string
  taxRate: string
  taxAmount: string
  invoiceDate: string
  sellerName: string
  buyerName: string
}

export interface RevenueEditor {
  projectId: string
  contractId: string
  customerId: string
  revenueId: string
  settlementPeriod: string
  settlementDate: string
  grossAmount: string
  taxAmount: string
  retentionAmount: string
  dueDate: string
  invoiceNo: string
  invoiceType: string
  invoiceDate: string
  amountWithoutTax: string
  receivableId: string
  allocationAmount: string
  fundAccountId: string
  externalTxnNo: string
  collectedAt: string
  collectionAmount: string
  payerName: string
  remark: string
}

export function required(value: string | number | null | undefined, label: string): string {
  const normalized = value == null ? '' : String(value).trim()
  if (!normalized) throw new TypeError(`${label}不能为空`)
  return normalized
}

export function defaultOption(options: SelectOption[], preferred: string): string {
  return options.some((item) => item.value === preferred) ? preferred : (options[0]?.value ?? '')
}

export function dictionaryOptions(rows: DictDataRecord[]): SelectOption[] {
  return rows.map((item) => ({ value: item.dictValue, label: item.dictLabel }))
}

export function contractOptions(
  rows: ContractRecord[],
  kind: 'payment' | 'expense' | 'revenue',
  historicalId = '',
): SelectOption[] {
  return rows
    .filter((item) => {
      const historical = Boolean(historicalId) && item.id === historicalId
      const performing = item.approvalStatus === 'APPROVED' && item.contractStatus === 'PERFORMING'
      return historical || (performing && (kind !== 'revenue' || item.contractType === 'MAIN'))
    })
    .map((item) => {
      const selectable =
        item.approvalStatus === 'APPROVED' &&
        item.contractStatus === 'PERFORMING' &&
        (kind !== 'revenue' || item.contractType === 'MAIN')
      const option = { value: item.id, label: `${item.contractCode} · ${item.contractName}` }
      return selectable ? option : { ...option, label: `${option.label}（历史值）`, disabled: true }
    })
}

export function linkedPartnerOptions(
  rows: PartnerRecord[],
  expectedId?: string | null,
  historicalId?: string | null,
): SelectOption[] {
  const options = rows
    .filter((item) => Boolean(expectedId) && item.id === expectedId)
    .map((item) => ({ value: item.id, label: `${item.partnerCode} · ${item.partnerName}` }))
  if (historicalId && !options.some((item) => item.value === historicalId)) {
    const current = rows.find((item) => item.id === historicalId)
    options.push({
      value: historicalId,
      label: `${current?.partnerCode ? `${current.partnerCode} · ` : ''}${current?.partnerName || '历史往来单位'}（历史值）`,
      disabled: true,
    })
  }
  return options
}

export function allPartnerOptions(
  rows: PartnerRecord[],
  historicalId?: string | null,
): SelectOption[] {
  const options = rows.map((item) => ({
    value: item.id,
    label: `${item.partnerCode} · ${item.partnerName}`,
  }))
  if (historicalId && !options.some((item) => item.value === historicalId)) {
    options.push({ value: historicalId, label: '历史收款单位（历史值）', disabled: true })
  }
  return options
}

export function leafCostSubjectOptions(rows: CostSubjectOption[]): SelectOption[] {
  const parentIds = new Set(
    rows.map((item) => item.parentId).filter((id): id is string => Boolean(id)),
  )
  return rows
    .filter((item) => item.status === 'ENABLE' && !parentIds.has(item.id))
    .map((item) => ({ value: item.id, label: `${item.subjectCode} · ${item.subjectName}` }))
}

export function budgetOptions(rows: BudgetLineRecord[], costSubjectId: string): SelectOption[] {
  return rows
    .filter((item) => !costSubjectId || item.costSubjectId === costSubjectId)
    .filter((item): item is BudgetLineRecord & { id: string } => Boolean(item.id))
    .map((item) => ({
      value: item.id,
      label: `${item.costSubjectName || item.costSubjectId} · 可用 ${item.availableAmount}`,
    }))
}

export function emptyPaymentEditor(): PaymentEditor {
  return {
    id: '',
    projectId: '',
    contractId: '',
    partnerId: '',
    payType: '',
    expenseCategory: '',
    costSubjectId: '',
    budgetLineId: '',
    sourceType: '',
    sourceRefId: '',
    applyAmount: '',
    applyReason: '',
  }
}

export function emptyExpenseEditor(today: string): ExpenseEditor {
  return {
    id: '',
    projectId: '',
    contractId: '',
    costSubjectId: '',
    budgetLineId: '',
    payeePartnerId: '',
    expenseCategory: '',
    expenseDate: today,
    amount: '',
    description: '',
  }
}

export function emptyInvoiceEditor(today: string): InvoiceEditor {
  return {
    id: '',
    payRecordId: '',
    invoiceNo: '',
    invoiceType: '',
    invoiceAmount: '',
    taxRate: '',
    taxAmount: '',
    invoiceDate: today,
    sellerName: '',
    buyerName: '',
  }
}

export function emptyRevenueEditor(today: string): RevenueEditor {
  return {
    projectId: '',
    contractId: '',
    customerId: '',
    revenueId: '',
    settlementPeriod: today.slice(0, 7),
    settlementDate: today,
    grossAmount: '',
    taxAmount: '',
    retentionAmount: '0',
    dueDate: today,
    invoiceNo: '',
    invoiceType: '',
    invoiceDate: today,
    amountWithoutTax: '',
    receivableId: '',
    allocationAmount: '',
    fundAccountId: '',
    externalTxnNo: '',
    collectedAt: `${today}T12:00`,
    collectionAmount: '',
    payerName: '',
    remark: '',
  }
}

export function paymentCommand(value: PaymentEditor): PaymentApplicationCommand {
  return {
    projectId: required(value.projectId, '项目'),
    contractId: required(value.contractId, '合同'),
    partnerId: required(value.partnerId, '往来单位'),
    costSubjectId: required(value.costSubjectId, '成本科目'),
    budgetLineId: required(value.budgetLineId, '预算明细'),
    payType: required(value.payType, '付款类型'),
    applyAmount: required(value.applyAmount, '申请金额'),
    applyReason: value.applyReason.trim() || undefined,
    expenseCategory: value.expenseCategory.trim() || undefined,
  }
}

export function expenseCommand(value: ExpenseEditor): ExpenseApplicationCommand {
  return {
    projectId: required(value.projectId, '项目'),
    contractId: required(value.contractId, '合同'),
    costSubjectId: required(value.costSubjectId, '成本科目'),
    budgetLineId: required(value.budgetLineId, '预算明细'),
    payeePartnerId: required(value.payeePartnerId, '收款单位'),
    expenseCategory: required(value.expenseCategory, '费用类别'),
    expenseDate: required(value.expenseDate, '费用日期'),
    amount: required(value.amount, '费用金额'),
    description: required(value.description, '费用说明'),
  }
}

export function invoiceCommand(value: InvoiceEditor): InvoiceCommand {
  return {
    payRecordId: required(value.payRecordId, '付款记录'),
    invoiceNo: required(value.invoiceNo, '发票号码'),
    invoiceType: required(value.invoiceType, '发票类型'),
    invoiceAmount: required(value.invoiceAmount, '发票金额'),
    invoiceDate: required(value.invoiceDate, '开票日期'),
    taxRate: value.taxRate.trim() || undefined,
    taxAmount: value.taxAmount.trim() || undefined,
    sellerName: value.sellerName.trim() || undefined,
    buyerName: value.buyerName.trim() || undefined,
  }
}

export function settlementCommand(value: RevenueEditor): OwnerSettlementCommand {
  return {
    projectId: required(value.projectId, '项目'),
    contractId: required(value.contractId, '合同'),
    revenueId: required(value.revenueId, '已审批收入确认'),
    customerId: required(value.customerId, '建设单位'),
    settlementPeriod: required(value.settlementPeriod, '结算期间'),
    settlementDate: required(value.settlementDate, '结算日期'),
    grossAmount: required(value.grossAmount, '含税结算金额'),
    taxAmount: required(value.taxAmount, '税额'),
    retentionAmount: required(value.retentionAmount, '质保金'),
    dueDate: required(value.dueDate, '到期日期'),
    attachmentCount: 0,
    remark: value.remark.trim() || undefined,
  }
}

export function salesInvoiceCommand(value: RevenueEditor): SalesInvoiceCommand {
  return {
    projectId: required(value.projectId, '项目'),
    contractId: required(value.contractId, '合同'),
    customerId: required(value.customerId, '建设单位'),
    invoiceNo: required(value.invoiceNo, '发票号码'),
    invoiceType: required(value.invoiceType, '发票类型'),
    invoiceDate: required(value.invoiceDate, '开票日期'),
    amountWithoutTax: required(value.amountWithoutTax, '不含税金额'),
    taxAmount: required(value.taxAmount, '税额'),
    allocations: [
      {
        receivableId: required(value.receivableId, '应收款'),
        amount: required(value.allocationAmount, '分配金额'),
      },
    ],
    remark: value.remark.trim() || undefined,
  }
}

export function collectionCommand(value: RevenueEditor): CollectionCommand {
  return {
    projectId: required(value.projectId, '项目'),
    contractId: required(value.contractId, '合同'),
    customerId: required(value.customerId, '建设单位'),
    fundAccountId: required(value.fundAccountId, '资金账户'),
    externalTxnNo: required(value.externalTxnNo, '外部流水号'),
    collectedAt: required(value.collectedAt, '到账时间'),
    amount: required(value.collectionAmount, '回款金额'),
    payerName: required(value.payerName, '付款单位'),
    allocations: [
      {
        receivableId: required(value.receivableId, '应收款'),
        amount: required(value.allocationAmount, '分配金额'),
      },
    ],
    remark: value.remark.trim() || undefined,
  }
}
