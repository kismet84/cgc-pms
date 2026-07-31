<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { MoreOutlined, PlusOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import axios from 'axios'
import { createRole, deleteRole, getRoleDetail, getRoles, updateRole } from '@/api/modules/system'
import { useUserStore } from '@/stores/user'
import type { CreateRolePayload, SysRoleVO, UpdateRolePayload } from '@/types/system'
import PermissionModal from './PermissionModal.vue'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'
import { normalizeArray } from '@/utils/normalizeArray'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

// 字典常量
const STATUS_ENABLE = 'ENABLE'
const COMMON_STATUS_DICT = 'common_status'
const STATUS_LABEL: Record<string, string> = { ENABLE: '启用', DISABLE: '禁用' }
const STATUS_COLOR: Record<string, string> = { ENABLE: 'success', DISABLE: 'error' }

function statusLabel(status: string | undefined): string {
  return getDictLabelSync(COMMON_STATUS_DICT, status ?? '', STATUS_LABEL)
}

function statusColor(status: string | undefined): string {
  return getDictTagColorSync(COMMON_STATUS_DICT, status ?? '', STATUS_COLOR)
}

const loading = ref(false)
const allRoles = ref<SysRoleVO[]>([])
const userStore = useUserStore()
const pageNo = ref(1)
const pageSize = ref(20)

const filter = reactive({
  roleName: '',
  roleCode: '',
})

const permissionModalVisible = ref(false)
const selectedRole = ref<SysRoleVO | null>(null)
const detailModalVisible = ref(false)
const detailLoading = ref(false)
const detailTarget = ref<SysRoleVO | null>(null)
const roleDetail = ref<SysRoleVO | null>(null)
let detailRequestSequence = 0
const createModalVisible = ref(false)
const creating = ref(false)
const createForm = reactive<CreateRolePayload>(defaultCreateForm())
const updateModalVisible = ref(false)
const updating = ref(false)
const updateTarget = ref<SysRoleVO | null>(null)
const updateForm = reactive<UpdateRolePayload>({
  roleCode: '',
  roleName: '',
  status: 'ENABLE',
  dataScope: 'SELF',
})

const canCreateRole = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canViewRoleDetail = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canDeleteRole = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canUpdateRole = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)

const gridColumns = computed(() => [
  { field: 'roleName', title: '角色名称', width: 150 },
  { field: 'roleCode', title: '角色编码', width: 150 },
  { field: 'roleType', title: '角色类型', width: 120 },
  { field: 'status', title: '状态', width: 88, slots: { default: 'status' } },
  { field: 'createdAt', title: '创建时间', width: 160 },
  { title: '操作', width: 96, slots: { default: 'action' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('system_roles_cols', gridColumns)

const filteredRoles = computed(() => {
  return normalizeArray<SysRoleVO>(allRoles.value).filter((r) => {
    if (filter.roleName && !r.roleName.includes(filter.roleName)) return false
    if (filter.roleCode && !r.roleCode.includes(filter.roleCode)) return false
    return true
  })
})

const total = computed(() => filteredRoles.value.length)

const tableData = computed(() => {
  const start = (pageNo.value - 1) * pageSize.value
  return filteredRoles.value.slice(start, start + pageSize.value)
})
const roleStatusSummary = computed(() => [
  {
    label: '启用角色',
    count: filteredRoles.value.filter((r) => r.status === STATUS_ENABLE).length,
    tone: 'success',
  },
  {
    label: '禁用角色',
    count: filteredRoles.value.filter((r) => r.status !== STATUS_ENABLE).length,
    tone: 'danger',
  },
])
const recentRoles = computed(() => tableData.value.slice(0, 4))

async function fetchData() {
  loading.value = true
  try {
    allRoles.value = normalizeArray<SysRoleVO>(await getRoles())
  } catch (e: unknown) {
    console.error(e)
    const msg = axios.isAxiosError(e)
      ? (e.response?.data as { message?: string })?.message || e.message
      : e instanceof Error
        ? e.message
        : ''
    message.error(msg || '加载角色列表失败')
    allRoles.value = []
  } finally {
    loading.value = false
  }
}

function defaultCreateForm(): CreateRolePayload {
  return {
    roleCode: '',
    roleName: '',
    status: 'ENABLE',
    dataScope: 'SELF',
  }
}

function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return (error.response?.data as { message?: string })?.message || error.message
  }
  return error instanceof Error ? error.message : ''
}

function openCreateModal() {
  Object.assign(createForm, defaultCreateForm())
  createModalVisible.value = true
}

function closeCreateModal() {
  if (!creating.value) createModalVisible.value = false
}

async function handleCreateRole() {
  const roleCode = createForm.roleCode.trim()
  const roleName = createForm.roleName.trim()
  if (!roleCode || !roleName) {
    message.error('请填写角色编码和角色名称')
    return
  }
  if (roleCode.length > 50) {
    message.error('角色编码不能超过50个字符')
    return
  }
  if (roleName.length > 100) {
    message.error('角色名称不能超过100个字符')
    return
  }

  creating.value = true
  try {
    await createRole({
      roleCode,
      roleName,
      status: createForm.status,
      dataScope: createForm.dataScope,
    })
    createModalVisible.value = false
    message.success('角色创建成功')
    await fetchData()
  } catch (error: unknown) {
    message.error(errorMessage(error) || '角色创建失败')
  } finally {
    creating.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
}

function handleReset() {
  filter.roleName = ''
  filter.roleCode = ''
  pageNo.value = 1
}

function handleEditPermission(record: SysRoleVO) {
  selectedRole.value = record
  permissionModalVisible.value = true
}

function openUpdateModal(record: SysRoleVO) {
  updateTarget.value = record
  updateForm.roleCode = record.roleCode
  updateForm.roleName = record.roleName
  updateForm.status = record.status === 'DISABLE' ? 'DISABLE' : 'ENABLE'
  updateForm.dataScope =
    record.dataScope &&
    ['ALL', 'DEPT', 'DEPT_AND_CHILD', 'SELF', 'CUSTOM'].includes(record.dataScope)
      ? (record.dataScope as UpdateRolePayload['dataScope'])
      : 'SELF'
  updateModalVisible.value = true
}

function closeUpdateModal() {
  if (!updating.value) {
    updateModalVisible.value = false
    updateTarget.value = null
  }
}

async function handleUpdateRole() {
  if (!updateTarget.value) return
  const roleName = updateForm.roleName.trim()
  if (!roleName) {
    message.error('请填写角色名称')
    return
  }
  if (roleName.length > 100) {
    message.error('角色名称不能超过100个字符')
    return
  }

  updating.value = true
  try {
    await updateRole(updateTarget.value.id, {
      roleCode: updateTarget.value.roleCode,
      roleName,
      status: updateForm.status,
      dataScope: updateForm.dataScope,
    })
    updateModalVisible.value = false
    updateTarget.value = null
    message.success('角色修改成功')
    await fetchData()
  } catch (error: unknown) {
    message.error(errorMessage(error) || '角色修改失败')
  } finally {
    updating.value = false
  }
}

function isProtectedRole(record: SysRoleVO): boolean {
  const roleCode = String(record.roleCode || '')
    .trim()
    .toUpperCase()
  const roleType = String(record.roleType || '')
    .trim()
    .toUpperCase()
  return ['ADMIN', 'SUPER_ADMIN'].includes(roleCode) || ['SYSTEM', '系统角色'].includes(roleType)
}

function handleDeleteRole(record: SysRoleVO) {
  Modal.confirm({
    title: '确认删除角色',
    content: `确定要删除角色“${record.roleName}”吗？删除后不可恢复。`,
    okText: '删除',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteRole(record.id)
        message.success('角色删除成功')
        await fetchData()
      } catch (error: unknown) {
        message.error(errorMessage(error) || '角色删除失败')
        throw error
      }
    },
  })
}

async function openRoleDetail(record: SysRoleVO) {
  const requestSequence = ++detailRequestSequence
  detailTarget.value = record
  roleDetail.value = null
  detailModalVisible.value = true
  detailLoading.value = true
  try {
    const detail = await getRoleDetail(record.id)
    if (requestSequence === detailRequestSequence) roleDetail.value = detail
  } catch (error: unknown) {
    if (requestSequence === detailRequestSequence) {
      roleDetail.value = null
      message.error(errorMessage(error) || '加载角色详情失败')
    }
  } finally {
    if (requestSequence === detailRequestSequence) detailLoading.value = false
  }
}

function closeRoleDetail() {
  detailRequestSequence += 1
  detailModalVisible.value = false
  detailLoading.value = false
  detailTarget.value = null
  roleDetail.value = null
}

function handlePermissionSaved() {
  permissionModalVisible.value = false
  fetchData()
}

function handlePageChange(page: number) {
  pageNo.value = page
}

onMounted(() => {
  fetchDictData(COMMON_STATUS_DICT)
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-page-head-breadcrumb">
          <a-breadcrumb-item>系统设置</a-breadcrumb-item>
          <a-breadcrumb-item>角色管理</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.roleName"
        placeholder="搜索角色名称…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined class="lg-search-prefix-icon" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <main class="lg-list-table-panel">
        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button
              v-if="canCreateRole"
              type="primary"
              data-testid="create-role-button"
              @click="openCreateModal"
            >
              <template #icon><PlusOutlined /></template>
              新建角色
            </a-button>
            <a-button title="刷新角色列表" aria-label="刷新角色列表" @click="fetchData">
              <template #icon><ReloadOutlined /></template>
              刷新
            </a-button>
          </div>
          <div class="lg-toolbar-right">
            <ColumnSettingsButton
              :columns="columnSettings"
              :visible="colVisible"
              @toggle="toggleCol"
            />
          </div>
        </div>

        <!-- 表格 -->
        <div class="lg-table-wrap">
          <vxe-grid
            :data="tableData"
            :columns="visibleGridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            stripe
            border="inner"
            size="small"
          >
            <template #status="{ row }">
              <a-tag :color="statusColor(row.status)">
                {{ statusLabel(row.status) }}
              </a-tag>
            </template>
            <template #action="{ row }">
              <a-dropdown :trigger="['click']">
                <a-button
                  class="lg-row-action-trigger"
                  size="small"
                  type="text"
                  title="角色操作"
                  aria-label="角色操作"
                >
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item
                      v-if="canViewRoleDetail"
                      :data-testid="`view-role-detail-${row.id}`"
                      @click="openRoleDetail(row)"
                    >
                      查看详情
                    </a-menu-item>
                    <a-menu-item
                      v-if="canUpdateRole && !isProtectedRole(row)"
                      :data-testid="`update-role-${row.id}`"
                      @click="openUpdateModal(row)"
                    >
                      编辑角色
                    </a-menu-item>
                    <a-menu-item @click="handleEditPermission(row)">编辑权限</a-menu-item>
                    <a-menu-item
                      v-if="canDeleteRole && !isProtectedRole(row)"
                      danger
                      :data-testid="`delete-role-${row.id}`"
                      @click="handleDeleteRole(row)"
                    >
                      删除
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </template>
          </vxe-grid>
        </div>

        <!-- 分页 -->
        <div class="lg-pagination">
          <span class="lg-total">共 {{ total }} 条</span>
          <a-pagination
            v-model:current="pageNo"
            v-model:page-size="pageSize"
            :total="total"
            :page-size-options="['10', '20', '50']"
            show-size-changer
            show-quick-jumper
            size="small"
            @change="handlePageChange"
          />
        </div>
      </main>

      <aside class="lg-analysis-rail" aria-label="角色辅助分析">
        <div class="lg-analysis-panel lg-fill-card system-analysis-panel">
          <header class="lg-analysis-header">
            <div>
              <div class="lg-analysis-heading">辅助分析</div>
              <div class="lg-analysis-description">角色状态与近期角色</div>
            </div>
          </header>
          <section class="lg-panel">
            <div class="lg-panel-title">角色状态</div>
            <div class="lg-type-list">
              <div v-for="item in roleStatusSummary" :key="item.label" class="lg-type-row">
                <span class="lg-type-dot" :class="`is-${item.tone}`"></span>
                <span class="lg-type-label">{{ item.label }}</span>
                <strong>{{ item.count }}</strong>
              </div>
            </div>
          </section>
          <section class="lg-panel">
            <div class="lg-panel-title">近期角色</div>
            <div class="lg-rail-list">
              <div v-for="item in recentRoles" :key="item.id" class="lg-rail-item">
                <span class="lg-type-dot"></span>
                <span>{{ item.roleName }}</span>
              </div>
              <div v-if="!recentRoles.length" class="lg-empty-text">暂无角色</div>
            </div>
          </section>
        </div>
      </aside>
    </div>

    <a-modal
      :open="createModalVisible"
      title="新建角色"
      :footer="null"
      :closable="!creating"
      :mask-closable="false"
      @cancel="closeCreateModal"
    >
      <a-form layout="vertical">
        <a-form-item label="角色编码" required>
          <a-input
            v-model:value="createForm.roleCode"
            :maxlength="50"
            placeholder="请输入角色编码"
            data-testid="create-role-code"
          />
        </a-form-item>
        <a-form-item label="角色名称" required>
          <a-input
            v-model:value="createForm.roleName"
            :maxlength="100"
            placeholder="请输入角色名称"
            data-testid="create-role-name"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="createForm.status" data-testid="create-role-status">
            <a-select-option value="ENABLE">启用</a-select-option>
            <a-select-option value="DISABLE">禁用</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="数据范围">
          <a-select v-model:value="createForm.dataScope" data-testid="create-role-data-scope">
            <a-select-option value="SELF">仅本人</a-select-option>
            <a-select-option value="DEPT">本部门</a-select-option>
            <a-select-option value="DEPT_AND_CHILD">本部门及以下</a-select-option>
            <a-select-option value="ALL">全部数据</a-select-option>
            <a-select-option value="CUSTOM">自定义</a-select-option>
          </a-select>
        </a-form-item>
        <div class="lg-form-actions">
          <a-button :disabled="creating" @click="closeCreateModal">取消</a-button>
          <a-button
            type="primary"
            :loading="creating"
            data-testid="create-role-submit"
            @click="handleCreateRole"
          >
            创建
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      :open="updateModalVisible"
      :title="`编辑角色${updateTarget ? `：${updateTarget.roleName}` : ''}`"
      :footer="null"
      :closable="!updating"
      :mask-closable="false"
      data-testid="update-role-modal"
      @cancel="closeUpdateModal"
    >
      <a-form layout="vertical">
        <a-form-item label="角色编码">
          <a-input v-model:value="updateForm.roleCode" disabled data-testid="update-role-code" />
        </a-form-item>
        <a-form-item label="角色名称" required>
          <a-input
            v-model:value="updateForm.roleName"
            :maxlength="100"
            data-testid="update-role-name"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="updateForm.status" data-testid="update-role-status">
            <a-select-option value="ENABLE">启用</a-select-option>
            <a-select-option value="DISABLE">禁用</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="数据范围">
          <a-select v-model:value="updateForm.dataScope" data-testid="update-role-data-scope">
            <a-select-option value="SELF">仅本人</a-select-option>
            <a-select-option value="DEPT">本部门</a-select-option>
            <a-select-option value="DEPT_AND_CHILD">本部门及以下</a-select-option>
            <a-select-option value="ALL">全部数据</a-select-option>
            <a-select-option value="CUSTOM">自定义</a-select-option>
          </a-select>
        </a-form-item>
        <div class="lg-form-actions">
          <a-button :disabled="updating" @click="closeUpdateModal">取消</a-button>
          <a-button
            type="primary"
            :loading="updating"
            data-testid="update-role-submit"
            @click="handleUpdateRole"
          >
            保存
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      :open="detailModalVisible"
      :title="`角色详情${detailTarget ? `：${detailTarget.roleName}` : ''}`"
      :footer="null"
      :confirm-loading="detailLoading"
      data-testid="role-detail-modal"
      @cancel="closeRoleDetail"
    >
      <a-spin :spinning="detailLoading">
        <dl v-if="roleDetail" class="role-detail-list" data-testid="role-detail-content">
          <dt>角色名称</dt>
          <dd>{{ roleDetail.roleName }}</dd>
          <dt>角色编码</dt>
          <dd>{{ roleDetail.roleCode }}</dd>
          <dt>角色类型</dt>
          <dd>{{ roleDetail.roleType || '-' }}</dd>
          <dt>状态</dt>
          <dd>{{ statusLabel(roleDetail.status) }}</dd>
          <dt>数据范围</dt>
          <dd>{{ roleDetail.dataScope || '-' }}</dd>
          <dt>菜单 ID</dt>
          <dd>{{ roleDetail.menuIds?.join(', ') || '无' }}</dd>
          <dt>创建时间</dt>
          <dd>{{ roleDetail.createdAt || '-' }}</dd>
        </dl>
        <a-empty
          v-else-if="!detailLoading"
          description="角色详情加载失败，请关闭后重试"
          data-testid="role-detail-empty"
        />
      </a-spin>
    </a-modal>

    <!-- Permission Modal -->
    <PermissionModal
      v-model:open="permissionModalVisible"
      :role="selectedRole"
      @saved="handlePermissionSaved"
    />
  </div>
</template>

<style scoped>
.role-detail-list {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--ant-color-border, #f0f0f0);
  border-radius: 6px;
}

.role-detail-list dt,
.role-detail-list dd {
  min-width: 0;
  margin: 0;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ant-color-border, #f0f0f0);
}

.role-detail-list dt {
  color: var(--ant-color-text-secondary, rgb(0 0 0 / 45%));
  background: var(--ant-color-fill-alter, #fafafa);
}

.role-detail-list dd {
  overflow-wrap: anywhere;
}

.role-detail-list dt:nth-last-of-type(1),
.role-detail-list dd:last-child {
  border-bottom: 0;
}
</style>
