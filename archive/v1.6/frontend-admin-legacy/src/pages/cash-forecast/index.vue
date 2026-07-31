<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  approveCashForecast,
  approveFundingAction,
  completeFundingAction,
  createCashForecastCycle,
  createFundingAction,
  getCashForecastCycles,
  getCashForecastTrace,
  refreshCashForecastActuals,
  regenerateCashForecast,
  rollCashForecast,
  submitCashForecast,
  submitFundingAction,
  type CashForecastRow,
  type CashForecastTrace,
  type FundingActionRequest,
} from '@/api/modules/cashForecast'
import { getProjectList } from '@/api/modules/project'
import type { ProjectVO } from '@/types/project'

const iso = (date: Date) => date.toISOString().slice(0, 10)
const addDays = (date: string, days: number) => {
  const value = new Date(`${date}T00:00:00`)
  value.setDate(value.getDate() + days)
  return iso(value)
}
const numberValue = (value: unknown) => Number(value ?? 0)
const textValue = (value: unknown) => String(value ?? '')

const loading = ref(false)
const projects = ref<ProjectVO[]>([])
const projectId = ref<string>()
const cycles = ref<CashForecastRow[]>([])
const trace = ref<CashForecastTrace>()
const createOpen = ref(false)
const actionOpen = ref(false)
const rollOpen = ref(false)
const selectedLine = ref<CashForecastRow>()
const today = iso(new Date())

const cycleForm = reactive({
  forecastName: '项目滚动资金预测',
  asOfDate: today,
  horizonStart: today,
  horizonEnd: addDays(today, 90),
  scenario: 'BASE' as 'BASE' | 'OPTIMISTIC' | 'CONSERVATIVE',
  openingBalance: 0,
})
const actionForm = reactive<FundingActionRequest>({
  lineId: '',
  actionType: 'ACCELERATE_COLLECTION',
  plannedDate: today,
  amount: 0,
  reason: '',
})
const rollForm = reactive({
  asOfDate: addDays(today, 1),
  horizonEnd: addDays(today, 91),
  forecastName: '项目滚动资金预测（修订）',
})

const cycle = computed(() => trace.value?.cycle)
const cycleId = computed(() => textValue(cycle.value?.id))
const cycleStatus = computed(() => textValue(cycle.value?.status))
const totalInflow = computed(() =>
  (trace.value?.lines ?? []).reduce((sum, item) => sum + numberValue(item.planned_inflow), 0),
)
const totalOutflow = computed(() =>
  (trace.value?.lines ?? []).reduce((sum, item) => sum + numberValue(item.planned_outflow), 0),
)
const peakGap = computed(() =>
  Math.max(0, ...(trace.value?.lines ?? []).map((item) => numberValue(item.gap_amount))),
)
const closingBalance = computed(() => {
  const lines = trace.value?.lines ?? []
  return lines.length ? numberValue(lines.at(-1)?.projected_balance) : 0
})

const cycleColumns = [
  { title: '版本', dataIndex: 'cycle_code', key: 'cycle_code' },
  { title: '场景', dataIndex: 'scenario', key: 'scenario', width: 120 },
  { title: '基准日', dataIndex: 'as_of_date', key: 'as_of_date', width: 120 },
  { title: '预测截止', dataIndex: 'horizon_end', key: 'horizon_end', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '操作', key: 'operation', width: 90 },
]
const lineColumns = [
  { title: '预测日期', dataIndex: 'forecast_date', key: 'forecast_date', width: 120 },
  { title: '计划收款', dataIndex: 'planned_inflow', key: 'planned_inflow' },
  { title: '计划付款', dataIndex: 'planned_outflow', key: 'planned_outflow' },
  { title: '调度/融资', dataIndex: 'financing_amount', key: 'financing_amount' },
  { title: '预测余额', dataIndex: 'projected_balance', key: 'projected_balance' },
  { title: '资金缺口', dataIndex: 'gap_amount', key: 'gap_amount' },
  { title: '实际收款', dataIndex: 'actual_inflow', key: 'actual_inflow' },
  { title: '实际付款', dataIndex: 'actual_outflow', key: 'actual_outflow' },
  { title: '收款偏差', dataIndex: 'inflow_variance', key: 'inflow_variance' },
  { title: '付款偏差', dataIndex: 'outflow_variance', key: 'outflow_variance' },
  { title: '操作', key: 'operation', width: 100, fixed: 'right' as const },
]

async function withLoading(task: () => Promise<void>) {
  loading.value = true
  try {
    await task()
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  const result = await getProjectList({ pageNo: 1, pageSize: 100 })
  projects.value = result.records ?? []
  if (!projectId.value && projects.value.length) projectId.value = projects.value[0]?.id
  if (projectId.value) await loadCycles()
}

async function loadCycles() {
  if (!projectId.value) return
  await withLoading(async () => {
    cycles.value = await getCashForecastCycles(projectId.value as string)
    const currentId = cycleId.value
    const target = cycles.value.find((item) => textValue(item.id) === currentId) ?? cycles.value[0]
    trace.value = target?.id ? await getCashForecastTrace(textValue(target.id)) : undefined
  })
}

async function selectCycle(row: CashForecastRow) {
  trace.value = await getCashForecastTrace(textValue(row.id))
}

async function saveCycle() {
  if (!projectId.value || !cycleForm.forecastName)
    return message.warning('请选择项目并填写预测名称')
  await withLoading(async () => {
    trace.value = await createCashForecastCycle({
      projectId: projectId.value as string,
      ...cycleForm,
    })
    createOpen.value = false
    await loadCycles()
    message.success('资金预测版本已生成，计划收付已自动汇总')
  })
}

function openAction(line: CashForecastRow) {
  selectedLine.value = line
  actionForm.lineId = textValue(line.id)
  actionForm.plannedDate = textValue(line.forecast_date)
  actionForm.amount = numberValue(line.gap_amount)
  actionForm.reason = ''
  actionOpen.value = true
}

async function saveAction() {
  if (!cycleId.value || !actionForm.reason || actionForm.amount <= 0)
    return message.warning('请完整填写缺口措施')
  await createFundingAction(cycleId.value, { ...actionForm })
  actionOpen.value = false
  trace.value = await getCashForecastTrace(cycleId.value)
  message.success('缺口措施已拟定，请提交后由其他人员审批')
}

async function actionLifecycle(
  row: CashForecastRow,
  operation: 'submit' | 'approve' | 'reject' | 'complete',
) {
  const id = textValue(row.id)
  if (operation === 'submit') await submitFundingAction(id)
  if (operation === 'approve') await approveFundingAction(id, true, '资金预测工作台审批通过')
  if (operation === 'reject') await approveFundingAction(id, false, '退回重新制定资金措施')
  if (operation === 'complete')
    await completeFundingAction(id, numberValue(row.amount), `CF-ACTION-${id}`)
  trace.value = await getCashForecastTrace(cycleId.value)
  message.success('资金措施状态已更新')
}

async function regenerate() {
  trace.value = await regenerateCashForecast(cycleId.value)
  message.success('预测已按最新收付款计划重算')
}

async function submitCycle() {
  trace.value = await submitCashForecast(cycleId.value)
  await loadCycles()
  message.success('预测已提交审批')
}

function decideCycle(approved: boolean) {
  Modal.confirm({
    title: approved ? '确认批准资金预测？' : '确认驳回资金预测？',
    content: approved ? '批准后当前场景的上一版本将失效。' : '驳回后返回草稿，可重新测算。',
    async onOk() {
      trace.value = await approveCashForecast(
        cycleId.value,
        approved,
        approved ? '资金预测工作台审批通过' : '预测依据需要修订',
      )
      await loadCycles()
      message.success(approved ? '资金预测已批准' : '资金预测已驳回')
    },
  })
}

async function refreshActuals() {
  trace.value = await refreshCashForecastActuals(cycleId.value)
  message.success('已从资金日记账刷新实际收付与偏差')
}

async function saveRoll() {
  trace.value = await rollCashForecast(cycleId.value, { ...rollForm })
  rollOpen.value = false
  await loadCycles()
  message.success('下一滚动版本已建立')
}

onMounted(loadProjects)
</script>

<template>
  <div class="cash-forecast-page app-page">
    <a-page-header
      title="项目资金计划与现金预测"
      sub-title="计划收付、资金缺口、调度措施、实际偏差与滚动修正"
    >
      <template #extra>
        <a-select
          v-model:value="projectId"
          show-search
          style="width: 280px"
          placeholder="选择项目"
          @change="loadCycles"
        >
          <a-select-option v-for="project in projects" :key="project.id" :value="project.id">
            {{ project.projectCode }} · {{ project.projectName }}
          </a-select-option>
        </a-select>
        <a-button type="primary" :disabled="!projectId" @click="createOpen = true"
          >新建预测版本</a-button
        >
        <a-button :loading="loading" @click="loadCycles">刷新</a-button>
      </template>
    </a-page-header>

    <a-row :gutter="16" class="summary-row">
      <a-col :span="6"
        ><a-card><a-statistic title="计划收款" :value="totalInflow" :precision="2" /></a-card
      ></a-col>
      <a-col :span="6"
        ><a-card><a-statistic title="计划付款" :value="totalOutflow" :precision="2" /></a-card
      ></a-col>
      <a-col :span="6"
        ><a-card><a-statistic title="峰值缺口" :value="peakGap" :precision="2" /></a-card
      ></a-col>
      <a-col :span="6"
        ><a-card><a-statistic title="期末预测余额" :value="closingBalance" :precision="2" /></a-card
      ></a-col>
    </a-row>

    <a-card title="预测版本" class="section-card">
      <a-table
        row-key="id"
        :columns="cycleColumns"
        :data-source="cycles"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <a-button v-if="column.key === 'operation'" type="link" @click="selectCycle(record)"
            >查看</a-button
          >
        </template>
      </a-table>
    </a-card>

    <a-card v-if="cycle" class="section-card">
      <template #title>{{ cycle.forecast_name }} · {{ cycle.cycle_code }}</template>
      <template #extra>
        <a-space wrap>
          <a-tag color="blue">{{ cycleStatus }}</a-tag>
          <a-button v-if="cycleStatus === 'DRAFT'" @click="regenerate">按计划重算</a-button>
          <a-button v-if="cycleStatus === 'DRAFT'" type="primary" @click="submitCycle"
            >提交审批</a-button
          >
          <a-button v-if="cycleStatus === 'SUBMITTED'" type="primary" @click="decideCycle(true)"
            >审批通过</a-button
          >
          <a-button v-if="cycleStatus === 'SUBMITTED'" danger @click="decideCycle(false)"
            >驳回</a-button
          >
          <a-button v-if="['APPROVED', 'SUPERSEDED'].includes(cycleStatus)" @click="refreshActuals"
            >刷新实际偏差</a-button
          >
          <a-button v-if="cycleStatus === 'APPROVED'" @click="rollOpen = true"
            >建立滚动版本</a-button
          >
        </a-space>
      </template>
      <a-table
        row-key="id"
        :columns="lineColumns"
        :data-source="trace?.lines ?? []"
        :pagination="false"
        :scroll="{ x: 1450 }"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <a-button
            v-if="
              column.key === 'operation' &&
              cycleStatus === 'DRAFT' &&
              numberValue(record.gap_amount) > 0
            "
            type="link"
            @click="openAction(record)"
            >制定措施</a-button
          >
        </template>
      </a-table>
    </a-card>

    <a-card v-if="trace" title="资金缺口调度措施" class="section-card">
      <a-table row-key="id" :data-source="trace.actions" :pagination="false" size="small">
        <a-table-column title="日期" data-index="planned_date" />
        <a-table-column title="措施类型" data-index="action_type" />
        <a-table-column title="金额" data-index="amount" />
        <a-table-column title="原因" data-index="reason" />
        <a-table-column title="状态" data-index="status" />
        <a-table-column title="操作">
          <template #default="{ record }">
            <a-space>
              <a-button
                v-if="record.status === 'PROPOSED'"
                type="link"
                @click="actionLifecycle(record, 'submit')"
                >提交</a-button
              >
              <a-button
                v-if="record.status === 'SUBMITTED'"
                type="link"
                @click="actionLifecycle(record, 'approve')"
                >批准</a-button
              >
              <a-button
                v-if="record.status === 'SUBMITTED'"
                type="link"
                danger
                @click="actionLifecycle(record, 'reject')"
                >退回</a-button
              >
              <a-button
                v-if="record.status === 'APPROVED'"
                type="link"
                @click="actionLifecycle(record, 'complete')"
                >完成</a-button
              >
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <a-tabs v-if="trace">
      <a-tab-pane key="sources" tab="计划来源追溯">
        <a-descriptions bordered size="small" :column="3">
          <a-descriptions-item label="收款计划"
            >{{ trace.collectionSchedules.length }} 笔</a-descriptions-item
          >
          <a-descriptions-item label="付款计划"
            >{{ trace.paymentSchedules.length }} 笔</a-descriptions-item
          >
          <a-descriptions-item label="实际资金流水"
            >{{ trace.actualJournals.length }} 笔</a-descriptions-item
          >
        </a-descriptions>
      </a-tab-pane>
      <a-tab-pane key="audit" tab="审批与审计留痕">
        <a-table
          row-key="event_at"
          :data-source="trace.auditTrail"
          :pagination="false"
          size="small"
        >
          <a-table-column title="事件" data-index="event_type" />
          <a-table-column title="操作人" data-index="operator_id" />
          <a-table-column title="时间" data-index="event_at" />
          <a-table-column title="摘要哈希" data-index="payload_hash" />
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="createOpen" title="新建资金预测版本" @ok="saveCycle">
      <a-form layout="vertical">
        <a-form-item label="预测名称" required
          ><a-input v-model:value="cycleForm.forecastName"
        /></a-form-item>
        <a-form-item label="场景" required
          ><a-select v-model:value="cycleForm.scenario"
            ><a-select-option value="BASE">基准</a-select-option
            ><a-select-option value="OPTIMISTIC">乐观</a-select-option
            ><a-select-option value="CONSERVATIVE">保守</a-select-option></a-select
          ></a-form-item
        >
        <a-form-item label="基准日" required
          ><a-input v-model:value="cycleForm.asOfDate" type="date"
        /></a-form-item>
        <a-form-item label="预测开始" required
          ><a-input v-model:value="cycleForm.horizonStart" type="date"
        /></a-form-item>
        <a-form-item label="预测截止" required
          ><a-input v-model:value="cycleForm.horizonEnd" type="date"
        /></a-form-item>
        <a-form-item label="期初余额" required
          ><a-input-number
            v-model:value="cycleForm.openingBalance"
            :min="0"
            :precision="2"
            style="width: 100%"
        /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="actionOpen" title="制定资金缺口措施" @ok="saveAction">
      <a-form layout="vertical">
        <a-form-item label="缺口日期"
          ><a-input v-model:value="actionForm.plannedDate" type="date" disabled
        /></a-form-item>
        <a-form-item label="措施类型" required
          ><a-select v-model:value="actionForm.actionType"
            ><a-select-option value="ACCELERATE_COLLECTION">加速回款</a-select-option
            ><a-select-option value="DEFER_PAYMENT">延后付款</a-select-option
            ><a-select-option value="FUND_TRANSFER">资金调拨</a-select-option
            ><a-select-option value="FINANCING">外部融资</a-select-option></a-select
          ></a-form-item
        >
        <a-form-item label="覆盖金额" required
          ><a-input-number
            v-model:value="actionForm.amount"
            :min="0.01"
            :precision="2"
            style="width: 100%"
        /></a-form-item>
        <a-form-item label="措施原因" required
          ><a-textarea v-model:value="actionForm.reason"
        /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="rollOpen" title="建立下一滚动版本" @ok="saveRoll">
      <a-form layout="vertical">
        <a-form-item label="预测名称" required
          ><a-input v-model:value="rollForm.forecastName"
        /></a-form-item>
        <a-form-item label="新基准日" required
          ><a-input v-model:value="rollForm.asOfDate" type="date"
        /></a-form-item>
        <a-form-item label="预测截止" required
          ><a-input v-model:value="rollForm.horizonEnd" type="date"
        /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.cash-forecast-page {
  min-height: 100%;
}
.summary-row,
.section-card {
  margin-bottom: 16px;
}
</style>
