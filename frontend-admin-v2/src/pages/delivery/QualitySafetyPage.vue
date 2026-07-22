<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type {
  QualityConsequenceCommand,
  QualityInspectionCommand,
  QualityInspectionRecord,
  QualityIssueCommand,
  QualityIssueRecord,
  QualityPlanCommand,
  QualityPlanRecord,
  QualityRectificationCommand,
  QualityRectificationRecord,
  QualityReinspectionCommand,
  QualityTraceRecord,
  SiteFileRecord,
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
import { listSiteFiles, uploadSiteFile } from '@/services/delivery'
import {
  activateQualityPlan,
  completeQualityPlan,
  createQualityConsequence,
  createQualityInspection,
  createQualityIssue,
  createQualityPlan,
  createQualityRectification,
  loadQualityInspections,
  loadQualityIssues,
  loadQualityPlans,
  loadQualityTrace,
  postQualityConsequence,
  reinspectQualityRectification,
  submitQualityInspection,
  submitQualityRectification,
} from '@/services/quality'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import { deliveryLabel } from './labels'

type DialogKind =
  | 'plan'
  | 'inspection'
  | 'issue'
  | 'rectification'
  | 'reinspection'
  | 'consequence'
  | 'evidence'
  | null
interface EvidenceTarget {
  businessType: 'QS_INSPECTION' | 'QS_ISSUE' | 'QS_RECTIFICATION'
  businessId: string
  documentType: 'INSPECTION_EVIDENCE' | 'ISSUE_EVIDENCE' | 'RECTIFICATION_EVIDENCE'
  label: string
  issue?: QualityIssueRecord
}
const session = useSessionStore()
const workspace = useWorkspaceStore()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const plans = ref<QualityPlanRecord[]>([])
const inspections = ref<QualityInspectionRecord[]>([])
const issues = ref<QualityIssueRecord[]>([])
const selectedPlanId = ref('')
const activeInspection = ref<QualityInspectionRecord | null>(null)
const activeIssue = ref<QualityIssueRecord | null>(null)
const activeRectification = ref<QualityRectificationRecord | null>(null)
const trace = ref<QualityTraceRecord | null>(null)
const traceFiles = ref<Array<{ stage: string; files: SiteFileRecord[] }>>([])
const dialog = ref<DialogKind>(null)
const evidence = ref<File | null>(null)
const evidenceTarget = ref<EvidenceTarget | null>(null)
let projectController: AbortController | null = null
let inspectionController: AbortController | null = null
let traceController: AbortController | null = null
let generation = 0

const today = () => new Date().toISOString().slice(0, 10)
const projectId = computed(() => workspace.selectedProjectId || '')
const scopeProjectIds = computed(() =>
  projectId.value ? [projectId.value] : workspace.projects.map((project) => project.value),
)
const selectedPlan = computed(
  () => plans.value.find((item) => item.id === selectedPlanId.value) ?? null,
)
const canPlan = computed(() => Boolean(projectId.value) && can('quality:safety:plan:maintain'))
const canInspect = computed(
  () => Boolean(projectId.value) && can('quality:safety:inspection:maintain'),
)
const canRectify = computed(() => Boolean(projectId.value) && can('quality:safety:rectify'))
const canReinspect = computed(() => Boolean(projectId.value) && can('quality:safety:reinspect'))
const canConsequence = computed(() => Boolean(projectId.value) && can('quality:safety:consequence'))

const planForm = reactive<QualityPlanCommand>({
  projectId: '',
  planCode: '',
  planName: '',
  inspectionType: 'QUALITY',
  frequencyType: 'SINGLE',
  startDate: today(),
  endDate: today(),
  ownerUserId: '',
  remark: '',
})
const inspectionForm = reactive<QualityInspectionCommand>({
  planId: '',
  inspectionCode: '',
  inspectionDate: today(),
  location: '',
  inspectorUserId: '',
  summary: '',
  remark: '',
})
const issueForm = reactive<QualityIssueCommand>({
  inspectionId: '',
  category: '',
  severity: 'MEDIUM',
  title: '',
  description: '',
  responsibleKind: 'INTERNAL',
  responsiblePartnerId: '',
  responsibleUserId: '',
  dueDate: today(),
  remark: '',
})
const rectificationForm = reactive<QualityRectificationCommand>({
  issueId: '',
  actionDescription: '',
  responsibleUserId: '',
  plannedCompleteDate: today(),
  remark: '',
})
const reinspectionForm = reactive<QualityReinspectionCommand>({ result: 'PASS', comment: '' })
const consequenceForm = reactive<QualityConsequenceCommand>({
  issueId: '',
  partnerId: '',
  contractId: '',
  consequenceCode: '',
  decisionType: 'NONE',
  fineAmount: '0',
  reworkCostAmount: '0',
  evaluationScore: '80',
  evaluationComment: '',
  remark: '',
})

function can(permission: string): boolean {
  return (
    session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
    session.hasPermission(permission)
  )
}
function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}
function clearNotice(): void {
  errorMessage.value = ''
  successMessage.value = ''
}
function statusTone(status: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  if (['ACTIVE', 'COMPLETED', 'CLOSED', 'PASSED', 'POSTED'].includes(status)) return 'success'
  if (['RECTIFYING', 'PENDING_REINSPECTION', 'SUBMITTED'].includes(status)) return 'warning'
  if (['REJECTED', 'CRITICAL'].includes(status)) return 'danger'
  return 'neutral'
}

async function loadProject(preserveNotice = false): Promise<void> {
  projectController?.abort()
  inspectionController?.abort()
  traceController?.abort()
  const requestGeneration = ++generation
  plans.value = []
  inspections.value = []
  issues.value = []
  selectedPlanId.value = ''
  trace.value = null
  traceFiles.value = []
  if (!scopeProjectIds.value.length) return
  const controller = new AbortController()
  projectController = controller
  loading.value = true
  if (!preserveNotice) clearNotice()
  try {
    // ponytail: fan-out stays simple; add a server aggregate endpoint only if project counts make it slow.
    const loaded = await Promise.all(
      scopeProjectIds.value.map(async (id) =>
        Promise.all([
          loadQualityPlans(id, controller.signal),
          loadQualityIssues(id, undefined, controller.signal),
        ]),
      ),
    )
    if (requestGeneration !== generation) return
    plans.value = loaded.flatMap(([projectPlans]) => projectPlans)
    issues.value = loaded.flatMap(([, projectIssues]) => projectIssues)
    selectedPlanId.value = plans.value[0]?.id ?? ''
    await loadInspections()
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '质量安全事实加载失败')
  } finally {
    if (requestGeneration === generation) loading.value = false
  }
}

async function loadInspections(): Promise<void> {
  inspectionController?.abort()
  inspections.value = []
  if (!selectedPlanId.value) return
  const controller = new AbortController()
  inspectionController = controller
  try {
    inspections.value = await loadQualityInspections(selectedPlanId.value, controller.signal)
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '检查记录加载失败')
  }
}

async function openTrace(issue: QualityIssueRecord, preserveNotice = false): Promise<void> {
  traceController?.abort()
  const controller = new AbortController()
  traceController = controller
  activeIssue.value = issue
  if (!preserveNotice) clearNotice()
  try {
    const current = await loadQualityTrace(issue.id, controller.signal)
    const targets = [
      ['检查证据', 'QS_INSPECTION', current.inspection.id],
      ['问题证据', 'QS_ISSUE', current.issue.id],
      ...current.rectifications.flatMap(
        (item) =>
          [[`整改第 ${item.roundNo} 轮`, 'QS_RECTIFICATION', item.id]] as Array<
            [string, string, string]
          >,
      ),
    ] as Array<[string, string, string]>
    const files = await Promise.all(
      targets.map(async ([stage, type, id]) => ({
        stage,
        files: await listSiteFiles(type, id, controller.signal),
      })),
    )
    if (!controller.signal.aborted) {
      trace.value = current
      traceFiles.value = files
    }
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '整改追溯加载失败')
  }
}

function show(
  kind: Exclude<DialogKind, null>,
  target?: QualityInspectionRecord | QualityIssueRecord,
): void {
  clearNotice()
  evidence.value = null
  dialog.value = kind
  if (kind === 'plan')
    Object.assign(planForm, {
      projectId: projectId.value,
      planCode: `QS-${today().replaceAll('-', '')}`,
      planName: '质量安全检查计划',
      inspectionType: 'QUALITY',
      frequencyType: 'SINGLE',
      startDate: today(),
      endDate: today(),
      ownerUserId: session.userInfo?.userId ?? '',
      remark: '',
    })
  if (kind === 'inspection')
    Object.assign(inspectionForm, {
      planId: selectedPlanId.value,
      inspectionCode: `CHK-${today().replaceAll('-', '')}`,
      inspectionDate: today(),
      location: '',
      inspectorUserId: session.userInfo?.userId ?? '',
      summary: '',
      remark: '',
    })
  if (kind === 'issue' && target) {
    activeInspection.value = target as QualityInspectionRecord
    Object.assign(issueForm, {
      inspectionId: target.id,
      category: '',
      severity: 'MEDIUM',
      title: '',
      description: '',
      responsibleKind: 'INTERNAL',
      responsiblePartnerId: '',
      responsibleUserId: session.userInfo?.userId ?? '',
      dueDate: today(),
      remark: '',
    })
  }
  if (kind === 'rectification' && target) {
    activeIssue.value = target as QualityIssueRecord
    Object.assign(rectificationForm, {
      issueId: target.id,
      actionDescription: '',
      responsibleUserId: (target as QualityIssueRecord).responsibleUserId,
      plannedCompleteDate: (target as QualityIssueRecord).dueDate,
      remark: '',
    })
  }
  if (kind === 'consequence' && target) {
    activeIssue.value = target as QualityIssueRecord
    Object.assign(consequenceForm, {
      issueId: target.id,
      partnerId: (target as QualityIssueRecord).responsiblePartnerId ?? '',
      contractId: '',
      consequenceCode: `QS-C-${today().replaceAll('-', '')}`,
      decisionType: 'NONE',
      fineAmount: '0',
      reworkCostAmount: '0',
      evaluationScore: '80',
      evaluationComment: '',
      remark: '',
    })
  }
}

async function showReinspection(issue: QualityIssueRecord): Promise<void> {
  await openTrace(issue)
  const current = [...(trace.value?.rectifications ?? [])]
    .reverse()
    .find((item) => item.status === 'SUBMITTED')
  if (!current) {
    errorMessage.value = '未找到待复检整改轮次'
    return
  }
  activeRectification.value = current
  evidence.value = null
  Object.assign(reinspectionForm, { result: 'PASS', comment: '' })
  dialog.value = 'reinspection'
}

function chooseEvidence(event: Event): void {
  evidence.value = (event.target as HTMLInputElement).files?.[0] ?? null
}
function showEvidence(target: EvidenceTarget): void {
  clearNotice()
  evidence.value = null
  evidenceTarget.value = target
  dialog.value = 'evidence'
}
async function uploadRequired(
  type: string,
  businessId: string,
  documentType: string,
): Promise<void> {
  if (!evidence.value) throw new TypeError('必须选择阶段证据附件')
  await uploadSiteFile(evidence.value, type, businessId, documentType)
}

async function runWrite(
  action: () => Promise<void>,
  success: string,
  issue?: QualityIssueRecord,
): Promise<void> {
  if (!projectId.value) {
    errorMessage.value = '请先选择具体项目'
    return
  }
  saving.value = true
  clearNotice()
  try {
    await action()
    dialog.value = null
    successMessage.value = success
    await loadProject(true)
    const refreshed = issue ? issues.value.find((item) => item.id === issue.id) : null
    if (refreshed) await openTrace(refreshed, true)
  } catch (error) {
    errorMessage.value = errorText(
      error,
      error instanceof Error ? error.message : '操作失败，当前数据未变更',
    )
    await loadProject(true).catch(() => undefined)
    const refreshed = issue ? issues.value.find((item) => item.id === issue.id) : null
    if (refreshed) await openTrace(refreshed, true)
  } finally {
    saving.value = false
  }
}

const savePlan = () =>
  runWrite(async () => {
    await createQualityPlan(planForm)
  }, '检查计划已创建')
const activatePlan = (plan: QualityPlanRecord) =>
  runWrite(async () => {
    await activateQualityPlan(plan.id)
  }, '计划已激活')
const finishPlan = (plan: QualityPlanRecord) =>
  runWrite(async () => {
    await completeQualityPlan(plan.id)
  }, '计划已完成')
const saveInspection = () =>
  runWrite(async () => {
    const created = await createQualityInspection(inspectionForm)
    await uploadRequired('QS_INSPECTION', created.id, 'INSPECTION_EVIDENCE')
  }, '检查及证据已创建')
const saveIssue = () =>
  runWrite(async () => {
    const inspection = activeInspection.value
    if (!inspection) throw new TypeError('检查记录不存在')
    const created = await createQualityIssue(inspection.id, issueForm)
    await uploadRequired('QS_ISSUE', created.id, 'ISSUE_EVIDENCE')
  }, '问题及证据已创建')
const submitInspection = (inspection: QualityInspectionRecord) =>
  runWrite(async () => {
    await submitQualityInspection(inspection.id)
  }, '检查已提交')
const saveRectification = () =>
  runWrite(
    async () => {
      const issue = activeIssue.value
      if (!issue) throw new TypeError('问题单不存在')
      const created = await createQualityRectification(rectificationForm)
      await uploadRequired('QS_RECTIFICATION', created.id, 'RECTIFICATION_EVIDENCE')
      await submitQualityRectification(created.id)
    },
    '整改已提交复检',
    activeIssue.value ?? undefined,
  )
const saveReinspection = () =>
  runWrite(
    async () => {
      const item = activeRectification.value
      if (!item) throw new TypeError('整改轮次不存在')
      await uploadRequired('QS_RECTIFICATION', item.id, 'REINSPECTION_EVIDENCE')
      await reinspectQualityRectification(item.id, reinspectionForm)
    },
    '复检结果已提交',
    activeIssue.value ?? undefined,
  )
const saveConsequence = () =>
  runWrite(
    async () => {
      const created = await createQualityConsequence(consequenceForm)
      await postQualityConsequence(created.id)
    },
    '后果已确认',
    activeIssue.value ?? undefined,
  )
const postExistingConsequence = () => {
  const consequence = trace.value?.consequence
  const issue = activeIssue.value
  if (!consequence || !issue) return
  return runWrite(
    async () => {
      await postQualityConsequence(consequence.id)
    },
    '后果已确认',
    issue,
  )
}
const saveEvidence = () => {
  const target = evidenceTarget.value
  if (!target) return
  return runWrite(
    async () => {
      await uploadRequired(target.businessType, target.businessId, target.documentType)
    },
    `${target.label}已上传`,
    target.issue,
  )
}
const submitDraftRectification = (item: QualityRectificationRecord) => {
  const issue = activeIssue.value
  if (!issue) return
  return runWrite(
    async () => {
      await submitQualityRectification(item.id)
    },
    '整改已提交复检',
    issue,
  )
}

watch(scopeProjectIds, () => void loadProject(), { immediate: true })
watch(selectedPlanId, () => void loadInspections())
onBeforeUnmount(() => {
  generation += 1
  projectController?.abort()
  inspectionController?.abort()
  traceController?.abort()
})
</script>

<template>
  <section class="quality-page" aria-label="质量安全整改闭环">
    <h1 class="v2-visually-hidden">质量安全整改闭环</h1>
    <div class="quality-page__notice" role="status" aria-live="polite">
      <V2Alert v-if="errorMessage" tone="danger" title="操作未完成">{{ errorMessage }}</V2Alert>
      <V2Alert v-else-if="successMessage" tone="success" title="操作完成">{{
        successMessage
      }}</V2Alert>
    </div>

    <div v-if="canPlan && projectId" class="quality-page__actions">
      <V2Button size="small" @click="show('plan')">新建检查计划</V2Button>
    </div>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载质量安全事实"
      description="读取计划和问题链。"
    />
    <V2PageState
      v-else-if="!scopeProjectIds.length"
      kind="empty"
      title="暂无可访问项目"
      description="当前账号没有可查看的项目。"
    />
    <template v-else>
      <div class="quality-page__columns">
        <V2Card title="检查计划" :subtitle="`共 ${plans.length} 条`">
          <div v-if="plans.length" class="quality-page__stack">
            <article v-for="plan in plans" :key="plan.id" class="quality-page__item">
              <button type="button" class="quality-page__title" @click="selectedPlanId = plan.id">
                {{ plan.planCode }} · {{ plan.planName }}
              </button>
              <div class="quality-page__facts">
                <V2Badge :tone="statusTone(plan.status)">{{ deliveryLabel(plan.status) }}</V2Badge
                ><span>{{ plan.startDate }} 至 {{ plan.endDate }}</span>
              </div>
              <div class="quality-page__actions">
                <V2Button
                  v-if="canPlan && plan.status === 'DRAFT'"
                  size="small"
                  variant="secondary"
                  :loading="saving"
                  @click="activatePlan(plan)"
                  >激活</V2Button
                >
                <V2Button
                  v-if="canPlan && plan.status === 'ACTIVE'"
                  size="small"
                  variant="ghost"
                  :loading="saving"
                  @click="finishPlan(plan)"
                  >完成</V2Button
                >
              </div>
            </article>
          </div>
          <p v-else>暂无检查计划。</p>
        </V2Card>

        <V2Card title="检查记录" :subtitle="selectedPlan ? selectedPlan.planName : '先选择计划'">
          <template #actions
            ><V2Button
              v-if="canInspect && selectedPlan?.status === 'ACTIVE'"
              size="small"
              @click="show('inspection')"
              >新建检查</V2Button
            ></template
          >
          <div v-if="inspections.length" class="quality-page__stack">
            <article
              v-for="inspection in inspections"
              :key="inspection.id"
              class="quality-page__item"
            >
              <strong>{{ inspection.inspectionCode }}</strong>
              <p>{{ inspection.location }} · {{ inspection.summary }}</p>
              <div class="quality-page__facts">
                <V2Badge :tone="statusTone(inspection.status)">{{
                  deliveryLabel(inspection.status)
                }}</V2Badge
                ><span>{{ inspection.inspectionDate }}</span>
              </div>
              <div class="quality-page__actions">
                <V2Button
                  v-if="canInspect && inspection.status === 'DRAFT'"
                  size="small"
                  variant="ghost"
                  @click="
                    showEvidence({
                      businessType: 'QS_INSPECTION',
                      businessId: inspection.id,
                      documentType: 'INSPECTION_EVIDENCE',
                      label: '检查证据',
                    })
                  "
                  >上传检查证据</V2Button
                >
                <V2Button
                  v-if="canInspect && inspection.status === 'DRAFT'"
                  size="small"
                  variant="secondary"
                  @click="show('issue', inspection)"
                  >登记问题</V2Button
                >
                <V2Button
                  v-if="canInspect && inspection.status === 'DRAFT'"
                  size="small"
                  variant="ghost"
                  :loading="saving"
                  @click="submitInspection(inspection)"
                  >提交检查</V2Button
                >
              </div>
            </article>
          </div>
          <p v-else>暂无检查记录。</p>
        </V2Card>
      </div>

      <V2Card title="问题、整改与后果" :subtitle="`共 ${issues.length} 条`">
        <div v-if="issues.length" class="quality-page__issue-grid">
          <article v-for="issue in issues" :key="issue.id" class="quality-page__item">
            <div class="quality-page__facts">
              <V2Badge :tone="statusTone(issue.severity)">{{
                deliveryLabel(issue.severity)
              }}</V2Badge
              ><V2Badge :tone="statusTone(issue.status)">{{ deliveryLabel(issue.status) }}</V2Badge>
            </div>
            <h3>{{ issue.issueCode }} · {{ issue.title }}</h3>
            <p>{{ issue.description }}</p>
            <small>期限 {{ issue.dueDate }}</small>
            <div class="quality-page__actions">
              <V2Button size="small" variant="secondary" @click="openTrace(issue)">追溯</V2Button>
              <V2Button
                v-if="canInspect && issue.status === 'OPEN'"
                size="small"
                variant="ghost"
                @click="
                  showEvidence({
                    businessType: 'QS_ISSUE',
                    businessId: issue.id,
                    documentType: 'ISSUE_EVIDENCE',
                    label: '问题证据',
                    issue,
                  })
                "
                >上传问题证据</V2Button
              >
              <V2Button
                v-if="canRectify && issue.status === 'RECTIFYING'"
                size="small"
                @click="show('rectification', issue)"
                >提交整改</V2Button
              >
              <V2Button
                v-if="canReinspect && issue.status === 'PENDING_REINSPECTION'"
                size="small"
                @click="showReinspection(issue)"
                >复检</V2Button
              >
              <V2Button
                v-if="canConsequence && issue.status === 'CLOSED' && issue.responsiblePartnerId"
                size="small"
                variant="ghost"
                @click="show('consequence', issue)"
                >登记后果</V2Button
              >
            </div>
          </article>
        </div>
        <p v-else>暂无质量安全问题。</p>
      </V2Card>
    </template>

    <V2Dialog
      :open="Boolean(trace)"
      title="闭环追溯"
      :description="trace ? `${trace.issue.issueCode} · 整改与复检记录` : undefined"
      :close-on-backdrop="true"
      panel-class="v2-detail-dialog"
      @update:open="
        (open) => {
          if (!open) {
            trace = null
            traceFiles = []
          }
        }
      "
    >
      <template v-if="trace">
        <ol class="quality-page__timeline">
          <li>
            <strong>计划</strong
            ><span>{{ trace.plan.planCode }} / {{ deliveryLabel(trace.plan.status) }}</span>
          </li>
          <li>
            <strong>检查</strong
            ><span
              >{{ trace.inspection.inspectionCode }} /
              {{ deliveryLabel(trace.inspection.status) }}</span
            >
          </li>
          <li>
            <strong>问题</strong
            ><span>{{ trace.issue.issueCode }} / {{ deliveryLabel(trace.issue.status) }}</span>
          </li>
          <li v-for="item in trace.rectifications" :key="item.id">
            <strong>整改第 {{ item.roundNo }} 轮</strong
            ><span
              >{{ deliveryLabel(item.status) }} · {{ item.reinspectionComment || '未复检' }}</span
            >
          </li>
          <li>
            <strong>后果</strong
            ><span>{{
              trace.consequence
                ? `${trace.consequence.consequenceCode} / ${deliveryLabel(trace.consequence.status)}`
                : '未登记'
            }}</span>
          </li>
        </ol>
        <div class="quality-page__evidence" aria-label="阶段证据附件">
          <section v-for="group in traceFiles" :key="group.stage">
            <h3>{{ group.stage }}</h3>
            <ul>
              <li v-for="file in group.files" :key="file.id">{{ file.originalName }}</li>
              <li v-if="!group.files.length">无附件</li>
            </ul>
          </section>
        </div>
      </template>
      <template #footer>
        <template v-if="trace">
          <template v-for="item in trace.rectifications" :key="item.id">
            <V2Button
              v-if="canRectify && item.status === 'DRAFT'"
              size="small"
              variant="ghost"
              @click="
                showEvidence({
                  businessType: 'QS_RECTIFICATION',
                  businessId: item.id,
                  documentType: 'RECTIFICATION_EVIDENCE',
                  label: `整改第 ${item.roundNo} 轮证据`,
                  issue: trace.issue,
                })
              "
              >上传整改证据</V2Button
            >
            <V2Button
              v-if="canRectify && item.status === 'DRAFT'"
              size="small"
              :loading="saving"
              @click="submitDraftRectification(item)"
              >提交既有整改</V2Button
            >
          </template>
          <V2Button
            v-if="canConsequence && trace.consequence?.status === 'DRAFT'"
            size="small"
            :loading="saving"
            @click="postExistingConsequence"
          >
            确认既有后果
          </V2Button>
        </template>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="dialog === 'evidence'"
      :title="evidenceTarget?.label || '上传阶段证据'"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
      @update:open="
        (open) => {
          if (!open) dialog = null
        }
      "
      ><form id="quality-evidence-form" class="quality-page__form" @submit.prevent="saveEvidence">
        <label class="quality-page__wide"
          >阶段证据<input type="file" required @change="chooseEvidence"
        /></label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="dialog = null">取消</V2Button>
        <V2Button type="submit" form="quality-evidence-form" :loading="saving">上传证据</V2Button>
      </template></V2Dialog
    >

    <V2Dialog
      :open="dialog === 'plan'"
      title="新建检查计划"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
      @update:open="
        (open) => {
          if (!open) dialog = null
        }
      "
      ><form id="quality-plan-form" class="quality-page__form" @submit.prevent="savePlan">
        <V2Input v-model="planForm.planCode" label="计划编码" required /><V2Input
          v-model="planForm.planName"
          label="计划名称"
          required
        /><V2Select
          v-model="planForm.inspectionType"
          label="检查类型"
          :options="[
            { value: 'QUALITY', label: '质量' },
            { value: 'SAFETY', label: '安全' },
          ]"
        /><V2Select
          v-model="planForm.frequencyType"
          label="频次"
          :options="[
            { value: 'SINGLE', label: '单次' },
            { value: 'WEEKLY', label: '每周' },
            { value: 'MONTHLY', label: '每月' },
          ]"
        /><label>开始日期<input v-model="planForm.startDate" type="date" required /></label
        ><label>结束日期<input v-model="planForm.endDate" type="date" required /></label
        ><V2Input v-model="planForm.ownerUserId" label="负责人用户 ID" required />
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="dialog = null">取消</V2Button>
        <V2Button type="submit" form="quality-plan-form" :loading="saving">保存计划</V2Button>
      </template></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'inspection'"
      title="新建检查"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
      @update:open="
        (open) => {
          if (!open) dialog = null
        }
      "
      ><form
        id="quality-inspection-form"
        class="quality-page__form"
        @submit.prevent="saveInspection"
      >
        <V2Input v-model="inspectionForm.inspectionCode" label="检查编码" required /><label
          >检查日期<input v-model="inspectionForm.inspectionDate" type="date" required /></label
        ><V2Input v-model="inspectionForm.location" label="检查地点" required /><V2Input
          v-model="inspectionForm.inspectorUserId"
          label="检查人用户 ID"
          required
        /><label class="quality-page__wide"
          >检查摘要<textarea v-model="inspectionForm.summary" required /></label
        ><label class="quality-page__wide"
          >检查证据<input type="file" required @change="chooseEvidence"
        /></label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="dialog = null">取消</V2Button>
        <V2Button type="submit" form="quality-inspection-form" :loading="saving">保存检查</V2Button>
      </template></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'issue'"
      title="登记问题"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
      @update:open="
        (open) => {
          if (!open) dialog = null
        }
      "
      ><form id="quality-issue-form" class="quality-page__form" @submit.prevent="saveIssue">
        <V2Input v-model="issueForm.category" label="问题分类" required /><V2Select
          v-model="issueForm.severity"
          label="严重程度"
          :options="
            ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((value) => ({
              value,
              label: deliveryLabel(value),
            }))
          "
        /><V2Input v-model="issueForm.title" label="问题标题" required /><V2Select
          v-model="issueForm.responsibleKind"
          label="责任类型"
          :options="[
            { value: 'INTERNAL', label: '内部' },
            { value: 'PARTNER', label: '合作方' },
          ]"
        /><V2Input
          v-if="issueForm.responsibleKind === 'PARTNER'"
          v-model="issueForm.responsiblePartnerId"
          label="责任合作方 ID"
          required
        /><V2Input v-model="issueForm.responsibleUserId" label="责任人用户 ID" required /><label
          >整改期限<input v-model="issueForm.dueDate" type="date" required /></label
        ><label class="quality-page__wide"
          >问题描述<textarea v-model="issueForm.description" required /></label
        ><label class="quality-page__wide"
          >问题证据<input type="file" required @change="chooseEvidence"
        /></label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="dialog = null">取消</V2Button>
        <V2Button type="submit" form="quality-issue-form" :loading="saving">登记问题</V2Button>
      </template></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'rectification'"
      title="提交整改"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
      @update:open="
        (open) => {
          if (!open) dialog = null
        }
      "
      ><form
        id="quality-rectification-form"
        class="quality-page__form"
        @submit.prevent="saveRectification"
      >
        <V2Input
          v-model="rectificationForm.responsibleUserId"
          label="整改责任人 ID"
          required
        /><label
          >计划完成日期<input
            v-model="rectificationForm.plannedCompleteDate"
            type="date"
            required /></label
        ><label class="quality-page__wide"
          >整改措施<textarea v-model="rectificationForm.actionDescription" required /></label
        ><label class="quality-page__wide"
          >整改证据<input type="file" required @change="chooseEvidence"
        /></label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="dialog = null">取消</V2Button>
        <V2Button type="submit" form="quality-rectification-form" :loading="saving"
          >提交整改</V2Button
        >
      </template></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'reinspection'"
      title="整改复检"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
      @update:open="
        (open) => {
          if (!open) dialog = null
        }
      "
      ><form
        id="quality-reinspection-form"
        class="quality-page__form"
        @submit.prevent="saveReinspection"
      >
        <V2Select
          v-model="reinspectionForm.result"
          label="复检结论"
          :options="[
            { value: 'PASS', label: '通过' },
            { value: 'REJECT', label: '驳回' },
          ]"
        /><label class="quality-page__wide"
          >复检意见<textarea v-model="reinspectionForm.comment" required /></label
        ><label class="quality-page__wide"
          >复检证据<input type="file" required @change="chooseEvidence"
        /></label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="dialog = null">取消</V2Button>
        <V2Button type="submit" form="quality-reinspection-form" :loading="saving"
          >提交复检</V2Button
        >
      </template></V2Dialog
    >
    <V2Dialog
      :open="dialog === 'consequence'"
      title="登记合作方后果"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
      @update:open="
        (open) => {
          if (!open) dialog = null
        }
      "
      ><form
        id="quality-consequence-form"
        class="quality-page__form"
        @submit.prevent="saveConsequence"
      >
        <V2Input v-model="consequenceForm.partnerId" label="合作方 ID" required /><V2Input
          v-model="consequenceForm.contractId"
          label="关联合同 ID"
          required
        /><V2Input v-model="consequenceForm.consequenceCode" label="后果编码" required /><V2Select
          v-model="consequenceForm.decisionType"
          label="处置类型"
          :options="
            ['NONE', 'FINE', 'REWORK_COST', 'BOTH'].map((value) => ({
              value,
              label: deliveryLabel(value),
            }))
          "
        /><V2Input v-model="consequenceForm.fineAmount" label="罚款金额" required /><V2Input
          v-model="consequenceForm.reworkCostAmount"
          label="返工成本"
          required
        /><V2Input v-model="consequenceForm.evaluationScore" label="评价得分" required /><label
          class="quality-page__wide"
          >评价意见<textarea v-model="consequenceForm.evaluationComment" required />
        </label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="dialog = null">取消</V2Button>
        <V2Button type="submit" form="quality-consequence-form" :loading="saving"
          >确认登记</V2Button
        >
      </template></V2Dialog
    >
  </section>
</template>

<style scoped>
.quality-page {
  display: grid;
  gap: var(--v2-space-3);
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-12);
}
.quality-page h1,
.quality-page h3,
.quality-page p {
  margin-block: 0;
}
.quality-page__notice:empty {
  display: none;
}
.quality-page__facts,
.quality-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}
.quality-page__columns,
.quality-page__issue-grid,
.quality-page__evidence {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.quality-page__issue-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.quality-page__stack {
  display: grid;
  gap: var(--v2-space-2);
}
.quality-page__item {
  display: grid;
  gap: var(--v2-space-2);
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.quality-page__item > strong,
.quality-page__item > h3 {
  font-size: var(--v2-font-size-14);
  font-weight: var(--v2-font-weight-semibold);
}
.quality-page__title {
  padding: 0;
  color: var(--v2-color-primary-hover);
  background: none;
  border: 0;
  font: inherit;
  font-size: var(--v2-font-size-14);
  font-weight: var(--v2-font-weight-semibold);
  text-align: left;
  cursor: pointer;
}
.quality-page__timeline {
  display: grid;
  gap: var(--v2-space-2);
  padding-left: 1.25rem;
}
.quality-page__timeline li {
  padding-left: var(--v2-space-2);
}
.quality-page__timeline span {
  display: block;
  color: var(--v2-color-text-secondary);
}
.quality-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.quality-page__form label {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.quality-page__form input,
.quality-page__form textarea {
  min-height: 2.5rem;
  padding: var(--v2-space-2);
  color: var(--v2-color-text);
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
  border-radius: var(--v2-radius-md);
  font: inherit;
}
.quality-page__form :deep(.v2-field__control) {
  background: transparent;
  border-color: color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
}
.quality-page__form textarea {
  min-height: 6rem;
  resize: vertical;
}
.quality-page__wide {
  grid-column: 1 / -1;
}
@media (max-width: 64rem) {
  .quality-page__issue-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 40rem) {
  .quality-page__columns,
  .quality-page__issue-grid,
  .quality-page__evidence,
  .quality-page__form {
    grid-template-columns: 1fr;
  }
  .quality-page__wide {
    grid-column: auto;
  }
}
</style>
