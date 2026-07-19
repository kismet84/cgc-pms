<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { Modal, message } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  EyeOutlined,
  FileTextOutlined,
  ReloadOutlined,
  StopOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import {
  getAccountingEntries,
  getAccountingEntryDetail,
  postAccountingEntry,
  resubmitAccountingEntry,
  reviewAccountingEntry,
  reverseAccountingEntry,
} from '@/api/modules/accounting'
import { useUserStore } from '@/stores/user'
import type {
  AccountingEntryDetailVO,
  AccountingEntryQuery,
  AccountingEntryStatus,
  AccountingEntryVO,
} from '@/types/accounting'
import ListQueryPanel from '@/components/list-page/ListQueryPanel.vue'

const userStore = useUserStore()
const isAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role.toUpperCase())),
)
const canReview = computed(() => isAdmin.value || userStore.hasPermission('accounting:review'))
const canPost = computed(
  () =>
    isAdmin.value ||
    userStore.hasPermission('accounting:post') ||
    userStore.hasPermission('accounting:edit'),
)
const canAdjust = computed(
  () => isAdmin.value || userStore.hasPermission('accounting:adjustment:add'),
)

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
  { title: '复核状态', dataIndex: 'reviewStatus', key: 'reviewStatus', width: 110 },
  { title: '操作', key: 'actions', fixed: 'right', width: 330 },
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
const statusSummary = computed(() =>
  (Object.keys(statusMeta) as AccountingEntryStatus[]).map((status) => ({
    status,
    label: statusMeta[status].label,
    count: rows.value.filter((row) => row.entryStatus === status).length,
  })),
)
const pageDebit = computed(() =>
  rows.value.reduce((sum, row) => sum + Number(row.totalDebit || 0), 0),
)
const pageCredit = computed(() =>
  rows.value.reduce((sum, row) => sum + Number(row.totalCredit || 0), 0),
)
const pageDifference = computed(() => Math.abs(pageDebit.value - pageCredit.value))
const balancedCount = computed(
  () => rows.value.filter((row) => Number(row.totalDebit) === Number(row.totalCredit)).length,
)

function formatAmount(value: number) {
  return `¥${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
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
    total.value = Number(result.total ?? 0)
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

function confirmReview(entry: AccountingEntryVO, approved: boolean) {
  Modal.confirm({
    title: approved ? '确认复核通过' : '确认驳回复核',
    content: approved
      ? `确认凭证 ${entry.entryCode} 的科目、金额与来源单据一致？`
      : `凭证 ${entry.entryCode} 将退回制单人修改。`,
    okText: approved ? '复核通过' : '驳回',
    okType: approved ? 'primary' : 'danger',
    async onOk() {
      actionEntryId.value = entry.id
      try {
        await reviewAccountingEntry(
          entry.id,
          approved,
          approved ? '复核通过' : '科目、金额或来源资料需修正',
        )
        message.success(approved ? '凭证复核通过' : '凭证已驳回')
        await fetchEntries()
      } finally {
        actionEntryId.value = null
      }
    },
  })
}

async function resubmit(entry: AccountingEntryVO) {
  actionEntryId.value = entry.id
  try {
    await resubmitAccountingEntry(entry.id)
    message.success('凭证已重新提交复核')
    await fetchEntries()
  } finally {
    actionEntryId.value = null
  }
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
        await reverseAccountingEntry(entry.id, `冲销凭证 ${entry.entryCode}`)
        message.success('冲销凭证已生成，复核过账后生效')
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
  <div class="lg-list-page lg-page app-page accounting-entry-page settlement-domain-page">
    <div class="lg-page-head accounting-entry-page-head">
      <a-breadcrumb class="accounting-entry-breadcrumb">
        <a-breadcrumb-item>结算收付</a-breadcrumb-item>
        <a-breadcrumb-item>会计凭证</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-grid accounting-entry-workspace settlement-domain-workspace">
      <div class="lg-left accounting-entry-main-column settlement-domain-main-column">
        <section
          class="lg-kpi-strip accounting-entry-kpis settlement-domain-kpi"
          aria-label="会计凭证关键指标"
        >
          <article class="accounting-entry-kpi-item">
            <div class="accounting-entry-kpi-content">
              <span>凭证总数</span><strong>{{ total }} <small>张</small></strong>
            </div>
            <i class="accounting-entry-kpi-icon is-total"><FileTextOutlined /></i>
          </article>
          <article class="accounting-entry-kpi-item">
            <div class="accounting-entry-kpi-content">
              <span>当前页草稿</span
              ><strong>{{ statusSummary[0]?.count ?? 0 }} <small>张</small></strong>
            </div>
            <i class="accounting-entry-kpi-icon is-draft"><ClockCircleOutlined /></i>
          </article>
          <article class="accounting-entry-kpi-item">
            <div class="accounting-entry-kpi-content">
              <span>当前页已过账</span
              ><strong>{{ statusSummary[1]?.count ?? 0 }} <small>张</small></strong>
            </div>
            <i class="accounting-entry-kpi-icon is-posted"><CheckCircleOutlined /></i>
          </article>
          <article class="accounting-entry-kpi-item">
            <div class="accounting-entry-kpi-content">
              <span>当前页已冲销</span
              ><strong>{{ statusSummary[2]?.count ?? 0 }} <small>张</small></strong>
            </div>
            <i class="accounting-entry-kpi-icon is-reversed"><StopOutlined /></i>
          </article>
          <article class="accounting-entry-kpi-item">
            <div class="accounting-entry-kpi-content">
              <span>当前页借贷差额</span><strong>{{ formatAmount(pageDifference) }}</strong>
            </div>
            <i class="accounting-entry-kpi-icon is-difference"><WarningOutlined /></i>
          </article>
        </section>

        <ListQueryPanel aria-label="会计凭证筛选" @search="handleSearch" @reset="handleReset">
          <template #primary>
            <a-input
              v-model:value="query.entryType"
              placeholder="搜索凭证类型"
              allow-clear
              @press-enter="handleSearch"
            />
          </template>
          <template #filters>
            <a-input v-model:value="query.sourceType" placeholder="来源类型" allow-clear />
            <a-select v-model:value="query.entryStatus" placeholder="全部凭证状态" allow-clear>
              <a-select-option value="DRAFT">草稿</a-select-option>
              <a-select-option value="POSTED">已过账</a-select-option>
              <a-select-option value="REVERSED">已冲销</a-select-option>
            </a-select>
            <a-input v-model:value="query.startDate" type="date" aria-label="开始日期" />
            <a-input v-model:value="query.endDate" type="date" aria-label="结束日期" />
          </template>
        </ListQueryPanel>

        <section
          class="lg-list-table-panel accounting-entry-table-panel settlement-domain-table-panel"
        >
          <div class="lg-toolbar accounting-entry-toolbar settlement-domain-toolbar">
            <div class="lg-toolbar-left">
              <strong>凭证记录</strong>
              <span>共 {{ total }} 条</span>
            </div>
            <div class="lg-toolbar-right">
              <a-button title="刷新凭证" aria-label="刷新凭证" @click="fetchEntries">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
          </div>
          <div class="lg-table-wrap settlement-domain-table-wrap">
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
                <template v-else-if="column.key === 'reviewStatus'">
                  <a-tag
                    :color="
                      record.reviewStatus === 'APPROVED'
                        ? 'success'
                        : record.reviewStatus === 'REJECTED'
                          ? 'error'
                          : 'processing'
                    "
                  >
                    {{
                      record.reviewStatus === 'APPROVED'
                        ? '复核通过'
                        : record.reviewStatus === 'REJECTED'
                          ? '已驳回'
                          : '待复核'
                    }}
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
                      v-if="
                        canReview &&
                        record.entryStatus === 'DRAFT' &&
                        record.reviewStatus === 'PENDING'
                      "
                      type="link"
                      size="small"
                      :data-testid="`review-${record.id}`"
                      @click="confirmReview(record, true)"
                    >
                      复核
                    </a-button>
                    <a-button
                      v-if="record.entryStatus === 'DRAFT' && record.reviewStatus === 'REJECTED'"
                      type="link"
                      size="small"
                      :data-testid="`resubmit-${record.id}`"
                      @click="resubmit(record)"
                    >
                      重提
                    </a-button>
                    <a-button
                      v-if="
                        canPost &&
                        record.entryStatus === 'DRAFT' &&
                        record.reviewStatus === 'APPROVED'
                      "
                      type="link"
                      size="small"
                      :loading="actionEntryId === record.id"
                      :data-testid="`post-${record.id}`"
                      @click="confirmPost(record)"
                    >
                      过账
                    </a-button>
                    <a-button
                      v-if="canAdjust && record.entryStatus === 'POSTED'"
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
          </div>

          <div class="lg-pagination accounting-entry-pagination settlement-domain-pagination">
            <span>共 {{ total }} 条</span>
            <a-pagination
              :current="query.pageNo"
              :page-size="query.pageSize"
              :total="total"
              :show-size-changer="true"
              show-quick-jumper
              @change="handlePageChange"
              @show-size-change="handlePageChange"
            />
          </div>
        </section>
      </div>

      <aside
        class="lg-analysis-rail accounting-entry-analysis-rail settlement-domain-analysis-rail"
        aria-label="凭证辅助分析"
      >
        <div class="lg-analysis-panel accounting-entry-analysis-panel">
          <div class="accounting-entry-analysis-head lg-analysis-header">
            <div>
              <strong class="lg-analysis-heading">辅助分析</strong>
              <span class="lg-analysis-description">当前页状态与借贷平衡</span>
            </div>
          </div>

          <section class="accounting-entry-analysis-section">
            <div class="accounting-entry-section-title">状态分布</div>
            <div
              v-for="item in statusSummary"
              :key="item.status"
              class="accounting-entry-status-row"
            >
              <span><i :class="`is-${item.status.toLowerCase()}`"></i>{{ item.label }}</span>
              <strong>{{ item.count }} 条</strong>
            </div>
          </section>

          <section class="accounting-entry-analysis-section">
            <div class="accounting-entry-section-title">借贷汇总</div>
            <div class="accounting-entry-amount-row">
              <span>借方合计</span><strong>{{ formatAmount(pageDebit) }}</strong>
            </div>
            <div class="accounting-entry-amount-row">
              <span>贷方合计</span><strong>{{ formatAmount(pageCredit) }}</strong>
            </div>
            <div
              class="accounting-entry-balance-note"
              :class="{ 'is-warning': pageDifference > 0 }"
            >
              <strong>{{ balancedCount }}/{{ rows.length }} 条凭证借贷平衡</strong>
              <span>统计范围为当前页数据</span>
            </div>
          </section>

          <section class="accounting-entry-rule-note">
            <strong>手工生成入口未开放</strong>
            <span>付款、回款凭证由权威业务写侧自动生成；当前仅开放查询、详情、过账和冲销。</span>
          </section>
        </div>
      </aside>
    </div>

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
  background: var(--surface-subtle);
}

.accounting-entry-page-head {
  align-items: center;
  min-height: 0;
  padding: 0;
}

.accounting-entry-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.accounting-entry-workspace {
  align-items: stretch;
}

.accounting-entry-main-column {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.accounting-entry-kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  margin: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.accounting-entry-kpi-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 10px 18px;
}

.accounting-entry-kpi-item + .accounting-entry-kpi-item {
  border-left: 1px solid var(--border-subtle);
}

.accounting-entry-kpi-content {
  min-width: 0;
}

.accounting-entry-kpi-content > span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.accounting-entry-kpi-content strong {
  display: block;
  margin-top: 4px;
  color: var(--text);
  font-size: 24px;
  line-height: 1;
}

.accounting-entry-kpi-content small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.accounting-entry-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 10px;
  font-size: 18px;
}

.accounting-entry-kpi-icon.is-draft,
.accounting-entry-kpi-icon.is-difference {
  color: var(--warning);
  background: var(--warning-soft);
}

.accounting-entry-kpi-icon.is-posted {
  color: var(--success);
  background: var(--success-soft);
}

.accounting-entry-kpi-icon.is-reversed {
  color: var(--error);
  background: var(--error-soft);
}

.accounting-entry-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  width: 100%;
  margin: 0;
}

.accounting-entry-filter-grid,
.accounting-entry-filter-foot {
  display: flex;
  flex: 1 0 100%;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.accounting-entry-filter-grid > *,
.accounting-entry-filter-foot > :not(.accounting-entry-filter-actions) {
  flex: 1 1 180px;
  min-width: 0;
}

.accounting-entry-filter-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.accounting-entry-filter :deep(.ant-input),
.accounting-entry-filter :deep(.ant-select-selector) {
  min-height: 40px;
  border-radius: var(--radius-sm);
}

.accounting-entry-table-panel {
  min-width: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.accounting-entry-table-panel > .lg-table-wrap {
  flex: 1;
  min-height: 0;
}

.accounting-entry-toolbar .lg-toolbar-left {
  gap: 8px;
}

.accounting-entry-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}

.accounting-entry-toolbar .lg-toolbar-left span {
  color: var(--text-secondary);
  font-size: 13px;
}

.accounting-entry-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--border-subtle);
}

.accounting-entry-pagination > span {
  color: var(--text-secondary);
  font-size: 13px;
}

.accounting-entry-analysis-rail {
  display: flex !important;
}

.accounting-entry-analysis-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 0 0 12px;
  overflow: auto;
  position: sticky;
  top: 0;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.accounting-entry-analysis-head {
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.accounting-entry-analysis-section,
.accounting-entry-rule-note {
  padding: 10px 16px 0;
}

.accounting-entry-analysis-section + .accounting-entry-analysis-section,
.accounting-entry-rule-note {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.accounting-entry-analysis-head strong,
.accounting-entry-section-title {
  color: var(--text-primary);
  font-weight: 700;
}

.accounting-entry-analysis-head span,
.accounting-entry-rule-note span {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 18px;
}

.accounting-entry-status-row,
.accounting-entry-amount-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.accounting-entry-status-row > span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.accounting-entry-status-row i {
  width: 7px;
  height: 7px;
  background: var(--text-secondary);
  border-radius: 999px;
}

.accounting-entry-status-row i.is-posted {
  background: var(--success);
}

.accounting-entry-status-row i.is-reversed {
  background: var(--warning);
}

.accounting-entry-status-row strong,
.accounting-entry-amount-row strong {
  color: var(--text-primary);
}

.accounting-entry-balance-note {
  display: grid;
  gap: 4px;
  margin-top: 10px;
  padding: 10px;
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
}

.accounting-entry-balance-note.is-warning {
  background: var(--warning-soft);
}

.accounting-entry-balance-note strong {
  color: var(--text-primary);
  font-size: 13px;
}

.accounting-entry-balance-note span {
  color: var(--text-secondary);
  font-size: 12px;
}

.accounting-entry-rule-note {
  background: transparent;
}

.accounting-entry-rule-note strong {
  color: var(--text-primary);
}

.accounting-entry-lines {
  margin-top: 20px;
}

@media (max-width: 1200px) {
  .accounting-entry-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .accounting-entry-kpi-item:nth-child(odd) {
    border-left: 0;
  }

  .accounting-entry-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .accounting-entry-kpis {
    grid-template-columns: 1fr;
  }

  .accounting-entry-kpi-item + .accounting-entry-kpi-item {
    border-top: 1px solid var(--border-subtle);
    border-left: 0;
  }

  .accounting-entry-filter-grid,
  .accounting-entry-filter-foot {
    flex-direction: column;
    align-items: stretch;
  }

  .accounting-entry-filter-grid > *,
  .accounting-entry-filter-foot > :not(.accounting-entry-filter-actions) {
    width: 100%;
    flex: 0 0 auto;
  }

  .accounting-entry-filter-actions {
    width: 100%;
    margin-left: 0;
  }

  .accounting-entry-filter-actions :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
