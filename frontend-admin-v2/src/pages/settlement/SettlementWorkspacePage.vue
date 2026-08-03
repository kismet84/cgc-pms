<script setup lang="ts">
import type {
  ContractRecord,
  SettlementApprovalRecord,
  SettlementAttachmentRecord,
  SettlementCommand,
  SettlementCompute,
  SettlementCostRecord,
  SettlementItemCommand,
  SettlementPaymentRecord,
  SettlementRecord,
  SettlementSources,
  SettlementVariationRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
} from '@/components'
import { formatAmount, formatDecimal } from '@/pages/dashboard/model'
import { loadContractPage } from '@/services/commercial'
import { deleteSiteFile, uploadSiteFile } from '@/services/delivery'
import { isApiClientError } from '@/services/request'
import { loadEnabledDictDataByCode, type DictDataRecord } from '@/services/system-management'
import {
  computeSettlement,
  createSettlement,
  deleteSettlement,
  loadSettlement,
  loadSettlementApprovalRecords,
  loadSettlementAttachments,
  loadSettlementCosts,
  loadSettlementPayments,
  loadSettlements,
  loadSettlementSources,
  loadSettlementVariations,
  saveSettlementItems,
  submitSettlement,
  updateSettlement,
} from '@/services/subcontract'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

type PendingAction = 'delete' | 'submit' | 'delete-file'

const emptySources: SettlementSources = {
  contractItems: [],
  varOrders: [],
  subMeasures: [],
  payRecords: [],
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const records = ref<SettlementRecord[]>([])
const selected = ref<SettlementRecord | null>(null)
const sources = ref<SettlementSources>({ ...emptySources })
const variations = ref<SettlementVariationRecord[]>([])
const payments = ref<SettlementPaymentRecord[]>([])
const costs = ref<SettlementCostRecord[]>([])
const attachments = ref<SettlementAttachmentRecord[]>([])
const approvals = ref<SettlementApprovalRecord[]>([])
const contracts = ref<ContractRecord[]>([])
const settlementStatuses = ref<DictDataRecord[]>([])
const approvalStatuses = ref<DictDataRecord[]>([])
const checkedSourceIds = ref<string[]>([])
const preview = ref<SettlementCompute | null>(null)
const pageNo = ref(1)
const pageSize = 10
const total = ref(0)
const settlementStatus = ref('')
const approvalStatus = ref('')
const keyword = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const detailErrorMessage = ref('')
const formOpen = ref(false)
const itemsOpen = ref(false)
const pendingAction = ref<PendingAction | null>(null)
const pendingFile = ref<SettlementAttachmentRecord | null>(null)
const uploadFile = ref<File | null>(null)
const form = reactive({ projectId: '', contractId: '', deductionAmount: '0', remark: '' })
let listController: AbortController | null = null
let detailController: AbortController | null = null
let candidateController: AbortController | null = null
let listGeneration = 0
let detailGeneration = 0
let candidateGeneration = 0
let settlementDictionariesLoaded = false

const detailId = computed(() => String(route.params.id || '').trim())
const isDetail = computed(() => Boolean(detailId.value))
const projectId = computed(() => workspace.selectedProjectId || '')
const canQuery = computed(() => session.hasPermission('settlement:query'))
const canAdd = computed(() => session.hasPermission('settlement:add'))
const canEdit = computed(() => session.hasPermission('settlement:edit'))
const canDelete = computed(() => session.hasPermission('settlement:delete'))
const canSubmit = computed(() => session.hasPermission('settlement:submit'))
const editable = computed(
  () =>
    selected.value &&
    canEdit.value &&
    ['DRAFT', 'REJECTED'].includes(selected.value.approvalStatus),
)
const deletable = computed(
  () =>
    selected.value &&
    canDelete.value &&
    ['DRAFT', 'REJECTED'].includes(selected.value.approvalStatus),
)
const submittable = computed(
  () =>
    selected.value &&
    canSubmit.value &&
    ['DRAFT', 'REJECTED'].includes(selected.value.approvalStatus),
)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const projectOptions = computed(() => workspace.projects)
const contractOptions = computed(() =>
  contracts.value.map((item) => ({
    value: item.id,
    label: `${item.contractCode || '未编号'} · ${item.contractName}`,
  })),
)
const confirmationTitle = computed(() =>
  pendingAction.value === 'submit'
    ? '提交结算审批'
    : pendingAction.value === 'delete-file'
      ? '删除附件'
      : '删除结算草稿',
)
const confirmationDescription = computed(() => {
  if (pendingAction.value === 'delete-file')
    return pendingFile.value ? `确认删除附件“${pendingFile.value.originalName}”？` : ''
  return selected.value ? `确认操作结算“${selected.value.settlementCode}”？` : ''
})
const settlementStatusOptions = computed(() =>
  settlementStatuses.value.map((item) => ({ value: item.dictValue, label: item.dictLabel })),
)
const approvalStatusOptions = computed(() =>
  approvalStatuses.value.map((item) => ({ value: item.dictValue, label: item.dictLabel })),
)

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

function statusLabel(value: string | null | undefined): string {
  return (
    {
      FINAL: '终期结算',
      SUBCONTRACT: '分包结算',
      SETTLEMENT_V2: '服务端结算快照 V2',
      SUCCESS: '成功',
      PENDING: '待处理',
      PAID: '已付款',
      PARTIAL: '部分付款',
      UNPAID: '未付款',
    }[value || ''] ||
    value ||
    '—'
  )
}

function settlementStatusLabel(value: string | null | undefined): string {
  if (!value) return '—'
  return settlementStatuses.value.find((item) => item.dictValue === value)?.dictLabel ?? value
}

function approvalStatusLabel(value: string | null | undefined): string {
  if (!value) return '—'
  return approvalStatuses.value.find((item) => item.dictValue === value)?.dictLabel ?? value
}

async function loadSettlementDictionaries(signal?: AbortSignal): Promise<void> {
  if (settlementDictionariesLoaded) return
  const [nextSettlementStatuses, nextApprovalStatuses] = await Promise.all([
    loadEnabledDictDataByCode('settlement_final_status', signal),
    loadEnabledDictDataByCode('approval_status', signal),
  ])
  settlementStatuses.value = nextSettlementStatuses
  approvalStatuses.value = nextApprovalStatuses
  settlementDictionariesLoaded = true
}

function money(value: string | null | undefined): string {
  return formatAmount(value)
}

function query() {
  return {
    pageNo: pageNo.value,
    pageSize,
    projectId: projectId.value || undefined,
    settlementStatus: settlementStatus.value || undefined,
    approvalStatus: approvalStatus.value || undefined,
    keyword: keyword.value.trim() || undefined,
  }
}

async function loadPage(): Promise<void> {
  if (!canQuery.value || isDetail.value) return
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  errorMessage.value = ''
  try {
    const [, page] = await Promise.all([
      loadSettlementDictionaries(controller.signal),
      loadSettlements(query(), controller.signal),
    ])
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = page.total
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, '结算台账加载失败')
      showToast('error', '结算台账读取失败', errorMessage.value)
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function loadDetail(id: string): Promise<boolean> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  detailLoading.value = true
  detailErrorMessage.value = ''
  try {
    await loadSettlementDictionaries(controller.signal)
    const [
      record,
      nextSources,
      nextVariations,
      nextPayments,
      nextCosts,
      nextAttachments,
      nextApprovals,
    ] = await Promise.all([
      loadSettlement(id, controller.signal),
      loadSettlementSources(id, controller.signal),
      loadSettlementVariations(id, controller.signal),
      loadSettlementPayments(id, controller.signal),
      loadSettlementCosts(id, controller.signal),
      loadSettlementAttachments(id, controller.signal),
      loadSettlementApprovalRecords(id, controller.signal),
    ])
    if (generation !== detailGeneration) return false
    selected.value = record
    sources.value = nextSources
    variations.value = nextVariations
    payments.value = nextPayments
    costs.value = nextCosts
    attachments.value = nextAttachments
    approvals.value = nextApprovals
    return true
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      selected.value = null
      detailErrorMessage.value = errorText(error, '结算详情加载失败')
      showToast('error', '结算详情读取失败', detailErrorMessage.value)
    }
    return false
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

async function loadContracts(targetProjectId: string, selectedContractId = ''): Promise<void> {
  candidateController?.abort()
  const controller = new AbortController()
  candidateController = controller
  const generation = ++candidateGeneration
  contracts.value = []
  preview.value = null
  if (!targetProjectId) return
  try {
    const page = await loadContractPage(
      { pageNo: 1, pageSize: 200, projectId: targetProjectId, contractType: 'SUB' },
      controller.signal,
    )
    if (generation !== candidateGeneration) return
    contracts.value = page.records
    if (selectedContractId && contracts.value.some((item) => item.id === selectedContractId))
      await changeContract(selectedContractId)
  } catch (error) {
    if (!controller.signal.aborted && generation === candidateGeneration)
      showToast('error', '合同候选读取失败', errorText(error, '合同候选读取失败'))
  }
}

async function changeProject(value: string): Promise<void> {
  form.projectId = value
  form.contractId = ''
  await loadContracts(value)
}

async function changeContract(value: string): Promise<void> {
  form.contractId = value
  preview.value = null
  if (!value) return
  try {
    preview.value = await computeSettlement(value, candidateController?.signal)
  } catch (error) {
    showToast('error', '金额试算失败', errorText(error, '金额试算失败'))
  }
}

async function openForm(edit = false): Promise<void> {
  const record = edit ? selected.value : null
  form.projectId = record?.projectId || projectId.value
  form.contractId = record?.contractId || ''
  form.deductionAmount = record?.deductionAmount ?? '0'
  form.remark = record?.remark ?? ''
  await loadContracts(form.projectId, form.contractId)
  formOpen.value = true
}

function command(): SettlementCommand {
  if (!form.contractId.trim()) throw new TypeError('分包合同不能为空')
  const deduction = form.deductionAmount.trim()
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(deduction))
    throw new TypeError('扣款金额必须是非负数，最多两位小数')
  return {
    contractId: form.contractId,
    deductionAmount: deduction,
    remark: form.remark.trim() || null,
  }
}

async function saveForm(): Promise<void> {
  if (busy.value) return
  busy.value = true
  try {
    const editingId = selected.value?.id
    const wasDetailRoute = isDetail.value
    let id: string
    if (editingId) {
      await updateSettlement(editingId, command())
      id = editingId
    } else {
      id = await createSettlement(command())
    }
    formOpen.value = false
    const reread = await loadDetail(id)
    if (!reread) {
      showToast('warning', '结算已保存，结果未确认', '暂未取得最新结果，请刷新重试。')
      return
    }
    await router.push({ path: '/settlement/list', query: route.query })
    if (!wasDetailRoute) await loadPage()
    showToast('success', '结算已保存', '最新金额与状态已加载。')
  } catch (error) {
    showToast('error', '结算保存失败', errorText(error, '保存失败'))
  } finally {
    busy.value = false
  }
}

function openItems(): void {
  if (!selected.value) return
  checkedSourceIds.value = (selected.value.items || [])
    .filter((item) => item.sourceType === 'CT_CONTRACT' && item.sourceId)
    .map((item) => item.sourceId as string)
  itemsOpen.value = true
}

function toggleSource(id: string, checked: boolean): void {
  checkedSourceIds.value = checked
    ? [...new Set([...checkedSourceIds.value, id])]
    : checkedSourceIds.value.filter((value) => value !== id)
}

async function saveItems(): Promise<void> {
  if (!selected.value || busy.value) return
  busy.value = true
  try {
    const items: SettlementItemCommand[] = checkedSourceIds.value.map((sourceId) => ({
      sourceType: 'CT_CONTRACT',
      sourceId,
    }))
    await saveSettlementItems(selected.value.id, items)
    itemsOpen.value = false
    await loadDetail(selected.value.id)
    showToast('success', '结算明细已保存', '数量、单价和金额已自动重建。')
  } catch (error) {
    showToast('error', '结算明细保存失败', errorText(error, '保存失败'))
  } finally {
    busy.value = false
  }
}

async function confirmAction(): Promise<void> {
  const record = selected.value
  const action = pendingAction.value
  if (!record || !action || busy.value) return
  busy.value = true
  try {
    if (action === 'submit') {
      await submitSettlement(record.id)
      await loadDetail(record.id)
      await router.push({ path: '/settlement/list', query: route.query })
      showToast('success', '结算已提交', '最新金额、来源与审批状态已加载。')
    } else if (action === 'delete') {
      await deleteSettlement(record.id)
      selected.value = null
      await router.push({ path: '/settlement/list', query: route.query })
      showToast('success', '结算草稿已删除', '结算台账已刷新。')
    } else if (pendingFile.value) {
      await deleteSiteFile(pendingFile.value.id)
      await loadDetail(record.id)
      showToast('success', '附件已删除', '附件列表已刷新。')
    }
  } catch (error) {
    showToast('error', '操作失败', errorText(error, '操作失败'))
  } finally {
    busy.value = false
    pendingAction.value = null
    pendingFile.value = null
  }
}

function requestAttachmentDelete(file: SettlementAttachmentRecord): void {
  pendingFile.value = file
  pendingAction.value = 'delete-file'
}

function closeConfirmation(): void {
  pendingAction.value = null
  pendingFile.value = null
}

function chooseFile(event: Event): void {
  uploadFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function uploadAttachment(): Promise<void> {
  if (!selected.value || !uploadFile.value || busy.value) return
  busy.value = true
  try {
    await uploadSiteFile(uploadFile.value, 'SETTLEMENT', selected.value.id, 'OTHER')
    uploadFile.value = null
    await loadDetail(selected.value.id)
    showToast('success', '附件已上传', '附件列表已刷新。')
  } catch (error) {
    showToast('error', '附件上传失败', errorText(error, '上传失败'))
  } finally {
    busy.value = false
  }
}

function search(): void {
  pageNo.value = 1
  void loadPage()
}

function changePage(next: number): void {
  if (next < 1 || next > pageCount.value || next === pageNo.value) return
  pageNo.value = next
  void loadPage()
}

watch(
  [detailId, projectId, settlementStatus, approvalStatus],
  ([id]) => {
    if (id) {
      if (!records.value.length) void loadPage()
      void loadDetail(id)
    } else {
      selected.value = null
      pageNo.value = 1
      void loadPage()
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
  candidateController?.abort()
})
</script>

<template>
  <section class="settlement-workspace">
    <V2PageState
      v-if="!canQuery"
      kind="forbidden"
      title="无权访问结算管理"
      description="系统未加载结算数据。"
    />

    <template v-else>
      <V2Card title="结算台账" :heading-level="1">
        <template #actions>
          <div class="settlement-workspace__filters">
            <V2Input
              v-model="keyword"
              label="搜索"
              hide-label
              placeholder="结算编号或备注"
              @keyup.enter="search"
            />
            <V2Select
              v-model="settlementStatus"
              label="结算状态"
              hide-label
              allow-empty
              :options="settlementStatusOptions"
              placeholder="全部结算状态"
              @update:model-value="search"
            />
            <V2Select
              v-model="approvalStatus"
              label="审批状态"
              hide-label
              allow-empty
              :options="approvalStatusOptions"
              placeholder="全部审批状态"
              @update:model-value="search"
            />
            <V2Button size="small" variant="secondary" @click="search">查询</V2Button>
            <V2Button v-if="canAdd" size="small" @click="openForm(false)">新建结算</V2Button>
          </div>
        </template>
      </V2Card>

      <V2PageState
        v-if="loading && !records.length"
        kind="loading"
        title="正在加载"
        description="正在读取结算台账。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="结算台账加载失败"
        :description="errorMessage"
      >
        <template #actions><V2Button @click="loadPage">重试</V2Button></template>
      </V2PageState>
      <V2PageState
        v-else-if="!records.length"
        title="暂无结算记录"
        description="当前项目和筛选范围没有可访问结算。"
      />
      <V2Card v-else :heading-level="2">
        <div
          class="settlement-workspace__table-wrap"
          role="region"
          aria-label="结算台账表格"
          tabindex="0"
        >
          <table class="v2-table settlement-workspace__table">
            <thead>
              <tr>
                <th>结算编号</th>
                <th>合同</th>
                <th>合作方</th>
                <th>结算金额</th>
                <th>已付</th>
                <th>结算状态</th>
                <th>审批状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in records" :key="record.id">
                <td>
                  <V2Button
                    size="small"
                    variant="ghost"
                    class="v2-table__record-link"
                    @click="router.push({ path: `/settlement/${record.id}`, query: route.query })"
                    >{{ record.settlementCode }}</V2Button
                  >
                </td>
                <td>{{ record.contractName || '—' }}</td>
                <td>{{ record.partnerName || '—' }}</td>
                <td>{{ money(record.finalAmount) }}</td>
                <td>{{ money(record.paidAmount) }}</td>
                <td>
                  <V2Badge>{{ settlementStatusLabel(record.settlementStatus) }}</V2Badge>
                </td>
                <td>
                  <V2Badge>{{ approvalStatusLabel(record.approvalStatus) }}</V2Badge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="settlement-workspace__pager" aria-label="结算分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo <= 1"
              @click="changePage(pageNo - 1)"
              >上一页</V2Button
            >
            <span>第 {{ pageNo }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo >= pageCount"
              @click="changePage(pageNo + 1)"
              >下一页</V2Button
            >
          </nav>
        </template>
      </V2Card>
    </template>

    <V2Dialog
      :open="isDetail"
      :title="selected?.settlementCode || '结算详情'"
      description="查看结算金额、来源、付款、成本与审批记录。"
      :close-disabled="busy"
      :close-on-backdrop="!editable"
      panel-class="v2-dialog-standard v2-detail-dialog v2-dialog-wide"
      @close="router.push({ path: '/settlement/list', query: route.query })"
    >
      <V2PageState
        v-if="detailLoading && !selected"
        kind="loading"
        title="正在加载"
        description="正在读取结算详情与追溯数据。"
      />
      <V2PageState
        v-else-if="detailErrorMessage"
        kind="error"
        title="详情加载失败"
        :description="detailErrorMessage"
      >
        <template #actions
          ><V2Button type="button" @click="loadDetail(detailId)">重试</V2Button></template
        >
      </V2PageState>
      <template v-else-if="selected">
        <section class="settlement-workspace__summary">
          <section class="v2-detail-dialog__section">
            <div class="v2-detail-dialog__section-heading"><h3>业务范围</h3></div>
            <dl class="v2-detail-dialog__facts">
              <div>
                <dt>项目</dt>
                <dd>{{ selected.projectName || '—' }}</dd>
              </div>
              <div>
                <dt>合同</dt>
                <dd>{{ selected.contractName || '—' }}</dd>
              </div>
              <div>
                <dt>分包单位</dt>
                <dd>{{ selected.partnerName || '—' }}</dd>
              </div>
              <div>
                <dt>类型</dt>
                <dd>{{ statusLabel(selected.settlementType) }}</dd>
              </div>
            </dl>
          </section>
          <section class="v2-detail-dialog__section">
            <div class="v2-detail-dialog__section-heading"><h3>金额快照</h3></div>
            <dl class="v2-detail-dialog__facts">
              <div>
                <dt>合同额</dt>
                <dd>{{ money(selected.contractAmount) }}</dd>
              </div>
              <div>
                <dt>签证额</dt>
                <dd>{{ money(selected.changeAmount) }}</dd>
              </div>
              <div>
                <dt>审定计量</dt>
                <dd>{{ money(selected.measuredAmount) }}</dd>
              </div>
              <div>
                <dt>扣款</dt>
                <dd>{{ money(selected.deductionAmount) }}</dd>
              </div>
              <div>
                <dt>结算额</dt>
                <dd>{{ money(selected.finalAmount) }}</dd>
              </div>
              <div>
                <dt>质保金</dt>
                <dd>{{ money(selected.warrantyAmount) }}</dd>
              </div>
            </dl>
          </section>
          <section class="v2-detail-dialog__section">
            <div class="v2-detail-dialog__section-heading"><h3>状态与余额</h3></div>
            <dl class="v2-detail-dialog__facts">
              <div>
                <dt>审批</dt>
                <dd>{{ approvalStatusLabel(selected.approvalStatus) }}</dd>
              </div>
              <div>
                <dt>结算</dt>
                <dd>{{ settlementStatusLabel(selected.settlementStatus) }}</dd>
              </div>
              <div>
                <dt>累计付款</dt>
                <dd>{{ money(selected.paidAmount) }}</dd>
              </div>
              <div>
                <dt>未付金额</dt>
                <dd>{{ money(selected.unpaidAmount) }}</dd>
              </div>
              <div>
                <dt>金额口径</dt>
                <dd>{{ statusLabel(selected.amountFormulaVersion) }}</dd>
              </div>
            </dl>
          </section>
        </section>

        <section class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading"><h3>结算明细</h3></div>
          <div
            class="settlement-workspace__table-wrap"
            role="region"
            aria-label="结算明细表格"
            tabindex="0"
          >
            <table class="v2-table settlement-workspace__table">
              <thead>
                <tr>
                  <th>清单</th>
                  <th>单位</th>
                  <th>审批计量数量</th>
                  <th>合同单价</th>
                  <th>金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in selected.items || []" :key="item.id">
                  <td>{{ item.itemName }}</td>
                  <td>{{ item.unit || '—' }}</td>
                  <td>{{ formatDecimal(item.quantity) }}</td>
                  <td>{{ money(item.unitPrice) }}</td>
                  <td>{{ money(item.amount) }}</td>
                </tr>
                <tr v-if="!selected.items?.length">
                  <td colspan="5">暂无结算明细</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="settlement-workspace__trace">
          <section class="v2-detail-dialog__section">
            <div class="v2-detail-dialog__section-heading"><h3>计量与签证来源</h3></div>
            <ul>
              <li v-for="item in sources.subMeasures" :key="item.id">
                {{ item.measureCode }} · {{ money(item.approvedAmount) }} ·
                {{ approvalStatusLabel(item.approvalStatus) }}
              </li>
              <li v-for="item in sources.varOrders" :key="item.id">
                {{ item.varCode }} · {{ item.varName }} · {{ money(item.confirmedAmount) }}
              </li>
              <li v-if="!sources.subMeasures.length && !sources.varOrders.length">暂无来源</li>
            </ul>
          </section>
          <section class="v2-detail-dialog__section">
            <div class="v2-detail-dialog__section-heading"><h3>付款追溯</h3></div>
            <ul>
              <li v-for="item in payments" :key="item.id">
                {{ item.applyCode || '付款记录' }} · {{ money(item.actualPayAmount) }} ·
                {{ statusLabel(item.payStatus) }}
              </li>
              <li v-if="!payments.length">暂无付款记录</li>
            </ul>
          </section>
          <section class="v2-detail-dialog__section">
            <div class="v2-detail-dialog__section-heading"><h3>成本追溯</h3></div>
            <ul>
              <li v-for="item in costs" :key="item.id">
                {{ item.costSubjectName || '成本记录' }} · {{ money(item.amount) }} ·
                {{ statusLabel(item.costStatus) }}
              </li>
              <li v-if="!costs.length">暂无成本记录</li>
            </ul>
          </section>
          <section class="v2-detail-dialog__section">
            <div class="v2-detail-dialog__section-heading"><h3>审批记录</h3></div>
            <ul>
              <li v-for="item in approvals" :key="item.id">
                {{ item.nodeName || '审批节点' }} · {{ item.operatorName || '—' }} ·
                {{ item.actionName || statusLabel(item.actionType) }} · {{ item.createdAt || '—' }}
              </li>
              <li v-if="!approvals.length">暂无审批记录</li>
            </ul>
          </section>
        </section>

        <section class="v2-detail-dialog__section">
          <div class="v2-detail-dialog__section-heading"><h3>结算附件</h3></div>
          <div v-if="editable" class="settlement-workspace__upload">
            <input
              id="settlement-file"
              class="v2-file-input"
              type="file"
              aria-label="选择结算附件"
              @change="chooseFile"
            />
            <V2Button
              type="button"
              size="small"
              :disabled="!uploadFile || busy"
              @click="uploadAttachment"
              >上传</V2Button
            >
          </div>
          <ul>
            <li v-for="file in attachments" :key="file.id">
              <span>{{ file.originalName }} · {{ file.uploadedAt || '—' }}</span>
              <V2Button
                type="button"
                v-if="editable"
                size="small"
                variant="ghost"
                @click="requestAttachmentDelete(file)"
                >删除</V2Button
              >
            </li>
            <li v-if="!attachments.length">暂无附件</li>
          </ul>
        </section>
      </template>
      <template #footer>
        <V2Button
          type="button"
          variant="secondary"
          :disabled="busy"
          @click="router.push({ path: '/settlement/list', query: route.query })"
          >关闭</V2Button
        >
        <V2Button
          type="button"
          v-if="editable"
          variant="secondary"
          :disabled="busy"
          @click="openForm(true)"
          >编辑</V2Button
        >
        <V2Button
          type="button"
          v-if="editable"
          variant="secondary"
          :disabled="busy"
          @click="openItems"
          >维护明细</V2Button
        >
        <V2Button
          type="button"
          v-if="submittable"
          :disabled="busy"
          @click="pendingAction = 'submit'"
          >提交审批</V2Button
        >
        <V2Button
          type="button"
          v-if="deletable"
          variant="danger"
          :disabled="busy"
          @click="pendingAction = 'delete'"
          >删除</V2Button
        >
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="formOpen"
      :title="selected ? '编辑结算' : '新建结算'"
      description="金额按所选合同、计量和签证自动试算，保存后加载最新结果。"
      :close-disabled="busy"
      :close-on-backdrop="false"
    >
      <form
        id="settlement-workspace-editor-form"
        class="settlement-workspace__form"
        @submit.prevent="saveForm"
      >
        <V2Select
          v-model="form.projectId"
          label="项目"
          :options="projectOptions"
          required
          :disabled="busy"
          @update:model-value="changeProject"
        />
        <V2Select
          v-model="form.contractId"
          label="分包合同"
          :options="contractOptions"
          required
          :disabled="busy || !form.projectId"
          @update:model-value="changeContract"
        />
        <V2Input
          v-model="form.deductionAmount"
          label="终期扣款"
          :decimal-scale="2"
          required
          hint="非负金额，最多两位小数"
        />
        <V2Input v-model="form.remark" label="备注" />
        <div v-if="preview" class="settlement-workspace__preview" aria-live="polite">
          <span>合同额 {{ money(preview.contractAmount) }}</span>
          <span>签证额 {{ money(preview.changeAmount) }}</span>
          <span>审定计量 {{ money(preview.measuredAmount) }}</span>
          <span>已付 {{ money(preview.paidAmount) }}</span>
        </div>
      </form>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="busy" @click="formOpen = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="settlement-workspace-editor-form" :loading="busy">
          保存
        </V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="itemsOpen"
      title="维护结算明细"
      description="只选择已审批计量覆盖的合同清单；数量、单价、金额自动重建。"
      :close-disabled="busy"
      :close-on-backdrop="false"
    >
      <div class="settlement-workspace__source-list">
        <label v-for="item in sources.contractItems" :key="item.id">
          <input
            type="checkbox"
            :checked="checkedSourceIds.includes(item.id)"
            @change="toggleSource(item.id, ($event.target as HTMLInputElement).checked)"
          />
          <span
            >{{ item.itemCode || '未编号' }} · {{ item.itemName }} · 数量
            {{ formatDecimal(item.measuredQuantity) }} · {{ money(item.amount) }}</span
          >
        </label>
        <p v-if="!sources.contractItems.length">暂无可结算合同清单。</p>
      </div>
      <template #footer>
        <V2Button variant="secondary" :disabled="busy" @click="itemsOpen = false">取消</V2Button>
        <V2Button :loading="busy" @click="saveItems">保存明细</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(pendingAction)"
      :title="confirmationTitle"
      :description="confirmationDescription"
      :confirm-text="pendingAction === 'submit' ? '确认提交' : '确认删除'"
      :danger="pendingAction !== 'submit'"
      :loading="busy"
      @confirm="confirmAction"
      @close="closeConfirmation"
    />
  </section>
</template>

<style scoped>
.settlement-workspace {
  display: grid;
  gap: var(--v2-space-3);
  min-width: 0;
}
.settlement-workspace__filters,
.settlement-workspace__actions,
.settlement-workspace__pager,
.settlement-workspace__upload,
.settlement-workspace__preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--v2-space-2);
}
.settlement-workspace__filters {
  justify-content: flex-end;
}
.settlement-workspace__summary,
.settlement-workspace__trace {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-3);
}
.settlement-workspace__summary {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.settlement-workspace__summary .v2-detail-dialog__facts {
  grid-template-columns: minmax(0, 1fr);
}
.settlement-workspace__summary .v2-detail-dialog__facts > div:nth-last-child(2) {
  border-bottom: var(--v2-border-width) solid var(--v2-dialog-divider);
}
.settlement-workspace__trace {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.settlement-workspace__table-wrap {
  max-width: 100%;
  overflow-x: auto;
}
.settlement-workspace__table {
  min-width: 47.5rem;
}
.settlement-workspace__pager {
  justify-content: flex-end;
}
.settlement-workspace dl {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: var(--v2-space-2) var(--v2-space-3);
  margin: 0;
}
.settlement-workspace dt {
  color: var(--v2-color-text-secondary);
}
.settlement-workspace dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.settlement-workspace ul {
  display: grid;
  gap: var(--v2-space-2);
  margin: 0;
  padding-left: var(--v2-space-5);
}
.settlement-workspace__form,
.settlement-workspace__source-list {
  display: grid;
  gap: var(--v2-space-3);
}
.settlement-workspace__source-list label {
  display: flex;
  align-items: flex-start;
  gap: var(--v2-space-2);
}
.settlement-workspace__preview {
  padding: var(--v2-space-3);
  background: var(--v2-color-surface-subtle);
  border-radius: var(--v2-radius-sm);
}
@media (max-width: 56.25rem) {
  .settlement-workspace__summary,
  .settlement-workspace__trace {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 32.5rem) {
  .settlement-workspace__filters > *,
  .settlement-workspace__actions > * {
    flex: 1 1 100%;
  }
  .settlement-workspace__summary,
  .settlement-workspace__trace {
    grid-template-columns: minmax(0, 1fr);
  }
  .settlement-workspace__pager {
    justify-content: space-between;
  }
}
</style>
