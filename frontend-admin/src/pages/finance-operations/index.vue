<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import {
  createIntegrationEndpoint,
  createPaymentSchedule,
  generateFinanceAlerts,
  getFinanceAlerts,
  getFinanceSnapshots,
  getIntegrationEndpoints,
  getPaymentSchedules,
  handleFinanceAlert,
  rebuildFinanceSnapshot,
  runFinanceReconciliation,
  type FinanceRow,
} from '@/api/modules/financeOperations'
import { reversePayment } from '@/api/modules/payment'
import { useReferenceStore } from '@/stores/reference'

const referenceStore = useReferenceStore()
const { projects, contracts } = storeToRefs(referenceStore)
const loading = ref(false)
const alerts = ref<FinanceRow[]>([])
const schedules = ref<FinanceRow[]>([])
const snapshots = ref<FinanceRow[]>([])
const endpoints = ref<FinanceRow[]>([])
const reconcileResult = ref<FinanceRow>()
const scheduleOpen = ref(false)
const reversalOpen = ref(false)
const endpointOpen = ref(false)
const snapshotProjectId = ref<string>()

const scheduleForm = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  scheduleName: '',
  plannedDate: new Date().toISOString().slice(0, 10),
  plannedAmount: undefined as number | undefined,
  reminderDays: 7,
})
const reversalForm = reactive({
  payRecordId: '',
  reversalType: 'REVERSAL' as 'REVERSAL' | 'REFUND',
  externalTxnNo: '',
  reversedAt: nowText(),
  reason: '',
})
const endpointForm = reactive({
  endpointType: 'BANK', endpointCode: '', endpointName: '', baseUrl: '', credentialRef: '', callbackSecret: '',
})

function nowText() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
function str(row: FinanceRow, key: string) { return row[key] == null ? '-' : String(row[key]) }
function money(row: FinanceRow, key: string) { return Number(row[key] ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }

async function load() {
  loading.value = true
  try {
    ;[alerts.value, schedules.value, endpoints.value] = await Promise.all([
      getFinanceAlerts(), getPaymentSchedules(), getIntegrationEndpoints(),
    ])
  } finally { loading.value = false }
}
async function runReconcile() {
  reconcileResult.value = await runFinanceReconciliation(new Date().toISOString().slice(0, 10))
  message.success(`对账完成，发现 ${reconcileResult.value.issue_count ?? 0} 项差异`)
}
async function generateAlerts() {
  await generateFinanceAlerts(); alerts.value = await getFinanceAlerts(); message.success('预警扫描完成')
}
function resolveAlert(row: FinanceRow) {
  Modal.confirm({ title: '确认关闭该预警？', content: str(row, 'message'), async onOk() {
    await handleFinanceAlert(String(row.id), 'RESOLVED', '已在资金运营工作台核实处理')
    alerts.value = await getFinanceAlerts(); message.success('预警已处理')
  } })
}
async function saveSchedule() {
  if (!scheduleForm.projectId || !scheduleForm.contractId || !scheduleForm.scheduleName || !scheduleForm.plannedAmount) return message.warning('请完整填写付款计划')
  await createPaymentSchedule({ ...scheduleForm, projectId: scheduleForm.projectId, contractId: scheduleForm.contractId })
  scheduleOpen.value = false; schedules.value = await getPaymentSchedules(); message.success('付款计划已创建')
}
async function submitReversal() {
  if (!reversalForm.payRecordId || !reversalForm.externalTxnNo || !reversalForm.reason) return message.warning('请完整填写冲销信息')
  await reversePayment(reversalForm.payRecordId, {
    reversalType: reversalForm.reversalType, externalTxnNo: reversalForm.externalTxnNo,
    reversedAt: reversalForm.reversedAt, reason: reversalForm.reason,
  })
  reversalOpen.value = false; message.success(reversalForm.reversalType === 'REFUND' ? '退款已完成全链恢复' : '付款冲销已完成')
}
async function rebuildSnapshot() {
  if (!snapshotProjectId.value) return message.warning('请选择项目')
  await rebuildFinanceSnapshot(snapshotProjectId.value)
  snapshots.value = await getFinanceSnapshots(snapshotProjectId.value)
  message.success('驾驶舱快照已按业务事实重算')
}
async function saveEndpoint() {
  if (!endpointForm.endpointCode || !endpointForm.endpointName || !endpointForm.callbackSecret) return message.warning('请完整填写端点信息')
  await createIntegrationEndpoint({ ...endpointForm, config: {} })
  endpointOpen.value = false; endpoints.value = await getIntegrationEndpoints(); message.success('集成端点已创建')
}
function onScheduleProject(projectId: string) {
  scheduleForm.contractId = undefined
  referenceStore.fetchContracts({ projectId })
}

onMounted(async () => {
  await Promise.all([referenceStore.fetchProjects(), referenceStore.fetchContracts()])
  await load()
})
</script>

<template>
  <div class="finance-operations-page">
    <a-page-header title="资金运营中心" sub-title="付款异常、日终对账、到期预警、财务快照与外部集成统一入口">
      <template #extra>
        <a-button @click="reversalOpen = true">付款冲销/退款</a-button>
        <a-button @click="scheduleOpen = true">新建付款计划</a-button>
        <a-button type="primary" :loading="loading" @click="load">刷新</a-button>
      </template>
    </a-page-header>

    <a-row :gutter="16" class="summary-row">
      <a-col :span="6"><a-card><a-statistic title="开放预警" :value="alerts.length" /></a-card></a-col>
      <a-col :span="6"><a-card><a-statistic title="付款计划" :value="schedules.length" /></a-card></a-col>
      <a-col :span="6"><a-card><a-statistic title="集成端点" :value="endpoints.length" /></a-card></a-col>
      <a-col :span="6"><a-card><a-statistic title="最近对账差异" :value="Number(reconcileResult?.issue_count ?? 0)" /></a-card></a-col>
    </a-row>

    <a-tabs>
      <a-tab-pane key="alerts" tab="运营待办">
        <a-space class="toolbar"><a-button type="primary" @click="runReconcile">执行日终对账</a-button><a-button @click="generateAlerts">扫描到期/归档/发票预警</a-button></a-space>
        <a-table :data-source="alerts" row-key="id" :pagination="false" size="middle">
          <a-table-column title="类型" data-index="alert_type" /><a-table-column title="级别" data-index="severity" />
          <a-table-column title="业务" data-index="business_type" /><a-table-column title="说明" data-index="message" />
          <a-table-column title="截止时间" data-index="due_at" />
          <a-table-column title="操作"><template #default="{ record }"><a-button type="link" @click="resolveAlert(record)">完成处理</a-button></template></a-table-column>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="schedules" tab="付款计划">
        <a-table :data-source="schedules" row-key="id" :pagination="false">
          <a-table-column title="计划" data-index="schedule_name" /><a-table-column title="计划日期" data-index="planned_date" />
          <a-table-column title="计划金额"><template #default="{ record }">{{ money(record, 'planned_amount') }}</template></a-table-column>
          <a-table-column title="已付金额"><template #default="{ record }">{{ money(record, 'paid_amount') }}</template></a-table-column>
          <a-table-column title="状态" data-index="status" />
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="snapshots" tab="财务快照">
        <a-space class="toolbar"><a-select v-model:value="snapshotProjectId" placeholder="选择项目" style="width:280px"><a-select-option v-for="p in projects ?? []" :key="p.id" :value="p.id">{{ p.projectName }}</a-select-option></a-select><a-button type="primary" @click="rebuildSnapshot">增量刷新（可事实重算）</a-button></a-space>
        <a-table :data-source="snapshots" row-key="id" :pagination="false"><a-table-column title="日期" data-index="snapshot_date" /><a-table-column title="合同金额" data-index="contract_amount" /><a-table-column title="付款金额" data-index="paid_amount" /><a-table-column title="预算消耗" data-index="budget_consumed" /><a-table-column title="现金流出" data-index="cash_outflow" /><a-table-column title="利润" data-index="profit_amount" /><a-table-column title="口径" data-index="formula_version" /></a-table>
      </a-tab-pane>
      <a-tab-pane key="integrations" tab="外部集成">
        <a-space class="toolbar"><a-button type="primary" @click="endpointOpen = true">配置端点</a-button><span class="hint">密钥仅保存哈希；业务服务不直接访问任意外部 URL，消息由受控连接器租约派发。</span></a-space>
        <a-table :data-source="endpoints" row-key="id" :pagination="false"><a-table-column title="类型" data-index="endpoint_type" /><a-table-column title="编码" data-index="endpoint_code" /><a-table-column title="名称" data-index="endpoint_name" /><a-table-column title="凭据引用" data-index="credential_ref" /><a-table-column title="启用" data-index="enabled_flag" /></a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="scheduleOpen" title="新建付款计划" @ok="saveSchedule"><a-form layout="vertical"><a-form-item label="项目" required><a-select v-model:value="scheduleForm.projectId" @change="onScheduleProject"><a-select-option v-for="p in projects ?? []" :key="p.id" :value="p.id">{{ p.projectName }}</a-select-option></a-select></a-form-item><a-form-item label="合同" required><a-select v-model:value="scheduleForm.contractId"><a-select-option v-for="c in contracts ?? []" :key="c.id" :value="c.id">{{ c.contractName }}</a-select-option></a-select></a-form-item><a-form-item label="计划名称" required><a-input v-model:value="scheduleForm.scheduleName" /></a-form-item><a-form-item label="计划日期" required><a-input v-model:value="scheduleForm.plannedDate" type="date" /></a-form-item><a-form-item label="计划金额" required><a-input-number v-model:value="scheduleForm.plannedAmount" :min="0.01" :precision="2" style="width:100%" /></a-form-item><a-form-item label="提前提醒天数"><a-input-number v-model:value="scheduleForm.reminderDays" :min="0" /></a-form-item></a-form></a-modal>
    <a-modal v-model:open="reversalOpen" title="付款冲销 / 银行退款" @ok="submitReversal"><a-alert type="warning" show-icon message="仅已归档且未关联核验通过发票的成功付款可冲销；操作会同步恢复预算、来源、现金日记和凭证。" /><a-form layout="vertical" class="modal-form"><a-form-item label="付款记录 ID" required><a-input v-model:value="reversalForm.payRecordId" /></a-form-item><a-form-item label="类型"><a-radio-group v-model:value="reversalForm.reversalType"><a-radio value="REVERSAL">冲销</a-radio><a-radio value="REFUND">退款</a-radio></a-radio-group></a-form-item><a-form-item label="冲销流水号" required><a-input v-model:value="reversalForm.externalTxnNo" /></a-form-item><a-form-item label="冲销时间" required><a-input v-model:value="reversalForm.reversedAt" /></a-form-item><a-form-item label="原因" required><a-textarea v-model:value="reversalForm.reason" /></a-form-item></a-form></a-modal>
    <a-modal v-model:open="endpointOpen" title="配置外部财务端点" @ok="saveEndpoint"><a-form layout="vertical"><a-form-item label="类型"><a-select v-model:value="endpointForm.endpointType"><a-select-option v-for="t in ['BANK','E_INVOICE','ERP','GENERAL_LEDGER','TAX']" :key="t" :value="t">{{ t }}</a-select-option></a-select></a-form-item><a-form-item label="编码" required><a-input v-model:value="endpointForm.endpointCode" /></a-form-item><a-form-item label="名称" required><a-input v-model:value="endpointForm.endpointName" /></a-form-item><a-form-item label="基础 URL"><a-input v-model:value="endpointForm.baseUrl" /></a-form-item><a-form-item label="凭据引用"><a-input v-model:value="endpointForm.credentialRef" placeholder="例如 vault://finance/bank" /></a-form-item><a-form-item label="回调密钥" required><a-input-password v-model:value="endpointForm.callbackSecret" /></a-form-item></a-form></a-modal>
  </div>
</template>

<style scoped>
.finance-operations-page{padding:0 20px 24px}.summary-row{margin-bottom:16px}.toolbar{margin-bottom:16px}.hint{color:#667085}.modal-form{margin-top:16px}
</style>
