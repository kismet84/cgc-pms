<script setup lang="ts">
import type { SiteFileRecord } from '@cgc-pms/frontend-contracts'
import { onBeforeUnmount, ref, watch } from 'vue'
import { deleteSiteFile, getSiteFileUrl, listSiteFiles, uploadSiteFile } from '@/services/delivery'
import { isApiClientError } from '@/services/request'
import V2Badge from './V2Badge.vue'
import V2Button from './V2Button.vue'
import V2ConfirmDialog from './V2ConfirmDialog.vue'

const props = withDefaults(
  defineProps<{
    businessType: string
    businessId: string
    documentType?: string
    title?: string
    canUpload?: boolean
    canDelete?: boolean
  }>(),
  {
    documentType: 'OTHER',
    title: '附件',
    canUpload: false,
    canDelete: false,
  },
)

const emit = defineEmits<{ changed: [] }>()
const files = ref<SiteFileRecord[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const pendingDelete = ref<SiteFileRecord | null>(null)
let controller: AbortController | null = null
let loadGeneration = 0

function errorText(value: unknown, fallback: string) {
  return isApiClientError(value) ? value.message : value instanceof Error ? value.message : fallback
}

async function load() {
  controller?.abort()
  pendingDelete.value = null
  const generation = ++loadGeneration
  if (!props.businessId) {
    files.value = []
    loading.value = false
    return
  }
  const requestController = new AbortController()
  controller = requestController
  loading.value = true
  error.value = ''
  try {
    const result = await listSiteFiles(
      props.businessType,
      props.businessId,
      requestController.signal,
    )
    if (generation === loadGeneration) files.value = result
  } catch (value) {
    if (generation === loadGeneration && !requestController.signal.aborted) {
      error.value = errorText(value, '附件读取失败')
    }
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await uploadSiteFile(file, props.businessType, props.businessId, props.documentType)
    await load()
    emit('changed')
  } catch (value) {
    error.value = errorText(value, '附件上传失败')
  } finally {
    busy.value = false
  }
}

async function download(file: SiteFileRecord) {
  const target = window.open('about:blank', '_blank')
  if (target) target.opener = null
  busy.value = true
  error.value = ''
  try {
    const url = await getSiteFileUrl(file.id)
    if (target) target.location.href = url
    else window.location.assign(url)
  } catch (value) {
    target?.close()
    error.value = errorText(value, '附件下载失败')
  } finally {
    busy.value = false
  }
}

async function confirmDelete() {
  if (!pendingDelete.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await deleteSiteFile(pendingDelete.value.id)
    pendingDelete.value = null
    await load()
    emit('changed')
  } catch (value) {
    error.value = errorText(value, '附件删除失败')
  } finally {
    busy.value = false
  }
}

function scanLabel(file: SiteFileRecord) {
  return file.virusScanStatus === 'CLEAN'
    ? '扫描通过'
    : file.virusScanStatus === 'INFECTED'
      ? '检测到风险'
      : file.virusScanStatus === 'FAILED'
        ? '扫描失败'
        : '等待扫描'
}

function scanTone(file: SiteFileRecord): 'success' | 'danger' | 'warning' {
  return file.virusScanStatus === 'CLEAN'
    ? 'success'
    : file.virusScanStatus === 'INFECTED'
      ? 'danger'
      : 'warning'
}

watch(() => [props.businessType, props.businessId], load, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="business-attachments" :aria-busy="loading || busy">
    <div class="business-attachments__heading">
      <h3>{{ title }}</h3>
      <label v-if="canUpload && businessId" class="business-attachments__upload">
        <span>{{ busy ? '处理中' : '上传附件' }}</span>
        <input type="file" :disabled="busy" @change="upload" />
      </label>
    </div>
    <p v-if="error" class="business-attachments__error" role="alert">
      {{ error }}
      <V2Button size="small" variant="ghost" @click="load">重试</V2Button>
    </p>
    <p v-else-if="loading">附件读取中…</p>
    <ul v-else-if="files.length" class="business-attachments__list">
      <li v-for="file in files" :key="file.id">
        <span class="business-attachments__name">{{ file.originalName }}</span>
        <V2Badge :tone="scanTone(file)">{{ scanLabel(file) }}</V2Badge>
        <V2Button
          size="small"
          variant="ghost"
          :disabled="busy || file.virusScanStatus !== 'CLEAN'"
          @click="download(file)"
          >下载</V2Button
        >
        <V2Button
          v-if="canDelete"
          size="small"
          variant="ghost"
          :disabled="busy"
          @click="pendingDelete = file"
          >删除</V2Button
        >
      </li>
    </ul>
    <p v-else>暂无附件</p>
  </section>

  <V2ConfirmDialog
    :open="Boolean(pendingDelete)"
    title="删除附件"
    :description="pendingDelete ? `确认删除附件“${pendingDelete.originalName}”？` : ''"
    confirm-text="确认删除"
    :loading="busy"
    danger
    @close="pendingDelete = null"
    @confirm="confirmDelete"
  />
</template>

<style scoped>
.business-attachments {
  display: grid;
  gap: var(--v2-space-3);
}
.business-attachments__heading,
.business-attachments__list li {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
}
.business-attachments__heading {
  justify-content: space-between;
}
.business-attachments__heading h3,
.business-attachments p {
  margin: 0;
}
.business-attachments__upload {
  cursor: pointer;
  color: var(--v2-color-primary);
  font-weight: 600;
}
.business-attachments__upload input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
.business-attachments__list {
  display: grid;
  gap: var(--v2-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.business-attachments__name {
  min-width: 0;
  flex: 1;
  overflow-wrap: anywhere;
}
.business-attachments__error {
  color: var(--v2-color-danger);
}
</style>
