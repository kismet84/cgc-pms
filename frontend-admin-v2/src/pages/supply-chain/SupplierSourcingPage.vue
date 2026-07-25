<script setup lang="ts">
import type {
  BidEvaluationCommand,
  ContractRecord,
  PartnerRecord,
  PurchaseOrderRecord,
  PurchaseRequestRecord,
  ReceiptRecord,
  SourcingEventCommand,
  SourcingEventRecord,
  SourcingTraceRecord,
  SupplierPerformanceRecord,
  SupplierQuoteCommand,
  SupplierReturnCommand,
  SupplierReturnRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2Dialog,
  V2GlassButton,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
  useToastMessage,
} from '@/components'
import {
  awardSourcingEvent,
  confirmSupplierPerformance,
  confirmSupplierReturn,
  createBidEvaluation,
  createSourcingEvent,
  createSupplierBlacklist,
  createSupplierPerformance,
  createSupplierQuote,
  createSupplierReturn,
  declineSourcingSupplier,
  inviteSourcingSuppliers,
  linkSourcingContract,
  loadPurchaseOrders,
  loadPurchaseRequests,
  loadReceipts,
  loadSourcingEvents,
  loadSourcingTrace,
  loadSupplierPerformance,
  loadSupplierReturns,
  publishSourcingEvent,
  reviewSupplierBlacklist,
  startSourcingEvaluation,
  submitSupplierBlacklist,
  submitSupplierQuote,
} from '@/services/supply-chain'
import { loadContractPage, loadPartners } from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

type Action =
  | 'event'
  | 'invite'
  | 'decline'
  | 'quote'
  | 'evaluate'
  | 'award'
  | 'contract'
  | 'performance'
  | 'return'
  | 'blacklist'
  | 'review'
  | null

const session = useSessionStore()
const workspace = useWorkspaceStore()
const events = ref<SourcingEventRecord[]>([])
const trace = ref<SourcingTraceRecord | null>(null)
const performance = ref<SupplierPerformanceRecord[]>([])
const returns = ref<SupplierReturnRecord[]>([])
const partners = ref<PartnerRecord[]>([])
const purchaseRequests = ref<PurchaseRequestRecord[]>([])
const purchaseOrders = ref<PurchaseOrderRecord[]>([])
const receipts = ref<ReceiptRecord[]>([])
const contracts = ref<ContractRecord[]>([])
const selectedId = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const action = ref<Action>(null)
const targetId = ref('')
const partnerId = ref('')
const form = reactive<Record<string, string>>({})
let listController: AbortController | null = null
let traceController: AbortController | null = null
let optionController: AbortController | null = null
let listGeneration = 0
let traceGeneration = 0

const projectId = computed(() => workspace.selectedProjectId || '')
const canMaintain = computed(() => session.hasPermission('supplier:sourcing:maintain'))
const canQuote = computed(() => session.hasPermission('supplier:sourcing:quote'))
const canEvaluate = computed(() => session.hasPermission('supplier:sourcing:evaluate'))
const canAward = computed(() => session.hasPermission('supplier:sourcing:award'))
const canPerformance = computed(() => session.hasPermission('supplier:performance:evaluate'))
const canReview = computed(() => session.hasPermission('supplier:blacklist:review'))
const selected = computed(() => events.value.find((item) => item.id === selectedId.value) ?? null)
const partnerOptions = computed(() =>
  partners.value.map((item) => ({
    value: item.id,
    label: `${item.partnerCode} · ${item.partnerName}`,
  })),
)
const purchaseRequestOptions = computed(() =>
  purchaseRequests.value.map((item) => ({
    value: item.id,
    label: `${item.requestCode || '采购需求编号缺失'} · ${item.purpose || item.contractName || '用途未填写'}`,
  })),
)
const contractOptions = computed(() =>
  contracts.value.map((item) => ({
    value: item.id,
    label: `${item.contractCode} · ${item.contractName}`,
  })),
)
const purchaseOrderOptions = computed(() =>
  purchaseOrders.value.map((item) => ({
    value: item.id,
    label: `${item.orderCode || '采购订单编号缺失'} · ${item.partnerName || '供应商信息缺失'}`,
  })),
)
const receiptOptions = computed(() =>
  receipts.value.map((item) => ({
    value: item.id,
    label: `${item.receiptCode || '验收单编号缺失'} · ${item.orderCode || '采购订单信息缺失'}`,
  })),
)
const dialogTitle = computed(
  () =>
    ({
      event: '新建招采事件',
      invite: '邀请供应商',
      decline: '拒绝邀请',
      quote: '登记报价',
      evaluate: '评审报价',
      award: '定标',
      contract: '关联合同',
      performance: '履约评价',
      return: '登记退货',
      blacklist: '发起黑名单',
      review: '审核黑名单',
    })[action.value ?? ''] ?? '',
)

function partnerLabel(id: string): string {
  const partner = partners.value.find((item) => item.id === id)
  return partner ? `${partner.partnerCode} · ${partner.partnerName}` : '供应商信息缺失'
}

function quoteCode(id: string): string {
  return trace.value?.quotes.find((item) => item.id === id)?.quoteCode ?? '报价信息缺失'
}

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

function required(name: string, label: string): string {
  const value = form[name]?.trim() ?? ''
  if (!value) throw new TypeError(`${label}不能为空`)
  return value
}

function decimal(name: string, label: string): string {
  const value = required(name, label)
  if (!/^\d+(?:\.\d+)?$/.test(value)) throw new TypeError(`${label}必须为非负十进制数`)
  return value
}

async function show(next: Exclude<Action, null>, id = '', supplier = ''): Promise<void> {
  action.value = next
  targetId.value = id
  partnerId.value = supplier
  for (const key of Object.keys(form)) delete form[key]
  if (next === 'event')
    Object.assign(form, {
      projectId: projectId.value,
      sourcingType: 'INQUIRY',
      currencyCode: 'CNY',
      deadline: new Date(Date.now() + 7 * 86_400_000).toISOString().slice(0, 16),
    })
  if (next === 'quote')
    Object.assign(form, { partnerId: supplier, taxRate: '0', deliveryDays: '0' })
  if (next === 'evaluate')
    Object.assign(form, {
      commercialScore: '0',
      technicalScore: '0',
      deliveryScore: '0',
      qualityScore: '0',
    })
  if (next === 'review') form.decision = 'APPROVE'
  if (next === 'return') form.returnDate = new Date().toISOString().slice(0, 10)
  if (!['event', 'contract', 'performance', 'return'].includes(next)) return

  optionController?.abort()
  const controller = new AbortController()
  optionController = controller
  busy.value = true
  try {
    if (next === 'event') {
      const page = await loadPurchaseRequests(
        { pageNum: 1, pageSize: 200, projectId: projectId.value || undefined },
        controller.signal,
      )
      purchaseRequests.value = page.records
    } else if (next === 'contract') {
      const page = await loadContractPage(
        { pageNo: 1, pageSize: 200, projectId: selected.value?.projectId || undefined },
        controller.signal,
      )
      contracts.value = page.records
    } else if (next === 'performance') {
      const page = await loadPurchaseOrders(
        { pageNum: 1, pageSize: 200, projectId: projectId.value || undefined },
        controller.signal,
      )
      purchaseOrders.value = page.records
    } else {
      const page = await loadReceipts(
        { pageNum: 1, pageSize: 200, projectId: projectId.value || undefined },
        controller.signal,
      )
      receipts.value = page.records
    }
  } catch (error) {
    if (!controller.signal.aborted) {
      errorMessage.value = errorText(error, '业务候选项加载失败')
      showToast('error', '候选项读取失败', errorMessage.value)
    }
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}

async function loadPage(): Promise<void> {
  listController?.abort()
  traceController?.abort()
  trace.value = null
  selectedId.value = ''
  const projectIds = projectId.value
    ? [projectId.value]
    : workspace.projects.map((project) => project.value)
  if (!projectIds.length) {
    events.value = []
    performance.value = []
    returns.value = []
    return
  }
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  errorMessage.value = ''
  try {
    const [partnerPage, results] = await Promise.all([
      loadPartners(
        { pageNo: 1, pageSize: 200, partnerType: 'SUPPLIER', status: 'ENABLE' },
        controller.signal,
      ),
      Promise.all(
        projectIds.map(async (currentProjectId) =>
          Promise.all([
            loadSourcingEvents(currentProjectId, controller.signal),
            loadSupplierPerformance(currentProjectId, controller.signal),
            loadSupplierReturns(currentProjectId, controller.signal),
          ]),
        ),
      ),
    ])
    if (generation !== listGeneration) return
    partners.value = partnerPage.records
    events.value = results.flatMap(([nextEvents]) => nextEvents)
    performance.value = results.flatMap(([, nextPerformance]) => nextPerformance)
    returns.value = results.flatMap(([, , nextReturns]) => nextReturns)
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      events.value = []
      performance.value = []
      returns.value = []
      errorMessage.value = errorText(error, '供应商招采数据加载失败')
      showToast('error', '供应商招采读取失败', errorMessage.value)
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function selectEvent(id: string): Promise<void> {
  selectedId.value = id
  traceController?.abort()
  const controller = new AbortController()
  traceController = controller
  const generation = ++traceGeneration
  detailLoading.value = true
  try {
    const value = await loadSourcingTrace(id, controller.signal)
    if (generation === traceGeneration) trace.value = value
  } catch (error) {
    if (!controller.signal.aborted && generation === traceGeneration) {
      trace.value = null
      errorMessage.value = errorText(error, '招采追溯加载失败')
      showToast('error', '招采追溯读取失败', errorMessage.value)
    }
  } finally {
    if (generation === traceGeneration) detailLoading.value = false
  }
}

async function act(task: () => Promise<unknown>, message: string): Promise<void> {
  if (busy.value) return
  const eventId = selectedId.value
  busy.value = true
  errorMessage.value = ''
  try {
    await task()
    await loadPage()
    if (eventId && events.value.some((item) => item.id === eventId)) await selectEvent(eventId)
    if (!errorMessage.value) successMessage.value = message
  } catch (error) {
    errorMessage.value = errorText(error, '操作失败')
    showToast('error', '操作失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function save(): Promise<void> {
  if (!action.value || busy.value) return
  const current = action.value
  await act(async () => {
    if (current === 'event') {
      const payload: SourcingEventCommand = {
        projectId: required('projectId', '项目'),
        purchaseRequestId: required('purchaseRequestId', '采购需求'),
        sourcingCode: required('sourcingCode', '招采编号'),
        sourcingTitle: required('sourcingTitle', '招采主题'),
        sourcingType: form.sourcingType as 'INQUIRY' | 'TENDER',
        deadline: required('deadline', '报价截止时间'),
        currencyCode: required('currencyCode', '币种'),
        remark: form.remark?.trim() || undefined,
      }
      await createSourcingEvent(payload)
    } else if (current === 'invite') {
      await inviteSourcingSuppliers(targetId.value, [required('partnerId', '供应商')])
    } else if (current === 'decline') {
      await declineSourcingSupplier(
        selectedId.value,
        partnerId.value,
        required('reason', '拒绝原因'),
      )
    } else if (current === 'quote') {
      const payload: SupplierQuoteCommand = {
        sourcingEventId: selectedId.value,
        partnerId: required('partnerId', '供应商'),
        quoteCode: required('quoteCode', '报价编号'),
        totalAmount: decimal('totalAmount', '含税总价'),
        taxRate: decimal('taxRate', '税率'),
        deliveryDays: Number(required('deliveryDays', '交付天数')),
        validityDate: required('validityDate', '报价有效期'),
        commercialTerms: required('commercialTerms', '商务条款'),
        remark: form.remark?.trim() || undefined,
      }
      if (!Number.isInteger(payload.deliveryDays) || payload.deliveryDays < 0)
        throw new TypeError('交付天数必须为非负整数')
      await createSupplierQuote(payload)
    } else if (current === 'evaluate') {
      const payload: BidEvaluationCommand = {
        quoteId: targetId.value,
        commercialScore: decimal('commercialScore', '商务评分'),
        technicalScore: decimal('technicalScore', '技术评分'),
        deliveryScore: decimal('deliveryScore', '交付评分'),
        qualityScore: decimal('qualityScore', '质量评分'),
        evaluationComment: required('evaluationComment', '评审意见'),
      }
      await createBidEvaluation(payload)
    } else if (current === 'award') {
      await awardSourcingEvent(
        selectedId.value,
        targetId.value,
        required('awardReason', '定标依据'),
      )
    } else if (current === 'contract') {
      await linkSourcingContract(selectedId.value, required('contractId', '合同'))
    } else if (current === 'performance') {
      await createSupplierPerformance(
        required('purchaseOrderId', '采购订单'),
        decimal('serviceScore', '服务协同评分'),
        required('evaluationComment', '评价意见'),
      )
    } else if (current === 'return') {
      const payload: SupplierReturnCommand = {
        receiptId: required('receiptId', '验收单'),
        returnCode: required('returnCode', '退货编号'),
        returnDate: required('returnDate', '退货日期'),
        returnQuantity: decimal('returnQuantity', '退货数量'),
        returnAmount: decimal('returnAmount', '退货金额'),
        reason: required('reason', '退货原因'),
      }
      await createSupplierReturn(payload)
    } else if (current === 'blacklist') {
      await createSupplierBlacklist(targetId.value, required('reason', '列入原因'))
    } else if (current === 'review') {
      await reviewSupplierBlacklist(
        targetId.value,
        form.decision as 'APPROVE' | 'REJECT',
        required('comment', '审核意见'),
      )
    }
  }, '操作已完成，状态已更新。')
  if (!errorMessage.value) action.value = null
}

function label(status?: string | null): string {
  const labels: Record<string, string> = {
    INQUIRY: '询价',
    TENDER: '招标',
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    EVALUATING: '评审中',
    AWARDED: '已定标',
    CONTRACTED: '已关联合同',
    INVITED: '已邀请',
    DECLINED: '已拒绝',
    QUOTED: '已报价',
    SUBMITTED: '已提交',
    WINNER: '中标',
    LOST: '未中标',
    CONFIRMED: '已确认',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
    PENDING: '待处理',
  }
  return status ? (labels[status] ?? '未知状态') : '未知状态'
}

function closeTrace(): void {
  traceController?.abort()
  selectedId.value = ''
  trace.value = null
}

watch(
  () => [projectId.value, ...workspace.projects.map((project) => project.value)],
  () => void loadPage(),
  { immediate: true },
)
onBeforeUnmount(() => {
  listController?.abort()
  traceController?.abort()
  optionController?.abort()
})
</script>

<template>
  <section class="supplier-page" aria-label="供应商招采履约">
    <V2PageState
      v-if="loading"
      kind="loading"
      :heading-level="1"
      title="正在加载供应商招采"
      description="读取事件、履约评价和退货事实。"
    />
    <template v-else>
      <V2Card title="供应商招采履约" :heading-level="1">
        <template #actions>
          <V2Button v-if="canMaintain" size="small" @click="show('event')">新建招采事件</V2Button>
          <V2Button
            v-if="canPerformance"
            size="small"
            variant="secondary"
            @click="show('performance')"
            >登记履约评价</V2Button
          >
          <V2Button v-if="canPerformance" size="small" variant="secondary" @click="show('return')"
            >登记退货</V2Button
          >
        </template>
      </V2Card>

      <V2PageState
        v-if="!errorMessage && !events.length"
        kind="empty"
        title="暂无招采事件"
        description="当前项目范围尚无可访问招采事件。"
      />
      <V2Card v-else title="招采事件">
        <template #title-extra
          ><V2Badge>事件 {{ events.length }}</V2Badge></template
        >
        <div class="supplier-page__table-wrap">
          <table class="v2-table--top" aria-label="招采事件列表">
            <thead>
              <tr>
                <th>编号</th>
                <th>主题</th>
                <th>方式</th>
                <th>截止时间</th>
                <th class="v2-table-cell--status">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in events" :key="item.id">
                <th scope="row">
                  <V2Button
                    size="small"
                    variant="ghost"
                    class="v2-table__record-link"
                    @click="selectEvent(item.id)"
                  >
                    {{ item.sourcingCode }}
                  </V2Button>
                </th>
                <td class="v2-table-cell--wrap">{{ item.sourcingTitle }}</td>
                <td>{{ label(item.sourcingType) }}</td>
                <td>{{ item.deadline }}</td>
                <td class="v2-table-cell--status">
                  <V2Badge>{{ label(item.status) }}</V2Badge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>

      <V2Dialog
        :open="Boolean(selectedId) && !action"
        title="招采闭环追溯"
        :description="selected ? `${selected.sourcingCode} · ${selected.sourcingTitle}` : ''"
        panel-class="v2-detail-dialog"
        :close-on-backdrop="true"
        @close="closeTrace"
      >
        <V2PageState
          v-if="detailLoading"
          kind="loading"
          title="正在加载闭环追溯"
          description="正在读取最新状态。"
        />
        <div v-else-if="selected && trace" class="supplier-page__trace">
          <div class="v2-detail-dialog__quick-actions">
            <V2Button
              v-if="canMaintain && selected.status === 'DRAFT'"
              type="button"
              size="small"
              @click="show('invite', selected.id)"
              >邀请供应商</V2Button
            >
            <V2Button
              v-if="canMaintain && selected.status === 'DRAFT'"
              type="button"
              size="small"
              :loading="busy"
              @click="act(() => publishSourcingEvent(selected.id), '招采事件已发布。')"
              >发布</V2Button
            >
            <V2Button
              v-if="canEvaluate && selected.status === 'PUBLISHED'"
              type="button"
              size="small"
              @click="act(() => startSourcingEvaluation(selected.id), '已进入评审。')"
              >开始评审</V2Button
            >
            <V2Button
              v-if="canAward && selected.status === 'AWARDED'"
              type="button"
              size="small"
              @click="show('contract', selected.id)"
              >关联合同</V2Button
            >
          </div>

          <section class="v2-detail-dialog__section">
            <h3>受邀供应商</h3>
            <div
              class="supplier-page__table-wrap"
              role="region"
              aria-label="受邀供应商表格"
              tabindex="0"
            >
              <table aria-label="受邀供应商">
                <thead>
                  <tr>
                    <th>供应商编号</th>
                    <th>邀请状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in trace.invitedSuppliers" :key="item.id">
                    <th scope="row">{{ partnerLabel(item.partnerId) }}</th>
                    <td>{{ label(item.invitationStatus) }}</td>
                    <td>
                      <V2Button
                        v-if="
                          canQuote &&
                          selected.status === 'PUBLISHED' &&
                          ['INVITED', 'PENDING'].includes(item.invitationStatus)
                        "
                        type="button"
                        size="small"
                        @click="show('quote', item.id, item.partnerId)"
                        >登记报价</V2Button
                      >
                      <V2Button
                        v-if="
                          canQuote &&
                          selected.status === 'PUBLISHED' &&
                          ['INVITED', 'PENDING'].includes(item.invitationStatus)
                        "
                        type="button"
                        size="small"
                        variant="ghost"
                        @click="show('decline', item.id, item.partnerId)"
                        >拒绝</V2Button
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="v2-detail-dialog__section">
            <h3>供应商报价</h3>
            <div
              class="supplier-page__table-wrap"
              role="region"
              aria-label="供应商报价表格"
              tabindex="0"
            >
              <table aria-label="供应商报价">
                <thead>
                  <tr>
                    <th>报价编号</th>
                    <th>供应商</th>
                    <th>金额</th>
                    <th>币种</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in trace.quotes" :key="item.id">
                    <th scope="row">{{ item.quoteCode }}</th>
                    <td>{{ partnerLabel(item.partnerId) }}</td>
                    <td>{{ item.totalAmount }}</td>
                    <td>{{ selected.currencyCode }}</td>
                    <td>{{ label(item.status) }}</td>
                    <td>
                      <V2Button
                        v-if="canQuote && item.status === 'DRAFT'"
                        type="button"
                        size="small"
                        @click="act(() => submitSupplierQuote(item.id), '报价已提交。')"
                        >提交报价</V2Button
                      >
                      <V2Button
                        v-if="
                          canEvaluate &&
                          selected.status === 'EVALUATING' &&
                          item.status === 'SUBMITTED' &&
                          !trace.bidEvaluations.some((entry) => entry.quoteId === item.id)
                        "
                        type="button"
                        size="small"
                        @click="show('evaluate', item.id)"
                        >评审</V2Button
                      >
                      <V2Button
                        v-if="
                          canAward &&
                          selected.status === 'EVALUATING' &&
                          trace.bidEvaluations.some((entry) => entry.quoteId === item.id)
                        "
                        type="button"
                        size="small"
                        @click="show('award', item.id)"
                        >定标</V2Button
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="v2-detail-dialog__section">
            <h3>报价评审</h3>
            <div
              class="supplier-page__table-wrap"
              role="region"
              aria-label="报价评审表格"
              tabindex="0"
            >
              <table aria-label="报价评审">
                <thead>
                  <tr>
                    <th>报价编号</th>
                    <th>供应商</th>
                    <th>总分</th>
                    <th>意见</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in trace.bidEvaluations" :key="item.id">
                    <th scope="row">{{ quoteCode(item.quoteId) }}</th>
                    <td>{{ partnerLabel(item.partnerId) }}</td>
                    <td>{{ item.totalScore }}</td>
                    <td class="v2-table-cell--wrap">{{ item.evaluationComment }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section class="v2-detail-dialog__section">
            <h3>合同与履约</h3>
            <dl class="v2-detail-dialog__facts">
              <div>
                <dt>关联合同</dt>
                <dd>
                  {{
                    trace.contract?.contractName ||
                    trace.contract?.contractCode ||
                    (selected.contractId ? '合同信息缺失' : '尚未关联')
                  }}
                </dd>
              </div>
              <div>
                <dt>履约事实</dt>
                <dd>
                  订单 {{ trace.purchaseOrders.length }} · 验收 {{ trace.receipts.length }} · 结算
                  {{ trace.settlements.length }} · 质量安全 {{ trace.qualitySafetyFacts.length }}
                </dd>
              </div>
            </dl>
          </section>
        </div>
      </V2Dialog>

      <V2Card title="履约评价、退货与黑名单">
        <template #title-extra>
          <div class="supplier-page__facts" aria-label="供应商履约与退货概览">
            <V2Badge>评价 {{ performance.length }}</V2Badge>
            <V2Badge tone="warning">退货 {{ returns.length }}</V2Badge>
          </div>
        </template>
        <div class="supplier-page__grid">
          <section>
            <h3>履约评价</h3>
            <table aria-label="供应商履约评价">
              <thead>
                <tr>
                  <th>评价编号</th>
                  <th>供应商</th>
                  <th>总分</th>
                  <th>等级</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in performance" :key="item.id">
                  <th scope="row">{{ item.evaluationCode }}</th>
                  <td>{{ partnerLabel(item.partnerId) }}</td>
                  <td>{{ item.totalScore }}</td>
                  <td>{{ item.grade }}</td>
                  <td>{{ label(item.status) }}</td>
                  <td>
                    <V2Button
                      v-if="canPerformance && item.status === 'DRAFT'"
                      size="small"
                      @click="act(() => confirmSupplierPerformance(item.id), '履约评价已确认。')"
                      >确认</V2Button
                    >
                    <V2Button
                      v-if="
                        canPerformance &&
                        item.status === 'CONFIRMED' &&
                        item.recommendBlacklist === 1
                      "
                      size="small"
                      variant="secondary"
                      @click="show('blacklist', item.id)"
                      >发起黑名单</V2Button
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </section>
          <section>
            <h3>退货</h3>
            <table aria-label="供应商退货">
              <thead>
                <tr>
                  <th>退货编号</th>
                  <th>供应商</th>
                  <th>数量</th>
                  <th>金额</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in returns" :key="item.id">
                  <th scope="row">{{ item.returnCode }}</th>
                  <td>{{ partnerLabel(item.partnerId) }}</td>
                  <td>{{ item.returnQuantity }}</td>
                  <td>{{ item.returnAmount }}</td>
                  <td>{{ label(item.status) }}</td>
                  <td>
                    <V2Button
                      v-if="canPerformance && item.status === 'DRAFT'"
                      size="small"
                      @click="act(() => confirmSupplierReturn(item.id), '退货已确认。')"
                      >确认</V2Button
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>
        <section v-if="trace?.blacklistRecords.length">
          <h3>黑名单记录</h3>
          <table aria-label="供应商黑名单">
            <thead>
              <tr>
                <th>供应商编号</th>
                <th>原因</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in trace.blacklistRecords" :key="item.id">
                <th scope="row">{{ partnerLabel(item.partnerId) }}</th>
                <td class="v2-table-cell--wrap">{{ item.reason }}</td>
                <td>{{ label(item.status) }}</td>
                <td>
                  <V2Button
                    v-if="canPerformance && item.status === 'DRAFT'"
                    size="small"
                    @click="act(() => submitSupplierBlacklist(item.id), '黑名单申请已提交。')"
                    >提交</V2Button
                  >
                  <V2Button
                    v-if="canReview && item.status === 'SUBMITTED'"
                    size="small"
                    @click="show('review', item.id)"
                    >审核</V2Button
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </V2Card>
    </template>

    <V2Dialog
      :open="Boolean(action)"
      :title="dialogTitle"
      :close-disabled="busy"
      :close-on-backdrop="false"
      panel-class="supplier-action-dialog"
      @close="action = null"
    >
      <form id="supplier-action-form" class="supplier-page__form" @submit.prevent="save">
        <template v-if="action === 'event'">
          <V2Select
            v-model="form.projectId"
            label="项目"
            :options="workspace.projects"
            :disabled="Boolean(projectId)"
            required
          />
          <V2Select
            v-model="form.purchaseRequestId"
            label="采购需求"
            :options="purchaseRequestOptions"
            placeholder="选择采购需求"
            required
          />
          <V2Input v-model="form.sourcingCode" label="招采编号" required />
          <V2Input v-model="form.sourcingTitle" label="招采主题" required />
          <V2Select
            v-model="form.sourcingType"
            label="招采方式"
            :options="[
              { value: 'INQUIRY', label: '询价' },
              { value: 'TENDER', label: '招标' },
            ]"
            required
          />
          <V2Input
            v-model="form.deadline"
            label="报价截止时间"
            placeholder="YYYY-MM-DDTHH:mm"
            required
          />
          <V2Input v-model="form.currencyCode" label="币种" required />
          <label class="supplier-page__wide">备注<textarea v-model="form.remark" /></label>
        </template>
        <V2Select
          v-if="action === 'invite'"
          v-model="form.partnerId"
          label="供应商"
          :options="partnerOptions"
          placeholder="选择供应商"
          required
        />
        <label v-if="action === 'decline'" class="supplier-page__wide"
          >拒绝原因<textarea v-model="form.reason" required />
        </label>
        <template v-if="action === 'quote'">
          <V2Select
            v-model="form.partnerId"
            label="供应商"
            :options="partnerOptions"
            disabled
            required
          /><V2Input v-model="form.quoteCode" label="报价编号" required />
          <V2Input v-model="form.totalAmount" label="含税总价" required /><V2Input
            v-model="form.taxRate"
            label="税率(%)"
            required
          />
          <V2Input v-model="form.deliveryDays" label="交付天数" required /><label
            >报价有效期<input v-model="form.validityDate" type="date" required
          /></label>
          <label class="supplier-page__wide"
            >商务条款<textarea v-model="form.commercialTerms" required />
          </label>
        </template>
        <template v-if="action === 'evaluate'">
          <V2Input v-model="form.commercialScore" label="商务评分" required /><V2Input
            v-model="form.technicalScore"
            label="技术评分"
            required
          />
          <V2Input v-model="form.deliveryScore" label="交付评分" required /><V2Input
            v-model="form.qualityScore"
            label="质量评分"
            required
          />
          <label class="supplier-page__wide"
            >评审意见<textarea v-model="form.evaluationComment" required />
          </label>
        </template>
        <label v-if="action === 'award'" class="supplier-page__wide"
          >定标依据<textarea v-model="form.awardReason" required />
        </label>
        <V2Select
          v-if="action === 'contract'"
          v-model="form.contractId"
          label="合同"
          :options="contractOptions"
          placeholder="选择合同"
          required
        />
        <template v-if="action === 'performance'">
          <V2Select
            v-model="form.purchaseOrderId"
            label="采购订单"
            :options="purchaseOrderOptions"
            placeholder="选择采购订单"
            required
          /><V2Input v-model="form.serviceScore" label="服务协同评分" required />
          <label class="supplier-page__wide"
            >评价意见<textarea v-model="form.evaluationComment" required />
          </label>
        </template>
        <template v-if="action === 'return'">
          <V2Select
            v-model="form.receiptId"
            label="验收单"
            :options="receiptOptions"
            placeholder="选择验收单"
            required
          /><V2Input v-model="form.returnCode" label="退货编号" required />
          <label>退货日期<input v-model="form.returnDate" type="date" required /></label
          ><V2Input v-model="form.returnQuantity" label="退货数量" required />
          <V2Input v-model="form.returnAmount" label="退货金额" required /><label
            class="supplier-page__wide"
            >退货原因<textarea v-model="form.reason" required />
          </label>
        </template>
        <label v-if="action === 'blacklist'" class="supplier-page__wide"
          >列入原因<textarea v-model="form.reason" required />
        </label>
        <template v-if="action === 'review'">
          <V2Select
            v-model="form.decision"
            label="审核决定"
            :options="[
              { value: 'APPROVE', label: '通过' },
              { value: 'REJECT', label: '驳回' },
            ]"
            required
          />
          <label class="supplier-page__wide"
            >审核意见<textarea v-model="form.comment" required />
          </label>
        </template>
      </form>
      <template #footer>
        <V2GlassButton text="取消" :disabled="busy" :on-click="() => (action = null)" />
        <V2Button type="submit" form="supplier-action-form" :loading="busy">确认提交</V2Button>
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped>
.supplier-page {
  display: grid;
  gap: var(--v2-space-4);
  min-width: 0;
}
.supplier-page__table-wrap {
  overflow-x: auto;
}
.supplier-page__facts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--v2-space-2);
}
:global(.supplier-action-dialog .v2-dialog__body) {
  z-index: 3;
}
.supplier-page__table-wrap table {
  min-width: 46rem;
}
.supplier-page__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}
.supplier-page__grid section {
  min-width: 0;
  overflow-x: auto;
}
.supplier-page table {
  width: 100%;
  border-collapse: collapse;
}
.supplier-page th,
.supplier-page td {
  padding: var(--v2-space-3);
  text-align: left;
  border-bottom: var(--v2-border-width) solid var(--v2-color-border-subtle);
  white-space: nowrap;
}
.supplier-page__trace {
  display: grid;
  gap: var(--v2-space-3);
}
.supplier-page__trace .supplier-page__table-wrap {
  max-width: 100%;
}
.supplier-page__trace .supplier-page__table-wrap table {
  min-width: 0;
  table-layout: auto;
}
.supplier-page__trace :is(th, td) {
  white-space: normal;
  overflow-wrap: anywhere;
}
.supplier-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}
.supplier-page__form label {
  display: grid;
  gap: var(--v2-space-1);
}
.supplier-page__form input,
.supplier-page__form textarea {
  width: 100%;
  box-sizing: border-box;
}
.supplier-page__form textarea {
  min-height: var(--v2-control-height-textarea);
}
.supplier-page__wide {
  grid-column: 1 / -1;
}
@media (max-width: 760px) {
  .supplier-page__grid,
  .supplier-page__form {
    grid-template-columns: 1fr;
  }
  .supplier-page__wide {
    grid-column: auto;
  }
}
</style>
