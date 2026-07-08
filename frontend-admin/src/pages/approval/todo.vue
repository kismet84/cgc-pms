<script setup lang="ts">
import dayjs from 'dayjs'
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { MoreOutlined } from '@ant-design/icons-vue'
import type { Dayjs } from 'dayjs'
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
import type { PageParams, PageResult } from '@/types/api'
import { ColumnSettingsButton, LgEmptyState } from '@/components/list-page'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { readPositiveIntQuery, readStringQuery, replaceListQuery } from '@/composables/listPageQuery'
import { useUserStore } from '@/stores/user'
import {
  canAccessWorkflowBusinessEntry,
  coreBusinessTypeOptions,
  getWorkflowBusinessEntryPath,
  getWorkflowBusinessTypeLabel,
  getWorkflowInstanceStatusMeta,
  getWorkflowNodeStatusMeta,
  getWorkflowTaskStatusMeta,
  instanceStatusOptions,
  preloadWorkflowDisplayDicts,
  WF_INSTANCE_RUNNING,
} from './workflowDisplay'

// 字典常量 - 工作流节点状态
const WF_NODE_ACTIVE = 'ACTIVE'
const WF_NODE_COMPLETED = 'COMPLETED'

// 字典常量 - 工作流任务状态
const WF_TASK_PENDING = 'PENDING'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref(String(route.meta.approvalTab ?? 'todo'))

const loading = ref(false)
const hasLoaded = ref(false)
const listError = ref<string | null>(null)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const tabTotals = ref<Record<string, number>>({ todo: 0, done: 0, cc: 0, mine: 0 })
const filterKeyword = ref('')
const filterBusinessType = ref('')
const filterInstanceStatus = ref('')
const filterTimeRange = ref<[Dayjs, Dayjs] | null>(null)

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

const statusFilterOptions = [{ label: '全部', value: '' }, ...instanceStatusOptions]

const businessTypeFilterOptions = [{ label: '全部业务', value: '' }, ...coreBusinessTypeOptions]

function hydrateFromRouteQuery() {
  filterKeyword.value = readStringQuery(route.query.keyword) ?? ''
  filterBusinessType.value = readStringQuery(route.query.businessType) ?? ''
  filterInstanceStatus.value = readStringQuery(route.query.instanceStatus) ?? ''
  const startTime = readStringQuery(route.query.startTime)
  const endTime = readStringQuery(route.query.endTime)
  filterTimeRange.value =
    startTime && endTime ? [dayjs(startTime), dayjs(endTime)] : null
  pageNo.value = readPositiveIntQuery(route.query.pageNo, 1)
  pageSize.value = readPositiveIntQuery(route.query.pageSize, 20)
}

async function syncRouteQuery() {
  const nextQuery = replaceListQuery(
    route.query,
    {
      keyword: filterKeyword.value.trim() || undefined,
      businessType: filterBusinessType.value || undefined,
      instanceStatus: filterInstanceStatus.value || undefined,
      startTime: filterTimeRange.value?.[0]?.startOf('day').format('YYYY-MM-DD HH:mm:ss'),
      endTime: filterTimeRange.value?.[1]?.endOf('day').format('YYYY-MM-DD HH:mm:ss'),
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    },
    ['keyword', 'businessType', 'instanceStatus', 'startTime', 'endTime', 'pageNo', 'pageSize'],
  )
  await router.replace({ path: route.path, query: nextQuery })
}

function syncActiveTotal(value: unknown) {
  const nextTotal = Number(value ?? 0)
  total.value = Number.isFinite(nextTotal) ? nextTotal : 0
  tabTotals.value[activeTab.value] = total.value
}

function buildQueryParams(): PageParams {
  const params: PageParams = {
    pageNo: pageNo.value,
    pageNum: pageNo.value,
    pageSize: pageSize.value,
  }
  const keyword = filterKeyword.value.trim()
  if (keyword) params.keyword = keyword
  if (filterBusinessType.value) params.businessType = filterBusinessType.value
  if (filterInstanceStatus.value) params.instanceStatus = filterInstanceStatus.value
  if (filterTimeRange.value?.[0] && filterTimeRange.value?.[1]) {
    params.startTime = filterTimeRange.value[0].startOf('day').format('YYYY-MM-DD HH:mm:ss')
    params.endTime = filterTimeRange.value[1].endOf('day').format('YYYY-MM-DD HH:mm:ss')
  }
  return params
}

async function fetchData() {
  listError.value = null
  await syncRouteQuery()
  loading.value = true
  try {
    const params = buildQueryParams()

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
    listError.value = '请检查筛选条件或网络状态后重试。'
    message.error('加载列表失败，请稍后重试')
  } finally {
    hasLoaded.value = true
    loading.value = false
  }
}

function handleTabChange(key: string) {
  pageNo.value = 1
  router.push({
    path: `/approval/${key}`,
    query: replaceListQuery(
      route.query,
      {
        keyword: filterKeyword.value.trim() || undefined,
        businessType: filterBusinessType.value || undefined,
        instanceStatus: filterInstanceStatus.value || undefined,
        startTime: filterTimeRange.value?.[0]?.startOf('day').format('YYYY-MM-DD HH:mm:ss'),
        endTime: filterTimeRange.value?.[1]?.endOf('day').format('YYYY-MM-DD HH:mm:ss'),
        pageNo: 1,
        pageSize: pageSize.value,
      },
      ['keyword', 'businessType', 'instanceStatus', 'startTime', 'endTime', 'pageNo', 'pageSize'],
    ),
  })
}

function handlePageChange(pno: number, psize: number) {
  pageNo.value = pno
  pageSize.value = psize
  fetchData()
}

function handleFilterSearch() {
  pageNo.value = 1
  fetchData()
}

function handleFilterReset() {
  filterKeyword.value = ''
  filterBusinessType.value = ''
  filterInstanceStatus.value = ''
  filterTimeRange.value = null
  handleFilterSearch()
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
      {
        field: 'instanceStatus',
        title: '当前状态',
        width: 100,
        slots: { default: 'instanceStatus' },
      },
      { field: 'createdAt', title: '发起时间', width: 160, slots: { default: 'createdAt' } },
      { field: 'updatedAt', title: '最近更新时间', width: 160, slots: { default: 'updatedAt' } },
      {
        field: 'currentNodeName',
        title: '当前节点',
        width: 140,
        slots: { default: 'currentNodeName' },
      },
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
const hasActiveFilters = computed(
  () =>
    Boolean(filterKeyword.value.trim()) ||
    Boolean(filterBusinessType.value) ||
    Boolean(filterInstanceStatus.value) ||
    Boolean(filterTimeRange.value?.length),
)
const showEmptyState = computed(
  () => hasLoaded.value && !loading.value && !listError.value && !tableData.value.length,
)
const detailNodes = computed(() => (Array.isArray(detail.value?.nodes) ? detail.value.nodes : []))
const detailRecords = computed(() =>
  Array.isArray(detail.value?.records) ? detail.value.records : [],
)
const completedNodeCount = computed(
  () => detailNodes.value.filter((node) => node.nodeStatus === WF_NODE_COMPLETED).length,
)
const availableActions = computed(() =>
  Array.isArray(detail.value?.availableActions) ? detail.value.availableActions : [],
)
const isDetailRunning = computed(() => detail.value?.instanceStatus === WF_INSTANCE_RUNNING)
const fullDetailOnlyActions = computed(() =>
  availableActions.value.filter((action) => action === 'transfer' || action === 'addSign'),
)

function canShowApprovalActions() {
  return activeTab.value === 'todo' && isDetailRunning.value
}

function canShowInitiatorActions() {
  return activeTab.value === 'mine'
}

function findMyPendingTask() {
  const activeNode = detailNodes.value.find((node) => node.nodeStatus === WF_NODE_ACTIVE)
  const tasks = Array.isArray(activeNode?.tasks) ? activeNode.tasks : []
  return tasks.find((task) => task.taskStatus === WF_TASK_PENDING)
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

function businessTypeLabel(value: unknown) {
  return getWorkflowBusinessTypeLabel(value)
}

function getInstanceStatusMeta(status: unknown) {
  return getWorkflowInstanceStatusMeta(status)
}

function getRecordActionName(record: WfRecordVO): string {
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

function openFullDetail() {
  if (!detail.value?.id) return
  router.push(`/approval/${detail.value.id}`)
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

function getNodeStatusMeta(status: unknown) {
  return getWorkflowNodeStatusMeta(status, nodeStatusMap)
}

function getTaskStatusMeta(status: unknown) {
  return getWorkflowTaskStatusMeta(status, taskStatusMap)
}

onMounted(() => {
  hydrateFromRouteQuery()
  preloadWorkflowDisplayDicts()
  fetchData()
})

watch(
  () => route.meta.approvalTab,
  (tab) => {
    const nextTab = String(tab ?? 'todo')
    if (nextTab === activeTab.value) return
    activeTab.value = nextTab
    hydrateFromRouteQuery()
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
          <ColumnSettingsButton
            :columns="columnSettings"
            :visible="colVisible"
            @toggle="toggleCol"
          />
        </div>

        <div class="lg-search-bar approval-filter-bar">
          <a-input
            v-model:value="filterKeyword"
            allow-clear
            placeholder="搜索标题/摘要"
            class="approval-filter-keyword"
            @press-enter="handleFilterSearch"
          />
          <a-select
            v-model:value="filterBusinessType"
            :options="businessTypeFilterOptions"
            placeholder="全部业务"
            style="width: 150px"
          />
          <a-select
            v-model:value="filterInstanceStatus"
            :options="statusFilterOptions"
            placeholder="全部状态"
            style="width: 140px"
          />
          <a-range-picker v-model:value="filterTimeRange" />
          <div class="lg-search-actions">
            <a-button type="primary" @click="handleFilterSearch">查询</a-button>
            <a-button @click="handleFilterReset">重置</a-button>
          </div>
        </div>

        <div class="lg-table-wrap">
          <div v-if="listError" class="approval-list-feedback">
            <a-result status="error" title="审批列表加载失败" :sub-title="listError">
              <template #extra>
                <a-button type="primary" @click="fetchData">重试</a-button>
              </template>
            </a-result>
          </div>
          <div v-else-if="showEmptyState" class="approval-list-feedback">
            <LgEmptyState :description="tableEmptyText()">
              <a-button v-if="hasActiveFilters" @click="handleFilterReset">清空筛选</a-button>
            </LgEmptyState>
          </div>
          <vxe-grid
            v-else
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
              <a-tag>{{ businessTypeLabel(row.businessType) }}</a-tag>
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
              <span>{{ businessTypeLabel(detail.businessType) }}</span>
            </div>
            <a-tag :color="getInstanceStatusMeta(detail.instanceStatus).color">
              {{ getInstanceStatusMeta(detail.instanceStatus).text }}
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
          <div v-if="businessEntryPath(detail)" class="approval-actions">
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

          <div
            v-if="availableActions.length > 0 && canShowApprovalActions()"
            class="approval-actions"
          >
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
          </div>
          <div
            v-if="availableActions.includes('withdraw') && canShowInitiatorActions()"
            class="approval-actions"
          >
            <a-button :loading="actionLoading" @click="handleWithdraw"> 撤回 </a-button>
          </div>
          <a-alert
            v-if="fullDetailOnlyActions.length > 0 && canShowApprovalActions()"
            class="approval-action-hint"
            type="info"
            show-icon
          >
            <template #message>
              转办、加签等完整处理入口请进入独立详情页
              <a-button type="link" size="small" @click="openFullDetail">打开详情</a-button>
            </template>
          </a-alert>
          <!-- v-if="availableActions.includes('resubmit') && canShowInitiatorActions() && !isDetailRunning" -->
          <div
            v-if="
              availableActions.includes('resubmit') && canShowInitiatorActions() && !isDetailRunning
            "
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
                  <a-tag :color="getNodeStatusMeta(node.nodeStatus).color">
                    {{ getNodeStatusMeta(node.nodeStatus).text }}
                  </a-tag>
                </template>
                <template #description>
                  <div v-if="Array.isArray(node.tasks)" class="approval-node-tasks">
                    <span v-for="task in node.tasks" :key="task.id">
                      {{ task.approverName }}
                      <a-tag :color="getTaskStatusMeta(task.taskStatus).color">
                        {{ getTaskStatusMeta(task.taskStatus).text }}
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
                <a-tag>{{ getRecordActionName(record) }}</a-tag>
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

.approval-action-hint {
  margin-top: 4px;
}

.approval-filter-bar {
  padding: 12px 0;
}

.approval-filter-keyword {
  flex: 1;
  min-width: 220px;
}

.approval-list-feedback {
  padding: 12px 0;
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
