<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import axios from 'axios'
import { getMenuTree, getRoles } from '@/api/modules/system'
import type { MenuTreeVO, SysRoleVO } from '@/types/system'

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

async function fetchData() {
  loading.value = true
  try {
    const [nextMenus, nextRoles] = await Promise.all([getMenuTree(), getRoles()])
    menuTree.value = nextMenus
    roles.value = nextRoles
  } catch (e: unknown) {
    console.error(e)
    const msg = axios.isAxiosError(e)
      ? (e.response?.data as { message?: string })?.message || e.message
      : e instanceof Error
        ? e.message
        : ''
    message.error(msg || '加载权限清单失败')
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
  </div>
</template>
