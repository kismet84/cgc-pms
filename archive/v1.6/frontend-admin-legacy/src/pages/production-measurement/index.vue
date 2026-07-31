<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import { useReferenceStore } from '@/stores/reference'
import { uploadFile } from '@/api/modules/file'
import {
  closeMeasurementPeriod,
  createMeasurementPeriod,
  createOwnerMeasurementSubmission,
  createProductionMeasurement,
  getMeasurementPeriods,
  getMeasurementSources,
  getOwnerMeasurementSubmission,
  getOwnerMeasurementSubmissions,
  getProductionMeasurements,
  reviewOwnerMeasurementSubmission,
  submitProductionMeasurement,
  type MeasurementRow,
} from '@/api/modules/productionMeasurement'

const referenceStore = useReferenceStore()
const { projects, contracts } = storeToRefs(referenceStore)
const loading = ref(false)
const projectId = ref<string>()
const contractId = ref<string>()
const periods = ref<MeasurementRow[]>([])
const sources = ref<MeasurementRow[]>([])
const measurements = ref<MeasurementRow[]>([])
const submissions = ref<MeasurementRow[]>([])
const periodOpen = ref(false)
const measurementOpen = ref(false)
const ownerOpen = ref(false)
const reviewOpen = ref(false)
const selectedMeasurement = ref<MeasurementRow>()
const selectedSubmission = ref<MeasurementRow>()
const measurementFile = ref<File>()
const ownerFile = ref<File>()
const settlementFile = ref<File>()
const selectedSourceKeys = ref<string[]>([])
const quantityBySource = reactive<Record<string, number | undefined>>({})
const today = new Date().toISOString().slice(0, 10)

const periodForm = reactive({
  periodCode: today.slice(0, 7),
  periodName: `${today.slice(0, 7)} 月度计量`,
  startDate: `${today.slice(0, 7)}-01`,
  endDate: today,
  cutoffDate: today,
  remark: '',
})
const measurementForm = reactive({
  periodId: undefined as string | undefined,
  measureDate: today,
  attachmentCount: 1,
  remark: '',
})
const ownerForm = reactive({ externalDocumentNo: '', attachmentCount: 1, remark: '' })
const reviewForm = reactive({
  decision: 'CONFIRMED',
  reviewerName: '',
  reviewComment: '',
  settlementDate: today,
  dueDate: today,
  taxAmount: 0,
  retentionAmount: 0,
  attachmentCount: 1,
})
const reviewLines = ref<
  Array<{
    measurementLineId: string
    itemName: string
    submittedQuantity: number
    confirmedQuantity: number
    deductionReason: string
  }>
>([])

const projectContracts = computed(() =>
  (contracts.value ?? []).filter(
    (item) => !projectId.value || String(item.projectId) === String(projectId.value),
  ),
)
const openPeriods = computed(() => periods.value.filter((item) => item.status === 'OPEN'))
function id(row?: MeasurementRow) {
  return String(row?.id ?? '')
}
function value(row: MeasurementRow, ...keys: string[]) {
  const key = keys.find((item) => row[item] !== undefined)
  return key ? row[key] : undefined
}
function money(input: unknown) {
  return Number(input ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}
function sourceKey(row: MeasurementRow) {
  return `${row.sourceType}:${row.sourceId}`
}
function selectFile(target: 'measurement' | 'owner' | 'settlement', event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (target === 'measurement') measurementFile.value = file
  if (target === 'owner') ownerFile.value = file
  if (target === 'settlement') settlementFile.value = file
}

async function load() {
  loading.value = true
  try {
    ;[periods.value, measurements.value, submissions.value] = await Promise.all([
      getMeasurementPeriods(projectId.value, contractId.value),
      getProductionMeasurements(projectId.value),
      getOwnerMeasurementSubmissions(projectId.value),
    ])
    sources.value =
      projectId.value && contractId.value
        ? await getMeasurementSources(projectId.value, contractId.value)
        : []
  } finally {
    loading.value = false
  }
}
async function onProjectChange() {
  contractId.value = undefined
  await referenceStore.fetchContracts({ projectId: projectId.value })
  await load()
}
async function onContractChange() {
  await load()
}

async function savePeriod() {
  if (!projectId.value || !contractId.value) return message.warning('请先选择项目和业主合同')
  await createMeasurementPeriod({
    projectId: projectId.value,
    contractId: contractId.value,
    ...periodForm,
  })
  periodOpen.value = false
  await load()
  message.success('计量周期已创建')
}
function closePeriod(row: MeasurementRow) {
  Modal.confirm({
    title: '关闭计量周期？',
    content: '关闭后不能新增或提交该周期计量。',
    async onOk() {
      await closeMeasurementPeriod(id(row))
      await load()
      message.success('计量周期已关闭')
    },
  })
}

function openMeasurement() {
  if (!projectId.value || !contractId.value) return message.warning('请先选择项目和业主合同')
  selectedSourceKeys.value = []
  measurementFile.value = undefined
  measurementOpen.value = true
}
async function saveMeasurement() {
  if (!projectId.value || !contractId.value || !measurementForm.periodId)
    return message.warning('请完整选择项目、合同和计量周期')
  if (!measurementFile.value) return message.warning('请上传总体计量依据')
  const lines = selectedSourceKeys.value.map((key) => {
    const source = sources.value.find((item) => sourceKey(item) === key)!
    const currentQuantity = quantityBySource[key]
    return {
      contractItemId: source.sourceType === 'CONTRACT_ITEM' ? source.sourceId : null,
      contractChangeId: source.sourceType === 'CONTRACT_CHANGE' ? source.sourceId : null,
      currentQuantity,
      evidenceCount: 1,
    }
  })
  if (
    !lines.length ||
    lines.some((line) => !line.currentQuantity || Number(line.currentQuantity) <= 0)
  )
    return message.warning('至少选择一条来源并填写本次计量量')
  const created = await createProductionMeasurement({
    projectId: projectId.value,
    contractId: contractId.value,
    ...measurementForm,
    lines,
  })
  await uploadFile(
    measurementFile.value,
    'PRODUCTION_MEASUREMENT',
    id(created),
    'MEASUREMENT_EVIDENCE',
  )
  measurementOpen.value = false
  await load()
  message.success('产值计量草稿已创建')
}
function submitInternal(row: MeasurementRow) {
  Modal.confirm({
    title: '提交内部产值审批？',
    content: '提交后将冻结本次计量数量和单价快照。',
    async onOk() {
      await submitProductionMeasurement(id(row))
      await load()
      message.success('已提交内部审批')
    },
  })
}

function openOwner(row: MeasurementRow) {
  selectedMeasurement.value = row
  ownerFile.value = undefined
  ownerOpen.value = true
}
async function saveOwnerSubmission() {
  if (!selectedMeasurement.value || !ownerFile.value) return message.warning('请上传对业主报量文件')
  const created = await createOwnerMeasurementSubmission(id(selectedMeasurement.value), ownerForm)
  await uploadFile(
    ownerFile.value,
    'OWNER_MEASUREMENT_SUBMISSION',
    id(created),
    'OWNER_MEASUREMENT_REPORT',
  )
  ownerOpen.value = false
  await load()
  message.success('业主报量版本已登记')
}

async function openReview(row: MeasurementRow) {
  selectedSubmission.value = await getOwnerMeasurementSubmission(id(row))
  const lines = (selectedSubmission.value.lines ?? []) as MeasurementRow[]
  reviewLines.value = lines.map((line) => ({
    measurementLineId: String(line.measurement_line_id),
    itemName: String(line.item_name ?? ''),
    submittedQuantity: Number(line.submitted_quantity ?? 0),
    confirmedQuantity: Number(line.submitted_quantity ?? 0),
    deductionReason: '',
  }))
  settlementFile.value = undefined
  reviewOpen.value = true
}
async function saveReview() {
  if (!selectedSubmission.value || !reviewForm.reviewerName)
    return message.warning('请填写业主核定人')
  if (reviewForm.decision === 'RETURNED' && !reviewForm.reviewComment)
    return message.warning('业主退回必须填写原因')
  if (reviewForm.decision === 'CONFIRMED' && !settlementFile.value)
    return message.warning('请上传业主核定单/结算依据')
  const result = await reviewOwnerMeasurementSubmission(id(selectedSubmission.value), {
    ...reviewForm,
    lines:
      reviewForm.decision === 'CONFIRMED'
        ? reviewLines.value.map((line) => ({
            measurementLineId: line.measurementLineId,
            confirmedQuantity: line.confirmedQuantity,
            deductionReason: line.deductionReason,
          }))
        : [],
  })
  if (reviewForm.decision === 'CONFIRMED' && settlementFile.value && result.settlement?.id)
    await uploadFile(
      settlementFile.value,
      'OWNER_SETTLEMENT',
      String(result.settlement.id),
      'CONTRACT_ATTACHMENT',
    )
  reviewOpen.value = false
  await load()
  message.success(
    reviewForm.decision === 'CONFIRMED'
      ? '业主核定已登记并自动生成结算草稿'
      : '已登记业主退回，可重新报送新版本',
  )
}

onMounted(async () => {
  await Promise.all([referenceStore.fetchProjects(), referenceStore.fetchContracts()])
  await load()
})
</script>

<template>
  <div class="measurement-page">
    <a-page-header
      title="产值计量与业主结算"
      sub-title="合同清单/已批准变更 → 内部计量审批 → 对业主报量 → 逐项核定/核减 → 结算应收"
    >
      <template #extra>
        <a-select
          v-model:value="projectId"
          allow-clear
          placeholder="选择项目"
          style="width: 220px"
          @change="onProjectChange"
          ><a-select-option v-for="p in projects ?? []" :key="p.id" :value="p.id">{{
            p.projectName
          }}</a-select-option></a-select
        >
        <a-select
          v-model:value="contractId"
          allow-clear
          placeholder="选择业主合同"
          style="width: 260px"
          @change="onContractChange"
          ><a-select-option v-for="c in projectContracts" :key="c.id" :value="c.id">{{
            c.contractName
          }}</a-select-option></a-select
        >
        <a-button :loading="loading" @click="load">刷新</a-button>
      </template>
    </a-page-header>
    <a-alert
      class="chain-alert"
      type="info"
      show-icon
      message="只有内部审批通过的逐项完成量才能报送业主；业主核减必须逐项记录原因；核定完成自动生成业主结算草稿，结算审批通过后才形成应收。"
    />
    <a-tabs>
      <a-tab-pane key="measurement" tab="内部产值计量">
        <a-space class="toolbar"
          ><a-button @click="periodOpen = true">新建计量周期</a-button
          ><a-button type="primary" @click="openMeasurement">新建产值计量</a-button></a-space
        >
        <a-table :data-source="measurements" row-key="id" :loading="loading"
          ><a-table-column title="计量编号" data-index="measure_code" /><a-table-column
            title="周期"
            data-index="period_name"
          /><a-table-column title="计量日期" data-index="measure_date" /><a-table-column
            title="本期报量"
            ><template #default="{ record }">{{
              money(record.current_reported_amount)
            }}</template></a-table-column
          ><a-table-column title="累计产值"
            ><template #default="{ record }">{{
              money(record.cumulative_reported_amount)
            }}</template></a-table-column
          ><a-table-column title="状态" data-index="status" /><a-table-column title="操作"
            ><template #default="{ record }"
              ><a-button
                v-if="['DRAFT', 'REJECTED'].includes(record.status)"
                type="link"
                @click="submitInternal(record)"
                >提交审批</a-button
              ><a-button
                v-if="['INTERNAL_APPROVED', 'OWNER_RETURNED'].includes(record.status)"
                type="link"
                @click="openOwner(record)"
                >报送业主</a-button
              ></template
            ></a-table-column
          ></a-table
        >
      </a-tab-pane>
      <a-tab-pane key="owner" tab="业主报量与核定">
        <a-table :data-source="submissions" row-key="id" :loading="loading"
          ><a-table-column title="报量版本" data-index="submission_code" /><a-table-column
            title="计量编号"
            data-index="measure_code"
          /><a-table-column title="外部单号" data-index="external_document_no" /><a-table-column
            title="报送金额"
            ><template #default="{ record }">{{
              money(record.submitted_amount)
            }}</template></a-table-column
          ><a-table-column title="业主核定"
            ><template #default="{ record }">{{
              money(record.confirmed_amount)
            }}</template></a-table-column
          ><a-table-column title="核减"
            ><template #default="{ record }">{{
              money(record.deducted_amount)
            }}</template></a-table-column
          ><a-table-column title="状态" data-index="status" /><a-table-column title="操作"
            ><template #default="{ record }"
              ><a-button
                v-if="record.status === 'SUBMITTED'"
                type="link"
                @click="openReview(record)"
                >登记核定</a-button
              ></template
            ></a-table-column
          ></a-table
        >
      </a-tab-pane>
      <a-tab-pane key="period" tab="计量周期">
        <a-table :data-source="periods" row-key="id"
          ><a-table-column title="周期编码" data-index="period_code" /><a-table-column
            title="周期名称"
            data-index="period_name"
          /><a-table-column title="开始日" data-index="start_date" /><a-table-column
            title="结束日"
            data-index="end_date"
          /><a-table-column title="截止日" data-index="cutoff_date" /><a-table-column
            title="状态"
            data-index="status"
          /><a-table-column title="操作"
            ><template #default="{ record }"
              ><a-button
                v-if="record.status === 'OPEN'"
                type="link"
                danger
                @click="closePeriod(record)"
                >关闭</a-button
              ></template
            ></a-table-column
          ></a-table
        >
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="periodOpen" title="新建计量周期" @ok="savePeriod"
      ><a-form layout="vertical"
        ><a-form-item label="周期编码" required
          ><a-input v-model:value="periodForm.periodCode" /></a-form-item
        ><a-form-item label="周期名称" required
          ><a-input v-model:value="periodForm.periodName" /></a-form-item
        ><a-row :gutter="12"
          ><a-col :span="8"
            ><a-form-item label="开始日"
              ><a-input v-model:value="periodForm.startDate" type="date" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="结束日"
              ><a-input v-model:value="periodForm.endDate" type="date" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="截止日"
              ><a-input
                v-model:value="periodForm.cutoffDate"
                type="date" /></a-form-item></a-col></a-row></a-form
    ></a-modal>

    <a-modal v-model:open="measurementOpen" title="新建产值计量" width="980px" @ok="saveMeasurement"
      ><a-form layout="vertical"
        ><a-row :gutter="12"
          ><a-col :span="8"
            ><a-form-item label="计量周期" required
              ><a-select v-model:value="measurementForm.periodId"
                ><a-select-option v-for="p in openPeriods" :key="p.id" :value="String(p.id)">{{
                  p.period_name
                }}</a-select-option></a-select
              ></a-form-item
            ></a-col
          ><a-col :span="8"
            ><a-form-item label="计量日期" required
              ><a-input
                v-model:value="measurementForm.measureDate"
                type="date" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="计量依据" required
              ><input
                type="file"
                accept=".pdf,.png,.jpg,.jpeg"
                @change="selectFile('measurement', $event)" /></a-form-item></a-col></a-row></a-form
      ><a-table
        :data-source="sources"
        :row-key="sourceKey"
        :row-selection="{
          selectedRowKeys: selectedSourceKeys,
          onChange: (keys: unknown[]) => (selectedSourceKeys = keys.map(String)),
          getCheckboxProps: (record: MeasurementRow) => ({
            disabled: Number(record.remainingQuantity ?? 0) <= 0,
          }),
        }"
        :pagination="false"
        size="small"
        ><a-table-column title="来源" data-index="sourceType" /><a-table-column title="编码"
          ><template #default="{ record }">{{
            value(record, 'item_code')
          }}</template></a-table-column
        ><a-table-column title="名称"
          ><template #default="{ record }">{{
            value(record, 'item_name')
          }}</template></a-table-column
        ><a-table-column title="合同量"
          ><template #default="{ record }">{{ record.contractQuantity }}</template></a-table-column
        ><a-table-column title="已审批"
          ><template #default="{ record }">{{ record.approvedQuantity }}</template></a-table-column
        ><a-table-column title="剩余"
          ><template #default="{ record }">{{ record.remainingQuantity }}</template></a-table-column
        ><a-table-column title="本次计量量"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="quantityBySource[sourceKey(record)]"
              :min="0"
              :max="Number(record.remainingQuantity ?? 0)"
              :precision="4" /></template></a-table-column></a-table
    ></a-modal>

    <a-modal v-model:open="ownerOpen" title="登记对业主报量版本" @ok="saveOwnerSubmission"
      ><a-form layout="vertical"
        ><a-form-item label="业主报量单号"
          ><a-input v-model:value="ownerForm.externalDocumentNo" /></a-form-item
        ><a-form-item label="报量文件" required
          ><input
            type="file"
            accept=".pdf,.png,.jpg,.jpeg"
            @change="selectFile('owner', $event)" /></a-form-item
        ><a-form-item label="备注"
          ><a-textarea v-model:value="ownerForm.remark" /></a-form-item></a-form
    ></a-modal>

    <a-modal v-model:open="reviewOpen" title="登记业主逐项核定" width="900px" @ok="saveReview"
      ><a-form layout="vertical"
        ><a-row :gutter="12"
          ><a-col :span="8"
            ><a-form-item label="结论" required
              ><a-select v-model:value="reviewForm.decision"
                ><a-select-option value="CONFIRMED">确认</a-select-option
                ><a-select-option value="RETURNED">退回重报</a-select-option></a-select
              ></a-form-item
            ></a-col
          ><a-col :span="8"
            ><a-form-item label="业主核定人" required
              ><a-input v-model:value="reviewForm.reviewerName" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="核定/结算附件" :required="reviewForm.decision === 'CONFIRMED'"
              ><input
                type="file"
                accept=".pdf,.png,.jpg,.jpeg"
                @change="selectFile('settlement', $event)" /></a-form-item></a-col
          ><a-col :span="12"
            ><a-form-item label="结算日期"
              ><a-input
                v-model:value="reviewForm.settlementDate"
                type="date" /></a-form-item></a-col
          ><a-col :span="12"
            ><a-form-item label="应收到期日"
              ><a-input v-model:value="reviewForm.dueDate" type="date" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="税额"
              ><a-input-number
                v-model:value="reviewForm.taxAmount"
                :min="0"
                :precision="2"
                style="width: 100%" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="保留金"
              ><a-input-number
                v-model:value="reviewForm.retentionAmount"
                :min="0"
                :precision="2"
                style="width: 100%" /></a-form-item></a-col
          ><a-col :span="24"
            ><a-form-item label="核定意见/退回原因"
              ><a-textarea
                v-model:value="reviewForm.reviewComment" /></a-form-item></a-col></a-row></a-form
      ><a-table
        v-if="reviewForm.decision === 'CONFIRMED'"
        :data-source="reviewLines"
        row-key="measurementLineId"
        :pagination="false"
        size="small"
        ><a-table-column title="清单/变更项" data-index="itemName" /><a-table-column
          title="本次报量"
          data-index="submittedQuantity" /><a-table-column title="业主核定量"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="record.confirmedQuantity"
              :min="0"
              :max="record.submittedQuantity"
              :precision="4" /></template></a-table-column
        ><a-table-column title="核减原因"
          ><template #default="{ record }"
            ><a-input
              v-model:value="record.deductionReason"
              :placeholder="
                record.confirmedQuantity < record.submittedQuantity
                  ? '存在核减，必填'
                  : '无核减可留空'
              " /></template></a-table-column></a-table
    ></a-modal>
  </div>
</template>

<style scoped>
.measurement-page {
  padding: 0 20px 24px;
}
.chain-alert,
.toolbar {
  margin-bottom: 16px;
}
</style>
