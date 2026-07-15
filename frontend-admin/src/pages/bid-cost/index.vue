<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { createBidCost, getBidCosts } from '@/api/modules/bid'
import { useUserStore } from '@/stores/user'
import type { BidCostQuery, BidCostVO, BidStatus } from '@/types/bid'

const userStore = useUserStore()
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
          <a-button v-if="canCreate" type="primary" data-testid="create-button" @click="openCreate">
            <template #icon><PlusOutlined /></template>
            新建投标项目
          </a-button>
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
