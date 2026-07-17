<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import { uploadFile } from '@/api/modules/file'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import {
  confirmAcceptanceArchive,
  confirmDrawingReview,
  confirmTechnicalDisclosure,
  createAcceptanceArchive,
  createConstructionReference,
  createDrawingReview,
  createTechnicalDisclosure,
  createTechnicalRfi,
  createTechnicalScheme,
  getDrawingTrace,
  getTechnicalOverview,
  receiveDrawingVersion,
  receiveTechnicalDrawing,
  respondTechnicalRfi,
  reviewTechnicalRfiResponse,
  submitTechnicalRfi,
  submitTechnicalScheme,
  type DrawingTrace,
  type TechnicalOverview,
  type TechnicalRow,
} from '@/api/modules/technicalManagement'

type DialogKind =
  | 'scheme'
  | 'drawing'
  | 'version'
  | 'review'
  | 'rfi'
  | 'response'
  | 'responseReview'
  | 'disclosure'
  | 'reference'
  | 'archive'

const emptyOverview = (): TechnicalOverview => ({
  schemes: [],
  drawings: [],
  versions: [],
  reviews: [],
  rfis: [],
  responses: [],
  disclosures: [],
  constructionReferences: [],
  archives: [],
  constructionFacts: [],
  qualityInspections: [],
})

const referenceStore = useReferenceStore()
const userStore = useUserStore()
const { projects } = storeToRefs(referenceStore)
const projectId = ref<string>()
const loading = ref(false)
const saving = ref(false)
const activeTab = ref('drawings')
const overview = ref<TechnicalOverview>(emptyOverview())
const trace = ref<DrawingTrace>()
const traceOpen = ref(false)
const dialogOpen = ref(false)
const dialogKind = ref<DialogKind>('scheme')
const contextId = ref('')
const evidenceFile = ref<File>()

const today = () => new Date().toISOString().slice(0, 10)
const now = () => new Date().toISOString().slice(0, 16)
const currentUserId = computed(() => userStore.userInfo?.userId ?? '')
const kpi = computed(() => ({
  drawings: overview.value.drawings.length,
  openRfis: overview.value.rfis.filter((item) => !['CLOSED', 'CANCELLED'].includes(item.status))
    .length,
  approvedVersions: overview.value.versions.filter((item) => item.status === 'APPROVED').length,
  archived: overview.value.archives.filter((item) => item.status === 'ARCHIVED').length,
}))
const acceptedChangeRfis = computed(() =>
  overview.value.rfis.filter((item) => item.status === 'CHANGE_PENDING'),
)
const approvedVersions = computed(() =>
  overview.value.versions.filter((item) => item.status === 'APPROVED'),
)
const approvedSchemes = computed(() =>
  overview.value.schemes.filter((item) => item.status === 'APPROVED'),
)
const availableReferences = computed(() =>
  overview.value.constructionReferences.filter(
    (item) =>
      item.status === 'RECORDED' &&
      !overview.value.archives.some((archive) => archive.constructionReferenceId === item.id),
  ),
)

const form = reactive({
  code: '',
  name: '',
  type: 'SPECIAL',
  responsibleUserId: '',
  plannedDate: today(),
  specialty: '',
  sourceOrganization: '',
  versionNo: '',
  receivedAt: now(),
  previousVersionId: '',
  sourceRfiId: '',
  changeSummary: '',
  reviewDate: today(),
  chairUserId: '',
  participants: '',
  conclusion: 'PASS',
  summary: '',
  requiresRfi: false,
  subject: '',
  question: '',
  priority: 'NORMAL',
  dueDate: today(),
  responseContent: '',
  changeRequired: false,
  responderName: '',
  decision: 'ACCEPTED',
  comment: '',
  drawingVersionId: '',
  schemeId: '',
  presenterUserId: '',
  recipients: '',
  content: '',
  disclosureId: '',
  factId: '',
  workArea: '',
  archiveLocation: '',
  referenceId: '',
  inspectionId: '',
  remark: '',
})

const dialogTitle = computed(
  () =>
    ({
      scheme: '新建技术方案',
      drawing: '接收施工图纸',
      version: '接收图纸变更版',
      review: '登记图纸会审',
      rfi: '发起设计 RFI',
      response: '登记设计回复',
      responseReview: '复核设计回复',
      disclosure: '登记技术交底',
      reference: '登记施工引用',
      archive: '登记验收归档',
    })[dialogKind.value],
)

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
        PENDING: '审批中',
        APPROVED: '已批准',
        REJECTED: '已驳回',
        SUPERSEDED: '已替代',
        RECEIVED: '已接收',
        UNDER_REVIEW: '会审中',
        RFI_PENDING: '待RFI闭合',
        CONFIRMED: '已确认',
        SUBMITTED: '已提交',
        RESPONDED: '已回复',
        CHANGE_PENDING: '待改版',
        CLOSED: '已关闭',
        ACCEPTED: '已接受',
        RECORDED: '已留痕',
        ARCHIVED: '已归档',
      } as Record<string, string>
    )[status] ?? status
  )
}
function statusColor(status: string) {
  if (['APPROVED', 'CONFIRMED', 'CLOSED', 'ACCEPTED', 'RECORDED', 'ARCHIVED'].includes(status))
    return 'success'
  if (['REJECTED'].includes(status)) return 'error'
  if (['PENDING', 'UNDER_REVIEW', 'SUBMITTED', 'RESPONDED'].includes(status)) return 'processing'
  if (['RFI_PENDING', 'CHANGE_PENDING'].includes(status)) return 'warning'
  return 'default'
}
function setFile(event: Event) {
  evidenceFile.value = (event.target as HTMLInputElement).files?.[0]
}
function versionName(id?: string) {
  const row = overview.value.versions.find((item) => item.id === id)
  return row ? `${row.drawingCode}-${row.versionNo}` : (id ?? '-')
}

async function loadProject() {
  overview.value = emptyOverview()
  if (!projectId.value) return
  loading.value = true
  try {
    overview.value = await getTechnicalOverview(projectId.value)
  } finally {
    loading.value = false
  }
}

function openDialog(kind: DialogKind, row?: TechnicalRow) {
  if (!projectId.value) return message.warning('请先选择项目')
  dialogKind.value = kind
  contextId.value = row?.id ?? ''
  evidenceFile.value = undefined
  Object.assign(form, {
    code: '',
    name: '',
    type: 'SPECIAL',
    responsibleUserId: currentUserId.value,
    plannedDate: today(),
    specialty: '',
    sourceOrganization: '',
    versionNo: '',
    receivedAt: now(),
    previousVersionId: '',
    sourceRfiId: '',
    changeSummary: '',
    reviewDate: today(),
    chairUserId: currentUserId.value,
    participants: '',
    conclusion: 'PASS',
    summary: '',
    requiresRfi: false,
    subject: '',
    question: '',
    priority: 'NORMAL',
    dueDate: today(),
    responseContent: '',
    changeRequired: false,
    responderName: '',
    decision: 'ACCEPTED',
    comment: '',
    drawingVersionId: '',
    schemeId: approvedSchemes.value[0]?.id ?? '',
    presenterUserId: currentUserId.value,
    recipients: '',
    content: '',
    disclosureId: '',
    factId: '',
    workArea: '',
    archiveLocation: '',
    referenceId: '',
    inspectionId: '',
    remark: '',
  })
  if (kind === 'version') {
    const rfi = acceptedChangeRfis.value.find((item) =>
      overview.value.versions.some(
        (version) => version.drawingId === row?.id && version.id === item.drawingVersionId,
      ),
    )
    form.sourceRfiId = rfi?.id ?? ''
    form.previousVersionId = rfi?.drawingVersionId ?? ''
  }
  if (kind === 'disclosure' && row) form.drawingVersionId = row.id
  if (kind === 'reference' && row) form.disclosureId = row.id
  if (kind === 'archive' && row) form.referenceId = row.id
  dialogOpen.value = true
}

function requireFile(label: string) {
  if (evidenceFile.value) return true
  message.warning(`${label}必须上传附件`)
  return false
}
async function saveDialog() {
  if (!projectId.value) return
  saving.value = true
  try {
    if (dialogKind.value === 'scheme') {
      if (!form.code || !form.name || !requireFile('技术方案')) return
      const created = await createTechnicalScheme({
        projectId: projectId.value,
        schemeCode: form.code,
        schemeName: form.name,
        schemeType: form.type,
        responsibleUserId: form.responsibleUserId,
        plannedEffectiveDate: form.plannedDate,
        remark: form.remark,
      })
      await uploadFile(evidenceFile.value!, 'TECH_SCHEME', created.id, 'SCHEME_FILE')
    } else if (dialogKind.value === 'drawing') {
      if (!form.code || !form.name || !form.versionNo || !requireFile('图纸版本')) return
      const created = await receiveTechnicalDrawing({
        projectId: projectId.value,
        drawingCode: form.code,
        drawingName: form.name,
        specialty: form.specialty,
        sourceOrganization: form.sourceOrganization,
        versionNo: form.versionNo,
        receivedAt: form.receivedAt,
        changeSummary: form.changeSummary,
        remark: form.remark,
      })
      await uploadFile(
        evidenceFile.value!,
        'TECH_DRAWING_VERSION',
        created.versions[0].id,
        'DRAWING_FILE',
      )
    } else if (dialogKind.value === 'version') {
      if (
        !form.versionNo ||
        !form.previousVersionId ||
        !form.sourceRfiId ||
        !requireFile('变更图纸')
      )
        return
      const created = await receiveDrawingVersion(contextId.value, {
        versionNo: form.versionNo,
        previousVersionId: form.previousVersionId,
        sourceRfiId: form.sourceRfiId,
        receivedAt: form.receivedAt,
        changeSummary: form.changeSummary,
        remark: form.remark,
      })
      await uploadFile(evidenceFile.value!, 'TECH_DRAWING_VERSION', created.id, 'DRAWING_FILE')
    } else if (dialogKind.value === 'review') {
      if (!form.code || !form.summary || !requireFile('会审记录')) return
      const created = await createDrawingReview(contextId.value, {
        reviewCode: form.code,
        reviewDate: form.reviewDate,
        chairUserId: form.chairUserId,
        participantSummary: form.participants,
        conclusion: form.conclusion,
        reviewSummary: form.summary,
        requiresRfi: form.requiresRfi,
        remark: form.remark,
      })
      await uploadFile(evidenceFile.value!, 'TECH_DRAWING_REVIEW', created.id, 'REVIEW_MINUTES')
    } else if (dialogKind.value === 'rfi') {
      if (!form.code || !form.subject || !form.question || !requireFile('RFI问题依据')) return
      const created = await createTechnicalRfi(contextId.value, {
        rfiCode: form.code,
        subject: form.subject,
        question: form.question,
        priority: form.priority,
        responseDueDate: form.dueDate,
        remark: form.remark,
      })
      await uploadFile(evidenceFile.value!, 'TECH_RFI', created.id, 'RFI_EVIDENCE')
    } else if (dialogKind.value === 'response') {
      if (!form.responseContent || !form.responderName || !requireFile('设计回复')) return
      const created = await respondTechnicalRfi(contextId.value, {
        responseContent: form.responseContent,
        changeRequired: form.changeRequired,
        responderName: form.responderName,
      })
      await uploadFile(evidenceFile.value!, 'TECH_RFI_RESPONSE', created.id, 'DESIGN_RESPONSE')
    } else if (dialogKind.value === 'responseReview') {
      if (!form.comment) return message.warning('请填写复核意见')
      await reviewTechnicalRfiResponse(contextId.value, {
        decision: form.decision,
        reviewComment: form.comment,
      })
    } else if (dialogKind.value === 'disclosure') {
      if (!form.code || !form.name || !form.content || !requireFile('交底记录')) return
      const created = await createTechnicalDisclosure(projectId.value, {
        drawingVersionId: form.drawingVersionId,
        schemeId: form.schemeId || null,
        disclosureCode: form.code,
        disclosureTitle: form.name,
        disclosureDate: form.plannedDate,
        presenterUserId: form.presenterUserId,
        recipientSummary: form.recipients,
        disclosureContent: form.content,
        remark: form.remark,
      })
      await uploadFile(evidenceFile.value!, 'TECH_DISCLOSURE', created.id, 'DISCLOSURE_RECORD')
    } else if (dialogKind.value === 'reference') {
      const fact = overview.value.constructionFacts.find((item) => item.progressId === form.factId)
      if (!fact) return message.warning('请选择已提交日报中的WBS施工事实')
      await createConstructionReference(projectId.value, {
        disclosureId: form.disclosureId,
        dailyLogId: fact.dailyLogId,
        wbsTaskId: fact.wbsTaskId,
        referenceDate: fact.reportDate,
        workArea: form.workArea || fact.workArea || fact.taskName,
        referenceDescription: form.content,
        remark: form.remark,
      })
    } else if (dialogKind.value === 'archive') {
      if (!form.inspectionId || !form.archiveLocation || !requireFile('验收归档')) return
      const created = await createAcceptanceArchive(projectId.value, {
        constructionReferenceId: form.referenceId,
        qualityInspectionId: form.inspectionId,
        archiveCode: form.code,
        acceptanceDate: form.plannedDate,
        acceptanceConclusion: form.conclusion,
        archiveLocation: form.archiveLocation,
        remark: form.remark,
      })
      await uploadFile(evidenceFile.value!, 'TECH_ARCHIVE', created.id, 'ACCEPTANCE_ARCHIVE')
    }
    dialogOpen.value = false
    await loadProject()
    message.success('操作已保存，业务链已更新')
  } finally {
    saving.value = false
  }
}

async function transition(action: () => Promise<unknown>, success: string) {
  await action()
  await loadProject()
  message.success(success)
}
async function openTrace(id: string) {
  trace.value = await getDrawingTrace(id)
  traceOpen.value = true
}

onMounted(async () => {
  await referenceStore.fetchProjects()
})
</script>

<template>
  <div class="technical-page">
    <section class="hero-panel">
      <div>
        <p class="eyebrow">TECHNICAL CONTROL</p>
        <h1>图纸、RFI 与技术方案闭环</h1>
        <p class="subtitle">
          把批准方案、图纸版本、设计澄清、技术交底、施工事实与验收档案锁定在同一条证据链。
        </p>
      </div>
      <a-select
        v-model:value="projectId"
        class="project-select"
        show-search
        placeholder="选择项目后加载技术台账"
        :options="
          projects.map((item) => ({
            value: item.id,
            label: `${item.projectCode} · ${item.projectName}`,
          }))
        "
        @change="loadProject"
      />
    </section>

    <a-row :gutter="16" class="kpi-row">
      <a-col :xs="12" :lg="6"
        ><a-card><a-statistic title="图纸台账" :value="kpi.drawings" suffix="套" /></a-card
      ></a-col>
      <a-col :xs="12" :lg="6"
        ><a-card
          ><a-statistic title="有效施工版本" :value="kpi.approvedVersions" suffix="版" /></a-card
      ></a-col>
      <a-col :xs="12" :lg="6"
        ><a-card><a-statistic title="未闭合 RFI" :value="kpi.openRfis" suffix="项" /></a-card
      ></a-col>
      <a-col :xs="12" :lg="6"
        ><a-card><a-statistic title="验收归档" :value="kpi.archived" suffix="份" /></a-card
      ></a-col>
    </a-row>

    <a-card :loading="loading" class="workbench-card">
      <template #title>
        <div class="card-title">
          <span>技术业务台账</span
          ><span class="chain-note">方案 → 图纸 → 会审 → RFI → 改版 → 交底 → 施工 → 验收</span>
        </div>
      </template>
      <template #extra>
        <a-space>
          <a-button
            v-if="can('technical:scheme:maintain')"
            :disabled="!projectId"
            @click="openDialog('scheme')"
            >新建方案</a-button
          >
          <a-button
            v-if="can('technical:drawing:receive')"
            type="primary"
            :disabled="!projectId"
            @click="openDialog('drawing')"
            >接收图纸</a-button
          >
        </a-space>
      </template>

      <a-empty v-if="!projectId" description="请选择项目，系统将加载完整技术闭环台账" />
      <a-tabs v-else v-model:active-key="activeTab">
        <a-tab-pane key="drawings" tab="图纸与版本">
          <a-table
            :data-source="overview.drawings"
            row-key="id"
            size="middle"
            :pagination="false"
            :scroll="{ x: 980 }"
          >
            <a-table-column title="图纸编号" data-index="drawingCode" width="150" />
            <a-table-column title="图纸名称" data-index="drawingName" width="220" />
            <a-table-column title="专业" data-index="specialty" width="100" />
            <a-table-column title="当前版本" width="120"
              ><template #default="{ record }">{{
                record.currentVersionNo || '-'
              }}</template></a-table-column
            >
            <a-table-column title="版本状态" width="130"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.currentVersionStatus)">{{
                  statusName(record.currentVersionStatus)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作" fixed="right" width="190"
              ><template #default="{ record }"
                ><a-space
                  ><a-button type="link" @click="openTrace(record.id)">全链追溯</a-button
                  ><a-button
                    v-if="
                      can('technical:drawing:receive') &&
                      acceptedChangeRfis.some((rfi) =>
                        overview.versions.some(
                          (version) =>
                            version.drawingId === record.id && version.id === rfi.drawingVersionId,
                        ),
                      )
                    "
                    type="link"
                    @click="openDialog('version', record)"
                    >接收改版</a-button
                  ></a-space
                ></template
              ></a-table-column
            >
          </a-table>
          <a-divider orientation="left">版本履历</a-divider>
          <a-table
            :data-source="overview.versions"
            row-key="id"
            size="small"
            :pagination="{ pageSize: 8 }"
            :scroll="{ x: 900 }"
          >
            <a-table-column title="图纸/版本" width="180"
              ><template #default="{ record }"
                >{{ record.drawingCode }} / {{ record.versionNo }}</template
              ></a-table-column
            >
            <a-table-column title="接收时间" data-index="receivedAt" width="180" />
            <a-table-column title="变更摘要" data-index="changeSummary" />
            <a-table-column title="状态" width="130"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作" width="120"
              ><template #default="{ record }"
                ><a-button
                  v-if="record.status === 'RECEIVED' && can('technical:drawing:review')"
                  type="link"
                  @click="openDialog('review', record)"
                  >发起会审</a-button
                ></template
              ></a-table-column
            >
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="schemes" tab="技术方案">
          <a-table :data-source="overview.schemes" row-key="id" :pagination="false">
            <a-table-column title="方案编号" data-index="schemeCode" />
            <a-table-column title="方案名称" data-index="schemeName" />
            <a-table-column title="计划生效" data-index="plannedEffectiveDate" />
            <a-table-column title="状态"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作"
              ><template #default="{ record }"
                ><a-button
                  v-if="
                    ['DRAFT', 'REJECTED'].includes(record.status) && can('technical:scheme:submit')
                  "
                  type="link"
                  @click="transition(() => submitTechnicalScheme(record.id), '方案已进入审批')"
                  >提交审批</a-button
                ></template
              ></a-table-column
            >
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="rfi" tab="会审与 RFI">
          <a-table :data-source="overview.reviews" row-key="id" size="small" :pagination="false">
            <a-table-column title="会审编号" data-index="reviewCode" />
            <a-table-column title="图纸版本"
              ><template #default="{ record }">{{
                versionName(record.drawingVersionId)
              }}</template></a-table-column
            >
            <a-table-column title="结论" data-index="conclusion" />
            <a-table-column title="状态"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作"
              ><template #default="{ record }"
                ><a-space
                  ><a-button
                    v-if="record.status === 'DRAFT' && can('technical:drawing:review')"
                    type="link"
                    @click="transition(() => confirmDrawingReview(record.id), '会审记录已确认')"
                    >确认会审</a-button
                  ><a-button
                    v-if="
                      record.status === 'CONFIRMED' &&
                      record.requiresRfi &&
                      !overview.rfis.some((rfi) => rfi.reviewId === record.id) &&
                      can('technical:rfi:raise')
                    "
                    type="link"
                    @click="openDialog('rfi', record)"
                    >发起RFI</a-button
                  ></a-space
                ></template
              ></a-table-column
            >
          </a-table>
          <a-divider orientation="left">设计澄清台账</a-divider>
          <a-table
            :data-source="overview.rfis"
            row-key="id"
            size="small"
            :pagination="false"
            :scroll="{ x: 900 }"
          >
            <a-table-column title="RFI编号" data-index="rfiCode" width="130" />
            <a-table-column title="主题" data-index="subject" />
            <a-table-column title="优先级" data-index="priority" width="100" />
            <a-table-column title="回复期限" data-index="responseDueDate" width="120" />
            <a-table-column title="状态" width="120"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作" width="220"
              ><template #default="{ record }"
                ><a-space
                  ><a-button
                    v-if="record.status === 'DRAFT' && can('technical:rfi:raise')"
                    type="link"
                    @click="transition(() => submitTechnicalRfi(record.id), 'RFI已正式提交')"
                    >提交</a-button
                  ><a-button
                    v-if="record.status === 'SUBMITTED' && can('technical:rfi:respond')"
                    type="link"
                    @click="openDialog('response', record)"
                    >登记回复</a-button
                  ><a-button
                    v-if="record.status === 'RESPONDED' && can('technical:rfi:accept')"
                    type="link"
                    @click="
                      openDialog(
                        'responseReview',
                        overview.responses.find((item) => item.rfiId === record.id),
                      )
                    "
                    >复核回复</a-button
                  ></a-space
                ></template
              ></a-table-column
            >
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="delivery" tab="交底、施工与归档">
          <div class="toolbar">
            <a-button
              v-if="can('technical:disclosure:maintain')"
              :disabled="approvedVersions.length === 0"
              @click="openDialog('disclosure', approvedVersions[0])"
              >新建技术交底</a-button
            >
          </div>
          <a-table
            :data-source="overview.disclosures"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <a-table-column title="交底编号" data-index="disclosureCode" />
            <a-table-column title="交底主题" data-index="disclosureTitle" />
            <a-table-column title="图纸版本"
              ><template #default="{ record }">{{
                versionName(record.drawingVersionId)
              }}</template></a-table-column
            >
            <a-table-column title="状态"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作"
              ><template #default="{ record }"
                ><a-space
                  ><a-button
                    v-if="record.status === 'DRAFT'"
                    type="link"
                    @click="
                      transition(() => confirmTechnicalDisclosure(record.id), '技术交底已确认')
                    "
                    >确认交底</a-button
                  ><a-button
                    v-if="
                      record.status === 'CONFIRMED' &&
                      !overview.constructionReferences.some(
                        (item) => item.disclosureId === record.id,
                      )
                    "
                    type="link"
                    @click="openDialog('reference', record)"
                    >关联施工</a-button
                  ></a-space
                ></template
              ></a-table-column
            >
          </a-table>
          <a-divider orientation="left">施工引用与验收档案</a-divider>
          <a-table
            :data-source="overview.constructionReferences"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <a-table-column title="施工日期" data-index="referenceDate" />
            <a-table-column title="施工部位" data-index="workArea" />
            <a-table-column title="图纸版本"
              ><template #default="{ record }">{{
                versionName(record.drawingVersionId)
              }}</template></a-table-column
            >
            <a-table-column title="状态"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作"
              ><template #default="{ record }"
                ><a-button
                  v-if="availableReferences.some((item) => item.id === record.id)"
                  type="link"
                  @click="openDialog('archive', record)"
                  >验收归档</a-button
                ></template
              ></a-table-column
            >
          </a-table>
          <a-table
            class="archive-table"
            :data-source="overview.archives"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <a-table-column title="档案编号" data-index="archiveCode" />
            <a-table-column title="验收日期" data-index="acceptanceDate" />
            <a-table-column title="档案位置" data-index="archiveLocation" />
            <a-table-column title="状态"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="操作"
              ><template #default="{ record }"
                ><a-button
                  v-if="record.status === 'DRAFT'"
                  type="link"
                  @click="
                    transition(() => confirmAcceptanceArchive(record.id), '验收资料已正式归档')
                  "
                  >确认归档</a-button
                ></template
              ></a-table-column
            >
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <a-modal
      v-model:open="dialogOpen"
      :title="dialogTitle"
      :confirm-loading="saving"
      width="680px"
      @ok="saveDialog"
    >
      <a-form layout="vertical" class="dialog-form">
        <template v-if="dialogKind === 'scheme'">
          <a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="方案编号" required
                ><a-input v-model:value="form.code" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="方案类型"
                ><a-select
                  v-model:value="form.type"
                  :options="[
                    { value: 'GENERAL', label: '一般方案' },
                    { value: 'SPECIAL', label: '专项方案' },
                    { value: 'CONSTRUCTION_ORGANIZATION', label: '施工组织设计' },
                    { value: 'METHOD_STATEMENT', label: '作业指导书' },
                  ]" /></a-form-item></a-col
          ></a-row>
          <a-form-item label="方案名称" required><a-input v-model:value="form.name" /></a-form-item>
          <a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="责任人ID"
                ><a-input v-model:value="form.responsibleUserId" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="计划生效日期"
                ><a-input v-model:value="form.plannedDate" type="date" /></a-form-item></a-col
          ></a-row>
        </template>
        <template v-else-if="dialogKind === 'drawing'">
          <a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="图纸编号" required
                ><a-input v-model:value="form.code" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="首版版本号" required
                ><a-input v-model:value="form.versionNo" /></a-form-item></a-col
          ></a-row>
          <a-form-item label="图纸名称" required><a-input v-model:value="form.name" /></a-form-item>
          <a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="专业"
                ><a-input v-model:value="form.specialty" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="来源单位"
                ><a-input v-model:value="form.sourceOrganization" /></a-form-item></a-col
          ></a-row>
          <a-form-item label="接收时间"
            ><a-input v-model:value="form.receivedAt" type="datetime-local"
          /></a-form-item>
        </template>
        <template v-else-if="dialogKind === 'version'">
          <a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="新版本号" required
                ><a-input v-model:value="form.versionNo" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="接收时间"
                ><a-input
                  v-model:value="form.receivedAt"
                  type="datetime-local" /></a-form-item></a-col
          ></a-row>
          <a-form-item label="来源RFI"
            ><a-select
              v-model:value="form.sourceRfiId"
              :options="
                acceptedChangeRfis.map((item) => ({
                  value: item.id,
                  label: `${item.rfiCode} · ${item.subject}`,
                }))
              "
              @change="
                (id: string) =>
                  (form.previousVersionId =
                    overview.rfis.find((item) => item.id === id)?.drawingVersionId ?? '')
              "
          /></a-form-item>
          <a-form-item label="变更摘要" required
            ><a-textarea v-model:value="form.changeSummary" :rows="3"
          /></a-form-item>
        </template>
        <template v-else-if="dialogKind === 'review'">
          <a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="会审编号" required
                ><a-input v-model:value="form.code" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="会审日期"
                ><a-input v-model:value="form.reviewDate" type="date" /></a-form-item></a-col
          ></a-row>
          <a-form-item label="参会人员摘要" required
            ><a-input v-model:value="form.participants"
          /></a-form-item>
          <a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="会审结论"
                ><a-select
                  v-model:value="form.conclusion"
                  :options="[
                    { value: 'PASS', label: '通过' },
                    { value: 'CONDITIONAL', label: '条件通过' },
                    { value: 'REJECTED', label: '退回' },
                  ]" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="需要RFI"
                ><a-switch v-model:checked="form.requiresRfi" /></a-form-item></a-col
          ></a-row>
          <a-form-item label="会审结论摘要" required
            ><a-textarea v-model:value="form.summary" :rows="3"
          /></a-form-item>
        </template>
        <template v-else-if="dialogKind === 'rfi'">
          <a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="RFI编号" required
                ><a-input v-model:value="form.code" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="优先级"
                ><a-select
                  v-model:value="form.priority"
                  :options="[
                    { value: 'NORMAL', label: '一般' },
                    { value: 'HIGH', label: '高' },
                    { value: 'URGENT', label: '紧急' },
                  ]" /></a-form-item></a-col
          ></a-row>
          <a-form-item label="主题" required><a-input v-model:value="form.subject" /></a-form-item
          ><a-form-item label="问题描述" required
            ><a-textarea v-model:value="form.question" :rows="4" /></a-form-item
          ><a-form-item label="要求回复日期"
            ><a-input v-model:value="form.dueDate" type="date"
          /></a-form-item>
        </template>
        <template v-else-if="dialogKind === 'response'">
          <a-form-item label="设计回复内容" required
            ><a-textarea v-model:value="form.responseContent" :rows="5" /></a-form-item
          ><a-form-item label="回复人/单位" required
            ><a-input v-model:value="form.responderName" /></a-form-item
          ><a-form-item label="是否要求改图"
            ><a-switch v-model:checked="form.changeRequired"
          /></a-form-item>
        </template>
        <template v-else-if="dialogKind === 'responseReview'">
          <a-form-item label="复核结论"
            ><a-radio-group v-model:value="form.decision"
              ><a-radio value="ACCEPTED">接受</a-radio
              ><a-radio value="REJECTED">退回</a-radio></a-radio-group
            ></a-form-item
          ><a-form-item label="复核意见" required
            ><a-textarea v-model:value="form.comment" :rows="4"
          /></a-form-item>
        </template>
        <template v-else-if="dialogKind === 'disclosure'">
          <a-form-item label="施工图纸版本"
            ><a-select
              v-model:value="form.drawingVersionId"
              :options="
                approvedVersions.map((item) => ({
                  value: item.id,
                  label: `${item.drawingCode}-${item.versionNo}`,
                }))
              " /></a-form-item
          ><a-form-item label="批准技术方案"
            ><a-select
              v-model:value="form.schemeId"
              allow-clear
              :options="
                approvedSchemes.map((item) => ({
                  value: item.id,
                  label: `${item.schemeCode} · ${item.schemeName}`,
                }))
              "
          /></a-form-item>
          <a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="交底编号" required
                ><a-input v-model:value="form.code" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="交底日期"
                ><a-input
                  v-model:value="form.plannedDate"
                  type="date" /></a-form-item></a-col></a-row
          ><a-form-item label="交底主题" required><a-input v-model:value="form.name" /></a-form-item
          ><a-form-item label="接受交底人员/班组" required
            ><a-input v-model:value="form.recipients" /></a-form-item
          ><a-form-item label="交底内容" required
            ><a-textarea v-model:value="form.content" :rows="4"
          /></a-form-item>
        </template>
        <template v-else-if="dialogKind === 'reference'">
          <a-form-item label="已提交日报中的WBS施工事实" required
            ><a-select
              v-model:value="form.factId"
              :options="
                overview.constructionFacts.map((item) => ({
                  value: item.progressId,
                  label: `${item.reportDate} · ${item.taskCode} ${item.taskName} · ${item.currentProgress}%`,
                }))
              " /></a-form-item
          ><a-form-item label="施工部位"><a-input v-model:value="form.workArea" /></a-form-item
          ><a-form-item label="施工引用说明" required
            ><a-textarea v-model:value="form.content" :rows="4"
          /></a-form-item>
        </template>
        <template v-else-if="dialogKind === 'archive'">
          <a-form-item label="已通过质量验收"
            ><a-select
              v-model:value="form.inspectionId"
              :options="
                overview.qualityInspections.map((item) => ({
                  value: item.id,
                  label: `${item.inspectionDate} · ${item.inspectionCode} · ${item.location || ''}`,
                }))
              " /></a-form-item
          ><a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="档案编号" required
                ><a-input v-model:value="form.code" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="验收日期"
                ><a-input
                  v-model:value="form.plannedDate"
                  type="date" /></a-form-item></a-col></a-row
          ><a-form-item label="验收结论"
            ><a-select
              v-model:value="form.conclusion"
              :options="[
                { value: 'PASS', label: '通过' },
                { value: 'CONDITIONAL_PASS', label: '有条件通过' },
              ]" /></a-form-item
          ><a-form-item label="档案位置" required
            ><a-input v-model:value="form.archiveLocation"
          /></a-form-item>
        </template>
        <a-form-item
          v-if="!['responseReview', 'reference'].includes(dialogKind)"
          label="业务附件"
          required
          ><input class="file-input" type="file" @change="setFile" />
          <div class="file-hint">
            附件在当前草稿阶段上传，提交或确认后锁定，禁止替换历史证据。
          </div></a-form-item
        >
        <a-form-item
          v-if="!['response', 'responseReview', 'reference'].includes(dialogKind)"
          label="备注"
          ><a-textarea v-model:value="form.remark" :rows="2"
        /></a-form-item>
      </a-form>
    </a-modal>

    <a-drawer v-model:open="traceOpen" title="图纸全链追溯" width="720">
      <a-descriptions v-if="trace" bordered size="small" :column="2"
        ><a-descriptions-item label="图纸编号">{{
          trace.drawing.drawingCode || trace.drawing.drawing_code
        }}</a-descriptions-item
        ><a-descriptions-item label="图纸名称">{{
          trace.drawing.drawingName || trace.drawing.drawing_name
        }}</a-descriptions-item
        ><a-descriptions-item label="版本数">{{ trace.versions.length }}</a-descriptions-item
        ><a-descriptions-item label="RFI数">{{ trace.rfis.length }}</a-descriptions-item
        ><a-descriptions-item label="批准方案/审批"
          >{{ trace.schemes.length }} / {{ trace.schemeApprovals.length }}</a-descriptions-item
        ><a-descriptions-item label="验收档案">{{
          trace.archives.length
        }}</a-descriptions-item></a-descriptions
      >
      <a-timeline v-if="trace" class="trace-timeline">
        <a-timeline-item v-for="item in trace.versions" :key="`v-${item.id}`" color="blue"
          >接收图纸版本 {{ item.versionNo || item.version_no }} ·
          {{ statusName(item.status) }}</a-timeline-item
        >
        <a-timeline-item v-for="item in trace.rfis" :key="`r-${item.id}`" color="orange"
          >RFI {{ item.rfiCode || item.rfi_code }} · {{ statusName(item.status) }}</a-timeline-item
        >
        <a-timeline-item v-for="item in trace.disclosures" :key="`d-${item.id}`" color="green"
          >技术交底 {{ item.disclosureCode || item.disclosure_code }} ·
          {{ statusName(item.status) }}</a-timeline-item
        >
        <a-timeline-item v-for="item in trace.archives" :key="`a-${item.id}`" color="green"
          >验收归档 {{ item.archiveCode || item.archive_code }} ·
          {{ statusName(item.status) }}</a-timeline-item
        >
      </a-timeline>
    </a-drawer>
  </div>
</template>

<style scoped>
.technical-page {
  padding: 20px;
  background: #f4f7fb;
  min-height: 100%;
}
.hero-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 30px;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(120deg, #102a43, #155e75 60%, #0f766e);
  box-shadow: 0 12px 30px rgb(15 76 92 / 18%);
}
.hero-panel h1 {
  margin: 3px 0 8px;
  color: #fff;
  font-size: 26px;
}
.eyebrow {
  margin: 0;
  color: #99f6e4;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}
.subtitle {
  max-width: 760px;
  margin: 0;
  color: #d9f4f2;
}
.project-select {
  width: 350px;
}
.kpi-row {
  margin-top: 16px;
}
.kpi-row :deep(.ant-card) {
  border: 0;
  border-radius: 12px;
  box-shadow: 0 5px 18px rgb(15 23 42 / 6%);
}
.workbench-card {
  margin-top: 16px;
  border: 0;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 6%);
}
.card-title {
  display: flex;
  align-items: baseline;
  gap: 16px;
}
.chain-note {
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
}
.toolbar {
  margin-bottom: 12px;
}
.archive-table {
  margin-top: 14px;
}
.file-input {
  width: 100%;
  padding: 9px;
  border: 1px dashed #94a3b8;
  border-radius: 8px;
  background: #f8fafc;
}
.file-hint {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}
.trace-timeline {
  margin-top: 28px;
}
@media (max-width: 768px) {
  .technical-page {
    padding: 12px;
  }
  .hero-panel {
    align-items: stretch;
    flex-direction: column;
    padding: 20px;
  }
  .project-select {
    width: 100%;
  }
  .chain-note {
    display: none;
  }
}
</style>
