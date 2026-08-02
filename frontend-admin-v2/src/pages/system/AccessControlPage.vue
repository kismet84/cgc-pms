<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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
  showToast,
} from '@/components'
import { navigationDomains } from '@/navigation/catalog'
import { isApiClientError } from '@/services/request'
import {
  assignRoleMenus,
  assignUserRoles,
  createRole,
  createUser,
  deleteRole,
  deleteUser,
  loadMenus,
  loadRole,
  loadRoles,
  loadUser,
  loadUsers,
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
  { kind: 'user'; id: string; label: string } | { kind: 'role'; id: string; label: string }
type PermissionNode = {
  id: string
  label: string
  menuType: MenuRecord['menuType']
  path?: string
  perms?: string
  status: string
  menuIds: string[]
  children: PermissionNode[]
}
type PermissionRow = {
  node: PermissionNode
  depth: number
  hasChildren: boolean
}
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
})

const deleteTarget = ref<DeleteTarget | null>(null)
const statusTarget = ref<UserRecord | null>(null)
const userRoleSearch = ref('')
const selectedUserRoleId = ref('')
const roleSearch = ref('')
const selectedRoleId = ref('')
const selectedMenuIds = ref<Set<string>>(new Set())
const savedMenuIds = ref<Set<string>>(new Set())
const expandedMenuIds = ref<Set<string>>(new Set())
const roleLoading = ref(false)
let roleLoadVersion = 0
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
]
const canUserAdd = computed(() => session.hasAdminOrPermission('system:user:add'))
const canUserEdit = computed(() => session.hasAdminOrPermission('system:user:edit'))
const canUserDelete = computed(() => session.hasAdminOrPermission('system:user:delete'))
const canUserAssign = computed(() => session.hasAdminOrPermission('system:user:assign'))
const canRoleAdd = computed(() => session.hasAdminOrPermission('system:role:add'))
const canRoleEdit = computed(() => session.hasAdminOrPermission('system:role:edit'))
const canRoleDelete = computed(() => session.hasAdminOrPermission('system:role:delete'))
const canRoleAssign = computed(() => session.hasAdminOrPermission('system:role:assign'))
const visibleRoles = computed(() =>
  roles.value.slice((rolePageNo.value - 1) * pageSize, rolePageNo.value * pageSize),
)
const menuTypeOrder: Record<MenuRecord['menuType'], number> = { DIR: 0, MENU: 1, BUTTON: 2 }

function compareMenus(left: MenuRecord, right: MenuRecord): number {
  return (
    menuTypeOrder[left.menuType] - menuTypeOrder[right.menuType] ||
    left.orderNum - right.orderNum ||
    left.menuName.localeCompare(right.menuName)
  )
}

const filteredRoles = computed(() => {
  const query = roleSearch.value.trim().toLocaleLowerCase()
  if (!query) return roles.value
  return roles.value.filter(
    (role) =>
      role.roleName.toLocaleLowerCase().includes(query) ||
      role.roleCode.toLocaleLowerCase().includes(query),
  )
})
const selectedRole = computed(
  () => roles.value.find((role) => role.id === selectedRoleId.value) ?? null,
)
const filteredUserRoles = computed(() => {
  const query = userRoleSearch.value.trim().toLocaleLowerCase()
  return query
    ? roles.value.filter(
        (role) =>
          role.roleName.toLocaleLowerCase().includes(query) ||
          role.roleCode.toLocaleLowerCase().includes(query),
      )
    : roles.value
})
const selectedUserRole = computed(
  () => roles.value.find((role) => role.id === selectedUserRoleId.value) ?? roles.value[0] ?? null,
)
const actualChildrenByParent = computed(() => {
  const result = new Map<string, MenuRecord[]>()
  for (const menu of menus.value) {
    const children = result.get(menu.parentId) ?? []
    children.push(menu)
    result.set(menu.parentId, children)
  }
  for (const children of result.values()) {
    children.sort(compareMenus)
  }
  return result
})
const permissionTree = computed<PermissionNode[]>(() => {
  const claimed = new Set<string>()
  const roots: PermissionNode[] = []
  const tabNodes = new Map<string, PermissionNode>()
  const containerNodes = new Map<string, PermissionNode>()
  const normalizePath = (value?: string): string =>
    (value ?? '').replace(/^\/v2(?=\/|$)/, '').replace(/\/+$/, '')
  const navigationTabs = navigationDomains.flatMap((domain) =>
    domain.workspaces.flatMap((workspace) => workspace.tabs),
  )
  const navigationRecordIds = new Set(
    menus.value
      .filter(
        (menu) =>
          menu.menuType !== 'BUTTON' &&
          navigationTabs.some(
            (tab) =>
              (Boolean(tab.permission) && menu.perms === tab.permission) ||
              normalizePath(menu.path) === normalizePath(tab.path),
          ),
      )
      .map((menu) => menu.id),
  )
  const take = (predicate: (menu: MenuRecord) => boolean): MenuRecord | undefined =>
    menus.value.find((menu) => !claimed.has(menu.id) && predicate(menu))
  const actualNode = (menu: MenuRecord, label = menu.menuName): PermissionNode => {
    claimed.add(menu.id)
    return {
      id: menu.id,
      label,
      menuType: menu.menuType,
      path: menu.path,
      perms: menu.perms,
      status: menu.status,
      menuIds: [menu.id],
      children: (actualChildrenByParent.value.get(menu.id) ?? [])
        .filter((child) => !claimed.has(child.id) && !navigationRecordIds.has(child.id))
        .map((child) => actualNode(child)),
    }
  }

  navigationDomains.forEach((domain, domainIndex) => {
    const domainRecord = take((menu) => menu.menuType === 'DIR' && menu.menuName === domain.label)
    if (domainRecord) claimed.add(domainRecord.id)
    const domainNode: PermissionNode = {
      id: `navigation:domain:${domain.id}`,
      label: domain.label,
      menuType: 'DIR',
      status: domainRecord?.status ?? 'ENABLE',
      menuIds: domainRecord ? [domainRecord.id] : [],
      children: [],
    }
    containerNodes.set(`domain:${domain.id}`, domainNode)

    domain.workspaces.forEach((workspace, workspaceIndex) => {
      const workspaceRecord = take(
        (menu) => menu.menuType === 'DIR' && menu.menuName === workspace.label,
      )
      if (workspaceRecord) claimed.add(workspaceRecord.id)
      const workspaceNode: PermissionNode = {
        id: `navigation:workspace:${domain.id}:${workspace.id}`,
        label: workspace.label,
        menuType: 'DIR',
        status: workspaceRecord?.status ?? 'ENABLE',
        menuIds: workspaceRecord ? [workspaceRecord.id] : [],
        children: [],
      }
      containerNodes.set(`workspace:${domain.id}:${workspace.id}`, workspaceNode)

      workspace.tabs.forEach((tab) => {
        const tabRecord = take(
          (menu) =>
            menu.menuType !== 'BUTTON' &&
            ((Boolean(tab.permission) && menu.perms === tab.permission) ||
              normalizePath(menu.path) === normalizePath(tab.path)),
        )
        const tabNode = tabRecord
          ? actualNode(tabRecord, tab.label)
          : {
              id: `navigation:tab:${domain.id}:${workspace.id}:${normalizePath(tab.path)}`,
              label: tab.label,
              menuType: 'MENU' as const,
              path: tab.path,
              perms: tab.permission,
              status: 'MISSING',
              menuIds: [],
              children: [],
            }
        tabNodes.set(normalizePath(tab.path), tabNode)
        workspaceNode.children.push(tabNode)
      })
      if (
        workspaceNode.menuIds.length ||
        workspaceNode.children.some((node) => node.menuIds.length || node.children.length)
      ) {
        workspaceNode.id += `:${workspaceIndex}`
        domainNode.children.push(workspaceNode)
      }
    })
    if (domainNode.menuIds.length || domainNode.children.length) {
      domainNode.id += `:${domainIndex}`
      roots.push(domainNode)
    }
  })

  const legacyContainerTargets: Record<string, string> = {
    '/master-data': 'domain:master-data',
    '/contract': 'domain:commercial',
    '/contract-domain': 'domain:commercial',
    '/cost-domain': 'domain:commercial',
    '/procurement-inventory': 'domain:supply',
    '/system': 'domain:system-management',
    '/subcontract-domain': 'domain:subcontract-settlement',
    '/payment-invoice': 'domain:finance',
    '/inventory': 'workspace:supply:inventory',
    '/invoice': 'workspace:finance:receivables-payables',
    '/approval-center': 'workspace:system-management:workflow',
    '/alert': 'workspace:workbench:cockpit',
  }
  for (const menu of menus.value) {
    if (claimed.has(menu.id) || menu.menuType !== 'DIR') continue
    const target = containerNodes.get(legacyContainerTargets[normalizePath(menu.path)])
    if (!target) continue
    target.menuIds.push(menu.id)
    claimed.add(menu.id)
  }

  const contextualTargets: Record<string, string> = {
    'contract:submit': '/contract/ledger',
    'contract:change:submit': '/contract/ledger',
    'variation:order:submit': '/variation/order',
    'purchase:order:submit': '/purchase/order',
    'receipt:submit': '/purchase/receipt',
    'subcontract:measure:submit': '/subcontract/measure',
    'settlement:submit': '/settlement/list',
    'payment:app:submit': '/payment/application',
    'workflow:approve': '/approval/todo',
    'workflow:reject': '/approval/todo',
    'workflow:transfer': '/approval/todo',
    'workflow:add-sign': '/approval/todo',
    'workflow:withdraw': '/approval/mine',
    'workflow:resubmit': '/approval/mine',
    'project:member:list': '/project/list',
    'project:member:add': '/project/list',
    'project:member:edit': '/project/list',
    'project:member:delete': '/project/list',
    'inventory:transaction:list': '/inventory/stock',
    'inventory:transaction:add': '/inventory/stock',
    'alert:view': '/dashboard',
    'alert:edit': '/dashboard',
    'alert:evaluate': '/dashboard',
    'payment:record:query': '/finance-operations',
    'payment:record:reverse': '/finance-operations',
    'payment:record:writeback': '/finance-operations',
    'payment:trace:query': '/finance-operations',
    'system:user:query': '/system/users',
    'system:role:query': '/system/roles',
    'system:menu:query': '/system/permissions',
    'project:query': '/project/list',
    'contract:query': '/contract/ledger',
    'partner:query': '/partner',
    'org:query': '/org',
  }
  for (const menu of [...menus.value].sort(compareMenus)) {
    if (claimed.has(menu.id) || !menu.perms) continue
    const target = tabNodes.get(contextualTargets[menu.perms])
    if (target) target.children.push(actualNode(menu))
  }

  const notificationDirectory = take((menu) => menu.id === '730')
  if (notificationDirectory) claimed.add(notificationDirectory.id)
  const notificationMenu = take(
    (menu) =>
      menu.id === '761' || menu.perms === 'notification:view' || menu.perms === 'notification:edit',
  )
  if (notificationDirectory || notificationMenu) {
    roots.push({
      id: 'navigation:global',
      label: '全局功能',
      menuType: 'DIR',
      status: 'ENABLE',
      menuIds: notificationDirectory ? [notificationDirectory.id] : [],
      children: notificationMenu ? [actualNode(notificationMenu, '顶栏通知中心')] : [],
    })
  }

  const unclaimed = new Set(
    menus.value.filter((menu) => !claimed.has(menu.id)).map((menu) => menu.id),
  )
  const unmatched = menus.value
    .filter((menu) => unclaimed.has(menu.id) && !unclaimed.has(menu.parentId))
    .sort(compareMenus)
    .map((menu) => actualNode(menu))
  if (unmatched.length) {
    roots.push({
      id: 'navigation:unmatched',
      label: '待治理配置（非导航入口）',
      menuType: 'DIR',
      status: 'REVIEW',
      menuIds: [],
      children: unmatched,
    })
  }
  return roots
})
const permissionNodeMap = computed(() => {
  const result = new Map<string, PermissionNode>()
  const visit = (node: PermissionNode): void => {
    result.set(node.id, node)
    node.children.forEach(visit)
  }
  permissionTree.value.forEach(visit)
  return result
})
const permissionRows = computed<PermissionRow[]>(() => {
  const rows: PermissionRow[] = []
  const visit = (node: PermissionNode, depth: number): void => {
    rows.push({ node, depth, hasChildren: node.children.length > 0 })
    if (expandedMenuIds.value.has(node.id)) {
      node.children.forEach((child) => visit(child, depth + 1))
    }
  }
  permissionTree.value.forEach((node) => visit(node, 0))
  return rows
})
const permissionsDirty = computed(
  () =>
    selectedMenuIds.value.size !== savedMenuIds.value.size ||
    [...selectedMenuIds.value].some((id) => !savedMenuIds.value.has(id)),
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
    else await refreshPermissions()
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
}

async function refreshRoles(): Promise<void> {
  roles.value = await loadRoles()
}

async function refreshPermissions(): Promise<void> {
  const [currentRoles, currentMenus] = await Promise.all([loadRoles(), loadMenus()])
  roles.value = currentRoles
  menus.value = currentMenus
  expandedMenuIds.value = new Set(
    [...permissionNodeMap.value.values()]
      .filter((node) => node.children.length > 0)
      .map((node) => node.id),
  )
  const nextRoleId =
    currentRoles.find((role) => role.id === selectedRoleId.value)?.id ?? currentRoles[0]?.id ?? ''
  if (nextRoleId) await selectRole(nextRoleId, true)
  else applyRoleMenus([])
}

function clearRows(): void {
  users.value = []
  roles.value = []
  menus.value = []
  total.value = 0
  selectedUserRoleId.value = ''
  selectedRoleId.value = ''
  applyRoleMenus([])
}

function applyRoleMenus(menuIds: string[]): void {
  selectedMenuIds.value = new Set(menuIds)
  savedMenuIds.value = new Set(menuIds)
}

async function selectRole(roleId: string, throwOnError = false): Promise<void> {
  if (
    !roleId ||
    (!throwOnError && roleId === selectedRoleId.value && savedMenuIds.value.size > 0)
  ) {
    return
  }
  const requestVersion = ++roleLoadVersion
  roleLoading.value = true
  try {
    const detail = await loadRole(roleId)
    if (requestVersion !== roleLoadVersion) return
    selectedRoleId.value = detail.id
    applyRoleMenus(detail.menuIds)
    const index = roles.value.findIndex((role) => role.id === detail.id)
    if (index >= 0) roles.value.splice(index, 1, detail)
  } catch (value) {
    if (throwOnError) throw value
    showToast('error', '角色权限加载失败', messageOf(value))
  } finally {
    if (requestVersion === roleLoadVersion) roleLoading.value = false
  }
}

function subtreeMenuIds(nodeId: string): string[] {
  const result = new Set<string>()
  const visit = (node: PermissionNode): void => {
    node.menuIds.forEach((id) => result.add(id))
    node.children.forEach(visit)
  }
  const node = permissionNodeMap.value.get(nodeId)
  if (node) visit(node)
  return [...result]
}

function menuChecked(nodeId: string): boolean {
  const ids = subtreeMenuIds(nodeId)
  return ids.length > 0 && ids.every((id) => selectedMenuIds.value.has(id))
}

function menuIndeterminate(nodeId: string): boolean {
  const ids = subtreeMenuIds(nodeId)
  const selected = ids.filter((id) => selectedMenuIds.value.has(id)).length
  return selected > 0 && selected < ids.length
}

function toggleMenu(nodeId: string, checked: boolean): void {
  const next = new Set(selectedMenuIds.value)
  subtreeMenuIds(nodeId).forEach((id) => (checked ? next.add(id) : next.delete(id)))
  selectedMenuIds.value = next
}

function toggleExpanded(menuId: string): void {
  const next = new Set(expandedMenuIds.value)
  if (next.has(menuId)) next.delete(menuId)
  else next.add(menuId)
  expandedMenuIds.value = next
}

function expandAllMenus(): void {
  expandedMenuIds.value = new Set(
    [...permissionNodeMap.value.values()]
      .filter((node) => node.children.length > 0)
      .map((node) => node.id),
  )
}

function collapseAllMenus(): void {
  expandedMenuIds.value = new Set()
}

function selectAllMenus(): void {
  selectedMenuIds.value = new Set(menus.value.map((menu) => menu.id))
}

function clearAllMenus(): void {
  selectedMenuIds.value = new Set()
}

async function savePermissions(): Promise<void> {
  if (!selectedRole.value || !canRoleAssign.value || !permissionsDirty.value) return
  saving.value = true
  try {
    await assignRoleMenus(selectedRole.value.id, [...selectedMenuIds.value])
    const detail = await loadRole(selectedRole.value.id)
    applyRoleMenus(detail.menuIds)
    const index = roles.value.findIndex((role) => role.id === detail.id)
    if (index >= 0) roles.value.splice(index, 1, detail)
    showToast('success', '角色权限已保存', '已按服务端最新授权结果刷新。')
  } catch (value) {
    showToast('error', '角色权限保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

function searchUsers(): void {
  pageNo.value = 1
  void refresh()
}

function selectUserRole(roleId: string): void {
  if (roleId === selectedUserRoleId.value) return
  selectedUserRoleId.value = roleId
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
    if (editingRole.value) await updateRole(editingRole.value.id, command)
    else await createRole(command)
    roleDialog.value = false
    await refreshRoles()
    showToast('success', '角色已保存', '最新角色事实已载入。')
  } catch (value) {
    showToast('error', '角色保存失败', messageOf(value))
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
            @update:model-value="searchUsers"
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
      </template>
    </V2Card>
    <V2PageState v-if="loading" kind="loading" :title="`正在读取${title}`" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" :title="`${title}加载失败`" :description="error">
      <template #actions><V2Button @click="refresh">重试</V2Button></template>
    </V2PageState>

    <V2Card v-else-if="mode === 'users'">
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
          <div v-else class="access-control-page__table-wrap">
            <table class="v2-table--compact user-workspace__table" data-table-identity="contextual">
              <thead>
                <tr>
                  <th>用户名</th>
                  <th>姓名</th>
                  <th>联系方式</th>
                  <th>状态</th>
                  <th class="v2-table-cell--actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in users" :key="item.id">
                  <th scope="row">{{ item.username }}</th>
                  <td>{{ item.realName || '—' }}</td>
                  <td>{{ item.phone || item.email || '—' }}</td>
                  <td>
                    <V2Badge :tone="item.status === 'ENABLE' ? 'success' : 'neutral'">
                      {{ item.status === 'ENABLE' ? '启用' : '停用' }}
                    </V2Badge>
                  </td>
                  <td class="v2-table-cell--actions">
                    <div class="access-control-page__actions">
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
                          @click="
                            deleteTarget = { kind: 'user', id: item.id, label: item.username }
                          "
                        >
                          删除
                        </V2Button>
                      </V2ActionMenu>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
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
      </div>
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
              <th class="v2-table-cell--actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in visibleRoles" :key="item.id">
              <th scope="row">{{ item.roleCode }}</th>
              <td>{{ item.roleName }}</td>
              <td>{{ roleTypeLabel(item.roleType) }}</td>
              <td>{{ dataScopeLabel(item.dataScope) }}</td>
              <td>{{ item.status === 'ENABLE' ? '启用' : '停用' }}</td>
              <td class="v2-table-cell--actions">
                <div class="access-control-page__actions">
                  <V2ActionMenu
                    v-if="!isProtectedRole(item) && (canRoleEdit || canRoleDelete)"
                    :label="`${item.roleCode || item.roleName}更多操作`"
                    :placement="index >= visibleRoles.length - 3 ? 'top-end' : 'bottom-end'"
                  >
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
                  </V2ActionMenu>
                  <V2Badge v-if="isProtectedRole(item)" tone="warning">受保护</V2Badge>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <nav class="access-control-page__pagination v2-pagination" aria-label="角色分页">
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
        </nav>
      </template>
    </V2Card>

    <div v-else class="permission-workspace">
      <V2PageState
        v-if="!roles.length || !menus.length"
        kind="empty"
        title="暂无可配置权限"
        description="当前租户缺少角色或菜单权限数据。"
      />
      <template v-else>
        <V2Card title="角色" :heading-level="2" class="permission-workspace__roles">
          <V2Input
            v-model="roleSearch"
            label="搜索角色名称或编码"
            hide-label
            placeholder="搜索角色名称或编码"
          />
          <div class="permission-role-list" role="group" aria-label="角色">
            <V2Button
              v-for="role in filteredRoles"
              :key="role.id"
              class="permission-role-list__item"
              size="medium"
              :variant="role.id === selectedRoleId ? 'secondary' : 'ghost'"
              :aria-pressed="role.id === selectedRoleId"
              :disabled="roleLoading"
              @click="selectRole(role.id)"
            >
              <strong>{{ role.roleName }}</strong>
              <span class="permission-role-list__count">{{ role.menuIds.length }} 项</span>
            </V2Button>
            <p v-if="!filteredRoles.length" class="permission-role-list__empty">未找到匹配角色</p>
          </div>
        </V2Card>

        <V2Card
          :title="selectedRole?.roleName || '请选择角色'"
          :heading-level="2"
          class="permission-workspace__matrix"
        >
          <template #actions>
            <span class="permission-workspace__count">已选 {{ selectedMenuIds.size }} 项</span>
            <V2Button size="small" variant="secondary" @click="expandAllMenus">全部展开</V2Button>
            <V2Button size="small" variant="secondary" @click="collapseAllMenus">
              全部收起
            </V2Button>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="!canRoleAssign || roleLoading"
              @click="selectAllMenus"
            >
              全选
            </V2Button>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="!canRoleAssign || roleLoading"
              @click="clearAllMenus"
            >
              清空
            </V2Button>
            <V2Button
              v-if="canRoleAssign"
              size="small"
              :loading="saving"
              :disabled="roleLoading || !selectedRole || !permissionsDirty"
              @click="savePermissions"
            >
              保存权限
            </V2Button>
          </template>

          <V2PageState
            v-if="roleLoading"
            kind="loading"
            title="正在读取角色权限"
            description="请稍候。"
          />
          <div
            v-else
            class="access-control-page__table-wrap permission-tree-region"
            role="region"
            aria-label="目录、菜单与按钮权限"
            tabindex="0"
          >
            <table class="v2-table--compact" data-table-identity="contextual">
              <thead>
                <tr>
                  <th>权限名称</th>
                  <th>类型</th>
                  <th>权限标识</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="{ node, depth, hasChildren } in permissionRows" :key="node.id">
                  <th scope="row" :style="{ paddingInlineStart: `${depth * 24 + 12}px` }">
                    <span class="permission-tree-table__name">
                      <V2Button
                        v-if="hasChildren"
                        size="small"
                        variant="ghost"
                        :aria-label="
                          expandedMenuIds.has(node.id) ? `收起${node.label}` : `展开${node.label}`
                        "
                        :aria-expanded="expandedMenuIds.has(node.id)"
                        @click="toggleExpanded(node.id)"
                      >
                        {{ expandedMenuIds.has(node.id) ? '⌄' : '›' }}
                      </V2Button>
                      <span v-else class="permission-tree-table__spacer" aria-hidden="true" />
                      <input
                        type="checkbox"
                        :aria-label="`${node.label}权限`"
                        :checked="menuChecked(node.id)"
                        :indeterminate="menuIndeterminate(node.id)"
                        :disabled="!canRoleAssign || !subtreeMenuIds(node.id).length"
                        @change="toggleMenu(node.id, ($event.target as HTMLInputElement).checked)"
                      />
                      <span>{{ node.label }}</span>
                    </span>
                  </th>
                  <td>
                    <span
                      class="permission-tree-table__type"
                      :class="`permission-tree-table__type--${node.menuType.toLowerCase()}`"
                    >
                      {{ menuTypeLabel(node.menuType) }}
                    </span>
                  </td>
                  <td>
                    <code :title="node.perms || node.path || undefined">
                      {{ node.perms || node.path || '—' }}
                    </code>
                  </td>
                  <td>
                    <span
                      :class="
                        node.status !== 'ENABLE'
                          ? 'permission-tree-table__status--disabled'
                          : menuIndeterminate(node.id)
                            ? 'permission-tree-table__status--partial'
                            : menuChecked(node.id)
                              ? 'permission-tree-table__status--active'
                              : 'permission-tree-table__status--inactive'
                      "
                    >
                      {{
                        node.status === 'MISSING'
                          ? '缺少菜单记录'
                          : node.status === 'REVIEW'
                            ? '需治理'
                            : node.status !== 'ENABLE'
                              ? '停用项'
                              : menuIndeterminate(node.id)
                                ? '部分分配'
                                : menuChecked(node.id)
                                  ? '已分配'
                                  : '未分配'
                      }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
      </template>
    </div>

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
      </div>
      <template #footer>
        <V2Button variant="secondary" :disabled="saving" @click="roleDialog = false">取消</V2Button>
        <V2Button :loading="saving" @click="saveRole">保存</V2Button>
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

.permission-workspace {
  display: grid;
  grid-template-columns: minmax(14rem, 17rem) minmax(0, 1fr);
  gap: var(--v2-space-4);
  align-items: start;
}

.permission-workspace__roles,
.permission-workspace__matrix {
  min-width: 0;
}

.permission-workspace__matrix :deep(.v2-card__header) {
  align-items: stretch;
  flex-direction: column;
}

.permission-workspace__matrix :deep(.v2-card__actions) {
  width: 100%;
  min-width: 0;
  justify-content: flex-start;
}

.permission-workspace__matrix :deep(.v2-card__body) {
  min-width: 0;
}

.permission-workspace__count {
  color: var(--v2-color-primary);
  font-size: var(--v2-font-size-13);
  font-weight: var(--v2-font-weight-semibold);
  white-space: nowrap;
}

.user-workspace {
  display: grid;
  grid-template-columns: minmax(14rem, 0.65fr) minmax(36rem, 1.35fr);
  gap: var(--v2-space-4);
}

.user-workspace > section {
  min-width: 0;
}

.user-workspace__section-heading {
  display: flex;
  min-height: 2.5rem;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-2);
  margin-bottom: var(--v2-space-3);
}

.user-workspace__section-heading h3 {
  margin: 0;
}

.user-workspace__section-heading > span {
  color: var(--v2-color-text-muted);
}

.permission-role-list {
  display: grid;
  max-height: calc(var(--v2-space-12) * 13);
  margin-top: var(--v2-space-3);
  overflow-y: auto;
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.permission-role-list__item {
  width: 100%;
  text-align: left;
}

.permission-role-list__item > span {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-3);
}

.permission-role-list__item strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-role-list__empty {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-13);
}

.permission-role-list__count {
  flex: 0 0 auto;
  padding: 0 var(--v2-space-1);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  background: var(--v2-color-surface-subtle);
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

.permission-role-list__empty {
  margin: 0;
  padding: var(--v2-space-5) var(--v2-space-3);
  text-align: center;
}

.permission-tree-region {
  max-height: calc(var(--v2-space-12) * 14);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.permission-tree-region table {
  width: 100%;
  min-width: 38rem;
  table-layout: fixed;
}

.permission-tree-region thead {
  position: sticky;
  z-index: 1;
  top: 0;
  background: var(--v2-color-surface-subtle);
}

.permission-tree-region th:first-child {
  width: 40%;
}

.permission-tree-region th:nth-child(2) {
  width: 12%;
}

.permission-tree-region th:nth-child(3) {
  width: 33%;
}

.permission-tree-region th:nth-child(4) {
  width: 15%;
}

.permission-tree-region code {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.permission-tree-table__name {
  display: inline-flex;
  gap: var(--v2-space-2);
  align-items: center;
  font-weight: var(--v2-font-weight-medium);
}

.permission-tree-table__name input {
  width: var(--v2-space-4);
  height: var(--v2-space-4);
  margin: 0;
  accent-color: var(--v2-color-primary);
}

.permission-tree-table__spacer {
  display: inline-grid;
  width: var(--v2-space-5);
  height: var(--v2-space-5);
  flex: 0 0 var(--v2-space-5);
  place-items: center;
}

.permission-tree-table__type {
  display: inline-block;
  padding: var(--v2-space-0) var(--v2-space-1);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-12);
}

.permission-tree-table__type--menu {
  border-color: var(--v2-color-primary);
  color: var(--v2-color-primary);
}

.permission-tree-table__type--button,
.permission-tree-table__status--active {
  color: var(--v2-color-success);
}

.permission-tree-table__status--partial {
  color: var(--v2-color-warning-text);
}

.permission-tree-table__status--inactive,
.permission-tree-table__status--disabled {
  color: var(--v2-color-text-muted);
}

.permission-tree-table__status--disabled {
  text-decoration: line-through;
}

@media (max-width: 980px) {
  .permission-workspace,
  .user-workspace {
    grid-template-columns: 1fr;
  }

  .permission-role-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    max-height: calc(var(--v2-space-12) * 6);
  }
}

@media (max-width: 760px) {
  .access-control-page__form,
  .access-control-page__choices {
    grid-template-columns: 1fr;
  }

  .permission-role-list {
    grid-template-columns: 1fr;
  }
}
</style>
