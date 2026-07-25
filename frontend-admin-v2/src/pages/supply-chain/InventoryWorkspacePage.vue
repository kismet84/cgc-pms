<script setup lang="ts">
import type {
  StockConsumptionBaselineRecord,
  StockIncomingSupplyRecord,
  StockKpiRecord,
  StockLedger,
  StockTransferCandidateRecord,
  MaterialRecord,
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
  V2GlassButton,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
} from '@/components'
import {
  createStockTransfer,
  createWarehouse,
  deleteWarehouse,
  loadStockConsumptionBaseline,
  loadStockIncomingSupplies,
  loadStockKpi,
  loadStockLedger,
  loadStockTransferCandidates,
  loadMaterials,
  loadWarehouse,
  loadWarehouses,
  updateStockReplenishment,
  updateWarehouse,
  updateWarehouseStatus,
} from '@/services/supply-chain'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

type Mode = 'warehouse' | 'stock' | 'transaction'

const route = useRoute()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const warehouses = ref<WarehouseRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const warehouseTotal = ref(0)
const ledger = ref<StockLedger | null>(null)
const kpi = ref<StockKpiRecord | null>(null)
const candidates = ref<StockTransferCandidateRecord[]>([])
const incoming = ref<StockIncomingSupplyRecord[]>([])
const baseline = ref<StockConsumptionBaselineRecord | null>(null)
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const warehouseDialog = ref(false)
const stockDialog = ref<'settings' | 'transfer' | null>(null)
const deleteTarget = ref<WarehouseRecord | null>(null)
const editingWarehouseId = ref('')
const filter = reactive({ warehouseId: '', materialId: '', keyword: '' })
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
let generation = 0

const mode = computed<Mode>(() =>
  route.path === '/inventory/stock'
    ? 'stock'
    : route.path === '/inventory/transaction'
      ? 'transaction'
      : 'warehouse',
)
const projectId = computed(() => workspace.selectedProjectId || '')
const title = computed(
  () => ({ warehouse: '仓库管理', stock: '库存台账', transaction: '出入库' })[mode.value],
)
const canWarehouseAdd = computed(() => session.hasPermission('inventory:warehouse:add'))
const canWarehouseEdit = computed(() => session.hasPermission('inventory:warehouse:edit'))
const canWarehouseDelete = computed(() => session.hasPermission('inventory:warehouse:delete'))
const canStockEdit = computed(() => session.hasPermission('inventory:stock:edit'))
const canTransfer = computed(
  () => canStockEdit.value && session.hasPermission('inventory:transaction:add'),
)
const warehousePageCount = computed(() => Math.max(1, Math.ceil(warehouseTotal.value / pageSize)))
const transactionPageCount = computed(() =>
  Math.max(1, Math.ceil(Number(ledger.value?.txns.total ?? 0) / pageSize)),
)
const warehouseOptions = computed(() => [
  { value: '', label: '全部仓库' },
  ...warehouses.value.map((item) => ({
    value: item.id,
    label: `${item.warehouseCode} · ${item.warehouseName}`,
  })),
])
const materialOptions = computed(() =>
  materials.value.map((item) => ({
    value: item.id,
    label: [item.materialCode, item.materialName, item.specification].filter(Boolean).join(' · '),
  })),
)
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

    const [warehousePage, materialPage] = await Promise.all([
      loadWarehouses(
        { pageNo: 1, pageSize: 100, projectId: projectId.value || undefined, status: 'ENABLE' },
        current.signal,
      ),
      loadMaterials({ pageNo: 1, pageSize: 100, status: 'ENABLE' }, current.signal),
    ])
    if (currentGeneration !== generation) return
    warehouses.value = warehousePage.records
    materials.value = materialPage.records
    if (!filter.materialId) {
      ledger.value = null
      kpi.value =
        mode.value === 'stock'
          ? await loadStockKpi(
              {
                warehouseId: filter.warehouseId || undefined,
                projectId: projectId.value || undefined,
              },
              current.signal,
            )
          : null
      return
    }
    const [nextLedger, nextKpi] = await Promise.all([
      loadStockLedger(
        {
          warehouseId: filter.warehouseId,
          materialId: filter.materialId,
          projectId: projectId.value || undefined,
          keyword: filter.keyword || undefined,
          pageNo: pageNo.value,
          pageSize,
        },
        current.signal,
      ),
      mode.value === 'stock'
        ? loadStockKpi(
            { warehouseId: filter.warehouseId, projectId: projectId.value || undefined },
            current.signal,
          )
        : Promise.resolve(null),
    ])
    if (currentGeneration !== generation) return
    ledger.value = nextLedger
    kpi.value = nextKpi
    candidates.value = []
    incoming.value = []
    baseline.value = null
    if (mode.value === 'stock' && filter.warehouseId && nextLedger.stock?.id) {
      const stockId = nextLedger.stock.id
      const [nextCandidates, nextIncoming, nextBaseline] = await Promise.all([
        loadStockTransferCandidates(stockId, current.signal),
        loadStockIncomingSupplies(stockId, current.signal),
        loadStockConsumptionBaseline(stockId, current.signal),
      ])
      if (currentGeneration !== generation) return
      candidates.value = nextCandidates
      incoming.value = nextIncoming
      baseline.value = nextBaseline
    }
  } catch (error) {
    if (current.signal.aborted) return
    errorMessage.value = errorText(error, '库存数据加载失败')
    showToast('error', '库存数据读取失败', errorMessage.value)
    ledger.value = null
  } finally {
    if (currentGeneration === generation) loading.value = false
  }
}

function search(): void {
  pageNo.value = 1
  void loadPage()
}

function changePage(next: number): void {
  pageNo.value = next
  void loadPage()
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
  if (!stock?.id || !stockDialog.value) return
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
    void loadPage()
  },
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <main class="inventory-workspace-page">
    <V2Card :title="title" :heading-level="1">
      <template #actions>
        <V2Button
          v-if="mode === 'warehouse' && canWarehouseAdd"
          size="small"
          @click="openCreateWarehouse"
        >
          新建仓库
        </V2Button>
        <div v-else-if="mode !== 'warehouse'" class="inventory-workspace-page__filter-grid">
          <V2Select
            v-model="filter.warehouseId"
            label="仓库"
            hide-label
            placeholder="选择仓库"
            allow-empty
            :options="warehouseOptions"
          />
          <V2Select
            v-model="filter.materialId"
            label="物料"
            hide-label
            placeholder="选择物料"
            allow-empty
            :options="materialOptions"
          />
          <V2Input
            v-model="filter.keyword"
            label="流水关键词"
            hide-label
            placeholder="输入流水关键词"
          />
          <V2Button size="small" :loading="loading" @click="search">查询库存</V2Button>
        </div>
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取" description="正在读取库存数据。" />

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
                <td>{{ item.updatedAt || '-' }}</td>
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
        <nav
          v-if="warehousePageCount > 1"
          class="inventory-workspace-page__pager"
          aria-label="仓库分页"
        >
          <V2Button variant="secondary" :disabled="pageNo <= 1" @click="changePage(pageNo - 1)"
            >上一页</V2Button
          >
          <span>第 {{ pageNo }} 页</span>
          <V2Button
            variant="secondary"
            :disabled="pageNo >= warehousePageCount"
            @click="changePage(pageNo + 1)"
            >下一页</V2Button
          >
        </nav>
      </V2Card>
    </template>

    <template v-else>
      <dl v-if="kpi" class="inventory-workspace-page__kpis" aria-label="库存指标">
        <div>
          <dt>仓库</dt>
          <dd>
            <strong>{{ kpi.warehouseCount }}</strong>
          </dd>
        </div>
        <div>
          <dt>低库存</dt>
          <dd>
            <strong>{{ kpi.lowStockCount }}</strong>
          </dd>
        </div>
        <div>
          <dt>物料种类</dt>
          <dd>
            <strong>{{ kpi.materialTypeCount }}</strong>
          </dd>
        </div>
        <div>
          <dt>入/出流水</dt>
          <dd>
            <strong>{{ kpi.txnInCount }} / {{ kpi.txnOutCount }}</strong>
          </dd>
        </div>
      </dl>
      <V2PageState
        v-if="!errorMessage && !filter.materialId"
        title="请输入物料"
        description="仓库可选择全部仓库或单个仓库。"
      />
      <V2PageState
        v-else-if="!errorMessage && !ledger?.stock"
        title="暂无库存"
        description="该仓库与物料尚无库存余额。"
      />
      <template v-else>
        <V2Card class="inventory-workspace-page__stock">
          <div class="inventory-workspace-page__card-head">
            <div>
              <p>{{ ledger.stock.materialCode || '物料编码缺失' }}</p>
              <h2>{{ ledger.stock.materialName || '未命名物料' }}</h2>
            </div>
            <V2Badge tone="info">{{ ledger.stock.availableQty }} {{ ledger.stock.unit }}</V2Badge>
          </div>
          <dl>
            <dt>库存价值</dt>
            <dd>{{ ledger.stock.inventoryValue }}</dd>
            <dt>平均成本</dt>
            <dd>{{ ledger.stock.averageUnitCost }}</dd>
            <dt>安全库存</dt>
            <dd>{{ ledger.stock.safetyStockQty }}</dd>
            <dt>补货目标/提前期</dt>
            <dd>
              {{ ledger.stock.replenishmentTargetQty || '-' }} /
              {{ ledger.stock.replenishmentLeadDays ?? '-' }}天
            </dd>
          </dl>
          <div
            v-if="mode === 'stock' && filter.warehouseId"
            class="inventory-workspace-page__actions"
          >
            <V2Button v-if="canStockEdit" variant="secondary" @click="openSettings"
              >维护阈值</V2Button
            >
            <V2Button v-if="canTransfer && candidates.length" @click="openTransfer"
              >库存调拨</V2Button
            >
          </div>
        </V2Card>
        <section
          v-if="mode === 'stock' && filter.warehouseId"
          class="inventory-workspace-page__facts"
        >
          <V2Card
            ><h2>在途供应</h2>
            <p v-if="!incoming.length">无在途</p>
            <p v-for="item in incoming" :key="item.orderId">
              {{ item.orderCode }} · {{ item.remainingQty }} · {{ item.deliveryDate || '-' }}
            </p></V2Card
          >
          <V2Card
            ><h2>历史净领料</h2>
            <p v-if="baseline">
              30日 {{ baseline.netIssued30 }}；90日 {{ baseline.netIssued90 }}；截止
              {{ baseline.cutoffAt }}
            </p>
            <p v-else>暂无基线</p></V2Card
          >
        </section>
        <V2Card class="inventory-workspace-page__ledger">
          <h2>{{ mode === 'transaction' ? '来源流水' : '库存流水' }}</h2>
          <div class="inventory-workspace-page__table-wrap">
            <table>
              <thead>
                <tr>
                  <th>时间</th>
                  <th>仓库</th>
                  <th>类型</th>
                  <th>数量</th>
                  <th>结余</th>
                  <th>金额</th>
                  <th>来源类型</th>
                  <th>来源业务编号</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in ledger.txns.records" :key="item.id">
                  <td>{{ item.createdTime || '-' }}</td>
                  <td>{{ item.warehouseName || '仓库名称缺失' }}</td>
                  <td>{{ transactionTypeLabel(item.txnType) }}</td>
                  <td>{{ item.quantity }}</td>
                  <td>{{ item.availableAfter }}</td>
                  <td>{{ item.amount }}</td>
                  <td>{{ sourceTypeLabel(item.sourceType) }}</td>
                  <td>{{ item.sourceCode || '来源编号缺失' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-if="!errorMessage && !ledger.txns.records.length"
            :heading-level="2"
            title="暂无流水"
            description="当前筛选范围无来源流水。"
          />
          <nav
            v-if="transactionPageCount > 1"
            class="inventory-workspace-page__pager"
            aria-label="流水分页"
          >
            <V2Button variant="secondary" :disabled="pageNo <= 1" @click="changePage(pageNo - 1)"
              >上一页</V2Button
            ><span>第 {{ pageNo }} 页</span
            ><V2Button
              variant="secondary"
              :disabled="pageNo >= transactionPageCount"
              @click="changePage(pageNo + 1)"
              >下一页</V2Button
            >
          </nav>
        </V2Card>
      </template>
    </template>

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
        /><V2Input v-model="warehouseForm.warehouseCode" label="仓库编码" required /><V2Input
          v-model="warehouseForm.warehouseName"
          label="仓库名称"
          required
        /><V2Select
          v-model="warehouseForm.status"
          label="状态"
          :options="[
            { value: 'ENABLE', label: '启用' },
            { value: 'DISABLE', label: '停用' },
          ]"
        /><V2Input v-model="warehouseForm.remark" label="备注" />
      </div>
      <template #footer
        ><V2GlassButton text="取消" :disabled="busy" :on-click="() => (warehouseDialog = false)" />
        ><V2Button :loading="busy" @click="saveWarehouse">保存</V2Button></template
      >
    </V2Dialog>

    <V2Dialog
      :open="Boolean(stockDialog)"
      :title="stockDialog === 'transfer' ? '库存调拨' : '维护补货阈值'"
      :close-on-backdrop="false"
      :close-disabled="busy"
      @close="stockDialog = null"
    >
      <div v-if="stockDialog === 'settings'" class="inventory-workspace-page__form">
        <V2Input v-model="stockForm.safetyStockQty" label="安全库存" required /><V2Input
          v-model="stockForm.replenishmentTargetQty"
          label="补货目标量"
        /><V2Input v-model="stockForm.replenishmentLeadDays" label="补货提前期（天）" />
      </div>
      <div v-else class="inventory-workspace-page__form">
        <V2Select
          v-model="stockForm.sourceStockId"
          label="来源库存"
          :options="candidateOptions"
          required
        /><V2Input v-model="stockForm.quantity" label="调拨数量" required /><V2Input
          v-model="stockForm.reason"
          label="调拨原因"
          required
        />
      </div>
      <template #footer
        ><V2Button variant="secondary" :disabled="busy" @click="stockDialog = null">取消</V2Button
        ><V2Button :loading="busy" @click="saveStockAction">提交并读取</V2Button></template
      >
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
  </main>
</template>

<style scoped>
.inventory-workspace-page {
  display: grid;
  gap: var(--v2-space-5);
  min-width: 0;
}
.inventory-workspace-page__card-head,
.inventory-workspace-page__actions,
.inventory-workspace-page__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-3);
}
.inventory-workspace-page__stock h2 {
  margin: 0;
}
.inventory-workspace-page__facts,
.inventory-workspace-page__kpis {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}
.inventory-workspace-page__kpis {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
  padding-block: var(--v2-space-3);
  border-block: var(--v2-border-width) solid var(--v2-color-border-subtle);
}
.inventory-workspace-page__kpis > div {
  display: grid;
  gap: var(--v2-space-2);
}
.inventory-workspace-page__kpis strong {
  font-size: var(--v2-font-size-28);
}
.inventory-workspace-page__filter-grid,
.inventory-workspace-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
  align-items: end;
}
.inventory-workspace-page__filter-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  width: min(68vw, 68rem);
}
.inventory-workspace-page dl:not(.inventory-workspace-page__kpis) {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: var(--v2-space-2) var(--v2-space-4);
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
  border-collapse: collapse;
  min-width: 47.5rem;
}
.inventory-workspace-page th,
.inventory-workspace-page td {
  padding: var(--v2-space-3);
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
  text-align: left;
}
.inventory-workspace-page__pager {
  justify-content: center;
  margin-top: var(--v2-space-4);
}
@media (max-width: 56.25rem) {
  .inventory-workspace-page__kpis,
  .inventory-workspace-page__filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 40rem) {
  .inventory-workspace-page__card-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .inventory-workspace-page__facts,
  .inventory-workspace-page__kpis,
  .inventory-workspace-page__filter-grid,
  .inventory-workspace-page__form {
    grid-template-columns: 1fr;
  }
  .inventory-workspace-page__actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }
  .inventory-workspace-page__table-wrap {
    max-width: 100%;
  }
}
</style>
