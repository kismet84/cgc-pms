<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  getProjectList,
  getProjectDetail,
  createProject,
  updateProject,
  deleteProject,
} from '@/api/modules/project'
import {
  buildActionColumn,
  buildAmountColumn,
  buildStatusColumn,
  formatWanAmountWithUnit,
} from '@/composables/listTablePresets'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { useMobileViewport } from '@/composables/useMobileViewport'
import type { ProjectVO } from '@/types/project'
import type { PageResult } from '@/types/api'
import { fetchDictData, getDictLabelSync } from '@/utils/dict'
import ProjectAnalysisRail from './components/ProjectAnalysisRail.vue'
import ProjectQueryPanel from './components/ProjectQueryPanel.vue'
import ProjectTablePanel from './components/ProjectTablePanel.vue'

const filter = reactive({
  keyword: '',
  projectType: undefined as string | undefined,
  status: undefined as string | undefined,
})
const loading = ref(false)
const tableData = ref<ProjectVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const createVisible = ref(false)
const createLoading = ref(false)
const route = useRoute()
const router = useRouter()
const { isMobile } = useMobileViewport()
const PROJECT_LIST_SCROLL_KEY = 'project-list-scroll-position'
const createFormRef = ref()
const createForm = reactive({
  projectName: '',
  projectType: undefined as string | undefined,
  projectAddress: '',
  ownerUnit: '',
  supervisorUnit: '',
  designUnit: '',
  contractAmount: undefined as number | undefined,
  plannedStartDate: undefined as string | undefined,
  plannedEndDate: undefined as string | undefined,
})

function handleCreateModalOpen() {
  Object.assign(createForm, {
    projectName: '',
    projectType: undefined,
    projectAddress: '',
    ownerUnit: '',
    supervisorUnit: '',
    designUnit: '',
    contractAmount: undefined,
    plannedStartDate: undefined,
    plannedEndDate: undefined,
  })
  createVisible.value = true
}

function handleCreateModalClose() {
  createVisible.value = false
  createFormRef.value?.resetFields()
}

// ── Edit modal ──
const editVisible = ref(false)
const editLoading = ref(false)
const editingId = ref('')
const editFormRef = ref()
const editForm = reactive({
  projectName: '',
  projectType: undefined as string | undefined,
  projectAddress: '',
  ownerUnit: '',
  supervisorUnit: '',
  designUnit: '',
  contractAmount: undefined as number | undefined,
  plannedStartDate: undefined as string | undefined,
  plannedEndDate: undefined as string | undefined,
})

async function handleEditModalOpen(record: ProjectVO) {
  editingId.value = record.id
  editVisible.value = true
  try {
    const project: ProjectVO = await getProjectDetail(record.id)
    editForm.projectName = project.projectName
    editForm.projectType = project.projectType
    editForm.projectAddress = project.projectAddress || ''
    editForm.ownerUnit = project.ownerUnit || ''
    editForm.supervisorUnit = project.supervisorUnit || ''
    editForm.designUnit = project.designUnit || ''
    editForm.contractAmount = amountYuanToWan(project.contractAmount)
    editForm.plannedStartDate = project.plannedStartDate
    editForm.plannedEndDate = project.plannedEndDate
  } catch (e: unknown) {
    console.error(e)
    message.error('加载项目信息失败')
    editVisible.value = false
  }
}

function handleEditModalClose() {
  editVisible.value = false
  editFormRef.value?.resetFields()
}

async function handleEditSubmit() {
  try {
    await editFormRef.value?.validate()
  } catch {
    return
  }
  editLoading.value = true
  try {
    await updateProject(editingId.value, {
      projectName: editForm.projectName,
      projectType: editForm.projectType,
      projectAddress: editForm.projectAddress || undefined,
      ownerUnit: editForm.ownerUnit || undefined,
      supervisorUnit: editForm.supervisorUnit || undefined,
      designUnit: editForm.designUnit || undefined,
      contractAmount: amountWanToYuan(editForm.contractAmount),
      plannedStartDate: editForm.plannedStartDate,
      plannedEndDate: editForm.plannedEndDate,
    })
    message.success('项目更新成功')
    handleEditModalClose()
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('更新项目失败，请稍后重试')
  } finally {
    editLoading.value = false
  }
}

function handleDelete(record: ProjectVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除项目"${record.projectName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteProject(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        Modal.error({ title: '删除失败', content: '删除失败，请稍后重试' })
      }
    },
  })
}

async function handleCreateSubmit() {
  try {
    await createFormRef.value?.validate()
  } catch (e: unknown) {
    console.error(e)
    return
  }
  createLoading.value = true
  try {
    await createProject({
      projectName: createForm.projectName,
      projectType: createForm.projectType,
      projectAddress: createForm.projectAddress || undefined,
      ownerUnit: createForm.ownerUnit || undefined,
      supervisorUnit: createForm.supervisorUnit || undefined,
      designUnit: createForm.designUnit || undefined,
      contractAmount: amountWanToYuan(createForm.contractAmount),
      plannedStartDate: createForm.plannedStartDate,
      plannedEndDate: createForm.plannedEndDate,
    })
    message.success('项目创建成功')
    handleCreateModalClose()
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('创建项目失败，请稍后重试')
  } finally {
    createLoading.value = false
  }
}

async function fetchData() {
  loading.value = true
  syncQueryToRoute()
  try {
    const res: PageResult<ProjectVO> = await getProjectList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: filter.keyword || undefined,
      projectType: filter.projectType || undefined,
      status: filter.status || undefined,
    })
    tableData.value = res.records
    total.value = Number(res.total) || 0
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载项目列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}
function handleReset() {
  filter.keyword = ''
  filter.projectType = undefined
  filter.status = undefined
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

function readQueryString(key: string): string | undefined {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || undefined : value || undefined
}

function readQueryNumber(key: string, fallback: number): number {
  const value = Number(readQueryString(key))
  return Number.isFinite(value) && value > 0 ? value : fallback
}

function restoreFilterFromRoute() {
  filter.keyword = readQueryString('keyword') || ''
  filter.projectType = readQueryString('projectType')
  filter.status = readQueryString('status')
  pageNo.value = readQueryNumber('pageNo', 1)
  pageSize.value = readQueryNumber('pageSize', 20)
}

function syncQueryToRoute() {
  const query = {
    ...route.query,
    keyword: filter.keyword || undefined,
    projectType: filter.projectType || undefined,
    status: filter.status || undefined,
    pageNo: pageNo.value === 1 ? undefined : String(pageNo.value),
    pageSize: pageSize.value === 20 ? undefined : String(pageSize.value),
  }
  router.replace({ query })
}

onMounted(async () => {
  restoreFilterFromRoute()
  await fetchDictData(PROJECT_TYPE_DICT)
  await fetchData()
  await nextTick()
  const savedScroll = Number(sessionStorage.getItem(PROJECT_LIST_SCROLL_KEY))
  if (Number.isFinite(savedScroll) && savedScroll > 0) {
    window.scrollTo({ top: savedScroll })
  }
  sessionStorage.removeItem(PROJECT_LIST_SCROLL_KEY)
})

function openProjectOverview(record: ProjectVO) {
  sessionStorage.setItem(PROJECT_LIST_SCROLL_KEY, String(window.scrollY))
  router.push(`/project/${record.id}/overview`)
}

function amountYuanToWan(val?: string): number | undefined {
  const amount = Number(val)
  if (!Number.isFinite(amount)) return undefined
  return amount / 10000
}

function amountWanToYuan(val?: number): string | undefined {
  if (val == null || Number.isNaN(val)) return undefined
  return String(val * 10000)
}

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '前期',
  ACTIVE: '在建',
  ONGOING: '在建',
  COMPLETED: '已竣工',
  SUSPENDED: '已暂停',
  CLOSED: '已关闭',
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'processing',
  ACTIVE: 'success',
  ONGOING: 'success',
  COMPLETED: 'green',
  SUSPENDED: 'warning',
  CLOSED: 'error',
}

const APPROVAL_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  已批准: '已通过',
  审批中: '审批中',
  已拒绝: '已驳回',
}
const APPROVAL_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'processing',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  已批准: 'success',
  审批中: 'warning',
  已拒绝: 'error',
}

const PROJECT_TYPE_COLOR: Record<string, string> = {
  施工总承包: 'blue',
  专业分包: 'green',
  劳务分包: 'orange',
  材料采购: 'purple',
}

const PROJECT_TYPE_DICT = 'project_type'
const PROJECT_TYPE_LABEL: Record<string, string> = {
  施工总承包: '施工总承包',
  专业分包: '专业分包',
  劳务分包: '劳务分包',
  材料采购: '材料采购',
  BUILDING: '施工总承包',
  MAIN: '施工总承包',
  SUB: '专业分包',
  PURCHASE: '材料采购',
  MATERIAL: '材料采购',
  MATERIAL_PURCHASE: '材料采购',
  LABOR: '劳务分包',
  LABOR_SUB: '劳务分包',
  LABOR_SUBCONTRACT: '劳务分包',
  PROFESSIONAL_SUB: '专业分包',
  PROFESSIONAL_SUBCONTRACT: '专业分包',
  GENERAL: '施工总承包',
  GENERAL_CONTRACT: '施工总承包',
}

const PROJECT_TYPE_BASE_OPTIONS = Object.keys(PROJECT_TYPE_COLOR)
const PROJECT_STATUS_OPTIONS = ['DRAFT', 'ACTIVE', 'ONGOING', 'COMPLETED', 'SUSPENDED', 'CLOSED']

function projectTypeLabel(value: string | undefined) {
  const label = getDictLabelSync(PROJECT_TYPE_DICT, value ?? '', PROJECT_TYPE_LABEL)
  return label || value || '未分类'
}

function projectTypeColor(value: string | undefined) {
  return PROJECT_TYPE_COLOR[projectTypeLabel(value)] ?? 'default'
}

const projectTypeOptions = computed(() => {
  const options = new Set<string>(PROJECT_TYPE_BASE_OPTIONS)
  tableData.value.forEach((item) => {
    if (item.projectType) options.add(item.projectType)
  })
  if (filter.projectType) options.add(filter.projectType)
  if (createForm.projectType) options.add(createForm.projectType)
  if (editForm.projectType) options.add(editForm.projectType)
  return Array.from(options)
})

function calcCodeColumnWidth(values: Array<string | undefined>, title = '项目编号') {
  const longest = Math.max(title.length, ...values.map((value) => String(value ?? '').length))
  return Math.min(Math.max(longest * 9 + 42, 128), 240)
}

const projectStats = computed(() => {
  const rows = tableData.value
  return {
    total: total.value || rows.length,
    ongoing: rows.filter((item) => ['ACTIVE', 'ONGOING'].includes(item.status)).length,
    completed: rows.filter((item) => item.status === 'COMPLETED').length,
    draft: rows.filter((item) => item.status === 'DRAFT').length,
    risk: rows.filter((item) => ['SUSPENDED', 'CLOSED'].includes(item.status)).length,
  }
})

const totalContractAmount = computed(() =>
  tableData.value.reduce((sum, item) => sum + (parseFloat(item.contractAmount) || 0), 0),
)

const statusDistribution = computed(() => {
  const rows = tableData.value
  const counts = rows.reduce<Record<string, number>>((acc, item) => {
    acc[item.status] = (acc[item.status] || 0) + 1
    return acc
  }, {})
  return Object.entries(counts).map(([key, value]) => ({
    key,
    label: STATUS_LABEL[key] ?? key,
    value,
    percent: rows.length ? Math.round((value / rows.length) * 100) : 0,
  }))
})

const typeDistribution = computed(() => {
  const rows = tableData.value
  const counts = rows.reduce<Record<string, number>>((acc, item) => {
    const key = projectTypeLabel(item.projectType)
    acc[key] = (acc[key] || 0) + 1
    return acc
  }, {})
  return Object.entries(counts).map(([label, value]) => ({
    label,
    value,
    percent: rows.length ? Math.round((value / rows.length) * 100) : 0,
  }))
})

const riskProjects = computed(() => {
  const rows = tableData.value
    .filter((item) => ['SUSPENDED', 'CLOSED'].includes(item.status))
    .slice(0, 3)
    .map((item) => ({
      name: item.projectName,
      status: STATUS_LABEL[item.status] ?? item.status,
    }))
  return rows.length
    ? rows
    : [
        { name: '暂无高风险项目', status: '平稳' },
        { name: '工期与成本持续跟踪', status: '关注' },
      ]
})

const recentProjects = computed(() =>
  (tableData.value.length ? tableData.value.slice(0, 4) : [])
    .map((item) => ({
      name: item.projectName,
      status: STATUS_LABEL[item.status] ?? item.status,
    }))
    .concat(tableData.value.length ? [] : [{ name: '等待项目数据加载', status: '空状态' }]),
)

// ---- VxeGrid columns ----
const gridColumns = computed(() => [
  {
    field: 'projectCode',
    title: '项目编号',
    width: calcCodeColumnWidth(tableData.value.map((item) => item.projectCode)),
    minWidth: 128,
    showOverflow: false,
    slots: { default: 'projectCode' },
  },
  {
    field: 'projectName',
    title: '项目名称',
    minWidth: 200,
    showOverflow: 'tooltip',
    slots: { default: 'projectName' },
  },
  {
    field: 'projectType',
    title: '项目类型',
    width: 108,
    showOverflow: 'tooltip',
    slots: { default: 'projectType' },
  },
  buildAmountColumn('contractAmount', '合同金额', 'contractAmount', {
    showOverflow: false,
  }),
  {
    field: 'plannedStartDate',
    key: 'plannedDuration',
    title: '计划工期',
    width: 148,
    showOverflow: 'tooltip',
    slots: { default: 'plannedDuration' },
  },
  buildStatusColumn('status', '状态', 'status', {
    showOverflow: 'tooltip',
  }),
  buildStatusColumn('approvalStatus', '审批状态', 'approvalStatus', {
    showOverflow: 'tooltip',
  }),
  buildActionColumn('ops', { key: 'ops' }),
])
const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('project_list_cols_v3', gridColumns, {
  approvalStatus: false,
})
</script>

<template>
  <div
    class="project-list-page lg-list-page lg-page app-page"
    :class="{ 'project-list-page--mobile': isMobile }"
  >
    <div v-if="!isMobile" class="lg-page-head project-page-head">
      <div class="project-page-meta-row">
        <a-breadcrumb class="project-breadcrumb">
          <a-breadcrumb-item>项目管理</a-breadcrumb-item>
          <a-breadcrumb-item>项目列表</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- Create Modal -->
    <a-modal
      v-model:open="createVisible"
      title="新建项目"
      :width="800"
      :confirm-loading="createLoading"
      :mask-closable="false"
      ok-text="创建"
      cancel-text="取消"
      @ok="handleCreateSubmit"
      @cancel="handleCreateModalClose"
    >
      <a-form
        ref="createFormRef"
        :model="createForm"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
        class="pj-create-form"
      >
        <a-form-item
          label="项目名称"
          name="projectName"
          :rules="[{ required: true, message: '请输入项目名称' }]"
        >
          <a-input v-model:value="createForm.projectName" placeholder="请输入项目名称" />
        </a-form-item>
        <a-form-item
          label="项目类型"
          name="projectType"
          :rules="[{ required: true, message: '请选择项目类型' }]"
        >
          <a-select v-model:value="createForm.projectType" placeholder="请选择项目类型">
            <a-select-option v-for="item in projectTypeOptions" :key="item" :value="item">
              {{ projectTypeLabel(item) }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="项目地址" name="projectAddress">
          <a-input v-model:value="createForm.projectAddress" placeholder="请输入项目地址" />
        </a-form-item>
        <a-form-item label="建设单位" name="ownerUnit">
          <a-input v-model:value="createForm.ownerUnit" placeholder="请输入建设单位" />
        </a-form-item>
        <a-form-item label="监理单位" name="supervisorUnit">
          <a-input v-model:value="createForm.supervisorUnit" placeholder="请输入监理单位" />
        </a-form-item>
        <a-form-item label="设计单位" name="designUnit">
          <a-input v-model:value="createForm.designUnit" placeholder="请输入设计单位" />
        </a-form-item>
        <a-form-item label="合同金额(万元)" name="contractAmount">
          <a-input-number
            v-model:value="createForm.contractAmount"
            :min="0"
            :precision="2"
            placeholder="请输入合同金额"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="计划开工日期" name="plannedStartDate">
          <a-date-picker
            v-model:value="createForm.plannedStartDate"
            placeholder="请选择计划开工日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </a-form-item>
        <a-form-item label="计划竣工日期" name="plannedEndDate">
          <a-date-picker
            v-model:value="createForm.plannedEndDate"
            placeholder="请选择计划竣工日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Edit Modal -->
    <a-modal
      v-model:open="editVisible"
      title="编辑项目"
      :width="800"
      :confirm-loading="editLoading"
      :mask-closable="false"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleEditSubmit"
      @cancel="handleEditModalClose"
    >
      <a-form
        ref="editFormRef"
        :model="editForm"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
        class="pj-create-form"
      >
        <a-form-item
          label="项目名称"
          name="projectName"
          :rules="[{ required: true, message: '请输入项目名称' }]"
        >
          <a-input v-model:value="editForm.projectName" placeholder="请输入项目名称" />
        </a-form-item>
        <a-form-item
          label="项目类型"
          name="projectType"
          :rules="[{ required: true, message: '请选择项目类型' }]"
        >
          <a-select v-model:value="editForm.projectType" placeholder="请选择项目类型">
            <a-select-option v-for="item in projectTypeOptions" :key="item" :value="item">
              {{ projectTypeLabel(item) }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="项目地址" name="projectAddress">
          <a-input v-model:value="editForm.projectAddress" placeholder="请输入项目地址" />
        </a-form-item>
        <a-form-item label="建设单位" name="ownerUnit">
          <a-input v-model:value="editForm.ownerUnit" placeholder="请输入建设单位" />
        </a-form-item>
        <a-form-item label="监理单位" name="supervisorUnit">
          <a-input v-model:value="editForm.supervisorUnit" placeholder="请输入监理单位" />
        </a-form-item>
        <a-form-item label="设计单位" name="designUnit">
          <a-input v-model:value="editForm.designUnit" placeholder="请输入设计单位" />
        </a-form-item>
        <a-form-item label="合同金额(万元)" name="contractAmount">
          <a-input-number
            v-model:value="editForm.contractAmount"
            :min="0"
            :precision="2"
            placeholder="请输入合同金额"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="计划开工日期" name="plannedStartDate">
          <a-date-picker
            v-model:value="editForm.plannedStartDate"
            placeholder="请选择计划开工日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </a-form-item>
        <a-form-item label="计划竣工日期" name="plannedEndDate">
          <a-date-picker
            v-model:value="editForm.plannedEndDate"
            placeholder="请选择计划竣工日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <div class="lg-grid project-workspace">
      <div class="lg-left project-main-column">
        <ProjectQueryPanel
          :filter="filter"
          :project-type-options="projectTypeOptions"
          :project-type-label="projectTypeLabel"
          :project-status-options="PROJECT_STATUS_OPTIONS"
          :status-label="STATUS_LABEL"
          @search="handleSearch"
          @reset="handleReset"
        />

        <ProjectTablePanel
          :total="total"
          :loading="loading"
          :table-data="tableData"
          :is-mobile="isMobile"
          :page-no="pageNo"
          :page-size="pageSize"
          :visible-grid-columns="visibleGridColumns"
          :column-settings="columnSettings"
          :col-visible="colVisible"
          :status-label="STATUS_LABEL"
          :status-color="STATUS_COLOR"
          :approval-status-label="APPROVAL_STATUS_LABEL"
          :approval-status-color="APPROVAL_STATUS_COLOR"
          :project-type-label="projectTypeLabel"
          :project-type-color="projectTypeColor"
          :fmt-amount="formatWanAmountWithUnit"
          @toggle-col="toggleCol"
          @refresh="fetchData"
          @create="handleCreateModalOpen"
          @overview="openProjectOverview"
          @edit="handleEditModalOpen"
          @delete="handleDelete"
          @page-change="handlePageChange"
          @page-size-change="handlePageSizeChange"
        />
      </div>

      <ProjectAnalysisRail
        v-if="!isMobile"
        :project-stats="projectStats"
        :total-contract-amount="totalContractAmount"
        :type-distribution="typeDistribution"
        :status-distribution="statusDistribution"
        :risk-projects="riskProjects"
        :recent-projects="recentProjects"
      />
    </div>
  </div>
</template>

<style scoped>
.project-list-page {
  --lg-search-min-height: 60px;

  background: var(--surface-subtle);
}

.project-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.project-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.project-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.project-query-panel {
  align-items: stretch;
  flex-wrap: wrap;
  width: 100%;
  margin: 0;
}

.project-filter-grid {
  display: flex;
  flex-wrap: wrap;
  flex: 1 0 100%;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.project-filter-item {
  flex: 1 1 180px;
  min-width: 0;
}

.project-filter-item label {
  display: none;
}

.project-filter-item :deep(.ant-select),
.project-filter-item :deep(.ant-input-affix-wrapper) {
  width: 100%;
}

.project-search-select {
  width: 100%;
}

.project-search-icon {
  color: var(--text-secondary);
}

.project-filter-foot {
  display: flex;
  flex: 1 0 100%;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 0;
  width: 100%;
  min-width: 0;
}

.project-filter-item-keyword {
  flex: 1 1 320px;
}

.project-search-input {
  min-width: 0;
}

.project-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.project-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 20vw;
  grid-template-rows: auto minmax(0, 1fr);
  align-items: stretch;
  gap: clamp(8px, 1.2vw, 12px);
}

.project-main-column {
  display: contents;
}

.project-main-column > :deep(.project-query-panel) {
  grid-column: 1 / -1;
  grid-row: 1;
  min-width: 0;
}

.project-main-column > :deep(.project-table-panel) {
  grid-column: 1;
  grid-row: 2;
  min-width: 0;
}

.project-workspace > :deep(.project-analysis-rail) {
  grid-column: 2;
  grid-row: 2;
  width: auto;
  min-width: 0;
  margin-top: 0;
}

.pj-create-form {
  padding-top: 16px;
}

@media (width < 500px) {
  .project-list-page {
    --lg-search-min-height: 0;

    gap: 8px;
    padding: 0;
    background: var(--surface-subtle);
  }

  .project-workspace {
    display: block;
  }

  .project-main-column {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-width: 0;
  }
}
</style>
