<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import axios from 'axios'
import {
  getUserList,
  createUser,
  updateUser,
  updateUserStatus,
  deleteUser,
} from '@/api/modules/user'
import type { SysUserVO } from '@/types/user'

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
const formData = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
})

const columns = [
  { title: '用户名', dataIndex: 'username', width: 120 },
  { title: '姓名', dataIndex: 'realName', width: 100 },
  { title: '手机号', dataIndex: 'phone', width: 130 },
  { title: '邮箱', dataIndex: 'email', width: 180, ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 80, key: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  { title: '操作', key: 'action', width: 220, fixed: 'right' },
]

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
    if (editingId.value) {
      await updateUser(editingId.value, payload)
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

function handlePageChange(page: number) {
  pageNo.value = page
  fetchData()
}

onMounted(fetchData)
</script>

<template>
  <div>
    <!-- Filter -->
    <div
      style="display: flex; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; align-items: center"
    >
      <a-input
        v-model:value="filter.username"
        placeholder="用户名"
        allow-clear
        style="width: 150px"
      />
      <a-input
        v-model:value="filter.realName"
        placeholder="姓名"
        allow-clear
        style="width: 150px"
      />
      <a-button type="primary" @click="handleSearch">查询</a-button>
      <a-button @click="handleReset">重置</a-button>
      <a-button type="primary" @click="handleAdd" style="margin-left: auto">新增用户</a-button>
    </div>

    <!-- Table -->
    <a-table
      :columns="columns"
      :data-source="tableData"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="small"
      :scroll="{ x: 1000 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'ENABLE' ? 'success' : 'error'">
            {{ record.status === 'ENABLE' ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
          <a-button
            type="link"
            size="small"
            :danger="record.status === 'ENABLE'"
            @click="handleToggleStatus(record)"
          >
            {{ record.status === 'ENABLE' ? '禁用' : '启用' }}
          </a-button>
          <a-button type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
        </template>
      </template>
    </a-table>

    <!-- Pagination -->
    <div style="display: flex; align-items: center; justify-content: flex-end; margin-top: 12px">
      <span style="color: #4b5563; font-size: 13px; margin-right: 12px">共 {{ total }} 条</span>
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
      </a-form>
    </a-modal>
  </div>
</template>
