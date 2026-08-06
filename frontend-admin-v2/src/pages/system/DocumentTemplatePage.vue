<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
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
import DocumentCanvas from '@/components/document/DocumentCanvas.vue'
import {
  bindDefaultDocumentVersion,
  createDocumentTemplate,
  createDocumentVersion,
  disableDocumentVersion,
  loadDocumentBusinessTypes,
  loadDocumentFieldCatalog,
  loadDocumentTemplate,
  loadDocumentTemplates,
  publishDocumentVersion,
  previewDocumentTemplateHtml,
  updateDocumentVersion,
  type DocumentBusinessType,
  type DocumentBusinessTypeOption,
  type DocumentDesignSchema,
  type DocumentFieldCatalog,
  type DocumentDraft,
  type DocumentTemplateDetail,
  type DocumentTemplateSummary,
  type DocumentTemplateVersion,
} from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

type EditorMode = 'create' | 'version' | 'edit'
type VersionAction = { kind: 'publish' | 'disable' | 'default'; version: DocumentTemplateVersion }

const session = useSessionStore()
const businessType = ref<DocumentBusinessType>('')
const businessTypes = ref<DocumentBusinessTypeOption[]>([])
const catalog = ref<DocumentFieldCatalog | null>(null)
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
const canvasValid = ref(false)
const previewHtml = ref('')
const previewError = ref('')
const previewLoading = ref(false)
const versionAction = ref<VersionAction | null>(null)
let controller: AbortController | null = null
let previewTimer: ReturnType<typeof setTimeout> | undefined

const form = reactive({
  templateCode: '',
  templateName: '',
  schemaVersion: '',
  templateContent: '',
  fieldManifest: '',
  designSchema: blankDesign(''),
  previewBusinessId: '',
  legacy: false,
  remark: '',
})
const businessOptions = computed(() =>
  businessTypes.value.map((item) => ({
    value: item.businessType,
    label: item.displayName,
    ready: item.providerReady,
  })),
)
const selectedBusiness = computed(() =>
  businessTypes.value.find((item) => item.businessType === businessType.value),
)
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
    if (!businessTypes.value.length)
      businessTypes.value = await loadDocumentBusinessTypes(current.signal)
    if (
      !businessType.value ||
      !businessTypes.value.some((item) => item.businessType === businessType.value)
    ) {
      businessType.value = businessTypes.value[0]?.businessType ?? ''
    }
    if (!businessType.value) throw new Error('服务端未返回审批业务类型')
    const currentBusiness = businessTypes.value.find(
      (item) => item.businessType === businessType.value,
    )
    if (currentBusiness?.providerReady) {
      ;[templates.value, catalog.value] = await Promise.all([
        loadDocumentTemplates(businessType.value, current.signal),
        loadDocumentFieldCatalog(businessType.value, current.signal),
      ])
    } else {
      templates.value = await loadDocumentTemplates(businessType.value, current.signal)
      catalog.value = null
    }
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
  const schemaVersion = catalog.value?.schemaVersion ?? selectedBusiness.value?.schemaVersion ?? ''
  Object.assign(form, {
    templateCode: '',
    templateName: '',
    schemaVersion,
    templateContent: '',
    fieldManifest: '',
    designSchema: blankDesign(schemaVersion),
    previewBusinessId: '',
    legacy: false,
    remark: '',
  })
}

function openCreate(): void {
  if (!selectedBusiness.value?.providerReady) return
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
  const source = selectedVersion.value
  if (source) applyVersion(source)
  editorOpen.value = true
}

function openEdit(version: DocumentTemplateVersion): void {
  editorMode.value = 'edit'
  Object.assign(form, {
    templateCode: detail.value?.template.templateCode ?? '',
    templateName: detail.value?.template.templateName ?? '',
    remark: version.remark ?? '',
  })
  applyVersion(version)
  selectedVersionId.value = version.id
  editorOpen.value = true
}

async function saveDraft(): Promise<void> {
  if (
    !form.schemaVersion.trim() ||
    (!form.legacy && !canvasValid.value) ||
    (form.legacy && (!form.templateContent.trim() || !validManifest(form.fieldManifest)))
  ) {
    showToast(
      'warning',
      '模板草稿无效',
      form.legacy ? '契约版本、模板内容和字段清单必须有效。' : '请修复越出页面安全区域的元素。',
    )
    return
  }
  saving.value = true
  try {
    const draft: DocumentDraft = {
      schemaVersion: form.schemaVersion.trim(),
      remark: form.remark.trim() || undefined,
    }
    if (form.legacy) {
      draft.templateContent = form.templateContent
      draft.fieldManifest = form.fieldManifest
    } else
      draft.designSchema = JSON.stringify({
        ...form.designSchema,
        schemaVersion: form.schemaVersion.trim(),
      })
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

function blankDesign(schemaVersion: string): DocumentDesignSchema {
  return {
    schemaVersion,
    page: {
      size: 'A4',
      orientation: 'PORTRAIT',
      marginMm: { top: 12, right: 12, bottom: 12, left: 12 },
    },
    elements: [],
    tables: [],
  }
}

function applyVersion(version: DocumentTemplateVersion): void {
  form.schemaVersion = version.schemaVersion
  form.templateContent = version.templateContent
  form.fieldManifest = version.fieldManifest
  form.legacy = !version.designSchema
  if (!version.designSchema) return
  try {
    form.designSchema = JSON.parse(version.designSchema) as DocumentDesignSchema
  } catch {
    form.legacy = true
    showToast('warning', '画布模型无法读取', '已切换到旧源码兼容入口。')
  }
}

async function refreshPreview(): Promise<void> {
  if (!editorOpen.value || form.legacy || !businessType.value || !canvasValid.value) {
    previewHtml.value = ''
    previewError.value = ''
    previewLoading.value = false
    return
  }
  previewLoading.value = true
  previewError.value = ''
  try {
    previewHtml.value = (
      await previewDocumentTemplateHtml({
        businessType: businessType.value,
        designSchema: JSON.stringify(form.designSchema),
        businessId: form.previewBusinessId.trim() || undefined,
      })
    ).html
  } catch (value) {
    previewHtml.value = ''
    previewError.value = messageOf(value)
  } finally {
    previewLoading.value = false
  }
}

watch(
  () => [
    editorOpen.value,
    businessType.value,
    JSON.stringify(form.designSchema),
    form.previewBusinessId,
    form.legacy,
    canvasValid.value,
  ],
  () => {
    clearTimeout(previewTimer)
    previewTimer = setTimeout(() => void refreshPreview(), 250)
  },
)

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
onBeforeUnmount(() => {
  controller?.abort()
  clearTimeout(previewTimer)
})
</script>

<template>
  <V2Stack class="document-template-page" :gap="4">
    <V2Card title="业务单据模板" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="refreshPage">刷新</V2Button>
        <V2Button
          v-if="canEdit"
          size="small"
          :disabled="!selectedBusiness?.providerReady"
          @click="openCreate"
          >新增模板</V2Button
        >
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取业务模板" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="业务模板加载失败" :description="error">
      <template #actions><V2Button @click="refresh()">重试</V2Button></template>
    </V2PageState>
    <div v-else class="document-template-page__columns">
      <V2Card title="业务模块" title-id="document-business-types-title">
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
            <span>{{ option.label }}</span>
            <small>{{ option.ready ? '字段契约就绪' : '完整详情契约待配置' }}</small>
          </button>
        </div>
      </V2Card>

      <V2Card title="模板">
        <template #actions>
          <V2Button v-if="canEdit && detail" size="small" @click="openNewVersion">
            新建版本
          </V2Button>
        </template>
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
        <section v-if="detail" class="document-template-page__versions" aria-label="模板版本">
          <h3>版本</h3>
          <div
            v-for="(version, index) in detail.versions"
            :key="version.id"
            class="document-template-page__version-row"
            :class="{ 'is-selected': version.id === selectedVersionId }"
          >
            <button
              type="button"
              class="document-template-page__version-button"
              :aria-pressed="version.id === selectedVersionId"
              @click="selectedVersionId = version.id"
            >
              <span>
                <strong>V{{ version.versionNo }}</strong>
                <V2Badge :tone="statusTone(version.status)">
                  {{ versionStatusLabel(version.status) }}
                </V2Badge>
              </span>
              <small>
                {{ version.schemaVersion }} ·
                {{
                  detail.defaultBinding?.templateVersionId === version.id ? '默认版本' : '普通版本'
                }}
              </small>
            </button>
            <V2ActionMenu
              v-if="(canEdit && version.status === 'DRAFT') || canPublish"
              :label="`${detail.template.templateCode} V${version.versionNo}更多操作`"
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
        </section>
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

      <V2Card title="HTML预览">
        <V2PageState
          v-if="detailLoading"
          kind="loading"
          title="正在读取预览"
          description="请稍候。"
        />
        <V2PageState
          v-else-if="!selectedVersion"
          kind="empty"
          title="请选择模板版本"
          description="选择模板和版本后查看服务端HTML。"
        />
        <div v-else class="document-template-page__html-preview">
          <div class="document-template-page__preview-meta">
            <strong>V{{ selectedVersion.versionNo }}</strong>
            <V2Badge :tone="statusTone(selectedVersion.status)">
              {{ versionStatusLabel(selectedVersion.status) }}
            </V2Badge>
            <span>{{ selectedVersion.schemaVersion }}</span>
          </div>
          <iframe
            title="选中模板版本 HTML 预览"
            sandbox=""
            :srcdoc="selectedVersion.templateContent"
          ></iframe>
          <details class="document-template-page__version-detail">
            <summary>版本信息</summary>
            <strong>内容哈希</strong>
            <code>{{ selectedVersion.contentHash }}</code>
            <strong>字段清单</strong>
            <pre>{{ selectedVersion.fieldManifest }}</pre>
          </details>
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
      description="可视化画布保存后由服务端生成 HTML 和字段清单；发布与默认绑定仍使用独立命令。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard v2-dialog-wide"
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
        <V2Input v-model="form.schemaVersion" label="契约版本" required :disabled="!form.legacy" />
        <V2Input v-model="form.remark" label="备注" />
        <label v-if="form.legacy" class="document-template-page__textarea">
          <span>HTML 模板内容</span>
          <textarea v-model="form.templateContent" rows="12" required />
        </label>
        <div v-if="form.legacy" class="document-template-page__textarea">
          <span>字段清单（服务端只读）</span>
          <pre>{{ form.fieldManifest }}</pre>
        </div>
      </div>
      <template v-if="!form.legacy">
        <DocumentCanvas
          v-model="form.designSchema"
          :fields="catalog?.fields ?? []"
          :disabled="saving"
          :preview-html="previewHtml"
          :preview-loading="previewLoading"
          :preview-error="previewError"
          :preview-business-id="form.previewBusinessId"
          @update:valid="canvasValid = $event"
          @update:preview-business-id="form.previewBusinessId = $event"
        />
      </template>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="editorOpen = false">取消</V2Button>
        <V2Button :loading="saving" :disabled="!form.legacy && !canvasValid" @click="saveDraft"
          >保存草稿</V2Button
        >
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
  grid-template-columns: minmax(13rem, 0.6fr) minmax(19rem, 0.9fr) minmax(24rem, 1.5fr);
  gap: var(--v2-space-4);
}

.document-template-page__columns > * {
  min-width: 0;
}

.document-template-page__business-list {
  display: grid;
  gap: var(--v2-space-2);
}

.document-template-page__business-option {
  padding: var(--v2-space-2) var(--v2-space-3);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.document-template-page__business-option small {
  display: block;
  margin-top: var(--v2-space-1);
  color: var(--v2-color-text-muted);
}

.document-template-page__business-option.is-selected,
.document-template-page__version-row.is-selected {
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

.document-template-page__versions {
  display: grid;
  gap: var(--v2-space-2);
  margin-top: var(--v2-space-4);
  padding-top: var(--v2-space-3);
  border-top: 1px solid var(--v2-color-border);
}

.document-template-page__versions h3 {
  margin: 0;
  font-size: var(--v2-font-size-14);
}

.document-template-page__version-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--v2-space-2);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.document-template-page__version-button {
  display: grid;
  gap: var(--v2-space-1);
  min-width: 0;
  padding: var(--v2-space-2) var(--v2-space-3);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.document-template-page__version-button > span {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
}

.document-template-page__version-button small,
.document-template-page__preview-meta span {
  color: var(--v2-color-text-muted);
}

.document-template-page__html-preview {
  display: grid;
  gap: var(--v2-space-3);
}

.document-template-page__preview-meta {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
}

.document-template-page__html-preview iframe {
  width: 100%;
  min-height: 42rem;
  background: white;
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.document-template-page__version-detail {
  display: grid;
  gap: var(--v2-space-2);
}

.document-template-page__version-detail summary {
  cursor: pointer;
  font-weight: var(--v2-font-weight-semibold);
}

.document-template-page__version-detail code {
  overflow-wrap: anywhere;
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

.document-template-page__textarea pre {
  max-height: 16rem;
  padding: var(--v2-space-3);
  overflow: auto;
  background: var(--v2-color-surface-subtle);
  border-radius: var(--v2-radius-md);
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
