<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useReferenceStore } from '@/stores/reference'
import { storeToRefs } from 'pinia'
import {
  CheckCircleOutlined,
  DollarOutlined,
  FileDoneOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
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
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'
import { SETTLEMENT_GRID_COLUMNS, SETTLEMENT_STATUS_COLOR_MAP } from './pageConfig'

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
  } catch (e: unknown) {
    console.error(e)
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
    message.warning('结算统计加载失败，已显示空摘要')
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
  } catch (e: unknown) {
    console.error(e)
    message.error('创建结算单失败')
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

const kpiMax = computed(() => ({
  totalAmount: Math.max(parseFloat(kpi.value.totalFinalAmount), 1),
}))

function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}

// ---- VxeGrid columns ----
const gridColumns = computed(() => SETTLEMENT_GRID_COLUMNS)

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('settlement_list_cols_v2', gridColumns, {
  createdAt: false,
})

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

const colorMap = SETTLEMENT_STATUS_COLOR_MAP

const amountBreakdown = computed(() => {
  const finalAmount = parseFloat(kpi.value.totalFinalAmount) || 0
  const paidAmount = parseFloat(kpi.value.totalPaidAmount) || 0
  const unpaidAmount = parseFloat(kpi.value.totalUnpaidAmount) || 0
  const changeAmount = parseFloat(kpi.value.totalChangeAmount) || 0
  const max = Math.max(finalAmount, paidAmount, unpaidAmount, changeAmount, 1)
  return [
    { key: 'final', label: '定案金额', value: finalAmount, color: '#2563eb' },
    { key: 'paid', label: '已付金额', value: paidAmount, color: '#31c48d' },
    { key: 'unpaid', label: '未付金额', value: unpaidAmount, color: '#ef4444' },
    { key: 'change', label: '变更金额', value: changeAmount, color: '#f59e0b' },
  ].map((item) => ({
    ...item,
    display: fmtWan(String(item.value)),
    percent: kpiPct(item.value, max),
  }))
})

const paymentWarnings = computed(() =>
  tableData.value
    .map((row) => {
      const unpaid = parseFloat(row.unpaidAmount || '0') || 0
      return {
        id: row.id,
        project: row.projectName || '-',
        title: row.settlementCode || row.contractName || '-',
        amount: fmtWan(String(unpaid)),
        unpaid,
      }
    })
    .filter((row) => row.unpaid > 0)
    .sort((a, b) => b.unpaid - a.unpaid)
    .slice(0, 4),
)

function rowSettlementAmount(row: SettlementVO): string {
  return row.finalAmount || row.contractAmount || '0'
}
</script>

<template>
  <div class="lg-list-page lg-page app-page settlement-page">
    <!-- 页面头部 -->
    <div class="lg-page-head settlement-page-head">
      <div class="settlement-page-meta-row">
        <div>
          <a-breadcrumb class="settlement-breadcrumb">
            <a-breadcrumb-item>结算管理</a-breadcrumb-item>
            <a-breadcrumb-item>结算列表</a-breadcrumb-item>
          </a-breadcrumb>
          <div class="settlement-page-title-row">
            <h1>结算审定台账</h1>
            <span>送审、审定、差额与付款缺口集中核对</span>
          </div>
        </div>
        <div class="settlement-head-digest">
          <div>
            <span>定案金额</span>
            <strong>{{ fmtWan(kpi.totalFinalAmount) }}万</strong>
          </div>
          <div>
            <span>变更差额</span>
            <strong>{{ fmtWan(kpi.totalChangeAmount) }}万</strong>
          </div>
          <div>
            <span>未付金额</span>
            <strong>{{ fmtWan(kpi.totalUnpaidAmount) }}万</strong>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar settlement-search-bar">
      <div class="settlement-search-title">
        <strong>查询条件</strong>
        <span>编号 / 项目 / 状态</span>
      </div>
      <div class="settlement-search-fields">
        <a-input
          v-model:value="filter.keyword"
          class="settlement-search-input"
          placeholder="搜索结算编号、项目、合同"
          allow-clear
          size="large"
          @press-enter="handleSearch"
        >
          <template #prefix><SearchOutlined class="settlement-search-prefix-icon" /></template>
        </a-input>
        <a-select
          v-model:value="filter.projectId"
          class="settlement-search-select"
          placeholder="全部项目"
          allow-clear
          size="large"
          @change="onProjectChange"
        >
          <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.settlementStatus"
          class="settlement-search-select is-compact"
          placeholder="状态"
          allow-clear
          size="large"
        >
          <a-select-option value="DRAFT">草稿</a-select-option>
          <a-select-option value="FINALIZED">已定案</a-select-option>
          <a-select-option value="CANCELLED">已作废</a-select-option>
        </a-select>
      </div>
      <div class="settlement-search-actions">
        <a-button type="primary" size="large" @click="handleSearch">搜索</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>

    <div class="lg-grid settlement-workspace">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div v-if="!isMobile" class="lg-kpi-strip settlement-kpi-summary" aria-label="结算关键指标">
          <div class="lg-kpi-card settlement-kpi-item">
            <span class="settlement-kpi-icon is-total"><FileDoneOutlined /></span>
            <span class="settlement-kpi-label">结算总数</span>
            <span class="settlement-kpi-value">{{ kpi.totalCount }} <small>单</small></span>
          </div>
          <div class="lg-kpi-card settlement-kpi-item is-wide">
            <span class="settlement-kpi-icon is-amount"><DollarOutlined /></span>
            <span class="settlement-kpi-label">合同金额</span>
            <span class="settlement-kpi-value"
              >{{ fmtWan(kpi.totalContractAmount) }} <small>万元</small></span
            >
          </div>
          <div class="lg-kpi-card settlement-kpi-item is-progress">
            <span class="settlement-kpi-icon is-final"><CheckCircleOutlined /></span>
            <span class="settlement-kpi-label">定案金额</span>
            <span class="settlement-kpi-value"
              >{{ fmtWan(kpi.totalFinalAmount) }} <small>万元</small></span
            >
            <span class="settlement-kpi-progress">
              <span
                :style="{
                  width: kpiPct(parseFloat(kpi.totalFinalAmount), kpiMax.totalAmount) + '%',
                }"
              ></span
            ></span>
          </div>
          <div class="lg-kpi-card settlement-kpi-item is-progress is-paid">
            <span class="settlement-kpi-icon is-paid"><WalletOutlined /></span>
            <span class="settlement-kpi-label">已付金额</span>
            <span class="settlement-kpi-value"
              >{{ fmtWan(kpi.totalPaidAmount) }} <small>万元</small></span
            >
            <span class="settlement-kpi-progress">
              <span
                :style="{
                  width: kpiPct(parseFloat(kpi.totalPaidAmount), kpiMax.totalAmount) + '%',
                }"
              ></span
            ></span>
          </div>
          <div class="lg-kpi-card settlement-kpi-item is-unpaid">
            <span class="settlement-kpi-icon is-unpaid"><WalletOutlined /></span>
            <span class="settlement-kpi-label">未付金额</span>
            <span class="settlement-kpi-value"
              >{{ fmtWan(kpi.totalUnpaidAmount) }} <small>万元</small></span
            >
          </div>
        </div>

        <main class="lg-list-table-panel settlement-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar settlement-toolbar">
            <div class="lg-toolbar-left">
              <div class="settlement-table-heading">
                <span class="settlement-table-title">结算记录明细</span>
                <span class="settlement-table-count">共 {{ total }} 条，表格为主操作区</span>
              </div>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="openCreateModal">
                <template #icon><PlusOutlined /></template>
                新建结算
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="settlement-toolbar-hint">结算编号进入单据，行末查看更多操作</span>
            </div>
          </div>

          <!-- 表格 -->
          <div class="lg-table-wrap">
            <vxe-grid
              :data="tableData"
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #settlementCode="{ row }">
                <a-button class="settlement-code-link" type="link" @click="handleView(row)">
                  {{ row.settlementCode || '-' }}
                </a-button>
              </template>
              <template #settlementAmount="{ row }">
                <span>{{ fmtWan(rowSettlementAmount(row)) }}</span>
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
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleView(row)">查看</a-menu-item>
                      <a-menu-item
                        v-if="settlementStatusOf(row) !== 'FINALIZED'"
                        danger
                        @click="handleDelete(row)"
                      >
                        删除
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
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
      <aside class="lg-analysis-rail settlement-analysis-rail" aria-label="结算辅助分析">
        <div class="settlement-analysis-panel">
          <header class="settlement-analysis-head">
            <div>
              <div class="settlement-analysis-title">辅助分析</div>
              <div class="settlement-analysis-subtitle">状态、金额结构与付款提醒</div>
            </div>
            <a-button type="link" size="small" @click="fetchData">刷新</a-button>
          </header>

          <section class="settlement-analysis-focus">
            <span>本页重点</span>
            <strong>{{ fmtWan(kpi.totalUnpaidAmount) }} 万</strong>
            <em>审定后仍未支付金额，优先核对付款计划与合同差额。</em>
          </section>

          <section class="settlement-analysis-section">
            <div class="settlement-section-title">结算状态分布</div>
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
            <div v-if="!statusBreakdown.length" class="settlement-analysis-empty">
              暂无结算状态数据
            </div>
          </section>

          <section class="settlement-analysis-section">
            <div class="settlement-section-title">金额结构</div>
            <div v-for="item in amountBreakdown" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: item.percent + '%', background: item.color }"
                ></span>
              </span>
              <span class="settlement-type-amount">{{ item.display }}</span>
            </div>
          </section>

          <section class="settlement-analysis-section">
            <div class="settlement-warning-head">
              <div class="settlement-section-title">未付金额提醒</div>
              <span class="settlement-warning-count">{{ paymentWarnings.length }} 项</span>
            </div>
            <div v-for="item in paymentWarnings" :key="item.id" class="lg-warning-item">
              <span class="lg-warning-project">{{ item.project }}</span>
              <span class="lg-warning-title">{{ item.title }}</span>
              <span class="settlement-warning-amount">{{ item.amount }}万</span>
            </div>
            <div v-if="!paymentWarnings.length" class="lg-warning-empty">暂无未付提醒</div>
          </section>
        </div>
      </aside>
    </div>
  </div>

  <!-- 弹窗 -->
  <a-modal
    v-model:open="createModalVisible"
    title="新建结算单"
    :width="800"
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
.settlement-page {
  gap: 14px;
}

.settlement-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid var(--border-subtle);
  border-left: 4px solid var(--primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.settlement-page-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
  min-width: 0;
}

.settlement-breadcrumb {
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 20px;
}

.settlement-page-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.settlement-page-title-row h1 {
  margin: 0;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 32px;
}

.settlement-page-title-row span,
.settlement-head-digest span,
.settlement-search-title span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.settlement-head-digest {
  display: grid;
  grid-template-columns: repeat(3, minmax(96px, 1fr));
  gap: 10px;
  min-width: 360px;
}

.settlement-head-digest > div {
  padding: 10px 12px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.settlement-head-digest strong {
  display: block;
  margin-top: 3px;
  color: var(--text);
  font-size: 17px;
  font-weight: 800;
  line-height: 22px;
}

.settlement-search-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
  min-height: 0;
  padding: 16px;
  border-left: 4px solid var(--primary-soft);
}

.settlement-search-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  grid-column: 1 / -1;
}

.settlement-search-title strong {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.settlement-search-fields {
  display: flex;
  flex: 1 1 auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.settlement-search-input {
  width: min(520px, 31vw);
  min-width: 320px;
  flex: 1 1 auto;
}

.settlement-search-prefix-icon {
  color: var(--text-secondary);
}

.settlement-search-select {
  width: 180px;
  flex: 0 0 180px;
}

.settlement-search-select.is-compact {
  width: 150px;
  flex-basis: 150px;
}

.settlement-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.settlement-workspace {
  align-items: stretch;
  min-height: 0;
}

.settlement-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  overflow: hidden;
  min-height: 84px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.settlement-page .lg-left > .settlement-kpi-summary {
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
}

.settlement-kpi-item {
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

.settlement-kpi-item:last-child {
  border-right: 0;
}

.settlement-kpi-icon {
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

.settlement-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.settlement-kpi-icon.is-final,
.settlement-kpi-icon.is-paid {
  color: var(--success);
  background: var(--success-soft);
}

.settlement-kpi-icon.is-unpaid {
  color: var(--error);
  background: var(--error-soft);
}

.settlement-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settlement-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settlement-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.settlement-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.settlement-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.settlement-kpi-item.is-paid .settlement-kpi-progress > span {
  background: var(--kpi-unpaid);
}

.settlement-table-panel {
  min-height: 754px;
  border-top: 3px solid var(--primary);
}

.settlement-toolbar {
  align-items: center;
  min-height: 58px;
  background: linear-gradient(180deg, #fff, var(--surface-subtle));
}

.settlement-table-heading {
  display: grid;
  gap: 2px;
  margin-right: 4px;
}

.settlement-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.settlement-table-count,
.settlement-toolbar-hint {
  color: var(--text-secondary);
  font-size: 12px;
}

.settlement-analysis-rail {
  width: 336px;
}

.settlement-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.settlement-analysis-focus {
  display: grid;
  gap: 4px;
  padding: 14px;
  background: var(--error-soft);
  border: 1px solid rgba(239, 68, 68, 0.18);
  border-radius: var(--radius-md);
}

.settlement-analysis-focus span,
.settlement-analysis-focus em {
  color: var(--text-secondary);
  font-size: 12px;
  font-style: normal;
}

.settlement-analysis-focus strong {
  color: var(--error);
  font-size: 24px;
  font-weight: 800;
  line-height: 30px;
}

.settlement-analysis-head,
.settlement-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.settlement-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.settlement-analysis-subtitle,
.settlement-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.settlement-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.settlement-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.settlement-analysis-empty {
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: center;
}

.settlement-analysis-section :deep(.lg-type-row),
.lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

.settlement-type-amount {
  color: var(--text);
  font-size: 12px;
  font-weight: 700;
  text-align: right;
  white-space: nowrap;
}

.settlement-warning-amount {
  color: var(--error);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .settlement-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .settlement-page .lg-left > .settlement-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .settlement-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }

  .settlement-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .settlement-page-meta-row,
  .settlement-search-bar,
  .settlement-search-fields {
    align-items: stretch;
    flex-direction: column;
  }

  .settlement-page-subtitle {
    white-space: normal;
  }

  .settlement-head-digest {
    width: 100%;
    min-width: 0;
    grid-template-columns: 1fr;
  }

  .settlement-search-input,
  .settlement-search-select,
  .settlement-search-select.is-compact {
    width: 100%;
    min-width: 0;
    flex-basis: auto;
  }
}
</style>
