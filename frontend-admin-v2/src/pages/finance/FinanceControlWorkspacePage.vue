<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  V2ActionMenu,
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
} from '@/components'
import { showToast } from '@/components/toast'
import { formatAmount } from '@/pages/dashboard/model'
import { uploadSiteFile } from '@/services/delivery'
import {
  approveCashForecast,
  archiveCashJournal,
  closeFinancePeriod,
  createFundAccount,
  generateFinanceAlerts,
  handleFinanceAlert,
  loadAccountingEntries,
  loadAccountingEntryDetail,
  loadCashForecastCycles,
  loadCashForecastTrace,
  loadCashJournal,
  loadFinanceOperationsWorkspace,
  loadFinancePeriods,
  loadFinancialCloseTrace,
  loadFinancialStatement,
  loadFundAccounts,
  postAccountingEntry,
  rebuildFinanceSnapshot,
  regenerateCashForecast,
  reopenCashJournal,
  reopenFinancePeriod,
  resubmitAccountingEntry,
  reverseAccountingEntry,
  reverseCashJournal,
  reviewAccountingEntry,
  runFinancialCloseChecks,
  submitCashForecast,
  refreshCashForecastActuals,
} from '@/services/finance'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type {
  AccountingEntryDetail,
  AccountingEntryPage,
  AccountingEntryRecord,
  CashForecastCycleRecord,
  CashForecastTrace,
  CashJournalPage,
  FinanceOperationsWorkspace,
  FinancePeriodRecord,
  FinancialCloseTrace,
  FinancialStatement,
  FundAccountRecord,
  FundAccountCommand,
} from '@cgc-pms/frontend-contracts'

type Mode = 'operations' | 'journal' | 'forecast' | 'accounting' | 'close'
type JournalAction = 'archive' | 'reverse' | 'reopen'
type EntryAction = 'approve' | 'reject' | 'post' | 'resubmit' | 'reverse'
type PeriodAction = 'check' | 'close' | 'reopen'

interface FundAccountEditor {
  accountCode: string
  accountName: string
  accountType: 'CASH' | 'BANK'
  bankName: string
  bankAccountNo: string
  openingDate: string
  openingBalance: string
  remark: string
}

const route = useRoute()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const mode = computed<Mode>(() =>
  route.path === '/cash-journal'
    ? 'journal'
    : route.path === '/cash-forecast'
      ? 'forecast'
      : route.path === '/accounting-entry'
        ? 'accounting'
        : route.path === '/financial-close'
          ? 'close'
          : 'operations',
)
const titles: Record<Mode, string> = {
  operations: '资金运营',
  journal: '资金日记账',
  forecast: '资金预测',
  accounting: '会计凭证',
  close: '财务月结',
}
const permissions: Record<Mode, string> = {
  operations: 'finance:operations:query',
  journal: 'cashbook:journal:query',
  forecast: 'finance:forecast:query',
  accounting: 'accounting:query',
  close: 'finance:close:query',
}
const title = computed(() => titles[mode.value])
const canQuery = computed(() => session.hasPermission(permissions[mode.value]))
const projectId = computed(() => workspace.selectedProjectId || '')
const needsProject = computed(() => mode.value === 'operations' || mode.value === 'forecast')
const can = (permission: string) => session.hasPermission(permission)
const isSuperAdmin = computed(() => session.roles.includes('SUPER_ADMIN'))
const canManageAccounts = computed(
  () =>
    session.hasPermission('cashbook:account:manage') ||
    session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN'),
)

const operations = ref<FinanceOperationsWorkspace | null>(null)
const accounts = ref<FundAccountRecord[]>([])
const journalPageNo = ref(1)
const journal = ref<CashJournalPage>({ pageNo: 1, pageSize: 10, total: 0, records: [] })
const cycles = ref<CashForecastCycleRecord[]>([])
const forecastTrace = ref<CashForecastTrace | null>(null)
const entryPageNo = ref(1)
const entries = ref<AccountingEntryPage>({ pageNo: 1, pageSize: 10, total: 0, records: [] })
const entryDetail = ref<AccountingEntryDetail | null>(null)
const periods = ref<FinancePeriodRecord[]>([])
const closeTrace = ref<FinancialCloseTrace | null>(null)
const statement = ref<FinancialStatement | null>(null)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const accountDialog = ref(false)
const accountEditor = ref<FundAccountEditor | null>(null)
let controller: AbortController | null = null

const hasRows = computed(() => {
  if (mode.value === 'operations')
    return Boolean(
      operations.value &&
      (operations.value.schedules.length ||
        operations.value.alerts.length ||
        operations.value.snapshots.length),
    )
  if (mode.value === 'journal') return accounts.value.length > 0 || journal.value.records.length > 0
  if (mode.value === 'forecast') return cycles.value.length > 0
  if (mode.value === 'accounting') return entries.value.records.length > 0
  return periods.value.length > 0
})

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待处理',
  PENDING_ARCHIVE: '待归档',
  ARCHIVED: '已归档',
  REVERSED: '已冲销',
  SUBMITTED: '已提交',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  POSTED: '已过账',
  OPEN: '开放',
  CHECKING: '检查中',
  CLOSED: '已关账',
  REOPENED: '已反结账',
  PLANNED: '计划中',
  PARTIALLY_PAID: '部分付款',
  RESOLVED: '已解决',
  IGNORED: '已忽略',
  MATCHED: '已匹配',
  EXCEPTION: '异常',
  COMPLETED: '已完成',
  PROPOSED: '拟定',
  SUPERSEDED: '已滚动',
  BASE: '基准',
  OPTIMISTIC: '乐观',
  CONSERVATIVE: '保守',
  IN: '收入',
  OUT: '支出',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
  DEBIT: '借',
  CREDIT: '贷',
  PAYMENT_DUE: '付款到期',
  JOURNAL_ARCHIVE_OVERDUE: '日记账归档超时',
  INVOICE_MISSING: '发票缺失',
  ACCELERATE_COLLECTION: '加速回款',
  DEFER_PAYMENT: '延后付款',
  FUND_TRANSFER: '资金调拨',
  FINANCING: '融资补充',
}
const label = (value?: string | null) => (value ? statusLabels[value] || '状态待确认' : '—')
const amount = (value?: string | null) => (value == null ? '—' : formatAmount(value))
const projectRequired = () => {
  if (!projectId.value) {
    showToast('error', '请选择项目', '该工作台仅支持单项目范围。')
    return false
  }
  return true
}
const askReason = (message: string) => {
  const value = window.prompt(message, 'V2工作台人工操作')
  return value?.trim() || ''
}

function openFundAccount(): void {
  accountEditor.value = {
    accountCode: '',
    accountName: '',
    accountType: 'BANK',
    bankName: '',
    bankAccountNo: '',
    openingDate: new Date().toISOString().slice(0, 10),
    openingBalance: '0.00',
    remark: '',
  }
  accountDialog.value = true
}

function closeFundAccount(): void {
  if (busy.value) return
  accountDialog.value = false
  accountEditor.value = null
}

async function saveFundAccount(): Promise<void> {
  const value = accountEditor.value
  if (!value) return
  const required = [
    ['账户编码', value.accountCode],
    ['账户名称', value.accountName],
    ['开户日期', value.openingDate],
    ['期初余额', value.openingBalance],
  ].find(([, field]) => !field?.trim())
  if (required) {
    showToast('error', '资金账户保存失败', `${required[0]}不能为空。`)
    return
  }
  const command: FundAccountCommand = {
    accountCode: value.accountCode.trim(),
    accountName: value.accountName.trim(),
    accountType: value.accountType,
    bankName: value.bankName.trim() || undefined,
    bankAccountNo: value.bankAccountNo.trim() || undefined,
    openingDate: value.openingDate,
    openingBalance: value.openingBalance.trim(),
    remark: value.remark.trim() || undefined,
  }
  busy.value = true
  try {
    await createFundAccount(command)
    accountDialog.value = false
    accountEditor.value = null
    await load()
    showToast('success', '资金账户已创建', '资金账户列表已刷新。')
  } catch (cause) {
    showToast('error', '资金账户保存失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

async function load(): Promise<void> {
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    if (needsProject.value && !projectId.value) return
    if (mode.value === 'operations') {
      operations.value = await loadFinanceOperationsWorkspace(projectId.value, request.signal)
    } else if (mode.value === 'journal') {
      const [accountRows, journalPage] = await Promise.all([
        loadFundAccounts(request.signal),
        loadCashJournal(
          { pageNo: journalPageNo.value, pageSize: 10, projectId: projectId.value },
          request.signal,
        ),
      ])
      accounts.value = accountRows
      journal.value = journalPage
    } else if (mode.value === 'forecast') {
      cycles.value = await loadCashForecastCycles(projectId.value, request.signal)
      const selected =
        cycles.value.find((row) => row.id === forecastTrace.value?.cycle.id) || cycles.value[0]
      forecastTrace.value = selected
        ? await loadCashForecastTrace(selected.id, request.signal)
        : null
    } else if (mode.value === 'accounting') {
      entries.value = await loadAccountingEntries(
        { pageNo: entryPageNo.value, pageSize: 10, projectId: projectId.value },
        request.signal,
      )
      const selected =
        entries.value.records.find((row) => row.id === entryDetail.value?.entry.id) ||
        entries.value.records[0]
      entryDetail.value = selected
        ? await loadAccountingEntryDetail(selected.id, request.signal)
        : null
    } else {
      periods.value = await loadFinancePeriods(undefined, request.signal)
      const selected =
        periods.value.find((row) => row.id === closeTrace.value?.period.id) || periods.value[0]
      if (selected) {
        ;[closeTrace.value, statement.value] = await Promise.all([
          loadFinancialCloseTrace(selected.id, request.signal),
          loadFinancialStatement(selected.fiscalYear, selected.fiscalMonth, request.signal),
        ])
      } else {
        closeTrace.value = null
        statement.value = null
      }
    }
  } catch (cause) {
    if (!request.signal.aborted)
      errorMessage.value = cause instanceof Error ? cause.message : '请求失败，请稍后重试。'
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

async function selectForecast(id: string): Promise<void> {
  forecastTrace.value = await loadCashForecastTrace(id)
}
async function selectEntry(id: string): Promise<void> {
  entryDetail.value = await loadAccountingEntryDetail(id)
}
async function selectPeriod(row: FinancePeriodRecord): Promise<void> {
  ;[closeTrace.value, statement.value] = await Promise.all([
    loadFinancialCloseTrace(row.id),
    loadFinancialStatement(row.fiscalYear, row.fiscalMonth),
  ])
}

function changePage(kind: 'journal' | 'accounting', next: number): void {
  const page = kind === 'journal' ? journal.value : entries.value
  if (next < 1 || (next - 1) * 10 >= page.total) return
  if (kind === 'journal') journalPageNo.value = next
  else entryPageNo.value = next
  void load()
}

async function run(action: () => Promise<unknown>, success: string): Promise<void> {
  busy.value = true
  try {
    await action()
    await load()
    showToast('success', success, '已读取最新数据。')
  } catch (cause) {
    showToast('error', '操作失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}
async function refreshSnapshot(): Promise<void> {
  if (projectRequired()) await run(() => rebuildFinanceSnapshot(projectId.value), '财务快照已刷新')
}
async function refreshWorkspace(): Promise<void> {
  await load()
  if (!errorMessage.value) showToast('success', '刷新完成', '已读取最新数据。')
}
async function generateAlerts(): Promise<void> {
  await run(generateFinanceAlerts, '资金预警已生成')
}
async function resolveAlert(id: string): Promise<void> {
  const reason = askReason('请输入预警处理说明')
  if (reason) await run(() => handleFinanceAlert(id, 'RESOLVED', reason), '预警已处理')
}
async function actJournal(row: CashJournalPage['records'][number], action: JournalAction) {
  if (action === 'archive') return run(() => archiveCashJournal(row.id), '流水已归档')
  const reason = askReason(action === 'reverse' ? '请输入冲销原因' : '请输入撤销归档原因')
  if (!reason) return
  await run(
    () =>
      action === 'reverse' ? reverseCashJournal(row.id, reason) : reopenCashJournal(row.id, reason),
    action === 'reverse' ? '流水已冲销' : '流水已重开',
  )
}
async function uploadJournalEvidence(
  row: CashJournalPage['records'][number],
  event: Event,
): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  busy.value = true
  try {
    await uploadSiteFile(file, 'CASH_JOURNAL', row.id, 'BANK_RECEIPT')
    showToast('success', '银行回单已上传', '病毒扫描通过后可归档资金流水。')
  } catch (cause) {
    showToast('error', '银行回单上传失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    input.value = ''
    busy.value = false
  }
}
async function actForecast(action: 'regenerate' | 'submit' | 'approve' | 'reject' | 'refresh') {
  const id = forecastTrace.value?.cycle.id
  if (!id) return
  if (action === 'regenerate') return run(() => regenerateCashForecast(id), '预测已重算')
  if (action === 'submit') return run(() => submitCashForecast(id), '预测已提交')
  if (action === 'refresh') return run(() => refreshCashForecastActuals(id), '预测实际收付已回写')
  const comment = askReason(action === 'approve' ? '请输入批准意见' : '请输入驳回意见')
  if (comment)
    await run(() => approveCashForecast(id, action === 'approve', comment), '预测审批已完成')
}
async function actEntry(row: AccountingEntryRecord, action: EntryAction) {
  if (action === 'approve')
    return run(() => reviewAccountingEntry(row.id, true, '复核通过'), '凭证已复核')
  if (action === 'post') return run(() => postAccountingEntry(row.id), '凭证已过账')
  if (action === 'resubmit') return run(() => resubmitAccountingEntry(row.id), '凭证已重新提交')
  const reason = askReason(action === 'reject' ? '请输入驳回原因' : '请输入冲销原因')
  if (!reason) return
  await run(
    () =>
      action === 'reject'
        ? reviewAccountingEntry(row.id, false, reason)
        : reverseAccountingEntry(row.id, reason),
    action === 'reject' ? '凭证已驳回' : '冲销凭证已生成',
  )
}
async function actPeriod(row: FinancePeriodRecord, action: PeriodAction) {
  if (action === 'check')
    return run(() => runFinancialCloseChecks(row.fiscalYear, row.fiscalMonth), '月结检查已完成')
  const reason = askReason(action === 'close' ? '请输入关账说明' : '请输入反结账原因')
  if (!reason) return
  await run(
    () =>
      action === 'close'
        ? closeFinancePeriod(row.fiscalYear, row.fiscalMonth, reason)
        : reopenFinancePeriod(row.fiscalYear, row.fiscalMonth, reason),
    action === 'close' ? '期间已关账' : '期间已反结账',
  )
}

watch(
  [mode, projectId],
  () => {
    journalPageNo.value = 1
    entryPageNo.value = 1
    void load()
  },
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-control">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      :title="`无权访问${title}`"
      description="系统未加载任何财务数据。"
    />
    <template v-else>
      <V2Card :title="title" :heading-level="1">
        <template #actions>
          <div class="finance-control__actions">
            <V2Button
              v-if="mode === 'operations' && can('finance:analytics:maintain')"
              size="small"
              @click="refreshSnapshot"
            >
              刷新快照
            </V2Button>
            <V2Button
              v-if="mode === 'operations' && can('finance:operations:maintain')"
              size="small"
              variant="secondary"
              @click="generateAlerts"
            >
              生成预警
            </V2Button>
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace">
              刷新
            </V2Button>
          </div>
        </template>
      </V2Card>

      <V2PageState
        v-if="needsProject && !projectId"
        title="需切换至单项目"
        description="资金运营和预测必须按单项目范围读取。"
      />
      <V2PageState
        v-else-if="loading && !hasRows"
        kind="loading"
        title="正在加载"
        :description="`正在读取${title}。`"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        :title="`${title}加载失败`"
        :description="errorMessage"
      >
        <template #actions><V2Button @click="load">重试</V2Button></template>
      </V2PageState>
      <V2PageState
        v-else-if="!errorMessage && !hasRows && mode !== 'journal'"
        :title="`暂无${title}记录`"
        description="当前范围暂无可访问记录。"
      />

      <template v-else-if="mode === 'operations' && operations">
        <V2Card title="资金快照" :heading-level="2">
          <div class="finance-control__metrics" v-if="operations.snapshots[0]">
            <div>
              <span>合同额</span
              ><strong>{{ amount(operations.snapshots[0].contractAmount) }}</strong>
            </div>
            <div>
              <span>已付</span><strong>{{ amount(operations.snapshots[0].paidAmount) }}</strong>
            </div>
            <div>
              <span>流入</span><strong>{{ amount(operations.snapshots[0].cashInflow) }}</strong>
            </div>
            <div>
              <span>流出</span><strong>{{ amount(operations.snapshots[0].cashOutflow) }}</strong>
            </div>
            <div>
              <span>实际成本</span><strong>{{ amount(operations.snapshots[0].actualCost) }}</strong>
            </div>
            <div>
              <span>现金利润</span
              ><strong>{{ amount(operations.snapshots[0].profitAmount) }}</strong>
            </div>
          </div>
          <V2PageState
            v-else-if="!errorMessage"
            title="暂无资金快照"
            description="可使用重建快照动作生成最新快照。"
          />
        </V2Card>
        <V2Card title="付款计划" :heading-level="2">
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="付款计划表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>计划名称</th>
                  <th>计划日期</th>
                  <th>计划金额</th>
                  <th>已付金额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in operations.schedules" :key="row.id">
                  <td>{{ row.scheduleName }}</td>
                  <td>{{ row.plannedDate }}</td>
                  <td>{{ amount(row.plannedAmount) }}</td>
                  <td>{{ amount(row.paidAmount) }}</td>
                  <td>{{ label(row.status) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
        <V2Card title="资金预警" :heading-level="2">
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="资金预警表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>预警</th>
                  <th>等级</th>
                  <th>到期日期</th>
                  <th>状态</th>
                  <th class="v2-table-cell--actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in operations.alerts" :key="row.id">
                  <td>{{ row.message }}</td>
                  <td>{{ label(row.severity) }}</td>
                  <td>{{ row.dueAt || '—' }}</td>
                  <td>{{ label(row.status) }}</td>
                  <td class="v2-table-cell--actions">
                    <V2ActionMenu
                      :label="`${row.message}更多操作`"
                      :placement="index >= operations.alerts.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <V2Button
                        v-if="row.status === 'OPEN' && can('finance:operations:maintain')"
                        size="small"
                        variant="ghost"
                        @click="resolveAlert(row.id)"
                        >处理</V2Button
                      >
                    </V2ActionMenu>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
      </template>

      <template v-else-if="mode === 'journal'">
        <V2Card title="资金账户" :heading-level="2">
          <template #actions>
            <V2Button v-if="canManageAccounts" size="small" @click="openFundAccount">
              新建资金账户
            </V2Button>
          </template>
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="资金账户表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>账户编码</th>
                  <th>账户名称</th>
                  <th>开户行</th>
                  <th>期初余额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in accounts" :key="row.id">
                  <td>{{ row.accountCode }}</td>
                  <td>{{ row.accountName }}</td>
                  <td>{{ row.bankName || '—' }}</td>
                  <td>{{ amount(row.openingBalance) }}</td>
                  <td>{{ row.enabledFlag === 1 ? '启用' : '停用' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
        <V2Card title="资金流水" :heading-level="2">
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="资金流水表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>流水号</th>
                  <th>日期</th>
                  <th>方向</th>
                  <th>金额</th>
                  <th>余额</th>
                  <th>状态</th>
                  <th class="v2-table-cell--actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in journal.records" :key="row.id">
                  <td>{{ row.entryNo }}</td>
                  <td>{{ row.businessDate }}</td>
                  <td>{{ label(row.direction) }}</td>
                  <td>{{ amount(row.amount) }}</td>
                  <td>{{ amount(row.runningBalance) }}</td>
                  <td>{{ label(row.status) }}</td>
                  <td class="v2-table-cell--actions">
                    <V2ActionMenu
                      :label="`${row.entryNo}更多操作`"
                      :placement="index >= journal.records.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <label
                        v-if="
                          ['DRAFT', 'PENDING_ARCHIVE'].includes(row.status) &&
                          (can('file:upload') || can('cashbook:journal:maintain'))
                        "
                        class="v2-action-menu__item"
                      >
                        <span>上传银行回单</span>
                        <input
                          class="v2-visually-hidden"
                          type="file"
                          accept=".pdf,image/*"
                          :disabled="busy"
                          @change="uploadJournalEvidence(row, $event)"
                        />
                      </label>
                      <V2Button
                        v-if="
                          ['DRAFT', 'PENDING_ARCHIVE'].includes(row.status) &&
                          can('cashbook:journal:maintain')
                        "
                        size="small"
                        variant="ghost"
                        @click="actJournal(row, 'archive')"
                        >归档</V2Button
                      >
                      <V2Button
                        v-if="row.status === 'ARCHIVED' && can('cashbook:journal:maintain')"
                        size="small"
                        variant="ghost"
                        @click="actJournal(row, 'reverse')"
                        >冲销</V2Button
                      >
                      <V2Button
                        v-if="row.status === 'ARCHIVED' && isSuperAdmin"
                        size="small"
                        variant="ghost"
                        @click="actJournal(row, 'reopen')"
                        >重开</V2Button
                      >
                    </V2ActionMenu>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer>
            <div class="finance-control__pagination">
              <span>共 {{ journal.total }} 条</span>
              <V2Button
                size="small"
                variant="secondary"
                :disabled="journalPageNo === 1"
                @click="changePage('journal', journalPageNo - 1)"
              >
                上一页
              </V2Button>
              <span>第 {{ journalPageNo }} 页</span>
              <V2Button
                size="small"
                variant="secondary"
                :disabled="journalPageNo * 10 >= journal.total"
                @click="changePage('journal', journalPageNo + 1)"
              >
                下一页
              </V2Button>
            </div>
          </template>
        </V2Card>
      </template>

      <template v-else-if="mode === 'forecast'">
        <V2Card title="预测版本" :heading-level="2">
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="预测版本表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>版本编号</th>
                  <th>场景</th>
                  <th>区间</th>
                  <th>期初余额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in cycles" :key="row.id">
                  <td>
                    <V2Button size="small" variant="ghost" @click="selectForecast(row.id)">{{
                      row.cycleCode
                    }}</V2Button>
                  </td>
                  <td>{{ label(row.scenario) }}</td>
                  <td>{{ row.horizonStart }} 至 {{ row.horizonEnd }}</td>
                  <td>{{ amount(row.openingBalance) }}</td>
                  <td>{{ label(row.status) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
        <V2Card
          v-if="forecastTrace"
          :title="`预测明细 · ${forecastTrace.cycle.cycleCode}`"
          :heading-level="2"
        >
          <template #actions>
            <div class="finance-control__actions">
              <V2Button
                v-if="forecastTrace.cycle.status === 'DRAFT' && can('finance:forecast:maintain')"
                size="small"
                variant="secondary"
                @click="actForecast('regenerate')"
                >重算</V2Button
              >
              <V2Button
                v-if="forecastTrace.cycle.status === 'DRAFT' && can('finance:forecast:submit')"
                size="small"
                @click="actForecast('submit')"
                >提交</V2Button
              >
              <V2Button
                v-if="forecastTrace.cycle.status === 'SUBMITTED' && can('finance:forecast:approve')"
                size="small"
                @click="actForecast('approve')"
                >批准</V2Button
              >
              <V2Button
                v-if="forecastTrace.cycle.status === 'SUBMITTED' && can('finance:forecast:approve')"
                size="small"
                variant="danger"
                @click="actForecast('reject')"
                >驳回</V2Button
              >
              <V2Button
                v-if="forecastTrace.cycle.status === 'APPROVED' && can('finance:forecast:refresh')"
                size="small"
                @click="actForecast('refresh')"
                >回写实际收付</V2Button
              >
            </div>
          </template>
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="预测日明细表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__wide-table">
              <thead>
                <tr>
                  <th>日期</th>
                  <th>计划流入</th>
                  <th>计划流出</th>
                  <th>融资</th>
                  <th>预测余额</th>
                  <th>缺口</th>
                  <th>实际流入</th>
                  <th>实际流出</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in forecastTrace.lines" :key="row.id">
                  <td>{{ row.forecastDate }}</td>
                  <td>{{ amount(row.plannedInflow) }}</td>
                  <td>{{ amount(row.plannedOutflow) }}</td>
                  <td>{{ amount(row.financingAmount) }}</td>
                  <td>{{ amount(row.projectedBalance) }}</td>
                  <td>{{ amount(row.gapAmount) }}</td>
                  <td>{{ amount(row.actualInflow) }}</td>
                  <td>{{ amount(row.actualOutflow) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <h3>缺口措施（{{ forecastTrace.actions.length }}）</h3>
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="资金措施表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>计划日期</th>
                  <th>措施</th>
                  <th>计划金额</th>
                  <th>实际金额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in forecastTrace.actions" :key="row.id">
                  <td>{{ row.plannedDate }}</td>
                  <td>{{ label(row.actionType) }}</td>
                  <td>{{ amount(row.amount) }}</td>
                  <td>{{ amount(row.actualAmount) }}</td>
                  <td>{{ label(row.status) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
      </template>

      <template v-else-if="mode === 'accounting'">
        <V2Card title="凭证台账" :heading-level="2">
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="会计凭证表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>凭证编号</th>
                  <th>日期</th>
                  <th>借方</th>
                  <th>贷方</th>
                  <th>复核</th>
                  <th>状态</th>
                  <th class="v2-table-cell--actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in entries.records" :key="row.id">
                  <td>
                    <V2Button size="small" variant="ghost" @click="selectEntry(row.id)">{{
                      row.entryCode
                    }}</V2Button>
                  </td>
                  <td>{{ row.entryDate }}</td>
                  <td>{{ amount(row.totalDebit) }}</td>
                  <td>{{ amount(row.totalCredit) }}</td>
                  <td>{{ label(row.reviewStatus) }}</td>
                  <td>{{ label(row.entryStatus) }}</td>
                  <td class="v2-table-cell--actions">
                    <V2ActionMenu
                      :label="`${row.entryCode}更多操作`"
                      :placement="index >= entries.records.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <V2Button
                        v-if="
                          row.entryStatus === 'DRAFT' &&
                          row.reviewStatus === 'PENDING' &&
                          can('accounting:review')
                        "
                        size="small"
                        variant="ghost"
                        @click="actEntry(row, 'approve')"
                        >复核通过</V2Button
                      >
                      <V2Button
                        v-if="
                          row.entryStatus === 'DRAFT' &&
                          row.reviewStatus === 'PENDING' &&
                          can('accounting:review')
                        "
                        size="small"
                        variant="ghost"
                        @click="actEntry(row, 'reject')"
                        >驳回</V2Button
                      >
                      <V2Button
                        v-if="
                          row.entryStatus === 'DRAFT' &&
                          row.reviewStatus === 'APPROVED' &&
                          can('accounting:post')
                        "
                        size="small"
                        variant="ghost"
                        @click="actEntry(row, 'post')"
                        >过账</V2Button
                      >
                      <V2Button
                        v-if="
                          row.entryStatus === 'DRAFT' &&
                          row.reviewStatus === 'REJECTED' &&
                          can('accounting:add')
                        "
                        size="small"
                        variant="ghost"
                        @click="actEntry(row, 'resubmit')"
                        >重提</V2Button
                      >
                      <V2Button
                        v-if="row.entryStatus === 'POSTED' && can('accounting:adjustment:add')"
                        size="small"
                        variant="ghost"
                        @click="actEntry(row, 'reverse')"
                        >冲销</V2Button
                      >
                    </V2ActionMenu>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer>
            <div class="finance-control__pagination">
              <span>共 {{ entries.total }} 条</span>
              <V2Button
                size="small"
                variant="secondary"
                :disabled="entryPageNo === 1"
                @click="changePage('accounting', entryPageNo - 1)"
              >
                上一页
              </V2Button>
              <span>第 {{ entryPageNo }} 页</span>
              <V2Button
                size="small"
                variant="secondary"
                :disabled="entryPageNo * 10 >= entries.total"
                @click="changePage('accounting', entryPageNo + 1)"
              >
                下一页
              </V2Button>
            </div>
          </template>
        </V2Card>
        <V2Card
          v-if="entryDetail"
          :title="`分录明细 · ${entryDetail.entry.entryCode}`"
          :heading-level="2"
        >
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="会计分录表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>行号</th>
                  <th>方向</th>
                  <th>科目编码</th>
                  <th>科目名称</th>
                  <th>摘要</th>
                  <th>金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in entryDetail.lines" :key="row.id">
                  <td>{{ row.lineNo }}</td>
                  <td>{{ label(row.direction) }}</td>
                  <td>{{ row.accountCode || '—' }}</td>
                  <td>{{ row.accountName || row.costSubjectName || '—' }}</td>
                  <td>{{ row.summary }}</td>
                  <td>{{ amount(row.amount) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
      </template>

      <template v-else>
        <V2Card title="会计期间" :heading-level="2">
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="会计期间表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>期间编码</th>
                  <th>起止日期</th>
                  <th>问题数</th>
                  <th>状态</th>
                  <th class="v2-table-cell--actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in periods" :key="row.id">
                  <td>
                    <V2Button size="small" variant="ghost" @click="selectPeriod(row)">{{
                      row.periodCode
                    }}</V2Button>
                  </td>
                  <td>{{ row.startDate }} 至 {{ row.endDate }}</td>
                  <td>{{ row.issueCount }}</td>
                  <td>{{ label(row.status) }}</td>
                  <td class="v2-table-cell--actions">
                    <V2ActionMenu
                      :label="`${row.periodCode}更多操作`"
                      :placement="index >= periods.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <V2Button
                        v-if="row.status !== 'CLOSED' && can('finance:close:check')"
                        size="small"
                        variant="ghost"
                        @click="actPeriod(row, 'check')"
                        >运行检查</V2Button
                      >
                      <V2Button
                        v-if="
                          row.status !== 'CLOSED' &&
                          row.issueCount === 0 &&
                          can('finance:close:close')
                        "
                        size="small"
                        variant="ghost"
                        @click="actPeriod(row, 'close')"
                        >关账</V2Button
                      >
                      <V2Button
                        v-if="row.status === 'CLOSED' && can('finance:close:reopen')"
                        size="small"
                        variant="ghost"
                        @click="actPeriod(row, 'reopen')"
                        >反结账</V2Button
                      >
                    </V2ActionMenu>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
        <V2Card
          v-if="closeTrace && statement"
          :title="`月结追溯 · ${closeTrace.period.periodCode}`"
          :heading-level="2"
        >
          <div class="finance-control__metrics">
            <div>
              <span>应收余额</span><strong>{{ amount(statement.receivableOutstanding) }}</strong>
            </div>
            <div>
              <span>应付余额</span><strong>{{ amount(statement.payableOutstanding) }}</strong>
            </div>
            <div>
              <span>现金流入</span><strong>{{ amount(statement.cashFlow.inflow) }}</strong>
            </div>
            <div>
              <span>现金流出</span><strong>{{ amount(statement.cashFlow.outflow) }}</strong>
            </div>
            <div>
              <span>检查项</span><strong>{{ closeTrace.checks.length }}</strong>
            </div>
            <div>
              <span>银行对账异常</span
              ><strong>{{
                closeTrace.bankReconciliations.filter((row) => row.status === 'EXCEPTION').length
              }}</strong>
            </div>
          </div>
          <h3>试算平衡</h3>
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="试算平衡表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>科目编码</th>
                  <th>科目名称</th>
                  <th>借方</th>
                  <th>贷方</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in statement.trialBalance"
                  :key="`${row.accountCode}-${row.accountName}`"
                >
                  <td>{{ row.accountCode }}</td>
                  <td>{{ row.accountName }}</td>
                  <td>{{ amount(row.debit) }}</td>
                  <td>{{ amount(row.credit) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
      </template>
      <V2Dialog
        v-model:open="accountDialog"
        title="新建资金账户"
        :close-disabled="busy"
        :close-on-backdrop="false"
        @update:open="(open) => !open && closeFundAccount()"
      >
        <form
          v-if="accountEditor"
          id="fund-account-form"
          class="finance-control__form"
          @submit.prevent="saveFundAccount"
        >
          <V2Input v-model="accountEditor.accountCode" label="账户编码" required />
          <V2Input v-model="accountEditor.accountName" label="账户名称" required />
          <V2Select
            v-model="accountEditor.accountType"
            label="账户类型"
            :options="[
              { value: 'BANK', label: '银行账户' },
              { value: 'CASH', label: '现金账户' },
            ]"
            required
          />
          <V2Input v-model="accountEditor.bankName" label="开户行" />
          <V2Input v-model="accountEditor.bankAccountNo" label="银行账号" />
          <V2Input
            v-model="accountEditor.openingDate"
            type="date"
            label="开户日期"
            required
          />
          <V2Input
            v-model="accountEditor.openingBalance"
            label="期初余额"
            required
            hint="金额按服务端十进制字符串提交"
          />
          <V2Input v-model="accountEditor.remark" label="备注" />
        </form>
        <template #footer>
          <V2Button type="button" variant="secondary" :disabled="busy" @click="closeFundAccount">
            取消
          </V2Button>
          <V2Button type="submit" form="fund-account-form" :loading="busy">保存</V2Button>
        </template>
      </V2Dialog>
    </template>
  </section>
</template>

<style scoped>
.finance-control {
  display: grid;
  gap: var(--v2-space-3);
  min-width: 0;
}
.finance-control__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
}
.finance-control__form {
  display: grid;
  gap: var(--v2-space-3);
}
.finance-control__pagination {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: var(--v2-space-2);
}
.finance-control__table-wrap {
  max-width: 100%;
  overflow-x: auto;
}
.finance-control__table {
  min-width: 44rem;
}
.finance-control__wide-table {
  min-width: 68rem;
}
.finance-control__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-2);
}
.finance-control__metrics > div {
  display: grid;
  gap: var(--v2-space-1);
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  background: var(--v2-color-surface-subtle);
}
.finance-control__metrics span {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}
.finance-control__metrics strong {
  font-size: var(--v2-font-size-14);
}
h3 {
  margin: var(--v2-space-3) 0 var(--v2-space-2);
  font-size: var(--v2-font-size-14);
}
@media (max-width: 760px) {
  .finance-control__metrics {
    grid-template-columns: 1fr;
  }
}
</style>
