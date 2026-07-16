<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import {
  createCollection,
  createOwnerSettlement,
  createSalesInvoice,
  getCollections,
  getOwnerSettlements,
  getReceivables,
  getRevenueDashboard,
  getSalesInvoices,
  runRevenueReconciliation,
  submitOwnerSettlement,
  type RevenueRow,
} from '@/api/modules/revenueOperations'
import { useReferenceStore } from '@/stores/reference'
import { uploadFile } from '@/api/modules/file'

const referenceStore = useReferenceStore()
const { projects, contracts, partners } = storeToRefs(referenceStore)
const loading = ref(false)
const projectId = ref<string>()
const dashboard = ref<RevenueRow>({})
const settlements = ref<RevenueRow[]>([])
const receivables = ref<RevenueRow[]>([])
const invoices = ref<RevenueRow[]>([])
const collections = ref<RevenueRow[]>([])
const settlementOpen = ref(false)
const invoiceOpen = ref(false)
const collectionOpen = ref(false)
const settlementFile = ref<File>()
const invoiceFile = ref<File>()
const collectionFile = ref<File>()

const today = new Date().toISOString().slice(0, 10)
const settlementForm = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  revenueId: undefined as string | undefined,
  customerId: undefined as string | undefined,
  settlementPeriod: today.slice(0, 7), settlementDate: today,
  grossAmount: undefined as number | undefined, taxAmount: 0, retentionAmount: 0,
  dueDate: today, attachmentCount: 1, remark: '',
})
const invoiceForm = reactive({
  projectId: undefined as string | undefined, contractId: undefined as string | undefined,
  customerId: undefined as string | undefined, receivableId: undefined as string | undefined,
  invoiceCode: '', invoiceNo: '', invoiceType: 'VAT_SPECIAL', invoiceDate: today,
  amountWithoutTax: undefined as number | undefined, taxAmount: 0, allocatedAmount: undefined as number | undefined,
  attachmentCount: 1, remark: '',
})
const collectionForm = reactive({
  projectId: undefined as string | undefined, contractId: undefined as string | undefined,
  customerId: undefined as string | undefined, receivableId: undefined as string | undefined,
  fundAccountId: '', externalTxnNo: '', collectedAt: `${today}T12:00:00`,
  amount: undefined as number | undefined, allocatedAmount: undefined as number | undefined,
  payerName: '', attachmentCount: 1, remark: '',
})

const currentReceivables = computed(() => receivables.value.filter((row) =>
  !projectId.value || String(row.project_id) === String(projectId.value)))

function value(row: RevenueRow, ...keys: string[]) {
  const key = keys.find((item) => row[item] !== undefined)
  return key ? row[key] : undefined
}
function money(input: unknown) {
  return Number(input ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function percent(input: unknown) { return `${(Number(input ?? 0) * 100).toFixed(2)}%` }
function rowId(row: RevenueRow) { return String(row.id) }
function selectFile(target: 'settlement' | 'invoice' | 'collection', event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (target === 'settlement') settlementFile.value = file
  if (target === 'invoice') invoiceFile.value = file
  if (target === 'collection') collectionFile.value = file
}

async function load() {
  loading.value = true
  try {
    const id = projectId.value
    ;[settlements.value, receivables.value, invoices.value, collections.value] = await Promise.all([
      getOwnerSettlements(id), getReceivables(id), getSalesInvoices(id), getCollections(id),
    ])
    dashboard.value = id ? await getRevenueDashboard(id) : {}
  } finally { loading.value = false }
}

async function onProjectChange(id?: string) {
  projectId.value = id
  if (id) await referenceStore.fetchContracts({ projectId: id })
  await load()
}

function openSettlement() {
  settlementForm.projectId = projectId.value
  settlementOpen.value = true
}
async function saveSettlement() {
  if (!settlementForm.projectId || !settlementForm.contractId || !settlementForm.customerId || !settlementForm.grossAmount) {
    return message.warning('请完整填写项目、合同、业主和结算金额')
  }
  if (!settlementFile.value) return message.warning('请上传业主确认单或结算附件')
  const created = await createOwnerSettlement({ ...settlementForm })
  await uploadFile(settlementFile.value, 'OWNER_SETTLEMENT', String(created.id), 'CONTRACT_ATTACHMENT')
  settlementOpen.value = false; await load(); message.success('业主结算草稿已创建')
}
function submitSettlement(row: RevenueRow) {
  Modal.confirm({ title: '提交业主结算审批？', content: '提交后进入多级审批，审批通过才会自动生成应收。', async onOk() {
    await submitOwnerSettlement(rowId(row)); await load(); message.success('已提交审批')
  } })
}

function openInvoice() {
  invoiceForm.projectId = projectId.value
  invoiceOpen.value = true
}
async function saveInvoice() {
  if (!invoiceForm.projectId || !invoiceForm.contractId || !invoiceForm.customerId || !invoiceForm.receivableId ||
      !invoiceForm.invoiceNo || invoiceForm.amountWithoutTax === undefined || !invoiceForm.allocatedAmount) {
    return message.warning('请完整填写发票及应收分配信息')
  }
  const { receivableId, allocatedAmount, ...payload } = invoiceForm
  if (!invoiceFile.value) return message.warning('请上传电子发票或扫描件')
  const created = await createSalesInvoice({ ...payload, allocations: [{ receivableId, amount: allocatedAmount }] })
  await uploadFile(invoiceFile.value, 'SALES_INVOICE', String(created.id), 'ELECTRONIC_INVOICE')
  invoiceOpen.value = false; await load(); message.success('销项发票已登记并关联应收')
}

function openCollection() {
  collectionForm.projectId = projectId.value
  collectionOpen.value = true
}
async function saveCollection() {
  if (!collectionForm.projectId || !collectionForm.contractId || !collectionForm.customerId || !collectionForm.fundAccountId ||
      !collectionForm.externalTxnNo || !collectionForm.amount || !collectionForm.payerName) {
    return message.warning('请完整填写到账必填信息')
  }
  const { receivableId, allocatedAmount, ...payload } = collectionForm
  const allocations = receivableId && allocatedAmount ? [{ receivableId, amount: allocatedAmount }] : []
  if (!collectionFile.value) return message.warning('请上传银行回单或到账凭证')
  const created = await createCollection({ ...payload, allocations })
  await uploadFile(collectionFile.value, 'COLLECTION_RECORD', String(created.id), 'BANK_RECEIPT')
  collectionOpen.value = false; await load(); message.success('回款已登记，现金日记与会计凭证已自动生成')
}

async function reconcile() {
  const result = await runRevenueReconciliation(today)
  message.success(`收入回款对账完成，发现 ${Number(result.issue_count ?? 0)} 项差异`)
}

onMounted(async () => {
  await Promise.all([referenceStore.fetchProjects(), referenceStore.fetchContracts(), referenceStore.fetchPartners()])
  await load()
})
</script>

<template>
  <div class="revenue-page">
    <a-page-header title="项目收入与回款" sub-title="收入确认、业主结算、应收、销项发票、到账、现金日记与凭证全链追溯">
      <template #extra>
        <a-select v-model:value="projectId" allow-clear placeholder="选择项目" style="width:260px" @change="onProjectChange">
          <a-select-option v-for="p in projects ?? []" :key="p.id" :value="p.id">{{ p.projectName }}</a-select-option>
        </a-select>
        <a-button @click="reconcile">日终对账</a-button>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </template>
    </a-page-header>

    <a-alert class="chain-alert" type="info" show-icon message="业务主线：项目 → 业主合同 → 收入确认 → 业主结算 → 应收 → 销项发票 → 回款 → 现金日记/会计凭证 → 驾驶舱" />
    <a-row :gutter="16" class="summary-row">
      <a-col :span="4"><a-card><a-statistic title="确认收入" :value="Number(dashboard.confirmedRevenue ?? 0)" :precision="2" /></a-card></a-col>
      <a-col :span="4"><a-card><a-statistic title="结算金额" :value="Number(dashboard.settledAmount ?? 0)" :precision="2" /></a-card></a-col>
      <a-col :span="4"><a-card><a-statistic title="应收金额" :value="Number(dashboard.receivableAmount ?? 0)" :precision="2" /></a-card></a-col>
      <a-col :span="4"><a-card><a-statistic title="回款金额" :value="Number(dashboard.collectedAmount ?? 0)" :precision="2" /></a-card></a-col>
      <a-col :span="4"><a-card><a-statistic title="未收金额" :value="Number(dashboard.outstandingAmount ?? 0)" :precision="2" /></a-card></a-col>
      <a-col :span="4"><a-card><a-statistic title="回款率" :value="percent(dashboard.collectionRate)" /></a-card></a-col>
    </a-row>

    <a-tabs>
      <a-tab-pane key="settlements" tab="业主结算">
        <a-space class="toolbar"><a-button type="primary" @click="openSettlement">新建业主结算</a-button><label>结算附件：<input type="file" accept=".pdf,.png,.jpg,.jpeg" @change="selectFile('settlement', $event)" /></label></a-space>
        <a-table :data-source="settlements" row-key="id" :loading="loading">
          <a-table-column title="结算编号" data-index="settlement_code" /><a-table-column title="结算周期" data-index="settlement_period" />
          <a-table-column title="结算日期" data-index="settlement_date" /><a-table-column title="含税金额"><template #default="{ record }">{{ money(record.gross_amount) }}</template></a-table-column>
          <a-table-column title="质保金"><template #default="{ record }">{{ money(record.retention_amount) }}</template></a-table-column><a-table-column title="状态" data-index="status" />
          <a-table-column title="操作"><template #default="{ record }"><a-button v-if="record.status === 'DRAFT'" type="link" @click="submitSettlement(record)">提交审批</a-button></template></a-table-column>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="receivables" tab="应收台账">
        <a-table :data-source="receivables" row-key="id" :loading="loading">
          <a-table-column title="应收编号" data-index="receivable_code" /><a-table-column title="类型" data-index="receivable_type" />
          <a-table-column title="原始金额"><template #default="{ record }">{{ money(record.original_amount) }}</template></a-table-column>
          <a-table-column title="已收"><template #default="{ record }">{{ money(record.collected_amount) }}</template></a-table-column>
          <a-table-column title="未收"><template #default="{ record }">{{ money(record.outstanding_amount) }}</template></a-table-column>
          <a-table-column title="到期日" data-index="due_date" /><a-table-column title="状态" data-index="status" />
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="invoices" tab="销项发票">
        <a-space class="toolbar"><a-button type="primary" @click="openInvoice">登记销项发票</a-button><label>发票文件：<input type="file" accept=".pdf,.png,.jpg,.jpeg" @change="selectFile('invoice', $event)" /></label></a-space>
        <a-table :data-source="invoices" row-key="id" :loading="loading"><a-table-column title="发票号码" data-index="invoice_no" /><a-table-column title="类型" data-index="invoice_type" /><a-table-column title="开票日期" data-index="invoice_date" /><a-table-column title="价税合计"><template #default="{ record }">{{ money(record.total_amount) }}</template></a-table-column><a-table-column title="验真" data-index="verification_status" /><a-table-column title="状态" data-index="status" /></a-table>
      </a-tab-pane>
      <a-tab-pane key="collections" tab="回款流水">
        <a-space class="toolbar"><a-button type="primary" @click="openCollection">登记到账回款</a-button><label>银行回单：<input type="file" accept=".pdf,.png,.jpg,.jpeg" @change="selectFile('collection', $event)" /></label></a-space>
        <a-table :data-source="collections" row-key="id" :loading="loading"><a-table-column title="回款编号" data-index="collection_code" /><a-table-column title="银行流水号" data-index="external_txn_no" /><a-table-column title="到账时间" data-index="collected_at" /><a-table-column title="到账金额"><template #default="{ record }">{{ money(record.amount) }}</template></a-table-column><a-table-column title="已分配"><template #default="{ record }">{{ money(record.allocated_amount) }}</template></a-table-column><a-table-column title="未分配"><template #default="{ record }">{{ money(record.unallocated_amount) }}</template></a-table-column><a-table-column title="状态" data-index="status" /></a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="settlementOpen" title="新建业主结算" width="720px" @ok="saveSettlement"><a-form layout="vertical"><a-row :gutter="16"><a-col :span="12"><a-form-item label="项目" required><a-select v-model:value="settlementForm.projectId"><a-select-option v-for="p in projects ?? []" :key="p.id" :value="p.id">{{ p.projectName }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="业主合同" required><a-select v-model:value="settlementForm.contractId"><a-select-option v-for="c in contracts ?? []" :key="c.id" :value="c.id">{{ c.contractName }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="业主/客户" required><a-select v-model:value="settlementForm.customerId" show-search><a-select-option v-for="p in partners ?? []" :key="p.id" :value="p.id">{{ p.partnerName }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="收入确认单 ID"><a-input v-model:value="settlementForm.revenueId" /></a-form-item></a-col><a-col :span="12"><a-form-item label="结算周期" required><a-input v-model:value="settlementForm.settlementPeriod" /></a-form-item></a-col><a-col :span="12"><a-form-item label="结算日期" required><a-input v-model:value="settlementForm.settlementDate" type="date" /></a-form-item></a-col><a-col :span="8"><a-form-item label="含税结算金额" required><a-input-number v-model:value="settlementForm.grossAmount" :min="0.01" :precision="2" style="width:100%" /></a-form-item></a-col><a-col :span="8"><a-form-item label="税额"><a-input-number v-model:value="settlementForm.taxAmount" :min="0" :precision="2" style="width:100%" /></a-form-item></a-col><a-col :span="8"><a-form-item label="质保金"><a-input-number v-model:value="settlementForm.retentionAmount" :min="0" :precision="2" style="width:100%" /></a-form-item></a-col><a-col :span="12"><a-form-item label="到期日" required><a-input v-model:value="settlementForm.dueDate" type="date" /></a-form-item></a-col><a-col :span="12"><a-form-item label="附件数量" required><a-input-number v-model:value="settlementForm.attachmentCount" :min="1" /></a-form-item></a-col></a-row></a-form></a-modal>

    <a-modal v-model:open="invoiceOpen" title="登记销项发票" width="720px" @ok="saveInvoice"><a-form layout="vertical"><a-row :gutter="16"><a-col :span="12"><a-form-item label="项目" required><a-select v-model:value="invoiceForm.projectId"><a-select-option v-for="p in projects ?? []" :key="p.id" :value="p.id">{{ p.projectName }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="业主合同" required><a-select v-model:value="invoiceForm.contractId"><a-select-option v-for="c in contracts ?? []" :key="c.id" :value="c.id">{{ c.contractName }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="业主/客户" required><a-select v-model:value="invoiceForm.customerId"><a-select-option v-for="p in partners ?? []" :key="p.id" :value="p.id">{{ p.partnerName }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="关联应收" required><a-select v-model:value="invoiceForm.receivableId"><a-select-option v-for="r in currentReceivables" :key="rowId(r)" :value="rowId(r)">{{ value(r, 'receivable_code') }} / 未收 {{ money(r.outstanding_amount) }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="发票号码" required><a-input v-model:value="invoiceForm.invoiceNo" /></a-form-item></a-col><a-col :span="12"><a-form-item label="发票代码"><a-input v-model:value="invoiceForm.invoiceCode" /></a-form-item></a-col><a-col :span="8"><a-form-item label="不含税金额" required><a-input-number v-model:value="invoiceForm.amountWithoutTax" :min="0" :precision="2" style="width:100%" /></a-form-item></a-col><a-col :span="8"><a-form-item label="税额"><a-input-number v-model:value="invoiceForm.taxAmount" :min="0" :precision="2" style="width:100%" /></a-form-item></a-col><a-col :span="8"><a-form-item label="分配到应收" required><a-input-number v-model:value="invoiceForm.allocatedAmount" :min="0.01" :precision="2" style="width:100%" /></a-form-item></a-col></a-row></a-form></a-modal>

    <a-modal v-model:open="collectionOpen" title="登记到账回款" width="720px" @ok="saveCollection"><a-form layout="vertical"><a-alert class="modal-alert" type="warning" show-icon message="提交成功将自动生成收入方向现金日记与会计凭证；同一银行流水号重复提交只返回原记录。" /><a-row :gutter="16"><a-col :span="12"><a-form-item label="项目" required><a-select v-model:value="collectionForm.projectId"><a-select-option v-for="p in projects ?? []" :key="p.id" :value="p.id">{{ p.projectName }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="业主合同" required><a-select v-model:value="collectionForm.contractId"><a-select-option v-for="c in contracts ?? []" :key="c.id" :value="c.id">{{ c.contractName }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="业主/付款方" required><a-select v-model:value="collectionForm.customerId"><a-select-option v-for="p in partners ?? []" :key="p.id" :value="p.id">{{ p.partnerName }}</a-select-option></a-select></a-form-item></a-col><a-col :span="12"><a-form-item label="资金账户 ID" required><a-input v-model:value="collectionForm.fundAccountId" /></a-form-item></a-col><a-col :span="12"><a-form-item label="银行流水号" required><a-input v-model:value="collectionForm.externalTxnNo" /></a-form-item></a-col><a-col :span="12"><a-form-item label="到账时间" required><a-input v-model:value="collectionForm.collectedAt" type="datetime-local" /></a-form-item></a-col><a-col :span="8"><a-form-item label="到账金额" required><a-input-number v-model:value="collectionForm.amount" :min="0.01" :precision="2" style="width:100%" /></a-form-item></a-col><a-col :span="8"><a-form-item label="关联应收"><a-select v-model:value="collectionForm.receivableId" allow-clear><a-select-option v-for="r in currentReceivables" :key="rowId(r)" :value="rowId(r)">{{ value(r, 'receivable_code') }}</a-select-option></a-select></a-form-item></a-col><a-col :span="8"><a-form-item label="分配金额"><a-input-number v-model:value="collectionForm.allocatedAmount" :min="0.01" :precision="2" style="width:100%" /></a-form-item></a-col><a-col :span="12"><a-form-item label="付款方名称" required><a-input v-model:value="collectionForm.payerName" /></a-form-item></a-col><a-col :span="12"><a-form-item label="附件数量" required><a-input-number v-model:value="collectionForm.attachmentCount" :min="1" /></a-form-item></a-col></a-row></a-form></a-modal>
  </div>
</template>

<style scoped>
.revenue-page{padding:0 20px 24px}.chain-alert,.summary-row{margin-bottom:16px}.toolbar{margin-bottom:16px}.modal-alert{margin-bottom:16px}
</style>
