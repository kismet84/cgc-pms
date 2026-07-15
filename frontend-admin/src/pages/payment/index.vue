<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import { MoreOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createApplication,
  deleteApplication,
  doWriteback,
  getApplicationDetail,
  getApplicationList,
  getBasisList,
  saveBasis,
  submitForApproval,
  updateApplication,
} from '@/api/modules/payment'
import { getCashJournalList } from '@/api/modules/cashbook'
import { getReceiptItems, getReceiptList } from '@/api/modules/receipt'
import { getMeasureItems, getMeasureList } from '@/api/modules/subcontract'
import { useColumnSettings } from '@/composables/useColumnSettings'
import {
  readPositiveIntQuery,
  readStringQuery,
  replaceListQuery,
} from '@/composables/listPageQuery'
import { formatWanAmount } from '@/composables/listTablePresets'
import { ColumnSettingsButton, LgEmptyState } from '@/components/list-page'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import { PAY_STATUS_COLOR, PAY_STATUS_LABEL, PAY_TYPE_COLOR, PAY_TYPE_LABEL } from '@/types/payment'
import type { PayApplicationBasisVO, PayApplicationVO } from '@/types/payment'
import type { CashJournalEntryVO } from '@/types/cashbook'
import type { MatReceiptVO } from '@/types/receipt'
import type { SubMeasureVO } from '@/types/subcontract'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'
import { APPROVAL_STATUS_COLOR, APPROVAL_STATUS_LABEL, PAYMENT_GRID_COLUMNS } from './pageConfig'
import PaymentFormModal from './components/PaymentFormModal.vue'
import PaymentOverviewPanel from './components/PaymentOverviewPanel.vue'

// 字典常量 - 审批状态
const APPROVAL_DRAFT = 'DRAFT'
const APPROVAL_APPROVED = 'APPROVED'

// 字典常量 - 付款状态
const PAY_STATUS_UNPAID = 'UNPAID'
const PAY_STATUS_PARTIAL = 'PARTIAL'
const PAY_STATUS_PAID = 'PAID'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  payType: undefined as string | undefined,
  payStatus: undefined as string | undefined,
  approvalStatus: undefined as string | undefined,
})

const loading = ref(false)
const hasLoaded = ref(false)
const listError = ref<string | null>(null)
const tableData = ref<PayApplicationVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const route = useRoute()
const router = useRouter()
const queryReady = ref(false)
const referenceStore = useReferenceStore()
const userStore = useUserStore()
const { projects, contracts } = storeToRefs(referenceStore)
const canViewCashJournal = computed(
  () =>
    userStore.roles.includes('ADMIN') ||
    userStore.roles.includes('SUPER_ADMIN') ||
    userStore.hasPermission('cashbook:journal:query'),
)
const hasActiveFilters = computed(() =>
  Boolean(
    filter.projectId ||
    filter.contractId ||
    filter.partnerId ||
    filter.payType ||
    filter.payStatus ||
    filter.approvalStatus,
  ),
)

type BasisType = 'MAT_RECEIPT' | 'SUB_MEASURE'
type BasisSourceOption = { id: string; label: string; type: BasisType; amount?: string }

const receiptList = ref<MatReceiptVO[]>([])
const measureList = ref<SubMeasureVO[]>([])
const receiptItemOptions = ref<BasisSourceOption[]>([])
const measureItemOptions = ref<BasisSourceOption[]>([])
const modalVisible = ref(false)
const modalTitle = ref('新建付款申请')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<PayApplicationVO>>({
  applyCode: '',
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  payType: undefined,
  applyAmount: undefined,
  applyReason: '',
})
const formPartnerName = computed(
  () => contracts.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)

function onContractChange(contractId: string) {
  const c = contracts.value?.find((ct) => ct.id === contractId)
  formData.partnerId = c?.partyBId
}

function handleFormProjectChange(projectId: string) {
  formData.contractId = undefined
  formData.partnerId = undefined
  referenceStore.fetchContracts({ projectId })
}

function handleFilterProjectChange(projectId: string | undefined) {
  filter.contractId = undefined
  referenceStore.fetchContracts(projectId ? { projectId } : {})
  handleSearch()
}

watch(
  () => formData.contractId,
  (val) => {
    if (!val) formData.partnerId = undefined
  },
)

const basisList = ref<(Partial<PayApplicationBasisVO> & { key: number })[]>([])
let basisKeyCounter = 0
const writebackVisible = ref(false)
const writebackTargetId = ref('')
const linkedCashJournal = ref<CashJournalEntryVO | null>(null)
const writebackForm = reactive({
  payAmount: undefined as number | undefined,
  payDate: undefined as string | undefined,
  payMethod: 'BANK_TRANSFER',
  voucherNo: '',
  externalTxnNo: '',
})

const PAY_STATUS_DICT = 'pay_status'
const APPROVAL_STATUS_DICT = 'approval_status'

function payStatusLabel(status: string | undefined): string {
  return getDictLabelSync(PAY_STATUS_DICT, status ?? '', PAY_STATUS_LABEL)
}

function payStatusColor(status: string | undefined): string {
  return getDictTagColorSync(PAY_STATUS_DICT, status ?? '', PAY_STATUS_COLOR)
}

function approvalStatusLabel(status: string | undefined): string {
  return getDictLabelSync(APPROVAL_STATUS_DICT, status ?? '', APPROVAL_STATUS_LABEL)
}

function approvalStatusColor(status: string | undefined): string {
  return getDictTagColorSync(APPROVAL_STATUS_DICT, status ?? '', APPROVAL_STATUS_COLOR)
}

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function fetchData() {
  loading.value = true
  listError.value = null
  try {
    await syncRouteQuery()
    const res = await getApplicationList({
      pageNo: pageNo.value,
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      payType: filter.payType,
      payStatus: filter.payStatus,
      approvalStatus: filter.approvalStatus,
    })
    tableData.value = res.records ?? []
    total.value = Number(res.total ?? 0)
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    listError.value = '请检查筛选条件或网络状态后重试。'
    message.error('加载付款申请列表失败')
  } finally {
    hasLoaded.value = true
    loading.value = false
  }
}

function hydrateFromRouteQuery() {
  filter.projectId = readStringQuery(route.query.projectId)
  filter.contractId = readStringQuery(route.query.contractId)
  filter.partnerId = readStringQuery(route.query.partnerId)
  filter.payType = readStringQuery(route.query.payType)
  filter.payStatus = readStringQuery(route.query.payStatus)
  filter.approvalStatus = readStringQuery(route.query.approvalStatus)
  pageNo.value = readPositiveIntQuery(route.query.pageNo, 1)
  pageSize.value = readPositiveIntQuery(route.query.pageSize, 20)
  queryReady.value = true
}

async function syncRouteQuery() {
  if (!queryReady.value) return
  const nextQuery = replaceListQuery(
    route.query,
    {
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      payType: filter.payType,
      payStatus: filter.payStatus,
      approvalStatus: filter.approvalStatus,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    },
    [
      'projectId',
      'contractId',
      'partnerId',
      'payType',
      'payStatus',
      'approvalStatus',
      'pageNo',
      'pageSize',
    ],
  )
  await router.replace({ path: route.path, query: nextQuery })
}

async function fetchReceipts() {
  try {
    const res = await getReceiptList({ pageNum: 1, pageSize: 50 })
    receiptList.value = res.records
    const items = await Promise.all(
      receiptList.value.map(async (receipt) => {
        const rows = await getReceiptItems(receipt.id)
        return rows.map((item) => ({
          id: item.id,
          type: 'MAT_RECEIPT' as const,
          amount: item.amount,
          label: `${receipt.receiptCode ?? receipt.id} / ${item.materialName ?? item.id}`,
        }))
      }),
    )
    receiptItemOptions.value = items.flat()
  } catch (e: unknown) {
    console.error('付款依据装载失败: 验收单', e)
    receiptList.value = []
    receiptItemOptions.value = []
    message.warning('验收单依据加载失败，可稍后重试')
  }
}

async function fetchMeasures() {
  try {
    const res = await getMeasureList({ pageNum: 1, pageSize: 50 })
    measureList.value = res.records
    const items = await Promise.all(
      measureList.value.map(async (measure) => {
        const rows = await getMeasureItems(measure.id)
        return rows.map((item) => ({
          id: item.id,
          type: 'SUB_MEASURE' as const,
          amount: item.amount,
          label: `${measure.measureCode ?? measure.id} / ${item.itemName ?? item.id}`,
        }))
      }),
    )
    measureItemOptions.value = items.flat()
  } catch (e: unknown) {
    console.error('付款依据装载失败: 分包计量', e)
    measureList.value = []
    measureItemOptions.value = []
    message.warning('分包计量依据加载失败，可稍后重试')
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.payType = undefined
  filter.payStatus = undefined
  filter.approvalStatus = undefined
  pageNo.value = 1
  referenceStore.fetchContracts({})
  fetchData()
}

function handlePageChange(page: number) {
  pageNo.value = page
  fetchData()
}

function handlePageSizeChange(_cur: number, size: number) {
  pageSize.value = size
  pageNo.value = 1
  fetchData()
}

function handleAdd() {
  modalTitle.value = '新建付款申请'
  editingId.value = null
  Object.assign(formData, {
    applyCode: '',
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    payType: undefined,
    applyAmount: undefined,
    applyReason: '',
  })
  basisList.value = []
  basisKeyCounter = 0
  modalVisible.value = true
}

async function handleEdit(record: PayApplicationVO) {
  modalTitle.value = '编辑付款申请'
  editingId.value = record.id
  try {
    const detail = await getApplicationDetail(record.id)
    if (detail.projectId) await referenceStore.fetchContracts({ projectId: detail.projectId })
    Object.assign(formData, {
      applyCode: detail.applyCode,
      projectId: detail.projectId,
      contractId: detail.contractId,
      partnerId: detail.partnerId,
      payType: detail.payType,
      applyAmount: detail.applyAmount,
      applyReason: detail.applyReason ?? '',
    })
    // detail.basis?.length ? detail.basis : (await getBasisList(record.id))
    const data = detail.basis?.length ? detail.basis : await getBasisList(record.id)
    basisList.value = data.map((it, idx) => ({ ...it, key: idx }))
    basisKeyCounter = basisList.value.length
  } catch (e: unknown) {
    console.error(e)
    message.error(getErrorMessage(e, '加载付款依据失败，请稍后重试'))
    return
  }
  modalVisible.value = true
}

async function handleDelete(record: PayApplicationVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除付款申请 ${record.applyCode}？`,
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteApplication(record.id)
        message.success('已删除')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error(getErrorMessage(e, '删除失败，请稍后重试'))
      }
    },
  })
}

async function handleSubmit() {
  const id = editingId.value
  if (!validateForm()) return
  const basisPayload = buildBasisPayload()
  try {
    if (id) {
      await updateApplication(id, formData)
      await saveBasis(id, basisPayload)
      message.success('更新成功')
    } else {
      const id = await createApplication(formData)
      await saveBasis(id, basisPayload)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error(getErrorMessage(e, '操作失败，请稍后重试'))
  }
}

async function handleApproval(record: PayApplicationVO) {
  try {
    await submitForApproval(record.id)
    message.success('已提交审批')
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error(getErrorMessage(e, '提交审批失败，请稍后重试'))
  }
}

function openWriteback(record: PayApplicationVO) {
  writebackTargetId.value = record.id
  writebackForm.payAmount = undefined
  writebackForm.payDate = undefined
  writebackForm.payMethod = 'BANK_TRANSFER'
  writebackForm.voucherNo = ''
  writebackForm.externalTxnNo = ''
  writebackVisible.value = true
}

async function handleWritebackOk() {
  if (!writebackForm.payAmount || !writebackForm.payDate || !writebackForm.payMethod) {
    message.warning('请完整填写支付金额、日期和方式')
    return
  }
  if (!writebackForm.externalTxnNo.trim()) {
    message.warning('请输入外部交易流水号')
    return
  }
  let payRecord: Awaited<ReturnType<typeof doWriteback>>
  try {
    payRecord = await doWriteback({
      payApplicationId: writebackTargetId.value,
      payAmount: writebackForm.payAmount,
      payDate: writebackForm.payDate,
      payMethod: writebackForm.payMethod,
      voucherNo: writebackForm.voucherNo || undefined,
      externalTxnNo: writebackForm.externalTxnNo.trim(),
    })
  } catch (e: unknown) {
    console.error(e)
    message.error(getErrorMessage(e, '回写失败，请稍后重试'))
    return
  }

  linkedCashJournal.value = null
  message.success('回写成功')
  writebackVisible.value = false
  Object.assign(writebackForm, {
    payAmount: undefined,
    payDate: undefined,
    payMethod: 'BANK_TRANSFER',
    voucherNo: '',
    externalTxnNo: '',
  })
  fetchData()

  if (canViewCashJournal.value) {
    await tryLoadLinkedCashJournal(payRecord.id, '付款成功，关联日记账暂不可查看')
  }
}

async function loadLinkedCashJournal(payRecordId: string) {
  const result = await getCashJournalList({
    pageNo: 1,
    pageSize: 1,
    sourceType: 'PAY_RECORD',
    sourceId: payRecordId,
  })
  linkedCashJournal.value = result.records?.[0] ?? null
}

async function tryLoadLinkedCashJournal(payRecordId: string, warningText: string) {
  linkedCashJournal.value = null
  try {
    await loadLinkedCashJournal(payRecordId)
  } catch (e: unknown) {
    console.warn('关联日记账加载失败', e)
    linkedCashJournal.value = null
    message.warning(warningText)
  }
}

function openLinkedCashJournal() {
  if (!linkedCashJournal.value) return
  void router.push({ path: '/cash-journal', query: { entryId: linkedCashJournal.value.id } })
}

function handleWritebackCancel() {
  writebackVisible.value = false
}

function handleAddBasis() {
  basisList.value.push({
    key: basisKeyCounter++,
    basisType: undefined,
    basisId: undefined,
    basisAmount: undefined,
  })
}

function handleRemoveBasis(idx: number) {
  basisList.value.splice(idx, 1)
}

function getSourceOptions(sourceType?: string): BasisSourceOption[] {
  if (sourceType === 'MAT_RECEIPT') return receiptItemOptions.value
  if (sourceType === 'SUB_MEASURE') return measureItemOptions.value
  return []
}

function handleSourceChange(idx: number) {
  basisList.value[idx].basisId = undefined
}

function toCents(value: unknown): number {
  return Math.round((Number(value) || 0) * 100)
}

function buildBasisPayload(): PayApplicationBasisVO[] {
  return basisList.value.map((item) => ({
    id: item.id,
    payApplicationId: item.payApplicationId,
    basisType: item.basisType,
    basisId: item.basisId,
    basisAmount: item.basisAmount,
    remark: item.remark,
  }))
}

function validateForm(): boolean {
  if (!formData.projectId || !formData.contractId || !formData.payType) {
    message.warning('请选择项目、合同和付款类型')
    return false
  }
  if (!formData.applyCode?.trim()) {
    message.warning('请填写申请编号')
    return false
  }
  if (toCents(formData.applyAmount) <= 0) {
    message.warning('申请金额必须大于 0')
    return false
  }
  if (!formData.applyReason?.trim()) {
    message.warning('请填写申请原因')
    return false
  }
  if (
    basisList.value.some(
      (item) => !item.basisType || !item.basisId || toCents(item.basisAmount) <= 0,
    )
  ) {
    message.warning('请完整填写付款依据行')
    return false
  }
  const basisTotal = basisList.value.reduce((sum, item) => sum + toCents(item.basisAmount), 0)
  if (basisTotal !== toCents(formData.applyAmount)) {
    message.warning('申请金额与付款依据金额合计不一致')
    return false
  }
  return true
}

const fmtWan = formatWanAmount

const kpiTotalApply = computed(() =>
  tableData.value.reduce((sum, row) => sum + (parseFloat(row.applyAmount) || 0), 0),
)
const kpiActualPaid = computed(() =>
  tableData.value.reduce((sum, row) => sum + (parseFloat(row.actualPayAmount || '0') || 0), 0),
)
const kpiUnpaid = computed(() =>
  tableData.value
    .filter((row) => row.payStatus === PAY_STATUS_UNPAID || row.payStatus === PAY_STATUS_PARTIAL)
    .reduce((sum, row) => sum + (parseFloat(row.applyAmount) || 0), 0),
)
const kpiApprovedUnpaid = computed(() =>
  tableData.value
    .filter(
      (row) =>
        row.approvalStatus === APPROVAL_APPROVED &&
        (row.payStatus === PAY_STATUS_UNPAID || row.payStatus === PAY_STATUS_PARTIAL),
    )
    .reduce((sum, row) => sum + (parseFloat(row.approvedAmount) || 0), 0),
)

function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}

const statusBreakdown = computed(() => {
  const map: Record<string, number> = {}
  tableData.value.forEach((row) => {
    map[row.payStatus] = (map[row.payStatus] || 0) + 1
  })
  const max = Math.max(total.value, tableData.value.length, 1)
  return Object.entries(map).map(([key, count]) => ({
    key,
    label: payStatusLabel(key),
    count,
    percent: kpiPct(count, max),
    color: key === PAY_STATUS_PAID ? '#31c48d' : key === PAY_STATUS_PARTIAL ? '#f59e0b' : '#94a3b8',
  }))
})

const approvalBreakdown = computed(() => {
  const map: Record<string, number> = {}
  tableData.value.forEach((row) => {
    const key = row.approvalStatus || APPROVAL_DRAFT
    map[key] = (map[key] || 0) + 1
  })
  const max = Math.max(total.value, tableData.value.length, 1)
  const colors: Record<string, string> = {
    DRAFT: '#94a3b8',
    APPROVING: '#2563eb',
    APPROVED: '#31c48d',
    REJECTED: '#ef4444',
  }
  return Object.entries(map).map(([key, count]) => ({
    key,
    label: approvalStatusLabel(key),
    count,
    percent: kpiPct(count, max),
    color: colors[key] ?? '#94a3b8',
  }))
})

const pendingPayments = computed(() =>
  tableData.value
    .filter((row) => row.approvalStatus === APPROVAL_APPROVED && row.payStatus !== PAY_STATUS_PAID)
    .map((row) => ({
      id: row.id,
      project: row.projectName || '-',
      title: row.applyCode || row.contractName || '-',
      amount: fmtWan(row.approvedAmount || row.applyAmount),
    }))
    .slice(0, 4),
)

const paidPct = computed(() => kpiPct(kpiActualPaid.value, Math.max(kpiTotalApply.value, 1)))
const kpiMax = computed(() => ({
  unpaid: Math.max(kpiUnpaid.value, 1),
  approvedUnpaid: Math.max(kpiApprovedUnpaid.value, 1),
}))

function fmtAmountText(value: number): string {
  return fmtWan(String(value))
}

const gridColumns = computed(() => [...PAYMENT_GRID_COLUMNS])
const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('payment_list_cols', gridColumns)
const showEmptyState = computed(() => hasLoaded.value && !loading.value && !tableData.value.length)

onMounted(() => {
  hydrateFromRouteQuery()
  fetchDictData(PAY_STATUS_DICT)
  fetchDictData(APPROVAL_STATUS_DICT)
  referenceStore.fetchProjects()
  referenceStore.fetchContracts(filter.projectId ? { projectId: filter.projectId } : {})
  referenceStore.fetchPartners()
  fetchData()
  fetchReceipts()
  fetchMeasures()
  const payRecordId = readStringQuery(route.query.payRecordId)
  if (payRecordId && canViewCashJournal.value) {
    void tryLoadLinkedCashJournal(payRecordId, '关联日记账暂不可查看')
  }
})
</script>

<template>
  <div class="lg-list-page lg-page app-page payment-page settlement-domain-page">
    <div v-if="linkedCashJournal" class="linked-cash-journal-banner">
      <div>
        <strong>关联资金流水 {{ linkedCashJournal.entryNo }}</strong>
        <span
          >状态：{{
            linkedCashJournal.status === 'PENDING_ARCHIVE'
              ? '待选择账户、上传附件并归档'
              : linkedCashJournal.status
          }}</span
        >
      </div>
      <a-button type="primary" @click="openLinkedCashJournal">进入资金日记账</a-button>
    </div>
    <PaymentOverviewPanel
      :filter="filter"
      :projects="projects ?? []"
      :contracts="contracts ?? []"
      :pay-type-label="PAY_TYPE_LABEL"
      :pay-status-label-map="PAY_STATUS_LABEL"
      :pay-status-label="payStatusLabel"
      :fmt-amount-text="fmtAmountText"
      :on-project-change="handleFilterProjectChange"
      :on-search="handleSearch"
      :on-reset="handleReset"
      :on-refresh="fetchData"
      :total="total"
      :kpi-total-apply="kpiTotalApply"
      :kpi-actual-paid="kpiActualPaid"
      :kpi-unpaid="kpiUnpaid"
      :kpi-approved-unpaid="kpiApprovedUnpaid"
      :paid-pct="paidPct"
      :kpi-unpaid-pct="kpiPct(kpiUnpaid, kpiMax.unpaid)"
      :status-breakdown="statusBreakdown"
      :approval-breakdown="approvalBreakdown"
      :pending-payments="pendingPayments"
    >
      <main class="lg-list-table-panel payment-table-panel settlement-domain-table-panel">
        <div class="lg-toolbar payment-toolbar settlement-domain-toolbar">
          <div class="lg-toolbar-left">
            <div class="payment-table-heading">
              <span class="payment-table-title">付款申请明细</span>
              <span class="payment-table-count">共 {{ total }} 条</span>
            </div>
          </div>
          <div class="lg-toolbar-right">
            <ColumnSettingsButton
              :columns="columnSettings"
              :visible="colVisible"
              @toggle="toggleCol"
            />
            <a-button @click="fetchData">
              <template #icon><ReloadOutlined /></template>
              刷新
            </a-button>
            <a-button type="primary" @click="handleAdd">
              <template #icon><PlusOutlined /></template>
              新建申请
            </a-button>
          </div>
        </div>

        <div class="lg-table-wrap settlement-domain-table-wrap">
          <div v-if="listError" class="payment-list-feedback">
            <a-result status="error" title="付款列表加载失败" :sub-title="listError">
              <template #extra>
                <a-button type="primary" @click="fetchData">重试</a-button>
              </template>
            </a-result>
          </div>
          <div v-else-if="showEmptyState" class="payment-list-feedback">
            <LgEmptyState description="暂无符合条件的付款申请">
              <a-button v-if="hasActiveFilters" @click="handleReset">清空筛选</a-button>
              <a-button v-else type="primary" @click="handleAdd">新建申请</a-button>
            </LgEmptyState>
          </div>
          <vxe-grid
            v-else
            :data="tableData"
            :columns="visibleGridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            stripe
            border="inner"
            size="small"
          >
            <template #applyAmount="{ row }">
              <span class="lg-money">{{ fmtWan(row.applyAmount) }} 万</span>
            </template>
            <template #approvedAmount="{ row }">
              <span class="lg-money">{{ fmtWan(row.approvedAmount) }} 万</span>
            </template>
            <template #actualPayAmount="{ row }">
              <span class="lg-money">{{ fmtWan(row.actualPayAmount) }} 万</span>
            </template>
            <template #payType="{ row }">
              <a-tag :color="PAY_TYPE_COLOR[row.payType] || 'default'" size="small">
                {{ PAY_TYPE_LABEL[row.payType] ?? row.payType }}
              </a-tag>
            </template>
            <template #payStatus="{ row }">
              <a-tag :color="payStatusColor(row.payStatus)" size="small">
                {{ payStatusLabel(row.payStatus) }}
              </a-tag>
            </template>
            <template #approvalStatus="{ row }">
              <a-tag :color="approvalStatusColor(row.approvalStatus)" size="small">
                {{ approvalStatusLabel(row.approvalStatus) }}
              </a-tag>
            </template>
            <template #action="{ row }">
              <a-dropdown :trigger="['click']">
                <a-button class="lg-row-action-trigger" size="small" type="text">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                    <a-menu-item
                      v-if="row.approvalStatus === APPROVAL_DRAFT"
                      @click="handleApproval(row)"
                    >
                      提交审批
                    </a-menu-item>
                    <a-menu-item
                      v-if="
                        row.approvalStatus === APPROVAL_APPROVED &&
                        row.payStatus !== PAY_STATUS_PAID
                      "
                      @click="openWriteback(row)"
                    >
                      付款回写
                    </a-menu-item>
                    <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </template>
          </vxe-grid>
        </div>

        <div class="lg-pagination settlement-domain-pagination">
          <span class="lg-total">共 {{ total }} 条</span>
          <a-pagination
            v-model:current="pageNo"
            v-model:page-size="pageSize"
            :total="total"
            :page-size-options="['10', '20', '50', '100']"
            show-size-changer
            show-quick-jumper
            @change="handlePageChange"
            @show-size-change="handlePageSizeChange"
          />
        </div>
      </main>
    </PaymentOverviewPanel>

    <PaymentFormModal
      v-model:open="modalVisible"
      :title="modalTitle"
      :form-data="formData"
      :projects="projects ?? []"
      :contracts="contracts ?? []"
      :form-partner-name="formPartnerName"
      :pay-type-label="PAY_TYPE_LABEL"
      :basis-list="basisList"
      :get-source-options="getSourceOptions"
      :on-form-project-change="handleFormProjectChange"
      :on-contract-change="onContractChange"
      :on-add-basis="handleAddBasis"
      :on-source-change="handleSourceChange"
      :on-remove-basis="handleRemoveBasis"
      @submit="handleSubmit"
    />

    <a-modal
      v-model:open="writebackVisible"
      title="付款回写"
      :width="800"
      @ok="handleWritebackOk"
      @cancel="handleWritebackCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="支付金额" required>
          <a-input-number
            v-model:value="writebackForm.payAmount"
            :min="0.01"
            :precision="2"
            style="width: 100%"
            placeholder="请输入支付金额"
          />
        </a-form-item>
        <a-form-item label="支付日期" required>
          <a-date-picker
            v-model:value="writebackForm.payDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="支付方式" required>
          <a-select v-model:value="writebackForm.payMethod" placeholder="请选择">
            <a-select-option value="BANK_TRANSFER">银行转账</a-select-option>
            <a-select-option value="CASH">现金</a-select-option>
            <a-select-option value="CHECK">支票</a-select-option>
            <a-select-option value="OTHER">其他</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="凭证号">
          <a-input v-model:value="writebackForm.voucherNo" placeholder="请输入凭证号" />
        </a-form-item>
        <a-form-item label="外部交易流水号" required>
          <a-input
            v-model:value="writebackForm.externalTxnNo"
            placeholder="银行或支付渠道唯一流水号"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.payment-page {
  gap: 0;
}

.linked-cash-journal-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border: 1px solid #b7d6ff;
  border-radius: 10px;
  background: #f0f7ff;
}

.linked-cash-journal-banner > div {
  display: grid;
  gap: 4px;
}
.linked-cash-journal-banner span {
  color: var(--text-secondary);
  font-size: 13px;
}

.payment-table-panel {
  min-height: 754px;
}

.payment-list-feedback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  padding: 24px;
}

.payment-toolbar {
  align-items: center;
  min-height: 56px;
}

.payment-table-heading {
  display: grid;
  gap: 2px;
  margin-right: 4px;
}

.payment-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.payment-table-count,
.payment-toolbar-hint {
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
