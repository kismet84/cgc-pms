<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DownloadOutlined, PlusOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons-vue'
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
  cashBalance: '0.00', bankBalance: '0.00', income: '0.00', expense: '0.00', pendingCount: 0,
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
const canMaintain = computed(() => isAdmin.value || userStore.hasPermission('cashbook:journal:maintain'))
const canExport = computed(() => isAdmin.value || userStore.hasPermission('cashbook:journal:export'))
const canManageAccount = computed(() => isAdmin.value || userStore.hasPermission('cashbook:account:manage'))
const canQueryProject = computed(() => isAdmin.value || userStore.hasPermission('project:query'))
const canQueryContract = computed(() => isAdmin.value || userStore.hasPermission('contract:query'))
const projects = computed(() => canQueryProject.value ? (referenceStore.projects ?? []) : [])
const contracts = computed(() => canQueryContract.value ? (referenceStore.contracts ?? []) : [])

const projectNames = computed(() => new Map(projects.value.map((item) => [item.id, item.projectName])))
const contractNames = computed(() => new Map(contracts.value.map((item) => [item.id, item.contractName])))

const kpis = computed(() => [
  { label: '现金余额', value: money(summary.value.cashBalance), tone: 'cash' },
  { label: '银行余额', value: money(summary.value.bankBalance), tone: 'bank' },
  { label: '本期收入', value: money(summary.value.income), tone: 'income' },
  { label: '本期支出', value: money(summary.value.expense), tone: 'expense' },
  { label: '待归档', value: String(summary.value.pendingCount), tone: 'pending' },
])

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
    Object.entries({ ...filter, pageNo: pageNo.value, pageSize: pageSize.value })
      .filter(([, value]) => value !== undefined && value !== ''),
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
    accountId: undefined, businessDateStart: undefined, businessDateEnd: undefined,
    direction: undefined, projectId: undefined, contractId: undefined,
    sourceType: undefined, sourceId: undefined, status: undefined,
    hasAttachment: undefined, keyword: undefined,
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
      getFundAccounts(), getManageableFundAccounts(),
    ])
  } finally {
    saving.value = false
  }
}

async function toggleAccount(account: FundAccountVO) {
  await setFundAccountEnabled(account.id, account.enabledFlag !== 1)
  ;[accounts.value, manageableAccounts.value] = await Promise.all([
    getFundAccounts(), getManageableFundAccounts(),
  ])
}

function openSource(entry: CashJournalEntryVO) {
  void router.push({ path: '/payment/application', query: { payRecordId: entry.sourceId } })
}

function money(value?: string) {
  return `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function statusLabel(status: CashJournalEntryVO['status']) {
  return { DRAFT: '草稿', PENDING_ARCHIVE: '待归档', ARCHIVED: '已归档', REVERSED: '已红冲' }[status]
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
  <div class="lg-page app-page cash-journal-page">
    <header class="cash-journal-header">
      <div>
        <h1>资金日记账</h1>
        <p>现金与银行账户统一收支，附件归档后计入实时余额。</p>
      </div>
      <div class="cash-journal-primary-actions">
        <a-button v-if="canManageAccount" @click="openAccountManager"><SettingOutlined />资金账户管理</a-button>
        <a-button v-if="canExport" @click="exportRows"><DownloadOutlined />导出</a-button>
        <a-button v-if="canMaintain" data-testid="create-entry-button" type="primary" @click="openCreate"><PlusOutlined />登记流水</a-button>
      </div>
    </header>

    <section class="cash-journal-kpis">
      <article v-for="item in kpis" :key="item.label" :class="`kpi-${item.tone}`">
        <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
      </article>
    </section>

    <details class="cash-journal-filters" open>
      <summary>筛选条件</summary>
      <div class="filter-grid">
        <input v-model="filter.keyword" placeholder="流水号 / 摘要 / 往来单位" @keyup.enter="search" />
        <select v-model="filter.accountId"><option :value="undefined">全部账户</option><option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.accountName }}</option></select>
        <input v-model="filter.businessDateStart" type="date" aria-label="开始日期" />
        <input v-model="filter.businessDateEnd" type="date" aria-label="结束日期" />
        <select v-model="filter.direction"><option :value="undefined">全部方向</option><option value="IN">收入</option><option value="OUT">支出</option></select>
        <select v-if="canQueryProject" v-model="filter.projectId" data-testid="project-filter" @change="handleProjectChange(filter.projectId)"><option :value="undefined">全部项目</option><option v-for="project in projects" :key="project.id" :value="project.id">{{ project.projectName }}</option></select>
        <select v-if="canQueryContract" v-model="filter.contractId" data-testid="contract-filter"><option :value="undefined">全部合同</option><option v-for="contract in contracts" :key="contract.id" :value="contract.id">{{ contract.contractName }}</option></select>
        <select v-model="filter.sourceType"><option :value="undefined">全部来源</option><option value="MANUAL">手工登记</option><option value="PAY_RECORD">付款回写</option><option value="REVERSAL">红冲</option></select>
        <select v-model="filter.status"><option :value="undefined">全部状态</option><option value="DRAFT">草稿</option><option value="PENDING_ARCHIVE">待归档</option><option value="ARCHIVED">已归档</option><option value="REVERSED">已红冲</option></select>
        <select v-model="filter.hasAttachment"><option :value="undefined">全部附件状态</option><option :value="true">已有附件</option><option :value="false">缺少附件</option></select>
      </div>
      <div class="filter-actions"><a-button @click="resetFilters">重置</a-button><a-button type="primary" @click="search">查询</a-button></div>
    </details>

    <section class="cash-journal-table-card">
      <div class="table-heading"><strong>收支流水</strong><a-button size="small" @click="loadRows"><ReloadOutlined />刷新</a-button></div>
      <div class="cash-journal-table-wrap">
        <table>
          <thead><tr><th>流水号</th><th>业务日期</th><th>账户</th><th>收入</th><th>支出</th><th>余额</th><th>往来单位</th><th>项目 / 合同</th><th>来源</th><th>附件</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id" tabindex="0" @click="loadDetail(row.id)" @keyup.enter="loadDetail(row.id)">
              <td><a-button type="link" size="small">{{ row.entryNo }}</a-button></td>
              <td>{{ row.businessDate }}</td>
              <td>{{ row.accountName || '待选择' }}</td>
              <td class="amount-in">{{ row.direction === 'IN' ? money(row.amount) : '-' }}</td>
              <td class="amount-out">{{ row.direction === 'OUT' ? money(row.amount) : '-' }}</td>
              <td>{{ row.runningBalance ? money(row.runningBalance) : '-' }}</td>
              <td>{{ row.counterpartyName || '-' }}</td>
              <td>{{ projectNames.get(row.projectId || '') || '-' }} / {{ contractNames.get(row.contractId || '') || '-' }}</td>
              <td>{{ row.sourceType === 'PAY_RECORD' ? '付款回写' : row.sourceType === 'REVERSAL' ? '红冲' : '手工登记' }}</td>
              <td>{{ row.attachmentCount || 0 }} 个</td>
              <td><a-tag>{{ statusLabel(row.status) }}</a-tag></td>
            </tr>
            <tr v-if="!loading && !rows.length"><td colspan="11" class="empty-row">暂无符合条件的资金流水</td></tr>
          </tbody>
        </table>
      </div>
      <a-pagination v-model:current="pageNo" v-model:page-size="pageSize" :total="total" show-size-changer @change="loadRows" />
    </section>

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
.cash-journal-page { display: grid; min-width: 0; grid-template-columns: minmax(0, 1fr); gap: 16px; }
.cash-journal-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.cash-journal-header h1 { margin: 0; font-size: 24px; }
.cash-journal-header p { margin: 5px 0 0; color: #667085; }
.cash-journal-primary-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.cash-journal-kpis { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); border: 1px solid #eaecf0; border-radius: 10px; background: #fff; overflow: hidden; }
.cash-journal-kpis article { display: grid; gap: 6px; padding: 16px; border-right: 1px solid #eaecf0; }
.cash-journal-kpis article:last-child { border-right: 0; }
.cash-journal-kpis span { color: #667085; font-size: 13px; }
.cash-journal-kpis strong { font-size: 22px; }
.kpi-income strong { color: #087a55; }
.kpi-expense strong, .kpi-pending strong { color: #b54708; }
.cash-journal-filters, .cash-journal-table-card { padding: 14px; border: 1px solid #eaecf0; border-radius: 10px; background: #fff; }
.cash-journal-table-card { min-width: 0; }
.cash-journal-filters summary { cursor: pointer; font-weight: 600; }
.filter-grid { display: grid; grid-template-columns: repeat(5, minmax(150px, 1fr)); gap: 10px; margin-top: 12px; }
.filter-grid input, .filter-grid select { min-height: 36px; padding: 6px 9px; border: 1px solid #d9d9d9; border-radius: 6px; background: #fff; }
.filter-actions, .table-heading { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.table-heading { align-items: center; justify-content: space-between; margin: 0 0 10px; }
.cash-journal-table-wrap { width: 100%; max-width: 100%; overflow-x: auto; }
table { width: 100%; min-width: 1240px; border-collapse: collapse; }
th, td { padding: 10px 8px; border-bottom: 1px solid #f0f0f0; text-align: left; white-space: nowrap; }
th { background: #f8fafc; color: #475467; font-size: 12px; }
tbody tr { cursor: pointer; }
tbody tr:hover { background: #f6faff; }
.amount-in { color: #087a55; }
.amount-out { color: #b42318; }
.empty-row { padding: 36px; text-align: center; color: #98a2b3; }
.cash-journal-table-card :deep(.ant-pagination) { justify-content: flex-end; margin-top: 14px; }
@media (max-width: 1000px) { .cash-journal-kpis { grid-template-columns: repeat(2, 1fr); } .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 440px) {
  .cash-journal-header { display: grid; grid-template-columns: minmax(0, 1fr); }
  .cash-journal-primary-actions { position: static; width: 100%; justify-content: flex-start; }
  .cash-journal-primary-actions :deep(.ant-btn) { flex: 1 1 132px; }
  .cash-journal-kpis { grid-template-columns: 1fr 1fr; }
  .cash-journal-kpis article { padding: 12px; }
  .filter-grid { grid-template-columns: minmax(0, 1fr); }
  .cash-journal-table-wrap { overflow-x: auto; }
}
</style>
