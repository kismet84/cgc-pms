<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  V2Badge,
  V2ActionMenu,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import {
  createPartner,
  deletePartner,
  loadPartner,
  loadPartners,
  loadPartnerTypes,
  updatePartner,
  type DictOption,
  type PartnerCommand,
  type PartnerRecord,
} from '@/services/master-data'
import { isApiClientError } from '@/services/request'
import { loadEnabledDictDataByCode, type DictDataRecord } from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const error = ref('')
const riskError = ref('')
const records = ref<PartnerRecord[]>([])
const partnerTypes = ref<DictOption[]>([])
const riskLevels = ref<DictDataRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const dialogOpen = ref(false)
const editingId = ref<string | null>(null)
const deleteTarget = ref<PartnerRecord | null>(null)
let loadController: AbortController | null = null
let riskLevelsLoaded = false

const filters = reactive({ partnerCode: '', partnerName: '', partnerType: '', status: '' })
const form = reactive({
  partnerCode: '',
  partnerName: '',
  partnerType: '',
  creditCode: '',
  legalPerson: '',
  contactName: '',
  contactPhone: '',
  bankName: '',
  bankAccount: '',
  qualificationLevel: '',
  blacklistFlag: false,
  riskLevel: '',
  defaultLeadDays: '',
  status: 'ENABLE',
})

const can = (permission: string) =>
  session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
  session.hasPermission(permission)
const canAdd = computed(() => can('partner:add'))
const canEdit = computed(() => can('partner:edit'))
const canDelete = computed(() => can('partner:delete'))
const typeOptions = computed(() =>
  partnerTypes.value
    .filter((item) => item.status === 'ENABLE')
    .map((item) => ({ value: item.dictValue, label: item.dictLabel })),
)
const statusOptions = [
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
]
const riskOptions = computed(() =>
  riskLevels.value.map((item) => ({ value: item.dictValue, label: item.dictLabel })),
)

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

function typeLabel(value: string): string {
  return partnerTypes.value.find((item) => item.dictValue === value)?.dictLabel ?? value
}

function riskLabel(value?: string | null): string {
  if (!value) return '未设置'
  return riskLevels.value.find((item) => item.dictValue === value)?.dictLabel ?? value
}

async function loadRiskLevels(signal: AbortSignal): Promise<void> {
  if (riskLevelsLoaded) return
  riskError.value = ''
  try {
    riskLevels.value = await loadEnabledDictDataByCode('partner_risk_level', signal)
    riskLevelsLoaded = true
  } catch (value) {
    if (signal.aborted) return
    riskLevels.value = []
    riskError.value = messageOf(value)
  }
}

function clearForm(): void {
  Object.assign(form, {
    partnerCode: '',
    partnerName: '',
    partnerType: '',
    creditCode: '',
    legalPerson: '',
    contactName: '',
    contactPhone: '',
    bankName: '',
    bankAccount: '',
    qualificationLevel: '',
    blacklistFlag: false,
    riskLevel: '',
    defaultLeadDays: '',
    status: 'ENABLE',
  })
}

function closeDialog(): void {
  dialogOpen.value = false
  editingId.value = null
  clearForm()
}

async function load(): Promise<void> {
  loadController?.abort()
  const controller = new AbortController()
  loadController = controller
  loading.value = true
  error.value = ''
  try {
    const [page, types] = await Promise.all([
      loadPartners(
        {
          pageNo: pageNo.value,
          pageSize: pageSize.value,
          partnerCode: filters.partnerCode,
          partnerName: filters.partnerName,
          partnerType: filters.partnerType,
          status: filters.status,
        },
        controller.signal,
      ),
      partnerTypes.value.length
        ? Promise.resolve(partnerTypes.value)
        : loadPartnerTypes(controller.signal),
      loadRiskLevels(controller.signal),
    ])
    records.value = page.records
    total.value = page.total
    partnerTypes.value = types
  } catch (value) {
    if (controller.signal.aborted) return
    records.value = []
    total.value = 0
    error.value = messageOf(value)
  } finally {
    if (loadController === controller) loading.value = false
  }
}

function search(): void {
  pageNo.value = 1
  void load()
}

function reset(): void {
  Object.assign(filters, { partnerCode: '', partnerName: '', partnerType: '', status: '' })
  search()
}

function previousPage(): void {
  if (pageNo.value <= 1) return
  pageNo.value--
  void load()
}

function nextPage(): void {
  if (pageNo.value * pageSize.value >= total.value) return
  pageNo.value++
  void load()
}

function openCreate(): void {
  clearForm()
  editingId.value = null
  dialogOpen.value = true
}

function openDetail(record: PartnerRecord): void {
  void router.push({ name: 'V2ShellPartnerDetail', params: { id: record.id } })
}

async function openEdit(record: PartnerRecord): Promise<void> {
  try {
    const detail = await loadPartner(record.id)
    editingId.value = detail.id
    Object.assign(form, {
      partnerCode: detail.partnerCode ?? '',
      partnerName: detail.partnerName ?? '',
      partnerType: detail.partnerType ?? '',
      creditCode: detail.creditCode ?? '',
      legalPerson: detail.legalPerson ?? '',
      contactName: detail.contactName ?? '',
      contactPhone: detail.contactPhone ?? '',
      bankName: detail.bankName ?? '',
      bankAccount: detail.bankAccount ?? '',
      qualificationLevel: detail.qualificationLevel ?? '',
      blacklistFlag: detail.blacklistFlag === 1,
      riskLevel: detail.riskLevel ?? '',
      defaultLeadDays: detail.defaultLeadDays == null ? '' : String(detail.defaultLeadDays),
      status: detail.status ?? 'ENABLE',
    })
    dialogOpen.value = true
  } catch (value) {
    clearForm()
    showToast('error', '无法打开合作方', messageOf(value))
  }
}

function command(): PartnerCommand | null {
  const name = form.partnerName.trim()
  const type = form.partnerType.trim()
  if (!name || !type) {
    showToast('warning', '信息不完整', '合作方名称和类型不能为空。')
    return null
  }
  const leadDays = form.defaultLeadDays.trim()
  if (leadDays && (!/^\d+$/.test(leadDays) || Number(leadDays) > 3650)) {
    showToast('warning', '默认提前期无效', '仅供应商可填写0到3650之间的整数。')
    return null
  }
  return {
    partnerCode: form.partnerCode.trim(),
    partnerName: name,
    partnerType: type,
    creditCode: form.creditCode.trim(),
    legalPerson: form.legalPerson.trim(),
    contactName: form.contactName.trim(),
    contactPhone: form.contactPhone.trim(),
    bankName: form.bankName.trim(),
    bankAccount: form.bankAccount.trim(),
    qualificationLevel: form.qualificationLevel.trim(),
    blacklistFlag: form.blacklistFlag ? 1 : 0,
    riskLevel: form.riskLevel || undefined,
    defaultLeadDays: type === 'SUPPLIER' && leadDays ? Number(leadDays) : null,
    status: form.status,
  }
}

async function save(): Promise<void> {
  const payload = command()
  if (!payload) return
  saving.value = true
  try {
    const id = editingId.value
    const savedId = id ?? String(await createPartner(payload))
    if (id) await updatePartner(id, payload)
    await loadPartner(savedId)
    closeDialog()
    await load()
    showToast('success', '合作方已保存', '最新资料已刷新。')
  } catch (value) {
    showToast('error', '保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deletePartner(deleteTarget.value.id)
    deleteTarget.value = null
    await load()
    showToast('success', '合作方已删除', '列表已刷新。')
  } catch (value) {
    showToast('error', '删除失败', messageOf(value))
  } finally {
    deleting.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => loadController?.abort())
</script>

<template>
  <V2Stack class="master-page" :gap="4">
    <V2Card title="合作方管理" :heading-level="1">
      <template #actions>
        <form class="v2-page-heading__filters" @submit.prevent="search">
          <V2Input
            v-model="filters.partnerCode"
            label="合作方编号"
            hide-label
            placeholder="合作方编号"
          />
          <V2Input
            v-model="filters.partnerName"
            label="合作方名称"
            hide-label
            placeholder="合作方名称"
          />
          <V2Select
            v-model="filters.partnerType"
            :options="typeOptions"
            label="合作方类型"
            hide-label
            placeholder="合作方类型"
            allow-empty
            @update:model-value="search"
          />
          <V2Select
            v-model="filters.status"
            :options="statusOptions"
            label="状态"
            hide-label
            placeholder="全部状态"
            allow-empty
            @update:model-value="search"
          />
          <V2Button type="submit" size="small">查询</V2Button>
          <V2Button variant="secondary" type="button" size="small" @click="reset">重置</V2Button>
        </form>
        <V2Button v-if="canAdd" size="small" @click="openCreate">新增合作方</V2Button>
      </template>
    </V2Card>

    <V2PageState
      v-if="riskError"
      kind="error"
      title="风险等级字典加载失败"
      :description="riskError"
    >
      <template #actions><V2Button @click="load">重试</V2Button></template>
    </V2PageState>

    <V2PageState v-if="loading" kind="loading" title="正在读取合作方" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="合作方加载失败" :description="error">
      <template #actions><V2Button @click="load">重试</V2Button></template>
    </V2PageState>
    <V2PageState
      v-else-if="!records.length"
      kind="empty"
      title="暂无合作方"
      description="调整筛选条件后重试。"
    />
    <V2Card v-else>
      <div class="master-page__table-wrap">
        <table class="v2-table--top">
          <thead>
            <tr>
              <th>编号</th>
              <th>名称</th>
              <th>类型</th>
              <th>联系人</th>
              <th>风险</th>
              <th>状态</th>
              <th class="v2-table-cell--actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(record, index) in records" :key="record.id">
              <th scope="row">
                <V2Button
                  size="small"
                  variant="ghost"
                  class="v2-table__record-link"
                  :aria-label="`打开合作方 ${record.partnerCode}`"
                  @click="openDetail(record)"
                >
                  {{ record.partnerCode }}
                </V2Button>
              </th>
              <td>{{ record.partnerName }}</td>
              <td>{{ typeLabel(record.partnerType) }}</td>
              <td>{{ record.contactName || '—' }}</td>
              <td>
                <V2Badge
                  :tone="record.blacklistFlag || record.riskLevel === 'HIGH' ? 'danger' : 'neutral'"
                >
                  {{ record.blacklistFlag ? '黑名单' : riskLabel(record.riskLevel) }}
                </V2Badge>
              </td>
              <td>
                <V2Badge :tone="record.status === 'ENABLE' ? 'success' : 'neutral'">
                  {{ record.status === 'ENABLE' ? '启用' : '停用' }}
                </V2Badge>
              </td>
              <td class="v2-table-cell--actions">
                <V2ActionMenu
                  :label="`${record.partnerCode || record.partnerName}更多操作`"
                  :placement="index >= records.length - 3 ? 'top-end' : 'bottom-end'"
                >
                  <V2Button
                    v-if="canEdit"
                    size="small"
                    variant="secondary"
                    @click="openEdit(record)"
                  >
                    编辑
                  </V2Button>
                  <V2Button
                    v-if="canDelete"
                    size="small"
                    variant="danger"
                    @click="deleteTarget = record"
                  >
                    删除
                  </V2Button>
                </V2ActionMenu>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <nav class="v2-pagination" aria-label="合作方分页">
          <span>共 {{ total }} 条</span>
          <V2Button variant="secondary" size="small" :disabled="pageNo <= 1" @click="previousPage">
            上一页
          </V2Button>
          <span>第 {{ pageNo }} 页</span>
          <V2Button
            variant="secondary"
            size="small"
            :disabled="pageNo * pageSize >= total"
            @click="nextPage"
          >
            下一页
          </V2Button>
        </nav>
      </template>
    </V2Card>

    <V2Dialog
      :open="dialogOpen"
      :title="editingId ? '编辑合作方' : '新增合作方'"
      description="保存后自动刷新；关闭即清空敏感字段。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="closeDialog"
    >
      <form id="partner-form" class="master-page__form" @submit.prevent="save">
        <V2Input
          v-model="form.partnerCode"
          label="合作方编号"
          :disabled="Boolean(editingId)"
          hint="新建时留空由服务端生成"
        />
        <V2Input v-model="form.partnerName" label="合作方名称" required />
        <V2Select v-model="form.partnerType" :options="typeOptions" label="合作方类型" required />
        <V2Input v-model="form.creditCode" label="统一社会信用代码" />
        <V2Input v-model="form.legalPerson" label="法定代表人" />
        <V2Input v-model="form.contactName" label="联系人" autocomplete="off" />
        <V2Input v-model="form.contactPhone" label="联系电话" type="tel" autocomplete="off" />
        <V2Input v-model="form.bankName" label="开户银行" autocomplete="off" />
        <V2Input v-model="form.bankAccount" label="银行账号" autocomplete="off" />
        <V2Input v-model="form.qualificationLevel" label="资质等级" />
        <V2Select v-model="form.riskLevel" :options="riskOptions" label="风险等级" allow-empty />
        <V2Input
          v-if="form.partnerType === 'SUPPLIER'"
          v-model="form.defaultLeadDays"
          label="默认提前期（天）"
        />
        <V2Select v-model="form.status" :options="statusOptions" label="状态" required />
        <label class="master-page__check">
          <input v-model="form.blacklistFlag" type="checkbox" />
          标记为黑名单
        </label>
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="closeDialog">取消</V2Button>
        <V2Button type="submit" form="partner-form" :loading="saving">保存</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除合作方"
      :description="
        deleteTarget ? `确认删除“${deleteTarget.partnerName}”？存在合同引用时服务端会拒绝。` : ''
      "
      danger
      :loading="deleting"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </V2Stack>
</template>

<style scoped>
.master-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

.master-page__table-wrap {
  overflow-x: auto;
}

.master-page__check {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
  min-height: var(--v2-control-height-touch);
}

@media (max-width: 48rem) {
  .master-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
