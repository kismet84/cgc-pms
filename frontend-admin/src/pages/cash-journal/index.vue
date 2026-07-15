<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  BankOutlined,
  DownloadOutlined,
  PlusOutlined,
  ReloadOutlined,
  SettingOutlined,
  WalletOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  archiveCashJournalEntry,
  createCashJournalEntry,
  createFundAccount,
  exportCashJournal,
  getCashJournalDetail,
  getCashJournalList,
  getCashJournalSummary,
  getFundAccounts,
  getManageableFundAccounts,
  reopenCashJournalEntry,
  reverseCashJournalEntry,
  setFundAccountEnabled,
  updateCashJournalEntry,
  updateFundAccount,
} from '@/api/modules/cashbook'
import { deleteFile, getFileUrl, uploadFile } from '@/api/modules/file'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import type {
  CashJournalCommand,
  CashJournalEntryVO,
  CashJournalQuery,
  CashJournalSummaryVO,
  FundAccountCommand,
  FundAccountVO,
} from '@/types/cashbook'
import { downloadBlobFile } from '@/utils/download'
import CashJournalDetailDrawer from './components/CashJournalDetailDrawer.vue'
import CashJournalFormModal from './components/CashJournalFormModal.vue'
import FundAccountModal from './components/FundAccountModal.vue'

const CASH_JOURNAL_BUSINESS_TYPE = 'CASH_JOURNAL'
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const referenceStore = useReferenceStore()

const filter = reactive<CashJournalQuery>({
  accountId: undefined,
  businessDateStart: undefined,
  businessDateEnd: undefined,
  direction: undefined,
  projectId: undefined,
  contractId: undefined,
  sourceType: undefined,
  sourceId: undefined,
  status: undefined,
  hasAttachment: undefined,
  keyword: undefined,
})
const rows = ref<CashJournalEntryVO[]>([])
const summary = ref<CashJournalSummaryVO>({
  cashBalance: '0.00',
  bankBalance: '0.00',
  income: '0.00',
  expense: '0.00',
  pendingCount: 0,
})
const accounts = ref<FundAccountVO[]>([])
const manageableAccounts = ref<FundAccountVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const saving = ref(false)
const formOpen = ref(false)
const accountModalOpen = ref(false)
const activeEntry = ref<CashJournalEntryVO | null>(null)
const editingEntry = ref<CashJournalEntryVO | null>(null)

const roles = computed(() => userStore.roles ?? [])
const isAdmin = computed(() => roles.value.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role)))
const isSuperAdmin = computed(() => roles.value.includes('SUPER_ADMIN'))
const canMaintain = computed(
  () => isAdmin.value || userStore.hasPermission('cashbook:journal:maintain'),
)
const canExport = computed(
  () => isAdmin.value || userStore.hasPermission('cashbook:journal:export'),
)
const canManageAccount = computed(
  () => isAdmin.value || userStore.hasPermission('cashbook:account:manage'),
)
const canQueryProject = computed(() => isAdmin.value || userStore.hasPermission('project:query'))
const canQueryContract = computed(() => isAdmin.value || userStore.hasPermission('contract:query'))
const projects = computed(() => (canQueryProject.value ? (referenceStore.projects ?? []) : []))
const contracts = computed(() => (canQueryContract.value ? (referenceStore.contracts ?? []) : []))

const projectNames = computed(
  () => new Map(projects.value.map((item) => [item.id, item.projectName])),
)
const contractNames = computed(
  () => new Map(contracts.value.map((item) => [item.id, item.contractName])),
)

const totalBalance = computed(
  () => Number(summary.value.cashBalance || 0) + Number(summary.value.bankBalance || 0),
)
const cashFlowTotal = computed(
  () => Number(summary.value.income || 0) + Number(summary.value.expense || 0),
)
const incomeRatio = computed(() =>
  cashFlowTotal.value
    ? Math.round((Number(summary.value.income || 0) / cashFlowTotal.value) * 100)
    : 0,
)
const expenseRatio = computed(() => (cashFlowTotal.value ? 100 - incomeRatio.value : 0))
const archivedCount = computed(() => rows.value.filter((row) => row.status === 'ARCHIVED').length)
const linkedPaymentCount = computed(
  () => rows.value.filter((row) => row.sourceType === 'PAY_RECORD').length,
)

function readQuery(name: string) {
  const value = route.query[name]
  return typeof value === 'string' && value ? value : undefined
}

function hydrateQuery() {
  filter.accountId = readQuery('accountId')
  filter.direction = readQuery('direction') as CashJournalQuery['direction']
  filter.status = readQuery('status') as CashJournalQuery['status']
  filter.sourceType = readQuery('sourceType') as CashJournalQuery['sourceType']
  filter.sourceId = readQuery('sourceId')
  filter.projectId = readQuery('projectId')
  filter.contractId = readQuery('contractId')
  filter.businessDateStart = readQuery('businessDateStart')
  filter.businessDateEnd = readQuery('businessDateEnd')
  filter.keyword = readQuery('keyword')
  const attachment = readQuery('hasAttachment')
  filter.hasAttachment = attachment == null ? undefined : attachment === 'true'
}

function buildQuery(): CashJournalQuery {
  return { ...filter, pageNo: pageNo.value, pageSize: pageSize.value }
}

async function syncQuery() {
  const query = Object.fromEntries(
    Object.entries({ ...filter, pageNo: pageNo.value, pageSize: pageSize.value }).filter(
      ([, value]) => value !== undefined && value !== '',
    ),
  )
  await router.replace({ path: '/cash-journal', query })
}

async function loadRows() {
  loading.value = true
  try {
    await syncQuery()
    const query = buildQuery()
    const [page, totals] = await Promise.all([
      getCashJournalList(query),
      getCashJournalSummary(query),
    ])
    rows.value = page.records ?? []
    total.value = Number(page.total ?? 0)
    summary.value = totals
  } catch (error) {
    console.error(error)
    message.error('资金日记账加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAccounts() {
  accounts.value = await getFundAccounts()
}

async function loadDetail(id: string) {
  activeEntry.value = await getCashJournalDetail(id)
}

async function search() {
  pageNo.value = 1
  await loadRows()
}

async function resetFilters() {
  Object.assign(filter, {
    accountId: undefined,
    businessDateStart: undefined,
    businessDateEnd: undefined,
    direction: undefined,
    projectId: undefined,
    contractId: undefined,
    sourceType: undefined,
    sourceId: undefined,
    status: undefined,
    hasAttachment: undefined,
    keyword: undefined,
  })
  await search()
}

function openCreate() {
  editingEntry.value = null
  formOpen.value = true
}

function openEdit() {
  if (!activeEntry.value) return
  editingEntry.value = activeEntry.value
  formOpen.value = true
}

async function saveEntry(command: CashJournalCommand) {
  saving.value = true
  try {
    const saved = editingEntry.value
      ? await updateCashJournalEntry(editingEntry.value.id, command)
      : await createCashJournalEntry(command)
    message.success(editingEntry.value ? '流水已更新' : '流水已登记')
    formOpen.value = false
    await Promise.all([loadRows(), loadDetail(saved.id)])
  } finally {
    saving.value = false
  }
}

async function archiveEntry() {
  if (!activeEntry.value) return
  await archiveCashJournalEntry(activeEntry.value.id)
  message.success('流水已归档')
  await Promise.all([loadRows(), loadDetail(activeEntry.value.id)])
}

async function reverseEntry(reason: string) {
  if (!activeEntry.value) return
  const reversal = await reverseCashJournalEntry(activeEntry.value.id, reason)
  message.success(`红冲成功，反向流水 ${reversal.entryNo}`)
  await Promise.all([loadRows(), loadDetail(activeEntry.value.id)])
}

async function reopenEntry(reason: string) {
  if (!activeEntry.value) return
  await reopenCashJournalEntry(activeEntry.value.id, reason)
  message.success('已撤销归档，可进行受控修正')
  await Promise.all([loadRows(), loadDetail(activeEntry.value.id)])
}

async function uploadAttachment(file: File) {
  if (!activeEntry.value) return
  await uploadFile(file, CASH_JOURNAL_BUSINESS_TYPE, activeEntry.value.id)
  message.success('附件上传成功')
  await Promise.all([loadRows(), loadDetail(activeEntry.value.id)])
}

async function removeAttachment(id: string) {
  await deleteFile(id)
  message.success('附件已删除')
  if (activeEntry.value) await Promise.all([loadRows(), loadDetail(activeEntry.value.id)])
}

async function downloadAttachment(id: string) {
  const url = await getFileUrl(id)
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function exportRows() {
  const blob = await exportCashJournal(buildQuery())
  downloadBlobFile(blob, `cash-journal-${new Date().toISOString().slice(0, 10)}.csv`)
}

async function openAccountManager() {
  manageableAccounts.value = await getManageableFundAccounts()
  accountModalOpen.value = true
}

async function saveAccount(command: FundAccountCommand, id?: string) {
  saving.value = true
  try {
    if (id) await updateFundAccount(id, command)
    else await createFundAccount(command)
    message.success(id ? '账户已更新' : '账户已新增')
    ;[accounts.value, manageableAccounts.value] = await Promise.all([
      getFundAccounts(),
      getManageableFundAccounts(),
    ])
  } finally {
    saving.value = false
  }
}

async function toggleAccount(account: FundAccountVO) {
  await setFundAccountEnabled(account.id, account.enabledFlag !== 1)
  ;[accounts.value, manageableAccounts.value] = await Promise.all([
    getFundAccounts(),
    getManageableFundAccounts(),
  ])
}

function openSource(entry: CashJournalEntryVO) {
  void router.push({ path: '/payment/application', query: { payRecordId: entry.sourceId } })
}

function money(value?: string) {
  return `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function statusLabel(status: CashJournalEntryVO['status']) {
  return { DRAFT: '草稿', PENDING_ARCHIVE: '待归档', ARCHIVED: '已归档', REVERSED: '已红冲' }[
    status
  ]
}

async function handleProjectChange(projectId?: string) {
  if (!canQueryContract.value) return
  await referenceStore.fetchContracts(projectId ? { projectId } : undefined)
}

onMounted(async () => {
  hydrateQuery()
  await Promise.all([
    loadRows(),
    loadAccounts(),
    canQueryProject.value ? referenceStore.fetchProjects() : Promise.resolve([]),
    canQueryContract.value ? referenceStore.fetchContracts() : Promise.resolve([]),
  ])
  const entryId = readQuery('entryId')
  if (entryId) await loadDetail(entryId)
})
</script>

<template>
  <div class="lg-list-page lg-page app-page cash-journal-page">
    <div class="lg-page-head cash-journal-page-head">
      <a-breadcrumb class="cash-journal-breadcrumb">
        <a-breadcrumb-item>结算收付</a-breadcrumb-item>
        <a-breadcrumb-item>资金日记账</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-grid cash-journal-workspace">
      <div class="lg-left cash-journal-main-column">
        <section class="lg-kpi-strip cash-journal-kpis" aria-label="资金日记账关键指标">
          <article class="cash-journal-kpi-item">
            <div class="cash-journal-kpi-content">
              <span>现金余额</span><strong>{{ money(summary.cashBalance) }}</strong>
            </div>
            <i class="cash-journal-kpi-icon is-cash"><WalletOutlined /></i>
          </article>
          <article class="cash-journal-kpi-item">
            <div class="cash-journal-kpi-content">
              <span>银行余额</span><strong>{{ money(summary.bankBalance) }}</strong>
            </div>
            <i class="cash-journal-kpi-icon is-bank"><BankOutlined /></i>
          </article>
          <article class="cash-journal-kpi-item">
            <div class="cash-journal-kpi-content">
              <span>本期收入</span><strong>{{ money(summary.income) }}</strong>
            </div>
            <i class="cash-journal-kpi-icon is-income"><ArrowUpOutlined /></i>
          </article>
          <article class="cash-journal-kpi-item">
            <div class="cash-journal-kpi-content">
              <span>本期支出</span><strong>{{ money(summary.expense) }}</strong>
            </div>
            <i class="cash-journal-kpi-icon is-expense"><ArrowDownOutlined /></i>
          </article>
          <article class="cash-journal-kpi-item">
            <div class="cash-journal-kpi-content">
              <span>待归档</span><strong>{{ summary.pendingCount }} <small>条</small></strong>
            </div>
            <i class="cash-journal-kpi-icon is-pending"><WarningOutlined /></i>
          </article>
        </section>

        <section class="lg-search-bar cash-journal-filters" aria-label="资金日记账筛选">
          <div class="cash-journal-filter-grid">
            <select v-model="filter.accountId">
              <option :value="undefined">全部账户</option>
              <option v-for="account in accounts" :key="account.id" :value="account.id">
                {{ account.accountName }}
              </option>
            </select>
            <select v-model="filter.direction">
              <option :value="undefined">全部方向</option>
              <option value="IN">收入</option>
              <option value="OUT">支出</option>
            </select>
            <select
              v-if="canQueryProject"
              v-model="filter.projectId"
              data-testid="project-filter"
              @change="handleProjectChange(filter.projectId)"
            >
              <option :value="undefined">全部项目</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">
                {{ project.projectName }}
              </option>
            </select>
            <select
              v-if="canQueryContract"
              v-model="filter.contractId"
              data-testid="contract-filter"
            >
              <option :value="undefined">全部合同</option>
              <option v-for="contract in contracts" :key="contract.id" :value="contract.id">
                {{ contract.contractName }}
              </option>
            </select>
            <select v-model="filter.sourceType">
              <option :value="undefined">全部来源</option>
              <option value="MANUAL">手工登记</option>
              <option value="PAY_RECORD">付款回写</option>
              <option value="REVERSAL">红冲</option>
            </select>
          </div>
          <div class="cash-journal-filter-foot">
            <input
              v-model="filter.keyword"
              class="cash-journal-keyword"
              placeholder="流水号 / 摘要 / 往来单位"
              @keyup.enter="search"
            />
            <input v-model="filter.businessDateStart" type="date" aria-label="开始日期" />
            <input v-model="filter.businessDateEnd" type="date" aria-label="结束日期" />
            <select v-model="filter.status">
              <option :value="undefined">全部状态</option>
              <option value="DRAFT">草稿</option>
              <option value="PENDING_ARCHIVE">待归档</option>
              <option value="ARCHIVED">已归档</option>
              <option value="REVERSED">已红冲</option>
            </select>
            <select v-model="filter.hasAttachment">
              <option :value="undefined">全部附件状态</option>
              <option :value="true">已有附件</option>
              <option :value="false">缺少附件</option>
            </select>
            <div class="cash-journal-filter-actions">
              <a-button type="primary" @click="search">搜索</a-button>
              <a-button @click="resetFilters"><ReloadOutlined />重置</a-button>
            </div>
          </div>
        </section>

        <section class="lg-list-table-panel cash-journal-table-card">
          <div class="lg-toolbar table-heading">
            <div class="lg-toolbar-left">
              <strong>收支流水</strong>
              <span>共 {{ total }} 条</span>
            </div>
            <div class="lg-toolbar-right">
              <a-button v-if="canManageAccount" @click="openAccountManager"
                ><SettingOutlined />资金账户管理</a-button
              >
              <a-button v-if="canExport" @click="exportRows"><DownloadOutlined />导出</a-button>
              <a-button @click="loadRows"><ReloadOutlined />刷新</a-button>
              <a-button
                v-if="canMaintain"
                data-testid="create-entry-button"
                type="primary"
                @click="openCreate"
                ><PlusOutlined />登记流水</a-button
              >
            </div>
          </div>
          <div class="lg-table-wrap cash-journal-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>流水号</th>
                  <th>业务日期</th>
                  <th>账户</th>
                  <th>收入</th>
                  <th>支出</th>
                  <th>余额</th>
                  <th>往来单位</th>
                  <th>项目 / 合同</th>
                  <th>来源</th>
                  <th>附件</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in rows"
                  :key="row.id"
                  tabindex="0"
                  @click="loadDetail(row.id)"
                  @keyup.enter="loadDetail(row.id)"
                >
                  <td>
                    <a-button type="link" size="small">{{ row.entryNo }}</a-button>
                  </td>
                  <td>{{ row.businessDate }}</td>
                  <td>{{ row.accountName || '待选择' }}</td>
                  <td class="amount-in">{{ row.direction === 'IN' ? money(row.amount) : '-' }}</td>
                  <td class="amount-out">
                    {{ row.direction === 'OUT' ? money(row.amount) : '-' }}
                  </td>
                  <td>{{ row.runningBalance ? money(row.runningBalance) : '-' }}</td>
                  <td>{{ row.counterpartyName || '-' }}</td>
                  <td>
                    {{ projectNames.get(row.projectId || '') || '-' }} /
                    {{ contractNames.get(row.contractId || '') || '-' }}
                  </td>
                  <td>
                    {{
                      row.sourceType === 'PAY_RECORD'
                        ? '付款回写'
                        : row.sourceType === 'REVERSAL'
                          ? '红冲'
                          : '手工登记'
                    }}
                  </td>
                  <td>{{ row.attachmentCount || 0 }} 个</td>
                  <td>
                    <a-tag>{{ statusLabel(row.status) }}</a-tag>
                  </td>
                </tr>
                <tr v-if="!loading && !rows.length">
                  <td colspan="11" class="empty-row">暂无符合条件的资金流水</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="lg-pagination cash-journal-pagination">
            <span>共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              show-size-changer
              @change="loadRows"
            />
          </div>
        </section>
      </div>

      <aside class="lg-analysis-rail cash-journal-analysis-rail" aria-label="资金辅助分析">
        <div class="lg-analysis-panel cash-journal-analysis-panel">
          <div class="cash-journal-analysis-head">
            <div>
              <strong>资金概览</strong>
              <span>余额、收支结构与归档提醒</span>
            </div>
          </div>

          <section class="cash-journal-analysis-section">
            <div class="cash-journal-section-title">账户余额</div>
            <strong class="cash-journal-balance">{{ money(String(totalBalance)) }}</strong>
            <span>共 {{ accounts.length }} 个可用资金账户</span>
          </section>

          <section class="cash-journal-analysis-section">
            <div class="cash-journal-section-title">本期收支结构</div>
            <div class="cash-journal-ratio-row">
              <span>收入</span><strong>{{ incomeRatio }}%</strong>
            </div>
            <div class="cash-journal-ratio-track">
              <span class="is-income" :style="{ width: `${incomeRatio}%` }"></span>
            </div>
            <div class="cash-journal-ratio-row">
              <span>支出</span><strong>{{ expenseRatio }}%</strong>
            </div>
            <div class="cash-journal-ratio-track">
              <span class="is-expense" :style="{ width: `${expenseRatio}%` }"></span>
            </div>
          </section>

          <section class="cash-journal-analysis-section">
            <div class="cash-journal-section-title">当前页业务构成</div>
            <div class="cash-journal-analysis-stat">
              <span>已归档流水</span><strong>{{ archivedCount }} 条</strong>
            </div>
            <div class="cash-journal-analysis-stat">
              <span>付款回写流水</span><strong>{{ linkedPaymentCount }} 条</strong>
            </div>
          </section>

          <section
            class="cash-journal-archive-note"
            :class="{ 'has-pending': summary.pendingCount > 0 }"
          >
            <strong>{{ summary.pendingCount }} 条待归档</strong>
            <span>归档前请确认业务来源及附件完整性。</span>
          </section>
        </div>
      </aside>
    </div>

    <CashJournalDetailDrawer
      :open="Boolean(activeEntry)"
      :entry="activeEntry"
      :can-maintain="canMaintain"
      :is-super-admin="isSuperAdmin"
      @close="activeEntry = null"
      @edit="openEdit"
      @archive="archiveEntry"
      @reverse="reverseEntry"
      @reopen="reopenEntry"
      @upload="uploadAttachment"
      @delete-file="removeAttachment"
      @download-file="downloadAttachment"
      @open-source="openSource"
    />
    <CashJournalFormModal
      :open="formOpen"
      :entry="editingEntry"
      :accounts="accounts"
      :projects="projects"
      :contracts="contracts"
      :saving="saving"
      @close="formOpen = false"
      @submit="saveEntry"
      @project-change="handleProjectChange"
    />
    <FundAccountModal
      :open="accountModalOpen"
      :accounts="manageableAccounts"
      :saving="saving"
      @close="accountModalOpen = false"
      @save="saveAccount"
      @toggle="toggleAccount"
    />
  </div>
</template>

<style scoped>
.cash-journal-page {
  min-width: 0;
  background: var(--surface-subtle);
}

.cash-journal-page-head {
  align-items: center;
  min-height: 0;
  padding: 0;
}

.cash-journal-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.cash-journal-workspace {
  align-items: stretch;
}

.cash-journal-main-column {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.cash-journal-kpis {
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

.cash-journal-kpi-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 10px 18px;
}

.cash-journal-kpi-item + .cash-journal-kpi-item {
  border-left: 1px solid var(--border-subtle);
}

.cash-journal-kpi-content {
  min-width: 0;
}

.cash-journal-kpi-content > span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.cash-journal-kpi-content strong {
  display: block;
  margin-top: 4px;
  color: var(--text);
  font-size: 24px;
  line-height: 1;
}

.cash-journal-kpi-content small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.cash-journal-kpi-icon {
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

.cash-journal-kpi-icon.is-bank,
.cash-journal-kpi-icon.is-expense {
  color: var(--warning);
  background: var(--warning-soft);
}

.cash-journal-kpi-icon.is-income {
  color: var(--success);
  background: var(--success-soft);
}

.cash-journal-kpi-icon.is-pending {
  color: var(--error);
  background: var(--error-soft);
}

.cash-journal-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  width: 100%;
  margin: 0;
}

.cash-journal-filter-grid,
.cash-journal-filter-foot {
  display: grid;
  flex: 1 0 100%;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.cash-journal-filter-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.cash-journal-filter-foot {
  grid-template-columns: minmax(190px, 1.7fr) repeat(4, minmax(110px, 1fr)) auto;
  gap: 10px;
}

.cash-journal-filter-grid select {
  width: 100%;
}

.cash-journal-filter-foot > input:not(.cash-journal-keyword),
.cash-journal-filter-foot > select {
  width: 100%;
}

.cash-journal-keyword {
  width: 100%;
}

.cash-journal-filter-grid select,
.cash-journal-filter-foot input,
.cash-journal-filter-foot select {
  min-width: 0;
  min-height: 40px;
  padding: 8px 12px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
}

.cash-journal-filter-grid select:focus,
.cash-journal-filter-foot input:focus,
.cash-journal-filter-foot select:focus {
  border-color: var(--primary);
  outline: 0;
  box-shadow: 0 0 0 2px var(--primary-soft);
}

.cash-journal-filter-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.cash-journal-table-card {
  min-width: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.table-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0;
  border-bottom: 1px solid var(--border-subtle);
}

.table-heading .lg-toolbar-left {
  gap: 8px;
}

.table-heading .lg-toolbar-left span {
  color: var(--text-secondary);
  font-size: 13px;
}

.table-heading .lg-toolbar-right {
  flex-wrap: wrap;
}

.cash-journal-table-wrap {
  flex: 1;
  width: 100%;
  max-width: 100%;
  min-height: 0;
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 1240px;
  border-collapse: collapse;
}

th,
td {
  height: 52px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-subtle);
  text-align: left;
  white-space: nowrap;
}

th {
  color: var(--text-secondary);
  background: var(--surface-subtle);
  font-size: 13px;
  font-weight: 500;
}

tbody tr {
  cursor: pointer;
}

tbody tr:hover {
  background: var(--primary-soft);
}

.amount-in {
  color: var(--success);
}

.amount-out {
  color: var(--danger);
}

.empty-row {
  padding: 36px;
  text-align: center;
  color: var(--text-secondary);
}

.cash-journal-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--border-subtle);
}

.cash-journal-pagination > span {
  color: var(--text-secondary);
  font-size: 13px;
}

.cash-journal-analysis-rail {
  display: flex !important;
}

.cash-journal-analysis-panel {
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

.cash-journal-analysis-head {
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.cash-journal-analysis-section,
.cash-journal-archive-note {
  padding: 10px 16px 0;
}

.cash-journal-analysis-section + .cash-journal-analysis-section,
.cash-journal-archive-note {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.cash-journal-analysis-head strong,
.cash-journal-section-title {
  color: var(--text-primary);
  font-weight: 700;
}

.cash-journal-analysis-head span,
.cash-journal-analysis-section > span,
.cash-journal-archive-note span {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 18px;
}

.cash-journal-balance {
  display: block;
  margin-top: 6px;
  color: var(--primary);
  font-size: 20px;
}

.cash-journal-ratio-row,
.cash-journal-analysis-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.cash-journal-ratio-row strong,
.cash-journal-analysis-stat strong {
  color: var(--text-primary);
}

.cash-journal-ratio-track {
  height: 6px;
  margin-top: 6px;
  overflow: hidden;
  background: var(--surface-subtle);
  border-radius: 999px;
}

.cash-journal-ratio-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.cash-journal-ratio-track .is-income {
  background: var(--success);
}

.cash-journal-ratio-track .is-expense {
  background: var(--warning);
}

.cash-journal-archive-note {
  background: transparent;
}

.cash-journal-archive-note.has-pending {
  color: var(--warning);
}

.cash-journal-archive-note strong {
  color: var(--text-primary);
}

@media (max-width: 1200px) {
  .cash-journal-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cash-journal-kpi-item:nth-child(odd) {
    border-left: 0;
  }

  .cash-journal-analysis-rail {
    width: 100%;
  }

  .cash-journal-filter-grid,
  .cash-journal-filter-foot {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cash-journal-filter-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .cash-journal-kpis {
    grid-template-columns: 1fr;
  }

  .cash-journal-kpi-item + .cash-journal-kpi-item {
    border-top: 1px solid var(--border-subtle);
    border-left: 0;
  }

  .cash-journal-filter-grid,
  .cash-journal-filter-foot {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .cash-journal-filter-grid select,
  .cash-journal-filter-foot > input,
  .cash-journal-filter-foot > select,
  .cash-journal-keyword {
    width: 100%;
    flex: 0 0 auto;
  }

  .cash-journal-filter-actions {
    grid-column: auto;
    width: 100%;
    margin-left: 0;
  }

  .cash-journal-filter-actions :deep(.ant-btn) {
    flex: 1;
  }

  .cash-journal-table-wrap {
    overflow-x: auto;
  }

  .cash-journal-pagination {
    align-items: flex-start;
  }
}
</style>
