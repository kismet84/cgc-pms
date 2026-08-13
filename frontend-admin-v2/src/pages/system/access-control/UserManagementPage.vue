<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  V2ActionMenu,
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  V2Stack,
  V2StatusToggle,
  showToast,
} from '@/components'
import { isApiClientError } from '@/services/request'
import {
  assignUserRoles,
  createUser,
  deleteUser,
  loadRoles,
  loadUser,
  loadUsers,
  updateUser,
  updateUserStatus,
  type RoleRecord,
  type UserRecord,
} from '@/services/system-management'
import { useSessionStore } from '@/stores/session'
import { filterRoles, statusOptions } from './model'
import './styles.css'

type DeleteTarget = { id: string; label: string }

const session = useSessionStore()
const pageSize = 10
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const users = ref<UserRecord[]>([])
const roles = ref<RoleRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
let controller: AbortController | null = null

const userFilter = reactive({ username: '', realName: '', status: '' })
const userDialog = ref(false)
const editingUser = ref<UserRecord | null>(null)
const userForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  orgId: '',
  roleIds: [] as string[],
})
const deleteTarget = ref<DeleteTarget | null>(null)
const statusTarget = ref<UserRecord | null>(null)
const userRoleSearch = ref('')
const selectedUserRoleId = ref('')
const selectedUserId = ref('')
const selectedUser = ref<UserRecord | null>(null)
const userDetailLoading = ref(false)
const userDetailError = ref('')
let userDetailLoadVersion = 0

const canUserAdd = computed(() => session.hasAdminOrPermission('system:user:add'))
const canUserEdit = computed(() => session.hasAdminOrPermission('system:user:edit'))
const canUserDelete = computed(() => session.hasAdminOrPermission('system:user:delete'))
const canUserAssign = computed(() => session.hasAdminOrPermission('system:user:assign'))
const filteredUserRoles = computed(() => filterRoles(roles.value, userRoleSearch.value))
const selectedUserRole = computed(
  () => roles.value.find((role) => role.id === selectedUserRoleId.value) ?? roles.value[0] ?? null,
)

function messageOf(value: unknown): string {
  return isApiClientError(value) || value instanceof Error ? value.message : '请求失败'
}

function clearUserDetail(): void {
  userDetailLoadVersion += 1
  selectedUserId.value = ''
  selectedUser.value = null
  userDetailLoading.value = false
  userDetailError.value = ''
}

function clearRows(): void {
  users.value = []
  roles.value = []
  total.value = 0
  selectedUserRoleId.value = ''
  clearUserDetail()
}

async function selectUser(userId: string): Promise<void> {
  if (!userId) {
    clearUserDetail()
    return
  }
  const requestVersion = ++userDetailLoadVersion
  selectedUserId.value = userId
  selectedUser.value = null
  userDetailLoading.value = true
  userDetailError.value = ''
  try {
    const detail = await loadUser(userId)
    if (requestVersion !== userDetailLoadVersion || selectedUserId.value !== userId) return
    selectedUser.value = detail
  } catch (value) {
    if (requestVersion !== userDetailLoadVersion || selectedUserId.value !== userId) return
    userDetailError.value = messageOf(value)
  } finally {
    if (requestVersion === userDetailLoadVersion) userDetailLoading.value = false
  }
}

async function refreshUsers(signal?: AbortSignal): Promise<void> {
  const currentRoles = await loadRoles()
  roles.value = currentRoles
  const nextRoleId =
    currentRoles.find((role) => role.id === selectedUserRoleId.value)?.id ??
    currentRoles[0]?.id ??
    ''
  selectedUserRoleId.value = nextRoleId
  if (!nextRoleId) {
    users.value = []
    total.value = 0
    clearUserDetail()
    return
  }
  const page = await loadUsers(
    {
      pageNo: pageNo.value,
      pageSize,
      username: userFilter.username.trim() || undefined,
      realName: userFilter.realName.trim() || undefined,
      status: userFilter.status || undefined,
      roleId: nextRoleId,
    },
    signal,
  )
  users.value = page.records
  total.value = page.total
  const nextUserId =
    page.records.find((user) => user.id === selectedUserId.value)?.id ?? page.records[0]?.id
  if (nextUserId) void selectUser(nextUserId)
  else clearUserDetail()
}

async function loadPage(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    await refreshUsers(current.signal)
  } catch (value) {
    if (!current.signal.aborted) {
      error.value = messageOf(value)
      clearRows()
    }
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshUsersPage(): Promise<void> {
  await loadPage()
  if (!error.value) showToast('success', '用户管理已刷新')
}

function searchUsers(): void {
  pageNo.value = 1
  void loadPage()
}

function selectUserRole(roleId: string): void {
  if (roleId === selectedUserRoleId.value) return
  selectedUserRoleId.value = roleId
  pageNo.value = 1
  void loadPage()
}

function changePage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= total.value) return
  pageNo.value = next
  void loadPage()
}

async function openUserEditor(item?: UserRecord): Promise<void> {
  editingUser.value = item ? await loadUser(item.id) : null
  const visibleRoleIds = new Set(roles.value.map((role) => role.id))
  Object.assign(userForm, {
    username: editingUser.value?.username ?? '',
    password: '',
    realName: editingUser.value?.realName ?? '',
    phone: editingUser.value?.phone ?? '',
    email: editingUser.value?.email ?? '',
    orgId: editingUser.value?.orgId ?? '',
    roleIds: (editingUser.value?.roleIds ?? []).filter((id) => visibleRoleIds.has(id)),
  })
  userDialog.value = true
}

async function saveUser(): Promise<void> {
  if (!userForm.username.trim() || (!editingUser.value && !userForm.password)) {
    showToast('warning', '信息不完整', '用户名和新用户密码不能为空。')
    return
  }
  saving.value = true
  try {
    const command = {
      username: userForm.username.trim(),
      password: userForm.password || undefined,
      realName: userForm.realName.trim(),
      phone: userForm.phone.trim(),
      email: userForm.email.trim(),
      orgId: userForm.orgId.trim() || null,
      roleIds: [...userForm.roleIds],
    }
    if (editingUser.value) {
      await updateUser(editingUser.value.id, command)
      if (canUserAssign.value) await assignUserRoles(editingUser.value.id, userForm.roleIds)
    } else {
      await createUser(command)
    }
    userDialog.value = false
    await refreshUsers()
    showToast('success', '用户已保存', '最新用户与角色事实已载入。')
  } catch (value) {
    showToast('error', '用户保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function toggleValue(values: string[], value: string, checked: boolean): void {
  const index = values.indexOf(value)
  if (checked && index < 0) values.push(value)
  if (!checked && index >= 0) values.splice(index, 1)
}

function requestUserStatusChange(user: UserRecord): void {
  if (!canUserEdit.value || saving.value || user.id === session.userInfo?.userId) return
  statusTarget.value = user
}

async function toggleUserStatus(): Promise<void> {
  if (!statusTarget.value) return
  saving.value = true
  try {
    const next = statusTarget.value.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
    await updateUserStatus(statusTarget.value.id, next)
    statusTarget.value = null
    await refreshUsers()
    showToast('success', '用户状态已更新', '用户清单已刷新。')
  } catch (value) {
    showToast('error', '用户状态更新失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  saving.value = true
  try {
    await deleteUser(deleteTarget.value.id)
    await refreshUsers()
    deleteTarget.value = null
    showToast('success', '已删除', '当前清单已刷新。')
  } catch (value) {
    showToast('error', '删除失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function userRoleNames(user: UserRecord): string {
  const roleIds = new Set(user.roleIds)
  return roles.value
    .filter((role) => roleIds.has(role.id))
    .map((role) => role.roleName)
    .join('、')
}

onMounted(() => void loadPage())
onBeforeUnmount(() => {
  controller?.abort()
  clearUserDetail()
})
</script>

<template>
  <V2Stack class="access-control-page" :gap="4">
    <V2Card title="用户管理" :heading-level="1">
      <template #actions>
        <form class="v2-page-heading__filters" @submit.prevent="searchUsers">
          <V2Input v-model="userFilter.username" label="用户名" hide-label placeholder="用户名" />
          <V2Input v-model="userFilter.realName" label="姓名" hide-label placeholder="姓名" />
          <V2Select
            v-model="userFilter.status"
            label="状态"
            hide-label
            placeholder="全部状态"
            :options="statusOptions"
            allow-empty
            @update:model-value="searchUsers"
          />
          <V2Button type="submit" size="small">查询</V2Button>
        </form>
        <V2Button size="small" variant="secondary" @click="refreshUsersPage">刷新</V2Button>
        <V2Button v-if="canUserAdd" size="small" @click="openUserEditor()">新增用户</V2Button>
      </template>
    </V2Card>
    <V2PageState v-if="loading" kind="loading" title="正在读取用户管理" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="用户管理加载失败" :description="error">
      <template #actions><V2Button @click="loadPage">重试</V2Button></template>
    </V2PageState>

    <V2Card v-else>
      <V2PageState
        v-if="!roles.length"
        kind="empty"
        title="暂无角色"
        description="当前租户没有可查询用户的角色。"
      />
      <div v-else class="user-workspace">
        <section aria-labelledby="user-workspace-roles-title">
          <div class="user-workspace__section-heading">
            <h3 id="user-workspace-roles-title">1. 角色</h3>
            <span>共 {{ filteredUserRoles.length }} 个</span>
          </div>
          <V2Input
            v-model="userRoleSearch"
            label="搜索角色名称或编码"
            hide-label
            placeholder="搜索角色名称或编码"
          />
          <div class="permission-role-list" role="group" aria-label="角色">
            <V2Button
              v-for="role in filteredUserRoles"
              :key="role.id"
              class="permission-role-list__item user-role-list__item"
              size="medium"
              :variant="role.id === selectedUserRole?.id ? 'secondary' : 'ghost'"
              :aria-pressed="role.id === selectedUserRole?.id"
              @click="selectUserRole(role.id)"
            >
              <strong>{{ role.roleName }}</strong>
              <span class="permission-role-list__count">{{ role.userCount ?? 0 }} 人</span>
            </V2Button>
            <p v-if="!filteredUserRoles.length" class="permission-role-list__empty">
              未找到匹配角色
            </p>
          </div>
        </section>

        <section aria-labelledby="user-workspace-users-title">
          <div class="user-workspace__section-heading">
            <h3 id="user-workspace-users-title">2. 用户</h3>
            <span>{{ selectedUserRole?.roleName || '请选择角色' }} · 共 {{ total }} 人</span>
          </div>
          <V2PageState
            v-if="!users.length"
            kind="empty"
            title="当前角色暂无用户"
            description="当前筛选条件没有匹配用户。"
          />
          <ul v-else class="user-workspace__list" role="listbox" aria-label="用户">
            <li
              v-for="(item, index) in users"
              :key="item.id"
              class="user-workspace__user"
              :class="{ 'is-selected': item.id === selectedUserId }"
              role="option"
              tabindex="0"
              :aria-selected="item.id === selectedUserId"
              @click="selectUser(item.id)"
              @keydown.enter.prevent="selectUser(item.id)"
              @keydown.space.prevent="selectUser(item.id)"
            >
              <span class="user-workspace__identity">
                <strong>{{ item.username }}</strong>
                <span>{{ item.realName || '未填写姓名' }}</span>
              </span>
              <span class="user-workspace__user-actions" @click.stop @keydown.stop>
                <V2StatusToggle
                  :enabled="item.status === 'ENABLE'"
                  :disabled="
                    !canUserEdit || saving || item.id === String(session.userInfo?.userId ?? '')
                  "
                  :aria-label="`${item.status === 'ENABLE' ? '停用' : '启用'}用户${item.username}`"
                  @toggle="requestUserStatusChange(item)"
                />
                <V2ActionMenu
                  v-if="canUserEdit || canUserDelete"
                  :label="`${item.username}更多操作`"
                  :placement="index >= users.length - 3 ? 'top-end' : 'bottom-end'"
                >
                  <V2Button
                    v-if="canUserEdit"
                    size="small"
                    variant="ghost"
                    @click="openUserEditor(item)"
                  >
                    编辑
                  </V2Button>
                  <V2Button
                    v-if="canUserDelete"
                    size="small"
                    variant="danger"
                    @click="deleteTarget = { id: item.id, label: item.username }"
                  >
                    删除
                  </V2Button>
                </V2ActionMenu>
              </span>
            </li>
          </ul>
          <nav class="access-control-page__pagination v2-pagination" aria-label="用户分页">
            <span>共 {{ total }} 条</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo === 1"
              @click="changePage(pageNo - 1)"
            >
              上一页
            </V2Button>
            <span>第 {{ pageNo }} 页</span>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="pageNo * pageSize >= total"
              @click="changePage(pageNo + 1)"
            >
              下一页
            </V2Button>
          </nav>
        </section>

        <section aria-labelledby="user-workspace-detail-title">
          <div class="user-workspace__section-heading">
            <h3 id="user-workspace-detail-title">3. 详情</h3>
            <span>{{ selectedUser?.username || '未选择用户' }}</span>
          </div>
          <V2PageState
            v-if="userDetailLoading"
            kind="loading"
            title="正在读取用户详情"
            description="请稍候。"
          />
          <V2PageState
            v-else-if="userDetailError"
            kind="error"
            title="用户详情加载失败"
            :description="userDetailError"
          >
            <template #actions
              ><V2Button size="small" @click="selectUser(selectedUserId)">重试</V2Button></template
            >
          </V2PageState>
          <dl v-else-if="selectedUser" class="user-workspace__details">
            <div>
              <dt>用户名</dt>
              <dd>{{ selectedUser.username }}</dd>
            </div>
            <div>
              <dt>姓名</dt>
              <dd>{{ selectedUser.realName || '—' }}</dd>
            </div>
            <div>
              <dt>手机</dt>
              <dd>{{ selectedUser.phone || '—' }}</dd>
            </div>
            <div>
              <dt>邮箱</dt>
              <dd>{{ selectedUser.email || '—' }}</dd>
            </div>
            <div>
              <dt>组织</dt>
              <dd>{{ selectedUser.orgId || '—' }}</dd>
            </div>
            <div>
              <dt>角色</dt>
              <dd>{{ userRoleNames(selectedUser) || '—' }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>
                <V2Badge :tone="selectedUser.status === 'ENABLE' ? 'success' : 'neutral'">
                  {{ selectedUser.status === 'ENABLE' ? '启用' : '停用' }}
                </V2Badge>
              </dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ selectedUser.createdAt || '—' }}</dd>
            </div>
          </dl>
          <V2PageState
            v-else
            kind="empty"
            title="暂无用户详情"
            description="请选择用户，或调整角色和筛选条件。"
          />
        </section>
      </div>
    </V2Card>

    <V2Dialog
      v-model:open="userDialog"
      :title="editingUser ? '编辑用户' : '新增用户'"
      :close-disabled="saving"
      :close-on-backdrop="false"
    >
      <div class="access-control-page__form">
        <V2Input
          v-model="userForm.username"
          label="用户名"
          required
          :disabled="Boolean(editingUser)"
        />
        <V2Input
          v-model="userForm.password"
          label="初始密码"
          type="password"
          :required="!editingUser"
          :disabled="Boolean(editingUser)"
          :hint="editingUser ? '密码修改使用专用改密流程。' : undefined"
        />
        <V2Input v-model="userForm.realName" label="姓名" />
        <V2Input v-model="userForm.phone" label="手机号" type="tel" />
        <V2Input v-model="userForm.email" label="邮箱" type="email" />
        <V2Input v-model="userForm.orgId" label="组织标识" />
      </div>
      <fieldset v-if="canUserAssign" class="access-control-page__choices">
        <legend>角色</legend>
        <label v-for="role in roles" :key="role.id">
          <input
            type="checkbox"
            :checked="userForm.roleIds.includes(role.id)"
            @change="
              toggleValue(userForm.roleIds, role.id, ($event.target as HTMLInputElement).checked)
            "
          />
          <span>{{ role.roleName }}</span>
        </label>
      </fieldset>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="userDialog = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveUser">保存</V2Button>
      </template>
    </V2Dialog>

    <V2ConfirmDialog
      :open="Boolean(statusTarget)"
      title="确认更新用户状态"
      :description="
        statusTarget
          ? `${statusTarget.status === 'ENABLE' ? '停用' : '启用'}“${statusTarget.username}”？系统会保护当前账号和最后管理员。`
          : ''
      "
      :confirm-text="statusTarget?.status === 'ENABLE' ? '停用' : '启用'"
      :danger="statusTarget?.status === 'ENABLE'"
      :loading="saving"
      @close="statusTarget = null"
      @confirm="toggleUserStatus"
    />

    <V2ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="确认删除"
      :description="
        deleteTarget ? `删除“${deleteTarget.label}”？系统会检查引用和管理员连续性。` : ''
      "
      confirm-text="删除"
      danger
      :loading="saving"
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </V2Stack>
</template>
