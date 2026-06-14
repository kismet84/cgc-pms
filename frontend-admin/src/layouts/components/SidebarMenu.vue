<script setup lang="ts">
import { computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { routes } from '@/router'
import type { RouteRecordRaw } from 'vue-router'
import {
  AccountBookOutlined,
  AimOutlined,
  AlertOutlined,
  ApartmentOutlined,
  AuditOutlined,
  BranchesOutlined,
  DatabaseOutlined,
  DollarOutlined,
  FileTextOutlined,
  HomeOutlined,
  InboxOutlined,
  ProjectOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
  SwapOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()

interface MenuItem {
  key: string
  label: string
  icon?: () => ReturnType<typeof h>
  children?: MenuItem[]
}

const iconMap: Record<string, MenuItem['icon']> = {
  AccountBookOutlined,
  AimOutlined,
  AlertOutlined,
  ApartmentOutlined,
  AuditOutlined,
  BranchesOutlined,
  DatabaseOutlined,
  DollarOutlined,
  FileTextOutlined,
  HomeOutlined,
  InboxOutlined,
  ProjectOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
  SwapOutlined,
}

const MENU_ORDER = [
  '/dashboard',
  '/project',
  '/cost-target',
  '/cost',
  '/contract',
  '/variation',
  '/settlement',
  '/payment',
  '/subcontract',
  '/purchase',
  '/inventory',
  '/invoice',
  '/approval',
  '/alert',
  '/material',
  '/org',
  '/system',
]

const menuItems = computed(() => {
  return routes
    .find((r) => r.path === '/')
    ?.children?.filter((r) => !r.meta?.hidden)
    .sort((a, b) => menuRank(resolveMenuPath('', a.path)) - menuRank(resolveMenuPath('', b.path)))
    .map((r) => buildMenuItem(r, ''))
})

function menuRank(path: string) {
  const index = MENU_ORDER.indexOf(path)
  return index === -1 ? MENU_ORDER.length : index
}

function buildMenuItem(route: RouteRecordRaw, parentPath: string): MenuItem {
  const fullPath = resolveMenuPath(parentPath, route.path)
  const item: MenuItem = {
    key: fullPath,
    label: route.meta?.title as string,
  }
  const iconName = route.meta?.icon as string
  if (iconName && iconMap[iconName]) {
    item.icon = () => h(iconMap[iconName])
  }
  if (route.children && route.children.length > 0) {
    item.children = route.children.filter((c) => !c.meta?.hidden).map((c) => buildMenuItem(c, fullPath))
  }
  return item
}

function resolveMenuPath(parentPath: string, routePath: string) {
  if (routePath.startsWith('/')) return routePath
  const normalizedParent = parentPath === '/' ? '' : parentPath
  return `${normalizedParent}/${routePath}`.replace(/\/+/g, '/')
}

const selectedKeys = computed(() => {
  return [route.path]
})

const openKeys = computed(() => {
  const keys: string[] = []
  let parent = route.matched[route.matched.length - 2]
  while (parent) {
    keys.push(parent.path)
    parent = route.matched[route.matched.findIndex((r) => r.path === parent.path) - 1]
  }
  return keys
})

function handleMenuClick({ key }: { key: string }) {
  router.push(key)
}
</script>

<template>
  <a-menu
    v-model:selected-keys="selectedKeys"
    v-model:open-keys="openKeys"
    mode="inline"
    class="sidebar-menu"
    :items="menuItems"
    @click="handleMenuClick"
  />
</template>

<style scoped>
.sidebar-menu {
  border-right: none;
  padding: 10px 10px 14px;
  height: calc(100vh - var(--shell-header-height));
  overflow-y: auto;
  background: transparent;
}

:deep(.ant-menu) {
  color: var(--text-secondary);
  background: transparent;
}

:deep(.ant-menu-item),
:deep(.ant-menu-submenu-title) {
  height: 38px;
  line-height: 38px;
  margin: 3px 0;
  border-radius: var(--radius-md);
}

:deep(.ant-menu-item-selected) {
  background: #edf5ff !important;
  color: var(--primary) !important;
  font-weight: 700;
}

:deep(.ant-menu-item-selected::after) {
  display: none;
}
</style>
