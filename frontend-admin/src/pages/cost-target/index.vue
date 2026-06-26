<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  ClockCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  FileTextOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  MoreOutlined,
  WarningOutlined,
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
import { ColumnSettingsButton } from '@/components/list-page'
import { useColumnSettings } from '@/composables/useColumnSettings'

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
const columns = computed(() => [
  { field: 'versionNo', title: '版本号', width: 130 },
  { field: 'versionName', title: '版本名称', minWidth: 160 },
  { field: 'projectName', title: '所属项目', width: 150 },
  {
    field: 'totalTargetAmount',
    title: '目标成本',
    width: 150,
    align: 'right' as const,
    slots: { default: 'amount' },
  },
  { field: 'effectiveDate', title: '生效日期', width: 110 },
  { field: 'approvalStatus', title: '审批状态', width: 108, slots: { default: 'approvalStatus' } },
  { field: 'status', title: '业务状态', width: 108, slots: { default: 'status' } },
  { field: 'isActive', title: '版本标识', width: 108, slots: { default: 'isActive' } },
  { title: '操作', width: 76, slots: { default: 'ops' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('cost_target_cols', columns)

const targetStats = computed(() => ({
  total: total.value,
  active: tableData.value.filter((item) => item.isActive === 1).length,
  approved: tableData.value.filter((item) => item.approvalStatus === 'APPROVED').length,
  approving: tableData.value.filter((item) => item.approvalStatus === 'APPROVING').length,
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

const targetVersionSummary = computed(() => {
  const cancelled = tableData.value.filter((item) => item.status === 'CANCELLED').length
  return [
    { label: '已生效', count: targetStats.value.active, color: '#52c41a' },
    {
      label: '未生效',
      count: Math.max(0, tableData.value.length - targetStats.value.active - cancelled),
      color: '#1677ff',
    },
    { label: '已作废', count: cancelled, color: '#ff4d4f' },
  ]
})

const recentTargets = computed(() => tableData.value.slice(0, 4))

function targetPercent(value: number): number {
  if (!total.value) return 0
  return Math.round((value / total.value) * 100)
}

onMounted(() => {
  referenceStore.fetchProjects()
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page ct-page">
    <div class="lg-page-head ct-page-head">
      <div class="ct-page-meta-row">
        <a-breadcrumb class="ct-breadcrumb">
          <a-breadcrumb-item>成本管理</a-breadcrumb-item>
          <a-breadcrumb-item>目标成本</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="ct-page-subtitle">统一管理目标成本版本、审批状态与当前生效版本</span>
      </div>
    </div>

    <div class="lg-search-bar ct-search-bar">
      <div class="ct-search-fields">
        <a-input
          v-model:value="filter.versionNo"
          class="ct-search-input"
          placeholder="搜索版本号…"
          allow-clear
          size="large"
          @press-enter="handleSearch"
        >
          <template #prefix><SearchOutlined class="ct-search-prefix-icon" /></template>
        </a-input>
        <a-select
          v-model:value="filter.projectId"
          class="ct-search-select"
          placeholder="全部项目"
          allow-clear
          show-search
          size="large"
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
      <div class="ct-search-actions">
        <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>

    <div class="lg-grid ct-content-grid">
      <main class="ct-main-column">
        <div class="ct-kpi-summary" aria-label="目标成本关键指标">
          <div class="ct-kpi-item">
            <span class="ct-kpi-icon is-total"><FileTextOutlined /></span>
            <span class="ct-kpi-label">版本总数</span>
            <span class="ct-kpi-value">{{ targetStats.total }} <small>个</small></span>
          </div>
          <div class="ct-kpi-item is-wide">
            <span class="ct-kpi-icon is-amount"><SafetyCertificateOutlined /></span>
            <span class="ct-kpi-label">生效版本</span>
            <span class="ct-kpi-value">{{ targetStats.active }} <small>个</small></span>
          </div>
          <div class="ct-kpi-item is-progress">
            <span class="ct-kpi-icon is-paid"><CheckCircleOutlined /></span>
            <span class="ct-kpi-label">已审批版本</span>
            <span class="ct-kpi-value">{{ targetStats.approved }} <small>个</small></span>
            <span class="ct-kpi-progress">
              <span :style="{ width: targetPercent(targetStats.approved) + '%' }"></span>
            </span>
          </div>
          <div class="ct-kpi-item is-progress is-unpaid">
            <span class="ct-kpi-icon is-unpaid"><ClockCircleOutlined /></span>
            <span class="ct-kpi-label">审批中版本</span>
            <span class="ct-kpi-value">{{ targetStats.approving }} <small>个</small></span>
            <span class="ct-kpi-progress">
              <span :style="{ width: targetPercent(targetStats.approving) + '%' }"></span>
            </span>
          </div>
          <div class="ct-kpi-item is-overdue">
            <span class="ct-kpi-icon is-overdue"><WarningOutlined /></span>
            <span class="ct-kpi-label">草稿版本</span>
            <span class="ct-kpi-value">{{ targetStats.draft }} <small>个</small></span>
          </div>
        </div>

        <section class="lg-list-table-panel ct-table-panel">
          <div class="lg-toolbar ct-table-toolbar">
            <div class="lg-toolbar-left">
              <span class="ct-table-title">目标成本版本</span>
              <span class="ct-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button aria-label="刷新目标成本" title="刷新目标成本" @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
              <a-button type="primary" @click="handleCreate">
                <template #icon><PlusOutlined /></template>
                新建目标成本
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="ct-toolbar-hint">固定表头 / 金额右对齐 / 行操作可展开</span>
            </div>
          </div>

          <div class="lg-table-wrap ct-table-wrap">
            <vxe-grid
              :data="tableData"
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
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
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item
                        v-if="row.isActive !== 1 && row.approvalStatus === 'APPROVED'"
                        :disabled="activating"
                        @click="handleActivate(row)"
                      >
                        <CheckCircleOutlined style="margin-right: 4px" />切换版本
                      </a-menu-item>
                      <a-menu-item
                        v-if="row.approvalStatus === 'DRAFT' || row.approvalStatus === 'REJECTED'"
                        danger
                        @click="handleDelete(row)"
                      >
                        删除
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
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

      <aside class="lg-analysis-rail ct-analysis-rail" aria-label="目标成本辅助分析">
        <div class="ct-analysis-panel">
          <header class="ct-analysis-head">
            <div>
              <div class="ct-analysis-title">目标成本分析</div>
              <div class="ct-analysis-subtitle">审批、版本与近期记录</div>
            </div>
            <a-button type="link" size="small" @click="fetchData">刷新</a-button>
          </header>

          <section class="ct-analysis-section">
            <div class="ct-section-title">审批状态分布</div>
            <div v-for="item in targetStatusSummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: targetPercent(item.count) + '%', background: item.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ targetPercent(item.count) }}%</span>
            </div>
          </section>

          <section class="ct-analysis-section">
            <div class="ct-section-title">版本状态分布</div>
            <div v-for="item in targetVersionSummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: targetPercent(item.count) + '%', background: item.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ targetPercent(item.count) }}%</span>
            </div>
          </section>

          <section class="ct-analysis-section">
            <div class="ct-section-title">近期版本</div>
            <div v-for="item in recentTargets" :key="item.id" class="ct-status-row">
              <span class="lg-type-dot" style="background: #1890ff"></span>
              <span class="ct-status-label">{{ item.versionName }}</span>
              <strong>{{ item.versionNo }}</strong>
            </div>
            <div v-if="!recentTargets.length" class="ct-empty-state">暂无目标成本版本</div>
          </section>
        </div>
      </aside>
    </div>

    <a-modal
      v-model:open="targetModalVisible"
      :title="targetModalMode === 'edit' ? '编辑目标成本' : '新建目标成本'"
      :width="800"
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
  max-height: calc(100vh - 96px);
  overflow: auto;
  padding: 12px 16px 0;
}
.ct-target-modal :deep(.ant-modal-header) {
  padding: 12px 16px;
}
.ct-target-modal :deep(.ant-modal-close) {
  top: 10px;
}
.ct-target-modal :deep(.ant-modal-title) {
  font-size: 15px;
  line-height: 22px;
}
.lg-link--disabled {
  color: var(--muted);
  pointer-events: none;
}
.ct-money {
  font-variant-numeric: tabular-nums;
}
.ct-ops {
  display: flex;
}
.ct-muted {
  color: var(--muted);
  font-size: 13px;
}
.ct-page {
  gap: 14px;
}
.ct-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}
.ct-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}
.ct-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}
.ct-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}
.ct-content-grid {
  align-items: stretch;
  min-height: 0;
}
.ct-main-column {
  min-width: 0;
}
.ct-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  overflow: hidden;
  min-height: 84px;
  margin-bottom: 16px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}
.ct-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px 8px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}
.ct-kpi-item:last-child {
  border-right: 0;
}
.ct-kpi-icon {
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
.ct-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}
.ct-kpi-icon.is-paid {
  color: var(--success);
  background: var(--success-soft);
}
.ct-kpi-icon.is-unpaid {
  color: var(--primary);
  background: var(--surface-tint);
}
.ct-kpi-icon.is-overdue {
  color: var(--error);
  background: var(--error-soft);
}
.ct-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ct-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ct-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}
.ct-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}
.ct-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}
.ct-kpi-item.is-unpaid .ct-kpi-progress > span {
  background: var(--kpi-unpaid);
}
.ct-analysis-rail {
  width: 336px;
  padding-top: 0;
}
.ct-analysis-panel {
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
.ct-analysis-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.ct-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}
.ct-analysis-subtitle {
  color: var(--text-secondary);
  font-size: 12px;
}
.ct-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}
.ct-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}
.ct-search-bar {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 74px;
}
.ct-search-fields {
  display: flex;
  flex: 1 1 auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
}
.ct-search-input {
  width: min(560px, 34vw);
  min-width: 360px;
  flex: 1 1 auto;
}
.ct-search-prefix-icon {
  color: var(--text-secondary);
}
.ct-search-select {
  width: 180px;
  flex: 0 0 180px;
}
.ct-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}
.ct-table-panel {
  overflow: hidden;
  border: 1px solid var(--border-subtle);
}
.ct-table-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}
.ct-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}
.ct-table-count,
.ct-toolbar-hint {
  color: var(--text-secondary);
  font-size: 13px;
}
.ct-table-wrap {
  min-height: 520px;
}
.ct-table-wrap :deep(.vxe-header--column .vxe-cell) {
  justify-content: center;
  text-align: center;
}
.ct-analysis-section :deep(.lg-type-row),
.ct-analysis-section .lg-type-row {
  display: grid;
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
  align-items: center;
  gap: 8px;
  color: var(--text);
  line-height: 1.5;
}
.ct-analysis-section .lg-type-dot {
  margin-top: 0;
}
.ct-analysis-section .lg-type-label {
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ct-status-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: var(--spacing-sm) 14px;
}
.ct-status-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}
.ct-status-label {
  min-width: 0;
  overflow: hidden;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ct-status-row strong {
  color: var(--text);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.ct-empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 72px;
  color: var(--muted);
  font-size: var(--font-size-sm);
  text-align: center;
}
@media (max-width: 768px) {
  .ct-page-head,
  .ct-search-bar,
  .ct-search-fields,
  .ct-search-input,
  .ct-search-select {
    align-items: stretch;
    width: 100%;
    flex: 1 1 100%;
    min-width: 0;
  }

  .ct-search-actions {
    justify-content: flex-start;
  }

  .ct-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ct-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }

  .ct-analysis-rail {
    width: 100%;
  }
}
</style>
