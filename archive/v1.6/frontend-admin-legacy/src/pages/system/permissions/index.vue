<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ControlOutlined,
  DeleteOutlined,
  DownOutlined,
  ExportOutlined,
  EditOutlined,
  EyeOutlined,
  FileOutlined,
  FolderOpenOutlined,
  MenuOutlined,
  PauseCircleOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import axios from 'axios'
import {
  deleteMenu,
  getMenuDetail,
  getMenuList,
  getMenuTree,
  getRoles,
  updateMenu,
} from '@/api/modules/system'
import { useUserStore } from '@/stores/user'
import type { MenuTreeVO, SysMenuVO, SysRoleVO, UpdateMenuPayload } from '@/types/system'

interface PermissionRow {
  id: string
  permissionCode: string
  menuName: string
  path: string
  sourceRemark: string
  bindingStatus: string
  moduleName: string
  menuType: MenuTreeVO['menuType']
  status: string
  roles: string[]
  level: number
  hasChildren: boolean
  menu: MenuTreeVO
}

const loading = ref(false)
const menuTree = ref<MenuTreeVO[]>([])
const roles = ref<SysRoleVO[]>([])
const permissionMenus = ref<SysMenuVO[]>([])
const userStore = useUserStore()
const updateOpen = ref(false)
const updating = ref(false)
const updateTargetId = ref<number | string>()
const updateForm = reactive<UpdateMenuPayload>(defaultUpdateForm())
const updateReady = ref(false)
const updateError = ref('')
let updateRequestSequence = 0
const deleteOpen = ref(false)
const deleting = ref(false)
const deleteTargetId = ref<number | string>()
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailTargetId = ref<number | string>()
const menuDetail = ref<SysMenuVO>()
const detailError = ref('')
let detailRequestSequence = 0
const listOpen = ref(false)
const listLoading = ref(false)
const flatMenus = ref<SysMenuVO[]>([])
const listError = ref('')
let listRequestSequence = 0
const keyword = ref('')
const moduleFilter = ref('ALL')
const typeFilter = ref<'ALL' | MenuTreeVO['menuType']>('ALL')
const statusFilter = ref<'ALL' | 'ENABLE' | 'DISABLE'>('ALL')
const roleFilter = ref('ALL')
const activeType = ref<'ALL' | MenuTreeVO['menuType']>('ALL')
const appliedFilters = reactive({
  keyword: '',
  module: 'ALL',
  type: 'ALL' as 'ALL' | MenuTreeVO['menuType'],
  status: 'ALL' as 'ALL' | 'ENABLE' | 'DISABLE',
  role: 'ALL',
})
const expandedIds = ref(new Set<string>())
const selectedIds = ref(new Set<string>())
const pageNo = ref(1)
const pageSize = ref(20)
const actionLoadingIds = ref(new Set<string>())
const columnVisibility = reactive({ roles: true, path: true, updated: true })
const permissionTabs: Array<{ key: 'ALL' | MenuTreeVO['menuType']; label: string }> = [
  { key: 'ALL', label: '全部权限' },
  { key: 'MENU', label: '菜单权限' },
  { key: 'BUTTON', label: '按钮权限' },
  { key: 'DIR', label: '目录权限' },
]

const isAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canUpdateMenu = computed(() => isAdmin.value)
const canDeleteMenu = computed(() => isAdmin.value)
const canViewMenuDetail = computed(() => isAdmin.value)
const canViewMenuList = computed(() => isAdmin.value)
const deleteTreeData = computed(() => toDeleteTreeData(menuTree.value))
const detailTreeData = computed(() => toDeleteTreeData(menuTree.value))
const updateParentTreeData = computed(() => [
  {
    title: '根节点',
    value: 0,
    children: toUpdateParentTreeData(menuTree.value, updateTargetId.value),
  },
])
const selectedDeleteMenu = computed(() => findMenu(menuTree.value, deleteTargetId.value))

const rows = computed(() => flattenPermissions(menuTree.value, roles.value, permissionMenus.value))
const moduleOptions = computed(() => {
  const names = new Set(
    menuTree.value
      .filter((menu) => menu.menuType === 'DIR')
      .map((menu) => menu.menuName)
      .filter(Boolean),
  )
  return [...names].map((name) => ({ label: name, value: name }))
})
const typeCounts = computed(() => ({
  ALL: rows.value.length,
  DIR: rows.value.filter((row) => row.menuType === 'DIR').length,
  MENU: rows.value.filter((row) => row.menuType === 'MENU').length,
  BUTTON: rows.value.filter((row) => row.menuType === 'BUTTON').length,
}))
const enabledCount = computed(() => rows.value.filter((row) => row.status === 'ENABLE').length)
const disabledCount = computed(() => rows.value.filter((row) => row.status !== 'ENABLE').length)
const filteredRows = computed(() => {
  const search = appliedFilters.keyword.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (activeType.value !== 'ALL' && row.menuType !== activeType.value) return false
    if (appliedFilters.module !== 'ALL' && row.moduleName !== appliedFilters.module) return false
    if (appliedFilters.type !== 'ALL' && row.menuType !== appliedFilters.type) return false
    if (appliedFilters.status !== 'ALL' && row.status !== appliedFilters.status) return false
    if (appliedFilters.role !== 'ALL' && !row.roles.includes(appliedFilters.role)) return false
    if (
      search &&
      !`${row.menuName} ${row.permissionCode} ${row.path}`.toLowerCase().includes(search)
    ) {
      return false
    }
    if (search || activeType.value !== 'ALL' || appliedFilters.type !== 'ALL') return true
    if (row.level === 0) return true
    return ancestorExpanded(row.menu)
  })
})
const totalPages = computed(() =>
  Math.max(1, Math.ceil(filteredRows.value.length / pageSize.value)),
)
const pagedRows = computed(() => {
  const start = (pageNo.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value)
})
const visibleSelectedCount = computed(
  () => pagedRows.value.filter((row) => selectedIds.value.has(row.id)).length,
)
const allVisibleSelected = computed(
  () => pagedRows.value.length > 0 && visibleSelectedCount.value === pagedRows.value.length,
)

function flattenPermissions(
  nodes: MenuTreeVO[],
  roleList: SysRoleVO[],
  menuList: SysMenuVO[],
  moduleName = '',
  level = 0,
): PermissionRow[] {
  const result: PermissionRow[] = []
  const menuById = new Map((menuList ?? []).map((menu) => [String(menu.id), menu]))
  const sortedNodes = [...nodes].sort((left, right) => {
    const typeOrder = { DIR: 0, MENU: 1, BUTTON: 2 }
    const typeDifference = typeOrder[left.menuType] - typeOrder[right.menuType]
    if (typeDifference !== 0) return typeDifference
    return Number(left.orderNum ?? 0) - Number(right.orderNum ?? 0)
  })
  for (const menu of sortedNodes) {
    const id = String(menu.id)
    const menuDetail = menuById.get(id)
    const effectiveMenu: MenuTreeVO = {
      ...menu,
      ...menuDetail,
      children: menu.children,
    }
    const nextModuleName =
      level === 0 ? (menu.menuType === 'DIR' ? menu.menuName : '-') : moduleName
    const linkedRoles = roleList
      .filter((role) => (role.menuIds ?? []).some((menuId) => String(menuId) === id))
      .map((role) => role.roleName)
    result.push({
      id,
      permissionCode: String(effectiveMenu.perms ?? '').trim() || '-',
      menuName: effectiveMenu.menuName || '-',
      path: effectiveMenu.path || '-',
      sourceRemark: '/system/menus/tree',
      bindingStatus: linkedRoles.length ? '已绑定' : '未绑定',
      moduleName: nextModuleName,
      menuType: effectiveMenu.menuType,
      status: effectiveMenu.status,
      roles: linkedRoles,
      level,
      hasChildren: Boolean(menu.children?.length),
      menu: effectiveMenu,
    })
    if (menu.children?.length) {
      result.push(
        ...flattenPermissions(menu.children, roleList, menuList, nextModuleName, level + 1),
      )
    }
  }
  return result
}

function ancestorExpanded(menu: MenuTreeVO) {
  let parentId = String(menu.parentId ?? '0')
  while (parentId !== '0') {
    if (!expandedIds.value.has(parentId)) return false
    const parent = rows.value.find((row) => row.id === parentId)
    if (!parent) return true
    parentId = String(parent.menu.parentId ?? '0')
  }
  return true
}

function replaceSetValue(target: typeof expandedIds | typeof selectedIds, next: Set<string>) {
  target.value = next
}

function toggleExpanded(id: string) {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  replaceSetValue(expandedIds, next)
}

function toggleSelected(id: string, checked: boolean) {
  const next = new Set(selectedIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  replaceSetValue(selectedIds, next)
}

function toggleSelectAll(checked: boolean) {
  const next = new Set(selectedIds.value)
  for (const row of pagedRows.value) {
    if (checked) next.add(row.id)
    else next.delete(row.id)
  }
  replaceSetValue(selectedIds, next)
}

function eventChecked(event: Event) {
  return (event.target as HTMLInputElement).checked
}

function rowIndent(level: number) {
  return `${level * 24}px`
}

function selectionAria(name: string) {
  return `选择${name}`
}

function expandAria(row: PermissionRow) {
  return `${expandedIds.value.has(row.id) ? '收起' : '展开'}${row.menuName}`
}

function statusAria(row: PermissionRow) {
  return `${row.status === 'ENABLE' ? '停用' : '启用'}${row.menuName}`
}

function tabCount(key: 'ALL' | MenuTreeVO['menuType']) {
  return typeCounts.value[key]
}

function applyFilters() {
  appliedFilters.keyword = keyword.value
  appliedFilters.module = moduleFilter.value
  appliedFilters.type = typeFilter.value
  appliedFilters.status = statusFilter.value
  appliedFilters.role = roleFilter.value
  pageNo.value = 1
}

function resetFilters() {
  keyword.value = ''
  moduleFilter.value = 'ALL'
  typeFilter.value = 'ALL'
  statusFilter.value = 'ALL'
  roleFilter.value = 'ALL'
  activeType.value = 'ALL'
  applyFilters()
}

function setActiveType(type: 'ALL' | MenuTreeVO['menuType']) {
  activeType.value = type
  pageNo.value = 1
}

function menuTypeClass(menuType: SysMenuVO['menuType']) {
  return { DIR: 'directory', MENU: 'menu', BUTTON: 'button' }[menuType] ?? 'menu'
}

function openUpdateFor(row: PermissionRow) {
  openUpdate()
  void selectUpdateTarget(row.id)
}

function openDetailFor(row: PermissionRow) {
  openDetail()
  void selectDetailTarget(row.id)
}

function openDeleteFor(row: PermissionRow) {
  deleteTargetId.value = row.id
  deleteOpen.value = true
}

function rowUpdatePayload(row: PermissionRow, status: string): UpdateMenuPayload {
  const menu = row.menu
  return {
    parentId: menu.parentId ?? 0,
    menuName: menu.menuName,
    menuType: menu.menuType,
    path: menu.path ?? '',
    component: menu.component ?? '',
    perms: menu.perms ?? '',
    icon: menu.icon ?? '',
    orderNum: Number(menu.orderNum ?? 0),
    status,
    visible: Number(menu.visible ?? 1),
  }
}

async function toggleMenuStatus(row: PermissionRow) {
  if (!canUpdateMenu.value || actionLoadingIds.value.has(row.id)) return
  const nextLoading = new Set(actionLoadingIds.value)
  nextLoading.add(row.id)
  actionLoadingIds.value = nextLoading
  const nextStatus = row.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  try {
    await updateMenu(row.id, rowUpdatePayload(row, nextStatus))
    await fetchData()
    message.success(`${row.menuName}已${nextStatus === 'ENABLE' ? '启用' : '停用'}`)
  } catch (error: unknown) {
    console.error(error)
    message.error(errorMessage(error, '权限状态更新失败'))
  } finally {
    const remaining = new Set(actionLoadingIds.value)
    remaining.delete(row.id)
    actionLoadingIds.value = remaining
  }
}

async function batchEnable() {
  const targets = rows.value.filter(
    (row) => selectedIds.value.has(row.id) && row.status !== 'ENABLE',
  )
  if (!targets.length) {
    message.error(selectedIds.value.size ? '所选权限均已启用' : '请先选择需要启用的权限')
    return
  }
  try {
    await Promise.all(targets.map((row) => updateMenu(row.id, rowUpdatePayload(row, 'ENABLE'))))
    await fetchData()
    message.success(`已启用 ${targets.length} 项权限`)
  } catch (error: unknown) {
    console.error(error)
    message.error(errorMessage(error, '批量启用失败，请刷新后核对状态'))
  }
}

function exportPermissions() {
  const csvRows = [
    ['权限名称', '权限标识', '所属模块', '类型', '关联角色', '接口路径', '状态'],
    ...filteredRows.value.map((row) => [
      row.menuName,
      row.permissionCode,
      row.moduleName,
      menuTypeLabel(row.menuType),
      row.roles.join('、'),
      row.path,
      row.status === 'ENABLE' ? '已启用' : '已停用',
    ]),
  ]
  const csv = `\ufeff${csvRows
    .map((cells) => cells.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(','))
    .join('\n')}`
  const href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = href
  link.download = '权限清单.csv'
  link.click()
  URL.revokeObjectURL(href)
}

function defaultUpdateForm(): UpdateMenuPayload {
  return {
    parentId: 0,
    menuName: '',
    menuType: 'MENU',
    path: '',
    component: '',
    perms: '',
    icon: '',
    orderNum: 0,
    status: 'ENABLE',
    visible: 1,
  }
}

function toDeleteTreeData(nodes: MenuTreeVO[]): Array<{
  title: string
  value: number | string
  menuType: string
  children?: ReturnType<typeof toDeleteTreeData>
}> {
  return nodes.map((node) => ({
    title: node.menuName,
    value: node.id,
    menuType: node.menuType,
    children: toDeleteTreeData(node.children ?? []),
  }))
}

function toUpdateParentTreeData(
  nodes: MenuTreeVO[],
  excludedId?: number | string,
): Array<{
  title: string
  value: number | string
  children?: ReturnType<typeof toUpdateParentTreeData>
}> {
  return nodes.flatMap((node) => {
    if (String(node.id) === String(excludedId) || node.menuType === 'BUTTON') return []
    return [
      {
        title: node.menuName,
        value: node.id,
        children: toUpdateParentTreeData(node.children ?? [], excludedId),
      },
    ]
  })
}

function findMenu(nodes: MenuTreeVO[], id?: number | string): MenuTreeVO | undefined {
  if (id === undefined || id === null || id === '') return undefined
  for (const node of nodes) {
    if (String(node.id) === String(id)) return node
    const child = findMenu(node.children ?? [], id)
    if (child) return child
  }
  return undefined
}

function resetUpdateForm() {
  Object.assign(updateForm, defaultUpdateForm())
  updateReady.value = false
}

function applyUpdateDetail(detail: SysMenuVO) {
  Object.assign(updateForm, {
    parentId: detail.parentId ?? 0,
    menuName: detail.menuName,
    menuType: detail.menuType,
    path: detail.path ?? '',
    component: detail.component ?? '',
    perms: detail.perms ?? '',
    icon: detail.icon ?? '',
    orderNum: detail.orderNum ?? 0,
    status: detail.status,
    visible: detail.visible ?? 1,
  })
  updateReady.value = true
}

function openUpdate() {
  updateRequestSequence += 1
  updateTargetId.value = undefined
  updateError.value = ''
  resetUpdateForm()
  updateOpen.value = true
}

function closeUpdate() {
  if (updating.value) return
  updateRequestSequence += 1
  updateOpen.value = false
  updateTargetId.value = undefined
  updateError.value = ''
  resetUpdateForm()
}

async function selectUpdateTarget(menuId: number | string) {
  updateTargetId.value = menuId
  updateError.value = ''
  resetUpdateForm()
  const requestSequence = ++updateRequestSequence

  try {
    const detail = await getMenuDetail(menuId)
    if (
      requestSequence === updateRequestSequence &&
      String(updateTargetId.value) === String(menuId)
    ) {
      applyUpdateDetail(detail)
    }
  } catch (error: unknown) {
    if (requestSequence !== updateRequestSequence) return
    console.error(error)
    updateError.value = errorMessage(error, '菜单详情加载失败')
    message.error(updateError.value)
  }
}

function openDelete() {
  deleteTargetId.value = undefined
  deleteOpen.value = true
}

function closeDelete() {
  if (deleting.value) return
  deleteOpen.value = false
  deleteTargetId.value = undefined
}

function openDetail() {
  detailRequestSequence += 1
  detailTargetId.value = undefined
  menuDetail.value = undefined
  detailError.value = ''
  detailOpen.value = true
}

function closeDetail() {
  if (detailLoading.value) return
  detailRequestSequence += 1
  detailOpen.value = false
  detailTargetId.value = undefined
  menuDetail.value = undefined
  detailError.value = ''
}

async function openMenuList() {
  listOpen.value = true
  flatMenus.value = []
  listError.value = ''
  listLoading.value = true
  const requestSequence = ++listRequestSequence

  try {
    const menus = await getMenuList()
    if (requestSequence === listRequestSequence) flatMenus.value = menus
  } catch (error: unknown) {
    if (requestSequence !== listRequestSequence) return
    console.error(error)
    listError.value = errorMessage(error, '菜单列表加载失败')
    message.error(listError.value)
  } finally {
    if (requestSequence === listRequestSequence) listLoading.value = false
  }
}

function closeMenuList() {
  if (listLoading.value) return
  listRequestSequence += 1
  listOpen.value = false
  flatMenus.value = []
  listError.value = ''
}

async function selectDetailTarget(menuId: number | string) {
  detailTargetId.value = menuId
  menuDetail.value = undefined
  detailError.value = ''
  detailLoading.value = true
  const requestSequence = ++detailRequestSequence

  try {
    const detail = await getMenuDetail(menuId)
    if (requestSequence === detailRequestSequence) menuDetail.value = detail
  } catch (error: unknown) {
    if (requestSequence !== detailRequestSequence) return
    console.error(error)
    detailError.value = errorMessage(error, '菜单详情加载失败')
    message.error(detailError.value)
  } finally {
    if (requestSequence === detailRequestSequence) detailLoading.value = false
  }
}

function displayValue(value: unknown) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function menuTypeLabel(menuType: SysMenuVO['menuType']) {
  return { DIR: '目录', MENU: '菜单', BUTTON: '按钮' }[menuType] ?? menuType
}

function statusLabel(status: string) {
  return { ENABLE: '启用', DISABLE: '停用' }[status] ?? displayValue(status)
}

function visibleLabel(visible?: number) {
  if (visible === 1) return '可见'
  if (visible === 0) return '隐藏'
  return '-'
}

function errorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    return (error.response?.data as { message?: string })?.message || error.message || fallback
  }
  return error instanceof Error ? error.message : fallback
}

async function submitUpdate() {
  const targetId = updateTargetId.value
  const menuName = updateForm.menuName.trim()
  if (targetId === undefined || !updateReady.value) {
    message.error('请选择要修改的菜单并等待详情加载完成')
    return
  }
  if (!menuName || !updateForm.menuType) {
    message.error('请填写菜单名称和菜单类型')
    return
  }

  const payload: UpdateMenuPayload = {
    parentId: updateForm.parentId ?? 0,
    menuName,
    menuType: updateForm.menuType,
    path: updateForm.path.trim(),
    component: updateForm.component.trim(),
    perms: updateForm.perms.trim(),
    icon: updateForm.icon.trim(),
    orderNum: Number(updateForm.orderNum ?? 0),
    status: updateForm.status,
    visible: Number(updateForm.visible),
  }

  updating.value = true
  updateError.value = ''
  let updated = false
  try {
    await updateMenu(targetId, payload)
    updated = true
    const [nextMenus, nextFlatMenus, nextDetail] = await Promise.all([
      getMenuTree(),
      getMenuList(),
      getMenuDetail(targetId),
    ])
    menuTree.value = nextMenus
    permissionMenus.value = nextFlatMenus ?? []
    flatMenus.value = nextFlatMenus ?? []
    menuDetail.value = nextDetail
    applyUpdateDetail(nextDetail)
    message.success(`菜单“${nextDetail.menuName}”已修改`)
  } catch (error: unknown) {
    console.error(error)
    const detail = errorMessage(error, updated ? '请手动刷新菜单数据' : '菜单修改失败')
    updateError.value = updated ? `菜单已修改，但刷新菜单数据失败：${detail}` : detail
    message.error(updateError.value)
  } finally {
    updating.value = false
  }
}

async function submitDelete() {
  const target = selectedDeleteMenu.value
  if (!target) {
    message.error('请选择要删除的菜单')
    return
  }

  deleting.value = true
  let deleted = false
  try {
    await deleteMenu(target.id)
    deleted = true
    const [nextMenus, nextRoles, nextMenuList] = await loadData()
    if (findMenu(nextMenus, target.id)) {
      throw new Error('刷新后的菜单树仍包含已删除目标')
    }
    menuTree.value = nextMenus
    roles.value = nextRoles
    permissionMenus.value = nextMenuList ?? []
    message.success(`菜单“${target.menuName}”已删除`)
    deleteOpen.value = false
    deleteTargetId.value = undefined
  } catch (error: unknown) {
    console.error(error)
    const detail = errorMessage(error, deleted ? '请手动刷新权限清单' : '菜单删除失败')
    message.error(deleted ? `菜单已删除，但刷新权限清单失败：${detail}` : detail)
  } finally {
    deleting.value = false
  }
}

function loadData() {
  return Promise.all([getMenuTree(), getRoles(), getMenuList()])
}

async function fetchData() {
  loading.value = true
  try {
    const [nextMenus, nextRoles, nextMenuList] = await loadData()
    menuTree.value = nextMenus
    roles.value = nextRoles
    permissionMenus.value = nextMenuList ?? []
    const firstExpandableMenu = nextMenus.find((menu) => menu.children?.length)
    if (!expandedIds.value.size && firstExpandableMenu) {
      expandedIds.value = new Set([String(firstExpandableMenu.id)])
    }
    const validIds = new Set(
      flattenPermissions(nextMenus, nextRoles, nextMenuList ?? []).map((row) => row.id),
    )
    selectedIds.value = new Set([...selectedIds.value].filter((id) => validIds.has(id)))
  } catch (e: unknown) {
    console.error(e)
    message.error(errorMessage(e, '加载权限清单失败'))
    menuTree.value = []
    roles.value = []
    permissionMenus.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="permission-page app-page">
    <a-breadcrumb class="permission-breadcrumb">
      <a-breadcrumb-item>系统管理</a-breadcrumb-item>
      <a-breadcrumb-item>权限清单</a-breadcrumb-item>
    </a-breadcrumb>

    <section class="permission-summary" aria-label="权限概览">
      <article class="summary-item summary-blue">
        <SafetyCertificateOutlined class="summary-icon" />
        <div>
          <strong>{{ rows.length }}</strong
          ><span>权限总数</span>
        </div>
      </article>
      <article class="summary-item summary-blue">
        <TeamOutlined class="summary-icon" />
        <div>
          <strong>{{ roles.length }}</strong
          ><span>系统角色</span>
        </div>
      </article>
      <article class="summary-item summary-green">
        <CheckCircleOutlined class="summary-icon" />
        <div>
          <strong>{{ enabledCount }}</strong
          ><span>已启用</span>
        </div>
      </article>
      <article class="summary-item summary-amber">
        <PauseCircleOutlined class="summary-icon" />
        <div>
          <strong>{{ disabledCount }}</strong
          ><span>已停用</span>
        </div>
      </article>
    </section>

    <section class="permission-filters" aria-label="筛选权限">
      <label class="search-control">
        <SearchOutlined />
        <input
          v-model="keyword"
          type="search"
          placeholder="搜索权限名称、标识或接口路径"
          @keyup.enter="applyFilters"
        />
      </label>
      <select v-model="moduleFilter" aria-label="所属模块">
        <option value="ALL">全部模块</option>
        <option v-for="option in moduleOptions" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
      <select v-model="typeFilter" aria-label="权限类型">
        <option value="ALL">全部类型</option>
        <option value="DIR">目录权限</option>
        <option value="MENU">菜单权限</option>
        <option value="BUTTON">按钮权限</option>
      </select>
      <select v-model="statusFilter" aria-label="权限状态">
        <option value="ALL">全部状态</option>
        <option value="ENABLE">已启用</option>
        <option value="DISABLE">已停用</option>
      </select>
      <select v-model="roleFilter" aria-label="关联角色">
        <option value="ALL">全部角色</option>
        <option v-for="role in roles" :key="String(role.id)" :value="role.roleName">
          {{ role.roleName }}
        </option>
      </select>
      <a-button type="primary" @click="applyFilters">查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
    </section>

    <section class="permission-table-card">
      <header class="permission-table-head">
        <nav class="permission-tabs" aria-label="权限类型">
          <button
            v-for="tab in permissionTabs"
            :key="tab.key"
            type="button"
            :class="{ active: activeType === tab.key }"
            @click="setActiveType(tab.key)"
          >
            {{ tab.label }} <span>{{ tabCount(tab.key) }}</span>
          </button>
        </nav>
        <div class="permission-table-actions">
          <a-button :disabled="!canUpdateMenu" @click="batchEnable">批量启用</a-button>
          <a-button @click="exportPermissions">
            <template #icon><ExportOutlined /></template>
            导出
          </a-button>
          <details class="column-settings">
            <summary><ControlOutlined />列设置</summary>
            <div class="column-settings-menu">
              <label><input v-model="columnVisibility.roles" type="checkbox" />关联角色</label>
              <label><input v-model="columnVisibility.path" type="checkbox" />接口路径</label>
              <label><input v-model="columnVisibility.updated" type="checkbox" />更新时间</label>
              <div v-if="isAdmin" class="column-settings-divider"></div>
              <a-button v-if="canViewMenuList" data-testid="menu-list-open" @click="openMenuList">
                菜单列表
              </a-button>
              <a-button v-if="canViewMenuDetail" data-testid="detail-menu-open" @click="openDetail">
                <template #icon><EyeOutlined /></template>
                查看详情
              </a-button>
              <a-button v-if="canUpdateMenu" data-testid="update-menu-open" @click="openUpdate">
                <template #icon><EditOutlined /></template>
                修改菜单
              </a-button>
              <a-button
                v-if="canDeleteMenu"
                danger
                data-testid="delete-menu-open"
                @click="openDelete"
              >
                <template #icon><DeleteOutlined /></template>
                删除菜单
              </a-button>
            </div>
          </details>
        </div>
      </header>

      <div class="permission-table-scroll" :class="{ loading }">
        <table class="permission-table">
          <thead>
            <tr>
              <th class="check-cell">
                <input
                  type="checkbox"
                  aria-label="选择当前页"
                  :checked="allVisibleSelected"
                  @change="toggleSelectAll(eventChecked($event))"
                />
              </th>
              <th>权限名称</th>
              <th>权限标识</th>
              <th>所属模块</th>
              <th>类型</th>
              <th v-if="columnVisibility.roles">关联角色</th>
              <th v-if="columnVisibility.path">接口路径</th>
              <th>状态</th>
              <th v-if="columnVisibility.updated">更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in pagedRows"
              :key="row.id"
              data-testid="permission-row"
              :class="{ selected: selectedIds.has(row.id) }"
            >
              <td class="check-cell">
                <input
                  type="checkbox"
                  :aria-label="selectionAria(row.menuName)"
                  :checked="selectedIds.has(row.id)"
                  @change="toggleSelected(row.id, eventChecked($event))"
                />
              </td>
              <td>
                <div class="permission-name" :style="{ paddingLeft: rowIndent(row.level) }">
                  <button
                    v-if="row.hasChildren"
                    class="tree-toggle"
                    type="button"
                    :aria-label="expandAria(row)"
                    @click="toggleExpanded(row.id)"
                  >
                    <DownOutlined :class="{ collapsed: !expandedIds.has(row.id) }" />
                  </button>
                  <span v-else class="tree-spacer"></span>
                  <FolderOpenOutlined v-if="row.menuType === 'DIR'" class="row-icon" />
                  <MenuOutlined v-else-if="row.menuType === 'MENU'" class="row-icon" />
                  <FileOutlined v-else class="row-icon" />
                  <span class="permission-name-text">{{ row.menuName }}</span>
                </div>
              </td>
              <td>
                <code>{{ row.permissionCode }}</code>
              </td>
              <td>{{ row.moduleName }}</td>
              <td>
                <span class="permission-type" :class="menuTypeClass(row.menuType)">
                  {{ menuTypeLabel(row.menuType) }}
                </span>
              </td>
              <td v-if="columnVisibility.roles">
                <div v-if="row.roles.length" class="role-list">
                  <span class="role-chip">{{ row.roles[0] }}</span>
                  <button v-if="row.roles.length > 1" type="button" @click="openDetailFor(row)">
                    +{{ row.roles.length - 1 }}
                  </button>
                </div>
                <span v-else class="muted">未关联</span>
              </td>
              <td v-if="columnVisibility.path">
                <code>{{ row.path }}</code>
              </td>
              <td>
                <button
                  class="status-switch"
                  :class="{ enabled: row.status === 'ENABLE' }"
                  type="button"
                  role="switch"
                  :aria-checked="row.status === 'ENABLE'"
                  :aria-label="statusAria(row)"
                  :disabled="!canUpdateMenu || actionLoadingIds.has(row.id)"
                  @click="toggleMenuStatus(row)"
                >
                  <span></span>
                </button>
              </td>
              <td v-if="columnVisibility.updated" class="muted">—</td>
              <td>
                <div class="row-actions">
                  <button v-if="canUpdateMenu" type="button" @click="openUpdateFor(row)">
                    编辑
                  </button>
                  <details>
                    <summary>更多<DownOutlined /></summary>
                    <div>
                      <button v-if="canViewMenuDetail" type="button" @click="openDetailFor(row)">
                        查看详情
                      </button>
                      <button v-if="canDeleteMenu" type="button" @click="openDeleteFor(row)">
                        删除
                      </button>
                    </div>
                  </details>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!loading && !pagedRows.length" class="permission-empty">
          <SearchOutlined />
          <strong>没有找到匹配的权限</strong>
          <span>请尝试调整搜索词或筛选条件</span>
        </div>
      </div>

      <div v-if="selectedIds.size" class="selection-bar">
        <span
          >已选择 <strong>{{ selectedIds.size }}</strong> 项</span
        >
        <button type="button" @click="selectedIds = new Set()">取消选择</button>
      </div>

      <footer class="permission-pagination">
        <span>共 {{ filteredRows.length }} 条</span>
        <div>
          <button type="button" :disabled="pageNo <= 1" @click="pageNo -= 1">‹</button>
          <span class="page-number">{{ pageNo }}</span>
          <span>/ {{ totalPages }}</span>
          <button type="button" :disabled="pageNo >= totalPages" @click="pageNo += 1">›</button>
          <select v-model="pageSize" aria-label="每页条数" @change="pageNo = 1">
            <option :value="20">20 条/页</option>
            <option :value="50">50 条/页</option>
          </select>
          <a-button title="刷新权限清单" aria-label="刷新权限清单" @click="fetchData">
            <template #icon><ReloadOutlined /></template>
          </a-button>
        </div>
      </footer>
    </section>

    <a-modal :open="updateOpen" title="修改菜单" :footer="null" @cancel="closeUpdate">
      <a-form layout="vertical">
        <a-form-item label="目标菜单" required>
          <a-tree-select
            :value="updateTargetId"
            data-testid="update-menu-target"
            :tree-data="detailTreeData"
            :disabled="updating"
            tree-default-expand-all
            placeholder="请选择目录、菜单或按钮"
            @change="selectUpdateTarget"
          />
        </a-form-item>
        <a-alert
          v-if="updateError"
          type="error"
          show-icon
          data-testid="update-menu-error"
          :message="updateError"
        />
        <template v-if="updateReady">
          <a-form-item label="菜单名称" required>
            <a-input v-model:value="updateForm.menuName" data-testid="update-menu-name" />
          </a-form-item>
          <a-form-item label="菜单类型" required>
            <a-select v-model:value="updateForm.menuType" data-testid="update-menu-type">
              <a-select-option value="DIR">目录</a-select-option>
              <a-select-option value="MENU">菜单</a-select-option>
              <a-select-option value="BUTTON">按钮</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="父节点">
            <a-tree-select
              v-model:value="updateForm.parentId"
              data-testid="update-menu-parent"
              :tree-data="updateParentTreeData"
              tree-default-expand-all
            />
          </a-form-item>
          <a-form-item label="路由路径">
            <a-input v-model:value="updateForm.path" data-testid="update-menu-path" />
          </a-form-item>
          <a-form-item label="组件">
            <a-input v-model:value="updateForm.component" data-testid="update-menu-component" />
          </a-form-item>
          <a-form-item label="权限码">
            <a-input v-model:value="updateForm.perms" data-testid="update-menu-perms" />
          </a-form-item>
          <a-form-item label="图标">
            <a-input v-model:value="updateForm.icon" data-testid="update-menu-icon" />
          </a-form-item>
          <a-form-item label="排序">
            <a-input-number
              v-model:value="updateForm.orderNum"
              data-testid="update-menu-order"
              :min="0"
              class="lg-full-control"
            />
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="updateForm.status" data-testid="update-menu-status">
              <a-select-option value="ENABLE">启用</a-select-option>
              <a-select-option value="DISABLE">停用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="可见性">
            <a-select v-model:value="updateForm.visible" data-testid="update-menu-visible">
              <a-select-option :value="1">可见</a-select-option>
              <a-select-option :value="0">隐藏</a-select-option>
            </a-select>
          </a-form-item>
          <div class="lg-form-actions">
            <a-button :disabled="updating" @click="closeUpdate">取消</a-button>
            <a-button
              type="primary"
              :loading="updating"
              data-testid="update-menu-submit"
              @click="submitUpdate"
            >
              保存修改
            </a-button>
          </div>
        </template>
        <div v-else-if="updateTargetId && !updateError" class="lg-empty-text">
          正在加载菜单详情…
        </div>
        <div v-else-if="!updateTargetId" class="lg-empty-text">请选择要修改的菜单</div>
      </a-form>
    </a-modal>

    <a-modal :open="deleteOpen" title="删除菜单" :footer="null" @cancel="closeDelete">
      <a-form layout="vertical">
        <a-form-item label="目标菜单" required>
          <a-tree-select
            v-model:value="deleteTargetId"
            data-testid="delete-menu-target"
            :tree-data="deleteTreeData"
            tree-default-expand-all
            placeholder="请选择目录、菜单或按钮"
          />
        </a-form-item>
        <a-alert type="warning" show-icon>
          <template #message>删除操作不可逆</template>
          <template #description>
            <span data-testid="delete-menu-warning">
              将删除“{{
                selectedDeleteMenu?.menuName ?? '未选择'
              }}”。有子节点或角色引用时后端会拒绝删除；此处不提供级联删除或强制解绑。
            </span>
          </template>
        </a-alert>
        <div class="lg-form-actions">
          <a-button :disabled="deleting" @click="closeDelete">取消</a-button>
          <a-button
            danger
            type="primary"
            :loading="deleting"
            data-testid="delete-menu-submit"
            @click="submitDelete"
          >
            确认删除
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal :open="detailOpen" title="菜单详情" :footer="null" @cancel="closeDetail">
      <a-form layout="vertical">
        <a-form-item label="目标菜单" required>
          <a-tree-select
            :value="detailTargetId"
            data-testid="detail-menu-target"
            :tree-data="detailTreeData"
            tree-default-expand-all
            placeholder="请选择目录、菜单或按钮"
            @change="selectDetailTarget"
          />
        </a-form-item>
      </a-form>

      <div v-if="detailLoading" data-testid="detail-menu-loading" class="lg-empty-text">
        正在加载菜单详情…
      </div>
      <a-alert
        v-else-if="detailError"
        type="error"
        show-icon
        data-testid="detail-menu-error"
        :message="detailError"
      />
      <dl v-else-if="menuDetail" data-testid="detail-menu-content" class="lg-detail-list">
        <div>
          <dt>名称</dt>
          <dd data-testid="detail-menu-name">{{ displayValue(menuDetail.menuName) }}</dd>
        </div>
        <div>
          <dt>类型</dt>
          <dd>{{ menuTypeLabel(menuDetail.menuType) }}</dd>
        </div>
        <div>
          <dt>父节点</dt>
          <dd>{{ displayValue(menuDetail.parentId) }}</dd>
        </div>
        <div>
          <dt>路径</dt>
          <dd>{{ displayValue(menuDetail.path) }}</dd>
        </div>
        <div>
          <dt>组件</dt>
          <dd>{{ displayValue(menuDetail.component) }}</dd>
        </div>
        <div>
          <dt>权限码</dt>
          <dd>{{ displayValue(menuDetail.perms) }}</dd>
        </div>
        <div>
          <dt>图标</dt>
          <dd>{{ displayValue(menuDetail.icon) }}</dd>
        </div>
        <div>
          <dt>排序</dt>
          <dd>{{ displayValue(menuDetail.orderNum) }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ statusLabel(menuDetail.status) }}</dd>
        </div>
        <div>
          <dt>可见性</dt>
          <dd>{{ visibleLabel(menuDetail.visible) }}</dd>
        </div>
      </dl>
      <div v-else class="lg-empty-text">请选择一个菜单节点查看详情</div>
    </a-modal>

    <a-modal
      :open="listOpen"
      title="菜单列表"
      :footer="null"
      width="1120px"
      @cancel="closeMenuList"
    >
      <div v-if="listLoading" data-testid="menu-list-loading" class="lg-empty-text">
        正在加载菜单列表…
      </div>
      <a-alert
        v-else-if="listError"
        type="error"
        show-icon
        data-testid="menu-list-error"
        :message="listError"
      />
      <div v-else-if="flatMenus.length" class="lg-menu-list-wrap">
        <table data-testid="menu-list-table" class="lg-menu-list-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>类型</th>
              <th>父节点</th>
              <th>路径</th>
              <th>权限码</th>
              <th>排序</th>
              <th>状态</th>
              <th>可见性</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="menu in flatMenus" :key="String(menu.id)" data-testid="menu-list-row">
              <td>{{ displayValue(menu.menuName) }}</td>
              <td>{{ menuTypeLabel(menu.menuType) }}</td>
              <td>{{ displayValue(menu.parentId) }}</td>
              <td>{{ displayValue(menu.path) }}</td>
              <td>{{ displayValue(menu.perms) }}</td>
              <td>{{ displayValue(menu.orderNum) }}</td>
              <td>{{ statusLabel(menu.status) }}</td>
              <td>{{ visibleLabel(menu.visible) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else data-testid="menu-list-empty" class="lg-empty-text">暂无菜单</div>
    </a-modal>
  </div>
</template>

<style scoped>
.permission-page {
  min-height: calc(100vh - 56px);
  padding: 22px 24px 28px;
  color: #172033;
  background: #f5f7fb;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.permission-breadcrumb {
  margin-bottom: 12px;
  color: #7c8798;
  font-size: 13px;
}

.permission-page :deep(.ant-btn) {
  height: 40px;
  border-radius: 7px;
  box-shadow: none;
}

.permission-page :deep(.ant-btn-primary) {
  border-color: #1677ff;
  background: #1677ff;
  box-shadow: 0 5px 12px rgba(22, 119, 255, 0.16);
}

.permission-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  min-height: 112px;
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid #dfe5ed;
  border-radius: 10px;
  background: #fff;
}

.summary-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  min-width: 0;
  padding: 18px 20px;
}

.summary-item:not(:last-child)::after {
  position: absolute;
  top: 29px;
  right: 0;
  width: 1px;
  height: 54px;
  background: #dfe5ed;
  content: '';
}

.summary-icon {
  flex: 0 0 auto;
  font-size: 46px;
}

.summary-blue .summary-icon {
  color: #1677ff;
}

.summary-green .summary-icon {
  color: #039855;
}

.summary-amber .summary-icon {
  color: #f79009;
}

.summary-item strong,
.summary-item span {
  display: block;
}

.summary-item strong {
  color: #101828;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
}

.summary-item div span {
  margin-top: 6px;
  color: #667085;
  font-size: 14px;
}

.permission-filters {
  display: grid;
  grid-template-columns: minmax(260px, 1.5fr) repeat(4, minmax(130px, 1fr)) auto auto;
  gap: 12px;
  margin-bottom: 16px;
}

.permission-filters select,
.search-control {
  width: 100%;
  height: 40px;
  border: 1px solid #d6dee9;
  border-radius: 7px;
  background: #fff;
}

.permission-filters select {
  min-width: 0;
  padding: 0 34px 0 13px;
  color: #344054;
  outline: none;
}

.permission-filters select:focus,
.search-control:focus-within {
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.08);
}

.search-control {
  display: flex;
  align-items: center;
  color: #7c8798;
}

.search-control > :first-child {
  margin-left: 12px;
  font-size: 17px;
}

.search-control input {
  min-width: 0;
  flex: 1;
  height: 100%;
  padding: 0 12px;
  border: 0;
  outline: 0;
  color: #344054;
  background: transparent;
}

.search-control input::placeholder {
  color: #98a2b3;
}

.permission-table-card {
  min-height: 488px;
  overflow: visible;
  border: 1px solid #dfe5ed;
  border-radius: 10px;
  background: #fff;
}

.permission-table-head {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  min-height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid #dfe5ed;
}

.permission-tabs {
  display: flex;
  align-items: stretch;
  gap: 28px;
}

.permission-tabs button {
  position: relative;
  padding: 0 2px;
  border: 0;
  color: #475467;
  background: transparent;
  font-weight: 500;
  white-space: nowrap;
}

.permission-tabs button span {
  margin-left: 4px;
}

.permission-tabs button.active {
  color: #1677ff;
  font-weight: 650;
}

.permission-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: #1677ff;
  content: '';
}

.permission-table-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.permission-table-actions :deep(.ant-btn) {
  height: 36px;
  padding: 0 13px;
}

.column-settings {
  position: relative;
}

.column-settings > summary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 36px;
  padding: 0 13px;
  border: 1px solid #d6dee9;
  border-radius: 7px;
  color: #344054;
  background: #fff;
  cursor: pointer;
  list-style: none;
  white-space: nowrap;
}

.column-settings > summary::-webkit-details-marker,
.row-actions summary::-webkit-details-marker {
  display: none;
}

.column-settings[open] > summary {
  border-color: #1677ff;
  color: #1677ff;
}

.column-settings-menu {
  position: absolute;
  top: 42px;
  right: 0;
  z-index: 20;
  display: grid;
  width: 180px;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e1e7ef;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 30px rgba(16, 24, 40, 0.14);
}

.column-settings:not([open]) .column-settings-menu {
  display: none;
}

.column-settings-menu label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #475467;
  font-size: 13px;
}

.column-settings-menu :deep(.ant-btn) {
  width: 100%;
  justify-content: flex-start;
}

.column-settings-divider {
  height: 1px;
  background: #e8edf3;
}

.permission-table-scroll {
  position: relative;
  height: min(500px, calc(100vh - 460px));
  min-height: 346px;
  overflow: auto;
}

.permission-table-scroll.loading {
  opacity: 0.58;
  pointer-events: none;
}

.permission-table {
  width: 100%;
  min-width: 1160px;
  border-collapse: collapse;
  table-layout: fixed;
}

.permission-table th {
  position: sticky;
  top: 0;
  z-index: 4;
  height: 50px;
  border-bottom: 1px solid #dfe5ed;
  color: #475467;
  background: #fafbfd;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
}

.permission-table td {
  height: 50px;
  padding: 0 10px 0 0;
  overflow: hidden;
  border-bottom: 1px solid #e8edf3;
  color: #344054;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-table tbody tr:hover {
  background: #f7faff;
}

.permission-table tbody tr.selected {
  background: #edf5ff;
}

.permission-table th:nth-child(1),
.permission-table td:nth-child(1) {
  width: 52px;
}

.permission-table th:nth-child(2) {
  width: 190px;
}

.permission-table th:nth-child(3) {
  width: 145px;
}

.permission-table th:nth-child(4) {
  width: 125px;
}

.permission-table th:nth-child(5) {
  width: 76px;
}

.permission-table th:last-child {
  width: 110px;
}

.check-cell {
  padding: 0 !important;
  text-align: center !important;
}

.check-cell input,
.column-settings-menu input {
  width: 15px;
  height: 15px;
  accent-color: #1677ff;
}

.permission-name {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

.permission-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.tree-toggle {
  display: grid;
  width: 20px;
  height: 28px;
  flex: 0 0 20px;
  place-items: center;
  padding: 0;
  border: 0;
  color: #667085;
  background: transparent;
}

.tree-toggle > :first-child {
  font-size: 12px;
  transition: transform 0.2s ease;
}

.tree-toggle > .collapsed {
  transform: rotate(-90deg);
}

.tree-spacer {
  width: 20px;
  flex: 0 0 20px;
}

.row-icon {
  flex: 0 0 auto;
  color: #526174;
  font-size: 17px;
}

.permission-table code {
  overflow: hidden;
  color: #475467;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
}

.permission-type {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 5px;
  font-size: 12px;
}

.permission-type.directory {
  color: #7f56d9;
  background: #f4f0ff;
}

.permission-type.menu {
  color: #1677ff;
  background: #eaf3ff;
}

.permission-type.button {
  color: #07883f;
  background: #eaf8ef;
}

.role-list {
  display: flex;
  align-items: center;
  gap: 5px;
  overflow: hidden;
}

.role-chip {
  max-width: 88px;
  overflow: hidden;
  padding: 2px 8px;
  border-radius: 12px;
  color: #475467;
  background: #f0f3f7;
  font-size: 12px;
  text-overflow: ellipsis;
}

.role-list button,
.selection-bar button,
.row-actions button {
  padding: 0;
  border: 0;
  color: #1677ff;
  background: transparent;
}

.muted {
  color: #98a2b3;
}

.status-switch {
  position: relative;
  width: 36px;
  height: 21px;
  padding: 0;
  border: 0;
  border-radius: 12px;
  background: #cbd5e1;
  transition: background 0.2s ease;
}

.status-switch span {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.22);
  transition: transform 0.2s ease;
}

.status-switch.enabled {
  background: #1677ff;
}

.status-switch.enabled span {
  transform: translateX(15px);
}

.status-switch:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.row-actions details {
  position: relative;
}

.row-actions summary {
  display: flex;
  align-items: center;
  gap: 3px;
  color: #1677ff;
  cursor: pointer;
  font-size: 13px;
  list-style: none;
}

.row-actions summary > :first-child {
  font-size: 10px;
}

.row-actions details > div {
  position: absolute;
  top: 24px;
  right: 0;
  z-index: 12;
  display: grid;
  min-width: 92px;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e1e7ef;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(16, 24, 40, 0.12);
}

.row-actions details:not([open]) > div {
  display: none;
}

.permission-empty {
  display: flex;
  height: 292px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #98a2b3;
}

.permission-empty > :first-child {
  font-size: 40px;
}

.permission-empty strong {
  color: #475467;
}

.selection-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 48px;
  padding: 0 20px;
  border-top: 1px solid #dfe5ed;
  color: #475467;
  background: #fbfdff;
}

.permission-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  padding: 0 20px;
  border-top: 1px solid #dfe5ed;
  color: #667085;
}

.permission-pagination > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.permission-pagination button,
.permission-pagination select {
  height: 34px;
  border: 1px solid #d6dee9;
  border-radius: 6px;
  color: #475467;
  background: #fff;
}

.permission-pagination button {
  min-width: 34px;
}

.permission-pagination button:disabled {
  opacity: 0.45;
}

.permission-pagination .page-number {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid #1677ff;
  border-radius: 6px;
  color: #1677ff;
  background: #edf5ff;
}

.permission-pagination select {
  padding: 0 8px;
}

.permission-pagination :deep(.ant-btn) {
  width: 34px;
  min-width: 34px;
  height: 34px;
  padding: 0;
}

.lg-detail-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 20px;
  margin: 0;
}

.lg-detail-list > div {
  min-width: 0;
}

.lg-detail-list dt {
  color: var(--lg-text-secondary, #6b7280);
  font-size: 12px;
}

.lg-detail-list dd {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
}

.lg-menu-list-wrap {
  max-height: 60vh;
  overflow: auto;
}

.lg-menu-list-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.lg-menu-list-table th,
.lg-menu-list-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--lg-border-color, #e5e7eb);
  text-align: left;
  white-space: nowrap;
}

.lg-menu-list-table th {
  color: var(--lg-text-secondary, #6b7280);
  font-weight: 600;
  background: var(--lg-fill-secondary, #f9fafb);
}

@media (max-width: 1200px) {
  .permission-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-item:nth-child(2)::after {
    display: none;
  }

  .permission-filters {
    grid-template-columns: minmax(260px, 2fr) repeat(2, minmax(150px, 1fr));
  }

  .permission-table-head {
    flex-wrap: wrap;
    gap: 10px;
    padding-top: 10px;
    padding-bottom: 10px;
  }
}

@media (max-width: 850px) {
  .permission-page {
    padding: 18px;
  }

  .permission-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-control {
    grid-column: 1 / -1;
  }

  .permission-tabs {
    width: 100%;
    overflow-x: auto;
  }

  .permission-table-actions {
    width: 100%;
    overflow-x: auto;
  }
}

@media (max-width: 560px) {
  .permission-page {
    padding: 14px;
  }

  .permission-summary,
  .permission-filters {
    grid-template-columns: 1fr;
  }

  .summary-item:not(:last-child)::after {
    top: auto;
    right: 10%;
    bottom: 0;
    width: 80%;
    height: 1px;
  }

  .permission-tabs {
    gap: 18px;
  }
}
</style>
