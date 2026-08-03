<script setup lang="ts">
import type {
  BudgetLineRecord,
  ContractPage,
  MaterialRecord,
  PartnerRecord,
  PurchaseOrderCommand,
  PurchaseOrderFromRequestCommand,
  PurchaseOrderItemRecord,
  PurchaseOrderRecord,
  PurchaseRequestCommand,
  PurchaseRequestItemRecord,
  PurchaseRequestRecord,
  ReceiptCommand,
  ReceiptItemRecord,
  ReceiptRecord,
  ReceiptSupplierReturnCommand,
  SiteFileRecord,
  WarehouseRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatAmount, formatDecimal } from '@/pages/dashboard/model'
import {
  MaterialSearchPicker,
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
import {
  createPurchaseOrder,
  createPurchaseOrderFromRequest,
  createPurchaseRequest,
  createReceipt,
  confirmReceiptSupplierReturn,
  deletePurchaseOrder,
  deleteReceipt,
  loadPurchaseOrder,
  loadPurchaseOrderItems,
  loadPurchaseOrderPricingSuggestion,
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
  saveReceiptItems,
  submitPurchaseOrder,
  submitPurchaseRequest,
  submitReceipt,
  updatePurchaseOrder,
  updateReceipt,
} from '@/services/supply-chain'
import { loadBudget, loadBudgetPage, loadContractPage, loadPartners } from '@/services/commercial'
import { deleteSiteFile, getSiteFileUrl, listSiteFiles, uploadSiteFile } from '@/services/delivery'
import {
  downloadDocumentGeneration,
  generateDocument,
  loadDocumentGenerationHistory,
  previewDocument,
  type DocumentGenerationRecord,
} from '@/services/system-management'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

type Mode = 'request' | 'order' | 'receipt'
type ListRecord = PurchaseRequestRecord | PurchaseOrderRecord | ReceiptRecord
type DetailItem = PurchaseRequestItemRecord | PurchaseOrderItemRecord | ReceiptItemRecord
type RequestItemDraft = {
  materialId: string
  budgetLineId: string
  quantity: string
  unit: string
  plannedDate: string
  useLocation: string
  remark: string
}
type OrderItemDraft = RequestItemDraft & {
  requestItemId: string
  unitPrice: string
  taxRate: string
  pricingMode: '' | 'FIXED' | 'ACTUAL'
  priceSource: '' | 'CONTRACT_ITEM' | 'RECENT_RECEIPT'
  priceSourceReceiptItemId: string
  priceEditable: boolean
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const records = ref<ListRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const selected = ref<ListRecord | null>(null)
const detailItems = ref<DetailItem[]>([])
const attachments = ref<SiteFileRecord[]>([])
const documentHistory = ref<DocumentGenerationRecord[]>([])
const attachmentToDelete = ref<SiteFileRecord | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const dialogOpen = ref(false)
const orderEditOpen = ref(false)
const supplierReturnOpen = ref(false)
const editingReceiptId = ref('')
const receiptCandidates = ref<ReceiptItemRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const partners = ref<PartnerRecord[]>([])
const contracts = ref<ContractPage['records']>([])
const warehouses = ref<WarehouseRecord[]>([])
const orderCandidates = ref<PurchaseOrderRecord[]>([])
const requestCandidates = ref<PurchaseRequestRecord[]>([])
const budgetLines = ref<BudgetLineRecord[]>([])
const form = reactive<Record<string, string>>({})
const requestItemDrafts = ref<RequestItemDraft[]>([])
const orderItemDrafts = ref<OrderItemDraft[]>([])
type OrderCreateMode = 'FROM_REQUEST' | 'EXCEPTION'
const orderCreateMode = ref<OrderCreateMode>('EXCEPTION')
const orderEditForm = reactive<Record<string, string>>({})
const orderItemEdits = ref<
  Array<{
    source: PurchaseOrderItemRecord
    budgetLineId: string
    unitPrice: string
    taxRate: string
  }>
>([])
const supplierReturnForm = reactive<Record<string, string>>({})
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
const canReturnReceipt = computed(
  () => mode.value === 'receipt' && session.hasAdminOrPermission('receipt:return'),
)
const attachmentBusinessType = computed(
  () =>
    ({ request: 'PURCHASE_REQUEST', order: 'PURCHASE_ORDER', receipt: 'MATERIAL_RECEIPT' })[
      mode.value
    ],
)
const attachmentDocumentType = computed(() =>
  mode.value === 'receipt' ? 'MATERIAL_ACCEPTANCE_FORM' : 'DELIVERY_NOTE',
)
function attachmentScanStatus(documentType: 'DELIVERY_NOTE' | 'MATERIAL_ACCEPTANCE_FORM'): string {
  const file = attachments.value.find((item) => item.documentType === documentType)
  if (!file) return '缺失'
  if (file.virusScanPassed === true) return '扫描通过'
  return file.virusScanStatus || '等待服务端扫描'
}
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const businessAttachments = computed(() =>
  attachments.value.filter(
    (file) =>
      file.documentType !== 'GENERATED_DOCUMENT' && !file.originalName.endsWith('-业务说明.txt'),
  ),
)
const currentDocument = computed(() => documentHistory.value[0] ?? null)
const canDeleteSelectedAttachment = computed(
  () => Boolean(selected.value) && selected.value?.approvalStatus === 'DRAFT' && canSaveItems.value,
)
const attachmentDeleteDescription = computed(() =>
  attachmentToDelete.value ? `确认删除附件“${attachmentToDelete.value.originalName}”？` : '',
)
const returnableReceiptItems = computed(() =>
  (detailItems.value as ReceiptItemRecord[])
    .filter((item) => item.id && item.acceptedQuantity !== '0')
    .map((item) => ({
      value: item.id as string,
      label: `${item.materialName || '物料名称缺失'} · 已验收 ${item.acceptedQuantity}`,
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
const budgetLineOptions = computed(() =>
  budgetLines.value
    .filter((item) => item.id)
    .map((item) => ({
      value: item.id as string,
      label: `${item.costSubjectName || item.costSubjectId} · 可用 ${item.availableAmount ?? item.budgetAmount}`,
    })),
)
const orderOptions = computed(() =>
  orderCandidates.value.map((item) => ({
    value: item.id,
    label: [recordCode(item), item.partnerName].filter(Boolean).join(' · '),
  })),
)
const requestOptions = computed(() =>
  requestCandidates.value.map((item) => ({
    value: item.id,
    label: [recordCode(item), item.projectName, statusLabel(item.approvalStatus)]
      .filter(Boolean)
      .join(' · '),
  })),
)
const sourceRequest = computed(() => {
  if (mode.value !== 'order' || !selected.value || !('requestId' in selected.value)) return null
  const order = selected.value as PurchaseOrderRecord
  if (!order.requestId) return null
  return { id: order.requestId, code: order.requestCode || order.requestId }
})

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

function positiveValue(value: string, label: string): string {
  const normalized = value.trim()
  if (!/^\d+(?:\.\d+)?$/.test(normalized) || /^0+(?:\.0+)?$/.test(normalized)) {
    throw new TypeError(`${label}必须大于0`)
  }
  return normalized
}

function requiredDraft(value: string, label: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError(`${label}不能为空`)
  return normalized
}

function taxRateValue(value: string, label: string): string {
  const normalized = value.trim()
  if (!/^(?:100(?:\.0+)?|\d{1,2}(?:\.\d+)?)$/.test(normalized)) {
    throw new TypeError(`${label}必须在0到100之间`)
  }
  return normalized
}

function requiredSourceId(value: string | null | undefined, label: string): string {
  const normalized = value?.trim() || ''
  if (!normalized) throw new TypeError(`${label}缺失，请刷新后重试`)
  return normalized
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
    PERFORMING: '履约中',
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
    RENDERING: '生成中',
    SUCCEEDED: '已生成',
    FAILED: '生成失败',
  }
  return status ? (labels[status] ?? '未知状态') : '未知状态'
}

function recordBusinessStatus(record: ListRecord): string {
  if ('receiptCode' in record) return statusLabel(record.approvalStatus)
  if ('orderStatus' in record) return statusLabel(record.orderStatus)
  return statusLabel(record.status)
}

function clearDetail(): void {
  detailController?.abort()
  supplierReturnOpen.value = false
  attachmentToDelete.value = null
  selected.value = null
  detailItems.value = []
  documentHistory.value = []
  attachments.value = []
}

function changeSupplierReturnItem(value: string): void {
  const item = (detailItems.value as ReceiptItemRecord[]).find(
    (candidate) => candidate.id === value,
  )
  supplierReturnForm.quantity = item?.acceptedQuantity || ''
  supplierReturnForm.reason = ''
}

function openSupplierReturn(): void {
  const first = returnableReceiptItems.value[0]
  if (!first || !selected.value) return
  for (const key of Object.keys(supplierReturnForm)) delete supplierReturnForm[key]
  supplierReturnForm.receiptItemId = first.value
  supplierReturnForm.returnDate =
    ('receiptDate' in selected.value && selected.value.receiptDate) || ''
  changeSupplierReturnItem(first.value)
  supplierReturnOpen.value = true
}

async function saveSupplierReturn(): Promise<void> {
  if (!selected.value || busy.value) return
  const recordId = selected.value.id
  const command: ReceiptSupplierReturnCommand = {
    receiptItemId: requiredSupplierReturn('receiptItemId', '验收明细'),
    returnKind: 'ACCEPTED',
    quantity: requiredSupplierReturn('quantity', '退货数量'),
    returnDate: requiredSupplierReturn('returnDate', '退货日期'),
    reason: requiredSupplierReturn('reason', '退货原因'),
    idempotencyKey: crypto.randomUUID(),
  }
  busy.value = true
  errorMessage.value = ''
  try {
    await confirmReceiptSupplierReturn(command)
    supplierReturnOpen.value = false
    await loadPage()
    const refreshed = records.value.find((record) => record.id === recordId)
    if (refreshed) await selectRecord(refreshed)
    showToast('success', '退货确认成功', '库存、订单与合同净应付已重新读取')
  } catch (error) {
    errorMessage.value = errorText(error, '供应商退货失败')
    showToast('error', '供应商退货失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function requiredSupplierReturn(key: string, label: string): string {
  const value = supplierReturnForm[key]?.trim()
  if (!value) throw new Error(`${label}不能为空`)
  return value
}

async function uploadAttachment(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selected.value || busy.value) return
  await uploadSelectedAttachment(file)
  input.value = ''
}

async function uploadReceiptAttachment(
  event: Event,
  documentType: 'DELIVERY_NOTE' | 'MATERIAL_ACCEPTANCE_FORM',
): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selected.value || busy.value) return
  await uploadSelectedAttachment(file, documentType)
  input.value = ''
}

async function previewReceiptDocument(): Promise<void> {
  if (!selected.value || mode.value !== 'receipt' || busy.value) return
  busy.value = true
  try {
    const blob = await previewDocument('MATERIAL_RECEIPT', selected.value.id)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${recordCode(selected.value)}-验收单预览.pdf`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    showToast('error', '验收单预览失败', errorText(error, '无法生成验收单预览'))
  } finally {
    busy.value = false
  }
}

async function previewPurchaseOrderDocument(): Promise<void> {
  if (
    !selected.value ||
    mode.value !== 'order' ||
    !['DRAFT', 'REJECTED'].includes(selected.value.approvalStatus || '') ||
    busy.value
  )
    return
  busy.value = true
  try {
    const blob = await previewDocument('PURCHASE_ORDER', selected.value.id)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${recordCode(selected.value)}-草稿水印预览.pdf`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    showToast('error', '订单预览失败', errorText(error, '无法生成订单预览'))
  } finally {
    busy.value = false
  }
}

async function generateReceiptDocument(): Promise<void> {
  if (!selected.value || mode.value !== 'receipt' || busy.value) return
  busy.value = true
  try {
    await generateDocument({
      businessType: 'MATERIAL_RECEIPT',
      businessId: selected.value.id,
      idempotencyKey: crypto.randomUUID(),
    })
    await loadDocumentHistory()
    showToast('success', '验收单生成已提交', '请刷新单据历史查看结果')
  } catch (error) {
    showToast('error', '验收单生成失败', errorText(error, '无法生成验收单'))
  } finally {
    busy.value = false
  }
}

async function uploadSelectedAttachment(
  file: File,
  documentType = attachmentDocumentType.value,
): Promise<void> {
  if (!selected.value || busy.value) return
  const record = selected.value
  const businessType = attachmentBusinessType.value
  busy.value = true
  errorMessage.value = ''
  try {
    await uploadSiteFile(file, businessType, record.id, documentType)
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

function requestAttachmentDelete(file: SiteFileRecord): void {
  if (!canDeleteSelectedAttachment.value || busy.value) return
  attachmentToDelete.value = file
}

async function confirmAttachmentDelete(): Promise<void> {
  const file = attachmentToDelete.value
  const record = selected.value
  if (!file || !record || busy.value) return
  busy.value = true
  try {
    await deleteSiteFile(file.id)
    const next = await listSiteFiles(attachmentBusinessType.value, record.id)
    if (selected.value?.id === record.id) attachments.value = next
    showToast('success', '附件已删除', '附件列表已更新')
  } catch (error) {
    showToast('error', '附件删除失败', errorText(error, '附件删除失败'))
  } finally {
    busy.value = false
    attachmentToDelete.value = null
  }
}

function documentBusinessType(): 'PURCHASE_REQUEST' | 'PURCHASE_ORDER' | 'MATERIAL_RECEIPT' | null {
  return mode.value === 'request'
    ? 'PURCHASE_REQUEST'
    : mode.value === 'order'
      ? 'PURCHASE_ORDER'
      : mode.value === 'receipt'
        ? 'MATERIAL_RECEIPT'
        : null
}

async function loadDocumentHistory(): Promise<void> {
  const businessType = documentBusinessType()
  if (!selected.value || !businessType) return
  documentHistory.value = (
    await loadDocumentGenerationHistory(businessType, selected.value.id)
  ).records
}

async function downloadGeneratedDocument(generation: DocumentGenerationRecord): Promise<void> {
  window.open(await downloadDocumentGeneration(generation.id), '_blank', 'noopener,noreferrer')
}

async function printPurchaseOrderDocument(): Promise<void> {
  const generation = currentDocument.value
  if (!generation || generation.status !== 'SUCCEEDED') {
    showToast('warning', '暂无正式 PDF', '审批通过后由后台自动生成')
    return
  }
  await downloadGeneratedDocument(generation)
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
  if (!active) return
  budgetLines.value = (await loadBudget(active.id)).lines ?? []
}

async function loadApprovedRequestCandidates(project?: string): Promise<void> {
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

async function loadOrderRequestItems(requestId: string): Promise<void> {
  orderItemDrafts.value = []
  if (!requestId) return
  const items = await loadPurchaseRequestItems(requestId)
  orderItemDrafts.value = items.map((item) => ({
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
  if (!orderItemDrafts.value.length) orderItemDrafts.value = [newOrderItemDraft()]
  await refreshOrderPrices()
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
  orderItemEdits.value = (detailItems.value as PurchaseOrderItemRecord[]).map((item) => ({
    source: item,
    budgetLineId: item.budgetLineId || '',
    unitPrice: item.unitPrice || '',
    taxRate: item.taxRate || '0',
  }))
  orderEditOpen.value = true
  busy.value = true
  try {
    const [partnerPage, contractPage] = await Promise.all([
      loadPartners({ pageNo: 1, pageSize: 200, partnerType: 'SUPPLIER', status: 'ENABLE' }),
      loadContractPage({
        pageNo: 1,
        pageSize: 200,
        projectId: order.projectId,
        contractType: 'PURCHASE',
        approvalStatus: 'APPROVED',
      }),
      loadActiveBudgetLines(order.projectId),
    ])
    partners.value = partnerPage.records
    contracts.value = contractPage.records
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
    if (!orderItemEdits.value.length) throw new TypeError('采购订单至少需要一条明细')
    await updatePurchaseOrder(id, {
      projectId: requiredOrderEdit('projectId', '项目'),
      contractId: requiredOrderEdit('contractId', '采购合同'),
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
    await savePurchaseOrderItems(
      id,
      orderItemEdits.value.map(({ source, budgetLineId, unitPrice, taxRate }, index) => ({
        orderId: id,
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
    )
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
  if ('requestCode' in record) return record.contractName || '采购申请'
  if ('receiptCode' in record) return record.orderCode || '采购订单编号缺失'
  if ('orderCode' in record) return record.requestCode || record.partnerName || '例外采购'
  return '-'
}

async function openSourceRequest(): Promise<void> {
  if (!sourceRequest.value || !selected.value) return
  await router.push({
    path: '/inventory/purchase-request',
    query: { projectId: selected.value.projectId, requestId: sourceRequest.value.id },
  })
}

function recordAmount(record: ListRecord): string {
  return 'totalAmount' in record && record.totalAmount != null
    ? formatAmount(record.totalAmount)
    : '暂无金额'
}

function newRequestItemDraft(): RequestItemDraft {
  return {
    materialId: '',
    budgetLineId: '',
    quantity: '1',
    unit: '',
    plannedDate: '',
    useLocation: '',
    remark: '',
  }
}

function newOrderItemDraft(): OrderItemDraft {
  return {
    ...newRequestItemDraft(),
    requestItemId: '',
    unitPrice: '',
    taxRate: '0',
    pricingMode: '',
    priceSource: '',
    priceSourceReceiptItemId: '',
    priceEditable: false,
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

function addRequestItem(): void {
  if (requestItemDrafts.value.length >= 200) return
  requestItemDrafts.value.push(newRequestItemDraft())
}

function addRequestMaterial(material: MaterialRecord): void {
  if (!materials.value.some((item) => item.id === material.id)) materials.value.push(material)
  const emptyIndex = requestItemDrafts.value.findIndex((item) => !item.materialId)
  const item = {
    ...(emptyIndex >= 0 ? requestItemDrafts.value[emptyIndex] : newRequestItemDraft()),
    materialId: material.id,
    unit: material.unit || '',
  }
  if (emptyIndex >= 0) requestItemDrafts.value[emptyIndex] = item
  else if (requestItemDrafts.value.length < 200) requestItemDrafts.value.push(item)
}

function removeRequestItem(index: number): void {
  if (requestItemDrafts.value.length <= 1) return
  requestItemDrafts.value.splice(index, 1)
}

function addOrderItem(): void {
  if (orderItemDrafts.value.length >= 200) return
  orderItemDrafts.value.push(newOrderItemDraft())
}

function removeOrderItem(index: number): void {
  if (orderItemDrafts.value.length <= 1) return
  orderItemDrafts.value.splice(index, 1)
}

function selectRequestMaterial(index: number, value: string): void {
  const item = requestItemDrafts.value[index]
  if (!item) return
  item.materialId = value
  const material = materials.value.find((candidate) => candidate.id === value)
  item.unit = material?.unit || ''
}

async function selectOrderMaterial(index: number, value: string): Promise<void> {
  const item = orderItemDrafts.value[index]
  if (!item) return
  item.materialId = value
  const material = materials.value.find((candidate) => candidate.id === value)
  item.unit = material?.unit || ''
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

async function refreshOrderPrices(): Promise<void> {
  if (!form.contractId) {
    orderItemDrafts.value.forEach((item) => {
      item.unitPrice = ''
      item.pricingMode = ''
      item.priceSource = ''
      item.priceSourceReceiptItemId = ''
      item.priceEditable = false
    })
    return
  }
  await Promise.all(
    orderItemDrafts.value.map((item, index) => selectOrderMaterial(index, item.materialId)),
  )
}

function itemName(item: DetailItem): string {
  return item.materialName || '物料名称缺失'
}

const detailTable = computed(() => {
  if (mode.value === 'request') {
    return {
      columns: ['物料', '规格', '单位', '数量', '使用部位', '计划日期'],
      rows: detailItems.value.map((item, index) => {
        const requestItem = item as PurchaseRequestItemRecord
        return {
          key: requestItem.id || `${index}`,
          cells: [
            itemName(requestItem),
            '-',
            requestItem.unit || '-',
            formatDecimal(requestItem.quantity),
            requestItem.useLocation || '-',
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
            formatDecimal(orderItem.quantity),
            formatAmount(orderItem.unitPrice),
            formatAmount(orderItem.amount),
            formatDecimal(orderItem.receivedQuantity),
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
      '本次合格数量',
      '系统批次号',
      '订单数量',
      '累计收货',
      '剩余数量',
      '单价',
      '金额',
      '使用部位',
    ],
    rows: detailItems.value.map((item, index) => {
      const receiptItem = item as ReceiptItemRecord
      return {
        key: receiptItem.id || `${index}`,
        cells: [
          itemName(receiptItem),
          receiptItem.specification || '-',
          receiptItem.unit || '-',
          formatDecimal(receiptItem.acceptedQuantity),
          receiptItem.systemBatchNo || selected.value?.systemBatchNo || '-',
          formatDecimal(receiptItem.orderedQuantity),
          formatDecimal(receiptItem.receivedQuantity),
          formatDecimal(receiptItem.remainingQuantity),
          formatAmount(receiptItem.unitPrice),
          formatAmount(receiptItem.amount),
          receiptItem.useLocation || '-',
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
  documentHistory.value = []
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
      const requestId =
        mode.value === 'request' && typeof route.query.requestId === 'string'
          ? route.query.requestId
          : ''
      if (requestId) {
        const listed = page.records.find((record) => record.id === requestId)
        if (listed) {
          void selectRecord(listed)
        } else {
          try {
            const source = await loadPurchaseRequest(requestId, controller.signal)
            if (generation === listGeneration) void selectRecord(source)
          } catch {
            // Invalid/stale source link: leave list usable and do not block page load.
          }
        }
      }
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
  documentHistory.value = []
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
    if (generation === detailGeneration) {
      try {
        await loadDocumentHistory()
      } catch (error) {
        showToast('warning', '单据历史读取失败', errorText(error, '无法读取单据生成历史'))
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

async function openCreate(nextOrderMode?: OrderCreateMode): Promise<void> {
  editingReceiptId.value = ''
  orderCreateMode.value = mode.value === 'order' ? (nextOrderMode ?? 'EXCEPTION') : 'EXCEPTION'
  for (const key of Object.keys(form)) delete form[key]
  form.projectId = projectId.value
  receiptCandidates.value = []
  requestCandidates.value = []
  requestItemDrafts.value = [newRequestItemDraft()]
  orderItemDrafts.value = [newOrderItemDraft()]
  if (mode.value === 'receipt')
    Object.assign(form, {
      receiptMode: 'INVENTORY',
      acceptedQuantity: '1',
    })
  dialogOpen.value = true
  busy.value = true
  try {
    const candidateProjectId = form.projectId || undefined
    if (mode.value === 'request') {
      const materialPage = await loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' })
      materials.value = materialPage.records
    } else if (mode.value === 'order') {
      const [partnerPage, materialPage, contractPage, requestPage] = await Promise.all([
        loadPartners({ pageNo: 1, pageSize: 200, partnerType: 'SUPPLIER', status: 'ENABLE' }),
        loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' }),
        loadContractPage({
          pageNo: 1,
          pageSize: 200,
          projectId: candidateProjectId,
          contractType: 'PURCHASE',
          approvalStatus: 'APPROVED',
        }),
        candidateProjectId && orderCreateMode.value === 'FROM_REQUEST'
          ? loadPurchaseRequests({
              pageNum: 1,
              pageSize: 200,
              projectId: candidateProjectId,
              approvalStatus: 'APPROVED',
              status: 'APPROVED',
            })
          : Promise.resolve({ records: [] as PurchaseRequestRecord[] }),
      ])
      partners.value = partnerPage.records
      materials.value = materialPage.records
      contracts.value = contractPage.records
      requestCandidates.value = requestPage.records.filter((item) => item.status !== 'CONVERTED')
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
    deliveryNoteNo: receipt.deliveryNoteNo || '',
    warehouseId: receipt.warehouseId || '',
    receiverId: receipt.receiverId || '',
    receiptMode: receipt.receiptMode || 'INVENTORY',
    remark: receipt.remark || '',
  })
  await changeReceiptOrder(form.orderId)
  Object.assign(form, {
    orderItemId: item.orderItemId || '',
    materialId: item.materialId || '',
    wbsTaskId: item.wbsTaskId || '',
    budgetLineId: item.budgetLineId || '',
    acceptedQuantity: item.acceptedQuantity,
    useLocation: item.useLocation || '',
  })
  selected.value = null
}

async function changeEditorProject(value: string): Promise<void> {
  form.projectId = value
  form.contractId = ''
  form.requestId = ''
  form.budgetLineId = ''
  budgetLines.value = []
  requestCandidates.value = []
  if (mode.value === 'order') orderItemDrafts.value = [newOrderItemDraft()]
  if (!value || busy.value) return
  busy.value = true
  try {
    if (mode.value === 'receipt') {
      const [orderPage, warehousePage] = await Promise.all([
        loadPurchaseOrders({
          pageNum: 1,
          pageSize: 200,
          projectId: value,
          approvalStatus: 'APPROVED',
        }),
        loadWarehouses({ pageNo: 1, pageSize: 200, projectId: value, status: 'ENABLE' }),
      ])
      orderCandidates.value = orderPage.records
      warehouses.value = warehousePage.records
    } else if (mode.value === 'order') {
      await loadActiveBudgetLines(value)
    }
    if (mode.value === 'order') {
      contracts.value = (
        await loadContractPage({
          pageNo: 1,
          pageSize: 200,
          projectId: value,
          contractType: 'PURCHASE',
          approvalStatus: 'APPROVED',
        })
      ).records
      if (orderCreateMode.value === 'FROM_REQUEST') await loadApprovedRequestCandidates(value)
    }
  } catch (error) {
    contracts.value = []
    errorMessage.value = errorText(error, '采购合同候选读取失败')
    showToast('error', '采购合同候选读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function changeOrderContract(value: string): Promise<void> {
  form.contractId = value
  await refreshOrderPrices()
}

async function changeOrderRequest(value: string): Promise<void> {
  form.requestId = value
  if (!value) {
    orderItemDrafts.value = [newOrderItemDraft()]
    await refreshOrderPrices()
    return
  }
  busy.value = true
  try {
    await loadOrderRequestItems(value)
  } catch (error) {
    orderItemDrafts.value = [newOrderItemDraft()]
    showToast('error', '采购申请明细读取失败', errorText(error, '无法读取已审批申请明细'))
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
        header: {
          projectId: required('projectId', '项目'),
          remark: optional('remark'),
        },
        items: requestItemDrafts.value.map((item, index) => ({
          materialId: requiredDraft(item.materialId, `第${index + 1}条物料`),
          quantity: positiveValue(item.quantity, `第${index + 1}条申请数量`),
          unit: item.unit.trim() || undefined,
          plannedDate: requiredDraft(item.plannedDate, `第${index + 1}条计划日期`),
          useLocation: requiredDraft(item.useLocation, `第${index + 1}条使用部位`),
          remark: item.remark.trim() || undefined,
        })),
      }
      id = await createPurchaseRequest(command)
    } else if (mode.value === 'order') {
      if (orderCreateMode.value === 'FROM_REQUEST') {
        const command: PurchaseOrderFromRequestCommand = {
          projectId: required('projectId', '项目'),
          contractId: required('contractId', '采购合同'),
          requestId: required('requestId', '已审批采购申请'),
          orderDate: optional('orderDate'),
          deliveryDate: optional('deliveryDate'),
          deliveryTerms: required('deliveryTerms', '交付条件'),
          remark: optional('remark'),
        }
        // 服务端按申请审批快照复制明细并定价；前端不提交金额、数量或订单明细事实。
        id = await createPurchaseOrderFromRequest(command)
      } else {
        const command: PurchaseOrderCommand = {
          projectId: required('projectId', '项目'),
          contractId: required('contractId', '采购合同'),
          partnerId: optional('partnerId'),
          orderType: optional('orderType'),
          orderDate: optional('orderDate'),
          deliveryDate: optional('deliveryDate'),
          deliveryTerms: optional('deliveryTerms'),
          exceptionPurchaseFlag: 1,
          exceptionReason: required('exceptionReason', '例外原因'),
          remark: optional('remark'),
        }
        id = await createPurchaseOrder(command)
        createdId = id
        if (canSaveItems.value) {
          await savePurchaseOrderItems(
            id,
            orderItemDrafts.value.map((item, index) => ({
              orderId: id,
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
          )
        }
      }
    } else {
      const command: ReceiptCommand = {
        projectId: required('projectId', '项目'),
        orderId: required('orderId', '采购订单'),
        contractId: optional('contractId'),
        partnerId: optional('partnerId'),
        receiptDate: optional('receiptDate'),
        deliveryNoteNo: optional('deliveryNoteNo'),
        warehouseId:
          form.receiptMode === 'INVENTORY'
            ? required('warehouseId', '入库仓库')
            : optional('warehouseId'),
        receiverId: optional('receiverId'),
        receiptMode: (form.receiptMode || 'INVENTORY') as ReceiptCommand['receiptMode'],
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
            acceptedQuantity: decimal('acceptedQuantity', '验收数量'),
            useLocation: optional('useLocation'),
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
        if (mode.value === 'order') await deletePurchaseOrder(createdId)
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
  [mode, projectId, () => route.query.requestId],
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
        <V2Button
          v-if="mode === 'order' && canAdd"
          size="small"
          @click="openCreate('FROM_REQUEST')"
        >
          新建采购订单
        </V2Button>
        <V2Button
          v-if="mode === 'order' && canAdd"
          variant="secondary"
          size="small"
          @click="openCreate('EXCEPTION')"
        >
          新建例外采购订单
        </V2Button>
        <V2Button v-if="mode !== 'order' && canAdd" size="small" @click="openCreate">
          新建{{ title }}
        </V2Button>
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
      <template v-if="canAdd" #actions>
        <V2Button v-if="mode === 'order'" @click="openCreate('FROM_REQUEST')">
          新建采购订单
        </V2Button>
        <V2Button v-if="mode === 'order'" variant="secondary" @click="openCreate('EXCEPTION')">
          新建例外采购订单
        </V2Button>
        <V2Button v-if="mode !== 'order'" @click="openCreate"> 新建{{ title }} </V2Button>
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
        <template #footer>
          <nav class="purchase-execution-page__pagination" aria-label="采购执行分页">
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

      <V2Dialog
        :open="Boolean(selected) && !orderEditOpen && !supplierReturnOpen"
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
              <div v-if="sourceRequest">
                <dt>来源采购申请</dt>
                <dd>
                  <V2Button
                    type="button"
                    variant="ghost"
                    size="small"
                    class="v2-table__record-link"
                    @click="openSourceRequest"
                  >
                    {{ sourceRequest.code }} · 查看采购申请
                  </V2Button>
                </dd>
              </div>
              <div>
                <dt>审批状态</dt>
                <dd>{{ statusLabel(selected.approvalStatus) }}</dd>
              </div>
              <div>
                <dt>业务状态</dt>
                <dd>{{ recordBusinessStatus(selected) }}</dd>
              </div>
              <div v-if="mode !== 'request'">
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
            <section
              v-if="
                businessAttachments.length ||
                mode === 'receipt' ||
                selected.approvalStatus === 'DRAFT'
              "
              class="v2-detail-dialog__section"
              aria-labelledby="purchase-attachment-title"
            >
              <div class="v2-detail-dialog__section-heading">
                <h3 id="purchase-attachment-title">业务附件</h3>
                <V2Badge tone="info">{{ businessAttachments.length }} 个</V2Badge>
              </div>
              <ul v-if="businessAttachments.length" class="purchase-execution-page__attachments">
                <li v-for="file in businessAttachments" :key="file.id">
                  <V2Button
                    type="button"
                    size="small"
                    variant="ghost"
                    @click="downloadAttachment(file)"
                  >
                    {{ file.originalName }}
                  </V2Button>
                  <V2Button
                    v-if="canDeleteSelectedAttachment"
                    type="button"
                    size="small"
                    variant="ghost"
                    @click="requestAttachmentDelete(file)"
                  >
                    删除
                  </V2Button>
                </li>
              </ul>
              <p v-else class="purchase-execution-page__attachment-empty">暂无附件</p>
              <template v-if="mode === 'receipt'">
                <p>送货单：{{ attachmentScanStatus('DELIVERY_NOTE') }}</p>
                <p>验收单：{{ attachmentScanStatus('MATERIAL_ACCEPTANCE_FORM') }}</p>
                <label class="purchase-execution-page__attachment">
                  <span>上传送货单</span>
                  <input
                    type="file"
                    :disabled="busy"
                    @change="uploadReceiptAttachment($event, 'DELIVERY_NOTE')"
                  />
                </label>
                <label class="purchase-execution-page__attachment">
                  <span>上传材料验收单</span>
                  <input
                    type="file"
                    :disabled="busy"
                    @change="uploadReceiptAttachment($event, 'MATERIAL_ACCEPTANCE_FORM')"
                  />
                </label>
              </template>
              <template v-if="selected.approvalStatus === 'DRAFT' && mode !== 'receipt'">
                <label class="purchase-execution-page__attachment">
                  <span>选择已签认的业务附件</span>
                  <input
                    type="file"
                    :disabled="busy"
                    accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.txt"
                    @change="uploadAttachment"
                  />
                </label>
              </template>
            </section>
            <section
              v-if="currentDocument"
              class="v2-detail-dialog__section purchase-execution-page__current-document"
              aria-labelledby="purchase-current-document-title"
            >
              <div class="v2-detail-dialog__section-heading">
                <h3 id="purchase-current-document-title">正式单据</h3>
                <V2Badge :tone="currentDocument.status === 'SUCCEEDED' ? 'success' : 'warning'">
                  {{ statusLabel(currentDocument.status) }}
                </V2Badge>
              </div>
              <p>{{ recordCode(selected) }}.pdf</p>
              <V2Button
                v-if="currentDocument.status === 'SUCCEEDED'"
                type="button"
                size="small"
                variant="ghost"
                @click="downloadGeneratedDocument(currentDocument)"
              >
                下载
              </V2Button>
              <p v-else>审批完成后由后台自动生成，当前状态无需人工操作。</p>
            </section>
          </template></template
        >
        <template #footer>
          <V2Button type="button" variant="secondary" :disabled="busy" @click="clearDetail"
            >关闭</V2Button
          >
          <V2Button
            v-if="
              mode === 'receipt' &&
              selected &&
              ['DRAFT', 'REJECTED'].includes(selected.approvalStatus || '')
            "
            type="button"
            variant="secondary"
            :disabled="busy"
            @click="previewReceiptDocument"
            >预览验收单</V2Button
          >
          <V2Button
            v-if="mode === 'receipt' && selected?.approvalStatus === 'APPROVED'"
            type="button"
            variant="secondary"
            :disabled="busy"
            @click="generateReceiptDocument"
            >生成验收单</V2Button
          >
          <V2Button
            v-if="
              mode === 'order' &&
              selected &&
              ['DRAFT', 'REJECTED'].includes(selected.approvalStatus || '')
            "
            type="button"
            variant="secondary"
            :disabled="busy"
            @click="previewPurchaseOrderDocument"
            >预览草稿水印订单</V2Button
          >
          <V2Button
            v-if="mode === 'order' && selected?.approvalStatus === 'APPROVED'"
            type="button"
            variant="ghost"
            :disabled="busy"
            @click="printPurchaseOrderDocument"
            >打印正式 PDF</V2Button
          >
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
          <V2Button
            v-if="canReturnReceipt && returnableReceiptItems.length"
            type="button"
            variant="secondary"
            :disabled="busy"
            @click="openSupplierReturn"
            >登记供应商退货</V2Button
          >
          <V2Button v-if="canSubmitSelected" type="button" :loading="busy" @click="submitSelected"
            >提交审批</V2Button
          >
        </template>
      </V2Dialog>

      <V2ConfirmDialog
        :open="Boolean(attachmentToDelete)"
        title="删除附件"
        :description="attachmentDeleteDescription"
        confirm-text="确认删除"
        danger
        :loading="busy"
        @close="attachmentToDelete = null"
        @confirm="confirmAttachmentDelete"
      />
    </section>

    <V2Dialog
      v-model:open="supplierReturnOpen"
      title="登记供应商退货"
      description="确认后由服务端冲销库存、订单收货量与合同净应付。"
      :close-disabled="busy"
      :close-on-backdrop="false"
    >
      <form
        id="receipt-supplier-return-form"
        class="purchase-execution-page__form"
        @submit.prevent="saveSupplierReturn"
      >
        <V2Select
          v-model="supplierReturnForm.receiptItemId"
          label="验收明细"
          :options="returnableReceiptItems"
          :disabled="busy"
          required
          @update:model-value="changeSupplierReturnItem"
        />
        <V2Input
          v-model="supplierReturnForm.quantity"
          label="退货数量"
          :decimal-scale="2"
          required
        />
        <V2Input v-model="supplierReturnForm.returnDate" type="date" label="退货日期" required />
        <V2Input v-model="supplierReturnForm.reason" label="退货原因" required />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="busy" @click="supplierReturnOpen = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="receipt-supplier-return-form" :loading="busy"
          >确认退货</V2Button
        >
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="orderEditOpen"
      title="编辑采购订单商业条件"
      description="已审批采购订单商业条件按服务端事实维护；来源明细不可改。"
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
          v-model="orderEditForm.contractId"
          label="采购合同"
          :options="contractOptions"
          :disabled="busy"
          required
        />
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
        <section aria-labelledby="purchase-order-item-price-title">
          <h3 id="purchase-order-item-price-title">明细价格与税率</h3>
          <div v-for="(item, index) in orderItemEdits" :key="item.source.id || index">
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
      :title="
        editingReceiptId
          ? `编辑${title}`
          : mode === 'order'
            ? orderCreateMode === 'FROM_REQUEST'
              ? '新建采购订单'
              : '新建例外采购订单'
            : `新建${title}`
      "
      :description="
        mode === 'order'
          ? orderCreateMode === 'FROM_REQUEST'
            ? '选择已审批采购申请与采购合同后手工建单；明细、数量和价格由服务端审批快照与合同事实决定。'
            : '例外采购须填写业务依据；仅用于有业务依据的例外采购。'
          : '填写基本信息与明细后一次提交，保存后刷新数量、金额与状态。'
      "
      :close-disabled="busy"
      :close-on-backdrop="false"
      :panel-class="mode === 'receipt' ? undefined : 'v2-dialog-wide'"
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
          @update:model-value="changeEditorProject"
        />
        <template v-if="mode === 'request'">
          <section
            class="purchase-execution-page__draft-lines"
            aria-labelledby="purchase-request-lines-title"
          >
            <div class="purchase-execution-page__draft-heading">
              <h3 id="purchase-request-lines-title">采购申请明细</h3>
              <div class="purchase-execution-page__draft-actions">
                <MaterialSearchPicker
                  :disabled="busy || requestItemDrafts.length >= 200"
                  @select="addRequestMaterial"
                />
                <V2Button
                  type="button"
                  size="small"
                  variant="secondary"
                  :disabled="busy || requestItemDrafts.length >= 200"
                  @click="addRequestItem"
                >
                  添加明细
                </V2Button>
              </div>
            </div>
            <div class="purchase-execution-page__draft-table-wrap">
              <table class="purchase-execution-page__draft-table">
                <thead>
                  <tr>
                    <th scope="col">物料编码/名称<span aria-hidden="true">*</span></th>
                    <th scope="col">申请数量<span aria-hidden="true">*</span></th>
                    <th scope="col">单位<span aria-hidden="true">*</span></th>
                    <th scope="col">计划日期<span aria-hidden="true">*</span></th>
                    <th scope="col">使用部位<span aria-hidden="true">*</span></th>
                    <th scope="col">备注</th>
                    <th scope="col">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(item, index) in requestItemDrafts" :key="index">
                    <td>
                      <V2Select
                        v-model="item.materialId"
                        :label="`第${index + 1}条物料`"
                        :hide-label="true"
                        :options="materialOptions"
                        :disabled="busy"
                        required
                        @update:model-value="selectRequestMaterial(index, $event)"
                      />
                    </td>
                    <td>
                      <V2Input
                        v-model="item.quantity"
                        :label="`第${index + 1}条申请数量`"
                        :hide-label="true"
                        :decimal-scale="2"
                        required
                      />
                    </td>
                    <td>
                      <V2Input
                        v-model="item.unit"
                        :label="`第${index + 1}条单位`"
                        :hide-label="true"
                        required
                      />
                    </td>
                    <td>
                      <V2Input
                        v-model="item.plannedDate"
                        :label="`第${index + 1}条计划日期`"
                        :hide-label="true"
                        placeholder="YYYY-MM-DD"
                        required
                      />
                    </td>
                    <td>
                      <V2Input
                        v-model="item.useLocation"
                        :label="`第${index + 1}条使用部位`"
                        :hide-label="true"
                        required
                      />
                    </td>
                    <td>
                      <V2Input
                        v-model="item.remark"
                        :label="`第${index + 1}条备注`"
                        :hide-label="true"
                      />
                    </td>
                    <td>
                      <V2Button
                        type="button"
                        size="small"
                        variant="ghost"
                        :disabled="busy || requestItemDrafts.length <= 1"
                        :aria-label="`删除第${index + 1}条明细`"
                        @click="removeRequestItem(index)"
                      >
                        删除
                      </V2Button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>
        <template v-else-if="mode === 'order'">
          <template v-if="orderCreateMode === 'FROM_REQUEST'">
            <V2Select
              v-model="form.requestId"
              label="已审批采购申请"
              :options="requestOptions"
              :disabled="busy"
              required
              @update:model-value="changeOrderRequest"
            />
            <p v-if="!requestCandidates.length" class="purchase-execution-page__form-hint">
              当前项目暂无同时满足审批状态与业务状态为“已通过”的采购申请。
            </p>
          </template>
          <V2Select
            v-model="form.contractId"
            label="采购合同"
            :options="contractOptions"
            :disabled="busy"
            required
            @update:model-value="changeOrderContract"
          />
          <V2Select
            v-if="orderCreateMode === 'EXCEPTION'"
            v-model="form.partnerId"
            label="供应商"
            :options="partnerOptions"
            allow-empty
            :disabled="busy"
          />
          <V2Input v-model="form.orderDate" label="订单日期" placeholder="YYYY-MM-DD" />
          <V2Input v-model="form.deliveryDate" label="交付日期" placeholder="YYYY-MM-DD" />
          <V2Input v-model="form.deliveryTerms" label="交付条件" required />
          <V2Input
            v-if="orderCreateMode === 'EXCEPTION'"
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
                v-if="orderCreateMode === 'EXCEPTION'"
                type="button"
                size="small"
                variant="secondary"
                :disabled="busy || orderItemDrafts.length >= 200"
                @click="addOrderItem"
              >
                添加明细
              </V2Button>
              <span v-else class="purchase-execution-page__form-hint">
                明细来自已审批采购申请，只读展示；保存时由服务端重新读取审批快照。
              </span>
            </div>
            <div class="purchase-execution-page__draft-table-wrap">
              <table class="purchase-execution-page__draft-table">
                <thead>
                  <tr>
                    <th scope="col">物料编码/名称<span aria-hidden="true">*</span></th>
                    <th scope="col">预算科目<span aria-hidden="true">*</span></th>
                    <th scope="col">订单数量<span aria-hidden="true">*</span></th>
                    <th scope="col">单位<span aria-hidden="true">*</span></th>
                    <th scope="col">服务端建议单价</th>
                    <th scope="col">税率</th>
                    <th scope="col">计价来源</th>
                    <th v-if="orderCreateMode === 'EXCEPTION'" scope="col">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(item, index) in orderItemDrafts" :key="item.requestItemId || index">
                    <td>
                      <span v-if="orderCreateMode === 'FROM_REQUEST'">{{
                        materialLabel(item.materialId)
                      }}</span>
                      <V2Select
                        v-else
                        v-model="item.materialId"
                        :label="`第${index + 1}条物料`"
                        :hide-label="true"
                        :options="materialOptions"
                        :disabled="busy"
                        required
                        @update:model-value="selectOrderMaterial(index, $event)"
                      />
                    </td>
                    <td>
                      <span v-if="orderCreateMode === 'FROM_REQUEST'">{{
                        budgetLineLabel(item.budgetLineId)
                      }}</span>
                      <V2Select
                        v-else
                        v-model="item.budgetLineId"
                        :label="`第${index + 1}条预算科目`"
                        :hide-label="true"
                        :options="budgetLineOptions"
                        :disabled="busy || !form.projectId"
                        required
                      />
                    </td>
                    <td>
                      <span v-if="orderCreateMode === 'FROM_REQUEST'">{{
                        formatDecimal(item.quantity)
                      }}</span>
                      <V2Input
                        v-else
                        v-model="item.quantity"
                        :label="`第${index + 1}条订单数量`"
                        :hide-label="true"
                        :decimal-scale="2"
                        required
                      />
                    </td>
                    <td>
                      <span v-if="orderCreateMode === 'FROM_REQUEST'">{{ item.unit || '-' }}</span>
                      <V2Input
                        v-else
                        v-model="item.unit"
                        :label="`第${index + 1}条单位`"
                        :hide-label="true"
                        required
                      />
                    </td>
                    <td>
                      <span v-if="orderCreateMode === 'FROM_REQUEST'">{{
                        formatAmount(item.unitPrice)
                      }}</span>
                      <V2Input
                        v-else
                        v-model="item.unitPrice"
                        :label="`第${index + 1}条服务端建议单价`"
                        :hide-label="true"
                        :decimal-scale="2"
                        :disabled="!item.priceEditable"
                        required
                      />
                    </td>
                    <td>
                      <span v-if="orderCreateMode === 'FROM_REQUEST'">由服务端合同事实决定</span>
                      <V2Input
                        v-else
                        v-model="item.taxRate"
                        :label="`第${index + 1}条税率`"
                        :hide-label="true"
                        :decimal-scale="2"
                        required
                      />
                    </td>
                    <td>
                      <span v-if="item.pricingMode">
                        {{ item.pricingMode === 'FIXED' ? '合同固定价' : '实际结算价' }} ·
                        {{ item.priceSource || '服务端' }}
                      </span>
                      <span v-else>-</span>
                    </td>
                    <td v-if="orderCreateMode === 'EXCEPTION'">
                      <V2Button
                        type="button"
                        size="small"
                        variant="ghost"
                        :disabled="busy || orderItemDrafts.length <= 1"
                        :aria-label="`删除第${index + 1}条明细`"
                        @click="removeOrderItem(index)"
                      >
                        删除
                      </V2Button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
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
          <section
            class="purchase-execution-page__draft-lines purchase-execution-page__receipt-lines"
            aria-labelledby="purchase-receipt-lines-title"
          >
            <div class="purchase-execution-page__draft-heading">
              <h3 id="purchase-receipt-lines-title">订单明细</h3>
            </div>
            <div class="purchase-execution-page__draft-table-wrap">
              <table
                class="purchase-execution-page__draft-table purchase-execution-page__receipt-table"
              >
                <thead>
                  <tr>
                    <th scope="col">选择<span aria-hidden="true">*</span></th>
                    <th scope="col">物料</th>
                    <th scope="col">规格</th>
                    <th scope="col">单位</th>
                    <th scope="col">订单数量</th>
                    <th scope="col">累计收货</th>
                    <th scope="col">剩余数量</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="!form.orderId">
                    <td colspan="7">请先选择采购订单</td>
                  </tr>
                  <tr v-else-if="!receiptCandidates.length">
                    <td colspan="7">暂无可验收明细</td>
                  </tr>
                  <template v-else>
                    <tr v-for="item in receiptCandidates" :key="item.orderItemId">
                      <td>
                        <input
                          v-model="form.orderItemId"
                          type="radio"
                          name="purchase-receipt-order-item"
                          :value="item.orderItemId || ''"
                          :disabled="busy"
                          :aria-label="`选择${item.materialName || '物料名称缺失'}`"
                          @change="changeReceiptItem(item.orderItemId || '')"
                        />
                      </td>
                      <td>{{ item.materialName || '-' }}</td>
                      <td>{{ item.specification || '-' }}</td>
                      <td>{{ item.unit || '-' }}</td>
                      <td>{{ formatDecimal(item.orderedQuantity) }}</td>
                      <td>{{ formatDecimal(item.receivedQuantity) }}</td>
                      <td>{{ formatDecimal(item.remainingQuantity) }}</td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </div>
          </section>
          <V2Select
            v-model="form.warehouseId"
            label="入库仓库"
            :options="warehouseOptions"
            allow-empty
            :disabled="busy"
            :required="form.receiptMode === 'INVENTORY'"
          />
          <V2Input v-model="form.receiptDate" label="验收日期" placeholder="YYYY-MM-DD" />
          <V2Input v-model="form.deliveryNoteNo" label="送货单号" />
          <V2Select
            v-model="form.receiptMode"
            label="验收模式"
            :options="[
              { value: 'INVENTORY', label: '入库' },
              { value: 'DIRECT_CONSUMPTION', label: '直耗' },
            ]"
          />
          <V2Input
            v-model="form.acceptedQuantity"
            label="本次合格数量"
            :decimal-scale="2"
            required
          />
          <V2Input
            v-if="form.receiptMode === 'DIRECT_CONSUMPTION'"
            v-model="form.useLocation"
            label="使用部位"
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
.purchase-execution-page__draft-lines {
  display: grid;
  grid-column: 1 / -1;
  gap: var(--v2-space-3);
}
.purchase-execution-page__draft-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
}
.purchase-execution-page__draft-heading h3 {
  margin: 0;
}
.purchase-execution-page__draft-actions {
  display: flex;
  flex: 1 1 30rem;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--v2-space-2);
}
.purchase-execution-page__draft-table-wrap {
  max-width: 100%;
  overflow-x: auto;
  -ms-overflow-style: auto;
  scrollbar-width: auto;
  border: 1px solid var(--v2-color-border-subtle);
  border-radius: var(--v2-radius-md);
}
.purchase-execution-page__draft-table-wrap::-webkit-scrollbar {
  display: block;
  width: 0.5rem;
  height: 0.5rem;
}
.purchase-execution-page__draft-table {
  width: 100%;
  min-width: 68rem;
  border-collapse: collapse;
}
.purchase-execution-page__receipt-table {
  min-width: 100%;
  table-layout: fixed;
}
.purchase-execution-page__receipt-table th:first-child,
.purchase-execution-page__receipt-table td:first-child {
  width: 4rem;
  text-align: center;
}
.purchase-execution-page__receipt-table td:not(:first-child) {
  overflow-wrap: anywhere;
}
.purchase-execution-page__draft-table th,
.purchase-execution-page__draft-table td {
  padding: var(--v2-space-2);
  border-bottom: 1px solid var(--v2-color-border-subtle);
  text-align: left;
  vertical-align: top;
}
.purchase-execution-page__draft-table th {
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-sm);
  font-weight: 600;
  white-space: nowrap;
  background: color-mix(in srgb, var(--v2-color-primary-soft) 52%, transparent);
}
.purchase-execution-page__draft-table th span {
  color: var(--v2-color-danger);
}
.purchase-execution-page__draft-table tr:last-child td {
  border-bottom: 0;
}
.purchase-execution-page__draft-table .v2-field {
  min-width: 8rem;
}
.purchase-execution-page__draft-table td:last-child {
  white-space: nowrap;
}
.purchase-execution-page__draft-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border-subtle);
  border-radius: var(--v2-radius-md);
}
.purchase-execution-page__draft-row > p,
.purchase-execution-page__draft-row > button:last-child {
  grid-column: 1 / -1;
}
.purchase-execution-page__current-document {
  display: grid;
  gap: var(--v2-space-2);
}
.purchase-execution-page__current-document p {
  margin: 0;
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
.purchase-execution-page__pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--v2-space-2);
  color: var(--v2-color-text-secondary);
}
@media (max-width: 640px) {
  .purchase-execution-page__form,
  .purchase-execution-page__draft-row {
    grid-template-columns: 1fr;
  }
}
</style>
