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
  SettingOutlined,
} from '@ant-design/icons-vue'
import { getStockLedger, getStockKpi, getWarehouseList } from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { WarehouseVO, MatStockTxnVO, StockKpiVO } from '@/types/inventory'
import type { SelectOption } from '@/types/ui'

const referenceStore = useReferenceStore()
const projects = computed(() => referenceStore.projects ?? [])
const materialList = computed(() => referenceStore.materials ?? [])

// ---- 筛选 ----
const filter = reactive({
  keyword: '',
  warehouseId: undefined as string | undefined,
  materialId: undefined as string | undefined,
  projectId: undefined as string | undefined,
})

// ---- 表格状态 ----
const loading = ref(false)
const stock = ref<{
  warehouseId: string
  materialId: string
  availableQty: string
  warehouseName?: string
  materialName?: string
  materialCode?: string
  unit?: string
} | null>(null)
const txnList = ref<MatStockTxnVO[]>([])
const txnTotal = ref(0)
const txnPageNo = ref(1)
const txnPageSize = ref(20)

// ---- KPI 状态 ----
const kpi = ref<StockKpiVO>({
  warehouseCount: 0,
  lowStockCount: 0,
  txnInCount: 0,
  txnOutCount: 0,
  materialTypeCount: 0,
})

// ---- 仓库下拉 ----
const warehouseList = ref<WarehouseVO[]>([])

// ---- 列可见性 ----
const COLS_KEY = 'stock_ledger_cols'
const defaultCols: Record<string, boolean> = {
  txnType: true,
  quantity: true,
  availableAfter: true,
  sourceType: true,
  sourceId: true,
  createdTime: true,
  ops: true,
}
let saved: Record<string, boolean> = defaultCols
try {
  const raw = localStorage.getItem(COLS_KEY)
  if (raw) saved = JSON.parse(raw)
} catch (e: unknown) {
  console.error(e)
  localStorage.removeItem(COLS_KEY)
}
const colVisible = reactive<Record<string, boolean>>({ ...defaultCols, ...saved })
function toggleCol(key: string) {
  colVisible[key] = !colVisible[key]
  localStorage.setItem(COLS_KEY, JSON.stringify(colVisible))
}

// ---- 排序 ----
const sortField = ref<string>('createdTime')
const sortOrder = ref<'asc' | 'desc'>('desc')
function handleSortChange({ field, order }: { field: string; order: 'asc' | 'desc' | null }) {
  sortField.value = field
  sortOrder.value = order || 'desc'
  txnPageNo.value = 1
  fetchData()
}

// ---- 详情抽屉 ----
const detailVisible = ref(false)
const detailItem = ref<MatStockTxnVO | null>(null)
function showDetail(row: MatStockTxnVO) {
  detailItem.value = row
  detailVisible.value = true
}
function closeDetail() {
  detailVisible.value = false
  detailItem.value = null
}

// ---- 防陈旧响应 ----
let fetchSeq = 0

async function fetchData() {
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
  const mySeq = ++fetchSeq
  loading.value = true
  try {
    const res = await getStockLedger({
      warehouseId: filter.warehouseId,
      materialId: filter.materialId,
      projectId: filter.projectId,
      keyword: filter.keyword || undefined,
      sortField: sortField.value,
      sortOrder: sortOrder.value,
      pageNo: txnPageNo.value,
      pageSize: txnPageSize.value,
    })
    if (mySeq !== fetchSeq) return
    stock.value = res.stock
    if (res.txns) {
      txnList.value = res.txns.records ?? []
      txnTotal.value = res.txns.total ?? 0
    } else {
      txnList.value = []
      txnTotal.value = 0
    }
  } catch (e: unknown) {
    if (mySeq !== fetchSeq) return
    console.error(e)
    stock.value = null
    txnList.value = []
    txnTotal.value = 0
    message.error('加载库存台账失败，请稍后重试')
  } finally {
    if (mySeq === fetchSeq) loading.value = false
  }
}

async function fetchKpi() {
  try {
    kpi.value = await getStockKpi({
      warehouseId: filter.warehouseId,
      projectId: filter.projectId,
    })
  } catch (e: unknown) {
    console.error(e)
    kpi.value = {
      warehouseCount: 0,
      lowStockCount: 0,
      txnInCount: 0,
      txnOutCount: 0,
      materialTypeCount: 0,
    }
  }
}

async function fetchWarehouses(projectId?: string) {
  try {
    const res = await getWarehouseList({ pageNo: 1, pageSize: 50, status: 'ENABLE', projectId })
    warehouseList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    warehouseList.value = []
  }
}

function handleSearch() {
  txnPageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.keyword = ''
  filter.warehouseId = undefined
  filter.materialId = undefined
  filter.projectId = undefined
  txnPageNo.value = 1
  stock.value = null
  txnList.value = []
  txnTotal.value = 0
}

function onProjectChange(projectId: string | undefined) {
  filter.warehouseId = undefined
  if (projectId) {
    fetchWarehouses(projectId)
  } else {
    fetchWarehouses()
  }
}

function handleTxnPageChange(page: number) {
  txnPageNo.value = page
  fetchData()
}

function handleTxnPageSizeChange(_cur: number, size: number) {
  txnPageSize.value = size
  txnPageNo.value = 1
  fetchData()
}

// ---- 辅助函数 ----
function getWarehouseName(id: string): string {
  return warehouseList.value.find((w) => w.id === id)?.warehouseName ?? id
}

function getMaterialName(id: string): string {
  return materialList.value.find((m) => m.id === id)?.materialName ?? id
}

function fmtQty(val: string | number): string {
  const n = typeof val === 'string' ? parseFloat(val) : val
  if (isNaN(n)) return '0.0000'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
}

// ---- KPI 计算 ----
const kpiMax = computed(() => ({
  txnInCount: Math.max(kpi.value.txnInCount, 1),
  txnOutCount: Math.max(kpi.value.txnOutCount, 1),
}))
function kpiPct(value: number, max: number): number {
  return Math.min(Math.round((value / max) * 100), 100)
}

// ---- 右侧分析面板 ----
const lowStockWarn = computed(() => {
  const items: { name: string; qty: number }[] = []
  if (
    stock.value &&
    Number(stock.value.availableQty) < 10 &&
    Number(stock.value.availableQty) > 0
  ) {
    items.push({
      name: stock.value.materialName || getMaterialName(stock.value.materialId),
      qty: Number(stock.value.availableQty),
    })
  }
  return items
})

const inOutStats = computed(() => {
  const total = kpi.value.txnInCount + kpi.value.txnOutCount || 1
  return {
    inPct: Math.round((kpi.value.txnInCount / total) * 100),
    outPct: Math.round((kpi.value.txnOutCount / total) * 100),
  }
})

// ---- 交易类型 ----
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

const SOURCE_TYPE_LABEL: Record<string, string> = {
  PURCHASE_IN: '采购入库',
  PURCHASE_RETURN: '采购退货',
  MATERIAL_OUT: '领料出库',
  MATERIAL_RETURN: '退料入库',
  INVENTORY_IN: '盘点入库',
  INVENTORY_OUT: '盘点出库',
  ADJUST: '库存调整',
  TRANSFER_IN: '调拨入库',
  TRANSFER_OUT: '调拨出库',
  INIT: '期初导入',
}
const SOURCE_TYPE_COLOR: Record<string, string> = {
  PURCHASE_IN: 'success',
  PURCHASE_RETURN: 'warning',
  MATERIAL_OUT: 'error',
  MATERIAL_RETURN: 'blue',
  INVENTORY_IN: 'processing',
  INVENTORY_OUT: 'warning',
  ADJUST: 'orange',
  TRANSFER_IN: 'cyan',
  TRANSFER_OUT: 'purple',
  INIT: 'default',
}
function getSourceTypeLabel(type: string | null | undefined): string {
  if (!type) return '-'
  return SOURCE_TYPE_LABEL[type] ?? type
}
function getSourceTypeColor(type: string | null | undefined): string {
  if (!type) return 'default'
  return SOURCE_TYPE_COLOR[type] ?? 'default'
}

// ---- vxe-grid 列定义 ----
const gridColumns = computed(() => [
  { type: 'seq' as const, title: '流水号', width: 80, align: 'center' as const },
  ...(colVisible.txnType
    ? [{ field: 'txnType', title: '类型', width: 80, slots: { default: 'txnType' } }]
    : []),
  ...(colVisible.quantity
    ? [
        {
          field: 'quantity',
          title: '变动量',
          width: 120,
          align: 'right' as const,
          sortable: true,
          slots: { default: 'quantity' },
        },
      ]
    : []),
  ...(colVisible.availableAfter
    ? [
        {
          field: 'availableAfter',
          title: '变动后余量',
          width: 130,
          align: 'right' as const,
          slots: { default: 'availableAfter' },
        },
      ]
    : []),
  ...(colVisible.sourceType
    ? [{ field: 'sourceType', title: '来源类型', width: 110, slots: { default: 'sourceType' } }]
    : []),
  ...(colVisible.sourceId
    ? [
        {
          field: 'sourceId',
          title: '关联单据',
          width: 130,
          ellipsis: true,
          slots: { default: 'sourceId' },
        },
      ]
    : []),
  ...(colVisible.createdTime
    ? [
        {
          field: 'createdTime',
          title: '操作时间',
          width: 150,
          sortable: true,
        },
      ]
    : []),
  ...(colVisible.ops
    ? [{ title: '操作', width: 70, align: 'center' as const, slots: { default: 'ops' } }]
    : []),
])

// ---- 移动端检测 ----
const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  await referenceStore.fetchProjects()
  await referenceStore.fetchMaterials()
  fetchWarehouses()
  fetchKpi()
})
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>库存台账</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索流水编号、来源单号…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-select
        v-model:value="filter.warehouseId"
        placeholder="请选择仓库"
        allow-clear
        size="large"
        style="min-width: 180px"
        show-search
        :filter-option="
          (input: string, option: SelectOption) => option.label?.toLowerCase().includes(input.toLowerCase())
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
        style="min-width: 220px"
        show-search
        :filter-option="
          (input: string, option: SelectOption) => option.label?.toLowerCase().includes(input.toLowerCase())
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
        <!-- KPI 横条：桌面 -->
        <div v-if="!isMobile" class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">仓库数量</span>
            <span class="lg-kpi-card-value">{{ kpi.warehouseCount }} <small>个</small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: var(--kpi-total)"></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">物料种类</span>
            <span class="lg-kpi-card-value">{{ kpi.materialTypeCount }} <small>种</small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: var(--kpi-amount)"></span
            ></span>
          </div>
          <div class="lg-kpi-card is-warn" v-if="kpi.lowStockCount > 0" :key="'warn'">
            <span class="lg-kpi-card-label">低库存物料</span>
            <span class="lg-kpi-card-value">{{ kpi.lowStockCount }} <small>种</small></span>
            <span class="lg-kpi-card-bar"
              ><span
                :style="{
                  width: kpiPct(kpi.lowStockCount, Math.max(kpi.materialTypeCount, 1)) + '%',
                  background: 'var(--kpi-overdue)',
                }"
              ></span
            ></span>
          </div>
          <div class="lg-kpi-card" v-else :key="'normal'">
            <span class="lg-kpi-card-label">低库存物料</span>
            <span class="lg-kpi-card-value">0 <small>种</small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 0%; background: var(--kpi-overdue)"></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">入库记录</span>
            <span class="lg-kpi-card-value">{{ kpi.txnInCount }} <small>条</small></span>
            <span class="lg-kpi-card-bar"
              ><span
                :style="{
                  width: kpiPct(kpi.txnInCount, kpiMax.txnInCount) + '%',
                  background: 'var(--kpi-paid)',
                }"
              ></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">出库记录</span>
            <span class="lg-kpi-card-value">{{ kpi.txnOutCount }} <small>条</small></span>
            <span class="lg-kpi-card-bar"
              ><span
                :style="{
                  width: kpiPct(kpi.txnOutCount, kpiMax.txnOutCount) + '%',
                  background: 'var(--kpi-unpaid)',
                }"
              ></span
            ></span>
          </div>
        </div>

        <!-- KPI 移动端：单卡片 -->
        <div v-else class="lg-kpi-single">
          <div
            class="lg-kpi-single-row"
            v-for="item in [
              {
                icon: InboxOutlined,
                bg: 'var(--kpi-total)',
                label: '仓库数量',
                value: kpi.warehouseCount,
                unit: '个',
              },
              {
                icon: InboxOutlined,
                bg: 'var(--kpi-amount)',
                label: '物料种类',
                value: kpi.materialTypeCount,
                unit: '种',
              },
              {
                icon: AlertOutlined,
                bg: 'var(--kpi-overdue)',
                label: '低库存物料',
                value: kpi.lowStockCount,
                unit: '种',
              },
              {
                icon: RiseOutlined,
                bg: 'var(--kpi-paid)',
                label: '入库记录',
                value: kpi.txnInCount,
                unit: '条',
              },
              {
                icon: FallOutlined,
                bg: 'var(--kpi-unpaid)',
                label: '出库记录',
                value: kpi.txnOutCount,
                unit: '条',
              },
            ]"
            :key="item.label"
          >
            <div class="lg-kpi-single-icon" :style="{ background: item.bg }">
              <component :is="item.icon" />
            </div>
            <span class="lg-kpi-single-label">{{ item.label }}</span>
            <span class="lg-kpi-single-value"
              >{{ item.value }} <small>{{ item.unit }}</small></span
            >
          </div>
        </div>

        <!-- Stock Balance Card（库存台账特有） -->
        <div v-if="stock" class="lg-panel" style="margin-bottom: 12px">
          <div
            style="display: flex; gap: 40px; align-items: center; flex-wrap: wrap; padding: 4px 0"
          >
            <div>
              <span style="font-size: 13px; color: #6b7280">仓库：</span>
              <span style="font-weight: 600">
                {{ stock.warehouseName || getWarehouseName(stock.warehouseId) }}
              </span>
            </div>
            <div>
              <span style="font-size: 13px; color: #6b7280">物料：</span>
              <span style="font-weight: 600">
                {{ stock.materialName || getMaterialName(stock.materialId) }}
              </span>
            </div>
            <div>
              <span style="font-size: 13px; color: #6b7280">当前库存：</span>
              <span style="font-weight: 700; font-size: 18px; color: #1677ff">
                {{ fmtQty(stock.availableQty) }}
              </span>
              <span style="font-size: 13px; color: #6b7280; margin-left: 4px">{{
                stock.unit
              }}</span>
            </div>
          </div>
        </div>

        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button @click="handleSearch">
              <template #icon><ReloadOutlined /></template>
            </a-button>
            <a-dropdown v-if="!isMobile">
              <a-button size="small">
                <template #icon><SettingOutlined /></template>
                列设置
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item v-for="(_, key) in defaultCols" :key="key" @click="toggleCol(key)">
                    <a-checkbox :checked="colVisible[key]">
                      {{
                        {
                          txnType: '类型',
                          quantity: '变动量',
                          availableAfter: '变动后余量',
                          sourceType: '来源类型',
                          sourceId: '关联单据',
                          createdTime: '操作时间',
                          ops: '操作',
                        }[key]
                      }}
                    </a-checkbox>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div class="lg-toolbar-right">
            <a-select
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              style="width: 160px"
              size="small"
              @change="onProjectChange"
            >
              <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
          </div>
        </div>

        <!-- 表格：桌面 -->
        <div v-if="!isMobile" class="lg-table-wrap">
          <div style="padding: 12px 14px 0 14px; font-weight: 600; font-size: 14px; color: #374151">
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
            @sort-change="handleSortChange"
          >
            <template #txnType="{ row }">
              <a-tag :color="TXN_TYPE_COLOR[row.txnType]">
                {{ TXN_TYPE_LABEL[row.txnType] ?? row.txnType }}
              </a-tag>
            </template>
            <template #quantity="{ row }">
              <span
                class="lg-money"
                :style="{
                  color: row.txnType === 'OUT' ? '#ef4444' : '#16a34a',
                }"
              >
                {{ row.txnType === 'OUT' ? '−' : '+' }}{{ fmtQty(row.quantity) }}
              </span>
            </template>
            <template #availableAfter="{ row }">
              <span
                class="lg-money"
                :style="{
                  color: Number(row.availableAfter) < 10 ? '#ef4444' : 'var(--text)',
                  fontWeight: Number(row.availableAfter) < 10 ? 700 : 600,
                }"
              >
                {{ fmtQty(row.availableAfter) }}
              </span>
            </template>
            <template #sourceType="{ row }">
              <a-tag :color="getSourceTypeColor(row.sourceType)" size="small">
                {{ getSourceTypeLabel(row.sourceType) }}
              </a-tag>
            </template>
            <template #sourceId="{ row }">
              <span v-if="row.sourceId" class="lg-link">
                {{ row.sourceId }}
              </span>
              <span v-else style="color: #9ca3af">-</span>
            </template>
            <template #ops="{ row }">
              <a class="lg-link" @click="showDetail(row)">详情</a>
            </template>
          </vxe-grid>
        </div>

        <!-- 移动端卡片列表 -->
        <div v-else class="lg-card-list">
          <div v-if="loading" class="lg-card-list-loading">
            <a-spin size="large" />
          </div>
          <div v-else-if="!txnList.length" class="lg-card-list-empty">
            <a-empty />
          </div>
          <div v-for="row in txnList" :key="row.id" class="lg-card-item">
            <div class="lg-card-item-head">
              <span class="lg-card-code">
                <a-tag :color="TXN_TYPE_COLOR[row.txnType]">
                  {{ TXN_TYPE_LABEL[row.txnType] ?? row.txnType }}
                </a-tag>
              </span>
              <span class="lg-card-head-right">
                <a-tag
                  v-if="colVisible.sourceType"
                  :color="getSourceTypeColor(row.sourceType)"
                  size="small"
                >
                  {{ getSourceTypeLabel(row.sourceType) }}
                </a-tag>
              </span>
            </div>
            <div class="lg-card-item-body">
              <div v-if="colVisible.quantity" class="lg-card-field">
                <span class="lg-card-label">变动量</span>
                <span
                  class="lg-card-value lg-card-money"
                  :style="{ color: row.txnType === 'OUT' ? '#ef4444' : '#16a34a' }"
                >
                  {{ row.txnType === 'OUT' ? '−' : '+' }}{{ fmtQty(row.quantity) }}
                </span>
              </div>
              <div v-if="colVisible.availableAfter" class="lg-card-field">
                <span class="lg-card-label">变动后余量</span>
                <span
                  class="lg-card-value lg-card-money"
                  :style="{ color: Number(row.availableAfter) < 10 ? '#ef4444' : 'var(--text)' }"
                >
                  {{ fmtQty(row.availableAfter) }}
                </span>
              </div>
              <div class="lg-card-field-row">
                <div v-if="colVisible.sourceId" class="lg-card-field">
                  <span class="lg-card-label">关联单据</span>
                  <span class="lg-card-value">{{ row.sourceId || '-' }}</span>
                </div>
                <div v-if="colVisible.createdTime" class="lg-card-field">
                  <span class="lg-card-label">操作时间</span>
                  <span class="lg-card-value">{{ row.createdTime || '-' }}</span>
                </div>
              </div>
            </div>
            <div class="lg-card-item-foot">
              <a-space :size="4">
                <a-button size="small" type="link" @click="showDetail(row)">详情</a-button>
              </a-space>
            </div>
          </div>
        </div>

        <!-- 分页 -->
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

        <!-- 流水详情 Drawer -->
        <a-drawer
          :open="detailVisible"
          title="流水详情"
          placement="right"
          :width="480"
          @close="closeDetail"
        >
          <template v-if="detailItem">
            <a-descriptions :column="2" size="small" bordered>
              <a-descriptions-item label="流水编号">{{ detailItem.id }}</a-descriptions-item>
              <a-descriptions-item label="交易类型">
                <a-tag :color="TXN_TYPE_COLOR[detailItem.txnType]">
                  {{ TXN_TYPE_LABEL[detailItem.txnType] ?? detailItem.txnType }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="仓库名称">
                {{ detailItem.warehouseName || getWarehouseName(detailItem.warehouseId) }}
              </a-descriptions-item>
              <a-descriptions-item label="物料名称">
                {{ detailItem.materialName || getMaterialName(detailItem.materialId) }}
              </a-descriptions-item>
              <a-descriptions-item label="变动量">
                <span
                  :style="{
                    color: detailItem.txnType === 'OUT' ? '#ef4444' : '#16a34a',
                    fontWeight: 600,
                  }"
                >
                  {{ detailItem.txnType === 'OUT' ? '−' : '+' }}{{ fmtQty(detailItem.quantity) }}
                </span>
              </a-descriptions-item>
              <a-descriptions-item label="变动后余量">
                <span style="font-weight: 600">{{ fmtQty(detailItem.availableAfter) }}</span>
              </a-descriptions-item>
              <a-descriptions-item label="来源类型">
                <a-tag :color="getSourceTypeColor(detailItem.sourceType)" size="small">
                  {{ getSourceTypeLabel(detailItem.sourceType) }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="来源单号">
                {{ detailItem.sourceId || '-' }}
              </a-descriptions-item>
              <a-descriptions-item label="操作时间" :span="2">
                {{ detailItem.createdTime || '-' }}
              </a-descriptions-item>
            </a-descriptions>
          </template>
        </a-drawer>
      </div>

      <!-- 右侧分析面板 -->
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">低库存预警</div>
          <div class="lg-type-list">
            <div v-for="w in lowStockWarn" :key="w.name" class="lg-type-row">
              <span
                class="lg-type-dot"
                :style="{ background: w.qty < 5 ? '#ef4444' : 'var(--kpi-overdue)' }"
              ></span>
              <span class="lg-type-label">{{ w.name }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{
                    width: Math.min(100, (w.qty / 10) * 100) + '%',
                    background: w.qty < 5 ? '#ef4444' : 'var(--kpi-overdue)',
                  }"
                ></span>
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
                <span
                  class="lg-type-bar"
                  :style="{
                    width: inOutStats.inPct + '%',
                    background: '#22c55e',
                  }"
                ></span>
              </span>
              <span class="lg-type-num" style="color: #22c55e">{{ kpi.txnInCount }}</span>
              <span class="lg-type-pct">{{ inOutStats.inPct }}%</span>
            </div>
            <div class="lg-type-row">
              <span class="lg-type-dot" style="background: #ef4444"></span>
              <span class="lg-type-label">出库次数</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{
                    width: inOutStats.outPct + '%',
                    background: '#ef4444',
                  }"
                ></span>
              </span>
              <span class="lg-type-num" style="color: #ef4444">{{ kpi.txnOutCount }}</span>
              <span class="lg-type-pct">{{ inOutStats.outPct }}%</span>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.cl-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
</style>
