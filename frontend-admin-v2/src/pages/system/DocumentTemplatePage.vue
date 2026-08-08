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
import { workflowModule } from '@/pages/system/workflow-business-modules'
import {
  bindDefaultDocumentVersion,
  createDocumentTemplate,
  createDocumentVersion,
  deleteDocumentTemplate,
  disableDocumentVersion,
  enableDocumentVersion,
  loadDocumentBusinessTypes,
  loadDocumentFieldCatalog,
  loadDocumentTemplate,
  loadDocumentTemplates,
  publishDocumentVersion,
  previewDocumentTemplateHtml,
  previewDocumentTemplateVersionHtml,
  updateDocumentVersion,
  type DocumentBusinessType,
  type DocumentBusinessTypeOption,
  type DocumentCanvasElement,
  type DocumentCanvasTable,
  type DocumentDesignSchema,
  type DocumentFieldCatalog,
  type DocumentDraft,
  type DocumentTemplateDetail,
  type DocumentTemplateSummary,
  type DocumentTemplateVersion,
} from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

type EditorMode = 'create' | 'version' | 'edit'
type VersionAction = {
  kind: 'publish' | 'disable' | 'enable' | 'default'
  version: DocumentTemplateVersion
}
type BusinessOption = {
  value: DocumentBusinessType
  label: string
}

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
const conversionIssues = ref<string[]>([])
const conversionNotices = ref<string[]>([])
const previewHtml = ref('')
const previewError = ref('')
const previewLoading = ref(false)
const versionPreviewHtml = ref('')
const versionPreviewError = ref('')
const versionPreviewLoading = ref(false)
const versionAction = ref<VersionAction | null>(null)
const deleteOpen = ref(false)
let controller: AbortController | null = null
let previewTimer: ReturnType<typeof setTimeout> | undefined
let previewRequest = 0
let versionPreviewRequest = 0

const form = reactive({
  templateCode: '',
  templateName: '',
  schemaVersion: '',
  designSchema: blankDesign(''),
  previewBusinessId: '',
  remark: '',
})
const businessOptions = computed<BusinessOption[]>(() =>
  businessTypes.value.map((item) => ({
    value: item.businessType,
    label: item.displayName,
  })),
)
const businessGroups = computed(() => {
  const groups = new Map<string, { key: string; label: string; options: BusinessOption[] }>()
  for (const option of businessOptions.value) {
    const module = workflowModule(option.value)
    const group = groups.get(module.key) ?? { ...module, options: [] }
    group.options.push(option)
    groups.set(module.key, group)
  }
  return [...groups.values()]
})
const selectedBusiness = computed(() =>
  businessTypes.value.find((item) => item.businessType === businessType.value),
)
const canEdit = computed(() => session.hasAdminOrPermission('document:template:edit'))
const canPublish = computed(() => session.hasAdminOrPermission('document:template:publish'))
const canPreviewVersion = computed(
  () => canEdit.value && session.hasAdminOrPermission('document:generate'),
)
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
      businessTypes.value = (await loadDocumentBusinessTypes(current.signal)).filter(
        (item) => item.businessType !== 'COST_SUBJECT_MAPPING',
      )
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
    designSchema: blankDesign(schemaVersion),
    previewBusinessId: '',
    remark: '',
  })
  conversionIssues.value = []
  conversionNotices.value = []
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
  if (source && applyVersion(source)) {
    showToast('warning', '旧版模板已转为画布草稿', '原版本保持不变；保存后生成新版。')
  }
  editorOpen.value = true
}

function openTemplateEditor(): void {
  if (!selectedVersion.value) return
  if (selectedVersion.value.status === 'DRAFT') openEdit(selectedVersion.value)
  else openNewVersion()
}

function openEdit(version: DocumentTemplateVersion): void {
  editorMode.value = 'edit'
  Object.assign(form, {
    templateCode: detail.value?.template.templateCode ?? '',
    templateName: detail.value?.template.templateName ?? '',
    remark: version.remark ?? '',
  })
  if (applyVersion(version)) {
    showToast('warning', '旧版草稿已转为画布', '保存后将使用画布模型。')
  }
  selectedVersionId.value = version.id
  editorOpen.value = true
}

async function saveDraft(): Promise<void> {
  if (!form.schemaVersion.trim() || !canvasValid.value || conversionIssues.value.length) {
    showToast(
      'warning',
      '模板草稿无效',
      conversionIssues.value[0] ?? '请修复越出页面安全区域的元素。',
    )
    return
  }
  saving.value = true
  try {
    const draft: DocumentDraft = {
      schemaVersion: form.schemaVersion.trim(),
      remark: form.remark.trim() || undefined,
      designSchema: JSON.stringify({
        ...form.designSchema,
        schemaVersion: form.schemaVersion.trim(),
      }),
    }
    let templateId = selectedTemplateId.value
    let versionId = selectedVersionId.value
    if (editorMode.value === 'create') {
      if (!form.templateName.trim()) {
        throw new Error('模板名称不能为空')
      }
      const version = await createDocumentTemplate({
        ...draft,
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
    else if (kind === 'enable') await enableDocumentVersion(version.id)
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

async function confirmDeleteTemplate(): Promise<void> {
  if (!detail.value) return
  saving.value = true
  try {
    await deleteDocumentTemplate(detail.value.template.id)
    deleteOpen.value = false
    await refresh()
    showToast('success', '模板已删除')
  } catch (value) {
    showToast('error', '模板删除失败', messageOf(value))
  } finally {
    saving.value = false
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

function applyVersion(version: DocumentTemplateVersion): boolean {
  conversionIssues.value = []
  conversionNotices.value = []
  form.schemaVersion = catalog.value?.schemaVersion ?? version.schemaVersion
  if (version.designSchema) {
    try {
      form.designSchema = {
        ...(JSON.parse(version.designSchema) as DocumentDesignSchema),
        schemaVersion: form.schemaVersion,
      }
      return false
    } catch {
      showToast('warning', '原画布模型无法读取', '已按字段清单生成新画布草稿。')
    }
  }
  form.designSchema = designFromLegacy(version)
  return true
}

function importHistoricalVersion(version: DocumentTemplateVersion): void {
  const converted = applyVersion(version)
  showToast(
    'success',
    `已导入 V${version.versionNo}`,
    conversionIssues.value.length
      ? conversionIssues.value.join('；')
      : conversionNotices.value.length
        ? conversionNotices.value.join('；')
        : converted
          ? '旧版字段已转换为画布，可继续二次设计。'
          : '可继续二次设计并保存为新模板或新版本。',
  )
}

function designFromLegacy(version: DocumentTemplateVersion): DocumentDesignSchema {
  const orientation = /@page\s*\{[^}]*\blandscape\b/i.test(version.templateContent)
    ? 'LANDSCAPE'
    : 'PORTRAIT'
  const pageWidth = orientation === 'PORTRAIT' ? 210 : 297
  const margin = 12
  const width = pageWidth - margin * 2
  let manifest = new Set<string>()
  try {
    const parsed: unknown = JSON.parse(version.fieldManifest)
    if (!Array.isArray(parsed) || !parsed.every((item) => typeof item === 'string')) {
      throw new Error('invalid manifest')
    }
    manifest = new Set(parsed)
  } catch {
    conversionIssues.value.push('历史字段清单无效，需修复后再保存')
  }
  const catalogFields = catalog.value?.fields ?? []
  const fieldByPath = new Map(catalogFields.map((field) => [field.path, field]))
  const legacyAliases: Record<string, string> = {
    'items.orderQuantity': 'items.orderedQuantity',
    'items.cumulativeReceivedQuantity': 'items.receivedQuantity',
  }
  const resolvedFields = [...manifest].map((path) => ({
    originalPath: path,
    field: fieldByPath.get(legacyAliases[path] ?? path),
  }))
  const aliasFields = resolvedFields.filter(
    ({ originalPath, field }) => field && field.path !== originalPath,
  )
  if (aliasFields.length) {
    conversionNotices.value.push(
      `旧字段已映射：${aliasFields.map(({ originalPath, field }) => `${originalPath}→${field!.path}`).join('、')}`,
    )
  }
  const missingFields = resolvedFields
    .filter(({ field }) => !field)
    .map(({ originalPath }) => originalPath)
  if (missingFields.length) {
    conversionNotices.value.push(`无现行数据源字段已转为文本占位：${missingFields.join('、')}`)
  }
  const fields = resolvedFields.flatMap(({ field }) => (field ? [field] : []))
  const scalarFields = fields.filter((field) => !field.collectionPath)
  const collectionGroups = new Map<string, typeof fields>()
  fields
    .filter((field) => field.collectionPath)
    .forEach((field) => {
      const collectionPath = field.collectionPath!
      collectionGroups.set(collectionPath, [...(collectionGroups.get(collectionPath) ?? []), field])
    })
  for (const [collectionPath, collectionFields] of collectionGroups) {
    if (collectionFields.length > 30) {
      conversionIssues.value.push(`${collectionPath} 超过30列，需精简后再保存`)
    }
  }
  const tableGroups = [...collectionGroups.entries()].map(
    ([collectionPath, collectionFields]) =>
      [collectionPath, collectionFields.slice(0, 30)] as const,
  )
  const gap = 6
  const columnWidth = (width - gap) / 2
  const elements: DocumentCanvasElement[] = [
    {
      id: 'legacy-title',
      type: 'TEXT',
      text: form.templateName || '单据标题',
      xMm: margin,
      yMm: margin,
      widthMm: width,
      heightMm: 14,
      fontSizePt: 18,
      align: 'CENTER',
      repeat: 'BODY',
      zIndex: 0,
    },
    ...scalarFields.map((field, index) => ({
      id: `legacy-field-${index + 1}`,
      type: 'FIELD' as const,
      text: field.label,
      fieldPath: field.path,
      xMm: margin + (index % 2) * (columnWidth + gap),
      yMm: 34 + Math.floor(index / 2) * 13,
      widthMm: columnWidth,
      heightMm: 10,
      fontSizePt: 10,
      align: 'LEFT' as const,
      repeat: 'BODY' as const,
      zIndex: index + 1,
    })),
    ...missingFields.map((path, index) => ({
      id: `legacy-placeholder-${index + 1}`,
      type: 'TEXT' as const,
      text: `${legacyPlaceholderLabel(path)}：________`,
      xMm: margin + ((scalarFields.length + index) % 2) * (columnWidth + gap),
      yMm: 34 + Math.floor((scalarFields.length + index) / 2) * 13,
      widthMm: columnWidth,
      heightMm: 10,
      fontSizePt: 10,
      align: 'LEFT' as const,
      repeat: 'BODY' as const,
      zIndex: scalarFields.length + index + 1,
    })),
  ]
  const tableY = 38 + Math.ceil((scalarFields.length + missingFields.length) / 2) * 13
  const tables: DocumentCanvasTable[] = tableGroups.map(
    ([collectionPath, collectionFields], tableIndex) => {
      const columnWidthMm = Math.round((width / collectionFields.length) * 10) / 10
      return {
        id: `legacy-table-${tableIndex + 1}`,
        collectionPath,
        xMm: margin,
        yMm: tableY + tableIndex * 46,
        widthMm: width,
        heightMm: 38,
        columns: collectionFields.map((field, index) => ({
          fieldPath: field.path,
          header: field.label,
          widthMm:
            index === collectionFields.length - 1
              ? Math.round((width - columnWidthMm * index) * 10) / 10
              : columnWidthMm,
        })),
      }
    },
  )
  return {
    schemaVersion: form.schemaVersion,
    page: {
      size: 'A4',
      orientation,
      marginMm: { top: margin, right: margin, bottom: margin, left: margin },
    },
    elements,
    tables,
  }
}

function legacyPlaceholderLabel(path: string): string {
  return (
    {
      'receipt.totalAmountChinese': '本次合计金额（大写）',
      'signatures.supplierRepresentative': '供应商代表',
      'signatures.receiver': '验收人',
      'signatures.projectManager': '项目负责人',
      'signatures.warehouseKeeperOrUser': '仓库管理员/使用人',
    }[path] ?? path
  )
}

async function refreshPreview(): Promise<void> {
  const request = ++previewRequest
  if (!editorOpen.value || !businessType.value || !canvasValid.value) {
    previewHtml.value = ''
    previewError.value = ''
    previewLoading.value = false
    return
  }
  previewLoading.value = true
  previewError.value = ''
  try {
    const html = (
      await previewDocumentTemplateHtml({
        businessType: businessType.value,
        designSchema: JSON.stringify(form.designSchema),
        businessId: form.previewBusinessId.trim() || undefined,
      })
    ).html
    if (request === previewRequest) previewHtml.value = html
  } catch (value) {
    if (request === previewRequest) {
      previewHtml.value = ''
      previewError.value = messageOf(value)
    }
  } finally {
    if (request === previewRequest) previewLoading.value = false
  }
}

async function refreshVersionPreview(): Promise<void> {
  const request = ++versionPreviewRequest
  versionPreviewHtml.value = ''
  versionPreviewError.value = ''
  versionPreviewLoading.value = false
  const versionId = selectedVersionId.value
  if (!versionId || !canPreviewVersion.value) return
  versionPreviewLoading.value = true
  try {
    const html = (await previewDocumentTemplateVersionHtml(versionId)).html
    if (request === versionPreviewRequest && versionId === selectedVersionId.value) {
      versionPreviewHtml.value = html
    }
  } catch (value) {
    if (request === versionPreviewRequest && versionId === selectedVersionId.value) {
      versionPreviewError.value = messageOf(value)
    }
  } finally {
    if (request === versionPreviewRequest) versionPreviewLoading.value = false
  }
}

watch(
  () => [
    editorOpen.value,
    businessType.value,
    JSON.stringify(form.designSchema),
    form.previewBusinessId,
    canvasValid.value,
  ],
  () => {
    clearTimeout(previewTimer)
    previewTimer = setTimeout(() => void refreshPreview(), 250)
  },
)

watch(
  () => [selectedVersionId.value, canPreviewVersion.value],
  () => void refreshVersionPreview(),
  { immediate: true },
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

function versionActionLabel(kind: VersionAction['kind']): string {
  return { publish: '发布', disable: '停用', enable: '启用', default: '设为默认' }[kind]
}

onMounted(() => void refresh())
onBeforeUnmount(() => {
  controller?.abort()
  previewRequest += 1
  versionPreviewRequest += 1
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
    <V2Card v-else class="document-template-page__workbench">
      <div class="document-template-page__columns">
        <section
          class="document-template-page__column"
          aria-labelledby="document-business-types-title"
        >
          <header class="document-template-page__column-heading">
            <h2 id="document-business-types-title">1.业务模块</h2>
          </header>
          <div
            class="document-template-page__business-list"
            aria-labelledby="document-business-types-title"
          >
            <section
              v-for="group in businessGroups"
              :key="group.key"
              class="document-template-page__business-group"
            >
              <div class="document-template-page__business-group-heading">
                <h3>{{ group.label }}</h3>
                <span>{{ group.options.length }}</span>
              </div>
              <div class="document-template-page__business-options">
                <button
                  v-for="option in group.options"
                  :key="option.value"
                  type="button"
                  class="document-template-page__business-option"
                  :class="{ 'is-selected': option.value === businessType }"
                  :aria-pressed="option.value === businessType"
                  @click="selectBusinessType(option.value)"
                >
                  <span>{{ option.label }}</span>
                </button>
              </div>
            </section>
          </div>
        </section>

        <section class="document-template-page__column" aria-labelledby="document-templates-title">
          <header class="document-template-page__column-heading">
            <h2 id="document-templates-title">2.模板与版本</h2>
            <div class="document-template-page__column-actions">
              <V2Button v-if="canEdit && selectedVersion" size="small" @click="openTemplateEditor">
                编辑模板
              </V2Button>
              <V2Button v-if="canEdit && detail" size="small" @click="openNewVersion">
                新建版本
              </V2Button>
              <V2Button
                v-if="canEdit && detail"
                size="small"
                variant="danger"
                @click="deleteOpen = true"
              >
                删除模板
              </V2Button>
            </div>
          </header>
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
                    detail.defaultBinding?.templateVersionId === version.id
                      ? '默认版本'
                      : '普通版本'
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
                <V2Button
                  v-if="canPublish && version.status === 'DISABLED'"
                  size="small"
                  @click="versionAction = { kind: 'enable', version }"
                >
                  启用
                </V2Button>
              </V2ActionMenu>
            </div>
          </section>
          <V2Pagination
            :total="templates.length"
            :page-no="pageNo"
            :page-size="pageSize"
            label="业务模板分页"
            @update:page-no="pageNo = $event"
          />
        </section>

        <section class="document-template-page__column" aria-labelledby="document-preview-title">
          <header class="document-template-page__column-heading">
            <h2 id="document-preview-title">3.HTML预览</h2>
          </header>
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
          <V2PageState
            v-else-if="!canPreviewVersion"
            kind="empty"
            title="无 HTML 预览权限"
            description="需要模板编辑和单据生成权限。"
          />
          <V2PageState
            v-else-if="versionPreviewLoading"
            kind="loading"
            title="正在生成 HTML 预览"
            description="请稍候。"
          />
          <V2PageState
            v-else-if="versionPreviewError"
            kind="error"
            title="HTML 预览失败"
            :description="versionPreviewError"
          />
          <V2PageState
            v-else-if="!versionPreviewHtml"
            kind="empty"
            title="暂无 HTML 预览"
            description="服务端未返回可预览内容。"
          />
          <div v-else class="document-template-page__html-preview">
            <div class="document-template-page__preview-meta">
              <strong>V{{ selectedVersion.versionNo }}</strong>
              <V2Badge :tone="statusTone(selectedVersion.status)">
                {{ versionStatusLabel(selectedVersion.status) }}
              </V2Badge>
              <span>{{ selectedVersion.schemaVersion }}</span>
            </div>
            <iframe title="选中模板版本 HTML 预览" sandbox="" :srcdoc="versionPreviewHtml"></iframe>
            <details class="document-template-page__version-detail">
              <summary>版本信息</summary>
              <strong>内容哈希</strong>
              <code>{{ selectedVersion.contentHash }}</code>
              <strong>字段清单</strong>
              <pre>{{ selectedVersion.fieldManifest }}</pre>
            </details>
          </div>
        </section>
      </div>
    </V2Card>

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
      fullscreen
    >
      <div class="document-template-page__form">
        <section
          v-if="editorMode !== 'edit' && detail?.versions.length"
          class="document-template-page__history-import"
          aria-label="导入历史模板"
        >
          <div>
            <strong>导入历史模板</strong>
            <small>一键载入历史版本后继续设计</small>
          </div>
          <V2Button
            v-for="version in detail.versions"
            :key="version.id"
            size="small"
            variant="secondary"
            @click="importHistoricalVersion(version)"
          >
            导入 V{{ version.versionNo }} · {{ versionStatusLabel(version.status) }}
          </V2Button>
        </section>
        <V2Input
          v-model="form.templateCode"
          label="模板编码"
          :placeholder="editorMode === 'create' ? '保存时自动生成' : undefined"
          disabled
        />
        <V2Input
          v-model="form.templateName"
          label="模板名称"
          required
          :disabled="editorMode !== 'create'"
        />
        <V2Input v-model="form.schemaVersion" label="契约版本" required disabled />
        <V2Input v-model="form.remark" label="备注" />
      </div>
      <p
        v-if="conversionIssues.length"
        class="document-template-page__conversion-warning"
        role="alert"
      >
        历史模板未完全转换：{{ conversionIssues.join('；') }}。保存已阻止。
      </p>
      <p
        v-if="conversionNotices.length"
        class="document-template-page__conversion-warning"
        role="status"
      >
        历史模板兼容处理：{{ conversionNotices.join('；') }}。可继续设计并保存。
      </p>
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
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="editorOpen = false">取消</V2Button>
        <V2Button
          :loading="saving"
          :disabled="!canvasValid || Boolean(conversionIssues.length)"
          @click="saveDraft"
          >保存草稿</V2Button
        >
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(versionAction)"
      title="确认模板状态变更"
      :description="
        versionAction
          ? `${versionActionLabel(versionAction.kind)} V${versionAction.version.versionNo}？`
          : ''
      "
      :confirm-text="versionAction ? versionActionLabel(versionAction.kind) : '确认'"
      :danger="versionAction?.kind === 'disable'"
      :loading="saving"
      @close="versionAction = null"
      @confirm="confirmVersionAction"
    />
    <V2ConfirmDialog
      :open="deleteOpen"
      title="确认删除模板"
      :description="detail ? `删除“${detail.template.templateName}”？仅未发布草稿允许删除。` : ''"
      confirm-text="删除"
      danger
      :loading="saving"
      @close="deleteOpen = false"
      @confirm="confirmDeleteTemplate"
    />
  </V2Stack>
</template>

<style scoped>
.document-template-page {
  height: 100%;
  min-height: 0;
}

.document-template-page__workbench {
  flex: 1;
  min-height: 0;
}

.document-template-page__workbench :deep(.v2-card__body) {
  height: 100%;
  min-height: 0;
  padding: 0;
}

.document-template-page__columns {
  display: grid;
  height: 100%;
  min-height: 0;
  grid-template-columns: minmax(13rem, 0.6fr) minmax(19rem, 0.9fr) minmax(24rem, 1.5fr);
}

.document-template-page__column {
  min-width: 0;
  min-height: 0;
  padding: 0 var(--v2-space-4) var(--v2-space-4);
  overflow-y: auto;
}

.document-template-page__column + .document-template-page__column {
  border-left: 1px solid var(--v2-color-border-subtle);
}

.document-template-page__column-heading {
  position: sticky;
  z-index: 1;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
  min-height: 3.5rem;
  margin-bottom: var(--v2-space-3);
  padding: var(--v2-space-3) 0;
  background: var(--v2-color-surface);
  border-bottom: 1px solid var(--v2-color-border-subtle);
}

.document-template-page__column-heading h2 {
  margin: 0;
  font-size: var(--v2-font-size-14);
}

.document-template-page__column-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--v2-space-2);
}

.document-template-page__business-list {
  display: grid;
  gap: var(--v2-space-4);
}

.document-template-page__business-group,
.document-template-page__business-options {
  display: grid;
  gap: var(--v2-space-2);
}

.document-template-page__business-group-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--v2-space-1);
  color: var(--v2-color-text-muted);
  border-bottom: 1px solid var(--v2-color-border);
}

.document-template-page__business-group-heading h3 {
  margin: 0;
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-14);
}

.document-template-page__business-group-heading span {
  font-size: var(--v2-font-size-11);
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

.document-template-page__history-import {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
  flex-wrap: wrap;
  padding: var(--v2-space-3);
  background: var(--v2-color-surface-subtle);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.document-template-page__history-import > div {
  display: grid;
  gap: var(--v2-space-1);
  margin-right: auto;
}

.document-template-page__history-import small {
  color: var(--v2-color-text-muted);
}

.document-template-page__conversion-warning {
  margin: 0;
  padding: var(--v2-space-3);
  color: var(--v2-color-danger-text);
  background: var(--v2-color-danger-soft);
  border-radius: var(--v2-radius-md);
}

@media (max-width: 1180px) {
  .document-template-page {
    height: auto;
  }

  .document-template-page__workbench {
    flex: initial;
  }

  .document-template-page__workbench :deep(.v2-card__body),
  .document-template-page__columns {
    height: auto;
  }

  .document-template-page__columns {
    grid-template-columns: 1fr;
  }

  .document-template-page__column {
    overflow-y: visible;
  }

  .document-template-page__column + .document-template-page__column {
    border-top: 1px solid var(--v2-color-border-subtle);
    border-left: 0;
  }

  .document-template-page__column-heading {
    position: static;
  }
}

@media (max-width: 980px) {
  .document-template-page__form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .document-template-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
