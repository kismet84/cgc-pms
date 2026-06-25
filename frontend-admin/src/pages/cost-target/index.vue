<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  PlusOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getCostTargetList, activateCostTarget, deleteCostTarget } from '@/api/modules/costTarget'
import { useReferenceStore } from '@/stores/reference'
import CostTargetEditPage from './edit.vue'
import type { CostTargetVO, CostTargetQueryParams } from '@/types/costTarget'
import type { SelectOption } from '@/types/ui'
import type { PageResult } from '@/types/api'
import {
  APPROVAL_STATUS_LABEL,
  APPROVAL_STATUS_COLOR,
  TARGET_STATUS_LABEL,
  TARGET_STATUS_COLOR,
} from '@/types/costTarget'

// ---- Dropdown data ----
const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])

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
const targetModalVisible = ref(false)
const targetModalMode = ref<'create' | 'edit'>('create')
const targetModalId = ref('')

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
    const res: PageResult<CostTargetVO> | CostTargetVO[] = await getCostTargetList(params)
    const records = Array.isArray(res) ? res : Array.isArray(res?.records) ? res.records : []
    tableData.value = records
    total.value = Array.isArray(res) ? records.length : Number(res?.total ?? records.length)
  } catch (e: unknown) {
    console.error(e)
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
  targetModalMode.value = 'create'
  targetModalId.value = ''
  targetModalVisible.value = true
}

function handleEdit(row: CostTargetVO) {
  targetModalMode.value = 'edit'
  targetModalId.value = String(row.id)
  targetModalVisible.value = true
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
      } catch (e: unknown) {
        console.error(e)
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
      } catch (e: unknown) {
        console.error(e)
        // 响应拦截器已弹出后端返回的错误消息，此处刷新列表以移除已删除的记录
        fetchData()
      }
    },
  })
}

function handleTargetSaved() {
  targetModalVisible.value = false
  fetchData()
}

function handleTargetClose() {
  targetModalVisible.value = false
}

// ---- Helpers ----
function fmtAmount(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// ---- VxeGrid columns ----
const columns = [
  { field: 'versionNo', title: '版本号', width: 130 },
  { field: 'versionName', title: '版本名称', minWidth: 160 },
  { field: 'projectName', title: '所属项目', width: 150 },
  {
    field: 'totalTargetAmount',
    title: '目标成本合计(万元)',
    width: 150,
    align: 'right' as const,
    slots: { default: 'amount' },
  },
  { field: 'effectiveDate', title: '生效日期', width: 110 },
  { field: 'approvalStatus', title: '审批状态', width: 108, slots: { default: 'approvalStatus' } },
  { field: 'status', title: '业务状态', width: 108, slots: { default: 'status' } },
  { field: 'isActive', title: '版本标识', width: 108, slots: { default: 'isActive' } },
  { title: '操作', width: 160, slots: { default: 'ops' } },
]

const targetStats = computed(() => ({
  total: total.value,
  active: tableData.value.filter((item) => item.isActive === 1).length,
  approved: tableData.value.filter((item) => item.approvalStatus === 'APPROVED').length,
  draft: tableData.value.filter((item) => item.approvalStatus === 'DRAFT').length,
}))

const targetStatusSummary = computed(() => [
  { label: '已通过', count: targetStats.value.approved, color: '#52c41a' },
  { label: '草稿', count: targetStats.value.draft, color: '#faad14' },
  {
    label: '其他状态',
    count: Math.max(
      0,
      tableData.value.length - targetStats.value.approved - targetStats.value.draft,
    ),
    color: '#8c8c8c',
  },
])

const recentTargets = computed(() => tableData.value.slice(0, 4))

onMounted(() => {
  referenceStore.fetchProjects()
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
        <a-breadcrumb-item>目标管理</a-breadcrumb-item>
        <a-breadcrumb-item>目标成本</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-search-bar ct-search-bar">
      <div class="ct-search-fields">
        <a-input
          v-model:value="filter.versionNo"
          class="ct-search-input"
          placeholder="搜索版本号…"
          allow-clear
          @press-enter="handleSearch"
        >
          <template #prefix><SearchOutlined style="color: #697380" /></template>
        </a-input>
        <a-select
          v-model:value="filter.projectId"
          class="ct-search-select"
          placeholder="全部项目"
          allow-clear
          show-search
          :filter-option="
            (input: string, option: SelectOption) =>
              option.label?.toLowerCase().includes(input.toLowerCase())
          "
          @change="handleSearch"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">{{
            p.projectName
          }}</a-select-option>
        </a-select>
      </div>
      <div class="lg-search-actions">
        <a-button type="primary" @click="handleSearch">查询</a-button>
        <a-button @click="handleReset">重置</a-button>
      </div>
    </div>

    <div class="lg-grid ct-content-grid">
      <main class="ct-main-column">
        <div class="lg-kpi-strip ct-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">版本总数</span>
            <span class="lg-kpi-card-value">{{ targetStats.total }} <small>个</small></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">当前版本</span>
            <span class="lg-kpi-card-value">{{ targetStats.active }} <small>个</small></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">已审批</span>
            <span class="lg-kpi-card-value">{{ targetStats.approved }} <small>个</small></span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">草稿版本</span>
            <span class="lg-kpi-card-value">{{ targetStats.draft }} <small>个</small></span>
          </div>
        </div>

        <section class="lg-list-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <a-button type="primary" @click="handleCreate">
                <template #icon><PlusOutlined /></template>
                新建目标成本
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
              </a-button>
            </div>
          </div>

          <div class="lg-table-wrap">
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
                <div class="ct-ops lg-ops">
                  <a class="lg-link" @click="handleEdit(row)">编辑</a>
                  <a
                    v-if="row.isActive !== 1 && row.approvalStatus === 'APPROVED'"
                    class="lg-link"
                    :class="{ 'lg-link--disabled': activating }"
                    @click="handleActivate(row)"
                  >
                    <CheckCircleOutlined style="margin-right: 4px" />切换版本
                  </a>
                  <a
                    v-if="row.approvalStatus === 'DRAFT' || row.approvalStatus === 'REJECTED'"
                    class="lg-link lg-link--danger"
                    @click="handleDelete(row)"
                    >删除</a
                  >
                </div>
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
              @change="handlePageChange"
              @show-size-change="handlePageSizeChange"
            />
          </div>
        </section>
      </main>

      <aside class="lg-analysis-rail ct-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">审批状态分布</div>
          <div class="lg-type-list">
            <div v-for="item in targetStatusSummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span style="margin-left: auto">{{ item.count }} 个</span>
            </div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">近期版本</div>
          <div class="lg-type-list">
            <div v-for="item in recentTargets" :key="item.id" class="lg-type-row">
              <span class="lg-type-dot" style="background: #1890ff"></span>
              <span class="lg-type-label">{{ item.versionName }}</span>
            </div>
            <div v-if="!recentTargets.length" class="lg-warning-empty">暂无目标成本版本</div>
          </div>
        </section>
      </aside>
    </div>

    <a-modal
      v-model:open="targetModalVisible"
      :title="targetModalMode === 'edit' ? '编辑目标成本' : '新建目标成本'"
      :width="1160"
      :destroy-on-close="true"
      :footer="null"
      :mask-closable="false"
      centered
      class="ct-target-modal"
      @cancel="handleTargetClose"
    >
      <CostTargetEditPage
        :embedded="true"
        :mode="targetModalMode"
        :target-id="targetModalId"
        @saved="handleTargetSaved"
        @close="handleTargetClose"
      />
    </a-modal>
  </div>
</template>

<style scoped>
.ct-target-modal :deep(.ant-modal-body) {
  max-height: 82vh;
  overflow: auto;
}
.lg-link--disabled {
  color: #9ca3af;
  pointer-events: none;
}
.ct-money {
  font-variant-numeric: tabular-nums;
}
.ct-ops {
  display: flex;
}
.ct-muted {
  color: #9ca3af;
  font-size: 13px;
}
.ct-content-grid {
  align-items: start;
}
.ct-main-column {
  min-width: 0;
}
.ct-kpi-strip {
  margin-bottom: 16px;
}
.ct-analysis-rail {
  padding-top: 0;
}
.ct-analysis-rail .lg-panel:first-child {
  min-height: 98px;
}
.ct-search-bar {
  align-items: center;
}
.ct-search-fields {
  display: flex;
  flex: 1 1 auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
}
.ct-search-input {
  width: auto;
  min-width: 240px;
  flex: 1 1 auto;
}
.ct-search-select {
  width: 200px;
  flex: 0 0 200px;
}
@media (max-width: 768px) {
  .ct-search-fields,
  .ct-search-input,
  .ct-search-select {
    width: 100%;
    flex: 1 1 100%;
  }
}
</style>
