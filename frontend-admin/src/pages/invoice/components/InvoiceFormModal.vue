<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { message, Modal, Upload } from 'ant-design-vue'
import axios from 'axios'
import { UploadOutlined } from '@ant-design/icons-vue'
import type { UploadFile } from 'ant-design-vue'
import { createInvoice, updateInvoice, recognizeInvoice } from '@/api/modules/invoice'
import type { InvoiceVO, PayRecordBrief, InvoiceRecognizeResultVO } from '@/types/invoice'
import { uploadFile } from '@/api/modules/file'

const INVOICE_BUSINESS_TYPE = 'INVOICE_ATTACHMENT'

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
const abortController = ref<AbortController | null>(null)

const emptyForm: Partial<InvoiceVO> = {
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
}

function resetForm() {
  Object.assign(formData, { ...emptyForm })
}

function applyRecord(record: InvoiceVO) {
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
}

watch(
  () => [props.visible, props.mode, props.editRecord] as const,
  ([vis, mode, record]) => {
    if (!vis) return
    if (mode === 'create') {
      modalTitle.value = '新增发票'
      editingId.value = null
      resetForm()
    } else if (mode === 'edit' && record) {
      modalTitle.value = '编辑发票'
      editingId.value = record.id
      applyRecord(record)
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
  if (!formData.payRecordId) {
    message.warning('请选择关联的付款记录')
    return
  }
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
    emit('close')
    emit('saved')

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
  emit('close')
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
      }
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
      <a-form-item label="付款记录" required>
        <a-select v-model:value="formData.payRecordId" placeholder="请选择关联的付款记录">
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
</template>

<style scoped></style>
