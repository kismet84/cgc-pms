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
  pageSize: 20,
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
const successMessage = ref('')
const selected = ref<BidCostRecord | null>(null)
const panelMode = ref<PanelMode>('closed')
const form = reactive({ bidProjectName: '', remark: '' })
const pendingAction = ref<PendingAction>(null)
const projects = ref<ProjectContextOption[]>([])
const wonProjectId = ref('')

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
}

function closePanel(): void {
  detailController?.abort()
  selected.value = null
  panelMode.value = 'closed'
}

async function save(): Promise<void> {
  if (actionBusy.value) return
  const bidProjectName = form.bidProjectName.trim()
  if (!bidProjectName) {
    errorMessage.value = '投标项目名称不能为空'
    return
  }
  actionBusy.value = true
  errorMessage.value = ''
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
    errorMessage.value = errorText(error, '投标成本保存失败')
    if (selected.value) await openDetail(selected.value.id, 'detail', true)
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
  projectController?.abort()
  const controller = new AbortController()
  projectController = controller
  errorMessage.value = ''
  try {
    projects.value = await loadProjectContextOptions(controller.signal)
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

async function confirmAction(): Promise<void> {
  const action = pendingAction.value
  const record = selected.value
  if (!action || !record || actionBusy.value) return
  if (action === 'won' && !wonProjectId.value) {
    errorMessage.value = '中标项目不能为空'
    return
  }
  actionBusy.value = true
  errorMessage.value = ''
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

function projectLabel(projectId?: string | null): string {
  return projects.value.find((project) => project.id === projectId)?.projectName ?? projectId ?? '—'
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
      <V2Alert
        v-if="successMessage"
        tone="success"
        title="操作成功"
        dismissible
        @dismiss="successMessage = ''"
      >
        {{ successMessage }}
      </V2Alert>

      <V2Card title="投标成本" :heading-level="1">
        <template #actions>
          <V2Button v-if="canAdd" @click="openCreate">新建投标成本</V2Button>
        </template>
        <div class="bid-cost-page__filters">
          <V2Input
            v-model="filter.keyword"
            label="关键词"
            placeholder="投标项目名称"
            @keyup.enter="query"
          />
          <V2Select v-model="filter.bidStatus" label="状态" :options="STATUS_OPTIONS" allow-empty />
          <V2Button :loading="loading" @click="query">查询</V2Button>
        </div>
      </V2Card>

      <V2PageState
        v-if="loading && records.length === 0"
        title="正在加载投标成本"
        description="正在读取当前筛选结果。"
        kind="loading"
      />
      <V2PageState
        v-else-if="!loading && records.length === 0"
        title="暂无投标成本"
        description="当前筛选条件下没有可访问记录。"
        kind="empty"
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
                <th scope="col">投标项目</th>
                <th scope="col">关联项目</th>
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
                <td>{{ projectLabel(record.projectId) }}</td>
                <td>
                  <V2Badge :tone="statusTone(record.bidStatus)" dot>{{
                    statusLabel(record.bidStatus)
                  }}</V2Badge>
                </td>
                <td>{{ record.remark || '—' }}</td>
                <td>{{ record.updatedAt || '—' }}</td>
                <td>
                  <div class="bid-cost-page__actions">
                    <V2Button size="small" variant="secondary" @click="openDetail(record.id)"
                      >详情</V2Button
                    >
                    <V2Button
                      v-if="canEdit && record.bidStatus === 'BIDDING'"
                      size="small"
                      variant="ghost"
                      @click="openDetail(record.id, 'edit')"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="canChangeStatus && record.bidStatus === 'BIDDING'"
                      size="small"
                      @click="requestWon(record)"
                      >标记中标</V2Button
                    >
                    <V2Button
                      v-if="canChangeStatus && record.bidStatus === 'BIDDING'"
                      size="small"
                      variant="secondary"
                      @click="requestLost(record)"
                      >标记未中标</V2Button
                    >
                    <V2Button
                      v-if="canDelete && record.bidStatus === 'BIDDING'"
                      size="small"
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
              : '投标成本详情'
        "
        panel-class="v2-dialog-standard v2-detail-dialog"
        :close-on-backdrop="false"
        :close-disabled="actionBusy"
        @close="closePanel"
      >
        <V2PageState
          v-if="detailLoading"
          title="正在加载投标详情"
          description="请稍候。"
          kind="loading"
        />
        <div v-else-if="panelMode === 'detail' && selected" class="bid-cost-page__detail">
          <dl class="v2-detail-dialog__facts">
            <dt>投标项目</dt>
            <dd>{{ selected.bidProjectName }}</dd>
            <dt>状态</dt>
            <dd>
              <V2Badge :tone="statusTone(selected.bidStatus)">{{
                statusLabel(selected.bidStatus)
              }}</V2Badge>
            </dd>
            <dt>关联项目</dt>
            <dd>{{ projectLabel(selected.projectId) }}</dd>
            <dt>备注</dt>
            <dd>{{ selected.remark || '—' }}</dd>
            <dt>创建时间</dt>
            <dd>{{ selected.createdAt || '—' }}</dd>
            <dt>更新时间</dt>
            <dd>{{ selected.updatedAt || '—' }}</dd>
          </dl>
          <div class="bid-cost-page__actions">
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
        <form v-else class="bid-cost-page__form" @submit.prevent="save">
          <V2Input
            v-model="form.bidProjectName"
            label="投标项目名称"
            required
            :disabled="actionBusy"
          />
          <label class="bid-cost-page__native-field">
            <span>备注</span>
            <textarea v-model="form.remark" maxlength="500" :disabled="actionBusy"></textarea>
          </label>
          <div class="bid-cost-page__actions">
            <V2Button type="submit" :loading="actionBusy">{{
              panelMode === 'create' ? '创建' : '保存变更'
            }}</V2Button>
            <V2GlassButton text="取消" :disabled="actionBusy" :on-click="closePanel" />
          </div>
        </form>
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
      @close="pendingAction = null"
      @confirm="confirmAction"
    >
      <V2Select
        v-if="pendingAction === 'won'"
        v-model="wonProjectId"
        label="中标关联项目"
        :options="projectOptions"
        required
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

.bid-cost-page__table-wrap {
  min-width: 0;
  overflow-x: auto;
}

.bid-cost-page__table {
  width: 100%;
  min-width: 64rem;
  border-collapse: collapse;
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}

.bid-cost-page__table th,
.bid-cost-page__table td {
  padding: var(--v2-space-3);
  border-bottom: 1px solid var(--v2-color-border-subtle);
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
}

.bid-cost-page__table th {
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface-subtle);
  font-weight: var(--v2-font-weight-semibold);
}

.bid-cost-page__table .bid-cost-page__actions {
  flex-wrap: nowrap;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}

dt,
.bid-cost-page__native-field {
  color: var(--v2-color-text-secondary);
}

dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.bid-cost-page__native-field {
  display: grid;
  gap: var(--v2-space-1);
}

.bid-cost-page__native-field textarea {
  min-height: 6rem;
  padding: var(--v2-space-2) var(--v2-space-3);
  color: var(--v2-color-text);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  resize: vertical;
}

@media (max-width: 48rem) {
  .bid-cost-page__filters {
    grid-template-columns: 1fr;
  }
}
</style>
