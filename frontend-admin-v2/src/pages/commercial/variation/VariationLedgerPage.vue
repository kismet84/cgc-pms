<script setup lang="ts">
import type { VariationPage, VariationQuery } from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Badge, V2Button, V2Card, V2Input, V2PageState, V2Select, showToast } from '@/components'
import { formatAmount } from '@/shared/display'
import { loadVariationPage } from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const records = ref<VariationPage['records']>([])
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const filter = reactive<VariationQuery>({
  pageNo: 1,
  pageSize: 10,
  projectId: '',
  varCode: '',
  varType: '',
  direction: '',
  startDate: undefined,
  endDate: undefined,
})
let generation = 0
let controller: AbortController | null = null

const canCreate = computed(() => session.hasPermission('variation:order:add'))
const canEdit = computed(() => session.hasPermission('variation:order:edit'))
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / (filter.pageSize ?? 10))))

const APPROVAL_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}

const OWNER_STATUS_LABELS: Record<string, string> = {
  NOT_READY: '未就绪',
  NOT_SUBMITTED: '未申报',
  INTERNAL_APPROVED: '内部已通过',
  OWNER_SUBMITTED: '已申报',
  OWNER_RETURNED: '业主退回',
  CHANGE_PENDING: '合同变更审批中',
  CHANGE_EFFECTIVE: '已生效',
}

function approvalStatusLabel(value?: string | null): string {
  return (value && APPROVAL_STATUS_LABELS[value]) || '未知状态'
}

function ownerStatusLabel(value?: string | null): string {
  return (value && OWNER_STATUS_LABELS[value]) || '未知状态'
}

function textQuery(key: string): string {
  return typeof route.query[key] === 'string' ? route.query[key].trim() : ''
}

function hydrateFilter(): void {
  filter.projectId = textQuery('projectId')
  filter.varCode = textQuery('varCode')
  filter.varType = textQuery('varType')
  filter.direction = textQuery('direction')
  const periodBounds = reportPeriodBounds(textQuery('period'))
  filter.startDate = periodBounds?.startDate
  filter.endDate = periodBounds?.endDate
  const pageNo = Number(textQuery('pageNo') || '1')
  filter.pageNo = Number.isInteger(pageNo) && pageNo > 0 ? pageNo : 1
}

function errorText(error: unknown, fallback: string): string {
  if (isApiClientError(error)) return error.message
  return error instanceof Error ? error.message : fallback
}

async function replaceListQuery(): Promise<boolean> {
  const location = {
    path: '/variation/order',
    query: {
      ...route.query,
      varCode: filter.varCode || undefined,
      varType: filter.varType || undefined,
      direction: filter.direction || undefined,
      pageNo: filter.pageNo && filter.pageNo > 1 ? String(filter.pageNo) : undefined,
    },
  }
  if (router.resolve(location).fullPath === route.fullPath) return false
  await router.replace(location)
  return true
}

async function loadList(): Promise<void> {
  hydrateFilter()
  controller?.abort()
  const current = new AbortController()
  controller = current
  const currentGeneration = ++generation
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await loadVariationPage(filter, current.signal)
    if (currentGeneration !== generation) return
    records.value = page.records
    total.value = page.total
  } catch (error) {
    if (!current.signal.aborted && currentGeneration === generation) {
      records.value = []
      total.value = 0
      errorMessage.value = errorText(error, '签证变更台账加载失败')
      showToast('error', '签证变更操作未完成', errorMessage.value)
    }
  } finally {
    if (currentGeneration === generation) loading.value = false
  }
}

async function openWorkspace(mode: 'create' | 'detail' | 'edit', id?: string): Promise<void> {
  await router.push({
    path: '/variation/order',
    query: { ...route.query, mode, ...(id ? { id } : {}) },
  })
}

async function search(): Promise<void> {
  filter.pageNo = 1
  if (!(await replaceListQuery())) await loadList()
}
const query = search

async function changePage(pageNo: number): Promise<void> {
  filter.pageNo = pageNo
  if (!(await replaceListQuery())) await loadList()
}

watch(() => route.fullPath, loadList, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Card title="签证变更" :heading-level="1">
    <template #actions>
      <div class="variation-page__toolbar">
        <form class="variation-page__filters" @submit.prevent="search">
          <V2Input
            v-model="filter.varCode"
            type="search"
            label="变更编号"
            hide-label
            placeholder="输入变更编号"
          />
          <V2Select
            v-model="filter.varType"
            label="变更类型"
            hide-label
            allow-empty
            placeholder="全部类型"
            :options="[
              { value: '', label: '全部类型' },
              { value: 'DESIGN', label: '设计变更' },
              { value: 'SITE', label: '现场签证' },
              { value: 'OTHER', label: '其他' },
            ]"
            @update:model-value="query"
          />
          <V2Select
            v-model="filter.direction"
            label="方向"
            hide-label
            allow-empty
            placeholder="全部方向"
            :options="[
              { value: '', label: '全部方向' },
              { value: 'COST', label: '成本' },
              { value: 'INCOME', label: '收入' },
            ]"
            @update:model-value="query"
          />
          <V2Button type="submit" size="small" :loading="loading">查询</V2Button>
        </form>
        <V2Button v-if="canCreate" size="small" @click="openWorkspace('create')">
          新建变更
        </V2Button>
      </div>
    </template>
  </V2Card>

  <V2Card>
    <V2PageState
      v-if="loading && !records.length"
      kind="loading"
      title="正在加载签证变更"
      description="请稍候。"
    />
    <V2PageState
      v-else-if="!records.length && !errorMessage"
      title="暂无签证变更"
      description="当前筛选条件下没有数据。"
    />
    <div
      v-else-if="records.length"
      class="variation-page__table-wrap"
      role="region"
      aria-label="签证变更列表"
      tabindex="0"
    >
      <table>
        <thead>
          <tr>
            <th>编号</th>
            <th>名称</th>
            <th>项目</th>
            <th>合同</th>
            <th>申报金额</th>
            <th>审批</th>
            <th>业主</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td>
              <V2Button
                size="small"
                variant="ghost"
                class="v2-table__record-link"
                @click="openWorkspace('detail', record.id)"
              >
                {{ record.varCode }}
              </V2Button>
            </td>
            <td>{{ record.varName }}</td>
            <td>{{ record.projectName || '—' }}</td>
            <td>{{ record.contractName || '合同名称缺失' }}</td>
            <td>{{ formatAmount(record.reportedAmount) }}</td>
            <td>
              <V2Badge>{{ approvalStatusLabel(record.approvalStatus) }}</V2Badge>
            </td>
            <td>{{ ownerStatusLabel(record.ownerStatus) }}</td>
            <td class="variation-page__actions">
              <V2Button
                v-if="canEdit && record.approvalStatus === 'DRAFT'"
                size="small"
                variant="ghost"
                @click="openWorkspace('edit', record.id)"
              >
                编辑
              </V2Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #footer>
      <nav class="variation-page__pager" aria-label="签证变更分页">
        <span>共 {{ total }} 条</span>
        <V2Button
          size="small"
          variant="secondary"
          :disabled="(filter.pageNo ?? 1) <= 1 || loading"
          @click="changePage((filter.pageNo ?? 1) - 1)"
        >
          上一页
        </V2Button>
        <span>第 {{ filter.pageNo }} 页</span>
        <V2Button
          size="small"
          variant="secondary"
          :disabled="(filter.pageNo ?? 1) >= pageCount || loading"
          @click="changePage((filter.pageNo ?? 1) + 1)"
        >
          下一页
        </V2Button>
      </nav>
    </template>
  </V2Card>
</template>
