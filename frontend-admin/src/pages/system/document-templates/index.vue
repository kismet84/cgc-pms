<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  bindDocumentDefaultTemplate,
  copyDocumentTemplateVersion,
  createDocumentTemplate,
  disableDocumentTemplateVersion,
  exportDocumentTemplateVersion,
  getDocumentTemplate,
  getDocumentTemplateFieldCatalog,
  getDocumentTemplates,
  importDocumentTemplate,
  previewDocumentTemplateVersion,
  publishDocumentTemplateVersion,
  updateDocumentTemplateVersion,
  validateDocumentTemplate,
} from '@/api/modules/document'
import { useUserStore } from '@/stores/user'
import type {
  DocumentBusinessType,
  DocumentTemplateCreatePayload,
  DocumentTemplateDetail,
  DocumentTemplateDraft,
  DocumentTemplateField,
  DocumentTemplateFieldCatalog,
  DocumentTemplateSummary,
  DocumentTemplateVersion,
} from '@/types/document'

type EditorMode = 'create' | 'edit' | 'import'

interface EditorForm extends DocumentTemplateCreatePayload {
  versionId?: string
}

const userStore = useUserStore()
const businessType = ref<DocumentBusinessType>('PAYMENT')
const templates = ref<DocumentTemplateSummary[]>([])
const detail = ref<DocumentTemplateDetail | null>(null)
const catalog = ref<DocumentTemplateFieldCatalog | null>(null)
const selectedTemplateId = ref('')
const selectedVersionId = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const editorVisible = ref(false)
const editorMode = ref<EditorMode>('create')
const saving = ref(false)
const validating = ref(false)
const previewing = ref(false)
const validationHint = ref('')
const previewBusinessId = ref('')
const importInput = ref<HTMLInputElement | null>(null)

function defaultField(type: DocumentBusinessType) {
  return type === 'PAYMENT' ? 'payment.applyCode' : 'settlement.code'
}

function defaultSchema(type: DocumentBusinessType) {
  return type === 'PAYMENT' ? 'payment.v1' : 'settlement.v1'
}

function blankEditor(type: DocumentBusinessType): EditorForm {
  const field = defaultField(type)
  return {
    templateCode: '',
    templateName: '',
    businessType: type,
    schemaVersion: catalog.value?.businessType === type ? catalog.value.schemaVersion : defaultSchema(type),
    templateContent: `<html><body><h1>新业务单据</h1><p>{{${field}}}</p></body></html>`,
    fieldManifest: JSON.stringify([field], null, 2),
    remark: '',
  }
}

const editorForm = reactive<EditorForm>(blankEditor('PAYMENT'))

const isAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canEdit = computed(() => isAdmin.value || userStore.hasPermission('document:template:edit'))
const canPublish = computed(() => isAdmin.value || userStore.hasPermission('document:template:publish'))
const canPreview = computed(
  () => canEdit.value && (isAdmin.value || userStore.hasPermission('document:generate')),
)
const versions = computed(() => detail.value?.versions ?? [])
const selectedVersion = computed(() =>
  versions.value.find((version) => String(version.id) === selectedVersionId.value) ?? null,
)
const scalarFields = computed(() => catalog.value?.fields.filter((field) => !field.collectionPath) ?? [])
const collectionGroups = computed(() => {
  const groups = new Map<string, DocumentTemplateField[]>()
  ;(catalog.value?.fields ?? []).forEach((field) => {
    if (!field.collectionPath) return
    const values = groups.get(field.collectionPath) ?? []
    values.push(field)
    groups.set(field.collectionPath, values)
  })
  return [...groups.entries()].map(([path, fields]) => ({ path, fields }))
})

const templateColumns = [
  { title: '模板名称', dataIndex: 'templateName' },
  { title: '编码', dataIndex: 'templateCode', width: 220 },
  { title: '状态', dataIndex: 'enabled', width: 90 },
  { title: '默认绑定', dataIndex: 'defaultVersionId', width: 100 },
]
const versionColumns = [
  { title: '版本', dataIndex: 'versionNo', width: 80 },
  { title: '状态', dataIndex: 'status', width: 110 },
  { title: '契约', dataIndex: 'schemaVersion', width: 130 },
  { title: '发布时间', dataIndex: 'publishedAt', width: 170 },
  { title: '操作', key: 'action', width: 310 },
]

function businessTypeLabel(type: DocumentBusinessType) {
  return type === 'PAYMENT' ? '付款申请单' : '结算单'
}

function statusColor(status: string) {
  return status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'processing' : 'default'
}

function resetEditor(type: DocumentBusinessType) {
  Object.assign(editorForm, blankEditor(type))
  validationHint.value = ''
}

async function loadCatalog(type = businessType.value) {
  catalog.value = await getDocumentTemplateFieldCatalog(type)
}

async function loadDetail(templateId: string, versionId?: string) {
  detailLoading.value = true
  try {
    detail.value = await getDocumentTemplate(templateId)
    selectedTemplateId.value = String(templateId)
    selectedVersionId.value = versionId ?? String(detail.value.versions[0]?.id ?? '')
  } finally {
    detailLoading.value = false
  }
}

async function loadTemplates(preferredTemplateId?: string, preferredVersionId?: string) {
  loading.value = true
  try {
    templates.value = await getDocumentTemplates(businessType.value)
    const target =
      templates.value.find((template) => String(template.id) === preferredTemplateId) ?? templates.value[0]
    if (target) {
      await loadDetail(String(target.id), preferredVersionId)
    } else {
      detail.value = null
      selectedTemplateId.value = ''
      selectedVersionId.value = ''
    }
  } catch (error) {
    console.error(error)
    templates.value = []
    detail.value = null
    message.error('加载业务单据模板失败')
  } finally {
    loading.value = false
  }
}

async function reloadForBusinessType() {
  selectedTemplateId.value = ''
  selectedVersionId.value = ''
  try {
    await loadCatalog()
    await loadTemplates()
  } catch (error) {
    console.error(error)
    message.error('加载字段目录失败')
  }
}

async function selectTemplate(templateId: string) {
  try {
    await loadDetail(String(templateId))
  } catch (error) {
    console.error(error)
    message.error('加载模板版本失败')
  }
}

async function openCreate() {
  await loadCatalog(businessType.value)
  editorMode.value = 'create'
  resetEditor(businessType.value)
  editorVisible.value = true
}

async function openEdit(version: DocumentTemplateVersion) {
  if (!detail.value) return
  await loadCatalog(detail.value.template.businessType)
  editorMode.value = 'edit'
  Object.assign(editorForm, {
    templateCode: detail.value.template.templateCode,
    templateName: detail.value.template.templateName,
    businessType: detail.value.template.businessType,
    schemaVersion: version.schemaVersion,
    templateContent: version.templateContent,
    fieldManifest: version.fieldManifest,
    remark: version.remark ?? '',
    versionId: String(version.id),
  })
  validationHint.value = ''
  editorVisible.value = true
}

function onEditorBusinessTypeChange(value: DocumentBusinessType) {
  editorForm.businessType = value
  editorForm.schemaVersion = defaultSchema(value)
  resetEditor(value)
  loadCatalog(value).catch((error) => {
    console.error(error)
    message.error('加载字段目录失败')
  })
}

function currentDraft(): DocumentTemplateDraft {
  return {
    schemaVersion: editorForm.schemaVersion.trim(),
    templateContent: editorForm.templateContent,
    fieldManifest: editorForm.fieldManifest,
    remark: editorForm.remark?.trim() || undefined,
  }
}

function ensureManifestField(path: string) {
  let fields: string[] = []
  try {
    const value: unknown = JSON.parse(editorForm.fieldManifest)
    if (Array.isArray(value) && value.every((field) => typeof field === 'string')) fields = value
  } catch {
    message.warning('字段清单不是有效 JSON，已按当前插入字段重新建立')
  }
  if (!fields.includes(path)) fields.push(path)
  editorForm.fieldManifest = JSON.stringify(fields, null, 2)
}

function insertText(value: string) {
  const textarea = document.querySelector<HTMLTextAreaElement>('[data-testid="document-template-editor"]')
  const source = editorForm.templateContent
  const start = textarea?.selectionStart ?? source.length
  const end = textarea?.selectionEnd ?? source.length
  editorForm.templateContent = `${source.slice(0, start)}${value}${source.slice(end)}`
  void nextTick(() => {
    const current = document.querySelector<HTMLTextAreaElement>('[data-testid="document-template-editor"]')
    current?.focus()
    current?.setSelectionRange(start + value.length, start + value.length)
  })
}

function insertField(field: DocumentTemplateField) {
  if (field.collectionPath) {
    const localPath = field.path.slice(field.collectionPath.length + 1)
    insertText(`{{#each ${field.collectionPath}}}\n  {{${localPath}}}\n{{/each}}`)
  } else {
    insertText(`{{${field.path}}}`)
  }
  ensureManifestField(field.path)
}

function insertCollection(path: string, fields: DocumentTemplateField[]) {
  const first = fields[0]
  if (!first) return
  const localPath = first.path.slice(path.length + 1)
  insertText(`{{#each ${path}}}\n  {{${localPath}}}\n{{/each}}`)
  ensureManifestField(first.path)
}

async function validateEditor() {
  validating.value = true
  validationHint.value = ''
  try {
    const result = await validateDocumentTemplate(editorForm.businessType, currentDraft())
    validationHint.value = `校验通过：${result.fieldCount} 个字段，${result.collectionPaths.length} 个循环集合。`
    message.success('模板校验通过')
  } catch (error) {
    console.error(error)
    validationHint.value = '校验失败：请按接口返回的字段、schema 或循环上下文错误修正。'
  } finally {
    validating.value = false
  }
}

async function saveEditor() {
  const code = editorForm.templateCode.trim()
  const name = editorForm.templateName.trim()
  if (!code || !name) {
    message.error('请填写模板编码和模板名称')
    return
  }
  saving.value = true
  try {
    let version: DocumentTemplateVersion
    if (editorMode.value === 'edit') {
      await updateDocumentTemplateVersion(editorForm.versionId!, currentDraft())
      version = { ...selectedVersion.value!, ...currentDraft(), id: editorForm.versionId! }
      message.success('草稿已保存')
      await loadDetail(selectedTemplateId.value, editorForm.versionId)
    } else {
      const payload: DocumentTemplateCreatePayload = {
        ...currentDraft(),
        templateCode: code,
        templateName: name,
        businessType: editorForm.businessType,
      }
      version =
        editorMode.value === 'import'
          ? await importDocumentTemplate(payload)
          : await createDocumentTemplate(payload)
      message.success(editorMode.value === 'import' ? '模板已导入为草稿' : '模板草稿已创建')
      businessType.value = editorForm.businessType
      await loadCatalog(businessType.value)
      await loadTemplates(String(version.templateId), String(version.id))
    }
    editorVisible.value = false
  } catch (error) {
    console.error(error)
  } finally {
    saving.value = false
  }
}

function requestCopy(version: DocumentTemplateVersion) {
  if (!detail.value) return
  Modal.confirm({
    title: '创建新草稿版本',
    content: `将基于 V${version.versionNo} 创建可编辑草稿，已发布版本不会被修改。`,
    okText: '创建草稿',
    cancelText: '取消',
    onOk: async () => {
      const copied = await copyDocumentTemplateVersion(String(detail.value!.template.id), String(version.id))
      message.success('已创建新草稿版本')
      await loadDetail(selectedTemplateId.value, String(copied.id))
    },
  })
}

function requestPublish(version: DocumentTemplateVersion) {
  Modal.confirm({
    title: '发布模板版本',
    content: `发布 V${version.versionNo} 后模板正文和字段清单不可再编辑。`,
    okText: '发布',
    cancelText: '取消',
    onOk: async () => {
      await publishDocumentTemplateVersion(String(version.id))
      message.success('模板版本已发布')
      await loadDetail(selectedTemplateId.value, String(version.id))
      await loadTemplates(selectedTemplateId.value, String(version.id))
    },
  })
}

function requestDisable(version: DocumentTemplateVersion) {
  Modal.confirm({
    title: '停用模板版本',
    content: `停用 V${version.versionNo} 后不能再用于新生成；历史生成文件保持不变。`,
    okText: '停用',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await disableDocumentTemplateVersion(String(version.id))
      message.success('模板版本已停用')
      await loadDetail(selectedTemplateId.value)
      await loadTemplates(selectedTemplateId.value)
    },
  })
}

async function bindDefault(version: DocumentTemplateVersion) {
  if (!detail.value) return
  const expectedLockVersion = detail.value.defaultBinding?.lockVersion ?? 0
  try {
    await bindDocumentDefaultTemplate(String(version.id), expectedLockVersion)
    message.success('默认模板已切换')
    await loadDetail(selectedTemplateId.value, String(version.id))
    await loadTemplates(selectedTemplateId.value, String(version.id))
  } catch (error) {
    console.error(error)
    message.warning('默认模板可能已被其他管理员切换，已刷新当前状态')
    await loadDetail(selectedTemplateId.value)
    await loadTemplates(selectedTemplateId.value)
  }
}

async function exportVersion(version: DocumentTemplateVersion) {
  try {
    const payload = await exportDocumentTemplateVersion(String(version.id))
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${payload.templateCode}-v${version.versionNo}.json`
    link.click()
    URL.revokeObjectURL(url)
    message.success('模板 JSON 已导出')
  } catch (error) {
    console.error(error)
  }
}

function triggerImport() {
  importInput.value?.click()
}

async function importFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const payload = JSON.parse(await file.text()) as Partial<DocumentTemplateCreatePayload>
    if (
      !payload.templateCode ||
      !payload.templateName ||
      (payload.businessType !== 'PAYMENT' && payload.businessType !== 'SETTLEMENT') ||
      !payload.schemaVersion ||
      !payload.templateContent ||
      !payload.fieldManifest
    ) {
      throw new Error('文件结构不完整')
    }
    await loadCatalog(payload.businessType)
    editorMode.value = 'import'
    Object.assign(editorForm, {
      ...payload,
      templateCode: `${payload.templateCode.slice(0, 72)}_IMPORT`,
      remark: payload.remark ?? '由受控模板 JSON 导入，待校验后发布',
    })
    validationHint.value = '导入内容尚未持久化；请先校验，再保存为新草稿。'
    editorVisible.value = true
  } catch (error) {
    console.error(error)
    message.error('导入失败：文件必须是通过本页面导出的模板 JSON')
  }
}

async function previewVersion(version: DocumentTemplateVersion) {
  if (!previewBusinessId.value.trim()) {
    message.error('请输入用于预览的业务对象 ID')
    return
  }
  previewing.value = true
  try {
    const blob = await previewDocumentTemplateVersion(String(version.id), previewBusinessId.value.trim())
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank', 'noopener,noreferrer')
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (error) {
    console.error(error)
  } finally {
    previewing.value = false
  }
}

function isDefault(version: DocumentTemplateVersion) {
  return String(detail.value?.defaultBinding?.templateVersionId ?? '') === String(version.id)
}

onMounted(reloadForBusinessType)
</script>

<template>
  <div class="lg-list-page lg-page app-page document-template-page">
    <div class="lg-page-head">
      <a-breadcrumb class="lg-page-head-breadcrumb">
        <a-breadcrumb-item>系统设置</a-breadcrumb-item>
        <a-breadcrumb-item>业务单据模板</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-select v-model:value="businessType" style="width: 160px" @change="reloadForBusinessType">
          <a-select-option value="PAYMENT">付款申请单</a-select-option>
          <a-select-option value="SETTLEMENT">结算单</a-select-option>
        </a-select>
        <a-button @click="loadTemplates(selectedTemplateId, selectedVersionId)">刷新</a-button>
        <a-button v-if="canEdit" type="primary" data-testid="create-document-template" @click="openCreate">
          新建模板
        </a-button>
        <a-button v-if="canEdit" data-testid="import-document-template" @click="triggerImport">
          导入模板
        </a-button>
        <input
          ref="importInput"
          type="file"
          accept="application/json,.json"
          class="hidden-input"
          @change="importFile"
        />
      </div>
      <span class="page-tip">仅受限 HTML/CSS、字段目录和单层循环可发布；不支持脚本或拖拽设计器。</span>
    </div>

    <div class="template-layout">
      <main class="lg-list-table-panel">
        <h3>{{ businessTypeLabel(businessType) }}模板</h3>
        <a-table
          row-key="id"
          :columns="templateColumns"
          :data-source="templates"
          :loading="loading"
          :pagination="false"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <a-button
              v-if="column.dataIndex === 'templateName'"
              type="link"
              :class="{ selected: String(record.id) === selectedTemplateId }"
              @click="selectTemplate(String(record.id))"
            >
              {{ record.templateName }}
            </a-button>
            <a-tag v-else-if="column.dataIndex === 'enabled'" :color="record.enabled === 1 ? 'success' : 'default'">
              {{ record.enabled === 1 ? '启用' : '停用' }}
            </a-tag>
            <span v-else-if="column.dataIndex === 'defaultVersionId'">
              {{ record.defaultVersionId ? '已绑定' : '—' }}
            </span>
          </template>
        </a-table>
        <a-empty v-if="!loading && !templates.length" description="当前类型暂无模板，可新建或导入草稿" />
      </main>

      <main class="lg-list-table-panel version-panel">
        <div class="panel-heading">
          <div>
            <h3>{{ detail?.template.templateName || '模板版本' }}</h3>
            <span v-if="detail" class="page-tip">{{ detail.template.templateCode }}</span>
          </div>
          <a-button
            v-if="canEdit && selectedVersion"
            :disabled="detailLoading"
            @click="requestCopy(selectedVersion)"
          >
            从当前版本创建草稿
          </a-button>
        </div>
        <a-table
          row-key="id"
          :columns="versionColumns"
          :data-source="versions"
          :loading="detailLoading"
          :pagination="false"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <a-button
              v-if="column.dataIndex === 'versionNo'"
              type="link"
              :class="{ selected: String(record.id) === selectedVersionId }"
              @click="selectedVersionId = String(record.id)"
            >
              V{{ record.versionNo }}
            </a-button>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
              <a-tag v-if="isDefault(record)" color="gold">默认</a-tag>
            </template>
            <a-space v-else-if="column.key === 'action'" size="small" wrap>
              <a-button v-if="canEdit && record.status === 'DRAFT'" size="small" @click="openEdit(record)">
                编辑
              </a-button>
              <a-button v-if="canEdit" size="small" @click="exportVersion(record)">导出</a-button>
              <a-button
                v-if="canPublish && record.status === 'DRAFT'"
                size="small"
                type="primary"
                @click="requestPublish(record)"
              >
                发布
              </a-button>
              <a-button
                v-if="canPublish && record.status === 'PUBLISHED' && !isDefault(record)"
                size="small"
                @click="bindDefault(record)"
              >
                设为默认
              </a-button>
              <a-button
                v-if="canPublish && record.status === 'PUBLISHED'"
                size="small"
                danger
                @click="requestDisable(record)"
              >
                停用
              </a-button>
            </a-space>
          </template>
        </a-table>
        <a-empty v-if="detail && !versions.length" description="该模板没有版本" />
      </main>
    </div>

    <section v-if="selectedVersion" class="lg-list-table-panel preview-panel">
      <div class="panel-heading">
        <div>
          <h3>受控预览</h3>
          <span class="page-tip">仅预览已保存草稿/发布版本；不写入生成记录、不归档。</span>
        </div>
        <a-space>
          <a-input v-model:value="previewBusinessId" placeholder="业务对象 ID" style="width: 220px" />
          <a-button
            v-if="canPreview"
            :loading="previewing"
            type="primary"
            data-testid="preview-document-template"
            @click="previewVersion(selectedVersion)"
          >
            预览当前版本
          </a-button>
          <span v-else class="page-tip">需同时具备模板维护和单据生成权限</span>
        </a-space>
      </div>
    </section>

    <a-modal
      v-model:open="editorVisible"
      :title="editorMode === 'edit' ? '编辑草稿版本' : editorMode === 'import' ? '导入模板草稿' : '新建模板草稿'"
      width="1120px"
      :mask-closable="!saving"
      :closable="!saving"
      :footer="null"
    >
      <a-form layout="vertical">
        <div class="editor-meta-grid">
          <a-form-item label="模板编码" required>
            <a-input v-model:value="editorForm.templateCode" :disabled="editorMode === 'edit'" />
          </a-form-item>
          <a-form-item label="模板名称" required>
            <a-input v-model:value="editorForm.templateName" :disabled="editorMode === 'edit'" />
          </a-form-item>
          <a-form-item label="业务类型" required>
            <a-select
              v-model:value="editorForm.businessType"
              :disabled="editorMode === 'edit'"
              @change="onEditorBusinessTypeChange"
            >
              <a-select-option value="PAYMENT">付款申请单</a-select-option>
              <a-select-option value="SETTLEMENT">结算单</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="数据契约">
            <a-input v-model:value="editorForm.schemaVersion" disabled />
          </a-form-item>
        </div>

        <div class="editor-grid">
          <section>
            <a-form-item label="受限 HTML/CSS 模板" required>
              <a-textarea
                v-model:value="editorForm.templateContent"
                data-testid="document-template-editor"
                :rows="20"
                spellcheck="false"
              />
            </a-form-item>
            <a-form-item label="字段清单 JSON" required>
              <a-textarea v-model:value="editorForm.fieldManifest" :rows="7" spellcheck="false" />
            </a-form-item>
            <a-form-item label="备注">
              <a-input v-model:value="editorForm.remark" :maxlength="500" />
            </a-form-item>
            <a-alert v-if="validationHint" :message="validationHint" type="info" show-icon />
            <div class="editor-actions">
              <a-button :loading="validating" @click="validateEditor">校验草稿</a-button>
              <a-button type="primary" :loading="saving" @click="saveEditor">保存草稿</a-button>
            </div>
          </section>

          <aside class="field-catalog">
            <h4>字段目录</h4>
            <p class="page-tip">点击字段会插入占位符，并同步加入字段清单。集合字段必须放在对应循环内。</p>
            <a-collapse ghost>
              <a-collapse-panel key="scalar" header="普通字段">
                <a-space wrap>
                  <a-button
                    v-for="field in scalarFields"
                    :key="field.path"
                    size="small"
                    @click="insertField(field)"
                  >
                    {{ field.label }} · {{ field.path }}
                  </a-button>
                </a-space>
              </a-collapse-panel>
              <a-collapse-panel
                v-for="group in collectionGroups"
                :key="group.path"
                :header="`循环：${group.path}`"
              >
                <a-space wrap>
                  <a-button size="small" type="dashed" @click="insertCollection(group.path, group.fields)">
                    插入循环骨架
                  </a-button>
                  <a-button
                    v-for="field in group.fields"
                    :key="field.path"
                    size="small"
                    @click="insertField(field)"
                  >
                    {{ field.label }}
                  </a-button>
                </a-space>
              </a-collapse-panel>
            </a-collapse>
          </aside>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.template-layout,
.editor-grid,
.editor-meta-grid {
  display: grid;
  gap: 16px;
}

.template-layout {
  grid-template-columns: minmax(300px, 0.9fr) minmax(520px, 1.5fr);
}

.version-panel,
.preview-panel {
  min-width: 0;
}

.preview-panel {
  margin-top: 16px;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.panel-heading h3,
.lg-list-table-panel h3,
.field-catalog h4 {
  margin: 0;
}

.page-tip {
  color: var(--ant-color-text-secondary);
  font-size: 12px;
}

.editor-meta-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.editor-grid {
  grid-template-columns: minmax(0, 1fr) 330px;
}

.field-catalog {
  max-height: 710px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--ant-color-border-secondary);
  border-radius: 6px;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.hidden-input {
  display: none;
}

.selected {
  font-weight: 700;
}

@media (max-width: 1100px) {
  .template-layout,
  .editor-grid,
  .editor-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
