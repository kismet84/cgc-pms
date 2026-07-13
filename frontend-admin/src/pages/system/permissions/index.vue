<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import axios from 'axios'
import { createMenu, deleteMenu, getMenuTree, getRoles } from '@/api/modules/system'
import { useUserStore } from '@/stores/user'
import type { CreateMenuPayload, MenuTreeVO, SysRoleVO } from '@/types/system'

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

const isAdmin = computed(() =>
  userStore.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(String(role).toUpperCase())),
)
const canCreateMenu = computed(() => isAdmin.value)
const canDeleteMenu = computed(() => isAdmin.value)
const parentTreeData = computed(() => [
  {
    title: '根节点',
    value: 0,
    children: toParentTreeData(menuTree.value),
  },
])
const deleteTreeData = computed(() => toDeleteTreeData(menuTree.value))
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
              将删除“{{ selectedDeleteMenu?.menuName ?? '未选择' }}”。有子节点或角色引用时后端会拒绝删除；此处不提供级联删除或强制解绑。
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
  </div>
</template>
