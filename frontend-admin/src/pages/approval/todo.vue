<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { MoreOutlined } from '@ant-design/icons-vue'
import {
  approveTask,
  getInstanceDetail,
  getMyTodos,
  getMyDone,
  getMyCc,
  getMyInitiatedInstances,
  rejectTask,
  resubmitInstance,
  withdrawInstance,
  type WfTaskVO,
  type WfRecordVO,
  type WfCcVO,
  type WfMineInstanceVO,
  type WfInstanceVO,
} from '@/api/modules/workflow'
import type { PageResult } from '@/types/api'
import { ColumnSettingsButton } from '@/components/list-page'
import { useColumnSettings } from '@/composables/useColumnSettings'

const router = useRouter()
const route = useRoute()

const activeTab = ref(String(route.meta.approvalTab ?? 'todo'))

const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const tabTotals = ref<Record<string, number>>({ todo: 0, done: 0, cc: 0, mine: 0 })
const mineStatus = ref('')

const todoData = ref<WfTaskVO[]>([])
const doneData = ref<WfRecordVO[]>([])
const ccData = ref<WfCcVO[]>([])
const mineData = ref<WfMineInstanceVO[]>([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<WfInstanceVO | null>(null)
const actionLoading = ref(false)
const approvalComment = ref('')
const showApproveModal = ref(false)
const showRejectModal = ref(false)

const businessTypeMap: Record<string, string> = {
  CONTRACT_APPROVAL: '合同审批',
  PAY_APPLICATION: '付款申请',
  PAY_REQUEST: '付款申请',
  PURCHASE_ORDER: '采购订单',
  PURCHASE_REQUEST: '采购申请',
  MATERIAL_RECEIPT: '材料验收',
  VAR_ORDER: '签证变更',
  CT_CHANGE: '合同变更',
  MAT_RECEIPT: '材料验收',
  SUB_MEASURE: '分包计量',
  SETTLEMENT: '结算审批',
  COST_TARGET: '目标成本',
}

const statusMap: Record<string, { text: string; color: string }> = {
  RUNNING: { text: '审批中', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  WITHDRAWN: { text: '已撤回', color: 'default' },
}

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

const mineStatusOptions = [
  { label: '全部', value: '' },
  { label: '审批中', value: 'RUNNING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已撤回', value: 'WITHDRAWN' },
]

function syncActiveTotal(value: unknown) {
  const nextTotal = Number(value ?? 0)
  total.value = Number.isFinite(nextTotal) ? nextTotal : 0
  tabTotals.value[activeTab.value] = total.value
}

async function fetchData() {
  loading.value = true
  try {
    const params = { pageNo: pageNo.value, pageNum: pageNo.value, pageSize: pageSize.value }
    if (activeTab.value === 'mine' && mineStatus.value) {
      Object.assign(params, { instanceStatus: mineStatus.value })
    }

    if (activeTab.value === 'todo') {
      const res: PageResult<WfTaskVO> = await getMyTodos(params)
      todoData.value = res.records
      syncActiveTotal(res.total)
    } else if (activeTab.value === 'done') {
      const res: PageResult<WfRecordVO> = await getMyDone(params)
      doneData.value = res.records
      syncActiveTotal(res.total)
    } else if (activeTab.value === 'cc') {
      const res: PageResult<WfCcVO> = await getMyCc(params)
      ccData.value = res.records
      syncActiveTotal(res.total)
    } else if (activeTab.value === 'mine') {
      const res: PageResult<WfMineInstanceVO> = await getMyInitiatedInstances(params)
      mineData.value = res.records
      syncActiveTotal(res.total)
    }
  } catch (e: unknown) {
    console.error(e)
    if (activeTab.value === 'todo') todoData.value = []
    else if (activeTab.value === 'done') doneData.value = []
    else if (activeTab.value === 'cc') ccData.value = []
    else if (activeTab.value === 'mine') mineData.value = []
    syncActiveTotal(0)
    message.error('加载列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleTabChange(key: string) {
  pageNo.value = 1
  router.push(`/approval/${key}`)
  fetchData()
}

function handlePageChange(pno: number, psize: number) {
  pageNo.value = pno
  pageSize.value = psize
  fetchData()
}

function handleMineStatusChange(value: string | number) {
  mineStatus.value = String(value)
  pageNo.value = 1
  fetchData()
}

async function handleDetail(record: { instanceId: string }) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getInstanceDetail(record.instanceId)
  } catch (e: unknown) {
    console.error(e)
    message.error('加载审批详情失败')
  } finally {
    detailLoading.value = false
  }
}

const gridColumns = computed(() => {
  if (activeTab.value === 'mine') {
    return [
      { field: 'businessType', title: '业务类型', width: 120, slots: { default: 'businessType' } },
      { field: 'title', title: '审批标题', ellipsis: true, slots: { default: 'title' } },
      { field: 'instanceStatus', title: '当前状态', width: 100, slots: { default: 'instanceStatus' } },
      { field: 'createdAt', title: '发起时间', width: 160, slots: { default: 'createdAt' } },
      { field: 'updatedAt', title: '最近更新时间', width: 160, slots: { default: 'updatedAt' } },
      { field: 'currentNodeName', title: '当前节点', width: 140, slots: { default: 'currentNodeName' } },
      { title: '操作', width: 76, slots: { default: 'action' } },
    ]
  }
  return [
    { field: 'title', title: '审批标题', ellipsis: true, slots: { default: 'title' } },
    { field: 'businessType', title: '业务类型', width: 120, slots: { default: 'businessType' } },
    { field: 'timeCol', title: '时间', width: 160, slots: { default: 'timeCol' } },
    { field: 'instanceStatus', title: '状态', width: 100, slots: { default: 'instanceStatus' } },
    { title: '操作', width: 76, slots: { default: 'action' } },
  ]
})

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('approval_todo_cols', gridColumns)

const tabs = [
  { key: 'todo', label: '我的待办' },
  { key: 'done', label: '我的已办' },
  { key: 'cc', label: '抄送我的' },
  { key: 'mine', label: '我发起' },
]

const tableData = computed<Record<string, unknown>[]>(() => {
  if (activeTab.value === 'todo') return todoData.value as unknown as Record<string, unknown>[]
  if (activeTab.value === 'done') return doneData.value as unknown as Record<string, unknown>[]
  if (activeTab.value === 'mine') return mineData.value as unknown as Record<string, unknown>[]
  return ccData.value as unknown as Record<string, unknown>[]
})
const approvalSummary = computed(() => [
  { label: '待办任务', count: tabTotals.value.todo, color: '#1890ff' },
  { label: '已处理记录', count: tabTotals.value.done, color: '#52c41a' },
  { label: '抄送记录', count: tabTotals.value.cc, color: '#faad14' },
  { label: '发起实例', count: tabTotals.value.mine, color: '#722ed1' },
])
const recentApprovals = computed(() => tableData.value.slice(0, 4))
const detailNodes = computed(() => (Array.isArray(detail.value?.nodes) ? detail.value.nodes : []))
const detailRecords = computed(() =>
  Array.isArray(detail.value?.records) ? detail.value.records : [],
)
const completedNodeCount = computed(
  () => detailNodes.value.filter((node) => node.nodeStatus === 'COMPLETED').length,
)
const availableActions = computed(() =>
  Array.isArray(detail.value?.availableActions) ? detail.value.availableActions : [],
)
const isDetailRunning = computed(() => detail.value?.instanceStatus === 'RUNNING')

function findMyPendingTask() {
  const activeNode = detailNodes.value.find((node) => node.nodeStatus === 'ACTIVE')
  const tasks = Array.isArray(activeNode?.tasks) ? activeNode.tasks : []
  return tasks.find((task) => task.taskStatus === 'PENDING')
}

function openApproveModal() {
  approvalComment.value = ''
  showApproveModal.value = true
}

function openRejectModal() {
  approvalComment.value = ''
  showRejectModal.value = true
}

async function refreshDetail() {
  if (!detail.value?.id) return
  detail.value = await getInstanceDetail(detail.value.id)
  fetchData()
}

async function handleApprove() {
  const task = findMyPendingTask()
  if (!task) {
    message.error('未找到待处理任务')
    return
  }
  actionLoading.value = true
  try {
    await approveTask(task.id, {
      action: 'APPROVE',
      comment: approvalComment.value,
      idempotencyKey: `${task.id}-${Date.now()}`,
    })
    message.success('审批通过')
    showApproveModal.value = false
    await refreshDetail()
  } catch (e: unknown) {
    console.error(e)
    message.error('审批操作失败')
  } finally {
    actionLoading.value = false
  }
}

async function handleReject() {
  if (!approvalComment.value.trim()) {
    message.warning('请输入驳回原因')
    return
  }
  const task = findMyPendingTask()
  if (!task) {
    message.error('未找到待处理任务')
    return
  }
  actionLoading.value = true
  try {
    await rejectTask(task.id, {
      action: 'REJECT',
      comment: approvalComment.value,
      idempotencyKey: `${task.id}-${Date.now()}`,
    })
    message.success('已驳回')
    showRejectModal.value = false
    await refreshDetail()
  } catch (e: unknown) {
    console.error(e)
    message.error('审批操作失败')
  } finally {
    actionLoading.value = false
  }
}

async function handleWithdraw() {
  if (!detail.value?.id) return
  actionLoading.value = true
  try {
    await withdrawInstance(detail.value.id)
    message.success('已撤回')
    await refreshDetail()
  } catch (e: unknown) {
    console.error(e)
    message.error('撤回失败')
  } finally {
    actionLoading.value = false
  }
}

async function handleResubmit() {
  if (!detail.value?.id) return
  const instanceId = detail.value.id
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
        await refreshDetail()
      } catch (e: unknown) {
        console.error(e)
        message.error('重新提交失败')
      } finally {
        actionLoading.value = false
      }
    },
  })
}

function getTimeCol(record: Record<string, unknown>): string {
  if (activeTab.value === 'todo') return (record.receivedAt as string) ?? ''
  if (activeTab.value === 'done') return (record.createdAt as string) ?? ''
  return (record.createdTime as string) ?? ''
}

function displayText(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

function getInstanceStatusMeta(status: unknown) {
  const key = String(status ?? '')
  return statusMap[key] ?? { text: displayText(status), color: 'default' }
}

function getActionLabel(): string {
  return activeTab.value === 'todo' ? '处理' : '查看'
}

function pageHeaderTitle(): string {
  const t = tabs.find((t) => t.key === activeTab.value)
  return t?.label ?? '我的待办'
}

function pageHeaderSubtitle(): string {
  if (activeTab.value === 'todo') return '处理需要您审批的业务单据'
  if (activeTab.value === 'done') return '查看您已处理的审批记录'
  if (activeTab.value === 'mine') return '追踪您发起的审批实例'
  return '查看抄送给您的业务单据'
}

function tableEmptyText(): string {
  if (activeTab.value === 'todo') return '暂无待办任务'
  if (activeTab.value === 'done') return '暂无已处理记录'
  if (activeTab.value === 'mine') return '暂无发起记录'
  return '暂无抄送记录'
}

function shouldShowTableEmpty(): boolean {
  return total.value === 0 && tableData.value.length === 0
}

onMounted(() => {
  fetchData()
})

watch(
  () => route.meta.approvalTab,
  (tab) => {
    const nextTab = String(tab ?? 'todo')
    if (nextTab === activeTab.value) return
    activeTab.value = nextTab
    pageNo.value = 1
    fetchData()
  },
)
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
        <a-breadcrumb-item>审批中心</a-breadcrumb-item>
        <a-breadcrumb-item>{{ pageHeaderTitle() }}</a-breadcrumb-item>
      </a-breadcrumb>
      <p class="app-page-subtitle" style="margin: 0; color: var(--text-secondary); font-size: 13px">
        {{ pageHeaderSubtitle() }}
      </p>
    </div>

    <div class="lg-grid">
      <main class="lg-list-table-panel">
        <div class="lg-tabs-toolbar">
          <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
            <a-tab-pane v-for="tab in tabs" :key="tab.key" :tab="tab.label" />
          </a-tabs>
          <a-segmented
            v-if="activeTab === 'mine'"
            v-model:value="mineStatus"
            :options="mineStatusOptions"
            @change="handleMineStatusChange"
          />
          <ColumnSettingsButton
            :columns="columnSettings"
            :visible="colVisible"
            @toggle="toggleCol"
          />
        </div>

        <div class="lg-table-wrap">
          <vxe-grid
            :data="tableData"
            :columns="visibleGridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            stripe
            border="inner"
            size="small"
          >
            <template #title="{ row }">
              <a class="lg-link" @click="handleDetail(row as { instanceId: string })">{{
                row.title
              }}</a>
            </template>
            <template #businessType="{ row }">
              <a-tag>{{
                businessTypeMap[row.businessType as string] || (row.businessType as string) || '—'
              }}</a-tag>
            </template>
            <template #timeCol="{ row }">
              {{ getTimeCol(row) }}
            </template>
            <template #createdAt="{ row }">
              {{ displayText(row.createdAt) }}
            </template>
            <template #updatedAt="{ row }">
              {{ displayText(row.updatedAt) }}
            </template>
            <template #currentNodeName="{ row }">
              {{ displayText(row.currentNodeName) }}
            </template>
            <template #instanceStatus="{ row }">
              <a-tag :color="getInstanceStatusMeta(row.instanceStatus).color">
                {{ getInstanceStatusMeta(row.instanceStatus).text }}
              </a-tag>
            </template>
            <template #action="{ row }">
              <a-dropdown :trigger="['click']">
                <a-button class="lg-row-action-trigger" size="small" type="text">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item @click="handleDetail(row as { instanceId: string })">
                      {{ getActionLabel() }}
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </template>
            <template #empty>
              <div v-if="shouldShowTableEmpty()" class="lg-empty-text">{{ tableEmptyText() }}</div>
            </template>
          </vxe-grid>
        </div>

        <div class="lg-pagination">
          <span class="lg-total">共 {{ total }} 条</span>
          <a-pagination
            v-model:current="pageNo"
            v-model:page-size="pageSize"
            :total="total"
            :page-size-options="['10', '20', '50', '100']"
            show-size-changer
            show-quick-jumper
            @change="(p: number, ps: number) => handlePageChange(p, ps)"
          />
        </div>
      </main>

      <aside class="lg-analysis-rail">
        <div class="lg-panel">
          <div class="lg-panel-title">审批分类</div>
          <div class="lg-type-list">
            <div v-for="item in approvalSummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </div>
          </div>
        </div>
        <div class="lg-panel">
          <div class="lg-panel-title">近期审批</div>
          <div class="lg-rail-list">
            <div
              v-for="item in recentApprovals"
              :key="String(item.instanceId ?? item.id ?? item.title)"
              class="lg-rail-item"
            >
              <span class="lg-type-dot"></span>
              <span>{{ item.title }}</span>
            </div>
            <div v-if="!recentApprovals.length" class="lg-empty-text">暂无审批</div>
          </div>
        </div>
      </aside>
    </div>

    <a-modal
      v-model:open="detailVisible"
      title="审批详情"
      :footer="null"
      :width="800"
      wrap-class-name="approval-detail-modal"
    >
      <a-spin :spinning="detailLoading">
        <div v-if="detail" class="approval-detail-content">
          <div class="approval-detail-head">
            <div>
              <strong>{{ detail.title }}</strong>
              <span>{{ businessTypeMap[detail.businessType] || detail.businessType }}</span>
            </div>
            <a-tag :color="statusMap[detail.instanceStatus]?.color">
              {{ statusMap[detail.instanceStatus]?.text || detail.instanceStatus }}
            </a-tag>
          </div>

          <a-descriptions bordered size="small" :column="2">
            <a-descriptions-item label="审批标题">{{ detail.title }}</a-descriptions-item>
            <a-descriptions-item label="模板名称">{{ detail.templateName }}</a-descriptions-item>
            <a-descriptions-item label="发起人">{{ detail.initiatorName }}</a-descriptions-item>
            <a-descriptions-item label="发起时间">{{ detail.startedAt }}</a-descriptions-item>
            <a-descriptions-item v-if="detail.amount" label="金额">
              {{ Number(detail.amount).toLocaleString('zh-CN') }} 元
            </a-descriptions-item>
            <a-descriptions-item label="当前轮次">
              第 {{ detail.currentRound }} 轮
            </a-descriptions-item>
            <a-descriptions-item v-if="detail.businessSummary" label="业务摘要" :span="2">
              {{ detail.businessSummary }}
            </a-descriptions-item>
          </a-descriptions>

          <div v-if="availableActions.length > 0 && isDetailRunning" class="approval-actions">
            <a-button
              v-if="availableActions.includes('approve')"
              type="primary"
              :loading="actionLoading"
              @click="openApproveModal"
            >
              同意
            </a-button>
            <a-button
              v-if="availableActions.includes('reject')"
              danger
              :loading="actionLoading"
              @click="openRejectModal"
            >
              驳回
            </a-button>
            <a-button
              v-if="availableActions.includes('withdraw')"
              :loading="actionLoading"
              @click="handleWithdraw"
            >
              撤回
            </a-button>
          </div>
          <div
            v-if="availableActions.includes('resubmit') && !isDetailRunning"
            class="approval-actions"
          >
            <a-button type="primary" :loading="actionLoading" @click="handleResubmit">
              重新提交
            </a-button>
          </div>

          <section class="approval-detail-section">
            <h3>审批流程</h3>
            <a-steps :current="completedNodeCount" size="small" direction="vertical">
              <a-step v-for="node in detailNodes" :key="node.id">
                <template #title>
                  {{ node.nodeName }}
                  <a-tag :color="nodeStatusMap[node.nodeStatus]?.color">
                    {{ nodeStatusMap[node.nodeStatus]?.text || node.nodeStatus }}
                  </a-tag>
                </template>
                <template #description>
                  <div v-if="Array.isArray(node.tasks)" class="approval-node-tasks">
                    <span v-for="task in node.tasks" :key="task.id">
                      {{ task.approverName }}
                      <a-tag :color="taskStatusMap[task.taskStatus]?.color">
                        {{ taskStatusMap[task.taskStatus]?.text || task.taskStatus }}
                      </a-tag>
                    </span>
                  </div>
                </template>
              </a-step>
            </a-steps>
          </section>

          <section class="approval-detail-section">
            <h3>审批记录</h3>
            <a-timeline>
              <a-timeline-item v-for="record in detailRecords" :key="record.id">
                <strong>{{ record.operatorName }}</strong>
                <a-tag>{{ actionNameMap[record.actionType] || record.actionName }}</a-tag>
                <p v-if="record.comment">{{ record.comment }}</p>
                <small>{{ record.createdAt }}</small>
              </a-timeline-item>
            </a-timeline>
            <div v-if="!detailRecords.length" class="lg-empty-text">暂无审批记录</div>
          </section>
        </div>
      </a-spin>
    </a-modal>

    <a-modal
      v-model:open="showApproveModal"
      title="审批通过"
      :width="800"
      :confirm-loading="actionLoading"
      @ok="handleApprove"
    >
      <a-textarea v-model:value="approvalComment" placeholder="审批意见（选填）" :rows="3" />
    </a-modal>

    <a-modal
      v-model:open="showRejectModal"
      title="驳回"
      :width="800"
      :confirm-loading="actionLoading"
      @ok="handleReject"
    >
      <a-textarea v-model:value="approvalComment" placeholder="请输入驳回原因（必填）" :rows="3" />
    </a-modal>
  </div>
</template>

<style scoped>
.approval-detail-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.approval-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.approval-detail-head > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.approval-detail-head strong {
  color: var(--text);
  font-size: 16px;
}

.approval-detail-head span {
  color: var(--text-secondary);
  font-size: 12px;
}

.approval-actions {
  display: flex;
  gap: 8px;
  padding: 10px 0 2px;
}

.approval-detail-section h3 {
  margin: 0 0 10px;
  color: var(--text);
  font-size: 14px;
}

.approval-node-tasks {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.approval-detail-section p {
  margin: 4px 0 2px;
  color: var(--text-secondary);
}

.approval-detail-section small {
  color: var(--muted);
}

:global(.approval-detail-modal .ant-modal-body) {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
}
</style>
