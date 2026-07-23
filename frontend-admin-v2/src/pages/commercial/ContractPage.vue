<script setup lang="ts">
import type {
  ContractCompositeRecord,
  ContractItemRecord,
  ContractKpi,
  ContractPage,
  ContractPaymentTermRecord,
  ContractQuery,
  ContractSaveCommand,
  ContractType,
  PartnerRecord,
  ProjectContextOption,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Input,
  V2PageState,
  V2Select,
} from '@/components'
import { formatAmount } from '@/pages/dashboard/model'
import {
  createContractComposite,
  deleteContract,
  loadContractComposite,
  loadContractKpi,
  loadContractPage,
  loadPartners,
  loadProjectContextOptions,
  submitContract,
  updateContractComposite,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'

const CONTRACT_TYPE_OPTIONS: Array<{ value: ContractType; label: string }> = [
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

const CONTRACT_PRESET_VIEWS: Array<{
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

function contractTypeLabel(value?: string | null): string {
  return CONTRACT_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? '未知类型'
}

function contractStatusLabel(value?: string | null): string {
  return CONTRACT_STATUS_OPTIONS.find((option) => option.value === value)?.label ?? '未知状态'
}

function approvalStatusLabel(value?: string | null): string {
  return APPROVAL_STATUS_OPTIONS.find((option) => option.value === value)?.label ?? '未知状态'
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const deleting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const contracts = ref<ContractPage['records']>([])
const total = ref(0)
const kpi = ref<ContractKpi | null>(null)
const detail = ref<ContractCompositeRecord | null>(null)
const projects = ref<ProjectContextOption[]>([])
const partners = ref<PartnerRecord[]>([])
const form = ref<ContractSaveCommand>(emptyCommand())
const pendingDelete = ref(false)

const filter = reactive<ContractQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  projectId: '',
  contractType: undefined,
  contractStatus: undefined,
  approvalStatus: undefined,
})

let listGeneration = 0
let detailGeneration = 0
let listController: AbortController | null = null
let detailController: AbortController | null = null
let refController: AbortController | null = null

const mode = computed<'ledger' | 'create' | 'detail' | 'edit'>(() => {
  if (route.path === '/contract/ledger') return 'ledger'
  if (route.path === '/contract/create') return 'create'
  if (route.path.endsWith('/edit')) return 'edit'
  return 'detail'
})

const contractId = computed(() =>
  typeof route.params.id === 'string' ? route.params.id.trim() : '',
)
const canCreate = computed(() => session.hasPermission('contract:add'))
const canEdit = computed(() => session.hasPermission('contract:edit'))
const canSubmit = computed(() => session.hasPermission('contract:submit'))
const canDelete = computed(() => session.hasPermission('contract:delete'))
const canQuery = computed(() => session.hasPermission('contract:query'))
const currentContract = computed(() => detail.value?.contract ?? null)
const currentContractIsDraft = computed(() => currentContract.value?.approvalStatus === 'DRAFT')
const pageCount = computed(() => {
  const pageSize = filter.pageSize ?? 10
  return Math.max(1, Math.ceil(total.value / pageSize))
})
const activePresetView = computed(
  () =>
    CONTRACT_PRESET_VIEWS.find(
      (preset) =>
        preset.contractStatus === filter.contractStatus &&
        preset.approvalStatus === filter.approvalStatus,
    )?.id ?? '',
)
const formLocked = computed(
  () =>
    saving.value ||
    submitting.value ||
    deleting.value ||
    (mode.value === 'edit' && !currentContractIsDraft.value),
)

function emptyCommand(): ContractSaveCommand {
  return {
    contract: {
      projectId: typeof route.query.projectId === 'string' ? route.query.projectId.trim() : '',
      contractName: '',
      contractType: 'MAIN',
      partyAId: '',
      partyBId: '',
      contractAmount: '',
      currentAmount: '',
      paidAmount: '',
      taxRate: '',
      taxAmount: '',
      amountWithoutTax: '',
      signedDate: '',
      startDate: '',
      endDate: '',
      paymentMethod: '',
      settlementMethod: '',
      settlementAmount: '',
      version: '',
      remark: '',
    },
    items: [],
    paymentTerms: [],
  }
}

function blankItem(index = form.value.items.length): ContractItemRecord {
  return {
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

function blankTerm(index = form.value.paymentTerms.length): ContractPaymentTermRecord {
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

function cloneCommandFromDetail(value: ContractCompositeRecord): ContractSaveCommand {
  return {
    contract: {
      id: value.contract.id,
      projectId: value.contract.projectId,
      contractName: value.contract.contractName,
      contractType: value.contract.contractType,
      partyAId: value.contract.partyAId,
      partyBId: value.contract.partyBId,
      contractAmount: value.contract.contractAmount ?? '',
      currentAmount: value.contract.currentAmount ?? '',
      paidAmount: value.contract.paidAmount ?? '',
      taxRate: value.contract.taxRate ?? '',
      taxAmount: value.contract.taxAmount ?? '',
      amountWithoutTax: value.contract.amountWithoutTax ?? '',
      signedDate: value.contract.signedDate ?? '',
      startDate: value.contract.startDate ?? '',
      endDate: value.contract.endDate ?? '',
      paymentMethod: value.contract.paymentMethod ?? '',
      settlementMethod: value.contract.settlementMethod ?? '',
      settlementAmount: value.contract.settlementAmount ?? '',
      version: value.contract.version ?? '',
      remark: value.contract.remark ?? '',
    },
    items: value.items.map((item) => ({
      id: item.id ?? '',
      contractId: item.contractId ?? value.contract.id,
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

function resetNotices(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

function hydrateFilter(): void {
  filter.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  filter.projectId = typeof route.query.projectId === 'string' ? route.query.projectId : ''
  filter.contractStatus =
    typeof route.query.contractStatus === 'string'
      ? (route.query.contractStatus as ContractQuery['contractStatus'])
      : undefined
  filter.approvalStatus =
    typeof route.query.approvalStatus === 'string'
      ? (route.query.approvalStatus as ContractQuery['approvalStatus'])
      : undefined
  filter.contractType =
    typeof route.query.contractType === 'string'
      ? (route.query.contractType as ContractType)
      : undefined
  const periodBounds = reportPeriodBounds(
    typeof route.query.period === 'string' ? route.query.period : null,
  )
  filter.startDate = periodBounds?.startDate
  filter.endDate = periodBounds?.endDate
  const pageNo = typeof route.query.pageNo === 'string' ? Number(route.query.pageNo) : 1
  filter.pageNo = Number.isInteger(pageNo) && pageNo > 0 ? pageNo : 1
}

async function replaceLedgerQuery(): Promise<boolean> {
  const location = {
    path: '/contract/ledger',
    query: {
      ...(filter.keyword ? { keyword: filter.keyword } : {}),
      ...(filter.projectId ? { projectId: filter.projectId } : {}),
      ...(filter.contractType ? { contractType: filter.contractType } : {}),
      ...(filter.contractStatus ? { contractStatus: filter.contractStatus } : {}),
      ...(filter.approvalStatus ? { approvalStatus: filter.approvalStatus } : {}),
      ...(typeof route.query.period === 'string' ? { period: route.query.period } : {}),
      ...(filter.pageNo && filter.pageNo > 1 ? { pageNo: String(filter.pageNo) } : {}),
    },
    hash: route.hash,
  }
  if (router.resolve(location).fullPath === route.fullPath) return false
  await router.replace(location)
  return true
}

async function loadReferenceData(): Promise<void> {
  refController?.abort()
  const controller = new AbortController()
  refController = controller
  try {
    const projectOptions = await loadProjectContextOptions(controller.signal)
    if (refController !== controller) return
    projects.value = projectOptions
    if (mode.value === 'create' || mode.value === 'edit') {
      const partnerPage = await loadPartners(undefined, controller.signal)
      if (refController !== controller) return
      partners.value = partnerPage.records
    } else {
      partners.value = []
    }
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '合同候选数据加载失败')
  } finally {
    if (refController === controller) refController = null
  }
}

async function loadLedger(preserveNotice = false): Promise<void> {
  hydrateFilter()
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const [page, summary] = await Promise.all([
      loadContractPage(filter, controller.signal),
      loadContractKpi(
        {
          projectId: filter.projectId,
          contractType: filter.contractType,
          contractStatus: filter.contractStatus,
          approvalStatus: filter.approvalStatus,
          startDate: filter.startDate,
          endDate: filter.endDate,
        },
        controller.signal,
      ),
    ])
    if (generation !== listGeneration) return
    contracts.value = page.records
    total.value = page.total
    kpi.value = summary
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      contracts.value = []
      total.value = 0
      kpi.value = null
      errorMessage.value = errorText(error, '合同台账加载失败')
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function loadDetail(preserveNotice = false): Promise<void> {
  if (!contractId.value) return
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const value = await loadContractComposite(contractId.value, controller.signal)
    if (generation !== detailGeneration) return
    detail.value = value
    if (mode.value === 'edit') form.value = cloneCommandFromDetail(value)
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      detail.value = null
      errorMessage.value = errorText(error, '合同详情加载失败')
    }
  } finally {
    if (generation === detailGeneration) loading.value = false
  }
}

function contractTitle(): string {
  if (mode.value === 'ledger') return '合同台账'
  if (mode.value === 'create') return '新建合同'
  if (mode.value === 'edit') return '编辑合同'
  return currentContract.value?.contractName || '合同详情'
}

function projectLabel(projectId?: string | null): string {
  return projects.value.find((item) => item.id === projectId)?.projectName ?? projectId ?? '—'
}

function partnerLabel(partnerId?: string | null): string {
  return partners.value.find((item) => item.id === partnerId)?.partnerName ?? partnerId ?? '—'
}

function updateContractField(
  key: keyof ContractSaveCommand['contract'],
  value: string | ContractType,
): void {
  form.value = {
    ...form.value,
    contract: { ...form.value.contract, [key]: value },
  }
}

function updateContractType(value: string): void {
  updateContractField('contractType', value as ContractType)
}

function updateItem(index: number, key: keyof ContractItemRecord, value: string): void {
  form.value = {
    ...form.value,
    items: form.value.items.map((item, itemIndex) =>
      itemIndex === index ? { ...item, [key]: value } : item,
    ),
  }
}

function updateTerm(index: number, key: keyof ContractPaymentTermRecord, value: string): void {
  form.value = {
    ...form.value,
    paymentTerms: form.value.paymentTerms.map((term, termIndex) =>
      termIndex === index ? { ...term, [key]: value } : term,
    ),
  }
}

function addItem(): void {
  form.value = { ...form.value, items: [...form.value.items, blankItem()] }
}

function removeItem(index: number): void {
  form.value = {
    ...form.value,
    items: form.value.items.filter((_, itemIndex) => itemIndex !== index),
  }
}

function addTerm(): void {
  form.value = { ...form.value, paymentTerms: [...form.value.paymentTerms, blankTerm()] }
}

function removeTerm(index: number): void {
  form.value = {
    ...form.value,
    paymentTerms: form.value.paymentTerms.filter((_, termIndex) => termIndex !== index),
  }
}

function cleaned(value?: string | null): string | null {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

function sanitizeCommand(value: ContractSaveCommand): ContractSaveCommand {
  return {
    contract: {
      ...value.contract,
      id: cleaned(value.contract.id),
      projectId: cleaned(value.contract.projectId),
      contractName: value.contract.contractName.trim(),
      partyAId: cleaned(value.contract.partyAId),
      partyBId: cleaned(value.contract.partyBId),
      contractAmount: cleaned(value.contract.contractAmount),
      currentAmount: cleaned(value.contract.currentAmount),
      paidAmount: cleaned(value.contract.paidAmount),
      taxRate: cleaned(value.contract.taxRate),
      taxAmount: cleaned(value.contract.taxAmount),
      amountWithoutTax: cleaned(value.contract.amountWithoutTax),
      signedDate: cleaned(value.contract.signedDate),
      startDate: cleaned(value.contract.startDate),
      endDate: cleaned(value.contract.endDate),
      paymentMethod: cleaned(value.contract.paymentMethod),
      settlementMethod: cleaned(value.contract.settlementMethod),
      settlementAmount: cleaned(value.contract.settlementAmount),
      version: cleaned(String(value.contract.version ?? '')),
      remark: cleaned(value.contract.remark),
    },
    items: value.items.map((item, index) => ({
      ...item,
      id: cleaned(item.id ?? ''),
      contractId: cleaned(item.contractId ?? ''),
      itemCode: cleaned(item.itemCode ?? ''),
      itemName: item.itemName.trim(),
      itemSpec: cleaned(item.itemSpec ?? ''),
      unit: cleaned(item.unit ?? ''),
      quantity: cleaned(item.quantity ?? ''),
      unitPrice: cleaned(item.unitPrice ?? ''),
      amount: cleaned(item.amount ?? ''),
      taxRate: cleaned(item.taxRate ?? ''),
      taxAmount: cleaned(item.taxAmount ?? ''),
      amountWithoutTax: cleaned(item.amountWithoutTax ?? ''),
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

function validateForm(command: ContractSaveCommand): string | null {
  if (!command.contract.projectId) return '项目不能为空'
  if (!command.contract.contractName) return '合同名称不能为空'
  if (!command.contract.partyAId || !command.contract.partyBId) return '甲乙方不能为空'
  if (!command.contract.contractAmount) return '合同金额不能为空'
  if (command.items.some((item) => !item.itemName?.trim())) return '合同清单名称不能为空'
  if (command.paymentTerms.some((term) => !term.termName?.trim())) return '付款条款名称不能为空'
  return null
}

async function saveContract(): Promise<void> {
  if (formLocked.value) return
  const command = sanitizeCommand(form.value)
  const validation = validateForm(command)
  if (validation) {
    errorMessage.value = validation
    return
  }
  saving.value = true
  resetNotices()
  try {
    if (mode.value === 'create') {
      const id = await createContractComposite(command)
      await router.replace({ path: `/contract/${id}`, query: route.query })
      successMessage.value = '合同已创建，并已刷新最新数据。'
      return
    }
    await updateContractComposite(contractId.value, command)
    await loadDetail(true)
    successMessage.value = '合同已保存，并已刷新最新数据。'
  } catch (error) {
    errorMessage.value = errorText(error, '合同保存失败')
    if (mode.value === 'edit' && contractId.value) {
      await loadDetail(true)
      if (isApiClientError(error) && error.status === 409) {
        form.value = detail.value ? cloneCommandFromDetail(detail.value) : form.value
        errorMessage.value = `${error.message}；已刷新最新数据`
      }
    }
  } finally {
    saving.value = false
  }
}

async function submitCurrentContract(): Promise<void> {
  if (!contractId.value || submitting.value) return
  submitting.value = true
  resetNotices()
  try {
    await submitContract(
      contractId.value,
      currentContract.value?.version ?? form.value.contract.version,
    )
    await loadDetail(true)
    successMessage.value = '合同已提交审批。'
  } catch (error) {
    errorMessage.value = errorText(error, '合同提交失败')
    await loadDetail(true)
    if (isApiClientError(error) && error.status === 409) {
      errorMessage.value = `${error.message}；草稿已保留并已刷新最新数据`
    }
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!contractId.value || deleting.value) return
  deleting.value = true
  resetNotices()
  try {
    await deleteContract(contractId.value)
    pendingDelete.value = false
    await router.push({ path: '/contract/ledger', query: route.query })
  } catch (error) {
    errorMessage.value = errorText(error, '合同删除失败')
    await loadDetail(true)
  } finally {
    deleting.value = false
  }
}

function closeDeleteDialog(): void {
  if (!deleting.value) pendingDelete.value = false
}

function openEdit(): void {
  if (!contractId.value) return
  void router.push({ path: `/contract/${contractId.value}/edit`, query: route.query })
}

function openDetail(id: string): void {
  void router.push({ path: `/contract/${id}`, query: route.query })
}

function openCreate(): void {
  void router.push({ path: '/contract/create', query: route.query })
}

function backToLedger(): void {
  void router.push({ path: '/contract/ledger', query: route.query })
}

async function applyPresetView(preset: (typeof CONTRACT_PRESET_VIEWS)[number]): Promise<void> {
  filter.keyword = ''
  filter.contractType = undefined
  filter.contractStatus = preset.contractStatus
  filter.approvalStatus = preset.approvalStatus
  filter.pageNo = 1
  if (!(await replaceLedgerQuery())) await loadLedger()
}

async function goPage(nextPage: number): Promise<void> {
  if (nextPage < 1 || nextPage > pageCount.value) return
  filter.pageNo = nextPage
  if (!(await replaceLedgerQuery())) await loadLedger()
}

function startCreate(): void {
  detail.value = null
  form.value = emptyCommand()
}

watch(
  () => route.fullPath,
  async () => {
    await loadReferenceData()
    if (mode.value === 'ledger') await loadLedger()
    else if (mode.value === 'create') {
      resetNotices()
      startCreate()
    } else {
      await loadDetail()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
  refController?.abort()
})
</script>

<template>
  <section class="contract-page" aria-labelledby="contract-title">
    <V2Alert v-if="errorMessage" tone="danger" title="请求未完成">{{ errorMessage }}</V2Alert>
    <V2Alert v-if="successMessage" tone="success" title="操作完成">{{ successMessage }}</V2Alert>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载合同数据"
      description="请稍候。"
      title-id="contract-title"
      :heading-level="1"
    />

    <template v-else-if="mode === 'ledger'">
      <V2Card v-if="kpi">
        <dl class="contract-page__kpi-grid">
          <div>
            <dt>合同总数</dt>
            <dd>{{ kpi.totalCount }}</dd>
          </div>
          <div>
            <dt>合同总额</dt>
            <dd>{{ formatAmount(kpi.totalAmount) }}</dd>
          </div>
          <div>
            <dt>累计已付</dt>
            <dd>{{ formatAmount(kpi.paidAmount) }}</dd>
          </div>
          <div>
            <dt>未付金额</dt>
            <dd>{{ formatAmount(kpi.unpaidAmount) }}</dd>
          </div>
          <div>
            <dt>逾期合同</dt>
            <dd>{{ kpi.overdueCount }}</dd>
          </div>
        </dl>
      </V2Card>

      <V2Card
        class="contract-page__list-card"
        title="合同列表"
        title-id="contract-title"
        :heading-level="1"
      >
        <template #actions>
          <V2Button v-if="canCreate" @click="openCreate">新建合同</V2Button>
        </template>
        <nav class="contract-page__preset-views" aria-label="合同预设视图">
          <V2Button
            v-for="preset in CONTRACT_PRESET_VIEWS"
            :key="preset.id"
            size="small"
            :variant="activePresetView === preset.id ? 'primary' : 'ghost'"
            :aria-pressed="activePresetView === preset.id"
            :disabled="loading"
            @click="applyPresetView(preset)"
          >
            {{ preset.label }}
          </V2Button>
        </nav>

        <V2PageState
          v-if="!contracts.length"
          kind="empty"
          title="暂无可见合同"
          description="调整筛选条件，或联系管理员核对项目和权限范围。"
          :heading-level="3"
        />

        <div v-else class="contract-page__table-wrap" tabindex="0">
          <table class="contract-page__table">
            <caption class="v2-visually-hidden">
              合同列表
            </caption>
            <thead>
              <tr>
                <th scope="col">合同编号 / 名称</th>
                <th scope="col">项目</th>
                <th scope="col">类型</th>
                <th scope="col">合同状态</th>
                <th scope="col">审批状态</th>
                <th scope="col">当前金额</th>
                <th scope="col">已付金额</th>
                <th scope="col">乙方</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="contract in contracts" :key="contract.id">
                <td>
                  <strong>{{ contract.contractCode }}</strong>
                  <span>{{ contract.contractName }}</span>
                </td>
                <td>{{ projectLabel(contract.projectId) }}</td>
                <td>
                  <V2Badge tone="info">{{ contractTypeLabel(contract.contractType) }}</V2Badge>
                </td>
                <td>
                  <V2Badge tone="info">{{ contractStatusLabel(contract.contractStatus) }}</V2Badge>
                </td>
                <td>
                  <V2Badge tone="info">{{ approvalStatusLabel(contract.approvalStatus) }}</V2Badge>
                </td>
                <td>{{ formatAmount(contract.currentAmount) }}</td>
                <td>{{ formatAmount(contract.paidAmount) }}</td>
                <td>{{ contract.partyBName || partnerLabel(contract.partyBId) }}</td>
                <td>
                  <div class="contract-page__actions">
                    <V2Button size="small" variant="secondary" @click="openDetail(contract.id)"
                      >详情</V2Button
                    >
                    <V2Button
                      v-if="canEdit && contract.approvalStatus === 'DRAFT'"
                      size="small"
                      variant="ghost"
                      @click="
                        router.push({ path: `/contract/${contract.id}/edit`, query: route.query })
                      "
                      >编辑</V2Button
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <template v-if="contracts.length" #footer>
          <nav class="contract-page__pagination" aria-label="合同分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="(filter.pageNo ?? 1) <= 1"
              @click="goPage((filter.pageNo ?? 1) - 1)"
              >上一页</V2Button
            >
            <span>第 {{ filter.pageNo ?? 1 }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="(filter.pageNo ?? 1) >= pageCount"
              @click="goPage((filter.pageNo ?? 1) + 1)"
              >下一页</V2Button
            >
          </nav>
        </template>
      </V2Card>
    </template>

    <template v-else-if="mode === 'create' || detail">
      <V2Card
        :title="contractTitle()"
        title-id="contract-title"
        :heading-level="1"
        :subtitle="
          mode === 'detail' && currentContract
            ? `${currentContract.contractCode} · ${projectLabel(currentContract.projectId)}`
            : undefined
        "
      >
        <template #actions>
          <div class="contract-page__actions">
            <V2Button size="small" variant="ghost" @click="backToLedger">返回台账</V2Button>
            <V2Button
              v-if="mode === 'detail' && canEdit && currentContractIsDraft"
              size="small"
              variant="secondary"
              @click="openEdit"
              >编辑</V2Button
            >
            <V2Button
              v-if="mode === 'detail' && canSubmit && currentContractIsDraft"
              size="small"
              :loading="submitting"
              @click="submitCurrentContract"
              >提交审批</V2Button
            >
            <V2Button
              v-if="mode === 'detail' && canDelete && currentContractIsDraft"
              size="small"
              variant="danger"
              :loading="deleting"
              @click="pendingDelete = true"
              >删除</V2Button
            >
          </div>
        </template>
      </V2Card>

      <div v-if="mode === 'detail' && currentContract" class="contract-page__detail-grid">
        <V2Card title="合同头">
          <dl>
            <dt>项目</dt>
            <dd>{{ projectLabel(currentContract.projectId) }}</dd>
            <dt>甲方</dt>
            <dd>{{ currentContract.partyAName || partnerLabel(currentContract.partyAId) }}</dd>
            <dt>乙方</dt>
            <dd>{{ currentContract.partyBName || partnerLabel(currentContract.partyBId) }}</dd>
            <dt>合同额</dt>
            <dd>{{ formatAmount(currentContract.contractAmount) }}</dd>
            <dt>当前额</dt>
            <dd>{{ formatAmount(currentContract.currentAmount) }}</dd>
            <dt>已付额</dt>
            <dd>{{ formatAmount(currentContract.paidAmount) }}</dd>
            <dt>结算额</dt>
            <dd>{{ formatAmount(currentContract.settlementAmount) }}</dd>
            <dt>税额</dt>
            <dd>{{ formatAmount(currentContract.taxAmount) }}</dd>
            <dt>不含税额</dt>
            <dd>{{ formatAmount(currentContract.amountWithoutTax) }}</dd>
            <dt>审批状态</dt>
            <dd>{{ approvalStatusLabel(currentContract.approvalStatus) }}</dd>
          </dl>
        </V2Card>
        <V2Card title="合同清单" :subtitle="`共 ${detail?.items.length ?? 0} 条`">
          <div
            v-if="detail?.items.length"
            class="contract-page__table-wrap"
            role="region"
            aria-label="合同清单表格"
            tabindex="0"
          >
            <table class="contract-page__table contract-page__detail-table">
              <thead>
                <tr>
                  <th scope="col">名称</th>
                  <th scope="col">编号</th>
                  <th scope="col">规格</th>
                  <th scope="col">单位</th>
                  <th scope="col">数量</th>
                  <th scope="col">单价</th>
                  <th scope="col">金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail?.items" :key="item.id || item.itemName">
                  <td>{{ item.itemName }}</td>
                  <td>{{ item.itemCode || '未编号' }}</td>
                  <td>{{ item.itemSpec || '—' }}</td>
                  <td>{{ item.unit || '—' }}</td>
                  <td>{{ item.quantity || '—' }}</td>
                  <td>{{ formatAmount(item.unitPrice || null) }}</td>
                  <td>{{ formatAmount(item.amount || null) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-else
            kind="empty"
            title="暂无合同清单"
            description="当前合同还没有明细。"
            :heading-level="3"
          />
        </V2Card>
        <V2Card title="付款条款" :subtitle="`共 ${detail?.paymentTerms.length ?? 0} 条`">
          <div
            v-if="detail?.paymentTerms.length"
            class="contract-page__table-wrap"
            role="region"
            aria-label="付款条款表格"
            tabindex="0"
          >
            <table class="contract-page__table contract-page__detail-table">
              <thead>
                <tr>
                  <th scope="col">条款名称</th>
                  <th scope="col">付款比例</th>
                  <th scope="col">付款金额</th>
                  <th scope="col">付款条件</th>
                  <th scope="col">计划日期</th>
                  <th scope="col">实际日期</th>
                  <th scope="col">状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="term in detail?.paymentTerms" :key="term.id || term.termName">
                  <td>{{ term.termName }}</td>
                  <td>{{ term.paymentRatio || '—' }}</td>
                  <td>{{ formatAmount(term.paymentAmount || null) }}</td>
                  <td>{{ term.paymentCondition || '—' }}</td>
                  <td>{{ term.plannedDate || '—' }}</td>
                  <td>{{ term.actualDate || '—' }}</td>
                  <td>{{ term.termStatus || '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-else
            kind="empty"
            title="暂无付款条款"
            description="当前合同还没有付款节点。"
            :heading-level="3"
          />
        </V2Card>
        <V2Card title="审批记录" :subtitle="`共 ${detail?.approvalRecords.length ?? 0} 条`">
          <div v-if="detail?.approvalRecords.length" class="contract-page__rows">
            <article
              v-for="record in detail?.approvalRecords"
              :key="record.id"
              class="contract-page__approval-row"
            >
              <strong>{{ record.actionName }}</strong>
              <p>{{ record.nodeName }} · {{ record.operatorName }} · {{ record.createdAt }}</p>
              <p>{{ record.comment || '无审批意见' }}</p>
            </article>
          </div>
          <V2PageState
            v-else
            kind="empty"
            title="暂无审批历史"
            description="草稿合同还没有审批轨迹。"
            :heading-level="3"
          />
        </V2Card>
      </div>

      <template v-else>
        <V2Alert v-if="mode === 'edit' && !currentContractIsDraft" tone="warning" title="非草稿锁定"
          >当前合同不处于草稿状态，禁止再次编辑。</V2Alert
        >

        <V2Card title="合同头">
          <div class="contract-page__form-grid">
            <V2Select
              :model-value="form.contract.projectId || ''"
              label="项目"
              :options="projects.map((item) => ({ value: item.id, label: item.projectName }))"
              :disabled="formLocked"
              @update:model-value="updateContractField('projectId', $event)"
            />
            <V2Input
              :model-value="form.contract.contractName"
              label="合同名称"
              :disabled="formLocked"
              @update:model-value="updateContractField('contractName', $event)"
            />
            <V2Select
              :model-value="form.contract.contractType"
              label="合同类型"
              :options="CONTRACT_TYPE_OPTIONS"
              :disabled="formLocked"
              @update:model-value="updateContractType"
            />
            <V2Select
              :model-value="form.contract.partyAId || ''"
              label="甲方"
              :options="partners.map((item) => ({ value: item.id, label: item.partnerName }))"
              :disabled="formLocked"
              @update:model-value="updateContractField('partyAId', $event)"
            />
            <V2Select
              :model-value="form.contract.partyBId || ''"
              label="乙方"
              :options="partners.map((item) => ({ value: item.id, label: item.partnerName }))"
              :disabled="formLocked"
              @update:model-value="updateContractField('partyBId', $event)"
            />
            <V2Input
              :model-value="form.contract.contractAmount || ''"
              label="合同金额"
              :disabled="formLocked"
              @update:model-value="updateContractField('contractAmount', $event)"
            />
            <V2Input
              :model-value="form.contract.taxRate || ''"
              label="税率"
              :disabled="formLocked"
              @update:model-value="updateContractField('taxRate', $event)"
            />
            <V2Input
              :model-value="form.contract.taxAmount || ''"
              label="税额"
              :disabled="formLocked"
              @update:model-value="updateContractField('taxAmount', $event)"
            />
            <V2Input
              :model-value="form.contract.amountWithoutTax || ''"
              label="不含税额"
              :disabled="formLocked"
              @update:model-value="updateContractField('amountWithoutTax', $event)"
            />
            <V2Input
              :model-value="form.contract.paymentMethod || ''"
              label="付款方式"
              :disabled="formLocked"
              @update:model-value="updateContractField('paymentMethod', $event)"
            />
            <V2Input
              :model-value="form.contract.settlementMethod || ''"
              label="结算方式"
              :disabled="formLocked"
              @update:model-value="updateContractField('settlementMethod', $event)"
            />
            <label class="contract-page__native-field">
              <span>签订日期</span>
              <input v-model="form.contract.signedDate" type="date" :disabled="formLocked" />
            </label>
            <label class="contract-page__native-field">
              <span>开始日期</span>
              <input v-model="form.contract.startDate" type="date" :disabled="formLocked" />
            </label>
            <label class="contract-page__native-field">
              <span>结束日期</span>
              <input v-model="form.contract.endDate" type="date" :disabled="formLocked" />
            </label>
            <label class="contract-page__native-field contract-page__wide">
              <span>备注</span>
              <textarea v-model="form.contract.remark" rows="3" :disabled="formLocked" />
            </label>
          </div>
        </V2Card>

        <V2Card title="合同清单">
          <template #actions>
            <V2Button size="small" :disabled="formLocked" @click="addItem">新增清单</V2Button>
          </template>
          <div v-if="form.items.length" class="contract-page__editor-list">
            <article
              v-for="(item, index) in form.items"
              :key="item.id || `${index}-${item.itemName}`"
            >
              <div class="contract-page__form-grid">
                <V2Input
                  :model-value="item.itemName"
                  label="名称"
                  :disabled="formLocked"
                  @update:model-value="updateItem(index, 'itemName', $event)"
                />
                <V2Input
                  :model-value="item.itemCode || ''"
                  label="编号"
                  :disabled="formLocked"
                  @update:model-value="updateItem(index, 'itemCode', $event)"
                />
                <V2Input
                  :model-value="item.unit || ''"
                  label="单位"
                  :disabled="formLocked"
                  @update:model-value="updateItem(index, 'unit', $event)"
                />
                <V2Input
                  :model-value="item.quantity || ''"
                  label="数量"
                  :disabled="formLocked"
                  @update:model-value="updateItem(index, 'quantity', $event)"
                />
                <V2Input
                  :model-value="item.unitPrice || ''"
                  label="单价"
                  :disabled="formLocked"
                  @update:model-value="updateItem(index, 'unitPrice', $event)"
                />
                <V2Input
                  :model-value="item.amount || ''"
                  label="金额"
                  :disabled="formLocked"
                  @update:model-value="updateItem(index, 'amount', $event)"
                />
              </div>
              <div class="contract-page__actions">
                <V2Button
                  size="small"
                  variant="danger"
                  :disabled="formLocked"
                  @click="removeItem(index)"
                  >移除</V2Button
                >
              </div>
            </article>
          </div>
          <V2PageState
            v-else
            kind="empty"
            title="暂无合同清单"
            description="可按最小闭环先保存合同头，再补录清单。"
            :heading-level="3"
          />
        </V2Card>

        <V2Card title="付款条款">
          <template #actions>
            <V2Button size="small" :disabled="formLocked" @click="addTerm">新增条款</V2Button>
          </template>
          <div v-if="form.paymentTerms.length" class="contract-page__editor-list">
            <article
              v-for="(term, index) in form.paymentTerms"
              :key="term.id || `${index}-${term.termName}`"
            >
              <div class="contract-page__form-grid">
                <V2Input
                  :model-value="term.termName"
                  label="条款名称"
                  :disabled="formLocked"
                  @update:model-value="updateTerm(index, 'termName', $event)"
                />
                <V2Input
                  :model-value="term.paymentRatio || ''"
                  label="付款比例"
                  :disabled="formLocked"
                  @update:model-value="updateTerm(index, 'paymentRatio', $event)"
                />
                <V2Input
                  :model-value="term.paymentAmount || ''"
                  label="付款金额"
                  :disabled="formLocked"
                  @update:model-value="updateTerm(index, 'paymentAmount', $event)"
                />
                <label class="contract-page__native-field">
                  <span>计划日期</span>
                  <input v-model="term.plannedDate" type="date" :disabled="formLocked" />
                </label>
              </div>
              <div class="contract-page__actions">
                <V2Button
                  size="small"
                  variant="danger"
                  :disabled="formLocked"
                  @click="removeTerm(index)"
                  >移除</V2Button
                >
              </div>
            </article>
          </div>
          <V2PageState
            v-else
            kind="empty"
            title="暂无付款条款"
            description="可先保存草稿，再按节点补录付款安排。"
            :heading-level="3"
          />
        </V2Card>

        <V2Card title="保存">
          <div class="contract-page__actions">
            <V2Button
              v-if="mode === 'create' ? canCreate : canEdit"
              :loading="saving"
              :disabled="formLocked || !canQuery"
              @click="saveContract"
              >{{ mode === 'create' ? '创建合同' : '保存变更' }}</V2Button
            >
            <V2Button variant="secondary" :disabled="saving" @click="backToLedger">取消</V2Button>
          </div>
        </V2Card>
      </template>
    </template>

    <V2PageState
      v-else
      kind="error"
      title="合同不可访问"
      description="合同不存在、超出项目范围，或当前账号没有访问权限。"
      title-id="contract-title"
      :heading-level="1"
    >
      <template #actions>
        <V2Button variant="secondary" @click="backToLedger">返回台账</V2Button>
      </template>
    </V2PageState>

    <V2ConfirmDialog
      :open="pendingDelete"
      title="删除合同"
      description="删除后不可恢复；当前只会删除服务端允许删除的草稿或可删合同。"
      confirm-text="确认删除"
      danger
      :loading="deleting"
      @close="closeDeleteDialog"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.contract-page {
  display: grid;
  gap: var(--v2-space-3);
  color: var(--v2-color-text);
}

.contract-page__form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}

.contract-page__detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}

.contract-page__kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin: 0;
}

.contract-page__kpi-grid > div {
  min-width: 0;
  padding: var(--v2-space-3);
}

.contract-page__kpi-grid > div:not(:last-child) {
  border-right: 1px solid var(--v2-color-border-subtle);
}

.contract-page__kpi-grid dt {
  font-size: var(--v2-font-size-12);
}

.contract-page__kpi-grid dd {
  margin-top: var(--v2-space-2);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-15);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.contract-page__editor-list,
.contract-page__rows {
  display: grid;
  gap: var(--v2-space-3);
}

.contract-page__actions,
.contract-page__pagination {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}

.contract-page__pagination,
.contract-page__rows,
.contract-page__detail-grid {
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}

.contract-page__pagination {
  justify-content: flex-end;
}

.contract-page__preset-views {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  margin-bottom: var(--v2-space-3);
}

.contract-page__table-wrap {
  min-width: 0;
  overflow-x: auto;
}

.contract-page__table {
  width: 100%;
  min-width: 68rem;
  border-collapse: collapse;
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}

.contract-page__table th,
.contract-page__table td {
  padding: var(--v2-space-3);
  border-bottom: 1px solid var(--v2-color-border-subtle);
  text-align: left;
  vertical-align: middle;
}

.contract-page__table th {
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface-subtle);
  white-space: nowrap;
}

.contract-page__table td:first-child strong,
.contract-page__table td:first-child span {
  display: block;
}

.contract-page__table td:first-child span {
  margin-top: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}

.contract-page__table td:nth-child(6),
.contract-page__table td:nth-child(7) {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.contract-page__detail-table td:nth-child(2),
.contract-page__detail-table td:nth-child(3),
.contract-page__detail-table td:nth-child(5) {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.contract-page__rows article,
.contract-page__editor-list article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--v2-space-2);
  align-items: end;
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.contract-page__editor-list .contract-page__form-grid {
  grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
}

.contract-page__approval-row p,
.contract-page__rows p {
  margin: 0;
  color: var(--v2-color-text-secondary);
}

.contract-page__native-field {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}

.contract-page__native-field input,
.contract-page__native-field textarea {
  min-height: 2.5rem;
  padding: 0 var(--v2-space-3);
  color: var(--v2-color-text);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.contract-page__native-field textarea {
  min-height: 6rem;
  padding: var(--v2-space-2) var(--v2-space-3);
  resize: vertical;
}

.contract-page__wide {
  grid-column: 1 / -1;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}

dt {
  color: var(--v2-color-text-secondary);
}

dd {
  margin: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 64rem) {
  .contract-page__form-grid,
  .contract-page__editor-list .contract-page__form-grid,
  .contract-page__detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .contract-page__editor-list article {
    grid-template-columns: 1fr;
  }

  .contract-page__kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .contract-page__kpi-grid > div {
    border-right: 0;
    border-bottom: 1px solid var(--v2-color-border-subtle);
  }
}

@media (max-width: 48rem) {
  .contract-page__form-grid,
  .contract-page__editor-list .contract-page__form-grid,
  .contract-page__detail-grid {
    grid-template-columns: 1fr;
  }

  .contract-page__kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>
