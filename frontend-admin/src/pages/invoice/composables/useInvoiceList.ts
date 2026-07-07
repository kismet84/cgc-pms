import { ref, reactive, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getInvoiceList,
  deleteInvoice,
  verifyInvoice,
  getPayRecordList,
} from '@/api/modules/invoice'
import type { InvoiceVO, PayRecordBrief } from '@/types/invoice'
import { VERIFY_STATUS_LABEL } from '@/types/invoice'

const INVOICE_BUSINESS_TYPE = 'INVOICE_ATTACHMENT'

export function fmtAmount(val: string | undefined): string {
  if (!val) return '-'
  const n = parseFloat(val)
  if (isNaN(n)) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

export function useInvoiceList() {
  const filter = reactive({
    keyword: '',
    payRecordId: undefined as string | undefined,
    verifyStatus: undefined as string | undefined,
  })

  const loading = ref(false)
  const tableData = ref<InvoiceVO[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)

  const payRecordList = ref<PayRecordBrief[]>([])

  const gridColumns = computed(() => [
    { field: 'invoiceNo', title: '发票号码', minWidth: 150, ellipsis: true },
    { field: 'invoiceType', title: '发票类型', width: 108, slots: { default: 'invoiceType' } },
    {
      field: 'invoiceAmount',
      title: '发票金额',
      width: 128,
      align: 'right' as const,
      slots: { default: 'invoiceAmount' },
    },
    {
      field: 'taxRate',
      title: '税率(%)',
      width: 92,
      slots: { default: 'taxRate' },
    },
    {
      field: 'taxAmount',
      title: '税额',
      width: 110,
      align: 'right' as const,
      slots: { default: 'taxAmount' },
    },
    { field: 'invoiceDate', title: '开票日期', width: 110 },
    { field: 'verifyStatus', title: '核验状态', width: 108, slots: { default: 'verifyStatus' } },
    { field: 'remark', title: '备注', minWidth: 140, ellipsis: true },
    { field: 'createdAt', title: '创建时间', width: 150 },
    { title: '操作', width: 76, slots: { default: 'action' } },
  ])

  async function fetchData() {
    loading.value = true
    try {
      const res = await getInvoiceList({
        pageNo: pageNo.value,
        pageSize: pageSize.value,
        payRecordId: filter.payRecordId,
        invoiceNo: filter.keyword || undefined,
        verifyStatus: filter.verifyStatus,
      })
      tableData.value = res.records
      total.value = Number(res.total ?? 0)
    } catch (e: unknown) {
      console.error(e)
      tableData.value = []
      total.value = 0
      message.error('加载发票列表失败，请稍后重试')
    } finally {
      loading.value = false
    }
  }

  async function fetchPayRecords() {
    try {
      const res = await getPayRecordList()
      payRecordList.value = res.records
    } catch (e: unknown) {
      console.error(e)
      payRecordList.value = []
    }
  }

  function handleSearch() {
    pageNo.value = 1
    fetchData()
  }

  function handleReset() {
    filter.keyword = ''
    filter.payRecordId = undefined
    filter.verifyStatus = undefined
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

  function handleDelete(record: InvoiceVO) {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除发票"${record.invoiceNo}"吗？`,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteInvoice(record.id)
          message.success('删除成功')
          fetchData()
        } catch (e: unknown) {
          console.error(e)
          Modal.error({ title: '删除失败', content: '删除失败，请稍后重试' })
        }
      },
    })
  }

  function handleVerify(record: InvoiceVO) {
    Modal.confirm({
      title: '发票核验',
      content: `请选择发票"${record.invoiceNo}"的核验结果：`,
      okText: '认证通过',
      cancelText: '标记异常',
      okType: 'primary',
      cancelButtonProps: { danger: true },
      onOk: async () => {
        try {
          await verifyInvoice(record.id, 'VERIFIED')
          message.success('发票已认证通过')
          fetchData()
        } catch (e: unknown) {
          console.error(e)
          message.error('核验操作失败，请稍后重试')
        }
      },
      onCancel: async () => {
        try {
          await verifyInvoice(record.id, 'ABNORMAL')
          message.warning('发票已标记为异常')
          fetchData()
        } catch (e: unknown) {
          console.error(e)
          message.error('核验操作失败，请稍后重试')
        }
      },
    })
  }

  // KPI computations
  const kpiInvoiceTotal = computed(() =>
    tableData.value.reduce((s, r) => s + (parseFloat(r.invoiceAmount) || 0), 0),
  )
  const kpiInvoiced = computed(() =>
    tableData.value
      .filter((r) => r.verifyStatus === 'VERIFIED')
      .reduce((s, r) => s + (parseFloat(r.invoiceAmount) || 0), 0),
  )
  const kpiUninvoiced = computed(() =>
    tableData.value
      .filter((r) => r.verifyStatus !== 'VERIFIED')
      .reduce((s, r) => s + (parseFloat(r.invoiceAmount) || 0), 0),
  )
  const kpiAbnormal = computed(
    () => tableData.value.filter((r) => r.verifyStatus === 'ABNORMAL').length,
  )

  const kpiMax = computed(() => ({
    total: Math.max(kpiInvoiceTotal.value, 1),
  }))
  function kpiPct(value: number, max: number): number {
    if (max === 0) return 0
    return Math.min(Math.round((value / max) * 100), 100)
  }

  const verifyBreakdown = computed(() => {
    const m: Record<string, number> = {}
    tableData.value.forEach((r) => {
      const label = VERIFY_STATUS_LABEL[r.verifyStatus] ?? r.verifyStatus
      m[label] = (m[label] || 0) + 1
    })
    const total = Object.values(m).reduce((s, v) => s + v, 0) || 1
    return Object.entries(m).map(([label, count]) => ({
      label,
      count,
      pct: Math.round((count / total) * 100),
    }))
  })

  function init() {
    fetchPayRecords()
    fetchData()
  }

  return {
    filter,
    loading,
    tableData,
    total,
    pageNo,
    pageSize,
    payRecordList,
    gridColumns,
    fetchData,
    fetchPayRecords,
    handleSearch,
    handleReset,
    handlePageChange,
    handlePageSizeChange,
    handleDelete,
    handleVerify,
    kpiInvoiceTotal,
    kpiInvoiced,
    kpiUninvoiced,
    kpiAbnormal,
    kpiMax,
    kpiPct,
    verifyBreakdown,
    init,
    INVOICE_BUSINESS_TYPE,
  }
}
