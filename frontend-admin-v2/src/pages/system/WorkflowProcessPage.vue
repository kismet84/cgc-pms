<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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
import { workflowBusinessTypeLabel } from '@/pages/workbench/model'

const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const error = ref('')
const templates = ref<WorkflowTemplateRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const current = ref<WorkflowTemplateRecord | null>(null)
let controller: AbortController | null = null

const filter = reactive({ businessType: '', enabled: '', keyword: '' })
const detailDialog = ref(false)
const templateDialog = ref(false)
const nodeDialog = ref(false)
const editingNode = ref<WorkflowTemplateNodeRecord | null>(null)
const deleteTarget = ref<WorkflowTemplateNodeRecord | null>(null)

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
  approverConfig: '{"type":"USER","userId":1}',
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

async function loadList(signal?: AbortSignal): Promise<void> {
  const page = await loadWorkflowTemplates(
    {
      pageNo: pageNo.value,
      pageSize,
      businessType: filter.businessType.trim() || undefined,
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
    if (current.value) await selectTemplate(current.value.id, false)
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

async function selectTemplate(id: string, openDialog = true): Promise<void> {
  if (openDialog) detailDialog.value = true
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
  pageNo.value = 1
  void refresh()
}

function reset(): void {
  Object.assign(filter, { businessType: '', enabled: '', keyword: '' })
  search()
}

function changePage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= total.value) return
  pageNo.value = next
  void refresh()
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
  detailDialog.value = false
  templateDialog.value = true
}

function closeTemplateEditor(): void {
  templateDialog.value = false
  detailDialog.value = true
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
  Object.assign(nodeForm, {
    nodeCode: node?.nodeCode ?? '',
    nodeName: node?.nodeName ?? '',
    approveMode: node?.approveMode ?? 'SEQUENTIAL',
    approverConfig: node?.approverConfig ?? '{"type":"USER","userId":1}',
    allowTransfer: String(node?.allowTransfer ?? 1),
    allowAddSign: String(node?.allowAddSign ?? 1),
    timeoutHours: node?.timeoutHours == null ? '' : String(node.timeoutHours),
    remark: node?.remark ?? '',
  })
  detailDialog.value = false
  nodeDialog.value = true
}

function closeNodeEditor(): void {
  nodeDialog.value = false
  detailDialog.value = true
}

function openDeleteNode(node: WorkflowTemplateNodeRecord): void {
  detailDialog.value = false
  deleteTarget.value = node
}

function closeDeleteNode(): void {
  deleteTarget.value = null
  detailDialog.value = true
}

async function saveNode(): Promise<void> {
  if (!current.value || !nodeForm.nodeName.trim() || !validJson(nodeForm.approverConfig)) {
    showToast('warning', '节点配置无效', '节点名称和合法审批人 JSON 不能为空。')
    return
  }
  const command: WorkflowTemplateNodeCommand = {
    nodeCode: nodeForm.nodeCode.trim() || undefined,
    nodeName: nodeForm.nodeName.trim(),
    nodeType: 'APPROVAL',
    approveMode: nodeForm.approveMode as WorkflowTemplateNodeCommand['approveMode'],
    approverConfig: nodeForm.approverConfig.trim(),
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

function validJson(value: string): boolean {
  try {
    return Boolean(JSON.parse(value))
  } catch {
    return false
  }
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
          <V2Input
            v-model="filter.businessType"
            label="业务类型"
            placeholder="输入业务类型编码"
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

    <div v-else>
      <V2Card>
        <V2PageState
          v-if="!templates.length"
          kind="empty"
          title="暂无流程模板"
          description="当前筛选条件没有流程模板。"
        />
        <div v-else class="workflow-process-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>模板编码</th>
                <th>流程名称</th>
                <th>业务类型</th>
                <th>节点</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="template in templates" :key="template.id">
                <th scope="row">
                  <V2Button
                    type="button"
                    size="small"
                    variant="ghost"
                    class="v2-table__record-link"
                    @click="selectTemplate(template.id)"
                  >
                    {{ template.templateCode }}
                  </V2Button>
                </th>
                <td>{{ template.templateName }}</td>
                <td>{{ workflowBusinessTypeLabel(template.businessType) }}</td>
                <td>{{ template.nodeCount }}</td>
                <td>
                  <V2Badge :tone="template.enabled === 1 ? 'success' : 'neutral'">
                    {{ statusLabel(template.enabled) }}
                  </V2Badge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="workflow-process-page__pagination v2-pagination" aria-label="系统流程分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo === 1"
              @click="changePage(pageNo - 1)"
            >
              上一页
            </V2Button>
            <span>第 {{ pageNo }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo * pageSize >= total"
              @click="changePage(pageNo + 1)"
            >
              下一页
            </V2Button>
          </nav>
        </template>
      </V2Card>

      <V2Dialog
        v-model:open="detailDialog"
        title="审批流程详情"
        :description="
          current ? `${current.templateCode} · ${amountRange(current)}` : '读取流程模板详情。'
        "
        :close-disabled="detailLoading"
        :close-on-backdrop="!detailLoading"
        panel-class="v2-dialog-standard v2-detail-dialog v2-dialog-wide"
      >
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
          description="选择模板后可查看详情、维护节点和调整顺序。"
        />
        <template v-else>
          <div class="workflow-process-page__detail-head">
            <div>
              <strong>{{ current.templateName }}</strong>
              <span>{{ current.templateCode }} · {{ amountRange(current) }}</span>
            </div>
            <div>
              <V2Button type="button" size="small" variant="secondary" @click="openTemplateEditor">
                编辑模板
              </V2Button>
              <V2Button type="button" size="small" @click="openNodeEditor()">新增节点</V2Button>
            </div>
          </div>
          <div
            class="workflow-process-page__table-wrap"
            role="region"
            aria-label="审批节点表格"
            tabindex="0"
          >
            <table>
              <thead>
                <tr>
                  <th>顺序</th>
                  <th>节点编码</th>
                  <th>节点名称</th>
                  <th>审批模式</th>
                  <th>转办/加签</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(node, index) in current.nodes" :key="node.id">
                  <td>{{ node.nodeOrder }}</td>
                  <th scope="row">{{ node.nodeCode }}</th>
                  <td>{{ node.nodeName }}</td>
                  <td>{{ modeLabel(node.approveMode) }}</td>
                  <td>
                    {{ node.allowTransfer === 1 ? '可' : '否' }} /
                    {{ node.allowAddSign === 1 ? '可' : '否' }}
                  </td>
                  <td>
                    <div class="workflow-process-page__actions">
                      <V2Button
                        type="button"
                        size="small"
                        variant="ghost"
                        :disabled="saving || index === 0"
                        @click="moveNode(node, -1)"
                      >
                        上移
                      </V2Button>
                      <V2Button
                        type="button"
                        size="small"
                        variant="ghost"
                        :disabled="saving || index === (current.nodes?.length ?? 0) - 1"
                        @click="moveNode(node, 1)"
                      >
                        下移
                      </V2Button>
                      <V2Button
                        type="button"
                        size="small"
                        variant="ghost"
                        @click="openNodeEditor(node)"
                      >
                        编辑
                      </V2Button>
                      <V2Button
                        type="button"
                        size="small"
                        variant="danger"
                        @click="openDeleteNode(node)"
                      >
                        删除
                      </V2Button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </V2Dialog>
    </div>

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
      description="审批人配置支持 USER、ROLE、POSITION 或 PROJECT_ROLE JSON。"
      :close-disabled="saving"
      :close-on-backdrop="!saving"
      panel-class="v2-dialog-standard"
      @close="closeNodeEditor"
    >
      <div class="workflow-process-page__form-grid">
        <V2Input v-model="nodeForm.nodeCode" label="节点编码" placeholder="留空自动生成" />
        <V2Input v-model="nodeForm.nodeName" label="节点名称" required />
        <V2Select v-model="nodeForm.approveMode" label="审批模式" :options="approveModeOptions" />
        <V2Input v-model="nodeForm.approverConfig" label="审批人配置 JSON" required />
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

.workflow-process-page__filters,
.workflow-process-page__detail-head,
.workflow-process-page__pagination,
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

.workflow-process-page__detail-head {
  justify-content: space-between;
  margin-bottom: var(--v2-space-4);
}

.workflow-process-page__detail-head > div {
  display: flex;
  flex-direction: column;
  gap: var(--v2-space-1);
}

.workflow-process-page__detail-head > div:last-child {
  flex-direction: row;
}

.workflow-process-page__pagination {
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .workflow-process-page__form-grid {
    grid-template-columns: 1fr;
  }

  .workflow-process-page__detail-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
