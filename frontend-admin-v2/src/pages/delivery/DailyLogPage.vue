<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { formatDecimal } from '@/pages/dashboard/model'
import { useRoute, useRouter } from 'vue-router'
import type {
  DailyProgressCommand,
  FieldDailyLogCommand,
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
  V2Input,
  V2PageState,
  V2Select,
  showToast,
  useToastMessage,
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
  uploadSiteFileIdempotently,
} from '@/services/delivery'
import { featureFlags } from '@/services/featureFlags'
import {
  FieldDraftRepository,
  fieldDraftStatusLabel,
  fieldDraftSyncFailure,
  type FieldDraft,
} from '@/services/fieldDrafts'
import { getSessionNamespaceIdentity, useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

const SITE_DAILY_LOG = 'SITE_DAILY_LOG'

interface DailyProgressRow extends DailyProgressCommand {
  key: string
  taskCode: string
  taskName: string
  included: boolean
}

interface DailyDraftPayload {
  command: FieldDailyLogCommand
  recordId?: string
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
const successMessage = useToastMessage()
watch(errorMessage, (value) => {
  if (value) showToast('error', '操作未完成', value)
})
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
const localDraft = ref<FieldDraft<DailyDraftPayload> | null>(null)
const localPhoto = ref<File | null>(null)
let draftRepository: FieldDraftRepository | null = null

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

const projectOptions = computed(() => {
  const options = workspace.projects.map((item) => ({ value: item.value, label: item.label }))
  if (form.projectId && !options.some((item) => item.value === form.projectId))
    options.unshift({ value: form.projectId, label: `本地草稿项目（${form.projectId}）` })
  return options
})
const canEdit = computed(
  () => hasPermission('site:daily:edit') || session.hasPermission('site:daily:self'),
)
const canReportProgress = computed(
  () => hasPermission('schedule:progress') || session.hasPermission('schedule:daily-progress:self'),
)
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
    activeRecord.value.scheduleManaged &&
    canReportProgress.value,
)
const offlineDraftEnabled = computed(
  () => featureFlags.offlineDraft.enabled && featureFlags.fieldDailyLog.enabled,
)
const offlineSyncEnabled = computed(
  () => offlineDraftEnabled.value && featureFlags.offlineSync.enabled,
)
const localDraftLabel = computed(() =>
  localDraft.value ? fieldDraftStatusLabel(localDraft.value.status) : '未保存本地草稿',
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

async function loadList(preserveNotice = false): Promise<boolean> {
  listController?.abort()
  const controller = new AbortController()
  listController = controller
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
      controller.signal,
    )
    if (listController !== controller) return false
    records.value = page.records
    total.value = page.total
    return true
  } catch (error) {
    if (!controller.signal.aborted && listController === controller) {
      records.value = []
      total.value = 0
      errorMessage.value = message(error, '现场日报加载失败')
    }
    return false
  } finally {
    if (listController === controller) loading.value = false
  }
}

async function refreshDailyLogs(): Promise<void> {
  if (await loadList()) showToast('success', '刷新完成', '现场日报已刷新。')
}

function openCreate(): void {
  dialogMode.value = 'create'
  activeRecord.value = null
  qualityFacts.value = []
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
  localDraft.value = null
  localPhoto.value = null
  void restoreDailyDraft()
}

async function openRecord(record: SiteDailyLogRecord, edit = false): Promise<void> {
  detailController?.abort()
  detailController = new AbortController()
  const requestId = ++detailRequestId.value
  dialogMode.value = edit ? 'edit' : 'view'
  qualityFacts.value = []
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
    localDraft.value = null
    localPhoto.value = null
    await restoreDailyDraft()
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
  if (!canViewQuality.value) return
  qualityLoading.value = true
  try {
    const facts = await loadSiteDailyQualitySafety(id, detailController?.signal)
    if (requestId === detailRequestId.value) qualityFacts.value = facts
  } catch (error) {
    if (requestId === detailRequestId.value) {
      showToast(
        'error',
        '质量安全摘要读取失败',
        message(error, '当日质量安全检查加载失败，不影响日报正文查看。'),
      )
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

async function saveLocalDailyDraft(status: 'DRAFT' | 'PENDING' = 'DRAFT'): Promise<boolean> {
  const command = cleanLogCommand(form)
  if (!command.projectId || !command.reportDate || !command.constructionContent) {
    errorMessage.value = '请完整填写项目、日报日期和施工内容'
    return false
  }
  try {
    const repository = localRepository()
    const id = dailyDraftId(command)
    const clientRequestId = localDraft.value?.clientRequestId ?? crypto.randomUUID()
    localDraft.value = await repository.put<DailyDraftPayload>({
      id,
      kind: 'DAILY_LOG',
      clientRequestId,
      status,
      payload: {
        command: {
          ...command,
          clientRequestId,
          expectedVersion: recordVersion(activeRecord.value),
        },
        recordId: activeRecord.value?.id,
      },
    })
    if (localPhoto.value) {
      await repository.putAttachment(id, localPhoto.value)
      localPhoto.value = null
    }
    successMessage.value = status === 'DRAFT' ? '本地草稿已保存。' : '草稿已进入待同步状态。'
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '本地草稿保存失败'
    return false
  }
}

async function syncLocalDailyDraft(): Promise<void> {
  if (!offlineSyncEnabled.value || !(await saveLocalDailyDraft('PENDING')) || !localDraft.value)
    return
  const repository = localRepository()
  const draft = localDraft.value
  if (!navigator.onLine) {
    localDraft.value = await repository.put({ ...draft, status: 'RETRYABLE', error: '当前离线' })
    errorMessage.value = '当前离线，草稿保留为可重试状态'
    return
  }
  saving.value = true
  resetNotices()
  try {
    localDraft.value = await repository.put({ ...draft, status: 'SYNCING' })
    const payload = draft.payload
    const recordId = payload.recordId || (await createSiteDailyLog(payload.command))
    if (payload.recordId) await updateSiteDailyLog(payload.recordId, payload.command)
    for (const attachment of await repository.attachments(draft.id)) {
      await uploadSiteFileIdempotently(
        new File([attachment.file], attachment.name, { type: attachment.type }),
        SITE_DAILY_LOG,
        recordId,
      )
    }
    await repository.removeAttachments(draft.id)
    localDraft.value = await repository.put({ ...draft, status: 'SYNCED' })
    successMessage.value = '本地日报草稿已同步。'
    await loadList(true)
  } catch (error) {
    const code = isApiClientError(error) ? error.code : undefined
    const status = isApiClientError(error) ? error.status : undefined
    localDraft.value = await repository.put({
      ...draft,
      status: fieldDraftSyncFailure(code, status),
      error: message(error, '同步失败'),
    })
    errorMessage.value = message(error, '本地日报同步失败')
  } finally {
    saving.value = false
  }
}

async function restoreDailyDraft(): Promise<void> {
  if (!offlineDraftEnabled.value) return
  try {
    const repository = localRepository()
    const draft = (
      activeRecord.value
        ? await repository.get<DailyDraftPayload>(dailyDraftId(cleanLogCommand(form)))
        : (await repository.list('DAILY_LOG')).find((item) => item.status !== 'SYNCED')
    ) as FieldDraft<DailyDraftPayload> | undefined
    if (!draft || draft.status === 'SYNCED') return
    localDraft.value = draft
    Object.assign(form, draft.payload.command)
    successMessage.value = `已恢复${fieldDraftStatusLabel(draft.status)}。`
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '本地草稿恢复失败'
  }
}

function chooseLocalPhoto(event: Event): void {
  localPhoto.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

function localRepository(): FieldDraftRepository {
  if (draftRepository) return draftRepository
  const identity = getSessionNamespaceIdentity()
  if (!identity) throw new TypeError('当前会话缺少租户标识，不能使用本地草稿')
  draftRepository = new FieldDraftRepository(identity.tenantId, identity.userId)
  return draftRepository
}

function dailyDraftId(command: SiteDailyLogCommand): string {
  return `daily:${activeRecord.value?.id || `${command.projectId || 'none'}:${command.reportDate || 'none'}`}`
}

function recordVersion(record: SiteDailyLogRecord | null): number | undefined {
  const value = (record as (SiteDailyLogRecord & { version?: unknown }) | null)?.version
  return typeof value === 'number' ? value : undefined
}

async function saveProgress(): Promise<boolean> {
  if (!activeRecord.value?.scheduleManaged) {
    errorMessage.value = '项目缺少生效进度计划或已批准周计划，现场日报禁止提交'
    return false
  }
  if (!canReportProgress.value) return false
  const items = progressRows.value
    .filter((row) => row.included)
    .map((row) => ({
      wbsTaskId: row.wbsTaskId,
      currentProgress: String(row.currentProgress).trim(),
      completedQuantity: String(row.completedQuantity).trim(),
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
    successMessage.value = '实际进度已保存；后续提交将使用最新进度。'
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
  if (!navigator.onLine) {
    errorMessage.value = '日报正式提交必须在线完成'
    return
  }
  if (!(await saveProgress())) return
  saving.value = true
  resetNotices()
  try {
    const version = recordVersion(record)
    if (version === undefined) throw new TypeError('日报缺少版本，请刷新后重试')
    await submitSiteDailyLog(record.id, version)
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
  draftRepository = null
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
    <V2Card
      class="daily-log-page__toolbar-card"
      title="现场日报"
      title-id="daily-log-title"
      :heading-level="1"
    >
      <template #actions>
        <div class="daily-log-page__toolbar">
          <V2Select
            class="daily-log-page__status-filter"
            :model-value="filter.status"
            label="日报状态"
            hide-label
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
          <V2Button size="small" variant="ghost" @click="refreshDailyLogs">刷新</V2Button>
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
      v-else-if="!records.length && !errorMessage"
      kind="empty"
      title="暂无现场日报"
      description="调整筛选条件，或由具备权限的账号创建日报草稿。"
      :heading-level="2"
    />
    <V2Card v-else>
      <div class="daily-log-page__table-wrap">
        <table class="daily-log-page__table daily-log-page__list-table v2-table--top">
          <caption class="v2-visually-hidden">
            现场日报列表
          </caption>
          <colgroup>
            <col style="width: 14rem" />
            <col style="width: 7rem" />
            <col style="width: 14rem" />
            <col style="width: 6rem" />
            <col style="width: 10rem" />
            <col style="width: 6rem" />
            <col style="width: 18rem" />
            <col style="width: 7rem" />
          </colgroup>
          <thead>
            <tr>
              <th>日报标识</th>
              <th>日报日期</th>
              <th>项目</th>
              <th>状态</th>
              <th>天气摘要</th>
              <th>在场人数</th>
              <th>施工内容</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id">
              <td>
                <V2Button
                  size="small"
                  variant="ghost"
                  class="v2-table__record-link"
                  @click="openRecord(record)"
                >
                  {{ record.dailyLogCode || '日报标识缺失' }}
                </V2Button>
              </td>
              <td>{{ record.reportDate }}</td>
              <td>{{ record.projectName || '—' }}</td>
              <td class="daily-log-page__facts">
                <V2Badge :tone="record.status === 'DRAFT' ? 'neutral' : 'success'">
                  {{ record.status === 'DRAFT' ? '草稿' : '已提交' }}
                </V2Badge>
              </td>
              <td class="v2-table-cell--wrap">
                {{ record.weatherSummary || '未填写天气摘要' }}
              </td>
              <td>{{ record.onSiteHeadcount ?? '未填写' }}</td>
              <td class="daily-log-page__summary daily-log-page__summary-cell v2-table-cell--wrap">
                {{ record.constructionContent }}
              </td>
              <td>
                <div class="daily-log-page__actions">
                  <V2Button
                    v-if="canEdit && record.status === 'DRAFT'"
                    size="small"
                    variant="ghost"
                    @click="openRecord(record, true)"
                  >
                    编辑草稿
                  </V2Button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <nav class="daily-log-page__pagination" aria-label="现场日报分页">
          <div class="daily-log-page__actions">
            <span>共 {{ total }} 条</span>
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
        </nav>
      </template>
    </V2Card>

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
          : 'v2-dialog-standard v2-detail-dialog v2-dialog-wide'
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
            <dd>{{ activeRecord.projectName || '—' }}</dd>
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

      <section v-else class="v2-detail-dialog__section">
        <h3>日报信息</h3>
        <form class="daily-log-page__form" @submit.prevent="saveRecord">
          <V2Select
            v-model="form.projectId"
            label="项目"
            :options="projectOptions"
            required
            placeholder="请选择项目"
          />
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
          <label v-if="offlineDraftEnabled">
            本地暂存照片
            <input type="file" accept="image/*" @change="chooseLocalPhoto" />
          </label>
          <p v-if="offlineDraftEnabled" class="daily-log-page__span-2" role="status">
            同步状态：{{ localDraftLabel
            }}<span v-if="localPhoto"> · 待暂存 {{ localPhoto.name }}</span>
          </p>
        </form>
      </section>

      <template v-if="activeRecord">
        <section class="v2-detail-dialog__section">
          <h3>附件</h3>
          <p>
            {{ activeRecord.status === 'DRAFT' ? '仅草稿可上传/删除' : '已提交附件只读不可变' }}
          </p>
          <div v-if="dialogMode !== 'view' && canEdit && activeRecord.status === 'DRAFT'">
            <input
              ref="fileInput"
              class="daily-log-page__file-input"
              type="file"
              @change="onFileChange"
            />
            <V2Button
              type="button"
              size="small"
              variant="secondary"
              :disabled="saving"
              @click="openFilePicker"
            >
              选择文件
            </V2Button>
          </div>
          <V2PageState
            v-if="filesLoading"
            kind="loading"
            title="正在加载附件"
            description="附件列表独立读取，不影响日报正文。"
            :heading-level="3"
          />
          <div v-else-if="files.length" class="daily-log-page__stack">
            <article v-for="file in files" :key="file.id" class="daily-log-page__row">
              <V2Button type="button" size="small" variant="ghost" @click="downloadFile(file.id)">
                {{ file.originalName }}
              </V2Button>
              <V2Button
                v-if="dialogMode !== 'view' && canEdit && activeRecord.status === 'DRAFT'"
                type="button"
                size="small"
                variant="danger"
                @click="requestFileRemoval(file.id, file.originalName)"
              >
                删除
              </V2Button>
            </article>
          </div>
          <p v-else class="daily-log-page__empty-copy">暂无附件。</p>
        </section>

        <section v-if="activeRecord.scheduleManaged" class="v2-detail-dialog__section">
          <h3>WBS 实际进度</h3>
          <p>
            {{
              canReportProgress
                ? '仅周计划内任务可填报；提交后统一刷新进度。'
                : '当前账号无进度填报权限，正文详情继续可读且不会发起进度请求。'
            }}
          </p>
          <div
            v-if="progressRows.length"
            class="daily-log-page__table-wrap"
            role="region"
            aria-label="WBS 实际进度表格"
            tabindex="0"
          >
            <table class="daily-log-page__table v2-table--top">
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
                    <span v-if="dialogMode === 'view'">{{
                      formatDecimal(row.currentProgress)
                    }}</span>
                    <input
                      v-else
                      v-model="row.currentProgress"
                      type="number"
                      min="0"
                      max="100"
                      step="0.01"
                      :disabled="
                        !canReportProgress || !row.included || activeRecord.status !== 'DRAFT'
                      "
                    />
                  </td>
                  <td>
                    <span v-if="dialogMode === 'view'">{{
                      formatDecimal(row.completedQuantity)
                    }}</span>
                    <input
                      v-else
                      v-model="row.completedQuantity"
                      type="number"
                      min="0"
                      step="0.01"
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
          <div>
            <V2Button
              v-if="dialogMode !== 'view' && canReportProgress && activeRecord.status === 'DRAFT'"
              type="button"
              size="small"
              :loading="progressSaving"
              @click="saveProgress"
            >
              保存实际进度
            </V2Button>
          </div>
        </section>
        <V2Alert v-else tone="danger" title="日报提交已阻断">
          项目缺少生效进度计划或已批准周计划。请先完成计划基线和周计划，再提交现场日报。
        </V2Alert>

        <section class="v2-detail-dialog__section">
          <h3>质量安全摘要</h3>
          <p>
            {{
              canViewQuality
                ? '仅有质量安全查询权限时读取；失败不阻断日报正文。'
                : '当前账号无质量安全摘要权限，零请求。'
            }}
          </p>
          <V2PageState
            v-if="qualityLoading"
            kind="loading"
            title="正在加载质量安全摘要"
            description="摘要只读，不反向改写来源业务。"
            :heading-level="3"
          />
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
        </section>

        <section class="v2-detail-dialog__section">
          <h3>只读联动事实</h3>
          <p>材料到货、领料、计划任务与审计均只读展示。</p>
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
        </section>
      </template>

      <template v-if="dialogMode !== 'view'" #footer>
        <V2Button type="button" variant="secondary" @click="dialogOpen = false">关闭</V2Button>
        <V2Button
          v-if="offlineDraftEnabled"
          type="button"
          variant="secondary"
          :loading="saving"
          @click="saveLocalDailyDraft()"
        >
          保存到本机
        </V2Button>
        <V2Button
          v-if="offlineSyncEnabled"
          type="button"
          variant="secondary"
          :loading="saving"
          @click="syncLocalDailyDraft"
        >
          手动同步
        </V2Button>
        <V2Button type="button" variant="secondary" :loading="saving" @click="saveRecord">
          保存草稿
        </V2Button>
        <V2Button
          v-if="canSubmitCurrent"
          type="button"
          :loading="saving"
          @click="requestDailySubmit"
        >
          提交定稿
        </V2Button>
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
.daily-log-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
  justify-content: flex-end;
}
.daily-log-page__status-filter {
  flex: 0 0 11rem;
  min-width: 10rem;
  max-width: 14rem;
}
.daily-log-page__span-2 {
  grid-column: 1 / -1;
}
.daily-log-page__stack {
  display: grid;
  gap: var(--v2-space-3);
}
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
.daily-log-page__empty-copy {
  margin: 0;
  color: var(--v2-color-text-secondary);
}
.daily-log-page__facts,
.daily-log-page__summary {
  font-size: var(--v2-font-size-12);
}
.daily-log-page__file-input {
  position: absolute;
  width: var(--v2-border-width);
  height: var(--v2-border-width);
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
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
  width: var(--v2-space-2);
  height: var(--v2-space-2);
  background: var(--v2-color-danger-soft);
  border-inline-end: var(--v2-border-width) solid var(--v2-color-danger);
  border-block-end: var(--v2-border-width) solid var(--v2-color-danger);
  content: '';
  transform: translateY(-50%) rotate(45deg);
}
.daily-log-page__form textarea {
  min-height: var(--v2-control-height-textarea);
  padding: var(--v2-space-2) var(--v2-space-3);
  resize: vertical;
}
.daily-log-page__table-wrap {
  overflow: auto;
}
.daily-log-page__list-table {
  min-width: 82rem;
  table-layout: fixed;
}
.daily-log-page__panel {
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  font-size: var(--v2-font-size-12);
}
.daily-log-page__linked-facts {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
@media (max-width: 64rem) {
  .daily-log-page__form {
    grid-template-columns: 1fr;
  }
  .daily-log-page__toolbar {
    width: 100%;
  }
  .daily-log-page__status-filter {
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
