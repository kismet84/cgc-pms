<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  MoreOutlined,
  PayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
import {
  getApplicationList,
  createApplication,
  updateApplication,
  deleteApplication,
  getBasisList,
  saveBasis,
  submitForApproval,
  doWriteback,
} from '@/api/modules/payment'
import { useReferenceStore } from '@/stores/reference'
import { getReceiptList } from '@/api/modules/receipt'
import { getMeasureList } from '@/api/modules/subcontract'
import type { PayApplicationVO, PayApplicationBasisVO } from '@/types/payment'
import { PAY_TYPE_LABEL, PAY_TYPE_COLOR, PAY_STATUS_LABEL, PAY_STATUS_COLOR } from '@/types/payment'
import type { MatReceiptVO } from '@/types/receipt'
import type { SubMeasureVO } from '@/types/subcontract'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  payType: undefined as string | undefined,
  payStatus: undefined as string | undefined,
  approvalStatus: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<PayApplicationVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const referenceStore = useReferenceStore()
const { projects, contracts } = storeToRefs(referenceStore)
const receiptList = ref<MatReceiptVO[]>([])
const measureList = ref<SubMeasureVO[]>([])
const modalVisible = ref(false)
const modalTitle = ref('新建付款申请')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<PayApplicationVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  payType: undefined,
  applyAmount: undefined,
  applyReason: '',
})
const formPartnerName = computed(
  () => contracts.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)
function onContractChange(contractId: string) {
  const c = contracts.value?.find((ct) => ct.id === contractId)
  formData.partnerId = c?.partyBId
}
watch(
  () => formData.contractId,
  (val) => {
    if (!val) formData.partnerId = undefined
  },
)
const basisList = ref<(Partial<PayApplicationBasisVO> & { key: number })[]>([])
let basisKeyCounter = 0
const writebackVisible = ref(false)
const writebackTargetId = ref('')
const writebackForm = reactive({
  payAmount: undefined as number | undefined,
  payDate: undefined as string | undefined,
  payMethod: 'BANK_TRANSFER',
  voucherNo: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getApplicationList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      payType: filter.payType,
      payStatus: filter.payStatus,
      approvalStatus: filter.approvalStatus,
    })
    tableData.value = res.records
    total.value = Number(res.total ?? 0)
  } catch (e) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载付款申请列表失败')
  } finally {
    loading.value = false
  }
}
async function fetchReceipts() {
  try {
    const res = await getReceiptList({ pageNum: 1, pageSize: 50 })
    receiptList.value = res.records
  } catch {
    receiptList.value = []
  }
}
async function fetchMeasures() {
  try {
    const res = await getMeasureList({ pageNum: 1, pageSize: 50 })
    measureList.value = res.records
  } catch {
    measureList.value = []
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}
function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.payType = undefined
  filter.payStatus = undefined
  filter.approvalStatus = undefined
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

function handleAdd() {
  modalTitle.value = '新建付款申请'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    payType: undefined,
    applyAmount: undefined,
    applyReason: '',
  })
  basisList.value = []
  basisKeyCounter = 0
  modalVisible.value = true
}
async function handleEdit(record: PayApplicationVO) {
  modalTitle.value = '编辑付款申请'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    payType: record.payType,
    applyAmount: record.applyAmount,
    applyReason: record.applyReason ?? '',
  })
  try {
    const data = await getBasisList(record.id)
    basisList.value = data.map((it, idx) => ({ ...it, key: idx }))
    basisKeyCounter = basisList.value.length
  } catch {
    message.error('加载付款依据失败，请稍后重试')
    return
  }
  modalVisible.value = true
}
async function handleDelete(record: PayApplicationVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除付款申请 ${record.applyCode}？`,
    okType: 'danger',
    onOk: async () => {
      await deleteApplication(record.id)
      message.success('已删除')
      fetchData()
    },
  })
}

async function handleSubmit() {
  const id = editingId.value
  try {
    if (id) {
      await updateApplication(id, formData)
      await saveBasis(id, basisList.value)
      message.success('更新成功')
    } else {
      const id = await createApplication(formData)
      await saveBasis(id, basisList.value)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()
  } catch (e) {
    console.error(e)
  }
}

async function handleApproval(record: PayApplicationVO) {
  try {
    await submitForApproval(record.id)
    message.success('已提交审批')
    fetchData()
  } catch (e) {
    console.error(e)
  }
}
function openWriteback(record: PayApplicationVO) {
  writebackTargetId.value = record.id
  writebackForm.payAmount = undefined
  writebackForm.payDate = undefined
  writebackForm.payMethod = 'BANK_TRANSFER'
  writebackForm.voucherNo = ''
  writebackVisible.value = true
}
async function handleWritebackOk() {
  try {
    await doWriteback(writebackTargetId.value, writebackForm)
    message.success('回写成功')
    writebackVisible.value = false
    fetchData()
  } catch (e) {
    console.error(e)
  }
}
function handleWritebackCancel() {
  writebackVisible.value = false
}

function handleAddBasis() {
  basisList.value.push({
    key: basisKeyCounter++,
    sourceType: undefined,
    sourceId: undefined,
    amount: undefined,
  })
}
function handleRemoveBasis(idx: number) {
  basisList.value.splice(idx, 1)
}
function getSourceOptions(sourceType: string): { id: string; label: string }[] {
  if (sourceType === 'RECEIPT')
    return receiptList.value.map((r) => ({ id: r.id, label: r.receiptCode ?? r.id }))
  if (sourceType === 'MEASURE')
    return measureList.value.map((m) => ({ id: m.id, label: m.measureCode ?? m.id }))
  return []
}
function handleSourceChange(idx: number) {
  basisList.value[idx].sourceId = undefined
}

function fmtWan(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  return isNaN(n) ? '0.00' : (n / 10000).toFixed(2)
}
const kpiTotalApply = computed(() =>
  tableData.value.reduce((s, r) => s + (parseFloat(r.applyAmount) || 0), 0),
)
const kpiActualPaid = computed(() =>
  tableData.value.reduce((s, r) => s + (parseFloat(r.actualPayAmount || '0') || 0), 0),
)
const kpiUnpaid = computed(() =>
  tableData.value
    .filter((r) => r.payStatus === 'UNPAID' || r.payStatus === 'PARTIAL')
    .reduce((s, r) => s + (parseFloat(r.applyAmount) || 0), 0),
)
const kpiApprovedUnpaid = computed(() =>
  tableData.value
    .filter(
      (r) =>
        r.approvalStatus === 'APPROVED' && (r.payStatus === 'UNPAID' || r.payStatus === 'PARTIAL'),
    )
    .reduce((s, r) => s + (parseFloat(r.approvedAmount) || 0), 0),
)

const statusBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    m[r.payStatus] = (m[r.payStatus] || 0) + 1
  })
  const max = Math.max(total.value, tableData.value.length, 1)
  return Object.entries(m).map(([k, v]) => ({
    key: k,
    label: PAY_STATUS_LABEL[k] ?? k,
    count: v,
    percent: kpiPct(v, max),
    color: k === 'PAID' ? '#31c48d' : k === 'PARTIAL' ? '#f59e0b' : '#94a3b8',
  }))
})

const approvalBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    m[r.approvalStatus || 'DRAFT'] = (m[r.approvalStatus || 'DRAFT'] || 0) + 1
  })
  const max = Math.max(total.value, tableData.value.length, 1)
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    APPROVING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
  }
  const colors: Record<string, string> = {
    DRAFT: '#94a3b8',
    APPROVING: '#2563eb',
    APPROVED: '#31c48d',
    REJECTED: '#ef4444',
  }
  return Object.entries(m).map(([k, v]) => ({
    key: k,
    label: labels[k] ?? k,
    count: v,
    percent: kpiPct(v, max),
    color: colors[k] ?? '#94a3b8',
  }))
})

const pendingPayments = computed(() =>
  tableData.value
    .filter((r) => r.approvalStatus === 'APPROVED' && r.payStatus !== 'PAID')
    .map((row) => ({
      id: row.id,
      project: row.projectName || '-',
      title: row.applyCode || row.contractName || '-',
      amount: fmtWan(row.approvedAmount || row.applyAmount),
    }))
    .slice(0, 4),
)

const paidPct = computed(() => kpiPct(kpiActualPaid.value, Math.max(kpiTotalApply.value, 1)))
function fmtAmountText(value: number): string {
  return fmtWan(String(value))
}

const kpiMax = computed(() => ({
  unpaid: Math.max(kpiUnpaid.value, 1),
  approvedUnpaid: Math.max(kpiApprovedUnpaid.value, 1),
}))
function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}

// vxe-grid columns
const gridColumns = computed(() => [
  { field: 'applyCode', title: '申请编号', minWidth: 150, ellipsis: true },
  { field: 'projectName', title: '项目', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同', minWidth: 150, ellipsis: true },
  { field: 'partnerName', title: '合作方', minWidth: 140, ellipsis: true },
  {
    field: 'applyAmount',
    title: '申请金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'applyAmount' },
  },
  {
    field: 'approvedAmount',
    title: '审批金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'approvedAmount' },
  },
  {
    field: 'actualPayAmount',
    title: '实付金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'actualPayAmount' },
  },
  { field: 'payType', title: '付款类型', width: 108, slots: { default: 'payType' } },
  { field: 'payStatus', title: '支付状态', width: 108, slots: { default: 'payStatus' } },
  { field: 'approvalStatus', title: '审批状态', width: 108, slots: { default: 'approvalStatus' } },
  { title: '操作', width: 76, slots: { default: 'action' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('payment_list_cols', gridColumns)

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({})
  referenceStore.fetchPartners()
  fetchData()
  fetchReceipts()
  fetchMeasures()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page payment-page">
    <!-- 页面头部 -->
    <div class="lg-page-head payment-page-head">
      <div class="payment-page-meta-row">
        <a-breadcrumb class="payment-breadcrumb">
          <a-breadcrumb-item>付款管理</a-breadcrumb-item>
          <a-breadcrumb-item>付款申请</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="payment-page-subtitle">按合同跟踪申请金额、审批状态与支付回写。</span>
      </div>
    </div>

    <div class="lg-search-bar payment-search-bar">
      <div class="payment-search-fields">
        <a-select
          v-model:value="filter.projectId"
          class="payment-search-select"
          placeholder="全部项目"
          allow-clear
          size="large"
          @change="
            (v: string | undefined) => {
              filter.contractId = undefined
              if (v) referenceStore.fetchContracts({ projectId: v })
              handleSearch()
            }
          "
        >
          <template #suffixIcon><SearchOutlined /></template>
          <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.contractId"
          class="payment-search-select"
          placeholder="全部合同"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="c in contracts" :key="c.id" :value="c.id">
            {{ c.contractName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.payType"
          class="payment-search-select is-compact"
          placeholder="类型"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="(label, key) in PAY_TYPE_LABEL" :key="key" :value="key">
            {{ label }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.payStatus"
          class="payment-search-select is-compact"
          placeholder="状态"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="(label, key) in PAY_STATUS_LABEL" :key="key" :value="key">
            {{ label }}
          </a-select-option>
        </a-select>
      </div>
      <div class="payment-search-actions">
        <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>

    <div class="lg-grid payment-workspace">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div class="payment-kpi-summary" aria-label="付款关键指标">
          <div class="payment-kpi-item">
            <span class="payment-kpi-icon is-total"><PayCircleOutlined /></span>
            <span class="payment-kpi-label">申请总数</span>
            <span class="payment-kpi-value">{{ total }} <small>单</small></span>
          </div>
          <div class="payment-kpi-item is-wide">
            <span class="payment-kpi-icon is-amount"><DollarOutlined /></span>
            <span class="payment-kpi-label">申请金额</span>
            <span class="payment-kpi-value"
              >{{ fmtAmountText(kpiTotalApply) }} <small>万元</small></span
            >
          </div>
          <div class="payment-kpi-item is-progress">
            <span class="payment-kpi-icon is-paid"><CheckCircleOutlined /></span>
            <span class="payment-kpi-label">已付金额</span>
            <span class="payment-kpi-value"
              >{{ fmtAmountText(kpiActualPaid) }} <small>万元</small></span
            >
            <span class="payment-kpi-progress"
              ><span :style="{ width: paidPct + '%' }"></span
            ></span>
          </div>
          <div class="payment-kpi-item is-progress is-unpaid">
            <span class="payment-kpi-icon is-unpaid"><WalletOutlined /></span>
            <span class="payment-kpi-label">待付款金额</span>
            <span class="payment-kpi-value"
              >{{ fmtAmountText(kpiUnpaid) }} <small>万元</small></span
            >
            <span class="payment-kpi-progress">
              <span :style="{ width: kpiPct(kpiUnpaid, kpiMax.unpaid) + '%' }"></span>
            </span>
          </div>
          <div class="payment-kpi-item">
            <span class="payment-kpi-icon is-pending"><ClockCircleOutlined /></span>
            <span class="payment-kpi-label">已批未付</span>
            <span class="payment-kpi-value"
              >{{ fmtAmountText(kpiApprovedUnpaid) }} <small>万元</small></span
            >
          </div>
        </div>

        <main class="lg-list-table-panel payment-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar payment-toolbar">
            <div class="lg-toolbar-left">
              <span class="payment-table-title">付款申请</span>
              <span class="payment-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建申请
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="payment-toolbar-hint">固定表头 / 状态标签 / 行操作展开</span>
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
              <template #applyAmount="{ row }">
                <span class="lg-money">{{ fmtWan(row.applyAmount) }} 万</span>
              </template>
              <template #approvedAmount="{ row }">
                <span class="lg-money">{{ fmtWan(row.approvedAmount) }} 万</span>
              </template>
              <template #actualPayAmount="{ row }">
                <span class="lg-money">{{ fmtWan(row.actualPayAmount) }} 万</span>
              </template>
              <template #payType="{ row }">
                <a-tag :color="PAY_TYPE_COLOR[row.payType] || 'default'" size="small">{{
                  PAY_TYPE_LABEL[row.payType] ?? row.payType
                }}</a-tag>
              </template>
              <template #payStatus="{ row }">
                <a-tag :color="PAY_STATUS_COLOR[row.payStatus] || 'default'" size="small">{{
                  PAY_STATUS_LABEL[row.payStatus] ?? row.payStatus
                }}</a-tag>
              </template>
              <template #approvalStatus="{ row }">
                <a-tag
                  :color="
                    row.approvalStatus === 'APPROVED'
                      ? 'success'
                      : row.approvalStatus === 'REJECTED'
                        ? 'error'
                        : row.approvalStatus === 'APPROVING'
                          ? 'processing'
                          : 'default'
                  "
                  size="small"
                  >{{ row.approvalStatus }}</a-tag
                >
              </template>
              <template #action="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item
                        v-if="row.approvalStatus === 'DRAFT'"
                        @click="handleApproval(row)"
                      >
                        提交审批
                      </a-menu-item>
                      <a-menu-item
                        v-if="row.approvalStatus === 'APPROVED' && row.payStatus !== 'PAID'"
                        @click="openWriteback(row)"
                      >
                        付款回写
                      </a-menu-item>
                      <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
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
              :page-size-options="['10', '20', '50', '100']"
              show-size-changer
              show-quick-jumper
              @change="handlePageChange"
              @show-size-change="handlePageSizeChange"
            />
          </div>
        </main>
      </div>

      <!-- 右侧分析面板 -->
      <aside class="lg-analysis-rail payment-analysis-rail" aria-label="付款辅助分析">
        <div class="payment-analysis-panel">
          <header class="payment-analysis-head">
            <div>
              <div class="payment-analysis-title">付款分析</div>
              <div class="payment-analysis-subtitle">支付状态、审批状态与待付款</div>
            </div>
            <a-button type="link" size="small" @click="fetchData">刷新</a-button>
          </header>

          <section class="payment-analysis-section">
            <div class="payment-section-title">付款状态统计</div>
            <div v-for="it in statusBreakdown" :key="it.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: it.color }"></span>
              <span class="lg-type-label">{{ it.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: it.percent + '%', background: it.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ it.count }}</span>
              <span class="lg-type-pct">{{ it.percent }}%</span>
            </div>
            <div v-if="!statusBreakdown.length" class="payment-analysis-empty">
              暂无付款状态数据
            </div>
          </section>

          <section class="payment-analysis-section">
            <div class="payment-section-title">审批状态</div>
            <div v-for="it in approvalBreakdown" :key="it.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: it.color }"></span>
              <span class="lg-type-label">{{ it.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: it.percent + '%', background: it.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ it.count }}</span>
              <span class="lg-type-pct">{{ it.percent }}%</span>
            </div>
          </section>

          <section class="payment-analysis-section">
            <div class="payment-warning-head">
              <div class="payment-section-title">待付款提醒</div>
              <span class="payment-warning-count">{{ pendingPayments.length }} 项</span>
            </div>
            <div v-for="item in pendingPayments" :key="item.id" class="lg-warning-item">
              <span class="lg-warning-project">{{ item.project }}</span>
              <span class="lg-warning-title">{{ item.title }}</span>
              <span class="payment-warning-amount">{{ item.amount }}万</span>
            </div>
            <div v-if="!pendingPayments.length" class="lg-warning-empty">暂无待付款提醒</div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Create/Edit Modal -->
    <a-modal v-model:open="modalVisible" :title="modalTitle" :width="800" @ok="handleSubmit">
      <a-form layout="vertical" :model="formData">
        <a-row :gutter="16">
          <a-col :span="12"
            ><a-form-item label="项目"
              ><a-select
                v-model:value="formData.projectId"
                placeholder="请选择项目"
                style="width: 100%"
                :options="(projects ?? []).map((p) => ({ value: p.id, label: p.projectName }))"
                @change="
                  (v: string) => {
                    formData.contractId = undefined
                    formData.partnerId = undefined
                    referenceStore.fetchContracts({ projectId: v })
                  }
                " /></a-form-item
          ></a-col>
          <a-col :span="12"
            ><a-form-item label="合同"
              ><a-select
                v-model:value="formData.contractId"
                placeholder="请选择合同"
                style="width: 100%"
                :options="(contracts ?? []).map((c) => ({ value: c.id, label: c.contractName }))"
                @change="onContractChange" /></a-form-item
          ></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12"
            ><a-form-item label="合作方"
              ><a-input
                :value="formPartnerName"
                disabled
                placeholder="选择合同后自动填充乙方" /></a-form-item
          ></a-col>
          <a-col :span="12"
            ><a-form-item label="付款类型"
              ><a-select
                v-model:value="formData.payType"
                placeholder="请选择付款类型"
                style="width: 100%"
                ><a-select-option v-for="(label, key) in PAY_TYPE_LABEL" :key="key" :value="key">{{
                  label
                }}</a-select-option></a-select
              ></a-form-item
            ></a-col
          >
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12"
            ><a-form-item label="申请金额"
              ><a-input-number
                v-model:value="formData.applyAmount"
                :min="0"
                :precision="2"
                style="width: 100%"
                placeholder="金额（元）" /></a-form-item
          ></a-col>
          <a-col :span="12"
            ><a-form-item label="申请原因"
              ><a-textarea
                v-model:value="formData.applyReason"
                placeholder="申请原因"
                :rows="2" /></a-form-item
          ></a-col>
        </a-row>
      </a-form>
      <div style="border-top: 1px solid #f0f0f0; padding-top: 12px; margin-top: 4px">
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
          "
        >
          <span style="font-weight: 600; font-size: 14px">付款依据</span
          ><a-button size="small" @click="handleAddBasis">添加依据行</a-button>
        </div>
        <a-table
          :data-source="basisList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 240 }"
        >
          <a-table-column title="来源类型" width="100"
            ><template #default="{ record: item, index }"
              ><a-select
                v-model:value="item.sourceType"
                size="small"
                style="width: 100%"
                @change="handleSourceChange(index)"
                ><a-select-option value="RECEIPT">材料验收</a-select-option
                ><a-select-option value="MEASURE">分包计量</a-select-option></a-select
              ></template
            ></a-table-column
          >
          <a-table-column title="来源单据" width="240"
            ><template #default="{ record: item }"
              ><a-select
                v-model:value="item.sourceId"
                size="small"
                placeholder="选择单据"
                allow-clear
                style="width: 100%"
                ><a-select-option
                  v-for="opt in getSourceOptions(item.sourceType || 'RECEIPT')"
                  :key="opt.id"
                  :value="opt.id"
                  >{{ opt.label }}</a-select-option
                ></a-select
              ></template
            ></a-table-column
          >
          <a-table-column title="金额" width="160"
            ><template #default="{ record: item }"
              ><a-input-number
                v-model:value="item.amount"
                :min="0"
                :precision="2"
                size="small"
                style="width: 100%"
                placeholder="金额" /></template
          ></a-table-column>
          <a-table-column title="操作" width="76"
            ><template #default="{ index }"
              ><a-button type="link" size="small" danger @click="handleRemoveBasis(index)"
                >删除</a-button
              ></template
            ></a-table-column
          >
        </a-table>
      </div>
    </a-modal>

    <!-- Writeback Modal -->
    <a-modal
      v-model:open="writebackVisible"
      title="付款回写"
      :width="480"
      @ok="handleWritebackOk"
      @cancel="handleWritebackCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="支付金额" required
          ><a-input-number
            v-model:value="writebackForm.payAmount"
            :min="0.01"
            :precision="2"
            style="width: 100%"
            placeholder="请输入支付金额"
        /></a-form-item>
        <a-form-item label="支付日期" required
          ><a-date-picker
            v-model:value="writebackForm.payDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
        /></a-form-item>
        <a-form-item label="支付方式" required
          ><a-select v-model:value="writebackForm.payMethod" placeholder="请选择"
            ><a-select-option value="BANK_TRANSFER">银行转账</a-select-option
            ><a-select-option value="CASH">现金</a-select-option
            ><a-select-option value="CHECK">支票</a-select-option
            ><a-select-option value="OTHER">其他</a-select-option></a-select
          ></a-form-item
        >
        <a-form-item label="凭证号"
          ><a-input v-model:value="writebackForm.voucherNo" placeholder="请输入凭证号"
        /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.payment-page {
  gap: 14px;
}

.payment-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.payment-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.payment-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.payment-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.payment-search-bar {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 91px;
}

.payment-search-fields {
  display: flex;
  flex: 1 1 auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.payment-search-select {
  width: 230px;
  flex: 0 0 230px;
}

.payment-search-select.is-compact {
  width: 150px;
  flex-basis: 150px;
}

.payment-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.payment-workspace {
  align-items: stretch;
  min-height: 0;
}

.payment-kpi-summary {
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

.payment-kpi-item {
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

.payment-kpi-item:last-child {
  border-right: 0;
}

.payment-kpi-icon {
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

.payment-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.payment-kpi-icon.is-paid {
  color: var(--success);
  background: var(--success-soft);
}

.payment-kpi-icon.is-unpaid,
.payment-kpi-icon.is-pending {
  color: var(--error);
  background: var(--error-soft);
}

.payment-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.payment-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.payment-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.payment-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.payment-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.payment-kpi-item.is-unpaid .payment-kpi-progress > span {
  background: var(--kpi-unpaid);
}

.payment-table-panel {
  min-height: 754px;
}

.payment-toolbar {
  align-items: center;
}

.payment-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.payment-table-count,
.payment-toolbar-hint {
  color: var(--text-secondary);
  font-size: 12px;
}

.payment-analysis-rail {
  width: 336px;
}

.payment-analysis-panel {
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

.payment-analysis-head,
.payment-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.payment-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.payment-analysis-subtitle,
.payment-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.payment-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.payment-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.payment-analysis-empty {
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: center;
}

.payment-analysis-section :deep(.lg-type-row),
.lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

.payment-warning-amount {
  color: var(--error);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .payment-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .payment-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }

  .payment-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .payment-page-meta-row,
  .payment-search-bar,
  .payment-search-fields {
    align-items: stretch;
    flex-direction: column;
  }

  .payment-page-subtitle {
    white-space: normal;
  }

  .payment-search-select,
  .payment-search-select.is-compact {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
