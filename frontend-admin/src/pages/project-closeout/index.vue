<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import { uploadFile } from '@/api/modules/file'
import { getOwnerSettlements, type RevenueRow } from '@/api/modules/revenueOperations'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import {
  acceptArchiveTransfer,
  bindFinalSettlement,
  closeProjectFromCloseout,
  confirmSectionAcceptance,
  createArchiveTransfer,
  createCloseoutDefect,
  createFinalAcceptance,
  createSectionAcceptance,
  getCloseoutOverview,
  getCloseoutTrace,
  initiateProjectCloseout,
  rectifyCloseoutDefect,
  registerCloseoutWarranty,
  releaseCloseoutWarranty,
  submitFinalAcceptance,
  verifyCloseoutDefect,
  verifyTailCollection,
  type CloseoutOverview,
  type CloseoutRow,
  type CloseoutTrace,
} from '@/api/modules/projectCloseout'

type DialogKind =
  | 'initiate'
  | 'section'
  | 'sectionConfirm'
  | 'final'
  | 'finalSubmit'
  | 'settlement'
  | 'warranty'
  | 'defect'
  | 'rectify'
  | 'verifyDefect'
  | 'releaseWarranty'
  | 'archive'
  | 'archiveAccept'
  | 'close'

const emptyOverview = (): CloseoutOverview => ({
  sectionAcceptances: [],
  finalAcceptances: [],
  settlements: [],
  receivables: [],
  warranties: [],
  defects: [],
  archiveTransfers: [],
  wbsReadiness: { totalTasks: 0, incompleteTasks: 0 },
  wbsTasks: [],
  qualityInspections: [],
})

const referenceStore = useReferenceStore()
const userStore = useUserStore()
const { projects } = storeToRefs(referenceStore)
const projectId = ref<string>()
const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const dialogKind = ref<DialogKind>('initiate')
const contextId = ref('')
const evidenceFile = ref<File>()
const overview = ref<CloseoutOverview>(emptyOverview())
const settlementCandidates = ref<RevenueRow[]>([])
const trace = ref<CloseoutTrace>()
const traceOpen = ref(false)
const today = () => new Date().toISOString().slice(0, 10)
const currentUserId = computed(() => userStore.userInfo?.userId ?? '')
const closeout = computed(() => overview.value.closeout)
const stage = computed(() => closeout.value?.status ?? 'NOT_STARTED')
const outstanding = computed(() =>
  overview.value.receivables.reduce((sum, row) => sum + Number(row.outstandingAmount ?? 0), 0),
)
const openDefects = computed(
  () => overview.value.defects.filter((row) => row.status !== 'CLOSED').length,
)
const acceptedSections = computed(
  () => overview.value.sectionAcceptances.filter((row) => row.status === 'ACCEPTED').length,
)
const retentionReceivables = computed(() =>
  overview.value.receivables.filter((row) => row.receivableType === 'RETENTION'),
)

const form = reactive({
  code: '',
  name: '',
  date: today(),
  wbsTaskId: '',
  qualityInspectionId: '',
  conclusion: 'PASS',
  organizer: '',
  participants: '',
  summary: '',
  ownerSettlementId: '',
  contractId: '',
  receivableId: '',
  amount: '',
  startDate: today(),
  endDate: today(),
  responsibleUserId: '',
  description: '',
  deadline: today(),
  rectificationContent: '',
  decision: 'ACCEPTED',
  comment: '',
  recipientOrganization: '',
  recipientName: '',
  archiveLocation: '',
  transferScope: '',
  reason: '',
  remark: '',
})

const dialogTitle = computed(
  () =>
    ({
      initiate: '发起项目收尾',
      section: '登记分部分项验收',
      sectionConfirm: '补充分项验收附件并确认',
      final: '登记并提交竣工验收',
      finalSubmit: '补充竣工验收附件并重新提交',
      settlement: '绑定竣工结算',
      warranty: '登记质保责任期',
      defect: '登记缺陷责任',
      rectify: '提交缺陷整改',
      verifyDefect: '复验缺陷整改',
      releaseWarranty: '释放质保金',
      archive: '移交竣工档案',
      archiveAccept: '补充档案附件并确认移交',
      close: '关闭项目',
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
        NOT_STARTED: '未发起',
        INITIATED: '已发起',
        SECTION_ACCEPTANCE: '分项验收',
        FINAL_ACCEPTANCE_PENDING: '竣工验收审批中',
        FINAL_ACCEPTANCE_APPROVED: '竣工验收已通过',
        FINAL_SETTLEMENT_BOUND: '竣工结算已绑定',
        TAIL_PAYMENT_COLLECTED: '尾款已回收',
        WARRANTY_ACTIVE: '质保期',
        DEFECT_LIABILITY: '缺陷责任处理中',
        WARRANTY_RELEASED: '质保责任已解除',
        READY_TO_CLOSE: '可关闭',
        CLOSED: '项目已关闭',
        DRAFT: '草稿',
        PENDING: '审批中',
        APPROVED: '已批准',
        REJECTED: '已驳回',
        ACCEPTED: '已接收',
        ACTIVE: '生效中',
        RELEASED: '已释放',
        OPEN: '待整改',
        PENDING_VERIFICATION: '待复验',
        COLLECTED: '已收回',
      } as Record<string, string>
    )[status] ?? status
  )
}

function statusColor(status: string) {
  if (
    ['APPROVED', 'ACCEPTED', 'RELEASED', 'CLOSED', 'COLLECTED', 'READY_TO_CLOSE'].includes(status)
  )
    return 'success'
  if (['REJECTED', 'OPEN'].includes(status)) return 'error'
  if (['PENDING', 'FINAL_ACCEPTANCE_PENDING', 'PENDING_VERIFICATION'].includes(status))
    return 'processing'
  if (['DEFECT_LIABILITY', 'WARRANTY_ACTIVE'].includes(status)) return 'warning'
  return 'default'
}

function setFile(event: Event) {
  evidenceFile.value = (event.target as HTMLInputElement).files?.[0]
}

async function loadProject() {
  overview.value = emptyOverview()
  settlementCandidates.value = []
  if (!projectId.value) return
  loading.value = true
  try {
    const [closeoutData, settlements] = await Promise.all([
      getCloseoutOverview(projectId.value),
      getOwnerSettlements(projectId.value, 'RECEIVABLE_CREATED'),
    ])
    overview.value = closeoutData
    settlementCandidates.value = settlements
  } finally {
    loading.value = false
  }
}

function openDialog(kind: DialogKind, row?: CloseoutRow) {
  if (!projectId.value) return message.warning('请先选择项目')
  dialogKind.value = kind
  contextId.value = row?.id ?? ''
  evidenceFile.value = undefined
  Object.assign(form, {
    code: '',
    name: '',
    date: today(),
    wbsTaskId: '',
    qualityInspectionId: '',
    conclusion: 'PASS',
    organizer: '',
    participants: '',
    summary: '',
    ownerSettlementId: '',
    contractId: '',
    receivableId: '',
    amount: '',
    startDate: today(),
    endDate: today(),
    responsibleUserId: currentUserId.value,
    description: '',
    deadline: today(),
    rectificationContent: '',
    decision: 'ACCEPTED',
    comment: '',
    recipientOrganization: '',
    recipientName: '',
    archiveLocation: '',
    transferScope: '',
    reason: '',
    remark: '',
  })
  if (kind === 'warranty') {
    const receivable = retentionReceivables.value[0]
    form.receivableId = receivable?.id ?? ''
    form.contractId = receivable?.contractId ?? ''
    form.amount = String(receivable?.originalAmount ?? '')
  }
  dialogOpen.value = true
}

function selectRetentionReceivable(receivableId: string) {
  const receivable = retentionReceivables.value.find((row) => row.id === receivableId)
  form.receivableId = receivableId
  form.contractId = receivable?.contractId ?? ''
  form.amount = String(receivable?.originalAmount ?? '')
}

async function attach(businessType: string, businessId: string, documentType: string) {
  if (!evidenceFile.value) throw new Error('请上传业务证据附件')
  await uploadFile(evidenceFile.value, businessType, businessId, documentType)
}

async function saveDialog() {
  if (!projectId.value) return
  saving.value = true
  try {
    const closeoutId = closeout.value?.id ?? ''
    if (dialogKind.value === 'initiate') {
      await initiateProjectCloseout({
        projectId: projectId.value,
        closeoutCode: form.code,
        plannedCompletionDate: form.date,
        remark: form.remark,
      })
    } else if (dialogKind.value === 'section') {
      const row = await createSectionAcceptance(closeoutId, {
        wbsTaskId: form.wbsTaskId,
        qualityInspectionId: form.qualityInspectionId,
        acceptanceCode: form.code,
        acceptanceName: form.name,
        acceptanceDate: form.date,
        conclusion: form.conclusion,
        remark: form.remark,
      })
      await attach('CLOSEOUT_SECTION_ACCEPTANCE', row.id, 'SECTION_ACCEPTANCE_RECORD')
      await confirmSectionAcceptance(row.id)
    } else if (dialogKind.value === 'sectionConfirm') {
      await attach('CLOSEOUT_SECTION_ACCEPTANCE', contextId.value, 'SECTION_ACCEPTANCE_RECORD')
      await confirmSectionAcceptance(contextId.value)
    } else if (dialogKind.value === 'final') {
      const row = await createFinalAcceptance(closeoutId, {
        acceptanceCode: form.code,
        acceptanceDate: form.date,
        organizer: form.organizer,
        participantSummary: form.participants,
        conclusion: form.conclusion,
        acceptanceSummary: form.summary,
        remark: form.remark,
      })
      await attach('CLOSEOUT_FINAL_ACCEPTANCE', row.id, 'FINAL_ACCEPTANCE_CERTIFICATE')
      await submitFinalAcceptance(row.id)
    } else if (dialogKind.value === 'finalSubmit') {
      await attach('CLOSEOUT_FINAL_ACCEPTANCE', contextId.value, 'FINAL_ACCEPTANCE_CERTIFICATE')
      await submitFinalAcceptance(contextId.value)
    } else if (dialogKind.value === 'settlement') {
      await bindFinalSettlement(closeoutId, form.ownerSettlementId)
    } else if (dialogKind.value === 'warranty') {
      await registerCloseoutWarranty(closeoutId, {
        contractId: form.contractId,
        receivableId: form.receivableId,
        warrantyCode: form.code,
        warrantyAmount: form.amount,
        warrantyStartDate: form.startDate,
        warrantyEndDate: form.endDate,
        responsibleUserId: form.responsibleUserId,
        remark: form.remark,
      })
    } else if (dialogKind.value === 'defect') {
      await createCloseoutDefect(contextId.value, {
        defectCode: form.code,
        defectTitle: form.name,
        defectDescription: form.description,
        responsibleUserId: form.responsibleUserId,
        rectificationDeadline: form.deadline,
        remark: form.remark,
      })
    } else if (dialogKind.value === 'rectify') {
      await attach('CLOSEOUT_DEFECT', contextId.value, 'DEFECT_RECTIFICATION_EVIDENCE')
      await rectifyCloseoutDefect(contextId.value, {
        rectificationContent: form.rectificationContent,
      })
    } else if (dialogKind.value === 'verifyDefect') {
      await verifyCloseoutDefect(contextId.value, {
        decision: form.decision,
        verificationComment: form.comment,
      })
    } else if (dialogKind.value === 'releaseWarranty') {
      await attach('CLOSEOUT_WARRANTY', contextId.value, 'WARRANTY_RELEASE_VOUCHER')
      await releaseCloseoutWarranty(contextId.value)
    } else if (dialogKind.value === 'archive') {
      const row = await createArchiveTransfer(closeoutId, {
        transferCode: form.code,
        transferDate: form.date,
        recipientOrganization: form.recipientOrganization,
        recipientName: form.recipientName,
        archiveLocation: form.archiveLocation,
        transferScope: form.transferScope,
        remark: form.remark,
      })
      await attach('CLOSEOUT_ARCHIVE_TRANSFER', row.id, 'ARCHIVE_TRANSFER_LIST')
      await acceptArchiveTransfer(row.id)
    } else if (dialogKind.value === 'archiveAccept') {
      await attach('CLOSEOUT_ARCHIVE_TRANSFER', contextId.value, 'ARCHIVE_TRANSFER_LIST')
      await acceptArchiveTransfer(contextId.value)
    } else if (dialogKind.value === 'close') {
      await closeProjectFromCloseout(closeoutId, {
        actualCompletionDate: form.date,
        reason: form.reason,
      })
    }
    dialogOpen.value = false
    message.success('操作成功')
    await loadProject()
  } catch (error) {
    if (error instanceof Error && error.message === '请上传业务证据附件')
      message.warning(error.message)
    else throw error
  } finally {
    saving.value = false
  }
}

async function runTailVerification() {
  if (!closeout.value) return
  await verifyTailCollection(closeout.value.id)
  message.success('尾款回收校验通过')
  await loadProject()
}

async function openTrace() {
  if (!closeout.value) return
  trace.value = await getCloseoutTrace(closeout.value.id)
  traceOpen.value = true
}

function revenueText(row: RevenueRow) {
  return `${String(row.settlement_code ?? row.settlementCode ?? row.id)} · ${String(row.gross_amount ?? row.grossAmount ?? 0)}`
}

onMounted(async () => {
  await referenceStore.fetchProjects()
})
</script>

<template>
  <div class="closeout-page">
    <section class="hero-card">
      <div>
        <p class="eyebrow">PROJECT COMPLETION CONTROL</p>
        <h1>项目竣工与收尾闭环</h1>
        <p class="hero-copy">
          把验收、结算、回款、质保、缺陷、档案和项目关闭锁定在同一条不可跳步的证据链。
        </p>
      </div>
      <div class="project-select">
        <a-select
          v-model:value="projectId"
          show-search
          allow-clear
          placeholder="选择项目"
          :filter-option="
            (input: string, option: { label?: string }) =>
              String(option.label ?? '')
                .toLowerCase()
                .includes(input.toLowerCase())
          "
          :options="
            projects.map((item) => ({
              value: item.id,
              label: `${item.projectCode} · ${item.projectName}`,
            }))
          "
          @change="loadProject"
        />
        <span>{{ projectId ? `当前阶段：${statusName(stage)}` : '选择项目后加载收尾台账' }}</span>
      </div>
    </section>

    <section class="kpi-grid">
      <div class="kpi-card">
        <span>分项验收</span
        ><strong>{{ acceptedSections }}/{{ overview.wbsReadiness.totalTasks }}</strong
        ><small>已接收 / WBS任务</small>
      </div>
      <div class="kpi-card">
        <span>未收应收</span><strong>¥{{ outstanding.toLocaleString() }}</strong
        ><small>尾款与质保金</small>
      </div>
      <div class="kpi-card">
        <span>开放缺陷</span><strong>{{ openDefects }}</strong
        ><small>必须全部复验关闭</small>
      </div>
      <div class="kpi-card">
        <span>收尾阶段</span><strong class="stage-text">{{ statusName(stage) }}</strong
        ><small>项目关闭唯一入口</small>
      </div>
    </section>

    <a-card :loading="loading" class="ledger-card">
      <template #title>
        <div class="card-title">
          <div>
            <strong>竣工收尾业务台账</strong
            ><span>分项验收 → 竣工验收 → 竣工结算 → 尾款 → 质保/缺陷 → 档案 → 关闭</span>
          </div>
        </div>
      </template>
      <template #extra>
        <a-space wrap>
          <a-button
            v-if="projectId && !closeout"
            type="primary"
            :disabled="!can('closeout:initiate')"
            @click="openDialog('initiate')"
            >发起收尾</a-button
          >
          <a-button v-if="closeout" @click="openTrace">全链路追溯</a-button>
          <a-button
            v-if="closeout && ['INITIATED', 'SECTION_ACCEPTANCE'].includes(stage)"
            :disabled="!can('closeout:section:maintain')"
            @click="openDialog('section')"
            >登记分项验收</a-button
          >
          <a-button
            v-if="closeout && stage === 'SECTION_ACCEPTANCE'"
            type="primary"
            :disabled="!can('closeout:acceptance:submit')"
            @click="openDialog('final')"
            >发起竣工验收</a-button
          >
          <a-button
            v-if="closeout && stage === 'FINAL_ACCEPTANCE_APPROVED'"
            type="primary"
            :disabled="!can('closeout:settlement:bind')"
            @click="openDialog('settlement')"
            >绑定竣工结算</a-button
          >
          <a-button
            v-if="closeout && stage === 'FINAL_SETTLEMENT_BOUND'"
            type="primary"
            :disabled="!can('closeout:collection:verify')"
            @click="runTailVerification"
            >校验尾款回收</a-button
          >
          <a-button
            v-if="closeout && stage === 'TAIL_PAYMENT_COLLECTED'"
            type="primary"
            :disabled="!can('closeout:warranty:maintain')"
            @click="openDialog('warranty')"
            >登记质保责任</a-button
          >
          <a-button
            v-if="closeout && stage === 'WARRANTY_RELEASED'"
            type="primary"
            :disabled="!can('closeout:archive:maintain')"
            @click="openDialog('archive')"
            >移交竣工档案</a-button
          >
          <a-button
            v-if="closeout && stage === 'READY_TO_CLOSE'"
            danger
            type="primary"
            :disabled="!can('closeout:close')"
            @click="openDialog('close')"
            >关闭项目</a-button
          >
        </a-space>
      </template>

      <a-empty v-if="!projectId" description="请选择项目，系统将加载完整收尾链路" />
      <a-empty v-else-if="!closeout" description="该项目尚未发起竣工收尾" />
      <a-tabs v-else>
        <a-tab-pane key="acceptance" tab="验收与结算">
          <a-table
            :data-source="overview.sectionAcceptances"
            row-key="id"
            :pagination="false"
            size="small"
          >
            <a-table-column title="验收编号" data-index="acceptanceCode" />
            <a-table-column title="WBS任务"
              ><template #default="{ record }"
                >{{ record.taskCode }} · {{ record.taskName }}</template
              ></a-table-column
            >
            <a-table-column title="验收日期" data-index="acceptanceDate" />
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
                ><a-button
                  v-if="record.status === 'DRAFT'"
                  type="link"
                  :disabled="!can('closeout:section:maintain')"
                  @click="openDialog('sectionConfirm', record)"
                  >补附件并确认</a-button
                ></template
              ></a-table-column
            >
          </a-table>
          <a-divider>竣工验收与最终结算</a-divider>
          <a-descriptions bordered size="small" :column="2">
            <a-descriptions-item label="竣工验收">{{
              overview.finalAcceptances[0]?.acceptanceCode ?? '-'
            }}</a-descriptions-item>
            <a-descriptions-item label="审批状态"
              ><a-tag :color="statusColor(overview.finalAcceptances[0]?.status ?? 'DRAFT')">{{
                statusName(overview.finalAcceptances[0]?.status ?? 'DRAFT')
              }}</a-tag></a-descriptions-item
            >
            <a-descriptions-item label="结算单">{{
              overview.settlements[0]?.settlementCode ?? '-'
            }}</a-descriptions-item>
            <a-descriptions-item label="结算金额"
              >¥{{
                Number(overview.settlements[0]?.grossAmount ?? 0).toLocaleString()
              }}</a-descriptions-item
            >
          </a-descriptions>
          <a-button
            v-if="
              overview.finalAcceptances[0] &&
              ['DRAFT', 'REJECTED'].includes(overview.finalAcceptances[0].status)
            "
            type="link"
            :disabled="!can('closeout:acceptance:submit')"
            @click="openDialog('finalSubmit', overview.finalAcceptances[0])"
            >补附件并重新提交竣工验收</a-button
          >
        </a-tab-pane>
        <a-tab-pane key="receivables" tab="尾款与质保金">
          <a-table
            :data-source="overview.receivables"
            row-key="id"
            :pagination="false"
            size="small"
          >
            <a-table-column title="应收编号" data-index="receivableCode" />
            <a-table-column title="类型"
              ><template #default="{ record }">{{
                record.receivableType === 'RETENTION' ? '质保金' : '尾款'
              }}</template></a-table-column
            >
            <a-table-column title="原值"
              ><template #default="{ record }"
                >¥{{ Number(record.originalAmount).toLocaleString() }}</template
              ></a-table-column
            >
            <a-table-column title="未收"
              ><template #default="{ record }"
                >¥{{ Number(record.outstandingAmount).toLocaleString() }}</template
              ></a-table-column
            >
            <a-table-column title="状态"
              ><template #default="{ record }"
                ><a-tag :color="statusColor(record.status)">{{
                  statusName(record.status)
                }}</a-tag></template
              ></a-table-column
            >
          </a-table>
          <a-divider>质保责任</a-divider>
          <a-table :data-source="overview.warranties" row-key="id" :pagination="false" size="small">
            <a-table-column title="质保编号" data-index="warrantyCode" />
            <a-table-column title="质保金额"
              ><template #default="{ record }"
                >¥{{ Number(record.warrantyAmount).toLocaleString() }}</template
              ></a-table-column
            >
            <a-table-column title="责任期"
              ><template #default="{ record }"
                >{{ record.warrantyStartDate }} → {{ record.warrantyEndDate }}</template
              ></a-table-column
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
                    v-if="['ACTIVE', 'DEFECT_LIABILITY'].includes(record.status)"
                    type="link"
                    :disabled="!can('closeout:defect:maintain')"
                    @click="openDialog('defect', record)"
                    >登记缺陷</a-button
                  ><a-button
                    v-if="['ACTIVE', 'DEFECT_LIABILITY'].includes(record.status)"
                    type="link"
                    :disabled="!can('closeout:warranty:maintain')"
                    @click="openDialog('releaseWarranty', record)"
                    >释放质保金</a-button
                  ></a-space
                ></template
              ></a-table-column
            >
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="defects" tab="缺陷与档案">
          <a-table :data-source="overview.defects" row-key="id" :pagination="false" size="small">
            <a-table-column title="缺陷编号" data-index="defectCode" />
            <a-table-column title="缺陷标题" data-index="defectTitle" />
            <a-table-column title="整改期限" data-index="rectificationDeadline" />
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
                  v-if="record.status === 'OPEN'"
                  type="link"
                  :disabled="!can('closeout:defect:maintain')"
                  @click="openDialog('rectify', record)"
                  >提交整改</a-button
                ><a-button
                  v-if="record.status === 'PENDING_VERIFICATION'"
                  type="link"
                  :disabled="!can('closeout:defect:verify')"
                  @click="openDialog('verifyDefect', record)"
                  >复验</a-button
                ></template
              ></a-table-column
            >
          </a-table>
          <a-divider>档案移交</a-divider>
          <a-table
            :data-source="overview.archiveTransfers"
            row-key="id"
            :pagination="false"
            size="small"
          >
            <a-table-column title="移交编号" data-index="transferCode" />
            <a-table-column title="接收单位" data-index="recipientOrganization" />
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
                  :disabled="!can('closeout:archive:maintain')"
                  @click="openDialog('archiveAccept', record)"
                  >补附件并确认移交</a-button
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
      width="720px"
      @ok="saveDialog"
    >
      <a-form layout="vertical">
        <template v-if="dialogKind === 'initiate'"
          ><a-form-item label="收尾编号" required><a-input v-model:value="form.code" /></a-form-item
          ><a-form-item label="计划竣工日期" required
            ><a-input v-model:value="form.date" type="date" /></a-form-item
        ></template>
        <template v-else-if="dialogKind === 'section'"
          ><a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="验收编号" required
                ><a-input v-model:value="form.code" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="验收名称" required
                ><a-input v-model:value="form.name" /></a-form-item></a-col></a-row
          ><a-form-item label="WBS任务" required
            ><a-select
              v-model:value="form.wbsTaskId"
              :options="
                overview.wbsTasks.map((row) => ({
                  value: row.id,
                  label: `${row.taskCode} · ${row.taskName}`,
                }))
              " /></a-form-item
          ><a-form-item label="质量验收记录" required
            ><a-select
              v-model:value="form.qualityInspectionId"
              :options="
                overview.qualityInspections.map((row) => ({
                  value: row.id,
                  label: `${row.inspectionCode} · ${row.conclusion}`,
                }))
              " /></a-form-item
        ></template>
        <template v-else-if="dialogKind === 'final'"
          ><a-form-item label="竣工验收编号" required
            ><a-input v-model:value="form.code" /></a-form-item
          ><a-form-item label="组织单位" required
            ><a-input v-model:value="form.organizer" /></a-form-item
          ><a-form-item label="参验单位/人员" required
            ><a-textarea v-model:value="form.participants" /></a-form-item
          ><a-form-item label="验收总结" required
            ><a-textarea v-model:value="form.summary" /></a-form-item
        ></template>
        <template v-else-if="dialogKind === 'settlement'"
          ><a-form-item label="已审批并生成应收的业主结算" required
            ><a-select
              v-model:value="form.ownerSettlementId"
              :options="
                settlementCandidates.map((row) => ({
                  value: String(row.id),
                  label: revenueText(row),
                }))
              " /></a-form-item
        ></template>
        <template v-else-if="dialogKind === 'warranty'"
          ><a-form-item label="质保金应收" required
            ><a-select
              :value="form.receivableId"
              :options="
                retentionReceivables.map((row) => ({
                  value: row.id,
                  label: `${row.receivableCode} · ¥${row.originalAmount}`,
                }))
              "
              @change="selectRetentionReceivable" /></a-form-item
          ><a-form-item label="质保编号" required><a-input v-model:value="form.code" /></a-form-item
          ><a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="开始日期"
                ><a-input v-model:value="form.startDate" type="date" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="截止日期"
                ><a-input v-model:value="form.endDate" type="date" /></a-form-item></a-col></a-row
        ></template>
        <template v-else-if="dialogKind === 'defect'"
          ><a-form-item label="缺陷编号" required><a-input v-model:value="form.code" /></a-form-item
          ><a-form-item label="缺陷标题" required><a-input v-model:value="form.name" /></a-form-item
          ><a-form-item label="缺陷描述" required
            ><a-textarea v-model:value="form.description" /></a-form-item
          ><a-form-item label="整改期限"
            ><a-input v-model:value="form.deadline" type="date" /></a-form-item
        ></template>
        <template v-else-if="dialogKind === 'rectify'"
          ><a-form-item label="整改内容" required
            ><a-textarea v-model:value="form.rectificationContent" :rows="4" /></a-form-item
        ></template>
        <template v-else-if="dialogKind === 'verifyDefect'"
          ><a-form-item label="复验结论"
            ><a-radio-group v-model:value="form.decision"
              ><a-radio value="ACCEPTED">通过并关闭</a-radio
              ><a-radio value="REJECTED">退回整改</a-radio></a-radio-group
            ></a-form-item
          ><a-form-item label="复验意见" required
            ><a-textarea v-model:value="form.comment" /></a-form-item
        ></template>
        <template v-else-if="dialogKind === 'archive'"
          ><a-form-item label="移交编号" required><a-input v-model:value="form.code" /></a-form-item
          ><a-row :gutter="16"
            ><a-col :span="12"
              ><a-form-item label="接收单位"
                ><a-input v-model:value="form.recipientOrganization" /></a-form-item></a-col
            ><a-col :span="12"
              ><a-form-item label="接收人"
                ><a-input v-model:value="form.recipientName" /></a-form-item></a-col></a-row
          ><a-form-item label="档案位置"
            ><a-input v-model:value="form.archiveLocation" /></a-form-item
          ><a-form-item label="移交范围"
            ><a-textarea v-model:value="form.transferScope" /></a-form-item
        ></template>
        <template v-else-if="dialogKind === 'close'"
          ><a-alert
            type="warning"
            show-icon
            message="项目关闭后业务数据锁定，不能重新开启。" /><a-form-item label="实际竣工日期"
            ><a-input v-model:value="form.date" type="date" /></a-form-item
          ><a-form-item label="关闭原因" required
            ><a-textarea v-model:value="form.reason" /></a-form-item
        ></template>
        <a-form-item
          v-if="
            [
              'section',
              'sectionConfirm',
              'final',
              'finalSubmit',
              'rectify',
              'releaseWarranty',
              'archive',
              'archiveAccept',
            ].includes(dialogKind)
          "
          label="业务证据附件"
          required
          ><input type="file" @change="setFile" />
          <div class="file-hint">附件在确认后锁定，禁止替换历史证据。</div></a-form-item
        >
        <a-form-item
          v-if="
            !['settlement', 'rectify', 'verifyDefect', 'releaseWarranty', 'close'].includes(
              dialogKind,
            )
          "
          label="备注"
          ><a-textarea v-model:value="form.remark"
        /></a-form-item>
      </a-form>
    </a-modal>

    <a-drawer v-model:open="traceOpen" title="项目竣工收尾全链路追溯" width="760">
      <a-descriptions v-if="trace" bordered :column="1" size="small">
        <a-descriptions-item label="收尾主档"
          >{{ trace.closeout.closeoutCode }} ·
          {{ statusName(trace.closeout.status) }}</a-descriptions-item
        >
        <a-descriptions-item label="分项验收"
          >{{ trace.sectionAcceptances.length }} 条</a-descriptions-item
        >
        <a-descriptions-item label="竣工验收审批留痕"
          >{{ trace.approvalRecords.length }} 条</a-descriptions-item
        >
        <a-descriptions-item label="结算应收"
          >{{ trace.receivables.length }} 条</a-descriptions-item
        >
        <a-descriptions-item label="回款分配"
          >{{ trace.collectionAllocations.length }} 条</a-descriptions-item
        >
        <a-descriptions-item label="质保责任">{{ trace.warranties.length }} 条</a-descriptions-item>
        <a-descriptions-item label="缺陷责任">{{ trace.defects.length }} 条</a-descriptions-item>
        <a-descriptions-item label="档案移交"
          >{{ trace.archiveTransfers.length }} 条</a-descriptions-item
        >
      </a-descriptions>
    </a-drawer>
  </div>
</template>

<style scoped>
.closeout-page {
  display: grid;
  gap: 18px;
}
.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 32px;
  padding: 30px;
  border-radius: 20px;
  color: #fff;
  background: linear-gradient(128deg, #102a43 0%, #1f5f74 58%, #2b7a78 100%);
  box-shadow: 0 14px 36px rgba(16, 42, 67, 0.18);
}
.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.18em;
  color: #9fe7e5;
}
h1 {
  margin: 0;
  font-size: 30px;
}
.hero-copy {
  max-width: 720px;
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.78);
}
.project-select {
  width: 360px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}
.project-select :deep(.ant-select) {
  width: 100%;
}
.project-select span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.kpi-card {
  padding: 18px 20px;
  border: 1px solid #e7eef2;
  border-radius: 16px;
  background: #fff;
}
.kpi-card span,
.kpi-card small {
  display: block;
  color: #718096;
}
.kpi-card strong {
  display: block;
  margin: 7px 0 4px;
  font-size: 26px;
  color: #153e4b;
}
.kpi-card .stage-text {
  font-size: 20px;
}
.ledger-card {
  border-radius: 18px;
}
.card-title div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-title span {
  font-size: 12px;
  font-weight: 400;
  color: #718096;
}
.file-hint {
  margin-top: 6px;
  color: #8c8c8c;
  font-size: 12px;
}
@media (max-width: 960px) {
  .hero-card {
    flex-direction: column;
  }
  .project-select {
    width: 100%;
  }
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 560px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
  .hero-card {
    padding: 22px;
  }
}
</style>
