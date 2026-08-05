<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Alert, V2Button, V2Card, V2Input, V2Stack } from '@/components'
import { normalizeRedirect } from '@/services/navigation'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const tenantId = ref('')
const username = ref('')
const password = ref('')
const errorMessage = ref('')
const submitting = computed(() => session.status === 'authenticating')
const demoSubmitting = ref(false)
const showDemoEntry = import.meta.env.DEV

async function submit(): Promise<void> {
  errorMessage.value = ''
  const normalizedTenantId = Number(tenantId.value)
  const normalizedUsername = username.value.trim()
  if (!/^\d+$/.test(tenantId.value.trim()) || !Number.isSafeInteger(normalizedTenantId)) {
    errorMessage.value = '请输入有效的非负整数租户ID'
    return
  }
  if (!normalizedUsername || !password.value) {
    errorMessage.value = '请输入租户ID、用户名和密码'
    return
  }

  try {
    await session.login({
      tenantId: normalizedTenantId,
      username: normalizedUsername,
      password: password.value,
    })
    password.value = ''
    await router.replace(normalizeRedirect(route.query.redirect))
  } catch (error) {
    password.value = ''
    errorMessage.value = authErrorMessage(error)
  }
}

async function enterDemo(): Promise<void> {
  if (!showDemoEntry || demoSubmitting.value) return
  errorMessage.value = ''
  demoSubmitting.value = true
  try {
    const response = await fetch('/api/auth/dev-login?username=admin', {
      credentials: 'same-origin',
    })
    const payload = (await response.json()) as { code?: string }
    if (!response.ok || payload.code !== '0') throw new Error('DEMO_LOGIN_FAILED')
    window.location.assign(
      router.resolve(normalizeRedirect(route.query.redirect, '/dashboard')).href,
    )
  } catch {
    demoSubmitting.value = false
    errorMessage.value = '示例环境暂时不可用，请检查本地示例库'
  }
}

function authErrorMessage(error: unknown): string {
  const code = readErrorCode(error)
  if (code === 'AUTH_FAILED') return '用户名或密码错误'
  if (code === 'AUTH_DISABLED') return '账号已被禁用，请联系管理员'
  return '暂时无法登录，请稍后重试'
}

function readErrorCode(error: unknown): string | null {
  if (!error || typeof error !== 'object') return null
  const code = (error as { code?: unknown }).code
  return typeof code === 'string' ? code : null
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-intro" aria-labelledby="auth-title">
      <p class="auth-brand">CGC-PMS</p>
      <h1 id="auth-title">让项目经营事实清晰、可控、可追溯</h1>
      <p>新版工作台沿用现有权限、业务口径与同源安全会话。</p>
      <dl class="auth-points">
        <div>
          <dt>认证方式</dt>
          <dd>HttpOnly 同源 Cookie</dd>
        </div>
        <div>
          <dt>数据边界</dt>
          <dd>沿用现有租户与权限契约</dd>
        </div>
      </dl>
    </section>

    <V2Card title="登录新版工作台" subtitle="使用现有 CGC-PMS 账号继续" class="auth-card">
      <form novalidate @submit.prevent="submit">
        <V2Stack :gap="4">
          <V2Alert v-if="errorMessage" title="登录失败" tone="danger">
            {{ errorMessage }}
          </V2Alert>
          <V2Input
            v-model="tenantId"
            label="租户ID"
            type="number"
            placeholder="请输入租户ID，例如 0"
            required
            :disabled="submitting || demoSubmitting"
          />
          <V2Input
            v-model="username"
            label="用户名"
            autocomplete="username"
            placeholder="请输入用户名"
            required
            :disabled="submitting || demoSubmitting"
          />
          <V2Input
            v-model="password"
            label="密码"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            required
            :disabled="submitting || demoSubmitting"
          />
          <V2Button type="submit" size="touch" :loading="submitting" :disabled="demoSubmitting">
            {{ submitting ? '正在登录' : '登录' }}
          </V2Button>
          <V2Button
            v-if="showDemoEntry"
            type="button"
            variant="secondary"
            size="touch"
            :loading="demoSubmitting"
            :disabled="submitting"
            @click="enterDemo"
          >
            {{ demoSubmitting ? '正在进入示例环境' : '免密码进入示例环境' }}
          </V2Button>
          <p v-if="showDemoEntry" class="auth-demo-note">
            仅本地开发环境可用；进入后可切换演示角色。
          </p>
          <p class="auth-security-note">认证凭据不会写入浏览器存储、URL 或前端日志。</p>
        </V2Stack>
      </form>
    </V2Card>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(22rem, 0.85fr);
  align-items: center;
  gap: clamp(
    var(--v2-space-8),
    7vw,
    calc(var(--v2-space-12) + var(--v2-space-12) + var(--v2-space-4))
  );
  padding: clamp(var(--v2-space-6), 6vw, calc(var(--v2-space-12) + var(--v2-space-12)));
  background:
    linear-gradient(120deg, var(--v2-color-primary-soft), transparent 52%), var(--v2-color-canvas);
}

.auth-intro {
  max-width: 40rem;
}

.auth-brand {
  margin: 0 0 var(--v2-space-5);
  color: var(--v2-color-primary);
  font-size: var(--v2-font-size-15);
  font-weight: var(--v2-font-weight-heavy);
  letter-spacing: 0.08em;
}

.auth-intro h1 {
  max-width: 12em;
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-42);
  line-height: var(--v2-line-height-tight);
}

.auth-intro > p:not(.auth-brand) {
  max-width: 34rem;
  margin: var(--v2-space-5) 0 var(--v2-space-8);
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-15);
  line-height: var(--v2-line-height-body);
}

.auth-points {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
  margin: 0;
}

.auth-points div {
  padding-block-start: var(--v2-space-4);
  border-top: var(--v2-border-width) solid var(--v2-color-border);
}

.auth-points dt,
.auth-points dd {
  margin: 0;
}

.auth-points dt {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

.auth-points dd {
  margin-block-start: var(--v2-space-2);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-13);
  font-weight: var(--v2-font-weight-semibold);
}

.auth-card {
  width: min(100%, 28rem);
  justify-self: end;
}

.auth-card :deep(.v2-button) {
  width: 100%;
}

.auth-security-note {
  margin: 0;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
  line-height: var(--v2-line-height-body);
  text-align: center;
}

.auth-demo-note {
  margin: 0;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-11);
  line-height: var(--v2-line-height-body);
  text-align: center;
}

@media (max-width: 48rem) {
  .auth-page {
    grid-template-columns: 1fr;
    align-content: center;
    gap: var(--v2-space-6);
    padding: var(--v2-space-5) var(--v2-space-4);
    background: var(--v2-color-canvas);
  }

  .auth-intro h1 {
    font-size: var(--v2-font-size-28);
  }

  .auth-intro > p:not(.auth-brand),
  .auth-points {
    display: none;
  }

  .auth-brand {
    margin-block-end: var(--v2-space-3);
  }

  .auth-card {
    width: 100%;
    justify-self: stretch;
  }
}
</style>
