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
import { showToast } from '@/components/toast'
import { dashboardStatusLabel, formatAmount } from '@/shared/display'
import { loadContractPage, loadPartners } from '@/services/commercial'
import { uploadSiteFile } from '@/services/delivery'
import {
  confirmCollection,
  confirmSalesInvoice,
  createCollection,
  createOwnerSettlement,
  createSalesInvoice,
  creditReceivable,
  loadApprovedContractRevenues,
  loadCollections,
  loadFundAccounts,
  loadReceivables,
  loadRevenueSettlements,
  loadSalesInvoices,
  reverseCollection,
  submitOwnerSettlement,
} from '@/services/finance'
import { loadEnabledDictDataByCode, type DictDataRecord } from '@/services/system-management'
import { localDateInputValue } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type {
  CollectionRecord,
  ContractRecord,
  ContractRevenueRecord,
  FundAccountRecord,
  OwnerSettlementRecord,
  PartnerRecord,
  ReceivableRecord,
  SalesInvoiceRecord,
} from '@cgc-pms/frontend-contracts'
import {
  collectionCommand,
  contractOptions as buildContractOptions,
  defaultOption,
  dictionaryOptions,
  emptyRevenueEditor,
  linkedPartnerOptions,
  salesInvoiceCommand,
  settlementCommand,
  type RevenueEditor,
} from './model'

type EditorKind = 'settlement' | 'salesInvoice' | 'collection'
type RevenueRow =
  | (OwnerSettlementRecord & { kind: 'settlement' })
  | (ReceivableRecord & { kind: 'receivable' })
  | (SalesInvoiceRecord & { kind: 'salesInvoice' })
  | (CollectionRecord & { kind: 'collection' })
type RevenueKind = RevenueRow['kind']
type Action = 'submit' | 'credit' | 'reverse'

const title = '收入与回款'
const pageSize = 10
const session = useSessionStore()
const workspace = useWorkspaceStore()
const projectId = computed(() => workspace.selectedProjectId || '')
const canQuery = computed(() => session.hasPermission('revenue:operations:query'))
const can = (action: string) =>
  session.hasAdminOrPermission(`revenue:${action}`) ||
  session.hasAdminOrPermission(
    action === 'reverse' ? 'revenue:collection:reverse' : 'revenue:operations:maintain',
  )
const canAdd = computed(() => can('maintain'))

const rows = ref<RevenueRow[]>([])
const revenuePageNo = ref<Record<RevenueKind, number>>({
  settlement: 1,
  receivable: 1,
  salesInvoice: 1,
  collection: 1,
})
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const dialog = ref(false)
const editor = ref<RevenueEditor | null>(null)
const editorKind = ref<EditorKind>('settlement')
const pending = ref<{ row: RevenueRow; action: Action } | null>(null)
const contracts = ref<ContractRecord[]>([])
const partners = ref<PartnerRecord[]>([])
const fundAccounts = ref<FundAccountRecord[]>([])
const contractRevenues = ref<ContractRevenueRecord[]>([])
const invoiceTypes = ref<DictDataRecord[]>([])
const settlementAttachment = ref<File | null>(null)
const salesInvoiceAttachment = ref<File | null>(null)
const collectionAttachment = ref<File | null>(null)
const settlementAttachmentInput = ref<HTMLInputElement | null>(null)
const settlementAttachmentTarget = ref<OwnerSettlementRecord | null>(null)
let controller: AbortController | null = null
let dictionariesLoaded = false

const hasRows = computed(() => rows.value.length > 0)
const revenueSections = computed(() =>
  (
    [
      ['settlement', '业主结算'],
      ['receivable', '应收款'],
      ['salesInvoice', '销项发票'],
      ['collection', '回款'],
    ] as const
  ).map(([key, sectionTitle]) => {
    const allRows = rows.value.filter((row) => row.kind === key)
    const currentPage = revenuePageNo.value[key]
    return {
      key,
      title: sectionTitle,
      total: allRows.length,
      pageNo: currentPage,
      rows: allRows.slice((currentPage - 1) * pageSize, currentPage * pageSize),
    }
  }),
)
const projectOptions = computed(() =>
  workspace.projects.filter(
    (item) => item.status === 'ACTIVE' || item.value === editor.value?.projectId,
  ),
)
const selectedContract = computed(() =>
  contracts.value.find((item) => item.id === editor.value?.contractId),
)
const contractOptions = computed(() =>
  buildContractOptions(contracts.value, 'revenue', editor.value?.contractId),
)
const customerOptions = computed(() =>
  linkedPartnerOptions(partners.value, selectedContract.value?.partyAId, editor.value?.customerId),
)
const invoiceTypeOptions = computed(() => dictionaryOptions(invoiceTypes.value))
const fundAccountOptions = computed(() =>
  fundAccounts.value
    .filter((item) => item.enabledFlag === 1)
    .map((item) => ({ value: item.id, label: `${item.accountCode} · ${item.accountName}` })),
)
const approvedRevenueOptions = computed(() =>
  contractRevenues.value
    .filter(
      (item) =>
        item.approvalStatus === 'APPROVED' &&
        item.projectId === editor.value?.projectId &&
        item.contractId === editor.value?.contractId,
    )
    .map((item) => ({
      value: item.id,
      label: `${item.revenueCode} · 收入确认 ${formatAmount(item.revenueAmount)}`,
    })),
)
const receivableOptions = computed(() =>
  rows.value
    .filter(
      (item): item is ReceivableRecord & { kind: 'receivable' } =>
        item.kind === 'receivable' &&
        item.status === 'OPEN' &&
        (!editor.value?.projectId || item.projectId === editor.value.projectId) &&
        (!editor.value?.contractId || item.contractId === editor.value.contractId) &&
        (!editor.value?.customerId || item.customerId === editor.value.customerId),
    )
    .map((item) => ({
      value: item.id,
      label: `${item.receivableCode} · 可分配 ${formatAmount(item.outstandingAmount)}`,
    })),
)

function rowText(row: RevenueRow): string {
  return row.kind === 'settlement'
    ? row.settlementCode
    : row.kind === 'receivable'
      ? row.receivableCode
      : row.kind === 'salesInvoice'
        ? row.invoiceNo
        : row.collectionCode
}

function rowMoney(row: RevenueRow): string {
  return formatAmount(
    row.kind === 'settlement'
      ? row.grossAmount
      : row.kind === 'receivable'
        ? row.outstandingAmount
        : row.kind === 'salesInvoice'
          ? row.totalAmount
          : row.amount,
  )
}

async function loadDictionaries(signal?: AbortSignal): Promise<void> {
  if (dictionariesLoaded) return
  invoiceTypes.value = await loadEnabledDictDataByCode('invoice_type', signal)
  dictionariesLoaded = true
}

async function load(): Promise<void> {
  if (!canQuery.value) return
  revenuePageNo.value = { settlement: 1, receivable: 1, salesInvoice: 1, collection: 1 }
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    await loadDictionaries(request.signal)
    const query = { projectId: projectId.value || undefined }
    const [settlements, receivables, salesInvoices, collections] = await Promise.all([
      loadRevenueSettlements(query, request.signal),
      loadReceivables(query, request.signal),
      loadSalesInvoices(query, request.signal),
      loadCollections(query, request.signal),
    ])
    rows.value = [
      ...settlements.map((item) => ({ ...item, kind: 'settlement' as const })),
      ...receivables.map((item) => ({ ...item, kind: 'receivable' as const })),
      ...salesInvoices.map((item) => ({ ...item, kind: 'salesInvoice' as const })),
      ...collections.map((item) => ({ ...item, kind: 'collection' as const })),
    ]
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

async function loadContracts(value: string): Promise<void> {
  contracts.value = []
  if (!value) return
  contracts.value = (await loadContractPage({ pageNo: 1, pageSize: 200, projectId: value })).records
}

async function changeProject(value: string): Promise<void> {
  if (!editor.value) return
  editor.value.projectId = value
  editor.value.contractId = ''
  editor.value.customerId = ''
  editor.value.revenueId = ''
  contractRevenues.value = []
  await loadContracts(value)
}

async function changeContract(value: string): Promise<void> {
  if (!editor.value) return
  editor.value.contractId = value
  editor.value.revenueId = ''
  editor.value.customerId = contracts.value.find((item) => item.id === value)?.partyAId || ''
  contractRevenues.value = []
  if (editorKind.value === 'settlement' && editor.value.projectId && value) {
    contractRevenues.value = (
      await loadApprovedContractRevenues(editor.value.projectId, value)
    ).records
  }
}

async function openForm(kind: EditorKind): Promise<void> {
  try {
    await loadDictionaries()
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : '请稍后重试。'
    errorMessage.value = message
    showToast('error', '业务字典加载失败', message)
    return
  }
  const value = emptyRevenueEditor(localDateInputValue())
  value.projectId = projectId.value
  value.invoiceType = defaultOption(invoiceTypeOptions.value, 'VAT_SPECIAL')
  editorKind.value = kind
  editor.value = value
  settlementAttachment.value = null
  salesInvoiceAttachment.value = null
  collectionAttachment.value = null
  contractRevenues.value = []
  dialog.value = true
  try {
    const jobs: Promise<unknown>[] = []
    if (!partners.value.length)
      jobs.push(loadPartners().then((page) => (partners.value = page.records)))
    if (kind === 'collection' && !fundAccounts.value.length) {
      jobs.push(loadFundAccounts().then((items) => (fundAccounts.value = items)))
    }
    await Promise.all(jobs)
    if (value.projectId) await loadContracts(value.projectId)
  } catch (cause) {
    dialog.value = false
    showToast('error', '候选项加载失败', cause instanceof Error ? cause.message : '请稍后重试。')
  }
}

function onAttachment(kind: EditorKind, event: Event): void {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  if (kind === 'settlement') settlementAttachment.value = file
  else if (kind === 'salesInvoice') salesInvoiceAttachment.value = file
  else collectionAttachment.value = file
}

async function save(): Promise<void> {
  if (!editor.value || busy.value) return
  busy.value = true
  try {
    const value = editor.value
    if (editorKind.value === 'settlement') {
      if (!settlementAttachment.value) throw new TypeError('业主结算附件不能为空')
      const settlement = await createOwnerSettlement(settlementCommand(value))
      await uploadSiteFile(
        settlementAttachment.value,
        'OWNER_SETTLEMENT',
        settlement.id,
        'OWNER_CONFIRMATION',
      )
    } else if (editorKind.value === 'salesInvoice') {
      if (!salesInvoiceAttachment.value) throw new TypeError('销项发票附件不能为空')
      const command = salesInvoiceCommand(value)
      const salesInvoiceId = (await createSalesInvoice(command)).id
      await uploadSiteFile(
        salesInvoiceAttachment.value,
        'SALES_INVOICE',
        salesInvoiceId,
        'ELECTRONIC_INVOICE',
      )
      await confirmSalesInvoice(salesInvoiceId, command.allocations)
    } else {
      if (!collectionAttachment.value) throw new TypeError('银行回单不能为空')
      const command = collectionCommand(value)
      const collectionId = (await createCollection(command)).id
      await uploadSiteFile(
        collectionAttachment.value,
        'COLLECTION_RECORD',
        collectionId,
        'BANK_RECEIPT',
      )
      await confirmCollection(collectionId, command.allocations ?? [])
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

function requestSettlementAttachment(row: RevenueRow): void {
  if (row.kind !== 'settlement') return
  settlementAttachmentTarget.value = row
  settlementAttachmentInput.value?.click()
}

async function uploadSettlementAttachment(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const target = settlementAttachmentTarget.value
  input.value = ''
  settlementAttachmentTarget.value = null
  if (!file || !target || busy.value) return
  busy.value = true
  try {
    await uploadSiteFile(file, 'OWNER_SETTLEMENT', target.id, 'OWNER_CONFIRMATION')
    showToast('success', '附件已上传', '业主结算可提交审批。')
  } catch (cause) {
    showToast('error', '附件上传失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

async function confirmAction(): Promise<void> {
  const value = pending.value
  pending.value = null
  if (!value || busy.value) return
  busy.value = true
  try {
    if (value.action === 'submit' && value.row.kind === 'settlement') {
      await submitOwnerSettlement(value.row.id)
    } else if (value.action === 'credit' && value.row.kind === 'receivable') {
      await creditReceivable(
        value.row.id,
        value.row.outstandingAmount,
        '人工核减',
        crypto.randomUUID(),
      )
    } else if (value.action === 'reverse' && value.row.kind === 'collection') {
      await reverseCollection(value.row.id, '人工冲销', crypto.randomUUID())
    }
    await load()
    showToast('success', '操作成功', '已按服务端最新数据刷新。')
  } catch (cause) {
    showToast('error', '操作失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

const confirmationTitle = computed(() =>
  pending.value?.action === 'submit'
    ? '确认提交审批'
    : pending.value?.action === 'credit'
      ? '确认核减全部未收金额'
      : '确认冲销回款',
)

watch(projectId, () => void load(), { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-workspace">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      title="无权访问收入与回款"
      description="系统未加载财务数据。"
    />
    <template v-else>
      <V2Card :title="title" :heading-level="1">
        <template #actions>
          <div class="finance-workspace__actions">
            <V2Button v-if="canAdd" size="small" @click="openForm('settlement')"
              >新建业主结算</V2Button
            >
            <V2Button
              v-if="canAdd"
              size="small"
              variant="secondary"
              @click="openForm('salesInvoice')"
              >新建销项发票</V2Button
            >
            <V2Button v-if="canAdd" size="small" variant="secondary" @click="openForm('collection')"
              >新建回款</V2Button
            >
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace"
              >刷新</V2Button
            >
          </div>
        </template>
      </V2Card>
      <V2PageState
        v-if="loading && !hasRows"
        kind="loading"
        title="正在加载"
        description="正在读取收入与回款。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="收入与回款加载失败"
        :description="errorMessage"
        ><template #actions><V2Button @click="load">重试</V2Button></template></V2PageState
      >
      <V2PageState
        v-else-if="!errorMessage && !hasRows"
        title="暂无收入与回款记录"
        description="当前项目范围没有可访问数据。"
      />
      <section v-else class="finance-workspace__revenue-sections">
        <V2Card
          v-for="section in revenueSections"
          :key="section.key"
          :title="section.key === 'settlement' ? undefined : section.title"
          :heading-level="2"
        >
          <V2PageState
            v-if="!errorMessage && !section.rows.length"
            :title="`暂无${section.title}记录`"
            description="当前项目范围没有可访问数据。"
          />
          <div
            v-else
            class="finance-workspace__table-wrap"
            role="region"
            :aria-label="`${section.title}表格`"
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
                <tr v-for="(row, index) in section.rows" :key="`${row.kind}-${row.id}`">
                  <td>{{ rowText(row) }}</td>
                  <td>
                    {{
                      workspace.projects.find((item) => item.value === row.projectId)?.label ||
                      '项目名称缺失'
                    }}
                  </td>
                  <td>{{ dashboardStatusLabel(row.status) }}</td>
                  <td>{{ rowMoney(row) }}</td>
                  <td class="v2-table-cell--actions">
                    <V2ActionMenu
                      :label="`${rowText(row)}更多操作`"
                      :placement="index >= section.rows.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <V2Button
                        v-if="row.kind === 'settlement' && row.status === 'DRAFT' && can('submit')"
                        size="small"
                        variant="ghost"
                        @click="pending = { row, action: 'submit' }"
                        >提交</V2Button
                      >
                      <V2Button
                        v-if="
                          row.kind === 'settlement' && row.status === 'DRAFT' && can('maintain')
                        "
                        size="small"
                        variant="ghost"
                        @click="requestSettlementAttachment(row)"
                        >上传附件</V2Button
                      >
                      <V2Button
                        v-if="row.kind === 'receivable' && row.status === 'OPEN' && can('maintain')"
                        size="small"
                        variant="ghost"
                        @click="pending = { row, action: 'credit' }"
                        >应收核减</V2Button
                      >
                      <V2Button
                        v-if="
                          row.kind === 'collection' && row.status === 'CONFIRMED' && can('reverse')
                        "
                        size="small"
                        variant="ghost"
                        @click="pending = { row, action: 'reverse' }"
                        >回款冲销</V2Button
                      >
                    </V2ActionMenu>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer
            ><V2Pagination
              :total="section.total"
              :page-no="section.pageNo"
              :page-size="pageSize"
              :label="`${section.title}分页`"
              @update:page-no="revenuePageNo[section.key] = $event"
          /></template>
        </V2Card>
      </section>

      <V2Dialog
        v-model:open="dialog"
        :title="
          editorKind === 'settlement'
            ? '新建业主结算'
            : editorKind === 'salesInvoice'
              ? '新建销项发票'
              : '新建回款'
        "
        :close-disabled="busy"
        :close-on-backdrop="false"
      >
        <form
          v-if="editor"
          id="revenue-operation-form"
          class="finance-workspace__form"
          @submit.prevent="save"
        >
          <V2Select
            v-model="editor.projectId"
            label="项目"
            :options="projectOptions"
            required
            @update:model-value="changeProject"
          />
          <V2Select
            v-model="editor.contractId"
            label="合同"
            :options="contractOptions"
            required
            :disabled="!contractOptions.length"
            @update:model-value="changeContract"
          />
          <p v-if="!contractOptions.length">当前项目无可用合同，不能提交。</p>
          <template v-if="editorKind === 'settlement'">
            <V2Select
              v-model="editor.revenueId"
              label="已审批收入确认"
              :options="approvedRevenueOptions"
              required
              :disabled="!editor.contractId || !approvedRevenueOptions.length"
            />
            <p v-if="editor.contractId && !approvedRevenueOptions.length">
              当前项目合同无已审批收入确认，不能提交。
            </p>
            <V2Select
              v-model="editor.customerId"
              label="建设单位"
              :options="customerOptions"
              required
            />
            <V2Input
              v-model="editor.settlementPeriod"
              label="结算期间"
              placeholder="YYYY-MM"
              required
            />
            <V2Input
              v-model="editor.settlementDate"
              label="结算日期"
              placeholder="YYYY-MM-DD"
              required
            />
            <V2Input
              v-model="editor.grossAmount"
              label="含税结算金额"
              :decimal-scale="2"
              required
            />
            <V2Input v-model="editor.taxAmount" label="税额" :decimal-scale="2" required />
            <V2Input v-model="editor.retentionAmount" label="质保金" :decimal-scale="2" required />
            <V2Input v-model="editor.dueDate" label="到期日期" placeholder="YYYY-MM-DD" required />
            <label class="v2-field"
              ><span class="v2-field__label">业主结算附件*</span
              ><input type="file" required @change="onAttachment('settlement', $event)"
            /></label>
          </template>
          <template v-else-if="editorKind === 'salesInvoice'">
            <V2Select
              v-model="editor.customerId"
              label="建设单位"
              :options="customerOptions"
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
              v-model="editor.invoiceDate"
              label="开票日期"
              placeholder="YYYY-MM-DD"
              required
            />
            <V2Input
              v-model="editor.amountWithoutTax"
              label="不含税金额"
              :decimal-scale="2"
              required
            />
            <V2Input v-model="editor.taxAmount" label="税额" :decimal-scale="2" required />
            <V2Select
              v-model="editor.receivableId"
              label="应收款"
              :options="receivableOptions"
              required
            />
            <V2Input
              v-model="editor.allocationAmount"
              label="分配金额"
              :decimal-scale="2"
              required
            />
            <label class="v2-field"
              ><span class="v2-field__label">销项发票附件*</span
              ><input
                type="file"
                accept=".pdf,image/*"
                required
                @change="onAttachment('salesInvoice', $event)"
            /></label>
          </template>
          <template v-else>
            <V2Select
              v-model="editor.customerId"
              label="建设单位"
              :options="customerOptions"
              required
            />
            <V2Select
              v-model="editor.fundAccountId"
              label="资金账户"
              :options="fundAccountOptions"
              required
            />
            <V2Input v-model="editor.externalTxnNo" label="外部流水号" required />
            <V2Input v-model="editor.collectedAt" type="datetime-local" label="到账时间" required />
            <V2Input
              v-model="editor.collectionAmount"
              label="回款金额"
              :decimal-scale="2"
              required
            />
            <V2Input v-model="editor.payerName" label="付款单位" required />
            <V2Select
              v-model="editor.receivableId"
              label="应收款"
              :options="receivableOptions"
              required
            />
            <V2Input
              v-model="editor.allocationAmount"
              label="分配金额"
              :decimal-scale="2"
              required
            />
            <label class="v2-field"
              ><span class="v2-field__label">银行回单*</span
              ><input type="file" required @change="onAttachment('collection', $event)"
            /></label>
          </template>
          <V2Input v-model="editor.remark" label="备注" />
        </form>
        <template #footer
          ><V2Button type="button" variant="secondary" :disabled="busy" @click="dialog = false"
            >取消</V2Button
          ><V2Button type="submit" form="revenue-operation-form" :loading="busy"
            >保存</V2Button
          ></template
        >
      </V2Dialog>

      <input
        ref="settlementAttachmentInput"
        class="v2-visually-hidden"
        type="file"
        aria-label="业主结算补传附件"
        @change="uploadSettlementAttachment"
      />
      <V2ConfirmDialog
        :open="Boolean(pending)"
        :title="confirmationTitle"
        :description="
          pending
            ? `${rowText(pending.row)} 将执行${confirmationTitle.replace('确认', '')}，服务端仍会校验状态、权限和余额。`
            : ''
        "
        confirm-text="确认执行"
        :loading="busy"
        @confirm="confirmAction"
        @close="pending = null"
      />
    </template>
  </section>
</template>

<style scoped src="./finance-workspace.css"></style>
