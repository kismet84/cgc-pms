<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { V2Button, V2Card, V2Input, V2PageState, V2Select, V2Stack, showToast } from '@/components'
import { getCurrentUser } from '@/services/auth'
import {
  changePassword,
  loadPreferences,
  savePreferences,
  updateProfile,
  type UserPreferences,
} from '@/services/account'
import { isApiClientError } from '@/services/request'
import { useSessionStore } from '@/stores/session'

type Mode = 'profile' | 'settings' | 'help'

const route = useRoute()
const session = useSessionStore()
const mode = computed<Mode>(() =>
  route.path === '/settings' ? 'settings' : route.path === '/help' ? 'help' : 'profile',
)
const loading = ref(false)
const profileSaving = ref(false)
const passwordSaving = ref(false)
const preferencesSaving = ref(false)
const passwordError = ref('')
let loadSequence = 0

const profile = reactive({
  username: '',
  realName: '',
  phone: '',
  email: '',
  avatar: '',
})
const password = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const preferences = reactive<UserPreferences>({
  sidebarCollapsed: false,
  notificationEnabled: true,
  theme: 'light',
  tableDensity: 'middle',
})

const themeOptions = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
]
const densityOptions = [
  { value: 'default', label: '宽松' },
  { value: 'middle', label: '适中' },
  { value: 'small', label: '紧凑' },
]

function messageOf(value: unknown): string {
  return isApiClientError(value) ? value.message : '请求失败，请稍后重试'
}

function fillProfile(currentUser = session.userInfo): void {
  profile.username = currentUser?.username ?? ''
  profile.realName = currentUser?.realName ?? ''
  profile.phone = currentUser?.phone ?? ''
  profile.email = currentUser?.email ?? ''
  profile.avatar = currentUser?.avatar ?? ''
}

function fillPreferences(current: UserPreferences): void {
  Object.assign(preferences, current)
}

async function load(): Promise<void> {
  const sequence = ++loadSequence
  if (mode.value === 'help') {
    loading.value = false
    return
  }
  loading.value = true
  try {
    if (mode.value === 'profile') {
      const currentUser = await getCurrentUser()
      if (sequence !== loadSequence) return
      session.replaceUserInfo(currentUser)
      fillProfile(currentUser)
    } else {
      const current = await loadPreferences()
      if (sequence !== loadSequence) return
      fillPreferences(current)
    }
  } catch (value) {
    if (sequence === loadSequence) showToast('error', '请求未完成', messageOf(value))
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  profileSaving.value = true
  try {
    await updateProfile({
      realName: profile.realName.trim(),
      phone: profile.phone.trim(),
      email: profile.email.trim(),
      avatar: profile.avatar.trim(),
    })
    const currentUser = await getCurrentUser()
    session.replaceUserInfo(currentUser)
    fillProfile(currentUser)
    showToast('success', '资料已保存', '当前账号信息已刷新。')
  } catch (value) {
    showToast('error', '请求未完成', messageOf(value))
  } finally {
    profileSaving.value = false
  }
}

function validatePassword(): string {
  if (!password.oldPassword) return '请输入旧密码'
  if (
    password.newPassword.length < 10 ||
    !/[a-z]/.test(password.newPassword) ||
    !/[A-Z]/.test(password.newPassword) ||
    !/\d/.test(password.newPassword) ||
    !/[^A-Za-z0-9]/.test(password.newPassword)
  )
    return '新密码至少10位，且必须包含大写字母、小写字母、数字和特殊字符'
  return password.newPassword === password.confirmPassword ? '' : '两次输入的新密码不一致'
}

async function savePassword(): Promise<void> {
  passwordError.value = validatePassword()
  if (passwordError.value) return
  passwordSaving.value = true
  try {
    await changePassword({
      oldPassword: password.oldPassword,
      newPassword: password.newPassword,
    })
    Object.assign(password, { oldPassword: '', newPassword: '', confirmPassword: '' })
    showToast('success', '密码已修改', '密码字段已清空。')
  } catch (value) {
    showToast('error', '请求未完成', messageOf(value))
  } finally {
    passwordSaving.value = false
  }
}

async function saveSettings(): Promise<void> {
  preferencesSaving.value = true
  try {
    await savePreferences({ ...preferences })
    fillPreferences(await loadPreferences())
    showToast('success', '偏好已保存', '偏好设置已刷新。')
  } catch (value) {
    showToast('error', '请求未完成', messageOf(value))
  } finally {
    preferencesSaving.value = false
  }
}

watch(() => route.path, load, { immediate: true })
</script>

<template>
  <V2Stack class="account-page" :gap="4">
    <V2Card
      :title="mode === 'profile' ? '个人资料' : mode === 'settings' ? '偏好设置' : '使用帮助'"
      :heading-level="1"
    ></V2Card>
    <nav class="account-page__tabs" aria-label="账号中心">
      <RouterLink to="/profile">个人资料</RouterLink>
      <RouterLink to="/settings">偏好设置</RouterLink>
      <RouterLink to="/help">使用帮助</RouterLink>
    </nav>
    <V2PageState v-if="loading" kind="loading" title="正在读取当前账号" description="请稍候。" />

    <template v-else-if="mode === 'profile'">
      <V2Card title="基本资料" subtitle="用户名、角色与权限不可在此修改。">
        <form id="profile-form" class="account-page__form" @submit.prevent="saveProfile">
          <V2Input v-model="profile.username" label="用户名" disabled autocomplete="username" />
          <V2Input v-model="profile.realName" label="姓名" required autocomplete="name" />
          <V2Input v-model="profile.phone" label="手机号" type="tel" autocomplete="tel" />
          <V2Input v-model="profile.email" label="邮箱" type="email" autocomplete="email" />
          <V2Input v-model="profile.avatar" label="头像地址" type="url" autocomplete="url" />
          <V2Button type="submit" :loading="profileSaving">保存资料</V2Button>
        </form>
      </V2Card>

      <V2Card title="修改密码" subtitle="旧密码只用于本次校验，不写入 URL 或浏览器存储。">
        <form id="password-form" class="account-page__form" @submit.prevent="savePassword">
          <V2Input
            v-model="password.oldPassword"
            label="旧密码"
            type="password"
            required
            autocomplete="current-password"
          />
          <V2Input
            v-model="password.newPassword"
            label="新密码"
            type="password"
            required
            autocomplete="new-password"
            hint="至少10位，包含大小写字母、数字和特殊字符"
            :error="passwordError"
          />
          <V2Input
            v-model="password.confirmPassword"
            label="确认新密码"
            type="password"
            required
            autocomplete="new-password"
          />
          <V2Button type="submit" :loading="passwordSaving">修改密码</V2Button>
        </form>
      </V2Card>
    </template>

    <V2Card
      v-else-if="mode === 'settings'"
      title="界面与通知偏好"
      subtitle="保存后重新载入当前偏好。"
    >
      <form id="settings-form" class="account-page__form" @submit.prevent="saveSettings">
        <label class="account-page__check">
          <input v-model="preferences.sidebarCollapsed" type="checkbox" />
          默认收起侧栏
        </label>
        <label class="account-page__check">
          <input v-model="preferences.notificationEnabled" type="checkbox" />
          启用站内通知
        </label>
        <V2Select
          :model-value="preferences.theme"
          :options="themeOptions"
          label="界面主题"
          @update:model-value="preferences.theme = $event as UserPreferences['theme']"
        />
        <V2Select
          :model-value="preferences.tableDensity"
          :options="densityOptions"
          label="表格密度"
          @update:model-value="preferences.tableDensity = $event as UserPreferences['tableDensity']"
        />
        <V2Button type="submit" :loading="preferencesSaving">保存偏好</V2Button>
      </form>
    </V2Card>

    <template v-else>
      <V2Card title="导航与账号">
        <ul>
          <li>使用侧栏进入当前账号有权限的工作区。</li>
          <li>账号中心只维护当前登录用户的资料、密码和偏好。</li>
          <li>退出后，受保护页面会要求重新登录。</li>
        </ul>
      </V2Card>
      <V2Card title="键盘与安全">
        <ul>
          <li>使用 Tab 在可操作控件间移动；菜单和对话框支持 Escape 关闭。</li>
          <li>认证信息使用同源 HttpOnly Cookie，不应粘贴到 URL 或问题描述。</li>
          <li>遇到业务权限或数据问题，请联系本系统管理员并提供页面路径与追踪号。</li>
        </ul>
      </V2Card>
    </template>
  </V2Stack>
</template>

<style scoped>
.account-page__tabs,
.account-page__form {
  display: grid;
  gap: var(--v2-space-4);
}

.account-page__tabs {
  grid-template-columns: repeat(3, max-content);
}

.account-page__tabs a {
  color: var(--v2-color-primary);
  font-weight: var(--v2-font-weight-heavy);
}

.account-page__tabs a.router-link-active {
  text-decoration: underline;
  text-underline-offset: 0.35rem;
}

.account-page__form {
  max-width: 42rem;
}

.account-page__form .v2-button {
  justify-self: start;
}

.account-page__check {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
  min-height: var(--v2-control-height-touch);
}

.account-page ul {
  display: grid;
  gap: var(--v2-space-2);
  margin: 0;
  padding-inline-start: var(--v2-space-5);
}

@media (max-width: 40rem) {
  .account-page__tabs {
    grid-template-columns: 1fr;
  }
}
</style>
