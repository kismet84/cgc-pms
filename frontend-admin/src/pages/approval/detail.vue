<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getInstanceDetail,
  approveTask,
  rejectTask,
  withdrawInstance,
  resubmitInstance,
  transferTask,
  addSignTask,
  type WfInstanceVO,
} from '@/api/modules/workflow'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import {
  canAccessWorkflowBusinessEntry,
  getWorkflowBusinessEntryPath,
  getWorkflowBusinessTypeLabel,
  getWorkflowInstanceStatusMeta,
  WF_INSTANCE_RUNNING,
  WF_INSTANCE_APPROVED,
  WF_INSTANCE_REJECTED,
  WF_INSTANCE_WITHDRAWN,
  WF_INSTANCE_VOIDED,
} from './workflowDisplay'

// 字典常量 - 工作流节点状态
const WF_NODE_WAITING = 'WAITING'
const WF_NODE_ACTIVE = 'ACTIVE'
const WF_NODE_COMPLETED = 'COMPLETED'
const WF_NODE_REJECTED = 'REJECTED'
const WF_NODE_SKIPPED = 'SKIPPED'

// 字典常量 - 工作流任务状态
const WF_TASK_PENDING = 'PENDING'
const WF_TASK_APPROVED = 'APPROVED'
const WF_TASK_REJECTED = 'REJECTED'
const WF_TASK_CANCELLED = 'CANCELLED'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const instanceId = route.params.instanceId as string

const loading = ref(false)
const detail = ref<WfInstanceVO | null>(null)
const actionLoading = ref(false)
const approvalComment = ref('')
const showApproveModal = ref(false)
const showRejectModal = ref(false)
const pendingAction = ref<'APPROVE' | 'REJECT'>('APPROVE')

const showTransferModal = ref(false)
const transferTargetUserId = ref('')
const transferComment = ref('')

const showAddSignModal = ref(false)
const addSignUserIds = ref<string[]>([])
const addSignComment = ref('')

const actionNameMap: Record<string, string> = {
  SUBMIT: '提交审批',
  APPROVE: '同意',
  REJECT: '驳回',
  WITHDRAW: '撤回',
  RESUBMIT: '重新提交',
  TRANSFER: '转办',
  ADD_SIGN: '加签',
}

const nodeStatusMap: Record<string, { text: string; color: string }> = {
  WAITING: { text: '等待', color: 'default' },
  ACTIVE: { text: '审批中', color: 'processing' },
  COMPLETED: { text: '已完成', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  SKIPPED: { text: '已跳过', color: 'default' },
}

const taskStatusMap: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待处理', color: 'processing' },
  APPROVED: { text: '已同意', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已取消', color: 'default' },
  TRANSFERRED: { text: '已转办', color: 'warning' },
}

const isRunning = computed(() => detail.value?.instanceStatus === 'RUNNING')
const availableActions = computed(() =>
  Array.isArray(detail.value?.availableActions) ? detail.value.availableActions : [],
)
const nodes = computed(() => (Array.isArray(detail.value?.nodes) ? detail.value.nodes : []))
const records = computed(() => (Array.isArray(detail.value?.records) ? detail.value.records : []))
const completedNodeCount = computed(
  () => nodes.value.filter((node) => node.nodeStatus === WF_NODE_COMPLETED).length,
)

function displayText(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

function getInstanceStatusMeta(status: unknown) {
  return getWorkflowInstanceStatusMeta(status)
}

function getNodeStatusMeta(status: unknown) {
  const key = String(status ?? '')
  return nodeStatusMap[key] ?? { text: '未知节点状态', color: 'default' }
}

function getTaskStatusMeta(status: unknown) {
  const key = String(status ?? '')
  return taskStatusMap[key] ?? { text: '未知任务状态', color: 'default' }
}

function getRecordActionName(record: { actionType?: string; actionName?: string }) {
  const key = String(record.actionType ?? '')
  return actionNameMap[key] ?? displayText(record.actionName)
}

function businessEntryPath(record: WfInstanceVO | null) {
  return getWorkflowBusinessEntryPath(record)
}

function canOpenBusinessEntry(record: WfInstanceVO | null) {
  return canAccessWorkflowBusinessEntry(record, userStore.hasPermission, userStore.roles)
}

function openBusinessEntry(record: WfInstanceVO) {
  if (!canOpenBusinessEntry(record)) return
  const path = businessEntryPath(record)
  if (path) router.push(path)
}

async function fetchDetail() {
  loading.value = true
  try {
    detail.value = await getInstanceDetail(instanceId)
  } catch (e: unknown) {
    console.error(e)
    const msg = e instanceof Error ? e.message : '加载审批详情失败'
    message.error(msg)
  } finally {
    loading.value = false
  }
}

function handleAction(action: string) {
  if (action === 'approve') {
    pendingAction.value = 'APPROVE'
    approvalComment.value = ''
    showApproveModal.value = true
  } else if (action === 'reject') {
    pendingAction.value = 'REJECT'
    approvalComment.value = ''
    showRejectModal.value = true
  } else if (action === 'withdraw') {
    handleWithdraw()
  } else if (action === 'resubmit') {
    handleResubmit()
  } else if (action === 'transfer') {
    transferTargetUserId.value = ''
    transferComment.value = ''
    showTransferModal.value = true
  } else if (action === 'addSign') {
    addSignUserIds.value = []
    addSignComment.value = ''
    showAddSignModal.value = true
  }
}

async function handleApprove() {
  actionLoading.value = true
  try {
    const activeNode = nodes.value.find((n) => n.nodeStatus === WF_NODE_ACTIVE)
    const tasks = Array.isArray(activeNode?.tasks) ? activeNode.tasks : []
    const myTask = tasks.find((t) => t.taskStatus === WF_TASK_PENDING)
    if (!myTask) {
      message.error('未找到待处理任务')
      return
    }
    await approveTask(myTask.id, {
      action: 'APPROVE',
      comment: approvalComment.value,
      idempotencyKey: `${myTask.id}-${Date.now()}`,
    })
    message.success('审批通过')
    showApproveModal.value = false
    fetchDetail()
  } catch (e: unknown) {
    console.error(e)
    const msg = e instanceof Error ? e.message : '审批操作失败'
    message.error(msg)
  } finally {
    actionLoading.value = false
  }
}

async function handleReject() {
  if (!approvalComment.value.trim()) {
    message.warning('请输入驳回原因')
    return
  }
  actionLoading.value = true
  try {
    const activeNode = nodes.value.find((n) => n.nodeStatus === WF_NODE_ACTIVE)
    const tasks = Array.isArray(activeNode?.tasks) ? activeNode.tasks : []
    const myTask = tasks.find((t) => t.taskStatus === WF_TASK_PENDING)
    if (!myTask) {
      message.error('未找到待处理任务')
      return
    }
    await rejectTask(myTask.id, {
      action: 'REJECT',
      comment: approvalComment.value,
      idempotencyKey: `${myTask.id}-${Date.now()}`,
    })
    message.success('已驳回')
    showRejectModal.value = false
    fetchDetail()
  } catch (e: unknown) {
    console.error(e)
    const msg = e instanceof Error ? e.message : '审批操作失败'
    message.error(msg)
  } finally {
    actionLoading.value = false
  }
}

async function handleTransfer() {
  if (!transferTargetUserId.value.trim()) {
    message.warning('请输入转办目标用户ID')
    return
  }
  actionLoading.value = true
  try {
    const activeNode = nodes.value.find((n) => n.nodeStatus === WF_NODE_ACTIVE)
    const tasks = Array.isArray(activeNode?.tasks) ? activeNode.tasks : []
    const myTask = tasks.find((t) => t.taskStatus === WF_TASK_PENDING)
    if (!myTask) {
      message.error('未找到待处理任务')
      return
    }
    await transferTask(
      myTask.id,
      transferTargetUserId.value.trim(),
      transferComment.value || undefined,
    )
    message.success('已转办')
    showTransferModal.value = false
    fetchDetail()
  } catch (e: unknown) {
    console.error(e)
    const msg = e instanceof Error ? e.message : '审批操作失败'
    message.error(msg)
  } finally {
    actionLoading.value = false
  }
}

async function handleAddSign() {
  if (addSignUserIds.value.length === 0) {
    message.warning('请选择至少一个加签审批人')
    return
  }
  actionLoading.value = true
  try {
    const activeNode = nodes.value.find((n) => n.nodeStatus === WF_NODE_ACTIVE)
    const tasks = Array.isArray(activeNode?.tasks) ? activeNode.tasks : []
    const myTask = tasks.find((t) => t.taskStatus === WF_TASK_PENDING)
    if (!myTask) {
      message.error('未找到待处理任务')
      return
    }
    await addSignTask(myTask.id, addSignUserIds.value, addSignComment.value || undefined)
    message.success('已加签')
    showAddSignModal.value = false
    fetchDetail()
  } catch (e: unknown) {
    console.error(e)
    const msg = e instanceof Error ? e.message : '审批操作失败'
    message.error(msg)
  } finally {
    actionLoading.value = false
  }
}

async function handleWithdraw() {
  Modal.confirm({
    title: '确认撤回',
    content: '撤回后审批将回到草稿状态，是否继续？',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      actionLoading.value = true
      try {
        await withdrawInstance(instanceId)
        message.success('已撤回')
        fetchDetail()
      } catch (e: unknown) {
        console.error(e)
        const msg = e instanceof Error ? e.message : '撤回失败'
        message.error(msg)
      } finally {
        actionLoading.value = false
      }
    },
  })
}

async function handleResubmit() {
  Modal.confirm({
    title: '确认重新提交',
    content: '确定重新提交该审批吗？',
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      actionLoading.value = true
      try {
        await resubmitInstance(instanceId)
        message.success('已重新提交')
        fetchDetail()
      } catch (e: unknown) {
        console.error(e)
        const msg = e instanceof Error ? e.message : '重新提交失败'
        message.error(msg)
      } finally {
        actionLoading.value = false
      }
    },
  })
}

onMounted(() => {
  fetchDetail()
})
</script>

<template>
  <div class="project-target-redesign app-page">
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb"
        ><a-breadcrumb-item>审批中心</a-breadcrumb-item
        ><a-breadcrumb-item>审批详情</a-breadcrumb-item></a-breadcrumb
      >
      <div class="pt-head-actions">
        <a-tag v-if="detail" :color="getInstanceStatusMeta(detail.instanceStatus).color">
          {{ getInstanceStatusMeta(detail.instanceStatus).text }}
        </a-tag>
      </div>
    </div>

    <a-spin :spinning="loading">
      <div v-if="detail" class="wf-detail-content">
        <!-- Info Card -->
        <div class="pt-panel">
          <a-descriptions title="基本信息" :column="2" size="small" bordered>
            <a-descriptions-item label="审批标题">{{ detail.title }}</a-descriptions-item>
            <a-descriptions-item label="业务类型">
              {{ getWorkflowBusinessTypeLabel(detail.businessType) }}
            </a-descriptions-item>
            <a-descriptions-item label="模板名称">{{ detail.templateName }}</a-descriptions-item>
            <a-descriptions-item label="发起人">{{ detail.initiatorName }}</a-descriptions-item>
            <a-descriptions-item label="发起时间">{{ detail.startedAt }}</a-descriptions-item>
            <a-descriptions-item v-if="detail.amount" label="金额">
              {{ Number(detail.amount).toLocaleString() }} 元
            </a-descriptions-item>
            <a-descriptions-item label="当前轮次"
              >第 {{ detail.currentRound }} 轮</a-descriptions-item
            >
            <a-descriptions-item v-if="detail.endedAt" label="结束时间">{{
              detail.endedAt
            }}</a-descriptions-item>
          </a-descriptions>
          <div v-if="businessEntryPath(detail)" class="wf-actions">
            <a-tooltip :title="canOpenBusinessEntry(detail) ? '' : '无权访问该业务单据'">
              <span>
                <a-button
                  type="link"
                  size="small"
                  :disabled="!canOpenBusinessEntry(detail)"
                  @click="openBusinessEntry(detail)"
                >
                  查看业务单据
                </a-button>
              </span>
            </a-tooltip>
          </div>
        </div>

        <!-- Action Bar -->
        <div v-if="availableActions.length > 0 && isRunning" class="wf-actions">
          <a-space>
            <a-button
              v-if="availableActions.includes('approve')"
              type="primary"
              @click="handleAction('approve')"
            >
              同意
            </a-button>
            <a-button
              v-if="availableActions.includes('reject')"
              danger
              @click="handleAction('reject')"
            >
              驳回
            </a-button>
            <a-button
              v-if="availableActions.includes('transfer')"
              @click="handleAction('transfer')"
            >
              转办
            </a-button>
            <a-button v-if="availableActions.includes('addSign')" @click="handleAction('addSign')">
              加签
            </a-button>
            <a-button
              v-if="availableActions.includes('withdraw')"
              @click="handleAction('withdraw')"
            >
              撤回
            </a-button>
          </a-space>
        </div>
        <div v-if="availableActions.includes('resubmit') && !isRunning" class="wf-actions">
          <a-button type="primary" @click="handleAction('resubmit')">重新提交</a-button>
        </div>

        <!-- Node Flow -->
        <div class="pt-panel">
          <h4 class="wf-section-title">审批流程</h4>
          <a-steps :current="completedNodeCount" size="small" direction="vertical">
            <a-step v-for="node in nodes" :key="node.id">
              <template #title>
                {{ node.nodeName }}
                <a-tag :color="getNodeStatusMeta(node.nodeStatus).color" class="wf-inline-tag">
                  {{ getNodeStatusMeta(node.nodeStatus).text }}
                </a-tag>
              </template>
              <template #description>
                <div
                  v-if="Array.isArray(node.tasks) && node.tasks.length > 0"
                  class="wf-node-tasks"
                >
                  <div v-for="task in node.tasks" :key="task.id" class="wf-node-task-item">
                    <span>{{ task.approverName }}</span>
                    <a-tag :color="getTaskStatusMeta(task.taskStatus).color" size="small">
                      {{ getTaskStatusMeta(task.taskStatus).text }}
                    </a-tag>
                    <span v-if="task.comment" class="wf-task-comment">{{ task.comment }}</span>
                  </div>
                </div>
              </template>
            </a-step>
          </a-steps>
        </div>

        <!-- Approval Records Timeline -->
        <div class="pt-panel">
          <h4 class="wf-section-title">审批记录</h4>
          <a-timeline>
            <a-timeline-item v-for="record in records" :key="record.id">
              <div>
                <strong>{{ record.operatorName }}</strong>
                <a-tag class="wf-inline-tag">{{ getRecordActionName(record) }}</a-tag>
              </div>
              <div v-if="record.comment" class="wf-record-comment">
                {{ record.comment }}
              </div>
              <div class="wf-record-time">
                {{ record.createdAt }}
              </div>
            </a-timeline-item>
          </a-timeline>
        </div>
      </div>
    </a-spin>

    <!-- Approve Modal -->
    <a-modal
      v-model:open="showApproveModal"
      title="审批通过"
      :width="800"
      class="lg-modal-form is-compact"
      :confirm-loading="actionLoading"
      ok-text="同意"
      cancel-text="取消"
      @ok="handleApprove"
    >
      <a-textarea v-model:value="approvalComment" placeholder="审批意见（选填）" :rows="3" />
    </a-modal>

    <!-- Reject Modal -->
    <a-modal
      v-model:open="showRejectModal"
      title="驳回"
      :width="800"
      class="lg-modal-form is-compact"
      :confirm-loading="actionLoading"
      ok-text="驳回"
      cancel-text="取消"
      @ok="handleReject"
    >
      <a-textarea v-model:value="approvalComment" placeholder="请输入驳回原因（必填）" :rows="3" />
    </a-modal>

    <!-- Transfer Modal -->
    <a-modal
      v-model:open="showTransferModal"
      title="转办"
      :width="800"
      class="lg-modal-form is-compact"
      :confirm-loading="actionLoading"
      ok-text="转办"
      cancel-text="取消"
      @ok="handleTransfer"
    >
      <a-form layout="vertical">
        <a-form-item label="目标用户ID" required>
          <a-input v-model:value="transferTargetUserId" placeholder="请输入目标用户的ID" />
        </a-form-item>
        <a-form-item label="转办说明">
          <a-textarea v-model:value="transferComment" placeholder="转办说明（选填）" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Add Sign Modal -->
    <a-modal
      v-model:open="showAddSignModal"
      title="加签"
      :width="800"
      class="lg-modal-form is-compact"
      :confirm-loading="actionLoading"
      ok-text="加签"
      cancel-text="取消"
      @ok="handleAddSign"
    >
      <a-form layout="vertical">
        <a-form-item label="加签审批人" required>
          <a-select
            v-model:value="addSignUserIds"
            mode="tags"
            placeholder="请输入审批人用户ID（可输入多个）"
            class="lg-full-control"
          />
        </a-form-item>
        <a-form-item label="加签说明">
          <a-textarea v-model:value="addSignComment" placeholder="加签说明（选填）" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.wf-section-title {
  margin-top: 0;
}

.wf-inline-tag {
  margin-left: 8px;
}

.wf-task-comment,
.wf-record-time {
  color: var(--muted);
  font-size: 12px;
}

.wf-record-comment {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}

.wf-record-time {
  margin-top: 2px;
}
</style>
