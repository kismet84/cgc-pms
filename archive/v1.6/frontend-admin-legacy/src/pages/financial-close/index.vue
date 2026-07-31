<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  closeFinancialPeriod,
  createAdjustmentEntry,
  createFinancialPeriod,
  getFinancialCloseTrace,
  getFinancialPeriods,
  getFinancialStatements,
  reopenFinancialPeriod,
  runFinancialCloseChecks,
  type FinancialRow,
  type FinancialTrace,
} from '@/api/modules/financialClose'

const today = new Date()
const year = ref(today.getFullYear())
const month = ref(today.getMonth() + 1)
const loading = ref(false)
const periods = ref<FinancialRow[]>([])
const trace = ref<FinancialTrace>()
const statements = ref<FinancialRow>()
const adjustmentOpen = ref(false)
const adjustment = reactive({
  entryDate: today.toISOString().slice(0, 10),
  reason: '',
  debitAccountCode: '6602',
  debitAccountName: '管理费用',
  creditAccountCode: '2202',
  creditAccountName: '应付账款',
  amount: undefined as number | undefined,
})

const currentPeriod = computed(() => trace.value?.period)
const issueCount = computed(() => Number(currentPeriod.value?.issue_count ?? 0))
const statusLabel = computed(() => {
  const value = String(currentPeriod.value?.status ?? 'NOT_CREATED')
  return (
    { OPEN: '开放', CHECKING: '检查中', CLOSED: '已结账', REOPENED: '已反结账' }[value] ?? '未建账'
  )
})
const checkColumns = [
  { title: '检查项', dataIndex: 'check_type', key: 'check_type' },
  { title: '结果', dataIndex: 'check_status', key: 'check_status', width: 100 },
  { title: '异常数', dataIndex: 'issue_count', key: 'issue_count', width: 100 },
  { title: '检查时间', dataIndex: 'checked_at', key: 'checked_at', width: 180 },
]
const reconciliationColumns = [
  { title: '科目', dataIndex: 'account_type', key: 'account_type' },
  { title: '业务余额', dataIndex: 'expected_amount', key: 'expected_amount' },
  { title: '账面余额', dataIndex: 'ledger_amount', key: 'ledger_amount' },
  { title: '差异', dataIndex: 'difference_amount', key: 'difference_amount' },
  { title: '状态', dataIndex: 'status', key: 'status' },
]

async function loadPeriods() {
  periods.value = await getFinancialPeriods(year.value)
  const found = periods.value.find(
    (item) => Number(item.fiscal_year) === year.value && Number(item.fiscal_month) === month.value,
  )
  trace.value = found?.id ? await getFinancialCloseTrace(String(found.id)) : undefined
}
async function withLoading(task: () => Promise<void>) {
  loading.value = true
  try {
    await task()
  } finally {
    loading.value = false
  }
}
async function ensurePeriod() {
  await withLoading(async () => {
    const period = await createFinancialPeriod(year.value, month.value)
    trace.value = await getFinancialCloseTrace(String(period.id))
    await loadPeriods()
    message.success('会计期间已建立')
  })
}
async function runChecks() {
  await withLoading(async () => {
    trace.value = await runFinancialCloseChecks(year.value, month.value)
    message.success(`检查完成，发现 ${issueCount.value} 项异常`)
  })
}
function closePeriod() {
  Modal.confirm({
    title: '确认执行月结？',
    content: '结账后该期间禁止新增、过账和冲销凭证。',
    async onOk() {
      trace.value = await closeFinancialPeriod(year.value, month.value, '财务工作台月结')
      message.success('月结成功，期间已锁定')
    },
  })
}
function reopenPeriod() {
  Modal.confirm({
    title: '确认反结账？',
    content: '反结账仅用于审计调整，调整完成后必须重新检查和结账。',
    async onOk() {
      trace.value = await reopenFinancialPeriod(year.value, month.value, '审计调整')
      message.success('期间已反结账')
    },
  })
}
async function loadStatements() {
  statements.value = await getFinancialStatements(year.value, month.value)
  message.success('财务报表已按已过账凭证重算')
}
async function saveAdjustment() {
  if (!adjustment.reason || !adjustment.amount) return message.warning('请完整填写调整原因和金额')
  await createAdjustmentEntry({
    entryDate: adjustment.entryDate,
    reason: adjustment.reason,
    lines: [
      {
        direction: 'DEBIT',
        accountCode: adjustment.debitAccountCode,
        accountName: adjustment.debitAccountName,
        amount: adjustment.amount,
        summary: adjustment.reason,
      },
      {
        direction: 'CREDIT',
        accountCode: adjustment.creditAccountCode,
        accountName: adjustment.creditAccountName,
        amount: adjustment.amount,
        summary: adjustment.reason,
      },
    ],
  })
  adjustmentOpen.value = false
  message.success('调整凭证已生成，等待复核')
  await runChecks()
}

onMounted(loadPeriods)
</script>

<template>
  <div class="financial-close-page app-page">
    <a-page-header
      title="财务核算与月结"
      sub-title="凭证复核、应收应付对账、银企对账、锁账与反结账"
    >
      <template #extra>
        <a-button @click="adjustmentOpen = true">新建调整凭证</a-button>
        <a-button @click="loadStatements">重算财务报表</a-button>
        <a-button type="primary" :loading="loading" @click="loadPeriods">刷新</a-button>
      </template>
    </a-page-header>

    <a-card class="period-control">
      <a-space wrap>
        <a-input-number v-model:value="year" :min="2000" aria-label="会计年度" />
        <a-select v-model:value="month" style="width: 120px" aria-label="会计月份">
          <a-select-option v-for="item in 12" :key="item" :value="item"
            >{{ item }} 月</a-select-option
          >
        </a-select>
        <a-tag color="blue">{{ statusLabel }}</a-tag>
        <a-button v-if="!currentPeriod" @click="ensurePeriod">建立期间</a-button>
        <a-button v-if="currentPeriod && currentPeriod.status !== 'CLOSED'" @click="runChecks"
          >运行月结检查</a-button
        >
        <a-button
          v-if="currentPeriod && currentPeriod.status !== 'CLOSED'"
          type="primary"
          :disabled="issueCount > 0"
          @click="closePeriod"
          >执行月结</a-button
        >
        <a-button v-if="currentPeriod?.status === 'CLOSED'" danger @click="reopenPeriod"
          >反结账</a-button
        >
      </a-space>
    </a-card>

    <a-row :gutter="16" class="summary-row">
      <a-col :span="6"
        ><a-card><a-statistic title="月结状态" :value="statusLabel" /></a-card
      ></a-col>
      <a-col :span="6"
        ><a-card><a-statistic title="检查异常" :value="issueCount" /></a-card
      ></a-col>
      <a-col :span="6"
        ><a-card
          ><a-statistic
            title="银行对账"
            :value="trace?.bankReconciliations.length ?? 0"
            suffix="笔" /></a-card
      ></a-col>
      <a-col :span="6"
        ><a-card
          ><a-statistic
            title="审计事件"
            :value="trace?.auditTrail.length ?? 0"
            suffix="条" /></a-card
      ></a-col>
    </a-row>

    <a-tabs>
      <a-tab-pane key="checks" tab="月结检查">
        <a-table
          row-key="id"
          :columns="checkColumns"
          :data-source="trace?.checks ?? []"
          :pagination="false"
        />
      </a-tab-pane>
      <a-tab-pane key="accounts" tab="应收应付对账">
        <a-table
          row-key="id"
          :columns="reconciliationColumns"
          :data-source="trace?.accountReconciliations ?? []"
          :pagination="false"
        />
      </a-tab-pane>
      <a-tab-pane key="bank" tab="银企对账">
        <a-table row-key="id" :data-source="trace?.bankReconciliations ?? []" :pagination="false">
          <a-table-column title="方向" data-index="direction" />
          <a-table-column title="业务类型" data-index="business_type" />
          <a-table-column title="银行金额" data-index="bank_amount" />
          <a-table-column title="差异" data-index="difference_amount" />
          <a-table-column title="状态" data-index="status" />
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="trace" tab="凭证与审计追溯">
        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="凭证数量">{{
            trace?.entries.length ?? 0
          }}</a-descriptions-item>
          <a-descriptions-item label="审计事件">{{
            trace?.auditTrail.length ?? 0
          }}</a-descriptions-item>
          <a-descriptions-item label="应收余额">{{
            statements?.receivableOutstanding ?? '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="应付余额">{{
            statements?.payableOutstanding ?? '-'
          }}</a-descriptions-item>
        </a-descriptions>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="adjustmentOpen" title="新建调整凭证" @ok="saveAdjustment">
      <a-form layout="vertical">
        <a-form-item label="凭证日期" required
          ><a-input v-model:value="adjustment.entryDate" type="date"
        /></a-form-item>
        <a-form-item label="调整原因" required
          ><a-textarea v-model:value="adjustment.reason"
        /></a-form-item>
        <a-form-item label="借方科目"
          ><a-input v-model:value="adjustment.debitAccountName"
        /></a-form-item>
        <a-form-item label="贷方科目"
          ><a-input v-model:value="adjustment.creditAccountName"
        /></a-form-item>
        <a-form-item label="金额" required
          ><a-input-number v-model:value="adjustment.amount" :min="0.01" style="width: 100%"
        /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.financial-close-page {
  min-height: 100%;
}
.period-control {
  margin-bottom: 16px;
}
.summary-row {
  margin-bottom: 16px;
}
</style>
