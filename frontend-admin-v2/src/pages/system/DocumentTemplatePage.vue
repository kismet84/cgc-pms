<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Badge, V2Button, V2Card, V2ConfirmDialog, V2PageState, showToast } from '@/components'
import {
  bindDefaultDocumentVersion,
  disableDocumentVersion,
  enableDocumentVersion,
  installAllSystemDocumentTemplates,
  installSystemDocumentTemplate,
  loadDocumentBusinessTypes,
  loadDocumentTemplate,
  loadDocumentTemplates,
  loadSystemDocumentTemplateStatuses,
  previewDocumentTemplateVersionHtml,
  publishDocumentVersion,
  type DocumentBusinessTypeOption,
  type DocumentTemplateDetail,
  type DocumentTemplateSummary,
  type DocumentTemplateVersion,
  type SystemDocumentTemplateStatus,
} from '@/services/system-management'
import { useSessionStore } from '@/stores/session'
import { workflowModule } from '@/pages/system/workflow-business-modules'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const loading = ref(true)
const detailLoading = ref(false)
const installing = ref('')
const error = ref('')
const search = ref('')
const businessTypes = ref<DocumentBusinessTypeOption[]>([])
const templates = ref<DocumentTemplateSummary[]>([])
const statuses = ref<SystemDocumentTemplateStatus[]>([])
const detail = ref<DocumentTemplateDetail>()
const businessType = ref(String(route.query.businessType ?? ''))
const selectedTemplateId = ref(String(route.query.templateId ?? ''))
const selectedVersionId = ref(String(route.query.versionId ?? ''))
const previewHtml = ref('')
const previewLoading = ref(false)
const previewError = ref('')
const pendingAction = ref<'installAll' | 'publish' | 'disable' | 'enable' | 'default'>()
const actionLoading = ref(false)
let previewRequest = 0

const canEdit = computed(() => session.hasAdminOrPermission('document:template:edit'))
const canPublish = computed(() => session.hasAdminOrPermission('document:template:publish'))
const selectedVersion = computed(() =>
  detail.value?.versions.find((item) => item.id === selectedVersionId.value),
)
const selectedStatus = computed(() =>
  statuses.value.find((item) => item.businessType === businessType.value),
)
const selectedBusinessName = computed(
  () =>
    businessTypes.value.find((item) => item.businessType === businessType.value)?.displayName ??
    '业务单据',
)
const confirmDescription = computed(() =>
  pendingAction.value === 'installAll'
    ? '将在当前租户校验并安装全部 28 类系统模板。任一定义失败时，本次安装全部回滚。'
    : actionConfirm(pendingAction.value ?? ''),
)
const filteredBusinessGroups = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  const groups = new Map<
    string,
    {
      key: string
      label: string
      rows: Array<{
        business: DocumentBusinessTypeOption
        templates: DocumentTemplateSummary[]
        status?: SystemDocumentTemplateStatus
      }>
    }
  >()
  for (const business of businessTypes.value) {
    const rows = templates.value.filter((item) => item.businessType === business.businessType)
    const searchable = [
      business.displayName,
      business.businessType,
      ...rows.flatMap((item) => [item.templateName, item.templateCode]),
    ]
      .join(' ')
      .toLowerCase()
    if (keyword && !searchable.includes(keyword)) continue
    const module = workflowModule(business.businessType)
    const group = groups.get(module.key) ?? { ...module, rows: [] }
    group.rows.push({
      business,
      templates: rows,
      status: statuses.value.find((item) => item.businessType === business.businessType),
    })
    groups.set(module.key, group)
  }
  return [...groups.values()]
})

onMounted(load)
onBeforeUnmount(() => {
  previewRequest += 1
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [types, allTemplates, systemStatuses] = await Promise.all([
      loadDocumentBusinessTypes(),
      loadDocumentTemplates(''),
      loadSystemDocumentTemplateStatuses(),
    ])
    statuses.value = systemStatuses
    businessTypes.value = systemStatuses.map((status) => {
      const available = types.find((item) => item.businessType === status.businessType)
      return (
        available ?? {
          businessType: status.businessType,
          displayName: status.templateName,
          schemaVersion: status.schemaVersion,
          providerReady: true,
          fieldCount: 0,
        }
      )
    })
    templates.value = allTemplates
    if (!businessTypes.value.some((item) => item.businessType === businessType.value)) {
      businessType.value = businessTypes.value[0]?.businessType ?? ''
    }
    const candidates = templates.value.filter((item) => item.businessType === businessType.value)
    if (!candidates.some((item) => item.id === selectedTemplateId.value)) {
      selectedTemplateId.value = candidates[0]?.id ?? ''
    }
    if (selectedTemplateId.value)
      await selectTemplate(selectedTemplateId.value, selectedVersionId.value)
    else {
      detail.value = undefined
      selectedVersionId.value = ''
      syncUrl()
    }
  } catch (value) {
    error.value = messageOf(value)
  } finally {
    loading.value = false
  }
}

async function refreshPage(): Promise<void> {
  await load()
  if (!error.value) showToast('success', '业务单据模板已刷新')
}

async function selectBusiness(type: string): Promise<void> {
  businessType.value = type
  const candidates = templates.value.filter((item) => item.businessType === type)
  selectedTemplateId.value = candidates[0]?.id ?? ''
  selectedVersionId.value = ''
  if (selectedTemplateId.value) await selectTemplate(selectedTemplateId.value)
  else {
    detail.value = undefined
    previewHtml.value = ''
    syncUrl()
  }
}

async function selectTemplate(id: string, preferredVersionId = ''): Promise<void> {
  detailLoading.value = true
  try {
    detail.value = await loadDocumentTemplate(id)
    selectedTemplateId.value = id
    selectedVersionId.value =
      detail.value.versions.find((item) => item.id === preferredVersionId)?.id ??
      detail.value.versions[0]?.id ??
      ''
    syncUrl()
    await loadPreview()
  } catch (value) {
    showToast('error', '模板详情加载失败', messageOf(value))
  } finally {
    detailLoading.value = false
  }
}

async function selectVersion(id: string): Promise<void> {
  selectedVersionId.value = id
  syncUrl()
  await loadPreview()
}

async function loadPreview(): Promise<void> {
  const version = selectedVersion.value
  const request = ++previewRequest
  previewHtml.value = ''
  previewError.value = ''
  if (!version) return
  previewLoading.value = true
  try {
    const result = await previewDocumentTemplateVersionHtml(version.id)
    if (request === previewRequest) previewHtml.value = result.html
  } catch (value) {
    if (request === previewRequest) previewError.value = messageOf(value)
  } finally {
    if (request === previewRequest) previewLoading.value = false
  }
}

function syncUrl(): void {
  router.replace({
    query: {
      ...route.query,
      businessType: businessType.value || undefined,
      templateId: selectedTemplateId.value || undefined,
      versionId: selectedVersionId.value || undefined,
    },
  })
}

function createTemplate(): void {
  router.push({
    path: '/system/document-templates/new',
    query: { businessType: businessType.value },
  })
}

function createVersion(): void {
  if (!detail.value) return
  router.push({
    path: `/system/document-templates/${detail.value.template.id}/versions/new`,
    query: { sourceVersionId: selectedVersionId.value },
  })
}

function editDraft(): void {
  if (!detail.value || !selectedVersion.value) return
  router.push(
    `/system/document-templates/${detail.value.template.id}/versions/${selectedVersion.value.id}/edit`,
  )
}

async function installOne(): Promise<void> {
  if (!businessType.value) return
  installing.value = businessType.value
  try {
    const result = await installSystemDocumentTemplate(businessType.value)
    showToast('success', installMessage(result.action, result.bindingAction))
    businessType.value = result.businessType
    selectedTemplateId.value = result.templateId
    selectedVersionId.value = result.versionId
    await load()
  } catch (value) {
    showToast('error', '系统模板安装失败', messageOf(value))
  } finally {
    installing.value = ''
  }
}

async function installAll(): Promise<void> {
  installing.value = 'ALL'
  try {
    const results = await installAllSystemDocumentTemplates()
    const changed = results.filter((item) => item.action !== 'UNCHANGED').length
    const preserved = results.filter((item) => item.bindingAction === 'PRESERVED_CUSTOM').length
    showToast(
      'success',
      `28 类模板校验完成，新增或升级 ${changed} 类；保留自定义默认 ${preserved} 类`,
    )
    await load()
  } catch (value) {
    showToast('error', '安装全部失败，当前事务已回滚', messageOf(value))
  } finally {
    installing.value = ''
  }
}

async function versionAction(kind: 'publish' | 'disable' | 'enable' | 'default'): Promise<void> {
  const version = selectedVersion.value
  if (!version || !detail.value) return
  try {
    if (kind === 'publish') await publishDocumentVersion(version.id)
    if (kind === 'disable') await disableDocumentVersion(version.id)
    if (kind === 'enable') await enableDocumentVersion(version.id)
    if (kind === 'default') {
      await bindDefaultDocumentVersion(version.id, detail.value.defaultBinding?.lockVersion ?? 0)
    }
    showToast('success', '版本状态已更新')
    await load()
  } catch (value) {
    showToast('error', '版本操作失败', messageOf(value))
  }
}

function requestAction(kind: 'installAll' | 'publish' | 'disable' | 'enable' | 'default'): void {
  pendingAction.value = kind
}

async function confirmAction(): Promise<void> {
  const action = pendingAction.value
  if (!action) return
  actionLoading.value = true
  try {
    if (action === 'installAll') await installAll()
    else await versionAction(action)
    pendingAction.value = undefined
  } finally {
    actionLoading.value = false
  }
}

function actionConfirm(kind: string): string {
  return (
    {
      publish: '发布后版本不可修改，确定发布吗？',
      disable: '确定停用此已发布版本吗？',
      enable: '确定重新启用此版本吗？',
      default: '确定将此版本设为当前业务默认吗？',
    }[kind] ?? '确定继续吗？'
  )
}

function installMessage(action: string, binding: string): string {
  const actionText =
    { CREATED: '系统模板已创建', UPGRADED: '系统模板已升级', UNCHANGED: '系统模板已是最新' }[
      action
    ] ?? action
  const bindingText =
    binding === 'PRESERVED_CUSTOM' ? '；租户自定义默认保持不变' : '；系统默认已更新'
  return actionText + bindingText
}

function versionStatusLabel(status: DocumentTemplateVersion['status']): string {
  return { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' }[status]
}

function messageOf(value: unknown): string {
  return value instanceof Error ? value.message : '请求失败'
}
</script>

<template>
  <section class="template-page">
    <V2Card title="业务单据模板" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" :disabled="loading" @click="refreshPage"
          >刷新</V2Button
        >
        <V2Button
          v-if="canPublish"
          size="small"
          variant="secondary"
          :loading="installing === 'ALL'"
          @click="requestAction('installAll')"
          >安装全部</V2Button
        >
        <V2Button v-if="canEdit" size="small" :disabled="!businessType" @click="createTemplate"
          >新建模板</V2Button
        >
      </template>
    </V2Card>
    <p class="page-summary">28 类正式工程单据，显式安装、追加升级、租户默认安全保留。</p>

    <V2PageState
      v-if="loading"
      title="正在加载业务单据模板"
      description="正在读取业务类型、系统目录和租户模板。"
    />
    <V2PageState v-else-if="error" title="模板平台加载失败" :description="error">
      <template #actions><V2Button @click="load">重试</V2Button></template>
    </V2PageState>

    <div v-else class="template-workbench">
      <aside class="template-nav">
        <div class="search-box">
          <input v-model="search" placeholder="搜索业务、模板名称或编码" />
        </div>
        <div class="nav-scroll">
          <section v-for="group in filteredBusinessGroups" :key="group.key" class="module-group">
            <h2>{{ group.label }}</h2>
            <div v-for="row in group.rows" :key="row.business.businessType" class="business-group">
              <button
                type="button"
                class="business-row"
                :class="{ active: row.business.businessType === businessType }"
                @click="selectBusiness(row.business.businessType)"
              >
                <span
                  ><strong>{{ row.business.displayName }}</strong></span
                >
                <V2Badge
                  :tone="
                    row.status?.current ? 'success' : row.status?.installed ? 'warning' : 'neutral'
                  "
                >
                  {{ row.status?.current ? '最新' : row.status?.installed ? '待升级' : '未安装' }}
                </V2Badge>
              </button>
              <button
                v-for="template in row.templates"
                :key="template.id"
                type="button"
                class="template-row"
                :class="{ active: template.id === selectedTemplateId }"
                @click="selectTemplate(template.id)"
              >
                <span>{{ template.templateName }}</span
                ><small>{{ template.templateCode }}</small>
              </button>
            </div>
          </section>
          <p v-if="!filteredBusinessGroups.length" class="empty-nav">没有匹配的业务或模板。</p>
        </div>
      </aside>

      <div class="template-detail">
        <div class="detail-toolbar">
          <div>
            <p>{{ selectedBusinessName }}</p>
            <h2>
              {{ detail?.template.templateName || selectedStatus?.templateName || '尚未安装模板' }}
            </h2>
            <span v-if="detail">{{ detail.template.templateCode }}</span>
          </div>
          <div class="detail-actions">
            <V2Button
              v-if="canPublish"
              variant="secondary"
              :loading="installing === businessType"
              @click="installOne"
            >
              {{ selectedStatus?.installed ? '检查升级' : '安装系统模板' }}
            </V2Button>
            <V2Button
              v-if="canEdit && detail && selectedVersion?.status === 'DRAFT'"
              @click="editDraft"
              >编辑草稿</V2Button
            >
            <V2Button v-else-if="canEdit && detail" @click="createVersion">创建新版</V2Button>
          </div>
        </div>

        <div v-if="!detail" class="no-template">
          <strong>当前业务暂无租户模板</strong>
          <span>可安装受版本控制的系统模板，或新建租户自定义模板。</span>
        </div>

        <div v-else class="detail-grid">
          <section class="preview-panel">
            <div class="preview-toolbar">
              <span>大幅预览</span>
              <span v-if="selectedVersion"
                >V{{ selectedVersion.versionNo }} ·
                {{ versionStatusLabel(selectedVersion.status) }}</span
              >
            </div>
            <div class="preview-stage">
              <iframe v-if="previewHtml" title="业务单据模板预览" :srcdoc="previewHtml" />
              <div v-else class="preview-state">
                {{ previewLoading ? '正在生成服务端预览…' : previewError || '选择版本查看预览' }}
              </div>
            </div>
          </section>

          <aside class="version-panel">
            <div class="status-card">
              <h3>系统模板状态</h3>
              <dl>
                <div>
                  <dt>目录状态</dt>
                  <dd>
                    {{
                      selectedStatus?.current
                        ? '已是最新'
                        : selectedStatus?.installed
                          ? '可升级'
                          : '未安装'
                    }}
                  </dd>
                </div>
                <div>
                  <dt>默认绑定</dt>
                  <dd>
                    {{
                      selectedStatus?.defaultBinding === 'CUSTOM'
                        ? '保留自定义'
                        : selectedStatus?.defaultBinding === 'SYSTEM'
                          ? '系统模板'
                          : '未绑定'
                    }}
                  </dd>
                </div>
                <div>
                  <dt>页面方向</dt>
                  <dd>{{ selectedStatus?.orientation === 'LANDSCAPE' ? 'A4 横向' : 'A4 纵向' }}</dd>
                </div>
              </dl>
            </div>
            <div class="version-list">
              <h3>版本历史</h3>
              <button
                v-for="version in detail.versions"
                :key="version.id"
                type="button"
                :class="{ active: version.id === selectedVersionId }"
                @click="selectVersion(version.id)"
              >
                <span
                  ><strong>V{{ version.versionNo }}</strong
                  ><V2Badge
                    :tone="
                      version.status === 'PUBLISHED'
                        ? 'success'
                        : version.status === 'DRAFT'
                          ? 'warning'
                          : 'neutral'
                    "
                    >{{ versionStatusLabel(version.status) }}</V2Badge
                  ></span
                >
                <small>{{ version.publishedAt || '尚未发布' }}</small>
              </button>
            </div>
            <div v-if="selectedVersion" class="version-actions">
              <V2Button
                v-if="selectedVersion.status === 'DRAFT' && canPublish"
                size="small"
                @click="requestAction('publish')"
                >发布</V2Button
              >
              <V2Button
                v-if="selectedVersion.status === 'PUBLISHED' && canPublish"
                size="small"
                variant="secondary"
                @click="requestAction('default')"
                >设为默认</V2Button
              >
              <V2Button
                v-if="selectedVersion.status === 'PUBLISHED' && canPublish"
                size="small"
                variant="secondary"
                @click="requestAction('disable')"
                >停用</V2Button
              >
              <V2Button
                v-if="selectedVersion.status === 'DISABLED' && canPublish"
                size="small"
                variant="secondary"
                @click="requestAction('enable')"
                >启用</V2Button
              >
              <V2Button v-if="canEdit" size="small" variant="secondary" @click="createVersion"
                >复制为新版</V2Button
              >
            </div>
          </aside>
        </div>
        <div v-if="detailLoading" class="detail-loading">正在加载模板详情…</div>
      </div>
    </div>
    <V2ConfirmDialog
      :open="Boolean(pendingAction)"
      :title="pendingAction === 'installAll' ? '确认安装全部系统模板' : '确认版本操作'"
      :description="confirmDescription"
      :confirm-text="pendingAction === 'installAll' ? '安装全部' : '确认执行'"
      :danger="pendingAction === 'disable'"
      :loading="actionLoading"
      @close="pendingAction = undefined"
      @confirm="confirmAction"
    />
  </section>
</template>

<style scoped>
.template-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.page-summary {
  margin: 0;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-13);
}
.detail-toolbar p {
  margin: 0 0 4px;
  color: var(--v2-color-primary);
  font-size: 12px;
  font-weight: 600;
}
.detail-toolbar h2 {
  margin: 0;
  color: var(--v2-color-text-strong);
}
.detail-toolbar span {
  color: var(--v2-color-text-secondary);
  font-size: 13px;
}
.detail-actions,
.version-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.template-workbench {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  height: calc(100vh - 210px);
  min-height: 620px;
  border: 1px solid var(--v2-color-border);
  background: var(--v2-color-surface);
  overflow: hidden;
}
.template-nav {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-right: 1px solid var(--v2-color-border);
}
.search-box {
  padding: 14px;
  border-bottom: 1px solid var(--v2-color-border-subtle);
}
.search-box input {
  box-sizing: border-box;
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--v2-color-border);
  border-radius: 4px;
}
.nav-scroll {
  overflow: auto;
  padding: 8px 0 24px;
}
.module-group h2 {
  margin: 14px 16px 6px;
  color: var(--v2-color-text-secondary);
  font-size: 12px;
}
.business-row,
.template-row {
  display: flex;
  width: 100%;
  border: 0;
  background: none;
  cursor: pointer;
  text-align: left;
}
.business-row {
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
}
.business-row span,
.template-row {
  min-width: 0;
}
.business-row strong,
.template-row span,
.template-row small {
  display: block;
}
.template-row small {
  margin-top: 2px;
  color: var(--v2-color-text-muted);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.business-row.active {
  background: var(--v2-color-primary-soft);
  color: var(--v2-color-primary);
}
.template-row {
  flex-direction: column;
  padding: 8px 18px 8px 32px;
  color: var(--v2-color-text-secondary);
}
.template-row.active {
  background: var(--v2-color-surface-subtle);
  color: var(--v2-color-text-strong);
  border-left: 3px solid var(--v2-color-primary);
}
.empty-nav {
  padding: 24px;
  color: var(--v2-color-text-muted);
}
.template-detail {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
.detail-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--v2-color-border);
}
.detail-toolbar h2 {
  font-size: 18px;
}
.no-template {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
  color: var(--v2-color-text-secondary);
}
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  min-height: 0;
  flex: 1;
}
.preview-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--v2-color-canvas);
  overflow: hidden;
}
.preview-toolbar {
  display: flex;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--v2-color-surface);
  border-bottom: 1px solid var(--v2-color-border);
  color: var(--v2-color-text-secondary);
  font-size: 13px;
}
.preview-stage {
  display: flex;
  justify-content: center;
  min-height: 0;
  overflow: auto;
  padding: 24px;
}
.preview-stage iframe {
  width: min(100%, 780px);
  height: 100%;
  min-height: 900px;
  border: 0;
  background: var(--v2-color-surface);
  box-shadow: var(--v2-shadow-float);
}
.preview-state {
  margin: auto;
  color: var(--v2-color-text-secondary);
}
.version-panel {
  overflow: auto;
  border-left: 1px solid var(--v2-color-border);
  padding: 16px;
}
.version-panel h3 {
  margin: 0 0 12px;
  font-size: 14px;
}
.status-card {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--v2-color-border-subtle);
}
.status-card dl {
  margin: 0;
}
.status-card dl div {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
}
.status-card dt {
  color: var(--v2-color-text-secondary);
}
.status-card dd {
  margin: 0;
  color: var(--v2-color-text-strong);
}
.version-list {
  padding: 16px 0;
}
.version-list button {
  width: 100%;
  padding: 10px;
  margin-bottom: 8px;
  border: 1px solid var(--v2-color-border-subtle);
  border-radius: 4px;
  background: var(--v2-color-surface);
  text-align: left;
  cursor: pointer;
}
.version-list button.active {
  border-color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}
.version-list button span {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.version-list small {
  display: block;
  margin-top: 4px;
  color: var(--v2-color-text-muted);
}
.detail-loading {
  position: absolute;
  inset: 64px 0 0;
  display: grid;
  place-items: center;
  background: var(--v2-dialog-surface);
  color: var(--v2-color-text-secondary);
}
@media (max-width: 1180px) {
  .template-workbench {
    grid-template-columns: 280px minmax(0, 1fr);
  }
  .detail-grid {
    grid-template-columns: minmax(0, 1fr) 240px;
  }
}
</style>
