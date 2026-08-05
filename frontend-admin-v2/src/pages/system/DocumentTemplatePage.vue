<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  V2ActionMenu,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Stack,
  showToast,
} from '@/components'
import { isApiClientError } from '@/services/request'
import {
  bindDefaultDocumentVersion,
  createDocumentTemplate,
  createDocumentVersion,
  disableDocumentVersion,
  loadDocumentTemplate,
  loadDocumentTemplates,
  publishDocumentVersion,
  updateDocumentVersion,
  type DocumentBusinessType,
  type DocumentTemplateDetail,
  type DocumentTemplateSummary,
  type DocumentTemplateVersion,
} from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

type EditorMode = 'create' | 'version' | 'edit'
type VersionAction = { kind: 'publish' | 'disable' | 'default'; version: DocumentTemplateVersion }

const session = useSessionStore()
const businessType = ref<DocumentBusinessType>('PAYMENT')
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const error = ref('')
const templates = ref<DocumentTemplateSummary[]>([])
const detail = ref<DocumentTemplateDetail | null>(null)
const selectedTemplateId = ref('')
const selectedVersionId = ref('')
const pageNo = ref(1)
const pageSize = 10
const editorOpen = ref(false)
const editorMode = ref<EditorMode>('create')
const versionAction = ref<VersionAction | null>(null)
let controller: AbortController | null = null

const form = reactive({
  templateCode: '',
  templateName: '',
  schemaVersion: '',
  templateContent: '',
  fieldManifest: '',
  remark: '',
})
const businessOptions: Array<{ value: DocumentBusinessType; label: string }> = [
  { value: 'PAYMENT', label: '付款申请单' },
  { value: 'SETTLEMENT', label: '结算单' },
  { value: 'PURCHASE_REQUEST', label: '采购申请单' },
  { value: 'PURCHASE_ORDER', label: '采购订单' },
  { value: 'MATERIAL_RECEIPT', label: '材料验收单' },
]
const canEdit = computed(() => session.hasPermission('document:template:edit'))
const canPublish = computed(() => session.hasPermission('document:template:publish'))
const selectedVersion = computed(
  () => detail.value?.versions.find((item) => item.id === selectedVersionId.value) ?? null,
)
const pagedTemplates = computed(() =>
  templates.value.slice((pageNo.value - 1) * pageSize, pageNo.value * pageSize),
)

async function refresh(preferredTemplateId?: string, preferredVersionId?: string): Promise<void> {
  pageNo.value = 1
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    templates.value = await loadDocumentTemplates(businessType.value, current.signal)
    const target =
      templates.value.find((item) => item.id === preferredTemplateId) ?? templates.value[0]
    if (target) await selectTemplate(target.id, preferredVersionId)
    else {
      detail.value = null
      selectedTemplateId.value = ''
      selectedVersionId.value = ''
    }
  } catch (value) {
    if (!current.signal.aborted) {
      templates.value = []
      detail.value = null
      error.value = messageOf(value)
    }
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshPage(): Promise<void> {
  await refresh()
  if (!error.value) showToast('success', '业务模板已刷新')
}

async function selectBusinessType(value: DocumentBusinessType): Promise<void> {
  if (value === businessType.value) return
  businessType.value = value
  templates.value = []
  detail.value = null
  selectedTemplateId.value = ''
  selectedVersionId.value = ''
  await refresh()
}

async function selectTemplate(id: string, preferredVersionId?: string): Promise<void> {
  detailLoading.value = true
  try {
    detail.value = await loadDocumentTemplate(id)
    selectedTemplateId.value = id
    selectedVersionId.value =
      detail.value.versions.find((item) => item.id === preferredVersionId)?.id ??
      detail.value.versions[0]?.id ??
      ''
  } catch (value) {
    detail.value = null
    showToast('error', '模板详情加载失败', messageOf(value))
  } finally {
    detailLoading.value = false
  }
}

function blankDraft(): void {
  const defaults: Record<DocumentBusinessType, { field: string; schema: string }> = {
    PAYMENT: { field: 'payment.applyCode', schema: 'payment.v1' },
    SETTLEMENT: { field: 'settlement.code', schema: 'settlement.v1' },
    PURCHASE_REQUEST: { field: 'purchaseRequest.requestCode', schema: 'purchase-request.v2' },
    PURCHASE_ORDER: { field: 'purchaseOrder.orderCode', schema: 'purchase-order.v1' },
    MATERIAL_RECEIPT: { field: 'receipt.receiptCode', schema: 'material-receipt.v1' },
  }
  const defaultValue = defaults[businessType.value]
  Object.assign(form, {
    templateCode: '',
    templateName: '',
    schemaVersion: defaultValue.schema,
    templateContent: `<html><body><h1>业务单据</h1><p>{{${defaultValue.field}}}</p></body></html>`,
    fieldManifest: JSON.stringify([defaultValue.field], null, 2),
    remark: '',
  })
}

function openCreate(): void {
  editorMode.value = 'create'
  blankDraft()
  editorOpen.value = true
}

function openNewVersion(): void {
  if (!detail.value) return
  editorMode.value = 'version'
  blankDraft()
  form.templateCode = detail.value.template.templateCode
  form.templateName = detail.value.template.templateName
  editorOpen.value = true
}

function openEdit(version: DocumentTemplateVersion): void {
  editorMode.value = 'edit'
  Object.assign(form, {
    templateCode: detail.value?.template.templateCode ?? '',
    templateName: detail.value?.template.templateName ?? '',
    schemaVersion: version.schemaVersion,
    templateContent: version.templateContent,
    fieldManifest: version.fieldManifest,
    remark: version.remark ?? '',
  })
  selectedVersionId.value = version.id
  editorOpen.value = true
}

async function saveDraft(): Promise<void> {
  if (
    !form.schemaVersion.trim() ||
    !form.templateContent.trim() ||
    !validManifest(form.fieldManifest)
  ) {
    showToast('warning', '模板草稿无效', '契约版本、模板内容和 JSON 字段清单必须有效。')
    return
  }
  saving.value = true
  try {
    const draft = {
      schemaVersion: form.schemaVersion.trim(),
      templateContent: form.templateContent,
      fieldManifest: form.fieldManifest,
      remark: form.remark.trim() || undefined,
    }
    let templateId = selectedTemplateId.value
    let versionId = selectedVersionId.value
    if (editorMode.value === 'create') {
      if (!form.templateCode.trim() || !form.templateName.trim()) {
        throw new Error('模板编码和名称不能为空')
      }
      const version = await createDocumentTemplate({
        ...draft,
        templateCode: form.templateCode.trim(),
        templateName: form.templateName.trim(),
        businessType: businessType.value,
      })
      templateId = version.templateId
      versionId = version.id
    } else if (editorMode.value === 'version') {
      const version = await createDocumentVersion(requiredTemplateId(), draft)
      templateId = version.templateId
      versionId = version.id
    } else {
      await updateDocumentVersion(requiredVersionId(), draft)
    }
    editorOpen.value = false
    await refresh(templateId, versionId)
    showToast('success', '模板草稿已保存', '最新版本与锁信息已载入。')
  } catch (value) {
    showToast('error', '模板保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmVersionAction(): Promise<void> {
  if (!versionAction.value || !detail.value) return
  saving.value = true
  try {
    const { kind, version } = versionAction.value
    if (kind === 'publish') await publishDocumentVersion(version.id)
    else if (kind === 'disable') await disableDocumentVersion(version.id)
    else {
      await bindDefaultDocumentVersion(
        version.id,
        detail.value.defaultBinding?.lockVersion ?? detail.value.template.defaultLockVersion ?? 0,
      )
    }
    versionAction.value = null
    await refresh(selectedTemplateId.value, version.id)
    showToast('success', '模板状态已更新', '服务端版本和默认绑定已重新读取。')
  } catch (value) {
    showToast('error', '模板状态更新失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function validManifest(value: string): boolean {
  try {
    const parsed: unknown = JSON.parse(value)
    return Array.isArray(parsed) && parsed.every((item) => typeof item === 'string')
  } catch {
    return false
  }
}

function requiredTemplateId(): string {
  if (!selectedTemplateId.value) throw new Error('请选择模板')
  return selectedTemplateId.value
}

function requiredVersionId(): string {
  if (!selectedVersionId.value) throw new Error('请选择版本')
  return selectedVersionId.value
}

function messageOf(value: unknown): string {
  return isApiClientError(value) || value instanceof Error ? value.message : '请求失败'
}

function statusTone(status: string): 'success' | 'warning' | 'neutral' {
  return status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'warning' : 'neutral'
}

function versionStatusLabel(status: DocumentTemplateVersion['status']): string {
  return { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' }[status]
}

onMounted(() => void refresh())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="document-template-page" :gap="4">
    <V2Card title="业务单据模板" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="refreshPage">刷新</V2Button>
        <V2Button v-if="canEdit" size="small" @click="openCreate">新增模板</V2Button>
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取业务模板" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="业务模板加载失败" :description="error">
      <template #actions><V2Button @click="refresh()">重试</V2Button></template>
    </V2PageState>
    <div v-else class="document-template-page__columns">
      <V2Card title="业务类型" title-id="document-business-types-title">
        <div
          class="document-template-page__business-list"
          aria-labelledby="document-business-types-title"
        >
          <button
            v-for="option in businessOptions"
            :key="option.value"
            type="button"
            class="document-template-page__business-option"
            :class="{ 'is-selected': option.value === businessType }"
            :aria-pressed="option.value === businessType"
            @click="selectBusinessType(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </V2Card>

      <V2Card title="模板">
        <V2PageState
          v-if="!templates.length"
          kind="empty"
          title="暂无模板"
          description="当前业务类型没有模板。"
        />
        <div v-else class="document-template-page__list">
          <V2Button
            v-for="item in pagedTemplates"
            :key="item.id"
            variant="ghost"
            :class="{ 'is-selected': item.id === selectedTemplateId }"
            :aria-pressed="item.id === selectedTemplateId"
            @click="selectTemplate(item.id)"
          >
            <strong>{{ item.templateName }}</strong>
            <span>{{ item.templateCode }}</span>
            <V2Badge :tone="item.enabled === 1 ? 'success' : 'neutral'">
              {{ item.enabled === 1 ? '启用' : '停用' }}
            </V2Badge>
          </V2Button>
        </div>
        <template #footer>
          <V2Pagination
            :total="templates.length"
            :page-no="pageNo"
            :page-size="pageSize"
            label="业务模板分页"
            @update:page-no="pageNo = $event"
          />
        </template>
      </V2Card>

      <V2Card title="详情">
        <template #actions>
          <V2Button v-if="canEdit && detail" size="small" @click="openNewVersion">
            新建版本
          </V2Button>
        </template>
        <V2PageState
          v-if="detailLoading"
          kind="loading"
          title="正在读取版本"
          description="请稍候。"
        />
        <V2PageState
          v-else-if="!detail"
          kind="empty"
          title="请选择模板"
          description="选择模板后查看服务端版本。"
        />
        <div v-else class="document-template-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>版本</th>
                <th>状态</th>
                <th>契约</th>
                <th>默认</th>
                <th>发布时间</th>
                <th class="v2-table-cell--actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(version, index) in detail.versions" :key="version.id">
                <th scope="row">
                  <button
                    type="button"
                    class="document-template-page__version-button"
                    :class="{ 'is-selected': version.id === selectedVersionId }"
                    :aria-pressed="version.id === selectedVersionId"
                    @click="selectedVersionId = version.id"
                  >
                    V{{ version.versionNo }}
                  </button>
                </th>
                <td>
                  <V2Badge :tone="statusTone(version.status)">
                    {{ versionStatusLabel(version.status) }}
                  </V2Badge>
                </td>
                <td>{{ version.schemaVersion }}</td>
                <td>{{ detail.defaultBinding?.templateVersionId === version.id ? '是' : '否' }}</td>
                <td>{{ version.publishedAt ?? '—' }}</td>
                <td class="v2-table-cell--actions">
                  <div class="document-template-page__actions">
                    <V2ActionMenu
                      v-if="(canEdit && version.status === 'DRAFT') || canPublish"
                      :label="`${detail.templateCode} V${version.versionNo}更多操作`"
                      :placement="index >= detail.versions.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <V2Button
                        v-if="canEdit && version.status === 'DRAFT'"
                        size="small"
                        variant="ghost"
                        @click="openEdit(version)"
                      >
                        编辑
                      </V2Button>
                      <V2Button
                        v-if="canPublish && version.status === 'DRAFT'"
                        size="small"
                        @click="versionAction = { kind: 'publish', version }"
                      >
                        发布
                      </V2Button>
                      <V2Button
                        v-if="canPublish && version.status === 'PUBLISHED'"
                        size="small"
                        variant="secondary"
                        @click="versionAction = { kind: 'default', version }"
                      >
                        设为默认
                      </V2Button>
                      <V2Button
                        v-if="canPublish && version.status === 'PUBLISHED'"
                        size="small"
                        variant="danger"
                        @click="versionAction = { kind: 'disable', version }"
                      >
                        停用
                      </V2Button>
                    </V2ActionMenu>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="selectedVersion" class="document-template-page__version-detail">
            <strong>内容哈希</strong>
            <code>{{ selectedVersion.contentHash }}</code>
            <strong>字段清单</strong>
            <pre>{{ selectedVersion.fieldManifest }}</pre>
          </div>
        </div>
      </V2Card>
    </div>

    <V2Dialog
      v-model:open="editorOpen"
      :title="
        editorMode === 'create'
          ? '新增业务模板'
          : editorMode === 'version'
            ? '新建模板版本'
            : '编辑模板草稿'
      "
      description="只保存草稿；发布和默认绑定使用独立命令。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
    >
      <div class="document-template-page__form">
        <V2Input
          v-model="form.templateCode"
          label="模板编码"
          required
          :disabled="editorMode !== 'create'"
        />
        <V2Input
          v-model="form.templateName"
          label="模板名称"
          required
          :disabled="editorMode !== 'create'"
        />
        <V2Input v-model="form.schemaVersion" label="契约版本" required />
        <V2Input v-model="form.remark" label="备注" />
        <label class="document-template-page__textarea">
          <span>HTML 模板内容</span>
          <textarea v-model="form.templateContent" rows="12" required />
        </label>
        <label class="document-template-page__textarea">
          <span>字段清单 JSON</span>
          <textarea v-model="form.fieldManifest" rows="12" required />
        </label>
      </div>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="editorOpen = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveDraft">保存草稿</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(versionAction)"
      title="确认模板状态变更"
      :description="
        versionAction
          ? `${versionAction.kind === 'publish' ? '发布' : versionAction.kind === 'disable' ? '停用' : '设为默认'} V${versionAction.version.versionNo}？`
          : ''
      "
      :confirm-text="
        versionAction?.kind === 'publish'
          ? '发布'
          : versionAction?.kind === 'disable'
            ? '停用'
            : '设为默认'
      "
      :danger="versionAction?.kind === 'disable'"
      :loading="saving"
      @close="versionAction = null"
      @confirm="confirmVersionAction"
    />
  </V2Stack>
</template>

<style scoped>
.document-template-page__columns {
  display: grid;
  grid-template-columns: minmax(12rem, 0.55fr) minmax(18rem, 0.8fr) minmax(0, 1.65fr);
  gap: var(--v2-space-4);
}

.document-template-page__columns > * {
  min-width: 0;
}

.document-template-page__business-list {
  display: grid;
  gap: var(--v2-space-2);
}

.document-template-page__business-option,
.document-template-page__version-button {
  padding: var(--v2-space-2) var(--v2-space-3);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.document-template-page__business-option.is-selected,
.document-template-page__version-button.is-selected {
  border-color: var(--v2-color-primary);
  box-shadow: inset 0 0 0 1px var(--v2-color-primary);
}

.document-template-page__list {
  display: grid;
  gap: var(--v2-space-2);
}

.document-template-page__list > .v2-button {
  display: grid;
  grid-template-columns: 1fr auto;
  text-align: left;
}

.document-template-page__list > .v2-button.is-selected {
  text-decoration: underline;
}

.document-template-page__list span {
  color: var(--v2-color-text-muted);
}

.document-template-page__table-wrap {
  overflow-x: auto;
}

.document-template-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
}

.document-template-page__version-detail {
  display: grid;
  margin-top: var(--v2-space-4);
  gap: var(--v2-space-2);
}

.document-template-page__version-detail pre {
  max-height: calc(var(--v2-space-12) * 3);
  padding: var(--v2-space-3);
  overflow: auto;
  background: var(--v2-color-surface-subtle);
  border-radius: var(--v2-radius-md);
}

.document-template-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

.document-template-page__textarea {
  display: grid;
  gap: var(--v2-space-2);
}

.document-template-page__textarea textarea {
  width: 100%;
  padding: var(--v2-space-3);
  resize: vertical;
}

@media (max-width: 1180px) {
  .document-template-page__columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .document-template-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
