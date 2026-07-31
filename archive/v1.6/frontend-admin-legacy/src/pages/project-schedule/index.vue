<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import { useReferenceStore } from '@/stores/reference'
import {
  calculateProgressSnapshot,
  createCorrectiveAction,
  createPeriodPlan,
  createProjectSchedule,
  getProjectSchedule,
  getProjectSchedules,
  getProjectScheduleTrace,
  replacePeriodPlanItems,
  replaceWbsTasks,
  submitCorrectiveAction,
  submitPeriodPlan,
  submitProjectSchedule,
  type ScheduleRow,
  type WbsTaskRequest,
} from '@/api/modules/projectSchedule'

const referenceStore = useReferenceStore()
const { projects } = storeToRefs(referenceStore)
const loading = ref(false)
const projectId = ref<string>()
const schedules = ref<ScheduleRow[]>([])
const selected = ref<ScheduleRow>()
const trace = ref<ScheduleRow>()
const detailOpen = ref(false)
const scheduleOpen = ref(false)
const wbsOpen = ref(false)
const periodOpen = ref(false)
const correctiveOpen = ref(false)
const snapshotDate = ref(new Date().toISOString().slice(0, 10))
const scheduleForm = reactive({
  planCode: '',
  planName: '',
  startDate: '',
  endDate: '',
  remark: '',
})
const wbsRows = ref<WbsTaskRequest[]>([])
const periodForm = reactive({
  periodType: 'MONTHLY' as 'MONTHLY' | 'WEEKLY',
  parentPeriodPlanId: undefined as string | undefined,
  periodCode: '',
  periodName: '',
  startDate: '',
  endDate: '',
  taskIds: [] as string[],
  targetProgress: 100,
})
const correctiveForm = reactive({
  actionCode: '',
  reason: '',
  actionPlan: '',
  responsibleUserId: '',
  dueDate: '',
})

const tasks = computed(() =>
  Array.isArray(selected.value?.tasks) ? (selected.value?.tasks as ScheduleRow[]) : [],
)
const periods = computed(() =>
  Array.isArray(selected.value?.periodPlans) ? (selected.value?.periodPlans as ScheduleRow[]) : [],
)
const approvedMonths = computed(() =>
  periods.value.filter(
    (item) => value(item, 'periodType', 'period_type') === 'MONTHLY' && item.status === 'APPROVED',
  ),
)
const latestSnapshot = computed(() => {
  const row = selected.value?.latestSnapshot
  return row && typeof row === 'object' ? (row as ScheduleRow) : undefined
})
const canEdit = computed(() => ['DRAFT', 'REJECTED'].includes(String(selected.value?.status ?? '')))

function value(row: ScheduleRow | undefined, ...keys: string[]) {
  if (!row) return undefined
  const key = keys.find((item) => row[item] !== undefined)
  return key ? row[key] : undefined
}
function id(row?: ScheduleRow) {
  return String(row?.id ?? '')
}
function statusColor(status: unknown) {
  return (
    (
      {
        ACTIVE: 'success',
        APPROVED: 'success',
        PENDING: 'processing',
        REJECTED: 'error',
        SUPERSEDED: 'default',
        LAGGING: 'error',
        OVERDUE: 'error',
        ON_TRACK: 'success',
      } as Record<string, string>
    )[String(status)] ?? 'default'
  )
}
function statusName(status: unknown) {
  return (
    (
      {
        DRAFT: '草稿',
        PENDING: '审批中',
        ACTIVE: '生效',
        APPROVED: '已审批',
        REJECTED: '已驳回',
        SUPERSEDED: '已被修订',
        LAGGING: '延期',
        OVERDUE: '逾期',
        ON_TRACK: '正常',
        COMPLETED: '完成',
      } as Record<string, string>
    )[String(status)] ?? String(status ?? '-')
  )
}

async function load() {
  if (!projectId.value) {
    schedules.value = []
    return
  }
  loading.value = true
  try {
    schedules.value = await getProjectSchedules(projectId.value)
    if (selected.value) await openDetail(selected.value)
  } finally {
    loading.value = false
  }
}
async function openDetail(row: ScheduleRow) {
  selected.value = await getProjectSchedule(id(row))
  trace.value = undefined
  detailOpen.value = true
}
function openSchedule() {
  if (!projectId.value) return message.warning('请先选择项目')
  const today = new Date().toISOString().slice(0, 10)
  Object.assign(scheduleForm, {
    planCode: `BASE-${today.replaceAll('-', '')}`,
    planName: '项目基线计划',
    startDate: today,
    endDate: '',
    remark: '',
  })
  scheduleOpen.value = true
}
async function saveSchedule() {
  if (!projectId.value || !scheduleForm.endDate) return message.warning('请完整填写计划信息')
  const result = await createProjectSchedule({
    projectId: projectId.value,
    planCode: scheduleForm.planCode,
    planName: scheduleForm.planName,
    plannedStartDate: scheduleForm.startDate,
    plannedEndDate: scheduleForm.endDate,
    remark: scheduleForm.remark,
  })
  scheduleOpen.value = false
  await load()
  await openDetail(result)
}
function openWbs() {
  wbsRows.value = tasks.value.length
    ? tasks.value.map((row) => ({
        taskCode: String(value(row, 'taskCode', 'task_code') ?? ''),
        taskName: String(value(row, 'taskName', 'task_name') ?? ''),
        parentTaskCode: String(value(row, 'parentTaskCode', 'parent_task_code') ?? ''),
        predecessorTaskCode: String(
          value(row, 'predecessorTaskCode', 'predecessor_task_code') ?? '',
        ),
        workArea: String(value(row, 'workArea', 'work_area') ?? ''),
        plannedStartDate: String(value(row, 'plannedStartDate', 'planned_start_date') ?? ''),
        plannedEndDate: String(value(row, 'plannedEndDate', 'planned_end_date') ?? ''),
        weightPercent: Number(value(row, 'weightPercent', 'weight_percent') ?? 0),
        plannedQuantity: Number(value(row, 'plannedQuantity', 'planned_quantity') ?? 0),
        unit: String(row.unit ?? ''),
      }))
    : [newTask()]
  wbsOpen.value = true
}
function newTask(): WbsTaskRequest {
  return {
    taskCode: `WBS-${String(wbsRows.value.length + 1).padStart(3, '0')}`,
    taskName: '',
    plannedStartDate: String(value(selected.value, 'planned_start_date', 'plannedStartDate') ?? ''),
    plannedEndDate: String(value(selected.value, 'planned_end_date', 'plannedEndDate') ?? ''),
    weightPercent: 0,
    plannedQuantity: 0,
  }
}
async function saveWbs() {
  if (!selected.value || wbsRows.value.some((row) => !row.taskCode || !row.taskName))
    return message.warning('请完整填写WBS任务')
  if (
    Math.abs(wbsRows.value.reduce((sum, row) => sum + Number(row.weightPercent || 0), 0) - 100) >
    0.0001
  )
    return message.warning('WBS权重合计必须等于100%')
  selected.value = await replaceWbsTasks(
    id(selected.value),
    Number(value(selected.value, 'version') ?? 0),
    wbsRows.value,
  )
  wbsOpen.value = false
  await load()
}
async function submitScheduleRow(row: ScheduleRow) {
  Modal.confirm({
    title: '提交计划审批',
    content: '提交后将进入多级审批，审批通过后成为项目唯一生效计划。',
    async onOk() {
      await submitProjectSchedule(id(row))
      message.success('已提交审批')
      await load()
    },
  })
}
function openPeriod(type: 'MONTHLY' | 'WEEKLY') {
  if (!selected.value) return
  const today = new Date().toISOString().slice(0, 10)
  Object.assign(periodForm, {
    periodType: type,
    parentPeriodPlanId: undefined,
    periodCode: `${type === 'MONTHLY' ? 'M' : 'W'}-${today.replaceAll('-', '')}`,
    periodName: type === 'MONTHLY' ? '月度施工计划' : '周施工计划',
    startDate: today,
    endDate: today,
    taskIds: tasks.value.map(id),
    targetProgress: 100,
  })
  periodOpen.value = true
}
async function savePeriod() {
  if (!selected.value || !periodForm.taskIds.length) return message.warning('至少选择一条WBS任务')
  if (periodForm.periodType === 'WEEKLY' && !periodForm.parentPeriodPlanId)
    return message.warning('周计划必须关联已审批月计划')
  const row = await createPeriodPlan(id(selected.value), {
    schedulePlanId: id(selected.value),
    periodType: periodForm.periodType,
    parentPeriodPlanId: periodForm.parentPeriodPlanId,
    periodCode: periodForm.periodCode,
    periodName: periodForm.periodName,
    startDate: periodForm.startDate,
    endDate: periodForm.endDate,
  })
  const periodId = id(row)
  await replacePeriodPlanItems(
    periodId,
    Number(value(row, 'version') ?? 0),
    periodForm.taskIds.map((wbsTaskId) => ({
      wbsTaskId,
      targetProgress: periodForm.targetProgress,
    })),
  )
  periodOpen.value = false
  await submitPeriodPlan(periodId)
  message.success('月周计划已保存并提交审批')
  await openDetail(selected.value)
}
async function calculateSnapshot() {
  if (!selected.value) return
  await calculateProgressSnapshot(id(selected.value), snapshotDate.value)
  await openDetail(selected.value)
  message.success('偏差快照已更新')
}
function openCorrective() {
  if (
    !latestSnapshot.value ||
    !['LAGGING', 'OVERDUE'].includes(String(latestSnapshot.value.status))
  )
    return message.warning('当前快照不需要发起纠偏')
  const today = new Date().toISOString().slice(0, 10)
  Object.assign(correctiveForm, {
    actionCode: `COR-${today.replaceAll('-', '')}`,
    reason: '',
    actionPlan: '',
    responsibleUserId: '',
    dueDate: '',
  })
  correctiveOpen.value = true
}
async function saveCorrective() {
  if (
    !selected.value ||
    !latestSnapshot.value ||
    !correctiveForm.responsibleUserId ||
    !correctiveForm.dueDate
  )
    return message.warning('请完整填写纠偏信息')
  const row = await createCorrectiveAction(id(selected.value), {
    snapshotId: id(latestSnapshot.value),
    actionCode: correctiveForm.actionCode,
    reason: correctiveForm.reason,
    actionPlan: correctiveForm.actionPlan,
    responsibleUserId: correctiveForm.responsibleUserId,
    dueDate: correctiveForm.dueDate,
  })
  await submitCorrectiveAction(id(row))
  correctiveOpen.value = false
  message.success('纠偏单已提交审批；通过后将自动生成计划修订草稿')
  await openDetail(selected.value)
}
async function loadTrace() {
  if (!selected.value) return
  trace.value = await getProjectScheduleTrace(id(selected.value))
}

onMounted(async () => {
  await referenceStore.fetchProjects()
})
</script>

<template>
  <div class="schedule-page">
    <header class="page-head">
      <div>
        <h2>项目计划与施工履约</h2>
        <p>基线 → WBS → 月/周计划 → 日报实绩 → 偏差预警 → 纠偏 → 计划修订</p>
      </div>
      <a-space
        ><a-select
          v-model:value="projectId"
          show-search
          allow-clear
          placeholder="选择项目"
          style="width: 280px"
          :field-names="{ label: 'projectName', value: 'id' }"
          :options="projects ?? []"
          @change="load"
        /><a-button type="primary" @click="openSchedule">新建基线计划</a-button></a-space
      >
    </header>

    <a-alert
      type="info"
      show-icon
      message="计划版本受审批控制；任一项目同一时间仅允许一个生效版本，纠偏审批通过会生成修订草稿。"
    />
    <a-table :data-source="schedules" :loading="loading" row-key="id" :pagination="false">
      <a-table-column title="版本" key="version"
        ><template #default="{ record }"
          >V{{ value(record, 'versionNo', 'version_no') }}</template
        ></a-table-column
      >
      <a-table-column title="计划编码" key="code"
        ><template #default="{ record }">{{
          value(record, 'planCode', 'plan_code')
        }}</template></a-table-column
      >
      <a-table-column title="计划名称" key="name"
        ><template #default="{ record }">{{
          value(record, 'planName', 'plan_name')
        }}</template></a-table-column
      >
      <a-table-column title="类型" key="type"
        ><template #default="{ record }">{{
          value(record, 'planType', 'plan_type') === 'REVISION' ? '修订计划' : '基线计划'
        }}</template></a-table-column
      >
      <a-table-column title="计划周期" key="dates"
        ><template #default="{ record }"
          >{{ value(record, 'plannedStartDate', 'planned_start_date') }} 至
          {{ value(record, 'plannedEndDate', 'planned_end_date') }}</template
        ></a-table-column
      >
      <a-table-column title="状态" key="status"
        ><template #default="{ record }"
          ><a-tag :color="statusColor(record.status)">{{
            statusName(record.status)
          }}</a-tag></template
        ></a-table-column
      >
      <a-table-column title="操作" key="actions"
        ><template #default="{ record }"
          ><a-space
            ><a-button type="link" @click="openDetail(record)">履约详情</a-button
            ><a-button
              v-if="['DRAFT', 'REJECTED'].includes(String(record.status))"
              type="link"
              @click="submitScheduleRow(record)"
              >提交审批</a-button
            ></a-space
          ></template
        ></a-table-column
      >
    </a-table>
    <a-empty
      v-if="projectId && !schedules.length && !loading"
      description="该项目尚未建立基线计划"
    />

    <a-drawer v-model:open="detailOpen" title="计划履约详情" width="92vw">
      <template v-if="selected">
        <a-descriptions bordered size="small" :column="4">
          <a-descriptions-item label="计划">{{
            value(selected, 'plan_name', 'planName')
          }}</a-descriptions-item
          ><a-descriptions-item label="版本"
            >V{{ value(selected, 'version_no', 'versionNo') }}</a-descriptions-item
          ><a-descriptions-item label="类型">{{
            value(selected, 'plan_type', 'planType')
          }}</a-descriptions-item
          ><a-descriptions-item label="状态"
            ><a-tag :color="statusColor(selected.status)">{{
              statusName(selected.status)
            }}</a-tag></a-descriptions-item
          >
        </a-descriptions>
        <a-tabs>
          <a-tab-pane key="wbs" tab="WBS">
            <a-space class="toolbar"
              ><a-button v-if="canEdit" type="primary" @click="openWbs">维护WBS</a-button
              ><a-button v-if="canEdit" @click="submitScheduleRow(selected)"
                >提交计划审批</a-button
              ></a-space
            >
            <a-table :data-source="tasks" row-key="id" :pagination="false" size="small"
              ><a-table-column title="编码" data-index="task_code" /><a-table-column
                title="任务"
                data-index="task_name" /><a-table-column
                title="作业面"
                data-index="work_area" /><a-table-column
                title="计划开始"
                data-index="planned_start_date" /><a-table-column
                title="计划完成"
                data-index="planned_end_date" /><a-table-column
                title="权重%"
                data-index="weight_percent" /><a-table-column
                title="实际进度%"
                data-index="actual_progress" /><a-table-column title="状态" data-index="status"
            /></a-table>
          </a-tab-pane>
          <a-tab-pane key="period" tab="月/周计划">
            <a-space class="toolbar"
              ><a-button :disabled="selected.status !== 'ACTIVE'" @click="openPeriod('MONTHLY')"
                >新建月计划</a-button
              ><a-button :disabled="selected.status !== 'ACTIVE'" @click="openPeriod('WEEKLY')"
                >新建周计划</a-button
              ></a-space
            >
            <a-table :data-source="periods" row-key="id" :pagination="false" size="small"
              ><a-table-column title="类型" key="type"
                ><template #default="{ record }">{{
                  value(record, 'periodType', 'period_type') === 'MONTHLY' ? '月计划' : '周计划'
                }}</template></a-table-column
              ><a-table-column title="编码" data-index="periodCode" /><a-table-column
                title="名称"
                data-index="periodName"
              /><a-table-column title="开始" data-index="startDate" /><a-table-column
                title="结束"
                data-index="endDate"
              /><a-table-column title="状态" key="status"
                ><template #default="{ record }"
                  ><a-tag :color="statusColor(record.status)">{{
                    statusName(record.status)
                  }}</a-tag></template
                ></a-table-column
              ></a-table
            >
          </a-tab-pane>
          <a-tab-pane key="deviation" tab="偏差与纠偏">
            <a-space class="toolbar"
              ><a-date-picker v-model:value="snapshotDate" value-format="YYYY-MM-DD" /><a-button
                @click="calculateSnapshot"
                >计算偏差</a-button
              ><a-button
                danger
                :disabled="
                  !latestSnapshot || !['LAGGING', 'OVERDUE'].includes(String(latestSnapshot.status))
                "
                @click="openCorrective"
                >发起纠偏</a-button
              ></a-space
            >
            <a-descriptions v-if="latestSnapshot && latestSnapshot.id" bordered :column="4"
              ><a-descriptions-item label="快照日">{{
                value(latestSnapshot, 'snapshot_date', 'snapshotDate')
              }}</a-descriptions-item
              ><a-descriptions-item label="计划进度"
                >{{
                  value(latestSnapshot, 'planned_progress', 'plannedProgress')
                }}%</a-descriptions-item
              ><a-descriptions-item label="实际进度"
                >{{
                  value(latestSnapshot, 'actual_progress', 'actualProgress')
                }}%</a-descriptions-item
              ><a-descriptions-item label="偏差"
                ><a-tag :color="statusColor(latestSnapshot.status)"
                  >{{ value(latestSnapshot, 'deviation_percent', 'deviationPercent') }}% /
                  {{ statusName(latestSnapshot.status) }}</a-tag
                ></a-descriptions-item
              ></a-descriptions
            >
            <a-empty v-else description="暂无偏差快照" />
          </a-tab-pane>
          <a-tab-pane key="trace" tab="全链路追溯"
            ><a-button class="toolbar" @click="loadTrace">加载追溯关系</a-button>
            <pre v-if="trace" class="trace">{{ JSON.stringify(trace, null, 2) }}</pre>
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-drawer>

    <a-modal v-model:open="scheduleOpen" title="新建项目基线计划" @ok="saveSchedule"
      ><a-form layout="vertical"
        ><a-form-item label="计划编码" required
          ><a-input v-model:value="scheduleForm.planCode" /></a-form-item
        ><a-form-item label="计划名称" required
          ><a-input v-model:value="scheduleForm.planName" /></a-form-item
        ><a-row :gutter="12"
          ><a-col :span="12"
            ><a-form-item label="计划开始" required
              ><a-date-picker
                v-model:value="scheduleForm.startDate"
                value-format="YYYY-MM-DD"
                style="width: 100%" /></a-form-item></a-col
          ><a-col :span="12"
            ><a-form-item label="计划完成" required
              ><a-date-picker
                v-model:value="scheduleForm.endDate"
                value-format="YYYY-MM-DD"
                style="width: 100%" /></a-form-item></a-col></a-row
        ><a-form-item label="说明"
          ><a-textarea v-model:value="scheduleForm.remark" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="wbsOpen" title="维护WBS任务" width="92vw" @ok="saveWbs"
      ><a-alert
        type="warning"
        show-icon
        message="提交前权重合计必须等于100%；父任务和前置任务均使用本表任务编码。"
      /><a-table :data-source="wbsRows" :pagination="false" size="small"
        ><a-table-column title="任务编码"
          ><template #default="{ record }"
            ><a-input v-model:value="record.taskCode" /></template></a-table-column
        ><a-table-column title="任务名称"
          ><template #default="{ record }"
            ><a-input v-model:value="record.taskName" /></template></a-table-column
        ><a-table-column title="父任务编码"
          ><template #default="{ record }"
            ><a-input v-model:value="record.parentTaskCode" /></template></a-table-column
        ><a-table-column title="前置任务编码"
          ><template #default="{ record }"
            ><a-input v-model:value="record.predecessorTaskCode" /></template></a-table-column
        ><a-table-column title="开始"
          ><template #default="{ record }"
            ><a-date-picker
              v-model:value="record.plannedStartDate"
              value-format="YYYY-MM-DD" /></template></a-table-column
        ><a-table-column title="完成"
          ><template #default="{ record }"
            ><a-date-picker
              v-model:value="record.plannedEndDate"
              value-format="YYYY-MM-DD" /></template></a-table-column
        ><a-table-column title="权重%"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="record.weightPercent"
              :min="0.0001"
              :max="100" /></template></a-table-column
        ><a-table-column title="计划量"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="record.plannedQuantity"
              :min="0" /></template></a-table-column
        ><a-table-column title="操作"
          ><template #default="{ index }"
            ><a-button danger type="link" @click="wbsRows.splice(index, 1)"
              >删除</a-button
            ></template
          ></a-table-column
        ></a-table
      ><a-button class="toolbar" @click="wbsRows.push(newTask())">添加任务</a-button></a-modal
    >
    <a-modal
      v-model:open="periodOpen"
      :title="periodForm.periodType === 'MONTHLY' ? '新建月计划' : '新建周计划'"
      @ok="savePeriod"
      ><a-form layout="vertical"
        ><a-form-item v-if="periodForm.periodType === 'WEEKLY'" label="所属已审批月计划" required
          ><a-select
            v-model:value="periodForm.parentPeriodPlanId"
            :options="
              approvedMonths.map((row) => ({
                label: value(row, 'periodName', 'period_name'),
                value: id(row),
              }))
            " /></a-form-item
        ><a-form-item label="计划编码"
          ><a-input v-model:value="periodForm.periodCode" /></a-form-item
        ><a-form-item label="计划名称"
          ><a-input v-model:value="periodForm.periodName" /></a-form-item
        ><a-row :gutter="12"
          ><a-col :span="12"
            ><a-date-picker
              v-model:value="periodForm.startDate"
              value-format="YYYY-MM-DD"
              style="width: 100%" /></a-col
          ><a-col :span="12"
            ><a-date-picker
              v-model:value="periodForm.endDate"
              value-format="YYYY-MM-DD"
              style="width: 100%" /></a-col></a-row
        ><a-form-item label="纳入WBS任务"
          ><a-select
            v-model:value="periodForm.taskIds"
            mode="multiple"
            :options="
              tasks.map((row) => ({
                label: `${value(row, 'task_code', 'taskCode')} ${value(row, 'task_name', 'taskName')}`,
                value: id(row),
              }))
            " /></a-form-item
        ><a-form-item label="本周期目标进度%"
          ><a-input-number
            v-model:value="periodForm.targetProgress"
            :min="0"
            :max="100" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="correctiveOpen" title="发起进度纠偏" @ok="saveCorrective"
      ><a-form layout="vertical"
        ><a-form-item label="纠偏编码"
          ><a-input v-model:value="correctiveForm.actionCode" /></a-form-item
        ><a-form-item label="偏差原因"
          ><a-textarea v-model:value="correctiveForm.reason" /></a-form-item
        ><a-form-item label="纠偏措施"
          ><a-textarea v-model:value="correctiveForm.actionPlan" /></a-form-item
        ><a-form-item label="责任人用户ID"
          ><a-input v-model:value="correctiveForm.responsibleUserId" /></a-form-item
        ><a-form-item label="完成期限"
          ><a-date-picker
            v-model:value="correctiveForm.dueDate"
            value-format="YYYY-MM-DD" /></a-form-item></a-form
    ></a-modal>
  </div>
</template>

<style scoped>
.schedule-page {
  display: grid;
  gap: 16px;
}
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.page-head h2 {
  margin: 0;
}
.page-head p {
  margin: 4px 0 0;
  color: var(--text-secondary);
}
.toolbar {
  margin: 12px 0;
}
.trace {
  max-height: 520px;
  overflow: auto;
  padding: 14px;
  background: var(--surface-muted);
  border-radius: 8px;
  white-space: pre-wrap;
}
@media (width < 900px) {
  .page-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
