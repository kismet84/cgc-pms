<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import axios from 'axios'
import {
  createMenu,
  deleteMenu,
  getMenuDetail,
  getMenuList,
  getMenuTree,
  getRoles,
} from '@/api/modules/system'
import { useUserStore } from '@/stores/user'
import type { CreateMenuPayload, MenuTreeVO, SysMenuVO, SysRoleVO } from '@/types/system'

interface PermissionRow {
  id: string
  permissionCode: string
  menuName: string
  path: string
  sourceRemark: string
  bindingStatus: string
}

const loading = ref(false)
const menuTree = ref<MenuTreeVO[]>([])
const roles = ref<SysRoleVO[]>([])
const userStore = useUserStore()
const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive<CreateMenuPayload>(defaultCreateForm())
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

const isAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canCreateMenu = computed(() => isAdmin.value)
const canDeleteMenu = computed(() => isAdmin.value)
const canViewMenuDetail = computed(() => isAdmin.value)
const canViewMenuList = computed(() => isAdmin.value)
const parentTreeData = computed(() => [
  {
    title: '根节点',
    value: 0,
    children: toParentTreeData(menuTree.value),
  },
])
const deleteTreeData = computed(() => toDeleteTreeData(menuTree.value))
const detailTreeData = computed(() => toDeleteTreeData(menuTree.value))
const selectedDeleteMenu = computed(() => findMenu(menuTree.value, deleteTargetId.value))

const rows = computed(() => flattenPermissions(menuTree.value, boundMenuIds.value))
const boundMenuIds = computed(() => {
  const ids = new Set<string>()
  for (const role of roles.value) {
    for (const id of role.menuIds ?? []) ids.add(String(id))
  }
  return ids
})

const columns = [
  { field: 'permissionCode', title: '权限码', minWidth: 220 },
  { field: 'menuName', title: '菜单名称', width: 160 },
  { field: 'path', title: '路径', minWidth: 180 },
  { field: 'sourceRemark', title: '来源/备注', width: 160 },
  { field: 'bindingStatus', title: '角色绑定', width: 100, slots: { default: 'bindingStatus' } },
]

function flattenPermissions(nodes: MenuTreeVO[], boundMenuIds: Set<string>): PermissionRow[] {
  const result: PermissionRow[] = []
  for (const menu of nodes) {
    const permissionCode = String(menu.perms ?? '').trim()
    if (permissionCode) {
      result.push({
        id: String(menu.id),
        permissionCode,
        menuName: menu.menuName || '-',
        path: menu.path || '-',
        sourceRemark: '/system/menus/tree',
        bindingStatus: boundMenuIds.has(String(menu.id)) ? '已绑定' : '未绑定',
      })
    }
    if (menu.children?.length) {
      result.push(...flattenPermissions(menu.children, boundMenuIds))
    }
  }
  return result
}

function defaultCreateForm(): CreateMenuPayload {
  return {
    parentId: 0,
    menuName: '',
    menuType: 'MENU',
    path: '',
    component: '',
    perms: '',
    icon: '',
    orderNum: 0,
  }
}

function toParentTreeData(nodes: MenuTreeVO[]): Array<{
  title: string
  value: number | string
  children?: ReturnType<typeof toParentTreeData>
}> {
  return nodes
    .filter((node) => node.menuType !== 'BUTTON')
    .map((node) => ({
      title: node.menuName,
      value: node.id,
      children: toParentTreeData(node.children ?? []),
    }))
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

function findMenu(nodes: MenuTreeVO[], id?: number | string): MenuTreeVO | undefined {
  if (id === undefined || id === null || id === '') return undefined
  for (const node of nodes) {
    if (String(node.id) === String(id)) return node
    const child = findMenu(node.children ?? [], id)
    if (child) return child
  }
  return undefined
}

function openCreate() {
  Object.assign(createForm, defaultCreateForm())
  createOpen.value = true
}

function closeCreate() {
  if (!creating.value) createOpen.value = false
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

async function submitCreate() {
  const menuName = createForm.menuName.trim()
  if (!menuName || !createForm.menuType) {
    message.error('请填写菜单名称和菜单类型')
    return
  }

  creating.value = true
  try {
    const payload: CreateMenuPayload = {
      parentId: createForm.parentId,
      menuName,
      menuType: createForm.menuType,
      orderNum: createForm.orderNum ?? 0,
    }
    const path = createForm.path?.trim()
    const component = createForm.component?.trim()
    const perms = createForm.perms?.trim()
    const icon = createForm.icon?.trim()
    if (path) payload.path = path
    if (component) payload.component = component
    if (perms) payload.perms = perms
    if (icon) payload.icon = icon

    await createMenu(payload)
    message.success('菜单创建成功')
    createOpen.value = false
    await fetchData()
  } catch (error: unknown) {
    console.error(error)
    message.error(errorMessage(error, '菜单创建失败'))
  } finally {
    creating.value = false
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
    const [nextMenus, nextRoles] = await loadData()
    if (findMenu(nextMenus, target.id)) {
      throw new Error('刷新后的菜单树仍包含已删除目标')
    }
    menuTree.value = nextMenus
    roles.value = nextRoles
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
  return Promise.all([getMenuTree(), getRoles()])
}

async function fetchData() {
  loading.value = true
  try {
    const [nextMenus, nextRoles] = await loadData()
    menuTree.value = nextMenus
    roles.value = nextRoles
  } catch (e: unknown) {
    console.error(e)
    message.error(errorMessage(e, '加载权限清单失败'))
    menuTree.value = []
    roles.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-page-head-breadcrumb">
          <a-breadcrumb-item>系统设置</a-breadcrumb-item>
          <a-breadcrumb-item>权限清单</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid">
      <main class="lg-list-table-panel">
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button
              v-if="canCreateMenu"
              type="primary"
              data-testid="create-menu-open"
              @click="openCreate"
            >
              <template #icon><PlusOutlined /></template>
              新建菜单
            </a-button>
            <a-button v-if="canViewMenuDetail" data-testid="detail-menu-open" @click="openDetail">
              <template #icon><EyeOutlined /></template>
              查看详情
            </a-button>
            <a-button v-if="canViewMenuList" data-testid="menu-list-open" @click="openMenuList">
              菜单列表
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
            <a-button title="刷新权限清单" aria-label="刷新权限清单" @click="fetchData">
              <template #icon><ReloadOutlined /></template>
              刷新
            </a-button>
          </div>
        </div>

        <div class="lg-table-wrap">
          <vxe-grid
            :data="rows"
            :columns="columns"
            :loading="loading"
            :column-config="{ resizable: true }"
            stripe
            border="inner"
            size="small"
          >
            <template #bindingStatus="{ row }">
              <a-tag :color="row.bindingStatus === '已绑定' ? 'success' : 'default'">
                {{ row.bindingStatus }}
              </a-tag>
            </template>
            <template #empty>
              <div class="lg-empty-text">暂无权限码</div>
            </template>
          </vxe-grid>
        </div>
      </main>

      <aside class="lg-analysis-rail">
        <div class="lg-panel">
          <div class="lg-panel-title">治理口径</div>
          <div class="lg-rail-list">
            <div class="lg-rail-item">
              <span class="lg-type-dot"></span>
              <span>只读展示 sys_menu.perms</span>
            </div>
            <div class="lg-rail-item">
              <span class="lg-type-dot"></span>
              <span>角色绑定来自角色 menuIds</span>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <a-modal :open="createOpen" title="新建菜单" :footer="null" @cancel="closeCreate">
      <a-form layout="vertical">
        <a-form-item label="菜单名称" required>
          <a-input
            v-model:value="createForm.menuName"
            data-testid="create-menu-name"
            placeholder="请输入菜单名称"
          />
        </a-form-item>
        <a-form-item label="菜单类型" required>
          <a-select v-model:value="createForm.menuType" data-testid="create-menu-type">
            <a-select-option value="DIR">目录</a-select-option>
            <a-select-option value="MENU">菜单</a-select-option>
            <a-select-option value="BUTTON">按钮</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="父节点">
          <a-tree-select
            v-model:value="createForm.parentId"
            data-testid="create-menu-parent"
            :tree-data="parentTreeData"
            tree-default-expand-all
          />
        </a-form-item>
        <a-form-item label="路由路径">
          <a-input v-model:value="createForm.path" data-testid="create-menu-path" />
        </a-form-item>
        <a-form-item label="组件">
          <a-input v-model:value="createForm.component" data-testid="create-menu-component" />
        </a-form-item>
        <a-form-item label="权限码">
          <a-input v-model:value="createForm.perms" data-testid="create-menu-perms" />
        </a-form-item>
        <a-form-item label="图标">
          <a-input v-model:value="createForm.icon" data-testid="create-menu-icon" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number
            v-model:value="createForm.orderNum"
            data-testid="create-menu-order"
            :min="0"
            class="lg-full-control"
          />
        </a-form-item>
        <div class="lg-form-actions">
          <a-button :disabled="creating" @click="closeCreate">取消</a-button>
          <a-button
            type="primary"
            :loading="creating"
            data-testid="create-menu-submit"
            @click="submitCreate"
          >
            创建
          </a-button>
        </div>
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
</style>
