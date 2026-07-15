<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue'
import { FilterOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { createBidCost, getBidCosts } from '@/api/modules/bid'
import { useUserStore } from '@/stores/user'
import type { BidCostQuery, BidCostVO, BidStatus } from '@/types/bid'
import { useMobileViewport } from '@/composables/useMobileViewport'

const userStore = useUserStore()
const { isMobile } = useMobileViewport()
const mobileFiltersOpen = ref(false)
const loading = ref(false)
const saving = ref(false)
const createOpen = ref(false)
const rows = ref<BidCostVO[]>([])
const total = ref(0)
const query = reactive<BidCostQuery>({ pageNo: 1, pageSize: 20 })
const createForm = reactive({ bidProjectName: '', remark: '' })
const canCreate = computed(
  () =>
    userStore.hasPermission('bid:add') ||
    userStore.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN'),
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
              :scroll="{ x: 780 }"
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
          <header><strong>辅助分析</strong><span>投标状态与近期项目</span></header>
          <section>
            <h3>投标概览</h3>
            <div class="bid-cost-analysis-row">
              <span>项目总数</span><b>{{ total }} 个</b>
            </div>
            <div v-for="item in statusCounts" :key="item.status" class="bid-cost-analysis-row">
              <span>{{ item.label }}</span
              ><b>{{ item.count }} 个</b>
            </div>
          </section>
          <section>
            <h3>近期投标</h3>
            <div v-for="row in recentRows" :key="row.id" class="bid-cost-recent-row">
              <span>{{ row.bidProjectName }}</span>
              <small>{{ statusMeta[row.bidStatus].label }}</small>
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
