<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type {
  ContractRecord,
  PartnerRecord,
  QualityConsequenceCommand,
  QualityInspectionCommand,
  QualityInspectionRecord,
  QualityIssueCommand,
  QualityIssueRecord,
  QualityPlanCommand,
  QualityPlanRef,
  QualityPlanRecord,
  QualityRectificationCommand,
  QualityRectificationRecord,
  QualityReinspectionCommand,
  QualityTraceRecord,
  QualityWorkspaceCounts,
  QualityWorkspaceView,
  SiteFileRecord,
} from '@cgc-pms/frontend-contracts'
import {
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
  showToast,
  useToastMessage,
} from '@/components'
import { loadContractPage, loadPartners } from '@/services/commercial'
import { listSiteFiles } from '@/services/delivery'
import {
  activateQualityPlan,
  completeQualityPlan,
  createQualityConsequence,
  createQualityInspection,
  createQualityIssue,
  createQualityPlan,
  createQualityRectification,
  loadQualityTrace,
  loadQualityWorkspace,
  reinspectQualityRectification,
  submitQualityConsequence,
  submitQualityInspection,
  submitQualityRectification,
} from '@/services/quality'
import { featureFlags } from '@/services/featureFlags'
import {
  FieldDraftRepository,
  fieldDraftStatusLabel,
  type FieldDraft,
} from '@/services/fieldDrafts'
import { isApiClientError } from '@/services/request'
import { localDateInputValue } from '@/services/workspace-context'
import { getSessionNamespaceIdentity, useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import V2Tabs from '@/components/V2Tabs.vue'
import { deliveryLabel } from './labels'
import QualityConsequencePanel from './quality-safety/QualityConsequencePanel.vue'
import QualityInspectionPanel from './quality-safety/QualityInspectionPanel.vue'
import QualityPlanPanel from './quality-safety/QualityPlanPanel.vue'
import QualityRectificationPanel from './quality-safety/QualityRectificationPanel.vue'
import QualityReinspectionPanel from './quality-safety/QualityReinspectionPanel.vue'
import {
  persistQualityDraft,
  restoreQualityDraft as restorePersistedQualityDraft,
  synchronizeQualityDraft,
  type QualityDraftPayload,
} from './quality-safety/quality-draft-sync'

type QualityTab = QualityWorkspaceView
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
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const workspaceLoaded = ref(false)
const saving = ref(false)
const errorMessage = ref('')
watch(errorMessage, (value) => {
  if (value) showToast('error', '操作未完成', value)
})
const successMessage = useToastMessage()
const plans = ref<QualityPlanRecord[]>([])
const inspections = ref<QualityInspectionRecord[]>([])
const issues = ref<QualityIssueRecord[]>([])
const selectedPlanId = ref('')
const selectedPlanRef = ref<QualityPlanRef | null>(null)
const activeTab = ref<QualityTab>(
  typeof route.query.tab === 'string' &&
    ['plan', 'inspection', 'rectification', 'reinspection', 'consequence'].includes(route.query.tab)
    ? (route.query.tab as QualityTab)
    : 'plan',
)
const pageSize = 10
const pageNo = ref(1)
const activeTotal = ref(0)
const workspaceCounts = ref<QualityWorkspaceCounts>({
  plan: 0,
  inspection: 0,
  rectification: 0,
  reinspection: 0,
  consequence: 0,
})
const activeInspection = ref<QualityInspectionRecord | null>(null)
const activeIssue = ref<QualityIssueRecord | null>(null)
const activeRectification = ref<QualityRectificationRecord | null>(null)
const trace = ref<QualityTraceRecord | null>(null)
const traceFiles = ref<Array<{ stage: string; files: SiteFileRecord[] }>>([])
const partners = ref<PartnerRecord[]>([])
const contracts = ref<ContractRecord[]>([])
const dialog = ref<DialogKind>(null)
const evidence = ref<File | null>(null)
const evidenceTarget = ref<EvidenceTarget | null>(null)
let projectController: AbortController | null = null
let traceController: AbortController | null = null
let generation = 0
let loadScheduled = false
const localDraft = ref<FieldDraft<QualityDraftPayload> | null>(null)
let draftRepository: FieldDraftRepository | null = null

const today = () => localDateInputValue()
const projectId = computed(() => workspace.selectedProjectId || '')
const hasProjectScope = computed(() => Boolean(projectId.value || workspace.projects.length))
const selectedPlan = computed(() => selectedPlanRef.value)
const rectificationIssues = computed(() =>
  activeTab.value === 'rectification' ? issues.value : [],
)
const reinspectionIssues = computed(() => (activeTab.value === 'reinspection' ? issues.value : []))
const consequenceIssues = computed(() => (activeTab.value === 'consequence' ? issues.value : []))
const pagedPlans = computed(() => plans.value)
const pagedInspections = computed(() => inspections.value)
const pagedRectificationIssues = computed(() => rectificationIssues.value)
const pagedReinspectionIssues = computed(() => reinspectionIssues.value)
const pagedConsequenceIssues = computed(() => consequenceIssues.value)
const visibleTabs = computed(() => [
  { value: 'plan', label: '检查计划', count: workspaceCounts.value.plan },
  { value: 'inspection', label: '检查记录', count: workspaceCounts.value.inspection },
  { value: 'rectification', label: '问题整改', count: workspaceCounts.value.rectification },
  { value: 'reinspection', label: '复检闭环', count: workspaceCounts.value.reinspection },
  { value: 'consequence', label: '后果追踪', count: workspaceCounts.value.consequence },
])
const activePaginationLabel = computed(
  () =>
    `${visibleTabs.value.find((tab) => tab.value === activeTab.value)?.label ?? '质量安全'}分页`,
)
const currentUserId = computed(() => String(session.userInfo?.userId ?? ''))
const userOptions = (value = '') => {
  const options = currentUserId.value
    ? [
        {
          value: currentUserId.value,
          label: session.userInfo?.realName || session.userInfo?.username || '当前用户',
        },
      ]
    : []
  if (value && value !== currentUserId.value) options.push({ value, label: '已指定项目成员' })
  return options
}
const partnerOptions = computed(() =>
  partners.value
    .filter(
      (item) =>
        item.status === 'ENABLE' &&
        ['SUPPLIER', 'SUB', 'SUBCONTRACTOR'].includes((item.partnerType || '').toUpperCase()),
    )
    .map((item) => ({
      value: item.id,
      label: `${item.partnerCode} · ${item.partnerName}`,
    })),
)
const consequencePartnerOptions = computed(() => {
  const partnerId = activeIssue.value?.responsiblePartnerId
  if (!partnerId) return []
  return [
    partnerOptions.value.find((item) => item.value === partnerId) ?? {
      value: partnerId,
      label: partnerLabel(partnerId),
    },
  ]
})
const contractOptions = computed(() =>
  contracts.value
    .filter(
      (item) =>
        (!projectId.value || item.projectId === projectId.value) &&
        Boolean(consequenceForm.partnerId) &&
        [item.partyAId, item.partyBId].includes(consequenceForm.partnerId),
    )
    .map((item) => ({
      value: item.id,
      label: `${item.contractCode} · ${item.contractName}`,
    })),
)
const partnerLabel = (partnerId?: string) =>
  partnerOptions.value.find((item) => item.value === partnerId)?.label || '已关联合作方'
const canPlan = computed(() => Boolean(projectId.value) && can('quality:safety:plan:maintain'))
const canInspect = computed(
  () => Boolean(projectId.value) && can('quality:safety:inspection:maintain'),
)
const canRectify = computed(() => Boolean(projectId.value) && can('quality:safety:rectify'))
const canReinspect = computed(() => Boolean(projectId.value) && can('quality:safety:reinspect'))
const canConsequence = computed(() => Boolean(projectId.value) && can('quality:safety:consequence'))

const workflowSubmittable = (status: string): boolean =>
  ['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(status)
const offlineDraftEnabled = computed(
  () => featureFlags.offlineDraft.enabled && featureFlags.fieldQualitySafety.enabled,
)
const offlineSyncEnabled = computed(
  () => offlineDraftEnabled.value && featureFlags.offlineSync.enabled,
)
const localDraftLabel = computed(() =>
  localDraft.value ? fieldDraftStatusLabel(localDraft.value.status) : '未保存本地草稿',
)

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

function planQueryValue(): string {
  const value = route.query.planId
  return typeof value === 'string' ? value : ''
}

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
async function loadProject(preserveNotice = false): Promise<void> {
  projectController?.abort()
  traceController?.abort()
  const requestGeneration = ++generation
  plans.value = []
  inspections.value = []
  issues.value = []
  trace.value = null
  traceFiles.value = []
  const controller = new AbortController()
  projectController = controller
  loading.value = true
  if (!preserveNotice) clearNotice()
  try {
    const loaded = await loadQualityWorkspace(
      {
        view: activeTab.value,
        pageNo: pageNo.value,
        pageSize,
        projectId: projectId.value || undefined,
        planId: selectedPlanId.value || undefined,
      },
      controller.signal,
    )
    if (requestGeneration !== generation) return
    workspaceCounts.value = loaded.counts
    activeTotal.value = loaded.page.total
    workspaceLoaded.value = true
    selectedPlanRef.value = loaded.selectedPlanRef ?? null
    selectedPlanId.value = selectedPlanRef.value?.id ?? ''
    if (activeTab.value === 'plan') plans.value = loaded.page.records as QualityPlanRecord[]
    else if (activeTab.value === 'inspection')
      inspections.value = loaded.page.records as QualityInspectionRecord[]
    else issues.value = loaded.page.records as QualityIssueRecord[]
    if (planQueryValue() && planQueryValue() !== selectedPlanId.value) {
      const query = { ...route.query }
      delete query.planId
      await router.replace({ query, hash: route.hash })
    }
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '质量安全事实加载失败')
  } finally {
    if (requestGeneration === generation) loading.value = false
  }
}

function scheduleProjectLoad(): void {
  if (loadScheduled) return
  loadScheduled = true
  queueMicrotask(() => {
    loadScheduled = false
    void loadProject()
  })
}

function selectPlan(planId: string): void {
  if (planId === selectedPlanId.value) return
  void router.replace({
    query: { ...route.query, planId },
    hash: route.hash,
  })
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

async function loadCommercialOptions(): Promise<void> {
  try {
    const [partnerPage, contractPage] = await Promise.all([
      loadPartners({ pageNo: 1, pageSize: 200, status: 'ENABLE' }),
      loadContractPage({ pageNo: 1, pageSize: 200, projectId: projectId.value || undefined }),
    ])
    partners.value = partnerPage.records
    contracts.value = contractPage.records
  } catch (error) {
    partners.value = []
    contracts.value = []
    errorMessage.value = errorText(error, '合作方与合同候选加载失败')
  }
}

async function show(
  kind: Exclude<DialogKind, null>,
  target?: QualityInspectionRecord | QualityIssueRecord,
): Promise<void> {
  clearNotice()
  evidence.value = null
  if (kind === 'issue' || kind === 'consequence') await loadCommercialOptions()
  dialog.value = kind
  if (kind === 'plan')
    Object.assign(planForm, {
      projectId: projectId.value,
      planCode: '',
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
      inspectionCode: '',
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
    await restoreQualityDraft('ISSUE')
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
    await restoreQualityDraft('RECTIFICATION')
  }
  if (kind === 'consequence' && target) {
    activeIssue.value = target as QualityIssueRecord
    Object.assign(consequenceForm, {
      issueId: target.id,
      partnerId: (target as QualityIssueRecord).responsiblePartnerId ?? '',
      contractId: '',
      consequenceCode: '',
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
function showInspectionEvidence(inspection: QualityInspectionRecord): void {
  showEvidence({
    businessType: 'QS_INSPECTION',
    businessId: inspection.id,
    documentType: 'INSPECTION_EVIDENCE',
    label: '检查证据',
  })
}
function showIssueEvidence(issue: QualityIssueRecord): void {
  showEvidence({
    businessType: 'QS_ISSUE',
    businessId: issue.id,
    documentType: 'ISSUE_EVIDENCE',
    label: '问题证据',
    issue,
  })
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
  if (!navigator.onLine) {
    errorMessage.value = '该业务动作必须在线完成；可先保存允许的本地草稿'
    return
  }
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

async function saveQualityDraft(
  kind: QualityDraftPayload['kind'],
  status: 'DRAFT' | 'PENDING' = 'DRAFT',
): Promise<boolean> {
  const payload = qualityDraftPayload(kind)
  if (!payload) return false
  try {
    localDraft.value = await persistQualityDraft({
      repository: localRepository(),
      payload,
      currentDraft: localDraft.value,
      evidence: evidence.value,
      status,
    })
    successMessage.value = status === 'DRAFT' ? '质量安全草稿已保存到本机' : '草稿已进入待同步状态'
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '质量安全草稿保存失败'
    return false
  }
}

async function syncQualityDraft(kind: QualityDraftPayload['kind']): Promise<void> {
  if (!offlineSyncEnabled.value || !(await saveQualityDraft(kind, 'PENDING')) || !localDraft.value)
    return
  const repository = localRepository()
  const draft = localDraft.value
  saving.value = true
  try {
    const result = await synchronizeQualityDraft({
      repository,
      draft,
      online: navigator.onLine,
    })
    localDraft.value = result.draft
    if (result.kind === 'OFFLINE') {
      errorMessage.value = '当前离线，草稿保留为可重试状态'
      return
    }
    if (result.kind === 'MISSING_EVIDENCE') {
      errorMessage.value = '同步质量问题或整改前必须选择证据附件'
      return
    }
    if (result.kind === 'FAILED') {
      errorMessage.value = result.message
      return
    }
    clearNotice()
    dialog.value = null
    successMessage.value = '质量安全本地草稿已同步'
    await loadProject(true)
  } finally {
    saving.value = false
  }
}

async function restoreQualityDraft(kind: QualityDraftPayload['kind']): Promise<void> {
  localDraft.value = null
  if (!offlineDraftEnabled.value) return
  try {
    const payload = qualityDraftPayload(kind)
    if (!payload) return
    const draft = await restorePersistedQualityDraft(localRepository(), payload)
    if (!draft) return
    localDraft.value = draft
    if (draft.payload.kind === 'ISSUE') Object.assign(issueForm, draft.payload.command)
    else Object.assign(rectificationForm, draft.payload.command)
    successMessage.value = `已恢复${fieldDraftStatusLabel(draft.status)}`
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '质量安全草稿恢复失败'
  }
}

function qualityDraftPayload(kind: QualityDraftPayload['kind']): QualityDraftPayload | null {
  if (kind === 'ISSUE') {
    if (!activeInspection.value) {
      errorMessage.value = '检查记录不存在'
      return null
    }
    return { kind, inspectionId: activeInspection.value.id, command: { ...issueForm } }
  }
  if (!activeIssue.value) {
    errorMessage.value = '问题单不存在'
    return null
  }
  return {
    kind,
    command: { ...rectificationForm },
  }
}

function localRepository(): FieldDraftRepository {
  if (draftRepository) return draftRepository
  const identity = getSessionNamespaceIdentity()
  if (!identity) throw new TypeError('当前会话缺少租户标识，不能使用本地草稿')
  draftRepository = new FieldDraftRepository(identity.tenantId, identity.userId)
  return draftRepository
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
    '整改已提交审批',
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
      await submitQualityConsequence(created.id)
    },
    '后果已提交审批',
    activeIssue.value ?? undefined,
  )
const submitExistingConsequence = () => {
  const consequence = trace.value?.consequence
  const issue = activeIssue.value
  if (!consequence || !issue) return
  return runWrite(
    async () => {
      await submitQualityConsequence(consequence.id)
    },
    '后果已提交审批',
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
    '整改已提交审批',
    issue,
  )
}

watch(
  projectId,
  (_value, previous) => {
    if (previous !== undefined) {
      selectedPlanId.value = ''
      selectedPlanRef.value = null
      if (planQueryValue()) {
        const query = { ...route.query }
        delete query.planId
        void router.replace({ query, hash: route.hash })
      }
    } else selectedPlanId.value = planQueryValue()
    pageNo.value = 1
    scheduleProjectLoad()
  },
  { immediate: true },
)
watch(
  () => route.query.planId,
  () => {
    const planId = planQueryValue()
    if (!planId || planId === selectedPlanId.value) return
    selectedPlanId.value = planId
    pageNo.value = 1
    scheduleProjectLoad()
  },
)
watch(activeTab, () => {
  pageNo.value = 1
  scheduleProjectLoad()
})
watch(pageNo, () => {
  scheduleProjectLoad()
})
onBeforeUnmount(() => {
  generation += 1
  projectController?.abort()
  traceController?.abort()
  draftRepository = null
})
</script>

<template>
  <section class="quality-page" aria-label="质量安全整改闭环">
    <V2Card
      v-if="!loading && !hasProjectScope && !errorMessage"
      title="质量安全整改闭环"
      :heading-level="1"
    ></V2Card>
    <V2PageState
      v-if="loading && !workspaceLoaded"
      kind="loading"
      title="正在加载质量安全事实"
      description="读取计划和问题链。"
    />
    <V2PageState
      v-else-if="!hasProjectScope && !errorMessage"
      kind="empty"
      title="暂无可访问项目"
      description="当前账号没有可查看的项目。"
    />
    <template v-else>
      <V2Card title="质量安全整改闭环" :heading-level="1">
        <template #actions>
          <div class="quality-page__actions">
            <V2Button
              v-if="activeTab === 'plan' && canPlan && projectId"
              size="small"
              @click="show('plan')"
              >新建检查计划</V2Button
            >
            <V2Button
              v-if="activeTab === 'inspection' && canInspect && selectedPlan?.status === 'ACTIVE'"
              size="small"
              variant="secondary"
              @click="show('inspection')"
              >新建检查</V2Button
            >
          </div>
        </template>
      </V2Card>
      <V2Tabs
        v-model="activeTab"
        :tabs="visibleTabs"
        id-prefix="quality"
        aria-label="质量安全业务分区"
      />
      <V2Card>
        <section
          v-if="visibleTabs.length"
          role="tabpanel"
          :id="`quality-panel-${activeTab}`"
          :aria-labelledby="`quality-tab-${activeTab}`"
          class="quality-page__record-sections"
        >
          <QualityPlanPanel
            v-if="activeTab === 'plan'"
            :plans="pagedPlans"
            :selected-plan-id="selectedPlanId"
            :can-maintain="canPlan"
            :saving="saving"
            :has-error="Boolean(errorMessage)"
            @select="selectPlan"
            @activate="activatePlan"
            @finish="finishPlan"
          />
          <QualityInspectionPanel
            v-else-if="activeTab === 'inspection'"
            :inspections="pagedInspections"
            :can-maintain="canInspect"
            :saving="saving"
            :has-error="Boolean(errorMessage)"
            @upload-evidence="showInspectionEvidence"
            @create-issue="show('issue', $event)"
            @submit="submitInspection"
          />
          <QualityRectificationPanel
            v-else-if="activeTab === 'rectification'"
            :issues="pagedRectificationIssues"
            :can-inspect="canInspect"
            :can-rectify="canRectify"
            :has-error="Boolean(errorMessage)"
            @open-trace="openTrace"
            @upload-evidence="showIssueEvidence"
            @rectify="show('rectification', $event)"
          />
          <QualityReinspectionPanel
            v-else-if="activeTab === 'reinspection'"
            :issues="pagedReinspectionIssues"
            :can-reinspect="canReinspect"
            :has-error="Boolean(errorMessage)"
            @open-trace="openTrace"
            @reinspect="showReinspection"
          />
          <QualityConsequencePanel
            v-else-if="activeTab === 'consequence'"
            :issues="pagedConsequenceIssues"
            :can-consequence="canConsequence"
            :has-error="Boolean(errorMessage)"
            :partner-label="partnerLabel"
            @open-trace="openTrace"
            @create-consequence="show('consequence', $event)"
          />
        </section>
        <V2PageState
          v-else-if="!errorMessage"
          kind="empty"
          title="暂无可访问分区"
          description="当前账号没有质量安全业务分区权限。"
        />
        <template #footer>
          <V2Pagination
            v-model:page-no="pageNo"
            :total="activeTotal"
            :label="activePaginationLabel"
          />
        </template>
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
              v-if="canRectify && workflowSubmittable(item.status)"
              type="button"
              variant="secondary"
              @click="
                showEvidence({
                  businessType: 'QS_RECTIFICATION',
                  businessId: item.id,
                  documentType: 'RECTIFICATION_EVIDENCE',
                  label: `整改第 ${item.roundNo} 轮证据`,
                  issue: trace.issue,
                })
              "
            >
              上传整改证据
            </V2Button>
            <V2Button
              v-if="canRectify && workflowSubmittable(item.status)"
              type="button"
              :loading="saving"
              @click="submitDraftRectification(item)"
            >
              提交既有整改
            </V2Button>
          </template>
          <V2Button
            v-if="
              canConsequence && trace.consequence && workflowSubmittable(trace.consequence.status)
            "
            type="button"
            :loading="saving"
            @click="submitExistingConsequence"
          >
            提交既有后果审批
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
        <p class="quality-page__wide">计划编码由服务端自动生成</p>
        <V2Input v-model="planForm.planName" label="计划名称" required /><V2Select
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
        ><V2Select
          v-model="planForm.ownerUserId"
          label="负责人"
          :options="userOptions(planForm.ownerUserId)"
          required
        />
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
        <p class="quality-page__wide">检查编码由服务端自动生成</p>
        <label>检查日期<input v-model="inspectionForm.inspectionDate" type="date" required /></label
        ><V2Input v-model="inspectionForm.location" label="检查地点" required /><V2Select
          v-model="inspectionForm.inspectorUserId"
          label="检查人"
          :options="userOptions(inspectionForm.inspectorUserId)"
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
        /><V2Select
          v-if="issueForm.responsibleKind === 'PARTNER'"
          v-model="issueForm.responsiblePartnerId"
          label="责任合作方"
          :options="partnerOptions"
          required
          placeholder="请选择责任合作方"
        /><V2Select
          v-model="issueForm.responsibleUserId"
          label="责任人"
          :options="userOptions(issueForm.responsibleUserId)"
          required
        /><label>整改期限<input v-model="issueForm.dueDate" type="date" required /></label
        ><label class="quality-page__wide"
          >问题描述<textarea v-model="issueForm.description" required /></label
        ><label class="quality-page__wide"
          >问题证据<input type="file" required @change="chooseEvidence"
        /></label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="dialog = null">取消</V2Button>
        <span v-if="offlineDraftEnabled" role="status">同步状态：{{ localDraftLabel }}</span>
        <V2Button
          v-if="offlineDraftEnabled"
          variant="secondary"
          :loading="saving"
          @click="saveQualityDraft('ISSUE')"
          >保存到本机</V2Button
        >
        <V2Button
          v-if="offlineSyncEnabled"
          variant="secondary"
          :loading="saving"
          @click="syncQualityDraft('ISSUE')"
          >手动同步</V2Button
        >
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
        <V2Select
          v-model="rectificationForm.responsibleUserId"
          label="整改责任人"
          :options="userOptions(rectificationForm.responsibleUserId)"
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
        <span v-if="offlineDraftEnabled" role="status">同步状态：{{ localDraftLabel }}</span>
        <V2Button
          v-if="offlineDraftEnabled"
          variant="secondary"
          :loading="saving"
          @click="saveQualityDraft('RECTIFICATION')"
          >保存到本机</V2Button
        >
        <V2Button
          v-if="offlineSyncEnabled"
          variant="secondary"
          :loading="saving"
          @click="syncQualityDraft('RECTIFICATION')"
          >手动同步</V2Button
        >
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
      title="登记合作方后果并提交审批"
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
        <V2Select
          v-model="consequenceForm.partnerId"
          label="合作方"
          :options="consequencePartnerOptions"
          required
          disabled
          placeholder="请选择合作方"
        /><V2Select
          v-model="consequenceForm.contractId"
          label="关联合同"
          :options="contractOptions"
          required
          placeholder="请选择合同"
        />
        <p class="quality-page__wide">后果编码由服务端自动生成</p>
        <V2Select
          v-model="consequenceForm.decisionType"
          label="处置类型"
          :options="
            ['NONE', 'FINE', 'REWORK_COST', 'BOTH'].map((value) => ({
              value,
              label: deliveryLabel(value),
            }))
          "
        /><V2Input
          v-model="consequenceForm.fineAmount"
          label="罚款金额"
          :decimal-scale="2"
          required
        /><V2Input
          v-model="consequenceForm.reworkCostAmount"
          label="返工成本"
          :decimal-scale="2"
          required
        /><V2Input
          v-model="consequenceForm.evaluationScore"
          label="评价得分"
          :decimal-scale="2"
          required
        /><label class="quality-page__wide"
          >评价意见<textarea v-model="consequenceForm.evaluationComment" required />
        </label>
      </form>
      <template #footer>
        <V2Button variant="secondary" @click="dialog = null">取消</V2Button>
        <V2Button type="submit" form="quality-consequence-form" :loading="saving"
          >提交审批</V2Button
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
.quality-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}
.quality-page__evidence {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.quality-page__record-sections {
  display: grid;
  gap: var(--v2-space-4);
}
.quality-page__record-sections h3 {
  margin: 0 0 var(--v2-space-2);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-14);
  font-weight: var(--v2-font-weight-semibold);
  line-height: var(--v2-line-height-tight);
}
.quality-page__table-wrap {
  overflow-x: auto;
}
.quality-page__table {
  min-width: 64rem;
}
.quality-page__table tbody th span {
  display: block;
  margin-top: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
  font-weight: var(--v2-font-weight-regular);
}
.quality-page__timeline {
  display: grid;
  gap: var(--v2-space-2);
  padding-left: var(--v2-space-5);
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
.quality-page__form textarea {
  min-height: var(--v2-control-height-textarea);
  resize: vertical;
}
.quality-page__wide {
  grid-column: 1 / -1;
}
@media (max-width: 64rem) {
  .quality-page__table {
    min-width: 56rem;
  }
}
@media (max-width: 40rem) {
  .quality-page__evidence,
  .quality-page__form {
    grid-template-columns: 1fr;
  }
  .quality-page__wide {
    grid-column: auto;
  }
}
</style>
