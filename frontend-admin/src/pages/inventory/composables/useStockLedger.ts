import { ref, reactive, computed } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  getStockLedger,
  getStockKpi,
  getWarehouseList,
  updateStockSafetyThreshold,
} from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import { readPositiveIntQuery, readStringQuery, replaceListQuery } from '@/composables/listPageQuery'
import type { WarehouseVO, MatStockTxnVO, StockKpiVO, MatStockVO } from '@/types/inventory'
import { useColumnSettings } from '@/composables/useColumnSettings'

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
  MAT_RECEIPT: '材料验收入库',
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
  MAT_RECEIPT: 'success',
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

export function useStockLedger({
  route,
  router,
}: {
  route: RouteLocationNormalizedLoaded
  router: Router
}) {
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
  const hasLoaded = ref(false)
  const listError = ref<string | null>(null)
  const stock = ref<MatStockVO | null>(null)
  const safetyThresholdDraft = ref<number | null>(null)
  const thresholdSaving = ref(false)
  const txnList = ref<MatStockTxnVO[]>([])
  const txnTotal = ref(0)
  const txnPageNo = ref(1)
  const txnPageSize = ref(20)
  const queryReady = ref(false)
  const hasActiveFilters = computed(
    () => Boolean(filter.projectId || filter.warehouseId || filter.materialId || filter.keyword),
  )
  const hasRequiredFilters = computed(() => Boolean(filter.warehouseId && filter.materialId))

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

  function resetTxnState() {
    stock.value = null
    safetyThresholdDraft.value = null
    txnList.value = []
    txnTotal.value = 0
  }

  async function fetchData() {
    listError.value = null
    await syncRouteQuery()
    if (!filter.warehouseId) {
      resetTxnState()
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
      safetyThresholdDraft.value = res.stock ? Number(res.stock.safetyStockQty) : null
      if (res.txns) {
        txnList.value = res.txns.records ?? []
        txnTotal.value = Number(res.txns.total ?? 0)
      } else {
        txnList.value = []
        txnTotal.value = 0
      }
    } catch (e: unknown) {
      if (mySeq !== fetchSeq) return
      console.error(e)
      resetTxnState()
      listError.value = '请检查筛选条件或网络状态后重试。'
      message.error('加载库存台账失败，请稍后重试')
    } finally {
      if (mySeq === fetchSeq) {
        hasLoaded.value = true
        loading.value = false
      }
    }
  }

  function hydrateFromRouteQuery() {
    filter.projectId = readStringQuery(route.query.projectId)
    filter.warehouseId = readStringQuery(route.query.warehouseId)
    filter.materialId = readStringQuery(route.query.materialId)
    filter.keyword = readStringQuery(route.query.keyword) ?? ''
    txnPageNo.value = readPositiveIntQuery(route.query.pageNo, 1)
    txnPageSize.value = readPositiveIntQuery(route.query.pageSize, 20)
    queryReady.value = true
  }

  async function syncRouteQuery() {
    if (!queryReady.value) return
    const nextQuery = replaceListQuery(
      route.query,
      {
        projectId: filter.projectId,
        warehouseId: filter.warehouseId,
        materialId: filter.materialId,
        keyword: filter.keyword || undefined,
        pageNo: txnPageNo.value,
        pageSize: txnPageSize.value,
      },
      ['projectId', 'warehouseId', 'materialId', 'keyword', 'pageNo', 'pageSize'],
    )
    await router.replace({ path: route.path, query: nextQuery })
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
    fetchKpi()
  }

  function handleReset() {
    filter.keyword = ''
    filter.warehouseId = undefined
    filter.materialId = undefined
    filter.projectId = undefined
    txnPageNo.value = 1
    hasLoaded.value = false
    listError.value = null
    resetTxnState()
    fetchWarehouses()
    fetchKpi()
    void syncRouteQuery()
  }

  function onProjectChange(projectId: string | undefined) {
    filter.projectId = projectId
    filter.warehouseId = undefined
    if (projectId) {
      fetchWarehouses(projectId)
    } else {
      fetchWarehouses()
    }
    fetchKpi()
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
    const items: { name: string; qty: number; threshold: number }[] = []
    const safetyStockQty = Number(stock.value?.safetyStockQty)
    if (
      stock.value &&
      Number(stock.value.availableQty) < safetyStockQty &&
      Number(stock.value.availableQty) > 0
    ) {
      items.push({
        name: stock.value.materialName || getMaterialName(stock.value.materialId),
        qty: Number(stock.value.availableQty),
        threshold: safetyStockQty,
      })
    }
    return items
  })

  function handleReplenish() {
    const quantity = Number(stock.value?.availableQty)
    const safetyStockQty = Number(stock.value?.safetyStockQty)
    if (!stock.value || quantity <= 0 || quantity >= safetyStockQty) return
    const suggestedQuantity = Math.max(0, safetyStockQty - quantity).toFixed(4)
    const projectId = warehouseList.value.find((w) => w.id === stock.value?.warehouseId)?.projectId
    if (!projectId) {
      message.warning('当前仓库缺少项目归属，无法发起补货申请')
      return
    }
    router.push({
      path: '/inventory/purchase-request',
      query: {
        prefill: 'replenishment',
        projectId,
        materialId: stock.value.materialId,
        quantity: suggestedQuantity,
      },
    })
  }

  async function handleSafetyThresholdSave() {
    if (!stock.value || safetyThresholdDraft.value == null) return
    thresholdSaving.value = true
    try {
      const updated = await updateStockSafetyThreshold(stock.value.id, String(safetyThresholdDraft.value))
      stock.value = updated
      safetyThresholdDraft.value = Number(updated.safetyStockQty)
      await fetchKpi()
      message.success('安全库存阈值已更新')
    } catch (e: unknown) {
      console.error(e)
    } finally {
      thresholdSaving.value = false
    }
  }

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
    { field: 'txnType', title: '类型', width: 80, slots: { default: 'txnType' } },
    {
      field: 'quantity',
      title: '变动量',
      width: 120,
      align: 'right' as const,
      sortable: true,
      slots: { default: 'quantity' },
    },
    {
      field: 'availableAfter',
      title: '变动后余量',
      width: 130,
      align: 'right' as const,
      slots: { default: 'availableAfter' },
    },
    { field: 'sourceType', title: '来源类型', width: 120, slots: { default: 'sourceType' } },
    {
      field: 'sourceId',
      title: '关联单据',
      width: 130,
      ellipsis: true,
      slots: { default: 'sourceId' },
    },
    {
      field: 'createdTime',
      title: '操作时间',
      width: 150,
      sortable: true,
    },
    { key: 'ops', title: '操作', width: 76, align: 'center' as const, slots: { default: 'ops' } },
  ])
  const {
    visibleColumns: visibleGridColumns,
    columnSettings,
    colVisible,
    toggleCol,
  } = useColumnSettings('stock_ledger_cols', gridColumns)

  const showEmptyState = computed(
    () => hasLoaded.value && !loading.value && !listError.value && hasRequiredFilters.value && !txnList.value.length,
  )

  function init() {
    hydrateFromRouteQuery()
    fetchWarehouses(filter.projectId)
    fetchKpi()
    if (hasRequiredFilters.value) {
      fetchData()
    }
  }

  return {
    // 状态
    filter,
    loading,
    hasLoaded,
    listError,
    hasActiveFilters,
    hasRequiredFilters,
    showEmptyState,
    stock,
    safetyThresholdDraft,
    thresholdSaving,
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
    columnSettings,
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
    handleReplenish,
    handleSafetyThresholdSave,
    inOutStats,
    gridColumns,
    visibleGridColumns,
    init,
  }
}
