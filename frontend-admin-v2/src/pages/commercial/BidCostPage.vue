<script setup lang="ts">
import type { BidCostRecord, BidStatus } from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  V2Alert,
  V2ActionMenu,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  showToast,
  useToastMessage,
} from '@/components'
import {
  createBidCost,
  deleteBidCost,
  loadBidCost,
  loadBidOwnerOptions,
  loadBidCostPage,
  updateBidCost,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { formatAmount } from '@/shared/display'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'

type PanelMode = 'closed' | 'detail' | 'create' | 'edit'
type PendingAction = 'delete' | null

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'PREPARING', label: '注册' },
  { value: 'SUBMITTED', label: '投标' },
  { value: 'EVALUATING', label: '评标' },
  { value: 'WON', label: '中标' },
  { value: 'LOST', label: '未中标' },
]
const RESULT_OPTIONS = [
  { value: '', label: '全部结果' },
  { value: 'WON', label: '中标' },
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
  result: '',
  ownerId: '',
  deadlineFrom: '',
  deadlineTo: '',
  projectId: '',
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
})
const records = ref<BidCostRecord[]>([])
const ownerOptions = ref<Array<{ value: string; label: string; disabled?: boolean }>>([])
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
const form = reactive({
  bidProjectName: '',
  bidSectionName: '',
  tendereeName: '',
  agencyName: '',
  projectLocation: '',
  tenderMethod: '',
  sourcePlatform: '',
  externalBidNo: '',
  sourceUrl: '',
  ownerId: '',
  documentReceivedDate: '',
  bidDeadlineAt: '',
  openingAt: '',
  bidValidUntil: '',
  plannedStartDate: '',
  plannedEndDate: '',
  ceilingPrice: '',
  finalBidPrice: '',
  remark: '',
})
const pendingAction = ref<PendingAction>(null)

watch(errorMessage, (message) => {
  if (message) showToast('error', '操作未完成', message)
})

let listGeneration = 0
let detailGeneration = 0
let listController: AbortController | null = null
let detailController: AbortController | null = null

const canQuery = computed(() => session.hasPermission('bid:query'))
const canAdd = computed(() => session.hasPermission('bid:add'))
const canEdit = computed(() => session.hasPermission('bid:edit'))
const canDelete = computed(() => session.hasPermission('bid:delete'))
const selectedIsBidding = computed(() => selected.value?.bidStatus === 'PREPARING')
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / filter.pageSize)))

function errorText(error: unknown, fallback: string): string {
  return isApiClientError(error) ? error.message : fallback
}

const nullable = (value: string) => value.trim() || null
const datetimeLocal = (value?: string | null) => (value ? value.replace(' ', 'T').slice(0, 16) : '')
function fillForm(value?: BidCostRecord): void {
  if (value?.ownerId && !ownerOptions.value.some((option) => option.value === value.ownerId)) {
    ownerOptions.value.push({
      value: value.ownerId,
      label: `${value.ownerName || value.ownerId}（历史负责人）`,
      disabled: true,
    })
  }
  Object.assign(form, {
    bidProjectName: value?.bidProjectName ?? '',
    bidSectionName: value?.bidSectionName ?? '',
    tendereeName: value?.tendereeName ?? '',
    agencyName: value?.agencyName ?? '',
    projectLocation: value?.projectLocation ?? '',
    tenderMethod: value?.tenderMethod ?? '',
    sourcePlatform: value?.sourcePlatform ?? '',
    externalBidNo: value?.externalBidNo ?? '',
    sourceUrl: value?.sourceUrl ?? '',
    ownerId: value?.ownerId ?? '',
    documentReceivedDate: value?.documentReceivedDate ?? '',
    bidDeadlineAt: datetimeLocal(value?.bidDeadlineAt),
    openingAt: datetimeLocal(value?.openingAt),
    bidValidUntil: value?.bidValidUntil ?? '',
    plannedStartDate: value?.plannedStartDate ?? '',
    plannedEndDate: value?.plannedEndDate ?? '',
    ceilingPrice: value?.ceilingPrice ?? '',
    finalBidPrice: value?.finalBidPrice ?? '',
    remark: value?.remark ?? '',
  })
}

function deadlineEndExclusive(value: string): string | undefined {
  if (!value) return undefined
  const date = new Date(`${value}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + 1)
  return `${date.toISOString().slice(0, 10)}T00:00:00`
}

function hydrateFilter(): void {
  filter.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  filter.bidStatus = typeof route.query.bidStatus === 'string' ? route.query.bidStatus : ''
  filter.result = typeof route.query.result === 'string' ? route.query.result : ''
  filter.ownerId = typeof route.query.ownerId === 'string' ? route.query.ownerId : ''
  filter.deadlineFrom = typeof route.query.deadlineFrom === 'string' ? route.query.deadlineFrom : ''
  filter.deadlineTo = typeof route.query.deadlineTo === 'string' ? route.query.deadlineTo : ''
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
    path: '/engineering-tender/records',
    query: {
      ...(filter.keyword.trim() ? { keyword: filter.keyword.trim() } : {}),
      ...(filter.bidStatus ? { bidStatus: filter.bidStatus } : {}),
      ...(filter.result ? { result: filter.result } : {}),
      ...(filter.ownerId ? { ownerId: filter.ownerId } : {}),
      ...(filter.deadlineFrom ? { deadlineFrom: filter.deadlineFrom } : {}),
      ...(filter.deadlineTo ? { deadlineTo: filter.deadlineTo } : {}),
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
    const [page, users] = await Promise.all([
      loadBidCostPage(
        {
          pageNo: filter.pageNo,
          pageSize: filter.pageSize,
          keyword: filter.keyword,
          bidStatus: (filter.bidStatus || undefined) as BidStatus | undefined,
          result: filter.result || undefined,
          ownerId: filter.ownerId || undefined,
          deadlineFrom: filter.deadlineFrom ? `${filter.deadlineFrom}T00:00:00` : undefined,
          deadlineTo: deadlineEndExclusive(filter.deadlineTo),
          projectId: filter.projectId || undefined,
          startDate: filter.startDate,
          endDate: filter.endDate,
        },
        controller.signal,
      ),
      loadBidOwnerOptions(controller.signal),
    ])
    if (generation !== listGeneration) return
    records.value = page.records
    total.value = page.total
    ownerOptions.value = [
      { value: '', label: '全部负责人' },
      ...users.map((user) => ({
        value: user.ownerId,
        label: user.ownerName ?? user.ownerId,
      })),
    ]
  } catch (error) {
    if (!controller.signal.aborted && generation === listGeneration) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, '投标记录加载失败')
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
  panelMode.value = mode
  detailLoading.value = true
  if (!preserveNotice) errorMessage.value = ''
  try {
    const value = await loadBidCost(id, controller.signal)
    if (generation !== detailGeneration) return
    selected.value = value
    fillForm(value)
    panelErrorMessage.value = ''
    bidProjectNameError.value = ''
  } catch (error) {
    if (!controller.signal.aborted && generation === detailGeneration) {
      selected.value = null
      panelMode.value = 'closed'
      errorMessage.value = errorText(error, '投标记录详情加载失败')
    }
  } finally {
    if (generation === detailGeneration) detailLoading.value = false
  }
}

function openCreate(): void {
  selected.value = null
  fillForm()
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
  const command = {
    bidProjectName,
    bidSectionName: nullable(form.bidSectionName),
    tendereeName: nullable(form.tendereeName),
    agencyName: nullable(form.agencyName),
    projectLocation: nullable(form.projectLocation),
    tenderMethod: nullable(form.tenderMethod),
    sourcePlatform: nullable(form.sourcePlatform),
    externalBidNo: nullable(form.externalBidNo),
    sourceUrl: nullable(form.sourceUrl),
    ownerId: nullable(form.ownerId),
    documentReceivedDate: nullable(form.documentReceivedDate),
    bidDeadlineAt: nullable(form.bidDeadlineAt),
    openingAt: nullable(form.openingAt),
    bidValidUntil: nullable(form.bidValidUntil),
    plannedStartDate: nullable(form.plannedStartDate),
    plannedEndDate: nullable(form.plannedEndDate),
    ceilingPrice: nullable(form.ceilingPrice),
    finalBidPrice: nullable(form.finalBidPrice),
    remark: nullable(form.remark),
  }
  try {
    if (panelMode.value === 'create') {
      const id = await createBidCost(command)
      await loadList(true)
      await openDetail(id)
      successMessage.value = '投标记录已创建，并已刷新最新数据。'
    } else if (selected.value) {
      const id = selected.value.id
      await updateBidCost(id, command)
      await loadList(true)
      await openDetail(id)
      successMessage.value = '投标记录已保存，并已刷新最新数据。'
    }
  } catch (error) {
    panelErrorMessage.value = errorText(error, '投标记录保存失败')
  } finally {
    actionBusy.value = false
  }
}

function requestDelete(record: BidCostRecord): void {
  selected.value = record
  pendingAction.value = 'delete'
}

function closeConfirmation(): void {
  pendingAction.value = null
}

async function confirmAction(): Promise<void> {
  const action = pendingAction.value
  const record = selected.value
  if (!action || !record || actionBusy.value) return
  actionBusy.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await deleteBidCost(record.id)
    pendingAction.value = null
    closePanel()
    await loadList(true)
    successMessage.value = '投标记录已删除。'
  } catch (error) {
    errorMessage.value = errorText(error, '投标记录删除失败')
    pendingAction.value = null
    await loadList(true)
    if (panelMode.value !== 'closed') await openDetail(record.id, 'detail', true)
  } finally {
    actionBusy.value = false
  }
}

function statusLabel(status: BidStatus): string {
  return {
    PREPARING: '注册',
    SUBMITTED: '投标',
    EVALUATING: '评标',
    WON: '中标',
    LOST: '未中标',
    CLOSED: '已关闭',
    WITHDRAWN: '已撤回',
    TERMINATED: '已终止',
  }[status]
}

function statusTone(status: BidStatus): 'info' | 'success' | 'neutral' {
  return status === 'WON' ? 'success' : status === 'LOST' ? 'neutral' : 'info'
}

function resultLabel(record: BidCostRecord): string {
  return record.bidStatus === 'WON' ? '中标' : record.bidStatus === 'LOST' ? '未中标' : '—'
}

watch(
  () => route.fullPath,
  () => void loadList(),
  { immediate: true },
)

onBeforeUnmount(() => {
  listController?.abort()
  detailController?.abort()
})
</script>

<template>
  <div class="bid-cost-page">
    <V2PageState
      v-if="!canQuery"
      code="403"
      title="无权访问投标记录"
      description="当前账号没有访问权限，页面未加载业务数据。"
      kind="error"
      :heading-level="1"
    />

    <template v-else>
      <V2Card title="投标记录" :heading-level="1">
        <template #actions>
          <V2Button v-if="canAdd" size="small" @click="openCreate">新建投标记录</V2Button>
        </template>
      </V2Card>
      <V2Card>
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
          <V2Select
            v-model="filter.result"
            label="投标结果"
            :options="RESULT_OPTIONS"
            allow-empty
          />
          <V2Select
            v-model="filter.ownerId"
            label="投标负责人"
            :options="ownerOptions"
            allow-empty
          />
          <V2Input v-model="filter.deadlineFrom" type="date" label="截止日期起" />
          <V2Input v-model="filter.deadlineTo" type="date" label="截止日期止" />
          <V2Button size="small" :loading="loading" @click="query">查询</V2Button>
        </div>
      </V2Card>

      <V2PageState
        v-if="loading && records.length === 0"
        title="正在加载投标记录"
        description="正在读取当前筛选结果。"
        kind="loading"
        :heading-level="2"
      />
      <V2PageState
        v-else-if="!loading && records.length === 0 && !errorMessage"
        title="暂无投标记录"
        description="当前筛选条件下没有可访问记录。"
        kind="empty"
        :heading-level="2"
      />

      <V2Card v-else-if="records.length">
        <div
          class="bid-cost-page__table-wrap"
          role="region"
          aria-label="投标记录列表"
          :aria-busy="loading"
          tabindex="0"
        >
          <table class="bid-cost-page__table">
            <caption class="v2-visually-hidden">
              投标记录列表
            </caption>
            <thead>
              <tr>
                <th scope="col">投标编号</th>
                <th scope="col">工程名称</th>
                <th scope="col">招标人</th>
                <th scope="col">招标代理</th>
                <th scope="col">投标负责人</th>
                <th scope="col">投标截止时间</th>
                <th scope="col">当前状态</th>
                <th scope="col">投标结果</th>
                <th scope="col">最终投标价</th>
                <th scope="col">投标费用</th>
                <th scope="col">更新时间</th>
                <th scope="col" class="v2-table-cell--actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(record, index) in records"
                :key="record.id"
                tabindex="0"
                @click="router.push(`/engineering-tender/records/${record.id}`)"
                @keydown.enter="router.push(`/engineering-tender/records/${record.id}`)"
              >
                <td>
                  <V2Button
                    size="small"
                    variant="ghost"
                    class="v2-table__record-link"
                    @click.stop="router.push(`/engineering-tender/records/${record.id}`)"
                  >
                    {{ record.bidCode }}
                  </V2Button>
                </td>
                <td>{{ record.bidProjectName }}</td>
                <td>{{ record.tendereeName || '—' }}</td>
                <td>{{ record.agencyName || '—' }}</td>
                <td>{{ record.ownerName || '—' }}</td>
                <td>{{ record.bidDeadlineAt || '—' }}</td>
                <td>
                  <V2Badge :tone="statusTone(record.bidStatus)" dot>{{
                    statusLabel(record.bidStatus)
                  }}</V2Badge>
                </td>
                <td>{{ resultLabel(record) }}</td>
                <td>{{ formatAmount(record.finalBidPrice) }}</td>
                <td>{{ formatAmount(record.bidExpense) }}</td>
                <td>{{ record.updatedAt || '—' }}</td>
                <td class="v2-table-cell--actions" @click.stop @keydown.stop>
                  <V2ActionMenu
                    :label="`${record.bidCode}更多操作`"
                    :placement="index >= records.length - 3 ? 'top-end' : 'bottom-end'"
                  >
                    <V2Button
                      v-if="canEdit && record.bidStatus === 'PREPARING'"
                      variant="ghost"
                      @click="openDetail(record.id, 'edit')"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="canDelete && record.bidStatus === 'PREPARING'"
                      variant="danger"
                      @click="requestDelete(record)"
                      >删除</V2Button
                    >
                  </V2ActionMenu>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="bid-cost-page__pagination" aria-label="投标记录分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="filter.pageNo <= 1"
              @click="changePage(filter.pageNo - 1)"
              >上一页</V2Button
            >
            <span>第 {{ filter.pageNo }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="filter.pageNo >= pageCount"
              @click="changePage(filter.pageNo + 1)"
              >下一页</V2Button
            >
          </nav>
        </template>
      </V2Card>

      <V2Dialog
        :open="detailLoading || panelMode !== 'closed'"
        :title="
          panelMode === 'create'
            ? '新建投标记录'
            : panelMode === 'edit'
              ? '编辑投标记录'
              : '投标记录预览'
        "
        :panel-class="panelMode === 'detail' ? 'v2-detail-dialog' : undefined"
        :close-on-backdrop="panelMode === 'detail'"
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
            <div>
              <dt>项目名称</dt>
              <dd>{{ selected.bidProjectName }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>
                <V2Badge :tone="statusTone(selected.bidStatus)">{{
                  statusLabel(selected.bidStatus)
                }}</V2Badge>
              </dd>
            </div>
            <div>
              <dt>备注</dt>
              <dd>{{ selected.remark || '—' }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ selected.updatedAt || '—' }}</dd>
            </div>
            <div>
              <dt>计划工期</dt>
              <dd>
                {{ selected.plannedStartDate || '—' }} 至 {{ selected.plannedEndDate || '—' }}
              </dd>
            </div>
          </dl>
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
          <V2Input v-model="form.tendereeName" label="招标人" :disabled="actionBusy" />
          <V2Input v-model="form.agencyName" label="招标代理" :disabled="actionBusy" />
          <V2Input v-model="form.projectLocation" label="建设地点" :disabled="actionBusy" />
          <V2Input v-model="form.tenderMethod" label="招标方式" :disabled="actionBusy" />
          <V2Select
            v-model="form.ownerId"
            label="投标负责人"
            :options="ownerOptions"
            allow-empty
            :disabled="actionBusy"
          />
          <V2Input
            v-model="form.documentReceivedDate"
            type="date"
            label="获取文件日期"
            :disabled="actionBusy"
          />
          <label class="bid-cost-page__native-field"
            ><span>投标截止时间</span
            ><input v-model="form.bidDeadlineAt" type="datetime-local" :disabled="actionBusy"
          /></label>
          <label class="bid-cost-page__native-field"
            ><span>开标时间</span
            ><input v-model="form.openingAt" type="datetime-local" :disabled="actionBusy"
          /></label>
          <V2Input
            v-model="form.bidValidUntil"
            type="date"
            label="投标有效期"
            :disabled="actionBusy"
          />
          <V2Input
            v-model="form.plannedStartDate"
            type="date"
            label="计划开工"
            :disabled="actionBusy"
          />
          <V2Input
            v-model="form.plannedEndDate"
            type="date"
            label="计划完工"
            :disabled="actionBusy"
          />
          <V2Input
            v-model="form.ceilingPrice"
            type="number"
            label="招标控制价"
            :disabled="actionBusy"
          />
          <V2Input
            v-model="form.finalBidPrice"
            type="number"
            label="最终投标价"
            :disabled="actionBusy"
          />
          <label class="bid-cost-page__native-field">
            <span>备注</span>
            <textarea v-model="form.remark" maxlength="500" :disabled="actionBusy"></textarea>
          </label>
        </form>
        <template v-if="panelMode === 'create' || panelMode === 'edit'" #footer>
          <V2Button type="button" variant="secondary" :disabled="actionBusy" @click="closePanel">
            取消
          </V2Button>
          <V2Button type="submit" form="bid-cost-form" :loading="actionBusy">
            {{ panelMode === 'create' ? '创建' : '保存变更' }}
          </V2Button>
        </template>
        <template v-else-if="panelMode === 'detail'" #footer>
          <V2Button
            v-if="canEdit && selectedIsBidding"
            type="button"
            variant="secondary"
            @click="panelMode = 'edit'"
          >
            编辑
          </V2Button>
          <V2Button
            v-if="canDelete && selectedIsBidding"
            type="button"
            variant="danger"
            @click="selected && requestDelete(selected)"
          >
            删除
          </V2Button>
        </template>
      </V2Dialog>
    </template>

    <V2ConfirmDialog
      :open="pendingAction !== null"
      title="删除投标记录"
      description="仅注册状态记录可删除，此操作不可撤销。"
      confirm-text="确认删除"
      danger
      :loading="actionBusy"
      @close="closeConfirmation"
      @confirm="confirmAction"
    >
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

@media (min-width: 75rem) {
  .bid-cost-page__filters {
    grid-template-columns:
      minmax(10rem, 1.4fr) repeat(3, minmax(8rem, 1fr)) repeat(2, minmax(9rem, 1fr))
      auto;
  }
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
