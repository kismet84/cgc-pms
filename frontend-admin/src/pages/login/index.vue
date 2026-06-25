<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { login } from '@/api/modules/auth'
import type { LoginParams } from '@/types/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const formState = reactive<LoginParams>({
  username: 'admin',
  password: '',
  remember: true,
})

async function handleSubmit() {
  if (!formState.username || !formState.password) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    // 接入真实后端登录接口
    // tokens are now HttpOnly cookies set by the backend — frontend only stores userInfo
    const result = await login(formState)
    userStore.setUserInfo(result.userInfo)

    message.success('登录成功')
    router.push(normalizeRedirect(route.query.redirect))
  } catch (err) {
    message.error(err instanceof Error ? err.message : '登录失败，请重试')
  } finally {
    loading.value = false
  }
}

function normalizeRedirect(value: unknown) {
  const redirect = Array.isArray(value) ? value[0] : value
  if (typeof redirect !== 'string') {
    return '/'
  }
  if (!redirect.startsWith('/') || redirect.startsWith('//')) {
    return '/'
  }
  return redirect
}

function handleForgotPassword() {
  message.info('请联系系统管理员重置密码')
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="logo" aria-hidden="true">▣</div>
        <h1 class="title">建筑工程总包项目管理系统</h1>
        <p class="subtitle">Construction General Contracting PMS</p>
      </div>

      <a-form :model="formState" layout="vertical" class="login-form" @finish="handleSubmit">
        <a-form-item name="username">
          <a-input
            v-model:value="formState.username"
            size="large"
            placeholder="请输入用户名"
            allow-clear
          >
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>

        <a-form-item name="password">
          <a-input-password
            v-model:value="formState.password"
            size="large"
            placeholder="请输入密码"
            @press-enter="handleSubmit"
          >
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>

        <a-form-item>
          <div class="form-extra">
            <a-checkbox v-model:checked="formState.remember">记住我</a-checkbox>
            <a
              class="forgot"
              role="button"
              tabindex="0"
              @click="handleForgotPassword"
              @keydown.enter="handleForgotPassword"
              >忘记密码？</a
            >
          </div>
        </a-form-item>

        <a-form-item>
          <a-button type="primary" size="large" block html-type="submit" :loading="loading">
            登 录
          </a-button>
        </a-form-item>
      </a-form>

      <p class="copyright">© 2026 建筑工程总包项目管理系统</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1677ff 0%, #0b5fe9 100%);
}

.login-card {
  width: 400px;
  padding: 40px 40px 28px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.18);
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.logo {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, #1677ff, #0b5fe9);
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 28px;
  margin: 0 auto 16px;
  box-shadow: 0 8px 20px rgba(22, 119, 255, 0.3);
}

.title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 6px;
}

.subtitle {
  font-size: 12px;
  color: var(--muted);
  margin: 0;
}

.form-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.forgot {
  color: var(--primary);
  font-size: 14px;
}

.copyright {
  text-align: center;
  font-size: 12px;
  color: #b0b7c3;
  margin: 16px 0 0;
}
</style>
