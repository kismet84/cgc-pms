<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  V2ActionMenu,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
} from '@/components'
import PaymentTraceDialog from '@/components/finance/PaymentTraceDialog.vue'
import { showToast } from '@/components/toast'
import { dashboardStatusLabel, formatAmount } from '@/shared/display'
import { uploadSiteFile } from '@/services/delivery'
import {
  createInvoice,
  deleteInvoice,
  loadInvoices,
  loadPaymentTraceByInvoice,
  loadPayRecordOptions,
  saveInvoiceAllocations,
  updateInvoice,
  verifyInvoice,
} from '@/services/finance'
import { loadEnabledDictDataByCode, type DictDataRecord } from '@/services/system-management'
import { localDateInputValue } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type {
  InvoiceRecord,
  PaymentTraceRecord,
  PayRecordOption,
} from '@cgc-pms/frontend-contracts'
import {
  defaultOption,
  dictionaryOptions,
  emptyInvoiceEditor,
  invoiceCommand,
  type InvoiceEditor,
} from './model'

type Action = 'delete' | 'verify'

const title = '发票管理'
const pageSize = 10
const session = useSessionStore()
const workspace = useWorkspaceStore()
const projectId = computed(() => workspace.selectedProjectId || '')
const canQuery = computed(() => session.hasPermission('invoice:query'))
const can = (action: string) => session.hasAdminOrPermission(`invoice:${action}`)
const canAdd = computed(() => can('add'))
const canTrace = computed(() => session.hasAdminOrPermission('payment:trace:query'))

const rows = ref<InvoiceRecord[]>([])
const pageNo = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const dialog = ref(false)
const editor = ref<InvoiceEditor | null>(null)
const pending = ref<{ row: InvoiceRecord; action: Action } | null>(null)
const payRecords = ref<PayRecordOption[]>([])
const invoiceTypes = ref<DictDataRecord[]>([])
const invoiceAttachment = ref<File | null>(null)
const traceOpen = ref(false)
const traceRows = ref<PaymentTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
let controller: AbortController | null = null
let dictionariesLoaded = false

const hasRows = computed(() => rows.value.length > 0)
const payRecordOptions = computed(() =>
  payRecords.value.map((item) => ({
    value: item.id,
    label: `${item.voucherNo || `付款记录 ${item.id}`} · ${formatAmount(item.payAmount)}`,
  })),
)
const invoiceTypeOptions = computed(() => dictionaryOptions(invoiceTypes.value))

async function loadDictionaries(signal?: AbortSignal): Promise<void> {
  if (dictionariesLoaded) return
  invoiceTypes.value = await loadEnabledDictDataByCode('invoice_type', signal)
  dictionariesLoaded = true
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
    await loadDictionaries(request.signal)
    const page = await loadInvoices(
      { projectId: projectId.value || undefined, pageNo: pageNo.value, pageSize },
      request.signal,
    )
    const maxPage = Math.max(1, Math.ceil(page.total / pageSize))
    if (pageNo.value > maxPage) {
      pageNo.value = maxPage
      await load(true)
      return
    }
    rows.value = page.records
    total.value = page.total
  } catch (cause) {
    if (!request.signal.aborted) {
      errorMessage.value = cause instanceof Error ? cause.message : '读取失败'
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
  if (next < 1 || (next - 1) * pageSize >= total.value) return
  pageNo.value = next
  void load(true)
}

async function openForm(row?: InvoiceRecord): Promise<void> {
  try {
    await loadDictionaries()
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : '请稍后重试。'
    errorMessage.value = message
    showToast('error', '业务字典加载失败', message)
    return
  }
  const value = emptyInvoiceEditor(localDateInputValue())
  value.invoiceType = defaultOption(invoiceTypeOptions.value, 'VAT_SPECIAL')
  invoiceAttachment.value = null
  if (row) {
    value.id = row.id
    value.payRecordId = row.payRecordId || ''
    value.invoiceNo = row.invoiceNo
    value.invoiceType = row.invoiceType || defaultOption(invoiceTypeOptions.value, 'VAT_SPECIAL')
    value.invoiceAmount = row.invoiceAmount
    value.taxRate = row.taxRate || ''
    value.taxAmount = row.taxAmount || ''
    value.invoiceDate = row.invoiceDate || localDateInputValue()
    value.sellerName = row.sellerName || ''
    value.buyerName = row.buyerName || ''
  }
  editor.value = value
  dialog.value = true
  try {
    if (!payRecords.value.length) {
      payRecords.value = (await loadPayRecordOptions()).records
    }
  } catch (cause) {
    dialog.value = false
    showToast('error', '候选项加载失败', cause instanceof Error ? cause.message : '请稍后重试。')
  }
}

function onInvoiceAttachment(event: Event): void {
  invoiceAttachment.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function save(): Promise<void> {
  if (!editor.value || busy.value) return
  busy.value = true
  try {
    const value = editor.value
    const command = invoiceCommand(value)
    if (!value.id && !invoiceAttachment.value) throw new TypeError('发票附件不能为空')
    if (value.id) {
      await updateInvoice(value.id, command)
    } else {
      const invoiceId = await createInvoice(command)
      await saveInvoiceAllocations(invoiceId, [
        { payRecordId: command.payRecordId, allocatedAmount: command.invoiceAmount },
      ])
      if (invoiceAttachment.value) {
        await uploadSiteFile(invoiceAttachment.value, 'INVOICE', invoiceId, 'ELECTRONIC_INVOICE')
      }
    }
    dialog.value = false
    await load()
    showToast('success', '保存成功', '已按服务端最新数据刷新。')
  } catch (cause) {
    showToast('error', '保存失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

async function openTrace(row: InvoiceRecord): Promise<void> {
  traceOpen.value = true
  traceRows.value = []
  traceError.value = ''
  traceLoading.value = true
  try {
    traceRows.value = await loadPaymentTraceByInvoice(row.id)
  } catch (cause) {
    traceError.value = cause instanceof Error ? cause.message : 'Trace 读取失败'
  } finally {
    traceLoading.value = false
  }
}

async function confirmAction(): Promise<void> {
  const value = pending.value
  pending.value = null
  if (!value || busy.value) return
  busy.value = true
  try {
    if (value.action === 'delete') await deleteInvoice(value.row.id)
    else await verifyInvoice(value.row.id, 'VERIFIED')
    await load()
    showToast('success', '操作成功', '已按服务端最新数据刷新。')
  } catch (cause) {
    showToast('error', '操作失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

watch(projectId, () => void load(), { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-workspace">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      title="无权访问发票管理"
      description="系统未加载财务数据。"
    />
    <template v-else>
      <V2Card :title="title" :heading-level="1">
        <template #actions>
          <div class="finance-workspace__actions">
            <V2Button v-if="canAdd" size="small" @click="openForm()">新建发票管理</V2Button>
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace">
              刷新
            </V2Button>
          </div>
        </template>
      </V2Card>
      <V2PageState
        v-if="loading && !hasRows"
        kind="loading"
        title="正在加载"
        description="正在读取发票管理。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="发票管理加载失败"
        :description="errorMessage"
        ><template #actions><V2Button @click="load">重试</V2Button></template></V2PageState
      >
      <V2PageState
        v-else-if="!errorMessage && !hasRows"
        title="暂无发票管理记录"
        description="当前项目范围没有可访问数据。"
      />
      <V2Card v-else :heading-level="2">
        <div
          class="finance-workspace__table-wrap"
          role="region"
          aria-label="发票管理表格"
          tabindex="0"
        >
          <table class="v2-table finance-workspace__table">
            <thead>
              <tr>
                <th>编号</th>
                <th>项目</th>
                <th>状态</th>
                <th>金额</th>
                <th class="v2-table-cell--actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in rows" :key="row.id">
                <td>
                  <button
                    v-if="canTrace"
                    type="button"
                    class="v2-table__record-link"
                    @click="openTrace(row)"
                  >
                    {{ row.invoiceNo }}</button
                  ><span v-else>{{ row.invoiceNo }}</span>
                </td>
                <td>
                  {{
                    workspace.projects.find((item) => item.value === row.projectId)?.label ||
                    '项目名称缺失'
                  }}
                </td>
                <td>{{ dashboardStatusLabel(row.verifyStatus) }}</td>
                <td>{{ formatAmount(row.invoiceAmount) }}</td>
                <td class="v2-table-cell--actions">
                  <V2ActionMenu
                    :label="`${row.invoiceNo}更多操作`"
                    :placement="index >= rows.length - 3 ? 'top-end' : 'bottom-end'"
                  >
                    <V2Button
                      v-if="row.verifyStatus !== 'VERIFIED' && can('edit')"
                      size="small"
                      variant="ghost"
                      @click="openForm(row)"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="row.verifyStatus !== 'VERIFIED' && can('delete')"
                      size="small"
                      variant="ghost"
                      @click="pending = { row, action: 'delete' }"
                      >删除</V2Button
                    >
                    <V2Button
                      v-if="row.verifyStatus !== 'VERIFIED' && can('verify')"
                      size="small"
                      variant="ghost"
                      @click="pending = { row, action: 'verify' }"
                      >验真</V2Button
                    >
                  </V2ActionMenu>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer
          ><V2Pagination
            :total="total"
            :page-no="pageNo"
            :page-size="pageSize"
            label="发票管理分页"
            :disabled="loading"
            @update:page-no="changePage"
        /></template>
      </V2Card>

      <V2Dialog
        v-model:open="dialog"
        :title="editor?.id ? '编辑发票管理' : '新建发票管理'"
        :close-disabled="busy"
        :close-on-backdrop="false"
      >
        <form
          v-if="editor"
          id="invoice-management-form"
          class="finance-workspace__form"
          @submit.prevent="save"
        >
          <V2Select
            v-model="editor.payRecordId"
            label="付款记录"
            :options="payRecordOptions"
            required
          />
          <V2Input v-model="editor.invoiceNo" label="发票号码" required />
          <V2Select
            v-model="editor.invoiceType"
            label="发票类型"
            :options="invoiceTypeOptions"
            required
          />
          <V2Input
            v-model="editor.invoiceAmount"
            label="发票金额"
            :decimal-scale="2"
            required
            hint="按字符串提交"
          />
          <V2Input
            v-model="editor.invoiceDate"
            label="开票日期"
            placeholder="YYYY-MM-DD"
            required
          />
          <V2Input
            v-model="editor.taxRate"
            label="税率"
            :decimal-scale="2"
            hint="按服务端字符串口径提交"
          />
          <V2Input
            v-model="editor.taxAmount"
            label="税额"
            :decimal-scale="2"
            hint="按服务端字符串口径提交"
          />
          <V2Input v-model="editor.sellerName" label="销售方" />
          <V2Input v-model="editor.buyerName" label="购买方" />
          <label
            >发票附件<span v-if="!editor.id">*</span
            ><input
              type="file"
              accept=".pdf,image/*"
              :required="!editor.id"
              @change="onInvoiceAttachment"
          /></label>
        </form>
        <template #footer
          ><V2Button type="button" variant="secondary" :disabled="busy" @click="dialog = false"
            >取消</V2Button
          ><V2Button type="submit" form="invoice-management-form" :loading="busy"
            >保存</V2Button
          ></template
        >
      </V2Dialog>
      <V2ConfirmDialog
        :open="Boolean(pending)"
        :title="pending?.action === 'delete' ? '确认删除记录' : '确认标记验真通过'"
        :description="
          pending
            ? `${pending.row.invoiceNo} 将执行${pending.action === 'delete' ? '删除记录' : '标记验真通过'}，服务端仍会校验状态、权限和余额。`
            : ''
        "
        confirm-text="确认执行"
        :loading="busy"
        @confirm="confirmAction"
        @close="pending = null"
      />
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

<style scoped src="./finance-workspace.css"></style>
