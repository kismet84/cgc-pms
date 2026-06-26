<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  DollarOutlined,
  FileTextOutlined,
  FlagOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import {
  getProjectList,
  getProjectDetail,
  createProject,
  updateProject,
  deleteProject,
} from '@/api/modules/project'
import { ColumnSettingsButton } from '@/components/list-page'
import type { ProjectVO } from '@/types/project'
import type { PageResult } from '@/types/api'

const filter = reactive({
  keyword: '',
})

const loading = ref(false)
const tableData = ref<ProjectVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const COLS_KEY = 'project_list_cols_v3'
const defaultCols: Record<string, boolean> = {
  projectCode: true,
  projectName: true,
  projectType: true,
  contractAmount: true,
  plannedDuration: true,
  status: true,
  approvalStatus: false,
  ops: true,
}
const colLabels: Record<string, string> = {
  projectCode: '项目编号',
  projectName: '项目名称',
  projectType: '项目类型',
  contractAmount: '合同金额',
  plannedDuration: '计划工期',
  status: '状态',
  approvalStatus: '审批状态',
  ops: '操作',
}
let savedCols: Record<string, boolean> = defaultCols
try {
  const raw = localStorage.getItem(COLS_KEY)
  if (raw) savedCols = JSON.parse(raw)
} catch (e: unknown) {
  console.error(e)
  localStorage.removeItem(COLS_KEY)
}
const colVisible = reactive<Record<string, boolean>>({ ...defaultCols, ...savedCols })
function toggleCol(key: string) {
  colVisible[key] = !colVisible[key]
  localStorage.setItem(COLS_KEY, JSON.stringify(colVisible))
}

const columnSettings = computed(() =>
  Object.keys(defaultCols).map((key) => ({
    key,
    label: colLabels[key],
    required: key === 'projectCode',
  })),
)

const createVisible = ref(false)
const createLoading = ref(false)
const router = useRouter()
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
    editForm.contractAmount = project.contractAmount
      ? parseFloat(project.contractAmount) / 10000
      : undefined
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
      contractAmount:
        editForm.contractAmount != null ? String(editForm.contractAmount * 10000) : undefined,
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
      contractAmount:
        createForm.contractAmount != null ? String(createForm.contractAmount) : undefined,
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
  try {
    const res: PageResult<ProjectVO> = await getProjectList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      keyword: filter.keyword || undefined,
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

onMounted(fetchData)

const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

function fmtAmount(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '-'
  return (
    (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) +
    ' 万元'
  )
}

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '前期',
  ONGOING: '在建',
  COMPLETED: '已竣工',
  SUSPENDED: '已暂停',
  CLOSED: '已关闭',
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'processing',
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

function calcCodeColumnWidth(values: Array<string | undefined>, title = '项目编号') {
  const longest = Math.max(title.length, ...values.map((value) => String(value ?? '').length))
  return Math.min(Math.max(longest * 9 + 42, 128), 240)
}

const projectStats = computed(() => {
  const rows = tableData.value
  return {
    total: total.value || rows.length,
    ongoing: rows.filter((item) => item.status === 'ONGOING').length,
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
    const key = item.projectType || '未分类'
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
  ...(colVisible.projectCode
    ? [
        {
          field: 'projectCode',
          title: '项目编号',
          width: calcCodeColumnWidth(tableData.value.map((item) => item.projectCode)),
          minWidth: 128,
          showOverflow: false,
          slots: { default: 'projectCode' },
        },
      ]
    : []),
  ...(colVisible.projectName
    ? [
        {
          field: 'projectName',
          title: '项目名称',
          minWidth: 200,
          showOverflow: 'tooltip',
          slots: { default: 'projectName' },
        },
      ]
    : []),
  ...(colVisible.projectType
    ? [
        {
          field: 'projectType',
          title: '项目类型',
          width: 108,
          showOverflow: 'tooltip',
          slots: { default: 'projectType' },
        },
      ]
    : []),
  ...(colVisible.contractAmount
    ? [
        {
          field: 'contractAmount',
          title: '合同金额',
          width: 140,
          minWidth: 140,
          align: 'right' as const,
          showOverflow: false,
          slots: { default: 'contractAmount' },
        },
      ]
    : []),
  ...(colVisible.plannedDuration
    ? [
        {
          field: 'plannedStartDate',
          title: '计划工期',
          width: 148,
          showOverflow: 'tooltip',
          slots: { default: 'plannedDuration' },
        },
      ]
    : []),
  ...(colVisible.status
    ? [
        {
          field: 'status',
          title: '状态',
          width: 88,
          showOverflow: 'tooltip',
          slots: { default: 'status' },
        },
      ]
    : []),
  ...(colVisible.approvalStatus
    ? [
        {
          field: 'approvalStatus',
          title: '审批状态',
          width: 108,
          showOverflow: 'tooltip',
          slots: { default: 'approvalStatus' },
        },
      ]
    : []),
  ...(colVisible.ops ? [{ title: '操作', width: 76, slots: { default: 'ops' } }] : []),
])
</script>

<template>
  <div class="project-list-page lg-list-page lg-page app-page">
    <div class="lg-page-head project-page-head">
      <div class="project-page-meta-row">
        <a-breadcrumb class="project-breadcrumb">
          <a-breadcrumb-item>项目管理</a-breadcrumb-item>
          <a-breadcrumb-item>项目列表</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="project-page-subtitle">统一查看项目基础信息、状态、工期与合同金额</span>
      </div>
    </div>

    <section class="lg-search-bar project-query-panel" aria-label="项目查询条件">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索项目编号、名称、类型、建设单位"
        allow-clear
        size="large"
        class="project-search-input"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined class="project-search-icon" /></template>
      </a-input>
      <div class="project-search-actions">
        <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </section>

    <!-- Create Modal -->
    <a-modal
      v-model:open="createVisible"
      title="新建项目"
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
            <a-select-option value="施工总承包">施工总承包</a-select-option>
            <a-select-option value="专业分包">专业分包</a-select-option>
            <a-select-option value="劳务分包">劳务分包</a-select-option>
            <a-select-option value="材料采购">材料采购</a-select-option>
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
            <a-select-option value="施工总承包">施工总承包</a-select-option>
            <a-select-option value="专业分包">专业分包</a-select-option>
            <a-select-option value="劳务分包">劳务分包</a-select-option>
            <a-select-option value="材料采购">材料采购</a-select-option>
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
        <div class="project-kpi-summary" aria-label="项目关键指标">
          <div class="project-kpi-item">
            <span class="project-kpi-icon is-total"><FileTextOutlined /></span>
            <span class="project-kpi-label">项目总数</span>
            <span class="project-kpi-value">{{ projectStats.total || 0 }} <small>个</small></span>
          </div>
          <div class="project-kpi-item">
            <span class="project-kpi-icon is-amount"><DollarOutlined /></span>
            <span class="project-kpi-label">合同总金额</span>
            <span class="project-kpi-value">
              {{
                totalContractAmount
                  ? (totalContractAmount / 10000).toLocaleString('zh-CN', {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    })
                  : '0.00'
              }}
              <small>万元</small>
            </span>
          </div>
          <div class="project-kpi-item">
            <span class="project-kpi-icon is-ongoing"><SafetyCertificateOutlined /></span>
            <span class="project-kpi-label">在建项目</span>
            <span class="project-kpi-value">{{ projectStats.ongoing || 0 }} <small>个</small></span>
          </div>
          <div class="project-kpi-item">
            <span class="project-kpi-icon is-completed"><FlagOutlined /></span>
            <span class="project-kpi-label">已竣工项目</span>
            <span class="project-kpi-value"
              >{{ projectStats.completed || 0 }} <small>个</small></span
            >
          </div>
          <div class="project-kpi-item is-warn">
            <span class="project-kpi-icon is-risk"><WarningOutlined /></span>
            <span class="project-kpi-label">风险项目</span>
            <span class="project-kpi-value">{{ projectStats.risk || 0 }} <small>个</small></span>
          </div>
        </div>

        <main class="lg-list-table-panel project-table-panel">
          <div class="lg-toolbar project-table-toolbar">
            <div class="lg-toolbar-left">
              <span class="project-table-title">项目列表</span>
              <span class="project-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button aria-label="刷新项目列表" title="刷新" @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
              <a-button type="primary" @click="handleCreateModalOpen">
                <template #icon><PlusOutlined /></template>
                新建项目
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="project-toolbar-hint">固定表头 / 金额右对齐 / 编号可查看总览</span>
            </div>
          </div>

          <div class="lg-table-wrap project-table-wrap">
            <vxe-grid
              :data="tableData"
              :columns="gridColumns"
              :loading="loading"
              :column-config="{ resizable: true, useKey: true }"
              show-overflow="title"
              show-header-overflow="title"
              stripe
              border="inner"
              size="small"
            >
              <template #projectCode="{ row }">
                <a-button
                  class="project-code-link"
                  type="link"
                  @click="router.push(`/project/${row.id}/overview`)"
                >
                  {{ row.projectCode }}
                </a-button>
              </template>
              <template #projectName="{ row }">
                <span class="project-name-text">{{ row.projectName }}</span>
              </template>
              <template #projectType="{ row }">
                <a-tag :color="PROJECT_TYPE_COLOR[row.projectType]" size="small">
                  {{ row.projectType || '未分类' }}
                </a-tag>
              </template>
              <template #contractAmount="{ row }">
                <span class="lg-money project-contract-amount">{{
                  fmtAmount(row.contractAmount)
                }}</span>
              </template>
              <template #plannedDuration="{ row }">
                <span>{{
                  row.plannedStartDate || row.plannedEndDate
                    ? `${row.plannedStartDate || '-'} ~ ${row.plannedEndDate || '-'}`
                    : '-'
                }}</span>
              </template>
              <template #status="{ row }">
                <a-tag :color="STATUS_COLOR[row.status]" size="small">
                  {{ STATUS_LABEL[row.status] ?? row.status }}
                </a-tag>
              </template>
              <template #approvalStatus="{ row }">
                <a-tag
                  v-if="row.approvalStatus"
                  :color="APPROVAL_STATUS_COLOR[row.approvalStatus]"
                  size="small"
                >
                  {{ APPROVAL_STATUS_LABEL[row.approvalStatus] ?? row.approvalStatus }}
                </a-tag>
                <span v-else class="project-empty-text">-</span>
              </template>
              <template #ops="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="router.push(`/project/${row.id}/overview`)">
                        查看
                      </a-menu-item>
                      <a-menu-item @click="handleEditModalOpen(row)">编辑</a-menu-item>
                      <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
            </vxe-grid>
          </div>

          <div class="lg-pagination project-pagination">
            <span class="lg-total">共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :page-size-options="['10', '20', '50', '100']"
              show-size-changer
              @change="handlePageChange"
              @show-size-change="handlePageSizeChange"
            />
          </div>
        </main>
      </div>

      <aside class="lg-analysis-rail project-analysis-rail" aria-label="项目辅助分析">
        <div class="project-analysis-panel">
          <header class="project-analysis-head">
            <div>
              <div class="project-analysis-title">项目分析</div>
              <div class="project-analysis-subtitle">类型、状态与近期记录</div>
            </div>
          </header>

          <section class="project-analysis-section">
            <div class="project-section-title">项目类型分布</div>
            <div v-for="item in typeDistribution" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot project-dot-primary"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-num">{{ item.value }}</span>
              <span class="lg-type-pct">{{ item.percent }}%</span>
            </div>
            <div v-if="!typeDistribution.length" class="lg-warning-empty">暂无项目类型</div>
          </section>

          <section class="project-analysis-section">
            <div class="project-section-title">项目状态</div>
            <div v-for="item in statusDistribution" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot project-dot-success"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-num">{{ item.value }}</span>
              <span class="lg-type-pct">{{ item.percent }}%</span>
            </div>
            <div v-if="!statusDistribution.length" class="lg-warning-empty">暂无项目状态</div>
          </section>

          <section class="project-analysis-section">
            <div class="project-warning-head">
              <div class="project-section-title">风险项目</div>
              <span class="project-warning-count">{{ projectStats.risk }} 项</span>
            </div>
            <div v-for="item in riskProjects" :key="item.name" class="lg-type-row">
              <span class="lg-type-dot project-dot-warning"></span>
              <span class="lg-type-label">{{ item.name }}</span>
              <span class="project-risk-status">{{ item.status }}</span>
            </div>
          </section>

          <section class="project-analysis-section">
            <div class="project-warning-head">
              <div class="project-section-title">近期项目</div>
              <span class="project-warning-count">{{ recentProjects.length }} 项</span>
            </div>
            <div v-for="item in recentProjects" :key="item.name" class="lg-type-row">
              <span class="lg-type-dot project-dot-primary"></span>
              <span class="lg-type-label">{{ item.name }}</span>
              <span class="project-risk-status">{{ item.status }}</span>
            </div>
          </section>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.project-list-page {
  gap: 14px;
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

.project-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.project-query-panel {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 74px;
}

.project-search-input {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 640px;
}

.project-search-icon {
  color: var(--text-secondary);
}

.project-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.project-workspace {
  align-items: stretch;
  min-height: 0;
}

.project-main-column {
  gap: 12px;
}

.project-kpi-summary {
  display: grid;
  grid-template-columns: 0.95fr 1.2fr 0.95fr 0.95fr 0.95fr;
  gap: 0;
  overflow: hidden;
  min-height: 84px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.project-kpi-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.project-kpi-item:last-child {
  border-right: 0;
}

.project-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.project-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.project-kpi-icon.is-ongoing,
.project-kpi-icon.is-completed {
  color: var(--success);
  background: var(--success-soft);
}

.project-kpi-icon.is-risk {
  color: var(--error);
  background: var(--error-soft);
}

.project-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 22px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.project-table-panel {
  overflow: hidden;
  border: 1px solid var(--border-subtle);
}

.project-table-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}

.project-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.project-table-count,
.project-toolbar-hint,
.project-analysis-subtitle,
.project-warning-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.project-table-wrap {
  min-height: 520px;
}

.project-table-wrap :deep(.vxe-header--column .vxe-cell) {
  justify-content: center;
  text-align: center;
}

.project-code-link,
.project-name-text,
.project-contract-amount {
  font-size: 14px;
  line-height: 22px;
}

.project-code-link {
  height: auto;
  padding: 0;
  font-weight: 700;
}

.project-code-link,
.project-code-link:hover,
.project-code-link:focus {
  background: transparent;
}

.project-pagination {
  border-top: 1px solid var(--border-subtle);
}

.project-analysis-rail {
  width: 336px;
}

.project-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.project-analysis-head,
.project-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.project-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.project-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.project-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.project-analysis-section :deep(.lg-type-row),
.project-analysis-section .lg-type-row {
  grid-template-columns: 9px minmax(60px, 1fr) 28px 38px;
}

.project-dot-primary {
  background: var(--primary);
}

.project-dot-success {
  background: var(--success);
}

.project-dot-warning {
  background: var(--warning);
}

.project-risk-status {
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.pj-create-form {
  padding-top: 16px;
}

@media (max-width: 1200px) {
  .project-page-head,
  .project-query-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .project-search-input,
  .project-analysis-rail {
    width: 100%;
    max-width: none;
  }

  .project-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
