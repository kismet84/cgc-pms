<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2Cluster,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import {
  activateMappingVersion,
  createAssignmentRule,
  createBidTransfer,
  createCostSubject,
  createFinanceAllocation,
  createMappingVersion,
  deleteCostSubject,
  loadAssignmentRules,
  loadBidTransfers,
  loadCostSubject,
  loadCostSubjectReconciliation,
  loadCostSubjectTree,
  loadFinanceAllocations,
  loadMappingVersions,
  loadProjectScopes,
  loadSubjectImpact,
  reverseBidTransfer,
  reverseFinanceAllocation,
  saveProjectScope,
  toggleCostSubjectStatus,
  updateCostSubject,
  type AssignmentRuleRecord,
  type CostSubjectAuditRow,
  type CostSubjectCommand,
  type CostSubjectRecord,
  type MappingVersionRecord,
  type ProjectScopeRecord,
  type SubjectImpactRecord,
} from '@/services/cost-subject'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'

type Section = 'taxonomy' | 'rules' | 'scope' | 'trace'

const route = useRoute()
const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
let controller: AbortController | null = null

const section = computed<Section>(() => {
  const value = route.path.split('/').at(-1)
  return value === 'rules' || value === 'scope' || value === 'trace' ? value : 'taxonomy'
})
const title = computed(
  () =>
    ({
      taxonomy: '成本科目体系',
      rules: '归集规则与映射版本',
      scope: '项目适用与目标成本',
      trace: '影响与转入追踪',
    })[section.value],
)

const can = (permission: string) => session.hasAdminOrPermission(permission)
const canSubjectAdd = computed(() => can('cost:add'))
const canSubjectEdit = computed(() => can('cost:edit'))
const canSubjectDelete = computed(() => can('cost:delete'))
const canMappingEdit = computed(() => can('cost:subject:mapping:edit'))
const canMappingActivate = computed(() => can('cost:subject:mapping:activate'))
const canRuleEdit = computed(() => can('cost:subject:rule:edit'))
const canScopeEdit = computed(() => can('cost:subject:scope:edit'))
const canBidTransfer = computed(() => can('cost:subject:bid-transfer'))
const canFinanceAllocate = computed(() => can('cost:subject:finance-allocate'))

const subjects = ref<CostSubjectRecord[]>([])
const selectedFirstLevelId = ref('')
const selectedSubjectId = ref('')
const standardCostRoot = computed(
  () => subjects.value.find((item) => item.subjectCode === '5401') ?? null,
)
const firstLevelSubjects = computed(() => standardCostRoot.value?.children ?? [])
const selectedFirstLevel = computed(
  () => firstLevelSubjects.value.find((item) => item.id === selectedFirstLevelId.value) ?? null,
)
const secondLevelSubjects = computed(() => selectedFirstLevel.value?.children ?? [])
const selectedSubject = computed(
  () => secondLevelSubjects.value.find((item) => item.id === selectedSubjectId.value) ?? null,
)
const subjectDialog = ref(false)
const subjectMode = ref<'create' | 'edit'>('create')
const subjectDeleteTarget = ref<CostSubjectRecord | null>(null)
const subjectStatusTarget = ref<CostSubjectRecord | null>(null)
const subjectForm = reactive({
  parentId: '0',
  subjectCode: '',
  subjectName: '',
  subjectType: 'MATERIAL',
  sortOrder: '0',
  status: 'ENABLE',
})

const versions = ref<MappingVersionRecord[]>([])
const rules = ref<AssignmentRuleRecord[]>([])
const pageSize = 10
const versionPageNo = ref(1)
const rulePageNo = ref(1)
const mappingDialog = ref(false)
const activationTarget = ref<MappingVersionRecord | null>(null)
const activationApprovalId = ref('')
const ruleDialog = ref(false)
const mappingForm = reactive({
  versionCode: '',
  versionName: '',
  effectiveDate: '',
  remark: '',
  sourceSubjectId: '',
  targetGroupCode: '',
  targetSubjectId: '',
  historicalDisplayName: '',
  mappingReason: '',
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

const scopeProjectId = ref('')
const scopes = ref<ProjectScopeRecord[]>([])
const scopePageNo = ref(1)
const scopeDialog = ref(false)
const scopeForm = reactive({
  costSubjectId: '',
  enabled: 'true',
  effectiveFrom: '',
  effectiveTo: '',
  remark: '',
})

const impactSubjectId = ref('')
const reconciliationProjectId = ref('')
const impact = ref<SubjectImpactRecord | null>(null)
const reconciliation = ref<CostSubjectAuditRow | null>(null)
const transfers = ref<CostSubjectAuditRow[]>([])
const allocations = ref<CostSubjectAuditRow[]>([])
const transferPageNo = ref(1)
const allocationPageNo = ref(1)
const transferDialog = ref(false)
const allocationDialog = ref(false)
const reverseTarget = ref<CostSubjectAuditRow | null>(null)
const reverseKind = ref<'transfer' | 'allocation'>('transfer')
const transferForm = reactive({
  bidCostId: '',
  projectId: '',
  targetId: '',
  mappingVersionId: '',
  approvalInstanceId: '',
  idempotencyKey: '',
  remark: '',
})
const allocationForm = reactive({
  sourceType: 'ACCOUNTING_ENTRY_LINE',
  sourceId: '',
  allocationBasis: 'BENEFIT_AMOUNT',
  accountingPeriod: '',
  costSubjectId: '',
  approvalInstanceId: '',
  idempotencyKey: '',
  remark: '',
  lines: [{ projectId: '', basisValue: '1' }],
})
const reverseForm = reactive({ approvalInstanceId: '', idempotencyKey: '', remark: '' })

const statusOptions = [
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
]
const subjectTypeLabels: Record<string, string> = {
  ROOT: '根科目',
  BID: '投标成本',
  PURCHASE: '采购成本',
  MATERIAL: '材料费',
  TESTING: '试验检测费',
  CONSTRUCTION: '施工成本',
  LABOR: '人工费',
  MACHINERY: '机械费',
  UTILITY: '水电费',
  SUBCONTRACT: '分包费',
  MEASURES: '措施费',
  OTHER: '其他成本',
  OVERHEAD: '间接费用',
}
const subjectTypeLabel = (value?: string) => subjectTypeLabels[value ?? ''] ?? '其他成本'
const enabledOptions = [
  { value: 'true', label: '启用' },
  { value: 'false', label: '停用' },
]
const sourceTypeOptions = [
  { value: 'ACCOUNTING_ENTRY_LINE', label: '已过账借方凭证明细' },
  { value: 'EXPENSE_APPLICATION', label: '已审批费用申请' },
]
const allocationBasisOptions = [
  { value: 'DIRECT_PROJECT', label: '直接归属' },
  { value: 'BENEFIT_AMOUNT', label: '受益金额' },
  { value: 'OCCUPIED_DAYS', label: '占用天数' },
  { value: 'CONTRACT_AMOUNT_EXCEPTION', label: '合同额例外' },
]
const impactLabels: Array<[keyof SubjectImpactRecord, string]> = [
  ['costItems', '成本明细'],
  ['targetItems', '目标成本明细'],
  ['forecastItems', '完工预测'],
  ['budgetLines', '预算明细'],
  ['payments', '付款申请'],
  ['expenses', '费用申请'],
  ['settlementItems', '结算明细'],
  ['accountingLines', '会计凭证明细'],
  ['assignmentRules', '归集规则'],
  ['projectScopes', '项目范围'],
]
const pagedVersions = computed(() => pageSlice(versions.value, versionPageNo.value))
const pagedRules = computed(() => pageSlice(rules.value, rulePageNo.value))
const pagedScopes = computed(() => pageSlice(scopes.value, scopePageNo.value))
const pagedTransfers = computed(() => pageSlice(transfers.value, transferPageNo.value))
const pagedAllocations = computed(() => pageSlice(allocations.value, allocationPageNo.value))

function pageSlice<T>(items: T[], pageNo: number): T[] {
  return items.slice((pageNo - 1) * pageSize, pageNo * pageSize)
}

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

function normalizeTaxonomySelection(): void {
  if (!firstLevelSubjects.value.some((item) => item.id === selectedFirstLevelId.value)) {
    selectedFirstLevelId.value = firstLevelSubjects.value[0]?.id ?? ''
  }
  if (!secondLevelSubjects.value.some((item) => item.id === selectedSubjectId.value)) {
    selectedSubjectId.value = secondLevelSubjects.value[0]?.id ?? ''
  }
}

function selectFirstLevel(subject: CostSubjectRecord): void {
  selectedFirstLevelId.value = subject.id
  selectedSubjectId.value = subject.children?.[0]?.id ?? ''
}

function clearSubjectForm(): void {
  Object.assign(subjectForm, {
    parentId: '0',
    subjectCode: '',
    subjectName: '',
    subjectType: 'MATERIAL',
    sortOrder: '0',
    status: 'ENABLE',
  })
}

function openSubjectCreate(parent?: CostSubjectRecord): void {
  subjectMode.value = 'create'
  clearSubjectForm()
  subjectForm.parentId = parent?.id ?? '0'
  subjectDialog.value = true
}

function openSubjectEdit(): void {
  const current = selectedSubject.value
  if (!current) return
  subjectMode.value = 'edit'
  Object.assign(subjectForm, {
    parentId: current.parentId || '0',
    subjectCode: current.subjectCode,
    subjectName: current.subjectName,
    subjectType: current.subjectType,
    sortOrder: String(current.sortOrder ?? 0),
    status: current.status,
  })
  subjectDialog.value = true
}

function subjectCommand(): CostSubjectCommand | null {
  const subjectCode = subjectForm.subjectCode.trim()
  const subjectName = subjectForm.subjectName.trim()
  if (!subjectCode || !subjectName) {
    showToast('warning', '信息不完整', '科目编码和名称不能为空。')
    return null
  }
  const sortOrder = Number(subjectForm.sortOrder)
  if (!Number.isInteger(sortOrder) || sortOrder < 0) {
    showToast('warning', '排序无效', '排序必须为非负整数。')
    return null
  }
  return {
    parentId: subjectForm.parentId || '0',
    subjectCode,
    subjectName,
    subjectType: subjectForm.subjectType.trim() || 'MATERIAL',
    accountCategory: 'COST',
    sortOrder,
    status: subjectForm.status as 'ENABLE' | 'DISABLE',
  }
}

async function saveSubject(): Promise<void> {
  const command = subjectCommand()
  if (!command) return
  saving.value = true
  try {
    const currentId = subjectMode.value === 'edit' ? selectedSubjectId.value : ''
    const savedId = currentId || String(await createCostSubject(command))
    if (currentId) await updateCostSubject(currentId, command)
    await loadCostSubject(savedId)
    subjectDialog.value = false
    await loadTaxonomy()
    selectedSubjectId.value = savedId
    showToast('success', '成本科目已保存', '科目树已刷新。')
  } catch (value) {
    showToast('error', '保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmSubjectStatus(): Promise<void> {
  if (!subjectStatusTarget.value) return
  saving.value = true
  try {
    const id = subjectStatusTarget.value.id
    await toggleCostSubjectStatus(id)
    subjectStatusTarget.value = null
    await loadCostSubject(id)
    await loadTaxonomy()
    showToast('success', '科目状态已更新', '最新状态已刷新。')
  } catch (value) {
    showToast('error', '状态更新失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmSubjectDelete(): Promise<void> {
  if (!subjectDeleteTarget.value) return
  saving.value = true
  try {
    await deleteCostSubject(subjectDeleteTarget.value.id)
    subjectDeleteTarget.value = null
    selectedSubjectId.value = ''
    await loadTaxonomy()
    showToast('success', '成本科目已删除', '科目树已刷新。')
  } catch (value) {
    showToast('error', '删除失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function loadTaxonomy(signal?: AbortSignal): Promise<void> {
  subjects.value = await loadCostSubjectTree(signal)
  normalizeTaxonomySelection()
}

async function loadRules(signal?: AbortSignal): Promise<void> {
  versionPageNo.value = 1
  rulePageNo.value = 1
  ;[versions.value, rules.value] = await Promise.all([
    loadMappingVersions(signal),
    loadAssignmentRules(signal),
  ])
}

async function saveMapping(): Promise<void> {
  if (
    !mappingForm.versionCode.trim() ||
    !mappingForm.versionName.trim() ||
    !mappingForm.sourceSubjectId.trim() ||
    !mappingForm.targetGroupCode.trim() ||
    !mappingForm.historicalDisplayName.trim()
  ) {
    showToast('warning', '信息不完整', '版本、源科目、归集组和历史展示名称不能为空。')
    return
  }
  saving.value = true
  try {
    const savedId = String(
      await createMappingVersion({
        versionCode: mappingForm.versionCode.trim(),
        versionName: mappingForm.versionName.trim(),
        effectiveDate: mappingForm.effectiveDate || null,
        remark: mappingForm.remark.trim(),
        items: [
          {
            sourceSubjectId: mappingForm.sourceSubjectId.trim(),
            targetGroupCode: mappingForm.targetGroupCode.trim(),
            targetSubjectId: mappingForm.targetSubjectId.trim() || null,
            historicalDisplayName: mappingForm.historicalDisplayName.trim(),
            mappingReason: mappingForm.mappingReason.trim(),
          },
        ],
      }),
    )
    mappingDialog.value = false
    await loadRules()
    if (!versions.value.some((item) => item.id === savedId)) {
      throw new Error('新映射版本未出现在最新列表')
    }
    showToast('success', '映射草稿已创建', '版本列表已刷新。')
  } catch (value) {
    showToast('error', '创建映射失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmActivation(): Promise<void> {
  if (!activationTarget.value || !activationApprovalId.value.trim()) {
    showToast('warning', '审批实例缺失', '必须填写已通过的审批实例标识。')
    return
  }
  saving.value = true
  try {
    await activateMappingVersion(activationTarget.value.id, activationApprovalId.value)
    activationTarget.value = null
    activationApprovalId.value = ''
    await loadRules()
    showToast('success', '映射版本已启用', '规则和版本状态已刷新。')
  } catch (value) {
    showToast('error', '启用失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function saveRule(): Promise<void> {
  if (
    !ruleForm.ruleCode.trim() ||
    !ruleForm.mappingVersionId.trim() ||
    !ruleForm.sourceType.trim() ||
    !ruleForm.costSubjectId.trim()
  ) {
    showToast('warning', '信息不完整', '规则、映射版本、业务来源和目标科目不能为空。')
    return
  }
  const priority = Number(ruleForm.priority)
  if (!Number.isInteger(priority)) {
    showToast('warning', '优先级无效', '优先级必须为整数。')
    return
  }
  saving.value = true
  try {
    const savedId = String(
      await createAssignmentRule({
        ruleCode: ruleForm.ruleCode.trim(),
        mappingVersionId: ruleForm.mappingVersionId.trim(),
        sourceType: ruleForm.sourceType.trim(),
        businessCategory: ruleForm.businessCategory.trim() || '*',
        projectId: ruleForm.projectId.trim() || null,
        costSubjectId: ruleForm.costSubjectId.trim(),
        priority,
        effectiveFrom: ruleForm.effectiveFrom || null,
        effectiveTo: ruleForm.effectiveTo || null,
        remark: ruleForm.remark.trim(),
      }),
    )
    ruleDialog.value = false
    await loadRules()
    if (!rules.value.some((item) => item.id === savedId)) {
      throw new Error('新归集规则未出现在最新列表')
    }
    showToast('success', '归集规则已创建', '规则列表已刷新。')
  } catch (value) {
    showToast('error', '创建规则失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function queryScopes(): Promise<void> {
  if (!scopeProjectId.value.trim()) {
    showToast('warning', '项目缺失', '请输入项目标识。')
    return
  }
  loading.value = true
  scopePageNo.value = 1
  error.value = ''
  try {
    scopes.value = await loadProjectScopes(scopeProjectId.value)
  } catch (value) {
    scopes.value = []
    error.value = messageOf(value)
  } finally {
    loading.value = false
  }
}

function editScope(record?: ProjectScopeRecord): void {
  Object.assign(scopeForm, {
    costSubjectId: record?.costSubjectId ?? '',
    enabled: record?.enabled === 0 ? 'false' : 'true',
    effectiveFrom: record?.effectiveFrom ?? '',
    effectiveTo: record?.effectiveTo ?? '',
    remark: '',
  })
  scopeDialog.value = true
}

async function submitScope(): Promise<void> {
  if (!scopeProjectId.value.trim() || !scopeForm.costSubjectId.trim()) {
    showToast('warning', '信息不完整', '项目和末级科目不能为空。')
    return
  }
  saving.value = true
  try {
    await saveProjectScope({
      projectId: scopeProjectId.value.trim(),
      costSubjectId: scopeForm.costSubjectId.trim(),
      enabled: scopeForm.enabled === 'true',
      effectiveFrom: scopeForm.effectiveFrom || null,
      effectiveTo: scopeForm.effectiveTo || null,
      remark: scopeForm.remark.trim(),
    })
    scopeDialog.value = false
    scopes.value = await loadProjectScopes(scopeProjectId.value)
    scopePageNo.value = 1
    showToast('success', '项目范围已保存', '适用范围已刷新。')
  } catch (value) {
    showToast('error', '保存范围失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function loadTrace(signal?: AbortSignal): Promise<void> {
  transferPageNo.value = 1
  allocationPageNo.value = 1
  ;[transfers.value, allocations.value] = await Promise.all([
    loadBidTransfers(signal),
    loadFinanceAllocations(signal),
  ])
}

async function queryImpact(): Promise<void> {
  if (!impactSubjectId.value.trim()) {
    showToast('warning', '科目缺失', '请输入成本科目标识。')
    return
  }
  try {
    impact.value = await loadSubjectImpact(impactSubjectId.value)
  } catch (value) {
    impact.value = null
    showToast('error', '影响查询失败', messageOf(value))
  }
}

async function queryReconciliation(): Promise<void> {
  if (!reconciliationProjectId.value.trim()) {
    showToast('warning', '项目缺失', '请输入项目标识。')
    return
  }
  try {
    reconciliation.value = await loadCostSubjectReconciliation(reconciliationProjectId.value)
  } catch (value) {
    reconciliation.value = null
    showToast('error', '项目对账失败', messageOf(value))
  }
}

async function submitTransfer(): Promise<void> {
  if (
    !transferForm.bidCostId.trim() ||
    !transferForm.projectId.trim() ||
    !transferForm.targetId.trim() ||
    !transferForm.mappingVersionId.trim() ||
    !transferForm.approvalInstanceId.trim() ||
    !transferForm.idempotencyKey.trim()
  ) {
    showToast('warning', '信息不完整', '转入对象、映射版本、审批实例和幂等键不能为空。')
    return
  }
  saving.value = true
  try {
    await createBidTransfer({ ...transferForm })
    transferDialog.value = false
    await loadTrace()
    showToast('success', '投标成本已转入', '目标成本转入事实已刷新。')
  } catch (value) {
    showToast('error', '转入失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function addAllocationLine(): void {
  allocationForm.lines.push({ projectId: '', basisValue: '1' })
}

function removeAllocationLine(index: number): void {
  if (allocationForm.lines.length > 1) allocationForm.lines.splice(index, 1)
}

async function submitAllocation(): Promise<void> {
  if (
    !allocationForm.sourceId.trim() ||
    !allocationForm.accountingPeriod.trim() ||
    !allocationForm.costSubjectId.trim() ||
    !allocationForm.approvalInstanceId.trim() ||
    !allocationForm.idempotencyKey.trim() ||
    allocationForm.lines.some(
      (line) => !line.projectId.trim() || !line.basisValue.trim() || Number(line.basisValue) <= 0,
    )
  ) {
    showToast('warning', '信息不完整', '来源、期间、科目、审批、幂等键和项目依据必须有效。')
    return
  }
  saving.value = true
  try {
    await createFinanceAllocation({
      sourceType: allocationForm.sourceType,
      sourceId: allocationForm.sourceId.trim(),
      allocationBasis: allocationForm.allocationBasis,
      accountingPeriod: allocationForm.accountingPeriod.trim(),
      costSubjectId: allocationForm.costSubjectId.trim(),
      approvalInstanceId: allocationForm.approvalInstanceId.trim(),
      idempotencyKey: allocationForm.idempotencyKey.trim(),
      remark: allocationForm.remark.trim(),
      lines: allocationForm.lines.map((line) => ({
        projectId: line.projectId.trim(),
        basisValue: line.basisValue.trim(),
      })),
    })
    allocationDialog.value = false
    await loadTrace()
    showToast('success', '财务费用已分摊', '分摊记录已刷新。')
  } catch (value) {
    showToast('error', '分摊失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openReverse(kind: 'transfer' | 'allocation', row: CostSubjectAuditRow): void {
  reverseKind.value = kind
  reverseTarget.value = row
  Object.assign(reverseForm, { approvalInstanceId: '', idempotencyKey: '', remark: '' })
}

async function submitReverse(): Promise<void> {
  if (
    !reverseTarget.value ||
    !reverseForm.approvalInstanceId.trim() ||
    !reverseForm.idempotencyKey.trim()
  ) {
    showToast('warning', '信息不完整', '审批实例和幂等键不能为空。')
    return
  }
  saving.value = true
  try {
    const id = String(reverseTarget.value.id)
    if (reverseKind.value === 'transfer') {
      await reverseBidTransfer(
        id,
        reverseForm.approvalInstanceId,
        reverseForm.idempotencyKey,
        reverseForm.remark,
      )
    } else {
      await reverseFinanceAllocation(
        id,
        reverseForm.approvalInstanceId,
        reverseForm.idempotencyKey,
        reverseForm.remark,
      )
    }
    reverseTarget.value = null
    await loadTrace()
    showToast('success', '冲销已完成', '反向事实已刷新。')
  } catch (value) {
    showToast('error', '冲销失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function rowText(row: CostSubjectAuditRow, key: string): string {
  const value = row[key]
  if (value == null || value === '') return '—'
  return key === 'status' ? statusLabel(String(value)) : String(value)
}

function statusLabel(status: string): string {
  return (
    {
      ACTIVE: '已启用',
      DISABLE: '停用',
      DRAFT: '草稿',
      ENABLE: '启用',
      POSTED: '已入账',
      REVERSED: '已冲销',
    }[status] ?? status
  )
}

async function loadActive(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    if (section.value === 'taxonomy') await loadTaxonomy(current.signal)
    if (section.value === 'rules') await loadRules(current.signal)
    if (section.value === 'scope') scopes.value = []
    if (section.value === 'trace') await loadTrace(current.signal)
  } catch (value) {
    if (!current.signal.aborted) error.value = messageOf(value)
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshActive(): Promise<void> {
  await loadActive()
  if (error.value) showToast('error', '刷新失败', error.value)
  else showToast('success', '已刷新', '当前内容已更新。')
}

watch(section, () => void loadActive())
onMounted(() => void loadActive())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="cost-subject-page" :gap="4">
    <V2Card :title="title" :heading-level="1">
      <template #actions>
        <form
          v-if="section === 'scope'"
          class="v2-page-heading__filters"
          @submit.prevent="queryScopes"
        >
          <V2Input
            v-model="scopeProjectId"
            label="项目标识"
            hide-label
            placeholder="项目标识"
            required
          />
          <V2Button type="submit" size="small">查询</V2Button>
          <V2Button
            v-if="canScopeEdit"
            type="button"
            size="small"
            :disabled="!scopeProjectId.trim()"
            @click="editScope()"
          >
            维护范围
          </V2Button>
          <span class="cost-subject-page__hint">
            项目存在范围配置后，目标成本和财务分摊只能使用范围内启用末级科目。
          </span>
        </form>
        <V2Button size="small" variant="secondary" @click="refreshActive">刷新</V2Button>
      </template>
    </V2Card>

    <V2PageState
      v-if="loading"
      kind="loading"
      title="正在读取成本科目事实"
      description="请稍候。"
    />
    <V2PageState v-else-if="error" kind="error" title="成本科目加载失败" :description="error">
      <template #actions><V2Button @click="loadActive">重试</V2Button></template>
    </V2PageState>

    <template v-else-if="section === 'taxonomy'">
      <V2Card>
        <div class="cost-subject-page__columns cost-subject-page__taxonomy">
          <section aria-labelledby="cost-subject-first-level-title">
            <div class="cost-subject-page__section-heading">
              <span>
                <h3 id="cost-subject-first-level-title">1. 一级科目</h3>
                <small>5401.xx · 共 {{ firstLevelSubjects.length }} 个</small>
              </span>
              <V2Button
                v-if="canSubjectAdd && standardCostRoot"
                size="small"
                @click="openSubjectCreate(standardCostRoot)"
              >
                新增一级科目
              </V2Button>
            </div>
            <V2PageState
              v-if="!standardCostRoot"
              kind="empty"
              title="缺少标准成本根科目"
              description="未读取到 5401 标准成本体系。"
            />
            <div class="cost-subject-page__list">
              <button
                v-for="subject in firstLevelSubjects"
                :key="subject.id"
                type="button"
                class="cost-subject-page__list-item"
                :class="{ 'is-selected': selectedFirstLevelId === subject.id }"
                :aria-pressed="selectedFirstLevelId === subject.id"
                @click="selectFirstLevel(subject)"
              >
                <span>
                  <strong>{{ subject.subjectName }}</strong>
                  <small>{{ subject.subjectCode }}</small>
                </span>
                <V2Badge :tone="subject.status === 'ENABLE' ? 'success' : 'neutral'">
                  {{ subject.status === 'ENABLE' ? '启用' : '停用' }}
                </V2Badge>
              </button>
            </div>
          </section>

          <section aria-labelledby="cost-subject-second-level-title">
            <div class="cost-subject-page__section-heading">
              <span>
                <h3 id="cost-subject-second-level-title">2. 二级科目</h3>
                <small>5401.xx.xx · 共 {{ secondLevelSubjects.length }} 个</small>
              </span>
              <V2Button
                v-if="canSubjectAdd && selectedFirstLevel"
                size="small"
                @click="openSubjectCreate(selectedFirstLevel)"
              >
                新增二级科目
              </V2Button>
            </div>
            <V2PageState
              v-if="!selectedFirstLevel"
              kind="empty"
              title="请选择一级科目"
              description="选择后读取所属二级科目。"
            />
            <V2PageState
              v-else-if="!secondLevelSubjects.length"
              kind="empty"
              title="暂无二级科目"
              description="可在当前一级科目下新增。"
            />
            <div v-else class="cost-subject-page__list">
              <button
                v-for="subject in secondLevelSubjects"
                :key="subject.id"
                type="button"
                class="cost-subject-page__list-item"
                :class="{ 'is-selected': selectedSubjectId === subject.id }"
                :aria-pressed="selectedSubjectId === subject.id"
                @click="selectedSubjectId = subject.id"
              >
                <span>
                  <strong>{{ subject.subjectName }}</strong>
                  <small
                    >{{ subject.subjectCode }} · {{ subjectTypeLabel(subject.subjectType) }}</small
                  >
                </span>
                <V2Badge :tone="subject.status === 'ENABLE' ? 'success' : 'neutral'">
                  {{ subject.status === 'ENABLE' ? '启用' : '停用' }}
                </V2Badge>
              </button>
            </div>
          </section>

          <section aria-labelledby="cost-subject-detail-title">
            <div class="cost-subject-page__section-heading">
              <h3 id="cost-subject-detail-title">3. 科目详情</h3>
              <V2Cluster v-if="selectedSubject">
                <V2Button
                  v-if="canSubjectAdd"
                  size="small"
                  variant="secondary"
                  @click="openSubjectCreate(selectedSubject)"
                >
                  新增子科目
                </V2Button>
                <V2Button v-if="canSubjectEdit" size="small" @click="openSubjectEdit"
                  >编辑</V2Button
                >
                <V2Button
                  v-if="canSubjectEdit"
                  size="small"
                  variant="secondary"
                  @click="subjectStatusTarget = selectedSubject"
                >
                  {{ selectedSubject.status === 'ENABLE' ? '停用' : '启用' }}
                </V2Button>
                <V2Button
                  v-if="canSubjectDelete"
                  size="small"
                  variant="danger"
                  @click="subjectDeleteTarget = selectedSubject"
                >
                  删除
                </V2Button>
              </V2Cluster>
            </div>
            <template v-if="selectedSubject">
              <dl class="cost-subject-page__facts">
                <div>
                  <dt>编码</dt>
                  <dd>{{ selectedSubject.subjectCode }}</dd>
                </div>
                <div>
                  <dt>名称</dt>
                  <dd>{{ selectedSubject.subjectName }}</dd>
                </div>
                <div>
                  <dt>类型</dt>
                  <dd>{{ subjectTypeLabel(selectedSubject.subjectType) }}</dd>
                </div>
                <div>
                  <dt>层级</dt>
                  <dd>{{ selectedSubject.level }}</dd>
                </div>
                <div>
                  <dt>排序</dt>
                  <dd>{{ selectedSubject.sortOrder }}</dd>
                </div>
                <div>
                  <dt>末级</dt>
                  <dd>{{ selectedSubject.children?.length ? '否' : '是' }}</dd>
                </div>
              </dl>
            </template>
            <V2PageState
              v-else
              kind="empty"
              title="请选择科目"
              description="选择科目后查看详情。"
            />
          </section>
        </div>
      </V2Card>
    </template>

    <template v-else-if="section === 'rules'">
      <V2Card title="映射版本">
        <template #actions>
          <V2Button v-if="canMappingEdit" size="small" @click="mappingDialog = true">
            新建映射版本
          </V2Button>
        </template>
        <V2PageState
          v-if="!versions.length"
          kind="empty"
          title="暂无映射版本"
          description="映射必须先保存为草稿，再绑定已通过审批启用。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>版本</th>
                <th>名称</th>
                <th>映射数</th>
                <th>生效日期</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedVersions" :key="record.id">
                <th scope="row">{{ record.versionCode }}</th>
                <td>{{ record.versionName }}</td>
                <td>{{ record.itemCount }}</td>
                <td>{{ record.effectiveDate || '—' }}</td>
                <td>
                  <V2Badge tone="neutral">{{ statusLabel(record.status) }}</V2Badge>
                </td>
                <td>
                  <V2Button
                    v-if="record.status === 'DRAFT' && canMappingActivate"
                    size="small"
                    variant="secondary"
                    @click="activationTarget = record"
                  >
                    审批后启用
                  </V2Button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="versions.length"
            :page-no="versionPageNo"
            :page-size="pageSize"
            label="映射版本分页"
            @update:page-no="versionPageNo = $event"
          />
        </template>
      </V2Card>

      <V2Card title="显式归集规则">
        <template #actions>
          <V2Button v-if="canRuleEdit" size="small" @click="ruleDialog = true"> 新增规则 </V2Button>
        </template>
        <V2PageState
          v-if="!rules.length"
          kind="empty"
          title="暂无归集规则"
          description="无规则命中时保持待归类。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>规则</th>
                <th>来源</th>
                <th>业务分类</th>
                <th>项目</th>
                <th>科目编码</th>
                <th>科目名称</th>
                <th>版本</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedRules" :key="record.id">
                <th scope="row">{{ record.ruleCode }}</th>
                <td>{{ record.sourceType }}</td>
                <td>{{ record.businessCategory }}</td>
                <td>{{ record.projectId || '全局' }}</td>
                <td>{{ record.subjectCode }}</td>
                <td>{{ record.subjectName }}</td>
                <td>{{ record.versionCode }}</td>
                <td>
                  <V2Badge tone="neutral">{{ statusLabel(record.status) }}</V2Badge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="rules.length"
            :page-no="rulePageNo"
            :page-size="pageSize"
            label="归集规则分页"
            @update:page-no="rulePageNo = $event"
          />
        </template>
      </V2Card>
    </template>

    <template v-else-if="section === 'scope'">
      <V2PageState
        v-if="!scopes.length"
        kind="empty"
        title="暂无项目范围结果"
        description="输入项目标识查询；空结果不代表可自行放宽范围。"
      />
      <V2Card v-else title="范围结果">
        <div class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>科目编码</th>
                <th>科目名称</th>
                <th>状态</th>
                <th>生效</th>
                <th>失效</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedScopes" :key="record.id">
                <th scope="row">{{ record.subjectCode }}</th>
                <td>{{ record.subjectName }}</td>
                <td>{{ record.enabled === 1 ? '启用' : '停用' }}</td>
                <td>{{ record.effectiveFrom || '—' }}</td>
                <td>{{ record.effectiveTo || '—' }}</td>
                <td>
                  <V2Button
                    v-if="canScopeEdit"
                    size="small"
                    variant="secondary"
                    @click="editScope(record)"
                  >
                    维护
                  </V2Button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="scopes.length"
            :page-no="scopePageNo"
            :page-size="pageSize"
            label="项目范围分页"
            @update:page-no="scopePageNo = $event"
          />
        </template>
      </V2Card>
    </template>

    <template v-else>
      <V2Card title="影响分析与项目对账">
        <div class="cost-subject-page__query cost-subject-page__query--wide">
          <form @submit.prevent="queryImpact">
            <V2Input v-model="impactSubjectId" label="成本科目标识" required />
            <V2Button type="submit">查询引用影响</V2Button>
          </form>
          <form @submit.prevent="queryReconciliation">
            <V2Input v-model="reconciliationProjectId" label="项目标识" required />
            <V2Button type="submit">项目对账</V2Button>
          </form>
        </div>
        <template #actions>
          <V2Cluster>
            <V2Button v-if="canBidTransfer" size="small" @click="transferDialog = true">
              投标成本转入
            </V2Button>
            <V2Button v-if="canFinanceAllocate" size="small" @click="allocationDialog = true">
              财务费用分摊
            </V2Button>
          </V2Cluster>
        </template>
      </V2Card>

      <div v-if="impact || reconciliation" class="cost-subject-page__columns">
        <V2Card title="科目引用影响">
          <dl v-if="impact" class="cost-subject-page__facts">
            <div v-for="[key, label] in impactLabels" :key="key">
              <dt>{{ label }}</dt>
              <dd>{{ impact[key] }}</dd>
            </div>
          </dl>
          <V2PageState v-else kind="empty" title="尚未查询" description="输入成本科目标识。" />
        </V2Card>
        <V2Card title="项目成本对账">
          <dl v-if="reconciliation" class="cost-subject-page__facts">
            <div v-for="(value, key) in reconciliation" :key="key">
              <dt>{{ key }}</dt>
              <dd>{{ value }}</dd>
            </div>
          </dl>
          <V2PageState v-else kind="empty" title="尚未查询" description="输入项目标识。" />
        </V2Card>
      </div>

      <V2Card title="投标成本转入记录">
        <V2PageState
          v-if="!transfers.length"
          kind="empty"
          title="暂无转入记录"
          description="页面不推导目标成本金额或可冲销状态。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>转入编号</th>
                <th>投标项目</th>
                <th>目标版本</th>
                <th>金额</th>
                <th>状态</th>
                <th>审批实例</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedTransfers" :key="String(record.id)">
                <th scope="row">{{ rowText(record, 'transferCode') }}</th>
                <td>{{ rowText(record, 'bidProjectName') }}</td>
                <td>{{ rowText(record, 'versionNo') }}</td>
                <td>{{ rowText(record, 'totalAmount') }}</td>
                <td>{{ rowText(record, 'status') }}</td>
                <td>{{ rowText(record, 'approvalInstanceId') }}</td>
                <td>
                  <V2Button
                    v-if="canBidTransfer && record.status === 'POSTED' && !record.reversalOfId"
                    size="small"
                    variant="danger"
                    @click="openReverse('transfer', record)"
                  >
                    冲销
                  </V2Button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="transfers.length"
            :page-no="transferPageNo"
            :page-size="pageSize"
            label="投标成本转入记录分页"
            @update:page-no="transferPageNo = $event"
          />
        </template>
      </V2Card>

      <V2Card title="项目财务费用分摊记录">
        <V2PageState
          v-if="!allocations.length"
          kind="empty"
          title="暂无分摊记录"
          description="金额、状态和项目范围均以系统记录为准。"
        />
        <div v-else class="cost-subject-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>批次编号</th>
                <th>来源</th>
                <th>依据</th>
                <th>期间</th>
                <th>金额</th>
                <th>科目</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in pagedAllocations" :key="String(record.id)">
                <th scope="row">{{ rowText(record, 'batchCode') }}</th>
                <td>{{ rowText(record, 'sourceType') }}</td>
                <td>{{ rowText(record, 'allocationBasis') }}</td>
                <td>{{ rowText(record, 'accountingPeriod') }}</td>
                <td>{{ rowText(record, 'sourceAmount') }}</td>
                <td>{{ rowText(record, 'subjectName') }}</td>
                <td>{{ rowText(record, 'status') }}</td>
                <td>
                  <V2Button
                    v-if="canFinanceAllocate && record.status === 'POSTED' && !record.reversalOfId"
                    size="small"
                    variant="danger"
                    @click="openReverse('allocation', record)"
                  >
                    冲销
                  </V2Button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            :total="allocations.length"
            :page-no="allocationPageNo"
            :page-size="pageSize"
            label="财务费用分摊记录分页"
            @update:page-no="allocationPageNo = $event"
          />
        </template>
      </V2Card>
    </template>

    <V2Dialog
      :open="subjectDialog"
      :title="subjectMode === 'edit' ? '编辑成本科目' : '新增成本科目'"
      description="层级、租户、唯一性和引用保护由系统校验。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="subjectDialog = false"
    >
      <form id="cost-subject-form" class="cost-subject-page__form" @submit.prevent="saveSubject">
        <V2Input v-model="subjectForm.subjectCode" label="科目编码" required />
        <V2Input v-model="subjectForm.subjectName" label="科目名称" required />
        <V2Input v-model="subjectForm.subjectType" label="科目类型" required />
        <V2Input v-model="subjectForm.parentId" label="父科目标识" />
        <V2Input v-model="subjectForm.sortOrder" label="排序" />
        <V2Select v-model="subjectForm.status" :options="statusOptions" label="状态" required />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="subjectDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="cost-subject-form" :loading="saving">保存</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="mappingDialog"
      title="新建科目映射版本"
      description="本次创建一条映射；后续可继续创建新版本。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="mappingDialog = false"
    >
      <form id="mapping-form" class="cost-subject-page__form" @submit.prevent="saveMapping">
        <V2Input v-model="mappingForm.versionCode" label="版本编码" required />
        <V2Input v-model="mappingForm.versionName" label="版本名称" required />
        <V2Input v-model="mappingForm.effectiveDate" label="生效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="mappingForm.sourceSubjectId" label="源科目标识" required />
        <V2Input v-model="mappingForm.targetGroupCode" label="归集组编码" required />
        <V2Input v-model="mappingForm.targetSubjectId" label="目标末级科目标识" />
        <V2Input v-model="mappingForm.historicalDisplayName" label="历史展示名称" required />
        <V2Input v-model="mappingForm.mappingReason" label="映射原因" />
        <V2Input v-model="mappingForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="mappingDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="mapping-form" :loading="saving">创建草稿</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="Boolean(activationTarget)"
      title="启用映射版本"
      description="仅绑定已通过且业务匹配的审批实例。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="activationTarget = null"
    >
      <form id="activation-form" @submit.prevent="confirmActivation">
        <V2Input v-model="activationApprovalId" label="审批实例标识" required />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="activationTarget = null"
          >取消</V2Button
        >
        <V2Button type="submit" form="activation-form" :loading="saving">确认启用</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="ruleDialog"
      title="新增显式归集规则"
      description="未命中或同优先级冲突时保持待归类并失败关闭。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="ruleDialog = false"
    >
      <form id="rule-form" class="cost-subject-page__form" @submit.prevent="saveRule">
        <V2Input v-model="ruleForm.ruleCode" label="规则编码" required />
        <V2Input v-model="ruleForm.mappingVersionId" label="映射版本标识" required />
        <V2Input v-model="ruleForm.sourceType" label="业务来源" required />
        <V2Input v-model="ruleForm.businessCategory" label="业务分类" />
        <V2Input v-model="ruleForm.projectId" label="项目标识" hint="留空表示全局规则" />
        <V2Input v-model="ruleForm.costSubjectId" label="目标末级科目标识" required />
        <V2Input v-model="ruleForm.priority" label="优先级" />
        <V2Input v-model="ruleForm.effectiveFrom" label="生效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="ruleForm.effectiveTo" label="失效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="ruleForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="ruleDialog = false">取消</V2Button>
        <V2Button type="submit" form="rule-form" :loading="saving">创建规则</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="scopeDialog"
      title="维护项目科目范围"
      description="启用、日期和末级科目资格由系统校验。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="scopeDialog = false"
    >
      <form id="scope-form" class="cost-subject-page__form" @submit.prevent="submitScope">
        <V2Input :model-value="scopeProjectId" label="项目标识" disabled />
        <V2Input v-model="scopeForm.costSubjectId" label="末级科目标识" required />
        <V2Select v-model="scopeForm.enabled" :options="enabledOptions" label="状态" />
        <V2Input v-model="scopeForm.effectiveFrom" label="生效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="scopeForm.effectiveTo" label="失效日期" placeholder="YYYY-MM-DD" />
        <V2Input v-model="scopeForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="scopeDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="scope-form" :loading="saving">保存范围</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="transferDialog"
      title="投标成本转入目标成本"
      description="仅中标项目、可编辑目标版本、ACTIVE 映射和已通过审批可执行。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="transferDialog = false"
    >
      <form id="transfer-form" class="cost-subject-page__form" @submit.prevent="submitTransfer">
        <V2Input v-model="transferForm.bidCostId" label="投标成本标识" required />
        <V2Input v-model="transferForm.projectId" label="中标项目标识" required />
        <V2Input v-model="transferForm.targetId" label="目标成本版本标识" required />
        <V2Input v-model="transferForm.mappingVersionId" label="启用映射版本标识" required />
        <V2Input v-model="transferForm.approvalInstanceId" label="审批实例标识" required />
        <V2Input v-model="transferForm.idempotencyKey" label="幂等键" required />
        <V2Input v-model="transferForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="transferDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="transfer-form" :loading="saving">确认转入</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="allocationDialog"
      title="项目财务费用分摊"
      description="仅支持已过账借方凭证明细或已审批费用申请；默认不自动分摊。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="allocationDialog = false"
    >
      <form id="allocation-form" class="cost-subject-page__form" @submit.prevent="submitAllocation">
        <V2Select
          v-model="allocationForm.sourceType"
          :options="sourceTypeOptions"
          label="来源类型"
        />
        <V2Input v-model="allocationForm.sourceId" label="来源标识" required />
        <V2Select
          v-model="allocationForm.allocationBasis"
          :options="allocationBasisOptions"
          label="分摊依据"
        />
        <V2Input
          v-model="allocationForm.accountingPeriod"
          label="会计期间"
          placeholder="YYYY-MM"
          required
        />
        <V2Input v-model="allocationForm.costSubjectId" label="财务费用末级科目标识" required />
        <V2Input v-model="allocationForm.approvalInstanceId" label="审批实例标识" required />
        <V2Input v-model="allocationForm.idempotencyKey" label="幂等键" required />
        <V2Input v-model="allocationForm.remark" label="备注" />
        <fieldset class="cost-subject-page__lines">
          <legend>项目分摊依据</legend>
          <div v-for="(line, index) in allocationForm.lines" :key="index">
            <V2Input v-model="line.projectId" :label="`项目 ${index + 1} 标识`" required />
            <V2Input v-model="line.basisValue" :label="`项目 ${index + 1} 依据值`" required />
            <V2Button
              type="button"
              size="small"
              variant="secondary"
              :disabled="allocationForm.lines.length === 1"
              @click="removeAllocationLine(index)"
            >
              移除
            </V2Button>
          </div>
          <V2Button type="button" size="small" variant="secondary" @click="addAllocationLine">
            增加项目
          </V2Button>
        </fieldset>
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="allocationDialog = false"
          >取消</V2Button
        >
        <V2Button type="submit" form="allocation-form" :loading="saving">确认分摊</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      :open="Boolean(reverseTarget)"
      :title="reverseKind === 'transfer' ? '冲销投标成本转入' : '冲销财务费用分摊'"
      description="冲销生成反向事实，不修改原记录。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="reverseTarget = null"
    >
      <form id="reverse-form" class="cost-subject-page__form" @submit.prevent="submitReverse">
        <V2Input v-model="reverseForm.approvalInstanceId" label="审批实例标识" required />
        <V2Input v-model="reverseForm.idempotencyKey" label="幂等键" required />
        <V2Input v-model="reverseForm.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="reverseTarget = null"
          >取消</V2Button
        >
        <V2Button type="submit" form="reverse-form" :loading="saving">确认冲销</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(subjectStatusTarget)"
      title="更新成本科目状态"
      :description="
        subjectStatusTarget
          ? `确认${subjectStatusTarget.status === 'ENABLE' ? '停用' : '启用'}“${subjectStatusTarget.subjectName}”？存在引用时系统会拒绝停用。`
          : ''
      "
      :danger="subjectStatusTarget?.status === 'ENABLE'"
      :loading="saving"
      @close="subjectStatusTarget = null"
      @confirm="confirmSubjectStatus"
    />

    <V2ConfirmDialog
      :open="Boolean(subjectDeleteTarget)"
      title="删除成本科目"
      :description="
        subjectDeleteTarget
          ? `确认删除“${subjectDeleteTarget.subjectName}”？子科目或任何业务引用存在时系统会拒绝。`
          : ''
      "
      danger
      :loading="saving"
      @close="subjectDeleteTarget = null"
      @confirm="confirmSubjectDelete"
    />
  </V2Stack>
</template>

<style scoped>
.cost-subject-page__hint {
  margin: 0;
  color: var(--v2-color-text-muted);
}

.cost-subject-page__columns {
  display: grid;
  grid-template-columns: minmax(13rem, 0.55fr) minmax(20rem, 0.85fr) minmax(24rem, 1.2fr);
  gap: var(--v2-space-4);
}

.cost-subject-page__columns > section {
  min-width: 0;
}

.cost-subject-page__section-heading,
.cost-subject-page__list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
}

.cost-subject-page__section-heading {
  min-height: 2.5rem;
  margin-bottom: var(--v2-space-3);
}

.cost-subject-page__section-heading h3 {
  margin: 0;
}

.cost-subject-page__section-heading > span,
.cost-subject-page__list-item small {
  color: var(--v2-color-text-muted);
}

.cost-subject-page__list {
  display: grid;
  gap: var(--v2-space-2);
}

.cost-subject-page__list-item {
  width: 100%;
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  background: var(--v2-color-surface);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.cost-subject-page__list-item:hover,
.cost-subject-page__list-item.is-selected {
  border-color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.cost-subject-page__list-item > span {
  display: grid;
  min-width: 0;
  gap: var(--v2-space-1);
}

.cost-subject-page__list-item strong,
.cost-subject-page__list-item small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-subject-page__query,
.cost-subject-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

.cost-subject-page__query--wide {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.cost-subject-page__query--wide form {
  display: flex;
  align-items: end;
  gap: var(--v2-space-3);
}

.cost-subject-page__table-wrap {
  overflow-x: auto;
}

.cost-subject-page__table-wrap table {
  min-width: 46rem;
}

.cost-subject-page__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
  margin: 0;
}

.cost-subject-page__facts div {
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.cost-subject-page__facts dt {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-12);
}

.cost-subject-page__facts dd {
  margin: var(--v2-space-1) 0 0;
  overflow-wrap: anywhere;
}

.cost-subject-page__lines {
  grid-column: 1 / -1;
  display: grid;
  gap: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  padding: var(--v2-space-4);
}

.cost-subject-page__lines > div {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  align-items: end;
  gap: var(--v2-space-3);
}

@media (max-width: 64rem) {
  .cost-subject-page__columns,
  .cost-subject-page__query,
  .cost-subject-page__form,
  .cost-subject-page__facts {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 48rem) {
  .cost-subject-page__query--wide,
  .cost-subject-page__query--wide form,
  .cost-subject-page__lines > div {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
