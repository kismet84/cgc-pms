<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import axios from 'axios'
import {
  getUserList,
  createUser,
  updateUser,
  updateUserStatus,
  deleteUser,
  assignUserRoles,
} from '@/api/modules/user'
import { getRoles } from '@/api/modules/system'
import type { SysUserVO } from '@/types/user'
import type { SysRoleVO } from '@/types/system'

const loading = ref(false)
const tableData = ref<SysUserVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const filter = reactive({
  username: '',
  realName: '',
})

const modalVisible = ref(false)
const modalTitle = ref('新增用户')
const editingId = ref<string | null>(null)
const saving = ref(false)
const allRoles = ref<SysRoleVO[]>([])
const formData = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  roleIds: [] as (number | string)[],
})

const columns = [
  { title: '用户名', dataIndex: 'username', width: 120 },
  { title: '姓名', dataIndex: 'realName', width: 100 },
  { title: '角色', dataIndex: 'roleNames', width: 120, key: 'roleNames' },
  { title: '手机号', dataIndex: 'phone', width: 130 },
  { title: '邮箱', dataIndex: 'email', width: 180, ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 80, key: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  { title: '操作', key: 'action', width: 180 },
]

const gridColumns = computed(() => [
  { field: 'username', title: '用户名', width: 120 },
  { field: 'realName', title: '姓名', width: 100 },
  { field: 'roleNames', title: '角色', width: 120, slots: { default: 'roleNames' } },
  { field: 'phone', title: '手机号', width: 130 },
  { field: 'email', title: '邮箱', width: 180, ellipsis: true },
  { field: 'status', title: '状态', width: 80, slots: { default: 'status' } },
  { field: 'createdAt', title: '创建时间', width: 160 },
  { title: '操作', width: 180, slots: { default: 'action' } },
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getUserList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      username: filter.username || undefined,
      realName: filter.realName || undefined,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.username = ''
  filter.realName = ''
  pageNo.value = 1
  fetchData()
}

function handleAdd() {
  modalTitle.value = '新增用户'
  editingId.value = null
  Object.assign(formData, {
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    roleIds: [],
  })
  modalVisible.value = true
}

function handleEdit(record: SysUserVO) {
  modalTitle.value = '编辑用户'
  editingId.value = record.id
  Object.assign(formData, {
    username: record.username,
    password: '',
    realName: record.realName,
    phone: record.phone ?? '',
    email: record.email ?? '',
    roleIds: record.roleIds ? [...record.roleIds] : [],
  })
  modalVisible.value = true
}

async function handleModalOk() {
  if (!formData.username.trim()) {
    message.warning('请输入用户名')
    return
  }
  if (!editingId.value && !formData.password) {
    message.warning('请输入密码')
    return
  }
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      username: formData.username.trim(),
      realName: formData.realName.trim(),
      phone: formData.phone || undefined,
      email: formData.email || undefined,
    }
    if (formData.password) payload.password = formData.password
    if (formData.roleIds.length > 0) payload.roleIds = formData.roleIds
    if (editingId.value) {
      await updateUser(editingId.value, payload)
      await assignUserRoles(editingId.value, formData.roleIds as string[])
      message.success('更新成功')
    } else {
      await createUser(payload)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    const msg = axios.isAxiosError(e)
      ? (e.response?.data as { message?: string })?.message || e.message
      : e instanceof Error
        ? e.message
        : ''
    if (msg.includes('已存在') || msg.includes('duplicate')) {
      message.error('用户名已存在')
    } else {
      message.error('操作失败，请稍后重试')
    }
  } finally {
    saving.value = false
  }
}

function handleToggleStatus(record: SysUserVO) {
  const newStatus = record.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  const action = newStatus === 'ENABLE' ? '启用' : '禁用'
  Modal.confirm({
    title: `确认${action}`,
    content: `确定要${action}用户"${record.username}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await updateUserStatus(record.id, newStatus)
        message.success(`${action}成功`)
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error(`${action}失败`)
      }
    },
  })
}

function handleDelete(record: SysUserVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除用户"${record.username}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteUser(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败')
      }
    },
  })
}

async function fetchRoles() {
  try {
    allRoles.value = await getRoles()
  } catch (e: unknown) {
    console.error(e)
  }
}

function handlePageChange(page: number) {
  pageNo.value = page
  fetchData()
}

onMounted(() => {
  fetchData()
  fetchRoles()
})
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <a-breadcrumb style="margin-bottom:5px;font-size:13px">
        <a-breadcrumb-item>系统设置</a-breadcrumb-item>
        <a-breadcrumb-item>用户管理</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.username"
        placeholder="搜索用户名、姓名…"
        allow-clear
        style="flex: 1; max-width: 200px"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-input
        v-model:value="filter.realName"
        placeholder="搜索用户名、姓名…"
        allow-clear
        style="flex: 1; max-width: 200px"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" @click="handleSearch">查询</a-button>
      <a-button @click="handleReset">重置</a-button>
    </div>

    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" @click="handleAdd">
          <template #icon><PlusOutlined /></template>
          新增用户
        </a-button>
        <a-button @click="fetchData">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
    </div>

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
        <template #roleNames="{ row }">
          <template v-if="row.roleNames && row.roleNames.length">
            <a-tag v-for="(r, i) in row.roleNames" :key="i" style="margin-right: 4px">{{
              r
            }}</a-tag>
          </template>
          <span v-else style="color: var(--muted)">-</span>
        </template>
        <template #status="{ row }">
          <a-tag :color="row.status === 'ENABLE' ? 'success' : 'error'">
            {{ row.status === 'ENABLE' ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template #action="{ row }">
          <div class="lg-ops">
            <a class="lg-link" @click="handleEdit(row)">编辑</a>
            <a
              class="lg-link"
              :class="{ 'lg-del': row.status === 'ENABLE' }"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'ENABLE' ? '禁用' : '启用' }}
            </a>
            <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
          </div>
        </template>
      </vxe-grid>
    </div>

    <div class="lg-pagination">
      <span class="lg-total">共 {{ total }} 条</span>
      <a-pagination
        v-model:current="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-size-options="['10', '20', '50']"
        show-size-changer
        show-quick-jumper
        @change="handlePageChange"
      />
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="480"
      @ok="handleModalOk"
      :confirm-loading="saving"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="用户名" required>
          <a-input
            v-model:value="formData.username"
            placeholder="请输入用户名"
            :disabled="!!editingId"
          />
        </a-form-item>
        <a-form-item label="密码" :required="!editingId">
          <a-input-password v-model:value="formData.password" placeholder="留空则不修改密码" />
        </a-form-item>
        <a-form-item label="姓名">
          <a-input v-model:value="formData.realName" placeholder="请输入真实姓名" />
        </a-form-item>
        <a-form-item label="手机号">
          <a-input v-model:value="formData.phone" placeholder="请输入手机号" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="formData.email" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select
            v-model:value="formData.roleIds"
            mode="multiple"
            placeholder="请选择角色"
            option-filter-prop="label"
            :allow-clear="true"
          >
            <a-select-option
              v-for="role in allRoles"
              :key="role.id"
              :value="role.id"
              :label="role.roleName"
            >
              {{ role.roleName }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
