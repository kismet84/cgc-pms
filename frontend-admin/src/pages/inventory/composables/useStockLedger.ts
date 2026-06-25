import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import { getStockLedger, getStockKpi, getWarehouseList } from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { WarehouseVO, MatStockTxnVO, StockKpiVO } from '@/types/inventory'

// ---- 交易类型 ----
export const TXN_TYPE_LABEL: Record<string, string> = {
  IN: '入库',
  OUT: '出库',
  ADJUST: '调整',
}
export const TXN_TYPE_COLOR: Record<string, string> = {
  IN: 'success',
  OUT: 'error',
  ADJUST: 'warning',
}

export const SOURCE_TYPE_LABEL: Record<string, string> = {
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
export const SOURCE_TYPE_COLOR: Record<string, string> = {
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

export function getSourceTypeLabel(type: string | null | undefined): string {
  if (!type) return '-'
  return SOURCE_TYPE_LABEL[type] ?? type
}
export function getSourceTypeColor(type: string | null | undefined): string {
  if (!type) return 'default'
  return SOURCE_TYPE_COLOR[type] ?? 'default'
}

// ---- 列可见性 localStorage key ----
export const COLS_KEY = 'stock_ledger_cols'
export const defaultCols: Record<string, boolean> = {
  txnType: true,
  quantity: true,
  availableAfter: true,
  sourceType: true,
  sourceId: true,
  createdTime: true,
  ops: true,
}

export function useStockLedger() {
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
  const emptyKpi: StockKpiVO = {
    warehouseCount: 0,
    lowStockCount: 0,
    txnInCount: 0,
    txnOutCount: 0,
    materialTypeCount: 0,
  }

  const kpi = ref<StockKpiVO>({ ...emptyKpi })

  // ---- 仓库下拉 ----
  const warehouseList = ref<WarehouseVO[]>([])

  // ---- 列可见性 ----
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
      const res = await getStockKpi({
        warehouseId: filter.warehouseId,
        projectId: filter.projectId,
      })
      kpi.value = { ...emptyKpi, ...(res || {}) }
    } catch (e: unknown) {
      console.error(e)
      kpi.value = { ...emptyKpi }
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
    if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) return 0
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
    const inCount = Number(kpi.value.txnInCount) || 0
    const outCount = Number(kpi.value.txnOutCount) || 0
    const total = inCount + outCount || 1
    return {
      inPct: Math.round((inCount / total) * 100),
      outPct: Math.round((outCount / total) * 100),
    }
  })

  // ---- vxe-grid 列定义 ----
  const gridColumns = computed(() => [
    { type: 'seq' as const, title: '流水号', width: 92, align: 'center' as const },
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
      ? [{ field: 'sourceType', title: '来源类型', width: 120, slots: { default: 'sourceType' } }]
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
      ? [{ title: '操作', width: 84, align: 'center' as const, slots: { default: 'ops' } }]
      : []),
  ])

  return {
    // 状态
    filter,
    loading,
    stock,
    txnList,
    txnTotal,
    txnPageNo,
    txnPageSize,
    kpi,
    warehouseList,
    projects,
    materialList,
    // 列可见性
    colVisible,
    toggleCol,
    // 排序
    sortField,
    sortOrder,
    handleSortChange,
    // 详情
    detailVisible,
    detailItem,
    showDetail,
    closeDetail,
    // 数据获取
    fetchData,
    fetchKpi,
    fetchWarehouses,
    handleSearch,
    handleReset,
    onProjectChange,
    handleTxnPageChange,
    handleTxnPageSizeChange,
    // 辅助函数
    getWarehouseName,
    getMaterialName,
    fmtQty,
    kpiMax,
    kpiPct,
    lowStockWarn,
    inOutStats,
    gridColumns,
  }
}
