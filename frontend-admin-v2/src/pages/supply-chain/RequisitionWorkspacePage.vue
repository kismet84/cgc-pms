<script setup lang="ts">
import type {
  ContractRecord,
  MaterialRecord,
  MaterialReturnRecord,
  PartnerRecord,
  RequisitionCommand,
  RequisitionItemRecord,
  RequisitionRecord,
  RequisitionTraceRecord,
  WarehouseRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
} from '@/components'
import { formatAmount } from '@/pages/dashboard/model'
import {
  confirmMaterialReturn,
  createRequisition,
  deleteRequisition,
  loadMaterialReturn,
  loadMaterialReturnItems,
  loadMaterials,
  loadRequisition,
  loadRequisitionItems,
  loadRequisitions,
  loadRequisitionTrace,
  loadWarehouses,
  reverseMaterialReturn,
  saveRequisitionItems,
  stockOutRequisition,
  submitRequisition,
  updateRequisition,
} from '@/services/supply-chain'
import { loadContractPage, loadPartners } from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

interface EditorItem {
  key: string
  materialId: string
  materialName: string
  quantity: string
  unitPrice: string
  useLocation: string
  remark: string
}

const session = useSessionStore()
const workspace = useWorkspaceStore()
const records = ref<RequisitionRecord[]>([])
const selected = ref<RequisitionRecord | null>(null)
const items = ref<RequisitionItemRecord[]>([])
const trace = ref<RequisitionTraceRecord | null>(null)
const materialReturn = ref<MaterialReturnRecord | null>(null)
const returnItems = ref<Awaited<ReturnType<typeof loadMaterialReturnItems>>>([])
const warehouses = ref<WarehouseRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const partners = ref<PartnerRecord[]>([])
const contracts = ref<ContractRecord[]>([])
const editorItems = ref<EditorItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)
const detailLoading = ref(false)
const detailOpen = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const editorOpen = ref(false)
const returnOpen = ref(false)
const reverseOpen = ref(false)
const deleteOpen = ref(false)
const editingId = ref('')
const filter = reactive({ requisitionCode: '', approvalStatus: '' })
const form = reactive<Record<string, string>>({})
let listController: AbortController | null = null
let detailController: AbortController | null = null
let listGeneration = 0
let detailGeneration = 0

const projectId = computed(() => workspace.selectedProjectId || '')
const reportPeriod = computed(() => reportPeriodBounds(workspace.selectedReportPeriod))
const canAdd = computed(() => hasPermission('requisition:add') && hasPermission('requisition:edit'))
const canEdit = computed(() => hasPermission('requisition:edit'))
const canDelete = computed(() => hasPermission('requisition:delete'))
const canSubmit = computed(() => hasPermission('requisition:submit'))
const canStockOut = computed(() => hasPermission('requisition:stock-out'))
const canReturn = computed(() => hasPermission('requisition:return'))
const canTrace = computed(() => hasPermission('procurement:trace:query'))
const canEditSelected = computed(
  () =>
    canEdit.value &&
    Boolean(selected.value) &&
    ['DRAFT', 'REJECTED'].includes(selected.value?.approvalStatus || ''),
)
const canSubmitSelected = computed(
  () => canSubmit.value && selected.value?.approvalStatus === 'DRAFT',
)
const canStockOutSelected = computed(
  () =>
    canStockOut.value &&
    selected.value?.approvalStatus === 'APPROVED' &&
    selected.value?.stockOutFlag !== '1',
)
const canReturnSelected = computed(
  () =>
    canReturn.value &&
    selected.value?.stockOutFlag === '1' &&
    Boolean(
      trace.value?.stockTransactions.some((item) =>
        ['MAT_REQUISITION', 'REQUISITION'].includes(item.sourceType || ''),
      ),
    ),
)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const itemOptions = computed(() =>
  items.value
    .filter((item) => item.id)
    .map((item) => ({
      value: item.id || '',
      label: `${item.materialName || '物料名称缺失'} · 已领 ${item.quantity}`,
    })),
)
const transactionOptions = computed(() =>
  (trace.value?.stockTransactions ?? [])
    .filter(
      (item) =>
        ['MAT_REQUISITION', 'REQUISITION'].includes(item.sourceType || '') &&
        item.sourceLineId === form.requisitionItemId,
    )
    .map((item) => ({
      value: item.id,
      label: `${transactionTypeLabel(item.txnType)} · ${item.quantity} · ${item.createdTime || '-'}`,
    })),
)
const warehouseOptions = computed(() =>
  warehouses.value.map((item) => ({
    value: item.id,
    label: [item.warehouseCode, item.warehouseName].filter(Boolean).join(' · '),
  })),
)
const materialOptions = computed(() =>
  materials.value.map((item) => ({
    value: item.id,
    label: [item.materialCode, item.materialName, item.specification].filter(Boolean).join(' · '),
  })),
)
const partnerOptions = computed(() => [
  { value: '', label: '不指定供应商' },
  ...partners.value.map((item) => ({
    value: item.id,
    label: [item.partnerCode, item.partnerName].filter(Boolean).join(' · '),
  })),
])
const contractOptions = computed(() =>
  contracts.value.map((item) => ({
    value: item.id,
    label: [item.contractCode, item.contractName].filter(Boolean).join(' · '),
  })),
)

function hasPermission(code: string): boolean {
  return (
    session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
    session.hasPermission(code)
  )
}

function warehouseLabel(item: RequisitionRecord): string {
  if (item.warehouseName)
    return [item.warehouseCode, item.warehouseName].filter(Boolean).join(' · ')
  return item.warehouseCode || '-'
}

function returnMaterialLabel(requisitionItemId: string): string {
  return items.value.find((item) => item.id === requisitionItemId)?.materialName || '原领料物料'
}

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

function required(name: string, label: string): string {
  const value = form[name]?.trim() ?? ''
  if (!value) throw new TypeError(`${label}不能为空`)
  return value
}

function optional(name: string): string | undefined {
  return form[name]?.trim() || undefined
}

function positiveDecimal(value: string, label: string): string {
  const normalized = value.trim()
  if (!/^\d+(?:\.\d+)?$/.test(normalized) || /^0+(?:\.0+)?$/.test(normalized)) {
    throw new TypeError(`${label}必须为正十进制数`)
  }
  return normalized
}

function nonNegativeDecimal(value: string, label: string): string {
  const normalized = value.trim()
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) {
    throw new TypeError(`${label}必须为非负十进制数`)
  }
  return normalized
}

function statusLabel(status?: string | null): string {
  return (
    {
      DRAFT: '草稿',
      APPROVING: '审批中',
      APPROVED: '已通过',
      REJECTED: '已驳回',
      CONFIRMED: '已确认',
      REVERSED: '已冲销',
      CANCELLED: '已取消',
    }[status ?? ''] ?? '未知状态'
  )
}

function statusTone(status?: string | null): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'APPROVED' || status === 'CONFIRMED') return 'success'
  if (status === 'APPROVING') return 'info'
  if (status === 'REJECTED' || status === 'REVERSED') return 'warning'
  return 'neutral'
}

function transactionTypeLabel(type?: string | null): string {
  return (
    {
      OUT: '出库',
      RETURN_IN: '退料入库',
      TRANSFER_IN: '调拨入库',
      TRANSFER_OUT: '调拨出库',
    }[type ?? ''] ?? '未知流水类型'
  )
}

function newEditorItem(source?: RequisitionItemRecord): EditorItem {
  return {
    key: crypto.randomUUID(),
    materialId: source?.materialId || '',
    materialName: source?.materialName || '',
    quantity: source?.quantity || '',
    unitPrice: source?.unitPrice || '',
    useLocation: source?.useLocation || '',
    remark: source?.remark || '',
  }
}

async function loadPage(): Promise<void> {
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await loadRequisitions(
      {
        pageNo: pageNo.value,
        pageSize,
        projectId: projectId.value || undefined,
        dateFrom: reportPeriod.value?.startDate,
        dateTo: reportPeriod.value?.endDate,
        requisitionCode: filter.requisitionCode || undefined,
        approvalStatus: filter.approvalStatus || undefined,
      },
      controller.signal,
    )
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = Number(page.total ?? 0)
    if (editorOpen.value) return
    if (selected.value) {
      const refreshed = page.records.find((item) => item.id === selected.value?.id)
      if (refreshed) await selectRecord(refreshed)
      else clearDetail()
    }
  } catch (error) {
    if (controller.signal.aborted) return
    errorMessage.value = errorText(error, '领料单加载失败')
    showToast('error', '领料单读取失败', errorMessage.value)
    records.value = []
    total.value = 0
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

function clearDetail(): void {
  detailController?.abort()
  detailOpen.value = false
  selected.value = null
  items.value = []
  trace.value = null
  materialReturn.value = null
  returnItems.value = []
}

async function selectRecord(record: RequisitionRecord): Promise<void> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  detailLoading.value = true
  detailOpen.value = true
  editorOpen.value = false
  selected.value = record
  items.value = []
  trace.value = null
  materialReturn.value = null
  returnItems.value = []
  try {
    const [detail, nextItems] = await Promise.all([
      loadRequisition(record.id, controller.signal),
      loadRequisitionItems(record.id, controller.signal),
    ])
    if (generation !== detailGeneration) return
    selected.value = detail
    items.value = nextItems
    if (!canTrace.value) return
    let nextTrace: RequisitionTraceRecord
    try {
      nextTrace = await loadRequisitionTrace(record.id, controller.signal)
    } catch (error) {
      if (controller.signal.aborted || generation !== detailGeneration) return
      showToast('error', '追溯链路不可用', errorText(error, '当前账号无法读取追溯链路'))
      return
    }
    if (generation !== detailGeneration) return
    trace.value = nextTrace
    if (nextTrace.materialReturn?.id) {
      const [nextReturn, nextReturnItems] = await Promise.all([
        loadMaterialReturn(nextTrace.materialReturn.id, controller.signal),
        loadMaterialReturnItems(nextTrace.materialReturn.id, controller.signal),
      ])
      if (generation !== detailGeneration) return
      materialReturn.value = nextReturn
      returnItems.value = nextReturnItems
    }
  } catch (error) {
    if (controller.signal.aborted) return
    showToast('error', '链路读取失败', errorText(error, '领料详情加载失败'))
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

function search(): void {
  pageNo.value = 1
  clearDetail()
  void loadPage()
}

function resetSearch(): void {
  filter.requisitionCode = ''
  filter.approvalStatus = ''
  search()
}

function changePage(next: number): void {
  pageNo.value = next
  clearDetail()
  void loadPage()
}

async function loadEditorCandidates(candidateProjectId: string): Promise<void> {
  busy.value = true
  try {
    const [warehousePage, materialPage, partnerPage, contractPage] = await Promise.all([
      loadWarehouses({
        pageNo: 1,
        pageSize: 200,
        projectId: candidateProjectId || undefined,
        status: 'ENABLE',
      }),
      loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' }),
      loadPartners({ pageNo: 1, pageSize: 200, partnerType: 'SUPPLIER', status: 'ENABLE' }),
      candidateProjectId
        ? loadContractPage({
            pageNo: 1,
            pageSize: 200,
            projectId: candidateProjectId,
            contractStatus: 'PERFORMING',
          })
        : Promise.resolve({ records: [], total: 0, pageNo: 1, pageSize: 200 }),
    ])
    warehouses.value = warehousePage.records
    materials.value = materialPage.records
    partners.value = partnerPage.records
    contracts.value = contractPage.records
  } catch (error) {
    errorMessage.value = errorText(error, '领料候选读取失败')
    showToast('error', '领料候选读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function changeMaterial(row: EditorItem, value: string): void {
  row.materialId = value
  row.materialName = materials.value.find((item) => item.id === value)?.materialName || ''
}

function addEditorItem(): void {
  if (editorItems.value.length >= 200) return
  editorItems.value.push(newEditorItem())
}

function removeEditorItem(index: number): void {
  if (editorItems.value.length === 1) {
    editorItems.value[0] = newEditorItem()
    return
  }
  editorItems.value.splice(index, 1)
}

async function changeEditorProject(value: string): Promise<void> {
  form.projectId = value
  form.contractId = ''
  form.warehouseId = ''
  await loadEditorCandidates(value)
}

async function openCreate(): Promise<void> {
  detailOpen.value = false
  editingId.value = ''
  Object.assign(form, {
    projectId: projectId.value,
    contractId: '',
    warehouseId: '',
    partnerId: '',
    requisitionDate: new Date().toISOString().slice(0, 10),
    remark: '',
  })
  editorItems.value = [newEditorItem()]
  editorOpen.value = true
  errorMessage.value = ''
  await loadEditorCandidates(form.projectId)
}

async function openEdit(): Promise<void> {
  if (!selected.value || !canEditSelected.value) return
  detailOpen.value = false
  editingId.value = selected.value.id
  Object.assign(form, {
    projectId: selected.value.projectId,
    contractId: selected.value.contractId || '',
    warehouseId: selected.value.warehouseId || '',
    partnerId: selected.value.partnerId || '',
    requisitionDate: selected.value.requisitionDate || '',
    remark: selected.value.remark || '',
  })
  editorItems.value = items.value.length ? items.value.map(newEditorItem) : [newEditorItem()]
  editorOpen.value = true
  errorMessage.value = ''
  await loadEditorCandidates(form.projectId)
}

function closeEditor(): void {
  if (busy.value) return
  const reopenDetail = Boolean(editingId.value && selected.value)
  editorOpen.value = false
  editingId.value = ''
  editorItems.value = []
  errorMessage.value = ''
  detailOpen.value = reopenDetail
}

async function saveEditor(submitAfter = false): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  let createdId = ''
  try {
    if (!editorItems.value.length) throw new TypeError('至少添加一条领料明细')
    const body: RequisitionCommand = {
      projectId: required('projectId', '项目'),
      contractId: required('contractId', '合同'),
      warehouseId: required('warehouseId', '仓库'),
      partnerId: optional('partnerId'),
      requisitionDate: required('requisitionDate', '领料日期'),
      remark: optional('remark'),
    }
    const itemCommands = editorItems.value.map((item, index) => ({
      materialId:
        item.materialId.trim() ||
        (() => {
          throw new TypeError(`第${index + 1}条明细物料不能为空`)
        })(),
      materialName: item.materialName.trim() || undefined,
      quantity: positiveDecimal(item.quantity, `第${index + 1}条领料数量`),
      unitPrice: item.unitPrice.trim()
        ? nonNegativeDecimal(item.unitPrice, `第${index + 1}条单价`)
        : undefined,
      useLocation: item.useLocation.trim() || undefined,
      remark: item.remark.trim() || undefined,
    }))

    const id = editingId.value || (await createRequisition(body))
    if (!editingId.value) {
      createdId = id
      editingId.value = id
    } else {
      await updateRequisition(id, body)
    }
    await saveRequisitionItems(
      id,
      itemCommands.map((item) => ({ ...item, requisitionId: id })),
    )
    await Promise.all([loadRequisition(id), loadRequisitionItems(id)])
    if (submitAfter) {
      if (!canSubmit.value) throw new TypeError('当前账号无提交审批权限')
      await submitRequisition(id)
    }
    editorOpen.value = false
    editingId.value = ''
    editorItems.value = []
    await loadPage()
    const refreshed = records.value.find((item) => item.id === id)
    if (refreshed) await selectRecord(refreshed)
    showToast(
      'success',
      '操作成功',
      submitAfter ? '领料申请已保存并提交审批' : '领料申请草稿已保存',
    )
  } catch (error) {
    errorMessage.value = createdId
      ? `草稿 ${createdId} 已创建，后续保存失败：${errorText(error, '领料单保存失败')}。请修正后重试。`
      : errorText(error, '领料单保存失败')
    showToast('error', '领料单保存失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function execute(action: 'submit' | 'stock-out'): Promise<void> {
  if (!selected.value || busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    if (action === 'submit') await submitRequisition(selected.value.id)
    else await stockOutRequisition(selected.value.id)
    await loadPage()
    showToast('success', '操作成功', action === 'submit' ? '领料单已提交' : '领料出库已完成')
  } catch (error) {
    errorMessage.value = errorText(error, action === 'submit' ? '提交失败' : '出库失败')
    showToast('error', action === 'submit' ? '提交失败' : '出库失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!selected.value) return
  busy.value = true
  try {
    await deleteRequisition(selected.value.id)
    deleteOpen.value = false
    clearDetail()
    await loadPage()
  } catch (error) {
    errorMessage.value = errorText(error, '删除失败')
    showToast('error', '领料单删除失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function changeReturnItem(value: string): void {
  form.requisitionItemId = value
  form.originalStockTxnId =
    (trace.value?.stockTransactions ?? []).find(
      (item) =>
        item.sourceLineId === value &&
        ['MAT_REQUISITION', 'REQUISITION'].includes(item.sourceType || ''),
    )?.id || ''
}

function openReturn(): void {
  const requisitionItemId = items.value[0]?.id || ''
  Object.assign(form, {
    requisitionItemId,
    originalStockTxnId: '',
    returnQuantity: '',
    returnDate: new Date().toISOString().slice(0, 10),
    returnReason: '',
    idempotencyKey: crypto.randomUUID(),
  })
  changeReturnItem(requisitionItemId)
  returnOpen.value = true
}

async function saveReturn(): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    const id = await confirmMaterialReturn({
      requisitionItemId: required('requisitionItemId', '领料明细'),
      originalStockTxnId: required('originalStockTxnId', '原出库流水'),
      quantity: positiveDecimal(required('returnQuantity', '退料数量'), '退料数量'),
      returnDate: required('returnDate', '退料日期'),
      reason: required('returnReason', '退料原因'),
      idempotencyKey: form.idempotencyKey,
    })
    returnOpen.value = false
    if (selected.value) await selectRecord(selected.value)
    materialReturn.value = await loadMaterialReturn(id)
    returnItems.value = await loadMaterialReturnItems(id)
    showToast('success', '操作成功', '退料已确认')
  } catch (error) {
    errorMessage.value = errorText(error, '退料失败')
    showToast('error', '退料失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function openReverse(): void {
  form.reversalReason = ''
  reverseOpen.value = true
}

async function reverseReturn(): Promise<void> {
  if (!materialReturn.value || busy.value) return
  busy.value = true
  try {
    await reverseMaterialReturn(materialReturn.value.id, required('reversalReason', '冲销原因'))
    reverseOpen.value = false
    if (selected.value) await selectRecord(selected.value)
  } catch (error) {
    errorMessage.value = errorText(error, '退料冲销失败')
    showToast('error', '退料冲销失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

watch(
  [projectId, () => workspace.selectedReportPeriod],
  () => {
    pageNo.value = 1
    editorOpen.value = false
    clearDetail()
    void loadPage()
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
})
</script>

<template>
  <section class="requisition-page">
    <V2Card title="领用与退料" :heading-level="1">
      <template #actions>
        <div class="requisition-page__toolbar">
          <V2Input
            v-model="filter.requisitionCode"
            label="领料单号"
            hide-label
            type="search"
            placeholder="输入领料单号"
          />
          <V2Select
            v-model="filter.approvalStatus"
            label="审批状态"
            hide-label
            placeholder="全部状态"
            allow-empty
            :options="[
              { value: '', label: '全部状态' },
              { value: 'DRAFT', label: '草稿' },
              { value: 'APPROVING', label: '审批中' },
              { value: 'APPROVED', label: '已通过' },
              { value: 'REJECTED', label: '已驳回' },
            ]"
            @update:model-value="search"
          />
          <V2Button size="small" variant="secondary" :loading="loading" @click="search"
            >查询</V2Button
          >
          <V2Button size="small" variant="ghost" :disabled="loading" @click="resetSearch"
            >重置</V2Button
          >
          <V2Button v-if="canAdd" size="small" @click="openCreate">发起领料申请</V2Button>
        </div>
      </template>
    </V2Card>

    <section class="requisition-page__workspace" aria-label="领用与退料工作台">
      <V2Card class="requisition-page__master">
        <V2PageState
          v-if="loading"
          kind="loading"
          :heading-level="2"
          title="正在读取"
          description="正在读取领料申请与退料记录。"
        />
        <V2PageState
          v-else-if="!errorMessage && !records.length"
          :heading-level="2"
          title="暂无领料申请"
          description="当前项目和筛选范围无匹配记录。"
        />
        <div
          v-else
          class="requisition-page__table-wrap"
          role="region"
          aria-label="领料申请列表"
          tabindex="0"
        >
          <table>
            <thead>
              <tr>
                <th>单号</th>
                <th>日期</th>
                <th v-if="!projectId">项目</th>
                <th>仓库编码</th>
                <th>仓库名称</th>
                <th>审批</th>
                <th>出库</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in records"
                :key="item.id"
                :class="{
                  'requisition-page__row--selected': selected?.id === item.id && !editorOpen,
                }"
              >
                <th scope="row">
                  <V2Button
                    size="small"
                    variant="ghost"
                    class="v2-table__record-link"
                    @click="selectRecord(item)"
                  >
                    {{ item.requisitionCode || '领料单号缺失' }}
                  </V2Button>
                </th>
                <td>{{ item.requisitionDate || '-' }}</td>
                <td v-if="!projectId">
                  {{ item.projectName || '项目信息缺失' }}
                </td>
                <td>{{ item.warehouseCode || '仓库编码缺失' }}</td>
                <td>{{ item.warehouseName || '仓库名称缺失' }}</td>
                <td>
                  <V2Badge :tone="statusTone(item.approvalStatus)">{{
                    statusLabel(item.approvalStatus)
                  }}</V2Badge>
                </td>
                <td>{{ item.stockOutFlag === '1' ? '已出库' : '未出库' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <template v-if="pageCount > 1" #footer>
          <nav class="requisition-page__pager" aria-label="领料单分页">
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo <= 1"
              @click="changePage(pageNo - 1)"
              >上一页</V2Button
            >
            <span>第 {{ pageNo }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo >= pageCount"
              @click="changePage(pageNo + 1)"
              >下一页</V2Button
            >
          </nav>
        </template>
      </V2Card>
    </section>

    <V2Dialog
      :open="editorOpen"
      :title="editingId ? '编辑领料申请' : '发起领料申请'"
      description="先保存完整草稿，再按权限提交审批。"
      panel-class="v2-dialog-standard"
      :close-on-backdrop="false"
      :close-disabled="busy"
      @close="closeEditor"
    >
      <section class="requisition-page__editor" aria-label="领料申请表单">
        <div class="requisition-page__form">
          <V2Select
            v-model="form.projectId"
            label="项目"
            :options="workspace.projects"
            :disabled="busy || Boolean(editingId)"
            required
            @update:model-value="changeEditorProject"
          />
          <V2Select
            v-model="form.contractId"
            label="合同"
            :options="contractOptions"
            :disabled="busy"
            required
          />
          <V2Select
            v-model="form.warehouseId"
            label="领用仓库"
            :options="warehouseOptions"
            :disabled="busy"
            required
          />
          <V2Input
            v-model="form.requisitionDate"
            label="领用日期"
            placeholder="YYYY-MM-DD"
            required
          />
          <V2Select
            v-model="form.partnerId"
            label="供应商"
            :options="partnerOptions"
            allow-empty
            :disabled="busy"
          />
          <V2Input v-model="form.remark" label="申请备注" />
        </div>

        <div class="requisition-page__line-head">
          <div>
            <h3>物料明细</h3>
            <p>最多 200 条；金额保存后自动计算。</p>
          </div>
          <V2Button
            type="button"
            size="small"
            variant="secondary"
            :disabled="busy || editorItems.length >= 200"
            @click="addEditorItem"
            >添加物料</V2Button
          >
        </div>
        <div class="requisition-page__lines">
          <article
            v-for="(row, index) in editorItems"
            :key="row.key"
            class="requisition-page__line"
          >
            <div class="requisition-page__line-number">{{ index + 1 }}</div>
            <V2Select
              :model-value="row.materialId"
              label="物料"
              :options="materialOptions"
              :disabled="busy"
              required
              @update:model-value="(value) => changeMaterial(row, value)"
            />
            <V2Input v-model="row.quantity" label="领用数量" required />
            <V2Input v-model="row.unitPrice" label="参考单价" />
            <V2Input v-model="row.useLocation" label="使用部位" />
            <V2Input v-model="row.remark" label="明细备注" />
            <V2Button
              type="button"
              size="small"
              variant="ghost"
              :disabled="busy"
              @click="removeEditorItem(index)"
              >移除</V2Button
            >
          </article>
        </div>
      </section>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="busy" @click="closeEditor">
          取消
        </V2Button>
        <V2Button type="button" variant="secondary" :loading="busy" @click="saveEditor(false)">
          保存草稿
        </V2Button>
        <V2Button v-if="canSubmit" type="button" :loading="busy" @click="saveEditor(true)">
          保存并提交审批
        </V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="detailOpen"
      title="领退料链路"
      :description="selected?.requisitionCode || ''"
      panel-class="v2-dialog-standard v2-detail-dialog"
      :close-on-backdrop="true"
      :close-disabled="busy"
      @close="clearDetail"
    >
      <V2PageState
        v-if="detailLoading"
        kind="loading"
        :heading-level="2"
        title="正在读取领退料链路"
        description="正在读取申请、审批、出库和退料事实。"
      />
      <template v-else-if="selected">
        <div class="requisition-page__detail-head">
          <div>
            <p class="requisition-page__eyebrow">当前申请</p>
            <strong>{{ selected.requisitionCode || '领料单号缺失' }}</strong>
            <span
              >{{ selected.projectName || '项目名称缺失' }} · {{ warehouseLabel(selected) }}</span
            >
          </div>
        </div>

        <section class="requisition-page__facts" aria-label="领退料状态">
          <div>
            <span>审批状态</span>
            <V2Badge :tone="statusTone(selected.approvalStatus)">{{
              statusLabel(selected.approvalStatus)
            }}</V2Badge>
          </div>
          <div>
            <span>出库状态</span>
            <strong>{{
              selected.stockOutFlag === '1' ? selected.stockOutAt || '已完成' : '未执行'
            }}</strong>
          </div>
          <div>
            <span>申请金额</span>
            <strong>{{ formatAmount(selected.totalAmount) }}</strong>
          </div>
          <div>
            <span>退料状态</span>
            <strong>{{ materialReturn ? statusLabel(materialReturn.status) : '暂无退料' }}</strong>
          </div>
        </section>

        <section class="requisition-page__section">
          <div class="requisition-page__section-head">
            <h3>领料明细</h3>
            <span>{{ items.length }} 条</span>
          </div>
          <div
            class="requisition-page__table-wrap"
            role="region"
            aria-label="领料明细"
            tabindex="0"
          >
            <table>
              <thead>
                <tr>
                  <th>物料</th>
                  <th>规格</th>
                  <th>单位</th>
                  <th>数量</th>
                  <th>单价</th>
                  <th>金额</th>
                  <th>使用部位</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in items" :key="item.id || item.materialId">
                  <th scope="row">{{ item.materialName || '物料名称缺失' }}</th>
                  <td>{{ item.specification || '-' }}</td>
                  <td>{{ item.unit || '-' }}</td>
                  <td>{{ item.quantity }}</td>
                  <td>{{ item.unitPrice || '-' }}</td>
                  <td>{{ formatAmount(item.amount) }}</td>
                  <td>{{ item.useLocation || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="requisition-page__timeline" aria-label="领退料链路">
          <article>
            <span>1</span>
            <div>
              <strong>申请创建</strong>
              <p>{{ selected.requisitionDate || '-' }}</p>
            </div>
          </article>
          <article>
            <span>2</span>
            <div>
              <strong>审批</strong>
              <p>
                {{ statusLabel(selected.approvalStatus) }} ·
                {{ canTrace ? `${trace?.approvalRecords.length || 0} 条审批记录` : '无追溯权限' }}
              </p>
            </div>
          </article>
          <article>
            <span>3</span>
            <div>
              <strong>库存出库</strong>
              <p>
                {{ canTrace ? `${trace?.stockTransactions.length || 0} 条库存流水` : '无追溯权限' }}
              </p>
            </div>
          </article>
          <article>
            <span>4</span>
            <div>
              <strong>退料</strong>
              <p v-if="materialReturn">
                {{ materialReturn.returnCode }} · {{ statusLabel(materialReturn.status) }} ·
                {{ formatAmount(materialReturn.totalAmount) }}
              </p>
              <p v-else>暂无退料</p>
            </div>
          </article>
        </section>

        <section v-if="materialReturn" class="requisition-page__section">
          <div class="requisition-page__section-head">
            <h3>退料明细</h3>
            <V2Button
              v-if="canReturn && materialReturn.status === 'CONFIRMED'"
              type="button"
              size="small"
              variant="danger"
              @click="openReverse"
              >冲销退料</V2Button
            >
          </div>
          <p v-for="item in returnItems" :key="item.id">
            {{ returnMaterialLabel(item.requisitionItemId) }} · 数量 {{ item.quantity }} · 金额
            {{ formatAmount(item.amount) }}
          </p>
        </section>
      </template>
      <template #footer>
        <V2Button v-if="canEditSelected" type="button" variant="secondary" @click="openEdit">
          编辑
        </V2Button>
        <V2Button
          v-if="canDelete && selected?.approvalStatus === 'DRAFT'"
          type="button"
          variant="danger"
          @click="deleteOpen = true"
          >删除</V2Button
        >
        <V2Button v-if="canSubmitSelected" type="button" :loading="busy" @click="execute('submit')"
          >提交审批</V2Button
        >
        <V2Button
          v-if="canStockOutSelected"
          type="button"
          :loading="busy"
          @click="execute('stock-out')"
          >执行出库</V2Button
        >
        <V2Button v-if="canReturnSelected" type="button" @click="openReturn">发起退料</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="returnOpen"
      title="发起退料"
      :close-on-backdrop="false"
      :close-disabled="busy"
      @close="returnOpen = false"
    >
      <div class="requisition-page__return-form">
        <V2Select
          v-model="form.requisitionItemId"
          label="原领料明细"
          :options="itemOptions"
          required
          @update:model-value="changeReturnItem"
        />
        <V2Select
          v-model="form.originalStockTxnId"
          label="原出库流水"
          :options="transactionOptions"
          required
        />
        <V2Input v-model="form.returnQuantity" label="退料数量" required />
        <V2Input v-model="form.returnDate" label="退料日期" placeholder="YYYY-MM-DD" required />
        <V2Input v-model="form.returnReason" label="退料原因" required />
      </div>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="busy" @click="returnOpen = false"
          >取消</V2Button
        >
        <V2Button type="button" :loading="busy" @click="saveReturn">确认退料</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="reverseOpen"
      title="冲销退料"
      :close-on-backdrop="false"
      :close-disabled="busy"
      @close="reverseOpen = false"
    >
      <V2Input v-model="form.reversalReason" label="冲销原因" required />
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="busy" @click="reverseOpen = false"
          >取消</V2Button
        >
        <V2Button type="button" variant="danger" :loading="busy" @click="reverseReturn"
          >确认冲销</V2Button
        >
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="deleteOpen"
      title="删除领料申请"
      :description="selected ? `确认删除 ${selected.requisitionCode || selected.id}？` : ''"
      danger
      :loading="busy"
      @close="deleteOpen = false"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.requisition-page {
  display: grid;
  gap: var(--v2-space-4);
  min-width: 0;
}
.requisition-page__toolbar,
.requisition-page__detail-head,
.requisition-page__actions,
.requisition-page__pager,
.requisition-page__line-head,
.requisition-page__editor-actions,
.requisition-page__section-head {
  display: flex;
  align-items: center;
  gap: var(--v2-space-3);
}
.requisition-page__toolbar {
  display: grid;
  grid-template-columns: minmax(12rem, 1.3fr) minmax(10rem, 1fr) auto auto auto;
  width: min(70vw, 68rem);
}
.requisition-page__workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--v2-space-4);
  min-width: 0;
}
.requisition-page__master,
.requisition-page__detail {
  min-width: 0;
}
.requisition-page__table-wrap {
  overflow: auto;
}
.requisition-page table {
  width: 100%;
  min-width: 40rem;
  border-collapse: collapse;
}
.requisition-page th,
.requisition-page td {
  padding: var(--v2-space-3);
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
  text-align: left;
  vertical-align: middle;
}
.requisition-page tbody tr {
  transition: background-color var(--v2-motion-fast) var(--v2-ease-standard);
}
.requisition-page tbody tr:hover,
.requisition-page__row--selected {
  background: var(--v2-color-surface-subtle);
}
.requisition-page__pager {
  justify-content: flex-end;
}
.requisition-page__detail-head,
.requisition-page__line-head,
.requisition-page__section-head {
  justify-content: space-between;
}
.requisition-page__detail-head > div:first-child {
  display: grid;
  gap: var(--v2-space-1);
}
.requisition-page__detail-head span,
.requisition-page__eyebrow,
.requisition-page__line-head p,
.requisition-page__timeline p {
  color: var(--v2-color-text-secondary);
}
.requisition-page__eyebrow,
.requisition-page__line-head h3,
.requisition-page__line-head p,
.requisition-page__section-head h3,
.requisition-page__timeline p {
  margin: 0;
}
.requisition-page__actions,
.requisition-page__editor-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}
.requisition-page__facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-3);
  margin-block: var(--v2-space-4);
}
.requisition-page__facts > div {
  display: grid;
  gap: var(--v2-space-2);
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  background: var(--v2-color-surface-subtle);
}
.requisition-page__facts span {
  color: var(--v2-color-text-secondary);
}
.requisition-page__section {
  display: grid;
  gap: var(--v2-space-3);
  padding-block: var(--v2-space-4);
  border-top: var(--v2-border-width) solid var(--v2-color-border);
}
.requisition-page__timeline {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-3);
  padding-block: var(--v2-space-4);
  border-top: var(--v2-border-width) solid var(--v2-color-border);
}
.requisition-page__timeline article {
  display: flex;
  gap: var(--v2-space-3);
}
.requisition-page__timeline article > span {
  display: grid;
  width: var(--v2-control-height-sm);
  height: var(--v2-control-height-sm);
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--v2-color-primary);
  color: white;
  font-weight: var(--v2-font-weight-semibold);
}
.requisition-page__timeline article div {
  display: grid;
  gap: var(--v2-space-1);
}
.requisition-page__editor {
  display: grid;
  gap: var(--v2-space-4);
}
.requisition-page__form,
.requisition-page__return-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.requisition-page__lines {
  display: grid;
  gap: var(--v2-space-3);
}
.requisition-page__line {
  display: grid;
  grid-template-columns:
    auto minmax(12rem, 1.6fr) minmax(7rem, 0.7fr) minmax(7rem, 0.7fr) minmax(9rem, 1fr)
    minmax(9rem, 1fr) auto;
  gap: var(--v2-space-2);
  align-items: end;
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.requisition-page__line-number {
  display: grid;
  width: var(--v2-control-height-sm);
  height: var(--v2-control-height-md);
  place-items: center;
  color: var(--v2-color-text-secondary);
  font-weight: var(--v2-font-weight-semibold);
}
.requisition-page__editor-actions {
  padding-top: var(--v2-space-4);
  border-top: var(--v2-border-width) solid var(--v2-color-border);
}
@media (max-width: 80rem) {
  .requisition-page__line {
    grid-template-columns: auto repeat(2, minmax(0, 1fr));
  }
  .requisition-page__line > :nth-child(n + 6) {
    grid-column: auto;
  }
}
@media (max-width: 72rem) {
  .requisition-page__workspace {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 64rem) {
  .requisition-page__toolbar,
  .requisition-page__form,
  .requisition-page__return-form,
  .requisition-page__facts,
  .requisition-page__timeline {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .requisition-page__toolbar {
    width: min(70vw, 42rem);
  }
}
@media (max-width: 40rem) {
  .requisition-page__toolbar,
  .requisition-page__form,
  .requisition-page__return-form,
  .requisition-page__facts,
  .requisition-page__timeline,
  .requisition-page__line {
    grid-template-columns: 1fr;
  }
  .requisition-page__toolbar {
    width: 100%;
  }
  .requisition-page__detail-head,
  .requisition-page__line-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .requisition-page__actions,
  .requisition-page__editor-actions {
    justify-content: flex-start;
  }
  .requisition-page__line-number {
    width: 100%;
    height: auto;
    justify-content: start;
  }
}
</style>
