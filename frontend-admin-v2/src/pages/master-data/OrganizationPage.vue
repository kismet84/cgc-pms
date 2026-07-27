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
  deleteCompany,
  deleteDepartment,
  deletePosition,
  loadCompanies,
  loadDepartmentTree,
  loadPositions,
  saveCompany,
  saveDepartment,
  savePosition,
  type OrgCompanyRecord,
  type OrgDepartmentRecord,
  type OrgPositionRecord,
} from '@/services/master-data'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'

type Kind = 'company' | 'department' | 'position'
type DeleteTarget = { kind: Kind; id: string; label: string }

const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const error = ref('')
const companies = ref<OrgCompanyRecord[]>([])
const departmentTree = ref<OrgDepartmentRecord[]>([])
const positions = ref<OrgPositionRecord[]>([])
const dialogKind = ref<Kind | null>(null)
const editingId = ref<string | null>(null)
const deleteTarget = ref<DeleteTarget | null>(null)
let loadController: AbortController | null = null

const companyForm = reactive({ companyCode: '', companyName: '', status: 'ENABLE', remark: '' })
const departmentForm = reactive({
  companyId: '',
  parentId: '0',
  deptCode: '',
  deptName: '',
  orderNum: '0',
  status: 'ENABLE',
  remark: '',
})
const positionForm = reactive({
  companyId: '',
  departmentId: '',
  positionCode: '',
  positionName: '',
  status: 'ENABLE',
  remark: '',
})

const can = (permission: string) =>
  session.roles.some((role) => role === 'ADMIN' || role === 'SUPER_ADMIN') ||
  session.hasPermission(permission)
const canAdd = computed(() => can('org:add'))
const canEdit = computed(() => can('org:edit'))
const canDelete = computed(() => can('org:delete'))
const flatDepartments = computed(() => flattenDepartments(departmentTree.value))
const companyOptions = computed(() =>
  companies.value.map((item) => ({ value: item.id, label: item.companyName })),
)
const parentOptions = computed(() => [
  { value: '0', label: '根部门' },
  ...flatDepartments.value
    .filter((item) => item.companyId === departmentForm.companyId && item.id !== editingId.value)
    .map((item) => ({ value: item.id, label: item.deptName })),
])
const positionDepartmentOptions = computed(() =>
  flatDepartments.value
    .filter((item) => item.companyId === positionForm.companyId)
    .map((item) => ({ value: item.id, label: item.deptName })),
)
const statusOptions = [
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
]

function flattenDepartments(items: OrgDepartmentRecord[]): OrgDepartmentRecord[] {
  return items.flatMap((item) => [item, ...flattenDepartments(item.children ?? [])])
}

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

function statusLabel(status: string): string {
  return status === 'ENABLE' ? '启用' : '停用'
}

function companyName(id: string): string {
  return companies.value.find((item) => item.id === id)?.companyName ?? id
}

function departmentName(id: string): string {
  return flatDepartments.value.find((item) => item.id === id)?.deptName ?? id
}

async function load(): Promise<void> {
  loadController?.abort()
  const controller = new AbortController()
  loadController = controller
  loading.value = true
  error.value = ''
  try {
    const [companyPage, departments, positionPage] = await Promise.all([
      loadCompanies(controller.signal),
      loadDepartmentTree(controller.signal),
      loadPositions(controller.signal),
    ])
    companies.value = companyPage.records
    departmentTree.value = departments
    positions.value = positionPage.records
  } catch (value) {
    if (controller.signal.aborted) return
    companies.value = []
    departmentTree.value = []
    positions.value = []
    error.value = messageOf(value)
  } finally {
    if (loadController === controller) loading.value = false
  }
}

async function refresh(): Promise<void> {
  await load()
  if (!error.value) showToast('success', '组织架构已刷新', '已载入最新资料。')
}

function closeDialog(): void {
  dialogKind.value = null
  editingId.value = null
}

function openCreate(kind: Kind): void {
  editingId.value = null
  if (kind === 'company') {
    Object.assign(companyForm, { companyCode: '', companyName: '', status: 'ENABLE', remark: '' })
  } else if (kind === 'department') {
    Object.assign(departmentForm, {
      companyId: companies.value[0]?.id ?? '',
      parentId: '0',
      deptCode: '',
      deptName: '',
      orderNum: '0',
      status: 'ENABLE',
      remark: '',
    })
  } else {
    const companyId = companies.value[0]?.id ?? ''
    Object.assign(positionForm, {
      companyId,
      departmentId: flatDepartments.value.find((item) => item.companyId === companyId)?.id ?? '',
      positionCode: '',
      positionName: '',
      status: 'ENABLE',
      remark: '',
    })
  }
  dialogKind.value = kind
}

function openCompany(record: OrgCompanyRecord): void {
  editingId.value = record.id
  Object.assign(companyForm, {
    companyCode: record.companyCode,
    companyName: record.companyName,
    status: record.status,
    remark: record.remark ?? '',
  })
  dialogKind.value = 'company'
}

function openDepartment(record: OrgDepartmentRecord): void {
  editingId.value = record.id
  Object.assign(departmentForm, {
    companyId: record.companyId,
    parentId: record.parentId && record.parentId !== '0' ? record.parentId : '0',
    deptCode: record.deptCode,
    deptName: record.deptName,
    orderNum: String(record.orderNum ?? 0),
    status: record.status,
    remark: record.remark ?? '',
  })
  dialogKind.value = 'department'
}

function openPosition(record: OrgPositionRecord): void {
  editingId.value = record.id
  Object.assign(positionForm, {
    companyId: record.companyId,
    departmentId: record.departmentId,
    positionCode: record.positionCode,
    positionName: record.positionName,
    status: record.status,
    remark: record.remark ?? '',
  })
  dialogKind.value = 'position'
}

async function save(): Promise<void> {
  saving.value = true
  try {
    if (dialogKind.value === 'company') {
      if (!companyForm.companyCode.trim() || !companyForm.companyName.trim()) {
        throw new TypeError('公司编码和名称不能为空')
      }
      await saveCompany(editingId.value, {
        companyCode: companyForm.companyCode.trim(),
        companyName: companyForm.companyName.trim(),
        status: companyForm.status,
        remark: companyForm.remark.trim(),
      })
    } else if (dialogKind.value === 'department') {
      if (
        !departmentForm.companyId ||
        !departmentForm.deptCode.trim() ||
        !departmentForm.deptName.trim()
      ) {
        throw new TypeError('所属公司、部门编码和名称不能为空')
      }
      const orderNum = Number(departmentForm.orderNum)
      if (!Number.isInteger(orderNum) || orderNum < 0) throw new TypeError('排序号必须为非负整数')
      await saveDepartment(editingId.value, {
        companyId: departmentForm.companyId,
        parentId: departmentForm.parentId || '0',
        deptCode: departmentForm.deptCode.trim(),
        deptName: departmentForm.deptName.trim(),
        orderNum,
        status: departmentForm.status,
        remark: departmentForm.remark.trim(),
      })
    } else if (dialogKind.value === 'position') {
      if (
        !positionForm.companyId ||
        !positionForm.departmentId ||
        !positionForm.positionCode.trim() ||
        !positionForm.positionName.trim()
      ) {
        throw new TypeError('所属公司、部门、岗位编码和名称不能为空')
      }
      await savePosition(editingId.value, {
        companyId: positionForm.companyId,
        departmentId: positionForm.departmentId,
        positionCode: positionForm.positionCode.trim(),
        positionName: positionForm.positionName.trim(),
        status: positionForm.status,
        remark: positionForm.remark.trim(),
      })
    } else {
      return
    }
    closeDialog()
    await load()
    showToast('success', '组织资料已保存', '公司、部门和岗位已重新读取。')
  } catch (value) {
    showToast('error', '保存失败', value instanceof TypeError ? value.message : messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    const target = deleteTarget.value
    if (target.kind === 'company') await deleteCompany(target.id)
    else if (target.kind === 'department') await deleteDepartment(target.id)
    else await deletePosition(target.id)
    deleteTarget.value = null
    await load()
    showToast('success', '组织资料已删除', '列表已刷新。')
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
  <V2Stack class="org-page" :gap="4">
    <V2Card title="组织架构" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="refresh">刷新</V2Button>
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取组织架构" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="组织架构加载失败" :description="error">
      <template #actions><V2Button @click="load">重试</V2Button></template>
    </V2PageState>

    <template v-else>
      <V2Card title="公司">
        <template #title-extra>
          <V2Badge tone="neutral">{{ companies.length }} 家</V2Badge>
        </template>
        <template #actions>
          <V2Button v-if="canAdd" size="small" @click="openCreate('company')">新增公司</V2Button>
        </template>
        <div class="org-page__table-wrap">
          <table class="v2-table--top">
            <thead>
              <tr>
                <th>编码</th>
                <th>名称</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in companies" :key="item.id">
                <td>{{ item.companyCode }}</td>
                <td>{{ item.companyName }}</td>
                <td>
                  <V2Badge :tone="item.status === 'ENABLE' ? 'success' : 'neutral'">
                    {{ statusLabel(item.status) }}
                  </V2Badge>
                </td>
                <td>
                  <V2Cluster>
                    <V2Button
                      v-if="canEdit"
                      size="small"
                      variant="secondary"
                      @click="openCompany(item)"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="canDelete"
                      size="small"
                      variant="danger"
                      @click="
                        deleteTarget = { kind: 'company', id: item.id, label: item.companyName }
                      "
                      >删除</V2Button
                    >
                  </V2Cluster>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>

      <V2Card title="部门">
        <template #title-extra>
          <V2Badge tone="neutral">{{ flatDepartments.length }} 个</V2Badge>
        </template>
        <template #actions>
          <V2Button v-if="canAdd" size="small" @click="openCreate('department')">新增部门</V2Button>
        </template>
        <div class="org-page__table-wrap">
          <table class="v2-table--top">
            <thead>
              <tr>
                <th>公司</th>
                <th>编码</th>
                <th>名称</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in flatDepartments" :key="item.id">
                <td>{{ companyName(item.companyId) }}</td>
                <td>{{ item.deptCode }}</td>
                <td>{{ item.deptName }}</td>
                <td>
                  <V2Badge :tone="item.status === 'ENABLE' ? 'success' : 'neutral'">
                    {{ statusLabel(item.status) }}
                  </V2Badge>
                </td>
                <td>
                  <V2Cluster>
                    <V2Button
                      v-if="canEdit"
                      size="small"
                      variant="secondary"
                      @click="openDepartment(item)"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="canDelete"
                      size="small"
                      variant="danger"
                      @click="
                        deleteTarget = { kind: 'department', id: item.id, label: item.deptName }
                      "
                      >删除</V2Button
                    >
                  </V2Cluster>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>

      <V2Card title="岗位">
        <template #title-extra>
          <V2Badge tone="neutral">{{ positions.length }} 个</V2Badge>
        </template>
        <template #actions>
          <V2Button v-if="canAdd" size="small" @click="openCreate('position')">新增岗位</V2Button>
        </template>
        <div class="org-page__table-wrap">
          <table class="v2-table--top">
            <thead>
              <tr>
                <th>公司</th>
                <th>部门</th>
                <th>编码</th>
                <th>名称</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in positions" :key="item.id">
                <td>{{ companyName(item.companyId) }}</td>
                <td>{{ departmentName(item.departmentId) }}</td>
                <td>{{ item.positionCode }}</td>
                <td>{{ item.positionName }}</td>
                <td>
                  <V2Badge :tone="item.status === 'ENABLE' ? 'success' : 'neutral'">
                    {{ statusLabel(item.status) }}
                  </V2Badge>
                </td>
                <td>
                  <V2Cluster>
                    <V2Button
                      v-if="canEdit"
                      size="small"
                      variant="secondary"
                      @click="openPosition(item)"
                      >编辑</V2Button
                    >
                    <V2Button
                      v-if="canDelete"
                      size="small"
                      variant="danger"
                      @click="
                        deleteTarget = { kind: 'position', id: item.id, label: item.positionName }
                      "
                      >删除</V2Button
                    >
                  </V2Cluster>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </V2Card>
    </template>

    <V2Dialog
      :open="Boolean(dialogKind)"
      :title="`${editingId ? '编辑' : '新增'}${dialogKind === 'company' ? '公司' : dialogKind === 'department' ? '部门' : '岗位'}`"
      :close-disabled="saving"
      :close-on-backdrop="false"
      @close="closeDialog"
    >
      <form id="org-form" class="org-page__form" @submit.prevent="save">
        <template v-if="dialogKind === 'company'">
          <V2Input v-model="companyForm.companyCode" label="公司编码" required />
          <V2Input v-model="companyForm.companyName" label="公司名称" required />
          <V2Select v-model="companyForm.status" :options="statusOptions" label="状态" required />
          <V2Input v-model="companyForm.remark" label="备注" />
        </template>
        <template v-else-if="dialogKind === 'department'">
          <V2Select
            v-model="departmentForm.companyId"
            :options="companyOptions"
            label="所属公司"
            required
          />
          <V2Select v-model="departmentForm.parentId" :options="parentOptions" label="上级部门" />
          <V2Input v-model="departmentForm.deptCode" label="部门编码" required />
          <V2Input v-model="departmentForm.deptName" label="部门名称" required />
          <V2Input v-model="departmentForm.orderNum" label="排序号" />
          <V2Select
            v-model="departmentForm.status"
            :options="statusOptions"
            label="状态"
            required
          />
          <V2Input v-model="departmentForm.remark" label="备注" />
        </template>
        <template v-else>
          <V2Select
            v-model="positionForm.companyId"
            :options="companyOptions"
            label="所属公司"
            required
          />
          <V2Select
            v-model="positionForm.departmentId"
            :options="positionDepartmentOptions"
            label="所属部门"
            required
          />
          <V2Input v-model="positionForm.positionCode" label="岗位编码" required />
          <V2Input v-model="positionForm.positionName" label="岗位名称" required />
          <V2Select v-model="positionForm.status" :options="statusOptions" label="状态" required />
          <V2Input v-model="positionForm.remark" label="备注" />
        </template>
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="closeDialog">取消</V2Button>
        <V2Button type="submit" form="org-form" :loading="saving">保存</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除组织资料"
      :description="
        deleteTarget ? `确认删除“${deleteTarget.label}”？存在下级或业务引用时服务端会拒绝。` : ''
      "
      danger
      :loading="deleting"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </V2Stack>
</template>

<style scoped>
.org-page__table-wrap {
  overflow-x: auto;
}

.org-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

@media (max-width: 48rem) {
  .org-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
