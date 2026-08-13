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
import {
  loadBudget,
  loadBudgetPage,
  loadContractPage,
  loadCostSubjectOptions,
  loadPartners,
  type CostSubjectOption,
} from '@/services/commercial'
import { uploadSiteFile } from '@/services/delivery'
import {
  createExpense,
  deleteExpense,
  loadExpenseApplications,
  loadPaymentTraceByExpense,
  submitExpense,
  updateExpense,
} from '@/services/finance'
import { loadEnabledDictDataByCode, type DictDataRecord } from '@/services/system-management'
import { localDateInputValue } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type {
  BudgetLineRecord,
  ContractRecord,
  ExpenseApplicationRecord,
  PartnerRecord,
  PaymentTraceRecord,
} from '@cgc-pms/frontend-contracts'
import {
  allPartnerOptions,
  contractOptions as buildContractOptions,
  defaultOption,
  dictionaryOptions,
  emptyExpenseEditor,
  expenseCommand,
  leafCostSubjectOptions,
  type ExpenseEditor,
} from './model'

type Action = 'delete' | 'submit'

const title = '费用申请'
const pageSize = 10
const session = useSessionStore()
const workspace = useWorkspaceStore()
const projectId = computed(() => workspace.selectedProjectId || '')
const canQuery = computed(() => session.hasPermission('expense:query'))
const can = (action: string) => session.hasAdminOrPermission(`expense:${action}`)
const canAdd = computed(() => can('add'))
const canTrace = computed(() => session.hasAdminOrPermission('payment:trace:query'))

const rows = ref<ExpenseApplicationRecord[]>([])
const pageNo = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const dialog = ref(false)
const editor = ref<ExpenseEditor | null>(null)
const pending = ref<{ row: ExpenseApplicationRecord; action: Action } | null>(null)
const contracts = ref<ContractRecord[]>([])
const partners = ref<PartnerRecord[]>([])
const costSubjects = ref<CostSubjectOption[]>([])
const budgetLines = ref<BudgetLineRecord[]>([])
const expenseCategories = ref<DictDataRecord[]>([])
const expenseAttachment = ref<File | null>(null)
const traceOpen = ref(false)
const traceRows = ref<PaymentTraceRecord[]>([])
const traceLoading = ref(false)
const traceError = ref('')
let controller: AbortController | null = null
let dictionariesLoaded = false

const hasRows = computed(() => rows.value.length > 0)
const projectOptions = computed(() =>
  workspace.projects.filter(
    (item) => item.status === 'ACTIVE' || item.value === editor.value?.projectId,
  ),
)
const contractOptions = computed(() =>
  buildContractOptions(contracts.value, 'expense', editor.value?.contractId),
)
const payeePartnerOptions = computed(() =>
  allPartnerOptions(partners.value, editor.value?.payeePartnerId),
)
const costSubjectOptions = computed(() => leafCostSubjectOptions(costSubjects.value))
const budgetLineOptions = computed(() =>
  budgetLines.value
    .filter(
      (item) => !editor.value?.costSubjectId || item.costSubjectId === editor.value.costSubjectId,
    )
    .filter((item): item is BudgetLineRecord & { id: string } => Boolean(item.id))
    .map((item) => ({
      value: item.id,
      label: `${item.costSubjectName || item.costSubjectId} · 可用 ${formatAmount(item.availableAmount)}`,
    })),
)
const expenseCategoryOptions = computed(() => dictionaryOptions(expenseCategories.value))

async function loadDictionaries(signal?: AbortSignal): Promise<void> {
  if (dictionariesLoaded) return
  expenseCategories.value = await loadEnabledDictDataByCode('expense_category', signal)
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
    const page = await loadExpenseApplications(
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

async function loadContracts(value: string): Promise<void> {
  contracts.value = []
  if (!value) return
  contracts.value = (await loadContractPage({ pageNo: 1, pageSize: 200, projectId: value })).records
}

async function loadBudgetLines(value: string): Promise<void> {
  budgetLines.value = []
  if (!value) return
  const page = await loadBudgetPage({ pageNo: 1, pageSize: 50, projectId: value })
  const details = await Promise.all(
    page.records
      .filter((item) => item.active || item.status === 'ACTIVE')
      .map((item) => loadBudget(item.id)),
  )
  budgetLines.value = details.flatMap((item) => item.lines || [])
}

async function changeProject(value: string): Promise<void> {
  if (!editor.value) return
  editor.value.projectId = value
  editor.value.contractId = ''
  editor.value.budgetLineId = ''
  await Promise.all([loadContracts(value), loadBudgetLines(value)])
}

async function openForm(row?: ExpenseApplicationRecord): Promise<void> {
  try {
    await loadDictionaries()
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : '请稍后重试。'
    errorMessage.value = message
    showToast('error', '业务字典加载失败', message)
    return
  }
  const value = emptyExpenseEditor(localDateInputValue())
  value.projectId = row?.projectId || projectId.value
  value.expenseCategory = defaultOption(expenseCategoryOptions.value, 'CONTRACT')
  expenseAttachment.value = null
  if (row) {
    value.id = row.id
    value.contractId = row.contractId || ''
    value.costSubjectId = row.costSubjectId || ''
    value.budgetLineId = row.budgetLineId || ''
    value.payeePartnerId = row.payeePartnerId || ''
    value.expenseCategory = row.expenseCategory
    value.expenseDate = row.expenseDate
    value.amount = row.amount
    value.description = row.description || ''
  }
  editor.value = value
  dialog.value = true
  try {
    const jobs: Promise<unknown>[] = []
    if (!partners.value.length)
      jobs.push(loadPartners().then((page) => (partners.value = page.records)))
    if (!costSubjects.value.length) {
      jobs.push(loadCostSubjectOptions().then((items) => (costSubjects.value = items)))
    }
    await Promise.all(jobs)
    if (value.projectId) {
      await Promise.all([loadContracts(value.projectId), loadBudgetLines(value.projectId)])
    }
  } catch (cause) {
    dialog.value = false
    showToast('error', '候选项加载失败', cause instanceof Error ? cause.message : '请稍后重试。')
  }
}

function onExpenseAttachment(event: Event): void {
  expenseAttachment.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function save(): Promise<void> {
  if (!editor.value || busy.value) return
  busy.value = true
  try {
    const value = editor.value
    const command = expenseCommand(value)
    if (!value.id && !expenseAttachment.value) throw new TypeError('费用附件不能为空')
    const expenseId = value.id || (await createExpense(command))
    if (value.id) await updateExpense(value.id, command)
    value.id = expenseId
    if (expenseAttachment.value) {
      await uploadSiteFile(expenseAttachment.value, 'EXPENSE', expenseId, 'OTHER')
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

async function openTrace(row: ExpenseApplicationRecord): Promise<void> {
  traceOpen.value = true
  traceRows.value = []
  traceError.value = ''
  traceLoading.value = true
  try {
    traceRows.value = await loadPaymentTraceByExpense(row.id)
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
    if (value.action === 'delete') await deleteExpense(value.row.id)
    else await submitExpense(value.row.id)
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
      title="无权访问费用申请"
      description="系统未加载财务数据。"
    />
    <template v-else>
      <V2Card :title="title" :heading-level="1">
        <template #actions>
          <div class="finance-workspace__actions">
            <V2Button v-if="canAdd" size="small" @click="openForm()">新建费用申请</V2Button>
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
        description="正在读取费用申请。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="费用申请加载失败"
        :description="errorMessage"
        ><template #actions><V2Button @click="load">重试</V2Button></template></V2PageState
      >
      <V2PageState
        v-else-if="!errorMessage && !hasRows"
        title="暂无费用申请记录"
        description="当前项目范围没有可访问数据。"
      />
      <V2Card v-else :heading-level="2">
        <div
          class="finance-workspace__table-wrap"
          role="region"
          aria-label="费用申请表格"
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
                    {{ row.expenseCode }}</button
                  ><span v-else>{{ row.expenseCode }}</span>
                </td>
                <td>
                  {{
                    workspace.projects.find((item) => item.value === row.projectId)?.label ||
                    '项目名称缺失'
                  }}
                </td>
                <td>{{ dashboardStatusLabel(row.approvalStatus) }}</td>
                <td>{{ formatAmount(row.amount) }}</td>
                <td class="v2-table-cell--actions">
                  <V2ActionMenu
                    :label="`${row.expenseCode}更多操作`"
                    :placement="index >= rows.length - 3 ? 'top-end' : 'bottom-end'"
                  >
                    <V2Button
                      v-if="row.approvalStatus === 'DRAFT' && can('edit')"
                      size="small"
                      variant="ghost"
                      @click="openForm(row)"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="row.approvalStatus === 'DRAFT' && can('delete')"
                      size="small"
                      variant="ghost"
                      @click="pending = { row, action: 'delete' }"
                      >删除</V2Button
                    >
                    <V2Button
                      v-if="row.approvalStatus === 'DRAFT' && can('submit')"
                      size="small"
                      variant="ghost"
                      @click="pending = { row, action: 'submit' }"
                      >提交</V2Button
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
            label="费用申请分页"
            :disabled="loading"
            @update:page-no="changePage"
        /></template>
      </V2Card>

      <V2Dialog
        v-model:open="dialog"
        :title="editor?.id ? '编辑费用申请' : '新建费用申请'"
        :close-disabled="busy"
        :close-on-backdrop="false"
      >
        <form
          v-if="editor"
          id="expense-application-form"
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
          />
          <p v-if="!contractOptions.length">当前项目无可用合同，不能提交。</p>
          <V2Select
            v-model="editor.costSubjectId"
            label="成本科目"
            :options="costSubjectOptions"
            required
            @update:model-value="editor.budgetLineId = ''"
          />
          <V2Select
            v-model="editor.budgetLineId"
            label="预算明细"
            :options="budgetLineOptions"
            required
          />
          <V2Select
            v-model="editor.payeePartnerId"
            label="收款单位"
            :options="payeePartnerOptions"
            required
          />
          <V2Select
            v-model="editor.expenseCategory"
            label="费用类别"
            :options="expenseCategoryOptions"
            required
          />
          <V2Input
            v-model="editor.expenseDate"
            label="费用日期"
            placeholder="YYYY-MM-DD"
            required
          />
          <V2Input
            v-model="editor.amount"
            label="费用金额"
            :decimal-scale="2"
            required
            hint="按字符串提交"
          />
          <V2Input v-model="editor.description" label="费用说明" required />
          <label class="v2-field"
            ><span class="v2-field__label">费用附件<span v-if="!editor.id">*</span></span
            ><input
              class="v2-file-input"
              type="file"
              :required="!editor.id"
              @change="onExpenseAttachment"
          /></label>
        </form>
        <template #footer
          ><V2Button type="button" variant="secondary" :disabled="busy" @click="dialog = false"
            >取消</V2Button
          ><V2Button type="submit" form="expense-application-form" :loading="busy"
            >保存</V2Button
          ></template
        >
      </V2Dialog>

      <V2ConfirmDialog
        :open="Boolean(pending)"
        :title="pending?.action === 'delete' ? '确认删除记录' : '确认提交审批'"
        :description="
          pending
            ? `${pending.row.expenseCode} 将执行${pending.action === 'delete' ? '删除记录' : '提交审批'}，服务端仍会校验状态、权限和余额。`
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
