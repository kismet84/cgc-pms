<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { getBidCosts } from '@/api/modules/bid'
import type { BidCostQuery, BidCostVO, BidStatus } from '@/types/bid'

const loading = ref(false)
const rows = ref<BidCostVO[]>([])
const total = ref(0)
const query = reactive<BidCostQuery>({ pageNo: 1, pageSize: 20 })

const statusMeta: Record<BidStatus, { label: string; color: string }> = {
  BIDDING: { label: '投标中', color: 'processing' },
  WON: { label: '已中标', color: 'success' },
  LOST: { label: '未中标', color: 'default' },
}

const columns = [
  { title: '投标项目', dataIndex: 'bidProjectName', key: 'bidProjectName', minWidth: 240 },
  { title: '投标状态', dataIndex: 'bidStatus', key: 'bidStatus', width: 120 },
  { title: '关联项目ID', dataIndex: 'projectId', key: 'projectId', width: 180 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
]

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    return (error.response?.data as { message?: string } | undefined)?.message || error.message
  }
  return error instanceof Error ? error.message : '加载投标成本失败'
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
    message.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNo = 1
  void fetchRows()
}

function reset() {
  Object.assign(query, { pageNo: 1, pageSize: 20, bidStatus: undefined, keyword: undefined })
  void fetchRows()
}

function changePage(pageNo: number, pageSize: number) {
  query.pageNo = pageNo
  query.pageSize = pageSize
  void fetchRows()
}

onMounted(fetchRows)
</script>

<template>
  <div class="lg-list-page lg-page app-page bid-cost-page">
    <div class="lg-page-head">
      <a-breadcrumb>
        <a-breadcrumb-item>项目经营</a-breadcrumb-item>
        <a-breadcrumb-item>投标成本</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <section class="lg-search-bar bid-cost-filter" aria-label="投标成本筛选">
      <a-input
        v-model:value="query.keyword"
        allow-clear
        placeholder="投标项目名称"
        data-testid="keyword-input"
        @press-enter="search"
      />
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
        <a-button type="primary" data-testid="search-button" @click="search">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button data-testid="reset-button" @click="reset">重置</a-button>
      </div>
    </section>

    <section class="lg-list-table-panel bid-cost-table-panel">
      <div class="lg-toolbar">
        <div class="lg-toolbar-left">
          <strong>投标项目</strong><span>共 {{ total }} 条</span>
        </div>
        <div class="lg-toolbar-right">
          <a-button data-testid="refresh-button" @click="fetchRows">
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
        </div>
      </div>
      <div class="lg-table-wrap">
        <a-table
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
          :show-size-changer="true"
          show-quick-jumper
          @change="changePage"
          @show-size-change="changePage"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.bid-cost-page {
  min-width: 0;
  background: var(--surface-subtle);
}

.bid-cost-filter {
  display: grid;
  grid-template-columns: minmax(220px, 2fr) minmax(180px, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.bid-cost-filter-actions,
.bid-cost-pagination {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bid-cost-table-panel {
  min-width: 0;
  overflow: hidden;
}

.bid-cost-pagination {
  justify-content: space-between;
  border-top: 1px solid var(--border-subtle);
}

@media (max-width: 768px) {
  .bid-cost-filter {
    grid-template-columns: 1fr;
  }

  .bid-cost-filter-actions > :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
