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
  V2Dialog,
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

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const loading = ref(false)
const saving = ref(false)
const progressSaving = ref(false)
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
const progressRows = ref<DailyProgressRow[]>([])
const files = ref<Array<{ id: string; originalName: string }>>([])
const qualityFacts = ref<SiteDailyQualitySafetyRecord[]>([])
let listController: AbortController | null = null
let detailController: AbortController | null = null
const detailRequestId = ref(0)

const filter = reactive({
  projectId: '',
  startDate: '',
  endDate: '',
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
const routeProjectId = computed(() =>
  typeof route.query.projectId === 'string' && route.query.projectId.trim()
    ? route.query.projectId.trim()
    : '',
)
const selectedProjectId = computed(() => routeProjectId.value || workspace.selectedProjectId || '')
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

function hydrateQuery(): void {
  filter.projectId = selectedProjectId.value
  const periodBounds = reportPeriodBounds(selectedReportPeriod.value)
  filter.startDate =
    periodBounds?.startDate ??
    (typeof route.query.startDate === 'string' ? route.query.startDate : '')
  filter.endDate =
    periodBounds?.endDate ?? (typeof route.query.endDate === 'string' ? route.query.endDate : '')
  filter.status = typeof route.query.status === 'string' ? route.query.status : ''
  const nextPage = Number(route.query.pageNo)
  pageNo.value = Number.isInteger(nextPage) && nextPage > 0 ? nextPage : 1
}

function setQuery(): void {
  void router.replace({
    query: {
      ...(filter.projectId ? { projectId: filter.projectId } : {}),
      ...(selectedReportPeriod.value ? { period: selectedReportPeriod.value } : {}),
      ...(filter.startDate ? { startDate: filter.startDate } : {}),
      ...(filter.endDate ? { endDate: filter.endDate } : {}),
      ...(filter.status ? { status: filter.status } : {}),
      ...(pageNo.value > 1 ? { pageNo: String(pageNo.value) } : {}),
    },
    hash: route.hash,
  })
}

function resetFilters(): void {
  const periodBounds = reportPeriodBounds(selectedReportPeriod.value)
  filter.startDate = periodBounds?.startDate ?? ''
  filter.endDate = periodBounds?.endDate ?? ''
  filter.status = ''
  search()
}

async function loadList(preserveNotice = false): Promise<void> {
  listController?.abort()
  listController = new AbortController()
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    const page = await loadSiteDailyLogs(
      {
        pageNo: pageNo.value,
        pageSize: pageSize.value,
        projectId: filter.projectId || undefined,
        startDate: filter.startDate || undefined,
        endDate: filter.endDate || undefined,
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
    projectId: filter.projectId || selectedProjectId.value,
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
      successMessage.value = '日报草稿已更新；列表已按服务端权威状态重读。'
    } else {
      await createSiteDailyLog(command)
      successMessage.value = '日报草稿已创建；列表已按服务端权威状态重读。'
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

async function submitCurrent(): Promise<void> {
  if (
    !activeRecord.value ||
    !window.confirm(`确认提交 ${activeRecord.value.reportDate} 现场日报吗？`)
  )
    return
  if (!(await saveProgress())) return
  saving.value = true
  resetNotices()
  try {
    await submitSiteDailyLog(activeRecord.value.id)
    dialogOpen.value = false
    successMessage.value = '现场日报已提交；列表已按服务端权威状态重读。'
    await loadList(true)
  } catch (error) {
    errorMessage.value = message(error, '现场日报提交失败')
    await openRecord(activeRecord.value)
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
    successMessage.value = '附件已上传；列表已按服务端权威状态重读。'
    await loadFiles(activeRecord.value.id, detailRequestId.value)
  } catch (error) {
    errorMessage.value = message(error, '附件上传失败')
  } finally {
    saving.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}

async function downloadFile(id: string): Promise<void> {
  const url = await getSiteFileUrl(id)
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function removeFile(id: string): Promise<void> {
  if (!activeRecord.value || !window.confirm('确认删除该附件吗？')) return
  saving.value = true
  resetNotices()
  try {
    await deleteSiteFile(id)
    successMessage.value = '附件已删除；列表已按服务端权威状态重读。'
    await loadFiles(activeRecord.value.id, detailRequestId.value)
  } catch (error) {
    errorMessage.value = message(error, '附件删除失败')
  } finally {
    saving.value = false
  }
}

function search(): void {
  pageNo.value = 1
  setQuery()
  void loadList()
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
    <V2Alert v-if="errorMessage" tone="danger" title="请求未完成">{{ errorMessage }}</V2Alert>
    <V2Alert v-if="successMessage" tone="success" title="操作完成">{{ successMessage }}</V2Alert>

    <V2Card
      title="现场日报"
      :subtitle="filter.projectId ? `当前项目：${filter.projectId}` : '可按项目、日期和状态筛选'"
    >
      <template #actions>
        <div class="daily-log-page__actions">
          <V2Button size="small" variant="ghost" @click="loadList()">刷新</V2Button>
          <V2Button v-if="canEdit" size="small" @click="openCreate">新建日报</V2Button>
        </div>
      </template>
      <h1 id="daily-log-title" class="sr-only">现场日报</h1>
      <form class="daily-log-page__filters" @submit.prevent="search">
        <V2Select
          v-if="projectOptions.length"
          v-model="filter.projectId"
          label="项目"
          :options="projectOptions"
          allow-empty
          placeholder="全部项目"
        />
        <V2Input
          v-else
          v-model="filter.projectId"
          label="项目 ID"
          hint="没有项目列表时，可输入已确认的项目 ID。"
        />
        <label>
          开始日期
          <input v-model="filter.startDate" type="date" />
        </label>
        <label>
          结束日期
          <input v-model="filter.endDate" type="date" />
        </label>
        <V2Select
          v-model="filter.status"
          label="状态"
          :options="[
            { value: 'DRAFT', label: '草稿' },
            { value: 'SUBMITTED', label: '已提交' },
          ]"
          allow-empty
          placeholder="全部状态"
        />
        <div class="daily-log-page__actions">
          <V2Button type="submit">查询</V2Button>
          <V2Button variant="secondary" @click="resetFilters">重置</V2Button>
        </div>
      </form>
    </V2Card>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载现场日报"
      description="只读取当前账号可见范围内的日报事实。"
    />
    <V2PageState
      v-else-if="!records.length"
      kind="empty"
      title="暂无现场日报"
      description="调整筛选条件，或由具备权限的账号创建日报草稿。"
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
      <span>共 {{ total }} 条</span>
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
      panel-class="daily-log-page__dialog"
    >
      <form class="daily-log-page__form" @submit.prevent="saveRecord">
        <V2Select
          v-if="projectOptions.length && dialogMode !== 'view'"
          v-model="form.projectId"
          label="项目"
          :options="projectOptions"
          required
        />
        <V2Input
          v-else
          v-model="form.projectId"
          label="项目 ID"
          :disabled="dialogMode === 'view'"
          required
        />
        <label>
          日报日期
          <input v-model="form.reportDate" type="date" :disabled="dialogMode === 'view'" required />
        </label>
        <label class="daily-log-page__span-2">
          施工内容
          <textarea
            v-model="form.constructionContent"
            rows="4"
            :disabled="dialogMode === 'view'"
            required
          />
        </label>
        <label class="daily-log-page__span-2">
          问题与延误
          <textarea v-model="form.issuesDelays" rows="3" :disabled="dialogMode === 'view'" />
        </label>
        <label class="daily-log-page__span-2">
          次日计划
          <textarea v-model="form.nextDayPlan" rows="3" :disabled="dialogMode === 'view'" />
        </label>
        <label class="daily-log-page__span-2">
          天气摘要
          <textarea v-model="form.weatherSummary" rows="2" :disabled="dialogMode === 'view'" />
        </label>
        <label>
          在场人数
          <input
            v-model.number="form.onSiteHeadcount"
            type="number"
            min="0"
            step="1"
            :disabled="dialogMode === 'view'"
          />
        </label>
      </form>

      <template v-if="activeRecord">
        <V2Card
          title="附件"
          :subtitle="activeRecord.status === 'DRAFT' ? '仅草稿可上传/删除' : '已提交附件只读不可变'"
        >
          <template #actions>
            <input
              v-if="canEdit && activeRecord.status === 'DRAFT'"
              type="file"
              @change="onFileChange"
            />
          </template>
          <V2PageState
            v-if="filesLoading"
            kind="loading"
            title="正在加载附件"
            description="附件列表独立读取，不影响日报正文。"
          />
          <div v-else-if="files.length" class="daily-log-page__stack">
            <article v-for="file in files" :key="file.id" class="daily-log-page__row">
              <button type="button" class="daily-log-page__link" @click="downloadFile(file.id)">
                {{ file.originalName }}
              </button>
              <button
                v-if="canEdit && activeRecord.status === 'DRAFT'"
                type="button"
                class="daily-log-page__danger-link"
                @click="removeFile(file.id)"
              >
                删除
              </button>
            </article>
          </div>
          <p v-else class="daily-log-page__empty-copy">暂无附件。</p>
        </V2Card>

        <V2Card
          v-if="activeRecord.scheduleManaged"
          title="WBS 实际进度"
          :subtitle="
            canReportProgress
              ? '仅周计划内任务可填报；提交后统一走权威回读。'
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
                    <input
                      v-model="row.included"
                      type="checkbox"
                      :disabled="dialogMode === 'view' || activeRecord.status !== 'DRAFT'"
                    />
                  </td>
                  <td>{{ row.taskCode }}</td>
                  <td>{{ row.taskName }}</td>
                  <td>
                    <input
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
                    <input
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
                    <input
                      v-model="row.workDescription"
                      type="text"
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
            <V2Button
              v-if="canReportProgress && activeRecord.status === 'DRAFT'"
              size="small"
              variant="secondary"
              :loading="progressSaving"
              @click="saveProgress"
            >
              保存实际进度
            </V2Button>
          </template>
        </V2Card>

        <V2Card
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

        <V2Card title="只读联动事实" subtitle="材料到货、领料、计划任务与审计均只读展示。">
          <div class="daily-log-page__stack">
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

      <template #footer>
        <V2Button variant="secondary" @click="dialogOpen = false">关闭</V2Button>
        <V2Button v-if="dialogMode !== 'view'" :loading="saving" @click="saveRecord">
          保存草稿
        </V2Button>
        <V2Button
          v-if="canSubmitCurrent"
          variant="secondary"
          :loading="saving"
          @click="submitCurrent"
        >
          提交定稿
        </V2Button>
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped>
.daily-log-page {
  display: grid;
  gap: var(--v2-space-3);
  color: var(--v2-color-text);
}
.daily-log-page__filters,
.daily-log-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
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
  justify-content: space-between;
}
.daily-log-page__summary,
.daily-log-page__empty-copy {
  margin: 0;
  color: var(--v2-color-text-secondary);
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
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  font: inherit;
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
  text-align: left;
  vertical-align: top;
}
.daily-log-page__panel {
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
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
  color: var(--v2-color-primary-700, #175cd3);
}
.daily-log-page__danger-link {
  color: var(--v2-color-danger-600, #b42318);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
@media (max-width: 64rem) {
  .daily-log-page__filters,
  .daily-log-page__form {
    grid-template-columns: 1fr;
  }
  .daily-log-page__pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
