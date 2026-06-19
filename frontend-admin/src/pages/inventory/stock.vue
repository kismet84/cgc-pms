<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  SearchOutlined,
  ReloadOutlined,
  InboxOutlined,
  FallOutlined,
  RiseOutlined,
  AlertOutlined,
} from '@ant-design/icons-vue'
import { getStockLedger } from '@/api/modules/inventory'
import { getWarehouseList } from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { WarehouseVO, MatStockVO, MatStockTxnVO } from '@/types/inventory'

const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() { isMobile.value = window.innerWidth < MOBILE_BP }

const filter = reactive({
  warehouseId: undefined as string | undefined,
  materialId: undefined as string | undefined,
})

const loading = ref(false)
const stock = ref<MatStockVO | null>(null)
const txnList = ref<MatStockTxnVO[]>([])
const txnTotal = ref(0)
const txnPageNo = ref(1)
const txnPageSize = ref(20)

const warehouseList = ref<WarehouseVO[]>([])
const referenceStore = useReferenceStore()
const materialList = computed(() => referenceStore.materials ?? [])

const TXN_TYPE_LABEL: Record<string, string> = {
  IN: '入库',
  OUT: '出库',
  ADJUST: '调整',
}
const TXN_TYPE_COLOR: Record<string, string> = {
  IN: 'success',
  OUT: 'error',
  ADJUST: 'warning',
}

const gridColumns = computed(() => [
  { field: 'id', title: '流水编号', width: 100 },
  { field: 'txnType', title: '类型', width: 70, slots: { default: 'txnType' } },
  { field: 'quantity', title: '变动量', width: 100, slots: { default: 'quantity' } },
  { field: 'availableAfter', title: '变动后余量', width: 120, slots: { default: 'availableAfter' } },
  { field: 'sourceType', title: '来源类型', width: 100 },
  { field: 'createdTime', title: '操作时间', width: 160 },
])

async function fetchLedger() {
  if (!filter.warehouseId) {
    stock.value = null
    txnList.value = []
    txnTotal.value = 0
    return
  }
  if (!filter.materialId) {
    message.warning('请先选择物料')
    return
  }
  loading.value = true
  try {
    const res = await getStockLedger({
      warehouseId: filter.warehouseId,
      materialId: filter.materialId,
      pageNo: txnPageNo.value,
      pageSize: txnPageSize.value,
    })
    stock.value = res.stock
    if (res.txns) {
      txnList.value = res.txns.records ?? []
      txnTotal.value = res.txns.total ?? 0
    } else {
      txnList.value = []
      txnTotal.value = 0
    }
  } catch (e: unknown) {
    console.error(e)
    stock.value = null
    txnList.value = []
    txnTotal.value = 0
    message.error('加载库存台账失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchWarehouses() {
  try {
    const res = await getWarehouseList({ pageNo: 1, pageSize: 50, status: 'ENABLE' })
    warehouseList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    warehouseList.value = []
  }
}

function handleSearch() {
  txnPageNo.value = 1
  fetchLedger()
}

function handleReset() {
  filter.warehouseId = undefined
  filter.materialId = undefined
  txnPageNo.value = 1
  stock.value = null
  txnList.value = []
  txnTotal.value = 0
}

function handleTxnPageChange(page: number) {
  txnPageNo.value = page
  fetchLedger()
}

function handleTxnPageSizeChange(_cur: number, size: number) {
  txnPageSize.value = size
  txnPageNo.value = 1
  fetchLedger()
}

function getWarehouseName(id: string): string {
  return warehouseList.value.find((w) => w.id === id)?.warehouseName ?? id
}

function getMaterialName(id: string): string {
  return materialList.value.find((m) => m.id === id)?.materialName ?? id
}

const kpiStockValue = computed(() =>
  stock.value ? Number(stock.value.availableQty || 0).toLocaleString() : '0',
)
const kpiTxnIn = computed(() => txnList.value.filter((t) => t.txnType === 'IN').length)
const kpiTxnOut = computed(() => txnList.value.filter((t) => t.txnType === 'OUT').length)
const lowStockWarn = computed(() =>
  stock.value && Number(stock.value.availableQty) < 10
    ? [{ name: getMaterialName(stock.value.materialId), qty: stock.value.availableQty }]
    : [],
)

onMounted(() => {
  window.addEventListener('resize', onResize)
  fetchWarehouses()
  referenceStore.fetchMaterials()
})
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="lg-page app-page">
    <!-- Page head -->
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>库存台账</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-select
        v-model:value="filter.warehouseId"
        placeholder="请选择仓库"
        allow-clear
        size="large"
        style="min-width: 200px"
        show-search
        :filter-option="
          (input: string, option: any) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
      >
        <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
          {{ w.warehouseName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.materialId"
        placeholder="选择物料"
        allow-clear
        size="large"
        style="min-width: 240px"
        show-search
        :filter-option="
          (input: string, option: any) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
      >
        <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
          {{ m.materialName }} <span style="color: #9ca3af">({{ m.materialCode }})</span>
        </a-select-option>
      </a-select>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <!-- 左列 -->
      <div class="lg-left">
        <!-- KPI strip -->
        <div v-if="!isMobile" class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">当前库存量</span>
            <span class="lg-kpi-card-value">{{ kpiStockValue }} <small>{{ stock?.unit || '' }}</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-total)"></span></span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">低库存物料</span>
            <span class="lg-kpi-card-value">{{ lowStockWarn.length }} <small>种</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: lowStockWarn.length ? '100%' : '0%', background: 'var(--kpi-overdue)' }"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">入库记录</span>
            <span class="lg-kpi-card-value">{{ kpiTxnIn }} <small>条</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-paid)"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">出库记录</span>
            <span class="lg-kpi-card-value">{{ kpiTxnOut }} <small>条</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-unpaid)"></span></span>
          </div>
        </div>

        <!-- KPI 移动端 -->
        <div v-else class="lg-kpi-single">
          <div
            class="lg-kpi-single-row"
            v-for="item in [
              { icon: InboxOutlined, bg: 'var(--kpi-total)', label: '当前库存量', value: kpiStockValue, unit: stock?.unit || '' },
              { icon: AlertOutlined, bg: 'var(--kpi-overdue)', label: '低库存物料', value: lowStockWarn.length, unit: '种' },
              { icon: RiseOutlined, bg: 'var(--kpi-paid)', label: '入库记录', value: kpiTxnIn, unit: '条' },
              { icon: FallOutlined, bg: 'var(--kpi-unpaid)', label: '出库记录', value: kpiTxnOut, unit: '条' },
            ]"
            :key="item.label"
          >
            <div class="lg-kpi-single-icon" :style="{ background: item.bg }">
              <component :is="item.icon" />
            </div>
            <span class="lg-kpi-single-label">{{ item.label }}</span>
            <span class="lg-kpi-single-value">{{ item.value }} <small>{{ item.unit }}</small></span>
          </div>
        </div>

        <!-- Stock Balance Card -->
        <div v-if="stock" class="lg-panel" style="margin-bottom: 12px">
          <div style="display: flex; gap: 40px; align-items: center; flex-wrap: wrap; padding: 4px 0">
            <div>
              <span style="font-size: 13px; color: #6b7280">仓库：</span>
              <span style="font-weight: 600">{{ getWarehouseName(stock.warehouseId) }}</span>
            </div>
            <div>
              <span style="font-size: 13px; color: #6b7280">物料：</span>
              <span style="font-weight: 600">{{ getMaterialName(stock.materialId) }}</span>
            </div>
            <div>
              <span style="font-size: 13px; color: #6b7280">当前库存：</span>
              <span style="font-weight: 700; font-size: 18px; color: #1677ff">
                {{
                  Number(stock.availableQty).toLocaleString('zh-CN', { minimumFractionDigits: 4 })
                }}
              </span>
              <span style="font-size: 13px; color: #6b7280; margin-left: 4px">{{
                stock.unit
              }}</span>
            </div>
          </div>
        </div>
        <div
          v-else-if="filter.warehouseId"
          class="lg-panel"
          style="margin-bottom: 12px; color: #9ca3af; padding: 16px 22px"
        >
          该仓库暂无选中物料库存记录
        </div>

        <!-- Transaction Ledger Table -->
        <div class="lg-table-wrap">
          <div style="padding: 0 0 12px; font-weight: 600; font-size: 14px; color: #374151">
            出入库流水
          </div>
          <vxe-grid
            :data="txnList"
            :columns="gridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            stripe
            border="inner"
            size="small"
            max-height="480"
          >
            <template #txnType="{ row }">
              <a-tag :color="TXN_TYPE_COLOR[row.txnType]">
                {{ TXN_TYPE_LABEL[row.txnType] ?? row.txnType }}
              </a-tag>
            </template>
            <template #quantity="{ row }">
              <span
                :style="{
                  color: row.txnType === 'OUT' ? '#ef4444' : '#16a34a',
                  fontWeight: 600,
                }"
              >
                {{ row.txnType === 'OUT' ? '-' : '+'
                }}{{
                  Number(row.quantity).toLocaleString('zh-CN', { minimumFractionDigits: 4 })
                }}
              </span>
            </template>
            <template #availableAfter="{ row }">
              <span style="font-weight: 600">
                {{
                  Number(row.availableAfter).toLocaleString('zh-CN', {
                    minimumFractionDigits: 4,
                  })
                }}
              </span>
            </template>
          </vxe-grid>
        </div>

        <!-- Pagination -->
        <div class="lg-pagination">
          <span class="lg-total">共 {{ txnTotal }} 条流水</span>
          <a-pagination
            v-model:current="txnPageNo"
            v-model:page-size="txnPageSize"
            :total="txnTotal"
            :page-size-options="['10', '20', '50', '100']"
            show-size-changer
            show-quick-jumper
            @change="handleTxnPageChange"
            @show-size-change="handleTxnPageSizeChange"
          />
        </div>
      </div>

      <!-- 右侧分析面板 -->
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">低库存预警</div>
          <div class="lg-type-list">
            <div v-for="w in lowStockWarn" :key="w.name" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: w.qty < 5 ? '#ef4444' : 'var(--kpi-overdue)' }"></span>
              <span class="lg-type-label">{{ w.name }}</span>
              <span class="lg-type-bar-wrap">
                <span class="lg-type-bar" :style="{ width: Math.min(100, (Number(w.qty) / 10) * 100) + '%', background: w.qty < 5 ? '#ef4444' : 'var(--kpi-overdue)' }"></span>
              </span>
              <span class="lg-type-num" style="color: #ef4444">{{ w.qty }}</span>
              <span class="lg-type-pct"></span>
            </div>
            <div v-if="lowStockWarn.length === 0" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: 'var(--kpi-paid)' }"></span>
              <span class="lg-type-label" style="grid-column: 2 / span 4">库存正常</span>
            </div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">出入库统计</div>
          <div class="lg-type-list">
            <div class="lg-type-row">
              <span class="lg-type-dot" style="background: #22c55e"></span>
              <span class="lg-type-label">入库次数</span>
              <span class="lg-type-bar-wrap">
                <span class="lg-type-bar" :style="{ width: kpiTxnIn + kpiTxnOut > 0 ? Math.round((kpiTxnIn / (kpiTxnIn + kpiTxnOut)) * 100) + '%' : '0%', background: '#22c55e' }"></span>
              </span>
              <span class="lg-type-num" style="color: #22c55e">{{ kpiTxnIn }}</span>
              <span class="lg-type-pct">{{ kpiTxnIn + kpiTxnOut > 0 ? Math.round((kpiTxnIn / (kpiTxnIn + kpiTxnOut)) * 100) : 0 }}%</span>
            </div>
            <div class="lg-type-row">
              <span class="lg-type-dot" style="background: #ef4444"></span>
              <span class="lg-type-label">出库次数</span>
              <span class="lg-type-bar-wrap">
                <span class="lg-type-bar" :style="{ width: kpiTxnIn + kpiTxnOut > 0 ? Math.round((kpiTxnOut / (kpiTxnIn + kpiTxnOut)) * 100) + '%' : '0%', background: '#ef4444' }"></span>
              </span>
              <span class="lg-type-num" style="color: #ef4444">{{ kpiTxnOut }}</span>
              <span class="lg-type-pct">{{ kpiTxnIn + kpiTxnOut > 0 ? Math.round((kpiTxnOut / (kpiTxnIn + kpiTxnOut)) * 100) : 0 }}%</span>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
</style>
