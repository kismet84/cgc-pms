<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { message } from 'ant-design-vue'
import {
  DollarOutlined,
  LockOutlined,
  MoreOutlined,
  ToolOutlined,
  ReloadOutlined,
  SearchOutlined,
  AlertOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { getCostLedger, getCostLedgerSummary, getCostLedgerDetail } from '@/api/modules/cost'
import { getCostSubjectList } from '@/api/modules/costSubject'
import type {
  CostLedgerVO,
  CostLedgerQueryParams,
  CostLedgerSummaryVO,
  SourceType,
} from '@/types/cost'
import { SOURCE_TYPE_LABEL, SOURCE_TYPE_COLOR } from '@/types/cost'
import type { PageResult } from '@/types/api'
import { useReferenceStore } from '@/stores/reference'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
const route = useRoute()
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}

// ---- Reference store ----
const referenceStore = useReferenceStore()
const { projects: projectList, contracts: contractList, partners: partnerList } =
  storeToRefs(referenceStore)

// ---- Dropdown data ----
const contractOptions = ref(contractList.value ?? [])
const costSubjectOptions = ref<{ id: string; subjectName: string }[]>([])

async function loadCostSubjectOptions() {
  try {
    costSubjectOptions.value = await getCostSubjectList()
  } catch {
    costSubjectOptions.value = []
  }
}

// ---- Filter state ----
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

// ---- Table state ----
const loading = ref(false)
const tableData = ref<CostLedgerVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

// ---- KPI state ----
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

// ---- Detail drawer ----
const detailVisible = ref(false)
const detailItem = ref<CostLedgerVO | null>(null)

async function loadContractOptions(projectId?: string) {
  try {
    if (!projectId) {
      contractOptions.value = await referenceStore.fetchContracts()
      return
    }
    const contracts = await referenceStore.fetchContracts({ projectId })
    contractOptions.value = contracts
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

// ---- Fetch data ----
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

// ---- Format helpers ----
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

// ---- Cost type label map ----
const COST_TYPE_LABEL: Record<string, string> = {
  CONTRACT_LOCKED: '合同锁定成本',
  ACTUAL_COST: '实际成本',
  TARGET_COST: '成本目标',
  PAID_AMOUNT: '已付款',
  DYNAMIC_COST: '动态成本',
  CT_CONTRACT: '合同锁定成本',
  CT_DIRECT: '直接成本',
  CT_INDIRECT: '间接成本',
  CT_MATERIAL: '材料成本',
  CT_MACHINE: '机械使用成本',
  CT_SUBCONTRACT: '分包成本',
  CT_LABOR: '人工成本',
  CT_OTHER: '其他成本',
  MATERIAL_RECEIPT: '材料验收成本',
  MAT_RECEIPT: '材料验收成本',
  SUB_MEASURE: '分包计量成本',
  VAR_ORDER: '签证变更成本',
  VARIATION: '签证变更成本',
  CT_CHANGE: '合同变更成本',
}

// ---- Computed KPI ----
const lockedAmount = computed(() => {
  const bySource = summary.value.bySourceType
  const byCostType = summary.value.byCostType
  return bySource['CT_CONTRACT'] ?? byCostType['CONTRACT_LOCKED'] ?? summary.value.totalAmount
})

const kpiStats = computed(() => {
  const total = parseFloat(summary.value.totalAmount) || 0
  const locked = parseFloat(lockedAmount.value) || 0
  const actual = total
  return {
    total,
    locked,
    actual,
    dynamic: total,
    deviation: total - locked,
  }
})

// ---- Analysis rail data ----
interface RailItem {
  label: string
  amount: string
}

const subjectBreakdown = computed<RailItem[]>(() => {
  const map = summary.value.byCostType
  const entries = Object.entries(map).map(([key, val]) => ({
    label: COST_TYPE_LABEL[key] ?? key,
    amount: val,
  }))
  return entries.length
    ? entries.sort((a, b) => parseFloat(b.amount) - parseFloat(a.amount))
    : [{ label: '暂无数据', amount: '0' }]
})

const sourceBreakdown = computed<RailItem[]>(() => {
  const map = summary.value.bySourceType
  const entries = Object.entries(map).map(([key, val]) => ({
    label: SOURCE_TYPE_LABEL[key as SourceType] ?? key,
    amount: val,
  }))
  return entries.length
    ? entries.sort((a, b) => parseFloat(b.amount) - parseFloat(a.amount))
    : [{ label: '暂无数据', amount: '0' }]
})

const maxAmount = computed(() => {
  return Math.max(
    ...subjectBreakdown.value.map((item) => parseFloat(item.amount) || 0),
    ...sourceBreakdown.value.map((item) => parseFloat(item.amount) || 0),
    1,
  )
})

function barPercent(amount: string): string {
  const n = parseFloat(amount) || 0
  if (maxAmount.value === 0) return '0%'
  return ((n / maxAmount.value) * 100).toFixed(1) + '%'
}

// ---- Column defs (vxe-grid) ----
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

// ---- Init ----
onMounted(async () => {
  window.addEventListener('resize', onResize)
  applyRouteQuery()
  referenceStore.fetchProjects()
  referenceStore.fetchPartners()
  void loadContractOptions(filter.projectId)
  void loadCostSubjectOptions()
  fetchData()
  fetchSummary()
})
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="lg-list-page lg-page app-page cost-ledger-page">
    <div class="lg-page-head cost-ledger-page-head">
      <div class="cost-ledger-head-main">
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>成本管理</a-breadcrumb-item>
          <a-breadcrumb-item>成本列表</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid cost-ledger-grid">
      <div class="lg-left cost-ledger-main">
        <section v-if="!isMobile" class="lg-kpi-strip cost-ledger-kpi-summary" aria-label="成本关键指标">
          <div class="cost-ledger-kpi-item">
            <span class="cost-ledger-kpi-icon is-total"><DollarOutlined /></span>
            <span class="cost-ledger-kpi-label">成本总额</span>
            <span class="cost-ledger-kpi-value">
              {{ fmtWan(String(kpiStats.total)) }} <small>万元</small>
            </span>
          </div>
          <div class="cost-ledger-kpi-item is-wide">
            <span class="cost-ledger-kpi-icon is-amount"><LockOutlined /></span>
            <span class="cost-ledger-kpi-label">锁定成本</span>
            <span class="cost-ledger-kpi-value">
              {{ fmtWan(String(kpiStats.locked)) }} <small>万元</small>
            </span>
          </div>
          <div class="cost-ledger-kpi-item is-progress">
            <span class="cost-ledger-kpi-icon is-paid"><ToolOutlined /></span>
            <span class="cost-ledger-kpi-label">实际成本</span>
            <span class="cost-ledger-kpi-value">
              {{ fmtWan(String(kpiStats.actual)) }} <small>万元</small>
            </span>
            <span class="cost-ledger-kpi-progress">
              <span :style="{ width: barPercent(String(kpiStats.actual)) }"></span>
            </span>
          </div>
          <div class="cost-ledger-kpi-item is-progress is-dynamic">
            <span class="cost-ledger-kpi-icon is-unpaid"><ToolOutlined /></span>
            <span class="cost-ledger-kpi-label">动态成本</span>
            <span class="cost-ledger-kpi-value">
              {{ fmtWan(String(kpiStats.dynamic)) }} <small>万元</small>
            </span>
            <span class="cost-ledger-kpi-progress">
              <span :style="{ width: barPercent(String(kpiStats.dynamic)) }"></span>
            </span>
          </div>
          <div class="cost-ledger-kpi-item is-overdue">
            <span class="cost-ledger-kpi-icon is-overdue"><AlertOutlined /></span>
            <span class="cost-ledger-kpi-label">偏差金额</span>
            <span class="cost-ledger-kpi-value">
              {{ fmtWan(String(kpiStats.deviation)) }} <small>万元</small>
            </span>
          </div>
        </section>
        <div v-else class="lg-kpi-single">
          <div
            class="lg-kpi-single-row"
            v-for="item in [
              {
                icon: DollarOutlined,
                bg: 'var(--kpi-total)',
                label: '成本总额',
                value: fmtWan(String(kpiStats.total)),
                unit: '万元',
              },
              {
                icon: LockOutlined,
                bg: 'var(--kpi-amount)',
                label: '锁定成本',
                value: fmtWan(String(kpiStats.locked)),
                unit: '万元',
              },
              {
                icon: ToolOutlined,
                bg: 'var(--kpi-paid)',
                label: '动态成本',
                value: fmtWan(String(kpiStats.dynamic)),
                unit: '万元',
              },
              {
                icon: AlertOutlined,
                bg: 'var(--kpi-overdue)',
                label: '偏差金额',
                value: fmtWan(String(kpiStats.deviation)),
                unit: '万元',
              },
            ]"
            :key="item.label"
          >
            <div class="lg-kpi-single-icon" :style="{ background: item.bg }">
              <component :is="item.icon" />
            </div>
            <span class="lg-kpi-single-label">{{ item.label }}</span>
            <span class="lg-kpi-single-value"
              >{{ item.value }} <small>{{ item.unit }}</small></span
            >
          </div>
        </div>

        <div class="lg-search-bar cost-ledger-query-panel">
          <div class="cost-ledger-query-primary">
            <a-select
              v-if="filterVisibility.projectId"
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              class="cost-ledger-query-select"
              size="large"
              @change="handleProjectFilterChange"
            >
              <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.contractId"
              v-model:value="filter.contractId"
              placeholder="合同"
              allow-clear
              class="cost-ledger-query-select"
              size="large"
              @change="handleSearch"
            >
              <a-select-option
                v-for="contract in contractOptions"
                :key="contract.id"
                :value="contract.id"
              >
                {{ contract.contractName }}
              </a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.partnerId"
              v-model:value="filter.partnerId"
              placeholder="合作方"
              allow-clear
              class="cost-ledger-query-select"
              size="large"
              @change="handleSearch"
            >
              <a-select-option
                v-for="partner in partnerList ?? []"
                :key="partner.id"
                :value="partner.id"
              >
                {{ partner.partnerName }}
              </a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.costSubjectId"
              v-model:value="filter.costSubjectId"
              placeholder="成本科目"
              allow-clear
              class="cost-ledger-query-select"
              size="large"
              @change="handleSearch"
            >
              <a-select-option
                v-for="subject in costSubjectOptions"
                :key="subject.id"
                :value="subject.id"
              >
                {{ subject.subjectName }}
              </a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.costType"
              v-model:value="filter.costType"
              placeholder="成本类型"
              allow-clear
              class="cost-ledger-query-select"
              size="large"
              @change="handleSearch"
            >
              <a-select-option value="MATERIAL">材料费</a-select-option>
              <a-select-option value="LABOR">人工费</a-select-option>
              <a-select-option value="MACHINERY">机械费</a-select-option>
              <a-select-option value="SUBCONTRACT">分包费</a-select-option>
              <a-select-option value="OTHER">其他</a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.sourceType"
              v-model:value="filter.sourceType"
              placeholder="来源类型"
              allow-clear
              class="cost-ledger-query-select"
              size="large"
              @change="handleSearch"
            >
              <a-select-option value="PURCHASE_ORDER">采购单</a-select-option>
              <a-select-option value="SUB_MEASURE">分包计量</a-select-option>
              <a-select-option value="SETTLEMENT">结算单</a-select-option>
              <a-select-option value="MANUAL">手工录入</a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.costStatus"
              v-model:value="filter.costStatus"
              placeholder="成本状态"
              allow-clear
              class="cost-ledger-status-select"
              size="large"
              @change="handleSearch"
            >
              <a-select-option value="CONFIRMED">已确认</a-select-option>
              <a-select-option value="PENDING">待确认</a-select-option>
            </a-select>
            <a-range-picker
              v-if="filterVisibility.dateRange"
              v-model:value="filter.dateRange"
              class="cost-ledger-query-range"
              size="large"
              value-format="YYYY-MM-DD"
              @change="handleSearch"
            />
          </div>
          <div class="cost-ledger-query-keyword-row">
            <a-input
              v-model:value="filter.keyword"
              class="cost-ledger-keyword-search"
              placeholder="搜索编号、科目名、类型、项目、合同…"
              allow-clear
              size="large"
              @press-enter="handleSearch"
            >
              <template #prefix><SearchOutlined class="cost-ledger-search-prefix-icon" /></template>
            </a-input>
            <div class="cost-ledger-query-actions">
              <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
              <a-button size="large" @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
              <a-dropdown trigger="click">
                <a-button size="large">
                  <template #icon><SettingOutlined /></template>
                  筛选栏设置
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item
                      v-for="item in filterSettingItems"
                      :key="item.key"
                      @click="toggleFilterVisibility(item.key)"
                    >
                      <a-checkbox :checked="filterVisibility[item.key]">
                        {{ item.label }}
                      </a-checkbox>
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
          </div>
        </div>

        <main class="lg-list-table-panel cost-ledger-table-panel">
          <div class="lg-toolbar cost-toolbar">
            <div class="lg-toolbar-left">
              <div class="cost-ledger-table-heading">
                <span class="cost-ledger-table-title">成本记录</span>
                <span class="cost-ledger-table-count">共 {{ total }} 条</span>
              </div>
            </div>
            <div class="lg-toolbar-right cost-toolbar-right">
              <ColumnSettingsButton
                v-if="!isMobile"
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button aria-label="刷新成本列表" title="刷新成本列表" @click="handleSearch">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
          </div>

          <div v-if="isMobile" class="cost-ledger-mobile-list">
            <div v-if="loading" class="cost-ledger-mobile-state">
              <a-spin />
            </div>
            <div v-else-if="!tableData.length" class="cost-ledger-mobile-state">
              <a-empty description="暂无成本记录" />
            </div>
            <template v-else>
              <button
                v-for="row in tableData"
                :key="row.id"
                type="button"
                class="cost-ledger-mobile-card"
                @click="showDetail(row)"
              >
                <div class="cost-ledger-mobile-card-head">
                  <span class="cost-ledger-mobile-subject">{{ row.costSubjectName || '-' }}</span>
                  <span class="cost-ledger-mobile-amount">{{ fmtAmountYuan(row.amount) }}</span>
                </div>
                <div class="cost-ledger-mobile-meta">项目：{{ row.projectName || '-' }}</div>
                <div class="cost-ledger-mobile-meta">合同：{{ row.contractName || '-' }}</div>
                <div class="cost-ledger-mobile-meta">来源：{{ SOURCE_TYPE_LABEL[row.sourceType as SourceType] || row.sourceType || '-' }}</div>
                <div class="cost-ledger-mobile-meta">状态：{{ row.costStatus === 'CONFIRMED' ? '已确认' : row.costStatus === 'PENDING' ? '待确认' : row.costStatus || '-' }}</div>
              </button>
            </template>
          </div>
          <div v-else class="lg-table-wrap cost-ledger-table-wrap">
            <vxe-grid
              :data="tableData"
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #sourceType="{ row }">
                <a-tag
                  :color="SOURCE_TYPE_COLOR[row.sourceType as SourceType] || 'default'"
                  size="small"
                >
                  {{ SOURCE_TYPE_LABEL[row.sourceType as SourceType] || row.sourceType }}
                </a-tag>
              </template>
              <template #amount="{ row }">
                <span class="lg-money">{{ fmtWan(row.amount) }}</span>
              </template>
              <template #costStatus="{ row }">
                <a-tag
                  :color="
                    row.costStatus === 'CONFIRMED'
                      ? 'success'
                      : row.costStatus === 'PENDING'
                        ? 'processing'
                        : 'default'
                  "
                  size="small"
                >
                  {{
                    row.costStatus === 'CONFIRMED'
                      ? '已确认'
                      : row.costStatus === 'PENDING'
                        ? '待确认'
                        : row.costStatus
                  }}
                </a-tag>
              </template>
              <template #ops="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="showDetail(row)">详情</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
            </vxe-grid>
          </div>

          <div class="lg-pagination">
            <span class="lg-total">共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :page-size-options="['10', '20', '50', '100']"
              show-size-changer
              show-quick-jumper
              @change="handlePageChange"
              @show-size-change="handleShowSizeChange"
            />
          </div>
        </main>
      </div>

      <aside class="lg-analysis-rail cost-ledger-analysis-rail" aria-label="成本辅助分析">
        <div class="cost-ledger-analysis-panel">
          <header class="cost-ledger-analysis-head">
            <div>
              <div class="cost-ledger-analysis-title">成本分析</div>
            </div>
            <a-button type="link" size="small" @click="handleSearch">刷新</a-button>
          </header>

          <section class="cost-ledger-analysis-section">
            <div class="cost-ledger-section-title">成本科目占比</div>
            <div v-for="item in subjectBreakdown" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: 'var(--kpi-paid)' }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: barPercent(item.amount), background: 'var(--kpi-paid)' }"
                ></span>
              </span>
              <span class="lg-type-num">{{ fmtWan(item.amount) }}</span>
              <span class="lg-type-pct">{{ barPercent(item.amount) }}</span>
            </div>
          </section>

          <section class="cost-ledger-analysis-section">
            <div class="cost-ledger-section-title">来源类型分布</div>
            <div v-for="item in sourceBreakdown" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: 'var(--kpi-amount)' }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: barPercent(item.amount), background: 'var(--kpi-amount)' }"
                ></span>
              </span>
              <span class="lg-type-num">{{ fmtWan(item.amount) }}</span>
              <span class="lg-type-pct">{{ barPercent(item.amount) }}</span>
            </div>
          </section>

          <section class="cost-ledger-analysis-section">
            <div class="lg-warning-head">
              <div class="cost-ledger-section-title">超预算预警</div>
              <span class="cost-ledger-warning-count">
                {{ sourceBreakdown.filter((i) => parseFloat(i.amount) > 0).length }} 项
              </span>
            </div>
            <div
              v-for="item in sourceBreakdown.filter((i) => parseFloat(i.amount) > 0).slice(0, 5)"
              :key="'warn-' + item.label"
              class="lg-warning-item"
            >
              <span class="lg-warning-project">{{ item.label }}</span>
              <span class="lg-warning-days">{{ fmtWan(item.amount) }} 万</span>
            </div>
            <div
              v-if="sourceBreakdown.every((i) => parseFloat(i.amount) === 0)"
              class="lg-warning-empty"
            >
              暂无超预算项
            </div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Detail drawer -->
    <a-drawer
      :open="detailVisible"
      title="成本明细"
      placement="right"
      :width="800"
      class="cost-ledger-detail-drawer"
      @close="detailVisible = false"
    >
      <template v-if="detailItem">
        <div class="cost-ledger-detail-summary">
          <div>
            <span>成本科目</span>
            <strong>{{ detailItem.costSubjectName || '-' }}</strong>
          </div>
          <div>
            <span>金额含税</span>
            <strong>{{ fmtAmountYuan(detailItem.amount) }}</strong>
          </div>
          <div>
            <span>来源类型</span>
            <a-tag :color="SOURCE_TYPE_COLOR[detailItem.sourceType as SourceType] || 'default'">
              {{ SOURCE_TYPE_LABEL[detailItem.sourceType as SourceType] || detailItem.sourceType }}
            </a-tag>
          </div>
        </div>
        <a-descriptions class="cost-ledger-detail-descriptions" :column="2" size="small" bordered>
          <a-descriptions-item label="成本科目">{{
            detailItem.costSubjectName || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="费用类型">{{
            COST_TYPE_LABEL[detailItem.costType] || detailItem.costType || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="来源类型">
            <a-tag :color="SOURCE_TYPE_COLOR[detailItem.sourceType as SourceType] || 'default'">
              {{ SOURCE_TYPE_LABEL[detailItem.sourceType as SourceType] || detailItem.sourceType }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="金额(含税)">{{
            fmtAmountYuan(detailItem.amount)
          }}</a-descriptions-item>
          <a-descriptions-item label="税额">{{
            fmtAmountYuan(detailItem.taxAmount)
          }}</a-descriptions-item>
          <a-descriptions-item label="不含税金额">{{
            fmtAmountYuan(detailItem.amountWithoutTax)
          }}</a-descriptions-item>
          <a-descriptions-item label="生成标识">{{
            detailItem.generatedFlag === '1' ? '自动生成' : '手动录入'
          }}</a-descriptions-item>
          <a-descriptions-item label="来源单据ID">{{
            detailItem.sourceId || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="来源明细ID">{{
            detailItem.sourceItemId || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="创建人">{{
            detailItem.createdBy || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{
            detailItem.createdAt || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="2">{{
            detailItem.remark || '-'
          }}</a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.cost-ledger-page {
  gap: 14px;
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

.cost-ledger-head-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.cost-ledger-query-panel {
  align-items: stretch;
  flex-direction: column;
  gap: 12px;
  margin: 0;
  min-height: 74px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cost-ledger-query-primary {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.cost-ledger-query-keyword-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.cost-ledger-keyword-search {
  flex: 1 1 auto;
  min-width: 320px;
}

.cost-ledger-search-prefix-icon {
  color: var(--text-secondary);
}

.cost-ledger-query-select {
  width: 160px;
}

.cost-ledger-status-select {
  width: 132px;
}

.cost-ledger-query-range {
  width: 260px;
}

.cost-ledger-query-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.cost-ledger-grid {
  align-items: stretch;
  min-height: 0;
  height: calc(100vh - 74px);
}

.cost-ledger-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.cost-ledger-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  min-height: 0;
}

.cost-toolbar {
  flex: 0 0 auto;
  border-bottom: 1px solid var(--border-subtle);
}

.cost-ledger-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.cost-ledger-table-heading,
.cost-toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cost-ledger-table-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.cost-ledger-table-wrap {
  flex: 1 1 auto;
  min-height: 0;
}

.cost-ledger-table-panel > .lg-pagination {
  flex: 0 0 auto;
}

.cost-ledger-table-wrap :deep(.vxe-grid) {
  height: 100%;
}

.cost-ledger-mobile-list {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 12px;
  min-height: 320px;
  padding: 12px;
  background: var(--surface-subtle);
}

.cost-ledger-mobile-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.cost-ledger-mobile-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 14px;
  color: var(--text);
  text-align: left;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cost-ledger-mobile-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.cost-ledger-mobile-subject,
.cost-ledger-mobile-amount {
  font-weight: 700;
}

.cost-ledger-mobile-amount {
  color: var(--primary);
}

.cost-ledger-mobile-meta {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.cost-ledger-table-wrap :deep(.vxe-header--column .vxe-cell) {
  justify-content: center;
  text-align: center;
}

.cost-ledger-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  margin-bottom: 0;
  overflow: hidden;
  min-height: 84px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cost-ledger-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px 8px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.cost-ledger-kpi-item:last-child {
  border-right: 0;
}

.cost-ledger-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.cost-ledger-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.cost-ledger-kpi-icon.is-paid {
  color: var(--success);
  background: var(--success-soft);
}

.cost-ledger-kpi-icon.is-unpaid {
  color: var(--primary);
  background: var(--surface-tint);
}

.cost-ledger-kpi-icon.is-overdue {
  color: var(--error);
  background: var(--error-soft);
}

.cost-ledger-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-ledger-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-ledger-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.cost-ledger-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.cost-ledger-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.cost-ledger-kpi-item.is-dynamic .cost-ledger-kpi-progress > span {
  background: var(--kpi-unpaid);
}

.cost-ledger-analysis-rail {
  width: 336px;
  min-height: 0;
}

.cost-ledger-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
  height: 100%;
  padding: 0 0 12px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cost-ledger-detail-drawer :deep(.ant-drawer-body) {
  background: var(--surface-subtle);
}

.cost-ledger-detail-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  overflow: hidden;
  margin-bottom: 12px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cost-ledger-detail-summary > div {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 14px 16px;
  border-right: 1px solid var(--border-subtle);
}

.cost-ledger-detail-summary > div:last-child {
  border-right: 0;
}

.cost-ledger-detail-summary span {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.cost-ledger-detail-summary strong {
  overflow: hidden;
  color: var(--text);
  font-size: 18px;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-ledger-detail-descriptions {
  background: var(--surface);
  border-radius: var(--radius-lg);
}

.cost-ledger-analysis-head,
.cost-ledger-analysis-section .lg-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.cost-ledger-analysis-head {
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.cost-ledger-analysis-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 20px;
}

.cost-ledger-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.cost-ledger-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 10px 16px 0;
}

.cost-ledger-analysis-section + .cost-ledger-analysis-section {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.cost-ledger-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.cost-ledger-analysis-section :deep(.lg-type-row),
.cost-ledger-analysis-section .lg-type-row {
  display: grid;
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 42px 46px;
  align-items: center;
  gap: 8px;
  color: var(--text);
  line-height: 1.5;
}

.cost-ledger-analysis-section .lg-type-dot {
  margin-top: 0;
}

.cost-ledger-analysis-section .lg-type-label {
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-ledger-analysis-section .lg-warning-empty {
  color: var(--muted);
  font-size: 13px;
  line-height: 20px;
}

@media (max-width: 1200px) {
  .cost-ledger-page-head,
  .cost-ledger-query-panel,
  .cost-ledger-query-keyword-row,
  .cost-ledger-query-primary {
    align-items: stretch;
    flex-direction: column;
  }

  .cost-ledger-query-actions {
    justify-content: flex-start;
  }

  .cost-ledger-keyword-search,
  .cost-ledger-query-select,
  .cost-ledger-status-select,
  .cost-ledger-query-range {
    width: 100%;
    min-width: 0;
  }

  .cost-ledger-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cost-ledger-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }

  .cost-ledger-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .cost-ledger-head-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
