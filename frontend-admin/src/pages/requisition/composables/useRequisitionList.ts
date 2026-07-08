import { ref, reactive, computed } from 'vue'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  getRequisitionList,
  deleteRequisition,
  submitRequisitionForApproval,
} from '@/api/modules/requisition'
import { getWarehouseList } from '@/api/modules/inventory'
import { readPositiveIntQuery, readStringQuery, replaceListQuery } from '@/composables/listPageQuery'
import {
  buildActionColumn,
  buildAmountColumn,
  buildDateColumn,
  buildStatusColumn,
  formatWanAmount,
} from '@/composables/listTablePresets'
import type { MatRequisitionVO } from '@/types/requisition'
import type { WarehouseVO } from '@/types/inventory'

export const fmtAmount = formatWanAmount

export function useRequisitionList({
  route,
  router,
}: {
  route: RouteLocationNormalizedLoaded
  router: Router
}) {
  const filter = reactive({
    projectId: undefined as string | undefined,
    contractId: undefined as string | undefined,
    warehouseId: undefined as string | undefined,
    approvalStatus: undefined as string | undefined,
    requisitionCode: '',
  })

  const loading = ref(false)
  const hasLoaded = ref(false)
  const listError = ref<string | null>(null)
  const tableData = ref<MatRequisitionVO[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)
  const queryReady = ref(false)

  const warehouseList = ref<WarehouseVO[]>([])
  const hasActiveFilters = computed(
    () =>
      Boolean(
        filter.projectId ||
          filter.contractId ||
          filter.warehouseId ||
          filter.approvalStatus ||
          filter.requisitionCode,
      ),
  )

  // ---- KPI computeds ----
  const kpiTotalCount = computed(() => total.value)
  const kpiTotalAmount = computed(() => {
    return tableData.value.reduce((sum, r) => sum + parseFloat(r.totalAmount || '0'), 0).toFixed(2)
  })

  const gridColumns = computed(() => [
    {
      field: 'requisitionCode',
      title: '领料单号',
      minWidth: 150,
      slots: { default: 'requisitionCode' },
    },
    { field: 'projectName', title: '项目', minWidth: 150, ellipsis: true },
    { field: 'contractName', title: '合同', minWidth: 150, ellipsis: true },
    { field: 'partnerName', title: '供应商', minWidth: 140, ellipsis: true },
    buildDateColumn('requisitionDate', '领料日期'),
    buildAmountColumn('totalAmount', '总金额'),
    buildStatusColumn('stockOutFlag', '出库状态'),
    buildStatusColumn('approvalStatus', '审批状态'),
    buildActionColumn(),
  ])

  async function fetchData() {
    loading.value = true
    listError.value = null
    try {
      await syncRouteQuery()
      const res = await getRequisitionList({
        pageNo: pageNo.value,
        pageSize: pageSize.value,
        projectId: filter.projectId,
        contractId: filter.contractId,
        warehouseId: filter.warehouseId,
        requisitionCode: filter.requisitionCode || undefined,
        approvalStatus: filter.approvalStatus,
      })
      tableData.value = res.records
      total.value = Number(res.total ?? 0)
    } catch (e: unknown) {
      console.error(e)
      tableData.value = []
      total.value = 0
      listError.value = '请检查筛选条件或网络状态后重试。'
      message.error('加载领料申请列表失败，请稍后重试')
    } finally {
      hasLoaded.value = true
      loading.value = false
    }
  }

  function hydrateFromRouteQuery() {
    filter.projectId = readStringQuery(route.query.projectId)
    filter.warehouseId = readStringQuery(route.query.warehouseId)
    filter.approvalStatus = readStringQuery(route.query.approvalStatus)
    filter.requisitionCode = readStringQuery(route.query.requisitionCode) ?? ''
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
        warehouseId: filter.warehouseId,
        approvalStatus: filter.approvalStatus,
        requisitionCode: filter.requisitionCode || undefined,
        pageNo: pageNo.value,
        pageSize: pageSize.value,
      },
      ['projectId', 'warehouseId', 'approvalStatus', 'requisitionCode', 'pageNo', 'pageSize'],
    )
    await router.replace({ path: route.path, query: nextQuery })
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
    filter.contractId = undefined
    filter.warehouseId = undefined
    filter.approvalStatus = undefined
    filter.requisitionCode = ''
    pageNo.value = 1
    listError.value = null
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

  function handleDelete(record: MatRequisitionVO) {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除领料申请"${record.requisitionCode}"吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteRequisition(record.id!)
          message.success('删除成功')
          fetchData()
        } catch (e: unknown) {
          console.error(e)
          message.error('删除失败，请稍后重试')
        }
      },
    })
  }

  function handleSubmitApproval(record: MatRequisitionVO) {
    Modal.confirm({
      title: '确认提交',
      content: `确定要提交领料申请"${record.requisitionCode}"吗？提交后将进入审批流程`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await submitRequisitionForApproval(record.id!)
          message.success('提交审批成功')
          fetchData()
        } catch (e: unknown) {
          console.error(e)
          message.error('提交审批失败')
        }
      },
    })
  }

  function init() {
    hydrateFromRouteQuery()
    fetchWarehouses()
    fetchData()
  }

  const showEmptyState = computed(
    () => hasLoaded.value && !loading.value && !listError.value && !tableData.value.length,
  )

  return {
    filter,
    loading,
    hasLoaded,
    listError,
    hasActiveFilters,
    showEmptyState,
    tableData,
    total,
    pageNo,
    pageSize,
    warehouseList,
    kpiTotalCount,
    kpiTotalAmount,
    gridColumns,
    fetchData,
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
