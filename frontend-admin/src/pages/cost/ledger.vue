<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { message } from 'ant-design-vue'
import {
  getCostLedger,
  getCostLedgerSummary,
  getCostLedgerDetail,
  executeOverheadAllocation,
  getOverheadAllocationRules,
  type OverheadAllocationRuleVO,
} from '@/api/modules/cost'
import { getCostSubjectList } from '@/api/modules/costSubject'
import type { CostLedgerVO, CostLedgerQueryParams, CostLedgerSummaryVO } from '@/types/cost'
import { COST_TYPE_DICT, getCostTypeLabel, getSourceTypeLabel } from '@/types/cost'
import type { PageResult } from '@/types/api'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { fetchDictData } from '@/utils/dict'
import CostLedgerOverview from './components/CostLedgerOverview.vue'
import CostLedgerTablePanel from './components/CostLedgerTablePanel.vue'
import CostLedgerAnalysisRail from './components/CostLedgerAnalysisRail.vue'
import CostLedgerDetailDrawer from './components/CostLedgerDetailDrawer.vue'
import { useMobileViewport } from '@/composables/useMobileViewport'

const { isMobile } = useMobileViewport()
const route = useRoute()
const userStore = useUserStore()

const isAllocationAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canExecuteAllocation = computed(
  () =>
    isAllocationAdmin.value ||
    (userStore.hasPermission('cost:ledger:query') && userStore.hasPermission('overhead:execute')),
)
const canViewAllocationRules = computed(
  () => isAllocationAdmin.value || userStore.hasPermission('overhead:query'),
)
const ruleModalOpen = ref(false)
const ruleLoading = ref(false)
const ruleRows = ref<OverheadAllocationRuleVO[]>([])
const rulePageNo = ref(1)
const rulePageSize = ref(10)
const ruleTotal = ref(0)
let ruleRequestId = 0

const ruleColumns = [
  { title: '成本科目 ID', dataIndex: 'costSubjectId' },
  { title: '分摊依据', dataIndex: 'allocationBasis' },
  { title: '分摊周期', dataIndex: 'allocationCycle' },
  { title: '状态', dataIndex: 'status' },
]

async function fetchAllocationRules() {
  const requestId = ++ruleRequestId
  ruleLoading.value = true
  ruleRows.value = []
  ruleTotal.value = 0
  try {
    const result = await getOverheadAllocationRules(rulePageNo.value, rulePageSize.value)
    if (requestId !== ruleRequestId) return
    ruleRows.value = result.records
    ruleTotal.value = result.total
  } catch (error: unknown) {
    console.error(error)
    if (requestId === ruleRequestId) message.error('加载间接费规则失败')
  } finally {
    if (requestId === ruleRequestId) ruleLoading.value = false
  }
}

function openRuleModal() {
  rulePageNo.value = 1
  ruleModalOpen.value = true
  void fetchAllocationRules()
}

function handleRulePageChange(page: number, pageSize: number) {
  rulePageNo.value = page
  rulePageSize.value = pageSize
  void fetchAllocationRules()
}
const allocationModalOpen = ref(false)
const allocationSubmitting = ref(false)
const allocationMonth = ref<string>()
const allocationPeriod = computed(() =>
  allocationMonth.value
    ? dayjs(`${allocationMonth.value}-01`).endOf('month').format('YYYY-MM-DD')
    : '',
)

function disableIncompleteMonth(current: Dayjs) {
  return !current.startOf('month').isBefore(dayjs().startOf('month'))
}

function openAllocationModal() {
  allocationMonth.value = dayjs().subtract(1, 'month').format('YYYY-MM')
  allocationModalOpen.value = true
}

async function confirmAllocation() {
  if (!allocationPeriod.value) {
    message.warning('请选择目标月份')
    return
  }
  allocationSubmitting.value = true
  try {
    const result = await executeOverheadAllocation(allocationPeriod.value)
    if (result.idempotent) {
      message.info(`${result.period} 已执行，无需重复生成成本`)
    } else {
      message.success(
        `分摊完成：生成 ${result.costItemCount} 条成本，共 ¥${result.allocatedAmount}`,
      )
    }
    allocationModalOpen.value = false
    handleSearch()
  } catch (error: unknown) {
    console.error(error)
    message.error('间接费分摊失败，未生成部分成本，请核对后重试')
  } finally {
    allocationSubmitting.value = false
  }
}

const referenceStore = useReferenceStore()
const {
  projects: projectList,
  contracts: contractList,
  partners: partnerList,
} = storeToRefs(referenceStore)

const contractOptions = ref(contractList.value ?? [])
const costSubjectOptions = ref<{ id: string; subjectName: string }[]>([])
const projectOptions = computed(() => projectList.value ?? [])

async function loadCostSubjectOptions() {
  try {
    costSubjectOptions.value = await getCostSubjectList()
  } catch {
    costSubjectOptions.value = []
  }
}

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  costSubjectId: undefined as string | undefined,
  costType: undefined as string | undefined,
  sourceType: undefined as string | undefined,
  costStatus: undefined as string | undefined,
  dateRange: null as string[] | null,
  keyword: '',
})
const filterVisibility = reactive({
  projectId: true,
  contractId: true,
  partnerId: true,
  costSubjectId: true,
  costType: true,
  sourceType: true,
  costStatus: true,
  dateRange: true,
})
const filterSettingItems = [
  { key: 'projectId', label: '项目' },
  { key: 'contractId', label: '合同' },
  { key: 'partnerId', label: '合作方' },
  { key: 'costSubjectId', label: '成本科目' },
  { key: 'costType', label: '成本类型' },
  { key: 'sourceType', label: '来源类型' },
  { key: 'costStatus', label: '成本状态' },
  { key: 'dateRange', label: '成本日期' },
] as const

const loading = ref(false)
const tableData = ref<CostLedgerVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const summary = ref<CostLedgerSummaryVO>({
  totalAmount: '0',
  totalTaxAmount: '0',
  bySourceType: {},
  byProject: {},
  byCostType: {},
})

function emptySummary(): CostLedgerSummaryVO {
  return {
    totalAmount: '0',
    totalTaxAmount: '0',
    bySourceType: {},
    byProject: {},
    byCostType: {},
  }
}

function normalizeSummary(
  value: Partial<CostLedgerSummaryVO> | null | undefined,
): CostLedgerSummaryVO {
  return {
    ...emptySummary(),
    ...(value ?? {}),
    bySourceType: value?.bySourceType ?? {},
    byProject: value?.byProject ?? {},
    byCostType: value?.byCostType ?? {},
  }
}

const detailVisible = ref(false)
const detailItem = ref<CostLedgerVO | null>(null)

async function loadContractOptions(projectId?: string) {
  try {
    if (!projectId) {
      contractOptions.value = await referenceStore.fetchContracts()
      return
    }
    contractOptions.value = await referenceStore.fetchContracts({ projectId })
  } catch (e: unknown) {
    console.error(e)
    contractOptions.value = []
  }
}

async function onProjectChange(val: string | undefined) {
  filter.contractId = undefined
  filter.partnerId = undefined
  await loadContractOptions(val)
}

async function handleProjectFilterChange(val: string | undefined) {
  await onProjectChange(val)
  handleSearch()
}

async function fetchData() {
  loading.value = true
  const params: CostLedgerQueryParams = {
    pageNo: pageNo.value,
    pageSize: pageSize.value,
    projectId: filter.projectId,
    contractId: filter.contractId,
    partnerId: filter.partnerId,
    costSubjectId: filter.costSubjectId,
    costType: filter.costType,
    sourceType: filter.sourceType,
    costStatus: filter.costStatus,
    startDate: filter.dateRange?.[0],
    endDate: filter.dateRange?.[1],
    keyword: filter.keyword || undefined,
  }
  try {
    const res: PageResult<CostLedgerVO> = await getCostLedger(params)
    tableData.value = res.records
    total.value = Number(res.total) || 0
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载成本列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchSummary() {
  try {
    summary.value = normalizeSummary(
      await getCostLedgerSummary({
        projectId: filter.projectId,
        contractId: filter.contractId,
        partnerId: filter.partnerId,
        costSubjectId: filter.costSubjectId,
        costType: filter.costType,
        sourceType: filter.sourceType,
        costStatus: filter.costStatus,
        startDate: filter.dateRange?.[0],
        endDate: filter.dateRange?.[1],
        keyword: filter.keyword || undefined,
      }),
    )
  } catch (e: unknown) {
    console.error(e)
    summary.value = emptySummary()
    message.error('加载成本汇总失败')
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
  fetchSummary()
}

function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.costSubjectId = undefined
  filter.costType = undefined
  filter.sourceType = undefined
  filter.costStatus = undefined
  filter.dateRange = null
  filter.keyword = ''
  contractOptions.value = contractList.value ?? []
  pageNo.value = 1
  handleSearch()
}

function toggleFilterVisibility(key: (typeof filterSettingItems)[number]['key']) {
  filterVisibility[key] = !filterVisibility[key]
}

function handlePageChange(page: number) {
  pageNo.value = page
  fetchData()
}

function handleShowSizeChange(_current: number, size: number) {
  pageSize.value = size
  pageNo.value = 1
  fetchData()
}

function applyRouteQuery() {
  const projectId = route.query.projectId
  filter.projectId = typeof projectId === 'string' ? projectId : undefined
}

async function showDetail(record: CostLedgerVO) {
  detailVisible.value = true
  try {
    detailItem.value = await getCostLedgerDetail(record.id)
  } catch (e: unknown) {
    console.error(e)
    detailItem.value = record
  }
}

function closeDetail() {
  detailVisible.value = false
}

function fmtWan(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtAmountYuan(val: string | undefined): string {
  if (!val) return '¥0.00'
  const n = parseFloat(val)
  if (isNaN(n)) return '¥0.00'
  return '¥' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function costTypeLabel(value: string | undefined) {
  return getCostTypeLabel(value)
}

const lockedAmount = computed(() => {
  const bySource = summary.value.bySourceType
  const byCostType = summary.value.byCostType
  return bySource.CT_CONTRACT ?? byCostType.CONTRACT_LOCKED ?? summary.value.totalAmount
})

const kpiStats = computed(() => {
  const totalValue = parseFloat(summary.value.totalAmount) || 0
  const locked = parseFloat(lockedAmount.value) || 0
  return {
    total: totalValue,
    locked,
    actual: totalValue,
    dynamic: totalValue,
    deviation: totalValue - locked,
  }
})

interface RailItem {
  label: string
  amount: string
}

const subjectBreakdown = computed<RailItem[]>(() => {
  const entries = Object.entries(summary.value.byCostType).map(([key, val]) => ({
    label: getCostTypeLabel(key),
    amount: val,
  }))
  return entries.length
    ? entries.sort((a, b) => parseFloat(b.amount) - parseFloat(a.amount))
    : [{ label: '暂无数据', amount: '0' }]
})

const sourceBreakdown = computed<RailItem[]>(() => {
  const entries = Object.entries(summary.value.bySourceType).map(([key, val]) => ({
    label: getSourceTypeLabel(key),
    amount: val,
  }))
  return entries.length
    ? entries.sort((a, b) => parseFloat(b.amount) - parseFloat(a.amount))
    : [{ label: '暂无数据', amount: '0' }]
})

const maxAmount = computed(() =>
  Math.max(
    ...subjectBreakdown.value.map((item) => parseFloat(item.amount) || 0),
    ...sourceBreakdown.value.map((item) => parseFloat(item.amount) || 0),
    1,
  ),
)

function barPercent(amount: string): string {
  const n = parseFloat(amount) || 0
  if (maxAmount.value === 0) return '0%'
  return ((n / maxAmount.value) * 100).toFixed(1) + '%'
}

const gridColumns = computed(() => [
  { field: 'id', title: '成本编号', minWidth: 190, ellipsis: true },
  { field: 'costSubjectName', title: '成本科目', minWidth: 150, ellipsis: true },
  { field: 'sourceType', title: '来源类型', width: 120, slots: { default: 'sourceType' } },
  {
    field: 'amount',
    title: '金额',
    width: 128,
    align: 'right' as const,
    slots: { default: 'amount' },
  },
  { field: 'costDate', title: '成本日期', width: 110 },
  { field: 'costStatus', title: '状态', width: 90, slots: { default: 'costStatus' } },
  { title: '操作', width: 76, slots: { default: 'ops' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('cost_ledger_cols', gridColumns)

onMounted(async () => {
  await fetchDictData(COST_TYPE_DICT)
  applyRouteQuery()
  referenceStore.fetchProjects()
  referenceStore.fetchPartners()
  void loadContractOptions(filter.projectId)
  void loadCostSubjectOptions()
  fetchData()
  fetchSummary()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page cost-ledger-page project-operation-list-page">
    <div class="lg-page-head cost-ledger-page-head">
      <div class="cost-ledger-head-main">
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>成本管理</a-breadcrumb-item>
          <a-breadcrumb-item>成本列表</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
      <a-space>
        <a-button
          v-if="canViewAllocationRules"
          data-testid="view-overhead-allocation-rules"
          @click="openRuleModal"
        >
          查看间接费规则
        </a-button>
        <a-button
          v-if="canExecuteAllocation"
          type="primary"
          data-testid="execute-overhead-allocation"
          @click="openAllocationModal"
        >
          执行间接费分摊
        </a-button>
      </a-space>
    </div>

    <div class="lg-grid cost-ledger-grid project-operation-workspace">
      <div class="lg-left cost-ledger-main project-operation-main-column">
        <CostLedgerOverview
          :is-mobile="isMobile"
          :kpi-stats="kpiStats"
          :filter="filter"
          :filter-visibility="filterVisibility"
          :filter-setting-items="filterSettingItems"
          :project-list="projectOptions"
          :contract-options="contractOptions"
          :partner-list="partnerList ?? []"
          :cost-subject-options="costSubjectOptions"
          :fmt-wan="fmtWan"
          :bar-percent="barPercent"
          :handle-search="handleSearch"
          :handle-reset="handleReset"
          :handle-project-filter-change="handleProjectFilterChange"
          :toggle-filter-visibility="toggleFilterVisibility"
        />

        <CostLedgerTablePanel
          :is-mobile="isMobile"
          :loading="loading"
          :table-data="tableData"
          :total="total"
          :page-no="pageNo"
          :page-size="pageSize"
          :visible-grid-columns="visibleGridColumns"
          :column-settings="columnSettings"
          :col-visible="colVisible"
          :fmt-wan="fmtWan"
          :fmt-amount-yuan="fmtAmountYuan"
          :handle-search="handleSearch"
          :handle-page-change="handlePageChange"
          :handle-show-size-change="handleShowSizeChange"
          :show-detail="showDetail"
          :toggle-col="toggleCol"
        />
      </div>

      <CostLedgerAnalysisRail
        :subject-breakdown="subjectBreakdown"
        :source-breakdown="sourceBreakdown"
        :fmt-wan="fmtWan"
        :bar-percent="barPercent"
        :handle-search="handleSearch"
      />
    </div>

    <CostLedgerDetailDrawer
      :open="detailVisible"
      :detail-item="detailItem"
      :fmt-amount-yuan="fmtAmountYuan"
      :cost-type-label="costTypeLabel"
      @close="closeDetail"
    />

    <a-modal v-model:open="ruleModalOpen" title="间接费规则" :footer="null" width="760px">
      <a-table
        row-key="id"
        :columns="ruleColumns"
        :data-source="ruleRows"
        :loading="ruleLoading"
        :pagination="{
          current: rulePageNo,
          pageSize: rulePageSize,
          total: ruleTotal,
          showSizeChanger: true,
        }"
        @change="
          (pagination: { current?: number; pageSize?: number }) =>
            handleRulePageChange(pagination.current ?? 1, pagination.pageSize ?? 10)
        "
      />
    </a-modal>

    <a-modal
      v-model:open="allocationModalOpen"
      title="确认执行间接费分摊"
      ok-text="确认执行"
      cancel-text="取消"
      :confirm-loading="allocationSubmitting"
      :ok-button-props="{ disabled: !allocationPeriod }"
      :mask-closable="!allocationSubmitting"
      @ok="confirmAllocation"
    >
      <a-form layout="vertical">
        <a-form-item label="目标月份" required>
          <a-date-picker
            v-model:value="allocationMonth"
            picker="month"
            value-format="YYYY-MM"
            :disabled-date="disableIncompleteMonth"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
      <a-alert type="warning" show-icon>
        <template #message>期间：{{ allocationPeriod || '请选择月份' }}</template>
        <template #description>
          仅可选择已完整结束的月份。将按现有启用规则向活跃项目生成已确认成本并刷新成本汇总。相同租户、规则和月份不可重复生成；提交后请等待结果，不要重复点击。
        </template>
      </a-alert>
    </a-modal>
  </div>
</template>

<style scoped>
.cost-ledger-page {
  background: var(--surface-subtle);
}

.cost-ledger-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
  gap: 16px;
}

.cost-ledger-head-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.cl-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.cost-ledger-grid {
}

.cost-ledger-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

@media (max-width: 1200px) {
  .cost-ledger-page-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
