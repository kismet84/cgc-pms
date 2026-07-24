<script setup lang="ts">
import type {
  CostLedgerQuery,
  CostLedgerRecord,
  CostLedgerSummary,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Alert, V2Button, V2Card, V2Dialog, V2Input, V2PageState } from '@/components'
import { loadCostLedger, loadCostLedgerPage, loadCostLedgerSummary } from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { reportPeriodBounds } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const filter = reactive<CostLedgerQuery>({ pageNo: 1, pageSize: 10 })
const records = ref<CostLedgerRecord[]>([])
const summary = ref<CostLedgerSummary | null>(null)
const detail = ref<CostLedgerRecord | null>(null)
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
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / (filter.pageSize || 10))))
const errorText = (e: unknown, fallback: string) =>
  isApiClientError(e) ? e.message : e instanceof Error ? e.message : fallback
function hydrate() {
  const bounds = reportPeriodBounds(
    typeof route.query.period === 'string' ? route.query.period : null,
  )
  Object.assign(filter, {
    pageNo: Math.max(1, Number(route.query.pageNo) || 1),
    pageSize: 10,
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
    const [page, totals] = await Promise.all([
      loadCostLedgerPage({ ...filter }, current.signal),
      loadCostLedgerSummary({ ...filter }, current.signal),
    ])
    if (token !== generation) return
    records.value = page.records
    total.value = page.total
    summary.value = totals
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
      ...route.query,
      keyword: filter.keyword?.trim() || undefined,
      pageNo: undefined,
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
          <V2Input v-model="filter.keyword" label="关键词" @keyup.enter="query" /><V2Button
            variant="secondary"
            :loading="loading"
            @click="query"
            >查询</V2Button
          >
        </div></V2Card
      ><V2Card title="成本明细"
        ><template v-if="summary" #actions>
          <dl class="cost-page__summary">
            <dt>成本总额</dt>
            <dd>{{ summary.totalAmount }}</dd>
            <dt>税额</dt>
            <dd>{{ summary.totalTaxAmount }}</dd>
          </dl>
        </template>
        <V2PageState
          v-if="loading && !records.length"
          title="正在加载成本台账"
          description="正在读取当前项目和报告期内的成本记录。"
          kind="loading"
        /><V2PageState
          v-else-if="!records.length"
          title="暂无成本台账"
          description="当前筛选条件下没有可访问的成本记录。"
          kind="empty"
        />
        <div
          v-else
          class="table-wrap"
          role="region"
          aria-label="成本台账列表"
          tabindex="0"
          :aria-busy="loading"
        >
          <table>
            <caption class="v2-visually-hidden">
              成本台账列表
            </caption>
            <thead>
              <tr>
                <th scope="col">成本科目</th>
                <th scope="col">项目</th>
                <th scope="col">发生日期</th>
                <th scope="col">金额</th>
                <th scope="col">税额</th>
                <th scope="col">来源</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in records" :key="row.id">
                <td>{{ row.costSubjectName || row.costType }}</td>
                <td>{{ row.projectName || '—' }}</td>
                <td>{{ row.costDate || '—' }}</td>
                <td>{{ row.amount }}</td>
                <td>{{ row.taxAmount }}</td>
                <td>{{ row.sourceType }}</td>
                <td>
                  <V2Button size="small" variant="secondary" @click="openDetail(row.id)"
                    >详情</V2Button
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template v-if="records.length" #footer>
          <nav aria-label="成本台账分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="(filter.pageNo || 1) <= 1"
              @click="page((filter.pageNo || 1) - 1)"
              >上一页</V2Button
            ><span>第 {{ filter.pageNo }} 页</span
            ><V2Button
              size="small"
              variant="secondary"
              :disabled="(filter.pageNo || 1) >= pageCount"
              @click="page((filter.pageNo || 1) + 1)"
              >下一页</V2Button
            >
          </nav>
        </template></V2Card
      >
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
        <dl v-else-if="detail" class="v2-detail-dialog__facts">
          <dt>ID</dt>
          <dd>{{ detail.id }}</dd>
          <dt>项目</dt>
          <dd>{{ detail.projectName || '—' }}</dd>
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
  grid-template-columns: minmax(12rem, 1fr) auto;
  gap: var(--v2-space-3);
  align-items: end;
}
dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}
.cost-page__summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2) var(--v2-space-3);
  align-items: baseline;
  justify-content: flex-end;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}
.cost-page__summary dd {
  color: var(--v2-color-text);
  font-weight: var(--v2-font-weight-semibold);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.table-wrap {
  min-width: 0;
  overflow-x: auto;
}
table {
  width: 100%;
  min-width: 56rem;
  border-collapse: collapse;
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
}
th,
td {
  padding: var(--v2-space-3);
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid var(--v2-color-border-subtle);
}
th {
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface-subtle);
  font-weight: var(--v2-font-weight-semibold);
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
  .cost-page__summary {
    justify-content: flex-start;
  }
}
</style>
