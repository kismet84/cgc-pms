<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  V2Badge,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Dialog,
  V2Input,
  V2PageState,
  V2Select,
  V2Stack,
  showToast,
} from '@/components'
import { isApiClientError } from '@/services/request'
import {
  assignRoleMenus,
  assignUserRoles,
  createMenu,
  createRole,
  createUser,
  deleteMenu,
  deleteRole,
  deleteUser,
  loadMenu,
  loadMenus,
  loadRole,
  loadRoles,
  loadUser,
  loadUsers,
  updateMenu,
  updateRole,
  updateUser,
  updateUserStatus,
  type MenuRecord,
  type RoleRecord,
  type UserRecord,
} from '@/services/system-management'
import { useSessionStore } from '@/stores/session'

type Mode = 'users' | 'roles' | 'permissions'
type DeleteTarget =
  | { kind: 'user'; id: string; label: string }
  | { kind: 'role'; id: string; label: string }
  | { kind: 'menu'; id: string; label: string }

const route = useRoute()
const session = useSessionStore()
const mode = computed<Mode>(() =>
  route.path.endsWith('/roles')
    ? 'roles'
    : route.path.endsWith('/permissions')
      ? 'permissions'
      : 'users',
)
const title = computed(() =>
  mode.value === 'users' ? '用户管理' : mode.value === 'roles' ? '角色管理' : '权限清单',
)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const users = ref<UserRecord[]>([])
const roles = ref<RoleRecord[]>([])
const menus = ref<MenuRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
const rolePageNo = ref(1)
const menuPageNo = ref(1)
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

const roleDialog = ref(false)
const editingRole = ref<RoleRecord | null>(null)
const roleForm = reactive({
  roleCode: '',
  roleName: '',
  status: 'ENABLE',
  dataScope: 'SELF',
  menuIds: [] as string[],
})

const menuDialog = ref(false)
const editingMenu = ref<MenuRecord | null>(null)
const menuForm = reactive({
  parentId: '0',
  menuName: '',
  menuType: 'BUTTON' as MenuRecord['menuType'],
  path: '',
  component: '',
  perms: '',
  icon: '',
  orderNum: '0',
  status: 'ENABLE',
  visible: '1',
})

const deleteTarget = ref<DeleteTarget | null>(null)
const statusTarget = ref<UserRecord | null>(null)
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
]
const dataScopeOptions = [
  { value: 'ALL', label: '全部数据' },
  { value: 'DEPT', label: '本部门' },
  { value: 'DEPT_AND_CHILD', label: '本部门及下级' },
  { value: 'SELF', label: '仅本人' },
  { value: 'CUSTOM', label: '自定义' },
]
const menuTypeOptions = [
  { value: 'DIR', label: '目录' },
  { value: 'MENU', label: '菜单' },
  { value: 'BUTTON', label: '按钮权限' },
]
const visibleOptions = [
  { value: '1', label: '可见' },
  { value: '0', label: '隐藏' },
]

const canUserAdd = computed(() => session.hasPermission('system:user:add'))
const canUserEdit = computed(() => session.hasPermission('system:user:edit'))
const canUserDelete = computed(() => session.hasPermission('system:user:delete'))
const canUserAssign = computed(() => session.hasPermission('system:user:assign'))
const canRoleAdd = computed(() => session.hasPermission('system:role:add'))
const canRoleEdit = computed(() => session.hasPermission('system:role:edit'))
const canRoleDelete = computed(() => session.hasPermission('system:role:delete'))
const canRoleAssign = computed(() => session.hasPermission('system:role:assign'))
const canMenuAdd = computed(() => session.hasPermission('system:menu:add'))
const canMenuEdit = computed(() => session.hasPermission('system:menu:edit'))
const canMenuDelete = computed(() => session.hasPermission('system:menu:delete'))
const visibleRoles = computed(() =>
  roles.value.slice((rolePageNo.value - 1) * pageSize, rolePageNo.value * pageSize),
)
const visibleMenus = computed(() =>
  menus.value.slice((menuPageNo.value - 1) * pageSize, menuPageNo.value * pageSize),
)

async function refresh(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  error.value = ''
  try {
    if (mode.value === 'users') await refreshUsers(current.signal)
    else if (mode.value === 'roles') await refreshRoles()
    else await refreshMenus()
  } catch (value) {
    if (!current.signal.aborted) {
      error.value = messageOf(value)
      clearRows()
    }
  } finally {
    if (controller === current) loading.value = false
  }
}

async function refreshPage(): Promise<void> {
  await refresh()
  if (!error.value) showToast('success', `${title.value}已刷新`)
}

async function refreshUsers(signal?: AbortSignal): Promise<void> {
  const [page, currentRoles] = await Promise.all([
    loadUsers(
      {
        pageNo: pageNo.value,
        pageSize,
        username: userFilter.username.trim() || undefined,
        realName: userFilter.realName.trim() || undefined,
        status: userFilter.status || undefined,
      },
      signal,
    ),
    loadRoles(),
  ])
  users.value = page.records
  total.value = page.total
  roles.value = currentRoles
}

async function refreshRoles(): Promise<void> {
  ;[roles.value, menus.value] = await Promise.all([loadRoles(), loadMenus()])
}

async function refreshMenus(): Promise<void> {
  menus.value = await loadMenus()
}

function clearRows(): void {
  users.value = []
  roles.value = []
  menus.value = []
  total.value = 0
}

function searchUsers(): void {
  pageNo.value = 1
  void refresh()
}

function changePage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= total.value) return
  pageNo.value = next
  void refresh()
}

function changeRolePage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= roles.value.length) return
  rolePageNo.value = next
}

function changeMenuPage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= menus.value.length) return
  menuPageNo.value = next
}

async function openUserEditor(item?: UserRecord): Promise<void> {
  editingUser.value = item ? await loadUser(item.id) : null
  Object.assign(userForm, {
    username: editingUser.value?.username ?? '',
    password: '',
    realName: editingUser.value?.realName ?? '',
    phone: editingUser.value?.phone ?? '',
    email: editingUser.value?.email ?? '',
    orgId: editingUser.value?.orgId ?? '',
    roleIds: [...(editingUser.value?.roleIds ?? [])],
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

async function openRoleEditor(item?: RoleRecord): Promise<void> {
  editingRole.value = item ? await loadRole(item.id) : null
  Object.assign(roleForm, {
    roleCode: editingRole.value?.roleCode ?? '',
    roleName: editingRole.value?.roleName ?? '',
    status: editingRole.value?.status ?? 'ENABLE',
    dataScope: editingRole.value?.dataScope ?? 'SELF',
    menuIds: [...(editingRole.value?.menuIds ?? [])],
  })
  roleDialog.value = true
}

async function saveRole(): Promise<void> {
  if (!roleForm.roleCode.trim() || !roleForm.roleName.trim()) {
    showToast('warning', '信息不完整', '角色编码和名称不能为空。')
    return
  }
  saving.value = true
  try {
    const command = {
      roleCode: roleForm.roleCode.trim(),
      roleName: roleForm.roleName.trim(),
      status: roleForm.status,
      dataScope: roleForm.dataScope,
    }
    let roleId = editingRole.value?.id
    if (roleId) await updateRole(roleId, command)
    else roleId = await createRole(command)
    if (canRoleAssign.value) await assignRoleMenus(roleId, roleForm.menuIds)
    roleDialog.value = false
    await refreshRoles()
    showToast('success', '角色已保存', '最新角色与菜单授权已载入。')
  } catch (value) {
    showToast('error', '角色保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

async function openMenuEditor(item?: MenuRecord): Promise<void> {
  editingMenu.value = item ? await loadMenu(item.id) : null
  Object.assign(menuForm, {
    parentId: editingMenu.value?.parentId ?? '0',
    menuName: editingMenu.value?.menuName ?? '',
    menuType: editingMenu.value?.menuType ?? 'BUTTON',
    path: editingMenu.value?.path ?? '',
    component: editingMenu.value?.component ?? '',
    perms: editingMenu.value?.perms ?? '',
    icon: editingMenu.value?.icon ?? '',
    orderNum: String(editingMenu.value?.orderNum ?? 0),
    status: editingMenu.value?.status ?? 'ENABLE',
    visible: String(editingMenu.value?.visible ?? 1),
  })
  menuDialog.value = true
}

async function saveMenu(): Promise<void> {
  if (!menuForm.menuName.trim()) {
    showToast('warning', '信息不完整', '权限名称不能为空。')
    return
  }
  saving.value = true
  try {
    const command = {
      parentId: menuForm.parentId.trim() || '0',
      menuName: menuForm.menuName.trim(),
      menuType: menuForm.menuType,
      path: menuForm.path.trim(),
      component: menuForm.component.trim(),
      perms: menuForm.perms.trim(),
      icon: menuForm.icon.trim(),
      orderNum: Number(menuForm.orderNum) || 0,
      status: menuForm.status,
      visible: Number(menuForm.visible),
    }
    if (editingMenu.value) await updateMenu(editingMenu.value.id, command)
    else await createMenu(command)
    menuDialog.value = false
    await refreshMenus()
    showToast('success', '权限项已保存', '最新菜单事实已载入。')
  } catch (value) {
    showToast('error', '权限项保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function toggleValue(values: string[], value: string, checked: boolean): void {
  const index = values.indexOf(value)
  if (checked && index < 0) values.push(value)
  if (!checked && index >= 0) values.splice(index, 1)
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
    if (deleteTarget.value.kind === 'user') {
      await deleteUser(deleteTarget.value.id)
      await refreshUsers()
    } else if (deleteTarget.value.kind === 'role') {
      await deleteRole(deleteTarget.value.id)
      await refreshRoles()
    } else {
      await deleteMenu(deleteTarget.value.id)
      await refreshMenus()
    }
    deleteTarget.value = null
    showToast('success', '已删除', '当前清单已刷新。')
  } catch (value) {
    showToast('error', '删除失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function isProtectedRole(role: RoleRecord): boolean {
  return role.roleType === 'SYSTEM' || ['ADMIN', 'SUPER_ADMIN'].includes(role.roleCode)
}

function parentMenuName(parentId: string): string {
  if (parentId === '0') return '顶级'
  return menus.value.find((item) => item.id === parentId)?.menuName ?? '上级菜单不可用'
}

function roleTypeLabel(value?: string): string {
  return value === 'SYSTEM' ? '系统角色' : '自定义角色'
}

function dataScopeLabel(value: string): string {
  return (
    {
      ALL: '全部数据',
      COMPANY: '本公司',
      DEPT: '本部门',
      DEPT_AND_CHILD: '本部门及下级',
      SELF: '本人数据',
      CUSTOM: '自定义范围',
    }[value] ?? '未配置'
  )
}

function menuTypeLabel(value: MenuRecord['menuType']): string {
  return { DIR: '目录', MENU: '菜单', BUTTON: '按钮' }[value]
}

function messageOf(value: unknown): string {
  return isApiClientError(value) || value instanceof Error ? value.message : '请求失败'
}

watch(
  () => route.path,
  () => {
    pageNo.value = 1
    rolePageNo.value = 1
    menuPageNo.value = 1
    clearRows()
    void refresh()
  },
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <V2Stack class="access-control-page" :gap="4">
    <V2Card :title="title" :heading-level="1">
      <template #actions>
        <form
          v-if="mode === 'users'"
          class="v2-page-heading__filters"
          @submit.prevent="searchUsers"
        >
          <V2Input v-model="userFilter.username" label="用户名" hide-label placeholder="用户名" />
          <V2Input v-model="userFilter.realName" label="姓名" hide-label placeholder="姓名" />
          <V2Select
            v-model="userFilter.status"
            label="状态"
            hide-label
            placeholder="全部状态"
            :options="statusOptions"
            allow-empty
          />
          <V2Button type="submit" size="small">查询</V2Button>
        </form>
        <V2Button size="small" variant="secondary" @click="refreshPage">刷新</V2Button>
        <V2Button v-if="mode === 'users' && canUserAdd" size="small" @click="openUserEditor()">
          新增用户
        </V2Button>
        <V2Button v-if="mode === 'roles' && canRoleAdd" size="small" @click="openRoleEditor()">
          新增角色
        </V2Button>
        <V2Button
          v-if="mode === 'permissions' && canMenuAdd"
          size="small"
          @click="openMenuEditor()"
        >
          新增权限项
        </V2Button>
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" :title="`正在读取${title}`" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" :title="`${title}加载失败`" :description="error">
      <template #actions><V2Button @click="refresh">重试</V2Button></template>
    </V2PageState>

    <V2Card v-else-if="mode === 'users'" title="用户清单">
      <V2PageState
        v-if="!users.length"
        kind="empty"
        title="暂无用户"
        description="当前筛选条件没有用户。"
      />
      <div v-else class="access-control-page__table-wrap">
        <table data-table-identity="contextual">
          <thead>
            <tr>
              <th>用户名</th>
              <th>姓名</th>
              <th>角色</th>
              <th>联系方式</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in users" :key="item.id">
              <th scope="row">{{ item.username }}</th>
              <td>{{ item.realName || '—' }}</td>
              <td>{{ item.roleNames.join('、') || '未分配' }}</td>
              <td>{{ item.phone || item.email || '—' }}</td>
              <td>
                <V2Badge :tone="item.status === 'ENABLE' ? 'success' : 'neutral'">
                  {{ item.status === 'ENABLE' ? '启用' : '停用' }}
                </V2Badge>
              </td>
              <td>
                <div class="access-control-page__actions">
                  <V2Button
                    v-if="canUserEdit"
                    size="small"
                    variant="ghost"
                    @click="openUserEditor(item)"
                  >
                    编辑
                  </V2Button>
                  <V2Button
                    v-if="canUserEdit"
                    size="small"
                    variant="secondary"
                    @click="statusTarget = item"
                  >
                    {{ item.status === 'ENABLE' ? '停用' : '启用' }}
                  </V2Button>
                  <V2Button
                    v-if="canUserDelete"
                    size="small"
                    variant="danger"
                    @click="deleteTarget = { kind: 'user', id: item.id, label: item.username }"
                  >
                    删除
                  </V2Button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <div class="access-control-page__pagination">
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
        </div>
      </template>
    </V2Card>

    <V2Card v-else-if="mode === 'roles'" title="角色清单">
      <V2PageState
        v-if="!roles.length"
        kind="empty"
        title="暂无角色"
        description="当前租户没有角色。"
      />
      <div v-else class="access-control-page__table-wrap">
        <table>
          <thead>
            <tr>
              <th>角色编码</th>
              <th>角色名称</th>
              <th>类型</th>
              <th>数据范围</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in visibleRoles" :key="item.id">
              <th scope="row">{{ item.roleCode }}</th>
              <td>{{ item.roleName }}</td>
              <td>{{ roleTypeLabel(item.roleType) }}</td>
              <td>{{ dataScopeLabel(item.dataScope) }}</td>
              <td>{{ item.status === 'ENABLE' ? '启用' : '停用' }}</td>
              <td>
                <div class="access-control-page__actions">
                  <V2Button
                    v-if="canRoleEdit && !isProtectedRole(item)"
                    size="small"
                    variant="ghost"
                    @click="openRoleEditor(item)"
                  >
                    编辑
                  </V2Button>
                  <V2Button
                    v-if="canRoleDelete && !isProtectedRole(item)"
                    size="small"
                    variant="danger"
                    @click="deleteTarget = { kind: 'role', id: item.id, label: item.roleName }"
                  >
                    删除
                  </V2Button>
                  <V2Badge v-if="isProtectedRole(item)" tone="warning">受保护</V2Badge>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <div class="access-control-page__pagination">
          <span>共 {{ roles.length }} 条</span>
          <V2Button
            size="small"
            variant="secondary"
            :disabled="rolePageNo === 1"
            @click="changeRolePage(rolePageNo - 1)"
          >
            上一页
          </V2Button>
          <span>第 {{ rolePageNo }} 页</span>
          <V2Button
            size="small"
            variant="secondary"
            :disabled="rolePageNo * pageSize >= roles.length"
            @click="changeRolePage(rolePageNo + 1)"
          >
            下一页
          </V2Button>
        </div>
      </template>
    </V2Card>

    <V2Card v-else title="菜单与权限码">
      <V2PageState
        v-if="!menus.length"
        kind="empty"
        title="暂无权限项"
        description="当前租户没有菜单权限数据。"
      />
      <div v-else class="access-control-page__table-wrap">
        <table data-table-identity="contextual">
          <thead>
            <tr>
              <th>名称</th>
              <th>类型</th>
              <th>上级菜单</th>
              <th>路径</th>
              <th>权限码</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in visibleMenus" :key="item.id">
              <th scope="row">{{ item.menuName }}</th>
              <td>{{ menuTypeLabel(item.menuType) }}</td>
              <td>{{ parentMenuName(item.parentId) }}</td>
              <td>{{ item.path || '—' }}</td>
              <td>
                <code>{{ item.perms || '—' }}</code>
              </td>
              <td>{{ item.status === 'ENABLE' ? '启用' : '停用' }}</td>
              <td>
                <div class="access-control-page__actions">
                  <V2Button
                    v-if="canMenuEdit"
                    size="small"
                    variant="ghost"
                    @click="openMenuEditor(item)"
                  >
                    编辑
                  </V2Button>
                  <V2Button
                    v-if="canMenuDelete"
                    size="small"
                    variant="danger"
                    @click="deleteTarget = { kind: 'menu', id: item.id, label: item.menuName }"
                  >
                    删除
                  </V2Button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <div class="access-control-page__pagination">
          <span>共 {{ menus.length }} 条</span>
          <V2Button
            size="small"
            variant="secondary"
            :disabled="menuPageNo === 1"
            @click="changeMenuPage(menuPageNo - 1)"
          >
            上一页
          </V2Button>
          <span>第 {{ menuPageNo }} 页</span>
          <V2Button
            size="small"
            variant="secondary"
            :disabled="menuPageNo * pageSize >= menus.length"
            @click="changeMenuPage(menuPageNo + 1)"
          >
            下一页
          </V2Button>
        </div>
      </template>
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

    <V2Dialog
      v-model:open="roleDialog"
      :title="editingRole ? '编辑角色' : '新增角色'"
      :close-disabled="saving"
      :close-on-backdrop="false"
    >
      <div class="access-control-page__form">
        <V2Input
          v-model="roleForm.roleCode"
          label="角色编码"
          required
          :disabled="Boolean(editingRole)"
        />
        <V2Input v-model="roleForm.roleName" label="角色名称" required />
        <V2Select v-model="roleForm.status" label="状态" :options="statusOptions.slice(1)" />
        <V2Select v-model="roleForm.dataScope" label="数据范围" :options="dataScopeOptions" />
      </div>
      <fieldset v-if="canRoleAssign" class="access-control-page__choices">
        <legend>菜单与权限</legend>
        <label v-for="menu in menus" :key="menu.id">
          <input
            type="checkbox"
            :checked="roleForm.menuIds.includes(menu.id)"
            @change="
              toggleValue(roleForm.menuIds, menu.id, ($event.target as HTMLInputElement).checked)
            "
          />
          <span
            >{{ menu.menuName }}<small>{{ menu.perms || menu.path || '目录' }}</small></span
          >
        </label>
      </fieldset>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="roleDialog = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveRole">保存</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="menuDialog"
      :title="editingMenu ? '编辑权限项' : '新增权限项'"
      :close-disabled="saving"
      :close-on-backdrop="false"
    >
      <div class="access-control-page__form">
        <V2Input v-model="menuForm.menuName" label="名称" required />
        <V2Select v-model="menuForm.menuType" label="类型" :options="menuTypeOptions" />
        <V2Select
          v-model="menuForm.parentId"
          label="上级菜单"
          :options="[
            { value: '0', label: '顶级' },
            ...menus.map((item) => ({ value: item.id, label: item.menuName })),
          ]"
        />
        <V2Input v-model="menuForm.perms" label="权限码" />
        <V2Input v-model="menuForm.path" label="路由路径" />
        <V2Input v-model="menuForm.component" label="组件标识" />
        <V2Input v-model="menuForm.icon" label="图标" />
        <V2Input v-model="menuForm.orderNum" label="排序" />
        <V2Select v-model="menuForm.status" label="状态" :options="statusOptions.slice(1)" />
        <V2Select v-model="menuForm.visible" label="可见性" :options="visibleOptions" />
      </div>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="menuDialog = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveMenu">保存</V2Button>
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

<style scoped>
.access-control-page__table-wrap {
  overflow-x: auto;
}

.access-control-page__actions,
.access-control-page__pagination {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
}

.access-control-page__pagination {
  justify-content: flex-end;
}

.access-control-page__form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
}

.access-control-page__choices {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-height: calc(var(--v2-space-12) * 4);
  margin-top: var(--v2-space-4);
  padding: var(--v2-space-3);
  overflow-y: auto;
  gap: var(--v2-space-2);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.access-control-page__choices label {
  display: flex;
  gap: var(--v2-space-2);
  align-items: flex-start;
}

.access-control-page__choices span,
.access-control-page__choices small {
  display: block;
}

.access-control-page__choices small {
  color: var(--v2-color-text-muted);
}

@media (max-width: 760px) {
  .access-control-page__form,
  .access-control-page__choices {
    grid-template-columns: 1fr;
  }
}
</style>
