<script setup lang="ts">
import type {
  CostControlAmountRow,
  CostControlOverview,
  CostCorrectiveCloseCommand,
  CostCorrectiveCommand,
  CostForecastCommand,
} from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  V2Alert,
  V2Button,
  V2Card,
  V2Dialog,
  V2GlassButton,
  V2Input,
  V2PageState,
  V2Select,
  useToastMessage,
} from '@/components'
import {
  closeCostCorrective,
  confirmCostForecast,
  createCostCorrective,
  createCostForecast,
  loadCostSubjectOptions,
  loadCostControl,
  loadCostForecastTrace,
  submitCostCorrective,
  updateCostCorrective,
  updateCostForecast,
} from '@/services/commercial'
import type { CostSubjectOption } from '@/services/commercial'
import { loadProjectUsers } from '@/services/projects'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'
const route = useRoute()
const session = useSessionStore()
const projectId = ref('')
const overview = ref<CostControlOverview | null>(null)
const trace = ref<CostControlOverview | null>(null)
const costSubjects = ref<CostSubjectOption[]>([])
const userOptions = ref<Array<{ value: string; label: string }>>([])
const loading = ref(false)
const actionBusy = ref(false)
const errorMessage = ref('')
const successMessage = useToastMessage()
const forecastOpen = ref(false)
const correctiveOpen = ref(false)
const closeOpen = ref(false)
const editingForecastId = ref('')
const editingCorrectiveId = ref('')
let controller: AbortController | null = null
let traceController: AbortController | null = null
let generation = 0
let traceGeneration = 0
const forecast = reactive<CostForecastCommand>({
  projectId: '',
  forecastCode: '',
  forecastName: '',
  forecastDate: '',
  items: [],
  remark: null,
  version: null,
})
const corrective = reactive<CostCorrectiveCommand>({
  forecastId: '',
  actionCode: '',
  actionTitle: '',
  rootCause: '',
  actionPlan: '',
  expectedSavingAmount: '',
  responsibleUserId: '',
  dueDate: '',
  remark: null,
  version: null,
})
const closing = reactive<CostCorrectiveCloseCommand>({
  actualSavingAmount: '',
  resultDescription: '',
  version: '',
})
const canQuery = computed(() => session.hasPermission('cost:control:query'))
const canForecast = computed(() => session.hasPermission('cost:forecast:maintain'))
const canConfirm = computed(() => session.hasPermission('cost:forecast:confirm'))
const canCorrective = computed(() => session.hasPermission('cost:corrective:maintain'))
const canSubmit = computed(() => session.hasPermission('cost:corrective:submit'))
const latest = computed(() => overview.value?.latestForecast ?? {})
const actions = computed(() => overview.value?.correctiveActions ?? [])
const inputItems = computed(() => overview.value?.forecastInputItems ?? [])
const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVING: '审批中',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  PENDING: '待执行',
  OPEN: '执行中',
  ACTION_REQUIRED: '需要纠偏',
  CLOSED: '已关闭',
  CONFIRMED: '已确认',
}
const text = (row: CostControlAmountRow, key: string) => String(row[key] ?? '')
const statusLabel = (value: string) => STATUS_LABELS[value] ?? '未知状态'
const costSubjectLabel = (id: string, index: number) => {
  const subject = costSubjects.value.find((item) => item.id === id)
  return subject ? `${subject.subjectCode} · ${subject.subjectName}` : `成本科目 ${index + 1}`
}
const traceRow = (key: string): CostControlAmountRow => {
  const value = (trace.value as unknown as Record<string, unknown> | null)?.[key]
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as CostControlAmountRow)
    : {}
}
const traceRows = (key: string): CostControlAmountRow[] => {
  const value = (trace.value as unknown as Record<string, unknown> | null)?.[key]
  return Array.isArray(value) ? (value as CostControlAmountRow[]) : []
}
const errorText = (e: unknown, f: string) =>
  isApiClientError(e) ? e.message : e instanceof Error ? e.message : f
const needsAuthoritativeReload = (e: unknown) =>
  isApiClientError(e) && (e.status === 409 || e.status === 422)
async function handleActionError(e: unknown, fallback: string) {
  const message = errorText(e, fallback)
  if (needsAuthoritativeReload(e)) await load()
  errorMessage.value = message
}
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
    if (!projectId.value) {
      overview.value = null
      return
    }
    const [value, subjects, users] = await Promise.all([
      loadCostControl(projectId.value, current.signal),
      loadCostSubjectOptions(current.signal),
      loadProjectUsers(current.signal).catch(() => null),
    ])
    if (token === generation) {
      overview.value = value
      costSubjects.value = subjects
      userOptions.value =
        users?.records
          .filter((user) => ['ACTIVE', 'ENABLE'].includes(user.status))
          .map((user) => ({
            value: user.id,
            label: user.realName ? `${user.realName}（${user.username}）` : user.username,
          })) ??
        (session.userInfo?.userId
          ? [
              {
                value: session.userInfo.userId,
                label: session.userInfo.username || '当前用户',
              },
            ]
          : [])
    }
  } catch (e) {
    if (!current.signal.aborted && token === generation) {
      overview.value = null
      errorMessage.value = errorText(e, '动态利润控制加载失败')
    }
  } finally {
    if (token === generation) loading.value = false
  }
}
function openForecast(row?: CostControlAmountRow) {
  editingForecastId.value = row ? text(row, 'id') : ''
  Object.assign(forecast, {
    projectId: projectId.value,
    forecastCode: row ? text(row, 'forecast_code') : '',
    forecastName: row ? text(row, 'forecast_name') : '',
    forecastDate: row ? text(row, 'forecast_date') : new Date().toISOString().slice(0, 10),
    items: inputItems.value.map((item) => ({
      costSubjectId: text(item, 'cost_subject_id'),
      estimatedRemainingAmount: text(item, 'recommended_remaining_amount'),
      remark: null,
    })),
    remark: row ? text(row, 'remark') : null,
    version: row ? text(row, 'version') : null,
  })
  forecastOpen.value = true
}
function validDecimal(value: string, positive = false) {
  return /^(?:0|[1-9]\d*)(?:\.\d+)?$/.test(value) && (positive ? !/^0(?:\.0+)?$/.test(value) : true)
}
async function saveForecast() {
  if (actionBusy.value) return
  if (
    !forecast.forecastCode.trim() ||
    !forecast.forecastName.trim() ||
    !forecast.forecastDate ||
    !forecast.items.length ||
    forecast.items.some((i) => !i.costSubjectId || !validDecimal(i.estimatedRemainingAmount))
  ) {
    errorMessage.value = '请完整填写预测信息与剩余成本'
    return
  }
  actionBusy.value = true
  try {
    const command = { ...forecast, items: forecast.items.map((i) => ({ ...i })) }
    if (editingForecastId.value) await updateCostForecast(editingForecastId.value, command)
    else await createCostForecast(command)
    forecastOpen.value = false
    successMessage.value = '完工预测已保存'
    await load()
  } catch (e) {
    await handleActionError(e, '完工预测保存失败')
  } finally {
    actionBusy.value = false
  }
}
async function confirmForecast() {
  const id = text(latest.value, 'id'),
    version = text(latest.value, 'version')
  if (actionBusy.value || !id || !version) return
  actionBusy.value = true
  try {
    await confirmCostForecast(id, version)
    successMessage.value = '完工预测已确认'
    await load()
  } catch (e) {
    await handleActionError(e, '完工预测确认失败')
  } finally {
    actionBusy.value = false
  }
}
async function showTrace() {
  const id = text(latest.value, 'id')
  if (!id) return
  traceController?.abort()
  const current = new AbortController()
  traceController = current
  const token = ++traceGeneration
  try {
    const value = await loadCostForecastTrace(id, current.signal)
    if (token === traceGeneration) trace.value = value
  } catch (e) {
    if (!current.signal.aborted && token === traceGeneration)
      errorMessage.value = errorText(e, '预测追溯加载失败')
  }
}
function openCorrective(row?: CostControlAmountRow) {
  editingCorrectiveId.value = row ? text(row, 'id') : ''
  Object.assign(corrective, {
    forecastId: row ? text(row, 'forecast_id') : text(latest.value, 'id'),
    actionCode: row ? text(row, 'action_code') : '',
    actionTitle: row ? text(row, 'action_title') : '',
    rootCause: row ? text(row, 'root_cause') : '',
    actionPlan: row ? text(row, 'action_plan') : '',
    expectedSavingAmount: row ? text(row, 'expected_saving_amount') : '',
    responsibleUserId: row ? text(row, 'responsible_user_id') : '',
    dueDate: row ? text(row, 'due_date') : '',
    remark: row ? text(row, 'remark') : null,
    version: row ? text(row, 'version') : null,
  })
  correctiveOpen.value = true
}
async function saveCorrective() {
  if (actionBusy.value) return
  if (
    !corrective.forecastId ||
    !corrective.actionCode.trim() ||
    !corrective.actionTitle.trim() ||
    !corrective.rootCause.trim() ||
    !corrective.actionPlan.trim() ||
    !validDecimal(corrective.expectedSavingAmount, true) ||
    !corrective.responsibleUserId ||
    !corrective.dueDate
  ) {
    errorMessage.value = '请完整填写纠偏措施'
    return
  }
  actionBusy.value = true
  try {
    if (editingCorrectiveId.value)
      await updateCostCorrective(editingCorrectiveId.value, { ...corrective })
    else await createCostCorrective({ ...corrective })
    correctiveOpen.value = false
    successMessage.value = '纠偏措施已保存'
    await load()
  } catch (e) {
    await handleActionError(e, '纠偏措施保存失败')
  } finally {
    actionBusy.value = false
  }
}
async function submitAction(row: CostControlAmountRow) {
  if (actionBusy.value) return
  actionBusy.value = true
  try {
    await submitCostCorrective(text(row, 'id'), text(row, 'version'))
    successMessage.value = '纠偏措施已提交'
    await load()
  } catch (e) {
    await handleActionError(e, '纠偏措施提交失败')
  } finally {
    actionBusy.value = false
  }
}
function openClose(row: CostControlAmountRow) {
  editingCorrectiveId.value = text(row, 'id')
  Object.assign(closing, {
    actualSavingAmount: '',
    resultDescription: '',
    version: text(row, 'version'),
  })
  closeOpen.value = true
}
async function closeAction() {
  if (
    actionBusy.value ||
    !validDecimal(closing.actualSavingAmount) ||
    !closing.resultDescription.trim()
  )
    return
  actionBusy.value = true
  try {
    await closeCostCorrective(editingCorrectiveId.value, { ...closing })
    closeOpen.value = false
    successMessage.value = '纠偏措施已关闭'
    await load()
  } catch (e) {
    await handleActionError(e, '纠偏措施关闭失败')
  } finally {
    actionBusy.value = false
  }
}
watch(() => route.fullPath, load, { immediate: true })
onBeforeUnmount(() => {
  controller?.abort()
  traceController?.abort()
})
</script>
<template>
  <div class="cost-page">
    <V2PageState
      v-if="!canQuery"
      title="无权访问动态利润控制"
      description="请联系管理员开通访问权限。"
      kind="forbidden"
    /><template v-else
      ><V2Alert v-if="errorMessage" tone="danger" title="动态利润操作未完成">{{
        errorMessage
      }}</V2Alert
      ><V2Card title="动态利润控制" :heading-level="1"
        ><template #actions
          ><V2Button variant="secondary" :loading="loading" @click="load">刷新</V2Button></template
        ></V2Card
      ><V2PageState
        v-if="loading"
        title="正在加载动态利润控制"
        description="正在读取完工预测、利润指标和纠偏措施。"
        kind="loading" /><template v-else-if="overview"
        ><V2Card title="最新完工预测"
          ><dl>
            <dt>预测编号</dt>
            <dd>{{ text(latest, 'forecast_code') || '—' }}</dd>
            <dt>完工成本</dt>
            <dd>{{ text(latest, 'forecast_at_completion_amount') || '—' }}</dd>
            <dt>预测利润</dt>
            <dd>{{ text(latest, 'forecast_profit_amount') || '—' }}</dd>
            <dt>成本偏差</dt>
            <dd>{{ text(latest, 'cost_variance_amount') || '—' }}</dd>
            <dt>状态</dt>
            <dd>{{ statusLabel(text(latest, 'status')) }}</dd>
          </dl>
          <template #footer
            ><div class="actions">
              <V2Button
                v-if="canForecast"
                variant="secondary"
                @click="openForecast(text(latest, 'id') ? latest : undefined)"
                >{{ text(latest, 'id') ? '编辑预测' : '新建预测' }}</V2Button
              ><V2Button
                v-if="canConfirm && text(latest, 'status') === 'DRAFT'"
                variant="secondary"
                :disabled="actionBusy"
                @click="confirmForecast"
                >确认预测</V2Button
              ><V2Button v-if="text(latest, 'id')" variant="secondary" @click="showTrace"
                >查看追溯</V2Button
              ><V2Button
                v-if="canCorrective && text(latest, 'status') === 'ACTION_REQUIRED'"
                variant="secondary"
                @click="openCorrective()"
                >新建纠偏措施</V2Button
              >
            </div></template
          ></V2Card
        ><V2Card title="纠偏措施"
          ><V2PageState
            v-if="!actions.length"
            title="暂无纠偏措施"
            description="当前项目尚未登记可执行的成本纠偏措施。"
            kind="empty"
          />
          <div
            v-else
            class="cost-page__table-wrap"
            role="region"
            aria-label="纠偏措施表格"
            tabindex="0"
          >
            <table class="cost-page__table">
              <thead>
                <tr>
                  <th scope="col">措施</th>
                  <th scope="col">预计节约</th>
                  <th scope="col">状态</th>
                  <th scope="col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in actions" :key="text(row, 'id')">
                  <td>{{ text(row, 'action_title') }}</td>
                  <td>{{ text(row, 'expected_saving_amount') }}</td>
                  <td>{{ statusLabel(text(row, 'status')) }}</td>
                  <td>
                    <div class="actions">
                      <V2Button
                        v-if="canCorrective && ['DRAFT', 'REJECTED'].includes(text(row, 'status'))"
                        variant="secondary"
                        @click="openCorrective(row)"
                        >编辑</V2Button
                      ><V2Button
                        v-if="canSubmit && ['DRAFT', 'REJECTED'].includes(text(row, 'status'))"
                        variant="secondary"
                        :disabled="actionBusy"
                        @click="submitAction(row)"
                        >提交</V2Button
                      ><V2Button
                        v-if="canSubmit && text(row, 'status') === 'APPROVED'"
                        @click="openClose(row)"
                        >关闭</V2Button
                      >
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div></V2Card
        ><V2Dialog
          :open="!!trace"
          title="预测追溯"
          panel-class="v2-detail-dialog"
          :close-on-backdrop="false"
          @close="trace = null"
          ><section class="v2-detail-dialog__section">
            <dl class="v2-detail-dialog__facts">
              <dt>预测编号</dt>
              <dd>{{ text(traceRow('forecast'), 'forecast_code') || '—' }}</dd>
              <dt>预测名称</dt>
              <dd>{{ text(traceRow('forecast'), 'forecast_name') || '—' }}</dd>
              <dt>预测日期</dt>
              <dd>{{ text(traceRow('forecast'), 'forecast_date') || '—' }}</dd>
              <dt>预测利润</dt>
              <dd>{{ text(traceRow('forecast'), 'forecast_profit_amount') || '—' }}</dd>
              <dt>状态</dt>
              <dd>{{ statusLabel(text(traceRow('forecast'), 'status')) }}</dd>
            </dl>
            <h3>目标版本</h3>
            <dl class="v2-detail-dialog__facts">
              <dt>版本</dt>
              <dd>
                {{ text(traceRow('target'), 'version_no') || '—' }} /
                {{ text(traceRow('target'), 'version_name') || '—' }}
              </dd>
              <dt>目标成本</dt>
              <dd>{{ text(traceRow('target'), 'total_target_amount') || '—' }}</dd>
            </dl>
            <h3>预测明细</h3>
            <V2PageState
              v-if="!traceRows('forecastItems').length"
              title="暂无预测明细"
              description="当前预测没有科目明细。"
            />
            <div
              v-else
              class="cost-page__table-wrap"
              role="region"
              aria-label="预测明细"
              tabindex="0"
            >
              <table class="cost-page__table">
                <thead>
                  <tr>
                    <th>成本科目</th>
                    <th>预计剩余成本</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(item, index) in traceRows('forecastItems')" :key="text(item, 'id')">
                    <td>{{ costSubjectLabel(text(item, 'cost_subject_id'), index) }}</td>
                    <td>{{ text(item, 'estimated_remaining_amount') || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <h3>纠偏与审批</h3>
            <dl class="v2-detail-dialog__facts">
              <dt>纠偏措施</dt>
              <dd>{{ traceRows('correctiveActions').length }} 项</dd>
              <dt>审批记录</dt>
              <dd>{{ traceRows('approvalInstances').length }} 条</dd>
            </dl>
          </section></V2Dialog
        ></template
      ><V2PageState
        v-else
        title="暂无动态利润数据"
        description="请选择项目，或先生成该项目的完工预测。"
        kind="empty"
    /></template>
    <V2Dialog
      :open="forecastOpen"
      :title="editingForecastId ? '编辑完工预测' : '新建完工预测'"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="forecastOpen = false"
      ><form id="cost-forecast-form" class="form" @submit.prevent="saveForecast">
        <V2Input v-model="forecast.forecastCode" label="预测编号" required /><V2Input
          v-model="forecast.forecastName"
          label="预测名称"
          required
        /><V2Input v-model="forecast.forecastDate" label="预测日期" type="date" required />
        <div v-for="(item, index) in forecast.items" :key="item.costSubjectId" class="item">
          <span>{{ costSubjectLabel(item.costSubjectId, index) }}</span
          ><V2Input v-model="item.estimatedRemainingAmount" label="预计剩余成本" required />
        </div>
      </form>
      <template #footer>
        <V2GlassButton
          text="取消"
          :disabled="actionBusy"
          :on-click="() => (forecastOpen = false)"
        />
        <V2Button type="submit" form="cost-forecast-form" :loading="actionBusy">保存预测</V2Button>
      </template></V2Dialog
    >
    <V2Dialog
      :open="correctiveOpen"
      :title="editingCorrectiveId ? '编辑纠偏措施' : '新建纠偏措施'"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="correctiveOpen = false"
      ><form id="cost-corrective-form" class="form" @submit.prevent="saveCorrective">
        <V2Input v-model="corrective.actionCode" label="措施编号" required /><V2Input
          v-model="corrective.actionTitle"
          label="措施标题"
          required
        /><V2Input v-model="corrective.rootCause" label="根因" required /><V2Input
          v-model="corrective.actionPlan"
          label="行动计划"
          required
        /><V2Input
          v-model="corrective.expectedSavingAmount"
          label="预计节约金额"
          required
        /><V2Select
          v-model="corrective.responsibleUserId"
          label="负责人"
          :options="userOptions"
          required
        /><V2Input v-model="corrective.dueDate" label="截止日期" type="date" required />
      </form>
      <template #footer>
        <V2GlassButton
          text="取消"
          :disabled="actionBusy"
          :on-click="() => (correctiveOpen = false)"
        />
        <V2Button type="submit" form="cost-corrective-form" :loading="actionBusy"
          >保存措施</V2Button
        >
      </template></V2Dialog
    >
    <V2Dialog
      :open="closeOpen"
      title="关闭纠偏措施"
      :close-on-backdrop="false"
      :close-disabled="actionBusy"
      @close="closeOpen = false"
      ><form id="cost-corrective-close-form" class="form" @submit.prevent="closeAction">
        <V2Input v-model="closing.actualSavingAmount" label="实际节约金额" required /><V2Input
          v-model="closing.resultDescription"
          label="结果说明"
          required
        />
      </form>
      <template #footer>
        <V2GlassButton text="取消" :disabled="actionBusy" :on-click="() => (closeOpen = false)" />
        <V2Button type="submit" form="cost-corrective-close-form" :loading="actionBusy"
          >确认关闭</V2Button
        >
      </template></V2Dialog
    >
  </div>
</template>
<style scoped>
.cost-page,
.form {
  display: grid;
  gap: var(--v2-space-4);
}
.actions {
  display: flex;
  gap: var(--v2-space-2);
  align-items: end;
  flex-wrap: wrap;
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
.cost-page__table-wrap {
  min-width: 0;
  overflow-x: auto;
}
.cost-page__table {
  min-width: 40rem;
}
.cost-page__table td:nth-child(2) {
  font-variant-numeric: tabular-nums;
}
.cost-page__table .actions {
  align-items: center;
  flex-wrap: nowrap;
}
.item {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--v2-space-2);
  align-items: end;
}
</style>
