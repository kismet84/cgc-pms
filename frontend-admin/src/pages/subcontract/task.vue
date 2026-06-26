<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileDoneOutlined,
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
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  status: undefined as string | undefined,
  taskCode: '',
  taskName: '',
  keyword: '',
})

const loading = ref(false)
const tableData = ref<SubTaskVO[]>([])
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

const STATUS_LABEL: Record<string, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  SUSPENDED: '已暂停',
}
const STATUS_COLOR: Record<string, string> = {
  NOT_STARTED: 'default',
  IN_PROGRESS: 'processing',
  COMPLETED: 'success',
  SUSPENDED: 'warning',
}

// ---- vxe-grid columns ----
const gridColumns = computed(() => [
  { field: 'taskCode', title: '任务编号', minWidth: 140, ellipsis: true },
  {
    field: 'taskName',
    title: '任务名称',
    minWidth: 140,
    slots: { default: 'taskName' },
    ellipsis: true,
  },
  { field: 'projectName', title: '项目名称', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同名称', minWidth: 150, ellipsis: true },
  { field: 'partnerName', title: '分包商', minWidth: 140, ellipsis: true },
  { field: 'workArea', title: '施工区域', minWidth: 120, ellipsis: true },
  { field: 'progressPercent', title: '进度', width: 90, slots: { default: 'progressPercent' } },
  { field: 'status', title: '状态', width: 88, slots: { default: 'status' } },
  { field: 'plannedStartDate', title: '计划开始', width: 112 },
  { field: 'plannedEndDate', title: '计划结束', width: 112 },
  { title: '操作', width: 76, slots: { default: 'action' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('subcontract_task_cols', gridColumns)

async function fetchData() {
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
    message.error('加载分包任务列表失败，请稍后重试')
  } finally {
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
  modalVisible.value = true
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
    message.error('操作失败，请稍后重试')
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

function statusPct(count: number) {
  const base = tableData.value.length || 0
  if (!base) return 0
  return Math.round((count / base) * 100)
}

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'SUB' })
  referenceStore.fetchPartners({ partnerType: 'SUB' })
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page subcontract-task-page">
    <div class="lg-page-head subcontract-task-page-head">
      <div class="subcontract-task-title-block">
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>分包管理</a-breadcrumb-item>
          <a-breadcrumb-item>分包任务</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="subcontract-task-title-row">
          <h1>分包任务</h1>
          <span>跟踪分包任务进度、施工区域、计划日期与状态风险。</span>
        </div>
      </div>
    </div>

    <div class="lg-search-bar subcontract-task-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索任务编号、名称…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
      </a-input>
      <a-select
        v-model:value="filter.projectId"
        placeholder="全部项目"
        allow-clear
        size="large"
        show-search
        :filter-option="
          (input: string, option: SelectOption) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
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
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <div class="subcontract-task-main-column">
        <div class="subcontract-task-kpi-summary" aria-label="分包任务关键指标">
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

        <main class="lg-list-table-panel subcontract-task-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <div class="subcontract-task-table-title">
                <strong>任务明细</strong>
                <span>共 {{ total }} 条</span>
              </div>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建任务
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
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
          </div>

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

      <aside class="lg-analysis-rail subcontract-task-analysis-rail" aria-label="分包任务辅助分析">
        <div class="subcontract-task-analysis-panel">
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
                  referenceStore.fetchContracts({ projectId: v })
                }
              "
              :filter-option="
                (input: string, option: any) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
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
              :filter-option="
                (input: string, option: any) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
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
              <a-select-option value="IN_PROGRESS">进行中</a-select-option>
              <a-select-option value="COMPLETED">已完成</a-select-option>
              <a-select-option value="SUSPENDED">已暂停</a-select-option>
            </a-select>
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
  margin-bottom: 7px;
}

.lg-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}

.subcontract-task-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.subcontract-task-title-row h1 {
  margin: 0;
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  color: #0f172a;
}

.subcontract-task-title-row span {
  font-size: 13px;
  color: #64748b;
}

.subcontract-task-search-bar {
  min-height: 74px;
  display: grid;
  grid-template-columns: minmax(260px, 1.7fr) minmax(180px, 1fr) 160px auto auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
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
  height: 88px;
  min-height: 88px;
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
  padding: 12px 18px;
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

.subcontract-task-analysis-rail {
  width: 336px;
}

.subcontract-task-analysis-panel {
  height: 856px;
  min-height: 856px;
  box-sizing: border-box;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.subcontract-task-analysis-section {
  padding: 18px;
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
