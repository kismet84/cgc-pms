<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import { uploadFile } from '@/api/modules/file'
import { getPurchaseRequestList } from '@/api/modules/inventory'
import { getOrderList } from '@/api/modules/purchase'
import type { PurchaseRequestVO } from '@/types/inventory'
import type { MatPurchaseOrderVO } from '@/types/purchase'
import type { PartnerVO } from '@/types/partner'
import type { ContractVO } from '@/types/contract'
import {
  addSourcingSuppliers,
  awardSourcingEvent,
  confirmSupplierReturn,
  confirmSupplierPerformance,
  createBidEvaluation,
  createSourcingEvent,
  createSupplierBlacklist,
  createSupplierPerformance,
  createSupplierQuote,
  createSupplierReturn,
  declineSourcingSupplier,
  getBidEvaluations,
  getSourcingEvents,
  getSourcingSuppliers,
  getSourcingTrace,
  getSupplierPerformance,
  getSupplierQuotes,
  getSupplierReturns,
  linkSourcingContract,
  publishSourcingEvent,
  reviewSupplierBlacklist,
  startBidEvaluation,
  submitSupplierBlacklist,
  submitSupplierQuote,
  type BidEvaluation,
  type SourcingEvent,
  type SourcingSupplier,
  type SourcingTrace,
  type SupplierBlacklistRecord,
  type SupplierPerformanceEvaluation,
  type SupplierQuote,
  type SupplierReturn,
} from '@/api/modules/supplierSourcing'

const referenceStore = useReferenceStore()
const userStore = useUserStore()
const { projects } = storeToRefs(referenceStore)
const projectId = ref<string>()
const loading = ref(false)
const events = ref<SourcingEvent[]>([])
const selectedEventId = ref<string>()
const invitations = ref<SourcingSupplier[]>([])
const quotes = ref<SupplierQuote[]>([])
const evaluations = ref<BidEvaluation[]>([])
const performance = ref<SupplierPerformanceEvaluation[]>([])
const supplierReturns = ref<SupplierReturn[]>([])
const purchaseRequests = ref<PurchaseRequestVO[]>([])
const purchaseOrders = ref<MatPurchaseOrderVO[]>([])
const suppliers = ref<PartnerVO[]>([])
const contracts = ref<ContractVO[]>([])
const trace = ref<SourcingTrace>()
const traceOpen = ref(false)

const eventOpen = ref(false)
const inviteOpen = ref(false)
const quoteOpen = ref(false)
const evaluationOpen = ref(false)
const awardOpen = ref(false)
const contractOpen = ref(false)
const performanceOpen = ref(false)
const supplierReturnOpen = ref(false)
const blacklistOpen = ref(false)
const reviewOpen = ref(false)
const currentQuote = ref<SupplierQuote>()
const currentPerformance = ref<SupplierPerformanceEvaluation>()
const currentBlacklist = ref<SupplierBlacklistRecord>()
const currentReceipt = ref<SourcingTrace['receipts'][number]>()
const requirementFile = ref<File>()
const quoteFile = ref<File>()

const selectedEvent = computed(() => events.value.find((item) => item.id === selectedEventId.value))
const eligibleSuppliers = computed(() =>
  suppliers.value.filter(
    (item) => item.partnerType === 'SUPPLIER' && item.status === 'ENABLE' && !item.blacklistFlag,
  ),
)
const invitedPartnerIds = computed(() => new Set(invitations.value.map((item) => item.partnerId)))
const availableSuppliers = computed(() =>
  eligibleSuppliers.value.filter((item) => !invitedPartnerIds.value.has(item.id)),
)
const projectContracts = computed(() =>
  contracts.value.filter(
    (item) =>
      item.projectId === projectId.value &&
      item.contractType === 'PURCHASE' &&
      item.approvalStatus === 'APPROVED' &&
      item.contractStatus === 'PERFORMING',
  ),
)
const projectOrders = computed(() =>
  purchaseOrders.value.filter(
    (item) => item.projectId === projectId.value && item.approvalStatus === 'APPROVED',
  ),
)
const kpi = computed(() => ({
  active: events.value.filter((item) => ['PUBLISHED', 'EVALUATING'].includes(item.status)).length,
  awarded: events.value.filter((item) => ['AWARDED', 'CONTRACTED'].includes(item.status)).length,
  confirmed: performance.value.filter((item) => item.status === 'CONFIRMED').length,
  risk: performance.value.filter((item) => item.recommendBlacklist === 1).length,
}))

const defaultDeadline = () => {
  const date = new Date(Date.now() + 7 * 86400000)
  return date.toISOString().slice(0, 16)
}
const today = () => new Date().toISOString().slice(0, 10)
const eventForm = reactive({
  purchaseRequestId: '',
  sourcingCode: '',
  sourcingTitle: '',
  sourcingType: 'INQUIRY' as 'INQUIRY' | 'TENDER',
  deadline: defaultDeadline(),
  currencyCode: 'CNY',
  remark: '',
})
const inviteForm = reactive({ partnerIds: [] as string[] })
const quoteForm = reactive({
  partnerId: '',
  quoteCode: '',
  totalAmount: 0,
  taxRate: 13,
  deliveryDays: 7,
  validityDate: today(),
  commercialTerms: '',
})
const evaluationForm = reactive({
  commercialScore: 80,
  technicalScore: 80,
  deliveryScore: 80,
  qualityScore: 80,
  evaluationComment: '',
})
const awardForm = reactive({ quoteId: '', awardReason: '' })
const contractForm = reactive({ contractId: '' })
const performanceForm = reactive({ purchaseOrderId: '', serviceScore: 80, evaluationComment: '' })
const supplierReturnForm = reactive({
  returnCode: '',
  returnDate: today(),
  returnQuantity: 0,
  returnAmount: 0,
  reason: '',
})
const blacklistForm = reactive({ reason: '' })
const reviewForm = reactive({ decision: 'APPROVE' as 'APPROVE' | 'REJECT', comment: '' })

function can(permission: string) {
  return (
    userStore.hasPermission(permission) ||
    userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role))
  )
}
function partnerName(id?: string) {
  return suppliers.value.find((item) => item.id === id)?.partnerName ?? id ?? '-'
}
function requestName(id?: string) {
  return purchaseRequests.value.find((item) => item.id === id)?.requestCode ?? id ?? '-'
}
function statusName(status: string) {
  return (
    (
      {
        DRAFT: '草稿',
        PENDING: '待邀请',
        INVITED: '已邀请',
        PUBLISHED: '报价中',
        EVALUATING: '评审中',
        AWARDED: '已定标',
        CONTRACTED: '已关联合同',
        CANCELLED: '已取消',
        DECLINED: '已放弃',
        QUOTED: '已报价',
        SUBMITTED: '已提交',
        WINNER: '中标',
        LOST: '未中标',
        INVALID: '已失效',
        CONFIRMED: '已确认',
        APPROVED: '已通过',
        REJECTED: '已驳回',
      } as Record<string, string>
    )[status] ?? status
  )
}
function statusColor(status: string) {
  return (
    (
      {
        PUBLISHED: 'processing',
        EVALUATING: 'warning',
        AWARDED: 'blue',
        CONTRACTED: 'success',
        INVITED: 'processing',
        QUOTED: 'blue',
        DECLINED: 'default',
        SUBMITTED: 'processing',
        WINNER: 'success',
        LOST: 'default',
        CONFIRMED: 'success',
        APPROVED: 'success',
        REJECTED: 'error',
      } as Record<string, string>
    )[status] ?? 'default'
  )
}
function setRequirementFile(event: Event) {
  requirementFile.value = (event.target as HTMLInputElement).files?.[0]
}
function setQuoteFile(event: Event) {
  quoteFile.value = (event.target as HTMLInputElement).files?.[0]
}

async function loadProject() {
  events.value = []
  performance.value = []
  selectedEventId.value = undefined
  invitations.value = []
  quotes.value = []
  evaluations.value = []
  if (!projectId.value) return
  loading.value = true
  try {
    const [
      loadedEvents,
      loadedPerformance,
      loadedReturns,
      requestPage,
      orderPage,
      loadedSuppliers,
      loadedContracts,
    ] = await Promise.all([
      getSourcingEvents(projectId.value),
      getSupplierPerformance(projectId.value),
      getSupplierReturns(projectId.value),
      getPurchaseRequestList({ pageNum: 1, pageSize: 1000, projectId: projectId.value }),
      getOrderList({ pageNum: 1, pageSize: 1000, projectId: projectId.value }),
      referenceStore.fetchPartners({ partnerType: 'SUPPLIER' }),
      referenceStore.fetchContracts({ projectId: projectId.value }),
    ])
    events.value = loadedEvents
    performance.value = loadedPerformance
    supplierReturns.value = loadedReturns
    purchaseRequests.value = (requestPage.records ?? []).filter(
      (item) => item.approvalStatus === 'APPROVED',
    )
    purchaseOrders.value = orderPage.records ?? []
    suppliers.value = loadedSuppliers
    contracts.value = loadedContracts
    selectedEventId.value = events.value[0]?.id
    await loadEventDetail()
  } finally {
    loading.value = false
  }
}
async function loadEventDetail() {
  if (!selectedEventId.value) {
    invitations.value = []
    quotes.value = []
    evaluations.value = []
    return
  }
  ;[invitations.value, quotes.value, evaluations.value] = await Promise.all([
    getSourcingSuppliers(selectedEventId.value),
    getSupplierQuotes(selectedEventId.value),
    getBidEvaluations(selectedEventId.value),
  ])
}
async function selectEvent(row: SourcingEvent) {
  selectedEventId.value = row.id
  await loadEventDetail()
}

function openEvent() {
  if (!projectId.value) return message.warning('请先选择项目')
  Object.assign(eventForm, {
    purchaseRequestId: '',
    sourcingCode: `SRC-${today().replaceAll('-', '')}`,
    sourcingTitle: '',
    sourcingType: 'INQUIRY',
    deadline: defaultDeadline(),
    currencyCode: 'CNY',
    remark: '',
  })
  requirementFile.value = undefined
  eventOpen.value = true
}
async function saveEvent() {
  if (!projectId.value || !eventForm.purchaseRequestId || !eventForm.sourcingTitle)
    return message.warning('请完整填写招采事件')
  if (!requirementFile.value) return message.warning('必须上传采购需求或招标文件')
  const created = await createSourcingEvent({ projectId: projectId.value, ...eventForm })
  await uploadFile(requirementFile.value, 'SUPPLIER_SOURCING', created.id, 'SOURCING_REQUIREMENT')
  eventOpen.value = false
  await loadProject()
  selectedEventId.value = created.id
  await loadEventDetail()
  message.success('招采事件及采购需求文件已保存')
}
function openInvite(row: SourcingEvent) {
  selectedEventId.value = row.id
  inviteForm.partnerIds = []
  inviteOpen.value = true
}
async function saveInvite() {
  if (!selectedEventId.value || inviteForm.partnerIds.length === 0)
    return message.warning('请选择受邀供应商')
  await addSourcingSuppliers(selectedEventId.value, inviteForm.partnerIds)
  inviteOpen.value = false
  await loadEventDetail()
  message.success('受邀供应商已加入')
}
async function publish(row: SourcingEvent) {
  await publishSourcingEvent(row.id)
  message.success('询价/招标已发布')
  await loadProject()
}
async function decline(row: SourcingSupplier) {
  if (!selectedEventId.value) return
  Modal.confirm({
    title: '登记供应商放弃报价',
    content: `${partnerName(row.partnerId)}确认不参与本次报价，系统将保留响应记录。`,
    async onOk() {
      await declineSourcingSupplier(
        selectedEventId.value!,
        row.partnerId,
        '供应商确认不参与本次报价',
      )
      await loadEventDetail()
    },
  })
}
function openQuote(partnerId?: string) {
  if (!selectedEventId.value) return message.warning('请选择报价中的招采事件')
  Object.assign(quoteForm, {
    partnerId: partnerId ?? '',
    quoteCode: `QUO-${today().replaceAll('-', '')}`,
    totalAmount: 0,
    taxRate: 13,
    deliveryDays: 7,
    validityDate: selectedEvent.value?.deadline.slice(0, 10) ?? today(),
    commercialTerms: '',
  })
  quoteFile.value = undefined
  quoteOpen.value = true
}
async function saveQuote() {
  if (!selectedEventId.value || !quoteForm.partnerId || quoteForm.totalAmount <= 0)
    return message.warning('请完整填写有效报价')
  if (!quoteFile.value) return message.warning('提交报价前必须上传报价单')
  const created = await createSupplierQuote({
    sourcingEventId: selectedEventId.value,
    ...quoteForm,
  })
  await uploadFile(quoteFile.value, 'SUPPLIER_QUOTE', created.id, 'QUOTE_ATTACHMENT')
  await submitSupplierQuote(created.id)
  quoteOpen.value = false
  await loadEventDetail()
  message.success('报价单与附件已提交')
}
async function submitDraftQuote(row: SupplierQuote) {
  await submitSupplierQuote(row.id)
  await loadEventDetail()
}
async function startEvaluation(row: SourcingEvent) {
  await startBidEvaluation(row.id)
  message.success('已进入比价评审')
  await loadProject()
}
function openEvaluation(row: SupplierQuote) {
  currentQuote.value = row
  Object.assign(evaluationForm, {
    commercialScore: 80,
    technicalScore: 80,
    deliveryScore: 80,
    qualityScore: 80,
    evaluationComment: '',
  })
  evaluationOpen.value = true
}
async function saveEvaluation() {
  if (!currentQuote.value || !evaluationForm.evaluationComment)
    return message.warning('请填写完整评分和评审意见')
  await createBidEvaluation({ quoteId: currentQuote.value.id, ...evaluationForm })
  evaluationOpen.value = false
  await loadEventDetail()
  message.success('报价评审已留痕')
}
function openAward(row: SourcingEvent) {
  selectedEventId.value = row.id
  Object.assign(awardForm, { quoteId: evaluations.value[0]?.quoteId ?? '', awardReason: '' })
  awardOpen.value = true
}
async function saveAward() {
  if (!selectedEventId.value || !awardForm.quoteId || !awardForm.awardReason)
    return message.warning('请选择中标报价并填写定标依据')
  await awardSourcingEvent(selectedEventId.value, awardForm.quoteId, awardForm.awardReason)
  awardOpen.value = false
  await loadProject()
  message.success('定标完成，未中标报价已自动锁定')
}
function openContract(row: SourcingEvent) {
  selectedEventId.value = row.id
  contractForm.contractId = ''
  contractOpen.value = true
}
async function saveContract() {
  if (!selectedEventId.value || !contractForm.contractId) return message.warning('请选择采购合同')
  await linkSourcingContract(selectedEventId.value, contractForm.contractId)
  contractOpen.value = false
  await loadProject()
  message.success('中标结果已关联采购合同')
}
function openPerformance() {
  Object.assign(performanceForm, { purchaseOrderId: '', serviceScore: 80, evaluationComment: '' })
  performanceOpen.value = true
}
async function savePerformance() {
  if (!performanceForm.purchaseOrderId || !performanceForm.evaluationComment)
    return message.warning('请选择已完成履约的采购订单并填写评价意见')
  await createSupplierPerformance(
    performanceForm.purchaseOrderId,
    performanceForm.serviceScore,
    performanceForm.evaluationComment,
  )
  performanceOpen.value = false
  await loadProject()
  message.success('系统已汇总交付、质量、退货、结算和质量安全事实')
}
async function confirmPerformance(row: SupplierPerformanceEvaluation) {
  await confirmSupplierPerformance(row.id)
  await loadProject()
  message.success('履约评价已确认并锁定')
}
function openSupplierReturn(receipt: SourcingTrace['receipts'][number]) {
  currentReceipt.value = receipt
  Object.assign(supplierReturnForm, {
    returnCode: `SRT-${today().replaceAll('-', '')}`,
    returnDate: today(),
    returnQuantity: 0,
    returnAmount: 0,
    reason: '',
  })
  supplierReturnOpen.value = true
}
async function saveSupplierReturn() {
  if (!currentReceipt.value || supplierReturnForm.returnQuantity <= 0 || !supplierReturnForm.reason)
    return message.warning('请完整填写供应商退货事实')
  await createSupplierReturn({ receiptId: currentReceipt.value.id, ...supplierReturnForm })
  supplierReturnOpen.value = false
  if (projectId.value) supplierReturns.value = await getSupplierReturns(projectId.value)
  if (selectedEventId.value) trace.value = await getSourcingTrace(selectedEventId.value)
  message.success('供应商退货已登记，确认后纳入履约评价')
}
async function confirmReturn(row: SupplierReturn) {
  await confirmSupplierReturn(row.id)
  if (projectId.value) supplierReturns.value = await getSupplierReturns(projectId.value)
  if (selectedEventId.value) trace.value = await getSourcingTrace(selectedEventId.value)
  message.success('供应商退货已确认并锁定')
}
function openBlacklist(row: SupplierPerformanceEvaluation) {
  currentPerformance.value = row
  blacklistForm.reason = ''
  blacklistOpen.value = true
}
async function saveBlacklist() {
  if (!currentPerformance.value || !blacklistForm.reason)
    return message.warning('请填写黑名单申请原因')
  const created = await createSupplierBlacklist(currentPerformance.value.id, blacklistForm.reason)
  await submitSupplierBlacklist(created.id)
  blacklistOpen.value = false
  message.success('黑名单申请已提交，等待独立审核')
  if (selectedEventId.value) await openTraceById(selectedEventId.value)
}
function openReview(row: SupplierBlacklistRecord) {
  currentBlacklist.value = row
  Object.assign(reviewForm, { decision: 'APPROVE', comment: '' })
  reviewOpen.value = true
}
async function saveReview() {
  if (!currentBlacklist.value || !reviewForm.comment) return message.warning('请填写审核意见')
  await reviewSupplierBlacklist(currentBlacklist.value.id, reviewForm.decision, reviewForm.comment)
  reviewOpen.value = false
  message.success(reviewForm.decision === 'APPROVE' ? '供应商已纳入黑名单' : '黑名单申请已驳回')
  if (selectedEventId.value) await openTraceById(selectedEventId.value)
  await referenceStore
    .fetchPartners({ partnerType: 'SUPPLIER' })
    .then((rows) => (suppliers.value = rows))
}
async function openTraceById(id: string) {
  trace.value = await getSourcingTrace(id)
  traceOpen.value = true
}
async function openTrace(row: SourcingEvent) {
  selectedEventId.value = row.id
  await openTraceById(row.id)
}

onMounted(async () => {
  await referenceStore.fetchProjects()
  projectId.value =
    projects.value?.find((item) => item.status === 'ACTIVE')?.id ?? projects.value?.[0]?.id
  await loadProject()
})
</script>

<template>
  <div class="supplier-sourcing-page">
    <header class="hero">
      <div>
        <p class="eyebrow">PROCUREMENT · SUPPLIER GOVERNANCE</p>
        <h2>供应商招采与履约评价闭环</h2>
        <p>采购需求 → 询价/招标 → 比价评审 → 定标 → 合同 → 交付质量 → 结算 → 综合评价/黑名单</p>
      </div>
      <div class="hero-actions">
        <a-select
          v-model:value="projectId"
          show-search
          option-filter-prop="label"
          placeholder="选择项目"
          style="width: 260px"
          :options="(projects ?? []).map((item) => ({ label: item.projectName, value: item.id }))"
          @change="loadProject"
        />
        <a-button v-if="can('supplier:sourcing:maintain')" type="primary" @click="openEvent">
          新建询价/招标
        </a-button>
      </div>
    </header>

    <section class="kpi-grid">
      <div class="kpi">
        <span>执行中招采</span><strong>{{ kpi.active }}</strong>
      </div>
      <div class="kpi">
        <span>已定标/合同化</span><strong>{{ kpi.awarded }}</strong>
      </div>
      <div class="kpi">
        <span>已确认评价</span><strong>{{ kpi.confirmed }}</strong>
      </div>
      <div class="kpi risk">
        <span>黑名单建议</span><strong>{{ kpi.risk }}</strong>
      </div>
    </section>

    <a-card title="招采事件台账" :loading="loading" class="section-card">
      <a-table :data-source="events" row-key="id" :pagination="false" size="middle">
        <a-table-column title="招采编号" data-index="sourcingCode" width="150" />
        <a-table-column title="主题" data-index="sourcingTitle" />
        <a-table-column title="采购需求" width="150">
          <template #default="{ record }">{{ requestName(record.purchaseRequestId) }}</template>
        </a-table-column>
        <a-table-column title="方式" width="90">
          <template #default="{ record }">{{
            record.sourcingType === 'TENDER' ? '招标' : '询价'
          }}</template>
        </a-table-column>
        <a-table-column title="截止时间" data-index="deadline" width="180" />
        <a-table-column title="状态" width="120">
          <template #default="{ record }"
            ><a-tag :color="statusColor(record.status)">{{
              statusName(record.status)
            }}</a-tag></template
          >
        </a-table-column>
        <a-table-column title="操作" width="430" fixed="right">
          <template #default="{ record }">
            <a-space wrap>
              <a-button size="small" @click="selectEvent(record)">明细</a-button>
              <a-button
                v-if="record.status === 'DRAFT' && can('supplier:sourcing:maintain')"
                size="small"
                @click="openInvite(record)"
                >邀请供应商</a-button
              >
              <a-button
                v-if="record.status === 'DRAFT' && can('supplier:sourcing:maintain')"
                size="small"
                type="primary"
                @click="publish(record)"
                >发布</a-button
              >
              <a-button
                v-if="record.status === 'PUBLISHED' && can('supplier:sourcing:evaluate')"
                size="small"
                @click="startEvaluation(record)"
                >启动评审</a-button
              >
              <a-button
                v-if="record.status === 'EVALUATING' && can('supplier:sourcing:award')"
                size="small"
                type="primary"
                @click="openAward(record)"
                >定标</a-button
              >
              <a-button
                v-if="record.status === 'AWARDED' && can('supplier:sourcing:award')"
                size="small"
                type="primary"
                @click="openContract(record)"
                >关联合同</a-button
              >
              <a-button size="small" @click="openTrace(record)">全链路</a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <div v-if="selectedEvent" class="detail-grid">
      <a-card :title="`受邀供应商 · ${selectedEvent.sourcingCode}`" class="section-card">
        <a-table :data-source="invitations" row-key="id" :pagination="false" size="small">
          <a-table-column title="供应商"
            ><template #default="{ record }">{{
              partnerName(record.partnerId)
            }}</template></a-table-column
          >
          <a-table-column title="响应状态" width="110"
            ><template #default="{ record }"
              ><a-tag :color="statusColor(record.invitationStatus)">{{
                statusName(record.invitationStatus)
              }}</a-tag></template
            ></a-table-column
          >
          <a-table-column title="操作" width="180"
            ><template #default="{ record }"
              ><a-space
                ><a-button
                  v-if="
                    selectedEvent?.status === 'PUBLISHED' &&
                    ['PENDING', 'INVITED'].includes(record.invitationStatus) &&
                    can('supplier:sourcing:quote')
                  "
                  size="small"
                  @click="openQuote(record.partnerId)"
                  >录入报价</a-button
                ><a-button
                  v-if="
                    selectedEvent?.status === 'PUBLISHED' &&
                    ['PENDING', 'INVITED'].includes(record.invitationStatus)
                  "
                  size="small"
                  danger
                  @click="decline(record)"
                  >放弃</a-button
                ></a-space
              ></template
            ></a-table-column
          >
        </a-table>
      </a-card>

      <a-card title="报价与比价评审" class="section-card">
        <template #extra
          ><a-button
            v-if="selectedEvent.status === 'PUBLISHED' && can('supplier:sourcing:quote')"
            size="small"
            @click="openQuote()"
            >新增报价</a-button
          ></template
        >
        <a-table :data-source="quotes" row-key="id" :pagination="false" size="small">
          <a-table-column title="供应商"
            ><template #default="{ record }">{{
              partnerName(record.partnerId)
            }}</template></a-table-column
          >
          <a-table-column title="报价编号" data-index="quoteCode" />
          <a-table-column title="含税报价"
            ><template #default="{ record }"
              >¥ {{ Number(record.totalAmount).toLocaleString() }}</template
            ></a-table-column
          >
          <a-table-column title="交期" width="80"
            ><template #default="{ record }">{{ record.deliveryDays }}天</template></a-table-column
          >
          <a-table-column title="综合分" width="90"
            ><template #default="{ record }">{{
              evaluations.find((item) => item.quoteId === record.id)?.totalScore ?? '-'
            }}</template></a-table-column
          >
          <a-table-column title="状态" width="100"
            ><template #default="{ record }"
              ><a-tag :color="statusColor(record.status)">{{
                statusName(record.status)
              }}</a-tag></template
            ></a-table-column
          >
          <a-table-column title="操作" width="170"
            ><template #default="{ record }"
              ><a-space
                ><a-button
                  v-if="record.status === 'DRAFT'"
                  size="small"
                  @click="submitDraftQuote(record)"
                  >提交</a-button
                ><a-button
                  v-if="
                    selectedEvent?.status === 'EVALUATING' &&
                    record.status === 'SUBMITTED' &&
                    !evaluations.some((item) => item.quoteId === record.id) &&
                    can('supplier:sourcing:evaluate')
                  "
                  size="small"
                  type="primary"
                  @click="openEvaluation(record)"
                  >评分</a-button
                ></a-space
              ></template
            ></a-table-column
          >
        </a-table>
      </a-card>
    </div>

    <a-card title="供应商履约综合评价" class="section-card">
      <template #extra
        ><a-button
          v-if="can('supplier:performance:evaluate')"
          type="primary"
          @click="openPerformance"
          >生成评价</a-button
        ></template
      >
      <a-table :data-source="performance" row-key="id" :pagination="false" size="middle">
        <a-table-column title="评价编号" data-index="evaluationCode" width="170" />
        <a-table-column title="供应商"
          ><template #default="{ record }">{{
            partnerName(record.partnerId)
          }}</template></a-table-column
        >
        <a-table-column title="交付/质量/服务/商务"
          ><template #default="{ record }"
            >{{ record.deliveryScore }} / {{ record.qualityScore }} / {{ record.serviceScore }} /
            {{ record.commercialScore }}</template
          ></a-table-column
        >
        <a-table-column title="综合分" data-index="totalScore" width="90" />
        <a-table-column title="等级" data-index="grade" width="70" />
        <a-table-column title="事实来源" width="180"
          ><template #default="{ record }"
            >验收 {{ record.approvedReceiptCount }} · 退货 {{ record.returnCount }} · 结算
            {{ record.finalizedSettlementCount }} · 质量安全
            {{ record.qualitySafetyFactCount }}</template
          ></a-table-column
        >
        <a-table-column title="状态" width="100"
          ><template #default="{ record }"
            ><a-tag :color="statusColor(record.status)">{{
              statusName(record.status)
            }}</a-tag></template
          ></a-table-column
        >
        <a-table-column title="操作" width="210"
          ><template #default="{ record }"
            ><a-space
              ><a-button
                v-if="record.status === 'DRAFT' && can('supplier:performance:evaluate')"
                size="small"
                type="primary"
                @click="confirmPerformance(record)"
                >确认评价</a-button
              ><a-button
                v-if="
                  record.status === 'CONFIRMED' &&
                  record.recommendBlacklist === 1 &&
                  can('supplier:performance:evaluate')
                "
                size="small"
                danger
                @click="openBlacklist(record)"
                >黑名单申请</a-button
              ></a-space
            ></template
          ></a-table-column
        >
      </a-table>
    </a-card>

    <a-card title="供应商退货事实" class="section-card">
      <a-table :data-source="supplierReturns" row-key="id" :pagination="false" size="middle">
        <a-table-column title="退货编号" data-index="returnCode" width="170" />
        <a-table-column title="供应商"
          ><template #default="{ record }">{{
            partnerName(record.partnerId)
          }}</template></a-table-column
        >
        <a-table-column title="退货日期" data-index="returnDate" width="120" />
        <a-table-column title="数量/金额"
          ><template #default="{ record }"
            >{{ record.returnQuantity }} / ¥
            {{ Number(record.returnAmount).toLocaleString() }}</template
          ></a-table-column
        >
        <a-table-column title="原因" data-index="reason" />
        <a-table-column title="状态" width="100"
          ><template #default="{ record }"
            ><a-tag :color="statusColor(record.status)">{{
              statusName(record.status)
            }}</a-tag></template
          ></a-table-column
        >
        <a-table-column title="操作" width="100"
          ><template #default="{ record }"
            ><a-button
              v-if="record.status === 'DRAFT' && can('supplier:performance:evaluate')"
              size="small"
              type="primary"
              @click="confirmReturn(record)"
              >确认</a-button
            ></template
          ></a-table-column
        >
      </a-table>
    </a-card>

    <a-modal v-model:open="eventOpen" title="新建询价/招标" width="680px" @ok="saveEvent">
      <a-form layout="vertical"
        ><a-form-item label="采购需求" required
          ><a-select
            v-model:value="eventForm.purchaseRequestId"
            :options="
              purchaseRequests.map((item) => ({ label: item.requestCode, value: item.id }))
            " /></a-form-item
        ><a-row :gutter="16"
          ><a-col :span="12"
            ><a-form-item label="招采编号" required
              ><a-input v-model:value="eventForm.sourcingCode" /></a-form-item></a-col
          ><a-col :span="12"
            ><a-form-item label="招采方式" required
              ><a-select v-model:value="eventForm.sourcingType"
                ><a-select-option value="INQUIRY">询价</a-select-option
                ><a-select-option value="TENDER">招标</a-select-option></a-select
              ></a-form-item
            ></a-col
          ></a-row
        ><a-form-item label="招采主题" required
          ><a-input v-model:value="eventForm.sourcingTitle" /></a-form-item
        ><a-form-item label="报价截止时间" required
          ><a-input v-model:value="eventForm.deadline" type="datetime-local" /></a-form-item
        ><a-form-item label="采购需求/招标文件" required
          ><input type="file" @change="setRequirementFile" /></a-form-item
        ><a-form-item label="备注"><a-textarea v-model:value="eventForm.remark" /></a-form-item
      ></a-form>
    </a-modal>
    <a-modal v-model:open="inviteOpen" title="邀请合格供应商" @ok="saveInvite"
      ><a-alert
        message="发布前至少邀请三家启用且未列入黑名单的供应商"
        type="info"
        show-icon
        class="modal-alert" /><a-select
        v-model:value="inviteForm.partnerIds"
        mode="multiple"
        style="width: 100%"
        :options="availableSuppliers.map((item) => ({ label: item.partnerName, value: item.id }))"
    /></a-modal>
    <a-modal v-model:open="quoteOpen" title="录入供应商报价" width="680px" @ok="saveQuote"
      ><a-form layout="vertical"
        ><a-form-item label="供应商" required
          ><a-select
            v-model:value="quoteForm.partnerId"
            :options="
              invitations
                .filter((item) => ['PENDING', 'INVITED'].includes(item.invitationStatus))
                .map((item) => ({ label: partnerName(item.partnerId), value: item.partnerId }))
            " /></a-form-item
        ><a-row :gutter="16"
          ><a-col :span="12"
            ><a-form-item label="报价编号" required
              ><a-input v-model:value="quoteForm.quoteCode" /></a-form-item></a-col
          ><a-col :span="12"
            ><a-form-item label="含税总价" required
              ><a-input-number
                v-model:value="quoteForm.totalAmount"
                :min="0.01"
                style="width: 100%" /></a-form-item></a-col></a-row
        ><a-row :gutter="16"
          ><a-col :span="8"
            ><a-form-item label="税率(%)"
              ><a-input-number
                v-model:value="quoteForm.taxRate"
                :min="0"
                :max="100" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="交付天数"
              ><a-input-number
                v-model:value="quoteForm.deliveryDays"
                :min="0" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="报价有效期"
              ><a-input
                v-model:value="quoteForm.validityDate"
                type="date" /></a-form-item></a-col></a-row
        ><a-form-item label="商务条款" required
          ><a-textarea v-model:value="quoteForm.commercialTerms" :rows="3" /></a-form-item
        ><a-form-item label="报价单附件" required
          ><input type="file" @change="setQuoteFile" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="evaluationOpen" title="比价评审" @ok="saveEvaluation"
      ><a-row :gutter="12"
        ><a-col :span="12"
          ><a-form-item label="商务评分"
            ><a-input-number
              v-model:value="evaluationForm.commercialScore"
              :min="0"
              :max="100" /></a-form-item></a-col
        ><a-col :span="12"
          ><a-form-item label="技术评分"
            ><a-input-number
              v-model:value="evaluationForm.technicalScore"
              :min="0"
              :max="100" /></a-form-item></a-col
        ><a-col :span="12"
          ><a-form-item label="交付评分"
            ><a-input-number
              v-model:value="evaluationForm.deliveryScore"
              :min="0"
              :max="100" /></a-form-item></a-col
        ><a-col :span="12"
          ><a-form-item label="质量评分"
            ><a-input-number
              v-model:value="evaluationForm.qualityScore"
              :min="0"
              :max="100" /></a-form-item></a-col></a-row
      ><a-form-item label="评审意见" required
        ><a-textarea v-model:value="evaluationForm.evaluationComment" /></a-form-item
    ></a-modal>
    <a-modal v-model:open="awardOpen" title="定标决策" @ok="saveAward"
      ><a-form layout="vertical"
        ><a-form-item label="中标报价" required
          ><a-select
            v-model:value="awardForm.quoteId"
            :options="
              evaluations.map((item) => ({
                label: `${partnerName(item.partnerId)} · ${item.totalScore}分`,
                value: item.quoteId,
              }))
            " /></a-form-item
        ><a-form-item label="定标依据" required
          ><a-textarea v-model:value="awardForm.awardReason" :rows="4" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="contractOpen" title="关联中标采购合同" @ok="saveContract"
      ><a-alert
        message="合同必须已审批履约，项目一致且乙方为中标供应商"
        type="warning"
        show-icon
        class="modal-alert" /><a-select
        v-model:value="contractForm.contractId"
        style="width: 100%"
        :options="
          projectContracts
            .filter((item) => item.partyBId === selectedEvent?.awardedPartnerId)
            .map((item) => ({
              label: `${item.contractCode} · ${item.contractName}`,
              value: item.id,
            }))
        "
    /></a-modal>
    <a-modal v-model:open="performanceOpen" title="生成履约综合评价" @ok="savePerformance"
      ><a-form layout="vertical"
        ><a-form-item label="采购订单" required
          ><a-select
            v-model:value="performanceForm.purchaseOrderId"
            :options="
              projectOrders.map((item) => ({
                label: `${item.orderCode} · ${item.partnerName ?? partnerName(item.partnerId)}`,
                value: item.id,
              }))
            " /></a-form-item
        ><a-form-item label="服务协同评分"
          ><a-input-number
            v-model:value="performanceForm.serviceScore"
            :min="0"
            :max="100" /></a-form-item
        ><a-form-item label="评价意见" required
          ><a-textarea
            v-model:value="performanceForm.evaluationComment"
            :rows="4" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="supplierReturnOpen" title="登记供应商退货" @ok="saveSupplierReturn"
      ><a-alert
        message="退货必须来源于已审批验收单，确认后将进入履约评价且不可修改。"
        type="warning"
        show-icon
        class="modal-alert" /><a-form layout="vertical"
        ><a-form-item label="验收单">{{ currentReceipt?.receiptCode }}</a-form-item
        ><a-form-item label="退货编号" required
          ><a-input v-model:value="supplierReturnForm.returnCode" /></a-form-item
        ><a-row :gutter="16"
          ><a-col :span="8"
            ><a-form-item label="退货日期" required
              ><a-input
                v-model:value="supplierReturnForm.returnDate"
                type="date" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="退货数量" required
              ><a-input-number
                v-model:value="supplierReturnForm.returnQuantity"
                :min="0.0001" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="退货金额"
              ><a-input-number
                v-model:value="supplierReturnForm.returnAmount"
                :min="0" /></a-form-item></a-col></a-row
        ><a-form-item label="退货原因" required
          ><a-textarea v-model:value="supplierReturnForm.reason" :rows="3" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:open="blacklistOpen" title="发起供应商黑名单申请" @ok="saveBlacklist"
      ><a-alert
        message="仅低于准入红线且已确认的履约评价允许发起；审批通过后将立即阻断新合同和采购订单提交。"
        type="error"
        show-icon
        class="modal-alert" /><a-textarea
        v-model:value="blacklistForm.reason"
        :rows="4"
        placeholder="请说明客观事实与纳入原因"
    /></a-modal>
    <a-modal v-model:open="reviewOpen" title="审核供应商黑名单" @ok="saveReview"
      ><a-form layout="vertical"
        ><a-form-item label="审核决定"
          ><a-radio-group v-model:value="reviewForm.decision"
            ><a-radio value="APPROVE">通过</a-radio
            ><a-radio value="REJECT">驳回</a-radio></a-radio-group
          ></a-form-item
        ><a-form-item label="审核意见" required
          ><a-textarea v-model:value="reviewForm.comment" /></a-form-item></a-form
    ></a-modal>

    <a-drawer v-model:open="traceOpen" title="供应商招采履约全链路" width="760">
      <template v-if="trace"
        ><a-descriptions bordered :column="2" size="small"
          ><a-descriptions-item label="采购需求">{{
            trace.purchaseRequest?.requestCode
          }}</a-descriptions-item
          ><a-descriptions-item label="招采事件">{{ trace.event.sourcingCode }}</a-descriptions-item
          ><a-descriptions-item label="受邀/报价/评审"
            >{{ trace.invitedSuppliers.length }} / {{ trace.quotes.length }} /
            {{ trace.bidEvaluations.length }}</a-descriptions-item
          ><a-descriptions-item label="中标供应商">{{
            partnerName(trace.event.awardedPartnerId)
          }}</a-descriptions-item
          ><a-descriptions-item label="采购合同">{{
            trace.contract?.contractCode ?? '-'
          }}</a-descriptions-item
          ><a-descriptions-item label="订单/验收/退货/结算"
            >{{ trace.purchaseOrders.length }} / {{ trace.receipts.length }} /
            {{ trace.supplierReturns.length }} / {{ trace.settlements.length }}</a-descriptions-item
          ><a-descriptions-item label="履约评价">{{
            trace.performanceEvaluations.length
          }}</a-descriptions-item
          ><a-descriptions-item label="质量安全事实">{{
            trace.qualitySafetyFacts.length
          }}</a-descriptions-item></a-descriptions
        >
        <h3 class="drawer-title">验收与供应商退货</h3>
        <a-table :data-source="trace.receipts" row-key="id" :pagination="false" size="small"
          ><a-table-column title="验收单" data-index="receiptCode" /><a-table-column
            title="日期"
            data-index="receiptDate"
          /><a-table-column title="质量" data-index="qualityStatus" /><a-table-column title="操作"
            ><template #default="{ record }"
              ><a-button
                v-if="record.approvalStatus === 'APPROVED' && can('supplier:performance:evaluate')"
                size="small"
                @click="openSupplierReturn(record)"
                >登记退货</a-button
              ></template
            ></a-table-column
          ></a-table
        >
        <h3 class="drawer-title">黑名单审批记录</h3>
        <a-table :data-source="trace.blacklistRecords" row-key="id" :pagination="false" size="small"
          ><a-table-column title="供应商"
            ><template #default="{ record }">{{
              partnerName(record.partnerId)
            }}</template></a-table-column
          ><a-table-column title="原因" data-index="reason" /><a-table-column title="状态"
            ><template #default="{ record }"
              ><a-tag :color="statusColor(record.status)">{{
                statusName(record.status)
              }}</a-tag></template
            ></a-table-column
          ><a-table-column title="操作"
            ><template #default="{ record }"
              ><a-button
                v-if="record.status === 'SUBMITTED' && can('supplier:blacklist:review')"
                size="small"
                type="primary"
                @click="openReview(record)"
                >审核</a-button
              ></template
            ></a-table-column
          ></a-table
        ></template
      >
    </a-drawer>
  </div>
</template>

<style scoped>
.supplier-sourcing-page {
  padding: 20px;
  min-height: 100%;
  background: #f4f7fb;
}
.hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  padding: 26px 30px;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(120deg, #102a43, #176b87 58%, #1aa6a6);
  box-shadow: 0 12px 32px rgba(16, 42, 67, 0.18);
}
.hero h2 {
  margin: 4px 0 8px;
  color: #fff;
  font-size: 26px;
}
.hero p {
  margin: 0;
  opacity: 0.86;
}
.eyebrow {
  font-size: 12px;
  letter-spacing: 0.16em;
  opacity: 0.72 !important;
}
.hero-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 16px 0;
}
.kpi {
  padding: 18px 20px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e7edf4;
}
.kpi span {
  color: #65758b;
}
.kpi strong {
  display: block;
  margin-top: 7px;
  font-size: 26px;
  color: #17324d;
}
.kpi.risk strong {
  color: #c43b52;
}
.section-card {
  margin-bottom: 16px;
  border-radius: 12px;
  box-shadow: 0 5px 18px rgba(16, 42, 67, 0.06);
}
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 16px;
}
.modal-alert {
  margin-bottom: 16px;
}
.drawer-title {
  margin: 22px 0 12px;
}
@media (max-width: 1100px) {
  .hero {
    align-items: flex-start;
    flex-direction: column;
  }
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .supplier-sourcing-page {
    padding: 12px;
  }
  .hero {
    padding: 20px;
  }
  .hero-actions,
  .hero-actions :deep(.ant-select) {
    width: 100% !important;
  }
  .kpi-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
