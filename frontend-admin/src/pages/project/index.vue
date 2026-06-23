<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  DollarOutlined,
  FileTextOutlined,
  FlagOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import VChart from 'vue-echarts'
import {
  getProjectList,
  getProjectDetail,
  createProject,
  updateProject,
  deleteProject,
} from '@/api/modules/project'
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

const COLS_KEY = 'project_list_cols'
const defaultCols: Record<string, boolean> = {
  projectCode: true,
  projectName: true,
  projectType: true,
  contractAmount: true,
  plannedDuration: true,
  status: true,
  approvalStatus: true,
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

function getStatusTagClass(status: string) {
  return {
    'lg-tag--success': status === 'ONGOING' || status === 'COMPLETED',
    'lg-tag--warning': status === 'SUSPENDED',
    'lg-tag--danger': status === 'CLOSED',
  }
}

function getApprovalTagClass(status: string) {
  return {
    'lg-tag--success': status === '已批准',
    'lg-tag--warning': status === '审批中',
    'lg-tag--danger': status === '已拒绝',
  }
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
  const fallback = [
    { name: '在建', value: 12 },
    { name: '已完工', value: 6 },
    { name: '前期', value: 4 },
    { name: '暂停', value: 2 },
  ]
  const data = Object.entries(counts).map(([key, value]) => ({
    name: STATUS_LABEL[key] ?? key,
    value,
  }))
  return data.length ? data : fallback
})

const statusOption = computed(() => ({
  color: ['#1890ff', '#52c41a', '#faad14', '#ff4d4f'],
  title: {
    text: String(projectStats.value.total),
    subtext: '项目总数',
    left: 'center',
    top: 'center',
    textStyle: {
      color: '#333',
      fontSize: 18,
      fontWeight: 600,
    },
    subtextStyle: {
      color: '#8c8c8c',
      fontSize: 12,
    },
    itemGap: 2,
  },
  tooltip: { trigger: 'item' },
  legend: { show: false },
  series: [
    {
      type: 'pie',
      radius: ['52%', '76%'],
      center: ['50%', '50%'],
      label: { show: false },
      data: statusDistribution.value,
    },
  ],
}))

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
  (tableData.value.length ? tableData.value.slice(0, 3) : [])
    .map((item) => ({
      name: item.projectName,
      status: STATUS_LABEL[item.status] ?? item.status,
    }))
    .concat(tableData.value.length ? [] : [{ name: '等待项目数据加载', status: '空状态' }]),
)

function kpiPct(val: number, max: number): number {
  if (!max || max <= 0) return 0
  return Math.round((val / max) * 100)
}

const kpiMax = computed(() => ({
  total: total.value || 1,
  amount: tableData.value.reduce((m, r) => Math.max(m, parseFloat(r.contractAmount) || 0), 0) || 1,
}))

// ---- VxeGrid columns ----
const gridColumns = computed(() => [
  ...(colVisible.projectCode
    ? [{ field: 'projectCode', title: '项目编号', width: 138, ellipsis: true }]
    : []),
  ...(colVisible.projectName
    ? [
        {
          field: 'projectName',
          title: '项目名称',
          minWidth: 140,
          slots: { default: 'projectName' },
        },
      ]
    : []),
  ...(colVisible.projectType
    ? [{ field: 'projectType', title: '项目类型', width: 100, slots: { default: 'projectType' } }]
    : []),
  ...(colVisible.contractAmount
    ? [
        {
          field: 'contractAmount',
          title: '合同金额',
          width: 120,
          align: 'right' as const,
          slots: { default: 'contractAmount' },
        },
      ]
    : []),
  ...(colVisible.plannedDuration
    ? [
        {
          field: 'plannedStartDate',
          title: '计划工期',
          width: 96,
          slots: { default: 'plannedDuration' },
        },
      ]
    : []),
  ...(colVisible.status
    ? [{ field: 'status', title: '状态', width: 74, slots: { default: 'status' } }]
    : []),
  ...(colVisible.approvalStatus
    ? [
        {
          field: 'approvalStatus',
          title: '审批状态',
          width: 86,
          slots: { default: 'approvalStatus' },
        },
      ]
    : []),
  ...(colVisible.ops ? [{ title: '操作', width: 112, slots: { default: 'ops' } }] : []),
])
</script>

<template>
  <div class="project-list-page app-page">
    <a-breadcrumb class="project-breadcrumb">
      <a-breadcrumb-item>项目管理</a-breadcrumb-item>
      <a-breadcrumb-item>项目列表</a-breadcrumb-item>
    </a-breadcrumb>

    <!-- 搜索栏 -->
    <div class="project-search">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索项目编号、名称、类型、建设单位…"
        allow-clear
        class="project-search-input"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #8c8c8c" /></template>
      </a-input>
      <div class="project-search-actions">
        <a-button type="primary" @click="handleSearch">查询</a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>

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

    <!-- KPI 卡片 -->
    <div class="project-stats-grid">
      <div class="project-stat-card">
        <div class="project-stat-title">
          <FileTextOutlined style="color: #1890ff" />
          <span>项目总数</span>
        </div>
        <div class="project-stat-value">{{ projectStats.total }} <small>个</small></div>
      </div>
      <div class="project-stat-card">
        <div class="project-stat-title">
          <DollarOutlined style="color: #faad14" />
          <span>合同总金额</span>
        </div>
        <div class="project-stat-value">
          {{
            (totalContractAmount / 10000).toLocaleString('zh-CN', {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })
          }}
          <small>万元</small>
        </div>
      </div>
      <div class="project-stat-card">
        <div class="project-stat-title">
          <SafetyCertificateOutlined style="color: #52c41a" />
          <span>在建项目</span>
        </div>
        <div class="project-stat-value">{{ projectStats.ongoing }} <small>个</small></div>
      </div>
      <div class="project-stat-card">
        <div class="project-stat-title">
          <FlagOutlined style="color: #52c41a" />
          <span>已竣工项目</span>
        </div>
        <div class="project-stat-value">{{ projectStats.completed }} <small>个</small></div>
      </div>
      <div class="project-stat-card project-stat-card--risk">
        <div class="project-stat-title">
          <WarningOutlined />
          <span>风险项目</span>
        </div>
        <div class="project-stat-value">{{ projectStats.risk }} <small>个</small></div>
      </div>
    </div>

    <div class="project-content-layout">
      <!-- 左列 -->
      <div class="project-table-panel">
        <!-- 工具栏 -->
        <div class="project-table-toolbar">
          <div>
            <a-button type="primary" @click="handleCreateModalOpen">
              <template #icon><PlusOutlined /></template>
              新建项目
            </a-button>
            <a-button class="project-refresh-btn" @click="fetchData">
              <template #icon><ReloadOutlined /></template>
            </a-button>
            <a-dropdown :trigger="['click']">
              <a-button class="project-column-btn">
                <template #icon><SettingOutlined /></template>
                列设置
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item v-for="(_, key) in defaultCols" :key="key" @click="toggleCol(key)">
                    <a-checkbox :checked="colVisible[key]">
                      {{ colLabels[key] }}
                    </a-checkbox>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </div>

        <!-- 表格 -->
        <div class="lg-table-wrap">
          <vxe-grid
            :data="tableData"
            :columns="gridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            stripe
            border="inner"
            size="small"
            max-height="480"
          >
            <template #projectName="{ row }">
              <a class="lg-link" @click="router.push(`/project/${row.id}/overview`)">{{
                row.projectName
              }}</a>
            </template>
            <template #projectType="{ row }">
              <span class="lg-tag">{{ row.projectType }}</span>
            </template>
            <template #contractAmount="{ row }">
              <span class="lg-money">{{ fmtAmount(row.contractAmount) }}</span>
            </template>
            <template #plannedDuration="{ row }">
              <span>{{ row.plannedStartDate }} ~ {{ row.plannedEndDate }}</span>
            </template>
            <template #status="{ row }">
              <span class="lg-tag lg-tag--pill" :class="getStatusTagClass(row.status)">{{
                STATUS_LABEL[row.status] ?? row.status
              }}</span>
            </template>
            <template #approvalStatus="{ row }">
              <span class="lg-tag lg-tag--pill" :class="getApprovalTagClass(row.approvalStatus)">{{
                row.approvalStatus
              }}</span>
            </template>
            <template #ops="{ row }">
              <div class="lg-ops">
                <a class="lg-link" @click="router.push(`/project/${row.id}/overview`)">查看</a>
                <a class="lg-link" @click="handleEditModalOpen(row)">编辑</a>
                <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
              </div>
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
      </div>

      <!-- 右侧分析面板 -->
      <aside class="project-right-panel">
        <section class="project-widget">
          <div class="project-widget-title">项目状态分布</div>
          <VChart :option="statusOption" autoresize style="height: 176px" />
        </section>
        <section class="project-widget">
          <div class="project-widget-title">项目风险提示</div>
          <div class="project-list">
            <div v-for="item in riskProjects" :key="item.name" class="project-list-row">
              <span
                class="project-list-dot"
                :style="{
                  background:
                    item.status === '平稳' ? 'var(--project-success)' : 'var(--project-danger)',
                }"
              ></span>
              <span class="project-list-label">{{ item.name }}</span>
            </div>
          </div>
        </section>
        <section class="project-widget">
          <div class="project-widget-title">近期项目</div>
          <div class="project-list">
            <div v-for="item in recentProjects" :key="item.name" class="project-list-row">
              <span class="project-list-dot" :style="{ background: '#1890ff' }"></span>
              <span class="project-list-label">{{ item.name }}</span>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.project-list-page {
  --project-primary: #1890ff;
  --project-primary-hover: #40a9ff;
  --project-primary-light: #e6f7ff;
  --project-primary-text: #0050b3;
  --project-bg: #f0f2f5;
  --project-surface: #fff;
  --project-text: #333333;
  --project-text-secondary: #8c8c8c;
  --project-border: #f0f0f0;
  --project-control-border: #d9d9d9;
  --project-success: #52c41a;
  --project-success-bg: #f6ffed;
  --project-warning: #faad14;
  --project-warning-bg: #fffbe6;
  --project-danger: #ff4d4f;
  --project-danger-bg: #fff1f0;
  --project-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  --project-radius: 8px;

  min-height: 100%;
  padding: 20px 24px;
  background: var(--project-bg);
  color: var(--project-text);
}

.project-breadcrumb {
  margin-bottom: 16px;
  color: var(--project-text-secondary);
  font-size: 14px;
}

.project-search {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px 24px;
  background: var(--project-surface);
  border-radius: var(--project-radius);
  box-shadow: var(--project-shadow);
}

.project-search-input {
  flex: 1;
  min-width: 240px;
}

.project-search-input :deep(.ant-input),
.project-search-input :deep(.ant-input-affix-wrapper) {
  font-size: 14px;
}

.project-search :deep(.ant-input-affix-wrapper) {
  border: 0;
  box-shadow: none;
  padding-inline: 0;
}

.project-search :deep(.ant-input),
.project-search :deep(.ant-input::placeholder) {
  color: var(--project-text-secondary);
}

.project-search-actions {
  display: flex;
  gap: 8px;
}

.project-search :deep(.ant-btn),
.project-table-toolbar :deep(.ant-btn) {
  height: 34px;
  border-radius: 4px;
  font-size: 14px;
}

.project-search :deep(.ant-btn-primary),
.project-table-toolbar :deep(.ant-btn-primary) {
  background: var(--project-primary);
  border-color: var(--project-primary);
}

.project-search :deep(.ant-btn-primary:hover),
.project-table-toolbar :deep(.ant-btn-primary:hover) {
  background: var(--project-primary-hover);
  border-color: var(--project-primary-hover);
}

.project-stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.project-stat-card {
  min-height: 112px;
  padding: 20px;
  background: var(--project-surface);
  border: 0;
  border-radius: var(--project-radius);
  box-shadow: var(--project-shadow);
}

.project-stat-card--risk {
  background: var(--project-danger-bg);
  border-left: 4px solid var(--project-danger);
}

.project-stat-card--risk .project-stat-value {
  color: var(--project-danger);
}

.project-stat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--project-text-secondary);
  font-size: 14px;
}

.project-stat-title :deep(.anticon) {
  font-size: 16px;
}

.project-stat-value {
  color: var(--project-text);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.25;
  font-variant-numeric: tabular-nums;
}

.project-stat-value small {
  margin-left: 4px;
  color: var(--project-text-secondary);
  font-size: 14px;
  font-weight: 400;
}

.project-content-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.project-table-panel {
  flex: 1;
  min-width: 0;
  min-height: 516px;
  padding: 16px 0 0;
  background: var(--project-surface);
  border-radius: var(--project-radius);
  box-shadow: var(--project-shadow);
}

.project-table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px 16px;
  border-bottom: 1px solid var(--project-border);
}

.project-refresh-btn {
  margin-left: 8px;
  width: 34px;
  padding-inline: 0;
}

.project-column-btn {
  margin-left: 8px;
}

/* lg-table-wrap 已提供全局 vxe-table/Ant Table 统一样式；仅保留页面级溢出控制 */

.project-right-panel {
  width: 290px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.project-widget {
  padding: 20px;
  background: var(--project-surface);
  border-radius: var(--project-radius);
  box-shadow: var(--project-shadow);
}

.project-widget-title {
  margin-bottom: 16px;
  color: var(--project-text);
  font-size: 14px;
  font-weight: 500;
}

.project-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.project-list-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: var(--project-text);
  font-size: 13px;
  line-height: 1.5;
}

.project-list-dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.project-list-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pj-create-form {
  padding-top: 16px;
}

@media (max-width: 1400px) {
  .project-stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .project-content-layout {
    display: block;
  }

  .project-right-panel {
    display: none;
  }

  .project-stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .project-list-page {
    padding: 16px;
  }

  .project-search {
    align-items: stretch;
    flex-direction: column;
    padding: 14px 16px;
  }

  .project-search-actions {
    justify-content: flex-end;
  }

  .project-stats-grid {
    grid-template-columns: 1fr;
  }

  .project-content-layout .lg-pagination {
    align-items: flex-end;
    flex-direction: column;
  }
}
</style>
