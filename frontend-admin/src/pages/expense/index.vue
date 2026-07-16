<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  createExpense,
  deleteExpense,
  getExpenseDetail,
  getExpenseList,
  submitExpense,
  updateExpense,
} from '@/api/modules/expense'
import { getBudgetDetail, getBudgetList } from '@/api/modules/budget'
import { uploadFile } from '@/api/modules/file'
import { useReferenceStore } from '@/stores/reference'
import type { BudgetLineVO } from '@/types/budget'
import type { ExpenseApplicationVO } from '@/types/expense'

const referenceStore = useReferenceStore()
const rows = ref<ExpenseApplicationVO[]>([])
const loading = ref(false)
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const modalOpen = ref(false)
const editingId = ref<string>()
const budgetLines = ref<BudgetLineVO[]>([])
const proofFile = ref<File>()
const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  approvalStatus: undefined as string | undefined,
})
const form = reactive<Partial<ExpenseApplicationVO>>({
  expenseCategory: 'OTHER',
  expenseDate: new Date().toISOString().slice(0, 10),
  description: '',
})
const projects = computed(() => referenceStore.projects ?? [])
const contracts = computed(() => referenceStore.contracts ?? [])
const selectedContract = computed(() => contracts.value.find((item) => item.id === form.contractId))

async function load() {
  loading.value = true
  try {
    const result = await getExpenseList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      ...filter,
    })
    rows.value = result.records ?? []
    total.value = Number(result.total ?? 0)
  } catch (error) {
    console.error(error)
    message.error('加载费用申请失败')
  } finally {
    loading.value = false
  }
}

async function loadProjectContext(projectId?: string) {
  form.contractId = undefined
  form.costSubjectId = undefined
  form.budgetLineId = undefined
  budgetLines.value = []
  if (!projectId) return
  await referenceStore.fetchContracts({ projectId })
  const result = await getBudgetList({ pageNo: 1, pageSize: 10, projectId, status: 'ACTIVE' })
  const active = (result.records ?? []).find((item) => item.active || item.status === 'ACTIVE')
  if (active) budgetLines.value = (await getBudgetDetail(active.id)).lines ?? []
}

function selectBudgetLine(lineId: string) {
  form.costSubjectId = budgetLines.value.find((line) => line.id === lineId)?.costSubjectId
}

function selectContract(contractId: string) {
  form.payeePartnerId = contracts.value.find((item) => item.id === contractId)?.partyBId
}

function reset() {
  editingId.value = undefined
  Object.assign(form, {
    projectId: undefined,
    contractId: undefined,
    costSubjectId: undefined,
    budgetLineId: undefined,
    payeePartnerId: undefined,
    expenseCategory: 'OTHER',
    expenseDate: new Date().toISOString().slice(0, 10),
    amount: undefined,
    description: '',
    remark: '',
  })
  budgetLines.value = []
  proofFile.value = undefined
}

function openCreate() {
  reset()
  modalOpen.value = true
}

async function openEdit(row: ExpenseApplicationVO) {
  try {
    const detail = await getExpenseDetail(row.id)
    reset()
    editingId.value = row.id
    Object.assign(form, detail)
    await loadProjectContext(detail.projectId)
    form.contractId = detail.contractId
    form.payeePartnerId = detail.payeePartnerId
    form.budgetLineId = detail.budgetLineId
    form.costSubjectId = detail.costSubjectId
    modalOpen.value = true
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载费用申请失败')
  }
}

async function save() {
  if (
    !form.projectId ||
    !form.contractId ||
    !form.budgetLineId ||
    !form.payeePartnerId ||
    !form.expenseCategory ||
    !form.expenseDate ||
    Number(form.amount) <= 0 ||
    !form.description?.trim()
  ) {
    message.warning('请完整填写项目、合同、预算科目、费用分类、日期、金额和说明')
    return
  }
  try {
    const id = editingId.value
    if (id) await updateExpense(id, form)
    else editingId.value = await createExpense(form)
    if (proofFile.value) await uploadFile(proofFile.value, 'EXPENSE', editingId.value!)
    message.success('费用申请草稿已保存')
    modalOpen.value = false
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function submit(row: ExpenseApplicationVO) {
  try {
    await submitExpense(row.id)
    message.success('费用申请已提交审批并占用预算')
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '提交失败')
  }
}

function remove(row: ExpenseApplicationVO) {
  Modal.confirm({
    title: '删除费用申请',
    content: `确认删除 ${row.expenseCode}？`,
    okType: 'danger',
    onOk: async () => {
      await deleteExpense(row.id)
      await load()
    },
  })
}

function chooseProof(event: Event) {
  proofFile.value = (event.target as HTMLInputElement).files?.[0]
}

onMounted(async () => {
  await Promise.all([referenceStore.fetchProjects(), referenceStore.fetchContracts({})])
  await load()
})
</script>

<template>
  <div class="page-container">
    <a-card title="费用申请与预算占用">
      <template #extra
        ><a-space
          ><a-button @click="load">刷新</a-button
          ><a-button type="primary" @click="openCreate">新建费用申请</a-button></a-space
        ></template
      >
      <a-form layout="inline" style="margin-bottom: 16px">
        <a-form-item label="项目"
          ><a-select
            v-model:value="filter.projectId"
            allow-clear
            style="width: 220px"
            @change="load"
            ><a-select-option v-for="project in projects" :key="project.id" :value="project.id">{{
              project.projectName
            }}</a-select-option></a-select
          ></a-form-item
        >
        <a-form-item label="审批"
          ><a-select
            v-model:value="filter.approvalStatus"
            allow-clear
            style="width: 150px"
            @change="load"
            ><a-select-option value="DRAFT">草稿</a-select-option
            ><a-select-option value="APPROVING">审批中</a-select-option
            ><a-select-option value="APPROVED">已通过</a-select-option
            ><a-select-option value="REJECTED">已驳回</a-select-option></a-select
          ></a-form-item
        >
      </a-form>
      <a-table
        :loading="loading"
        :data-source="rows"
        row-key="id"
        :pagination="{ current: pageNo, pageSize, total }"
      >
        <a-table-column title="单号" data-index="expenseCode" />
        <a-table-column title="费用分类" data-index="expenseCategory" />
        <a-table-column title="申请金额" data-index="amount" align="right" />
        <a-table-column title="可转付款" data-index="availableToConvert" align="right" />
        <a-table-column title="审批" data-index="approvalStatus" />
        <a-table-column title="状态" data-index="status" />
        <a-table-column title="操作" width="220"
          ><template #default="{ record }"
            ><a-space
              ><a-button type="link" @click="openEdit(record)">查看/编辑</a-button
              ><a-button
                v-if="record.approvalStatus === 'DRAFT' || record.approvalStatus === 'REJECTED'"
                type="link"
                @click="submit(record)"
                >提交</a-button
              ><a-button
                v-if="record.approvalStatus === 'DRAFT'"
                type="link"
                danger
                @click="remove(record)"
                >删除</a-button
              ></a-space
            ></template
          ></a-table-column
        >
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '费用申请详情' : '新建费用申请'"
      :width="820"
      @ok="save"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"
            ><a-form-item label="项目" required
              ><a-select
                v-model:value="form.projectId"
                :disabled="!!editingId"
                @change="loadProjectContext"
                ><a-select-option
                  v-for="project in projects"
                  :key="project.id"
                  :value="project.id"
                  >{{ project.projectName }}</a-select-option
                ></a-select
              ></a-form-item
            ></a-col
          >
          <a-col :span="12"
            ><a-form-item label="合同" required
              ><a-select v-model:value="form.contractId" @change="selectContract"
                ><a-select-option
                  v-for="contract in contracts"
                  :key="contract.id"
                  :value="contract.id"
                  >{{ contract.contractName }}</a-select-option
                ></a-select
              ></a-form-item
            ></a-col
          >
          <a-col :span="12"
            ><a-form-item label="预算科目" required
              ><a-select v-model:value="form.budgetLineId" @change="selectBudgetLine"
                ><a-select-option v-for="line in budgetLines" :key="line.id" :value="line.id"
                  >{{ line.costSubjectName }}（可用 {{ line.availableAmount }}）</a-select-option
                ></a-select
              ></a-form-item
            ></a-col
          >
          <a-col :span="12"
            ><a-form-item label="付款对象"
              ><a-input :value="selectedContract?.partyBName" disabled /></a-form-item
          ></a-col>
          <a-col :span="8"
            ><a-form-item label="费用分类" required
              ><a-select v-model:value="form.expenseCategory"
                ><a-select-option value="LABOR">人工费</a-select-option
                ><a-select-option value="MATERIAL">材料费</a-select-option
                ><a-select-option value="SUBCONTRACT">分包费</a-select-option
                ><a-select-option value="OTHER">其他</a-select-option></a-select
              ></a-form-item
            ></a-col
          >
          <a-col :span="8"
            ><a-form-item label="费用日期" required
              ><a-input v-model:value="form.expenseDate" type="date" /></a-form-item
          ></a-col>
          <a-col :span="8"
            ><a-form-item label="费用金额" required
              ><a-input-number
                v-model:value="form.amount"
                :min="0.01"
                :precision="2"
                style="width: 100%" /></a-form-item
          ></a-col>
          <a-col :span="24"
            ><a-form-item label="费用说明" required
              ><a-textarea v-model:value="form.description" :rows="3" /></a-form-item
          ></a-col>
          <a-col :span="24"
            ><a-form-item label="费用附件"
              ><input type="file" accept=".pdf,.png,.jpg,.jpeg" @change="chooseProof" /><span
                v-if="proofFile"
                style="margin-left: 8px"
                >{{ proofFile.name }}</span
              ></a-form-item
            ></a-col
          >
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>
