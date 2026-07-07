<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  getVarOrderList,
  createVarOrder,
  updateVarOrder,
  deleteVarOrder,
  getVarOrderDetail,
  saveVarOrderItems,
  submitVarOrderForApproval,
} from '@/api/modules/variation'
import { getContractItems } from '@/api/modules/contract'
import { getCostSubjectTree } from '@/api/modules/costSubject'
import { useReferenceStore } from '@/stores/reference'
import type { VarOrderVO, VarOrderItemVO } from '@/types/variation'
import type { ContractItem } from '@/types/contract'
import type { CostSubjectTreeNode } from '@/types/costSubject'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'
import { useColumnSettings } from '@/composables/useColumnSettings'
import VariationOrderModal from './components/VariationOrderModal.vue'
import VariationOrderWorkspace from './components/VariationOrderWorkspace.vue'
import {
  APPROVAL_STATUS_COLOR,
  APPROVAL_STATUS_LABEL,
  DIRECTION_OPTIONS,
  VAR_TYPE_COLOR,
  VAR_TYPE_LABEL,
  VAR_TYPE_OPTIONS,
  buildVariationGridColumns,
} from './pageConfig'

// 字典常量 - 审批状态
const APPROVAL_DRAFT = 'DRAFT'
const APPROVAL_APPROVED = 'APPROVED'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  varType: undefined as string | undefined,
  direction: undefined as string | undefined,
  varCode: '',
})
const filterVisibility = reactive({
  projectId: true,
  varType: true,
  direction: true,
})
const filterSettingItems = [
  { key: 'projectId', label: '项目' },
  { key: 'varType', label: '变更类型' },
  { key: 'direction', label: '方向' },
] as const

const loading = ref(false)
const tableData = ref<VarOrderVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const MOBILE_BP = 768
const isMobile = ref(false)

const referenceStore = useReferenceStore()
const { projects: projectList, contracts: contractList } = storeToRefs(referenceStore)

const modalVisible = ref(false)
const modalTitle = ref('新建签证')
const editingId = ref<string | null>(null)
const modalReadonly = ref(false)
const formData = reactive<Partial<VarOrderVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  varType: undefined,
  varName: '',
  direction: 'COST',
  impactDays: 0,
  ownerConfirmFlag: 0,
  remark: '',
})
const formPartnerName = computed(
  () => contractList.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)
async function onContractChange(contractId: string) {
  const c = contractList.value?.find((ct) => ct.id === contractId)
  formData.partnerId = c?.partyBId
  await ensureCostSubjects()
  await loadContractItems(contractId)
}
function onFormProjectChange(projectId: string) {
  formData.contractId = undefined
  formData.partnerId = undefined
  itemList.value = []
  referenceStore.fetchContracts({ projectId })
}
watch(
  () => formData.contractId,
  (val) => {
    if (!val) {
      formData.partnerId = undefined
      itemList.value = []
    }
  },
)

const itemList = ref<(Partial<VarOrderItemVO> & { key: number })[]>([])
let itemKeyCounter = 0
const contractItemsLoading = ref(false)
const costSubjectOptions = ref<{ value: string; label: string }[]>([])

const APPROVAL_STATUS_DICT = 'approval_status'

function approvalStatusLabel(status: string | undefined): string {
  return getDictLabelSync(APPROVAL_STATUS_DICT, status ?? '', APPROVAL_STATUS_LABEL)
}

function approvalStatusColor(status: string | undefined): string {
  return getDictTagColorSync(APPROVAL_STATUS_DICT, status ?? '', APPROVAL_STATUS_COLOR)
}

function calcCodeColumnWidth(values: Array<string | undefined>, title = '变更编号') {
  const longest = Math.max(title.length, ...values.map((value) => String(value ?? '').length))
  return Math.min(Math.max(longest * 9 + 42, 128), 240)
}

// ---- VxeGrid columns ----
const gridColumns = computed(() =>
  buildVariationGridColumns(calcCodeColumnWidth(tableData.value.map((item) => item.varCode))),
)
const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('var_order_cols_v2', gridColumns, {
  contractName: false,
  partnerName: false,
  reportedAmount: false,
  approvedAmount: false,
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getVarOrderList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      varType: filter.varType,
      direction: filter.direction,
      varCode: filter.varCode || undefined,
    })
    tableData.value = res.records
    total.value = Number(res.total) || 0
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载签证列表失败')
  } finally {
    loading.value = false
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
  filter.varType = undefined
  filter.direction = undefined
  filter.varCode = ''
  pageNo.value = 1
  fetchData()
}
function handleProjectChange(val: string | undefined) {
  filter.contractId = undefined
  filter.partnerId = undefined
  if (val) referenceStore.fetchContracts({ projectId: val })
  handleSearch()
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
function toggleFilterVisibility(key: (typeof filterSettingItems)[number]['key']) {
  filterVisibility[key] = !filterVisibility[key]
}

async function handleAdd() {
  modalTitle.value = '新建签证'
  editingId.value = null
  modalReadonly.value = false
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    varType: undefined,
    varName: '',
    direction: 'COST',
    impactDays: 0,
    ownerConfirmFlag: 0,
    remark: '',
  })
  itemList.value = []
  itemKeyCounter = 0
  await ensureCostSubjects()
  modalVisible.value = true
}

async function openVarOrderModal(record: VarOrderVO, readonly: boolean) {
  modalTitle.value = readonly ? '查看签证' : '编辑签证'
  editingId.value = record.id
  modalReadonly.value = readonly
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    varType: record.varType,
    varName: record.varName,
    direction: record.direction,
    impactDays: record.impactDays ?? 0,
    ownerConfirmFlag: record.ownerConfirmFlag ?? 0,
    remark: record.remark ?? '',
  })
  await ensureCostSubjects()
  try {
    const detail = await getVarOrderDetail(record.id)
    itemList.value = (detail.items ?? []).map((it, idx) => ({ ...it, key: idx }))
    itemKeyCounter = itemList.value.length
  } catch (e: unknown) {
    console.error(e)
    message.error('加载变更明细失败，请稍后重试')
    return
  }
  modalVisible.value = true
}

async function handleView(record: VarOrderVO) {
  await openVarOrderModal(record, true)
}

async function handleEdit(record: VarOrderVO) {
  await openVarOrderModal(record, false)
}

async function handleSubmitApproval(record: VarOrderVO) {
  Modal.confirm({
    title: '提交审批',
    content: `确认提交签证 ${record.varCode}？`,
    onOk: async () => {
      await submitVarOrderForApproval(record.id)
      message.success('已提交审批')
      fetchData()
    },
  })
}
async function handleDelete(record: VarOrderVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除签证 ${record.varCode}？`,
    okType: 'danger',
    onOk: async () => {
      await deleteVarOrder(record.id)
      message.success('已删除')
      fetchData()
    },
  })
}

async function handleSubmit() {
  if (modalReadonly.value) return
  if (!formData.projectId) {
    message.warning('请选择项目')
    return
  }
  if (!formData.contractId) {
    message.warning('请选择合同')
    return
  }
  if (!formData.varType) {
    message.warning('请选择变更类型')
    return
  }

  const id = editingId.value
  const activeItems = itemList.value.filter((item) => toNumber(item.quantity) > 0)
  if (!activeItems.length) {
    message.warning('请至少保留一条有效明细')
    return
  }

  const missingName = activeItems.some((item) => !item.itemName?.trim())
  if (missingName) {
    message.warning('请填写明细名称')
    return
  }
  const missingCostSubject = activeItems.some((item) => !item.costSubjectId)
  if (missingCostSubject) {
    message.warning('请选择成本科目')
    return
  }
  const effectiveItems = activeItems

  try {
    if (id) {
      await updateVarOrder(id, formData)
      await saveVarOrderItems(id, effectiveItems)
      message.success('更新成功')
    } else {
      const newId = await createVarOrder(formData)
      try {
        await saveVarOrderItems(newId, effectiveItems)
      } catch (e: unknown) {
        await deleteVarOrder(newId).catch((cleanupError: unknown) => {
          console.error(cleanupError)
        })
        throw e
      }
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('保存签证失败')
  }
}

function toNumber(value: unknown): number {
  const n = Number(value ?? 0)
  return Number.isFinite(n) ? n : 0
}

function flattenCostSubjects(
  nodes: CostSubjectTreeNode[] = [],
  depth = 0,
): { value: string; label: string }[] {
  return nodes.flatMap((node) => [
    { value: node.id, label: `${'　'.repeat(depth)}${node.subjectName}` },
    ...flattenCostSubjects(node.children ?? [], depth + 1),
  ])
}

async function ensureCostSubjects() {
  if (costSubjectOptions.value.length) return
  try {
    costSubjectOptions.value = flattenCostSubjects(await getCostSubjectTree('COST'))
  } catch (e: unknown) {
    console.error(e)
    message.error('加载成本科目失败')
  }
}

async function loadContractItems(contractId?: string) {
  if (!contractId) {
    itemList.value = []
    return
  }
  contractItemsLoading.value = true
  try {
    const rows = await getContractItems(contractId)
    itemKeyCounter = 0
    itemList.value = rows.map((row: ContractItem) => ({
      key: itemKeyCounter++,
      itemName: row.itemName,
      unit: row.unit,
      quantity: 0,
      unitPrice: toNumber(row.unitPrice),
      amount: 0,
    }))
  } catch (e: unknown) {
    console.error(e)
    itemList.value = []
    message.error('加载合同清单失败')
  } finally {
    contractItemsLoading.value = false
  }
}

function handleAddItem() {
  itemList.value.push({
    key: itemKeyCounter++,
    itemName: '',
    unit: '',
    quantity: 0,
    unitPrice: 0,
    amount: 0,
  })
}
function handleRemoveItem(idx: number) {
  itemList.value.splice(idx, 1)
}
function handleItemQtyChange(idx: number) {
  const item = itemList.value[idx]
  item.amount = toNumber(item.quantity) * toNumber(item.unitPrice)
}
function handleItemPriceChange(idx: number) {
  const item = itemList.value[idx]
  item.amount = toNumber(item.quantity) * toNumber(item.unitPrice)
}

const itemsTotalAmount = computed(() =>
  itemList.value
    .filter((item) => toNumber(item.quantity) > 0)
    .reduce((sum, i) => sum + toNumber(i.amount), 0),
)

const variationStats = computed(() => ({
  total: total.value,
  draft: tableData.value.filter((item) => item.approvalStatus === APPROVAL_DRAFT).length,
  approved: tableData.value.filter((item) => item.approvalStatus === APPROVAL_APPROVED).length,
  cost: tableData.value.filter((item) => item.direction === 'COST').length,
}))

function calcPercent(count: number): number {
  const denominator = Number(total.value)
  if (!Number.isFinite(denominator) || denominator <= 0) return 0
  return Math.round((count / denominator) * 100)
}

const variationTypeSummary = computed(() =>
  VAR_TYPE_OPTIONS.map((option) => {
    const count = tableData.value.filter((item) => item.varType === option.value).length
    return {
      label: option.label,
      count,
      percent: calcPercent(count),
    }
  }),
)

const approvalStatusSummary = computed(() => {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    APPROVING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
  }
  return Object.keys(labels).map((key) => {
    const count = tableData.value.filter((item) => item.approvalStatus === key).length
    return {
      key,
      label: approvalStatusLabel(key),
      count,
      percent: calcPercent(count),
    }
  })
})

const recentVariations = computed(() => tableData.value.slice(0, 4))

function fmtWan(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  return isNaN(n) ? '0.00' : (n / 10000).toFixed(2)
}

function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}

onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
  fetchDictData(APPROVAL_STATUS_DICT)
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({})
  referenceStore.fetchPartners()
  fetchData()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div class="lg-list-page lg-page app-page variation-page">
    <div class="lg-page-head vo-page-head">
      <div class="vo-page-meta-row">
        <a-breadcrumb class="vo-breadcrumb">
          <a-breadcrumb-item>合同管理</a-breadcrumb-item>
          <a-breadcrumb-item>签证列表</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <VariationOrderWorkspace
      :filter="filter"
      :filter-visibility="filterVisibility"
      :filter-setting-items="filterSettingItems"
      :project-list="projectList ?? []"
      :var-type-options="VAR_TYPE_OPTIONS"
      :direction-options="DIRECTION_OPTIONS"
      :total="total"
      :is-mobile="isMobile"
      :loading="loading"
      :table-data="tableData"
      :visible-grid-columns="visibleGridColumns"
      :column-settings="columnSettings"
      :col-visible="colVisible"
      :variation-stats="variationStats"
      :variation-type-summary="variationTypeSummary"
      :approval-status-summary="approvalStatusSummary"
      :recent-variations="recentVariations"
      :approval-draft="APPROVAL_DRAFT"
      :var-type-label="VAR_TYPE_LABEL"
      :var-type-color="VAR_TYPE_COLOR"
      :approval-status-label="approvalStatusLabel"
      :approval-status-color="approvalStatusColor"
      :fmt-wan="fmtWan"
      :handle-project-change="handleProjectChange"
      :handle-search="handleSearch"
      :handle-reset="handleReset"
      :toggle-filter-visibility="toggleFilterVisibility"
      :toggle-col="toggleCol"
      :fetch-data="fetchData"
      :handle-add="handleAdd"
      :handle-view="handleView"
      :handle-edit="handleEdit"
      :handle-submit-approval="handleSubmitApproval"
      :handle-delete="handleDelete"
      :handle-page-change="handlePageChange"
      :handle-page-size-change="handlePageSizeChange"
      :page-no="pageNo"
      :page-size="pageSize"
    />

    <VariationOrderModal
      v-model:open="modalVisible"
      :title="modalTitle"
      :modal-readonly="modalReadonly"
      :form-data="formData"
      :project-list="projectList ?? []"
      :contract-list="contractList ?? []"
      :form-partner-name="formPartnerName"
      :var-type-options="VAR_TYPE_OPTIONS"
      :direction-options="DIRECTION_OPTIONS"
      :item-list="itemList"
      :contract-items-loading="contractItemsLoading"
      :cost-subject-options="costSubjectOptions"
      :items-total-amount="itemsTotalAmount"
      :on-form-project-change="onFormProjectChange"
      :on-contract-change="onContractChange"
      :handle-submit="handleSubmit"
      :handle-add-item="handleAddItem"
      :handle-item-qty-change="handleItemQtyChange"
      :handle-item-price-change="handleItemPriceChange"
      :handle-remove-item="handleRemoveItem"
    />
  </div>
</template>

<style scoped>
.variation-page {
  background: var(--surface-subtle);
}

.vo-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.vo-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.vo-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

@media (max-width: 768px) {
  .vo-page-meta-row {
    gap: 6px;
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
