<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { FundAccountCommand, FundAccountVO } from '@/types/cashbook'

const props = defineProps<{
  open: boolean
  accounts: FundAccountVO[]
  saving?: boolean
}>()

const emit = defineEmits<{
  close: []
  save: [command: FundAccountCommand, id?: string]
  toggle: [account: FundAccountVO]
}>()

const editingId = ref<string>()
const form = reactive<FundAccountCommand>({
  accountCode: '',
  accountName: '',
  accountType: 'BANK',
  bankName: '',
  bankAccountNo: '',
  openingDate: new Date().toISOString().slice(0, 10),
  openingBalance: '0.00',
  remark: '',
})

watch(
  () => props.open,
  (open) => {
    if (open && !editingId.value) reset()
  },
)

function reset() {
  editingId.value = undefined
  form.accountCode = ''
  form.accountName = ''
  form.accountType = 'BANK'
  form.bankName = ''
  form.bankAccountNo = ''
  form.openingDate = new Date().toISOString().slice(0, 10)
  form.openingBalance = '0.00'
  form.remark = ''
}

function edit(account: FundAccountVO) {
  editingId.value = account.id
  form.accountCode = account.accountCode
  form.accountName = account.accountName
  form.accountType = account.accountType
  form.bankName = account.bankName ?? ''
  form.bankAccountNo = account.bankAccountNo ?? ''
  form.openingDate = account.openingDate
  form.openingBalance = account.openingBalance
  form.remark = account.remark ?? ''
}

function save() {
  if (!form.accountCode.trim() || !form.accountName.trim())
    return message.warning('请填写账户编码和名称')
  if (!form.openingDate || Number(form.openingBalance) < 0)
    return message.warning('请填写合法期初信息')
  if (form.accountType === 'BANK' && (!form.bankName?.trim() || !form.bankAccountNo?.trim())) {
    return message.warning('银行账户必须填写开户行和完整账号')
  }
  emit('save', { ...form }, editingId.value)
}
</script>

<template>
  <a-modal :open="open" title="资金账户管理" width="920px" :footer="null" @cancel="emit('close')">
    <div class="fund-account-layout">
      <section class="account-list">
        <div class="section-heading">
          <strong>账户列表</strong><a-button size="small" @click="reset">新增</a-button>
        </div>
        <div class="account-table-wrap">
          <table>
            <thead>
              <tr>
                <th>编码</th>
                <th>名称</th>
                <th>类型</th>
                <th>账号</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="account in accounts" :key="account.id">
                <td>{{ account.accountCode }}</td>
                <td>{{ account.accountName }}</td>
                <td>{{ account.accountType === 'BANK' ? '银行' : '现金' }}</td>
                <td>{{ account.bankAccountNo || '-' }}</td>
                <td>{{ account.enabledFlag === 1 ? '启用' : '停用' }}</td>
                <td>
                  <a-button type="link" size="small" @click="edit(account)">编辑</a-button>
                  <a-button type="link" size="small" @click="emit('toggle', account)">
                    {{ account.enabledFlag === 1 ? '停用' : '启用' }}
                  </a-button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="account-form">
        <strong>{{ editingId ? '编辑账户' : '新增账户' }}</strong>
        <label><span>账户编码 *</span><input v-model="form.accountCode" maxlength="64" /></label>
        <label><span>账户名称 *</span><input v-model="form.accountName" maxlength="128" /></label>
        <label
          ><span>账户类型 *</span
          ><select v-model="form.accountType">
            <option value="CASH">现金</option>
            <option value="BANK">银行</option>
          </select></label
        >
        <label v-if="form.accountType === 'BANK'"
          ><span>开户行 *</span><input v-model="form.bankName" maxlength="128"
        /></label>
        <label v-if="form.accountType === 'BANK'"
          ><span>完整账号 *</span
          ><input v-model="form.bankAccountNo" maxlength="128" autocomplete="off"
        /></label>
        <label><span>期初日期 *</span><input v-model="form.openingDate" type="date" /></label>
        <label
          ><span>期初余额 *</span
          ><input v-model="form.openingBalance" type="number" min="0" step="0.01"
        /></label>
        <label><span>备注</span><textarea v-model="form.remark" maxlength="500" /></label>
        <a-button type="primary" :loading="saving" @click="save">保存账户</a-button>
      </section>
    </div>
  </a-modal>
</template>

<style scoped>
.fund-account-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 1fr);
  gap: 20px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.account-table-wrap {
  overflow-x: auto;
}
table {
  width: 100%;
  min-width: 560px;
  border-collapse: collapse;
}
th,
td {
  padding: 9px;
  border-bottom: 1px solid #eee;
  text-align: left;
  white-space: nowrap;
}
.account-form {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 14px;
  background: #f8fafc;
  border-radius: 8px;
}
label {
  display: grid;
  gap: 4px;
}
label span {
  font-size: 12px;
  color: #667085;
}
input,
select,
textarea {
  width: 100%;
  min-height: 34px;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
}
textarea {
  min-height: 62px;
}
@media (max-width: 760px) {
  .fund-account-layout {
    grid-template-columns: 1fr;
  }
}
</style>
