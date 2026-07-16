<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { message, Modal, Upload } from 'ant-design-vue'
import axios from 'axios'
import { UploadOutlined } from '@ant-design/icons-vue'
import type { UploadFile } from 'ant-design-vue'
import {
  createInvoice,
  updateInvoice,
  recognizeInvoice,
  saveInvoiceAllocations,
  getInvoiceAllocations,
} from '@/api/modules/invoice'
import type { InvoiceVO, PayRecordBrief, InvoiceRecognizeResultVO, InvoicePaymentAllocationVO } from '@/types/invoice'
import { uploadFile } from '@/api/modules/file'

const INVOICE_BUSINESS_TYPE = 'INVOICE'
const MAX_UPLOAD_SIZE_MB = 20

const props = defineProps<{
  visible: boolean
  mode: 'create' | 'edit'
  editRecord: InvoiceVO | null
  payRecordList: PayRecordBrief[]
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const modalTitle = ref('新增发票')
const editingId = ref<string | null>(null)

const formData = reactive<Partial<InvoiceVO>>({
  payRecordId: undefined,
  documentType: 'ELECTRONIC_INVOICE',
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

const uploadFileList = ref<UploadFile[]>([])
const recognizing = ref(false)
const recognizeResult = ref<InvoiceRecognizeResultVO | null>(null)
const allocations = ref<(InvoicePaymentAllocationVO & { key: number })[]>([])
let allocationKey = 0
const abortController = ref<AbortController | null>(null)

const emptyForm: Partial<InvoiceVO> = {
  payRecordId: undefined,
  documentType: 'ELECTRONIC_INVOICE',
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
}

function resetForm() {
  Object.assign(formData, { ...emptyForm })
  allocations.value = [{ key: allocationKey++, payRecordId: '', allocatedAmount: '' }]
}

function applyRecord(record: InvoiceVO) {
  Object.assign(formData, {
    payRecordId: record.payRecordId,
    documentType: record.documentType,
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
}

watch(
  () => [props.visible, props.mode, props.editRecord] as const,
  async ([vis, mode, record]) => {
    if (!vis) return
    if (mode === 'create') {
      modalTitle.value = '新增发票'
      editingId.value = null
      resetForm()
    } else if (mode === 'edit' && record) {
      modalTitle.value = '编辑发票'
      editingId.value = record.id
      applyRecord(record)
      const saved = await getInvoiceAllocations(record.id)
      allocations.value = saved.map((item) => ({ ...item, key: allocationKey++ }))
    }
    if (abortController.value) {
      abortController.value.abort()
      abortController.value = null
    }
    uploadFileList.value = []
    recognizeResult.value = null
  },
  { immediate: true },
)

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
  if (!formData.invoiceDate) {
    message.warning('请选择开票日期')
    return
  }
  if (!editingId.value && uploadFileList.value.length === 0) {
    message.warning('新建发票必须上传电子发票或扫描件')
    return
  }
  const activeAllocations = allocations.value.filter(
    (item) => item.payRecordId && Number(item.allocatedAmount) > 0,
  )
  if (!activeAllocations.length || activeAllocations.length !== allocations.value.length) {
    message.warning('请完整填写每一条付款核销分配')
    return
  }
  if (new Set(activeAllocations.map((item) => item.payRecordId)).size !== activeAllocations.length) {
    message.warning('同一付款记录不能重复分配')
    return
  }
  const allocatedTotal = activeAllocations.reduce((sum, item) => sum + Number(item.allocatedAmount), 0)
  if (allocatedTotal > Number(formData.invoiceAmount) + 0.001) {
    message.warning('核销分配合计不能超过发票金额')
    return
  }
  formData.payRecordId = activeAllocations[0].payRecordId

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
    if (invoiceId) {
      await saveInvoiceAllocations(invoiceId, activeAllocations.map(({ payRecordId, allocatedAmount }) => ({
        payRecordId,
        allocatedAmount,
      })))
    }
    if (invoiceId && recognizeResult.value) {
      const { createInvoiceOcrReview } = await import('@/api/modules/financeOperations')
      await createInvoiceOcrReview({
        invoiceId,
        confidence: Number(recognizeResult.value.confidence ?? 0),
        rawResult: recognizeResult.value,
        comparison: {
          invoiceNo: formData.invoiceNo,
          invoiceAmount: formData.invoiceAmount,
          invoiceDate: formData.invoiceDate,
          sellerTaxNo: formData.sellerTaxNo,
        },
      })
    }

    // Upload attachment if a file was selected
    if (uploadFileList.value.length > 0 && invoiceId) {
      const file = uploadFileList.value[0].originFileObj as File
      try {
        await uploadFile(file, INVOICE_BUSINESS_TYPE, invoiceId, formData.documentType)
      } catch (e: unknown) {
        console.error(e)
        message.warning('发票已创建，但文件上传失败。请稍后在发票详情中重新上传。')
      }
    }
    emit('close')
    emit('saved')
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
  emit('close')
}

function handleBeforeUpload(file: File) {
  const isPdf = file.type === 'application/pdf' || file.name.endsWith('.pdf')
  if (!isPdf) {
    message.error('仅支持PDF格式')
    return Upload.LIST_IGNORE
  }
  const isWithinLimit = file.size / 1024 / 1024 <= MAX_UPLOAD_SIZE_MB
  if (!isWithinLimit) {
    message.error(`文件大小不能超过${MAX_UPLOAD_SIZE_MB}MB`)
    return Upload.LIST_IGNORE
  }
  return false // prevent auto-upload; manual upload handled in handleRecognize()
}

function addAllocation() {
  allocations.value.push({ key: allocationKey++, payRecordId: '', allocatedAmount: '' })
}

function removeAllocation(key: number) {
  if (allocations.value.length === 1) return
  allocations.value = allocations.value.filter((item) => item.key !== key)
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
      ;(formData as Record<string, unknown>)[field] = 'VAT_SPECIAL'
    } else if (field === 'invoiceNo' || field === 'remark') {
      ;(formData as Record<string, unknown>)[field] = ''
    } else {
      ;(formData as Record<string, unknown>)[field] = undefined
    }
  }
}

function applyRecognitionResult(result: InvoiceRecognizeResultVO) {
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
      ;(formData as Record<string, unknown>)[field] = value
    }
  }
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
      } else {
        const msg = (e.response?.data as { message?: string } | undefined)?.message
        message.error(msg || '发票识别失败，请检查文件后重试')
      }
    } else {
      message.error('发票识别失败，请检查文件后重试')
    }
  } finally {
    recognizing.value = false
    abortController.value = null
  }
}

defineExpose({
  formData,
  uploadFileList,
  recognizeResult,
  allocations,
  applyRecognitionResult,
  handleBeforeUpload,
  handleAdd: () => {
    // exposed for tests - reset form and prepare for create mode
    resetForm()
    if (abortController.value) {
      abortController.value.abort()
      abortController.value = null
    }
    uploadFileList.value = []
    recognizeResult.value = null
  },
})
</script>

<template>
  <a-modal
    :open="visible"
    :title="modalTitle"
    :width="800"
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
      <a-form-item label="付款核销分配" required>
        <div class="allocation-list">
          <div v-for="item in allocations" :key="item.key" class="allocation-row">
            <a-select v-model:value="item.payRecordId" placeholder="付款记录">
              <a-select-option v-for="pr in payRecordList" :key="pr.id" :value="pr.id">
                {{ pr.voucherNo ? `#${pr.voucherNo}` : `付款记录#${pr.id}` }}（{{ pr.payAmount ?? '-' }}）
              </a-select-option>
            </a-select>
            <a-input-number v-model:value="item.allocatedAmount" :min="0.01" :precision="2" placeholder="分配金额" />
            <a-button danger :disabled="allocations.length === 1" @click="removeAllocation(item.key)">删除</a-button>
          </div>
          <a-button type="dashed" block @click="addAllocation">增加付款分配（一票多付）</a-button>
          <div class="allocation-tip">支持一张发票分配多笔付款；多张发票也可分别核销同一付款，后台同时校验发票与付款两侧额度。</div>
        </div>
      </a-form-item>
      <a-form-item label="文档类型" required>
        <a-select v-model:value="formData.documentType">
          <a-select-option value="ELECTRONIC_INVOICE">电子发票</a-select-option>
          <a-select-option value="SCANNED_INVOICE">扫描件</a-select-option>
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
      <a-form-item label="开票日期" required>
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
</template>

<style scoped>
.allocation-list{display:grid;gap:8px}.allocation-row{display:grid;grid-template-columns:minmax(0,1fr) 150px auto;gap:8px}.allocation-tip{color:#667085;font-size:12px;line-height:20px}
</style>
