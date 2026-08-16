<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  V2Button,
  V2Card,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
} from '@/components'
import { showToast } from '@/components/toast'
import {
  createFundAccount,
  loadFundAccounts,
  loadManagedFundAccounts,
  updateFundAccount,
} from '@/services/finance'
import { localDateInputValue } from '@/services/workspace-context'
import { useSessionStore } from '@/stores/session'
import type { FundAccountCommand, FundAccountRecord } from '@cgc-pms/frontend-contracts'
import { amount, pageSlice } from './model'

interface FundAccountEditor {
  id: string
  accountCode: string
  accountName: string
  accountType: 'CASH' | 'BANK'
  accountingSubjectCode: '' | '1001' | '1002.01' | '1002.02' | '1002.03'
  bankName: string
  bankAccountNo: string
  openingDate: string
  openingBalance: string
  remark: string
}

const pageSize = 10
const session = useSessionStore()
const canQuery = computed(() => session.hasPermission('cashbook:journal:query'))
const canManageAccounts = computed(
  () =>
    session.hasPermission('cashbook:account:manage') ||
    session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN'),
)
const canAccess = computed(() => canQuery.value || canManageAccounts.value)
const accounts = ref<FundAccountRecord[]>([])
const pageNo = ref(1)
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const dialog = ref(false)
const editor = ref<FundAccountEditor | null>(null)
let controller: AbortController | null = null
const pagedAccounts = computed(() => pageSlice(accounts.value, pageNo.value))
const dialogTitle = computed(() => (editor.value?.id ? '编辑资金账户' : '新建资金账户'))
const accountingSubjectOptions = computed(() =>
  editor.value?.accountType === 'CASH'
    ? [{ value: '1001', label: '1001 · 库存现金' }]
    : [
        { value: '1002.01', label: '1002.01 · 基本账户' },
        { value: '1002.02', label: '1002.02 · 一般账户' },
        { value: '1002.03', label: '1002.03 · 项目专户' },
      ],
)

async function load(): Promise<void> {
  if (!canAccess.value) return
  pageNo.value = 1
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  errorMessage.value = ''
  try {
    accounts.value = canManageAccounts.value
      ? await loadManagedFundAccounts(request.signal)
      : await loadFundAccounts(request.signal)
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

function openFundAccount(account?: FundAccountRecord): void {
  editor.value = {
    id: account?.id ?? '',
    accountCode: account?.accountCode ?? '',
    accountName: account?.accountName ?? '',
    accountType: account?.accountType === 'CASH' ? 'CASH' : 'BANK',
    accountingSubjectCode: account?.accountingSubjectCode ?? '',
    bankName: account?.bankName ?? '',
    bankAccountNo: account?.bankAccountNo ?? '',
    openingDate: account?.openingDate ?? localDateInputValue(),
    openingBalance: account?.openingBalance ?? '0.00',
    remark: account?.remark ?? '',
  }
  dialog.value = true
}

function closeFundAccount(): void {
  if (busy.value) return
  dialog.value = false
  editor.value = null
}

async function saveFundAccount(): Promise<void> {
  const value = editor.value
  if (!value) return
  const missing = [
    ['账户编码', value.accountCode],
    ['账户名称', value.accountName],
    ['开户日期', value.openingDate],
    ['期初余额', value.openingBalance],
  ].find(([, field]) => !field?.trim())
  if (missing) {
    showToast('error', '资金账户保存失败', `${missing[0]}不能为空。`)
    return
  }
  const accountingSubjectCode = value.accountType === 'CASH' ? '1001' : value.accountingSubjectCode
  if (!accountingSubjectCode) {
    showToast('error', '资金账户保存失败', '银行账户必须选择基本账户、一般账户或项目专户。')
    return
  }
  if (
    value.accountType === 'BANK' &&
    !['1002.01', '1002.02', '1002.03'].includes(accountingSubjectCode)
  ) {
    showToast('error', '资金账户保存失败', '银行账户总账科目不合法，请重新选择。')
    return
  }
  const command: FundAccountCommand = {
    accountCode: value.accountCode.trim(),
    accountName: value.accountName.trim(),
    accountType: value.accountType,
    accountingSubjectCode,
    bankName: value.bankName.trim() || undefined,
    bankAccountNo: value.bankAccountNo.trim() || undefined,
    openingDate: value.openingDate,
    openingBalance: value.openingBalance.trim(),
    remark: value.remark.trim() || undefined,
  }
  busy.value = true
  try {
    if (value.id) await updateFundAccount(value.id, command)
    else await createFundAccount(command)
    dialog.value = false
    editor.value = null
    await load()
    showToast('success', value.id ? '资金账户已更新' : '资金账户已创建', '资金账户列表已刷新。')
  } catch (cause) {
    showToast('error', '资金账户保存失败', cause instanceof Error ? cause.message : '请稍后重试。')
  } finally {
    busy.value = false
  }
}

void load()
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="finance-control">
    <V2PageState
      v-if="!canAccess"
      kind="error"
      title="无权访问资金账户"
      description="系统未加载任何财务数据。"
    />
    <template v-else>
      <V2Card title="资金账户" :heading-level="1">
        <template #actions>
          <div class="finance-control__actions">
            <V2Button size="small" variant="secondary" :loading="loading" @click="refreshWorkspace">
              刷新
            </V2Button>
          </div>
        </template>
      </V2Card>
      <V2PageState
        v-if="loading && !accounts.length"
        kind="loading"
        title="正在加载"
        description="正在读取资金账户。"
      />
      <V2PageState
        v-else-if="errorMessage"
        kind="error"
        title="资金账户加载失败"
        :description="errorMessage"
        ><template #actions><V2Button @click="load">重试</V2Button></template></V2PageState
      >
      <V2Card v-else title="资金账户清单" :heading-level="2">
        <template #actions
          ><V2Button v-if="canManageAccounts" size="small" @click="openFundAccount()"
            >新建资金账户</V2Button
          ></template
        >
        <div
          class="finance-control__table-wrap"
          role="region"
          aria-label="资金账户表格"
          tabindex="0"
        >
          <table class="v2-table finance-control__table">
            <thead>
              <tr>
                <th>账户编码</th>
                <th>账户名称</th>
                <th>开户行</th>
                <th>总账科目</th>
                <th>期初余额</th>
                <th>状态</th>
                <th v-if="canManageAccounts">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in pagedAccounts" :key="row.id">
                <td>{{ row.accountCode }}</td>
                <td>{{ row.accountName }}</td>
                <td>{{ row.bankName || '—' }}</td>
                <td>{{ row.accountingSubjectCode || '待配置' }}</td>
                <td>{{ amount(row.openingBalance) }}</td>
                <td>{{ row.enabledFlag === 1 ? '启用' : '停用' }}</td>
                <td v-if="canManageAccounts">
                  <V2Button size="small" variant="secondary" @click="openFundAccount(row)">
                    编辑
                  </V2Button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer
          ><V2Pagination
            :total="accounts.length"
            :page-no="pageNo"
            :page-size="pageSize"
            label="资金账户分页"
            @update:page-no="pageNo = $event"
        /></template>
      </V2Card>
      <V2Dialog
        v-model:open="dialog"
        :title="dialogTitle"
        :close-disabled="busy"
        :close-on-backdrop="false"
        @update:open="(open) => !open && closeFundAccount()"
      >
        <form
          v-if="editor"
          id="fund-account-form"
          class="finance-control__form"
          @submit.prevent="saveFundAccount"
        >
          <V2Input v-model="editor.accountCode" label="账户编码" required /><V2Input
            v-model="editor.accountName"
            label="账户名称"
            required
          />
          <V2Select
            v-model="editor.accountType"
            label="账户类型"
            :options="[
              { value: 'BANK', label: '银行账户' },
              { value: 'CASH', label: '现金账户' },
            ]"
            required
          />
          <V2Select
            v-model="editor.accountingSubjectCode"
            label="正式总账科目"
            :options="accountingSubjectOptions"
            required
          />
          <V2Input v-model="editor.bankName" label="开户行" /><V2Input
            v-model="editor.bankAccountNo"
            label="银行账号"
          /><V2Input v-model="editor.openingDate" type="date" label="开户日期" required />
          <V2Input
            v-model="editor.openingBalance"
            label="期初余额"
            :decimal-scale="2"
            required
            hint="金额按服务端十进制字符串提交"
          /><V2Input v-model="editor.remark" label="备注" />
        </form>
        <template #footer
          ><V2Button type="button" variant="secondary" :disabled="busy" @click="closeFundAccount"
            >取消</V2Button
          ><V2Button type="submit" form="fund-account-form" :loading="busy"
            >保存</V2Button
          ></template
        >
      </V2Dialog>
    </template>
  </section>
</template>

<style scoped src="./finance-control.css"></style>
