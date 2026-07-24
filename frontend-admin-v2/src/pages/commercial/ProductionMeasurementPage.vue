<script setup lang="ts">
import type {
  ContractRecord,
  MeasurementAmountRow,
  MeasurementPeriodCommand,
  MeasurementSaveCommand,
  OwnerMeasurementReviewCommand,
  OwnerMeasurementSubmissionCommand,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  useToastMessage,
} from '@/components'
import { uploadSiteFile } from '@/services/delivery'
import {
  closeMeasurementPeriod,
  createMeasurement,
  createMeasurementPeriod,
  loadContractPage,
  loadMeasurementPeriods,
  loadMeasurementSettlementTrace,
  loadMeasurementSources,
  loadMeasurements,
  loadOwnerMeasurementSubmission,
  loadOwnerMeasurementSubmissions,
  reviewOwnerMeasurement,
  submitMeasurement,
  submitOwnerMeasurement,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
type SourceLine = { source: MeasurementAmountRow; selected: boolean; currentQuantity: string }
type Dialog = 'closed' | 'period' | 'measurement' | 'owner' | 'review' | 'trace'
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const projectId = ref('')
const status = ref('')
const contracts = ref<ContractRecord[]>([])
const periods = ref<MeasurementAmountRow[]>([])
const creationPeriods = ref<MeasurementAmountRow[]>([])
const measurements = ref<MeasurementAmountRow[]>([])
const submissions = ref<MeasurementAmountRow[]>([])
const expandedMeasurements = ref<Set<string>>(new Set())
const sourceLines = ref<SourceLine[]>([])
const selected = ref<MeasurementAmountRow | null>(null)
const trace = ref<MeasurementAmountRow | null>(null)
const dialog = ref<Dialog>('closed')
const loading = ref(false)
const detailLoading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const evidenceFile = ref<File | null>(null)
let controller: AbortController | null = null
let detailController: AbortController | null = null
let generation = 0
const today = new Date().toISOString().slice(0, 10)
const periodForm = reactive<MeasurementPeriodCommand>({
  projectId: '',
  contractId: '',
  periodCode: today.slice(0, 7),
  periodName: `${today.slice(0, 7)} 月度计量`,
  startDate: `${today.slice(0, 7)}-01`,
  endDate: today,
  cutoffDate: today,
  remark: null,
})
const measurementForm = reactive({
  projectId: '',
  contractId: '',
  periodId: '',
  measureDate: today,
  remark: '',
})
const ownerForm = reactive({ externalDocumentNo: '', remark: '' })
const reviewForm = reactive({
  decision: 'CONFIRMED' as 'CONFIRMED' | 'RETURNED',
  reviewerName: '',
  reviewComment: '',
  settlementDate: today,
  dueDate: today,
  taxAmount: '0',
  retentionAmount: '0',
})
const reviewLines = ref<
  Array<{ measurementLineId: string; confirmedQuantity: string; deductionReason: string }>
>([])
const canQuery = computed(() => session.hasPermission('measurement:query'))
const canMaintain = computed(() => session.hasPermission('measurement:maintain'))
const canSubmit = computed(() => session.hasPermission('measurement:submit'))
const canOwnerSubmit = computed(() => session.hasPermission('measurement:owner:submit'))
const canOwnerReview = computed(() => session.hasPermission('measurement:owner:review'))
const contractOptions = computed(() =>
  contracts.value.map((c) => ({ value: c.id, label: c.contractName })),
)
const projectOptions = computed(() => workspace.projects)
const periodOptions = computed(() =>
  creationPeriods.value
    .filter((p) => text(p, 'status') === 'OPEN')
    .map((p) => ({
      value: text(p, 'id'),
      label: text(p, 'period_name', 'periodName', 'period_code'),
    })),
)
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '内部审批中' },
  { value: 'INTERNAL_APPROVED', label: '内部已通过' },
  { value: 'OWNER_SUBMITTED', label: '已报业主' },
  { value: 'OWNER_RETURNED', label: '业主退回' },
  { value: 'SETTLEMENT_CREATED', label: '已生成结算' },
]
const statusLabels: Record<string, string> = {
  OPEN: '开放',
  CLOSED: '已关闭',
  DRAFT: '草稿',
  REJECTED: '已驳回',
  PENDING: '内部审批中',
  INTERNAL_APPROVED: '内部已通过',
  APPROVED: '内部已通过',
  OWNER_SUBMITTED: '已报业主',
  OWNER_RETURNED: '业主退回',
  SUBMITTED: '已报送',
  CONFIRMED: '已核定',
  RETURNED: '已退回',
  SETTLEMENT_CREATED: '已生成结算',
}
const statusLabel = (value: string) => statusLabels[value] || value || '—'
const text = (row: MeasurementAmountRow | undefined, ...keys: string[]) => {
  for (const key of keys) {
    if (row?.[key] !== undefined && row[key] !== null) return String(row[key])
  }
  return ''
}
const visibleMeasurements = computed(() => measurements.value)
const periodFor = (row: MeasurementAmountRow) =>
  periods.value.find((period) => text(period, 'id') === text(row, 'period_id'))
const projectLabel = (row: MeasurementAmountRow) =>
  workspace.projects.find((project) => project.value === text(row, 'project_id'))?.label || '—'
const submissionsFor = (row: MeasurementAmountRow) =>
  submissions.value.filter(
    (submission) =>
      text(submission, 'measurement_id') === text(row, 'id') ||
      text(submission, 'measure_code') === text(row, 'measure_code'),
  )
const approvalStatus = (row: MeasurementAmountRow) =>
  text(row, 'approval_status') ||
  (['DRAFT', 'REJECTED', 'PENDING'].includes(text(row, 'status'))
    ? text(row, 'status')
    : 'APPROVED')
const ownerStatus = (row: MeasurementAmountRow) => {
  const latest = submissionsFor(row)[0]
  if (latest) return text(latest, 'status')
  const measurementStatus = text(row, 'status')
  if (measurementStatus === 'OWNER_SUBMITTED') return 'SUBMITTED'
  if (measurementStatus === 'OWNER_RETURNED') return 'RETURNED'
  if (measurementStatus === 'OWNER_CONFIRMED') return 'CONFIRMED'
  if (measurementStatus === 'SETTLEMENT_CREATED') return 'SETTLEMENT_CREATED'
  return ''
}
const statusTone = (value: string): 'neutral' | 'info' | 'success' | 'warning' | 'danger' => {
  if (['REJECTED', 'RETURNED', 'OWNER_RETURNED'].includes(value)) return 'warning'
  if (['CONFIRMED', 'SETTLEMENT_CREATED'].includes(value)) return 'success'
  if (['OPEN', 'APPROVED', 'INTERNAL_APPROVED', 'SUBMITTED', 'OWNER_SUBMITTED'].includes(value))
    return 'info'
  return 'neutral'
}
const dateText = (row: MeasurementAmountRow, ...keys: string[]) =>
  text(row, ...keys).slice(0, 10) || '—'
const isExpanded = (row: MeasurementAmountRow) => expandedMeasurements.value.has(text(row, 'id'))
function toggleExpanded(row: MeasurementAmountRow) {
  const id = text(row, 'id')
  const next = new Set(expandedMeasurements.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedMeasurements.value = next
}
const decimal = (value: string, allowZero = false) =>
  /^(?:0|[1-9]\d*)(?:\.\d{1,4})?$/.test(value) && (allowZero || !/^0(?:\.0+)?$/.test(value))
const errorText = (e: unknown, f: string) =>
  isApiClientError(e) ? e.message : e instanceof Error ? e.message : f
const needsReload = (e: unknown) => isApiClientError(e) && (e.status === 409 || e.status === 422)
function bounds() {
  return reportPeriodBounds(typeof route.query.period === 'string' ? route.query.period : null)
}
async function load() {
  if (!canQuery.value) return
  projectId.value = typeof route.query.projectId === 'string' ? route.query.projectId : ''
  status.value = typeof route.query.status === 'string' ? route.query.status : ''
  controller?.abort()
  const current = new AbortController()
  controller = current
  const token = ++generation
  loading.value = true
  errorMessage.value = ''
  const date = bounds()
  try {
    const query = {
      projectId: projectId.value || undefined,
      status: status.value || undefined,
      startDate: date?.startDate,
      endDate: date?.endDate,
    }
    const [periodRows, measurementRows, submissionRows] = await Promise.all([
      loadMeasurementPeriods(query, current.signal),
      loadMeasurements(query, current.signal),
      loadOwnerMeasurementSubmissions(query, current.signal),
    ])
    if (token !== generation) return
    periods.value = periodRows
    measurements.value = measurementRows
    submissions.value = submissionRows
  } catch (e) {
    if (!current.signal.aborted && token === generation) {
      periods.value = []
      measurements.value = []
      submissions.value = []
      errorMessage.value = errorText(e, '产值计量加载失败')
    }
  } finally {
    if (token === generation) loading.value = false
  }
}
async function applyFilter() {
  await router.replace({
    path: '/production-measurement',
    query: {
      ...route.query,
      status: status.value || undefined,
    },
    hash: route.hash,
  })
}
async function changeStatus(value: string) {
  status.value = value
  await applyFilter()
}
async function run(action: () => Promise<unknown>, success: string) {
  if (actionBusy.value) return
  actionBusy.value = true
  errorMessage.value = ''
  try {
    await action()
    successMessage.value = success
    dialog.value = 'closed'
    await load()
  } catch (e) {
    const message = errorText(e, '产值计量操作失败')
    if (needsReload(e)) await load()
    errorMessage.value = message
  } finally {
    actionBusy.value = false
  }
}
function selectFile(event: Event) {
  evidenceFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}
async function loadCreationContracts(selectedProjectId: string) {
  try {
    contracts.value = selectedProjectId
      ? (
          await loadContractPage({
            pageNo: 1,
            pageSize: 100,
            projectId: selectedProjectId,
          })
        ).records
      : []
  } catch (e) {
    contracts.value = []
    errorMessage.value = errorText(e, '业主合同加载失败')
  }
}
async function changePeriodProject(value: string) {
  periodForm.projectId = value
  periodForm.contractId = ''
  await loadCreationContracts(value)
}
async function openPeriod() {
  Object.assign(periodForm, { projectId: projectId.value, contractId: '' })
  await loadCreationContracts(periodForm.projectId)
  dialog.value = 'period'
}
async function savePeriod() {
  if (!periodForm.projectId || !periodForm.contractId) {
    errorMessage.value = '请选择项目和业主合同'
    return
  }
  await run(() => createMeasurementPeriod({ ...periodForm }), '计量期间已创建')
}
async function changeMeasurementProject(value: string) {
  Object.assign(measurementForm, { projectId: value, contractId: '', periodId: '' })
  creationPeriods.value = []
  sourceLines.value = []
  await loadCreationContracts(value)
}
async function changeMeasurementContract(value: string) {
  measurementForm.contractId = value
  measurementForm.periodId = ''
  creationPeriods.value = []
  sourceLines.value = []
  if (!measurementForm.projectId || !value) return
  const date = bounds()
  try {
    const [availablePeriods, sources] = await Promise.all([
      loadMeasurementPeriods({
        projectId: measurementForm.projectId,
        contractId: value,
        startDate: date?.startDate,
        endDate: date?.endDate,
      }),
      loadMeasurementSources(measurementForm.projectId, value),
    ])
    creationPeriods.value = availablePeriods
    measurementForm.periodId = periodOptions.value[0]?.value ?? ''
    sourceLines.value = sources.map((source) => ({
      source,
      selected: false,
      currentQuantity: '',
    }))
  } catch (e) {
    errorMessage.value = errorText(e, '计量期间和来源加载失败')
  }
}
async function openMeasurement() {
  Object.assign(measurementForm, {
    projectId: projectId.value,
    contractId: '',
    periodId: '',
  })
  creationPeriods.value = []
  sourceLines.value = []
  await loadCreationContracts(measurementForm.projectId)
  sourceLines.value.forEach((row) => {
    row.selected = false
    row.currentQuantity = ''
  })
  evidenceFile.value = null
  dialog.value = 'measurement'
}
async function saveMeasurement() {
  const chosen = sourceLines.value.filter((row) => row.selected)
  if (
    !measurementForm.periodId ||
    !measurementForm.projectId ||
    !measurementForm.contractId ||
    !evidenceFile.value ||
    !chosen.length ||
    chosen.some((row) => !decimal(row.currentQuantity))
  ) {
    errorMessage.value = '请选择期间、真实附件和至少一条正数计量来源'
    return
  }
  const command: MeasurementSaveCommand = {
    projectId: measurementForm.projectId,
    contractId: measurementForm.contractId,
    periodId: measurementForm.periodId,
    measureDate: measurementForm.measureDate,
    attachmentCount: 1,
    remark: measurementForm.remark || null,
    lines: chosen.map((row) => ({
      contractItemId:
        text(row.source, 'sourceType') === 'CONTRACT_ITEM' ? text(row.source, 'sourceId') : null,
      contractChangeId:
        text(row.source, 'sourceType') === 'CONTRACT_CHANGE' ? text(row.source, 'sourceId') : null,
      currentQuantity: row.currentQuantity,
      evidenceCount: 1,
    })),
  }
  await run(async () => {
    const created = await createMeasurement(command)
    await uploadSiteFile(
      evidenceFile.value!,
      'PRODUCTION_MEASUREMENT',
      text(created, 'id'),
      'MEASUREMENT_EVIDENCE',
    )
  }, '产值计量草稿已创建')
}
function openOwner(row: MeasurementAmountRow) {
  selected.value = row
  evidenceFile.value = null
  dialog.value = 'owner'
}
async function saveOwner() {
  if (!selected.value || !evidenceFile.value) {
    errorMessage.value = '请上传真实业主报量文件'
    return
  }
  const command: OwnerMeasurementSubmissionCommand = {
    externalDocumentNo: ownerForm.externalDocumentNo || null,
    attachmentCount: 1,
    remark: ownerForm.remark || null,
    version: text(selected.value, 'version'),
  }
  await run(async () => {
    const created = await submitOwnerMeasurement(text(selected.value!, 'id'), command)
    await uploadSiteFile(
      evidenceFile.value!,
      'OWNER_MEASUREMENT_SUBMISSION',
      text(created, 'id'),
      'OWNER_MEASUREMENT_REPORT',
    )
  }, '业主报量已登记')
}
async function openReview(row: MeasurementAmountRow) {
  detailController?.abort()
  const current = new AbortController()
  detailController = current
  selected.value = row
  dialog.value = 'review'
  detailLoading.value = true
  evidenceFile.value = null
  try {
    const value = await loadOwnerMeasurementSubmission(text(row, 'id'), current.signal)
    const raw = Array.isArray(value.lines) ? (value.lines as MeasurementAmountRow[]) : []
    reviewLines.value = raw.map((line) => ({
      measurementLineId: text(line, 'measurement_line_id', 'id'),
      confirmedQuantity: text(line, 'submitted_quantity'),
      deductionReason: '',
    }))
  } catch (e) {
    if (!current.signal.aborted) errorMessage.value = errorText(e, '业主报量详情加载失败')
  } finally {
    detailLoading.value = false
  }
}
async function saveReview() {
  if (
    !selected.value ||
    !reviewForm.reviewerName.trim() ||
    (reviewForm.decision === 'RETURNED' && !reviewForm.reviewComment.trim()) ||
    (reviewForm.decision === 'CONFIRMED' &&
      (!evidenceFile.value ||
        reviewLines.value.some((line) => !decimal(line.confirmedQuantity, true))))
  ) {
    errorMessage.value = '请完整填写核定信息、数量和真实附件'
    return
  }
  const command: OwnerMeasurementReviewCommand = {
    decision: reviewForm.decision,
    reviewerName: reviewForm.reviewerName.trim(),
    reviewComment: reviewForm.reviewComment || null,
    settlementDate: reviewForm.decision === 'CONFIRMED' ? reviewForm.settlementDate : null,
    dueDate: reviewForm.decision === 'CONFIRMED' ? reviewForm.dueDate : null,
    taxAmount: reviewForm.decision === 'CONFIRMED' ? reviewForm.taxAmount : null,
    retentionAmount: reviewForm.decision === 'CONFIRMED' ? reviewForm.retentionAmount : null,
    attachmentCount: reviewForm.decision === 'CONFIRMED' ? 1 : null,
    lines:
      reviewForm.decision === 'CONFIRMED' ? reviewLines.value.map((line) => ({ ...line })) : [],
    version: text(selected.value, 'version'),
  }
  await run(
    async () => {
      if (reviewForm.decision === 'CONFIRMED')
        await uploadSiteFile(
          evidenceFile.value!,
          'OWNER_MEASUREMENT_SUBMISSION',
          text(selected.value!, 'id'),
          'OWNER_CONFIRMATION',
        )
      await reviewOwnerMeasurement(text(selected.value!, 'id'), command)
    },
    reviewForm.decision === 'CONFIRMED' ? '业主核定已登记' : '业主退回已登记',
  )
}
async function openTrace(row: MeasurementAmountRow) {
  dialog.value = 'trace'
  detailLoading.value = true
  trace.value = null
  try {
    trace.value = await loadMeasurementSettlementTrace(text(row, 'settlement_id', 'id'))
  } catch (e) {
    errorMessage.value = errorText(e, '结算追溯加载失败')
  } finally {
    detailLoading.value = false
  }
}
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => {
  controller?.abort()
  detailController?.abort()
})
</script>
<template>
  <div class="measurement-page">
    <V2PageState
      v-if="!canQuery"
      title="无权访问产值计量"
      description="系统未加载计量业务数据。"
      kind="forbidden"
    /><template v-else
      ><V2Alert v-if="errorMessage" tone="danger" title="产值计量操作未完成">{{
        errorMessage
      }}</V2Alert
      ><V2Card title="产值计量与业主结算" :heading-level="1"
        ><template #actions
          ><div class="actions">
            <V2Select
              class="measurement-page__status-filter"
              :model-value="status"
              label="状态"
              hide-label
              :options="statusOptions"
              allow-empty
              placeholder="全部状态"
              @update:model-value="changeStatus"
            /><V2Button v-if="canMaintain" variant="secondary" @click="openPeriod"
              >新建期间</V2Button
            ><V2Button v-if="canMaintain" variant="secondary" @click="openMeasurement"
              >新建计量</V2Button
            >
          </div></template
        >
        <V2PageState
          v-if="loading && !periods.length && !measurements.length && !submissions.length"
          title="正在加载产值计量"
          description="正在读取当前项目和报告期内的计量与业主报量。"
          kind="loading"
        /><V2PageState
          v-else-if="!visibleMeasurements.length"
          title="暂无产值计量"
          description="当前项目、计量日期和状态下没有可访问的计量记录。"
          kind="empty"
        />
        <div
          v-else
          class="measurement-page__table-wrap"
          role="region"
          aria-label="产值计量列表"
          :aria-busy="loading"
          tabindex="0"
        >
          <table class="measurement-page__table measurement-page__measurement-table">
            <caption class="v2-visually-hidden">
              产值计量与业主结算记录
            </caption>
            <thead>
              <tr>
                <th scope="col">所属项目</th>
                <th scope="col">计量期间</th>
                <th scope="col">计量编号</th>
                <th scope="col">计量日期</th>
                <th scope="col">本期申报</th>
                <th scope="col">累计申报</th>
                <th scope="col">内部状态</th>
                <th scope="col">业主状态</th>
                <th scope="col">时间窗口</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="row in visibleMeasurements" :key="text(row, 'id')">
                <tr>
                  <td>{{ projectLabel(row) }}</td>
                  <td>{{ text(row, 'period_name', 'period_code') || '—' }}</td>
                  <td>
                    <strong>{{ text(row, 'measure_code') || '计量单' }}</strong>
                  </td>
                  <td>{{ dateText(row, 'measure_date') }}</td>
                  <td>{{ text(row, 'current_reported_amount') || '—' }}</td>
                  <td>{{ text(row, 'cumulative_reported_amount') || '—' }}</td>
                  <td>
                    <V2Badge :tone="statusTone(approvalStatus(row))">{{
                      statusLabel(approvalStatus(row))
                    }}</V2Badge>
                  </td>
                  <td>
                    <V2Badge :tone="statusTone(ownerStatus(row))">{{
                      ownerStatus(row) ? statusLabel(ownerStatus(row)) : '未报送'
                    }}</V2Badge>
                  </td>
                  <td>
                    <V2Badge :tone="statusTone(text(periodFor(row), 'status'))">{{
                      statusLabel(text(periodFor(row), 'status'))
                    }}</V2Badge>
                  </td>
                  <td>
                    <div class="actions">
                      <V2Button
                        size="small"
                        variant="secondary"
                        :aria-expanded="isExpanded(row)"
                        :aria-controls="`measurement-detail-${text(row, 'id')}`"
                        @click="toggleExpanded(row)"
                        >详情</V2Button
                      ><V2Button
                        v-if="canSubmit && ['DRAFT', 'REJECTED'].includes(text(row, 'status'))"
                        size="small"
                        variant="secondary"
                        :disabled="actionBusy"
                        @click="
                          run(
                            () => submitMeasurement(text(row, 'id'), text(row, 'version')),
                            '计量已提交',
                          )
                        "
                        >提交内部审批</V2Button
                      ><V2Button
                        v-if="
                          canOwnerSubmit &&
                          ['INTERNAL_APPROVED', 'OWNER_RETURNED'].includes(text(row, 'status'))
                        "
                        size="small"
                        variant="secondary"
                        @click="openOwner(row)"
                        >对业主报量</V2Button
                      ><V2Button
                        v-if="
                          canMaintain &&
                          text(periodFor(row), 'status') === 'OPEN' &&
                          !['DRAFT', 'REJECTED', 'PENDING', 'OWNER_SUBMITTED'].includes(
                            text(row, 'status'),
                          )
                        "
                        size="small"
                        variant="secondary"
                        :disabled="actionBusy"
                        @click="
                          run(
                            () =>
                              closeMeasurementPeriod(
                                text(periodFor(row), 'id'),
                                text(periodFor(row), 'version'),
                              ),
                            '期间已关闭',
                          )
                        "
                        >关闭期间</V2Button
                      >
                    </div>
                  </td>
                </tr>
                <tr
                  v-if="isExpanded(row)"
                  :id="`measurement-detail-${text(row, 'id')}`"
                  class="measurement-page__detail-row"
                >
                  <td colspan="10">
                    <section
                      class="measurement-page__detail"
                      :aria-label="`${text(row, 'measure_code')} 业主报送记录`"
                    >
                      <h3>{{ text(row, 'measure_code') }} · 业主报送记录</h3>
                      <p v-if="!submissionsFor(row).length" class="measurement-page__empty">
                        尚未报送。内部审批通过后才能登记业主报送。
                      </p>
                      <div v-else class="measurement-page__table-wrap">
                        <table class="measurement-page__table measurement-page__submission-table">
                          <caption class="v2-visually-hidden">
                            业主报送版本
                          </caption>
                          <thead>
                            <tr>
                              <th scope="col">报送编号</th>
                              <th scope="col">版本</th>
                              <th scope="col">报送时间</th>
                              <th scope="col">报送金额</th>
                              <th scope="col">核定金额</th>
                              <th scope="col">业主状态</th>
                              <th scope="col">结算编号</th>
                              <th scope="col">操作</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr
                              v-for="submission in submissionsFor(row)"
                              :key="text(submission, 'id')"
                            >
                              <td>
                                <strong>{{ text(submission, 'submission_code') || '—' }}</strong>
                              </td>
                              <td>V{{ text(submission, 'revision_no') || '1' }}</td>
                              <td>{{ dateText(submission, 'submitted_at') }}</td>
                              <td>{{ text(submission, 'submitted_amount') || '—' }}</td>
                              <td>{{ text(submission, 'confirmed_amount') || '—' }}</td>
                              <td>
                                <V2Badge :tone="statusTone(text(submission, 'status'))">{{
                                  statusLabel(text(submission, 'status'))
                                }}</V2Badge>
                              </td>
                              <td>
                                {{ text(submission, 'settlement_code', 'settlement_id') || '—' }}
                              </td>
                              <td>
                                <div class="actions">
                                  <V2Button
                                    v-if="
                                      canOwnerReview && text(submission, 'status') === 'SUBMITTED'
                                    "
                                    size="small"
                                    variant="secondary"
                                    @click="openReview(submission)"
                                    >业主核定</V2Button
                                  ><V2Button
                                    v-if="
                                      text(submission, 'settlement_id') ||
                                      text(submission, 'status') === 'SETTLEMENT_CREATED'
                                    "
                                    size="small"
                                    variant="secondary"
                                    @click="openTrace(submission)"
                                    >结算追溯</V2Button
                                  >
                                </div>
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    </section>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </V2Card></template
    >
    <V2Dialog
      :open="dialog === 'period'"
      title="新建计量期间"
      description="选择项目和业主合同，并设置本计量期间的日期范围。"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="dialog = 'closed'"
      ><form
        id="measurement-period-form"
        class="measurement-page__period-form"
        @submit.prevent="savePeriod"
      >
        <V2Select
          :model-value="periodForm.projectId"
          label="项目"
          :options="projectOptions"
          required
          @update:model-value="changePeriodProject"
        /><V2Select
          v-model="periodForm.contractId"
          label="业主合同"
          :options="contractOptions"
          required
        /><V2Input v-model="periodForm.periodCode" label="期间编码" required /><V2Input
          v-model="periodForm.periodName"
          label="期间名称"
          required
        />
        <fieldset class="measurement-page__period-dates">
          <legend>日期范围</legend>
          <V2Input v-model="periodForm.startDate" label="开始日期" type="date" required /><V2Input
            v-model="periodForm.endDate"
            label="结束日期"
            type="date"
            required
          /><V2Input v-model="periodForm.cutoffDate" label="截止日期" type="date" required />
        </fieldset>
      </form>
      <template #footer>
        <V2Button variant="ghost" :disabled="actionBusy" @click="dialog = 'closed'">取消</V2Button
        ><V2Button type="submit" form="measurement-period-form" :loading="actionBusy"
          >保存期间</V2Button
        >
      </template></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'measurement'"
      title="新建产值计量"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="dialog = 'closed'"
      ><form id="measurement-form" class="form" @submit.prevent="saveMeasurement">
        <V2Select
          :model-value="measurementForm.projectId"
          label="项目"
          :options="projectOptions"
          @update:model-value="changeMeasurementProject"
        /><V2Select
          :model-value="measurementForm.contractId"
          label="业主合同"
          :options="contractOptions"
          @update:model-value="changeMeasurementContract"
        /><V2Select
          v-model="measurementForm.periodId"
          label="计量期间"
          :options="periodOptions"
        /><V2Input v-model="measurementForm.measureDate" label="计量日期" type="date" /><label
          >总体计量依据<input aria-label="总体计量依据" type="file" @change="selectFile"
        /></label>
        <div
          v-for="row in sourceLines"
          :key="text(row.source, 'sourceType') + text(row.source, 'sourceId')"
          class="source"
        >
          <label
            ><input v-model="row.selected" type="checkbox" />{{
              text(row.source, 'itemName', 'item_name', 'sourceId')
            }}</label
          ><span>剩余 {{ text(row.source, 'remainingQuantity') }}</span
          ><V2Input v-model="row.currentQuantity" label="本次计量量" />
        </div>
      </form>
      <template #footer>
        <V2Button variant="ghost" :disabled="actionBusy" @click="dialog = 'closed'">取消</V2Button>
        <V2Button type="submit" form="measurement-form" :loading="actionBusy">创建计量</V2Button>
      </template></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'owner'"
      title="对业主报量"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="dialog = 'closed'"
      ><form id="owner-measurement-form" class="form" @submit.prevent="saveOwner">
        <V2Input v-model="ownerForm.externalDocumentNo" label="外部单据号" /><label
          >业主报量文件<input aria-label="业主报量文件" type="file" @change="selectFile"
        /></label>
      </form>
      <template #footer>
        <V2Button variant="ghost" :disabled="actionBusy" @click="dialog = 'closed'">取消</V2Button>
        <V2Button type="submit" form="owner-measurement-form" :loading="actionBusy"
          >登记报量</V2Button
        >
      </template></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'review'"
      title="业主核定"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="dialog = 'closed'"
      ><V2PageState
        v-if="detailLoading"
        title="正在加载报量详情"
        description="正在读取业主报量明细和核定数据。"
        kind="loading"
      />
      <form v-else id="owner-review-form" class="form" @submit.prevent="saveReview">
        <V2Select
          v-model="reviewForm.decision"
          label="核定结论"
          :options="[
            { value: 'CONFIRMED', label: '确认' },
            { value: 'RETURNED', label: '退回' },
          ]"
        /><V2Input v-model="reviewForm.reviewerName" label="业主核定人" /><V2Input
          v-model="reviewForm.reviewComment"
          label="核定意见"
        /><template v-if="reviewForm.decision === 'CONFIRMED'"
          ><V2Input v-model="reviewForm.taxAmount" label="税额" /><V2Input
            v-model="reviewForm.retentionAmount"
            label="保留金" /><label
            >业主核定依据<input aria-label="业主核定依据" type="file" @change="selectFile"
          /></label>
          <div v-for="(line, index) in reviewLines" :key="line.measurementLineId">
            <V2Input v-model="line.confirmedQuantity" :label="`第 ${index + 1} 项核定数量`" /></div
        ></template>
      </form>
      <template v-if="!detailLoading" #footer>
        <V2Button variant="ghost" :disabled="actionBusy" @click="dialog = 'closed'">取消</V2Button>
        <V2Button type="submit" form="owner-review-form" :loading="actionBusy">保存核定</V2Button>
      </template></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'trace'"
      title="结算追溯"
      panel-class="v2-detail-dialog"
      :close-on-backdrop="false"
      @close="dialog = 'closed'"
      ><V2PageState
        v-if="detailLoading"
        title="正在加载结算追溯"
        description="正在读取计量、报量与结算的关联链路。"
        kind="loading"
      />
      <section v-else-if="trace" class="v2-detail-dialog__section">
        <dl class="v2-detail-dialog__facts">
          <dt>结算编号</dt>
          <dd>{{ text(trace, 'settlement_code', 'settlement_id', 'id') }}</dd>
          <dt>计量编号</dt>
          <dd>{{ text(trace, 'measure_code', 'measurement_id') || '—' }}</dd>
          <dt>结算金额</dt>
          <dd>{{ text(trace, 'settlement_amount', 'confirmed_amount') || '—' }}</dd>
          <dt>状态</dt>
          <dd>{{ statusLabel(text(trace, 'status')) }}</dd>
        </dl>
      </section>
    </V2Dialog>
  </div>
</template>
<style scoped>
.measurement-page,
.form {
  display: grid;
  gap: var(--v2-space-4);
}
.actions {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
  flex-wrap: wrap;
}
.measurement-page__status-filter {
  width: 12rem;
}
.measurement-page__period-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}
.measurement-page__period-dates {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-3);
  min-width: 0;
  margin: 0;
  padding: var(--v2-space-4);
  border: 1px solid var(--v2-color-border-subtle);
  border-radius: var(--v2-radius-md);
}
.measurement-page__period-dates legend {
  padding-inline: var(--v2-space-2);
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
  font-weight: var(--v2-font-weight-semibold);
}
.measurement-page__table-wrap {
  min-width: 0;
  overflow-x: auto;
}
.measurement-page__table {
  min-width: 56rem;
}
.measurement-page__measurement-table {
  min-width: 90rem;
}
.measurement-page__submission-table {
  min-width: 64rem;
}
.measurement-page__table .actions {
  align-items: center;
}
.measurement-page__detail-row > td {
  padding: 0;
  background: var(--v2-color-surface-subtle);
}
.measurement-page__detail {
  display: grid;
  gap: var(--v2-space-3);
  padding: var(--v2-space-4) var(--v2-space-5);
  border-bottom: 1px solid var(--v2-color-border);
}
.measurement-page__detail h3 {
  margin: 0;
  font-size: var(--v2-font-size-15);
  line-height: var(--v2-line-height-tight);
}
.measurement-page__empty {
  margin: 0;
  padding: var(--v2-space-4);
  border: 1px dashed var(--v2-color-border);
  color: var(--v2-color-text-muted);
  background: var(--v2-color-surface);
  text-align: center;
}
dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}
dd {
  margin: 0;
}
.source {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--v2-space-3);
  align-items: center;
  padding-block: var(--v2-space-2);
  border-bottom: 1px solid var(--v2-color-border);
}
.source {
  grid-template-columns: 1fr auto minmax(10rem, 1fr);
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
label {
  display: grid;
  gap: var(--v2-space-1);
}
@media (max-width: 48rem) {
  .source,
  .measurement-page__period-form,
  .measurement-page__period-dates {
    grid-template-columns: 1fr;
  }
}
</style>
