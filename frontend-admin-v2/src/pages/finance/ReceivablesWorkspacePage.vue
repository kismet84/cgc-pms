<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  V2ActionMenu,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
} from '@/components'
import { showToast } from '@/components/toast'
import { formatAmount } from '@/pages/dashboard/model'
import {
  loadBudget,
  loadBudgetPage,
  loadContractPage,
  loadCostSubjectOptions,
  loadPartners,
  type CostSubjectOption,
} from '@/services/commercial'
import {
  createExpense,
  createInvoice,
  createOwnerSettlement,
  createPayment,
  createSalesInvoice,
  creditReceivable,
  deleteExpense,
  deleteInvoice,
  deletePayment,
  loadCollections,
  loadExpenseApplications,
  loadInvoices,
  loadPaymentApplications,
  loadPaymentSourceOptions,
  loadPayRecordOptions,
  loadReceivables,
  loadRevenueSettlements,
  loadSalesInvoices,
  reverseCollection,
  submitExpense,
  submitOwnerSettlement,
  submitPayment,
  updateExpense,
  updateInvoice,
  updatePayment,
  verifyInvoice,
  savePaymentSources,
  type PaymentSourceOptionRecord,
} from '@/services/finance'
import { uploadSiteFile } from '@/services/delivery'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type {
  BudgetLineRecord,
  CollectionRecord,
  ExpenseApplicationCommand,
  ExpenseApplicationRecord,
  InvoiceCommand,
  InvoiceRecord,
  OwnerSettlementCommand,
  OwnerSettlementRecord,
  PartnerRecord,
  PaymentApplicationCommand,
  PaymentApplicationRecord,
  PayRecordOption,
  ReceivableRecord,
  SalesInvoiceCommand,
  SalesInvoiceRecord,
} from '@cgc-pms/frontend-contracts'

type Mode = 'payment' | 'expense' | 'revenue' | 'invoice'
type EditorKind = Exclude<Mode, 'revenue'> | 'settlement' | 'salesInvoice'
type RecordRow = PaymentApplicationRecord | ExpenseApplicationRecord | InvoiceRecord
type RevenueRow =
  | (OwnerSettlementRecord & { kind: 'settlement' })
  | (ReceivableRecord & { kind: 'receivable' })
  | (SalesInvoiceRecord & { kind: 'salesInvoice' })
  | (CollectionRecord & { kind: 'collection' })
type Row = RecordRow | RevenueRow
type Action = 'delete' | 'submit' | 'verify' | 'credit' | 'reverse'

interface FinanceEditor {
  id: string
  projectId: string
  contractId: string
  partnerId: string
  payType: string
  applyAmount: string
  applyReason: string
  expenseCategory: string
  costSubjectId: string
  budgetLineId: string
  sourceType: string
  sourceRefId: string
  payeePartnerId: string
  expenseDate: string
  amount: string
  description: string
  payRecordId: string
  invoiceNo: string
  invoiceType: string
  invoiceAmount: string
  taxRate: string
  taxAmount: string
  invoiceDate: string
  sellerName: string
  buyerName: string
  customerId: string
  settlementPeriod: string
  settlementDate: string
  grossAmount: string
  retentionAmount: string
  dueDate: string
  amountWithoutTax: string
  attachmentCount: string
  remark: string
}

const today = () => new Date().toISOString().slice(0, 10)
const emptyEditor = (): FinanceEditor => ({
  id: '',
  projectId: '',
  contractId: '',
  partnerId: '',
  payType: 'FINAL',
  applyAmount: '',
  applyReason: '',
  expenseCategory: 'CONTRACT',
  costSubjectId: '',
  budgetLineId: '',
  sourceType: '',
  sourceRefId: '',
  payeePartnerId: '',
  expenseDate: today(),
  amount: '',
  description: '',
  payRecordId: '',
  invoiceNo: '',
  invoiceType: 'VAT_SPECIAL',
  invoiceAmount: '',
  taxRate: '',
  taxAmount: '',
  invoiceDate: today(),
  sellerName: '',
  buyerName: '',
  customerId: '',
  settlementPeriod: today().slice(0, 7),
  settlementDate: today(),
  grossAmount: '',
  retentionAmount: '0',
  dueDate: today(),
  amountWithoutTax: '',
  attachmentCount: '0',
  remark: '',
})

const route = useRoute()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const mode = computed<Mode>(() =>
  route.path === '/payment/application'
    ? 'payment'
    : route.path === '/payment/expense'
      ? 'expense'
      : route.path === '/invoice'
        ? 'invoice'
        : 'revenue',
)
const title = computed(
  () =>
    ({ payment: '付款申请', expense: '费用申请', revenue: '收入与回款', invoice: '发票管理' })[
      mode.value
    ],
)
const permission = computed(
  () =>
    ({
      payment: 'payment:app:query',
      expense: 'expense:query',
      revenue: 'revenue:operations:query',
      invoice: 'invoice:query',
    })[mode.value],
)
const canQuery = computed(() => session.hasPermission(permission.value))
const projectId = computed(() => workspace.selectedProjectId || '')
const can = (action: string) =>
  session.hasPermission(`${mode.value === 'payment' ? 'payment:app' : mode.value}:${action}`) ||
  (mode.value === 'revenue' &&
    session.hasPermission(
      action === 'reverse' ? 'revenue:collection:reverse' : 'revenue:operations:maintain',
    ))
const canAdd = computed(() => (mode.value === 'revenue' ? can('maintain') : can('add')))

const rows = ref<RecordRow[]>([])
const revenueRows = ref<RevenueRow[]>([])
const revenueSections = computed(() => [
  {
    key: 'settlement',
    title: '业主结算',
    rows: revenueRows.value.filter((row) => row.kind === 'settlement'),
  },
  {
    key: 'receivable',
    title: '应收款',
    rows: revenueRows.value.filter((row) => row.kind === 'receivable'),
  },
  {
    key: 'salesInvoice',
    title: '销项发票',
    rows: revenueRows.value.filter((row) => row.kind === 'salesInvoice'),
  },
  {
    key: 'collection',
    title: '回款',
    rows: revenueRows.value.filter((row) => row.kind === 'collection'),
  },
])
const hasRows = computed(() => rows.value.length > 0 || revenueRows.value.length > 0)
const loading = ref(false)
const errorMessage = ref('')
const busy = ref(false)
const dialog = ref(false)
const editor = ref<FinanceEditor | null>(null)
const editorKind = ref<EditorKind>('payment')
const pending = ref<{ row: Row; action: Action } | null>(null)
const contracts = ref<Array<{ id: string; contractCode: string; contractName: string }>>([])
const partners = ref<PartnerRecord[]>([])
const costSubjects = ref<CostSubjectOption[]>([])
const budgetLines = ref<BudgetLineRecord[]>([])
const payRecords = ref<PayRecordOption[]>([])
const paymentSources = ref<PaymentSourceOptionRecord[]>([])
const paymentAttachment = ref<File | null>(null)
let controller: AbortController | null = null

const text = (row: Row) =>
  'applyCode' in row
    ? row.applyCode
    : 'expenseCode' in row
      ? row.expenseCode
      : 'invoiceNo' in row
        ? row.invoiceNo
        : 'settlementCode' in row
          ? row.settlementCode
          : 'receivableCode' in row
            ? row.receivableCode
            : row.collectionCode
const project = (row: Row) => {
  if ('projectName' in row && row.projectName) return row.projectName
  const value = 'projectId' in row ? row.projectId : null
  return workspace.projects.find((option) => option.value === value)?.label || '项目名称缺失'
}
const status = (row: Row) =>
  'approvalStatus' in row
    ? row.approvalStatus
    : 'verifyStatus' in row
      ? row.verifyStatus
      : row.status || '—'
const money = (row: Row) =>
  formatAmount(
    'applyAmount' in row
      ? row.applyAmount
      : 'invoiceAmount' in row
        ? row.invoiceAmount
        : 'grossAmount' in row
          ? row.grossAmount
          : 'originalAmount' in row
            ? row.outstandingAmount
            : 'totalAmount' in row
              ? row.totalAmount
              : row.amount,
  )
const isDraft = (row: RecordRow) =>
  'approvalStatus' in row ? row.approvalStatus === 'DRAFT' : row.verifyStatus !== 'VERIFIED'
const canVerify = (row: RecordRow) =>
  'verifyStatus' in row && row.verifyStatus !== 'VERIFIED' && can('verify')

async function load(): Promise<void> {
  if (!canQuery.value) return
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    const query = { projectId: projectId.value || undefined }
    rows.value = []
    revenueRows.value = []
    if (mode.value === 'payment') {
      rows.value = (await loadPaymentApplications(query, request.signal)).records
    } else if (mode.value === 'expense') {
      rows.value = (await loadExpenseApplications(query, request.signal)).records
    } else if (mode.value === 'invoice') {
      rows.value = (await loadInvoices(query, request.signal)).records
    } else {
      const [settlements, receivables, salesInvoices, collections] = await Promise.all([
        loadRevenueSettlements(query, request.signal),
        loadReceivables(query, request.signal),
        loadSalesInvoices(query, request.signal),
        loadCollections(query, request.signal),
      ])
      revenueRows.value = [
        ...settlements.map((item) => ({ ...item, kind: 'settlement' as const })),
        ...receivables.map((item) => ({ ...item, kind: 'receivable' as const })),
        ...salesInvoices.map((item) => ({ ...item, kind: 'salesInvoice' as const })),
        ...collections.map((item) => ({ ...item, kind: 'collection' as const })),
      ]
    }
  } catch (cause) {
    if (!request.signal.aborted) {
      errorMessage.value = cause instanceof Error ? cause.message : '读取失败'
    }
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

async function refreshWorkspace(): Promise<void> {
  await load()
  if (!errorMessage.value) showToast('success', '刷新完成', '已读取最新数据。')
}

const projectOptions = computed(() => workspace.projects)
const contractOptions = computed(() =>
  contracts.value.map((item) => ({
    value: item.id,
    label: `${item.contractCode} · ${item.contractName}`,
  })),
)
const partnerOptions = computed(() =>
  partners.value.map((item) => ({
    value: item.id,
    label: `${item.partnerCode} · ${item.partnerName}`,
  })),
)
const customerOptions = computed(() =>
  partners.value
    .filter((item) => item.partnerType === 'CUSTOMER')
    .map((item) => ({
      value: item.id,
      label: `${item.partnerCode} · ${item.partnerName}`,
    })),
)
const costSubjectOptions = computed(() =>
  costSubjects.value.map((item) => ({
    value: item.id,
    label: `${item.subjectCode} · ${item.subjectName}`,
  })),
)
const budgetLineOptions = computed(() =>
  budgetLines.value
    .filter(
      (item) => !editor.value?.costSubjectId || item.costSubjectId === editor.value.costSubjectId,
    )
    .filter((item): item is BudgetLineRecord & { id: string } => Boolean(item.id))
    .map((item) => ({
      value: item.id,
      label: `${item.costSubjectName || item.costSubjectId} · 可用 ${formatAmount(item.availableAmount)}`,
    })),
)
const paymentSourceOptions = computed(() =>
  paymentSources.value.map((item) => ({
    value: `${item.sourceType}:${item.sourceRefId}`,
    label: `${item.documentCode} · 可付 ${formatAmount(item.availableAmount)}`,
  })),
)
const payRecordOptions = computed(() =>
  payRecords.value.map((item) => ({
    value: item.id,
    label: `${item.voucherNo || `付款记录 ${item.id}`} · ${formatAmount(item.payAmount)}`,
  })),
)
const payTypeOptions = [
  { value: 'FINAL', label: '结算付款' },
  { value: 'PROGRESS', label: '进度付款' },
]
const expenseCategoryOptions = [
  { value: 'CONTRACT', label: '合同费用' },
  { value: 'MATERIAL', label: '材料费用' },
  { value: 'LABOR', label: '人工费用' },
  { value: 'OTHER', label: '其他费用' },
]
const invoiceTypeOptions = [
  { value: 'VAT_SPECIAL', label: '增值税专用发票' },
  { value: 'VAT_NORMAL', label: '增值税普通发票' },
  { value: 'OTHER', label: '其他票据' },
]

async function loadContracts(value: string): Promise<void> {
  contracts.value = []
  if (!value) return
  const page = await loadContractPage({ pageNo: 1, pageSize: 200, projectId: value })
  contracts.value = page.records.map((item) => ({
    id: item.id,
    contractCode: item.contractCode,
    contractName: item.contractName,
  }))
}

async function loadBudgetLines(value: string): Promise<void> {
  budgetLines.value = []
  if (!value) return
  const page = await loadBudgetPage({ pageNo: 1, pageSize: 50, projectId: value })
  const details = await Promise.all(
    page.records
      .filter((item) => item.active || item.status === 'ACTIVE')
      .map((item) => loadBudget(item.id)),
  )
  budgetLines.value = details.flatMap((item) => item.lines || [])
}

async function changeProject(value: string): Promise<void> {
  if (!editor.value) return
  editor.value.projectId = value
  editor.value.contractId = ''
  editor.value.budgetLineId = ''
  editor.value.sourceType = ''
  editor.value.sourceRefId = ''
  paymentSources.value = []
  await Promise.all([
    loadContracts(value),
    editorKind.value === 'payment' || editorKind.value === 'expense'
      ? loadBudgetLines(value)
      : Promise.resolve(),
  ])
}

async function loadPaymentSources(): Promise<void> {
  paymentSources.value = []
  if (!editor.value || editorKind.value !== 'payment') return
  editor.value.sourceType = ''
  editor.value.sourceRefId = ''
  const { projectId, contractId, partnerId, payType, expenseCategory } = editor.value
  if (!projectId || !contractId || !partnerId || !payType) return
  paymentSources.value = await loadPaymentSourceOptions({
    projectId,
    contractId,
    partnerId,
    payType,
    expenseCategory,
  })
}

function choosePaymentSource(value: string): void {
  if (!editor.value) return
  const [sourceType, sourceRefId] = value.split(':', 2)
  editor.value.sourceType = sourceType || ''
  editor.value.sourceRefId = sourceRefId || ''
}

function onPaymentAttachment(event: Event): void {
  paymentAttachment.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function loadCandidates(kind: EditorKind): Promise<void> {
  const jobs: Promise<unknown>[] = []
  if (!partners.value.length && kind !== 'invoice') {
    jobs.push(loadPartners().then((page) => (partners.value = page.records)))
  }
  if ((kind === 'payment' || kind === 'expense') && !costSubjects.value.length) {
    jobs.push(loadCostSubjectOptions().then((items) => (costSubjects.value = items)))
  }
  if (kind === 'invoice' && !payRecords.value.length) {
    jobs.push(loadPayRecordOptions().then((page) => (payRecords.value = page.records)))
  }
  await Promise.all(jobs)
}

async function openForm(kind: EditorKind, row?: RecordRow): Promise<void> {
  const value = emptyEditor()
  editorKind.value = kind
  paymentAttachment.value = null
  paymentSources.value = []
  value.projectId = row?.projectId || projectId.value
  if (kind === 'payment') value.expenseCategory = 'SUBCONTRACT'
  if (row) {
    value.id = row.id
    value.contractId = row.contractId || ''
    if ('applyCode' in row) {
      value.partnerId = row.partnerId || ''
      value.payType = row.payType
      value.applyAmount = row.applyAmount
      value.applyReason = row.applyReason || ''
      value.expenseCategory = row.expenseCategory || 'CONTRACT'
    } else if ('expenseCode' in row) {
      value.costSubjectId = row.costSubjectId || ''
      value.budgetLineId = row.budgetLineId || ''
      value.payeePartnerId = row.payeePartnerId || ''
      value.expenseCategory = row.expenseCategory
      value.expenseDate = row.expenseDate
      value.amount = row.amount
      value.description = row.description || ''
    } else {
      value.payRecordId = row.payRecordId || ''
      value.invoiceNo = row.invoiceNo
      value.invoiceType = row.invoiceType || 'VAT_SPECIAL'
      value.invoiceAmount = row.invoiceAmount
      value.taxRate = row.taxRate || ''
      value.taxAmount = row.taxAmount || ''
      value.invoiceDate = row.invoiceDate || today()
      value.sellerName = row.sellerName || ''
      value.buyerName = row.buyerName || ''
    }
  }
  editor.value = value
  dialog.value = true
  try {
    await loadCandidates(kind)
    if (value.projectId && kind !== 'invoice') {
      await Promise.all([
        loadContracts(value.projectId),
        kind === 'payment' || kind === 'expense'
          ? loadBudgetLines(value.projectId)
          : Promise.resolve(),
      ])
    }
  } catch (cause) {
    dialog.value = false
    showToast('error', '候选项加载失败', cause instanceof Error ? cause.message : '请稍后重试。')
  }
}

function openCreate(): void {
  if (mode.value !== 'revenue') void openForm(mode.value)
}

function openEdit(row: RecordRow): void {
  const kind: EditorKind =
    'applyCode' in row ? 'payment' : 'expenseCode' in row ? 'expense' : 'invoice'
  void openForm(kind, row)
}

function required(value: string, label: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError(`${label}不能为空`)
  return normalized
}

function paymentCommand(value: FinanceEditor): PaymentApplicationCommand {
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

function expenseCommand(value: FinanceEditor): ExpenseApplicationCommand {
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

function invoiceCommand(value: FinanceEditor): InvoiceCommand {
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

function settlementCommand(value: FinanceEditor): OwnerSettlementCommand {
  return {
    projectId: required(value.projectId, '项目'),
    contractId: required(value.contractId, '合同'),
    customerId: required(value.customerId, '建设单位'),
    settlementPeriod: required(value.settlementPeriod, '结算期间'),
    settlementDate: required(value.settlementDate, '结算日期'),
    grossAmount: required(value.grossAmount, '含税结算金额'),
    taxAmount: required(value.taxAmount, '税额'),
    retentionAmount: required(value.retentionAmount, '质保金'),
    dueDate: required(value.dueDate, '到期日期'),
    attachmentCount: Number(value.attachmentCount || '0'),
    remark: value.remark.trim() || undefined,
  }
}

function salesInvoiceCommand(value: FinanceEditor): SalesInvoiceCommand {
  return {
    projectId: required(value.projectId, '项目'),
    contractId: required(value.contractId, '合同'),
    customerId: required(value.customerId, '建设单位'),
    invoiceNo: required(value.invoiceNo, '发票号码'),
    invoiceType: required(value.invoiceType, '发票类型'),
    invoiceDate: required(value.invoiceDate, '开票日期'),
    amountWithoutTax: required(value.amountWithoutTax, '不含税金额'),
    taxAmount: required(value.taxAmount, '税额'),
    attachmentCount: Number(value.attachmentCount || '0'),
    allocations: [],
    remark: value.remark.trim() || undefined,
  }
}

async function save(): Promise<void> {
  if (!editor.value || busy.value) return
  busy.value = true
  try {
    const value = editor.value
    if (editorKind.value === 'payment') {
      const command = paymentCommand(value)
      const sourceType = required(value.sourceType, '付款来源类型')
      const sourceRefId = required(value.sourceRefId, '付款来源')
      if (!value.id && !paymentAttachment.value) throw new TypeError('付款附件不能为空')
      const paymentId = value.id || (await createPayment(command))
      if (value.id) await updatePayment(value.id, command)
      await savePaymentSources(paymentId, [
        { sourceType, sourceRefId, sourceAmount: command.applyAmount },
      ])
      if (paymentAttachment.value) {
        await uploadSiteFile(paymentAttachment.value, 'PAYMENT', paymentId, 'PAYMENT_PROOF')
      }
    } else if (editorKind.value === 'expense') {
      const command = expenseCommand(value)
      if (value.id) await updateExpense(value.id, command)
      else await createExpense(command)
    } else if (editorKind.value === 'invoice') {
      const command = invoiceCommand(value)
      if (value.id) await updateInvoice(value.id, command)
      else await createInvoice(command)
    } else if (editorKind.value === 'settlement') {
      await createOwnerSettlement(settlementCommand(value))
    } else {
      await createSalesInvoice(salesInvoiceCommand(value))
    }
    dialog.value = false
    await load()
    showToast('success', '保存成功', '已按服务端最新数据刷新。')
  } catch (cause) {
    showToast('error', '保存失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

function requestAction(row: Row, action: Action): void {
  pending.value = { row, action }
}
const confirmationTitle = computed(() => {
  const action = pending.value?.action
  return action === 'delete'
    ? '确认删除记录'
    : action === 'submit'
      ? '确认提交审批'
      : action === 'verify'
        ? '确认标记验真通过'
        : action === 'credit'
          ? '确认核减全部未收金额'
          : '确认冲销回款'
})
const confirmationDescription = computed(() => {
  const row = pending.value?.row
  return row
    ? `${text(row)} 将执行${confirmationTitle.value.replace('确认', '')}，服务端仍会校验状态、权限和余额。`
    : ''
})

async function confirmAction(): Promise<void> {
  const value = pending.value
  pending.value = null
  if (value) await act(value.row, value.action)
}

async function act(row: Row, action: Action): Promise<void> {
  const id = String(row.id)
  if (!id || busy.value) return
  busy.value = true
  try {
    if (action === 'delete') {
      if (mode.value === 'payment') await deletePayment(id)
      else if (mode.value === 'expense') await deleteExpense(id)
      else await deleteInvoice(id)
    } else if (action === 'submit') {
      if (mode.value === 'payment') await submitPayment(id)
      else if (mode.value === 'expense') await submitExpense(id)
      else await submitOwnerSettlement(id)
    } else if (action === 'verify') {
      await verifyInvoice(id, 'VERIFIED')
    } else if (action === 'credit' && 'receivableCode' in row) {
      await creditReceivable(id, row.outstandingAmount, '人工核减', crypto.randomUUID())
    } else if (action === 'reverse' && 'collectionCode' in row) {
      await reverseCollection(id, '人工冲销', crypto.randomUUID())
    }
    await load()
    showToast('success', '操作成功', '已按服务端最新数据刷新。')
  } catch (cause) {
    showToast('error', '操作失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

watch([mode, projectId], () => void load(), { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-workspace">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      :title="`无权访问${title}`"
      description="系统未加载财务数据。"
    />
    <template v-else>
      <V2Card :title="title" :heading-level="1">
        <template #actions>
          <div class="finance-workspace__actions">
            <V2Button v-if="mode !== 'revenue' && canAdd" size="small" @click="openCreate">
              新建{{ title }}
            </V2Button>
            <V2Button
              v-if="mode === 'revenue' && canAdd"
              size="small"
              @click="openForm('settlement')"
            >
              新建业主结算
            </V2Button>
            <V2Button
              v-if="mode === 'revenue' && canAdd"
              size="small"
              variant="secondary"
              @click="openForm('salesInvoice')"
            >
              新建销项发票
            </V2Button>
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace">
              刷新
            </V2Button>
          </div>
        </template>
      </V2Card>

      <V2PageState
        v-if="loading && !hasRows"
        kind="loading"
        title="正在加载"
        :description="`正在读取${title}。`"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        :title="`${title}加载失败`"
        :description="errorMessage"
      >
        <template #actions><V2Button @click="load">重试</V2Button></template>
      </V2PageState>
      <V2PageState
        v-else-if="!hasRows"
        :title="`暂无${title}记录`"
        description="当前项目范围没有可访问数据。"
      />

      <section v-else-if="mode === 'revenue'" class="finance-workspace__revenue-sections">
        <section v-for="section in revenueSections" :key="section.key">
          <V2Card :title="section.title" :heading-level="2">
            <V2PageState
              v-if="!section.rows.length"
              :title="`暂无${section.title}记录`"
              description="当前项目范围没有可访问数据。"
            />
            <div
              v-else
              class="finance-workspace__table-wrap"
              role="region"
              :aria-label="`${section.title}表格`"
              tabindex="0"
            >
              <table class="v2-table finance-workspace__table">
                <thead>
                  <tr>
                    <th>编号</th>
                    <th>项目</th>
                    <th>状态</th>
                    <th>金额</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in section.rows" :key="`${row.kind}-${row.id}`">
                    <td>{{ text(row) }}</td>
                    <td>{{ project(row) }}</td>
                    <td>{{ status(row) }}</td>
                    <td>{{ money(row) }}</td>
                    <td>
                      <V2ActionMenu label="记录操作">
                        <V2Button
                          v-if="
                            row.kind === 'settlement' && row.status === 'DRAFT' && can('submit')
                          "
                          size="small"
                          variant="ghost"
                          @click="requestAction(row, 'submit')"
                        >
                          提交
                        </V2Button>
                        <V2Button
                          v-if="
                            row.kind === 'receivable' && row.status === 'OPEN' && can('maintain')
                          "
                          size="small"
                          variant="ghost"
                          @click="requestAction(row, 'credit')"
                        >
                          应收核减
                        </V2Button>
                        <V2Button
                          v-if="
                            row.kind === 'collection' &&
                            row.status === 'CONFIRMED' &&
                            can('reverse')
                          "
                          size="small"
                          variant="ghost"
                          @click="requestAction(row, 'reverse')"
                        >
                          回款冲销
                        </V2Button>
                      </V2ActionMenu>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </V2Card>
        </section>
      </section>

      <V2Card v-else :heading-level="2">
        <div
          class="finance-workspace__table-wrap"
          role="region"
          :aria-label="`${title}表格`"
          tabindex="0"
        >
          <table class="v2-table finance-workspace__table">
            <thead>
              <tr>
                <th>编号</th>
                <th>项目</th>
                <th>状态</th>
                <th>金额</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>{{ text(row) }}</td>
                <td>{{ project(row) }}</td>
                <td>{{ status(row) }}</td>
                <td>{{ money(row) }}</td>
                <td>
                  <V2ActionMenu label="记录操作">
                    <V2Button
                      v-if="isDraft(row) && can('edit')"
                      size="small"
                      variant="ghost"
                      @click="openEdit(row)"
                    >
                      编辑
                    </V2Button>
                    <V2Button
                      v-if="isDraft(row) && can('delete')"
                      size="small"
                      variant="ghost"
                      @click="requestAction(row, 'delete')"
                    >
                      删除
                    </V2Button>
                    <V2Button
                      v-if="mode !== 'invoice' && isDraft(row) && can('submit')"
                      size="small"
                      variant="ghost"
                      @click="requestAction(row, 'submit')"
                    >
                      提交
                    </V2Button>
                    <V2Button
                      v-if="canVerify(row)"
                      size="small"
                      variant="ghost"
                      @click="requestAction(row, 'verify')"
                    >
                      验真
                    </V2Button>
                  </V2ActionMenu>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>

      <V2Dialog
        v-model:open="dialog"
        :title="editor?.id ? `编辑${title}` : `新建${title}`"
        :close-disabled="busy"
        :close-on-backdrop="false"
      >
        <form
          v-if="editor"
          id="finance-workspace-editor-form"
          class="finance-workspace__form"
          @submit.prevent="save"
        >
          <template v-if="editorKind !== 'invoice'">
            <V2Select
              v-model="editor.projectId"
              label="项目"
              :options="projectOptions"
              required
              @update:model-value="changeProject"
            />
            <V2Select
              v-model="editor.contractId"
              label="合同"
              :options="contractOptions"
              required
              :disabled="!contractOptions.length"
            />
            <p v-if="!contractOptions.length">当前项目无可用合同，不能提交。</p>
          </template>

          <template v-if="editorKind === 'payment'">
            <V2Select
              v-model="editor.partnerId"
              label="往来单位"
              :options="partnerOptions"
              required
              @update:model-value="loadPaymentSources"
            />
            <V2Select
              v-model="editor.payType"
              label="付款类型"
              :options="payTypeOptions"
              required
              @update:model-value="loadPaymentSources"
            />
            <V2Select
              v-model="editor.costSubjectId"
              label="成本科目"
              :options="costSubjectOptions"
              required
              @update:model-value="editor.budgetLineId = ''"
            />
            <V2Select
              v-model="editor.budgetLineId"
              label="预算明细"
              :options="budgetLineOptions"
              required
            />
            <V2Select
              :model-value="
                editor.sourceType && editor.sourceRefId
                  ? `${editor.sourceType}:${editor.sourceRefId}`
                  : ''
              "
              label="付款来源"
              :options="paymentSourceOptions"
              required
              :disabled="!paymentSourceOptions.length"
              @update:model-value="choosePaymentSource"
            />
            <V2Input v-model="editor.applyAmount" label="申请金额" required hint="按字符串提交" />
            <V2Input v-model="editor.applyReason" label="申请事由" required />
            <label class="v2-field">
              <span class="v2-field__label">付款附件*</span>
              <input type="file" required @change="onPaymentAttachment" />
            </label>
          </template>

          <template v-else-if="editorKind === 'expense'">
            <V2Select
              v-model="editor.costSubjectId"
              label="成本科目"
              :options="costSubjectOptions"
              required
            />
            <V2Select
              v-model="editor.budgetLineId"
              label="预算明细"
              :options="budgetLineOptions"
              required
            />
            <V2Select
              v-model="editor.payeePartnerId"
              label="收款单位"
              :options="partnerOptions"
              required
            />
            <V2Select
              v-model="editor.expenseCategory"
              label="费用类别"
              :options="expenseCategoryOptions"
              required
            />
            <V2Input
              v-model="editor.expenseDate"
              label="费用日期"
              placeholder="YYYY-MM-DD"
              required
            />
            <V2Input v-model="editor.amount" label="费用金额" required hint="按字符串提交" />
            <V2Input v-model="editor.description" label="费用说明" required />
          </template>

          <template v-else-if="editorKind === 'invoice'">
            <V2Select
              v-model="editor.payRecordId"
              label="付款记录"
              :options="payRecordOptions"
              required
            />
            <V2Input v-model="editor.invoiceNo" label="发票号码" required />
            <V2Select
              v-model="editor.invoiceType"
              label="发票类型"
              :options="invoiceTypeOptions"
              required
            />
            <V2Input v-model="editor.invoiceAmount" label="发票金额" required hint="按字符串提交" />
            <V2Input
              v-model="editor.invoiceDate"
              label="开票日期"
              placeholder="YYYY-MM-DD"
              required
            />
            <V2Input v-model="editor.taxRate" label="税率" hint="按服务端字符串口径提交" />
            <V2Input v-model="editor.taxAmount" label="税额" hint="按服务端字符串口径提交" />
            <V2Input v-model="editor.sellerName" label="销售方" />
            <V2Input v-model="editor.buyerName" label="购买方" />
          </template>

          <template v-else-if="editorKind === 'settlement'">
            <V2Select
              v-model="editor.customerId"
              label="建设单位"
              :options="customerOptions"
              required
            />
            <V2Input
              v-model="editor.settlementPeriod"
              label="结算期间"
              placeholder="YYYY-MM"
              required
            />
            <V2Input
              v-model="editor.settlementDate"
              label="结算日期"
              placeholder="YYYY-MM-DD"
              required
            />
            <V2Input v-model="editor.grossAmount" label="含税结算金额" required />
            <V2Input v-model="editor.taxAmount" label="税额" required />
            <V2Input v-model="editor.retentionAmount" label="质保金" required />
            <V2Input v-model="editor.dueDate" label="到期日期" placeholder="YYYY-MM-DD" required />
          </template>

          <template v-else>
            <V2Select
              v-model="editor.customerId"
              label="建设单位"
              :options="customerOptions"
              required
            />
            <V2Input v-model="editor.invoiceNo" label="发票号码" required />
            <V2Select
              v-model="editor.invoiceType"
              label="发票类型"
              :options="invoiceTypeOptions"
              required
            />
            <V2Input
              v-model="editor.invoiceDate"
              label="开票日期"
              placeholder="YYYY-MM-DD"
              required
            />
            <V2Input v-model="editor.amountWithoutTax" label="不含税金额" required />
            <V2Input v-model="editor.taxAmount" label="税额" required />
          </template>

          <V2Input v-model="editor.remark" label="备注" />
        </form>
        <template #footer>
          <V2Button type="button" variant="secondary" :disabled="busy" @click="dialog = false">
            取消
          </V2Button>
          <V2Button type="submit" form="finance-workspace-editor-form" :loading="busy">
            保存
          </V2Button>
        </template>
      </V2Dialog>

      <V2ConfirmDialog
        :open="Boolean(pending)"
        :title="confirmationTitle"
        :description="confirmationDescription"
        confirm-text="确认执行"
        :loading="busy"
        @confirm="confirmAction"
        @close="pending = null"
      />
    </template>
  </section>
</template>

<style scoped>
.finance-workspace {
  display: grid;
  gap: var(--v2-space-3);
  min-width: 0;
}
.finance-workspace__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
}
.finance-workspace__table-wrap {
  max-width: 100%;
  overflow-x: auto;
}
.finance-workspace__table {
  min-width: 44rem;
}
.finance-workspace__form,
.finance-workspace__revenue-sections {
  display: grid;
  gap: var(--v2-space-3);
}
@media (max-width: 32.5rem) {
  .finance-workspace__actions > * {
    flex: 1 1 100%;
  }
}
</style>
