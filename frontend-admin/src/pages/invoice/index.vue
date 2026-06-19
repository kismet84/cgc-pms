<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal, Upload } from 'ant-design-vue'
import axios from 'axios'
import {
  SearchOutlined,
  UploadOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import {
  getInvoiceList,
  createInvoice,
  updateInvoice,
  deleteInvoice,
  verifyInvoice,
  getPayRecordList,
  recognizeInvoice,
} from '@/api/modules/invoice'
import type { InvoiceVO, PayRecordBrief, InvoiceRecognizeResultVO } from '@/types/invoice'
import {
  INVOICE_TYPE_LABEL,
  INVOICE_TYPE_COLOR,
  VERIFY_STATUS_LABEL,
  VERIFY_STATUS_COLOR,
} from '@/types/invoice'
import { uploadFile } from '@/api/modules/file'

const INVOICE_BUSINESS_TYPE = 'INVOICE_ATTACHMENT'

const filter = reactive({
  keyword: '',
  payRecordId: undefined as string | undefined,
  verifyStatus: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<InvoiceVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const payRecordList = ref<PayRecordBrief[]>([])

const modalVisible = ref(false)
const modalTitle = ref('新增发票')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<InvoiceVO>>({
  payRecordId: undefined,
  invoiceNo: '',
  invoiceType: 'VAT_SPECIAL',
  invoiceAmount: undefined,
  taxRate: undefined,
  taxAmount: undefined,
  invoiceDate: undefined,
  sellerName: undefined,
  buyerName: undefined,
  buyerTaxNo: undefined,
  sellerTaxNo: undefined,
  remark: '',
})

const uploadFileList = ref<any[]>([])
const recognizing = ref(false)
const recognizeResult = ref<InvoiceRecognizeResultVO | null>(null)
const abortController = ref<AbortController | null>(null)

// ---- vxe-grid columns ----
const gridColumns = computed(() => [
  { field: 'invoiceNo', title: '发票号码', width: 140, ellipsis: true },
  { field: 'invoiceType', title: '发票类型', width: 100, slots: { default: 'invoiceType' } },
  {
    field: 'invoiceAmount',
    title: '发票金额',
    width: 110,
    align: 'right' as const,
    slots: { default: 'invoiceAmount' },
  },
  {
    field: 'taxRate',
    title: '税率(%)',
    width: 80,
    slots: { default: 'taxRate' },
  },
  {
    field: 'taxAmount',
    title: '税额',
    width: 110,
    align: 'right' as const,
    slots: { default: 'taxAmount' },
  },
  { field: 'invoiceDate', title: '开票日期', width: 110 },
  { field: 'verifyStatus', title: '核验状态', width: 90, slots: { default: 'verifyStatus' } },
  { field: 'remark', title: '备注', width: 120, ellipsis: true },
  { field: 'createdAt', title: '创建时间', width: 150 },
  { title: '操作', width: 150, slots: { default: 'action' } },
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getInvoiceList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      payRecordId: filter.payRecordId,
      invoiceNo: filter.keyword || undefined,
      verifyStatus: filter.verifyStatus,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载发票列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchPayRecords() {
  try {
    const res = await getPayRecordList()
    payRecordList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    payRecordList.value = []
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.keyword = ''
  filter.payRecordId = undefined
  filter.verifyStatus = undefined
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
  modalTitle.value = '新增发票'
  editingId.value = null
  Object.assign(formData, {
    payRecordId: undefined,
    invoiceNo: '',
    invoiceType: 'VAT_SPECIAL',
    invoiceAmount: undefined,
    taxRate: undefined,
    taxAmount: undefined,
    invoiceDate: undefined,
    sellerName: undefined,
    buyerName: undefined,
    buyerTaxNo: undefined,
    sellerTaxNo: undefined,
    remark: '',
  })
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  uploadFileList.value = []
  recognizeResult.value = null
  modalVisible.value = true
}

function handleEdit(record: InvoiceVO) {
  modalTitle.value = '编辑发票'
  editingId.value = record.id
  Object.assign(formData, {
    payRecordId: record.payRecordId,
    invoiceNo: record.invoiceNo,
    invoiceType: record.invoiceType,
    invoiceAmount: record.invoiceAmount,
    taxRate: record.taxRate,
    taxAmount: record.taxAmount,
    invoiceDate: record.invoiceDate,
    sellerName: record.sellerName,
    buyerName: record.buyerName,
    buyerTaxNo: record.buyerTaxNo,
    sellerTaxNo: record.sellerTaxNo,
    remark: record.remark,
  })
  modalVisible.value = true
}

function handleDelete(record: InvoiceVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除发票"${record.invoiceNo}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteInvoice(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        Modal.error({ title: '删除失败', content: '删除失败，请稍后重试' })
      }
    },
  })
}

function handleVerify(record: InvoiceVO) {
  Modal.confirm({
    title: '发票核验',
    content: `请选择发票"${record.invoiceNo}"的核验结果：`,
    okText: '认证通过',
    cancelText: '标记异常',
    okType: 'primary',
    cancelButtonProps: { danger: true },
    onOk: async () => {
      try {
        await verifyInvoice(record.id, 'VERIFIED')
        message.success('发票已认证通过')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('核验操作失败，请稍后重试')
      }
    },
    onCancel: async () => {
      try {
        await verifyInvoice(record.id, 'ABNORMAL')
        message.warning('发票已标记为异常')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('核验操作失败，请稍后重试')
      }
    },
  })
}

async function handleModalOk() {
  if (!formData.invoiceNo || formData.invoiceNo.trim() === '') {
    message.warning('请输入发票号码')
    return
  }
  if (!formData.invoiceType) {
    message.warning('请选择发票类型')
    return
  }
  if (!formData.invoiceAmount || parseFloat(formData.invoiceAmount) <= 0) {
    message.warning('请输入有效的发票金额')
    return
  }

  try {
    let invoiceId: string | null = null
    if (editingId.value) {
      await updateInvoice(editingId.value, formData)
      invoiceId = editingId.value
      message.success('更新成功')
    } else {
      invoiceId = await createInvoice(formData)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()

    // Upload attachment if a file was selected
    if (uploadFileList.value.length > 0 && invoiceId) {
      const file = uploadFileList.value[0].originFileObj as File
      try {
        await uploadFile(file, INVOICE_BUSINESS_TYPE, invoiceId)
      } catch (e: unknown) {
        console.error(e)
        message.warning('发票已创建，但文件上传失败。请稍后在发票详情中重新上传。')
      }
    }
  } catch (e: unknown) {
    console.error(e)
    const msg = axios.isAxiosError(e)
      ? (e.response?.data as { message?: string })?.message || e.message
      : e instanceof Error
        ? e.message
        : ''
    if (msg.includes('已存在') || msg.includes('duplicate')) {
      Modal.error({ title: '操作失败', content: '发票号码已存在，同一租户下发票号码不可重复' })
    } else {
      Modal.error({ title: '操作失败', content: '操作失败，请稍后重试' })
    }
  }
}

function handleModalCancel() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  uploadFileList.value = []
  recognizeResult.value = null
  modalVisible.value = false
}

function handleBeforeUpload(file: File) {
  const isPdf = file.type === 'application/pdf' || file.name.endsWith('.pdf')
  if (!isPdf) {
    message.error('仅支持PDF格式')
    return Upload.LIST_IGNORE
  }
  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    message.error('文件大小不能超过50MB')
    return Upload.LIST_IGNORE
  }
  return false // prevent auto-upload; manual upload handled in handleRecognize()
}

async function handleRecognize() {
  if (!uploadFileList.value.length) return

  // Cancel any previous in-flight request
  if (abortController.value) {
    abortController.value.abort()
  }
  abortController.value = new AbortController()

  // Clear invoice-related form fields so recognition result always overwrites
  clearInvoiceFields()

  recognizing.value = true
  try {
    const file = uploadFileList.value[0].originFileObj as File
    const result = await recognizeInvoice(file, abortController.value.signal)

    if (result) {
      applyRecognitionResult(result)
      message.success('发票识别完成，请核对自动填充的内容')
    } else {
      message.warning('未识别到发票信息，请手动填写')
    }
  } catch (e: unknown) {
    console.error(e)
    if (axios.isCancel(e)) {
      return
    }
    if (axios.isAxiosError(e)) {
      if (e.code === 'ECONNABORTED' || e.message?.includes('timeout')) {
        message.error('识别超时，请检查网络后重试')
      }
    }
  } finally {
    recognizing.value = false
    abortController.value = null
  }
}

/** Clear all invoice-related fields before re-recognizing */
function clearInvoiceFields() {
  const fields: (keyof InvoiceVO)[] = [
    'invoiceNo',
    'invoiceType',
    'invoiceAmount',
    'taxRate',
    'taxAmount',
    'invoiceDate',
    'sellerName',
    'buyerName',
    'buyerTaxNo',
    'sellerTaxNo',
    'remark',
  ]
  for (const field of fields) {
    if (field === 'invoiceType') {
      ;(formData as any)[field] = 'VAT_SPECIAL'
    } else if (field === 'invoiceNo' || field === 'remark') {
      ;(formData as any)[field] = ''
    } else {
      ;(formData as any)[field] = undefined
    }
  }
}

function applyRecognitionResult(result: InvoiceRecognizeResultVO) {
  // Fill all recognized fields (form was cleared before recognition)
  const fields: (keyof InvoiceRecognizeResultVO)[] = [
    'invoiceNo',
    'invoiceType',
    'invoiceAmount',
    'taxRate',
    'taxAmount',
    'invoiceDate',
    'sellerName',
    'buyerName',
    'buyerTaxNo',
    'sellerTaxNo',
    'remark',
  ]
  for (const field of fields) {
    const value = result[field]
    if (value != null && value !== '') {
      ;(formData as any)[field] = value
    }
  }
}

function fmtAmount(val: string | undefined): string {
  if (!val) return '-'
  const n = parseFloat(val)
  if (isNaN(n)) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

const kpiInvoiceTotal = computed(() =>
  tableData.value.reduce((s, r) => s + (parseFloat(r.invoiceAmount) || 0), 0),
)
const kpiInvoiced = computed(() =>
  tableData.value
    .filter((r) => r.verifyStatus === 'VERIFIED')
    .reduce((s, r) => s + (parseFloat(r.invoiceAmount) || 0), 0),
)
const kpiUninvoiced = computed(() =>
  tableData.value
    .filter((r) => r.verifyStatus !== 'VERIFIED')
    .reduce((s, r) => s + (parseFloat(r.invoiceAmount) || 0), 0),
)
const kpiAbnormal = computed(
  () => tableData.value.filter((r) => r.verifyStatus === 'FAILED').length,
)

const kpiMax = computed(() => ({
  total: Math.max(kpiInvoiceTotal.value, 1),
}))
function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}

const verifyBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    const label = VERIFY_STATUS_LABEL[r.verifyStatus] ?? r.verifyStatus
    m[label] = (m[label] || 0) + 1
  })
  const total = Object.values(m).reduce((s, v) => s + v, 0) || 1
  return Object.entries(m).map(([label, count]) => ({
    label,
    count,
    pct: Math.round((count / total) * 100),
  }))
})

onMounted(() => {
  fetchPayRecords()
  fetchData()
})

defineExpose({
  formData,
  uploadFileList,
  recognizeResult,
  applyRecognitionResult,
  handleBeforeUpload,
  handleAdd,
})
</script>

<template>
  <div class="lg-page app-page">
    <!-- 页面头部 -->
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>发票管理</a-breadcrumb-item>
          <a-breadcrumb-item>发票列表</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索发票号码…"
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
        <div class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">发票总额</span>
            <span class="lg-kpi-card-value">{{ kpiInvoiceTotal.toLocaleString() }} <small>元</small></span>
            <span class="lg-kpi-card-bar"><span style="width: 100%; background: var(--kpi-amount)"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">已核验</span>
            <span class="lg-kpi-card-value">{{ kpiInvoiced.toLocaleString() }} <small>元</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: kpiPct(kpiInvoiced, kpiMax.total) + '%', background: 'var(--kpi-paid)' }"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">待核验</span>
            <span class="lg-kpi-card-value">{{ kpiUninvoiced.toLocaleString() }} <small>元</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: kpiPct(kpiUninvoiced, kpiMax.total) + '%', background: 'var(--kpi-unpaid)' }"></span></span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">异常发票</span>
            <span class="lg-kpi-card-value" style="color: #ef4444">{{ kpiAbnormal }} <small>张</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: kpiPct(kpiAbnormal, kpiMax.total) + '%', background: 'var(--kpi-overdue)' }"></span></span>
          </div>
        </div>

        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button type="primary" @click="handleAdd">
              <template #icon><PlusOutlined /></template>
              新增发票
            </a-button>
            <a-button @click="fetchData">
              <template #icon><ReloadOutlined /></template>
            </a-button>
          </div>
          <div class="lg-toolbar-right">
            <a-select
              v-model:value="filter.payRecordId"
              placeholder="全部付款记录"
              allow-clear
              style="width: 180px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option v-for="pr in payRecordList" :key="pr.id" :value="pr.id">
                {{ pr.voucherNo ? `#${pr.voucherNo}` : `付款记录#${pr.id}` }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="filter.verifyStatus"
              placeholder="全部核验状态"
              allow-clear
              style="width: 130px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option value="PENDING">待核验</a-select-option>
              <a-select-option value="VERIFIED">已认证</a-select-option>
              <a-select-option value="ABNORMAL">异常</a-select-option>
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
            <template #invoiceType="{ row }">
              <a-tag
                :color="
                  INVOICE_TYPE_COLOR[row.invoiceType as keyof typeof INVOICE_TYPE_COLOR] || 'default'
                "
              >
                {{
                  INVOICE_TYPE_LABEL[row.invoiceType as keyof typeof INVOICE_TYPE_LABEL] ||
                  row.invoiceType
                }}
              </a-tag>
            </template>
            <template #invoiceAmount="{ row }">
              <span class="lg-money">{{ fmtAmount(row.invoiceAmount) }}</span>
            </template>
            <template #taxRate="{ row }">
              <span>{{ row.taxRate ? row.taxRate + '%' : '-' }}</span>
            </template>
            <template #taxAmount="{ row }">
              <span class="lg-money">{{ fmtAmount(row.taxAmount) }}</span>
            </template>
            <template #verifyStatus="{ row }">
              <a-tag
                :color="
                  VERIFY_STATUS_COLOR[row.verifyStatus as keyof typeof VERIFY_STATUS_COLOR] || 'default'
                "
              >
                {{
                  VERIFY_STATUS_LABEL[row.verifyStatus as keyof typeof VERIFY_STATUS_LABEL] ||
                  row.verifyStatus
                }}
              </a-tag>
            </template>
            <template #action="{ row }">
              <div class="lg-ops">
                <a class="lg-link" @click="handleEdit(row)">编辑</a>
                <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
                <a
                  v-if="row.verifyStatus === 'PENDING'"
                  class="lg-link"
                  @click="handleVerify(row)"
                >核验</a>
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
      </div>

      <!-- 右侧分析面板 -->
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">核验状态分布</div>
          <div class="lg-type-list">
            <div v-for="it in verifyBreakdown" :key="it.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: it.label === '已认证' ? '#31c48d' : it.label === '异常' ? '#ef4444' : it.label === '待核验' ? '#f59e0b' : '#8b5cf6' }"></span>
              <span class="lg-type-label">{{ it.label }}</span>
              <span class="lg-type-bar-wrap">
                <span class="lg-type-bar" :style="{ width: it.pct + '%', background: it.label === '已认证' ? '#31c48d' : it.label === '异常' ? '#ef4444' : it.label === '待核验' ? '#f59e0b' : '#8b5cf6' }"></span>
              </span>
              <span class="lg-type-num">{{ it.count }}</span>
              <span class="lg-type-pct">{{ it.pct }}%</span>
            </div>
          </div>
        </section>

        <section class="lg-panel">
          <div class="lg-panel-title">异常提醒</div>
          <div class="lg-warning-list">
            <div v-if="kpiAbnormal > 0" class="lg-warning-item">
              <span class="lg-warning-project">核验失败</span>
              <span class="lg-warning-title">{{ kpiAbnormal }} 张</span>
              <span class="lg-warning-days" style="color: #ef4444">需处理</span>
            </div>
            <div v-else class="lg-warning-empty">无异常发票</div>
          </div>
        </section>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="680"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="发票附件">
          <a-upload
            v-model:file-list="uploadFileList"
            accept=".pdf"
            :max-count="1"
            :before-upload="handleBeforeUpload"
            :show-upload-list="true"
          >
            <a-button>
              <upload-outlined />
              点击或拖拽上传PDF发票
            </a-button>
          </a-upload>
          <a-button
            type="primary"
            style="margin-top: 8px"
            :disabled="!uploadFileList.length"
            :loading="recognizing"
            @click="handleRecognize"
          >
            识别发票
          </a-button>
        </a-form-item>
        <a-form-item label="付款记录">
          <a-select
            v-model:value="formData.payRecordId"
            placeholder="请选择关联的付款记录（可选）"
            allow-clear
          >
            <a-select-option v-for="pr in payRecordList" :key="pr.id" :value="pr.id">
              {{ pr.voucherNo ? `#${pr.voucherNo}` : `付款记录#${pr.id}` }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="发票号码" required>
          <a-input v-model:value="formData.invoiceNo" placeholder="请输入发票号码" />
        </a-form-item>
        <a-form-item label="发票类型" required>
          <a-select v-model:value="formData.invoiceType" placeholder="请选择发票类型">
            <a-select-option value="VAT_SPECIAL">增值税专票</a-select-option>
            <a-select-option value="VAT_NORMAL">增值税普票</a-select-option>
            <a-select-option value="OTHER">其他</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="发票金额" required>
          <a-input-number
            v-model:value="formData.invoiceAmount"
            :min="0"
            :precision="2"
            style="width: 100%"
            placeholder="请输入发票金额"
          />
        </a-form-item>
        <a-form-item label="税率(%)">
          <a-input-number
            v-model:value="formData.taxRate"
            :min="0"
            :max="100"
            :precision="2"
            style="width: 100%"
            placeholder="请输入税率"
          />
        </a-form-item>
        <a-form-item label="税额">
          <a-input-number
            v-model:value="formData.taxAmount"
            :min="0"
            :precision="2"
            style="width: 100%"
            placeholder="请输入税额"
          />
        </a-form-item>
        <a-form-item label="开票日期">
          <a-date-picker
            v-model:value="formData.invoiceDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="卖方名称">
          <a-input v-model:value="formData.sellerName" placeholder="请输入卖方名称" />
        </a-form-item>
        <a-form-item label="卖方税号">
          <a-input v-model:value="formData.sellerTaxNo" placeholder="请输入卖方纳税人识别号" />
        </a-form-item>
        <a-form-item label="买方名称">
          <a-input v-model:value="formData.buyerName" placeholder="请输入买方名称" />
        </a-form-item>
        <a-form-item label="买方税号">
          <a-input v-model:value="formData.buyerTaxNo" placeholder="请输入买方纳税人识别号" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" :rows="2" placeholder="请输入备注" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped></style>
