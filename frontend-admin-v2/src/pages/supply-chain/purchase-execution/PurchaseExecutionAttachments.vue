<script setup lang="ts">
import type { SiteFileRecord } from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { V2Badge, V2Button, V2ConfirmDialog, showToast } from '@/components'
import { deleteSiteFile, getSiteFileUrl, listSiteFiles, uploadSiteFile } from '@/services/delivery'
import {
  downloadDocumentGeneration,
  generateDocument,
  loadDocumentGenerationHistory,
  previewDocument,
  type DocumentGenerationRecord,
} from '@/services/system-management'
import { errorText, statusLabel, type PurchaseExecutionMode } from './model'

const props = defineProps<{
  mode: PurchaseExecutionMode
  businessId: string
  businessCode: string
  approvalStatus?: string | null
  canEdit: boolean
}>()

const businessType = computed(
  () =>
    ({
      request: 'PURCHASE_REQUEST',
      order: 'PURCHASE_ORDER',
      receipt: 'MATERIAL_RECEIPT',
    })[props.mode],
)
const attachmentDocumentType = computed(() =>
  props.mode === 'receipt' ? 'MATERIAL_ACCEPTANCE_FORM' : 'DELIVERY_NOTE',
)
const attachments = ref<SiteFileRecord[]>([])
const documentHistory = ref<DocumentGenerationRecord[]>([])
const busy = ref(false)
const attachmentToDelete = ref<SiteFileRecord | null>(null)
let controller: AbortController | null = null
let generation = 0

const businessAttachments = computed(() =>
  attachments.value.filter(
    (file) =>
      file.documentType !== 'GENERATED_DOCUMENT' && !file.originalName.endsWith('-业务说明.txt'),
  ),
)
const currentDocument = computed(() => documentHistory.value[0] ?? null)
const isDraft = computed(() => props.approvalStatus === 'DRAFT')
const canDelete = computed(() => props.canEdit && isDraft.value)
const showAttachmentSection = computed(
  () => businessAttachments.value.length > 0 || props.mode === 'receipt' || isDraft.value,
)

function attachmentScanStatus(documentType: 'DELIVERY_NOTE' | 'MATERIAL_ACCEPTANCE_FORM'): string {
  const file = attachments.value.find((item) => item.documentType === documentType)
  if (!file) return '缺失'
  if (file.virusScanPassed === true) return '扫描通过'
  return file.virusScanStatus || '等待服务端扫描'
}

async function load(): Promise<void> {
  controller?.abort()
  const requestController = new AbortController()
  controller = requestController
  const currentGeneration = ++generation
  const [files, history] = await Promise.allSettled([
    listSiteFiles(businessType.value, props.businessId, requestController.signal),
    loadDocumentGenerationHistory(businessType.value, props.businessId),
  ])
  if (requestController.signal.aborted || currentGeneration !== generation) return
  attachments.value = files.status === 'fulfilled' ? files.value : []
  documentHistory.value = history.status === 'fulfilled' ? history.value.records : []
  if (files.status === 'rejected') {
    showToast('warning', '附件读取失败', errorText(files.reason, '附件列表加载失败'))
  }
  if (history.status === 'rejected') {
    showToast('warning', '单据历史读取失败', errorText(history.reason, '无法读取单据生成历史'))
  }
}

async function upload(event: Event, documentType = attachmentDocumentType.value): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || busy.value) return
  busy.value = true
  try {
    await uploadSiteFile(file, businessType.value, props.businessId, documentType)
    attachments.value = await listSiteFiles(businessType.value, props.businessId)
    showToast('success', '附件上传成功', `${file.name} 已通过安全检查，附件列表已更新`)
  } catch (error) {
    showToast('error', '附件上传失败', errorText(error, '附件上传失败'))
  } finally {
    busy.value = false
    input.value = ''
  }
}

async function download(file: SiteFileRecord): Promise<void> {
  try {
    window.open(await getSiteFileUrl(file.id), '_blank', 'noopener,noreferrer')
  } catch (error) {
    showToast('error', '附件下载失败', errorText(error, '下载链接获取失败'))
  }
}

async function confirmDelete(): Promise<void> {
  const file = attachmentToDelete.value
  if (!file || busy.value) return
  busy.value = true
  try {
    await deleteSiteFile(file.id)
    attachments.value = await listSiteFiles(businessType.value, props.businessId)
    showToast('success', '附件已删除', '附件列表已更新')
  } catch (error) {
    showToast('error', '附件删除失败', errorText(error, '附件删除失败'))
  } finally {
    busy.value = false
    attachmentToDelete.value = null
  }
}

async function preview(): Promise<void> {
  if (!['order', 'receipt'].includes(props.mode) || busy.value) return
  busy.value = true
  try {
    const blob = await previewDocument(
      props.mode === 'order' ? 'PURCHASE_ORDER' : 'MATERIAL_RECEIPT',
      props.businessId,
    )
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${props.businessCode}-${props.mode === 'order' ? '草稿水印预览' : '验收单预览'}.pdf`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    const isOrder = props.mode === 'order'
    showToast(
      'error',
      isOrder ? '订单预览失败' : '验收单预览失败',
      errorText(error, isOrder ? '无法生成订单预览' : '无法生成验收单预览'),
    )
  } finally {
    busy.value = false
  }
}

async function generateReceiptDocument(): Promise<void> {
  if (props.mode !== 'receipt' || busy.value) return
  busy.value = true
  try {
    await generateDocument({
      businessType: 'MATERIAL_RECEIPT',
      businessId: props.businessId,
      idempotencyKey: crypto.randomUUID(),
    })
    const history = await loadDocumentGenerationHistory(businessType.value, props.businessId)
    documentHistory.value = history.records
    showToast('success', '验收单生成已提交', '请刷新单据历史查看结果')
  } catch (error) {
    showToast('error', '验收单生成失败', errorText(error, '无法生成验收单'))
  } finally {
    busy.value = false
  }
}

async function downloadGenerated(generationRecord: DocumentGenerationRecord): Promise<void> {
  window.open(
    await downloadDocumentGeneration(generationRecord.id),
    '_blank',
    'noopener,noreferrer',
  )
}

async function printPurchaseOrderDocument(): Promise<void> {
  const generationRecord = currentDocument.value
  if (!generationRecord || generationRecord.status !== 'SUCCEEDED') {
    showToast('warning', '暂无正式 PDF', '审批通过后由后台自动生成')
    return
  }
  await downloadGenerated(generationRecord)
}

watch(
  () => [props.mode, props.businessId] as const,
  () => void load(),
  { immediate: true },
)

onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section
    v-if="showAttachmentSection"
    class="v2-detail-dialog__section purchase-execution-page__attachments"
    aria-labelledby="purchase-attachment-title"
  >
    <div class="v2-detail-dialog__section-heading">
      <h3 id="purchase-attachment-title">业务附件</h3>
      <V2Badge tone="info">{{ businessAttachments.length }} 个</V2Badge>
    </div>
    <ul v-if="businessAttachments.length">
      <li v-for="file in businessAttachments" :key="file.id">
        <V2Button type="button" size="small" variant="ghost" @click="download(file)">
          {{ file.originalName }}
        </V2Button>
        <V2Button
          v-if="canDelete"
          type="button"
          size="small"
          variant="ghost"
          @click="attachmentToDelete = file"
          >删除</V2Button
        >
      </li>
    </ul>
    <p v-else class="purchase-execution-page__attachment-empty">暂无附件</p>

    <template v-if="mode === 'receipt'">
      <p>送货单：{{ attachmentScanStatus('DELIVERY_NOTE') }}</p>
      <p>验收单：{{ attachmentScanStatus('MATERIAL_ACCEPTANCE_FORM') }}</p>
      <label class="purchase-execution-page__attachment">
        <span>上传送货单</span>
        <input type="file" :disabled="busy" @change="upload($event, 'DELIVERY_NOTE')" />
      </label>
      <label class="purchase-execution-page__attachment">
        <span>上传材料验收单</span>
        <input type="file" :disabled="busy" @change="upload($event, 'MATERIAL_ACCEPTANCE_FORM')" />
      </label>
    </template>
    <label v-else-if="isDraft" class="purchase-execution-page__attachment">
      <span>选择已签认的业务附件</span>
      <input
        type="file"
        :disabled="busy"
        accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.txt"
        @change="upload"
      />
    </label>
  </section>

  <div v-if="mode !== 'request'" class="purchase-execution-page__attachment-actions">
    <V2Button
      v-if="
        ['order', 'receipt'].includes(mode) && ['DRAFT', 'REJECTED'].includes(approvalStatus || '')
      "
      type="button"
      size="small"
      variant="secondary"
      :disabled="busy"
      @click="preview"
      >{{ mode === 'order' ? '预览草稿水印订单' : '预览验收单' }}</V2Button
    >
    <V2Button
      v-if="mode === 'receipt' && approvalStatus === 'APPROVED'"
      type="button"
      size="small"
      variant="secondary"
      :disabled="busy"
      @click="generateReceiptDocument"
      >生成验收单</V2Button
    >
    <V2Button
      v-if="mode === 'order' && approvalStatus === 'APPROVED'"
      type="button"
      size="small"
      variant="ghost"
      :disabled="busy"
      @click="printPurchaseOrderDocument"
      >打印正式 PDF</V2Button
    >
  </div>

  <section
    v-if="currentDocument"
    class="v2-detail-dialog__section"
    aria-labelledby="purchase-current-document-title"
  >
    <div class="v2-detail-dialog__section-heading">
      <h3 id="purchase-current-document-title">正式单据</h3>
      <V2Badge :tone="currentDocument.status === 'SUCCEEDED' ? 'success' : 'warning'">
        {{ statusLabel(currentDocument.status) }}
      </V2Badge>
    </div>
    <p>{{ businessCode }}.pdf</p>
    <V2Button
      v-if="currentDocument.status === 'SUCCEEDED'"
      type="button"
      size="small"
      variant="ghost"
      @click="downloadGenerated(currentDocument)"
      >下载</V2Button
    >
    <p v-else>审批完成后由后台自动生成，当前状态无需人工操作。</p>
  </section>

  <V2ConfirmDialog
    :open="Boolean(attachmentToDelete)"
    title="删除附件"
    :description="attachmentToDelete ? `确认删除附件“${attachmentToDelete.originalName}”？` : ''"
    confirm-text="确认删除"
    danger
    :loading="busy"
    @close="attachmentToDelete = null"
    @confirm="confirmDelete"
  />
</template>
