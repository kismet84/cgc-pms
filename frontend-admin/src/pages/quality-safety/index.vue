<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import { getMemberList } from '@/api/modules/project'
import { uploadFile } from '@/api/modules/file'
import type { ContractVO } from '@/types/contract'
import type { MemberVO } from '@/types/project'
import type { PartnerVO } from '@/types/partner'
import {
  activateQualityPlan,
  completeQualityPlan,
  createQualityConsequence,
  createQualityInspection,
  createQualityIssue,
  createQualityPlan,
  createQualityRectification,
  getQualityInspections,
  getQualityIssues,
  getQualityPlans,
  getQualityTrace,
  postQualityConsequence,
  reinspectQualityRectification,
  submitQualityInspection,
  submitQualityRectification,
  type QualityConsequence,
  type QualityInspection,
  type QualityIssue,
  type QualityPlan,
  type QualityRectification,
  type QualityTrace,
} from '@/api/modules/qualitySafety'

type EvidenceTarget = 'inspection' | 'issue' | 'rectification' | 'reinspection'

const referenceStore = useReferenceStore()
const userStore = useUserStore()
const { projects } = storeToRefs(referenceStore)
const projectId = ref<string>()
const loading = ref(false)
const activeTab = ref('plans')
const plans = ref<QualityPlan[]>([])
const selectedPlanId = ref<string>()
const inspections = ref<QualityInspection[]>([])
const issues = ref<QualityIssue[]>([])
const members = ref<MemberVO[]>([])
const partners = ref<PartnerVO[]>([])
const contracts = ref<ContractVO[]>([])
const trace = ref<QualityTrace>()
const traceOpen = ref(false)
const planOpen = ref(false)
const inspectionOpen = ref(false)
const issueOpen = ref(false)
const rectificationOpen = ref(false)
const reinspectionOpen = ref(false)
const consequenceOpen = ref(false)
const currentInspection = ref<QualityInspection>()
const currentIssue = ref<QualityIssue>()
const currentRectification = ref<QualityRectification>()
const evidence = reactive<Record<EvidenceTarget, File | undefined>>({
  inspection: undefined,
  issue: undefined,
  rectification: undefined,
  reinspection: undefined,
})

const today = () => new Date().toISOString().slice(0, 10)
const currentUserId = computed(() => userStore.userInfo?.userId ?? '')
const selectedPlan = computed(() => plans.value.find((item) => item.id === selectedPlanId.value))
const externalPartners = computed(() =>
  partners.value.filter((item) => ['SUPPLIER', 'SUB', 'SUBCONTRACTOR'].includes(item.partnerType)),
)
const projectContracts = computed(() =>
  contracts.value.filter((item) => item.projectId === projectId.value),
)
const kpi = computed(() => ({
  open: issues.value.filter((item) => item.status === 'RECTIFYING').length,
  pending: issues.value.filter((item) => item.status === 'PENDING_REINSPECTION').length,
  overdue: issues.value.filter((item) => item.status !== 'CLOSED' && item.dueDate < today()).length,
  closed: issues.value.filter((item) => item.status === 'CLOSED').length,
}))

const planForm = reactive({
  planCode: '',
  planName: '',
  inspectionType: 'QUALITY' as 'QUALITY' | 'SAFETY',
  frequencyType: 'SINGLE' as 'SINGLE' | 'WEEKLY' | 'MONTHLY',
  startDate: today(),
  endDate: today(),
  ownerUserId: '',
  remark: '',
})
const inspectionForm = reactive({
  inspectionCode: '',
  inspectionDate: today(),
  location: '',
  inspectorUserId: '',
  summary: '',
})
const issueForm = reactive({
  category: '',
  severity: 'MEDIUM' as QualityIssue['severity'],
  title: '',
  description: '',
  responsibleKind: 'INTERNAL' as QualityIssue['responsibleKind'],
  responsiblePartnerId: undefined as string | undefined,
  responsibleUserId: '',
  dueDate: today(),
})
const rectificationForm = reactive({
  actionDescription: '',
  responsibleUserId: '',
  plannedCompleteDate: today(),
})
const reinspectionForm = reactive({ result: 'PASS' as 'PASS' | 'REJECT', comment: '' })
const consequenceForm = reactive({
  partnerId: '',
  contractId: undefined as string | undefined,
  consequenceCode: '',
  decisionType: 'NONE' as QualityConsequence['decisionType'],
  fineAmount: 0,
  reworkCostAmount: 0,
  evaluationScore: 80,
  evaluationComment: '',
})

function can(permission: string) {
  return (
    userStore.hasPermission(permission) ||
    userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role))
  )
}
function statusName(status: string) {
  return (
    (
      {
        DRAFT: '草稿',
        ACTIVE: '执行中',
        COMPLETED: '已完成',
        SUBMITTED: '已提交',
        OPEN: '待启动整改',
        RECTIFYING: '整改中',
        PENDING_REINSPECTION: '待复验',
        CLOSED: '已关闭',
        PASSED: '复验通过',
        REJECTED: '复验驳回',
        POSTED: '已确认',
        PASS: '合格',
        ISSUES: '发现问题',
      } as Record<string, string>
    )[status] ?? status
  )
}
function statusColor(status: string) {
  return (
    (
      {
        ACTIVE: 'processing',
        COMPLETED: 'success',
        SUBMITTED: 'blue',
        RECTIFYING: 'warning',
        PENDING_REINSPECTION: 'processing',
        CLOSED: 'success',
        PASSED: 'success',
        REJECTED: 'error',
        POSTED: 'success',
        PASS: 'success',
        ISSUES: 'error',
      } as Record<string, string>
    )[status] ?? 'default'
  )
}
function severityColor(severity: string) {
  return (
    (
      { LOW: 'green', MEDIUM: 'orange', HIGH: 'red', CRITICAL: 'magenta' } as Record<string, string>
    )[severity] ?? 'default'
  )
}
function memberName(userId: string) {
  return members.value.find((item) => item.userId === userId)?.userName ?? userId
}
function partnerName(partnerId?: string) {
  return externalPartners.value.find((item) => item.id === partnerId)?.partnerName ?? '-'
}
function setEvidence(event: Event, target: EvidenceTarget) {
  evidence[target] = (event.target as HTMLInputElement).files?.[0]
}

async function loadProject() {
  plans.value = []
  inspections.value = []
  issues.value = []
  selectedPlanId.value = undefined
  if (!projectId.value) return
  loading.value = true
  try {
    const [loadedPlans, loadedIssues, memberPage, loadedContracts, loadedPartners] =
      await Promise.all([
        getQualityPlans(projectId.value),
        getQualityIssues(projectId.value),
        getMemberList(projectId.value, { pageNo: 1, pageSize: 500 }),
        referenceStore.fetchContracts({ projectId: projectId.value }),
        referenceStore.fetchPartners(),
      ])
    plans.value = loadedPlans
    issues.value = loadedIssues
    members.value = memberPage.records ?? []
    contracts.value = loadedContracts
    partners.value = loadedPartners
    selectedPlanId.value = plans.value[0]?.id
    await loadInspections()
  } finally {
    loading.value = false
  }
}
async function loadInspections() {
  inspections.value = selectedPlanId.value ? await getQualityInspections(selectedPlanId.value) : []
}
async function selectPlan(row: QualityPlan) {
  selectedPlanId.value = row.id
  await loadInspections()
  activeTab.value = 'inspections'
}
function openPlan() {
  if (!projectId.value) return message.warning('请先选择项目')
  Object.assign(planForm, {
    planCode: `QS-${today().replaceAll('-', '')}`,
    planName: '质量安全检查计划',
    inspectionType: 'QUALITY',
    frequencyType: 'SINGLE',
    startDate: today(),
    endDate: today(),
    ownerUserId: currentUserId.value,
    remark: '',
  })
  planOpen.value = true
}
async function savePlan() {
  if (!projectId.value || !planForm.planCode || !planForm.planName || !planForm.ownerUserId)
    return message.warning('请完整填写检查计划')
  const created = await createQualityPlan({ projectId: projectId.value, ...planForm })
  planOpen.value = false
  await loadProject()
  selectedPlanId.value = created.id
  await loadInspections()
  message.success('检查计划已创建')
}
async function activatePlan(row: QualityPlan) {
  await activateQualityPlan(row.id)
  message.success('检查计划已激活')
  await loadProject()
}
async function completePlan(row: QualityPlan) {
  Modal.confirm({
    title: '完成检查计划',
    content: '系统将校验检查记录和问题单是否全部闭环。',
    async onOk() {
      await completeQualityPlan(row.id)
      message.success('检查计划已完成')
      await loadProject()
    },
  })
}
function openInspection() {
  if (!selectedPlan.value || selectedPlan.value.status !== 'ACTIVE')
    return message.warning('请选择执行中的检查计划')
  Object.assign(inspectionForm, {
    inspectionCode: `CHK-${today().replaceAll('-', '')}`,
    inspectionDate: today(),
    location: '',
    inspectorUserId: currentUserId.value,
    summary: '',
  })
  evidence.inspection = undefined
  inspectionOpen.value = true
}
async function saveInspection() {
  if (
    !selectedPlanId.value ||
    !inspectionForm.location ||
    !inspectionForm.summary ||
    !inspectionForm.inspectorUserId
  )
    return message.warning('请完整填写检查记录')
  if (!evidence.inspection) return message.warning('检查记录必须上传现场证据')
  const created = await createQualityInspection({ planId: selectedPlanId.value, ...inspectionForm })
  await uploadFile(evidence.inspection, 'QS_INSPECTION', created.id, 'INSPECTION_EVIDENCE')
  inspectionOpen.value = false
  await loadInspections()
  message.success('检查记录及现场证据已保存')
}
function openIssue(row: QualityInspection) {
  currentInspection.value = row
  Object.assign(issueForm, {
    category: '',
    severity: 'MEDIUM',
    title: '',
    description: '',
    responsibleKind: 'INTERNAL',
    responsiblePartnerId: undefined,
    responsibleUserId: currentUserId.value,
    dueDate: row.inspectionDate,
  })
  evidence.issue = undefined
  issueOpen.value = true
}
async function saveIssue() {
  const inspection = currentInspection.value
  if (
    !inspection ||
    !issueForm.category ||
    !issueForm.title ||
    !issueForm.description ||
    !issueForm.responsibleUserId
  )
    return message.warning('请完整填写问题单')
  if (issueForm.responsibleKind === 'PARTNER' && !issueForm.responsiblePartnerId)
    return message.warning('外部责任问题必须选择供应商或分包商')
  if (!evidence.issue) return message.warning('问题单必须上传问题证据')
  const created = await createQualityIssue(inspection.id, {
    inspectionId: inspection.id,
    ...issueForm,
  })
  await uploadFile(evidence.issue, 'QS_ISSUE', created.id, 'ISSUE_EVIDENCE')
  issueOpen.value = false
  message.success('问题单及证据已保存')
}
async function submitInspection(row: QualityInspection) {
  await submitQualityInspection(row.id)
  message.success('检查记录已提交，问题单进入整改')
  await Promise.all([
    loadInspections(),
    projectId.value
      ? getQualityIssues(projectId.value).then((rows) => (issues.value = rows))
      : Promise.resolve(),
  ])
}
async function openTrace(row: QualityIssue) {
  currentIssue.value = row
  trace.value = await getQualityTrace(row.id)
  traceOpen.value = true
}
function openRectification(row: QualityIssue) {
  currentIssue.value = row
  Object.assign(rectificationForm, {
    actionDescription: '',
    responsibleUserId: row.responsibleUserId,
    plannedCompleteDate: row.dueDate,
  })
  evidence.rectification = undefined
  rectificationOpen.value = true
}
async function saveRectification() {
  const issue = currentIssue.value
  if (!issue || !rectificationForm.actionDescription || !evidence.rectification)
    return message.warning('请填写整改措施并上传整改证据')
  const created = await createQualityRectification({ issueId: issue.id, ...rectificationForm })
  await uploadFile(evidence.rectification, 'QS_RECTIFICATION', created.id, 'RECTIFICATION_EVIDENCE')
  await submitQualityRectification(created.id)
  rectificationOpen.value = false
  message.success('整改结果已提交复验')
  await loadProject()
}
async function openReinspection(row: QualityIssue) {
  currentIssue.value = row
  const loaded = await getQualityTrace(row.id)
  const submitted = [...loaded.rectifications].reverse().find((item) => item.status === 'SUBMITTED')
  if (!submitted) return message.warning('未找到待复验的整改轮次')
  currentRectification.value = submitted
  Object.assign(reinspectionForm, { result: 'PASS', comment: '' })
  evidence.reinspection = undefined
  reinspectionOpen.value = true
}
async function saveReinspection() {
  if (!currentRectification.value || !reinspectionForm.comment || !evidence.reinspection)
    return message.warning('请填写复验意见并上传复验证据')
  await uploadFile(
    evidence.reinspection,
    'QS_RECTIFICATION',
    currentRectification.value.id,
    'REINSPECTION_EVIDENCE',
  )
  await reinspectQualityRectification(
    currentRectification.value.id,
    reinspectionForm.result,
    reinspectionForm.comment,
  )
  reinspectionOpen.value = false
  message.success(
    reinspectionForm.result === 'PASS' ? '复验通过，问题单已关闭' : '复验驳回，问题单退回整改',
  )
  await loadProject()
}
function openConsequence(row: QualityIssue) {
  if (!row.responsiblePartnerId) return message.warning('内部责任问题无需生成合作方评价')
  currentIssue.value = row
  Object.assign(consequenceForm, {
    partnerId: row.responsiblePartnerId,
    contractId: undefined,
    consequenceCode: `QS-C-${today().replaceAll('-', '')}`,
    decisionType: 'NONE',
    fineAmount: 0,
    reworkCostAmount: 0,
    evaluationScore: 80,
    evaluationComment: '',
  })
  consequenceOpen.value = true
}
async function saveConsequence() {
  const issue = currentIssue.value
  if (!issue || !consequenceForm.partnerId || !consequenceForm.evaluationComment)
    return message.warning('请完整填写处罚成本与评价')
  const created = await createQualityConsequence({ issueId: issue.id, ...consequenceForm })
  await postQualityConsequence(created.id)
  consequenceOpen.value = false
  message.success('处罚成本与合作方评价已确认，返工成本已自动入账')
  await loadProject()
}

onMounted(async () => {
  await referenceStore.fetchProjects()
})
</script>

<template>
  <div class="qs-page">
    <div class="qs-hero">
      <div>
        <h2>质量安全整改闭环</h2>
        <p>检查计划 → 检查记录 → 问题单 → 整改 → 复验 → 处罚/成本 → 合作方评价</p>
      </div>
      <a-select
        v-model:value="projectId"
        show-search
        option-filter-prop="label"
        placeholder="选择项目"
        style="width: 320px"
        @change="loadProject"
      >
        <a-select-option
          v-for="project in projects ?? []"
          :key="project.id"
          :value="project.id"
          :label="project.projectName"
          >{{ project.projectName }}</a-select-option
        >
      </a-select>
    </div>
    <a-row :gutter="16" class="qs-kpis">
      <a-col :span="6"
        ><a-card><a-statistic title="整改中" :value="kpi.open" /></a-card
      ></a-col>
      <a-col :span="6"
        ><a-card><a-statistic title="待复验" :value="kpi.pending" /></a-card
      ></a-col>
      <a-col :span="6"
        ><a-card
          ><a-statistic
            title="已逾期"
            :value="kpi.overdue"
            :value-style="{ color: kpi.overdue ? '#cf1322' : undefined }" /></a-card
      ></a-col>
      <a-col :span="6"
        ><a-card><a-statistic title="已关闭" :value="kpi.closed" /></a-card
      ></a-col>
    </a-row>

    <a-card :loading="loading">
      <a-tabs v-model:active-key="activeTab">
        <a-tab-pane key="plans" tab="检查计划">
          <div class="toolbar">
            <a-button
              type="primary"
              :disabled="!projectId || !can('quality:safety:plan:maintain')"
              @click="openPlan"
              >新建检查计划</a-button
            >
          </div>
          <a-table :data-source="plans" row-key="id" :pagination="false">
            <a-table-column title="计划编号" data-index="planCode" />
            <a-table-column title="计划名称" data-index="planName" />
            <a-table-column title="类型"
              ><template #default="{ record }"
                ><a-tag>{{
                  record.inspectionType === 'QUALITY' ? '质量' : '安全'
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="周期"
              ><template #default="{ record }"
                >{{ record.startDate }} ～ {{ record.endDate }}</template
              ></a-table-column
            >
            <a-table-column title="责任人"
              ><template #default="{ record }">{{
                memberName(record.ownerUserId)
              }}</template></a-table-column
            >
            <a-table-column title="状态"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作" :width="260"
              ><template #default="{ record }">
                <a-space>
                  <a-button
                    v-if="record.status === 'DRAFT'"
                    type="link"
                    :disabled="!can('quality:safety:plan:maintain')"
                    @click="activatePlan(record)"
                    >激活</a-button
                  >
                  <a-button type="link" @click="selectPlan(record)">检查记录</a-button>
                  <a-button
                    v-if="record.status === 'ACTIVE'"
                    type="link"
                    :disabled="!can('quality:safety:plan:maintain')"
                    @click="completePlan(record)"
                    >完成计划</a-button
                  >
                </a-space>
              </template></a-table-column
            >
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="inspections" tab="检查记录">
          <div class="toolbar">
            <a-select
              v-model:value="selectedPlanId"
              placeholder="选择检查计划"
              style="width: 320px"
              @change="loadInspections"
            >
              <a-select-option v-for="plan in plans" :key="plan.id" :value="plan.id"
                >{{ plan.planName }}（{{ statusName(plan.status) }}）</a-select-option
              >
            </a-select>
            <a-button
              type="primary"
              :disabled="
                selectedPlan?.status !== 'ACTIVE' || !can('quality:safety:inspection:maintain')
              "
              @click="openInspection"
              >新增检查记录</a-button
            >
          </div>
          <a-table :data-source="inspections" row-key="id" :pagination="false">
            <a-table-column title="检查编号" data-index="inspectionCode" />
            <a-table-column title="日期" data-index="inspectionDate" />
            <a-table-column title="位置" data-index="location" />
            <a-table-column title="检查人"
              ><template #default="{ record }">{{
                memberName(record.inspectorUserId)
              }}</template></a-table-column
            >
            <a-table-column title="结论"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.conclusion)">{{
                  statusName(record.conclusion)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="状态"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作" :width="220"
              ><template #default="{ record }"
                ><a-space>
                  <a-button
                    v-if="record.status === 'DRAFT'"
                    type="link"
                    :disabled="!can('quality:safety:inspection:maintain')"
                    @click="openIssue(record)"
                    >登记问题</a-button
                  >
                  <a-button
                    v-if="record.status === 'DRAFT'"
                    type="link"
                    :disabled="!can('quality:safety:inspection:maintain')"
                    @click="submitInspection(record)"
                    >提交检查</a-button
                  >
                </a-space></template
              ></a-table-column
            >
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="issues" tab="整改问题台账">
          <a-table :data-source="issues" row-key="id" :pagination="{ pageSize: 20 }">
            <a-table-column title="问题编号" data-index="issueCode" />
            <a-table-column title="问题" data-index="title" />
            <a-table-column title="类别" data-index="category" />
            <a-table-column title="等级"
              ><template #default="{ record }"
                ><a-tag :color="severityColor(record.severity)">{{
                  record.severity
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="责任"
              ><template #default="{ record }">{{
                record.responsibleKind === 'PARTNER'
                  ? partnerName(record.responsiblePartnerId)
                  : memberName(record.responsibleUserId)
              }}</template></a-table-column
            >
            <a-table-column title="期限" data-index="dueDate" />
            <a-table-column title="状态"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作" :width="300"
              ><template #default="{ record }"
                ><a-space wrap>
                  <a-button type="link" @click="openTrace(record)">全链路</a-button>
                  <a-button
                    v-if="record.status === 'RECTIFYING'"
                    type="link"
                    :disabled="!can('quality:safety:rectify')"
                    @click="openRectification(record)"
                    >提交整改</a-button
                  >
                  <a-button
                    v-if="record.status === 'PENDING_REINSPECTION'"
                    type="link"
                    :disabled="!can('quality:safety:reinspect')"
                    @click="openReinspection(record)"
                    >复验</a-button
                  >
                  <a-button
                    v-if="record.status === 'CLOSED' && record.responsiblePartnerId"
                    type="link"
                    :disabled="!can('quality:safety:consequence')"
                    @click="openConsequence(record)"
                    >处罚与评价</a-button
                  >
                </a-space></template
              ></a-table-column
            >
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <a-modal v-model:open="planOpen" title="新建检查计划" @ok="savePlan">
      <a-form layout="vertical"
        ><a-form-item label="计划编号"><a-input v-model:value="planForm.planCode" /></a-form-item
        ><a-form-item label="计划名称"><a-input v-model:value="planForm.planName" /></a-form-item>
        <a-row :gutter="12"
          ><a-col :span="12"
            ><a-form-item label="检查类型"
              ><a-select v-model:value="planForm.inspectionType"
                ><a-select-option value="QUALITY">质量</a-select-option
                ><a-select-option value="SAFETY">安全</a-select-option></a-select
              ></a-form-item
            ></a-col
          ><a-col :span="12"
            ><a-form-item label="频次"
              ><a-select v-model:value="planForm.frequencyType"
                ><a-select-option value="SINGLE">单次</a-select-option
                ><a-select-option value="WEEKLY">每周</a-select-option
                ><a-select-option value="MONTHLY">每月</a-select-option></a-select
              ></a-form-item
            ></a-col
          ></a-row
        >
        <a-row :gutter="12"
          ><a-col :span="12"
            ><a-form-item label="开始日期"
              ><a-input v-model:value="planForm.startDate" type="date" /></a-form-item></a-col
          ><a-col :span="12"
            ><a-form-item label="结束日期"
              ><a-input v-model:value="planForm.endDate" type="date" /></a-form-item></a-col
        ></a-row>
        <a-form-item label="计划责任人"
          ><a-select v-model:value="planForm.ownerUserId"
            ><a-select-option
              v-for="member in members"
              :key="member.userId"
              :value="member.userId"
              >{{ member.userName ?? member.userId }}</a-select-option
            ></a-select
          ></a-form-item
        ></a-form
      >
    </a-modal>
    <a-modal v-model:open="inspectionOpen" title="新增检查记录" @ok="saveInspection"
      ><a-form layout="vertical"
        ><a-form-item label="检查编号"
          ><a-input v-model:value="inspectionForm.inspectionCode" /></a-form-item
        ><a-form-item label="检查日期"
          ><a-input v-model:value="inspectionForm.inspectionDate" type="date" /></a-form-item
        ><a-form-item label="检查位置"
          ><a-input v-model:value="inspectionForm.location" /></a-form-item
        ><a-form-item label="检查人"
          ><a-select v-model:value="inspectionForm.inspectorUserId"
            ><a-select-option
              v-for="member in members"
              :key="member.userId"
              :value="member.userId"
              >{{ member.userName ?? member.userId }}</a-select-option
            ></a-select
          ></a-form-item
        ><a-form-item label="检查摘要"
          ><a-textarea v-model:value="inspectionForm.summary" :rows="3" /></a-form-item
        ><a-form-item label="现场证据（必传）"
          ><input type="file" @change="setEvidence($event, 'inspection')" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="issueOpen" title="登记问题单" @ok="saveIssue"
      ><a-form layout="vertical"
        ><a-form-item label="问题类别"><a-input v-model:value="issueForm.category" /></a-form-item
        ><a-form-item label="严重程度"
          ><a-select v-model:value="issueForm.severity"
            ><a-select-option value="LOW">低</a-select-option
            ><a-select-option value="MEDIUM">中</a-select-option
            ><a-select-option value="HIGH">高</a-select-option
            ><a-select-option value="CRITICAL">重大</a-select-option></a-select
          ></a-form-item
        ><a-form-item label="问题标题"><a-input v-model:value="issueForm.title" /></a-form-item
        ><a-form-item label="问题描述"
          ><a-textarea v-model:value="issueForm.description" :rows="3" /></a-form-item
        ><a-form-item label="责任类型"
          ><a-radio-group v-model:value="issueForm.responsibleKind"
            ><a-radio value="INTERNAL">内部</a-radio
            ><a-radio value="PARTNER">供应商/分包商</a-radio></a-radio-group
          ></a-form-item
        ><a-form-item v-if="issueForm.responsibleKind === 'PARTNER'" label="责任合作方"
          ><a-select
            v-model:value="issueForm.responsiblePartnerId"
            show-search
            option-filter-prop="label"
            ><a-select-option
              v-for="partner in externalPartners"
              :key="partner.id"
              :value="partner.id"
              :label="partner.partnerName"
              >{{ partner.partnerName }}</a-select-option
            ></a-select
          ></a-form-item
        ><a-form-item label="整改责任人"
          ><a-select v-model:value="issueForm.responsibleUserId"
            ><a-select-option
              v-for="member in members"
              :key="member.userId"
              :value="member.userId"
              >{{ member.userName ?? member.userId }}</a-select-option
            ></a-select
          ></a-form-item
        ><a-form-item label="整改期限"
          ><a-input v-model:value="issueForm.dueDate" type="date" /></a-form-item
        ><a-form-item label="问题证据（必传）"
          ><input type="file" @change="setEvidence($event, 'issue')" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="rectificationOpen" title="提交整改" @ok="saveRectification"
      ><a-form layout="vertical"
        ><a-form-item label="整改措施"
          ><a-textarea v-model:value="rectificationForm.actionDescription" :rows="4" /></a-form-item
        ><a-form-item label="计划完成日"
          ><a-input
            v-model:value="rectificationForm.plannedCompleteDate"
            type="date" /></a-form-item
        ><a-form-item label="整改完成证据（必传）"
          ><input
            type="file"
            @change="setEvidence($event, 'rectification')" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="reinspectionOpen" title="整改复验" @ok="saveReinspection"
      ><a-form layout="vertical"
        ><a-form-item label="复验结果"
          ><a-radio-group v-model:value="reinspectionForm.result"
            ><a-radio value="PASS">通过并关闭</a-radio
            ><a-radio value="REJECT">驳回重整</a-radio></a-radio-group
          ></a-form-item
        ><a-form-item label="复验意见"
          ><a-textarea v-model:value="reinspectionForm.comment" :rows="4" /></a-form-item
        ><a-form-item label="复验证据（必传）"
          ><input type="file" @change="setEvidence($event, 'reinspection')" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="consequenceOpen" title="处罚、成本与合作方评价" @ok="saveConsequence"
      ><a-form layout="vertical"
        ><a-form-item label="责任合作方"
          ><a-select v-model:value="consequenceForm.partnerId" disabled
            ><a-select-option
              v-for="partner in externalPartners"
              :key="partner.id"
              :value="partner.id"
              >{{ partner.partnerName }}</a-select-option
            ></a-select
          ></a-form-item
        ><a-form-item label="关联合同（可选）"
          ><a-select v-model:value="consequenceForm.contractId" allow-clear
            ><a-select-option
              v-for="contract in projectContracts"
              :key="contract.id"
              :value="contract.id"
              >{{ contract.contractName }}</a-select-option
            ></a-select
          ></a-form-item
        ><a-form-item label="记录编号"
          ><a-input v-model:value="consequenceForm.consequenceCode" /></a-form-item
        ><a-form-item label="处理决定"
          ><a-select v-model:value="consequenceForm.decisionType"
            ><a-select-option value="NONE">不处罚/无返工成本</a-select-option
            ><a-select-option value="FINE">罚款/扣款</a-select-option
            ><a-select-option value="REWORK_COST">返工成本</a-select-option
            ><a-select-option value="BOTH">罚款并计返工成本</a-select-option></a-select
          ></a-form-item
        ><a-row :gutter="12"
          ><a-col :span="12"
            ><a-form-item label="罚款/扣款金额"
              ><a-input-number
                v-model:value="consequenceForm.fineAmount"
                :min="0"
                style="width: 100%" /></a-form-item></a-col
          ><a-col :span="12"
            ><a-form-item label="返工成本"
              ><a-input-number
                v-model:value="consequenceForm.reworkCostAmount"
                :min="0"
                style="width: 100%" /></a-form-item></a-col></a-row
        ><a-form-item label="履约评分（0-100）"
          ><a-input-number
            v-model:value="consequenceForm.evaluationScore"
            :min="0"
            :max="100"
            style="width: 100%" /></a-form-item
        ><a-form-item label="评价说明"
          ><a-textarea
            v-model:value="consequenceForm.evaluationComment"
            :rows="3" /></a-form-item></a-form
    ></a-modal>

    <a-drawer v-model:open="traceOpen" title="质量安全全链路追溯" width="680">
      <a-descriptions v-if="trace" bordered :column="1" size="small">
        <a-descriptions-item label="检查计划"
          >{{ trace.plan.planCode }} / {{ trace.plan.planName }}</a-descriptions-item
        >
        <a-descriptions-item label="检查记录"
          >{{ trace.inspection.inspectionCode }} /
          {{ trace.inspection.inspectionDate }}</a-descriptions-item
        >
        <a-descriptions-item label="问题单"
          >{{ trace.issue.issueCode }} / {{ trace.issue.title }}</a-descriptions-item
        >
        <a-descriptions-item label="责任与期限"
          >{{
            trace.issue.responsibleKind === 'PARTNER'
              ? partnerName(trace.issue.responsiblePartnerId)
              : memberName(trace.issue.responsibleUserId)
          }}
          / {{ trace.issue.dueDate }}</a-descriptions-item
        >
        <a-descriptions-item label="整改复验轮次"
          ><div v-for="item in trace.rectifications" :key="item.id">
            第 {{ item.roundNo }} 轮：{{ statusName(item.status) }} — {{ item.actionDescription }}
          </div>
          <span v-if="!trace.rectifications.length">尚无</span></a-descriptions-item
        >
        <a-descriptions-item label="处罚与成本"
          ><span v-if="trace.consequence"
            >{{ trace.consequence.decisionType }}；罚款 {{ trace.consequence.fineAmount }}；返工成本
            {{ trace.consequence.reworkCostAmount }}；{{
              statusName(trace.consequence.status)
            }}</span
          ><span v-else>尚未登记</span></a-descriptions-item
        >
        <a-descriptions-item label="合作方评价"
          ><span v-if="trace.evaluation"
            >{{ trace.evaluation.score }} 分 — {{ trace.evaluation.evaluationComment }}</span
          ><span v-else>尚未生成</span></a-descriptions-item
        >
        <a-descriptions-item label="成本台账"
          ><span v-if="trace.costItem"
            >成本记录 {{ trace.costItem.id }}，金额 {{ trace.costItem.amount }}</span
          ><span v-else>无返工成本</span></a-descriptions-item
        >
      </a-descriptions>
    </a-drawer>
  </div>
</template>

<style scoped>
.qs-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.qs-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22px 24px;
  border-radius: 14px;
  background: linear-gradient(135deg, #102a43, #155e75);
  color: white;
}
.qs-hero h2 {
  margin: 0 0 6px;
  color: white;
}
.qs-hero p {
  margin: 0;
  opacity: 0.82;
}
.qs-kpis :deep(.ant-card) {
  border-radius: 12px;
}
.toolbar {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 16px;
}
</style>
