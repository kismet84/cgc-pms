<script setup lang="ts">
import type {
  BudgetLineRecord,
  ContractPage,
  MaterialRecord,
  PartnerRecord,
  PurchaseOrderCommand,
  PurchaseOrderItemRecord,
  PurchaseOrderRecord,
  PurchaseRequestCommand,
  PurchaseRequestItemRecord,
  PurchaseRequestRecord,
  ReceiptCommand,
  ReceiptItemRecord,
  ReceiptRecord,
  SiteFileRecord,
  WarehouseRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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
import {
  createPurchaseOrder,
  createPurchaseRequest,
  createReceipt,
  deletePurchaseOrder,
  deletePurchaseRequest,
  deleteReceipt,
  loadPurchaseOrder,
  loadPurchaseOrderItems,
  loadPurchaseOrders,
  loadPurchaseRequest,
  loadPurchaseRequestItems,
  loadPurchaseRequests,
  loadMaterials,
  loadReceipt,
  loadReceiptItems,
  loadReceipts,
  loadOrderItemsForReceipt,
  loadWarehouses,
  savePurchaseOrderItems,
  savePurchaseRequestItems,
  saveReceiptItems,
  submitPurchaseOrder,
  submitPurchaseRequest,
  submitReceipt,
  updatePurchaseOrder,
  updateReceipt,
} from '@/services/supply-chain'
import { loadBudget, loadBudgetPage, loadContractPage, loadPartners } from '@/services/commercial'
import { getSiteFileUrl, listSiteFiles, uploadSiteFile } from '@/services/delivery'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

type Mode = 'request' | 'order' | 'receipt'
type ListRecord = PurchaseRequestRecord | PurchaseOrderRecord | ReceiptRecord
type DetailItem = PurchaseRequestItemRecord | PurchaseOrderItemRecord | ReceiptItemRecord

const route = useRoute()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const records = ref<ListRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const selected = ref<ListRecord | null>(null)
const detailItems = ref<DetailItem[]>([])
const attachments = ref<SiteFileRecord[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const dialogOpen = ref(false)
const orderEditOpen = ref(false)
const editingReceiptId = ref('')
const receiptCandidates = ref<ReceiptItemRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const partners = ref<PartnerRecord[]>([])
const contracts = ref<ContractPage['records']>([])
const budgetLines = ref<BudgetLineRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const requestCandidates = ref<PurchaseRequestRecord[]>([])
const requestItemCandidates = ref<PurchaseRequestItemRecord[]>([])
const orderCandidates = ref<PurchaseOrderRecord[]>([])
const form = reactive<Record<string, string>>({})
const orderEditForm = reactive<Record<string, string>>({})
let listController: AbortController | null = null
let detailController: AbortController | null = null
let listGeneration = 0
let detailGeneration = 0

const mode = computed<Mode>(() =>
  route.path === '/purchase/order'
    ? 'order'
    : route.path === '/purchase/receipt'
      ? 'receipt'
      : 'request',
)
const projectId = computed(() => workspace.selectedProjectId || '')
const title = computed(
  () => ({ request: '采购申请', order: '采购订单', receipt: '材料验收' })[mode.value],
)
const permissions = computed(
  () =>
    ({
      request: {
        add: 'purchase:request:add',
        edit: 'purchase:request:edit',
        delete: 'purchase:request:delete',
        submit: 'purchase:request:submit',
      },
      order: {
        add: 'purchase:order:add',
        edit: 'purchase:order:edit',
        delete: 'purchase:order:delete',
        submit: 'purchase:order:submit',
      },
      receipt: {
        add: 'receipt:add',
        edit: 'receipt:edit',
        delete: 'receipt:delete',
        submit: 'receipt:submit',
      },
    })[mode.value],
)
const canAdd = computed(
  () =>
    session.hasAdminOrPermission(permissions.value.add) &&
    session.hasAdminOrPermission(permissions.value.edit) &&
    session.hasAdminOrPermission(permissions.value.delete),
)
const canSubmit = computed(() => session.hasAdminOrPermission(permissions.value.submit))
const canSaveItems = computed(() => session.hasAdminOrPermission(permissions.value.edit))
const canSubmitSelected = computed(
  () => canSubmit.value && selected.value?.approvalStatus === 'DRAFT',
)
const canEditSelectedOrder = computed(
  () => mode.value === 'order' && canSaveItems.value && selected.value?.approvalStatus === 'DRAFT',
)
const canEditSelectedReceipt = computed(
  () =>
    mode.value === 'receipt' && canSaveItems.value && selected.value?.approvalStatus === 'DRAFT',
)
const attachmentBusinessType = computed(
  () =>
    ({ request: 'PURCHASE_REQUEST', order: 'PURCHASE_ORDER', receipt: 'MATERIAL_RECEIPT' })[
      mode.value
    ],
)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const receiptCandidateOptions = computed(() =>
  receiptCandidates.value.map((item) => ({
    value: item.orderItemId || '',
    label: `${item.materialName || '物料名称缺失'} · 剩余 ${item.remainingQuantity ?? '-'}`,
  })),
)
const materialOptions = computed(() =>
  materials.value.map((item) => ({
    value: item.id,
    label: [item.materialCode, item.materialName, item.specification].filter(Boolean).join(' · '),
  })),
)
const contractOptions = computed(() =>
  contracts.value.map((item) => ({
    value: item.id,
    label: [item.contractCode, item.contractName].filter(Boolean).join(' · '),
  })),
)
const budgetLineOptions = computed(() =>
  budgetLines.value
    .filter((item): item is BudgetLineRecord & { id: string } => Boolean(item.id))
    .map((item) => ({
      value: item.id,
      label: item.costSubjectName || item.costSubjectId,
    })),
)
const partnerOptions = computed(() => [
  { value: '', label: '不指定供应商' },
  ...partners.value.map((item) => ({
    value: item.id,
    label: [item.partnerCode, item.partnerName].filter(Boolean).join(' · '),
  })),
])
const warehouseOptions = computed(() => [
  { value: '', label: '不入库' },
  ...warehouses.value.map((item) => ({
    value: item.id,
    label: [item.warehouseCode, item.warehouseName].filter(Boolean).join(' · '),
  })),
])
const requestOptions = computed(() => [
  { value: '', label: '例外采购（无申请来源）' },
  ...requestCandidates.value.map((item) => ({
    value: item.id,
    label: [recordCode(item), item.purpose].filter(Boolean).join(' · '),
  })),
])
const requestItemOptions = computed(() =>
  requestItemCandidates.value
    .filter((item) => item.id)
    .map((item) => ({
      value: item.id || '',
      label: `${item.materialName || '物料名称缺失'} · ${item.quantity}`,
    })),
)
const orderOptions = computed(() =>
  orderCandidates.value.map((item) => ({
    value: item.id,
    label: [recordCode(item), item.partnerName].filter(Boolean).join(' · '),
  })),
)

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

function decimal(name: string, label: string): string {
  const value = required(name, label)
  if (!/^\d+(?:\.\d+)?$/.test(value)) throw new TypeError(`${label}必须为非负十进制数`)
  return value
}

function recordCode(record: ListRecord): string {
  const businessCode = (value: string | null | undefined, label: string) =>
    value && !/^\d{15,}$/.test(value) ? value : `未生成${label}号`
  return 'receiptCode' in record
    ? businessCode(record.receiptCode, '验收单')
    : 'orderCode' in record
      ? businessCode(record.orderCode, '采购订单')
      : businessCode(record.requestCode, '采购申请')
}

function statusLabel(status?: string | null): string {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '待处理',
    APPROVING: '审批中',
    APPROVED: '已通过',
    CONVERTED: '已转订单',
    REJECTED: '已驳回',
    IN_PROGRESS: '进行中',
    PARTIAL_RECEIVED: '部分到货',
    RECEIVED: '已到货',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    QUALIFIED: '合格',
    PARTIAL: '部分合格',
    PARTIAL_QUALIFIED: '部分合格',
    PARTIALLY_QUALIFIED: '部分合格',
    UNQUALIFIED: '不合格',
    RETURN: '退货',
    REPLACE: '换货',
    CONCESSION: '让步接收',
  }
  return status ? (labels[status] ?? '未知状态') : '未知状态'
}

function recordBusinessStatus(record: ListRecord): string {
  if ('receiptCode' in record) return statusLabel(record.qualityStatus || record.approvalStatus)
  if ('orderStatus' in record) return statusLabel(record.orderStatus)
  return statusLabel(record.status)
}

function clearDetail(): void {
  detailController?.abort()
  selected.value = null
  detailItems.value = []
  attachments.value = []
}

async function uploadAttachment(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selected.value || busy.value) return
  await uploadSelectedAttachment(file)
  input.value = ''
}

async function generateBusinessAttachment(): Promise<void> {
  if (!selected.value || busy.value) return
  const content = [
    `${title.value}业务说明`,
    `单据编号：${recordCode(selected.value)}`,
    `项目：${selected.value.projectName || '项目名称缺失'}`,
    `来源：${recordSource(selected.value)}`,
    `金额：${recordAmount(selected.value)}`,
    `明细数量：${detailItems.value.length}条`,
  ].join('\n')
  await uploadSelectedAttachment(
    new File([content], `${recordCode(selected.value)}-业务说明.txt`, { type: 'text/plain' }),
  )
}

async function uploadSelectedAttachment(file: File): Promise<void> {
  if (!selected.value || busy.value) return
  const record = selected.value
  const businessType = attachmentBusinessType.value
  busy.value = true
  errorMessage.value = ''
  try {
    await uploadSiteFile(file, businessType, record.id, 'OTHER')
    const next = await listSiteFiles(businessType, record.id)
    if (selected.value?.id === record.id) attachments.value = next
    showToast('success', '附件上传成功', `${file.name} 已通过安全检查，附件列表已更新`)
  } catch (error) {
    errorMessage.value = errorText(error, '附件上传失败')
    showToast('error', '附件上传失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function downloadAttachment(file: SiteFileRecord): Promise<void> {
  try {
    window.open(await getSiteFileUrl(file.id), '_blank', 'noopener,noreferrer')
  } catch (error) {
    showToast('error', '附件下载失败', errorText(error, '下载链接获取失败'))
  }
}

function requiredOrderEdit(name: string, label: string): string {
  const value = orderEditForm[name]?.trim() ?? ''
  if (!value) throw new TypeError(`${label}不能为空`)
  return value
}

function optionalOrderEdit(name: string): string | undefined {
  return orderEditForm[name]?.trim() || undefined
}

async function openOrderEdit(): Promise<void> {
  if (!selected.value || mode.value !== 'order') return
  const order = selected.value as PurchaseOrderRecord & {
    deliveryTerms?: string | null
    exceptionPurchaseFlag?: number | null
    exceptionReason?: string | null
  }
  for (const key of Object.keys(orderEditForm)) delete orderEditForm[key]
  Object.assign(orderEditForm, {
    projectId: order.projectId,
    requestId: order.requestId || '',
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
  orderEditOpen.value = true
  busy.value = true
  try {
    partners.value = (
      await loadPartners({ pageNo: 1, pageSize: 200, partnerType: 'SUPPLIER', status: 'ENABLE' })
    ).records
  } catch (error) {
    errorMessage.value = errorText(error, '供应商读取失败')
    showToast('error', '供应商读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function saveOrderEdit(): Promise<void> {
  if (!selected.value || busy.value) return
  busy.value = true
  errorMessage.value = ''
  const id = selected.value.id
  try {
    await updatePurchaseOrder(id, {
      projectId: requiredOrderEdit('projectId', '项目'),
      requestId: optionalOrderEdit('requestId'),
      contractId: optionalOrderEdit('contractId'),
      partnerId: requiredOrderEdit('partnerId', '供应商'),
      orderCode: requiredOrderEdit('orderCode', '采购订单号'),
      orderType: optionalOrderEdit('orderType'),
      orderDate: requiredOrderEdit('orderDate', '订单日期'),
      deliveryDate: requiredOrderEdit('deliveryDate', '交付日期'),
      deliveryTerms: requiredOrderEdit('deliveryTerms', '交付条件'),
      exceptionPurchaseFlag: Number(orderEditForm.exceptionPurchaseFlag || '0'),
      exceptionReason: optionalOrderEdit('exceptionReason'),
      remark: optionalOrderEdit('remark'),
    })
    orderEditOpen.value = false
    await loadPage()
    const refreshed = records.value.find((record) => record.id === id)
    if (refreshed) await selectRecord(refreshed)
    showToast('success', '采购订单已更新', '商业条件已刷新')
  } catch (error) {
    errorMessage.value = errorText(error, '采购订单更新失败')
    showToast('error', '采购订单更新失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function recordSource(record: ListRecord): string {
  if ('requestCode' in record) return record.contractName || record.purpose || '-'
  if ('receiptCode' in record) return record.orderCode || '采购订单编号缺失'
  if ('orderCode' in record) return record.requestCode || record.partnerName || '例外采购'
  return '-'
}

function recordAmount(record: ListRecord): string {
  return 'totalAmount' in record && record.totalAmount != null ? record.totalAmount : '暂无金额'
}

function itemName(item: DetailItem): string {
  return item.materialName || '物料名称缺失'
}

const detailTable = computed(() => {
  if (mode.value === 'request') {
    return {
      columns: ['物料', '规格', '单位', '数量', '预计单价', '预计金额', '计划日期'],
      rows: detailItems.value.map((item, index) => {
        const requestItem = item as PurchaseRequestItemRecord
        return {
          key: requestItem.id || `${index}`,
          cells: [
            itemName(requestItem),
            '-',
            requestItem.unit || '-',
            requestItem.quantity,
            requestItem.estimatedUnitPrice ?? '-',
            requestItem.estimatedAmount ?? '-',
            requestItem.plannedDate || '-',
          ],
        }
      }),
    }
  }
  if (mode.value === 'order') {
    return {
      columns: ['物料', '规格', '单位', '数量', '单价', '金额', '已收数量'],
      rows: detailItems.value.map((item, index) => {
        const orderItem = item as PurchaseOrderItemRecord
        return {
          key: orderItem.id || `${index}`,
          cells: [
            itemName(orderItem),
            orderItem.specification || '-',
            orderItem.unit || '-',
            orderItem.quantity,
            orderItem.unitPrice,
            orderItem.amount ?? '-',
            orderItem.receivedQuantity ?? '-',
          ],
        }
      }),
    }
  }
  return {
    columns: [
      '物料',
      '规格',
      '单位',
      '实收数量',
      '合格数量',
      '不合格数量',
      '订单数量',
      '累计收货',
      '剩余数量',
      '单价',
      '金额',
      '使用部位',
      '不合格处置',
      '处置原因',
    ],
    rows: detailItems.value.map((item, index) => {
      const receiptItem = item as ReceiptItemRecord
      return {
        key: receiptItem.id || `${index}`,
        cells: [
          itemName(receiptItem),
          receiptItem.specification || '-',
          receiptItem.unit || '-',
          receiptItem.actualQuantity,
          receiptItem.qualifiedQuantity,
          receiptItem.unqualifiedQuantity,
          receiptItem.orderedQuantity ?? '-',
          receiptItem.receivedQuantity ?? '-',
          receiptItem.remainingQuantity ?? '-',
          receiptItem.unitPrice ?? '-',
          receiptItem.amount ?? '-',
          receiptItem.useLocation || '-',
          receiptItem.dispositionType ? statusLabel(receiptItem.dispositionType) : '-',
          receiptItem.dispositionReason || '-',
        ],
      }
    }),
  }
})

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
    const query = {
      pageNum: pageNo.value,
      pageSize,
      projectId: projectId.value || undefined,
    }
    const page =
      mode.value === 'request'
        ? await loadPurchaseRequests(query, controller.signal)
        : mode.value === 'order'
          ? await loadPurchaseOrders(query, controller.signal)
          : await loadReceipts(query, controller.signal)
    if (generation === listGeneration) {
      records.value = page.records
      total.value = page.total
    }
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, `${title.value}加载失败`)
      showToast('error', `${title.value}读取失败`, errorMessage.value)
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function selectRecord(record: ListRecord): Promise<void> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  selected.value = record
  detailItems.value = []
  attachments.value = []
  detailLoading.value = true
  try {
    const [detail, items] =
      mode.value === 'request'
        ? await Promise.all([
            loadPurchaseRequest(record.id, controller.signal),
            loadPurchaseRequestItems(record.id, controller.signal),
          ])
        : mode.value === 'order'
          ? await Promise.all([
              loadPurchaseOrder(record.id, controller.signal),
              loadPurchaseOrderItems(record.id, controller.signal),
            ])
          : await Promise.all([
              loadReceipt(record.id, controller.signal),
              loadReceiptItems(record.id, controller.signal),
            ])
    if (generation !== detailGeneration) return
    selected.value = detail
    detailItems.value = items
    try {
      const next = await listSiteFiles(attachmentBusinessType.value, record.id, controller.signal)
      if (generation === detailGeneration) attachments.value = next
    } catch (error) {
      if (!controller.signal.aborted && generation === detailGeneration) {
        showToast('warning', '附件读取失败', errorText(error, '附件列表加载失败'))
      }
    }
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      showToast('error', '详情读取失败', errorText(error, '详情加载失败'))
    }
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

async function openCreate(): Promise<void> {
  editingReceiptId.value = ''
  for (const key of Object.keys(form)) delete form[key]
  form.projectId = projectId.value
  receiptCandidates.value = []
  requestItemCandidates.value = []
  if (mode.value === 'request') Object.assign(form, { quantity: '1', estimatedUnitPrice: '0' })
  if (mode.value === 'order')
    Object.assign(form, { quantity: '1', unitPrice: '0', taxRate: '0', exceptionPurchaseFlag: '0' })
  if (mode.value === 'receipt')
    Object.assign(form, {
      receiptMode: 'INVENTORY',
      actualQuantity: '1',
      qualifiedQuantity: '1',
      unqualifiedQuantity: '0',
    })
  dialogOpen.value = true
  busy.value = true
  try {
    const candidateProjectId = form.projectId || undefined
    if (mode.value === 'request') {
      const [materialPage, contractPage, budgetPage] = await Promise.all([
        loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' }),
        loadContractPage({
          pageNo: 1,
          pageSize: 200,
          projectId: candidateProjectId,
          contractType: 'PURCHASE',
          approvalStatus: 'APPROVED',
        }),
        loadBudgetPage({
          pageNo: 1,
          pageSize: 100,
          projectId: candidateProjectId,
          status: 'ACTIVE',
        }),
      ])
      materials.value = materialPage.records
      contracts.value = contractPage.records
      const activeBudget = budgetPage.records.find((item) => item.active)
      budgetLines.value = activeBudget ? ((await loadBudget(activeBudget.id)).lines ?? []) : []
    } else if (mode.value === 'order') {
      const [requestPage, partnerPage, materialPage] = await Promise.all([
        loadPurchaseRequests({ pageNum: 1, pageSize: 200, projectId: candidateProjectId }),
        loadPartners({ pageNo: 1, pageSize: 200, partnerType: 'SUPPLIER', status: 'ENABLE' }),
        loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' }),
      ])
      requestCandidates.value = requestPage.records
      partners.value = partnerPage.records
      materials.value = materialPage.records
    } else {
      const [orderPage, warehousePage] = await Promise.all([
        loadPurchaseOrders({ pageNum: 1, pageSize: 200, projectId: candidateProjectId }),
        loadWarehouses({
          pageNo: 1,
          pageSize: 200,
          projectId: candidateProjectId,
          status: 'ENABLE',
        }),
      ])
      orderCandidates.value = orderPage.records
      warehouses.value = warehousePage.records
    }
  } catch (error) {
    errorMessage.value = errorText(error, '业务候选读取失败')
    showToast('error', '业务候选读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function openReceiptEdit(): Promise<void> {
  if (!canEditSelectedReceipt.value || !selected.value || !detailItems.value.length) return
  const receipt = selected.value as ReceiptRecord
  const item = detailItems.value[0] as ReceiptItemRecord
  await openCreate()
  editingReceiptId.value = receipt.id
  Object.assign(form, {
    projectId: receipt.projectId,
    orderId: receipt.orderId || '',
    contractId: receipt.contractId || '',
    partnerId: receipt.partnerId || '',
    receiptDate: receipt.receiptDate || '',
    warehouseId: receipt.warehouseId || '',
    receiverId: receipt.receiverId || '',
    receiptMode: receipt.receiptMode || 'INVENTORY',
    qualityStatus: receipt.qualityStatus || '',
    remark: receipt.remark || '',
  })
  await changeReceiptOrder(form.orderId)
  Object.assign(form, {
    orderItemId: item.orderItemId || '',
    materialId: item.materialId || '',
    wbsTaskId: item.wbsTaskId || '',
    budgetLineId: item.budgetLineId || '',
    actualQuantity: item.actualQuantity,
    qualifiedQuantity: item.qualifiedQuantity,
    unqualifiedQuantity: item.unqualifiedQuantity,
    batchNo: item.batchNo || '',
    useLocation: item.useLocation || '',
    dispositionType: item.dispositionType || '',
    dispositionStatus: item.dispositionStatus || '',
    dispositionReason: item.dispositionReason || '',
  })
  selected.value = null
}

function changeMaterial(value: string): void {
  form.materialId = value
  const material = materials.value.find((item) => item.id === value)
  form.materialName = material?.materialName || ''
  form.unit = material?.unit || ''
}

function changeRequestItem(value: string): void {
  form.requestItemId = value
  const item = requestItemCandidates.value.find((candidate) => candidate.id === value)
  if (!item) return
  form.materialId = item.materialId || ''
  form.materialName = item.materialName || ''
  form.unit = item.unit || ''
  form.quantity = item.quantity
  form.unitPrice = item.estimatedUnitPrice || form.unitPrice
}

async function changeRequest(value: string): Promise<void> {
  form.requestId = value
  form.requestItemId = ''
  requestItemCandidates.value = []
  if (!value || busy.value) return
  busy.value = true
  try {
    requestItemCandidates.value = await loadPurchaseRequestItems(value)
    changeRequestItem(requestItemCandidates.value[0]?.id || '')
  } catch (error) {
    errorMessage.value = errorText(error, '采购申请明细读取失败')
    showToast('error', '采购申请明细读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function changeReceiptItem(value: string): void {
  form.orderItemId = value
  const item = receiptCandidates.value.find((candidate) => candidate.orderItemId === value)
  form.materialId = item?.materialId || ''
}

async function changeReceiptOrder(value: string): Promise<void> {
  form.orderId = value
  form.orderItemId = ''
  receiptCandidates.value = []
  const order = orderCandidates.value.find((candidate) => candidate.id === value)
  form.contractId = order?.contractId || ''
  form.partnerId = order?.partnerId || ''
  if (!value) return
  await loadReceiptCandidates(value)
}

async function loadReceiptCandidates(orderId = form.orderId): Promise<void> {
  if (!orderId?.trim() || busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    receiptCandidates.value = await loadOrderItemsForReceipt(orderId)
    changeReceiptItem(receiptCandidates.value[0]?.orderItemId || '')
  } catch (error) {
    receiptCandidates.value = []
    errorMessage.value = errorText(error, '订单明细读取失败')
    showToast('error', '订单明细读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function changePage(next: number): void {
  if (next < 1 || next > pageCount.value || next === pageNo.value) return
  pageNo.value = next
  void loadPage()
}

async function save(): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  let createdId = ''
  try {
    let id: string
    if (mode.value === 'request') {
      const command: PurchaseRequestCommand = {
        projectId: required('projectId', '项目'),
        contractId: required('contractId', '采购合同'),
        purpose: optional('purpose'),
        remark: optional('remark'),
      }
      id = await createPurchaseRequest(command)
      createdId = id
      if (canSaveItems.value) {
        await savePurchaseRequestItems(id, [
          {
            requestId: id,
            materialId: required('materialId', '物料'),
            materialName: optional('materialName'),
            quantity: decimal('quantity', '申请数量'),
            budgetLineId: required('budgetLineId', '预算科目'),
            estimatedUnitPrice: decimal('estimatedUnitPrice', '预计单价'),
            unit: optional('unit'),
            plannedDate: optional('plannedDate'),
          },
        ])
      }
    } else if (mode.value === 'order') {
      const requestId = optional('requestId')
      const command: PurchaseOrderCommand = {
        projectId: required('projectId', '项目'),
        requestId,
        partnerId: optional('partnerId'),
        orderType: optional('orderType'),
        orderDate: optional('orderDate'),
        deliveryDate: optional('deliveryDate'),
        deliveryTerms: optional('deliveryTerms'),
        exceptionPurchaseFlag: requestId ? 0 : Number(form.exceptionPurchaseFlag || '0'),
        exceptionReason: optional('exceptionReason'),
        remark: optional('remark'),
      }
      id = await createPurchaseOrder(command)
      createdId = id
      if (canSaveItems.value) {
        await savePurchaseOrderItems(id, [
          {
            orderId: id,
            requestItemId: optional('requestItemId'),
            projectId: command.projectId,
            materialId: required('materialId', '物料'),
            materialName: optional('materialName'),
            quantity: decimal('quantity', '订单数量'),
            unitPrice: decimal('unitPrice', '订单单价'),
            taxRate: decimal('taxRate', '税率'),
            unit: optional('unit'),
          },
        ])
      }
    } else {
      const command: ReceiptCommand = {
        projectId: required('projectId', '项目'),
        orderId: required('orderId', '采购订单'),
        contractId: optional('contractId'),
        partnerId: optional('partnerId'),
        receiptDate: optional('receiptDate'),
        warehouseId:
          form.receiptMode === 'INVENTORY'
            ? required('warehouseId', '入库仓库')
            : optional('warehouseId'),
        receiverId: optional('receiverId'),
        receiptMode: (form.receiptMode || 'INVENTORY') as ReceiptCommand['receiptMode'],
        qualityStatus: optional('qualityStatus'),
        remark: optional('remark'),
      }
      id = editingReceiptId.value || (await createReceipt(command))
      if (editingReceiptId.value) await updateReceipt(id, command)
      else createdId = id
      if (canSaveItems.value) {
        await saveReceiptItems(id, [
          {
            receiptId: id,
            orderItemId: required('orderItemId', '订单明细'),
            materialId: optional('materialId'),
            wbsTaskId: optional('wbsTaskId'),
            budgetLineId: optional('budgetLineId'),
            actualQuantity: decimal('actualQuantity', '实收数量'),
            qualifiedQuantity: decimal('qualifiedQuantity', '合格数量'),
            unqualifiedQuantity: decimal('unqualifiedQuantity', '不合格数量'),
            batchNo: optional('batchNo'),
            useLocation: optional('useLocation'),
            dispositionType: optional('dispositionType'),
            dispositionStatus: optional('dispositionStatus'),
            dispositionReason: optional('dispositionReason'),
          },
        ])
      }
    }
    createdId = ''
    editingReceiptId.value = ''
    dialogOpen.value = false
    await loadPage()
    const created = records.value.find((record) => record.id === id)
    if (created) await selectRecord(created)
    showToast('success', '操作成功', `${title.value}已保存，列表与详情已更新`)
  } catch (error) {
    const failure = errorText(error, `${title.value}保存失败`)
    if (createdId) {
      try {
        if (mode.value === 'request') await deletePurchaseRequest(createdId)
        else if (mode.value === 'order') await deletePurchaseOrder(createdId)
        else await deleteReceipt(createdId)
        errorMessage.value = `${failure}；本次新建草稿已回滚`
      } catch (rollbackError) {
        errorMessage.value = `${failure}；草稿回滚失败：${errorText(rollbackError, '需要人工核对')}`
      }
    } else errorMessage.value = failure
    showToast('error', `${title.value}保存失败`, errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function submitSelected(): Promise<void> {
  if (!selected.value || busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    if (mode.value === 'request') await submitPurchaseRequest(selected.value.id)
    else if (mode.value === 'order') await submitPurchaseOrder(selected.value.id)
    else await submitReceipt(selected.value.id)
    const id = selected.value.id
    await loadPage()
    const refreshed = records.value.find((record) => record.id === id)
    if (refreshed) await selectRecord(refreshed)
    showToast('success', '操作成功', `${title.value}已提交，状态已重新读取`)
  } catch (error) {
    errorMessage.value = errorText(error, `${title.value}提交失败`)
    showToast('error', `${title.value}提交失败`, errorMessage.value)
  } finally {
    busy.value = false
  }
}

watch(
  [mode, projectId],
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
    <V2Card :title="title" :heading-level="1">
      <template #actions>
        <V2Button v-if="canAdd" size="small" @click="openCreate">新建{{ title }}</V2Button>
      </template>
    </V2Card>

    <V2PageState
      v-if="loading && !records.length"
      kind="loading"
      title="正在加载"
      :description="`正在读取${title}。`"
    />
    <V2PageState
      v-else-if="!errorMessage && !loading && !records.length"
      title="暂无记录"
      :description="`当前项目范围没有${title}。`"
    >
      <template v-if="canAdd" #actions
        ><V2Button @click="openCreate">新建{{ title }}</V2Button></template
      >
    </V2PageState>

    <section v-else class="purchase-execution-page__layout">
      <V2Card :heading-level="2">
        <div class="purchase-execution-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>编号</th>
                <th>来源</th>
                <th class="v2-table-cell--status">审批状态</th>
                <th class="v2-table-cell--status">业务状态</th>
                <th class="v2-table-cell--numeric">金额</th>
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
                  >
                    {{ recordCode(record) }}
                  </V2Button>
                </td>
                <td>{{ recordSource(record) }}</td>
                <td class="v2-table-cell--status">
                  <V2Badge>{{ statusLabel(record.approvalStatus) }}</V2Badge>
                </td>
                <td class="v2-table-cell--status">
                  <V2Badge>{{ recordBusinessStatus(record) }}</V2Badge>
                </td>
                <td class="v2-table-cell--numeric">{{ recordAmount(record) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav
          v-if="total > pageSize"
          class="purchase-execution-page__pagination"
          aria-label="采购执行分页"
        >
          <span>共 {{ total }} 条</span>
          <div>
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
          </div>
        </nav>
      </V2Card>

      <V2Dialog
        :open="Boolean(selected) && !orderEditOpen"
        :title="`${title}详情`"
        :description="selected ? recordCode(selected) : ''"
        panel-class="v2-detail-dialog"
        :close-on-backdrop="false"
        @close="clearDetail"
      >
        <template v-if="selected"
          ><V2PageState
            v-if="detailLoading"
            kind="loading"
            title="正在读取详情"
            description="请稍候。"
          />
          <template v-else>
            <dl class="purchase-execution-page__facts v2-detail-dialog__facts">
              <div>
                <dt>编号</dt>
                <dd>{{ recordCode(selected) }}</dd>
              </div>
              <div>
                <dt>项目</dt>
                <dd>{{ selected.projectName || '项目信息缺失' }}</dd>
              </div>
              <div>
                <dt>来源</dt>
                <dd>{{ recordSource(selected) }}</dd>
              </div>
              <div>
                <dt>审批状态</dt>
                <dd>{{ statusLabel(selected.approvalStatus) }}</dd>
              </div>
              <div>
                <dt>业务状态</dt>
                <dd>{{ recordBusinessStatus(selected) }}</dd>
              </div>
              <div>
                <dt>金额</dt>
                <dd>{{ recordAmount(selected) }}</dd>
              </div>
            </dl>
            <section class="v2-detail-dialog__section" aria-labelledby="purchase-detail-title">
              <div class="v2-detail-dialog__section-heading">
                <h3 id="purchase-detail-title">单据明细</h3>
                <V2Badge tone="info">{{ detailItems.length }} 条</V2Badge>
              </div>
              <V2PageState
                v-if="!errorMessage && !detailItems.length"
                title="暂无明细"
                description="当前单据暂无明细。"
                :heading-level="3"
              />
              <div
                v-else
                class="v2-detail-dialog__table"
                role="region"
                :aria-label="`${title}明细表格`"
                tabindex="0"
              >
                <table>
                  <thead>
                    <tr>
                      <th v-for="column in detailTable.columns" :key="column" scope="col">
                        {{ column }}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in detailTable.rows" :key="row.key">
                      <th scope="row">{{ row.cells[0] }}</th>
                      <td v-for="(cell, index) in row.cells.slice(1)" :key="index">
                        {{ cell }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
            <section class="v2-detail-dialog__section" aria-labelledby="purchase-attachment-title">
              <div class="v2-detail-dialog__section-heading">
                <h3 id="purchase-attachment-title">审批附件</h3>
                <V2Badge tone="info">{{ attachments.length }} 个</V2Badge>
              </div>
              <ul v-if="attachments.length" class="purchase-execution-page__attachments">
                <li v-for="file in attachments" :key="file.id">
                  <V2Button
                    type="button"
                    size="small"
                    variant="ghost"
                    @click="downloadAttachment(file)"
                  >
                    {{ file.originalName }}
                  </V2Button>
                </li>
              </ul>
              <p v-else class="purchase-execution-page__attachment-empty">暂无附件</p>
              <template v-if="selected.approvalStatus === 'DRAFT'">
                <label class="purchase-execution-page__attachment">
                  <span>选择已签认的业务附件</span>
                  <input
                    type="file"
                    :disabled="busy"
                    accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.txt"
                    @change="uploadAttachment"
                  />
                </label>
                <V2Button
                  type="button"
                  variant="secondary"
                  :loading="busy"
                  @click="generateBusinessAttachment"
                  >生成并上传单据说明</V2Button
                >
              </template>
            </section>
          </template></template
        >
        <template #footer>
          <V2Button
            v-if="canEditSelectedOrder"
            type="button"
            variant="secondary"
            :disabled="busy"
            @click="openOrderEdit"
            >编辑商业条件</V2Button
          >
          <V2Button
            v-if="canEditSelectedReceipt"
            type="button"
            variant="secondary"
            :disabled="busy"
            @click="openReceiptEdit"
            >编辑验收明细</V2Button
          >
          <V2Button v-if="canSubmitSelected" type="button" :loading="busy" @click="submitSelected"
            >提交审批</V2Button
          >
        </template>
      </V2Dialog>
    </section>

    <V2Dialog
      v-model:open="orderEditOpen"
      title="编辑采购订单商业条件"
      description="自动转单后补齐供应商、日期与交付条件。"
      :close-disabled="busy"
      :close-on-backdrop="false"
    >
      <form
        id="purchase-order-commercial-form"
        class="purchase-execution-page__form"
        @submit.prevent="saveOrderEdit"
      >
        <V2Input v-model="orderEditForm.orderCode" label="采购订单号" disabled required />
        <V2Select
          v-model="orderEditForm.partnerId"
          label="供应商"
          :options="partnerOptions"
          :disabled="busy"
          required
        />
        <V2Input
          v-model="orderEditForm.orderDate"
          label="订单日期"
          placeholder="YYYY-MM-DD"
          required
        />
        <V2Input
          v-model="orderEditForm.deliveryDate"
          label="交付日期"
          placeholder="YYYY-MM-DD"
          required
        />
        <V2Input v-model="orderEditForm.deliveryTerms" label="交付条件" required />
        <V2Input v-model="orderEditForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="busy" @click="orderEditOpen = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="purchase-order-commercial-form" :loading="busy"
          >保存</V2Button
        >
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="dialogOpen"
      :title="editingReceiptId ? `编辑${title}` : `新建${title}`"
      description="保存后刷新数量、金额与状态。"
      :close-disabled="busy"
      :close-on-backdrop="false"
    >
      <form
        id="purchase-execution-editor-form"
        class="purchase-execution-page__form"
        @submit.prevent="save"
      >
        <V2Select
          v-model="form.projectId"
          label="项目"
          :options="workspace.projects"
          :disabled="busy"
          required
        />
        <template v-if="mode === 'request'">
          <V2Select
            v-model="form.contractId"
            label="采购合同"
            :options="contractOptions"
            :disabled="busy"
            required
          />
          <V2Input v-model="form.purpose" label="采购用途" required />
          <V2Select
            v-model="form.materialId"
            label="物料"
            :options="materialOptions"
            :disabled="busy"
            required
            @update:model-value="changeMaterial"
          />
          <V2Input v-model="form.quantity" label="申请数量" required /><V2Input
            v-model="form.estimatedUnitPrice"
            label="预计单价"
            required
          />
          <V2Select
            v-model="form.budgetLineId"
            label="预算科目"
            :options="budgetLineOptions"
            :disabled="busy"
            required
          />
          <V2Input v-model="form.unit" label="单位" /><V2Input
            v-model="form.plannedDate"
            label="计划日期"
            placeholder="YYYY-MM-DD"
            required
          />
        </template>
        <template v-else-if="mode === 'order'">
          <V2Select
            v-model="form.requestId"
            label="采购申请"
            :options="requestOptions"
            allow-empty
            :disabled="busy"
            hint="无来源时后端按例外采购门禁校验"
            @update:model-value="changeRequest"
          />
          <V2Select
            v-if="form.requestId"
            v-model="form.requestItemId"
            label="申请明细"
            :options="requestItemOptions"
            :disabled="busy"
            required
            @update:model-value="changeRequestItem"
          />
          <V2Select
            v-model="form.partnerId"
            label="供应商"
            :options="partnerOptions"
            allow-empty
            :disabled="busy"
          />
          <V2Select
            v-model="form.materialId"
            label="物料"
            :options="materialOptions"
            :disabled="busy"
            required
            @update:model-value="changeMaterial"
          />
          <V2Input v-model="form.quantity" label="订单数量" required />
          <V2Input v-model="form.unitPrice" label="订单单价" required /><V2Input
            v-model="form.taxRate"
            label="税率"
            required
          />
          <V2Input v-model="form.orderDate" label="订单日期" placeholder="YYYY-MM-DD" /><V2Input
            v-model="form.deliveryDate"
            label="交付日期"
            placeholder="YYYY-MM-DD"
          />
          <V2Input
            v-model="form.exceptionPurchaseFlag"
            label="例外采购标记"
            hint="仅无申请来源时使用：0或1"
          /><V2Input v-model="form.exceptionReason" label="例外原因" />
        </template>
        <template v-else>
          <V2Select
            v-model="form.orderId"
            label="采购订单"
            :options="orderOptions"
            :disabled="busy"
            required
            @update:model-value="changeReceiptOrder"
          />
          <V2Select
            v-model="form.orderItemId"
            label="订单明细"
            :options="receiptCandidateOptions"
            required
            :disabled="busy || !form.orderId"
            @update:model-value="changeReceiptItem"
          />
          <V2Select
            v-model="form.warehouseId"
            label="入库仓库"
            :options="warehouseOptions"
            allow-empty
            :disabled="busy"
            :required="form.receiptMode === 'INVENTORY'"
          />
          <V2Input v-model="form.receiptDate" label="验收日期" placeholder="YYYY-MM-DD" />
          <V2Select
            v-model="form.receiptMode"
            label="验收模式"
            :options="[
              { value: 'INVENTORY', label: '入库' },
              { value: 'DIRECT_CONSUMPTION', label: '直耗' },
            ]"
          />
          <V2Input v-model="form.actualQuantity" label="实收数量" required /><V2Input
            v-model="form.qualifiedQuantity"
            label="合格数量"
            required
          />
          <V2Input v-model="form.unqualifiedQuantity" label="不合格数量" required /><V2Input
            v-model="form.batchNo"
            label="批次号"
          />
          <V2Select
            v-if="Number(form.unqualifiedQuantity || 0) > 0"
            v-model="form.dispositionType"
            label="不合格处置"
            :options="[
              { value: 'RETURN', label: '退货' },
              { value: 'REPLACE', label: '换货' },
              { value: 'CONCESSION', label: '让步接收' },
            ]"
            required
          />
          <V2Input
            v-if="Number(form.unqualifiedQuantity || 0) > 0"
            v-model="form.dispositionReason"
            label="处置原因"
            required
          />
        </template>
        <V2Input v-model="form.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="busy" @click="dialogOpen = false">取消</V2Button>
        <V2Button type="submit" form="purchase-execution-editor-form" :loading="busy">
          保存
        </V2Button>
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped>
.purchase-execution-page {
  display: grid;
  gap: var(--v2-space-5);
  min-width: 0;
}
.purchase-execution-page__layout {
  min-width: 0;
}
tr.selected {
  background: var(--v2-color-surface-subtle);
}
.purchase-execution-page__table-wrap {
  max-width: 100%;
  overflow-x: auto;
}
.purchase-execution-page__table-wrap table {
  min-width: 48rem;
}
.purchase-execution-page__facts {
  display: grid;
  gap: var(--v2-space-2);
  margin: 0 0 var(--v2-space-4);
}
.purchase-execution-page__facts div {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--v2-space-3);
}
.purchase-execution-page__facts dt {
  color: var(--v2-color-text-secondary);
}
.purchase-execution-page__facts dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.purchase-execution-page__actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  margin-top: var(--v2-space-4);
}
.purchase-execution-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.purchase-execution-page__form > :last-child,
.purchase-execution-page__form > .purchase-execution-page__actions {
  grid-column: 1 / -1;
}
.purchase-execution-page__attachment {
  display: grid;
  gap: var(--v2-space-2);
}
.purchase-execution-page__attachments {
  display: grid;
  gap: var(--v2-space-1);
  margin: 0;
  padding: 0;
  list-style: none;
}
.purchase-execution-page__attachments li {
  display: flex;
}
.purchase-execution-page__attachment-empty {
  margin: 0;
  color: var(--v2-color-text-secondary);
}
.purchase-execution-page__pagination,
.purchase-execution-page__pagination div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
}
.purchase-execution-page__pagination {
  margin-top: var(--v2-space-3);
  color: var(--v2-color-text-secondary);
}
@media (max-width: 640px) {
  .purchase-execution-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
