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
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import { isApiClientError } from '@/services/request'
import {
  createDictData,
  createDictType,
  deleteDictData,
  deleteDictType,
  loadDictData,
  loadDictTypes,
  updateDictData,
  updateDictType,
  type DictDataRecord,
  type DictTypeRecord,
} from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const types = ref<DictTypeRecord[]>([])
const data = ref<DictDataRecord[]>([])
const selectedTypeId = ref('')
const pageSize = 10
const typePageNo = ref(1)
const typeTotal = ref(0)
const dataPageNo = ref(1)
const dataTotal = ref(0)
const typeDialog = ref(false)
const dataDialog = ref(false)
const editingType = ref<DictTypeRecord | null>(null)
const editingData = ref<DictDataRecord | null>(null)
const deleteTarget = ref<{ kind: 'type' | 'data'; id: string; label: string } | null>(null)
let controller: AbortController | null = null

const typeFilter = reactive({ dictCode: '', dictName: '', status: '' })
const dataFilter = reactive({ dictLabel: '', status: '' })
const typeForm = reactive({ dictCode: '', dictName: '', status: 'ENABLE' })
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
const canAdd = computed(() => session.hasPermission('system:dict:add'))
const canEdit = computed(() => session.hasPermission('system:dict:edit'))
const canDelete = computed(() => session.hasPermission('system:dict:delete'))
const selectedType = computed(() => types.value.find((item) => item.id === selectedTypeId.value))

async function refresh(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    const page = await loadDictTypes(
      {
        pageNo: typePageNo.value,
        pageSize,
        dictCode: typeFilter.dictCode.trim() || undefined,
        dictName: typeFilter.dictName.trim() || undefined,
        status: typeFilter.status || undefined,
      },
      current.signal,
    )
    types.value = page.records
    typeTotal.value = page.total
    if (!types.value.some((item) => item.id === selectedTypeId.value)) {
      selectedTypeId.value = types.value[0]?.id ?? ''
      dataPageNo.value = 1
    }
    await refreshData(current.signal)
  } catch (value) {
    if (!current.signal.aborted) {
      types.value = []
      data.value = []
      typeTotal.value = 0
      dataTotal.value = 0
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

async function refreshData(signal?: AbortSignal): Promise<void> {
  if (!selectedTypeId.value) {
    data.value = []
    dataTotal.value = 0
    return
  }
  const page = await loadDictData(
    {
      pageNo: dataPageNo.value,
      pageSize,
      typeId: selectedTypeId.value,
      dictLabel: dataFilter.dictLabel.trim() || undefined,
      status: dataFilter.status || undefined,
    },
    signal,
  )
  data.value = page.records
  dataTotal.value = page.total
}

function selectType(id: string): void {
  selectedTypeId.value = id
  dataPageNo.value = 1
  void refreshData().catch((value) => showToast('error', '字典数据加载失败', messageOf(value)))
}

function searchTypes(): void {
  typePageNo.value = 1
  void refresh()
}

function searchData(): void {
  dataPageNo.value = 1
  void refreshData().catch((value) => showToast('error', '字典数据加载失败', messageOf(value)))
}

function changeTypePage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= typeTotal.value) return
  typePageNo.value = next
  void refresh()
}

function changeDataPage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= dataTotal.value) return
  dataPageNo.value = next
  void refreshData().catch((value) => showToast('error', '字典数据加载失败', messageOf(value)))
}

function openTypeEditor(item?: DictTypeRecord): void {
  editingType.value = item ?? null
  Object.assign(typeForm, {
    dictCode: item?.dictCode ?? '',
    dictName: item?.dictName ?? '',
    status: item?.status ?? 'ENABLE',
  })
  typeDialog.value = true
}

async function saveType(): Promise<void> {
  if (!typeForm.dictCode.trim() || !typeForm.dictName.trim()) {
    showToast('warning', '信息不完整', '字典编码和名称不能为空。')
    return
  }
  saving.value = true
  try {
    const command = {
      dictCode: typeForm.dictCode.trim(),
      dictName: typeForm.dictName.trim(),
      status: typeForm.status,
    }
    if (editingType.value) await updateDictType(editingType.value.id, command)
    else selectedTypeId.value = await createDictType(command)
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
    if (editingData.value) await updateDictData(editingData.value.id, command)
    else await createDictData(command)
    dataDialog.value = false
    await refreshData()
    showToast('success', '字典数据已保存', '最新服务端数据已载入。')
  } catch (value) {
    showToast('error', '字典数据保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  saving.value = true
  try {
    if (deleteTarget.value.kind === 'type') {
      await deleteDictType(deleteTarget.value.id)
      selectedTypeId.value = ''
      await refresh()
    } else {
      await deleteDictData(deleteTarget.value.id)
      await refreshData()
    }
    deleteTarget.value = null
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
        <form class="v2-page-heading__filters" @submit.prevent="searchTypes">
          <V2Input
            v-model="typeFilter.dictCode"
            label="字典编码"
            hide-label
            placeholder="字典编码"
          />
          <V2Input
            v-model="typeFilter.dictName"
            label="字典名称"
            hide-label
            placeholder="字典名称"
          />
          <V2Select
            v-model="typeFilter.status"
            label="状态"
            hide-label
            placeholder="全部状态"
            :options="statusOptions"
            allow-empty
            @update:model-value="searchTypes"
          />
          <V2Button type="submit" size="small">查询</V2Button>
        </form>
        <V2Button size="small" variant="secondary" @click="refreshPage">刷新</V2Button>
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取字典" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="字典加载失败" :description="error">
      <template #actions><V2Button @click="refresh">重试</V2Button></template>
    </V2PageState>
    <div v-else class="dictionary-page__columns">
      <V2Card title="字典类型">
        <template #actions>
          <V2Button v-if="canAdd" size="small" @click="openTypeEditor()">新增类型</V2Button>
        </template>
        <V2PageState
          v-if="!types.length"
          kind="empty"
          title="暂无字典类型"
          description="当前筛选条件没有数据。"
        />
        <div v-else class="dictionary-page__table-wrap">
          <table data-table-identity="contextual">
            <thead>
              <tr>
                <th>编码</th>
                <th>名称</th>
                <th>状态</th>
                <th class="v2-table-cell--actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(item, index) in types"
                :key="item.id"
                :class="{ 'is-selected': item.id === selectedTypeId }"
              >
                <th scope="row">
                  <V2Button size="small" variant="ghost" @click="selectType(item.id)">
                    {{ item.dictCode }}
                  </V2Button>
                </th>
                <td>{{ item.dictName }}</td>
                <td>
                  <V2Badge :tone="item.status === 'ENABLE' ? 'success' : 'neutral'">
                    {{ item.status === 'ENABLE' ? '启用' : '停用' }}
                  </V2Badge>
                </td>
                <td class="v2-table-cell--actions">
                  <div class="dictionary-page__actions">
                    <V2ActionMenu
                      v-if="canEdit || canDelete"
                      :label="`${item.dictCode || item.dictName}更多操作`"
                      :placement="index >= types.length - 3 ? 'top-end' : 'bottom-end'"
                    >
                      <V2Button
                        v-if="canEdit"
                        size="small"
                        variant="ghost"
                        @click="openTypeEditor(item)"
                      >
                        编辑
                      </V2Button>
                      <V2Button
                        v-if="canDelete"
                        size="small"
                        variant="danger"
                        @click="deleteTarget = { kind: 'type', id: item.id, label: item.dictName }"
                      >
                        删除
                      </V2Button>
                    </V2ActionMenu>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="dictionary-page__pagination v2-pagination" aria-label="字典类型分页">
            <span>共 {{ typeTotal }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="typePageNo === 1"
              @click="changeTypePage(typePageNo - 1)"
            >
              上一页
            </V2Button>
            <span>第 {{ typePageNo }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="typePageNo * pageSize >= typeTotal"
              @click="changeTypePage(typePageNo + 1)"
            >
              下一页
            </V2Button>
          </nav>
        </template>
      </V2Card>

      <V2Card :title="selectedType ? `${selectedType.dictName} · 字典数据` : '字典数据'">
        <template #actions>
          <V2Button v-if="canAdd && selectedTypeId" size="small" @click="openDataEditor()">
            新增数据
          </V2Button>
        </template>
        <div v-if="selectedTypeId" class="dictionary-page__data-filter">
          <V2Input v-model="dataFilter.dictLabel" label="标签筛选" hide-label placeholder="标签" />
          <V2Select
            v-model="dataFilter.status"
            label="状态筛选"
            hide-label
            placeholder="全部状态"
            :options="statusOptions"
            allow-empty
            @update:model-value="searchData"
          />
          <V2Button size="small" variant="secondary" @click="searchData">筛选</V2Button>
        </div>
        <V2PageState
          v-if="!selectedTypeId"
          kind="empty"
          title="请选择字典类型"
          description="选择左侧类型后读取字典数据。"
        />
        <V2PageState
          v-else-if="!data.length"
          kind="empty"
          title="暂无字典数据"
          description="当前类型没有字典项。"
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
              <tr v-for="(item, index) in data" :key="item.id">
                <th scope="row">{{ item.dictLabel }}</th>
                <td>{{ item.dictValue }}</td>
                <td>{{ item.orderNum }}</td>
                <td>{{ item.status === 'ENABLE' ? '启用' : '停用' }}</td>
                <td class="v2-table-cell--actions">
                  <div class="dictionary-page__actions">
                    <V2ActionMenu
                      v-if="canEdit || canDelete"
                      :label="`${item.dictLabel || item.dictValue}更多操作`"
                      :placement="index >= data.length - 3 ? 'top-end' : 'bottom-end'"
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
                        v-if="canDelete"
                        size="small"
                        variant="danger"
                        @click="deleteTarget = { kind: 'data', id: item.id, label: item.dictLabel }"
                      >
                        删除
                      </V2Button>
                    </V2ActionMenu>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <template #footer>
          <nav class="dictionary-page__pagination v2-pagination" aria-label="字典数据分页">
            <span>共 {{ dataTotal }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="dataPageNo === 1"
              @click="changeDataPage(dataPageNo - 1)"
            >
              上一页
            </V2Button>
            <span>第 {{ dataPageNo }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="dataPageNo * pageSize >= dataTotal"
              @click="changeDataPage(dataPageNo + 1)"
            >
              下一页
            </V2Button>
          </nav>
        </template>
      </V2Card>
    </div>

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
        <V2Select v-model="typeForm.status" label="状态" :options="statusOptions.slice(1)" />
      </div>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="typeDialog = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveType">保存</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="dataDialog"
      :title="editingData ? '编辑字典数据' : '新增字典数据'"
      :close-disabled="saving"
      :close-on-backdrop="false"
    >
      <div class="dictionary-page__form">
        <V2Input v-model="dataForm.dictLabel" label="标签" required />
        <V2Input v-model="dataForm.dictValue" label="值" required />
        <V2Input v-model="dataForm.orderNum" label="排序" />
        <V2Select v-model="dataForm.status" label="状态" :options="statusOptions.slice(1)" />
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
      :description="deleteTarget ? `删除“${deleteTarget.label}”前，系统会检查保护项和引用。` : ''"
      confirm-text="删除"
      danger
      :loading="saving"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </V2Stack>
</template>

<style scoped>
.dictionary-page__data-filter,
.dictionary-page__actions,
.dictionary-page__pagination {
  display: flex;
  gap: var(--v2-space-2);
  align-items: end;
}

.dictionary-page__pagination {
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
}

.dictionary-page__columns {
  display: grid;
  grid-template-columns: minmax(24rem, 0.9fr) minmax(30rem, 1.1fr);
  gap: var(--v2-space-4);
}

.dictionary-page__data-filter {
  margin-bottom: var(--v2-space-3);
}

.dictionary-page__table-wrap {
  overflow-x: auto;
}

.dictionary-page tr.is-selected {
  background: var(--v2-color-primary-soft);
}

.dictionary-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

@media (max-width: 1040px) {
  .dictionary-page__columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .dictionary-page__form,
  .dictionary-page__data-filter {
    display: grid;
    grid-template-columns: 1fr;
  }
}
</style>
