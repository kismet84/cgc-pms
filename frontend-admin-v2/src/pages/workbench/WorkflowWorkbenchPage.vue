<script setup lang="ts">
import {
  canPerformWorkflowAction,
  type WorkflowCc,
  type WorkflowInstance,
  type WorkflowMine,
  type WorkflowRecord,
  type WorkflowTab,
  type WorkflowTask,
  type WorkflowUiAction,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2Dialog,
  V2GlassButton,
  V2Input,
  V2PageState,
  V2Select,
} from '@/components'
import {
  addSignWorkflowTask,
  approveWorkflowTask,
  loadWorkflowBusinessTypes,
  loadWorkflowInstance,
  loadWorkflowList,
  rejectWorkflowTask,
  resubmitWorkflowInstance,
  transferWorkflowTask,
  withdrawWorkflowInstance,
} from '@/services/workflow'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import {
  WORKFLOW_ACTION_LABELS,
  WORKFLOW_TABS,
  workflowApproveModeLabel,
  workflowBusinessTypeLabel,
  workflowDate,
  workflowRows,
  workflowStatusLabel,
} from './model'

type WorkflowRecordSet = WorkflowTask[] | WorkflowRecord[] | WorkflowCc[] | WorkflowMine[]
const workflowInstanceStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'RUNNING', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'WITHDRAWN', label: '已撤回' },
  { value: 'VOIDED', label: '已作废' },
]

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspace = useWorkspaceStore()

const activeTab = computed<WorkflowTab>(() => {
  const returnTab = String(route.query.returnTab ?? '')
  const value =
    route.params.instanceId && ['todo', 'done', 'cc', 'mine'].includes(returnTab)
      ? returnTab
      : route.meta.workflowTab
  return value === 'done' || value === 'cc' || value === 'mine' ? value : 'todo'
})
const instanceId = computed(() => String(route.params.instanceId ?? ''))
const isDetailRoute = computed(() => Boolean(instanceId.value))
const keyword = ref('')
const businessType = ref('')
const instanceStatus = ref('')
const pageNo = ref(1)
const pageSize = 20
const total = ref(0)
const records = ref<WorkflowRecordSet>([])
const visibleBusinessTypes = ref<string[]>([])
const listLoading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const detail = ref<WorkflowInstance | null>(null)
const action = ref<WorkflowUiAction | null>(null)
const actionOpen = ref(false)
const actionLoading = ref(false)
const comment = ref('')
const targetUserId = ref('')
const additionalUserIds = ref('')
const idempotencyKey = ref('')
let listController: AbortController | null = null
let businessTypesController: AbortController | null = null
let detailController: AbortController | null = null

const rows = computed(() => workflowRows(activeTab.value, records.value))
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const workflowBusinessTypeOptions = computed(() => [
  { value: '', label: '全部业务' },
  ...visibleBusinessTypes.value.map((value) => ({
    value,
    label: workflowBusinessTypeLabel(value),
  })),
])
const availableActions = computed(() =>
  (detail.value?.availableActions ?? []).filter((candidate) =>
    canPerformWorkflowAction(candidate, detail.value?.availableActions ?? [], session.permissions),
  ),
)
const pendingTask = computed(() =>
  detail.value?.nodes
    ?.flatMap((node) => node.tasks ?? [])
    .find((task) => task.taskStatus === 'PENDING'),
)

function statusTone(status: string): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'APPROVED' || status === 'COMPLETED' || status === 'APPROVE') return 'success'
  if (status === 'REJECTED' || status === 'VOIDED' || status === 'REJECT') return 'danger'
  if (status === 'RUNNING' || status === 'PENDING') return 'info'
  if (['WITHDRAWN', 'TRANSFERRED', 'WITHDRAW', 'TRANSFER'].includes(status)) return 'warning'
  return 'neutral'
}

function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

function listQuery() {
  const periodBounds = reportPeriodBounds(workspace.selectedReportPeriod)
  return {
    pageNo: pageNo.value,
    pageSize,
    keyword: keyword.value.trim() || undefined,
    businessType: businessType.value.trim() || undefined,
    instanceStatus: instanceStatus.value || undefined,
    startTime: periodBounds ? `${periodBounds.startDate}T00:00:00` : undefined,
    endTime: periodBounds ? `${periodBounds.endDate}T23:59:59` : undefined,
  }
}

async function loadList() {
  listController?.abort()
  listController = new AbortController()
  listLoading.value = true
  errorMessage.value = ''
  try {
    const result = await loadWorkflowList(activeTab.value, listQuery(), listController.signal)
    records.value = result.records as WorkflowRecordSet
    total.value = result.total
  } catch (error) {
    if (listController.signal.aborted) return
    records.value = []
    total.value = 0
    errorMessage.value = errorText(error, '审批列表加载失败')
  } finally {
    if (!listController.signal.aborted) listLoading.value = false
  }
}

async function loadBusinessTypes() {
  businessTypesController?.abort()
  businessTypesController = new AbortController()
  try {
    visibleBusinessTypes.value = await loadWorkflowBusinessTypes(
      activeTab.value,
      businessTypesController.signal,
    )
    if (businessType.value && !visibleBusinessTypes.value.includes(businessType.value)) {
      businessType.value = ''
    }
  } catch {
    if (!businessTypesController.signal.aborted) visibleBusinessTypes.value = []
  }
}

async function loadDetail() {
  if (!instanceId.value) return
  detailController?.abort()
  detailController = new AbortController()
  detailLoading.value = true
  errorMessage.value = ''
  detail.value = null
  try {
    detail.value = await loadWorkflowInstance(instanceId.value, detailController.signal)
  } catch (error) {
    if (detailController.signal.aborted) return
    errorMessage.value = errorText(error, '审批详情不可访问或不存在')
  } finally {
    if (!detailController.signal.aborted) detailLoading.value = false
  }
}

function openDetail(id: string) {
  void router.push({
    path: `/approval/instances/${id}`,
    query: { returnTab: activeTab.value },
  })
}

function closeDetail() {
  const returnTab = String(route.query.returnTab ?? 'todo')
  void router.push(
    `/approval/${['todo', 'done', 'cc', 'mine'].includes(returnTab) ? returnTab : 'todo'}`,
  )
}

function resetFilters() {
  keyword.value = ''
  businessType.value = ''
  instanceStatus.value = ''
  pageNo.value = 1
  void loadList()
}

function search() {
  pageNo.value = 1
  void loadList()
}

function changeInstanceStatus(value: string) {
  instanceStatus.value = value
  search()
}

function changePage(delta: number) {
  pageNo.value += delta
  void loadList()
}

function openAction(nextAction: WorkflowUiAction) {
  action.value = nextAction
  comment.value = ''
  targetUserId.value = ''
  additionalUserIds.value = ''
  idempotencyKey.value = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${instanceId.value}`
  actionOpen.value = true
}

async function submitAction() {
  if (actionLoading.value || !action.value || !detail.value) return
  if (!availableActions.value.includes(action.value)) {
    actionOpen.value = false
    await loadDetail()
    errorMessage.value = '当前账号无权执行该动作，详情已刷新'
    return
  }
  if (action.value === 'reject' && !comment.value.trim()) {
    errorMessage.value = '驳回必须填写原因'
    return
  }
  const taskId = pendingTask.value?.id
  if (['approve', 'reject', 'transfer', 'addSign'].includes(action.value) && !taskId) {
    actionOpen.value = false
    await loadDetail()
    errorMessage.value = '当前没有可处理任务，详情已刷新'
    return
  }
  if (action.value === 'transfer' && !targetUserId.value.trim()) {
    errorMessage.value = '请输入转办目标用户 ID'
    return
  }
  const userIds = additionalUserIds.value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
  if (action.value === 'addSign' && userIds.length === 0) {
    errorMessage.value = '请输入至少一个加签用户 ID'
    return
  }

  actionLoading.value = true
  errorMessage.value = ''
  try {
    if (action.value === 'approve') {
      await approveWorkflowTask(taskId!, {
        action: 'APPROVE',
        comment: comment.value.trim() || undefined,
        idempotencyKey: idempotencyKey.value,
      })
    } else if (action.value === 'reject') {
      await rejectWorkflowTask(taskId!, {
        action: 'REJECT',
        comment: comment.value.trim(),
        idempotencyKey: idempotencyKey.value,
      })
    } else if (action.value === 'withdraw') {
      await withdrawWorkflowInstance(detail.value.id)
    } else if (action.value === 'resubmit') {
      await resubmitWorkflowInstance(detail.value.id)
    } else if (action.value === 'transfer') {
      await transferWorkflowTask(
        taskId!,
        targetUserId.value.trim(),
        comment.value.trim() || undefined,
      )
    } else {
      await addSignWorkflowTask(taskId!, userIds, comment.value.trim() || undefined)
    }
    actionOpen.value = false
    await loadDetail()
  } catch (error) {
    const message = errorText(error, '审批动作执行失败，未修改页面事实')
    await loadDetail()
    errorMessage.value = message
  } finally {
    actionLoading.value = false
  }
}

watch(
  () => [activeTab.value, instanceId.value, workspace.selectedReportPeriod],
  async () => {
    if (isDetailRoute.value) void loadDetail()
    else {
      pageNo.value = 1
      await loadBusinessTypes()
      await loadList()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  businessTypesController?.abort()
  detailController?.abort()
})
</script>

<template>
  <section class="workflow-page" aria-labelledby="workflow-title">
    <h1 id="workflow-title" class="v2-visually-hidden">审批工作台</h1>
    <V2Alert
      v-if="errorMessage"
      tone="danger"
      title="请求未完成"
      dismissible
      @dismiss="errorMessage = ''"
    >
      {{ errorMessage }}
    </V2Alert>

    <template v-if="true">
      <V2Card class="workflow-filter">
        <form class="workflow-filter__form" @submit.prevent="search">
          <V2Input
            v-model="keyword"
            class="workflow-filter__keyword"
            type="search"
            label="关键词"
            placeholder="标题或业务编号"
          />
          <V2Select
            id="workflow-business-type"
            class="workflow-filter__business-type"
            v-model="businessType"
            label="业务类型"
            :options="workflowBusinessTypeOptions"
            allow-empty
          />
          <V2Select
            id="workflow-instance-status"
            class="workflow-filter__status"
            :model-value="instanceStatus"
            label="实例状态"
            :options="workflowInstanceStatusOptions"
            allow-empty
            @update:model-value="changeInstanceStatus"
          />
          <div class="workflow-filter__actions">
            <V2Button class="workflow-filter__search" type="submit" size="small">查询</V2Button>
            <V2Button type="button" size="small" variant="ghost" @click="resetFilters"
              >重置</V2Button
            >
          </div>
        </form>
      </V2Card>

      <V2PageState
        v-if="listLoading"
        kind="loading"
        title="正在加载审批列表"
        description="请稍候。"
        :heading-level="2"
      />
      <V2PageState
        v-else-if="rows.length === 0"
        title="暂无审批记录"
        description="当前筛选范围内没有可显示记录。"
        :heading-level="2"
      />
      <V2Card
        v-else
        :title="`${WORKFLOW_TABS.find((tab) => tab.value === activeTab)?.label}（${total}）`"
      >
        <div class="workflow-table-wrap">
          <table class="workflow-table">
            <thead>
              <tr>
                <th>审批事项</th>
                <th>业务编号</th>
                <th>业务类型</th>
                <th>状态</th>
                <th>处理人/节点</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.key">
                <td>
                  <button
                    type="button"
                    class="workflow-table__title"
                    @click="openDetail(row.instanceId)"
                  >
                    <strong>{{ row.title }}</strong>
                  </button>
                  <small>{{ row.note }}</small>
                </td>
                <td>{{ row.businessId }}</td>
                <td>{{ workflowBusinessTypeLabel(row.businessType) }}</td>
                <td>
                  <V2Badge :tone="statusTone(row.status)" dot>{{
                    workflowStatusLabel(row.status)
                  }}</V2Badge>
                </td>
                <td>{{ row.actor }}</td>
                <td>{{ workflowDate(row.time) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <div class="workflow-pagination">
            <div>
              <V2Button size="small" variant="ghost" :disabled="pageNo <= 1" @click="changePage(-1)"
                >上一页</V2Button
              >
              <span>第 {{ pageNo }} 页</span>
              <V2Button
                size="small"
                variant="ghost"
                :disabled="pageNo >= pageCount"
                @click="changePage(1)"
                >下一页</V2Button
              >
            </div>
          </div>
        </template>
      </V2Card>
    </template>

    <V2Dialog
      :open="isDetailRoute"
      title="审批详情"
      :description="
        detail
          ? `${workflowBusinessTypeLabel(detail.businessType)} · ${detail.templateName}`
          : '查看流程记录并执行当前允许动作。'
      "
      close-label="关闭审批详情"
      panel-class="v2-dialog-standard v2-detail-dialog"
      :close-on-backdrop="true"
      @close="closeDetail"
    >
      <V2PageState
        v-if="detailLoading"
        kind="loading"
        title="正在加载审批详情"
        description="正在校验当前账号可见范围。"
        :heading-level="3"
      />
      <V2PageState
        v-else-if="!detail"
        kind="empty"
        title="无法显示审批详情"
        description="实例不存在或当前账号无权访问。"
        :heading-level="3"
      />
      <template v-else>
        <div class="v2-detail-dialog__section">
          <V2Badge :tone="statusTone(detail.instanceStatus)" dot>{{
            workflowStatusLabel(detail.instanceStatus)
          }}</V2Badge>
          <p class="v2-detail-dialog__message">{{ detail.businessSummary ?? '-' }}</p>
          <dl class="v2-detail-dialog__facts">
            <div>
              <dt>审批事项</dt>
              <dd>{{ detail.title }}</dd>
            </div>
            <div>
              <dt>发起人</dt>
              <dd>{{ detail.initiatorName }}</dd>
            </div>
            <div>
              <dt>发起时间</dt>
              <dd>{{ workflowDate(detail.startedAt) }}</dd>
            </div>
            <div>
              <dt>业务编号</dt>
              <dd>{{ detail.businessId }}</dd>
            </div>
            <div>
              <dt>金额</dt>
              <dd>{{ detail.amount ?? '-' }}</dd>
            </div>
          </dl>
        </div>
        <div v-if="availableActions.length" class="v2-detail-dialog__actions" aria-label="审批动作">
          <p class="v2-detail-dialog__message">可用操作</p>
          <div class="v2-detail-dialog__quick-actions">
            <V2GlassButton
              v-for="candidate in availableActions"
              :key="candidate"
              :text="WORKFLOW_ACTION_LABELS[candidate]"
              :disabled="actionLoading"
              @click="openAction(candidate)"
            />
          </div>
        </div>

        <div class="workflow-detail-grid">
          <V2Card title="审批节点" :heading-level="3">
            <ol class="workflow-timeline">
              <li v-for="node in detail.nodes" :key="node.id">
                <V2Badge :tone="statusTone(node.nodeStatus)">{{
                  workflowStatusLabel(node.nodeStatus)
                }}</V2Badge>
                <div>
                  <strong>{{ node.nodeName }}</strong
                  ><small
                    >第 {{ node.roundNo }} 轮 ·
                    {{ workflowApproveModeLabel(node.approveMode) }}</small
                  >
                </div>
              </li>
            </ol>
          </V2Card>
          <V2Card title="操作记录" :heading-level="3">
            <ol class="workflow-timeline">
              <li v-if="detail.records.length === 0" class="workflow-timeline__empty">
                暂无操作记录
              </li>
              <li v-for="record in detail.records" :key="record.id">
                <V2Badge tone="neutral">{{ record.actionName }}</V2Badge>
                <div>
                  <strong>{{ record.operatorName }}</strong
                  ><small
                    >{{ workflowDate(record.createdAt) }} · {{ record.comment ?? '无备注' }}</small
                  >
                </div>
              </li>
            </ol>
          </V2Card>
        </div>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="actionOpen"
      :title="action ? WORKFLOW_ACTION_LABELS[action] : '审批动作'"
      description="提交后将刷新最新状态。"
      :close-on-backdrop="false"
      panel-class="v2-dialog-standard"
    >
      <div class="workflow-action-form">
        <V2Input v-if="action === 'transfer'" v-model="targetUserId" label="目标用户 ID" />
        <V2Input
          v-if="action === 'addSign'"
          v-model="additionalUserIds"
          label="加签用户 ID"
          placeholder="多个 ID 用逗号分隔"
        />
        <label
          >处理意见<textarea v-model="comment" rows="4" :required="action === 'reject'"></textarea>
        </label>
      </div>
      <template #footer>
        <V2Button variant="ghost" :disabled="actionLoading" @click="actionOpen = false"
          >取消</V2Button
        >
        <V2Button
          :variant="action === 'reject' ? 'danger' : 'primary'"
          :loading="actionLoading"
          @click="submitAction"
          >确认提交</V2Button
        >
      </template>
    </V2Dialog>
  </section>
</template>

<style scoped>
.workflow-page {
  box-sizing: border-box;
  width: 100%;
  min-height: 100%;
  display: grid;
  align-content: start;
  flex: 1;
  gap: 10px;
  padding: 10px;
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-13);
  line-height: var(--v2-line-height-body);
}
.workflow-filter__form {
  display: grid;
  grid-template-columns: minmax(12rem, 2fr) repeat(2, minmax(10rem, 1fr)) auto;
  gap: var(--v2-space-3);
  align-items: end;
}
.workflow-filter label,
.workflow-action-form label {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}
.workflow-filter input,
.workflow-filter select,
.workflow-action-form input,
.workflow-action-form textarea {
  min-height: var(--v2-control-height-md);
  box-sizing: border-box;
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  padding: 0.5rem 0.75rem;
  color: var(--v2-color-text);
  background: var(--v2-color-surface);
  font: inherit;
}
.workflow-filter select,
.workflow-filter select option {
  direction: ltr;
  text-align: left;
}
.workflow-filter select {
  text-align-last: left;
}
.workflow-filter__actions,
.workflow-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
}
.workflow-table-wrap {
  overflow-x: auto;
}
.workflow-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 50rem;
}
.workflow-table th,
.workflow-table td {
  padding: 0.75rem;
  border-bottom: 1px solid var(--v2-color-border-subtle);
  text-align: left;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-13);
}
.workflow-table th {
  background: var(--v2-color-surface-subtle);
}
.workflow-table strong,
.workflow-table small {
  display: block;
}
.workflow-table strong {
  color: var(--v2-color-text);
}
.workflow-table__title {
  appearance: none;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.workflow-table__title:hover strong,
.workflow-table__title:focus-visible strong {
  color: var(--v2-color-primary);
  text-decoration: underline;
}
.workflow-table small {
  margin-top: var(--v2-space-1);
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.workflow-pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--v2-space-2);
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}
.workflow-pagination div {
  display: flex;
  gap: var(--v2-space-2);
}
.workflow-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--v2-space-4);
}
.workflow-timeline {
  display: grid;
  gap: var(--v2-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}
.workflow-timeline li {
  display: flex;
  align-items: flex-start;
  gap: var(--v2-space-3);
  padding-bottom: var(--v2-space-3);
  border-bottom: 1px solid var(--v2-color-border-subtle);
}
.workflow-timeline strong,
.workflow-timeline small {
  display: block;
}
.workflow-timeline strong {
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-13);
}
.workflow-timeline__empty {
  justify-content: center;
  min-height: 4rem;
  color: var(--v2-color-text-muted);
  border-bottom: 0 !important;
}
.workflow-timeline small {
  margin-top: var(--v2-space-1);
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.workflow-action-form {
  display: grid;
  gap: var(--v2-space-3);
}
@media (max-width: 64rem) {
  .workflow-filter__form {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 40rem) {
  .workflow-detail-grid {
    grid-template-columns: 1fr;
  }
  .workflow-filter__form {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: var(--v2-space-2);
  }
  .workflow-filter__form > .workflow-filter__keyword,
  .workflow-filter__form > .workflow-filter__business-type,
  .workflow-filter__actions > .workflow-filter__search {
    display: none;
  }
  .workflow-filter__status {
    min-width: 0;
  }
  .workflow-filter__actions {
    grid-column: auto;
    flex-wrap: nowrap;
  }
}
</style>
