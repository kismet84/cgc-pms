<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { formatAmount } from '@/shared/display'
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
import {
  createBidTransferRequest,
  createFinanceAllocationRequest,
  loadBidTransferRequests,
  loadFinanceAllocationRequests,
  loadBidTransfers,
  loadCostSubjectReconciliation,
  loadFinanceAllocations,
  loadSubjectImpact,
  reverseBidTransfer,
  reverseFinanceAllocation,
  submitBidTransferRequest,
  submitFinanceAllocationRequest,
  type BidTransferRequestRecord,
  type CostSubjectAuditRow,
  type FinanceAllocationRequestRecord,
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
  sourceTypeOptions,
  statusLabel,
  targetVersionLabel,
} from './model'
import './styles.css'

const session = useSessionStore()
const pageSize = 10
const loading = ref(false)
const saving = ref(false)
const error = ref('')
let controller: AbortController | null = null

const can = (permission: string) => session.hasAdminOrPermission(permission)
const canBidTransferCreate = computed(() => can('cost:subject:bid-transfer'))
const canBidTransferSubmit = computed(() => can('cost:subject:transfer:submit'))
const canFinanceAllocationCreate = computed(() => can('cost:subject:finance-allocate'))
const canFinanceAllocationSubmit = computed(() => can('cost:subject:allocation:submit'))

const impactSubjectId = ref('')
const reconciliationProjectId = ref('')
const impact = ref<SubjectImpactRecord | null>(null)
const reconciliation = ref<CostSubjectAuditRow | null>(null)
const transfers = ref<CostSubjectAuditRow[]>([])
const allocations = ref<CostSubjectAuditRow[]>([])
const transferRequests = ref<BidTransferRequestRecord[]>([])
const allocationRequests = ref<FinanceAllocationRequestRecord[]>([])
const transferPageNo = ref(1)
const allocationPageNo = ref(1)
const transferDialog = ref(false)
const allocationDialog = ref(false)
const reverseTarget = ref<CostSubjectAuditRow | null>(null)
const reverseKind = ref<'transfer' | 'allocation'>('transfer')
const transferForm = reactive({
  bidCostId: '',
  projectId: '',
  targetId: '',
  mappingVersionId: '',
  idempotencyKey: '',
  remark: '',
})
const allocationForm = reactive({
  sourceType: 'ACCOUNTING_ENTRY_LINE',
  sourceId: '',
  allocationBasis: 'BENEFIT_AMOUNT',
  accountingPeriod: '',
  costSubjectId: '',
  idempotencyKey: '',
  remark: '',
  lines: [{ projectId: '', basisValue: '1' }],
})
const reverseForm = reactive({ approvalInstanceId: '', idempotencyKey: '', remark: '' })

const pagedTransfers = computed(() => pageSlice(transfers.value, transferPageNo.value))
const pagedAllocations = computed(() => pageSlice(allocations.value, allocationPageNo.value))

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

async function loadTrace(signal?: AbortSignal): Promise<void> {
  transferPageNo.value = 1
  allocationPageNo.value = 1
  ;[transferRequests.value, allocationRequests.value, transfers.value, allocations.value] =
    await Promise.all([
      loadBidTransferRequests(signal),
      loadFinanceAllocationRequests(signal),
      loadBidTransfers(signal),
      loadFinanceAllocations(signal),
    ])
}

async function queryImpact(): Promise<void> {
  if (!impactSubjectId.value.trim()) {
    showToast('warning', '科目缺失', '请输入成本科目标识。')
    return
  }
  try {
    impact.value = await loadSubjectImpact(impactSubjectId.value)
  } catch (value) {
    impact.value = null
    showToast('error', '影响查询失败', messageOf(value))
  }
}

async function queryReconciliation(): Promise<void> {
  if (!reconciliationProjectId.value.trim()) {
    showToast('warning', '项目缺失', '请输入项目标识。')
    return
  }
  try {
    reconciliation.value = await loadCostSubjectReconciliation(reconciliationProjectId.value)
  } catch (value) {
    reconciliation.value = null
    showToast('error', '项目对账失败', messageOf(value))
  }
}

async function submitTransfer(): Promise<void> {
  if (
    !transferForm.bidCostId.trim() ||
    !transferForm.projectId.trim() ||
    !transferForm.targetId.trim() ||
    !transferForm.mappingVersionId.trim() ||
    !transferForm.idempotencyKey.trim()
  ) {
    showToast('warning', '信息不完整', '转入对象、映射版本和幂等键不能为空。')
    return
  }
  saving.value = true
  try {
    await createBidTransferRequest({ ...transferForm })
    transferDialog.value = false
    await loadTrace()
    showToast('success', '转入草稿已保存', '请在申请清单提交审批。')
  } catch (value) {
    showToast('error', '转入失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function addAllocationLine(): void {
  allocationForm.lines.push({ projectId: '', basisValue: '1' })
}

function removeAllocationLine(index: number): void {
  if (allocationForm.lines.length > 1) allocationForm.lines.splice(index, 1)
}

async function submitAllocation(): Promise<void> {
  if (
    !allocationForm.sourceId.trim() ||
    !allocationForm.accountingPeriod.trim() ||
    !allocationForm.costSubjectId.trim() ||
    !allocationForm.idempotencyKey.trim() ||
    allocationForm.lines.some(
      (line) => !line.projectId.trim() || !line.basisValue.trim() || Number(line.basisValue) <= 0,
    )
  ) {
    showToast('warning', '信息不完整', '来源、期间、科目、幂等键和项目依据必须有效。')
    return
  }
  saving.value = true
  try {
    await createFinanceAllocationRequest({
      sourceType: allocationForm.sourceType,
      sourceId: allocationForm.sourceId.trim(),
      allocationBasis: allocationForm.allocationBasis,
      accountingPeriod: allocationForm.accountingPeriod.trim(),
      costSubjectId: allocationForm.costSubjectId.trim(),
      idempotencyKey: allocationForm.idempotencyKey.trim(),
      remark: allocationForm.remark.trim(),
      lines: allocationForm.lines.map((line) => ({
        projectId: line.projectId.trim(),
        basisValue: line.basisValue.trim(),
      })),
    })
    allocationDialog.value = false
    await loadTrace()
    showToast('success', '分摊草稿已保存', '请在申请清单提交审批。')
  } catch (value) {
    showToast('error', '分摊失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function submitTransferWorkflow(record: BidTransferRequestRecord): Promise<void> {
  if (saving.value) return
  saving.value = true
  try {
    await submitBidTransferRequest(record.id)
    await loadTrace()
    showToast('success', '转入申请已提交', '审批状态已刷新。')
  } catch (value) {
    showToast('error', '转入申请提交失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function submitAllocationWorkflow(record: FinanceAllocationRequestRecord): Promise<void> {
  if (saving.value) return
  saving.value = true
  try {
    await submitFinanceAllocationRequest(record.id)
    await loadTrace()
    showToast('success', '分摊申请已提交', '审批状态已刷新。')
  } catch (value) {
    showToast('error', '分摊申请提交失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openReverse(kind: 'transfer' | 'allocation', row: CostSubjectAuditRow): void {
  reverseKind.value = kind
  reverseTarget.value = row
  Object.assign(reverseForm, { approvalInstanceId: '', idempotencyKey: '', remark: '' })
}

async function submitReverse(): Promise<void> {
  if (
    !reverseTarget.value ||
    !reverseForm.approvalInstanceId.trim() ||
    !reverseForm.idempotencyKey.trim()
  ) {
    showToast('warning', '信息不完整', '审批实例和幂等键不能为空。')
    return
  }
  saving.value = true
  try {
    const id = String(reverseTarget.value.id)
    if (reverseKind.value === 'transfer') {
      await reverseBidTransfer(
        id,
        reverseForm.approvalInstanceId,
        reverseForm.idempotencyKey,
        reverseForm.remark,
      )
    } else {
      await reverseFinanceAllocation(
        id,
        reverseForm.approvalInstanceId,
        reverseForm.idempotencyKey,
        reverseForm.remark,
      )
    }
    reverseTarget.value = null
    await loadTrace()
    showToast('success', '冲销已完成', '反向事实已刷新。')
  } catch (value) {
    showToast('error', '冲销失败', messageOf(value))
  } finally {
    saving.value = false
  }
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
  if (error.value) showToast('error', '刷新失败', error.value)
  else showToast('success', '已刷新', '当前内容已更新。')
}

onMounted(() => void loadPage())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="cost-subject-page" :gap="4">
    <V2Card title="影响与转入追踪" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="refreshTrace">刷新</V2Button>
      </template>
    </V2Card>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在读取成本科目事实"
      description="请稍候。"
    />
    <V2PageState v-else-if="error" kind="error" title="成本科目加载失败" :description="error">
      <template #actions><V2Button @click="loadPage">重试</V2Button></template>
    </V2PageState>

    <template v-else>
      <V2Card title="影响分析与项目对账">
        <div class="cost-subject-page__query cost-subject-page__query--wide">
          <form @submit.prevent="queryImpact">
            <V2Input v-model="impactSubjectId" label="成本科目标识" required />
            <V2Button type="submit">查询引用影响</V2Button>
          </form>
          <form @submit.prevent="queryReconciliation">
            <V2Input v-model="reconciliationProjectId" label="项目标识" required />
            <V2Button type="submit">项目对账</V2Button>
          </form>
        </div>
        <template #actions>
          <V2Cluster>
            <V2Button v-if="canBidTransferCreate" size="small" @click="transferDialog = true">
              新建转入申请
            </V2Button>
            <V2Button
              v-if="canFinanceAllocationCreate"
              size="small"
              @click="allocationDialog = true"
            >
              新建分摊申请
            </V2Button>
          </V2Cluster>
        </template>
      </V2Card>

      <div v-if="impact || reconciliation" class="cost-subject-page__columns">
        <V2Card title="科目引用影响">
          <dl v-if="impact" class="cost-subject-page__facts">
            <div v-for="[key, label] in impactLabels" :key="key">
              <dt>{{ label }}</dt>
              <dd>{{ impact[key] }}</dd>
            </div>
          </dl>
          <V2PageState v-else kind="empty" title="尚未查询" description="输入成本科目标识。" />
        </V2Card>
        <V2Card title="项目成本对账">
          <dl v-if="reconciliation" class="cost-subject-page__facts">
            <div v-for="(value, key) in reconciliation" :key="key">
              <dt>{{ key }}</dt>
              <dd>{{ value }}</dd>
            </div>
          </dl>
          <V2PageState v-else kind="empty" title="尚未查询" description="输入项目标识。" />
        </V2Card>
      </div>

      <V2Card title="投标成本转入申请">
        <V2PageState
          v-if="!transferRequests.length"
          kind="empty"
          title="暂无转入申请"
          description="先保存草稿，再从申请清单提交审批。"
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
                <th>审批实例</th>
                <th>终态记录</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in transferRequests" :key="record.id">
                <th scope="row">{{ record.requestCode || record.id }}</th>
                <td>{{ bidCostLabel(record) }}</td>
                <td>{{ requestProjectLabel(record) }}</td>
                <td>{{ targetVersionLabel(record) }}</td>
                <td>{{ formatAmount(record.totalAmount) }}</td>
                <td>{{ statusLabel(record.status) }}</td>
                <td>{{ record.approvalInstanceId || '—' }}</td>
                <td>{{ record.finalTransferId || '—' }}</td>
                <td>
                  <V2Button
                    v-if="
                      canBidTransferSubmit &&
                      ['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(record.status)
                    "
                    size="small"
                    :loading="saving"
                    @click="submitTransferWorkflow(record)"
                  >
                    提交审批
                  </V2Button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>

      <V2Card title="项目财务费用分摊申请">
        <V2PageState
          v-if="!allocationRequests.length"
          kind="empty"
          title="暂无分摊申请"
          description="先保存草稿，再从申请清单提交审批。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>申请编号</th>
                <th>项目</th>
                <th>来源</th>
                <th>依据</th>
                <th>期间</th>
                <th>金额</th>
                <th>科目</th>
                <th>状态</th>
                <th>审批实例</th>
                <th>终态批次</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in allocationRequests" :key="record.id">
                <th scope="row">{{ record.requestCode || record.id }}</th>
                <td>{{ requestProjectLabel(record) }}</td>
                <td>{{ allocationSourceLabel(record) }}</td>
                <td>{{ allocationBasisLabel(record.allocationBasis) }}</td>
                <td>{{ record.accountingPeriod }}</td>
                <td>{{ formatAmount(record.sourceAmount) }}</td>
                <td>{{ allocationSubjectLabel(record) }}</td>
                <td>{{ statusLabel(record.status) }}</td>
                <td>{{ record.approvalInstanceId || '—' }}</td>
                <td>{{ record.finalBatchId || '—' }}</td>
                <td>
                  <V2Button
                    v-if="
                      canFinanceAllocationSubmit &&
                      ['DRAFT', 'REJECTED', 'WITHDRAWN'].includes(record.status)
                    "
                    size="small"
                    :loading="saving"
                    @click="submitAllocationWorkflow(record)"
                  >
                    提交审批
                  </V2Button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>

      <V2Card title="投标成本转入记录">
        <V2PageState
          v-if="!transfers.length"
          kind="empty"
          title="暂无转入记录"
          description="页面不推导目标成本金额或可冲销状态。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>转入编号</th>
                <th>投标项目</th>
                <th>目标版本</th>
                <th>金额</th>
                <th>状态</th>
                <th>审批实例</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedTransfers" :key="String(record.id)">
                <th scope="row">{{ rowText(record, 'transferCode') }}</th>
                <td>{{ rowText(record, 'bidProjectName') }}</td>
                <td>{{ rowText(record, 'versionNo') }}</td>
                <td>{{ formatAmount(rowText(record, 'totalAmount')) }}</td>
                <td>{{ rowText(record, 'status') }}</td>
                <td>{{ rowText(record, 'approvalInstanceId') }}</td>
                <td>
                  <V2Button
                    v-if="
                      canBidTransferCreate && record.status === 'POSTED' && !record.reversalOfId
                    "
                    size="small"
                    variant="danger"
                    @click="openReverse('transfer', record)"
                  >
                    冲销
                  </V2Button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="transfers.length"
            :page-no="transferPageNo"
            :page-size="pageSize"
            label="投标成本转入记录分页"
            @update:page-no="transferPageNo = $event"
          />
        </template>
      </V2Card>

      <V2Card title="项目财务费用分摊记录">
        <V2PageState
          v-if="!allocations.length"
          kind="empty"
          title="暂无分摊记录"
          description="金额、状态和项目范围均以系统记录为准。"
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
                <th scope="row">{{ rowText(record, 'batchCode') }}</th>
                <td>{{ rowText(record, 'sourceType') }}</td>
                <td>{{ rowText(record, 'allocationBasis') }}</td>
                <td>{{ rowText(record, 'accountingPeriod') }}</td>
                <td>{{ formatAmount(rowText(record, 'sourceAmount')) }}</td>
                <td>{{ rowText(record, 'subjectName') }}</td>
                <td>{{ rowText(record, 'status') }}</td>
                <td>
                  <V2Button
                    v-if="
                      canFinanceAllocationCreate &&
                      record.status === 'POSTED' &&
                      !record.reversalOfId
                    "
                    size="small"
                    variant="danger"
                    @click="openReverse('allocation', record)"
                  >
                    冲销
                  </V2Button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="allocations.length"
            :page-no="allocationPageNo"
            :page-size="pageSize"
            label="财务费用分摊记录分页"
            @update:page-no="allocationPageNo = $event"
          />
        </template>
      </V2Card>
    </template>

    <V2Dialog
      :open="transferDialog"
      title="新建投标成本转入申请"
      description="保存草稿后从申请清单提交审批；审批通过后系统生成转入记录。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="transferDialog = false"
    >
      <form id="transfer-form" class="cost-subject-page__form" @submit.prevent="submitTransfer">
        <V2Input v-model="transferForm.bidCostId" label="投标成本标识" required />
        <V2Input v-model="transferForm.projectId" label="中标项目标识" required />
        <V2Input v-model="transferForm.targetId" label="目标成本版本标识" required />
        <V2Input v-model="transferForm.mappingVersionId" label="启用映射版本标识" required />
        <V2Input v-model="transferForm.idempotencyKey" label="幂等键" required />
        <V2Input v-model="transferForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="transferDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="transfer-form" :loading="saving">保存草稿</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="allocationDialog"
      title="新建项目财务费用分摊申请"
      description="保存草稿后从申请清单提交审批；审批通过后系统生成分摊记录。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="allocationDialog = false"
    >
      <form id="allocation-form" class="cost-subject-page__form" @submit.prevent="submitAllocation">
        <V2Select
          v-model="allocationForm.sourceType"
          :options="sourceTypeOptions"
          label="来源类型"
        />
        <V2Input v-model="allocationForm.sourceId" label="来源标识" required />
        <V2Select
          v-model="allocationForm.allocationBasis"
          :options="allocationBasisOptions"
          label="分摊依据"
        />
        <V2Input
          v-model="allocationForm.accountingPeriod"
          label="会计期间"
          placeholder="YYYY-MM"
          required
        />
        <V2Input v-model="allocationForm.costSubjectId" label="财务费用末级科目标识" required />
        <V2Input v-model="allocationForm.idempotencyKey" label="幂等键" required />
        <V2Input v-model="allocationForm.remark" label="备注" />
        <fieldset class="cost-subject-page__lines">
          <legend>项目分摊依据</legend>
          <div v-for="(line, index) in allocationForm.lines" :key="index">
            <V2Input v-model="line.projectId" :label="`项目 ${index + 1} 标识`" required />
            <V2Input v-model="line.basisValue" :label="`项目 ${index + 1} 依据值`" required />
            <V2Button
              type="button"
              size="small"
              variant="secondary"
              :disabled="allocationForm.lines.length === 1"
              @click="removeAllocationLine(index)"
            >
              移除
            </V2Button>
          </div>
          <V2Button type="button" size="small" variant="secondary" @click="addAllocationLine">
            增加项目
          </V2Button>
        </fieldset>
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="allocationDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="allocation-form" :loading="saving">保存草稿</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="Boolean(reverseTarget)"
      :title="reverseKind === 'transfer' ? '冲销投标成本转入' : '冲销财务费用分摊'"
      description="冲销生成反向事实，不修改原记录。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="reverseTarget = null"
    >
      <form id="reverse-form" class="cost-subject-page__form" @submit.prevent="submitReverse">
        <V2Input v-model="reverseForm.approvalInstanceId" label="审批实例标识" required />
        <V2Input v-model="reverseForm.idempotencyKey" label="幂等键" required />
        <V2Input v-model="reverseForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="reverseTarget = null"
          >取消</V2Button
        >
        <V2Button type="submit" form="reverse-form" :loading="saving">确认冲销</V2Button>
      </template>
    </V2Dialog>
  </V2Stack>
</template>
