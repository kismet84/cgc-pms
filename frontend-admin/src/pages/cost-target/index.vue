<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { PlusOutlined, ReloadOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getCostTargetList, activateCostTarget, deleteCostTarget } from '@/api/modules/costTarget'
import { useReferenceStore } from '@/stores/reference'
import CostTargetEditPage from './edit.vue'
import type { CostTargetVO, CostTargetQueryParams } from '@/types/costTarget'
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

const targetStats = computed(() => {
  const rows = tableData.value
  const totalTarget = rows.reduce((sum, item) => sum + (parseFloat(item.totalTargetAmount) || 0), 0)
  const locked = rows
    .filter((item) => item.isActive === 1 || item.approvalStatus === 'APPROVED')
    .reduce((sum, item) => sum + (parseFloat(item.totalTargetAmount) || 0), 0)
  const dynamic = rows
    .filter((item) => item.status === 'ACTIVE')
    .reduce((sum, item) => sum + (parseFloat(item.totalTargetAmount) || 0), 0)
  return {
    totalTarget,
    locked,
    dynamic,
    deviation: totalTarget - locked,
  }
})

const approvalRows = computed(() => {
  const counts = tableData.value.reduce<Record<string, number>>((acc, item) => {
    acc[item.approvalStatus] = (acc[item.approvalStatus] || 0) + 1
    return acc
  }, {})
  const rows = Object.entries(counts).map(([status, count]) => ({
    label: APPROVAL_STATUS_LABEL[status] ?? status,
    count,
  }))
  return rows.length ? rows : [{ label: '暂无版本', count: 0 }]
})

const warningRows = computed(() => {
  const rows = tableData.value
    .filter((item) => item.approvalStatus === 'REJECTED' || item.status === 'CANCELLED')
    .slice(0, 3)
    .map((item) => ({
      name: item.versionName,
      status:
        APPROVAL_STATUS_LABEL[item.approvalStatus] ??
        TARGET_STATUS_LABEL[item.status] ??
        item.status,
    }))
  return rows.length ? rows : [{ name: '暂无偏差预警', status: '平稳' }]
})

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

function isActiveTag(isActive: number) {
  return isActive === 1 ? { label: '当前版本', color: 'green' } : null
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
  { field: 'approvalStatus', title: '审批状态', width: 100, slots: { default: 'approvalStatus' } },
  { field: 'status', title: '业务状态', width: 100, slots: { default: 'status' } },
  { field: 'isActive', title: '版本标识', width: 100, slots: { default: 'isActive' } },
  { title: '操作', width: 160, slots: { default: 'ops' } },
]

onMounted(() => {
  referenceStore.fetchProjects()
  fetchData()
})
</script>

<template>
  <div class="ct-page app-page project-target-redesign">
    <div class="pt-page-head">
      <div>
        <a-breadcrumb class="pt-breadcrumb">
          <a-breadcrumb-item>目标管理</a-breadcrumb-item>
          <a-breadcrumb-item>目标管理</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
      <div class="pt-head-actions">
        <a-button type="primary" @click="handleCreate">
          <template #icon><PlusOutlined /></template>
          新建目标成本
        </a-button>
        <a-button @click="fetchData">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
    </div>

    <!-- Filter card -->
    <div class="pt-filter-surface">
      <div class="pt-filter-row">
        <div class="pt-field">
          <label>所属项目：</label>
          <a-select
            v-model:value="filter.projectId"
            placeholder="请选择项目"
            allow-clear
            style="width: 180px"
            show-search
            :filter-option="
              (input: string, option: any) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">{{
              p.projectName
            }}</a-select-option>
          </a-select>
        </div>
        <div class="pt-field">
          <label>版本号：</label>
          <a-input
            v-model:value="filter.versionNo"
            placeholder="请输入版本号"
            style="width: 160px"
          />
        </div>
        <div class="pt-field">
          <label>审批状态：</label>
          <a-select
            v-model:value="filter.approvalStatus"
            placeholder="全部"
            allow-clear
            style="width: 130px"
          >
            <a-select-option value="DRAFT">草稿</a-select-option>
            <a-select-option value="APPROVING">审批中</a-select-option>
            <a-select-option value="APPROVED">已通过</a-select-option>
            <a-select-option value="REJECTED">已驳回</a-select-option>
          </a-select>
        </div>
        <div class="pt-field">
          <label>版本标识：</label>
          <a-select
            v-model:value="filter.isActive"
            placeholder="全部"
            allow-clear
            style="width: 130px"
          >
            <a-select-option :value="1">当前版本</a-select-option>
            <a-select-option :value="0">历史版本</a-select-option>
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
        <div class="pt-kpi-label">目标总额</div>
        <div class="pt-kpi-value">
          {{ fmtAmount(String(targetStats.totalTarget)) }} <small>万元</small>
        </div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">已锁定成本</div>
        <div class="pt-kpi-value">
          {{ fmtAmount(String(targetStats.locked)) }} <small>万元</small>
        </div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">动态成本</div>
        <div class="pt-kpi-value">
          {{ fmtAmount(String(targetStats.dynamic)) }} <small>万元</small>
        </div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">偏差金额</div>
        <div class="pt-kpi-value">
          {{ fmtAmount(String(targetStats.deviation)) }} <small>万元</small>
        </div>
      </div>
    </div>

    <div class="pt-ledger-layout target-layout">
      <main class="pt-panel pt-table-panel">
        <div class="pt-panel-header">目标版本列表</div>
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
              <a class="pt-link" @click="handleEdit(row)">编辑</a>
              <a
                v-if="row.isActive !== 1 && row.approvalStatus === 'APPROVED'"
                class="pt-link"
                :class="{ 'ct-link--disabled': activating }"
                @click="handleActivate(row)"
              >
                <CheckCircleOutlined style="margin-right: 4px" />切换版本
              </a>
              <a
                v-if="row.approvalStatus === 'DRAFT' || row.approvalStatus === 'REJECTED'"
                class="pt-link pt-danger"
                @click="handleDelete(row)"
                >删除</a
              >
            </div>
          </template>
        </vxe-grid>
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
          <div class="pt-panel-header">目标占比</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li class="pt-compact-row">
                <span>当前版本</span
                ><b>{{ tableData.filter((i) => i.isActive === 1).length }} 个</b>
              </li>
              <li class="pt-compact-row">
                <span>历史版本</span
                ><b>{{ tableData.filter((i) => i.isActive !== 1).length }} 个</b>
              </li>
            </ul>
          </div>
        </section>
        <section class="pt-panel">
          <div class="pt-panel-header">偏差预警</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li v-for="item in warningRows" :key="item.name" class="pt-compact-row">
                <span>{{ item.name }}</span>
                <b>{{ item.status }}</b>
              </li>
            </ul>
          </div>
        </section>
        <section class="pt-panel">
          <div class="pt-panel-header">审批状态</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li v-for="item in approvalRows" :key="item.label" class="pt-compact-row">
                <span>{{ item.label }}</span>
                <b>{{ item.count }} 个</b>
              </li>
            </ul>
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
.ct-page {
  padding: 4px 0;
}
.ct-target-modal :deep(.ant-modal-body) {
  max-height: 82vh;
  overflow: auto;
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
.ct-muted {
  color: #9ca3af;
  font-size: 13px;
}
</style>
