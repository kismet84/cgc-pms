<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { CashJournalCommand, CashJournalEntryVO, FundAccountVO } from '@/types/cashbook'
import type { ProjectVO } from '@/types/project'
import type { ContractVO } from '@/types/contract'

const props = defineProps<{
  open: boolean
  entry: CashJournalEntryVO | null
  accounts: FundAccountVO[]
  projects: ProjectVO[]
  contracts: ContractVO[]
  saving?: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [command: CashJournalCommand]
  projectChange: [projectId?: string]
}>()

const form = reactive<CashJournalCommand>({
  accountId: undefined,
  direction: 'OUT',
  amount: '',
  businessDate: new Date().toISOString().slice(0, 10),
  counterpartyName: '',
  summary: '',
  projectId: undefined,
  contractId: undefined,
})

const sourceLocked = computed(() => props.entry?.sourceType === 'PAY_RECORD')
const enabledAccounts = computed(() => props.accounts.filter((item) => item.enabledFlag === 1))
const filteredContracts = computed(() =>
  props.contracts.filter((item) => !form.projectId || item.projectId === form.projectId),
)

watch(
  () => [props.open, props.entry] as const,
  ([open, entry]) => {
    if (!open) return
    form.accountId = entry?.accountId
    form.direction = entry?.direction ?? 'OUT'
    form.amount = entry?.amount ?? ''
    form.businessDate = entry?.businessDate ?? new Date().toISOString().slice(0, 10)
    form.counterpartyName = entry?.counterpartyName ?? ''
    form.summary = entry?.summary ?? ''
    form.projectId = entry?.projectId
    form.contractId = entry?.contractId
  },
  { immediate: true },
)

function onProjectChange() {
  form.contractId = undefined
  emit('projectChange', form.projectId)
}

function submit() {
  if (!form.accountId) return message.warning('请选择资金账户')
  if (!form.amount || Number(form.amount) <= 0) return message.warning('请输入有效金额')
  if (!form.businessDate) return message.warning('请选择业务日期')
  if (!form.summary.trim()) return message.warning('请输入摘要')
  emit('submit', { ...form, summary: form.summary.trim() })
}
</script>

<template>
  <a-modal
    :open="open"
    :title="entry ? '编辑资金流水' : '登记资金流水'"
    :confirm-loading="saving"
    width="680px"
    @ok="submit"
    @cancel="emit('close')"
  >
    <div class="cash-journal-form-grid">
      <label>
        <span>资金账户 *</span>
        <select v-model="form.accountId">
          <option value="" disabled>请选择</option>
          <option v-for="account in enabledAccounts" :key="account.id" :value="account.id">
            {{ account.accountName }}（{{ account.accountType === 'BANK' ? account.bankAccountNo : '现金' }}）
          </option>
        </select>
      </label>
      <label>
        <span>收支方向 *</span>
        <select v-model="form.direction" :disabled="sourceLocked">
          <option value="IN">收入</option>
          <option value="OUT">支出</option>
        </select>
      </label>
      <label>
        <span>金额 *</span>
        <input v-model="form.amount" type="number" min="0.01" step="0.01" :disabled="sourceLocked" />
      </label>
      <label>
        <span>业务日期 *</span>
        <input v-model="form.businessDate" type="date" :disabled="sourceLocked" />
      </label>
      <label>
        <span>项目</span>
        <select v-model="form.projectId" :disabled="sourceLocked" @change="onProjectChange">
          <option :value="undefined">不关联</option>
          <option v-for="project in projects" :key="project.id" :value="project.id">
            {{ project.projectName }}
          </option>
        </select>
      </label>
      <label>
        <span>合同</span>
        <select v-model="form.contractId" :disabled="sourceLocked || !form.projectId">
          <option :value="undefined">不关联</option>
          <option v-for="contract in filteredContracts" :key="contract.id" :value="contract.id">
            {{ contract.contractName }}
          </option>
        </select>
      </label>
      <label>
        <span>往来单位</span>
        <input v-model="form.counterpartyName" maxlength="200" />
      </label>
      <label class="full-row">
        <span>摘要 *</span>
        <textarea v-model="form.summary" maxlength="500" />
      </label>
    </div>
  </a-modal>
</template>

<style scoped>
.cash-journal-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
label { display: grid; gap: 6px; color: #4b5563; }
label span { font-size: 13px; }
input,
select,
textarea { width: 100%; min-height: 36px; padding: 6px 10px; border: 1px solid #d9d9d9; border-radius: 6px; background: #fff; }
textarea { min-height: 80px; resize: vertical; }
.full-row { grid-column: 1 / -1; }
@media (max-width: 560px) { .cash-journal-form-grid { grid-template-columns: 1fr; } .full-row { grid-column: auto; } }
</style>
