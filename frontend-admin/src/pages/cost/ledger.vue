<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
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
    total.value = res.total
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

// ---- Column defs ----
const columns = [
  { title: '编号', dataIndex: 'id', width: 100 },
  { title: '成本科目', dataIndex: 'costSubjectName', width: 130, ellipsis: true },
  { title: '来源类型', dataIndex: 'sourceType', width: 110 },
  { title: '金额(万元)', dataIndex: 'amount', width: 110, align: 'right' as const },
  { title: '成本日期', dataIndex: 'costDate', width: 110 },
  { title: '状态', dataIndex: 'costStatus', width: 80 },
  { title: '操作', dataIndex: 'ops', width: 70 },
]

// ---- Init ----
onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchPartners()
  fetchSubjectTree()
  fetchData()
  fetchSummary()
})
</script>

<template>
  <div class="project-target-redesign app-page">
    <!-- Page head -->
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb">
        <a-breadcrumb-item>成本管理</a-breadcrumb-item>
        <a-breadcrumb-item>成本台账</a-breadcrumb-item>
      </a-breadcrumb>
      <div class="pt-head-actions">
        <a-button type="primary" @click="handleSearch"><SearchOutlined />查询</a-button>
        <a-button @click="handleReset"><ReloadOutlined />重置</a-button>
      </div>
    </div>

    <!-- KPI strip -->
    <div class="pt-kpi-strip">
      <div class="pt-kpi">
        <div class="pt-kpi-label">成本总额</div>
        <div class="pt-kpi-value">{{ fmtWan(String(kpiStats.total)) }} <small>万元</small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">锁定成本</div>
        <div class="pt-kpi-value">{{ fmtWan(String(kpiStats.locked)) }} <small>万元</small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">动态成本</div>
        <div class="pt-kpi-value">{{ fmtWan(String(kpiStats.dynamic)) }} <small>万元</small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">偏差金额</div>
        <div class="pt-kpi-value">{{ fmtWan(String(kpiStats.deviation)) }} <small>万元</small></div>
      </div>
    </div>

    <!-- Filter surface -->
    <div class="pt-panel pt-filter-surface">
      <div class="pt-filter-row">
        <div class="pt-field">
          <label>项目：</label>
          <a-select
            v-model:value="filter.projectId"
            placeholder="全部项目"
            allow-clear
            style="width: 180px"
            @change="onProjectChange"
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pt-field">
          <label>合同：</label>
          <a-select
            v-model:value="filter.contractId"
            placeholder="全部合同"
            allow-clear
            style="width: 180px"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pt-field">
          <label>供应商：</label>
          <a-select
            v-model:value="filter.partnerId"
            placeholder="全部供应商"
            allow-clear
            style="width: 160px"
          >
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
              {{ p.partnerName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pt-field">
          <label>来源类型：</label>
          <a-select
            v-model:value="filter.sourceType"
            placeholder="全部来源"
            allow-clear
            style="width: 150px"
          >
            <a-select-option v-for="(label, key) in SOURCE_TYPE_LABEL" :key="key" :value="key">
              {{ label }}
            </a-select-option>
          </a-select>
        </div>
      </div>
      <div class="pt-filter-row">
        <div class="pt-field">
          <label>成本科目：</label>
          <a-tree-select
            v-model:value="filter.costSubjectId"
            :tree-data="subjectTree"
            placeholder="全部科目"
            allow-clear
            style="width: 200px"
            tree-default-expand-all
          />
        </div>
        <div class="pt-field">
          <label>日期范围：</label>
          <a-range-picker v-model:value="filter.dateRange" style="width: 240px" />
        </div>
        <div class="pt-field">
          <label>关键词：</label>
          <a-input
            v-model:value="filter.keyword"
            placeholder="编号/科目名"
            allow-clear
            style="width: 180px"
            @press-enter="handleSearch"
          />
        </div>
        <div class="pt-filter-actions">
          <a-button @click="handleSearch" type="primary" size="small">
            <SearchOutlined />
          </a-button>
          <a-button @click="handleReset" size="small">
            <ReloadOutlined />
          </a-button>
        </div>
      </div>
    </div>

    <!-- Ledger layout -->
    <div class="pt-ledger-layout">
      <main class="pt-panel pt-table-panel">
        <div class="pt-panel-header">成本清单</div>
        <a-table
          :columns="columns"
          :data-source="tableData"
          :loading="loading"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'sourceType'">
              <a-tag
                :color="SOURCE_TYPE_COLOR[record.sourceType as SourceType] || 'default'"
                size="small"
              >
                {{ SOURCE_TYPE_LABEL[record.sourceType as SourceType] || record.sourceType }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'amount'">
              <span class="cl-money">{{ fmtWan(record.amount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'costStatus'">
              <a-tag
                :color="
                  record.costStatus === '已确认'
                    ? 'success'
                    : record.costStatus === '待确认'
                      ? 'processing'
                      : 'default'
                "
                size="small"
              >
                {{ record.costStatus || '-' }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'ops'">
              <a class="pt-link" @click="showDetail(record)">详情</a>
            </template>
          </template>
        </a-table>
        <a-empty
          v-if="!loading && tableData.length === 0"
          description="暂无成本台账记录"
          style="padding: 48px 0"
        />
        <div class="pt-pagination">
          <span class="pt-total">共 {{ total }} 条</span>
          <a-pagination
            :current="pageNum"
            :total="total"
            :page-size="pageSize"
            :show-size-changer="true"
            :page-size-options="['10', '20', '50']"
            show-quick-jumper
            size="small"
            @change="handlePageChange"
            @showSizeChange="handleShowSizeChange"
          />
        </div>
      </main>

      <!-- Analysis rail -->
      <aside class="pt-analysis-rail">
        <section class="pt-panel">
          <div class="pt-panel-header">成本科目占比</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li v-for="item in subjectBreakdown" :key="item.label" class="pt-compact-row">
                <span>{{ item.label }}</span>
                <b>{{ fmtWan(item.amount) }} 万</b>
              </li>
            </ul>
          </div>
        </section>

        <section class="pt-panel">
          <div class="pt-panel-header">来源类型分布</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li v-for="item in sourceBreakdown" :key="item.label" class="pt-compact-row">
                <span>{{ item.label }}</span>
                <b>{{ fmtWan(item.amount) }} 万</b>
              </li>
            </ul>
          </div>
        </section>

        <section class="pt-panel">
          <div class="pt-panel-header">超预算预警</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li
                v-for="item in sourceBreakdown.filter((i) => parseFloat(i.amount) > 0).slice(0, 5)"
                :key="'warn-' + item.label"
                class="pt-compact-row"
              >
                <span>{{ item.label }}</span>
                <b style="color: #ef4444">{{ fmtWan(item.amount) }} 万</b>
              </li>
              <li
                v-if="sourceBreakdown.every((i) => parseFloat(i.amount) === 0)"
                class="pt-compact-row"
              >
                <span>暂无超预算项</span>
              </li>
            </ul>
          </div>
        </section>
      </aside>
    </div>

    <!-- Detail drawer (unchanged) -->
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
.cl-money {
  font-variant-numeric: tabular-nums;
}
</style>
