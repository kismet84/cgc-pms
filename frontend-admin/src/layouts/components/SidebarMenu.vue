<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { navigationItems } from '@/router/navigation'
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

interface MenuEntry {
  key: string
  label: string
  target: string
  icon?: Component
  matchPrefixes?: string[]
}

interface MenuSection {
  title?: string
  items: MenuEntry[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isAdmin = computed(() => {
  return userStore.roles.includes('ADMIN') || userStore.roles.includes('SUPER_ADMIN')
})

function isMenuVisible(item: { adminOnly?: boolean }) {
  if (item.adminOnly && !isAdmin.value) return false
  return true
}

function findTop(key: string) {
  return navigationItems.find((item) => item.key === key)
}

function childEntry(parentKey: string, childKey: string, icon: Component): MenuEntry | undefined {
  const parent = findTop(parentKey)
  const child = parent?.children?.find((item) => item.key === childKey)
  if (!parent || !child || !isMenuVisible(parent) || !isMenuVisible(child)) return undefined
  return {
    key: child.key,
    target: child.key,
    label: child.label,
    icon,
    matchPrefixes: [child.key],
  }
}

function parentEntry(parentKey: string, icon: Component): MenuEntry | undefined {
  const parent = findTop(parentKey)
  if (!parent || !isMenuVisible(parent)) return undefined
  const target = parent.children?.find((item) => isMenuVisible(item))?.key ?? parent.key
  return {
    key: parent.key,
    target,
    label: parent.label,
    icon,
    matchPrefixes: parent.matchPrefixes,
  }
}

function compact(items: Array<MenuEntry | undefined>) {
  return items.filter(Boolean) as MenuEntry[]
}

const menuSections = computed<MenuSection[]>(() => [
  {
    items: compact([parentEntry('/workbench', HomeOutlined)]),
  },
  {
    title: '项目与主数据',
    items: compact([
      childEntry('/master-data', '/project/list', ProjectOutlined),
      childEntry('/master-data', '/partner', AccountBookOutlined),
      childEntry('/master-data', '/org', BranchesOutlined),
      childEntry('/master-data', '/material/dictionary', FileTextOutlined),
    ]),
  },
  {
    title: '经营与风控',
    items: compact([
      parentEntry('/contract-domain', FileTextOutlined),
      parentEntry('/cost-domain', DollarOutlined),
      parentEntry('/procurement-inventory', ShoppingCartOutlined),
      parentEntry('/subcontract-domain', BranchesOutlined),
      parentEntry('/payment-invoice', AccountBookOutlined),
      parentEntry('/settlement-domain', AccountBookOutlined),
      parentEntry('/approval-center', AuditOutlined),
      parentEntry('/system-management', SettingOutlined),
    ]),
  },
])

function isActive(entry: MenuEntry) {
  return (
    route.path === entry.key ||
    route.path === entry.target ||
    entry.matchPrefixes?.some((prefix) => route.path === prefix || route.path.startsWith(`${prefix}/`))
  )
}

function handleMenuClick(entry: MenuEntry) {
  router.push(entry.target)
}
</script>

<template>
  <nav class="sidebar-menu" tabindex="0" role="menu" aria-label="主导航菜单">
    <template v-for="section in menuSections" :key="section.title || 'root'">
      <div v-if="section.title" class="menu-group">{{ section.title }}</div>
      <button
        v-for="item in section.items"
        :key="item.key"
        type="button"
        class="menu-item"
        :class="{ 'menu-item--active': isActive(item) }"
        role="menuitem"
        @click="handleMenuClick(item)"
      >
        <component :is="item.icon" class="menu-icon" />
        <span class="menu-label">{{ item.label }}</span>
      </button>
    </template>
  </nav>
</template>

<style scoped>
.sidebar-menu {
  height: calc(100vh - var(--shell-header-height));
  padding: 16px 0;
  overflow-y: auto;
  background: transparent;
}

.menu-group {
  padding: 8px 24px;
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.menu-item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 44px;
  padding: 0 24px;
  border: 0;
  border-right: 3px solid transparent;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.2s,
    color 0.2s;
}

.menu-item:hover {
  background: #f5f5f5;
}

.menu-item--active {
  background: #e6f7ff;
  color: var(--primary);
  border-right-color: var(--primary);
}

.menu-icon {
  width: 20px;
  margin-right: 10px;
  color: var(--text-secondary);
  font-size: 15px;
}

.menu-item--active .menu-icon {
  color: var(--primary);
}

:global(.ant-layout-sider-collapsed) .menu-group,
:global(.ant-layout-sider-collapsed) .menu-label {
  display: none;
}

:global(.ant-layout-sider-collapsed) .menu-item {
  justify-content: center;
  padding: 0;
  border-right-color: transparent;
}

:global(.ant-layout-sider-collapsed) .menu-icon {
  margin-right: 0;
  font-size: 17px;
}
</style>
