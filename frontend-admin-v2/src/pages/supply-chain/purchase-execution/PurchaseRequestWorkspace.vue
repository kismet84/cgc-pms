<script setup lang="ts">
import type {
  MaterialRecord,
  PurchaseRequestCommand,
  PurchaseRequestItemRecord,
  PurchaseRequestRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  MaterialSearchPicker,
  V2Badge,
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
} from '@/components'
import {
  createPurchaseRequest,
  loadPurchaseRequest,
  loadPurchaseRequestFormOptions,
  loadPurchaseRequestItems,
  loadPurchaseRequests,
  submitPurchaseRequest,
} from '@/services/supply-chain'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import PurchaseExecutionDetail from './PurchaseExecutionDetail.vue'
import {
  errorText,
  newRequestItemDraft,
  optional,
  positiveValue,
  recordAmount,
  requestCode,
  requestDetailTable,
  required,
  requiredDraft,
  statusLabel,
  type RequestItemDraft,
} from './model'
import {
  savePurchaseRequest,
  submitSavedPurchaseRequest,
} from './application/save-purchase-request'

const route = useRoute()
const session = useSessionStore()
const workspace = useWorkspaceStore()
const application = { create: createPurchaseRequest, submit: submitPurchaseRequest }

const records = ref<PurchaseRequestRecord[]>([])
const selected = ref<PurchaseRequestRecord | null>(null)
const detailItems = ref<PurchaseRequestItemRecord[]>([])
const materials = ref<MaterialRecord[]>([])
const drafts = ref<RequestItemDraft[]>([])
const form = reactive<Record<string, string>>({})
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const loading = ref(false)
const detailLoading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const editorOpen = ref(false)

let listController: AbortController | null = null
let detailController: AbortController | null = null
let listGeneration = 0
let detailGeneration = 0

const projectId = computed(() => workspace.selectedProjectId || '')
const canUseSelf = computed(() => session.hasPermission('purchase:request:self'))
const selfOnly = computed(
  () => canUseSelf.value && !session.hasAdminOrPermission('purchase:request:edit'),
)
const canAdd = computed(
  () =>
    canUseSelf.value ||
    (session.hasAdminOrPermission('purchase:request:add') &&
      session.hasAdminOrPermission('purchase:request:edit') &&
      session.hasAdminOrPermission('purchase:request:delete')),
)
const canSaveItems = computed(
  () => canUseSelf.value || session.hasAdminOrPermission('purchase:request:edit'),
)
const canSubmitSelected = computed(
  () =>
    (canUseSelf.value || session.hasAdminOrPermission('purchase:request:submit')) &&
    selected.value?.approvalStatus === 'DRAFT',
)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const materialOptions = computed(() =>
  materials.value.map((item) => ({
    value: item.id,
    label: [item.materialCode, item.materialName, item.specification].filter(Boolean).join(' · '),
  })),
)
const detailTable = computed(() => requestDetailTable(detailItems.value))

async function loadPage(): Promise<void> {
  listController?.abort()
  detailController?.abort()
  selected.value = null
  detailItems.value = []
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await loadPurchaseRequests(
      { pageNum: pageNo.value, pageSize, projectId: projectId.value || undefined },
      controller.signal,
    )
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = page.total
    const requestId = typeof route.query.requestId === 'string' ? route.query.requestId : ''
    if (!requestId) return
    const listed = page.records.find((record) => record.id === requestId)
    if (listed) {
      void selectRecord(listed)
      return
    }
    try {
      const source = await loadPurchaseRequest(requestId, controller.signal)
      if (generation === listGeneration) void selectRecord(source)
    } catch {
      // Invalid/stale source link: leave list usable and do not block page load.
    }
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, '采购申请加载失败')
      showToast('error', '采购申请读取失败', errorMessage.value)
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function selectRecord(record: PurchaseRequestRecord): Promise<void> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  selected.value = record
  detailItems.value = []
  detailLoading.value = true
  try {
    const [detail, items] = await Promise.all([
      loadPurchaseRequest(record.id, controller.signal),
      loadPurchaseRequestItems(record.id, controller.signal),
    ])
    if (generation !== detailGeneration) return
    selected.value = detail
    detailItems.value = items
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      showToast('error', '详情读取失败', errorText(error, '采购申请详情加载失败'))
    }
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

function clearDetail(): void {
  detailController?.abort()
  selected.value = null
  detailItems.value = []
}

async function openCreate(): Promise<void> {
  for (const key of Object.keys(form)) delete form[key]
  form.projectId = projectId.value
  drafts.value = [newRequestItemDraft()]
  materials.value = []
  editorOpen.value = true
  if (!form.projectId) return
  busy.value = true
  try {
    materials.value = (await loadPurchaseRequestFormOptions(form.projectId)).materials
  } catch (error) {
    errorMessage.value = errorText(error, '采购申请候选读取失败')
    showToast('error', '业务候选读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function changeProject(value: string): Promise<void> {
  form.projectId = value
  materials.value = []
  if (!value || busy.value) return
  busy.value = true
  try {
    materials.value = (await loadPurchaseRequestFormOptions(value)).materials
  } catch (error) {
    errorMessage.value = errorText(error, '采购申请候选读取失败')
    showToast('error', '业务候选读取失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

function addItem(): void {
  if (drafts.value.length < 200) drafts.value.push(newRequestItemDraft())
}

function addMaterial(material: MaterialRecord): void {
  if (!materials.value.some((item) => item.id === material.id)) materials.value.push(material)
  const emptyIndex = drafts.value.findIndex((item) => !item.materialId)
  const item = {
    ...(emptyIndex >= 0 ? drafts.value[emptyIndex] : newRequestItemDraft()),
    materialId: material.id,
    unit: material.unit || '',
  }
  if (emptyIndex >= 0) drafts.value[emptyIndex] = item
  else if (drafts.value.length < 200) drafts.value.push(item)
}

function removeItem(index: number): void {
  if (drafts.value.length > 1) drafts.value.splice(index, 1)
}

function selectMaterial(index: number, value: string): void {
  const item = drafts.value[index]
  if (!item) return
  item.materialId = value
  item.unit = materials.value.find((candidate) => candidate.id === value)?.unit || ''
}

async function save(): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    const command: PurchaseRequestCommand = {
      header: {
        projectId: required(form, 'projectId', '项目'),
        remark: optional(form, 'remark'),
      },
      items: drafts.value.map((item, index) => ({
        materialId: requiredDraft(item.materialId, `第${index + 1}条物料`),
        quantity: positiveValue(item.quantity, `第${index + 1}条申请数量`),
        unit: item.unit.trim() || undefined,
        plannedDate: requiredDraft(item.plannedDate, `第${index + 1}条计划日期`),
        useLocation: requiredDraft(item.useLocation, `第${index + 1}条使用部位`),
        remark: item.remark.trim() || undefined,
      })),
    }
    const id = await savePurchaseRequest(command, application)
    editorOpen.value = false
    await loadPage()
    const created = records.value.find((record) => record.id === id)
    if (created) await selectRecord(created)
    showToast('success', '操作成功', '采购申请已保存，列表与详情已更新')
  } catch (error) {
    errorMessage.value = errorText(error, '采购申请保存失败')
    showToast('error', '采购申请保存失败', errorMessage.value)
  } finally {
    busy.value = false
  }
}

async function submitSelected(): Promise<void> {
  if (!selected.value || busy.value) return
  busy.value = true
  try {
    const id = selected.value.id
    await submitSavedPurchaseRequest(id, application)
    await loadPage()
    const refreshed = records.value.find((record) => record.id === id)
    if (refreshed) await selectRecord(refreshed)
    showToast('success', '操作成功', '采购申请已提交，状态已重新读取')
  } catch (error) {
    showToast('error', '采购申请提交失败', errorText(error, '采购申请提交失败'))
  } finally {
    busy.value = false
  }
}

function changePage(next: number): void {
  if (next < 1 || next > pageCount.value || next === pageNo.value) return
  pageNo.value = next
  void loadPage()
}

watch(
  [projectId, () => route.query.requestId],
  () => {
    pageNo.value = 1
    void loadPage()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
})
</script>

<template>
  <section class="purchase-execution-page">
    <V2Card title="采购申请" :heading-level="1">
      <template #actions>
        <V2Button v-if="canAdd" size="small" @click="openCreate">新建采购申请</V2Button>
      </template>
    </V2Card>

    <V2PageState
      v-if="loading && !records.length"
      kind="loading"
      title="正在加载"
      description="正在读取采购申请。"
    />
    <V2PageState
      v-else-if="!errorMessage && !loading && !records.length"
      title="暂无记录"
      description="当前项目范围没有采购申请。"
    >
      <template v-if="canAdd" #actions
        ><V2Button @click="openCreate">新建采购申请</V2Button></template
      >
    </V2PageState>

    <section v-else class="purchase-execution-page__layout">
      <V2Card :heading-level="2">
        <div class="purchase-execution-page__table-wrap">
          <table>
            <thead>
              <tr>
                <th>编号</th>
                <th>来源</th>
                <th>审批状态</th>
                <th>业务状态</th>
                <th>金额</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="record in records"
                :key="record.id"
                :class="{ selected: selected?.id === record.id }"
              >
                <td>
                  <V2Button
                    variant="ghost"
                    size="small"
                    class="v2-table__record-link"
                    @click="selectRecord(record)"
                  >
                    {{ requestCode(record) }}
                  </V2Button>
                </td>
                <td>{{ record.contractName || '采购申请' }}</td>
                <td>
                  <V2Badge>{{ statusLabel(record.approvalStatus) }}</V2Badge>
                </td>
                <td>
                  <V2Badge>{{ statusLabel(record.status) }}</V2Badge>
                </td>
                <td>{{ recordAmount(record) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="purchase-execution-page__pagination" aria-label="采购申请分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              variant="secondary"
              size="small"
              :disabled="pageNo <= 1 || loading"
              @click="changePage(pageNo - 1)"
              >上一页</V2Button
            >
            <span>第 {{ pageNo }} 页</span>
            <V2Button
              variant="secondary"
              size="small"
              :disabled="pageNo >= pageCount || loading"
              @click="changePage(pageNo + 1)"
              >下一页</V2Button
            >
          </nav>
        </template>
      </V2Card>
    </section>

    <PurchaseExecutionDetail
      v-if="selected"
      open
      mode="request"
      title="采购申请"
      :business-id="selected.id"
      :business-code="requestCode(selected)"
      :project-name="selected.projectName"
      :source-label="selected.contractName || '采购申请'"
      :approval-status="selected.approvalStatus"
      :business-status="statusLabel(selected.status)"
      :amount="selected.totalAmount"
      :detail-table="detailTable"
      :detail-loading="detailLoading"
      :can-edit="false"
      :can-manage-attachments="canSaveItems"
      :can-submit="canSubmitSelected"
      @close="clearDetail"
      @submit="submitSelected"
    />

    <V2Dialog
      v-model:open="editorOpen"
      title="新建采购申请"
      description="填写基本信息与明细后一次提交，保存后刷新数量、金额与状态。"
      :close-disabled="busy"
      :close-on-backdrop="false"
      panel-class="v2-dialog-wide"
    >
      <form
        id="purchase-request-editor-form"
        class="purchase-execution-page__form"
        @submit.prevent="save"
      >
        <V2Select
          v-model="form.projectId"
          label="项目"
          :options="workspace.projects"
          :disabled="busy"
          required
          @update:model-value="changeProject"
        />
        <section
          class="purchase-execution-page__draft-lines"
          aria-labelledby="purchase-request-lines-title"
        >
          <div class="purchase-execution-page__draft-heading">
            <h3 id="purchase-request-lines-title">采购申请明细</h3>
            <div class="purchase-execution-page__draft-actions">
              <MaterialSearchPicker
                v-if="!selfOnly"
                :disabled="busy || drafts.length >= 200"
                @select="addMaterial"
              />
              <V2Button
                type="button"
                size="small"
                variant="secondary"
                :disabled="busy || drafts.length >= 200"
                @click="addItem"
                >添加明细</V2Button
              >
            </div>
          </div>
          <div class="purchase-execution-page__draft-table-wrap">
            <table class="purchase-execution-page__draft-table">
              <thead>
                <tr>
                  <th scope="col">物料编码/名称<span aria-hidden="true">*</span></th>
                  <th scope="col">申请数量<span aria-hidden="true">*</span></th>
                  <th scope="col">单位<span aria-hidden="true">*</span></th>
                  <th scope="col">计划日期<span aria-hidden="true">*</span></th>
                  <th scope="col">使用部位<span aria-hidden="true">*</span></th>
                  <th scope="col">备注</th>
                  <th scope="col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in drafts" :key="index">
                  <td>
                    <V2Select
                      v-model="item.materialId"
                      :label="`第${index + 1}条物料`"
                      hide-label
                      :options="materialOptions"
                      :disabled="busy"
                      required
                      @update:model-value="selectMaterial(index, $event)"
                    />
                  </td>
                  <td>
                    <V2Input
                      v-model="item.quantity"
                      :label="`第${index + 1}条申请数量`"
                      hide-label
                      :decimal-scale="2"
                      required
                    />
                  </td>
                  <td>
                    <V2Input
                      v-model="item.unit"
                      :label="`第${index + 1}条单位`"
                      hide-label
                      required
                    />
                  </td>
                  <td>
                    <V2Input
                      v-model="item.plannedDate"
                      :label="`第${index + 1}条计划日期`"
                      hide-label
                      placeholder="YYYY-MM-DD"
                      required
                    />
                  </td>
                  <td>
                    <V2Input
                      v-model="item.useLocation"
                      :label="`第${index + 1}条使用部位`"
                      hide-label
                      required
                    />
                  </td>
                  <td>
                    <V2Input v-model="item.remark" :label="`第${index + 1}条备注`" hide-label />
                  </td>
                  <td>
                    <V2Button
                      type="button"
                      size="small"
                      variant="ghost"
                      :disabled="busy || drafts.length <= 1"
                      :aria-label="`删除第${index + 1}条明细`"
                      @click="removeItem(index)"
                      >删除</V2Button
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <V2Input v-model="form.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="busy" @click="editorOpen = false">取消</V2Button>
        <V2Button type="submit" form="purchase-request-editor-form" :loading="busy">保存</V2Button>
      </template>
    </V2Dialog>
  </section>
</template>

<style src="./purchase-execution.css"></style>
