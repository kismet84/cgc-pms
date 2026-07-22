<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type {
  ArchiveTransferCommand,
  CloseProjectCommand,
  CloseoutDefect,
  CloseoutOverview,
  CloseoutTrace,
  CloseoutWarranty,
  DefectCommand,
  DefectVerificationCommand,
  FinalAcceptanceCommand,
  InitiateCloseoutCommand,
  RectificationCommand,
  SectionAcceptanceCommand,
  SettlementBindingCommand,
  SiteFileRecord,
  WarrantyCommand,
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
  acceptArchiveTransfer,
  bindFinalSettlement,
  closeProjectCloseout,
  confirmSectionAcceptance,
  createArchiveTransfer,
  createCloseoutDefect,
  createFinalAcceptance,
  createSectionAcceptance,
  initiateProjectCloseout,
  loadCloseoutOverview,
  loadCloseoutTrace,
  rectifyCloseoutDefect,
  registerWarranty,
  releaseWarranty,
  submitFinalAcceptance,
  verifyCloseoutDefect,
  verifyTailCollection,
} from '@/services/closeout'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

type DialogKind =
  | 'initiate'
  | 'section'
  | 'finalAcceptance'
  | 'settlement'
  | 'warranty'
  | 'defect'
  | 'rectification'
  | 'verification'
  | 'release'
  | 'archive'
  | 'closeProject'
  | null

interface PendingEvidence {
  kind: 'section' | 'finalAcceptance' | 'archive'
  businessType:
    'CLOSEOUT_SECTION_ACCEPTANCE' | 'CLOSEOUT_FINAL_ACCEPTANCE' | 'CLOSEOUT_ARCHIVE_TRANSFER'
  businessId: string
  documentType:
    'SECTION_ACCEPTANCE_RECORD' | 'FINAL_ACCEPTANCE_CERTIFICATE' | 'ARCHIVE_TRANSFER_LIST'
}

interface EvidenceGroup {
  stage: string
  files: SiteFileRecord[]
}

const route = useRoute()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const dialog = ref<DialogKind>(null)
const overview = ref<CloseoutOverview | null>(null)
const trace = ref<CloseoutTrace | null>(null)
const traceFiles = ref<EvidenceGroup[]>([])
const pendingEvidence = ref<PendingEvidence | null>(null)
const uploadFile = ref<File | null>(null)
const selectedDefect = ref<CloseoutDefect | null>(null)
const selectedWarranty = ref<CloseoutWarranty | null>(null)
let projectController: AbortController | null = null
let traceController: AbortController | null = null
let generation = 0

const today = () => new Date().toISOString().slice(0, 10)
const projectId = computed(() => {
  const query = typeof route.query.projectId === 'string' ? route.query.projectId.trim() : ''
  return query || workspace.selectedProjectId || ''
})
const projectOptions = computed(() =>
  workspace.projects.map((item) => ({ value: item.value, label: item.label })),
)
const closeout = computed(() => overview.value?.closeout ?? null)
const canInitiate = computed(() => can('closeout:initiate'))
const canQuery = computed(() => can('closeout:query'))
const canSection = computed(() => can('closeout:section:maintain'))
const canAcceptance = computed(() => can('closeout:acceptance:submit'))
const canSettlement = computed(() => can('closeout:settlement:bind'))
const canCollection = computed(() => can('closeout:collection:verify'))
const canWarranty = computed(() => can('closeout:warranty:maintain'))
const canDefect = computed(() => can('closeout:defect:maintain'))
const canDefectVerify = computed(() => can('closeout:defect:verify'))
const canArchive = computed(() => can('closeout:archive:maintain'))
const canClose = computed(() => can('closeout:close'))
const sectionQualityOptions = computed(() =>
  (overview.value?.qualityInspections ?? []).map((item) => ({
    value: item.id,
    label: `${item.inspectionCode} · ${item.conclusion}`,
  })),
)
const sectionWbsOptions = computed(() =>
  (overview.value?.wbsTasks ?? []).map((item) => ({
    value: item.id,
    label: `${item.taskCode} · ${item.taskName}`,
  })),
)
const retentionReceivables = computed(() =>
  (overview.value?.receivables ?? []).filter((item) => item.receivableType === 'RETENTION'),
)
const warrantyContractOptions = computed(() =>
  (overview.value?.settlements ?? []).map((item) => ({
    value: item.contractId,
    label: `${item.settlementCode} · 合同 ${item.contractId}`,
  })),
)

const initiateForm = reactive<InitiateCloseoutCommand>({
  projectId: '',
  closeoutCode: '',
  plannedCompletionDate: today(),
  remark: '',
})
const sectionForm = reactive<SectionAcceptanceCommand>({
  wbsTaskId: '',
  qualityInspectionId: '',
  acceptanceCode: '',
  acceptanceName: '',
  acceptanceDate: today(),
  conclusion: 'PASS',
  remark: '',
})
const finalAcceptanceForm = reactive<FinalAcceptanceCommand>({
  acceptanceCode: '',
  acceptanceDate: today(),
  organizer: '',
  participantSummary: '',
  conclusion: 'PASS',
  acceptanceSummary: '',
  remark: '',
})
const settlementForm = reactive<SettlementBindingCommand>({ ownerSettlementId: '' })
const warrantyForm = reactive<WarrantyCommand>({
  contractId: '',
  receivableId: '',
  warrantyCode: '',
  warrantyAmount: '',
  warrantyStartDate: today(),
  warrantyEndDate: today(),
  responsibleUserId: '',
  remark: '',
})
const defectForm = reactive<DefectCommand>({
  defectCode: '',
  defectTitle: '',
  defectDescription: '',
  responsibleUserId: '',
  rectificationDeadline: today(),
  remark: '',
})
const rectificationForm = reactive<RectificationCommand>({ rectificationContent: '' })
const verificationForm = reactive<DefectVerificationCommand>({
  decision: 'ACCEPTED',
  verificationComment: '',
})
const archiveForm = reactive<ArchiveTransferCommand>({
  transferCode: '',
  transferDate: today(),
  recipientOrganization: '',
  recipientName: '',
  archiveLocation: '',
  transferScope: '',
  remark: '',
})
const closeForm = reactive<CloseProjectCommand>({
  actualCompletionDate: today(),
  reason: '',
})

function can(permission: string): boolean {
  return (
    session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
    session.hasPermission(permission)
  )
}

function badgeTone(status?: string): 'success' | 'warning' | 'danger' | 'neutral' {
  if (!status) return 'neutral'
  if (['ACCEPTED', 'APPROVED', 'RELEASED', 'READY_TO_CLOSE', 'CLOSED'].includes(status))
    return 'success'
  if (['REJECTED'].includes(status)) return 'danger'
  if (
    [
      'INITIATED',
      'SECTION_ACCEPTANCE',
      'FINAL_ACCEPTANCE_PENDING',
      'FINAL_SETTLEMENT_BOUND',
      'TAIL_PAYMENT_COLLECTED',
      'WARRANTY_ACTIVE',
      'DEFECT_LIABILITY',
      'DRAFT',
      'PENDING',
      'PENDING_VERIFICATION',
    ].includes(status)
  )
    return 'warning'
  return 'neutral'
}

function clearNotice(): void {
  errorMessage.value = ''
  successMessage.value = ''
}

function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

function pickFile(event: Event): void {
  uploadFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

function show(kind: Exclude<DialogKind, null>, target?: CloseoutWarranty | CloseoutDefect): void {
  if (pendingEvidence.value) {
    dialog.value = pendingEvidence.value.kind
    errorMessage.value = '业务对象已创建，仅需重试附件上传；不会重复提交业务写请求'
    return
  }
  clearNotice()
  dialog.value = kind
  uploadFile.value = null
  selectedWarranty.value =
    kind === 'defect' || kind === 'release' ? (target as CloseoutWarranty) : null
  selectedDefect.value =
    kind === 'rectification' || kind === 'verification' ? (target as CloseoutDefect) : null
  if (kind === 'initiate') {
    Object.assign(initiateForm, {
      projectId: projectId.value,
      closeoutCode: `PC-${today().replaceAll('-', '')}`,
      plannedCompletionDate: today(),
      remark: '',
    })
  }
  if (kind === 'section') {
    Object.assign(sectionForm, {
      wbsTaskId: overview.value?.wbsTasks[0]?.id ?? '',
      qualityInspectionId: overview.value?.qualityInspections[0]?.id ?? '',
      acceptanceCode: `SA-${today().replaceAll('-', '')}`,
      acceptanceName: '分部分项验收',
      acceptanceDate: today(),
      conclusion: 'PASS',
      remark: '',
    })
  }
  if (kind === 'finalAcceptance') {
    Object.assign(finalAcceptanceForm, {
      acceptanceCode: `FA-${today().replaceAll('-', '')}`,
      acceptanceDate: today(),
      organizer: '建设单位',
      participantSummary: '',
      conclusion: 'PASS',
      acceptanceSummary: '',
      remark: '',
    })
  }
  if (kind === 'settlement') Object.assign(settlementForm, { ownerSettlementId: '' })
  if (kind === 'warranty') {
    const retention = retentionReceivables.value[0]
    Object.assign(warrantyForm, {
      contractId: overview.value?.settlements[0]?.contractId ?? '',
      receivableId: retention?.id ?? '',
      warrantyCode: `W-${today().replaceAll('-', '')}`,
      warrantyAmount: retention?.originalAmount ?? '',
      warrantyStartDate: today(),
      warrantyEndDate: today(),
      responsibleUserId: String(session.userInfo?.userId ?? ''),
      remark: '',
    })
  }
  if (kind === 'defect') {
    Object.assign(defectForm, {
      defectCode: `DF-${today().replaceAll('-', '')}`,
      defectTitle: '',
      defectDescription: '',
      responsibleUserId:
        selectedWarranty.value?.responsibleUserId ?? String(session.userInfo?.userId ?? ''),
      rectificationDeadline: today(),
      remark: '',
    })
  }
  if (kind === 'rectification') Object.assign(rectificationForm, { rectificationContent: '' })
  if (kind === 'verification') {
    Object.assign(verificationForm, { decision: 'ACCEPTED', verificationComment: '' })
  }
  if (kind === 'release') uploadFile.value = null
  if (kind === 'archive') {
    Object.assign(archiveForm, {
      transferCode: `AT-${today().replaceAll('-', '')}`,
      transferDate: today(),
      recipientOrganization: '',
      recipientName: '',
      archiveLocation: '',
      transferScope: '',
      remark: '',
    })
  }
  if (kind === 'closeProject') {
    Object.assign(closeForm, { actualCompletionDate: today(), reason: '' })
  }
}

async function loadProject(preserveNotice = false): Promise<void> {
  projectController?.abort()
  traceController?.abort()
  const requestGeneration = ++generation
  overview.value = null
  trace.value = null
  traceFiles.value = []
  if (!projectId.value) return
  const controller = new AbortController()
  projectController = controller
  loading.value = true
  if (!preserveNotice) clearNotice()
  try {
    const loaded = await loadCloseoutOverview(projectId.value, controller.signal)
    if (requestGeneration === generation) overview.value = loaded
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '竣工收尾事实加载失败')
  } finally {
    if (requestGeneration === generation) loading.value = false
  }
}

async function openTrace(closeoutId = closeout.value?.id, preserveNotice = false): Promise<void> {
  if (!closeoutId) return
  traceController?.abort()
  const controller = new AbortController()
  traceController = controller
  if (!preserveNotice) clearNotice()
  try {
    const current = await loadCloseoutTrace(closeoutId, controller.signal)
    const groups = await Promise.all(
      [
        ...current.sectionAcceptances.map((row, index) => ({
          stage: `分项验收 ${index + 1}`,
          businessType: 'CLOSEOUT_SECTION_ACCEPTANCE' as const,
          documentType: 'SECTION_ACCEPTANCE_RECORD',
          businessId: String((row as Record<string, unknown>).id),
        })),
        ...current.finalAcceptances.map((row, index) => ({
          stage: `竣工验收 ${index + 1}`,
          businessType: 'CLOSEOUT_FINAL_ACCEPTANCE' as const,
          documentType: 'FINAL_ACCEPTANCE_CERTIFICATE',
          businessId: String((row as Record<string, unknown>).id),
        })),
        ...current.warranties.map((row, index) => ({
          stage: `质保释放 ${index + 1}`,
          businessType: 'CLOSEOUT_WARRANTY' as const,
          documentType: 'WARRANTY_RELEASE_VOUCHER',
          businessId: String((row as Record<string, unknown>).id),
        })),
        ...current.defects.map((row, index) => ({
          stage: `缺陷整改 ${index + 1}`,
          businessType: 'CLOSEOUT_DEFECT' as const,
          documentType: 'DEFECT_RECTIFICATION_EVIDENCE',
          businessId: String((row as Record<string, unknown>).id),
        })),
        ...current.archiveTransfers.map((row, index) => ({
          stage: `档案移交 ${index + 1}`,
          businessType: 'CLOSEOUT_ARCHIVE_TRANSFER' as const,
          documentType: 'ARCHIVE_TRANSFER_LIST',
          businessId: String((row as Record<string, unknown>).id),
        })),
      ].map(async (item) => ({
        stage: item.stage,
        files: await listSiteFiles(item.businessType, item.businessId, controller.signal),
      })),
    )
    if (!controller.signal.aborted) {
      trace.value = current
      traceFiles.value = groups
    }
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '收尾追溯加载失败')
  }
}

async function uploadPendingEvidence(): Promise<void> {
  if (!uploadFile.value) throw new Error('请上传阶段证据')
  const pending = pendingEvidence.value
  if (!pending) return
  await uploadSiteFile(
    uploadFile.value,
    pending.businessType,
    pending.businessId,
    pending.documentType,
  )
  pendingEvidence.value = null
}

async function attachEvidence(pending: PendingEvidence): Promise<void> {
  pendingEvidence.value = pending
  await uploadPendingEvidence()
}

async function run(
  action: () => Promise<void>,
  success: string,
  reloadTrace = true,
): Promise<void> {
  saving.value = true
  clearNotice()
  try {
    await action()
    dialog.value = null
    successMessage.value = success
  } catch (error) {
    errorMessage.value = pendingEvidence.value
      ? `业务对象已创建，仅附件上传失败；请直接重试附件上传。${errorText(error, '')}`
      : errorText(error, '操作失败，已保留后端权威状态')
  } finally {
    await loadProject(true)
    if (reloadTrace && closeout.value?.id)
      await openTrace(closeout.value.id, true).catch(() => undefined)
    saving.value = false
  }
}

function formatAmount(value?: string | null): string {
  return value?.trim() ? value : '0'
}

const saveDialog = () =>
  run(async () => {
    if (!projectId.value) throw new Error('缺少项目范围')
    if (pendingEvidence.value) {
      await uploadPendingEvidence()
      return
    }
    if (dialog.value === 'initiate') {
      await initiateProjectCloseout(initiateForm)
      return
    }
    if (!closeout.value) throw new Error('当前项目尚未发起收尾')
    if (dialog.value === 'section') {
      const created = await createSectionAcceptance(closeout.value.id, sectionForm)
      await attachEvidence({
        kind: 'section',
        businessType: 'CLOSEOUT_SECTION_ACCEPTANCE',
        businessId: String((created as Record<string, unknown>).id),
        documentType: 'SECTION_ACCEPTANCE_RECORD',
      })
      return
    }
    if (dialog.value === 'finalAcceptance') {
      const created = await createFinalAcceptance(closeout.value.id, finalAcceptanceForm)
      await attachEvidence({
        kind: 'finalAcceptance',
        businessType: 'CLOSEOUT_FINAL_ACCEPTANCE',
        businessId: String((created as Record<string, unknown>).id),
        documentType: 'FINAL_ACCEPTANCE_CERTIFICATE',
      })
      return
    }
    if (dialog.value === 'settlement') {
      await bindFinalSettlement(closeout.value.id, settlementForm)
      return
    }
    if (dialog.value === 'warranty') {
      await registerWarranty(closeout.value.id, warrantyForm)
      return
    }
    if (dialog.value === 'defect' && selectedWarranty.value) {
      await createCloseoutDefect(selectedWarranty.value.id, defectForm)
      return
    }
    if (dialog.value === 'rectification' && selectedDefect.value) {
      if (!uploadFile.value) throw new Error('请上传缺陷整改证据')
      await uploadSiteFile(
        uploadFile.value,
        'CLOSEOUT_DEFECT',
        selectedDefect.value.id,
        'DEFECT_RECTIFICATION_EVIDENCE',
      )
      await rectifyCloseoutDefect(selectedDefect.value.id, rectificationForm)
      return
    }
    if (dialog.value === 'verification' && selectedDefect.value) {
      await verifyCloseoutDefect(selectedDefect.value.id, verificationForm)
      return
    }
    if (dialog.value === 'release' && selectedWarranty.value) {
      if (!uploadFile.value) throw new Error('请上传质保释放凭证')
      await uploadSiteFile(
        uploadFile.value,
        'CLOSEOUT_WARRANTY',
        selectedWarranty.value.id,
        'WARRANTY_RELEASE_VOUCHER',
      )
      await releaseWarranty(selectedWarranty.value.id)
      return
    }
    if (dialog.value === 'archive') {
      const created = await createArchiveTransfer(closeout.value.id, archiveForm)
      await attachEvidence({
        kind: 'archive',
        businessType: 'CLOSEOUT_ARCHIVE_TRANSFER',
        businessId: String((created as Record<string, unknown>).id),
        documentType: 'ARCHIVE_TRANSFER_LIST',
      })
      return
    }
    if (dialog.value === 'closeProject') {
      await closeProjectCloseout(closeout.value.id, closeForm)
    }
  }, '竣工收尾步骤已提交并回读')

watch(projectId, () => void loadProject(), { immediate: true })

onBeforeUnmount(() => {
  generation += 1
  projectController?.abort()
  traceController?.abort()
})
</script>

<template>
  <section class="closeout-page" aria-labelledby="closeout-title">
    <header>
      <p class="closeout-page__eyebrow">项目履约 · 竣工收尾</p>
      <h1 id="closeout-title">竣工收尾闭环</h1>
      <p>发起 → 分项验收 → 竣工验收 → 最终结算 → 尾款回收 → 质保缺陷 → 档案移交 → 项目关闭。</p>
    </header>

    <div class="closeout-page__notice" aria-live="polite">
      <V2Alert v-if="errorMessage" tone="danger" title="操作未完成">{{ errorMessage }}</V2Alert>
      <V2Alert v-else-if="successMessage" tone="success" title="操作完成">{{
        successMessage
      }}</V2Alert>
    </div>

    <V2Card title="项目范围" subtitle="查询身份只发起 GET 请求，不推导前端权威状态。">
      <div class="closeout-page__toolbar">
        <V2Select
          v-if="projectOptions.length"
          :model-value="projectId"
          label="项目"
          :options="projectOptions"
          @update:model-value="workspace.selectProject"
        />
        <span v-else>{{ projectId ? `项目 ${projectId}` : '请选择项目' }}</span>
        <V2Button
          v-if="canInitiate && !closeout && projectId"
          size="small"
          @click="show('initiate')"
          >发起收尾</V2Button
        >
      </div>
    </V2Card>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在加载竣工收尾事实"
      description="正在回读收尾主线、验收、结算、回款、质保与档案。"
    />
    <V2PageState
      v-else-if="!projectId"
      kind="empty"
      title="尚未选择项目"
      description="请选择项目后查看收尾闭环。"
    />
    <V2PageState
      v-else-if="!canQuery"
      kind="empty"
      title="缺少 closeout:query"
      description="当前身份没有收尾查询权限。"
    />
    <template v-else>
      <V2Card
        v-if="closeout"
        title="收尾主线"
        :subtitle="`${closeout.closeoutCode} · 后端状态 ${closeout.status}`"
      >
        <div class="closeout-page__facts">
          <V2Badge :tone="badgeTone(closeout.status)">{{ closeout.status }}</V2Badge>
          <span>计划完成 {{ closeout.plannedCompletionDate }}</span>
          <span v-if="closeout.finalOwnerSettlementId"
            >结算 {{ closeout.finalOwnerSettlementId }}</span
          >
          <span v-if="closeout.tailCollectionVerifiedAt"
            >尾款核验 {{ closeout.tailCollectionVerifiedAt }}</span
          >
        </div>
        <template #footer>
          <div class="closeout-page__actions">
            <V2Button
              v-if="canSection && closeout.status === 'INITIATED'"
              size="small"
              @click="show('section')"
              >登记分项验收</V2Button
            >
            <V2Button
              v-if="canAcceptance && closeout.status === 'SECTION_ACCEPTANCE'"
              size="small"
              @click="show('finalAcceptance')"
              >登记竣工验收</V2Button
            >
            <V2Button
              v-if="canSettlement && closeout.status === 'FINAL_ACCEPTANCE_APPROVED'"
              size="small"
              @click="show('settlement')"
              >绑定最终结算</V2Button
            >
            <V2Button
              v-if="canCollection && closeout.status === 'FINAL_SETTLEMENT_BOUND'"
              size="small"
              variant="secondary"
              :loading="saving"
              @click="
                run(() => verifyTailCollection(closeout.id).then(() => undefined), '尾款核验完成')
              "
              >核验尾款回收</V2Button
            >
            <V2Button
              v-if="
                canWarranty &&
                ['TAIL_PAYMENT_COLLECTED', 'WARRANTY_ACTIVE', 'DEFECT_LIABILITY'].includes(
                  closeout.status,
                )
              "
              size="small"
              @click="show('warranty')"
              >登记质保责任</V2Button
            >
            <V2Button
              v-if="canArchive && closeout.status === 'WARRANTY_RELEASED'"
              size="small"
              @click="show('archive')"
              >登记档案移交</V2Button
            >
            <V2Button
              v-if="canClose && closeout.status === 'READY_TO_CLOSE'"
              size="small"
              variant="ghost"
              @click="show('closeProject')"
              >关闭项目</V2Button
            >
            <V2Button size="small" variant="secondary" @click="openTrace(closeout.id)"
              >追溯</V2Button
            >
          </div>
        </template>
      </V2Card>

      <div class="closeout-page__columns">
        <V2Card
          title="分项与竣工验收"
          :subtitle="`分项 ${overview?.sectionAcceptances.length ?? 0} / 竣工 ${overview?.finalAcceptances.length ?? 0}`"
        >
          <div class="closeout-page__stack">
            <article
              v-for="section in overview?.sectionAcceptances ?? []"
              :key="section.id"
              class="closeout-page__item"
            >
              <strong>{{ section.acceptanceCode }} · {{ section.acceptanceName }}</strong>
              <div class="closeout-page__facts">
                <V2Badge :tone="badgeTone(section.status)">{{ section.status }}</V2Badge>
                <span>{{ section.taskCode }} / {{ section.acceptanceDate }}</span>
              </div>
              <V2Button
                v-if="canSection && section.status === 'DRAFT'"
                size="small"
                :loading="saving"
                @click="
                  run(
                    () => confirmSectionAcceptance(section.id).then(() => undefined),
                    '分项验收已确认',
                  )
                "
                >确认分项验收</V2Button
              >
            </article>
            <article
              v-for="item in overview?.finalAcceptances ?? []"
              :key="item.id"
              class="closeout-page__item"
            >
              <strong>{{ item.acceptanceCode }} · {{ item.organizer }}</strong>
              <div class="closeout-page__facts">
                <V2Badge :tone="badgeTone(item.status)">{{ item.status }}</V2Badge>
                <span>{{ item.conclusion }} / {{ item.acceptanceDate }}</span>
              </div>
              <V2Button
                v-if="canAcceptance && ['DRAFT', 'REJECTED'].includes(item.status)"
                size="small"
                :loading="saving"
                @click="
                  run(
                    () => submitFinalAcceptance(item.id).then(() => undefined),
                    '竣工验收已提交审批',
                  )
                "
                >提交竣工验收</V2Button
              >
            </article>
          </div>
        </V2Card>

        <V2Card
          title="结算与回款"
          subtitle="不加载候选结算列表；手工输入结算 ID，避免扩大 revenue 权限。"
        >
          <div class="closeout-page__stack">
            <article
              v-for="item in overview?.settlements ?? []"
              :key="item.id"
              class="closeout-page__item"
            >
              <strong>{{ item.settlementCode }}</strong>
              <div class="closeout-page__facts">
                <V2Badge :tone="badgeTone(item.status)">{{ item.status }}</V2Badge>
                <span>结算类型 {{ item.settlementType || '-' }}</span>
                <span>净应收 {{ formatAmount(item.netReceivableAmount) }}</span>
              </div>
            </article>
            <article
              v-for="item in overview?.receivables ?? []"
              :key="item.id"
              class="closeout-page__item"
            >
              <strong>{{ item.receivableCode }} · {{ item.receivableType }}</strong>
              <div class="closeout-page__facts">
                <V2Badge :tone="badgeTone(item.status)">{{ item.status }}</V2Badge>
                <span>原值 {{ formatAmount(item.originalAmount) }}</span>
                <span>未收 {{ formatAmount(item.outstandingAmount) }}</span>
              </div>
            </article>
            <p v-if="!(overview?.settlements?.length || overview?.receivables?.length)">
              暂无最终结算和回款事实。
            </p>
          </div>
        </V2Card>
      </div>

      <div class="closeout-page__columns">
        <V2Card
          title="质保与缺陷"
          :subtitle="`质保 ${overview?.warranties.length ?? 0} / 缺陷 ${overview?.defects.length ?? 0}`"
        >
          <div class="closeout-page__stack">
            <article
              v-for="item in overview?.warranties ?? []"
              :key="item.id"
              class="closeout-page__item"
            >
              <strong>{{ item.warrantyCode }}</strong>
              <div class="closeout-page__facts">
                <V2Badge :tone="badgeTone(item.status)">{{ item.status }}</V2Badge>
                <span>金额 {{ formatAmount(item.warrantyAmount) }}</span>
                <span>{{ item.warrantyStartDate }} 至 {{ item.warrantyEndDate }}</span>
              </div>
              <div class="closeout-page__actions">
                <V2Button
                  v-if="canDefect && ['ACTIVE', 'DEFECT_LIABILITY'].includes(item.status)"
                  size="small"
                  @click="show('defect', item)"
                  >登记缺陷</V2Button
                >
                <V2Button
                  v-if="canWarranty && ['ACTIVE', 'DEFECT_LIABILITY'].includes(item.status)"
                  size="small"
                  variant="secondary"
                  @click="show('release', item)"
                  >释放质保</V2Button
                >
              </div>
            </article>
            <article
              v-for="item in overview?.defects ?? []"
              :key="item.id"
              class="closeout-page__item"
            >
              <strong>{{ item.defectCode }} · {{ item.defectTitle }}</strong>
              <div class="closeout-page__facts">
                <V2Badge :tone="badgeTone(item.status)">{{ item.status }}</V2Badge>
                <span>期限 {{ item.rectificationDeadline }}</span>
              </div>
              <div class="closeout-page__actions">
                <V2Button
                  v-if="canDefect && item.status === 'OPEN'"
                  size="small"
                  @click="show('rectification', item)"
                  >提交整改</V2Button
                >
                <V2Button
                  v-if="canDefectVerify && item.status === 'PENDING_VERIFICATION'"
                  size="small"
                  variant="secondary"
                  @click="show('verification', item)"
                  >复验缺陷</V2Button
                >
              </div>
            </article>
          </div>
        </V2Card>

        <V2Card
          title="WBS 与质量前置"
          :subtitle="`未完工 ${overview?.wbsReadiness?.incompleteTasks ?? 0}`"
        >
          <div class="closeout-page__stack">
            <article
              v-for="item in overview?.wbsTasks ?? []"
              :key="item.id"
              class="closeout-page__item"
            >
              <strong>{{ item.taskCode }} · {{ item.taskName }}</strong>
              <div class="closeout-page__facts">
                <V2Badge :tone="badgeTone(item.status)">{{ item.status }}</V2Badge>
                <span>进度 {{ item.actualProgress }}</span>
              </div>
            </article>
            <article
              v-for="item in overview?.qualityInspections ?? []"
              :key="item.id"
              class="closeout-page__item"
            >
              <strong>{{ item.inspectionCode }}</strong>
              <div class="closeout-page__facts">
                <V2Badge :tone="badgeTone(item.status)">{{ item.status }}</V2Badge>
                <span>{{ item.conclusion }} / {{ item.inspectionDate }}</span>
              </div>
            </article>
          </div>
        </V2Card>
      </div>

      <V2Card title="档案移交" :subtitle="`共 ${overview?.archiveTransfers.length ?? 0} 条`">
        <div class="closeout-page__stack">
          <article
            v-for="item in overview?.archiveTransfers ?? []"
            :key="item.id"
            class="closeout-page__item"
          >
            <strong>{{ item.transferCode }} · {{ item.recipientOrganization }}</strong>
            <div class="closeout-page__facts">
              <V2Badge :tone="badgeTone(item.status)">{{ item.status }}</V2Badge>
              <span>{{ item.archiveLocation }}</span>
            </div>
            <V2Button
              v-if="canArchive && item.status === 'DRAFT'"
              size="small"
              :loading="saving"
              @click="
                run(() => acceptArchiveTransfer(item.id).then(() => undefined), '档案移交已签收')
              "
              >确认签收</V2Button
            >
          </article>
          <p v-if="!overview?.archiveTransfers.length">暂无档案移交事实。</p>
        </div>
      </V2Card>

      <V2Card
        v-if="trace"
        title="收尾追溯"
        :subtitle="`${trace.closeout.closeoutCode} · 后端权威链`"
      >
        <ol class="closeout-page__timeline">
          <li>
            <strong>收尾主线</strong><span>{{ trace.closeout.status }}</span>
          </li>
          <li v-for="item in trace.sectionAcceptances" :key="String(item.id)">
            <strong>分项验收 {{ item.acceptance_code || item.acceptanceCode }}</strong>
            <span>{{ item.status }}</span>
          </li>
          <li v-for="item in trace.finalAcceptances" :key="String(item.id)">
            <strong>竣工验收 {{ item.acceptance_code || item.acceptanceCode }}</strong>
            <span>{{ item.status }}</span>
          </li>
          <li v-for="item in trace.collectionAllocations" :key="item.id">
            <strong>回款分配 {{ item.collectionCode || item.id }}</strong>
            <span>{{ item.receivableType }} / {{ item.allocatedAmount }}</span>
          </li>
          <li v-for="item in trace.warranties" :key="String(item.id)">
            <strong>质保 {{ item.warranty_code || item.warrantyCode }}</strong>
            <span>{{ item.status }}</span>
          </li>
          <li v-for="item in trace.defects" :key="String(item.id)">
            <strong>缺陷 {{ item.defect_code || item.defectCode }}</strong>
            <span>{{ item.status }}</span>
          </li>
          <li v-for="item in trace.archiveTransfers" :key="String(item.id)">
            <strong>档案 {{ item.transfer_code || item.transferCode }}</strong>
            <span>{{ item.status }}</span>
          </li>
        </ol>
        <div class="closeout-page__evidence" aria-label="阶段证据附件">
          <section v-for="group in traceFiles" :key="group.stage">
            <h3>{{ group.stage }}</h3>
            <ul>
              <li v-for="file in group.files" :key="file.id">{{ file.originalName }}</li>
              <li v-if="!group.files.length">无附件</li>
            </ul>
          </section>
        </div>
      </V2Card>
    </template>

    <V2Dialog
      :open="Boolean(dialog)"
      :title="
        {
          initiate: '发起收尾',
          section: '登记分项验收',
          finalAcceptance: '登记竣工验收',
          settlement: '绑定最终结算',
          warranty: '登记质保责任',
          defect: '登记缺陷',
          rectification: '提交缺陷整改',
          verification: '复验缺陷',
          release: '释放质保',
          archive: '登记档案移交',
          closeProject: '关闭项目',
        }[dialog ?? 'initiate']
      "
      :close-disabled="saving || Boolean(pendingEvidence)"
      @update:open="
        (open) => {
          if (!open) dialog = null
        }
      "
    >
      <form class="closeout-page__form" @submit.prevent="saveDialog">
        <template v-if="dialog === 'initiate'">
          <V2Input v-model="initiateForm.closeoutCode" label="收尾编号" required />
          <label
            >计划完成日期<input v-model="initiateForm.plannedCompletionDate" type="date" required
          /></label>
          <label class="closeout-page__wide">备注<textarea v-model="initiateForm.remark" /></label>
        </template>
        <template v-else-if="dialog === 'section'">
          <V2Select v-model="sectionForm.wbsTaskId" label="WBS 任务" :options="sectionWbsOptions" />
          <V2Select
            v-model="sectionForm.qualityInspectionId"
            label="质量验收记录"
            :options="sectionQualityOptions"
          />
          <V2Input v-model="sectionForm.acceptanceCode" label="验收编号" required />
          <V2Input v-model="sectionForm.acceptanceName" label="验收名称" required />
          <label>验收日期<input v-model="sectionForm.acceptanceDate" type="date" required /></label>
          <V2Select
            v-model="sectionForm.conclusion"
            label="结论"
            :options="[
              { value: 'PASS', label: 'PASS' },
              { value: 'CONDITIONAL_PASS', label: 'CONDITIONAL_PASS' },
            ]"
          />
          <label class="closeout-page__wide"
            >验收记录<input type="file" required @change="pickFile"
          /></label>
        </template>
        <template v-else-if="dialog === 'finalAcceptance'">
          <V2Input v-model="finalAcceptanceForm.acceptanceCode" label="竣工验收编号" required />
          <label
            >验收日期<input v-model="finalAcceptanceForm.acceptanceDate" type="date" required
          /></label>
          <V2Input v-model="finalAcceptanceForm.organizer" label="组织单位" required />
          <V2Select
            v-model="finalAcceptanceForm.conclusion"
            label="结论"
            :options="[
              { value: 'PASS', label: 'PASS' },
              { value: 'CONDITIONAL_PASS', label: 'CONDITIONAL_PASS' },
            ]"
          />
          <label class="closeout-page__wide"
            >参与方<textarea v-model="finalAcceptanceForm.participantSummary" required />
          </label>
          <label class="closeout-page__wide"
            >验收摘要<textarea v-model="finalAcceptanceForm.acceptanceSummary" required />
          </label>
          <label class="closeout-page__wide"
            >验收证明<input type="file" required @change="pickFile"
          /></label>
        </template>
        <template v-else-if="dialog === 'settlement'">
          <V2Input
            v-model="settlementForm.ownerSettlementId"
            label="最终结算 ID"
            required
            placeholder="手工输入 ownerSettlementId"
          />
        </template>
        <template v-else-if="dialog === 'warranty'">
          <V2Select
            v-model="warrantyForm.contractId"
            label="合同"
            :options="warrantyContractOptions"
          />
          <V2Select
            v-model="warrantyForm.receivableId"
            label="质保金应收"
            :options="
              retentionReceivables.map((item) => ({
                value: item.id,
                label: `${item.receivableCode} · ${item.originalAmount}`,
              }))
            "
          />
          <V2Input v-model="warrantyForm.warrantyCode" label="质保编号" required />
          <V2Input v-model="warrantyForm.warrantyAmount" label="质保金额" required />
          <label
            >开始日期<input v-model="warrantyForm.warrantyStartDate" type="date" required
          /></label>
          <label
            >截止日期<input v-model="warrantyForm.warrantyEndDate" type="date" required
          /></label>
          <V2Input v-model="warrantyForm.responsibleUserId" label="责任人 ID" required />
        </template>
        <template v-else-if="dialog === 'defect'">
          <V2Input v-model="defectForm.defectCode" label="缺陷编号" required />
          <V2Input v-model="defectForm.defectTitle" label="缺陷标题" required />
          <V2Input v-model="defectForm.responsibleUserId" label="责任人 ID" required />
          <label
            >整改期限<input v-model="defectForm.rectificationDeadline" type="date" required
          /></label>
          <label class="closeout-page__wide"
            >缺陷描述<textarea v-model="defectForm.defectDescription" required />
          </label>
        </template>
        <template v-else-if="dialog === 'rectification'">
          <label class="closeout-page__wide"
            >整改内容<textarea v-model="rectificationForm.rectificationContent" required />
          </label>
          <label class="closeout-page__wide"
            >整改证据<input type="file" required @change="pickFile"
          /></label>
        </template>
        <template v-else-if="dialog === 'verification'">
          <V2Select
            v-model="verificationForm.decision"
            label="复验结论"
            :options="[
              { value: 'ACCEPTED', label: 'ACCEPTED' },
              { value: 'REJECTED', label: 'REJECTED' },
            ]"
          />
          <label class="closeout-page__wide"
            >复验意见<textarea v-model="verificationForm.verificationComment" required />
          </label>
        </template>
        <template v-else-if="dialog === 'release'">
          <p>当前质保释放只接受已登记质保责任，不在前端推导可释放条件。</p>
          <label class="closeout-page__wide"
            >释放凭证<input type="file" required @change="pickFile"
          /></label>
        </template>
        <template v-else-if="dialog === 'archive'">
          <V2Input v-model="archiveForm.transferCode" label="移交编号" required />
          <label>移交日期<input v-model="archiveForm.transferDate" type="date" required /></label>
          <V2Input v-model="archiveForm.recipientOrganization" label="接收单位" required />
          <V2Input v-model="archiveForm.recipientName" label="接收人" required />
          <V2Input v-model="archiveForm.archiveLocation" label="档案位置" required />
          <label class="closeout-page__wide"
            >移交范围<textarea v-model="archiveForm.transferScope" required />
          </label>
          <label class="closeout-page__wide"
            >签收清单<input type="file" required @change="pickFile"
          /></label>
        </template>
        <template v-else-if="dialog === 'closeProject'">
          <label
            >实际完工日期<input v-model="closeForm.actualCompletionDate" type="date" required
          /></label>
          <label class="closeout-page__wide"
            >关闭原因<textarea v-model="closeForm.reason" required />
          </label>
        </template>
        <V2Button type="submit" :loading="saving">{{
          pendingEvidence ? '仅重试附件上传' : '提交并回读'
        }}</V2Button>
      </form>
    </V2Dialog>
  </section>
</template>

<style scoped>
.closeout-page {
  display: grid;
  gap: var(--v2-space-3);
  color: var(--v2-color-text);
}
.closeout-page h1,
.closeout-page h3,
.closeout-page p {
  margin-block: 0;
}
.closeout-page__eyebrow {
  color: var(--v2-color-primary-hover);
  font-size: var(--v2-font-size-12);
  font-weight: 700;
}
.closeout-page__notice:empty {
  display: none;
}
.closeout-page__toolbar,
.closeout-page__facts,
.closeout-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}
.closeout-page__toolbar {
  justify-content: space-between;
}
.closeout-page__columns,
.closeout-page__evidence {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.closeout-page__stack,
.closeout-page__item {
  display: grid;
  gap: var(--v2-space-2);
}
.closeout-page__item {
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.closeout-page__timeline {
  display: grid;
  gap: var(--v2-space-2);
  padding-left: 1.25rem;
}
.closeout-page__timeline span {
  display: block;
  color: var(--v2-color-text-secondary);
}
.closeout-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.closeout-page__form label {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.closeout-page__form input,
.closeout-page__form textarea {
  min-height: 2.5rem;
  padding: var(--v2-space-2);
  color: var(--v2-color-text);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  font: inherit;
}
.closeout-page__form textarea {
  min-height: 6rem;
  resize: vertical;
}
.closeout-page__wide {
  grid-column: 1 / -1;
}
@media (max-width: 64rem) {
  .closeout-page__columns {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 40rem) {
  .closeout-page__form,
  .closeout-page__evidence {
    grid-template-columns: 1fr;
  }
  .closeout-page__wide {
    grid-column: auto;
  }
}
</style>
