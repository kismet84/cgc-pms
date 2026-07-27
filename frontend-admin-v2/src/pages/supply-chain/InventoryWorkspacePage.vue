<script setup lang="ts">
import type {
  MaterialRecord,
  StockConsumptionBaselineRecord,
  StockIncomingSupplyRecord,
  StockKpiRecord,
  StockLedger,
  StockRecord,
  StockTransferCandidateRecord,
  WarehouseCommand,
  WarehouseRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  V2ActionMenu,
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
  createStockTransfer,
  createWarehouse,
  deleteWarehouse,
  loadMaterials,
  loadStockConsumptionBaseline,
  loadStockIncomingSupplies,
  loadStockKpi,
  loadStockLedger,
  loadStocks,
  loadStockTransferCandidates,
  loadWarehouse,
  loadWarehouses,
  updateStockReplenishment,
  updateWarehouse,
  updateWarehouseStatus,
} from '@/services/supply-chain'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

type Mode = 'warehouse' | 'stock'

const route = useRoute()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const dateTimeLabel = (value?: string) => (value ? value.replace('T', ' ').slice(0, 16) : '-')
const warehouses = ref<WarehouseRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const stocks = ref<StockRecord[]>([])
const warehouseTotal = ref(0)
const stockTotal = ref(0)
const kpi = ref<StockKpiRecord | null>(null)
const selectedStock = ref<StockRecord | null>(null)
const ledger = ref<StockLedger | null>(null)
const candidates = ref<StockTransferCandidateRecord[]>([])
const incoming = ref<StockIncomingSupplyRecord[]>([])
const baseline = ref<StockConsumptionBaselineRecord | null>(null)
const pageNo = ref(1)
const pageSize = 10
const transactionPageNo = ref(1)
const transactionPageSize = 10
const loading = ref(false)
const detailLoading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const detailError = ref('')
const warehouseDialog = ref(false)
const stockDialog = ref<'settings' | 'transfer' | null>(null)
const deleteTarget = ref<WarehouseRecord | null>(null)
const editingWarehouseId = ref('')
const filter = reactive({ warehouseId: '', materialId: '', keyword: '' })
const transactionKeyword = ref('')
const warehouseForm = reactive<WarehouseCommand>({
  projectId: '',
  warehouseCode: '',
  warehouseName: '',
  status: 'ENABLE',
  remark: '',
})
const stockForm = reactive({
  safetyStockQty: '',
  replenishmentTargetQty: '',
  replenishmentLeadDays: '',
  sourceStockId: '',
  quantity: '',
  reason: '',
  idempotencyKey: '',
})
let controller: AbortController | null = null
let detailController: AbortController | null = null
let generation = 0
let detailGeneration = 0

const mode = computed<Mode>(() => (route.path === '/inventory/warehouse' ? 'warehouse' : 'stock'))
const projectId = computed(() => workspace.selectedProjectId || '')
const title = computed(() => (mode.value === 'warehouse' ? '仓库管理' : '库存台账'))
const canWarehouseAdd = computed(() => session.hasPermission('inventory:warehouse:add'))
const canWarehouseEdit = computed(() => session.hasPermission('inventory:warehouse:edit'))
const canWarehouseDelete = computed(() => session.hasPermission('inventory:warehouse:delete'))
const canReadStock = computed(() => session.hasPermission('inventory:stock:list'))
const canReadMaterials = computed(() => session.hasPermission('material:dict:list'))
const canStockEdit = computed(() => session.hasPermission('inventory:stock:edit'))
const canTransfer = computed(
  () => canStockEdit.value && session.hasPermission('inventory:transaction:add'),
)
const pageCount = computed(() =>
  Math.max(
    1,
    Math.ceil((mode.value === 'warehouse' ? warehouseTotal.value : stockTotal.value) / pageSize),
  ),
)
const transactionPageCount = computed(() =>
  Math.max(1, Math.ceil(Number(ledger.value?.txns.total ?? 0) / transactionPageSize)),
)
const warehouseOptions = computed(() => [
  { value: '', label: '全部仓库' },
  ...warehouses.value.map((item) => ({
    value: item.id,
    label: `${item.warehouseCode} · ${item.warehouseName}`,
  })),
])
const materialOptions = computed(() => [
  { value: '', label: '全部物料' },
  ...materials.value.map((item) => ({
    value: item.id,
    label: [item.materialCode, item.materialName, item.specification].filter(Boolean).join(' · '),
  })),
])
const candidateOptions = computed(() =>
  candidates.value.map((item) => ({
    value: item.stockId,
    label: `${item.warehouseName} · 可调 ${item.transferableQty}`,
  })),
)

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

function required(value: string, label: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError(`${label}不能为空`)
  return normalized
}

function decimal(value: string, label: string): string {
  const normalized = required(value, label)
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) throw new TypeError(`${label}必须为非负十进制数`)
  return normalized
}

function statusLabel(status?: string | null): string {
  return status === 'ENABLE' ? '启用' : status === 'DISABLE' ? '停用' : '未知状态'
}

function transactionTypeLabel(type?: string | null): string {
  return (
    {
      IN: '入库',
      OUT: '出库',
      TRANSFER_IN: '调拨入库',
      TRANSFER_OUT: '调拨出库',
      RETURN_IN: '退料入库',
    }[type ?? ''] ?? '未知流水类型'
  )
}

function sourceTypeLabel(type?: string | null): string {
  return (
    {
      MAT_RECEIPT: '采购验收',
      MAT_REQUISITION: '领料',
      MATERIAL_RETURN: '退料',
      MATERIAL_RETURN_REVERSAL: '退料冲销',
      STOCK_TRANSFER: '库存调拨',
    }[type ?? ''] ?? '未知来源类型'
  )
}

async function loadPage(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  const currentGeneration = ++generation
  loading.value = true
  errorMessage.value = ''
  try {
    if (mode.value === 'warehouse') {
      const page = await loadWarehouses(
        { pageNo: pageNo.value, pageSize, projectId: projectId.value || undefined },
        current.signal,
      )
      if (currentGeneration !== generation) return
      warehouses.value = page.records
      warehouseTotal.value = Number(page.total ?? 0)
      return
    }

    const [warehousePage, materialPage, stockPage, nextKpi] = await Promise.all([
      loadWarehouses(
        { pageNo: 1, pageSize: 200, projectId: projectId.value || undefined, status: 'ENABLE' },
        current.signal,
      ),
      canReadMaterials.value
        ? loadMaterials({ pageNo: 1, pageSize: 200, status: 'ENABLE' }, current.signal)
        : Promise.resolve({ records: [], total: 0, pageNo: 1, pageSize: 200 }),
      loadStocks(
        {
          warehouseId: filter.warehouseId || undefined,
          materialId: filter.materialId || undefined,
          projectId: projectId.value || undefined,
          keyword: filter.keyword || undefined,
          pageNo: pageNo.value,
          pageSize,
        },
        current.signal,
      ),
      canReadStock.value
        ? loadStockKpi(
            {
              warehouseId: filter.warehouseId || undefined,
              projectId: projectId.value || undefined,
            },
            current.signal,
          )
        : Promise.resolve(null),
    ])
    if (currentGeneration !== generation) return
    warehouses.value = warehousePage.records
    materials.value = materialPage.records
    stocks.value = stockPage.records
    stockTotal.value = Number(stockPage.total ?? 0)
    kpi.value = nextKpi
    if (selectedStock.value) {
      const refreshed = stockPage.records.find((item) => item.id === selectedStock.value?.id)
      if (refreshed) {
        selectedStock.value = refreshed
      } else {
        clearStockDetail()
      }
    }
  } catch (error) {
    if (current.signal.aborted) return
    errorMessage.value = errorText(error, '库存数据加载失败')
    showToast('error', '库存数据读取失败', errorMessage.value)
    stocks.value = []
    stockTotal.value = 0
  } finally {
    if (currentGeneration === generation) loading.value = false
  }
}

function clearStockDetail(): void {
  detailController?.abort()
  selectedStock.value = null
  ledger.value = null
  candidates.value = []
  incoming.value = []
  baseline.value = null
  detailError.value = ''
  transactionKeyword.value = ''
  transactionPageNo.value = 1
}

async function openStock(record: StockRecord, resetPage = true): Promise<void> {
  if (!record.warehouseId || !record.materialId) return
  detailController?.abort()
  const current = new AbortController()
  detailController = current
  const currentGeneration = ++detailGeneration
  selectedStock.value = record
  detailLoading.value = true
  detailError.value = ''
  if (resetPage) {
    transactionPageNo.value = 1
    transactionKeyword.value = ''
  }
  try {
    const [nextLedger, nextCandidates, nextIncoming, nextBaseline] = await Promise.all([
      loadStockLedger(
        {
          warehouseId: record.warehouseId,
          materialId: record.materialId,
          projectId: projectId.value || undefined,
          keyword: transactionKeyword.value || undefined,
          pageNo: transactionPageNo.value,
          pageSize: transactionPageSize,
        },
        current.signal,
      ),
      canReadStock.value && record.id
        ? loadStockTransferCandidates(record.id, current.signal)
        : Promise.resolve([]),
      canReadStock.value && record.id
        ? loadStockIncomingSupplies(record.id, current.signal)
        : Promise.resolve([]),
      canReadStock.value && record.id
        ? loadStockConsumptionBaseline(record.id, current.signal)
        : Promise.resolve(null),
    ])
    if (currentGeneration !== detailGeneration) return
    ledger.value = nextLedger
    candidates.value = nextCandidates
    incoming.value = nextIncoming
    baseline.value = nextBaseline
  } catch (error) {
    if (current.signal.aborted) return
    detailError.value = errorText(error, '库存明细加载失败')
    showToast('error', '库存明细读取失败', detailError.value)
  } finally {
    if (currentGeneration === detailGeneration) detailLoading.value = false
  }
}

function search(): void {
  pageNo.value = 1
  clearStockDetail()
  void loadPage()
}

function resetSearch(): void {
  filter.warehouseId = ''
  filter.materialId = ''
  filter.keyword = ''
  search()
}

function changePage(next: number): void {
  pageNo.value = next
  clearStockDetail()
  void loadPage()
}

function searchTransactions(): void {
  if (!selectedStock.value) return
  transactionPageNo.value = 1
  void openStock(selectedStock.value, false)
}

function changeTransactionPage(next: number): void {
  if (!selectedStock.value) return
  transactionPageNo.value = next
  void openStock(selectedStock.value, false)
}

function openCreateWarehouse(): void {
  editingWarehouseId.value = ''
  Object.assign(warehouseForm, {
    projectId: projectId.value,
    warehouseCode: '',
    warehouseName: '',
    status: 'ENABLE',
    remark: '',
  })
  warehouseDialog.value = true
}

async function openEditWarehouse(record: WarehouseRecord): Promise<void> {
  busy.value = true
  try {
    const detail = await loadWarehouse(record.id)
    editingWarehouseId.value = detail.id
    Object.assign(warehouseForm, {
      projectId: detail.projectId,
      warehouseCode: detail.warehouseCode,
      warehouseName: detail.warehouseName,
      status: detail.status === 'DISABLE' ? 'DISABLE' : 'ENABLE',
      remark: detail.remark || '',
    })
    warehouseDialog.value = true
  } catch (error) {
    errorMessage.value = errorText(error, '仓库详情加载失败')
    showToast('error', '仓库详情读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function saveWarehouse(): Promise<void> {
  busy.value = true
  errorMessage.value = ''
  try {
    const body: WarehouseCommand = {
      projectId: required(warehouseForm.projectId, '项目ID'),
      warehouseCode: required(warehouseForm.warehouseCode, '仓库编码'),
      warehouseName: required(warehouseForm.warehouseName, '仓库名称'),
      status: warehouseForm.status,
      remark: warehouseForm.remark?.trim() || undefined,
    }
    if (editingWarehouseId.value) await updateWarehouse(editingWarehouseId.value, body)
    else await createWarehouse(body)
    warehouseDialog.value = false
    await loadPage()
    showToast('success', '操作成功', '仓库已保存')
  } catch (error) {
    errorMessage.value = errorText(error, '仓库保存失败')
    showToast('error', '仓库保存失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function toggleWarehouse(record: WarehouseRecord): Promise<void> {
  busy.value = true
  try {
    await updateWarehouseStatus(record.id, record.status === 'ENABLE' ? 'DISABLE' : 'ENABLE')
    await loadPage()
  } catch (error) {
    errorMessage.value = errorText(error, '仓库状态更新失败')
    showToast('error', '仓库状态更新失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function confirmDeleteWarehouse(): Promise<void> {
  if (!deleteTarget.value) return
  busy.value = true
  try {
    await deleteWarehouse(deleteTarget.value.id)
    deleteTarget.value = null
    await loadPage()
  } catch (error) {
    errorMessage.value = errorText(error, '仓库删除失败')
    showToast('error', '仓库删除失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function openSettings(): void {
  const stock = ledger.value?.stock
  if (!stock?.id) return
  stockForm.safetyStockQty = stock.safetyStockQty
  stockForm.replenishmentTargetQty = stock.replenishmentTargetQty || ''
  stockForm.replenishmentLeadDays = stock.replenishmentLeadDays?.toString() || ''
  stockDialog.value = 'settings'
}

function openTransfer(): void {
  stockForm.sourceStockId = ''
  stockForm.quantity = ''
  stockForm.reason = ''
  stockForm.idempotencyKey = crypto.randomUUID()
  stockDialog.value = 'transfer'
}

async function saveStockAction(): Promise<void> {
  const stock = ledger.value?.stock
  const selected = selectedStock.value
  if (!stock?.id || !stockDialog.value || !selected) return
  busy.value = true
  errorMessage.value = ''
  try {
    if (stockDialog.value === 'settings') {
      const leadDays = stockForm.replenishmentLeadDays.trim()
      if (leadDays && !/^\d+$/.test(leadDays)) throw new TypeError('补货提前期必须为整数')
      await updateStockReplenishment(stock.id, {
        safetyStockQty: decimal(stockForm.safetyStockQty, '安全库存'),
        replenishmentTargetQty: stockForm.replenishmentTargetQty.trim()
          ? decimal(stockForm.replenishmentTargetQty, '补货目标量')
          : null,
        replenishmentLeadDays: leadDays ? Number(leadDays) : null,
      })
    } else {
      await createStockTransfer({
        sourceStockId: required(stockForm.sourceStockId, '来源库存'),
        targetStockId: stock.id,
        quantity: decimal(stockForm.quantity, '调拨数量'),
        idempotencyKey: stockForm.idempotencyKey,
        reason: required(stockForm.reason, '调拨原因'),
      })
    }
    stockDialog.value = null
    await loadPage()
    await openStock(selected, false)
    showToast('success', '操作成功', '库存事实已写入并重新读取')
  } catch (error) {
    errorMessage.value = errorText(error, '库存操作失败')
    showToast('error', '库存操作失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

watch(
  [mode, projectId],
  () => {
    pageNo.value = 1
    filter.warehouseId = ''
    filter.materialId = ''
    filter.keyword = ''
    clearStockDetail()
    void loadPage()
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  controller?.abort()
  detailController?.abort()
})
</script>

<template>
  <section class="inventory-workspace-page">
    <V2Card :title="title" :heading-level="1">
      <template #actions>
        <V2Button
          v-if="mode === 'warehouse' && canWarehouseAdd"
          size="small"
          @click="openCreateWarehouse"
        >
          新建仓库
        </V2Button>
        <div v-else class="inventory-workspace-page__toolbar">
          <V2Select
            v-model="filter.warehouseId"
            label="仓库"
            hide-label
            placeholder="全部仓库"
            allow-empty
            :options="warehouseOptions"
          />
          <V2Select
            v-model="filter.materialId"
            label="物料"
            hide-label
            placeholder="全部物料"
            allow-empty
            :options="materialOptions"
          />
          <V2Input
            v-model="filter.keyword"
            label="物料关键词"
            hide-label
            type="search"
            placeholder="物料编码、名称或规格"
          />
          <V2Button size="small" :loading="loading" @click="search">查询</V2Button>
          <V2Button size="small" variant="ghost" :disabled="loading" @click="resetSearch"
            >重置</V2Button
          >
        </div>
      </template>
      <dl v-if="mode === 'stock' && kpi" class="v2-ledger-kpis" aria-label="库存指标">
        <div>
          <dt>启用仓库</dt>
          <dd>{{ kpi.warehouseCount }}</dd>
        </div>
        <div>
          <dt>低库存项</dt>
          <dd>{{ kpi.lowStockCount }}</dd>
        </div>
        <div>
          <dt>库存物料</dt>
          <dd>{{ kpi.materialTypeCount }}</dd>
        </div>
        <div>
          <dt>入库 / 出库流水</dt>
          <dd>{{ kpi.txnInCount }} / {{ kpi.txnOutCount }}</dd>
        </div>
      </dl>
    </V2Card>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在读取"
      description="正在读取库存余额与流水。"
    />

    <template v-else-if="mode === 'warehouse'">
      <V2PageState
        v-if="!errorMessage && !warehouses.length"
        title="暂无仓库"
        description="当前项目范围无可见仓库。"
      />
      <V2Card v-else>
        <div
          class="inventory-workspace-page__table-wrap"
          role="region"
          aria-label="仓库列表"
          tabindex="0"
        >
          <table>
            <thead>
              <tr>
                <th>仓库编码</th>
                <th>仓库名称</th>
                <th>项目</th>
                <th class="v2-table-cell--status">状态</th>
                <th>更新时间</th>
                <th class="v2-table-cell--actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in warehouses" :key="item.id">
                <th scope="row">{{ item.warehouseCode }}</th>
                <td>{{ item.warehouseName }}</td>
                <td>{{ item.projectName || '项目信息缺失' }}</td>
                <td class="v2-table-cell--status">
                  <V2Badge :tone="item.status === 'ENABLE' ? 'success' : 'neutral'">
                    {{ statusLabel(item.status) }}
                  </V2Badge>
                </td>
                <td>{{ dateTimeLabel(item.updatedAt) }}</td>
                <td class="v2-table-cell--actions">
                  <V2ActionMenu :label="`${item.warehouseName}操作`">
                    <V2Button
                      v-if="canWarehouseEdit"
                      variant="ghost"
                      size="small"
                      @click="openEditWarehouse(item)"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="canWarehouseEdit"
                      variant="ghost"
                      size="small"
                      :disabled="busy"
                      @click="toggleWarehouse(item)"
                      >{{ item.status === 'ENABLE' ? '停用' : '启用' }}</V2Button
                    >
                    <V2Button
                      v-if="canWarehouseDelete"
                      variant="danger"
                      size="small"
                      @click="deleteTarget = item"
                      >删除</V2Button
                    >
                  </V2ActionMenu>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav v-if="pageCount > 1" class="inventory-workspace-page__pager" aria-label="仓库分页">
          <V2Button variant="secondary" :disabled="pageNo <= 1" @click="changePage(pageNo - 1)"
            >上一页</V2Button
          >
          <span>第 {{ pageNo }} / {{ pageCount }} 页</span>
          <V2Button
            variant="secondary"
            :disabled="pageNo >= pageCount"
            @click="changePage(pageNo + 1)"
            >下一页</V2Button
          >
        </nav>
      </V2Card>
    </template>

    <template v-else>
      <V2PageState
        v-if="!errorMessage && !stocks.length"
        title="暂无库存台账"
        description="当前项目和筛选范围没有库存余额记录。"
      />
      <V2Card v-else title="全部库存余额">
        <template #title-extra>
          <V2Badge tone="neutral">共 {{ stockTotal }} 条</V2Badge>
        </template>
        <div
          class="inventory-workspace-page__table-wrap"
          role="region"
          aria-label="库存台账列表"
          tabindex="0"
        >
          <table class="inventory-workspace-page__stock-table">
            <thead>
              <tr>
                <th>物料编码</th>
                <th>物料名称</th>
                <th v-if="!projectId">项目</th>
                <th>仓库</th>
                <th>可用数量</th>
                <th>安全库存</th>
                <th>平均成本</th>
                <th>库存价值</th>
                <th>更新时间</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in stocks"
                :key="item.id || `${item.warehouseId}-${item.materialId}`"
                :class="{
                  'inventory-workspace-page__row--selected': item.id === selectedStock?.id,
                }"
              >
                <th scope="row">
                  <V2Button
                    size="small"
                    variant="ghost"
                    class="v2-table__record-link"
                    @click="openStock(item)"
                  >
                    {{ item.materialCode || '物料编码缺失' }}
                  </V2Button>
                </th>
                <td>{{ item.materialName || '物料名称缺失' }}</td>
                <td v-if="!projectId">
                  {{ item.projectName || '项目信息缺失' }}
                </td>
                <td>{{ item.warehouseName || '仓库名称缺失' }}</td>
                <td>
                  <strong>{{ item.availableQty }}</strong> {{ item.unit || '' }}
                </td>
                <td>{{ item.safetyStockQty }} {{ item.unit || '' }}</td>
                <td>{{ item.averageUnitCost }}</td>
                <td>{{ formatAmount(item.inventoryValue) }}</td>
                <td>{{ dateTimeLabel(item.updatedTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav v-if="pageCount > 1" class="inventory-workspace-page__pager" aria-label="库存台账分页">
          <V2Button variant="secondary" :disabled="pageNo <= 1" @click="changePage(pageNo - 1)"
            >上一页</V2Button
          >
          <span>第 {{ pageNo }} / {{ pageCount }} 页</span>
          <V2Button
            variant="secondary"
            :disabled="pageNo >= pageCount"
            @click="changePage(pageNo + 1)"
            >下一页</V2Button
          >
        </nav>
      </V2Card>
    </template>

    <V2Dialog
      :open="Boolean(selectedStock)"
      title="库存明细与流水"
      :description="
        selectedStock
          ? `${selectedStock.materialCode || '物料编码缺失'} · ${selectedStock.materialName || '物料名称缺失'} · ${selectedStock.warehouseName || '仓库名称缺失'}`
          : ''
      "
      panel-class="v2-dialog-standard v2-detail-dialog v2-dialog-bottom-sheet"
      :close-on-backdrop="true"
      @backdrop-click="clearStockDetail"
      @close="clearStockDetail"
    >
      <V2PageState
        v-if="detailLoading"
        kind="loading"
        :heading-level="2"
        title="正在读取库存链路"
        description="正在读取余额、供应、消耗和来源流水。"
      />
      <V2PageState
        v-else-if="detailError"
        kind="error"
        :heading-level="2"
        title="库存链路读取失败"
        :description="detailError"
      />
      <template v-else-if="ledger?.stock">
        <section class="inventory-workspace-page__drawer-summary">
          <dl class="v2-detail-dialog__facts">
            <div>
              <dt>可用数量</dt>
              <dd>{{ ledger.stock.availableQty }} {{ ledger.stock.unit }}</dd>
            </div>
            <div>
              <dt>库存价值</dt>
              <dd>{{ formatAmount(ledger.stock.inventoryValue) }}</dd>
            </div>
            <div>
              <dt>平均成本</dt>
              <dd>{{ ledger.stock.averageUnitCost }}</dd>
            </div>
            <div>
              <dt>安全库存</dt>
              <dd>{{ ledger.stock.safetyStockQty }}</dd>
            </div>
          </dl>
        </section>

        <section class="inventory-workspace-page__facts">
          <section class="v2-detail-dialog__section" aria-labelledby="inventory-incoming-title">
            <h3 id="inventory-incoming-title">在途供应</h3>
            <p v-if="!incoming.length">无在途供应</p>
            <p v-for="item in incoming" :key="item.orderId">
              {{ item.orderCode }} · {{ item.remainingQty }} · {{ item.deliveryDate || '-' }}
            </p>
          </section>
          <section class="v2-detail-dialog__section" aria-labelledby="inventory-baseline-title">
            <h3 id="inventory-baseline-title">历史净领料</h3>
            <p v-if="baseline">
              30日 {{ baseline.netIssued30 }}；90日 {{ baseline.netIssued90 }}；截止
              {{ baseline.cutoffAt }}
            </p>
            <p v-else>暂无消耗基线</p>
          </section>
        </section>

        <section class="inventory-workspace-page__transactions" aria-labelledby="stock-txn-title">
          <div class="inventory-workspace-page__transaction-head">
            <div>
              <h3 id="stock-txn-title">库存流水</h3>
              <p>按业务来源展示数量、金额与结余。</p>
            </div>
            <div class="inventory-workspace-page__transaction-search">
              <V2Input
                v-model="transactionKeyword"
                label="流水关键词"
                hide-label
                type="search"
                placeholder="流水号或来源编号"
              />
              <V2Button type="button" size="small" variant="secondary" @click="searchTransactions"
                >查询流水</V2Button
              >
            </div>
          </div>
          <div
            class="inventory-workspace-page__table-wrap"
            role="region"
            aria-label="库存流水列表"
            tabindex="0"
          >
            <table>
              <thead>
                <tr>
                  <th>时间</th>
                  <th>类型</th>
                  <th>数量</th>
                  <th>结余</th>
                  <th>金额</th>
                  <th>来源</th>
                  <th>来源业务编号</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in ledger.txns.records" :key="item.id">
                  <td>{{ item.createdTime || '-' }}</td>
                  <td>{{ transactionTypeLabel(item.txnType) }}</td>
                  <td>{{ item.quantity }}</td>
                  <td>{{ item.availableAfter }}</td>
                  <td>{{ formatAmount(item.amount) }}</td>
                  <td>{{ sourceTypeLabel(item.sourceType) }}</td>
                  <td>{{ item.sourceCode || '来源编号缺失' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-if="!errorMessage && !detailError && !ledger.txns.records.length"
            :heading-level="3"
            title="暂无流水"
            description="当前库存项没有匹配的来源流水。"
          />
          <nav
            v-if="transactionPageCount > 1"
            class="inventory-workspace-page__pager"
            aria-label="库存流水分页"
          >
            <V2Button
              type="button"
              variant="secondary"
              :disabled="transactionPageNo <= 1"
              @click="changeTransactionPage(transactionPageNo - 1)"
              >上一页</V2Button
            >
            <span>第 {{ transactionPageNo }} / {{ transactionPageCount }} 页</span>
            <V2Button
              type="button"
              variant="secondary"
              :disabled="transactionPageNo >= transactionPageCount"
              @click="changeTransactionPage(transactionPageNo + 1)"
              >下一页</V2Button
            >
          </nav>
        </section>
      </template>
      <template #footer>
        <V2Button v-if="canStockEdit" type="button" variant="secondary" @click="openSettings">
          维护阈值
        </V2Button>
        <V2Button v-if="canTransfer && candidates.length" type="button" @click="openTransfer">
          库存调拨
        </V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="warehouseDialog"
      :title="editingWarehouseId ? '编辑仓库' : '新建仓库'"
      :close-on-backdrop="false"
      :close-disabled="busy"
      @close="warehouseDialog = false"
    >
      <div class="inventory-workspace-page__form">
        <V2Select
          v-model="warehouseForm.projectId"
          label="项目"
          :options="workspace.projects"
          :disabled="Boolean(editingWarehouseId)"
          required
        />
        <V2Input v-model="warehouseForm.warehouseCode" label="仓库编码" required />
        <V2Input v-model="warehouseForm.warehouseName" label="仓库名称" required />
        <V2Select
          v-model="warehouseForm.status"
          label="状态"
          :options="[
            { value: 'ENABLE', label: '启用' },
            { value: 'DISABLE', label: '停用' },
          ]"
        />
        <V2Input v-model="warehouseForm.remark" label="备注" />
      </div>
      <template #footer>
        <V2Button
          type="button"
          variant="secondary"
          :disabled="busy"
          @click="warehouseDialog = false"
        >
          取消
        </V2Button>
        <V2Button type="button" :loading="busy" @click="saveWarehouse">保存</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="Boolean(stockDialog)"
      :title="stockDialog === 'transfer' ? '库存调拨' : '维护补货阈值'"
      :close-on-backdrop="false"
      :close-disabled="busy"
      @close="stockDialog = null"
    >
      <div v-if="stockDialog === 'settings'" class="inventory-workspace-page__form">
        <V2Input v-model="stockForm.safetyStockQty" label="安全库存" required />
        <V2Input v-model="stockForm.replenishmentTargetQty" label="补货目标量" />
        <V2Input v-model="stockForm.replenishmentLeadDays" label="补货提前期（天）" />
      </div>
      <div v-else class="inventory-workspace-page__form">
        <V2Select
          v-model="stockForm.sourceStockId"
          label="来源库存"
          :options="candidateOptions"
          required
        />
        <V2Input v-model="stockForm.quantity" label="调拨数量" required />
        <V2Input v-model="stockForm.reason" label="调拨原因" required />
      </div>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="busy" @click="stockDialog = null"
          >取消</V2Button
        >
        <V2Button type="button" :loading="busy" @click="saveStockAction">提交并读取</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除仓库"
      :description="deleteTarget ? `确认删除 ${deleteTarget.warehouseName}？` : ''"
      danger
      :loading="busy"
      @close="deleteTarget = null"
      @confirm="confirmDeleteWarehouse"
    />
  </section>
</template>

<style scoped>
.inventory-workspace-page {
  display: grid;
  gap: var(--v2-space-4);
  min-width: 0;
}
.inventory-workspace-page__toolbar,
.inventory-workspace-page__actions,
.inventory-workspace-page__pager,
.inventory-workspace-page__transaction-head,
.inventory-workspace-page__transaction-search {
  display: flex;
  align-items: center;
  gap: var(--v2-space-3);
}
.inventory-workspace-page__toolbar {
  display: grid;
  grid-template-columns: minmax(10rem, 1fr) minmax(12rem, 1.25fr) minmax(14rem, 1.4fr) auto auto;
  width: min(72vw, 76rem);
}
.inventory-workspace-page dt {
  color: var(--v2-color-text-muted);
}
.inventory-workspace-page dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.inventory-workspace-page__table-wrap {
  overflow: auto;
}
.inventory-workspace-page table {
  width: 100%;
  min-width: 62rem;
  border-collapse: collapse;
}
.inventory-workspace-page th,
.inventory-workspace-page td {
  padding: var(--v2-space-3);
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
  text-align: left;
  vertical-align: middle;
}
.inventory-workspace-page__stock-table tbody tr {
  transition: background-color var(--v2-motion-fast) var(--v2-ease-standard);
}
.inventory-workspace-page__stock-table tbody tr:hover,
.inventory-workspace-page__row--selected {
  background: var(--v2-color-surface-subtle);
}
.inventory-workspace-page__pager {
  justify-content: flex-end;
  margin-top: var(--v2-space-4);
}
.inventory-workspace-page__drawer-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--v2-space-4);
}
.inventory-workspace-page__drawer-summary dl {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-3);
  margin: 0;
}
.inventory-workspace-page__drawer-summary dl div {
  display: grid;
  gap: var(--v2-space-1);
  padding: var(--v2-space-3);
  border-radius: var(--v2-radius-md);
  background: var(--v2-color-surface-subtle);
}
.inventory-workspace-page__drawer-summary dd {
  color: var(--v2-color-text);
  font-weight: var(--v2-font-weight-semibold);
}
.inventory-workspace-page__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
  margin-block: var(--v2-space-4);
}
.inventory-workspace-page__transactions {
  display: grid;
  gap: var(--v2-space-3);
}
.inventory-workspace-page__transaction-head {
  justify-content: space-between;
}
.inventory-workspace-page__transaction-head h3,
.inventory-workspace-page__transaction-head p {
  margin: 0;
}
.inventory-workspace-page__transaction-head p {
  margin-top: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.inventory-workspace-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
  align-items: end;
}
@media (max-width: 64rem) {
  .inventory-workspace-page__toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: min(70vw, 42rem);
  }
  .inventory-workspace-page__drawer-summary dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .inventory-workspace-page__drawer-summary,
  .inventory-workspace-page__transaction-head {
    align-items: stretch;
    flex-direction: column;
  }
}
@media (max-width: 40rem) {
  .inventory-workspace-page__toolbar,
  .inventory-workspace-page__facts,
  .inventory-workspace-page__drawer-summary dl,
  .inventory-workspace-page__form {
    grid-template-columns: 1fr;
  }
  .inventory-workspace-page__toolbar {
    width: 100%;
  }
  .inventory-workspace-page__actions,
  .inventory-workspace-page__transaction-search {
    flex-wrap: wrap;
  }
}
</style>
