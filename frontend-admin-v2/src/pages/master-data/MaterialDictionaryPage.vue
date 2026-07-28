<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2Cluster,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import {
  createMaterial,
  loadMaterial,
  loadMaterialCategories,
  loadMaterials,
  updateMaterial,
  updateMaterialStatus,
  type MaterialCategory,
  type MaterialRecord,
} from '@/services/master-data'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const changingStatus = ref(false)
const error = ref('')
const records = ref<MaterialRecord[]>([])
const categories = ref<MaterialCategory[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const dialogOpen = ref(false)
const editingId = ref<string | null>(null)
const statusTarget = ref<MaterialRecord | null>(null)
let loadController: AbortController | null = null

const filters = reactive({ materialCode: '', materialName: '', categoryId: '', status: '' })
const form = reactive({
  materialCode: '',
  materialName: '',
  categoryId: '',
  specification: '',
  unit: '',
  brand: '',
  defaultTaxRate: '',
  status: 'ENABLE',
  remark: '',
})

const can = (permission: string) =>
  session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
  session.hasPermission(permission)
const canAdd = computed(() => can('material:dict:add'))
const canEdit = computed(() => can('material:dict:edit'))
const categoryOptions = computed(() =>
  categories.value
    .filter((item) => item.status === 'ENABLE')
    .map((item) => ({ value: item.id, label: item.categoryName })),
)
const statusOptions = [
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
]

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

function categoryName(id?: string): string {
  if (!id) return '未分类'
  return categories.value.find((item) => item.id === id)?.categoryName ?? id
}

function clearForm(): void {
  Object.assign(form, {
    materialCode: '',
    materialName: '',
    categoryId: '',
    specification: '',
    unit: '',
    brand: '',
    defaultTaxRate: '',
    status: 'ENABLE',
    remark: '',
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
    const [page, currentCategories] = await Promise.all([
      loadMaterials(
        {
          pageNo: pageNo.value,
          pageSize: pageSize.value,
          materialCode: filters.materialCode,
          materialName: filters.materialName,
          categoryId: filters.categoryId,
          status: filters.status,
        },
        controller.signal,
      ),
      categories.value.length
        ? Promise.resolve(categories.value)
        : loadMaterialCategories(controller.signal),
    ])
    records.value = page.records
    total.value = page.total
    categories.value = currentCategories
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
  Object.assign(filters, { materialCode: '', materialName: '', categoryId: '', status: '' })
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
  dialogOpen.value = true
}

async function openEdit(record: MaterialRecord): Promise<void> {
  try {
    const detail = await loadMaterial(record.id)
    editingId.value = detail.id
    Object.assign(form, {
      materialCode: detail.materialCode,
      materialName: detail.materialName,
      categoryId: detail.categoryId ?? '',
      specification: detail.specification ?? '',
      unit: detail.unit ?? '',
      brand: detail.brand ?? '',
      defaultTaxRate: detail.defaultTaxRate ?? '',
      status: detail.status,
      remark: detail.remark ?? '',
    })
    dialogOpen.value = true
  } catch (value) {
    clearForm()
    showToast('error', '无法打开材料', messageOf(value))
  }
}

function command(): Omit<MaterialRecord, 'id' | 'createdAt'> | null {
  const code = form.materialCode.trim()
  const name = form.materialName.trim()
  if (!code || !name) {
    showToast('warning', '信息不完整', '材料编码和名称不能为空。')
    return null
  }
  const taxRate = form.defaultTaxRate.trim()
  if (taxRate && !/^(?:100(?:\.0{1,2})?|\d{1,2}(?:\.\d{1,2})?)$/.test(taxRate)) {
    showToast('warning', '默认税率无效', '默认税率必须为0到100且最多2位小数。')
    return null
  }
  return {
    materialCode: code,
    materialName: name,
    categoryId: form.categoryId || undefined,
    specification: form.specification.trim(),
    unit: form.unit.trim(),
    brand: form.brand.trim(),
    defaultTaxRate: taxRate || undefined,
    status: form.status,
    remark: form.remark.trim(),
  }
}

async function save(): Promise<void> {
  const payload = command()
  if (!payload) return
  saving.value = true
  try {
    const id = editingId.value
    const savedId = id ?? (await createMaterial(payload))
    if (id) await updateMaterial(id, payload)
    await loadMaterial(savedId)
    closeDialog()
    await load()
    showToast('success', '材料已保存', '最新资料已刷新。')
  } catch (value) {
    showToast('error', '保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmStatus(): Promise<void> {
  if (!statusTarget.value) return
  changingStatus.value = true
  try {
    const target = statusTarget.value
    const status = target.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
    await updateMaterialStatus(target.id, status)
    await loadMaterial(target.id)
    statusTarget.value = null
    await load()
    showToast('success', '材料状态已更新', '后续业务将使用最新启停状态。')
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
  <V2Stack class="material-page" :gap="4">
    <V2Card title="材料字典" :heading-level="1">
      <template #actions>
        <form class="v2-page-heading__filters" @submit.prevent="search">
          <V2Input
            v-model="filters.materialCode"
            label="材料编码"
            hide-label
            placeholder="材料编码"
          />
          <V2Input
            v-model="filters.materialName"
            label="材料名称"
            hide-label
            placeholder="材料名称"
          />
          <V2Select
            v-model="filters.categoryId"
            :options="categoryOptions"
            label="材料分类"
            hide-label
            placeholder="材料分类"
            allow-empty
          />
          <V2Select
            v-model="filters.status"
            :options="statusOptions"
            label="状态"
            hide-label
            placeholder="全部状态"
            allow-empty
          />
          <V2Button type="submit" size="small">查询</V2Button>
          <V2Button variant="secondary" type="button" size="small" @click="reset">重置</V2Button>
        </form>
        <V2Button v-if="canAdd" size="small" @click="openCreate">新增材料</V2Button>
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取材料字典" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="材料字典加载失败" :description="error">
      <template #actions><V2Button @click="load">重试</V2Button></template>
    </V2PageState>
    <V2PageState
      v-else-if="!records.length"
      kind="empty"
      title="暂无材料"
      description="调整筛选条件后重试。"
    />
    <V2Card v-else title="查询结果">
      <div class="material-page__table-wrap">
        <table class="v2-table--top">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>分类</th>
              <th>规格</th>
              <th>单位</th>
              <th>默认税率</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id">
              <td>{{ record.materialCode }}</td>
              <td>{{ record.materialName }}</td>
              <td>{{ categoryName(record.categoryId) }}</td>
              <td>{{ record.specification || '—' }}</td>
              <td>{{ record.unit || '—' }}</td>
              <td>{{ record.defaultTaxRate ?? '—' }}</td>
              <td>
                <V2Badge :tone="record.status === 'ENABLE' ? 'success' : 'neutral'">
                  {{ record.status === 'ENABLE' ? '启用' : '停用' }}
                </V2Badge>
              </td>
              <td>
                <V2Cluster v-if="canEdit">
                  <V2Button size="small" variant="secondary" @click="openEdit(record)"
                    >编辑</V2Button
                  >
                  <V2Button size="small" variant="secondary" @click="statusTarget = record">
                    {{ record.status === 'ENABLE' ? '停用' : '启用' }}
                  </V2Button>
                </V2Cluster>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <nav class="v2-pagination" aria-label="材料字典分页">
          <span>共 {{ total }} 项</span>
          <V2Button variant="secondary" size="small" :disabled="pageNo <= 1" @click="previousPage"
            >上一页</V2Button
          >
          <span>第 {{ pageNo }} 页</span>
          <V2Button
            variant="secondary"
            size="small"
            :disabled="pageNo * pageSize >= total"
            @click="nextPage"
            >下一页</V2Button
          >
        </nav>
      </template>
    </V2Card>

    <V2Dialog
      :open="dialogOpen"
      :title="editingId ? '编辑材料' : '新增材料'"
      description="材料编码创建后不可修改；保存后自动刷新。"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="closeDialog"
    >
      <form id="material-form" class="material-page__form" @submit.prevent="save">
        <V2Input
          v-model="form.materialCode"
          label="材料编码"
          required
          :disabled="Boolean(editingId)"
        />
        <V2Input v-model="form.materialName" label="材料名称" required />
        <V2Select
          v-model="form.categoryId"
          :options="categoryOptions"
          label="材料分类"
          allow-empty
        />
        <V2Input v-model="form.specification" label="规格型号" />
        <V2Input v-model="form.unit" label="计量单位" />
        <V2Input v-model="form.brand" label="品牌" />
        <V2Input v-model="form.defaultTaxRate" label="默认税率（%）" />
        <V2Select v-model="form.status" :options="statusOptions" label="状态" required />
        <V2Input v-model="form.remark" label="备注" />
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="closeDialog">取消</V2Button>
        <V2Button type="submit" form="material-form" :loading="saving">保存</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(statusTarget)"
      title="更新材料状态"
      :description="
        statusTarget
          ? `确认${statusTarget.status === 'ENABLE' ? '停用' : '启用'}“${statusTarget.materialName}”？`
          : ''
      "
      :danger="statusTarget?.status === 'ENABLE'"
      :loading="changingStatus"
      @close="statusTarget = null"
      @confirm="confirmStatus"
    />
  </V2Stack>
</template>

<style scoped>
.material-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

.material-page__table-wrap {
  overflow-x: auto;
}

@media (max-width: 48rem) {
  .material-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
