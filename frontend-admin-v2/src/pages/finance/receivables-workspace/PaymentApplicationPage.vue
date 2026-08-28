<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  V2ActionMenu,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
} from '@/components'
import PaymentTraceDialog from '@/components/finance/PaymentTraceDialog.vue'
import { showToast } from '@/components/toast'
import { dashboardStatusLabel, formatAmount } from '@/shared/display'
import {
  loadBudget,
  loadBudgetPage,
  loadContractPage,
  loadCostSubjectOptions,
  loadPartners,
  type CostSubjectOption,
} from '@/services/commercial'
import { uploadSiteFile } from '@/services/delivery'
import { isApiClientError } from '@/services/request'
import {
  createPayment,
  deletePayment,
  loadFundAccounts,
  loadPaymentApplications,
  loadPaymentSourceOptions,
  loadPaymentSources as loadStoredPaymentSources,
  loadPaymentTraceByApplication,
  loadPayRecordOptions,
  reversePaymentRecord,
  savePaymentSources,
  submitPayment,
  updatePayment,
  writebackPayment,
  type PaymentSourceOptionRecord,
} from '@/services/finance'
import { loadEnabledDictDataByCode, type DictDataRecord } from '@/services/system-management'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type {
  BudgetLineRecord,
  ContractRecord,
  FundAccountRecord,
  PartnerRecord,
  PaymentApplicationRecord,
  PaymentTraceRecord,
  PayRecordOption,
  PayRecordWritebackCommand,
} from '@cgc-pms/frontend-contracts'
import {
  contractOptions as buildContractOptions,
  defaultOption,
  dictionaryOptions,
  emptyPaymentEditor,
  leafCostSubjectOptions,
  linkedPartnerOptions,
  paymentCommand,
  required,
  type PaymentEditor,
} from './model'

type Action = 'delete' | 'submit'

interface WritebackEditor {
  payAmount: string
  paidAt: string
  fundAccountId: string
  payMethod: string
  voucherNo: string
  externalTxnNo: string
  remark: string
}

interface ReversalEditor {
  reversalType: 'REVERSAL' | 'REFUND'
  externalTxnNo: string
  reversedAt: string
  reason: string
}

const title = '付款申请'
const pageSize = 10
const session = useSessionStore()
const workspace = useWorkspaceStore()
const canQuery = computed(() => session.hasPermission('payment:app:query'))
const can = (action: string) => session.hasAdminOrPermission(`payment:app:${action}`)
const canAdd = computed(() => can('add'))
const canWriteback = computed(() => session.hasPermission('payment:record:writeback'))
const canReversePayment = computed(() => session.hasPermission('payment:record:reverse'))
const canDirectPayment = computed(() => session.hasPermission('payment:direct'))
const canTrace = computed(() => session.hasAdminOrPermission('payment:trace:query'))
const projectId = computed(() => workspace.selectedProjectId || '')

const rows = ref<PaymentApplicationRecord[]>([])
const pageNo = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const dialog = ref(false)
const editor = ref<PaymentEditor | null>(null)
const pending = ref<{ row: PaymentApplicationRecord; action: Action } | null>(null)
const contracts = ref<ContractRecord[]>([])
const partners = ref<PartnerRecord[]>([])
const costSubjects = ref<CostSubjectOption[]>([])
const budgetLines = ref<BudgetLineRecord[]>([])
const paymentSources = ref<PaymentSourceOptionRecord[]>([])
const payRecords = ref<PayRecordOption[]>([])
const fundAccounts = ref<FundAccountRecord[]>([])
const payTypes = ref<DictDataRecord[]>([])
const expenseCategories = ref<DictDataRecord[]>([])
const payMethods = ref<DictDataRecord[]>([])
const paymentAttachment = ref<File | null>(null)
const writebackTarget = ref<PaymentApplicationRecord | null>(null)
const writebackEditor = ref<WritebackEditor | null>(null)
const reversalTarget = ref<PayRecordOption | null>(null)
const reversalEditor = ref<ReversalEditor | null>(null)
const traceOpen = ref(false)
const traceRows = ref<PaymentTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
let controller: AbortController | null = null
let dictionariesLoaded = false

const hasRows = computed(() => rows.value.length > 0)
const projectOptions = computed(() =>
  workspace.projects.filter(
    (item) => item.status === 'ACTIVE' || item.value === editor.value?.projectId,
  ),
)
const selectedContract = computed(() =>
  contracts.value.find((item) => item.id === editor.value?.contractId),
)
const contractOptions = computed(() =>
  buildContractOptions(contracts.value, 'payment', editor.value?.contractId),
)
const partnerOptions = computed(() =>
  linkedPartnerOptions(partners.value, selectedContract.value?.partyBId, editor.value?.partnerId),
)
const costSubjectOptions = computed(() => leafCostSubjectOptions(costSubjects.value))
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
const paymentSourceOptions = computed(() => [
  ...paymentSources.value.map((item) => ({
    value: `${item.sourceType}:${item.sourceRefId}`,
    label: `${item.documentCode} · 可付 ${formatAmount(item.availableAmount)}`,
  })),
  ...(canDirectPayment.value && editor.value?.expenseCategory !== 'MATERIAL'
    ? [{ value: 'DIRECT:self', label: '直接付款（保存后绑定本申请）' }]
    : []),
])
const fundAccountOptions = computed(() =>
  fundAccounts.value
    .filter((item) => item.enabledFlag === 1)
    .map((item) => ({ value: item.id, label: `${item.accountCode} · ${item.accountName}` })),
)
const payTypeOptions = computed(() => dictionaryOptions(payTypes.value))
const expenseCategoryOptions = computed(() => dictionaryOptions(expenseCategories.value))
const payMethodOptions = computed(() => dictionaryOptions(payMethods.value))

function paymentRecord(row: PaymentApplicationRecord): PayRecordOption | undefined {
  return payRecords.value.find((record) => record.payApplicationId === row.id)
}

async function loadDictionaries(signal?: AbortSignal): Promise<void> {
  if (dictionariesLoaded) return
  const [nextPayTypes, nextExpenseCategories, nextPayMethods] = await Promise.all([
    loadEnabledDictDataByCode('pay_type', signal),
    loadEnabledDictDataByCode('expense_category', signal),
    loadEnabledDictDataByCode('pay_method', signal),
  ])
  payTypes.value = nextPayTypes
  expenseCategories.value = nextExpenseCategories
  payMethods.value = nextPayMethods
  dictionariesLoaded = true
}

async function load(preservePage = false): Promise<void> {
  if (!canQuery.value) return
  if (!preservePage) pageNo.value = 1
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    await loadDictionaries(request.signal)
    const [applications, records] = await Promise.all([
      loadPaymentApplications(
        { projectId: projectId.value || undefined, pageNo: pageNo.value, pageSize },
        request.signal,
      ),
      canReversePayment.value
        ? loadPayRecordOptions(request.signal)
        : Promise.resolve({ records: [], total: 0 }),
    ])
    const maxPage = Math.max(1, Math.ceil(applications.total / pageSize))
    if (pageNo.value > maxPage) {
      pageNo.value = maxPage
      await load(true)
      return
    }
    rows.value = applications.records
    total.value = applications.total
    payRecords.value = records.records
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

function changePage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= total.value) return
  pageNo.value = next
  void load(true)
}

async function loadContracts(value: string): Promise<void> {
  contracts.value = []
  if (!value) return
  contracts.value = (await loadContractPage({ pageNo: 1, pageSize: 200, projectId: value })).records
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

async function loadCandidates(): Promise<void> {
  const jobs: Promise<unknown>[] = []
  if (!partners.value.length)
    jobs.push(loadPartners().then((page) => (partners.value = page.records)))
  if (!costSubjects.value.length) {
    jobs.push(loadCostSubjectOptions().then((items) => (costSubjects.value = items)))
  }
  await Promise.all(jobs)
}

async function changeProject(value: string): Promise<void> {
  if (!editor.value) return
  editor.value.projectId = value
  editor.value.contractId = ''
  editor.value.partnerId = ''
  editor.value.budgetLineId = ''
  editor.value.sourceType = ''
  editor.value.sourceRefId = ''
  paymentSources.value = []
  await Promise.all([loadContracts(value), loadBudgetLines(value)])
}

async function changeContract(value: string): Promise<void> {
  if (!editor.value) return
  editor.value.contractId = value
  editor.value.partnerId = contracts.value.find((item) => item.id === value)?.partyBId || ''
  await loadPaymentSources()
}

async function loadPaymentSources(): Promise<void> {
  paymentSources.value = []
  if (!editor.value) return
  editor.value.sourceType = ''
  editor.value.sourceRefId = ''
  const { projectId: project, contractId, partnerId, payType, expenseCategory } = editor.value
  if (!project || !contractId || !partnerId || !payType) return
  paymentSources.value = await loadPaymentSourceOptions({
    projectId: project,
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

async function openForm(row?: PaymentApplicationRecord): Promise<void> {
  try {
    await loadDictionaries()
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : '请稍后重试。'
    errorMessage.value = message
    showToast('error', '业务字典加载失败', message)
    return
  }
  const value = emptyPaymentEditor()
  paymentAttachment.value = null
  paymentSources.value = []
  value.projectId = row?.projectId || projectId.value
  value.payType = defaultOption(payTypeOptions.value, 'FINAL')
  value.expenseCategory = defaultOption(expenseCategoryOptions.value, 'SUBCONTRACT')
  if (row) {
    value.id = row.id
    value.version = row.version
    value.contractId = row.contractId || ''
    value.partnerId = row.partnerId || ''
    value.payType = row.payType
    value.applyAmount = row.applyAmount
    value.applyReason = row.applyReason || ''
    value.expenseCategory =
      row.expenseCategory || defaultOption(expenseCategoryOptions.value, 'CONTRACT')
    value.costSubjectId = row.costSubjectId || ''
    value.budgetLineId = row.budgetLineId || ''
  }
  editor.value = value
  dialog.value = true
  try {
    await loadCandidates()
    if (value.projectId) {
      await Promise.all([loadContracts(value.projectId), loadBudgetLines(value.projectId)])
    }
    if (row) {
      await loadPaymentSources()
      const source = (await loadStoredPaymentSources(row.id))[0]
      value.sourceType = source?.sourceType || ''
      value.sourceRefId = source?.sourceRefId || ''
    }
  } catch (cause) {
    dialog.value = false
    showToast('error', '候选项加载失败', cause instanceof Error ? cause.message : '请稍后重试。')
  }
}

function onPaymentAttachment(event: Event): void {
  paymentAttachment.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function save(): Promise<void> {
  if (!editor.value || busy.value) return
  busy.value = true
  let createdPaymentId = ''
  try {
    const value = editor.value
    const command = paymentCommand(value)
    const sourceType = required(value.sourceType, '付款来源类型')
    if (value.expenseCategory === 'MATERIAL' && sourceType !== 'MAT_RECEIPT') {
      throw new TypeError('材料付款必须选择材料验收来源')
    }
    if (!value.id && !paymentAttachment.value) throw new TypeError('付款附件不能为空')
    const source = {
      sourceType,
      sourceRefId: sourceType === 'DIRECT' ? value.id : required(value.sourceRefId, '付款来源'),
      sourceAmount: command.applyAmount,
    }
    const paymentId = value.id || (await createPayment(command))
    if (!value.id) createdPaymentId = paymentId
    if (value.id) {
      if (value.version == null) throw new TypeError('付款申请版本缺失，请刷新后重试')
      await updatePayment(value.id, {
        ...command,
        expectedVersion: value.version,
        sources: [
          { ...source, sourceRefId: sourceType === 'DIRECT' ? value.id : source.sourceRefId },
        ],
      })
    }
    value.id = paymentId
    if (createdPaymentId) {
      await savePaymentSources(paymentId, [
        { ...source, sourceRefId: sourceType === 'DIRECT' ? paymentId : source.sourceRefId },
      ])
    }
    if (paymentAttachment.value) {
      await uploadSiteFile(paymentAttachment.value, 'PAYMENT', paymentId, 'PAYMENT_PROOF')
    }
    dialog.value = false
    createdPaymentId = ''
    await load()
    showToast('success', '保存成功', '已按服务端最新数据刷新。')
  } catch (cause) {
    if (isApiClientError(cause) && cause.code === 'PAY_APP_STATUS_CONFLICT') {
      dialog.value = false
      await load()
      showToast('error', '数据已变化', '付款申请已被其他用户修改，已刷新最新数据。')
      return
    }
    let message = cause instanceof Error ? cause.message : '请稍后重试。'
    if (createdPaymentId) {
      try {
        await deletePayment(createdPaymentId)
        if (editor.value?.id === createdPaymentId) editor.value.id = ''
        message += '；本次新建草稿已回滚'
      } catch (rollbackCause) {
        message += `；草稿回滚失败：${rollbackCause instanceof Error ? rollbackCause.message : '需要人工核对'}`
      }
    }
    showToast('error', '保存失败', message)
  } finally {
    busy.value = false
  }
}

async function openTrace(row: PaymentApplicationRecord): Promise<void> {
  traceOpen.value = true
  traceRows.value = []
  traceError.value = ''
  traceLoading.value = true
  try {
    traceRows.value = [await loadPaymentTraceByApplication(row.id)]
  } catch (cause) {
    traceError.value = cause instanceof Error ? cause.message : 'Trace 读取失败'
  } finally {
    traceLoading.value = false
  }
}

async function openWriteback(row: PaymentApplicationRecord): Promise<void> {
  busy.value = true
  try {
    const [accounts] = await Promise.all([loadFundAccounts(), loadDictionaries()])
    fundAccounts.value = accounts
    writebackTarget.value = row
    writebackEditor.value = {
      payAmount: row.approvedAmount,
      paidAt: '',
      fundAccountId: '',
      payMethod: defaultOption(payMethodOptions.value, 'BANK_TRANSFER'),
      voucherNo: '',
      externalTxnNo: '',
      remark: '',
    }
  } catch (cause) {
    showToast(
      'error',
      '付款候选项加载失败',
      cause instanceof Error ? cause.message : '请稍后重试。',
    )
  } finally {
    busy.value = false
  }
}

async function submitWriteback(): Promise<void> {
  const target = writebackTarget.value
  const value = writebackEditor.value
  if (!target || !value) return
  busy.value = true
  try {
    const command: PayRecordWritebackCommand = {
      payApplicationId: target.id,
      payAmount: required(value.payAmount, '付款金额'),
      paidAt: `${required(value.paidAt, '付款时间').replace('T', ' ')}:00`,
      fundAccountId: required(value.fundAccountId, '资金账户'),
      payMethod: required(value.payMethod, '付款方式'),
      voucherNo: value.voucherNo.trim() || undefined,
      externalTxnNo: required(value.externalTxnNo, '外部流水号'),
      remark: value.remark.trim() || undefined,
    }
    await writebackPayment(command)
    writebackTarget.value = null
    writebackEditor.value = null
    await load()
    showToast('success', '付款回写成功', '支付记录与资金日记账已刷新。')
  } catch (cause) {
    showToast('error', '付款回写失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

function closeWriteback(): void {
  if (busy.value) return
  writebackTarget.value = null
  writebackEditor.value = null
}

function openPaymentReversal(row: PaymentApplicationRecord): void {
  const record = paymentRecord(row)
  if (!record) return
  reversalTarget.value = record
  reversalEditor.value = { reversalType: 'REVERSAL', externalTxnNo: '', reversedAt: '', reason: '' }
}

async function submitPaymentReversal(): Promise<void> {
  const target = reversalTarget.value
  const value = reversalEditor.value
  if (!target || !value) return
  busy.value = true
  try {
    await reversePaymentRecord(target.id, {
      reversalType: value.reversalType,
      externalTxnNo: required(value.externalTxnNo, '冲销流水号'),
      reversedAt: `${required(value.reversedAt, '冲销时间').replace('T', ' ')}:00`,
      reason: required(value.reason, '冲销原因'),
    })
    reversalTarget.value = null
    reversalEditor.value = null
    await load()
    showToast('success', '支付冲销成功', '支付记录与资金台账已刷新。')
  } catch (cause) {
    showToast('error', '支付冲销失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

function closePaymentReversal(): void {
  if (busy.value) return
  reversalTarget.value = null
  reversalEditor.value = null
}

function requestAction(row: PaymentApplicationRecord, action: Action): void {
  pending.value = { row, action }
}

async function confirmAction(): Promise<void> {
  const value = pending.value
  pending.value = null
  if (!value || busy.value) return
  busy.value = true
  try {
    if (value.action === 'delete') await deletePayment(value.row.id)
    else await submitPayment(value.row.id)
    await load()
    showToast('success', '操作成功', '已按服务端最新数据刷新。')
  } catch (cause) {
    showToast('error', '操作失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

watch(projectId, () => void load(), { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-workspace">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      title="无权访问付款申请"
      description="系统未加载财务数据。"
    />
    <template v-else>
      <V2Card :title="title" :heading-level="1">
        <template #actions>
          <div class="finance-workspace__actions">
            <V2Button v-if="canAdd" size="small" @click="openForm()">新建付款申请</V2Button>
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
        description="正在读取付款申请。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="付款申请加载失败"
        :description="errorMessage"
      >
        <template #actions><V2Button @click="load">重试</V2Button></template>
      </V2PageState>
      <V2PageState
        v-else-if="!errorMessage && !hasRows"
        title="暂无付款申请记录"
        description="当前项目范围没有可访问数据。"
      />
      <V2Card v-else :heading-level="2">
        <div
          class="finance-workspace__table-wrap"
          role="region"
          aria-label="付款申请表格"
          tabindex="0"
        >
          <table class="v2-table finance-workspace__table">
            <thead>
              <tr>
                <th>编号</th>
                <th>项目</th>
                <th>状态</th>
                <th>金额</th>
                <th class="v2-table-cell--actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in rows" :key="row.id">
                <td>
                  <button
                    v-if="canTrace"
                    type="button"
                    class="v2-table__record-link"
                    @click="openTrace(row)"
                  >
                    {{ row.applyCode }}
                  </button>
                  <span v-else>{{ row.applyCode }}</span>
                </td>
                <td>
                  {{
                    row.projectName ||
                    workspace.projects.find((item) => item.value === row.projectId)?.label ||
                    '项目名称缺失'
                  }}
                </td>
                <td>
                  {{
                    dashboardStatusLabel(
                      ['PAID', 'PARTIALLY_PAID'].includes(row.payStatus || '')
                        ? row.payStatus
                        : row.approvalStatus,
                    )
                  }}
                </td>
                <td>{{ formatAmount(row.applyAmount) }}</td>
                <td class="v2-table-cell--actions">
                  <V2ActionMenu
                    :label="`${row.applyCode}更多操作`"
                    :placement="index >= rows.length - 3 ? 'top-end' : 'bottom-end'"
                  >
                    <V2Button
                      v-if="
                        row.approvalStatus === 'APPROVED' &&
                        row.payStatus === 'APPROVED' &&
                        canWriteback
                      "
                      size="small"
                      variant="ghost"
                      @click="openWriteback(row)"
                      >付款回写</V2Button
                    >
                    <V2Button
                      v-if="canReversePayment && Boolean(paymentRecord(row))"
                      size="small"
                      variant="ghost"
                      @click="openPaymentReversal(row)"
                      >支付冲销</V2Button
                    >
                    <V2Button
                      v-if="row.approvalStatus === 'DRAFT' && can('edit')"
                      size="small"
                      variant="ghost"
                      @click="openForm(row)"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="row.approvalStatus === 'DRAFT' && can('delete')"
                      size="small"
                      variant="ghost"
                      @click="requestAction(row, 'delete')"
                      >删除</V2Button
                    >
                    <V2Button
                      v-if="row.approvalStatus === 'DRAFT' && can('submit')"
                      size="small"
                      variant="ghost"
                      @click="requestAction(row, 'submit')"
                      >提交</V2Button
                    >
                  </V2ActionMenu>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="total"
            :page-no="pageNo"
            :page-size="pageSize"
            label="付款申请分页"
            :disabled="loading"
            @update:page-no="changePage"
          />
        </template>
      </V2Card>

      <V2Dialog
        v-model:open="dialog"
        :title="editor?.id ? '编辑付款申请' : '新建付款申请'"
        :close-disabled="busy"
        :close-on-backdrop="false"
      >
        <form
          v-if="editor"
          id="payment-application-form"
          class="finance-workspace__form finance-workspace__form--payment"
          @submit.prevent="save"
        >
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
            @update:model-value="changeContract"
          />
          <p v-if="!contractOptions.length">当前项目无可用合同，不能提交。</p>
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
            v-model="editor.expenseCategory"
            label="费用类别"
            :options="expenseCategoryOptions"
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
              editor.sourceType === 'DIRECT'
                ? 'DIRECT:self'
                : editor.sourceType && editor.sourceRefId
                  ? `${editor.sourceType}:${editor.sourceRefId}`
                  : ''
            "
            label="付款来源"
            :options="paymentSourceOptions"
            required
            :disabled="!paymentSourceOptions.length"
            @update:model-value="choosePaymentSource"
          />
          <V2Input
            v-model="editor.applyAmount"
            label="申请金额"
            :decimal-scale="2"
            required
            hint="按字符串提交"
          />
          <V2Input v-model="editor.applyReason" label="申请事由" required />
          <label class="v2-field"
            ><span class="v2-field__label">付款附件{{ editor.id ? '' : '*' }}</span
            ><input type="file" :required="!editor.id" @change="onPaymentAttachment"
          /></label>
        </form>
        <template #footer>
          <V2Button type="button" variant="secondary" :disabled="busy" @click="dialog = false"
            >取消</V2Button
          >
          <V2Button type="submit" form="payment-application-form" :loading="busy">保存</V2Button>
        </template>
      </V2Dialog>

      <V2Dialog
        :open="Boolean(writebackTarget)"
        title="付款回写"
        :close-disabled="busy"
        :close-on-backdrop="false"
        @update:open="(open) => !open && closeWriteback()"
      >
        <form
          v-if="writebackEditor"
          id="payment-writeback-form"
          class="finance-workspace__form"
          @submit.prevent="submitWriteback"
        >
          <V2Input
            v-model="writebackEditor.payAmount"
            label="付款金额"
            :decimal-scale="2"
            required
            hint="使用服务端批准金额，服务端负责余额校验"
          />
          <V2Input
            v-model="writebackEditor.paidAt"
            type="datetime-local"
            label="付款时间"
            required
          />
          <V2Select
            v-model="writebackEditor.fundAccountId"
            label="资金账户"
            :options="fundAccountOptions"
            required
          />
          <V2Select
            v-model="writebackEditor.payMethod"
            label="付款方式"
            :options="payMethodOptions"
            required
          />
          <V2Input v-model="writebackEditor.voucherNo" label="凭证号" />
          <V2Input v-model="writebackEditor.externalTxnNo" label="外部流水号" required />
          <V2Input v-model="writebackEditor.remark" label="备注" />
        </form>
        <template #footer>
          <V2Button type="button" variant="secondary" :disabled="busy" @click="closeWriteback"
            >取消</V2Button
          >
          <V2Button type="submit" form="payment-writeback-form" :loading="busy">确认回写</V2Button>
        </template>
      </V2Dialog>

      <V2Dialog
        :open="Boolean(reversalTarget)"
        title="支付冲销"
        description="生成反向支付事实，不删除原支付记录。"
        :close-disabled="busy"
        :close-on-backdrop="false"
        @close="closePaymentReversal"
      >
        <form
          v-if="reversalEditor"
          id="payment-reversal-form"
          class="finance-workspace__form"
          @submit.prevent="submitPaymentReversal"
        >
          <V2Select
            v-model="reversalEditor.reversalType"
            label="冲销类型"
            :options="[
              { value: 'REVERSAL', label: '会计冲销' },
              { value: 'REFUND', label: '银行退款' },
            ]"
            required
          />
          <V2Input v-model="reversalEditor.externalTxnNo" label="冲销流水号" required />
          <V2Input
            v-model="reversalEditor.reversedAt"
            type="datetime-local"
            label="冲销时间"
            required
          />
          <V2Input v-model="reversalEditor.reason" label="冲销原因" required />
        </form>
        <template #footer>
          <V2Button type="button" variant="secondary" :disabled="busy" @click="closePaymentReversal"
            >取消</V2Button
          >
          <V2Button type="submit" form="payment-reversal-form" :loading="busy">确认冲销</V2Button>
        </template>
      </V2Dialog>

      <V2ConfirmDialog
        :open="Boolean(pending)"
        :title="pending?.action === 'delete' ? '确认删除记录' : '确认提交审批'"
        :description="
          pending
            ? `${pending.row.applyCode} 将执行${pending.action === 'delete' ? '删除记录' : '提交审批'}，服务端仍会校验状态、权限和余额。`
            : ''
        "
        confirm-text="确认执行"
        :loading="busy"
        @confirm="confirmAction"
        @close="pending = null"
      />
      <PaymentTraceDialog
        :open="traceOpen"
        :traces="traceRows"
        :loading="traceLoading"
        :error="traceError"
        @close="traceOpen = false"
      />
    </template>
  </section>
</template>

<style scoped src="./finance-workspace.css"></style>
