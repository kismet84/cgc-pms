<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import VChart from 'vue-echarts'
import { getProjectList, createProject, deleteProject } from '@/api/modules/project'
import type { ProjectVO } from '@/types/project'
import type { PageResult } from '@/types/api'

const filter = reactive({
  projectCode: '',
  projectName: '',
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
      projectCode: filter.projectCode || undefined,
      projectName: filter.projectName || undefined,
      projectType: filter.projectType,
      status: filter.status,
    })
    tableData.value = res.records
    total.value = res.total
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
  filter.projectCode = ''
  filter.projectName = ''
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

onMounted(fetchData)

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

const columns = [
  { title: '项目编号', dataIndex: 'projectCode', width: 150 },
  {
    title: '项目名称',
    dataIndex: 'projectName',
    minWidth: 180,
  },
  {
    title: '项目类型',
    dataIndex: 'projectType',
    width: 120,
  },
  {
    title: '合同金额',
    dataIndex: 'contractAmount',
    width: 140,
    align: 'right',
  },
  {
    title: '计划工期',
    dataIndex: 'plannedStartDate',
    width: 200,
  },
  { title: '状态', dataIndex: 'status', width: 90 },
  {
    title: '审批状态',
    dataIndex: 'approvalStatus',
    width: 100,
  },
  { title: '操作', dataIndex: 'ops', width: 120, fixed: 'right' },
]
</script>

<template>
  <div class="pj-page app-page project-target-redesign">
    <div class="pt-page-head">
      <div>
        <a-breadcrumb class="pt-breadcrumb">
          <a-breadcrumb-item>项目管理</a-breadcrumb-item>
          <a-breadcrumb-item>项目列表</a-breadcrumb-item>
        </a-breadcrumb>
        <h1 class="app-page-title">项目列表</h1>
      </div>
      <div class="pt-head-actions">
        <a-button type="primary" @click="handleCreateModalOpen">
          <PlusOutlined />
          新建项目
        </a-button>
      </div>
    </div>

    <!-- Filter -->
    <div class="pt-filter-surface">
      <div class="pt-filter-row">
        <div class="pt-field">
          <label for="filter-project-code">项目编号：</label>
          <a-input
            id="filter-project-code"
            v-model:value="filter.projectCode"
            placeholder="请输入项目编号"
            style="width: 160px"
            allow-clear
          />
        </div>
        <div class="pt-field">
          <label for="filter-project-name">项目名称：</label>
          <a-input
            id="filter-project-name"
            v-model:value="filter.projectName"
            placeholder="请输入项目名称"
            style="width: 180px"
            allow-clear
          />
        </div>
        <div class="pt-field">
          <label for="filter-project-type">项目类型：</label>
          <a-select
            id="filter-project-type"
            v-model:value="filter.projectType"
            placeholder="全部"
            allow-clear
            style="width: 140px"
          >
            <a-select-option value="施工总承包">施工总承包</a-select-option>
            <a-select-option value="专业分包">专业分包</a-select-option>
            <a-select-option value="劳务分包">劳务分包</a-select-option>
            <a-select-option value="材料采购">材料采购</a-select-option>
          </a-select>
        </div>
        <div class="pt-field">
          <label for="filter-status">状态：</label>
          <a-select
            id="filter-status"
            v-model:value="filter.status"
            placeholder="全部"
            allow-clear
            style="width: 120px"
          >
            <a-select-option value="DRAFT">前期</a-select-option>
            <a-select-option value="ONGOING">在建</a-select-option>
            <a-select-option value="COMPLETED">已竣工</a-select-option>
            <a-select-option value="SUSPENDED">已暂停</a-select-option>
            <a-select-option value="CLOSED">已关闭</a-select-option>
          </a-select>
        </div>
        <div class="pt-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <div class="pt-kpi-strip">
      <div class="pt-kpi">
        <div class="pt-kpi-label">项目总数</div>
        <div class="pt-kpi-value">{{ projectStats.total }} <small>个</small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">在建项目</div>
        <div class="pt-kpi-value">{{ projectStats.ongoing }} <small>个</small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">已完工项目</div>
        <div class="pt-kpi-value">{{ projectStats.completed }} <small>个</small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">风险项目</div>
        <div class="pt-kpi-value">{{ projectStats.risk }} <small>个</small></div>
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

    <div class="pt-ledger-layout">
      <main class="pt-panel pt-table-panel">
        <div class="pt-panel-header">项目清单</div>
        <a-table
          :data-source="tableData"
          :columns="columns"
          :loading="loading"
          :pagination="false"
          row-key="id"
          size="small"
          :scroll="{ x: 1100 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'projectName'">
              <a class="pt-link" @click="router.push(`/project/${record.id}/overview`)">{{ record.projectName }}</a>
            </template>
            <template v-else-if="column.dataIndex === 'projectType'">
              <a-tag :color="TYPE_COLOR[record.projectType] ?? 'default'">{{
                record.projectType
              }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'contractAmount'">
              <span class="pj-money">{{ fmtAmount(record.contractAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'plannedStartDate'">
              <span>{{ record.plannedStartDate }} ~ {{ record.plannedEndDate }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag :color="STATUS_COLOR[record.status] ?? 'default'">{{
                STATUS_LABEL[record.status] ?? record.status
              }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'approvalStatus'">
              <a-tag :color="APPROVAL_COLOR[record.approvalStatus] ?? 'default'">{{
                record.approvalStatus
              }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'ops'">
              <div class="pj-ops">
                <a class="pt-link" @click="router.push(`/project/${record.id}/overview`)">查看</a>
                <a class="pt-link" @click="router.push(`/project/${record.id}/edit`)">编辑</a>
                <a class="pt-link pt-danger" @click="handleDelete(record)">删除</a>
              </div>
            </template>
          </template>
        </a-table>
        <div class="pt-pagination">
          <span class="pt-total">共 {{ total }} 条</span>
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

      <aside class="pt-analysis-rail">
        <section class="pt-panel">
          <div class="pt-panel-header">项目状态分布</div>
          <div class="pt-panel-body">
            <VChart :option="statusOption" autoresize class="pt-chart" />
          </div>
        </section>
        <section class="pt-panel">
          <div class="pt-panel-header">项目风险提示</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li v-for="item in riskProjects" :key="item.name" class="pt-compact-row">
                <span>{{ item.name }}</span>
                <b>{{ item.status }}</b>
              </li>
            </ul>
          </div>
        </section>
        <section class="pt-panel">
          <div class="pt-panel-header">近期项目</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li v-for="item in recentProjects" :key="item.name" class="pt-compact-row">
                <span>{{ item.name }}</span>
                <b>{{ item.status }}</b>
              </li>
            </ul>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.pj-page {
  padding: 4px 0;
}
.pj-money {
  font-variant-numeric: tabular-nums;
}
.pj-ops {
  display: flex;
  gap: 10px;
}
.pj-create-form {
  padding-top: 16px;
}
</style>
