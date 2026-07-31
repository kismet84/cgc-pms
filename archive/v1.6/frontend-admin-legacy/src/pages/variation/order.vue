<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
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
  submitVariationToOwner,
  reviewVariationOwnerSubmission,
  getVariationTrace,
} from '@/api/modules/variation'
import { uploadFile } from '@/api/modules/file'
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
import { useMobileViewport } from '@/composables/useMobileViewport'
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
const { isMobile } = useMobileViewport()

const referenceStore = useReferenceStore()
const { projects: projectList, contracts: contractList } = storeToRefs(referenceStore)

const modalVisible = ref(false)
const modalTitle = ref('新建签证')
const editingId = ref<string | null>(null)
const modalReadonly = ref(false)
const evidenceFile = ref<File>()
const today = () => new Date().toISOString().slice(0, 10)
const formData = reactive<Partial<VarOrderVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  varType: undefined,
  varName: '',
  eventDate: today(),
  eventDescription: '',
  causeCategory: '',
  direction: 'COST',
  impactDays: 0,
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
    eventDate: today(),
    claimDeadline: undefined,
    eventDescription: '',
    causeCategory: '',
    responsibleParty: '',
    businessMatterKey: '',
    direction: 'COST',
    impactDays: 0,
    remark: '',
  })
  itemList.value = []
  evidenceFile.value = undefined
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
    eventDate: record.eventDate,
    claimDeadline: record.claimDeadline,
    eventDescription: record.eventDescription,
    causeCategory: record.causeCategory,
    responsibleParty: record.responsibleParty,
    businessMatterKey: record.businessMatterKey,
    direction: record.direction,
    impactDays: record.impactDays ?? 0,
    remark: record.remark ?? '',
  })
  await ensureCostSubjects()
  evidenceFile.value = undefined
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
  if (
    !formData.eventDate ||
    !formData.eventDescription?.trim() ||
    !formData.causeCategory?.trim()
  ) {
    message.warning('请填写事件日期、事件说明和原因分类')
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
      if (evidenceFile.value) await uploadFile(evidenceFile.value, 'VARIATION', id, 'SITE_EVIDENCE')
      message.success('更新成功')
    } else {
      const newId = await createVarOrder(formData)
      try {
        await saveVarOrderItems(newId, effectiveItems)
        if (evidenceFile.value)
          await uploadFile(evidenceFile.value, 'VARIATION', newId, 'SITE_EVIDENCE')
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
      claimUnitPrice: toNumber(row.unitPrice),
      claimAmount: 0,
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
    claimUnitPrice: 0,
    claimAmount: 0,
  })
}
function handleRemoveItem(idx: number) {
  itemList.value.splice(idx, 1)
}
function handleItemQtyChange(idx: number) {
  const item = itemList.value[idx]
  item.amount = toNumber(item.quantity) * toNumber(item.unitPrice)
  item.claimAmount = toNumber(item.quantity) * toNumber(item.claimUnitPrice)
}
function handleItemClaimPriceChange(idx: number) {
  const item = itemList.value[idx]
  item.claimAmount = toNumber(item.quantity) * toNumber(item.claimUnitPrice)
}
function onEvidenceFileChange(file?: File) {
  evidenceFile.value = file
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
const itemsClaimTotalAmount = computed(() =>
  itemList.value
    .filter((item) => toNumber(item.quantity) > 0)
    .reduce((sum, item) => sum + toNumber(item.claimAmount), 0),
)

type OwnerSnapshotItem = {
  id: string | number
  item_name?: string
  claimed_amount?: string | number
}
type OwnerSubmissionSnapshot = {
  id: string | number
  revision_no?: number
  status?: string
  items?: OwnerSnapshotItem[]
}
const ownerModalVisible = ref(false)
const ownerMode = ref<'SUBMIT' | 'REVIEW'>('SUBMIT')
const ownerTarget = ref<VarOrderVO>()
const ownerFile = ref<File>()
const ownerSubmission = ref<OwnerSubmissionSnapshot>()
const ownerForm = reactive({
  externalDocumentNo: '',
  responseDocumentNo: '',
  responseComment: '',
  conclusion: 'CONFIRMED' as 'CONFIRMED' | 'RETURNED',
  lines: [] as Array<{
    submissionItemId: string | number
    itemName: string
    claimedAmount: number
    confirmedAmount: number
    reductionReason: string
  }>,
})
const traceVisible = ref(false)
const traceData = ref<Record<string, unknown>>({})

async function handleOwnerAction(row: VarOrderVO) {
  if (['INTERNAL_APPROVED', 'OWNER_RETURNED'].includes(row.ownerStatus ?? '')) {
    ownerMode.value = 'SUBMIT'
    ownerTarget.value = row
    ownerFile.value = undefined
    ownerForm.externalDocumentNo = ''
    ownerModalVisible.value = true
    return
  }
  if (row.ownerStatus === 'OWNER_SUBMITTED') {
    const detail = await getVarOrderDetail(row.id)
    const latest = (detail.ownerSubmissions ?? []).at(-1) as unknown as
      | OwnerSubmissionSnapshot
      | undefined
    if (!latest) return message.error('未找到待核定的业主申报版本')
    ownerMode.value = 'REVIEW'
    ownerTarget.value = row
    ownerSubmission.value = latest
    ownerFile.value = undefined
    ownerForm.responseDocumentNo = ''
    ownerForm.responseComment = ''
    ownerForm.conclusion = 'CONFIRMED'
    ownerForm.lines = (latest.items ?? []).map((item) => ({
      submissionItemId: item.id,
      itemName: item.item_name ?? '',
      claimedAmount: toNumber(item.claimed_amount),
      confirmedAmount: toNumber(item.claimed_amount),
      reductionReason: '',
    }))
    ownerModalVisible.value = true
    return
  }
  traceData.value = await getVariationTrace(row.id)
  traceVisible.value = true
}

async function submitOwnerAction() {
  const row = ownerTarget.value
  if (!row || !ownerFile.value) return message.warning('请上传本次业主往来文件')
  try {
    if (ownerMode.value === 'SUBMIT') {
      if (!ownerForm.externalDocumentNo.trim()) return message.warning('请填写对外发文号')
      await uploadFile(ownerFile.value, 'VARIATION', row.id, 'OWNER_SUBMISSION')
      await submitVariationToOwner(row.id, {
        externalDocumentNo: ownerForm.externalDocumentNo,
        submittedAt: new Date().toISOString(),
      })
    } else {
      if (!ownerForm.responseDocumentNo.trim()) return message.warning('请填写业主回复文号')
      await uploadFile(ownerFile.value, 'VARIATION', row.id, 'OWNER_CONFIRMATION')
      await reviewVariationOwnerSubmission(row.id, String(ownerSubmission.value?.id), {
        conclusion: ownerForm.conclusion,
        responseDocumentNo: ownerForm.responseDocumentNo,
        responseComment: ownerForm.responseComment,
        reviewedAt: new Date().toISOString(),
        items:
          ownerForm.conclusion === 'RETURNED'
            ? []
            : ownerForm.lines.map((line) => ({
                submissionItemId: line.submissionItemId,
                confirmedAmount: line.confirmedAmount,
                reductionReason: line.reductionReason,
              })),
      })
    }
    message.success(ownerMode.value === 'SUBMIT' ? '业主申报已登记' : '业主核定已登记')
    ownerModalVisible.value = false
    fetchData()
  } catch (error: unknown) {
    console.error(error)
    message.error('业主往来处理失败')
  }
}

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

onMounted(() => {
  fetchDictData(APPROVAL_STATUS_DICT)
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({})
  referenceStore.fetchPartners()
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page variation-page project-operation-list-page">
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
      :handle-owner-action="handleOwnerAction"
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
      :items-claim-total-amount="itemsClaimTotalAmount"
      :on-form-project-change="onFormProjectChange"
      :on-contract-change="onContractChange"
      :handle-submit="handleSubmit"
      :handle-add-item="handleAddItem"
      :handle-item-qty-change="handleItemQtyChange"
      :handle-item-price-change="handleItemPriceChange"
      :handle-item-claim-price-change="handleItemClaimPriceChange"
      :on-evidence-file-change="onEvidenceFileChange"
      :handle-remove-item="handleRemoveItem"
    />

    <a-modal
      v-model:open="ownerModalVisible"
      :title="ownerMode === 'SUBMIT' ? '提交业主申报' : '登记业主回复'"
      @ok="submitOwnerAction"
    >
      <template v-if="ownerMode === 'SUBMIT'">
        <a-form layout="vertical"
          ><a-form-item label="对外发文号"
            ><a-input v-model:value="ownerForm.externalDocumentNo" /></a-form-item
        ></a-form>
      </template>
      <template v-else>
        <a-form layout="vertical">
          <a-form-item label="业主结论"
            ><a-radio-group v-model:value="ownerForm.conclusion"
              ><a-radio value="CONFIRMED">核定</a-radio
              ><a-radio value="RETURNED">退回</a-radio></a-radio-group
            ></a-form-item
          >
          <a-form-item label="业主回复文号"
            ><a-input v-model:value="ownerForm.responseDocumentNo"
          /></a-form-item>
          <a-form-item label="回复说明"
            ><a-textarea v-model:value="ownerForm.responseComment"
          /></a-form-item>
          <a-table
            v-if="ownerForm.conclusion === 'CONFIRMED'"
            :data-source="ownerForm.lines"
            :pagination="false"
            row-key="submissionItemId"
            size="small"
          >
            <a-table-column title="明细" data-index="itemName" />
            <a-table-column title="申报金额" data-index="claimedAmount" />
            <a-table-column title="核定金额"
              ><template #default="{ record }"
                ><a-input-number
                  v-model:value="record.confirmedAmount"
                  :min="0"
                  :max="record.claimedAmount" /></template
            ></a-table-column>
            <a-table-column title="核减原因"
              ><template #default="{ record }"
                ><a-input v-model:value="record.reductionReason" /></template
            ></a-table-column>
          </a-table>
        </a-form>
      </template>
      <div style="margin-top: 12px">
        <label
          >本次业主往来文件：<input
            type="file"
            @change="ownerFile = ($event.target as HTMLInputElement).files?.[0]"
        /></label>
      </div>
    </a-modal>

    <a-modal v-model:open="traceVisible" title="变更签证全链追溯" :footer="null" width="900px">
      <pre style="max-height: 560px; overflow: auto; white-space: pre-wrap">{{
        JSON.stringify(traceData, null, 2)
      }}</pre>
    </a-modal>
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
