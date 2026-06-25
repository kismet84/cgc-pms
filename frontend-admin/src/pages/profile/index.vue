<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { updateProfile, changePassword } from '@/api/modules/auth'

const userStore = useUserStore()

const profileForm = reactive({
  realName: userStore.userInfo?.realName ?? '',
  phone: userStore.userInfo?.phone ?? '',
  email: userStore.userInfo?.email ?? '',
  avatar: userStore.userInfo?.avatar ?? '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const profileLoading = ref(false)
const passwordLoading = ref(false)

async function handleProfileSave() {
  if (!profileForm.realName.trim()) {
    message.warning('请输入真实姓名')
    return
  }
  profileLoading.value = true
  try {
    const data = await updateProfile({
      realName: profileForm.realName.trim(),
      phone: profileForm.phone.trim(),
      email: profileForm.email.trim(),
      avatar: profileForm.avatar.trim(),
    })
    userStore.setUserInfo(data)
    message.success('个人资料更新成功')
  } catch (e: unknown) {
    console.error(e)
    // error is already handled by request interceptor
  } finally {
    profileLoading.value = false
  }
}

async function handlePasswordChange() {
  if (!passwordForm.oldPassword) {
    message.warning('请输入原密码')
    return
  }
  if (!passwordForm.newPassword) {
    message.warning('请输入新密码')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.error('两次输入的新密码不一致')
    return
  }
  passwordLoading.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    message.success('密码修改成功')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch (e: unknown) {
    console.error(e)
    // error is already handled by request interceptor
  } finally {
    passwordLoading.value = false
  }
}
</script>

<template>
  <div class="lg-page app-page profile-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-page-head-breadcrumb">
          <a-breadcrumb-item>系统管理</a-breadcrumb-item>
          <a-breadcrumb-item>个人资料</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="profile-layout">
      <aside class="lg-card profile-left">
        <div class="profile-avatar-section">
          <a-avatar
            :size="100"
            :src="userStore.userInfo?.avatar"
            class="profile-avatar"
            :style="{ backgroundColor: userStore.userInfo?.avatar ? 'transparent' : '#1677ff' }"
          >
            <template v-if="!userStore.userInfo?.avatar">
              {{ userStore.userInfo?.realName?.charAt(0) }}
            </template>
          </a-avatar>
          <div class="profile-name">{{ userStore.userInfo?.realName }}</div>
          <div class="profile-role">{{ userStore.userInfo?.roleName }}</div>
        </div>

        <a-divider />

        <a-descriptions :column="1" size="small" class="profile-info">
          <a-descriptions-item label="用户名">
            {{ userStore.userInfo?.username }}
          </a-descriptions-item>
          <a-descriptions-item label="手机号">
            {{ userStore.userInfo?.phone || '未设置' }}
          </a-descriptions-item>
          <a-descriptions-item label="邮箱">
            {{ userStore.userInfo?.email || '未设置' }}
          </a-descriptions-item>
        </a-descriptions>
      </aside>

      <main class="profile-right">
        <section class="lg-section">
          <div class="lg-section-head">
            <h2 class="lg-section-title">个人资料</h2>
          </div>
          <div class="lg-section-body">
            <a-form :model="profileForm" layout="vertical" @finish="handleProfileSave">
              <a-form-item label="真实姓名" name="realName">
                <a-input
                  v-model:value="profileForm.realName"
                  placeholder="请输入真实姓名"
                  allow-clear
                />
              </a-form-item>

              <a-form-item label="手机号" name="phone">
                <a-input v-model:value="profileForm.phone" placeholder="请输入手机号" allow-clear />
              </a-form-item>

              <a-form-item label="邮箱" name="email">
                <a-input v-model:value="profileForm.email" placeholder="请输入邮箱" allow-clear />
              </a-form-item>

              <a-form-item label="头像地址" name="avatar">
                <a-input
                  v-model:value="profileForm.avatar"
                  placeholder="请输入头像图片地址"
                  allow-clear
                />
              </a-form-item>

              <a-form-item>
                <a-button type="primary" html-type="submit" :loading="profileLoading">
                  保存
                </a-button>
              </a-form-item>
            </a-form>
          </div>
        </section>

        <section class="lg-section">
          <div class="lg-section-head">
            <h2 class="lg-section-title">修改密码</h2>
          </div>
          <div class="lg-section-body">
            <a-form :model="passwordForm" layout="vertical" @finish="handlePasswordChange">
              <!-- hidden username field for password manager context -->
              <input
                type="text"
                autocomplete="username"
                :value="userStore.userInfo?.username"
                readonly
                hidden
              />

              <a-form-item label="原密码" name="oldPassword">
                <a-input-password
                  v-model:value="passwordForm.oldPassword"
                  placeholder="请输入原密码"
                  autocomplete="current-password"
                />
              </a-form-item>

              <a-form-item label="新密码" name="newPassword">
                <a-input-password
                  v-model:value="passwordForm.newPassword"
                  placeholder="请输入新密码"
                  autocomplete="new-password"
                />
              </a-form-item>

              <a-form-item label="确认新密码" name="confirmPassword">
                <a-input-password
                  v-model:value="passwordForm.confirmPassword"
                  placeholder="请再次输入新密码"
                  autocomplete="new-password"
                />
              </a-form-item>

              <a-form-item>
                <a-button :loading="passwordLoading" html-type="submit"> 修改密码 </a-button>
              </a-form-item>
            </a-form>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  max-width: 1100px;
}

.profile-left {
  padding: 32px 24px 24px;
}

.profile-avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 16px;
}

.profile-avatar {
  margin-bottom: 12px;
  font-size: 36px;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
}

.profile-role {
  font-size: 13px;
  color: var(--muted);
}

.profile-info {
  margin-top: 8px;
}

.profile-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

@media (max-width: 900px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }
}
</style>
