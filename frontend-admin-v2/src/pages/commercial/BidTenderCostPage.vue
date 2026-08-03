<script setup lang="ts">
import type { CashJournalPage, CashJournalSummary } from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { V2Alert, V2Badge, V2Button, V2Card, V2Input, V2PageState, V2Select } from '@/components'
import {
  loadBidCostOptions,
  loadBidCostSubjectOptions,
  type BidCostOption,
  type CostSubjectOption,
} from '@/services/commercial'
import {
  archiveCashJournal,
  createCashJournal,
  exportCashJournal,
  loadBidFundAccountOptions,
  loadCashJournal,
  loadCashJournalSummary,
  reverseCashJournal,
  type BidFundAccountOption,
} from '@/services/finance'
import { uploadSiteFile } from '@/services/delivery'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { dashboardStatusLabel } from '@/pages/dashboard/model'

const session = useSessionStore()
const bids = ref<BidCostOption[]>([])
const subjects = ref<CostSubjectOption[]>([])
const accounts = ref<BidFundAccountOption[]>([])
const page = ref<CashJournalPage>({ pageNo: 1, pageSize: 20, total: 0, records: [] })
const summary = ref<CashJournalSummary | null>(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const selectedBidId = ref('')
const evidence = ref<Record<string, File | null>>({})
const form = reactive({
  direction: 'OUT' as 'IN' | 'OUT',
  amount: '',
  businessDate: new Date().toISOString().slice(0, 10),
  accountId: '',
  costSubjectId: '',
  counterpartyName: '',
  summary: '',
})
let controller: AbortController | null = null

const canMaintain = computed(() => session.hasPermission('bid:cost:maintain'))
const canExport = computed(() => session.hasPermission('bid:cost:export'))
const selectedBid = computed(
  () => bids.value.find((item) => item.id === selectedBidId.value) ?? null,
)
const bidOptions = computed(() =>
  bids.value.map((item) => ({ value: item.id, label: `${item.bidCode} · ${item.bidProjectName}` })),
)
const subjectOptions = computed(() =>
  subjects.value
    .filter((item) => item.status === 'ENABLE' && /^5401\.01\.[^.]+$/.test(item.subjectCode))
    .map((item) => ({ value: item.id, label: `${item.subjectCode} ${item.subjectName}` })),
)
const accountOptions = computed(() =>
  accounts.value
    .filter((item) => item.enabledFlag === 1)
    .map((item) => ({ value: item.id, label: `${item.accountName}（${item.accountType}）` })),
)
const metrics = computed(() =>
  summary.value
    ? [
        ['累计现金支出', summary.value.cumulativeCashOut],
        ['累计现金收回', summary.value.cumulativeCashIn],
        ['未退保证金', summary.value.outstandingDeposit],
        ['实际投标费用', summary.value.actualBidExpense],
        ['现金净流出', summary.value.cashNetOutflow],
      ]
    : [],
)
function message(value: unknown, fallback: string) {
  return isApiClientError(value) ? value.message : fallback
}
async function loadBase() {
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const [bidRows, subjectRows, accountRows] = await Promise.all([
      loadBidCostOptions(controller.signal),
      loadBidCostSubjectOptions(controller.signal),
      loadBidFundAccountOptions(controller.signal),
    ])
    bids.value = bidRows
    subjects.value = subjectRows
    accounts.value = accountRows
    if (!selectedBidId.value) selectedBidId.value = bids.value[0]?.id ?? ''
    await loadLedger()
  } catch (value) {
    if (!controller.signal.aborted) error.value = message(value, '投标成本加载失败')
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}
async function loadLedger() {
  if (!selectedBidId.value) {
    page.value = { pageNo: 1, pageSize: 20, total: 0, records: [] }
    summary.value = null
    return
  }
  const query = {
    bidCostId: selectedBidId.value,
    costSubjectRootCode: '5401.01',
    pageNo: 1,
    pageSize: 50,
  }
  ;[page.value, summary.value] = await Promise.all([
    loadCashJournal(query, controller?.signal),
    loadCashJournalSummary(query, controller?.signal),
  ])
}
async function create() {
  if (
    !selectedBidId.value ||
    !form.amount ||
    !form.costSubjectId ||
    !form.summary.trim() ||
    busy.value
  )
    return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await createCashJournal({
      ...form,
      accountId: form.accountId || null,
      bidCostId: selectedBidId.value,
      costSubjectId: form.costSubjectId,
      counterpartyName: form.counterpartyName.trim() || null,
      summary: form.summary.trim(),
    })
    notice.value = '投标现金流水草稿已登记。'
    form.amount = ''
    form.counterpartyName = ''
    form.summary = ''
    await loadLedger()
  } catch (value) {
    error.value = message(value, '投标现金流水登记失败')
  } finally {
    busy.value = false
  }
}
async function archive(row: CashJournalPage['records'][number]) {
  busy.value = true
  error.value = ''
  try {
    const file = evidence.value[row.id]
    if (file) await uploadSiteFile(file, 'CASH_JOURNAL', row.id, 'BANK_RECEIPT')
    await archiveCashJournal(row.id)
    notice.value = '流水已归档。'
    await loadLedger()
  } catch (value) {
    error.value = message(value, '流水归档失败')
  } finally {
    busy.value = false
  }
}
async function reverse(row: CashJournalPage['records'][number]) {
  const reason = window.prompt('请输入红冲原因')?.trim()
  if (!reason) return
  busy.value = true
  try {
    await reverseCashJournal(row.id, reason)
    notice.value = '红冲流水已生成。'
    await loadLedger()
  } catch (value) {
    error.value = message(value, '红冲失败')
  } finally {
    busy.value = false
  }
}
async function exportLedger() {
  if (!selectedBidId.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const blob = await exportCashJournal({
      bidCostId: selectedBidId.value,
      costSubjectRootCode: '5401.01',
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `投标成本-${selectedBid.value?.bidCode ?? selectedBidId.value}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch (value) {
    error.value = message(value, '投标成本导出失败')
  } finally {
    busy.value = false
  }
}
function chooseEvidence(id: string, event: Event) {
  evidence.value[id] = (event.target as HTMLInputElement).files?.[0] ?? null
}
function reversalLinkLabel(row: CashJournalPage['records'][number]): string {
  if (row.reverseOfEntryId) return '原流水'
  if (row.reversalEntryId) return '红冲流水'
  return '—'
}
watch(selectedBidId, () => {
  if (!loading.value) void loadLedger()
})
void loadBase()
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <div class="bid-cost-ledger">
    <V2Alert v-if="error" tone="danger" title="操作未完成">{{ error }}</V2Alert>
    <V2Alert v-if="notice" tone="success" title="已完成">{{ notice }}</V2Alert>
    <V2Card title="投标成本" :heading-level="1"></V2Card>
    <V2Card title="投标现金概览">
      <V2Select v-model="selectedBidId" label="投标记录" :options="bidOptions" />
      <V2Button v-if="canExport && selectedBidId" size="small" :loading="busy" @click="exportLedger"
        >导出当前投标</V2Button
      >
      <div class="bid-cost-ledger__metrics">
        <div v-for="item in metrics" :key="item[0]">
          <span>{{ item[0] }}</span
          ><strong>{{ item[1] }}</strong>
        </div>
      </div>
    </V2Card>
    <V2PageState
      v-if="loading"
      title="正在加载投标成本"
      description="请稍候，正在读取投标现金事实。"
      kind="loading"
    />
    <template v-else>
      <V2Card v-if="canMaintain && selectedBidId" title="登记现金流水">
        <div class="bid-cost-ledger__form">
          <V2Select
            v-model="form.direction"
            label="收支方向"
            :options="[
              { value: 'OUT', label: '支出' },
              { value: 'IN', label: '收回' },
            ]"
          />
          <V2Input v-model="form.amount" label="金额" type="number" />
          <label>业务日期<input v-model="form.businessDate" type="date" /></label>
          <V2Select
            v-model="form.accountId"
            label="资金账户"
            :options="accountOptions"
            allow-empty
          />
          <V2Select v-model="form.costSubjectId" label="成本科目" :options="subjectOptions" />
          <V2Input v-model="form.counterpartyName" label="对方单位" />
          <V2Input v-model="form.summary" label="摘要" />
          <V2Button
            :loading="busy"
            :disabled="!form.amount || !form.costSubjectId || !form.summary.trim()"
            @click="create"
            >保存草稿</V2Button
          >
        </div>
      </V2Card>
      <V2Card title="现金日记账事实">
        <div class="bid-cost-ledger__table">
          <table>
            <thead>
              <tr>
                <th>业务日期</th>
                <th>日记账编号</th>
                <th>投标编号</th>
                <th>工程名称</th>
                <th>收支方向</th>
                <th>成本科目</th>
                <th>摘要</th>
                <th>对方单位</th>
                <th>金额</th>
                <th>资金账户</th>
                <th>经办人</th>
                <th>保证金类型</th>
                <th>附件</th>
                <th>状态</th>
                <th>原冲关联</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in page.records" :key="row.id">
                <td>{{ row.businessDate }}</td>
                <td>{{ row.entryNo }}</td>
                <td>{{ selectedBid?.bidCode || '—' }}</td>
                <td>{{ selectedBid?.bidProjectName || '—' }}</td>
                <td>{{ row.direction }}</td>
                <td>{{ row.costSubjectCode }} {{ row.costSubjectName }}</td>
                <td>{{ row.summary }}</td>
                <td>{{ row.counterpartyName || '—' }}</td>
                <td>{{ row.amount }}</td>
                <td>{{ row.accountName || '—' }}</td>
                <td>{{ row.createdBy || '—' }}</td>
                <td>{{ row.bidDepositType || '—' }}</td>
                <td>{{ row.attachmentCount ?? 0 }}</td>
                <td>
                  <V2Badge tone="info">{{ dashboardStatusLabel(row.status) }}</V2Badge>
                </td>
                <td>{{ reversalLinkLabel(row) }}</td>
                <td class="bid-cost-ledger__actions">
                  <template v-if="canMaintain && ['DRAFT', 'PENDING_ARCHIVE'].includes(row.status)"
                    ><input
                      type="file"
                      aria-label="银行回单"
                      @change="chooseEvidence(row.id, $event)"
                    /><V2Button size="small" :loading="busy" @click="archive(row)"
                      >归档</V2Button
                    ></template
                  ><V2Button
                    v-if="canMaintain && row.status === 'ARCHIVED'"
                    size="small"
                    variant="danger"
                    @click="reverse(row)"
                    >红冲</V2Button
                  >
                </td>
              </tr>
              <tr v-if="!page.records.length">
                <td colspan="16">暂无投标现金流水</td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>
    </template>
  </div>
</template>

<style scoped>
.bid-cost-ledger {
  display: grid;
  gap: var(--v2-space-4);
}
.bid-cost-ledger__metrics,
.bid-cost-ledger__form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--v2-space-3);
  margin-top: var(--v2-space-4);
  align-items: end;
}
.bid-cost-ledger__metrics div {
  display: grid;
  gap: 4px;
  padding: 12px;
  background: var(--v2-color-surface-subtle);
  border-radius: var(--v2-radius-sm);
}
label {
  display: grid;
  gap: 6px;
  font-size: var(--v2-font-size-13);
  color: var(--v2-color-text-secondary);
}
input {
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  background: var(--v2-color-surface);
  color: var(--v2-color-text);
}
.bid-cost-ledger__table {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  padding: 10px;
  border-bottom: 1px solid var(--v2-color-border);
  text-align: left;
  white-space: nowrap;
}
.bid-cost-ledger__actions {
  display: flex;
  gap: 6px;
  align-items: center;
}
</style>
