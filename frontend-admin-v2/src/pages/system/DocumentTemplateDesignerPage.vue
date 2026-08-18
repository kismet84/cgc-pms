<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { V2Button, V2Card, V2PageState, showToast } from '@/components'
import DocumentFlowDesigner from '@/components/document/DocumentFlowDesigner.vue'
import {
  createDocumentTemplate,
  createDocumentVersion,
  loadDocumentBusinessTypes,
  loadDocumentFieldCatalog,
  loadDocumentTemplate,
  previewDocumentTemplateHtml,
  updateDocumentVersion,
  type DocumentDesignSchema,
  type DocumentFieldCatalog,
  type DocumentTemplateDetail,
  type DocumentTemplateVersion,
} from '@/services/system-management'
import {
  blankDocumentDesign,
  convertDocumentDesignToFlow,
  convertLegacyDocumentDesign,
} from './documentTemplateSchema'

type Mode = 'create' | 'version' | 'edit'
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const catalog = ref<DocumentFieldCatalog>()
const detail = ref<DocumentTemplateDetail>()
const design = ref<DocumentDesignSchema>(blankDocumentDesign(''))
const previewHtml = ref('')
const previewError = ref('')
const previewLoading = ref(false)
const valid = ref(true)
const baseline = ref('')
let previewTimer: ReturnType<typeof setTimeout> | undefined
let previewRequest = 0

const form = reactive({ templateName: '', remark: '', previewBusinessId: '' })
const templateId = computed(() => String(route.params.templateId ?? ''))
const versionId = computed(() => String(route.params.versionId ?? ''))
const sourceVersionId = computed(() => String(route.query.sourceVersionId ?? ''))
const businessType = ref(String(route.query.businessType ?? ''))
const mode = computed<Mode>(() =>
  versionId.value ? 'edit' : templateId.value ? 'version' : 'create',
)
const currentVersion = computed(() =>
  detail.value?.versions.find((item) => item.id === versionId.value),
)
const immutable = computed(() => mode.value === 'edit' && currentVersion.value?.status !== 'DRAFT')
const dirty = computed(() => snapshot() !== baseline.value)
const pageTitle = computed(() =>
  mode.value === 'create'
    ? '新建业务单据模板'
    : mode.value === 'version'
      ? '创建模板版本'
      : '编辑模板草稿',
)

onMounted(load)
onBeforeUnmount(() => {
  if (previewTimer) clearTimeout(previewTimer)
  window.removeEventListener('beforeunload', beforeUnload)
})
window.addEventListener('beforeunload', beforeUnload)
onBeforeRouteLeave(() => !dirty.value || globalThis.confirm('存在未保存修改，确定离开设计器吗？'))

watch(design, schedulePreview, { deep: true })
watch(() => form.previewBusinessId, schedulePreview)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    if (templateId.value) {
      detail.value = await loadDocumentTemplate(templateId.value)
      businessType.value = detail.value.template.businessType
      form.templateName = detail.value.template.templateName
    } else if (!businessType.value) {
      businessType.value =
        (await loadDocumentBusinessTypes()).find((item) => item.providerReady)?.businessType ?? ''
    }
    if (!businessType.value) throw new Error('缺少业务类型')
    catalog.value = await loadDocumentFieldCatalog(businessType.value)
    const source =
      mode.value === 'edit'
        ? detail.value?.versions.find((item) => item.id === versionId.value)
        : detail.value?.versions.find((item) => item.id === sourceVersionId.value)
    if (source) applySource(source)
    else {
      design.value = blankDocumentDesign(catalog.value.schemaVersion)
      form.templateName ||= `${catalog.value.displayName || businessType.value}单据模板`
    }
    baseline.value = snapshot()
    schedulePreview()
  } catch (value) {
    error.value = value instanceof Error ? value.message : '设计器加载失败'
  } finally {
    loading.value = false
  }
}

function applySource(version: DocumentTemplateVersion): void {
  form.remark = version.remark ?? ''
  if (version.designSchema) {
    try {
      design.value = convertDocumentDesignToFlow(
        JSON.parse(version.designSchema) as DocumentDesignSchema,
      )
      return
    } catch {
      // Fall through to the fail-closed legacy conversion path.
    }
  }
  design.value = convertDocumentDesignToFlow(
    convertLegacyDocumentDesign({
      version,
      schemaVersion: catalog.value!.schemaVersion,
      templateName: form.templateName,
      catalogFields: catalog.value!.fields,
    }).designSchema,
  )
  showToast('info', '旧模板已转换为 v2 流式草稿，请预览确认')
}

function schedulePreview(): void {
  if (loading.value || !businessType.value) return
  if (previewTimer) clearTimeout(previewTimer)
  const request = ++previewRequest
  previewTimer = setTimeout(async () => {
    previewLoading.value = true
    previewError.value = ''
    try {
      const result = await previewDocumentTemplateHtml({
        businessType: businessType.value,
        designSchema: JSON.stringify(design.value),
        businessId: form.previewBusinessId || undefined,
      })
      if (request === previewRequest) previewHtml.value = result.html
    } catch (value) {
      if (request === previewRequest) {
        previewHtml.value = ''
        previewError.value = value instanceof Error ? value.message : '预览生成失败'
      }
    } finally {
      if (request === previewRequest) previewLoading.value = false
    }
  }, 250)
}

async function save(): Promise<void> {
  if (!catalog.value || !valid.value || immutable.value) return
  saving.value = true
  try {
    const draft = {
      schemaVersion: catalog.value.schemaVersion,
      designSchema: JSON.stringify(design.value),
      remark: form.remark,
    }
    let saved: DocumentTemplateVersion
    if (mode.value === 'create') {
      if (!form.templateName.trim()) throw new Error('模板名称不能为空')
      saved = await createDocumentTemplate({
        ...draft,
        templateName: form.templateName.trim(),
        businessType: businessType.value,
      })
    } else if (mode.value === 'version') {
      saved = await createDocumentVersion(templateId.value, draft)
    } else {
      await updateDocumentVersion(versionId.value, draft)
      saved = currentVersion.value!
    }
    baseline.value = snapshot()
    showToast('success', '模板草稿已保存')
    await router.replace({
      path: '/system/document-templates',
      query: {
        businessType: businessType.value,
        templateId: saved.templateId,
        versionId: saved.id,
      },
    })
  } catch (value) {
    showToast('error', '保存失败', value instanceof Error ? value.message : '请检查模板内容')
  } finally {
    saving.value = false
  }
}

function back(): void {
  router.push({
    path: '/system/document-templates',
    query: {
      businessType: businessType.value,
      templateId: templateId.value || undefined,
      versionId: versionId.value || sourceVersionId.value || undefined,
    },
  })
}

function beforeUnload(event: BeforeUnloadEvent): void {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

function snapshot(): string {
  return JSON.stringify({ form, design: design.value })
}
</script>

<template>
  <section class="designer-page">
    <V2Card
      :title="pageTitle"
      :heading-level="1"
      leading-action-label="返回模板管理"
      @leading-action="back"
    >
      <template #actions>
        <V2Button
          size="small"
          variant="secondary"
          :disabled="previewLoading"
          @click="schedulePreview"
          >预览</V2Button
        >
        <V2Button size="small" :loading="saving" :disabled="!valid || immutable" @click="save"
          >保存</V2Button
        >
      </template>
    </V2Card>
    <p class="designer-summary">流式区块按文档顺序自然排版，服务端实时编译预览。</p>
    <div class="designer-identity">
      <label>模板名称<input v-model="form.templateName" :disabled="mode !== 'create'" /></label>
      <label>业务名称<input :value="catalog?.displayName ?? ''" disabled /></label>
      <label>样例业务 ID<input v-model="form.previewBusinessId" placeholder="可选" /></label>
    </div>

    <V2PageState v-if="loading" title="正在加载设计器" description="正在读取字段目录与模板草稿。" />
    <V2PageState v-else-if="error" title="设计器加载失败" :description="error">
      <template #actions><V2Button @click="load">重试</V2Button></template>
    </V2PageState>
    <template v-else>
      <div v-if="immutable" class="immutable-banner">
        已发布版本不可修改；请从管理页创建新版本。
      </div>
      <DocumentFlowDesigner
        v-model="design"
        :fields="catalog?.fields ?? []"
        :preview-html="previewHtml"
        :preview-loading="previewLoading"
        :preview-error="previewError"
        :disabled="immutable"
        @update:valid="valid = $event"
      />
      <div class="designer-remark">
        <label>版本说明<input v-model="form.remark" placeholder="记录本次版式调整" /></label>
      </div>
    </template>
  </section>
</template>

<style scoped>
.designer-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.designer-summary {
  margin: 0;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-13);
}
.designer-identity {
  display: grid;
  grid-template-columns: 1.4fr 1fr 0.8fr;
  gap: 10px;
  padding: 12px 16px;
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
}
.designer-identity label,
.designer-remark label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  color: var(--v2-color-text-secondary);
  font-size: 12px;
}
.designer-identity input,
.designer-remark input {
  height: 34px;
  box-sizing: border-box;
  padding: 0 10px;
  border: 1px solid var(--v2-color-border);
  border-radius: 4px;
  background: var(--v2-color-surface);
}
.immutable-banner {
  padding: 10px 14px;
  border: 1px solid var(--v2-color-warning);
  background: var(--v2-color-warning-soft);
  color: var(--v2-color-warning-text);
}
.designer-remark {
  padding: 12px 16px;
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
}
.designer-remark input {
  max-width: 720px;
}
@media (max-width: 900px) {
  .designer-identity {
    grid-template-columns: 1fr;
  }
}
</style>
