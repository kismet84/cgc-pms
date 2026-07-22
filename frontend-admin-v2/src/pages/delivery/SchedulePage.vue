<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type {
  CorrectiveActionCommand,
  PeriodPlanCommand,
  PeriodPlanRecord,
  ScheduleCommand,
  ScheduleDetail,
  ScheduleRecord,
  WbsTaskCommand,
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
} from '@/components'
import { isApiClientError } from '@/services/request'
import {
  calculateScheduleSnapshot,
  createCorrectiveAction,
  createPeriodPlan,
  createSchedule,
  loadSchedule,
  loadSchedules,
  loadScheduleTrace,
  replacePeriodPlanItems,
  replaceWbsTasks,
  submitCorrectiveAction,
  submitPeriodPlan,
  submitSchedule,
} from '@/services/delivery'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import { deliveryLabel } from './labels'

interface EditableWbsTask extends WbsTaskCommand {
  key: string
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const schedules = ref<ScheduleRecord[]>([])
const detail = ref<ScheduleDetail | null>(null)
const traceSummary = ref<string[]>([])
const traceLoaded = ref(false)
const createOpen = ref(false)
const wbsOpen = ref(false)
const periodOpen = ref(false)
const correctiveOpen = ref(false)
const pendingScheduleSubmit = ref<{ id: string; planCode: string } | null>(null)
const detailRequestId = ref(0)
let listController: AbortController | null = null
let detailController: AbortController | null = null

const scheduleForm = reactive<ScheduleCommand>({
  projectId: '',
  planCode: '',
  planName: '',
  plannedStartDate: '',
  plannedEndDate: '',
  remark: '',
})
const wbsRows = ref<EditableWbsTask[]>([])
const periodForm = reactive<
  PeriodPlanCommand & {
    taskIds: string[]
    targetProgress: string
    plannedQuantity: string
  }
>({
  schedulePlanId: '',
  periodType: 'MONTHLY',
  parentPeriodPlanId: '',
  periodCode: '',
  periodName: '',
  startDate: '',
  endDate: '',
  remark: '',
  taskIds: [],
  targetProgress: '100',
  plannedQuantity: '',
})
const correctiveForm = reactive<CorrectiveActionCommand>({
  snapshotId: '',
  actionCode: '',
  reason: '',
  actionPlan: '',
  responsibleUserId: '',
  dueDate: '',
  remark: '',
})
const snapshotDate = ref(new Date().toISOString().slice(0, 10))

const projectId = computed(() => workspace.selectedProjectId || '')
const scheduleId = computed(() =>
  typeof route.params.scheduleId === 'string' ? route.params.scheduleId.trim() : '',
)
const isDetailRoute = computed(() => Boolean(scheduleId.value))
const projectOptions = computed(() =>
  workspace.projects.map((item) => ({ value: item.value, label: item.label })),
)
const projectLabel = computed(() =>
  projectId.value
    ? (workspace.projects.find((item) => item.value === projectId.value)?.label ?? projectId.value)
    : '全部项目',
)
const canMaintain = computed(() => hasPermission('schedule:maintain'))
const canSubmit = computed(() => hasPermission('schedule:submit'))
const canProgress = computed(() => hasPermission('schedule:progress'))
const canCorrect = computed(() => hasPermission('schedule:correct'))
const isEditable = computed(() => ['DRAFT', 'REJECTED'].includes(detail.value?.status ?? ''))
const latestSnapshotStatus = computed(() => detail.value?.latestSnapshot?.status ?? '')
const latestSnapshotNeedsCorrection = computed(() =>
  ['LAGGING', 'OVERDUE'].includes(latestSnapshotStatus.value),
)
const approvedMonthlyPlans = computed(
  () =>
    detail.value?.periodPlans.filter(
      (item) => item.periodType === 'MONTHLY' && item.status === 'APPROVED',
    ) ?? [],
)

function hasPermission(code: string): boolean {
  return (
    session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
    session.hasPermission(code)
  )
}

function resetNotices(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function message(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

function statusTone(status: string): 'info' | 'success' | 'warning' | 'danger' | 'neutral' {
  if (['ACTIVE', 'APPROVED', 'COMPLETED'].includes(status)) return 'success'
  if (['PENDING'].includes(status)) return 'warning'
  if (['REJECTED', 'LAGGING', 'OVERDUE'].includes(status)) return 'danger'
  return 'neutral'
}

async function reloadList(preserveNotice = false): Promise<void> {
  listController?.abort()
  listController = new AbortController()
  loading.value = true
  if (!preserveNotice) resetNotices()
  try {
    schedules.value = await loadSchedules(projectId.value || undefined, listController.signal)
  } catch (error) {
    if (!listController.signal.aborted) {
      schedules.value = []
      detail.value = null
      errorMessage.value = message(error, '项目计划加载失败')
    }
  } finally {
    if (!listController.signal.aborted) loading.value = false
  }
}

async function openDetail(scheduleId: string, preserveNotice = false): Promise<void> {
  detailController?.abort()
  detailController = new AbortController()
  const requestId = ++detailRequestId.value
  detailLoading.value = true
  traceLoaded.value = false
  traceSummary.value = []
  if (!preserveNotice) resetNotices()
  try {
    const current = await loadSchedule(scheduleId, detailController.signal)
    if (requestId !== detailRequestId.value) return
    detail.value = current
    snapshotDate.value = new Date().toISOString().slice(0, 10)
  } catch (error) {
    if (!detailController.signal.aborted && requestId === detailRequestId.value) {
      detail.value = null
      errorMessage.value = message(error, '计划详情加载失败')
    }
  } finally {
    if (requestId === detailRequestId.value) detailLoading.value = false
  }
}

function goToDetail(id: string): void {
  void router.push({ path: `/project-schedule/${id}`, query: route.query })
}

function backToList(): void {
  void router.push({ path: '/project-schedule', query: route.query })
}

function openCreate(): void {
  Object.assign(scheduleForm, {
    projectId: projectId.value,
    planCode: `BASE-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}`,
    planName: '项目基线计划',
    plannedStartDate: new Date().toISOString().slice(0, 10),
    plannedEndDate: '',
    remark: '',
  })
  createOpen.value = true
  resetNotices()
}

async function saveSchedule(): Promise<void> {
  const command = cleanScheduleCommand(scheduleForm)
  if (
    !command.projectId ||
    !command.planCode ||
    !command.planName ||
    !command.plannedStartDate ||
    !command.plannedEndDate
  ) {
    errorMessage.value = '请完整填写项目计划基本信息'
    return
  }
  saving.value = true
  resetNotices()
  try {
    const created = await createSchedule(command)
    createOpen.value = false
    successMessage.value = '项目计划已创建。'
    await reloadList(true)
    goToDetail(created.id)
  } catch (error) {
    errorMessage.value = message(error, '项目计划创建失败')
  } finally {
    saving.value = false
  }
}

function openWbs(): void {
  if (!detail.value) return
  wbsRows.value = detail.value.tasks.length
    ? detail.value.tasks.map((task) => ({
        key: task.id,
        taskCode: task.taskCode,
        taskName: task.taskName,
        parentTaskCode: task.parentTaskCode ?? '',
        predecessorTaskCode: task.predecessorTaskCode ?? '',
        workArea: task.workArea ?? '',
        responsibleUserId: task.responsibleUserId ?? '',
        plannedStartDate: task.plannedStartDate,
        plannedEndDate: task.plannedEndDate,
        weightPercent: task.weightPercent,
        plannedQuantity: task.plannedQuantity ?? '',
        unit: task.unit ?? '',
        remark: task.remark ?? '',
      }))
    : [emptyTask(detail.value.plannedStartDate, detail.value.plannedEndDate)]
  wbsOpen.value = true
  resetNotices()
}

function addTaskRow(): void {
  wbsRows.value.push(
    emptyTask(
      detail.value?.plannedStartDate ?? new Date().toISOString().slice(0, 10),
      detail.value?.plannedEndDate ?? '',
    ),
  )
}

async function saveWbs(): Promise<void> {
  if (!detail.value) return
  const tasks = wbsRows.value.map(cleanTaskCommand)
  if (!tasks.length || tasks.some((task) => !task.taskCode || !task.taskName)) {
    errorMessage.value = 'WBS 至少保留一条完整任务'
    return
  }
  const total = tasks.reduce((sum, task) => sum + Number(task.weightPercent || 0), 0)
  if (Math.abs(total - 100) > 0.0001) {
    errorMessage.value = 'WBS 权重合计必须等于 100%'
    return
  }
  saving.value = true
  resetNotices()
  try {
    const next = await replaceWbsTasks(detail.value.id, detail.value.version ?? 0, tasks)
    detail.value = next
    wbsOpen.value = false
    successMessage.value = 'WBS 已保存。'
    await reloadList(true)
  } catch (error) {
    errorMessage.value = message(error, 'WBS 保存失败')
    await openDetail(detail.value.id, true)
  } finally {
    saving.value = false
  }
}

function openPeriod(periodType: 'MONTHLY' | 'WEEKLY'): void {
  if (!detail.value) return
  const today = new Date().toISOString().slice(0, 10)
  Object.assign(periodForm, {
    schedulePlanId: detail.value.id,
    periodType,
    parentPeriodPlanId: '',
    periodCode: `${periodType === 'MONTHLY' ? 'M' : 'W'}-${today.replaceAll('-', '')}`,
    periodName: periodType === 'MONTHLY' ? '月计划' : '周计划',
    startDate: today,
    endDate: today,
    remark: '',
    taskIds: detail.value.tasks.map((task) => task.id),
    targetProgress: '100',
    plannedQuantity: '',
  })
  periodOpen.value = true
  resetNotices()
}

async function savePeriod(): Promise<void> {
  if (!detail.value) return
  if (!periodForm.taskIds.length) {
    errorMessage.value = '月周计划至少选择一条 WBS 任务'
    return
  }
  if (periodForm.periodType === 'WEEKLY' && !periodForm.parentPeriodPlanId.trim()) {
    errorMessage.value = '周计划必须关联已审批月计划'
    return
  }
  saving.value = true
  resetNotices()
  try {
    const created = await createPeriodPlan(detail.value.id, cleanPeriodCommand(periodForm))
    const itemDetail = await replacePeriodPlanItems(
      created.id,
      created.version ?? 0,
      periodForm.taskIds.map((taskId) => ({
        wbsTaskId: taskId,
        targetProgress: periodForm.targetProgress.trim() || '0',
        plannedQuantity: periodForm.plannedQuantity.trim() || undefined,
      })),
    )
    await submitPeriodPlan(itemDetail.id)
    periodOpen.value = false
    successMessage.value = '月周计划已提交。'
    await openDetail(detail.value.id, true)
    await reloadList(true)
  } catch (error) {
    errorMessage.value = message(error, '月周计划提交失败')
    await openDetail(detail.value.id, true)
  } finally {
    saving.value = false
  }
}

function requestScheduleSubmit(target: { id: string; planCode: string }): void {
  pendingScheduleSubmit.value = { id: target.id, planCode: target.planCode }
}

async function submitCurrentSchedule(): Promise<void> {
  const pending = pendingScheduleSubmit.value
  if (!pending || saving.value) return
  saving.value = true
  resetNotices()
  try {
    const next = await submitSchedule(pending.id)
    detail.value = next
    successMessage.value = '项目计划已提交。'
    await reloadList(true)
  } catch (error) {
    errorMessage.value = message(error, '项目计划提交失败')
    await openDetail(pending.id, true)
  } finally {
    saving.value = false
    pendingScheduleSubmit.value = null
  }
}

async function calculateSnapshot(): Promise<void> {
  if (!detail.value) return
  saving.value = true
  resetNotices()
  try {
    await calculateScheduleSnapshot(detail.value.id, snapshotDate.value)
    successMessage.value = '偏差快照已更新。'
    await openDetail(detail.value.id, true)
  } catch (error) {
    errorMessage.value = message(error, '偏差快照计算失败')
    await openDetail(detail.value.id, true)
  } finally {
    saving.value = false
  }
}

function openCorrective(): void {
  if (!detail.value?.latestSnapshot) return
  Object.assign(correctiveForm, {
    snapshotId: detail.value.latestSnapshot.id,
    actionCode: `COR-${new Date().toISOString().slice(0, 10).replaceAll('-', '')}`,
    reason: '',
    actionPlan: '',
    responsibleUserId: '',
    dueDate: '',
    remark: '',
  })
  correctiveOpen.value = true
  resetNotices()
}

async function saveCorrective(): Promise<void> {
  if (!detail.value) return
  const command = cleanCorrectiveCommand(correctiveForm)
  if (
    !command.snapshotId ||
    !command.actionCode ||
    !command.reason ||
    !command.actionPlan ||
    !command.responsibleUserId ||
    !command.dueDate
  ) {
    errorMessage.value = '请完整填写纠偏信息'
    return
  }
  saving.value = true
  resetNotices()
  try {
    const created = await createCorrectiveAction(detail.value.id, command)
    await submitCorrectiveAction(created.id)
    correctiveOpen.value = false
    successMessage.value = '纠偏单已提交。'
    await openDetail(detail.value.id, true)
  } catch (error) {
    errorMessage.value = message(error, '纠偏单提交失败')
    await openDetail(detail.value.id, true)
  } finally {
    saving.value = false
  }
}

async function loadTrace(): Promise<void> {
  if (!detail.value) return
  saving.value = true
  resetNotices()
  try {
    const trace = await loadScheduleTrace(detail.value.id)
    const latestSnapshot = trace.snapshots.at(-1)
    const latestCorrectiveAction = trace.correctiveActions.at(-1)
    const latestAlert = trace.alerts.at(-1)
    traceSummary.value = [
      `计划：${trace.schedule.planCode} / ${deliveryLabel(trace.schedule.status)}`,
      `WBS / 周期计划：${trace.wbsTasks.length} / ${trace.periodPlans.length}`,
      `日报实绩 / 快照：${trace.dailyProgress.length} / ${trace.snapshots.length}`,
      `纠偏 / 修订：${trace.correctiveActions.length} / ${trace.revisions.length}`,
      `最近快照：${latestSnapshot ? `${latestSnapshot.snapshotDate} / ${deliveryLabel(latestSnapshot.status)}` : '暂无'}`,
      `最近纠偏：${latestCorrectiveAction ? latestCorrectiveAction.actionCode : '暂无'}`,
      `最近预警ID：${latestAlert?.id || '暂无'}`,
    ]
    traceLoaded.value = true
  } catch (error) {
    errorMessage.value = message(error, '追溯关系加载失败')
  } finally {
    saving.value = false
  }
}

watch(
  [projectId, scheduleId],
  async ([, currentScheduleId]) => {
    if (currentScheduleId) {
      schedules.value = []
      await openDetail(currentScheduleId)
      return
    }
    await reloadList()
    if (detail.value) {
      detail.value = null
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
})

function emptyTask(startDate: string, endDate: string): EditableWbsTask {
  return {
    key: `${Date.now()}-${Math.random()}`,
    taskCode: `WBS-${String(wbsRows.value.length + 1).padStart(3, '0')}`,
    taskName: '',
    parentTaskCode: '',
    predecessorTaskCode: '',
    workArea: '',
    responsibleUserId: '',
    plannedStartDate: startDate,
    plannedEndDate: endDate,
    weightPercent: '0',
    plannedQuantity: '',
    unit: '',
    remark: '',
  }
}

function cleanTaskCommand(task: EditableWbsTask): WbsTaskCommand {
  return {
    taskCode: task.taskCode.trim(),
    taskName: task.taskName.trim(),
    parentTaskCode: task.parentTaskCode?.trim() || undefined,
    predecessorTaskCode: task.predecessorTaskCode?.trim() || undefined,
    workArea: task.workArea?.trim() || undefined,
    responsibleUserId: task.responsibleUserId?.trim() || undefined,
    plannedStartDate: task.plannedStartDate,
    plannedEndDate: task.plannedEndDate,
    weightPercent: task.weightPercent.trim() || '0',
    plannedQuantity: task.plannedQuantity?.trim() || undefined,
    unit: task.unit?.trim() || undefined,
    remark: task.remark?.trim() || undefined,
  }
}

function cleanScheduleCommand(form: ScheduleCommand): ScheduleCommand {
  return {
    projectId: form.projectId.trim(),
    planCode: form.planCode.trim(),
    planName: form.planName.trim(),
    plannedStartDate: form.plannedStartDate,
    plannedEndDate: form.plannedEndDate,
    remark: form.remark?.trim() || undefined,
  }
}

function cleanPeriodCommand(
  form: PeriodPlanCommand & { parentPeriodPlanId?: string },
): PeriodPlanCommand {
  return {
    schedulePlanId: form.schedulePlanId.trim(),
    periodType: form.periodType,
    parentPeriodPlanId: form.parentPeriodPlanId?.trim() || undefined,
    periodCode: form.periodCode.trim(),
    periodName: form.periodName.trim(),
    startDate: form.startDate,
    endDate: form.endDate,
    remark: form.remark?.trim() || undefined,
  }
}

function cleanCorrectiveCommand(form: CorrectiveActionCommand): CorrectiveActionCommand {
  return {
    snapshotId: form.snapshotId.trim(),
    actionCode: form.actionCode.trim(),
    reason: form.reason.trim(),
    actionPlan: form.actionPlan.trim(),
    responsibleUserId: form.responsibleUserId.trim(),
    dueDate: form.dueDate,
    remark: form.remark?.trim() || undefined,
  }
}
</script>

<template>
  <section class="schedule-page" aria-labelledby="schedule-title">
    <V2Alert v-if="errorMessage" tone="danger" title="请求未完成">{{ errorMessage }}</V2Alert>
    <V2Alert v-if="successMessage" tone="success" title="操作完成">{{ successMessage }}</V2Alert>

    <V2Card
      :title="isDetailRoute ? '施工履约详情' : '项目计划与施工履约'"
      title-id="schedule-title"
      :heading-level="1"
      :subtitle="
        isDetailRoute && detail
          ? `${detail.planCode} · ${deliveryLabel(detail.status)}`
          : projectLabel
            ? `当前范围：${projectLabel}`
            : '当前范围：全部项目'
      "
    >
      <template #actions>
        <div v-if="!isDetailRoute" class="schedule-page__actions">
          <V2Button size="small" variant="ghost" @click="reloadList()">刷新</V2Button>
          <V2Button v-if="canMaintain" size="small" @click="openCreate">新建基线计划</V2Button>
        </div>
      </template>
      <p v-if="isDetailRoute" class="schedule-page__hint">
        查看计划任务、月周计划、进度偏差与纠偏记录。
      </p>
    </V2Card>

    <V2PageState
      v-if="!isDetailRoute && loading"
      kind="loading"
      title="正在加载项目计划"
      :description="projectId ? '只读取当前项目范围内可见计划。' : '正在读取全部可见项目的计划。'"
      :heading-level="2"
    />
    <V2PageState
      v-else-if="!isDetailRoute && !schedules.length"
      kind="empty"
      :title="projectId ? '当前项目暂无计划' : '全部项目暂无计划'"
      description="具备维护权限的账号可以创建基线计划。"
      :heading-level="2"
    />
    <div v-else-if="!isDetailRoute" class="schedule-page__grid">
      <V2Card
        v-for="item in schedules"
        :key="item.id"
        :title="item.planName"
        :subtitle="`${item.planCode} · V${item.versionNo}`"
      >
        <div class="schedule-page__facts">
          <V2Badge :tone="statusTone(item.status)">{{ deliveryLabel(item.status) }}</V2Badge>
          <span v-if="!projectId">
            {{
              workspace.projects.find((project) => project.value === item.projectId)?.label ??
              `项目 ${item.projectId}`
            }}
          </span>
          <span>{{ item.planType === 'REVISION' ? '修订计划' : '基线计划' }}</span>
          <span>{{ item.plannedStartDate }} 至 {{ item.plannedEndDate }}</span>
        </div>
        <template #footer>
          <div class="schedule-page__actions">
            <V2Button size="small" variant="secondary" @click="goToDetail(item.id)"
              >履约详情</V2Button
            >
            <V2Button
              v-if="canSubmit && ['DRAFT', 'REJECTED'].includes(item.status)"
              size="small"
              variant="ghost"
              @click="requestScheduleSubmit(item)"
            >
              提交审批
            </V2Button>
          </div>
        </template>
      </V2Card>
    </div>

    <V2PageState
      v-if="isDetailRoute && !detail && !detailLoading"
      kind="error"
      title="计划详情不可用"
      description="计划可能已删除、无权查看，或不属于当前项目。"
      :heading-level="2"
    >
      <template #actions>
        <V2Button variant="secondary" @click="backToList">返回计划列表</V2Button>
      </template>
    </V2PageState>

    <V2Card
      v-if="isDetailRoute && (detail || detailLoading)"
      :title="detail?.planName || '计划详情'"
      :subtitle="detail ? `${detail.planCode} · ${deliveryLabel(detail.status)}` : '正在加载详情'"
    >
      <template #actions>
        <div class="schedule-page__actions">
          <V2Button size="small" variant="secondary" @click="backToList">返回计划列表</V2Button>
          <V2Button v-if="detail" size="small" variant="ghost" @click="openDetail(detail.id)"
            >刷新详情</V2Button
          >
          <V2Button
            v-if="detail && canMaintain && isEditable"
            size="small"
            variant="secondary"
            @click="openWbs"
          >
            维护 WBS
          </V2Button>
          <V2Button
            v-if="detail && canSubmit && isEditable"
            size="small"
            variant="secondary"
            :loading="saving"
            @click="requestScheduleSubmit(detail)"
          >
            提交计划
          </V2Button>
        </div>
      </template>

      <V2PageState
        v-if="detailLoading"
        kind="loading"
        title="正在加载计划详情"
        description="读取任务、周期计划、快照和纠偏链。"
        :heading-level="3"
      />
      <template v-else-if="detail">
        <div class="schedule-page__detail-grid">
          <V2Card title="计划概览" :heading-level="3">
            <dl class="schedule-page__definition">
              <dt>计划类型</dt>
              <dd>{{ detail.planType === 'REVISION' ? '修订计划' : '基线计划' }}</dd>
              <dt>计划周期</dt>
              <dd>{{ detail.plannedStartDate }} 至 {{ detail.plannedEndDate }}</dd>
              <dt>当前状态</dt>
              <dd>{{ deliveryLabel(detail.status) }}</dd>
              <dt>备注</dt>
              <dd>{{ detail.remark || '—' }}</dd>
            </dl>
          </V2Card>

          <V2Card title="偏差与纠偏" subtitle="快照与纠偏均走独立权限。" :heading-level="3">
            <div class="schedule-page__snapshot-toolbar">
              <label>
                快照日期
                <input v-model="snapshotDate" type="date" />
              </label>
              <V2Button
                v-if="canProgress"
                size="small"
                variant="secondary"
                :loading="saving"
                @click="calculateSnapshot"
              >
                计算偏差
              </V2Button>
              <V2Button
                v-if="canCorrect && latestSnapshotNeedsCorrection"
                size="small"
                variant="danger"
                @click="openCorrective"
              >
                发起纠偏
              </V2Button>
            </div>
            <dl v-if="detail.latestSnapshot" class="schedule-page__definition">
              <dt>快照日期</dt>
              <dd>{{ detail.latestSnapshot.snapshotDate }}</dd>
              <dt>计划进度</dt>
              <dd>{{ detail.latestSnapshot.plannedProgress }}%</dd>
              <dt>实际进度</dt>
              <dd>{{ detail.latestSnapshot.actualProgress }}%</dd>
              <dt>偏差状态</dt>
              <dd>
                {{ detail.latestSnapshot.deviationPercent }}% /
                {{ deliveryLabel(detail.latestSnapshot.status) }}
              </dd>
            </dl>
            <p v-else class="schedule-page__empty-copy">暂无偏差快照。</p>
          </V2Card>
        </div>

        <V2Card title="WBS 任务" :subtitle="`共 ${detail.tasks.length} 条`" :heading-level="3">
          <div v-if="detail.tasks.length" class="schedule-page__table-wrap">
            <table class="schedule-page__table">
              <thead>
                <tr>
                  <th>任务编码</th>
                  <th>名称</th>
                  <th>前置</th>
                  <th>周期</th>
                  <th>权重</th>
                  <th>实际进度</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="task in detail.tasks" :key="task.id">
                  <td>{{ task.taskCode }}</td>
                  <td>{{ task.taskName }}</td>
                  <td>{{ task.predecessorTaskCode || '—' }}</td>
                  <td>{{ task.plannedStartDate }} 至 {{ task.plannedEndDate }}</td>
                  <td>{{ task.weightPercent }}%</td>
                  <td>{{ task.actualProgress }}%</td>
                  <td>{{ deliveryLabel(task.status) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-else
            kind="empty"
            title="暂无 WBS"
            description="计划提交前至少需要一条 WBS 任务。"
            :heading-level="3"
          />
        </V2Card>

        <V2Card
          title="月周计划"
          :subtitle="`共 ${detail.periodPlans.length} 条`"
          :heading-level="3"
        >
          <template #actions>
            <div class="schedule-page__actions">
              <V2Button
                v-if="canMaintain && detail.status === 'ACTIVE'"
                size="small"
                variant="ghost"
                @click="openPeriod('MONTHLY')"
              >
                新建月计划
              </V2Button>
              <V2Button
                v-if="canMaintain && detail.status === 'ACTIVE'"
                size="small"
                variant="ghost"
                @click="openPeriod('WEEKLY')"
              >
                新建周计划
              </V2Button>
            </div>
          </template>
          <div v-if="detail.periodPlans.length" class="schedule-page__table-wrap">
            <table class="schedule-page__table">
              <thead>
                <tr>
                  <th>类型</th>
                  <th>编码</th>
                  <th>名称</th>
                  <th>周期</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="period in detail.periodPlans" :key="period.id">
                  <td>{{ period.periodType === 'MONTHLY' ? '月计划' : '周计划' }}</td>
                  <td>{{ period.periodCode }}</td>
                  <td>{{ period.periodName }}</td>
                  <td>{{ period.startDate }} 至 {{ period.endDate }}</td>
                  <td>{{ deliveryLabel(period.status) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="schedule-page__empty-copy">暂无月周计划。</p>
        </V2Card>

        <V2Card
          title="纠偏链与追溯"
          :subtitle="`纠偏单 ${detail.correctiveActions.length} 条`"
          :heading-level="3"
        >
          <template #actions>
            <V2Button size="small" variant="ghost" :loading="saving" @click="loadTrace"
              >加载追溯</V2Button
            >
          </template>
          <div v-if="detail.correctiveActions.length" class="schedule-page__stack">
            <article
              v-for="action in detail.correctiveActions"
              :key="action.id"
              class="schedule-page__panel"
            >
              <strong>{{ action.actionCode }}</strong>
              <p>{{ action.reason }}</p>
              <small>{{ action.dueDate }} · {{ deliveryLabel(action.status) }}</small>
            </article>
          </div>
          <p v-else class="schedule-page__empty-copy">暂无纠偏单。</p>
          <ul v-if="traceLoaded" class="schedule-page__trace">
            <li v-for="item in traceSummary" :key="item">{{ item }}</li>
          </ul>
        </V2Card>
      </template>
    </V2Card>

    <V2ConfirmDialog
      :open="Boolean(pendingScheduleSubmit)"
      title="提交项目计划"
      :description="`确认提交计划 ${pendingScheduleSubmit?.planCode ?? ''}？提交后将进入审批流程。`"
      confirm-text="确认提交"
      :loading="saving"
      @close="pendingScheduleSubmit = null"
      @confirm="submitCurrentSchedule"
    />

    <V2Dialog
      v-model:open="createOpen"
      title="新建项目计划"
      description="填写计划基本信息。创建成功后进入详情页。"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
    >
      <form id="schedule-create-form" class="schedule-page__form" @submit.prevent="saveSchedule">
        <V2Select
          v-if="projectOptions.length"
          v-model="scheduleForm.projectId"
          class="schedule-page__span-2"
          label="项目"
          :options="projectOptions"
          required
        />
        <V2Input
          v-else
          v-model="scheduleForm.projectId"
          class="schedule-page__span-2"
          label="项目 ID"
          required
          hint="没有项目列表时，使用已确认的项目 ID。"
        />
        <V2Input v-model="scheduleForm.planCode" label="计划编号" required />
        <V2Input v-model="scheduleForm.planName" label="计划名称" required />
        <label>
          计划开始
          <input v-model="scheduleForm.plannedStartDate" type="date" required />
        </label>
        <label>
          计划完成
          <input v-model="scheduleForm.plannedEndDate" type="date" required />
        </label>
        <label class="schedule-page__span-2">
          说明
          <textarea v-model="scheduleForm.remark" rows="3" />
        </label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="createOpen = false">取消</V2Button>
        <V2Button type="submit" form="schedule-create-form" :loading="saving">创建计划</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="wbsOpen"
      title="维护 WBS"
      description="仅支持单前置 FS；权重合计必须等于 100%。"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard schedule-page__dialog-wide"
    >
      <form id="schedule-wbs-form" class="schedule-page__stack" @submit.prevent="saveWbs">
        <article v-for="(task, index) in wbsRows" :key="task.key" class="schedule-page__panel">
          <div class="schedule-page__panel-actions">
            <strong>任务 {{ index + 1 }}</strong>
            <V2Button type="button" size="small" variant="ghost" @click="wbsRows.splice(index, 1)">
              删除
            </V2Button>
          </div>
          <div class="schedule-page__form">
            <V2Input v-model="task.taskCode" label="任务编码" required />
            <V2Input v-model="task.taskName" label="任务名称" required />
            <V2Input v-model="task.parentTaskCode" label="父任务编码" />
            <V2Input v-model="task.predecessorTaskCode" label="前置任务编码" />
            <V2Input v-model="task.workArea" label="作业面" />
            <V2Input v-model="task.responsibleUserId" label="责任人用户 ID" />
            <label>
              计划开始
              <input v-model="task.plannedStartDate" type="date" />
            </label>
            <label>
              计划完成
              <input v-model="task.plannedEndDate" type="date" />
            </label>
            <V2Input v-model="task.weightPercent" label="权重%" required />
            <V2Input v-model="task.plannedQuantity" label="计划量" />
            <V2Input v-model="task.unit" label="单位" />
            <label class="schedule-page__span-2">
              备注
              <textarea v-model="task.remark" rows="2" />
            </label>
          </div>
        </article>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="addTaskRow">添加任务</V2Button>
        <V2Button variant="secondary" @click="wbsOpen = false">取消</V2Button>
        <V2Button type="submit" form="schedule-wbs-form" :loading="saving">保存 WBS</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="periodOpen"
      :title="periodForm.periodType === 'MONTHLY' ? '新建月计划' : '新建周计划'"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
    >
      <form id="schedule-period-form" class="schedule-page__form" @submit.prevent="savePeriod">
        <V2Select
          v-if="periodForm.periodType === 'WEEKLY'"
          v-model="periodForm.parentPeriodPlanId"
          label="所属月计划"
          :options="
            approvedMonthlyPlans.map((item: PeriodPlanRecord) => ({
              value: item.id,
              label: `${item.periodCode} ${item.periodName}`,
            }))
          "
          required
        />
        <V2Input v-model="periodForm.periodCode" label="计划编码" required />
        <V2Input v-model="periodForm.periodName" label="计划名称" required />
        <label>
          开始日期
          <input v-model="periodForm.startDate" type="date" required />
        </label>
        <label>
          结束日期
          <input v-model="periodForm.endDate" type="date" required />
        </label>
        <V2Input v-model="periodForm.targetProgress" label="目标进度%" required />
        <V2Input v-model="periodForm.plannedQuantity" label="周期计划量" />
        <label class="schedule-page__span-2">
          备注
          <textarea v-model="periodForm.remark" rows="2" />
        </label>
        <fieldset class="schedule-page__span-2 schedule-page__fieldset">
          <legend>纳入任务</legend>
          <label v-for="task in detail?.tasks ?? []" :key="task.id" class="schedule-page__checkbox">
            <input v-model="periodForm.taskIds" type="checkbox" :value="task.id" />
            <span>{{ task.taskCode }} {{ task.taskName }}</span>
          </label>
        </fieldset>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="periodOpen = false">取消</V2Button>
        <V2Button type="submit" form="schedule-period-form" :loading="saving">保存并提交</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="correctiveOpen"
      title="发起纠偏"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
    >
      <form
        id="schedule-corrective-form"
        class="schedule-page__form"
        @submit.prevent="saveCorrective"
      >
        <V2Input v-model="correctiveForm.actionCode" label="纠偏编码" required />
        <V2Input v-model="correctiveForm.responsibleUserId" label="责任人用户 ID" required />
        <label>
          完成期限
          <input v-model="correctiveForm.dueDate" type="date" required />
        </label>
        <label class="schedule-page__span-2">
          偏差原因
          <textarea v-model="correctiveForm.reason" rows="3" required />
        </label>
        <label class="schedule-page__span-2">
          纠偏措施
          <textarea v-model="correctiveForm.actionPlan" rows="4" required />
        </label>
        <label class="schedule-page__span-2">
          备注
          <textarea v-model="correctiveForm.remark" rows="2" />
        </label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="correctiveOpen = false">取消</V2Button>
        <V2Button type="submit" form="schedule-corrective-form" :loading="saving"
          >提交纠偏</V2Button
        >
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped>
.schedule-page {
  display: grid;
  gap: var(--v2-space-3);
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-12);
}
.schedule-page__grid,
.schedule-page__detail-grid {
  display: grid;
  gap: var(--v2-space-3);
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.schedule-page__actions,
.schedule-page__facts,
.schedule-page__snapshot-toolbar,
.schedule-page__panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}
.schedule-page__hint,
.schedule-page__empty-copy {
  margin: 0;
  color: var(--v2-color-text-secondary);
}
.schedule-page__definition {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}
.schedule-page__definition dd,
.schedule-page__definition dt {
  margin: 0;
}
.schedule-page__definition dt {
  color: var(--v2-color-text-secondary);
}
.schedule-page__table-wrap {
  overflow: auto;
}
.schedule-page__table {
  width: 100%;
  border-collapse: collapse;
}
.schedule-page__table th,
.schedule-page__table td {
  padding: 0.75rem;
  border-bottom: 1px solid var(--v2-color-border);
  text-align: left;
  vertical-align: top;
}
.schedule-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.schedule-page__form label {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.schedule-page__form input,
.schedule-page__form textarea {
  min-height: 2.5rem;
  padding: 0 var(--v2-space-3);
  color: var(--v2-color-text);
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
  border-radius: var(--v2-radius-md);
  font: inherit;
}
.schedule-page__form :deep(.v2-field__control) {
  background: transparent;
  border-color: color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
}
.schedule-page__form textarea {
  min-height: 6rem;
  padding: var(--v2-space-2) var(--v2-space-3);
  resize: vertical;
}
.schedule-page__span-2 {
  grid-column: 1 / -1;
}
.schedule-page__stack {
  display: grid;
  gap: var(--v2-space-2);
}
.schedule-page__panel {
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  background: var(--v2-color-surface);
}
.schedule-page__trace {
  margin: var(--v2-space-3) 0 0;
  padding-left: 1.25rem;
  color: var(--v2-color-text-secondary);
}
.schedule-page__fieldset {
  display: grid;
  gap: var(--v2-space-2);
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.schedule-page__checkbox {
  display: flex;
  gap: var(--v2-space-2);
  align-items: center;
  color: var(--v2-color-text);
}
.schedule-page__dialog-wide {
  width: min(72rem, calc(100vw - 2rem));
}
@media (max-width: 64rem) {
  .schedule-page__grid,
  .schedule-page__detail-grid,
  .schedule-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
