import { ref, reactive, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getReceiptList, deleteReceipt, submitReceiptForApproval } from '@/api/modules/receipt'
import { getOrderList } from '@/api/modules/purchase'
import { getWarehouseList } from '@/api/modules/inventory'
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

export function fmtAmount(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function useReceiptList() {
  const filter = reactive({
    projectId: undefined as string | undefined,
    orderId: undefined as string | undefined,
    contractId: undefined as string | undefined,
    partnerId: undefined as string | undefined,
    receiptCode: '',
    qualityStatus: undefined as string | undefined,
  })

  const loading = ref(false)
  const tableData = ref<MatReceiptVO[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)

  const orderList = ref<MatPurchaseOrderVO[]>([])
  const warehouseList = ref<WarehouseVO[]>([])

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
    { field: 'receiptDate', title: '验收日期', width: 112 },
    {
      field: 'totalAmount',
      title: '总金额',
      width: 128,
      align: 'right' as const,
      slots: { default: 'totalAmount' },
    },
    { field: 'qualityStatus', title: '质量状态', width: 108, slots: { default: 'qualityStatus' } },
    {
      field: 'approvalStatus',
      title: '审批状态',
      width: 108,
      slots: { default: 'approvalStatus' },
    },
    { title: '操作', width: 76, slots: { default: 'action' } },
  ])

  async function fetchData() {
    loading.value = true
    try {
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
      message.error('加载验收列表失败，请稍后重试')
    } finally {
      loading.value = false
    }
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
    fetchOrders()
    fetchWarehouses()
    fetchData()
  }

  return {
    filter,
    loading,
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
