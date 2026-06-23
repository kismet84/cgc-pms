<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { storeToRefs } from 'pinia'
import { QuestionCircleOutlined, MenuFoldOutlined } from '@ant-design/icons-vue'
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
      :width="216"
      :collapsed-width="64"
      class="sidebar"
      theme="light"
    >
      <div class="brand" :class="{ 'brand--collapsed': collapsed }">
        <div class="logo" aria-hidden="true">
          <span class="logo-mark">建</span>
        </div>
        <span v-if="!collapsed" class="brand-text">建筑工程总包项目管理系统</span>
      </div>
      <SidebarMenu />
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
          <QuestionCircleOutlined
            aria-label="帮助"
            style="font-size: 18px; cursor: pointer"
            @click="router.push('/help')"
          />
          <a-dropdown>
            <div class="user-info">
              <a-avatar
                :size="32"
                :style="{ background: 'var(--brand-gradient-avatar, linear-gradient(135deg, #8ac1ff, #006dff))' }"
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
  background: var(--bg);
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 20;
  overflow: hidden;
  background: var(--surface) !important;
  border-right: 1px solid var(--border);
  box-shadow: none;
}

.brand {
  height: var(--shell-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

.brand--collapsed {
  justify-content: center;
  padding: 0;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--brand-gradient-logo, linear-gradient(135deg, #1668dc, #0ea5e9));
  display: grid;
  place-items: center;
  color: var(--surface);
  flex-shrink: 0;
  box-shadow: 0 8px 18px rgba(22, 104, 220, 0.22);
}

.logo-mark {
  font-size: 15px;
  font-weight: 800;
  line-height: 1;
}

.brand-text {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
  text-overflow: ellipsis;
}

.topbar {
  height: var(--shell-header-height);
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid var(--border);
  padding: 0 22px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: sticky;
  top: 0;
  z-index: 10;
  margin-left: var(--shell-sidebar-width);
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
}

.hamburger {
  font-size: 20px;
  cursor: pointer;
  color: var(--text);
}

.flex-1 {
  flex: 1;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.user-text {
  line-height: 1.3;
}

.username {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}

.role {
  margin-top: 1px;
  font-size: 12px;
  color: var(--muted);
}

.main-content {
  padding: 18px;
  margin-left: var(--shell-sidebar-width);
  min-height: calc(100vh - var(--shell-header-height));
  background: var(--bg);
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
