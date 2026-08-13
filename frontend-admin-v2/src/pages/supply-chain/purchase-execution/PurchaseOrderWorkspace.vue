<script setup lang="ts">
import type {
  BudgetLineRecord,
  ContractItemRecord,
  ContractPage,
  MaterialRecord,
  PartnerRecord,
  PurchaseOrderCommand,
  PurchaseOrderFromRequestCommand,
  PurchaseOrderItemRecord,
  PurchaseOrderRecord,
  PurchaseRequestRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
} from '@/components'
import { formatAmount, formatDecimal } from '@/shared/display'
import {
  loadBudget,
  loadBudgetPage,
  loadContract,
  loadContractItems,
  loadContractPage,
  loadPartners,
} from '@/services/commercial'
import {
  createPurchaseOrder,
  createPurchaseOrderFromRequest,
  deletePurchaseOrder,
  loadMaterials,
  loadPurchaseOrder,
  loadPurchaseOrderItems,
  loadPurchaseOrderPricingSuggestion,
  loadPurchaseOrders,
  loadPurchaseRequestItems,
  loadPurchaseRequests,
  savePurchaseOrderItems,
  submitPurchaseOrder,
  updatePurchaseOrder,
} from '@/services/supply-chain'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import PurchaseExecutionDetail from './PurchaseExecutionDetail.vue'
import {
  errorText,
  newOrderItemDraft,
  optional,
  orderCode,
  orderDetailTable,
  positiveValue,
  recordAmount,
  required,
  requiredDraft,
  requiredSourceId,
  statusLabel,
  taxRateValue,
  type OrderItemDraft,
} from './model'
import {
  NewPurchaseOrderSaveError,
  savePurchaseOrder,
  submitSavedPurchaseOrder,
} from './application/save-purchase-order'

type OrderCreateMode = 'FROM_REQUEST' | 'EXCEPTION'

const router = useRouter()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const application = {
  create: createPurchaseOrder,
  createFromRequest: createPurchaseOrderFromRequest,
  update: updatePurchaseOrder,
  saveItems: savePurchaseOrderItems,
  deleteDraft: deletePurchaseOrder,
  submit: submitPurchaseOrder,
}

const records = ref<PurchaseOrderRecord[]>([])
const selected = ref<PurchaseOrderRecord | null>(null)
const detailItems = ref<PurchaseOrderItemRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const partners = ref<PartnerRecord[]>([])
const contracts = ref<ContractPage['records']>([])
const contractItems = ref<ContractItemRecord[]>([])
const requestCandidates = ref<PurchaseRequestRecord[]>([])
const budgetLines = ref<BudgetLineRecord[]>([])
const drafts = ref<OrderItemDraft[]>([])
const editItems = ref<
  Array<{
    source: PurchaseOrderItemRecord
    budgetLineId: string
    unitPrice: string
    taxRate: string
  }>
>([])
const form = reactive<Record<string, string>>({})
const editForm = reactive<Record<string, string>>({})
const createMode = ref<OrderCreateMode>('FROM_REQUEST')
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)
const detailLoading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const editorOpen = ref(false)
const editOpen = ref(false)

let listController: AbortController | null = null
let detailController: AbortController | null = null
let listGeneration = 0
let detailGeneration = 0

const projectId = computed(() => workspace.selectedProjectId || '')
const canAdd = computed(
  () =>
    session.hasAdminOrPermission('purchase:order:add') &&
    session.hasAdminOrPermission('purchase:order:edit') &&
    session.hasAdminOrPermission('purchase:order:delete'),
)
const canSaveItems = computed(() => session.hasAdminOrPermission('purchase:order:edit'))
const canEditSelected = computed(
  () => canSaveItems.value && selected.value?.approvalStatus === 'DRAFT',
)
const canSubmitSelected = computed(
  () =>
    session.hasAdminOrPermission('purchase:order:submit') &&
    selected.value?.approvalStatus === 'DRAFT',
)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const detailTable = computed(() => orderDetailTable(detailItems.value))
const sourceRequest = computed(() =>
  selected.value?.requestId
    ? {
        id: selected.value.requestId,
        code: selected.value.requestCode || selected.value.requestId,
      }
    : null,
)
const contractOptions = computed(() =>
  contracts.value.map((item) => ({
    value: item.id,
    label: [item.contractCode, item.contractName].filter(Boolean).join(' · '),
  })),
)
const partnerOptions = computed(() => {
  const options = partners.value.map((item) => ({
    value: item.id,
    label: [item.partnerCode, item.partnerName].filter(Boolean).join(' · '),
  }))
  for (const value of [form.partnerId, editForm.partnerId]) {
    if (!value || options.some((item) => item.value === value)) continue
    const contract = contracts.value.find((item) => item.partyBId === value)
    options.push({
      value,
      label: contract?.partyBName || selected.value?.partnerName || '历史供应商',
    })
  }
  return options
})
const materialOptions = computed(() => {
  const options = new Map<string, string>()
  for (const item of contractItems.value) {
    if (!item.materialId || options.has(item.materialId)) continue
    const material = materials.value.find((candidate) => candidate.id === item.materialId)
    options.set(
      item.materialId,
      [
        material?.materialCode || item.itemCode,
        material?.materialName || item.itemName,
        material?.specification || item.itemSpec,
      ]
        .filter(Boolean)
        .join(' · '),
    )
  }
  return [...options].map(([value, label]) => ({ value, label }))
})
const budgetLineOptions = computed(() =>
  budgetLines.value
    .filter((item) => item.id)
    .map((item) => ({
      value: item.id as string,
      label: `${item.costSubjectName || item.costSubjectId} · 可用 ${item.availableAmount ?? item.budgetAmount}`,
    })),
)
const requestOptions = computed(() =>
  requestCandidates.value.map((item) => ({
    value: item.id,
    label: [
      item.requestCode || '未生成采购申请号',
      item.projectName,
      statusLabel(item.approvalStatus),
    ]
      .filter(Boolean)
      .join(' · '),
  })),
)

async function loadPage(): Promise<void> {
  listController?.abort()
  detailController?.abort()
  selected.value = null
  detailItems.value = []
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await loadPurchaseOrders(
      { pageNum: pageNo.value, pageSize, projectId: projectId.value || undefined },
      controller.signal,
    )
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = page.total
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, '采购订单加载失败')
      showToast('error', '采购订单读取失败', errorMessage.value)
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function selectRecord(record: PurchaseOrderRecord): Promise<void> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  selected.value = record
  detailItems.value = []
  detailLoading.value = true
  try {
    const [detail, items] = await Promise.all([
      loadPurchaseOrder(record.id, controller.signal),
      loadPurchaseOrderItems(record.id, controller.signal),
    ])
    if (generation !== detailGeneration) return
    selected.value = detail
    detailItems.value = items
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      showToast('error', '详情读取失败', errorText(error, '采购订单详情加载失败'))
    }
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

function clearDetail(): void {
  detailController?.abort()
  selected.value = null
  detailItems.value = []
}

async function loadActiveBudgetLines(project: string): Promise<void> {
  budgetLines.value = []
  if (!project) return
  const page = await loadBudgetPage({
    pageNo: 1,
    pageSize: 20,
    projectId: project,
    status: 'ACTIVE',
  })
  const active = page.records.find((item) => item.active || item.status === 'ACTIVE')
  if (active) budgetLines.value = (await loadBudget(active.id)).lines ?? []
}

async function loadApprovedRequests(project: string): Promise<void> {
  requestCandidates.value = []
  if (!project) return
  const page = await loadPurchaseRequests({
    pageNum: 1,
    pageSize: 200,
    projectId: project,
    approvalStatus: 'APPROVED',
    status: 'APPROVED',
  })
  requestCandidates.value = page.records.filter((item) => item.status !== 'CONVERTED')
}

async function loadCandidates(project?: string): Promise<void> {
  const [partnerPage, materialPage, contractPage] = await Promise.all([
    loadPartners({ pageNo: 1, pageSize: 200, partnerType: 'SUPPLIER', status: 'ENABLE' }),
    loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' }),
    loadContractPage({
      pageNo: 1,
      pageSize: 200,
      projectId: project,
      contractType: 'PURCHASE',
      approvalStatus: 'APPROVED',
      contractStatus: 'PERFORMING',
    }),
  ])
  partners.value = partnerPage.records
  materials.value = materialPage.records
  contracts.value = contractPage.records
  await loadActiveBudgetLines(project || '')
  if (createMode.value === 'FROM_REQUEST' && project) await loadApprovedRequests(project)
}

async function openCreate(mode: OrderCreateMode): Promise<void> {
  createMode.value = mode
  for (const key of Object.keys(form)) delete form[key]
  form.projectId = projectId.value
  drafts.value = [newOrderItemDraft()]
  contractItems.value = []
  requestCandidates.value = []
  editorOpen.value = true
  busy.value = true
  try {
    await loadCandidates(form.projectId || undefined)
  } catch (error) {
    errorMessage.value = errorText(error, '采购订单候选读取失败')
    showToast('error', '业务候选读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function changeProject(value: string): Promise<void> {
  form.projectId = value
  form.contractId = ''
  form.partnerId = ''
  form.requestId = ''
  drafts.value = [newOrderItemDraft()]
  contractItems.value = []
  if (!value || busy.value) return
  busy.value = true
  try {
    await loadCandidates(value)
  } catch (error) {
    contracts.value = []
    errorMessage.value = errorText(error, '采购合同候选读取失败')
    showToast('error', '采购合同候选读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function changeContract(value: string): Promise<void> {
  form.contractId = value
  form.partnerId = contracts.value.find((item) => item.id === value)?.partyBId || ''
  contractItems.value = []
  if (value) {
    try {
      const items = await loadContractItems(value)
      if (form.contractId === value) contractItems.value = items
    } catch (error) {
      if (form.contractId === value) {
        showToast('error', '合同材料读取失败', errorText(error, '无法读取采购合同明细'))
      }
    }
  }
  if (createMode.value === 'EXCEPTION') {
    const materialIds = new Set(
      contractItems.value.map((item) => item.materialId).filter((id): id is string => Boolean(id)),
    )
    drafts.value.forEach((item) => {
      if (item.materialId && !materialIds.has(item.materialId)) item.materialId = ''
    })
  }
  await refreshPrices()
}

async function changeRequest(value: string): Promise<void> {
  form.requestId = value
  drafts.value = [newOrderItemDraft()]
  if (!value) {
    await refreshPrices()
    return
  }
  busy.value = true
  try {
    const items = await loadPurchaseRequestItems(value)
    drafts.value = items.map((item) => ({
      ...newOrderItemDraft(),
      requestItemId: item.id || '',
      materialId: item.materialId || '',
      budgetLineId: item.budgetLineId || '',
      quantity: item.approvedQuantity || item.quantity,
      unit: item.unit || '',
      plannedDate: item.plannedDate || '',
      useLocation: item.useLocation || '',
      remark: item.remark || '',
    }))
    if (!drafts.value.length) drafts.value = [newOrderItemDraft()]
    await refreshPrices()
  } catch (error) {
    drafts.value = [newOrderItemDraft()]
    showToast('error', '采购申请明细读取失败', errorText(error, '无法读取已审批申请明细'))
  } finally {
    busy.value = false
  }
}

function materialLabel(materialId: string): string {
  const material = materials.value.find((item) => item.id === materialId)
  return material
    ? [material.materialCode, material.materialName, material.specification]
        .filter(Boolean)
        .join(' · ')
    : materialId || '-'
}

function budgetLineLabel(budgetLineId: string): string {
  const line = budgetLines.value.find((item) => item.id === budgetLineId)
  return line ? `${line.costSubjectName || line.costSubjectId}` : budgetLineId || '-'
}

function addItem(): void {
  if (drafts.value.length < 200) drafts.value.push(newOrderItemDraft())
}

function removeItem(index: number): void {
  if (drafts.value.length > 1) drafts.value.splice(index, 1)
}

async function selectMaterial(index: number, value: string): Promise<void> {
  const item = drafts.value[index]
  if (!item) return
  item.materialId = value
  const material = materials.value.find((candidate) => candidate.id === value)
  const contractItem = contractItems.value.find((candidate) => candidate.materialId === value)
  item.unit = material?.unit || contractItem?.unit || ''
  item.unitPrice = ''
  item.pricingMode = ''
  item.priceSource = ''
  item.priceSourceReceiptItemId = ''
  item.priceEditable = false
  if (!form.contractId || !value) return
  try {
    const suggestion = await loadPurchaseOrderPricingSuggestion(form.contractId, value)
    item.unitPrice = suggestion.unitPrice
    item.pricingMode = suggestion.pricingMode
    item.priceSource = suggestion.priceSource
    item.priceSourceReceiptItemId = suggestion.sourceReceiptItemId || ''
    item.priceEditable = suggestion.editable
  } catch (error) {
    showToast('warning', '价格读取失败', errorText(error, '合同未返回服务端价格'))
  }
}

async function refreshPrices(): Promise<void> {
  if (!form.contractId) {
    drafts.value.forEach((item) => {
      item.unitPrice = ''
      item.pricingMode = ''
      item.priceSource = ''
      item.priceSourceReceiptItemId = ''
      item.priceEditable = false
    })
    return
  }
  await Promise.all(drafts.value.map((item, index) => selectMaterial(index, item.materialId)))
}

async function save(): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    let id: string
    if (createMode.value === 'FROM_REQUEST') {
      const command: PurchaseOrderFromRequestCommand = {
        projectId: required(form, 'projectId', '项目'),
        contractId: required(form, 'contractId', '采购合同'),
        requestId: required(form, 'requestId', '已审批采购申请'),
        orderDate: optional(form, 'orderDate'),
        deliveryDate: optional(form, 'deliveryDate'),
        deliveryTerms: required(form, 'deliveryTerms', '交付条件'),
        remark: optional(form, 'remark'),
      }
      // 服务端按申请审批快照复制明细并定价；前端不提交金额、数量或订单明细事实。
      id = await savePurchaseOrder({ kind: 'FROM_REQUEST', command }, application)
    } else {
      const command: PurchaseOrderCommand = {
        projectId: required(form, 'projectId', '项目'),
        contractId: required(form, 'contractId', '采购合同'),
        partnerId: optional(form, 'partnerId'),
        orderDate: optional(form, 'orderDate'),
        deliveryDate: optional(form, 'deliveryDate'),
        deliveryTerms: optional(form, 'deliveryTerms'),
        exceptionPurchaseFlag: 1,
        exceptionReason: required(form, 'exceptionReason', '例外原因'),
        remark: optional(form, 'remark'),
      }
      id = await savePurchaseOrder(
        {
          kind: 'CREATE_EXCEPTION',
          command,
          saveItems: canSaveItems.value,
          items: (orderId) =>
            drafts.value.map((item, index) => ({
              orderId,
              projectId: command.projectId,
              materialId: requiredDraft(item.materialId, `第${index + 1}条物料`),
              budgetLineId: requiredDraft(item.budgetLineId, `第${index + 1}条预算科目`),
              quantity: positiveValue(item.quantity, `第${index + 1}条订单数量`),
              unitPrice: positiveValue(item.unitPrice, `第${index + 1}条服务端单价`),
              taxRate: taxRateValue(item.taxRate, `第${index + 1}条税率`),
              unit: item.unit.trim() || undefined,
              priceSource: item.priceSource || undefined,
              priceSourceReceiptItemId: item.priceSourceReceiptItemId || undefined,
              remark: item.remark.trim() || undefined,
            })),
        },
        application,
      )
    }
    editorOpen.value = false
    await loadPage()
    const created = records.value.find((record) => record.id === id)
    if (created) await selectRecord(created)
    showToast('success', '操作成功', '采购订单已保存，列表与详情已更新')
  } catch (error) {
    if (error instanceof NewPurchaseOrderSaveError) {
      const failure = errorText(error.saveError, '采购订单保存失败')
      errorMessage.value = error.rollbackFailed
        ? `${failure}；草稿回滚失败：${errorText(error.rollbackError, '需要人工核对')}`
        : `${failure}；本次新建草稿已回滚`
    } else errorMessage.value = errorText(error, '采购订单保存失败')
    showToast('error', '采购订单保存失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function openEdit(): Promise<void> {
  if (!selected.value || !canEditSelected.value) return
  const order = selected.value as PurchaseOrderRecord & {
    deliveryTerms?: string | null
    exceptionPurchaseFlag?: number | null
    exceptionReason?: string | null
  }
  for (const key of Object.keys(editForm)) delete editForm[key]
  Object.assign(editForm, {
    projectId: order.projectId,
    contractId: order.contractId || '',
    partnerId: order.partnerId || '',
    orderCode: order.orderCode,
    orderType: order.orderType || '',
    orderDate: order.orderDate || '',
    deliveryDate: order.deliveryDate || '',
    deliveryTerms: order.deliveryTerms || '',
    exceptionPurchaseFlag: String(order.exceptionPurchaseFlag || 0),
    exceptionReason: order.exceptionReason || '',
    remark: order.remark || '',
  })
  editItems.value = detailItems.value.map((item) => ({
    source: item,
    budgetLineId: item.budgetLineId || '',
    unitPrice: item.unitPrice || '',
    taxRate: item.taxRate || '0',
  }))
  editOpen.value = true
  busy.value = true
  try {
    const [partnerPage, contractPage, currentContract] = await Promise.all([
      loadPartners({ pageNo: 1, pageSize: 200, partnerType: 'SUPPLIER', status: 'ENABLE' }),
      loadContractPage({
        pageNo: 1,
        pageSize: 200,
        projectId: order.projectId,
        contractType: 'PURCHASE',
        approvalStatus: 'APPROVED',
        contractStatus: 'PERFORMING',
      }),
      order.contractId ? loadContract(order.contractId) : Promise.resolve(null),
      loadActiveBudgetLines(order.projectId),
    ])
    partners.value = partnerPage.records
    contracts.value =
      currentContract && !contractPage.records.some((item) => item.id === currentContract.id)
        ? [...contractPage.records, currentContract]
        : contractPage.records
  } catch (error) {
    showToast('error', '供应商读取失败', errorText(error, '供应商读取失败'))
  } finally {
    busy.value = false
  }
}

function requiredEdit(name: string, label: string): string {
  return required(editForm, name, label)
}

async function saveEdit(): Promise<void> {
  if (!selected.value || busy.value) return
  const id = selected.value.id
  busy.value = true
  try {
    if (!editItems.value.length) throw new TypeError('采购订单至少需要一条明细')
    await savePurchaseOrder(
      {
        kind: 'EDIT',
        id,
        command: {
          projectId: requiredEdit('projectId', '项目'),
          contractId: requiredEdit('contractId', '采购合同'),
          partnerId: requiredEdit('partnerId', '供应商'),
          orderCode: requiredEdit('orderCode', '采购订单号'),
          orderType: optional(editForm, 'orderType'),
          orderDate: requiredEdit('orderDate', '订单日期'),
          deliveryDate: requiredEdit('deliveryDate', '交付日期'),
          deliveryTerms: requiredEdit('deliveryTerms', '交付条件'),
          exceptionPurchaseFlag: Number(editForm.exceptionPurchaseFlag || '0'),
          exceptionReason: optional(editForm, 'exceptionReason'),
          remark: optional(editForm, 'remark'),
        },
        items: (orderId) =>
          editItems.value.map(({ source, budgetLineId, unitPrice, taxRate }, index) => ({
            orderId,
            requestItemId: source.requestItemId,
            wbsTaskId: source.wbsTaskId,
            budgetLineId: requiredSourceId(budgetLineId, `第${index + 1}条预算科目`),
            projectId: source.projectId,
            materialId: requiredSourceId(source.materialId, `第${index + 1}条物料`),
            unit: source.unit,
            quantity: source.quantity,
            unitPrice: positiveValue(unitPrice, `第${index + 1}条单价`),
            priceSource: source.priceSource,
            priceSourceReceiptItemId: source.priceSourceReceiptItemId,
            taxRate: taxRateValue(taxRate, `第${index + 1}条税率`),
            receivedQuantity: source.receivedQuantity,
            remark: source.remark,
          })),
      },
      application,
    )
    editOpen.value = false
    await loadPage()
    const refreshed = records.value.find((record) => record.id === id)
    if (refreshed) await selectRecord(refreshed)
    showToast('success', '采购订单已更新', '商业条件已刷新')
  } catch (error) {
    showToast('error', '采购订单更新失败', errorText(error, '采购订单更新失败'))
  } finally {
    busy.value = false
  }
}

async function submitSelected(): Promise<void> {
  if (!selected.value || busy.value) return
  const id = selected.value.id
  busy.value = true
  try {
    await submitSavedPurchaseOrder(id, application)
    await loadPage()
    const refreshed = records.value.find((record) => record.id === id)
    if (refreshed) await selectRecord(refreshed)
    showToast('success', '操作成功', '采购订单已提交，状态已重新读取')
  } catch (error) {
    showToast('error', '采购订单提交失败', errorText(error, '采购订单提交失败'))
  } finally {
    busy.value = false
  }
}

async function openSourceRequest(): Promise<void> {
  if (!sourceRequest.value || !selected.value) return
  await router.push({
    path: '/inventory/purchase-request',
    query: { projectId: selected.value.projectId, requestId: sourceRequest.value.id },
  })
}

function changePage(next: number): void {
  if (next < 1 || next > pageCount.value || next === pageNo.value) return
  pageNo.value = next
  void loadPage()
}

watch(
  projectId,
  () => {
    pageNo.value = 1
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
  <section class="purchase-execution-page">
    <V2Card title="采购订单" :heading-level="1">
      <template #actions>
        <V2Button v-if="canAdd" size="small" @click="openCreate('FROM_REQUEST')"
          >新建采购订单</V2Button
        >
        <V2Button v-if="canAdd" variant="secondary" size="small" @click="openCreate('EXCEPTION')"
          >新建例外采购订单</V2Button
        >
      </template>
    </V2Card>

    <V2PageState
      v-if="loading && !records.length"
      kind="loading"
      title="正在加载"
      description="正在读取采购订单。"
    />
    <V2PageState
      v-else-if="!errorMessage && !loading && !records.length"
      title="暂无记录"
      description="当前项目范围没有采购订单。"
    >
      <template v-if="canAdd" #actions>
        <V2Button @click="openCreate('FROM_REQUEST')">新建采购订单</V2Button>
        <V2Button variant="secondary" @click="openCreate('EXCEPTION')">新建例外采购订单</V2Button>
      </template>
    </V2PageState>

    <section v-else class="purchase-execution-page__layout">
      <V2Card :heading-level="2">
        <div class="purchase-execution-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>编号</th>
                <th>来源</th>
                <th>审批状态</th>
                <th>业务状态</th>
                <th>金额</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="record in records"
                :key="record.id"
                :class="{ selected: selected?.id === record.id }"
              >
                <td>
                  <V2Button
                    variant="ghost"
                    size="small"
                    class="v2-table__record-link"
                    @click="selectRecord(record)"
                    >{{ orderCode(record) }}</V2Button
                  >
                </td>
                <td>{{ record.requestCode || record.partnerName || '例外采购' }}</td>
                <td>
                  <V2Badge>{{ statusLabel(record.approvalStatus) }}</V2Badge>
                </td>
                <td>
                  <V2Badge>{{ statusLabel(record.orderStatus) }}</V2Badge>
                </td>
                <td>{{ recordAmount(record) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="purchase-execution-page__pagination" aria-label="采购订单分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              variant="secondary"
              size="small"
              :disabled="pageNo <= 1 || loading"
              @click="changePage(pageNo - 1)"
              >上一页</V2Button
            >
            <span>第 {{ pageNo }} 页</span>
            <V2Button
              variant="secondary"
              size="small"
              :disabled="pageNo >= pageCount || loading"
              @click="changePage(pageNo + 1)"
              >下一页</V2Button
            >
          </nav>
        </template>
      </V2Card>
    </section>

    <PurchaseExecutionDetail
      v-if="selected"
      open
      mode="order"
      title="采购订单"
      :business-id="selected.id"
      :business-code="orderCode(selected)"
      :project-name="selected.projectName"
      :source-label="selected.requestCode || selected.partnerName || '例外采购'"
      :approval-status="selected.approvalStatus"
      :business-status="statusLabel(selected.orderStatus)"
      :amount="selected.totalAmount"
      :source-request="sourceRequest"
      :detail-table="detailTable"
      :detail-loading="detailLoading"
      :can-edit="canEditSelected"
      :can-manage-attachments="canSaveItems"
      :can-submit="canSubmitSelected"
      @close="clearDetail"
      @edit="openEdit"
      @submit="submitSelected"
      @open-source-request="openSourceRequest"
    />

    <V2Dialog
      v-model:open="editorOpen"
      :title="createMode === 'FROM_REQUEST' ? '新建采购订单' : '新建例外采购订单'"
      :description="
        createMode === 'FROM_REQUEST'
          ? '选择已审批采购申请与采购合同后手工建单；明细、数量和价格由服务端审批快照与合同事实决定。'
          : '例外采购须填写业务依据；仅用于有业务依据的例外采购。'
      "
      :close-disabled="busy"
      :close-on-backdrop="false"
      panel-class="v2-dialog-wide"
    >
      <form
        id="purchase-order-editor-form"
        class="purchase-execution-page__form"
        @submit.prevent="save"
      >
        <V2Select
          v-model="form.projectId"
          label="项目"
          :options="workspace.projects"
          :disabled="busy"
          required
          @update:model-value="changeProject"
        />
        <V2Select
          v-if="createMode === 'FROM_REQUEST'"
          v-model="form.requestId"
          label="已审批采购申请"
          :options="requestOptions"
          :disabled="busy"
          required
          @update:model-value="changeRequest"
        />
        <p
          v-if="createMode === 'FROM_REQUEST' && !requestCandidates.length"
          class="purchase-execution-page__form-hint"
        >
          当前项目暂无同时满足审批状态与业务状态为“已通过”的采购申请。
        </p>
        <V2Select
          v-model="form.contractId"
          label="采购合同"
          :options="contractOptions"
          :disabled="busy"
          required
          @update:model-value="changeContract"
        />
        <V2Select
          v-if="createMode === 'EXCEPTION'"
          v-model="form.partnerId"
          label="供应商"
          :options="partnerOptions"
          disabled
          required
        />
        <V2Input v-model="form.orderDate" label="订单日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="form.deliveryDate" label="交付日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="form.deliveryTerms" label="交付条件" required />
        <V2Input
          v-if="createMode === 'EXCEPTION'"
          v-model="form.exceptionReason"
          label="例外原因"
          required
        />
        <section
          class="purchase-execution-page__draft-lines"
          aria-labelledby="purchase-order-lines-title"
        >
          <div class="purchase-execution-page__draft-heading">
            <h3 id="purchase-order-lines-title">采购订单明细</h3>
            <V2Button
              v-if="createMode === 'EXCEPTION'"
              type="button"
              size="small"
              variant="secondary"
              :disabled="busy || drafts.length >= 200"
              @click="addItem"
              >添加明细</V2Button
            >
            <span v-else class="purchase-execution-page__form-hint"
              >明细来自已审批采购申请，只读展示；保存时由服务端重新读取审批快照。</span
            >
          </div>
          <div class="purchase-execution-page__draft-table-wrap">
            <table class="purchase-execution-page__draft-table">
              <thead>
                <tr>
                  <th>物料编码/名称*</th>
                  <th>预算科目*</th>
                  <th>订单数量*</th>
                  <th>单位*</th>
                  <th>服务端建议单价</th>
                  <th>税率</th>
                  <th>计价来源</th>
                  <th v-if="createMode === 'EXCEPTION'">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in drafts" :key="item.requestItemId || index">
                  <td>
                    <span v-if="createMode === 'FROM_REQUEST'">{{
                      materialLabel(item.materialId)
                    }}</span
                    ><V2Select
                      v-else
                      v-model="item.materialId"
                      :label="`第${index + 1}条物料`"
                      hide-label
                      :options="materialOptions"
                      :disabled="busy"
                      required
                      @update:model-value="selectMaterial(index, $event)"
                    />
                  </td>
                  <td>
                    <span v-if="createMode === 'FROM_REQUEST'">{{
                      budgetLineLabel(item.budgetLineId)
                    }}</span
                    ><V2Select
                      v-else
                      v-model="item.budgetLineId"
                      :label="`第${index + 1}条预算科目`"
                      hide-label
                      :options="budgetLineOptions"
                      :disabled="busy || !form.projectId"
                      required
                    />
                  </td>
                  <td>
                    <span v-if="createMode === 'FROM_REQUEST'">{{
                      formatDecimal(item.quantity)
                    }}</span
                    ><V2Input
                      v-else
                      v-model="item.quantity"
                      :label="`第${index + 1}条订单数量`"
                      hide-label
                      :decimal-scale="2"
                      required
                    />
                  </td>
                  <td>
                    <span v-if="createMode === 'FROM_REQUEST'">{{ item.unit || '-' }}</span
                    ><V2Input
                      v-else
                      v-model="item.unit"
                      :label="`第${index + 1}条单位`"
                      hide-label
                      required
                    />
                  </td>
                  <td>
                    <span v-if="createMode === 'FROM_REQUEST'">{{
                      formatAmount(item.unitPrice)
                    }}</span
                    ><V2Input
                      v-else
                      v-model="item.unitPrice"
                      :label="`第${index + 1}条服务端建议单价`"
                      hide-label
                      :decimal-scale="2"
                      :disabled="!item.priceEditable"
                      required
                    />
                  </td>
                  <td>
                    <span v-if="createMode === 'FROM_REQUEST'">由服务端合同事实决定</span
                    ><V2Input
                      v-else
                      v-model="item.taxRate"
                      :label="`第${index + 1}条税率`"
                      hide-label
                      :decimal-scale="2"
                      required
                    />
                  </td>
                  <td>
                    <span v-if="item.pricingMode"
                      >{{ item.pricingMode === 'FIXED' ? '合同固定价' : '实际结算价' }} ·
                      {{ item.priceSource || '服务端' }}</span
                    ><span v-else>-</span>
                  </td>
                  <td v-if="createMode === 'EXCEPTION'">
                    <V2Button
                      type="button"
                      size="small"
                      variant="ghost"
                      :disabled="busy || drafts.length <= 1"
                      @click="removeItem(index)"
                      >删除</V2Button
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <V2Input v-model="form.remark" label="备注" />
      </form>
      <template #footer
        ><V2Button variant="secondary" :disabled="busy" @click="editorOpen = false">取消</V2Button
        ><V2Button type="submit" form="purchase-order-editor-form" :loading="busy"
          >保存</V2Button
        ></template
      >
    </V2Dialog>

    <V2Dialog
      v-model:open="editOpen"
      title="编辑采购订单商业条件"
      description="已审批采购订单商业条件按服务端事实维护；来源明细不可改。"
      :close-disabled="busy"
      :close-on-backdrop="false"
    >
      <form
        id="purchase-order-commercial-form"
        class="purchase-execution-page__form"
        @submit.prevent="saveEdit"
      >
        <V2Input v-model="editForm.orderCode" label="采购订单号" disabled required />
        <V2Select
          v-model="editForm.contractId"
          label="采购合同"
          :options="contractOptions"
          disabled
          required
        />
        <V2Select
          v-model="editForm.partnerId"
          label="供应商"
          :options="partnerOptions"
          disabled
          required
        />
        <V2Input v-model="editForm.orderDate" label="订单日期" placeholder="YYYY-MM-DD" required />
        <V2Input
          v-model="editForm.deliveryDate"
          label="交付日期"
          placeholder="YYYY-MM-DD"
          required
        />
        <V2Input v-model="editForm.deliveryTerms" label="交付条件" required />
        <V2Input v-model="editForm.remark" label="备注" />
        <section aria-labelledby="purchase-order-item-price-title">
          <h3 id="purchase-order-item-price-title">明细价格与税率</h3>
          <div v-for="(item, index) in editItems" :key="item.source.id || index">
            <p>
              {{ item.source.materialName || '物料名称缺失' }} · 数量
              {{ formatDecimal(item.source.quantity) }} · 来源行不可修改
            </p>
            <V2Select
              v-model="item.budgetLineId"
              :label="`第${index + 1}条预算科目`"
              :options="budgetLineOptions"
              :disabled="busy"
              required
            />
            <V2Input
              v-model="item.unitPrice"
              :label="`第${index + 1}条单价`"
              :decimal-scale="2"
              :disabled="item.source.pricingMode === 'FIXED'"
              required
            />
            <p v-if="item.source.pricingMode === 'FIXED'">固定单价：以合同约定为准</p>
            <p v-else-if="item.source.pricingMode === 'ACTUAL'">
              实际单价来源：{{ item.source.priceSource || '服务端合同价格' }}
            </p>
            <V2Input
              v-model="item.taxRate"
              :label="`第${index + 1}条税率`"
              :decimal-scale="2"
              required
            />
          </div>
        </section>
      </form>
      <template #footer
        ><V2Button variant="secondary" :disabled="busy" @click="editOpen = false">取消</V2Button
        ><V2Button type="submit" form="purchase-order-commercial-form" :loading="busy"
          >保存</V2Button
        ></template
      >
    </V2Dialog>
  </section>
</template>

<style src="./purchase-execution.css"></style>
