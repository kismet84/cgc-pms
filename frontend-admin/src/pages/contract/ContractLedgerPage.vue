<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  FileTextOutlined,
  DollarOutlined,
  PayCircleOutlined,
  WalletOutlined,
  ClockCircleOutlined,
  PlusOutlined,
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
    total.value = Number(res.total) || 0
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

const TYPE_COLOR_HEX: Record<ContractType, string> = {
  MAIN: '#2f7df6',
  SUB: '#31c48d',
  PURCHASE: '#f59e0b',
  LEASE: '#8b5cf6',
  SERVICE: '#22c7d7',
}

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

// ---- KPI 最大值归一化 ----
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

// ---- Mobile detection ----
const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

// ---- VxeGrid columns ----
const gridColumns = computed(() => [
  { type: 'checkbox', width: 42 },
  ...(colVisible.contractCode
    ? [
        {
          field: 'contractCode',
          title: '合同编号',
          width: 150,
        },
      ]
    : []),
  ...(colVisible.contractName
    ? [{ field: 'contractName', title: '合同名称', minWidth: 120 }]
    : []),
  ...(colVisible.contractType
    ? [{ field: 'contractType', title: '合同类型', width: 100, slots: { default: 'contractType' } }]
    : []),
  ...(colVisible.partyAName
    ? [{ field: 'partyAName', title: '甲方', minWidth: 100, ellipsis: true }]
    : []),
  ...(colVisible.partyBName
    ? [{ field: 'partyBName', title: '乙方', minWidth: 100, ellipsis: true }]
    : []),
  ...(colVisible.contractAmount
    ? [
        {
          field: 'contractAmount',
          title: '合同金额(含税)',
          width: 140,
          align: 'right' as const,
          slots: { default: 'amount' },
        },
      ]
    : []),
  ...(colVisible.signedDate ? [{ field: 'signedDate', title: '签订日期', width: 110 }] : []),
  ...(colVisible.contractStatus
    ? [{ field: 'contractStatus', title: '合同状态', width: 100, slots: { default: 'status' } }]
    : []),
  ...(colVisible.ops ? [{ title: '操作', width: 130, slots: { default: 'ops' } }] : []),
])
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>合同管理</a-breadcrumb-item>
          <a-breadcrumb-item>合同台账</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索合同编号、名称、甲方、乙方…"
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
        <!-- KPI 横条：桌面/平板 -->
        <div v-if="!isMobile" class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">合同总数</span>
            <span class="lg-kpi-card-value">{{ kpi.totalCount }} <small>份</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-total)"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">合同总金额(含税)</span>
            <span class="lg-kpi-card-value">{{ fmtAmount(kpi.totalAmount) }} <small>万元</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-amount)"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">已付款</span>
            <span class="lg-kpi-card-value">{{ fmtAmount(kpi.paidAmount) }} <small>万元</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: kpiPct(parseFloat(kpi.paidAmount), kpiMax.totalAmount) + '%', background: 'var(--kpi-paid)' }"></span></span>
            <span class="lg-kpi-card-hint">{{ kpiPct(parseFloat(kpi.paidAmount), kpiMax.totalAmount) }}%</span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">未付款</span>
            <span class="lg-kpi-card-value">{{ fmtAmount(kpi.unpaidAmount) }} <small>万元</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: kpiPct(parseFloat(kpi.unpaidAmount), kpiMax.totalAmount) + '%', background: 'var(--kpi-unpaid)' }"></span></span>
            <span class="lg-kpi-card-hint">{{ kpiPct(parseFloat(kpi.unpaidAmount), kpiMax.totalAmount) }}%</span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">逾期合同</span>
            <span class="lg-kpi-card-value">{{ kpi.overdueCount }} <small>份</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: kpiPct(kpi.overdueCount, kpiMax.overdueCount) + '%', background: 'var(--kpi-overdue)' }"></span></span>
            <span class="lg-kpi-card-hint" v-if="kpi.overdueCount">占 {{ kpiPct(kpi.overdueCount, kpiMax.totalCount) }}%</span>
          </div>
        </div>

        <!-- KPI 移动端：单条卡片 -->
        <div v-else class="lg-kpi-single">
          <div
            class="lg-kpi-single-row"
            v-for="item in [
              {
                icon: FileTextOutlined,
                bg: 'var(--kpi-total)',
                label: '合同总数',
                value: kpi.totalCount,
                unit: '份',
              },
              {
                icon: DollarOutlined,
                bg: 'var(--kpi-amount)',
                label: '合同总金额(含税)',
                value: fmtAmount(kpi.totalAmount),
                unit: '万元',
              },
              {
                icon: PayCircleOutlined,
                bg: 'var(--kpi-paid)',
                label: '已付款金额',
                value: fmtAmount(kpi.paidAmount),
                unit: '万元',
              },
              {
                icon: WalletOutlined,
                bg: 'var(--kpi-unpaid)',
                label: '未付款金额',
                value: fmtAmount(kpi.unpaidAmount),
                unit: '万元',
              },
              {
                icon: ClockCircleOutlined,
                bg: 'var(--kpi-overdue)',
                label: '逾期合同数',
                value: kpi.overdueCount,
                unit: '份',
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

        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button type="primary" @click="handleCreate">
              <template #icon><PlusOutlined /></template>
              新建合同
            </a-button>
            <a-button @click="fetchData">
              <template #icon><ReloadOutlined /></template>
            </a-button>
            <a-dropdown v-if="!isMobile">
              <a-button>
                <template #icon><SettingOutlined /></template>
                列设置
              </a-button>
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
          </div>
          <div class="lg-toolbar-right">
            <a-select
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              style="width: 160px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="filter.contractType"
              placeholder="全部类型"
              allow-clear
              style="width: 120px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option v-for="(label, key) in TYPE_LABEL" :key="key" :value="key">
                {{ label }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="filter.contractStatus"
              placeholder="全部状态"
              allow-clear
              style="width: 120px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option v-for="(label, key) in STATUS_LABEL" :key="key" :value="key">
                {{ label }}
              </a-select-option>
            </a-select>
          </div>
        </div>

        <!-- 表格：桌面/平板 -->
        <div v-if="!isMobile" class="lg-table-wrap">
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
              <a class="lg-link">{{ row.contractCode }}</a>
            </template>
            <template #contractType="{ row }">
              <a-tag :color="TYPE_COLOR[row.contractType as ContractType]">
                {{ TYPE_LABEL[row.contractType as ContractType] }}
              </a-tag>
            </template>
            <template #amount="{ row }">
              <span class="lg-money">{{
                parseFloat(row.contractAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
            <template #status="{ row }">
              <ContractStatusTag :status="row.contractStatus as ContractStatus" />
            </template>
            <template #ops="{ row }">
              <div class="lg-ops">
                <a class="lg-link" @click="handleView(row)">查看</a>
                <a class="lg-link" @click="handleEdit(row)">编辑</a>
                <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
              </div>
            </template>
          </vxe-grid>
        </div>

        <!-- 移动端卡片列表 -->
        <div v-else class="lg-card-list">
          <div v-if="loading" class="lg-card-list-loading">
            <a-spin size="large" />
          </div>
          <div v-else-if="!tableData.length" class="lg-card-list-empty">
            <a-empty />
          </div>
          <div v-for="row in tableData" :key="row.id" class="lg-card-item">
            <div class="lg-card-item-head">
              <span class="lg-card-code">
                <a class="lg-link" @click="handleView(row)">{{ row.contractCode }}</a>
              </span>
              <span class="lg-card-head-right">
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
            <div class="lg-card-item-body">
              <div v-if="colVisible.contractName" class="lg-card-field">
                <span class="lg-card-label">合同名称</span>
                <span class="lg-card-value">{{ row.contractName }}</span>
              </div>
              <div v-if="colVisible.partyAName || colVisible.partyBName" class="lg-card-field">
                <span class="lg-card-label">签约双方</span>
                <span class="lg-card-value">
                  <template v-if="colVisible.partyAName">{{ row.partyAName }}</template>
                  <template v-if="colVisible.partyAName && colVisible.partyBName"> · </template>
                  <template v-if="colVisible.partyBName">{{ row.partyBName }}</template>
                </span>
              </div>
              <div class="lg-card-field-row">
                <div v-if="colVisible.contractAmount" class="lg-card-field">
                  <span class="lg-card-label">合同金额(含税)</span>
                  <span class="lg-card-value lg-card-money">{{
                    parseFloat(row.contractAmount).toLocaleString('zh-CN', {
                      minimumFractionDigits: 2,
                    })
                  }}</span>
                </div>
                <div v-if="colVisible.signedDate" class="lg-card-field">
                  <span class="lg-card-label">签订日期</span>
                  <span class="lg-card-value">{{ row.signedDate }}</span>
                </div>
              </div>
            </div>
            <div class="lg-card-item-foot">
              <a-space :size="4">
                <a-button size="small" type="link" @click="handleView(row)">查看</a-button>
                <a-button size="small" type="link" @click="handleEdit(row)">编辑</a-button>
                <a-button size="small" type="link" danger @click="handleDelete(row)">删除</a-button>
              </a-space>
            </div>
          </div>
        </div>

        <!-- 分页 -->
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
            @show-size-change="handlePageSizeChange"
          />
        </div>
      </div>

      <!-- 右侧分析面板 -->
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">合同类型分布</div>
          <div class="lg-type-list">
            <div v-for="item in typeDistribution" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span class="lg-type-bar" :style="{ width: typePercent(item.value) + '%', background: item.color }"></span>
              </span>
              <span class="lg-type-num">{{ item.value }}</span>
              <span class="lg-type-pct">{{ typePercent(item.value) }}%</span>
            </div>
          </div>
        </section>

        <section class="lg-panel">
          <div class="lg-panel-title">合同状态</div>
          <div class="lg-type-list">
            <div v-for="item in statusBars" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span class="lg-type-bar" :style="{ width: item.percent + '%', background: item.color }"></span>
              </span>
              <span class="lg-type-num">{{ item.value }}</span>
              <span class="lg-type-pct">{{ item.percent }}%</span>
            </div>
          </div>
        </section>

        <section class="lg-panel">
          <div class="lg-warning-head">
            <div class="lg-panel-title" style="margin-bottom:0">逾期预警</div>
            <a-button type="link" size="small" @click="handleAllAlerts">查看全部</a-button>
          </div>
          <div class="lg-warning-list">
            <div
              v-for="row in warningRows"
              :key="`${row.project}-${row.title}`"
              class="lg-warning-item"
            >
              <span class="lg-warning-project">{{ row.project }}</span>
              <span class="lg-warning-title">{{ row.title }}</span>
              <span class="lg-warning-days">{{ row.days }}天</span>
            </div>
            <div v-if="!warningRows.length" class="lg-warning-empty">
              暂无逾期合同
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
/* 仅保留页面专属样式 — 其余已由 lg-* 全局类覆盖 */

.cl-contract-modal :deep(.ant-modal-body) {
  max-height: 82vh;
  overflow: auto;
}
.cl-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
</style>
