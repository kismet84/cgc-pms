<script setup lang="ts">
import type {
  PurchaseOrderRecord,
  ReceiptCommand,
  ReceiptItemRecord,
  ReceiptRecord,
  ReceiptSupplierReturnCommand,
  WarehouseRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
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
import { formatDecimal } from '@/shared/display'
import {
  confirmReceiptSupplierReturn,
  createReceipt,
  deleteReceipt,
  loadOrderItemsForReceipt,
  loadPurchaseOrder,
  loadPurchaseOrders,
  loadReceipt,
  loadReceiptItems,
  loadReceipts,
  loadWarehouses,
  saveReceiptItems,
  submitReceipt,
  updateReceipt,
} from '@/services/supply-chain'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import PurchaseExecutionDetail from './PurchaseExecutionDetail.vue'
import {
  decimal,
  errorText,
  optional,
  receiptCode,
  receiptDetailTable,
  recordAmount,
  required,
  statusLabel,
} from './model'
import {
  NewMaterialReceiptSaveError,
  saveMaterialReceipt,
  submitSavedMaterialReceipt,
} from './application/save-material-receipt'

const session = useSessionStore()
const workspace = useWorkspaceStore()
const application = {
  create: createReceipt,
  update: updateReceipt,
  saveItems: saveReceiptItems,
  deleteDraft: deleteReceipt,
  submit: submitReceipt,
}

const records = ref<ReceiptRecord[]>([])
const selected = ref<ReceiptRecord | null>(null)
const detailItems = ref<ReceiptItemRecord[]>([])
const orderCandidates = ref<PurchaseOrderRecord[]>([])
const receiptCandidates = ref<ReceiptItemRecord[]>([])
const warehouses = ref<WarehouseRecord[]>([])
const form = reactive<Record<string, string>>({})
const returnForm = reactive<Record<string, string>>({})
const editingId = ref('')
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)
const detailLoading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const editorOpen = ref(false)
const returnOpen = ref(false)

let listController: AbortController | null = null
let detailController: AbortController | null = null
let listGeneration = 0
let detailGeneration = 0

const projectId = computed(() => workspace.selectedProjectId || '')
const canAdd = computed(
  () =>
    session.hasAdminOrPermission('receipt:add') &&
    session.hasAdminOrPermission('receipt:edit') &&
    session.hasAdminOrPermission('receipt:delete'),
)
const canSaveItems = computed(() => session.hasAdminOrPermission('receipt:edit'))
const canEditSelected = computed(
  () => canSaveItems.value && selected.value?.approvalStatus === 'DRAFT',
)
const canSubmitSelected = computed(
  () =>
    session.hasAdminOrPermission('receipt:submit') && selected.value?.approvalStatus === 'DRAFT',
)
const canReturn = computed(() => session.hasAdminOrPermission('receipt:return'))
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const detailTable = computed(() => receiptDetailTable(detailItems.value, selected.value))
const orderOptions = computed(() =>
  orderCandidates.value
    .filter(
      (item) =>
        (item.approvalStatus === 'APPROVED' &&
          ['PERFORMING', 'PARTIAL_RECEIVED'].includes(item.orderStatus || '')) ||
        (Boolean(editingId.value) && item.id === form.orderId),
    )
    .map((item) => ({
      value: item.id,
      label: [item.orderCode, item.partnerName].filter(Boolean).join(' · '),
    })),
)
const warehouseOptions = computed(() => {
  const options = [
    { value: '', label: '不入库' },
    ...warehouses.value.map((item) => ({
      value: item.id,
      label: [item.warehouseCode, item.warehouseName].filter(Boolean).join(' · '),
    })),
  ]
  if (form.warehouseId && !options.some((item) => item.value === form.warehouseId)) {
    options.push({ value: form.warehouseId, label: '历史仓库' })
  }
  return options
})
const returnableItems = computed(() =>
  detailItems.value
    .filter((item) => item.id && item.acceptedQuantity !== '0')
    .map((item) => ({
      value: item.id as string,
      label: `${item.materialName || '物料名称缺失'} · 已验收 ${item.acceptedQuantity}`,
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
    const page = await loadReceipts(
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
      errorMessage.value = errorText(error, '材料验收加载失败')
      showToast('error', '材料验收读取失败', errorMessage.value)
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function selectRecord(record: ReceiptRecord): Promise<void> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  selected.value = record
  detailItems.value = []
  detailLoading.value = true
  try {
    const [detail, items] = await Promise.all([
      loadReceipt(record.id, controller.signal),
      loadReceiptItems(record.id, controller.signal),
    ])
    if (generation !== detailGeneration) return
    selected.value = detail
    detailItems.value = items
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      showToast('error', '详情读取失败', errorText(error, '材料验收详情加载失败'))
    }
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

function clearDetail(): void {
  detailController?.abort()
  selected.value = null
  detailItems.value = []
  returnOpen.value = false
}

async function loadCandidates(project?: string): Promise<void> {
  const [orderPage, warehousePage] = await Promise.all([
    loadPurchaseOrders({ pageNum: 1, pageSize: 200, projectId: project }),
    loadWarehouses({ pageNo: 1, pageSize: 200, projectId: project, status: 'ENABLE' }),
  ])
  orderCandidates.value = orderPage.records
  warehouses.value = warehousePage.records
}

async function openCreate(): Promise<void> {
  editingId.value = ''
  for (const key of Object.keys(form)) delete form[key]
  Object.assign(form, {
    projectId: projectId.value,
    receiptMode: 'INVENTORY',
    acceptedQuantity: '1',
  })
  receiptCandidates.value = []
  editorOpen.value = true
  busy.value = true
  try {
    await loadCandidates(form.projectId || undefined)
  } catch (error) {
    errorMessage.value = errorText(error, '材料验收候选读取失败')
    showToast('error', '业务候选读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function openEdit(): Promise<void> {
  if (!selected.value || !canEditSelected.value || !detailItems.value.length) return
  const receipt = selected.value as ReceiptRecord & {
    receiverId?: string | null
    receiptMode?: ReceiptCommand['receiptMode'] | null
  }
  const item = detailItems.value[0]!
  await openCreate()
  if (receipt.orderId && !orderCandidates.value.some((order) => order.id === receipt.orderId)) {
    try {
      orderCandidates.value.push(await loadPurchaseOrder(receipt.orderId))
    } catch (error) {
      showToast('warning', '历史订单读取失败', errorText(error, '无法回显原验收订单'))
    }
  }
  editingId.value = receipt.id
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
  await changeOrder(form.orderId)
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

async function changeProject(value: string): Promise<void> {
  form.projectId = value
  form.orderId = ''
  form.contractId = ''
  form.partnerId = ''
  form.orderItemId = ''
  form.warehouseId = ''
  receiptCandidates.value = []
  if (!value || busy.value) return
  busy.value = true
  try {
    await loadCandidates(value)
  } catch (error) {
    showToast('error', '业务候选读取失败', errorText(error, '材料验收候选读取失败'))
  } finally {
    busy.value = false
  }
}

function changeItem(value: string): void {
  form.orderItemId = value
  form.materialId =
    receiptCandidates.value.find((candidate) => candidate.orderItemId === value)?.materialId || ''
}

async function changeOrder(value: string): Promise<void> {
  form.orderId = value
  form.orderItemId = ''
  receiptCandidates.value = []
  const order = orderCandidates.value.find((candidate) => candidate.id === value)
  form.contractId = order?.contractId || ''
  form.partnerId = order?.partnerId || ''
  if (!value) return
  busy.value = true
  try {
    receiptCandidates.value = await loadOrderItemsForReceipt(value)
    changeItem(receiptCandidates.value[0]?.orderItemId || '')
  } catch (error) {
    receiptCandidates.value = []
    showToast('error', '订单明细读取失败', errorText(error, '订单明细读取失败'))
  } finally {
    busy.value = false
  }
}

async function save(): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    const command: ReceiptCommand = {
      projectId: required(form, 'projectId', '项目'),
      orderId: required(form, 'orderId', '采购订单'),
      contractId: optional(form, 'contractId'),
      partnerId: optional(form, 'partnerId'),
      receiptDate: optional(form, 'receiptDate'),
      deliveryNoteNo: optional(form, 'deliveryNoteNo'),
      warehouseId:
        form.receiptMode === 'INVENTORY'
          ? required(form, 'warehouseId', '入库仓库')
          : optional(form, 'warehouseId'),
      receiverId: optional(form, 'receiverId'),
      receiptMode: (form.receiptMode || 'INVENTORY') as ReceiptCommand['receiptMode'],
      remark: optional(form, 'remark'),
    }
    const id = await saveMaterialReceipt(
      {
        id: editingId.value || undefined,
        command,
        saveItems: canSaveItems.value,
        items: (receiptId) => [
          {
            receiptId,
            orderItemId: required(form, 'orderItemId', '订单明细'),
            materialId: optional(form, 'materialId'),
            wbsTaskId: optional(form, 'wbsTaskId'),
            budgetLineId: optional(form, 'budgetLineId'),
            acceptedQuantity: decimal(form, 'acceptedQuantity', '验收数量'),
            useLocation: optional(form, 'useLocation'),
          },
        ],
      },
      application,
    )
    editingId.value = ''
    editorOpen.value = false
    await loadPage()
    const created = records.value.find((record) => record.id === id)
    if (created) await selectRecord(created)
    showToast('success', '操作成功', '材料验收已保存，列表与详情已更新')
  } catch (error) {
    if (error instanceof NewMaterialReceiptSaveError) {
      const failure = errorText(error.saveError, '材料验收保存失败')
      errorMessage.value = error.rollbackFailed
        ? `${failure}；草稿回滚失败：${errorText(error.rollbackError, '需要人工核对')}`
        : `${failure}；本次新建草稿已回滚`
    } else errorMessage.value = errorText(error, '材料验收保存失败')
    showToast('error', '材料验收保存失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function submitSelected(): Promise<void> {
  if (!selected.value || busy.value) return
  const id = selected.value.id
  busy.value = true
  try {
    await submitSavedMaterialReceipt(id, application)
    await loadPage()
    const refreshed = records.value.find((record) => record.id === id)
    if (refreshed) await selectRecord(refreshed)
    showToast('success', '操作成功', '材料验收已提交，状态已重新读取')
  } catch (error) {
    showToast('error', '材料验收提交失败', errorText(error, '材料验收提交失败'))
  } finally {
    busy.value = false
  }
}

function changeReturnItem(value: string): void {
  const item = detailItems.value.find((candidate) => candidate.id === value)
  returnForm.quantity = item?.acceptedQuantity || ''
  returnForm.reason = ''
}

function openReturn(): void {
  const first = returnableItems.value[0]
  if (!first || !selected.value) return
  for (const key of Object.keys(returnForm)) delete returnForm[key]
  returnForm.receiptItemId = first.value
  returnForm.returnDate = selected.value.receiptDate || ''
  changeReturnItem(first.value)
  returnOpen.value = true
}

async function saveReturn(): Promise<void> {
  if (!selected.value || busy.value) return
  const id = selected.value.id
  const command: ReceiptSupplierReturnCommand = {
    receiptItemId: required(returnForm, 'receiptItemId', '验收明细'),
    returnKind: 'ACCEPTED',
    quantity: required(returnForm, 'quantity', '退货数量'),
    returnDate: required(returnForm, 'returnDate', '退货日期'),
    reason: required(returnForm, 'reason', '退货原因'),
    idempotencyKey: crypto.randomUUID(),
  }
  busy.value = true
  try {
    await confirmReceiptSupplierReturn(command)
    returnOpen.value = false
    await loadPage()
    const refreshed = records.value.find((record) => record.id === id)
    if (refreshed) await selectRecord(refreshed)
    showToast('success', '退货确认成功', '库存、订单与合同净应付已重新读取')
  } catch (error) {
    showToast('error', '供应商退货失败', errorText(error, '供应商退货失败'))
  } finally {
    busy.value = false
  }
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
    <V2Card title="材料验收" :heading-level="1">
      <template #actions
        ><V2Button v-if="canAdd" size="small" @click="openCreate">新建材料验收</V2Button></template
      >
    </V2Card>

    <V2PageState
      v-if="loading && !records.length"
      kind="loading"
      title="正在加载"
      description="正在读取材料验收。"
    />
    <V2PageState
      v-else-if="!errorMessage && !loading && !records.length"
      title="暂无记录"
      description="当前项目范围没有材料验收。"
    >
      <template v-if="canAdd" #actions
        ><V2Button @click="openCreate">新建材料验收</V2Button></template
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
                    >{{ receiptCode(record) }}</V2Button
                  >
                </td>
                <td>{{ record.orderCode || '采购订单编号缺失' }}</td>
                <td>
                  <V2Badge>{{ statusLabel(record.approvalStatus) }}</V2Badge>
                </td>
                <td>
                  <V2Badge>{{ statusLabel(record.approvalStatus) }}</V2Badge>
                </td>
                <td>{{ recordAmount(record) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="purchase-execution-page__pagination" aria-label="材料验收分页">
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
      mode="receipt"
      title="材料验收"
      :business-id="selected.id"
      :business-code="receiptCode(selected)"
      :project-name="selected.projectName"
      :source-label="selected.orderCode || '采购订单编号缺失'"
      :approval-status="selected.approvalStatus"
      :business-status="statusLabel(selected.approvalStatus)"
      :amount="selected.totalAmount"
      :detail-table="detailTable"
      :detail-loading="detailLoading"
      :can-edit="canEditSelected"
      :can-manage-attachments="canSaveItems"
      :can-submit="canSubmitSelected"
      :can-return="canReturn && returnableItems.length > 0"
      @close="clearDetail"
      @edit="openEdit"
      @submit="submitSelected"
      @return="openReturn"
    />

    <V2Dialog
      v-model:open="editorOpen"
      :title="editingId ? '编辑材料验收' : '新建材料验收'"
      description="填写基本信息与明细后一次提交，保存后刷新数量、金额与状态。"
      :close-disabled="busy"
      :close-on-backdrop="false"
    >
      <form
        id="material-receipt-editor-form"
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
          v-model="form.orderId"
          label="采购订单"
          :options="orderOptions"
          :disabled="busy"
          required
          @update:model-value="changeOrder"
        />
        <section
          class="purchase-execution-page__draft-lines"
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
                  <th>选择*</th>
                  <th>物料</th>
                  <th>规格</th>
                  <th>单位</th>
                  <th>订单数量</th>
                  <th>累计收货</th>
                  <th>剩余数量</th>
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
                        @change="changeItem(item.orderItemId || '')"
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
        <V2Input v-model="form.acceptedQuantity" label="本次合格数量" :decimal-scale="2" required />
        <V2Input
          v-if="form.receiptMode === 'DIRECT_CONSUMPTION'"
          v-model="form.useLocation"
          label="使用部位"
          required
        />
        <V2Input v-model="form.remark" label="备注" />
      </form>
      <template #footer
        ><V2Button variant="secondary" :disabled="busy" @click="editorOpen = false">取消</V2Button
        ><V2Button type="submit" form="material-receipt-editor-form" :loading="busy"
          >保存</V2Button
        ></template
      >
    </V2Dialog>

    <V2Dialog
      v-model:open="returnOpen"
      title="登记供应商退货"
      description="确认后由服务端冲销库存、订单收货量与合同净应付。"
      :close-disabled="busy"
      :close-on-backdrop="false"
    >
      <form
        id="receipt-supplier-return-form"
        class="purchase-execution-page__form"
        @submit.prevent="saveReturn"
      >
        <V2Select
          v-model="returnForm.receiptItemId"
          label="验收明细"
          :options="returnableItems"
          :disabled="busy"
          required
          @update:model-value="changeReturnItem"
        />
        <V2Input v-model="returnForm.quantity" label="退货数量" :decimal-scale="2" required />
        <V2Input v-model="returnForm.returnDate" type="date" label="退货日期" required />
        <V2Input v-model="returnForm.reason" label="退货原因" required />
      </form>
      <template #footer
        ><V2Button variant="secondary" :disabled="busy" @click="returnOpen = false">取消</V2Button
        ><V2Button type="submit" form="receipt-supplier-return-form" :loading="busy"
          >确认退货</V2Button
        ></template
      >
    </V2Dialog>
  </section>
</template>

<style src="./purchase-execution.css"></style>
