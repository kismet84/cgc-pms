<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { FundOutlined, ReloadOutlined, PlusOutlined, LinkOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import { useUserStore } from '@/stores/user'
import { getUserList, type SysUserBrief } from '@/api/modules/system'
import {
  closeCostCorrective,
  confirmCostForecast,
  createCostCorrective,
  createCostForecast,
  getCostControlOverview,
  getCostForecastTrace,
  submitCostCorrective,
  updateCostForecast,
} from '@/api/modules/costControl'
import type {
  CostControlOverview,
  CostControlRow,
  CorrectivePayload,
  ForecastInputItem,
  ForecastPayload,
} from '@/types/costControl'

const referenceStore = useReferenceStore()
const userStore = useUserStore()
const projects = computed(() => referenceStore.projects ?? [])
const users = ref<SysUserBrief[]>([])
const projectId = ref<string>()
const loading = ref(false)
const overview = ref<CostControlOverview>()
const forecastVisible = ref(false)
const correctiveVisible = ref(false)
const closeVisible = ref(false)
const traceVisible = ref(false)
const traceData = ref<CostControlRow>()
const activeAction = ref<CostControlRow>()
const isAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canMaintainForecast = computed(
  () => isAdmin.value || userStore.hasPermission('cost:forecast:maintain'),
)
const canConfirmForecast = computed(
  () => isAdmin.value || userStore.hasPermission('cost:forecast:confirm'),
)
const canMaintainCorrective = computed(
  () => isAdmin.value || userStore.hasPermission('cost:corrective:maintain'),
)
const canSubmitCorrective = computed(
  () => isAdmin.value || userStore.hasPermission('cost:corrective:submit'),
)

const forecastForm = reactive({
  forecastCode: '',
  forecastName: '',
  forecastDate: new Date().toISOString().slice(0, 10),
  remark: '',
  items: [] as ForecastInputItem[],
})
const correctiveForm = reactive<CorrectivePayload>({
  forecastId: '',
  actionCode: '',
  actionTitle: '',
  rootCause: '',
  actionPlan: '',
  expectedSavingAmount: 0,
  responsibleUserId: '',
  dueDate: '',
  remark: '',
})
const closeForm = reactive({ actualSavingAmount: 0, resultDescription: '' })

function text(row: CostControlRow | undefined, key: string): string {
  const value = row?.[key]
  return value == null ? '' : String(value)
}
function amount(row: CostControlRow | undefined, key: string): number {
  return Number(row?.[key] ?? 0)
}
function money(value: unknown): string {
  return Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}
function percent(value: unknown): string {
  return `${(Number(value ?? 0) * 100).toFixed(2)}%`
}
function statusLabel(status: unknown): string {
  return (
    {
      DRAFT: '草稿',
      ACTION_REQUIRED: '待纠偏',
      CONTROLLED: '已受控',
      SUPERSEDED: '已被替代',
      PENDING: '审批中',
      APPROVED: '已批准',
      REJECTED: '已驳回',
      CLOSED: '已关闭',
    }[String(status)] || String(status || '-')
  )
}

const target = computed(() => overview.value?.activeTarget)
const forecast = computed(() => overview.value?.latestForecast)
const hasTarget = computed(() => Boolean(target.value && target.value.id))
const hasForecast = computed(() => Boolean(forecast.value && forecast.value.id))
const targetColumns = [
  { title: '成本科目', dataIndex: 'subject_name', key: 'subject_name' },
  { title: '投标成本', dataIndex: 'bid_cost_amount', key: 'bid_cost_amount', align: 'right' },
  { title: '目标成本', dataIndex: 'target_amount', key: 'target_amount', align: 'right' },
  {
    title: '责任预算',
    dataIndex: 'responsibility_amount',
    key: 'responsibility_amount',
    align: 'right',
  },
  { title: '责任主体', dataIndex: 'responsibility_unit', key: 'responsibility_unit' },
]
const correctionColumns = [
  { title: '编号', dataIndex: 'action_code', key: 'action_code' },
  { title: '措施', dataIndex: 'action_title', key: 'action_title' },
  {
    title: '预计节约',
    dataIndex: 'expected_saving_amount',
    key: 'expected_saving_amount',
    align: 'right',
  },
  { title: '责任人', dataIndex: 'responsible_user_name', key: 'responsible_user_name' },
  { title: '完成期限', dataIndex: 'due_date', key: 'due_date' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '操作', key: 'actions', width: 150 },
]

async function load() {
  if (!projectId.value) {
    overview.value = undefined
    return
  }
  loading.value = true
  try {
    overview.value = await getCostControlOverview(projectId.value)
  } catch (error) {
    console.error(error)
    overview.value = undefined
    message.error('加载目标成本与动态利润数据失败')
  } finally {
    loading.value = false
  }
}

function openForecast() {
  if (!projectId.value || !hasTarget.value) return
  const latest = forecast.value
  const draft = text(latest, 'status') === 'DRAFT'
  forecastForm.forecastCode = draft ? text(latest, 'forecast_code') : `FC-${Date.now()}`
  forecastForm.forecastName = draft ? text(latest, 'forecast_name') : '完工成本滚动预测'
  forecastForm.forecastDate = draft
    ? text(latest, 'forecast_date')
    : new Date().toISOString().slice(0, 10)
  forecastForm.remark = draft ? text(latest, 'remark') : ''
  const previous = new Map(
    (overview.value?.forecastItems ?? []).map((item) => [
      String(item.cost_subject_id),
      Number(item.estimated_remaining_amount ?? 0),
    ]),
  )
  forecastForm.items = (overview.value?.forecastInputItems ?? []).map((item) => ({
    ...item,
    estimatedRemainingAmount: draft
      ? previous.get(String(item.cost_subject_id))
      : Number(item.recommended_remaining_amount ?? 0),
  }))
  forecastVisible.value = true
}

async function saveForecast(confirmAfterSave: boolean) {
  if (!projectId.value || !forecastForm.forecastCode || !forecastForm.forecastName) {
    message.warning('请完整填写预测编号和名称')
    return
  }
  const payload: ForecastPayload = {
    projectId: projectId.value,
    forecastCode: forecastForm.forecastCode,
    forecastName: forecastForm.forecastName,
    forecastDate: forecastForm.forecastDate,
    remark: forecastForm.remark,
    items: forecastForm.items.map((item) => ({
      costSubjectId: String(item.cost_subject_id),
      estimatedRemainingAmount: Number(item.estimatedRemainingAmount ?? 0),
    })),
  }
  try {
    const draftId = text(forecast.value, 'status') === 'DRAFT' ? text(forecast.value, 'id') : ''
    const saved = draftId
      ? await updateCostForecast(draftId, payload)
      : await createCostForecast(payload)
    if (confirmAfterSave) await confirmCostForecast(String(saved.id))
    message.success(confirmAfterSave ? '完工预测已确认并更新动态利润' : '完工预测草稿已保存')
    forecastVisible.value = false
    await load()
  } catch (error) {
    console.error(error)
  }
}

function openCorrective() {
  if (!hasForecast.value) return
  correctiveForm.forecastId = text(forecast.value, 'id')
  correctiveForm.actionCode = `CA-${Date.now()}`
  correctiveForm.actionTitle = '成本偏差纠偏措施'
  correctiveForm.rootCause = ''
  correctiveForm.actionPlan = ''
  correctiveForm.expectedSavingAmount = Math.max(0, amount(forecast.value, 'cost_variance_amount'))
  correctiveForm.responsibleUserId = ''
  correctiveForm.dueDate = ''
  correctiveForm.remark = ''
  correctiveVisible.value = true
}

async function saveCorrective() {
  try {
    await createCostCorrective({ ...correctiveForm })
    message.success('纠偏措施已建立')
    correctiveVisible.value = false
    await load()
  } catch (error) {
    console.error(error)
  }
}

async function submitAction(row: CostControlRow) {
  Modal.confirm({
    title: '提交纠偏审批？',
    content: '提交后将进入成本偏差纠偏审批流程。',
    onOk: async () => {
      await submitCostCorrective(String(row.id))
      message.success('已提交审批')
      await load()
    },
  })
}

function openClose(row: CostControlRow) {
  activeAction.value = row
  closeForm.actualSavingAmount = amount(row, 'expected_saving_amount')
  closeForm.resultDescription = ''
  closeVisible.value = true
}

async function closeAction() {
  if (!activeAction.value) return
  await closeCostCorrective(String(activeAction.value.id), closeForm)
  message.success('纠偏措施已关闭，预测状态已重新评估')
  closeVisible.value = false
  await load()
}

async function openTrace() {
  if (!hasForecast.value) return
  traceData.value = await getCostForecastTrace(text(forecast.value, 'id'))
  traceVisible.value = true
}

onMounted(async () => {
  await Promise.all([
    referenceStore.fetchProjects(),
    getUserList({ pageNo: 1, pageSize: 200 }).then(
      (result) => (users.value = result.records.filter((user) => user.status === 'ENABLE')),
    ),
  ])
})
</script>

<template>
  <div class="cost-control-page app-page">
    <header class="cc-head">
      <div>
        <a-breadcrumb
          ><a-breadcrumb-item>成本管理</a-breadcrumb-item
          ><a-breadcrumb-item>动态利润控制</a-breadcrumb-item></a-breadcrumb
        >
        <h1>目标成本与动态利润闭环</h1>
        <p>投标成本 → 目标成本 → 责任预算 → 承诺/实际成本 → 完工预测 → 纠偏 → 项目利润</p>
      </div>
      <div class="cc-actions">
        <a-select
          v-model:value="projectId"
          show-search
          option-filter-prop="label"
          placeholder="选择项目"
          style="width: 280px"
          @change="load"
        >
          <a-select-option
            v-for="project in projects"
            :key="project.id"
            :value="String(project.id)"
            :label="project.projectName"
            >{{ project.projectName }}</a-select-option
          >
        </a-select>
        <a-button :disabled="!projectId" @click="load"><ReloadOutlined />刷新</a-button>
      </div>
    </header>

    <a-spin :spinning="loading">
      <a-empty v-if="!projectId" description="请选择项目进入成本控制工作台" />
      <template v-else>
        <a-alert
          v-if="!hasTarget"
          type="warning"
          show-icon
          message="项目尚无已审批生效的目标成本与责任预算，不能创建完工预测。"
        />
        <section class="cc-kpis">
          <article>
            <span>投标成本</span><strong>{{ money(target?.total_bid_cost_amount) }}</strong>
          </article>
          <article>
            <span>目标 / 责任预算</span
            ><strong
              >{{ money(target?.total_target_amount) }} /
              {{ money(target?.total_responsibility_amount) }}</strong
            >
          </article>
          <article>
            <span>承诺 / 实际成本</span
            ><strong
              >{{ money(forecast?.committed_cost_amount) }} /
              {{ money(forecast?.actual_cost_amount) }}</strong
            >
          </article>
          <article>
            <span>完工预测成本</span
            ><strong>{{ money(forecast?.forecast_at_completion_amount) }}</strong>
          </article>
          <article :class="{ danger: amount(forecast, 'cost_variance_amount') > 0 }">
            <span>成本偏差</span><strong>{{ money(forecast?.cost_variance_amount) }}</strong>
          </article>
          <article>
            <span>预测利润 / 利润率</span
            ><strong
              >{{ money(forecast?.forecast_profit_amount) }} /
              {{ percent(forecast?.profit_margin) }}</strong
            >
          </article>
        </section>

        <section class="cc-panel">
          <div class="cc-panel-head">
            <div>
              <h2>成本基线与责任预算</h2>
              <p>当前生效版本：{{ target?.version_no || '-' }}，审批通过后不可直接修改。</p>
            </div>
          </div>
          <a-table
            :data-source="overview?.targetItems || []"
            :columns="targetColumns"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }"
              ><template
                v-if="
                  ['bid_cost_amount', 'target_amount', 'responsibility_amount'].includes(
                    column.dataIndex,
                  )
                "
                >{{ money(record[column.dataIndex]) }}</template
              ></template
            >
          </a-table>
        </section>

        <section class="cc-panel">
          <div class="cc-panel-head">
            <div>
              <h2>完工成本与动态利润</h2>
              <p>
                版本 {{ forecast?.version_no || '-' }} · {{ statusLabel(forecast?.status) }} · 公式
                COST_EAC_V1
              </p>
            </div>
            <div>
              <a-button :disabled="!hasForecast" @click="openTrace"
                ><LinkOutlined />全链追溯</a-button
              ><a-button
                v-if="canMaintainForecast"
                type="primary"
                :disabled="!hasTarget"
                @click="openForecast"
                ><FundOutlined />{{
                  forecast?.status === 'DRAFT' ? '编辑预测' : '新建预测'
                }}</a-button
              >
            </div>
          </div>
          <a-descriptions bordered size="small" :column="4">
            <a-descriptions-item label="合同收入">{{
              money(forecast?.contract_income_amount)
            }}</a-descriptions-item>
            <a-descriptions-item label="预计剩余成本">{{
              money(forecast?.estimated_remaining_amount)
            }}</a-descriptions-item>
            <a-descriptions-item label="预测利润">{{
              money(forecast?.forecast_profit_amount)
            }}</a-descriptions-item>
            <a-descriptions-item label="利润率">{{
              percent(forecast?.profit_margin)
            }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <section class="cc-panel">
          <div class="cc-panel-head">
            <div>
              <h2>成本偏差纠偏</h2>
              <p>正偏差预测必须建立措施、完成审批并登记结果后方可受控关闭。</p>
            </div>
            <a-button
              v-if="canMaintainCorrective"
              type="primary"
              :disabled="forecast?.status !== 'ACTION_REQUIRED'"
              @click="openCorrective"
              ><PlusOutlined />建立纠偏措施</a-button
            >
          </div>
          <a-table
            :data-source="overview?.correctiveActions || []"
            :columns="correctionColumns"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'expected_saving_amount'">{{
                money(record.expected_saving_amount)
              }}</template>
              <template v-else-if="column.dataIndex === 'status'"
                ><a-tag>{{ statusLabel(record.status) }}</a-tag></template
              >
              <template v-else-if="column.key === 'actions'"
                ><a-button
                  v-if="
                    canSubmitCorrective && ['DRAFT', 'REJECTED'].includes(String(record.status))
                  "
                  type="link"
                  size="small"
                  @click="submitAction(record)"
                  >提交审批</a-button
                ><a-button
                  v-if="canSubmitCorrective && record.status === 'APPROVED'"
                  type="link"
                  size="small"
                  @click="openClose(record)"
                  >登记结果</a-button
                ></template
              >
            </template>
          </a-table>
        </section>
      </template>
    </a-spin>

    <a-modal
      v-model:open="forecastVisible"
      title="完工成本滚动预测"
      width="980px"
      ok-text="确认预测"
      cancel-text="取消"
      @ok="saveForecast(true)"
    >
      <a-form layout="vertical"
        ><div class="cc-form-grid">
          <a-form-item label="预测编号" required
            ><a-input v-model:value="forecastForm.forecastCode" /></a-form-item
          ><a-form-item label="预测名称" required
            ><a-input v-model:value="forecastForm.forecastName" /></a-form-item
          ><a-form-item label="预测日期" required
            ><a-date-picker
              v-model:value="forecastForm.forecastDate"
              value-format="YYYY-MM-DD"
              style="width: 100%"
          /></a-form-item></div
      ></a-form>
      <a-table
        :data-source="forecastForm.items"
        row-key="cost_subject_id"
        size="small"
        :pagination="false"
        :scroll="{ y: 360 }"
      >
        <a-table-column title="科目" data-index="subject_name" />
        <a-table-column title="责任预算" data-index="responsibility_amount"
          ><template #default="{ record }">{{
            money(record.responsibility_amount)
          }}</template></a-table-column
        >
        <a-table-column title="承诺成本" data-index="committed_amount"
          ><template #default="{ record }">{{
            money(record.committed_amount)
          }}</template></a-table-column
        >
        <a-table-column title="实际成本" data-index="actual_amount"
          ><template #default="{ record }">{{
            money(record.actual_amount)
          }}</template></a-table-column
        >
        <a-table-column title="预计剩余成本" width="180"
          ><template #default="{ record }"
            ><a-input-number
              v-model:value="record.estimatedRemainingAmount"
              :min="0"
              :precision="2"
              style="width: 100%" /></template
        ></a-table-column>
      </a-table>
      <template #footer
        ><a-button @click="forecastVisible = false">取消</a-button
        ><a-button v-if="canMaintainForecast" @click="saveForecast(false)">保存草稿</a-button
        ><a-button
          v-if="canMaintainForecast && canConfirmForecast"
          type="primary"
          @click="saveForecast(true)"
          >确认预测</a-button
        ></template
      >
    </a-modal>

    <a-modal v-model:open="correctiveVisible" title="建立成本偏差纠偏措施" @ok="saveCorrective">
      <a-form layout="vertical"
        ><a-form-item label="措施编号" required
          ><a-input v-model:value="correctiveForm.actionCode" /></a-form-item
        ><a-form-item label="措施名称" required
          ><a-input v-model:value="correctiveForm.actionTitle" /></a-form-item
        ><a-form-item label="根因" required
          ><a-textarea v-model:value="correctiveForm.rootCause" /></a-form-item
        ><a-form-item label="行动方案" required
          ><a-textarea v-model:value="correctiveForm.actionPlan"
        /></a-form-item>
        <div class="cc-form-grid">
          <a-form-item label="预计节约" required
            ><a-input-number
              v-model:value="correctiveForm.expectedSavingAmount"
              :min="0.01"
              :precision="2" /></a-form-item
          ><a-form-item label="责任人" required
            ><a-select
              v-model:value="correctiveForm.responsibleUserId"
              show-search
              option-filter-prop="label"
              ><a-select-option
                v-for="user in users"
                :key="user.id"
                :value="String(user.id)"
                :label="user.realName || user.username"
                >{{ user.realName || user.username }}</a-select-option
              ></a-select
            ></a-form-item
          ><a-form-item label="完成期限" required
            ><a-date-picker v-model:value="correctiveForm.dueDate" value-format="YYYY-MM-DD"
          /></a-form-item></div
      ></a-form>
    </a-modal>

    <a-modal v-model:open="closeVisible" title="登记纠偏结果" @ok="closeAction"
      ><a-form layout="vertical"
        ><a-form-item label="实际节约金额" required
          ><a-input-number
            v-model:value="closeForm.actualSavingAmount"
            :min="0"
            :precision="2" /></a-form-item
        ><a-form-item label="结果说明" required
          ><a-textarea v-model:value="closeForm.resultDescription" /></a-form-item></a-form
    ></a-modal>
    <a-drawer v-model:open="traceVisible" title="成本与利润全链追溯" width="720">
      <pre class="cc-trace">{{ JSON.stringify(traceData, null, 2) }}</pre>
    </a-drawer>
  </div>
</template>

<style scoped>
.cost-control-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.cc-head,
.cc-panel-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
}
.cc-head h1 {
  margin: 8px 0 4px;
  font-size: 24px;
}
.cc-head p,
.cc-panel-head p {
  margin: 0;
  color: var(--text-secondary);
}
.cc-actions,
.cc-panel-head > div:last-child {
  display: flex;
  gap: 8px;
}
.cc-kpis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.cc-kpis article,
.cc-panel {
  padding: 16px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}
.cc-kpis article {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.cc-kpis span {
  color: var(--text-secondary);
}
.cc-kpis strong {
  font-size: 20px;
}
.cc-kpis .danger strong {
  color: #cf1322;
}
.cc-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.cc-panel h2 {
  margin: 0 0 4px;
  font-size: 17px;
}
.cc-form-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.cc-trace {
  white-space: pre-wrap;
  word-break: break-all;
  background: #f6f8fa;
  padding: 12px;
  border-radius: 8px;
}
@media (max-width: 900px) {
  .cc-head,
  .cc-panel-head {
    flex-direction: column;
  }
  .cc-kpis,
  .cc-form-grid {
    grid-template-columns: 1fr;
  }
  .cc-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
