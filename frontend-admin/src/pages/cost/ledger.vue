<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { message } from 'ant-design-vue'
import {
  DollarOutlined,
  LockOutlined,
  ToolOutlined,
  CarOutlined,
  ReloadOutlined,
  SearchOutlined,
  PieChartOutlined,
  BarChartOutlined,
  AlertOutlined,
} from '@ant-design/icons-vue'
import type { TreeSelectProps } from 'ant-design-vue'
import { getCostLedger, getCostLedgerSummary, getCostLedgerDetail } from '@/api/modules/cost'
import { getCostSubjectTree, type CostSubjectTreeNode } from '@/api/modules/costSubject'
import type {
  CostLedgerVO,
  CostLedgerQueryParams,
  CostLedgerSummaryVO,
  SourceType,
} from '@/types/cost'
import { SOURCE_TYPE_LABEL, SOURCE_TYPE_COLOR } from '@/types/cost'
import type { PageResult } from '@/types/api'
import { useReferenceStore } from '@/stores/reference'

const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() { isMobile.value = window.innerWidth < MOBILE_BP }

// ---- Reference store ----
const referenceStore = useReferenceStore()
const {
  projects: projectList,
  contracts: contractList,
  partners: partnerList,
} = storeToRefs(referenceStore)

// ---- Dropdown data ----
const subjectTree = ref<TreeSelectProps['treeData']>([])

// ---- Filter state ----
const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  costSubjectId: undefined as string | undefined,
  costType: undefined as string | undefined,
  sourceType: undefined as string | undefined,
  costStatus: undefined as string | undefined,
  dateRange: [] as string[],
  keyword: '',
})

// ---- Table state ----
const loading = ref(false)
const tableData = ref<CostLedgerVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

// ---- KPI state ----
const summary = ref<CostLedgerSummaryVO>({
  totalAmount: '0',
  totalTaxAmount: '0',
  bySourceType: {},
  byProject: {},
  byCostType: {},
})

// ---- Detail drawer ----
const detailVisible = ref(false)
const detailItem = ref<CostLedgerVO | null>(null)

// ---- Summary view mode ----
const summaryMode = ref<'sourceType' | 'project' | 'costType'>('sourceType')

async function fetchSubjectTree() {
  try {
    const data = await getCostSubjectTree()
    subjectTree.value = convertToTreeData(data)
  } catch (e: unknown) {
    console.error(e)
    subjectTree.value = []
  }
}

interface TreeNode {
  id: string
  subjectCode: string
  subjectName: string
  children?: TreeNode[]
}

function convertToTreeData(nodes: TreeNode[]): TreeSelectProps['treeData'] {
  return nodes.map((node) => ({
    value: node.id,
    title: `${node.subjectCode} ${node.subjectName}`,
    children: node.children ? convertToTreeData(node.children) : undefined,
  }))
}

function onProjectChange(val: string | undefined) {
  filter.contractId = undefined
  if (val) referenceStore.fetchContracts({ projectId: val })
}

// ---- Fetch data ----
async function fetchData() {
  loading.value = true
  const params: CostLedgerQueryParams = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    projectId: filter.projectId,
    contractId: filter.contractId,
    partnerId: filter.partnerId,
    costSubjectId: filter.costSubjectId,
    costType: filter.costType,
    sourceType: filter.sourceType,
    costStatus: filter.costStatus,
    startDate: filter.dateRange[0],
    endDate: filter.dateRange[1],
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
    message.error('加载成本台账失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchSummary() {
  try {
    summary.value = await getCostLedgerSummary({
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      costSubjectId: filter.costSubjectId,
      costType: filter.costType,
      sourceType: filter.sourceType,
      costStatus: filter.costStatus,
      startDate: filter.dateRange[0],
      endDate: filter.dateRange[1],
      keyword: filter.keyword || undefined,
    })
  } catch (e: unknown) {
    console.error(e)
    summary.value = {
      totalAmount: '0',
      totalTaxAmount: '0',
      bySourceType: {},
      byProject: {},
      byCostType: {},
    }
    message.error('加载成本汇总失败')
  }
}

function handleSearch() {
  pageNum.value = 1
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
  filter.dateRange = []
  filter.keyword = ''
  referenceStore.invalidateContracts()
  pageNum.value = 1
  handleSearch()
}

function handlePageChange(page: number) {
  pageNum.value = page
  fetchData()
}

function handleShowSizeChange(_current: number, size: number) {
  pageSize.value = size
  pageNum.value = 1
  fetchData()
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
  CT_DIRECT: '直接成本',
  CT_INDIRECT: '间接成本',
  CT_MATERIAL: '材料成本',
  CT_SUBCONTRACT: '分包成本',
  CT_LABOR: '人工成本',
  CT_OTHER: '其他成本',
}

// ---- Computed KPI ----
const lockedAmount = computed(() => {
  const bySource = summary.value.bySourceType
  return bySource['CT_CONTRACT'] ?? summary.value.totalAmount
})

const kpiStats = computed(() => {
  const total = parseFloat(summary.value.totalAmount) || 0
  const locked = parseFloat(lockedAmount.value) || 0
  return {
    total,
    locked,
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
  { field: 'id', title: '编号', width: 180, ellipsis: true },
  { field: 'costSubjectName', title: '成本科目', width: 130, ellipsis: true },
  { field: 'sourceType', title: '来源类型', width: 110, slots: { default: 'sourceType' } },
  { field: 'amount', title: '金额(万元)', width: 110, align: 'right' as const, slots: { default: 'amount' } },
  { field: 'costDate', title: '成本日期', width: 110 },
  { field: 'costStatus', title: '状态', width: 90, slots: { default: 'costStatus' } },
  { title: '操作', width: 80, slots: { default: 'ops' } },
])

// ---- Init ----
onMounted(() => {
  window.addEventListener('resize', onResize)
  referenceStore.fetchProjects()
  referenceStore.fetchPartners()
  fetchSubjectTree()
  fetchData()
  fetchSummary()
})
onUnmounted(() => window.removeEventListener('resize', onResize))
</script>

<template>
  <div class="lg-page app-page">
    <!-- Page head -->
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>成本管理</a-breadcrumb-item>
          <a-breadcrumb-item>成本台账</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索编号、科目名、类型、项目、合同…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <!-- 左列 -->
      <div class="lg-left">
        <!-- KPI strip -->
        <div v-if="!isMobile" class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">成本总额</span>
            <span class="lg-kpi-card-value">{{ fmtWan(String(kpiStats.total)) }} <small>万元</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-total)"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">锁定成本</span>
            <span class="lg-kpi-card-value">{{ fmtWan(String(kpiStats.locked)) }} <small>万元</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-amount)"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">动态成本</span>
            <span class="lg-kpi-card-value">{{ fmtWan(String(kpiStats.dynamic)) }} <small>万元</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-paid)"></span></span>
          </div>
          <div class="lg-kpi-card is-warn" v-if="kpiStats.deviation !== 0">
            <span class="lg-kpi-card-label">偏差金额</span>
            <span class="lg-kpi-card-value">{{ fmtWan(String(kpiStats.deviation)) }} <small>万元</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: Math.min(100, Math.abs(kpiStats.deviation) / Math.max(kpiStats.total, 1) * 100) + '%', background: 'var(--kpi-overdue)' }"></span></span>
          </div>
        </div>

        <!-- KPI 移动端 -->
        <div v-else class="lg-kpi-single">
          <div
            class="lg-kpi-single-row"
            v-for="item in [
              { icon: DollarOutlined, bg: 'var(--kpi-total)', label: '成本总额', value: fmtWan(String(kpiStats.total)), unit: '万元' },
              { icon: LockOutlined, bg: 'var(--kpi-amount)', label: '锁定成本', value: fmtWan(String(kpiStats.locked)), unit: '万元' },
              { icon: ToolOutlined, bg: 'var(--kpi-paid)', label: '动态成本', value: fmtWan(String(kpiStats.dynamic)), unit: '万元' },
              { icon: AlertOutlined, bg: 'var(--kpi-overdue)', label: '偏差金额', value: fmtWan(String(kpiStats.deviation)), unit: '万元' },
            ]"
            :key="item.label"
          >
            <div class="lg-kpi-single-icon" :style="{ background: item.bg }">
              <component :is="item.icon" />
            </div>
            <span class="lg-kpi-single-label">{{ item.label }}</span>
            <span class="lg-kpi-single-value">{{ item.value }} <small>{{ item.unit }}</small></span>
          </div>
        </div>

        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button @click="handleSearch">
              <template #icon><ReloadOutlined /></template>
            </a-button>
          </div>
          <div class="lg-toolbar-right">
            <a-select
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              style="width: 160px"
              size="small"
              @change="onProjectChange"
            >
              <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
          </div>
        </div>

        <!-- 表格 -->
        <div class="lg-table-wrap">
          <vxe-grid
            :data="tableData"
            :columns="gridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            stripe
            border="inner"
            size="small"
            max-height="480"
          >
            <template #sourceType="{ row }">
              <a-tag :color="SOURCE_TYPE_COLOR[row.sourceType as SourceType] || 'default'" size="small">
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
                {{ row.costStatus === 'CONFIRMED' ? '已确认' : row.costStatus === 'PENDING' ? '待确认' : row.costStatus }}
              </a-tag>
            </template>
            <template #ops="{ row }">
              <a class="lg-link" @click="showDetail(row)">详情</a>
            </template>
          </vxe-grid>
        </div>

        <!-- 分页 -->
        <div class="lg-pagination">
          <span class="lg-total">共 {{ total }} 条</span>
          <a-pagination
            v-model:current="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-size-options="['10', '20', '50', '100']"
            show-size-changer
            show-quick-jumper
            @change="handlePageChange"
            @show-size-change="handleShowSizeChange"
          />
        </div>
      </div>

      <!-- 右侧分析面板 -->
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">成本科目占比</div>
          <div class="lg-type-list">
            <div v-for="item in subjectBreakdown" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: 'var(--kpi-paid)' }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span class="lg-type-bar" :style="{ width: barPercent(item.amount), background: 'var(--kpi-paid)' }"></span>
              </span>
              <span class="lg-type-num">{{ fmtWan(item.amount) }}</span>
              <span class="lg-type-pct">{{ barPercent(item.amount) }}</span>
            </div>
          </div>
        </section>

        <section class="lg-panel">
          <div class="lg-panel-title">来源类型分布</div>
          <div class="lg-type-list">
            <div v-for="item in sourceBreakdown" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: 'var(--kpi-amount)' }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span class="lg-type-bar" :style="{ width: barPercent(item.amount), background: 'var(--kpi-amount)' }"></span>
              </span>
              <span class="lg-type-num">{{ fmtWan(item.amount) }}</span>
              <span class="lg-type-pct">{{ barPercent(item.amount) }}</span>
            </div>
          </div>
        </section>

        <section class="lg-panel">
          <div class="lg-warning-head">
            <div class="lg-panel-title" style="margin-bottom:0">超预算预警</div>
          </div>
          <div class="lg-warning-list">
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
          </div>
        </section>
      </aside>
    </div>

    <!-- Detail drawer -->
    <a-drawer
      :open="detailVisible"
      title="成本明细"
      placement="right"
      :width="520"
      @close="detailVisible = false"
    >
      <template v-if="detailItem">
        <a-descriptions :column="2" size="small" bordered>
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
.cl-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
</style>
