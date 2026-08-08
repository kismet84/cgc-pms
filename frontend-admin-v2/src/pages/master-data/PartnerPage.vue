<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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
  V2StatusToggle,
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
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const changingStatus = ref(false)
const error = ref('')
const detailError = ref('')
const riskError = ref('')
const records = ref<PartnerRecord[]>([])
const partnerTypes = ref<DictOption[]>([])
const riskLevels = ref<DictDataRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const dialogOpen = ref(false)
const selectedPartnerId = ref('')
const detailRecord = ref<PartnerRecord | null>(null)
const editingId = ref<string | null>(null)
const deleteTarget = ref<PartnerRecord | null>(null)
const statusTarget = ref<PartnerRecord | null>(null)
let loadController: AbortController | null = null
let riskLevelsLoaded = false
let detailRequest = 0

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

function text(value: unknown): string {
  return value === null || value === undefined || value === '' ? '—' : String(value)
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
  const preferredSelection = selectedPartnerId.value
  clearDetail()
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
    if (loadController === controller) loading.value = false
    const selected = page.records.find((item) => item.id === preferredSelection)
    await selectPartner(selected?.id ?? page.records[0]?.id ?? '')
  } catch (value) {
    if (controller.signal.aborted) return
    records.value = []
    total.value = 0
    clearDetail()
    error.value = messageOf(value)
  } finally {
    if (loadController === controller) loading.value = false
  }
}

function search(): void {
  pageNo.value = 1
  void load()
}

function selectType(value: string): void {
  filters.partnerType = value
  search()
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

async function selectPartner(id: string): Promise<void> {
  const request = ++detailRequest
  selectedPartnerId.value = id
  detailRecord.value = null
  detailError.value = ''
  if (!id) {
    detailLoading.value = false
    return
  }
  detailLoading.value = true
  try {
    const detail = await loadPartner(id)
    if (request === detailRequest) detailRecord.value = detail
  } catch (value) {
    if (request === detailRequest) detailError.value = messageOf(value)
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}

function clearDetail(): void {
  detailRequest++
  selectedPartnerId.value = ''
  detailRecord.value = null
  detailError.value = ''
  detailLoading.value = false
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
    ...(editingId.value ? { partnerCode: form.partnerCode.trim() } : {}),
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

async function confirmStatus(): Promise<void> {
  if (!statusTarget.value) return
  changingStatus.value = true
  try {
    const detail = await loadPartner(statusTarget.value.id)
    const { id, ...current } = detail
    await updatePartner(id, {
      ...current,
      defaultLeadDays: current.defaultLeadDays ?? null,
      status: current.status === 'ENABLE' ? 'DISABLE' : 'ENABLE',
    })
    await loadPartner(id)
    statusTarget.value = null
    await load()
    showToast('success', '合作方状态已更新', '最新状态已刷新。')
  } catch (value) {
    showToast('error', '状态更新失败', messageOf(value))
  } finally {
    changingStatus.value = false
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

    <V2Card class="partner-workspace-card">
      <div class="partner-workspace">
        <section aria-labelledby="partner-types-title">
          <div class="partner-workspace__heading">
            <h2 id="partner-types-title">1. 类型</h2>
            <span>{{ typeOptions.length }} 个</span>
          </div>
          <div class="partner-type-list" role="group" aria-label="合作方类型">
            <V2Button
              size="medium"
              :variant="filters.partnerType ? 'ghost' : 'secondary'"
              :aria-pressed="!filters.partnerType"
              @click="selectType('')"
            >
              全部类型
            </V2Button>
            <V2Button
              v-for="option in typeOptions"
              :key="option.value"
              size="medium"
              :variant="filters.partnerType === option.value ? 'secondary' : 'ghost'"
              :aria-pressed="filters.partnerType === option.value"
              @click="selectType(option.value)"
            >
              {{ option.label }}
            </V2Button>
          </div>
        </section>

        <section aria-labelledby="partners-title">
          <div class="partner-workspace__heading">
            <h2 id="partners-title">2. 合作方</h2>
            <span>共 {{ total }} 条</span>
          </div>
          <V2PageState
            v-if="loading"
            kind="loading"
            title="正在读取合作方"
            description="请稍候。"
          />
          <V2PageState v-else-if="error" kind="error" title="合作方加载失败" :description="error">
            <template #actions><V2Button @click="load">重试</V2Button></template>
          </V2PageState>
          <V2PageState
            v-else-if="!records.length"
            kind="empty"
            title="暂无合作方"
            description="调整筛选条件后重试。"
          />
          <div v-else class="partner-list" role="listbox" aria-label="合作方">
            <article
              v-for="(record, index) in records"
              :key="record.id"
              class="partner-list__item"
              :class="{ 'partner-list__item--selected': selectedPartnerId === record.id }"
            >
              <V2Button
                class="partner-list__select"
                size="medium"
                :variant="selectedPartnerId === record.id ? 'secondary' : 'ghost'"
                role="option"
                :aria-selected="selectedPartnerId === record.id"
                :aria-label="`选择合作方 ${record.partnerCode}`"
                @click="selectPartner(record.id)"
              >
                <span>
                  <strong>{{ record.partnerName }}</strong>
                  <small>{{ record.partnerCode }} · {{ typeLabel(record.partnerType) }}</small>
                </span>
              </V2Button>
              <V2Badge
                :tone="record.blacklistFlag || record.riskLevel === 'HIGH' ? 'danger' : 'neutral'"
              >
                {{ record.blacklistFlag ? '黑名单' : riskLabel(record.riskLevel) }}
              </V2Badge>
              <V2StatusToggle
                :enabled="record.status === 'ENABLE'"
                :disabled="!canEdit || changingStatus"
                :aria-label="`${record.status === 'ENABLE' ? '停用' : '启用'}合作方 ${record.partnerName}`"
                @toggle="statusTarget = record"
              />
              <V2ActionMenu
                :label="`${record.partnerCode || record.partnerName}更多操作`"
                :placement="index >= records.length - 3 ? 'top-end' : 'bottom-end'"
              >
                <V2Button v-if="canEdit" size="small" variant="secondary" @click="openEdit(record)">
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
            </article>
          </div>
          <nav class="v2-pagination" aria-label="合作方分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              variant="secondary"
              size="small"
              :disabled="pageNo <= 1"
              @click="previousPage"
            >
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
        </section>

        <section aria-labelledby="partner-detail-title">
          <div class="partner-workspace__heading">
            <h2 id="partner-detail-title">3. 详情</h2>
          </div>
          <V2PageState
            v-if="detailLoading"
            kind="loading"
            title="正在读取合作方详情"
            description="请稍候。"
          />
          <V2PageState
            v-else-if="detailError"
            kind="error"
            title="合作方详情加载失败"
            :description="detailError"
          />
          <dl v-else-if="detailRecord" class="partner-detail__facts">
            <div>
              <dt>合作方名称</dt>
              <dd>{{ detailRecord.partnerName }}</dd>
            </div>
            <div>
              <dt>合作方编号</dt>
              <dd>{{ detailRecord.partnerCode }}</dd>
            </div>
            <div>
              <dt>合作方类型</dt>
              <dd>{{ typeLabel(detailRecord.partnerType) }}</dd>
            </div>
            <div>
              <dt>统一社会信用代码</dt>
              <dd>{{ text(detailRecord.creditCode) }}</dd>
            </div>
            <div>
              <dt>法定代表人</dt>
              <dd>{{ text(detailRecord.legalPerson) }}</dd>
            </div>
            <div>
              <dt>联系人</dt>
              <dd>{{ text(detailRecord.contactName) }}</dd>
            </div>
            <div>
              <dt>联系电话</dt>
              <dd>{{ text(detailRecord.contactPhone) }}</dd>
            </div>
            <div>
              <dt>开户银行</dt>
              <dd>{{ text(detailRecord.bankName) }}</dd>
            </div>
            <div>
              <dt>银行账号</dt>
              <dd>{{ text(detailRecord.bankAccount) }}</dd>
            </div>
            <div>
              <dt>资质等级</dt>
              <dd>{{ text(detailRecord.qualificationLevel) }}</dd>
            </div>
            <div>
              <dt>默认提前期</dt>
              <dd>{{ text(detailRecord.defaultLeadDays) }}</dd>
            </div>
            <div>
              <dt>风险等级</dt>
              <dd>{{ riskLabel(detailRecord.riskLevel) }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>
                <V2Badge :tone="detailRecord.status === 'ENABLE' ? 'success' : 'neutral'">
                  {{ detailRecord.status === 'ENABLE' ? '启用' : '停用' }}
                </V2Badge>
              </dd>
            </div>
            <div>
              <dt>黑名单</dt>
              <dd>{{ detailRecord.blacklistFlag ? '是' : '否' }}</dd>
            </div>
          </dl>
          <V2PageState v-else kind="empty" title="暂无合作方详情" description="请选择合作方。" />
        </section>
      </div>
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
        <V2Input v-model="form.partnerCode" label="合作方编号" disabled />
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
        <V2Select
          v-model="form.status"
          :options="statusOptions"
          label="状态"
          required
          :disabled="Boolean(editingId)"
        />
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
      :open="Boolean(statusTarget)"
      title="更新合作方状态"
      :description="
        statusTarget
          ? `确认${statusTarget.status === 'ENABLE' ? '停用' : '启用'}“${statusTarget.partnerName}”？`
          : ''
      "
      :danger="statusTarget?.status === 'ENABLE'"
      :loading="changingStatus"
      @close="statusTarget = null"
      @confirm="confirmStatus"
    />

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

.partner-workspace-card :deep(.v2-card__body) {
  min-height: 0;
  height: calc(100vh - 15rem);
  padding: 0;
}

.partner-workspace {
  display: grid;
  height: 100%;
  min-height: 32rem;
  grid-template-columns: minmax(11rem, 0.55fr) minmax(24rem, 1.15fr) minmax(18rem, 0.9fr);
}

.partner-workspace > section {
  min-width: 0;
  padding: var(--v2-space-4);
  overflow-y: auto;
}

.partner-workspace > section + section {
  border-left: var(--v2-border-width) solid var(--v2-color-border);
}

.partner-workspace__heading {
  position: sticky;
  z-index: 1;
  top: calc(var(--v2-space-4) * -1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
  margin: calc(var(--v2-space-4) * -1) calc(var(--v2-space-4) * -1) var(--v2-space-3);
  padding: var(--v2-space-4);
  background: var(--v2-color-surface);
}

.partner-workspace__heading h2 {
  margin: 0;
  font-size: var(--v2-font-size-17);
}

.partner-workspace__heading span,
.partner-list__select small {
  color: var(--v2-color-text-muted);
}

.partner-type-list,
.partner-list {
  display: grid;
  gap: var(--v2-space-2);
}

.partner-type-list > :deep(button) {
  width: 100%;
  justify-content: flex-start;
}

.partner-list__item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: var(--v2-space-2);
  padding: var(--v2-space-2);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.partner-list__item--selected {
  border-color: var(--v2-color-primary);
}

.partner-list__select {
  min-width: 0;
  justify-content: flex-start;
  text-align: left;
}

.partner-list__select span {
  display: grid;
  min-width: 0;
  gap: var(--v2-space-1);
}

.partner-list__select strong,
.partner-list__select small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.partner-detail__facts {
  display: grid;
  gap: var(--v2-space-3);
  margin: 0;
}

.partner-detail__facts > div {
  display: grid;
  grid-template-columns: minmax(7rem, 0.45fr) minmax(0, 1fr);
  gap: var(--v2-space-3);
  padding-bottom: var(--v2-space-2);
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
}

.partner-detail__facts dt {
  color: var(--v2-color-text-muted);
}

.partner-detail__facts dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.partner-workspace .v2-pagination {
  margin-top: var(--v2-space-4);
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

@media (max-width: 980px) {
  .partner-workspace-card :deep(.v2-card__body) {
    height: auto;
  }

  .partner-workspace {
    height: auto;
    min-height: 0;
    grid-template-columns: 1fr;
  }

  .partner-workspace > section {
    overflow: visible;
  }

  .partner-workspace > section + section {
    border-top: var(--v2-border-width) solid var(--v2-color-border);
    border-left: 0;
  }

  .partner-workspace__heading {
    position: static;
  }
}
</style>
