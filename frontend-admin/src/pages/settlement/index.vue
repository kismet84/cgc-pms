<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useReferenceStore } from '@/stores/reference'
import { storeToRefs } from 'pinia'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getSettlementList,
  deleteSettlement,
  getSettlementKpi,
  createSettlement,
} from '@/api/modules/settlement'
import type {
  SettlementVO,
  SettlementQueryParams,
  SettlementKpiVO,
  SettlementStatus,
} from '@/types/settlement'
import { SETTLEMENT_STATUS_LABEL, SETTLEMENT_STATUS_COLOR } from '@/types/settlement'
import type { PageResult } from '@/types/api'

const router = useRouter()
const referenceStore = useReferenceStore()
const { projects, contracts } = storeToRefs(referenceStore)

const filter = reactive({
  keyword: '',
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  settlementStatus: undefined as SettlementStatus | undefined,
  settlementCode: '',
  settlementType: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<SettlementVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const kpi = ref<SettlementKpiVO>({
  totalCount: 0,
  totalContractAmount: '0',
  totalFinalAmount: '0',
  totalChangeAmount: '0',
  totalPaidAmount: '0',
  totalUnpaidAmount: '0',
  draftCount: 0,
  finalizedCount: 0,
})

const createModalVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive({
  contractId: undefined as string | undefined,
  settlementType: undefined as string | undefined,
  remark: '',
})

function settlementStatusOf(row: Partial<SettlementVO>): SettlementStatus {
  return (row.settlementStatus || 'DRAFT') as SettlementStatus
}
const createFormPartnerName = computed(
  () => contracts.value?.find((c) => c.id === createForm.contractId)?.partyBName ?? '',
)
watch(
  () => createForm.contractId,
  (val) => {
    if (!val) createForm.settlementType = undefined
  },
)

function onProjectChange(val: string | undefined) {
  filter.contractId = undefined
  if (val) referenceStore.fetchContracts({ projectId: val })
}

async function fetchData() {
  loading.value = true
  const params: SettlementQueryParams = {
    projectId: filter.projectId,
    contractId: filter.contractId,
    partnerId: filter.partnerId,
    settlementStatus: filter.settlementStatus,
    settlementCode: filter.settlementCode || undefined,
    settlementType: filter.settlementType,
    keyword: filter.keyword || undefined,
    pageNo: pageNo.value,
    pageSize: pageSize.value,
  }
  try {
    const res: PageResult<SettlementVO> = await getSettlementList(params)
    const records = Array.isArray(res?.records) ? res.records : Array.isArray(res) ? res : []
    tableData.value = records
    total.value = Number(res?.total ?? records.length)
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载结算列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchKpi() {
  try {
    kpi.value = { ...kpi.value, ...(await getSettlementKpi()) }
  } catch {
    kpi.value = {
      totalCount: 0,
      totalContractAmount: '0',
      totalFinalAmount: '0',
      totalChangeAmount: '0',
      totalPaidAmount: '0',
      totalUnpaidAmount: '0',
      draftCount: 0,
      finalizedCount: 0,
    }
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
  fetchKpi()
}
function handleReset() {
  filter.keyword = ''
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.settlementStatus = undefined
  filter.settlementCode = ''
  filter.settlementType = undefined
  pageNo.value = 1
  fetchData()
  fetchKpi()
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
function handleView(row: SettlementVO) {
  router.push(`/settlement/${row.id}`)
}

async function handleDelete(row: SettlementVO) {
  if (row.settlementStatus === 'FINALIZED') {
    message.warning('已定案的结算单不可删除')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定删除结算单 ${row.settlementCode}？`,
    okType: 'danger',
    onOk: async () => {
      await deleteSettlement(row.id)
      message.success('已删除')
      fetchData()
      fetchKpi()
    },
  })
}

function openCreateModal() {
  createForm.contractId = undefined
  createForm.settlementType = undefined
  createForm.remark = ''
  createModalVisible.value = true
}
async function handleCreate() {
  createLoading.value = true
  try {
    await createSettlement(createForm)
    message.success('创建成功')
    createModalVisible.value = false
    fetchData()
    fetchKpi()
  } catch (e) {
    console.error(e)
  } finally {
    createLoading.value = false
  }
}

function fmtWan(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  return isNaN(n)
    ? '0.00'
    : (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// ---- Computed KPI ----
const progressPct = computed(() => {
  const t = kpi.value.draftCount + kpi.value.finalizedCount
  return t > 0 ? ((kpi.value.finalizedCount / t) * 100).toFixed(0) + '%' : '0%'
})

const kpiMax = computed(() => ({
  totalAmount: Math.max(parseFloat(kpi.value.totalFinalAmount), 1),
}))

function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}

// ---- VxeGrid columns ----
const gridColumns = computed(() => [
  { field: 'settlementCode', title: '结算编号', minWidth: 160, ellipsis: true },
  { field: 'projectName', title: '项目', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同', minWidth: 150, ellipsis: true },
  {
    field: 'settlementAmount',
    title: '结算金额(万)',
    width: 140,
    align: 'right' as const,
    slots: { default: 'settlementAmount' },
  },
  {
    field: 'settlementStatus',
    title: '状态',
    width: 100,
    slots: { default: 'settlementStatus' },
  },
  { field: 'createdAt', title: '创建时间', width: 160 },
  { title: '操作', width: 120, slots: { default: 'ops' } },
])

// ---- Mobile detection ----
const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}
onMounted(() => {
  window.addEventListener('resize', onResize)
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({})
  referenceStore.fetchPartners()
  fetchData()
  fetchKpi()
})
onUnmounted(() => window.removeEventListener('resize', onResize))

// ---- Analysis rail ----
const statusBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    const status = settlementStatusOf(r)
    m[status] = (m[status] || 0) + 1
  })
  const total = Object.values(m).reduce((s, v) => s + v, 0) || 1
  return Object.entries(m).map(([k, v]) => ({
    label: SETTLEMENT_STATUS_LABEL[k as SettlementStatus] ?? k,
    key: k,
    count: v,
    pct: Math.round((v / total) * 100),
  }))
})

const colorMap: Record<string, string> = {
  DRAFT: '#f59e0b',
  FINALIZED: '#31c48d',
  CANCELLED: '#ef4444',
}
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <!-- 页面头部 -->
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>结算管理</a-breadcrumb-item>
          <a-breadcrumb-item>结算列表</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索结算编号、项目、合同…"
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
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div v-if="!isMobile" class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">累计结算金额</span>
            <span class="lg-kpi-card-value"
              >{{ fmtWan(kpi.totalFinalAmount) }} <small>万元</small></span
            >
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: var(--kpi-amount)"></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">待审核金额</span>
            <span class="lg-kpi-card-value"
              >{{ fmtWan(kpi.totalContractAmount) }} <small>万元</small></span
            >
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: var(--kpi-total)"></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">已确认金额</span>
            <span class="lg-kpi-card-value"
              >{{ fmtWan(kpi.totalPaidAmount) }} <small>万元</small></span
            >
            <span class="lg-kpi-card-bar"
              ><span
                :style="{
                  width: kpiPct(parseFloat(kpi.totalPaidAmount), kpiMax.totalAmount) + '%',
                  background: 'var(--kpi-paid)',
                }"
              ></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">结算进度</span>
            <span class="lg-kpi-card-value">{{ progressPct }} <small>已定案</small></span>
            <span class="lg-kpi-card-bar"
              ><span
                :style="{ width: parseFloat(progressPct) + '%', background: 'var(--kpi-unpaid)' }"
              ></span
            ></span>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <a-button type="primary" @click="openCreateModal">
                <template #icon><PlusOutlined /></template>
                新建结算
              </a-button>
              <a-button @click="fetchData">
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
                <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
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
              <template #settlementAmount="{ row }">
                <span>{{ fmtWan(row.settlementAmount) }}</span>
              </template>
              <template #settlementStatus="{ row }">
                <a-tag
                  :color="SETTLEMENT_STATUS_COLOR[settlementStatusOf(row)] || 'default'"
                  size="small"
                >
                  {{ SETTLEMENT_STATUS_LABEL[settlementStatusOf(row)] ?? settlementStatusOf(row) }}
                </a-tag>
              </template>
              <template #ops="{ row }">
                <div class="lg-ops">
                  <a class="lg-link" @click="handleView(row)">查看</a>
                  <a
                    v-if="settlementStatusOf(row) !== 'FINALIZED'"
                    class="lg-link lg-del"
                    @click="handleDelete(row)"
                    >删除</a
                  >
                </div>
              </template>
            </vxe-grid>
          </div>

          <!-- 分页 -->
          <div class="lg-pagination">
            <span class="lg-total">共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :page-size-options="['10', '20', '50']"
              show-size-changer
              show-quick-jumper
              @change="handlePageChange"
              @showSizeChange="handlePageSizeChange"
            />
          </div>
        </main>
      </div>

      <!-- 右侧分析面板 -->
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">结算状态分布</div>
          <div class="lg-type-list">
            <div v-for="it in statusBreakdown" :key="it.key" class="lg-type-row">
              <span
                class="lg-type-dot"
                :style="{ background: colorMap[it.key] || '#94a3b8' }"
              ></span>
              <span class="lg-type-label">{{ it.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: it.pct + '%', background: colorMap[it.key] || '#94a3b8' }"
                ></span>
              </span>
              <span class="lg-type-num">{{ it.count }}</span>
              <span class="lg-type-pct">{{ it.pct }}%</span>
            </div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">草稿/定案概览</div>
          <div class="lg-type-list">
            <div class="lg-type-row">
              <span class="lg-type-dot" style="background: #f59e0b"></span>
              <span class="lg-type-label">草稿</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{
                    width:
                      (kpi.totalCount > 0
                        ? ((kpi.draftCount / kpi.totalCount) * 100).toFixed(0)
                        : '0') + '%',
                    background: '#f59e0b',
                  }"
                ></span>
              </span>
              <span class="lg-type-num">{{ kpi.draftCount }}</span>
              <span class="lg-type-pct"
                >{{
                  kpi.totalCount > 0 ? ((kpi.draftCount / kpi.totalCount) * 100).toFixed(0) : '0'
                }}%</span
              >
            </div>
            <div class="lg-type-row">
              <span class="lg-type-dot" style="background: #31c48d"></span>
              <span class="lg-type-label">已定案</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{
                    width:
                      (kpi.totalCount > 0
                        ? ((kpi.finalizedCount / kpi.totalCount) * 100).toFixed(0)
                        : '0') + '%',
                    background: '#31c48d',
                  }"
                ></span>
              </span>
              <span class="lg-type-num">{{ kpi.finalizedCount }}</span>
              <span class="lg-type-pct"
                >{{
                  kpi.totalCount > 0
                    ? ((kpi.finalizedCount / kpi.totalCount) * 100).toFixed(0)
                    : '0'
                }}%</span
              >
            </div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">未付金额提醒</div>
          <div class="lg-type-list">
            <div class="lg-type-row">
              <span class="lg-type-dot" style="background: #ef4444"></span>
              <span class="lg-type-label">未付金额</span>
              <span class="lg-type-num" style="color: #ef4444; font-weight: 600"
                >{{ fmtWan(kpi.totalUnpaidAmount) }} 万</span
              >
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>

  <!-- 弹窗 -->
  <a-modal
    v-model:open="createModalVisible"
    title="新建结算单"
    :confirm-loading="createLoading"
    @ok="handleCreate"
  >
    <a-form layout="vertical">
      <a-form-item label="关联合同" required>
        <a-select
          v-model:value="createForm.contractId"
          placeholder="请选择合同"
          style="width: 100%"
          show-search
          option-filter-prop="label"
        >
          <a-select-option v-for="c in contracts" :key="c.id" :value="c.id" :label="c.contractName">
            {{ c.contractName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="合作方">
        <a-input :value="createFormPartnerName" disabled placeholder="选择合同后自动填充乙方" />
      </a-form-item>
      <a-form-item label="结算类型">
        <a-select
          v-model:value="createForm.settlementType"
          placeholder="请选择"
          allow-clear
          style="width: 100%"
        >
          <a-select-option value="PROGRESS">进度结算</a-select-option>
          <a-select-option value="FINAL">竣工结算</a-select-option>
          <a-select-option value="INTERIM">期中结算</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model:value="createForm.remark" placeholder="备注（选填）" :rows="3" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped>
/* 页面专属样式 — 其余已由 lg-* 全局类覆盖 */
</style>
