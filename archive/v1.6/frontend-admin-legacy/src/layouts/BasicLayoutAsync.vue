<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'
import {
  CalendarOutlined,
  DownOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  ProjectOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { getUserInfo } from '@/api/modules/auth'
import { useMobileViewport } from '@/composables/useMobileViewport'
import SidebarMenu from './components/SidebarMenu.vue'
import ObjectContextNavigation from './components/ObjectContextNavigation.vue'
import WorkspaceTabs from './components/WorkspaceTabs.vue'
import NotificationBell from '@/components/NotificationBell.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { userInfo } = storeToRefs(userStore)
const collapsed = ref(false)
const globalSearchKeyword = ref('')
const { isMobile, isCompactDesktop } = useMobileViewport()
const bellReady = ref(false)
const mobilePageTitle = computed(() => String(route.meta.title || 'CGC-PMS'))
const isDashboard = computed(() => route.path === '/dashboard')
const currentProjectName = computed(() => String(route.meta.title || '项目工作台'))
const reportPeriod = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}年${now.getMonth() + 1}月`
})

watch([isMobile, isCompactDesktop], ([mobile, compactDesktop]) => {
  if (mobile || compactDesktop) {
    collapsed.value = true
  }
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

function openProjectList() {
  router.push('/project/list')
}

function handleGlobalSearch() {
  const keyword = globalSearchKeyword.value.trim()
  router.push({ path: '/dashboard/reports', query: keyword ? { keyword } : undefined })
}

async function refreshUserInfoIfNeeded() {
  if (!userInfo.value?.userId) {
    return
  }
  if (
    userInfo.value.realName &&
    userInfo.value.phone !== undefined &&
    userInfo.value.email !== undefined
  ) {
    return
  }
  try {
    userStore.setUserInfo(await getUserInfo())
  } catch {
    // ignore and keep auth-only state; request interceptor will handle expired sessions
  }
}

onMounted(() => {
  refreshUserInfoIfNeeded()
  setTimeout(() => {
    bellReady.value = true
  }, 500)
})
</script>

<template>
  <a-layout
    class="basic-layout"
    :class="{ 'basic-layout--mobile-nav-open': isMobile && !collapsed }"
  >
    <a-layout-sider
      v-model:collapsed="collapsed"
      width="var(--shell-sidebar-width)"
      :collapsed-width="isMobile ? 0 : 72"
      class="sidebar"
      theme="light"
    >
      <div class="sidebar-shell">
        <div class="brand" :class="{ 'brand--collapsed': collapsed }">
          <ProjectOutlined class="logo" aria-hidden="true" />
          <span v-if="!collapsed" class="brand-text">
            <strong>CGC-PMS</strong>
            <small>建造 · 陪伴 · 成就</small>
          </span>
        </div>
        <SidebarMenu :collapsed="collapsed" />

        <div class="sidebar-footer" :class="{ 'sidebar-footer--collapsed': collapsed }">
          <button
            type="button"
            class="sidebar-tool-button sidebar-toggle"
            :aria-label="collapsed ? '展开菜单' : '折叠菜单'"
            @click="collapsed = !collapsed"
          >
            <MenuFoldOutlined class="sidebar-tool-icon" aria-hidden="true" />
            <span v-if="!collapsed">折叠菜单</span>
          </button>
        </div>
      </div>
    </a-layout-sider>
    <div
      v-if="isMobile && !collapsed"
      class="sidebar-mask"
      aria-hidden="true"
      @click="collapsed = true"
    ></div>

    <a-layout class="workspace-layout">
      <header v-if="!isMobile" class="global-topbar">
        <button
          type="button"
          class="topbar-context topbar-context--project"
          @click="openProjectList"
        >
          <span>当前项目</span>
          <strong>{{ currentProjectName }}</strong>
          <DownOutlined aria-hidden="true" />
        </button>
        <div class="topbar-context topbar-context--period">
          <span>报告期</span>
          <strong>{{ reportPeriod }}</strong>
          <CalendarOutlined aria-hidden="true" />
        </div>
        <form class="global-search" role="search" @submit.prevent="handleGlobalSearch">
          <a-input
            v-model:value="globalSearchKeyword"
            aria-label="全局搜索"
            placeholder="全局搜索（合同/项目/单据/功能）"
          >
            <template #suffix><SearchOutlined aria-hidden="true" /></template>
          </a-input>
        </form>
        <div class="global-topbar__tools" aria-label="全局工具栏">
          <button type="button" class="global-tool-button" @click="router.push('/approval/todo')">
            我的待办
          </button>
          <span v-if="bellReady" class="global-notification" aria-label="通知中心">
            <NotificationBell label="通知中心" placement="bottomRight" />
          </span>
          <a-dropdown :trigger="['click']">
            <div class="global-user" aria-label="打开个人账户菜单">
              <a-avatar :size="32" class="user-avatar">
                {{ userInfo?.realName?.[0] || '●' }}
              </a-avatar>
              <div class="user-text">
                <div class="username">{{ userInfo?.realName || '张三' }}</div>
                <div class="role">{{ userInfo?.roleName || '项目经理' }}</div>
              </div>
            </div>
            <template #overlay>
              <a-menu>
                <a-menu-item key="profile" @click="router.push('/profile')">个人资料</a-menu-item>
                <a-menu-item key="settings" @click="router.push('/settings')">偏好设置</a-menu-item>
                <a-menu-item key="help" @click="router.push('/help')">帮助与支持</a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </header>
      <header v-if="isMobile" class="mobile-topbar">
        <button
          type="button"
          class="mobile-topbar-button"
          aria-label="打开导航菜单"
          @click="collapsed = false"
        >
          <MenuOutlined aria-hidden="true" />
        </button>
        <div class="mobile-topbar-title">{{ mobilePageTitle }}</div>
        <button
          type="button"
          class="mobile-profile-button"
          aria-label="打开个人中心"
          @click="router.push('/profile')"
        >
          <a-avatar :size="30" class="user-avatar">
            {{ userInfo?.realName?.[0] || '●' }}
          </a-avatar>
        </button>
      </header>
      <WorkspaceTabs v-if="!isDashboard" />
      <ObjectContextNavigation v-if="!isDashboard" />
      <a-layout-content class="main-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: var(--shell-bg);
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 20;
  overflow: hidden;
  background: var(--shell-sidebar-bg) !important;
  border-right: 1px solid var(--border);
  backdrop-filter: blur(12px);
}

.sidebar-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.brand {
  height: var(--shell-header-height);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

.brand--collapsed {
  justify-content: center;
  padding: 0;
}

.logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  color: var(--brand-logo-fg);
  font-size: 19px;
  background: var(--brand-gradient-logo);
  flex-shrink: 0;
}

.brand-text {
  min-width: 0;
  display: grid;
  gap: 1px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-text strong {
  color: var(--primary);
  font-size: 20px;
  font-weight: 800;
  line-height: 22px;
}

.brand-text small {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 500;
  line-height: 14px;
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  padding: 12px;
  border-top: 1px solid var(--border);
}

.sidebar-footer--collapsed {
  padding: 12px 8px;
}

.sidebar-tool-button {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 40px;
  color: var(--text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
}

.sidebar-tool-button {
  justify-content: flex-start;
  gap: 16px;
  padding: 0 10px;
  font-size: 13px;
  cursor: pointer;
  transition:
    background 0.16s ease,
    border-color 0.16s ease,
    color 0.16s ease;
}

.sidebar-tool-button:hover {
  color: var(--primary);
  background: var(--surface-tint);
  border-color: var(--border);
}

.sidebar-tool-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.sidebar-footer--collapsed .sidebar-tool-button {
  justify-content: center;
  padding: 0;
}

.user-avatar {
  background: var(--brand-gradient-avatar);
}

.user-text {
  line-height: 1.3;
}

.username {
  font-size: 13px;
  font-weight: 800;
  color: var(--text);
}

.role {
  margin-top: 1px;
  font-size: 11px;
  color: var(--muted);
}

.global-topbar {
  position: sticky;
  top: 0;
  z-index: 13;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  min-height: var(--shell-header-height);
  padding: 0;
  background: color-mix(in srgb, var(--surface) 94%, transparent);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(12px);
}

.topbar-context {
  position: relative;
  align-self: stretch;
  min-width: 0;
  padding: 12px 44px 10px 18px;
  background: transparent;
  border: 0;
  border-right: 1px solid var(--border);
  color: var(--text-secondary);
  text-align: left;
}

button.topbar-context {
  cursor: pointer;
}

button.topbar-context:hover {
  background: var(--surface-tint);
}

.topbar-context--project {
  width: 292px;
}

.topbar-context--period {
  width: 264px;
}

.topbar-context span {
  display: block;
  margin-bottom: 3px;
  font-size: 12px;
  line-height: 16px;
}

.topbar-context strong {
  display: block;
  overflow: hidden;
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-context > :last-child {
  position: absolute;
  right: 18px;
  bottom: 19px;
  color: var(--text-secondary);
}

.global-search {
  width: min(280px, 26vw);
  margin: 0 auto;
}

.global-search :deep(.ant-input-affix-wrapper) {
  height: 38px;
  border-color: var(--border);
  border-radius: 7px;
  box-shadow: none;
}

.global-topbar__tools {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 16px;
}

.global-tool-button,
.global-user {
  min-height: 38px;
  color: var(--text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
}

.global-tool-button {
  padding: 0 8px;
  font-size: 13px;
  cursor: pointer;
}

@media (width < 1240px) {
  .topbar-context--project {
    width: 240px;
  }

  .topbar-context--period {
    width: 210px;
  }

  .global-search {
    width: min(220px, 22vw);
  }
}

.global-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 8px 3px 4px;
  cursor: pointer;
}

.global-tool-button:hover,
.global-user:hover {
  color: var(--primary);
  background: var(--surface-tint);
  border-color: var(--border);
}

.global-notification {
  display: inline-flex;
  align-items: center;
}

.main-content {
  padding: 0;
  min-height: 100vh;
  background: transparent;
}

.workspace-layout {
  min-width: 0;
  margin-left: var(--shell-sidebar-width);
}

:deep(.ant-layout-sider-collapsed) + .workspace-layout {
  margin-left: var(--shell-sidebar-collapsed-width);
}

.sidebar-mask {
  display: none;
}

.mobile-topbar {
  display: none;
}

@media (width < 500px) {
  .basic-layout {
    overflow-x: hidden;
  }

  .sidebar {
    z-index: 30;
    transition: width 0.22s ease;
  }

  .basic-layout--mobile-nav-open .sidebar {
    transform: translateX(0);
  }

  .global-topbar {
    display: none;
  }

  .workspace-layout,
  :deep(.ant-layout-sider-collapsed) + .workspace-layout {
    margin-left: 0;
  }

  .main-content {
    width: 100%;
    max-width: 100vw;
    min-height: calc(100vh - 48px);
    padding: 8px;
    overflow-x: hidden;
  }

  .mobile-topbar {
    position: sticky;
    top: 0;
    z-index: 15;
    display: grid;
    grid-template-columns: 40px minmax(0, 1fr) 40px;
    align-items: center;
    height: 48px;
    padding: 0 8px;
    background: var(--surface);
    border-bottom: 1px solid var(--border);
  }

  .mobile-topbar-button,
  .mobile-profile-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    padding: 0;
    color: var(--text);
    background: transparent;
    border: 0;
    border-radius: var(--radius-md);
    cursor: pointer;
  }

  .mobile-topbar-title {
    min-width: 0;
    overflow: hidden;
    color: var(--text);
    font-size: 16px;
    font-weight: 700;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .user-text {
    display: none;
  }

  .sidebar-mask {
    position: fixed;
    inset: 0;
    z-index: 25;
    display: block;
    background: var(--shell-mask-bg);
  }
}
</style>
