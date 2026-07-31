<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { message, Modal } from 'ant-design-vue'
import { FilterOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  createBidCost,
  deleteBidCost,
  getBidCost,
  getBidCosts,
  markBidCostAsLost,
  markBidCostAsWon,
  updateBidCost,
} from '@/api/modules/bid'
import { getProjectList } from '@/api/modules/project'
import { useUserStore } from '@/stores/user'
import type { BidCostQuery, BidCostVO, BidStatus } from '@/types/bid'
import type { ProjectVO } from '@/types/project'
import { useMobileViewport } from '@/composables/useMobileViewport'

const userStore = useUserStore()
const { isMobile } = useMobileViewport()
const mobileFiltersOpen = ref(false)
const loading = ref(false)
const saving = ref(false)
const createOpen = ref(false)
const editOpen = ref(false)
const editingId = ref('')
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<BidCostVO | null>(null)
const wonOpen = ref(false)
const wonLoading = ref(false)
const projectLoading = ref(false)
const winningBid = ref<BidCostVO | null>(null)
const selectedProjectId = ref<string>()
const projectOptions = ref<ProjectVO[]>([])
let detailRequestId = 0
const rows = ref<BidCostVO[]>([])
const total = ref(0)
const query = reactive<BidCostQuery>({ pageNo: 1, pageSize: 20 })
const createForm = reactive({ bidProjectName: '', remark: '' })
const editForm = reactive({ bidProjectName: '', remark: '' })
const canCreate = computed(
  () =>
    userStore.hasPermission('bid:add') ||
    userStore.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN'),
)
const canEdit = computed(
  () =>
    userStore.hasPermission('bid:edit') ||
    userStore.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN'),
)
const canDelete = computed(
  () =>
    userStore.hasPermission('bid:delete') ||
    userStore.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN'),
)
const canChangeStatus = computed(
  () =>
    userStore.hasPermission('bid:status') ||
    userStore.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN'),
)
const selectedWonProject = computed(() =>
  projectOptions.value.find((project) => project.id === selectedProjectId.value),
)

const statusMeta: Record<BidStatus, { label: string; color: string }> = {
  BIDDING: { label: '投标中', color: 'processing' },
  WON: { label: '已中标', color: 'success' },
  LOST: { label: '未中标', color: 'default' },
}

const statusCounts = computed(() =>
  (Object.keys(statusMeta) as BidStatus[]).map((status) => ({
    status,
    label: statusMeta[status].label,
    count: rows.value.filter((row) => row.bidStatus === status).length,
  })),
)
const recentRows = computed(() => rows.value.slice(0, 4))

const columns = [
  { title: '投标项目', dataIndex: 'bidProjectName', key: 'bidProjectName', minWidth: 240 },
  { title: '投标状态', dataIndex: 'bidStatus', key: 'bidStatus', width: 120 },
  { title: '关联项目ID', dataIndex: 'projectId', key: 'projectId', width: 180 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 260, fixed: 'right' as const },
]

function errorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    return (error.response?.data as { message?: string } | undefined)?.message || error.message
  }
  return error instanceof Error ? error.message : fallback
}

async function fetchRows() {
  loading.value = true
  try {
    const result = await getBidCosts({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      bidStatus: query.bidStatus,
      keyword: query.keyword?.trim() || undefined,
    })
    rows.value = result.records ?? []
    total.value = Number(result.total ?? 0)
  } catch (error: unknown) {
    rows.value = []
    total.value = 0
    message.error(errorMessage(error, '加载投标成本失败'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.bidProjectName = ''
  createForm.remark = ''
  createOpen.value = true
}

async function submitCreate() {
  const bidProjectName = createForm.bidProjectName.trim()
  if (!bidProjectName) {
    message.warning('请填写投标项目名称')
    return
  }
  saving.value = true
  try {
    await createBidCost({
      bidProjectName,
      remark: createForm.remark.trim() || undefined,
    })
    message.success('投标项目创建成功')
    createOpen.value = false
    createForm.bidProjectName = ''
    createForm.remark = ''
    query.pageNo = 1
    await fetchRows()
  } catch (error: unknown) {
    message.error(errorMessage(error, '新建投标项目失败'))
  } finally {
    saving.value = false
  }
}

function openEdit(row: BidCostVO) {
  editingId.value = row.id
  editForm.bidProjectName = row.bidProjectName
  editForm.remark = row.remark || ''
  editOpen.value = true
}

async function submitEdit() {
  const bidProjectName = editForm.bidProjectName.trim()
  if (!bidProjectName) {
    message.warning('请填写投标项目名称')
    return
  }
  saving.value = true
  try {
    await updateBidCost(editingId.value, {
      bidProjectName,
      remark: editForm.remark.trim() || undefined,
    })
    message.success('投标项目更新成功')
    editOpen.value = false
    await fetchRows()
  } catch (error: unknown) {
    message.error(errorMessage(error, '更新投标项目失败'))
  } finally {
    saving.value = false
  }
}

function confirmDelete(row: BidCostVO) {
  Modal.confirm({
    title: '删除投标项目',
    content: `确认删除“${row.bidProjectName}”吗？仅投标中项目可删除。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteBidCost(row.id)
        message.success('投标项目删除成功')
        await fetchRows()
      } catch (error: unknown) {
        message.error(errorMessage(error, '删除投标项目失败'))
        throw error
      }
    },
  })
}

function confirmMarkLost(row: BidCostVO) {
  Modal.confirm({
    title: '标记未中标',
    content: `确认将“${row.bidProjectName}”标记为未中标吗？该投标的 BID_COST 费用将被核销。`,
    okText: '确认未中标',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await markBidCostAsLost(row.id)
        message.success('投标项目已标记为未中标')
        await fetchRows()
      } catch (error: unknown) {
        message.error(errorMessage(error, '标记未中标失败'))
        throw error
      }
    },
  })
}

async function openMarkWon(row: BidCostVO) {
  winningBid.value = row
  selectedProjectId.value = undefined
  projectOptions.value = []
  wonOpen.value = true
  projectLoading.value = true
  try {
    const result = await getProjectList({ pageNo: 1, pageSize: 100 })
    projectOptions.value = (result.records ?? []).filter((project) => project.status !== 'ARCHIVED')
  } catch (error: unknown) {
    message.error(errorMessage(error, '加载关联项目失败'))
  } finally {
    projectLoading.value = false
  }
}

async function submitMarkWon() {
  if (!winningBid.value || !selectedProjectId.value || !selectedWonProject.value) {
    message.warning('请选择关联项目')
    return
  }
  wonLoading.value = true
  try {
    await markBidCostAsWon(winningBid.value.id, selectedProjectId.value)
    message.success('投标项目已标记为中标')
    wonOpen.value = false
    await fetchRows()
  } catch (error: unknown) {
    message.error(errorMessage(error, '标记中标失败'))
  } finally {
    wonLoading.value = false
  }
}

function clearMarkWon() {
  winningBid.value = null
  selectedProjectId.value = undefined
  projectOptions.value = []
}

async function openDetail(row: BidCostVO) {
  const requestId = ++detailRequestId
  detail.value = null
  detailOpen.value = true
  detailLoading.value = true
  try {
    const result = await getBidCost(row.id)
    if (requestId === detailRequestId) {
      detail.value = result
    }
  } catch (error: unknown) {
    if (requestId === detailRequestId) {
      message.error(errorMessage(error, '加载投标详情失败'))
    }
  } finally {
    if (requestId === detailRequestId) {
      detailLoading.value = false
    }
  }
}

function clearDetail() {
  detailRequestId += 1
  detailLoading.value = false
  detail.value = null
}

function search() {
  query.pageNo = 1
  void fetchRows()
}

function reset() {
  Object.assign(query, { pageNo: 1, pageSize: 20, bidStatus: undefined, keyword: undefined })
  mobileFiltersOpen.value = false
  void fetchRows()
}

function applyMobileFilters() {
  mobileFiltersOpen.value = false
  search()
}

function changePage(pageNo: number, pageSize: number) {
  query.pageNo = pageNo
  query.pageSize = pageSize
  void fetchRows()
}

onMounted(fetchRows)
</script>

<template>
  <div class="lg-list-page lg-page app-page bid-cost-page project-operation-list-page">
    <div class="lg-page-head">
      <a-breadcrumb>
        <a-breadcrumb-item>项目经营</a-breadcrumb-item>
        <a-breadcrumb-item>投标成本</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-grid bid-cost-workspace project-operation-workspace">
      <div class="lg-left bid-cost-main project-operation-main-column">
        <section
          class="lg-search-bar bid-cost-filter project-operation-query-panel"
          aria-label="投标成本筛选"
        >
          <div class="bid-cost-search-row">
            <a-input
              v-model:value="query.keyword"
              allow-clear
              size="large"
              placeholder="搜索投标项目名称"
              data-testid="keyword-input"
              @press-enter="search"
            >
              <template #prefix><SearchOutlined /></template>
            </a-input>
            <a-button
              type="primary"
              class="project-operation-desktop-query-action"
              data-testid="search-button"
              @click="search"
              >搜索</a-button
            >
            <a-button
              class="project-operation-desktop-query-action"
              data-testid="reset-button"
              @click="reset"
            >
              <template #icon><ReloadOutlined /></template>重置
            </a-button>
            <a-button
              class="bid-cost-filter-button project-operation-filter-toggle"
              :aria-expanded="mobileFiltersOpen"
              aria-controls="bid-cost-status-filter"
              @click="mobileFiltersOpen = !mobileFiltersOpen"
            >
              <template #icon><FilterOutlined /></template>筛选
            </a-button>
          </div>
          <div
            id="bid-cost-status-filter"
            class="bid-cost-status-filter project-operation-filter-panel"
            :class="{ 'is-open': mobileFiltersOpen }"
          >
            <a-select
              v-model:value="query.bidStatus"
              allow-clear
              placeholder="全部投标状态"
              data-testid="status-select"
            >
              <a-select-option value="BIDDING">投标中</a-select-option>
              <a-select-option value="WON">已中标</a-select-option>
              <a-select-option value="LOST">未中标</a-select-option>
            </a-select>
            <div class="bid-cost-filter-actions">
              <a-button @click="reset">重置</a-button>
              <a-button type="primary" @click="applyMobileFilters">应用筛选</a-button>
            </div>
          </div>
        </section>

        <section class="lg-list-table-panel bid-cost-table-panel project-operation-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <strong>投标项目</strong><span>共 {{ total }} 条</span>
            </div>
            <div class="lg-toolbar-right">
              <a-button
                v-if="canCreate"
                type="primary"
                data-testid="create-button"
                @click="openCreate"
              >
                <template #icon><PlusOutlined /></template>
                新建投标项目
              </a-button>
              <a-button data-testid="refresh-button" @click="fetchRows">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
          </div>
          <div class="lg-table-wrap bid-cost-table-wrap">
            <div v-if="isMobile" class="bid-cost-mobile-list">
              <article v-for="row in rows" :key="row.id" class="bid-cost-mobile-card">
                <div class="bid-cost-mobile-card-head">
                  <strong>{{ row.bidProjectName }}</strong>
                  <a-tag :color="statusMeta[row.bidStatus].color">
                    {{ statusMeta[row.bidStatus].label }}
                  </a-tag>
                </div>
                <div>{{ row.projectId || '暂未关联项目' }}</div>
                <small>{{ row.createdAt || '-' }}</small>
                <a-button type="link" data-testid="mobile-detail-button" @click="openDetail(row)">
                  查看详情
                </a-button>
                <a-button
                  v-if="canEdit && row.bidStatus === 'BIDDING'"
                  type="link"
                  data-testid="mobile-edit-button"
                  @click="openEdit(row)"
                >
                  编辑
                </a-button>
                <a-button
                  v-if="canChangeStatus && row.bidStatus === 'BIDDING'"
                  type="link"
                  data-testid="mobile-mark-won-button"
                  @click="openMarkWon(row)"
                >
                  标记中标
                </a-button>
                <a-button
                  v-if="canChangeStatus && row.bidStatus === 'BIDDING'"
                  type="link"
                  danger
                  data-testid="mobile-mark-lost-button"
                  @click="confirmMarkLost(row)"
                >
                  标记未中标
                </a-button>
                <a-button
                  v-if="canDelete && row.bidStatus === 'BIDDING'"
                  type="link"
                  danger
                  data-testid="mobile-delete-button"
                  @click="confirmDelete(row)"
                >
                  删除
                </a-button>
              </article>
              <a-empty v-if="!loading && !rows.length" description="暂无投标项目" />
            </div>
            <a-table
              v-else
              row-key="id"
              :columns="columns"
              :data-source="rows"
              :loading="loading"
              :pagination="false"
              :scroll="{ x: 860 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'bidStatus'">
                  <a-tag :color="statusMeta[record.bidStatus as BidStatus].color">
                    {{ statusMeta[record.bidStatus as BidStatus].label }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'projectId'">
                  {{ record.projectId || '—' }}
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-button type="link" data-testid="detail-button" @click="openDetail(record)">
                    查看
                  </a-button>
                  <a-button
                    v-if="canEdit && record.bidStatus === 'BIDDING'"
                    type="link"
                    data-testid="edit-button"
                    @click="openEdit(record)"
                  >
                    编辑
                  </a-button>
                  <a-button
                    v-if="canChangeStatus && record.bidStatus === 'BIDDING'"
                    type="link"
                    data-testid="mark-won-button"
                    @click="openMarkWon(record)"
                  >
                    标记中标
                  </a-button>
                  <a-button
                    v-if="canChangeStatus && record.bidStatus === 'BIDDING'"
                    type="link"
                    danger
                    data-testid="mark-lost-button"
                    @click="confirmMarkLost(record)"
                  >
                    标记未中标
                  </a-button>
                  <a-button
                    v-if="canDelete && record.bidStatus === 'BIDDING'"
                    type="link"
                    danger
                    data-testid="delete-button"
                    @click="confirmDelete(record)"
                  >
                    删除
                  </a-button>
                </template>
              </template>
              <template #emptyText>暂无投标项目</template>
            </a-table>
          </div>
          <div class="lg-pagination bid-cost-pagination">
            <span>共 {{ total }} 条</span>
            <a-pagination
              :current="query.pageNo"
              :page-size="query.pageSize"
              :total="total"
              :show-size-changer="!isMobile"
              :show-quick-jumper="!isMobile"
              @change="changePage"
              @show-size-change="changePage"
            />
          </div>
        </section>
      </div>

      <aside
        class="lg-analysis-rail bid-cost-analysis project-operation-analysis-rail"
        aria-label="投标成本辅助分析"
      >
        <div class="lg-analysis-panel lg-fill-card bid-cost-analysis-panel">
          <header class="lg-analysis-header" aria-label="投标成本分析概览">
            <div>
              <strong class="lg-analysis-heading">辅助分析</strong>
              <span class="lg-analysis-description">投标状态与近期项目</span>
            </div>
          </header>
          <section class="lg-analysis-section">
            <div class="lg-analysis-section-title">投标概览</div>
            <div class="lg-analysis-overview-list">
              <div class="lg-analysis-overview-row">
                <span>项目总数</span><strong>{{ total }} 个</strong>
              </div>
              <div v-for="item in statusCounts" :key="item.status" class="lg-analysis-overview-row">
                <span>{{ item.label }}</span
                ><strong>{{ item.count }} 个</strong>
              </div>
            </div>
          </section>
          <section class="lg-analysis-section">
            <div class="lg-analysis-section-title">近期投标</div>
            <div v-for="row in recentRows" :key="row.id" class="lg-type-row">
              <span class="lg-type-dot lg-analysis-dot-primary"></span>
              <span class="lg-type-label">{{ row.bidProjectName }}</span>
              <span class="lg-type-pct">{{ statusMeta[row.bidStatus].label }}</span>
            </div>
            <div v-if="!recentRows.length" class="bid-cost-analysis-empty">暂无投标项目</div>
          </section>
        </div>
      </aside>
    </div>

    <a-modal
      v-model:open="createOpen"
      title="新建投标项目"
      :confirm-loading="saving"
      ok-text="创建"
      cancel-text="取消"
      data-testid="create-modal"
      @ok="submitCreate"
    >
      <a-form layout="vertical">
        <a-form-item label="投标项目名称" required>
          <a-input
            v-model:value="createForm.bidProjectName"
            :maxlength="200"
            placeholder="请输入投标项目名称"
            data-testid="create-name-input"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea
            v-model:value="createForm.remark"
            :maxlength="500"
            :rows="4"
            show-count
            placeholder="可选，仅记录投标头备注"
            data-testid="create-remark-input"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="wonOpen"
      title="标记投标项目中标"
      :confirm-loading="wonLoading"
      ok-text="确认中标"
      cancel-text="取消"
      data-testid="mark-won-modal"
      @ok="submitMarkWon"
      @after-close="clearMarkWon"
    >
      <a-form layout="vertical">
        <a-form-item label="关联项目" required>
          <a-select
            v-model:value="selectedProjectId"
            show-search
            :loading="projectLoading"
            :filter-option="
              (input: string, option: { label?: string }) =>
                String(option.label || '')
                  .toLowerCase()
                  .includes(input.toLowerCase())
            "
            placeholder="请选择中标后关联的项目"
            data-testid="won-project-select"
          >
            <a-select-option
              v-for="project in projectOptions"
              :key="project.id"
              :value="project.id"
              :label="project.projectName"
            >
              {{ project.projectName }}（{{ project.projectCode }}）
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-alert
          v-if="winningBid && selectedWonProject"
          type="warning"
          show-icon
          :message="`确认将“${winningBid.bidProjectName}”标记为中标并关联到“${selectedWonProject.projectName}”吗？`"
        />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="editOpen"
      title="编辑投标项目"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      data-testid="edit-modal"
      @ok="submitEdit"
    >
      <a-form layout="vertical">
        <a-form-item label="投标项目名称" required>
          <a-input v-model:value="editForm.bidProjectName" :maxlength="200" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="editForm.remark" :maxlength="500" :rows="4" show-count />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="detailOpen"
      title="投标项目详情"
      :footer="null"
      data-testid="detail-modal"
      @after-close="clearDetail"
    >
      <a-spin :spinning="detailLoading">
        <a-descriptions v-if="detail" :column="1" bordered size="small">
          <a-descriptions-item label="投标项目名称">
            {{ detail.bidProjectName }}
          </a-descriptions-item>
          <a-descriptions-item label="投标状态">
            <a-tag :color="statusMeta[detail.bidStatus].color">
              {{ statusMeta[detail.bidStatus].label }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="关联项目">
            {{ detail.projectId || '暂未关联项目' }}
          </a-descriptions-item>
          <a-descriptions-item label="备注">{{ detail.remark || '—' }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ detail.createdAt || '—' }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{ detail.updatedAt || '—' }}</a-descriptions-item>
        </a-descriptions>
        <a-empty v-else-if="!detailLoading" description="详情加载失败，请关闭后重试" />
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.bid-cost-page {
  min-width: 0;
  background: var(--surface-subtle);
}

.bid-cost-filter {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  box-sizing: border-box;
  min-height: 60px;
  padding: 10px 14px;
  border: 0;
  box-shadow:
    inset 0 0 0 1px var(--border),
    var(--shadow-soft);
}

.bid-cost-search-row,
.bid-cost-status-filter,
.bid-cost-filter-actions,
.bid-cost-pagination {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bid-cost-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  width: 100%;
  min-width: 0;
}

.bid-cost-search-row :deep(.ant-input-affix-wrapper) {
  width: 100%;
  height: 40px;
  min-width: 0;
}

.bid-cost-status-filter :deep(.ant-select) {
  width: 100%;
}

.bid-cost-status-filter.is-open {
  display: grid !important;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.bid-cost-filter-actions {
  justify-content: flex-end;
}

.bid-cost-table-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.bid-cost-table-wrap {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.bid-cost-pagination {
  justify-content: flex-end;
  border-top: 1px solid var(--border-subtle);
}

.bid-cost-analysis-panel header,
.bid-cost-analysis-panel section {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.bid-cost-analysis-panel header {
  display: grid;
  gap: 2px;
}

.bid-cost-analysis-panel header span,
.bid-cost-analysis-panel small,
.bid-cost-analysis-empty,
.bid-cost-mobile-card small {
  color: var(--text-secondary);
  font-size: 12px;
}

.bid-cost-analysis-panel h3 {
  margin: 0 0 10px;
  font-size: 14px;
}

.bid-cost-analysis-row,
.bid-cost-recent-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 28px;
}

.bid-cost-recent-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bid-cost-mobile-list {
  display: grid;
}

.bid-cost-mobile-card {
  display: grid;
  gap: 5px;
  min-height: 88px;
  padding: 10px 14px;
  color: var(--text-secondary);
  font-size: 13px;
  border-bottom: 1px solid var(--border-subtle);
}

.bid-cost-mobile-card > :deep(.ant-btn-link) {
  justify-self: end;
  height: auto;
  padding: 0;
}

.bid-cost-mobile-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-primary);
  font-size: 14px;
}

@media (width < 500px) {
  .bid-cost-filter {
    display: flex;
    min-height: 40px;
    padding: 0;
    background: transparent;
    box-shadow: none;
  }

  .bid-cost-search-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
  }

  .bid-cost-status-filter.is-open {
    display: grid !important;
    gap: 8px;
    margin-top: 8px;
    padding: 10px;
    background: var(--surface);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
  }

  .bid-cost-table-panel .lg-toolbar {
    min-height: 48px;
    padding: 8px 12px;
  }

  .bid-cost-table-panel .lg-toolbar-right [data-testid='refresh-button'] {
    display: none;
  }
}
</style>
