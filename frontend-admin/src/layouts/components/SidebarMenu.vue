<script setup lang="ts">
import { computed, h, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
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

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

interface MenuItem {
  key: string
  label: string
  icon?: () => ReturnType<typeof h>
  children?: MenuItem[]
}

const iconMap: Record<string, MenuItem['icon']> = {
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

const menuItems = computed(() => {
  return navigationItems
    .filter((item) => isMenuVisible(item))
    .map((item) => buildMenuItem(item))
})

const isAdmin = computed(() => {
  return userStore.roles.includes('ADMIN') || userStore.roles.includes('SUPER_ADMIN')
})

function buildMenuItem(navigation: NavigationItem): MenuItem {
  const item: MenuItem = {
    key: navigation.key,
    label: navigation.label,
  }
  const iconName = navigation.icon
  if (iconName && iconMap[iconName]) {
    item.icon = () => h(iconMap[iconName])
  }
  if (navigation.children && navigation.children.length > 0) {
    item.children = navigation.children
      .filter((child) => isMenuVisible(child))
      .map((child) => buildMenuItem(child))
  }
  return item
}

function isMenuVisible(item: NavigationItem) {
  if (item.adminOnly && !isAdmin.value) return false
  return true
}

const selectedKeys = computed(() => {
  return [route.path]
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
  router.push(key)
}

let menuObserver: MutationObserver | null = null

function ensureMenuRole() {
  const menuUl = document.querySelector('.sidebar-menu')
  if (menuUl && !menuUl.hasAttribute('role')) {
    menuUl.setAttribute('role', 'menu')
  }
}

onMounted(() => {
  nextTick(() => {
    ensureMenuRole()
    menuObserver = new MutationObserver(() => ensureMenuRole())
    menuObserver.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class'],
    })
  })
})

onBeforeUnmount(() => {
  menuObserver?.disconnect()
})
</script>

<template>
  <a-menu
    :selected-keys="selectedKeys"
    v-model:open-keys="openKeys"
    mode="inline"
    class="sidebar-menu"
    :items="menuItems"
    tabindex="0"
    role="menu"
    aria-label="主导航菜单"
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
