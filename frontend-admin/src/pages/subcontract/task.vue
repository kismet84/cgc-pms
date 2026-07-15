<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileDoneOutlined,
  FilterOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  SyncOutlined,
} from '@ant-design/icons-vue'
import {
  getSubTaskList,
  createSubTask,
  updateSubTask,
  deleteSubTask,
} from '@/api/modules/subcontract'
import { useReferenceStore } from '@/stores/reference'
import type { SubTaskVO } from '@/types/subcontract'
import type { SelectOption } from '@/types/ui'
import {
  readPositiveIntQuery,
  readStringQuery,
  replaceListQuery,
} from '@/composables/listPageQuery'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton, LgEmptyState } from '@/components/list-page'
import {
  SUBCONTRACT_TASK_GRID_COLUMNS,
  SUBCONTRACT_TASK_STATUS_COLOR,
  SUBCONTRACT_TASK_STATUS_LABEL,
} from './pageConfig'

const filterPanelOpen = ref(false)
const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  status: undefined as string | undefined,
  taskCode: '',
  taskName: '',
  keyword: '',
})

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const hasLoaded = ref(false)
const listError = ref<string | null>(null)
const tableData = ref<SubTaskVO[]>([])
const predecessorTasks = ref<SubTaskVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const { projects: projectList, contracts: contractList } = storeToRefs(referenceStore)

const modalVisible = ref(false)
const modalTitle = ref('新建分包任务')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<SubTaskVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  predecessorTaskId: undefined,
  taskName: '',
  workArea: '',
  plannedStartDate: undefined,
  plannedEndDate: undefined,
  actualStartDate: undefined,
  actualEndDate: undefined,
  progressPercent: '0',
  status: 'NOT_STARTED',
  remark: '',
})
const formPartnerName = computed(
  () => contractList.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)
const predecessorOptions = computed(() =>
  predecessorTasks.value.filter(
    (task) => task.projectId === formData.projectId && task.id !== editingId.value,
  ),
)
const predecessorStartBlocked = computed(() => {
  const predecessor = predecessorOptions.value.find(
    (task) => task.id === formData.predecessorTaskId,
  )
  return predecessor != null && predecessor.status !== 'COMPLETED'
})

async function loadPredecessorOptions(projectId?: string) {
  if (!projectId) {
    predecessorTasks.value = []
    return
  }
  try {
    const res = await getSubTaskList({ pageNum: 1, pageSize: 1000, projectId })
    predecessorTasks.value = res.records
  } catch {
    predecessorTasks.value = []
    message.error('加载同项目前置任务失败，请稍后重试')
  }
}

function filterSelectOption(input: string, option: SelectOption) {
  return option.label?.toLowerCase().includes(input.toLowerCase()) ?? false
}

function onContractChange(contractId: string) {
  const c = contractList.value?.find((ct) => ct.id === contractId)
  formData.partnerId = c?.partyBId
}
watch(
  () => formData.contractId,
  (val) => {
    if (!val) formData.partnerId = undefined
  },
)

const STATUS_LABEL = SUBCONTRACT_TASK_STATUS_LABEL
const STATUS_COLOR = SUBCONTRACT_TASK_STATUS_COLOR

// ---- vxe-grid columns ----
const gridColumns = computed(() => SUBCONTRACT_TASK_GRID_COLUMNS)

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('subcontract_task_cols_v2', gridColumns, {
  plannedStartDate: false,
  plannedEndDate: false,
})

function hydrateFromRouteQuery() {
  filter.projectId = readStringQuery(route.query.projectId)
  filter.status = readStringQuery(route.query.status)
  filter.keyword = readStringQuery(route.query.keyword) ?? ''
  pageNo.value = readPositiveIntQuery(route.query.pageNo, 1)
  pageSize.value = readPositiveIntQuery(route.query.pageSize, 20)
}

async function syncRouteQuery() {
  const nextQuery = replaceListQuery(
    route.query,
    {
      projectId: filter.projectId,
      status: filter.status,
      keyword: filter.keyword,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    },
    ['projectId', 'status', 'keyword', 'pageNo', 'pageSize'],
  )
  await router.replace({ path: route.path, query: nextQuery })
}

async function fetchData() {
  listError.value = null
  await syncRouteQuery()
  loading.value = true
  try {
    const res = await getSubTaskList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      status: filter.status,
      taskCode: filter.keyword || filter.taskCode || undefined,
    })
    tableData.value = res.records
    total.value = Number(res.total ?? 0)
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    listError.value = '请检查筛选条件或网络状态后重试。'
    message.error('加载分包任务列表失败，请稍后重试')
  } finally {
    hasLoaded.value = true
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.status = undefined
  filter.taskCode = ''
  filter.taskName = ''
  filter.keyword = ''
  pageNo.value = 1
  fetchData()
}

function handlePageChange(page: number) {
  pageNo.value = page
  fetchData()
}

function handlePageSizeChange(_cur: number, size: number) {
  pageSize.value = size
  pageNo.value = 1
  fetchData()
}

function handleAdd() {
  modalTitle.value = '新建分包任务'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    predecessorTaskId: undefined,
    taskName: '',
    workArea: '',
    plannedStartDate: undefined,
    plannedEndDate: undefined,
    actualStartDate: undefined,
    actualEndDate: undefined,
    progressPercent: '0',
    status: 'NOT_STARTED',
    remark: '',
  })
  modalVisible.value = true
}

function handleEdit(record: SubTaskVO) {
  modalTitle.value = '编辑分包任务'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    predecessorTaskId: record.predecessorTaskId,
    taskName: record.taskName,
    workArea: record.workArea,
    plannedStartDate: record.plannedStartDate,
    plannedEndDate: record.plannedEndDate,
    actualStartDate: record.actualStartDate,
    actualEndDate: record.actualEndDate,
    progressPercent: record.progressPercent,
    status: record.status,
    remark: record.remark,
  })
  loadPredecessorOptions(record.projectId)
  modalVisible.value = true
}

function handleView(record: SubTaskVO) {
  handleEdit(record)
  modalTitle.value = '查看分包任务'
}

function handleDelete(record: SubTaskVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除分包任务"${record.taskName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteSubTask(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

async function handleModalOk() {
  if (!formData.projectId) {
    message.warning('请选择项目')
    return
  }
  if (!formData.taskName) {
    message.warning('请输入任务名称')
    return
  }

  try {
    if (editingId.value) {
      await updateSubTask(editingId.value, formData)
      message.success('更新成功')
    } else {
      await createSubTask(formData)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error(e instanceof Error && e.message ? e.message : '操作失败，请稍后重试')
  }
}

function handleModalCancel() {
  modalVisible.value = false
}

// ---- KPI ----
const kpiInProgress = computed(
  () => tableData.value.filter((r) => r.status === 'IN_PROGRESS').length,
)
const kpiCompleted = computed(() => tableData.value.filter((r) => r.status === 'COMPLETED').length)
const kpiPending = computed(() => tableData.value.filter((r) => r.status === 'NOT_STARTED').length)
const kpiSuspended = computed(() => tableData.value.filter((r) => r.status === 'SUSPENDED').length)
const avgProgress = computed(() => {
  if (!tableData.value.length) return 0
  const totalProgress = tableData.value.reduce(
    (sum, item) => sum + (parseFloat(item.progressPercent || '0') || 0),
    0,
  )
  return Math.round(totalProgress / tableData.value.length)
})
const taskStatusSummary = computed(() => [
  {
    label: '进行中',
    count: kpiInProgress.value,
    color: '#1890ff',
    pct: statusPct(kpiInProgress.value),
  },
  {
    label: '已完成',
    count: kpiCompleted.value,
    color: '#52c41a',
    pct: statusPct(kpiCompleted.value),
  },
  { label: '待开始', count: kpiPending.value, color: '#faad14', pct: statusPct(kpiPending.value) },
  {
    label: '已暂停',
    count: kpiSuspended.value,
    color: '#ff4d4f',
    pct: statusPct(kpiSuspended.value),
  },
])
const recentTasks = computed(() => tableData.value.slice(0, 4))
const wbsTimelineRows = computed(() => {
  const today = new Date()
  const todayKey = [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, '0'),
    String(today.getDate()).padStart(2, '0'),
  ].join('-')
  const rows = [...tableData.value].sort((a, b) =>
    (a.taskCode || a.taskName || '').localeCompare(b.taskCode || b.taskName || '', 'zh-CN', {
      numeric: true,
    }),
  )
  const times = rows.flatMap((row) =>
    [row.plannedStartDate, row.plannedEndDate]
      .map((date) => (date ? new Date(date).getTime() : Number.NaN))
      .filter(Number.isFinite),
  )
  const min = Math.min(...times)
  const max = Math.max(...times)
  const span = Math.max(max - min, 1)

  return rows.map((row) => {
    const start = row.plannedStartDate ? new Date(row.plannedStartDate).getTime() : Number.NaN
    const end = row.plannedEndDate ? new Date(row.plannedEndDate).getTime() : Number.NaN
    const progress = parseFloat(row.progressPercent || '0') || 0
    const hasPlan = Number.isFinite(start) && Number.isFinite(end)
    const left = hasPlan ? clampPercent(((Math.min(start, end) - min) / span) * 100) : 0
    const width = hasPlan ? clampPercent((Math.abs(end - start) / span) * 100 || 4) : 0
    const isDelayed =
      Number.isFinite(end) &&
      row.plannedEndDate! < todayKey &&
      !row.actualEndDate &&
      row.status !== 'COMPLETED' &&
      progress < 100

    let predecessorRisk: 'INCOMPLETE' | 'LATE' | undefined
    if (
      row.predecessorTaskId &&
      row.plannedStartDate &&
      row.predecessorActualEndDate &&
      row.predecessorActualEndDate > row.plannedStartDate
    ) {
      predecessorRisk = 'LATE'
    } else if (
      row.predecessorTaskId &&
      row.plannedStartDate &&
      row.plannedStartDate <= todayKey &&
      row.predecessorStatus !== 'COMPLETED'
    ) {
      predecessorRisk = 'INCOMPLETE'
    }

    return { row, hasPlan, left, width, isDelayed, predecessorRisk }
  })
})

function statusPct(count: number) {
  const base = tableData.value.length || 0
  if (!base) return 0
  return Math.round((count / base) * 100)
}

function clampPercent(value: number) {
  return Math.min(100, Math.max(0, Math.round(value)))
}

const showEmptyState = computed(() => hasLoaded.value && !loading.value && !tableData.value.length)

onMounted(() => {
  hydrateFromRouteQuery()
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'SUB' })
  referenceStore.fetchPartners({ partnerType: 'SUB' })
  fetchData()
})
</script>

<template>
  <div
    class="lg-list-page lg-page app-page subcontract-task-page procurement-subcontract-list-page"
  >
    <div class="lg-page-head subcontract-task-page-head">
      <a-breadcrumb class="lg-breadcrumb">
        <a-breadcrumb-item>分包管理</a-breadcrumb-item>
        <a-breadcrumb-item>分包任务</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-grid subcontract-task-workspace">
      <div class="lg-left subcontract-task-main-column">
        <div class="lg-kpi-strip subcontract-task-kpi-summary" aria-label="分包任务关键指标">
          <div class="subcontract-task-kpi-item">
            <span class="subcontract-task-kpi-icon is-blue"><FileDoneOutlined /></span>
            <div>
              <span class="subcontract-task-kpi-label">任务总数</span>
              <strong>{{ total }}</strong>
              <span class="subcontract-task-kpi-hint">全部任务</span>
            </div>
          </div>
          <div class="subcontract-task-kpi-item">
            <span class="subcontract-task-kpi-icon is-cyan"><SyncOutlined /></span>
            <div>
              <span class="subcontract-task-kpi-label">进行中</span>
              <strong>{{ kpiInProgress }}</strong>
              <span class="subcontract-task-kpi-hint">{{ statusPct(kpiInProgress) }}%</span>
            </div>
          </div>
          <div class="subcontract-task-kpi-item">
            <span class="subcontract-task-kpi-icon is-green"><CheckCircleOutlined /></span>
            <div>
              <span class="subcontract-task-kpi-label">已完成</span>
              <strong>{{ kpiCompleted }}</strong>
              <span class="subcontract-task-kpi-hint">{{ statusPct(kpiCompleted) }}%</span>
            </div>
          </div>
          <div class="subcontract-task-kpi-item">
            <span class="subcontract-task-kpi-icon is-amber"><ClockCircleOutlined /></span>
            <div>
              <span class="subcontract-task-kpi-label">待开始</span>
              <strong>{{ kpiPending }}</strong>
              <span class="subcontract-task-kpi-hint">{{ statusPct(kpiPending) }}%</span>
            </div>
          </div>
          <div class="subcontract-task-kpi-item">
            <span class="subcontract-task-kpi-icon is-red"><PauseCircleOutlined /></span>
            <div>
              <span class="subcontract-task-kpi-label">已暂停</span>
              <strong>{{ kpiSuspended }}</strong>
              <span class="subcontract-task-kpi-hint">均值 {{ avgProgress }}%</span>
            </div>
          </div>
        </div>

        <div class="lg-search-bar subcontract-task-search-bar procurement-subcontract-query-panel">
          <div
            class="subcontract-task-filter-grid procurement-subcontract-filter-panel"
            :class="{ 'is-open': filterPanelOpen }"
          >
            <a-select
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              size="large"
              show-search
              :filter-option="filterSelectOption"
              @change="handleSearch"
            >
              <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="filter.status"
              placeholder="全部任务状态"
              allow-clear
              size="large"
              @change="handleSearch"
            >
              <a-select-option value="NOT_STARTED">未开始</a-select-option>
              <a-select-option value="IN_PROGRESS">进行中</a-select-option>
              <a-select-option value="COMPLETED">已完成</a-select-option>
              <a-select-option value="SUSPENDED">已暂停</a-select-option>
            </a-select>
          </div>
          <div class="subcontract-task-filter-foot procurement-subcontract-query-row">
            <a-input
              v-model:value="filter.keyword"
              placeholder="搜索任务编号、名称…"
              allow-clear
              size="large"
              @press-enter="handleSearch"
            >
              <template #prefix>
                <SearchOutlined style="color: var(--text-secondary)" />
              </template>
            </a-input>
            <div class="subcontract-task-filter-actions procurement-subcontract-query-actions">
              <a-button
                class="procurement-subcontract-desktop-action"
                type="primary"
                size="large"
                @click="handleSearch"
                >搜索</a-button
              >
              <a-button
                class="procurement-subcontract-desktop-action"
                size="large"
                @click="handleReset"
              >
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
              <a-button
                class="procurement-subcontract-filter-toggle"
                size="large"
                :aria-expanded="filterPanelOpen"
                @click="filterPanelOpen = !filterPanelOpen"
              >
                <template #icon><FilterOutlined /></template>
                筛选
              </a-button>
            </div>
          </div>
        </div>

        <main
          class="lg-list-table-panel subcontract-task-table-panel procurement-subcontract-table-panel"
        >
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <div class="subcontract-task-table-title">
                <strong>任务明细</strong>
                <span>共 {{ total }} 条</span>
              </div>
            </div>
            <div class="lg-toolbar-right">
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建任务
              </a-button>
            </div>
          </div>

          <div class="lg-table-wrap">
            <div v-if="listError" class="subcontract-task-list-feedback">
              <a-result status="error" title="分包任务列表加载失败" :sub-title="listError">
                <template #extra>
                  <a-button type="primary" @click="fetchData">重试</a-button>
                </template>
              </a-result>
            </div>
            <div v-else-if="showEmptyState" class="subcontract-task-list-feedback">
              <LgEmptyState description="暂无符合条件的分包任务">
                <a-button
                  v-if="filter.keyword || filter.projectId || filter.status"
                  @click="handleReset"
                >
                  清空筛选
                </a-button>
                <a-button v-else type="primary" @click="handleAdd">新建任务</a-button>
              </LgEmptyState>
            </div>
            <vxe-grid
              v-else
              class="procurement-subcontract-desktop-table"
              :data="tableData"
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #taskCode="{ row }">
                <a-button class="subcontract-task-code-link" type="link" @click="handleView(row)">
                  {{ row.taskCode || '-' }}
                </a-button>
              </template>
              <template #taskName="{ row }">
                <a class="lg-link">{{ row.taskName }}</a>
              </template>
              <template #progressPercent="{ row }">
                <a-progress
                  v-if="row.progressPercent"
                  :percent="parseFloat(row.progressPercent)"
                  :stroke-width="8"
                  size="small"
                  :show-info="true"
                />
                <span v-else style="color: var(--muted)">-</span>
              </template>
              <template #status="{ row }">
                <a-tag :color="STATUS_COLOR[row.status]">
                  {{ STATUS_LABEL[row.status] ?? row.status }}
                </a-tag>
              </template>
              <template #action="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
            </vxe-grid>
            <div v-if="!listError && !showEmptyState" class="procurement-subcontract-mobile-list">
              <button
                v-for="row in tableData"
                :key="row.id"
                class="procurement-subcontract-mobile-card"
                type="button"
                @click="handleView(row)"
              >
                <span class="procurement-subcontract-mobile-card-title">{{
                  row.taskName || row.taskCode || '-'
                }}</span>
                <span class="procurement-subcontract-mobile-card-status">
                  <a-tag :color="STATUS_COLOR[row.status]">{{
                    STATUS_LABEL[row.status] ?? row.status
                  }}</a-tag>
                </span>
                <span class="procurement-subcontract-mobile-card-subtitle"
                  >{{ row.taskCode || '编号待维护' }} · {{ row.projectName || '未关联项目' }}</span
                >
                <span class="procurement-subcontract-mobile-card-meta"
                  >进度 {{ row.progressPercent || '0' }}% ·
                  {{ row.plannedEndDate || '计划日期待维护' }}</span
                >
              </button>
            </div>
          </div>

          <section class="subcontract-task-wbs-panel" aria-label="项目内 WBS 树与只读甘特展示">
            <div class="subcontract-task-wbs-head">
              <div>
                <strong>项目内 WBS 树与只读甘特展示</strong>
                <span>按 WBS 编码排序的平铺展示</span>
              </div>
              <a-tag color="blue">只读</a-tag>
            </div>
            <div v-if="wbsTimelineRows.length" class="subcontract-task-wbs-list">
              <div
                v-for="item in wbsTimelineRows"
                :key="item.row.id"
                class="subcontract-task-wbs-row"
              >
                <div class="subcontract-task-wbs-meta">
                  <strong>{{ item.row.taskCode || '-' }}</strong>
                  <span>{{ item.row.taskName || '-' }}</span>
                </div>
                <div class="subcontract-task-wbs-dates">
                  <span
                    >计划：{{ item.row.plannedStartDate || '未设置计划日期' }} ~
                    {{ item.row.plannedEndDate || '未设置计划日期' }}</span
                  >
                  <span
                    >实际：{{ item.row.actualStartDate || '-' }} ~
                    {{ item.row.actualEndDate || '-' }}</span
                  >
                  <span v-if="item.row.predecessorTaskId"
                    >前置：{{ item.row.predecessorTaskName || item.row.predecessorTaskId }}</span
                  >
                </div>
                <div class="subcontract-task-wbs-progress">
                  <a-progress
                    :percent="parseFloat(item.row.progressPercent || '0') || 0"
                    size="small"
                    :stroke-width="6"
                  />
                  <a-tag :color="STATUS_COLOR[item.row.status]">
                    {{ STATUS_LABEL[item.row.status] ?? item.row.status }}
                  </a-tag>
                  <a-tag v-if="item.isDelayed" color="error">已延期</a-tag>
                  <a-tag v-if="item.predecessorRisk === 'INCOMPLETE'" color="warning"
                    >前置未完成</a-tag
                  >
                  <a-tag v-if="item.predecessorRisk === 'LATE'" color="error">前置迟交</a-tag>
                </div>
                <div class="subcontract-task-gantt-track">
                  <span
                    v-if="item.hasPlan"
                    class="subcontract-task-gantt-bar"
                    :style="{ left: item.left + '%', width: item.width + '%' }"
                  ></span>
                  <em v-else>未设置计划日期</em>
                </div>
              </div>
            </div>
            <div v-else class="subcontract-task-wbs-empty">暂无任务可生成 WBS/甘特概览</div>
          </section>

          <!-- 分页 -->
          <div class="lg-pagination">
            <span class="lg-total">共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :page-size-options="['10', '20', '50', '100']"
              show-size-changer
              show-quick-jumper
              @change="handlePageChange"
              @show-size-change="handlePageSizeChange"
            />
          </div>
        </main>
      </div>

      <aside
        class="lg-analysis-rail subcontract-task-analysis-rail procurement-subcontract-analysis-rail"
        aria-label="分包任务辅助分析"
      >
        <div class="lg-analysis-panel subcontract-task-analysis-panel">
          <header class="lg-analysis-header">
            <div>
              <div class="lg-analysis-heading">辅助分析</div>
              <div class="lg-analysis-description">任务状态、计量进度与近期任务</div>
            </div>
          </header>
          <section class="subcontract-task-analysis-section">
            <div class="subcontract-task-section-head">
              <strong>任务状态分布</strong>
              <span>{{ tableData.length }} 条</span>
            </div>
            <div class="subcontract-task-bar-list">
              <div
                v-for="item in taskStatusSummary"
                :key="item.label"
                class="subcontract-task-bar-row"
              >
                <div class="subcontract-task-bar-meta">
                  <span><i :style="{ background: item.color }"></i>{{ item.label }}</span>
                  <strong>{{ item.count }} 条</strong>
                </div>
                <div class="subcontract-task-bar-track">
                  <span :style="{ width: item.pct + '%', background: item.color }"></span>
                </div>
              </div>
            </div>
          </section>
          <section class="subcontract-task-analysis-section">
            <div class="subcontract-task-section-head">
              <strong>进度概览</strong>
              <span>均值 {{ avgProgress }}%</span>
            </div>
            <div class="subcontract-task-progress-box">
              <a-progress :percent="avgProgress" :stroke-width="10" />
              <span>按当前页任务进度估算，用于快速识别滞后任务。</span>
            </div>
          </section>
          <section class="subcontract-task-analysis-section">
            <div class="subcontract-task-section-head">
              <strong>近期任务</strong>
              <span>最新 4 条</span>
            </div>
            <div class="subcontract-task-recent-list">
              <div v-for="item in recentTasks" :key="item.id" class="subcontract-task-recent-item">
                <span>{{ item.taskName || item.taskCode }}</span>
                <strong>{{ STATUS_LABEL[item.status] ?? item.status }}</strong>
              </div>
              <div v-if="!recentTasks.length" class="subcontract-task-empty">暂无任务</div>
            </div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="800"
      wrap-class-name="compact-subcontract-task-modal"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <div class="subcontract-task-modal-body">
        <a-form
          :label-col="{ span: 5 }"
          :wrapper-col="{ span: 18 }"
          class="subcontract-task-modal-form"
          size="small"
        >
          <a-form-item label="项目" required>
            <a-select
              v-model:value="formData.projectId"
              placeholder="请选择项目"
              show-search
              @change="
                (v: string) => {
                  formData.contractId = undefined
                  formData.partnerId = undefined
                  formData.predecessorTaskId = undefined
                  referenceStore.fetchContracts({ projectId: v })
                  loadPredecessorOptions(v)
                }
              "
              :filter-option="filterSelectOption"
            >
              <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="分包合同">
            <a-select
              v-model:value="formData.contractId"
              placeholder="请选择合同"
              allow-clear
              show-search
              :filter-option="filterSelectOption"
              @change="onContractChange"
            >
              <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
                {{ c.contractName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="分包商">
            <a-input :value="formPartnerName" disabled placeholder="选择合同后自动填充乙方" />
          </a-form-item>
          <a-form-item label="任务名称" required>
            <a-input v-model:value="formData.taskName" placeholder="请输入任务名称" />
          </a-form-item>
          <a-form-item label="施工区域">
            <a-input v-model:value="formData.workArea" placeholder="请输入施工区域" />
          </a-form-item>
          <a-form-item label="前置任务（FS）">
            <a-select
              v-model:value="formData.predecessorTaskId"
              placeholder="请选择同项目前置任务"
              allow-clear
              show-search
              :filter-option="filterSelectOption"
            >
              <a-select-option
                v-for="task in predecessorOptions"
                :key="task.id"
                :value="task.id"
                :label="`${task.taskCode || '-'} ${task.taskName}`"
              >
                {{ task.taskCode || '-' }} {{ task.taskName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="计划开始日期">
            <a-date-picker
              v-model:value="formData.plannedStartDate"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item label="计划结束日期">
            <a-date-picker
              v-model:value="formData.plannedEndDate"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item label="实际开始日期">
            <a-date-picker
              v-model:value="formData.actualStartDate"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item label="实际结束日期">
            <a-date-picker
              v-model:value="formData.actualEndDate"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item label="进度百分比">
            <a-input-number
              v-model:value="formData.progressPercent"
              :min="0"
              :max="100"
              :precision="2"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="formData.status" placeholder="请选择状态">
              <a-select-option value="NOT_STARTED">未开始</a-select-option>
              <a-select-option value="IN_PROGRESS" :disabled="predecessorStartBlocked"
                >进行中</a-select-option
              >
              <a-select-option value="COMPLETED" :disabled="predecessorStartBlocked"
                >已完成</a-select-option
              >
              <a-select-option value="SUSPENDED">已暂停</a-select-option>
            </a-select>
            <div v-if="predecessorStartBlocked" class="form-tip">
              前置任务未完成，当前任务不能开工或完成
            </div>
          </a-form-item>
          <a-form-item label="备注">
            <a-textarea v-model:value="formData.remark" :rows="1" placeholder="请输入备注" />
          </a-form-item>
        </a-form>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.subcontract-task-page {
  color: #0f172a;
}

.subcontract-task-page-head {
  margin-bottom: 14px;
}

.lg-breadcrumb {
  font-size: 14px;
}

.subcontract-task-search-bar {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 14px;
  min-height: 108px;
  margin-bottom: 0;
  padding: 16px;
}

.subcontract-task-filter-grid,
.subcontract-task-filter-foot {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.subcontract-task-filter-foot {
  grid-template-columns: minmax(0, 1fr) auto;
}

.subcontract-task-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.subcontract-task-main-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.subcontract-task-kpi-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  height: auto;
  min-height: 108px;
  margin-bottom: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.subcontract-task-kpi-item {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  min-height: 108px;
  padding: 16px 18px;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  border-right: 1px solid #edf1f5;
}

.subcontract-task-kpi-item:last-child {
  border-right: 0;
}

.subcontract-task-kpi-icon {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  font-size: 18px;
}

.subcontract-task-kpi-icon.is-blue {
  color: #2563eb;
  background: #eff6ff;
}

.subcontract-task-kpi-icon.is-cyan {
  color: #0891b2;
  background: #ecfeff;
}

.subcontract-task-kpi-icon.is-green {
  color: #16a34a;
  background: #f0fdf4;
}

.subcontract-task-kpi-icon.is-amber {
  color: #d97706;
  background: #fffbeb;
}

.subcontract-task-kpi-icon.is-red {
  color: #dc2626;
  background: #fef2f2;
}

.subcontract-task-kpi-label,
.subcontract-task-kpi-hint {
  display: block;
  font-size: 12px;
  color: #64748b;
  line-height: 18px;
}

.subcontract-task-kpi-item strong {
  display: block;
  margin: 1px 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 24px;
  font-weight: 700;
}

.subcontract-task-table-panel {
  min-height: 754px;
}

.subcontract-task-table-title {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  margin-right: 4px;
}

.subcontract-task-table-title strong {
  font-size: 15px;
  color: #0f172a;
}

.subcontract-task-table-title span {
  font-size: 12px;
  color: #64748b;
}

.subcontract-task-wbs-panel {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.subcontract-task-wbs-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.subcontract-task-wbs-head strong,
.subcontract-task-wbs-head span {
  display: block;
}

.subcontract-task-wbs-head strong {
  color: #0f172a;
}

.subcontract-task-wbs-head span,
.subcontract-task-wbs-dates,
.subcontract-task-gantt-track em,
.subcontract-task-wbs-empty {
  font-size: 12px;
  color: #64748b;
}

.subcontract-task-wbs-list {
  display: grid;
  gap: 10px;
}

.subcontract-task-wbs-row {
  display: grid;
  grid-template-columns: minmax(160px, 1.1fr) minmax(240px, 1.4fr) minmax(180px, 1fr);
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid #edf1f5;
  border-radius: 8px;
  background: #fff;
}

.subcontract-task-wbs-meta strong,
.subcontract-task-wbs-meta span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subcontract-task-wbs-meta strong {
  color: #2563eb;
  font-size: 12px;
}

.subcontract-task-wbs-meta span {
  color: #0f172a;
  font-weight: 600;
}

.subcontract-task-wbs-dates {
  display: grid;
  gap: 4px;
}

.subcontract-task-wbs-progress {
  display: grid;
  grid-template-columns: minmax(100px, 1fr) auto auto;
  gap: 8px;
  align-items: center;
}

.subcontract-task-gantt-track {
  grid-column: 1 / -1;
  position: relative;
  height: 18px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}

.subcontract-task-gantt-bar {
  position: absolute;
  top: 3px;
  bottom: 3px;
  min-width: 8px;
  border-radius: 999px;
  background: linear-gradient(90deg, #38bdf8, #2563eb);
}

.subcontract-task-gantt-track em {
  display: block;
  line-height: 18px;
  text-align: center;
  font-style: normal;
}

.subcontract-task-wbs-empty {
  padding: 18px 0 4px;
  text-align: center;
}

.subcontract-task-analysis-rail {
  width: var(--lg-rail-width, 240px);
}

.subcontract-task-analysis-panel {
  height: 100%;
  min-height: 100%;
  box-sizing: border-box;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.subcontract-task-analysis-section {
  padding: 14px 16px;
  border-bottom: 1px solid #edf1f5;
}

.subcontract-task-analysis-section:last-child {
  border-bottom: 0;
}

.subcontract-task-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.subcontract-task-section-head strong {
  font-size: 15px;
  color: #0f172a;
}

.subcontract-task-section-head span,
.subcontract-task-progress-box span {
  font-size: 12px;
  color: #64748b;
}

.subcontract-task-bar-list,
.subcontract-task-recent-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.subcontract-task-bar-meta,
.subcontract-task-recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.subcontract-task-bar-meta span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  color: #334155;
}

.subcontract-task-bar-meta i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.subcontract-task-bar-meta strong,
.subcontract-task-recent-item strong {
  color: #0f172a;
  font-weight: 600;
  white-space: nowrap;
}

.subcontract-task-bar-track {
  margin-top: 7px;
  height: 6px;
  border-radius: 999px;
  background: #f1f5f9;
  overflow: hidden;
}

.subcontract-task-bar-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.subcontract-task-progress-box {
  display: grid;
  gap: 8px;
}

.subcontract-task-recent-item {
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.subcontract-task-recent-item:last-child {
  border-bottom: 0;
}

.subcontract-task-recent-item span {
  min-width: 0;
  overflow: hidden;
  color: #334155;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subcontract-task-empty {
  padding: 18px 0;
  color: #94a3b8;
  text-align: center;
  font-size: 13px;
}

.subcontract-task-modal-body {
  max-height: calc(100vh - 220px);
  overflow: auto;
  padding-right: 4px;
}

.subcontract-task-modal-form :deep(.ant-form-item) {
  margin-bottom: 8px;
}

:global(.compact-subcontract-task-modal .ant-modal-body) {
  padding-top: 12px;
  padding-bottom: 12px;
}

:global(.compact-subcontract-task-modal .ant-modal-footer) {
  margin-top: 0;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

@media (max-width: 1280px) {
  .subcontract-task-search-bar,
  .subcontract-task-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .subcontract-task-analysis-rail {
    width: 100%;
  }
}
</style>
