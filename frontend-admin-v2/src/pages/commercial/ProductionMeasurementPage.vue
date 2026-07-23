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
import { V2Alert, V2Button, V2Card, V2Dialog, V2Input, V2PageState, V2Select } from '@/components'
import { uploadSiteFile } from '@/services/delivery'
import {
  closeMeasurementPeriod,
  createMeasurement,
  createMeasurementPeriod,
  loadContractPage,
  loadMeasurement,
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
type SourceLine = { source: MeasurementAmountRow; selected: boolean; currentQuantity: string }
type Dialog = 'closed' | 'period' | 'measurement' | 'detail' | 'owner' | 'review' | 'trace'
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const projectId = ref('')
const contractId = ref('')
const status = ref('')
const contracts = ref<ContractRecord[]>([])
const periods = ref<MeasurementAmountRow[]>([])
const measurements = ref<MeasurementAmountRow[]>([])
const submissions = ref<MeasurementAmountRow[]>([])
const sourceLines = ref<SourceLine[]>([])
const selected = ref<MeasurementAmountRow | null>(null)
const detail = ref<MeasurementAmountRow | null>(null)
const trace = ref<MeasurementAmountRow | null>(null)
const dialog = ref<Dialog>('closed')
const loading = ref(false)
const detailLoading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const evidenceFile = ref<File | null>(null)
let controller: AbortController | null = null
let detailController: AbortController | null = null
let generation = 0
let detailGeneration = 0
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
const measurementForm = reactive({ periodId: '', measureDate: today, remark: '' })
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
const periodOptions = computed(() =>
  periods.value
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
const text = (row: MeasurementAmountRow | undefined, ...keys: string[]) => {
  for (const key of keys) {
    if (row?.[key] !== undefined && row[key] !== null) return String(row[key])
  }
  return ''
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
  contractId.value = typeof route.query.contractId === 'string' ? route.query.contractId : ''
  status.value = typeof route.query.status === 'string' ? route.query.status : ''
  controller?.abort()
  const current = new AbortController()
  controller = current
  const token = ++generation
  loading.value = true
  errorMessage.value = ''
  const date = bounds()
  try {
    const contractPage = projectId.value
      ? await loadContractPage(
          { pageNo: 1, pageSize: 100, projectId: projectId.value },
          current.signal,
        )
      : { records: [], total: 0, pageNo: 1, pageSize: 100 }
    contracts.value = contractPage.records
    if (contractId.value && !contracts.value.some((c) => c.id === contractId.value))
      contractId.value = ''
    const query = {
      projectId: projectId.value || undefined,
      status: status.value || undefined,
      startDate: date?.startDate,
      endDate: date?.endDate,
    }
    const [periodRows, measurementRows, submissionRows] = await Promise.all([
      loadMeasurementPeriods(
        { ...query, contractId: contractId.value || undefined },
        current.signal,
      ),
      loadMeasurements(query, current.signal),
      loadOwnerMeasurementSubmissions(query, current.signal),
    ])
    const sources =
      projectId.value && contractId.value
        ? await loadMeasurementSources(projectId.value, contractId.value, current.signal)
        : []
    if (token !== generation) return
    periods.value = periodRows
    measurements.value = measurementRows
    submissions.value = submissionRows
    sourceLines.value = sources.map((source) => ({ source, selected: false, currentQuantity: '' }))
  } catch (e) {
    if (!current.signal.aborted && token === generation) {
      periods.value = []
      measurements.value = []
      submissions.value = []
      sourceLines.value = []
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
      contractId: contractId.value || undefined,
      status: status.value || undefined,
    },
    hash: route.hash,
  })
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
function openPeriod() {
  if (!projectId.value || !contractId.value) {
    errorMessage.value = '请先选择项目和业主合同'
    return
  }
  Object.assign(periodForm, { projectId: projectId.value, contractId: contractId.value })
  dialog.value = 'period'
}
async function savePeriod() {
  await run(() => createMeasurementPeriod({ ...periodForm }), '计量期间已创建')
}
function openMeasurement() {
  if (!projectId.value || !contractId.value) {
    errorMessage.value = '请先选择项目和业主合同'
    return
  }
  measurementForm.periodId = periodOptions.value[0]?.value ?? ''
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
    !evidenceFile.value ||
    !chosen.length ||
    chosen.some((row) => !decimal(row.currentQuantity))
  ) {
    errorMessage.value = '请选择期间、真实附件和至少一条正数计量来源'
    return
  }
  const command: MeasurementSaveCommand = {
    projectId: projectId.value,
    contractId: contractId.value,
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
async function openDetail(row: MeasurementAmountRow) {
  detailController?.abort()
  const current = new AbortController()
  detailController = current
  const token = ++detailGeneration
  selected.value = row
  detail.value = null
  dialog.value = 'detail'
  detailLoading.value = true
  try {
    const value = await loadMeasurement(text(row, 'id'), current.signal)
    if (token === detailGeneration) detail.value = value
  } catch (e) {
    if (!current.signal.aborted && token === detailGeneration)
      errorMessage.value = errorText(e, '计量详情加载失败')
  } finally {
    if (token === detailGeneration) detailLoading.value = false
  }
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
      ><V2Alert v-if="successMessage" tone="success" title="产值计量操作完成">{{
        successMessage
      }}</V2Alert
      ><V2Card title="产值计量与业主结算" :heading-level="1"
        ><template #actions
          ><div class="actions">
            <V2Button v-if="canMaintain" variant="secondary" @click="openPeriod">新建期间</V2Button
            ><V2Button v-if="canMaintain" variant="secondary" @click="openMeasurement"
              >新建计量</V2Button
            >
          </div></template
        >
        <div class="filters">
          <V2Select
            v-model="contractId"
            label="业主合同"
            :options="contractOptions"
            allow-empty
          /><V2Select v-model="status" label="状态" :options="statusOptions" allow-empty /><V2Button
            variant="secondary"
            :loading="loading"
            @click="applyFilter"
            >查询</V2Button
          >
        </div></V2Card
      ><V2PageState
        v-if="loading && !measurements.length"
        title="正在加载产值计量"
        description="正在读取当前项目和报告期内的计量与业主报量。"
        kind="loading"
      /><V2PageState
        v-else-if="!measurements.length"
        title="暂无产值计量"
        description="当前筛选条件下没有可访问的计量记录。"
        kind="empty"
      /><V2Card v-else title="计量记录"
        ><div
          class="measurement-page__table-wrap"
          role="region"
          aria-label="计量记录"
          :aria-busy="loading"
          tabindex="0"
        >
          <table class="measurement-page__table">
            <caption class="v2-visually-hidden">
              计量记录
            </caption>
            <thead>
              <tr>
                <th scope="col">计量编号</th>
                <th scope="col">期间</th>
                <th scope="col">本期申报</th>
                <th scope="col">累计申报</th>
                <th scope="col">状态</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in measurements" :key="text(row, 'id')">
                <td>
                  <strong>{{ text(row, 'measure_code') || '计量单' }}</strong>
                </td>
                <td>{{ text(row, 'period_name', 'period_code') || '—' }}</td>
                <td>{{ text(row, 'current_reported_amount') || '—' }}</td>
                <td>{{ text(row, 'cumulative_reported_amount') || '—' }}</td>
                <td>{{ text(row, 'status') || '—' }}</td>
                <td>
                  <div class="actions">
                    <V2Button size="small" variant="secondary" @click="openDetail(row)"
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
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div></V2Card
      ><V2Card title="业主报量与结算"
        ><V2PageState
          v-if="!submissions.length"
          title="暂无业主报量"
          description="当前项目尚未提交业主报量或生成结算。"
          kind="empty"
        />
        <div
          v-else
          class="measurement-page__table-wrap"
          role="region"
          aria-label="业主报量与结算"
          tabindex="0"
        >
          <table class="measurement-page__table measurement-page__submission-table">
            <caption class="v2-visually-hidden">
              业主报量与结算
            </caption>
            <thead>
              <tr>
                <th scope="col">报量单</th>
                <th scope="col">申报金额</th>
                <th scope="col">核定金额</th>
                <th scope="col">状态</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in submissions" :key="text(row, 'id')">
                <td>
                  <strong>{{ text(row, 'external_document_no', 'measure_code') }}</strong>
                </td>
                <td>{{ text(row, 'submitted_amount') || '—' }}</td>
                <td>{{ text(row, 'confirmed_amount') || '—' }}</td>
                <td>{{ text(row, 'status') || '—' }}</td>
                <td>
                  <div class="actions">
                    <V2Button
                      v-if="canOwnerReview && text(row, 'status') === 'SUBMITTED'"
                      size="small"
                      variant="secondary"
                      @click="openReview(row)"
                      >业主核定</V2Button
                    ><V2Button
                      v-if="
                        text(row, 'settlement_id') || text(row, 'status') === 'SETTLEMENT_CREATED'
                      "
                      size="small"
                      variant="secondary"
                      @click="openTrace(row)"
                      >结算追溯</V2Button
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div></V2Card
      ><V2Card title="计量期间"
        ><div v-for="row in periods" :key="text(row, 'id')" class="submission">
          <span>{{ text(row, 'period_name', 'period_code') }} · {{ text(row, 'status') }}</span
          ><V2Button
            v-if="canMaintain && text(row, 'status') === 'OPEN'"
            variant="secondary"
            :disabled="actionBusy"
            @click="
              run(() => closeMeasurementPeriod(text(row, 'id'), text(row, 'version')), '期间已关闭')
            "
            >关闭期间</V2Button
          >
        </div></V2Card
      ></template
    >
    <V2Dialog
      :open="dialog === 'period'"
      title="新建计量期间"
      panel-class="v2-dialog-standard"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="dialog = 'closed'"
      ><form class="form" @submit.prevent="savePeriod">
        <V2Input v-model="periodForm.periodCode" label="期间编码" /><V2Input
          v-model="periodForm.periodName"
          label="期间名称"
        /><V2Input v-model="periodForm.startDate" label="开始日期" type="date" /><V2Input
          v-model="periodForm.endDate"
          label="结束日期"
          type="date"
        /><V2Input v-model="periodForm.cutoffDate" label="截止日期" type="date" /><V2Button
          type="submit"
          variant="secondary"
          :loading="actionBusy"
          >保存期间</V2Button
        >
      </form></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'measurement'"
      title="新建产值计量"
      panel-class="v2-dialog-standard"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="dialog = 'closed'"
      ><form class="form" @submit.prevent="saveMeasurement">
        <V2Select
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
        <V2Button type="submit" variant="secondary" :loading="actionBusy">创建计量</V2Button>
      </form></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'detail'"
      title="计量详情"
      panel-class="v2-dialog-standard v2-detail-dialog"
      :close-on-backdrop="false"
      @close="dialog = 'closed'"
      ><V2PageState
        v-if="detailLoading"
        title="正在加载计量详情"
        description="正在读取计量来源、数量和审批状态。"
        kind="loading"
      />
      <section v-else-if="detail" class="v2-detail-dialog__section">
        <dl class="v2-detail-dialog__facts">
          <dt>计量编号</dt>
          <dd>{{ text(detail, 'measure_code', 'id') }}</dd>
          <dt>计量期间</dt>
          <dd>{{ text(detail, 'period_name', 'period_code') || '—' }}</dd>
          <dt>本期申报</dt>
          <dd>{{ text(detail, 'current_reported_amount') || '—' }}</dd>
          <dt>累计申报</dt>
          <dd>{{ text(detail, 'cumulative_reported_amount') || '—' }}</dd>
          <dt>状态</dt>
          <dd>{{ text(detail, 'status') || '—' }}</dd>
          <dt>备注</dt>
          <dd>{{ text(detail, 'remark') || '—' }}</dd>
        </dl>
      </section>
    </V2Dialog>
    <V2Dialog
      :open="dialog === 'owner'"
      title="对业主报量"
      panel-class="v2-dialog-standard"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="dialog = 'closed'"
      ><form class="form" @submit.prevent="saveOwner">
        <V2Input v-model="ownerForm.externalDocumentNo" label="外部单据号" /><label
          >业主报量文件<input aria-label="业主报量文件" type="file" @change="selectFile" /></label
        ><V2Button type="submit" variant="secondary" :loading="actionBusy">登记报量</V2Button>
      </form></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'review'"
      title="业主核定"
      panel-class="v2-dialog-standard"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="dialog = 'closed'"
      ><V2PageState
        v-if="detailLoading"
        title="正在加载报量详情"
        description="正在读取业主报量明细和核定数据。"
        kind="loading"
      />
      <form v-else class="form" @submit.prevent="saveReview">
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
          <div v-for="line in reviewLines" :key="line.measurementLineId">
            <V2Input
              v-model="line.confirmedQuantity"
              :label="`核定数量 ${line.measurementLineId}`"
            /></div></template
        ><V2Button type="submit" variant="secondary" :loading="actionBusy">保存核定</V2Button>
      </form></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'trace'"
      title="结算追溯"
      panel-class="v2-dialog-standard v2-detail-dialog"
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
          <dd>{{ text(trace, 'status') || '—' }}</dd>
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
.filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(10rem, 1fr)) auto;
  gap: var(--v2-space-3);
  align-items: end;
}
.actions {
  display: flex;
  gap: var(--v2-space-2);
  flex-wrap: wrap;
}
.measurement-page__table-wrap {
  min-width: 0;
  overflow-x: auto;
}
.measurement-page__table {
  width: 100%;
  min-width: 56rem;
  border-collapse: collapse;
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}
.measurement-page__submission-table {
  min-width: 48rem;
}
.measurement-page__table th,
.measurement-page__table td {
  padding: var(--v2-space-3);
  border-bottom: 1px solid var(--v2-color-border-subtle);
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
}
.measurement-page__table th {
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface-subtle);
  font-weight: var(--v2-font-weight-semibold);
}
.measurement-page__table .actions {
  flex-wrap: nowrap;
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
.submission,
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
  .filters,
  .source {
    grid-template-columns: 1fr;
  }
}
</style>
