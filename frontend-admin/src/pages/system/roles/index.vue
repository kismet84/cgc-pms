<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
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

const gridColumns = computed(() => [
  { field: 'roleName', title: '角色名称', width: 150 },
  { field: 'roleCode', title: '角色编码', width: 150 },
  { field: 'roleType', title: '角色类型', width: 120 },
  { field: 'status', title: '状态', width: 80, slots: { default: 'status' } },
  { field: 'createdAt', title: '创建时间', width: 160 },
  { title: '操作', width: 100, slots: { default: 'action' } },
])

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
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom:5px;font-size:13px">
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
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <!-- 工具栏 -->
    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" ghost @click="fetchData">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="lg-table-wrap">
      <vxe-grid
        :data="tableData"
        :columns="gridColumns"
        :loading="loading"
        :column-config="{ resizable: true }"
        stripe
        border="inner"
        size="small"
        max-height="480"
      >
        <template #status="{ row }">
          <a-tag :color="row.status === 'ENABLE' ? 'success' : 'error'">
            {{ row.status === 'ENABLE' ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template #action="{ row }">
          <div class="lg-ops">
            <a class="lg-link" @click="handleEditPermission(row)">编辑权限</a>
          </div>
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

    <!-- Permission Modal -->
    <PermissionModal
      v-model:open="permissionModalVisible"
      :role="selectedRole"
      @saved="handlePermissionSaved"
    />
  </div>
</template>
