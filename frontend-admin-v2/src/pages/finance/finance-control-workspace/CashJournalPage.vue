<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { V2ActionMenu, V2Button, V2Card, V2PageState, V2Pagination } from '@/components'
import PaymentTraceDialog from '@/components/finance/PaymentTraceDialog.vue'
import { showToast } from '@/components/toast'
import { uploadSiteFile } from '@/services/delivery'
import {
  archiveCashJournal,
  loadCashJournal,
  loadPaymentTraceByCashJournal,
  reopenCashJournal,
  reverseCashJournal,
} from '@/services/finance'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type {
  CashJournalPage as CashJournalPageResult,
  PaymentTraceRecord,
} from '@cgc-pms/frontend-contracts'
import { amount, askReason, label } from './model'

type JournalAction = 'archive' | 'reverse' | 'reopen'

const pageSize = 10
const session = useSessionStore()
const workspace = useWorkspaceStore()
const projectId = computed(() => workspace.selectedProjectId || '')
const canQuery = computed(() => session.hasPermission('cashbook:journal:query'))
const can = (permission: string) => session.hasPermission(permission)
const isSuperAdmin = computed(() => session.roles.includes('SUPER_ADMIN'))
const pageNo = ref(1)
const journal = ref<CashJournalPageResult>({ pageNo: 1, pageSize, total: 0, records: [] })
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const traceOpen = ref(false)
const traceRows = ref<PaymentTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
let controller: AbortController | null = null

async function load(preservePage = false): Promise<void> {
  if (!canQuery.value) return
  if (!preservePage) pageNo.value = 1
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    journal.value = await loadCashJournal(
      { pageNo: pageNo.value, pageSize, projectId: projectId.value },
      request.signal,
    )
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
  if (next < 1 || (next - 1) * pageSize >= journal.value.total) return
  pageNo.value = next
  void load(true)
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

async function actJournal(
  row: CashJournalPageResult['records'][number],
  action: JournalAction,
): Promise<void> {
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
  row: CashJournalPageResult['records'][number],
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

async function openTrace(id: string): Promise<void> {
  traceOpen.value = true
  traceRows.value = []
  traceError.value = ''
  traceLoading.value = true
  try {
    traceRows.value = [await loadPaymentTraceByCashJournal(id)]
  } catch (cause) {
    traceError.value = cause instanceof Error ? cause.message : 'Trace 读取失败'
  } finally {
    traceLoading.value = false
  }
}

watch(projectId, () => void load(), { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-control">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      title="无权访问资金日记账"
      description="系统未加载任何财务数据。"
    />
    <template v-else>
      <V2Card title="资金日记账" :heading-level="1">
        <template #actions>
          <div class="finance-control__actions">
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace">
              刷新
            </V2Button>
          </div>
        </template>
      </V2Card>
      <V2PageState
        v-if="loading && !journal.records.length"
        kind="loading"
        title="正在加载"
        description="正在读取资金日记账。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="资金日记账加载失败"
        :description="errorMessage"
        ><template #actions><V2Button @click="load">重试</V2Button></template></V2PageState
      >
      <V2Card v-else title="资金流水" :heading-level="2">
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
                    <V2Button
                      v-if="can('payment:trace:query')"
                      size="small"
                      variant="ghost"
                      @click="openTrace(row.id)"
                      >查看 Trace</V2Button
                    >
                    <label
                      v-if="
                        ['DRAFT', 'PENDING_ARCHIVE'].includes(row.status) &&
                        (can('file:upload') || can('cashbook:journal:maintain'))
                      "
                      class="v2-action-menu__item"
                      ><span>上传银行回单</span
                      ><input
                        class="v2-visually-hidden"
                        type="file"
                        accept=".pdf,image/*"
                        :disabled="busy"
                        @change="uploadJournalEvidence(row, $event)"
                    /></label>
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
        <template #footer
          ><V2Pagination
            :total="journal.total"
            :page-no="pageNo"
            :page-size="pageSize"
            label="资金流水分页"
            @update:page-no="changePage"
        /></template>
      </V2Card>
      <PaymentTraceDialog
        :open="traceOpen"
        :traces="traceRows"
        :loading="traceLoading"
        :error="traceError"
        @close="traceOpen = false"
      />
    </template>
  </section>
</template>

<style scoped src="./finance-control.css"></style>
