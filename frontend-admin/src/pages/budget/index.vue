<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import {
  createBudget,
  deleteBudget,
  getBudgetDetail,
  getBudgetList,
  saveBudgetLines,
  submitBudget,
  updateBudget,
} from '@/api/modules/budget'
import { getCostSubjectList } from '@/api/modules/costSubject'
import { useReferenceStore } from '@/stores/reference'
import type { BudgetLineVO, ProjectBudgetVO } from '@/types/budget'
import type { CostSubjectVO } from '@/types/costSubject'

const referenceStore = useReferenceStore()
const loading = ref(false)
const rows = ref<ProjectBudgetVO[]>([])
const subjects = ref<CostSubjectVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const modalOpen = ref(false)
const editingId = ref<string>()
const filter = reactive({
  projectId: undefined as string | undefined,
  status: undefined as string | undefined,
})
const form = reactive<Partial<ProjectBudgetVO>>({
  projectId: undefined,
  versionNo: '',
  budgetName: '',
  remark: '',
})
const lines = ref<(BudgetLineVO & { key: number })[]>([])
let lineKey = 0

const projects = computed(() => referenceStore.projects ?? [])
const totalAmount = computed(() =>
  lines.value.reduce((sum, line) => sum + (Number(line.budgetAmount) || 0), 0).toFixed(2),
)
const subjectOptions = computed(() =>
  subjects.value
    .filter((item) => item.status !== 'DISABLED')
    .map((item) => ({
      value: item.id,
      label: `${item.subjectCode} ${item.subjectName}`,
    })),
)

async function load() {
  loading.value = true
  try {
    const result = await getBudgetList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      ...filter,
    })
    rows.value = result.records ?? []
    total.value = Number(result.total ?? 0)
  } catch (error) {
    console.error(error)
    message.error('加载项目预算失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = undefined
  Object.assign(form, { projectId: undefined, versionNo: '', budgetName: '', remark: '' })
  lines.value = []
  lineKey = 0
}

function addLine() {
  lines.value.push({ key: lineKey++, costSubjectId: '', budgetAmount: '' })
}

function openCreate() {
  resetForm()
  addLine()
  modalOpen.value = true
}

async function openEdit(row: ProjectBudgetVO) {
  try {
    const detail = await getBudgetDetail(row.id)
    editingId.value = row.id
    Object.assign(form, detail)
    lines.value = (detail.lines ?? []).map((line) => ({ ...line, key: lineKey++ }))
    modalOpen.value = true
  } catch (error) {
    console.error(error)
    message.error('加载预算明细失败')
  }
}

async function save() {
  if (
    !form.projectId ||
    !form.versionNo?.trim() ||
    !form.budgetName?.trim() ||
    lines.value.length === 0
  ) {
    message.warning('请完整填写项目、版本、预算名称和预算科目')
    return
  }
  if (lines.value.some((line) => !line.costSubjectId || Number(line.budgetAmount) <= 0)) {
    message.warning('预算科目和金额必须完整且金额大于 0')
    return
  }
  if (new Set(lines.value.map((line) => line.costSubjectId)).size !== lines.value.length) {
    message.warning('同一成本科目不能重复')
    return
  }
  const payload = { ...form, totalAmount: totalAmount.value }
  try {
    const id = editingId.value
    if (id) await updateBudget(id, payload)
    else editingId.value = await createBudget(payload)
    await saveBudgetLines(
      editingId.value!,
      lines.value.map(({ key: _key, ...line }) => line),
    )
    message.success('预算草稿已保存')
    modalOpen.value = false
    await load()
  } catch (error) {
    console.error(error)
    message.error(error instanceof Error ? error.message : '保存预算失败')
  }
}

async function submit(row: ProjectBudgetVO) {
  try {
    await submitBudget(row.id)
    message.success('预算已提交审批')
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '提交失败')
  }
}

function remove(row: ProjectBudgetVO) {
  Modal.confirm({
    title: '删除预算草稿',
    content: `确认删除 ${row.budgetName}？`,
    okType: 'danger',
    onOk: async () => {
      await deleteBudget(row.id)
      message.success('已删除')
      await load()
    },
  })
}

onMounted(async () => {
  await Promise.all([
    referenceStore.fetchProjects(),
    getCostSubjectList('COST').then((data) => (subjects.value = data)),
  ])
  await load()
})
</script>

<template>
  <div class="page-container">
    <a-card title="项目预算与可用额度">
      <template #extra>
        <a-space>
          <a-button :icon="h(ReloadOutlined)" @click="load">刷新</a-button>
          <a-button type="primary" :icon="h(PlusOutlined)" @click="openCreate">新建预算</a-button>
        </a-space>
      </template>
      <a-form layout="inline" style="margin-bottom: 16px">
        <a-form-item label="项目">
          <a-select
            v-model:value="filter.projectId"
            allow-clear
            style="width: 240px"
            @change="load"
          >
            <a-select-option v-for="project in projects" :key="project.id" :value="project.id">{{
              project.projectName
            }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="filter.status" allow-clear style="width: 160px" @change="load">
            <a-select-option value="DRAFT">草稿</a-select-option>
            <a-select-option value="ACTIVE">生效</a-select-option>
            <a-select-option value="SUPERSEDED">已替代</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
      <a-table
        :loading="loading"
        :data-source="rows"
        row-key="id"
        :pagination="{ current: pageNo, pageSize, total }"
      >
        <a-table-column title="预算名称" data-index="budgetName" />
        <a-table-column title="版本" data-index="versionNo" width="120" />
        <a-table-column title="总额" data-index="totalAmount" align="right" />
        <a-table-column title="审批" data-index="approvalStatus" width="120" />
        <a-table-column title="状态" data-index="status" width="120" />
        <a-table-column title="操作" width="220">
          <template #default="{ record }">
            <a-space>
              <a-button type="link" @click="openEdit(record)">查看/编辑</a-button>
              <a-button
                v-if="record.approvalStatus === 'DRAFT' || record.approvalStatus === 'REJECTED'"
                type="link"
                @click="submit(record)"
                >提交</a-button
              >
              <a-button
                v-if="record.approvalStatus === 'DRAFT'"
                type="link"
                danger
                @click="remove(record)"
                >删除</a-button
              >
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '预算明细' : '新建项目预算'"
      :width="900"
      @ok="save"
    >
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="8"
            ><a-form-item label="项目" required
              ><a-select v-model:value="form.projectId" :disabled="!!editingId"
                ><a-select-option
                  v-for="project in projects"
                  :key="project.id"
                  :value="project.id"
                  >{{ project.projectName }}</a-select-option
                ></a-select
              ></a-form-item
            ></a-col
          >
          <a-col :span="8"
            ><a-form-item label="预算版本" required
              ><a-input v-model:value="form.versionNo" /></a-form-item
          ></a-col>
          <a-col :span="8"
            ><a-form-item label="预算名称" required
              ><a-input v-model:value="form.budgetName" /></a-form-item
          ></a-col>
        </a-row>
        <div style="display: flex; justify-content: space-between; margin-bottom: 8px">
          <strong>预算科目（合计 {{ totalAmount }} 元）</strong
          ><a-button @click="addLine">添加科目</a-button>
        </div>
        <a-table :data-source="lines" row-key="key" :pagination="false" size="small">
          <a-table-column title="成本科目"
            ><template #default="{ record }"
              ><a-select
                v-model:value="record.costSubjectId"
                show-search
                :options="subjectOptions"
                style="width: 100%" /></template
          ></a-table-column>
          <a-table-column title="预算金额" width="220"
            ><template #default="{ record }"
              ><a-input-number
                v-model:value="record.budgetAmount"
                :min="0.01"
                :precision="2"
                style="width: 100%" /></template
          ></a-table-column>
          <a-table-column title="可用金额" data-index="availableAmount" width="160" />
          <a-table-column title="操作" width="90"
            ><template #default="{ index }"
              ><a-button type="link" danger @click="lines.splice(index, 1)"
                >删除</a-button
              ></template
            ></a-table-column
          >
        </a-table>
      </a-form>
    </a-modal>
  </div>
</template>
