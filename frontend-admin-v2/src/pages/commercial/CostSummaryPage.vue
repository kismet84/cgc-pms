<script setup lang="ts">
import type {
  CostProjectSummary,
  CostSummaryHistoryRecord,
  ProjectContextOption,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Alert, V2Button, V2Card, V2PageState, V2Select } from '@/components'
import {
  loadCostSummary,
  loadCostSummaryHistory,
  loadProjectContextOptions,
  refreshCostSummary,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const projectId = ref('')
const projects = ref<ProjectContextOption[]>([])
const latest = ref<CostProjectSummary | null>(null)
const history = ref<CostSummaryHistoryRecord[]>([])
const loading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
let controller: AbortController | null = null
let generation = 0
const canQuery = computed(() => session.hasPermission('cost:summary:view'))
const canRefresh = computed(() => session.hasPermission('cost:summary:refresh'))
const projectOptions = computed(() =>
  projects.value.map((p) => ({ value: p.id, label: p.projectName })),
)
const errorText = (e: unknown, f: string) =>
  isApiClientError(e) ? e.message : e instanceof Error ? e.message : f
const needsAuthoritativeReload = (e: unknown) =>
  isApiClientError(e) && (e.status === 409 || e.status === 422)
async function load() {
  if (!canQuery.value) return
  projectId.value = typeof route.query.projectId === 'string' ? route.query.projectId : ''
  controller?.abort()
  const current = new AbortController()
  controller = current
  const token = ++generation
  loading.value = true
  errorMessage.value = ''
  try {
    projects.value = await loadProjectContextOptions(current.signal)
    if (!projectId.value && projects.value.length) projectId.value = projects.value[0]!.id
    if (!projectId.value) {
      latest.value = null
      history.value = []
      return
    }
    const [now, rows] = await Promise.all([
      loadCostSummary(projectId.value, current.signal),
      loadCostSummaryHistory(projectId.value, current.signal),
    ])
    if (token !== generation) return
    latest.value = now
    history.value = rows
  } catch (e) {
    if (!current.signal.aborted && token === generation) {
      latest.value = null
      history.value = []
      errorMessage.value = errorText(e, '成本核对加载失败')
    }
  } finally {
    if (token === generation) loading.value = false
  }
}
async function changeProject() {
  await router.replace({
    path: '/cost/summary',
    query: {
      ...(projectId.value ? { projectId: projectId.value } : {}),
      ...(typeof route.query.period === 'string' ? { period: route.query.period } : {}),
    },
    hash: route.hash,
  })
}
async function refresh() {
  if (actionBusy.value || !canRefresh.value || !projectId.value) return
  actionBusy.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await refreshCostSummary(projectId.value)
    successMessage.value = '成本汇总已刷新'
    await load()
  } catch (e) {
    const message = errorText(e, '成本汇总刷新失败')
    if (needsAuthoritativeReload(e)) await load()
    errorMessage.value = message
  } finally {
    actionBusy.value = false
  }
}
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <div class="cost-page">
    <V2PageState
      v-if="!canQuery"
      title="无权访问成本核对"
      description="请联系管理员开通访问权限。"
      kind="forbidden"
    /><template v-else
      ><V2Alert v-if="errorMessage" tone="danger" title="成本核对请求未完成">{{
        errorMessage
      }}</V2Alert
      ><V2Alert v-if="successMessage" tone="success" title="成本核对操作完成">{{
        successMessage
      }}</V2Alert
      ><V2Card title="成本核对" :heading-level="1"
        ><div class="filters">
          <V2Select
            v-model="projectId"
            label="项目"
            :options="projectOptions"
            @update:model-value="changeProject"
          /><V2Button v-if="canRefresh" variant="secondary" :loading="actionBusy" @click="refresh"
            >刷新汇总</V2Button
          >
        </div></V2Card
      ><V2PageState
        v-if="loading"
        title="正在加载成本核对"
        description="正在读取项目成本汇总及历史记录。"
        kind="loading"
      /><V2PageState
        v-else-if="!latest"
        title="暂无成本汇总"
        description="当前项目尚未生成可核对的成本汇总。"
        kind="empty"
      /><template v-else
        ><V2Card :title="latest.projectName || '项目汇总'"
          ><dl>
            <dt>目标成本</dt>
            <dd>{{ latest.targetCost }}</dd>
            <dt>实际成本</dt>
            <dd>{{ latest.actualCost }}</dd>
            <dt>动态成本</dt>
            <dd>{{ latest.dynamicCost }}</dd>
            <dt>预测利润</dt>
            <dd>{{ latest.forecastProfit }}</dd>
            <dt>利润率</dt>
            <dd>{{ latest.profitMargin }}</dd>
          </dl></V2Card
        ><V2Card title="汇总历史"
          ><V2PageState
            v-if="!history.length"
            title="暂无历史记录"
            description="当前项目尚无成本汇总快照历史。"
            kind="empty"
          />
          <div v-else class="table-wrap" role="region" aria-label="成本汇总历史表格" tabindex="0">
            <table>
              <thead>
                <tr>
                  <th>日期</th>
                  <th>成本科目</th>
                  <th>目标成本</th>
                  <th>实际成本</th>
                  <th>预测利润</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in history" :key="row.id">
                  <td>{{ row.summaryDate }}</td>
                  <td>{{ row.costSubjectName }}</td>
                  <td>{{ row.targetCost }}</td>
                  <td>{{ row.actualCost }}</td>
                  <td>{{ row.forecastProfit }}</td>
                </tr>
              </tbody>
            </table>
          </div></V2Card
        ></template
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
  display: flex;
  gap: var(--v2-space-3);
  align-items: end;
}
.filters > *:first-child {
  flex: 1;
}
dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--v2-space-2) var(--v2-space-4);
  margin: 0;
}
dd {
  margin: 0;
}
.table-wrap {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: var(--v2-space-2);
  border-bottom: 1px solid var(--v2-color-border);
}
</style>
