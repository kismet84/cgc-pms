<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  V2ActionMenu,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import { isApiClientError } from '@/services/request'
import {
  createDictData,
  createDictGroup,
  createDictType,
  deleteDictData,
  deleteDictGroup,
  deleteDictType,
  loadDictTree,
  updateDictData,
  updateDictGroup,
  updateDictType,
  type DictDataRecord,
  type DictGroupRecord,
  type DictGroupTreeRecord,
  type DictTreeType,
} from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

type DeleteKind = 'group' | 'type' | 'data'
type ActiveLevel = 'group' | 'type' | 'data'

const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const tree = ref<DictGroupTreeRecord[]>([])
const keyword = ref('')
const statusFilter = ref('')
const selectedGroupId = ref('')
const selectedTypeId = ref('')
const selectedDataId = ref('')
const pageNo = ref(1)
const pageSize = 10
const groupDialog = ref(false)
const typeDialog = ref(false)
const dataDialog = ref(false)
const editingGroup = ref<DictGroupRecord | null>(null)
const editingType = ref<DictTreeType | null>(null)
const editingData = ref<DictDataRecord | null>(null)
const deleteTarget = ref<{ kind: DeleteKind; id: string; label: string } | null>(null)
let controller: AbortController | null = null

const groupForm = reactive({ groupCode: '', groupName: '', orderNum: '0', status: 'ENABLE' })
const typeForm = reactive({ dictCode: '', dictName: '', dictClass: '', status: 'ENABLE' })
const dataForm = reactive({
  dictLabel: '',
  dictValue: '',
  cssClass: '',
  listClass: '',
  orderNum: '0',
  status: 'ENABLE',
})
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
]
const canAdd = computed(() => session.isAdmin)
const canEdit = computed(() => session.isAdmin)
const canDelete = computed(() => session.isAdmin)
const selectedGroup = computed(() => tree.value.find((item) => item.id === selectedGroupId.value))
const selectedType = computed(() =>
  selectedGroup.value?.types.find((item) => item.id === selectedTypeId.value),
)
const selectedData = computed(() =>
  selectedType.value?.data.find((item) => item.id === selectedDataId.value),
)
const protectedType = (item?: DictTreeType | null) =>
  item?.dictClass === 'SYSTEM' || item?.dictClass === 'STATE_MACHINE'
const protectedData = (item: DictDataRecord) =>
  protectedType(selectedType.value) ||
  (selectedType.value?.dictCode === 'invoice_type' &&
    ['VAT_SPECIAL', 'VAT_NORMAL', 'OTHER'].includes(item.dictValue))
const matchesStatus = (status: string) => !statusFilter.value || status === statusFilter.value
const typeMatchesStatus = (item: DictTreeType) =>
  matchesStatus(item.status) || item.data.some((entry) => matchesStatus(entry.status))
const groups = computed(() =>
  tree.value.filter(
    (item) => matchesStatus(item.status) || item.types.some((type) => typeMatchesStatus(type)),
  ),
)
const types = computed(() => (selectedGroup.value?.types ?? []).filter(typeMatchesStatus))
const data = computed(() =>
  (selectedType.value?.data ?? []).filter((item) => matchesStatus(item.status)),
)
const activeLevel = computed<ActiveLevel>(() =>
  selectedType.value ? 'data' : selectedGroup.value ? 'type' : 'group',
)
const activeRows = computed(() =>
  activeLevel.value === 'group'
    ? groups.value
    : activeLevel.value === 'type'
      ? types.value
      : data.value,
)
const activeTotal = computed(() => activeRows.value.length)
const pagedRows = computed(() =>
  activeRows.value.slice((pageNo.value - 1) * pageSize, pageNo.value * pageSize),
)
const pagedGroups = computed(() =>
  activeLevel.value === 'group' ? (pagedRows.value as DictGroupTreeRecord[]) : groups.value,
)
const pagedTypes = computed(() =>
  activeLevel.value === 'type' ? (pagedRows.value as DictTreeType[]) : types.value,
)
const pagedData = computed(() =>
  activeLevel.value === 'data' ? (pagedRows.value as DictDataRecord[]) : [],
)
const paginationLabel = computed(
  () => ({ group: '字典分组分页', type: '字典类型分页', data: '字典项分页' })[activeLevel.value],
)

async function refresh(options: { locate?: boolean; notify?: boolean } = {}): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    tree.value = await loadDictTree(keyword.value.trim(), current.signal)
    reconcileSelection(Boolean(options.locate))
    if (options.notify) showToast('success', '字典已刷新')
  } catch (value) {
    if (!current.signal.aborted) {
      tree.value = []
      clearSelection()
      error.value = messageOf(value)
    }
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshPage(): Promise<void> {
  await refresh()
  if (!error.value) showToast('success', '字典已刷新')
}

function reconcileSelection(locate: boolean): void {
  if (locate) {
    const group = groups.value[0]
    const type = group?.types.find((item) => matchesStatus(item.status))
    const item = type?.data.find((entry) => matchesStatus(entry.status))
    selectedGroupId.value = group?.id ?? ''
    selectedTypeId.value = type?.id ?? ''
    selectedDataId.value = item?.id ?? ''
    pageNo.value = 1
    return
  }
  if (!groups.value.some((item) => item.id === selectedGroupId.value)) clearSelection()
  else if (!types.value.some((item) => item.id === selectedTypeId.value)) clearTypeSelection()
  else if (!selectedData.value || !matchesStatus(selectedData.value.status))
    selectedDataId.value = ''
  clampPage()
}

function clearSelection(): void {
  selectedGroupId.value = ''
  clearTypeSelection()
}

function clearTypeSelection(): void {
  selectedTypeId.value = ''
  selectedDataId.value = ''
  pageNo.value = 1
}

function clampPage(): void {
  pageNo.value = Math.min(pageNo.value, Math.max(1, Math.ceil(activeTotal.value / pageSize)))
}

function search(): void {
  clearSelection()
  void refresh({ locate: Boolean(keyword.value.trim()) })
}

function changeStatus(): void {
  clearSelection()
}

function selectGroup(id: string): void {
  selectedGroupId.value = id
  clearTypeSelection()
}

function selectType(id: string): void {
  selectedTypeId.value = id
  selectedDataId.value = ''
  pageNo.value = 1
}

function selectData(id: string): void {
  selectedDataId.value = id
}

function openGroupEditor(item?: DictGroupRecord): void {
  editingGroup.value = item ?? null
  Object.assign(groupForm, {
    groupCode: item?.groupCode ?? '',
    groupName: item?.groupName ?? '',
    orderNum: String(item?.orderNum ?? 0),
    status: item?.status ?? 'ENABLE',
  })
  groupDialog.value = true
}

async function saveGroup(): Promise<void> {
  if (!groupForm.groupCode.trim() || !groupForm.groupName.trim()) {
    showToast('warning', '信息不完整', '分组编码和名称不能为空。')
    return
  }
  saving.value = true
  try {
    const command = {
      groupCode: groupForm.groupCode.trim(),
      groupName: groupForm.groupName.trim(),
      orderNum: Number(groupForm.orderNum) || 0,
      status: groupForm.status,
    }
    const id = editingGroup.value
      ? (await updateDictGroup(editingGroup.value.id, command), editingGroup.value.id)
      : await createDictGroup(command)
    keyword.value = ''
    statusFilter.value = ''
    selectedGroupId.value = id
    groupDialog.value = false
    await refresh()
    showToast('success', '字典分组已保存', '最新服务端数据已载入。')
  } catch (value) {
    showToast('error', '字典分组保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openTypeEditor(item?: DictTreeType): void {
  if (!selectedGroupId.value) return
  editingType.value = item ?? null
  Object.assign(typeForm, {
    dictCode: item?.dictCode ?? '',
    dictName: item?.dictName ?? '',
    dictClass: item?.dictClass ?? 'BUSINESS',
    status: item?.status ?? 'ENABLE',
  })
  typeDialog.value = true
}

async function saveType(): Promise<void> {
  if (
    !selectedGroupId.value ||
    !typeForm.dictCode.trim() ||
    !typeForm.dictName.trim() ||
    !typeForm.dictClass.trim()
  ) {
    showToast('warning', '信息不完整', '字典编码、名称和分类不能为空。')
    return
  }
  saving.value = true
  try {
    const command = {
      groupId: selectedGroupId.value,
      dictCode: typeForm.dictCode.trim(),
      dictName: typeForm.dictName.trim(),
      dictClass: typeForm.dictClass.trim(),
      status: typeForm.status,
    }
    const id = editingType.value
      ? (await updateDictType(editingType.value.id, command), editingType.value.id)
      : await createDictType(command)
    keyword.value = ''
    statusFilter.value = ''
    selectedTypeId.value = id
    typeDialog.value = false
    await refresh()
    showToast('success', '字典类型已保存', '最新服务端数据已载入。')
  } catch (value) {
    showToast('error', '字典类型保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function openDataEditor(item?: DictDataRecord): void {
  if (!selectedTypeId.value) return
  editingData.value = item ?? null
  Object.assign(dataForm, {
    dictLabel: item?.dictLabel ?? '',
    dictValue: item?.dictValue ?? '',
    cssClass: item?.cssClass ?? '',
    listClass: item?.listClass ?? '',
    orderNum: String(item?.orderNum ?? 0),
    status: item?.status ?? 'ENABLE',
  })
  dataDialog.value = true
}

async function saveData(): Promise<void> {
  if (!selectedTypeId.value || !dataForm.dictLabel.trim() || !dataForm.dictValue.trim()) {
    showToast('warning', '信息不完整', '字典标签和值不能为空。')
    return
  }
  saving.value = true
  try {
    const command = {
      dictTypeId: selectedTypeId.value,
      dictLabel: dataForm.dictLabel.trim(),
      dictValue: dataForm.dictValue.trim(),
      cssClass: dataForm.cssClass.trim(),
      listClass: dataForm.listClass.trim(),
      orderNum: Number(dataForm.orderNum) || 0,
      status: dataForm.status,
    }
    const id = editingData.value
      ? (await updateDictData(editingData.value.id, command), editingData.value.id)
      : await createDictData(command)
    keyword.value = ''
    statusFilter.value = ''
    selectedDataId.value = id
    dataDialog.value = false
    await refresh()
    showToast('success', '字典项已保存', '最新服务端数据已载入。')
  } catch (value) {
    showToast('error', '字典项保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  saving.value = true
  try {
    if (deleteTarget.value.kind === 'group') await deleteDictGroup(deleteTarget.value.id)
    else if (deleteTarget.value.kind === 'type') await deleteDictType(deleteTarget.value.id)
    else await deleteDictData(deleteTarget.value.id)
    clearSelection()
    deleteTarget.value = null
    await refresh()
    showToast('success', '已删除', '最新服务端数据已载入。')
  } catch (value) {
    showToast('error', '删除失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function messageOf(value: unknown): string {
  return isApiClientError(value) || value instanceof Error ? value.message : '请求失败'
}

onMounted(() => void refresh())
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="dictionary-page" :gap="4">
    <V2Card title="字典管理" :heading-level="1">
      <template #actions>
        <form class="v2-page-heading__filters" @submit.prevent="search">
          <V2Input
            v-model="keyword"
            label="搜索分组、类型或字典项"
            hide-label
            placeholder="搜索分组、类型或字典项"
          />
          <V2Select
            v-model="statusFilter"
            label="状态"
            hide-label
            :options="statusOptions"
            allow-empty
            @update:model-value="changeStatus"
          />
          <V2Button type="submit" size="small">搜索</V2Button>
        </form>
        <V2Button v-if="canAdd" size="small" @click="openGroupEditor()">新增分组</V2Button>
        <V2Button size="small" variant="secondary" :loading="loading" @click="refreshPage">
          刷新
        </V2Button>
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取字典树" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="字典加载失败" :description="error">
      <template #actions><V2Button @click="refresh()">重试</V2Button></template>
    </V2PageState>

    <V2Card v-else title="分组 → 类型 → 字典项" :heading-level="2">
      <div class="dictionary-page__columns">
        <section class="dictionary-page__groups-column" aria-labelledby="dictionary-groups-title">
          <div class="dictionary-page__section-heading">
            <h3 id="dictionary-groups-title">1. 分组</h3>
            <V2ActionMenu
              v-if="selectedGroup && (canEdit || canDelete)"
              :label="`${selectedGroup.groupName}更多操作`"
            >
              <V2Button
                v-if="canEdit"
                size="small"
                variant="ghost"
                @click="openGroupEditor(selectedGroup)"
              >
                编辑
              </V2Button>
              <V2Button
                v-if="canDelete"
                size="small"
                variant="danger"
                @click="
                  deleteTarget = {
                    kind: 'group',
                    id: selectedGroup.id,
                    label: selectedGroup.groupName,
                  }
                "
              >
                删除
              </V2Button>
            </V2ActionMenu>
          </div>
          <V2PageState
            v-if="!pagedGroups.length"
            kind="empty"
            title="暂无字典分组"
            description="当前搜索或状态条件没有数据。"
          />
          <div v-else class="dictionary-page__list">
            <button
              v-for="group in pagedGroups"
              :key="group.id"
              type="button"
              class="dictionary-page__list-item"
              :class="{ 'is-selected': selectedGroupId === group.id }"
              :aria-pressed="selectedGroupId === group.id"
              @click="selectGroup(group.id)"
            >
              <strong>{{ group.groupName }}</strong>
              <V2Badge :tone="group.status === 'ENABLE' ? 'success' : 'neutral'">
                {{ group.types.length }} 个类型
              </V2Badge>
            </button>
          </div>
        </section>

        <section aria-labelledby="dictionary-types-title">
          <div class="dictionary-page__section-heading">
            <h3 id="dictionary-types-title">2. 类型</h3>
            <div class="dictionary-page__actions">
              <V2Button v-if="canAdd && selectedGroup" size="small" @click="openTypeEditor()">
                新增类型
              </V2Button>
              <V2ActionMenu
                v-if="selectedType && (canEdit || (canDelete && !protectedType(selectedType)))"
                :label="`${selectedType.dictName}更多操作`"
              >
                <V2Button
                  v-if="canEdit"
                  size="small"
                  variant="ghost"
                  @click="openTypeEditor(selectedType)"
                >
                  编辑
                </V2Button>
                <V2Button
                  v-if="canDelete && !protectedType(selectedType)"
                  size="small"
                  variant="danger"
                  @click="
                    deleteTarget = {
                      kind: 'type',
                      id: selectedType.id,
                      label: selectedType.dictName,
                    }
                  "
                >
                  删除
                </V2Button>
              </V2ActionMenu>
            </div>
          </div>
          <V2PageState
            v-if="!selectedGroup"
            kind="empty"
            title="请选择分组"
            description="选择分组后查看字典类型。"
          />
          <V2PageState
            v-else-if="!pagedTypes.length"
            kind="empty"
            title="暂无字典类型"
            description="当前分组或状态条件没有数据。"
          />
          <div v-else class="dictionary-page__list">
            <button
              v-for="type in pagedTypes"
              :key="type.id"
              type="button"
              class="dictionary-page__list-item"
              :class="{ 'is-selected': selectedTypeId === type.id }"
              :aria-pressed="selectedTypeId === type.id"
              @click="selectType(type.id)"
            >
              <strong>{{ type.dictName }}</strong>
              <V2Badge :tone="type.status === 'ENABLE' ? 'success' : 'neutral'">
                {{ type.data.length }} 个字典项
              </V2Badge>
            </button>
          </div>
        </section>

        <section aria-labelledby="dictionary-data-title">
          <div class="dictionary-page__section-heading">
            <h3 id="dictionary-data-title">3. 字典项</h3>
            <V2Button
              v-if="canAdd && selectedType && !protectedType(selectedType)"
              size="small"
              @click="openDataEditor()"
            >
              新增字典项
            </V2Button>
          </div>
          <V2PageState
            v-if="!selectedType"
            kind="empty"
            title="请选择字典类型"
            description="选择类型后查看字典项。"
          />
          <V2PageState
            v-else-if="!pagedData.length"
            kind="empty"
            title="暂无字典项"
            description="当前类型或状态条件没有数据。"
          />
          <div v-else class="dictionary-page__table-wrap">
            <table data-table-identity="contextual">
              <thead>
                <tr>
                  <th>标签</th>
                  <th>值</th>
                  <th>排序</th>
                  <th>状态</th>
                  <th class="v2-table-cell--actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(item, index) in pagedData"
                  :key="item.id"
                  :class="{ 'is-selected': item.id === selectedDataId }"
                >
                  <th scope="row">
                    <V2Button size="small" variant="ghost" @click="selectData(item.id)">
                      {{ item.dictLabel }}
                    </V2Button>
                  </th>
                  <td>{{ item.dictValue }}</td>
                  <td>{{ item.orderNum }}</td>
                  <td>{{ item.status === 'ENABLE' ? '启用' : '停用' }}</td>
                  <td class="v2-table-cell--actions">
                    <V2ActionMenu
                      v-if="canEdit || (canDelete && !protectedData(item))"
                      :label="`${item.dictLabel || item.dictValue}更多操作`"
                      :placement="index >= pagedData.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <V2Button
                        v-if="canEdit"
                        size="small"
                        variant="ghost"
                        @click="openDataEditor(item)"
                      >
                        编辑
                      </V2Button>
                      <V2Button
                        v-if="canDelete && !protectedData(item)"
                        size="small"
                        variant="danger"
                        @click="deleteTarget = { kind: 'data', id: item.id, label: item.dictLabel }"
                      >
                        删除
                      </V2Button>
                    </V2ActionMenu>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
      <template #footer>
        <V2Pagination
          v-model:page-no="pageNo"
          :total="activeTotal"
          :page-size="pageSize"
          :label="paginationLabel"
        />
      </template>
    </V2Card>

    <V2Dialog
      v-model:open="groupDialog"
      :title="editingGroup ? '编辑字典分组' : '新增字典分组'"
      :close-disabled="saving"
      :close-on-backdrop="false"
    >
      <div class="dictionary-page__form">
        <V2Input
          v-model="groupForm.groupCode"
          label="分组编码"
          required
          :disabled="Boolean(editingGroup)"
        />
        <V2Input v-model="groupForm.groupName" label="分组名称" required />
        <V2Input v-model="groupForm.orderNum" label="排序" />
        <V2Select v-model="groupForm.status" label="状态" :options="statusOptions.slice(1)" />
      </div>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="groupDialog = false"
          >取消</V2Button
        >
        <V2Button :loading="saving" @click="saveGroup">保存</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="typeDialog"
      :title="editingType ? '编辑字典类型' : '新增字典类型'"
      :close-disabled="saving"
      :close-on-backdrop="false"
    >
      <div class="dictionary-page__form">
        <V2Input
          v-model="typeForm.dictCode"
          label="字典编码"
          required
          :disabled="Boolean(editingType)"
        />
        <V2Input v-model="typeForm.dictName" label="字典名称" required />
        <V2Input v-model="typeForm.dictClass" label="字典分类" required disabled />
        <V2Select
          v-model="typeForm.status"
          label="状态"
          :options="statusOptions.slice(1)"
          :disabled="protectedType(editingType)"
        />
      </div>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="typeDialog = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveType">保存</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="dataDialog"
      :title="editingData ? '编辑字典项' : '新增字典项'"
      :close-disabled="saving"
      :close-on-backdrop="false"
    >
      <div class="dictionary-page__form">
        <V2Input v-model="dataForm.dictLabel" label="标签" required />
        <V2Input
          v-model="dataForm.dictValue"
          label="值"
          required
          :disabled="Boolean(editingData && protectedData(editingData))"
        />
        <V2Input v-model="dataForm.orderNum" label="排序" />
        <V2Select
          v-model="dataForm.status"
          label="状态"
          :options="statusOptions.slice(1)"
          :disabled="Boolean(editingData && protectedData(editingData))"
        />
        <V2Input v-model="dataForm.cssClass" label="CSS 类" />
        <V2Input v-model="dataForm.listClass" label="列表样式" />
      </div>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="dataDialog = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveData">保存</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="确认删除"
      :description="deleteTarget ? `删除“${deleteTarget.label}”前，服务端会检查保护项和引用。` : ''"
      confirm-text="删除"
      danger
      :loading="saving"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </V2Stack>
</template>

<style scoped>
.dictionary-page__columns {
  display: grid;
  grid-template-columns: minmax(14rem, 0.75fr) minmax(16rem, 0.85fr) minmax(30rem, 1.4fr);
  gap: var(--v2-space-4);
}

.dictionary-page__columns > section {
  min-width: 0;
}

.dictionary-page__groups-column {
  position: sticky;
  top: 0;
  z-index: 1;
  align-self: start;
  background: var(--v2-color-surface);
}

.dictionary-page__section-heading,
.dictionary-page__selection,
.dictionary-page__actions,
.dictionary-page__list-item {
  display: flex;
  gap: var(--v2-space-2);
  align-items: center;
  justify-content: space-between;
}

.dictionary-page__section-heading {
  min-height: var(--v2-control-height-touch);
  margin-bottom: var(--v2-space-3);
}

.dictionary-page__section-heading h3 {
  margin: 0;
}

.dictionary-page__list {
  display: grid;
  gap: var(--v2-space-2);
}

.dictionary-page__list-item,
.dictionary-page__selection {
  width: 100%;
  padding: var(--v2-space-3);
  color: inherit;
  text-align: left;
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.dictionary-page__list-item {
  font: inherit;
  cursor: pointer;
}

.dictionary-page__list-item:hover {
  border-color: var(--v2-color-primary);
}

.dictionary-page__list-item span,
.dictionary-page__selection > div {
  display: grid;
  gap: var(--v2-space-1);
}

.dictionary-page small {
  color: var(--v2-color-text-secondary);
}

.dictionary-page .is-selected {
  background: var(--v2-color-primary-soft);
  border-color: var(--v2-color-primary);
}

.dictionary-page__table-wrap {
  overflow-x: auto;
}

.dictionary-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

@media (max-width: 1180px) {
  .dictionary-page__columns {
    grid-template-columns: 1fr;
  }

  .dictionary-page__groups-column {
    position: static;
  }
}

@media (max-width: 680px) {
  .dictionary-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
