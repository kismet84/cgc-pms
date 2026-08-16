<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  V2ActionMenu,
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
} from '@/components'
import PaymentTraceDialog from '@/components/finance/PaymentTraceDialog.vue'
import { showToast } from '@/components/toast'
import {
  loadAccountingEntries,
  createAccountingCostCarryover,
  loadFinanceOperationsFormOptions,
  loadAccountingEntryDetail,
  loadPaymentTraceByVoucher,
  postAccountingEntry,
  resubmitAccountingEntry,
  reverseAccountingEntry,
  reviewAccountingEntry,
} from '@/services/finance'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import { localDateInputValue } from '@/services/workspace-context'
import type {
  AccountingEntryDetail,
  AccountingEntryPage as AccountingEntryPageResult,
  AccountingEntryRecord,
  PaymentTraceRecord,
  FinanceOperationsFormOptions,
} from '@cgc-pms/frontend-contracts'
import { amount, askReason, label } from './model'

type EntryAction = 'approve' | 'reject' | 'post' | 'resubmit' | 'reverse'

const pageSize = 10
const paymentTraceSourceTypes = new Set(['PAY_APPLICATION', 'PAY_RECORD', 'PAY_INVOICE'])
const session = useSessionStore()
const workspace = useWorkspaceStore()
const projectId = computed(() => workspace.selectedProjectId || '')
const canQuery = computed(() => session.hasPermission('accounting:query'))
const can = (permission: string) => session.hasPermission(permission)
const pageNo = ref(1)
const entries = ref<AccountingEntryPageResult>({ pageNo: 1, pageSize, total: 0, records: [] })
const detail = ref<AccountingEntryDetail | null>(null)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const traceOpen = ref(false)
const traceRows = ref<PaymentTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
const carryoverDialog = ref(false)
const carryoverOptions = ref<FinanceOperationsFormOptions>({ contracts: [] })
const carryoverForm = reactive({ contractId: '', carryoverDate: localDateInputValue() })
const carryoverContractOptions = computed(() =>
  carryoverOptions.value.contracts
    .filter((item) => item.contractType === 'MAIN')
    .map((item) => ({ value: item.id, label: `${item.contractCode} · ${item.contractName}` })),
)
let controller: AbortController | null = null

function canOpenPaymentTrace(row: AccountingEntryRecord): boolean {
  return paymentTraceSourceTypes.has(row.sourceType)
}

async function load(preservePage = false): Promise<void> {
  if (!canQuery.value) return
  if (!preservePage) pageNo.value = 1
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    entries.value = await loadAccountingEntries(
      { pageNo: pageNo.value, pageSize, projectId: projectId.value },
      request.signal,
    )
    const selected =
      entries.value.records.find((row) => row.id === detail.value?.entry.id) ||
      entries.value.records[0]
    detail.value = selected ? await loadAccountingEntryDetail(selected.id, request.signal) : null
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

function changePage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= entries.value.total) return
  pageNo.value = next
  void load(true)
}

async function selectEntry(id: string): Promise<void> {
  detail.value = await loadAccountingEntryDetail(id)
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

async function actEntry(row: AccountingEntryRecord, action: EntryAction): Promise<void> {
  if (action === 'approve') {
    return run(() => reviewAccountingEntry(row.id, true, '复核通过'), '凭证已复核')
  }
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

async function openTrace(id: string): Promise<void> {
  traceOpen.value = true
  traceRows.value = []
  traceError.value = ''
  traceLoading.value = true
  try {
    traceRows.value = await loadPaymentTraceByVoucher(id)
  } catch (cause) {
    traceError.value = cause instanceof Error ? cause.message : 'Trace 读取失败'
  } finally {
    traceLoading.value = false
  }
}

async function openCarryover(): Promise<void> {
  if (!projectId.value) return
  try {
    carryoverOptions.value = await loadFinanceOperationsFormOptions(projectId.value)
    carryoverForm.contractId = carryoverContractOptions.value[0]?.value ?? ''
    carryoverForm.carryoverDate = localDateInputValue()
    carryoverDialog.value = true
  } catch (cause) {
    showToast(
      'error',
      '无法创建成本结转',
      cause instanceof Error ? cause.message : '合同候选加载失败。',
    )
  }
}

async function submitCarryover(): Promise<void> {
  if (!projectId.value || !carryoverForm.contractId || !carryoverForm.carryoverDate) {
    showToast('warning', '信息不完整', '请选择项目、主合同和结转日期。')
    return
  }
  await run(
    () =>
      createAccountingCostCarryover({
        projectId: projectId.value,
        contractId: carryoverForm.contractId,
        carryoverDate: carryoverForm.carryoverDate,
      }),
    '成本结转凭证已生成',
  )
  carryoverDialog.value = false
}

watch(projectId, () => void load(), { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-control">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      title="无权访问会计凭证"
      description="系统未加载任何财务数据。"
    />
    <template v-else>
      <V2Card title="会计凭证" :heading-level="1">
        <template #actions>
          <div class="finance-control__actions">
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace">
              刷新
            </V2Button>
            <V2Button
              v-if="can('accounting:cost-carryover')"
              size="small"
              :disabled="!projectId"
              title="按固定八类映射将合同履约成本结转至主营业务成本"
              @click="openCarryover"
              >成本结转</V2Button
            >
          </div>
        </template>
      </V2Card>
      <V2PageState
        v-if="loading && !entries.records.length"
        kind="loading"
        title="正在加载"
        description="正在读取会计凭证。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="会计凭证加载失败"
        :description="errorMessage"
        ><template #actions><V2Button @click="load">重试</V2Button></template></V2PageState
      >
      <V2PageState
        v-else-if="!errorMessage && !entries.records.length"
        title="暂无会计凭证记录"
        description="当前范围暂无可访问记录。"
      />
      <template v-else>
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
                        v-if="can('payment:trace:query')"
                        size="small"
                        variant="ghost"
                        :disabled="!canOpenPaymentTrace(row)"
                        :title="
                          canOpenPaymentTrace(row)
                            ? '查看付款全链路'
                            : '该凭证未绑定付款申请，不属于付款 Trace'
                        "
                        @click="openTrace(row.id)"
                        >查看付款 Trace</V2Button
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
          <template #footer
            ><V2Pagination
              :total="entries.total"
              :page-no="pageNo"
              :page-size="pageSize"
              label="会计凭证分页"
              @update:page-no="changePage"
          /></template>
        </V2Card>
        <V2Card v-if="detail" :title="`分录明细 · ${detail.entry.entryCode}`" :heading-level="2"
          ><div
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
                  <th>会计科目编码</th>
                  <th>会计科目名称</th>
                  <th>摘要</th>
                  <th>金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in detail.lines" :key="row.id">
                  <td>{{ row.lineNo }}</td>
                  <td>{{ label(row.direction) }}</td>
                  <td>{{ row.accountCode || '—' }}</td>
                  <td>{{ row.accountName || row.costSubjectName || '—' }}</td>
                  <td>{{ row.summary }}</td>
                  <td>{{ amount(row.amount) }}</td>
                </tr>
              </tbody>
            </table>
          </div></V2Card
        >
      </template>
      <PaymentTraceDialog
        :open="traceOpen"
        :traces="traceRows"
        :loading="traceLoading"
        :error="traceError"
        @close="traceOpen = false"
      />
      <V2Dialog
        :open="carryoverDialog"
        title="项目成本结转"
        description="系统按1451八类余额生成借记6401、贷记1451的平衡凭证；历史凭证不改写。"
        :close-disabled="busy"
        :close-on-backdrop="false"
        @close="carryoverDialog = false"
      >
        <form
          id="accounting-cost-carryover-form"
          class="finance-control__form"
          @submit.prevent="submitCarryover"
        >
          <V2Select
            v-model="carryoverForm.contractId"
            label="权威主合同"
            :options="carryoverContractOptions"
            required
          />
          <V2Input v-model="carryoverForm.carryoverDate" label="结转日期" type="date" required />
        </form>
        <template #footer>
          <V2Button variant="secondary" :disabled="busy" @click="carryoverDialog = false"
            >取消</V2Button
          >
          <V2Button type="submit" form="accounting-cost-carryover-form" :loading="busy"
            >生成凭证</V2Button
          >
        </template>
      </V2Dialog>
    </template>
  </section>
</template>

<style scoped src="./finance-control.css"></style>
