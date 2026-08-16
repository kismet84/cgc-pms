<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2Cluster,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import {
  createAssignmentRule,
  createMappingVersion,
  createOverheadAllocationRule,
  diffRulePlan,
  executeOverheadAllocation,
  generateInitialRulePlan,
  loadAssignmentRules,
  loadGovernanceFormOptions,
  loadMappingVersions,
  loadOverheadAllocationRules,
  setOverheadAllocationRuleStatus,
  submitRulePlan,
  trialRulePlan,
  updateOverheadAllocationRule,
  validateRulePlan,
  type AssignmentRuleRecord,
  type GovernanceFormOptions,
  type MappingVersionRecord,
  type OverheadAllocationExecutionResult,
  type OverheadAllocationRuleCommand,
  type OverheadAllocationRuleRecord,
} from '@/services/cost-subject'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
import { formatAmount } from '@/shared/display'
import { pageSlice, ruleProjectLabel, statusLabel } from './model'
import './styles.css'

const governedSources = [
  'MAT_RECEIPT',
  'MAT_REQUISITION',
  'SUB_MEASURE',
  'VAR_ORDER',
  'CT_CHANGE',
  'CT_CONTRACT',
  'QUALITY_SAFETY_CONSEQUENCE',
  'OVERHEAD_ALLOCATION',
  'OVERHEAD_ALLOCATION_CLEARING',
  'ACCOUNTING_ENTRY_LINE',
  'EXPENSE_APPLICATION',
  'FINANCE_COST_ALLOCATION',
  'FINANCE_COST_ALLOCATION_REVERSAL',
  'BID_COST',
  'BID_COST_WRITE_OFF',
  'MATERIAL_RETURN',
  'MATERIAL_RETURN_REVERSAL',
  'SUPPLIER_RETURN',
  'SUPPLIER_RETURN_REVERSAL',
].map((value) => ({ value, label: value }))

const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const versions = ref<MappingVersionRecord[]>([])
const rules = ref<AssignmentRuleRecord[]>([])
const overheadRules = ref<OverheadAllocationRuleRecord[]>([])
const options = ref<GovernanceFormOptions>({
  projects: [],
  costSubjects: [],
  rulePlans: [],
  bidCosts: [],
  targetVersions: [],
  financeSources: [],
  pendingClassifications: [],
})
const versionPageNo = ref(1)
const rulePageNo = ref(1)
const pageSize = 10
const planDialog = ref(false)
const ruleDialog = ref(false)
const trialDialog = ref(false)
const overheadDialog = ref(false)
const editingOverheadRuleId = ref('')
const overheadExecuteDialog = ref(false)
const trialResult = ref<Record<string, unknown> | null>(null)
const validationResult = ref<Record<string, unknown> | null>(null)
const overheadExecutionResult = ref<OverheadAllocationExecutionResult | null>(null)
let controller: AbortController | null = null

const canPlanEdit = computed(() => session.hasPermission('cost:subject:mapping:edit'))
const canRuleEdit = computed(() => session.hasPermission('cost:subject:rule:edit'))
const canSubmit = computed(() => session.hasPermission('cost:rule-plan:submit'))
const canOverheadQuery = computed(() => session.hasAdminOrPermission('overhead:query'))
const canOverheadAdd = computed(() => session.hasPermission('overhead:add'))
const canOverheadEdit = computed(() => session.hasPermission('overhead:edit'))
const canOverheadExecute = computed(() => session.hasPermission('overhead:execute'))
const pagedVersions = computed(() => pageSlice(versions.value, versionPageNo.value, pageSize))
const pagedRules = computed(() => pageSlice(rules.value, rulePageNo.value, pageSize))
const subjectOptions = computed(() =>
  options.value.costSubjects.map((item) => ({
    value: item.id,
    label: `${item.subjectCode} · ${item.subjectName}`,
  })),
)
const projectOptions = computed(() => [
  { value: '', label: '全公司通用' },
  ...options.value.projects.map((item) => ({
    value: item.id,
    label: `${item.projectCode} · ${item.projectName}`,
  })),
])
const draftPlanOptions = computed(() =>
  versions.value
    .filter((item) => item.status === 'DRAFT')
    .map((item) => ({ value: item.id, label: `${item.versionCode} · ${item.versionName}` })),
)
const overheadSubjectOptions = computed(() =>
  options.value.costSubjects
    .filter((item) => item.subjectType === 'OVERHEAD' && item.status === 'ENABLE')
    .map((item) => ({ value: item.id, label: `${item.subjectCode} · ${item.subjectName}` })),
)

const planForm = reactive({
  versionCode: '',
  versionName: '',
  effectiveDate: '',
  remark: '',
  mappings: [mappingLine()],
  rules: [ruleLine()],
})
const ruleForm = reactive({
  ruleCode: '',
  mappingVersionId: '',
  sourceType: '',
  businessCategory: '*',
  projectId: '',
  costSubjectId: '',
  priority: '100',
  effectiveFrom: '',
  effectiveTo: '',
  remark: '',
})
const trialForm = reactive({
  planId: '',
  sourceType: '',
  businessCategory: '*',
  projectId: '',
  baseId: '',
})
const overheadForm = reactive({
  costSubjectId: '',
  allocationBasis: 'DIRECT_LABOR' as OverheadAllocationRuleCommand['allocationBasis'],
  allocationCycle: 'MONTHLY' as const,
})
const overheadExecutionForm = reactive({ period: previousMonthValue() })

function previousMonthValue(): string {
  const now = new Date()
  let year = now.getFullYear()
  let month = now.getMonth()
  if (month === 0) {
    year -= 1
    month = 12
  }
  return `${year}-${String(month).padStart(2, '0')}`
}

function monthEndDate(value: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(value)
  if (!match) return ''
  const year = Number(match[1])
  const month = Number(match[2])
  const day = new Date(year, month, 0).getDate()
  return `${match[1]}-${match[2]}-${String(day).padStart(2, '0')}`
}

function mappingLine() {
  return {
    sourceSubjectId: '',
    targetGroupCode: '',
    targetSubjectId: '',
    historicalDisplayName: '',
    mappingReason: '',
  }
}
function ruleLine() {
  return {
    ruleCode: '',
    sourceType: '',
    businessCategory: '*',
    projectId: '',
    costSubjectId: '',
    priority: '100',
    effectiveFrom: '',
    effectiveTo: '',
    remark: '',
  }
}
function messageOf(value: unknown): string {
  return isApiClientError(value)
    ? value.message
    : value instanceof Error
      ? value.message
      : '请求失败，请稍后重试'
}

function overheadSubjectLabel(id: string): string {
  const subject = options.value.costSubjects.find((item) => item.id === id)
  return subject ? `${subject.subjectCode} · ${subject.subjectName}` : id
}

function allocationBasisLabel(value: OverheadAllocationRuleRecord['allocationBasis']): string {
  return { DIRECT_LABOR: '直接人工', CONTRACT_AMOUNT: '合同金额', USAGE: '使用量' }[value]
}

function allocationCycleLabel(value: OverheadAllocationRuleRecord['allocationCycle']): string {
  return value === 'MONTHLY' ? '每月' : '按次'
}

async function loadPage(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    const [versionRows, ruleRows, formOptions, overheadPage] = await Promise.all([
      loadMappingVersions(current.signal),
      loadAssignmentRules(current.signal),
      loadGovernanceFormOptions(current.signal),
      canOverheadQuery.value
        ? loadOverheadAllocationRules(current.signal)
        : Promise.resolve({ records: [], total: 0, pageNo: 1, pageSize: 100 }),
    ])
    versions.value = versionRows
    rules.value = ruleRows
    options.value = formOptions
    overheadRules.value = overheadPage.records
  } catch (value) {
    if (!current.signal.aborted) error.value = messageOf(value)
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshPage(): Promise<void> {
  await loadPage()
  if (!error.value) showToast('success', '已刷新', '成本规则方案已更新。')
}

function openOverheadRule(): void {
  editingOverheadRuleId.value = ''
  Object.assign(overheadForm, {
    costSubjectId: '',
    allocationBasis: 'DIRECT_LABOR',
    allocationCycle: 'MONTHLY',
  })
  overheadDialog.value = true
}

function editOverheadRule(record: OverheadAllocationRuleRecord): void {
  editingOverheadRuleId.value = record.id
  Object.assign(overheadForm, {
    costSubjectId: record.costSubjectId,
    allocationBasis: record.allocationBasis,
    // 旧版按次规则已停止支持；编辑保存即迁移为当前唯一可执行的月度周期。
    allocationCycle: 'MONTHLY',
  })
  overheadDialog.value = true
}

async function saveOverheadRule(): Promise<void> {
  if (!overheadForm.costSubjectId) {
    showToast('warning', '请选择间接费科目', '仅可选择启用的末级间接费成本科目。')
    return
  }
  saving.value = true
  try {
    if (editingOverheadRuleId.value) {
      await updateOverheadAllocationRule(editingOverheadRuleId.value, { ...overheadForm })
    } else {
      await createOverheadAllocationRule({ ...overheadForm })
    }
    overheadDialog.value = false
    await loadPage()
    showToast(
      'success',
      editingOverheadRuleId.value ? '间接费规则已更新' : '间接费规则已创建',
      '规则已保存；产生执行事实后参数将冻结。',
    )
  } catch (value) {
    showToast('error', editingOverheadRuleId.value ? '更新失败' : '创建失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function toggleOverheadRule(record: OverheadAllocationRuleRecord): Promise<void> {
  const status = record.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  saving.value = true
  try {
    await setOverheadAllocationRuleStatus(record.id, status)
    await loadPage()
    showToast('success', status === 'ENABLE' ? '规则已启用' : '规则已停用', '历史分摊事实未变更。')
  } catch (value) {
    showToast('error', '状态更新失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function runOverheadAllocation(): Promise<void> {
  if (!overheadExecutionForm.period) {
    showToast('warning', '请选择期间', '期间不能为空。')
    return
  }
  saving.value = true
  try {
    overheadExecutionResult.value = await executeOverheadAllocation(
      monthEndDate(overheadExecutionForm.period),
    )
    showToast(
      'success',
      overheadExecutionResult.value.idempotent ? '期间已处理' : '分摊执行完成',
      `生成 ${overheadExecutionResult.value.costItemCount} 条成本事实，金额 ${formatAmount(overheadExecutionResult.value.allocatedAmount)}。`,
    )
  } catch (value) {
    showToast('error', '执行失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function resetPlan(): void {
  Object.assign(planForm, {
    versionCode: '',
    versionName: '',
    effectiveDate: new Date().toISOString().slice(0, 10),
    remark: '',
    mappings: [mappingLine()],
    rules: [ruleLine()],
  })
  planDialog.value = true
}

async function savePlan(): Promise<void> {
  const mappings = planForm.mappings
  const planRules = planForm.rules
  if (
    !planForm.versionCode.trim() ||
    !planForm.versionName.trim() ||
    !planForm.effectiveDate ||
    mappings.some(
      (line) =>
        !line.sourceSubjectId ||
        !line.targetGroupCode.trim() ||
        !line.targetSubjectId ||
        !line.historicalDisplayName.trim(),
    ) ||
    planRules.some((line) => !line.ruleCode.trim() || !line.sourceType || !line.costSubjectId)
  ) {
    showToast('warning', '方案不完整', '版本、多行科目映射及多行自动归集规则均需完整。')
    return
  }
  saving.value = true
  try {
    await createMappingVersion({
      versionCode: planForm.versionCode.trim(),
      versionName: planForm.versionName.trim(),
      effectiveDate: planForm.effectiveDate,
      remark: planForm.remark.trim(),
      items: mappings.map((line) => ({ ...line, targetSubjectId: line.targetSubjectId || null })),
      rules: planRules.map((line) => ({
        ...line,
        projectId: line.projectId || null,
        priority: Number(line.priority),
        effectiveFrom: line.effectiveFrom || null,
        effectiveTo: line.effectiveTo || null,
      })),
    })
    planDialog.value = false
    await loadPage()
    showToast('success', '成本规则方案已保存', '请先系统校验，再提交财务负责人审批。')
  } catch (value) {
    showToast('error', '创建失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function generateInitial(): Promise<void> {
  saving.value = true
  try {
    await generateInitialRulePlan()
    await loadPage()
    showToast('success', '初始方案已生成', '系统未自动启用，请复核、校验并审批。')
  } catch (value) {
    showToast('error', '生成失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function validatePlan(record: MappingVersionRecord): Promise<void> {
  saving.value = true
  try {
    validationResult.value = await validateRulePlan(record.id)
    await loadPage()
    showToast(
      'success',
      '系统校验完成',
      validationResult.value.passed ? '方案通过校验。' : '存在缺失来源或冲突，请查看报告。',
    )
  } catch (value) {
    showToast('error', '校验失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function submitPlan(record: MappingVersionRecord): Promise<void> {
  saving.value = true
  try {
    await submitRulePlan(record.id)
    await loadPage()
    showToast('success', '已提交审批', '财务负责人审批通过后系统自动启用并退役旧方案。')
  } catch (value) {
    showToast('error', '提交失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function saveRule(): Promise<void> {
  if (
    !ruleForm.ruleCode.trim() ||
    !ruleForm.mappingVersionId ||
    !ruleForm.sourceType ||
    !ruleForm.costSubjectId
  ) {
    showToast('warning', '规则不完整', '请选择草稿方案、业务来源和目标末级成本科目。')
    return
  }
  saving.value = true
  try {
    await createAssignmentRule({
      ...ruleForm,
      projectId: ruleForm.projectId || null,
      priority: Number(ruleForm.priority),
      effectiveFrom: ruleForm.effectiveFrom || null,
      effectiveTo: ruleForm.effectiveTo || null,
    })
    ruleDialog.value = false
    await loadPage()
    showToast('success', '规则已添加', '请重新校验所属方案。')
  } catch (value) {
    showToast('error', '新增失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function runTrial(): Promise<void> {
  if (!trialForm.planId || !trialForm.sourceType) {
    showToast('warning', '试算条件缺失', '请选择方案与业务来源。')
    return
  }
  saving.value = true
  try {
    const trial = await trialRulePlan(
      trialForm.planId,
      trialForm.sourceType,
      trialForm.businessCategory,
      trialForm.projectId,
    )
    const diff = trialForm.baseId ? await diffRulePlan(trialForm.planId, trialForm.baseId) : null
    trialResult.value = { trial, diff }
  } catch (value) {
    showToast('error', '试算失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

onMounted(() => void loadPage())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="cost-subject-page" :gap="4">
    <V2Card title="成本规则方案" :heading-level="1">
      <template #actions>
        <V2Cluster>
          <V2Button
            v-if="canPlanEdit"
            size="small"
            variant="secondary"
            :loading="saving"
            @click="generateInitial"
            >生成标准初始方案</V2Button
          >
          <V2Button v-if="canPlanEdit" size="small" @click="resetPlan">新建方案</V2Button>
          <V2Button size="small" variant="secondary" @click="refreshPage">刷新</V2Button>
        </V2Cluster>
      </template>
    </V2Card>
    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在读取成本规则方案"
      description="请稍候。"
    />
    <V2PageState v-else-if="error" kind="error" title="加载失败" :description="error"
      ><template #actions><V2Button @click="loadPage">重试</V2Button></template></V2PageState
    >
    <template v-else>
      <V2Card title="版本方案">
        <template #actions
          ><V2Button size="small" variant="secondary" @click="trialDialog = true"
            >规则试算与版本差异</V2Button
          ></template
        >
        <V2PageState
          v-if="!versions.length"
          kind="empty"
          title="暂无方案"
          description="可生成标准初始方案，复核后再审批启用。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>版本编号</th>
                <th>名称</th>
                <th>映射数</th>
                <th>生效日</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedVersions" :key="record.id">
                <th>{{ record.versionCode }}</th>
                <td>{{ record.versionName }}</td>
                <td>{{ record.itemCount }}</td>
                <td>{{ record.effectiveDate || '—' }}</td>
                <td>
                  <V2Badge tone="neutral">{{ statusLabel(record.status) }}</V2Badge>
                </td>
                <td>
                  <V2Cluster>
                    <V2Button
                      v-if="
                        canPlanEdit && ['DRAFT', 'REJECTED', 'VALIDATED'].includes(record.status)
                      "
                      size="small"
                      variant="secondary"
                      :loading="saving"
                      @click="validatePlan(record)"
                      >系统校验</V2Button
                    >
                    <V2Button
                      v-if="canSubmit && record.status === 'VALIDATED'"
                      size="small"
                      :loading="saving"
                      @click="submitPlan(record)"
                      >提交审批</V2Button
                    >
                  </V2Cluster>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer
          ><V2Pagination
            :total="versions.length"
            :page-no="versionPageNo"
            :page-size="pageSize"
            label="方案分页"
            @update:page-no="versionPageNo = $event"
        /></template>
      </V2Card>
      <V2Card title="方案规则明细"
        ><template #actions
          ><V2Button
            v-if="canRuleEdit && draftPlanOptions.length"
            size="small"
            @click="ruleDialog = true"
            >向草稿方案新增规则</V2Button
          ></template
        >
        <V2PageState
          v-if="!rules.length"
          kind="empty"
          title="暂无规则"
          description="未命中规则时业务保持待归类，不得提交或入账。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>规则</th>
                <th>来源</th>
                <th>分类</th>
                <th>项目</th>
                <th>目标科目</th>
                <th>版本</th>
                <th>优先级</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedRules" :key="record.id">
                <th>{{ record.ruleCode }}</th>
                <td>{{ record.sourceType }}</td>
                <td>{{ record.businessCategory }}</td>
                <td>{{ ruleProjectLabel(record) }}</td>
                <td>{{ record.subjectCode }} · {{ record.subjectName }}</td>
                <td>{{ record.versionCode }}</td>
                <td>{{ record.priority }}</td>
                <td>{{ statusLabel(record.status) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer
          ><V2Pagination
            :total="rules.length"
            :page-no="rulePageNo"
            :page-size="pageSize"
            label="规则分页"
            @update:page-no="rulePageNo = $event"
        /></template>
      </V2Card>
      <V2Card v-if="canOverheadQuery" title="间接费分摊规则">
        <template #actions
          ><V2Cluster>
            <V2Button
              v-if="canOverheadExecute"
              size="small"
              variant="secondary"
              @click="overheadExecuteDialog = true"
              >按期间执行</V2Button
            ><V2Button v-if="canOverheadAdd" size="small" @click="openOverheadRule"
              >新建间接费规则</V2Button
            ></V2Cluster
          ></template
        >
        <V2PageState
          v-if="!overheadRules.length"
          kind="empty"
          title="暂无间接费分摊规则"
          description="配置启用的末级间接费成本科目后，可按月归集。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>间接费科目编码</th>
                <th>分摊依据</th>
                <th>执行周期</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in overheadRules" :key="record.id">
                <th>{{ overheadSubjectLabel(record.costSubjectId) }}</th>
                <td>{{ allocationBasisLabel(record.allocationBasis) }}</td>
                <td>{{ allocationCycleLabel(record.allocationCycle) }}</td>
                <td>
                  <V2Badge :tone="record.status === 'ENABLE' ? 'success' : 'neutral'">{{
                    record.status === 'ENABLE' ? '已启用' : '已停用'
                  }}</V2Badge>
                </td>
                <td>
                  <V2Button
                    v-if="canOverheadEdit"
                    size="small"
                    variant="secondary"
                    :disabled="saving"
                    @click="editOverheadRule(record)"
                    >编辑</V2Button
                  >
                  <V2Button
                    v-if="canOverheadEdit"
                    size="small"
                    variant="secondary"
                    :loading="saving"
                    @click="toggleOverheadRule(record)"
                    >{{ record.status === 'ENABLE' ? '停用' : '启用' }}</V2Button
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>
      <V2Card v-if="validationResult" title="最近校验报告">
        <pre class="cost-subject-page__report">{{ JSON.stringify(validationResult, null, 2) }}</pre>
      </V2Card>
    </template>

    <V2Dialog
      :open="overheadDialog"
      :title="editingOverheadRuleId ? '编辑间接费分摊规则' : '新建间接费分摊规则'"
      description="仅选择启用的末级间接费成本科目；规则一旦产生执行事实，参数将冻结。"
      :close-disabled="saving"
      @close="overheadDialog = false"
    >
      <form id="overhead-rule-form" class="cost-subject-page__form" @submit.prevent="saveOverheadRule">
        <V2Select
          v-model="overheadForm.costSubjectId"
          :options="overheadSubjectOptions"
          label="间接费科目"
          required
        /><V2Select
          v-model="overheadForm.allocationBasis"
          :options="[
            { value: 'DIRECT_LABOR', label: '直接人工' },
            { value: 'CONTRACT_AMOUNT', label: '合同金额' },
          ]"
          label="分摊依据"
          required
        /><V2Input model-value="每月" label="执行周期" disabled />
      </form>
      <template #footer
        ><V2Button variant="secondary" :disabled="saving" @click="overheadDialog = false"
          >取消</V2Button
        ><V2Button type="submit" form="overhead-rule-form" :loading="saving">保存规则</V2Button></template
      >
    </V2Dialog>

    <V2Dialog
      :open="overheadExecuteDialog"
      title="执行间接费分摊"
      description="同一规则与期间幂等；已处理期间不会重复生成成本事实。"
      :close-disabled="saving"
      @close="overheadExecuteDialog = false"
    >
      <form
        id="overhead-execute-form"
        class="cost-subject-page__form"
        @submit.prevent="runOverheadAllocation"
      >
        <V2Input v-model="overheadExecutionForm.period" type="month" label="分摊期间" required />
        <dl v-if="overheadExecutionResult" class="cost-subject-page__summary cost-subject-page__span">
          <div><dt>规则数</dt><dd>{{ overheadExecutionResult.ruleCount }}</dd></div>
          <div><dt>新增批次</dt><dd>{{ overheadExecutionResult.createdRunCount }}</dd></div>
          <div><dt>成本事实</dt><dd>{{ overheadExecutionResult.costItemCount }}</dd></div>
          <div><dt>分摊金额</dt><dd>{{ formatAmount(overheadExecutionResult.allocatedAmount) }}</dd></div>
        </dl>
      </form>
      <template #footer
        ><V2Button variant="secondary" :disabled="saving" @click="overheadExecuteDialog = false"
          >关闭</V2Button
        ><V2Button type="submit" form="overhead-execute-form" :loading="saving"
          >执行分摊</V2Button
        ></template
      >
    </V2Dialog>

    <V2Dialog
      :open="planDialog"
      title="新建成本规则方案"
      description="科目映射与自动归集规则统一保存、校验、审批和启用。"
      panel-class="v2-dialog-wide"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="planDialog = false"
    >
      <form
        id="rule-plan-form"
        class="cost-subject-page__form cost-subject-page__form--wide"
        @submit.prevent="savePlan"
      >
        <V2Input v-model="planForm.versionCode" label="版本编码" required /><V2Input
          v-model="planForm.versionName"
          label="版本名称"
          required
        /><V2Input v-model="planForm.effectiveDate" label="生效日期" required /><V2Input
          v-model="planForm.remark"
          label="方案说明"
        />
        <fieldset class="cost-subject-page__lines cost-subject-page__span">
          <legend>源科目 → 归集组 → 目标末级成本科目</legend>
          <div
            v-for="(line, index) in planForm.mappings"
            :key="index"
            class="cost-subject-page__line-grid"
          >
            <V2Select
              v-model="line.sourceSubjectId"
              :options="subjectOptions"
              :label="`源科目 ${index + 1}`"
              required
            /><V2Input v-model="line.targetGroupCode" label="归集组" required /><V2Select
              v-model="line.targetSubjectId"
              :options="subjectOptions"
              label="目标科目"
              required
            /><V2Input v-model="line.historicalDisplayName" label="历史展示名" required /><V2Input
              v-model="line.mappingReason"
              label="映射原因"
            /><V2Button
              type="button"
              size="small"
              variant="secondary"
              :disabled="planForm.mappings.length === 1"
              @click="planForm.mappings.splice(index, 1)"
              >移除</V2Button
            >
          </div>
          <V2Button
            type="button"
            size="small"
            variant="secondary"
            @click="planForm.mappings.push(mappingLine())"
            >增加映射</V2Button
          >
        </fieldset>
        <fieldset class="cost-subject-page__lines cost-subject-page__span">
          <legend>自动归集规则</legend>
          <div
            v-for="(line, index) in planForm.rules"
            :key="index"
            class="cost-subject-page__line-grid"
          >
            <V2Input v-model="line.ruleCode" :label="`规则编码 ${index + 1}`" required /><V2Select
              v-model="line.sourceType"
              :options="governedSources"
              label="业务来源"
              required
            /><V2Input v-model="line.businessCategory" label="业务分类" /><V2Select
              v-model="line.projectId"
              :options="projectOptions"
              label="项目范围"
            /><V2Select
              v-model="line.costSubjectId"
              :options="subjectOptions"
              label="目标科目"
              required
            /><V2Input v-model="line.priority" label="优先级" /><V2Button
              type="button"
              size="small"
              variant="secondary"
              :disabled="planForm.rules.length === 1"
              @click="planForm.rules.splice(index, 1)"
              >移除</V2Button
            >
          </div>
          <V2Button
            type="button"
            size="small"
            variant="secondary"
            @click="planForm.rules.push(ruleLine())"
            >增加规则</V2Button
          >
        </fieldset>
      </form>
      <template #footer
        ><V2Button variant="secondary" :disabled="saving" @click="planDialog = false">取消</V2Button
        ><V2Button type="submit" form="rule-plan-form" :loading="saving"
          >保存统一草稿</V2Button
        ></template
      >
    </V2Dialog>

    <V2Dialog
      :open="ruleDialog"
      title="向草稿方案新增规则"
      :close-disabled="saving"
      @close="ruleDialog = false"
      ><form id="single-rule-form" class="cost-subject-page__form" @submit.prevent="saveRule">
        <V2Select
          v-model="ruleForm.mappingVersionId"
          :options="draftPlanOptions"
          label="草稿方案"
          required
        /><V2Input v-model="ruleForm.ruleCode" label="规则编码" required /><V2Select
          v-model="ruleForm.sourceType"
          :options="governedSources"
          label="业务来源"
          required
        /><V2Input v-model="ruleForm.businessCategory" label="业务分类" /><V2Select
          v-model="ruleForm.projectId"
          :options="projectOptions"
          label="项目范围"
        /><V2Select
          v-model="ruleForm.costSubjectId"
          :options="subjectOptions"
          label="目标科目"
          required
        /><V2Input v-model="ruleForm.priority" label="优先级" /><V2Input
          v-model="ruleForm.remark"
          label="说明"
        />
      </form>
      <template #footer
        ><V2Button variant="secondary" @click="ruleDialog = false">取消</V2Button
        ><V2Button type="submit" form="single-rule-form" :loading="saving">添加</V2Button></template
      ></V2Dialog
    >

    <V2Dialog
      :open="trialDialog"
      title="规则试算与版本差异"
      :close-disabled="saving"
      @close="trialDialog = false"
      ><form id="trial-form" class="cost-subject-page__form" @submit.prevent="runTrial">
        <V2Select
          v-model="trialForm.planId"
          :options="
            options.rulePlans.map((item) => ({
              value: item.id,
              label: `${item.versionCode} · ${item.versionName}`,
            }))
          "
          label="试算方案"
          required
        /><V2Select
          v-model="trialForm.sourceType"
          :options="governedSources"
          label="业务来源"
          required
        /><V2Input v-model="trialForm.businessCategory" label="业务分类" /><V2Select
          v-model="trialForm.projectId"
          :options="projectOptions"
          label="项目"
        /><V2Select
          v-model="trialForm.baseId"
          :options="[
            { value: '', label: '不比较' },
            ...options.rulePlans.map((item) => ({
              value: item.id,
              label: `${item.versionCode} · ${item.versionName}`,
            })),
          ]"
          label="对比基线"
        />
        <pre v-if="trialResult" class="cost-subject-page__report cost-subject-page__span">{{
          JSON.stringify(trialResult, null, 2)
        }}</pre>
      </form>
      <template #footer
        ><V2Button variant="secondary" @click="trialDialog = false">关闭</V2Button
        ><V2Button type="submit" form="trial-form" :loading="saving">执行试算</V2Button></template
      ></V2Dialog
    >
  </V2Stack>
</template>
