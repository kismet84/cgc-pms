<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
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
  } catch (e: unknown) {
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

const TYPE_COLOR: Record<string, string> = {
  施工总承包: 'blue',
  专业分包: 'green',
  劳务分包: 'cyan',
  材料采购: 'orange',
}

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'processing',
  ONGOING: 'success',
  COMPLETED: 'default',
  SUSPENDED: 'warning',
  CLOSED: 'error',
}

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '前期',
  ONGOING: '在建',
  COMPLETED: '已竣工',
  SUSPENDED: '已暂停',
  CLOSED: '已关闭',
}

const APPROVAL_COLOR: Record<string, string> = {
  已批准: 'success',
  审批中: 'processing',
  待审批: 'default',
  已拒绝: 'error',
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
  color: ['#1668dc', '#16a34a', '#f59e0b', '#dc2626'],
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
  { field: 'projectCode', title: '项目编号', width: 140, ellipsis: true },
  { field: 'projectName', title: '项目名称', minWidth: 160, slots: { default: 'projectName' } },
  { field: 'projectType', title: '项目类型', width: 110, slots: { default: 'projectType' } },
  {
    field: 'contractAmount',
    title: '合同金额',
    width: 130,
    align: 'right' as const,
    slots: { default: 'contractAmount' },
  },
  {
    field: 'plannedStartDate',
    title: '计划工期',
    width: 200,
    slots: { default: 'plannedDuration' },
  },
  { field: 'status', title: '状态', width: 80, slots: { default: 'status' } },
  { field: 'approvalStatus', title: '审批状态', width: 90, slots: { default: 'approvalStatus' } },
  { title: '操作', width: 120, slots: { default: 'ops' } },
])
</script>

<template>
  <div class="lg-page app-page">
    <!-- 页面头部 -->
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom:5px;font-size:13px">
          <a-breadcrumb-item>项目管理</a-breadcrumb-item>
          <a-breadcrumb-item>项目列表</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索项目编号、名称、类型、建设单位…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
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

    <div class="lg-grid">
      <!-- 左列 -->
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div v-if="!isMobile" class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">项目总数</span>
            <span class="lg-kpi-card-value">{{ projectStats.total }} <small>个</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-total)"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">合同总金额</span>
            <span class="lg-kpi-card-value">{{ (parseFloat(String(kpiMax.amount)) / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }} <small>万元</small></span>
            <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-amount)"></span></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">在建项目</span>
            <span class="lg-kpi-card-value">{{ projectStats.ongoing }} <small>个</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: kpiPct(projectStats.ongoing, kpiMax.total) + '%', background: 'var(--kpi-paid)' }"></span></span>
            <span class="lg-kpi-card-hint">{{ kpiPct(projectStats.ongoing, kpiMax.total) }}%</span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">已完工项目</span>
            <span class="lg-kpi-card-value">{{ projectStats.completed }} <small>个</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: kpiPct(projectStats.completed, kpiMax.total) + '%', background: 'var(--kpi-paid)' }"></span></span>
            <span class="lg-kpi-card-hint">{{ kpiPct(projectStats.completed, kpiMax.total) }}%</span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">风险项目</span>
            <span class="lg-kpi-card-value">{{ projectStats.risk }} <small>个</small></span>
            <span class="lg-kpi-card-bar"><span :style="{ width: kpiPct(projectStats.risk, kpiMax.total) + '%', background: 'var(--kpi-overdue)' }"></span></span>
            <span class="lg-kpi-card-hint" v-if="projectStats.risk">{{ kpiPct(projectStats.risk, kpiMax.total) }}%</span>
          </div>
        </div>

        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button type="primary" @click="handleCreateModalOpen">
              <template #icon><PlusOutlined /></template>
              新建项目
            </a-button>
            <a-button @click="fetchData">
              <template #icon><ReloadOutlined /></template>
            </a-button>
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
              <a-tag :color="TYPE_COLOR[row.projectType] ?? 'default'">{{
                row.projectType
              }}</a-tag>
            </template>
            <template #contractAmount="{ row }">
              <span class="lg-money">{{ fmtAmount(row.contractAmount) }}</span>
            </template>
            <template #plannedDuration="{ row }">
              <span>{{ row.plannedStartDate }} ~ {{ row.plannedEndDate }}</span>
            </template>
            <template #status="{ row }">
              <a-tag :color="STATUS_COLOR[row.status] ?? 'default'">{{
                STATUS_LABEL[row.status] ?? row.status
              }}</a-tag>
            </template>
            <template #approvalStatus="{ row }">
              <a-tag :color="APPROVAL_COLOR[row.approvalStatus] ?? 'default'">{{
                row.approvalStatus
              }}</a-tag>
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
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">项目状态分布</div>
          <VChart :option="statusOption" autoresize class="pt-chart" />
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">项目风险提示</div>
          <ul class="pt-compact-list">
            <li v-for="item in riskProjects" :key="item.name" class="pt-compact-row">
              <span>{{ item.name }}</span>
              <b>{{ item.status }}</b>
            </li>
          </ul>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">近期项目</div>
          <ul class="pt-compact-list">
            <li v-for="item in recentProjects" :key="item.name" class="pt-compact-row">
              <span>{{ item.name }}</span>
              <b>{{ item.status }}</b>
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.pj-create-form {
  padding-top: 16px;
}

.pt-chart {
  height: 176px;
}

.pt-compact-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pt-compact-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 13px;
}

.pt-compact-row b {
  color: var(--text);
  font-weight: 700;
}
</style>
