<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import axios from 'axios'
import { getRoles } from '@/api/modules/system'
import type { SysRoleVO } from '@/types/system'
import PermissionModal from './PermissionModal.vue'

const loading = ref(false)
const allRoles = ref<SysRoleVO[]>([])
const pageNo = ref(1)
const pageSize = ref(20)

const filter = reactive({
  roleName: '',
  roleCode: '',
})

const permissionModalVisible = ref(false)
const selectedRole = ref<SysRoleVO | null>(null)

const columns = [
  { title: '角色名称', dataIndex: 'roleName', width: 150 },
  { title: '角色编码', dataIndex: 'roleCode', width: 150 },
  { title: '角色类型', dataIndex: 'roleType', width: 120 },
  { title: '状态', dataIndex: 'status', width: 80, key: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  { title: '操作', key: 'action', width: 100 },
]

const filteredRoles = computed(() => {
  return allRoles.value.filter((r) => {
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

async function fetchData() {
  loading.value = true
  try {
    allRoles.value = await getRoles()
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

onMounted(fetchData)
</script>

<template>
  <div class="project-target-redesign app-page">
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb"
        ><a-breadcrumb-item>系统设置</a-breadcrumb-item
        ><a-breadcrumb-item>角色管理</a-breadcrumb-item></a-breadcrumb
      >
    </div>

    <!-- Filter -->
    <div class="pt-filter-surface">
      <div class="pt-filter-row">
        <div class="pt-field">
          <label>角色名称：</label>
          <a-input
            v-model:value="filter.roleName"
            placeholder="角色名称"
            allow-clear
            style="width: 150px"
          />
        </div>
        <div class="pt-field">
          <label>角色编码：</label>
          <a-input
            v-model:value="filter.roleCode"
            placeholder="角色编码"
            allow-clear
            style="width: 150px"
          />
        </div>
        <div class="pt-filter-surface-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="pt-table-panel">
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'ENABLE' ? 'success' : 'error'">
              {{ record.status === 'ENABLE' ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="pt-link">
              <a-button type="link" size="small" @click="handleEditPermission(record)">
                编辑权限
              </a-button>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="pt-pagination">
      <span class="pt-pagination-total">共 {{ total }} 条</span>
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

    <!-- Permission Modal -->
    <PermissionModal
      v-model:open="permissionModalVisible"
      :role="selectedRole"
      @saved="handlePermissionSaved"
    />
  </div>
</template>
