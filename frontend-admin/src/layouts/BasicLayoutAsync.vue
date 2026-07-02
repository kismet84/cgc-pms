<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'
import { MenuFoldOutlined, ProjectOutlined } from '@ant-design/icons-vue'
import { getUserInfo } from '@/api/modules/auth'
import SidebarMenu from './components/SidebarMenu.vue'
import NotificationBell from '@/components/NotificationBell.vue'

const router = useRouter()
const userStore = useUserStore()
const { userInfo } = storeToRefs(userStore)
const collapsed = ref(false)
const isMobile = ref(false)
let mobileQuery: MediaQueryList | undefined
const bellReady = ref(false)

function syncMobileState(event: MediaQueryList | MediaQueryListEvent) {
  isMobile.value = event.matches
  if (event.matches) {
    collapsed.value = true
  }
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

async function refreshUserInfoIfNeeded() {
  if (!userInfo.value?.userId) {
    return
  }
  if (userInfo.value.realName && userInfo.value.phone !== undefined && userInfo.value.email !== undefined) {
    return
  }
  try {
    userStore.setUserInfo(await getUserInfo())
  } catch {
    // ignore and keep auth-only state; request interceptor will handle expired sessions
  }
}

onMounted(() => {
  mobileQuery = window.matchMedia('(max-width: 768px)')
  syncMobileState(mobileQuery)
  mobileQuery.addEventListener('change', syncMobileState)
  refreshUserInfoIfNeeded()
  setTimeout(() => {
    bellReady.value = true
  }, 500)
})

onBeforeUnmount(() => {
  mobileQuery?.removeEventListener('change', syncMobileState)
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
      :collapsed-width="72"
      class="sidebar"
      theme="light"
    >
      <div class="sidebar-shell">
        <div class="brand" :class="{ 'brand--collapsed': collapsed }">
          <ProjectOutlined class="logo" aria-hidden="true" />
          <span v-if="!collapsed" class="brand-text">建筑工程总包项目</span>
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
          <span class="sidebar-tool-button sidebar-bell" :aria-label="bellReady ? '通知' : undefined">
            <NotificationBell
              v-if="bellReady"
              :label="collapsed ? '' : '通知中心'"
              placement="topLeft"
            />
            <span v-if="bellReady && !collapsed" class="sidebar-bell-label sidebar-bell-label--sr"
              >通知中心</span
            >
          </span>
          <a-dropdown>
            <div class="sidebar-user" :class="{ 'sidebar-user--collapsed': collapsed }">
              <a-avatar :size="32" class="user-avatar">
                {{ userInfo?.realName?.[0] || '●' }}
              </a-avatar>
              <div v-if="!collapsed" class="user-text">
                <div class="username">{{ userInfo?.realName || '张三' }}</div>
                <div class="role">{{ userInfo?.roleName || '项目经理' }}</div>
              </div>
            </div>
            <template #overlay>
              <a-menu>
                <a-menu-item key="profile" @click="router.push('/profile')">个人中心</a-menu-item>
                <a-menu-item key="settings" @click="router.push('/settings')">设置</a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </div>
    </a-layout-sider>
    <div
      v-if="isMobile && !collapsed"
      class="sidebar-mask"
      aria-hidden="true"
      @click="collapsed = true"
    ></div>

    <a-layout>
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
  overflow: hidden;
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0;
  text-overflow: ellipsis;
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

.sidebar-tool-button,
.sidebar-user {
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

.sidebar-tool-button:hover,
.sidebar-user:hover {
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

.sidebar-footer--collapsed .sidebar-tool-button,
.sidebar-footer--collapsed .sidebar-user {
  justify-content: center;
  padding: 0;
}

.sidebar-bell {
  justify-content: flex-start;
  padding: 0 10px;
}

.sidebar-footer--collapsed .sidebar-bell {
  justify-content: center;
  padding: 0;
}

.sidebar-bell-label--sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.sidebar-user {
  gap: 11px;
  padding: 4px 8px 4px 4px;
  cursor: pointer;
  transition:
    background 0.16s ease,
    border-color 0.16s ease;
}

.sidebar-user--collapsed {
  padding: 4px;
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

.main-content {
  padding: 0;
  margin-left: var(--shell-sidebar-width);
  min-height: 100vh;
  background: transparent;
}

/* 折叠时调整 */
:deep(.ant-layout-sider-collapsed) + .ant-layout .main-content {
  margin-left: var(--shell-sidebar-collapsed-width);
}

.sidebar-mask {
  display: none;
}

@media (max-width: 768px) {
  .basic-layout {
    overflow-x: hidden;
  }

  .sidebar {
    z-index: 30;
    transform: translateX(0);
    transition: width 0.22s ease;
  }

  .basic-layout--mobile-nav-open .sidebar {
    transform: translateX(0);
  }

  .main-content {
    margin-left: var(--shell-sidebar-collapsed-width);
  }

  :deep(.ant-layout-sider-collapsed) + .ant-layout .main-content {
    margin-left: var(--shell-sidebar-collapsed-width);
  }

  .main-content {
    width: calc(100% - var(--shell-sidebar-collapsed-width));
    max-width: calc(100vw - var(--shell-sidebar-collapsed-width));
    min-height: 100vh;
    padding: 12px;
    overflow-x: hidden;
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
