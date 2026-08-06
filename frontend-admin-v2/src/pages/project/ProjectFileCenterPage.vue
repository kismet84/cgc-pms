<script setup lang="ts">
import type { DictionaryItem, ProjectContextOption } from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
  showToast,
} from '@/components'
import {
  addProjectFileVersion,
  createProjectFile,
  getProjectFileDownloadUrl,
  loadProjectFiles,
  requestProjectFilePreview,
  type ProjectFileRecord,
  type ProjectFileVersion,
} from '@/services/project-files'
import { loadProjectDictionary, loadVisibleProjects } from '@/services/projects'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const filter = reactive({ projectId: '', keyword: '', categoryCode: '', pageNo: 1, pageSize: 10 })
const records = ref<ProjectFileRecord[]>([])
const projects = ref<ProjectContextOption[]>([])
const categories = ref<DictionaryItem[]>([])
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const selectedVersions = reactive<Record<string, string>>({})
const createOpen = ref(false)
const createForm = reactive({ projectId: '', name: '', categoryCode: '' })
const createUpload = ref<File | null>(null)
const versionTarget = ref<ProjectFileRecord | null>(null)
const versionUpload = ref<File | null>(null)
let controller: AbortController | null = null
let generation = 0

const canManage = computed(() => session.hasPermission('project:file:manage'))
const projectOptions = computed(() => [
  { value: '', label: '全部项目' },
  ...projects.value.map((item) => ({ value: item.id, label: item.projectName })),
])
const createProjectOptions = computed(() =>
  projects.value.map((item) => ({ value: item.id, label: item.projectName })),
)
const categoryOptions = computed(() => [
  { value: '', label: '全部分类' },
  ...categories.value.map((item) => ({ value: item.dictValue, label: item.dictLabel })),
])
const createCategoryOptions = computed(() =>
  categories.value
    .filter((item) => item.status === 'ENABLE')
    .map((item) => ({ value: item.dictValue, label: item.dictLabel })),
)

function errorText(value: unknown, fallback: string) {
  return isApiClientError(value) ? value.message : value instanceof Error ? value.message : fallback
}

function categoryLabel(row: ProjectFileRecord) {
  return (
    row.categoryName ??
    categories.value.find((item) => item.dictValue === row.categoryCode)?.dictLabel ??
    row.categoryCode
  )
}

function versionOptions(row: ProjectFileRecord) {
  return [...row.versions]
    .sort((left, right) => right.versionNo - left.versionNo)
    .map((item) => ({ value: item.id, label: `V${item.versionNo}` }))
}

function currentVersion(row: ProjectFileRecord): ProjectFileVersion | undefined {
  return (
    row.versions.find((item) => item.id === selectedVersions[row.id]) ??
    [...row.versions].sort((left, right) => right.versionNo - left.versionNo)[0]
  )
}

function submitter(version?: ProjectFileVersion) {
  if (!version) return '—'
  const named = version.submitterName ?? version.createdByName
  if (named) return named
  return version.createdBy ? `用户#${version.createdBy}` : '历史导入'
}

function dateTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function scanLabel(version?: ProjectFileVersion) {
  if (!version || version.virusScanStatus === 'CLEAN') return ''
  return version.virusScanStatus === 'INFECTED'
    ? '检测到风险'
    : version.virusScanStatus === 'FAILED'
      ? '扫描失败'
      : '等待扫描'
}

function hydrate() {
  filter.projectId = typeof route.query.projectId === 'string' ? route.query.projectId : ''
  filter.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  filter.categoryCode = typeof route.query.categoryCode === 'string' ? route.query.categoryCode : ''
  filter.pageNo = Math.max(1, Number(route.query.pageNo) || 1)
}

async function load() {
  hydrate()
  controller?.abort()
  const current = new AbortController()
  controller = current
  const token = ++generation
  loading.value = true
  errorMessage.value = ''
  try {
    const [page, projectRows, categoryRows] = await Promise.all([
      loadProjectFiles({ ...filter }, current.signal),
      loadVisibleProjects(current.signal),
      loadProjectDictionary('file_category', current.signal),
    ])
    if (token !== generation) return
    records.value = page.records
    total.value = page.total
    projects.value = projectRows
    categories.value = categoryRows
    for (const row of page.records) {
      if (!row.versions.some((item) => item.id === selectedVersions[row.id])) {
        const latest = [...row.versions].sort((left, right) => right.versionNo - left.versionNo)[0]
        if (latest) selectedVersions[row.id] = latest.id
      }
    }
  } catch (value) {
    if (!current.signal.aborted && token === generation) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(value, '文件中心加载失败')
    }
  } finally {
    if (token === generation) loading.value = false
  }
}

async function query(pageNo = 1) {
  const target = {
    path: '/project/files',
    query: {
      projectId: filter.projectId || undefined,
      keyword: filter.keyword.trim() || undefined,
      categoryCode: filter.categoryCode || undefined,
      pageNo: pageNo > 1 ? String(pageNo) : undefined,
    },
  }
  if (router.resolve(target).fullPath === route.fullPath) {
    await load()
    return
  }
  await router.replace(target)
}

function openCreate() {
  Object.assign(createForm, {
    projectId: filter.projectId,
    name: '',
    categoryCode: createCategoryOptions.value[0]?.value ?? '',
  })
  createUpload.value = null
  createOpen.value = true
}

function fileFrom(event: Event) {
  return (event.target as HTMLInputElement).files?.[0] ?? null
}

async function create() {
  if (
    !createForm.projectId ||
    !createForm.name.trim() ||
    !createForm.categoryCode ||
    !createUpload.value
  )
    return
  busy.value = true
  errorMessage.value = ''
  try {
    await createProjectFile({ ...createForm, file: createUpload.value })
    createOpen.value = false
    await query()
    showToast('success', '文件已创建', '服务端已生成编号和 V1。')
  } catch (value) {
    errorMessage.value = errorText(value, '新建文件失败')
  } finally {
    busy.value = false
  }
}

async function appendVersion() {
  if (!versionTarget.value || !versionUpload.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    await addProjectFileVersion(versionTarget.value.id, versionUpload.value)
    versionTarget.value = null
    await load()
    showToast('success', '新版本已上传', '列表已回读服务端版本。')
  } catch (value) {
    errorMessage.value = errorText(value, '新版本上传失败')
  } finally {
    busy.value = false
  }
}

function openVersionUpload(row: ProjectFileRecord) {
  versionTarget.value = row
  versionUpload.value = null
}

function sleep(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(resolve, milliseconds)
    signal.addEventListener(
      'abort',
      () => {
        window.clearTimeout(timer)
        reject(new DOMException('Aborted', 'AbortError'))
      },
      { once: true },
    )
  })
}

async function preview(row: ProjectFileRecord) {
  const version = currentVersion(row)
  if (!version || version.virusScanStatus !== 'CLEAN') return
  const target = window.open('about:blank', '_blank')
  if (target) target.opener = null
  const previewController = new AbortController()
  const deadline = Date.now() + 30_000
  busy.value = true
  errorMessage.value = ''
  try {
    let result = await requestProjectFilePreview(version.id, previewController.signal)
    while (result.status === 'PROCESSING' && Date.now() < deadline) {
      await sleep(Math.max(1, result.retryAfterSeconds ?? 2) * 1_000, previewController.signal)
      result = await requestProjectFilePreview(version.id, previewController.signal)
    }
    if (result.status === 'READY' && result.url) {
      if (target) target.location.href = result.url
      else window.location.assign(result.url)
      return
    }
    throw new Error(
      result.message ??
        (result.status === 'UNSUPPORTED'
          ? '该格式不支持在线预览'
          : result.status === 'PROCESSING'
            ? '预览仍在生成，请稍后重试'
            : '预览生成失败'),
    )
  } catch (value) {
    target?.close()
    errorMessage.value = errorText(value, '文件预览失败')
  } finally {
    previewController.abort()
    busy.value = false
  }
}

async function download(row: ProjectFileRecord) {
  const version = currentVersion(row)
  if (!version || version.virusScanStatus !== 'CLEAN') return
  const target = window.open('about:blank', '_blank')
  if (target) target.opener = null
  busy.value = true
  errorMessage.value = ''
  try {
    const url = await getProjectFileDownloadUrl(version.sysFileId)
    if (target) target.location.href = url
    else window.location.assign(url)
  } catch (value) {
    target?.close()
    errorMessage.value = errorText(value, '文件下载失败')
  } finally {
    busy.value = false
  }
}

watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <div class="project-file-center">
    <V2Card title="文件中心" :heading-level="1">
      <template #actions>
        <div class="filters">
          <V2Select v-model="filter.projectId" label="项目" :options="projectOptions" allow-empty />
          <V2Input v-model="filter.keyword" label="编号或名称" type="search" />
          <V2Select
            v-model="filter.categoryCode"
            label="分类"
            :options="categoryOptions"
            allow-empty
          />
          <V2Button size="small" variant="secondary" :loading="loading" @click="query()">
            查询
          </V2Button>
          <V2Button v-if="canManage" size="small" @click="openCreate">新建文件</V2Button>
        </div>
      </template>
    </V2Card>

    <p v-if="errorMessage" class="error" role="alert">
      {{ errorMessage }}
      <V2Button size="small" variant="ghost" @click="load">重试</V2Button>
    </p>

    <V2Card>
      <V2PageState
        v-if="loading && !records.length"
        title="正在加载文件"
        description="正在读取有权项目的文件目录。"
        kind="loading"
      />
      <V2PageState
        v-else-if="!errorMessage && !records.length"
        title="暂无文件"
        description="当前筛选条件下没有可访问文件。"
        kind="empty"
      />
      <div v-else class="table-wrap" role="region" aria-label="项目文件列表" tabindex="0">
        <table>
          <caption class="v2-visually-hidden">
            项目文件列表
          </caption>
          <thead>
            <tr>
              <th scope="col">编号</th>
              <th scope="col">项目</th>
              <th scope="col">名称</th>
              <th scope="col">分类</th>
              <th scope="col">版本</th>
              <th scope="col">提交人</th>
              <th scope="col">最后更新日期</th>
              <th scope="col">维护方式</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in records" :key="row.id">
              <th scope="row">
                <V2Button
                  size="small"
                  variant="ghost"
                  :disabled="busy || currentVersion(row)?.virusScanStatus !== 'CLEAN'"
                  @click="preview(row)"
                  >{{ row.fileCode }}</V2Button
                >
              </th>
              <td>{{ row.projectName || '—' }}</td>
              <td>{{ row.displayName }}</td>
              <td>{{ categoryLabel(row) }}</td>
              <td>
                <V2Select
                  v-model="selectedVersions[row.id]"
                  class="project-file-center__version-select"
                  :label="`${row.fileCode}版本`"
                  hide-label
                  :options="versionOptions(row)"
                />
              </td>
              <td>{{ submitter(currentVersion(row)) }}</td>
              <td>{{ dateTime(currentVersion(row)?.createdAt) }}</td>
              <td>
                {{
                  row.maintainMode === 'MANAGED'
                    ? '文件中心维护'
                    : row.sourceHint || '由原业务模块维护'
                }}
              </td>
              <td class="actions">
                <span v-if="scanLabel(currentVersion(row))" class="scan-warning">
                  {{ scanLabel(currentVersion(row)) }}
                </span>
                <V2Button
                  size="small"
                  variant="secondary"
                  :disabled="busy || currentVersion(row)?.virusScanStatus !== 'CLEAN'"
                  @click="download(row)"
                  >下载</V2Button
                >
                <V2Button
                  v-if="canManage && row.maintainMode === 'MANAGED'"
                  size="small"
                  variant="ghost"
                  :disabled="busy"
                  @click="openVersionUpload(row)"
                  >上传新版本</V2Button
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <V2Pagination
          :total="total"
          :page-no="filter.pageNo"
          :page-size="filter.pageSize"
          label="项目文件分页"
          :disabled="loading"
          @update:page-no="query"
        />
      </template>
    </V2Card>
  </div>

  <V2Dialog :open="createOpen" title="新建文件" :close-disabled="busy" @close="createOpen = false">
    <form id="project-file-create" class="form" @submit.prevent="create">
      <V2Select
        v-model="createForm.projectId"
        label="项目"
        :options="createProjectOptions"
        required
      />
      <V2Input v-model="createForm.name" label="名称" required />
      <V2Select
        v-model="createForm.categoryCode"
        label="分类"
        :options="createCategoryOptions"
        required
      />
      <label class="file-field"
        >文件<input type="file" required @change="createUpload = fileFrom($event)"
      /></label>
    </form>
    <template #footer>
      <V2Button variant="secondary" :disabled="busy" @click="createOpen = false">取消</V2Button>
      <V2Button type="submit" form="project-file-create" :loading="busy">创建</V2Button>
    </template>
  </V2Dialog>

  <V2Dialog
    :open="Boolean(versionTarget)"
    title="上传新版本"
    :description="versionTarget?.displayName"
    :close-disabled="busy"
    @close="versionTarget = null"
  >
    <form id="project-file-version" @submit.prevent="appendVersion">
      <label class="file-field"
        >文件<input type="file" required @change="versionUpload = fileFrom($event)"
      /></label>
    </form>
    <template #footer>
      <V2Button variant="secondary" :disabled="busy" @click="versionTarget = null">取消</V2Button>
      <V2Button type="submit" form="project-file-version" :loading="busy">上传</V2Button>
    </template>
  </V2Dialog>
</template>

<style scoped>
.project-file-center {
  display: grid;
  gap: var(--v2-space-4);
}
.filters,
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: var(--v2-space-2);
}
.table-wrap {
  overflow-x: auto;
}
.project-file-center__version-select :deep(.v2-field__control) {
  height: var(--v2-control-height-sm);
  min-height: var(--v2-control-height-sm);
}
table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}
th,
td {
  padding: var(--v2-space-3);
  text-align: left;
  border-bottom: 1px solid var(--v2-color-border);
}
.form {
  display: grid;
  gap: var(--v2-space-3);
}
.file-field {
  display: grid;
  gap: var(--v2-space-2);
  font-weight: 600;
}
.error {
  color: var(--v2-color-danger);
}
.scan-warning {
  color: var(--v2-color-danger);
  white-space: nowrap;
}
</style>
