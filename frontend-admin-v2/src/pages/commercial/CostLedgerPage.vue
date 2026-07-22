<script setup lang="ts">
import type {
  CostLedgerQuery,
  CostLedgerRecord,
  CostLedgerSummary,
  ProjectContextOption,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Alert, V2Button, V2Card, V2Dialog, V2Input, V2PageState, V2Select } from '@/components'
import {
  loadCostLedger,
  loadCostLedgerPage,
  loadCostLedgerSummary,
  loadProjectContextOptions,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const filter = reactive<CostLedgerQuery>({ pageNo: 1, pageSize: 20 })
const records = ref<CostLedgerRecord[]>([])
const summary = ref<CostLedgerSummary | null>(null)
const detail = ref<CostLedgerRecord | null>(null)
const projects = ref<ProjectContextOption[]>([])
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const detailOpen = ref(false)
let generation = 0
let detailGeneration = 0
let controller: AbortController | null = null
let detailController: AbortController | null = null
const canQuery = computed(() => session.hasPermission('cost:ledger:query'))
const projectOptions = computed(() => [
  { value: '', label: '全部项目' },
  ...projects.value.map((p) => ({ value: p.id, label: p.projectName })),
])
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / 20)))
const errorText = (e: unknown, fallback: string) =>
  isApiClientError(e) ? e.message : e instanceof Error ? e.message : fallback
function hydrate() {
  const bounds = reportPeriodBounds(
    typeof route.query.period === 'string' ? route.query.period : null,
  )
  Object.assign(filter, {
    pageNo: Math.max(1, Number(route.query.pageNo) || 1),
    pageSize: 20,
    projectId: typeof route.query.projectId === 'string' ? route.query.projectId : undefined,
    keyword: typeof route.query.keyword === 'string' ? route.query.keyword : undefined,
    startDate: bounds?.startDate,
    endDate: bounds?.endDate,
  })
}
async function load() {
  if (!canQuery.value) return
  hydrate()
  controller?.abort()
  const current = new AbortController()
  controller = current
  const token = ++generation
  loading.value = true
  errorMessage.value = ''
  try {
    const [page, totals, options] = await Promise.all([
      loadCostLedgerPage({ ...filter }, current.signal),
      loadCostLedgerSummary({ ...filter }, current.signal),
      loadProjectContextOptions(current.signal),
    ])
    if (token !== generation) return
    records.value = page.records
    total.value = page.total
    summary.value = totals
    projects.value = options
  } catch (e) {
    if (!current.signal.aborted && token === generation) {
      records.value = []
      summary.value = null
      errorMessage.value = errorText(e, '成本台账加载失败')
    }
  } finally {
    if (token === generation) loading.value = false
  }
}
async function query() {
  filter.pageNo = 1
  await router.replace({
    path: '/cost/ledger',
    query: {
      ...(filter.projectId ? { projectId: filter.projectId } : {}),
      ...(filter.keyword?.trim() ? { keyword: filter.keyword.trim() } : {}),
      ...(typeof route.query.period === 'string' ? { period: route.query.period } : {}),
    },
    hash: route.hash,
  })
}
async function page(value: number) {
  await router.replace({
    query: { ...route.query, ...(value > 1 ? { pageNo: String(value) } : { pageNo: undefined }) },
    hash: route.hash,
  })
}
async function openDetail(id: string) {
  detailController?.abort()
  const current = new AbortController()
  detailController = current
  const token = ++detailGeneration
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const value = await loadCostLedger(id, current.signal)
    if (token === detailGeneration) detail.value = value
  } catch (e) {
    if (!current.signal.aborted && token === detailGeneration)
      errorMessage.value = errorText(e, '台账详情加载失败')
  } finally {
    if (token === detailGeneration) detailLoading.value = false
  }
}
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => {
  controller?.abort()
  detailController?.abort()
})
</script>
<template>
  <div class="cost-page">
    <V2PageState
      v-if="!canQuery"
      title="无权访问成本台账"
      description="请联系管理员开通访问权限。"
      kind="forbidden"
    /><template v-else
      ><V2Alert v-if="errorMessage" tone="danger" title="成本台账请求未完成">{{
        errorMessage
      }}</V2Alert
      ><V2Card title="成本台账" :heading-level="1"
        ><div class="filters">
          <V2Select
            v-model="filter.projectId"
            label="项目"
            :options="projectOptions"
            allow-empty
          /><V2Input v-model="filter.keyword" label="关键词" @keyup.enter="query" /><V2Button
            variant="secondary"
            :loading="loading"
            @click="query"
            >查询</V2Button
          >
        </div></V2Card
      ><V2Card v-if="summary" title="筛选汇总"
        ><dl>
          <dt>成本总额</dt>
          <dd>{{ summary.totalAmount }}</dd>
          <dt>税额</dt>
          <dd>{{ summary.totalTaxAmount }}</dd>
        </dl></V2Card
      ><V2PageState
        v-if="loading && !records.length"
        title="正在加载成本台账"
        description="正在读取当前项目和报告期内的成本记录。"
        kind="loading"
      /><V2PageState
        v-else-if="!records.length"
        title="暂无成本台账"
        description="当前筛选条件下没有可访问的成本记录。"
        kind="empty"
      /><V2Card
        v-for="row in records"
        v-else
        :key="row.id"
        :title="row.costSubjectName || row.costType"
        ><dl>
          <dt>项目</dt>
          <dd>{{ row.projectName || row.projectId }}</dd>
          <dt>发生日期</dt>
          <dd>{{ row.costDate || '—' }}</dd>
          <dt>金额</dt>
          <dd>{{ row.amount }}</dd>
          <dt>税额</dt>
          <dd>{{ row.taxAmount }}</dd>
          <dt>来源</dt>
          <dd>{{ row.sourceType }}</dd>
        </dl>
        <template #footer
          ><V2Button variant="secondary" @click="openDetail(row.id)">详情</V2Button></template
        ></V2Card
      >
      <nav v-if="records.length">
        <V2Button
          variant="secondary"
          :disabled="(filter.pageNo || 1) <= 1"
          @click="page((filter.pageNo || 1) - 1)"
          >上一页</V2Button
        ><span>第 {{ filter.pageNo }} / {{ pageCount }} 页，共 {{ total }} 条</span
        ><V2Button
          variant="secondary"
          :disabled="(filter.pageNo || 1) >= pageCount"
          @click="page((filter.pageNo || 1) + 1)"
          >下一页</V2Button
        >
      </nav>
      <V2Dialog
        :open="detailOpen"
        title="成本台账详情"
        panel-class="v2-dialog-standard v2-detail-dialog"
        :close-on-backdrop="false"
        @close="detailOpen = false"
        ><V2PageState
          v-if="detailLoading"
          title="正在加载成本详情"
          description="正在读取成本来源、金额和业务关联。"
          kind="loading"
        />
        <dl v-else-if="detail">
          <dt>ID</dt>
          <dd>{{ detail.id }}</dd>
          <dt>项目</dt>
          <dd>{{ detail.projectName || detail.projectId }}</dd>
          <dt>合同</dt>
          <dd>{{ detail.contractName || detail.contractId || '—' }}</dd>
          <dt>含税金额</dt>
          <dd>{{ detail.amount }}</dd>
          <dt>未税金额</dt>
          <dd>{{ detail.amountWithoutTax }}</dd>
          <dt>状态</dt>
          <dd>{{ detail.costStatus }}</dd>
          <dt>备注</dt>
          <dd>{{ detail.remark || '—' }}</dd>
        </dl></V2Dialog
      ></template
    >
  </div>
</template>
<style scoped>
.cost-page {
  display: grid;
  gap: var(--v2-space-4);
}
.filters {
  display: grid;
  grid-template-columns: minmax(12rem, 1fr) minmax(12rem, 1fr) auto;
  gap: var(--v2-space-3);
  align-items: end;
}
dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
nav {
  display: flex;
  gap: var(--v2-space-2);
  align-items: center;
  justify-content: flex-end;
}
@media (max-width: 48rem) {
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
