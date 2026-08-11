<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  V2ActionMenu,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Pagination,
  V2Select,
  V2Stack,
  V2StatusToggle,
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
type StatusTarget =
  | { kind: 'company'; record: OrgCompanyRecord }
  | { kind: 'department'; record: OrgDepartmentRecord }
  | { kind: 'position'; record: OrgPositionRecord }

const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const changingStatus = ref(false)
const error = ref('')
const companies = ref<OrgCompanyRecord[]>([])
const companyDirectory = ref<OrgCompanyRecord[]>([])
const companyTotal = ref(0)
const departmentTree = ref<OrgDepartmentRecord[]>([])
const positions = ref<OrgPositionRecord[]>([])
const positionTotal = ref(0)
const selectedCompanyId = ref('')
const selectedDepartmentId = ref('')
const dialogKind = ref<Kind | null>(null)
const editingId = ref<string | null>(null)
const deleteTarget = ref<DeleteTarget | null>(null)
const statusTarget = ref<StatusTarget | null>(null)
const pageSize = 10
const companyPageNo = ref(1)
const departmentPageNo = ref(1)
const positionPageNo = ref(1)
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
const selectedDepartments = computed(() =>
  flatDepartments.value.filter((item) => item.companyId === selectedCompanyId.value),
)
const pagedDepartments = computed(() =>
  selectedDepartments.value.slice(
    (departmentPageNo.value - 1) * pageSize,
    departmentPageNo.value * pageSize,
  ),
)
const selectedCompany = computed(
  () => companies.value.find((item) => item.id === selectedCompanyId.value) ?? null,
)
const selectedDepartment = computed(
  () => selectedDepartments.value.find((item) => item.id === selectedDepartmentId.value) ?? null,
)
const companyOptions = computed(() =>
  companyDirectory.value
    .filter(
      (item) =>
        item.status === 'ENABLE' ||
        (Boolean(editingId.value) &&
          ((dialogKind.value === 'department' && item.id === departmentForm.companyId) ||
            (dialogKind.value === 'position' && item.id === positionForm.companyId))),
    )
    .map((item) => ({ value: item.id, label: item.companyName })),
)
const parentOptions = computed(() => {
  const current = flatDepartments.value.find((item) => item.id === editingId.value)
  const excluded = new Set([
    editingId.value ?? '',
    ...flattenDepartments(current?.children ?? []).map((item) => item.id),
  ])
  return [
    { value: '0', label: '根部门' },
    ...flatDepartments.value
      .filter(
        (item) =>
          item.companyId === departmentForm.companyId &&
          !excluded.has(item.id) &&
          (item.status === 'ENABLE' ||
            (Boolean(editingId.value) && item.id === departmentForm.parentId)),
      )
      .map((item) => ({ value: item.id, label: item.deptName })),
  ]
})
const positionDepartmentOptions = computed(() =>
  flatDepartments.value
    .filter(
      (item) =>
        item.companyId === positionForm.companyId &&
        (item.status === 'ENABLE' ||
          (Boolean(editingId.value) && item.id === positionForm.departmentId)),
    )
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

function companyName(id: string): string {
  return companyDirectory.value.find((item) => item.id === id)?.companyName ?? id
}

function departmentName(id: string): string {
  return flatDepartments.value.find((item) => item.id === id)?.deptName ?? id
}

function normalizeSelection(): void {
  if (!companies.value.some((item) => item.id === selectedCompanyId.value)) {
    selectedCompanyId.value = companies.value[0]?.id ?? ''
  }
  if (!selectedDepartments.value.some((item) => item.id === selectedDepartmentId.value)) {
    selectedDepartmentId.value = selectedDepartments.value[0]?.id ?? ''
  }
}

async function loadSelectedPositions(signal?: AbortSignal): Promise<void> {
  if (!selectedCompanyId.value || !selectedDepartmentId.value) {
    positions.value = []
    positionTotal.value = 0
    return
  }
  const page = await loadPositions(
    {
      pageNo: positionPageNo.value,
      pageSize,
      companyId: selectedCompanyId.value,
      departmentId: selectedDepartmentId.value,
    },
    signal,
  )
  positions.value = page.records
  positionTotal.value = page.total
}

function refreshSelectedPositions(): void {
  void loadSelectedPositions().catch((value) =>
    showToast('error', '岗位加载失败', messageOf(value)),
  )
}

async function loadCompanyDirectory(
  currentPage: { records: OrgCompanyRecord[]; total: number },
  signal: AbortSignal,
): Promise<OrgCompanyRecord[]> {
  const directoryPageSize = 200
  if (companyPageNo.value === 1 && currentPage.total <= pageSize) return currentPage.records
  const records: OrgCompanyRecord[] = []
  for (let pageNo = 1; records.length < currentPage.total; pageNo += 1) {
    const page =
      pageNo === companyPageNo.value && pageSize === directoryPageSize
        ? currentPage
        : await loadCompanies({ pageNo, pageSize: directoryPageSize }, signal)
    if (!page.records.length) break
    records.push(...page.records)
  }
  return records
}

async function load(resetPages = true): Promise<void> {
  if (resetPages) {
    companyPageNo.value = 1
    departmentPageNo.value = 1
    positionPageNo.value = 1
  }
  loadController?.abort()
  const controller = new AbortController()
  loadController = controller
  loading.value = true
  error.value = ''
  try {
    const [companyPage, departments] = await Promise.all([
      loadCompanies({ pageNo: companyPageNo.value, pageSize }, controller.signal),
      loadDepartmentTree(undefined, controller.signal),
    ])
    const directory = await loadCompanyDirectory(companyPage, controller.signal)
    companies.value = companyPage.records
    companyDirectory.value = directory
    companyTotal.value = companyPage.total
    departmentTree.value = departments
    normalizeSelection()
    await loadSelectedPositions(controller.signal)
  } catch (value) {
    if (controller.signal.aborted) return
    companies.value = []
    companyDirectory.value = []
    companyTotal.value = 0
    departmentTree.value = []
    positions.value = []
    positionTotal.value = 0
    error.value = messageOf(value)
  } finally {
    if (loadController === controller) loading.value = false
  }
}

function changeCompanyPage(next: number): void {
  companyPageNo.value = next
  departmentPageNo.value = 1
  positionPageNo.value = 1
  void load(false)
}

function changePositionPage(next: number): void {
  positionPageNo.value = next
  refreshSelectedPositions()
}

function changeDepartmentPage(next: number): void {
  departmentPageNo.value = next
  const nextDepartment = selectedDepartments.value[(next - 1) * pageSize]
  selectedDepartmentId.value = nextDepartment?.id ?? ''
  positionPageNo.value = 1
  refreshSelectedPositions()
}

function selectCompany(id: string): void {
  if (selectedCompanyId.value === id) return
  selectedCompanyId.value = id
  departmentPageNo.value = 1
  positionPageNo.value = 1
  selectedDepartmentId.value = selectedDepartments.value[0]?.id ?? ''
  refreshSelectedPositions()
}

function selectDepartment(id: string): void {
  if (selectedDepartmentId.value === id) return
  selectedDepartmentId.value = id
  positionPageNo.value = 1
  refreshSelectedPositions()
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
    const companyId =
      companyDirectory.value.find(
        (item) => item.id === selectedCompanyId.value && item.status === 'ENABLE',
      )?.id ??
      companyDirectory.value.find((item) => item.status === 'ENABLE')?.id ??
      ''
    Object.assign(departmentForm, {
      companyId,
      parentId: '0',
      deptCode: '',
      deptName: '',
      orderNum: '0',
      status: 'ENABLE',
      remark: '',
    })
  } else {
    const companyId =
      companyDirectory.value.find(
        (item) => item.id === selectedCompanyId.value && item.status === 'ENABLE',
      )?.id ??
      companyDirectory.value.find((item) => item.status === 'ENABLE')?.id ??
      ''
    Object.assign(positionForm, {
      companyId,
      departmentId:
        flatDepartments.value.find(
          (item) =>
            item.id === selectedDepartmentId.value &&
            item.companyId === companyId &&
            item.status === 'ENABLE',
        )?.id ||
        flatDepartments.value.find(
          (item) => item.companyId === companyId && item.status === 'ENABLE',
        )?.id ||
        '',
      positionCode: '',
      positionName: '',
      status: 'ENABLE',
      remark: '',
    })
  }
  dialogKind.value = kind
}

function changeDepartmentCompany(companyId: string): void {
  departmentForm.companyId = companyId
  departmentForm.parentId = '0'
}

function changePositionCompany(companyId: string): void {
  positionForm.companyId = companyId
  positionForm.departmentId =
    flatDepartments.value.find((item) => item.companyId === companyId && item.status === 'ENABLE')
      ?.id ?? ''
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
    await load(false)
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
    await load(false)
    showToast('success', '组织资料已删除', '列表已刷新。')
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
    const target = statusTarget.value
    const status = target.record.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
    if (target.kind === 'company') {
      const { id, ...command } = target.record
      await saveCompany(id, { ...command, status })
    } else if (target.kind === 'department') {
      const record = target.record
      await saveDepartment(record.id, {
        companyId: record.companyId,
        parentId: record.parentId,
        deptCode: record.deptCode,
        deptName: record.deptName,
        orderNum: record.orderNum,
        status,
        remark: record.remark,
      })
    } else {
      const { id, ...command } = target.record
      await savePosition(id, { ...command, status })
    }
    statusTarget.value = null
    await load(false)
    showToast('success', '组织资料状态已更新', '公司、部门和岗位已重新读取。')
  } catch (value) {
    showToast('error', '状态更新失败', messageOf(value))
  } finally {
    changingStatus.value = false
  }
}

function statusTargetLabel(target: StatusTarget | null): string {
  if (!target) return ''
  if (target.kind === 'company') return target.record.companyName
  if (target.kind === 'department') return target.record.deptName
  return target.record.positionName
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

    <V2Card v-else>
      <div class="org-page__columns">
        <section aria-labelledby="org-companies-title">
          <div class="org-page__section-heading">
            <span>
              <h3 id="org-companies-title">1. 公司</h3>
              <small>共 {{ companyTotal }} 家</small>
            </span>
            <V2Button v-if="canAdd" size="small" @click="openCreate('company')">新增公司</V2Button>
          </div>
          <div class="org-page__list">
            <div
              v-for="(item, index) in companies"
              :key="item.id"
              class="org-page__list-item"
              :class="{ 'is-selected': selectedCompanyId === item.id }"
            >
              <button
                type="button"
                class="org-page__select"
                :aria-pressed="selectedCompanyId === item.id"
                @click="selectCompany(item.id)"
              >
                <span>
                  <strong>{{ item.companyName }}</strong>
                  <small>{{ item.companyCode }}</small>
                </span>
              </button>
              <V2StatusToggle
                :enabled="item.status === 'ENABLE'"
                :disabled="!canEdit || changingStatus"
                :aria-label="`${item.status === 'ENABLE' ? '停用' : '启用'}公司 ${item.companyName}`"
                @toggle="statusTarget = { kind: 'company', record: item }"
              />
              <V2ActionMenu
                :label="`${item.companyCode || item.companyName}更多操作`"
                :placement="index >= companies.length - 3 ? 'top-end' : 'bottom-end'"
              >
                <V2Button
                  v-if="canEdit"
                  size="small"
                  variant="secondary"
                  @click="openCompany(item)"
                >
                  编辑
                </V2Button>
                <V2Button
                  v-if="canDelete"
                  size="small"
                  variant="danger"
                  @click="deleteTarget = { kind: 'company', id: item.id, label: item.companyName }"
                >
                  删除
                </V2Button>
              </V2ActionMenu>
            </div>
          </div>
          <V2Pagination
            :total="companyTotal"
            :page-no="companyPageNo"
            :page-size="pageSize"
            label="公司分页"
            unit="家"
            :disabled="loading"
            @update:page-no="changeCompanyPage"
          />
        </section>

        <section aria-labelledby="org-departments-title">
          <div class="org-page__section-heading">
            <span>
              <h3 id="org-departments-title">2. 部门</h3>
              <small>
                {{ selectedCompany?.companyName ?? '未选择公司' }} · 共
                {{ selectedDepartments.length }} 个
              </small>
            </span>
            <V2Button v-if="canAdd" size="small" @click="openCreate('department')">
              新增部门
            </V2Button>
          </div>
          <V2PageState
            v-if="!selectedCompany"
            kind="empty"
            title="请选择公司"
            description="选择公司后显示所属部门。"
          />
          <V2PageState
            v-else-if="!selectedDepartments.length"
            kind="empty"
            title="当前公司暂无部门"
            description="可新增部门。"
          />
          <div class="org-page__list">
            <div
              v-for="(item, index) in pagedDepartments"
              :key="item.id"
              class="org-page__list-item"
              :class="{ 'is-selected': selectedDepartmentId === item.id }"
            >
              <button
                type="button"
                class="org-page__select"
                :aria-pressed="selectedDepartmentId === item.id"
                @click="selectDepartment(item.id)"
              >
                <span>
                  <strong>{{ item.deptName }}</strong>
                  <small>{{ item.deptCode }}</small>
                </span>
              </button>
              <V2StatusToggle
                :enabled="item.status === 'ENABLE'"
                :disabled="!canEdit || changingStatus"
                :aria-label="`${item.status === 'ENABLE' ? '停用' : '启用'}部门 ${item.deptName}`"
                @toggle="statusTarget = { kind: 'department', record: item }"
              />
              <V2ActionMenu
                :label="`${item.deptCode || item.deptName}更多操作`"
                :placement="index >= pagedDepartments.length - 3 ? 'top-end' : 'bottom-end'"
              >
                <V2Button
                  v-if="canEdit"
                  size="small"
                  variant="secondary"
                  @click="openDepartment(item)"
                >
                  编辑
                </V2Button>
                <V2Button
                  v-if="canDelete"
                  size="small"
                  variant="danger"
                  @click="deleteTarget = { kind: 'department', id: item.id, label: item.deptName }"
                >
                  删除
                </V2Button>
              </V2ActionMenu>
            </div>
          </div>
          <V2Pagination
            :page-no="departmentPageNo"
            :total="selectedDepartments.length"
            :page-size="pageSize"
            label="部门分页"
            unit="个"
            @update:page-no="changeDepartmentPage"
          />
        </section>

        <section aria-labelledby="org-positions-title">
          <div class="org-page__section-heading">
            <span>
              <h3 id="org-positions-title">3. 岗位</h3>
              <small>
                {{ selectedDepartment?.deptName ?? '未选择部门' }} · 共 {{ positionTotal }} 个
              </small>
            </span>
            <V2Button v-if="canAdd" size="small" @click="openCreate('position')">新增岗位</V2Button>
          </div>
          <V2PageState
            v-if="!selectedDepartment"
            kind="empty"
            title="请选择部门"
            description="选择部门后显示所属岗位。"
          />
          <V2PageState
            v-else-if="!positions.length"
            kind="empty"
            title="当前部门暂无岗位"
            description="可新增岗位。"
          />
          <div class="org-page__list">
            <div v-for="(item, index) in positions" :key="item.id" class="org-page__list-item">
              <span>
                <strong>{{ item.positionName }}</strong>
                <small>
                  {{ companyName(item.companyId) }} / {{ departmentName(item.departmentId) }} ·
                  {{ item.positionCode }}
                </small>
              </span>
              <V2StatusToggle
                :enabled="item.status === 'ENABLE'"
                :disabled="!canEdit || changingStatus"
                :aria-label="`${item.status === 'ENABLE' ? '停用' : '启用'}岗位 ${item.positionName}`"
                @toggle="statusTarget = { kind: 'position', record: item }"
              />
              <V2ActionMenu
                :label="`${item.positionCode || item.positionName}更多操作`"
                :placement="index >= positions.length - 3 ? 'top-end' : 'bottom-end'"
              >
                <V2Button
                  v-if="canEdit"
                  size="small"
                  variant="secondary"
                  @click="openPosition(item)"
                >
                  编辑
                </V2Button>
                <V2Button
                  v-if="canDelete"
                  size="small"
                  variant="danger"
                  @click="
                    deleteTarget = { kind: 'position', id: item.id, label: item.positionName }
                  "
                >
                  删除
                </V2Button>
              </V2ActionMenu>
            </div>
          </div>
          <V2Pagination
            :total="positionTotal"
            :page-no="positionPageNo"
            :page-size="pageSize"
            label="岗位分页"
            unit="个"
            :disabled="loading"
            @update:page-no="changePositionPage"
          />
        </section>
      </div>
    </V2Card>

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
          <V2Select
            v-model="companyForm.status"
            :options="statusOptions"
            label="状态"
            required
            :disabled="Boolean(editingId)"
          />
          <V2Input v-model="companyForm.remark" label="备注" />
        </template>
        <template v-else-if="dialogKind === 'department'">
          <V2Select
            :model-value="departmentForm.companyId"
            :options="companyOptions"
            label="所属公司"
            required
            @update:model-value="changeDepartmentCompany"
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
            :disabled="Boolean(editingId)"
          />
          <V2Input v-model="departmentForm.remark" label="备注" />
        </template>
        <template v-else>
          <V2Select
            :model-value="positionForm.companyId"
            :options="companyOptions"
            label="所属公司"
            required
            @update:model-value="changePositionCompany"
          />
          <V2Select
            v-model="positionForm.departmentId"
            :options="positionDepartmentOptions"
            label="所属部门"
            required
          />
          <V2Input v-model="positionForm.positionCode" label="岗位编码" required />
          <V2Input v-model="positionForm.positionName" label="岗位名称" required />
          <V2Select
            v-model="positionForm.status"
            :options="statusOptions"
            label="状态"
            required
            :disabled="Boolean(editingId)"
          />
          <V2Input v-model="positionForm.remark" label="备注" />
        </template>
      </form>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="closeDialog">取消</V2Button>
        <V2Button type="submit" form="org-form" :loading="saving">保存</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(statusTarget)"
      title="更新组织资料状态"
      :description="
        statusTarget
          ? `确认${statusTarget.record.status === 'ENABLE' ? '停用' : '启用'}“${statusTargetLabel(statusTarget)}”？`
          : ''
      "
      :danger="statusTarget?.record.status === 'ENABLE'"
      :loading="changingStatus"
      @close="statusTarget = null"
      @confirm="confirmStatus"
    />

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
.org-page__columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

.org-page__columns > section,
.org-page__section-heading > span,
.org-page__list-item > span,
.org-page__select > span {
  min-width: 0;
}

.org-page__section-heading,
.org-page__select {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
}

.org-page__section-heading {
  min-height: 2.5rem;
  margin-bottom: var(--v2-space-3);
}

.org-page__section-heading h3,
.org-page__section-heading small,
.org-page__list-item strong,
.org-page__list-item small {
  display: block;
}

.org-page__section-heading h3 {
  margin: 0;
}

.org-page__section-heading small,
.org-page__list-item small,
.org-page__select small {
  overflow: hidden;
  color: var(--v2-color-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-page__list {
  display: grid;
  gap: var(--v2-space-2);
  margin-bottom: var(--v2-space-3);
}

.org-page__list-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--v2-space-2);
  min-height: var(--v2-control-height-lg);
  padding: var(--v2-space-3);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.org-page__list-item.is-selected {
  border-color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.org-page__select {
  grid-column: 1;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.org-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

@media (max-width: 64rem) {
  .org-page__columns {
    grid-template-columns: 1fr;
  }

  .org-page__form {
    grid-template-columns: 1fr;
  }
}
</style>
