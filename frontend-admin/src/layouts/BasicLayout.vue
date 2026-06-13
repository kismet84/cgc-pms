<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { QuestionCircleOutlined, MenuFoldOutlined } from '@ant-design/icons-vue'
import SidebarMenu from './components/SidebarMenu.vue'
import NotificationBell from '@/components/NotificationBell.vue'

const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <a-layout class="basic-layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      :width="206"
      :collapsed-width="64"
      class="sidebar"
      theme="light"
    >
      <div class="brand">
        <div class="logo">▣</div>
        <span v-if="!collapsed" class="brand-text">建筑工程总包项目管理系统</span>
      </div>
      <SidebarMenu />
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="topbar">
        <MenuFoldOutlined
          class="hamburger"
          :aria-label="collapsed ? '展开菜单' : '折叠菜单'"
          @click="collapsed = !collapsed"
        />
        <div class="flex-1"></div>
        <div class="top-actions">
          <span aria-label="通知"><NotificationBell /></span>
          <QuestionCircleOutlined aria-label="帮助" style="font-size: 18px; cursor: pointer" @click="router.push('/help')" />
          <a-dropdown>
            <div class="user-info">
              <a-avatar
                :size="32"
                :style="{ background: 'linear-gradient(135deg, #8ac1ff, #006dff)' }"
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
}

.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 10;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.03);
}

.brand {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid #e5eaf3;
  font-weight: 700;
  white-space: nowrap;
}

.logo {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: linear-gradient(135deg, #1677ff, #0b5fe9);
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 15px;
  box-shadow: 0 4px 10px rgba(22, 119, 255, 0.25);
  flex-shrink: 0;
}

.brand-text {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topbar {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e5eaf3;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 18px;
  position: sticky;
  top: 0;
  z-index: 5;
  margin-left: 206px;
}

.hamburger {
  font-size: 20px;
  cursor: pointer;
  color: #374151;
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
  font-weight: 600;
  color: #111827;
}

.role {
  font-size: 12px;
  color: #6b7280;
}

.main-content {
  padding: 18px;
  margin-left: 206px;
  min-height: calc(100vh - 56px);
}

/* 折叠时调整 */
:deep(.ant-layout-sider-collapsed) + .ant-layout .topbar,
:deep(.ant-layout-sider-collapsed) + .ant-layout .main-content {
  margin-left: 64px;
}
</style>
