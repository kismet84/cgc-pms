<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { V2ActionMenu, V2Button, V2Card, V2PageState, V2Pagination } from '@/components'
import { showToast } from '@/components/toast'
import {
  generateFinanceAlerts,
  handleFinanceAlert,
  loadFinanceOperationsWorkspace,
  rebuildFinanceSnapshot,
} from '@/services/finance'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import type { FinanceOperationsWorkspace } from '@cgc-pms/frontend-contracts'
import { amount, askReason, label, pageSlice } from './model'

const pageSize = 10
const session = useSessionStore()
const workspace = useWorkspaceStore()
const projectId = computed(() => workspace.selectedProjectId || '')
const canQuery = computed(() => session.hasPermission('finance:operations:query'))
const can = (permission: string) => session.hasPermission(permission)
const operations = ref<FinanceOperationsWorkspace | null>(null)
const schedulePageNo = ref(1)
const alertPageNo = ref(1)
const snapshotPageNo = ref(1)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
let controller: AbortController | null = null

const pagedSchedules = computed(() =>
  pageSlice(operations.value?.schedules ?? [], schedulePageNo.value),
)
const pagedAlerts = computed(() => pageSlice(operations.value?.alerts ?? [], alertPageNo.value))
const pagedSnapshots = computed(() =>
  pageSlice(operations.value?.snapshots ?? [], snapshotPageNo.value),
)

async function load(): Promise<void> {
  if (!canQuery.value) return
  schedulePageNo.value = 1
  alertPageNo.value = 1
  snapshotPageNo.value = 1
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    operations.value = await loadFinanceOperationsWorkspace(
      projectId.value || undefined,
      request.signal,
    )
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

async function refreshSnapshot(): Promise<void> {
  if (!projectId.value) {
    showToast('error', '请选择项目', '项目明细和项目写操作需要切换至具体项目。')
    return
  }
  await run(() => rebuildFinanceSnapshot(projectId.value), '财务快照已刷新')
}

async function generateAlerts(): Promise<void> {
  await run(generateFinanceAlerts, '资金预警已生成')
}

async function resolveAlert(id: string): Promise<void> {
  const reason = askReason('请输入预警处理说明')
  if (reason) await run(() => handleFinanceAlert(id, 'RESOLVED', reason), '预警已处理')
}

watch(projectId, () => void load(), { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-control">
    <V2PageState
      v-if="!canQuery"
      kind="error"
      title="无权访问资金运营"
      description="系统未加载任何财务数据。"
    />
    <template v-else>
      <V2Card title="资金运营" :heading-level="1">
        <template #actions>
          <div class="finance-control__actions">
            <V2Button
              v-if="projectId && can('finance:analytics:maintain')"
              size="small"
              @click="refreshSnapshot"
              >刷新快照</V2Button
            >
            <V2Button
              v-if="can('finance:operations:maintain')"
              size="small"
              variant="secondary"
              @click="generateAlerts"
              >生成预警</V2Button
            >
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace"
              >刷新</V2Button
            >
          </div>
        </template>
      </V2Card>
      <V2PageState
        v-if="loading && !operations"
        kind="loading"
        title="正在加载"
        description="正在读取资金运营。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="资金运营加载失败"
        :description="errorMessage"
        ><template #actions><V2Button @click="load">重试</V2Button></template></V2PageState
      >
      <template v-else-if="operations">
        <V2Card v-if="!projectId" title="企业资金概览" :heading-level="2">
          <div class="finance-control__metrics">
            <div>
              <span>企业资金余额</span><strong>{{ amount(operations.summary.fundBalance) }}</strong>
            </div>
            <div>
              <span>预测流入</span><strong>{{ amount(operations.summary.forecastInflow) }}</strong>
            </div>
            <div>
              <span>预测流出</span><strong>{{ amount(operations.summary.forecastOutflow) }}</strong>
            </div>
            <div>
              <span>非项目融资</span
              ><strong>{{ amount(operations.summary.financingAmount) }}</strong>
            </div>
            <div>
              <span>资金缺口</span><strong>{{ amount(operations.summary.fundingGap) }}</strong>
            </div>
            <div>
              <span>可访问项目</span><strong>{{ operations.summary.projectCount }}</strong>
            </div>
          </div>
        </V2Card>
        <V2Card v-else title="资金快照" :heading-level="2">
          <div v-if="operations.snapshots[0]" class="finance-control__metrics">
            <div>
              <span>合同额</span
              ><strong>{{ amount(operations.snapshots[0].contractAmount) }}</strong>
            </div>
            <div>
              <span>已付</span><strong>{{ amount(operations.snapshots[0].paidAmount) }}</strong>
            </div>
            <div>
              <span>流入</span><strong>{{ amount(operations.snapshots[0].cashInflow) }}</strong>
            </div>
            <div>
              <span>流出</span><strong>{{ amount(operations.snapshots[0].cashOutflow) }}</strong>
            </div>
            <div>
              <span>实际成本</span><strong>{{ amount(operations.snapshots[0].actualCost) }}</strong>
            </div>
            <div>
              <span>现金利润</span
              ><strong>{{ amount(operations.snapshots[0].profitAmount) }}</strong>
            </div>
          </div>
          <V2PageState
            v-else-if="!errorMessage"
            title="暂无资金快照"
            description="可使用重建快照动作生成最新快照。"
          />
        </V2Card>
        <V2Card v-if="!projectId" title="项目资金对比" :heading-level="2">
          <div
            v-if="operations.snapshots.length"
            class="finance-control__table-wrap"
            role="region"
            aria-label="项目资金对比表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>项目</th>
                  <th>快照日期</th>
                  <th>合同额</th>
                  <th>现金流入</th>
                  <th>现金流出</th>
                  <th>现金利润</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedSnapshots" :key="row.id">
                  <td>{{ row.projectName || '项目信息缺失' }}</td>
                  <td>{{ row.snapshotDate }}</td>
                  <td>{{ amount(row.contractAmount) }}</td>
                  <td>{{ amount(row.cashInflow) }}</td>
                  <td>{{ amount(row.cashOutflow) }}</td>
                  <td>{{ amount(row.profitAmount) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <V2PageState
            v-else-if="!errorMessage"
            title="暂无项目快照"
            description="切换至具体项目后可刷新项目快照。"
          />
          <template #footer
            ><V2Pagination
              :total="operations.snapshots.length"
              :page-no="snapshotPageNo"
              :page-size="pageSize"
              label="项目资金对比分页"
              @update:page-no="snapshotPageNo = $event"
          /></template>
        </V2Card>
        <V2Card title="付款预测" :heading-level="2">
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="付款计划表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>计划名称</th>
                  <th>计划日期</th>
                  <th>计划金额</th>
                  <th>已付金额</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedSchedules" :key="row.id">
                  <td>{{ row.scheduleName }}</td>
                  <td>{{ row.plannedDate }}</td>
                  <td>{{ amount(row.plannedAmount) }}</td>
                  <td>{{ amount(row.paidAmount) }}</td>
                  <td>{{ label(row.status) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer
            ><V2Pagination
              :total="operations.schedules.length"
              :page-no="schedulePageNo"
              :page-size="pageSize"
              label="付款计划分页"
              @update:page-no="schedulePageNo = $event"
          /></template>
        </V2Card>
        <V2Card title="资金预警" :heading-level="2">
          <div
            class="finance-control__table-wrap"
            role="region"
            aria-label="资金预警表格"
            tabindex="0"
          >
            <table class="v2-table finance-control__table">
              <thead>
                <tr>
                  <th>预警</th>
                  <th>等级</th>
                  <th>到期日期</th>
                  <th>状态</th>
                  <th class="v2-table-cell--actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in pagedAlerts" :key="row.id">
                  <td>{{ row.message }}</td>
                  <td>{{ label(row.severity) }}</td>
                  <td>{{ row.dueAt || '—' }}</td>
                  <td>{{ label(row.status) }}</td>
                  <td class="v2-table-cell--actions">
                    <V2ActionMenu
                      :label="`${row.message}更多操作`"
                      :placement="index >= pagedAlerts.length - 3 ? 'top-end' : 'bottom-end'"
                      ><V2Button
                        v-if="row.status === 'OPEN' && can('finance:operations:maintain')"
                        size="small"
                        variant="ghost"
                        @click="resolveAlert(row.id)"
                        >处理</V2Button
                      ></V2ActionMenu
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer
            ><V2Pagination
              :total="operations.alerts.length"
              :page-no="alertPageNo"
              :page-size="pageSize"
              label="资金预警分页"
              @update:page-no="alertPageNo = $event"
          /></template>
        </V2Card>
      </template>
    </template>
  </section>
</template>

<style scoped src="./finance-control.css"></style>
