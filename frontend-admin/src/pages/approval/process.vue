<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import {
  createWorkflowTemplateNode,
  deleteWorkflowTemplateNode,
  getWorkflowTemplateDetail,
  getWorkflowTemplates,
  reorderWorkflowTemplateNodes,
  updateWorkflowTemplate,
  updateWorkflowTemplateNode,
  type WfTemplateNodeVO,
  type WfTemplateVO,
  type WorkflowTemplateNodeParams,
} from '@/api/modules/workflow'
import type { PageResult } from '@/types/api'

interface TemplateFilter {
  businessType: string
  enabled?: number
  keyword: string
}

const loading = ref(false)
const savingTemplate = ref(false)
const savingNode = ref(false)
const drawerVisible = ref(false)
const nodeModalVisible = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const templates = ref<WfTemplateVO[]>([])
const currentTemplate = ref<WfTemplateVO | null>(null)
const editingNode = ref<WfTemplateNodeVO | null>(null)

const filter = reactive<TemplateFilter>({
  businessType: '',
  enabled: undefined,
  keyword: '',
})

const templateForm = reactive({
  templateName: '',
  enabled: 1,
  amountMin: '',
  amountMax: '',
  conditionRule: '',
  formSchema: '',
  remark: '',
})

const nodeForm = reactive<WorkflowTemplateNodeParams>({
  nodeCode: '',
  nodeName: '',
  nodeOrder: undefined,
  nodeType: 'APPROVAL',
  approveMode: 'SEQUENTIAL',
  approverConfig: '{"type":"USER","userId":1}',
  passRuleJson: '',
  rejectRuleJson: '',
  conditionRule: '',
  nodeConfig: '',
  allowTransfer: 1,
  allowAddSign: 1,
  timeoutHours: undefined,
  remark: '',
})

const businessTypeMap: Record<string, string> = {
  CONTRACT_APPROVAL: '合同审批',
  PURCHASE_ORDER: '采购订单',
  PURCHASE_REQUEST: '采购申请',
  MATERIAL_RECEIPT: '材料验收',
  SUB_MEASURE: '分包计量',
  PAY_REQUEST: '付款申请',
  VAR_ORDER: '变更签证',
  CT_CHANGE: '合同变更',
  SETTLEMENT: '结算审批',
  COST_TARGET: '目标成本',
}

const businessTypeOptions = Object.entries(businessTypeMap).map(([value, label]) => ({
  value,
  label,
}))

const enabledOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
]

const approveModeOptions = [
  { value: 'SEQUENTIAL', label: '顺序审批' },
  { value: 'COUNTERSIGN', label: '会签' },
  { value: 'OR_SIGN', label: '或签' },
]

const templateColumns = [
  { title: '流程模板', dataIndex: 'templateName', key: 'templateName', ellipsis: true },
  { title: '模板编码', dataIndex: 'templateCode', key: 'templateCode', width: 210 },
  { title: '业务类型', dataIndex: 'businessType', key: 'businessType', width: 120 },
  { title: '节点数', dataIndex: 'nodeCount', key: 'nodeCount', width: 90 },
  { title: '金额范围', key: 'amountRange', width: 180 },
  { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170 },
  { title: '操作', key: 'action', width: 100 },
]

const nodeColumns = [
  { title: '节点顺序', dataIndex: 'nodeOrder', key: 'nodeOrder', width: 90 },
  { title: '节点编码', dataIndex: 'nodeCode', key: 'nodeCode', width: 110 },
  { title: '节点名称', dataIndex: 'nodeName', key: 'nodeName', ellipsis: true },
  { title: '审批模式', dataIndex: 'approveMode', key: 'approveMode', width: 110 },
  { title: '审批人配置', dataIndex: 'approverConfig', key: 'approverConfig', ellipsis: true },
  { title: '转办', dataIndex: 'allowTransfer', key: 'allowTransfer', width: 80 },
  { title: '加签', dataIndex: 'allowAddSign', key: 'allowAddSign', width: 80 },
  { title: '操作', key: 'action', width: 190 },
]

const nodes = computed(() => currentTemplate.value?.nodes ?? [])

async function fetchTemplates() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNo.value,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      businessType: filter.businessType || undefined,
      enabled: filter.enabled,
      keyword: filter.keyword || undefined,
    }
    const res: PageResult<WfTemplateVO> = await getWorkflowTemplates(params)
    templates.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    templates.value = []
    total.value = 0
    message.error('加载审批流程模板失败')
  } finally {
    loading.value = false
  }
}

async function loadTemplateDetail(templateId: string) {
  const detail = await getWorkflowTemplateDetail(templateId)
  currentTemplate.value = detail
  templateForm.templateName = detail.templateName
  templateForm.enabled = detail.enabled
  templateForm.amountMin = detail.amountMin ?? ''
  templateForm.amountMax = detail.amountMax ?? ''
  templateForm.conditionRule = detail.conditionRule ?? ''
  templateForm.formSchema = detail.formSchema ?? ''
  templateForm.remark = detail.remark ?? ''
}

async function handleEdit(record: WfTemplateVO) {
  try {
    await loadTemplateDetail(record.id)
    drawerVisible.value = true
  } catch (e: unknown) {
    console.error(e)
    message.error('加载流程详情失败')
  }
}

async function handleSaveTemplate() {
  if (!currentTemplate.value) return
  if (!templateForm.templateName.trim()) {
    message.warning('流程名称不能为空')
    return
  }
  const amountMin = toOptionalNumber(templateForm.amountMin)
  const amountMax = toOptionalNumber(templateForm.amountMax)
  if (amountMin !== undefined && amountMax !== undefined && amountMin > amountMax) {
    message.warning('金额下限不能大于金额上限')
    return
  }

  savingTemplate.value = true
  try {
    await updateWorkflowTemplate(currentTemplate.value.id, {
      templateName: templateForm.templateName.trim(),
      enabled: templateForm.enabled,
      amountMin,
      amountMax,
      conditionRule: emptyToUndefined(templateForm.conditionRule),
      formSchema: emptyToUndefined(templateForm.formSchema),
      remark: emptyToUndefined(templateForm.remark),
    })
    await loadTemplateDetail(currentTemplate.value.id)
    await fetchTemplates()
    message.success('审批流程已保存')
  } catch (e: unknown) {
    console.error(e)
    message.error('保存审批流程失败')
  } finally {
    savingTemplate.value = false
  }
}

function handleAddNode() {
  editingNode.value = null
  resetNodeForm({
    nodeOrder: nodes.value.length + 1,
  })
  nodeModalVisible.value = true
}

function handleEditNode(node: WfTemplateNodeVO) {
  editingNode.value = node
  resetNodeForm({
    nodeCode: node.nodeCode,
    nodeName: node.nodeName,
    nodeOrder: node.nodeOrder,
    nodeType: node.nodeType,
    approveMode: node.approveMode,
    approverConfig: node.approverConfig,
    passRuleJson: node.passRuleJson ?? '',
    rejectRuleJson: node.rejectRuleJson ?? '',
    conditionRule: node.conditionRule ?? '',
    nodeConfig: node.nodeConfig ?? '',
    allowTransfer: node.allowTransfer ?? 1,
    allowAddSign: node.allowAddSign ?? 1,
    timeoutHours: node.timeoutHours,
    remark: node.remark ?? '',
  })
  nodeModalVisible.value = true
}

async function handleSaveNode() {
  if (!currentTemplate.value) return
  const payload = buildNodePayload()
  if (!payload) return

  savingNode.value = true
  try {
    if (editingNode.value) {
      await updateWorkflowTemplateNode(currentTemplate.value.id, editingNode.value.id, payload)
      message.success('审批节点已更新')
    } else {
      await createWorkflowTemplateNode(currentTemplate.value.id, payload)
      message.success('审批节点已新增')
    }
    nodeModalVisible.value = false
    await loadTemplateDetail(currentTemplate.value.id)
    await fetchTemplates()
  } catch (e: unknown) {
    console.error(e)
    message.error('保存审批节点失败')
  } finally {
    savingNode.value = false
  }
}

function handleDeleteNode(node: WfTemplateNodeVO) {
  if (!currentTemplate.value) return
  Modal.confirm({
    title: '删除节点',
    content: `确定删除节点“${node.nodeName}”吗？删除后仅影响新发起审批。`,
    okText: '删除节点',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      if (!currentTemplate.value) return
      try {
        await deleteWorkflowTemplateNode(currentTemplate.value.id, node.id)
        await loadTemplateDetail(currentTemplate.value.id)
        await fetchTemplates()
        message.success('审批节点已删除')
      } catch (e: unknown) {
        console.error(e)
        message.error('删除审批节点失败')
      }
    },
  })
}

async function moveNode(node: WfTemplateNodeVO, direction: -1 | 1) {
  if (!currentTemplate.value) return
  const nodeIds = nodes.value.map((item) => item.id)
  const index = nodeIds.indexOf(node.id)
  const targetIndex = index + direction
  if (index < 0 || targetIndex < 0 || targetIndex >= nodeIds.length) return
  const nextIds = [...nodeIds]
  const [moved] = nextIds.splice(index, 1)
  nextIds.splice(targetIndex, 0, moved)
  try {
    await reorderWorkflowTemplateNodes(currentTemplate.value.id, nextIds)
    await loadTemplateDetail(currentTemplate.value.id)
    message.success('节点顺序已调整')
  } catch (e: unknown) {
    console.error(e)
    message.error('调整节点顺序失败')
  }
}

function buildNodePayload(): WorkflowTemplateNodeParams | null {
  if (!nodeForm.nodeName?.trim()) {
    message.warning('节点名称不能为空')
    return null
  }
  if (!nodeForm.approverConfig?.trim()) {
    message.warning('审批人配置不能为空')
    return null
  }
  if (!isJson(nodeForm.approverConfig)) {
    message.warning('审批人配置必须是合法 JSON')
    return null
  }
  for (const key of ['passRuleJson', 'rejectRuleJson', 'conditionRule', 'nodeConfig'] as const) {
    const value = nodeForm[key]
    if (value && !isJson(value)) {
      message.warning(`${key} 必须是合法 JSON`)
      return null
    }
  }
  return {
    nodeCode: emptyToUndefined(nodeForm.nodeCode),
    nodeName: nodeForm.nodeName.trim(),
    nodeOrder: nodeForm.nodeOrder,
    nodeType: nodeForm.nodeType || 'APPROVAL',
    approveMode: nodeForm.approveMode || 'SEQUENTIAL',
    approverConfig: nodeForm.approverConfig.trim(),
    passRuleJson: emptyToUndefined(nodeForm.passRuleJson),
    rejectRuleJson: emptyToUndefined(nodeForm.rejectRuleJson),
    conditionRule: emptyToUndefined(nodeForm.conditionRule),
    nodeConfig: emptyToUndefined(nodeForm.nodeConfig),
    allowTransfer: nodeForm.allowTransfer,
    allowAddSign: nodeForm.allowAddSign,
    timeoutHours: nodeForm.timeoutHours,
    remark: emptyToUndefined(nodeForm.remark),
  }
}

function resetNodeForm(values: Partial<WorkflowTemplateNodeParams> = {}) {
  nodeForm.nodeCode = values.nodeCode ?? ''
  nodeForm.nodeName = values.nodeName ?? ''
  nodeForm.nodeOrder = values.nodeOrder
  nodeForm.nodeType = values.nodeType ?? 'APPROVAL'
  nodeForm.approveMode = values.approveMode ?? 'SEQUENTIAL'
  nodeForm.approverConfig = values.approverConfig ?? '{"type":"USER","userId":1}'
  nodeForm.passRuleJson = values.passRuleJson ?? ''
  nodeForm.rejectRuleJson = values.rejectRuleJson ?? ''
  nodeForm.conditionRule = values.conditionRule ?? ''
  nodeForm.nodeConfig = values.nodeConfig ?? ''
  nodeForm.allowTransfer = values.allowTransfer ?? 1
  nodeForm.allowAddSign = values.allowAddSign ?? 1
  nodeForm.timeoutHours = values.timeoutHours
  nodeForm.remark = values.remark ?? ''
}

function handleSearch() {
  pageNo.value = 1
  fetchTemplates()
}

function handleReset() {
  filter.businessType = ''
  filter.enabled = undefined
  filter.keyword = ''
  handleSearch()
}

function handleTableChange(pagination: { current?: number; pageSize?: number }) {
  pageNo.value = pagination.current ?? 1
  pageSize.value = pagination.pageSize ?? 20
  fetchTemplates()
}

function businessTypeLabel(type: string) {
  return businessTypeMap[type] ?? type
}

function approveModeLabel(mode: string) {
  return approveModeOptions.find((option) => option.value === mode)?.label ?? mode
}

function formatAmountRange(record: WfTemplateVO) {
  const min = record.amountMin ?? '不限'
  const max = record.amountMax ?? '不限'
  return `${min} ~ ${max}`
}

function emptyToUndefined(value?: string) {
  return value == null || value.trim() === '' ? undefined : value.trim()
}

function toOptionalNumber(value: string) {
  if (!value || String(value).trim() === '') return undefined
  return Number(value)
}

function isJson(value: string) {
  try {
    JSON.parse(value)
    return true
  } catch {
    return false
  }
}

onMounted(fetchTemplates)
</script>

<template>
  <div class="project-target-redesign lg-list-page lg-page app-page approval-process-page">
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb">
        <a-breadcrumb-item>审批管理</a-breadcrumb-item>
        <a-breadcrumb-item>审批流程管理</a-breadcrumb-item>
      </a-breadcrumb>
      <div class="pt-head-actions">
        <a-button @click="fetchTemplates">刷新</a-button>
      </div>
    </div>

    <div class="lg-search-bar process-search-bar">
      <a-select
        v-model:value="filter.businessType"
        :options="businessTypeOptions"
        allow-clear
        placeholder="全部业务"
        style="width: 180px"
      />
      <a-select
        v-model:value="filter.enabled"
        :options="enabledOptions"
        allow-clear
        placeholder="全部状态"
        style="width: 140px"
      />
      <a-input
        v-model:value="filter.keyword"
        allow-clear
        placeholder="搜索流程模板名称/编码"
        style="flex: 1; min-width: 220px"
        @press-enter="handleSearch"
      />
      <div class="lg-search-actions">
        <a-button type="primary" @click="handleSearch">查询</a-button>
        <a-button @click="handleReset">重置</a-button>
      </div>
    </div>

    <div class="lg-list-table-panel process-table-panel">
      <div class="lg-table-wrap">
        <a-table
          :columns="templateColumns"
          :data-source="templates"
          :loading="loading"
          row-key="id"
          size="small"
          :pagination="{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (value: number) => `共 ${value} 条`,
          }"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'templateName'">
              <a @click="handleEdit(record)">{{ record.templateName }}</a>
            </template>
            <template v-else-if="column.key === 'businessType'">
              <a-tag>{{ businessTypeLabel(record.businessType) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'amountRange'">
              {{ formatAmountRange(record) }}
            </template>
            <template v-else-if="column.key === 'enabled'">
              <a-tag :color="record.enabled === 1 ? 'success' : 'default'">
                {{ record.enabled === 1 ? '启用' : '停用' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <a-drawer
      v-model:open="drawerVisible"
      width="920"
      title="编辑审批流程"
      :destroy-on-close="false"
    >
      <template v-if="currentTemplate">
        <div class="process-section">
          <div class="process-section-head">
            <div>
              <h3>流程模板</h3>
              <p>模板变更仅影响新发起审批，运行中实例继续使用原节点快照。</p>
            </div>
            <a-space>
              <a-tag>{{ businessTypeLabel(currentTemplate.businessType) }}</a-tag>
              <a-tag :color="currentTemplate.enabled === 1 ? 'success' : 'default'">
                {{ currentTemplate.enabled === 1 ? '启用' : '停用' }}
              </a-tag>
            </a-space>
          </div>

          <a-form layout="vertical">
            <div class="process-form-grid">
              <a-form-item label="流程名称" required>
                <a-input v-model:value="templateForm.templateName" placeholder="流程名称" />
              </a-form-item>
              <a-form-item label="启用状态">
                <a-select v-model:value="templateForm.enabled" :options="enabledOptions" />
              </a-form-item>
              <a-form-item label="金额下限">
                <a-input v-model:value="templateForm.amountMin" placeholder="不限" />
              </a-form-item>
              <a-form-item label="金额上限">
                <a-input v-model:value="templateForm.amountMax" placeholder="不限" />
              </a-form-item>
            </div>
            <a-form-item label="匹配条件 JSON">
              <a-textarea
                v-model:value="templateForm.conditionRule"
                :rows="2"
                placeholder="可为空"
              />
            </a-form-item>
            <a-form-item label="备注">
              <a-textarea v-model:value="templateForm.remark" :rows="2" placeholder="备注" />
            </a-form-item>
            <a-space>
              <a-button type="primary" :loading="savingTemplate" @click="handleSaveTemplate">
                保存流程模板
              </a-button>
            </a-space>
          </a-form>
        </div>

        <div class="process-section">
          <div class="process-section-head">
            <div>
              <h3>审批节点</h3>
              <p>支持新增节点、删除节点、编辑审批人配置与调整节点顺序。</p>
            </div>
            <a-button type="primary" @click="handleAddNode">新增节点</a-button>
          </div>

          <a-table
            :columns="nodeColumns"
            :data-source="nodes"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'approveMode'">
                {{ approveModeLabel(record.approveMode) }}
              </template>
              <template v-else-if="column.key === 'allowTransfer'">
                <a-tag :color="record.allowTransfer === 1 ? 'success' : 'default'">
                  {{ record.allowTransfer === 1 ? '允许' : '禁止' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'allowAddSign'">
                <a-tag :color="record.allowAddSign === 1 ? 'success' : 'default'">
                  {{ record.allowAddSign === 1 ? '允许' : '禁止' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space>
                  <a-button
                    type="link"
                    size="small"
                    :disabled="index === 0"
                    @click="moveNode(record, -1)"
                  >
                    上移
                  </a-button>
                  <a-button
                    type="link"
                    size="small"
                    :disabled="index === nodes.length - 1"
                    @click="moveNode(record, 1)"
                  >
                    下移
                  </a-button>
                  <a-button type="link" size="small" @click="handleEditNode(record)">编辑</a-button>
                  <a-button type="link" size="small" danger @click="handleDeleteNode(record)">
                    删除节点
                  </a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </template>
    </a-drawer>

    <a-modal
      v-model:open="nodeModalVisible"
      :title="editingNode ? '编辑节点' : '新增节点'"
      :confirm-loading="savingNode"
      width="720px"
      ok-text="保存节点"
      cancel-text="取消"
      @ok="handleSaveNode"
    >
      <a-form layout="vertical">
        <div class="process-form-grid">
          <a-form-item label="节点编码">
            <a-input v-model:value="nodeForm.nodeCode" placeholder="为空时自动生成 N1/N2" />
          </a-form-item>
          <a-form-item label="节点名称" required>
            <a-input v-model:value="nodeForm.nodeName" placeholder="节点名称" />
          </a-form-item>
          <a-form-item label="节点顺序">
            <a-input-number v-model:value="nodeForm.nodeOrder" :min="1" style="width: 100%" />
          </a-form-item>
          <a-form-item label="审批模式">
            <a-select v-model:value="nodeForm.approveMode" :options="approveModeOptions" />
          </a-form-item>
          <a-form-item label="允许转办">
            <a-select v-model:value="nodeForm.allowTransfer" :options="enabledOptions" />
          </a-form-item>
          <a-form-item label="允许加签">
            <a-select v-model:value="nodeForm.allowAddSign" :options="enabledOptions" />
          </a-form-item>
        </div>
        <a-form-item label="审批人配置" required>
          <a-textarea v-model:value="nodeForm.approverConfig" :rows="3" />
        </a-form-item>
        <a-form-item label="节点配置 JSON">
          <a-textarea v-model:value="nodeForm.nodeConfig" :rows="2" placeholder="可为空" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="nodeForm.remark" :rows="2" placeholder="备注" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.approval-process-page {
  min-height: 100%;
}

.process-section {
  padding: 20px;
  margin-bottom: 16px;
  background: var(--surface);
  border: 0;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.process-section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.process-section-head h3 {
  margin: 0 0 4px;
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.process-section-head p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.process-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.process-table-panel {
  padding: 16px 0;
}

.process-search-bar :deep(.ant-select-selector),
.process-search-bar :deep(.ant-input) {
  height: 40px;
}
</style>
