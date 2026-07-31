import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
import type { DictDataVO } from '@/types/dict'
import { fetchDictData, getDictTagColorSync } from '@/utils/dict'
import {
  buildActionColumn,
  buildAmountColumn,
  buildDateColumn,
  buildStatusColumn,
  formatWanAmount,
} from '@/composables/listTablePresets'
import { useColumnSettings } from '@/composables/useColumnSettings'

const CONTRACT_TYPE_DICT = 'contract_type'
const CONTRACT_STATUS_DICT = 'contract_status'
const CHART_COLOR: Record<string, string> = {
  blue: 'var(--primary)',
  processing: 'var(--primary)',
  green: 'var(--success)',
  success: 'var(--success)',
  orange: 'var(--warning)',
  warning: 'var(--warning)',
  red: 'var(--error)',
  error: 'var(--error)',
}

export function useContractLedger() {
  const route = useRoute()
  const router = useRouter()
  const referenceStore = useReferenceStore()
  const projects = computed(() => referenceStore.projects ?? [])
  const contractTypeOptions = ref<DictDataVO[]>([])
  const contractStatusOptions = ref<DictDataVO[]>([])
  const typeLabelMap = computed<Record<string, string>>(() =>
    Object.fromEntries(contractTypeOptions.value.map((item) => [item.dictValue, item.dictLabel])),
  )
  const typeColorMap = computed<Record<string, string>>(() =>
    Object.fromEntries(
      contractTypeOptions.value.map((item) => [
        item.dictValue,
        getDictTagColorSync(CONTRACT_TYPE_DICT, item.dictValue),
      ]),
    ),
  )

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

  function calcCodeColumnWidth(values: Array<string | undefined>, title = '合同编号') {
    const longest = Math.max(title.length, ...values.map((value) => String(value ?? '').length))
    return Math.min(Math.max(longest * 9 + 42, 128), 240)
  }

  const defaultColumnVisibility: Record<string, boolean> = {
    contractCode: true,
    contractName: true,
    contractType: true,
    partyAName: false,
    partyBName: true,
    contractAmount: true,
    signedDate: false,
    contractStatus: true,
    ops: true,
  }

  // ---- Fetch ----
  let fetchSeq = 0
  async function fetchData() {
    const mySeq = ++fetchSeq
    loading.value = true
    syncQueryToRoute()
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
    restoreFilterFromRoute()
    const [types, statuses] = await Promise.all([
      fetchDictData(CONTRACT_TYPE_DICT),
      fetchDictData(CONTRACT_STATUS_DICT),
      referenceStore.fetchProjects(),
    ])
    contractTypeOptions.value = types
    contractStatusOptions.value = statuses
    fetchData()
    fetchKpi()
  })

  function readQueryString(key: string): string | undefined {
    const value = route.query[key]
    return Array.isArray(value) ? value[0] || undefined : value || undefined
  }

  function readQueryNumber(key: string, fallback: number): number {
    const value = Number(readQueryString(key))
    return Number.isFinite(value) && value > 0 ? value : fallback
  }

  function restoreFilterFromRoute() {
    filter.keyword = readQueryString('keyword') || ''
    filter.projectId = readQueryString('projectId')
    filter.contractType = readQueryString('contractType') as ContractType | undefined
    filter.contractStatus = readQueryString('contractStatus') as ContractStatus | undefined
    const startDate = readQueryString('startDate')
    const endDate = readQueryString('endDate')
    filter.dateRange = startDate || endDate ? [startDate || '', endDate || ''] : []
    pageNo.value = readQueryNumber('pageNo', 1)
    pageSize.value = readQueryNumber('pageSize', 20)
  }

  function syncQueryToRoute() {
    const query = {
      ...route.query,
      keyword: filter.keyword || undefined,
      projectId: filter.projectId || undefined,
      contractType: filter.contractType || undefined,
      contractStatus: filter.contractStatus || undefined,
      startDate: filter.dateRange[0] || undefined,
      endDate: filter.dateRange[1] || undefined,
      pageNo: pageNo.value === 1 ? undefined : String(pageNo.value),
      pageSize: pageSize.value === 20 ? undefined : String(pageSize.value),
    }
    router.replace({ query })
  }

  // ---- Helpers ----
  const fmtAmount = formatWanAmount

  // ---- Computed analysis ----
  const typeDistribution = computed(() => {
    if (!tableData.value.length) return []
    const counts = tableData.value.reduce<Record<string, number>>((acc, item) => {
      acc[item.contractType] = (acc[item.contractType] || 0) + 1
      return acc
    }, {})
    return contractTypeOptions.value
      .map((item) => ({
        key: item.dictValue,
        label: item.dictLabel,
        value: counts[item.dictValue] || 0,
        color:
          CHART_COLOR[getDictTagColorSync(CONTRACT_TYPE_DICT, item.dictValue)] || 'var(--info)',
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
    const counts = tableData.value.reduce<Record<string, number>>((acc, item) => {
      acc[item.contractStatus] = (acc[item.contractStatus] || 0) + 1
      return acc
    }, {})
    const total = Object.values(counts).reduce((s, v) => s + v, 0) || 1
    return contractStatusOptions.value
      .filter((item) => (counts[item.dictValue] || 0) > 0)
      .map((item) => ({
        key: item.dictValue,
        label: item.dictLabel,
        value: counts[item.dictValue] || 0,
        color:
          CHART_COLOR[getDictTagColorSync(CONTRACT_STATUS_DICT, item.dictValue)] || 'var(--info)',
        percent: Math.round(((counts[item.dictValue] || 0) / total) * 100),
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
    {
      field: 'contractCode',
      title: '合同编号',
      width: calcCodeColumnWidth(tableData.value.map((item) => item.contractCode)),
      minWidth: 128,
      showOverflow: false,
      slots: { default: 'contractCode' },
    },
    { field: 'contractName', title: '合同名称', minWidth: 118, showOverflow: 'tooltip' },
    {
      field: 'contractType',
      title: '合同类型',
      width: 108,
      showOverflow: 'tooltip',
      slots: { default: 'contractType' },
    },
    { field: 'partyAName', title: '甲方', minWidth: 116, showOverflow: 'tooltip' },
    { field: 'partyBName', title: '乙方', minWidth: 104, showOverflow: 'tooltip' },
    buildAmountColumn('contractAmount', '合同金额(含税)', 'amount', {
      showOverflow: false,
    }),
    buildDateColumn('signedDate', '签订日期', { showOverflow: 'tooltip' }),
    buildStatusColumn('contractStatus', '合同状态', 'status', {
      showOverflow: 'tooltip',
    }),
    buildActionColumn('ops', {
      field: 'ops',
      align: 'center' as const,
      headerAlign: 'center' as const,
    }),
  ])
  const { visibleColumns, columnSettings, colVisible, toggleCol } = useColumnSettings(
    'contract_ledger_cols_v2',
    gridColumns,
    defaultColumnVisibility,
  )

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
    contractTypeOptions,
    contractStatusOptions,
    typeLabelMap,
    typeColorMap,
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
    columnSettings,
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
    visibleColumns,
  }
}
