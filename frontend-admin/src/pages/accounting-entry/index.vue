<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { Modal, message } from 'ant-design-vue'
import { EyeOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import {
  getAccountingEntries,
  getAccountingEntryDetail,
  postAccountingEntry,
  reverseAccountingEntry,
} from '@/api/modules/accounting'
import { useUserStore } from '@/stores/user'
import type {
  AccountingEntryDetailVO,
  AccountingEntryQuery,
  AccountingEntryStatus,
  AccountingEntryVO,
} from '@/types/accounting'

const userStore = useUserStore()
const isAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role.toUpperCase())),
)
const canEdit = computed(() => isAdmin.value || userStore.hasPermission('accounting:edit'))

const loading = ref(false)
const rows = ref<AccountingEntryVO[]>([])
const total = ref(0)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<AccountingEntryDetailVO | null>(null)
const actionEntryId = ref<string | null>(null)

const query = reactive<AccountingEntryQuery>({
  pageNo: 1,
  pageSize: 20,
})

const columns = [
  { title: '凭证编号', dataIndex: 'entryCode', key: 'entryCode', width: 190 },
  { title: '凭证日期', dataIndex: 'entryDate', key: 'entryDate', width: 120 },
  { title: '凭证类型', dataIndex: 'entryType', key: 'entryType', width: 130 },
  { title: '来源类型', dataIndex: 'sourceType', key: 'sourceType', width: 140 },
  { title: '来源单据ID', dataIndex: 'sourceId', key: 'sourceId', width: 150 },
  { title: '借方合计', dataIndex: 'totalDebit', key: 'totalDebit', width: 130, align: 'right' },
  { title: '贷方合计', dataIndex: 'totalCredit', key: 'totalCredit', width: 130, align: 'right' },
  { title: '状态', dataIndex: 'entryStatus', key: 'entryStatus', width: 100 },
  { title: '操作', key: 'actions', fixed: 'right', width: 210 },
]

const lineColumns = [
  { title: '行号', dataIndex: 'lineNo', key: 'lineNo', width: 70 },
  { title: '方向', dataIndex: 'direction', key: 'direction', width: 90 },
  { title: '科目', dataIndex: 'costSubjectId', key: 'subject', minWidth: 180 },
  { title: '摘要', dataIndex: 'summary', key: 'summary', minWidth: 180 },
  { title: '金额', dataIndex: 'amount', key: 'amount', width: 130, align: 'right' },
]

const statusMeta: Record<AccountingEntryStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  POSTED: { label: '已过账', color: 'processing' },
  REVERSED: { label: '已冲销', color: 'warning' },
}

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    return (error.response?.data as { message?: string } | undefined)?.message || error.message
  }
  return error instanceof Error ? error.message : fallback
}

async function fetchEntries() {
  loading.value = true
  try {
    const result = await getAccountingEntries(query)
    rows.value = result.records ?? []
    total.value = result.total ?? 0
  } catch (error: unknown) {
    console.error(error)
    rows.value = []
    total.value = 0
    message.error(getErrorMessage(error, '加载会计凭证失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  void fetchEntries()
}

function handleReset() {
  Object.assign(query, {
    pageNo: 1,
    pageSize: 20,
    entryType: undefined,
    sourceType: undefined,
    startDate: undefined,
    endDate: undefined,
    entryStatus: undefined,
  })
  void fetchEntries()
}

function handlePageChange(pageNo: number, pageSize: number) {
  query.pageNo = pageNo
  query.pageSize = pageSize
  void fetchEntries()
}

async function openDetail(entry: AccountingEntryVO) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getAccountingEntryDetail(entry.id)
  } catch (error: unknown) {
    console.error(error)
    message.error(getErrorMessage(error, '加载凭证详情失败'))
  } finally {
    detailLoading.value = false
  }
}

function confirmPost(entry: AccountingEntryVO) {
  Modal.confirm({
    title: '确认过账',
    content: `凭证 ${entry.entryCode} 过账后不可直接编辑，是否继续？`,
    okText: '确认过账',
    cancelText: '取消',
    async onOk() {
      actionEntryId.value = entry.id
      try {
        await postAccountingEntry(entry.id)
        message.success('凭证过账成功')
        await fetchEntries()
      } catch (error: unknown) {
        console.error(error)
        message.error(getErrorMessage(error, '凭证过账失败'))
        throw error
      } finally {
        actionEntryId.value = null
      }
    },
  })
}

function confirmReverse(entry: AccountingEntryVO) {
  Modal.confirm({
    title: '确认冲销',
    content: `凭证 ${entry.entryCode} 冲销后不可恢复为已过账，是否继续？`,
    okText: '确认冲销',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      actionEntryId.value = entry.id
      try {
        await reverseAccountingEntry(entry.id)
        message.success('凭证冲销成功')
        await fetchEntries()
      } catch (error: unknown) {
        console.error(error)
        message.error(getErrorMessage(error, '凭证冲销失败'))
        throw error
      } finally {
        actionEntryId.value = null
      }
    },
  })
}

onMounted(fetchEntries)
</script>

<template>
  <div class="lg-list-page lg-page app-page accounting-entry-page">
    <div class="lg-page-head">
      <a-breadcrumb class="lg-page-head-breadcrumb">
        <a-breadcrumb-item>结算收付</a-breadcrumb-item>
        <a-breadcrumb-item>会计凭证</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <a-alert
      class="accounting-entry-note"
      type="info"
      show-icon
      message="凭证生成入口暂未开放"
      description="当前只开放凭证查询、详情、过账和冲销。来源单据到借贷科目的生成规则需完成会计确认后另行启用。"
    />

    <section class="accounting-entry-filter" aria-label="会计凭证筛选">
      <a-input v-model:value="query.entryType" placeholder="凭证类型" allow-clear />
      <a-input v-model:value="query.sourceType" placeholder="来源类型" allow-clear />
      <a-select v-model:value="query.entryStatus" placeholder="凭证状态" allow-clear>
        <a-select-option value="DRAFT">草稿</a-select-option>
        <a-select-option value="POSTED">已过账</a-select-option>
        <a-select-option value="REVERSED">已冲销</a-select-option>
      </a-select>
      <a-input v-model:value="query.startDate" type="date" aria-label="开始日期" />
      <a-input v-model:value="query.endDate" type="date" aria-label="结束日期" />
      <div class="accounting-entry-filter-actions">
        <a-button type="primary" data-testid="search-button" @click="handleSearch">查询</a-button>
        <a-button data-testid="reset-button" @click="handleReset">重置</a-button>
        <a-button title="刷新凭证" aria-label="刷新凭证" @click="fetchEntries">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
    </section>

    <section class="accounting-entry-table-panel">
      <a-table
        row-key="id"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1360 }"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'entryStatus'">
            <a-tag :color="statusMeta[record.entryStatus as AccountingEntryStatus].color">
              {{ statusMeta[record.entryStatus as AccountingEntryStatus].label }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'totalDebit' || column.key === 'totalCredit'">
            ¥ {{ record[column.dataIndex] }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button
                type="link"
                size="small"
                :data-testid="`detail-${record.id}`"
                @click="openDetail(record)"
              >
                <template #icon><EyeOutlined /></template>
                详情
              </a-button>
              <a-button
                v-if="canEdit && record.entryStatus === 'DRAFT'"
                type="link"
                size="small"
                :loading="actionEntryId === record.id"
                :data-testid="`post-${record.id}`"
                @click="confirmPost(record)"
              >
                过账
              </a-button>
              <a-button
                v-if="canEdit && record.entryStatus === 'POSTED'"
                type="link"
                danger
                size="small"
                :loading="actionEntryId === record.id"
                :data-testid="`reverse-${record.id}`"
                @click="confirmReverse(record)"
              >
                冲销
              </a-button>
            </a-space>
          </template>
        </template>
        <template #emptyText>暂无会计凭证</template>
      </a-table>

      <a-pagination
        v-if="total > 0"
        class="accounting-entry-pagination"
        :current="query.pageNo"
        :page-size="query.pageSize"
        :total="total"
        :show-size-changer="true"
        show-quick-jumper
        :show-total="(value: number) => `共 ${value} 条`"
        @change="handlePageChange"
        @show-size-change="handlePageChange"
      />
    </section>

    <a-drawer v-model:open="detailOpen" title="会计凭证详情" :width="720">
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-descriptions bordered :column="2" size="small">
            <a-descriptions-item label="凭证编号">{{ detail.entry.entryCode }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusMeta[detail.entry.entryStatus].color">
                {{ statusMeta[detail.entry.entryStatus].label }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="凭证日期">{{ detail.entry.entryDate }}</a-descriptions-item>
            <a-descriptions-item label="凭证类型">{{ detail.entry.entryType }}</a-descriptions-item>
            <a-descriptions-item label="来源类型">{{
              detail.entry.sourceType
            }}</a-descriptions-item>
            <a-descriptions-item label="来源单据ID">{{
              detail.entry.sourceId
            }}</a-descriptions-item>
            <a-descriptions-item label="借方合计"
              >¥ {{ detail.entry.totalDebit }}</a-descriptions-item
            >
            <a-descriptions-item label="贷方合计"
              >¥ {{ detail.entry.totalCredit }}</a-descriptions-item
            >
          </a-descriptions>

          <a-table
            class="accounting-entry-lines"
            row-key="id"
            :columns="lineColumns"
            :data-source="detail.lines"
            :pagination="false"
            :scroll="{ x: 680 }"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'direction'">
                {{ record.direction === 'DEBIT' ? '借方' : '贷方' }}
              </template>
              <template v-else-if="column.key === 'subject'">
                {{ detail.subjectNames[record.costSubjectId] || record.costSubjectId }}
              </template>
              <template v-else-if="column.key === 'amount'">¥ {{ record.amount }}</template>
            </template>
            <template #emptyText>暂无凭证明细</template>
          </a-table>
        </template>
      </a-spin>
    </a-drawer>
  </div>
</template>

<style scoped>
.accounting-entry-page {
  min-width: 0;
}

.accounting-entry-note {
  margin-bottom: 16px;
}

.accounting-entry-filter {
  display: grid;
  grid-template-columns: repeat(5, minmax(140px, 1fr)) auto;
  gap: 12px;
  align-items: center;
  padding: 16px;
  margin-bottom: 16px;
  background: var(--surface-card);
  border: 1px solid var(--line-soft);
  border-radius: 12px;
}

.accounting-entry-filter-actions {
  display: flex;
  gap: 8px;
}

.accounting-entry-table-panel {
  min-width: 0;
  padding: 16px;
  overflow: hidden;
  background: var(--surface-card);
  border: 1px solid var(--line-soft);
  border-radius: 12px;
}

.accounting-entry-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.accounting-entry-lines {
  margin-top: 20px;
}

@media (max-width: 1100px) {
  .accounting-entry-filter {
    grid-template-columns: repeat(2, minmax(160px, 1fr));
  }
}

@media (max-width: 640px) {
  .accounting-entry-filter {
    grid-template-columns: 1fr;
  }

  .accounting-entry-filter-actions {
    flex-wrap: wrap;
  }
}
</style>
