<script setup lang="ts">
import { computed, h, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { navigationItems, type NavigationItem } from '@/router/navigation'
import { useUserStore } from '@/stores/user'
import {
  AccountBookOutlined,
  AuditOutlined,
  BranchesOutlined,
  DollarOutlined,
  FileTextOutlined,
  HomeOutlined,
  ProjectOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
} from '@ant-design/icons-vue'

interface MenuItem {
  key: string
  label: string
  icon?: () => ReturnType<typeof h>
  children?: MenuItem[]
}

const iconMap: Record<string, Component> = {
  AccountBookOutlined,
  AuditOutlined,
  BranchesOutlined,
  DollarOutlined,
  FileTextOutlined,
  HomeOutlined,
  ProjectOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const props = defineProps<{
  collapsed?: boolean
}>()

const isAdmin = computed(() => {
  return userStore.roles.includes('ADMIN') || userStore.roles.includes('SUPER_ADMIN')
})

function isMenuVisible(item: Pick<NavigationItem, 'adminOnly'>) {
  if (item.adminOnly && userStore.roles.length > 0 && !isAdmin.value) return false
  return true
}

function buildMenuItem(navigation: NavigationItem): MenuItem | undefined {
  if (!isMenuVisible(navigation)) return undefined

  const iconName = navigation.icon
  const item: MenuItem = {
    key: navigation.key,
    label: navigation.label,
    icon: iconName && iconMap[iconName] ? () => h(iconMap[iconName]) : undefined,
  }

  if (navigation.children?.length) {
    item.children = navigation.children
      .map((child) => buildMenuItem(child))
      .filter(Boolean) as MenuItem[]
  }

  return item.children?.length || !navigation.children?.length ? item : undefined
}

const menuItems = computed(() => {
  return navigationItems.map((item) => buildMenuItem(item)).filter(Boolean) as MenuItem[]
})

const selectedKeys = computed(() => {
  const child = navigationItems
    .flatMap((item) => item.children || [])
    .filter((item) => isMenuVisible(item))
    .find((item) => route.path === item.key || route.path.startsWith(`${item.key}/`))

  if (child) return [child.key]

  const parent = navigationItems.find((item) =>
    item.matchPrefixes?.some(
      (prefix) => route.path === prefix || route.path.startsWith(`${prefix}/`),
    ),
  )
  return parent ? [parent.key] : [route.path]
})

function computeOpenKeys(): string[] {
  const parent = navigationItems.find((item) =>
    item.matchPrefixes?.some(
      (prefix) => route.path === prefix || route.path.startsWith(`${prefix}/`),
    ),
  )
  return parent ? [parent.key] : []
}

const openKeys = ref<string[]>(computeOpenKeys())

watch(
  () => route.path,
  () => {
    openKeys.value = computeOpenKeys()
  },
  { immediate: true },
)

function handleMenuClick({ key }: { key: string }) {
  const target = String(key)
  if (target.startsWith('/')) {
    router.push(target)
  }
}

function handleSectionClick(item: MenuItem) {
  const target = item.children?.[0]?.key || item.key
  if (target.startsWith('/')) {
    router.push(target)
  }
}
</script>

<template>
  <nav v-if="props.collapsed" class="sidebar-menu sidebar-menu--collapsed" aria-label="主导航菜单">
    <button
      v-for="item in menuItems"
      :key="item.key"
      type="button"
      class="collapsed-menu-item"
      :class="{ 'collapsed-menu-item--active': openKeys.includes(item.key) }"
      :title="item.label"
      @click="handleSectionClick(item)"
    >
      <component :is="item.icon" v-if="item.icon" class="collapsed-menu-icon" />
    </button>
  </nav>
  <a-menu
    v-else
    v-model:open-keys="openKeys"
    :selected-keys="selectedKeys"
    :items="menuItems"
    mode="inline"
    class="sidebar-menu"
    tabindex="0"
    role="menu"
    aria-label="主导航菜单"
    @click="handleMenuClick"
  />
</template>

<style scoped>
.sidebar-menu {
  height: calc(100vh - var(--shell-header-height));
  padding: 14px 10px 18px;
  overflow-y: auto;
  background: transparent;
  border-right: 0;
}

.sidebar-menu--collapsed {
  display: flex;
  align-items: stretch;
  flex-direction: column;
  width: var(--shell-sidebar-collapsed-width);
}

.collapsed-menu-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 42px;
  margin: 3px auto;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background 0.16s ease,
    color 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}

.collapsed-menu-item:hover {
  background: var(--surface-tint);
  color: var(--primary);
}

.collapsed-menu-item--active {
  background: var(--primary-soft);
  color: var(--primary);
  border-color: rgba(37, 99, 235, 0.14);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.08);
}

.collapsed-menu-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
}

:deep(.ant-menu) {
  color: var(--text);
  background: transparent;
}

:deep(.ant-menu-submenu-title),
:deep(.ant-menu-item) {
  height: 40px;
  margin: 3px 0;
  padding-inline: 14px !important;
  border-radius: 10px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
  line-height: 40px;
  transition:
    background 0.16s ease,
    color 0.16s ease;
}

:deep(.ant-menu-submenu-title:hover),
:deep(.ant-menu-item:hover) {
  background: #f4f8ff !important;
  color: var(--primary) !important;
}

:deep(.ant-menu-submenu-selected > .ant-menu-submenu-title),
:deep(.ant-menu-item-selected) {
  background: var(--primary-soft) !important;
  color: var(--primary) !important;
  font-weight: 800;
}

:deep(.ant-menu-item-selected) {
  box-shadow: inset 3px 0 0 var(--primary);
}

:deep(.ant-menu-item .ant-menu-title-content),
:deep(.ant-menu-submenu-title .ant-menu-title-content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.ant-menu-sub .ant-menu-item) {
  height: 34px;
  margin: 0;
  padding-left: 42px !important;
  font-size: 13px;
  font-weight: 600;
  line-height: 34px;
}

:deep(.ant-menu-inline-collapsed) {
  width: var(--shell-sidebar-collapsed-width);
}

:global(.ant-layout-sider-collapsed) .sidebar-menu,
:deep(.ant-menu-inline-collapsed) {
  padding-top: 16px;
}

:global(.ant-layout-sider-collapsed) .sidebar-menu :deep(.ant-menu-submenu-title),
:global(.ant-layout-sider-collapsed) .sidebar-menu :deep(.ant-menu-item),
:deep(.ant-menu-inline-collapsed > .ant-menu-submenu > .ant-menu-submenu-title),
:deep(.ant-menu-inline-collapsed > .ant-menu-item) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-inline: 0 !important;
}

:global(.ant-layout-sider-collapsed) .sidebar-menu :deep(.ant-menu-title-content),
:global(.ant-layout-sider-collapsed) .sidebar-menu :deep(.ant-menu-submenu-arrow),
:deep(.ant-menu-inline-collapsed .ant-menu-title-content),
:deep(.ant-menu-inline-collapsed .ant-menu-submenu-arrow) {
  display: none;
}

:global(.ant-layout-sider-collapsed) .sidebar-menu :deep(.ant-menu-item-icon),
:global(.ant-layout-sider-collapsed) .sidebar-menu :deep(.anticon),
:deep(.ant-menu-inline-collapsed .ant-menu-item-icon),
:deep(.ant-menu-inline-collapsed .anticon) {
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  margin-inline-end: 0;
  font-size: 17px;
  opacity: 1;
  visibility: visible;
}
</style>
