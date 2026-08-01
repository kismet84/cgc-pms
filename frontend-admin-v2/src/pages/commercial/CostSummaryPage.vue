<script setup lang="ts">
import type {
  AccessibleCostSummary,
  CostProjectSummary,
  CostSummaryHistoryRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { V2Button, V2Card, V2PageState, V2Pagination, showToast } from '@/components'
import {
  loadAccessibleCostSummary,
  loadCostSummary,
  loadCostSummaryHistory,
  refreshCostSummary,
} from '@/services/commercial'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
const route = useRoute()
const session = useSessionStore()
const projectId = ref('')
const accessible = ref<AccessibleCostSummary | null>(null)
const latest = ref<CostProjectSummary | null>(null)
const history = ref<CostSummaryHistoryRecord[]>([])
const loading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const pageSize = 10
const projectPageNo = ref(1)
const historyPageNo = ref(1)
const pagedProjects = computed(() =>
  (accessible.value?.projects ?? []).slice(
    (projectPageNo.value - 1) * pageSize,
    projectPageNo.value * pageSize,
  ),
)
const pagedHistory = computed(() =>
  history.value.slice((historyPageNo.value - 1) * pageSize, historyPageNo.value * pageSize),
)

watch(errorMessage, (message) => {
  if (message) showToast('error', '成本核对请求未完成', message)
})
let controller: AbortController | null = null
let generation = 0
const canQuery = computed(() => session.hasPermission('cost:summary:view'))
const canRefresh = computed(() => session.hasPermission('cost:summary:refresh'))
const errorText = (e: unknown, f: string) =>
  isApiClientError(e) ? e.message : e instanceof Error ? e.message : f
const needsAuthoritativeReload = (e: unknown) =>
  isApiClientError(e) && (e.status === 409 || e.status === 422)
async function load() {
  if (!canQuery.value) return
  projectPageNo.value = 1
  historyPageNo.value = 1
  projectId.value = typeof route.query.projectId === 'string' ? route.query.projectId : ''
  controller?.abort()
  const current = new AbortController()
  controller = current
  const token = ++generation
  loading.value = true
  errorMessage.value = ''
  try {
    if (!projectId.value) {
      const scope = await loadAccessibleCostSummary(current.signal)
      if (token === generation) {
        accessible.value = scope
        latest.value = null
        history.value = []
      }
      return
    }
    accessible.value = null
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
      accessible.value = null
      history.value = []
      errorMessage.value = errorText(e, '成本核对加载失败')
    }
  } finally {
    if (token === generation) loading.value = false
  }
}
async function refresh() {
  if (actionBusy.value || !canRefresh.value || !projectId.value) return
  actionBusy.value = true
  errorMessage.value = ''
  try {
    await refreshCostSummary(projectId.value)
    await load()
    showToast('success', '刷新完成', '成本汇总已刷新。')
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
    />
    <template v-else>
      <V2Card title="成本核对" :heading-level="1">
        <template #actions>
          <V2Button
            v-if="canRefresh && projectId"
            size="small"
            variant="secondary"
            :loading="actionBusy"
            @click="refresh"
            >刷新汇总</V2Button
          >
        </template>
      </V2Card>
      <V2PageState
        v-if="loading"
        title="正在加载成本核对"
        description="正在读取项目成本汇总及历史记录。"
        kind="loading" /><V2PageState
        v-else-if="!projectId && !accessible?.projects.length && !errorMessage"
        title="暂无成本汇总"
        description="当前账号可访问项目尚未生成可核对的成本汇总。"
        kind="empty" /><V2Card v-else-if="!projectId && accessible">
        <div class="table-wrap" role="region" aria-label="全部项目成本汇总表格" tabindex="0">
          <table>
            <thead>
              <tr>
                <th scope="col">项目</th>
                <th scope="col">目标成本</th>
                <th scope="col">实际成本</th>
                <th scope="col">动态成本</th>
                <th scope="col">预测利润</th>
                <th scope="col">利润率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in pagedProjects" :key="row.projectId">
                <th scope="row">{{ row.projectName }}</th>
                <td>{{ row.targetCost }}</td>
                <td>{{ row.actualCost }}</td>
                <td>{{ row.dynamicCost }}</td>
                <td>{{ row.forecastProfit }}</td>
                <td>{{ row.profitMargin }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <V2Pagination
            v-model:page-no="projectPageNo"
            :total="accessible.projects.length"
            label="全部项目成本汇总分页"
          />
        </template>
      </V2Card>
      <V2PageState
        v-else-if="!latest && !errorMessage"
        title="暂无成本汇总"
        description="当前项目尚未生成可核对的成本汇总。"
        kind="empty" /><template v-else-if="latest"
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
            v-if="!history.length && !errorMessage"
            title="暂无历史记录"
            description="当前项目尚无成本汇总快照历史。"
            kind="empty" />
          <div
            v-else-if="history.length"
            class="table-wrap"
            role="region"
            aria-label="成本汇总历史表格"
            tabindex="0"
          >
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
                <tr v-for="row in pagedHistory" :key="row.id">
                  <td>{{ row.summaryDate }}</td>
                  <td>{{ row.costSubjectName }}</td>
                  <td>{{ row.targetCost }}</td>
                  <td>{{ row.actualCost }}</td>
                  <td>{{ row.forecastProfit }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer>
            <V2Pagination
              v-model:page-no="historyPageNo"
              :total="history.length"
              label="成本汇总历史分页"
            /> </template></V2Card></template
    ></template>
  </div>
</template>
<style scoped>
.cost-page {
  display: grid;
  gap: var(--v2-space-4);
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
</style>
