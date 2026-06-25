<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
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
    total.value = res.total
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
  return Object.entries(m).map(([k, v]) => ({ label: PAY_STATUS_LABEL[k] ?? k, count: v }))
})

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
  { title: '操作', width: 170, slots: { default: 'action' } },
])

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
  <div class="lg-list-page lg-page app-page">
    <!-- 页面头部 -->
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>付款管理</a-breadcrumb-item>
          <a-breadcrumb-item>付款申请</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-search-bar">
      <a-select
        v-model:value="filter.projectId"
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
        <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
          {{ p.projectName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.contractId"
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
        placeholder="全部类型"
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
        placeholder="全部状态"
        allow-clear
        size="large"
        @change="handleSearch"
      >
        <a-select-option v-for="(label, key) in PAY_STATUS_LABEL" :key="key" :value="key">
          {{ label }}
        </a-select-option>
      </a-select>
      <div class="lg-search-actions">
        <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>

    <div class="lg-grid">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">待付款金额</span>
            <span class="lg-kpi-card-value" style="color: #ef4444"
              >{{ kpiUnpaid.toLocaleString() }} <small>元</small></span
            >
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: #ef4444"></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">已审批未支付</span>
            <span class="lg-kpi-card-value"
              >{{ kpiApprovedUnpaid.toLocaleString() }} <small>元</small></span
            >
            <span class="lg-kpi-card-bar"
              ><span
                :style="{
                  width: kpiPct(kpiApprovedUnpaid, kpiMax.unpaid) + '%',
                  background: 'var(--kpi-unpaid)',
                }"
              ></span
            ></span>
            <span class="lg-kpi-card-hint">{{ kpiPct(kpiApprovedUnpaid, kpiMax.unpaid) }}%</span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">今日应付</span>
            <span class="lg-kpi-card-value">-<small>元</small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 0%; background: var(--muted)"></span
            ></span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">超比例付款</span>
            <span class="lg-kpi-card-value">0<small>条</small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 0%; background: var(--kpi-overdue)"></span
            ></span>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建申请
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
              </a-button>
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
                <div class="lg-ops">
                  <a class="lg-link" @click="handleEdit(row)">编辑</a>
                  <a
                    v-if="row.approvalStatus === 'DRAFT'"
                    class="lg-link"
                    @click="handleApproval(row)"
                    >提交审批</a
                  >
                  <a
                    v-if="row.approvalStatus === 'APPROVED' && row.payStatus !== 'PAID'"
                    class="lg-link"
                    @click="openWriteback(row)"
                    >付款回写</a
                  >
                  <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
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
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">付款状态统计</div>
          <div class="lg-type-list">
            <div v-for="it in statusBreakdown" :key="it.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: 'var(--kpi-paid)' }"></span>
              <span class="lg-type-label">{{ it.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{
                    width: kpiPct(it.count, total || 1) + '%',
                    background: 'var(--kpi-paid)',
                  }"
                ></span>
              </span>
              <span class="lg-type-num">{{ it.count }}</span>
              <span class="lg-type-pct">{{ kpiPct(it.count, total || 1) }}%</span>
            </div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">资金风险</div>
          <div class="lg-type-list">
            <div class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: '#ef4444' }"></span>
              <span class="lg-type-label">待付款总额</span>
              <span class="lg-type-bar-wrap">
                <span class="lg-type-bar" style="width: 100%; background: #ef4444"></span>
              </span>
              <span class="lg-type-num" style="color: #ef4444">{{
                kpiUnpaid.toLocaleString()
              }}</span>
              <span class="lg-type-pct">元</span>
            </div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">临期付款</div>
          暂无临期付款
        </section>
      </aside>
    </div>

    <!-- Create/Edit Modal -->
    <a-modal v-model:open="modalVisible" :title="modalTitle" :width="760" @ok="handleSubmit">
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
          <a-table-column title="操作" width="60"
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

<style scoped></style>
