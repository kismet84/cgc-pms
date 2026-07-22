<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type {
  DailyProgressCommand,
  SiteDailyLogCommand,
  SiteDailyLogRecord,
  SiteDailyLogStatus,
  SiteDailyQualitySafetyRecord,
} from '@cgc-pms/frontend-contracts'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2GlassButton,
  V2Input,
  V2PageState,
  V2Select,
} from '@/components'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import {
  createSiteDailyLog,
  deleteSiteFile,
  getSiteFileUrl,
  listSiteFiles,
  loadDailyProgress,
  loadSiteDailyLog,
  loadSiteDailyLogs,
  loadSiteDailyQualitySafety,
  replaceDailyProgress,
  submitSiteDailyLog,
  updateSiteDailyLog,
  uploadSiteFile,
} from '@/services/delivery'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

const SITE_DAILY_LOG = 'SITE_DAILY_LOG'

interface DailyProgressRow extends DailyProgressCommand {
  key: string
  taskCode: string
  taskName: string
  included: boolean
}

type PendingDailyAction =
  | { kind: 'submit'; record: SiteDailyLogRecord }
  | { kind: 'file'; recordId: string; requestId: number; fileId: string; fileName: string }

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const loading = ref(false)
const saving = ref(false)
const progressSaving = ref(false)
const pendingDailyAction = ref<PendingDailyAction | null>(null)
const filesLoading = ref(false)
const qualityLoading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const qualityError = ref('')
const records = ref<SiteDailyLogRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const dialogOpen = ref(false)
const dialogMode = ref<'create' | 'edit' | 'view'>('view')
const activeRecord = ref<SiteDailyLogRecord | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const progressRows = ref<DailyProgressRow[]>([])
const files = ref<Array<{ id: string; originalName: string }>>([])
const qualityFacts = ref<SiteDailyQualitySafetyRecord[]>([])
let listController: AbortController | null = null
let detailController: AbortController | null = null
const detailRequestId = ref(0)

const filter = reactive({
  status: '',
})
const form = reactive<SiteDailyLogCommand>({
  projectId: '',
  reportDate: '',
  constructionContent: '',
  issuesDelays: '',
  nextDayPlan: '',
  weatherSummary: '',
  onSiteHeadcount: null,
  expectedUpdatedAt: undefined,
})

const projectOptions = computed(() =>
  workspace.projects.map((item) => ({ value: item.value, label: item.label })),
)
const canEdit = computed(() => hasPermission('site:daily:edit'))
const canReportProgress = computed(() => hasPermission('schedule:progress'))
const canViewQuality = computed(() => hasPermission('quality:safety:query'))
const selectedProjectId = computed(() => workspace.selectedProjectId || '')
const selectedReportPeriod = computed(() =>
  typeof route.query.period === 'string'
    ? route.query.period
    : workspace.selectedReportPeriod || '',
)
const canSubmitCurrent = computed(
  () =>
    Boolean(activeRecord.value) &&
    activeRecord.value?.status === 'DRAFT' &&
    canEdit.value &&
    (!activeRecord.value.scheduleManaged || canReportProgress.value),
)

function hasPermission(code: string): boolean {
  return (
    session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
    session.hasPermission(code)
  )
}

function message(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

function resetNotices(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function warnUnsavedDialog(): void {
  if (dialogMode.value === 'view') return
  successMessage.value = ''
  errorMessage.value = '内容尚未保存，请保存草稿、提交定稿或点击关闭。'
}

function hydrateQuery(): void {
  filter.status = typeof route.query.status === 'string' ? route.query.status : ''
  const nextPage = Number(route.query.pageNo)
  pageNo.value = Number.isInteger(nextPage) && nextPage > 0 ? nextPage : 1
}

function setQuery(): void {
  void router.replace({
    query: {
      ...(selectedProjectId.value ? { projectId: selectedProjectId.value } : {}),
      ...(selectedReportPeriod.value ? { period: selectedReportPeriod.value } : {}),
      ...(filter.status ? { status: filter.status } : {}),
      ...(pageNo.value > 1 ? { pageNo: String(pageNo.value) } : {}),
    },
    hash: route.hash,
  })
}

function resetFilters(): void {
  filter.status = ''
  search()
}

async function loadList(preserveNotice = false): Promise<void> {
  listController?.abort()
  listController = new AbortController()
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const periodBounds = reportPeriodBounds(selectedReportPeriod.value)
    const page = await loadSiteDailyLogs(
      {
        pageNo: pageNo.value,
        pageSize: pageSize.value,
        projectId: selectedProjectId.value || undefined,
        startDate: periodBounds?.startDate,
        endDate: periodBounds?.endDate,
        status: (filter.status || undefined) as SiteDailyLogStatus | undefined,
      },
      listController.signal,
    )
    records.value = page.records
    total.value = page.total
  } catch (error) {
    if (!listController.signal.aborted) {
      records.value = []
      total.value = 0
      errorMessage.value = message(error, '现场日报加载失败')
    }
  } finally {
    if (!listController.signal.aborted) loading.value = false
  }
}

function openCreate(): void {
  dialogMode.value = 'create'
  activeRecord.value = null
  qualityFacts.value = []
  qualityError.value = ''
  files.value = []
  progressRows.value = []
  Object.assign(form, {
    projectId: selectedProjectId.value,
    reportDate: new Date().toISOString().slice(0, 10),
    constructionContent: '',
    issuesDelays: '',
    nextDayPlan: '',
    weatherSummary: '',
    onSiteHeadcount: null,
    expectedUpdatedAt: undefined,
  })
  dialogOpen.value = true
  resetNotices()
}

async function openRecord(record: SiteDailyLogRecord, edit = false): Promise<void> {
  detailController?.abort()
  detailController = new AbortController()
  const requestId = ++detailRequestId.value
  dialogMode.value = edit ? 'edit' : 'view'
  qualityFacts.value = []
  qualityError.value = ''
  files.value = []
  progressRows.value = []
  filesLoading.value = true
  qualityLoading.value = false
  try {
    const detail = await loadSiteDailyLog(record.id, detailController.signal)
    if (requestId !== detailRequestId.value) return
    activeRecord.value = detail
    Object.assign(form, {
      projectId: detail.projectId,
      reportDate: detail.reportDate,
      constructionContent: detail.constructionContent,
      issuesDelays: detail.issuesDelays ?? '',
      nextDayPlan: detail.nextDayPlan ?? '',
      weatherSummary: detail.weatherSummary ?? '',
      onSiteHeadcount: detail.onSiteHeadcount ?? null,
      expectedUpdatedAt: detail.updatedAt ?? undefined,
    })
    dialogOpen.value = true
    await Promise.all([
      loadFiles(detail.id, requestId),
      loadProgress(detail, requestId),
      loadQuality(detail.id, requestId),
    ])
  } catch (error) {
    if (!detailController.signal.aborted && requestId === detailRequestId.value) {
      errorMessage.value = message(error, '现场日报详情加载失败')
    }
  }
}

async function loadProgress(detail: SiteDailyLogRecord, requestId: number): Promise<void> {
  if (!detail.scheduleManaged) {
    progressRows.value = []
    return
  }
  const fallbackRows = (detail.plannedTasks ?? []).map((task) => ({
    key: task.id,
    wbsTaskId: task.id,
    taskCode: task.taskCode,
    taskName: task.taskName,
    currentProgress: task.progressPercent ?? '0',
    completedQuantity: '0',
    workDescription: '',
    included: false,
  }))
  if (!canReportProgress.value) {
    progressRows.value = fallbackRows
    return
  }
  const existing = await loadDailyProgress(detail.id, detailController?.signal)
  if (requestId !== detailRequestId.value) return
  const byTask = new Map(existing.map((item) => [item.wbsTaskId, item]))
  progressRows.value = fallbackRows.map((task) => {
    const current = byTask.get(task.wbsTaskId)
    return {
      ...task,
      currentProgress: current?.currentProgress ?? task.currentProgress,
      completedQuantity: current?.completedQuantity ?? '0',
      workDescription: current?.workDescription ?? '',
      included: Boolean(current),
    }
  })
}

async function loadFiles(id: string, requestId: number): Promise<void> {
  filesLoading.value = true
  try {
    const next = await listSiteFiles(SITE_DAILY_LOG, id, detailController?.signal)
    if (requestId === detailRequestId.value) {
      files.value = next.map((file) => ({ id: file.id, originalName: file.originalName }))
    }
  } catch (error) {
    if (requestId === detailRequestId.value) {
      errorMessage.value = message(error, '附件列表加载失败')
      files.value = []
    }
  } finally {
    if (requestId === detailRequestId.value) filesLoading.value = false
  }
}

async function loadQuality(id: string, requestId: number): Promise<void> {
  qualityFacts.value = []
  qualityError.value = ''
  if (!canViewQuality.value) return
  qualityLoading.value = true
  try {
    const facts = await loadSiteDailyQualitySafety(id, detailController?.signal)
    if (requestId === detailRequestId.value) qualityFacts.value = facts
  } catch (error) {
    if (requestId === detailRequestId.value) {
      qualityError.value = message(error, '当日质量安全检查加载失败，不影响日报正文查看。')
    }
  } finally {
    if (requestId === detailRequestId.value) qualityLoading.value = false
  }
}

async function saveRecord(): Promise<void> {
  const command = cleanLogCommand(form)
  if (!command.projectId || !command.reportDate || !command.constructionContent) {
    errorMessage.value = '请完整填写项目、日报日期和施工内容'
    return
  }
  saving.value = true
  resetNotices()
  try {
    if (dialogMode.value === 'edit' && activeRecord.value) {
      await updateSiteDailyLog(activeRecord.value.id, {
        ...command,
        expectedUpdatedAt: activeRecord.value.updatedAt ?? undefined,
      })
      successMessage.value = '日报草稿已更新。'
    } else {
      await createSiteDailyLog(command)
      successMessage.value = '日报草稿已创建。'
    }
    dialogOpen.value = false
    await loadList(true)
  } catch (error) {
    errorMessage.value = message(error, '现场日报保存失败')
    if (activeRecord.value) await openRecord(activeRecord.value, dialogMode.value === 'edit')
  } finally {
    saving.value = false
  }
}

async function saveProgress(): Promise<boolean> {
  if (!activeRecord.value?.scheduleManaged || !canReportProgress.value) return true
  const items = progressRows.value
    .filter((row) => row.included)
    .map((row) => ({
      wbsTaskId: row.wbsTaskId,
      currentProgress: row.currentProgress.trim(),
      completedQuantity: row.completedQuantity.trim(),
      workDescription: row.workDescription.trim(),
    }))
  if (!items.length) {
    errorMessage.value = '至少选择一条周计划任务填报实际进度'
    return false
  }
  if (items.some((item) => !item.workDescription)) {
    errorMessage.value = '已选任务必须填写完成情况'
    return false
  }
  progressSaving.value = true
  resetNotices()
  try {
    await replaceDailyProgress(activeRecord.value.id, items)
    successMessage.value = '实际进度已保存；后续提交将继续使用服务端事实。'
    return true
  } catch (error) {
    errorMessage.value = message(error, '实际进度保存失败')
    return false
  } finally {
    progressSaving.value = false
  }
}

function requestDailySubmit(): void {
  if (activeRecord.value) pendingDailyAction.value = { kind: 'submit', record: activeRecord.value }
}

async function submitCurrent(record: SiteDailyLogRecord): Promise<void> {
  if (!(await saveProgress())) return
  saving.value = true
  resetNotices()
  try {
    await submitSiteDailyLog(record.id)
    dialogOpen.value = false
    successMessage.value = '现场日报已提交。'
    await loadList(true)
  } catch (error) {
    errorMessage.value = message(error, '现场日报提交失败')
    await openRecord(record)
  } finally {
    saving.value = false
  }
}

async function onFileChange(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !activeRecord.value) return
  saving.value = true
  resetNotices()
  try {
    await uploadSiteFile(file, SITE_DAILY_LOG, activeRecord.value.id)
    successMessage.value = '附件已上传。'
    await loadFiles(activeRecord.value.id, detailRequestId.value)
  } catch (error) {
    errorMessage.value = message(error, '附件上传失败')
  } finally {
    saving.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}

function openFilePicker(): void {
  fileInput.value?.click()
}

async function downloadFile(id: string): Promise<void> {
  const url = await getSiteFileUrl(id)
  window.open(url, '_blank', 'noopener,noreferrer')
}

function requestFileRemoval(id: string, fileName: string): void {
  if (!activeRecord.value) return
  pendingDailyAction.value = {
    kind: 'file',
    recordId: activeRecord.value.id,
    requestId: detailRequestId.value,
    fileId: id,
    fileName,
  }
}

async function removeFile(pending: Extract<PendingDailyAction, { kind: 'file' }>): Promise<void> {
  saving.value = true
  resetNotices()
  try {
    await deleteSiteFile(pending.fileId)
    successMessage.value = '附件已删除。'
    await loadFiles(pending.recordId, pending.requestId)
  } catch (error) {
    errorMessage.value = message(error, '附件删除失败')
  } finally {
    saving.value = false
  }
}

function closeDailyConfirmation(): void {
  if (!saving.value && !progressSaving.value) pendingDailyAction.value = null
}

async function confirmDailyAction(): Promise<void> {
  const pending = pendingDailyAction.value
  if (!pending || saving.value || progressSaving.value) return
  if (pending.kind === 'submit') await submitCurrent(pending.record)
  else await removeFile(pending)
  pendingDailyAction.value = null
}

function search(): void {
  pageNo.value = 1
  setQuery()
  void loadList()
}

function applyStatusFilter(value: string): void {
  filter.status = value
  search()
}

function changePage(next: number): void {
  pageNo.value = next
  setQuery()
  void loadList()
}

watch(
  [selectedProjectId, selectedReportPeriod],
  async () => {
    hydrateQuery()
    pageNo.value = 1
    setQuery()
    await loadList()
  },
  { immediate: true },
)

watch(
  () => route.fullPath,
  () => {
    hydrateQuery()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
})

function cleanLogCommand(command: SiteDailyLogCommand): SiteDailyLogCommand {
  const headcount = command.onSiteHeadcount
  return {
    projectId: command.projectId?.trim() || undefined,
    reportDate: command.reportDate || undefined,
    constructionContent: command.constructionContent.trim(),
    issuesDelays: command.issuesDelays?.trim() || undefined,
    nextDayPlan: command.nextDayPlan?.trim() || undefined,
    weatherSummary: command.weatherSummary?.trim() || undefined,
    onSiteHeadcount:
      typeof headcount === 'number' && Number.isFinite(headcount) ? Math.trunc(headcount) : null,
    expectedUpdatedAt: command.expectedUpdatedAt,
  }
}
</script>

<template>
  <section class="daily-log-page" aria-labelledby="daily-log-title">
    <div
      v-if="(errorMessage || successMessage) && !dialogOpen"
      class="daily-log-page__notice-region"
    >
      <V2Alert
        v-if="errorMessage"
        class="daily-log-page__feedback"
        tone="danger"
        title="请求未完成"
        dismissible
        @dismiss="errorMessage = ''"
      >
        {{ errorMessage }}
      </V2Alert>
      <V2Alert
        v-else
        class="daily-log-page__feedback"
        tone="success"
        title="操作完成"
        dismissible
        @dismiss="successMessage = ''"
      >
        {{ successMessage }}
      </V2Alert>
    </div>

    <V2Card
      class="daily-log-page__toolbar-card"
      title="现场日报"
      title-id="daily-log-title"
      :heading-level="1"
      subtitle="范围由顶部项目与报告期控制；可按状态筛选"
    >
      <template #actions>
        <div class="daily-log-page__toolbar">
          <V2Select
            :model-value="filter.status"
            label="日报状态"
            :options="[
              { value: 'DRAFT', label: '草稿' },
              { value: 'SUBMITTED', label: '已提交' },
            ]"
            allow-empty
            placeholder="全部状态"
            @update:model-value="applyStatusFilter"
          />
          <V2Button v-if="filter.status" size="small" variant="ghost" @click="resetFilters"
            >重置</V2Button
          >
          <V2Button size="small" variant="ghost" @click="loadList()">刷新</V2Button>
          <V2Button v-if="canEdit" size="small" @click="openCreate">新建日报</V2Button>
        </div>
      </template>
    </V2Card>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载现场日报"
      description="只读取当前账号可见范围内的日报事实。"
      :heading-level="2"
    />
    <V2PageState
      v-else-if="!records.length"
      kind="empty"
      title="暂无现场日报"
      description="调整筛选条件，或由具备权限的账号创建日报草稿。"
      :heading-level="2"
    />
    <div v-else class="daily-log-page__list">
      <V2Card
        v-for="record in records"
        :key="record.id"
        :title="record.projectName || record.projectId"
        :subtitle="record.reportDate"
      >
        <div class="daily-log-page__facts">
          <V2Badge :tone="record.status === 'DRAFT' ? 'neutral' : 'success'">
            {{ record.status === 'DRAFT' ? '草稿' : '已提交' }}
          </V2Badge>
          <span>{{ record.weatherSummary || '未填写天气摘要' }}</span>
          <span>在场人数 {{ record.onSiteHeadcount ?? '未填写' }}</span>
        </div>
        <p class="daily-log-page__summary">{{ record.constructionContent }}</p>
        <template #footer>
          <div class="daily-log-page__actions">
            <V2Button size="small" variant="secondary" @click="openRecord(record)"
              >查看详情</V2Button
            >
            <V2Button
              v-if="canEdit && record.status === 'DRAFT'"
              size="small"
              variant="ghost"
              @click="openRecord(record, true)"
            >
              编辑草稿
            </V2Button>
          </div>
        </template>
      </V2Card>
    </div>

    <div v-if="records.length" class="daily-log-page__pagination">
      <div class="daily-log-page__actions">
        <V2Button
          size="small"
          variant="secondary"
          :disabled="pageNo <= 1"
          @click="changePage(pageNo - 1)"
        >
          上一页
        </V2Button>
        <span>第 {{ pageNo }} 页</span>
        <V2Button
          size="small"
          variant="secondary"
          :disabled="pageNo * pageSize >= total"
          @click="changePage(pageNo + 1)"
        >
          下一页
        </V2Button>
      </div>
    </div>

    <V2Dialog
      v-model:open="dialogOpen"
      :title="
        dialogMode === 'create'
          ? '新建现场日报'
          : dialogMode === 'edit'
            ? '编辑现场日报'
            : '现场日报详情'
      "
      :panel-class="
        dialogMode === 'view'
          ? 'v2-dialog-standard v2-detail-dialog'
          : 'v2-dialog-standard v2-detail-dialog daily-log-page__dialog'
      "
      :close-on-backdrop="dialogMode === 'view'"
      @backdrop-click="warnUnsavedDialog"
    >
      <div v-if="dialogMode === 'view' && activeRecord" class="v2-detail-dialog__section">
        <V2Badge :tone="activeRecord.status === 'DRAFT' ? 'neutral' : 'success'">
          {{ activeRecord.status === 'DRAFT' ? '草稿' : '已提交' }}
        </V2Badge>
        <p class="v2-detail-dialog__message">{{ activeRecord.constructionContent }}</p>
        <dl class="v2-detail-dialog__facts">
          <div>
            <dt>项目</dt>
            <dd>{{ activeRecord.projectName || activeRecord.projectId }}</dd>
          </div>
          <div>
            <dt>日报日期</dt>
            <dd>{{ activeRecord.reportDate }}</dd>
          </div>
          <div>
            <dt>天气摘要</dt>
            <dd>{{ activeRecord.weatherSummary || '未填写' }}</dd>
          </div>
          <div>
            <dt>在场人数</dt>
            <dd>{{ activeRecord.onSiteHeadcount ?? '未填写' }}</dd>
          </div>
          <div>
            <dt>问题与延误</dt>
            <dd>{{ activeRecord.issuesDelays || '无' }}</dd>
          </div>
          <div>
            <dt>次日计划</dt>
            <dd>{{ activeRecord.nextDayPlan || '未填写' }}</dd>
          </div>
        </dl>
      </div>

      <form v-else class="daily-log-page__form" @submit.prevent="saveRecord">
        <V2Select
          v-if="projectOptions.length"
          v-model="form.projectId"
          label="项目"
          :options="projectOptions"
          required
        />
        <V2Input v-else v-model="form.projectId" label="项目 ID" required />
        <label>
          日报日期
          <input v-model="form.reportDate" type="date" required />
        </label>
        <label class="daily-log-page__span-2">
          施工内容
          <textarea v-model="form.constructionContent" rows="4" required />
        </label>
        <label class="daily-log-page__span-2">
          问题与延误
          <textarea v-model="form.issuesDelays" rows="3" />
        </label>
        <label class="daily-log-page__span-2">
          次日计划
          <textarea v-model="form.nextDayPlan" rows="3" />
        </label>
        <label class="daily-log-page__span-2">
          天气摘要
          <textarea v-model="form.weatherSummary" rows="2" />
        </label>
        <label>
          在场人数
          <input v-model.number="form.onSiteHeadcount" type="number" min="0" step="1" />
        </label>
      </form>

      <template v-if="activeRecord">
        <V2Card
          :heading-level="3"
          title="附件"
          :subtitle="activeRecord.status === 'DRAFT' ? '仅草稿可上传/删除' : '已提交附件只读不可变'"
        >
          <template #actions>
            <template v-if="dialogMode !== 'view' && canEdit && activeRecord.status === 'DRAFT'">
              <input
                ref="fileInput"
                class="daily-log-page__file-input"
                type="file"
                @change="onFileChange"
              />
              <V2GlassButton
                text="选择文件"
                :disabled="saving"
                :on-click="openFilePicker"
                class-name="daily-log-page__glass-button"
              />
            </template>
          </template>
          <V2PageState
            v-if="filesLoading"
            kind="loading"
            title="正在加载附件"
            description="附件列表独立读取，不影响日报正文。"
            :heading-level="3"
          />
          <div v-else-if="files.length" class="daily-log-page__stack">
            <article v-for="file in files" :key="file.id" class="daily-log-page__row">
              <button type="button" class="daily-log-page__link" @click="downloadFile(file.id)">
                {{ file.originalName }}
              </button>
              <button
                v-if="dialogMode !== 'view' && canEdit && activeRecord.status === 'DRAFT'"
                type="button"
                class="daily-log-page__danger-link"
                @click="requestFileRemoval(file.id, file.originalName)"
              >
                删除
              </button>
            </article>
          </div>
          <p v-else class="daily-log-page__empty-copy">暂无附件。</p>
        </V2Card>

        <V2Card
          v-if="activeRecord.scheduleManaged"
          :heading-level="3"
          title="WBS 实际进度"
          :subtitle="
            canReportProgress
              ? '仅周计划内任务可填报；提交后统一刷新进度。'
              : '当前账号无进度填报权限，正文详情继续可读且不会发起进度请求。'
          "
        >
          <div v-if="progressRows.length" class="daily-log-page__table-wrap">
            <table class="daily-log-page__table">
              <thead>
                <tr>
                  <th v-if="canReportProgress">填报</th>
                  <th>任务编号</th>
                  <th>任务名称</th>
                  <th>累计进度%</th>
                  <th>累计完成量</th>
                  <th>完成情况</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in progressRows" :key="row.key">
                  <td v-if="canReportProgress">
                    <span v-if="dialogMode === 'view'">{{ row.included ? '是' : '否' }}</span>
                    <input
                      v-else
                      v-model="row.included"
                      type="checkbox"
                      :disabled="activeRecord.status !== 'DRAFT'"
                    />
                  </td>
                  <td>{{ row.taskCode }}</td>
                  <td>{{ row.taskName }}</td>
                  <td>
                    <span v-if="dialogMode === 'view'">{{ row.currentProgress }}</span>
                    <input
                      v-else
                      v-model="row.currentProgress"
                      type="number"
                      min="0"
                      max="100"
                      step="0.0001"
                      :disabled="
                        !canReportProgress || !row.included || activeRecord.status !== 'DRAFT'
                      "
                    />
                  </td>
                  <td>
                    <span v-if="dialogMode === 'view'">{{ row.completedQuantity }}</span>
                    <input
                      v-else
                      v-model="row.completedQuantity"
                      type="number"
                      min="0"
                      step="0.0001"
                      :disabled="
                        !canReportProgress || !row.included || activeRecord.status !== 'DRAFT'
                      "
                    />
                  </td>
                  <td>
                    <span v-if="dialogMode === 'view'">{{ row.workDescription || '—' }}</span>
                    <V2Input
                      v-else
                      v-model="row.workDescription"
                      :disabled="
                        !canReportProgress || !row.included || activeRecord.status !== 'DRAFT'
                      "
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="daily-log-page__empty-copy">暂无当日周计划任务。</p>
          <template #footer>
            <V2GlassButton
              v-if="dialogMode !== 'view' && canReportProgress && activeRecord.status === 'DRAFT'"
              text="保存实际进度"
              :loading="progressSaving"
              :on-click="saveProgress"
              class-name="daily-log-page__glass-button"
            />
          </template>
        </V2Card>

        <V2Card
          :heading-level="3"
          title="质量安全摘要"
          :subtitle="
            canViewQuality
              ? '仅有质量安全查询权限时读取；失败不阻断日报正文。'
              : '当前账号无质量安全摘要权限，零请求。'
          "
        >
          <V2PageState
            v-if="qualityLoading"
            kind="loading"
            title="正在加载质量安全摘要"
            description="摘要只读，不反向改写来源业务。"
            :heading-level="3"
          />
          <V2Alert v-else-if="qualityError" tone="danger" title="读取失败">{{
            qualityError
          }}</V2Alert>
          <div v-else-if="qualityFacts.length" class="daily-log-page__stack">
            <article
              v-for="item in qualityFacts"
              :key="item.inspectionId"
              class="daily-log-page__panel"
            >
              <strong>{{ item.inspectionCode }}</strong>
              <p>{{ item.location || '未填写检查地点' }} · {{ item.conclusion || '未填写结论' }}</p>
              <small
                >问题 {{ item.issueCount }} / 高风险 {{ item.highSeverityIssueCount }} / 未关闭
                {{ item.openIssueCount }}</small
              >
            </article>
          </div>
          <p v-else class="daily-log-page__empty-copy">暂无当日质量安全摘要。</p>
        </V2Card>

        <V2Card
          title="只读联动事实"
          subtitle="材料到货、领料、计划任务与审计均只读展示。"
          :heading-level="3"
        >
          <div class="daily-log-page__stack daily-log-page__linked-facts">
            <article class="daily-log-page__panel">
              <strong>材料到货</strong>
              <p>{{ activeRecord.deliveries?.length ?? 0 }} 条</p>
            </article>
            <article class="daily-log-page__panel">
              <strong>已审批领料</strong>
              <p>{{ activeRecord.requisitions?.length ?? 0 }} 条</p>
            </article>
            <article class="daily-log-page__panel">
              <strong>当日计划任务</strong>
              <p>{{ activeRecord.plannedTasks?.length ?? 0 }} 条</p>
            </article>
            <article class="daily-log-page__panel">
              <strong>变更历史</strong>
              <p>{{ activeRecord.auditTrail?.length ?? 0 }} 条</p>
            </article>
          </div>
        </V2Card>
      </template>

      <template v-if="dialogMode !== 'view'" #footer>
        <div class="daily-log-page__dialog-actions">
          <V2Alert
            v-if="errorMessage"
            class="daily-log-page__feedback"
            tone="danger"
            title="请求未完成"
            dismissible
            @dismiss="errorMessage = ''"
          >
            {{ errorMessage }}
          </V2Alert>
          <V2GlassButton
            text="关闭"
            :on-click="() => (dialogOpen = false)"
            class-name="daily-log-page__glass-button"
          />
          <V2GlassButton
            text="保存草稿"
            :loading="saving"
            :on-click="saveRecord"
            class-name="daily-log-page__glass-button"
          />
          <V2GlassButton
            v-if="canSubmitCurrent"
            text="提交定稿"
            :loading="saving"
            :on-click="requestDailySubmit"
            class-name="daily-log-page__glass-button"
          />
        </div>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(pendingDailyAction)"
      :title="pendingDailyAction?.kind === 'file' ? '删除附件' : '提交现场日报'"
      :description="
        pendingDailyAction?.kind === 'file'
          ? `“${pendingDailyAction.fileName}”将被永久删除，此操作无法撤销。`
          : `确认提交 ${pendingDailyAction?.record.reportDate ?? ''} 现场日报？提交后内容和附件将转为只读。`
      "
      :confirm-text="pendingDailyAction?.kind === 'file' ? '永久删除' : '确认提交'"
      :danger="pendingDailyAction?.kind === 'file'"
      :loading="saving || progressSaving"
      @close="closeDailyConfirmation"
      @confirm="confirmDailyAction"
    />
  </section>
</template>

<style scoped>
.daily-log-page {
  display: grid;
  gap: var(--v2-space-3);
  color: var(--v2-color-text);
}
.daily-log-page__notice-region {
  position: fixed;
  z-index: var(--v2-z-toast);
  inset-block-start: calc(var(--v2-space-6) + 3rem);
  inset-inline-end: var(--v2-page-gutter);
  width: min(24rem, calc(100vw - 2 * var(--v2-page-gutter)));
}
.daily-log-page__feedback {
  display: block;
  padding: var(--v2-space-2) var(--v2-space-3);
  border-radius: var(--v2-radius-md);
  box-shadow: var(--v2-shadow-panel);
}
.daily-log-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
  font-size: var(--v2-font-size-12);
}
.daily-log-page__toolbar-card :deep(.v2-card__body) {
  display: none;
}
.daily-log-page__toolbar-card :deep(.v2-card__header) {
  display: grid;
  grid-template-columns: minmax(14rem, 1fr) auto;
  align-items: center;
}
.daily-log-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
  justify-content: flex-end;
}
.daily-log-page__toolbar :deep(.v2-field) {
  flex: 0 0 11rem;
  min-width: 10rem;
  max-width: 14rem;
}
.daily-log-page__toolbar :deep(.v2-field__label) {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
.daily-log-page__span-2 {
  grid-column: 1 / -1;
}
.daily-log-page__list,
.daily-log-page__stack {
  display: grid;
  gap: var(--v2-space-3);
}
.daily-log-page__facts,
.daily-log-page__actions,
.daily-log-page__pagination,
.daily-log-page__row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}
.daily-log-page__pagination {
  justify-content: flex-end;
  font-size: var(--v2-font-size-12);
}
.daily-log-page__summary,
.daily-log-page__empty-copy {
  margin: 0;
  color: var(--v2-color-text-secondary);
}
.daily-log-page__facts,
.daily-log-page__summary {
  font-size: var(--v2-font-size-12);
}
.daily-log-page__form label {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.daily-log-page__form input,
.daily-log-page__form textarea {
  min-height: 2.5rem;
  padding: 0 var(--v2-space-3);
  color: var(--v2-color-text);
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
  border-radius: var(--v2-radius-md);
  font: inherit;
}
.daily-log-page__form :deep(.v2-field__control) {
  background: transparent;
  border-color: color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
}
.daily-log-page__file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
.daily-log-page__dialog :deep(.daily-log-page__glass-button) {
  width: auto;
  min-height: 2.5rem;
}
.daily-log-page__dialog-actions {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--v2-space-2);
  width: 100%;
}
.daily-log-page__dialog-actions .daily-log-page__feedback {
  position: absolute;
  z-index: var(--v2-z-toast);
  inset-block-end: calc(100% + var(--v2-space-2));
  inset-inline-end: 0;
  width: min(24rem, calc(100vw - 2 * var(--v2-page-gutter)));
}
.daily-log-page__dialog-actions .daily-log-page__feedback::after {
  position: absolute;
  inset-block-start: 100%;
  inset-inline-end: var(--v2-space-5);
  width: 8px;
  height: 8px;
  background: var(--v2-color-danger-soft);
  border-inline-end: var(--v2-border-width) solid var(--v2-color-danger);
  border-block-end: var(--v2-border-width) solid var(--v2-color-danger);
  content: '';
  transform: translateY(-50%) rotate(45deg);
}
.daily-log-page__form textarea {
  min-height: 6rem;
  padding: var(--v2-space-2) var(--v2-space-3);
  resize: vertical;
}
.daily-log-page__dialog {
  width: min(72rem, calc(100vw - 2rem));
}
.daily-log-page__table-wrap {
  overflow: auto;
}
.daily-log-page__table {
  width: 100%;
  border-collapse: collapse;
}
.daily-log-page__table th,
.daily-log-page__table td {
  padding: 0.75rem;
  border-bottom: 1px solid var(--v2-color-border);
  font-size: var(--v2-font-size-12);
  text-align: left;
  vertical-align: top;
}
.daily-log-page__panel {
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  font-size: var(--v2-font-size-12);
}
.daily-log-page__linked-facts {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.daily-log-page__link,
.daily-log-page__danger-link {
  padding: 0;
  background: transparent;
  border: 0;
  cursor: pointer;
  font: inherit;
}
.daily-log-page__link {
  color: var(--v2-color-primary-hover);
}
.daily-log-page__danger-link {
  color: var(--v2-color-danger-text);
}
@media (max-width: 64rem) {
  .daily-log-page__form {
    grid-template-columns: 1fr;
  }
  .daily-log-page__toolbar {
    width: 100%;
  }
  .daily-log-page__toolbar-card :deep(.v2-card__header) {
    grid-template-columns: 1fr;
  }
  .daily-log-page__toolbar :deep(.v2-field) {
    max-width: none;
  }
  .daily-log-page__pagination {
    align-items: flex-start;
    flex-direction: column;
  }
  .daily-log-page__linked-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 40rem) {
  .daily-log-page__linked-facts {
    grid-template-columns: 1fr;
  }
}
</style>
