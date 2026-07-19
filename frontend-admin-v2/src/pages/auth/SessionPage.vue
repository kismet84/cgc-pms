<script setup lang="ts">
import { useRouter } from 'vue-router'
import { V2Alert, V2Badge, V2Button, V2Card, V2Cluster, V2Stack } from '@/components'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

async function signOut(): Promise<void> {
  try {
    await session.logout()
  } finally {
    await router.replace('/login')
  }
}
</script>

<template>
  <main class="session-page">
    <V2Card
      title="安全会话已恢复"
      subtitle="当前页面只验证认证契约，不加载业务数据"
      class="session-card"
    >
      <V2Stack :gap="5">
        <V2Alert title="登录状态正常" tone="success">
          请求通过 HttpOnly 同源 Cookie 鉴权，前端不持有 token。
        </V2Alert>
        <dl class="session-summary">
          <div>
            <dt>当前用户</dt>
            <dd>{{ session.userInfo?.realName || session.userInfo?.username }}</dd>
          </div>
          <div>
            <dt>账号</dt>
            <dd>{{ session.userInfo?.username }}</dd>
          </div>
        </dl>
        <V2Cluster :gap="2">
          <V2Badge v-for="role in session.roles" :key="role" tone="info" dot>
            {{ role }}
          </V2Badge>
        </V2Cluster>
      </V2Stack>
      <template #footer>
        <V2Cluster justify="between">
          <span class="session-boundary">业务应用壳将在后续切片接入</span>
          <V2Button
            variant="secondary"
            :loading="session.status === 'signing-out'"
            @click="signOut"
          >
            退出登录
          </V2Button>
        </V2Cluster>
      </template>
    </V2Card>
  </main>
</template>

<style scoped>
.session-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--v2-page-gutter);
}

.session-card {
  width: min(38rem, 100%);
}

.session-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-4);
  margin: 0;
}

.session-summary div {
  padding: var(--v2-space-4);
  background: var(--v2-color-surface-subtle);
  border: var(--v2-border-width) solid var(--v2-color-border-subtle);
  border-radius: var(--v2-radius-sm);
}

.session-summary dt,
.session-summary dd {
  margin: 0;
}

.session-summary dt,
.session-boundary {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

.session-summary dd {
  margin-block-start: var(--v2-space-2);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-14);
  font-weight: var(--v2-font-weight-semibold);
}

@media (max-width: 36rem) {
  .session-summary {
    grid-template-columns: 1fr;
  }

  .session-boundary {
    flex-basis: 100%;
  }
}
</style>
