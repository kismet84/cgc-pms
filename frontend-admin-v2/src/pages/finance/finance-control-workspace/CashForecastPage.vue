<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { V2Button, V2Card, V2PageState, V2Pagination } from '@/components'
import { showToast } from '@/components/toast'
import {
  approveCashForecast,
  loadCashForecastCycles,
  loadCashForecastTrace,
  refreshCashForecastActuals,
  regenerateCashForecast,
  submitCashForecast,
} from '@/services/finance'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type { CashForecastCycleRecord, CashForecastTrace } from '@cgc-pms/frontend-contracts'
import { amount, askReason, label, pageSlice } from './model'

const pageSize = 10
const session = useSessionStore()
const workspace = useWorkspaceStore()
const projectId = computed(() => workspace.selectedProjectId || '')
const canQuery = computed(() => session.hasPermission('finance:forecast:query'))
const can = (permission: string) => session.hasPermission(permission)
const cycles = ref<CashForecastCycleRecord[]>([])
const pageNo = ref(1)
const trace = ref<CashForecastTrace | null>(null)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
let controller: AbortController | null = null
const pagedCycles = computed(() => pageSlice(cycles.value, pageNo.value))

function projectRequired(): boolean {
  if (projectId.value) return true
  showToast('error', '请选择项目', '项目明细和项目写操作需要切换至具体项目。')
  return false
}

async function load(): Promise<void> {
  if (!canQuery.value) return
  pageNo.value = 1
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    cycles.value = await loadCashForecastCycles(projectId.value || undefined, request.signal)
    if (projectId.value) {
      const selected =
        cycles.value.find((row) => row.id === trace.value?.cycle.id) || cycles.value[0]
      trace.value = selected ? await loadCashForecastTrace(selected.id, request.signal) : null
    } else {
      trace.value = null
    }
  } catch (cause) {
    if (!request.signal.aborted) {
      errorMessage.value = cause instanceof Error ? cause.message : '请求失败，请稍后重试。'
    }
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

async function refreshWorkspace(): Promise<void> {
  await load()
  if (!errorMessage.value) showToast('success', '刷新完成', '已读取最新数据。')
}

async function selectForecast(id: string): Promise<void> {
  if (projectRequired()) trace.value = await loadCashForecastTrace(id)
}

async function run(action: () => Promise<unknown>, success: string): Promise<void> {
  busy.value = true
  try {
    await action()
    await load()
    showToast('success', success, '已读取最新数据。')
  } catch (cause) {
    showToast('error', '操作失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

async function actForecast(
  action: 'regenerate' | 'submit' | 'approve' | 'reject' | 'refresh',
): Promise<void> {
  if (!projectRequired()) return
  const id = trace.value?.cycle.id
  if (!id) return
  if (action === 'regenerate') return run(() => regenerateCashForecast(id), '预测已重算')
  if (action === 'submit') return run(() => submitCashForecast(id), '预测已提交')
  if (action === 'refresh') return run(() => refreshCashForecastActuals(id), '预测实际收付已回写')
  const comment = askReason(action === 'approve' ? '请输入批准意见' : '请输入驳回意见')
  if (comment)
    await run(() => approveCashForecast(id, action === 'approve', comment), '预测审批已完成')
}

watch(projectId, () => void load(), { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-control">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      title="无权访问资金预测"
      description="系统未加载任何财务数据。"
    />
    <template v-else>
      <V2Card title="资金预测" :heading-level="1">
        <template #actions>
          <div class="finance-control__actions">
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace">
              刷新
            </V2Button>
          </div>
        </template>
      </V2Card>
      <V2PageState
        v-if="loading && !cycles.length"
        kind="loading"
        title="正在加载"
        description="正在读取资金预测。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="资金预测加载失败"
        :description="errorMessage"
        ><template #actions><V2Button @click="load">重试</V2Button></template></V2PageState
      >
      <V2PageState
        v-else-if="!errorMessage && !cycles.length"
        title="暂无资金预测记录"
        description="当前范围暂无可访问记录。"
      />
      <template v-else>
        <V2Card title="预测版本" :heading-level="2">
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="预测版本表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>项目</th>
                  <th>版本编号</th>
                  <th>场景</th>
                  <th>区间</th>
                  <th>期初余额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedCycles" :key="row.id">
                  <td>{{ row.projectName || '项目信息缺失' }}</td>
                  <td>
                    <V2Button size="small" variant="ghost" @click="selectForecast(row.id)">{{
                      row.cycleCode
                    }}</V2Button>
                  </td>
                  <td>{{ label(row.scenario) }}</td>
                  <td>{{ row.horizonStart }} 至 {{ row.horizonEnd }}</td>
                  <td>{{ amount(row.openingBalance) }}</td>
                  <td>{{ label(row.status) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer
            ><V2Pagination
              :total="cycles.length"
              :page-no="pageNo"
              :page-size="pageSize"
              label="预测版本分页"
              @update:page-no="pageNo = $event"
          /></template>
        </V2Card>
        <V2Card v-if="trace" :title="`预测明细 · ${trace.cycle.cycleCode}`" :heading-level="2">
          <template #actions
            ><div class="finance-control__actions">
              <V2Button
                v-if="trace.cycle.status === 'DRAFT' && can('finance:forecast:maintain')"
                size="small"
                variant="secondary"
                @click="actForecast('regenerate')"
                >重算</V2Button
              >
              <V2Button
                v-if="trace.cycle.status === 'DRAFT' && can('finance:forecast:submit')"
                size="small"
                @click="actForecast('submit')"
                >提交</V2Button
              >
              <V2Button
                v-if="trace.cycle.status === 'SUBMITTED' && can('finance:forecast:approve')"
                size="small"
                @click="actForecast('approve')"
                >批准</V2Button
              >
              <V2Button
                v-if="trace.cycle.status === 'SUBMITTED' && can('finance:forecast:approve')"
                size="small"
                variant="danger"
                @click="actForecast('reject')"
                >驳回</V2Button
              >
              <V2Button
                v-if="trace.cycle.status === 'APPROVED' && can('finance:forecast:refresh')"
                size="small"
                @click="actForecast('refresh')"
                >回写实际收付</V2Button
              >
            </div></template
          >
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="预测日明细表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__wide-table">
              <thead>
                <tr>
                  <th>日期</th>
                  <th>计划流入</th>
                  <th>计划流出</th>
                  <th>融资</th>
                  <th>预测余额</th>
                  <th>缺口</th>
                  <th>实际流入</th>
                  <th>实际流出</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in trace.lines" :key="row.id">
                  <td>{{ row.forecastDate }}</td>
                  <td>{{ amount(row.plannedInflow) }}</td>
                  <td>{{ amount(row.plannedOutflow) }}</td>
                  <td>{{ amount(row.financingAmount) }}</td>
                  <td>{{ amount(row.projectedBalance) }}</td>
                  <td>{{ amount(row.gapAmount) }}</td>
                  <td>{{ amount(row.actualInflow) }}</td>
                  <td>{{ amount(row.actualOutflow) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <h3>缺口措施（{{ trace.actions.length }}）</h3>
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="资金措施表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>计划日期</th>
                  <th>措施</th>
                  <th>计划金额</th>
                  <th>实际金额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in trace.actions" :key="row.id">
                  <td>{{ row.plannedDate }}</td>
                  <td>{{ label(row.actionType) }}</td>
                  <td>{{ amount(row.amount) }}</td>
                  <td>{{ amount(row.actualAmount) }}</td>
                  <td>{{ label(row.status) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
      </template>
    </template>
  </section>
</template>

<style scoped src="./finance-control.css"></style>
