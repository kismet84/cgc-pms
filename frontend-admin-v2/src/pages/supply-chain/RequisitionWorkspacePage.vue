<script setup lang="ts">
import type {
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
import { loadPartners } from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

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
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)
const detailLoading = ref(false)
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

function warehouseLabel(item: RequisitionRecord) {
  if (item.warehouseName)
    return [item.warehouseCode, item.warehouseName].filter(Boolean).join(' · ')
  return item.warehouseCode || '-'
}
let detailController: AbortController | null = null
let listGeneration = 0
let detailGeneration = 0

const projectId = computed(() => workspace.selectedProjectId || '')
const canAdd = computed(
  () =>
    session.hasPermission('requisition:add') &&
    session.hasPermission('requisition:edit') &&
    session.hasPermission('requisition:delete'),
)
const canEdit = computed(() => session.hasPermission('requisition:edit'))
const canDelete = computed(() => session.hasPermission('requisition:delete'))
const canSubmit = computed(() => session.hasPermission('requisition:submit'))
const canStockOut = computed(() => session.hasPermission('requisition:stock-out'))
const canReturn = computed(() => session.hasPermission('requisition:return'))
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
  () => canReturn.value && selected.value?.stockOutFlag === '1' && Boolean(trace.value),
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
    .filter((item) => item.sourceType === 'REQUISITION' && item.sourceLineId)
    .map((item) => ({
      value: item.id,
      label: `${transactionTypeLabel(item.txnType)} · ${item.quantity}`,
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
        requisitionCode: filter.requisitionCode || undefined,
        approvalStatus: filter.approvalStatus || undefined,
      },
      controller.signal,
    )
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = Number(page.total ?? 0)
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
  selected.value = record
  items.value = []
  trace.value = null
  materialReturn.value = null
  returnItems.value = []
  try {
    const [detail, nextItems, nextTrace] = await Promise.all([
      loadRequisition(record.id, controller.signal),
      loadRequisitionItems(record.id, controller.signal),
      loadRequisitionTrace(record.id, controller.signal),
    ])
    if (generation !== detailGeneration) return
    selected.value = detail
    items.value = nextItems
    trace.value = nextTrace
    if (nextTrace.materialReturn?.id) {
      const [nextReturn, nextReturnItems] = await Promise.all([
        loadMaterialReturn(nextTrace.materialReturn.id, controller.signal),
        loadMaterialReturnItems(nextTrace.materialReturn.id, controller.signal),
      ])
      if (generation !== detailGeneration) return
      materialReturn.value = nextReturn
      returnItems.value = nextReturnItems
    } else {
      materialReturn.value = null
      returnItems.value = []
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

function changePage(next: number): void {
  pageNo.value = next
  clearDetail()
  void loadPage()
}

async function loadEditorCandidates(candidateProjectId: string): Promise<void> {
  busy.value = true
  try {
    const [warehousePage, materialPage, partnerPage] = await Promise.all([
      loadWarehouses({
        pageNo: 1,
        pageSize: 200,
        projectId: candidateProjectId || undefined,
        status: 'ENABLE',
      }),
      loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' }),
      loadPartners({ pageNo: 1, pageSize: 200, partnerType: 'SUPPLIER', status: 'ENABLE' }),
    ])
    warehouses.value = warehousePage.records
    materials.value = materialPage.records
    partners.value = partnerPage.records
  } catch (error) {
    errorMessage.value = errorText(error, '领料候选读取失败')
    showToast('error', '领料候选读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function changeMaterial(value: string): void {
  form.materialId = value
  form.materialName = materials.value.find((item) => item.id === value)?.materialName || ''
}

async function changeEditorProject(value: string): Promise<void> {
  form.projectId = value
  form.warehouseId = ''
  await loadEditorCandidates(value)
}

async function openCreate(): Promise<void> {
  editingId.value = ''
  Object.assign(form, {
    projectId: projectId.value,
    warehouseId: '',
    partnerId: '',
    requisitionDate: new Date().toISOString().slice(0, 10),
    materialId: '',
    materialName: '',
    quantity: '',
    unitPrice: '',
    useLocation: '',
    remark: '',
  })
  editorOpen.value = true
  await loadEditorCandidates(form.projectId)
}

async function openEdit(): Promise<void> {
  if (!selected.value) return
  editingId.value = selected.value.id
  const item = items.value[0]
  Object.assign(form, {
    projectId: selected.value.projectId,
    warehouseId: selected.value.warehouseId || '',
    partnerId: selected.value.partnerId || '',
    requisitionDate: selected.value.requisitionDate || '',
    materialId: item?.materialId || '',
    materialName: item?.materialName || '',
    quantity: item?.quantity || '',
    unitPrice: item?.unitPrice || '',
    useLocation: item?.useLocation || '',
    remark: selected.value.remark || '',
  })
  editorOpen.value = true
  await loadEditorCandidates(form.projectId)
}

async function saveEditor(): Promise<void> {
  busy.value = true
  errorMessage.value = ''
  let createdId = ''
  try {
    const body: RequisitionCommand = {
      projectId: required('projectId', '项目'),
      warehouseId: required('warehouseId', '仓库'),
      partnerId: optional('partnerId'),
      requisitionDate: optional('requisitionDate'),
      remark: optional('remark'),
    }
    const id = editingId.value || (await createRequisition(body))
    if (!editingId.value) createdId = id
    else await updateRequisition(id, body)
    await saveRequisitionItems(id, [
      {
        requisitionId: id,
        ...(items.value[0]?.id && editingId.value ? { id: items.value[0].id } : {}),
        materialId: required('materialId', '物料'),
        materialName: optional('materialName'),
        quantity: decimal('quantity', '领料数量'),
        unitPrice: form.unitPrice?.trim() ? decimal('unitPrice', '单价') : undefined,
        useLocation: optional('useLocation'),
      },
    ])
    editorOpen.value = false
    await loadPage()
    const refreshed = records.value.find((item) => item.id === id)
    if (refreshed) await selectRecord(refreshed)
    showToast('success', '操作成功', '领料单已保存')
  } catch (error) {
    if (createdId) {
      try {
        await deleteRequisition(createdId)
      } catch {
        errorMessage.value = `${errorText(error, '领料单保存失败')}；新建草稿回滚失败，需要人工核查`
        showToast('error', '领料单保存失败', errorMessage.value)
        return
      }
    }
    errorMessage.value = `${errorText(error, '领料单保存失败')}${createdId ? '；本次新建草稿已回滚' : ''}`
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

function openReturn(): void {
  Object.assign(form, {
    requisitionItemId: items.value[0]?.id || '',
    originalStockTxnId: transactionOptions.value[0]?.value || '',
    returnQuantity: '',
    returnDate: new Date().toISOString().slice(0, 10),
    returnReason: '',
    idempotencyKey: crypto.randomUUID(),
  })
  returnOpen.value = true
}

async function saveReturn(): Promise<void> {
  busy.value = true
  errorMessage.value = ''
  try {
    const id = await confirmMaterialReturn({
      requisitionItemId: required('requisitionItemId', '领料明细'),
      originalStockTxnId: required('originalStockTxnId', '原出库流水'),
      quantity: decimal('returnQuantity', '退料数量'),
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

async function reverseReturn(): Promise<void> {
  if (!materialReturn.value) return
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

function openReverse(): void {
  form.reversalReason = ''
  reverseOpen.value = true
}

watch(
  projectId,
  () => {
    pageNo.value = 1
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
  <main class="requisition-page">
    <V2Card title="领料申请" :heading-level="1">
      <template #actions>
        <div class="requisition-page__filter-grid">
          <V2Input
            v-model="filter.requisitionCode"
            label="领料单号"
            hide-label
            placeholder="输入领料单号"
          /><V2Select
            v-model="filter.approvalStatus"
            label="审批状态"
            hide-label
            placeholder="选择审批状态"
            allow-empty
            :options="[
              { value: '', label: '全部状态' },
              { value: 'DRAFT', label: '草稿' },
              { value: 'APPROVING', label: '审批中' },
              { value: 'APPROVED', label: '已通过' },
              { value: 'REJECTED', label: '已驳回' },
            ]"
          /><V2Button size="small" :loading="loading" @click="search">查询领料单</V2Button>
        </div>
        <V2Button v-if="canAdd" size="small" @click="openCreate">新建领料单</V2Button>
      </template>
    </V2Card>
    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在读取"
      description="正在读取领退料数据。"
    />
    <V2PageState
      v-else-if="!errorMessage && !records.length"
      title="暂无领料单"
      description="当前项目范围无匹配记录。"
    />
    <V2Card v-else>
      <div class="requisition-page__table-wrap">
        <table>
          <thead>
            <tr>
              <th>单号</th>
              <th>日期</th>
              <th>仓库</th>
              <th>金额</th>
              <th>审批</th>
              <th>出库</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in records" :key="item.id">
              <td>
                <V2Button
                  size="small"
                  variant="ghost"
                  class="v2-table__record-link"
                  @click="selectRecord(item)"
                >
                  {{ item.requisitionCode || '领料单号缺失' }}
                </V2Button>
              </td>
              <td>{{ item.requisitionDate || '-' }}</td>
              <td>{{ warehouseLabel(item) }}</td>
              <td>{{ item.totalAmount || '-' }}</td>
              <td class="v2-table-cell--status">
                <V2Badge>{{ statusLabel(item.approvalStatus) }}</V2Badge>
              </td>
              <td class="v2-table-cell--status">
                {{ item.stockOutFlag === '1' ? '已出库' : '未出库' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <nav v-if="pageCount > 1" class="requisition-page__pager" aria-label="领料单分页">
        <V2Button variant="secondary" :disabled="pageNo <= 1" @click="changePage(pageNo - 1)"
          >上一页</V2Button
        ><span>第 {{ pageNo }} 页</span
        ><V2Button
          variant="secondary"
          :disabled="pageNo >= pageCount"
          @click="changePage(pageNo + 1)"
          >下一页</V2Button
        >
      </nav>
    </V2Card>

    <V2Dialog
      :open="Boolean(selected) && !editorOpen && !returnOpen && !reverseOpen && !deleteOpen"
      title="领退料完整链路"
      :description="selected ? selected.requisitionCode || '领料单号缺失' : ''"
      panel-class="v2-detail-dialog"
      :close-on-backdrop="true"
      @close="clearDetail"
      ><template v-if="selected"
        ><div class="requisition-page__detail-head">
          <div>
            <p>{{ selected.requisitionCode || '领料单号缺失' }}</p>
          </div>
          <div class="requisition-page__actions">
            <V2Button
              v-if="canEdit && selected.approvalStatus === 'DRAFT'"
              type="button"
              variant="secondary"
              @click="openEdit"
              >编辑</V2Button
            ><V2Button
              v-if="canDelete && selected.approvalStatus === 'DRAFT'"
              type="button"
              variant="danger"
              @click="deleteOpen = true"
              >删除</V2Button
            ><V2Button
              v-if="canSubmitSelected"
              type="button"
              :loading="busy"
              @click="execute('submit')"
              >提交审批</V2Button
            ><V2Button
              v-if="canStockOutSelected"
              type="button"
              :loading="busy"
              @click="execute('stock-out')"
              >执行出库</V2Button
            ><V2Button
              v-if="canReturnSelected"
              type="button"
              :disabled="!transactionOptions.length"
              @click="openReturn"
              >确认退料</V2Button
            >
          </div>
        </div>
        <V2PageState
          v-if="detailLoading"
          kind="loading"
          :heading-level="2"
          title="正在读取链路"
          description="请稍候。"
        /><template v-else
          ><section class="requisition-page__facts">
            <div>
              <span>审批</span><strong>{{ statusLabel(selected.approvalStatus) }}</strong>
            </div>
            <div>
              <span>出库</span
              ><strong>{{
                selected.stockOutFlag === '1' ? selected.stockOutAt || '已完成' : '未执行'
              }}</strong>
            </div>
            <div>
              <span>库存流水</span><strong>{{ trace?.stockTransactions.length || 0 }}</strong>
            </div>
            <div>
              <span>成本记录</span><strong>{{ trace?.costs.length || 0 }}</strong>
            </div>
          </section>
          <div class="requisition-page__subgrid">
            <section>
              <h3>领料明细</h3>
              <p v-for="item in items" :key="item.id || item.materialId">
                {{ item.materialName || '物料名称缺失' }} · 数量 {{ item.quantity }} · 单价
                {{ item.unitPrice || '-' }} · 金额 {{ item.amount || '-' }}
              </p>
            </section>
            <section>
              <h3>退料事实</h3>
              <p v-if="!materialReturn">暂无退料</p>
              <template v-else
                ><p>
                  {{ materialReturn.returnCode }} · {{ statusLabel(materialReturn.status) }} ·
                  {{ materialReturn.totalAmount }}
                </p>
                <p v-for="item in returnItems" :key="item.id">
                  数量 {{ item.quantity }} · 金额 {{ item.amount }}
                </p>
                <V2Button
                  v-if="canReturn && materialReturn.status === 'CONFIRMED'"
                  type="button"
                  variant="danger"
                  size="small"
                  @click="openReverse"
                  >冲销退料</V2Button
                ></template
              >
            </section>
          </div></template
        ></template
      ></V2Dialog
    >

    <V2Dialog
      :open="editorOpen"
      :title="editingId ? '编辑领料单' : '新建领料单'"
      :close-on-backdrop="false"
      :close-disabled="busy"
      @close="editorOpen = false"
      ><div class="requisition-page__form">
        <V2Select
          v-model="form.projectId"
          label="项目"
          :options="workspace.projects"
          :disabled="busy || Boolean(editingId)"
          required
          @update:model-value="changeEditorProject"
        /><V2Select
          v-model="form.warehouseId"
          label="仓库"
          :options="warehouseOptions"
          :disabled="busy"
          required
        /><V2Select
          v-model="form.partnerId"
          label="供应商"
          :options="partnerOptions"
          allow-empty
          :disabled="busy"
        /><V2Input v-model="form.requisitionDate" label="领料日期" /><V2Select
          v-model="form.materialId"
          label="物料"
          :options="materialOptions"
          :disabled="busy"
          required
          @update:model-value="changeMaterial"
        /><V2Input v-model="form.quantity" label="领料数量" required /><V2Input
          v-model="form.unitPrice"
          label="单价"
        /><V2Input v-model="form.useLocation" label="使用部位" /><V2Input
          v-model="form.remark"
          label="备注"
        />
      </div>
      <template #footer
        ><V2Button variant="secondary" :disabled="busy" @click="editorOpen = false">取消</V2Button
        ><V2Button :loading="busy" @click="saveEditor">保存</V2Button></template
      ></V2Dialog
    >
    <V2Dialog
      :open="returnOpen"
      title="确认退料"
      :close-on-backdrop="false"
      :close-disabled="busy"
      @close="returnOpen = false"
      ><div class="requisition-page__form">
        <V2Select
          v-model="form.requisitionItemId"
          label="领料明细"
          :options="itemOptions"
          required
        /><V2Select
          v-model="form.originalStockTxnId"
          label="原出库流水"
          :options="transactionOptions"
          required
        /><V2Input v-model="form.returnQuantity" label="退料数量" required /><V2Input
          v-model="form.returnDate"
          label="退料日期"
          required
        /><V2Input v-model="form.returnReason" label="退料原因" required />
      </div>
      <template #footer
        ><V2Button variant="secondary" :disabled="busy" @click="returnOpen = false">取消</V2Button
        ><V2Button :loading="busy" @click="saveReturn">确认退料</V2Button></template
      ></V2Dialog
    >
    <V2Dialog
      :open="reverseOpen"
      title="冲销退料"
      :close-on-backdrop="false"
      :close-disabled="busy"
      @close="reverseOpen = false"
      ><V2Input v-model="form.reversalReason" label="冲销原因" required /><template #footer
        ><V2Button variant="secondary" :disabled="busy" @click="reverseOpen = false">取消</V2Button
        ><V2Button variant="danger" :loading="busy" @click="reverseReturn"
          >确认冲销</V2Button
        ></template
      ></V2Dialog
    >
    <V2ConfirmDialog
      :open="deleteOpen"
      title="删除领料单"
      :description="selected ? `确认删除 ${selected.requisitionCode || selected.id}？` : ''"
      danger
      :loading="busy"
      @close="deleteOpen = false"
      @confirm="confirmDelete"
    />
  </main>
</template>

<style scoped>
.requisition-page {
  display: grid;
  gap: var(--v2-space-5);
  min-width: 0;
}
.requisition-page__detail-head,
.requisition-page__actions,
.requisition-page__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-3);
}
.requisition-page__filter-grid,
.requisition-page__form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-4);
  align-items: end;
}
.requisition-page__filter-grid {
  width: min(52vw, 52rem);
}
.requisition-page__table-wrap {
  overflow: auto;
}
.requisition-page table {
  width: 100%;
  min-width: 56rem;
  border-collapse: collapse;
}
.requisition-page th,
.requisition-page td {
  padding: var(--v2-space-3);
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
  text-align: left;
}
.requisition-page__pager {
  justify-content: center;
}
.requisition-page__actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}
.requisition-page__facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-3);
  margin-block: var(--v2-space-4);
}
.requisition-page__facts div {
  display: grid;
  gap: var(--v2-space-2);
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.requisition-page__facts span {
  color: var(--v2-color-text-secondary);
}
.requisition-page__subgrid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}
@media (max-width: 56.25rem) {
  .requisition-page__filter-grid,
  .requisition-page__form,
  .requisition-page__facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 40rem) {
  .requisition-page__detail-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .requisition-page__filter-grid,
  .requisition-page__form,
  .requisition-page__facts,
  .requisition-page__subgrid {
    grid-template-columns: 1fr;
  }
  .requisition-page__actions {
    justify-content: flex-start;
  }
  .requisition-page__table-wrap {
    max-width: 100%;
  }
}
</style>
