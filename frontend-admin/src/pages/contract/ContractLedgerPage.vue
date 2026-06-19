<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import {
  FileTextOutlined,
  DollarOutlined,
  PayCircleOutlined,
  WalletOutlined,
  ClockCircleOutlined,
  PlusOutlined,
  DownloadOutlined,
  SettingOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { useReferenceStore } from '@/stores/reference'
import { getContractLedger, getContractKpi, deleteContract } from '@/api/modules/contract'
import ContractFormPage from './ContractFormPage.vue'
import ContractStatusTag from '@/components/ContractStatusTag.vue'
import type {
  ContractVO,
  ContractQueryParams,
  ContractKpiVO,
  ContractType,
  ContractStatus,
} from '@/types/contract'
import type { PageResult } from '@/types/api'

const router = useRouter()
const referenceStore = useReferenceStore()
const projects = computed(() => referenceStore.projects ?? [])

const contractModalVisible = ref(false)
const contractModalMode = ref<'create' | 'edit'>('create')
const contractModalId = ref('')

function handleCreate() {
  contractModalMode.value = 'create'
  contractModalId.value = ''
  contractModalVisible.value = true
}

// ---- Row action handlers ----
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
function handleExport() {
  message.info('导出功能即将上线')
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
const filterExpanded = ref(false)
function toggleFilterExpand() {
  filterExpanded.value = !filterExpanded.value
}
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
async function fetchData() {
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
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载合同台账失败，请稍后重试')
  } finally {
    loading.value = false
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
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const TYPE_LABEL: Record<ContractType, string> = {
  MAIN: '总包合同',
  SUB: '分包合同',
  PURCHASE: '采购合同',
  LEASE: '租赁合同',
  SERVICE: '服务合同',
}
const TYPE_COLOR: Record<ContractType, string> = {
  MAIN: 'blue',
  SUB: 'green',
  PURCHASE: 'orange',
  LEASE: 'purple',
  SERVICE: 'cyan',
}

const CHART_COLORS = ['#2f7df6', '#31c48d', '#f59e0b', '#8b5cf6', '#22c7d7']
const STATUS_LABEL: Record<ContractStatus, string> = {
  DRAFT: '草稿',
  PERFORMING: '履约中',
  SETTLED: '已完成',
  TERMINATED: '已终止',
}
const STATUS_COLOR: Record<ContractStatus, string> = {
  DRAFT: '#94a3b8',
  PERFORMING: '#2f7df6',
  SETTLED: '#31c48d',
  TERMINATED: '#ef4444',
}

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
    .map((key, index) => ({
      key,
      label: TYPE_LABEL[key],
      value: counts[key],
      color: CHART_COLORS[index],
    }))
    .filter((item) => item.value > 0)
})

const statusDistribution = computed(() => {
  if (!tableData.value.length) return []
  const counts = tableData.value.reduce<Record<ContractStatus, number>>(
    (acc, item) => {
      acc[item.contractStatus] += 1
      return acc
    },
    { DRAFT: 0, PERFORMING: 0, SETTLED: 0, TERMINATED: 0 },
  )
  return (Object.keys(STATUS_LABEL) as ContractStatus[])
    .map((key) => ({
      key,
      label: STATUS_LABEL[key],
      value: counts[key],
      color: STATUS_COLOR[key],
    }))
    .filter((item) => item.value > 0)
})

const statusTotal = computed(
  () => statusDistribution.value.reduce((sum, item) => sum + item.value, 0) || 1,
)

const statusBars = computed(() =>
  statusDistribution.value.map((item) => ({
    ...item,
    percent: Math.round((item.value / statusTotal.value) * 100),
  })),
)

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

// ---- Mobile detection ----
const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

const donutOption = computed(() => ({
  color: typeDistribution.value.map((item) => item.color),
  tooltip: { trigger: 'item' },
  legend: { show: false },
  series: [
    {
      type: 'pie',
      radius: ['54%', '78%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      label: { show: false },
      data: typeDistribution.value.map((item) => ({ name: item.label, value: item.value })),
    },
  ],
}))

const statusDonutOption = computed(() => ({
  color: statusDistribution.value.map((item) => item.color),
  tooltip: { trigger: 'item' },
  legend: { show: false },
  series: [
    {
      type: 'pie',
      radius: ['54%', '78%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      label: { show: false },
      data: statusDistribution.value.map((item) => ({ name: item.label, value: item.value })),
    },
  ],
}))

// ---- VxeGrid columns ----
const gridColumns = computed(() => [
  { type: 'checkbox', width: 42 },
  ...(colVisible.contractCode
    ? [
        {
          field: 'contractCode',
          title: '合同编号',
          width: 140,
          ellipsis: true,
          slots: { default: 'contractCode' },
        },
      ]
    : []),
  ...(colVisible.contractName
    ? [{ field: 'contractName', title: '合同名称', minWidth: 140, ellipsis: true }]
    : []),
  ...(colVisible.contractType
    ? [{ field: 'contractType', title: '合同类型', width: 90, slots: { default: 'contractType' } }]
    : []),
  ...(colVisible.partyAName
    ? [{ field: 'partyAName', title: '甲方', width: 120, ellipsis: true }]
    : []),
  ...(colVisible.partyBName
    ? [{ field: 'partyBName', title: '乙方', width: 120, ellipsis: true }]
    : []),
  ...(colVisible.contractAmount
    ? [
        {
          field: 'contractAmount',
          title: '合同金额(含税)',
          width: 130,
          align: 'right' as const,
          slots: { default: 'amount' },
        },
      ]
    : []),
  ...(colVisible.signedDate ? [{ field: 'signedDate', title: '签订日期', width: 100 }] : []),
  ...(colVisible.contractStatus
    ? [{ field: 'contractStatus', title: '合同状态', width: 90, slots: { default: 'status' } }]
    : []),
  ...(colVisible.ops ? [{ title: '操作', width: 120, slots: { default: 'ops' } }] : []),
])
</script>

<template>
  <div class="cl-page app-page">
    <div class="cl-page-head">
      <div>
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>合同管理</a-breadcrumb-item>
          <a-breadcrumb-item>合同台账</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="cl-grid">
      <!-- Left column -->
      <div class="cl-left">
        <!-- Filter card -->
        <div class="cl-card cl-filter">
          <!-- 全局搜索卡片 -->
          <div class="pj-search-card">
            <div class="pj-search-label">输入查询信息</div>
            <div class="pj-search-row">
              <a-input
                v-model:value="filter.keyword"
                placeholder="输入合同编号、合同名称、合同类型、甲方名称、乙方名称等任意关键词"
                allow-clear
                size="large"
                @press-enter="handleSearch"
              >
                <template #prefix><SearchOutlined style="color: #9ca3af" /></template>
              </a-input>
              <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
              <a-button size="large" @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
            </div>
          </div>
        </div>

        <!-- KPI cards: desktop / tablet -->
        <div v-if="!isMobile" class="cl-kpis">
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background: #3b82f6"><FileTextOutlined /></div>
            <div>
              <div class="cl-kpi-title">合同总数</div>
              <div class="cl-kpi-value">{{ kpi.totalCount }} <small>份</small></div>
            </div>
          </div>
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background: #36c267"><DollarOutlined /></div>
            <div>
              <div class="cl-kpi-title">合同总金额(含税)</div>
              <div class="cl-kpi-value">{{ fmtAmount(kpi.totalAmount) }} <small>万元</small></div>
            </div>
          </div>
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background: #f59e0b"><PayCircleOutlined /></div>
            <div>
              <div class="cl-kpi-title">已付款金额</div>
              <div class="cl-kpi-value">{{ fmtAmount(kpi.paidAmount) }} <small>万元</small></div>
            </div>
          </div>
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background: #7c3aed"><WalletOutlined /></div>
            <div>
              <div class="cl-kpi-title">未付款金额</div>
              <div class="cl-kpi-value">{{ fmtAmount(kpi.unpaidAmount) }} <small>万元</small></div>
            </div>
          </div>
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background: #31c7cf"><ClockCircleOutlined /></div>
            <div>
              <div class="cl-kpi-title">逾期合同数</div>
              <div class="cl-kpi-value">{{ kpi.overdueCount }} <small>份</small></div>
            </div>
          </div>
        </div>

        <!-- KPI card: mobile (single card) -->
        <div v-else class="cl-kpi-single">
          <div
            class="cl-kpi-single-row"
            v-for="item in [
              {
                icon: FileTextOutlined,
                bg: '#3b82f6',
                label: '合同总数',
                value: kpi.totalCount,
                unit: '份',
              },
              {
                icon: DollarOutlined,
                bg: '#36c267',
                label: '合同总金额(含税)',
                value: fmtAmount(kpi.totalAmount),
                unit: '万元',
              },
              {
                icon: PayCircleOutlined,
                bg: '#f59e0b',
                label: '已付款金额',
                value: fmtAmount(kpi.paidAmount),
                unit: '万元',
              },
              {
                icon: WalletOutlined,
                bg: '#7c3aed',
                label: '未付款金额',
                value: fmtAmount(kpi.unpaidAmount),
                unit: '万元',
              },
              {
                icon: ClockCircleOutlined,
                bg: '#31c7cf',
                label: '逾期合同数',
                value: kpi.overdueCount,
                unit: '份',
              },
            ]"
            :key="item.label"
          >
            <div class="cl-kpi-single-icon" :style="{ background: item.bg }">
              <component :is="item.icon" />
            </div>
            <span class="cl-kpi-single-label">{{ item.label }}</span>
            <span class="cl-kpi-single-value"
              >{{ item.value }} <small>{{ item.unit }}</small></span
            >
          </div>
        </div>

        <!-- Toolbar -->
        <div class="cl-toolbar">
          <div class="cl-toolbar-left">
            <a-button type="primary" @click="handleCreate"
              ><template #icon><PlusOutlined /></template>新建合同</a-button
            >
            <a-button :disabled="true" title="即将上线" @click="handleExport"
              ><template #icon><DownloadOutlined /></template>导出</a-button
            >
            <a-dropdown v-if="!isMobile">
              <a-button
                ><template #icon><SettingOutlined /></template>列设置</a-button
              >
              <template #overlay>
                <a-menu>
                  <a-menu-item v-for="(_, key) in defaultCols" :key="key" @click="toggleCol(key)">
                    <a-checkbox :checked="colVisible[key]">
                      {{
                        {
                          contractCode: '合同编号',
                          contractName: '合同名称',
                          contractType: '合同类型',
                          partyAName: '甲方',
                          partyBName: '乙方',
                          contractAmount: '合同金额',
                          signedDate: '签订日期',
                          contractStatus: '合同状态',
                          ops: '操作',
                        }[key]
                      }}
                    </a-checkbox>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <a-button @click="fetchData"
              ><template #icon><ReloadOutlined /></template
            ></a-button>
          </div>
        </div>

        <!-- Table: desktop / tablet -->
        <div v-if="!isMobile" class="cl-card cl-table-wrap">
          <vxe-grid
            :data="tableData"
            :columns="gridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            :checkbox-config="{ highlight: true }"
            stripe
            border="inner"
            size="small"
            max-height="480"
          >
            <template #contractCode="{ row }">
              <a class="cl-link">{{ row.contractCode }}</a>
            </template>
            <template #contractType="{ row }">
              <a-tag :color="TYPE_COLOR[row.contractType as ContractType]">
                {{ TYPE_LABEL[row.contractType as ContractType] }}
              </a-tag>
            </template>
            <template #amount="{ row }">
              <span class="cl-money">{{
                parseFloat(row.contractAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
            <template #status="{ row }">
              <ContractStatusTag :status="row.contractStatus as ContractStatus" />
            </template>
            <template #ops="{ row }">
              <div class="cl-ops">
                <a class="cl-link" @click="handleView(row)">查看</a>
                <a class="cl-link" @click="handleEdit(row)">编辑</a>
                <a class="cl-link cl-del" @click="handleDelete(row)">删除</a>
              </div>
            </template>
          </vxe-grid>
        </div>

        <!-- Table: mobile cards -->
        <div v-else class="cl-card-list">
          <div v-if="loading" class="cl-card-list-loading">
            <a-spin size="large" />
          </div>
          <div v-else-if="!tableData.length" class="cl-card-list-empty">
            <a-empty />
          </div>
          <div v-for="row in tableData" :key="row.id" class="cl-card-item">
            <div class="cl-card-item-head">
              <span class="cl-card-code">
                <a class="cl-link" @click="handleView(row)">{{ row.contractCode }}</a>
              </span>
              <span class="cl-card-head-right">
                <a-tag
                  v-if="colVisible.contractType"
                  :color="TYPE_COLOR[row.contractType as ContractType]"
                >
                  {{ TYPE_LABEL[row.contractType as ContractType] }}
                </a-tag>
                <ContractStatusTag
                  v-if="colVisible.contractStatus"
                  :status="row.contractStatus as ContractStatus"
                />
              </span>
            </div>
            <div class="cl-card-item-body">
              <div v-if="colVisible.contractName" class="cl-card-field">
                <span class="cl-card-label">合同名称</span>
                <span class="cl-card-value">{{ row.contractName }}</span>
              </div>
              <div v-if="colVisible.partyAName || colVisible.partyBName" class="cl-card-field">
                <span class="cl-card-label">签约双方</span>
                <span class="cl-card-value">
                  <template v-if="colVisible.partyAName">{{ row.partyAName }}</template>
                  <template v-if="colVisible.partyAName && colVisible.partyBName"> · </template>
                  <template v-if="colVisible.partyBName">{{ row.partyBName }}</template>
                </span>
              </div>
              <div class="cl-card-field-row">
                <div v-if="colVisible.contractAmount" class="cl-card-field">
                  <span class="cl-card-label">合同金额(含税)</span>
                  <span class="cl-card-value cl-card-money">{{
                    parseFloat(row.contractAmount).toLocaleString('zh-CN', {
                      minimumFractionDigits: 2,
                    })
                  }}</span>
                </div>
                <div v-if="colVisible.signedDate" class="cl-card-field">
                  <span class="cl-card-label">签订日期</span>
                  <span class="cl-card-value">{{ row.signedDate }}</span>
                </div>
              </div>
            </div>
            <div class="cl-card-item-foot">
              <a-space :size="4">
                <a-button size="small" type="link" @click="handleView(row)">查看</a-button>
                <a-button size="small" type="link" @click="handleEdit(row)">编辑</a-button>
                <a-button size="small" type="link" danger @click="handleDelete(row)">删除</a-button>
              </a-space>
            </div>
          </div>
        </div>

        <!-- Pagination -->
        <div class="cl-pagination">
          <span class="cl-total">共 {{ total }} 条</span>
          <a-pagination
            v-model:current="pageNo"
            v-model:page-size="pageSize"
            :total="total"
            :page-size-options="['10', '20', '50', '100']"
            show-size-changer
            show-quick-jumper
            @change="handlePageChange"
            @show-size-change="handlePageSizeChange"
          />
        </div>
      </div>

      <!-- Right analysis rail -->
      <aside class="cl-analysis-rail">
        <section class="cl-panel">
          <div class="cl-panel-title">合同类型分布</div>
          <div class="cl-chart-row">
            <VChart :option="donutOption" autoresize class="cl-donut" />
            <div class="cl-legend">
              <div v-for="item in typeDistribution" :key="item.key" class="cl-legend-item">
                <span class="cl-legend-left">
                  <i class="cl-dot" :style="{ background: item.color }"></i>
                  {{ item.label }}
                </span>
                <b>{{ item.value }}</b>
              </div>
            </div>
          </div>
        </section>

        <section class="cl-panel">
          <div class="cl-panel-title">合同状态统计</div>
          <div class="cl-status-chart-row">
            <VChart :option="statusDonutOption" autoresize class="cl-donut" />
            <div class="cl-status-list">
              <div v-for="item in statusBars" :key="item.key" class="cl-status-line">
                <span>{{ item.label }}</span>
                <b>{{ item.value }}</b>
                <span class="cl-bar"
                  ><span :style="{ width: `${item.percent}%`, background: item.color }"></span
                ></span>
                <em>{{ item.percent }}%</em>
              </div>
            </div>
          </div>
        </section>

        <section class="cl-panel">
          <div class="cl-warning-head">
            <div class="cl-panel-title">逾期预警</div>
            <a-button type="link" size="small" @click="handleAllAlerts">查看全部</a-button>
          </div>
          <div class="cl-warning-list">
            <div
              v-for="row in warningRows"
              :key="`${row.project}-${row.title}`"
              class="cl-warning-item"
            >
              <i class="cl-red-dot"></i>
              <span>{{ row.project }}</span>
              <span>{{ row.title }}</span>
              <b class="cl-overdue">{{ row.days }}天</b>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>

  <a-modal
    v-model:open="contractModalVisible"
    :title="contractModalMode === 'edit' ? '编辑合同' : '新建合同'"
    :width="1180"
    :destroy-on-close="true"
    :footer="null"
    :mask-closable="false"
    centered
    class="cl-contract-modal"
    @cancel="handleContractClose"
  >
    <ContractFormPage
      :embedded="true"
      :mode="contractModalMode"
      :contract-id="contractModalId"
      @saved="handleContractSaved"
      @close="handleContractClose"
    />
  </a-modal>
</template>

<style scoped>
.cl-page {
  background: var(--bg);
  min-height: 100%;
  padding: 2px 0;
}
.cl-contract-modal :deep(.ant-modal-body) {
  max-height: 82vh;
  overflow: auto;
}
.cl-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 12px;
}
.cl-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
.cl-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 336px;
  gap: 16px;
  align-items: start;
}
.cl-left {
  min-width: 0;
}
.cl-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

/* Filter */
.cl-filter {
  padding: 12px 14px;
  margin-bottom: 10px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}
.cl-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
  margin-bottom: 10px;
}
.cl-filter-row--last {
  margin-bottom: 0;
}
.cl-field {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  white-space: nowrap;
}
.cl-field label {
  color: var(--text-secondary);
  min-width: 56px;
}
.cl-filter :deep(.ant-select-selector),
.cl-filter :deep(.ant-picker),
.cl-filter :deep(.ant-input),
.cl-filter :deep(.ant-btn) {
  font-size: 13px;
}
.cl-filter-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
  align-items: center;
}

/* KPI */
.cl-kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(132px, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}
.cl-kpi {
  min-height: 78px;
  padding: 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  display: flex;
  gap: 12px;
  align-items: flex-start;
  box-shadow: var(--shadow-soft);
}
.cl-kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}
.cl-kpi-title {
  font-size: 13px;
  color: var(--muted);
  margin-bottom: 4px;
}
.cl-kpi-value {
  font-size: 19px;
  font-weight: 800;
  color: var(--text);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}
.cl-kpi-value small {
  font-size: 13px;
  font-weight: 500;
  margin-left: 4px;
}
.cl-kpi-change {
  font-size: 12px;
  color: var(--muted);
  margin-top: 3px;
}
.up {
  color: #ef4444;
}
.down {
  color: #16a34a;
}

/* KPI single card (mobile) */
.cl-kpi-single {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
  padding: 8px 0;
  margin-bottom: 10px;
}
.cl-kpi-single-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  font-size: 13px;
}
.cl-kpi-single-row + .cl-kpi-single-row {
  border-top: 1px solid var(--border-subtle);
}
.cl-kpi-single-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 13px;
  flex-shrink: 0;
}
.cl-kpi-single-label {
  flex: 1;
  color: var(--text-secondary);
}
.cl-kpi-single-value {
  font-weight: 700;
  color: var(--text);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.cl-kpi-single-value small {
  font-size: 12px;
  font-weight: 500;
  margin-left: 2px;
}

/* Toolbar */
.cl-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 10px 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}
.cl-toolbar-left {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* Table */
.cl-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}
.cl-link {
  color: var(--primary);
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
}
.cl-money {
  font-variant-numeric: tabular-nums;
}
.cl-ops {
  display: flex;
  gap: 10px;
  justify-content: center;
}
.cl-del {
  color: var(--error);
}
.cl-table-wrap :deep(.vxe-table--header-wrapper) {
  background: var(--surface-subtle);
}
.cl-table-wrap :deep(.vxe-header--column) {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}
.cl-table-wrap :deep(.vxe-body--column) {
  color: var(--text);
  font-size: 13px;
}
.cl-table-wrap :deep(.vxe-body--row:hover) {
  background: #f8fbff;
}

/* Card list (mobile) */
.cl-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cl-card-list-loading,
.cl-card-list-empty {
  display: flex;
  justify-content: center;
  padding: 48px 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}
.cl-card-item {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 13px 14px;
  box-shadow: var(--shadow-soft);
}
.cl-card-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-subtle);
}
.cl-card-code {
  font-size: 14px;
  font-weight: 700;
}
.cl-card-head-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.cl-card-item-body {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.cl-card-field {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
}
.cl-card-label {
  color: var(--text-secondary);
  white-space: nowrap;
  min-width: 88px;
  flex-shrink: 0;
}
.cl-card-value {
  color: var(--text);
  word-break: break-all;
}
.cl-card-money {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.cl-card-field-row {
  display: flex;
  gap: 16px;
}
.cl-card-field-row .cl-card-field {
  flex: 1;
  min-width: 0;
}
.cl-card-field-row .cl-card-label {
  min-width: 0;
}
.cl-card-item-foot {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border-subtle);
  display: flex;
  justify-content: flex-end;
}

/* Pagination */
.cl-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.cl-total {
  font-size: 13px;
  color: var(--text-secondary);
}

/* Right analysis rail */
.cl-analysis-rail {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cl-panel {
  padding: 13px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}
.cl-panel-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 12px;
  color: var(--text);
}
.cl-chart-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cl-donut {
  width: 118px;
  height: 118px;
  flex-shrink: 0;
}
.cl-legend {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
.cl-legend-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.cl-legend-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.cl-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

/* Status bars */
.cl-status-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  font-size: 12px;
  min-width: 0;
  flex: 1;
}
.cl-status-chart-row {
  display: flex;
  gap: 10px;
  align-items: center;
}
.cl-status-line {
  display: grid;
  grid-template-columns: 46px 22px 1fr 42px;
  gap: 7px;
  align-items: center;
}
.cl-bar {
  height: 7px;
  border-radius: 99px;
  background: var(--border-subtle);
  overflow: hidden;
}
.cl-bar span {
  height: 100%;
  display: block;
  border-radius: 99px;
}

/* Overdue */
.cl-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.cl-warning-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 12px;
}
.cl-warning-item {
  display: grid;
  grid-template-columns: 8px 92px minmax(0, 1fr) 58px;
  gap: 7px;
  align-items: center;
}
.cl-warning-item span:nth-child(3) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cl-red-dot {
  width: 6px;
  height: 6px;
  background: var(--error);
  border-radius: 50%;
  display: inline-block;
}
.cl-overdue {
  color: var(--error);
  font-weight: 600;
  text-align: right;
}

/* 全局搜索卡片（与项目管理一致） */
.pj-search-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  margin-bottom: 10px;
}
.pj-search-label {
  font-size: 13px;
  color: var(--muted);
  margin-bottom: 8px;
}
.pj-search-row {
  display: flex;
  gap: 10px;
  align-items: center;
}
.pj-search-row .ant-input-affix-wrapper {
  flex: 1;
}

@media (max-width: 1280px) {
  .cl-grid {
    grid-template-columns: 1fr;
  }
  .cl-analysis-rail {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .cl-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .cl-analysis-rail {
    grid-template-columns: 1fr;
  }
  .cl-filter-actions {
    margin-left: 0;
  }
}

@media (max-width: 520px) {
  .cl-kpis {
    grid-template-columns: 1fr;
  }
  .cl-page-head {
    display: block;
  }
  .cl-filter {
    padding: 12px;
  }
  .cl-field,
  .cl-filter-actions,
  .cl-toolbar-left,
  .cl-pagination {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
