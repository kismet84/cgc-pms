<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, ArrowLeftOutlined } from '@ant-design/icons-vue'
import { useProjectStore } from '@/stores/project'
import { getUserList, type SysUserBrief } from '@/api/modules/system'
import type { MemberVO, MemberFormParams } from '@/types/project'

const route = useRoute()
const router = useRouter()
const store = useProjectStore()

const projectId = route.params.projectId as string

// ── Role definitions ──
const ROLE_OPTIONS = [
  { value: 'PM', label: '项目经理' },
  { value: 'CM', label: '商务经理' },
  { value: 'CSTM', label: '成本经理' },
  { value: 'MAT', label: '材料员' },
  { value: 'SUBC', label: '分包管理员' },
  { value: 'FIN', label: '财务' },
  { value: 'OTH', label: '其他' },
]

const ROLE_MAP: Record<string, string> = Object.fromEntries(
  ROLE_OPTIONS.map((r) => [r.value, r.label]),
)

const ROLE_COLOR: Record<string, string> = {
  PM: 'blue',
  CM: 'green',
  CSTM: 'orange',
  MAT: 'purple',
  SUBC: 'cyan',
  FIN: 'red',
  OTH: 'default',
}

// ── User lookup ──
const userList = ref<SysUserBrief[]>([])
const userMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  userList.value.forEach((u) => {
    map[u.id] = u.realName || u.username
  })
  return map
})

function getUserName(userId: string): string {
  return userMap.value[userId] ?? userId
}

// ── Add member modal ──
const addVisible = ref(false)
const addForm = reactive<MemberFormParams>({
  userId: '',
  roleCode: '',
  positionName: '',
  startDate: '',
  status: 'ACTIVE',
})
const addLoading = ref(false)

// ── Load data ──
async function loadUsers() {
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 200 })
    userList.value = res.records
  } catch {
    userList.value = []
  }
}

onMounted(async () => {
  await Promise.all([
    store.fetchProject(projectId),
    store.fetchMembers(projectId),
    loadUsers(),
  ])
})

// ── Handlers ──
function openAddModal() {
  addForm.userId = ''
  addForm.roleCode = ''
  addForm.positionName = ''
  addForm.startDate = ''
  addForm.status = 'ACTIVE'
  addVisible.value = true
}

async function handleAdd() {
  if (!addForm.userId || !addForm.roleCode) {
    message.warning('请选择用户和角色')
    return
  }
  addLoading.value = true
  try {
    await store.addMember(projectId, { ...addForm })
    message.success('添加成功')
    addVisible.value = false
    await store.fetchMembers(projectId)
  } catch {
    // error handled by interceptor
  } finally {
    addLoading.value = false
  }
}

async function handleRoleChange(member: MemberVO, newRole: string) {
  if (newRole === member.roleCode) return
  try {
    await store.updateMember(projectId, member.id, {
      userId: member.userId,
      roleCode: newRole,
    })
    message.success('角色更新成功')
    member.roleCode = newRole
  } catch {
    // error handled by interceptor
  }
}

function handleDelete(member: MemberVO) {
  Modal.confirm({
    title: '确认移除',
    content: `确定要从项目中移除成员「${getUserName(member.userId)}」吗？`,
    okText: '确认移除',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      await store.removeMember(projectId, member.id)
      message.success('已移除')
      await store.fetchMembers(projectId)
    },
  })
}

function goBack() {
  router.push('/project')
}

// ── Table columns ──
const columns = [
  { title: '姓名', dataIndex: 'userId', width: 140, slots: { customRender: 'userName' } },
  { title: '角色', dataIndex: 'roleCode', width: 160, slots: { customRender: 'role' } },
  { title: '岗位', dataIndex: 'positionName', width: 140 },
  { title: '开始日期', dataIndex: 'startDate', width: 130 },
  { title: '状态', dataIndex: 'status', width: 90, slots: { customRender: 'status' } },
  { title: '操作', dataIndex: 'ops', width: 80, slots: { customRender: 'ops' } },
]
</script>

<template>
  <div class="pm-page">
    <!-- Header -->
    <div class="pm-header">
      <a-button type="text" @click="goBack">
        <ArrowLeftOutlined />
      </a-button>
      <div class="pm-title">
        <span class="pm-title-label">项目成员管理</span>
        <span v-if="store.currentProject" class="pm-title-project">
          {{ store.currentProject.projectName }}
        </span>
      </div>
      <a-button type="primary" @click="openAddModal">
        <PlusOutlined />
        添加成员
      </a-button>
    </div>

    <!-- Table -->
    <div class="pj-card pm-table-wrap">
      <a-table
        :data-source="store.members"
        :columns="columns"
        :loading="store.membersLoading"
        :pagination="false"
        row-key="id"
        size="middle"
        :scroll="{ x: 740 }"
      >
        <template #bodyCell="{ column, record }">
          <!-- 姓名 -->
          <template v-if="column.dataIndex === 'userId'">
            <div class="pm-user-cell">
              <a-avatar :size="28" class="pm-avatar">
                {{ getUserName(record.userId).charAt(0) }}
              </a-avatar>
              <span class="pm-user-name">{{ getUserName(record.userId) }}</span>
            </div>
          </template>

          <!-- 角色 -->
          <template v-else-if="column.dataIndex === 'roleCode'">
            <a-select
              :value="record.roleCode"
              size="small"
              style="width: 130px"
              :options="ROLE_OPTIONS"
              @change="(val: string) => handleRoleChange(record, val)"
            />
          </template>

          <!-- 状态 -->
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
              {{ record.status === 'ACTIVE' ? '在岗' : record.status ?? '-' }}
            </a-tag>
          </template>

          <!-- 操作 -->
          <template v-else-if="column.dataIndex === 'ops'">
            <a-popconfirm
              title="确定移除该成员？"
              ok-text="确认"
              cancel-text="取消"
              @confirm="handleDelete(record)"
            >
              <a class="pm-link-danger">移除</a>
            </a-popconfirm>
          </template>
        </template>
      </a-table>

      <!-- Empty -->
      <a-empty
        v-if="!store.membersLoading && store.members.length === 0"
        description="暂无项目成员，点击上方按钮添加"
        style="padding: 48px 0"
      />
    </div>

    <!-- Total -->
    <div class="pj-pagination">
      <span class="pj-total">共 {{ store.membersTotal }} 人</span>
    </div>

    <!-- Add Member Modal -->
    <a-modal
      v-model:open="addVisible"
      title="添加项目成员"
      :confirm-loading="addLoading"
      @ok="handleAdd"
      @cancel="addVisible = false"
    >
      <a-form layout="vertical" class="pm-form">
        <a-form-item label="选择用户" required>
          <a-select
            v-model:value="addForm.userId"
            placeholder="请搜索并选择用户"
            show-search
            :filter-option="(input: string, option: any) =>
              (option.label ?? '').toLowerCase().includes(input.toLowerCase())"
            :options="userList.map(u => ({ value: u.id, label: `${u.realName || u.username} (${u.username})` }))"
            style="width: 100%"
            :dropdown-match-select-width="false"
          />
        </a-form-item>

        <a-form-item label="角色" required>
          <a-select
            v-model:value="addForm.roleCode"
            placeholder="请选择角色"
            :options="ROLE_OPTIONS"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item label="岗位名称">
          <a-input v-model:value="addForm.positionName" placeholder="如：施工员、安全员" />
        </a-form-item>

        <a-form-item label="开始日期">
          <a-date-picker v-model:value="addForm.startDate" value-format="YYYY-MM-DD" style="width: 100%" placeholder="选择开始日期" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.pm-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}

.pm-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  padding: 0 2px;
}

.pm-title {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.pm-title-label {
  font-size: 17px;
  font-weight: 600;
  color: #111827;
}

.pm-title-project {
  font-size: 13px;
  color: #6b7280;
  background: #f3f4f6;
  padding: 2px 10px;
  border-radius: 4px;
}

/* ── Shared card style with project list ── */
.pj-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

.pm-table-wrap {
  overflow: hidden;
}

.pj-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}

.pj-total {
  font-size: 13px;
  color: #4b5563;
}

/* ── User cell ── */
.pm-user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pm-avatar {
  background: linear-gradient(135deg, #8ac1ff, #006dff);
  flex-shrink: 0;
  font-size: 12px;
}

.pm-user-name {
  font-size: 14px;
  color: #111827;
  font-weight: 500;
}

/* ── Links ── */
.pm-link-danger {
  color: #ef4444;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
}

.pm-link-danger:hover {
  color: #dc2626;
  text-decoration: underline;
}

/* ── Form ── */
.pm-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}

.pm-form :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}
</style>
