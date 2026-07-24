<script setup lang="ts">
import type { BidCostRecord, BidStatus, ProjectContextOption } from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2GlassButton,
  V2Input,
  V2PageState,
  V2Select,
  useToastMessage,
} from '@/components'
import {
  createBidCost,
  deleteBidCost,
  loadBidCost,
  loadBidCostPage,
  loadProjectContextOptions,
  markBidCostLost,
  markBidCostWon,
  updateBidCost,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'

type PanelMode = 'closed' | 'detail' | 'create' | 'edit'
type PendingAction = 'delete' | 'won' | 'lost' | null

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'BIDDING', label: '投标中' },
  { value: 'WON', label: '已中标' },
  { value: 'LOST', label: '未中标' },
]

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const filter = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  bidStatus: '',
  projectId: '',
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
})
const records = ref<BidCostRecord[]>([])
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const panelErrorMessage = ref('')
const bidProjectNameError = ref('')
const selected = ref<BidCostRecord | null>(null)
const panelMode = ref<PanelMode>('closed')
const form = reactive({ bidProjectName: '', remark: '' })
const pendingAction = ref<PendingAction>(null)
const projects = ref<ProjectContextOption[]>([])
const wonProjectId = ref('')
const wonProjectError = ref('')

let listGeneration = 0
let detailGeneration = 0
let listController: AbortController | null = null
let detailController: AbortController | null = null
let projectController: AbortController | null = null

const canQuery = computed(() => session.hasPermission('bid:query'))
const canAdd = computed(() => session.hasPermission('bid:add'))
const canEdit = computed(() => session.hasPermission('bid:edit'))
const canDelete = computed(() => session.hasPermission('bid:delete'))
const canChangeStatus = computed(() => session.hasPermission('bid:status'))
const selectedIsBidding = computed(() => selected.value?.bidStatus === 'BIDDING')
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / filter.pageSize)))
const projectOptions = computed(() =>
  projects.value.map((project) => ({ value: project.id, label: project.projectName })),
)

function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

function hydrateFilter(): void {
  filter.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  filter.bidStatus = typeof route.query.bidStatus === 'string' ? route.query.bidStatus : ''
  filter.projectId = typeof route.query.projectId === 'string' ? route.query.projectId : ''
  const period =
    typeof route.query.period === 'string' ? reportPeriodBounds(route.query.period) : null
  filter.startDate = period?.startDate
  filter.endDate = period?.endDate
  const pageNo = typeof route.query.pageNo === 'string' ? Number(route.query.pageNo) : 1
  filter.pageNo = Number.isInteger(pageNo) && pageNo > 0 ? pageNo : 1
}

async function replaceQuery(): Promise<boolean> {
  const location = {
    path: '/bid-cost',
    query: {
      ...(filter.keyword.trim() ? { keyword: filter.keyword.trim() } : {}),
      ...(filter.bidStatus ? { bidStatus: filter.bidStatus } : {}),
      ...(filter.projectId ? { projectId: filter.projectId } : {}),
      ...(typeof route.query.period === 'string' ? { period: route.query.period } : {}),
      ...(filter.pageNo > 1 ? { pageNo: String(filter.pageNo) } : {}),
    },
    hash: route.hash,
  }
  if (router.resolve(location).fullPath === route.fullPath) return false
  await router.replace(location)
  return true
}

async function loadList(preserveNotice = false): Promise<void> {
  if (!canQuery.value) return
  hydrateFilter()
  listController?.abort()
  const controller = new AbortController()
  listController = controller
  const generation = ++listGeneration
  loading.value = true
  if (!preserveNotice) {
    errorMessage.value = ''
    successMessage.value = ''
  }
  try {
    const page = await loadBidCostPage(
      {
        pageNo: filter.pageNo,
        pageSize: filter.pageSize,
        keyword: filter.keyword,
        bidStatus: filter.bidStatus || undefined,
        projectId: filter.projectId || undefined,
        startDate: filter.startDate,
        endDate: filter.endDate,
      },
      controller.signal,
    )
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = page.total
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, '投标成本加载失败')
    }
  } finally {
    if (generation === listGeneration) loading.value = false
  }
}

async function query(): Promise<void> {
  filter.pageNo = 1
  if (!(await replaceQuery())) await loadList()
}

function changeStatus(value: string): void {
  filter.bidStatus = value
  void query()
}

async function changePage(nextPage: number): Promise<void> {
  if (nextPage < 1 || nextPage > pageCount.value || loading.value) return
  filter.pageNo = nextPage
  if (!(await replaceQuery())) await loadList()
}

async function openDetail(
  id: string,
  mode: 'detail' | 'edit' = 'detail',
  preserveNotice = false,
): Promise<void> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const generation = ++detailGeneration
  detailLoading.value = true
  if (!preserveNotice) errorMessage.value = ''
  try {
    const value = await loadBidCost(id, controller.signal)
    if (generation !== detailGeneration) return
    selected.value = value
    panelMode.value = mode
    form.bidProjectName = value.bidProjectName
    form.remark = value.remark ?? ''
    panelErrorMessage.value = ''
    bidProjectNameError.value = ''
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      selected.value = null
      panelMode.value = 'closed'
      errorMessage.value = errorText(error, '投标成本详情加载失败')
    }
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

function openCreate(): void {
  selected.value = null
  form.bidProjectName = ''
  form.remark = ''
  panelMode.value = 'create'
  errorMessage.value = ''
  panelErrorMessage.value = ''
  bidProjectNameError.value = ''
}

function closePanel(): void {
  detailController?.abort()
  selected.value = null
  panelMode.value = 'closed'
  panelErrorMessage.value = ''
  bidProjectNameError.value = ''
}

async function save(): Promise<void> {
  if (actionBusy.value) return
  const bidProjectName = form.bidProjectName.trim()
  if (!bidProjectName) {
    bidProjectNameError.value = '投标项目名称不能为空'
    return
  }
  actionBusy.value = true
  panelErrorMessage.value = ''
  bidProjectNameError.value = ''
  successMessage.value = ''
  const command = { bidProjectName, remark: form.remark.trim() || null }
  try {
    if (panelMode.value === 'create') {
      const id = await createBidCost(command)
      await loadList(true)
      await openDetail(id)
      successMessage.value = '投标成本已创建，并已刷新最新数据。'
    } else if (selected.value) {
      const id = selected.value.id
      await updateBidCost(id, command)
      await loadList(true)
      await openDetail(id)
      successMessage.value = '投标成本已保存，并已刷新最新数据。'
    }
  } catch (error) {
    panelErrorMessage.value = errorText(error, '投标成本保存失败')
  } finally {
    actionBusy.value = false
  }
}

function requestDelete(record: BidCostRecord): void {
  selected.value = record
  pendingAction.value = 'delete'
}

async function requestWon(record: BidCostRecord): Promise<void> {
  selected.value = record
  wonProjectId.value = ''
  wonProjectError.value = ''
  projectController?.abort()
  const controller = new AbortController()
  projectController = controller
  errorMessage.value = ''
  try {
    if (!projects.value.length) {
      projects.value = await loadProjectContextOptions(controller.signal)
    }
    if (projectController !== controller) return
    pendingAction.value = 'won'
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = errorText(error, '可见项目加载失败')
  } finally {
    if (projectController === controller) projectController = null
  }
}

function requestLost(record: BidCostRecord): void {
  selected.value = record
  pendingAction.value = 'lost'
}

function closeConfirmation(): void {
  pendingAction.value = null
  wonProjectError.value = ''
}

async function confirmAction(): Promise<void> {
  const action = pendingAction.value
  const record = selected.value
  if (!action || !record || actionBusy.value) return
  if (action === 'won' && !wonProjectId.value) {
    wonProjectError.value = '中标项目不能为空'
    return
  }
  actionBusy.value = true
  errorMessage.value = ''
  wonProjectError.value = ''
  successMessage.value = ''
  try {
    if (action === 'delete') await deleteBidCost(record.id)
    if (action === 'won') await markBidCostWon(record.id, wonProjectId.value)
    if (action === 'lost') await markBidCostLost(record.id)
    pendingAction.value = null
    closePanel()
    await loadList(true)
    successMessage.value =
      action === 'delete' ? '投标成本已删除。' : '投标状态已更新，并已刷新最新数据。'
  } catch (error) {
    errorMessage.value = errorText(error, action === 'delete' ? '投标成本删除失败' : '状态更新失败')
    pendingAction.value = null
    await loadList(true)
    if (panelMode.value !== 'closed') await openDetail(record.id, 'detail', true)
  } finally {
    actionBusy.value = false
  }
}

function statusLabel(status: BidStatus): string {
  return status === 'WON' ? '已中标' : status === 'LOST' ? '未中标' : '投标中'
}

function statusTone(status: BidStatus): 'info' | 'success' | 'neutral' {
  return status === 'WON' ? 'success' : status === 'LOST' ? 'neutral' : 'info'
}

watch(
  () => route.fullPath,
  () => void loadList(),
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
  projectController?.abort()
})
</script>

<template>
  <div class="bid-cost-page">
    <V2PageState
      v-if="!canQuery"
      code="403"
      title="无权访问投标成本"
      description="当前账号没有访问权限，页面未加载业务数据。"
      kind="error"
      :heading-level="1"
    />

    <template v-else>
      <V2Alert
        v-if="errorMessage"
        tone="danger"
        title="操作失败"
        dismissible
        @dismiss="errorMessage = ''"
      >
        {{ errorMessage }}
      </V2Alert>
      <V2Card title="投标成本" :heading-level="1">
        <template #actions>
          <V2Button v-if="canAdd" @click="openCreate">新建投标成本</V2Button>
        </template>
        <div class="bid-cost-page__filters">
          <V2Input
            v-model="filter.keyword"
            type="search"
            label="关键词"
            hide-label
            placeholder="输入投标项目名称"
            @keyup.enter="query"
          />
          <V2Select
            :model-value="filter.bidStatus"
            label="状态"
            hide-label
            :options="STATUS_OPTIONS"
            allow-empty
            placeholder="全部状态"
            @update:model-value="changeStatus"
          />
          <V2Button :loading="loading" @click="query">查询</V2Button>
        </div>
      </V2Card>

      <V2PageState
        v-if="loading && records.length === 0"
        title="正在加载投标成本"
        description="正在读取当前筛选结果。"
        kind="loading"
        :heading-level="2"
      />
      <V2PageState
        v-else-if="!loading && records.length === 0"
        title="暂无投标成本"
        description="当前筛选条件下没有可访问记录。"
        kind="empty"
        :heading-level="2"
      />

      <V2Card v-else title="投标成本列表">
        <div
          class="bid-cost-page__table-wrap"
          role="region"
          aria-label="投标成本列表"
          :aria-busy="loading"
          tabindex="0"
        >
          <table class="bid-cost-page__table">
            <caption class="v2-visually-hidden">
              投标成本列表
            </caption>
            <thead>
              <tr>
                <th scope="col">项目名称</th>
                <th scope="col">状态</th>
                <th scope="col">备注</th>
                <th scope="col">更新时间</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in records" :key="record.id">
                <td>
                  <strong>{{ record.bidProjectName }}</strong>
                </td>
                <td>
                  <V2Badge :tone="statusTone(record.bidStatus)" dot>{{
                    statusLabel(record.bidStatus)
                  }}</V2Badge>
                </td>
                <td>{{ record.remark || '—' }}</td>
                <td>{{ record.updatedAt || '—' }}</td>
                <td>
                  <div class="bid-cost-page__actions">
                    <V2Button variant="secondary" @click="openDetail(record.id)">预览</V2Button>
                    <V2Button
                      v-if="canEdit && record.bidStatus === 'BIDDING'"
                      variant="ghost"
                      @click="openDetail(record.id, 'edit')"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="canChangeStatus && record.bidStatus === 'BIDDING'"
                      @click="requestWon(record)"
                      >标记中标</V2Button
                    >
                    <V2Button
                      v-if="canChangeStatus && record.bidStatus === 'BIDDING'"
                      variant="secondary"
                      @click="requestLost(record)"
                      >标记未中标</V2Button
                    >
                    <V2Button
                      v-if="canDelete && record.bidStatus === 'BIDDING'"
                      variant="danger"
                      @click="requestDelete(record)"
                      >删除</V2Button
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>

      <nav v-if="records.length" class="bid-cost-page__pagination" aria-label="投标成本分页">
        <span>共 {{ total }} 条</span>
        <V2Button
          variant="secondary"
          :disabled="filter.pageNo <= 1"
          @click="changePage(filter.pageNo - 1)"
          >上一页</V2Button
        >
        <span>第 {{ filter.pageNo }} 页</span>
        <V2Button
          variant="secondary"
          :disabled="filter.pageNo >= pageCount"
          @click="changePage(filter.pageNo + 1)"
          >下一页</V2Button
        >
      </nav>

      <V2Dialog
        :open="detailLoading || panelMode !== 'closed'"
        :title="
          panelMode === 'create'
            ? '新建投标成本'
            : panelMode === 'edit'
              ? '编辑投标成本'
              : '投标成本预览'
        "
        :panel-class="panelMode === 'detail' ? 'v2-detail-dialog' : undefined"
        :close-on-backdrop="false"
        :close-disabled="actionBusy"
        @close="closePanel"
      >
        <V2Alert v-if="panelErrorMessage && !detailLoading" tone="danger" title="保存失败">
          {{ panelErrorMessage }}
        </V2Alert>
        <V2PageState
          v-if="detailLoading"
          title="正在加载投标详情"
          description="请稍候。"
          kind="loading"
          :heading-level="2"
        />
        <div v-else-if="panelMode === 'detail' && selected" class="bid-cost-page__detail">
          <dl class="v2-detail-dialog__facts">
            <dt>项目名称</dt>
            <dd>{{ selected.bidProjectName }}</dd>
            <dt>状态</dt>
            <dd>
              <V2Badge :tone="statusTone(selected.bidStatus)">{{
                statusLabel(selected.bidStatus)
              }}</V2Badge>
            </dd>
            <dt>备注</dt>
            <dd>{{ selected.remark || '—' }}</dd>
            <dt>更新时间</dt>
            <dd>{{ selected.updatedAt || '—' }}</dd>
          </dl>
          <div class="v2-detail-dialog__quick-actions">
            <V2GlassButton
              v-if="canEdit && selectedIsBidding"
              text="编辑"
              :on-click="() => (panelMode = 'edit')"
            />
            <V2GlassButton
              v-if="canChangeStatus && selectedIsBidding"
              text="标记中标"
              :on-click="() => requestWon(selected)"
            />
            <V2GlassButton
              v-if="canChangeStatus && selectedIsBidding"
              text="标记未中标"
              :on-click="() => requestLost(selected)"
            />
            <V2GlassButton
              v-if="canDelete && selectedIsBidding"
              text="删除"
              :on-click="() => requestDelete(selected)"
            />
          </div>
        </div>
        <form
          v-else
          id="bid-cost-form"
          class="bid-cost-page__form"
          novalidate
          @submit.prevent="save"
        >
          <V2Input
            v-model="form.bidProjectName"
            label="投标项目名称"
            required
            :disabled="actionBusy"
            :error="bidProjectNameError"
          />
          <label class="bid-cost-page__native-field">
            <span>备注</span>
            <textarea v-model="form.remark" maxlength="500" :disabled="actionBusy"></textarea>
          </label>
        </form>
        <template v-if="panelMode === 'create' || panelMode === 'edit'" #footer>
          <V2GlassButton text="取消" :disabled="actionBusy" :on-click="closePanel" />
          <V2GlassButton
            :text="panelMode === 'create' ? '创建' : '保存变更'"
            type="submit"
            form="bid-cost-form"
            :loading="actionBusy"
          />
        </template>
      </V2Dialog>
    </template>

    <V2ConfirmDialog
      :open="pendingAction !== null"
      :title="
        pendingAction === 'delete'
          ? '删除投标成本'
          : pendingAction === 'won'
            ? '确认中标'
            : '确认未中标'
      "
      :description="
        pendingAction === 'delete'
          ? '仅投标中记录可删除，此操作不可撤销。'
          : '状态更新后不可在此页面回退。'
      "
      :confirm-text="pendingAction === 'delete' ? '确认删除' : '确认更新'"
      :danger="pendingAction === 'delete' || pendingAction === 'lost'"
      :loading="actionBusy"
      @close="closeConfirmation"
      @confirm="confirmAction"
    >
      <V2Select
        v-if="pendingAction === 'won'"
        v-model="wonProjectId"
        label="中标关联项目"
        :options="projectOptions"
        required
        :error="wonProjectError"
        @update:model-value="wonProjectError = ''"
      />
    </V2ConfirmDialog>
  </div>
</template>

<style scoped>
.bid-cost-page,
.bid-cost-page__form,
.bid-cost-page__detail {
  display: grid;
  gap: var(--v2-space-4);
}

.bid-cost-page__filters {
  display: grid;
  grid-template-columns: minmax(12rem, 1fr) minmax(10rem, 16rem) auto;
  gap: var(--v2-space-3);
  align-items: end;
}

.bid-cost-page__actions,
.bid-cost-page__pagination {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}

.bid-cost-page__pagination {
  justify-content: flex-end;
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}

.bid-cost-page__table {
  min-width: 64rem;
}

.bid-cost-page__table .bid-cost-page__actions {
  flex-wrap: nowrap;
}

.bid-cost-page__native-field {
  display: grid;
  gap: var(--v2-space-1);
}

.bid-cost-page__native-field textarea {
  min-height: var(--v2-control-height-textarea);
  resize: vertical;
}

@media (max-width: 48rem) {
  .bid-cost-page__filters {
    grid-template-columns: 1fr;
  }
}
</style>
