<script setup lang="ts">
import type {
  BudgetAvailabilityRecord,
  BudgetLineRecord,
  BudgetQuery,
  BudgetSaveCommand,
  ProjectBudgetRecord,
  ProjectContextOption,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Alert, V2Button, V2Card, V2Dialog, V2Input, V2PageState, V2Select } from '@/components'
import {
  createBudget,
  deleteBudget,
  loadBudget,
  loadBudgetAvailability,
  loadBudgetPage,
  loadProjectContextOptions,
  saveBudgetLines,
  submitBudget,
  updateBudget,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const filter = reactive<BudgetQuery>({ pageNo: 1, pageSize: 20 })
const records = ref<ProjectBudgetRecord[]>([])
const total = ref(0)
const projects = ref<ProjectContextOption[]>([])
const detail = ref<ProjectBudgetRecord | null>(null)
const availability = ref<BudgetAvailabilityRecord[]>([])
const form = reactive<BudgetSaveCommand>(blank())
const lines = ref<BudgetLineRecord[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const dialog = ref<'closed' | 'detail' | 'create' | 'edit'>('closed')
let controller: AbortController | null = null
let detailController: AbortController | null = null
let generation = 0
let detailGeneration = 0
const canQuery = computed(() => session.hasPermission('budget:query'))
const canAdd = computed(() => session.hasPermission('budget:add'))
const canEdit = computed(() => session.hasPermission('budget:edit'))
const canDelete = computed(() => session.hasPermission('budget:delete'))
const canSubmit = computed(() => session.hasPermission('budget:submit'))
const projectOptions = computed(() =>
  projects.value.map((p) => ({ value: p.id, label: p.projectName })),
)
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
]
const decimal = (v: string) => /^(?:0|[1-9]\d*)(?:\.\d+)?$/.test(v) && !/^0(?:\.0+)?$/.test(v)
const errorText = (e: unknown, f: string) =>
  isApiClientError(e) ? e.message : e instanceof Error ? e.message : f
const needsReload = (e: unknown) => isApiClientError(e) && e.status === 409
function blank(): BudgetSaveCommand {
  return {
    projectId: '',
    versionNo: '',
    budgetName: '',
    totalAmount: '',
    version: null,
    remark: null,
  }
}
function hydrate() {
  const bounds = reportPeriodBounds(
    typeof route.query.period === 'string' ? route.query.period : null,
  )
  Object.assign(filter, {
    pageNo: Math.max(1, Number(route.query.pageNo) || 1),
    pageSize: 20,
    projectId: typeof route.query.projectId === 'string' ? route.query.projectId : undefined,
    status: typeof route.query.status === 'string' ? route.query.status : undefined,
    startDate: bounds?.startDate,
    endDate: bounds?.endDate,
  })
}
async function load() {
  if (!canQuery.value) return
  hydrate()
  controller?.abort()
  const current = new AbortController()
  controller = current
  const token = ++generation
  loading.value = true
  errorMessage.value = ''
  try {
    const [page, options] = await Promise.all([
      loadBudgetPage({ ...filter }, current.signal),
      loadProjectContextOptions(current.signal),
    ])
    if (token !== generation) return
    records.value = page.records
    total.value = page.total
    projects.value = options
  } catch (e) {
    if (!current.signal.aborted && token === generation) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(e, '项目预算加载失败')
    }
  } finally {
    if (token === generation) loading.value = false
  }
}
async function query() {
  filter.pageNo = 1
  await router.replace({
    path: '/budget',
    query: {
      ...(filter.projectId ? { projectId: filter.projectId } : {}),
      ...(filter.status ? { status: filter.status } : {}),
      ...(typeof route.query.period === 'string' ? { period: route.query.period } : {}),
    },
    hash: route.hash,
  })
}
async function openDetail(id: string, mode: 'detail' | 'edit' = 'detail') {
  detailController?.abort()
  const current = new AbortController()
  detailController = current
  const token = ++detailGeneration
  dialog.value = mode
  detailLoading.value = true
  errorMessage.value = ''
  try {
    const [value, balance] = await Promise.all([
      loadBudget(id, current.signal),
      loadBudgetAvailability(id, current.signal),
    ])
    if (token !== detailGeneration) return
    detail.value = value
    availability.value = balance
    lines.value = (value.lines ?? []).map((row) => ({ ...row }))
    Object.assign(form, {
      projectId: value.projectId,
      versionNo: value.versionNo,
      budgetName: value.budgetName,
      totalAmount: value.totalAmount,
      version: value.version ?? null,
      remark: value.remark ?? null,
    })
  } catch (e) {
    if (!current.signal.aborted && token === detailGeneration) {
      detail.value = null
      availability.value = []
      errorMessage.value = errorText(e, '预算详情加载失败')
    }
  } finally {
    if (token === detailGeneration) detailLoading.value = false
  }
}
function openCreate() {
  detail.value = null
  availability.value = []
  lines.value = [{ costSubjectId: '', budgetAmount: '' }]
  Object.assign(form, blank(), { projectId: filter.projectId ?? '' })
  dialog.value = 'create'
}
async function run(
  action: () => Promise<unknown>,
  success: string,
  options: { refreshDetailId?: string } = {},
) {
  if (actionBusy.value) return
  actionBusy.value = true
  errorMessage.value = ''
  try {
    await action()
    successMessage.value = success
    await load()
    if (options.refreshDetailId) await openDetail(options.refreshDetailId, 'detail')
    else dialog.value = 'closed'
  } catch (e) {
    const message = errorText(e, '预算操作失败')
    if (needsReload(e)) {
      await load()
      if (detail.value) await openDetail(detail.value.id, 'detail')
    }
    errorMessage.value = message
  } finally {
    actionBusy.value = false
  }
}
async function save() {
  if (
    !form.projectId ||
    !form.versionNo.trim() ||
    !form.budgetName.trim() ||
    !decimal(form.totalAmount)
  ) {
    errorMessage.value = '请完整填写预算版本与正数金额'
    return
  }
  const command = { ...form, versionNo: form.versionNo.trim(), budgetName: form.budgetName.trim() }
  if (dialog.value === 'create')
    await run(async () => {
      const id = await createBudget(command)
      if (lines.value.some((row) => row.costSubjectId && decimal(row.budgetAmount)))
        await saveBudgetLines(
          id,
          lines.value.filter((row) => row.costSubjectId && decimal(row.budgetAmount)),
          '0',
        )
    }, '预算已创建')
  else if (detail.value) await run(() => updateBudget(detail.value!.id, command), '预算已更新')
}
async function saveLines() {
  if (
    !detail.value ||
    lines.value.length === 0 ||
    lines.value.some((row) => !row.costSubjectId || !decimal(row.budgetAmount))
  ) {
    errorMessage.value = '预算明细必须完整且金额大于0'
    return
  }
  await run(
    () => saveBudgetLines(detail.value!.id, lines.value, detail.value!.version ?? ''),
    '预算明细已保存',
    { refreshDetailId: detail.value.id },
  )
}
function addLine() {
  lines.value.push({ costSubjectId: '', budgetAmount: '' })
}
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => {
  controller?.abort()
  detailController?.abort()
})
</script>
<template>
  <div class="budget-page">
    <V2PageState
      v-if="!canQuery"
      title="无权访问项目预算"
      description="系统未加载预算业务数据。"
      kind="forbidden"
    /><template v-else
      ><V2Alert v-if="errorMessage" tone="danger" title="预算操作未完成">{{ errorMessage }}</V2Alert
      ><V2Alert v-if="successMessage" tone="success" title="预算操作完成">{{
        successMessage
      }}</V2Alert
      ><V2Card title="项目预算" :heading-level="1"
        ><template #actions
          ><V2Button v-if="canAdd" variant="secondary" @click="openCreate"
            >新建预算</V2Button
          ></template
        >
        <div class="filters">
          <V2Select
            v-model="filter.projectId"
            label="项目"
            :options="projectOptions"
            allow-empty
          /><V2Select
            v-model="filter.status"
            label="状态"
            :options="statusOptions"
            allow-empty
          /><V2Button class="budget-query" variant="secondary" :loading="loading" @click="query"
            >查询</V2Button
          >
        </div></V2Card
      ><V2PageState
        v-if="loading && !records.length"
        title="正在加载项目预算"
        description="正在读取当前项目和报告期内的预算版本。"
        kind="loading"
      /><V2PageState
        v-else-if="!records.length"
        title="暂无项目预算"
        description="当前筛选条件下没有可访问的预算版本。"
        kind="empty"
      /><V2Card v-for="row in records" v-else :key="row.id" :title="row.budgetName"
        ><dl>
          <dt>版本</dt>
          <dd>{{ row.versionNo }}</dd>
          <dt>预算总额</dt>
          <dd>{{ row.totalAmount }}</dd>
          <dt>审批状态</dt>
          <dd>{{ row.approvalStatus }}</dd>
          <dt>业务状态</dt>
          <dd>{{ row.status }}</dd>
        </dl>
        <template #footer
          ><div class="actions">
            <V2Button variant="secondary" @click="openDetail(row.id)">详情</V2Button
            ><V2Button
              v-if="canEdit && ['DRAFT', 'REJECTED'].includes(row.approvalStatus)"
              variant="secondary"
              @click="openDetail(row.id, 'edit')"
              >编辑</V2Button
            ><V2Button
              v-if="canSubmit && ['DRAFT', 'REJECTED'].includes(row.approvalStatus)"
              variant="secondary"
              :disabled="actionBusy"
              @click="run(() => submitBudget(row.id, row.version ?? ''), '预算已提交')"
              >提交</V2Button
            ><V2Button
              v-if="canDelete && row.approvalStatus === 'DRAFT'"
              variant="danger"
              :disabled="actionBusy"
              @click="run(() => deleteBudget(row.id, row.version ?? ''), '预算已删除')"
              >删除</V2Button
            >
          </div></template
        ></V2Card
      ><V2Dialog
        :open="dialog !== 'closed'"
        :title="dialog === 'create' ? '新建预算' : dialog === 'edit' ? '编辑预算' : '预算详情'"
        panel-class="v2-dialog-standard v2-detail-dialog"
        :close-on-backdrop="false"
        :close-disabled="actionBusy"
        @close="dialog = 'closed'"
        ><V2PageState
          v-if="detailLoading"
          title="正在加载预算详情"
          description="正在读取预算明细和服务端可用余额。"
          kind="loading"
        />
        <form
          v-else-if="dialog === 'create' || dialog === 'edit'"
          class="form"
          @submit.prevent="save"
        >
          <V2Select
            v-model="form.projectId"
            label="项目"
            :options="projectOptions"
            :disabled="dialog === 'edit'"
          /><V2Input v-model="form.versionNo" label="预算版本号" required /><V2Input
            v-model="form.budgetName"
            label="预算名称"
            required
          /><V2Input v-model="form.totalAmount" label="预算总额" required />
          <div v-if="dialog === 'create'" class="lines">
            <div v-for="(line, index) in lines" :key="index" class="line">
              <V2Input v-model="line.costSubjectId" label="成本科目ID" /><V2Input
                v-model="line.budgetAmount"
                label="预算金额"
              />
            </div>
            <V2Button type="button" variant="secondary" @click="addLine">添加明细</V2Button>
          </div>
          <V2Button type="submit" variant="secondary" :loading="actionBusy">保存预算</V2Button>
        </form>
        <div v-else-if="detail" class="form">
          <dl>
            <dt>ID</dt>
            <dd>{{ detail.id }}</dd>
            <dt>预算总额</dt>
            <dd>{{ detail.totalAmount }}</dd>
            <dt>版本</dt>
            <dd>{{ detail.version }}</dd>
          </dl>
          <div
            v-if="canEdit && ['DRAFT', 'REJECTED'].includes(detail.approvalStatus)"
            class="lines"
          >
            <div v-for="(line, index) in lines" :key="line.id || index" class="line">
              <V2Input v-model="line.costSubjectId" label="成本科目ID" /><V2Input
                v-model="line.budgetAmount"
                label="预算金额"
              />
            </div>
            <V2Button variant="secondary" @click="addLine">添加明细</V2Button
            ><V2Button variant="secondary" :loading="actionBusy" @click="saveLines"
              >保存明细</V2Button
            >
          </div>
          <div class="table-wrap" role="region" aria-label="预算可用额" tabindex="0">
            <table>
              <thead>
                <tr>
                  <th>科目</th>
                  <th>预算</th>
                  <th>占用</th>
                  <th>消耗</th>
                  <th>可用</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in availability" :key="row.budgetLineId">
                  <td>{{ row.costSubjectId }}</td>
                  <td>{{ row.budgetAmount }}</td>
                  <td>{{ row.reservedAmount }}</td>
                  <td>{{ row.consumedAmount }}</td>
                  <td>{{ row.availableAmount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div></V2Dialog
      ></template
    >
  </div>
</template>
<style scoped>
.budget-page,
.form,
.lines {
  display: grid;
  gap: var(--v2-space-4);
}
.filters,
.line {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: var(--v2-space-3);
  align-items: end;
}
.actions {
  display: flex;
  gap: var(--v2-space-2);
  flex-wrap: wrap;
}
.budget-query {
  color: var(--v2-color-text-strong);
}
dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}
dd {
  margin: 0;
}
.table-wrap {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: var(--v2-space-2);
  border-bottom: 1px solid var(--v2-color-border);
}
@media (max-width: 48rem) {
  .filters,
  .line {
    grid-template-columns: 1fr;
  }
}
</style>
