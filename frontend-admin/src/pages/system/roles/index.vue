<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { MoreOutlined, PlusOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import axios from 'axios'
import { createRole, getRoles } from '@/api/modules/system'
import { useUserStore } from '@/stores/user'
import type { CreateRolePayload, SysRoleVO } from '@/types/system'
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
const createModalVisible = ref(false)
const creating = ref(false)
const createForm = reactive<CreateRolePayload>(defaultCreateForm())

const canCreateRole = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)

const gridColumns = computed(() => [
  { field: 'roleName', title: '角色名称', width: 150 },
  { field: 'roleCode', title: '角色编码', width: 150 },
  { field: 'roleType', title: '角色类型', width: 120 },
  { field: 'status', title: '状态', width: 88, slots: { default: 'status' } },
  { field: 'createdAt', title: '创建时间', width: 160 },
  { title: '操作', width: 76, slots: { default: 'action' } },
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
                    <a-menu-item @click="handleEditPermission(row)">编辑权限</a-menu-item>
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

      <aside class="lg-analysis-rail">
        <div class="lg-panel">
          <div class="lg-panel-title">角色状态</div>
          <div class="lg-type-list">
            <div v-for="item in roleStatusSummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :class="`is-${item.tone}`"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </div>
          </div>
        </div>
        <div class="lg-panel">
          <div class="lg-panel-title">近期角色</div>
          <div class="lg-rail-list">
            <div v-for="item in recentRoles" :key="item.id" class="lg-rail-item">
              <span class="lg-type-dot"></span>
              <span>{{ item.roleName }}</span>
            </div>
            <div v-if="!recentRoles.length" class="lg-empty-text">暂无角色</div>
          </div>
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

    <!-- Permission Modal -->
    <PermissionModal
      v-model:open="permissionModalVisible"
      :role="selectedRole"
      @saved="handlePermissionSaved"
    />
  </div>
</template>
