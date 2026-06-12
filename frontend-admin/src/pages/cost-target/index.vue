<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  PlusOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getCostTargetList, activateCostTarget, deleteCostTarget } from '@/api/modules/costTarget'
import { getProjectList } from '@/api/modules/project'
import type { CostTargetVO, CostTargetQueryParams } from '@/types/costTarget'
import {
  APPROVAL_STATUS_LABEL,
  APPROVAL_STATUS_COLOR,
  TARGET_STATUS_LABEL,
  TARGET_STATUS_COLOR,
} from '@/types/costTarget'
import type { PageResult } from '@/types/api'
import type { ProjectVO } from '@/types/project'

const router = useRouter()

// ---- Dropdown data ----
const projectList = ref<ProjectVO[]>([])

// ---- Filter state ----
const filter = reactive({
  projectId: undefined as string | undefined,
  versionNo: '',
  approvalStatus: undefined as string | undefined,
  isActive: undefined as number | undefined,
})

// ---- Table state ----
const loading = ref(false)
const activating = ref(false)
const tableData = ref<CostTargetVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

// ---- Fetch projects for filter ----
async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 500 })
    projectList.value = res.records
  } catch {
    projectList.value = []
  }
}

// ---- Fetch data ----
async function fetchData() {
  loading.value = true
  const params: CostTargetQueryParams = {
    pageNo: pageNo.value,
    pageSize: pageSize.value,
    projectId: filter.projectId,
    versionNo: filter.versionNo || undefined,
    approvalStatus: filter.approvalStatus,
    isActive: filter.isActive,
  }
  try {
    const res: PageResult<CostTargetVO> = await getCostTargetList(params)
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
    message.error('加载目标成本版本列表失败')
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
  filter.versionNo = ''
  filter.approvalStatus = undefined
  filter.isActive = undefined
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

// ---- Actions ----
function handleCreate() {
  router.push('/cost-target/create')
}

function handleEdit(row: CostTargetVO) {
  router.push(`/cost-target/${row.id}/edit`)
}

function handleActivate(row: CostTargetVO) {
  Modal.confirm({
    title: '确认切换版本？',
    content: `将激活版本「${row.versionNo} ${row.versionName}」，该项目下其他版本将自动失效。`,
    okText: '确认切换',
    cancelText: '取消',
    onOk: async () => {
      activating.value = true
      try {
        await activateCostTarget(row.id)
        message.success('版本已激活')
        fetchData()
      } catch {
        message.error('版本激活失败')
      } finally {
        activating.value = false
      }
    },
  })
}

function handleDelete(row: CostTargetVO) {
  Modal.confirm({
    title: '确认删除？',
    content: `将删除版本「${row.versionNo} ${row.versionName}」，删除后不可恢复。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteCostTarget(row.id)
        message.success('已删除')
        fetchData()
      } catch {
        message.error('删除失败')
      }
    },
  })
}

// ---- Helpers ----
function fmtAmount(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function isActiveTag(isActive: number) {
  return isActive === 1 ? { label: '当前版本', color: 'green' } : null
}

// ---- VxeGrid columns ----
const columns = [
  { field: 'versionNo', title: '版本号', width: 130 },
  { field: 'versionName', title: '版本名称', minWidth: 160 },
  { field: 'projectName', title: '所属项目', width: 150 },
  { field: 'totalTargetAmount', title: '目标成本合计(万元)', width: 150, align: 'right' as const, slots: { default: 'amount' } },
  { field: 'effectiveDate', title: '生效日期', width: 110 },
  { field: 'approvalStatus', title: '审批状态', width: 100, slots: { default: 'approvalStatus' } },
  { field: 'status', title: '业务状态', width: 100, slots: { default: 'status' } },
  { field: 'isActive', title: '版本标识', width: 100, slots: { default: 'isActive' } },
  { title: '操作', width: 200, fixed: 'right' as const, slots: { default: 'ops' } },
]

onMounted(() => {
  fetchProjects()
  fetchData()
})
</script>

<template>
  <div class="ct-page">
    <a-breadcrumb class="ct-breadcrumb">
      <a-breadcrumb-item>成本管理</a-breadcrumb-item>
      <a-breadcrumb-item>目标成本管理</a-breadcrumb-item>
    </a-breadcrumb>

    <!-- Filter card -->
    <div class="ct-card ct-filter">
      <div class="ct-filter-row">
        <div class="ct-field">
          <label>所属项目：</label>
          <a-select
            v-model:value="filter.projectId"
            placeholder="请选择项目"
            allow-clear
            style="width:180px"
          >
            <a-select-option
              v-for="p in projectList"
              :key="p.id"
              :value="p.id"
            >{{ p.projectName }}</a-select-option>
          </a-select>
        </div>
        <div class="ct-field">
          <label>版本号：</label>
          <a-input v-model:value="filter.versionNo" placeholder="请输入版本号" style="width:160px" />
        </div>
        <div class="ct-field">
          <label>审批状态：</label>
          <a-select v-model:value="filter.approvalStatus" placeholder="全部" allow-clear style="width:130px">
            <a-select-option value="DRAFT">草稿</a-select-option>
            <a-select-option value="APPROVING">审批中</a-select-option>
            <a-select-option value="APPROVED">已通过</a-select-option>
            <a-select-option value="REJECTED">已驳回</a-select-option>
          </a-select>
        </div>
        <div class="ct-field">
          <label>版本标识：</label>
          <a-select v-model:value="filter.isActive" placeholder="全部" allow-clear style="width:130px">
            <a-select-option :value="1">当前版本</a-select-option>
            <a-select-option :value="0">历史版本</a-select-option>
          </a-select>
        </div>
        <div class="ct-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="ct-toolbar">
      <div class="ct-toolbar-left">
        <a-button type="primary" @click="handleCreate">
          <template #icon><PlusOutlined /></template>
          新建目标成本
        </a-button>
        <a-button @click="fetchData">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
    </div>

    <!-- Table -->
    <div class="ct-card ct-table-wrap">
      <vxe-grid
        :data="tableData"
        :columns="columns"
        :loading="loading"
        :column-config="{ resizable: true }"
        stripe
        border="inner"
        size="small"
        max-height="480"
      >
        <template #amount="{ row }">
          <span class="ct-money">{{ fmtAmount(row.totalTargetAmount) }}</span>
        </template>
        <template #approvalStatus="{ row }">
          <a-tag :color="APPROVAL_STATUS_COLOR[row.approvalStatus] || 'default'">
            {{ APPROVAL_STATUS_LABEL[row.approvalStatus] || row.approvalStatus }}
          </a-tag>
        </template>
        <template #status="{ row }">
          <a-tag :color="TARGET_STATUS_COLOR[row.status] || 'default'">
            {{ TARGET_STATUS_LABEL[row.status] || row.status }}
          </a-tag>
        </template>
        <template #isActive="{ row }">
          <a-tag v-if="row.isActive === 1" color="green">当前版本</a-tag>
          <span v-else class="ct-muted">历史版本</span>
        </template>
        <template #ops="{ row }">
          <div class="ct-ops">
            <a class="ct-link" @click="handleEdit(row)">编辑</a>
            <a
              v-if="row.isActive !== 1 && row.approvalStatus === 'APPROVED'"
              class="ct-link"
              :class="{ 'ct-link--disabled': activating }"
              @click="handleActivate(row)"
            >
              <CheckCircleOutlined style="margin-right:4px" />切换版本
            </a>
            <a
              v-if="row.approvalStatus === 'DRAFT' || row.approvalStatus === 'REJECTED'"
              class="ct-link ct-del"
              @click="handleDelete(row)"
            >删除</a>
          </div>
        </template>
      </vxe-grid>
    </div>

    <!-- Pagination -->
    <div class="ct-pagination">
      <span class="ct-total">共 {{ total }} 条</span>
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
</template>

<style scoped>
.ct-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.ct-breadcrumb {
  margin-bottom: 16px;
  font-size: 14px;
}
.ct-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

/* Filter */
.ct-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.ct-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
}
.ct-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.ct-field label {
  color: #374151;
  min-width: 56px;
}
.ct-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}

/* Toolbar */
.ct-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}
.ct-toolbar-left {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* Table */
.ct-table-wrap {
  overflow: hidden;
}
.ct-link {
  color: #1677ff;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
}
.ct-link--disabled {
  color: #9ca3af;
  pointer-events: none;
}
.ct-money {
  font-variant-numeric: tabular-nums;
}
.ct-ops {
  display: flex;
  gap: 10px;
  justify-content: center;
}
.ct-del {
  color: #ef4444;
}
.ct-muted {
  color: #9ca3af;
  font-size: 13px;
}

/* Pagination */
.ct-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.ct-total {
  font-size: 13px;
  color: #4b5563;
}
</style>
