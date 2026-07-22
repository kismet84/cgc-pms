<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type {
  AcceptanceArchive,
  ConstructionReference,
  DrawingReview,
  DrawingTrace,
  DrawingVersion,
  RfiResponse,
  TechnicalDisclosure,
  TechnicalDrawing,
  TechnicalOverview,
  TechnicalRfi,
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
import { uploadSiteFile } from '@/services/delivery'
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
  loadDrawingTrace,
  loadTechnicalOverview,
  receiveDrawingVersion,
  receiveTechnicalDrawing,
  respondTechnicalRfi,
  reviewTechnicalRfiResponse,
  submitTechnicalRfi,
  submitTechnicalScheme,
} from '@/services/technical'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import { deliveryLabel } from './labels'

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
  | null
type Target =
  | TechnicalDrawing
  | DrawingVersion
  | DrawingReview
  | TechnicalRfi
  | RfiResponse
  | TechnicalDisclosure
  | ConstructionReference
interface PendingEvidence {
  kind: Exclude<DialogKind, null>
  businessType: string
  businessId: string
  documentType: string
}

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
const session = useSessionStore()
const workspace = useWorkspaceStore()
const overview = ref<TechnicalOverview>(emptyOverview())
const trace = ref<DrawingTrace | null>(null)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const dialog = ref<DialogKind>(null)
const target = ref<Target | null>(null)
const evidence = ref<File | null>(null)
const pendingEvidence = ref<PendingEvidence | null>(null)
let projectController: AbortController | null = null
let traceController: AbortController | null = null
let generation = 0

const today = () => new Date().toISOString().slice(0, 10)
const now = () => new Date().toISOString().slice(0, 16)
const projectId = computed(() => workspace.selectedProjectId || '')
const scopeProjectIds = computed(() =>
  projectId.value ? [projectId.value] : workspace.projects.map((project) => project.value),
)
const approvedSchemes = computed(() =>
  overview.value.schemes.filter((item) => item.status === 'APPROVED'),
)
const acceptedChangeRfis = computed(() =>
  overview.value.rfis.filter((item) => item.status === 'CHANGE_PENDING'),
)
const approvedVersions = computed(() =>
  overview.value.versions.filter((item) => item.status === 'APPROVED'),
)
const availableReferences = computed(() =>
  overview.value.constructionReferences.filter(
    (item) =>
      item.status === 'RECORDED' &&
      !overview.value.archives.some((archive) => archive.constructionReferenceId === item.id),
  ),
)

const canWrite = computed(() => Boolean(projectId.value))
const canSchemeMaintain = computed(() => canWrite.value && can('technical:scheme:maintain'))
const canSchemeSubmit = computed(() => canWrite.value && can('technical:scheme:submit'))
const canDrawingReceive = computed(() => canWrite.value && can('technical:drawing:receive'))
const canDrawingReview = computed(() => canWrite.value && can('technical:drawing:review'))
const canRfiRaise = computed(() => canWrite.value && can('technical:rfi:raise'))
const canRfiRespond = computed(() => canWrite.value && can('technical:rfi:respond'))
const canRfiAccept = computed(() => canWrite.value && can('technical:rfi:accept'))
const canDisclosure = computed(() => canWrite.value && can('technical:disclosure:maintain'))
const canArchive = computed(() => canWrite.value && can('technical:archive:confirm'))

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
  referenceId: '',
  inspectionId: '',
  archiveLocation: '',
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
    })[dialog.value ?? 'scheme'],
)

function can(permission: string): boolean {
  return (
    session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
    session.hasPermission(permission)
  )
}
function clearNotice(): void {
  errorMessage.value = ''
  successMessage.value = ''
}
function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}
function tone(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  if (['APPROVED', 'CONFIRMED', 'CLOSED', 'ACCEPTED', 'RECORDED', 'ARCHIVED'].includes(status))
    return 'success'
  if (['REJECTED'].includes(status)) return 'danger'
  if (
    ['PENDING', 'UNDER_REVIEW', 'SUBMITTED', 'RESPONDED', 'RFI_PENDING', 'CHANGE_PENDING'].includes(
      status,
    )
  )
    return 'warning'
  return 'neutral'
}
function versionLabel(id?: string | null): string {
  const item = overview.value.versions.find((row) => row.id === id)
  return item ? `${item.drawingCode ?? '图纸'}-${item.versionNo}` : (id ?? '-')
}

async function loadProject(preserveNotice = false): Promise<void> {
  projectController?.abort()
  traceController?.abort()
  const requestGeneration = ++generation
  overview.value = emptyOverview()
  trace.value = null
  if (!scopeProjectIds.value.length) return
  const controller = new AbortController()
  projectController = controller
  loading.value = true
  if (!preserveNotice) clearNotice()
  try {
    // ponytail: fan-out stays simple; add a server aggregate endpoint only if project counts make it slow.
    const loaded = await Promise.all(
      scopeProjectIds.value.map((id) => loadTechnicalOverview(id, controller.signal)),
    )
    if (requestGeneration === generation) {
      overview.value = {
        schemes: loaded.flatMap((item) => item.schemes),
        drawings: loaded.flatMap((item) => item.drawings),
        versions: loaded.flatMap((item) => item.versions),
        reviews: loaded.flatMap((item) => item.reviews),
        rfis: loaded.flatMap((item) => item.rfis),
        responses: loaded.flatMap((item) => item.responses),
        disclosures: loaded.flatMap((item) => item.disclosures),
        constructionReferences: loaded.flatMap((item) => item.constructionReferences),
        archives: loaded.flatMap((item) => item.archives),
        constructionFacts: loaded.flatMap((item) => item.constructionFacts),
        qualityInspections: loaded.flatMap((item) => item.qualityInspections),
      }
    }
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '技术管理事实加载失败')
  } finally {
    if (requestGeneration === generation) loading.value = false
  }
}
async function openTrace(drawing: TechnicalDrawing, preserveNotice = false): Promise<void> {
  traceController?.abort()
  const controller = new AbortController()
  traceController = controller
  if (!preserveNotice) clearNotice()
  try {
    const loaded = await loadDrawingTrace(drawing.id, controller.signal)
    if (!controller.signal.aborted) trace.value = loaded
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '图纸闭环追溯加载失败')
  }
}

function show(kind: Exclude<DialogKind, null>, row?: Target): void {
  if (pendingEvidence.value) {
    dialog.value = pendingEvidence.value.kind
    errorMessage.value = '业务对象已创建，仅需重试阶段证据上传；不会重复创建业务对象'
    return
  }
  clearNotice()
  dialog.value = kind
  target.value = row ?? null
  evidence.value = null
  Object.assign(form, {
    code: '',
    name: '',
    type: 'SPECIAL',
    responsibleUserId: session.userInfo?.userId ?? '',
    plannedDate: today(),
    specialty: '',
    sourceOrganization: '',
    versionNo: '',
    receivedAt: now(),
    previousVersionId: '',
    sourceRfiId: '',
    changeSummary: '',
    chairUserId: session.userInfo?.userId ?? '',
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
    presenterUserId: session.userInfo?.userId ?? '',
    recipients: '',
    content: '',
    disclosureId: '',
    factId: '',
    workArea: '',
    referenceId: '',
    inspectionId: '',
    archiveLocation: '',
    remark: '',
  })
  if (kind === 'version' && row) {
    const drawing = row as TechnicalDrawing
    const rfi = acceptedChangeRfis.value.find((item) =>
      overview.value.versions.some(
        (version) => version.drawingId === drawing.id && version.id === item.drawingVersionId,
      ),
    )
    form.previousVersionId = rfi?.drawingVersionId ?? ''
    form.sourceRfiId = rfi?.id ?? ''
  }
  if (kind === 'disclosure' && row) form.drawingVersionId = row.id
  if (kind === 'reference' && row) form.disclosureId = row.id
  if (kind === 'archive' && row) form.referenceId = row.id
}
function chooseEvidence(event: Event): void {
  evidence.value = (event.target as HTMLInputElement).files?.[0] ?? null
}
async function attachEvidence(
  kind: Exclude<DialogKind, null>,
  documentType: string,
  businessType: string,
  businessId: string,
): Promise<void> {
  pendingEvidence.value = { kind, documentType, businessType, businessId }
  await uploadPendingEvidence()
}
async function uploadPendingEvidence(): Promise<void> {
  if (!evidence.value) throw new Error('请上传阶段证据')
  const pending = pendingEvidence.value
  if (!pending) return
  await uploadSiteFile(
    evidence.value,
    pending.businessType,
    pending.businessId,
    pending.documentType,
  )
  pendingEvidence.value = null
}

async function save(): Promise<void> {
  if (!projectId.value || !dialog.value) return
  const kind = dialog.value
  saving.value = true
  clearNotice()
  try {
    if (pendingEvidence.value) {
      await uploadPendingEvidence()
    } else if (kind === 'scheme') {
      const created = await createTechnicalScheme({
        projectId: projectId.value,
        schemeCode: form.code,
        schemeName: form.name,
        schemeType: form.type as 'SPECIAL',
        responsibleUserId: form.responsibleUserId,
        plannedEffectiveDate: form.plannedDate,
        remark: form.remark,
      })
      await attachEvidence(kind, 'SCHEME_FILE', 'TECH_SCHEME', created.id)
    } else if (kind === 'drawing') {
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
      await attachEvidence(kind, 'DRAWING_FILE', 'TECH_DRAWING_VERSION', created.versions[0]!.id)
    } else if (kind === 'version' && target.value) {
      const created = await receiveDrawingVersion(target.value.id, {
        versionNo: form.versionNo,
        previousVersionId: form.previousVersionId,
        sourceRfiId: form.sourceRfiId,
        receivedAt: form.receivedAt,
        changeSummary: form.changeSummary,
        remark: form.remark,
      })
      await attachEvidence(kind, 'DRAWING_FILE', 'TECH_DRAWING_VERSION', created.id)
    } else if (kind === 'review' && target.value) {
      const created = await createDrawingReview(target.value.id, {
        reviewCode: form.code,
        reviewDate: form.plannedDate,
        chairUserId: form.chairUserId,
        participantSummary: form.participants,
        conclusion: form.conclusion as DrawingReview['conclusion'],
        reviewSummary: form.summary,
        requiresRfi: form.requiresRfi,
        remark: form.remark,
      })
      await attachEvidence(kind, 'REVIEW_MINUTES', 'TECH_DRAWING_REVIEW', created.id)
    } else if (kind === 'rfi' && target.value) {
      const created = await createTechnicalRfi(target.value.id, {
        rfiCode: form.code,
        subject: form.subject,
        question: form.question,
        priority: form.priority as TechnicalRfi['priority'],
        responseDueDate: form.dueDate,
        remark: form.remark,
      })
      await attachEvidence(kind, 'RFI_EVIDENCE', 'TECH_RFI', created.id)
    } else if (kind === 'response' && target.value) {
      const created = await respondTechnicalRfi(target.value.id, {
        responseContent: form.responseContent,
        changeRequired: form.changeRequired,
        responderName: form.responderName,
      })
      await attachEvidence(kind, 'DESIGN_RESPONSE', 'TECH_RFI_RESPONSE', created.id)
    } else if (kind === 'responseReview' && target.value) {
      await reviewTechnicalRfiResponse(target.value.id, {
        decision: form.decision as 'ACCEPTED' | 'REJECTED',
        reviewComment: form.comment,
      })
    } else if (kind === 'disclosure') {
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
      await attachEvidence(kind, 'DISCLOSURE_RECORD', 'TECH_DISCLOSURE', created.id)
    } else if (kind === 'reference') {
      const fact = overview.value.constructionFacts.find((item) => item.progressId === form.factId)
      if (!fact) throw new Error('请选择已提交日报中的施工事实')
      await createConstructionReference(projectId.value, {
        disclosureId: form.disclosureId,
        dailyLogId: fact.dailyLogId,
        wbsTaskId: fact.wbsTaskId,
        referenceDate: fact.reportDate,
        workArea: form.workArea || fact.workArea || fact.taskName,
        referenceDescription: form.content,
        remark: form.remark,
      })
    } else if (kind === 'archive') {
      const created = await createAcceptanceArchive(projectId.value, {
        constructionReferenceId: form.referenceId,
        qualityInspectionId: form.inspectionId,
        archiveCode: form.code,
        acceptanceDate: form.plannedDate,
        acceptanceConclusion: form.conclusion as AcceptanceArchive['acceptanceConclusion'],
        archiveLocation: form.archiveLocation,
        remark: form.remark,
      })
      await attachEvidence(kind, 'ACCEPTANCE_ARCHIVE', 'TECH_ARCHIVE', created.id)
    }
    successMessage.value = '业务步骤已完成。'
    dialog.value = null
  } catch (error) {
    errorMessage.value = pendingEvidence.value
      ? `业务对象已创建，仅附件上传失败；请直接重试附件，不会重复创建。${errorText(error, '')}`
      : error instanceof Error && error.message !== 'Network request failed'
        ? error.message
        : errorText(error, '技术业务提交失败；已保留当前事实，可重试')
  } finally {
    await loadProject(true)
    saving.value = false
  }
}
async function act(action: () => Promise<unknown>, message: string): Promise<void> {
  saving.value = true
  clearNotice()
  try {
    await action()
    successMessage.value = message
    await loadProject(true)
  } catch (error) {
    errorMessage.value = errorText(error, '操作失败；已保留当前事实，可重试')
    await loadProject(true)
  } finally {
    saving.value = false
  }
}

watch(scopeProjectIds, () => void loadProject(), { immediate: true })
onBeforeUnmount(() => {
  projectController?.abort()
  traceController?.abort()
})
</script>

<template>
  <section class="technical-page" aria-label="图纸 RFI 技术闭环">
    <div class="technical-page__toolbar">
      <div class="technical-page__actions">
        <V2Button v-if="canSchemeMaintain" size="small" @click="show('scheme')">新建方案</V2Button>
        <V2Button v-if="canDrawingReceive" size="small" variant="secondary" @click="show('drawing')"
          >接收图纸</V2Button
        >
      </div>
    </div>
    <div class="technical-page__notice" aria-live="polite">
      <V2Alert
        v-if="errorMessage"
        tone="danger"
        title="操作未完成"
        dismissible
        @dismiss="errorMessage = ''"
        >{{ errorMessage }}</V2Alert
      >
      <V2Alert
        v-if="successMessage"
        tone="success"
        title="操作完成"
        dismissible
        @dismiss="successMessage = ''"
        >{{ successMessage }}</V2Alert
      >
    </div>
    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载技术事实"
      description="正在加载方案、图纸、RFI、交底和归档状态。"
    />
    <V2PageState
      v-else-if="!scopeProjectIds.length"
      kind="empty"
      title="暂无可访问项目"
      description="当前账号没有可查看的项目。"
    />
    <template v-else>
      <div class="technical-page__facts" aria-label="技术闭环概览">
        <V2Badge>方案 {{ overview.schemes.length }}</V2Badge
        ><V2Badge>图纸 {{ overview.drawings.length }}</V2Badge
        ><V2Badge tone="warning"
          >开放 RFI
          {{
            overview.rfis.filter((item) => !['CLOSED', 'CANCELLED'].includes(item.status)).length
          }}</V2Badge
        ><V2Badge tone="success"
          >归档 {{ overview.archives.filter((item) => item.status === 'ARCHIVED').length }}</V2Badge
        >
      </div>
      <div class="technical-page__columns">
        <V2Card title="技术方案" subtitle="方案维护与提交权限分离">
          <div class="technical-page__stack">
            <article v-for="item in overview.schemes" :key="item.id" class="technical-page__item">
              <strong>{{ item.schemeCode }} · {{ item.schemeName }}</strong
              ><V2Badge :tone="tone(item.status)">{{ deliveryLabel(item.status) }}</V2Badge
              ><V2Button
                v-if="canSchemeSubmit && item.status === 'DRAFT'"
                size="small"
                @click="act(() => submitTechnicalScheme(item.id), '方案已提交审批')"
                >提交方案</V2Button
              >
            </article>
            <p v-if="!overview.schemes.length">暂无技术方案。</p>
          </div>
        </V2Card>
        <V2Card title="图纸与版本" subtitle="变更版必须关联已接受且要求改版的 RFI">
          <div class="technical-page__stack">
            <article
              v-for="drawing in overview.drawings"
              :key="drawing.id"
              class="technical-page__item"
            >
              <button class="technical-page__title" type="button" @click="openTrace(drawing)">
                {{ drawing.drawingCode }} · {{ drawing.drawingName }}</button
              ><span
                >{{ drawing.currentVersionNo }} /
                {{ deliveryLabel(drawing.currentVersionStatus) }}</span
              >
              <div class="technical-page__actions">
                <V2Button size="small" variant="secondary" @click="openTrace(drawing)"
                  >追溯</V2Button
                ><V2Button
                  v-if="canDrawingReceive && acceptedChangeRfis.length"
                  size="small"
                  @click="show('version', drawing)"
                  >接收变更版</V2Button
                >
              </div>
            </article>
            <p v-if="!overview.drawings.length">暂无图纸。</p>
          </div>
        </V2Card>
      </div>
      <V2Card title="版本、会审与 RFI" subtitle="可用操作随业务阶段变化">
        <div class="technical-page__grid">
          <article
            v-for="version in overview.versions"
            :key="version.id"
            class="technical-page__item"
          >
            <strong>{{ versionLabel(version.id) }}</strong
            ><V2Badge :tone="tone(version.status)">{{ deliveryLabel(version.status) }}</V2Badge
            ><V2Button
              v-if="canDrawingReview && version.status === 'RECEIVED'"
              size="small"
              @click="show('review', version)"
              >登记会审</V2Button
            >
          </article>
          <article v-for="review in overview.reviews" :key="review.id" class="technical-page__item">
            <strong>{{ review.reviewCode }} · {{ deliveryLabel(review.conclusion) }}</strong
            ><V2Badge :tone="tone(review.status)">{{ deliveryLabel(review.status) }}</V2Badge
            ><V2Button
              v-if="canDrawingReview && review.status === 'DRAFT'"
              size="small"
              @click="act(() => confirmDrawingReview(review.id), '会审已确认')"
              >确认会审</V2Button
            ><V2Button
              v-if="canRfiRaise && review.status === 'CONFIRMED' && Boolean(review.requiresRfi)"
              size="small"
              @click="show('rfi', review)"
              >发起 RFI</V2Button
            >
          </article>
          <article v-for="rfi in overview.rfis" :key="rfi.id" class="technical-page__item">
            <strong>{{ rfi.rfiCode }} · {{ rfi.subject }}</strong
            ><V2Badge :tone="tone(rfi.status)">{{ deliveryLabel(rfi.status) }}</V2Badge
            ><V2Button
              v-if="canRfiRaise && rfi.status === 'DRAFT'"
              size="small"
              @click="act(() => submitTechnicalRfi(rfi.id), 'RFI 已提交')"
              >提交 RFI</V2Button
            ><V2Button
              v-if="canRfiRespond && rfi.status === 'SUBMITTED'"
              size="small"
              @click="show('response', rfi)"
              >设计回复</V2Button
            >
          </article>
          <article
            v-for="response in overview.responses"
            :key="response.id"
            class="technical-page__item"
          >
            <strong
              >{{ response.responderName }} ·
              {{ response.changeRequired ? '要求改版' : '无需改版' }}</strong
            ><V2Badge :tone="tone(response.reviewStatus ?? response.status)">{{
              deliveryLabel(response.reviewStatus ?? response.status)
            }}</V2Badge
            ><V2Button
              v-if="canRfiAccept && (response.reviewStatus ?? response.status) === 'SUBMITTED'"
              size="small"
              @click="show('responseReview', response)"
              >接受/驳回</V2Button
            >
          </article>
        </div>
      </V2Card>
      <V2Card title="交底、施工依据与验收归档" subtitle="归档只接受已提交且通过的质量检查事实">
        <div class="technical-page__actions">
          <V2Button
            v-if="canDisclosure && approvedVersions.length"
            size="small"
            @click="show('disclosure', approvedVersions[0])"
            >登记交底</V2Button
          >
        </div>
        <div class="technical-page__grid">
          <article v-for="item in overview.disclosures" :key="item.id" class="technical-page__item">
            <strong>{{ item.disclosureCode }} · {{ item.disclosureTitle }}</strong
            ><V2Badge :tone="tone(item.status)">{{ deliveryLabel(item.status) }}</V2Badge
            ><V2Button
              v-if="canDisclosure && item.status === 'DRAFT'"
              size="small"
              @click="act(() => confirmTechnicalDisclosure(item.id), '技术交底已确认')"
              >确认交底</V2Button
            ><V2Button
              v-if="canDisclosure && item.status === 'CONFIRMED'"
              size="small"
              @click="show('reference', item)"
              >登记施工依据</V2Button
            >
          </article>
          <article v-for="item in availableReferences" :key="item.id" class="technical-page__item">
            <strong>{{ item.workArea }} · {{ item.referenceDate }}</strong
            ><V2Badge :tone="tone(item.status)">{{ deliveryLabel(item.status) }}</V2Badge
            ><V2Button
              v-if="canArchive && overview.qualityInspections.length"
              size="small"
              @click="show('archive', item)"
              >登记验收归档</V2Button
            >
          </article>
          <article v-for="item in overview.archives" :key="item.id" class="technical-page__item">
            <strong>{{ item.archiveCode }} · {{ item.archiveLocation }}</strong
            ><V2Badge :tone="tone(item.status)">{{ deliveryLabel(item.status) }}</V2Badge
            ><V2Button
              v-if="canArchive && item.status === 'DRAFT'"
              size="small"
              @click="act(() => confirmAcceptanceArchive(item.id), '验收档案已确认')"
              >确认归档</V2Button
            >
          </article>
        </div>
      </V2Card>
      <V2Card
        v-if="trace"
        title="图纸闭环追溯"
        :subtitle="`${trace.drawing.drawingCode} · 版本、会审、RFI、交底与归档记录`"
        ><ol class="technical-page__timeline">
          <li v-for="item in trace.versions" :key="`v-${item.id}`">
            <strong>版本 {{ item.versionNo }}</strong
            ><span>{{ deliveryLabel(item.status) }}</span>
          </li>
          <li v-for="item in trace.reviews" :key="`review-${item.id}`">
            <strong>会审 {{ item.reviewCode }}</strong
            ><span>{{ deliveryLabel(item.status) }}</span>
          </li>
          <li v-for="item in trace.rfis" :key="`rfi-${item.id}`">
            <strong>RFI {{ item.rfiCode }}</strong
            ><span>{{ deliveryLabel(item.status) }}</span>
          </li>
          <li v-for="item in trace.disclosures" :key="`d-${item.id}`">
            <strong>交底 {{ item.disclosureCode }}</strong
            ><span>{{ deliveryLabel(item.status) }}</span>
          </li>
          <li v-for="item in trace.archives" :key="`a-${item.id}`">
            <strong>档案 {{ item.archiveCode }}</strong
            ><span>{{ deliveryLabel(item.status) }}</span>
          </li>
        </ol></V2Card
      >
    </template>

    <V2Dialog
      :open="Boolean(dialog)"
      :title="dialogTitle"
      :close-disabled="saving || Boolean(pendingEvidence)"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
      @update:open="
        (open) => {
          if (!open) dialog = null
        }
      "
    >
      <form id="technical-dialog-form" class="technical-page__form" @submit.prevent="save">
        <template v-if="dialog === 'scheme'"
          ><V2Input v-model="form.code" label="方案编码" required /><V2Input
            v-model="form.name"
            label="方案名称"
            required /><V2Select
            v-model="form.type"
            label="方案类型"
            :options="
              ['GENERAL', 'SPECIAL', 'CONSTRUCTION_ORGANIZATION', 'METHOD_STATEMENT'].map(
                (value) => ({ value, label: deliveryLabel(value) }),
              )
            " /><V2Input v-model="form.responsibleUserId" label="负责人 ID" required
        /></template>
        <template v-if="dialog === 'drawing'"
          ><V2Input v-model="form.code" label="图纸编码" required /><V2Input
            v-model="form.name"
            label="图纸名称"
            required /><V2Input v-model="form.specialty" label="专业" required /><V2Input
            v-model="form.sourceOrganization"
            label="来源单位"
            required /><V2Input v-model="form.versionNo" label="版本号" required
        /></template>
        <template v-if="dialog === 'version'"
          ><V2Input v-model="form.versionNo" label="新版本号" required /><V2Select
            v-model="form.sourceRfiId"
            label="来源 RFI"
            :options="
              acceptedChangeRfis.map((item) => ({
                value: item.id,
                label: `${item.rfiCode} · ${item.subject}`,
              }))
            " /><V2Input v-model="form.previousVersionId" label="前版 ID" required /><label
            class="technical-page__wide"
            >变更摘要<textarea v-model="form.changeSummary" required /></label
        ></template>
        <template v-if="dialog === 'review'"
          ><V2Input v-model="form.code" label="会审编码" required /><V2Input
            v-model="form.chairUserId"
            label="主持人 ID"
            required /><V2Input v-model="form.participants" label="参与人" required /><V2Select
            v-model="form.conclusion"
            label="会审结论"
            :options="
              ['PASS', 'CONDITIONAL', 'REJECTED'].map((value) => ({
                value,
                label: deliveryLabel(value),
              }))
            " /><label><input v-model="form.requiresRfi" type="checkbox" /> 需要 RFI</label
          ><label class="technical-page__wide"
            >会审摘要<textarea v-model="form.summary" required /></label
        ></template>
        <template v-if="dialog === 'rfi'"
          ><V2Input v-model="form.code" label="RFI 编码" required /><V2Input
            v-model="form.subject"
            label="主题"
            required /><V2Select
            v-model="form.priority"
            label="优先级"
            :options="
              ['NORMAL', 'HIGH', 'URGENT'].map((value) => ({ value, label: deliveryLabel(value) }))
            " /><label>回复期限<input v-model="form.dueDate" type="date" required /></label
          ><label class="technical-page__wide"
            >问题<textarea v-model="form.question" required /></label
        ></template>
        <template v-if="dialog === 'response'"
          ><V2Input v-model="form.responderName" label="回复人" required /><label
            ><input v-model="form.changeRequired" type="checkbox" /> 需要图纸改版</label
          ><label class="technical-page__wide"
            >设计回复<textarea v-model="form.responseContent" required /></label
        ></template>
        <template v-if="dialog === 'responseReview'"
          ><V2Select
            v-model="form.decision"
            label="复核结论"
            :options="[
              { value: 'ACCEPTED', label: '接受' },
              { value: 'REJECTED', label: '驳回' },
            ]" /><label class="technical-page__wide"
            >复核意见<textarea v-model="form.comment" required /></label
        ></template>
        <template v-if="dialog === 'disclosure'"
          ><V2Select
            v-model="form.drawingVersionId"
            label="批准图纸版本"
            :options="
              approvedVersions.map((item) => ({ value: item.id, label: versionLabel(item.id) }))
            " /><V2Select
            v-model="form.schemeId"
            label="批准方案"
            :options="
              approvedSchemes.map((item) => ({ value: item.id, label: item.schemeName }))
            " /><V2Input v-model="form.code" label="交底编码" required /><V2Input
            v-model="form.name"
            label="交底标题"
            required /><V2Input v-model="form.presenterUserId" label="交底人 ID" required /><V2Input
            v-model="form.recipients"
            label="接受人"
            required /><label class="technical-page__wide"
            >交底内容<textarea v-model="form.content" required /></label
        ></template>
        <template v-if="dialog === 'reference'"
          ><V2Select
            v-model="form.factId"
            label="已提交日报施工事实"
            :options="
              overview.constructionFacts.map((item) => ({
                value: item.progressId,
                label: `${item.reportDate} · ${item.taskCode} ${item.taskName}`,
              }))
            " /><V2Input v-model="form.workArea" label="施工区域" /><label
            class="technical-page__wide"
            >引用说明<textarea v-model="form.content" required /></label
        ></template>
        <template v-if="dialog === 'archive'"
          ><V2Select
            v-model="form.inspectionId"
            label="通过的质量检查"
            :options="
              overview.qualityInspections.map((item) => ({
                value: item.id,
                label: `${item.inspectionCode} · ${item.inspectionDate}`,
              }))
            " /><V2Input v-model="form.code" label="档案编码" required /><V2Select
            v-model="form.conclusion"
            label="验收结论"
            :options="[
              { value: 'PASS', label: '通过' },
              { value: 'CONDITIONAL_PASS', label: '有条件通过' },
            ]" /><V2Input v-model="form.archiveLocation" label="归档位置" required
        /></template>
        <label
          v-if="
            dialog === 'scheme' ||
            dialog === 'drawing' ||
            dialog === 'version' ||
            dialog === 'review' ||
            dialog === 'rfi' ||
            dialog === 'response' ||
            dialog === 'disclosure' ||
            dialog === 'archive'
          "
          class="technical-page__wide"
          >阶段证据<input type="file" required @change="chooseEvidence"
        /></label>
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="dialog = null">取消</V2Button>
        <V2Button type="submit" form="technical-dialog-form" :loading="saving">{{
          pendingEvidence ? '重试附件上传' : '确认提交'
        }}</V2Button>
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped>
.technical-page {
  display: grid;
  gap: var(--v2-space-3);
  color: var(--v2-color-text);
}
.technical-page h1,
.technical-page p {
  margin-block: 0;
}
.technical-page__toolbar,
.technical-page__facts,
.technical-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}
.technical-page__toolbar {
  justify-content: space-between;
}
.technical-page__notice:empty {
  display: none;
}
.technical-page__columns,
.technical-page__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.technical-page__grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: var(--v2-space-3);
}
.technical-page__stack,
.technical-page__item {
  display: grid;
  gap: var(--v2-space-2);
}
.technical-page__item {
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.technical-page__title {
  padding: 0;
  color: var(--v2-color-primary-hover);
  background: none;
  border: 0;
  font: inherit;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}
.technical-page__timeline {
  display: grid;
  gap: var(--v2-space-2);
  padding-left: 1.25rem;
}
.technical-page__timeline span {
  display: block;
  color: var(--v2-color-text-secondary);
}
.technical-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.technical-page__form label {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.technical-page__form input,
.technical-page__form textarea {
  min-height: 2.5rem;
  padding: var(--v2-space-2);
  color: var(--v2-color-text);
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
  border-radius: var(--v2-radius-md);
  font: inherit;
}
.technical-page__form :deep(.v2-field__control) {
  background: transparent;
  border-color: color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
}
.technical-page__form textarea {
  min-height: 6rem;
  resize: vertical;
}
.technical-page__wide {
  grid-column: 1 / -1;
}
@media (max-width: 64rem) {
  .technical-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 40rem) {
  .technical-page__columns,
  .technical-page__grid,
  .technical-page__form {
    grid-template-columns: 1fr;
  }
  .technical-page__wide {
    grid-column: auto;
  }
}
</style>
