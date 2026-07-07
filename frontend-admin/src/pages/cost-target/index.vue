<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ClockCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  FileTextOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  MoreOutlined,
  SettingOutlined,
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
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

// 字典常量 - 审批状态
const APPROVAL_DRAFT = 'DRAFT'
const APPROVAL_APPROVING = 'APPROVING'
const APPROVAL_APPROVED = 'APPROVED'
const APPROVAL_REJECTED = 'REJECTED'

const MOBILE_BP = 768
const isMobile = ref(window.innerWidth < MOBILE_BP)
const router = useRouter()

function onResize() {
  isMobile.value = window.innerWidth < MOBILE_BP
}

// ---- Dropdown data ----
const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const approvalStatusOptions = [APPROVAL_DRAFT, APPROVAL_APPROVING, APPROVAL_APPROVED, APPROVAL_REJECTED]
const APPROVAL_STATUS_DICT = 'approval_status'
const TARGET_STATUS_DICT = 'cost_target_status'

function approvalStatusLabel(status: string | undefined): string {
  return getDictLabelSync(APPROVAL_STATUS_DICT, status ?? '', APPROVAL_STATUS_LABEL)
}

function approvalStatusColor(status: string | undefined): string {
  return getDictTagColorSync(APPROVAL_STATUS_DICT, status ?? '', APPROVAL_STATUS_COLOR)
}

function targetStatusLabel(status: string | undefined): string {
  return getDictLabelSync(TARGET_STATUS_DICT, status ?? '', TARGET_STATUS_LABEL)
}

function targetStatusColor(status: string | undefined): string {
  return getDictTagColorSync(TARGET_STATUS_DICT, status ?? '', TARGET_STATUS_COLOR)
}

const activeStatusOptions = [
  { label: '当前启用', value: 1 },
  { label: '未启用', value: 0 },
]

// ---- Filter state ----
const filter = reactive({
  projectId: undefined as string | undefined,
  versionNo: '',
  approvalStatus: undefined as string | undefined,
  isActive: undefined as number | undefined,
})
const filterVisibility = reactive({
  projectId: true,
  approvalStatus: true,
  isActive: true,
})
const filterSettingItems = [
  { key: 'projectId', label: '项目' },
  { key: 'approvalStatus', label: '审批状态' },
  { key: 'isActive', label: '启用状态' },
] as const

// ---- Table state ----
const loading = ref(false)
const activating = ref(false)
const tableData = ref<CostTargetVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const targetModalVisible = ref(false)
const targetModalMode = ref<'create' | 'edit' | 'view'>('create')
const targetModalId = ref('')
const targetModalTitle = computed(() => {
  if (targetModalMode.value === 'view') return '成本目标详情'
  return targetModalMode.value === 'edit' ? '编辑成本目标' : '新建成本目标'
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
    const res: PageResult<CostTargetVO> | CostTargetVO[] = await getCostTargetList(params)
    const records = Array.isArray(res) ? res : Array.isArray(res?.records) ? res.records : []
    tableData.value = records
    total.value = Array.isArray(res) ? records.length : Number(res?.total ?? records.length)
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载成本目标版本列表失败')
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
function toggleFilterVisibility(key: (typeof filterSettingItems)[number]['key']) {
  filterVisibility[key] = !filterVisibility[key]
}

// ---- Actions ----
function handleCreate() {
  router.push('/cost-target/create')
}

function handleEdit(row: CostTargetVO) {
  targetModalMode.value = 'edit'
  targetModalId.value = String(row.id)
  targetModalVisible.value = true
}

function handleView(row: CostTargetVO) {
  targetModalMode.value = 'view'
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
    title: '成本目标',
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
  approved: tableData.value.filter((item) => item.approvalStatus === APPROVAL_APPROVED).length,
  approving: tableData.value.filter((item) => item.approvalStatus === APPROVAL_APPROVING).length,
  draft: tableData.value.filter((item) => item.approvalStatus === APPROVAL_DRAFT).length,
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
    { label: targetStatusLabel('ACTIVE'), count: targetStats.value.active, color: '#52c41a' },
    {
      label: '未生效',
      count: Math.max(0, tableData.value.length - targetStats.value.active - cancelled),
      color: '#1677ff',
    },
    { label: targetStatusLabel('CANCELLED'), count: cancelled, color: '#ff4d4f' },
  ]
})

const recentTargets = computed(() => tableData.value.slice(0, 4))

function targetPercent(value: number): number {
  if (!total.value) return 0
  return Math.round((value / total.value) * 100)
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  fetchDictData(APPROVAL_STATUS_DICT)
  fetchDictData(TARGET_STATUS_DICT)
  referenceStore.fetchProjects()
  fetchData()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div class="lg-list-page lg-page app-page ct-page">
    <div class="lg-page-head ct-page-head">
      <div class="ct-page-head-main">
        <a-breadcrumb class="ct-breadcrumb">
          <a-breadcrumb-item>成本管理</a-breadcrumb-item>
          <a-breadcrumb-item>成本目标</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid ct-content-grid">
      <main class="lg-left ct-main-column">
        <section class="lg-kpi-strip ct-kpi-summary" aria-label="成本目标关键指标">
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
        </section>

        <div class="lg-search-bar ct-search-bar">
          <div class="ct-search-fields">
            <a-select
              v-if="filterVisibility.projectId"
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
            <a-select
              v-if="filterVisibility.approvalStatus"
              v-model:value="filter.approvalStatus"
              class="ct-search-select"
              placeholder="审批状态"
              allow-clear
              size="large"
              @change="handleSearch"
            >
              <a-select-option v-for="item in approvalStatusOptions" :key="item" :value="item">
                {{ approvalStatusLabel(item) }}
              </a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.isActive"
              v-model:value="filter.isActive"
              class="ct-search-select"
              placeholder="启用状态"
              allow-clear
              size="large"
              @change="handleSearch"
            >
              <a-select-option
                v-for="item in activeStatusOptions"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </a-select-option>
            </a-select>
          </div>
          <div class="ct-search-keyword-row">
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
            <div class="ct-search-actions">
              <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
              <a-button size="large" @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
              <a-dropdown trigger="click">
                <a-button size="large">
                  <template #icon><SettingOutlined /></template>
                  筛选栏设置
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item
                      v-for="item in filterSettingItems"
                      :key="item.key"
                      @click="toggleFilterVisibility(item.key)"
                    >
                      <a-checkbox :checked="filterVisibility[item.key]">
                        {{ item.label }}
                      </a-checkbox>
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
          </div>
        </div>

        <main class="lg-list-table-panel ct-table-panel">
          <div class="lg-toolbar ct-table-toolbar">
            <div class="lg-toolbar-left">
              <div class="ct-table-heading">
                <span class="ct-table-title">成本目标版本</span>
                <span class="ct-table-count">共 {{ total }} 条</span>
              </div>
            </div>
            <div class="lg-toolbar-right ct-table-toolbar-right">
              <ColumnSettingsButton
                v-if="!isMobile"
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button aria-label="刷新成本目标" title="刷新成本目标" @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
              <a-button type="primary" @click="handleCreate">
                <template #icon><PlusOutlined /></template>
                新建成本目标
              </a-button>
            </div>
          </div>

          <div v-if="isMobile" class="ct-mobile-list">
            <div v-if="loading" class="ct-mobile-state">
              <a-spin />
            </div>
            <div v-else-if="!tableData.length" class="ct-mobile-state">
              <a-empty description="暂无成本目标版本" />
            </div>
            <template v-else>
              <article v-for="row in tableData" :key="row.id" class="ct-mobile-card">
                <div class="ct-mobile-card-head">
                  <div>
                    <strong>{{ row.versionNo || '-' }}</strong>
                    <div class="ct-mobile-card-name">{{ row.versionName || '-' }}</div>
                  </div>
                  <a-tag :color="row.isActive === 1 ? 'green' : 'default'">
                    {{ row.isActive === 1 ? '当前启用' : '未启用' }}
                  </a-tag>
                </div>
                <div class="ct-mobile-card-meta">成本目标：{{ fmtAmount(row.totalTargetAmount) }} 万元</div>
                <div class="ct-mobile-card-meta">
                  审批状态：{{ approvalStatusLabel(row.approvalStatus) || '-' }}
                </div>
                <a-button type="link" class="ct-mobile-card-link" @click="handleView(row)">
                  查看详情
                </a-button>
              </article>
            </template>
          </div>
          <div v-else class="lg-table-wrap ct-table-wrap">
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
                <a-tag :color="approvalStatusColor(row.approvalStatus)">
                  {{ approvalStatusLabel(row.approvalStatus) }}
                </a-tag>
              </template>
              <template #status="{ row }">
                <a-tag :color="targetStatusColor(row.status)">
                  {{ targetStatusLabel(row.status) }}
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
                      <a-menu-item @click="handleView(row)">查看详情</a-menu-item>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item
                        v-if="row.isActive !== 1 && row.approvalStatus === APPROVAL_APPROVED"
                        :disabled="activating"
                        @click="handleActivate(row)"
                      >
                        <CheckCircleOutlined style="margin-right: 4px" />切换版本
                      </a-menu-item>
                      <a-menu-item
                        v-if="row.approvalStatus === APPROVAL_DRAFT || row.approvalStatus === APPROVAL_REJECTED"
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
        </main>
      </main>

      <aside class="lg-analysis-rail ct-analysis-rail" aria-label="成本目标辅助分析">
        <div class="lg-analysis-panel lg-fill-card ct-analysis-panel">
          <header class="ct-analysis-head">
            <div>
              <div class="ct-analysis-title">成本目标分析</div>
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
            <div v-if="!recentTargets.length" class="ct-empty-state">暂无成本目标版本</div>
          </section>
        </div>
      </aside>
    </div>

    <a-modal
      v-model:open="targetModalVisible"
      :title="targetModalTitle"
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
  background: var(--surface-subtle);
}
.ct-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
  gap: 16px;
}
.ct-page-head-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.ct-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}
.ct-page-head-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}
.ct-content-grid {
}
.ct-main-column {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}
.ct-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  margin-bottom: 0;
  overflow: hidden;
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
  padding-top: 0;
}
.ct-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0 0 12px;
}
.ct-analysis-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.ct-analysis-head {
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border-subtle);
}
.ct-analysis-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 20px;
}
.ct-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 10px 16px 0;
}
.ct-analysis-section + .ct-analysis-section {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}
.ct-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}
.ct-search-bar {
  align-items: stretch;
  flex-direction: column;
  gap: 12px;
  margin: 0;
}
.ct-search-fields {
  display: flex;
  flex: 0 0 auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
}
.ct-search-keyword-row {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}
.ct-search-input {
  flex: 1 1 auto;
  min-width: 320px;
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
  display: flex;
  flex: 1;
  flex-direction: column;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  min-height: 0;
}
.ct-table-toolbar {
  flex: 0 0 auto;
  border-bottom: 1px solid var(--border-subtle);
}
.ct-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}
.ct-table-heading,
.ct-table-toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ct-table-count {
  color: var(--text-secondary);
  font-size: 13px;
}
.ct-table-wrap {
  flex: 1 1 auto;
  min-height: 0;
}
.ct-table-wrap :deep(.vxe-grid) {
  height: 100%;
}
.ct-table-panel > .lg-pagination {
  flex: 0 0 auto;
}
.ct-table-wrap {
  min-height: 520px;
}
.ct-mobile-list {
  display: grid;
  gap: 12px;
  padding: 12px;
}
.ct-mobile-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}
.ct-mobile-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}
.ct-mobile-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.ct-mobile-card-name,
.ct-mobile-card-meta {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}
.ct-mobile-card-link {
  justify-self: flex-start;
  padding-left: 0;
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
  .ct-search-keyword-row,
  .ct-search-input,
  .ct-search-select {
    align-items: stretch;
    width: 100%;
    flex: 1 1 100%;
    min-width: 0;
  }

  .ct-page-head-actions {
    width: 100%;
    justify-content: flex-start;
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
