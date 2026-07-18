<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import type {
  CostSubjectImpact,
  CostSubjectMappingVersion,
  CostSubjectRule,
  CostSubjectTreeNode,
  CostSubjectV2Row,
  CostSubjectVO,
  ProjectCostSubjectScope,
} from '@/types/costSubject'
import { normalizeArray } from '@/utils/normalizeArray'
import { useUserStore } from '@/stores/user'
import {
  activateCostSubjectMappingVersion,
  createBidCostTargetTransfer,
  createCostSubject,
  createCostSubjectMappingVersion,
  createCostSubjectRule,
  createFinanceCostAllocation,
  deleteCostSubject,
  getBidCostTargetTransfers,
  getCostSubjectImpact,
  getCostSubjectMappingVersions,
  getCostSubjectReconciliation,
  getCostSubjectRules,
  getCostSubjectTree,
  getFinanceCostAllocations,
  getProjectCostSubjectScopes,
  reverseBidCostTargetTransfer,
  reverseFinanceCostAllocation,
  saveProjectCostSubjectScope,
  toggleCostSubjectStatus,
  updateCostSubject,
} from '@/api/modules/costSubject'

const route = useRoute()
const userStore = useUserStore()
const activeTab = computed(
  () =>
    String(route.name ?? '')
      .replace('CostSubject', '')
      .toLowerCase() || 'taxonomy',
)
const canSubjectAdd = computed(() => userStore.hasPermission('cost:add'))
const canSubjectEdit = computed(() => userStore.hasPermission('cost:edit'))
const canSubjectDelete = computed(() => userStore.hasPermission('cost:delete'))
const canMappingEdit = computed(() => userStore.hasPermission('cost:subject:mapping:edit'))
const canMappingActivate = computed(() => userStore.hasPermission('cost:subject:mapping:activate'))
const canRuleEdit = computed(() => userStore.hasPermission('cost:subject:rule:edit'))
const canScopeEdit = computed(() => userStore.hasPermission('cost:subject:scope:edit'))
const canBidTransfer = computed(() => userStore.hasPermission('cost:subject:bid-transfer'))
const canFinanceAllocate = computed(() => userStore.hasPermission('cost:subject:finance-allocate'))

const treeData = ref<CostSubjectTreeNode[]>([])
const treeLoading = ref(false)
const selectedKeys = ref<string[]>([])
const expandedKeys = ref<string[]>([])
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formLoading = ref(false)
const formData = reactive<Partial<CostSubjectVO>>({
  parentId: null,
  subjectCode: '',
  subjectName: '',
  subjectType: 'MATERIAL',
  accountCategory: 'COST',
  level: 1,
  sortOrder: 0,
  status: 'ENABLE',
})

function findNode(nodes: CostSubjectTreeNode[], id: string): CostSubjectTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = node.children ? findNode(node.children, id) : null
    if (found) return found
  }
  return null
}
const selectedNode = computed(() => findNode(treeData.value, selectedKeys.value[0]))

async function loadTree() {
  treeLoading.value = true
  try {
    treeData.value = normalizeArray(await getCostSubjectTree('COST'))
  } finally {
    treeLoading.value = false
  }
}

function openSubjectForm(mode: 'create' | 'edit', parent?: CostSubjectTreeNode | null) {
  formMode.value = mode
  const source = mode === 'edit' ? selectedNode.value : null
  Object.assign(formData, {
    parentId: source?.parentId ?? parent?.id ?? null,
    subjectCode: source?.subjectCode ?? '',
    subjectName: source?.subjectName ?? '',
    subjectType: source?.subjectType ?? 'MATERIAL',
    accountCategory: 'COST',
    level: source?.level ?? (parent?.level ?? 0) + 1,
    sortOrder: source?.sortOrder ?? 0,
    status: source?.status ?? 'ENABLE',
  })
  formOpen.value = true
}

async function saveSubject() {
  if (!formData.subjectCode?.trim() || !formData.subjectName?.trim()) {
    message.warning('科目编码和名称不能为空')
    return
  }
  formLoading.value = true
  try {
    if (formMode.value === 'edit' && selectedNode.value)
      await updateCostSubject(selectedNode.value.id, formData)
    else await createCostSubject(formData)
    formOpen.value = false
    await loadTree()
    message.success('成本科目已保存')
  } finally {
    formLoading.value = false
  }
}

async function switchStatus() {
  if (!selectedNode.value) return
  await toggleCostSubjectStatus(selectedNode.value.id)
  await loadTree()
  message.success('状态已更新')
}

function removeSubject() {
  if (!selectedNode.value) return
  const node = selectedNode.value
  Modal.confirm({
    title: '确认删除成本科目',
    content: '存在任何业务引用、V2映射、归集规则或项目范围时，后端将拒绝删除。',
    okType: 'danger',
    async onOk() {
      await deleteCostSubject(node.id)
      selectedKeys.value = []
      await loadTree()
      message.success('科目已删除')
    },
  })
}

const versions = ref<CostSubjectMappingVersion[]>([])
const rules = ref<CostSubjectRule[]>([])
const mappingOpen = ref(false)
const ruleOpen = ref(false)
const mappingDraft = reactive({
  versionCode: '',
  versionName: '',
  effectiveDate: '',
  remark: '',
  items: [
    {
      sourceSubjectId: '',
      targetGroupCode: '',
      targetSubjectId: '',
      historicalDisplayName: '',
      mappingReason: '',
    },
  ],
})
const ruleDraft = reactive({
  ruleCode: '',
  mappingVersionId: '',
  sourceType: '',
  businessCategory: '*',
  projectId: '',
  costSubjectId: '',
  priority: 100,
  effectiveFrom: '',
  effectiveTo: '',
  remark: '',
})

async function loadRules() {
  ;[versions.value, rules.value] = await Promise.all([
    getCostSubjectMappingVersions(),
    getCostSubjectRules(),
  ])
}

async function saveMapping() {
  await createCostSubjectMappingVersion({
    ...mappingDraft,
    items: mappingDraft.items.map((item) => ({
      ...item,
      targetSubjectId: item.targetSubjectId || null,
    })),
  })
  mappingOpen.value = false
  await loadRules()
  message.success('映射草稿已创建')
}

function activateMapping(row: CostSubjectMappingVersion) {
  const approvalInstanceId = window.prompt(`请输入映射版本 ${row.versionCode} 已通过的审批实例ID`)
  if (!approvalInstanceId) return
  void activateCostSubjectMappingVersion(row.id, approvalInstanceId).then(async () => {
    await loadRules()
    message.success('映射版本已启用')
  })
}

async function saveRule() {
  await createCostSubjectRule({
    ...ruleDraft,
    projectId: ruleDraft.projectId || null,
    effectiveTo: ruleDraft.effectiveTo || null,
  })
  ruleOpen.value = false
  await loadRules()
  message.success('归集规则草稿已创建；随映射版本审批启用')
}

const scopeProjectId = ref('')
const scopes = ref<ProjectCostSubjectScope[]>([])
const scopeOpen = ref(false)
const scopeDraft = reactive({
  costSubjectId: '',
  enabled: true,
  effectiveFrom: '',
  effectiveTo: '',
  remark: '',
})
async function loadScopes() {
  if (!scopeProjectId.value) return
  scopes.value = await getProjectCostSubjectScopes(scopeProjectId.value)
}
async function saveScope() {
  await saveProjectCostSubjectScope({
    ...scopeDraft,
    projectId: scopeProjectId.value,
    effectiveTo: scopeDraft.effectiveTo || null,
  })
  scopeOpen.value = false
  await loadScopes()
  message.success('项目适用范围已保存')
}

const traceSubjectId = ref('')
const traceProjectId = ref('')
const impact = ref<CostSubjectImpact | null>(null)
const transfers = ref<CostSubjectV2Row[]>([])
const allocations = ref<CostSubjectV2Row[]>([])
const reconciliation = ref<CostSubjectV2Row | null>(null)
const transferOpen = ref(false)
const allocationOpen = ref(false)
const transferDraft = reactive({
  bidCostId: '',
  projectId: '',
  targetId: '',
  mappingVersionId: '',
  approvalInstanceId: '',
  idempotencyKey: '',
  remark: '',
})
const allocationDraft = reactive({
  sourceType: 'ACCOUNTING_ENTRY_LINE',
  sourceId: '',
  allocationBasis: 'BENEFIT_AMOUNT',
  accountingPeriod: '',
  costSubjectId: '',
  approvalInstanceId: '',
  idempotencyKey: '',
  remark: '',
  lines: [{ projectId: '', basisValue: 1 }],
})

async function loadTrace() {
  ;[transfers.value, allocations.value] = await Promise.all([
    getBidCostTargetTransfers(),
    getFinanceCostAllocations(),
  ])
}
async function queryImpact() {
  if (traceSubjectId.value) impact.value = await getCostSubjectImpact(traceSubjectId.value)
}
async function queryReconciliation() {
  if (traceProjectId.value)
    reconciliation.value = await getCostSubjectReconciliation(traceProjectId.value)
}
async function saveTransfer() {
  await createBidCostTargetTransfer(transferDraft)
  transferOpen.value = false
  await loadTrace()
  message.success('投标成本已形成目标成本转入事实')
}
async function saveAllocation() {
  await createFinanceCostAllocation(allocationDraft)
  allocationOpen.value = false
  await loadTrace()
  message.success('财务费用分摊已过账')
}

async function reverseTransfer(row: CostSubjectV2Row) {
  const approvalInstanceId = window.prompt('请输入已通过的投标成本转入冲销审批实例ID')
  if (!approvalInstanceId) return
  const idempotencyKey = window.prompt('请输入本次冲销幂等键')
  if (!idempotencyKey) return
  await reverseBidCostTargetTransfer(String(row.id), approvalInstanceId, idempotencyKey)
  await loadTrace()
  message.success('投标成本转入已生成反向事实')
}

async function reverseAllocation(row: CostSubjectV2Row) {
  const approvalInstanceId = window.prompt('请输入已通过的财务费用分摊冲销审批实例ID')
  if (!approvalInstanceId) return
  const idempotencyKey = window.prompt('请输入本次冲销幂等键')
  if (!idempotencyKey) return
  await reverseFinanceCostAllocation(String(row.id), approvalInstanceId, idempotencyKey)
  await loadTrace()
  message.success('财务费用分摊已生成反向事实')
}

async function loadActiveWorkspace() {
  if (activeTab.value === 'taxonomy') await loadTree()
  if (activeTab.value === 'rules') await loadRules()
  if (activeTab.value === 'trace') await loadTrace()
}
watch(activeTab, loadActiveWorkspace)
onMounted(loadActiveWorkspace)
</script>

<template>
  <div class="lg-list-page lg-page app-page cost-subject-center">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>基础资料</a-breadcrumb-item>
          <a-breadcrumb-item>成本科目中心</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="page-note">
          统一科目、显式归集、项目范围、转入与分摊追踪；旧科目引用已迁移并留存审计。
        </div>
      </div>
    </div>

    <section v-if="activeTab === 'taxonomy'" class="workspace-grid">
      <a-card title="成本域科目树" :loading="treeLoading">
        <template #extra>
          <a-space>
            <a-button size="small" @click="loadTree">刷新</a-button>
            <a-button
              v-if="canSubjectAdd"
              size="small"
              type="primary"
              @click="openSubjectForm('create')"
              >新增根科目</a-button
            >
          </a-space>
        </template>
        <a-tree
          :tree-data="treeData"
          :field-names="{ children: 'children', title: 'subjectName', key: 'id' }"
          :selected-keys="selectedKeys"
          :expanded-keys="expandedKeys"
          show-line
          block-node
          @select="(keys: (string | number)[]) => (selectedKeys = keys.map(String))"
          @expand="(keys: (string | number)[]) => (expandedKeys = keys.map(String))"
        >
          <template #title="{ subjectCode, subjectName }">
            <span class="tree-code">{{ subjectCode }}</span> {{ subjectName }}
          </template>
        </a-tree>
      </a-card>
      <a-card title="科目详情">
        <template v-if="selectedNode" #extra>
          <a-space v-if="canSubjectAdd || canSubjectEdit || canSubjectDelete">
            <a-button
              v-if="canSubjectAdd"
              size="small"
              @click="openSubjectForm('create', selectedNode)"
              >新增子科目</a-button
            >
            <a-button v-if="canSubjectEdit" size="small" @click="openSubjectForm('edit')"
              >编辑</a-button
            >
            <a-button v-if="canSubjectEdit" size="small" @click="switchStatus">{{
              selectedNode.status === 'ENABLE' ? '停用' : '启用'
            }}</a-button>
            <a-button v-if="canSubjectDelete" size="small" danger @click="removeSubject"
              >删除</a-button
            >
          </a-space>
        </template>
        <a-descriptions v-if="selectedNode" bordered :column="2">
          <a-descriptions-item label="编码">{{ selectedNode.subjectCode }}</a-descriptions-item>
          <a-descriptions-item label="名称">{{ selectedNode.subjectName }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ selectedNode.subjectType }}</a-descriptions-item>
          <a-descriptions-item label="层级">{{ selectedNode.level }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ selectedNode.status }}</a-descriptions-item>
          <a-descriptions-item label="末级">{{
            selectedNode.children?.length ? '否' : '是'
          }}</a-descriptions-item>
        </a-descriptions>
        <a-empty v-else description="选择科目查看详情与影响保护" />
      </a-card>
    </section>

    <section v-else-if="activeTab === 'rules'" class="stack">
      <a-card title="映射版本">
        <template v-if="canMappingEdit" #extra
          ><a-button type="primary" @click="mappingOpen = true">新建映射版本</a-button></template
        >
        <a-table :data-source="versions" row-key="id" :pagination="false" size="small">
          <a-table-column title="版本" data-index="versionCode" />
          <a-table-column title="名称" data-index="versionName" />
          <a-table-column title="映射数" data-index="itemCount" />
          <a-table-column title="生效日期" data-index="effectiveDate" />
          <a-table-column title="状态" data-index="status" />
          <a-table-column title="操作">
            <template #default="{ record }"
              ><a-button
                v-if="record.status === 'DRAFT' && canMappingActivate"
                type="link"
                @click="activateMapping(record)"
                >审批后启用</a-button
              ></template
            >
          </a-table-column>
        </a-table>
      </a-card>
      <a-card title="显式归集规则">
        <template v-if="canRuleEdit" #extra
          ><a-button type="primary" @click="ruleOpen = true">新增规则</a-button></template
        >
        <a-table :data-source="rules" row-key="id" size="small">
          <a-table-column title="规则" data-index="ruleCode" />
          <a-table-column title="来源" data-index="sourceType" />
          <a-table-column title="业务分类" data-index="businessCategory" />
          <a-table-column title="项目" data-index="projectId" />
          <a-table-column title="末级科目" data-index="subjectName" />
          <a-table-column title="版本" data-index="versionCode" />
          <a-table-column title="状态" data-index="status" />
        </a-table>
      </a-card>
    </section>

    <section v-else-if="activeTab === 'scope'" class="stack">
      <a-alert
        type="info"
        show-icon
        message="项目存在适用范围配置后，目标成本与财务分摊只能使用范围内启用末级科目。"
      />
      <a-card title="项目适用范围">
        <template #extra>
          <a-space>
            <a-input
              v-model:value="scopeProjectId"
              placeholder="项目ID"
              style="width: 220px"
              @press-enter="loadScopes"
            />
            <a-button @click="loadScopes">查询</a-button>
            <a-button
              v-if="canScopeEdit"
              type="primary"
              :disabled="!scopeProjectId"
              @click="scopeOpen = true"
              >维护范围</a-button
            >
            <a-button href="/cost-target/index">进入目标成本</a-button>
          </a-space>
        </template>
        <a-table :data-source="scopes" row-key="id" size="small">
          <a-table-column title="科目编码" data-index="subjectCode" />
          <a-table-column title="科目名称" data-index="subjectName" />
          <a-table-column title="启用" data-index="enabled" />
          <a-table-column title="生效" data-index="effectiveFrom" />
          <a-table-column title="失效" data-index="effectiveTo" />
        </a-table>
      </a-card>
    </section>

    <section v-else class="stack">
      <a-card title="影响分析与项目对账">
        <a-space wrap>
          <a-input v-model:value="traceSubjectId" placeholder="成本科目ID" style="width: 200px" />
          <a-button @click="queryImpact">查询引用影响</a-button>
          <a-input v-model:value="traceProjectId" placeholder="项目ID" style="width: 200px" />
          <a-button @click="queryReconciliation">项目对账</a-button>
          <a-button v-if="canBidTransfer" type="primary" @click="transferOpen = true"
            >投标成本转入</a-button
          >
          <a-button v-if="canFinanceAllocate" type="primary" @click="allocationOpen = true"
            >财务费用分摊</a-button
          >
        </a-space>
        <a-descriptions v-if="impact" class="result-box" bordered :column="4">
          <a-descriptions-item v-for="(value, key) in impact" :key="key" :label="key">{{
            value
          }}</a-descriptions-item>
        </a-descriptions>
        <a-descriptions v-if="reconciliation" class="result-box" bordered :column="3">
          <a-descriptions-item v-for="(value, key) in reconciliation" :key="key" :label="key">{{
            value
          }}</a-descriptions-item>
        </a-descriptions>
      </a-card>
      <a-card title="投标成本转入记录">
        <a-table :data-source="transfers" row-key="id" size="small" :scroll="{ x: 900 }">
          <a-table-column title="转入编号" data-index="transferCode" />
          <a-table-column title="投标项目" data-index="bidProjectName" />
          <a-table-column title="目标版本" data-index="versionNo" />
          <a-table-column title="金额" data-index="totalAmount" />
          <a-table-column title="状态" data-index="status" />
          <a-table-column title="审批实例" data-index="approvalInstanceId" />
          <a-table-column title="操作" fixed="right">
            <template #default="{ record }"
              ><a-button
                v-if="canBidTransfer && record.status === 'POSTED' && !record.reversalOfId"
                type="link"
                danger
                @click="reverseTransfer(record)"
                >冲销</a-button
              ></template
            >
          </a-table-column>
        </a-table>
      </a-card>
      <a-card title="项目财务费用分摊记录">
        <a-table :data-source="allocations" row-key="id" size="small" :scroll="{ x: 900 }">
          <a-table-column title="批次" data-index="batchCode" />
          <a-table-column title="来源" data-index="sourceType" />
          <a-table-column title="依据" data-index="allocationBasis" />
          <a-table-column title="期间" data-index="accountingPeriod" />
          <a-table-column title="金额" data-index="sourceAmount" />
          <a-table-column title="科目" data-index="subjectName" />
          <a-table-column title="状态" data-index="status" />
          <a-table-column title="操作" fixed="right">
            <template #default="{ record }"
              ><a-button
                v-if="canFinanceAllocate && record.status === 'POSTED' && !record.reversalOfId"
                type="link"
                danger
                @click="reverseAllocation(record)"
                >冲销</a-button
              ></template
            >
          </a-table-column>
        </a-table>
      </a-card>
    </section>

    <a-modal
      v-model:open="formOpen"
      title="成本科目"
      :confirm-loading="formLoading"
      @ok="saveSubject"
    >
      <a-form layout="vertical">
        <a-form-item label="编码" required
          ><a-input v-model:value="formData.subjectCode"
        /></a-form-item>
        <a-form-item label="名称" required
          ><a-input v-model:value="formData.subjectName"
        /></a-form-item>
        <a-form-item label="类型"><a-input v-model:value="formData.subjectType" /></a-form-item>
        <a-form-item label="父科目"
          ><a-input :value="formData.parentId || '根节点'" disabled
        /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="mappingOpen" title="新建科目映射版本" width="1000px" @ok="saveMapping">
      <a-form layout="vertical">
        <a-row :gutter="16"
          ><a-col :span="8"
            ><a-form-item label="版本编码"
              ><a-input v-model:value="mappingDraft.versionCode" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="版本名称"
              ><a-input v-model:value="mappingDraft.versionName" /></a-form-item></a-col
          ><a-col :span="8"
            ><a-form-item label="生效日期"
              ><a-input
                v-model:value="mappingDraft.effectiveDate"
                placeholder="YYYY-MM-DD" /></a-form-item></a-col
        ></a-row>
        <a-space v-for="(item, index) in mappingDraft.items" :key="index" class="mapping-line" wrap>
          <a-input v-model:value="item.sourceSubjectId" placeholder="源科目ID" />
          <a-input v-model:value="item.targetGroupCode" placeholder="V2归集组编码" />
          <a-input
            v-model:value="item.targetSubjectId"
            placeholder="目标末级科目ID；仅历史展示可空"
          />
          <a-input v-model:value="item.historicalDisplayName" placeholder="历史展示名称" />
          <a-button danger @click="mappingDraft.items.splice(index, 1)">移除</a-button>
        </a-space>
        <a-button
          @click="
            mappingDraft.items.push({
              sourceSubjectId: '',
              targetGroupCode: '',
              targetSubjectId: '',
              historicalDisplayName: '',
              mappingReason: '',
            })
          "
          >增加映射</a-button
        >
      </a-form>
    </a-modal>

    <a-modal v-model:open="ruleOpen" title="新增显式归集规则" @ok="saveRule">
      <a-form layout="vertical">
        <a-form-item label="规则编码"><a-input v-model:value="ruleDraft.ruleCode" /></a-form-item>
        <a-form-item label="映射版本ID"
          ><a-input v-model:value="ruleDraft.mappingVersionId"
        /></a-form-item>
        <a-form-item label="业务来源"
          ><a-input v-model:value="ruleDraft.sourceType" placeholder="例如 MATERIAL_RECEIPT"
        /></a-form-item>
        <a-form-item label="业务分类"
          ><a-input v-model:value="ruleDraft.businessCategory"
        /></a-form-item>
        <a-form-item label="项目ID"
          ><a-input v-model:value="ruleDraft.projectId" placeholder="空表示全局规则"
        /></a-form-item>
        <a-form-item label="目标末级科目ID"
          ><a-input v-model:value="ruleDraft.costSubjectId"
        /></a-form-item>
        <a-form-item label="生效日期"
          ><a-input v-model:value="ruleDraft.effectiveFrom" placeholder="YYYY-MM-DD"
        /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="scopeOpen" title="维护项目科目范围" @ok="saveScope">
      <a-form layout="vertical">
        <a-form-item label="末级科目ID"
          ><a-input v-model:value="scopeDraft.costSubjectId"
        /></a-form-item>
        <a-form-item label="启用"><a-switch v-model:checked="scopeDraft.enabled" /></a-form-item>
        <a-form-item label="生效日期"
          ><a-input v-model:value="scopeDraft.effectiveFrom" placeholder="YYYY-MM-DD"
        /></a-form-item>
        <a-form-item label="失效日期"
          ><a-input v-model:value="scopeDraft.effectiveTo" placeholder="可空"
        /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="transferOpen" title="投标成本转入目标成本" @ok="saveTransfer">
      <a-alert
        type="warning"
        show-icon
        message="仅中标项目、草稿目标成本、ACTIVE映射版本和已通过审批实例可执行。"
      />
      <a-form layout="vertical" class="modal-form">
        <a-form-item label="投标成本ID"
          ><a-input v-model:value="transferDraft.bidCostId"
        /></a-form-item>
        <a-form-item label="中标项目ID"
          ><a-input v-model:value="transferDraft.projectId"
        /></a-form-item>
        <a-form-item label="目标成本版本ID"
          ><a-input v-model:value="transferDraft.targetId"
        /></a-form-item>
        <a-form-item label="ACTIVE映射版本ID"
          ><a-input v-model:value="transferDraft.mappingVersionId"
        /></a-form-item>
        <a-form-item label="审批实例ID"
          ><a-input v-model:value="transferDraft.approvalInstanceId"
        /></a-form-item>
        <a-form-item label="幂等键"
          ><a-input v-model:value="transferDraft.idempotencyKey"
        /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="allocationOpen"
      title="项目财务费用分摊"
      width="800px"
      @ok="saveAllocation"
    >
      <a-alert
        type="warning"
        show-icon
        message="仅支持已过账借方凭证明细或已审批费用申请；默认不自动分摊。"
      />
      <a-form layout="vertical" class="modal-form">
        <a-form-item label="来源类型"
          ><a-select
            v-model:value="allocationDraft.sourceType"
            :options="[
              { value: 'ACCOUNTING_ENTRY_LINE', label: '凭证借方明细' },
              { value: 'EXPENSE_APPLICATION', label: '已审批费用申请' },
            ]"
        /></a-form-item>
        <a-form-item label="来源ID"
          ><a-input v-model:value="allocationDraft.sourceId"
        /></a-form-item>
        <a-form-item label="分摊依据"
          ><a-select
            v-model:value="allocationDraft.allocationBasis"
            :options="
              [
                'DIRECT_PROJECT',
                'BENEFIT_AMOUNT',
                'OCCUPIED_DAYS',
                'CONTRACT_AMOUNT_EXCEPTION',
              ].map((value) => ({ value, label: value }))
            "
        /></a-form-item>
        <a-form-item label="会计期间"
          ><a-input v-model:value="allocationDraft.accountingPeriod" placeholder="YYYY-MM"
        /></a-form-item>
        <a-form-item label="项目财务费用末级科目ID"
          ><a-input v-model:value="allocationDraft.costSubjectId"
        /></a-form-item>
        <a-form-item label="审批实例ID"
          ><a-input v-model:value="allocationDraft.approvalInstanceId"
        /></a-form-item>
        <a-form-item label="幂等键"
          ><a-input v-model:value="allocationDraft.idempotencyKey"
        /></a-form-item>
        <a-space v-for="(line, index) in allocationDraft.lines" :key="index" class="mapping-line">
          <a-input v-model:value="line.projectId" placeholder="项目ID" />
          <a-input-number v-model:value="line.basisValue" :min="0.000001" placeholder="依据值" />
          <a-button danger @click="allocationDraft.lines.splice(index, 1)">移除</a-button>
        </a-space>
        <a-button @click="allocationDraft.lines.push({ projectId: '', basisValue: 1 })"
          >增加项目</a-button
        >
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-note {
  margin-top: 6px;
  color: var(--muted);
}
.workspace-grid {
  display: grid;
  grid-template-columns: minmax(320px, 42%) minmax(0, 1fr);
  gap: 16px;
}
.stack {
  display: grid;
  gap: 16px;
}
.tree-code {
  margin-right: 8px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
.result-box,
.modal-form {
  margin-top: 16px;
}
.mapping-line {
  display: flex;
  margin-bottom: 10px;
}
.mapping-line :deep(.ant-input) {
  min-width: 140px;
}
@media (max-width: 980px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }
}
</style>
