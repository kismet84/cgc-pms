<script setup lang="ts">
import { computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { routes } from '@/router'
import type { RouteRecordRaw } from 'vue-router'
import {
  HomeOutlined,
  FileTextOutlined,
  ProjectOutlined,
  TeamOutlined,
  AuditOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()

const iconMap: Record<string, any> = {
  HomeOutlined,
  FileTextOutlined,
  ProjectOutlined,
  TeamOutlined,
  AuditOutlined,
  SettingOutlined,
}

const menuItems = computed(() => {
  return routes
    .find((r) => r.path === '/')
    ?.children?.filter((r) => !r.meta?.hidden)
    .map((r) => buildMenuItem(r))
})

function buildMenuItem(route: RouteRecordRaw): any {
  const item: any = {
    key: route.path,
    label: route.meta?.title as string,
  }
  const iconName = route.meta?.icon as string
  if (iconName && iconMap[iconName]) {
    item.icon = () => h(iconMap[iconName])
  }
  if (route.children && route.children.length > 0) {
    item.children = route.children.filter((c) => !c.meta?.hidden).map((c) => buildMenuItem(c))
  }
  return item
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
  padding: 14px 8px;
  height: calc(100vh - 56px);
  overflow-y: auto;
}

:deep(.ant-menu-item),
:deep(.ant-menu-submenu-title) {
  border-radius: 8px;
  margin: 4px 0;
  height: 42px;
  line-height: 42px;
}

:deep(.ant-menu-item-selected) {
  background: #f1f6ff !important;
  color: #1677ff !important;
  font-weight: 600;
}

:deep(.ant-menu-item-selected::after) {
  border-right: 3px solid #1677ff;
  border-radius: 0 8px 8px 0;
}
</style>
