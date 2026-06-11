<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
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
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getContractLedger, getContractKpi } from '@/api/modules/contract'
import type { ContractVO, ContractQueryParams, ContractKpiVO, ContractType, ContractStatus } from '@/types/contract'
import type { PageResult } from '@/types/api'

const router = useRouter()

function handleCreate() {
  router.push('/contract/create')
}

// ---- Filter state ----
const filter = reactive({
  projectId: undefined as string | undefined,
  contractType: undefined as ContractType | undefined,
  contractStatus: undefined as ContractStatus | undefined,
  partnerId: undefined as string | undefined,
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
  partnerName: true,
  contractAmount: true,
  signedDate: true,
  contractStatus: true,
  ops: true,
}
const colVisible = reactive<Record<string, boolean>>(
  JSON.parse(localStorage.getItem(COLS_KEY) || JSON.stringify(defaultCols))
)
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
    partnerId: filter.partnerId,
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
  } catch {
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
  } catch {
    kpi.value = { totalCount: 0, totalAmount: '0', paidAmount: '0', unpaidAmount: '0', overdueCount: 0 }
    message.error('加载合同指标失败，请稍后重试')
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}
function handleReset() {
  filter.projectId = undefined
  filter.contractType = undefined
  filter.contractStatus = undefined
  filter.partnerId = undefined
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

onMounted(() => {
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
  MAIN: '总包合同', SUB: '分包合同', PURCHASE: '采购合同', LEASE: '租赁合同', SERVICE: '服务合同',
}
const TYPE_COLOR: Record<ContractType, string> = {
  MAIN: 'blue', SUB: 'green', PURCHASE: 'orange', LEASE: 'purple', SERVICE: 'cyan',
}
const STATUS_LABEL: Record<ContractStatus, string> = {
  PERFORMING: '履约中', SETTLED: '已结算', TERMINATED: '已终止', DRAFT: '草稿',
}
const STATUS_COLOR: Record<ContractStatus, string> = {
  PERFORMING: 'success', SETTLED: 'default', TERMINATED: 'warning', DRAFT: 'processing',
}

// ---- ECharts donut option ----
const CHART_COLORS = ['#3b82f6', '#22c55e', '#f59e0b', '#8b5cf6', '#14b8c7']
const TYPE_NAMES = ['总包合同', '分包合同', '采购合同', '租赁合同', '服务合同']
const donutOption = computed(() => ({
  color: CHART_COLORS,
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { show: false },
  series: [{
    type: 'pie',
    radius: ['50%', '72%'],
    label: { show: false },
    data: [
      { name: '总包合同', value: 12 },
      { name: '分包合同', value: 45 },
      { name: '采购合同', value: 50 },
      { name: '租赁合同', value: 15 },
      { name: '服务合同', value: 6 },
    ],
  }],
}))

// ---- Status stats (mock) ----
const statusStats = [
  { label: '履约中', count: 98, pct: 76.56, color: '#22c55e' },
  { label: '已完成', count: 20, pct: 15.63, color: '#14b8c7' },
  { label: '已终止', count: 6,  pct: 4.69,  color: '#f59e0b' },
  { label: '草稿',   count: 4,  pct: 3.12,  color: '#8b5cf6' },
]

// ---- Overdue warnings (mock) ----
const overdueList = [
  { code: 'HT-FB-2024-005', name: '机电工程分包合同', days: 28 },
  { code: 'HT-CG-2024-012', name: '铝合金门窗采购合同', days: 15 },
  { code: 'HT-CG-2024-018', name: '防水材料采购合同', days: 7 },
]

// ---- VxeGrid columns ----
const gridColumns = computed(() => [
  { type: 'checkbox', width: 46, fixed: 'left' as const },
  ...(colVisible.contractCode ? [{ field: 'contractCode', title: '合同编号', width: 160, slots: { default: 'contractCode' } }] : []),
  ...(colVisible.contractName ? [{ field: 'contractName', title: '合同名称', minWidth: 160 }] : []),
  ...(colVisible.contractType ? [{ field: 'contractType', title: '合同类型', width: 110, slots: { default: 'contractType' } }] : []),
  ...(colVisible.partnerName ? [{ field: 'partnerName', title: '合作方', width: 160 }] : []),
  ...(colVisible.contractAmount ? [{ field: 'contractAmount', title: '合同金额(含税)', width: 140, align: 'right' as const, slots: { default: 'amount' } }] : []),
  ...(colVisible.signedDate ? [{ field: 'signedDate', title: '签订日期', width: 110 }] : []),
  ...(colVisible.contractStatus ? [{ field: 'contractStatus', title: '合同状态', width: 100, slots: { default: 'status' } }] : []),
  ...(colVisible.ops ? [{ title: '操作', width: 140, fixed: 'right' as const, slots: { default: 'ops' } }] : []),
])
</script>

<template>
  <div class="cl-page">
    <!-- Breadcrumb -->
    <a-breadcrumb class="cl-breadcrumb">
      <a-breadcrumb-item>合同管理</a-breadcrumb-item>
      <a-breadcrumb-item>合同台账</a-breadcrumb-item>
    </a-breadcrumb>

    <div class="cl-grid">
      <!-- Left column -->
      <div class="cl-left">
        <!-- Filter card -->
        <div class="cl-card cl-filter">
          <div class="cl-filter-row">
            <div class="cl-field">
              <label>项目名称：</label>
              <a-select v-model:value="filter.projectId" placeholder="请选择项目" allow-clear style="width:160px" />
            </div>
            <div class="cl-field">
              <label>合同类型：</label>
              <a-select v-model:value="filter.contractType" placeholder="全部" allow-clear style="width:140px">
                <a-select-option value="MAIN">总包</a-select-option>
                <a-select-option value="SUB">分包</a-select-option>
                <a-select-option value="PURCHASE">采购</a-select-option>
                <a-select-option value="LEASE">租赁</a-select-option>
                <a-select-option value="SERVICE">服务</a-select-option>
              </a-select>
            </div>
            <div class="cl-field">
              <label>合同状态：</label>
              <a-select v-model:value="filter.contractStatus" placeholder="全部" allow-clear style="width:140px">
                <a-select-option value="PERFORMING">履约中</a-select-option>
                <a-select-option value="SETTLED">已结算</a-select-option>
                <a-select-option value="TERMINATED">已终止</a-select-option>
                <a-select-option value="DRAFT">草稿</a-select-option>
              </a-select>
            </div>
          </div>
          <div class="cl-filter-row cl-filter-row--last">
            <div class="cl-field">
              <label>合作方：</label>
              <a-select v-model:value="filter.partnerId" placeholder="请选择合作方" allow-clear style="width:160px" />
            </div>
            <div class="cl-field">
              <label>合同编号：</label>
              <a-input v-model:value="filter.contractCode" placeholder="请输入合同编号" style="width:160px" />
            </div>
            <div class="cl-field">
              <label>签订日期：</label>
              <a-range-picker v-model:value="filter.dateRange" style="width:220px" />
            </div>
            <div class="cl-filter-actions">
              <a-button type="primary" @click="handleSearch">查询</a-button>
              <a-button @click="handleReset">重置</a-button>
              <a-button type="text">展开 ↓</a-button>
            </div>
          </div>
        </div>

        <!-- KPI cards -->
        <div class="cl-kpis">
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background:#3b82f6"><FileTextOutlined /></div>
            <div>
              <div class="cl-kpi-title">合同总数</div>
              <div class="cl-kpi-value">{{ kpi.totalCount }} <small>份</small></div>
              <div class="cl-kpi-change">较上月 <span class="up">↑ 8.5%</span></div>
            </div>
          </div>
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background:#36c267"><DollarOutlined /></div>
            <div>
              <div class="cl-kpi-title">合同总金额(含税)</div>
              <div class="cl-kpi-value">{{ fmtAmount(kpi.totalAmount) }} <small>万元</small></div>
              <div class="cl-kpi-change">较上月 <span class="up">↑ 12.3%</span></div>
            </div>
          </div>
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background:#f59e0b"><PayCircleOutlined /></div>
            <div>
              <div class="cl-kpi-title">已付款金额</div>
              <div class="cl-kpi-value">{{ fmtAmount(kpi.paidAmount) }} <small>万元</small></div>
              <div class="cl-kpi-change">较上月 <span class="up">↑ 9.7%</span></div>
            </div>
          </div>
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background:#7c3aed"><WalletOutlined /></div>
            <div>
              <div class="cl-kpi-title">未付款金额</div>
              <div class="cl-kpi-value">{{ fmtAmount(kpi.unpaidAmount) }} <small>万元</small></div>
              <div class="cl-kpi-change">较上月 <span class="up">↑ 15.2%</span></div>
            </div>
          </div>
          <div class="cl-kpi">
            <div class="cl-kpi-icon" style="background:#31c7cf"><ClockCircleOutlined /></div>
            <div>
              <div class="cl-kpi-title">逾期合同数</div>
              <div class="cl-kpi-value">{{ kpi.overdueCount }} <small>份</small></div>
              <div class="cl-kpi-change">较上月 <span class="down">↓ 25.0%</span></div>
            </div>
          </div>
        </div>

        <!-- Toolbar -->
        <div class="cl-toolbar">
          <div class="cl-toolbar-left">
            <a-button type="primary" @click="handleCreate"><template #icon><PlusOutlined /></template>新建合同</a-button>
            <a-button><template #icon><DownloadOutlined /></template>导出</a-button>
            <a-dropdown>
              <a-button><template #icon><SettingOutlined /></template>列设置</a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item v-for="(_, key) in defaultCols" :key="key" @click="toggleCol(key)">
                    <a-checkbox :checked="colVisible[key]">
                      {{ { contractCode: '合同编号', contractName: '合同名称', contractType: '合同类型', partnerName: '合作方', contractAmount: '合同金额', signedDate: '签订日期', contractStatus: '合同状态', ops: '操作' }[key] }}
                    </a-checkbox>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <a-button @click="fetchData"><template #icon><ReloadOutlined /></template></a-button>
          </div>
        </div>

        <!-- Table -->
        <div class="cl-card cl-table-wrap">
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
              <span class="cl-money">{{ parseFloat(row.contractAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
            </template>
            <template #status="{ row }">
              <a-tag :color="STATUS_COLOR[row.contractStatus as ContractStatus]">
                {{ STATUS_LABEL[row.contractStatus as ContractStatus] }}
              </a-tag>
            </template>
            <template #ops="{ row }">
              <div class="cl-ops">
                <a class="cl-link" @click="() => row">查看</a>
                <a class="cl-link" @click="() => row">编辑</a>
                <a class="cl-link cl-del" @click="() => row">删除</a>
              </div>
            </template>
          </vxe-grid>
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

      <!-- Right side panel -->
      <aside class="cl-side">
        <!-- Donut: type distribution -->
        <div class="cl-card cl-panel">
          <div class="cl-panel-title">合同类型分布</div>
          <div class="cl-chart-row">
            <VChart :option="donutOption" style="width:132px;height:132px" autoresize />
            <div class="cl-legend">
              <div v-for="(name, i) in TYPE_NAMES" :key="name" class="cl-legend-item">
                <span class="cl-legend-left">
                  <i class="cl-dot" :style="{ background: CHART_COLORS[i] }"></i>{{ name }}
                </span>
                <span>{{ [12, 45, 50, 15, 6][i] }} ({{ [9.38, 35.16, 39.06, 11.72, 4.68][i] }}%)</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Status stats -->
        <div class="cl-card cl-panel">
          <div class="cl-panel-title">合同状态统计</div>
          <div class="cl-status-list">
            <div v-for="s in statusStats" :key="s.label" class="cl-status-line">
              <span>{{ s.label }}</span>
              <b>{{ s.count }}</b>
              <div class="cl-bar">
                <span :style="{ width: s.pct + '%', background: s.color }"></span>
              </div>
              <span>{{ s.pct }}%</span>
            </div>
          </div>
        </div>

        <!-- Overdue warnings -->
        <div class="cl-card cl-panel">
          <div class="cl-warning-head">
            <div class="cl-panel-title" style="margin:0">逾期预警</div>
            <a class="cl-link" style="font-size:12px">全部预警 ›</a>
          </div>
          <div class="cl-warning-list">
            <div v-for="w in overdueList" :key="w.code" class="cl-warning-item">
              <i class="cl-red-dot"></i>
              <span>{{ w.code }}</span>
              <span>{{ w.name }}</span>
              <span class="cl-overdue">逾期 {{ w.days }} 天</span>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.cl-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.cl-breadcrumb {
  margin-bottom: 16px;
  font-size: 14px;
}
.cl-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 318px;
  gap: 14px;
  align-items: start;
}
.cl-left {
  min-width: 0;
}
.cl-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

/* Filter */
.cl-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.cl-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
  margin-bottom: 14px;
}
.cl-filter-row--last {
  margin-bottom: 0;
}
.cl-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.cl-field label {
  color: #374151;
  min-width: 56px;
}
.cl-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}

/* KPI */
.cl-kpis {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.cl-kpi {
  height: 96px;
  padding: 18px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.cl-kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}
.cl-kpi-title {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}
.cl-kpi-value {
  font-size: 21px;
  font-weight: 800;
  color: #111827;
  letter-spacing: 0.2px;
}
.cl-kpi-value small {
  font-size: 13px;
  font-weight: 500;
  margin-left: 4px;
}
.cl-kpi-change {
  font-size: 12px;
  color: #6b7280;
  margin-top: 5px;
}
.up { color: #ef4444; }
.down { color: #16a34a; }

/* Toolbar */
.cl-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
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
}
.cl-link {
  color: #1677ff;
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
  color: #ef4444;
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
  color: #4b5563;
}

/* Side panel */
.cl-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cl-panel {
  padding: 14px 16px;
}
.cl-panel-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 12px;
  color: #111827;
}
.cl-chart-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.cl-legend {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: #4b5563;
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
  gap: 12px;
  font-size: 12px;
}
.cl-status-line {
  display: grid;
  grid-template-columns: 52px 24px 1fr 48px;
  gap: 8px;
  align-items: center;
}
.cl-bar {
  height: 7px;
  border-radius: 99px;
  background: #eef2f7;
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
  gap: 12px;
  font-size: 12px;
}
.cl-warning-item {
  display: grid;
  grid-template-columns: 8px 90px 1fr 58px;
  gap: 8px;
  align-items: center;
}
.cl-red-dot {
  width: 6px;
  height: 6px;
  background: #ef4444;
  border-radius: 50%;
  display: inline-block;
}
.cl-overdue {
  color: #ef4444;
  font-weight: 600;
  text-align: right;
}

@media (max-width: 1200px) {
  .cl-grid {
    grid-template-columns: 1fr;
  }
  .cl-side {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
  }
  .cl-kpis {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
