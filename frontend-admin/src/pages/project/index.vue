<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getProjectList } from '@/api/modules/project'
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
  } catch {
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
  进行中: 'success',
  已完工: 'default',
  暂停: 'warning',
  前期: 'processing',
}

const APPROVAL_COLOR: Record<string, string> = {
  已批准: 'success',
  审批中: 'processing',
  待审批: 'default',
  已拒绝: 'error',
}

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
  { title: '操作', dataIndex: 'ops', width: 120, fixed: 'right', slots: { customRender: 'ops' } },
]
</script>

<template>
  <div class="pj-page">
    <a-page-header title="项目管理" class="pj-header" />

    <!-- Filter -->
    <div class="pj-card pj-filter">
      <div class="pj-filter-row">
        <div class="pj-field">
          <label>项目编号：</label>
          <a-input
            v-model:value="filter.projectCode"
            placeholder="请输入项目编号"
            style="width: 160px"
            allow-clear
          />
        </div>
        <div class="pj-field">
          <label>项目名称：</label>
          <a-input
            v-model:value="filter.projectName"
            placeholder="请输入项目名称"
            style="width: 180px"
            allow-clear
          />
        </div>
        <div class="pj-field">
          <label>项目类型：</label>
          <a-select
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
        <div class="pj-field">
          <label>状态：</label>
          <a-select
            v-model:value="filter.status"
            placeholder="全部"
            allow-clear
            style="width: 120px"
          >
            <a-select-option value="进行中">进行中</a-select-option>
            <a-select-option value="已完工">已完工</a-select-option>
            <a-select-option value="暂停">暂停</a-select-option>
            <a-select-option value="前期">前期</a-select-option>
          </a-select>
        </div>
        <div class="pj-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="pj-card pj-table-wrap">
      <a-table
        :data-source="tableData"
        :columns="columns"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="middle"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'projectName'">
            <a class="pj-link" @click="() => record">{{ record.projectName }}</a>
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
            <a-tag :color="STATUS_COLOR[record.status] ?? 'default'">{{ record.status }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'approvalStatus'">
            <a-tag :color="APPROVAL_COLOR[record.approvalStatus] ?? 'default'">{{
              record.approvalStatus
            }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'ops'">
            <div class="pj-ops">
              <a class="pj-link" @click="() => record">查看</a>
              <a class="pj-link" @click="() => record">编辑</a>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="pj-pagination">
      <span class="pj-total">共 {{ total }} 条</span>
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
.pj-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.pj-header {
  background: transparent;
  padding-bottom: 8px;
}
.pj-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.pj-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.pj-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
}
.pj-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.pj-field label {
  color: #374151;
  min-width: 56px;
}
.pj-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}
.pj-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
}
.pj-link {
  color: #1677ff;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
}
.pj-money {
  font-variant-numeric: tabular-nums;
}
.pj-ops {
  display: flex;
  gap: 10px;
}
.pj-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.pj-total {
  font-size: 13px;
  color: #4b5563;
}
</style>
