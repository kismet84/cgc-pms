<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal, Upload } from 'ant-design-vue'
import axios from 'axios'
import { UploadOutlined } from '@ant-design/icons-vue'
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
  payRecordId: undefined as string | undefined,
  invoiceNo: '' as string,
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

const columns = [
  { title: '发票号码', dataIndex: 'invoiceNo', width: 160 },
  { title: '发票类型', dataIndex: 'invoiceType', width: 120, key: 'invoiceType' },
  { title: '发票金额', dataIndex: 'invoiceAmount', width: 130, key: 'invoiceAmount' },
  { title: '税率(%)', dataIndex: 'taxRate', width: 90, key: 'taxRate' },
  { title: '税额', dataIndex: 'taxAmount', width: 130, key: 'taxAmount' },
  { title: '开票日期', dataIndex: 'invoiceDate', width: 120 },
  { title: '核验状态', dataIndex: 'verifyStatus', width: 100, key: 'verifyStatus' },
  { title: '备注', dataIndex: 'remark', width: 140, ellipsis: true },
  { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  { title: '操作', key: 'action', width: 240, fixed: 'right' },
]

async function fetchData() {
  loading.value = true
  try {
    const params = {
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      payRecordId: filter.payRecordId,
      invoiceNo: filter.invoiceNo || undefined,
      verifyStatus: filter.verifyStatus,
    }
    console.log('[fetchData] calling getInvoiceList with:', params)
    const res = await getInvoiceList(params)
    console.log('[fetchData] response:', res)
    tableData.value = res.records
    total.value = res.total
  } catch (err) {
    console.error('[fetchData] error:', err)
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
  } catch {
    payRecordList.value = []
  }
}

function handleSearch() {
  console.log('[handleSearch] clicked, filter:', JSON.parse(JSON.stringify(filter)))
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.payRecordId = undefined
  filter.invoiceNo = ''
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
      } catch {
        message.error('删除失败，请稍后重试')
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
      } catch {
        message.error('核验操作失败，请稍后重试')
      }
    },
    onCancel: async () => {
      try {
        await verifyInvoice(record.id, 'ABNORMAL')
        message.warning('发票已标记为异常')
        fetchData()
      } catch {
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
      } catch {
        message.warning('发票已创建，但文件上传失败。请稍后在发票详情中重新上传。')
      }
    }
  } catch (error: any) {
    const msg = error?.response?.data?.message || error?.message || ''
    if (msg.includes('已存在') || msg.includes('duplicate')) {
      message.error('发票号码已存在，同一租户下发票号码不可重复')
    } else {
      message.error('操作失败，请稍后重试')
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
  } catch (error: any) {
    if (axios.isCancel(error)) {
      return
    }
    if (error?.code === 'ECONNABORTED' || error?.message?.includes('timeout')) {
      message.error('识别超时，请检查网络后重试')
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

function getPayRecordLabel(record: InvoiceVO): string {
  if (!record.payRecordId) return '-'
  const pr = payRecordList.value.find((r) => r.id === record.payRecordId)
  if (pr) {
    return pr.voucherNo ? `#${pr.voucherNo}` : `付款记录#${pr.id}`
  }
  return record.payRecordId
}

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
  <div class="pm-page">
    <a-page-header title="发票管理" class="pm-header" />

    <!-- Filter -->
    <div class="pm-card pm-filter">
      <div class="pm-filter-row">
        <div class="pm-field">
          <label>关联付款记录：</label>
          <a-select
            v-model:value="filter.payRecordId"
            placeholder="全部"
            allow-clear
            style="width: 200px"
          >
            <a-select-option v-for="pr in payRecordList" :key="pr.id" :value="pr.id">
              {{ pr.voucherNo ? `#${pr.voucherNo}` : `付款记录#${pr.id}` }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>发票号码：</label>
          <a-input
            v-model:value="filter.invoiceNo"
            placeholder="输入发票号码"
            allow-clear
            style="width: 180px"
          />
        </div>
        <div class="pm-field">
          <label>核验状态：</label>
          <a-select
            v-model:value="filter.verifyStatus"
            placeholder="全部"
            allow-clear
            style="width: 110px"
          >
            <a-select-option value="PENDING">待核验</a-select-option>
            <a-select-option value="VERIFIED">已认证</a-select-option>
            <a-select-option value="ABNORMAL">异常</a-select-option>
          </a-select>
        </div>
        <div class="pm-filter-actions">
          <span @click="handleSearch"><a-button type="primary">查询</a-button></span>
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" @click="handleAdd">新增发票</a-button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="pm-card pm-table-wrap">
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ x: 1400 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'invoiceType'">
            <a-tag
              :color="
                INVOICE_TYPE_COLOR[record.invoiceType as keyof typeof INVOICE_TYPE_COLOR] ||
                'default'
              "
            >
              {{
                INVOICE_TYPE_LABEL[record.invoiceType as keyof typeof INVOICE_TYPE_LABEL] ||
                record.invoiceType
              }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'invoiceAmount'">
            <span>{{ fmtAmount(record.invoiceAmount) }}</span>
          </template>
          <template v-else-if="column.key === 'taxRate'">
            <span>{{ record.taxRate ? record.taxRate + '%' : '-' }}</span>
          </template>
          <template v-else-if="column.key === 'taxAmount'">
            <span>{{ fmtAmount(record.taxAmount) }}</span>
          </template>
          <template v-else-if="column.key === 'verifyStatus'">
            <a-tag
              :color="
                VERIFY_STATUS_COLOR[record.verifyStatus as keyof typeof VERIFY_STATUS_COLOR] ||
                'default'
              "
            >
              {{
                VERIFY_STATUS_LABEL[record.verifyStatus as keyof typeof VERIFY_STATUS_LABEL] ||
                record.verifyStatus
              }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
            <a-button type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
            <a-button
              v-if="record.verifyStatus === 'PENDING'"
              type="link"
              size="small"
              @click="handleVerify(record)"
              >核验</a-button
            >
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="pm-pagination">
      <span class="pm-total">共 {{ total }} 条</span>
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
          <a-date-picker v-model:value="formData.invoiceDate" value-format="YYYY-MM-DD" style="width: 100%" />
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

<style scoped>
.pm-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.pm-header {
  background: transparent;
  padding-bottom: 12px;
}
.pm-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.pm-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.pm-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
}
.pm-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.pm-field label {
  color: #374151;
}
.pm-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}
.pm-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
}
.pm-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.pm-total {
  font-size: 13px;
  color: #4b5563;
}
</style>
