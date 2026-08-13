import type {
  ContractCompositeRecord,
  ContractItemRecord,
  ContractPaymentTermRecord,
  ContractProjectOption,
  ContractQuery,
  ContractSaveCommand,
  ContractType,
  PartnerRecord,
} from '@cgc-pms/frontend-contracts'
import type { V2SelectOption } from '@/components/types'

export type ContractEditorMode = 'create' | 'edit'

export const CONTRACT_TYPE_OPTIONS: Array<{ value: ContractType; label: string }> = [
  { value: 'MAIN', label: '主合同' },
  { value: 'SUB', label: '分包合同' },
  { value: 'PURCHASE', label: '采购合同' },
  { value: 'LEASE', label: '租赁合同' },
  { value: 'SERVICE', label: '服务合同' },
]

const CONTRACT_STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PERFORMING', label: '履约中' },
  { value: 'SETTLED', label: '已结算' },
  { value: 'TERMINATED', label: '已终止' },
]

const APPROVAL_STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVING', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'WITHDRAWN', label: '已撤回' },
]

export const CONTRACT_PRESET_VIEWS: Array<{
  id: string
  label: string
  contractStatus?: ContractQuery['contractStatus']
  approvalStatus?: ContractQuery['approvalStatus']
}> = [
  { id: 'all', label: '全部合同' },
  { id: 'draft', label: '草稿合同', contractStatus: 'DRAFT' },
  { id: 'approving', label: '审批中', approvalStatus: 'APPROVING' },
  { id: 'performing', label: '履约中', contractStatus: 'PERFORMING' },
  { id: 'settled', label: '已结算', contractStatus: 'SETTLED' },
  { id: 'terminated', label: '已终止', contractStatus: 'TERMINATED' },
]

const PAYMENT_TERM_STATUS_LABELS: Record<string, string> = {
  PLANNED: '计划中',
  DUE: '待支付',
  PAID: '已支付',
  OVERDUE: '已逾期',
  CANCELLED: '已取消',
}

export function contractTypeLabel(value?: string | null): string {
  return CONTRACT_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? '未知类型'
}

export function contractStatusLabel(value?: string | null): string {
  return CONTRACT_STATUS_OPTIONS.find((option) => option.value === value)?.label ?? '未知状态'
}

export function approvalStatusLabel(value?: string | null): string {
  return APPROVAL_STATUS_OPTIONS.find((option) => option.value === value)?.label ?? '未知状态'
}

export function paymentTermStatusLabel(value?: string | null): string {
  return PAYMENT_TERM_STATUS_LABELS[value ?? ''] ?? '未知状态'
}

export function emptyCommand(initialProjectId = ''): ContractSaveCommand {
  return {
    contract: {
      projectId: initialProjectId,
      contractName: '',
      contractType: 'MAIN',
      partyAId: '',
      partyBId: '',
      contractAmount: '',
      taxRate: '',
      taxAmount: '',
      amountWithoutTax: '',
      signedDate: '',
      startDate: '',
      endDate: '',
      paymentMethod: '',
      settlementMethod: '',
      pricingMode: null,
      version: '',
      remark: '',
    },
    items: [],
    paymentTerms: [],
  }
}

export function blankItem(index: number): ContractItemRecord {
  return {
    materialId: '',
    itemCode: '',
    itemName: '',
    itemSpec: '',
    unit: '',
    quantity: '',
    unitPrice: '',
    amount: '',
    taxRate: '',
    taxAmount: '',
    amountWithoutTax: '',
    sortOrder: String(index + 1),
    remark: '',
  }
}

export function blankTerm(index: number): ContractPaymentTermRecord {
  return {
    termName: '',
    paymentRatio: '',
    paymentAmount: '',
    paymentCondition: '',
    plannedDate: '',
    actualDate: '',
    termStatus: 'PLANNED',
    sortOrder: String(index + 1),
    remark: '',
  }
}

export function cloneCommandFromDetail(value: ContractCompositeRecord): ContractSaveCommand {
  return {
    contract: {
      id: value.contract.id,
      projectId: value.contract.projectId,
      contractName: value.contract.contractName,
      contractType: value.contract.contractType,
      partyAId: value.contract.partyAId,
      partyBId: value.contract.partyBId,
      contractAmount: value.contract.contractAmount ?? '',
      taxRate: value.contract.taxRate ?? '',
      taxAmount: value.contract.taxAmount ?? '',
      amountWithoutTax: value.contract.amountWithoutTax ?? '',
      signedDate: value.contract.signedDate ?? '',
      startDate: value.contract.startDate ?? '',
      endDate: value.contract.endDate ?? '',
      paymentMethod: value.contract.paymentMethod ?? '',
      settlementMethod: value.contract.settlementMethod ?? '',
      pricingMode: value.contract.pricingMode ?? null,
      version: value.contract.version ?? '',
      remark: value.contract.remark ?? '',
    },
    items: value.items.map((item) => ({
      id: item.id ?? '',
      contractId: item.contractId ?? value.contract.id,
      materialId: item.materialId ?? '',
      itemCode: item.itemCode ?? '',
      itemName: item.itemName,
      itemSpec: item.itemSpec ?? '',
      unit: item.unit ?? '',
      quantity: item.quantity ?? '',
      unitPrice: item.unitPrice ?? '',
      amount: item.amount ?? '',
      taxRate: item.taxRate ?? '',
      taxAmount: item.taxAmount ?? '',
      amountWithoutTax: item.amountWithoutTax ?? '',
      sortOrder: item.sortOrder ?? '',
      remark: item.remark ?? '',
    })),
    paymentTerms: value.paymentTerms.map((term) => ({
      id: term.id ?? '',
      contractId: term.contractId ?? value.contract.id,
      termName: term.termName,
      paymentRatio: term.paymentRatio ?? '',
      paymentAmount: term.paymentAmount ?? '',
      paymentCondition: term.paymentCondition ?? '',
      plannedDate: term.plannedDate ?? '',
      actualDate: term.actualDate ?? '',
      termStatus: term.termStatus ?? '',
      sortOrder: term.sortOrder ?? '',
      remark: term.remark ?? '',
    })),
  }
}

export function partnerCandidates(
  partners: PartnerRecord[],
  excludedId: string | null | undefined,
  currentId: string | null | undefined,
  currentName: string | null | undefined,
  eligible: (partner: PartnerRecord) => boolean = () => true,
): V2SelectOption[] {
  const options: V2SelectOption[] = partners
    .filter((partner) => partner.id !== excludedId && eligible(partner))
    .map((partner) => ({ value: partner.id, label: partner.partnerName }))
  if (currentId && !options.some((option) => option.value === currentId)) {
    options.push({
      value: currentId,
      label: `${currentName || '历史合作方'}（历史值）`,
      disabled: true,
    })
  }
  return options
}

export function previewTaxBreakdown(
  amountValue?: string | null,
  taxRateValue?: string | null,
): { taxAmount: string; amountWithoutTax: string } {
  if (!amountValue?.trim() || !taxRateValue?.trim()) {
    return { taxAmount: '', amountWithoutTax: '' }
  }
  const amount = Number(amountValue)
  const taxRate = Number(taxRateValue)
  if (
    !Number.isFinite(amount) ||
    !Number.isFinite(taxRate) ||
    amount < 0 ||
    taxRate < 0 ||
    taxRate > 100
  ) {
    return { taxAmount: '', amountWithoutTax: '' }
  }
  const amountWithoutTax = taxRate === 0 ? amount : (amount * 100) / (100 + taxRate)
  const roundedWithoutTax = Number(amountWithoutTax.toFixed(2))
  return {
    taxAmount: (amount - roundedWithoutTax).toFixed(2),
    amountWithoutTax: roundedWithoutTax.toFixed(2),
  }
}

function cleaned(value?: string | null): string | null {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

export function sanitizeCommand(value: ContractSaveCommand): ContractSaveCommand {
  return {
    contract: {
      ...value.contract,
      id: cleaned(value.contract.id),
      projectId: cleaned(value.contract.projectId),
      contractName: value.contract.contractName.trim(),
      partyAId: cleaned(value.contract.partyAId),
      partyBId: cleaned(value.contract.partyBId),
      contractAmount: cleaned(value.contract.contractAmount),
      taxRate: cleaned(value.contract.taxRate),
      taxAmount: null,
      amountWithoutTax: null,
      signedDate: cleaned(value.contract.signedDate),
      startDate: cleaned(value.contract.startDate),
      endDate: cleaned(value.contract.endDate),
      paymentMethod: cleaned(value.contract.paymentMethod),
      settlementMethod: cleaned(value.contract.settlementMethod),
      pricingMode: cleaned(value.contract.pricingMode) as 'FIXED' | 'ACTUAL' | null,
      version: cleaned(String(value.contract.version ?? '')),
      remark: cleaned(value.contract.remark),
    },
    items: value.items.map((item, index) => ({
      ...item,
      id: cleaned(item.id ?? ''),
      contractId: cleaned(item.contractId ?? ''),
      materialId: cleaned(item.materialId ?? ''),
      itemCode: cleaned(item.itemCode ?? ''),
      itemName: item.itemName.trim(),
      itemSpec: cleaned(item.itemSpec ?? ''),
      unit: cleaned(item.unit ?? ''),
      quantity: cleaned(item.quantity ?? ''),
      unitPrice: cleaned(item.unitPrice ?? ''),
      amount: null,
      taxRate: null,
      taxAmount: null,
      amountWithoutTax: null,
      sortOrder: cleaned(String(item.sortOrder ?? index + 1)),
      remark: cleaned(item.remark ?? ''),
    })),
    paymentTerms: value.paymentTerms.map((term, index) => ({
      ...term,
      id: cleaned(term.id ?? ''),
      contractId: cleaned(term.contractId ?? ''),
      termName: term.termName.trim(),
      paymentRatio: cleaned(term.paymentRatio ?? ''),
      paymentAmount: cleaned(term.paymentAmount ?? ''),
      paymentCondition: cleaned(term.paymentCondition ?? ''),
      plannedDate: cleaned(term.plannedDate ?? ''),
      actualDate: cleaned(term.actualDate ?? ''),
      termStatus: cleaned(term.termStatus ?? ''),
      sortOrder: cleaned(String(term.sortOrder ?? index + 1)),
      remark: cleaned(term.remark ?? ''),
    })),
  }
}

export function validateCommand(
  command: ContractSaveCommand,
  projects: ContractProjectOption[],
): string | null {
  if (!command.contract.projectId) return '项目不能为空'
  const selectedProject = projects.find((project) => project.id === command.contract.projectId)
  if (
    !selectedProject ||
    (command.contract.contractType === 'MAIN'
      ? !selectedProject.mainEligible
      : !selectedProject.nonMainEligible)
  ) {
    return '所选项目不符合当前合同类型的审批、状态或预算要求'
  }
  if (!command.contract.contractName) return '合同名称不能为空'
  if (!command.contract.partyAId || !command.contract.partyBId) return '甲乙方不能为空'
  if (!command.contract.contractAmount) return '合同金额不能为空'
  if (!command.contract.taxRate) return '合同税率不能为空'
  if (command.items.some((item) => !item.itemName?.trim())) return '合同清单名称不能为空'
  if (command.items.some((item) => !item.quantity || !item.unitPrice)) {
    return '合同清单数量和单价不能为空'
  }
  if (command.paymentTerms.some((term) => !term.termName?.trim())) {
    return '付款条款名称不能为空'
  }
  return null
}
