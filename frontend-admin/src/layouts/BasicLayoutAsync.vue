<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'
import { MenuFoldOutlined, ProjectOutlined } from '@ant-design/icons-vue'
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

onMounted(() => {
  mobileQuery = window.matchMedia('(max-width: 768px)')
  syncMobileState(mobileQuery)
  mobileQuery.addEventListener('change', syncMobileState)
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
      :width="244"
      :collapsed-width="72"
      class="sidebar"
      theme="light"
    >
      <div class="brand" :class="{ 'brand--collapsed': collapsed }">
        <ProjectOutlined class="logo" aria-hidden="true" />
        <span v-if="!collapsed" class="brand-text">建筑工程总包项目</span>
      </div>
      <SidebarMenu :collapsed="collapsed" />
    </a-layout-sider>
    <div
      v-if="isMobile && !collapsed"
      class="sidebar-mask"
      aria-hidden="true"
      @click="collapsed = true"
    ></div>

    <a-layout>
      <a-layout-header class="topbar">
        <MenuFoldOutlined
          class="hamburger"
          :aria-label="collapsed ? '展开菜单' : '折叠菜单'"
          @click="collapsed = !collapsed"
        />
        <div class="flex-1"></div>
        <div class="top-actions">
          <span v-if="bellReady" aria-label="通知"><NotificationBell /></span>
          <a-dropdown>
            <div class="user-info">
              <a-avatar
                :size="32"
                :style="{
                  background:
                    'var(--brand-gradient-avatar, linear-gradient(135deg, #8ac1ff, #006dff))',
                }"
              >
                {{ userInfo?.realName?.[0] || '●' }}
              </a-avatar>
              <div class="user-text">
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
      </a-layout-header>

      <a-layout-content class="main-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background:
    linear-gradient(180deg, rgba(234, 242, 255, 0.78), rgba(246, 248, 251, 0) 280px),
    var(--bg);
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 20;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.94) !important;
  border-right: 1px solid var(--border);
  box-shadow: 8px 0 28px rgba(21, 32, 51, 0.04);
  backdrop-filter: blur(12px);
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
  border-radius: 10px;
  color: #fff;
  font-size: 19px;
  background: var(--brand-gradient-logo);
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.18);
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

.topbar {
  height: var(--shell-header-height);
  background: rgba(255, 255, 255, 0.82);
  border-bottom: 1px solid var(--border);
  padding: 0 28px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: sticky;
  top: 0;
  z-index: 10;
  margin-left: var(--shell-sidebar-width);
  -webkit-backdrop-filter: blur(16px);
  backdrop-filter: blur(16px);
}

.hamburger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  font-size: 18px;
  cursor: pointer;
  color: var(--text-secondary);
  transition:
    background 0.16s ease,
    color 0.16s ease;
}

.hamburger:hover {
  background: var(--surface-tint);
  color: var(--primary);
}

.flex-1 {
  flex: 1;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 42px;
  padding: 4px 8px 4px 4px;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition:
    background 0.16s ease,
    border-color 0.16s ease;
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.74);
  border-color: var(--border);
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
  min-height: calc(100vh - var(--shell-header-height));
  background: transparent;
}

/* 折叠时调整 */
:deep(.ant-layout-sider-collapsed) + .ant-layout .topbar,
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
    transform: translateX(-100%);
    transition: transform 0.22s ease;
    box-shadow: 10px 0 30px rgba(15, 23, 42, 0.16);
  }

  .basic-layout--mobile-nav-open .sidebar {
    transform: translateX(0);
  }

  .topbar,
  .main-content {
    margin-left: 0;
  }

  :deep(.ant-layout-sider-collapsed) + .ant-layout .topbar,
  :deep(.ant-layout-sider-collapsed) + .ant-layout .main-content {
    margin-left: 0;
  }

  .topbar {
    width: 100%;
    padding: 0 12px;
    gap: 12px;
  }

  .main-content {
    width: 100%;
    max-width: 100vw;
    min-height: calc(100vh - var(--shell-header-height));
    padding: 12px;
    overflow-x: hidden;
  }

  .top-actions {
    gap: 12px;
  }

  .user-text {
    display: none;
  }

  .sidebar-mask {
    position: fixed;
    inset: 0;
    z-index: 25;
    display: block;
    background: rgba(15, 23, 42, 0.34);
  }
}
</style>
