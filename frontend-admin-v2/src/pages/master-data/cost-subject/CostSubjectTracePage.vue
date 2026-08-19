<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  V2Button,
  V2Card,
  V2Cluster,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import V2Tabs from '@/components/V2Tabs.vue'
import { formatAmount } from '@/shared/display'
import {
  cancelRecalculationBatch,
  cancelReversalRequest,
  cancelBidTransferRequest,
  cancelFinanceAllocationRequest,
  createBidTransferRequest,
  createFinanceAllocationRequest,
  createRecalculationBatch,
  createReversalRequest,
  loadBidTransferRequests,
  loadBidTransfers,
  loadCostSubjectReconciliation,
  loadFinanceAllocationRequests,
  loadFinanceAllocations,
  loadGovernanceFormOptions,
  loadRecalculationBatches,
  loadReversalRequests,
  loadSubjectImpact,
  overrideClassification,
  submitBidTransferRequest,
  submitFinanceAllocationRequest,
  submitRecalculationBatch,
  submitReversalRequest,
  type BidCostOption,
  type BidTransferRequestRecord,
  type CostSubjectAuditRow,
  type FinanceAllocationRequestRecord,
  type FinanceSourceOption,
  type GovernanceFormOptions,
  type SubjectImpactRecord,
} from '@/services/cost-subject'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import {
  allocationBasisLabel,
  allocationBasisOptions,
  allocationSourceLabel,
  allocationSubjectLabel,
  bidCostLabel,
  impactLabels,
  pageSlice,
  requestProjectLabel,
  rowText,
  statusLabel,
  targetVersionLabel,
} from './model'
import './styles.css'

type TabValue = 'subject-impact' | 'project-reconciliation' | 'bid-transfer' | 'finance-allocation'
type ReversalTargetType = 'BID_TRANSFER' | 'FINANCE_ALLOCATION' | 'RECALCULATION'

const emptyOptions = (): GovernanceFormOptions => ({
  projects: [],
  costSubjects: [],
  rulePlans: [],
  bidCosts: [],
  targetVersions: [],
  financeSources: [],
  pendingClassifications: [],
})

const session = useSessionStore()
const pageSize = 10
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const activeTab = ref<TabValue>('subject-impact')
const options = ref<GovernanceFormOptions>(emptyOptions())
const impactSubjectId = ref('')
const reconciliationProjectId = ref('')
const impact = ref<SubjectImpactRecord | null>(null)
const reconciliation = ref<CostSubjectAuditRow | null>(null)
const transfers = ref<CostSubjectAuditRow[]>([])
const allocations = ref<CostSubjectAuditRow[]>([])
const transferRequests = ref<BidTransferRequestRecord[]>([])
const allocationRequests = ref<FinanceAllocationRequestRecord[]>([])
const recalculations = ref<CostSubjectAuditRow[]>([])
const reversals = ref<CostSubjectAuditRow[]>([])
const transferPageNo = ref(1)
const allocationPageNo = ref(1)
const transferDialog = ref(false)
const allocationDialog = ref(false)
const recalculationDialog = ref(false)
const reversalDialog = ref(false)
const overrideDialog = ref(false)
let controller: AbortController | null = null

const tabs = [
  { value: 'subject-impact', label: '科目影响' },
  { value: 'project-reconciliation', label: '项目成本对账' },
  { value: 'bid-transfer', label: '投标成本转入' },
  { value: 'finance-allocation', label: '财务费用分摊' },
]

const can = (permission: string) => session.hasPermission(permission)
const canBidCreate = computed(() => can('cost:subject:bid-transfer'))
const canBidSubmit = computed(() => can('cost:subject:transfer:submit'))
const canAllocationCreate = computed(
  () => can('cost:subject:finance-allocate') && can('cost:classification:override'),
)
const canAllocationSubmit = computed(() => can('cost:subject:allocation:submit'))
const canRecalculate = computed(() => can('cost:recalculation:edit') || can('cost:post-close:edit'))
const canRecalculateSubmit = computed(
  () => can('cost:recalculation:submit') || can('cost:post-close:submit'),
)
const canReverse = computed(() => can('cost:reversal:edit'))
const canReverseSubmit = computed(() => can('cost:reversal:submit'))
const canOverride = computed(() => can('cost:classification:override'))

const projectOptions = computed(() =>
  options.value.projects.map((item) => ({
    value: item.id,
    label: `${item.projectCode} · ${item.projectName}（${statusLabel(item.projectStatus)}）`,
  })),
)
const subjectOptions = computed(() =>
  options.value.costSubjects.map((item) => ({
    value: item.id,
    label: `${item.subjectCode} · ${item.subjectName}${item.overheadRuleStatus === 'DISABLE' ? '（间接费规则已停用）' : ''}`,
    disabled: item.status !== 'ENABLE' || item.overheadRuleStatus === 'DISABLE',
  })),
)
const activePlanOptions = computed(() =>
  options.value.rulePlans
    .filter((item) => item.status === 'ACTIVE')
    .map((item) => ({ value: item.id, label: `${item.versionCode} · ${item.versionName}` })),
)
const bidOptions = computed(() =>
  options.value.bidCosts.map((item) => ({
    value: item.id,
    label: `${item.bidCode} · ${item.bidProjectName} → ${item.projectCode} · ${item.projectName}`,
  })),
)
const financeSourceOptions = computed(() =>
  options.value.financeSources.map((item) => ({
    value: `${item.sourceType}:${item.sourceId}`,
    label: `${item.sourceCode} · ${item.sourceName || sourceTypeLabel(item.sourceType)}（余 ${formatAmount(item.remainingAmount)}）`,
  })),
)
const pagedTransfers = computed(() => pageSlice(transfers.value, transferPageNo.value))
const pagedAllocations = computed(() => pageSlice(allocations.value, allocationPageNo.value))

const transferForm = reactive({ bidCostId: '', targetId: '', mappingVersionId: '', remark: '' })
const allocationForm = reactive({
  sourceKey: '',
  allocationBasis: 'BENEFIT_AMOUNT',
  accountingPeriod: '',
  costSubjectId: '',
  remark: '',
  lines: [{ projectId: '', basisValue: '1' }],
})
const recalculationForm = reactive({
  batchType: 'HISTORY_RECALCULATION' as 'HISTORY_RECALCULATION' | 'POST_CLOSE_ADJUSTMENT',
  projectId: '',
  ruleVersionId: '',
  cutoffAt: '',
  reason: '',
})
const reversalForm = reactive({
  targetType: 'BID_TRANSFER' as ReversalTargetType,
  targetId: '',
  targetLabel: '',
  reason: '',
})
const overrideForm = reactive({
  caseId: null as string | null,
  snapshotId: null as string | null,
  sourceLabel: '',
  costSubjectId: '',
  reason: '',
})

const selectedBid = computed<BidCostOption | undefined>(() =>
  options.value.bidCosts.find((item) => item.id === transferForm.bidCostId),
)
const transferTargetOptions = computed(() => {
  const projectId = selectedBid.value?.projectId
  return options.value.targetVersions
    .filter(
      (item) =>
        item.projectId === projectId &&
        ['DRAFT', 'REJECTED'].includes(item.approvalStatus) &&
        item.status !== 'ACTIVE',
    )
    .map((item) => ({
      value: item.id,
      label: `${item.versionNo} · ${item.versionName}（${formatAmount(item.totalTargetAmount)}）`,
    }))
})
const selectedFinanceSource = computed<FinanceSourceOption | undefined>(() => {
  const [sourceType, sourceId] = allocationForm.sourceKey.split(':')
  return options.value.financeSources.find(
    (item) => item.sourceType === sourceType && item.sourceId === sourceId,
  )
})
const allocationBasisTotal = computed(() =>
  allocationForm.lines.reduce((sum, item) => sum + Number(item.basisValue || 0), 0),
)

function sourceTypeLabel(value: string): string {
  return value === 'ACCOUNTING_ENTRY_LINE' ? '已过账手工成本凭证明细' : '已审批费用申请'
}

function messageOf(value: unknown): string {
  if (isApiClientError(value)) return value.message
  return value instanceof Error ? value.message : '请求失败，请稍后重试'
}

async function loadTrace(signal?: AbortSignal): Promise<void> {
  ;[
    options.value,
    transferRequests.value,
    allocationRequests.value,
    transfers.value,
    allocations.value,
    recalculations.value,
    reversals.value,
  ] = await Promise.all([
    loadGovernanceFormOptions(signal),
    loadBidTransferRequests(signal),
    loadFinanceAllocationRequests(signal),
    loadBidTransfers(signal),
    loadFinanceAllocations(signal),
    loadRecalculationBatches(signal),
    loadReversalRequests(signal),
  ])
  transferPageNo.value = 1
  allocationPageNo.value = 1
}

async function loadPage(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    await loadTrace(current.signal)
  } catch (value) {
    if (!current.signal.aborted) error.value = messageOf(value)
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshTrace(): Promise<void> {
  await loadPage()
  showToast(
    error.value ? 'error' : 'success',
    error.value ? '刷新失败' : '已刷新',
    error.value || '当前内容已更新。',
  )
}

async function queryImpact(): Promise<void> {
  if (!impactSubjectId.value) return showToast('warning', '请选择科目', '仅支持成本域末级科目。')
  try {
    impact.value = await loadSubjectImpact(impactSubjectId.value)
  } catch (value) {
    impact.value = null
    showToast('error', '影响查询失败', messageOf(value))
  }
}

async function queryReconciliation(): Promise<void> {
  if (!reconciliationProjectId.value)
    return showToast('warning', '请选择项目', '项目范围按当前账号数据权限过滤。')
  try {
    reconciliation.value = await loadCostSubjectReconciliation(reconciliationProjectId.value)
  } catch (value) {
    reconciliation.value = null
    showToast('error', '项目对账失败', messageOf(value))
  }
}

function openTransfer(): void {
  Object.assign(transferForm, { bidCostId: '', targetId: '', mappingVersionId: '', remark: '' })
  transferDialog.value = true
}

async function saveTransfer(): Promise<void> {
  if (!selectedBid.value || !transferForm.targetId || !transferForm.mappingVersionId)
    return showToast('warning', '向导未完成', '请选择中标成本、目标成本版本和启用规则方案。')
  saving.value = true
  try {
    await createBidTransferRequest({
      bidCostId: selectedBid.value.id,
      projectId: selectedBid.value.projectId,
      targetId: transferForm.targetId,
      mappingVersionId: transferForm.mappingVersionId,
      remark: transferForm.remark.trim(),
    })
    transferDialog.value = false
    await loadTrace()
    showToast('success', '转入试算草稿已保存', '请由原申请人提交，再由另一名财务负责人审批。')
  } catch (value) {
    showToast('error', '转入申请失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function submitTransfer(record: BidTransferRequestRecord): Promise<void> {
  saving.value = true
  try {
    await submitBidTransferRequest(record.id)
    await loadTrace()
    showToast('success', '转入申请已提交', '申请人不能审批本人申请。')
  } catch (value) {
    showToast('error', '提交失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function cancelTransfer(record: BidTransferRequestRecord): Promise<void> {
  saving.value = true
  try {
    await cancelBidTransferRequest(record.id)
    await loadTrace()
    showToast('success', '转入草稿已取消', '来源已释放，可重新试算。')
  } catch (value) {
    showToast('error', '取消失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openAllocation(): void {
  Object.assign(allocationForm, {
    sourceKey: '',
    allocationBasis: 'BENEFIT_AMOUNT',
    accountingPeriod: '',
    costSubjectId: '',
    remark: '',
    lines: [{ projectId: '', basisValue: '1' }],
  })
  allocationDialog.value = true
}

function addAllocationLine(): void {
  allocationForm.lines.push({ projectId: '', basisValue: '1' })
}

async function saveAllocation(): Promise<void> {
  const source = selectedFinanceSource.value
  if (
    !source ||
    !allocationForm.accountingPeriod ||
    !allocationForm.costSubjectId ||
    allocationBasisTotal.value <= 0 ||
    allocationForm.lines.some((line) => !line.projectId || Number(line.basisValue) <= 0)
  )
    return showToast('warning', '向导未完成', '请选择来源、期间、科目、项目并填写正数依据。')
  saving.value = true
  try {
    await createFinanceAllocationRequest({
      sourceType: source.sourceType,
      sourceId: source.sourceId,
      allocationBasis: allocationForm.allocationBasis,
      accountingPeriod: allocationForm.accountingPeriod,
      costSubjectId: allocationForm.costSubjectId,
      remark: allocationForm.remark.trim(),
      lines: allocationForm.lines.map((line) => ({ ...line })),
    })
    allocationDialog.value = false
    await loadTrace()
    showToast('success', '分摊试算草稿已保存', '覆盖自动匹配时原因已纳入审批审计。')
  } catch (value) {
    showToast('error', '分摊申请失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function submitAllocation(record: FinanceAllocationRequestRecord): Promise<void> {
  saving.value = true
  try {
    await submitFinanceAllocationRequest(record.id)
    await loadTrace()
    showToast('success', '分摊申请已提交', '申请人不能审批本人申请。')
  } catch (value) {
    showToast('error', '提交失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function cancelAllocation(record: FinanceAllocationRequestRecord): Promise<void> {
  saving.value = true
  try {
    await cancelFinanceAllocationRequest(record.id)
    await loadTrace()
    showToast('success', '分摊草稿已取消', '来源已释放，可重新试算。')
  } catch (value) {
    showToast('error', '取消失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openRecalculation(): void {
  Object.assign(recalculationForm, {
    batchType: 'HISTORY_RECALCULATION',
    projectId: '',
    ruleVersionId: activePlanOptions.value[0]?.value ?? '',
    cutoffAt: new Date().toISOString().slice(0, 16),
    reason: '',
  })
  recalculationDialog.value = true
}

async function saveRecalculation(): Promise<void> {
  if (!recalculationForm.ruleVersionId || !recalculationForm.reason.trim())
    return showToast('warning', '试算条件不完整', '请选择规则方案并填写重算原因。')
  if (recalculationForm.batchType === 'POST_CLOSE_ADJUSTMENT' && !recalculationForm.projectId)
    return showToast('warning', '请选择已关闭项目', '关闭后财务调整不能使用全公司范围。')
  saving.value = true
  try {
    await createRecalculationBatch({
      projectId: recalculationForm.projectId || null,
      ruleVersionId: recalculationForm.ruleVersionId,
      cutoffAt: recalculationForm.cutoffAt || null,
      batchType: recalculationForm.batchType,
      reason: recalculationForm.reason.trim(),
    })
    recalculationDialog.value = false
    await loadTrace()
    showToast('success', '历史试算已冻结', '请复核差异与待归类数量，再提交审批。')
  } catch (value) {
    showToast('error', '历史试算失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function submitRecalculation(record: CostSubjectAuditRow): Promise<void> {
  saving.value = true
  try {
    await submitRecalculationBatch(String(record.id))
    await loadTrace()
    showToast('success', '重算批次已提交', '审批通过后系统以反向与正向事实完成重分类。')
  } catch (value) {
    showToast('error', '提交失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function cancelRecalculation(record: CostSubjectAuditRow): Promise<void> {
  saving.value = true
  try {
    await cancelRecalculationBatch(String(record.id))
    await loadTrace()
    showToast('success', '草稿已取消', '事实占用已释放。')
  } catch (value) {
    showToast('error', '取消失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openReversal(type: ReversalTargetType, row: CostSubjectAuditRow): void {
  Object.assign(reversalForm, {
    targetType: type,
    targetId: String(row.id),
    targetLabel: rowText(
      row,
      type === 'BID_TRANSFER'
        ? 'transferCode'
        : type === 'FINANCE_ALLOCATION'
          ? 'batchCode'
          : 'batchCode',
    ),
    reason: '',
  })
  reversalDialog.value = true
}

async function saveReversal(): Promise<void> {
  if (!reversalForm.targetId || !reversalForm.reason.trim())
    return showToast('warning', '冲销信息不完整', '请选择系统判断可冲销的事实并填写原因。')
  saving.value = true
  try {
    await createReversalRequest({
      targetType: reversalForm.targetType,
      targetId: reversalForm.targetId,
      reason: reversalForm.reason.trim(),
    })
    reversalDialog.value = false
    await loadTrace()
    showToast('success', '冲销申请已保存', '冲销只生成反向事实，不改删原记录。')
  } catch (value) {
    showToast('error', '冲销申请失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function submitReversal(record: CostSubjectAuditRow): Promise<void> {
  saving.value = true
  try {
    await submitReversalRequest(String(record.id))
    await loadTrace()
    showToast('success', '冲销申请已提交', '另一名财务负责人审批后生成反向事实。')
  } catch (value) {
    showToast('error', '提交失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function cancelReversal(record: CostSubjectAuditRow): Promise<void> {
  saving.value = true
  try {
    await cancelReversalRequest(String(record.id))
    await loadTrace()
    showToast('success', '冲销申请已取消', '目标占用已释放，可重新发起正确申请。')
  } catch (value) {
    showToast('error', '取消失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openOverride(record: CostSubjectAuditRow): void {
  Object.assign(overrideForm, {
    caseId: record.caseId ? String(record.caseId) : null,
    snapshotId: record.snapshotId ? String(record.snapshotId) : null,
    sourceLabel: `${rowText(record, 'sourceType')} · ${rowText(record, 'sourceId')} / ${rowText(record, 'sourceItemId')}`,
    costSubjectId: '',
    reason: '',
  })
  overrideDialog.value = true
}

async function saveOverride(): Promise<void> {
  if (!overrideForm.costSubjectId || !overrideForm.reason.trim())
    return showToast('warning', '覆盖信息不完整', '请选择启用末级成本科目并填写覆盖原因。')
  saving.value = true
  try {
    await overrideClassification({
      caseId: overrideForm.caseId,
      snapshotId: overrideForm.snapshotId,
      costSubjectId: overrideForm.costSubjectId,
      reason: overrideForm.reason.trim(),
    })
    overrideDialog.value = false
    await loadTrace()
    showToast('success', '归类覆盖已保存', '原匹配、规则版本、覆盖原因和操作者均已保留。')
  } catch (value) {
    showToast('error', '归类覆盖失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

onMounted(() => void loadPage())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="cost-subject-page" :gap="4">
    <V2Card title="成本追溯与转入" :heading-level="1">
      <template #actions>
        <V2Cluster>
          <V2Button v-if="canRecalculate" size="small" @click="openRecalculation"
            >历史重算</V2Button
          >
          <V2Button size="small" variant="secondary" @click="refreshTrace">刷新</V2Button>
        </V2Cluster>
      </template>
    </V2Card>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在读取成本追溯事实"
      description="请稍候。"
    />
    <V2PageState v-else-if="error" kind="error" title="成本追溯加载失败" :description="error">
      <template #actions><V2Button @click="loadPage">重试</V2Button></template>
    </V2PageState>

    <template v-else>
      <V2Card>
        <V2Tabs v-model="activeTab" :tabs="tabs" id-prefix="cost-trace" aria-label="成本追溯分区" />
      </V2Card>

      <section
        v-if="activeTab === 'subject-impact'"
        id="cost-trace-panel-subject-impact"
        role="tabpanel"
        aria-labelledby="cost-trace-tab-subject-impact"
      >
        <V2Card title="科目影响">
          <form class="cost-subject-page__query" @submit.prevent="queryImpact">
            <V2Select
              v-model="impactSubjectId"
              :options="subjectOptions"
              label="成本末级科目"
              required
            />
            <V2Button type="submit">查询引用影响</V2Button>
          </form>
          <dl v-if="impact" class="cost-subject-page__facts cost-subject-page__facts--impact">
            <div v-for="[key, label] in impactLabels" :key="key">
              <dt>{{ label }}</dt>
              <dd>{{ impact[key] }}</dd>
            </div>
          </dl>
          <V2PageState
            v-else
            kind="empty"
            title="请选择成本科目"
            description="引用计数以服务端租户和项目数据权限为准。"
          />
        </V2Card>
        <V2Card title="待归类与入账前覆盖">
          <V2PageState
            v-if="!options.pendingClassifications.length"
            kind="empty"
            title="暂无待处理归类"
            description="未命中、规则冲突或待入账的来源会在这里留痕。"
          />
          <div v-else class="cost-subject-page__table-wrap">
            <table>
              <thead>
                <tr>
                  <th>业务来源标识</th>
                  <th>项目</th>
                  <th>系统结果</th>
                  <th>原因</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="record in options.pendingClassifications"
                  :key="String(record.caseId || record.snapshotId)"
                >
                  <th>
                    {{ rowText(record, 'sourceType') }} · {{ rowText(record, 'sourceId') }} /
                    {{ rowText(record, 'sourceItemId') }}
                  </th>
                  <td>{{ rowText(record, 'projectId') }}</td>
                  <td>
                    {{ rowText(record, 'matchedSubjectCode') }}
                    {{ rowText(record, 'matchedSubjectName') }}
                  </td>
                  <td>{{ rowText(record, ['error', 'Message'].join('')) }}</td>
                  <td>
                    <V2Button v-if="canOverride" size="small" @click="openOverride(record)"
                      >财务覆盖</V2Button
                    ><span v-else>—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
      </section>

      <section
        v-else-if="activeTab === 'project-reconciliation'"
        id="cost-trace-panel-project-reconciliation"
        role="tabpanel"
        aria-labelledby="cost-trace-tab-project-reconciliation"
      >
        <V2Card title="项目成本对账">
          <form class="cost-subject-page__query" @submit.prevent="queryReconciliation">
            <V2Select
              v-model="reconciliationProjectId"
              :options="projectOptions"
              label="项目"
              required
            />
            <V2Button type="submit">生成对账</V2Button>
          </form>
          <dl v-if="reconciliation" class="cost-subject-page__facts">
            <div>
              <dt>目标成本</dt>
              <dd>{{ formatAmount(reconciliation.targetCost) }}</dd>
            </div>
            <div>
              <dt>实际成本</dt>
              <dd>{{ formatAmount(reconciliation.actualCost) }}</dd>
            </div>
            <div>
              <dt>投标转入</dt>
              <dd>{{ formatAmount(reconciliation.bidTransferred) }}</dd>
            </div>
            <div>
              <dt>财务分摊</dt>
              <dd>{{ formatAmount(reconciliation.financeAllocated) }}</dd>
            </div>
            <div>
              <dt>待归类</dt>
              <dd>{{ reconciliation.unclassifiedCount ?? 0 }}</dd>
            </div>
            <div>
              <dt>目标差异</dt>
              <dd>
                {{
                  formatAmount(
                    Number(reconciliation.actualCost ?? 0) - Number(reconciliation.targetCost ?? 0),
                  )
                }}
              </dd>
            </div>
          </dl>
          <V2PageState
            v-else
            kind="empty"
            title="按项目生成对账"
            description="对账统一采用已确认、已归类成本事实。"
          />
        </V2Card>

        <V2Card title="历史重算与关闭后调整">
          <V2PageState
            v-if="!recalculations.length"
            kind="empty"
            title="暂无重算批次"
            description="试算不会覆盖已过账原始事实。"
          />
          <div v-else class="cost-subject-page__table-wrap">
            <table>
              <thead>
                <tr>
                  <th>批次编号</th>
                  <th>范围</th>
                  <th>类型</th>
                  <th>原事实</th>
                  <th>变化</th>
                  <th>待归类</th>
                  <th>原金额</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in recalculations" :key="String(record.id)">
                  <th>{{ rowText(record, 'batchCode') }}</th>
                  <td>{{ rowText(record, 'projectName') }}</td>
                  <td>
                    {{
                      record.batchType === 'POST_CLOSE_ADJUSTMENT'
                        ? '关闭后调整'
                        : record.batchType === 'REVERSAL'
                          ? '重算冲销'
                          : '历史重算'
                    }}
                  </td>
                  <td>{{ record.originalFactCount }}</td>
                  <td>{{ record.changedFactCount }}</td>
                  <td>{{ record.unclassifiedCount }}</td>
                  <td>{{ formatAmount(record.originalTotal) }}</td>
                  <td>{{ statusLabel(String(record.status)) }}</td>
                  <td>
                    <V2Cluster>
                      <V2Button
                        v-if="
                          canRecalculateSubmit &&
                          ['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(String(record.status)) &&
                          Number(record.unclassifiedCount) === 0
                        "
                        size="small"
                        :loading="saving"
                        @click="submitRecalculation(record)"
                        >提交审批</V2Button
                      >
                      <V2Button
                        v-if="canRecalculate && record.status === 'DRAFT'"
                        size="small"
                        variant="secondary"
                        :loading="saving"
                        @click="cancelRecalculation(record)"
                        >取消草稿</V2Button
                      >
                      <V2Button
                        v-if="
                          canReverse &&
                          record.status === 'POSTED' &&
                          record.batchType !== 'REVERSAL'
                        "
                        size="small"
                        variant="danger"
                        @click="openReversal('RECALCULATION', record)"
                        >申请冲销</V2Button
                      >
                    </V2Cluster>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>

        <V2Card title="冲销申请">
          <V2PageState
            v-if="!reversals.length"
            kind="empty"
            title="暂无冲销申请"
            description="系统先判断资格，用户只选择原因并提交审批。"
          />
          <div v-else class="cost-subject-page__table-wrap">
            <table>
              <thead>
                <tr>
                  <th>申请编号</th>
                  <th>对象</th>
                  <th>项目</th>
                  <th>原因</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in reversals" :key="String(record.id)">
                  <th>{{ rowText(record, 'requestCode') }}</th>
                  <td>{{ rowText(record, 'targetType') }} · {{ rowText(record, 'targetId') }}</td>
                  <td>{{ rowText(record, 'projectName') }}</td>
                  <td>{{ rowText(record, 'reason') }}</td>
                  <td>{{ statusLabel(String(record.status)) }}</td>
                  <td>
                    <V2Cluster>
                      <V2Button
                        v-if="
                          canReverseSubmit &&
                          ['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(String(record.status))
                        "
                        size="small"
                        :loading="saving"
                        @click="submitReversal(record)"
                        >提交审批</V2Button
                      ><V2Button
                        v-if="
                          canReverse &&
                          String(record.createdBy) === String(session.userInfo?.userId) &&
                          ['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(String(record.status))
                        "
                        size="small"
                        variant="secondary"
                        :loading="saving"
                        @click="cancelReversal(record)"
                        >取消申请</V2Button
                      ><span
                        v-if="
                          !canReverseSubmit &&
                          !canReverse &&
                          !['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(String(record.status))
                        "
                        >—</span
                      >
                    </V2Cluster>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
      </section>

      <section
        v-else-if="activeTab === 'bid-transfer'"
        id="cost-trace-panel-bid-transfer"
        role="tabpanel"
        aria-labelledby="cost-trace-tab-bid-transfer"
      >
        <V2Card title="投标成本转入向导">
          <template #actions
            ><V2Button v-if="canBidCreate" size="small" @click="openTransfer"
              >新建转入申请</V2Button
            ></template
          >
          <V2PageState
            v-if="!transferRequests.length"
            kind="empty"
            title="暂无转入申请"
            description="按项目、中标成本、目标版本和规则方案逐步选择。"
          />
          <div v-else class="cost-subject-page__table-wrap">
            <table>
              <thead>
                <tr>
                  <th>申请编号</th>
                  <th>投标成本</th>
                  <th>项目</th>
                  <th>目标版本</th>
                  <th>金额</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in transferRequests" :key="record.id">
                  <th>{{ record.requestCode }}</th>
                  <td>{{ bidCostLabel(record) }}</td>
                  <td>{{ requestProjectLabel(record) }}</td>
                  <td>{{ targetVersionLabel(record) }}</td>
                  <td>{{ formatAmount(record.totalAmount) }}</td>
                  <td>{{ statusLabel(record.status) }}</td>
                  <td>
                    <V2Cluster
                      ><V2Button
                        v-if="
                          canBidSubmit && ['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(record.status)
                        "
                        size="small"
                        :loading="saving"
                        @click="submitTransfer(record)"
                        >提交审批</V2Button
                      ><V2Button
                        v-if="canBidCreate && record.status === 'DRAFT'"
                        size="small"
                        variant="secondary"
                        :loading="saving"
                        @click="cancelTransfer(record)"
                        >取消草稿</V2Button
                      ><span v-if="record.status !== 'DRAFT' && !canBidSubmit">—</span></V2Cluster
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
        <V2Card title="已过账转入记录">
          <V2PageState
            v-if="!transfers.length"
            kind="empty"
            title="暂无转入记录"
            description="审批通过后生成版本化转入记录。"
          />
          <div v-else class="cost-subject-page__table-wrap">
            <table>
              <thead>
                <tr>
                  <th>转入编号</th>
                  <th>项目</th>
                  <th>目标版本</th>
                  <th>金额</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in pagedTransfers" :key="String(record.id)">
                  <th>{{ rowText(record, 'transferCode') }}</th>
                  <td>{{ rowText(record, 'bidProjectName') }}</td>
                  <td>{{ rowText(record, 'versionNo') }}</td>
                  <td>{{ formatAmount(record.totalAmount) }}</td>
                  <td>{{ statusLabel(String(record.status)) }}</td>
                  <td>
                    <V2Button
                      v-if="canReverse && record.status === 'POSTED' && !record.reversalOfId"
                      size="small"
                      variant="danger"
                      @click="openReversal('BID_TRANSFER', record)"
                      >申请冲销</V2Button
                    ><span v-else>—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer
            ><V2Pagination
              :total="transfers.length"
              :page-no="transferPageNo"
              :page-size="pageSize"
              label="投标转入分页"
              @update:page-no="transferPageNo = $event"
          /></template>
        </V2Card>
      </section>

      <section
        v-else
        id="cost-trace-panel-finance-allocation"
        role="tabpanel"
        aria-labelledby="cost-trace-tab-finance-allocation"
      >
        <V2Card title="财务费用分摊向导">
          <template #actions
            ><V2Button v-if="canAllocationCreate" size="small" @click="openAllocation"
              >新建分摊申请</V2Button
            ></template
          >
          <V2PageState
            v-if="!allocationRequests.length"
            kind="empty"
            title="暂无分摊申请"
            description="仅显示可分摊的手工成本凭证或已审批费用来源。"
          />
          <div v-else class="cost-subject-page__table-wrap">
            <table>
              <thead>
                <tr>
                  <th>申请编号</th>
                  <th>项目</th>
                  <th>来源</th>
                  <th>期间</th>
                  <th>金额</th>
                  <th>科目</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in allocationRequests" :key="record.id">
                  <th>{{ record.requestCode }}</th>
                  <td>{{ requestProjectLabel(record) }}</td>
                  <td>{{ allocationSourceLabel(record) }}</td>
                  <td>{{ record.accountingPeriod }}</td>
                  <td>{{ formatAmount(record.sourceAmount) }}</td>
                  <td>{{ allocationSubjectLabel(record) }}</td>
                  <td>{{ statusLabel(record.status) }}</td>
                  <td>
                    <V2Cluster
                      ><V2Button
                        v-if="
                          canAllocationSubmit &&
                          ['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(record.status)
                        "
                        size="small"
                        :loading="saving"
                        @click="submitAllocation(record)"
                        >提交审批</V2Button
                      ><V2Button
                        v-if="canAllocationCreate && record.status === 'DRAFT'"
                        size="small"
                        variant="secondary"
                        :loading="saving"
                        @click="cancelAllocation(record)"
                        >取消草稿</V2Button
                      ><span v-if="record.status !== 'DRAFT' && !canAllocationSubmit"
                        >—</span
                      ></V2Cluster
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
        <V2Card title="已过账分摊记录">
          <V2PageState
            v-if="!allocations.length"
            kind="empty"
            title="暂无分摊记录"
            description="同一业务链只在权威成本确认节点生成一次成本事实。"
          />
          <div v-else class="cost-subject-page__table-wrap">
            <table>
              <thead>
                <tr>
                  <th>批次编号</th>
                  <th>来源</th>
                  <th>依据</th>
                  <th>期间</th>
                  <th>金额</th>
                  <th>科目</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in pagedAllocations" :key="String(record.id)">
                  <th>{{ rowText(record, 'batchCode') }}</th>
                  <td>{{ rowText(record, 'sourceCode') }}</td>
                  <td>{{ allocationBasisLabel(String(record.allocationBasis)) }}</td>
                  <td>{{ rowText(record, 'accountingPeriod') }}</td>
                  <td>{{ formatAmount(record.sourceAmount) }}</td>
                  <td>{{ rowText(record, 'subjectName') }}</td>
                  <td>{{ statusLabel(String(record.status)) }}</td>
                  <td>
                    <V2Button
                      v-if="canReverse && record.status === 'POSTED' && !record.reversalOfId"
                      size="small"
                      variant="danger"
                      @click="openReversal('FINANCE_ALLOCATION', record)"
                      >申请冲销</V2Button
                    ><span v-else>—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer
            ><V2Pagination
              :total="allocations.length"
              :page-no="allocationPageNo"
              :page-size="pageSize"
              label="财务分摊分页"
              @update:page-no="allocationPageNo = $event"
          /></template>
        </V2Card>
      </section>
    </template>

    <V2Dialog
      :open="transferDialog"
      title="投标成本转入向导"
      description="项目 → 投标成本 → 目标成本版本 → 规则方案 → 预览 → 审批"
      panel-class="v2-dialog-wide"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="transferDialog = false"
    >
      <form
        id="transfer-form"
        class="cost-subject-page__form cost-subject-page__form--wide"
        @submit.prevent="saveTransfer"
      >
        <V2Select
          v-model="transferForm.bidCostId"
          :options="bidOptions"
          label="已中标投标成本"
          required
          @update:model-value="transferForm.targetId = ''"
        />
        <V2Input
          :model-value="
            selectedBid ? `${selectedBid.projectCode} · ${selectedBid.projectName}` : ''
          "
          label="目标项目"
          disabled
        />
        <V2Select
          v-model="transferForm.targetId"
          :options="transferTargetOptions"
          label="可编辑目标成本版本"
          required
        />
        <V2Select
          v-model="transferForm.mappingVersionId"
          :options="activePlanOptions"
          label="启用成本规则方案"
          required
        />
        <V2Input v-model="transferForm.remark" label="转入说明" class="cost-subject-page__span" />
        <V2Card title="转入预览" class="cost-subject-page__span"
          ><p class="cost-subject-page__hint">
            系统按冻结的投标成本事实与方案映射试算；源事实变化时提交失败关闭。
          </p></V2Card
        >
      </form>
      <template #footer
        ><V2Button variant="secondary" @click="transferDialog = false">取消</V2Button
        ><V2Button type="submit" form="transfer-form" :loading="saving"
          >保存试算草稿</V2Button
        ></template
      >
    </V2Dialog>

    <V2Dialog
      :open="allocationDialog"
      title="财务费用分摊向导"
      description="来源 → 期间 → 科目 → 项目与依据 → 试算 → 审批"
      panel-class="v2-dialog-wide"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="allocationDialog = false"
    >
      <form
        id="allocation-form"
        class="cost-subject-page__form cost-subject-page__form--wide"
        @submit.prevent="saveAllocation"
      >
        <V2Select
          v-model="allocationForm.sourceKey"
          :options="financeSourceOptions"
          label="可分摊财务来源"
          required
        />
        <V2Input v-model="allocationForm.accountingPeriod" type="month" label="会计期间" required />
        <V2Select
          v-model="allocationForm.costSubjectId"
          :options="subjectOptions"
          label="目标末级成本科目"
          required
        />
        <V2Select
          v-model="allocationForm.allocationBasis"
          :options="allocationBasisOptions"
          label="分摊依据"
          required
        />
        <V2Input
          v-model="allocationForm.remark"
          label="覆盖原因或分摊说明"
          class="cost-subject-page__span"
          required
        />
        <fieldset class="cost-subject-page__lines cost-subject-page__span">
          <legend>项目与依据</legend>
          <div
            v-for="(line, index) in allocationForm.lines"
            :key="index"
            class="cost-subject-page__line-grid"
          >
            <V2Select
              v-model="line.projectId"
              :options="projectOptions"
              :label="`项目 ${index + 1}`"
              required
            /><V2Input
              v-model="line.basisValue"
              type="number"
              :label="`依据值 ${index + 1}`"
              required
            /><V2Button
              type="button"
              size="small"
              variant="secondary"
              :disabled="allocationForm.lines.length === 1"
              @click="allocationForm.lines.splice(index, 1)"
              >移除</V2Button
            >
          </div>
          <V2Button type="button" size="small" variant="secondary" @click="addAllocationLine"
            >增加项目</V2Button
          >
        </fieldset>
        <V2Card title="分摊试算" class="cost-subject-page__span"
          ><p class="cost-subject-page__hint">
            来源余额 {{ formatAmount(selectedFinanceSource?.remainingAmount ?? 0) }}；依据合计
            {{ allocationBasisTotal }}。最终金额由服务端按分尾差守恒计算。
          </p></V2Card
        >
      </form>
      <template #footer
        ><V2Button variant="secondary" @click="allocationDialog = false">取消</V2Button
        ><V2Button type="submit" form="allocation-form" :loading="saving"
          >保存试算草稿</V2Button
        ></template
      >
    </V2Dialog>

    <V2Dialog
      :open="recalculationDialog"
      title="历史重算与关闭后调整"
      description="冻结基准、保存旧快照、输出差异；审批后追加调整事实，不覆盖历史。"
      panel-class="v2-dialog-wide"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="recalculationDialog = false"
    >
      <form
        id="recalculation-form"
        class="cost-subject-page__form"
        @submit.prevent="saveRecalculation"
      >
        <V2Select
          v-model="recalculationForm.batchType"
          :options="[
            { value: 'HISTORY_RECALCULATION', label: '历史全量重算' },
            { value: 'POST_CLOSE_ADJUSTMENT', label: '关闭后财务调整' },
          ]"
          label="批次类型"
          required
        />
        <V2Select
          v-model="recalculationForm.projectId"
          :options="[{ value: '', label: '全部可访问在建项目' }, ...projectOptions]"
          label="项目范围"
        />
        <V2Select
          v-model="recalculationForm.ruleVersionId"
          :options="activePlanOptions"
          label="启用规则方案"
          required
        />
        <V2Input
          v-model="recalculationForm.cutoffAt"
          type="datetime-local"
          label="冻结基准时间"
          required
        />
        <V2Input
          v-model="recalculationForm.reason"
          label="重算或调整原因"
          required
          class="cost-subject-page__span"
        />
      </form>
      <template #footer
        ><V2Button variant="secondary" @click="recalculationDialog = false">取消</V2Button
        ><V2Button type="submit" form="recalculation-form" :loading="saving"
          >生成差异试算</V2Button
        ></template
      >
    </V2Dialog>

    <V2Dialog
      :open="reversalDialog"
      title="申请成本冲销"
      description="资格由系统判断；审批通过后生成反向事实，原记录和审计链保持不变。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="reversalDialog = false"
    >
      <form id="reversal-form" class="cost-subject-page__form" @submit.prevent="saveReversal">
        <V2Input :model-value="reversalForm.targetLabel" label="冲销对象" disabled />
        <V2Input v-model="reversalForm.reason" label="冲销原因" required />
      </form>
      <template #footer
        ><V2Button variant="secondary" @click="reversalDialog = false">取消</V2Button
        ><V2Button type="submit" form="reversal-form" :loading="saving"
          >保存冲销申请</V2Button
        ></template
      >
    </V2Dialog>

    <V2Dialog
      :open="overrideDialog"
      title="财务覆盖成本归类"
      description="仅首次入账前可覆盖；系统保留原匹配、规则版本和审计轨迹。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="overrideDialog = false"
    >
      <form
        id="classification-override-form"
        class="cost-subject-page__form"
        @submit.prevent="saveOverride"
      >
        <V2Input :model-value="overrideForm.sourceLabel" label="业务来源" disabled />
        <V2Select
          v-model="overrideForm.costSubjectId"
          :options="subjectOptions"
          label="目标末级成本科目"
          required
        />
        <V2Input
          v-model="overrideForm.reason"
          label="覆盖原因"
          required
          class="cost-subject-page__span"
        />
      </form>
      <template #footer
        ><V2Button variant="secondary" @click="overrideDialog = false">取消</V2Button
        ><V2Button type="submit" form="classification-override-form" :loading="saving"
          >保存覆盖</V2Button
        ></template
      >
    </V2Dialog>
  </V2Stack>
</template>
