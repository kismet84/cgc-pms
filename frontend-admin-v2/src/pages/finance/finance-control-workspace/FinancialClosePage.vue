<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { V2ActionMenu, V2Button, V2Card, V2PageState, V2Pagination } from '@/components'
import { showToast } from '@/components/toast'
import {
  closeFinancePeriod,
  loadFinancePeriods,
  loadFinancialCloseTrace,
  loadFinancialStatement,
  reopenFinancePeriod,
  runFinancialCloseChecks,
} from '@/services/finance'
import { useSessionStore } from '@/stores/session'
import type {
  FinancePeriodRecord,
  FinancialCloseTrace,
  FinancialStatement,
} from '@cgc-pms/frontend-contracts'
import { amount, askReason, label, pageSlice } from './model'

type PeriodAction = 'check' | 'close' | 'reopen'

const pageSize = 10
const session = useSessionStore()
const canQuery = computed(() => session.hasPermission('finance:close:query'))
const can = (permission: string) => session.hasPermission(permission)
const periods = ref<FinancePeriodRecord[]>([])
const pageNo = ref(1)
const trace = ref<FinancialCloseTrace | null>(null)
const statement = ref<FinancialStatement | null>(null)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
let controller: AbortController | null = null
const pagedPeriods = computed(() => pageSlice(periods.value, pageNo.value))

async function load(): Promise<void> {
  if (!canQuery.value) return
  pageNo.value = 1
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    periods.value = await loadFinancePeriods(undefined, request.signal)
    const selected =
      periods.value.find((row) => row.id === trace.value?.period.id) || periods.value[0]
    if (selected) {
      ;[trace.value, statement.value] = await Promise.all([
        loadFinancialCloseTrace(selected.id, request.signal),
        loadFinancialStatement(selected.fiscalYear, selected.fiscalMonth, request.signal),
      ])
    } else {
      trace.value = null
      statement.value = null
    }
  } catch (cause) {
    if (!request.signal.aborted) {
      errorMessage.value = cause instanceof Error ? cause.message : '请求失败，请稍后重试。'
    }
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

async function refreshWorkspace(): Promise<void> {
  await load()
  if (!errorMessage.value) showToast('success', '刷新完成', '已读取最新数据。')
}

async function selectPeriod(row: FinancePeriodRecord): Promise<void> {
  ;[trace.value, statement.value] = await Promise.all([
    loadFinancialCloseTrace(row.id),
    loadFinancialStatement(row.fiscalYear, row.fiscalMonth),
  ])
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

async function actPeriod(row: FinancePeriodRecord, action: PeriodAction): Promise<void> {
  if (action === 'check') {
    return run(() => runFinancialCloseChecks(row.fiscalYear, row.fiscalMonth), '月结检查已完成')
  }
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

void load()
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-control">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      title="无权访问财务月结"
      description="系统未加载任何财务数据。"
    />
    <template v-else>
      <V2Card title="财务月结" :heading-level="1">
        <template #actions>
          <div class="finance-control__actions">
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace">
              刷新
            </V2Button>
          </div>
        </template>
      </V2Card>
      <V2PageState
        v-if="loading && !periods.length"
        kind="loading"
        title="正在加载"
        description="正在读取财务月结。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="财务月结加载失败"
        :description="errorMessage"
        ><template #actions><V2Button @click="load">重试</V2Button></template></V2PageState
      >
      <V2PageState
        v-else-if="!errorMessage && !periods.length"
        title="暂无财务月结记录"
        description="当前范围暂无可访问记录。"
      />
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
                <tr v-for="(row, index) in pagedPeriods" :key="row.id">
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
                      :placement="index >= pagedPeriods.length - 3 ? 'top-end' : 'bottom-end'"
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
          <template #footer
            ><V2Pagination
              :total="periods.length"
              :page-no="pageNo"
              :page-size="pageSize"
              label="会计期间分页"
              @update:page-no="pageNo = $event"
          /></template>
        </V2Card>
        <V2Card
          v-if="trace && statement"
          :title="`月结追溯 · ${trace.period.periodCode}`"
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
              <span>检查项</span><strong>{{ trace.checks.length }}</strong>
            </div>
            <div>
              <span>银行对账异常</span
              ><strong>{{
                trace.bankReconciliations.filter((row) => row.status === 'EXCEPTION').length
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
    </template>
  </section>
</template>

<style scoped src="./finance-control.css"></style>
