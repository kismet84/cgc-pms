import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useReferenceStore } from '@/stores/reference'
import { getContractLedger, getContractKpi, deleteContract } from '@/api/modules/contract'
import type {
  ContractVO,
  ContractQueryParams,
  ContractKpiVO,
  ContractType,
  ContractStatus,
} from '@/types/contract'
import type { PageResult } from '@/types/api'

// ---- Constants ----
export const TYPE_LABEL: Record<ContractType, string> = {
  MAIN: '总包合同',
  SUB: '分包合同',
  PURCHASE: '采购合同',
  LEASE: '租赁合同',
  SERVICE: '服务合同',
}
export const TYPE_COLOR: Record<ContractType, string> = {
  MAIN: 'blue',
  SUB: 'green',
  PURCHASE: 'orange',
  LEASE: 'purple',
  SERVICE: 'cyan',
}

export const TYPE_COLOR_HEX: Record<ContractType, string> = {
  MAIN: '#2f7df6',
  SUB: '#31c48d',
  PURCHASE: '#f59e0b',
  LEASE: '#8b5cf6',
  SERVICE: '#22c7d7',
}

export const STATUS_LABEL: Record<ContractStatus, string> = {
  DRAFT: '草稿',
  PERFORMING: '履约中',
  SETTLED: '已完成',
  TERMINATED: '已终止',
}
export const STATUS_COLOR: Record<ContractStatus, string> = {
  DRAFT: '#94a3b8',
  PERFORMING: '#2f7df6',
  SETTLED: '#31c48d',
  TERMINATED: '#ef4444',
}

export function useContractLedger() {
  const router = useRouter()
  const referenceStore = useReferenceStore()
  const projects = computed(() => referenceStore.projects ?? [])

  // ---- Modal state ----
  const contractModalVisible = ref(false)
  const contractModalMode = ref<'create' | 'edit'>('create')
  const contractModalId = ref('')

  function handleCreate() {
    contractModalMode.value = 'create'
    contractModalId.value = ''
    contractModalVisible.value = true
  }

  function handleView(row: ContractVO) {
    router.push('/contract/' + row.id)
  }

  function handleEdit(row: ContractVO) {
    contractModalMode.value = 'edit'
    contractModalId.value = String(row.id)
    contractModalVisible.value = true
  }

  function handleDelete(row: ContractVO) {
    Modal.confirm({
      title: '确认删除',
      content: '确定删除该合同吗？',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteContract(String(row.id))
          message.success('已删除')
          fetchData()
        } catch (e: unknown) {
          console.error(e)
          message.error('删除失败')
        }
      },
    })
  }

  function handleAllAlerts() {
    router.push('/alert')
  }

  function handleContractSaved() {
    contractModalVisible.value = false
    fetchData()
    fetchKpi()
  }

  function handleContractClose() {
    contractModalVisible.value = false
  }

  // ---- Filter state ----
  const filter = reactive({
    keyword: '',
    projectId: undefined as string | undefined,
    contractType: undefined as ContractType | undefined,
    contractStatus: undefined as ContractStatus | undefined,
    contractCode: '',
    dateRange: [] as string[],
  })

  // ---- Table state ----
  const loading = ref(false)
  const tableData = ref<ContractVO[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)

  // ---- KPI state ----
  const kpi = ref<ContractKpiVO>({
    totalCount: 0,
    totalAmount: '0',
    paidAmount: '0',
    unpaidAmount: '0',
    overdueCount: 0,
  })

  // ---- Column visibility ----
  const COLS_KEY = 'contract_ledger_cols'
  const defaultCols: Record<string, boolean> = {
    contractCode: true,
    contractName: true,
    contractType: true,
    partyAName: true,
    partyBName: true,
    contractAmount: true,
    signedDate: true,
    contractStatus: true,
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

  // ---- Fetch ----
  let fetchSeq = 0
  async function fetchData() {
    const mySeq = ++fetchSeq
    loading.value = true
    const params: ContractQueryParams = {
      projectId: filter.projectId,
      contractType: filter.contractType,
      contractStatus: filter.contractStatus,
      keyword: filter.keyword || undefined,
      contractCode: filter.contractCode || undefined,
      startDate: filter.dateRange[0],
      endDate: filter.dateRange[1],
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    }
    try {
      const res: PageResult<ContractVO> = await getContractLedger(params)
      if (mySeq !== fetchSeq) return // stale response
      tableData.value = res.records
      total.value = Number(res.total) || 0
    } catch (e: unknown) {
      if (mySeq !== fetchSeq) return
      console.error(e)
      tableData.value = []
      total.value = 0
      message.error('加载合同台账失败，请稍后重试')
    } finally {
      if (mySeq === fetchSeq) loading.value = false
    }
  }

  async function fetchKpi() {
    try {
      kpi.value = await getContractKpi()
    } catch (e: unknown) {
      console.error(e)
      kpi.value = {
        totalCount: 0,
        totalAmount: '0',
        paidAmount: '0',
        unpaidAmount: '0',
        overdueCount: 0,
      }
      message.error('加载合同指标失败，请稍后重试')
    }
  }

  function handleSearch() {
    pageNo.value = 1
    fetchData()
  }

  function handleReset() {
    filter.keyword = ''
    filter.projectId = undefined
    filter.contractType = undefined
    filter.contractStatus = undefined
    filter.contractCode = ''
    filter.dateRange = []
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

  onMounted(async () => {
    await referenceStore.fetchProjects()
    fetchData()
    fetchKpi()
  })

  // ---- Helpers ----
  function fmtAmount(val: string): string {
    const n = parseFloat(val)
    if (isNaN(n)) return '0.00'
    return (n / 10000).toLocaleString('zh-CN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
  }

  // ---- Computed analysis ----
  const typeDistribution = computed(() => {
    if (!tableData.value.length) return []
    const counts = tableData.value.reduce<Record<ContractType, number>>(
      (acc, item) => {
        acc[item.contractType] += 1
        return acc
      },
      { MAIN: 0, SUB: 0, PURCHASE: 0, LEASE: 0, SERVICE: 0 },
    )
    return (Object.keys(TYPE_LABEL) as ContractType[])
      .map((key) => ({
        key,
        label: TYPE_LABEL[key],
        value: counts[key],
        color: TYPE_COLOR_HEX[key],
      }))
      .filter((item) => item.value > 0)
  })

  const totalCount = computed(() => tableData.value.length || 1)
  function typePercent(value: number): number {
    return Math.round((value / totalCount.value) * 100)
  }

  // ---- KPI max normalization ----
  const kpiMax = computed(() => ({
    totalCount: Math.max(kpi.value.totalCount, 1),
    totalAmount: Math.max(parseFloat(kpi.value.totalAmount), 1),
    overdueCount: Math.max(kpi.value.overdueCount, 1),
  }))
  function kpiPct(value: number, max: number): number {
    if (max === 0) return 0
    return Math.min(Math.round((value / max) * 100), 100)
  }

  const statusBars = computed(() => {
    if (!tableData.value.length) return []
    const counts = tableData.value.reduce<Record<ContractStatus, number>>(
      (acc, item) => {
        acc[item.contractStatus] += 1
        return acc
      },
      { DRAFT: 0, PERFORMING: 0, SETTLED: 0, TERMINATED: 0 },
    )
    const total = Object.values(counts).reduce((s, v) => s + v, 0) || 1
    return (Object.keys(STATUS_LABEL) as ContractStatus[])
      .filter((key) => counts[key] > 0)
      .map((key) => ({
        key,
        label: STATUS_LABEL[key],
        value: counts[key],
        color: STATUS_COLOR[key],
        percent: Math.round((counts[key] / total) * 100),
      }))
  })

  const warningRows = computed(() => {
    const now = new Date()
    return tableData.value
      .filter((item) => item.contractStatus === 'PERFORMING' && item.endDate)
      .map((item) => {
        const end = new Date(item.endDate)
        const days = Math.ceil((now.getTime() - end.getTime()) / (1000 * 60 * 60 * 24))
        return {
          project: item.projectName || '未知项目',
          title: item.contractName,
          days,
        }
      })
      .filter((item) => item.days > 0)
      .sort((a, b) => b.days - a.days)
      .slice(0, 5)
  })

  // ---- VxeGrid columns ----
  const gridColumns = computed(() => [
    ...(colVisible.contractCode
      ? [
          {
            field: 'contractCode',
            title: '合同编号',
            width: 180,
            showOverflow: 'tooltip',
            slots: { default: 'contractCode' },
          },
        ]
      : []),
    ...(colVisible.contractName
      ? [{ field: 'contractName', title: '合同名称', minWidth: 180, showOverflow: 'tooltip' }]
      : []),
    ...(colVisible.contractType
      ? [
          {
            field: 'contractType',
            title: '合同类型',
            width: 112,
            showOverflow: 'tooltip',
            slots: { default: 'contractType' },
          },
        ]
      : []),
    ...(colVisible.partyAName
      ? [{ field: 'partyAName', title: '甲方', minWidth: 140, showOverflow: 'tooltip' }]
      : []),
    ...(colVisible.partyBName
      ? [{ field: 'partyBName', title: '乙方', minWidth: 140, showOverflow: 'tooltip' }]
      : []),
    ...(colVisible.contractAmount
      ? [
          {
            field: 'contractAmount',
            title: '合同金额(含税)',
            width: 172,
            minWidth: 172,
            align: 'right' as const,
            showOverflow: false,
            slots: { default: 'amount' },
          },
        ]
      : []),
    ...(colVisible.signedDate
      ? [{ field: 'signedDate', title: '签订日期', width: 118, showOverflow: 'tooltip' }]
      : []),
    ...(colVisible.contractStatus
      ? [
          {
            field: 'contractStatus',
            title: '合同状态',
            width: 112,
            showOverflow: 'tooltip',
            slots: { default: 'status' },
          },
        ]
      : []),
    ...(colVisible.ops ? [{ title: '操作', width: 76, slots: { default: 'ops' } }] : []),
  ])

  return {
    // Modal state
    contractModalVisible,
    contractModalMode,
    contractModalId,
    handleCreate,
    handleView,
    handleEdit,
    handleDelete,
    handleAllAlerts,
    handleContractSaved,
    handleContractClose,
    // Filter
    filter,
    projects,
    // Table
    loading,
    tableData,
    total,
    pageNo,
    pageSize,
    // KPI
    kpi,
    // Column visibility
    colVisible,
    defaultCols,
    toggleCol,
    // Fetch
    fetchData,
    fetchKpi,
    handleSearch,
    handleReset,
    handlePageChange,
    handlePageSizeChange,
    // Helpers
    fmtAmount,
    kpiMax,
    kpiPct,
    typeDistribution,
    typePercent,
    statusBars,
    warningRows,
    // Grid
    gridColumns,
  }
}
