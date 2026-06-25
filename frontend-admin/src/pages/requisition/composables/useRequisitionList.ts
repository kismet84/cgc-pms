import { ref, reactive, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getRequisitionList,
  deleteRequisition,
  submitRequisitionForApproval,
} from '@/api/modules/requisition'
import { getWarehouseList } from '@/api/modules/inventory'
import type { MatRequisitionVO } from '@/types/requisition'
import type { WarehouseVO } from '@/types/inventory'

export function fmtAmount(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function useRequisitionList() {
  const filter = reactive({
    projectId: undefined as string | undefined,
    contractId: undefined as string | undefined,
    warehouseId: undefined as string | undefined,
    approvalStatus: undefined as string | undefined,
    requisitionCode: '',
  })

  const loading = ref(false)
  const tableData = ref<MatRequisitionVO[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)

  const warehouseList = ref<WarehouseVO[]>([])

  // ---- KPI computeds ----
  const kpiTotalCount = computed(() => total.value)
  const kpiTotalAmount = computed(() => {
    return tableData.value.reduce((sum, r) => sum + parseFloat(r.totalAmount || '0'), 0).toFixed(2)
  })

  const gridColumns = computed(() => [
    { field: 'requisitionCode', title: '领料单号', minWidth: 150, ellipsis: true },
    { field: 'projectName', title: '项目', minWidth: 150, ellipsis: true },
    { field: 'contractName', title: '合同', minWidth: 150, ellipsis: true },
    { field: 'partnerName', title: '供应商', minWidth: 140, ellipsis: true },
    { field: 'requisitionDate', title: '领料日期', width: 112 },
    {
      field: 'totalAmount',
      title: '总金额',
      width: 128,
      align: 'right' as const,
      slots: { default: 'totalAmount' },
    },
    {
      field: 'stockOutFlag',
      title: '出库状态',
      width: 108,
      slots: { default: 'stockOutFlag' },
    },
    {
      field: 'approvalStatus',
      title: '审批状态',
      width: 108,
      slots: { default: 'approvalStatus' },
    },
    { title: '操作', width: 200, slots: { default: 'action' } },
  ])

  async function fetchData() {
    loading.value = true
    try {
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
      total.value = res.total
    } catch (e: unknown) {
      console.error(e)
      tableData.value = []
      total.value = 0
      message.error('加载领料申请列表失败，请稍后重试')
    } finally {
      loading.value = false
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
    filter.contractId = undefined
    filter.warehouseId = undefined
    filter.approvalStatus = undefined
    filter.requisitionCode = ''
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
