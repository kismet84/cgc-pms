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
  V2Input,
  V2PageState,
  V2Select,
  showToast,
} from '@/components'
import {
  addSignWorkflowTask,
  approveWorkflowTask,
  loadWorkflowActionUsers,
  loadWorkflowBusinessTypes,
  loadWorkflowInstance,
  loadWorkflowList,
  rejectWorkflowTask,
  resubmitWorkflowInstance,
  transferWorkflowTask,
  withdrawWorkflowInstance,
} from '@/services/workflow'
import { isApiClientError } from '@/services/request'
import { loadEnabledDictDataByCode, type DictDataRecord } from '@/services/system-management'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import {
  WORKFLOW_ACTION_LABELS,
  workflowApproveModeLabel,
  workflowBusinessTypeLabel,
  workflowDate,
  workflowRows,
  workflowStatusLabel,
} from './model'

type WorkflowRecordSet = WorkflowTask[] | WorkflowRecord[] | WorkflowCc[] | WorkflowMine[]

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
const pageSize = 10
const total = ref(0)
const records = ref<WorkflowRecordSet>([])
const visibleBusinessTypes = ref<string[]>([])
const workflowInstanceStatuses = ref<DictDataRecord[]>([])
const listLoading = ref(false)
const hasLoadedList = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const detail = ref<WorkflowInstance | null>(null)
const action = ref<WorkflowUiAction | null>(null)
const actionOpen = ref(false)
const actionLoading = ref(false)
const actionErrorMessage = ref('')
const commentError = ref('')
const targetUserError = ref('')
const additionalUsersError = ref('')
const comment = ref('')
const targetUserId = ref('')
const additionalUserId = ref('')
const actionUserOptions = ref<Array<{ value: string; label: string }>>([])
const actionUsersLoading = ref(false)
const idempotencyKey = ref('')
let listController: AbortController | null = null
let businessTypesController: AbortController | null = null
let detailController: AbortController | null = null
let actionUsersController: AbortController | null = null
let workflowInstanceStatusesLoaded = false

const rows = computed(() => workflowRows(activeTab.value, records.value))
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const workflowBusinessTypeOptions = computed(() => [
  { value: '', label: '全部业务' },
  ...visibleBusinessTypes.value.map((value) => ({
    value,
    label: workflowBusinessTypeLabel(value),
  })),
])
const workflowInstanceStatusOptions = computed(() =>
  workflowInstanceStatuses.value.map((item) => ({
    value: item.dictValue,
    label: item.dictLabel,
  })),
)
const availableActions = computed(() =>
  (detail.value?.availableActions ?? []).filter((candidate) =>
    canPerformWorkflowAction(candidate, detail.value?.availableActions ?? [], session.permissions),
  ),
)
const pendingTask = computed(() =>
  detail.value?.nodes
    ?.flatMap((node) => node.tasks ?? [])
    .find((task) => task.taskStatus === 'PENDING' && task.approverId === session.userInfo?.userId),
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

async function loadWorkflowInstanceStatuses(signal?: AbortSignal): Promise<void> {
  if (workflowInstanceStatusesLoaded) return
  try {
    workflowInstanceStatuses.value = await loadEnabledDictDataByCode('wf_instance_status', signal)
    workflowInstanceStatusesLoaded = true
  } catch (error) {
    if (signal?.aborted) return
    workflowInstanceStatuses.value = []
    showToast('error', '实例状态字典加载失败', errorText(error, '请稍后重试。'))
  }
}

function listQuery() {
  const periodBounds = reportPeriodBounds(workspace.selectedReportPeriod)
  return {
    pageNo: pageNo.value,
    pageSize,
    keyword: keyword.value.trim() || undefined,
    businessType: businessType.value.trim() || undefined,
    instanceStatus: instanceStatus.value || undefined,
    startTime: periodBounds ? `${periodBounds.startDate} 00:00:00` : undefined,
    endTime: periodBounds ? `${periodBounds.endDate} 23:59:59` : undefined,
  }
}

async function loadList() {
  listController?.abort()
  listController = new AbortController()
  listLoading.value = true
  errorMessage.value = ''
  try {
    const [, result] = await Promise.all([
      loadWorkflowInstanceStatuses(listController.signal),
      loadWorkflowList(activeTab.value, listQuery(), listController.signal),
    ])
    records.value = result.records as WorkflowRecordSet
    total.value = result.total
  } catch (error) {
    if (listController.signal.aborted) return
    records.value = []
    total.value = 0
    errorMessage.value = errorText(error, '审批列表加载失败')
    showToast('error', '审批列表读取失败', errorMessage.value)
  } finally {
    if (!listController.signal.aborted) {
      listLoading.value = false
      hasLoadedList.value = true
    }
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
    await loadWorkflowInstanceStatuses(detailController.signal)
    detail.value = await loadWorkflowInstance(instanceId.value, detailController.signal)
  } catch (error) {
    if (detailController.signal.aborted) return
    errorMessage.value = errorText(error, '审批详情不可访问或不存在')
    showToast('error', '审批详情读取失败', errorMessage.value)
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

function changeBusinessType(value: string) {
  businessType.value = value
  search()
}

function changePage(delta: number) {
  pageNo.value += delta
  void loadList()
}

function openAction(nextAction: WorkflowUiAction) {
  action.value = nextAction
  actionErrorMessage.value = ''
  commentError.value = ''
  targetUserError.value = ''
  additionalUsersError.value = ''
  comment.value = ''
  targetUserId.value = ''
  additionalUserId.value = ''
  idempotencyKey.value = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${instanceId.value}`
  actionOpen.value = true
  if (nextAction === 'transfer' || nextAction === 'addSign') void loadActionUsers()
}

async function loadActionUsers() {
  const taskId = pendingTask.value?.id
  if (!taskId) {
    actionUserOptions.value = []
    actionErrorMessage.value = '当前没有可处理任务'
    return
  }
  actionUsersController?.abort()
  const controller = new AbortController()
  actionUsersController = controller
  actionUsersLoading.value = true
  try {
    const users = await loadWorkflowActionUsers(taskId, controller.signal)
    if (actionUsersController !== controller) return
    actionUserOptions.value = users
      .filter((item) => ['ACTIVE', 'ENABLE'].includes(item.status))
      .map((item) => ({
        value: item.id,
        label: item.realName ? `${item.realName}（${item.username}）` : item.username,
      }))
    if (!actionUserOptions.value.length) actionErrorMessage.value = '暂无可选用户'
  } catch (error) {
    if (!controller.signal.aborted) {
      actionUserOptions.value = []
      actionErrorMessage.value = errorText(error, '用户候选读取失败')
    }
  } finally {
    if (actionUsersController === controller) {
      actionUsersLoading.value = false
      actionUsersController = null
    }
  }
}

async function submitAction() {
  if (actionLoading.value || !action.value || !detail.value) return
  if (!availableActions.value.includes(action.value)) {
    actionOpen.value = false
    await loadDetail()
    errorMessage.value = '当前账号无权执行该动作，详情已刷新'
    showToast('error', '审批动作未执行', errorMessage.value)
    return
  }
  if (action.value === 'reject' && !comment.value.trim()) {
    commentError.value = '驳回必须填写原因'
    return
  }
  const taskId = pendingTask.value?.id
  if (['approve', 'reject', 'transfer', 'addSign'].includes(action.value) && !taskId) {
    actionOpen.value = false
    await loadDetail()
    errorMessage.value = '当前没有可处理任务，详情已刷新'
    showToast('error', '审批动作未执行', errorMessage.value)
    return
  }
  if (action.value === 'transfer' && !targetUserId.value.trim()) {
    targetUserError.value = '请选择转办用户'
    return
  }
  const userIds = additionalUserId.value ? [additionalUserId.value] : []
  if (action.value === 'addSign' && userIds.length === 0) {
    additionalUsersError.value = '请选择加签用户'
    return
  }

  actionLoading.value = true
  actionErrorMessage.value = ''
  commentError.value = ''
  targetUserError.value = ''
  additionalUsersError.value = ''
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
    actionErrorMessage.value = message
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
  actionUsersController?.abort()
})
</script>

<template>
  <section class="workflow-page" aria-labelledby="workflow-title">
    <V2Card class="workflow-filter" title="审批工作台" title-id="workflow-title" :heading-level="1">
      <template #actions>
        <form class="workflow-filter__form" @submit.prevent="search">
          <V2Input
            v-model="keyword"
            class="workflow-filter__keyword"
            type="search"
            label="关键词"
            hide-label
            placeholder="搜索标题或业务编号"
          />
          <V2Select
            id="workflow-business-type"
            class="workflow-filter__business-type"
            v-model="businessType"
            label="业务类型"
            hide-label
            :options="workflowBusinessTypeOptions"
            allow-empty
            placeholder="全部业务类型"
            @update:model-value="changeBusinessType"
          />
          <V2Select
            id="workflow-instance-status"
            class="workflow-filter__status"
            :model-value="instanceStatus"
            label="实例状态"
            hide-label
            :options="workflowInstanceStatusOptions"
            allow-empty
            placeholder="全部状态"
            @update:model-value="changeInstanceStatus"
          />
          <div class="workflow-filter__actions">
            <V2Button class="workflow-filter__search" type="submit" size="small">查询</V2Button>
            <V2Button type="button" size="small" variant="ghost" @click="resetFilters"
              >重置</V2Button
            >
          </div>
        </form>
      </template>
    </V2Card>
    <V2PageState
      v-if="listLoading && !hasLoadedList"
      kind="loading"
      title="正在加载审批列表"
      description="请稍候。"
      :heading-level="2"
    />
    <V2PageState
      v-else-if="!errorMessage && rows.length === 0"
      title="暂无审批记录"
      description="当前筛选范围内没有可显示记录。"
      :heading-level="2"
    />
    <V2Card v-else :aria-busy="listLoading">
      <div class="workflow-table-wrap" role="region" aria-label="审批任务表格" tabindex="0">
        <table class="workflow-table">
          <caption class="v2-visually-hidden">
            当前审批任务列表
          </caption>
          <thead>
            <tr>
              <th scope="col">业务编号</th>
              <th scope="col">审批事项</th>
              <th scope="col">业务类型</th>
              <th scope="col">状态</th>
              <th scope="col">处理人/节点</th>
              <th scope="col">时间</th>
              <th scope="col">说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.key">
              <td>
                <V2Button
                  class="v2-table__record-link"
                  variant="ghost"
                  size="small"
                  @click="openDetail(row.instanceId)"
                >
                  {{ row.businessCode }}
                </V2Button>
              </td>
              <td>
                <strong>{{ row.title }}</strong>
              </td>
              <td>{{ workflowBusinessTypeLabel(row.businessType) }}</td>
              <td>
                <V2Badge :tone="statusTone(row.status)" dot>{{
                  workflowStatusLabel(row.status)
                }}</V2Badge>
              </td>
              <td>{{ row.actor }}</td>
              <td>{{ workflowDate(row.time) }}</td>
              <td>{{ row.note }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <nav class="workflow-pagination" aria-label="审批任务分页">
          <span>共 {{ total }} 条</span>
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
        </nav>
      </template>
    </V2Card>

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
        v-else-if="!errorMessage && !detail"
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
              <dd>{{ detail.businessCode ?? '-' }}</dd>
            </div>
            <div>
              <dt>金额</dt>
              <dd>{{ detail.amount ?? '-' }}</dd>
            </div>
          </dl>
        </div>
        <div class="workflow-detail-grid">
          <section class="v2-detail-dialog__section">
            <h3>审批节点</h3>
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
          </section>
          <section class="v2-detail-dialog__section">
            <h3>操作记录</h3>
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
          </section>
        </div>
      </template>
      <template #footer>
        <V2Button
          v-for="candidate in availableActions"
          :key="candidate"
          type="button"
          :variant="
            candidate === 'reject' || candidate === 'withdraw'
              ? 'danger'
              : candidate === 'approve' || candidate === 'resubmit'
                ? 'primary'
                : 'secondary'
          "
          :disabled="actionLoading"
          @click="openAction(candidate)"
        >
          {{ WORKFLOW_ACTION_LABELS[candidate] }}
        </V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="actionOpen"
      :title="action ? WORKFLOW_ACTION_LABELS[action] : '审批动作'"
      description="提交后将刷新最新状态。"
      :close-on-backdrop="false"
      :close-disabled="actionLoading"
      panel-class="v2-dialog-standard"
    >
      <div class="workflow-action-form">
        <V2Alert v-if="actionErrorMessage" tone="danger" title="审批动作未完成">
          {{ actionErrorMessage }}
        </V2Alert>
        <V2Select
          v-if="action === 'transfer'"
          v-model="targetUserId"
          label="转办用户"
          :options="actionUserOptions"
          :disabled="actionUsersLoading || !actionUserOptions.length"
          :error="targetUserError"
        />
        <V2Select
          v-if="action === 'addSign'"
          v-model="additionalUserId"
          label="加签用户"
          :options="actionUserOptions"
          :disabled="actionUsersLoading || !actionUserOptions.length"
          :error="additionalUsersError"
        />
        <label>
          处理意见
          <textarea
            v-model="comment"
            rows="4"
            :required="action === 'reject'"
            :aria-invalid="commentError ? 'true' : undefined"
            :aria-describedby="commentError ? 'workflow-comment-error' : undefined"
          ></textarea>
          <small
            v-if="commentError"
            id="workflow-comment-error"
            class="workflow-action-form__error"
          >
            {{ commentError }}
          </small>
        </label>
      </div>
      <template #footer>
        <V2Button
          type="button"
          variant="secondary"
          :disabled="actionLoading"
          @click="actionOpen = false"
        >
          取消
        </V2Button>
        <V2Button type="button" :loading="actionLoading" @click="submitAction">确认提交</V2Button>
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
  gap: var(--v2-space-3);
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
.workflow-action-form label {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}
.workflow-filter__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
}
.workflow-table-wrap {
  overflow-x: auto;
}
.workflow-table {
  min-width: 50rem;
}
.workflow-table strong {
  display: block;
}
.workflow-table strong {
  color: var(--v2-color-text);
}
.workflow-pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--v2-space-2);
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}
.workflow-action-form__error {
  color: var(--v2-color-danger);
  font-size: var(--v2-font-size-12);
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
  border-bottom: var(--v2-border-width) solid var(--v2-color-border-subtle);
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
  min-height: calc(var(--v2-space-8) + var(--v2-space-8));
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
    grid-template-columns: minmax(0, 1fr);
    gap: var(--v2-space-2);
  }
  .workflow-filter__actions {
    grid-column: 1;
    flex-wrap: nowrap;
  }
  .workflow-filter__actions > button {
    flex: 1 1 0;
  }
}
</style>
