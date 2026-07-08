import { ref, reactive, computed } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { getReceiptList, deleteReceipt, submitReceiptForApproval } from '@/api/modules/receipt'
import { getOrderList } from '@/api/modules/purchase'
import { getWarehouseList } from '@/api/modules/inventory'
import { readPositiveIntQuery, readStringQuery, replaceListQuery } from '@/composables/listPageQuery'
import {
  buildActionColumn,
  buildAmountColumn,
  buildDateColumn,
  buildStatusColumn,
  formatWanAmount,
} from '@/composables/listTablePresets'
import type { MatReceiptVO } from '@/types/receipt'
import type { MatPurchaseOrderVO } from '@/types/purchase'
import type { WarehouseVO } from '@/types/inventory'

export const QUALITY_STATUS_LABEL: Record<string, string> = {
  ACCEPTED: '让步接收',
  QUALIFIED: '合格',
  PARTIAL: '部分合格',
  UNQUALIFIED: '不合格',
  PENDING: '待检验',
}
export const QUALITY_STATUS_COLOR: Record<string, string> = {
  ACCEPTED: 'warning',
  QUALIFIED: 'success',
  PARTIAL: 'warning',
  UNQUALIFIED: 'error',
  PENDING: 'processing',
}

export const fmtAmount = formatWanAmount

export function useReceiptList({
  route,
  router,
}: {
  route: RouteLocationNormalizedLoaded
  router: Router
}) {
  const filter = reactive({
    projectId: undefined as string | undefined,
    orderId: undefined as string | undefined,
    contractId: undefined as string | undefined,
    partnerId: undefined as string | undefined,
    receiptCode: '',
    qualityStatus: undefined as string | undefined,
  })

  const loading = ref(false)
  const hasLoaded = ref(false)
  const listError = ref<string | null>(null)
  const tableData = ref<MatReceiptVO[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)
  const queryReady = ref(false)

  const orderList = ref<MatPurchaseOrderVO[]>([])
  const warehouseList = ref<WarehouseVO[]>([])
  const hasActiveFilters = computed(
    () => Boolean(filter.projectId || filter.orderId || filter.receiptCode || filter.qualityStatus),
  )

  // ---- KPI computeds ----
  const kpiTotalCount = computed(() => total.value)
  const kpiTotalAmount = computed(() => {
    return tableData.value.reduce((sum, r) => sum + parseFloat(r.totalAmount || '0'), 0).toFixed(2)
  })
  const kpiQualifiedCount = computed(() => {
    return tableData.value.filter((r) => r.qualityStatus === 'QUALIFIED').length
  })
  const kpiUnqualifiedCount = computed(() => {
    return tableData.value.filter((r) => r.qualityStatus === 'UNQUALIFIED').length
  })

  const gridColumns = computed(() => [
    { field: 'receiptCode', title: '验收单号', minWidth: 150, slots: { default: 'receiptCode' } },
    { field: 'orderCode', title: '采购订单', minWidth: 140, ellipsis: true },
    { field: 'projectName', title: '项目', minWidth: 150, ellipsis: true },
    { field: 'partnerName', title: '供应商', minWidth: 140, ellipsis: true },
    buildDateColumn('receiptDate', '验收日期'),
    buildAmountColumn('totalAmount', '总金额'),
    buildStatusColumn('qualityStatus', '质量状态'),
    buildStatusColumn('approvalStatus', '审批状态'),
    buildActionColumn(),
  ])

  async function fetchData() {
    loading.value = true
    listError.value = null
    try {
      await syncRouteQuery()
      const res = await getReceiptList({
        pageNum: pageNo.value,
        pageSize: pageSize.value,
        projectId: filter.projectId,
        orderId: filter.orderId,
        contractId: filter.contractId,
        partnerId: filter.partnerId,
        receiptCode: filter.receiptCode || undefined,
        qualityStatus: filter.qualityStatus,
      })
      tableData.value = res.records
      total.value = Number(res.total ?? 0)
    } catch (e: unknown) {
      console.error(e)
      tableData.value = []
      total.value = 0
      listError.value = '请检查筛选条件或网络状态后重试。'
      message.error('加载验收列表失败')
    } finally {
      hasLoaded.value = true
      loading.value = false
    }
  }

  function hydrateFromRouteQuery() {
    filter.projectId = readStringQuery(route.query.projectId)
    filter.orderId = readStringQuery(route.query.orderId)
    filter.receiptCode = readStringQuery(route.query.receiptCode) ?? ''
    filter.qualityStatus = readStringQuery(route.query.qualityStatus)
    pageNo.value = readPositiveIntQuery(route.query.pageNo, 1)
    pageSize.value = readPositiveIntQuery(route.query.pageSize, 20)
    queryReady.value = true
  }

  async function syncRouteQuery() {
    if (!queryReady.value) return
    const nextQuery = replaceListQuery(
      route.query,
      {
        projectId: filter.projectId,
        orderId: filter.orderId,
        receiptCode: filter.receiptCode || undefined,
        qualityStatus: filter.qualityStatus,
        pageNo: pageNo.value,
        pageSize: pageSize.value,
      },
      ['projectId', 'orderId', 'receiptCode', 'qualityStatus', 'pageNo', 'pageSize'],
    )
    await router.replace({ path: route.path, query: nextQuery })
  }

  async function fetchOrders() {
    try {
      const res = await getOrderList({ pageNum: 1, pageSize: 50 })
      orderList.value = res.records
    } catch (e: unknown) {
      console.error(e)
      orderList.value = []
    }
  }

  async function fetchWarehouses() {
    try {
      const res = await getWarehouseList({ pageNo: 1, pageSize: 200, status: 'ENABLE' })
      warehouseList.value = res.records
    } catch (e: unknown) {
      console.error(e)
      warehouseList.value = []
    }
  }

  function handleSearch() {
    pageNo.value = 1
    fetchData()
  }

  function handleReset() {
    filter.projectId = undefined
    filter.orderId = undefined
    filter.contractId = undefined
    filter.partnerId = undefined
    filter.receiptCode = ''
    filter.qualityStatus = undefined
    pageNo.value = 1
    fetchData()
  }

  function handlePageChange(page: number) {
    pageNo.value = page
    fetchData()
  }

  function handlePageSizeChange(_cur: number, size: number) {
    pageSize.value = size
    pageNo.value = 1
    fetchData()
  }

  function handleDelete(record: MatReceiptVO) {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除验收单"${record.receiptCode}"吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteReceipt(record.id)
          message.success('删除成功')
          fetchData()
        } catch (e: unknown) {
          console.error(e)
          message.error('删除失败，请稍后重试')
        }
      },
    })
  }

  function handleSubmitApproval(record: MatReceiptVO) {
    Modal.confirm({
      title: '确认提交',
      content: `确定要提交验收单"${record.receiptCode}"吗？提交后将进入审批流程`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await submitReceiptForApproval(record.id)
          message.success('提交审批成功')
          fetchData()
        } catch (e: unknown) {
          console.error(e)
          message.error('提交审批失败，请稍后重试')
        }
      },
    })
  }

  function init() {
    hydrateFromRouteQuery()
    fetchOrders()
    fetchWarehouses()
    fetchData()
  }

  return {
    filter,
    loading,
    hasLoaded,
    listError,
    hasActiveFilters,
    tableData,
    total,
    pageNo,
    pageSize,
    orderList,
    warehouseList,
    kpiTotalCount,
    kpiTotalAmount,
    kpiQualifiedCount,
    kpiUnqualifiedCount,
    gridColumns,
    fetchData,
    fetchOrders,
    fetchWarehouses,
    handleSearch,
    handleReset,
    handlePageChange,
    handlePageSizeChange,
    handleDelete,
    handleSubmitApproval,
    init,
  }
}
