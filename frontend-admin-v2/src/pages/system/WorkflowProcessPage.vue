<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import {
  createWorkflowTemplateNode,
  deleteWorkflowTemplateNode,
  loadWorkflowTemplate,
  loadWorkflowTemplates,
  reorderWorkflowTemplateNodes,
  updateWorkflowTemplate,
  updateWorkflowTemplateNode,
  type WorkflowTemplateNodeCommand,
  type WorkflowTemplateNodeRecord,
  type WorkflowTemplateRecord,
} from '@/services/workflow-process'
import { isApiClientError } from '@/services/request'
import { loadPositions, type OrgPositionRecord } from '@/services/master-data'
import {
  loadRoles,
  loadUsers,
  type RoleRecord,
  type UserRecord,
} from '@/services/system-management'
import { workflowBusinessTypeLabel } from '@/pages/workbench/model'

type ApproverType = 'USER' | 'ROLE' | 'POSITION' | 'PROJECT_ROLE'

const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const error = ref('')
const templates = ref<WorkflowTemplateRecord[]>([])
const total = ref(0)
const selectedModule = ref('')
const current = ref<WorkflowTemplateRecord | null>(null)
let controller: AbortController | null = null

const filter = reactive({ enabled: '', keyword: '' })
const templateDialog = ref(false)
const nodeDialog = ref(false)
const editingNode = ref<WorkflowTemplateNodeRecord | null>(null)
const deleteTarget = ref<WorkflowTemplateNodeRecord | null>(null)
const approverOptionsLoading = ref(false)
const approverOptionsLoaded = ref(false)
const users = ref<UserRecord[]>([])
const roles = ref<RoleRecord[]>([])
const positions = ref<OrgPositionRecord[]>([])

const templateForm = reactive({
  templateName: '',
  enabled: '1',
  amountMin: '',
  amountMax: '',
  remark: '',
})

const nodeForm = reactive({
  nodeCode: '',
  nodeName: '',
  approveMode: 'SEQUENTIAL',
  approverType: 'USER' as ApproverType,
  approverValue: '',
  allowTransfer: '1',
  allowAddSign: '1',
  timeoutHours: '',
  remark: '',
})

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: '1', label: '启用' },
  { value: '0', label: '停用' },
]
const approveModeOptions = [
  { value: 'SEQUENTIAL', label: '顺序审批' },
  { value: 'COUNTERSIGN', label: '会签' },
  { value: 'OR_SIGN', label: '或签' },
]
const switchOptions = [
  { value: '1', label: '允许' },
  { value: '0', label: '禁止' },
]
const approverTypeOptions = [
  { value: 'USER', label: '指定人员' },
  { value: 'ROLE', label: '系统角色' },
  { value: 'POSITION', label: '组织岗位' },
  { value: 'PROJECT_ROLE', label: '项目角色' },
]
const projectRoleOptions = [
  { value: 'PM', label: '项目经理' },
  { value: 'CM', label: '商务经理' },
  { value: 'CSTM', label: '成本经理' },
  { value: 'FIN', label: '财务负责人' },
  { value: 'MAT', label: '物资负责人' },
  { value: 'OTH', label: '其他项目成员' },
]
const workflowModules: Record<string, { key: string; label: string }> = {
  PROJECT_APPROVAL: { key: 'delivery', label: '项目履约' },
  PROJECT_SCHEDULE: { key: 'delivery', label: '项目履约' },
  PROJECT_PERIOD_PLAN: { key: 'delivery', label: '项目履约' },
  PROJECT_CORRECTIVE_ACTION: { key: 'delivery', label: '项目履约' },
  TECH_ITEM: { key: 'delivery', label: '项目履约' },
  TECHNICAL_SCHEME: { key: 'delivery', label: '项目履约' },
  PROJECT_FINAL_ACCEPTANCE: { key: 'delivery', label: '项目履约' },
  CONTRACT_APPROVAL: { key: 'commercial', label: '商务合约' },
  VAR_ORDER: { key: 'commercial', label: '商务合约' },
  CT_CHANGE: { key: 'commercial', label: '商务合约' },
  COST_TARGET: { key: 'commercial', label: '商务合约' },
  PROJECT_BUDGET: { key: 'commercial', label: '商务合约' },
  PRODUCTION_MEASUREMENT: { key: 'commercial', label: '商务合约' },
  COST_CORRECTIVE_ACTION: { key: 'commercial', label: '商务合约' },
  BID_COST_TARGET_TRANSFER: { key: 'commercial', label: '商务合约' },
  BID_COST_TARGET_TRANSFER_REVERSAL: { key: 'commercial', label: '商务合约' },
  PURCHASE_REQUEST: { key: 'supply', label: '供应链与物资' },
  PURCHASE_ORDER: { key: 'supply', label: '供应链与物资' },
  MATERIAL_RECEIPT: { key: 'supply', label: '供应链与物资' },
  MATERIAL_REQUISITION: { key: 'supply', label: '供应链与物资' },
  SUB_MEASURE: { key: 'subcontract-settlement', label: '分包与结算' },
  SETTLEMENT: { key: 'subcontract-settlement', label: '分包与结算' },
  PAY_REQUEST: { key: 'finance', label: '资金财务' },
  EXPENSE: { key: 'finance', label: '资金财务' },
  CONTRACT_REVENUE: { key: 'finance', label: '资金财务' },
  OWNER_SETTLEMENT: { key: 'finance', label: '资金财务' },
  FINANCE_COST_ALLOCATION: { key: 'finance', label: '资金财务' },
  FINANCE_COST_ALLOCATION_REVERSAL: { key: 'finance', label: '资金财务' },
  COST_SUBJECT_MAPPING: { key: 'master-data', label: '基础资料' },
  DEMO_APPROVAL_SCENARIO: { key: 'system-management', label: '系统管理' },
}
const fallbackModule = { key: 'other', label: '其他' }
const workflowModule = (businessType: string) => workflowModules[businessType] ?? fallbackModule
const approverOptions = computed(() => {
  if (nodeForm.approverType === 'USER') {
    return users.value.map((item) => ({
      value: item.id,
      label: `${item.realName || item.username}（${item.username}）`,
    }))
  }
  if (nodeForm.approverType === 'ROLE') {
    return roles.value.map((item) => ({ value: item.id, label: item.roleName }))
  }
  if (nodeForm.approverType === 'POSITION') {
    return positions.value.map((item) => ({ value: item.id, label: item.positionName }))
  }
  return projectRoleOptions
})
const approverLabel = computed(() => {
  if (nodeForm.approverType === 'USER') return '审批人员'
  if (nodeForm.approverType === 'ROLE') return '审批角色'
  if (nodeForm.approverType === 'POSITION') return '审批岗位'
  return '项目角色'
})
const modules = computed(() => {
  const counts = new Map<string, { label: string; count: number }>()
  for (const template of templates.value) {
    const module = workflowModule(template.businessType)
    const currentCount = counts.get(module.key)?.count ?? 0
    counts.set(module.key, { label: module.label, count: currentCount + 1 })
  }
  return [...counts].map(([key, module]) => ({
    key,
    ...module,
  }))
})
const moduleTemplates = computed(() =>
  templates.value.filter(
    (template) => workflowModule(template.businessType).key === selectedModule.value,
  ),
)

async function loadList(signal?: AbortSignal): Promise<void> {
  const page = await loadWorkflowTemplates(
    {
      pageNo: 1,
      pageSize: 200,
      enabled: filter.enabled,
      keyword: filter.keyword.trim() || undefined,
    },
    signal,
  )
  templates.value = page.records
  total.value = page.total
}

async function refresh(): Promise<void> {
  controller?.abort()
  const currentController = new AbortController()
  controller = currentController
  loading.value = true
  error.value = ''
  try {
    await loadList(currentController.signal)
    if (!modules.value.some((item) => item.key === selectedModule.value)) {
      selectedModule.value = modules.value[0]?.key ?? ''
    }
    const selectedId = moduleTemplates.value.some((item) => item.id === current.value?.id)
      ? current.value!.id
      : moduleTemplates.value[0]?.id
    if (selectedId) await selectTemplate(selectedId)
    else current.value = null
  } catch (value) {
    if (!currentController.signal.aborted) {
      templates.value = []
      total.value = 0
      error.value = messageOf(value)
    }
  } finally {
    if (controller === currentController) loading.value = false
  }
}

async function selectModule(module: string): Promise<void> {
  if (selectedModule.value === module) return
  selectedModule.value = module
  const template = moduleTemplates.value[0]
  if (template) await selectTemplate(template.id)
  else current.value = null
}

async function selectTemplate(id: string): Promise<void> {
  detailLoading.value = true
  try {
    current.value = await loadWorkflowTemplate(id)
  } catch (value) {
    current.value = null
    showToast('error', '流程详情加载失败', messageOf(value))
  } finally {
    detailLoading.value = false
  }
}

function search(): void {
  void refresh()
}

function reset(): void {
  Object.assign(filter, { enabled: '', keyword: '' })
  search()
}

function openTemplateEditor(): void {
  if (!current.value) return
  Object.assign(templateForm, {
    templateName: current.value.templateName,
    enabled: String(current.value.enabled),
    amountMin: current.value.amountMin ?? '',
    amountMax: current.value.amountMax ?? '',
    remark: current.value.remark ?? '',
  })
  templateDialog.value = true
}

function closeTemplateEditor(): void {
  templateDialog.value = false
}

async function saveTemplate(): Promise<void> {
  if (!current.value || !templateForm.templateName.trim()) {
    showToast('warning', '信息不完整', '流程名称不能为空。')
    return
  }
  saving.value = true
  try {
    await updateWorkflowTemplate(current.value.id, {
      templateName: templateForm.templateName.trim(),
      enabled: Number(templateForm.enabled),
      amountMin: nullableDecimal(templateForm.amountMin),
      amountMax: nullableDecimal(templateForm.amountMax),
      remark: templateForm.remark.trim(),
    })
    templateDialog.value = false
    await selectTemplate(current.value.id)
    await loadList()
    showToast('success', '流程已保存', '模板与列表已刷新。')
  } catch (value) {
    showToast('error', '流程保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openNodeEditor(node?: WorkflowTemplateNodeRecord): void {
  editingNode.value = node ?? null
  const approver = parseApproverConfig(node?.approverConfig)
  Object.assign(nodeForm, {
    nodeCode: node?.nodeCode ?? '',
    nodeName: node?.nodeName ?? '',
    approveMode: node?.approveMode ?? 'SEQUENTIAL',
    approverType: approver.type,
    approverValue: approver.value,
    allowTransfer: String(node?.allowTransfer ?? 1),
    allowAddSign: String(node?.allowAddSign ?? 1),
    timeoutHours: node?.timeoutHours == null ? '' : String(node.timeoutHours),
    remark: node?.remark ?? '',
  })
  nodeDialog.value = true
  void loadApproverOptions()
}

function closeNodeEditor(): void {
  nodeDialog.value = false
}

function openDeleteNode(node: WorkflowTemplateNodeRecord): void {
  deleteTarget.value = node
}

function closeDeleteNode(): void {
  deleteTarget.value = null
}

async function saveNode(): Promise<void> {
  if (!current.value || !nodeForm.nodeName.trim() || !nodeForm.approverValue) {
    showToast('warning', '节点配置无效', '节点名称和审批人不能为空。')
    return
  }
  const command: WorkflowTemplateNodeCommand = {
    nodeCode: nodeForm.nodeCode.trim() || undefined,
    nodeName: nodeForm.nodeName.trim(),
    nodeType: 'APPROVAL',
    approveMode: nodeForm.approveMode as WorkflowTemplateNodeCommand['approveMode'],
    approverConfig: serializeApproverConfig(),
    allowTransfer: Number(nodeForm.allowTransfer),
    allowAddSign: Number(nodeForm.allowAddSign),
    timeoutHours: optionalPositiveInteger(nodeForm.timeoutHours),
    remark: nodeForm.remark.trim(),
  }
  saving.value = true
  try {
    if (editingNode.value) {
      await updateWorkflowTemplateNode(current.value.id, editingNode.value.id, command)
    } else {
      await createWorkflowTemplateNode(current.value.id, command)
    }
    nodeDialog.value = false
    await selectTemplate(current.value.id)
    await loadList()
    showToast('success', editingNode.value ? '节点已更新' : '节点已新增', '节点与列表已刷新。')
  } catch (value) {
    showToast('error', '节点保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function moveNode(node: WorkflowTemplateNodeRecord, offset: -1 | 1): Promise<void> {
  if (!current.value?.nodes) return
  const ids = current.value.nodes.map((item) => item.id)
  const index = ids.indexOf(node.id)
  const target = index + offset
  if (index < 0 || target < 0 || target >= ids.length) return
  ;[ids[index], ids[target]] = [ids[target]!, ids[index]!]
  saving.value = true
  try {
    await reorderWorkflowTemplateNodes(current.value.id, ids)
    await selectTemplate(current.value.id)
    showToast('success', '节点顺序已更新', '最新排序已载入。')
  } catch (value) {
    showToast('error', '节点排序失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function deleteNode(): Promise<void> {
  if (!current.value || !deleteTarget.value) return
  saving.value = true
  try {
    await deleteWorkflowTemplateNode(current.value.id, deleteTarget.value.id)
    deleteTarget.value = null
    await selectTemplate(current.value.id)
    await loadList()
    showToast('success', '节点已删除', '最新节点已载入。')
  } catch (value) {
    showToast('error', '节点删除失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function nullableDecimal(value: string): string | null {
  const normalized = value.trim()
  if (!normalized) return null
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) throw new Error('金额必须是非负数')
  return normalized
}

function optionalPositiveInteger(value: string): number | undefined {
  const normalized = value.trim()
  if (!normalized) return undefined
  if (!/^\d+$/.test(normalized)) throw new Error('超时时间必须是非负整数')
  return Number(normalized)
}

function parseApproverConfig(value?: string): { type: ApproverType; value: string } {
  try {
    const config = JSON.parse(value ?? '{}') as Record<string, unknown>
    const type = config.type as ApproverType
    if (type === 'USER') return { type, value: String(config.userId ?? '') }
    if (type === 'ROLE') {
      return {
        type,
        value:
          config.roleId == null ? `code:${String(config.roleCode ?? '')}` : String(config.roleId),
      }
    }
    if (type === 'POSITION') return { type, value: String(config.positionId ?? '') }
    if (type === 'PROJECT_ROLE') return { type, value: String(config.roleCode ?? '') }
  } catch {
    // Invalid legacy value becomes an explicit required selection.
  }
  return { type: 'USER', value: '' }
}

function serializeApproverConfig(): string {
  const type = nodeForm.approverType
  if (type === 'USER') return JSON.stringify({ type, userId: nodeForm.approverValue })
  if (type === 'ROLE') return JSON.stringify({ type, roleId: nodeForm.approverValue })
  if (type === 'POSITION') return JSON.stringify({ type, positionId: nodeForm.approverValue })
  return JSON.stringify({ type, roleCode: nodeForm.approverValue })
}

function changeApproverType(value: string): void {
  nodeForm.approverType = value as ApproverType
  nodeForm.approverValue = ''
}

async function loadApproverOptions(): Promise<void> {
  if (approverOptionsLoaded.value || approverOptionsLoading.value) {
    normalizeRoleSelection()
    return
  }
  approverOptionsLoading.value = true
  try {
    const [userPage, roleRows, positionPage] = await Promise.all([
      loadUsers({ pageNo: 1, pageSize: 1000 }),
      loadRoles(),
      loadPositions({ pageNo: 1, pageSize: 1000 }),
    ])
    users.value = userPage.records
    roles.value = roleRows.filter((item) => item.status === 'ENABLE')
    positions.value = positionPage.records.filter((item) => item.status === 'ENABLE')
    approverOptionsLoaded.value = true
    normalizeRoleSelection()
  } catch (value) {
    showToast('error', '审批人选项加载失败', messageOf(value))
  } finally {
    approverOptionsLoading.value = false
  }
}

function normalizeRoleSelection(): void {
  if (nodeForm.approverType !== 'ROLE' || !nodeForm.approverValue.startsWith('code:')) return
  const roleCode = nodeForm.approverValue.slice(5)
  nodeForm.approverValue = roles.value.find((item) => item.roleCode === roleCode)?.id ?? ''
}

function messageOf(value: unknown): string {
  return isApiClientError(value) || value instanceof Error ? value.message : '请求失败，请稍后重试'
}

function statusLabel(enabled: number): string {
  return enabled === 1 ? '启用' : '停用'
}

function modeLabel(mode: string): string {
  return approveModeOptions.find((item) => item.value === mode)?.label ?? mode
}

function amountRange(template: WorkflowTemplateRecord): string {
  return `${template.amountMin ?? '不限'} ～ ${template.amountMax ?? '不限'}`
}

onMounted(() => void refresh())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="workflow-process-page" :gap="4">
    <V2Card title="审批流程配置" :heading-level="1">
      <template #actions>
        <div class="workflow-process-page__filters">
          <V2Input
            v-model="filter.keyword"
            label="关键词"
            placeholder="流程名称或编码"
            hide-label
          />
          <V2Select
            v-model="filter.enabled"
            label="状态"
            :options="statusOptions"
            allow-empty
            placeholder="全部状态"
            hide-label
            @update:model-value="search"
          />
          <V2Button size="small" @click="search">查询</V2Button>
          <V2Button size="small" variant="secondary" @click="reset">重置</V2Button>
        </div>
        <V2Button size="small" variant="secondary" @click="refresh">刷新</V2Button>
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取审批流程" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="审批流程加载失败" :description="error">
      <template #actions><V2Button @click="refresh">重试</V2Button></template>
    </V2PageState>

    <V2Card v-else>
      <V2PageState
        v-if="!templates.length"
        kind="empty"
        title="暂无流程模板"
        description="当前筛选条件没有流程模板。"
      />
      <div v-else class="workflow-process-page__columns">
        <section aria-labelledby="workflow-categories-title">
          <div class="workflow-process-page__section-heading">
            <h3 id="workflow-categories-title">1. 业务模块</h3>
            <span>共 {{ modules.length }} 个</span>
          </div>
          <div class="workflow-process-page__list">
            <button
              v-for="module in modules"
              :key="module.key"
              type="button"
              class="workflow-process-page__list-item"
              :class="{ 'is-selected': selectedModule === module.key }"
              :aria-pressed="selectedModule === module.key"
              @click="selectModule(module.key)"
            >
              <strong>{{ module.label }}</strong>
              <V2Badge tone="neutral">{{ module.count }}</V2Badge>
            </button>
          </div>
        </section>

        <section aria-labelledby="workflow-templates-title">
          <div class="workflow-process-page__section-heading">
            <h3 id="workflow-templates-title">2. 流程模板</h3>
            <span>共 {{ moduleTemplates.length }} 条</span>
          </div>
          <div class="workflow-process-page__list">
            <button
              v-for="template in moduleTemplates"
              :key="template.id"
              type="button"
              class="workflow-process-page__list-item"
              :class="{ 'is-selected': current?.id === template.id }"
              :aria-pressed="current?.id === template.id"
              @click="selectTemplate(template.id)"
            >
              <span>
                <strong>{{ template.templateName }}</strong>
                <small>{{ template.templateCode }}</small>
              </span>
              <V2Badge :tone="template.enabled === 1 ? 'success' : 'neutral'">
                {{ statusLabel(template.enabled) }}
              </V2Badge>
            </button>
          </div>
        </section>

        <section aria-labelledby="workflow-configuration-title">
          <div class="workflow-process-page__section-heading">
            <h3 id="workflow-configuration-title">3. 流程配置</h3>
          </div>
          <V2PageState
            v-if="detailLoading"
            kind="loading"
            title="正在读取模板详情"
            description="请稍候。"
          />
          <V2PageState
            v-else-if="!current"
            kind="empty"
            title="请选择流程模板"
            description="选择模板后可维护设置和节点。"
          />
          <div v-else class="workflow-process-page__configuration">
            <section aria-labelledby="workflow-settings-title">
              <div class="workflow-process-page__section-heading">
                <h4 id="workflow-settings-title">基本设置</h4>
                <V2Button
                  type="button"
                  size="small"
                  variant="secondary"
                  @click="openTemplateEditor"
                >
                  编辑模板
                </V2Button>
              </div>
              <dl class="workflow-process-page__facts">
                <div>
                  <dt>模板编码</dt>
                  <dd>{{ current.templateCode }}</dd>
                </div>
                <div>
                  <dt>业务类型</dt>
                  <dd>{{ workflowBusinessTypeLabel(current.businessType) }}</dd>
                </div>
                <div>
                  <dt>金额范围</dt>
                  <dd>{{ amountRange(current) }}</dd>
                </div>
                <div>
                  <dt>节点数量</dt>
                  <dd>{{ current.nodes?.length ?? 0 }}</dd>
                </div>
                <div>
                  <dt>状态</dt>
                  <dd>{{ statusLabel(current.enabled) }}</dd>
                </div>
                <div>
                  <dt>备注</dt>
                  <dd>{{ current.remark || '-' }}</dd>
                </div>
              </dl>
            </section>

            <section aria-labelledby="workflow-nodes-title">
              <div class="workflow-process-page__section-heading">
                <h4 id="workflow-nodes-title">审批节点</h4>
                <V2Button type="button" size="small" @click="openNodeEditor()">新增节点</V2Button>
              </div>
              <V2PageState
                v-if="!current.nodes?.length"
                kind="empty"
                title="暂无审批节点"
                description="新增节点后可配置审批人与顺序。"
              />
              <div
                v-else
                class="workflow-process-page__table-wrap"
                role="region"
                aria-label="审批节点表格"
                tabindex="0"
              >
                <table>
                  <thead>
                    <tr>
                      <th>顺序</th>
                      <th>节点名称</th>
                      <th>模式</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(node, index) in current.nodes" :key="node.id">
                      <td>{{ node.nodeOrder }}</td>
                      <th scope="row">{{ node.nodeName }}</th>
                      <td>{{ modeLabel(node.approveMode) }}</td>
                      <td>
                        <div class="workflow-process-page__actions">
                          <V2Button
                            type="button"
                            size="small"
                            variant="ghost"
                            :disabled="saving || index === 0"
                            @click="moveNode(node, -1)"
                            >上移</V2Button
                          >
                          <V2Button
                            type="button"
                            size="small"
                            variant="ghost"
                            :disabled="saving || index === (current.nodes?.length ?? 0) - 1"
                            @click="moveNode(node, 1)"
                            >下移</V2Button
                          >
                          <V2Button
                            type="button"
                            size="small"
                            variant="ghost"
                            @click="openNodeEditor(node)"
                            >编辑</V2Button
                          >
                          <V2Button
                            type="button"
                            size="small"
                            variant="danger"
                            @click="openDeleteNode(node)"
                            >删除</V2Button
                          >
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </section>
      </div>
    </V2Card>

    <V2Dialog
      v-model:open="templateDialog"
      title="编辑审批流程"
      description="保存后重新读取模板和列表；运行中实例不受影响。"
      :close-disabled="saving"
      :close-on-backdrop="!saving"
      panel-class="v2-dialog-standard"
      @close="closeTemplateEditor"
    >
      <div class="workflow-process-page__form-grid">
        <V2Input v-model="templateForm.templateName" label="流程名称" required />
        <V2Select v-model="templateForm.enabled" label="状态" :options="statusOptions.slice(1)" />
        <V2Input v-model="templateForm.amountMin" label="金额下限" placeholder="不限" />
        <V2Input v-model="templateForm.amountMax" label="金额上限" placeholder="不限" />
        <V2Input v-model="templateForm.remark" label="备注" />
      </div>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="saving" @click="closeTemplateEditor">
          取消
        </V2Button>
        <V2Button type="button" :loading="saving" @click="saveTemplate">保存流程</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="nodeDialog"
      :title="editingNode ? '编辑审批节点' : '新增审批节点'"
      description="选择审批人员、系统角色、组织岗位或项目角色。"
      :close-disabled="saving"
      :close-on-backdrop="!saving"
      panel-class="v2-dialog-standard"
      @close="closeNodeEditor"
    >
      <div class="workflow-process-page__form-grid">
        <V2Input v-model="nodeForm.nodeCode" label="节点编码" placeholder="留空自动生成" />
        <V2Input v-model="nodeForm.nodeName" label="节点名称" required />
        <V2Select v-model="nodeForm.approveMode" label="审批模式" :options="approveModeOptions" />
        <V2Select
          :model-value="nodeForm.approverType"
          label="审批人类型"
          :options="approverTypeOptions"
          required
          @update:model-value="changeApproverType"
        />
        <V2Select
          v-model="nodeForm.approverValue"
          :label="approverLabel"
          :options="approverOptions"
          :disabled="approverOptionsLoading"
          :placeholder="approverOptionsLoading ? '正在加载' : '请选择'"
          required
        />
        <V2Select v-model="nodeForm.allowTransfer" label="允许转办" :options="switchOptions" />
        <V2Select v-model="nodeForm.allowAddSign" label="允许加签" :options="switchOptions" />
        <V2Input v-model="nodeForm.timeoutHours" label="超时小时" placeholder="不限" />
        <V2Input v-model="nodeForm.remark" label="备注" />
      </div>
      <template #footer>
        <V2Button type="button" variant="secondary" :disabled="saving" @click="closeNodeEditor">
          取消
        </V2Button>
        <V2Button type="button" :loading="saving" @click="saveNode">保存节点</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除审批节点"
      :description="deleteTarget ? `确定删除“${deleteTarget.nodeName}”吗？至少保留一个节点。` : ''"
      confirm-text="删除节点"
      danger
      :loading="saving"
      @close="closeDeleteNode"
      @confirm="deleteNode"
    />
  </V2Stack>
</template>

<style scoped>
.workflow-process-page__form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

.workflow-process-page__columns {
  display: grid;
  grid-template-columns: minmax(13rem, 0.6fr) minmax(18rem, 0.85fr) minmax(34rem, 1.55fr);
  gap: var(--v2-space-4);
}

.workflow-process-page__columns > section {
  min-width: 0;
}

.workflow-process-page__section-heading,
.workflow-process-page__list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
}

.workflow-process-page__section-heading {
  min-height: 2.5rem;
  margin-bottom: var(--v2-space-3);
}

.workflow-process-page__section-heading h3 {
  margin: 0;
}

.workflow-process-page__section-heading h4 {
  margin: 0;
}

.workflow-process-page__configuration {
  display: grid;
  gap: var(--v2-space-5);
}

.workflow-process-page__configuration > section + section {
  padding-top: var(--v2-space-4);
  border-top: var(--v2-border-width) solid var(--v2-color-border);
}

.workflow-process-page__list {
  display: grid;
  gap: var(--v2-space-2);
}

.workflow-process-page__list-item {
  width: 100%;
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  background: var(--v2-color-surface);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.workflow-process-page__list-item:hover,
.workflow-process-page__list-item.is-selected {
  border-color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.workflow-process-page__list-item span,
.workflow-process-page__facts div {
  display: grid;
  gap: var(--v2-space-1);
}

.workflow-process-page__list-item small,
.workflow-process-page__section-heading > span {
  color: var(--v2-color-text-muted);
}

.workflow-process-page__facts {
  display: grid;
  gap: var(--v2-space-2);
  margin: 0;
}

.workflow-process-page__facts div {
  grid-template-columns: minmax(5rem, auto) minmax(0, 1fr);
  padding-bottom: var(--v2-space-2);
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
}

.workflow-process-page__facts dt,
.workflow-process-page__facts dd {
  margin: 0;
}

.workflow-process-page__facts dt {
  color: var(--v2-color-text-muted);
}

.workflow-process-page__filters,
.workflow-process-page__actions {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
}

.workflow-process-page__filters {
  flex-wrap: wrap;
}

.workflow-process-page__table-wrap {
  overflow-x: auto;
}

.workflow-process-page table {
  width: 100%;
  border-collapse: collapse;
}

.workflow-process-page th,
.workflow-process-page td {
  padding: var(--v2-space-3);
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
  text-align: left;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .workflow-process-page__form-grid,
  .workflow-process-page__columns {
    grid-template-columns: 1fr;
  }
}
</style>
